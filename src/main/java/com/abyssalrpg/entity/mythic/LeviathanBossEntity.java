package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.config.LeviathanConfig;
import com.abyssalrpg.entity.AbstractAbyssalBoss;
import com.abyssalrpg.entity.mythic.leviathan.LeviathanAttackTiming;
import com.abyssalrpg.entity.mythic.leviathan.LeviathanAttackTiming.HitWindow;
import com.abyssalrpg.entity.mythic.leviathan.LeviathanAttackTiming.SkillTimeline;
import com.abyssalrpg.entity.vfx.AbyssalOrbEntity;
import com.abyssalrpg.entity.vfx.EnergySlashEntity;
import com.abyssalrpg.entity.vfx.LaserBeamEntity;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.LeviathanVfxPayload;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BOSS LEVIATHAN (GeckoLib 4.x — Bản nâng cấp 2.5x Colossal + Phase 2 Cuồng Bạo cận tử + Fix xoay đầu).
 *
 * =========================================================================
 * NGUYÊN NHÂN THỰC SỰ CỦA LỖI ĐẦU QUAY NHIỀU VÒNG KHI VÀO PHASE 2 & CÁCH ĐÃ SỬA:
 * 1. `smoothRotateToward()` trước đây cộng dồn `newYaw = this.getYRot() + clamp(diff)` mà KHÔNG `wrapDegrees`,
 *    nên khi boss lượn quanh người chơi nhiều vòng trong Phase 1, `yRot`/`yBodyRot` tích lũy lên ±720°..±1440°.
 *    Khi vào Phase 2 chuyển sang đứng yên, `LivingEntity.tickHeadTurn()` / client nội suy `yHeadRot - yBodyRot`
 *    kéo đầu xoay ngược nhiều vòng 360° cho tới khi về khoảng [-180, 180].
 *    -> ĐÃ SỬA: mọi thao tác xoay đều qua `approachAngle()` (dùng `Mth.wrapDegrees`), mỗi tick chuẩn hóa
 *       `yRot`/`yBodyRot`/`yHeadRot` về [-180, 180] và đồng bộ `yRotO`/`yBodyRotO`/`yHeadRotO` theo cung ngắn nhất.
 * 2. `LookAtPlayerGoal` + `RandomLookAroundGoal` + `LookControl` vẫn chạy trong lúc `PHASE_CHANGE` trong khi thân
 *    bị ép đứng yên, tạo xoay giằng co giữa đầu và thân.
 *    -> ĐÃ SỬA: thay bằng `LeviathanLookControl` (tắt hẳn khi chuyển phase) + `PhaseTransitionLockGoal` (ưu tiên 0,
 *       chiếm cả `Flag.MOVE` và `Flag.LOOK`) khóa cố định `lockedTransitionYaw` và đồng bộ qua DataTracker sang client.
 * 3. `LeviathanHoverMoveGoal` từng gán trực tiếp `boss.yHeadRot = Mth.rotLerp(...)` không chuẩn hóa/không kẹp góc.
 *    -> ĐÃ SỬA: mọi chỉnh góc đầu đều đi qua `applyWrappedRotation()` có kẹp ±45° so với thân.
 * 4. `LeviathanGeoModel.setCustomAnimations()` trước đây không `wrapDegrees`/clamp `netHeadYaw`, lại cộng dồn
 *    (`neck.getRotY() + ...`) và không tắt khi animation `phase_change` / `roar_shockwave` đang chạy.
 *    -> ĐÃ SỬA: tắt hoàn toàn head-tracking khi `isHeadLockedByAnimation()` (chuyển phase/roar/spawn/death),
 *       đặt góc tuyệt đối và kẹp cứng ±45° yaw, ±30° pitch.
 * =========================================================================
 */
public class LeviathanBossEntity extends AbstractGeoMythicBoss {
    // --- Hằng số kích thước đồng bộ 1 chỗ duy nhất cho EntityType, Renderer, tầm đòn & VFX ---
    public static final float SCALE = 2.5f;
    /** Hệ số cơ sở của model v2 (218 px tổng chiều dài), nhân `SCALE * visualScaleMultiplier` -> ~40-42 block uy lực! */
    public static final float BASE_RENDER_SCALE = 1.12f;
    public static final float BASE_WIDTH = 3.2f;
    public static final float BASE_HEIGHT = 2.6f;
    public static final float BASE_EYE_HEIGHT = 2.6f * 0.65f;

    // --- Bảng màu hiệu ứng ---
    private static final int COLOR_OCEAN_CYAN = 0x24E2FF;
    private static final int COLOR_EMBER_ORANGE = 0xFF6E1A;
    private static final int COLOR_ENRAGED_CRIMSON = 0xFF2820;
    private static final int COLOR_GOLD_FLARE = 0xFFD04A;

    // --- EntityAttributeModifier Phase 2 (giáp, sát thương, tốc độ: ADD_MULTIPLIED_BASE +0.5 = x1.5) ---
    // Máu tối đa Phase 2 KHÔNG dùng attribute modifier (do vanilla clamp MAX_HEALTH tại 1024),
    // mà dùng BossHealthHolder (`setBossMaxHealth(baseMaxHealth * phase2StatMultiplier)`).
    private static final ResourceLocation LEGACY_PHASE2_HEALTH_MOD_ID = AbyssalRpgMod.id("leviathan_phase2_health");
    private static final ResourceLocation PHASE2_ARMOR_MOD_ID = AbyssalRpgMod.id("leviathan_phase2_armor");
    private static final ResourceLocation PHASE2_DAMAGE_MOD_ID = AbyssalRpgMod.id("leviathan_phase2_damage");
    private static final ResourceLocation PHASE2_SPEED_MOD_ID = AbyssalRpgMod.id("leviathan_phase2_speed");
    private static final ResourceLocation PHASE2_FLY_SPEED_MOD_ID = AbyssalRpgMod.id("leviathan_phase2_fly_speed");

    // --- Khai báo RawAnimation GeckoLib ---
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.leviathan.idle");
    private static final RawAnimation ANIM_MOVE = RawAnimation.begin().thenLoop("animation.leviathan.move");
    private static final RawAnimation ANIM_DEATH = RawAnimation.begin().thenPlayAndHold("animation.leviathan.death");

    private static final RawAnimation ANIM_TAIL_SLAM = RawAnimation.begin().thenPlay("animation.leviathan.tail_slam");
    private static final RawAnimation ANIM_BITE_CHARGE = RawAnimation.begin().thenPlay("animation.leviathan.bite_charge");
    private static final RawAnimation ANIM_ENERGY_BEAM = RawAnimation.begin().thenPlay("animation.leviathan.energy_beam");
    private static final RawAnimation ANIM_SUMMON_MINIONS = RawAnimation.begin().thenPlay("animation.leviathan.summon_minions");
    private static final RawAnimation ANIM_WHIRLPOOL = RawAnimation.begin().thenPlay("animation.leviathan.whirlpool");
    private static final RawAnimation ANIM_ROAR_SHOCKWAVE = RawAnimation.begin().thenPlay("animation.leviathan.roar_shockwave");
    private static final RawAnimation ANIM_STORM_BARRAGE = RawAnimation.begin().thenPlay("animation.leviathan.storm_barrage");
    private static final RawAnimation ANIM_TIDAL_WRATH = RawAnimation.begin().thenPlay("animation.leviathan.tidal_wrath");
    private static final RawAnimation ANIM_PHASE_CHANGE = RawAnimation.begin().thenPlay("animation.leviathan.phase_change");

    // --- State Machine cho 8 skill + chuyển Phase + chết ---
    public enum SkillState {
        IDLE,
        TAIL_SLAM,
        BITE_CHARGE,
        ENERGY_BEAM,
        SUMMON_MINIONS,
        WHIRLPOOL,
        ROAR_SHOCKWAVE,
        STORM_BARRAGE,
        TIDAL_WRATH,
        PHASE_CHANGE,
        DEATH;

        public static SkillState byId(int id) {
            SkillState[] vals = values();
            return (id >= 0 && id < vals.length) ? vals[id] : IDLE;
        }
    }

    // --- DataTracker riêng của Leviathan (các khóa Phase 2 & LockedYaw kế thừa từ AbstractGeoMythicBoss) ---
    private static final EntityDataAccessor<Integer> DATA_SKILL_STATE =
            SynchedEntityData.defineId(LeviathanBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ACTION_ANIM_SPEED =
            SynchedEntityData.defineId(LeviathanBossEntity.class, EntityDataSerializers.FLOAT);

    public static final int DEFAULT_PHASE_TRANSITION_TICKS = 90; // 4.5 giây bất tử & hồi đầy 100% HP
    public static final int CUSTOM_DEATH_DURATION = 60;          // 3.0 giây death animation

    private static volatile boolean debugLoggingEnabled = false;
    private volatile Vec3 clientHeadWorldPos = null;

    // --- Quản lý cooldown riêng & chống lặp 3 lần ---
    private final Map<SkillState, Integer> skillCooldowns = new EnumMap<>(SkillState.class);
    private final Set<UUID> activeMinions = new HashSet<>();
    private SkillState lastSkill1 = SkillState.IDLE;
    private SkillState lastSkill2 = SkillState.IDLE;
    private int globalSkillRestTicks = 28;
    private int meleeCooldownTicks = 0;

    // --- Di chuyển & chống kẹt địa hình (3 giây) ---
    private Vec3 homeAnchor = null;
    private Vec3 lastProgressPos = null;
    private int stuckTicks = 0;
    private float orbitAngle = 0f;

    @SuppressWarnings("this-escape")
    public LeviathanBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        this.setNoGravity(true);
        this.moveControl = new LeviathanMoveControl(this);
        this.transitionTotalTicks = DEFAULT_PHASE_TRANSITION_TICKS;
        for (SkillState s : SkillState.values()) {
            skillCooldowns.put(s, 0);
        }
        // Gán máu tối đa riêng ngay sau super() (1500 HP từ config) để /summon và spawn egg đủ máu lập tức
        initializeBossHealth((float) LeviathanConfig.get().maxHealth);
    }

    public static boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled || LeviathanConfig.get().debugRotationLogs;
    }

    public static void setDebugLoggingEnabled(boolean enabled) {
        debugLoggingEnabled = enabled;
    }

    public void smoothRotateToward(Vec3 direction, float baseMaxDegPerTick) {
        super.smoothRotateToward(direction, baseMaxDegPerTick, (float) LeviathanConfig.get().phase2TurnSpeedMultiplier);
    }

    // =========================================================================
    // GECKOLIB 4.x ANIMATION CONTROLLERS (0-TICK SYNC + SHARED SPEED HANDLER)
    // =========================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Controller 1: idle / move / death (transition 5 ticks mượt mà)
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (!this.isAlive() || this.deathTime > 0 || getSkillState() == SkillState.DEATH) {
                return state.setAndContinue(ANIM_DEATH);
            }
            if (isTransitioningPhase()) {
                return state.setAndContinue(ANIM_IDLE);
            }
            if (state.isMoving() || this.getDeltaMovement().horizontalDistanceSqr() > 0.0025) {
                return state.setAndContinue(ANIM_MOVE);
            }
            return state.setAndContinue(ANIM_IDLE);
        }));

        // Controller 2: kỹ năng & chuyển phase đồng bộ 0-tick bằng LeviathanAttackTiming + transitionLength = 2 ticks!
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .setAnimationSpeedHandler(anim -> (double) anim.getSyncedActionAnimSpeed())
                .triggerableAnim("tail_slam", ANIM_TAIL_SLAM)
                .triggerableAnim("bite_charge", ANIM_BITE_CHARGE)
                .triggerableAnim("energy_beam", ANIM_ENERGY_BEAM)
                .triggerableAnim("summon_minions", ANIM_SUMMON_MINIONS)
                .triggerableAnim("whirlpool", ANIM_WHIRLPOOL)
                .triggerableAnim("roar_shockwave", ANIM_ROAR_SHOCKWAVE)
                .triggerableAnim("storm_barrage", ANIM_STORM_BARRAGE)
                .triggerableAnim("tidal_wrath", ANIM_TIDAL_WRATH)
                .triggerableAnim("phase_change", ANIM_PHASE_CHANGE));
    }

    public void playActionAnim(String animKey) {
        playActionAnim(animKey, 1.0f);
    }

    public void playActionAnim(String animKey, float speed) {
        if (!this.level().isClientSide) {
            this.setSyncedActionAnimSpeed(speed);
            this.triggerAnim("action", animKey);
        }
    }

    public float getEffectiveScale() {
        return SCALE * LeviathanConfig.get().visualScaleMultiplier;
    }

    public float getTotalRenderScale() {
        return BASE_RENDER_SCALE * getEffectiveScale();
    }

    public void updateClientHeadWorldPosition(double x, double y, double z) {
        this.clientHeadWorldPos = new Vec3(x, y, z);
    }

    /**
     * Vị trí miệng chính xác khớp `mouth_locator` (`[0.0, 23.9, -51.3]` trong model space v2)
     * để tia `ENERGY_BEAM` và VFX phát ra đúng từ giữa 2 răng nanh ngà!
     */
    public Vec3 getMouthWorldPosition() {
        if (this.level().isClientSide && this.clientHeadWorldPos != null) {
            return this.clientHeadWorldPos;
        }
        float total = getTotalRenderScale();
        double blocksPerPx = total / 16.0;
        double forwardBlocks = 51.3 * blocksPerPx;
        double heightBlocks = 23.9 * blocksPerPx;
        float yawRad = this.yHeadRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yawRad);
        double fz = Mth.cos(yawRad);
        return new Vec3(this.getX() + fx * forwardBlocks, this.getY() + heightBlocks, this.getZ() + fz * forwardBlocks);
    }

    // =========================================================================
    // DATA TRACKER, CULLING BOX, THUỘC TÍNH & PATHFINDING THÂN TO 2.5x
    // =========================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKILL_STATE, SkillState.IDLE.ordinal());
        builder.define(DATA_ACTION_ANIM_SPEED, 1.0f);
    }

    public float getSyncedActionAnimSpeed() {
        return Mth.clamp(this.entityData.get(DATA_ACTION_ANIM_SPEED), 0.5f, 2.0f);
    }

    public void setSyncedActionAnimSpeed(float speed) {
        this.entityData.set(DATA_ACTION_ANIM_SPEED, Mth.clamp(speed, 0.5f, 2.0f));
    }

    public SkillState getSkillState() {
        return SkillState.byId(this.entityData.get(DATA_SKILL_STATE));
    }

    public void setSkillState(SkillState state) {
        this.entityData.set(DATA_SKILL_STATE, state.ordinal());
    }

    @Override
    public boolean isTransitioningPhase() {
        return super.isTransitioningPhase() || getSkillState() == SkillState.PHASE_CHANGE;
    }

    /**
     * Khi đang chuyển phase, gầm, trồi lên hoặc chết -> animation chủ động điều khiển đầu/cổ,
     * tắt phần head-tracking trong LeviathanGeoModel.setCustomAnimations().
     */
    public boolean isHeadLockedByAnimation() {
        SkillState st = getSkillState();
        return !this.isAlive()
                || this.deathTime > 0
                || this.isSpawningIn()
                || this.isTransitioningPhase()
                || st == SkillState.PHASE_CHANGE
                || st == SkillState.ROAR_SHOCKWAVE
                || st == SkillState.DEATH;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // Mở rộng hộp culling đồng bộ theo getEffectiveScale() để đuôi roi dài không bao giờ bị cắt mất ở góc nghiêng!
        double eff = getEffectiveScale();
        return this.getBoundingBox().inflate(13.0 * eff, 6.0 * eff, 13.0 * eff);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        LeviathanPathNavigation nav = new LeviathanPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(false);
        nav.setMaxVisitedNodesMultiplier(4.0f); // Tăng số node tìm đường cho thân 8.0 x 6.5 không kẹt
        return nav;
    }

    @Override
    protected int getDefaultSpawnEggFloor() {
        return 15;
    }

    @Override
    public float getColossalScale(int floor) {
        // Kích thước 2.5x đã áp trực tiếp vào EntityType.sized() & LeviathanGeoRenderer.withScale() qua SCALE = 2.5f
        // Giữ Attributes.SCALE = 1.0 để không bị nhân đôi (double-scale).
        return 1.0f;
    }

    @Override
    protected Component getStandaloneBossTitle() {
        if (isEnragedVisual() || this.phase >= 2) {
            return Component.literal("🔥 LEVIATHAN — CUỒNG BẠO VỰC SÂU 🔥")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }
        return Component.literal("🔱 LEVIATHAN — Abyssal Deep-Sea Titan 🔱")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        LeviathanConfig cfg = LeviathanConfig.get();
        return createBaseBossAttributes()
                // Giữ attribute MAX_HEALTH trong giới hạn hợp lệ vanilla (<= 1024); máu thực lấy qua BossHealthHolder + Mixin
                .add(Attributes.MAX_HEALTH, Math.min(VANILLA_SAFE_MAX_HEALTH_ATTR, cfg.maxHealth))
                .add(Attributes.ATTACK_DAMAGE, cfg.meleeDamage)
                .add(Attributes.ARMOR, cfg.armor)
                .add(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness)
                .add(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed)
                .add(Attributes.FLYING_SPEED, cfg.baseMoveSpeed * 1.45)
                .add(Attributes.FOLLOW_RANGE, cfg.detectionRadius)
                .add(Attributes.STEP_HEIGHT, cfg.stepHeight)
                .add(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance);
    }

    private float computeTargetBossMaxHealth(boolean phase2) {
        LeviathanConfig cfg = LeviathanConfig.get();
        return (float) (phase2 ? (cfg.maxHealth * cfg.phase2StatMultiplier) : cfg.maxHealth);
    }

    @Override
    public void configureForFloor(int floor, BlockPos arenaCenter, boolean dungeonBoss) {
        super.configureForFloor(floor, arenaCenter, dungeonBoss);
        LeviathanConfig cfg = LeviathanConfig.get();
        setAttr(Attributes.MAX_HEALTH, Math.min(VANILLA_SAFE_MAX_HEALTH_ATTR, cfg.maxHealth));
        removeAttributeModifierIfPresent(Attributes.MAX_HEALTH, LEGACY_PHASE2_HEALTH_MOD_ID);
        setAttr(Attributes.ATTACK_DAMAGE, cfg.meleeDamage);
        setAttr(Attributes.ARMOR, cfg.armor);
        setAttr(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness);
        setAttr(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance);
        setAttr(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed);
        setAttr(Attributes.FLYING_SPEED, cfg.baseMoveSpeed * 1.45);
        setAttr(Attributes.FOLLOW_RANGE, cfg.detectionRadius);
        setAttr(Attributes.STEP_HEIGHT, cfg.stepHeight);
        setAttr(Attributes.SCALE, 1.0);

        if (this.hasEnteredPhase2) {
            setBossMaxHealth(computeTargetBossMaxHealth(true));
            applyPhase2Modifiers();
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        } else {
            initializeBossHealth(computeTargetBossMaxHealth(false));
            this.bossEvent.setColor(BossEvent.BossBarColor.BLUE);
        }

        // Khóa góc ban đầu gọn gàng cho animation trồi lên
        float initYaw = wrapAngle(this.getYRot());
        this.entityData.set(DATA_LOCKED_YAW, initYaw);
        snapSyncPrevAngles();
    }

    // =========================================================================
    // ĐĂNG KÝ GOAL: PRIORITY 0 KHÓA CHUYỂN PHASE + 8 SKILL + SELECTOR + MOVE
    // =========================================================================
    @Override
    protected void registerGoals() {
        // Priority 0: khóa toàn bộ MOVE + LOOK + TARGET trong suốt 4.5s chuyển Phase 2 (dùng chung từ AbstractGeoMythicBoss)
        this.goalSelector.addGoal(0, new BossLockGoal(this));

        // 8 Goal chuyên biệt tương ứng từng SkillState
        this.goalSelector.addGoal(1, new TailSlamGoal(this));
        this.goalSelector.addGoal(1, new BiteChargeGoal(this));
        this.goalSelector.addGoal(1, new EnergyBeamGoal(this));
        this.goalSelector.addGoal(1, new SummonMinionsGoal(this));
        this.goalSelector.addGoal(1, new WhirlpoolGoal(this));
        this.goalSelector.addGoal(1, new RoarShockwaveGoal(this));
        this.goalSelector.addGoal(1, new StormBarrageGoal(this));
        this.goalSelector.addGoal(1, new TidalWrathGoal(this));

        this.goalSelector.addGoal(2, new LeviathanSkillSelectorGoal(this));
        this.goalSelector.addGoal(3, new LeviathanHoverMoveGoal(this));
        this.goalSelector.addGoal(7, new SafeLookAtPlayerGoal(this, 64.0f));
        this.goalSelector.addGoal(8, new SafeRandomLookGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // =========================================================================
    // HỆ THỐNG PHASE 2 "CUỒNG BẠO" — KÍCH HOẠT KHI GẦN CHẾT (<= 5% HP HOẶC <= 1 HP)
    // =========================================================================
    private float getPhase2TriggerThreshold() {
        return Math.max(1.0f, this.getMaxHealth() * (float) LeviathanConfig.get().phase2TriggerHpRatio);
    }

    /**
     * Chặn đòn kết liễu ở Phase 1 trong `hurt()` — không cho chết ở Phase 1, kích hoạt chuỗi chuyển Phase 2.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Cho phép lệnh /kill hoặc void diệt sạch boss khi test
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        // Miễn nhiễm ngạt trong tường / đuối nước / sát thương rơi cho thủy quái khổng lồ
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        if (this.isSpawningIn() || this.isTransitioningPhase()) {
            return false;
        }

        if (!this.hasEnteredPhase2 && this.level() instanceof ServerLevel serverLevel) {
            float triggerHp = getPhase2TriggerThreshold();
            float estimatedDamage = estimatePostArmorDamage(source, amount);
            if (this.getHealth() - estimatedDamage <= triggerHp) {
                // Hủy đòn chí mạng, giữ HP ở ngưỡng tối thiểu và bắt đầu chuỗi 4.5s bất tử chuyển Phase 2!
                this.setHealth(Math.max(1.0f, Math.min(this.getHealth(), triggerHp)));
                beginPhase2Transition(serverLevel);
                return false;
            }
        }

        boolean applied = super.hurt(source, amount);

        // Kiểm tra dự phòng sau đòn đánh (trường hợp sát thương xuyên giáp hoặc crit vượt ước lượng)
        if (applied && !this.hasEnteredPhase2 && this.isAlive() && this.level() instanceof ServerLevel serverLevel) {
            float triggerHp = getPhase2TriggerThreshold();
            if (this.getHealth() <= triggerHp) {
                this.setHealth(Math.max(1.0f, this.getHealth()));
                beginPhase2Transition(serverLevel);
            }
        }
        return applied;
    }

    @Override
    public void die(DamageSource damageSource) {
        // Lưới an toàn cuối cùng: tuyệt đối không cho chết ở Phase 1 nếu không phải /kill
        if (!this.hasEnteredPhase2 && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && this.level() instanceof ServerLevel serverLevel) {
            this.setHealth(getPhase2TriggerThreshold());
            beginPhase2Transition(serverLevel);
            return;
        }

        setSkillState(SkillState.DEATH);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.blockPosition(), ModSounds.LEVIATHAN_DEATH, SoundSource.HOSTILE, 3.0f, 0.72f);
            ModNetworking.sendShakeNearby(serverLevel, this.position(), 80.0, 2.5f, 24);
        }
        super.die(damageSource);
    }

    /**
     * Bắt đầu chuỗi chuyển Phase 2 "Cuồng bạo" (mặc định 90 ticks = 4.5 giây):
     * 1. Khóa cờ `hasEnteredPhase2` (chỉ kích hoạt đúng 1 lần duy nhất, lưu NBT & DataTracker).
     * 2. Khóa cứng yaw/headYaw/bodyYaw tại góc hiện tại đã wrapDegrees + dừng toàn bộ AI/Move/Look.
     * 3. Áp modifier MAX_HEALTH (+50% -> 2250) ngay để thanh máu dâng mượt từ ngưỡng cận tử lên 100%.
     * 4. Chạy animation `phase_change` (4.5s), rung màn hình, âm thanh & hiệu ứng hạt.
     */
    private void beginPhase2Transition(ServerLevel level) {
        if (this.hasEnteredPhase2) return;

        LeviathanConfig cfg = LeviathanConfig.get();
        this.hasEnteredPhase2 = true;
        this.entityData.set(DATA_HAS_ENTERED_PHASE2, true);

        // Khóa góc hiện tại đã chuẩn hóa về [-180, 180] và đồng bộ sang Client qua DataTracker
        float locked = wrapAngle(this.getYRot());
        this.entityData.set(DATA_LOCKED_YAW, locked);
        this.setYRot(locked);
        this.yRotO = locked;
        this.yBodyRot = locked;
        this.yBodyRotO = locked;
        this.yHeadRot = locked;
        this.yHeadRotO = locked;
        this.setXRot(0.0f);
        this.xRotO = 0.0f;

        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.scheduledActions.clear();

        // Nâng máu tối đa riêng ngay (1500 -> 2250 theo config) để thanh máu hồi dần mượt lên 100%
        setBossMaxHealth(computeTargetBossMaxHealth(true));

        this.transitionStartHp = Math.max(1.0f, this.getHealth());
        this.transitionTotalTicks = Math.max(40, cfg.phaseTransitionTicks);
        this.entityData.set(DATA_PHASE_TRANS_TICKS, this.transitionTotalTicks);
        setSkillState(SkillState.PHASE_CHANGE);
        playActionAnim("phase_change");

        level.playSound(null, this.blockPosition(), ModSounds.LEVIATHAN_PHASE_CHANGE, SoundSource.HOSTILE, 3.2f, 0.70f);
        ModNetworking.sendShakeNearby(level, this.position(), 96.0, 2.4f, 30);
        announceToNearby(level, Component.literal("⚠ LEVIATHAN SẮP NGÃ XUỐNG... NGUỒN NĂNG LƯỢNG VỰC SÂU ĐANG BÙNG NỔ! ⚠")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    /**
     * Đỉnh điểm chuyển Phase (~giữa chuỗi): đổi texture sang bản Enraged + Glowmask,
     * đổi màu Bossbar sang Đỏ và phát thông báo Cuồng Bạo.
     */
    private void triggerPhase2VisualClimax(ServerLevel level) {
        this.entityData.set(DATA_ENRAGED_VISUAL, true);
        this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        Component enragedName = getStandaloneBossTitle();
        if (enragedName != null) {
            this.setCustomName(enragedName);
        }

        ShockwaveRingEntity.spawn(level, this.position(), 18.0f * getEffectiveScale(), COLOR_ENRAGED_CRIMSON, 20);
        ShockwaveRingEntity.spawn(level, this.position().add(0, 1.4 * getEffectiveScale(), 0), 12.0f * getEffectiveScale(), COLOR_GOLD_FLARE, 16);
        ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_PHASE_CLIMAX,
                this.position().add(0, 2.0 * getEffectiveScale(), 0), Vec3.ZERO, 9.0f * getEffectiveScale(), true);
        level.playSound(null, this.blockPosition(), ModSounds.LEVIATHAN_ROAR, SoundSource.HOSTILE, 3.4f, 0.62f);
        ModNetworking.sendShakeNearby(level, this.position(), 100.0, 3.0f, 22);

        announceToNearby(level, Component.literal("🔥 LEVIATHAN HÓA CUỒNG BẠO — PHASE 2: MÁU 2250, GIÁP 30, SỨC MẠNH x1.5! 🔥")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    /**
     * Kết thúc chuỗi 4.5s: hồi chạm đúng 100% HP (2250/2250), áp toàn bộ modifier Phase 2,
     * nhả khóa AI mượt từ đúng góc yaw hiện tại (KHÔNG reset đột ngột).
     */
    private void completePhase2Transformation(ServerLevel level) {
        if (!isEnragedVisual()) {
            triggerPhase2VisualClimax(level);
        }

        // Áp toàn bộ stat Phase 2 (= 1.5x Phase 1, kích thước giữ nguyên 2.5x)
        setBossMaxHealth(computeTargetBossMaxHealth(true));
        applyPhase2Modifiers();
        this.setHealth(this.getMaxHealth());

        // Đẩy nhẹ người chơi đứng sát thân boss (dùng mobAttack bình thường)
        float burstDmg = LeviathanConfig.get().roarShockwaveDamage * skillDamageScale();
        damagePlayersInRadius(level, this.position(), 8.5 * SCALE, burstDmg, 0.85);

        // Giữ nguyên góc hiện tại đã khóa, chỉ giải phóng trạng thái chuyển phase
        snapSyncPrevAngles();
        setSkillState(SkillState.IDLE);
        this.globalSkillRestTicks = 16;
    }

    /**
     * Áp các EntityAttributeModifier Phase 2 cho giáp/sát thương/tốc độ (`ADD_MULTIPLIED_BASE`, +0.5 = x1.5).
     * Máu tối đa không dùng attribute modifier mà quản lý qua `BossHealthHolder` (`setBossMaxHealth`).
     */
    private void applyPhase2Modifiers() {
        LeviathanConfig cfg = LeviathanConfig.get();
        double bonus = Math.max(0.0, cfg.phase2StatMultiplier - 1.0); // Mặc định +0.5 (+50% = x1.5)

        // Dọn modifier máu cũ nếu load từ bản save trước đây
        removeAttributeModifierIfPresent(Attributes.MAX_HEALTH, LEGACY_PHASE2_HEALTH_MOD_ID);

        // Giáp Phase 1 = 20 -> Phase 2 = 30 (không vượt trần 30 của vanilla)
        double baseArmor = Math.max(0.0, cfg.armor);
        double maxAllowedArmorBonus = (baseArmor > 0.0) ? Math.max(0.0, (30.0 / baseArmor) - 1.0) : 0.0;
        double armorBonus = Math.min(bonus, maxAllowedArmorBonus);
        applyPermanentMultiplier(Attributes.ARMOR, PHASE2_ARMOR_MOD_ID, armorBonus);

        applyPermanentMultiplier(Attributes.ATTACK_DAMAGE, PHASE2_DAMAGE_MOD_ID, bonus);
        applyPermanentMultiplier(Attributes.MOVEMENT_SPEED, PHASE2_SPEED_MOD_ID, bonus);
        applyPermanentMultiplier(Attributes.FLYING_SPEED, PHASE2_FLY_SPEED_MOD_ID, bonus);
    }

    // =========================================================================
    // TICK SERVER CHÍNH (normalizeAnglesAndSyncPrev chạy ở AbstractGeoMythicBoss.tick())
    // =========================================================================
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        this.setNoGravity(true);
        this.fallDistance = 0f;
        if (homeAnchor == null) {
            homeAnchor = arenaCenter != null ? Vec3.atBottomCenterOf(arenaCenter) : this.position();
        }

        syncBossBarPlayers(serverLevel, LeviathanConfig.get().bossBarRadius);

        // Hỗ trợ test nhanh bằng lệnh /data merge entity ... {Health:50f}
        if (!this.hasEnteredPhase2 && this.isAlive() && !this.isSpawningIn() && !this.isTransitioningPhase()) {
            if (this.getHealth() <= getPhase2TriggerThreshold()) {
                this.setHealth(Math.max(1.0f, this.getHealth()));
                beginPhase2Transition(serverLevel);
            }
        }

        int cdStep = (this.tickCount % 2 == 0) ? 2 : 1;
        for (SkillState s : SkillState.values()) {
            int cd = skillCooldowns.getOrDefault(s, 0);
            if (cd > 0) skillCooldowns.put(s, Math.max(0, cd - cdStep));
        }
        if (meleeCooldownTicks > 0) meleeCooldownTicks--;

        if (this.tickCount % 20 == 0 && !activeMinions.isEmpty()) {
            activeMinions.removeIf(uuid -> {
                Entity e = serverLevel.getEntity(uuid);
                return e == null || !e.isAlive();
            });
        }

        // Xử lý chuỗi 4.5 giây chuyển Phase 2 Cuồng Bạo (bất tử, khóa góc, hồi máu dần lên 100%)
        int pTrans = this.entityData.get(DATA_PHASE_TRANS_TICKS);
        if (pTrans > 0) {
            this.setDeltaMovement(Vec3.ZERO);
            this.getNavigation().stop();
            normalizeAnglesAndSyncPrev();

            int nextTrans = pTrans - 1;
            this.entityData.set(DATA_PHASE_TRANS_TICKS, nextTrans);

            // Hồi máu dần mượt từ transitionStartHp -> 100% MaxHealth (2250 HP)
            float progress = 1.0f - (nextTrans / (float) Math.max(1, this.transitionTotalTicks));
            float smooth = progress * progress * (3.0f - 2.0f * progress);
            float targetHp = Mth.lerp(smooth, this.transitionStartHp, this.getMaxHealth());
            if (targetHp > this.getHealth()) {
                this.setHealth(Math.min(this.getMaxHealth(), targetHp));
            }

            // Đổi sang texture Enraged + Bossbar Đỏ ngay giữa đỉnh gầm (~45% tiến độ)
            if (!isEnragedVisual() && progress >= 0.45f) {
                triggerPhase2VisualClimax(serverLevel);
            }

            // Log DEBUG từng tick trong chuyển phase để kiểm chứng không còn xoay đầu
            logRotationDebugIfEnabled(pTrans);

            if (pTrans % 5 == 0) {
                float r = (float) ((3.0 + (pTrans / (double) Math.max(1, this.transitionTotalTicks)) * 4.0) * getEffectiveScale());
                ModNetworking.sendLeviathanVfxNearby(serverLevel, LeviathanVfxPayload.KIND_PHASE_PULSE,
                        this.position(), Vec3.ZERO, r, isEnragedVisual());
            }
            if (pTrans % 15 == 0) {
                ModNetworking.sendShakeNearby(serverLevel, this.position(), 88.0, 1.35f, 10);
            }
            if (nextTrans <= 0) {
                completePhase2Transformation(serverLevel);
            }
            return;
        }

        // Đòn đánh cận chiến thường (Phase 1: 15 ST -> Phase 2: 22.5 ST; tầm đánh đồng bộ getEffectiveScale())
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && getSkillState() == SkillState.IDLE
                && meleeCooldownTicks <= 0 && !isSpawningIn()) {
            double reach = this.getBbWidth() * 0.85 + target.getBbWidth() + (2.2 * getEffectiveScale());
            if (this.distanceToSqr(target) <= reach * reach) {
                float meleeDmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                target.hurt(this.damageSources().mobAttack(this), meleeDmg);
                serverLevel.playSound(null, this.blockPosition(), ModSounds.LEVIATHAN_ATTACK, SoundSource.HOSTILE, 2.0f, 0.78f);
                ModNetworking.sendLeviathanVfxNearby(serverLevel, LeviathanVfxPayload.KIND_MELEE_SWEEP,
                        target.position().add(0, 1.0, 0), this.getLookAngle(), 2.6f * getEffectiveScale(), isEnragedVisual());
                meleeCooldownTicks = this.phase >= 2 ? 14 : 20;
            }
        }
    }

    private void logRotationDebugIfEnabled(int transTicksLeft) {
        logGeoRotationDebug("Leviathan-P2", transTicksLeft, isDebugLoggingEnabled());
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isTransitioningPhase() || this.isSpawningIn()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.calculateEntityAnimation(false);
            return;
        }
        if (this.isEffectiveAi() || this.isControlledByLocalInstance()) {
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.88));
        }
        this.calculateEntityAnimation(false);
    }

    public void finishSkill(int restMinTicks) {
        setSkillState(SkillState.IDLE);
        float cdDiv = (this.phase >= 2) ? (float) LeviathanConfig.get().phase2StatMultiplier : 1.0f;
        int scaledRest = Math.max(7, Math.round((restMinTicks * 0.68f) / cdDiv));
        int extra = this.getRandom().nextInt(this.phase >= 2 ? 6 : 11);
        this.globalSkillRestTicks = scaledRest + extra;
    }

    /** Hệ số sát thương kỹ năng: Phase 1 = 1.0x, Phase 2 = 1.5x (từ config). */
    public float skillDamageScale() {
        return this.phase >= 2 ? (float) LeviathanConfig.get().phase2StatMultiplier : 1.0f;
    }

    /** Đảm bảo thời gian telegraph của mọi skill không bao giờ nhỏ hơn 0.4 giây (8 ticks). */
    public int safeTelegraph(int baseTicks) {
        int minT = Math.max(8, LeviathanConfig.get().minTelegraphTicks);
        if (this.phase >= 2) {
            return Math.max(minT, Math.round(baseTicks / 1.25f));
        }
        return Math.max(minT, baseTicks);
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        this.setDeltaMovement(0, -0.025 * getEffectiveScale(), 0);
        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.deathTime % 6 == 0) {
                ModNetworking.sendLeviathanVfxNearby(serverLevel, LeviathanVfxPayload.KIND_DEATH_BURST,
                        this.position().add(0, 1.5 * getEffectiveScale(), 0), Vec3.ZERO,
                        3.0f * getEffectiveScale(), isEnragedVisual());
            }
            if (this.deathTime == CUSTOM_DEATH_DURATION - 10) {
                ShockwaveRingEntity.spawn(serverLevel, this.position(), 16.0f * getEffectiveScale(),
                        isEnragedVisual() ? COLOR_ENRAGED_CRIMSON : COLOR_OCEAN_CYAN, 18);
                ModNetworking.sendLeviathanVfxNearby(serverLevel, LeviathanVfxPayload.KIND_PHASE_CLIMAX,
                        this.position().add(0, 2.0 * getEffectiveScale(), 0), Vec3.ZERO,
                        7.0f * getEffectiveScale(), isEnragedVisual());
                ModNetworking.sendShakeNearby(serverLevel, this.position(), 96.0, 2.6f, 18);
            }
            if (this.deathTime >= CUSTOM_DEATH_DURATION && !this.isRemoved()) {
                this.level().broadcastEntityEvent(this, (byte) 60);
                this.remove(Entity.RemovalReason.KILLED);
            }
        }
    }

    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        // Đã chuyển hoàn toàn sang 1–2 hạt nhẹ phía Client trong LeviathanGeoRenderer (0 packet idle từ Server!)
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.LEVIATHAN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ELDER_GUARDIAN_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.LEVIATHAN_DEATH; }

    @Override
    protected void announceToNearby(ServerLevel level, Component msg) {
        announceToNearbyWithRadius(level, msg, LeviathanConfig.get().bossBarRadius);
    }

    // =========================================================================
    // LƯU & KHÔI PHỤC ĐẦY ĐỦ TRẠNG THÁI PHASE 2 / MODIFIER / TEXTURE / BOSSBAR TỪ NBT
    // =========================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saveSharedGeoBossData(tag);
        tag.putInt("LevGlobalRest", this.globalSkillRestTicks);
        tag.putInt("LevTidalCd", this.skillCooldowns.getOrDefault(SkillState.TIDAL_WRATH, 0));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadSharedGeoBossData(tag, "LevEnteredPhase2", "LevEnragedVisual", "LevPhaseTransTicks",
                "LevTransStartHp", "LevTransTotalTicks", "LevLockedYaw");
        if (tag.contains("LevGlobalRest")) this.globalSkillRestTicks = tag.getInt("LevGlobalRest");
        if (tag.contains("LevTidalCd")) this.skillCooldowns.put(SkillState.TIDAL_WRATH, tag.getInt("LevTidalCd"));

        if (this.hasEnteredPhase2) {
            this.phase = 2;
            if (getBossMaxHealth() <= 0.0f) {
                setBossMaxHealth(computeTargetBossMaxHealth(true));
            }
            applyPhase2Modifiers(); // Idempotent: gỡ modifier cũ rồi áp lại đúng 1 lần
            if (isEnragedVisual()) {
                this.bossEvent.setColor(BossEvent.BossBarColor.RED);
                Component enragedName = getStandaloneBossTitle();
                if (enragedName != null) {
                    this.setCustomName(enragedName);
                }
            }
            if (getPhaseTransitionTicks() > 0) {
                setSkillState(SkillState.PHASE_CHANGE);
            }
        } else if (getBossMaxHealth() <= 0.0f) {
            setBossMaxHealth(computeTargetBossMaxHealth(false));
        }
        // Bảo đảm khôi phục đúng Health trong NBT sau khi BossMaxHealth đã được thiết lập (tránh clamp 1024)
        if (tag.contains("Health", Tag.TAG_ANY_NUMERIC)) {
            float savedHp = tag.getFloat("Health");
            if (savedHp > 0.0f) {
                this.setHealth(savedHp);
            }
        }
        snapSyncPrevAngles();
    }

    // =========================================================================
    // CUSTOM PATH NAVIGATION (HỖ TRỢ THÂN KHỔNG LỒ 8.0 x 6.5 KHÔNG BỊ KẸT)
    // =========================================================================
    private static class LeviathanPathNavigation extends FlyingPathNavigation {
        public LeviathanPathNavigation(Mob mob, Level level) {
            super(mob, level);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            return super.createPathFinder(Math.max(maxVisitedNodes, 64));
        }
    }

    // =========================================================================
    // CUSTOM MOVE CONTROL (LOOK CONTROL & LOCK GOAL KẾ THỪA TỪ AbstractGeoMythicBoss)
    // =========================================================================
    private static class LeviathanMoveControl extends MoveControl {
        private final LeviathanBossEntity boss;

        public LeviathanMoveControl(LeviathanBossEntity boss) {
            super(boss);
            this.boss = boss;
        }

        @Override
        public void tick() {
            if (this.operation != Operation.MOVE_TO || boss.isSpawningIn() || boss.isTransitioningPhase()) return;
            Vec3 delta = new Vec3(this.wantedX, this.wantedY, this.wantedZ).subtract(boss.position());
            double dist = delta.length();
            if (dist < 0.8 * SCALE) {
                this.operation = Operation.WAIT;
                return;
            }
            double maxSpeed = boss.getAttributeValue(Attributes.MOVEMENT_SPEED) * this.speedModifier * 1.65;
            double speed = Math.min(maxSpeed, Math.max(0.14, dist * 0.12));
            boss.setDeltaMovement(boss.getDeltaMovement().lerp(delta.normalize().scale(speed), 0.16));
            boss.smoothRotateToward(delta, 5.5f);
        }
    }

    // =========================================================================
    // GOAL DI CHUYỂN LƠ LỬNG / BƠI TỰ DO (PHÓNG TẦM QUỸ ĐẠO THEO SCALE = 2.5x)
    // =========================================================================
    private static class LeviathanHoverMoveGoal extends Goal {
        private final LeviathanBossEntity boss;

        public LeviathanHoverMoveGoal(LeviathanBossEntity boss) {
            this.boss = boss;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !boss.isSpawningIn() && !boss.isTransitioningPhase() && boss.getSkillState() == SkillState.IDLE;
        }

        @Override
        public void tick() {
            if (!(boss.level() instanceof ServerLevel level)) return;
            LivingEntity target = boss.getTarget();
            Vec3 anchor = (target != null && target.isAlive()) ? target.position()
                    : (boss.homeAnchor != null ? boss.homeAnchor : boss.position());

            boss.orbitAngle += (boss.phase >= 2 ? 0.036f : 0.025f);
            double desiredDist = (target != null && target.isAlive())
                    ? ((boss.phase >= 2 ? 7.0 : 9.0) * SCALE)
                    : (9.0 * SCALE);
            double hoverY = (target != null && target.isInWater())
                    ? (0.4 * SCALE)
                    : ((2.2 + Math.sin(boss.tickCount * 0.07) * 0.85) * SCALE);
            double desiredY = anchor.y + hoverY;

            BlockPos ceiling = BlockPos.containing(boss.getX(), boss.getY() + (3.2 * SCALE), boss.getZ());
            if (!level.getBlockState(ceiling).isAir() && level.getBlockState(ceiling).getFluidState().isEmpty()) {
                desiredY = Math.min(desiredY, boss.getY() - 1.2);
            }

            Vec3 dest = new Vec3(anchor.x + Math.cos(boss.orbitAngle) * desiredDist, desiredY, anchor.z + Math.sin(boss.orbitAngle) * desiredDist);
            boss.getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 1.0);

            if (target != null && target.isAlive()) {
                // Xoay đầu nhẹ về mục tiêu qua hàm chuẩn hóa có kẹp ±45° (không bao giờ xoay nhiều vòng)
                Vec3 toT = target.position().subtract(boss.position());
                if (toT.horizontalDistanceSqr() > 1e-4) {
                    float tYaw = wrapAngle((float) (Mth.atan2(toT.z, toT.x) * Mth.RAD_TO_DEG) - 90.0f);
                    float nextHead = approachAngle(boss.yHeadRot, tYaw, 6.0f);
                    float rel = Mth.clamp(Mth.wrapDegrees(nextHead - boss.yBodyRot), -45.0f, 45.0f);
                    boss.yHeadRot = wrapAngle(boss.yBodyRot + rel);
                    boss.yHeadRotO = alignPrevAngle(boss.yHeadRotO, boss.yHeadRot);
                }

                // Chống kẹt địa hình sau 3 giây (60 ticks), đồng bộ prevAngles ngay sau dịch chuyển
                double minMove = 0.45 * SCALE;
                if (boss.lastProgressPos == null || boss.position().distanceToSqr(boss.lastProgressPos) >= minMove * minMove) {
                    boss.lastProgressPos = boss.position();
                    boss.stuckTicks = 0;
                } else if (++boss.stuckTicks >= 60) {
                    boss.stuckTicks = 0;
                    boss.orbitAngle += 1.6f;
                    Vec3 safe = findClearSpot(level, target.position());
                    ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_TELEPORT_SPLASH,
                            boss.position().add(0, 1.6 * boss.getEffectiveScale(), 0), Vec3.ZERO,
                            2.5f * boss.getEffectiveScale(), boss.isEnragedVisual());
                    boss.teleportTo(safe.x, safe.y, safe.z);
                    boss.setDeltaMovement(Vec3.ZERO);
                    boss.snapSyncPrevAngles();
                    ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_TELEPORT_SPLASH,
                            safe.add(0, 1.6 * boss.getEffectiveScale(), 0), Vec3.ZERO,
                            2.5f * boss.getEffectiveScale(), boss.isEnragedVisual());
                    boss.lastProgressPos = boss.position();
                }
            }
        }

        private Vec3 findClearSpot(ServerLevel level, Vec3 targetPos) {
            double rad = 8.5 * SCALE;
            double cyOff = 2.4 * SCALE;
            for (int i = 0; i < 8; i++) {
                double a = (Math.PI * 2.0 * i) / 8.0;
                double cx = targetPos.x + Math.cos(a) * rad, cz = targetPos.z + Math.sin(a) * rad, cy = targetPos.y + cyOff;
                BlockPos bp = BlockPos.containing(cx, cy, cz);
                if (level.getBlockState(bp).isAir() && level.getBlockState(bp.above(4)).isAir()) return new Vec3(cx, cy, cz);
            }
            return new Vec3(targetPos.x, targetPos.y + 3.8 * SCALE, targetPos.z);
        }
    }

    // =========================================================================
    // GOAL ĐIỀU PHỐI CHỌN SKILL (PHÓNG TẦM THEO SCALE 2.5x + COOLDOWN P2 / 1.5)
    // =========================================================================
    private static class LeviathanSkillSelectorGoal extends Goal {
        private final LeviathanBossEntity boss;

        public LeviathanSkillSelectorGoal(LeviathanBossEntity boss) {
            this.boss = boss;
        }

        @Override
        public boolean canUse() {
            LivingEntity t = boss.getTarget();
            return t != null && t.isAlive() && !boss.isSpawningIn() && !boss.isTransitioningPhase()
                    && boss.getSkillState() == SkillState.IDLE;
        }

        @Override
        public void tick() {
            if (!(boss.level() instanceof ServerLevel level)) return;
            LivingEntity target = boss.getTarget();
            if (target == null || !target.isAlive()) return;
            if (boss.globalSkillRestTicks > 0) {
                boss.globalSkillRestTicks--;
                return;
            }
            SkillState chosen = pickSkill(level, target);
            if (chosen == SkillState.IDLE) {
                boss.globalSkillRestTicks = 10;
                return;
            }

            LeviathanConfig cfg = LeviathanConfig.get();
            boss.lastSkill2 = boss.lastSkill1;
            boss.lastSkill1 = chosen;
            boss.setSkillState(chosen);

            // Cooldown skill Phase 2 chia 1.5 (theo phase2StatMultiplier)
            double cdDiv = (boss.phase >= 2) ? cfg.phase2StatMultiplier : 1.0;
            int baseCd = switch (chosen) {
                case TAIL_SLAM -> cfg.tailSlamCooldown;
                case BITE_CHARGE -> cfg.biteChargeCooldown;
                case ENERGY_BEAM -> cfg.beamCooldown;
                case SUMMON_MINIONS -> cfg.summonCooldown;
                case WHIRLPOOL -> cfg.whirlpoolCooldown;
                case ROAR_SHOCKWAVE -> cfg.roarShockwaveCooldown;
                case STORM_BARRAGE -> cfg.stormBarrageCooldown;
                case TIDAL_WRATH -> cfg.tidalWrathCooldown;
                default -> 60;
            };
            boss.skillCooldowns.put(chosen, Math.max(30, (int) Math.round(baseCd / cdDiv)));
        }

        private SkillState pickSkill(ServerLevel level, LivingEntity target) {
            LeviathanConfig cfg = LeviathanConfig.get();
            double dist = boss.distanceTo(target);
            float hpRatio = boss.getHealth() / boss.getMaxHealth();

            if (boss.phase >= 2 && hpRatio <= 0.35f && isReady(SkillState.TIDAL_WRATH)) {
                return SkillState.TIDAL_WRATH;
            }

            List<SkillState> pool = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            if (isReady(SkillState.TAIL_SLAM) && dist <= 13.0 * SCALE)
                add(pool, weights, SkillState.TAIL_SLAM, dist <= 7.5 * SCALE ? 26 : 12);
            if (isReady(SkillState.BITE_CHARGE) && dist >= 4.5 * SCALE && dist <= 28.0 * SCALE)
                add(pool, weights, SkillState.BITE_CHARGE, dist >= 9.0 * SCALE ? 24 : 14);
            if (isReady(SkillState.ENERGY_BEAM) && dist >= 5.5 * SCALE && dist <= 34.0 * SCALE)
                add(pool, weights, SkillState.ENERGY_BEAM, dist >= 10.0 * SCALE ? 24 : 14);
            if (isReady(SkillState.SUMMON_MINIONS) && boss.activeMinions.size() < cfg.maxActiveMinions - 1)
                add(pool, weights, SkillState.SUMMON_MINIONS, boss.activeMinions.isEmpty() ? 18 : 8);

            if (boss.phase >= 2) {
                if (isReady(SkillState.WHIRLPOOL) && dist <= 22.0 * SCALE) add(pool, weights, SkillState.WHIRLPOOL, 22);
                if (isReady(SkillState.ROAR_SHOCKWAVE) && dist <= 18.0 * SCALE)
                    add(pool, weights, SkillState.ROAR_SHOCKWAVE, dist <= 11.0 * SCALE ? 26 : 16);
                if (isReady(SkillState.STORM_BARRAGE) && dist <= 32.0 * SCALE) add(pool, weights, SkillState.STORM_BARRAGE, 24);
            }
            if (pool.isEmpty()) return SkillState.IDLE;

            int total = 0;
            for (int w : weights) total += w;
            int roll = level.random.nextInt(total);
            int acc = 0;
            for (int i = 0; i < pool.size(); i++) {
                acc += weights.get(i);
                if (roll < acc) return pool.get(i);
            }
            return pool.get(0);
        }

        private void add(List<SkillState> pool, List<Integer> weights, SkillState s, int w) {
            if (s == boss.lastSkill1 && s == boss.lastSkill2) return;
            pool.add(s);
            weights.add(s == boss.lastSkill1 ? Math.max(2, w / 4) : w);
        }

        private boolean isReady(SkillState s) {
            return boss.skillCooldowns.getOrDefault(s, 0) <= 0;
        }
    }

    // =========================================================================
    // BASE SYNCED SKILL GOAL CHO 8 SKILL (ĐỒNG BỘ 0-TICK QUA LeviathanAttackTiming + 0 sendParticles!)
    // =========================================================================
    private abstract static class BaseSyncedSkillGoal extends Goal {
        protected final LeviathanBossEntity boss;
        protected final SkillState ownedState;
        protected final SkillTimeline timeline;
        protected double prevAnimTick = 0.0;
        protected double animTick = 0.0;
        protected float animSpeed = 1.0f;
        protected int realTick = 0;

        protected BaseSyncedSkillGoal(LeviathanBossEntity boss, SkillState ownedState) {
            this.boss = boss;
            this.ownedState = ownedState;
            this.timeline = LeviathanAttackTiming.forSkill(ownedState);
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return boss.getSkillState() == ownedState && !boss.isTransitioningPhase() && !boss.isSpawningIn() && boss.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.prevAnimTick = 0.0;
            this.animTick = 0.0;
            this.realTick = 0;
            this.animSpeed = LeviathanAttackTiming.getAnimSpeed(ownedState, boss.phase >= 2);
            boss.playActionAnim(timeline.animKey(), this.animSpeed);
            onSkillStart();
        }

        protected void onSkillStart() {}

        @Override
        public void tick() {
            if (!(boss.level() instanceof ServerLevel level)) return;
            this.prevAnimTick = this.animTick;
            this.animTick += this.animSpeed;
            this.realTick++;
            onSkillTick(level, boss.getTarget(), this.prevAnimTick, this.animTick, this.realTick);
        }

        protected abstract void onSkillTick(ServerLevel level, LivingEntity target,
                                            double prevA, double currA, int tick);
    }

    // --- SKILL 1: TAIL SLAM (36t / 1.80s, impact đúng tick 16, đòn nặng 30 -> P2: 45) ---
    private static class TailSlamGoal extends BaseSyncedSkillGoal {
        public TailSlamGoal(LeviathanBossEntity boss) { super(boss, SkillState.TAIL_SLAM); }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevA, double currA, int tick) {
            float eff = boss.getEffectiveScale();
            boss.setDeltaMovement(boss.getDeltaMovement().scale(0.72));
            HitWindow hw = timeline.hitWindows()[0];

            if (tick == 1) {
                level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.HOSTILE, 2.2f, 0.62f);
            }
            if (currA < hw.impactTick() && tick % 4 == 1) {
                Vec3 backDir = boss.getLookAngle().multiply(-1, 0, -1);
                backDir = backDir.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : backDir.normalize();
                float rad = (float) ((3.0 + (currA / hw.impactTick()) * 7.5) * eff);
                ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_TAIL_TELEGRAPH,
                        boss.position().add(0, 0.8 * eff, 0), backDir, rad, boss.isEnragedVisual());
            }
            if (hw.crossedImpact(prevA, currA)) {
                float dmg = LeviathanConfig.get().tailSlamDamage * boss.skillDamageScale();
                double range = (boss.phase >= 2 ? 12.5 : 10.5) * eff;
                Vec3 backDir = boss.getLookAngle().multiply(-1, 0, -1);
                backDir = backDir.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : backDir.normalize();

                level.playSound(null, boss.blockPosition(), ModSounds.LEVIATHAN_SLAM, SoundSource.HOSTILE, 2.8f, 0.68f);
                ModNetworking.sendShakeNearby(level, boss.position(), 40.0 * eff, 1.85f, 12);
                EnergySlashEntity.spawn(level, boss, boss.position().add(0, 1.4 * eff, 0), backDir, 1.45f, 8.5f * eff, 0f,
                        boss.isEnragedVisual() ? COLOR_ENRAGED_CRIMSON : COLOR_EMBER_ORANGE, 0f, false);
                ShockwaveRingEntity.spawn(level, boss.position(), (float) range * 0.85f,
                        boss.isEnragedVisual() ? COLOR_ENRAGED_CRIMSON : COLOR_OCEAN_CYAN, 14);
                ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_TAIL_IMPACT,
                        boss.position().add(backDir.scale(4.5 * eff)), backDir, (float) range, boss.isEnragedVisual());

                AABB box = boss.getBoundingBox().inflate(range);
                for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    Vec3 toP = p.position().subtract(boss.position());
                    double d = toP.horizontalDistance();
                    if (d <= range) {
                        Vec3 hDir = d > 1e-3 ? new Vec3(toP.x, 0, toP.z).normalize() : backDir;
                        if (d < 4.5 * eff || hDir.dot(backDir) > -0.25) {
                            p.hurt(boss.damageSources().mobAttack(boss), dmg);
                            p.setDeltaMovement(p.getDeltaMovement().add(hDir.x * 1.35, 0.65, hDir.z * 1.35));
                            p.hurtMarked = true;
                        }
                    }
                }
            }
            if (currA >= timeline.totalTicks()) boss.finishSkill(22);
        }
    }

    // --- SKILL 2: BITE CHARGE (42t / 2.10s, lao táp đúng cửa sổ 16..32, đòn nặng 30 -> P2: 45) ---
    private static class BiteChargeGoal extends BaseSyncedSkillGoal {
        private Vec3 lockDir = new Vec3(0, 0, 1);
        private final Set<UUID> hit = new HashSet<>();

        public BiteChargeGoal(LeviathanBossEntity boss) { super(boss, SkillState.BITE_CHARGE); }

        @Override
        protected void onSkillStart() {
            hit.clear();
            lockDir = boss.getLookAngle().normalize();
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevA, double currA, int tick) {
            float eff = boss.getEffectiveScale();
            HitWindow hw = timeline.hitWindows()[0];

            if (tick == 1) {
                level.playSound(null, boss.blockPosition(), ModSounds.LEVIATHAN_ROAR, SoundSource.HOSTILE, 2.4f, 0.90f);
            }
            if (currA < hw.hitStartTick()) {
                if (currA <= timeline.lockAimTick() && target != null && target.isAlive()) {
                    Vec3 aim = target.position().add(0, 1.0, 0).subtract(boss.position());
                    if (aim.lengthSqr() > 1e-4) lockDir = aim.normalize();
                }
                boss.smoothRotateToward(lockDir, 9.5f);
                boss.setDeltaMovement(lockDir.scale(-0.10 * eff));
                if (tick % 4 == 1) {
                    ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_BITE_TELEGRAPH,
                            boss.getMouthWorldPosition(), lockDir, 18.0f * eff, boss.isEnragedVisual());
                }
                return;
            }
            if (hw.crossedImpact(prevA, currA)) {
                level.playSound(null, boss.blockPosition(), ModSounds.LEVIATHAN_ATTACK, SoundSource.HOSTILE, 2.8f, 0.65f);
            }
            if (hw.isActive(prevA, currA)) {
                double chargeSpeed = (boss.phase >= 2 ? 1.85 : 1.45) * 1.45;
                boss.setDeltaMovement(lockDir.scale(chargeSpeed));
                boss.smoothRotateToward(lockDir, 8.5f);
                if (tick % 3 == 0) {
                    ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_BITE_DASH,
                            boss.position().add(0, 1.5 * eff, 0), lockDir, 2.2f * eff, boss.isEnragedVisual());
                }
                float dmg = LeviathanConfig.get().biteChargeDamage * boss.skillDamageScale();
                for (Player p : level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(1.6 * eff),
                        pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    if (hit.add(p.getUUID())) {
                        p.hurt(boss.damageSources().mobAttack(boss), dmg);
                        p.setDeltaMovement(p.getDeltaMovement().add(lockDir.x * 1.4, 0.55, lockDir.z * 1.4));
                        p.hurtMarked = true;
                        level.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.8f, 0.72f);
                        ModNetworking.sendShakeNearby(level, p.position(), 36.0, 1.5f, 10);
                    }
                }
            }
            if (currA >= timeline.totalTicks()) boss.finishSkill(24);
        }
    }

    // --- SKILL 3: ENERGY BEAM (56t / 2.80s, khai hỏa đúng tick 18 từ đúng `mouth_locator` giữa 2 răng nanh ngà!) ---
    private static class EnergyBeamGoal extends BaseSyncedSkillGoal {
        private Vec3 lockDir = new Vec3(0, 0, 1);

        public EnergyBeamGoal(LeviathanBossEntity boss) { super(boss, SkillState.ENERGY_BEAM); }

        @Override
        protected void onSkillStart() {
            lockDir = boss.getLookAngle().normalize();
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevA, double currA, int tick) {
            float eff = boss.getEffectiveScale();
            boss.setDeltaMovement(boss.getDeltaMovement().scale(0.75));
            HitWindow hw = timeline.hitWindows()[0];
            Vec3 mouth = boss.getMouthWorldPosition();

            if (tick == 1) {
                level.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.5f, 0.75f);
            }
            if (currA < hw.impactTick()) {
                if (currA <= timeline.lockAimTick() && target != null && target.isAlive()) {
                    Vec3 aim = target.position().add(0, 1.0, 0).subtract(mouth);
                    if (aim.lengthSqr() > 1e-4) lockDir = lockDir.lerp(aim.normalize(), 0.28).normalize();
                }
                boss.smoothRotateToward(lockDir, 8.0f);
                if (tick % 4 == 1) {
                    ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_BEAM_TELEGRAPH,
                            mouth, lockDir, 28.0f * eff, boss.isEnragedVisual());
                }
                return;
            }
            if (hw.crossedImpact(prevA, currA)) {
                int beamDur = Math.max(18, Math.round((hw.hitEndTick() - hw.hitStartTick()) / animSpeed));
                float tickDmg = LeviathanConfig.get().beamTickDamage * boss.skillDamageScale();
                level.playSound(null, boss.blockPosition(), ModSounds.LEVIATHAN_BEAM, SoundSource.HOSTILE, 2.8f, 0.75f);
                Vec3 end = mouth.add(lockDir.scale(34.0 * eff));
                LaserBeamEntity.spawn(level, boss, mouth, end, (boss.phase >= 2 ? 1.15f : 0.90f) * eff,
                        boss.isEnragedVisual() ? COLOR_ENRAGED_CRIMSON : COLOR_OCEAN_CYAN, beamDur, tickDmg, boss.phase >= 2);
                LaserBeamEntity.spawn(level, boss, mouth, end, 0.45f * eff,
                        boss.isEnragedVisual() ? COLOR_GOLD_FLARE : COLOR_EMBER_ORANGE, beamDur, 0f, false);
            }
            if (currA >= timeline.totalTicks()) boss.finishSkill(26);
        }
    }

    // --- SKILL 4: SUMMON MINIONS (36t / 1.80s, triệu hồi đúng tick 16 khi 4 tay dang rộng!) ---
    private static class SummonMinionsGoal extends BaseSyncedSkillGoal {
        public SummonMinionsGoal(LeviathanBossEntity boss) { super(boss, SkillState.SUMMON_MINIONS); }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevA, double currA, int tick) {
            float eff = boss.getEffectiveScale();
            boss.setDeltaMovement(boss.getDeltaMovement().scale(0.75));
            HitWindow hw = timeline.hitWindows()[0];

            if (tick == 1) {
                level.playSound(null, boss.blockPosition(), ModSounds.LEVIATHAN_ROAR, SoundSource.HOSTILE, 2.4f, 0.80f);
            }
            if (currA < hw.impactTick() && tick % 5 == 1) {
                ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_SUMMON_RING,
                        boss.position().add(0, 1.2 * eff, 0), Vec3.ZERO, 5.5f * eff, boss.isEnragedVisual());
            }
            if (hw.crossedImpact(prevA, currA)) {
                int cap = Math.max(0, LeviathanConfig.get().maxActiveMinions - boss.activeMinions.size());
                int spawnCount = Math.min(cap, (boss.phase >= 2 ? 3 : 2) + level.random.nextInt(2));
                double ringR = 5.4 * eff;
                for (int i = 0; i < spawnCount; i++) {
                    EntityType<? extends Mob> mt = (i % 3 == 0) ? EntityType.GUARDIAN : EntityType.DROWNED;
                    Mob m = mt.create(level);
                    if (m != null) {
                        double a = (Math.PI * 2.0 * i) / Math.max(1, spawnCount);
                        double sx = boss.getX() + Math.cos(a) * ringR, sz = boss.getZ() + Math.sin(a) * ringR;
                        m.moveTo(sx, boss.getY() + 1.0 * eff, sz, level.random.nextFloat() * 360f, 0f);
                        m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
                        AbstractAbyssalBoss.scaleSummonedMinion(m, Math.max(4, boss.getFloor() / 2), false);
                        if (target != null) m.setTarget(target);
                        level.addFreshEntity(m);
                        boss.activeMinions.add(m.getUUID());
                        ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_TELEPORT_SPLASH,
                                new Vec3(sx, boss.getY() + 1.2 * eff, sz), Vec3.ZERO, 1.8f, boss.isEnragedVisual());
                    }
                }
                ShockwaveRingEntity.spawn(level, boss.position(), 9.0f * eff, COLOR_OCEAN_CYAN, 16);
            }
            if (currA >= timeline.totalTicks()) boss.finishSkill(24);
        }
    }

    // --- SKILL 5 (Phase 2): WHIRLPOOL / VORTEX (84t / 4.20s, xoáy đúng cửa sổ 16..76, 0 vòng lặp sendParticles!) ---
    private static class WhirlpoolGoal extends BaseSyncedSkillGoal {
        public WhirlpoolGoal(LeviathanBossEntity boss) { super(boss, SkillState.WHIRLPOOL); }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevA, double currA, int tick) {
            float eff = boss.getEffectiveScale();
            boss.setDeltaMovement(boss.getDeltaMovement().scale(0.75));
            HitWindow hw = timeline.hitWindows()[0];
            Vec3 center = boss.position().add(0, 1.0 * eff, 0);
            double vortexR = 16.5 * eff;
            double dmgR = 9.5 * eff;

            if (tick == 1) {
                level.playSound(null, boss.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 2.4f, 0.70f);
            }
            if (currA < hw.hitStartTick()) {
                if (tick % 4 == 1) {
                    float rad = (float) ((14.0 * eff) * (1.0 - (currA / hw.hitStartTick()) * 0.4));
                    ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_WHIRLPOOL_RING,
                            boss.position().add(0, 0.5 * eff, 0), Vec3.ZERO, rad, boss.isEnragedVisual());
                }
                return;
            }
            if (hw.isActive(prevA, currA)) {
                if (tick % 4 == 0) {
                    ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_WHIRLPOOL_SPIRAL,
                            center, Vec3.ZERO, (float) vortexR, boss.isEnragedVisual());
                }
                if (tick % 16 == 1) {
                    ShockwaveRingEntity.spawn(level, boss.position(), 13.0f * eff, COLOR_OCEAN_CYAN, 16);
                    level.playSound(null, boss.blockPosition(), SoundEvents.CONDUIT_AMBIENT, SoundSource.HOSTILE, 2.4f, 0.65f);
                }
                float tickDmg = LeviathanConfig.get().whirlpoolTickDamage * boss.skillDamageScale();
                AABB box = new AABB(center.x - vortexR, center.y - 5 * eff, center.z - vortexR,
                        center.x + vortexR, center.y + 7 * eff, center.z + vortexR);
                for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    double dist = p.position().distanceTo(center);
                    if (dist <= vortexR && dist > 2.2 * eff) {
                        Vec3 pull = center.subtract(p.position()).normalize().scale(0.18);
                        Vec3 tan = new Vec3(-pull.z, 0, pull.x).scale(0.55);
                        p.setDeltaMovement(p.getDeltaMovement().add(pull.x + tan.x, 0.02, pull.z + tan.z));
                        p.hurtMarked = true;
                    }
                    if (dist <= dmgR && tick % 12 == 0) {
                        p.hurt(boss.damageSources().mobAttack(boss), tickDmg);
                    }
                }
            }
            if (currA >= timeline.totalTicks()) boss.finishSkill(24);
        }
    }

    // --- SKILL 6 (Phase 2): ROAR SHOCKWAVE (50t / 2.50s, bung sóng đúng tick 18..42, 0 sendParticles!) ---
    private static class RoarShockwaveGoal extends BaseSyncedSkillGoal {
        private double curR = 0.0;
        private final Set<UUID> hit = new HashSet<>();

        public RoarShockwaveGoal(LeviathanBossEntity boss) { super(boss, SkillState.ROAR_SHOCKWAVE); }

        @Override
        protected void onSkillStart() {
            curR = 0.0;
            hit.clear();
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevA, double currA, int tick) {
            float eff = boss.getEffectiveScale();
            boss.setDeltaMovement(boss.getDeltaMovement().scale(0.70));
            HitWindow hw = timeline.hitWindows()[0];
            double maxR = 19.0 * eff;

            if (tick == 1) {
                level.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.8f, 0.60f);
            }
            if (currA < hw.impactTick()) {
                if (tick % 4 == 1) {
                    float rad = (float) Math.max(2.0 * eff, (9.0 * eff) * (1.0 - currA / hw.impactTick()));
                    ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_ROAR_TELEGRAPH,
                            boss.position().add(0, 1.2 * eff, 0), Vec3.ZERO, rad, boss.isEnragedVisual());
                }
                return;
            }
            if (hw.crossedImpact(prevA, currA)) {
                level.playSound(null, boss.blockPosition(), ModSounds.LEVIATHAN_ROAR, SoundSource.HOSTILE, 3.2f, 0.60f);
                ModNetworking.sendShakeNearby(level, boss.position(), 88.0, 2.4f, 16);
                ShockwaveRingEntity.spawn(level, boss.position(), (float) maxR, COLOR_ENRAGED_CRIMSON, 24);
                ShockwaveRingEntity.spawn(level, boss.position().add(0, 0.35, 0), (float) maxR, COLOR_GOLD_FLARE, 24);
            }
            if (hw.isActive(prevA, currA)) {
                double prevR = curR;
                double frac = Mth.clamp((currA - hw.hitStartTick()) / Math.max(1.0, hw.hitEndTick() - hw.hitStartTick()), 0.0, 1.0);
                curR = frac * maxR;
                if (tick % 3 == 0) {
                    ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_ROAR_WAVE,
                            boss.position().add(0, 0.5, 0), Vec3.ZERO, (float) curR, boss.isEnragedVisual());
                }

                float dmg = LeviathanConfig.get().roarShockwaveDamage * boss.skillDamageScale();
                AABB box = new AABB(boss.getX() - curR - 4, boss.getY() - 5, boss.getZ() - curR - 4,
                        boss.getX() + curR + 4, boss.getY() + 8, boss.getZ() + curR + 4);
                for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    double hd = Math.hypot(p.getX() - boss.getX(), p.getZ() - boss.getZ());
                    boolean inRing = hd >= Math.max(0.0, prevR - 1.8 * eff) && hd <= curR + 1.8 * eff;
                    boolean jumpedOver = !p.onGround() && (p.getY() - boss.getY()) > 1.15;
                    if (inRing && !jumpedOver && hit.add(p.getUUID())) {
                        p.hurt(boss.damageSources().mobAttack(boss), dmg);
                        Vec3 push = p.position().subtract(boss.position());
                        Vec3 norm = push.lengthSqr() > 1e-4 ? push.normalize() : new Vec3(1, 0, 0);
                        p.setDeltaMovement(p.getDeltaMovement().add(norm.x * 1.35, 0.56, norm.z * 1.35));
                        p.hurtMarked = true;
                    }
                }
            }
            if (currA >= timeline.totalTicks()) boss.finishSkill(24);
        }
    }

    // --- SKILL 7 (Phase 2): STORM BARRAGE (48t / 2.40s, 3 đợt đúng tick 14 / 24 / 34, 0 sendParticles!) ---
    private static class StormBarrageGoal extends BaseSyncedSkillGoal {
        private Vec3 cachedTargetPos = Vec3.ZERO;

        public StormBarrageGoal(LeviathanBossEntity boss) { super(boss, SkillState.STORM_BARRAGE); }

        @Override
        protected void onSkillStart() {
            cachedTargetPos = boss.getTarget() != null ? boss.getTarget().position() : boss.position();
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevA, double currA, int tick) {
            float eff = boss.getEffectiveScale();
            boss.setDeltaMovement(boss.getDeltaMovement().scale(0.75));
            if (tick == 1) {
                level.playSound(null, boss.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.5f, 0.82f);
            }
            if (currA < timeline.hitWindows()[0].impactTick() && tick % 4 == 1) {
                ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_STORM_CHARGE,
                        boss.position().add(0, 3.0 * eff, 0), Vec3.ZERO, 4.5f * eff, boss.isEnragedVisual());
            }
            for (HitWindow hw : timeline.hitWindows()) {
                if (hw.crossedImpact(prevA, currA)) {
                    Vec3 anchor = (target != null && target.isAlive()) ? target.position() : cachedTargetPos;
                    float dmg = LeviathanConfig.get().stormBarrageDamage * boss.skillDamageScale();
                    for (int i = 0; i < 3; i++) {
                        double ang = level.random.nextDouble() * Math.PI * 2.0;
                        double rad = (i == 0) ? 0.8 : ((3.5 + level.random.nextDouble() * 6.5) * eff);
                        Vec3 spot = anchor.add(Math.cos(ang) * rad, 0.0, Math.sin(ang) * rad);
                        MagicCircleEntity.spawn(level, boss, spot, 3.2f * eff, 22,
                                (i % 2 == 0) ? COLOR_ENRAGED_CRIMSON : COLOR_OCEAN_CYAN, dmg, true, false);
                        Vec3 sky = spot.add((level.random.nextDouble() - 0.5) * 4.0 * eff, 14.0 * eff,
                                (level.random.nextDouble() - 0.5) * 4.0 * eff);
                        AbyssalOrbEntity.spawn(level, boss, sky, spot.subtract(sky).normalize().scale(1.15), 1.0f * eff,
                                COLOR_OCEAN_CYAN, dmg * 0.7f, 3.0f * eff, false, false);
                    }
                }
            }
            if (currA >= timeline.totalTicks()) boss.finishSkill(24);
        }
    }

    // --- SKILL 8 (ULTIMATE Phase 2): TIDAL WRATH (52t / 2.60s, vút lên 0..22 -> bổ nhào 22..36 -> đập nổ đúng tick 36!) ---
    private static class TidalWrathGoal extends BaseSyncedSkillGoal {
        private Vec3 dropPos = Vec3.ZERO;
        private boolean impacted = false;

        public TidalWrathGoal(LeviathanBossEntity boss) { super(boss, SkillState.TIDAL_WRATH); }

        @Override
        protected void onSkillStart() {
            dropPos = boss.getTarget() != null ? boss.getTarget().position() : boss.position();
            impacted = false;
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevA, double currA, int tick) {
            float eff = boss.getEffectiveScale();
            HitWindow hw = timeline.hitWindows()[0];

            if (tick == 1) {
                boss.announceToNearby(level, Component.literal("🌊 LEVIATHAN TUNG TUYỆT KỸ: TIDAL WRATH! 🌊")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                level.playSound(null, boss.blockPosition(), ModSounds.LEVIATHAN_PHASE_CHANGE, SoundSource.HOSTILE, 3.2f, 0.62f);
            }
            if (currA < hw.hitStartTick()) {
                if (target != null && target.isAlive() && currA <= timeline.lockAimTick()) {
                    dropPos = target.position();
                }
                boss.setDeltaMovement(dropPos.add(0, 11.5 * eff, 0).subtract(boss.position()).scale(0.18));
                if (tick % 4 == 1) {
                    ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_TIDAL_TELEGRAPH,
                            dropPos.add(0, 0.35, 0), Vec3.ZERO, 11.0f * eff, true);
                }
                return;
            }
            if (!impacted && hw.isActive(prevA, currA)) {
                Vec3 dive = dropPos.add(0, 1.0 * eff, 0).subtract(boss.position());
                if (dive.lengthSqr() > 1e-4) {
                    boss.setDeltaMovement(dive.normalize().scale(2.65));
                }
                if (tick % 3 == 0) {
                    ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_BITE_DASH,
                            boss.position().add(0, 1.5 * eff, 0), Vec3.ZERO, 2.5f * eff, true);
                }
                double hitDist = 4.5 * eff;
                if (boss.position().distanceToSqr(dropPos) <= hitDist * hitDist || hw.crossedImpact(prevA, currA)) {
                    impacted = true;
                    impact(level, eff);
                }
            }
            if (currA >= timeline.totalTicks()) {
                if (!impacted) {
                    impacted = true;
                    impact(level, eff);
                }
                boss.finishSkill(36);
            }
        }

        private void impact(ServerLevel level, float eff) {
            Vec3 pos = boss.position();
            float dmg = LeviathanConfig.get().tidalWrathDamage * boss.skillDamageScale();
            double impactR = 14.5 * eff;
            boss.setDeltaMovement(Vec3.ZERO);

            level.playSound(null, boss.blockPosition(), ModSounds.LEVIATHAN_SLAM, SoundSource.HOSTILE, 3.4f, 0.55f);
            level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.62f);
            ModNetworking.sendShakeNearby(level, pos, 100.0, 3.5f, 26);

            ShockwaveRingEntity.spawn(level, pos, 20.0f * eff, COLOR_ENRAGED_CRIMSON, 24);
            ShockwaveRingEntity.spawn(level, pos.add(0, 1.0 * eff, 0), 14.5f * eff, COLOR_GOLD_FLARE, 20);
            ShockwaveRingEntity.spawn(level, pos.add(0, 2.0 * eff, 0), 10.0f * eff, COLOR_OCEAN_CYAN, 16);
            ModNetworking.sendLeviathanVfxNearby(level, LeviathanVfxPayload.KIND_TIDAL_IMPACT,
                    pos.add(0, 1.2 * eff, 0), Vec3.ZERO, (float) impactR, true);

            AABB box = new AABB(pos.x - impactR, pos.y - 6, pos.z - impactR, pos.x + impactR, pos.y + 8 * eff, pos.z + impactR);
            for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                double d = p.position().distanceTo(pos);
                if (d <= impactR) {
                    p.hurt(boss.damageSources().mobAttack(boss), dmg * (float) Math.max(0.60, 1.0 - (d / (impactR * 1.25))));
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                    Vec3 away = p.position().subtract(pos);
                    double len = Math.max(0.1, away.length());
                    p.setDeltaMovement(p.getDeltaMovement().add(away.x / len * 1.55, 0.95, away.z / len * 1.55));
                    p.hurtMarked = true;
                }
            }
        }
    }
}
