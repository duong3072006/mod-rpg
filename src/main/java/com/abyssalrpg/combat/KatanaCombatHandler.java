package com.abyssalrpg.combat;

import com.abyssalrpg.item.KatanaItem;
import com.abyssalrpg.network.KatanaActionC2SPayload;
import com.abyssalrpg.network.KatanaSyncPayload;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModItems;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trung tâm chiến đấu toàn diện cho **Damascus Katana (`abyssalrpg:katana`)**:
 * - **Chuỗi đánh thường 4 đòn liền mạch** (`HIT_1_DRAW -> HIT_2_DOWN_DIAG -> HIT_3_UP_DIAG -> HIT_4_FINISHER`) + Tự vẩy máu & tra vỏ (`1.8s`).
 * - **5 Nhánh Combo Mở Rộng (Input Trigger):**
 *   1. **Thrust (`THRUST`):** Sau Hit 1/2 -> Giữ chuột trái -> Đâm Katate-tsuki tầm xa (`5.0` block), xuyên `60%` giáp & phá khiên.
 *   2. **360° Sweep (`DELAY_SWEEP_360`):** Hit 1 -> Đợi `>= 0.30s` (Tick 10–28) -> Bấm đánh -> Quét tầm thấp + Xoay 360° AoE hất ngã quái nhỏ.
 *   3. **Iaido Blink (`IAIDO_BLINK` — Nhất Kiếm Tất Sát):** Bấm phím `[R]` -> Lóe sao 4 cánh, lướt xuyên `7.0` block, Judgment Cut & kích nổ sát thương trễ khi tra kiếm kêu "tách"!
 *   4. **Aerial Combo (`AIR_1_LAUNCH` -> `AIR_2_MID_SWEEP` -> `AIR_3_SLAM`):** Nhảy trên không đánh 3 đòn (Hất tung -> Treo trên không `motionY=0.05` -> Bổ thiên thạch `motionY=-1.2` đập đất AoE!).
 *   5. **Parry & Counter (`DEFLECT` -> `PARRY_COUNTER`):** Đỡ chuẩn nhịp -> Bấm đánh ngay lập tức -> Phản chém chí mạng (`×2.2` Crit) + làm choáng mục tiêu!
 */
public final class KatanaCombatHandler {
    public static final int AUTO_SHEATH_IDLE_TICKS = 36; // 1.8s rời giao tranh tự Chiburi & Noto
    public static final float PARRY_REFLECT_MULTIPLIER = 2.0F;
    public static final float PARRY_ARMOR_PIERCE_FRACTION = 0.20F;
    public static final long DELAY_COMBO_MIN_TICKS = 10L;  // ~0.30s sau khi Hit 1 dừng ở 0.24s
    public static final long DELAY_COMBO_MAX_TICKS = 28L;
    public static final int IAIDO_CHARGE_READY_TICKS = 7;  // 0.35s lóe sao 4 cánh báo tụ lực xong
    public static final long IAIDO_BLINK_COOLDOWN_TICKS = 120L; // Hồi chiêu 6.0s mỗi lần dùng Nhất Kiếm Tất Sát (Iaido Blink)
    public static final long SURIAGE_PARRY_WINDOW_TICKS = 4L; // 0.18s–0.20s cửa sổ Perfect Parry (Suriage kiểu Sekiro)!
    public static final float MUNE_OSHI_DAMAGE_THRESHOLD = 12.0F; // Đòn nặng > 12 sát thương kích hoạt Mune-oshi!
    public static final long PARRY_COUNTER_WINDOW_TICKS = 15L; // 0.75s cửa sổ phản công chí mạng sau Parry / Saya-uke!
    private static final int COMBO_RESET_TICKS = 32;

    private static final DustParticleOptions CYBER_CYAN =
            new DustParticleOptions(new Vector3f(0.45F, 0.94F, 1.00F), 1.20F);
    private static final DustParticleOptions STEEL_WHITE =
            new DustParticleOptions(new Vector3f(0.95F, 0.99F, 1.00F), 1.05F);
    private static final DustParticleOptions COUNTER_GOLD =
            new DustParticleOptions(new Vector3f(1.00F, 0.82F, 0.22F), 1.35F);
    private static final DustParticleOptions COUNTER_CRIMSON =
            new DustParticleOptions(new Vector3f(1.00F, 0.22F, 0.20F), 1.25F);

    private static final ProjectileDeflection CLIENT_ARROW_DEFLECT_SENTINEL = (proj, deflectedBy, random) -> {};

    private static final Map<UUID, PlayerKatanaState> PLAYER_STATES = new HashMap<>();
    private static final ThreadLocal<ActiveHitContext> ACTIVE_HIT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> COUNTER_DAMAGE_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> SHIELD_BREAK_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Float> OVERRIDE_ARMOR_PIERCE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> MAGIC_SKILL_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> REENTRANT_GUARD_DAMAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private KatanaCombatHandler() {}

    private static final class PlayerKatanaState {
        boolean drawn = false;
        boolean loadedFromStack = false;
        int nextComboStep = 0;
        KatanaMoveType lastMove = KatanaMoveType.HIT_1_DRAW;
        long lastSwingTick = -1000L;
        long lastCombatTick = -1000L;
        long lastDeflectTick = -1000L;
        long lastToggleTick = -1000L;

        // Nhánh 2: Delay 360° Sweep pulse hẹn giờ (Đòn B ở 0.30s)
        long delaySpin360Tick = -1000L;

        // Nhánh 3: Iaido Rapid Draw / Judgment Cut
        boolean iaidoCharging = false;
        long iaidoChargeStartTick = -1000L;
        boolean iaidoReadyNotified = false;
        long iaidoCooldownUntilTick = -1000L;
        long iaidoInvulnUntilTick = -1000L;
        long judgmentDetonateTick = -1000L;
        final List<UUID> judgmentTargets = new ArrayList<>();
        final List<Vec3> judgmentPositions = new ArrayList<>();

        // Nhánh 4: Aerial Combo & Meteor Air Slam
        int airComboStep = 0;
        long lastAirHitTick = -1000L;
        boolean airSlamDiving = false;
        long airSlamStartTick = -1000L;

        // Nhánh 5: Parry Counter-Slash Window & 5 Thế Đỡ Kenjutsu
        long parryCounterUntilTick = -1000L;
        long guardStartTick = -1000L;
        long sayaBlockLockUntilTick = -1000L;
        long muneOshiSlideUntilTick = -1000L;
        int ukeDurabilityCounter = 0;
        @Nullable
        UUID lastParriedAttackerId = null;
    }

    public static final class ActiveHitContext {
        public final Player player;
        public final Entity rawTarget;
        @Nullable
        public final LivingEntity realTarget;
        public final ItemStack stack;
        public final KatanaMoveType moveType;
        public final boolean wasIaidoDraw;

        ActiveHitContext(Player player, Entity rawTarget, @Nullable LivingEntity realTarget,
                         ItemStack stack, KatanaMoveType moveType, boolean wasIaidoDraw) {
            this.player = player;
            this.rawTarget = rawTarget;
            this.realTarget = realTarget;
            this.stack = stack;
            this.moveType = moveType;
            this.wasIaidoDraw = wasIaidoDraw;
        }
    }

    public static boolean isHoldingKatana(@Nullable LivingEntity entity) {
        return entity != null && entity.getMainHandItem().is(ModItems.KATANA);
    }

    public static boolean isShieldBreakingActive() {
        return Boolean.TRUE.equals(SHIELD_BREAK_ACTIVE.get());
    }

    public static boolean isIaidoOrAirSlamInvulnerable(@Nullable LivingEntity entity, @Nullable DamageSource source) {
        if (!(entity instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) {
            return false;
        }
        PlayerKatanaState st = PLAYER_STATES.get(sp.getUUID());
        if (st == null) {
            return false;
        }
        long now = level.getGameTime();
        if (now <= st.iaidoInvulnUntilTick && (source == null || !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))) {
            return true;
        }
        return st.airSlamDiving && source != null && source.is(DamageTypeTags.IS_FALL);
    }

    public static void runUnparryableMagic(Runnable action) {
        int prev = MAGIC_SKILL_DEPTH.get();
        MAGIC_SKILL_DEPTH.set(prev + 1);
        try {
            action.run();
        } finally {
            if (prev <= 0) {
                MAGIC_SKILL_DEPTH.remove();
            } else {
                MAGIC_SKILL_DEPTH.set(prev);
            }
        }
    }

    public static boolean isMagicSkillDamageActive() {
        return MAGIC_SKILL_DEPTH.get() > 0;
    }

    public static float getActiveArmorPierceFraction(@Nullable LivingEntity victim, @Nullable DamageSource source) {
        if (source == null || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return 0.0F;
        }
        Float override = OVERRIDE_ARMOR_PIERCE.get();
        if (override != null && override > 0.0F) {
            return override;
        }
        ActiveHitContext hit = ACTIVE_HIT.get();
        if (hit != null) {
            if (hit.moveType == KatanaMoveType.THRUST) return 0.60F;
            if (hit.moveType == KatanaMoveType.PARRY_COUNTER) return 0.40F;
            if (hit.moveType == KatanaMoveType.HIT_4_FINISHER) return 0.25F;
        }
        return 0.0F;
    }

    private static PlayerKatanaState stateOf(ServerPlayer player, @Nullable ItemStack stack) {
        PlayerKatanaState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerKatanaState());
        if (!st.loadedFromStack) {
            st.drawn = (stack != null && stack.is(ModItems.KATANA)) && KatanaItem.isDrawn(stack);
            st.loadedFromStack = true;
        }
        return st;
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerKatanaState st = PLAYER_STATES.remove(player.getUUID());
        if (st != null) {
            ItemStack main = player.getMainHandItem();
            if (main.is(ModItems.KATANA)) {
                KatanaItem.setDrawn(main, st.drawn);
            }
        }
    }

    /**
     * Kích hoạt khi người chơi nhấn giữ Chuột Phải:
     * - Nếu đang **Rút kiếm (`drawn`)**: vào 4 thế đỡ Kenjutsu (`Uke-nagashi`, `Kasa-uke`, `Suriage Parry`, `Mune-oshi`).
     * - Nếu đang **Tra vỏ (`!drawn`)**: vào tư thế **Saya-uke (Đỡ bằng vỏ khẩn cấp)** (Iaido Blink giờ kích hoạt riêng bằng phím `[R]`).
     */
    public static boolean onStartUse(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !isHoldingKatana(player)) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        PlayerKatanaState st = stateOf(player, stack);
        long now = level.getGameTime();
        st.lastCombatTick = now;
        st.guardStartTick = now;
        return true;
    }

    /** Kích hoạt khi người chơi thả Chuột Phải (Iaido Blink đã chuyển sang phím `[R]`). */
    public static void onReleaseUse(ServerPlayer player, int heldTicks) {
        if (!(player.level() instanceof ServerLevel level) || !isHoldingKatana(player)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        PlayerKatanaState st = stateOf(player, stack);
        if (st.iaidoCharging) {
            st.iaidoCharging = false;
            ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                    KatanaSyncPayload.EVENT_IAIDO_CHARGE, player.getId(), (byte) 0, 0.0F,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    player.getYRot(), player.getXRot(), st.drawn, 0.0F));
        }
    }

    /** Kích hoạt trực tiếp **Nhất Kiếm Tất Sát (`IAIDO_BLINK`)** bằng phím `[R]` — chỉ dùng được khi kiếm CHƯA rút ra và KHÔNG đang đỡ bằng vỏ! */
    private static void handleIaidoBlinkKey(ServerPlayer player, ServerLevel level) {
        ItemStack stack = player.getMainHandItem();
        PlayerKatanaState st = stateOf(player, stack);
        long now = level.getGameTime();
        if (st.airSlamDiving || st.judgmentDetonateTick > 0L) {
            return;
        }
        if (st.drawn) {
            player.displayClientMessage(Component.literal(
                    "§c✦ Chỉ có thể dùng Nhất Kiếm Tất Sát khi kiếm chưa rút ra! §7(Bấm [G] để tra kiếm)"), true);
            return;
        }
        if (player.isUsingItem() || now <= st.sayaBlockLockUntilTick) {
            player.displayClientMessage(Component.literal(
                    "§c✦ Không thể dùng kỹ năng để phản đòn khi đang đỡ bằng vỏ kiếm!"), true);
            return;
        }
        if (now < st.iaidoCooldownUntilTick) {
            float remSec = (st.iaidoCooldownUntilTick - now) / 20.0F;
            player.displayClientMessage(Component.literal(
                    "§c✦ Nhất Kiếm Tất Sát đang hồi chiêu: §e" + String.format(java.util.Locale.ROOT, "%.1fs", remSec)), true);
            return;
        }
        st.iaidoCharging = false;
        st.delaySpin360Tick = -1000L;
        // Lóe sao 4 cánh tại miệng vỏ ngay khoảnh khắc xuất chiêu bằng phím [R]!
        ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                KatanaSyncPayload.EVENT_IAIDO_READY, player.getId(), (byte) KatanaMoveType.IAIDO_BLINK.id,
                6.0F, player.getX(), player.getY() + 1.0, player.getZ(),
                player.getYRot(), player.getXRot(), false, 1.0F));
        executeIaidoBlinkSlash(player, level, st, stack);
    }

    /** Nhánh 3: Lướt xuyên mục tiêu 7.0 blocks, tạo 3–5 vệt chém Judgment Cut & hẹn giờ nổ sát thương trễ lúc tra kiếm "tách"! */
    private static void executeIaidoBlinkSlash(ServerPlayer player, ServerLevel level,
                                               PlayerKatanaState st, ItemStack stack) {
        long now = level.getGameTime();
        Vec3 startPos = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1e-4) {
            flat = Vec3.directionFromRotation(0.0F, player.getYRot());
        } else {
            flat = flat.normalize();
        }

        // Tìm điểm đến an toàn dọc theo quãng lướt 7.0 blocks (xuyên qua sinh vật, tự bước lên bậc thấp, không xuyên tường đặc!)
        double maxDist = 7.0;
        Vec3 dest = startPos;
        int steps = 28;
        double stepDist = maxDist / steps;
        for (int i = 1; i <= steps; i++) {
            Vec3 cand = dest.add(flat.scale(stepDist));
            if (level.noCollision(player, player.getBoundingBox().move(cand.subtract(startPos)))) {
                dest = cand;
            } else {
                Vec3 stepUp = cand.add(0.0, 0.6, 0.0);
                if (level.noCollision(player, player.getBoundingBox().move(stepUp.subtract(startPos)))) {
                    dest = stepUp;
                } else {
                    break;
                }
            }
        }

        // Ghi nhận mọi kẻ địch nằm trên hành lang lướt để kích nổ sát thương trễ!
        st.judgmentTargets.clear();
        st.judgmentPositions.clear();
        AABB sweepBox = new AABB(startPos, dest).inflate(1.65, 1.35, 1.65);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, sweepBox,
                e -> e != player && e.isAlive() && e.isAttackable());
        Vec3 seg = dest.subtract(startPos);
        double segLenSq = Math.max(1e-4, seg.lengthSqr());
        for (LivingEntity target : victims) {
            Vec3 rel = target.position().subtract(startPos);
            double proj = Mth.clamp(rel.dot(seg) / segLenSq, 0.0, 1.0);
            Vec3 closest = startPos.add(seg.scale(proj));
            if (closest.distanceToSqr(target.position()) <= 2.25 * 2.25) {
                st.judgmentTargets.add(target.getUUID());
                Vec3 markPos = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
                st.judgmentPositions.add(markPos);
                // Cắt nhẹ tức thì (khung khựng không gian) + triệu hồi 3–5 vệt chém chéo đan xen tại vị trí cũ của mục tiêu!
                level.sendParticles(CYBER_CYAN, markPos.x, markPos.y, markPos.z, 14, 0.45, 0.45, 0.45, 0.08);
                level.sendParticles(STEEL_WHITE, markPos.x, markPos.y, markPos.z, 10, 0.35, 0.35, 0.35, 0.12);
                ModNetworking.sendKatanaSyncNearby(level, markPos, new KatanaSyncPayload(
                        KatanaSyncPayload.EVENT_JUDGMENT_CUT, player.getId(), (byte) KatanaMoveType.IAIDO_BLINK.id,
                        14.0F, markPos.x, markPos.y, markPos.z,
                        player.getYRot(), player.getXRot(), false, 0.0F));
            }
        }

        // Nếu chém gió không trúng mục tiêu nào -> vẫn hiển thị chùm 3–5 vệt Judgment Cut tại trung điểm quãng lướt!
        if (st.judgmentPositions.isEmpty()) {
            Vec3 mid = startPos.lerp(dest, 0.55).add(0.0, 1.10, 0.0);
            ModNetworking.sendKatanaSyncNearby(level, mid, new KatanaSyncPayload(
                    KatanaSyncPayload.EVENT_JUDGMENT_CUT, player.getId(), (byte) KatanaMoveType.IAIDO_BLINK.id,
                    12.0F, mid.x, mid.y, mid.z,
                    player.getYRot(), player.getXRot(), false, 0.0F));
        }

        // Vệt khí tàn ảnh dày đặc dọc đường lướt 7 blocks
        for (int i = 0; i <= 18; i++) {
            Vec3 p = startPos.lerp(dest, i / 18.0).add(0.0, 0.95, 0.0);
            level.sendParticles(CYBER_CYAN, p.x, p.y, p.z, 2, 0.12, 0.25, 0.12, 0.02);
            if ((i & 1) == 0) {
                level.sendParticles(ParticleTypes.CLOUD, p.x, p.y - 0.5, p.z, 1, 0.15, 0.08, 0.15, 0.02);
            }
        }

        // Dịch chuyển lướt xuyên tức thì & cấp 8 ticks i-frame + hồi chiêu 6.0s (120 ticks)
        st.iaidoCooldownUntilTick = now + IAIDO_BLINK_COOLDOWN_TICKS;
        st.iaidoInvulnUntilTick = now + 8L;
        player.teleportTo(dest.x, dest.y, dest.z);
        player.setDeltaMovement(flat.scale(0.22));
        player.hurtMarked = true;

        st.drawn = false; // Kết thúc Iaido Blink sẽ tự tra gọn về vỏ (Noto)!
        KatanaItem.setDrawn(stack, false);
        st.lastMove = KatanaMoveType.IAIDO_BLINK;
        st.lastSwingTick = now;
        st.lastCombatTick = now;
        st.nextComboStep = 0;
        st.judgmentDetonateTick = now + 13L; // 0.65s sau thả nút (= 1.05s tổng) -> phát tiếng "tách" kích nổ sát thương trễ!

        level.playSound(null, dest.x, dest.y + 1.0, dest.z,
                ModSounds.KATANA_DRAW, SoundSource.PLAYERS, 1.25F, 1.55F);
        level.playSound(null, dest.x, dest.y + 1.0, dest.z,
                ModSounds.KATANA_SLASH, SoundSource.PLAYERS, 1.35F, 1.72F);
        ModNetworking.sendShakeNearby(level, dest, 14.0, 0.45F, 4);

        ModNetworking.sendKatanaSyncNearby(level, dest, new KatanaSyncPayload(
                KatanaSyncPayload.EVENT_SLASH, player.getId(), (byte) KatanaMoveType.IAIDO_BLINK.id,
                KatanaMoveType.IAIDO_BLINK.baseDisplayTicks, dest.x, dest.y + 1.0, dest.z,
                player.getYRot(), player.getXRot(), false, (float) startPos.distanceTo(dest)));
    }

    /** Kích nổ sát thương trễ tại mốc 1.05s khi Katana trượt chạm đáy vỏ kêu "tách"! */
    private static void detonateIaidoJudgmentCut(ServerPlayer player, ServerLevel level,
                                                 PlayerKatanaState st, ItemStack stack) {
        st.judgmentDetonateTick = -1000L;
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                ModSounds.KATANA_SHEATH, SoundSource.PLAYERS, 1.25F, 1.26F);

        if (st.judgmentTargets.isEmpty()) {
            return;
        }
        float baseDmg = (float) Math.max(KatanaItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float dmg = baseDmg * KatanaMoveType.IAIDO_BLINK.damageMultiplier;
        int hitCount = 0;

        OVERRIDE_ARMOR_PIERCE.set(0.35F);
        try {
            for (UUID uid : st.judgmentTargets) {
                Entity ent = level.getEntity(uid);
                if (ent instanceof LivingEntity target && target.isAlive() && target.isAttackable()) {
                    target.invulnerableTime = 0;
                    if (target.hurt(level.damageSources().playerAttack(player), dmg)) {
                        hitCount++;
                        Vec3 pos = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
                        level.sendParticles(COUNTER_CRIMSON, pos.x, pos.y, pos.z, 18, 0.48, 0.55, 0.48, 0.14);
                        level.sendParticles(CYBER_CYAN, pos.x, pos.y, pos.z, 18, 0.52, 0.55, 0.52, 0.16);
                        level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 14, 0.42, 0.45, 0.42, 0.22);
                        level.playSound(null, pos.x, pos.y, pos.z,
                                ModSounds.KATANA_SLASH, SoundSource.PLAYERS, 1.15F, 0.78F);
                        ModNetworking.sendKatanaSyncNearby(level, pos, new KatanaSyncPayload(
                                KatanaSyncPayload.EVENT_JUDGMENT_CUT, player.getId(), (byte) KatanaMoveType.IAIDO_BLINK.id,
                                8.0F, pos.x, pos.y, pos.z,
                                player.getYRot(), player.getXRot(), false, 1.0F));
                    }
                }
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
            st.judgmentTargets.clear();
            st.judgmentPositions.clear();
        }
        if (hitCount > 0) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            ModNetworking.sendShakeNearby(level, player.position(), 18.0, 0.72F, 6);
            player.displayClientMessage(Component.literal(
                    "§b§l✦ NHẤT KIẾM TẤT SÁT! §fKích nổ §c" + hitCount + " §fmục tiêu (§e×2.4 · 35% Xuyên Giáp§f)"), true);
        }
    }

    private static boolean isWithinFrontGuardArc(Player player, @Nullable Vec3 attackOrigin) {
        if (attackOrigin == null) {
            return true;
        }
        Vec3 toAttack = attackOrigin.subtract(player.position());
        Vec3 toAttackFlat = new Vec3(toAttack.x, 0.0, toAttack.z);
        if (toAttackFlat.lengthSqr() <= 0.04) {
            return true;
        }
        Vec3 look = player.getLookAngle();
        Vec3 lookFlat = new Vec3(look.x, 0.0, look.z);
        if (lookFlat.lengthSqr() <= 1e-4) {
            return true;
        }
        double dot = toAttackFlat.normalize().dot(lookFlat.normalize());
        return dot >= -0.22;
    }

    @Nullable
    public static ProjectileDeflection tryGetArrowDeflection(Player player, @Nullable Projectile projectile) {
        if (player == null || !(projectile instanceof AbstractArrow) || !player.isAlive() || !isHoldingKatana(player)) {
            return null;
        }
        boolean activelyGuarding = player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND;
        if (player.level().isClientSide) {
            if (!activelyGuarding || !isWithinFrontGuardArc(player, projectile.position())) {
                return null;
            }
            return CLIENT_ARROW_DEFLECT_SENTINEL;
        }
        if (!(player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) {
            return null;
        }
        long now = level.getGameTime();
        PlayerKatanaState st = stateOf(sp, sp.getMainHandItem());
        boolean deflectGrace = (now - st.lastDeflectTick) <= 4L;
        if (!activelyGuarding && !deflectGrace) {
            return null;
        }
        if (!isWithinFrontGuardArc(sp, projectile.position())) {
            return null;
        }
        return (proj, deflectedBy, random) -> {
            if (proj instanceof AbstractArrow arrow && sp.isAlive() && isHoldingKatana(sp)) {
                PlayerKatanaState curSt = stateOf(sp, sp.getMainHandItem());
                if (!curSt.drawn) {
                    executeSayaUkeArrowBlock(sp, level, curSt, arrow);
                } else {
                    executeArrowSideDeflect(sp, level, curSt, arrow);
                }
            }
        };
    }

    private static void executeSayaUkeArrowBlock(ServerPlayer player, ServerLevel level,
                                                 PlayerKatanaState st, AbstractArrow arrow) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        right = (right.lengthSqr() < 1e-4) ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        double sideSign = level.random.nextBoolean() ? 1.0 : -1.0;
        Vec3 deflectDir = right.scale(sideSign * 0.85).add(look.scale(0.35)).add(0.0, 0.30, 0.0).normalize();
        arrow.setDeltaMovement(deflectDir.scale(Math.max(0.9, arrow.getDeltaMovement().length() * 0.8)));
        arrow.setCritArrow(false);
        arrow.setBaseDamage(0.0);
        arrow.hurtMarked = true;
        triggerSayaUkeEmergencyBlock(player, level, st, null);
    }

    private static void executeArrowSideDeflect(ServerPlayer player, ServerLevel level,
                                                PlayerKatanaState st, AbstractArrow arrow) {
        long now = level.getGameTime();
        ItemStack stack = player.getMainHandItem();
        if (!st.drawn) {
            setDrawnState(player, level, st, stack, true, false);
        }
        st.lastCombatTick = now;
        st.lastDeflectTick = now;
        st.parryCounterUntilTick = now + PARRY_COUNTER_WINDOW_TICKS;

        Vec3 look = player.getLookAngle().normalize();
        Vec3 lookFlat = new Vec3(look.x, 0.0, look.z);
        lookFlat = (lookFlat.lengthSqr() < 1e-4) ? new Vec3(0.0, 0.0, 1.0) : lookFlat.normalize();
        Vec3 right = new Vec3(-lookFlat.z, 0.0, lookFlat.x).normalize();

        Vec3 relToArrow = arrow.position().subtract(player.position());
        double lateralDot = relToArrow.x * right.x + relToArrow.z * right.z;
        double sideSign = (Math.abs(lateralDot) > 0.05) ? Math.signum(lateralDot) : (level.random.nextBoolean() ? 1.0 : -1.0);

        double inSpeed = arrow.getDeltaMovement().length();
        double outSpeed = Mth.clamp(inSpeed * 0.85, 0.95, 1.80);
        Vec3 deflectDir = right.scale(sideSign * 0.88).add(lookFlat.scale(0.32)).add(0.0, 0.34, 0.0).normalize();
        Vec3 outVel = deflectDir.scale(outSpeed);

        arrow.setPos(arrow.getX() + deflectDir.x * 0.45, arrow.getY() + 0.14, arrow.getZ() + deflectDir.z * 0.45);
        arrow.setDeltaMovement(outVel);
        arrow.setCritArrow(false);
        arrow.setBaseDamage(0.0);
        float newYaw = (float) (Mth.atan2(outVel.x, outVel.z) * (180.0 / Math.PI));
        float newPitch = (float) (Mth.atan2(outVel.y, Math.hypot(outVel.x, outVel.z)) * (180.0 / Math.PI));
        arrow.setYRot(newYaw);
        arrow.setXRot(newPitch);
        arrow.yRotO = newYaw;
        arrow.xRotO = newPitch;
        arrow.hurtMarked = true;

        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

        Vec3 clashPos = player.getEyePosition().add(look.scale(0.85)).add(0.0, -0.22, 0.0);
        level.sendParticles(STEEL_WHITE, clashPos.x, clashPos.y, clashPos.z, 14, 0.24, 0.28, 0.24, 0.12);
        level.sendParticles(CYBER_CYAN, clashPos.x, clashPos.y, clashPos.z, 14, 0.28, 0.28, 0.28, 0.10);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, clashPos.x, clashPos.y, clashPos.z, 10, 0.24, 0.24, 0.24, 0.16);
        level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                ModSounds.KATANA_PARRY, SoundSource.PLAYERS, 1.20F, 1.22F + level.random.nextFloat() * 0.12F);
        ModNetworking.sendShakeNearby(level, clashPos, 12.0, 0.38F, 4);

        player.displayClientMessage(Component.literal("§b§l✦ GẠT MŨI TÊN! §7(Bấm Chuột Trái để Phản Công!)"), true);
        ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                KatanaSyncPayload.EVENT_DEFLECT, player.getId(), (byte) KatanaMoveType.DEFLECT.id,
                KatanaMoveType.DEFLECT.baseDisplayTicks, clashPos.x, clashPos.y, clashPos.z,
                player.getYRot(), player.getXRot(), true, 0.0F));
    }

    private static boolean isParryableWeaponMeleeAttack(DamageSource source, ServerPlayer victim) {
        if (source == null || isMagicSkillDamageActive() || ExcaliburCombatHandler.isTrueDamageActive()) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.BYPASSES_ARMOR)
                || source.is(DamageTypeTags.WITCH_RESISTANT_TO)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_LIGHTNING)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_FREEZING)
                || source.is(DamageTypeTags.IS_PROJECTILE)) {
            return false;
        }
        boolean meleeType = source.is(DamageTypes.PLAYER_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
        if (!meleeType) {
            return false;
        }
        Entity direct = source.getDirectEntity();
        Entity root = source.getEntity();
        return direct instanceof LivingEntity le && direct == root && le != victim;
    }

    /**
     * Hệ thống 5 Thế Đỡ Kenjutsu cho **Damascus Katana** khi giữ `[RMB]`:
     * 1. **Uke-nagashi (Đỡ Trượt Chéo — mặc định khi đã rút kiếm):** Giảm `65%` sát thương & giảm một nửa độ bền hao mòn!
     * 2. **Ten-no-uke / Kasa-uke (Đỡ Trên Cao — `RMB` + ngước nhìn `Pitch < -30°`):** Chặn `100%` đòn bổ dọc từ trên xuống kèm Impact Frame sụp `1.5u` & tia lửa trắng/cam!
     * 3. **Suriage / Sekiro Parry (Bấm `RMB` trong `0.18s` / `4 ticks` trước khi trúng đòn):** `0` sát thương, làm choáng quái (Posture Break `2s`), tia lửa tròn vàng-cam + **Klang!**, mở khóa ngay **Parry Counter**!
     * 4. **Mune-oshi (Đỡ Một Tay Đẩy Sống Kiếm — đòn nặng `Damage > 12` hoặc nổ):** Giảm `80%` sát thương, chống phá thế thủ, chỉ trượt lùi `1` block tạo vệt bụi dưới chân!
     * 5. **Saya-uke (Đỡ Bằng Vỏ Kiếm — giữ `RMB` khi chưa rút kiếm):** Chặn `100%` đòn khẩn cấp bằng vỏ kiếm, lập tức cho phép thả `RMB` tung **Iaido Blink** ngay lập tức hoặc bấm `LMB` tung **Batto-counter**!
     */
    public static boolean tryParryIncomingDamage(LivingEntity victim, DamageSource source, float incomingAmount) {
        if (Boolean.TRUE.equals(REENTRANT_GUARD_DAMAGE.get()) || Boolean.TRUE.equals(COUNTER_DAMAGE_ACTIVE.get())) {
            return false;
        }
        if (victim == null || victim.level().isClientSide || !(victim instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (incomingAmount <= 0.0F || !player.isAlive() || !isHoldingKatana(player) || source == null) {
            return false;
        }
        if (isMagicSkillDamageActive() || ExcaliburCombatHandler.isTrueDamageActive()) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.BYPASSES_ARMOR)
                || source.is(DamageTypeTags.WITCH_RESISTANT_TO)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_LIGHTNING)
                || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }

        long now = level.getGameTime();
        PlayerKatanaState st = stateOf(player, player.getMainHandItem());
        boolean activelyGuarding = player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND;
        boolean deflectGrace = (now - st.lastDeflectTick) <= 4L;
        if (!activelyGuarding && !deflectGrace) {
            return false;
        }

        Entity directEntity = source.getDirectEntity();
        Entity rootAttacker = source.getEntity();
        boolean isExplosion = source.is(DamageTypeTags.IS_EXPLOSION);
        boolean isArrow = !isExplosion && (source.is(DamageTypes.ARROW) || source.is(DamageTypes.TRIDENT) || directEntity instanceof AbstractArrow);
        boolean isMelee = !isExplosion && !isArrow && isParryableWeaponMeleeAttack(source, player);

        if (!isExplosion && !isArrow && !isMelee) {
            return false;
        }

        Vec3 attackOrigin = source.getSourcePosition() != null
                ? source.getSourcePosition()
                : (directEntity != null ? directEntity.position() : (rootAttacker != null ? rootAttacker.position() : null));
        if (!isExplosion && !isWithinFrontGuardArc(player, attackOrigin)) {
            return false;
        }
        if (isExplosion && attackOrigin != null && attackOrigin.distanceToSqr(player.position()) > 4.0
                && !isWithinFrontGuardArc(player, attackOrigin)) {
            return false;
        }

        // === THẾ 5: SAYA-UKE (Đỡ bằng vỏ khi chưa rút kiếm -> Chặn 100% & mở ngay Iaido / Batto-counter!) ===
        if (!st.drawn) {
            if (isArrow && directEntity instanceof AbstractArrow arrow && arrow.isAlive()) {
                executeSayaUkeArrowBlock(player, level, st, arrow);
            } else {
                LivingEntity attacker = (directEntity instanceof LivingEntity le) ? le : (rootAttacker instanceof LivingEntity rle ? rle : null);
                triggerSayaUkeEmergencyBlock(player, level, st, attacker);
            }
            return true;
        }

        // Mũi tên / đinh ba khi đã rút kiếm -> gạt lệch sang bên
        if (isArrow) {
            if (directEntity instanceof AbstractArrow arrow && arrow.isAlive()) {
                executeArrowSideDeflect(player, level, st, arrow);
            } else {
                st.lastCombatTick = now;
                st.lastDeflectTick = now;
                st.parryCounterUntilTick = now + PARRY_COUNTER_WINDOW_TICKS;
                player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                Vec3 look = player.getLookAngle().normalize();
                Vec3 clashPos = player.getEyePosition().add(look.scale(0.85)).add(0.0, -0.22, 0.0);
                level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                        ModSounds.KATANA_PARRY, SoundSource.PLAYERS, 1.20F, 1.22F);
                ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                        KatanaSyncPayload.EVENT_DEFLECT, player.getId(), (byte) KatanaMoveType.DEFLECT.id,
                        KatanaMoveType.DEFLECT.baseDisplayTicks, clashPos.x, clashPos.y, clashPos.z,
                        player.getYRot(), player.getXRot(), true, 0.0F));
            }
            return true;
        }

        ItemStack stack = player.getMainHandItem();
        st.lastCombatTick = now;
        Vec3 look = player.getLookAngle().normalize();
        Vec3 lookFlat = new Vec3(look.x, 0.0, look.z);
        lookFlat = (lookFlat.lengthSqr() < 1e-4) ? Vec3.directionFromRotation(0.0F, player.getYRot()) : lookFlat.normalize();

        // === THẾ 3: SURIAGE / SEKIRO PARRY (Bấm RMB trong 0.18s / 4 ticks trước khi trúng đòn!) ===
        boolean suriageTiming = deflectGrace || (now - st.guardStartTick) <= SURIAGE_PARRY_WINDOW_TICKS;
        if (isMelee && suriageTiming) {
            LivingEntity attacker = (LivingEntity) directEntity;
            st.lastDeflectTick = now;
            st.parryCounterUntilTick = now + PARRY_COUNTER_WINDOW_TICKS;
            st.lastParriedAttackerId = attacker.getUUID();

            if (attacker.isAlive()) {
                // Posture Break — Làm choáng mục tiêu 2.0s & hất bật ngược!
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, true, true));
                attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 3, false, true, true));
                attacker.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 3, false, false, false));
                Vec3 pushDir = attacker.position().subtract(player.position());
                Vec3 pushFlat = new Vec3(pushDir.x, 0.0, pushDir.z);
                if (pushFlat.lengthSqr() > 1e-4) {
                    pushFlat = pushFlat.normalize();
                    attacker.push(pushFlat.x * 0.72, 0.22, pushFlat.z * 0.72);
                    attacker.hurtMarked = true;
                }
            }

            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

            Vec3 clashPos = player.getEyePosition().add(look.scale(0.85)).add(0.0, -0.18, 0.0);
            // Tia lửa tròn lớn màu vàng cam + tiếng kim loại chát chúa (Klang!)
            level.sendParticles(COUNTER_GOLD, clashPos.x, clashPos.y, clashPos.z, 26, 0.40, 0.42, 0.40, 0.16);
            level.sendParticles(ParticleTypes.FLAME, clashPos.x, clashPos.y, clashPos.z, 14, 0.28, 0.28, 0.28, 0.14);
            level.sendParticles(ParticleTypes.CRIT, clashPos.x, clashPos.y, clashPos.z, 16, 0.30, 0.30, 0.30, 0.20);
            level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                    ModSounds.KATANA_PARRY, SoundSource.PLAYERS, 1.48F, 1.12F);
            level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                    SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.80F, 1.75F);
            ModNetworking.sendShakeNearby(level, clashPos, 16.0, 0.65F, 5);

            player.displayClientMessage(Component.literal(
                    "§6§l✦ SURIAGE PARRY! §fPhá thế địch (Choáng 2s) · Bấm §e[Chuột Trái] §ftung §cPhản Công§f!"), true);

            ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                    KatanaSyncPayload.EVENT_DEFLECT, player.getId(), (byte) KatanaMoveType.DEFLECT.id,
                    KatanaMoveType.DEFLECT.baseDisplayTicks, clashPos.x, clashPos.y, clashPos.z,
                    player.getYRot(), player.getXRot(), true, 1.0F));
            ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                    KatanaSyncPayload.EVENT_HIT_STOP, player.getId(), (byte) KatanaMoveType.DEFLECT.id,
                    1.8F, clashPos.x, clashPos.y, clashPos.z, player.getYRot(), player.getXRot(), true, 0.0F));
            return true;
        }

        // === THẾ 4: MUNE-OSHI (Đỡ Một Tay Đẩy Sống Kiếm — Đòn hạng nặng Damage > 12 hoặc vụ nổ!) ===
        if (isExplosion || incomingAmount > MUNE_OSHI_DAMAGE_THRESHOLD) {
            float reduced = Math.max(0.5F, incomingAmount * 0.20F);
            boolean applied;
            REENTRANT_GUARD_DAMAGE.set(Boolean.TRUE);
            try {
                applied = player.hurt(source, reduced);
            } finally {
                REENTRANT_GUARD_DAMAGE.set(Boolean.FALSE);
            }
            if (applied) {
                Vec3 slideDir = lookFlat.scale(-1.0);
                if (attackOrigin != null) {
                    Vec3 away = player.position().subtract(attackOrigin);
                    Vec3 awayFlat = new Vec3(away.x, 0.0, away.z);
                    if (awayFlat.lengthSqr() > 1e-4) {
                        slideDir = awayFlat.normalize();
                    }
                }
                // Giữ vững thế thủ, không bị phá thủ hay hất tung — chỉ trượt lùi nhẹ ~1 block tạo vệt bụi dưới chân!
                Vec3 cur = player.getDeltaMovement();
                player.setDeltaMovement(slideDir.x * 0.56, Math.min(0.0, cur.y), slideDir.z * 0.56);
                player.hurtMarked = true;
                st.muneOshiSlideUntilTick = now + 6L;
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

                Vec3 bracePos = player.getEyePosition().add(lookFlat.scale(0.68)).add(0.0, -0.22, 0.0);
                level.sendParticles(STEEL_WHITE, bracePos.x, bracePos.y, bracePos.z, 18, 0.35, 0.38, 0.35, 0.12);
                level.sendParticles(COUNTER_GOLD, bracePos.x, bracePos.y, bracePos.z, 12, 0.32, 0.32, 0.32, 0.10);
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.12, player.getZ(), 10, 0.35, 0.08, 0.35, 0.06);
                level.playSound(null, bracePos.x, bracePos.y, bracePos.z,
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.72F, 0.96F);
                level.playSound(null, bracePos.x, bracePos.y, bracePos.z,
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.25F, 0.88F);
                ModNetworking.sendShakeNearby(level, bracePos, 15.0, 0.58F, 5);

                player.displayClientMessage(Component.literal(
                        "§e§l⛨ MUNE-OSHI (ĐẨY SỐNG KIẾM)! §fGiảm §680%§f đòn nặng & giữ vững thế thủ!"), true);

                ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                        KatanaSyncPayload.EVENT_GUARD_MUNE_OSHI, player.getId(), (byte) KatanaMoveType.GUARD_MUNE_OSHI.id,
                        KatanaMoveType.GUARD_MUNE_OSHI.baseDisplayTicks, bracePos.x, bracePos.y, bracePos.z,
                        player.getYRot(), player.getXRot(), true, 0.0F));
            }
            return true;
        }

        // === THẾ 2: TEN-NO-UKE / KASA-UKE (Đỡ Trên Cao — Giữ RMB + Nhìn lên Pitch < -30° -> Chặn 100%!) ===
        if (player.getXRot() < -30.0F) {
            if (directEntity instanceof LivingEntity attacker && attacker.isAlive()) {
                Vec3 pushDir = attacker.position().subtract(player.position());
                Vec3 pushFlat = new Vec3(pushDir.x, 0.0, pushDir.z);
                if (pushFlat.lengthSqr() > 1e-4) {
                    pushFlat = pushFlat.normalize();
                    attacker.push(pushFlat.x * 0.42, 0.14, pushFlat.z * 0.42);
                    attacker.hurtMarked = true;
                }
            }
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

            Vec3 roofPos = player.getEyePosition().add(lookFlat.scale(0.50)).add(0.0, 0.34, 0.0);
            level.sendParticles(STEEL_WHITE, roofPos.x, roofPos.y, roofPos.z, 18, 0.36, 0.14, 0.36, 0.14);
            level.sendParticles(COUNTER_GOLD, roofPos.x, roofPos.y, roofPos.z, 14, 0.32, 0.14, 0.32, 0.12);
            level.sendParticles(ParticleTypes.FLAME, roofPos.x, roofPos.y, roofPos.z, 8, 0.24, 0.12, 0.24, 0.10);
            level.playSound(null, roofPos.x, roofPos.y, roofPos.z,
                    ModSounds.KATANA_PARRY, SoundSource.PLAYERS, 1.25F, 1.28F);
            level.playSound(null, roofPos.x, roofPos.y, roofPos.z,
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.15F, 1.38F);
            ModNetworking.sendShakeNearby(level, roofPos, 12.0, 0.40F, 4);

            player.displayClientMessage(Component.literal(
                    "§6§l⛨ KASA-UKE (ĐỠ TRÊN CAO)! §fChặn §e100%§f đòn bổ từ trên xuống!"), true);

            ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                    KatanaSyncPayload.EVENT_GUARD_KASA_IMPACT, player.getId(), (byte) KatanaMoveType.GUARD_KASA_IMPACT.id,
                    KatanaMoveType.GUARD_KASA_IMPACT.baseDisplayTicks, roofPos.x, roofPos.y, roofPos.z,
                    player.getYRot(), player.getXRot(), true, 0.0F));
            return true;
        }

        // === THẾ 1: UKE-NAGASHI (Đỡ Trượt Chéo mặc định -> Giảm 65% sát thương & giảm 50% độ bền hao mòn!) ===
        float ukeReduced = Math.max(0.25F, incomingAmount * 0.35F);
        boolean ukeApplied;
        REENTRANT_GUARD_DAMAGE.set(Boolean.TRUE);
        try {
            ukeApplied = player.hurt(source, ukeReduced);
        } finally {
            REENTRANT_GUARD_DAMAGE.set(Boolean.FALSE);
        }
        if (ukeApplied) {
            Vec3 cur = player.getDeltaMovement();
            player.setDeltaMovement(cur.x * 0.52, Math.min(cur.y, 0.08), cur.z * 0.52);
            player.hurtMarked = true;
            // Giảm độ bền hao mòn khi gạt trượt bằng sống kiếm (chỉ tốn 1 độ bền mỗi 2 lần đỡ!)
            st.ukeDurabilityCounter++;
            if ((st.ukeDurabilityCounter & 1) == 0) {
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }

            Vec3 clashPos = player.getEyePosition().add(look.scale(0.80)).add(0.0, -0.18, 0.0);
            level.sendParticles(COUNTER_GOLD, clashPos.x, clashPos.y, clashPos.z, 14, 0.28, 0.28, 0.28, 0.12);
            level.sendParticles(ParticleTypes.FLAME, clashPos.x, clashPos.y, clashPos.z, 10, 0.22, 0.22, 0.22, 0.09);
            level.sendParticles(STEEL_WHITE, clashPos.x, clashPos.y, clashPos.z, 6, 0.20, 0.20, 0.20, 0.08);
            level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.05F, 1.24F);
            level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                    ModSounds.KATANA_PARRY, SoundSource.PLAYERS, 0.68F, 1.32F);
            ModNetworking.sendShakeNearby(level, clashPos, 10.0, 0.22F, 3);

            ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                    KatanaSyncPayload.EVENT_GUARD_UKE_HIT, player.getId(), (byte) 0,
                    4.0F, clashPos.x, clashPos.y, clashPos.z, player.getYRot(), player.getXRot(), true, 0.0F));
        }
        return true;
    }

    /** Thế 5 — Saya-uke: Chặn đòn khẩn cấp bằng vỏ kiếm (chỉ phòng thủ, không phản sát thương hay kích hoạt phản đòn bằng kỹ năng!). */
    private static void triggerSayaUkeEmergencyBlock(ServerPlayer player, ServerLevel level,
                                                     PlayerKatanaState st, @Nullable LivingEntity attacker) {
        long now = level.getGameTime();
        st.lastCombatTick = now;
        st.lastDeflectTick = now;
        st.iaidoCharging = false;
        st.sayaBlockLockUntilTick = now + 5L;

        if (attacker != null && attacker.isAlive() && attacker != player) {
            Vec3 push = attacker.position().subtract(player.position());
            Vec3 pushFlat = new Vec3(push.x, 0.0, push.z);
            if (pushFlat.lengthSqr() > 1e-4) {
                pushFlat = pushFlat.normalize();
                attacker.push(pushFlat.x * 0.52, 0.16, pushFlat.z * 0.52);
                attacker.hurtMarked = true;
            }
        }

        Vec3 look = player.getLookAngle().normalize();
        Vec3 clashPos = player.getEyePosition().add(look.scale(0.75)).add(0.0, -0.20, 0.0);
        level.sendParticles(COUNTER_GOLD, clashPos.x, clashPos.y, clashPos.z, 16, 0.30, 0.30, 0.30, 0.13);
        level.sendParticles(ParticleTypes.FLAME, clashPos.x, clashPos.y, clashPos.z, 12, 0.24, 0.24, 0.24, 0.10);
        level.sendParticles(STEEL_WHITE, clashPos.x, clashPos.y, clashPos.z, 8, 0.24, 0.24, 0.24, 0.10);
        level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.18F, 0.92F);
        level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                ModSounds.KATANA_SHEATH, SoundSource.PLAYERS, 1.35F, 1.42F);
        ModNetworking.sendShakeNearby(level, clashPos, 14.0, 0.45F, 4);

        player.displayClientMessage(Component.literal(
                "§b§l⛨ SAYA-UKE (ĐỠ BẰNG VỎ)! §fChặn gọn đòn đánh bằng vỏ kiếm!"), true);

        ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                KatanaSyncPayload.EVENT_GUARD_SAYA_UKE, player.getId(), (byte) KatanaMoveType.GUARD_SAYA_UKE_HIT.id,
                4.0F, clashPos.x, clashPos.y, clashPos.z,
                player.getYRot(), player.getXRot(), false, 1.0F));
    }

    /** Phân nhánh thông minh theo Input Trigger (Parry Counter -> Aerial -> Delay 360° -> Normal 4-Hit). */
    private static KatanaMoveType resolveStandardAttackMove(ServerPlayer player, ServerLevel level,
                                                            PlayerKatanaState st, ItemStack stack, boolean wasSheathed) {
        long now = level.getGameTime();

        // 1. Ưu tiên cao nhất: Phản Công Chớp Nhoáng ngay sau Parry khi đã rút kiếm (`PARRY_COUNTER`)!
        if (st.drawn && now <= st.parryCounterUntilTick) {
            st.parryCounterUntilTick = -1000L;
            st.nextComboStep = 0;
            return KatanaMoveType.PARRY_COUNTER;
        }

        // 2. Nhánh Không Chiến (`!player.onGround()`): Hit 1 Hất tung -> Hit 2 Chém ngang treo không -> Hit 3 Bổ thiên thạch!
        if (!player.onGround() && !player.isInWater() && !player.isFallFlying() && !player.isPassenger()) {
            if (wasSheathed) {
                setDrawnState(player, level, st, stack, true, true, false);
            }
            if (now - st.lastAirHitTick > 22L) {
                st.airComboStep = 0;
            }
            KatanaMoveType airMove = KatanaMoveType.aerialByStep(st.airComboStep);
            st.airComboStep = Math.min(2, st.airComboStep + 1);
            st.lastAirHitTick = now;
            st.nextComboStep = 0;
            return airMove;
        }

        // 3. Nếu đang tra vỏ -> Hit 1 Rút kiếm chém lướt ngang (Nukitsuke)
        if (wasSheathed) {
            setDrawnState(player, level, st, stack, true, true, false);
            st.nextComboStep = 1;
            return KatanaMoveType.HIT_1_DRAW;
        }

        // 4. Nhánh Bạt Kiếm Liên Hoàn (Delay Combo): Sau Hit 1 đợi >= 0.30s (Tick 10..28) -> đánh tiếp -> Quét thấp + Xoay 360°!
        long sinceLast = now - st.lastSwingTick;
        if (st.lastMove == KatanaMoveType.HIT_1_DRAW
                && sinceLast >= DELAY_COMBO_MIN_TICKS && sinceLast <= DELAY_COMBO_MAX_TICKS) {
            st.nextComboStep = 0;
            st.delaySpin360Tick = now + 6L; // Đòn B xoay 360° AoE kích hoạt tại mốc 0.30s!
            return KatanaMoveType.DELAY_SWEEP_360;
        }

        // 5. Chuỗi đánh thường 4 đòn liền mạch
        if (sinceLast > COMBO_RESET_TICKS) {
            st.nextComboStep = 0;
        }
        KatanaMoveType move = KatanaMoveType.comboByStep(st.nextComboStep);
        st.nextComboStep = (st.nextComboStep + 1) % 4;
        return move;
    }

    @Nullable
    public static ActiveHitContext beginPlayerAttack(Player player, Entity target) {
        if (player.level().isClientSide || !isHoldingKatana(player) || !(player instanceof ServerPlayer sp)) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        ServerLevel level = sp.serverLevel();
        PlayerKatanaState st = stateOf(sp, stack);
        if (st.airSlamDiving) {
            return null;
        }
        long now = level.getGameTime();
        boolean wasSheathed = !st.drawn;
        KatanaMoveType move = resolveStandardAttackMove(sp, level, st, stack, wasSheathed);

        st.lastMove = move;
        st.lastSwingTick = now;
        st.lastCombatTick = now;
        applyMoveKinematics(sp, st, move, now, true);

        LivingEntity living = (target instanceof LivingEntity le) ? le : null;
        ActiveHitContext ctx = new ActiveHitContext(player, target, living, stack, move, wasSheathed && move == KatanaMoveType.HIT_1_DRAW);
        ACTIVE_HIT.set(ctx);
        return ctx;
    }

    public static float modifyAttackDamage(float amount) {
        ActiveHitContext ctx = ACTIVE_HIT.get();
        if (ctx == null) {
            return amount;
        }
        return amount * ctx.moveType.damageMultiplier;
    }

    public static void finishPlayerAttack(ActiveHitContext ctx) {
        ACTIVE_HIT.remove();
        if (!(ctx.player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) {
            return;
        }
        boolean hitAny = ctx.realTarget != null && ctx.realTarget.hurtTime > 0;
        if (ctx.realTarget != null && hitAny) {
            applySpecialOnHitEffects(sp, level, ctx.realTarget, ctx.moveType);
        }
        if (ctx.moveType == KatanaMoveType.HIT_1_DRAW
                || ctx.moveType == KatanaMoveType.DELAY_SWEEP_360
                || ctx.moveType == KatanaMoveType.PARRY_COUNTER
                || ctx.moveType.isAerialMove()) {
            if (performMoveHitbox(sp, level, ctx.moveType, ctx.realTarget)) {
                hitAny = true;
            }
        }
        executeSlashEffects(sp, level, ctx.moveType, hitAny, ctx.wasIaidoDraw);
    }

    /** Áp dụng động học (lướt nhẹ, giữ lơ lửng trên không, lao cắm thiên thạch, giật thân phản công). */
    private static void applyMoveKinematics(ServerPlayer player, PlayerKatanaState st,
                                            KatanaMoveType move, long now, boolean hasDirectTarget) {
        if (player.isPassenger() || player.isSwimming() || player.isFallFlying()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = (flat.lengthSqr() < 1e-4) ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
        Vec3 cur = player.getDeltaMovement();

        switch (move) {
            case AIR_1_LAUNCH -> {
                player.setDeltaMovement(cur.x * 0.65 + flat.x * 0.14, Math.max(cur.y, 0.28), cur.z * 0.65 + flat.z * 0.14);
                player.hurtMarked = true;
                return;
            }
            case AIR_2_MID_SWEEP -> {
                // Giữ nhân vật lơ lửng nhẹ trên không (motionY = 0.05 đúng spec!)
                player.setDeltaMovement(cur.x * 0.55 + flat.x * 0.12, 0.05, cur.z * 0.55 + flat.z * 0.12);
                player.fallDistance = 0.0F;
                player.hurtMarked = true;
                return;
            }
            case AIR_3_SLAM -> {
                // Bổ thiên thạch: cắm đầu lao thẳng xuống đất (motionY = -1.2!)
                st.airSlamDiving = true;
                st.airSlamStartTick = now;
                player.setDeltaMovement(flat.x * 0.20, -1.2, flat.z * 0.20);
                player.fallDistance = 0.0F;
                player.hurtMarked = true;
                return;
            }
            case THRUST -> {
                // Phóng người đâm thẳng Katate-tsuki (~0.7 block dash!)
                player.setDeltaMovement(cur.x * 0.35 + flat.x * 0.36, cur.y, cur.z * 0.35 + flat.z * 0.36);
                player.hurtMarked = true;
                return;
            }
            case PARRY_COUNTER -> {
                player.setDeltaMovement(cur.x * 0.35 + flat.x * 0.28, cur.y, cur.z * 0.35 + flat.z * 0.28);
                player.hurtMarked = true;
                return;
            }
            default -> {
            }
        }

        if (player.isShiftKeyDown()) {
            return;
        }
        double baseImpulse = switch (move) {
            case HIT_1_DRAW -> 0.175;
            case HIT_2_DOWN_DIAG -> 0.160;
            case HIT_3_UP_DIAG -> 0.175;
            case HIT_4_FINISHER -> 0.215;
            case DELAY_SWEEP_360 -> 0.180;
            default -> 0.0;
        };
        if (baseImpulse > 0.0) {
            double impulse = hasDirectTarget ? baseImpulse * 0.65 : baseImpulse;
            if (cur.horizontalDistanceSqr() < 0.18) {
                player.setDeltaMovement(cur.x + flat.x * impulse, cur.y, cur.z + flat.z * impulse);
                player.hurtMarked = true;
            }
        }
    }

    private static void applySpecialOnHitEffects(ServerPlayer player, ServerLevel level,
                                                 LivingEntity target, KatanaMoveType move) {
        Vec3 pos = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
        switch (move) {
            case THRUST -> {
                if (target instanceof Player tp && tp.isBlocking()) {
                    tp.disableShield();
                    level.playSound(null, pos.x, pos.y, pos.z,
                            SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.15F, 1.05F);
                }
                level.sendParticles(STEEL_WHITE, pos.x, pos.y, pos.z, 12, 0.26, 0.26, 0.26, 0.12);
                level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 10, 0.28, 0.28, 0.28, 0.16);
            }
            case AIR_1_LAUNCH -> {
                Vec3 v = target.getDeltaMovement();
                target.setDeltaMovement(v.x * 0.4, 0.44, v.z * 0.4);
                target.hurtMarked = true;
            }
            case AIR_2_MID_SWEEP -> {
                Vec3 v = target.getDeltaMovement();
                target.setDeltaMovement(v.x * 0.4, 0.16, v.z * 0.4);
                target.hurtMarked = true;
            }
            case PARRY_COUNTER -> {
                // Chí mạng Crit + làm choáng (Stun 1.8s) + hiệu ứng tia lửa vàng/đỏ!
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 36, 4, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 36, 3, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 36, 3, false, false, false));
                level.sendParticles(COUNTER_GOLD, pos.x, pos.y, pos.z, 18, 0.40, 0.42, 0.40, 0.14);
                level.sendParticles(COUNTER_CRIMSON, pos.x, pos.y, pos.z, 16, 0.38, 0.40, 0.38, 0.15);
                level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 18, 0.35, 0.38, 0.35, 0.22);
                level.playSound(null, pos.x, pos.y, pos.z,
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.25F, 1.15F);
            }
            default -> {
            }
        }
    }

    public static void handleActionPacket(ServerPlayer player, KatanaActionC2SPayload payload) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !isHoldingKatana(player)) {
            return;
        }
        switch (payload.actionId()) {
            case KatanaActionC2SPayload.ACTION_AIR_SLASH -> handleAirSlash(player, level);
            case KatanaActionC2SPayload.ACTION_TOGGLE_SHEATH -> handleToggleSheath(player, level);
            case KatanaActionC2SPayload.ACTION_HEAVY_THRUST -> handleHeavyThrust(player, level);
            case KatanaActionC2SPayload.ACTION_PARRY_COUNTER -> handleParryCounter(player, level);
            case KatanaActionC2SPayload.ACTION_IAIDO_BLINK -> handleIaidoBlinkKey(player, level);
            default -> {
            }
        }
    }

    /** Nhánh 1 (Hold Attack sau Hit 1/2): Đâm xuyên giáp 60%, phá khiên, tầm đâm một tay Katate-tsuki 5.0 blocks! */
    private static void handleHeavyThrust(ServerPlayer player, ServerLevel level) {
        if (player.isUsingItem()) {
            return;
        }
        long now = level.getGameTime();
        ItemStack stack = player.getMainHandItem();
        PlayerKatanaState st = stateOf(player, stack);
        if (!st.drawn || st.airSlamDiving) {
            return;
        }
        boolean fromHit1Or2 = (st.lastMove == KatanaMoveType.HIT_1_DRAW || st.lastMove == KatanaMoveType.HIT_2_DOWN_DIAG);
        long sinceLast = now - st.lastSwingTick;
        if (!fromHit1Or2 || sinceLast < st.lastMove.cancelAfterTicks || sinceLast > 28L) {
            return;
        }

        st.lastMove = KatanaMoveType.THRUST;
        st.lastSwingTick = now;
        st.lastCombatTick = now;
        st.nextComboStep = 0;

        applyMoveKinematics(player, st, KatanaMoveType.THRUST, now, false);
        player.swing(InteractionHand.MAIN_HAND, true);
        boolean hitAny = performThrustPierce(player, level);
        if (hitAny) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        executeSlashEffects(player, level, KatanaMoveType.THRUST, hitAny, false);
    }

    /** Nhánh 5 (Bấm đánh ngay sau Parry khi đã rút kiếm): Tung Phản Công Chí Mạng ×2.2 Crit + Stun! */
    private static void handleParryCounter(ServerPlayer player, ServerLevel level) {
        long now = level.getGameTime();
        ItemStack stack = player.getMainHandItem();
        PlayerKatanaState st = stateOf(player, stack);
        if (!st.drawn || now > st.parryCounterUntilTick) {
            return;
        }
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        st.iaidoCharging = false;
        st.parryCounterUntilTick = -1000L;
        st.lastMove = KatanaMoveType.PARRY_COUNTER;
        st.lastSwingTick = now;
        st.lastCombatTick = now;
        st.nextComboStep = 0;

        applyMoveKinematics(player, st, KatanaMoveType.PARRY_COUNTER, now, false);
        player.swing(InteractionHand.MAIN_HAND, true);
        boolean hitAny = performMoveHitbox(player, level, KatanaMoveType.PARRY_COUNTER, null);
        if (hitAny) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            player.displayClientMessage(Component.literal("§6§l✦ PHẢN CÔNG CHÍ MẠNG! §f(§e×2.2 Crit · Làm Choáng§f)"), true);
        }
        executeSlashEffects(player, level, KatanaMoveType.PARRY_COUNTER, hitAny, false);
    }

    private static void handleToggleSheath(ServerPlayer player, ServerLevel level) {
        ItemStack stack = player.getMainHandItem();
        PlayerKatanaState st = stateOf(player, stack);
        long now = level.getGameTime();
        if (now - st.lastToggleTick < 8L || st.airSlamDiving) {
            return;
        }
        st.lastToggleTick = now;
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        st.iaidoCharging = false;
        boolean targetDrawn = !st.drawn;
        if (targetDrawn) {
            st.lastCombatTick = now;
            st.lastSwingTick = now;
            st.lastMove = KatanaMoveType.HIT_1_DRAW;
            st.nextComboStep = 1;
            setDrawnState(player, level, st, stack, true, true, false);
            applyMoveKinematics(player, st, KatanaMoveType.HIT_1_DRAW, now, false);
            player.swing(InteractionHand.MAIN_HAND, true);
            boolean hitAny = performMoveHitbox(player, level, KatanaMoveType.HIT_1_DRAW, null);
            if (hitAny) {
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
            executeSlashEffects(player, level, KatanaMoveType.HIT_1_DRAW, hitAny, true);
        } else {
            setDrawnState(player, level, st, stack, false, false, true);
            player.displayClientMessage(Component.literal("§7✦ Chiburi & Sheathe — Vẩy máu & tra Katana về vỏ"), true);
        }
    }

    private static void handleAirSlash(ServerPlayer player, ServerLevel level) {
        long now = level.getGameTime();
        ItemStack stack = player.getMainHandItem();
        PlayerKatanaState st = stateOf(player, stack);
        if (st.airSlamDiving) {
            return;
        }
        // Nếu đang trong cửa sổ phản công sau Parry -> ưu tiên Phản Công ngay cả khi vừa thả chuột phải!
        if (now <= st.parryCounterUntilTick) {
            handleParryCounter(player, level);
            return;
        }
        if (player.isUsingItem()) {
            return;
        }
        if (now - st.lastSwingTick < st.lastMove.cancelAfterTicks) {
            return;
        }

        boolean wasSheathed = !st.drawn;
        KatanaMoveType move = resolveStandardAttackMove(player, level, st, stack, wasSheathed);
        st.lastMove = move;
        st.lastSwingTick = now;
        st.lastCombatTick = now;

        applyMoveKinematics(player, st, move, now, false);
        player.swing(InteractionHand.MAIN_HAND, true);
        boolean hitAny = (move != KatanaMoveType.AIR_3_SLAM) && performMoveHitbox(player, level, move, null);
        if (hitAny && (move == KatanaMoveType.HIT_1_DRAW || move == KatanaMoveType.DELAY_SWEEP_360)) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        executeSlashEffects(player, level, move, hitAny, wasSheathed && move == KatanaMoveType.HIT_1_DRAW);
    }

    /** Hitbox đâm xuyên giáp tầm xa (5.0 blocks), xuyên 60% giáp & phá khiên! */
    private static boolean performThrustPierce(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double reach = 5.0;
        Vec3 end = eye.add(look.scale(reach));
        AABB box = new AABB(eye, end).inflate(1.05);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e.isAttackable());
        if (candidates.isEmpty()) {
            return false;
        }
        float baseDmg = (float) Math.max(KatanaItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float dmg = baseDmg * KatanaMoveType.THRUST.damageMultiplier;
        boolean any = false;

        SHIELD_BREAK_ACTIVE.set(Boolean.TRUE);
        OVERRIDE_ARMOR_PIERCE.set(0.60F);
        try {
            for (LivingEntity target : candidates) {
                Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
                Vec3 rel = center.subtract(eye);
                double proj = rel.dot(look);
                if (proj < 0.2 || proj > reach + 0.5) {
                    continue;
                }
                Vec3 closest = eye.add(look.scale(proj));
                if (closest.distanceToSqr(center) > 1.15 * 1.15) {
                    continue;
                }
                if (target instanceof Player tp && tp.isBlocking()) {
                    tp.disableShield();
                    level.playSound(null, center.x, center.y, center.z,
                            SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.15F, 1.05F);
                }
                target.invulnerableTime = 0;
                if (target.hurt(level.damageSources().playerAttack(player), dmg)) {
                    any = true;
                    target.push(look.x * 0.55, 0.14, look.z * 0.55);
                    target.hurtMarked = true;
                    applySpecialOnHitEffects(player, level, target, KatanaMoveType.THRUST);
                }
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
            SHIELD_BREAK_ACTIVE.set(Boolean.FALSE);
        }
        return any;
    }

    /** Đòn B của Nhánh Delay Combo (mốc 0.30s): Xoay người 360° quét văng toàn bộ kẻ địch xung quanh! */
    private static void performDelay360SpinPulse(ServerPlayer player, ServerLevel level, ItemStack stack) {
        double radius = 3.65;
        AABB box = player.getBoundingBox().inflate(radius, 1.5, radius);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e.isAttackable());
        float baseDmg = (float) Math.max(KatanaItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float dmg = baseDmg * KatanaMoveType.DELAY_SWEEP_360.damageMultiplier;
        boolean hitAny = false;

        for (LivingEntity target : candidates) {
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq > radius * radius) {
                continue;
            }
            target.invulnerableTime = 0;
            if (target.hurt(level.damageSources().playerAttack(player), dmg)) {
                hitAny = true;
                double dist = Math.max(0.1, Math.sqrt(distSq));
                target.push((dx / dist) * 0.72, 0.28, (dz / dist) * 0.72);
                target.hurtMarked = true;
                // Hất ngã / khựng quái nhỏ
                if (target.getBbHeight() < 2.2F) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 24, 2, false, false, false));
                }
                level.sendParticles(CYBER_CYAN,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        8, 0.25, 0.25, 0.25, 0.08);
            }
        }
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                ModSounds.KATANA_SLASH, SoundSource.PLAYERS, 1.25F, 1.48F);
        if (hitAny) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            ModNetworking.sendShakeNearby(level, player.position(), 12.0, 0.42F, 4);
        }
    }

    /** Chạm đất của Đòn Chốt Không Trung (`AIR_3_SLAM` — Bổ Thiên Thạch): Cắm kiếm xuống đất tạo AoE & đất đá văng! */
    private static void performAirSlamLanding(ServerPlayer player, ServerLevel level,
                                              PlayerKatanaState st, ItemStack stack) {
        st.airSlamDiving = false;
        st.airComboStep = 0;
        st.lastCombatTick = level.getGameTime();
        player.fallDistance = 0.0F;

        Vec3 impactPos = player.position();
        double radius = 3.85;
        AABB box = player.getBoundingBox().inflate(radius, 1.8, radius);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e.isAttackable());
        float baseDmg = (float) Math.max(KatanaItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float dmg = baseDmg * KatanaMoveType.AIR_3_SLAM.damageMultiplier;
        boolean hitAny = false;

        OVERRIDE_ARMOR_PIERCE.set(0.25F);
        try {
            for (LivingEntity target : candidates) {
                double dx = target.getX() - impactPos.x;
                double dz = target.getZ() - impactPos.z;
                double distSq = dx * dx + dz * dz;
                if (distSq > radius * radius) {
                    continue;
                }
                target.invulnerableTime = 0;
                if (target.hurt(level.damageSources().playerAttack(player), dmg)) {
                    hitAny = true;
                    double dist = Math.max(0.1, Math.sqrt(distSq));
                    target.push((dx / dist) * 0.65, 0.36, (dz / dist) * 0.65);
                    target.hurtMarked = true;
                }
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
        }
        if (hitAny) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }

        // Hạt đất đá văng xung quanh điểm cắm kiếm!
        BlockPos below = BlockPos.containing(impactPos.x, impactPos.y - 0.25, impactPos.z);
        BlockState groundState = level.getBlockState(below);
        if (groundState.isAir()) {
            groundState = Blocks.STONE.defaultBlockState();
        }
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, groundState);
        for (int i = 0; i < 24; i++) {
            double ang = (Mth.TWO_PI * i) / 24.0;
            double r = 0.8 + (i % 3) * 0.65;
            level.sendParticles(debris,
                    impactPos.x + Math.cos(ang) * r, impactPos.y + 0.15, impactPos.z + Math.sin(ang) * r,
                    2, 0.12, 0.20, 0.12, 0.22);
        }
        level.sendParticles(ParticleTypes.CLOUD, impactPos.x, impactPos.y + 0.15, impactPos.z, 18, 1.4, 0.15, 1.4, 0.12);
        level.sendParticles(CYBER_CYAN, impactPos.x, impactPos.y + 0.30, impactPos.z, 22, 1.6, 0.30, 1.6, 0.12);
        level.playSound(null, impactPos.x, impactPos.y, impactPos.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.85F, 1.35F);
        level.playSound(null, impactPos.x, impactPos.y, impactPos.z,
                ModSounds.KATANA_SLASH, SoundSource.PLAYERS, 1.25F, 0.72F);
        ModNetworking.sendShakeNearby(level, impactPos, 16.0, 0.85F, 6);

        ModNetworking.sendKatanaSyncNearby(level, impactPos, new KatanaSyncPayload(
                KatanaSyncPayload.EVENT_AIR_SLAM_IMPACT, player.getId(), (byte) KatanaMoveType.AIR_3_SLAM.id,
                6.0F, impactPos.x, impactPos.y, impactPos.z,
                player.getYRot(), player.getXRot(), true, 0.0F));
    }

    private static boolean performMoveHitbox(ServerPlayer player, ServerLevel level,
                                             KatanaMoveType move, @Nullable LivingEntity skipTarget) {
        Vec3 look = player.getLookAngle().normalize();
        double reach = switch (move) {
            case HIT_1_DRAW, HIT_4_FINISHER, PARRY_COUNTER -> 4.10;
            case DELAY_SWEEP_360 -> 3.60;
            case AIR_1_LAUNCH, AIR_2_MID_SWEEP -> 3.85;
            default -> 3.80;
        };
        Vec3 center = player.getEyePosition().add(look.scale(1.65));
        AABB box = new AABB(center, center).inflate(reach * 0.72);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e != skipTarget && e.isAlive() && e.isAttackable());
        if (candidates.isEmpty()) {
            return false;
        }
        float baseDmg = (float) Math.max(KatanaItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float scale = (move == KatanaMoveType.DELAY_SWEEP_360) ? 0.65F
                : ((move == KatanaMoveType.HIT_1_DRAW || move == KatanaMoveType.PARRY_COUNTER) ? 1.00F : 0.94F);
        float dmg = baseDmg * move.damageMultiplier * scale;
        double minDot = switch (move) {
            case HIT_1_DRAW, DELAY_SWEEP_360, AIR_2_MID_SWEEP -> 0.00;
            case HIT_2_DOWN_DIAG, HIT_3_UP_DIAG, AIR_1_LAUNCH, PARRY_COUNTER -> 0.12;
            default -> 0.24;
        };

        boolean any = false;
        float pierce = switch (move) {
            case PARRY_COUNTER -> 0.40F;
            case HIT_4_FINISHER -> 0.25F;
            default -> 0.0F;
        };
        if (pierce > 0.0F) {
            OVERRIDE_ARMOR_PIERCE.set(pierce);
        }
        try {
            for (LivingEntity target : candidates) {
                Vec3 toTarget = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(player.getEyePosition());
                if (toTarget.lengthSqr() > (reach + 0.55) * (reach + 0.55)) {
                    continue;
                }
                if (toTarget.normalize().dot(look) < minDot) {
                    continue;
                }
                if (move == KatanaMoveType.PARRY_COUNTER) {
                    target.invulnerableTime = 0;
                }
                if (target.hurt(level.damageSources().playerAttack(player), dmg)) {
                    any = true;
                    double kb = switch (move) {
                        case HIT_4_FINISHER, PARRY_COUNTER -> 0.65;
                        case HIT_1_DRAW -> 0.36;
                        case HIT_3_UP_DIAG -> 0.34;
                        default -> 0.28;
                    };
                    double liftY = (move == KatanaMoveType.HIT_3_UP_DIAG) ? 0.26
                            : (move == KatanaMoveType.HIT_4_FINISHER ? 0.18 : 0.08);
                    target.push(look.x * kb, liftY, look.z * kb);
                    applySpecialOnHitEffects(player, level, target, move);
                }
            }
        } finally {
            if (pierce > 0.0F) {
                OVERRIDE_ARMOR_PIERCE.remove();
            }
        }
        return any;
    }

    private static void executeSlashEffects(ServerPlayer player, ServerLevel level,
                                            KatanaMoveType move, boolean landedHit, boolean wasIaidoDraw) {
        float pitch = switch (move) {
            case HIT_1_DRAW -> 1.38F;
            case HIT_2_DOWN_DIAG -> 1.24F;
            case HIT_3_UP_DIAG -> 1.32F;
            case HIT_4_FINISHER -> 0.84F;
            case THRUST -> 1.56F;
            case DELAY_SWEEP_360 -> 1.30F;
            case AIR_1_LAUNCH -> 1.44F;
            case AIR_2_MID_SWEEP -> 1.34F;
            case AIR_3_SLAM -> 0.92F;
            case PARRY_COUNTER -> 1.62F;
            default -> 1.05F;
        };
        float vol = (move == KatanaMoveType.HIT_4_FINISHER || move == KatanaMoveType.PARRY_COUNTER
                || move == KatanaMoveType.THRUST || move == KatanaMoveType.HIT_1_DRAW) ? 1.20F : 0.98F;
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                ModSounds.KATANA_SLASH, SoundSource.PLAYERS, vol, pitch);

        Vec3 anchor = player.position().add(0.0, player.getBbHeight() * (move == KatanaMoveType.HIT_1_DRAW ? 0.66 : 0.60), 0.0);
        if (landedHit && (move == KatanaMoveType.HIT_4_FINISHER || move == KatanaMoveType.THRUST || move == KatanaMoveType.PARRY_COUNTER)) {
            float stopTicks = (move == KatanaMoveType.THRUST) ? 1.6F : 2.0F;
            ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                    KatanaSyncPayload.EVENT_HIT_STOP, player.getId(), (byte) move.id,
                    stopTicks, anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot(), true, 0.0F));
            ModNetworking.sendShakeNearby(level, anchor, 12.0, move == KatanaMoveType.PARRY_COUNTER ? 0.68F : 0.48F, 5);
        }
        ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                KatanaSyncPayload.EVENT_SLASH, player.getId(), (byte) move.id,
                move.baseDisplayTicks, anchor.x, anchor.y, anchor.z,
                player.getYRot(), player.getXRot(), true, wasIaidoDraw ? 1.0F : 0.0F));
    }

    private static void setDrawnState(ServerPlayer player, ServerLevel level, PlayerKatanaState st,
                                      ItemStack stack, boolean drawn, boolean playSound) {
        setDrawnState(player, level, st, stack, drawn, playSound, !drawn);
    }

    private static void setDrawnState(ServerPlayer player, ServerLevel level, PlayerKatanaState st,
                                      ItemStack stack, boolean drawn, boolean playSound, boolean playChiburiNoto) {
        boolean changed = (st.drawn != drawn);
        st.drawn = drawn;
        KatanaItem.setDrawn(stack, drawn);
        if (playSound && changed) {
            level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                    drawn ? ModSounds.KATANA_DRAW : ModSounds.KATANA_SHEATH,
                    SoundSource.PLAYERS, 1.05F, drawn ? 1.18F : 0.96F);
        }
        byte evt = drawn ? KatanaSyncPayload.EVENT_DRAW : KatanaSyncPayload.EVENT_SHEATH;
        ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                evt, player.getId(), (byte) 0, playChiburiNoto ? KatanaMoveType.CHIBURI_NOTO.baseDisplayTicks : 6.0F,
                player.getX(), player.getY() + 1.0, player.getZ(),
                player.getYRot(), player.getXRot(), drawn, playChiburiNoto ? 1.0F : 0.0F));
    }

    public static void tickServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            ItemStack held = player.getMainHandItem();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack invStack = player.getInventory().getItem(i);
                if (invStack != held && invStack.is(ModItems.KATANA) && KatanaItem.isDrawn(invStack)) {
                    KatanaItem.setDrawn(invStack, false);
                }
            }
            if (!isHoldingKatana(player)) {
                PlayerKatanaState prev = PLAYER_STATES.get(player.getUUID());
                if (prev != null) {
                    prev.iaidoCharging = false;
                    prev.airSlamDiving = false;
                    prev.delaySpin360Tick = -1000L;
                    prev.judgmentDetonateTick = -1000L;
                    if (prev.drawn) {
                        prev.drawn = false;
                        ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                                KatanaSyncPayload.EVENT_SHEATH, player.getId(), (byte) 0, 6.0F,
                                player.getX(), player.getY() + 1.0, player.getZ(),
                                player.getYRot(), player.getXRot(), false, 0.0F));
                    }
                }
                continue;
            }
            PlayerKatanaState st = stateOf(player, held);
            if (st.drawn && !KatanaItem.isDrawn(held)) {
                st.drawn = false;
                ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                        KatanaSyncPayload.EVENT_SHEATH, player.getId(), (byte) 0, 6.0F,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        player.getYRot(), player.getXRot(), false, 0.0F));
            }
            long now = level.getGameTime();

            // 0. Vệt bụi trượt dưới chân nhân vật khi bị đẩy lùi nhẹ 1 block sau Mune-oshi!
            if (now <= st.muneOshiSlideUntilTick) {
                BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.25, player.getZ());
                BlockState groundState = level.getBlockState(below);
                if (groundState.isAir()) {
                    groundState = Blocks.DIRT.defaultBlockState();
                }
                BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, groundState);
                level.sendParticles(dust, player.getX(), player.getY() + 0.08, player.getZ(), 4, 0.24, 0.05, 0.24, 0.10);
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.08, player.getZ(), 2, 0.18, 0.04, 0.18, 0.03);
            }

            // 1. Nhánh 2: Kích hoạt xung AoE xoay 360° tại mốc 0.30s sau khi tung Delay Combo!
            if (st.delaySpin360Tick > 0L && now >= st.delaySpin360Tick) {
                st.delaySpin360Tick = -1000L;
                performDelay360SpinPulse(player, level, held);
            }

            // 2. Nhánh 3: Kích nổ sát thương trễ Nhất Kiếm Tất Sát khi lưỡi chạm đáy vỏ kêu "tách" ở 1.05s!
            if (st.judgmentDetonateTick > 0L && now >= st.judgmentDetonateTick) {
                detonateIaidoJudgmentCut(player, level, st, held);
            }

            // 3. Nhánh 4: Theo dõi cú lao Bổ Thiên Thạch (`AIR_3_SLAM`) -> kích nổ AoE ngay khi chạm đất!
            if (st.airSlamDiving) {
                player.fallDistance = 0.0F;
                if (player.onGround() || (now - st.airSlamStartTick) > 36L) {
                    performAirSlamLanding(player, level, st, held);
                } else {
                    Vec3 cur = player.getDeltaMovement();
                    if (cur.y > -1.0) {
                        player.setDeltaMovement(cur.x, -1.2, cur.z);
                        player.hurtMarked = true;
                    }
                }
            } else if (player.onGround()) {
                st.airComboStep = 0;
            }

            // 4. Nhánh 3: Tụ lực Iaido trong vỏ khi giữ Chuột Phải lúc chưa rút kiếm!
            if (st.iaidoCharging) {
                if (!player.isUsingItem() || player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
                    st.iaidoCharging = false;
                    ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                            KatanaSyncPayload.EVENT_IAIDO_CHARGE, player.getId(), (byte) 0, 0.0F,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            player.getYRot(), player.getXRot(), false, 0.0F));
                } else {
                    st.lastCombatTick = now;
                    if (!st.iaidoReadyNotified && (now - st.iaidoChargeStartTick) >= IAIDO_CHARGE_READY_TICKS) {
                        st.iaidoReadyNotified = true;
                        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                                ModSounds.KATANA_PARRY, SoundSource.PLAYERS, 0.78F, 1.88F);
                        player.displayClientMessage(Component.literal(
                                "§b§l✦ NHẤT KIẾM TẤT SÁT — TỤ LỰC XONG! §7(Thả Chuột Phải để lướt xuyên mục tiêu)"), true);
                        ModNetworking.sendKatanaSyncNearby(level, player.position(), new KatanaSyncPayload(
                                KatanaSyncPayload.EVENT_IAIDO_READY, player.getId(), (byte) KatanaMoveType.IAIDO_BLINK.id,
                                6.0F, player.getX(), player.getY() + 1.0, player.getZ(),
                                player.getYRot(), player.getXRot(), false, 1.0F));
                    }
                }
                continue;
            }

            if (st.drawn && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                st.lastCombatTick = now;
                continue;
            }

            // Tự vẩy máu Chiburi & tra kiếm vào vỏ sau 1.8s ngừng đánh
            if (st.drawn && st.delaySpin360Tick < 0L && !st.airSlamDiving
                    && (now - st.lastCombatTick) >= AUTO_SHEATH_IDLE_TICKS) {
                setDrawnState(player, level, st, held, false, false, true);
            }
        }
    }
}
