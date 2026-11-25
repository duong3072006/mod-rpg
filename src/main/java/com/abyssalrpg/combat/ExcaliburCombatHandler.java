package com.abyssalrpg.combat;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.item.ExcaliburItem;
import com.abyssalrpg.network.ExcaliburActionC2SPayload;
import com.abyssalrpg.network.ExcaliburSyncPayload;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModItems;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trung tâm chiến đấu Server-authoritative cho **Excalibur & Excalibur Morgan**:
 * 1. **3-Hit Knight Combo (Golden / Morgan):**
 *    - Hit 1 (0.35s): Diagonal Cleave + crescent slash trail (1.8 blocks ahead).
 *    - Hit 2 (0.35s): Horizontal Knight Sweep + torso twist.
 *    - Hit 3 (0.45s): Heavy Overhead Judgment Slam + 0.08s hit-stop + AoE shockwave!
 * 2. **Normal Ultimate (RMB — 'EXCALIBUR!'):**
 *    - 0.65s Two-Handed Skyward Charge + 0.10s Apex Flash + Vertical Overhead Slam firing a 14-block Golden Holy Beam!
 * 3. **4.0s Dark Mana Charge & 'Excalibur Morgan' Awakening ([R], 20s Duration, 35s Cooldown):**
 *    - 4.0s (80 ticks) channel with smooth Action Bar progress (`§5[Charging Dark Mana: X%...]`) + rising hum.
 *    - At 4.0s: thunderous dark explosion, Instant Heal 40% Max HP + Regeneration II (5s),
 *      +40% Damage Surge, Resistance II (20s), 100% Knockback Resistance, and cleanse/block Poison, Wither, Slowness, Weakness!
 *    - Morgan RMB ('EXCALIBUR MORGAN!'): massive 18-block pitch-black & dark-purple beam, wider AoE, heavy knockback,
 *      scorched ground particles, Wither II + Slowness II on hit!
 */
public final class ExcaliburCombatHandler {
    public static final int CHARGE_DURATION_TICKS = 80;     // 4.0 seconds
    public static final int MORGAN_DURATION_TICKS = 400;    // 20.0 seconds
    public static final int MORGAN_COOLDOWN_TICKS = 700;    // 35.0 seconds
    public static final int ULT_COOLDOWN_TICKS = 160;       // 8.0 seconds
    public static final int ULT_BEAM_FIRE_TICK = 15;        // 0.65s charge + 0.10s apex flash -> fire at 0.75s!
    public static final int ULT_TOTAL_TICKS = 24;           // 1.20s total including follow-through hold
    public static final float GOLDEN_BEAM_RANGE = 14.0F;
    public static final float MORGAN_BEAM_RANGE = 18.0F;
    private static final int COMBO_RESET_TICKS = 44;

    private static final DustParticleOptions GOLD_SHOCK =
            new DustParticleOptions(new Vector3f(1.00F, 0.86F, 0.28F), 1.55F);
    private static final DustParticleOptions WHITE_HOLY =
            new DustParticleOptions(new Vector3f(1.00F, 0.99F, 0.90F), 1.40F);
    private static final DustParticleOptions MORGAN_VOID =
            new DustParticleOptions(new Vector3f(0.10F, 0.03F, 0.06F), 1.65F);
    private static final DustParticleOptions MORGAN_CRIMSON =
            new DustParticleOptions(new Vector3f(0.98F, 0.12F, 0.18F), 1.55F);
    private static final DustParticleOptions MORGAN_BEAM_PURPLE =
            new DustParticleOptions(new Vector3f(0.70F, 0.18F, 1.00F), 1.65F);

    private static final Map<UUID, PlayerExcaliburState> PLAYER_STATES = new HashMap<>();
    private static final Map<UUID, ActiveUltimateCast> ACTIVE_ULTIMATES = new HashMap<>();
    private static final Map<UUID, ActiveMorganChannel> ACTIVE_CHANNELS = new HashMap<>();
    private static final ThreadLocal<ActiveHitContext> ACTIVE_HIT = new ThreadLocal<>();

    private ExcaliburCombatHandler() {}

    private static final class PlayerExcaliburState {
        int nextComboStep = 0;
        long lastSwingTick = -1000L;
        float energy = -1.0F; // -1 = chưa khởi tạo từ ItemStack
    }

    private static float getOrInitEnergy(ServerPlayer player, ItemStack stack) {
        PlayerExcaliburState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerExcaliburState());
        if (st.energy < 0.0F) {
            st.energy = ExcaliburItem.getEnergy(stack);
        }
        return st.energy;
    }

    private static void setAndSyncEnergy(ServerPlayer player, ServerLevel level, ItemStack stack, float value, boolean persistToStack) {
        PlayerExcaliburState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerExcaliburState());
        float clamped = Mth.clamp(value, 0.0F, 100.0F);
        boolean changed = Math.abs(st.energy - clamped) >= 0.05F;
        st.energy = clamped;
        if (persistToStack) {
            ExcaliburItem.setEnergy(stack, clamped);
        }
        if (changed || persistToStack) {
            // Đồng bộ thanh năng lượng thuần mạng (0 ghi đè ItemStack khi đứng yên -> 0 bao giờ giật tay/tụt kiếm!)
            ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                    ExcaliburSyncPayload.EVENT_ENERGY_SYNC, player.getId(), (byte) 0, ExcaliburItem.isMorgan(stack),
                    0.0F, player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(), 0.0F, clamped / 100.0F));
        }
    }

    private static final class ActiveUltimateCast {
        final ServerPlayer player;
        final ServerLevel level;
        final boolean morgan;
        int tick = 0;
        boolean fired = false;

        ActiveUltimateCast(ServerPlayer player, ServerLevel level, boolean morgan) {
            this.player = player;
            this.level = level;
            this.morgan = morgan;
        }
    }

    private static final class ActiveMorganChannel {
        final ServerPlayer player;
        final ServerLevel level;
        int tick = 0;

        ActiveMorganChannel(ServerPlayer player, ServerLevel level) {
            this.player = player;
            this.level = level;
        }
    }

    public static final class ActiveHitContext {
        public final Player player;
        public final Entity rawTarget;
        @Nullable
        public final LivingEntity realTarget;
        public final ItemStack stack;
        public final boolean morgan;
        public final ExcaliburMoveType moveType;
        public boolean hitLanded;

        ActiveHitContext(Player player, Entity rawTarget, @Nullable LivingEntity realTarget,
                         ItemStack stack, boolean morgan, ExcaliburMoveType moveType) {
            this.player = player;
            this.rawTarget = rawTarget;
            this.realTarget = realTarget;
            this.stack = stack;
            this.morgan = morgan;
            this.moveType = moveType;
        }
    }

    public static boolean isHoldingExcalibur(@Nullable LivingEntity entity) {
        return entity != null && entity.getMainHandItem().is(ModItems.EXCALIBUR);
    }

    public static boolean isActionLocked(UUID playerId) {
        return ACTIVE_ULTIMATES.containsKey(playerId) || ACTIVE_CHANNELS.containsKey(playerId);
    }

    /** Khởi tạo đòn chém cận chiến từ PlayerMixin. */
    @Nullable
    public static ActiveHitContext beginPlayerAttack(Player player, Entity target) {
        if (player.level().isClientSide || !isHoldingExcalibur(player)) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        boolean morgan = ExcaliburItem.isMorgan(stack);
        PlayerExcaliburState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerExcaliburState());
        long now = player.level().getGameTime();
        if (now - st.lastSwingTick > COMBO_RESET_TICKS) {
            st.nextComboStep = 0;
        }
        ExcaliburMoveType move = ExcaliburMoveType.comboByStep(st.nextComboStep);
        st.nextComboStep = (st.nextComboStep + 1) % 3;
        st.lastSwingTick = now;

        LivingEntity living = (target instanceof LivingEntity le) ? le : null;
        ActiveHitContext ctx = new ActiveHitContext(player, target, living, stack, morgan, move);
        ACTIVE_HIT.set(ctx);
        return ctx;
    }

    public static float modifyAttackDamage(float amount) {
        ActiveHitContext ctx = ACTIVE_HIT.get();
        if (ctx == null) {
            return amount;
        }
        // ItemAttributeModifiers already applies +40% base damage in Morgan form (36.0 -> 50.4).
        // Scale by combo multiplier (Hit 1: 1.00x, Hit 2: 1.10x, Hit 3: 1.35x).
        return amount * ctx.moveType.damageMultiplier;
    }

    public static void finishPlayerAttack(ActiveHitContext ctx) {
        ACTIVE_HIT.remove();
        if (!(ctx.player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) {
            return;
        }
        executeSlashEffects(sp, level, ctx.moveType, ctx.morgan, ctx.realTarget != null && ctx.realTarget.hurtTime > 0);
    }

    /** Nhận gói C2S từ Client (Air Slash, RMB Ultimate, [R] 4s Morgan Charge). */
    public static void handleActionPacket(ServerPlayer player, ExcaliburActionC2SPayload payload) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isHoldingExcalibur(player)) {
            cancelChannel(player, level, false);
            return;
        }
        switch (payload.actionId()) {
            case ExcaliburActionC2SPayload.ACTION_AIR_SLASH -> handleAirSlash(player, level);
            case ExcaliburActionC2SPayload.ACTION_ULTIMATE -> tryStartUltimate(player);
            case ExcaliburActionC2SPayload.ACTION_CHARGE_START -> tryToggleOrStartMorganCharge(player, level);
            case ExcaliburActionC2SPayload.ACTION_CHARGE_CANCEL -> cancelChannel(player, level, true);
            default -> {
            }
        }
    }

    private static void handleAirSlash(ServerPlayer player, ServerLevel level) {
        if (isActionLocked(player.getUUID())) {
            return;
        }
        long now = level.getGameTime();
        PlayerExcaliburState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerExcaliburState());
        if (now - st.lastSwingTick < 6L) {
            return;
        }
        if (now - st.lastSwingTick > COMBO_RESET_TICKS) {
            st.nextComboStep = 0;
        }
        ExcaliburMoveType move = ExcaliburMoveType.comboByStep(st.nextComboStep);
        st.nextComboStep = (st.nextComboStep + 1) % 3;
        st.lastSwingTick = now;

        boolean morgan = ExcaliburItem.isMorgan(player.getMainHandItem());
        player.swing(InteractionHand.MAIN_HAND, true);

        // Chém gió vẫn có tầm quét mặt cung phía trước 1.8..3.4 block cho cảm giác kiếm dài chân thực!
        boolean hitAny = performForwardArcCleave(player, level, move, morgan, null);
        executeSlashEffects(player, level, move, morgan, hitAny);
    }

    private static boolean performForwardArcCleave(ServerPlayer player, ServerLevel level,
                                                   ExcaliburMoveType move, boolean morgan,
                                                   @Nullable LivingEntity exclude) {
        Vec3 look = player.getLookAngle().normalize();
        double reach = morgan ? 4.2 : 3.6;
        Vec3 center = player.getEyePosition().add(look.scale(1.8));
        AABB box = new AABB(center, center).inflate(reach * 0.65);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e != exclude && e.isAlive() && e.isAttackable() && !e.isAlliedTo(player));
        if (candidates.isEmpty()) {
            return false;
        }
        float baseDmg = (float) (morgan ? ExcaliburItem.MORGAN_ATTACK_DAMAGE : ExcaliburItem.GOLDEN_ATTACK_DAMAGE);
        float dmg = baseDmg * move.damageMultiplier * (exclude != null ? 0.65F : 0.90F);
        boolean any = false;
        for (LivingEntity target : candidates) {
            Vec3 toTarget = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(player.getEyePosition());
            if (toTarget.lengthSqr() > (reach + 0.6) * (reach + 0.6)) {
                continue;
            }
            if (toTarget.normalize().dot(look) < (move == ExcaliburMoveType.COMBO_2 ? 0.05 : 0.25)) {
                continue;
            }
            if (target.hurt(level.damageSources().playerAttack(player), dmg)) {
                any = true;
                double kb = (move == ExcaliburMoveType.COMBO_3) ? 0.68 : 0.36;
                target.push(look.x * kb, move == ExcaliburMoveType.COMBO_3 ? 0.28 : 0.12, look.z * kb);
                if (morgan) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0, false, true));
                }
            }
        }
        return any;
    }

    private static void executeSlashEffects(ServerPlayer player, ServerLevel level,
                                            ExcaliburMoveType move, boolean morgan, boolean landedHit) {
        // Resonant holy-steel / dark-energy slash sound at exact strike keyframe!
        float pitch = switch (move) {
            case COMBO_1 -> morgan ? 0.78F : 1.12F;
            case COMBO_2 -> morgan ? 0.72F : 1.05F;
            default -> morgan ? 0.62F : 0.90F;
        };
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                ModSounds.EXCALIBUR_SLASH, SoundSource.PLAYERS, move == ExcaliburMoveType.COMBO_3 ? 1.20F : 0.95F, pitch);

        Vec3 look = player.getLookAngle().normalize();
        // Neo vệt chém ngay tâm vai/ngực người chơi (không đẩy rời ra 1.8 block phía trước!) để bám khít lưỡi kiếm!
        Vec3 anchor = player.position().add(0.0, player.getBbHeight() * 0.62, 0.0);

        // Hit 3 (Heavy Overhead Judgment Slam): AoE shockwave + 0.08s hit-stop impact!
        if (move == ExcaliburMoveType.COMBO_3) {
            boolean cleaveHit = performForwardArcCleave(player, level, move, morgan, null);
            landedHit = landedHit || cleaveHit;
            Vec3 slamPos = player.position().add(look.scale(2.1)).add(0.0, 0.15, 0.0);
            level.sendParticles(morgan ? MORGAN_VOID : GOLD_SHOCK,
                    slamPos.x, slamPos.y + 0.25, slamPos.z, 22, 1.35, 0.25, 1.35, 0.08);
            level.sendParticles(morgan ? MORGAN_CRIMSON : WHITE_HOLY,
                    slamPos.x, slamPos.y + 0.35, slamPos.z, 18, 1.10, 0.35, 1.10, 0.10);
            level.playSound(null, slamPos.x, slamPos.y, slamPos.z,
                    SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.55F, morgan ? 0.52F : 1.25F);
            ModNetworking.sendShakeNearby(level, slamPos, 16.0, landedHit ? 0.55F : 0.32F, 6);
            if (landedHit) {
                ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                        ExcaliburSyncPayload.EVENT_HIT_STOP, player.getId(), (byte) move.id, morgan,
                        1.6F, slamPos.x, slamPos.y, slamPos.z, player.getYRot(), player.getXRot(), 0.0F, 0.0F));
            }
        }

        // Tích năng lượng biến đổi dạng Morgan khi đánh trúng địch ở dạng Vàng (đồng bộ gói mạng mượt, 0 tụt tay!)
        if (!morgan && landedHit) {
            ItemStack main = player.getMainHandItem();
            if (main.is(ModItems.EXCALIBUR)) {
                float cur = getOrInitEnergy(player, main);
                setAndSyncEnergy(player, level, main, cur + (move == ExcaliburMoveType.COMBO_3 ? 22.0F : 14.0F), false);
            }
        }

        // Sync crescent slash trail & 3rd-person animation to all nearby clients!
        ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_SLASH, player.getId(), (byte) move.id, morgan,
                move.baseDisplayTicks, anchor.x, anchor.y, anchor.z,
                player.getYRot(), player.getXRot(), 1.8F, 0.0F));
    }

    /** Kích hoạt Tuyệt Kỹ Chuột Phải — 'EXCALIBUR!' (14 block) hoặc 'EXCALIBUR MORGAN!' (18 block). */
    public static void tryStartUltimate(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !isHoldingExcalibur(player)) {
            return;
        }
        if (isActionLocked(player.getUUID())) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        long now = level.getGameTime();
        long cdUntil = ExcaliburItem.getUltCooldownUntil(stack);
        if (now < cdUntil) {
            float rem = (cdUntil - now) / 20.0F;
            player.displayClientMessage(Component.literal(String.format("§e[Excalibur] Cooldown: %.1fs", rem)), true);
            return;
        }

        boolean morgan = ExcaliburItem.isMorgan(stack);
        ExcaliburItem.setUltCooldownUntil(stack, now + ULT_COOLDOWN_TICKS);
        ACTIVE_ULTIMATES.put(player.getUUID(), new ActiveUltimateCast(player, level, morgan));

        // Announce iconic Fate/stay night Noble Phantasm!
        player.displayClientMessage(
                Component.literal(morgan ? "§5§l✦ EXCALIBUR MORGAN!! ✦" : "§6§l✦ EXCALIBUR!! ✦"), true);

        // Rising charge hum during the 0.65s skyward raise!
        level.playSound(null, player.getX(), player.getY() + 1.4, player.getZ(),
                ModSounds.EXCALIBUR_CHARGE, SoundSource.PLAYERS, 1.35F, morgan ? 0.68F : 1.05F);

        ExcaliburMoveType ultMove = morgan ? ExcaliburMoveType.ULT_MORGAN : ExcaliburMoveType.ULT_EXCALIBUR;
        float range = morgan ? MORGAN_BEAM_RANGE : GOLDEN_BEAM_RANGE;
        ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_ULT_START, player.getId(), (byte) ultMove.id, morgan,
                ULT_TOTAL_TICKS, player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), range, 0.0F));
    }

    /** Bắt đầu (hoặc hủy nếu đang gồng) quá trình gồng 4.0s Dark Mana [R] để thức tỉnh Excalibur Morgan! */
    public static void tryToggleOrStartMorganCharge(ServerPlayer player, ServerLevel level) {
        if (ACTIVE_ULTIMATES.containsKey(player.getUUID())) {
            return;
        }
        if (ACTIVE_CHANNELS.containsKey(player.getUUID())) {
            cancelChannel(player, level, true);
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (ExcaliburItem.isMorgan(stack)) {
            long remTicks = Math.max(0L, ExcaliburItem.getMorganEndTick(stack) - level.getGameTime());
            player.displayClientMessage(Component.literal(
                    String.format("§c[Excalibur Morgan Active: %.1fs remaining]", remTicks / 20.0F)), true);
            return;
        }
        long now = level.getGameTime();
        long cdUntil = ExcaliburItem.getMorganCooldownUntil(stack);
        if (now < cdUntil) {
            float rem = (cdUntil - now) / 20.0F;
            player.displayClientMessage(Component.literal(
                    String.format("§c[Dark Mana Recharging: %.1fs]", rem)), true);
            return;
        }
        float energy = getOrInitEnergy(player, stack);
        if (energy < 99.5F && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.literal(
                    String.format("§6[Năng lượng biến đổi: %d%% / 100%%]", Math.round(energy))), true);
            return;
        }

        ACTIVE_CHANNELS.put(player.getUUID(), new ActiveMorganChannel(player, level));
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                ModSounds.EXCALIBUR_CHARGE, SoundSource.PLAYERS, 1.15F, 0.58F);
        player.displayClientMessage(Component.literal("§c[Đang gồng biến đổi Morgan: 0%...]"), true);

        ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_CHARGE_START, player.getId(), (byte) ExcaliburMoveType.MORGAN_CHANNEL.id,
                false, CHARGE_DURATION_TICKS, player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), 0.0F, 0.0F));
    }

    private static void cancelChannel(ServerPlayer player, ServerLevel level, boolean notify) {
        ActiveMorganChannel ch = ACTIVE_CHANNELS.remove(player.getUUID());
        if (ch != null) {
            if (notify) {
                player.displayClientMessage(Component.literal("§7[Dark Mana Channel Interrupted]"), true);
            }
            ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                    ExcaliburSyncPayload.EVENT_CHARGE_CANCEL, player.getId(), (byte) ExcaliburMoveType.MORGAN_CHANNEL.id,
                    false, 0.0F, player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(), 0.0F, 0.0F));
        }
    }

    /** Server tick chính: xử lý Ultimate Beams, 4s Morgan Charge, và 20s Morgan Buffs / Status Immunity / Revert. */
    public static void tickServer(MinecraftServer server) {
        // 1. Tick active RMB Ultimates ('EXCALIBUR!' / 'EXCALIBUR MORGAN!')
        Iterator<Map.Entry<UUID, ActiveUltimateCast>> ultIt = ACTIVE_ULTIMATES.entrySet().iterator();
        while (ultIt.hasNext()) {
            ActiveUltimateCast u = ultIt.next().getValue();
            ServerPlayer p = u.player;
            if (!p.isAlive() || p.isRemoved() || !isHoldingExcalibur(p)) {
                ultIt.remove();
                continue;
            }
            u.tick++;

            // Skyward charge particles (0.00s - 0.65s = ticks 1..13)
            if (u.tick <= 13) {
                if ((u.tick & 1) == 0) {
                    double r = 1.45 - (u.tick / 13.0) * 0.55;
                    for (int k = 0; k < 5; k++) {
                        double ang = (u.tick * 0.65) + k * (Math.PI * 2.0 / 5.0);
                        double px = p.getX() + Math.cos(ang) * r;
                        double pz = p.getZ() + Math.sin(ang) * r;
                        double py = p.getY() + 0.25 + (u.tick / 13.0) * 2.8;
                        u.level.sendParticles(u.morgan ? MORGAN_BEAM_PURPLE : GOLD_SHOCK, px, py, pz, 1, 0, 0.14, 0, 0.02);
                    }
                }
                if (u.tick == 12) {
                    // Apex gleam chime right before slam!
                    u.level.playSound(null, p.getX(), p.getY() + 2.2, p.getZ(),
                            SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.70F, u.morgan ? 0.58F : 1.48F);
                }
            }

            // Fire piercing Holy / Dark Energy Beam at exact overhead slam impact (tick 15 = 0.75s)!
            if (u.tick == ULT_BEAM_FIRE_TICK && !u.fired) {
                u.fired = true;
                fireExcaliburBeam(p, u.level, u.morgan);
            }

            if (u.tick >= ULT_TOTAL_TICKS) {
                ultIt.remove();
            }
        }

        // 2. Tick active 4.0s Dark Mana Channels ([R] 80 ticks)
        Iterator<Map.Entry<UUID, ActiveMorganChannel>> chIt = ACTIVE_CHANNELS.entrySet().iterator();
        while (chIt.hasNext()) {
            ActiveMorganChannel ch = chIt.next().getValue();
            ServerPlayer p = ch.player;
            if (!p.isAlive() || p.isRemoved() || !isHoldingExcalibur(p)) {
                chIt.remove();
                if (!p.isRemoved()) {
                    ModNetworking.sendExcaliburSyncNearby(ch.level, p.position(), new ExcaliburSyncPayload(
                            ExcaliburSyncPayload.EVENT_CHARGE_CANCEL, p.getId(), (byte) ExcaliburMoveType.MORGAN_CHANNEL.id,
                            false, 0.0F, p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(), 0.0F, 0.0F));
                }
                continue;
            }
            ch.tick++;
            float progress = Mth.clamp(ch.tick / (float) CHARGE_DURATION_TICKS, 0.0F, 1.0F);
            int pct = Math.min(100, Math.round(progress * 100.0F));

            // Smooth Action Bar percentage progress (`§c[Đang gồng biến đổi Morgan: 75%...]`)
            if ((ch.tick & 1) == 0 || ch.tick == CHARGE_DURATION_TICKS) {
                p.displayClientMessage(Component.literal("§c[Đang gồng biến đổi Morgan: " + pct + "%...]"), true);
            }

            // Rising ominous dark-mana hum every 14 ticks
            if (ch.tick == 16 || ch.tick == 32 || ch.tick == 48 || ch.tick == 64) {
                float pitch = 0.62F + progress * 0.62F;
                ch.level.playSound(null, p.getX(), p.getY() + 1.0, p.getZ(),
                        ModSounds.EXCALIBUR_CHARGE, SoundSource.PLAYERS, 1.15F, pitch);
            }

            // Swirling pitch-black & crimson-red vortex particles + client sync every 2 ticks
            if ((ch.tick & 1) == 0) {
                double radius = 2.1 - progress * 0.9;
                for (int k = 0; k < 4; k++) {
                    double ang = ch.tick * 0.35 + k * (Math.PI * 0.5);
                    double px = p.getX() + Math.cos(ang) * radius;
                    double pz = p.getZ() + Math.sin(ang) * radius;
                    double py = p.getY() + 0.25 + ((ch.tick + k * 7) % 24) * 0.085;
                    ch.level.sendParticles((k & 1) == 0 ? MORGAN_VOID : MORGAN_CRIMSON, px, py, pz, 1, 0, 0.06, 0, 0.02);
                }
                ModNetworking.sendExcaliburSyncNearby(ch.level, p.position(), new ExcaliburSyncPayload(
                        ExcaliburSyncPayload.EVENT_CHARGE_TICK, p.getId(), (byte) ExcaliburMoveType.MORGAN_CHANNEL.id,
                        false, CHARGE_DURATION_TICKS, p.getX(), p.getY(), p.getZ(),
                        p.getYRot(), p.getXRot(), 0.0F, progress));
            }

            // At 4.0 seconds (tick 80): AWAKEN EXCALIBUR MORGAN!
            if (ch.tick >= CHARGE_DURATION_TICKS) {
                chIt.remove();
                awakenExcaliburMorgan(p, ch.level);
            }
        }

        // 3. Tick active Morgan forms across all players (Status Immunity, Buffs, & 20s Auto-Revert + 35s Cooldown)
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            long now = level.getGameTime();
            boolean holdingActiveMorgan = false;

            // Scan inventory so Morgan ALWAYS cleanly reverts after 20s even if moved off mainhand!
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack st = player.getInventory().getItem(i);
                if (!st.is(ModItems.EXCALIBUR) || !ExcaliburItem.isMorgan(st)) {
                    continue;
                }
                long endTick = ExcaliburItem.getMorganEndTick(st);
                if (now >= endTick) {
                    // Revert smoothly to Golden Excalibur + apply 35-second cooldown!
                    ExcaliburItem.setMorganState(st, false, 0L, now + MORGAN_COOLDOWN_TICKS);
                    player.displayClientMessage(
                            Component.literal("§e[Excalibur] Reverted to Golden Form (35s Cooldown)"), true);
                    level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.90F, 1.15F);
                    ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                            ExcaliburSyncPayload.EVENT_MORGAN_REVERT, player.getId(), (byte) 0, false,
                            0.0F, player.getX(), player.getY(), player.getZ(),
                            player.getYRot(), player.getXRot(), 0.0F, 0.0F));
                } else if (st == player.getMainHandItem()) {
                    holdingActiveMorgan = true;
                }
            }

            if (holdingActiveMorgan) {
                // Ironclad Status Immunity: cleanse & block Poison, Wither, Slowness, Weakness continuously!
                player.removeEffect(MobEffects.POISON);
                player.removeEffect(MobEffects.WITHER);
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                player.removeEffect(MobEffects.WEAKNESS);
                // Maintain Resistance II (40% damage reduction) cleanly while active!
                MobEffectInstance res = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
                if (res == null || res.getAmplifier() < 1 || res.getDuration() < 15) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 1, false, false, true));
                }
            } else if ((now % 10L) == 0L && isHoldingExcalibur(player)) {
                // Hồi năng lượng biến đổi thụ động (+2.5% mỗi 0.5s) chỉ gửi gói mạng nhẹ -> 0 sửa ItemStack -> kiếm ĐỨNG IM 100%!
                ItemStack held = player.getMainHandItem();
                if (!ExcaliburItem.isMorgan(held) && now >= ExcaliburItem.getMorganCooldownUntil(held)) {
                    float cur = getOrInitEnergy(player, held);
                    if (cur < 100.0F) {
                        setAndSyncEnergy(player, level, held, cur + 2.5F, false);
                    }
                }
            }
        }
    }

    /** Bùng nổ Hắc Ám Đỏ Thẫm tại mốc 4.0s — Thức tỉnh Excalibur Morgan (20s) cùng toàn bộ Buff Chiến Thần! */
    private static void awakenExcaliburMorgan(ServerPlayer player, ServerLevel level) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.EXCALIBUR)) {
            return;
        }
        long now = level.getGameTime();
        setAndSyncEnergy(player, level, stack, 0.0F, true);
        ExcaliburItem.setMorganState(stack, true, now + MORGAN_DURATION_TICKS, 0L);

        // 1. Instant Heal 40% Max Health + Regeneration II for 5 seconds (100 ticks)!
        float healAmt = player.getMaxHealth() * 0.40F;
        player.heal(healAmt);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, true, true));

        // 2. Ironclad Resistance II (40% damage reduction) for full 20 seconds (400 ticks) + cleanse negative effects!
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MORGAN_DURATION_TICKS, 1, false, true, true));
        player.removeEffect(MobEffects.POISON);
        player.removeEffect(MobEffects.WITHER);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.WEAKNESS);

        // 3. Thunderous dark explosion & crimson-red shockwave knocking back surrounding foes!
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                ModSounds.MORGAN_TRANSFORM, SoundSource.PLAYERS, 1.55F, 0.82F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
                36, 1.4, 0.9, 1.4, 0.14);
        level.sendParticles(MORGAN_VOID, player.getX(), player.getY() + 1.1, player.getZ(),
                40, 1.6, 1.0, 1.6, 0.12);
        level.sendParticles(MORGAN_CRIMSON, player.getX(), player.getY() + 1.1, player.getZ(),
                44, 1.6, 1.1, 1.6, 0.14);

        AABB blastBox = player.getBoundingBox().inflate(5.0);
        for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, blastBox,
                e -> e != player && e.isAlive() && e.isAttackable() && !e.isAlliedTo(player))) {
            Vec3 away = foe.position().subtract(player.position());
            Vec3 dir = away.lengthSqr() > 1e-4 ? away.normalize() : new Vec3(0, 0, 1);
            foe.hurt(level.damageSources().magic(), 20.0F);
            foe.push(dir.x * 1.15, 0.42, dir.z * 1.15);
            foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }

        ModNetworking.sendShakeNearby(level, player.position(), 24.0, 1.05F, 12);
        player.displayClientMessage(Component.literal("§c§l✦ EXCALIBUR MORGAN AWAKENED! (20s) ✦"), true);

        ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_MORGAN_AWAKEN, player.getId(), (byte) ExcaliburMoveType.MORGAN_AWAKEN.id,
                true, ExcaliburMoveType.MORGAN_AWAKEN.baseDisplayTicks,
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), 5.0F, 1.0F));
    }

    /** Khai hỏa luồng sáng chém dọc TỪ TRỜI XUỐNG ĐẤT: Vàng Thánh Quang (18 block) hoặc Tím Hắc Ám Morgan (22 block)! */
    private static void fireExcaliburBeam(ServerPlayer player, ServerLevel level, boolean morgan) {
        // Bám phương ngang phía trước để luồng sáng chém dọc từ bầu trời giáng xuống dọc mặt đất cực kỳ hoành tráng!
        Vec3 flatLook = Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
        Vec3 origin = player.position().add(0.0, 1.15, 0.0).add(flatLook.scale(1.2));
        float range = morgan ? 22.0F : 18.0F;
        double halfRadius = morgan ? 2.75 : 2.10;
        float damage = morgan ? 132.0F : 92.0F;
        double knockback = morgan ? 1.55 : 1.05;

        // Massive echoing sky-cleave blast SFX!
        level.playSound(null, origin.x, origin.y, origin.z,
                ModSounds.EXCALIBUR_BEAM, SoundSource.PLAYERS, 1.75F, morgan ? 0.68F : 1.04F);
        ModNetworking.sendShakeNearby(level, origin, 28.0, morgan ? 1.25F : 0.90F, 12);

        // Piercing vertical sky-slash corridor along the full 18-block / 22-block path!
        Set<UUID> alreadyHit = new HashSet<>();
        int steps = (int) Math.ceil(range * 1.4);
        for (int s = 1; s <= steps; s++) {
            double dist = (s / (double) steps) * range;
            Vec3 pt = origin.add(flatLook.scale(dist));

            // Cột hạt ánh sáng Vàng (Excalibur) / Tím (Excalibur Morgan) giáng thẳng từ trời xuống mặt đất!
            if ((s & 1) == 0) {
                for (double skyY = 0.0; skyY <= 16.0; skyY += 4.0) {
                    level.sendParticles(morgan ? MORGAN_BEAM_PURPLE : GOLD_SHOCK,
                            pt.x, player.getY() + 0.4 + skyY, pt.z, morgan ? 4 : 4, 0.55, 1.2, 0.55, 0.04);
                    level.sendParticles(morgan ? MORGAN_VOID : WHITE_HOLY,
                            pt.x, player.getY() + 0.6 + skyY, pt.z, 3, 0.40, 1.1, 0.40, 0.05);
                }
                if (morgan) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, pt.x, player.getY() + 0.14, pt.z,
                            2, 0.75, 0.08, 0.75, 0.03);
                    level.sendParticles(ParticleTypes.DRAGON_BREATH, pt.x, player.getY() + 0.20, pt.z,
                            2, 0.75, 0.12, 0.75, 0.02);
                } else {
                    level.sendParticles(ParticleTypes.END_ROD, pt.x, player.getY() + 1.2, pt.z, 3, 0.55, 2.2, 0.55, 0.04);
                }
            }

            AABB sliceBox = new AABB(
                    pt.x - halfRadius, player.getY() - 1.5, pt.z - halfRadius,
                    pt.x + halfRadius, player.getY() + 10.0, pt.z + halfRadius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sliceBox,
                    e -> e != player && e.isAlive() && e.isAttackable() && !e.isAlliedTo(player))) {
                if (!alreadyHit.add(target.getUUID())) {
                    continue;
                }
                target.hurt(level.damageSources().playerAttack(player), damage);
                target.push(flatLook.x * knockback, morgan ? 0.52 : 0.38, flatLook.z * knockback);
                if (morgan) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1, false, true));
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, true));
                } else {
                    target.igniteForSeconds(3);
                }
            }
        }
        if (!morgan && !alreadyHit.isEmpty()) {
            ItemStack main = player.getMainHandItem();
            if (main.is(ModItems.EXCALIBUR)) {
                setAndSyncEnergy(player, level, main, getOrInitEnergy(player, main) + 25.0F, false);
            }
        }

        // Sync massive 3D Sky-to-Ground Light Slash Beam to all nearby clients!
        ExcaliburMoveType ultMove = morgan ? ExcaliburMoveType.ULT_MORGAN : ExcaliburMoveType.ULT_EXCALIBUR;
        ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_BEAM_FIRE, player.getId(), (byte) ultMove.id, morgan,
                18.0F, origin.x, origin.y, origin.z,
                player.getYRot(), 0.0F, range, 1.0F));
    }
}
