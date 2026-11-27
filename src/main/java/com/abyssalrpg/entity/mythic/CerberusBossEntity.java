package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.config.CerberusConfig;
import com.abyssalrpg.entity.mythic.cerberus.CerberusForm;
import com.abyssalrpg.entity.mythic.cerberus.CerberusGroupManager;
import com.abyssalrpg.entity.mythic.cerberus.CerberusGroupManager.EncounterGroup;
import com.abyssalrpg.entity.mythic.cerberus.CerberusSkillGoals;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.CerberusVfxPayload;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModEntities;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
 * BOSS CERBERUS (GeckoLib 4.8.3 — Tái sử dụng hạ tầng lớp cha AbstractGeoMythicBoss + BossHealthHolder).
 *
 * =========================================================================
 * HƯỚNG DẪN TÊN BONE BẮT BUỘC KHI XUẤT TỪ BLOCKBENCH (.bbmodel -> GeckoLib):
 * Khi chuyển đổi model gốc Sketchfab (opERR0R / MythicMobs x ModelEngine):
 * - XÓA / ĐỔI TÊN các bone chỉ dành cho ModelEngine: `hitbox`, `mount`, `nametag`, mọi bone tiền tố
 *   `h_`, `b_`, `p_`, và các locator thuần ModelEngine.
 * - GIỮ ĐÚNG các tên bone bắt buộc dưới đây (đã định nghĩa thành hằng số tập trung):
 *   + Gốc & Thân : `root`, `body`, `chest`
 *   + 3 Cổ       : `neck_1` (trái / Fire), `neck_2` (giữa / Alpha & dạng 1 đầu), `neck_3` (phải / Shadow)
 *   + 3 Đầu      : `head_1`, `head_2`, `head_3`
 *   + 3 Hàm dưới : `jaw_1`, `jaw_2`, `jaw_3`
 *   + 4 Chân     : `leg_fl`, `leg_fr`, `leg_bl`, `leg_br`
 *   + Đuôi       : `tail_1`, `tail_2`, `tail_3`
 * - Dạng 1 đầu (Phase 2):
 *   + Nếu `useSeparateSingleGeo = true` (mặc định): nạp `assets/abyssalrpg/geo/entity/cerberus_single.geo.json`
 *     (chỉ giữ `neck_2` / `head_2` / `jaw_2` ở chính giữa ngực).
 *   + Nếu `useSeparateSingleGeo = false`: dùng chung `cerberus.geo.json`, `CerberusGeoModel` tự ẩn `neck_1/head_1/jaw_1`
 *     và `neck_3/head_3/jaw_3` cho mọi form Phase 2.
 *
 * CÁCH ĐO & CHỈNH SCALE KHI THAY .bbmodel THẬT:
 * - Kích thước mục tiêu (ngang Leviathan & Spirit Hydra): rộng 8.0 block × cao 6.5 block (`BASE_WIDTH = 3.2`,
 *   `BASE_HEIGHT = 2.6`, `SCALE = 2.5`, `BASE_RENDER_SCALE = 1.25`).
 * - Trong Blockbench, đo chiều cao gốc theo pixel `H_px` (từ y=0 đến đỉnh đầu cao nhất) -> `H_orig = H_px / 16.0`.
 * - Khi `BASE_RENDER_SCALE = 1.25f`, hệ số render cần thiết = `6.5 / H_orig`, do đó đặt
 *   `scale = (6.5 / H_orig) / 1.25` trong `config/abyssalrpg_cerberus.json` (hoặc chỉnh hằng số `SCALE`).
 * - Dạng 1 đầu Phase 2 tự động nhân thêm `splitScaleFactor` (mặc định 0.75, chỉnh lên 1.0 trong config).
 * =========================================================================
 */
public class CerberusBossEntity extends AbstractGeoMythicBoss {
    // =========================================================================
    // HẰNG SỐ KÍCH THƯỚC & BONE / LOCATOR GECKOLIB (MODEL V4: 75.8w x 69.8h x 195.2l px, 38 bones, 1174 cubes)
    // SCALE cơ sở = 1.49f (H=6.50 blk) × visualScaleMultiplier (mặc định 1.10) -> SCALE hiệu dụng 1.639f (cao 7.15 blk)!
    // Hitbox Phase 1 = 8.80 x 7.15 blk (Phase 2 × splitScaleFactor=0.75 -> 6.60 x 5.36 blk).
    // =========================================================================
    public static final float NATIVE_HEIGHT_PX = 69.8f;
    public static final float TARGET_HITBOX_WIDTH = 8.0f;
    public static final float TARGET_HITBOX_HEIGHT = 6.5f;
    public static final float SCALE = 1.49f;
    public static final float BASE_RENDER_SCALE = 1.0f;
    public static final float BASE_WIDTH = TARGET_HITBOX_WIDTH / SCALE;
    public static final float BASE_HEIGHT = TARGET_HITBOX_HEIGHT / SCALE;
    public static final float BASE_EYE_HEIGHT = BASE_HEIGHT * 0.72f;
    /** Chuẩn quy đổi tầm đòn/VFX (2.5f × visualScaleMultiplier = 2.75f ở Phase 1, 2.06f ở Phase 2). */
    private static final float GAMEPLAY_REACH_UNIT = 2.5f;

    public static final int HEAD_COUNT = 3;
    public static final int SINGLE_ACTIVE_HEAD_INDEX = 1; // head_2 (đầu giữa cho dạng 1 đầu Phase 2)
    public static final int FULL_HEADS_MASK = (1 << HEAD_COUNT) - 1;

    // Bone gốc (giữ nguyên 100% tên tương thích Code & Animation, 100% bind rotation = [0,0,0])
    public static final String ROOT_BONE = "root";
    public static final String BODY_BONE = "body";
    public static final String CHEST_BONE = "chest";
    public static final String[] NECK_BONES = { "neck_1", "neck_2", "neck_3" };
    public static final String[] UPPER_NECK_BONES = { "neck_1_upper", "neck_2_upper", "neck_3_upper" };
    public static final String[] HEAD_BONES = { "head_1", "head_2", "head_3" };
    public static final String[] JAW_BONES = { "jaw_1", "jaw_2", "jaw_3" };
    public static final String[] LEG_BONES = { "leg_fl", "leg_fr", "leg_bl", "leg_br" };
    public static final String[] LOWER_LEG_BONES = { "leg_fl_lower", "leg_fr_lower", "leg_bl_lower", "leg_br_lower" };
    public static final String[] FOOT_BONES = { "foot_fl", "foot_fr", "foot_bl", "foot_br" };
    public static final String[] TAIL_BONES = { "tail_1", "tail_2", "tail_3", "tail_4", "tail_5", "tail_6" };
    public static final String[] MOUTH_FIRE_LOCATORS = { "mouth_fire_l", "mouth_fire_c", "mouth_fire_r" };
    public static final String TAIL_FIRE_LOCATOR = "tail_fire";
    public static final String TAIL_TIP_LOCATOR = "tail_tip";
    public static final String[] SPIKE_TIP_LOCATORS = {
            "spike_tip_l1", "spike_tip_r1", "spike_tip_l2", "spike_tip_r2", "spike_tip_l3", "spike_tip_r3"
    };
    public static final String[] CRACK_VENT_LOCATORS = {
            "crack_vent_fl", "crack_vent_fr", "crack_vent_bl", "crack_vent_br", "crack_vent_back"
    };
    public static final String[] FOOT_PAD_LOCATORS = { "foot_pad_fl", "foot_pad_fr", "foot_pad_bl", "foot_pad_br" };

    /** Tọa độ locator mouth_fire_l/c/r chính xác trong không gian Bedrock model v4 (1/16 block, -Z hướng trước). */
    public static final float[][] HEAD_MODEL_MOUTH_PIVOTS = {
            { -26.87f, 32.59f, -58.85f }, // head_1 (trái — FIRE, góc quạt +20°)
            {   0.00f, 37.87f, -66.94f }, // head_2 (giữa — ALPHA / dạng 1 đầu, hướng thẳng 0°)
            {  26.87f, 32.59f, -58.85f }  // head_3 (phải — SHADOW, góc quạt -20°)
    };
    /** Tọa độ locator tail_fire & tail_tip ở chóp lửa 3 cánh đầu đuôi (model px). */
    public static final float[] TAIL_FIRE_MODEL_PIVOT = { 0.00f, 22.00f, 106.10f };
    public static final float[] TAIL_TIP_MODEL_PIVOT  = { 0.00f, 22.00f, 118.60f };
    /** Tọa độ 6 locator đỉnh giáp sống lưng vát ngược (model v4). */
    public static final float[][] SPIKE_TIP_MODEL_PIVOTS = {
            { -4.20f, 46.50f, -18.00f },
            {  4.20f, 46.50f, -18.00f },
            { -3.20f, 42.00f,   6.00f },
            {  3.20f, 42.00f,   6.00f },
            { -2.40f, 39.50f,  26.00f },
            {  2.40f, 39.50f,  26.00f }
    };
    /** Tọa độ 5 locator khe nứt dung nham quanh vai, hông và sống lưng (crack_vent_*). */
    public static final float[][] CRACK_VENT_MODEL_PIVOTS = {
            { -17.20f, 33.00f, -15.00f },
            {  17.20f, 33.00f, -15.00f },
            { -14.00f, 30.50f,  28.00f },
            {  14.00f, 30.50f,  28.00f },
            {   0.00f, 44.30f,  -6.00f }
    };
    /** Tọa độ 4 locator đệm bàn chân xòe rộng (foot_pad_fl/fr/bl/br). */
    public static final float[][] FOOT_PAD_MODEL_PIVOTS = {
            { -29.20f, 0.20f, -21.20f },
            {  29.20f, 0.20f, -21.20f },
            { -24.20f, 0.20f,  23.80f },
            {  24.20f, 0.20f,  23.80f }
    };
    public static final float[] HEAD_BASE_FAN_YAW_DEG = { 20.0f, 0.0f, -20.0f };

    // --- Bảng màu VFX Cerberus ---
    public static final int COLOR_HELLFIRE_ORANGE = 0xFF6A14;
    public static final int COLOR_ALPHA_CRIMSON = 0xFF1E32;
    public static final int COLOR_FIRE_GOLD = 0xFF9E1A;
    public static final int COLOR_SHADOW_VIOLET = 0xA828FF;

    // --- EntityAttributeModifier cố định Phase 2 (ADD_MULTIPLIED_BASE +0.5 = x1.5, idempotent khi reload NBT) ---
    private static final ResourceLocation PHASE2_ARMOR_MOD_ID = AbyssalRpgMod.id("cerberus_phase2_armor");
    private static final ResourceLocation PHASE2_DAMAGE_MOD_ID = AbyssalRpgMod.id("cerberus_phase2_damage");
    private static final ResourceLocation PHASE2_SPEED_MOD_ID = AbyssalRpgMod.id("cerberus_phase2_speed");

    // --- Khai báo RawAnimation GeckoLib ---
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.cerberus.idle");
    private static final RawAnimation ANIM_MOVE = RawAnimation.begin().thenLoop("animation.cerberus.move");
    private static final RawAnimation ANIM_DEATH = RawAnimation.begin().thenPlayAndHold("animation.cerberus.death");
    private static final RawAnimation ANIM_SPLIT_FIREBALL = RawAnimation.begin().thenLoop("animation.cerberus.split_fireball");

    private static final RawAnimation ANIM_BITE_1 = RawAnimation.begin().thenPlay("animation.cerberus.bite_1");
    private static final RawAnimation ANIM_BITE_2 = RawAnimation.begin().thenPlay("animation.cerberus.bite_2");
    private static final RawAnimation ANIM_BITE_3 = RawAnimation.begin().thenPlay("animation.cerberus.bite_3");
    private static final RawAnimation ANIM_BREATH = RawAnimation.begin().thenPlay("animation.cerberus.breath");
    private static final RawAnimation ANIM_POUNCE = RawAnimation.begin().thenPlay("animation.cerberus.pounce");
    private static final RawAnimation ANIM_SLAM = RawAnimation.begin().thenPlay("animation.cerberus.slam");
    private static final RawAnimation ANIM_HOWL = RawAnimation.begin().thenPlay("animation.cerberus.howl");
    private static final RawAnimation ANIM_PHASE_CHANGE = RawAnimation.begin().thenPlay("animation.cerberus.phase_change");
    private static final RawAnimation ANIM_LAND_SPAWN = RawAnimation.begin().thenPlay("animation.cerberus.land_spawn");
    private static final RawAnimation ANIM_NOVA = RawAnimation.begin().thenPlay("animation.cerberus.nova");
    private static final RawAnimation ANIM_DASH = RawAnimation.begin().thenPlay("animation.cerberus.dash");
    private static final RawAnimation ANIM_SHADOW_STEP = RawAnimation.begin().thenPlay("animation.cerberus.shadow_step");
    private static final RawAnimation ANIM_PACK_HUNT = RawAnimation.begin().thenPlay("animation.cerberus.pack_hunt");

    // --- State Machine cho toàn bộ skill Phase 1 + 3 vai trò Phase 2 + Pack Hunt ---
    public enum SkillState {
        IDLE,
        // Phase 1 (TRIPLE)
        TRIPLE_BITE,
        HELLFIRE_BREATH,
        POUNCE,
        GROUND_SLAM,
        HOWL,
        // Phase 2 chuyên biệt theo vai trò
        BITE_COMBO,      // ALPHA_HEAD
        WRATH_CHARGE,    // ALPHA_HEAD
        FLAME_RAIN,      // FIRE_HEAD
        HELLFIRE_NOVA,   // FIRE_HEAD
        SHADOW_DASH,     // SHADOW_HEAD
        SHADOW_STEP,     // SHADOW_HEAD
        PHANTOM_CLAW,    // SHADOW_HEAD
        // Combo 3 cá thể
        PACK_HUNT,
        // Chuyển phase & Chết
        PHASE_CHANGE,
        DEATH;

        public static SkillState byId(int id) {
            SkillState[] vals = values();
            return (id >= 0 && id < vals.length) ? vals[id] : IDLE;
        }
    }

    // --- DataTracker đồng bộ Server <-> Client ---
    private static final EntityDataAccessor<Integer> DATA_FORM =
            SynchedEntityData.defineId(CerberusBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SKILL_STATE =
            SynchedEntityData.defineId(CerberusBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HAS_SPLIT =
            SynchedEntityData.defineId(CerberusBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SPLIT_STAGE =
            SynchedEntityData.defineId(CerberusBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANIM_LOCKED_HEADS_MASK =
            SynchedEntityData.defineId(CerberusBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_GROUP_ID =
            SynchedEntityData.defineId(CerberusBossEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_GROUP_TOTAL_HP =
            SynchedEntityData.defineId(CerberusBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GROUP_MAX_HP =
            SynchedEntityData.defineId(CerberusBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GROUP_ALIVE_COUNT =
            SynchedEntityData.defineId(CerberusBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHADOW_CLOAKED =
            SynchedEntityData.defineId(CerberusBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_ACTION_ANIM_SPEED =
            SynchedEntityData.defineId(CerberusBossEntity.class, EntityDataSerializers.FLOAT);

    public static final int CUSTOM_DEATH_DURATION = 55;
    private static final int MAX_FIRE_TRAIL_PATCHES = 12;

    private static volatile boolean debugLoggingEnabled = false;

    // --- Trạng thái nhóm & chuyển phase ---
    private UUID groupId = UUID.randomUUID();
    private boolean hasSplit = false;
    private boolean splitChildrenSpawned = false;
    private boolean finalGroupDeath = true;
    private boolean forceKilledBySharedPool = false;

    private Vec3 splitOriginPos = Vec3.ZERO;
    private final Vec3[] splitLandingTargets = { Vec3.ZERO, Vec3.ZERO, Vec3.ZERO };

    // --- Pack Hunt slot runtime ---
    private int packHuntSlotIndex = -1;
    private int packHuntDelayTicks = 0;
    @Nullable
    private UUID packHuntTargetId = null;

    // --- Cooldown skill, chống lặp 3 lần & vệt lửa ngắn FIRE_HEAD ---
    private final Map<SkillState, Integer> skillCooldowns = new EnumMap<>(SkillState.class);
    private SkillState lastSkill1 = SkillState.IDLE;
    private SkillState lastSkill2 = SkillState.IDLE;
    private int globalSkillRestTicks = 28;
    private int targetRefreshTicks = 0;
    private int footstepStrideTicks = 0;
    private boolean footstepPairToggle = false;

    private final List<FireTrailPatch> fireTrails = new ArrayList<>();

    // --- Cache vị trí bone đầu thật & góc head-tracking mượt trên Client + cờ reset góc khi đổi form ---
    private final Vec3[] clientHeadWorldPositions = new Vec3[HEAD_COUNT];
    private final float[] clientTrackedHeadYawDeg = new float[HEAD_COUNT];
    private final float[] clientTrackedHeadPitchDeg = new float[HEAD_COUNT];
    private int lastClientAmbientEmberTick = -100;
    private volatile boolean pendingClientHeadReset = false;

    private static final class FireTrailPatch {
        final Vec3 pos;
        int ticksLeft;

        FireTrailPatch(Vec3 pos, int ticks) {
            this.pos = pos;
            this.ticksLeft = ticks;
        }
    }

    @SuppressWarnings("this-escape")
    public CerberusBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
        this.xpReward = 260;
        for (SkillState s : SkillState.values()) {
            skillCooldowns.put(s, 0);
        }
        this.entityData.set(DATA_GROUP_ID, this.groupId.toString());
        initializeBossHealth((float) CerberusConfig.get().maxHealth);
        this.refreshDimensions();
    }

    public static boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled || CerberusConfig.get().debugRotationLogs;
    }

    public static void setDebugLoggingEnabled(boolean enabled) {
        debugLoggingEnabled = enabled;
    }

    // =========================================================================
    // GECKOLIB 4.x ANIMATION CONTROLLERS
    // =========================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (!this.isAlive() || this.deathTime > 0 || getSkillState() == SkillState.DEATH) {
                return state.setAndContinue(ANIM_DEATH);
            }
            if (getSplitStage() == 2) {
                return state.setAndContinue(ANIM_SPLIT_FIREBALL);
            }
            if (isTransitioningPhase()) {
                return state.setAndContinue(ANIM_IDLE);
            }
            if (state.isMoving() || this.getDeltaMovement().horizontalDistanceSqr() > 0.0025) {
                return state.setAndContinue(ANIM_MOVE);
            }
            return state.setAndContinue(ANIM_IDLE);
        }));

        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .setAnimationSpeedHandler(anim -> (double) anim.getSyncedActionAnimSpeed())
                .triggerableAnim("bite_1", ANIM_BITE_1)
                .triggerableAnim("bite_2", ANIM_BITE_2)
                .triggerableAnim("bite_3", ANIM_BITE_3)
                .triggerableAnim("breath", ANIM_BREATH)
                .triggerableAnim("pounce", ANIM_POUNCE)
                .triggerableAnim("slam", ANIM_SLAM)
                .triggerableAnim("howl", ANIM_HOWL)
                .triggerableAnim("phase_change", ANIM_PHASE_CHANGE)
                .triggerableAnim("split_fireball", ANIM_SPLIT_FIREBALL)
                .triggerableAnim("land_spawn", ANIM_LAND_SPAWN)
                .triggerableAnim("nova", ANIM_NOVA)
                .triggerableAnim("dash", ANIM_DASH)
                .triggerableAnim("shadow_step", ANIM_SHADOW_STEP)
                .triggerableAnim("pack_hunt", ANIM_PACK_HUNT));
    }

    public float getSyncedActionAnimSpeed() {
        return Mth.clamp(this.entityData.get(DATA_ACTION_ANIM_SPEED), 0.5f, 2.0f);
    }

    public void setSyncedActionAnimSpeed(float speed) {
        this.entityData.set(DATA_ACTION_ANIM_SPEED, Mth.clamp(speed, 0.5f, 2.0f));
    }

    public void playActionAnim(String key) {
        playActionAnim(key, 1.0f);
    }

    public void playActionAnim(String key, float speed) {
        if (!this.level().isClientSide) {
            setSyncedActionAnimSpeed(speed);
            this.triggerAnim("action", key);
        }
    }

    // =========================================================================
    // DATA TRACKER, FORM, SCALE ĐỘNG & VỊ TRÍ BONE ĐẦU THẬT
    // =========================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FORM, CerberusForm.TRIPLE.id());
        builder.define(DATA_SKILL_STATE, SkillState.IDLE.ordinal());
        builder.define(DATA_HAS_SPLIT, false);
        builder.define(DATA_SPLIT_STAGE, 0);
        builder.define(DATA_ANIM_LOCKED_HEADS_MASK, 0);
        builder.define(DATA_GROUP_ID, "");
        builder.define(DATA_GROUP_TOTAL_HP, (float) CerberusConfig.get().maxHealth);
        builder.define(DATA_GROUP_MAX_HP, (float) CerberusConfig.get().maxHealth);
        builder.define(DATA_GROUP_ALIVE_COUNT, 1);
        builder.define(DATA_SHADOW_CLOAKED, false);
        builder.define(DATA_ACTION_ANIM_SPEED, 1.0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_FORM.equals(key)) {
            this.refreshDimensions();
            if (this.level().isClientSide) {
                this.pendingClientHeadReset = true;
                for (int i = 0; i < HEAD_COUNT; i++) {
                    resetClientHeadTracking(i);
                }
            }
        }
    }

    public void stepClientHeadTracking(int headIdx, float targetYawDeg, float targetPitchDeg,
                                       float maxYawDeg, float maxPitchDeg) {
        if (headIdx < 0 || headIdx >= HEAD_COUNT) return;
        float curYaw = clientTrackedHeadYawDeg[headIdx];
        float curPitch = clientTrackedHeadPitchDeg[headIdx];
        float maxStep = 3.2f; // Mượt theo khung hình client (~64°/s ở 20fps, cực dịu ở 60fps+)
        clientTrackedHeadYawDeg[headIdx] = Mth.clamp(
                Mth.approachDegrees(curYaw, targetYawDeg, maxStep), -maxYawDeg, maxYawDeg);
        clientTrackedHeadPitchDeg[headIdx] = Mth.clamp(
                Mth.approachDegrees(curPitch, targetPitchDeg, maxStep), -maxPitchDeg, maxPitchDeg);
    }

    public void resetClientHeadTracking(int headIdx) {
        if (headIdx < 0 || headIdx >= HEAD_COUNT) return;
        clientTrackedHeadYawDeg[headIdx] = 0.0f;
        clientTrackedHeadPitchDeg[headIdx] = 0.0f;
    }

    public float getClientTrackedHeadYawDeg(int headIdx) {
        return (headIdx >= 0 && headIdx < HEAD_COUNT) ? clientTrackedHeadYawDeg[headIdx] : 0.0f;
    }

    public float getClientTrackedHeadPitchDeg(int headIdx) {
        return (headIdx >= 0 && headIdx < HEAD_COUNT) ? clientTrackedHeadPitchDeg[headIdx] : 0.0f;
    }

    public boolean consumeClientAmbientEmberTick() {
        if (this.tickCount - this.lastClientAmbientEmberTick >= 14) {
            this.lastClientAmbientEmberTick = this.tickCount;
            return true;
        }
        return false;
    }

    public boolean consumePendingClientHeadReset() {
        if (this.pendingClientHeadReset) {
            this.pendingClientHeadReset = false;
            return true;
        }
        return false;
    }

    public CerberusForm getCerberusForm() {
        return CerberusForm.byId(this.entityData.get(DATA_FORM));
    }

    public void setCerberusForm(CerberusForm form) {
        this.entityData.set(DATA_FORM, form.id());
        this.refreshDimensions();
        applyFormScaledMovementAttributes();
    }

    public SkillState getSkillState() {
        return SkillState.byId(this.entityData.get(DATA_SKILL_STATE));
    }

    public void setSkillState(SkillState state) {
        this.entityData.set(DATA_SKILL_STATE, state.ordinal());
    }

    public boolean hasSplit() {
        return this.hasSplit || this.entityData.get(DATA_HAS_SPLIT);
    }

    public int getSplitStage() {
        return this.entityData.get(DATA_SPLIT_STAGE);
    }

    public boolean isShadowCloaked() {
        return this.entityData.get(DATA_SHADOW_CLOAKED);
    }

    public void setShadowCloaked(boolean cloaked) {
        this.entityData.set(DATA_SHADOW_CLOAKED, cloaked);
    }

    public UUID getGroupId() {
        return this.groupId;
    }

    public String getSyncedGroupId() {
        String s = this.entityData.get(DATA_GROUP_ID);
        return (s != null && !s.isEmpty()) ? s : this.groupId.toString();
    }

    public float getSyncedGroupTotalHp() { return this.entityData.get(DATA_GROUP_TOTAL_HP); }
    public float getSyncedGroupMaxHp() { return Math.max(1.0f, this.entityData.get(DATA_GROUP_MAX_HP)); }
    public int getSyncedGroupAliveCount() { return this.entityData.get(DATA_GROUP_ALIVE_COUNT); }

    public void syncGroupHudState(float totalHp, float totalMax, int aliveCount) {
        this.entityData.set(DATA_GROUP_TOTAL_HP, Math.max(0.0f, totalHp));
        this.entityData.set(DATA_GROUP_MAX_HP, Math.max(1.0f, totalMax));
        this.entityData.set(DATA_GROUP_ALIVE_COUNT, Math.max(0, aliveCount));
    }

    /** Hệ số tỉ lệ dạng hiện tại so với dạng 3 đầu: 1.0 (TRIPLE) hoặc SPLIT_SCALE_FACTOR (0.75..1.0 ở Phase 2). */
    public float getFormScaleRatio() {
        return getCerberusForm().isSplitSingle() ? CerberusConfig.get().splitScaleFactor : 1.0f;
    }

    /** Scale render model thực tế (Phase 1 mặc định 1.88 * 1.25 = 2.35f; Phase 2 mặc định 2.35 * 0.75 = 1.7625f). */
    public float getEffectiveScale() {
        CerberusConfig cfg = CerberusConfig.get();
        return cfg.scale * cfg.visualScaleMultiplier * getFormScaleRatio();
    }

    /** Hệ số scale tương đối so với EntityType mặc định (1.25f ở Phase 1 -> 10.0x8.125; 0.9375f ở Phase 2 -> 7.5x6.09). */
    public float getRelativeScaleFactor() {
        return getEffectiveScale() / SCALE;
    }

    /** Hệ số tầm đòn/particle/projectile đồng bộ theo kích thước thực tế (3.125f ở Phase 1, 2.344f ở Phase 2). */
    public float getGameplayScale() {
        return GAMEPLAY_REACH_UNIT * getRelativeScaleFactor();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        float rel = getRelativeScaleFactor();
        return super.getDefaultDimensions(pose).scale(rel);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        float sc = getGameplayScale();
        return this.getBoundingBox().inflate(9.5 * sc, 5.5 * sc, 9.5 * sc);
    }

    private void applyFormScaledMovementAttributes() {
        CerberusConfig cfg = CerberusConfig.get();
        float scaleFactor = getRelativeScaleFactor();
        setAttr(Attributes.FOLLOW_RANGE, cfg.detectionRadius * Math.max(0.65f, scaleFactor));
        setAttr(Attributes.STEP_HEIGHT, cfg.stepHeight * Math.max(0.60f, scaleFactor));
    }

    @Override
    public boolean isTransitioningPhase() {
        return super.isTransitioningPhase() || getSkillState() == SkillState.PHASE_CHANGE;
    }

    public boolean isHeadLockedByAnimation(int headIdx) {
        if (!this.isAlive() || this.deathTime > 0 || this.isSpawningIn() || this.isTransitioningPhase()) {
            return true;
        }
        SkillState st = getSkillState();
        if (st == SkillState.PHASE_CHANGE || st == SkillState.DEATH || st == SkillState.GROUND_SLAM
                || st == SkillState.HOWL || st == SkillState.HELLFIRE_NOVA || st == SkillState.PACK_HUNT
                || st == SkillState.WRATH_CHARGE || st == SkillState.SHADOW_DASH || st == SkillState.POUNCE) {
            return true;
        }
        if (headIdx >= 0 && headIdx < HEAD_COUNT) {
            return (this.entityData.get(DATA_ANIM_LOCKED_HEADS_MASK) & (1 << headIdx)) != 0;
        }
        return false;
    }

    public void setHeadAnimationLock(int headIdx, boolean locked) {
        if (headIdx < 0 || headIdx >= HEAD_COUNT) return;
        int cur = this.entityData.get(DATA_ANIM_LOCKED_HEADS_MASK);
        int next = locked ? (cur | (1 << headIdx)) : (cur & ~(1 << headIdx));
        this.entityData.set(DATA_ANIM_LOCKED_HEADS_MASK, next);
    }

    public void setAllHeadsAnimationLocked(boolean locked) {
        this.entityData.set(DATA_ANIM_LOCKED_HEADS_MASK, locked ? FULL_HEADS_MASK : 0);
    }

    public void updateClientHeadWorldPosition(int headIdx, double wx, double wy, double wz) {
        if (headIdx >= 0 && headIdx < HEAD_COUNT) {
            this.clientHeadWorldPositions[headIdx] = new Vec3(wx, wy, wz);
        }
    }

    /**
     * Trả về vị trí thế giới thật của miệng bone đầu `headIdx`:
     * - Dạng 1 đầu Phase 2 luôn quy về đầu giữa `SINGLE_ACTIVE_HEAD_INDEX` (`head_2`).
     * - Trên Client: ưu tiên ma trận thật `GeoBone.getWorldPosition()`.
     * - Trên Server: tính chính xác từ pivot model nhân `BASE_RENDER_SCALE * getEffectiveScale()`.
     */
    public Vec3 getHeadWorldPosition(int headIdx) {
        int idx = getCerberusForm().isSplitSingle()
                ? SINGLE_ACTIVE_HEAD_INDEX
                : Mth.clamp(headIdx, 0, HEAD_COUNT - 1);
        if (this.level().isClientSide && clientHeadWorldPositions[idx] != null) {
            return clientHeadWorldPositions[idx];
        }
        float[] pivot = HEAD_MODEL_MOUTH_PIVOTS[idx];
        return modelPointToWorld(pivot[0], pivot[1], pivot[2], BASE_RENDER_SCALE * getEffectiveScale());
    }

    /** Vị trí thế giới thực của locator `tail_fire` tại chùy dung nham đầu đuôi. */
    public Vec3 getTailFireWorldPosition() {
        return modelPointToWorld(
                TAIL_FIRE_MODEL_PIVOT[0], TAIL_FIRE_MODEL_PIVOT[1], TAIL_FIRE_MODEL_PIVOT[2],
                BASE_RENDER_SCALE * getEffectiveScale()
        );
    }

    /** Vị trí thế giới thực của locator `tail_tip` tại mũi nhọn chùy đuôi. */
    public Vec3 getTailTipWorldPosition() {
        return modelPointToWorld(
                TAIL_TIP_MODEL_PIVOT[0], TAIL_TIP_MODEL_PIVOT[1], TAIL_TIP_MODEL_PIVOT[2],
                BASE_RENDER_SCALE * getEffectiveScale()
        );
    }

    /** Vị trí thế giới thực của locator đỉnh gai lưng `spike_tip_*`. */
    public Vec3 getSpikeTipWorldPosition(int idx) {
        float[] p = SPIKE_TIP_MODEL_PIVOTS[Math.floorMod(idx, SPIKE_TIP_MODEL_PIVOTS.length)];
        return modelPointToWorld(p[0], p[1], p[2], BASE_RENDER_SCALE * getEffectiveScale());
    }

    /** Vị trí thế giới thực của locator khe nứt dung nham `crack_vent_*`. */
    public Vec3 getCrackVentWorldPosition(int idx) {
        float[] p = CRACK_VENT_MODEL_PIVOTS[Math.floorMod(idx, CRACK_VENT_MODEL_PIVOTS.length)];
        return modelPointToWorld(p[0], p[1], p[2], BASE_RENDER_SCALE * getEffectiveScale());
    }

    /** Vị trí thế giới thực của locator đệm bàn chân `foot_pad_*` (0=fl, 1=fr, 2=bl, 3=br). */
    public Vec3 getFootPadWorldPosition(int idx) {
        float[] p = FOOT_PAD_MODEL_PIVOTS[Math.floorMod(idx, FOOT_PAD_MODEL_PIVOTS.length)];
        return modelPointToWorld(p[0], p[1], p[2], BASE_RENDER_SCALE * getEffectiveScale());
    }

    /** Helper lấy mặt nền rắn dưới 1 vị trí thế giới để bắn mảnh đá/bụi khớp địa hình. */
    public static BlockState sampleGroundBlockState(ServerLevel level, Vec3 pos) {
        BlockPos bp = BlockPos.containing(pos.x, pos.y - 0.35, pos.z);
        BlockState st = level.getBlockState(bp);
        if (st.isAir() || !st.getFluidState().isEmpty()) {
            st = level.getBlockState(bp.below());
        }
        return (st.isAir() || !st.getFluidState().isEmpty()) ? Blocks.BASALT.defaultBlockState() : st;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new LargeGeoGroundNavigation(this, level);
    }

    @Override
    protected int getDefaultSpawnEggFloor() {
        return 14;
    }

    @Override
    protected Component getStandaloneBossTitle() {
        boolean enraged = this.hasEnteredPhase2() || this.isEnragedVisual() || getCerberusForm().isSplitSingle();
        int rgb = enraged ? 0xFF2E2E : 0xFF8C1A;
        return Component.literal("Cerberus - Chúa Tể Ba Đầu")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true));
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        CerberusConfig cfg = CerberusConfig.get();
        float scaleMult = cfg.visualScaleMultiplier;
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, Math.min(VANILLA_SAFE_MAX_HEALTH_ATTR, cfg.maxHealth))
                .add(Attributes.ATTACK_DAMAGE, cfg.meleeDamage)
                .add(Attributes.ARMOR, cfg.armor)
                .add(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness)
                .add(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed)
                .add(Attributes.FOLLOW_RANGE, cfg.detectionRadius * scaleMult)
                .add(Attributes.STEP_HEIGHT, cfg.stepHeight * scaleMult)
                .add(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance);
    }

    @Override
    public void configureForFloor(int floor, BlockPos arenaCenter, boolean dungeonBoss) {
        super.configureForFloor(floor, arenaCenter, dungeonBoss);
        CerberusConfig cfg = CerberusConfig.get();
        setAttr(Attributes.MAX_HEALTH, Math.min(VANILLA_SAFE_MAX_HEALTH_ATTR, cfg.maxHealth));
        setAttr(Attributes.ATTACK_DAMAGE, cfg.meleeDamage);
        setAttr(Attributes.ARMOR, cfg.armor);
        setAttr(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness);
        setAttr(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance);
        setAttr(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed);
        setAttr(Attributes.SCALE, 1.0); // Scale áp đồng nhất qua EntityDimensions + GeoRenderer
        applyFormScaledMovementAttributes();

        if (this.hasEnteredPhase2 || getCerberusForm().isSplitSingle()) {
            this.phase = 2;
            applyPhase2Modifiers();
            EncounterGroup grp = getGroup();
            float targetMax = (grp != null) ? grp.getIndividualTargetMaxHp() : (float) (cfg.maxHealth * cfg.phase2StatMultiplier / 3.0);
            setBossMaxHealth(targetMax);
            if (this.getHealth() > targetMax) this.setHealth(targetMax);
        } else {
            initializeBossHealth((float) cfg.maxHealth);
        }

        Component title = getStandaloneBossTitle();
        if (title != null) {
            this.setCustomName(title);
        }
        snapSyncPrevAngles();
        if (this.level() instanceof ServerLevel sl) {
            CerberusGroupManager.get(sl.getServer()).getOrCreateGroup(this.groupId).registerMember(this);
        }
    }

    @Nullable
    public EncounterGroup getGroup() {
        if (this.level() instanceof ServerLevel sl) {
            return CerberusGroupManager.get(sl.getServer()).getOrCreateGroup(this.groupId);
        }
        return null;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new BossLockGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        CerberusSkillGoals.registerAll(this, this.goalSelector);
        this.goalSelector.addGoal(7, new SafeLookAtPlayerGoal(this, 64.0f));
        this.goalSelector.addGoal(8, new SafeRandomLookGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // =========================================================================
    // CHẶN CHẾT PHASE 1 -> CHUỖI CHUYỂN PHASE 2 TÁCH 3 CÁ THỂ 1 ĐẦU
    // =========================================================================
    private float getPhase2TriggerThreshold() {
        return Math.max(1.0f, this.getMaxHealth() * (float) CerberusConfig.get().phase2TriggerHpRatio);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypes.IN_WALL)) {
            return false;
        }
        if (this.isSpawningIn() || this.isTransitioningPhase() || this.isShadowCloaked()) {
            return false;
        }

        // Phase 1 (dạng 3 đầu, chưa vào Phase 2): chặn đòn đưa máu xuống <= 5% hoặc <= 1 HP
        if (!this.hasEnteredPhase2 && !this.hasSplit() && this.level() instanceof ServerLevel serverLevel) {
            float triggerHp = getPhase2TriggerThreshold();
            float estDamage = estimatePostArmorDamage(source, amount);
            if (this.getHealth() - estDamage <= triggerHp) {
                this.setHealth(Math.max(1.0f, Math.min(this.getHealth(), triggerHp)));
                beginPhase2SplitTransition(serverLevel);
                return false;
            }
        }

        float hpBefore = this.getHealth();
        boolean applied = super.hurt(source, amount);

        if (applied && this.level() instanceof ServerLevel serverLevel) {
            if (!this.hasEnteredPhase2 && !this.hasSplit() && this.isAlive()) {
                float triggerHp = getPhase2TriggerThreshold();
                if (this.getHealth() <= triggerHp) {
                    this.setHealth(Math.max(1.0f, this.getHealth()));
                    beginPhase2SplitTransition(serverLevel);
                    return true;
                }
            } else if (this.hasSplit() && this.isAlive()) {
                EncounterGroup group = getGroup();
                if (group != null && group.getHealthMode() == CerberusConfig.SplitHealthMode.SHARED_POOL
                        && !group.isApplyingSharedPoolDamage()) {
                    float actualDelta = Math.max(0.0f, hpBefore - this.getHealth());
                    if (actualDelta > 0.0f) {
                        group.applySharedPoolDamage(serverLevel, this, source, actualDelta);
                    }
                }
            }
        }
        return applied;
    }

    /** Đồng bộ máu khi đồng đội chịu sát thương ở chế độ SHARED_POOL. */
    public void syncSharedPoolHealth(float poolHp, float poolMaxHp) {
        setBossMaxHealth(poolMaxHp);
        this.setHealth(Mth.clamp(poolHp, 0.0f, poolMaxHp));
    }

    /** Kết liễu tức thì từ quỹ chung SHARED_POOL (chỉ 1 thành viên được `allowRewards = true`). */
    public void forceKillFromSharedPool(DamageSource source, boolean allowRewards) {
        this.forceKilledBySharedPool = true;
        this.finalGroupDeath = allowRewards;
        this.setHealth(0.0f);
        this.die(source);
    }

    @Override
    public void die(DamageSource damageSource) {
        // Lưới bảo vệ cuối cùng ở Phase 1: không chết nếu chưa tách (trừ /kill)
        if (!this.hasEnteredPhase2 && !this.hasSplit()
                && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && this.level() instanceof ServerLevel serverLevel) {
            this.setHealth(getPhase2TriggerThreshold());
            beginPhase2SplitTransition(serverLevel);
            return;
        }

        setSkillState(SkillState.DEATH);
        setAllHeadsAnimationLocked(true);
        setShadowCloaked(false);
        this.fireTrails.clear();

        if (this.level() instanceof ServerLevel serverLevel) {
            EncounterGroup group = getGroup();
            if (group != null) {
                if (!this.forceKilledBySharedPool
                        && this.hasSplit()
                        && group.getHealthMode() == CerberusConfig.SplitHealthMode.SHARED_POOL
                        && !group.isApplyingSharedPoolDamage()) {
                    // Cú đánh chí mạng trực tiếp lên 1 cá thể ở chế độ SHARED_POOL -> kết liễu đồng loạt 2 con còn lại trước!
                    group.applySharedPoolDamage(serverLevel, this, damageSource, Math.max(1.0f, group.getSharedPoolHp()));
                    return;
                }
                if (!this.forceKilledBySharedPool) {
                    this.finalGroupDeath = group.onMemberKilled(serverLevel, this, damageSource);
                } else {
                    // Đã xác định allowRewards từ forceKillFromSharedPool; chỉ ghi nhận sổ nhóm nếu cần
                    boolean groupSaysDrop = group.onMemberKilled(serverLevel, this, damageSource);
                    this.finalGroupDeath = this.finalGroupDeath && groupSaysDrop;
                }
            } else {
                this.finalGroupDeath = true;
            }

            serverLevel.playSound(null, this.blockPosition(), ModSounds.CERBERUS_DEATH, SoundSource.HOSTILE, 2.8f,
                    getCerberusForm().isSplitSingle() ? 0.92f : 0.72f);
            ModNetworking.sendShakeNearby(serverLevel, this.position(), 76.0, this.finalGroupDeath ? 2.6f : 1.4f, 18);

            if (this.finalGroupDeath) {
                announceToNearbyWithRadius(serverLevel,
                        Component.literal("⚔ CERBERUS ĐÃ BỊ HẠ GỤC — TAM THỦ ĐỊA NGỤC TAN BIẾN THÀNH TRO TÀN! ⚔")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        CerberusConfig.get().bossBarRadius);
            } else {
                announceToNearbyWithRadius(serverLevel,
                        Component.literal("✦ " + getCerberusForm().roleSubtitle() + " đã bị tiêu diệt! (Còn "
                                + (group != null ? group.getAliveMemberCount() : 0) + " cá thể)")
                                .withStyle(getCerberusForm().chatColor()),
                        CerberusConfig.get().bossBarRadius);
            }
        }
        super.die(damageSource);
    }

    @Override
    protected boolean shouldDropLoot() {
        return this.finalGroupDeath;
    }

    @Override
    public boolean shouldDropExperience() {
        return this.finalGroupDeath;
    }

    @Override
    protected boolean shouldAwardBossDefeatRewards() {
        return this.finalGroupDeath;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.level() instanceof ServerLevel sl) {
            EncounterGroup group = CerberusGroupManager.get(sl.getServer()).findGroup(this.groupId);
            if (group != null) {
                if (reason == RemovalReason.DISCARDED) {
                    group.onMemberVanished(sl.getServer(), this.getUUID());
                } else if (reason == RemovalReason.UNLOADED_TO_CHUNK
                        || reason == RemovalReason.UNLOADED_WITH_PLAYER
                        || reason == RemovalReason.CHANGED_DIMENSION) {
                    group.onMemberUnloaded(this.getUUID(), this.getHealth());
                } else {
                    group.releaseAllLocks(this.getUUID());
                }
            }
        }
        super.remove(reason);
    }

    // =========================================================================
    // CHUỖI CHUYỂN PHASE 2 (5.0s = 100 TICKS): GẦM -> 3 CẦU LỬA TAM GIÁC -> 3 CÁ THỂ
    // =========================================================================
    private void beginPhase2SplitTransition(ServerLevel level) {
        if (this.hasEnteredPhase2 || this.hasSplit()) return;
        CerberusConfig cfg = CerberusConfig.get();

        this.hasEnteredPhase2 = true;
        this.entityData.set(DATA_HAS_ENTERED_PHASE2, true);
        this.splitChildrenSpawned = false;
        this.splitOriginPos = this.position();

        lockCurrentRotation();
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.scheduledActions.clear();
        setAllHeadsAnimationLocked(true);

        // Tính 3 điểm đáp an toàn theo hình tam giác đều quanh người chơi gần nhất (hoặc quanh boss)
        Player nearest = level.getNearestPlayer(this, cfg.detectionRadius);
        Vec3 anchor = (nearest != null && nearest.isAlive() && !nearest.isSpectator())
                ? nearest.position() : this.position();
        double baseAng = this.getRandom().nextDouble() * Math.PI * 2.0;
        double triRadius = 11.5 * Math.max(1.0, getRelativeScaleFactor());
        for (int i = 0; i < 3; i++) {
            double ang = baseAng + i * (Math.PI * 2.0 / 3.0);
            Vec3 desired = new Vec3(anchor.x + Math.cos(ang) * triRadius, anchor.y, anchor.z + Math.sin(ang) * triRadius);
            this.splitLandingTargets[i] = findSafeLandingPosition(level, desired, this.position(), 8);
        }

        this.transitionStartHp = Math.max(1.0f, this.getHealth());
        this.transitionTotalTicks = Math.max(80, cfg.phaseTransitionTicks); // Mặc định 100 ticks = 5.0s
        this.entityData.set(DATA_PHASE_TRANS_TICKS, this.transitionTotalTicks);
        this.entityData.set(DATA_SPLIT_STAGE, 1);
        setSkillState(SkillState.PHASE_CHANGE);
        playActionAnim("phase_change");

        level.playSound(null, this.blockPosition(), ModSounds.CERBERUS_PHASE_CHANGE, SoundSource.HOSTILE, 3.2f, 0.70f);
        ModNetworking.sendShakeNearby(level, this.position(), 96.0, 2.8f, 32);
        announceToNearbyWithRadius(level,
                Component.literal("⚠ CERBERUS BỐC CHÁY CUỒNG BẠO — TAM THỦ SẮP PHÂN TÁCH THÀNH BA HỎA CẦU! ⚠")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                cfg.bossBarRadius);
    }

    private void tickPhase2Transition(ServerLevel level) {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        normalizeAnglesAndSyncPrev();

        int pTrans = this.entityData.get(DATA_PHASE_TRANS_TICKS);
        int nextTrans = pTrans - 1;
        this.entityData.set(DATA_PHASE_TRANS_TICKS, nextTrans);

        int total = Math.max(80, this.transitionTotalTicks);
        int elapsed = total - nextTrans;
        int stage2Start = (int) Math.round(total * 0.35); // tick 35: bùng nổ thành 3 cầu lửa
        int stage3Start = (int) Math.round(total * 0.68); // tick 68: 3 cầu lửa chạm đất tách 3 con

        logCerberusDebug("Cerberus-P2Trans", nextTrans);

        // Cá thể con (FIRE_HEAD / SHADOW_HEAD) chỉ chạy Stage 3 `land_spawn` hồi đầy máu và mở AI
        if (this.hasSplit()) {
            this.entityData.set(DATA_SPLIT_STAGE, 3);
            float prog = Mth.clamp(1.0f - (nextTrans / (float) Math.max(1, total - stage3Start)), 0.0f, 1.0f);
            float targetHp = Mth.lerp(prog, this.transitionStartHp, this.getMaxHealth());
            if (targetHp > this.getHealth()) {
                this.setHealth(Math.min(this.getMaxHealth(), targetHp));
            }
            spawnRoleBurstParticles(level, this.position().add(0, 1.4, 0), getCerberusForm(), 6);
            if (nextTrans <= 0) {
                finishPhase2Unlock();
            }
            return;
        }

        // === STAGE 1: GẦM & BỐC CHÁY (0..35%) ===
        if (elapsed < stage2Start) {
            this.entityData.set(DATA_SPLIT_STAGE, 1);
            float u = elapsed / (float) Math.max(1, stage2Start);
            if (elapsed % 3 == 0) {
                float r = (float) ((3.0 + (1.0 - u) * 3.5) * getEffectiveScale());
                double hOff = (0.6 + u * 2.4) * getEffectiveScale();
                ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_PHASE_SPIRAL,
                        this.position(), new Vec3(0.0, hOff, 0.0), r, (byte) getCerberusForm().id());
            }
            if (elapsed % 12 == 0) {
                ModNetworking.sendShakeNearby(level, this.position(), 88.0, 1.4f, 10);
            }
            return;
        }

        // === STAGE 2: NỔ THÀNH 3 QUẢ CẦU LỬA BAY HÌNH TAM GIÁC (35%..68%) ===
        if (elapsed == stage2Start) {
            this.entityData.set(DATA_SPLIT_STAGE, 2);
            this.noPhysics = true;
            this.splitOriginPos = this.position();
            playActionAnim("split_fireball");

            level.playSound(null, this.blockPosition(), ModSounds.CERBERUS_SPLIT_BURST, SoundSource.HOSTILE, 3.2f, 0.78f);
            ShockwaveRingEntity.spawn(level, this.position(), 15.0f * getEffectiveScale(), COLOR_ALPHA_CRIMSON, 20);
            ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_SPLIT_EXPLOSION,
                    this.position(), Vec3.ZERO, 6.0f * getEffectiveScale(), (byte) CerberusForm.ALPHA_HEAD.id());
            ModNetworking.sendShakeNearby(level, this.position(), 96.0, 2.6f, 20);

            int flightLen = Math.max(16, stage3Start - stage2Start);
            int[] colors = { COLOR_ALPHA_CRIMSON, COLOR_FIRE_GOLD, COLOR_SHADOW_VIOLET };
            float warnRadius = 3.8f * getRelativeScaleFactor();
            for (int i = 0; i < 3; i++) {
                MagicCircleEntity.spawn(level, this, this.splitLandingTargets[i], warnRadius, flightLen, colors[i], 0.0f, false, false);
            }
        }

        if (elapsed < stage3Start) {
            float u = Mth.clamp((elapsed - stage2Start) / (float) Math.max(1, stage3Start - stage2Start), 0.0f, 1.0f);
            double arcHeight = Math.sin(u * Math.PI) * 8.5;

            // Quả cầu #0 kéo chính thực thể gốc tới điểm đáp ALPHA_HEAD
            Vec3 p0 = splitOriginPos.lerp(splitLandingTargets[0], u).add(0.0, arcHeight, 0.0);
            this.setPos(p0.x, p0.y, p0.z);

            // Quả cầu #1 (FIRE_HEAD) và #2 (SHADOW_HEAD) bay tỏa hình tam giác đồng bộ
            Vec3 p1 = splitOriginPos.lerp(splitLandingTargets[1], u).add(0.0, arcHeight, 0.0);
            Vec3 p2 = splitOriginPos.lerp(splitLandingTargets[2], u).add(0.0, arcHeight, 0.0);

            spawnFireballCoreParticles(level, p0, CerberusForm.ALPHA_HEAD);
            spawnFireballCoreParticles(level, p1, CerberusForm.FIRE_HEAD);
            spawnFireballCoreParticles(level, p2, CerberusForm.SHADOW_HEAD);
            return;
        }

        // === STAGE 3: CHẠM ĐẤT -> HIỆN RA 3 CÁ THỂ 1 ĐẦU (68%..100%) ===
        if (!this.splitChildrenSpawned) {
            executeTriangleSplitLanding(level, Math.max(16, nextTrans));
        }
        if (nextTrans <= 0) {
            finishPhase2Unlock();
        }
    }

    private void executeTriangleSplitLanding(ServerLevel level, int remainingLockTicks) {
        this.splitChildrenSpawned = true;
        this.noPhysics = false;
        this.hasSplit = true;
        this.entityData.set(DATA_HAS_SPLIT, true);
        this.entityData.set(DATA_SPLIT_STAGE, 3);
        this.entityData.set(DATA_ENRAGED_VISUAL, true);
        this.phase = 2;

        CerberusConfig cfg = CerberusConfig.get();
        EncounterGroup group = CerberusGroupManager.get(level.getServer()).getOrCreateGroup(this.groupId);
        float totalP2Hp = (float) (cfg.maxHealth * cfg.phase2StatMultiplier);
        group.configurePhase2Split(cfg.splitHealthMode, totalP2Hp);

        Player nearest = level.getNearestPlayer(this, cfg.detectionRadius);
        Vec3 lookTarget = nearest != null ? nearest.position() : this.splitOriginPos;

        // 1. Thực thể gốc trở thành cá thể #1: ALPHA_HEAD tại điểm đáp 0
        Vec3 land0 = this.splitLandingTargets[0];
        this.setPos(land0.x, land0.y, land0.z);
        this.setCerberusForm(CerberusForm.ALPHA_HEAD);
        this.applyPhase2Modifiers();
        float eachMax = group.getIndividualTargetMaxHp();
        setBossMaxHealth(eachMax);
        this.transitionStartHp = Math.max(1.0f, eachMax * 0.25f);
        this.setHealth(this.transitionStartHp);
        this.facePositionImmediately(lookTarget);
        this.setCustomName(getStandaloneBossTitle());
        playActionAnim("land_spawn");
        triggerFireballLandingImpact(level, land0, CerberusForm.ALPHA_HEAD);
        group.registerMember(this);

        // 2. Tạo 2 thực thể mới cùng EntityType cho FIRE_HEAD (#2) và SHADOW_HEAD (#3)
        CerberusForm[] childForms = { CerberusForm.FIRE_HEAD, CerberusForm.SHADOW_HEAD };
        for (int i = 0; i < 2; i++) {
            Vec3 land = this.splitLandingTargets[i + 1];
            CerberusBossEntity child = ModEntities.CERBERUS.create(level);
            if (child != null) {
                child.moveTo(land.x, land.y, land.z, 0.0f, 0.0f);
                child.initializeAsSplitChild(this.groupId, childForms[i], this.floor, this.arenaCenter, this.dungeonBoss,
                        eachMax, remainingLockTicks, lookTarget);
                level.addFreshEntity(child);
                group.registerMember(child);
            }
            triggerFireballLandingImpact(level, land, childForms[i]);
        }

        group.syncGroupHudToLoadedMembers(level.getServer());
        announceToNearbyWithRadius(level,
                Component.literal("🔥 CERBERUS ĐÃ PHÂN THÂN THÀNH 3 CÁ THỂ CUỒNG BẠO (ALPHA · PYRE · UMBRA) — CHẾ ĐỘ MÁU: "
                        + cfg.splitHealthMode + "! 🔥").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                cfg.bossBarRadius);
    }

    private void initializeAsSplitChild(UUID sharedGroupId, CerberusForm form, int parentFloor,
                                        @Nullable BlockPos parentArena, boolean isDungeon,
                                        float maxHp, int lockTicks, Vec3 facePos) {
        this.configured = true;
        this.groupId = sharedGroupId;
        this.entityData.set(DATA_GROUP_ID, sharedGroupId.toString());
        this.floor = parentFloor;
        this.arenaCenter = parentArena;
        this.dungeonBoss = isDungeon;
        this.phase = 2;
        this.hasEnteredPhase2 = true;
        this.hasSplit = true;
        this.splitChildrenSpawned = true;
        this.finalGroupDeath = false;

        this.entityData.set(DATA_HAS_ENTERED_PHASE2, true);
        this.entityData.set(DATA_HAS_SPLIT, true);
        this.entityData.set(DATA_ENRAGED_VISUAL, true);
        this.entityData.set(DATA_SPLIT_STAGE, 3);
        this.entityData.set(DATA_PHASE_TRANS_TICKS, lockTicks);
        this.transitionTotalTicks = CerberusConfig.get().phaseTransitionTicks;
        setSkillState(SkillState.PHASE_CHANGE);
        setAllHeadsAnimationLocked(true);

        CerberusConfig cfg = CerberusConfig.get();
        setAttr(Attributes.MAX_HEALTH, Math.min(VANILLA_SAFE_MAX_HEALTH_ATTR, cfg.maxHealth));
        setAttr(Attributes.ATTACK_DAMAGE, cfg.meleeDamage);
        setAttr(Attributes.ARMOR, cfg.armor);
        setAttr(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness);
        setAttr(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance);
        setAttr(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed);
        setAttr(Attributes.SCALE, 1.0);

        setCerberusForm(form);
        applyPhase2Modifiers();
        setBossMaxHealth(maxHp);
        this.transitionStartHp = Math.max(1.0f, maxHp * 0.25f);
        this.setHealth(this.transitionStartHp);
        this.setCustomName(getStandaloneBossTitle());
        this.setCustomNameVisible(true);
        facePositionImmediately(facePos);
        playActionAnim("land_spawn");
    }

    private void triggerFireballLandingImpact(ServerLevel level, Vec3 pos, CerberusForm form) {
        float rel = getRelativeScaleFactor();
        level.playSound(null, BlockPos.containing(pos), ModSounds.CERBERUS_ROAR, SoundSource.HOSTILE, 2.4f,
                form == CerberusForm.SHADOW_HEAD ? 1.15f : (form == CerberusForm.FIRE_HEAD ? 0.95f : 0.78f));
        ShockwaveRingEntity.spawn(level, pos, 6.5f * rel, form.accentRgb(), 14);
        ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_SPLIT_EXPLOSION,
                pos, Vec3.ZERO, 3.6f * rel, (byte) form.id());
        ModNetworking.sendShakeNearby(level, pos, 64.0 * rel, 1.6f, 12);

        float landDmg = CerberusConfig.get().splitLandingDamage;
        if (landDmg > 0.01f) {
            com.abyssalrpg.combat.KatanaCombatHandler.runUnparryableMagic(() ->
                    damagePlayersInRadius(level, pos, 4.5 * rel, landDmg, 0.35));
        }
    }

    private void finishPhase2Unlock() {
        this.entityData.set(DATA_SPLIT_STAGE, 0);
        this.entityData.set(DATA_PHASE_TRANS_TICKS, 0);
        this.noPhysics = false;
        this.phase = 2;
        this.setHealth(this.getMaxHealth());
        setAllHeadsAnimationLocked(false);
        snapSyncPrevAngles();
        setSkillState(SkillState.IDLE);
        this.globalSkillRestTicks = 18 + this.getRandom().nextInt(14);
        if (this.level() instanceof ServerLevel sl) {
            EncounterGroup grp = getGroup();
            if (grp != null) {
                grp.registerMember(this);
                grp.syncGroupHudToLoadedMembers(sl.getServer());
            }
        }
    }

    private void applyPhase2Modifiers() {
        CerberusConfig cfg = CerberusConfig.get();
        double bonus = Math.max(0.0, cfg.phase2StatMultiplier - 1.0); // +0.5 -> x1.5

        // Giáp 18 -> 27 (kẹp không vượt trần vanilla 30)
        double baseArmor = Math.max(0.0, cfg.armor);
        double maxAllowedArmorBonus = (baseArmor > 0.0) ? Math.max(0.0, (30.0 / baseArmor) - 1.0) : 0.0;
        applyPermanentMultiplier(Attributes.ARMOR, PHASE2_ARMOR_MOD_ID, Math.min(bonus, maxAllowedArmorBonus));

        // Sát thương x1.5 & tốc độ di chuyển x1.5
        applyPermanentMultiplier(Attributes.ATTACK_DAMAGE, PHASE2_DAMAGE_MOD_ID, bonus);
        applyPermanentMultiplier(Attributes.MOVEMENT_SPEED, PHASE2_SPEED_MOD_ID, bonus);
    }

    private void spawnFireballCoreParticles(ServerLevel level, Vec3 pos, CerberusForm form) {
        if (this.tickCount % 2 == 0) {
            ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_SPLIT_FIREBALL,
                    pos, Vec3.ZERO, 1.4f, (byte) form.id());
        }
    }

    public static void spawnRoleBurstParticles(ServerLevel level, Vec3 pos, CerberusForm form, int count) {
        ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_ROLE_BURST,
                pos, Vec3.ZERO, Math.max(1.2f, count * 0.14f), (byte) form.id());
    }

    // =========================================================================
    // TICK SERVER: QUẢN LÝ CHUYỂN PHASE, VỆT LỬA, MỤC TIÊU PHÂN TÁN & COOLDOWN
    // =========================================================================
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        CerberusConfig cfg = CerberusConfig.get();
        this.fallDistance = 0.0f;

        // Hỗ trợ test trực tiếp bằng /data merge entity ... {Health:50f} hoặc /damage
        if (!this.hasEnteredPhase2 && !this.hasSplit() && this.isAlive()
                && !this.isSpawningIn() && !this.isTransitioningPhase()) {
            if (this.getHealth() <= getPhase2TriggerThreshold()) {
                this.setHealth(Math.max(1.0f, this.getHealth()));
                beginPhase2SplitTransition(serverLevel);
            }
        }

        // Giảm cooldown toàn bộ skill (tăng tốc hồi chiêu 1.5x để tăng tần suất tấn công)
        int cdStep = (this.tickCount % 2 == 0) ? 2 : 1;
        for (SkillState s : SkillState.values()) {
            int cd = skillCooldowns.getOrDefault(s, 0);
            if (cd > 0) skillCooldowns.put(s, Math.max(0, cd - cdStep));
        }
        if (this.globalSkillRestTicks > 0 && getSkillState() == SkillState.IDLE) {
            this.globalSkillRestTicks--;
        }

        // Xử lý vệt lửa ngắn nhẹ của FIRE_HEAD (giới hạn 12 ô, tự dọn sau 3 giây -> 0 lag)
        tickFireTrails(serverLevel);

        // Ưu tiên 1: Chuỗi chuyển Phase 2 (5.0s bất tử, tách 3 cá thể)
        if (this.entityData.get(DATA_PHASE_TRANS_TICKS) > 0) {
            tickPhase2Transition(serverLevel);
            return;
        }

        // Cập nhật phân tán mục tiêu theo nhóm (mỗi 15 tick)
        if (--this.targetRefreshTicks <= 0) {
            this.targetRefreshTicks = 15;
            EncounterGroup group = getGroup();
            if (group != null) {
                Player picked = group.chooseTargetFor(serverLevel, this);
                if (picked != null) {
                    this.setTarget(picked);
                } else if (this.getTarget() != null && (!this.getTarget().isAlive()
                        || this.getTarget() instanceof Player p && (p.isCreative() || p.isSpectator()))) {
                    this.setTarget(null);
                }
            }
        }

        // FIRE_HEAD để lại vệt lửa ngắn khi di chuyển nhanh trong chiến đấu
        if (getCerberusForm() == CerberusForm.FIRE_HEAD && this.tickCount % 10 == 0
                && this.getDeltaMovement().horizontalDistanceSqr() > 0.004 && this.getTarget() != null) {
            addFireTrailPatch(this.position(), 55);
        }

        // Bụi bước chân gắn locator foot_pad_* (gửi 1 gói VFX gọn sang Client) + rung nhẹ
        if (cfg.cerberusFancyEffects && this.onGround() && !this.isShadowCloaked() && getSplitStage() != 2
                && this.getDeltaMovement().horizontalDistanceSqr() > 0.0025) {
            if (++this.footstepStrideTicks >= 9) {
                this.footstepStrideTicks = 0;
                this.footstepPairToggle = !this.footstepPairToggle;
                int pawA = this.footstepPairToggle ? 0 : 1; // FL hoặc FR
                Vec3 pawPos = getFootPadWorldPosition(pawA);
                ModNetworking.sendCerberusVfxNearby(serverLevel, CerberusVfxPayload.KIND_FOOTSTEP_DUST,
                        pawPos, Vec3.ZERO, 1.0f, (byte) getCerberusForm().id());
                if (cfg.footstepShakeEnabled && cfg.footstepShakeIntensity > 0.005f) {
                    ModNetworking.sendShakeNearby(serverLevel, this.position(),
                            22.0 * getRelativeScaleFactor(), cfg.footstepShakeIntensity, 4);
                }
            }
        } else {
            this.footstepStrideTicks = 0;
        }
    }

    public void addFireTrailPatch(Vec3 pos, int durationTicks) {
        if (this.fireTrails.size() >= MAX_FIRE_TRAIL_PATCHES) {
            this.fireTrails.remove(0);
        }
        this.fireTrails.add(new FireTrailPatch(pos, durationTicks));
    }

    private void tickFireTrails(ServerLevel level) {
        if (this.fireTrails.isEmpty()) return;
        boolean checkDamage = (this.tickCount % 10 == 0);
        boolean emitParticles = (this.tickCount % 6 == 0);
        float trailDmg = 4.0f * skillDamageScale();

        List<FireTrailPatch> snapshot = new ArrayList<>(this.fireTrails);
        for (FireTrailPatch patch : snapshot) {
            if (!this.fireTrails.contains(patch)) continue;
            patch.ticksLeft--;
            if (patch.ticksLeft <= 0) {
                this.fireTrails.remove(patch);
                continue;
            }
            if (emitParticles) {
                ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_FIRE_TRAIL,
                        patch.pos, Vec3.ZERO, 1.1f, (byte) getCerberusForm().id());
            }
            if (checkDamage) {
                AABB box = new AABB(patch.pos.x - 1.3, patch.pos.y - 0.5, patch.pos.z - 1.3,
                        patch.pos.x + 1.3, patch.pos.y + 1.6, patch.pos.z + 1.3);
                for (Player p : level.getEntitiesOfClass(Player.class, box,
                        pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    com.abyssalrpg.combat.KatanaCombatHandler.runUnparryableMagic(() ->
                            p.hurt(this.damageSources().mobAttack(this), trailDmg));
                    p.igniteForSeconds(2);
                }
            }
        }
    }

    /** Triệu hồi Hellhound nhỏ (giới hạn tổng <= 6 con trên toàn nhóm EncounterGroup). */
    public int summonHellhounds(ServerLevel level, int desiredCount, @Nullable LivingEntity target) {
        EncounterGroup group = getGroup();
        int cap = group != null ? group.getRemainingHellhoundCapacity(level.getServer()) : CerberusConfig.get().maxActiveHellhounds;
        int count = Math.min(cap, desiredCount);
        if (count <= 0) return 0;

        int spawned = 0;
        double ringR = 3.2 * getEffectiveScale();
        for (int i = 0; i < count; i++) {
            Wolf hound = EntityType.WOLF.create(level);
            if (hound == null) continue;
            double ang = (Math.PI * 2.0 * i) / Math.max(1, count) + level.random.nextDouble() * 0.4;
            Vec3 desired = new Vec3(this.getX() + Math.cos(ang) * ringR, this.getY(), this.getZ() + Math.sin(ang) * ringR);
            Vec3 safe = findSafeLandingPosition(level, desired, this.position(), 5);
            hound.moveTo(safe.x, safe.y, safe.z, level.random.nextFloat() * 360f, 0f);
            hound.finalizeSpawn(level, level.getCurrentDifficultyAt(hound.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            hound.setCustomName(Component.literal("Hellhound").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            hound.addTag("abyssal_hellhound");

            AttributeInstance hp = hound.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(26.0); hound.setHealth(26.0f); }
            AttributeInstance atk = hound.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null) atk.setBaseValue(this.phase >= 2 ? 10.0 : 7.0);
            AttributeInstance spd = hound.getAttribute(Attributes.MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(0.38);

            hound.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 90, 0, false, false));
            hound.setRemainingFireTicks(20 * 60);
            hound.setRemainingPersistentAngerTime(20 * 120);
            if (target != null) {
                hound.setPersistentAngerTarget(target.getUUID());
                hound.setTarget(target);
            }
            level.addFreshEntity(hound);
            if (group != null) {
                group.registerHellhound(hound.getUUID());
            }
            ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_HELLHOUND_SUMMON,
                    safe, Vec3.ZERO, 1.4f, (byte) getCerberusForm().id());
            spawned++;
        }
        return spawned;
    }

    // =========================================================================
    // PACK HUNT & HELPER KỸ NĂNG DÙNG BỞI CerberusSkillGoals
    // =========================================================================
    public void beginPackHuntSlot(LivingEntity target, int slotIdx, int delayTicks) {
        this.packHuntSlotIndex = slotIdx;
        this.packHuntDelayTicks = delayTicks;
        this.packHuntTargetId = target.getUUID();
        setSkillState(SkillState.PACK_HUNT);
        setAllHeadsAnimationLocked(true);
        playActionAnim("pack_hunt");
    }

    public int getPackHuntSlotIndex() { return packHuntSlotIndex; }
    public int getPackHuntDelayTicks() { return packHuntDelayTicks; }
    @Nullable public UUID getPackHuntTargetId() { return packHuntTargetId; }

    public int getSkillCooldown(SkillState state) { return skillCooldowns.getOrDefault(state, 0); }

    public void setSkillCooldown(SkillState state, int baseTicks) {
        float div = (this.phase >= 2) ? (float) CerberusConfig.get().phase2StatMultiplier : 1.0f;
        int scaled = Math.max(20, Math.round(baseTicks / div));
        skillCooldowns.put(state, scaled);
    }

    public boolean wasUsedTwiceInARow(SkillState state) {
        return state != SkillState.IDLE && state == lastSkill1 && state == lastSkill2;
    }

    public void recordUsedSkill(SkillState state) {
        lastSkill2 = lastSkill1;
        lastSkill1 = state;
    }

    public int getGlobalSkillRestTicks() { return globalSkillRestTicks; }

    public float skillDamageScale() {
        return (this.phase >= 2) ? (float) CerberusConfig.get().phase2StatMultiplier : 1.0f;
    }

    public int safeTelegraph(int baseTicks) {
        return computeSafeTelegraph(baseTicks, CerberusConfig.get().minTelegraphTicks);
    }

    public void finishSkill(int restMinTicks, boolean releaseHeavyToken, boolean releaseUltLock) {
        setAllHeadsAnimationLocked(false);
        setShadowCloaked(false);
        setSkillState(SkillState.IDLE);
        this.packHuntSlotIndex = -1;
        this.packHuntDelayTicks = 0;

        EncounterGroup group = getGroup();
        if (group != null) {
            if (releaseHeavyToken) group.releaseHeavyToken(this.getUUID());
            if (releaseUltLock) group.releaseUltimateLock(this.getUUID());
        }
        // Nghỉ giữa các skill rút ngắn (0.6-1.1s) để tăng tần suất tấn công
        int baseRest = Math.max(12, Math.round(restMinTicks * 0.65f));
        this.globalSkillRestTicks = baseRest + this.getRandom().nextInt(11);
    }

    public void logCerberusDebug(String tag, int ticksLeft) {
        boolean force = isDebugLoggingEnabled();
        if (!force && !AbyssalRpgMod.LOGGER.isDebugEnabled()) return;
        logGeoRotationDebug(tag + ":" + getCerberusForm().name(), ticksLeft, force);
        EncounterGroup grp = getGroup();
        if (grp != null && force) {
            AbyssalRpgMod.LOGGER.info("[{}#{}] form={} splitStage={} skill={} hasToken={} {}",
                    tag, this.getId(), getCerberusForm(), getSplitStage(), getSkillState(),
                    grp.hasHeavyToken(this.getUUID()), grp.formatDebugSummary());
        }
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level() instanceof ServerLevel sl) {
            if (this.deathTime % 6 == 0) {
                spawnRoleBurstParticles(sl, this.position().add(0, 1.5 * getEffectiveScale(), 0), getCerberusForm(), 10);
            }
            if (this.deathTime == CUSTOM_DEATH_DURATION - 8) {
                ShockwaveRingEntity.spawn(sl, this.position(), 11.0f * getEffectiveScale(), getCerberusForm().accentRgb(), 18);
                ModNetworking.sendCerberusVfxNearby(sl, CerberusVfxPayload.KIND_DEATH_CLIMAX,
                        this.position(), Vec3.ZERO, 4.5f * getEffectiveScale(), (byte) getCerberusForm().id());
            }
            if (this.deathTime >= CUSTOM_DEATH_DURATION && !this.isRemoved()) {
                this.level().broadcastEntityEvent(this, (byte) 60);
                this.remove(Entity.RemovalReason.KILLED);
            }
        }
    }

    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        // NO-OP trên Server: chóp lửa 3 cánh fullbright + tàn lửa idle cực nhẹ được render 100% phía Client (CerberusGeoRenderer)!
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.CERBERUS_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.RAVAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.CERBERUS_DEATH; }

    // =========================================================================
    // LƯU & KHÔI PHỤC NBT (FORM, GROUP ID, PHASE 2, MODIFIER, SPLIT TARGETS)
    // =========================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saveSharedGeoBossData(tag);
        tag.putInt("CerberusForm", getCerberusForm().id());
        tag.putBoolean("CerberusHasSplit", this.hasSplit());
        tag.putInt("CerberusSplitStage", getSplitStage());
        tag.putBoolean("CerberusSplitSpawned", this.splitChildrenSpawned);
        tag.putUUID("CerberusGroupId", this.groupId);
        tag.putInt("CerberusGlobalRest", this.globalSkillRestTicks);
        tag.putDouble("SplitOrigX", splitOriginPos.x);
        tag.putDouble("SplitOrigY", splitOriginPos.y);
        tag.putDouble("SplitOrigZ", splitOriginPos.z);
        for (int i = 0; i < 3; i++) {
            tag.putDouble("SplitTgtX" + i, splitLandingTargets[i].x);
            tag.putDouble("SplitTgtY" + i, splitLandingTargets[i].y);
            tag.putDouble("SplitTgtZ" + i, splitLandingTargets[i].z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadSharedGeoBossData(tag, null, null, null, null, null, null);

        if (tag.hasUUID("CerberusGroupId")) {
            this.groupId = tag.getUUID("CerberusGroupId");
        }
        this.entityData.set(DATA_GROUP_ID, this.groupId.toString());

        CerberusForm savedForm = CerberusForm.byId(tag.getInt("CerberusForm"));
        this.hasSplit = tag.getBoolean("CerberusHasSplit") || savedForm.isSplitSingle();
        this.entityData.set(DATA_HAS_SPLIT, this.hasSplit);
        this.entityData.set(DATA_SPLIT_STAGE, Math.max(0, tag.getInt("CerberusSplitStage")));
        this.splitChildrenSpawned = tag.getBoolean("CerberusSplitSpawned");
        if (tag.contains("CerberusGlobalRest")) this.globalSkillRestTicks = tag.getInt("CerberusGlobalRest");

        if (tag.contains("SplitOrigX")) {
            this.splitOriginPos = new Vec3(tag.getDouble("SplitOrigX"), tag.getDouble("SplitOrigY"), tag.getDouble("SplitOrigZ"));
        }
        for (int i = 0; i < 3; i++) {
            if (tag.contains("SplitTgtX" + i)) {
                this.splitLandingTargets[i] = new Vec3(
                        tag.getDouble("SplitTgtX" + i), tag.getDouble("SplitTgtY" + i), tag.getDouble("SplitTgtZ" + i));
            }
        }

        setCerberusForm(savedForm);
        if (this.hasEnteredPhase2 || this.hasSplit) {
            this.phase = 2;
            applyPhase2Modifiers(); // Idempotent: không cộng dồn khi reload world
            if (getPhaseTransitionTicks() > 0) {
                setSkillState(SkillState.PHASE_CHANGE);
                setAllHeadsAnimationLocked(true);
            }
        }
        if (this.level() instanceof ServerLevel sl) {
            EncounterGroup grp = CerberusGroupManager.get(sl.getServer()).getOrCreateGroup(this.groupId);
            grp.registerMember(this);
            if (this.hasSplit && grp.getHealthMode() == CerberusConfig.SplitHealthMode.SHARED_POOL) {
                syncSharedPoolHealth(grp.getSharedPoolHp(), grp.getSharedPoolMaxHp());
            }
        }
        snapSyncPrevAngles();
    }
}

