package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.config.CinderDragonConfig;
import com.abyssalrpg.entity.mythic.cinder.CinderDragonHitboxPartEntity;
import com.abyssalrpg.entity.mythic.cinder.CinderDragonSkillGoals;
import com.abyssalrpg.entity.mythic.cinder.DragonAttackTiming;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.CinderDragonVfxPayload;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BOSS CINDER DRAGON — HỎA LONG GIÁP ĐÁ (`abyssalrpg:cinder_dragon`, GeckoLib 4.8.3).
 *
 * Kiến trúc tái sử dụng trọn bộ {@link AbstractGeoMythicBoss} (`BossHealthHolder` + `BossDefenseHolder`):
 * - Kích thước: `60%` Nightmare Knight (`SCALE = 4.5f` vs `7.5f`, nhân `visualScaleMultiplier` từ config).
 * - Multipart proxy hitbox 6 bộ phận (`head_neck ×1.25`, `body_chest ×1.0`, `wing_l/r ×0.85`, `tail_mid/tip ×0.85`).
 * - Phase 1: `2700 HP`, Defense `20` (qua `BossDefenseHolder`), 6 kỹ năng (nổi bật Cinder Breath quét ngang).
 * - Phase 2 "Vô Form" (cận tử <= 5% hoặc <= 1 HP): 5.5s biến hình bất tử khóa AI/góc xoay, hồi đầy lên `4590 HP`,
 *   Defense `20 -> 34`, Damage `×1.7`, Move/Fly Speed `×1.7`, Turn `×1.25`, Cooldown `/1.7`, +4 chiêu độc quyền Phase 2.
 * - Điều hướng kết hợp mặt đất (steering chống kẹt > 3s) + bay có kiểm soát (luôn đáp an toàn gần người chơi).
 * - 0 vòng lặp `ServerLevel.sendParticles` mỗi tick — gom toàn bộ VFX qua {@link CinderDragonVfxPayload}.
 */
public class CinderDragonBossEntity extends AbstractGeoMythicBoss {
    /** Kích thước Rồng Cổ Đại mới: `5.35f` (cũ `4.5f`) — vai/đầu `8.0–8.8` block, dài `~25.5–26.0` block, sải cánh `~31.5` block. */
    public static final float SCALE = 5.35f;

    /** Collision box chính gọn ở thân giữa để rồng bước mượt qua cây/đồi (step_height 2.4); 10 proxy hitbox phủ trọn thân/chân/cổ/đầu/cánh/đuôi. */
    public static final float CORE_COLLISION_WIDTH = 3.6f;
    public static final float CORE_COLLISION_HEIGHT = 4.4f;
    public static final float CORE_EYE_HEIGHT = 6.2f;

    // --- Hằng số tên bone khớp `cinder_dragon.geo.json` (57 bones đa khớp nối!) ---
    public static final String BODY_CHEST_BONE = "body_chest";
    public static final String NECK_1_BONE = "neck_1";
    public static final String NECK_2_BONE = "neck_2";
    public static final String NECK_3_BONE = "neck_3";
    public static final String NECK_4_BONE = "neck_4";
    public static final String HEAD_BONE = "head";
    public static final String JAW_BONE = "jaw";
    public static final String MOUTH_FLAME_BONE = "mouth_flame";
    public static final String WING_L_BONE = "wing_l";
    public static final String WING_R_BONE = "wing_r";
    public static final String TAIL_4_BONE = "tail_4";
    public static final String TAIL_7_BONE = "tail_7";
    public static final String TAIL_9_BONE = "tail_9";

    // --- Màu VFX hỏa long ---
    public static final int COLOR_CINDER_ORANGE = 0xFF6A14;
    public static final int COLOR_CRIMSON_FLAME = 0xD42416;
    public static final int COLOR_WHITE_HOT = 0xFFF0A0;

    private static final ResourceLocation PHASE2_SPEED_MOD_ID = AbyssalRpgMod.id("cinder_dragon_phase2_speed");

    public record PartSpec(String name, float modelX, float modelY, float modelZ,
                           float baseWidth, float baseHeight, float damageMultiplier) {}

    /**
     * 10 proxy hitbox đồng bộ 100% theo `cinder_dragon.geo.json` V3 (57 khớp nối, dáng cúi săn mồi dài ~149 px):
     * - Bao phủ trọn đầu/sừng, cổ 4 đốt, lồng ngực, thân sau/hông, 2 chân trước, 2 cánh và đuôi lưỡi liềm 9 đốt!
     */
    public static final PartSpec[] MULTIPART_SPECS = {
            new PartSpec("head_neck",   0.0f, 12.0f, -44.0f, 5.0f, 5.2f, 1.25f),
            new PartSpec("body_chest",  0.0f,  0.5f, -11.5f, 7.6f, 7.2f, 1.00f),
            new PartSpec("body_rear",   0.0f,  0.5f, +14.0f, 7.2f, 6.8f, 1.00f),
            new PartSpec("wing_l",     38.0f, 21.0f,  -4.0f, 8.2f, 6.0f, 0.85f),
            new PartSpec("wing_r",    -38.0f, 21.0f,  -4.0f, 8.2f, 6.0f, 0.85f),
            new PartSpec("tail_mid",    0.0f,  2.0f, +42.0f, 4.4f, 4.4f, 0.85f),
            new PartSpec("tail_tip",    0.0f,  7.5f, +72.0f, 5.0f, 5.2f, 0.85f),
            new PartSpec("leg_fl",    +14.0f,  0.0f, -17.2f, 4.2f, 5.2f, 1.00f),
            new PartSpec("leg_fr",    -14.0f,  0.0f, -17.2f, 4.2f, 5.2f, 1.00f),
            new PartSpec("neck_low",    0.0f,  5.5f, -28.0f, 5.4f, 5.2f, 1.00f)
    };

    // --- 16 Animation GeckoLib ---
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.cinder_dragon.idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("animation.cinder_dragon.walk");
    private static final RawAnimation ANIM_FLY = RawAnimation.begin().thenLoop("animation.cinder_dragon.fly");
    private static final RawAnimation ANIM_HOVER = RawAnimation.begin().thenLoop("animation.cinder_dragon.hover");
    private static final RawAnimation ANIM_DEATH = RawAnimation.begin().thenPlayAndHold("animation.cinder_dragon.death");

    public enum SkillState {
        IDLE(0, null),
        CINDER_BREATH(1, "breath"),
        MAGMA_FIREBALL_BARRAGE(2, "fireball"),
        SAVAGE_BITE(3, "bite"),
        WING_CLAW_SWEEP(4, "wing_sweep"),
        TAIL_CRESCENT_SLAM(5, "tail_slam"),
        WINGED_STOMP(6, "stomp"),
        // 4 chiêu độc quyền Phase 2
        SKYFALL_INFERNO(7, "skyfall_inferno"),
        SCORCHED_EARTH_BEAM(8, "beam"),
        CATACLYSM_WING_DIVE(9, "dive"),
        ASHEN_SUPERNOVA(10, "supernova"),
        PHASE_CHANGE(11, "phase_change");

        public final int id;
        @Nullable
        public final String animKey;

        SkillState(int id, @Nullable String animKey) {
            this.id = id;
            this.animKey = animKey;
        }

        public static SkillState byId(int id) {
            for (SkillState s : values()) {
                if (s.id == id) return s;
            }
            return IDLE;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_SKILL_STATE =
            SynchedEntityData.defineId(CinderDragonBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(CinderDragonBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOVERING =
            SynchedEntityData.defineId(CinderDragonBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_THROAT_GLOW =
            SynchedEntityData.defineId(CinderDragonBossEntity.class, EntityDataSerializers.FLOAT);

    public static final int CUSTOM_DEATH_DURATION = 64; // 3.20s khớp `death` animation
    private static volatile boolean debugLoggingEnabled = false;

    // --- Proxy multipart ---
    private final List<CinderDragonHitboxPartEntity> hitboxParts = new ArrayList<>();
    private long lastPartHitTick = -1L;
    @Nullable
    private UUID lastPartHitAttacker = null;

    // --- Vệt lửa tạm trên đất (3–4s, không phá khối) ---
    public static final class FirePatch {
        public final Vec3 pos;
        public final float radius;
        public int ticksLeft;
        public final float tickDamage;

        public FirePatch(Vec3 pos, float radius, int ticksLeft, float tickDamage) {
            this.pos = pos;
            this.radius = radius;
            this.ticksLeft = ticksLeft;
            this.tickDamage = tickDamage;
        }
    }

    private final List<FirePatch> activeFirePatches = new ArrayList<>();

    // --- Cooldown, chống lặp 3 lần & nghỉ giữa skill ---
    private final Map<SkillState, Integer> skillCooldowns = new EnumMap<>(SkillState.class);
    private SkillState lastSkill1 = SkillState.IDLE;
    private SkillState lastSkill2 = SkillState.IDLE;
    private int globalSkillRestTicks = 28;
    private int outOfCombatTicks = 0;

    // --- Chống kẹt địa hình > 3s ---
    private Vec3 lastAnchorPos = Vec3.ZERO;
    private int stuckTicks = 0;

    // --- Góc đầu Client & vị trí mouth_flame thực tế ---
    private float clientHeadYawDeg = 0.0f;
    private float clientHeadPitchDeg = 0.0f;
    private Vec3 clientMouthFlamePos = Vec3.ZERO;
    private boolean hasClientMouthFlamePos = false;
    private boolean customDeathStarted = false;
    private float lockedBreathYawDeg = 0.0f;
    private float breathSweepAngleDeg = 0.0f;

    @SuppressWarnings("this-escape")
    public CinderDragonBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
        this.xpReward = 320;
        this.noCulling = true;
        this.moveControl = new DragonGroundMoveControl(this);
        for (SkillState s : SkillState.values()) {
            this.skillCooldowns.put(s, 0);
        }
        if (!level.isClientSide) {
            CinderDragonConfig cfg = CinderDragonConfig.get();
            setBossDefense(cfg.defense);
            initializeBossHealth((float) cfg.maxHealth);
            syncStepHeight();
        }
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        CinderDragonConfig cfg = CinderDragonConfig.get();
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, VANILLA_SAFE_MAX_HEALTH_ATTR)
                .add(Attributes.ATTACK_DAMAGE, cfg.savageBiteDamage)
                .add(Attributes.ARMOR, 0.0) // Toàn bộ phòng thủ (P1=20, P2=34) quản lý thống nhất qua BossDefenseHolder!
                .add(Attributes.ARMOR_TOUGHNESS, 0.0)
                .add(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed)
                .add(Attributes.FOLLOW_RANGE, cfg.detectionRadius)
                .add(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance)
                .add(Attributes.STEP_HEIGHT, cfg.stepHeight);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKILL_STATE, SkillState.IDLE.id);
        builder.define(DATA_FLYING, false);
        builder.define(DATA_HOVERING, false);
        builder.define(DATA_THROAT_GLOW, 0.0f);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new LargeGeoGroundNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new BossLockGoal(this));
        this.goalSelector.addGoal(1, new FloatGoal(this));

        // 4 Chiêu độc quyền Phase 2 (ưu tiên cao khi đã Vô Form)
        this.goalSelector.addGoal(2, new CinderDragonSkillGoals.AshenSupernovaGoal(this));
        this.goalSelector.addGoal(3, new CinderDragonSkillGoals.SkyfallInfernoGoal(this));
        this.goalSelector.addGoal(3, new CinderDragonSkillGoals.CataclysmWingDiveGoal(this));
        this.goalSelector.addGoal(4, new CinderDragonSkillGoals.ScorchedEarthBeamGoal(this));

        // 6 Chiêu Phase 1 (dùng xuyên suốt cả 2 Phase, tốc độ & cooldown tăng mạnh ở Phase 2)
        this.goalSelector.addGoal(5, new CinderDragonSkillGoals.CinderBreathGoal(this));
        this.goalSelector.addGoal(5, new CinderDragonSkillGoals.TailCrescentSlamGoal(this));
        this.goalSelector.addGoal(6, new CinderDragonSkillGoals.SavageBiteGoal(this));
        this.goalSelector.addGoal(6, new CinderDragonSkillGoals.WingClawSweepGoal(this));
        this.goalSelector.addGoal(7, new CinderDragonSkillGoals.WingedStompGoal(this));
        this.goalSelector.addGoal(7, new CinderDragonSkillGoals.MagmaFireballBarrageGoal(this));

        // Di chuyển chủ động kết hợp steering chống kẹt
        this.goalSelector.addGoal(8, new CinderDragonSkillGoals.DragonChaseGoal(this));
        this.goalSelector.addGoal(9, new SafeLookAtPlayerGoal(this, 48.0f));
        this.goalSelector.addGoal(10, new SafeRandomLookGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                e -> e instanceof Player p && !p.isCreative() && !p.isSpectator()));
    }

    // --- Kích thước & Culling AABB rộng bao trọn sải cánh ~38.5 block ---
    public float getConfiguredScale() {
        return CinderDragonConfig.get().getEffectiveScale();
    }

    public float getScaleRatio() {
        return getConfiguredScale() / SCALE;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        float r = getScaleRatio();
        return EntityDimensions.scalable(CORE_COLLISION_WIDTH * r, CORE_COLLISION_HEIGHT * r)
                .withEyeHeight(CORE_EYE_HEIGHT * r);
    }

    // --- Nhịp bước chân trầm nặng + rung camera nhẹ ---
    private long lastFootstepTick = -100L;

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = getScaleRatio();
        // Rộng hơn toàn bộ sải cánh ~31.5 block (bán kính ngang ~38 block, cao ~24 block) chống culling khi đứng dưới bụng ngước lên
        return this.getBoundingBox().inflate(38.0 * r, 24.0 * r, 38.0 * r);
    }

    @Override
    protected int getDefaultSpawnEggFloor() {
        return 18;
    }

    @Override
    public void configureForFloor(int floor, @Nullable BlockPos arenaCenter, boolean dungeonBoss) {
        super.configureForFloor(floor, arenaCenter, dungeonBoss);
        CinderDragonConfig cfg = CinderDragonConfig.get();
        this.setCustomName(Component.translatable("entity.abyssalrpg.cinder_dragon")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);

        boolean p2 = this.hasEnteredPhase2();
        setBossDefense(computeTargetBossDefense(p2));
        initializeBossHealth(computeTargetBossMaxHealth(p2));
        syncStepHeight();
        applyPhase2AttributeModifiers(p2);

        var dmgInst = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmgInst != null) dmgInst.setBaseValue(cfg.savageBiteDamage);
        var spdInst = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spdInst != null) spdInst.setBaseValue(cfg.baseMoveSpeed);
        var followInst = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followInst != null) followInst.setBaseValue(cfg.detectionRadius);
        var kbInst = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbInst != null) kbInst.setBaseValue(cfg.knockbackResistance);
    }

    private void syncStepHeight() {
        var step = this.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) {
            step.setBaseValue(CinderDragonConfig.get().stepHeight);
        }
    }

    public float computeTargetBossMaxHealth(boolean phase2) {
        CinderDragonConfig cfg = CinderDragonConfig.get();
        return (float) (phase2 ? cfg.maxHealth * cfg.phase2HealthMultiplier : cfg.maxHealth);
    }

    public float computeTargetBossDefense(boolean phase2) {
        CinderDragonConfig cfg = CinderDragonConfig.get();
        return (float) (phase2 ? cfg.defense * cfg.phase2DefenseMultiplier : cfg.defense);
    }

    @Override public float getBossToughness() { return CinderDragonConfig.get().bossToughness; }
    @Override public float getMaxDamageReduction() { return CinderDragonConfig.get().maxDamageReduction; }
    @Override public float getMinDamagePerHit() { return CinderDragonConfig.get().minDamagePerHit; }

    public float scaleSkillDamage(float basePhase1Damage) {
        if (this.hasEnteredPhase2()) {
            return (float) (basePhase1Damage * CinderDragonConfig.get().phase2DamageMultiplier);
        }
        return basePhase1Damage;
    }

    public int scaleCooldown(int basePhase1Ticks) {
        if (this.hasEnteredPhase2()) {
            return Math.max(18, (int) Math.round(basePhase1Ticks / CinderDragonConfig.get().phase2CooldownDivisor));
        }
        return basePhase1Ticks;
    }

    public int computeDragonTelegraph(int baseTicks) {
        return computeSafeTelegraph(baseTicks, CinderDragonConfig.get().minTelegraphTicks);
    }

    // --- Trạng thái đồng bộ ---
    public SkillState getSkillState() {
        return SkillState.byId(this.entityData.get(DATA_SKILL_STATE));
    }

    public void setSkillState(SkillState state) {
        this.entityData.set(DATA_SKILL_STATE, state.id);
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public boolean isHovering() {
        return this.entityData.get(DATA_HOVERING);
    }

    public void setFlying(boolean flying, boolean hovering) {
        boolean prev = isFlying();
        this.entityData.set(DATA_FLYING, flying);
        this.entityData.set(DATA_HOVERING, flying && hovering);
        this.setNoGravity(flying);
        if (flying && !prev && this.level() instanceof ServerLevel sl) {
            sl.playSound(null, this.blockPosition(), ModSounds.CINDER_WING_FLAP, SoundSource.HOSTILE, 2.2f, 0.80f);
            this.fallDistance = 0.0f;
        }
    }

    public float getThroatGlow() {
        return this.entityData.get(DATA_THROAT_GLOW);
    }

    public void setThroatGlow(float glow) {
        this.entityData.set(DATA_THROAT_GLOW, Mth.clamp(glow, 0.0f, 1.0f));
    }

    public void setBreathSweepState(float lockedYawDeg, float sweepOffsetDeg) {
        this.lockedBreathYawDeg = lockedYawDeg;
        this.breathSweepAngleDeg = sweepOffsetDeg;
    }

    /** Tính tọa độ thế giới thật của locator `mouth_flame` theo góc thân/đầu hiện tại (khớp 100% với model V3!). */
    public Vec3 getMouthFlameWorldPos() {
        double sc = getConfiguredScale();
        Vec3 base = modelPointToWorld(0.0f, 17.1f, -47.4f, sc);
        if (getSkillState() == SkillState.CINDER_BREATH && Math.abs(this.breathSweepAngleDeg) > 0.5f) {
            Vec3 pivot = modelPointToWorld(0.0f, 15.6f, -17.2f, sc);
            Vec3 rel = base.subtract(pivot);
            return pivot.add(rotateY(rel, -Math.toRadians(this.breathSweepAngleDeg * 0.65f)));
        }
        return base;
    }

    public Vec3 getMouthFlameDirection() {
        double yawRad = (this.lockedBreathYawDeg + this.breathSweepAngleDeg) * Mth.DEG_TO_RAD;
        double dx = -Math.sin(yawRad);
        double dz = Math.cos(yawRad);
        // Miệng rồng mới cao ~7.3 block -> chúc nhẹ xuống mặt đất (-0.22) để luồng lửa quét trọn người chơi dưới đất!
        double dy = isFlying() ? -0.52 : -0.22;
        return new Vec3(dx, dy, dz).normalize();
    }

    public void updateClientMouthFlamePos(double x, double y, double z) {
        this.clientMouthFlamePos = new Vec3(x, y, z);
        this.hasClientMouthFlamePos = true;
    }

    public Vec3 getClientMouthFlamePos() {
        return this.hasClientMouthFlamePos ? this.clientMouthFlamePos : getMouthFlameWorldPos();
    }

    public boolean isHeadLockedByAnimation() {
        SkillState s = getSkillState();
        return this.isRotationLocked()
                || this.isDeadOrDying()
                || s != SkillState.IDLE;
    }

    public void stepClientHeadTracking(float targetYawDeg, float targetPitchDeg) {
        this.clientHeadYawDeg = approachAngle(this.clientHeadYawDeg, targetYawDeg, 4.5f);
        this.clientHeadPitchDeg = Mth.approach(this.clientHeadPitchDeg, targetPitchDeg, 3.5f);
    }

    public float getClientHeadYawDeg() { return this.clientHeadYawDeg; }
    public float getClientHeadPitchDeg() { return this.clientHeadPitchDeg; }

    public static boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled || CinderDragonConfig.get().debugLogs;
    }

    public static void setDebugLoggingEnabled(boolean enabled) {
        debugLoggingEnabled = enabled;
    }

    public List<CinderDragonHitboxPartEntity> getActiveHitboxParts() {
        return this.hitboxParts;
    }

    // --- Cooldown & chọn skill chống lặp 3 lần ---
    public boolean canUseSkill(SkillState state) {
        if (this.isRotationLocked() || this.getSkillState() != SkillState.IDLE) return false;
        if (this.globalSkillRestTicks > 0) return false;
        if (this.skillCooldowns.getOrDefault(state, 0) > 0) return false;
        return !(this.lastSkill1 == state && this.lastSkill2 == state);
    }

    public void markSkillUsed(SkillState state, int baseCooldownTicks) {
        this.skillCooldowns.put(state, scaleCooldown(baseCooldownTicks));
        this.lastSkill2 = this.lastSkill1;
        this.lastSkill1 = state;
        // Nghỉ giữa skill rút ngắn để tăng nhịp tấn công: Phase 1 = 0.7–1.2s (14–24t), Phase 2 = 0.4–0.75s (8–15t)
        if (this.hasEnteredPhase2()) {
            this.globalSkillRestTicks = 8 + this.random.nextInt(8);
        } else {
            this.globalSkillRestTicks = 14 + this.random.nextInt(11);
        }
    }

    public void markUltimateUsed() {
        this.skillCooldowns.put(SkillState.ASHEN_SUPERNOVA, CinderDragonConfig.get().ultimateCooldownTicks);
        this.lastSkill2 = this.lastSkill1;
        this.lastSkill1 = SkillState.ASHEN_SUPERNOVA;
        this.globalSkillRestTicks = 14;
    }

    public void addFirePatch(Vec3 pos, float radius, int durationTicks, float tickDamage) {
        if (this.activeFirePatches.size() >= 24) {
            this.activeFirePatches.remove(0);
        }
        this.activeFirePatches.add(new FirePatch(pos, radius, durationTicks, tickDamage));
    }

    // =========================================================================
    // TICK CHÍNH, MULTIPART PROXY SYNC, ANTI-STUCK & CHUYỂN PHASE 2 "VÔ FORM"
    // =========================================================================
    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel sl)) return;

        // 1. Giảm cooldown skill & rest (tăng tốc hồi chiêu 1.5x để tăng tần suất tấn công)
        if (this.globalSkillRestTicks > 0 && getSkillState() == SkillState.IDLE) {
            this.globalSkillRestTicks--;
        }
        int cdStep = (this.tickCount % 2 == 0) ? 2 : 1;
        for (Map.Entry<SkillState, Integer> e : this.skillCooldowns.entrySet()) {
            if (e.getValue() > 0) {
                e.setValue(Math.max(0, e.getValue() - cdStep));
            }
        }

        // 2. Xử lý chuỗi biến hình Phase 2 "Vô Form" (5.5s bất tử, hồi đầy 4590 HP, bùng dung nham)
        if (this.isTransitioningPhase()) {
            tickPhase2Transition(sl);
        } else if (getSkillState() == SkillState.IDLE && getThroatGlow() > 0.0f) {
            setThroatGlow(Math.max(0.0f, getThroatGlow() - 0.08f));
        }

        // 3. Duy trì & đồng bộ 6 proxy hitbox multipart
        if (this.isAlive() && !this.isRemoved()) {
            ensureAndSyncHitboxParts(sl);
        } else {
            clearHitboxParts();
        }

        // 4. Tick các vệt lửa trên đất (không phá khối)
        tickFirePatches(sl);

        // 5. Đồng bộ BossBar bán kính 112 block + hồi máu ngoài giao tranh
        CinderDragonConfig cfg = CinderDragonConfig.get();
        if (this.tickCount % 5 == 0) {
            syncBossBarPlayers(sl, cfg.bossBarRadius);
        }
        this.outOfCombatTicks = tickOutOfCombatRegen(sl, this.outOfCombatTicks,
                cfg.outOfCombatHealRadius, cfg.outOfCombatHealDelayTicks, cfg.outOfCombatHealPerSecond);

        // 6. Chống kẹt > 3s khi bám đuổi trên mặt đất
        if (!isFlying() && !isRotationLocked() && getSkillState() == SkillState.IDLE) {
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive() && this.distanceToSqr(target) > 100.0) {
                if (this.position().distanceToSqr(this.lastAnchorPos) < 1.2) {
                    this.stuckTicks++;
                    if (this.stuckTicks >= 60) {
                        this.stuckTicks = 0;
                        Vec3 toTarget = target.position().subtract(this.position());
                        Vec3 h = new Vec3(toTarget.x, 0, toTarget.z);
                        if (h.lengthSqr() > 1e-4) {
                            Vec3 step = h.normalize().scale(0.55);
                            this.setDeltaMovement(step.x, 0.45, step.z);
                            this.hurtMarked = true;
                        }
                    }
                } else {
                    this.lastAnchorPos = this.position();
                    this.stuckTicks = 0;
                }
            } else {
                this.stuckTicks = 0;
            }
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (isFlying()) {
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.92));
            this.calculateEntityAnimation(false);
        } else {
            super.travel(travelVector);
        }
    }

    private void ensureAndSyncHitboxParts(ServerLevel level) {
        float ratio = getScaleRatio();
        if (this.hitboxParts.size() != MULTIPART_SPECS.length) {
            clearHitboxParts();
            for (PartSpec spec : MULTIPART_SPECS) {
                this.hitboxParts.add(CinderDragonHitboxPartEntity.create(
                        level, this, spec.name(),
                        spec.baseWidth() * ratio, spec.baseHeight() * ratio, spec.damageMultiplier()));
            }
        }
        double totalScale = getConfiguredScale();
        boolean flying = isFlying();
        for (int i = 0; i < MULTIPART_SPECS.length; i++) {
            CinderDragonHitboxPartEntity part = this.hitboxParts.get(i);
            PartSpec spec = MULTIPART_SPECS[i];
            if (part.isRemoved() || part.level() != level) {
                part = CinderDragonHitboxPartEntity.create(
                        level, this, spec.name(),
                        spec.baseWidth() * ratio, spec.baseHeight() * ratio, spec.damageMultiplier());
                this.hitboxParts.set(i, part);
            }
            float yOff = spec.modelY();
            if (flying && (spec.name().equals("wing_l") || spec.name().equals("wing_r"))) {
                yOff += 6.0f; // Nâng hitbox cánh khớp dáng vỗ cánh khi bay
            }
            Vec3 wp = modelPointToWorld(spec.modelX(), yOff, spec.modelZ(), totalScale);
            part.setPartDimensions(spec.baseWidth() * ratio, spec.baseHeight() * ratio);
            part.syncPositionFromBone(wp.x, wp.y, wp.z, this.yBodyRot);
        }
    }

    public void clearHitboxParts() {
        for (CinderDragonHitboxPartEntity part : this.hitboxParts) {
            if (part != null && !part.isRemoved()) {
                part.discard();
            }
        }
        this.hitboxParts.clear();
    }

    private void tickFirePatches(ServerLevel level) {
        if (this.activeFirePatches.isEmpty()) return;
        List<FirePatch> snapshot = new ArrayList<>(this.activeFirePatches);
        for (FirePatch fp : snapshot) {
            if (!this.activeFirePatches.contains(fp)) continue;
            fp.ticksLeft--;
            if (fp.ticksLeft <= 0) {
                this.activeFirePatches.remove(fp);
                continue;
            }
            if (fp.ticksLeft % 10 == 0) {
                AABB box = new AABB(fp.pos.x - fp.radius, fp.pos.y - 1.0, fp.pos.z - fp.radius,
                        fp.pos.x + fp.radius, fp.pos.y + 2.5, fp.pos.z + fp.radius);
                for (Player p : level.getEntitiesOfClass(Player.class, box,
                        pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    if (p.position().distanceTo(fp.pos) <= fp.radius + 0.5) {
                        com.abyssalrpg.combat.KatanaCombatHandler.runUnparryableMagic(() ->
                                p.hurt(this.damageSources().mobAttack(this), fp.tickDamage));
                        p.igniteForSeconds(3);
                    }
                }
            }
            if (fp.ticksLeft % 14 == 0) {
                ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_FIRE_PATCH,
                        fp.pos, Vec3.ZERO, fp.radius, isEnragedVisual());
            }
        }
    }

    // =========================================================================
    // SÁT THƯƠNG, MULTIPART FORWARDING & CHẶN CÁI CHẾT PHASE 2 "VÔ FORM"
    // =========================================================================
    public boolean hurtFromPart(CinderDragonHitboxPartEntity part, DamageSource source, float amount) {
        if (this.level().isClientSide || !this.isAlive() || this.isRemoved()) return false;
        long now = this.level().getGameTime();
        UUID attackerId = source.getEntity() != null ? source.getEntity().getUUID() : null;
        if (now == this.lastPartHitTick && attackerId != null && attackerId.equals(this.lastPartHitAttacker)) {
            return false; // Chống 1 nhát chém quét trúng đồng thời 2 proxy trong cùng 1 tick
        }
        this.lastPartHitTick = now;
        this.lastPartHitAttacker = attackerId;

        float scaledAmount = amount * part.getDamageMultiplier();
        boolean result = this.hurt(source, scaledAmount);
        if (result) {
            this.setDeltaMovement(this.getDeltaMovement().x * 0.0, this.getDeltaMovement().y, this.getDeltaMovement().z * 0.0);
        }
        return result;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)) {
            return false;
        }
        if (this.isTransitioningPhase() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (!this.level().isClientSide
                && !this.hasEnteredPhase2()
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float est = estimatePostArmorDamage(source, amount);
            float threshold = (float) Math.max(1.0, this.getMaxHealth() * CinderDragonConfig.get().phase2TriggerHpRatio);
            if (this.getHealth() - est <= threshold) {
                triggerPhase2Transition((ServerLevel) this.level());
                return true;
            }
        }
        return super.hurt(source, amount);
    }

    private void triggerPhase2Transition(ServerLevel level) {
        CinderDragonConfig cfg = CinderDragonConfig.get();
        this.hasEnteredPhase2 = true;
        this.phase = 2;
        this.entityData.set(DATA_HAS_ENTERED_PHASE2, true);

        // Nếu đang bay thì hạ đáp an toàn ngay xuống mặt đất trước khi biến hình
        if (isFlying()) {
            Vec3 safe = findSafeLandingPosition(level, this.position(), this.position(), 8);
            this.setPos(safe.x, safe.y, safe.z);
            setFlying(false, false);
        }
        lockCurrentRotation();
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.scheduledActions.clear();

        float p2Max = computeTargetBossMaxHealth(true);
        setBossMaxHealth(p2Max);
        this.setHealth(Math.max(1.0f, this.getHealth()));

        this.transitionStartHp = this.getHealth();
        this.transitionTotalTicks = Math.max(100, cfg.phaseTransitionTicks); // 110 ticks = 5.5s
        this.entityData.set(DATA_PHASE_TRANS_TICKS, this.transitionTotalTicks);
        setSkillState(SkillState.PHASE_CHANGE);
        setThroatGlow(1.0f);

        level.playSound(null, this.blockPosition(), ModSounds.CINDER_PHASE_CHANGE, SoundSource.HOSTILE, 3.2f, 0.74f);
        if (cfg.enableFootstepShake) {
            ModNetworking.sendShakeNearby(level, this.position(), 72.0 * cfg.screenShakeRadiusMultiplier, 2.2f, 28);
        }
        announceToNearbyWithRadius(level,
                Component.literal("⚠ CINDER DRAGON BỪNG CHÁY VÔ FORM — GIÁP ĐÁ NỨT TOÁC LỘ LÕI DUNG NHAM! ⚠")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                cfg.bossBarRadius);
    }

    private void tickPhase2Transition(ServerLevel level) {
        int rem = this.entityData.get(DATA_PHASE_TRANS_TICKS);
        if (rem <= 0) return;

        int elapsed = this.transitionTotalTicks - rem;
        float progress = Mth.clamp((float) elapsed / Math.max(1, this.transitionTotalTicks), 0.0f, 1.0f);

        // Hồi đầy dần lên 4590 HP (100%)
        float targetHp = Mth.lerp(progress, this.transitionStartHp, this.getMaxHealth());
        if (targetHp > this.getHealth()) {
            this.setHealth(targetHp);
        }
        setThroatGlow(0.65f + 0.35f * Mth.sin(elapsed * 0.35f));

        // Đúng khoảnh khắc dang rộng sải cánh gầm vang (giây 3.0 = tick 60): bật texture enraged + glowmask + sóng nhiệt!
        if (elapsed == 60) {
            this.entityData.set(DATA_ENRAGED_VISUAL, true);
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
            this.setCustomName(Component.literal("🔥 Cinder Dragon — Hỏa Long Vô Form 🔥")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            this.bossEvent.setName(this.getDisplayName());
            level.playSound(null, this.blockPosition(), ModSounds.CINDER_ROAR, SoundSource.HOSTILE, 3.6f, 0.72f);
            if (CinderDragonConfig.get().enableFootstepShake) {
                ModNetworking.sendShakeNearby(level, this.position(), 88.0 * CinderDragonConfig.get().screenShakeRadiusMultiplier, 2.8f, 36);
            }
            ShockwaveRingEntity.spawn(level, this.position().add(0, 1.2, 0), 20.0f * getScaleRatio(), COLOR_CINDER_ORANGE, 18);
            ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_PHASE_BURST,
                    this.position().add(0, 3.6, 0), Vec3.ZERO, 20.0f * getScaleRatio(), true);
        } else if (elapsed % 14 == 0) {
            ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_PHASE_BURST,
                    this.position().add(0, 2.5, 0), Vec3.ZERO, 8.0f, elapsed >= 60);
        }

        if (isDebugLoggingEnabled() && elapsed % 15 == 0) {
            logGeoRotationDebug("CINDER_P2", rem, true);
        }

        rem--;
        this.entityData.set(DATA_PHASE_TRANS_TICKS, rem);

        if (rem == 0) {
            this.entityData.set(DATA_ENRAGED_VISUAL, true);
            this.setHealth(this.getMaxHealth());
            setBossDefense(computeTargetBossDefense(true));
            applyPhase2AttributeModifiers(true);
            setSkillState(SkillState.IDLE);
            setThroatGlow(0.35f);
            this.globalSkillRestTicks = 12;
            for (SkillState s : SkillState.values()) {
                if (s != SkillState.ASHEN_SUPERNOVA) {
                    this.skillCooldowns.put(s, 0);
                }
            }
            snapSyncPrevAngles();
        }
    }

    private void applyPhase2AttributeModifiers(boolean phase2) {
        if (phase2) {
            double speedBonus = Math.max(0.0, CinderDragonConfig.get().phase2SpeedMultiplier - 1.0);
            applyPermanentMultiplier(Attributes.MOVEMENT_SPEED, PHASE2_SPEED_MOD_ID, speedBonus);
        } else {
            removeAttributeModifierIfPresent(Attributes.MOVEMENT_SPEED, PHASE2_SPEED_MOD_ID);
        }
    }

    @Override
    public void die(DamageSource source) {
        clearHitboxParts();
        this.activeFirePatches.clear();
        setFlying(false, false);
        setSkillState(SkillState.IDLE);
        super.die(source);
    }

    @Override
    public void kill() {
        clearHitboxParts();
        this.activeFirePatches.clear();
        super.kill();
    }

    @Override
    protected void tickDeath() {
        if (!this.customDeathStarted) {
            this.customDeathStarted = true;
            setFlying(false, false);
            setSkillState(SkillState.IDLE);
            clearHitboxParts();
            if (this.level() instanceof ServerLevel sl) {
                sl.playSound(null, this.blockPosition(), ModSounds.CINDER_DEATH, SoundSource.HOSTILE, 3.2f, 0.72f);
                ModNetworking.sendCinderDragonVfxNearby(sl, CinderDragonVfxPayload.KIND_DEATH_BURST,
                        this.position().add(0, 2.5, 0), Vec3.ZERO, 12.0f, isEnragedVisual());
            }
        }
        this.deathTime++;
        if (this.deathTime >= CUSTOM_DEATH_DURATION && !this.level().isClientSide && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        clearHitboxParts();
        this.activeFirePatches.clear();
        super.remove(reason);
    }

    // --- Âm thanh trầm vang (pitch 0.72–0.85) & bước chân nặng kèm rung camera nhẹ (tắt được trong config) ---
    @Override protected SoundEvent getAmbientSound() { return ModSounds.CINDER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.CINDER_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.CINDER_DEATH; }
    @Override protected float getSoundVolume() { return 2.6f; }

    @Override
    public float getVoicePitch() {
        return 0.76f + (this.random.nextFloat() - this.random.nextFloat()) * 0.05f; // Trầm vang 0.72–0.81
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (this.isFlying()) return;
        long now = this.level().getGameTime();
        if (now - this.lastFootstepTick < 14L) return;
        this.lastFootstepTick = now;
        float pitch = 0.73f + this.random.nextFloat() * 0.09f; // 0.73–0.82 trầm nặng
        this.playSound(ModSounds.CINDER_SLAM, 1.25f, pitch);
        if (this.level() instanceof ServerLevel sl) {
            CinderDragonConfig cfg = CinderDragonConfig.get();
            if (cfg.enableFootstepShake) {
                ModNetworking.sendShakeNearby(sl, this.position(),
                        28.0 * cfg.screenShakeRadiusMultiplier * getScaleRatio(), 0.38f, 6);
            }
        }
    }

    // --- Lưu / Đọc NBT an toàn (không cộng dồn stat Phase 2 khi reload) ---
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saveSharedGeoBossData(tag);
        tag.putBoolean("CinderFlying", isFlying());
        tag.putInt("CinderUltCd", this.skillCooldowns.getOrDefault(SkillState.ASHEN_SUPERNOVA, 0));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadSharedGeoBossData(tag, null, null, null, null, null, null);
        boolean p2 = this.hasEnteredPhase2();
        float expectedMax = computeTargetBossMaxHealth(p2);
        if (getBossMaxHealth() <= 0.0f) {
            setBossMaxHealth(expectedMax);
        }
        setBossDefense(computeTargetBossDefense(p2));
        applyPhase2AttributeModifiers(p2);
        syncStepHeight();
        if (p2 && !isTransitioningPhase()) {
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
            this.setCustomName(Component.literal("🔥 Cinder Dragon — Hỏa Long Vô Form 🔥")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            this.bossEvent.setName(this.getDisplayName());
        }
        this.skillCooldowns.put(SkillState.ASHEN_SUPERNOVA, Math.max(0, tag.getInt("CinderUltCd")));
        setFlying(false, false); // Reload luôn đáp sạch tránh kẹt giữa không trung
        snapSyncPrevAngles();
    }

    // =========================================================================
    // GECKOLIB 4.8.3 CONTROLLERS (ĐỒNG BỘ 0-TICK VỚI DragonAttackTiming)
    // =========================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "locomotion", 4, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(ANIM_DEATH);
            }
            if (this.isFlying()) {
                return state.setAndContinue(this.isHovering() ? ANIM_HOVER : ANIM_FLY);
            }
            boolean moving = state.isMoving() || this.getDeltaMovement().horizontalDistanceSqr() > 0.0025;
            return state.setAndContinue(moving ? ANIM_WALK : ANIM_IDLE);
        }));

        AnimationController<CinderDragonBossEntity> actionCtrl = new AnimationController<>(this, "action", 1, state -> {
            if (this.isDeadOrDying()) {
                return PlayState.STOP;
            }
            SkillState sk = this.getSkillState();
            if (sk == SkillState.IDLE || sk.animKey == null) {
                return PlayState.STOP;
            }
            return state.setAndContinue(RawAnimation.begin().thenPlay("animation.cinder_dragon." + sk.animKey));
        });
        actionCtrl.setAnimationSpeedHandler(animatable ->
                (double) DragonAttackTiming.getAnimSpeed(animatable.getSkillState(), animatable.hasEnteredPhase2()));
        controllers.add(actionCtrl);
    }

    // --- MoveControl mượt không giật góc xoay ---
    private static final class DragonGroundMoveControl extends MoveControl {
        private final CinderDragonBossEntity dragon;

        DragonGroundMoveControl(CinderDragonBossEntity dragon) {
            super(dragon);
            this.dragon = dragon;
        }

        @Override
        public void tick() {
            if (dragon.isRotationLocked() || dragon.isFlying()) {
                this.operation = Operation.WAIT;
                return;
            }
            super.tick();
            dragon.normalizeAnglesAndSyncPrev();
        }
    }
}

