package com.abyssalrpg.entity.mythic.cerberus;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.config.CerberusConfig;
import com.abyssalrpg.entity.mythic.CerberusBossEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hệ thống lưu trữ và điều phối nhóm EncounterGroup cho Boss Cerberus (SavedData cấp Server).
 *
 * Quản lý xuyên suốt save/load world, chunk unload/load, đổi dimension, /kill, /summon thủ công:
 * 1. Danh sách thành viên nhóm (UUID, vai trò CerberusForm, máu lưu gần nhất, trạng thái ALIVE/DEAD/VANISHED).
 * 2. Một BossBar chung ("Cerberus", đổi sang màu Đỏ ở Phase 2) phản ánh tổng HP / tổng MaxHP cả nhóm;
 *    đồng thời đẩy bộ số tổng HP sang DataTracker từng cá thể để Ornate RPG BossBar HUD hiển thị đúng 1 thanh duy nhất.
 * 3. Hệ thống Attack Token (mặc định tối đa 1 cá thể tung đòn nặng cùng lúc) + khóa Ultimate chung (Pack Hunt),
 *    có thời hạn lease an toàn để không bao giờ deadlock nếu 1 cá thể bị unload hoặc chết giữa chừng.
 * 4. Ba chế độ máu Phase 2: SPLIT_EQUAL (1000×3), FULL_EACH (3000×3), SHARED_POOL (quỹ chung 3000 HP, cạn cả 3 cùng chết).
 * 5. Điều phối phân tán mục tiêu khi có >= 2 người chơi, giới hạn tổng Hellhound toàn nhóm (<= 6),
 *    và đảm bảo Loot / XP / Thông báo chiến thắng CHỈ phát đúng 1 lần khi cá thể cuối cùng chết thật.
 */
public final class CerberusGroupManager extends SavedData {
    private static final String DATA_NAME = AbyssalRpgMod.MOD_ID + "_cerberus_groups";
    private static final long TOKEN_LEASE_TIMEOUT_TICKS = 160L; // 8 giây chống kẹt token vĩnh viễn

    private final Map<UUID, EncounterGroup> groups = new HashMap<>();

    public static final Factory<CerberusGroupManager> FACTORY = new Factory<>(
            CerberusGroupManager::new,
            CerberusGroupManager::load,
            null
    );

    public CerberusGroupManager() {}

    public static CerberusGroupManager get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static void tickServer(MinecraftServer server) {
        if (server.getTickCount() % 2 != 0) return; // Tick nhẹ mỗi 2 tick (10 Hz)
        CerberusGroupManager mgr = get(server);
        if (mgr.groups.isEmpty()) return;

        long gameTime = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, EncounterGroup>> it = mgr.groups.entrySet().iterator();
        while (it.hasNext()) {
            EncounterGroup g = it.next().getValue();
            g.tick(server, gameTime);
            if (g.canGarbageCollect(gameTime)) {
                g.cleanupBossBar();
                it.remove();
                mgr.setDirty();
            }
        }
    }

    public EncounterGroup getOrCreateGroup(UUID groupId) {
        EncounterGroup g = groups.get(groupId);
        if (g == null) {
            g = new EncounterGroup(this, groupId);
            groups.put(groupId, g);
            setDirty();
        }
        return g;
    }

    @Nullable
    public EncounterGroup findGroup(UUID groupId) {
        return groups.get(groupId);
    }

    public List<EncounterGroup> getAllGroups() {
        return new ArrayList<>(groups.values());
    }

    public static CerberusGroupManager load(CompoundTag tag, HolderLookup.Provider lookup) {
        CerberusGroupManager mgr = new CerberusGroupManager();
        ListTag list = tag.getList("Groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag gt = list.getCompound(i);
            if (gt.hasUUID("GroupId")) {
                EncounterGroup g = EncounterGroup.load(mgr, gt);
                mgr.groups.put(g.groupId, g);
            }
        }
        return mgr;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (EncounterGroup g : groups.values()) {
            list.add(g.save());
        }
        tag.put("Groups", list);
        return tag;
    }

    // =========================================================================
    // TRẠNG THÁI THÀNH VIÊN & LỚP ENCOUNTER GROUP
    // =========================================================================
    public enum MemberState {
        ALIVE,
        DEAD,
        VANISHED
    }

    public static final class MemberRecord {
        public final UUID uuid;
        public CerberusForm form;
        public float lastHp;
        public float maxHp;
        public MemberState state;

        public MemberRecord(UUID uuid, CerberusForm form, float hp, float maxHp, MemberState state) {
            this.uuid = uuid;
            this.form = form;
            this.lastHp = hp;
            this.maxHp = maxHp;
            this.state = state;
        }
    }

    public static final class EncounterGroup {
        private final CerberusGroupManager manager;
        public final UUID groupId;

        private boolean phase2 = false;
        private boolean splitCompleted = false;
        private CerberusConfig.SplitHealthMode healthMode = CerberusConfig.SplitHealthMode.SPLIT_EQUAL;
        private float sharedPoolHp = 3000.0f;
        private float sharedPoolMaxHp = 3000.0f;
        private boolean rewardsGranted = false;
        private int packHuntCooldownTicks = 0;
        private long packHuntStartTick = -1L;
        @Nullable
        private UUID packHuntTargetUuid = null;
        private boolean applyingSharedPoolDamage = false;
        private long emptySinceTick = -1L;

        private final Map<UUID, MemberRecord> members = new LinkedHashMap<>();
        private final Set<UUID> activeHellhounds = new HashSet<>();

        // Hệ thống Attack Token & Khóa Ultimate chung (chỉ tồn tại runtime, tự dọn khi reload/unload)
        private final Map<UUID, Long> heavyTokenHolders = new LinkedHashMap<>();
        @Nullable
        private UUID ultimateLockHolder = null;
        private long ultimateLockTick = 0L;

        // Một ServerBossEvent duy nhất đại diện toàn bộ nhóm Cerberus
        private final ServerBossEvent sharedBossEvent;

        public EncounterGroup(CerberusGroupManager manager, UUID groupId) {
            this.manager = manager;
            this.groupId = groupId;
            this.sharedBossEvent = new ServerBossEvent(
                    Component.literal("Cerberus - Chúa Tể Ba Đầu")
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF8C1A)).withBold(true)),
                    BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.NOTCHED_12
            );
            // Ẩn thanh vanilla mặc định vì Ornate RPG BossBar HUD của mod vẽ thay thế (tránh 2 thanh chồng nhau)
            this.sharedBossEvent.setVisible(false);
        }

        public ServerBossEvent getSharedBossEvent() {
            return sharedBossEvent;
        }

        public boolean isPhase2() { return phase2; }
        public boolean isSplitCompleted() { return splitCompleted; }
        public CerberusConfig.SplitHealthMode getHealthMode() { return healthMode; }
        public float getSharedPoolHp() { return sharedPoolHp; }
        public float getSharedPoolMaxHp() { return sharedPoolMaxHp; }
        public boolean isRewardsGranted() { return rewardsGranted; }
        public int getPackHuntCooldownTicks() { return packHuntCooldownTicks; }
        public Map<UUID, MemberRecord> getMembersView() { return Collections.unmodifiableMap(members); }
        public Set<UUID> getHeavyTokenHoldersView() { return Collections.unmodifiableSet(heavyTokenHolders.keySet()); }
        @Nullable public UUID getUltimateLockHolder() { return ultimateLockHolder; }

        /** Đăng ký hoặc cập nhật một thành viên Cerberus vào nhóm. */
        public void registerMember(CerberusBossEntity boss) {
            UUID id = boss.getUUID();
            float maxHp = Math.max(1.0f, boss.getBossMaxHealth() > 0f ? boss.getBossMaxHealth() : boss.getMaxHealth());
            float hp = Math.max(0.0f, Math.min(maxHp, boss.getHealth()));
            MemberRecord rec = members.get(id);
            if (rec == null) {
                rec = new MemberRecord(id, boss.getCerberusForm(), hp, maxHp,
                        boss.isAlive() ? MemberState.ALIVE : MemberState.DEAD);
                members.put(id, rec);
            } else {
                rec.form = boss.getCerberusForm();
                rec.maxHp = maxHp;
                if (boss.isAlive() && rec.state != MemberState.DEAD) {
                    rec.state = MemberState.ALIVE;
                    rec.lastHp = hp;
                }
            }
            if (boss.hasEnteredPhase2() || boss.getCerberusForm().isSplitSingle()) {
                this.phase2 = true;
                updateBossBarStyle();
            }
            this.emptySinceTick = -1L;
            manager.setDirty();
        }

        /** Khởi tạo cấu hình máu Phase 2 khi tách thành 3 cá thể 1 đầu. */
        public void configurePhase2Split(CerberusConfig.SplitHealthMode mode, float totalPhase2Hp) {
            this.phase2 = true;
            this.splitCompleted = true;
            this.healthMode = mode != null ? mode : CerberusConfig.SplitHealthMode.SPLIT_EQUAL;
            this.sharedPoolMaxHp = Math.max(300.0f, totalPhase2Hp);
            this.sharedPoolHp = this.sharedPoolMaxHp;
            this.packHuntCooldownTicks = 240; // Chờ 12s sau khi tách mới được mở Pack Hunt đầu tiên
            updateBossBarStyle();
            manager.setDirty();
        }

        private void updateBossBarStyle() {
            if (this.phase2) {
                this.sharedBossEvent.setColor(BossEvent.BossBarColor.RED);
                this.sharedBossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_12);
                this.sharedBossEvent.setName(Component.literal("Cerberus - Chúa Tể Ba Đầu")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF2E2E)).withBold(true)));
            } else {
                this.sharedBossEvent.setColor(BossEvent.BossBarColor.YELLOW);
                this.sharedBossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_12);
                this.sharedBossEvent.setName(Component.literal("Cerberus - Chúa Tể Ba Đầu")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF8C1A)).withBold(true)));
            }
        }

        public float getIndividualTargetMaxHp() {
            CerberusConfig cfg = CerberusConfig.get();
            float totalP2 = (float) (cfg.maxHealth * cfg.phase2StatMultiplier);
            return switch (this.healthMode) {
                case SPLIT_EQUAL -> Math.max(100.0f, totalP2 / 3.0f);
                case FULL_EACH, SHARED_POOL -> Math.max(300.0f, totalP2);
            };
        }

        public int getAliveMemberCount() {
            int count = 0;
            for (MemberRecord r : members.values()) {
                if (r.state == MemberState.ALIVE) count++;
            }
            return count;
        }

        public float getGroupCurrentHp() {
            if (this.phase2 && this.splitCompleted && this.healthMode == CerberusConfig.SplitHealthMode.SHARED_POOL) {
                return Math.max(0.0f, this.sharedPoolHp);
            }
            float sum = 0.0f;
            for (MemberRecord r : members.values()) {
                if (r.state == MemberState.ALIVE) {
                    sum += Math.max(0.0f, r.lastHp);
                }
            }
            return sum;
        }

        public float getGroupMaxHp() {
            if (this.phase2 && this.splitCompleted && this.healthMode == CerberusConfig.SplitHealthMode.SHARED_POOL) {
                return Math.max(1.0f, this.sharedPoolMaxHp);
            }
            float sum = 0.0f;
            for (MemberRecord r : members.values()) {
                if (r.state != MemberState.VANISHED) {
                    sum += Math.max(1.0f, r.maxHp);
                }
            }
            return Math.max(1.0f, sum);
        }

        /**
         * Xử lý sát thương ở chế độ SHARED_POOL: trừ sát thương thực tế vào quỹ chung 3000 HP,
         * đồng bộ máu cho cả 3 cá thể và kết liễu đồng loạt nếu quỹ cạn.
         */
        public boolean isApplyingSharedPoolDamage() {
            return applyingSharedPoolDamage;
        }

        public void applySharedPoolDamage(ServerLevel level, CerberusBossEntity victim, DamageSource source, float actualDelta) {
            if (!this.phase2 || !this.splitCompleted || this.healthMode != CerberusConfig.SplitHealthMode.SHARED_POOL) {
                return;
            }
            if (this.applyingSharedPoolDamage || actualDelta <= 0.0f) return;

            this.applyingSharedPoolDamage = true;
            try {
                this.sharedPoolHp = Math.max(0.0f, this.sharedPoolHp - actualDelta);
                manager.setDirty();

                List<CerberusBossEntity> loaded = findLoadedAliveMembers(level.getServer());
                if (this.sharedPoolHp <= 0.0f) {
                    // Cạn quỹ chung: các đồng đội khác chết không rơi loot trước, để victim chết cuối cùng phát 1 lần loot!
                    for (CerberusBossEntity mate : loaded) {
                        if (!mate.getUUID().equals(victim.getUUID()) && mate.isAlive()) {
                            MemberRecord r = members.get(mate.getUUID());
                            if (r != null) {
                                r.lastHp = 0.0f;
                                r.state = MemberState.DEAD;
                            }
                            mate.forceKillFromSharedPool(source, false);
                        }
                    }
                    // Đánh dấu mọi thành viên chưa load cũng DEAD để victim chắc chắn là cá thể cuối cùng
                    for (MemberRecord r : members.values()) {
                        if (!r.uuid.equals(victim.getUUID()) && r.state == MemberState.ALIVE) {
                            r.lastHp = 0.0f;
                            r.state = MemberState.DEAD;
                        }
                    }
                    victim.forceKillFromSharedPool(source, true);
                } else {
                    for (CerberusBossEntity mate : loaded) {
                        if (mate.isAlive()) {
                            mate.syncSharedPoolHealth(this.sharedPoolHp, this.sharedPoolMaxHp);
                            MemberRecord r = members.get(mate.getUUID());
                            if (r != null) {
                                r.lastHp = this.sharedPoolHp;
                                r.maxHp = this.sharedPoolMaxHp;
                            }
                        }
                    }
                }
            } finally {
                this.applyingSharedPoolDamage = false;
            }
        }

        /**
         * Gọi khi 1 thành viên chết thật (`die`).
         * Trả về `true` DUY NHẤT cho cá thể cuối cùng còn sống chết trong nhóm (để phát Loot, XP, Thông báo 1 lần).
         */
        public boolean onMemberKilled(ServerLevel level, CerberusBossEntity member, DamageSource source) {
            releaseAllLocks(member.getUUID());
            MemberRecord rec = members.get(member.getUUID());
            if (rec != null) {
                rec.lastHp = 0.0f;
                rec.state = MemberState.DEAD;
            }
            if (this.phase2 && this.healthMode == CerberusConfig.SplitHealthMode.SHARED_POOL && getAliveMemberCount() == 0) {
                this.sharedPoolHp = 0.0f;
            }

            boolean isFinalKill = (getAliveMemberCount() == 0);
            boolean shouldDrop = false;
            if (isFinalKill) {
                if (!this.rewardsGranted) {
                    this.rewardsGranted = true;
                    shouldDrop = true;
                }
                cleanupBossBar();
                despawnRemainingHellhounds(level.getServer());
                this.emptySinceTick = level.getGameTime();
            } else if (!CerberusConfig.get().lootOnlyOnFinalMemberDeath) {
                shouldDrop = true;
            }
            manager.setDirty();
            syncGroupHudToLoadedMembers(level.getServer());
            return shouldDrop;
        }

        /**
         * Gọi khi 1 thành viên biến mất KHÔNG do chết (discard/despawn).
         * Nhóm vẫn chạy với các con còn lại; nếu tất cả đều biến mất không do chết -> KHÔNG rơi loot.
         */
        public void onMemberVanished(MinecraftServer server, UUID memberId) {
            releaseAllLocks(memberId);
            MemberRecord rec = members.get(memberId);
            if (rec != null && rec.state == MemberState.ALIVE) {
                rec.lastHp = 0.0f;
                rec.state = MemberState.VANISHED;
                manager.setDirty();
            }
            if (getAliveMemberCount() == 0) {
                cleanupBossBar();
                despawnRemainingHellhounds(server);
                this.emptySinceTick = server.overworld().getGameTime();
            } else {
                syncGroupHudToLoadedMembers(server);
            }
        }

        /** Gọi khi 1 thành viên bị unload chunk hoặc đổi dimension: giữ trạng thái ALIVE nhưng nhả token tấn công. */
        public void onMemberUnloaded(UUID memberId, float currentHp) {
            releaseAllLocks(memberId);
            MemberRecord rec = members.get(memberId);
            if (rec != null && rec.state == MemberState.ALIVE) {
                rec.lastHp = Math.max(1.0f, currentHp);
                manager.setDirty();
            }
        }

        // =====================================================================
        // HỆ THỐNG ATTACK TOKEN & KHÓA ULTIMATE CHUNG
        // =====================================================================
        public boolean tryAcquireHeavyToken(UUID memberId, long gameTime) {
            pruneExpiredTokens(gameTime);
            if (heavyTokenHolders.containsKey(memberId)) {
                heavyTokenHolders.put(memberId, gameTime);
                return true;
            }
            int limit = CerberusConfig.get().maxSimultaneousHeavyAttackers;
            if (heavyTokenHolders.size() < limit) {
                heavyTokenHolders.put(memberId, gameTime);
                return true;
            }
            return false;
        }

        public boolean hasHeavyToken(UUID memberId) {
            return heavyTokenHolders.containsKey(memberId);
        }

        public void releaseHeavyToken(UUID memberId) {
            heavyTokenHolders.remove(memberId);
        }

        public boolean tryAcquireUltimateLock(UUID memberId, long gameTime) {
            pruneExpiredTokens(gameTime);
            if (ultimateLockHolder == null || ultimateLockHolder.equals(memberId)) {
                ultimateLockHolder = memberId;
                ultimateLockTick = gameTime;
                return true;
            }
            return false;
        }

        public void releaseUltimateLock(UUID memberId) {
            if (memberId.equals(ultimateLockHolder)) {
                ultimateLockHolder = null;
                ultimateLockTick = 0L;
            }
        }

        public void releaseAllLocks(UUID memberId) {
            heavyTokenHolders.remove(memberId);
            releaseUltimateLock(memberId);
        }

        private void pruneExpiredTokens(long gameTime) {
            heavyTokenHolders.entrySet().removeIf(e -> {
                MemberRecord r = members.get(e.getKey());
                boolean invalid = (r == null || r.state != MemberState.ALIVE);
                boolean expired = (gameTime - e.getValue()) > TOKEN_LEASE_TIMEOUT_TICKS;
                return invalid || expired;
            });
            if (ultimateLockHolder != null) {
                MemberRecord r = members.get(ultimateLockHolder);
                if (r == null || r.state != MemberState.ALIVE || (gameTime - ultimateLockTick) > TOKEN_LEASE_TIMEOUT_TICKS * 2L) {
                    ultimateLockHolder = null;
                    ultimateLockTick = 0L;
                }
            }
        }

        // =====================================================================
        // PHÂN TÁN MỤC TIÊU (>= 2 NGƯỜI CHƠI) & PACK HUNT COMBO (3 CÁ THỂ)
        // =====================================================================
        @Nullable
        public Player chooseTargetFor(ServerLevel level, CerberusBossEntity member) {
            double range = CerberusConfig.get().detectionRadius;
            AABB box = member.getBoundingBox().inflate(range);
            List<Player> candidates = level.getEntitiesOfClass(Player.class, box,
                    p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && member.distanceToSqr(p) <= range * range);
            if (candidates.isEmpty()) return null;
            if (candidates.size() == 1) return candidates.get(0);

            // Thu thập danh sách mục tiêu đang bị đồng đội khác khóa để ưu tiên phân tán
            Set<UUID> claimedByTeammates = new HashSet<>();
            for (CerberusBossEntity mate : findLoadedAliveMembers(level.getServer())) {
                if (!mate.getUUID().equals(member.getUUID()) && mate.getTarget() instanceof Player p && p.isAlive()) {
                    claimedByTeammates.add(p.getUUID());
                }
            }

            Player bestUnclaimed = null;
            double bestUnclaimedDist = Double.MAX_VALUE;
            Player closestAny = null;
            double closestAnyDist = Double.MAX_VALUE;
            for (Player p : candidates) {
                double dSq = member.distanceToSqr(p);
                if (dSq < closestAnyDist) {
                    closestAnyDist = dSq;
                    closestAny = p;
                }
                if (!claimedByTeammates.contains(p.getUUID()) && dSq < bestUnclaimedDist) {
                    bestUnclaimedDist = dSq;
                    bestUnclaimed = p;
                }
            }
            return bestUnclaimed != null ? bestUnclaimed : closestAny;
        }

        /**
         * Combo nhóm "Pack Hunt": chỉ kích hoạt khi còn đủ 3 cá thể sống, không ai đang giữ khóa Ultimate,
         * cooldown 40 giây (800 ticks) đã hồi, và cả 3 đang ở cùng dimension trong phạm vi chiến đấu.
         */
        public boolean tryStartPackHunt(ServerLevel level, CerberusBossEntity initiator, LivingEntity target) {
            if (!this.phase2 || !this.splitCompleted) return false;
            if (this.packHuntCooldownTicks > 0 || getAliveMemberCount() < 3) return false;
            long now = level.getGameTime();
            if (!tryAcquireUltimateLock(initiator.getUUID(), now)) return false;

            List<CerberusBossEntity> loaded = findLoadedAliveMembers(level.getServer());
            List<CerberusBossEntity> ready = new ArrayList<>();
            for (CerberusBossEntity m : loaded) {
                if (m.level() == level && m.isAlive() && !m.isTransitioningPhase()
                        && m.getSkillState() == CerberusBossEntity.SkillState.IDLE
                        && m.distanceToSqr(target) <= 52.0 * 52.0) {
                    ready.add(m);
                }
            }
            if (ready.size() < 3) {
                releaseUltimateLock(initiator.getUUID());
                return false;
            }

            this.packHuntCooldownTicks = CerberusConfig.get().packHuntCooldown;
            this.packHuntStartTick = now;
            this.packHuntTargetUuid = target.getUUID();
            // Sắp xếp thứ tự lao đòn tuần tự cách nhau 0.8s (16 ticks): SHADOW (0) -> FIRE (1) -> ALPHA (2)
            ready.sort((a, b) -> Integer.compare(packHuntOrder(a.getCerberusForm()), packHuntOrder(b.getCerberusForm())));
            for (int i = 0; i < ready.size(); i++) {
                ready.get(i).beginPackHuntSlot(target, i, i * 16);
            }
            manager.setDirty();
            return true;
        }

        private static int packHuntOrder(CerberusForm form) {
            return switch (form) {
                case SHADOW_HEAD -> 0;
                case FIRE_HEAD -> 1;
                case ALPHA_HEAD -> 2;
                default -> 3;
            };
        }

        @Nullable public UUID getPackHuntTargetUuid() { return packHuntTargetUuid; }
        public long getPackHuntStartTick() { return packHuntStartTick; }

        // =====================================================================
        // GIỚI HẠN HELLHOUND TOÀN NHÓM (TỐI ĐA 6 CON CÙNG LÚC)
        // =====================================================================
        public int getRemainingHellhoundCapacity(MinecraftServer server) {
            pruneHellhounds(server);
            return Math.max(0, CerberusConfig.get().maxActiveHellhounds - activeHellhounds.size());
        }

        public void registerHellhound(UUID houndUuid) {
            activeHellhounds.add(houndUuid);
            manager.setDirty();
        }

        private void pruneHellhounds(MinecraftServer server) {
            if (activeHellhounds.isEmpty()) return;
            activeHellhounds.removeIf(uuid -> {
                Entity found = findEntityAcrossLevels(server, uuid);
                return found == null || !found.isAlive();
            });
        }

        private void despawnRemainingHellhounds(MinecraftServer server) {
            for (UUID uuid : activeHellhounds) {
                Entity found = findEntityAcrossLevels(server, uuid);
                if (found != null && found.isAlive()) {
                    found.discard();
                }
            }
            activeHellhounds.clear();
        }

        // =====================================================================
        // TICK ĐỊNH KỲ: ĐỒNG BỘ BOSSBAR CHUNG, DỌN TOKEN & DỌN NHÓM RỖNG
        // =====================================================================
        public void tick(MinecraftServer server, long gameTime) {
            if (this.packHuntCooldownTicks > 0) {
                this.packHuntCooldownTicks = Math.max(0, this.packHuntCooldownTicks - 2);
            }
            pruneExpiredTokens(gameTime);

            List<CerberusBossEntity> loaded = findLoadedAliveMembers(server);
            for (CerberusBossEntity m : loaded) {
                MemberRecord r = members.get(m.getUUID());
                if (r != null && r.state == MemberState.ALIVE) {
                    r.form = m.getCerberusForm();
                    r.maxHp = Math.max(1.0f, m.getBossMaxHealth() > 0f ? m.getBossMaxHealth() : m.getMaxHealth());
                    r.lastHp = Math.max(0.0f, m.getHealth());
                }
            }

            if (gameTime % 20L == 0L) {
                pruneHellhounds(server);
            }

            int aliveCount = getAliveMemberCount();
            if (aliveCount <= 0) {
                cleanupBossBar();
                if (this.emptySinceTick < 0L) {
                    this.emptySinceTick = gameTime;
                }
                return;
            }
            this.emptySinceTick = -1L;

            float totalHp = getGroupCurrentHp();
            float totalMaxHp = getGroupMaxHp();
            float frac = Mth.clamp(totalHp / Math.max(1.0f, totalMaxHp), 0.0f, 1.0f);
            updateBossBarStyle();
            this.sharedBossEvent.setProgress(frac);

            // Đồng bộ danh sách player cho sharedBossEvent & dữ liệu nhóm sang mọi thành viên đang load
            double barRadSq = CerberusConfig.get().bossBarRadius * CerberusConfig.get().bossBarRadius;
            Set<ServerPlayer> inRange = new HashSet<>();
            for (CerberusBossEntity m : loaded) {
                if (m.level() instanceof ServerLevel sl) {
                    for (ServerPlayer sp : sl.players()) {
                        if (sp.isAlive() && sp.distanceToSqr(m) <= barRadSq) {
                            inRange.add(sp);
                        }
                    }
                }
            }
            for (ServerPlayer sp : new ArrayList<>(this.sharedBossEvent.getPlayers())) {
                if (!inRange.contains(sp)) {
                    this.sharedBossEvent.removePlayer(sp);
                }
            }
            for (ServerPlayer sp : inRange) {
                this.sharedBossEvent.addPlayer(sp);
            }

            syncGroupHudToLoadedMembers(server);
        }

        public void syncGroupHudToLoadedMembers(MinecraftServer server) {
            float totalHp = getGroupCurrentHp();
            float totalMax = getGroupMaxHp();
            int aliveCount = getAliveMemberCount();
            for (CerberusBossEntity m : findLoadedAliveMembers(server)) {
                m.syncGroupHudState(totalHp, totalMax, aliveCount);
            }
        }

        public void cleanupBossBar() {
            this.sharedBossEvent.removeAllPlayers();
        }

        public boolean canGarbageCollect(long gameTime) {
            return getAliveMemberCount() == 0 && this.emptySinceTick >= 0L && (gameTime - this.emptySinceTick) > 200L;
        }

        public List<CerberusBossEntity> findLoadedAliveMembers(MinecraftServer server) {
            List<CerberusBossEntity> out = new ArrayList<>(members.size());
            for (MemberRecord rec : members.values()) {
                if (rec.state != MemberState.ALIVE) continue;
                Entity e = findEntityAcrossLevels(server, rec.uuid);
                if (e instanceof CerberusBossEntity cb && cb.isAlive()) {
                    out.add(cb);
                }
            }
            return out;
        }

        @Nullable
        private static Entity findEntityAcrossLevels(MinecraftServer server, UUID uuid) {
            for (ServerLevel sl : server.getAllLevels()) {
                Entity e = sl.getEntity(uuid);
                if (e != null) return e;
            }
            return null;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("GroupId", groupId);
            tag.putBoolean("Phase2", phase2);
            tag.putBoolean("SplitCompleted", splitCompleted);
            tag.putString("HealthMode", healthMode.name());
            tag.putFloat("SharedPoolHp", sharedPoolHp);
            tag.putFloat("SharedPoolMaxHp", sharedPoolMaxHp);
            tag.putBoolean("RewardsGranted", rewardsGranted);
            tag.putInt("PackHuntCd", packHuntCooldownTicks);

            ListTag mList = new ListTag();
            for (MemberRecord r : members.values()) {
                CompoundTag mt = new CompoundTag();
                mt.putUUID("Uuid", r.uuid);
                mt.putInt("Form", r.form.id());
                mt.putFloat("LastHp", r.lastHp);
                mt.putFloat("MaxHp", r.maxHp);
                mt.putString("State", r.state.name());
                mList.add(mt);
            }
            tag.put("Members", mList);

            ListTag hList = new ListTag();
            for (UUID hu : activeHellhounds) {
                CompoundTag ht = new CompoundTag();
                ht.putUUID("Uuid", hu);
                hList.add(ht);
            }
            tag.put("Hellhounds", hList);
            return tag;
        }

        public static EncounterGroup load(CerberusGroupManager mgr, CompoundTag tag) {
            UUID gid = tag.getUUID("GroupId");
            EncounterGroup g = new EncounterGroup(mgr, gid);
            g.phase2 = tag.getBoolean("Phase2");
            g.splitCompleted = tag.getBoolean("SplitCompleted");
            try {
                g.healthMode = CerberusConfig.SplitHealthMode.valueOf(tag.getString("HealthMode"));
            } catch (Exception ignored) {
                g.healthMode = CerberusConfig.SplitHealthMode.SPLIT_EQUAL;
            }
            g.sharedPoolHp = tag.contains("SharedPoolHp") ? tag.getFloat("SharedPoolHp") : 3000.0f;
            g.sharedPoolMaxHp = tag.contains("SharedPoolMaxHp") ? Math.max(1.0f, tag.getFloat("SharedPoolMaxHp")) : 3000.0f;
            g.rewardsGranted = tag.getBoolean("RewardsGranted");
            g.packHuntCooldownTicks = Math.max(0, tag.getInt("PackHuntCd"));

            ListTag mList = tag.getList("Members", Tag.TAG_COMPOUND);
            for (int i = 0; i < mList.size(); i++) {
                CompoundTag mt = mList.getCompound(i);
                if (!mt.hasUUID("Uuid")) continue;
                UUID u = mt.getUUID("Uuid");
                CerberusForm f = CerberusForm.byId(mt.getInt("Form"));
                float hp = mt.getFloat("LastHp");
                float max = Math.max(1.0f, mt.getFloat("MaxHp"));
                MemberState st;
                try {
                    st = MemberState.valueOf(mt.getString("State"));
                } catch (Exception ignored) {
                    st = MemberState.ALIVE;
                }
                g.members.put(u, new MemberRecord(u, f, hp, max, st));
            }
            ListTag hList = tag.getList("Hellhounds", Tag.TAG_COMPOUND);
            for (int i = 0; i < hList.size(); i++) {
                CompoundTag ht = hList.getCompound(i);
                if (ht.hasUUID("Uuid")) {
                    g.activeHellhounds.add(ht.getUUID("Uuid"));
                }
            }
            g.updateBossBarStyle();
            return g;
        }

        /** Mô tả ngắn phục vụ lệnh `/abyss cerberus debug`. */
        public String formatDebugSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("group=").append(groupId.toString(), 0, 8)
                    .append(" p2=").append(phase2)
                    .append(" mode=").append(healthMode)
                    .append(" hp=").append(String.format("%.0f/%.0f", getGroupCurrentHp(), getGroupMaxHp()))
                    .append(" alive=").append(getAliveMemberCount()).append("/").append(members.size())
                    .append(" heavyTokens=").append(heavyTokenHolders.size()).append("/").append(CerberusConfig.get().maxSimultaneousHeavyAttackers)
                    .append(" ultLock=").append(ultimateLockHolder != null ? ultimateLockHolder.toString().substring(0, 8) : "none")
                    .append(" hounds=").append(activeHellhounds.size()).append("/").append(CerberusConfig.get().maxActiveHellhounds)
                    .append(" packCd=").append(packHuntCooldownTicks);
            return sb.toString();
        }
    }
}
