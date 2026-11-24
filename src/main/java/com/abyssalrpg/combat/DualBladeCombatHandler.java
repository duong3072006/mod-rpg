package com.abyssalrpg.combat;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.entity.mythic.cinder.CinderDragonHitboxPartEntity;
import com.abyssalrpg.entity.mythic.hydra.HydraHitboxPartEntity;
import com.abyssalrpg.entity.mythic.jormungandr.JormungandrHitboxPartEntity;
import com.abyssalrpg.entity.mythic.nightmare.NightmareHitboxPartEntity;
import com.abyssalrpg.network.DualBladeComboPayload;
import com.abyssalrpg.network.DualBladeDashVfxPayload;
import com.abyssalrpg.network.DualBladeSlashC2SPayload;
import com.abyssalrpg.network.DualBladeVfxPayload;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModDamageTypes;
import com.abyssalrpg.registry.ModEffects;
import com.abyssalrpg.registry.ModEnchantments;
import com.abyssalrpg.registry.ModItems;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trung tâm điều phối chiến đấu Server-authoritative của Dual Blade (v4 — Netherite–Diamond):
 * - Chỉ số chuẩn: `30.0` sát thương, `2.5` tốc đánh (`8.0` tick/đòn), `7` tick i-frame, độ bền `1561`.
 * - Chuỗi 5 nhịp rõ nét (`COMBO_1..5`) + `SPRINT_THRUST` + `AIR_SLASH` (Hit 4 quét AOE 360° bán kính 3.5 block).
 * - Bộ 4 Enchantment chuyên biệt (`Lifesteal`, `Armor Pierce`, `Critical Strike`, `Deep Wounds`).
 * - Chiêu Chuột Phải **"Netherite–Diamond Flash Dash" (18 Block · 10 Hit · ×1.5)**:
 *   + Pha 1 (6 tick, `ticks 0..5`): Khuỵu sâu nén đà đưa 2 kiếm (1 thuận + 1 ngược) chĩa ngược ra sau lưng.
 *   + Pha 2 (7 tick, `ticks 6..12`, `0.35s`): Phóng lướt **`18.0` block** (`~2.57` block/tick, sub-step `0.5` block, tối đa 36 bước,
 *     chặn tường rắn, XUYÊN QUA mob & boss, miễn sát thương/rơi, `connection.teleport(..., KEEP_LOOK_FLAGS)` -> **0 rubber-band & 0 moved too quickly**),
 *     khử trùng lặp proxy hitbox Boss bằng `LinkedHashSet<UUID>`.
 *   + Pha 3 (5 tick, `ticks 13..17`): Hãm chân xoay chéo & nổ đúng **10 hit tức thì** (`2 hit/tick`, reset `invulnerableTime = 0` mỗi hit),
 *     mỗi hit `×1.5` dame cơ bản (`45.0` dmg, tùy chỉnh `dashDamageMultiplier`), xuyên **`+10%` phòng thủ** (cộng dồn `Armor Pierce`
 *     trên cả giáp vanilla lẫn `BossDefenseHolder`), gắn Chảy Máu, tương thích Crit/Deep Wounds/Lifesteal (trần `maxDashLifestealTotal`),
 *     và phát **1 payload VFX duy nhất** (`DualBladeDashVfxPayload`).
 */
public final class DualBladeCombatHandler {
    private static final ResourceLocation OFFHAND_PENALTY_MOD_ID = AbyssalRpgMod.id("dual_knife_offhand_penalty");
    private static final int COMBO_RESET_TICKS = 42;
    private static final int COMBO_LENGTH = DualBladeMoveType.MAIN_COMBO_COUNT;
    private static final Set<RelativeMovement> KEEP_LOOK_FLAGS =
            EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT);

    private static final Map<UUID, PlayerCombatState> PLAYER_STATES = new HashMap<>();
    private static final Map<UUID, BleedTracker> BLEED_TRACKERS = new HashMap<>();
    private static final Map<UUID, ActiveFlashDash> ACTIVE_DASHES = new HashMap<>();

    private static final ThreadLocal<ActiveHitContext> ACTIVE_HIT = new ThreadLocal<>();
    private static final ThreadLocal<Float> OVERRIDE_ARMOR_PIERCE = new ThreadLocal<>();
    private static final ThreadLocal<Float> OVERRIDE_POST_MITIGATION = new ThreadLocal<>();
    private static int cleanupCounter = 0;

    private DualBladeCombatHandler() {}

    public static final class ActiveHitContext {
        public final Player player;
        public final Entity rawTarget;
        @Nullable
        public final LivingEntity realTarget;
        public final ItemStack weaponStack;
        public final float attackStrength;
        public final boolean directFullHit;
        public final int comboStep;
        public final DualBladeMoveType moveType;
        public final float lifestealPercent;
        public final float armorPiercePercent;
        public final float deepWoundsDamageBonus;
        public final float deepWoundsBleedBonus;
        public final boolean criticalTriggered;
        public final float criticalMultiplier;
        public final int hurtResistanceTicks;
        public final boolean offhandOccupied;
        public final float targetHpBeforeHit;
        public boolean attackExecuted = false;
        public boolean hitLanded = false;
        public float finalRawDamage = 0.0F;
        public float postMitigationDamage = 0.0F;

        ActiveHitContext(Player player, Entity rawTarget, @Nullable LivingEntity realTarget,
                         ItemStack weaponStack, float attackStrength, boolean directFullHit,
                         int comboStep, DualBladeMoveType moveType,
                         float lifestealPercent, float armorPiercePercent,
                         float deepWoundsDamageBonus, float deepWoundsBleedBonus,
                         boolean criticalTriggered, float criticalMultiplier,
                         int hurtResistanceTicks, boolean offhandOccupied, float targetHpBeforeHit) {
            this.player = player;
            this.rawTarget = rawTarget;
            this.realTarget = realTarget;
            this.weaponStack = weaponStack;
            this.attackStrength = attackStrength;
            this.directFullHit = directFullHit;
            this.comboStep = comboStep;
            this.moveType = moveType;
            this.lifestealPercent = lifestealPercent;
            this.armorPiercePercent = armorPiercePercent;
            this.deepWoundsDamageBonus = deepWoundsDamageBonus;
            this.deepWoundsBleedBonus = deepWoundsBleedBonus;
            this.criticalTriggered = criticalTriggered;
            this.criticalMultiplier = criticalMultiplier;
            this.hurtResistanceTicks = hurtResistanceTicks;
            this.offhandOccupied = offhandOccupied;
            this.targetHpBeforeHit = targetHpBeforeHit;
        }
    }

    private static final class PlayerCombatState {
        int nextComboStep = 0;
        long lastSwingGameTime = -1000L;
        long lastLungeGameTime = -1000L;
    }

    private static final class BleedTracker {
        long lastTickGameTime;
        @Nullable
        UUID lastAttackerUuid;
        float deepWoundsBleedBonus;

        BleedTracker(long lastTickGameTime, @Nullable UUID lastAttackerUuid, float deepWoundsBleedBonus) {
            this.lastTickGameTime = lastTickGameTime;
            this.lastAttackerUuid = lastAttackerUuid;
            this.deepWoundsBleedBonus = deepWoundsBleedBonus;
        }
    }

    /** Trạng thái 3 pha Server-authoritative cho chiêu Chuột Phải Lướt Chém 10 Nhát (`Crimson-Gold Flash Dash`). */
    private static final class ActiveFlashDash {
        final ServerPlayer player;
        final ServerLevel level;
        final float lifestealPct;
        final float totalArmorPierce;
        final float critChance;
        final float critMult;
        final float deepWoundsBonus;
        final float perHitBaseDamage;
        final int totalHits;
        final double totalDistance;
        final float maxTotalLifesteal;

        int tick = 0;
        Vec3 startPos;
        Vec3 endPos;
        Vec3 dashDir;
        float dashYawDeg;
        boolean wallStopped = false;
        double traveledDistance = 0.0;
        int hitsDelivered = 0;
        float totalHealedSoFar = 0.0F;
        boolean bleedingApplied = false;
        final LinkedHashSet<UUID> hitTargetUuids = new LinkedHashSet<>();

        ActiveFlashDash(ServerPlayer player, ServerLevel level, ItemStack stack) {
            this.player = player;
            this.level = level;
            WeaponsConfig cfg = WeaponsConfig.get();
            int lifeLv = ModEnchantments.getLevel(level, stack, ModEnchantments.LIFESTEAL);
            int pierceLv = ModEnchantments.getLevel(level, stack, ModEnchantments.ARMOR_PIERCE);
            int critLv = ModEnchantments.getLevel(level, stack, ModEnchantments.CRITICAL_STRIKE);
            int woundsLv = ModEnchantments.getLevel(level, stack, ModEnchantments.DEEP_WOUNDS);

            this.lifestealPct = cfg.getLifestealPercent(lifeLv);
            // Cộng dồn xuyên thủ chiêu lướt (+10%) với Enchant Armor Pierce (5/12/20% -> tối đa 30%!)
            this.totalArmorPierce = Mth.clamp(cfg.dashArmorPierceBonus + cfg.getArmorPiercePercent(pierceLv), 0.0F, 0.85F);
            this.critChance = cfg.getCriticalChance(critLv);
            this.critMult = cfg.criticalDamageMultiplier;
            this.deepWoundsBonus = cfg.getDeepWoundsBonus(woundsLv);
            this.totalHits = cfg.dashHitCount;
            this.totalDistance = cfg.dashDistance;
            this.maxTotalLifesteal = cfg.maxDashLifestealTotal;

            double baseDmg = cfg.dualBladeDamage;
            this.perHitBaseDamage = cfg.dashMultiplierPerHit
                    ? (float) (baseDmg * cfg.dashDamageMultiplier)
                    : (float) ((baseDmg * cfg.dashDamageMultiplier) / Math.max(1, this.totalHits));

            this.startPos = player.position();
            this.endPos = this.startPos;
            this.dashYawDeg = player.getYRot();
            this.dashDir = computeDashDirection(player);
        }
    }

    /** Giải quyết mọi proxy hitbox multipart của Boss về đúng thực thể Boss chính (`LivingEntity`). */
    @Nullable
    public static LivingEntity resolveRealLivingTarget(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof LivingEntity living) {
            return living;
        }
        if (entity instanceof CinderDragonHitboxPartEntity part) {
            return part.getParentBoss();
        }
        if (entity instanceof NightmareHitboxPartEntity part) {
            return part.getParentBoss();
        }
        if (entity instanceof HydraHitboxPartEntity part) {
            return part.getParentBoss();
        }
        if (entity instanceof JormungandrHitboxPartEntity part) {
            return part.getParentBoss();
        }
        return null;
    }

    public static boolean isHoldingDualBlade(LivingEntity entity) {
        return entity != null && entity.getMainHandItem().is(ModItems.DUAL_KNIFE);
    }

    public static boolean isOffhandPenaltyActive(LivingEntity entity) {
        return WeaponsConfig.get().requireEmptyOffhand
                && isHoldingDualBlade(entity)
                && !entity.getOffhandItem().isEmpty();
    }

    public static boolean isPlayerDashing(Player player) {
        return player != null && ACTIVE_DASHES.containsKey(player.getUUID());
    }

    /** Miễn sát thương va chạm/rơi trong cửa sổ lướt (ticks 4..13 = 10 ticks, phủ trọn 6 tick phóng 15 block) để boss không hất văng giữa đường. */
    public static boolean isDashInvulnerable(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Player player) || source == null) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        ActiveFlashDash dash = ACTIVE_DASHES.get(player.getUUID());
        return dash != null && dash.tick >= 4 && dash.tick <= 13;
    }

    @Nullable
    public static ActiveHitContext getActiveHit() {
        return ACTIVE_HIT.get();
    }

    /**
     * Trả về tổng tỉ lệ xuyên giáp/phòng thủ hiệu lực cho đòn đánh Dual Blade hoặc chiêu Lướt Chém 10 Nhát.
     * Hoạt động thống nhất cho CẢ giáp vanilla (`LivingEntityMixin`) LẪN `BossDefenseHolder` (`AbstractGeoMythicBoss`).
     */
    public static float getActiveArmorPierceFraction(LivingEntity victim, DamageSource source) {
        if (source == null || source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(ModDamageTypes.BLEEDING)) {
            return 0.0F;
        }
        Float override = OVERRIDE_ARMOR_PIERCE.get();
        if (override != null && override > 0.0F) {
            return override;
        }
        ActiveHitContext ctx = ACTIVE_HIT.get();
        if (ctx != null && ctx.directFullHit && ctx.realTarget == victim && source.getEntity() == ctx.player) {
            return ctx.armorPiercePercent;
        }
        return 0.0F;
    }

    /** Ghi nhận sát thương thực tế sau trừ giáp/BossDefenseHolder & kháng phép để Lifesteal hồi máu chuẩn xác. */
    public static void recordPostMitigationDamage(LivingEntity victim, DamageSource source, float finalDamage) {
        if (victim == null || victim.level().isClientSide || source == null || source.is(ModDamageTypes.BLEEDING)) {
            return;
        }
        if (OVERRIDE_ARMOR_PIERCE.get() != null) {
            OVERRIDE_POST_MITIGATION.set(Math.max(0.0F, finalDamage));
        }
        ActiveHitContext ctx = ACTIVE_HIT.get();
        if (ctx != null && ctx.directFullHit && ctx.realTarget == victim && source.getEntity() == ctx.player) {
            ctx.postMitigationDamage = Math.max(0.0F, finalDamage);
        }
    }

    // ==================== CHIÊU CHUỘT PHẢI: CRIMSON-GOLD FLASH DASH (10 BLOCK · 10 HIT) ====================

    /** Khởi động chiêu Chuột Phải Lướt Chém 10 Nhát (Pha 1 Nén Đà -> Pha 2 Lướt 15 Block -> Pha 3 Nổ 10 Hit). */
    public static boolean startFlashDash(ServerPlayer player, ItemStack stack) {
        if (!player.isAlive() || player.isSpectator() || !stack.is(ModItems.DUAL_KNIFE)) {
            return false;
        }
        WeaponsConfig cfg = WeaponsConfig.get();
        if (cfg.requireEmptyOffhand && !player.getOffhandItem().isEmpty()) {
            return false;
        }
        if (ACTIVE_DASHES.containsKey(player.getUUID()) || player.getCooldowns().isOnCooldown(ModItems.DUAL_KNIFE)) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        player.getCooldowns().addCooldown(ModItems.DUAL_KNIFE, cfg.dashCooldownTicks);
        if (cfg.dashDurabilityCost > 0 && !player.getAbilities().instabuild) {
            stack.hurtAndBreak(cfg.dashDurabilityCost, player, EquipmentSlot.MAINHAND);
        }

        ActiveFlashDash dash = new ActiveFlashDash(player, level, stack);
        ACTIVE_DASHES.put(player.getUUID(), dash);

        // Phát âm thanh rút khí nén đà + đồng bộ Pha 1 (tư thế chạy thấp kéo 2 kiếm thẳng ra sau lưng!)
        level.playSound(null, player.getX(), player.getY() + 0.9, player.getZ(),
                ModSounds.DUAL_BLADE_DASH_CHARGE, SoundSource.PLAYERS, 0.92F, 1.04F);
        ModNetworking.sendDualBladeComboNearby(level, player.position(), new DualBladeComboPayload(
                player.getId(),
                DualBladeMoveType.FLASH_DASH_CHARGE.id,
                DualBladeMoveType.FLASH_DASH_CHARGE.baseDisplayTicks,
                1.0F,
                player.getYRot(),
                (byte) 0,
                false, 0.0, 0.0, 0.0
        ));
        return true;
    }

    private static Vec3 computeDashDirection(Player player) {
        float pitchRad = Mth.clamp(player.getXRot(), -20.0F, 20.0F) * Mth.DEG_TO_RAD;
        float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
        double h = Math.cos(pitchRad);
        double dx = -Math.sin(yawRad) * h;
        double dy = -Math.sin(pitchRad) * 0.55;
        double dz = Math.cos(yawRad) * h;
        Vec3 v = new Vec3(dx, dy, dz);
        return v.lengthSqr() > 1e-4 ? v.normalize() : new Vec3(0.0, 0.0, 1.0);
    }

    /** Trả về true khi chiêu Lướt Chém đã hoàn tất cả 3 pha. */
    private static boolean tickActiveFlashDash(ActiveFlashDash dash) {
        ServerPlayer player = dash.player;
        ServerLevel level = dash.level;
        if (!player.isAlive() || player.isRemoved() || player.level() != level) {
            return true;
        }

        int t = dash.tick;
        dash.tick++;

        // --- PHA 1 (ticks 0..5 = 0.30s): Khuỵu sâu nén đà kéo 2 kiếm ra sau ---
        if (t < 6) {
            Vec3 curVel = player.getDeltaMovement();
            player.setDeltaMovement(curVel.x * 0.35, Math.min(curVel.y, 0.0), curVel.z * 0.35);
            player.hurtMarked = true;
            player.resetFallDistance();
            // Cho phép tinh chỉnh hướng nhìn mượt tới sát khoảnh khắc phóng!
            dash.dashDir = computeDashDirection(player);
            dash.dashYawDeg = player.getYRot();
            dash.startPos = player.position();
            return false;
        }

        // --- PHA 2 (ticks 6..11 = 6 ticks = 0.30s): Phóng lướt 15.0 block (2.50 block/tick, sub-step 0.5 block -> tối đa 30 bước) ---
        if (t == 6) {
            dash.startPos = player.position();
            dash.endPos = dash.startPos;
            dash.dashDir = computeDashDirection(player);
            dash.dashYawDeg = player.getYRot();
            level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                    ModSounds.DUAL_BLADE_DASH_WHOOSH, SoundSource.PLAYERS, 1.0F, 1.02F);
            ModNetworking.sendDualBladeComboNearby(level, player.position(), new DualBladeComboPayload(
                    player.getId(),
                    DualBladeMoveType.FLASH_DASH_LAUNCH.id,
                    DualBladeMoveType.FLASH_DASH_LAUNCH.baseDisplayTicks,
                    1.0F,
                    dash.dashYawDeg,
                    (byte) 1,
                    false, 0.0, 0.0, 0.0
            ));
        }

        if (t >= 6 && t <= 11) {
            double perTickDist = dash.totalDistance / 6.0; // 15.0 / 6 = 2.50 blocks/tick (5 sub-step x 0.5 block, 0 moved too quickly!)
            double stepSize = 0.5;
            int subSteps = Math.max(1, (int) Math.ceil(perTickDist / stepSize));
            double actualStep = perTickDist / subSteps;

            Vec3 curPos = player.position();
            for (int s = 0; s < subSteps && !dash.wallStopped && dash.traveledDistance < dash.totalDistance; s++) {
                double rem = Math.min(actualStep, dash.totalDistance - dash.traveledDistance);
                Vec3 nextPos = curPos.add(dash.dashDir.scale(rem));

                // CHỈ kiểm tra va chạm khối rắn (`getBlockCollisions`) — BỎ QUA HOÀN TOÀN va chạm entity để XUYÊN QUA mob & boss!
                AABB nextBox = player.getBoundingBox().move(nextPos.subtract(player.position()));
                boolean clear = !level.getBlockCollisions(player, nextBox).iterator().hasNext();
                if (!clear) {
                    // Thử bước lên bậc thang / slab cao tối đa 0.62 block
                    Vec3 stepped = nextPos.add(0.0, 0.62, 0.0);
                    AABB stepBox = player.getBoundingBox().move(stepped.subtract(player.position()));
                    if (!level.getBlockCollisions(player, stepBox).iterator().hasNext()) {
                        nextPos = stepped;
                        clear = true;
                    }
                }
                if (!clear) {
                    dash.wallStopped = true;
                    break;
                }

                // Quét toàn bộ thực thể trong hành lang lướt rộng ~2.3 x cao ~2.5 block và khử trùng lặp proxy hitbox Boss!
                collectDashCorridorTargets(level, player, curPos, nextPos, dash.hitTargetUuids);
                curPos = nextPos;
                dash.traveledDistance += rem;
            }

            // Dịch chuyển Server-authoritative từng tick: giữ nguyên góc nhìn X_ROT / Y_ROT, 0 rubber-band & 0 moved too quickly!
            player.setPos(curPos.x, curPos.y, curPos.z);
            player.setDeltaMovement(dash.dashDir.scale(0.32));
            player.resetFallDistance();
            player.hurtMarked = true;
            if (player.connection != null) {
                player.connection.teleport(curPos.x, curPos.y, curPos.z, 0.0F, 0.0F, KEEP_LOOK_FLAGS);
            }
            dash.endPos = curPos;

            // Kết thúc Pha 2 ở tick 11: gửi 1 Payload VFX duy nhất (2 dải lụa Xanh Kim Cương–Netherite 15 block + 10 vệt chém) + vào Pha 3!
            if (t == 11) {
                List<Vec3> targetCenters = new ArrayList<>();
                for (UUID uuid : dash.hitTargetUuids) {
                    Entity ent = level.getEntity(uuid);
                    if (ent instanceof LivingEntity living && living.isAlive()) {
                        targetCenters.add(living.getBoundingBox().getCenter());
                        if (targetCenters.size() >= DualBladeDashVfxPayload.MAX_SYNC_TARGETS) {
                            break;
                        }
                    }
                }
                Vec3 mid = dash.startPos.lerp(dash.endPos, 0.5);
                ModNetworking.sendDualBladeDashVfxNearby(level, mid, new DualBladeDashVfxPayload(
                        player.getId(),
                        dash.startPos.x, dash.startPos.y + player.getBbHeight() * 0.52, dash.startPos.z,
                        dash.endPos.x, dash.endPos.y + player.getBbHeight() * 0.52, dash.endPos.z,
                        dash.dashYawDeg,
                        targetCenters
                ));
                ModNetworking.sendDualBladeComboNearby(level, dash.endPos, new DualBladeComboPayload(
                        player.getId(),
                        DualBladeMoveType.FLASH_DASH_END.id,
                        DualBladeMoveType.FLASH_DASH_END.baseDisplayTicks,
                        1.0F,
                        dash.dashYawDeg,
                        (byte) 1,
                        false, 0.0, 0.0, 0.0
                ));
                if (!targetCenters.isEmpty()) {
                    level.playSound(null, dash.endPos.x, dash.endPos.y + 1.0, dash.endPos.z,
                            ModSounds.DUAL_BLADE_DASH_FLURRY, SoundSource.PLAYERS, 1.0F, 1.04F);
                }
            }
            return false;
        }

        // --- PHA 3 (ticks 12..16 = 5 ticks): Hãm thế & Nổ đúng 10 Hit Tức Thì (`2 hit/tick`, reset i-frame từng hit!) ---
        player.resetFallDistance();
        if (dash.hitsDelivered < dash.totalHits && !dash.hitTargetUuids.isEmpty()) {
            int ticksLeft = Math.max(1, 17 - t);
            int remainingHits = dash.totalHits - dash.hitsDelivered;
            int hitsThisTick = (ticksLeft == 1) ? remainingHits : Math.max(1, (int) Math.ceil(remainingHits / (double) ticksLeft));
            deliverFlashDashFlurryHits(dash, hitsThisTick);
            dash.hitsDelivered += hitsThisTick;
        }

        return t >= 16;
    }

    private static void collectDashCorridorTargets(ServerLevel level, ServerPlayer player,
                                                   Vec3 p0, Vec3 p1, LinkedHashSet<UUID> outRealUuids) {
        AABB corridor = new AABB(p0, p1).inflate(1.15, 1.25, 1.15);
        List<Entity> found = level.getEntities(player, corridor, e -> e.isAlive() && e.isPickable() && e != player);
        for (Entity cand : found) {
            LivingEntity real = resolveRealLivingTarget(cand);
            if (real == null || !real.isAlive() || real == player || real.isAlliedTo(player)) {
                continue;
            }
            // LinkedHashSet<UUID>: đảm bảo mỗi Boss đa hitbox (Hydra/Nightmare/Jörmungandr/Cinder) CHỈ được ghi nhận 1 LẦN DUY NHẤT!
            outRealUuids.add(real.getUUID());
        }
    }

    private static void deliverFlashDashFlurryHits(ActiveFlashDash dash, int hitsThisTick) {
        ServerPlayer player = dash.player;
        ServerLevel level = dash.level;

        OVERRIDE_ARMOR_PIERCE.set(dash.totalArmorPierce);
        try {
            for (UUID targetUuid : dash.hitTargetUuids) {
                Entity ent = level.getEntity(targetUuid);
                if (!(ent instanceof LivingEntity target) || !target.isAlive()) {
                    continue;
                }

                // Gắn Chảy Máu (+3 tầng / làm mới thời lượng) ngay ở nhịp đầu để Deep Wounds cộng hưởng trọn chuỗi!
                if (!dash.bleedingApplied) {
                    applyBleeding(level, player, target, dash.deepWoundsBonus, 3);
                }

                for (int h = 0; h < hitsThisTick; h++) {
                    if (!target.isAlive()) {
                        break;
                    }
                    float hitDmg = dash.perHitBaseDamage;
                    if (dash.deepWoundsBonus > 0.0F && target.hasEffect(ModEffects.BLEEDING)) {
                        hitDmg *= (1.0F + dash.deepWoundsBonus);
                    }
                    boolean isCrit = dash.critChance > 0.0F && level.random.nextFloat() < dash.critChance;
                    if (isCrit) {
                        hitDmg *= dash.critMult;
                    }

                    // Reset invulnerableTime = 0 TRƯỚC MỖI HIT để đảm bảo đủ 10/10 hit đều gây sát thương thật!
                    float hpBefore = target.getHealth();
                    Vec3 savedVel = target.getDeltaMovement();
                    target.invulnerableTime = 0;
                    target.hurtTime = 0;
                    OVERRIDE_POST_MITIGATION.remove();

                    DamageSource src = player.damageSources().playerAttack(player);
                    boolean dealt = target.hurt(src, hitDmg);
                    if (dealt) {
                        // Giữ mục tiêu đứng yên ăn trọn 10 nhát chém (tránh knockback văng xa giữa chừng)
                        if (target.isAlive()) {
                            target.setDeltaMovement(savedVel.x * 0.5, savedVel.y, savedVel.z * 0.5);
                        }
                        // Lifesteal tương thích có trần tổng `maxDashLifestealTotal` cho cả cú lướt!
                        if (dash.lifestealPct > 0.0F && dash.totalHealedSoFar < dash.maxTotalLifesteal) {
                            Float postMit = OVERRIDE_POST_MITIGATION.get();
                            float actualDealt = (postMit != null && postMit > 0.0F)
                                    ? Math.min(postMit, Math.max(0.0F, hpBefore))
                                    : Math.max(0.0F, hpBefore - target.getHealth());
                            float wantHeal = actualDealt * dash.lifestealPct;
                            float allowed = Math.min(wantHeal, dash.maxTotalLifesteal - dash.totalHealedSoFar);
                            if (allowed > 0.01F) {
                                player.heal(allowed);
                                dash.totalHealedSoFar += allowed;
                            }
                        }
                    }
                }
            }
            dash.bleedingApplied = true;
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
            OVERRIDE_POST_MITIGATION.remove();
        }
    }

    // ==================== ĐÁNH THƯỜNG 5 NHỊP + SPRINT/AIR + CYCLONE 360° ====================

    public static int computeHurtResistanceTicks(Player player) {
        WeaponsConfig cfg = WeaponsConfig.get();
        int base = cfg.dualBladeHurtResistanceTicks;
        double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        if (speed >= 3.50) {
            return Math.min(base, 5);
        }
        if (speed >= 2.95) {
            return Math.min(base, 6);
        }
        return base;
    }

    @Nullable
    public static ActiveHitContext beginPlayerAttack(Player player, Entity rawTarget) {
        if (player.level().isClientSide || !isHoldingDualBlade(player)) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        LivingEntity realTarget = resolveRealLivingTarget(rawTarget);
        float strength = player.getAttackStrengthScale(0.5F);
        boolean offhandOccupied = isOffhandPenaltyActive(player);
        boolean directFullHit = strength >= 0.75F;

        PlayerCombatState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerCombatState());
        long now = player.level().getGameTime();
        if (now - st.lastSwingGameTime > COMBO_RESET_TICKS) {
            st.nextComboStep = 0;
        }
        int comboStep = offhandOccupied ? 0 : st.nextComboStep;
        DualBladeMoveType moveType = resolveMoveType(player, comboStep, offhandOccupied);

        WeaponsConfig cfg = WeaponsConfig.get();
        float lifestealPct = 0.0F;
        float armorPiercePct = 0.0F;
        float deepWoundsDmgBonus = 0.0F;
        float deepWoundsBleedBonus = 0.0F;
        boolean critTriggered = false;
        float critMult = 1.0F;

        if (realTarget != null && directFullHit) {
            int lifeLv = ModEnchantments.getLevel(player.level(), stack, ModEnchantments.LIFESTEAL);
            int pierceLv = ModEnchantments.getLevel(player.level(), stack, ModEnchantments.ARMOR_PIERCE);
            int critLv = ModEnchantments.getLevel(player.level(), stack, ModEnchantments.CRITICAL_STRIKE);
            int woundsLv = ModEnchantments.getLevel(player.level(), stack, ModEnchantments.DEEP_WOUNDS);

            lifestealPct = cfg.getLifestealPercent(lifeLv);
            armorPiercePct = cfg.getArmorPiercePercent(pierceLv);
            deepWoundsBleedBonus = cfg.getDeepWoundsBonus(woundsLv);
            if (woundsLv > 0 && realTarget.hasEffect(ModEffects.BLEEDING)) {
                deepWoundsDmgBonus = deepWoundsBleedBonus;
            }
            if (critLv > 0) {
                float critChance = cfg.getCriticalChance(critLv);
                if (player.level().random.nextFloat() < critChance) {
                    critTriggered = true;
                    critMult = cfg.criticalDamageMultiplier;
                }
            }
        }

        int hurtResTicks = computeHurtResistanceTicks(player);
        float hpBefore = realTarget != null ? realTarget.getHealth() : 0.0F;
        ActiveHitContext ctx = new ActiveHitContext(
                player, rawTarget, realTarget, stack, strength, directFullHit,
                comboStep, moveType, lifestealPct, armorPiercePct,
                deepWoundsDmgBonus, deepWoundsBleedBonus,
                critTriggered, critMult, hurtResTicks, offhandOccupied, hpBefore
        );
        ACTIVE_HIT.set(ctx);
        return ctx;
    }

    public static DualBladeMoveType resolveMoveType(Player player, int comboStep, boolean offhandOccupied) {
        if (offhandOccupied) {
            return DualBladeMoveType.COMBO_1;
        }
        if (!player.onGround() && !player.isInWater() && player.fallDistance > 0.05F) {
            return DualBladeMoveType.AIR_SLASH;
        }
        // Khi chạy nhanh (Sprint): chỉ dùng SPRINT_THRUST làm đòn mở màn (nhịp 0), sau đó nối thẳng
        // sang COMBO_2 -> COMBO_3 -> COMBO_4 (360°) -> COMBO_5 (chữ X) để không bị kẹt lặp mãi 1 đòn!
        if (player.isSprinting() && player.onGround() && comboStep == 0) {
            return DualBladeMoveType.SPRINT_THRUST;
        }
        return DualBladeMoveType.comboByIndex(comboStep);
    }

    public static float modifyAttackDamage(float originalAmount) {
        ActiveHitContext ctx = ACTIVE_HIT.get();
        if (ctx == null) {
            return originalAmount;
        }
        ctx.attackExecuted = true;
        float modified = originalAmount * (1.0F + ctx.deepWoundsDamageBonus) * ctx.criticalMultiplier;
        ctx.finalRawDamage = modified;
        return modified;
    }

    public static void onLivingHurtReturn(LivingEntity victim, DamageSource source, boolean hurtSucceeded) {
        if (!hurtSucceeded || victim.level().isClientSide) {
            return;
        }
        ActiveHitContext ctx = ACTIVE_HIT.get();
        if (ctx == null || !ctx.directFullHit) {
            return;
        }
        if (ctx.realTarget != victim || source.getEntity() != ctx.player) {
            return;
        }
        if (source.is(ModDamageTypes.BLEEDING)) {
            return;
        }

        ctx.hitLanded = true;
        int resTicks = ctx.hurtResistanceTicks;
        victim.invulnerableTime = 10 + resTicks;
        victim.hurtTime = Math.min(victim.hurtTime, resTicks);
    }

    public static void finishPlayerAttack(@Nullable ActiveHitContext ctx) {
        if (ctx == null) {
            return;
        }
        ACTIVE_HIT.remove();
        if (!ctx.attackExecuted && !ctx.hitLanded) {
            return;
        }

        Player player = ctx.player;
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        long now = level.getGameTime();
        PlayerCombatState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerCombatState());
        st.lastSwingGameTime = now;
        if (!ctx.offhandOccupied) {
            if (ctx.moveType == DualBladeMoveType.SPRINT_THRUST || ctx.moveType == DualBladeMoveType.AIR_SLASH) {
                st.nextComboStep = 1;
            } else {
                st.nextComboStep = (ctx.comboStep + 1) % COMBO_LENGTH;
            }
        } else {
            st.nextComboStep = 0;
        }

        if (ctx.hitLanded && !(ctx.rawTarget instanceof LivingEntity) && !player.getAbilities().instabuild) {
            ctx.weaponStack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }

        Vec3 hitPos = (ctx.hitLanded && ctx.rawTarget != null)
                ? ctx.rawTarget.getBoundingBox().getCenter()
                : null;

        if (ctx.hitLanded && ctx.directFullHit && ctx.realTarget != null) {
            LivingEntity target = ctx.realTarget;
            ItemStack stack = ctx.weaponStack;
            WeaponsConfig cfg = WeaponsConfig.get();

            if (ctx.lifestealPercent > 0.0F) {
                float actualDealt = ctx.postMitigationDamage > 0.0F
                        ? Math.min(ctx.postMitigationDamage, Math.max(0.0F, ctx.targetHpBeforeHit))
                        : Math.max(0.0F, ctx.targetHpBeforeHit - target.getHealth());
                float healAmt = Math.min(cfg.maxLifestealPerHit, actualDealt * ctx.lifestealPercent);
                if (healAmt > 0.01F) {
                    player.heal(healAmt);
                }
            }

            if (ctx.criticalTriggered && hitPos != null) {
                level.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                        ModSounds.DUAL_BLADE_CRIT, SoundSource.PLAYERS, 0.90F, 1.05F + level.random.nextFloat() * 0.14F);
                ModNetworking.sendDualBladeVfxNearby(level, new DualBladeVfxPayload(
                        DualBladeVfxPayload.KIND_CRIT_STRIKE,
                        player.getId(),
                        ctx.moveType.id,
                        (byte) 1,
                        hitPos.x, hitPos.y, hitPos.z,
                        0.0F, 1.0F, 0.0F
                ));
            }

            applyBleeding(level, player, target, ctx.deepWoundsBleedBonus, 1);

            if (ctx.moveType == DualBladeMoveType.COMBO_4 && !ctx.offhandOccupied) {
                triggerCyclone360Aoe(level, player, target, stack, ctx.finalRawDamage,
                        ctx.armorPiercePercent, ctx.deepWoundsBleedBonus);
            }
        }

        if (!ctx.offhandOccupied && ctx.directFullHit) {
            applySafeComboLunge(level, player, st, ctx.moveType, now);
        }

        emitComboAudioAndSync(level, player, ctx.moveType, ctx.criticalTriggered ? 1 : 0, hitPos);
    }

    public static void applyBleeding(ServerLevel level, Player attacker, LivingEntity target,
                                     float deepWoundsBleedBonus, int addStacks) {
        if (!target.isAlive() || addStacks <= 0) {
            return;
        }
        WeaponsConfig cfg = WeaponsConfig.get();
        int maxStacks = cfg.bleedBaseMaxStacks;

        MobEffectInstance existing = target.getEffect(ModEffects.BLEEDING);
        int currentStacks = existing != null ? (existing.getAmplifier() + 1) : 0;
        int nextStacks = Math.min(maxStacks, currentStacks + addStacks);

        long now = level.getGameTime();
        BleedTracker tracker = BLEED_TRACKERS.get(target.getUUID());
        if (tracker == null || existing == null) {
            BLEED_TRACKERS.put(target.getUUID(), new BleedTracker(now, attacker.getUUID(), deepWoundsBleedBonus));
        } else {
            tracker.lastAttackerUuid = attacker.getUUID();
            tracker.deepWoundsBleedBonus = Math.max(tracker.deepWoundsBleedBonus, deepWoundsBleedBonus);
        }

        int newAmp = Math.max(0, nextStacks - 1);
        target.addEffect(new MobEffectInstance(ModEffects.BLEEDING, cfg.bleedDurationTicks, newAmp, false, false, true));
    }

    public static float getBleedDeepWoundsBonus(LivingEntity entity) {
        BleedTracker tracker = BLEED_TRACKERS.get(entity.getUUID());
        return tracker != null ? tracker.deepWoundsBleedBonus : 0.0F;
    }

    private static void triggerCyclone360Aoe(ServerLevel level, Player player, @Nullable LivingEntity primaryTarget,
                                             ItemStack weaponStack, float mainHitDamage,
                                             float armorPiercePercent, float deepWoundsBleedBonus) {
        WeaponsConfig cfg = WeaponsConfig.get();
        double radius = cfg.cycloneRadius;
        int maxTargets = cfg.cycloneMaxTargets;
        float baseDamage = mainHitDamage > 1.0F ? mainHitDamage : (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        AABB box = player.getBoundingBox().inflate(radius, 1.5, radius);
        List<Entity> candidates = level.getEntities(player, box, e -> e.isAlive() && e.isPickable() && e != player);

        Set<UUID> seenReal = new HashSet<>();
        int hitCount = 0;
        if (primaryTarget != null) {
            seenReal.add(primaryTarget.getUUID());
            hitCount = 1;
        }

        int hurtResTicks = computeHurtResistanceTicks(player);
        OVERRIDE_ARMOR_PIERCE.set(armorPiercePercent);
        try {
            for (Entity cand : candidates) {
                if (hitCount >= maxTargets) {
                    break;
                }
                LivingEntity real = resolveRealLivingTarget(cand);
                if (real == null || !real.isAlive() || real == player || real.isAlliedTo(player)) {
                    continue;
                }
                if (!seenReal.add(real.getUUID())) {
                    continue;
                }
                double distSq = cand.getBoundingBox().distanceToSqr(center);
                if (distSq > radius * radius) {
                    continue;
                }

                float dmg = baseDamage;
                if (deepWoundsBleedBonus > 0.0F && real.hasEffect(ModEffects.BLEEDING)) {
                    dmg *= (1.0F + deepWoundsBleedBonus);
                }
                DamageSource src = player.damageSources().playerAttack(player);
                boolean dealt = cand.hurt(src, dmg);
                if (dealt && real.isAlive()) {
                    real.invulnerableTime = 10 + hurtResTicks;
                    real.hurtTime = Math.min(real.hurtTime, hurtResTicks);
                    applyBleeding(level, player, real, deepWoundsBleedBonus, 1);
                    hitCount++;
                }
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
        }
    }

    public static boolean isForwardStepSafe(ServerLevel level, Player player, double dx, double dz) {
        if (!level.noCollision(player, player.getBoundingBox().move(dx * 0.55, 0.08, dz * 0.55))) {
            return false;
        }
        if (player.onGround()) {
            double probeX = player.getX() + dx * 0.85;
            double probeZ = player.getZ() + dz * 0.85;
            int bx = Mth.floor(probeX);
            int by = Mth.floor(player.getY());
            int bz = Mth.floor(probeZ);

            boolean hasSafeGround = false;
            for (int dy = 0; dy >= -2; dy--) {
                BlockPos pos = new BlockPos(bx, by + dy, bz);
                BlockState state = level.getBlockState(pos);
                if (state.getFluidState().is(FluidTags.LAVA)
                        || state.is(Blocks.LAVA)
                        || state.is(Blocks.FIRE)
                        || state.is(Blocks.SOUL_FIRE)
                        || state.is(Blocks.MAGMA_BLOCK)) {
                    return false;
                }
                if (dy < 0 && !state.getCollisionShape(level, pos).isEmpty()) {
                    hasSafeGround = true;
                    break;
                }
            }
            if (!hasSafeGround) {
                return false;
            }
        }
        return true;
    }

    private static void applySafeComboLunge(ServerLevel level, Player player, PlayerCombatState st,
                                            DualBladeMoveType moveType, long now) {
        WeaponsConfig cfg = WeaponsConfig.get();
        if (!cfg.enableComboLunge || now - st.lastLungeGameTime < 4L || player.isShiftKeyDown()) {
            return;
        }
        double base = (moveType == DualBladeMoveType.SPRINT_THRUST) ? cfg.sprintLungeSpeed : cfg.comboLungeDistance;
        double mult = switch (moveType) {
            case COMBO_3 -> 1.15;
            case COMBO_4 -> 0.82;
            case COMBO_5 -> 1.08;
            case SPRINT_THRUST -> 1.20;
            case AIR_SLASH -> 0.90;
            default -> 1.00;
        };
        double dist = Mth.clamp(base * mult, 0.0, 0.28);
        if (dist <= 0.01) {
            return;
        }

        float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
        double dx = -Math.sin(yawRad);
        double dz = Math.cos(yawRad);
        if (!isForwardStepSafe(level, player, dx, dz)) {
            return;
        }

        st.lastLungeGameTime = now;
        Vec3 cur = player.getDeltaMovement();
        double boostY = (moveType == DualBladeMoveType.COMBO_5 && player.onGround()) ? 0.22 : 0.0;
        player.setDeltaMovement(
                Mth.clamp(cur.x + dx * dist, -0.48, 0.48),
                Math.max(cur.y, boostY > 0.0 ? boostY : cur.y),
                Mth.clamp(cur.z + dz * dist, -0.48, 0.48)
        );
        player.hurtMarked = true;
    }

    public static void handleSlashPacket(ServerPlayer player, DualBladeSlashC2SPayload payload) {
        if (!WeaponsConfig.get().enableHoldToSlash && payload.targetEntityId() >= 0) {
            return;
        }
        if (!player.isAlive() || player.isSpectator() || !isHoldingDualBlade(player) || isPlayerDashing(player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        int targetId = payload.targetEntityId();
        if (targetId >= 0) {
            // Đánh trúng quái: giữ nguyên xác thực chặt chẽ cooldown >= 90%, tầm đánh & đường nhìn (LOS)!
            if (player.getAttackStrengthScale(0.5F) < 0.90F) {
                return;
            }
            Entity target = level.getEntity(targetId);
            if (target == null || !target.isAlive() || target == player || !target.isAttackable()) {
                return;
            }
            double reach = player.entityInteractionRange() + 1.15;
            Vec3 eye = player.getEyePosition();
            double distSq = target.getBoundingBox().distanceToSqr(eye);
            if (distSq > reach * reach) {
                return;
            }
            Vec3 targetPoint = clampPointToBox(eye, target.getBoundingBox());
            HitResult blockHit = level.clip(new ClipContext(
                    eye, targetPoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (blockHit.getType() == HitResult.Type.BLOCK
                    && blockHit.getLocation().distanceToSqr(eye) + 0.25 < targetPoint.distanceToSqr(eye)) {
                return;
            }

            player.attack(target);
            player.swing(InteractionHand.MAIN_HAND, true);
            return;
        }

        // Nhánh chém gió (`targetId == -1`, không có quái): chống spam bằng cooldown tick (>= 4 tick) thay vì
        // chặn cứng bởi attackStrengthScale (do vanilla startAttack() đã reset ticker), giúp animation, VFX
        // & âm thanh luôn mượt 100%; chỉ bật sát thương AOE 360° & bước lướt khi đủ lực (>= 85%) để giữ cân bằng!
        long now = level.getGameTime();
        PlayerCombatState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerCombatState());
        if (now - st.lastSwingGameTime < 4L) {
            return;
        }
        float airStrength = player.getAttackStrengthScale(0.5F);
        boolean fullPowerAirSlash = airStrength >= 0.85F;

        if (now - st.lastSwingGameTime > COMBO_RESET_TICKS) {
            st.nextComboStep = 0;
        }
        boolean offhandOccupied = isOffhandPenaltyActive(player);
        int step = offhandOccupied ? 0 : st.nextComboStep;
        DualBladeMoveType moveType = resolveMoveType(player, step, offhandOccupied);
        st.lastSwingGameTime = now;
        if (!offhandOccupied) {
            st.nextComboStep = (moveType == DualBladeMoveType.SPRINT_THRUST || moveType == DualBladeMoveType.AIR_SLASH)
                    ? 1
                    : (step + 1) % COMBO_LENGTH;
        } else {
            st.nextComboStep = 0;
        }

        player.resetAttackStrengthTicker();
        player.swing(InteractionHand.MAIN_HAND, true);

        if (fullPowerAirSlash && moveType == DualBladeMoveType.COMBO_4 && !offhandOccupied) {
            ItemStack stack = player.getMainHandItem();
            WeaponsConfig cfg = WeaponsConfig.get();
            float piercePct = cfg.getArmorPiercePercent(ModEnchantments.getLevel(level, stack, ModEnchantments.ARMOR_PIERCE));
            float woundsBonus = cfg.getDeepWoundsBonus(ModEnchantments.getLevel(level, stack, ModEnchantments.DEEP_WOUNDS));
            triggerCyclone360Aoe(level, player, null, stack,
                    (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), piercePct, woundsBonus);
        }
        if (fullPowerAirSlash && !offhandOccupied) {
            applySafeComboLunge(level, player, st, moveType, now);
        }
        emitComboAudioAndSync(level, player, moveType, 0, null);
    }

    private static Vec3 clampPointToBox(Vec3 p, AABB box) {
        return new Vec3(
                Mth.clamp(p.x, box.minX, box.maxX),
                Mth.clamp(p.y, box.minY, box.maxY),
                Mth.clamp(p.z, box.minZ, box.maxZ)
        );
    }

    public static float computeComboDurationTicks(double actualAttackSpeed, DualBladeMoveType moveType) {
        double safeSpd = Math.max(1.2, actualAttackSpeed);
        float speedScale = (float) Mth.clamp(2.5 / safeSpd, 0.78, 1.30);
        return Mth.clamp(moveType.baseDisplayTicks * speedScale, 9.0F, 15.0F);
    }

    private static void emitComboAudioAndSync(ServerLevel level, Player player, DualBladeMoveType moveType,
                                              int powerTier, @Nullable Vec3 hitPos) {
        float basePitch = switch (moveType) {
            case COMBO_2 -> 1.16F;
            case COMBO_3 -> 1.08F;
            case COMBO_4 -> 0.94F;
            case COMBO_5 -> 0.86F;
            case SPRINT_THRUST -> 1.24F;
            case AIR_SLASH -> 1.14F;
            default -> 1.02F;
        };
        float pitch = basePitch + (level.random.nextFloat() - 0.5F) * 0.08F;
        boolean heavySound = moveType.isHeavyFinisherOrCyclone() || moveType == DualBladeMoveType.COMBO_3;
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                heavySound ? ModSounds.DUAL_BLADE_CROSS_SLASH : ModSounds.DUAL_BLADE_SLASH,
                SoundSource.PLAYERS, heavySound ? 0.84F : 0.66F, pitch);

        double actualSpeed = Math.max(1.2, player.getAttributeValue(Attributes.ATTACK_SPEED));
        float durationTicks = computeComboDurationTicks(actualSpeed, moveType);
        float animSpeedMult = (float) (actualSpeed / 2.5);

        boolean hasHit = (hitPos != null);
        ModNetworking.sendDualBladeComboNearby(level, player.position(), new DualBladeComboPayload(
                player.getId(),
                moveType.id,
                durationTicks,
                animSpeedMult,
                player.getYRot(),
                (byte) powerTier,
                hasHit,
                hasHit ? hitPos.x : 0.0,
                hasHit ? hitPos.y : 0.0,
                hasHit ? hitPos.z : 0.0
        ));
    }

    public static boolean consumeBleedTickReady(LivingEntity entity, int intervalTicks) {
        long now = entity.level().getGameTime();
        BleedTracker tracker = BLEED_TRACKERS.get(entity.getUUID());
        if (tracker == null) {
            BLEED_TRACKERS.put(entity.getUUID(), new BleedTracker(now, null, 0.0F));
            return false;
        }
        if (now - tracker.lastTickGameTime >= intervalTicks) {
            tracker.lastTickGameTime = now;
            return true;
        }
        return false;
    }

    @Nullable
    public static Entity resolveBleedAttacker(LivingEntity victim) {
        BleedTracker tracker = BLEED_TRACKERS.get(victim.getUUID());
        if (tracker == null || tracker.lastAttackerUuid == null) {
            return null;
        }
        if (victim.level() instanceof ServerLevel sl) {
            return sl.getEntity(tracker.lastAttackerUuid);
        }
        return null;
    }

    public static void syncPlayerAttributeModifiers(ServerPlayer player, long now) {
        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attr == null) {
            return;
        }
        WeaponsConfig cfg = WeaponsConfig.get();
        if (isOffhandPenaltyActive(player)) {
            double delta = cfg.offhandOccupiedAttackSpeed - cfg.dualBladeAttackSpeed;
            AttributeModifier existing = attr.getModifier(OFFHAND_PENALTY_MOD_ID);
            if (existing == null || Math.abs(existing.amount() - delta) > 1e-4) {
                attr.removeModifier(OFFHAND_PENALTY_MOD_ID);
                attr.addTransientModifier(new AttributeModifier(
                        OFFHAND_PENALTY_MOD_ID, delta, AttributeModifier.Operation.ADD_VALUE));
            }
        } else {
            attr.removeModifier(OFFHAND_PENALTY_MOD_ID);
        }
    }

    public static void tickServer(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayerAttributeModifiers(player, now);
        }

        // Cập nhật mọi chiêu Chuột Phải Lướt Chém 10 Nhát đang hoạt động
        if (!ACTIVE_DASHES.isEmpty()) {
            Iterator<Map.Entry<UUID, ActiveFlashDash>> dit = ACTIVE_DASHES.entrySet().iterator();
            while (dit.hasNext()) {
                ActiveFlashDash dash = dit.next().getValue();
                if (tickActiveFlashDash(dash)) {
                    dit.remove();
                }
            }
        }

        if (++cleanupCounter >= 100) {
            cleanupCounter = 0;
            PLAYER_STATES.entrySet().removeIf(e -> server.getPlayerList().getPlayer(e.getKey()) == null);
            Iterator<Map.Entry<UUID, BleedTracker>> it = BLEED_TRACKERS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, BleedTracker> entry = it.next();
                if (now - entry.getValue().lastTickGameTime > 240L) {
                    it.remove();
                }
            }
        }
    }
}
