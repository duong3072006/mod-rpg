package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.config.NightmareKnightConfig;
import com.abyssalrpg.entity.mythic.nightmare.NightmareHitboxPartEntity;
import com.abyssalrpg.entity.mythic.nightmare.NightmareSkillGoals;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.network.NightmareVfxPayload;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BOSS NIGHTMARE KNIGHT (GeckoLib 4.8.3 — Tái sử dụng trọn bộ lớp cha {@link AbstractGeoMythicBoss},
 * {@link com.abyssalrpg.api.BossHealthHolder} + {@link com.abyssalrpg.api.BossDefenseHolder}).
 *
 * =========================================================================
 * 1. DANH SÁCH HẰNG SỐ BONE BẮT BUỘC CHO MODEL BLOCKBENCH (.bbmodel -> GeckoLib Bedrock):
 *    - Gốc & Thân    : `root`, `body`, `pelvis`, `torso`, `chest`, `spikes_back`
 *    - Đầu & Sừng    : `head`, `jaw`, `horn_l`, `horn_r`, `mandible_l`, `mandible_r`
 *    - Tay trái      : `arm_l`, `arm_l_upper`, `arm_l_lower`, `hand_l`
 *    - Tay phải/Kiếm : `arm_r`, `arm_r_upper`, `arm_r_lower`, `hand_r`, `weapon` (con của `hand_r`), `sword_tip`
 *    - Chân thú      : `leg_l` (`thigh_l`, `shin_l`, `foot_l`), `leg_r` (`thigh_r`, `shin_r`, `foot_r`)
 *    - Phụ trợ       : `tail`
 *
 * 2. QUY ƯỚC KÍCH THƯỚC GẤP 3 LẦN BOSS CŨ:
 *    - Mốc cũ (Leviathan / Spirit Hydra / Cerberus cơ sở): `SCALE = 2.5f`, chiều cao mục tiêu `6.5` block.
 *    - Nightmare Knight: `SCALE = 3.0f * 2.5f = 7.5f` (đọc từ `NightmareKnightConfig.get().scale`).
 *    - Model gốc `nightmare_knight.geo.json` cao `41.6 px = 2.6` block -> `2.6 * 7.5 = 19.5` block (`= 3 * 6.5`!).
 *    - Thân chính dùng collision box vừa phải (`3.8 x 6.0` block, `stepHeight = 3.8`) để không làm lag chunk / kẹt địa hình;
 *      7 thực thể proxy multipart (`NightmareHitboxPartEntity`) bám theo `head`, `chest`, `pelvis`, `arm_l`, `arm_r`,
 *      `leg_l`, `leg_r` mỗi tick chuyển tiếp toàn bộ sát thương (cận chiến, cung tên, trident, projectile mod khác) về Boss.
 *
 * 3. CHỈ SỐ & PHÒNG THỦ TÙY CHỈNH:
 *    - Phase 1: HP = `4000`, Defense = `40` (vượt trần vanilla 30 -> dùng `BossDefenseHolder` + override `getDamageAfterArmorAbsorb`),
 *      `maxDamageReduction = 0.70`, `minDamagePerHit = 0.5`, KB Resist `1.0`, miễn nhiễm lửa/nước/rơi, không despawn.
 *    - Phase 2 "Cuồng bạo" (cận tử <= 5% HP hoặc <= 1 HP): 6.0s bất tử khóa AI/góc xoay, hồi đầy lên `7200 HP`,
 *      Defense `40 -> 72`, Damage `x1.8`, MoveSpeed `x1.8`, TurnSpeed `x1.3`, Cooldown `/1.8`, telegraph >= 0.5s.
 * =========================================================================
 */
public class NightmareKnightBossEntity extends AbstractGeoMythicBoss {
    /** Mốc SCALE cũ của Leviathan / Spirit Hydra (`2.5f`, chiều cao mục tiêu `6.5` block). */
    public static final float PREVIOUS_BOSS_BENCHMARK_SCALE = 2.5f;
    /** SCALE tuyến tính gấp 3 lần boss cũ: `3 * 2.5 = 7.5f` (chiều cao thực tế ~19.5 block). */
    public static final float SCALE = 3.0f * PREVIOUS_BOSS_BENCHMARK_SCALE;
    public static final float NATIVE_HEIGHT_PX = 41.6f;

    /** Collision box chính vừa phải giúp di chuyển nhẹ, không kẹt cây/tường 1-2 block (multipart phủ phần còn lại). */
    public static final float CORE_COLLISION_WIDTH = 3.8f;
    public static final float CORE_COLLISION_HEIGHT = 6.0f;
    public static final float CORE_EYE_HEIGHT = 16.5f;

    // --- Toàn bộ hằng số tên bone theo mục 1 của spec ---
    public static final String ROOT_BONE = "root";
    public static final String BODY_BONE = "body";
    public static final String PELVIS_BONE = "pelvis";
    public static final String TORSO_BONE = "torso";
    public static final String CHEST_BONE = "chest";
    public static final String SPIKES_BACK_BONE = "spikes_back";
    public static final String HEAD_BONE = "head";
    public static final String JAW_BONE = "jaw";
    public static final String HORN_L_BONE = "horn_l";
    public static final String HORN_R_BONE = "horn_r";
    public static final String MANDIBLE_L_BONE = "mandible_l";
    public static final String MANDIBLE_R_BONE = "mandible_r";
    public static final String ARM_L_BONE = "arm_l";
    public static final String ARM_L_UPPER_BONE = "arm_l_upper";
    public static final String ARM_L_LOWER_BONE = "arm_l_lower";
    public static final String HAND_L_BONE = "hand_l";
    public static final String ARM_R_BONE = "arm_r";
    public static final String ARM_R_UPPER_BONE = "arm_r_upper";
    public static final String ARM_R_LOWER_BONE = "arm_r_lower";
    public static final String HAND_R_BONE = "hand_r";
    public static final String WEAPON_BONE = "weapon";
    public static final String SWORD_TIP_BONE = "sword_tip";
    public static final String LEG_L_BONE = "leg_l";
    public static final String THIGH_L_BONE = "thigh_l";
    public static final String KNEE_SPUR_L_BONE = "knee_spur_l";
    public static final String SHIN_L_BONE = "shin_l";
    public static final String ANKLE_HOCK_L_BONE = "ankle_hock_l";
    public static final String FOOT_L_BONE = "foot_l";
    public static final String LEG_R_BONE = "leg_r";
    public static final String THIGH_R_BONE = "thigh_r";
    public static final String KNEE_SPUR_R_BONE = "knee_spur_r";
    public static final String SHIN_R_BONE = "shin_r";
    public static final String ANKLE_HOCK_R_BONE = "ankle_hock_r";
    public static final String FOOT_R_BONE = "foot_r";
    public static final String TAIL_BONE = "tail";
    // --- Bone mở rộng cho phiên bản Hiệp Sĩ Quỷ Có Cánh v3 (cánh 3 đốt + áo choàng 3 tầng & 2 vạt + khố rách) ---
    public static final String WING_L_BONE = "wing_l";
    public static final String WING_L_UPPER_BONE = "wing_l_upper";
    public static final String WING_L_MID_BONE = "wing_l_mid";
    public static final String WING_L_TIP_BONE = "wing_l_tip";
    public static final String WING_R_BONE = "wing_r";
    public static final String WING_R_UPPER_BONE = "wing_r_upper";
    public static final String WING_R_MID_BONE = "wing_r_mid";
    public static final String WING_R_TIP_BONE = "wing_r_tip";
    public static final String CAPE_BONE = "cape";
    public static final String CAPE_UPPER_BONE = "cape_upper";
    public static final String CAPE_MID_BONE = "cape_mid";
    public static final String CAPE_LOWER_BONE = "cape_lower";
    public static final String CAPE_L_BONE = "cape_l";
    public static final String CAPE_R_BONE = "cape_r";
    public static final String TABARD_BONE = "tabard";

    // --- Màu VFX ---
    public static final int COLOR_NIGHTMARE_CRIMSON = 0xD91628;
    public static final int COLOR_OBSIDIAN_BLOOD = 0x7A0918;
    public static final int COLOR_ENRAGED_FLAME = 0xFF3A1A;
    public static final int COLOR_BONE_ASH = 0xC8B8A6;

    // --- EntityAttributeModifier Phase 2 (ID cố định, idempotent) ---
    private static final ResourceLocation PHASE2_DAMAGE_MOD_ID = AbyssalRpgMod.id("nightmare_phase2_damage");
    private static final ResourceLocation PHASE2_SPEED_MOD_ID = AbyssalRpgMod.id("nightmare_phase2_speed");

    // --- Định nghĩa 7 phần tử Multipart Hitbox (khớp tọa độ pivot 1/16 block trong nightmare_knight.geo.json) ---
    public record PartSpec(String name, float modelX, float modelY, float modelZ, float baseWidth, float baseHeight) {}

    public static final PartSpec[] MULTIPART_SPECS = {
            new PartSpec(HEAD_BONE,   0.0f, 34.0f, -1.5f, 3.8f, 4.2f),
            new PartSpec(CHEST_BONE,  0.0f, 25.0f, -0.5f, 6.2f, 5.4f),
            new PartSpec(PELVIS_BONE, 0.0f, 16.5f,  0.0f, 4.4f, 4.4f),
            new PartSpec(ARM_L_BONE,  9.6f, 20.0f, -1.5f, 3.2f, 5.8f),
            new PartSpec(ARM_R_BONE, -9.6f, 20.0f, -2.5f, 3.4f, 6.0f),
            new PartSpec(LEG_L_BONE,  6.4f,  0.0f,  0.0f, 3.3f, 6.4f),
            new PartSpec(LEG_R_BONE, -6.4f,  0.0f,  0.0f, 3.3f, 6.4f)
    };

    // --- 17 Animation GeckoLib theo mục 1 ---
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.nightmare_knight.idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("animation.nightmare_knight.walk");
    private static final RawAnimation ANIM_DEATH = RawAnimation.begin().thenPlayAndHold("animation.nightmare_knight.death");

    private static final RawAnimation ANIM_SLASH = RawAnimation.begin().thenPlay("animation.nightmare_knight.slash");
    private static final RawAnimation ANIM_OVERHEAD_SMASH = RawAnimation.begin().thenPlay("animation.nightmare_knight.overhead_smash");
    private static final RawAnimation ANIM_THRUST = RawAnimation.begin().thenPlay("animation.nightmare_knight.thrust");
    private static final RawAnimation ANIM_CLAW_SWIPE = RawAnimation.begin().thenPlay("animation.nightmare_knight.claw_swipe");
    private static final RawAnimation ANIM_LEAP = RawAnimation.begin().thenPlay("animation.nightmare_knight.leap");
    private static final RawAnimation ANIM_SPIKE_ERUPTION = RawAnimation.begin().thenPlay("animation.nightmare_knight.spike_eruption");
    private static final RawAnimation ANIM_SWORD_WAVE = RawAnimation.begin().thenPlay("animation.nightmare_knight.sword_wave");
    private static final RawAnimation ANIM_CAGE = RawAnimation.begin().thenPlay("animation.nightmare_knight.cage");
    private static final RawAnimation ANIM_COMBO = RawAnimation.begin().thenPlay("animation.nightmare_knight.combo");
    private static final RawAnimation ANIM_SPIN_SLASH = RawAnimation.begin().thenPlay("animation.nightmare_knight.spin_slash");
    private static final RawAnimation ANIM_STORM = RawAnimation.begin().thenPlay("animation.nightmare_knight.storm");
    private static final RawAnimation ANIM_REALM = RawAnimation.begin().thenPlay("animation.nightmare_knight.realm");
    private static final RawAnimation ANIM_ULTIMATE = RawAnimation.begin().thenPlay("animation.nightmare_knight.ultimate");
    private static final RawAnimation ANIM_PHASE_CHANGE = RawAnimation.begin().thenPlay("animation.nightmare_knight.phase_change");

    public enum SkillState {
        IDLE(0),
        GREATSWORD_SLASH(1),
        OVERHEAD_SMASH(2),
        THRUST(3),
        CLAW_SWIPE(4),
        LEAP_SMASH(5),
        BONE_SPIKE_ERUPTION(6),
        SWORD_WAVE(7),
        BONE_CAGE(8),
        NIGHTMARE_COMBO(9),
        SPIKE_STORM(10),
        NIGHTMARE_REALM(11),
        SPIN_SLASH(12),
        FLAME_SWORD_WAVE(13),
        NIGHTMARE_DESCENT(14),
        PHASE_CHANGE(15),
        DEATH(16);

        public final int id;
        SkillState(int id) { this.id = id; }
        public static SkillState byId(int id) {
            for (SkillState s : values()) {
                if (s.id == id) return s;
            }
            return IDLE;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_SKILL_STATE =
            SynchedEntityData.defineId(NightmareKnightBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HEAD_ANIM_LOCKED =
            SynchedEntityData.defineId(NightmareKnightBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_REALM_ACTIVE =
            SynchedEntityData.defineId(NightmareKnightBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ACTION_NONCE =
            SynchedEntityData.defineId(NightmareKnightBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ACTION_ANIM_SPEED =
            SynchedEntityData.defineId(NightmareKnightBossEntity.class, EntityDataSerializers.FLOAT);

    private static volatile boolean debugLoggingEnabled = false;
    private int lastClientActionNonce = -1;

    private final List<NightmareHitboxPartEntity> hitboxParts = new ArrayList<>();
    private final EnumMap<SkillState, Integer> skillCooldowns = new EnumMap<>(SkillState.class);
    private final List<FlameTrailPatch> flamePatches = new ArrayList<>();
    private final Map<UUID, Integer> realmExposureTicks = new HashMap<>();

    private SkillState lastSkill1 = SkillState.IDLE;
    private SkillState lastSkill2 = SkillState.IDLE;
    private int globalSkillRestTicks = 30;
    private boolean phase2StatsApplied = false;
    private int outOfCombatTicks = 0;
    private int realmActiveTicks = 0;

    private long lastPartHitGameTick = -1L;
    @Nullable
    private UUID lastPartHitAttacker = null;

    private Vec3 lastProgressPos = Vec3.ZERO;
    private int stuckTicks = 0;
    private int stuckRecoveryCooldown = 0;
    private int walkStrideTicks = 0;

    private volatile Vec3 clientSwordTipPos = Vec3.ZERO;
    private float clientTrackedHeadYawDeg = 0.0f;
    private float clientTrackedHeadPitchDeg = 0.0f;

    private static final class FlameTrailPatch {
        final Vec3 pos;
        final float radius;
        final float damage;
        int ticksLeft;
        FlameTrailPatch(Vec3 pos, float radius, float damage, int ticksLeft) {
            this.pos = pos;
            this.radius = radius;
            this.damage = damage;
            this.ticksLeft = ticksLeft;
        }
    }

    @SuppressWarnings("this-escape")
    public NightmareKnightBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.PURPLE);
        this.moveControl = new GiantSteeringMoveControl(this);
        this.xpReward = 2500;
        this.noCulling = true;
        applyConfiguredPhase1BaseStats();
        this.refreshDimensions();
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        NightmareKnightConfig cfg = NightmareKnightConfig.get();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, VANILLA_SAFE_MAX_HEALTH_ATTR)
                .add(Attributes.ATTACK_DAMAGE, cfg.greatswordSlashDamage)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0)
                .add(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed)
                .add(Attributes.FOLLOW_RANGE, cfg.detectionRadius)
                .add(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance)
                .add(Attributes.STEP_HEIGHT, cfg.stepHeight)
                .add(Attributes.SCALE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKILL_STATE, SkillState.IDLE.id);
        builder.define(DATA_HEAD_ANIM_LOCKED, false);
        builder.define(DATA_REALM_ACTIVE, false);
        builder.define(DATA_ACTION_NONCE, 0);
        builder.define(DATA_ACTION_ANIM_SPEED, 1.0f);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new BossLockGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        NightmareSkillGoals.registerAll(this, this.goalSelector);
        this.goalSelector.addGoal(5, new SafeLookAtPlayerGoal(this, 64.0f));
        this.goalSelector.addGoal(6, new SafeRandomLookGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    private void applyConfiguredPhase1BaseStats() {
        NightmareKnightConfig cfg = NightmareKnightConfig.get();
        setAttr(Attributes.MAX_HEALTH, VANILLA_SAFE_MAX_HEALTH_ATTR);
        setAttr(Attributes.ATTACK_DAMAGE, cfg.greatswordSlashDamage);
        setAttr(Attributes.ARMOR, 0.0);
        setAttr(Attributes.ARMOR_TOUGHNESS, 0.0);
        setAttr(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed);
        setAttr(Attributes.FOLLOW_RANGE, cfg.detectionRadius);
        setAttr(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance);
        setAttr(Attributes.STEP_HEIGHT, cfg.stepHeight);
        setAttr(Attributes.SCALE, 1.0);

        if (!this.hasEnteredPhase2) {
            setBossDefense(cfg.defense);
            initializeBossHealth((float) cfg.maxHealth);
        }
    }

    @Override
    public void configureForFloor(int floor, BlockPos arenaCenter, boolean dungeonBoss) {
        this.configured = true;
        this.floor = Math.max(1, Math.min(18, floor));
        this.arenaCenter = arenaCenter;
        this.dungeonBoss = dungeonBoss;

        applyConfiguredPhase1BaseStats();
        this.xpReward = 2500;
        Component title = getStandaloneBossTitle();
        this.setCustomName(title);
        this.setCustomNameVisible(true);
        this.bossEvent.setName(title);
        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        this.triggerSummonEntrance();
    }

    @Override
    protected Component getStandaloneBossTitle() {
        return Component.translatable("entity.abyssalrpg.nightmare_knight")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
    }

    @Override
    public float getBossToughness() {
        return NightmareKnightConfig.get().bossToughness;
    }

    @Override
    public float getMaxDamageReduction() {
        return NightmareKnightConfig.get().maxDamageReduction;
    }

    @Override
    public float getMinDamagePerHit() {
        return NightmareKnightConfig.get().minDamagePerHit;
    }

    public float getConfiguredScale() {
        return NightmareKnightConfig.get().scale;
    }

    public float getScaleRatio() {
        return getConfiguredScale() / SCALE;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        float ratio = getScaleRatio();
        return EntityDimensions.scalable(CORE_COLLISION_WIDTH * ratio, CORE_COLLISION_HEIGHT * ratio)
                .withEyeHeight(CORE_EYE_HEIGHT * ratio);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        float ratio = getScaleRatio();
        // Bao trọn cả sải cánh quỷ khổng lồ (~31 block) và đại kiếm
        double halfW = 19.0 * ratio;
        double topH = 28.0 * ratio;
        return new AABB(
                this.getX() - halfW, this.getY() - 2.0, this.getZ() - halfW,
                this.getX() + halfW, this.getY() + topH, this.getZ() + halfW
        );
    }

    /** Tắt khói ambient rời rạc kế thừa từ lớp cha để giảm lag và bỏ hạt lơ lửng. */
    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        // Không sinh particle nền thừa trên server
    }

    public int getActionNonce() {
        return this.entityData.get(DATA_ACTION_NONCE);
    }

    public float getSyncedActionAnimSpeed() {
        float s = this.entityData.get(DATA_ACTION_ANIM_SPEED);
        return Float.isFinite(s) && s >= 0.5f ? s : 1.0f;
    }

    /**
     * Đồng bộ tức thời (0-tick blend) bắt đầu 1 đòn đánh sang Client cùng đúng tốc độ `animSpeed`.
     */
    public void beginSyncedActionAnimation(SkillState state, float animSpeed) {
        float safeSpeed = Mth.clamp(animSpeed, 0.5f, 2.0f);
        this.entityData.set(DATA_ACTION_ANIM_SPEED, safeSpeed);
        this.entityData.set(DATA_SKILL_STATE, state.id);
        this.entityData.set(DATA_ACTION_NONCE, this.entityData.get(DATA_ACTION_NONCE) + 1);
    }

    public void logAttackTiming(String skillName, int swingIdx, double animTick, int rawServerTick,
                                float animSpeed, int winStart, int winEnd, int impactTick, String eventTag) {
        if (!isDebugLoggingEnabled()) return;
        double expectedSec = impactTick / 20.0;
        double actualAnimSec = animTick / 20.0;
        double deltaTicks = animTick - impactTick;
        AbyssalRpgMod.LOGGER.info(
                "[NightmareKnight#{} TIMING {}] skill={} swing={} actionTick={} (rawTick={}, speed={}x) expectedAnimSec={}s actualAnimSec={}s hitWindow=[{}..{}] deltaTicks={}",
                this.getId(), eventTag, skillName, swingIdx,
                String.format("%.2f", animTick), rawServerTick, String.format("%.2f", animSpeed),
                String.format("%.3f", expectedSec), String.format("%.3f", actualAnimSec),
                winStart, winEnd, String.format("%.2f", deltaTicks)
        );
    }

    public SkillState getSkillState() {
        return SkillState.byId(this.entityData.get(DATA_SKILL_STATE));
    }

    public void setSkillState(SkillState state) {
        this.entityData.set(DATA_SKILL_STATE, state.id);
    }

    public boolean isHeadLockedByAnimation() {
        if (this.isRotationLocked() || !this.isAlive()) return true;
        if (this.entityData.get(DATA_HEAD_ANIM_LOCKED)) return true;
        SkillState st = getSkillState();
        return st == SkillState.PHASE_CHANGE
                || st == SkillState.DEATH
                || st == SkillState.SPIN_SLASH
                || st == SkillState.NIGHTMARE_DESCENT
                || st == SkillState.OVERHEAD_SMASH
                || st == SkillState.LEAP_SMASH;
    }

    public void setHeadAnimationLocked(boolean locked) {
        this.entityData.set(DATA_HEAD_ANIM_LOCKED, locked);
    }

    public boolean isRealmActive() {
        return this.entityData.get(DATA_REALM_ACTIVE);
    }

    public static boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled || NightmareKnightConfig.get().debugRotationLogs;
    }

    public static void setDebugLoggingEnabled(boolean enabled) {
        debugLoggingEnabled = enabled;
    }

    public int getGlobalSkillRestTicks() {
        return this.globalSkillRestTicks;
    }

    public void setGlobalSkillRestTicks(int ticks) {
        this.globalSkillRestTicks = Math.max(0, ticks);
    }

    public int getSkillCooldown(SkillState state) {
        return this.skillCooldowns.getOrDefault(state, 0);
    }

    public boolean isSkillReady(SkillState state) {
        return getSkillCooldown(state) <= 0;
    }

    public void setSkillCooldown(SkillState state, int phase1Ticks) {
        int ticks;
        if (state == SkillState.NIGHTMARE_DESCENT) {
            ticks = Math.max(400, phase1Ticks);
        } else if (this.phase >= 2 || this.hasEnteredPhase2) {
            ticks = Math.max(25, (int) Math.round(phase1Ticks / NightmareKnightConfig.get().phase2StatMultiplier));
        } else {
            ticks = Math.max(30, phase1Ticks);
        }
        this.skillCooldowns.put(state, ticks);
    }

    public boolean isBlockedByRepeatLimit(SkillState candidate) {
        return candidate != SkillState.IDLE && candidate == this.lastSkill1 && candidate == this.lastSkill2;
    }

    public void recordUsedSkill(SkillState skill) {
        this.lastSkill2 = this.lastSkill1;
        this.lastSkill1 = skill;
    }

    public float scaledDamage(float phase1Damage) {
        if (this.phase >= 2 || this.hasEnteredPhase2) {
            return (float) (phase1Damage * NightmareKnightConfig.get().phase2StatMultiplier);
        }
        return phase1Damage;
    }

    public int scaledTelegraph(int phase1Ticks) {
        NightmareKnightConfig cfg = NightmareKnightConfig.get();
        if (this.phase >= 2 || this.hasEnteredPhase2) {
            return Math.max(cfg.minTelegraphTicks, Math.round(phase1Ticks / 1.35f));
        }
        return Math.max(16, phase1Ticks);
    }

    public void finishSkill(int restTicks) {
        setHeadAnimationLocked(false);
        this.entityData.set(DATA_ACTION_ANIM_SPEED, 1.0f);
        setSkillState(SkillState.IDLE);
        setGlobalSkillRestTicks(restTicks);
        snapSyncPrevAngles();
    }

    public void playActionAnim(String animKey) {
        this.triggerAnim("action", animKey);
    }

    public void playScaledSound(ServerLevel level, SoundEvent sound, float baseVolume, float pitch) {
        float vol = baseVolume * NightmareKnightConfig.get().soundRangeMultiplier;
        level.playSound(null, this.getX(), this.getY() + 6.0, this.getZ(), sound, SoundSource.HOSTILE, vol, pitch);
    }

    public void triggerScaledShake(ServerLevel level, double baseRadius, float intensity, int durationTicks) {
        double r = baseRadius * NightmareKnightConfig.get().screenShakeRadiusMultiplier;
        ModNetworking.sendShakeNearby(level, this.position(), r, intensity, durationTicks);
    }

    public Vec3 getSwordTipWorldPos() {
        if (this.level().isClientSide && this.clientSwordTipPos.lengthSqr() > 1.0) {
            return this.clientSwordTipPos;
        }
        return modelPointToWorld(-17.22f, 1.58f, -32.59f, getConfiguredScale());
    }

    public void updateClientSwordTipPos(double x, double y, double z) {
        this.clientSwordTipPos = new Vec3(x, y, z);
    }

    public void stepClientHeadTracking(float targetYawDeg, float targetPitchDeg) {
        float maxStep = 3.6f;
        this.clientTrackedHeadYawDeg = Mth.clamp(
                Mth.approachDegrees(this.clientTrackedHeadYawDeg, targetYawDeg, maxStep), -42.0f, 42.0f);
        this.clientTrackedHeadPitchDeg = Mth.clamp(
                Mth.approachDegrees(this.clientTrackedHeadPitchDeg, targetPitchDeg, maxStep), -28.0f, 28.0f);
    }

    public float getClientTrackedHeadYawDeg() {
        return this.clientTrackedHeadYawDeg;
    }

    public float getClientTrackedHeadPitchDeg() {
        return this.clientTrackedHeadPitchDeg;
    }

    public List<NightmareHitboxPartEntity> getActiveHitboxParts() {
        return this.hitboxParts;
    }

    private void ensureAndSyncHitboxParts(ServerLevel level) {
        float scale = getConfiguredScale();
        float ratio = getScaleRatio();

        if (this.hitboxParts.size() != MULTIPART_SPECS.length) {
            discardAllHitboxParts();
            for (PartSpec spec : MULTIPART_SPECS) {
                NightmareHitboxPartEntity part = NightmareHitboxPartEntity.create(
                        level, this, spec.name(), spec.baseWidth() * ratio, spec.baseHeight() * ratio);
                this.hitboxParts.add(part);
            }
        }

        for (int i = 0; i < MULTIPART_SPECS.length; i++) {
            PartSpec spec = MULTIPART_SPECS[i];
            NightmareHitboxPartEntity part = this.hitboxParts.get(i);
            if (part == null || part.isRemoved()) {
                part = NightmareHitboxPartEntity.create(
                        level, this, spec.name(), spec.baseWidth() * ratio, spec.baseHeight() * ratio);
                this.hitboxParts.set(i, part);
            }
            part.setPartDimensions(spec.baseWidth() * ratio, spec.baseHeight() * ratio);
            Vec3 wp = modelPointToWorld(spec.modelX(), spec.modelY(), spec.modelZ(), scale);
            part.syncPositionFromBone(wp.x, wp.y, wp.z, this.yBodyRot);
        }
    }

    private void discardAllHitboxParts() {
        for (NightmareHitboxPartEntity part : this.hitboxParts) {
            if (part != null && !part.isRemoved()) {
                part.discard();
            }
        }
        this.hitboxParts.clear();
    }

    public boolean hurtFromPart(NightmareHitboxPartEntity part, DamageSource source, float amount) {
        if (this.level().isClientSide || !this.isAlive()) {
            return false;
        }
        long now = this.level().getGameTime();
        UUID attackerId = source.getEntity() != null ? source.getEntity().getUUID() : null;
        if (now == this.lastPartHitGameTick && attackerId != null && attackerId.equals(this.lastPartHitAttacker)) {
            return false;
        }
        this.lastPartHitGameTick = now;
        this.lastPartHitAttacker = attackerId;

        if (isDebugLoggingEnabled() && this.level() instanceof ServerLevel) {
            AbyssalRpgMod.LOGGER.info("[NightmareKnight#{} MULTIPART] Hit part='{}' rawDmg={} source={} attacker={}",
                    this.getId(), part.getPartName(), String.format("%.2f", amount), source.getMsgId(),
                    source.getEntity() != null ? source.getEntity().getName().getString() : "none");
        }
        return this.hurt(source, amount);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isSpawningIn() || this.isTransitioningPhase()) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }

        if (!this.level().isClientSide && !this.hasEnteredPhase2 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float threshold = Math.max(1.0f, (float) (this.getMaxHealth() * NightmareKnightConfig.get().phase2TriggerHpRatio));
            float estDmg = estimatePostArmorDamage(source, amount);
            if (this.getHealth() - estDmg <= threshold || this.getHealth() <= threshold) {
                triggerPhase2Transition(threshold);
                return false;
            }
        }

        boolean ok = super.hurt(source, amount);
        if (ok) {
            this.outOfCombatTicks = 0;
        }
        return ok;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // Kháng knockback 100%
    }

    private void triggerPhase2Transition(float minFloorHp) {
        NightmareKnightConfig cfg = NightmareKnightConfig.get();
        this.hasEnteredPhase2 = true;
        this.entityData.set(DATA_HAS_ENTERED_PHASE2, true);
        this.transitionStartHp = Math.max(1.0f, Math.min(this.getHealth(), minFloorHp));
        this.setHealth(this.transitionStartHp);

        this.transitionTotalTicks = cfg.phaseTransitionTicks;
        this.entityData.set(DATA_PHASE_TRANS_TICKS, this.transitionTotalTicks);
        this.setHeadAnimationLocked(true);

        lockCurrentRotation();
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.beginSyncedActionAnimation(SkillState.PHASE_CHANGE, 1.0f);

        if (this.level() instanceof ServerLevel sl) {
            playScaledSound(sl, ModSounds.NIGHTMARE_PHASE_CHANGE, 2.2f, 0.72f);
            triggerScaledShake(sl, 42.0, 2.6f, 45);
            ShockwaveRingEntity.spawn(sl, this.position(), 26.0f, COLOR_NIGHTMARE_CRIMSON, 22);
            announceToNearbyWithRadius(sl, Component.literal(
                    "☠ NIGHTMARE KNIGHT gầm vang xé toạc chiến giáp — CUỒNG BẠO PHASE 2 THỨC TỈNH!"
            ).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), cfg.bossBarRadius);
        }
    }

    private void tickPhaseTransition(ServerLevel level) {
        int ticksLeft = this.entityData.get(DATA_PHASE_TRANS_TICKS);
        if (ticksLeft <= 0) {
            return;
        }
        NightmareKnightConfig cfg = NightmareKnightConfig.get();
        this.setDeltaMovement(Vec3.ZERO);
        this.getNavigation().stop();
        this.normalizeAnglesAndSyncPrev();

        float targetP2MaxHp = (float) (cfg.maxHealth * cfg.phase2StatMultiplier);
        if (getBossMaxHealth() < targetP2MaxHp - 1.0f) {
            setBossMaxHealth(targetP2MaxHp);
        }

        int elapsed = this.transitionTotalTicks - ticksLeft;
        float progress = Mth.clamp((float) elapsed / Math.max(1, this.transitionTotalTicks), 0.0f, 1.0f);

        float desiredHp = Mth.lerp(progress, this.transitionStartHp, targetP2MaxHp);
        if (desiredHp > this.getHealth()) {
            this.setHealth(Math.min(targetP2MaxHp, desiredHp));
        }

        int crackTick = Math.round(this.transitionTotalTicks * 0.35f);
        if (elapsed == crackTick) {
            this.entityData.set(DATA_ENRAGED_VISUAL, true);
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
            playScaledSound(level, ModSounds.NIGHTMARE_ROAR, 2.4f, 0.65f);
            triggerScaledShake(level, 48.0, 3.0f, 35);
            ShockwaveRingEntity.spawn(level, this.position(), 32.0f, COLOR_ENRAGED_FLAME, 20);
            ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_PHASE_BURST,
                    this.position(), this.getLookAngle(), 10.0f, true);
        }

        if (isDebugLoggingEnabled()) {
            float sampleDmg = 15.0f;
            float effDefPts = computeEffectiveDefensePoints(sampleDmg);
            float redPct = computeEffectiveDamageReduction(sampleDmg) * 100.0f;
            AbyssalRpgMod.LOGGER.info(
                    "[NightmareKnight#{} PHASE_TRANS rem={}] yaw={} (prev={}) body={} (prev={}) head={} (prev={}) netHead={} | rawDef={} effDefPts={} reduction={}% HP={}/{}",
                    this.getId(), ticksLeft,
                    String.format("%.2f", this.getYRot()), String.format("%.2f", this.yRotO),
                    String.format("%.2f", this.yBodyRot), String.format("%.2f", this.yBodyRotO),
                    String.format("%.2f", this.yHeadRot), String.format("%.2f", this.yHeadRotO),
                    String.format("%.2f", Mth.wrapDegrees(this.yHeadRot - this.yBodyRot)),
                    String.format("%.1f", getBossDefense()), String.format("%.2f", effDefPts),
                    String.format("%.1f", redPct),
                    String.format("%.1f", this.getHealth()), String.format("%.1f", this.getMaxHealth())
            );
        }

        ticksLeft--;
        this.entityData.set(DATA_PHASE_TRANS_TICKS, ticksLeft);
        if (ticksLeft <= 0) {
            finalizePhase2Stats(true);
            this.setSkillState(SkillState.IDLE);
            this.setHeadAnimationLocked(false);
            this.setGlobalSkillRestTicks(24);
            this.snapSyncPrevAngles();
            playScaledSound(level, ModSounds.NIGHTMARE_SLAM, 2.2f, 0.70f);
            ShockwaveRingEntity.spawn(level, this.position(), 34.0f, COLOR_NIGHTMARE_CRIMSON, 18);
        }
    }

    public void finalizePhase2Stats(boolean healToFull) {
        NightmareKnightConfig cfg = NightmareKnightConfig.get();
        syncPhaseData(2);
        this.hasEnteredPhase2 = true;
        this.phase2StatsApplied = true;
        this.entityData.set(DATA_HAS_ENTERED_PHASE2, true);
        this.entityData.set(DATA_ENRAGED_VISUAL, true);
        this.bossEvent.setColor(BossEvent.BossBarColor.RED);

        float p2MaxHp = (float) (cfg.maxHealth * cfg.phase2StatMultiplier);
        setBossMaxHealth(p2MaxHp);
        if (healToFull) {
            this.setHealth(p2MaxHp);
        }

        float p2Def = (float) (cfg.defense * cfg.phase2StatMultiplier);
        setBossDefense(p2Def);

        double addMult = Math.max(0.0, cfg.phase2StatMultiplier - 1.0);
        applyPermanentMultiplier(Attributes.ATTACK_DAMAGE, PHASE2_DAMAGE_MOD_ID, addMult);
        applyPermanentMultiplier(Attributes.MOVEMENT_SPEED, PHASE2_SPEED_MOD_ID, addMult);
    }

    public void addFlameTrailPatch(Vec3 pos, float radius, float damage, int durationTicks) {
        int cap = NightmareKnightConfig.get().reducedVfxMode ? 8 : 14;
        while (this.flamePatches.size() >= cap) {
            this.flamePatches.remove(0);
        }
        this.flamePatches.add(new FlameTrailPatch(pos, radius, damage, durationTicks));
    }

    public void activateNightmareRealm(int durationTicks) {
        this.realmActiveTicks = Math.max(this.realmActiveTicks, durationTicks);
        this.entityData.set(DATA_REALM_ACTIVE, true);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel sl)) return;
        NightmareKnightConfig cfg = NightmareKnightConfig.get();

        syncBossBarPlayers(sl, cfg.bossBarRadius);

        if (this.isSpawningIn()) {
            ensureAndSyncHitboxParts(sl);
            return;
        }

        if (this.globalSkillRestTicks > 0) this.globalSkillRestTicks--;
        if (this.stuckRecoveryCooldown > 0) this.stuckRecoveryCooldown--;
        if (!this.skillCooldowns.isEmpty()) {
            for (Map.Entry<SkillState, Integer> e : this.skillCooldowns.entrySet()) {
                if (e.getValue() > 0) e.setValue(e.getValue() - 1);
            }
        }

        if (this.isTransitioningPhase()) {
            tickPhaseTransition(sl);
            ensureAndSyncHitboxParts(sl);
            return;
        }

        ensureAndSyncHitboxParts(sl);
        tickFlamePatches(sl);
        tickNightmareRealm(sl, cfg);
        tickJaegerFootsteps(sl, cfg);

        if (cfg.terrainDamageEnabled && this.tickCount % 4 == 0 && this.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
            clearSoftBlocksInPath(sl);
        }

        this.outOfCombatTicks = tickOutOfCombatRegen(
                sl, this.outOfCombatTicks,
                cfg.outOfCombatHealRadius, cfg.outOfCombatHealDelayTicks, cfg.outOfCombatHealPerSecond
        );

        tickStuckRecovery(sl);
    }

    /**
     * Nhịp bước chân khổng lồ kiểu Pacific Rim khớp đúng chu kỳ `walk` 1.80s (36 ticks -> đặt chân ở tick 9 & 27):
     * phát âm thanh dậm đất trầm nặng + rung camera rất nhẹ (tắt được qua `footstepScreenShakeEnabled`), 0 particle bụi!
     */
    private void tickJaegerFootsteps(ServerLevel level, NightmareKnightConfig cfg) {
        boolean walking = this.isAlive() && this.onGround()
                && getSkillState() == SkillState.IDLE
                && this.getDeltaMovement().horizontalDistanceSqr() > 0.003;
        if (!walking) {
            this.walkStrideTicks = 0;
            return;
        }
        this.walkStrideTicks++;
        int phase = this.walkStrideTicks % 36;
        if (phase == 9 || phase == 27) {
            float pitch = (phase == 9) ? 0.42f : 0.46f;
            level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(),
                    ModSounds.NIGHTMARE_SLAM, SoundSource.HOSTILE, 0.85f * cfg.soundRangeMultiplier, pitch);
            if (cfg.footstepScreenShakeEnabled) {
                ModNetworking.sendShakeNearby(level, this.position(), 28.0, 0.32f, 4);
            }
        }
    }

    private void tickFlamePatches(ServerLevel level) {
        if (this.flamePatches.isEmpty()) return;
        Iterator<FlameTrailPatch> it = this.flamePatches.iterator();
        while (it.hasNext()) {
            FlameTrailPatch patch = it.next();
            patch.ticksLeft--;
            if (patch.ticksLeft <= 0) {
                it.remove();
                continue;
            }
            if (patch.ticksLeft % 14 == 0) {
                ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_FLAME_PATCH,
                        patch.pos, Vec3.ZERO, patch.radius, true);
            }
            if (patch.ticksLeft % 10 == 0) {
                AABB box = new AABB(
                        patch.pos.x - patch.radius, patch.pos.y - 0.5, patch.pos.z - patch.radius,
                        patch.pos.x + patch.radius, patch.pos.y + 2.2, patch.pos.z + patch.radius);
                for (Player p : level.getEntitiesOfClass(Player.class, box,
                        pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    p.hurt(level.damageSources().mobAttack(this), patch.damage);
                    p.igniteForSeconds(3);
                }
            }
        }
    }

    private void tickNightmareRealm(ServerLevel level, NightmareKnightConfig cfg) {
        if (this.realmActiveTicks <= 0) {
            if (isRealmActive()) {
                this.entityData.set(DATA_REALM_ACTIVE, false);
                this.realmExposureTicks.clear();
            }
            return;
        }
        this.realmActiveTicks--;
        double radius = cfg.nightmareRealmRadius;
        double radSq = radius * radius;

        // 1 gói tin gọn mỗi 30 tick (1.5s) thay vì gửi nhiều sendParticles mỗi 10 tick
        if (this.realmActiveTicks % 30 == 0) {
            ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_REALM_PULSE,
                    this.position(), Vec3.ZERO, (float) radius, true);
        }

        if (this.realmActiveTicks % 10 == 0) {
            boolean anyLingeringPlayer = false;
            for (ServerPlayer player : level.players()) {
                if (!player.isAlive() || player.isCreative() || player.isSpectator()) continue;
                if (player.distanceToSqr(this) <= radSq) {
                    if (cfg.nightmareRealmEffectsEnabled) {
                        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 65, 0, true, false, true));
                    }
                    int exposed = this.realmExposureTicks.merge(player.getUUID(), 10, Integer::sum);
                    if (exposed >= 60) {
                        anyLingeringPlayer = true;
                    }
                } else {
                    this.realmExposureTicks.remove(player.getUUID());
                }
            }
            if (anyLingeringPlayer && cfg.nightmareRealmBossHealPerSecond > 0.0f && this.getHealth() < this.getMaxHealth()) {
                this.heal(cfg.nightmareRealmBossHealPerSecond * 0.5f);
            }
        }
    }

    private void clearSoftBlocksInPath(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(1.2, 0.2, 1.2);
        int minX = Mth.floor(box.minX), maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY), maxY = Mth.floor(Math.min(box.maxY + 2.0, level.getMaxBuildHeight() - 1));
        int minZ = Mth.floor(box.minZ), maxZ = Mth.floor(box.maxZ);
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        int broken = 0;
        for (int y = minY; y <= maxY && broken < 12; y++) {
            for (int x = minX; x <= maxX && broken < 12; x++) {
                for (int z = minZ; z <= maxZ && broken < 12; z++) {
                    mpos.set(x, y, z);
                    BlockState st = level.getBlockState(mpos);
                    if (st.isAir()) continue;
                    if (st.is(BlockTags.LEAVES) || st.is(BlockTags.FLOWERS) || st.is(BlockTags.REPLACEABLE)
                            || st.is(BlockTags.SNOW) || st.is(BlockTags.CLIMBABLE)) {
                        level.destroyBlock(mpos, false, this);
                        broken++;
                    }
                }
            }
        }
    }

    private void tickStuckRecovery(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || getSkillState() != SkillState.IDLE || this.isRotationLocked()) {
            this.stuckTicks = 0;
            this.lastProgressPos = this.position();
            return;
        }
        double distToTarget = this.distanceTo(target);
        if (distToTarget <= 11.0 || distToTarget > NightmareKnightConfig.get().detectionRadius) {
            this.stuckTicks = 0;
            this.lastProgressPos = this.position();
            return;
        }

        if (this.position().distanceToSqr(this.lastProgressPos) >= 1.2) {
            this.stuckTicks = 0;
            this.lastProgressPos = this.position();
            return;
        }

        this.stuckTicks++;
        if (this.stuckTicks >= 60 && this.stuckRecoveryCooldown <= 0) {
            this.stuckTicks = 0;
            this.stuckRecoveryCooldown = 100;
            Vec3 toTarget = target.position().subtract(this.position());
            Vec3 horiz = new Vec3(toTarget.x, 0.0, toTarget.z);
            Vec3 dir = horiz.lengthSqr() > 1e-4 ? horiz.normalize() : new Vec3(0, 0, 1);

            if (distToTarget <= 26.0 && this.onGround()) {
                this.setDeltaMovement(dir.x * 1.35, 1.05, dir.z * 1.35);
                this.hurtMarked = true;
                playScaledSound(level, ModSounds.NIGHTMARE_LEAP, 1.6f, 0.78f);
            } else {
                double step = Math.min(16.0, Math.max(8.0, distToTarget - 9.0));
                Vec3 desired = this.position().add(dir.scale(step));
                Vec3 safe = findSafeLandingPosition(level, desired, this.position(), 8);
                if (safe.distanceToSqr(this.position()) > 4.0) {
                    this.teleportTo(safe.x, safe.y, safe.z);
                    facePositionImmediately(target.position());
                    ShockwaveRingEntity.spawn(level, safe, 10.0f, COLOR_OBSIDIAN_BLOOD, 12);
                    ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_SLAM_IMPACT,
                            safe, dir, 8.0f, isEnragedVisual());
                    playScaledSound(level, ModSounds.NIGHTMARE_REALM, 1.5f, 0.85f);
                }
            }
            this.lastProgressPos = this.position();
        }
    }

    @Override
    protected boolean shouldDropLoot() {
        // Bắt buộc CHỈ rơi loot (gồm Nightmare Core 5% & 6-10 Nightmare Armor Shard) khi đã qua Phase 2 thực sự!
        return this.hasEnteredPhase2 && super.shouldDropLoot();
    }

    @Override
    public void die(DamageSource source) {
        this.setSkillState(SkillState.DEATH);
        this.setHeadAnimationLocked(true);
        discardAllHitboxParts();
        this.flamePatches.clear();
        this.realmActiveTicks = 0;
        this.entityData.set(DATA_REALM_ACTIVE, false);
        if (this.level() instanceof ServerLevel sl) {
            playScaledSound(sl, ModSounds.NIGHTMARE_DEATH, 2.5f, 0.72f);
            triggerScaledShake(sl, 56.0, 2.8f, 40);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        discardAllHitboxParts();
        super.remove(reason);
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.NIGHTMARE_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.NIGHTMARE_DEATH; }

    @Override
    protected float getSoundVolume() {
        return 1.6f * NightmareKnightConfig.get().soundRangeMultiplier;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saveSharedGeoBossData(tag);
        tag.putBoolean("NightmarePhase2Applied", this.phase2StatsApplied);
        tag.putInt("NightmareRealmTicks", this.realmActiveTicks);
        tag.putInt("NightmareUltimateCd", getSkillCooldown(SkillState.NIGHTMARE_DESCENT));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadSharedGeoBossData(tag, null, null, null, null, null, null);
        this.phase2StatsApplied = tag.getBoolean("NightmarePhase2Applied");
        this.realmActiveTicks = Math.max(0, tag.getInt("NightmareRealmTicks"));
        this.entityData.set(DATA_REALM_ACTIVE, this.realmActiveTicks > 0);
        if (tag.contains("NightmareUltimateCd")) {
            this.skillCooldowns.put(SkillState.NIGHTMARE_DESCENT, Math.max(0, tag.getInt("NightmareUltimateCd")));
        }

        if (this.hasEnteredPhase2 && !this.isTransitioningPhase()) {
            float savedHp = this.getHealth();
            finalizePhase2Stats(false);
            this.setHealth(Mth.clamp(savedHp, 1.0f, this.getMaxHealth()));
        } else if (getBossDefense() <= 0.0f) {
            setBossDefense(NightmareKnightConfig.get().defense);
        }
        this.snapSyncPrevAngles();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, state -> {
            if (!this.isAlive() || getSkillState() == SkillState.DEATH) {
                return state.setAndContinue(ANIM_DEATH);
            }
            if (this.isTransitioningPhase() || getSkillState() != SkillState.IDLE) {
                return state.setAndContinue(ANIM_IDLE);
            }
            if (state.isMoving() || this.getDeltaMovement().horizontalDistanceSqr() > 0.0025) {
                return state.setAndContinue(ANIM_WALK);
            }
            return state.setAndContinue(ANIM_IDLE);
        }));

        // Controller tấn công transitionLength = 0 + đồng bộ trực tiếp theo DATA_SKILL_STATE & DATA_ACTION_NONCE
        // + dùng đúng 1 hệ số tốc độ `getSyncedActionAnimSpeed()` như Server -> độ lệch animation/sát thương = 0 tick!
        controllers.add(new AnimationController<>(this, "action", 0, state -> {
            SkillState skill = getSkillState();
            if (!this.isAlive() || skill == SkillState.IDLE || skill == SkillState.DEATH) {
                return PlayState.STOP;
            }
            int nonce = getActionNonce();
            if (nonce != this.lastClientActionNonce) {
                this.lastClientActionNonce = nonce;
                state.getController().forceAnimationReset();
            }
            RawAnimation anim = rawAnimationForSkill(skill);
            return anim != null ? state.setAndContinue(anim) : PlayState.STOP;
        })
                .setAnimationSpeedHandler(animatable -> (double) animatable.getSyncedActionAnimSpeed())
                .triggerableAnim("slash", ANIM_SLASH)
                .triggerableAnim("overhead_smash", ANIM_OVERHEAD_SMASH)
                .triggerableAnim("thrust", ANIM_THRUST)
                .triggerableAnim("claw_swipe", ANIM_CLAW_SWIPE)
                .triggerableAnim("leap", ANIM_LEAP)
                .triggerableAnim("spike_eruption", ANIM_SPIKE_ERUPTION)
                .triggerableAnim("sword_wave", ANIM_SWORD_WAVE)
                .triggerableAnim("cage", ANIM_CAGE)
                .triggerableAnim("combo", ANIM_COMBO)
                .triggerableAnim("spin_slash", ANIM_SPIN_SLASH)
                .triggerableAnim("storm", ANIM_STORM)
                .triggerableAnim("realm", ANIM_REALM)
                .triggerableAnim("ultimate", ANIM_ULTIMATE)
                .triggerableAnim("phase_change", ANIM_PHASE_CHANGE));
    }

    @Nullable
    private static RawAnimation rawAnimationForSkill(SkillState skill) {
        return switch (skill) {
            case GREATSWORD_SLASH -> ANIM_SLASH;
            case OVERHEAD_SMASH -> ANIM_OVERHEAD_SMASH;
            case THRUST -> ANIM_THRUST;
            case CLAW_SWIPE -> ANIM_CLAW_SWIPE;
            case LEAP_SMASH -> ANIM_LEAP;
            case BONE_SPIKE_ERUPTION -> ANIM_SPIKE_ERUPTION;
            case SWORD_WAVE, FLAME_SWORD_WAVE -> ANIM_SWORD_WAVE;
            case BONE_CAGE -> ANIM_CAGE;
            case NIGHTMARE_COMBO -> ANIM_COMBO;
            case SPIKE_STORM -> ANIM_STORM;
            case NIGHTMARE_REALM -> ANIM_REALM;
            case SPIN_SLASH -> ANIM_SPIN_SLASH;
            case NIGHTMARE_DESCENT -> ANIM_ULTIMATE;
            case PHASE_CHANGE -> ANIM_PHASE_CHANGE;
            default -> null;
        };
    }

    public static final class GiantSteeringMoveControl extends MoveControl {
        private final NightmareKnightBossEntity boss;

        public GiantSteeringMoveControl(NightmareKnightBossEntity boss) {
            super(boss);
            this.boss = boss;
        }

        @Override
        public void tick() {
            if (boss.isRotationLocked()) {
                boss.setZza(0.0f);
                boss.setXxa(0.0f);
                return;
            }
            if (this.operation != Operation.MOVE_TO) {
                boss.setZza(0.0f);
                boss.setXxa(0.0f);
                return;
            }
            this.operation = Operation.WAIT;

            double dx = this.wantedX - boss.getX();
            double dz = this.wantedZ - boss.getZ();
            double horizSq = dx * dx + dz * dz;
            if (horizSq < 0.25) {
                boss.setZza(0.0f);
                return;
            }

            Vec3 desiredDir = new Vec3(dx, 0.0, dz).normalize();
            Vec3 steerDir = computeObstacleAwareSteer(desiredDir);

            boss.smoothRotateToward(steerDir, 6.0f, (float) NightmareKnightConfig.get().phase2TurnSpeedMultiplier);

            float speed = (float) (this.speedModifier * boss.getAttributeValue(Attributes.MOVEMENT_SPEED));
            boss.setSpeed(speed);
            boss.setZza(speed);

            if (boss.onGround() && isStepOrObstacleAhead(steerDir)) {
                boss.getJumpControl().jump();
            }
        }

        private Vec3 computeObstacleAwareSteer(Vec3 desiredDir) {
            if (!isTallWallAhead(desiredDir, 3.2)) {
                return desiredDir;
            }
            Vec3 left = rotateY(desiredDir, Math.toRadians(55.0));
            Vec3 right = rotateY(desiredDir, Math.toRadians(-55.0));
            boolean leftBlocked = isTallWallAhead(left, 3.5);
            boolean rightBlocked = isTallWallAhead(right, 3.5);
            if (!leftBlocked && rightBlocked) return left;
            if (!rightBlocked && leftBlocked) return right;
            return (boss.getId() % 2 == 0) ? left : right;
        }

        private boolean isStepOrObstacleAhead(Vec3 dir) {
            Level level = boss.level();
            BlockPos ahead = BlockPos.containing(
                    boss.getX() + dir.x * 2.6,
                    boss.getY() + 0.5,
                    boss.getZ() + dir.z * 2.6);
            boolean footBlocked = !level.getBlockState(ahead).getCollisionShape(level, ahead).isEmpty();
            boolean midOpen = level.getBlockState(ahead.above(4)).getCollisionShape(level, ahead.above(4)).isEmpty();
            return footBlocked && midOpen;
        }

        private boolean isTallWallAhead(Vec3 dir, double dist) {
            Level level = boss.level();
            BlockPos base = BlockPos.containing(
                    boss.getX() + dir.x * dist,
                    boss.getY() + 1.0,
                    boss.getZ() + dir.z * dist);
            int solidCount = 0;
            for (int y = 0; y < 5; y++) {
                BlockPos p = base.above(y);
                BlockState st = level.getBlockState(p);
                if (!st.getCollisionShape(level, p).isEmpty() && !st.is(BlockTags.LEAVES)) {
                    solidCount++;
                }
            }
            return solidCount >= 4;
        }
    }
}

