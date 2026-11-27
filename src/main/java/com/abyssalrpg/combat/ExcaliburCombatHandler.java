package com.abyssalrpg.combat;

import com.abyssalrpg.item.ExcaliburItem;
import com.abyssalrpg.network.ExcaliburActionC2SPayload;
import com.abyssalrpg.network.ExcaliburPassiveSyncPayload;
import com.abyssalrpg.network.ExcaliburSyncPayload;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModItems;
import com.abyssalrpg.registry.ModSounds;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trung tâm chiến đấu **Royal Swordplay + Vòng Phép Fairy Tail 2D** cho **Excalibur**:
 * - **Chuỗi 4 đòn thường:** `HIT_1_SWEEP` (0.35s) → `HIT_2_RISING` (0.35s) → `HIT_3_THRUST` (0.40s) → `HIT_4_SLAM` (0.50s).
 * - **3 Nhánh Combo Mở Rộng (Tối giản VFX — không khói bụi):**
 *   1. **Strike Air Thrust (`STRIKE_AIR`):** Hold Attack sau Hit 1/2 → Lấy đà (0.20s) + Đâm bộc phá (dash 0.6b) phóng 1 vòng xung kích bay 5 blocks, đẩy lùi & phá khiên.
 *   2. **Golden Crescent Wave (`GOLDEN_CRESCENT`):** Delay Combo (Hit 1 → Đợi 0.30s → Đánh) → Quét tròn 360° (0.28s) + Bổ phóng 1 lưỡi liềm vàng mảnh trượt mặt đất 7 blocks.
 *   3. **Aerial Judgment (`AIR_1_DIAG` → `AIR_2_DIAG` → `AIR_3_FALLING_STAR`):** 2 nhịp chém chéo giữ độ cao (`motionY = 0.05`) → Chĩa mũi kiếm xuống (`Pitch -10°`) lao cắm đất (`motionY = -1.3`) bật 1 vòng phép 2D vàng 0.3s.
 * - **Hệ thống Đỡ Kiếm & Phản Đòn Hoàng Gia (Guard & Royal Counter):**
 *   - Đỡ Trực Diện (`Hold RMB`): Giảm `70%` sát thương phía trước.
 *   - Đỡ Trên Cao (`Hold RMB + Pitch < -30°`): Chặn `100%` đòn bổ từ trên cao.
 *   - **Royal Parry Counter (`<= 0.18s` / `4 ticks` trước khi trúng đòn):**
 *     Nhịp 1 (`0.00–0.15s`) gạt chệch `+35°` lóe vòng phép vàng 2D + tiếng "Klang!" → Nhịp 2 (`0.15–0.40s`) tự động vặn ngược `-40°` chém ngang gây `200%` sát thương & làm choáng `0.8s` (`16 ticks`)!
 * - **Tuyệt kỹ `[Chuột Phải]` (đủ `100/100` năng lượng):**
 *   GĐ 1 (`0.00–0.90s` / 0–18t) tụ lực + vòng phép 2D dưới chân → GĐ 2 (`0.90–1.20s` / 18–24t) ngả kiếm `-125°` mở rộng vòng phép `+20%`
 *   → GĐ 3 (`1.20–2.30s` / 24–46t) bổ ngang `-85°` + 3 vòng phép đứng dọc trục bắn chùm tia vàng lõi trắng `20 blocks` (`1500%` sát thương chuẩn) → Thu thế (`2.30–2.60s` / 46–52t).
 */
public final class ExcaliburCombatHandler {
    public static final int ULT_TILT_START_TICK = 18;      // 0.90s
    public static final int ULT_CLEAVE_START_TICK = 24;    // 1.20s
    public static final int ULT_BEAM_FIRE_TICK = 27;       // 1.35s
    public static final int ULT_BEAM_END_TICK = 46;        // 2.30s
    public static final int ULT_TOTAL_TICKS = 52;          // 2.60s
    public static final float BEAM_RANGE = 20.0F;
    public static final float BEAM_START_HALF_W = 0.50F;
    public static final float BEAM_START_HALF_H = 0.60F;
    public static final float BEAM_END_HALF_W = 1.95F;
    public static final float BEAM_END_HALF_H = 2.35F;
    public static final float ULT_DAMAGE_MULTIPLIER = 15.0F;
    public static final int FULL_ENERGY_STRENGTH_AMP = 3;
    public static final int FULL_ENERGY_STRENGTH_TICKS = 80;
    public static final int ULT_COOLDOWN_TICKS = 500;      // Hồi chiêu 25 giây sau khi dùng Tuyệt kỹ Excalibur!

    public static final long DELAY_CRESCENT_MIN_TICKS = 7L;   // 0.30s+ sau Hit 1
    public static final long DELAY_CRESCENT_MAX_TICKS = 26L;
    public static final long PERFECT_PARRY_WINDOW_TICKS = 4L; // 0.18s–0.20s cửa sổ Royal Parry Counter!
    public static final int PARRY_STUN_TICKS = 16;            // 0.80s làm choáng mục tiêu ở Nhịp 2!
    public static final int AVALON_PARRY_WINDOW_TICKS = 4;    // 0.20s (4 ticks) cửa sổ Parry bằng vỏ Avalon khi kiếm trong vỏ!
    public static final int AVALON_PARRY_STUN_TICKS = 20;     // 1.00s làm choáng khi rút kiếm phản công từ vỏ Avalon!
    public static final float AVALON_PARRY_ENERGY_GAIN = 15.0F; // Hồi +15 Năng Lượng khi Parry & Rút kiếm phản công!
    public static final long AUTO_SHEATHE_IDLE_TICKS = 80L;   // 4.0s không đánh/đỡ -> tự động tra kiếm vào vỏ Avalon!
    public static final long SHEATHE_CLINK_DELAY_TICKS = 8L;  // Mốc 0.40s: phát tiếng "Clink!" + 3 đốm sáng vàng tại miệng vỏ!
    public static final long SHEATHE_TOTAL_TICKS = 11L;       // Tổng thời lượng animation cất kiếm: 0.55s (11 ticks)
    private static final int COMBO_RESET_TICKS = 36;

    private static final DustParticleOptions GOLD_SPARK =
            new DustParticleOptions(new Vector3f(1.00F, 0.90F, 0.40F), 0.95F);
    private static final DustParticleOptions WHITE_HOLY =
            new DustParticleOptions(new Vector3f(1.00F, 0.99F, 0.92F), 0.88F);
    private static final ProjectileDeflection CLIENT_ARROW_DEFLECT_SENTINEL = (proj, deflectedBy, random) -> {};

    private static final Map<UUID, PlayerExcaliburState> PLAYER_STATES = new HashMap<>();
    private static final Map<UUID, ActiveUltimateCast> ACTIVE_ULTIMATES = new HashMap<>();
    private static final ThreadLocal<ActiveHitContext> ACTIVE_HIT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> TRUE_DAMAGE_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> SHIELD_BREAK_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Float> OVERRIDE_ARMOR_PIERCE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REENTRANT_GUARD_DAMAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ExcaliburCombatHandler() {}

    private static final class PlayerExcaliburState {
        int nextComboStep = 0;
        ExcaliburMoveType lastMove = ExcaliburMoveType.HIT_1_SWEEP;
        long lastSwingTick = -1000L;
        long lastCombatTick = -1000L;
        long lastDishonorTick = -10000L;

        float energy = -1.0F;
        long lastDamagedEnemyTick = -100L;
        long lastKillEnergyTick = -100L;

        int sealMask = 0;
        int unlockedSeals = 0;
        boolean wasUnsealed = false;
        int ultCooldownTicks = 0;
        boolean ultCooldownInit = false;
        boolean appliedFullEnergyStrength = false;

        // 3 Nhánh Combo Mở Rộng + Royal Parry Counter 2 nhịp tự động
        long strikeAirFireTick = -1000L;       // Nhánh A: Giải phóng vòng xung kích 5 blocks ở 0.20s (Tick 4)
        long crescentWaveFireTick = -1000L;    // Nhánh B: Phóng lưỡi liềm vàng mảnh 7 blocks ở 0.40s (Tick 8)
        int airComboStep = 0;                  // Nhánh C: 0 -> Air1, 1 -> Air2, 2 -> Air3 Plunge
        long lastAirHitTick = -1000L;
        long airHoverUntilTick = -1000L;
        boolean fallingStarDiving = false;
        long fallingStarStartTick = -1000L;
        long guardStartTick = -1000L;
        long lastRoyalParryTick = -1000L;
        long royalCounterBeat2Tick = -1000L;   // Nhịp 2 (0.15s = Tick 3): Tự động chém ngang 200% ST & choáng 0.8s!
        int royalParryAttackerId = -1;

        long lastPassiveSyncTick = -100L;
        int lastSyncedHash = 0;
        byte lastBroadcastVisualFlags = -1;

        boolean unsheathed = false;
        boolean unsheathedInit = false;
        long lastActiveCombatOrGuardTick = -1000L;
        long sheatheClinkTick = -1000L;
        long sheatheEndTick = -1000L;
        int sheathedParryTicks = 0;
        long lastAvalonParryTick = -1000L;
    }

    private static final class ActiveUltimateCast {
        final ServerPlayer player;
        final ServerLevel level;
        final float lockedBaseAttack;
        final ItemStack weaponSnapshot;
        int tick = 0;
        boolean fired = false;

        ActiveUltimateCast(ServerPlayer player, ServerLevel level, float lockedBaseAttack, ItemStack weaponSnapshot) {
            this.player = player;
            this.level = level;
            this.lockedBaseAttack = lockedBaseAttack;
            this.weaponSnapshot = weaponSnapshot;
        }
    }

    public static final class ActiveHitContext {
        public final Player player;
        public final Entity rawTarget;
        @Nullable
        public final LivingEntity realTarget;
        public final ItemStack stack;
        public final ExcaliburMoveType moveType;

        ActiveHitContext(Player player, Entity rawTarget, @Nullable LivingEntity realTarget,
                         ItemStack stack, ExcaliburMoveType moveType) {
            this.player = player;
            this.rawTarget = rawTarget;
            this.realTarget = realTarget;
            this.stack = stack;
            this.moveType = moveType;
        }
    }

    public static boolean isHoldingExcalibur(@Nullable LivingEntity entity) {
        return entity != null && entity.getMainHandItem().is(ModItems.EXCALIBUR);
    }

    public static boolean isActionLocked(UUID playerId) {
        return ACTIVE_ULTIMATES.containsKey(playerId);
    }

    public static boolean isTrueDamageActive() {
        return Boolean.TRUE.equals(TRUE_DAMAGE_ACTIVE.get());
    }

    public static boolean isShieldBreakingActive() {
        return Boolean.TRUE.equals(SHIELD_BREAK_ACTIVE.get());
    }

    public static boolean isFallingStarFallImmune(@Nullable LivingEntity entity, @Nullable DamageSource source) {
        if (!(entity instanceof ServerPlayer sp) || source == null || !source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        PlayerExcaliburState st = PLAYER_STATES.get(sp.getUUID());
        return st != null && st.fallingStarDiving;
    }

    public static float getActiveArmorPierceFraction(@Nullable LivingEntity victim, @Nullable DamageSource source) {
        if (isTrueDamageActive()) {
            return 1.0F;
        }
        Float ov = OVERRIDE_ARMOR_PIERCE.get();
        if (ov != null && ov > 0.0F) {
            return ov;
        }
        ActiveHitContext hit = ACTIVE_HIT.get();
        if (hit != null && (hit.moveType == ExcaliburMoveType.ROYAL_PARRY_COUNTER
                || hit.moveType == ExcaliburMoveType.AVALON_PARRY_COUNTER)) {
            return 0.40F;
        }
        return 0.0F;
    }

    public static boolean canCastUltimate(ServerPlayer player) {
        if (!isHoldingExcalibur(player) || isActionLocked(player.getUUID())) {
            return false;
        }
        PlayerExcaliburState st = stateOf(player, player.getMainHandItem());
        return st.ultCooldownTicks <= 0 && st.energy >= ExcaliburItem.MAX_ENERGY - 0.01F;
    }

    public static boolean hasFullEnergy(ServerPlayer player) {
        if (!isHoldingExcalibur(player)) {
            return false;
        }
        PlayerExcaliburState st = stateOf(player, player.getMainHandItem());
        return st.energy >= ExcaliburItem.MAX_ENERGY - 0.01F;
    }

    public static int getUltimateCooldownTicks(ServerPlayer player) {
        if (!isHoldingExcalibur(player)) {
            return 0;
        }
        PlayerExcaliburState st = stateOf(player, player.getMainHandItem());
        return Math.max(0, st.ultCooldownTicks);
    }

    public static void onStartRoyalGuard(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !isHoldingExcalibur(player)) {
            return;
        }
        long now = level.getGameTime();
        ItemStack stack = player.getMainHandItem();
        PlayerExcaliburState st = stateOf(player, stack);
        st.lastCombatTick = now;
        st.guardStartTick = now;
        if (!st.unsheathed) {
            // Kiếm đang trong vỏ (is_unsheathed == false) -> Mở cửa sổ Parry bằng vỏ Avalon trong 4 ticks (0.20s), CHƯA rút kiếm!
            st.sheathedParryTicks = AVALON_PARRY_WINDOW_TICKS;
            st.sheatheClinkTick = -1000L;
            st.sheatheEndTick = -1000L;
            level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                    SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.72F, 1.38F);
            ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                    ExcaliburSyncPayload.EVENT_AVALON_PARRY_GUARD, player.getId(), (byte) 0,
                    4.0F, player.getX(), player.getY() + 1.0, player.getZ(),
                    player.getYRot(), player.getXRot(), 0.0F, 0.0F));
            return;
        }
        st.sheathedParryTicks = 0;
        markUnsheathed(player, level, st, stack, now);
    }

    /** Giảm bộ đếm cửa sổ Parry vỏ Avalon (`4 ticks / 0.20s`) mỗi tick khi đang giữ Chuột Phải lúc kiếm trong vỏ. */
    public static void onUseTick(ServerPlayer player) {
        PlayerExcaliburState st = PLAYER_STATES.get(player.getUUID());
        if (st != null && st.sheathedParryTicks > 0) {
            st.sheathedParryTicks--;
        }
    }

    private static void markUnsheathed(ServerPlayer player, ServerLevel level, PlayerExcaliburState st,
                                       ItemStack stack, long now) {
        st.lastActiveCombatOrGuardTick = now;
        st.sheatheClinkTick = -1000L;
        st.sheatheEndTick = -1000L;
        if (!st.unsheathed) {
            st.unsheathed = true;
            ExcaliburItem.setUnsheathed(stack, true);
            sendPassiveSync(player, level, st, true);
        }
    }

    private static void startSheatheAvalon(ServerPlayer player, ServerLevel level, PlayerExcaliburState st,
                                           ItemStack stack, long now) {
        st.unsheathed = false;
        ExcaliburItem.setUnsheathed(stack, false);
        st.nextComboStep = 0;
        st.lastMove = ExcaliburMoveType.SHEATHE_AVALON;
        st.sheatheClinkTick = now + SHEATHE_CLINK_DELAY_TICKS;
        st.sheatheEndTick = now + SHEATHE_TOTAL_TICKS;

        level.playSound(null, player.getX(), player.getY() + 0.95, player.getZ(),
                SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.45F, 1.48F);

        ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_SHEATHE, player.getId(), (byte) ExcaliburMoveType.SHEATHE_AVALON.id,
                ExcaliburMoveType.SHEATHE_AVALON.baseDisplayTicks, player.getX(), player.getY() + 0.95, player.getZ(),
                player.getYRot(), player.getXRot(), 0.0F, 0.0F));
        sendPassiveSync(player, level, st, true);
    }

    private static void handleManualSheathe(ServerPlayer player, ServerLevel level) {
        if (isActionLocked(player.getUUID()) || player.isUsingItem()) {
            return;
        }
        long now = level.getGameTime();
        ItemStack stack = player.getMainHandItem();
        PlayerExcaliburState st = stateOf(player, stack);
        if (!st.unsheathed || st.fallingStarDiving || now <= st.royalCounterBeat2Tick || now < st.sheatheEndTick) {
            return;
        }
        startSheatheAvalon(player, level, st, stack, now);
    }

    private static PlayerExcaliburState stateOf(ServerPlayer player, @Nullable ItemStack stack) {
        PlayerExcaliburState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerExcaliburState());
        if (st.energy < 0.0F) {
            st.energy = (stack != null && stack.is(ModItems.EXCALIBUR)) ? ExcaliburItem.getEnergy(stack) : 0.0F;
        }
        if (!st.unsheathedInit && stack != null && stack.is(ModItems.EXCALIBUR)) {
            st.unsheathed = ExcaliburItem.isUnsheathed(stack);
            st.unsheathedInit = true;
            if (st.unsheathed && player.level() instanceof ServerLevel sl) {
                st.lastActiveCombatOrGuardTick = sl.getGameTime();
            }
        }
        if (!st.ultCooldownInit && stack != null && stack.is(ModItems.EXCALIBUR)) {
            int savedCd = ExcaliburItem.getSavedUltCooldownTicks(stack);
            if (savedCd > 0) {
                st.ultCooldownTicks = Math.max(st.ultCooldownTicks, Math.min(savedCd, ULT_COOLDOWN_TICKS));
                ExcaliburItem.setSavedUltCooldownTicks(stack, 0);
            }
            st.ultCooldownInit = true;
        }
        return st;
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
        return toAttackFlat.normalize().dot(lookFlat.normalize()) >= -0.22;
    }

    /**
     * Gạt chệch mũi tên sang hướng khác khi đang giữ Chuột Phải đỡ bằng Excalibur (cả lúc kiếm trong vỏ Avalon lẫn lúc đã rút kiếm):
     * - Mũi tên bị hất lệch sang ngang/chếch lên, **không thể phản sát thương** (`baseDamage = 0`, `crit = false`, tắt lửa, không kích hoạt phản đòn).
     */
    @Nullable
    public static ProjectileDeflection tryGetArrowDeflection(Player player, @Nullable Projectile projectile) {
        if (player == null || !(projectile instanceof AbstractArrow) || !player.isAlive() || !isHoldingExcalibur(player)) {
            return null;
        }
        boolean activelyGuarding = player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND;
        if (!activelyGuarding) {
            return null;
        }
        boolean overheadGuard = player.getXRot() < -30.0F && projectile.getY() > player.getY() + 1.0;
        if (!overheadGuard && !isWithinFrontGuardArc(player, projectile.position())) {
            return null;
        }
        if (player.level().isClientSide) {
            return CLIENT_ARROW_DEFLECT_SENTINEL;
        }
        if (!(player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level) || isActionLocked(sp.getUUID())) {
            return null;
        }
        return (proj, deflectedBy, random) -> {
            if (proj instanceof AbstractArrow arrow && sp.isAlive() && isHoldingExcalibur(sp)) {
                PlayerExcaliburState curSt = stateOf(sp, sp.getMainHandItem());
                executeArrowSideDeflect(sp, level, curSt, arrow);
            }
        };
    }

    private static void executeArrowSideDeflect(ServerPlayer player, ServerLevel level,
                                                PlayerExcaliburState st, AbstractArrow arrow) {
        long now = level.getGameTime();
        st.lastCombatTick = now;
        ItemStack stack = player.getMainHandItem();

        Vec3 look = player.getLookAngle().normalize();
        Vec3 lookFlat = new Vec3(look.x, 0.0, look.z);
        lookFlat = (lookFlat.lengthSqr() < 1e-4) ? Vec3.directionFromRotation(0.0F, player.getYRot()) : lookFlat.normalize();
        Vec3 right = new Vec3(-lookFlat.z, 0.0, lookFlat.x).normalize();

        Vec3 relToArrow = arrow.position().subtract(player.position());
        double lateralDot = relToArrow.x * right.x + relToArrow.z * right.z;
        double sideSign = (Math.abs(lateralDot) > 0.05) ? Math.signum(lateralDot) : (level.random.nextBoolean() ? 1.0 : -1.0);

        double inSpeed = arrow.getDeltaMovement().length();
        double outSpeed = Mth.clamp(inSpeed * 0.82, 0.90, 1.70);
        Vec3 deflectDir = right.scale(sideSign * 0.90).add(lookFlat.scale(0.25)).add(0.0, 0.34, 0.0).normalize();
        Vec3 outVel = deflectDir.scale(outSpeed);

        arrow.setPos(arrow.getX() + deflectDir.x * 0.45, arrow.getY() + 0.14, arrow.getZ() + deflectDir.z * 0.45);
        arrow.setDeltaMovement(outVel);
        arrow.setCritArrow(false);
        arrow.setBaseDamage(0.0);
        arrow.clearFire();
        float newYaw = (float) (Mth.atan2(outVel.x, outVel.z) * (180.0 / Math.PI));
        float newPitch = (float) (Mth.atan2(outVel.y, Math.hypot(outVel.x, outVel.z)) * (180.0 / Math.PI));
        arrow.setYRot(newYaw);
        arrow.setXRot(newPitch);
        arrow.yRotO = newYaw;
        arrow.xRotO = newPitch;
        arrow.hurtMarked = true;

        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

        boolean overhead = st.unsheathed && player.getXRot() < -30.0F;
        Vec3 clashPos = overhead
                ? player.getEyePosition().add(lookFlat.scale(0.55)).add(0.0, 0.34, 0.0)
                : player.getEyePosition().add(look.scale(0.82)).add(0.0, -0.16, 0.0);

        level.sendParticles(GOLD_SPARK, clashPos.x, clashPos.y, clashPos.z, 9, 0.22, 0.22, 0.22, 0.08);
        level.sendParticles(WHITE_HOLY, clashPos.x, clashPos.y, clashPos.z, 4, 0.16, 0.16, 0.16, 0.05);
        level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                ModSounds.KATANA_PARRY, SoundSource.PLAYERS, 1.10F, 1.24F + level.random.nextFloat() * 0.10F);
        level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.92F, 1.28F);
        ModNetworking.sendShakeNearby(level, clashPos, 10.0, 0.24F, 3);

        player.displayClientMessage(Component.literal("§6✦ Hất chệch mũi tên!"), true);

        if (overhead) {
            ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                    ExcaliburSyncPayload.EVENT_GUARD_OVERHEAD_IMPACT, player.getId(), (byte) ExcaliburMoveType.GUARD_OVERHEAD_IMPACT.id,
                    ExcaliburMoveType.GUARD_OVERHEAD_IMPACT.baseDisplayTicks, clashPos.x, clashPos.y, clashPos.z,
                    player.getYRot(), player.getXRot(), 1.0F, 0.0F));
        } else {
            ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                    ExcaliburSyncPayload.EVENT_GUARD_CROSS_HIT, player.getId(), (byte) 0,
                    3.0F, clashPos.x, clashPos.y, clashPos.z, player.getYRot(), player.getXRot(), 1.0F, 0.0F));
        }
    }

    /**
     * Hệ thống Đỡ Kiếm & Phản Đòn Hoàng Gia (Section 5):
     * - **A. Đỡ Trực Diện (`Hold RMB`):** Giảm `70%` sát thương phía trước & bắn tia lửa vàng gọn.
     * - **B. Đỡ Trên Cao (`Hold RMB + Pitch < -30°`):** Chặn `100%` đòn bổ từ trên cao.
     * - **C. Royal Parry Counter (`<= 0.18s` / `4 ticks` trước khi trúng đòn):**
     *   - Nhịp 1 (`0.00–0.15s`): Miễn `100%` sát thương, xoay giật `+35°` gạt chệch vũ khí địch, lóe 1 vòng phép vàng 2D nhỏ tại điểm va chạm + tiếng `"Klang!"` thanh vang!
     *   - Nhịp 2 (`0.15–0.40s`): Tự động vặn ngược `-40°` tung nhát chém ngang cực nhanh gây `200%` sát thương & làm choáng mục tiêu `0.8s` (`16 ticks`)!
     */
    public static boolean tryRoyalParryIncomingDamage(LivingEntity victim, DamageSource source, float incomingAmount) {
        if (Boolean.TRUE.equals(REENTRANT_GUARD_DAMAGE.get())) {
            return false;
        }
        if (victim == null || victim.level().isClientSide || !(victim instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (incomingAmount <= 0.0F || !player.isAlive() || !isHoldingExcalibur(player) || source == null) {
            return false;
        }
        if (isTrueDamageActive() || KatanaCombatHandler.isMagicSkillDamageActive()) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_LIGHTNING)
                || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }

        long now = level.getGameTime();
        PlayerExcaliburState st = stateOf(player, player.getMainHandItem());
        boolean activelyGuarding = player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && !isActionLocked(player.getUUID());
        boolean parryGrace = (now - st.lastRoyalParryTick) <= 6L || (now - st.lastAvalonParryTick) <= 5L;
        if (!activelyGuarding && !parryGrace) {
            return false;
        }

        Entity direct = source.getDirectEntity();
        Entity root = source.getEntity();
        boolean isProjectile = source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypes.ARROW)
                || source.is(DamageTypes.TRIDENT)
                || source.is(DamageTypes.FIREBALL)
                || source.is(DamageTypes.UNATTRIBUTED_FIREBALL)
                || source.is(DamageTypes.WITHER_SKULL)
                || direct instanceof AbstractArrow
                || direct instanceof Projectile;
        boolean isMelee = !isProjectile
                && ((source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.STING))
                || (direct instanceof LivingEntity && direct != player));
        if (!isProjectile && !isMelee) {
            return false;
        }

        Vec3 attackOrigin = source.getSourcePosition() != null ? source.getSourcePosition()
                : (direct != null ? direct.position() : (root != null ? root.position() : null));
        if (!isWithinFrontGuardArc(player, attackOrigin)) {
            return false;
        }

        st.lastCombatTick = now;
        Vec3 look = player.getLookAngle().normalize();
        Vec3 lookFlat = new Vec3(look.x, 0.0, look.z);
        lookFlat = (lookFlat.lengthSqr() < 1e-4) ? Vec3.directionFromRotation(0.0F, player.getYRot()) : lookFlat.normalize();

        // === 0. AVALON SHEATHED PARRY & IAIDO DRAW-SLASH COUNTER (Kiếm đang trong vỏ + cửa sổ Parry 0.20s / 4 ticks!) ===
        if (!st.unsheathed) {
            boolean avalonParryWindow = st.sheathedParryTicks > 0
                    || (now - st.guardStartTick) <= AVALON_PARRY_WINDOW_TICKS
                    || (now - st.lastAvalonParryTick) <= 4L;
            if (isMelee && avalonParryWindow) {
                if ((now - st.lastAvalonParryTick) <= 4L) {
                    return true;
                }
                ItemStack held = player.getMainHandItem();
                st.sheathedParryTicks = 0;
                st.lastAvalonParryTick = now;
                st.lastMove = ExcaliburMoveType.AVALON_PARRY_COUNTER;
                st.lastSwingTick = now;
                st.nextComboStep = 0;

                if (player.isUsingItem()) {
                    player.stopUsingItem();
                }
                // Tự động chuyển sang trạng thái Rút Kiếm (`is_unsheathed = true`)!
                markUnsheathed(player, level, st, held, now);

                // Lao lướt nhẹ tới trước (dash 0.6 block)
                Vec3 curVel = player.getDeltaMovement();
                player.setDeltaMovement(curVel.x * 0.25 + lookFlat.x * 0.45, Math.max(curVel.y, 0.10), curVel.z * 0.25 + lookFlat.z * 0.45);
                player.hurtMarked = true;
                player.swing(InteractionHand.MAIN_HAND, true);

                // Gây 220% sát thương phản công ngang rộng Trái->Phải + Choáng 1.0s (20 ticks) + Knockback 0.6!
                LivingEntity primaryAttacker = (direct instanceof LivingEntity le) ? le : (root instanceof LivingEntity rle ? rle : null);
                float baseDmg = (float) Math.max(ExcaliburItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
                float counterDmg = baseDmg * ExcaliburMoveType.AVALON_PARRY_COUNTER.damageMultiplier;
                Vec3 slashCenter = player.getEyePosition().add(look.scale(1.90));
                AABB box = new AABB(slashCenter, slashCenter).inflate(2.90);
                List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != player && e.isAlive() && e.isAttackable() && !ExcaliburSeals.isAlly(player, e));

                OVERRIDE_ARMOR_PIERCE.set(0.40F);
                try {
                    for (LivingEntity target : candidates) {
                        boolean isAttacker = (primaryAttacker != null && target == primaryAttacker);
                        Vec3 toTarget = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(player.getEyePosition());
                        if (!isAttacker && (toTarget.lengthSqr() > 4.6 * 4.6 || toTarget.normalize().dot(look) < -0.08)) {
                            continue;
                        }
                        target.invulnerableTime = 0;
                        if (target.hurt(level.damageSources().playerAttack(player), counterDmg)) {
                            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AVALON_PARRY_STUN_TICKS, 5, false, true, true));
                            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, AVALON_PARRY_STUN_TICKS, 3, false, true, true));
                            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, AVALON_PARRY_STUN_TICKS, 3, false, false, false));
                            target.knockback(0.60, player.getX() - target.getX(), player.getZ() - target.getZ());
                            target.hurtMarked = true;
                        }
                    }
                } finally {
                    OVERRIDE_ARMOR_PIERCE.remove();
                }

                // Hồi lại +15 Năng Lượng & tiêu hao 1 độ bền!
                held.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                awardEnergy(player, level, st, held, AVALON_PARRY_ENERGY_GAIN, false);

                Vec3 clashPos = player.getEyePosition().add(look.scale(0.86)).add(0.0, -0.16, 0.0);
                level.sendParticles(GOLD_SPARK, clashPos.x, clashPos.y, clashPos.z, 12, 0.24, 0.24, 0.24, 0.09);
                level.sendParticles(WHITE_HOLY, clashPos.x, clashPos.y, clashPos.z, 6, 0.16, 0.16, 0.16, 0.06);
                level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.85F, 1.80F);
                level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                        ModSounds.KATANA_PARRY, SoundSource.PLAYERS, 1.45F, 1.08F);
                level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                        ModSounds.EXCALIBUR_SLASH, SoundSource.PLAYERS, 1.35F, 1.32F);
                ModNetworking.sendShakeNearby(level, clashPos, 16.0, 0.56F, 5);

                player.displayClientMessage(Component.literal(
                        "§6§l✦ AVALON IAIDO COUNTER! §fRút kiếm phản đòn §e220% ST §7(Choáng 1.0s · +15 NL)!"), true);

                ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                        ExcaliburSyncPayload.EVENT_AVALON_PARRY_COUNTER, player.getId(), (byte) ExcaliburMoveType.AVALON_PARRY_COUNTER.id,
                        ExcaliburMoveType.AVALON_PARRY_COUNTER.baseDisplayTicks, clashPos.x, clashPos.y, clashPos.z,
                        player.getYRot(), player.getXRot(), 2.3F, 0.0F));
                return true;
            }
            // Nếu giữ đỡ quá cửa sổ 0.20s khi kiếm vẫn trong vỏ -> đỡ giảm 70% sát thương bằng thân vỏ Avalon (không phản đòn)!
            float reducedSheathed = Math.max(0.25F, incomingAmount * 0.30F);
            boolean appliedSheathed;
            REENTRANT_GUARD_DAMAGE.set(Boolean.TRUE);
            try {
                appliedSheathed = player.hurt(source, reducedSheathed);
            } finally {
                REENTRANT_GUARD_DAMAGE.set(Boolean.FALSE);
            }
            if (appliedSheathed) {
                Vec3 clashPos = player.getEyePosition().add(look.scale(0.75)).add(0.0, -0.18, 0.0);
                level.sendParticles(GOLD_SPARK, clashPos.x, clashPos.y, clashPos.z, 7, 0.20, 0.20, 0.20, 0.06);
                level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.05F, 1.24F);
                ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                        ExcaliburSyncPayload.EVENT_GUARD_CROSS_HIT, player.getId(), (byte) 0,
                        3.0F, clashPos.x, clashPos.y, clashPos.z, player.getYRot(), player.getXRot(), 1.0F, 0.0F));
            }
            return true;
        }

        // === C. ROYAL PARRY COUNTER (Chuẩn nhịp <= 0.18s / 4 ticks trước khi trúng đòn cận chiến!) ===
        boolean perfectParryTiming = parryGrace || (now - st.guardStartTick) <= PERFECT_PARRY_WINDOW_TICKS;
        if (isMelee && perfectParryTiming) {
            if (parryGrace && st.royalCounterBeat2Tick > now) {
                return true;
            }
            st.lastRoyalParryTick = now;
            st.lastMove = ExcaliburMoveType.ROYAL_PARRY_COUNTER;
            st.lastSwingTick = now;
            st.nextComboStep = 0;

            LivingEntity attacker = (direct instanceof LivingEntity le) ? le : (root instanceof LivingEntity rle ? rle : null);
            st.royalParryAttackerId = (attacker != null && attacker.isAlive() && attacker != player) ? attacker.getId() : -1;
            st.royalCounterBeat2Tick = now + 3L; // Nhịp 2 tự động chém ngang ở 0.15s (Tick 3)!

            if (attacker != null && attacker.isAlive() && attacker != player) {
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 4, false, false, false));
                Vec3 push = attacker.position().subtract(player.position());
                Vec3 pushFlat = new Vec3(push.x, 0.0, push.z);
                if (pushFlat.lengthSqr() > 1e-4) {
                    pushFlat = pushFlat.normalize();
                    attacker.push(pushFlat.x * 0.35, 0.08, pushFlat.z * 0.35);
                    attacker.hurtMarked = true;
                }
            }

            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

            Vec3 clashPos = player.getEyePosition().add(look.scale(0.92)).add(0.0, -0.14, 0.0);
            level.sendParticles(GOLD_SPARK, clashPos.x, clashPos.y, clashPos.z, 10, 0.24, 0.24, 0.24, 0.08);
            level.sendParticles(WHITE_HOLY, clashPos.x, clashPos.y, clashPos.z, 6, 0.18, 0.18, 0.18, 0.06);
            level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                    ModSounds.KATANA_PARRY, SoundSource.PLAYERS, 1.45F, 1.02F);
            level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                    SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.68F, 1.78F);
            ModNetworking.sendShakeNearby(level, clashPos, 14.0, 0.50F, 4);

            player.displayClientMessage(Component.literal(
                    "§6§l✦ ROYAL PARRY COUNTER! §fGạt chệch & Phản chém §e200% ST §f(Choáng 0.8s)!"), true);

            ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                    ExcaliburSyncPayload.EVENT_ROYAL_PARRY, player.getId(), (byte) ExcaliburMoveType.ROYAL_PARRY_COUNTER.id,
                    ExcaliburMoveType.ROYAL_PARRY_COUNTER.baseDisplayTicks, clashPos.x, clashPos.y, clashPos.z,
                    player.getYRot(), player.getXRot(), 1.15F, 0.0F));
            return true;
        }

        // === B. POSE ĐỠ TRÊN CAO (Giữ RMB + Nhìn lên trời Pitch < -30° -> Chặn 100% đòn bổ trên cao!) ===
        if (isMelee && player.getXRot() < -30.0F) {
            LivingEntity attacker = (direct instanceof LivingEntity le) ? le : (root instanceof LivingEntity rle ? rle : null);
            if (attacker != null && attacker.isAlive() && attacker != player) {
                Vec3 push = attacker.position().subtract(player.position());
                Vec3 pushFlat = new Vec3(push.x, 0.0, push.z);
                if (pushFlat.lengthSqr() > 1e-4) {
                    pushFlat = pushFlat.normalize();
                    attacker.push(pushFlat.x * 0.42, 0.12, pushFlat.z * 0.42);
                    attacker.hurtMarked = true;
                }
            }
            player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

            Vec3 roofPos = player.getEyePosition().add(lookFlat.scale(0.55)).add(0.0, 0.34, 0.0);
            level.sendParticles(GOLD_SPARK, roofPos.x, roofPos.y, roofPos.z, 10, 0.28, 0.12, 0.28, 0.08);
            level.playSound(null, roofPos.x, roofPos.y, roofPos.z,
                    ModSounds.KATANA_PARRY, SoundSource.PLAYERS, 1.15F, 1.18F);
            level.playSound(null, roofPos.x, roofPos.y, roofPos.z,
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.05F, 1.30F);
            ModNetworking.sendShakeNearby(level, roofPos, 12.0, 0.36F, 4);

            ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                    ExcaliburSyncPayload.EVENT_GUARD_OVERHEAD_IMPACT, player.getId(), (byte) ExcaliburMoveType.GUARD_OVERHEAD_IMPACT.id,
                    ExcaliburMoveType.GUARD_OVERHEAD_IMPACT.baseDisplayTicks, roofPos.x, roofPos.y, roofPos.z,
                    player.getYRot(), player.getXRot(), 1.0F, 0.0F));
            return true;
        }

        // === A. POSE ĐỠ TRỰC DIỆN (Hold RMB -> Giảm 70% sát thương phía trước!) ===
        float reduced = Math.max(0.25F, incomingAmount * 0.30F);
        boolean applied;
        REENTRANT_GUARD_DAMAGE.set(Boolean.TRUE);
        try {
            applied = player.hurt(source, reduced);
        } finally {
            REENTRANT_GUARD_DAMAGE.set(Boolean.FALSE);
        }
        if (applied) {
            Vec3 cur = player.getDeltaMovement();
            player.setDeltaMovement(cur.x * 0.45, Math.min(cur.y, 0.08), cur.z * 0.45);
            player.hurtMarked = true;
            player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

            Vec3 clashPos = player.getEyePosition().add(look.scale(0.80)).add(0.0, -0.18, 0.0);
            level.sendParticles(GOLD_SPARK, clashPos.x, clashPos.y, clashPos.z, 8, 0.22, 0.22, 0.22, 0.07);
            level.playSound(null, clashPos.x, clashPos.y, clashPos.z,
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.05F, 1.18F);
            ModNetworking.sendShakeNearby(level, clashPos, 10.0, 0.22F, 3);

            ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                    ExcaliburSyncPayload.EVENT_GUARD_CROSS_HIT, player.getId(), (byte) 0,
                    3.0F, clashPos.x, clashPos.y, clashPos.z, player.getYRot(), player.getXRot(), 1.0F, 0.0F));
        }
        return true;
    }

    /** Nhịp 2 Royal Parry Counter (mốc 0.15s = Tick 3): Vặn ngược `-40°` chém ngang gây `200%` sát thương & làm choáng `0.8s`! */
    private static void fireRoyalCounterBeat2(ServerPlayer player, ServerLevel level,
                                              PlayerExcaliburState st, ItemStack stack) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = (flat.lengthSqr() < 1e-4) ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
        Vec3 cur = player.getDeltaMovement();
        player.setDeltaMovement(cur.x * 0.35 + flat.x * 0.26, cur.y, cur.z * 0.35 + flat.z * 0.26);
        player.hurtMarked = true;
        player.swing(InteractionHand.MAIN_HAND, true);

        float baseDmg = (float) Math.max(ExcaliburItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float dmg = baseDmg * ExcaliburMoveType.ROYAL_PARRY_COUNTER.damageMultiplier; // 200% sát thương!
        double reach = 4.20;
        Vec3 center = player.getEyePosition().add(look.scale(1.85));
        AABB box = new AABB(center, center).inflate(2.75);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e.isAttackable() && !ExcaliburSeals.isAlly(player, e));

        boolean hitAny = false;
        OVERRIDE_ARMOR_PIERCE.set(0.40F);
        try {
            for (LivingEntity target : candidates) {
                boolean isPrimaryAttacker = target.getId() == st.royalParryAttackerId;
                Vec3 toTarget = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(player.getEyePosition());
                if (!isPrimaryAttacker && toTarget.lengthSqr() > (reach + 0.5) * (reach + 0.5)) {
                    continue;
                }
                if (!isPrimaryAttacker && toTarget.normalize().dot(look) < -0.05) {
                    continue;
                }
                target.invulnerableTime = 0;
                if (target.hurt(level.damageSources().playerAttack(player), dmg)) {
                    hitAny = true;
                    // Làm choáng mục tiêu 0.8s (16 ticks)
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PARRY_STUN_TICKS, 5, false, true, true));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PARRY_STUN_TICKS, 3, false, true, true));
                    target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, PARRY_STUN_TICKS, 3, false, false, false));
                    target.setDeltaMovement(flat.x * 0.85, 0.24, flat.z * 0.85);
                    target.hurtMarked = true;
                }
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
            st.royalParryAttackerId = -1;
        }

        Vec3 anchor = player.position().add(0.0, player.getBbHeight() * 0.62, 0.0);
        level.playSound(null, anchor.x, anchor.y, anchor.z,
                ModSounds.EXCALIBUR_SLASH, SoundSource.PLAYERS, 1.32F, 1.36F);
        if (hitAny) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            awardEnergy(player, level, st, stack, ExcaliburItem.ENERGY_PER_HIT, false);
            ModNetworking.sendShakeNearby(level, anchor, 14.0, 0.52F, 4);
            ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                    ExcaliburSyncPayload.EVENT_HIT_STOP, player.getId(), (byte) ExcaliburMoveType.ROYAL_PARRY_COUNTER.id,
                    1.5F, anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot(), 0.0F, 0.0F));
        }

        ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_ROYAL_COUNTER_SLASH, player.getId(), (byte) ExcaliburMoveType.ROYAL_PARRY_COUNTER.id,
                5.0F, anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot(), 2.2F, 0.0F));
    }

    private static void ensureFullEnergyStrength(ServerPlayer player, PlayerExcaliburState st) {
        MobEffectInstance cur = player.getEffect(MobEffects.DAMAGE_BOOST);
        if (cur != null && (cur.getAmplifier() > FULL_ENERGY_STRENGTH_AMP
                || (cur.getAmplifier() == FULL_ENERGY_STRENGTH_AMP && cur.getDuration() > FULL_ENERGY_STRENGTH_TICKS + 10))) {
            return;
        }
        if (cur == null || cur.getAmplifier() < FULL_ENERGY_STRENGTH_AMP || cur.getDuration() < 35) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_BOOST, FULL_ENERGY_STRENGTH_TICKS, FULL_ENERGY_STRENGTH_AMP, true, true, true));
        }
        st.appliedFullEnergyStrength = true;
    }

    private static void clearFullEnergyStrength(ServerPlayer player, PlayerExcaliburState st) {
        if (!st.appliedFullEnergyStrength) {
            return;
        }
        st.appliedFullEnergyStrength = false;
        MobEffectInstance cur = player.getEffect(MobEffects.DAMAGE_BOOST);
        if (cur != null && cur.getAmplifier() == FULL_ENERGY_STRENGTH_AMP
                && cur.getDuration() <= FULL_ENERGY_STRENGTH_TICKS + 10) {
            player.removeEffect(MobEffects.DAMAGE_BOOST);
        }
    }

    private static void awardEnergy(ServerPlayer player, ServerLevel level, PlayerExcaliburState st,
                                    ItemStack held, float amount, boolean isKill) {
        if (amount <= 0.0F || held.isEmpty() || !held.is(ModItems.EXCALIBUR)) {
            return;
        }
        long now = level.getGameTime();
        if (st.energy >= ExcaliburItem.MAX_ENERGY - 0.01F) {
            return;
        }

        st.energy = Mth.clamp(st.energy + amount, 0.0F, ExcaliburItem.MAX_ENERGY);
        ExcaliburItem.setEnergy(held, st.energy);
        if (isKill) {
            st.lastKillEnergyTick = now;
        }

        int pct = Math.round(st.energy);
        if (st.energy >= ExcaliburItem.MAX_ENERGY - 0.01F) {
            ensureFullEnergyStrength(player, st);
            level.playSound(null, player.getX(), player.getY() + 1.1, player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.25F, 1.40F);
            level.sendParticles(WHITE_HOLY, player.getX(), player.getY() + 1.1, player.getZ(),
                    14, 0.55, 0.60, 0.55, 0.06);
            if (st.ultCooldownTicks > 0) {
                player.displayClientMessage(Component.literal(
                        "§e§l✦ EXCALIBUR ĐẦY 100/100 NĂNG LƯỢNG — SỨC MẠNH IV §7(Hồi kỹ năng còn "
                                + formatTimer(st.ultCooldownTicks) + ") ✦"), true);
            } else {
                player.displayClientMessage(Component.literal(
                        "§e§l✦ EXCALIBUR ĐẦY 100/100 NĂNG LƯỢNG — NHẬN SỨC MẠNH IV · BẤM [R] TUNG CHIÊU! ✦"), true);
            }
        } else if (isKill) {
            level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.25F + (st.energy / 100.0F) * 0.45F);
            player.displayClientMessage(Component.literal(
                    "§6✦ +5 Năng lượng Excalibur §e(" + pct + "/100)"), true);
        } else if (st.lastKillEnergyTick != now) {
            level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.32F, 1.35F + (st.energy / 100.0F) * 0.40F);
        }
        sendPassiveSync(player, level, st, true);
    }

    public static void onEntityKilled(LivingEntity victim, DamageSource source) {
        if (victim == null || victim.level().isClientSide || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        if (isTrueDamageActive()) {
            return;
        }
        ServerPlayer killer = null;
        if (source != null && source.getEntity() instanceof ServerPlayer sp) {
            killer = sp;
        } else if (victim.getKillCredit() instanceof ServerPlayer sp) {
            killer = sp;
        }
        if (killer == null || killer == victim || !killer.isAlive() || !isHoldingExcalibur(killer)) {
            return;
        }
        ItemStack held = killer.getMainHandItem();
        PlayerExcaliburState st = stateOf(killer, held);
        awardEnergy(killer, level, st, held, ExcaliburItem.ENERGY_PER_KILL, true);
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ACTIVE_ULTIMATES.remove(player.getUUID());
        PlayerExcaliburState st = PLAYER_STATES.remove(player.getUUID());
        if (st != null) {
            ItemStack main = player.getMainHandItem();
            if (main.is(ModItems.EXCALIBUR)) {
                if (st.energy >= 0.0F) {
                    ExcaliburItem.setEnergy(main, st.energy);
                }
                if (st.ultCooldownTicks > 0) {
                    ExcaliburItem.setSavedUltCooldownTicks(main, st.ultCooldownTicks);
                }
            } else if (st.ultCooldownTicks > 0) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack s = player.getInventory().getItem(i);
                    if (s.is(ModItems.EXCALIBUR)) {
                        ExcaliburItem.setSavedUltCooldownTicks(s, st.ultCooldownTicks);
                        break;
                    }
                }
            }
        }
    }

    public static void onLivingHurtReturn(LivingEntity victim, DamageSource source) {
        if (victim instanceof ServerPlayer sp && isHoldingExcalibur(sp)) {
            PlayerExcaliburState st = stateOf(sp, sp.getMainHandItem());
            st.lastCombatTick = sp.serverLevel().getGameTime();
        }
        Entity attacker = source != null ? source.getEntity() : null;
        if (attacker instanceof ServerPlayer sp && isHoldingExcalibur(sp)) {
            long now = sp.serverLevel().getGameTime();
            PlayerExcaliburState st = stateOf(sp, sp.getMainHandItem());
            st.lastCombatTick = now;
            if (!isTrueDamageActive() && victim != sp && !ExcaliburSeals.isAlly(sp, victim)) {
                st.lastDamagedEnemyTick = now;
            }
            if (ExcaliburSeals.isAlly(sp, victim)) {
                st.lastDishonorTick = now;
            }
        }
    }

    /** Phân nhánh chuẩn (Aerial Judgment -> Delay Golden Crescent Wave -> Royal Swordplay 4-Hit). */
    private static ExcaliburMoveType resolveStandardMove(ServerPlayer player, ServerLevel level, PlayerExcaliburState st) {
        long now = level.getGameTime();

        // 1. Nhánh C: Không Chiến — Aerial Judgment (`!player.onGround()`)
        if (!player.onGround() && !player.isInWater() && !player.isFallFlying() && !player.isPassenger()) {
            if (now - st.lastAirHitTick > 22L) {
                st.airComboStep = 0;
            }
            ExcaliburMoveType air = ExcaliburMoveType.aerialByStep(st.airComboStep);
            st.airComboStep = Math.min(2, st.airComboStep + 1);
            st.lastAirHitTick = now;
            st.nextComboStep = 0;
            return air;
        }

        // 2. Nhánh B: Golden Crescent Wave (Delay Combo: Hit 1 -> Đợi 0.30s [7–26t] -> Đánh!)
        long sinceLast = now - st.lastSwingTick;
        if (st.lastMove == ExcaliburMoveType.HIT_1_SWEEP
                && sinceLast >= DELAY_CRESCENT_MIN_TICKS && sinceLast <= DELAY_CRESCENT_MAX_TICKS) {
            st.nextComboStep = 0;
            st.crescentWaveFireTick = now + 8L; // Mốc 0.40s bổ phóng lưỡi liềm vàng mảnh 7 blocks!
            return ExcaliburMoveType.GOLDEN_CRESCENT;
        }

        // 3. Chuỗi 4 đòn thường Royal Swordplay
        if (sinceLast > COMBO_RESET_TICKS) {
            st.nextComboStep = 0;
        }
        ExcaliburMoveType move = ExcaliburMoveType.comboByStep(st.nextComboStep);
        st.nextComboStep = (st.nextComboStep + 1) % 4;
        return move;
    }

    @Nullable
    public static ActiveHitContext beginPlayerAttack(Player player, Entity target) {
        if (player.level().isClientSide || !isHoldingExcalibur(player) || !(player instanceof ServerPlayer sp)) {
            return null;
        }
        if (isActionLocked(sp.getUUID())) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        ServerLevel level = sp.serverLevel();
        PlayerExcaliburState st = stateOf(sp, stack);
        long now = level.getGameTime();
        if (st.fallingStarDiving || now <= st.royalCounterBeat2Tick) {
            return null;
        }
        ExcaliburMoveType move = resolveStandardMove(sp, level, st);
        st.lastMove = move;
        st.lastSwingTick = now;
        st.lastCombatTick = now;
        markUnsheathed(sp, level, st, stack, now);
        applyMoveKinematics(sp, st, move, now, true);

        LivingEntity living = (target instanceof LivingEntity le) ? le : null;
        ActiveHitContext ctx = new ActiveHitContext(player, target, living, stack, move);
        ACTIVE_HIT.set(ctx);
        return ctx;
    }

    private static void applyMoveKinematics(ServerPlayer player, PlayerExcaliburState st,
                                            ExcaliburMoveType move, long now, boolean hasDirectTarget) {
        if (player.isPassenger() || player.isSwimming() || player.isFallFlying()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = (flat.lengthSqr() < 1e-4) ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
        Vec3 cur = player.getDeltaMovement();

        switch (move) {
            case AIR_1_DIAG, AIR_2_DIAG -> {
                // Giảm trọng lực rơi (motionY = 0.05) giữ nhân vật lơ lửng ngang tầm mục tiêu!
                st.airHoverUntilTick = now + 5L;
                player.setDeltaMovement(cur.x * 0.60 + flat.x * 0.14, 0.05, cur.z * 0.60 + flat.z * 0.14);
                player.fallDistance = 0.0F;
                player.hurtMarked = true;
                return;
            }
            case AIR_3_FALLING_STAR -> {
                // Chĩa thẳng mũi kiếm xuống đất (Pitch -10°), lao vút xuống (motionY = -1.3)!
                st.fallingStarDiving = true;
                st.fallingStarStartTick = now;
                player.setDeltaMovement(flat.x * 0.14, -1.3, flat.z * 0.14);
                player.fallDistance = 0.0F;
                player.hurtMarked = true;
                return;
            }
            case STRIKE_AIR -> {
                // Đạp chân lao tới trước (dash 0.6 block) diễn ra ở mốc 0.20s (Tick 4)!
                return;
            }
            default -> {
            }
        }

        if (player.isShiftKeyDown()) {
            return;
        }
        double baseImpulse = switch (move) {
            case HIT_1_SWEEP -> 0.155;      // Lướt nhẹ tới trước 0.3 block
            case HIT_2_RISING -> 0.140;
            case HIT_3_THRUST -> 0.360;     // Lao người đâm thẳng tới trước (dash 0.65 block)
            case HIT_4_SLAM -> 0.155;
            case GOLDEN_CRESCENT -> 0.150;
            default -> 0.0;
        };
        if (baseImpulse > 0.0) {
            double impulse = hasDirectTarget ? baseImpulse * 0.70 : baseImpulse;
            if (cur.horizontalDistanceSqr() < 0.28) {
                double hopY = (move == ExcaliburMoveType.HIT_4_SLAM && player.onGround()) ? 0.13 : 0.0;
                player.setDeltaMovement(cur.x + flat.x * impulse, cur.y + hopY, cur.z + flat.z * impulse);
                player.hurtMarked = true;
            }
        }
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
        PlayerExcaliburState st = stateOf(sp, sp.getMainHandItem());
        boolean landedHit = (ctx.realTarget != null && ctx.realTarget.hurtTime > 0)
                || (st.lastDamagedEnemyTick == level.getGameTime());
        if (ctx.realTarget != null && landedHit) {
            applySpecialOnHit(ctx.realTarget, ctx.moveType);
        }
        if (ctx.moveType == ExcaliburMoveType.GOLDEN_CRESCENT) {
            if (perform360SpinHit(sp, level, ctx.realTarget)) {
                landedHit = true;
            }
        } else if (ctx.moveType == ExcaliburMoveType.AIR_1_DIAG || ctx.moveType == ExcaliburMoveType.AIR_2_DIAG) {
            if (performForwardArcCleave(sp, level, ctx.moveType, ctx.realTarget)) {
                landedHit = true;
            }
        }
        executeSlashEffects(sp, level, ctx.moveType, landedHit);
    }

    private static void applySpecialOnHit(LivingEntity target, ExcaliburMoveType move) {
        if (move == ExcaliburMoveType.AIR_1_DIAG || move == ExcaliburMoveType.AIR_2_DIAG) {
            Vec3 v = target.getDeltaMovement();
            target.setDeltaMovement(v.x * 0.45, 0.22, v.z * 0.45);
            target.hurtMarked = true;
        }
    }

    public static void handleActionPacket(ServerPlayer player, ExcaliburActionC2SPayload payload) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !isHoldingExcalibur(player)) {
            return;
        }
        switch (payload.actionId()) {
            case ExcaliburActionC2SPayload.ACTION_AIR_SLASH -> handleAirSlash(player, level);
            case ExcaliburActionC2SPayload.ACTION_ULTIMATE -> tryStartUltimate(player);
            case ExcaliburActionC2SPayload.ACTION_STRIKE_AIR -> handleStrikeAirStart(player, level);
            case ExcaliburActionC2SPayload.ACTION_SHEATHE -> handleManualSheathe(player, level);
            default -> {
            }
        }
    }

    /** Nhánh A: Strike Air Thrust (Hold Attack sau Hit 1/2) — 0.00–0.20s lấy đà + 0.20–0.35s đâm bộc phá (5 blocks, đẩy lùi & phá khiên)! */
    private static void handleStrikeAirStart(ServerPlayer player, ServerLevel level) {
        if (isActionLocked(player.getUUID()) || player.isUsingItem()) {
            return;
        }
        long now = level.getGameTime();
        ItemStack stack = player.getMainHandItem();
        PlayerExcaliburState st = stateOf(player, stack);
        if (st.fallingStarDiving || now <= st.royalCounterBeat2Tick) {
            return;
        }
        boolean fromHit1Or2 = (st.lastMove == ExcaliburMoveType.HIT_1_SWEEP || st.lastMove == ExcaliburMoveType.HIT_2_RISING);
        long since = now - st.lastSwingTick;
        if (!fromHit1Or2 || since < st.lastMove.cancelAfterTicks || since > 26L) {
            return;
        }

        st.lastMove = ExcaliburMoveType.STRIKE_AIR;
        st.lastSwingTick = now;
        st.lastCombatTick = now;
        st.nextComboStep = 0;
        st.strikeAirFireTick = now + 4L; // Mốc 0.20s phóng vòng xung kích 5 blocks!
        markUnsheathed(player, level, st, stack, now);

        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 0.90F, 1.32F);
        executeSlashEffects(player, level, ExcaliburMoveType.STRIKE_AIR, false);
    }

    private static void fireStrikeAirBlast(ServerPlayer player, ServerLevel level, PlayerExcaliburState st, ItemStack stack) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = (flat.lengthSqr() < 1e-4) ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
        Vec3 cur = player.getDeltaMovement();
        // Đạp chân lao tới trước (dash 0.6 block)
        player.setDeltaMovement(cur.x * 0.35 + flat.x * 0.34, cur.y, cur.z * 0.35 + flat.z * 0.34);
        player.hurtMarked = true;

        Vec3 origin = player.getEyePosition().add(0.0, -0.16, 0.0);
        double range = 5.0; // Chính xác 5 blocks!
        Vec3 end = origin.add(look.scale(range));
        AABB box = new AABB(origin, end).inflate(1.85);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e.isAttackable() && !ExcaliburSeals.isAlly(player, e));
        float baseDmg = (float) Math.max(ExcaliburItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float dmg = baseDmg * ExcaliburMoveType.STRIKE_AIR.damageMultiplier;
        boolean hitAny = false;

        SHIELD_BREAK_ACTIVE.set(Boolean.TRUE);
        OVERRIDE_ARMOR_PIERCE.set(0.30F);
        try {
            for (LivingEntity target : candidates) {
                Vec3 center = target.getBoundingBox().getCenter();
                Vec3 rel = center.subtract(origin);
                double along = rel.dot(look);
                if (along < 0.2 || along > range + 0.5) {
                    continue;
                }
                double frac = Mth.clamp(along / range, 0.0, 1.0);
                double maxRad = Mth.lerp(frac, 0.78, 1.65) + target.getBbWidth() * 0.38;
                Vec3 closest = origin.add(look.scale(along));
                if (closest.distanceToSqr(center) > maxRad * maxRad) {
                    continue;
                }
                if (target instanceof Player tp && tp.isBlocking()) {
                    tp.disableShield();
                    level.playSound(null, center.x, center.y, center.z,
                            SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.20F, 1.0F);
                }
                target.invulnerableTime = 0;
                if (target.hurt(level.damageSources().playerAttack(player), dmg)) {
                    hitAny = true;
                    target.setDeltaMovement(look.x * 1.40, 0.35, look.z * 1.40);
                    target.hurtMarked = true;
                }
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
            SHIELD_BREAK_ACTIVE.set(Boolean.FALSE);
        }

        if (hitAny) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            awardEnergy(player, level, st, stack, ExcaliburItem.ENERGY_PER_HIT, false);
        }

        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.25F, 1.08F);
        level.playSound(null, origin.x, origin.y, origin.z,
                ModSounds.EXCALIBUR_SLASH, SoundSource.PLAYERS, 1.15F, 1.38F);
        ModNetworking.sendShakeNearby(level, origin, 14.0, 0.38F, 4);

        ModNetworking.sendExcaliburSyncNearby(level, origin, new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_STRIKE_AIR_BLAST, player.getId(), (byte) ExcaliburMoveType.STRIKE_AIR.id,
                7.0F, origin.x, origin.y, origin.z,
                player.getYRot(), player.getXRot(), (float) range, 0.0F));
    }

    /** Nhánh B Đòn 2 (mốc 0.40s): Bổ thẳng xuống đất phóng 1 lưỡi liềm ánh sáng vàng mảnh trượt mặt đất 7 blocks (không khói bụi)! */
    private static void fireGoldenCrescentWave(ServerPlayer player, ServerLevel level, PlayerExcaliburState st, ItemStack stack) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = (flat.lengthSqr() < 1e-4) ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
        Vec3 origin = player.position().add(flat.scale(0.80)).add(0.0, 0.25, 0.0);
        double range = 7.0; // Chính xác 7 blocks!
        Vec3 end = origin.add(flat.scale(range));
        AABB box = new AABB(origin, end).inflate(1.50, 1.35, 1.50);

        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e.isAttackable() && !ExcaliburSeals.isAlly(player, e));
        float baseDmg = (float) Math.max(ExcaliburItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float dmg = baseDmg * ExcaliburMoveType.GOLDEN_CRESCENT.damageMultiplier;
        boolean hitAny = false;

        OVERRIDE_ARMOR_PIERCE.set(0.25F);
        try {
            for (LivingEntity target : candidates) {
                Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.45, 0.0);
                Vec3 rel = center.subtract(origin);
                double along = rel.x * flat.x + rel.z * flat.z;
                if (along < -0.3 || along > range + 0.5) {
                    continue;
                }
                Vec3 closest = origin.add(flat.scale(along));
                double perpSq = (center.x - closest.x) * (center.x - closest.x)
                        + (center.z - closest.z) * (center.z - closest.z);
                if (perpSq > 1.45 * 1.45 || Math.abs(center.y - origin.y) > 2.2) {
                    continue;
                }
                target.invulnerableTime = 0;
                if (target.hurt(level.damageSources().playerAttack(player), dmg)) {
                    hitAny = true;
                    target.push(flat.x * 0.62, 0.26, flat.z * 0.62);
                    target.hurtMarked = true;
                }
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
        }

        if (hitAny) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            awardEnergy(player, level, st, stack, ExcaliburItem.ENERGY_PER_HIT, false);
        }

        level.playSound(null, origin.x, origin.y, origin.z,
                ModSounds.EXCALIBUR_SLASH, SoundSource.PLAYERS, 1.32F, 0.90F);
        ModNetworking.sendShakeNearby(level, origin, 14.0, 0.42F, 5);

        ModNetworking.sendExcaliburSyncNearby(level, origin, new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_GROUND_WAVE, player.getId(), (byte) ExcaliburMoveType.GOLDEN_CRESCENT.id,
                9.5F, origin.x, origin.y, origin.z,
                player.getYRot(), 0.0F, (float) range, 0.0F));
    }

    private static void handleAirSlash(ServerPlayer player, ServerLevel level) {
        if (isActionLocked(player.getUUID())) {
            return;
        }
        long now = level.getGameTime();
        ItemStack stack = player.getMainHandItem();
        PlayerExcaliburState st = stateOf(player, stack);
        if (st.fallingStarDiving || now <= st.royalCounterBeat2Tick || player.isUsingItem()) {
            return;
        }
        if (now - st.lastSwingTick < st.lastMove.cancelAfterTicks) {
            return;
        }

        ExcaliburMoveType move = resolveStandardMove(player, level, st);
        st.lastMove = move;
        st.lastSwingTick = now;
        st.lastCombatTick = now;
        markUnsheathed(player, level, st, stack, now);

        applyMoveKinematics(player, st, move, now, false);
        player.swing(InteractionHand.MAIN_HAND, true);

        boolean hitAny = false;
        if (move == ExcaliburMoveType.GOLDEN_CRESCENT) {
            hitAny = perform360SpinHit(player, level, null);
        } else if (move != ExcaliburMoveType.AIR_3_FALLING_STAR) {
            hitAny = performForwardArcCleave(player, level, move, null);
        }
        executeSlashEffects(player, level, move, hitAny);
    }

    private static boolean perform360SpinHit(ServerPlayer player, ServerLevel level, @Nullable LivingEntity exclude) {
        double radius = 3.60;
        AABB box = player.getBoundingBox().inflate(radius, 1.4, radius);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e != exclude && e.isAlive() && e.isAttackable() && !ExcaliburSeals.isAlly(player, e));
        if (candidates.isEmpty()) {
            return false;
        }
        float baseDmg = (float) Math.max(ExcaliburItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float dmg = baseDmg * 0.95F;
        boolean any = false;
        for (LivingEntity target : candidates) {
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq > radius * radius) {
                continue;
            }
            target.invulnerableTime = 0;
            if (target.hurt(level.damageSources().playerAttack(player), dmg)) {
                any = true;
                double dist = Math.max(0.1, Math.sqrt(distSq));
                target.push((dx / dist) * 0.55, 0.18, (dz / dist) * 0.55);
                target.hurtMarked = true;
            }
        }
        return any;
    }

    /** Chạm đất của Aerial Judgment Air Hit 3: Gây sát thương AoE & kích hoạt 1 vòng phép 2D nhỏ màu vàng trong 0.3s (không đất đá/khói)! */
    private static void performFallingStarLanding(ServerPlayer player, ServerLevel level,
                                                  PlayerExcaliburState st, ItemStack stack) {
        st.fallingStarDiving = false;
        st.airComboStep = 0;
        st.lastCombatTick = level.getGameTime();
        player.fallDistance = 0.0F;

        Vec3 impactPos = player.position();
        double radius = 3.8;
        AABB box = player.getBoundingBox().inflate(radius, 1.8, radius);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e.isAttackable() && !ExcaliburSeals.isAlly(player, e));
        float baseDmg = (float) Math.max(ExcaliburItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float dmg = baseDmg * ExcaliburMoveType.AIR_3_FALLING_STAR.damageMultiplier;
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
                    target.setDeltaMovement((dx / dist) * 0.72, 0.42, (dz / dist) * 0.72);
                    target.hurtMarked = true;
                }
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
        }
        if (hitAny) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            awardEnergy(player, level, st, stack, ExcaliburItem.ENERGY_PER_HIT * 2.0F, false);
        }

        level.playSound(null, impactPos.x, impactPos.y, impactPos.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.70F, 1.15F);
        level.playSound(null, impactPos.x, impactPos.y, impactPos.z,
                ModSounds.EXCALIBUR_SLASH, SoundSource.PLAYERS, 1.10F, 0.92F);
        ModNetworking.sendShakeNearby(level, impactPos, 16.0, 0.62F, 5);

        // Vòng phép 2D nhỏ màu vàng xoay dưới mũi kiếm trong chính xác 0.30s (6 ticks)!
        ModNetworking.sendExcaliburSyncNearby(level, impactPos, new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_FALLING_STAR_IMPACT, player.getId(), (byte) ExcaliburMoveType.AIR_3_FALLING_STAR.id,
                6.0F, impactPos.x, impactPos.y, impactPos.z,
                player.getYRot(), player.getXRot(), 1.85F, 0.0F));
    }

    private static boolean performForwardArcCleave(ServerPlayer player, ServerLevel level,
                                                   ExcaliburMoveType move, @Nullable LivingEntity exclude) {
        Vec3 look = player.getLookAngle().normalize();
        double reach = (move == ExcaliburMoveType.HIT_3_THRUST) ? 4.65 : 3.95;
        Vec3 center = player.getEyePosition().add(look.scale(move == ExcaliburMoveType.HIT_3_THRUST ? 2.25 : 1.85));
        AABB box = new AABB(center, center).inflate(reach * 0.68);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e != exclude && e.isAlive() && e.isAttackable() && !ExcaliburSeals.isAlly(player, e));
        if (candidates.isEmpty()) {
            return false;
        }
        float baseDmg = (float) Math.max(ExcaliburItem.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float dmg = baseDmg * move.damageMultiplier * (exclude != null ? 0.65F : 0.94F);
        double minDot = switch (move) {
            case HIT_1_SWEEP -> 0.02;
            case HIT_2_RISING, AIR_1_DIAG, AIR_2_DIAG -> 0.14;
            case HIT_3_THRUST -> 0.62;
            default -> 0.25;
        };
        boolean any = false;
        for (LivingEntity target : candidates) {
            Vec3 toTarget = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(player.getEyePosition());
            if (toTarget.lengthSqr() > (reach + 0.6) * (reach + 0.6)) {
                continue;
            }
            if (toTarget.normalize().dot(look) < minDot) {
                continue;
            }
            if (target.hurt(level.damageSources().playerAttack(player), dmg)) {
                any = true;
                double kb = switch (move) {
                    case HIT_4_SLAM -> 0.62;
                    case HIT_3_THRUST -> 0.55;
                    default -> 0.32;
                };
                double liftY = switch (move) {
                    case HIT_2_RISING -> 0.26;
                    case HIT_4_SLAM -> 0.22;
                    default -> 0.10;
                };
                target.push(look.x * kb, liftY, look.z * kb);
                applySpecialOnHit(target, move);
            }
        }
        return any;
    }

    private static void executeSlashEffects(ServerPlayer player, ServerLevel level,
                                            ExcaliburMoveType move, boolean landedHit) {
        PlayerExcaliburState st = stateOf(player, player.getMainHandItem());

        float pitch = switch (move) {
            case HIT_1_SWEEP -> 1.16F;
            case HIT_2_RISING -> 1.24F;
            case HIT_3_THRUST -> 1.36F;
            case GOLDEN_CRESCENT -> 1.26F;
            case AIR_1_DIAG -> 1.28F;
            case AIR_2_DIAG -> 1.34F;
            case AIR_3_FALLING_STAR -> 0.98F;
            default -> 0.94F;
        };
        if (move != ExcaliburMoveType.STRIKE_AIR) {
            level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                    ModSounds.EXCALIBUR_SLASH, SoundSource.PLAYERS,
                    (move == ExcaliburMoveType.HIT_4_SLAM) ? 1.18F : 0.96F, pitch);
        }

        Vec3 anchor = player.position().add(0.0, player.getBbHeight() * 0.62, 0.0);

        if (move == ExcaliburMoveType.HIT_3_THRUST) {
            // Tối giản: Không xả khói bụi! Chỉ rung nhẹ camera khi đâm bộc phá.
            ModNetworking.sendShakeNearby(level, anchor, 10.0, 0.22F, 3);
        } else if (move == ExcaliburMoveType.HIT_4_SLAM) {
            boolean cleaveHit = performForwardArcCleave(player, level, move, null);
            landedHit = landedHit || cleaveHit;
            // Tối giản: Không xả bụi/khói — chỉ vệt chém vàng thẳng đứng sắc nét & tiếng va chạm gọn!
            ModNetworking.sendShakeNearby(level, anchor, 14.0, landedHit ? 0.44F : 0.28F, 5);
            if (landedHit) {
                ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                        ExcaliburSyncPayload.EVENT_HIT_STOP, player.getId(), (byte) move.id,
                        1.8F, anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot(), 0.0F, 0.0F));
            }
        }

        if (landedHit && isHoldingExcalibur(player)) {
            awardEnergy(player, level, st, player.getMainHandItem(), ExcaliburItem.ENERGY_PER_HIT, false);
        }

        ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_SLASH, player.getId(), (byte) move.id,
                move.baseDisplayTicks, anchor.x, anchor.y, anchor.z,
                player.getYRot(), player.getXRot(), 1.8F, 0.0F));
    }

    public static void tryStartUltimate(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !isHoldingExcalibur(player)) {
            return;
        }
        if (isActionLocked(player.getUUID())) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        PlayerExcaliburState st = stateOf(player, stack);

        if (st.ultCooldownTicks > 0) {
            player.displayClientMessage(Component.literal(
                    "§6[Excalibur] Tuyệt kỹ đang hồi chiêu: còn §e" + formatTimer(st.ultCooldownTicks) + "§6!"), true);
            return;
        }
        if (st.energy < ExcaliburItem.MAX_ENERGY - 0.01F) {
            player.displayClientMessage(Component.literal(
                    "§6[Excalibur] Cần đủ 100/100 Năng lượng (hiện có " + Math.round(st.energy) + "/100)"), true);
            return;
        }

        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        ensureFullEnergyStrength(player, st);
        float lockedBaseAttack = (float) Math.max(1.0, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        ItemStack weaponSnapshot = stack.copy();

        st.energy = 0.0F;
        long now = level.getGameTime();
        st.lastCombatTick = now;
        ExcaliburItem.setEnergy(stack, 0.0F);
        markUnsheathed(player, level, st, stack, now);

        ACTIVE_ULTIMATES.put(player.getUUID(), new ActiveUltimateCast(player, level, lockedBaseAttack, weaponSnapshot));

        player.displayClientMessage(Component.literal("§e§l✦ EXCALIBUR!! ✦"), true);
        rallyNearbyAlliesOnRelease(player, level);

        level.playSound(null, player.getX(), player.getY() + 1.6, player.getZ(),
                ModSounds.EXCALIBUR_CHARGE, SoundSource.PLAYERS, 1.38F, 1.16F);

        ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_ULT_START, player.getId(), (byte) ExcaliburMoveType.ULT_EXCALIBUR.id,
                ULT_TOTAL_TICKS, player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), BEAM_RANGE, 1.0F));
        sendPassiveSync(player, level, st, true);
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveUltimateCast>> ultIt = ACTIVE_ULTIMATES.entrySet().iterator();
        while (ultIt.hasNext()) {
            ActiveUltimateCast u = ultIt.next().getValue();
            ServerPlayer p = u.player;
            if (!p.isAlive() || p.isRemoved() || !isHoldingExcalibur(p)) {
                if (u.fired) {
                    PlayerExcaliburState abortSt = PLAYER_STATES.get(p.getUUID());
                    if (abortSt != null) {
                        abortSt.ultCooldownTicks = ULT_COOLDOWN_TICKS;
                        sendPassiveSync(p, u.level, abortSt, true);
                    }
                }
                ultIt.remove();
                continue;
            }
            u.tick++;

            // Tối giản hạt sạch trong quá trình tụ lực: chỉ vài đốm sáng nhỏ bay dọc thân kiếm (bỏ hoàn toàn cột lửa & khói!)
            if (u.tick < ULT_BEAM_FIRE_TICK && (u.tick & 1) == 0) {
                Vec3 bladeAnchor = p.position().add(0.0, u.tick >= ULT_TILT_START_TICK ? 2.35 : 1.95, 0.0);
                u.level.sendParticles(GOLD_SPARK, bladeAnchor.x, bladeAnchor.y, bladeAnchor.z, 2, 0.12, 0.35, 0.12, 0.02);
                if (u.tick == ULT_TILT_START_TICK) {
                    u.level.playSound(null, bladeAnchor.x, bladeAnchor.y, bladeAnchor.z,
                            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.25F, 1.28F);
                } else if (u.tick == ULT_CLEAVE_START_TICK) {
                    u.level.playSound(null, p.getX(), p.getY() + 1.0, p.getZ(),
                            SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.80F, 1.48F);
                }
            }

            if (u.tick == ULT_BEAM_FIRE_TICK && !u.fired) {
                u.fired = true;
                fireExcaliburFlashlightSlash(p, u.level, u.lockedBaseAttack, u.weaponSnapshot);
            }

            if (u.fired && u.tick > ULT_BEAM_FIRE_TICK && u.tick <= ULT_BEAM_END_TICK && (u.tick % 3) == 0) {
                Vec3 origin = p.getEyePosition().add(0.0, -0.18, 0.0);
                ModNetworking.sendShakeNearby(u.level, origin, 24.0, 0.52F, 3);
            }

            if (u.tick >= ULT_TOTAL_TICKS) {
                ultIt.remove();
                PlayerExcaliburState doneSt = PLAYER_STATES.get(p.getUUID());
                if (doneSt != null) {
                    doneSt.ultCooldownTicks = ULT_COOLDOWN_TICKS;
                    p.displayClientMessage(Component.literal(
                            "§6[Excalibur] Tuyệt kỹ đang hồi chiêu §e25s§6!"), true);
                    sendPassiveSync(p, u.level, doneSt, true);
                }
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            long now = level.getGameTime();
            boolean holdingExcalibur = isHoldingExcalibur(player);
            boolean holdingOffhandExcalibur = player.getOffhandItem().is(ModItems.EXCALIBUR);

            PlayerExcaliburState existing = PLAYER_STATES.get(player.getUUID());
            boolean ultCooldownJustFinished = false;
            if (existing != null) {
                if (existing.ultCooldownTicks > 0) {
                    existing.ultCooldownTicks--;
                    if (existing.ultCooldownTicks == 0) {
                        ultCooldownJustFinished = true;
                    }
                }
                if (!holdingExcalibur) {
                    existing.strikeAirFireTick = -1000L;
                    existing.crescentWaveFireTick = -1000L;
                    existing.royalCounterBeat2Tick = -1000L;
                    existing.sheatheClinkTick = -1000L;
                    existing.sheatheEndTick = -1000L;
                    existing.sheathedParryTicks = 0;
                    existing.fallingStarDiving = false;
                    if (existing.appliedFullEnergyStrength) {
                        clearFullEnergyStrength(player, existing);
                    }
                }
            }

            if (!holdingExcalibur && !holdingOffhandExcalibur) {
                continue;
            }

            ItemStack held = holdingExcalibur ? player.getMainHandItem() : player.getOffhandItem();
            PlayerExcaliburState st = stateOf(player, held);

            if (holdingExcalibur) {
                if (st.royalCounterBeat2Tick > 0L && now >= st.royalCounterBeat2Tick) {
                    st.royalCounterBeat2Tick = -1000L;
                    fireRoyalCounterBeat2(player, level, st, held);
                }
                if (st.strikeAirFireTick > 0L && now >= st.strikeAirFireTick) {
                    st.strikeAirFireTick = -1000L;
                    fireStrikeAirBlast(player, level, st, held);
                }
                if (st.crescentWaveFireTick > 0L && now >= st.crescentWaveFireTick) {
                    st.crescentWaveFireTick = -1000L;
                    fireGoldenCrescentWave(player, level, st, held);
                }
                // Mốc 0.40s của animation Cất Kiếm: phát tiếng kim loại khóa vào vỏ ("Clink!") + 3 đốm sáng vàng tại miệng vỏ Avalon!
                if (st.sheatheClinkTick > 0L && now >= st.sheatheClinkTick) {
                    st.sheatheClinkTick = -1000L;
                    float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
                    double fwdX = -Mth.sin(yawRad);
                    double fwdZ = Mth.cos(yawRad);
                    double leftX = Mth.cos(yawRad);
                    double leftZ = Mth.sin(yawRad);
                    Vec3 mouthPos = player.position().add(leftX * 0.34 + fwdX * 0.24, 0.92, leftZ * 0.34 + fwdZ * 0.24);
                    level.playSound(null, mouthPos.x, mouthPos.y, mouthPos.z,
                            SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.42F, 2.00F);
                    level.playSound(null, mouthPos.x, mouthPos.y, mouthPos.z,
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.30F, 1.95F);
                    level.sendParticles(GOLD_SPARK, mouthPos.x, mouthPos.y, mouthPos.z, 3, 0.08, 0.08, 0.08, 0.02);
                    ModNetworking.sendExcaliburSyncNearby(level, mouthPos, new ExcaliburSyncPayload(
                            ExcaliburSyncPayload.EVENT_SHEATHE_CLINK, player.getId(), (byte) ExcaliburMoveType.SHEATHE_AVALON.id,
                            6.0F, mouthPos.x, mouthPos.y, mouthPos.z,
                            player.getYRot(), player.getXRot(), 0.0F, 0.0F));
                }
                boolean activeCombatOrGuard = player.isUsingItem()
                        || ACTIVE_ULTIMATES.containsKey(player.getUUID())
                        || st.fallingStarDiving
                        || now <= st.royalCounterBeat2Tick;
                if (activeCombatOrGuard) {
                    st.lastActiveCombatOrGuardTick = now;
                } else if (st.unsheathed && now >= st.sheatheEndTick
                        && (now - st.lastActiveCombatOrGuardTick) >= AUTO_SHEATHE_IDLE_TICKS) {
                    // Auto-Sheathe: Sau 4.0s (80 ticks) không đánh/đỡ -> tự động tra Excalibur vào vỏ Avalon!
                    startSheatheAvalon(player, level, st, held, now);
                }
                if (now <= st.airHoverUntilTick && !player.onGround() && !st.fallingStarDiving) {
                    Vec3 cur = player.getDeltaMovement();
                    if (cur.y < 0.05) {
                        player.setDeltaMovement(cur.x, 0.05, cur.z);
                        player.fallDistance = 0.0F;
                        player.hurtMarked = true;
                    }
                }
                if (st.fallingStarDiving) {
                    player.fallDistance = 0.0F;
                    if (player.onGround() || (now - st.fallingStarStartTick) > 40L) {
                        performFallingStarLanding(player, level, st, held);
                    } else {
                        Vec3 cur = player.getDeltaMovement();
                        if (cur.y > -1.1) {
                            player.setDeltaMovement(cur.x, -1.3, cur.z);
                            player.hurtMarked = true;
                        }
                    }
                } else if (player.onGround() && (now - st.lastAirHitTick) > 4L) {
                    st.airComboStep = 0;
                }
            }

            boolean fullReady = holdingExcalibur && st.energy >= ExcaliburItem.MAX_ENERGY - 0.01F;
            if (fullReady) {
                if ((now % 10L) == 0L || !st.appliedFullEnergyStrength) {
                    ensureFullEnergyStrength(player, st);
                }
            } else if (!ACTIVE_ULTIMATES.containsKey(player.getUUID()) && st.appliedFullEnergyStrength) {
                clearFullEnergyStrength(player, st);
            }

            if ((now % 10L) == 0L) {
                ExcaliburSeals.Evaluation eval = ExcaliburSeals.evaluate(
                        player, level, now, st.lastCombatTick, st.lastDishonorTick);
                st.sealMask = eval.sealMask();
                st.unlockedSeals = eval.unlockedCount();
                if (eval.unsealed() && !st.wasUnsealed) {
                    st.wasUnsealed = true;
                    ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                            ExcaliburSyncPayload.EVENT_SEAL_UNLOCK, player.getId(), (byte) 0,
                            18.0F, player.getX(), player.getY(), player.getZ(),
                            player.getYRot(), player.getXRot(), eval.unlockedCount(), 1.0F));
                } else if (!eval.unsealed()) {
                    st.wasUnsealed = false;
                }
            }

            if ((now % 20L) == 0L) {
                applyKingsInspirationAura(player, level, st.unlockedSeals >= ExcaliburSeals.UNLOCK_THRESHOLD);
            }

            if (ultCooldownJustFinished && holdingExcalibur) {
                if (st.energy >= ExcaliburItem.MAX_ENERGY - 0.01F) {
                    level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.70F, 1.55F);
                    player.displayClientMessage(Component.literal(
                            "§e§l✦ EXCALIBUR ĐÃ HỒI CHIÊU — BẤM [R] TUNG TUYỆT KỸ! ✦"), true);
                } else {
                    player.displayClientMessage(Component.literal(
                            "§6[Excalibur] Tuyệt kỹ đã hồi chiêu xong!"), true);
                }
            }

            sendPassiveSync(player, level, st, ultCooldownJustFinished);
        }
    }

    private static void fireExcaliburFlashlightSlash(ServerPlayer player, ServerLevel level,
                                                     float lockedBaseAttack, ItemStack weaponSnapshot) {
        float effectiveBaseAttack = Math.max(lockedBaseAttack, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        ItemStack activeWeapon = isHoldingExcalibur(player) ? player.getMainHandItem() : weaponSnapshot;

        Vec3 fwd = player.getLookAngle().normalize();
        Vec3 worldUp = (Math.abs(fwd.y) > 0.98) ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = fwd.cross(worldUp).normalize();
        Vec3 up = right.cross(fwd).normalize();

        Vec3 origin = player.getEyePosition().add(0.0, -0.15, 0.0);
        Vec3 endCenter = origin.add(fwd.scale(BEAM_RANGE));
        AABB searchBox = new AABB(origin, endCenter).inflate(BEAM_END_HALF_H + 2.2);

        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && e.isAttackable() && !ExcaliburSeals.isAlly(player, e));

        TRUE_DAMAGE_ACTIVE.set(Boolean.TRUE);
        try {
            DamageSource dmgSource = level.damageSources().playerAttack(player);
            for (LivingEntity target : candidates) {
                Vec3 rel = target.getBoundingBox().getCenter().subtract(origin);
                double along = rel.dot(fwd);
                if (along < -0.4 || along > BEAM_RANGE + target.getBbWidth() * 0.5) {
                    continue;
                }
                double frac = Mth.clamp(along / BEAM_RANGE, 0.0, 1.0);
                double halfW = Mth.lerp(frac, BEAM_START_HALF_W, BEAM_END_HALF_W) + target.getBbWidth() * 0.45;
                double halfH = Mth.lerp(frac, BEAM_START_HALF_H, BEAM_END_HALF_H) + target.getBbHeight() * 0.45;

                double latX = Math.abs(rel.dot(right));
                double latY = Math.abs(rel.dot(up));
                if (latX > halfW || latY > halfH) {
                    continue;
                }

                if (target instanceof Player victimPlayer && victimPlayer.isBlocking()) {
                    victimPlayer.disableShield();
                }
                float resonatedBase = EnchantmentHelper.modifyDamage(level, activeWeapon, target, dmgSource, effectiveBaseAttack);
                float finalUltDamage = resonatedBase * ULT_DAMAGE_MULTIPLIER;
                target.invulnerableTime = 0;
                target.hurt(dmgSource, finalUltDamage);

                double launchSpeed = 2.40;
                double liftY = Math.max(0.60, fwd.y * 0.60 + 0.60);
                target.setDeltaMovement(target.getDeltaMovement().scale(0.15)
                        .add(fwd.x * launchSpeed, liftY, fwd.z * launchSpeed));
                target.hurtMarked = true;
                target.igniteForSeconds(4);

                level.sendParticles(GOLD_SPARK,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        8, 0.35, 0.35, 0.35, 0.06);
            }
        } finally {
            TRUE_DAMAGE_ACTIVE.set(Boolean.FALSE);
        }

        // Sạch sẽ, không khói mù che màn hình! Chỉ vài đốm sáng nhỏ dọc tia 20 blocks.
        for (int step = 2; step <= 10; step += 2) {
            double d = (step / 10.0) * BEAM_RANGE;
            Vec3 p = origin.add(fwd.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.45, 0.45, 0.45, 0.03);
        }

        PlayerExcaliburState st = stateOf(player, player.getMainHandItem());
        clearFullEnergyStrength(player, st);

        level.playSound(null, origin.x, origin.y, origin.z,
                ModSounds.EXCALIBUR_BEAM, SoundSource.PLAYERS, 1.90F, 1.05F);
        ModNetworking.sendShakeNearby(level, origin, 32.0, 0.85F, 16);

        player.displayClientMessage(Component.literal("§6§l✦ EXCALIBUR!! ✦"), true);

        // Chùm tia + 3 Vòng Phép đứng giữ từ Tick 27 (1.35s) tới Tick 52 (2.60s, thu nhỏ trong 2.30–2.60s)!
        ModNetworking.sendExcaliburSyncNearby(level, player.position(), new ExcaliburSyncPayload(
                ExcaliburSyncPayload.EVENT_BEAM_FIRE, player.getId(), (byte) ExcaliburMoveType.ULT_EXCALIBUR.id,
                (float) (ULT_TOTAL_TICKS - ULT_BEAM_FIRE_TICK), origin.x, origin.y, origin.z,
                player.getYRot(), player.getXRot(), BEAM_RANGE, 1.0F));
        sendPassiveSync(player, level, st, true);
    }

    private static void applyKingsInspirationAura(ServerPlayer wielder, ServerLevel level, boolean unsealed) {
        AABB box = wielder.getBoundingBox().inflate(14.0);
        List<LivingEntity> allies = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> ExcaliburSeals.isAlly(wielder, e));
        if (allies.isEmpty()) {
            return;
        }
        int strAmp = unsealed ? 2 : 1;
        for (LivingEntity ally : allies) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 45, strAmp, true, false, true));
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, 1, true, false, true));
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 45, 0, true, false, true));
            MobEffectInstance abs = ally.getEffect(MobEffects.ABSORPTION);
            if (abs == null || abs.getDuration() < 15) {
                ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 0, true, false, true));
            }
            level.sendParticles(GOLD_SPARK, ally.getX(), ally.getY() + 0.25, ally.getZ(),
                    1, 0.30, 0.10, 0.30, 0.01);
        }
    }

    private static void rallyNearbyAlliesOnRelease(ServerPlayer wielder, ServerLevel level) {
        AABB box = wielder.getBoundingBox().inflate(16.0);
        for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> ExcaliburSeals.isAlly(wielder, e))) {
            ally.heal(6.0F);
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 140, 1, false, true, true));
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 140, 1, false, true, true));
            level.sendParticles(GOLD_SPARK, ally.getX(), ally.getY() + 1.0, ally.getZ(),
                    6, 0.40, 0.50, 0.40, 0.04);
        }
    }

    private static void sendPassiveSync(ServerPlayer player, ServerLevel level,
                                        PlayerExcaliburState st, boolean force) {
        long now = level.getGameTime();
        byte flags = 0;
        if (st.unsheathed) flags |= ExcaliburPassiveSyncPayload.FLAG_UNSHEATHED;

        int roundedEnergy = Math.round(st.energy);
        boolean visualChanged = (flags != st.lastBroadcastVisualFlags);
        boolean anyTimerActive = st.ultCooldownTicks > 0;
        int hash = ((roundedEnergy * 31 + st.sealMask) * 31 + flags);
        long ticksSinceSync = now - st.lastPassiveSyncTick;
        if (!force && !visualChanged && hash == st.lastSyncedHash && (!anyTimerActive || ticksSinceSync < 20L)) {
            return;
        }
        st.lastSyncedHash = hash;
        st.lastPassiveSyncTick = now;

        ExcaliburPassiveSyncPayload payload = new ExcaliburPassiveSyncPayload(
                player.getId(),
                st.energy,
                (short) (st.sealMask & 0x1FFF),
                flags,
                (short) Mth.clamp(st.ultCooldownTicks, 0, 30000));
        if (force || visualChanged) {
            st.lastBroadcastVisualFlags = flags;
            ModNetworking.sendExcaliburPassiveNearby(level, player.position(), payload);
        } else {
            ModNetworking.sendExcaliburPassiveToPlayer(player, payload);
        }
    }

    public static String formatTimer(int ticks) {
        int totalSec = Math.max(0, (ticks + 19) / 20);
        int min = totalSec / 60;
        int sec = totalSec % 60;
        if (min > 0) {
            return min + "p" + (sec < 10 ? "0" : "") + sec + "s";
        }
        return sec + "s";
    }
}
