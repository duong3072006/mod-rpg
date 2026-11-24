package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.config.SpiritHydraConfig;
import com.abyssalrpg.entity.mythic.hydra.HydraAttackTiming;
import com.abyssalrpg.entity.mythic.hydra.HydraAttackTiming.HitWindow;
import com.abyssalrpg.entity.mythic.hydra.HydraAttackTiming.SkillTimeline;
import com.abyssalrpg.entity.mythic.hydra.HydraHitboxPartEntity;
import com.abyssalrpg.entity.vfx.AbyssalOrbEntity;
import com.abyssalrpg.entity.vfx.EnergySlashEntity;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.SeveredHydraHeadEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.HydraVfxPayload;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModItems;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BOSS SPIRIT HYDRA v2 (`abyssalrpg:spirit_hydra`, GeckoLib 4.8.3):
 *
 * 1. Đúng 4 đầu (`HEAD_COUNT = 4`: `outer_l`, `inner_l`, `inner_r`, `outer_r`), máu Phase 1 = `2000 HP`
 *    chia đều chính xác `500 HP / đầu` (`headHp[0..3]` đồng bộ qua `DataTracker` + lưu/tải đầy đủ trong NBT).
 *    Tổng HP thực của boss luôn đồng bộ tuyệt đối với `sum(headHp)`.
 * 2. Hệ thống Multipart Proxy Hitbox (`HydraHitboxPartEntity`):
 *    - 4 proxy Đầu (`head_1..4`) + 4 proxy Cổ (`neck_1..4`) bám đúng vị trí 4 cổ/đầu quạt rộng:
 *      đánh trúng đầu/cổ nào trừ thẳng vào `headHp[i]` của đúng đầu đó (`x1.15`). Hết 500 HP -> chặt ĐÚNG đầu đó
 *      (ẩn `neck_i/head_i`, lộ `stump_i` phát khói hồn, rơi `SeveredHydraHeadEntity` + `Spirit Hydra Fang`, stagger ngắn).
 *    - 3 proxy Thân/Chân/Đuôi (`chest_legs`, `pelvis_legs`, `tail`) + đánh trực tiếp thân chính:
 *      trừ vào đầu còn sống có máu thấp nhất hiện tại (không bao giờ soft-lock, mỗi mốc 500 HP vẫn chặt đều từng đầu).
 *    - Mỗi đầu bị chặt tăng `+8%` tốc độ ra đòn cho các đầu còn lại (config được).
 * 3. Phase 2 "Cuồng bạo" cận tử (`<= 5%` = `100 HP` hoặc `<= 1 HP`):
 *    - Chặn cái chết -> `5.0s` bất tử -> hồi mượt lên **`3000 HP`** (`750 HP/đầu`, hệ số `x1.5`) và
 *      **MỌC LẠI CẢ 4 ĐẦU** tuần tự cách nhau đúng `0.35s` (`7 ticks`), giáp `15 -> 22.5`, sát thương nặng `30 -> 45`,
 *      mặc định tắt chặt đầu ở Phase 2 (`allowHeadCutInPhase2 = false`).
 * 4. Đồng bộ 0-tick tuyệt đối với `HydraAttackTiming` (`transitionLength = 1–2 ticks`), chống xoay đầu (`wrapDegrees` +
 *    clamp từng đầu + tắt head-tracking trên đầu đang ra chiêu), VFX gọn nhẹ qua `HydraVfxPayload` S2C (0 spam particle server).
 */
public class HydraBossEntity extends AbstractGeoMythicBoss {
    // =========================================================================
    // HẰNG SỐ KÍCH THƯỚC & CẤU TRÚC BONE 4 ĐẦU (ĐỒNG BỘ 1:1 VỚI spirit_hydra.geo.json)
    // =========================================================================
    public static final float SCALE = 2.5f;
    public static final float BASE_RENDER_SCALE = 1.20f; // 1.20 * 2.5 * 1.15 = 3.45f -> dài ~32.3 block (nhỉnh hơn Leviathan 31.8 block)
    public static final float BASE_WIDTH = 2.4f;
    public static final float BASE_HEIGHT = 1.9f;
    public static final float BASE_EYE_HEIGHT = 2.1f;

    /** Đúng 4 đầu tách biệt hoàn toàn: 0=outer_l, 1=inner_l, 2=inner_r, 3=outer_r. */
    public static final int HEAD_COUNT = 4;
    public static final int FULL_HEADS_MASK = (1 << HEAD_COUNT) - 1; // 0xF

    public static final String BODY_BONE = "body";
    public static final String CHEST_BONE = "chest";
    public static final String TORSO_BONE = "torso";
    public static final String PELVIS_BONE = "pelvis";

    public static final String[] HEAD_BONES = { "head_1", "head_2", "head_3", "head_4" };
    public static final String[] NECK_BONES = { "neck_1", "neck_2", "neck_3", "neck_4" };
    public static final String[] STUMP_BONES = { "stump_1", "stump_2", "stump_3", "stump_4" };
    public static final String[] JAW_BONES = { "jaw_1", "jaw_2", "jaw_3", "jaw_4" };
    public static final String[] HEAD_ROLE_LABELS = { "Ngoài Trái", "Giữa Trái", "Giữa Phải", "Ngoài Phải" };

    /**
     * Tọa độ tâm miệng 4 đầu trong không gian Bedrock model (1/16 block, trước khi nhân `getTotalRenderScale()`),
     * trích xuất chính xác từ `tools/generate_spirit_hydra_model.py` (`HEAD_MOUTH_PIVOTS`).
     */
    public static final float[][] HEAD_MODEL_MOUTH_PIVOTS = {
            {  51.19f, 21.95f, -46.27f }, // head_1 (outer_l)
            {  21.72f, 39.98f, -61.06f }, // head_2 (inner_l)
            { -21.72f, 39.98f, -61.06f }, // head_3 (inner_r)
            { -51.19f, 21.95f, -46.27f }  // head_4 (outer_r)
    };

    /** Tọa độ giữa 4 cổ trong không gian model cho 4 proxy cổ (giúp cận chiến chặt từng đầu cực mượt). */
    public static final float[][] NECK_MODEL_MID_PIVOTS = {
            {  30.5f, 20.5f, -26.5f }, // neck_1 (outer_l)
            {  13.5f, 28.5f, -31.5f }, // neck_2 (inner_l)
            { -13.5f, 28.5f, -31.5f }, // neck_3 (inner_r)
            { -30.5f, 20.5f, -26.5f }  // neck_4 (outer_r)
    };

    /** Tọa độ gốc cổ tại mặt ngực trước (vị trí `stump_1..4`, nơi sinh vết chặt & đầu mọc lại). */
    public static final float[][] NECK_MODEL_ROOT_PIVOTS = {
            {  9.4f, 14.5f, -10.5f },
            {  4.8f, 17.0f, -13.0f },
            { -4.8f, 17.0f, -13.0f },
            { -9.4f, 14.5f, -10.5f }
    };

    /** Góc quạt nghỉ tự nhiên (độ) của 4 đầu (trái -> phải) phục vụ debug log & đầu rơi cosmetic. */
    public static final float[] HEAD_BASE_FAN_YAW_DEG = { -34.0f, -13.0f, +13.0f, +34.0f };

    /** Bảng định nghĩa 11 Proxy Hitbox (4 Đầu + 4 Cổ gắn `headIndex 0..3`, + 3 Thân/Chân/Đuôi `headIndex -1`). */
    public record PartSpec(String name, int headIndex, float modelX, float modelY, float modelZ,
                           float baseWidth, float baseHeight, float yOffsetDown) {}

    public static final PartSpec[] MULTIPART_SPECS = {
            // 4 proxy Đầu (tâm ở miệng/hộp sọ)
            new PartSpec("head_1", 0,  51.19f, 21.95f, -46.27f, 3.6f, 3.6f, 1.8f),
            new PartSpec("head_2", 1,  21.72f, 39.98f, -61.06f, 3.6f, 3.6f, 1.8f),
            new PartSpec("head_3", 2, -21.72f, 39.98f, -61.06f, 3.6f, 3.6f, 1.8f),
            new PartSpec("head_4", 3, -51.19f, 21.95f, -46.27f, 3.6f, 3.6f, 1.8f),
            // 4 proxy Cổ (vươn thấp tới tầm đánh cận chiến để đánh chính xác từng cổ/đầu từ mặt đất)
            new PartSpec("neck_1", 0,  30.50f, 18.00f, -26.50f, 2.7f, 3.8f, 2.1f),
            new PartSpec("neck_2", 1,  13.50f, 24.00f, -31.50f, 2.6f, 4.4f, 2.8f),
            new PartSpec("neck_3", 2, -13.50f, 24.00f, -31.50f, 2.6f, 4.4f, 2.8f),
            new PartSpec("neck_4", 3, -30.50f, 18.00f, -26.50f, 2.7f, 3.8f, 2.1f),
            // 3 proxy Thân, 4 Chân & Đuôi (đánh vào chuyển sát thương vào đầu còn sống thấp máu nhất)
            new PartSpec("chest_legs",  -1,  0.0f, 10.0f,  -4.0f, 5.4f, 4.0f, 2.0f),
            new PartSpec("pelvis_legs", -1,  0.0f, 10.5f,  28.5f, 5.0f, 3.8f, 1.9f),
            new PartSpec("tail",        -1,  0.0f, 24.0f,  66.0f, 3.6f, 6.2f, 2.4f)
    };

    // --- Màu hiệu ứng Spirit Hydra (P1: Xanh Băng Linh Hồn / P2: Trắng-Xanh Điện & Tím Lạnh) ---
    private static final int COLOR_SPIRIT_CYAN = 0x4DF4FF;
    private static final int COLOR_SOUL_SKY = 0x0EA5E9;
    private static final int COLOR_ENRAGED_ICE = 0x92F4FF;
    private static final int COLOR_ENRAGED_VIOLET = 0xB46BFF;

    // --- EntityAttributeModifier Phase 2 cố định (idempotent) ---
    private static final ResourceLocation LEGACY_PHASE2_HEALTH_MOD_ID = AbyssalRpgMod.id("spirit_hydra_phase2_health");
    private static final ResourceLocation PHASE2_ARMOR_MOD_ID = AbyssalRpgMod.id("spirit_hydra_phase2_armor");
    private static final ResourceLocation PHASE2_DAMAGE_MOD_ID = AbyssalRpgMod.id("spirit_hydra_phase2_damage");
    private static final ResourceLocation PHASE2_SPEED_MOD_ID = AbyssalRpgMod.id("spirit_hydra_phase2_speed");

    // --- Khai báo RawAnimation GeckoLib (khớp 1:1 với spirit_hydra.animation.json) ---
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.spirit_hydra.idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("animation.spirit_hydra.walk");
    private static final RawAnimation ANIM_DEATH = RawAnimation.begin().thenPlayAndHold("animation.spirit_hydra.death");

    private static final RawAnimation[] ANIM_BITES = new RawAnimation[HEAD_COUNT];
    static {
        for (int i = 0; i < HEAD_COUNT; i++) {
            ANIM_BITES[i] = RawAnimation.begin().thenPlay("animation.spirit_hydra.bite_" + (i + 1));
        }
    }
    private static final RawAnimation ANIM_TRIPLE_BITE = RawAnimation.begin().thenPlay("animation.spirit_hydra.triple_bite");
    private static final RawAnimation ANIM_BREATH = RawAnimation.begin().thenPlay("animation.spirit_hydra.breath");
    private static final RawAnimation ANIM_SPIT = RawAnimation.begin().thenPlay("animation.spirit_hydra.spit");
    private static final RawAnimation ANIM_SLAM = RawAnimation.begin().thenPlay("animation.spirit_hydra.slam");
    private static final RawAnimation ANIM_SUMMON_WISPS = RawAnimation.begin().thenPlay("animation.spirit_hydra.summon_wisps");
    private static final RawAnimation ANIM_STAGGER = RawAnimation.begin().thenPlay("animation.spirit_hydra.stagger");
    private static final RawAnimation ANIM_HEAD_CUT = RawAnimation.begin().thenPlay("animation.spirit_hydra.head_cut");
    private static final RawAnimation ANIM_HEAD_REGROW = RawAnimation.begin().thenPlay("animation.spirit_hydra.head_regrow");
    private static final RawAnimation ANIM_ALL_HEAD_BARRAGE = RawAnimation.begin().thenPlay("animation.spirit_hydra.all_head_barrage");
    private static final RawAnimation ANIM_SPIRIT_NOVA = RawAnimation.begin().thenPlay("animation.spirit_hydra.spirit_nova");
    private static final RawAnimation ANIM_SOUL_CHAIN = RawAnimation.begin().thenPlay("animation.spirit_hydra.soul_chain");
    private static final RawAnimation ANIM_WRATH = RawAnimation.begin().thenPlay("animation.spirit_hydra.wrath_of_many_heads");
    private static final RawAnimation ANIM_PHASE_CHANGE = RawAnimation.begin().thenPlay("animation.spirit_hydra.phase_change");

    public enum SkillState {
        IDLE,
        TRIPLE_BITE,
        SPIRIT_BREATH,
        SOUL_SPIT,
        TAIL_BODY_SLAM,
        SUMMON_WISPS,
        ALL_HEAD_BARRAGE,
        SPIRIT_NOVA,
        SOUL_CHAIN,
        WRATH_OF_MANY_HEADS,
        HEAD_STAGGER,
        PHASE_CHANGE,
        DEATH;

        public static SkillState byId(int id) {
            SkillState[] vals = values();
            return (id >= 0 && id < vals.length) ? vals[id] : IDLE;
        }
    }

    // --- DataTracker đồng bộ Server <-> Client (bao gồm 4 bể máu riêng 500 HP/đầu & tốc độ anim 0-tick!) ---
    private static final EntityDataAccessor<Integer> DATA_SKILL_STATE =
            SynchedEntityData.defineId(HydraBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ALIVE_HEADS_MASK =
            SynchedEntityData.defineId(HydraBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANIM_LOCKED_HEADS_MASK =
            SynchedEntityData.defineId(HydraBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STAGGER_TICKS =
            SynchedEntityData.defineId(HydraBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTIVE_BITE_HEAD =
            SynchedEntityData.defineId(HydraBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ACTION_ANIM_SPEED =
            SynchedEntityData.defineId(HydraBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEAD_SLOT_ANIM_SPEED =
            SynchedEntityData.defineId(HydraBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEAD_HP_0 =
            SynchedEntityData.defineId(HydraBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEAD_HP_1 =
            SynchedEntityData.defineId(HydraBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEAD_HP_2 =
            SynchedEntityData.defineId(HydraBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEAD_HP_3 =
            SynchedEntityData.defineId(HydraBossEntity.class, EntityDataSerializers.FLOAT);

    public static final int CUSTOM_DEATH_DURATION = 60; // 3.00s khớp `death` animation
    private static volatile boolean debugLoggingEnabled = false;

    // --- Cache vị trí thật bone đầu trên Client (cập nhật từ GeckoLib GeoBone.getWorldPosition()) ---
    private final Vec3[] clientHeadWorldPositions = new Vec3[HEAD_COUNT];
    private final float[] lastRenderedHeadYawDeg = new float[HEAD_COUNT];
    private final float[] lastRenderedHeadPitchDeg = new float[HEAD_COUNT];

    // --- Danh sách 11 Multipart Proxy Hitboxes ---
    private final List<HydraHitboxPartEntity> hitboxParts = new ArrayList<>();
    private long lastPartHitGameTick = -1L;
    @Nullable
    private UUID lastPartHitAttacker = null;
    private int pendingTargetHeadIndex = -1;

    // --- Hàng đợi chặt đầu chính xác theo từng đầu `headIdx` (0.3s giữa 2 đầu nếu 1 đòn lớn hạ nhiều đầu) ---
    private final Deque<Integer> pendingSeverHeadQueue = new ArrayDeque<>();
    private int severDelayTicks = 0;
    private int regrowCursor = 0;

    // --- Slot tấn công độc lập từng đầu đồng bộ 0-tick với `HydraAttackTiming.HEAD_BITE_WINDOW` ---
    private final int[] headSlotCooldowns = new int[HEAD_COUNT];
    private final double[] headSlotAnimTick = new double[HEAD_COUNT];
    private final boolean[] headSlotActive = new boolean[HEAD_COUNT];
    private final boolean[] headSlotIsRanged = new boolean[HEAD_COUNT];
    private final boolean[] headSlotImpactDone = new boolean[HEAD_COUNT];

    // --- Cooldown 9 skill, chống lặp 3 lần & giới hạn oan hồn Vex ---
    private final Map<SkillState, Integer> skillCooldowns = new EnumMap<>(SkillState.class);
    private final Set<UUID> activeWisps = new HashSet<>();
    private SkillState lastSkill1 = SkillState.IDLE;
    private SkillState lastSkill2 = SkillState.IDLE;
    private int globalSkillRestTicks = 28;

    @SuppressWarnings("this-escape")
    public HydraBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        this.xpReward = 260;
        this.noCulling = true;
        for (SkillState s : SkillState.values()) {
            skillCooldowns.put(s, 0);
        }
        for (int i = 0; i < HEAD_COUNT; i++) {
            headSlotCooldowns[i] = 28 + i * 16;
        }
        float p1Max = (float) SpiritHydraConfig.get().phase1MaxHealth;
        initializeBossHealth(p1Max);
        initAllHeadPools(p1Max / HEAD_COUNT);
    }

    public static boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled || SpiritHydraConfig.get().debugRotationLogs;
    }

    public static void setDebugLoggingEnabled(boolean enabled) {
        debugLoggingEnabled = enabled;
    }

    /** Hệ số render tổng (mặc định `1.20 * 2.5 * 1.15 = 3.45f`), khớp chiều dài ~32.3 block ngang/nhỉnh Leviathan. */
    public float getTotalRenderScale() {
        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        return BASE_RENDER_SCALE * cfg.scale * cfg.visualScaleMultiplier;
    }

    /** Tỷ lệ kích thước hiện tại so với chuẩn `SCALE = 2.5f` (dùng đồng bộ tầm đánh, proxy hitbox & culling). */
    public float getEffectiveScale() {
        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        return cfg.scale * cfg.visualScaleMultiplier;
    }

    public float getScaleRatio() {
        return getEffectiveScale() / SCALE;
    }

    // =========================================================================
    // GECKOLIB 4.8.3 ANIMATION CONTROLLERS (transitionLength = 1–2 TICKS CHO ĐÒN ĐÁNH!)
    // =========================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Controller 1: idle / walk / death
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (!this.isAlive() || this.deathTime > 0 || getSkillState() == SkillState.DEATH) {
                return state.setAndContinue(ANIM_DEATH);
            }
            if (isTransitioningPhase() || isHeadStaggering()) {
                return state.setAndContinue(ANIM_IDLE);
            }
            if (state.isMoving() || this.getDeltaMovement().horizontalDistanceSqr() > 0.0020) {
                return state.setAndContinue(ANIM_WALK);
            }
            return state.setAndContinue(ANIM_IDLE);
        }));

        // Controller 2: toàn bộ kỹ năng, stagger, chặt đầu, mọc đầu & chuyển phase (`transitionLength = 2 ticks` chống trễ!)
        AnimationController<HydraBossEntity> actionCtrl = new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .setAnimationSpeedHandler(anim -> (double) anim.getSyncedActionAnimSpeed())
                .triggerableAnim("triple_bite", ANIM_TRIPLE_BITE)
                .triggerableAnim("breath", ANIM_BREATH)
                .triggerableAnim("spit", ANIM_SPIT)
                .triggerableAnim("slam", ANIM_SLAM)
                .triggerableAnim("summon_wisps", ANIM_SUMMON_WISPS)
                .triggerableAnim("stagger", ANIM_STAGGER)
                .triggerableAnim("head_cut", ANIM_HEAD_CUT)
                .triggerableAnim("head_regrow", ANIM_HEAD_REGROW)
                .triggerableAnim("all_head_barrage", ANIM_ALL_HEAD_BARRAGE)
                .triggerableAnim("spirit_nova", ANIM_SPIRIT_NOVA)
                .triggerableAnim("soul_chain", ANIM_SOUL_CHAIN)
                .triggerableAnim("wrath_of_many_heads", ANIM_WRATH)
                .triggerableAnim("phase_change", ANIM_PHASE_CHANGE);
        for (int i = 0; i < HEAD_COUNT; i++) {
            actionCtrl.triggerableAnim("bite_" + (i + 1), ANIM_BITES[i]);
        }
        controllers.add(actionCtrl);

        // Controller 3: đòn cắn độc lập từng đầu (`transitionLength = 1 tick` ăn sát thương ngay khung hình cắn!)
        AnimationController<HydraBossEntity> headSlotCtrl = new AnimationController<>(this, "head_slot", 1, state -> PlayState.STOP)
                .setAnimationSpeedHandler(anim -> (double) anim.getSyncedHeadSlotAnimSpeed());
        for (int i = 0; i < HEAD_COUNT; i++) {
            headSlotCtrl.triggerableAnim("bite_" + (i + 1), ANIM_BITES[i]);
        }
        controllers.add(headSlotCtrl);
    }

    public void playActionAnim(String key, float speed) {
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_ACTION_ANIM_SPEED, Mth.clamp(speed, 0.5f, 2.0f));
            this.triggerAnim("action", key);
        }
    }

    public void playActionAnim(String key) {
        playActionAnim(key, 1.0f);
    }

    public void playHeadSlotBiteAnim(int headIdx, float speed) {
        if (!this.level().isClientSide && headIdx >= 0 && headIdx < HEAD_COUNT) {
            this.entityData.set(DATA_ACTIVE_BITE_HEAD, headIdx);
            this.entityData.set(DATA_HEAD_SLOT_ANIM_SPEED, Mth.clamp(speed, 0.5f, 2.0f));
            this.triggerAnim("head_slot", "bite_" + (headIdx + 1));
        }
    }

    public float getSyncedActionAnimSpeed() {
        return this.entityData.get(DATA_ACTION_ANIM_SPEED);
    }

    public float getSyncedHeadSlotAnimSpeed() {
        return this.entityData.get(DATA_HEAD_SLOT_ANIM_SPEED);
    }

    // =========================================================================
    // DATA TRACKER: TRẠNG THÁI 4 ĐẦU & 4 BỂ MÁU RIÊNG (`4 x 500 = 2000 HP`)
    // =========================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKILL_STATE, SkillState.IDLE.ordinal());
        builder.define(DATA_ALIVE_HEADS_MASK, FULL_HEADS_MASK);
        builder.define(DATA_ANIM_LOCKED_HEADS_MASK, 0);
        builder.define(DATA_STAGGER_TICKS, 0);
        builder.define(DATA_ACTIVE_BITE_HEAD, -1);
        builder.define(DATA_ACTION_ANIM_SPEED, 1.0f);
        builder.define(DATA_HEAD_SLOT_ANIM_SPEED, 1.0f);
        float initPerHead = (float) (SpiritHydraConfig.get().phase1MaxHealth / HEAD_COUNT);
        builder.define(DATA_HEAD_HP_0, initPerHead);
        builder.define(DATA_HEAD_HP_1, initPerHead);
        builder.define(DATA_HEAD_HP_2, initPerHead);
        builder.define(DATA_HEAD_HP_3, initPerHead);
    }

    private EntityDataAccessor<Float> headHpAccessor(int idx) {
        return switch (idx) {
            case 0 -> DATA_HEAD_HP_0;
            case 1 -> DATA_HEAD_HP_1;
            case 2 -> DATA_HEAD_HP_2;
            default -> DATA_HEAD_HP_3;
        };
    }

    public float getHeadHp(int headIdx) {
        if (headIdx < 0 || headIdx >= HEAD_COUNT) return 0.0f;
        return Math.max(0.0f, this.entityData.get(headHpAccessor(headIdx)));
    }

    public void setHeadHp(int headIdx, float hp) {
        if (headIdx < 0 || headIdx >= HEAD_COUNT) return;
        float cap = Math.max(1.0f, getPerHeadMaxHealth());
        float clamped = Float.isFinite(hp) ? Mth.clamp(hp, 0.0f, cap) : 0.0f;
        this.entityData.set(headHpAccessor(headIdx), clamped);
    }

    public void initAllHeadPools(float perHeadHp) {
        float val = Math.max(0.0f, perHeadHp);
        for (int i = 0; i < HEAD_COUNT; i++) {
            this.entityData.set(headHpAccessor(i), val);
        }
    }

    public float getPerHeadMaxHealth() {
        float max = getBossMaxHealth() > 0.0f ? getBossMaxHealth() : this.getMaxHealth();
        return Math.max(1.0f, max / HEAD_COUNT);
    }

    public float sumHeadHp() {
        return getHeadHp(0) + getHeadHp(1) + getHeadHp(2) + getHeadHp(3);
    }

    public SkillState getSkillState() {
        return SkillState.byId(this.entityData.get(DATA_SKILL_STATE));
    }

    public void setSkillState(SkillState state) {
        this.entityData.set(DATA_SKILL_STATE, state.ordinal());
    }

    public int getAliveHeadsMask() {
        return this.entityData.get(DATA_ALIVE_HEADS_MASK) & FULL_HEADS_MASK;
    }

    public void setAliveHeadsMask(int mask) {
        int clamped = mask & FULL_HEADS_MASK;
        if (clamped == 0) clamped = 1; // Bảo hiểm luôn giữ tối thiểu 1 đầu khi còn sống
        this.entityData.set(DATA_ALIVE_HEADS_MASK, clamped);
    }

    public boolean isHeadAlive(int headIdx) {
        if (headIdx < 0 || headIdx >= HEAD_COUNT) return false;
        return (getAliveHeadsMask() & (1 << headIdx)) != 0;
    }

    public int getAliveHeadCount() {
        return Integer.bitCount(getAliveHeadsMask());
    }

    public int getSeveredHeadCount() {
        return Math.max(0, HEAD_COUNT - getAliveHeadCount());
    }

    public List<Integer> getAliveHeadIndices() {
        List<Integer> list = new ArrayList<>(HEAD_COUNT);
        int mask = getAliveHeadsMask();
        for (int i = 0; i < HEAD_COUNT; i++) {
            if ((mask & (1 << i)) != 0) list.add(i);
        }
        return list;
    }

    public List<Integer> getSeveredHeadIndices() {
        List<Integer> list = new ArrayList<>(HEAD_COUNT);
        int mask = getAliveHeadsMask();
        for (int i = 0; i < HEAD_COUNT; i++) {
            if ((mask & (1 << i)) == 0) list.add(i);
        }
        return list;
    }

    public List<HydraHitboxPartEntity> getActiveHitboxParts() {
        return this.hitboxParts;
    }

    public int getStaggerTicks() {
        return this.entityData.get(DATA_STAGGER_TICKS);
    }

    public boolean isHeadStaggering() {
        return this.entityData.get(DATA_STAGGER_TICKS) > 0 || getSkillState() == SkillState.HEAD_STAGGER;
    }

    @Override
    public boolean isTransitioningPhase() {
        return super.isTransitioningPhase() || getSkillState() == SkillState.PHASE_CHANGE;
    }

    @Override
    public boolean isRotationLocked() {
        return super.isRotationLocked() || this.isHeadStaggering();
    }

    /**
     * Tắt head-tracking của ĐÚNG đầu đang chạy animation skill/cắn/phun/chặt/mọc/chuyển phase/chết,
     * các đầu còn lại vẫn theo dõi mục tiêu mượt mà với góc kẹp cứng.
     */
    public boolean isHeadLockedByAnimation(int headIdx) {
        if (!this.isAlive() || this.deathTime > 0 || this.isSpawningIn()
                || this.isTransitioningPhase() || this.isHeadStaggering()) {
            return true;
        }
        SkillState st = getSkillState();
        if (st == SkillState.PHASE_CHANGE || st == SkillState.HEAD_STAGGER
                || st == SkillState.SPIRIT_NOVA || st == SkillState.ALL_HEAD_BARRAGE
                || st == SkillState.WRATH_OF_MANY_HEADS || st == SkillState.TAIL_BODY_SLAM
                || st == SkillState.SUMMON_WISPS || st == SkillState.DEATH) {
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

    // =========================================================================
    // VỊ TRÍ THẾ GIỚI THẬT CỦA 4 ĐẦU & GỐC CỔ + ĐỒNG BỘ 11 PROXY HITBOX
    // =========================================================================
    public void updateClientHeadWorldPosition(int headIdx, double wx, double wy, double wz) {
        if (headIdx >= 0 && headIdx < HEAD_COUNT) {
            this.clientHeadWorldPositions[headIdx] = new Vec3(wx, wy, wz);
        }
    }

    public void recordClientHeadDebugAngles(int headIdx, float yawDeg, float pitchDeg) {
        if (headIdx >= 0 && headIdx < HEAD_COUNT) {
            this.lastRenderedHeadYawDeg[headIdx] = yawDeg;
            this.lastRenderedHeadPitchDeg[headIdx] = pitchDeg;
        }
    }

    public Vec3 getHeadWorldPosition(int headIdx) {
        int idx = Mth.clamp(headIdx, 0, HEAD_COUNT - 1);
        if (this.level().isClientSide && clientHeadWorldPositions[idx] != null) {
            return clientHeadWorldPositions[idx];
        }
        float[] pivot = HEAD_MODEL_MOUTH_PIVOTS[idx];
        float phase = idx * 1.57f;
        float swayX = Mth.sin(this.tickCount * 0.08f + phase) * 1.4f;
        float swayY = Mth.cos(this.tickCount * 0.10f + phase) * 0.9f;
        return modelPointToWorld(pivot[0] + swayX, pivot[1] + swayY, pivot[2], getTotalRenderScale());
    }

    public Vec3 getNeckRootWorldPosition(int headIdx) {
        int idx = Mth.clamp(headIdx, 0, HEAD_COUNT - 1);
        float[] pivot = NECK_MODEL_ROOT_PIVOTS[idx];
        return modelPointToWorld(pivot[0], pivot[1], pivot[2], getTotalRenderScale());
    }

    private void ensureAndSyncHitboxParts(ServerLevel level) {
        float renderScale = getTotalRenderScale();
        float ratio = getScaleRatio();

        if (this.hitboxParts.size() != MULTIPART_SPECS.length) {
            discardAllHitboxParts();
            for (PartSpec spec : MULTIPART_SPECS) {
                HydraHitboxPartEntity part = HydraHitboxPartEntity.create(
                        level, this, spec.name(), spec.headIndex(),
                        spec.baseWidth() * ratio, spec.baseHeight() * ratio);
                this.hitboxParts.add(part);
            }
        }

        for (int i = 0; i < MULTIPART_SPECS.length; i++) {
            PartSpec spec = MULTIPART_SPECS[i];
            HydraHitboxPartEntity part = this.hitboxParts.get(i);
            if (part == null || part.isRemoved()) {
                part = HydraHitboxPartEntity.create(
                        level, this, spec.name(), spec.headIndex(),
                        spec.baseWidth() * ratio, spec.baseHeight() * ratio);
                this.hitboxParts.set(i, part);
            }

            boolean active = !spec.name().isEmpty()
                    && (spec.headIndex() < 0 || isHeadAlive(spec.headIndex()));
            part.setPartActive(active);
            if (!active) {
                // Cất gọn proxy của đầu đã bị chặt vào gốc cổ để không cản raycast người chơi
                Vec3 stump = getNeckRootWorldPosition(Math.max(0, spec.headIndex()));
                part.syncPositionFromBone(stump.x, stump.y, stump.z, this.yBodyRot);
                continue;
            }

            part.setPartDimensions(spec.baseWidth() * ratio, spec.baseHeight() * ratio);
            float mx = spec.modelX();
            float my = spec.modelY();
            float mz = spec.modelZ();
            // Khi đầu đang phóng cắn/phun, hạ nhẹ proxy đầu xuống gần tầm mục tiêu cho ăn đòn phản công cực đã!
            if (spec.headIndex() >= 0 && (headSlotActive[spec.headIndex()] || getSkillState() == SkillState.TRIPLE_BITE)) {
                my = Math.max(10.0f, my - 9.0f);
                mz -= 8.0f;
            }
            Vec3 wp = modelPointToWorld(mx, my, mz, renderScale);
            double bottomY = Math.max(this.getY(), wp.y - spec.yOffsetDown() * ratio);
            part.syncPositionFromBone(wp.x, bottomY, wp.z, this.yBodyRot);
        }
    }

    private void discardAllHitboxParts() {
        for (HydraHitboxPartEntity part : this.hitboxParts) {
            if (part != null && !part.isRemoved()) {
                part.discard();
            }
        }
        this.hitboxParts.clear();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        discardAllHitboxParts();
        super.remove(reason);
    }

    // =========================================================================
    // CULLING BOX, PATHFINDING, ATTRIBUTES & TIÊU ĐỀ BOSSBAR
    // =========================================================================
    @Override
    public AABB getBoundingBoxForCulling() {
        float eff = getEffectiveScale();
        return this.getBoundingBox().inflate(13.5 * eff, 8.0 * eff, 13.5 * eff);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        HydraPathNavigation nav = new HydraPathNavigation(this, level);
        nav.setCanFloat(true);
        nav.setMaxVisitedNodesMultiplier(4.0f);
        return nav;
    }

    @Override
    protected int getDefaultSpawnEggFloor() {
        return 15;
    }

    @Override
    protected Component getStandaloneBossTitle() {
        int alive = getAliveHeadCount();
        if (isEnragedVisual() || this.phase >= 2) {
            return Component.literal("🔥 SPIRIT HYDRA — CUỒNG BẠO (" + alive + "/" + HEAD_COUNT + " ĐẦU) 🔥")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        }
        return Component.literal("✦ SPIRIT HYDRA — Tứ Thủ Oan Hồn (" + alive + "/" + HEAD_COUNT + " Đầu) ✦")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, Math.min(VANILLA_SAFE_MAX_HEALTH_ATTR, cfg.phase1MaxHealth))
                .add(Attributes.ATTACK_DAMAGE, cfg.meleeDamage)
                .add(Attributes.ARMOR, cfg.armor)
                .add(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness)
                .add(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed)
                .add(Attributes.FOLLOW_RANGE, cfg.detectionRadius)
                .add(Attributes.STEP_HEIGHT, cfg.stepHeight)
                .add(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance);
    }

    private float computeTargetBossMaxHealth(boolean phase2) {
        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        return (float) (phase2 ? (cfg.phase1MaxHealth * cfg.phase2StatMultiplier) : cfg.phase1MaxHealth);
    }

    @Override
    public void configureForFloor(int floor, BlockPos arenaCenter, boolean dungeonBoss) {
        super.configureForFloor(floor, arenaCenter, dungeonBoss);
        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        setAttr(Attributes.MAX_HEALTH, Math.min(VANILLA_SAFE_MAX_HEALTH_ATTR, cfg.phase1MaxHealth));
        removeAttributeModifierIfPresent(Attributes.MAX_HEALTH, LEGACY_PHASE2_HEALTH_MOD_ID);
        setAttr(Attributes.ATTACK_DAMAGE, cfg.meleeDamage);
        setAttr(Attributes.ARMOR, cfg.armor);
        setAttr(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness);
        setAttr(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance);
        setAttr(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed);
        setAttr(Attributes.FOLLOW_RANGE, cfg.detectionRadius);
        setAttr(Attributes.STEP_HEIGHT, cfg.stepHeight);
        setAttr(Attributes.SCALE, 1.0);

        if (this.hasEnteredPhase2) {
            float p2Max = computeTargetBossMaxHealth(true);
            setBossMaxHealth(p2Max);
            initAllHeadPools(p2Max / HEAD_COUNT);
            setAliveHeadsMask(FULL_HEADS_MASK);
            applyPhase2Modifiers();
            this.setHealth(p2Max);
            this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        } else {
            float p1Max = computeTargetBossMaxHealth(false);
            initializeBossHealth(p1Max);
            initAllHeadPools(p1Max / HEAD_COUNT);
            setAliveHeadsMask(FULL_HEADS_MASK);
            this.bossEvent.setColor(BossEvent.BossBarColor.BLUE);
        }
        refreshBossTitle();

        float initYaw = wrapAngle(this.getYRot());
        this.entityData.set(DATA_LOCKED_YAW, initYaw);
        snapSyncPrevAngles();
    }

    private void refreshBossTitle() {
        Component title = getStandaloneBossTitle();
        if (title != null) {
            this.setCustomName(title);
            this.bossEvent.setName(title);
        }
    }

    // =========================================================================
    // ĐĂNG KÝ AI GOALS: PRIORITY 0 KHÓA GÓC + 9 SKILL (ĐỒNG BỘ HydraAttackTiming) + CHASE
    // =========================================================================
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new BossLockGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));

        this.goalSelector.addGoal(1, new TripleBiteGoal(this));
        this.goalSelector.addGoal(1, new SpiritBreathGoal(this));
        this.goalSelector.addGoal(1, new SoulSpitGoal(this));
        this.goalSelector.addGoal(1, new TailBodySlamGoal(this));
        this.goalSelector.addGoal(1, new SummonWispsGoal(this));

        this.goalSelector.addGoal(1, new AllHeadBarrageGoal(this));
        this.goalSelector.addGoal(1, new SpiritNovaGoal(this));
        this.goalSelector.addGoal(1, new SoulChainGoal(this));
        this.goalSelector.addGoal(1, new WrathOfManyHeadsGoal(this));

        this.goalSelector.addGoal(2, new HydraSkillSelectorGoal(this));
        this.goalSelector.addGoal(3, new HydraChaseMoveGoal(this));
        this.goalSelector.addGoal(7, new SafeLookAtPlayerGoal(this, 64.0f));
        this.goalSelector.addGoal(8, new SafeRandomLookGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // =========================================================================
    // CƠ CHẾ 2000 HP CHIA ĐỀU 4 ĐẦU (500 HP/ĐẦU) & ĐỊNH TUYẾN SÁT THƯƠNG MULTIPART
    // =========================================================================
    private float getPhase2TriggerThreshold() {
        return Math.max(1.0f, (float) (SpiritHydraConfig.get().phase1MaxHealth * SpiritHydraConfig.get().phase2TriggerHpRatio));
    }

    private boolean canSeverHeadsNow() {
        return !this.hasEnteredPhase2 || SpiritHydraConfig.get().allowHeadCutInPhase2;
    }

    /** Nhận đòn từ 1 trong 11 proxy hitbox: đánh đầu/cổ `i` nhân x1.15 trừ thẳng `headHp[i]`; đánh thân/đuôi trừ đầu thấp máu nhất. */
    public boolean hurtFromPart(HydraHitboxPartEntity part, DamageSource source, float amount) {
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

        int targetHead = part.isHeadOrNeckPart() ? part.getHeadIndex() : -1;
        if (targetHead >= 0 && !isHeadAlive(targetHead)) {
            return false;
        }

        float mult = (targetHead >= 0) ? (float) SpiritHydraConfig.get().headHitDamageMultiplier : 1.0f;
        float finalAmount = amount * mult;

        if (isDebugLoggingEnabled()) {
            AbyssalRpgMod.LOGGER.info("[SpiritHydra#{} PART_HIT] part='{}' headIdx={} mult={}x rawDmg={} -> {}",
                    this.getId(), part.getPartName(), targetHead, String.format("%.2f", mult),
                    String.format("%.2f", amount), String.format("%.2f", finalAmount));
        }

        this.pendingTargetHeadIndex = targetHead;
        try {
            return this.hurt(source, finalAmount);
        } finally {
            this.pendingTargetHeadIndex = -1;
        }
    }

    /** Cho phép test/command đánh trực tiếp vào đúng đầu `headIdx` (0..3) hoặc thân (`-1`). */
    public boolean hurtSpecificHead(int headIdx, DamageSource source, float amount) {
        int clamped = (headIdx >= 0 && headIdx < HEAD_COUNT) ? headIdx : -1;
        if (clamped >= 0 && !isHeadAlive(clamped)) {
            return false;
        }
        float mult = (clamped >= 0) ? (float) SpiritHydraConfig.get().headHitDamageMultiplier : 1.0f;
        this.pendingTargetHeadIndex = clamped;
        try {
            return this.hurt(source, amount * mult);
        } finally {
            this.pendingTargetHeadIndex = -1;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypes.IN_WALL)) {
            return false;
        }
        if (this.isSpawningIn() || this.isTransitioningPhase() || this.isHeadStaggering()) {
            return false;
        }

        int preferredHead = this.pendingTargetHeadIndex;
        float hpBefore = this.getHealth();

        // Kiểm tra chặn cái chết Phase 1 (<= 5% = 100 HP hoặc <= 1 HP) TRƯỚC khi super.hurt() gây chết!
        if (!this.hasEnteredPhase2 && this.level() instanceof ServerLevel serverLevel) {
            float triggerHp = getPhase2TriggerThreshold();
            float estDamage = estimatePostArmorDamage(source, amount);
            if (hpBefore - estDamage <= triggerHp) {
                float floorHp = Math.max(1.0f, Math.min(hpBefore, triggerHp));
                clampHeadPoolsToTotal(floorHp);
                this.setHealth(sumHeadHp());
                beginPhase2Transition(serverLevel);
                return false;
            }
        }

        boolean applied = super.hurt(source, amount);

        if (applied && this.isAlive() && this.level() instanceof ServerLevel serverLevel) {
            float actualLoss = Math.max(0.0f, hpBefore - this.getHealth());
            if (actualLoss > 0.0001f) {
                applyDamageToHeadPools(serverLevel, actualLoss, preferredHead);
            }
            if (!this.hasEnteredPhase2) {
                float triggerHp = getPhase2TriggerThreshold();
                if (this.getHealth() <= triggerHp) {
                    float floorHp = Math.max(1.0f, this.getHealth());
                    clampHeadPoolsToTotal(floorHp);
                    this.setHealth(sumHeadHp());
                    beginPhase2Transition(serverLevel);
                    return true;
                }
            }
        }
        return applied;
    }

    /**
     * Phân bổ sát thương thực tế `actualLoss` vào bể máu 4 đầu (`headHp[0..3]`):
     * - Nếu `preferredHead >= 0` (đánh trúng đầu/cổ đó): trừ trực tiếp vào `headHp[preferredHead]` trước;
     *   khi `headHp[preferredHead] <= 0` -> chặt ĐÚNG đầu đó (phần sát thương dư tràn sang đầu thấp máu nhất để không mất damage).
     * - Nếu `preferredHead < 0` (đánh thân/chân/đuôi): trừ vào đầu còn sống có `headHp` thấp nhất hiện tại.
     * - Sau cùng luôn ép `boss.getHealth() == sum(headHp)` (0 sai số!).
     */
    private void applyDamageToHeadPools(ServerLevel level, float actualLoss, int preferredHead) {
        float rem = actualLoss;
        boolean canSever = canSeverHeadsNow();

        // 1. Ưu tiên đầu bị nhắm đánh trực tiếp
        if (preferredHead >= 0 && preferredHead < HEAD_COUNT && isHeadAlive(preferredHead)
                && !pendingSeverHeadQueue.contains(preferredHead)) {
            float cur = getHeadHp(preferredHead);
            if (cur > 0.0f) {
                int aliveWithHp = countActiveHeadsWithPositiveHp();
                float floor = (!this.hasEnteredPhase2 && aliveWithHp <= 1) ? 1.0f : 0.0f;
                float take = Math.min(rem, Math.max(0.0f, cur - floor));
                float next = Math.max(0.0f, cur - take);
                setHeadHp(preferredHead, next);
                rem -= take;
                if (next <= 0.001f && canSever && aliveWithHp > 1) {
                    setHeadHp(preferredHead, 0.0f);
                    enqueueHeadSever(level, preferredHead);
                }
            }
        }

        // 2. Đánh thân/chân/đuôi hoặc phần sát thương tràn: trừ lần lượt vào đầu còn sống có máu thấp nhất
        int guard = 0;
        while (rem > 0.001f && guard++ < HEAD_COUNT + 2) {
            int target = pickLowestHpAliveHead();
            if (target < 0) break;
            int aliveWithHp = countActiveHeadsWithPositiveHp();
            float floor = (!this.hasEnteredPhase2 && aliveWithHp <= 1) ? 1.0f : 0.0f;
            float cur = getHeadHp(target);
            float take = Math.min(rem, Math.max(0.0f, cur - floor));
            if (take <= 0.0001f) break;
            float next = Math.max(0.0f, cur - take);
            setHeadHp(target, next);
            rem -= take;
            if (next <= 0.001f && canSever && aliveWithHp > 1) {
                setHeadHp(target, 0.0f);
                enqueueHeadSever(level, target);
            }
        }

        // 3. Khóa bất biến: tổng HP boss luôn trùng khít tổng 4 bể máu đầu
        float total = sumHeadHp();
        if (!this.hasEnteredPhase2 && total < 1.0f) {
            int last = pickHighestHpAliveHead();
            if (last >= 0) setHeadHp(last, 1.0f);
            total = sumHeadHp();
        }
        if (Math.abs(this.getHealth() - total) > 0.02f) {
            this.setHealth(Math.min(this.getMaxHealth(), total));
        }
    }

    private int countActiveHeadsWithPositiveHp() {
        int count = 0;
        for (int i = 0; i < HEAD_COUNT; i++) {
            if (isHeadAlive(i) && !pendingSeverHeadQueue.contains(i) && getHeadHp(i) > 0.001f) {
                count++;
            }
        }
        return count;
    }

    private int pickLowestHpAliveHead() {
        int best = -1;
        float bestHp = Float.MAX_VALUE;
        for (int i = 0; i < HEAD_COUNT; i++) {
            if (!isHeadAlive(i) || pendingSeverHeadQueue.contains(i)) continue;
            float hp = getHeadHp(i);
            if (hp > 0.001f && hp < bestHp) {
                bestHp = hp;
                best = i;
            }
        }
        return best;
    }

    private int pickHighestHpAliveHead() {
        int best = -1;
        float bestHp = -1.0f;
        for (int i = 0; i < HEAD_COUNT; i++) {
            if (!isHeadAlive(i)) continue;
            float hp = getHeadHp(i);
            if (hp > bestHp) {
                bestHp = hp;
                best = i;
            }
        }
        return best;
    }

    /** Đồng bộ `headHp[0..3]` về đúng tổng `desiredTotal` khi chặn chết Phase 1 hoặc khi `/data merge` chỉnh Health ngoài. */
    private void clampHeadPoolsToTotal(float desiredTotal) {
        float clamped = Math.max(0.0f, Math.min(this.getMaxHealth(), desiredTotal));
        float curSum = sumHeadHp();
        if (curSum > 0.001f) {
            float ratio = clamped / curSum;
            for (int i = 0; i < HEAD_COUNT; i++) {
                setHeadHp(i, isHeadAlive(i) ? getHeadHp(i) * ratio : 0.0f);
            }
        } else {
            List<Integer> alive = getAliveHeadIndices();
            if (!alive.isEmpty()) {
                float share = clamped / alive.size();
                for (int i = 0; i < HEAD_COUNT; i++) {
                    setHeadHp(i, isHeadAlive(i) ? share : 0.0f);
                }
            }
        }
    }

    private void enqueueHeadSever(ServerLevel level, int headIdx) {
        if (headIdx < 0 || headIdx >= HEAD_COUNT || !isHeadAlive(headIdx)) return;
        if (pendingSeverHeadQueue.contains(headIdx)) return;
        if (getAliveHeadCount() <= 1) return;

        // Nếu đang trống hàng đợi -> chặt ĐÚNG đầu đó ngay lập tức cho phản hồi sắc bén!
        if (pendingSeverHeadQueue.isEmpty() && this.severDelayTicks <= 0) {
            executeSingleHeadSever(level, headIdx);
            this.severDelayTicks = SpiritHydraConfig.get().headSeverDelayTicks;
        } else {
            pendingSeverHeadQueue.addLast(headIdx);
        }
    }

    private void tickPendingHeadSevers(ServerLevel level) {
        if (this.severDelayTicks > 0) {
            this.severDelayTicks--;
            return;
        }
        if (pendingSeverHeadQueue.isEmpty()) return;
        if (getAliveHeadCount() <= 1) {
            pendingSeverHeadQueue.clear();
            return;
        }
        int nextIdx = pendingSeverHeadQueue.pollFirst();
        if (isHeadAlive(nextIdx) && getAliveHeadCount() > 1) {
            executeSingleHeadSever(level, nextIdx);
            this.severDelayTicks = SpiritHydraConfig.get().headSeverDelayTicks;
        }
    }

    private void executeSingleHeadSever(ServerLevel level, int headIdx) {
        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        Vec3 headPos = getHeadWorldPosition(headIdx);
        Vec3 stumpPos = getNeckRootWorldPosition(headIdx);

        // 1. Ẩn bone đầu/cổ `headIdx` qua bitmask DataTracker, lộ `stump_i`, đặt headHp = 0
        int newMask = getAliveHeadsMask() & ~(1 << headIdx);
        setAliveHeadsMask(newMask);
        setHeadHp(headIdx, 0.0f);
        setHeadAnimationLock(headIdx, false);
        this.headSlotActive[headIdx] = false;
        this.headSlotAnimTick[headIdx] = 0.0;

        // 2. Khựng đau stagger ngắn bất tử, khóa góc quay chống spin
        lockCurrentRotation();
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(DATA_STAGGER_TICKS, Math.max(getStaggerTicks(), cfg.headSeverStaggerTicks));
        if (!isTransitioningPhase()) {
            setSkillState(SkillState.HEAD_STAGGER);
            playActionAnim("head_cut", 1.0f);
        }

        // 3. VFX gọn nhẹ qua payload S2C tại vị trí gốc cổ cụt + âm thanh + rung màn hình nhẹ
        ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_HEAD_SEVER,
                stumpPos, headPos.subtract(stumpPos), 2.5f * getScaleRatio(), isEnragedVisual());
        level.playSound(null, BlockPos.containing(stumpPos), ModSounds.HYDRA_HEAD_CUT, SoundSource.HOSTILE, 2.8f, 0.80f);
        ModNetworking.sendShakeNearby(level, this.position(), 56.0, 1.25f, 12);

        // 4. Rơi đầu cosmetic + tỉ lệ rơi `Spirit Hydra Fang`
        Vec3 outDir = headPos.subtract(this.position());
        Vec3 horizOut = outDir.horizontalDistanceSqr() > 1e-4
                ? new Vec3(outDir.x, 0.0, outDir.z).normalize()
                : this.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 dropVel = horizOut.scale(0.32 + level.random.nextDouble() * 0.14)
                .add(0.0, 0.28 + level.random.nextDouble() * 0.10, 0.0);
        SeveredHydraHeadEntity.spawn(level, headPos, dropVel, wrapAngle(this.yBodyRot + HEAD_BASE_FAN_YAW_DEG[headIdx]),
                getEffectiveScale() * 0.55f, isEnragedVisual());

        if (level.random.nextDouble() <= cfg.hydraFangDropChance) {
            this.spawnAtLocation(new ItemStack(ModItems.SPIRIT_HYDRA_FANG, 1), (float) (headPos.y - this.getY()));
        }

        refreshBossTitle();
        int remaining = getAliveHeadCount();
        int spdPct = (int) Math.round(getSeveredHeadCount() * cfg.attackSpeedBonusPerLostHead * 100.0);
        announceToNearbyWithRadius(level,
                Component.literal("⚔ ĐẦU #" + (headIdx + 1) + " (" + HEAD_ROLE_LABELS[headIdx]
                        + ") CỦA SPIRIT HYDRA ĐÃ BỊ CHẶT! Còn " + remaining + "/" + HEAD_COUNT
                        + " đầu (tốc độ đánh +" + spdPct + "%).")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                cfg.bossBarRadius);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.hasEnteredPhase2 && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && this.level() instanceof ServerLevel serverLevel) {
            float floorHp = getPhase2TriggerThreshold();
            clampHeadPoolsToTotal(floorHp);
            this.setHealth(sumHeadHp());
            beginPhase2Transition(serverLevel);
            return;
        }

        setSkillState(SkillState.DEATH);
        setAllHeadsAnimationLocked(true);
        discardAllHitboxParts();
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.blockPosition(), ModSounds.HYDRA_DEATH, SoundSource.HOSTILE, 3.0f, 0.72f);
            ModNetworking.sendShakeNearby(serverLevel, this.position(), 84.0, 2.5f, 24);
        }
        super.die(damageSource);
    }

    // =========================================================================
    // PHASE 2 "CUỒNG BẠO": 5.0s BẤT TỬ, HỒI 3000 HP (750/ĐẦU), MỌC LẠI 4 ĐẦU MỖI 0.35s
    // =========================================================================
    private void beginPhase2Transition(ServerLevel level) {
        if (this.hasEnteredPhase2) return;

        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        this.hasEnteredPhase2 = true;
        this.entityData.set(DATA_HAS_ENTERED_PHASE2, true);
        this.pendingSeverHeadQueue.clear();
        this.entityData.set(DATA_STAGGER_TICKS, 0);

        lockCurrentRotation();
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.scheduledActions.clear();
        setAllHeadsAnimationLocked(true);
        Arrays.fill(this.headSlotActive, false);

        // Nâng BossMaxHealth Phase 2 = 3000 HP (2000 * 1.5)
        float p2Max = computeTargetBossMaxHealth(true);
        setBossMaxHealth(p2Max);

        this.transitionStartHp = Math.max(1.0f, this.getHealth());
        this.transitionTotalTicks = Math.max(80, cfg.phaseTransitionTicks); // 100 ticks = 5.0s
        this.regrowCursor = 0;
        this.entityData.set(DATA_PHASE_TRANS_TICKS, this.transitionTotalTicks);
        setSkillState(SkillState.PHASE_CHANGE);
        playActionAnim("phase_change", 1.0f);

        level.playSound(null, this.blockPosition(), ModSounds.HYDRA_PHASE_CHANGE, SoundSource.HOSTILE, 3.2f, 0.72f);
        ModNetworking.sendShakeNearby(level, this.position(), 96.0, 2.6f, 34);
        announceToNearbyWithRadius(level,
                Component.literal("⚠ SPIRIT HYDRA SẮP GỤC NGÃ... TỨ THỦ OAN HỒN ĐANG HỘI TỤ MỌC LẠI CẢ 4 ĐẦU! ⚠")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                cfg.bossBarRadius);
    }

    private void tickPhase2HeadRegrowth(ServerLevel level, int elapsed) {
        List<Integer> severed = getSeveredHeadIndices();
        if (severed.isEmpty()) return;

        // Mọc lại từng đầu bị chặt lần lượt cách nhau đúng `headRegrowIntervalTicks` (7 ticks = 0.35s) bắt đầu từ tick 38!
        int interval = Math.max(2, SpiritHydraConfig.get().headRegrowIntervalTicks);
        int startTick = HydraAttackTiming.PHASE2_REGROW_TICKS[0];
        if (elapsed < startTick) return;

        int nextDue = startTick + this.regrowCursor * interval;
        if (elapsed < nextDue) return;

        int regrowIdx = severed.get(0);
        this.regrowCursor++;
        setAliveHeadsMask(getAliveHeadsMask() | (1 << regrowIdx));
        setHeadHp(regrowIdx, getPerHeadMaxHealth() * 0.45f);

        Vec3 stump = getNeckRootWorldPosition(regrowIdx);
        Vec3 head = getHeadWorldPosition(regrowIdx);
        ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_HEAD_REGROW,
                stump, head.subtract(stump), 2.8f * getScaleRatio(), true);
        level.playSound(null, BlockPos.containing(head), ModSounds.HYDRA_HEAD_REGROW, SoundSource.HOSTILE,
                2.6f, 0.82f + regrowIdx * 0.06f);
        ModNetworking.sendShakeNearby(level, this.position(), 68.0, 1.15f, 8);
        refreshBossTitle();
    }

    private void triggerPhase2VisualClimax(ServerLevel level) {
        this.entityData.set(DATA_ENRAGED_VISUAL, true);
        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        refreshBossTitle();

        float eff = getEffectiveScale();
        ShockwaveRingEntity.spawn(level, this.position(), 16.0f * eff / SCALE, COLOR_ENRAGED_VIOLET, 20);
        ShockwaveRingEntity.spawn(level, this.position().add(0, 1.2 * eff, 0), 12.0f * eff / SCALE, COLOR_ENRAGED_ICE, 16);
        ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_PHASE_PULSE,
                this.position().add(0, 1.5 * eff, 0), this.getLookAngle(), 9.0f * getScaleRatio(), true);
        level.playSound(null, this.blockPosition(), ModSounds.HYDRA_ROAR, SoundSource.HOSTILE, 3.4f, 0.64f);
        ModNetworking.sendShakeNearby(level, this.position(), 100.0, 3.0f, 24);

        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        int p2Hp = Math.round(computeTargetBossMaxHealth(true));
        double p2Armor = Math.min(30.0, cfg.armor * cfg.phase2StatMultiplier);
        announceToNearbyWithRadius(level,
                Component.literal(String.format(
                        "🔥 SPIRIT HYDRA HÓA CUỒNG BẠO — PHASE 2: 4 ĐẦU HỒI SINH, MÁU %d (750/ĐẦU), GIÁP %.1f, SỨC MẠNH x%.1f! 🔥",
                        p2Hp, p2Armor, cfg.phase2StatMultiplier))
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                cfg.bossBarRadius);
    }

    private void completePhase2Transformation(ServerLevel level) {
        if (getAliveHeadsMask() != FULL_HEADS_MASK) {
            setAliveHeadsMask(FULL_HEADS_MASK);
        }
        if (!isEnragedVisual()) {
            triggerPhase2VisualClimax(level);
        }

        this.phase = 2;
        syncPhaseData(2);
        float p2Max = computeTargetBossMaxHealth(true);
        setBossMaxHealth(p2Max);
        initAllHeadPools(p2Max / HEAD_COUNT); // 750 HP x 4 = 3000 HP!
        applyPhase2Modifiers();
        this.setHealth(p2Max);
        refreshBossTitle();

        setAllHeadsAnimationLocked(false);
        snapSyncPrevAngles();
        setSkillState(SkillState.IDLE);
        this.globalSkillRestTicks = 18;
    }

    private void applyPhase2Modifiers() {
        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        double bonus = Math.max(0.0, cfg.phase2StatMultiplier - 1.0); // +0.5

        removeAttributeModifierIfPresent(Attributes.MAX_HEALTH, LEGACY_PHASE2_HEALTH_MOD_ID);

        double baseArmor = Math.max(0.0, cfg.armor);
        double maxAllowedArmorBonus = (baseArmor > 0.0) ? Math.max(0.0, (30.0 / baseArmor) - 1.0) : 0.0;
        applyPermanentMultiplier(Attributes.ARMOR, PHASE2_ARMOR_MOD_ID, Math.min(bonus, maxAllowedArmorBonus));
        applyPermanentMultiplier(Attributes.ATTACK_DAMAGE, PHASE2_DAMAGE_MOD_ID, bonus);
        applyPermanentMultiplier(Attributes.MOVEMENT_SPEED, PHASE2_SPEED_MOD_ID, bonus);
    }

    // =========================================================================
    // TICK SERVER: PROXY HITBOXES, BỂ MÁU 4 ĐẦU, CHUYỂN PHASE, SLOT 4 ĐẦU ĐỘC LẬP
    // =========================================================================
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        this.fallDistance = 0.0f;
        syncBossBarPlayers(serverLevel, cfg.bossBarRadius);
        ensureAndSyncHitboxParts(serverLevel);

        // Nếu lệnh ngoài (`/data merge` / heal) làm lệch Health so với sum(headHp), tự đồng bộ lại sạch sẽ
        if (!this.isTransitioningPhase() && this.isAlive()) {
            float sum = sumHeadHp();
            float cur = this.getHealth();
            if (Math.abs(cur - sum) > 0.5f) {
                if (cur < sum) {
                    applyDamageToHeadPools(serverLevel, sum - cur, -1);
                } else {
                    clampHeadPoolsToTotal(cur);
                }
            }
            if (!this.hasEnteredPhase2 && !this.isSpawningIn() && this.getHealth() <= getPhase2TriggerThreshold()) {
                float floorHp = Math.max(1.0f, this.getHealth());
                clampHeadPoolsToTotal(floorHp);
                this.setHealth(sumHeadHp());
                beginPhase2Transition(serverLevel);
            }
        }

        // Giảm cooldown toàn bộ skill & slot đầu
        for (SkillState s : SkillState.values()) {
            int cd = skillCooldowns.getOrDefault(s, 0);
            if (cd > 0) skillCooldowns.put(s, cd - 1);
        }
        for (int i = 0; i < HEAD_COUNT; i++) {
            if (headSlotCooldowns[i] > 0) headSlotCooldowns[i]--;
        }

        // Dọn danh sách oan hồn Vex đã chết mỗi giây
        if (this.tickCount % 20 == 0 && !activeWisps.isEmpty()) {
            activeWisps.removeIf(uuid -> {
                Entity e = serverLevel.getEntity(uuid);
                return e == null || !e.isAlive();
            });
        }

        // 1. Ưu tiên cao nhất: chuỗi chuyển Phase 2 (`100 ticks = 5.0s` bất tử, hồi mượt 3000 HP, mọc lại cả 4 đầu mỗi 0.35s)
        int pTrans = this.entityData.get(DATA_PHASE_TRANS_TICKS);
        if (pTrans > 0) {
            this.setDeltaMovement(Vec3.ZERO);
            this.getNavigation().stop();
            normalizeAnglesAndSyncPrev();

            int elapsed = this.transitionTotalTicks - pTrans;
            int nextTrans = pTrans - 1;
            this.entityData.set(DATA_PHASE_TRANS_TICKS, nextTrans);

            float progress = 1.0f - (nextTrans / (float) Math.max(1, this.transitionTotalTicks));
            float smooth = progress * progress * (3.0f - 2.0f * progress);
            float p2Max = computeTargetBossMaxHealth(true);
            float targetHp = Mth.lerp(smooth, this.transitionStartHp, p2Max);

            tickPhase2HeadRegrowth(serverLevel, elapsed);

            // Phân bổ máu hồi mượt đều vào các đầu đang sống để `sum(headHp) == getHealth()`
            List<Integer> aliveNow = getAliveHeadIndices();
            if (!aliveNow.isEmpty()) {
                float share = Math.min(p2Max / HEAD_COUNT, targetHp / aliveNow.size());
                for (int i = 0; i < HEAD_COUNT; i++) {
                    setHeadHp(i, isHeadAlive(i) ? share : 0.0f);
                }
                this.setHealth(Math.min(p2Max, sumHeadHp()));
            }

            if (!isEnragedVisual() && elapsed >= 30) {
                triggerPhase2VisualClimax(serverLevel);
            }

            logHydraRotationDebug("SpiritHydra-P2", pTrans);

            if (elapsed % 12 == 0) {
                ModNetworking.sendHydraVfxNearby(serverLevel, HydraVfxPayload.KIND_PHASE_PULSE,
                        this.position().add(0, 1.4 * getEffectiveScale(), 0),
                        this.getLookAngle(), 6.5f * getScaleRatio(), isEnragedVisual());
            }
            if (pTrans % 18 == 0) {
                ModNetworking.sendShakeNearby(serverLevel, this.position(), 88.0, 1.35f, 10);
            }
            if (nextTrans <= 0) {
                completePhase2Transformation(serverLevel);
            }
            return;
        }

        // 2. Xử lý hàng đợi chặt đầu nếu 1 đòn lớn hạ nhiều đầu cùng lúc
        tickPendingHeadSevers(serverLevel);

        // 3. Khựng đau sau khi bị chặt đầu (bất tử ngắn, khóa góc quay)
        int stag = this.entityData.get(DATA_STAGGER_TICKS);
        if (stag > 0) {
            this.setDeltaMovement(Vec3.ZERO);
            this.getNavigation().stop();
            normalizeAnglesAndSyncPrev();
            logHydraRotationDebug("SpiritHydra-Stagger", stag);

            int nextStag = stag - 1;
            this.entityData.set(DATA_STAGGER_TICKS, nextStag);
            if (nextStag <= 0 && getSkillState() == SkillState.HEAD_STAGGER) {
                snapSyncPrevAngles();
                setSkillState(SkillState.IDLE);
            }
            return;
        }

        // 4. Cập nhật các đòn cắn/phun độc lập của 4 đầu (đồng bộ 0-tick `HydraAttackTiming.HEAD_BITE_WINDOW`)
        LivingEntity target = this.getTarget();
        tickActiveHeadSlots(serverLevel, target);
        if (target != null && target.isAlive() && getSkillState() == SkillState.IDLE && !this.isSpawningIn()) {
            tryStartIndependentHeadSlot(serverLevel, target);
        }
    }

    private void tickActiveHeadSlots(ServerLevel level, @Nullable LivingEntity target) {
        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        float animSpeed = HydraAttackTiming.computeAttackAnimSpeed(this.phase >= 2, getSeveredHeadCount());
        double eff = getEffectiveScale();

        for (int i = 0; i < HEAD_COUNT; i++) {
            if (!headSlotActive[i]) continue;
            if (!isHeadAlive(i) || getSkillState() != SkillState.IDLE) {
                headSlotActive[i] = false;
                headSlotAnimTick[i] = 0.0;
                setHeadAnimationLock(i, false);
                continue;
            }
            double prev = headSlotAnimTick[i];
            double curr = prev + animSpeed;
            headSlotAnimTick[i] = curr;

            if (!headSlotImpactDone[i] && HydraAttackTiming.HEAD_BITE_WINDOW.crossedImpact(prev, curr)) {
                headSlotImpactDone[i] = true;
                Vec3 headPos = getHeadWorldPosition(i);
                if (!headSlotIsRanged[i]) {
                    // Cận chiến: gây sát thương ĐÚNG khung hình hàm khép cắn (`impactTick = 12`)!
                    float dmg = cfg.headSlotBiteDamage * skillDamageScale();
                    level.playSound(null, BlockPos.containing(headPos), ModSounds.HYDRA_BITE,
                            SoundSource.HOSTILE, 2.0f, 0.85f + i * 0.05f);
                    ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_BITE_IMPACT,
                            headPos, this.getLookAngle(), 2.8f * getScaleRatio(), isEnragedVisual());
                    if (target != null && target.isAlive() && target.position().distanceTo(headPos) <= 6.2 * eff) {
                        target.hurt(this.damageSources().mobAttack(this), dmg);
                    }
                } else if (target != null && target.isAlive()) {
                    // Tầm xa: nhả 1 cầu hồn ĐÚNG khung hình ngửa cổ há mõm!
                    Vec3 toT = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(headPos);
                    Vec3 dir = toT.lengthSqr() > 1e-4 ? toT.normalize() : this.getLookAngle();
                    level.playSound(null, BlockPos.containing(headPos), ModSounds.HYDRA_SPIT,
                            SoundSource.HOSTILE, 1.7f, 0.90f);
                    AbyssalOrbEntity.spawnParabolic(level, this, headPos,
                            dir.scale(1.18).add(0, 0.16, 0), 0.022f,
                            0.55f * (float) eff, isEnragedVisual() ? COLOR_ENRAGED_VIOLET : COLOR_SPIRIT_CYAN,
                            cfg.soulSpitDamage * skillDamageScale(), 2.2f * (float) eff, false, false, false);
                }
            }

            if (curr >= HydraAttackTiming.HEAD_BITE_TOTAL_TICKS) {
                headSlotActive[i] = false;
                headSlotAnimTick[i] = 0.0;
                setHeadAnimationLock(i, false);
            }
        }
    }

    private void tryStartIndependentHeadSlot(ServerLevel level, LivingEntity target) {
        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        int aliveCount = getAliveHeadCount();
        int lostCount = getSeveredHeadCount();
        double speedFactor = 1.0 + lostCount * cfg.attackSpeedBonusPerLostHead;
        if (this.phase >= 2) {
            speedFactor *= cfg.phase2StatMultiplier;
        }
        int maxConcurrent = Math.max(1, Math.min(2, (aliveCount + 1) / 2));
        int busy = 0;
        for (int i = 0; i < HEAD_COUNT; i++) {
            if (headSlotActive[i]) busy++;
        }
        if (busy >= maxConcurrent) return;

        float animSpeed = HydraAttackTiming.computeAttackAnimSpeed(this.phase >= 2, lostCount);
        double eff = getEffectiveScale();

        for (int i = 0; i < HEAD_COUNT; i++) {
            if (!isHeadAlive(i) || headSlotCooldowns[i] > 0 || headSlotActive[i]) continue;

            Vec3 headPos = getHeadWorldPosition(i);
            double dist = target.position().add(0, target.getBbHeight() * 0.5, 0).distanceTo(headPos);

            if (dist <= 5.6 * eff) {
                headSlotActive[i] = true;
                headSlotIsRanged[i] = false;
                headSlotImpactDone[i] = false;
                headSlotAnimTick[i] = 0.0;
                setHeadAnimationLock(i, true);
                playHeadSlotBiteAnim(i, animSpeed);
                ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_BITE_TELEGRAPH,
                        headPos, target.position().subtract(headPos), 2.4f * getScaleRatio(), isEnragedVisual());
                int baseCd = 54 + level.random.nextInt(24);
                headSlotCooldowns[i] = Math.max(18, (int) Math.round(baseCd / speedFactor));
                busy++;
            } else if (dist <= 18.0 * eff && level.random.nextFloat() < 0.32f) {
                headSlotActive[i] = true;
                headSlotIsRanged[i] = true;
                headSlotImpactDone[i] = false;
                headSlotAnimTick[i] = 0.0;
                setHeadAnimationLock(i, true);
                playHeadSlotBiteAnim(i, animSpeed);
                ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_SPIT_CHARGE,
                        headPos, target.position().subtract(headPos), 1.8f * getScaleRatio(), isEnragedVisual());
                int baseCd = 78 + level.random.nextInt(28);
                headSlotCooldowns[i] = Math.max(24, (int) Math.round(baseCd / speedFactor));
                busy++;
            }
            if (busy >= maxConcurrent) break;
        }
    }

    public void logHydraRotationDebug(String tag, int ticksLeft) {
        boolean force = isDebugLoggingEnabled();
        if (!force && !AbyssalRpgMod.LOGGER.isDebugEnabled()) return;
        logGeoRotationDebug(tag, ticksLeft, force);
        StringBuilder heads = new StringBuilder();
        int mask = getAliveHeadsMask();
        for (int i = 0; i < HEAD_COUNT; i++) {
            boolean alive = (mask & (1 << i)) != 0;
            float fanYaw = wrapAngle(this.yBodyRot + HEAD_BASE_FAN_YAW_DEG[i]);
            heads.append("H").append(i + 1).append(alive ? "(" : "(X|")
                    .append(String.format("%.0fhp|%.0f°)", getHeadHp(i), fanYaw));
            if (i < HEAD_COUNT - 1) heads.append(' ');
        }
        if (force) {
            AbyssalRpgMod.LOGGER.info("[{}#{} mask=0x{} ({}/{}) sumHP={}] {}",
                    tag, this.getId(), Integer.toHexString(mask), getAliveHeadCount(), HEAD_COUNT,
                    String.format("%.1f", sumHeadHp()), heads);
        }
    }

    public float skillDamageScale() {
        return (this.phase >= 2) ? (float) SpiritHydraConfig.get().phase2StatMultiplier : 1.0f;
    }

    public void finishSkill(int restMinTicks) {
        setAllHeadsAnimationLocked(false);
        setSkillState(SkillState.IDLE);
        float cdDiv = (this.phase >= 2) ? (float) SpiritHydraConfig.get().phase2StatMultiplier : 1.0f;
        int scaledRest = Math.max(12, Math.round(restMinTicks / cdDiv));
        int extra = this.getRandom().nextInt(this.phase >= 2 ? 10 : 18);
        this.globalSkillRestTicks = scaledRest + extra;
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.deathTime % 8 == 0) {
                ModNetworking.sendHydraVfxNearby(serverLevel, HydraVfxPayload.KIND_DEATH_BURST,
                        this.position().add(0, 1.8 * getEffectiveScale(), 0),
                        this.getLookAngle(), 5.0f * getScaleRatio(), isEnragedVisual());
            }
            if (this.deathTime == CUSTOM_DEATH_DURATION - 10) {
                ShockwaveRingEntity.spawn(serverLevel, this.position(), 15.0f * getScaleRatio(),
                        isEnragedVisual() ? COLOR_ENRAGED_VIOLET : COLOR_SPIRIT_CYAN, 20);
                ModNetworking.sendShakeNearby(serverLevel, this.position(), 96.0, 2.6f, 20);
            }
            if (this.deathTime >= CUSTOM_DEATH_DURATION && !this.isRemoved()) {
                this.level().broadcastEntityEvent(this, (byte) 60);
                this.remove(Entity.RemovalReason.KILLED);
            }
        }
    }

    /** Hủy toàn bộ spam particle server lúc idle; aura 1-2 hạt hồn mỗi 12 tick được vẽ siêu nhẹ phía Client! */
    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        // Cố tình để trống: VFX idle 1-2 hạt hồn mỗi 12 tick xử lý phía Client trong SpiritHydraGeoRenderer (0 packet mạng!)
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.HYDRA_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WARDEN_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.HYDRA_DEATH; }

    // =========================================================================
    // LƯU & KHÔI PHỤC ĐẦY ĐỦ BỂ MÁU 4 ĐẦU, BITMASK, PHASE 2 & MODIFIER TỪ NBT
    // =========================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saveSharedGeoBossData(tag);
        tag.putInt("HydraAliveMask", getAliveHeadsMask());
        for (int i = 0; i < HEAD_COUNT; i++) {
            tag.putFloat("HydraHeadHp_" + i, getHeadHp(i));
        }
        int[] q = new int[pendingSeverHeadQueue.size()];
        int qi = 0;
        for (int idx : pendingSeverHeadQueue) q[qi++] = idx;
        tag.putIntArray("HydraPendingSeverQueue", q);
        tag.putInt("HydraStaggerTicks", getStaggerTicks());
        tag.putInt("HydraGlobalRest", this.globalSkillRestTicks);
        tag.putInt("HydraWrathCd", this.skillCooldowns.getOrDefault(SkillState.WRATH_OF_MANY_HEADS, 0));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadSharedGeoBossData(tag, null, null, null, null, null, null);

        if (tag.contains("HydraAliveMask")) {
            setAliveHeadsMask(tag.getInt("HydraAliveMask") & FULL_HEADS_MASK);
        } else {
            setAliveHeadsMask(FULL_HEADS_MASK);
        }

        pendingSeverHeadQueue.clear();
        if (tag.contains("HydraPendingSeverQueue", Tag.TAG_INT_ARRAY)) {
            for (int idx : tag.getIntArray("HydraPendingSeverQueue")) {
                if (idx >= 0 && idx < HEAD_COUNT) pendingSeverHeadQueue.addLast(idx);
            }
        }
        this.entityData.set(DATA_STAGGER_TICKS, Math.max(0, tag.getInt("HydraStaggerTicks")));
        if (tag.contains("HydraGlobalRest")) this.globalSkillRestTicks = tag.getInt("HydraGlobalRest");
        if (tag.contains("HydraWrathCd")) this.skillCooldowns.put(SkillState.WRATH_OF_MANY_HEADS, tag.getInt("HydraWrathCd"));

        if (this.hasEnteredPhase2) {
            this.phase = 2;
            syncPhaseData(2);
            if (getBossMaxHealth() <= 0.0f) {
                setBossMaxHealth(computeTargetBossMaxHealth(true));
            }
            applyPhase2Modifiers();
            if (isEnragedVisual()) {
                this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
            }
            if (getPhaseTransitionTicks() > 0) {
                setSkillState(SkillState.PHASE_CHANGE);
                setAllHeadsAnimationLocked(true);
            }
        } else {
            if (getBossMaxHealth() <= 0.0f) {
                setBossMaxHealth(computeTargetBossMaxHealth(false));
            }
            this.bossEvent.setColor(BossEvent.BossBarColor.BLUE);
        }

        boolean loadedPerHead = false;
        for (int i = 0; i < HEAD_COUNT; i++) {
            String key = "HydraHeadHp_" + i;
            if (tag.contains(key, Tag.TAG_ANY_NUMERIC)) {
                setHeadHp(i, isHeadAlive(i) ? tag.getFloat(key) : 0.0f);
                loadedPerHead = true;
            }
        }
        if (loadedPerHead) {
            float sum = sumHeadHp();
            if (sum > 0.0f) {
                this.setHealth(Math.min(this.getMaxHealth(), sum));
            }
        } else if (tag.contains("Health", Tag.TAG_ANY_NUMERIC)) {
            float savedHp = tag.getFloat("Health");
            if (savedHp > 0.0f) {
                clampHeadPoolsToTotal(savedHp);
                this.setHealth(Math.min(this.getMaxHealth(), sumHeadHp()));
            }
        }

        refreshBossTitle();
        snapSyncPrevAngles();
    }

    // =========================================================================
    // PATH NAVIGATION & CHASE
    // =========================================================================
    private static class HydraPathNavigation extends GroundPathNavigation {
        public HydraPathNavigation(Mob mob, Level level) {
            super(mob, level);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            return super.createPathFinder(Math.max(maxVisitedNodes, 64));
        }
    }

    private static class HydraChaseMoveGoal extends Goal {
        private final HydraBossEntity boss;
        private int repathTicks = 0;

        public HydraChaseMoveGoal(HydraBossEntity boss) {
            this.boss = boss;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = boss.getTarget();
            return t != null && t.isAlive() && !boss.isRotationLocked() && boss.getSkillState() == SkillState.IDLE;
        }

        @Override
        public void tick() {
            LivingEntity target = boss.getTarget();
            if (target == null || !target.isAlive()) return;
            Vec3 toT = target.position().subtract(boss.position());
            double dist = toT.horizontalDistance();
            boss.smoothRotateToward(toT, 6.0f, (float) SpiritHydraConfig.get().phase2TurnSpeedMultiplier);

            double preferredDist = 5.4 * boss.getEffectiveScale();
            if (dist > preferredDist) {
                if (--repathTicks <= 0) {
                    repathTicks = 8;
                    boss.getNavigation().moveTo(target, 1.05);
                }
            } else {
                boss.getNavigation().stop();
            }
        }
    }

    // =========================================================================
    // SKILL SELECTOR (CHỐNG LẶP 3 LẦN, CHIA TẦM THEO EFFECTIVE SCALE)
    // =========================================================================
    private static class HydraSkillSelectorGoal extends Goal {
        private final HydraBossEntity boss;

        public HydraSkillSelectorGoal(HydraBossEntity boss) {
            this.boss = boss;
        }

        @Override
        public boolean canUse() {
            LivingEntity t = boss.getTarget();
            return t != null && t.isAlive() && !boss.isRotationLocked() && boss.getSkillState() == SkillState.IDLE;
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
                boss.globalSkillRestTicks = 12;
                return;
            }

            SpiritHydraConfig cfg = SpiritHydraConfig.get();
            boss.lastSkill2 = boss.lastSkill1;
            boss.lastSkill1 = chosen;
            boss.setSkillState(chosen);

            double cdDiv = (boss.phase >= 2) ? cfg.phase2StatMultiplier
                    : (1.0 + boss.getSeveredHeadCount() * cfg.attackSpeedBonusPerLostHead);
            int baseCd = switch (chosen) {
                case TRIPLE_BITE -> cfg.tripleBiteCooldown;
                case SPIRIT_BREATH -> cfg.spiritBreathCooldown;
                case SOUL_SPIT -> cfg.soulSpitCooldown;
                case TAIL_BODY_SLAM -> cfg.tailBodySlamCooldown;
                case SUMMON_WISPS -> cfg.summonWispsCooldown;
                case ALL_HEAD_BARRAGE -> cfg.allHeadBarrageCooldown;
                case SPIRIT_NOVA -> cfg.spiritNovaCooldown;
                case SOUL_CHAIN -> cfg.soulChainCooldown;
                case WRATH_OF_MANY_HEADS -> cfg.wrathCooldown;
                default -> 80;
            };
            int finalCd = (chosen == SkillState.WRATH_OF_MANY_HEADS)
                    ? cfg.wrathCooldown
                    : Math.max(28, (int) Math.round(baseCd / cdDiv));
            boss.skillCooldowns.put(chosen, finalCd);
        }

        private SkillState pickSkill(ServerLevel level, LivingEntity target) {
            SpiritHydraConfig cfg = SpiritHydraConfig.get();
            double eff = boss.getEffectiveScale();
            double dist = boss.distanceTo(target);
            float hpRatio = boss.getHealth() / Math.max(1.0f, boss.getMaxHealth());

            if (boss.phase >= 2 && hpRatio < 0.20f && isReady(SkillState.WRATH_OF_MANY_HEADS)) {
                return SkillState.WRATH_OF_MANY_HEADS;
            }

            List<SkillState> pool = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();

            if (isReady(SkillState.TRIPLE_BITE) && dist <= 11.5 * eff)
                add(pool, weights, SkillState.TRIPLE_BITE, dist <= 7.5 * eff ? 28 : 16);
            if (isReady(SkillState.SPIRIT_BREATH) && dist >= 2.8 * eff && dist <= 16.5 * eff)
                add(pool, weights, SkillState.SPIRIT_BREATH, 22);
            if (isReady(SkillState.SOUL_SPIT) && dist >= 4.2 * eff && dist <= 28.0 * eff)
                add(pool, weights, SkillState.SOUL_SPIT, dist >= 8.5 * eff ? 26 : 14);
            if (isReady(SkillState.TAIL_BODY_SLAM) && dist <= 10.5 * eff)
                add(pool, weights, SkillState.TAIL_BODY_SLAM, dist <= 6.5 * eff ? 26 : 12);
            if (isReady(SkillState.SUMMON_WISPS) && boss.activeWisps.size() < cfg.maxActiveWisps - 1)
                add(pool, weights, SkillState.SUMMON_WISPS, boss.activeWisps.isEmpty() ? 18 : 8);

            if (boss.phase >= 2) {
                if (isReady(SkillState.ALL_HEAD_BARRAGE) && dist <= 28.0 * eff)
                    add(pool, weights, SkillState.ALL_HEAD_BARRAGE, 25);
                if (isReady(SkillState.SPIRIT_NOVA) && dist <= 18.0 * eff)
                    add(pool, weights, SkillState.SPIRIT_NOVA, dist <= 11.0 * eff ? 26 : 16);
                if (isReady(SkillState.SOUL_CHAIN) && dist >= 5.0 * eff && dist <= 24.0 * eff)
                    add(pool, weights, SkillState.SOUL_CHAIN, 24);
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
    // BASE SKILL GOAL ĐỒNG BỘ 0-TICK TUYỆT ĐỐI VỚI HydraAttackTiming
    // =========================================================================
    private abstract static class BaseSyncedHydraSkillGoal extends Goal {
        protected final HydraBossEntity boss;
        protected final SkillState ownedState;
        protected final SkillTimeline timeline;
        protected double prevAnimTick = 0.0;
        protected double currAnimTick = 0.0;
        protected float animSpeed = 1.0f;

        protected BaseSyncedHydraSkillGoal(HydraBossEntity boss, SkillState ownedState) {
            this.boss = boss;
            this.ownedState = ownedState;
            this.timeline = HydraAttackTiming.forSkill(ownedState);
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return boss.getSkillState() == ownedState && !boss.isRotationLocked() && boss.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.prevAnimTick = 0.0;
            this.currAnimTick = 0.0;
            this.animSpeed = HydraAttackTiming.getAnimSpeed(ownedState, boss.phase >= 2, boss.getSeveredHeadCount());
            boss.getNavigation().stop();
            if (timeline != null && timeline.animKey() != null) {
                boss.playActionAnim(timeline.animKey(), animSpeed);
            }
        }

        @Override
        public void stop() {
            boss.setAllHeadsAnimationLocked(false);
        }

        @Override
        public void tick() {
            if (!(boss.level() instanceof ServerLevel level)) return;
            this.prevAnimTick = this.currAnimTick;
            this.currAnimTick += this.animSpeed;
            onSyncedTick(level, boss.getTarget(), prevAnimTick, currAnimTick);
            if (timeline != null && currAnimTick >= timeline.totalTicks()) {
                boss.finishSkill(restAfterSkill());
            }
        }

        protected int restAfterSkill() {
            return 24;
        }

        protected abstract void onSyncedTick(ServerLevel level, @Nullable LivingEntity target, double prevT, double currT);
    }

    // --- SKILL 1: TRIPLE BITE (`triple_bite`, 52t, impacts: 12, 24, 36) ---
    private static class TripleBiteGoal extends BaseSyncedHydraSkillGoal {
        private final List<Integer> strikeHeads = new ArrayList<>();
        private final Vec3[] lockedTargets = new Vec3[3];
        private final boolean[] warnSent = new boolean[3];
        private final List<Set<UUID>> hitSets = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());

        public TripleBiteGoal(HydraBossEntity boss) {
            super(boss, SkillState.TRIPLE_BITE);
        }

        @Override
        public void start() {
            super.start();
            strikeHeads.clear();
            Arrays.fill(lockedTargets, null);
            Arrays.fill(warnSent, false);
            for (Set<UUID> s : hitSets) s.clear();

            // Khớp thứ tự animation `triple_bite`: đầu 0 (outer_l), 3 (outer_r), 1 (inner_l) nếu còn sống; fallback đầu sống khác
            int[] pref = { 0, 3, 1, 2 };
            List<Integer> alive = boss.getAliveHeadIndices();
            for (int p : pref) {
                if (alive.contains(p) && strikeHeads.size() < 3) {
                    strikeHeads.add(p);
                }
            }
            if (strikeHeads.isEmpty() && !alive.isEmpty()) strikeHeads.add(alive.get(0));
            for (int h : strikeHeads) boss.setHeadAnimationLock(h, true);
        }

        @Override
        protected void onSyncedTick(ServerLevel level, @Nullable LivingEntity target, double prevT, double currT) {
            if (strikeHeads.isEmpty()) {
                boss.finishSkill(20);
                return;
            }
            if (target != null && target.isAlive() && currT < 34.0) {
                boss.smoothRotateToward(target.position().subtract(boss.position()), 7.5f,
                        (float) SpiritHydraConfig.get().phase2TurnSpeedMultiplier);
            }

            double eff = boss.getEffectiveScale();
            HitWindow[] wins = timeline.hitWindows();
            for (int k = 0; k < Math.min(strikeHeads.size(), wins.length); k++) {
                int hIdx = strikeHeads.get(k);
                if (!boss.isHeadAlive(hIdx)) continue;
                HitWindow w = wins[k];
                Vec3 headPos = boss.getHeadWorldPosition(hIdx);

                int warnTick = Math.max(1, w.hitStartTick() - 8);
                if (!warnSent[k] && currT >= warnTick) {
                    warnSent[k] = true;
                    lockedTargets[k] = (target != null && target.isAlive())
                            ? target.position().add(0, 0.9, 0)
                            : headPos.add(boss.getLookAngle().scale(4.2 * eff));
                    level.playSound(null, BlockPos.containing(headPos), ModSounds.HYDRA_ROAR,
                            SoundSource.HOSTILE, 1.8f, 1.02f + k * 0.08f);
                    ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_BITE_TELEGRAPH,
                            headPos, lockedTargets[k].subtract(headPos), 3.4f * boss.getScaleRatio(), boss.isEnragedVisual());
                }
                if (currT >= warnTick && currT < w.hitStartTick() - 2 && target != null && target.isAlive()) {
                    lockedTargets[k] = target.position().add(0, 0.9, 0);
                }

                if (w.crossedImpact(prevT, currT)) {
                    Vec3 strikePt = lockedTargets[k] != null ? lockedTargets[k] : headPos.add(boss.getLookAngle().scale(4.0 * eff));
                    level.playSound(null, BlockPos.containing(strikePt), ModSounds.HYDRA_BITE,
                            SoundSource.HOSTILE, 2.5f, 0.72f + k * 0.07f);
                    ModNetworking.sendShakeNearby(level, strikePt, 44.0, 1.35f, 8);
                    ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_BITE_IMPACT,
                            strikePt, strikePt.subtract(headPos), 3.2f * boss.getScaleRatio(), boss.isEnragedVisual());
                }

                if (w.isActive(prevT, currT)) {
                    Vec3 strikePt = lockedTargets[k] != null ? lockedTargets[k] : headPos.add(boss.getLookAngle().scale(4.0 * eff));
                    float dmg = SpiritHydraConfig.get().tripleBiteDamage * boss.skillDamageScale();
                    double hitRadius = 3.2 * eff;
                    AABB box = new AABB(strikePt.x - hitRadius, strikePt.y - 2.5, strikePt.z - hitRadius,
                            strikePt.x + hitRadius, strikePt.y + 3.2, strikePt.z + hitRadius);
                    for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                        if (p.position().distanceTo(strikePt) <= hitRadius && hitSets.get(k).add(p.getUUID())) {
                            p.hurt(boss.damageSources().mobAttack(boss), dmg);
                            Vec3 push = p.position().subtract(headPos);
                            Vec3 n = push.horizontalDistanceSqr() > 1e-4 ? new Vec3(push.x, 0, push.z).normalize() : boss.getLookAngle();
                            p.setDeltaMovement(p.getDeltaMovement().add(n.x * 0.85, 0.42, n.z * 0.85));
                            p.hurtMarked = true;
                        }
                    }
                }
            }
        }
    }

    // --- SKILL 2: SPIRIT BREATH (`breath`, 56t, active sweep 16..44 từ trái -> phải) ---
    private static class SpiritBreathGoal extends BaseSyncedHydraSkillGoal {
        private int breathHead = 1;
        private Vec3 baseForward = new Vec3(0, 0, 1);
        private int lastDamageStep = -1;
        private boolean warnSent = false;

        public SpiritBreathGoal(HydraBossEntity boss) {
            super(boss, SkillState.SPIRIT_BREATH);
        }

        @Override
        public void start() {
            super.start();
            List<Integer> alive = boss.getAliveHeadIndices();
            breathHead = alive.contains(1) ? 1 : (alive.contains(2) ? 2 : alive.get(0));
            boss.setHeadAnimationLock(1, true);
            boss.setHeadAnimationLock(2, true);
            baseForward = boss.getLookAngle().multiply(1, 0, 1);
            if (baseForward.lengthSqr() < 1e-4) baseForward = new Vec3(0, 0, 1);
            baseForward = baseForward.normalize();
            lastDamageStep = -1;
            warnSent = false;
        }

        @Override
        protected void onSyncedTick(ServerLevel level, @Nullable LivingEntity target, double prevT, double currT) {
            if (!boss.isHeadAlive(breathHead)) {
                List<Integer> alive = boss.getAliveHeadIndices();
                if (alive.isEmpty()) {
                    boss.finishSkill(18);
                    return;
                }
                breathHead = alive.get(0);
            }

            HitWindow win = timeline.hitWindows()[0];
            Vec3 origin = boss.getHeadWorldPosition(breathHead);
            double coneLen = 13.5 * boss.getEffectiveScale();

            if (!warnSent && currT >= 1.0) {
                warnSent = true;
                level.playSound(null, BlockPos.containing(origin), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.3f, 0.85f);
                ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_BREATH_TELEGRAPH,
                        origin, baseForward, (float) coneLen, boss.isEnragedVisual());
            }

            if (currT < timeline.lockAimTick() && target != null && target.isAlive()) {
                Vec3 aim = target.position().subtract(origin);
                if (aim.horizontalDistanceSqr() > 1e-4) {
                    baseForward = baseForward.lerp(new Vec3(aim.x, 0, aim.z).normalize(), 0.25).normalize();
                    boss.smoothRotateToward(baseForward, 6.5f, (float) SpiritHydraConfig.get().phase2TurnSpeedMultiplier);
                }
            }

            if (win.crossedImpact(prevT, currT)) {
                level.playSound(null, BlockPos.containing(origin), ModSounds.HYDRA_BREATH, SoundSource.HOSTILE, 2.8f, 0.78f);
            }

            if (win.isActive(prevT, currT)) {
                // Quét hình cung ngang mượt từ trái (+32°) sang phải (-32°) khớp chính xác animation `breath`!
                double frac = Mth.clamp((currT - win.hitStartTick()) / Math.max(1.0, win.hitEndTick() - win.hitStartTick()), 0.0, 1.0);
                double sweepRad = Math.toRadians(Mth.lerp((float) frac, -32.0f, +32.0f));
                Vec3 sweepDir = rotateY(baseForward, sweepRad);

                int step = (int) Math.floor((currT - win.hitStartTick()) / 4.0);
                if (step > lastDamageStep) {
                    lastDamageStep = step;
                    ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_BREATH_STREAM,
                            origin, sweepDir, (float) coneLen, boss.isEnragedVisual());

                    float tickDmg = SpiritHydraConfig.get().spiritBreathTickDamage * boss.skillDamageScale();
                    double halfAngleCos = Math.cos(Math.toRadians(22.0));
                    AABB box = new AABB(origin.x - coneLen, boss.getY() - 2, origin.z - coneLen,
                            origin.x + coneLen, origin.y + 4, origin.z + coneLen);
                    for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                        Vec3 toP = p.position().subtract(origin);
                        double hd = toP.horizontalDistance();
                        if (hd > 0.5 && hd <= coneLen) {
                            Vec3 hDir = new Vec3(toP.x, 0, toP.z).normalize();
                            if (hDir.dot(sweepDir) >= halfAngleCos) {
                                p.hurt(boss.damageSources().mobAttack(boss), tickDmg);
                            }
                        }
                    }
                }
            }
        }
    }

    // --- SKILL 3: SOUL SPIT (`spit`, 32t, staggered launches: 13, 16, 19, 22) ---
    private static class SoulSpitGoal extends BaseSyncedHydraSkillGoal {
        private boolean chargeSent = false;

        public SoulSpitGoal(HydraBossEntity boss) {
            super(boss, SkillState.SOUL_SPIT);
        }

        @Override
        public void start() {
            super.start();
            chargeSent = false;
            boss.setAllHeadsAnimationLocked(true);
        }

        @Override
        protected void onSyncedTick(ServerLevel level, @Nullable LivingEntity target, double prevT, double currT) {
            if (target != null && target.isAlive() && currT < timeline.lockAimTick()) {
                boss.smoothRotateToward(target.position().subtract(boss.position()), 7.0f,
                        (float) SpiritHydraConfig.get().phase2TurnSpeedMultiplier);
            }
            if (!chargeSent && currT >= 2.0) {
                chargeSent = true;
                for (int h : boss.getAliveHeadIndices()) {
                    ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_SPIT_CHARGE,
                            boss.getHeadWorldPosition(h), boss.getLookAngle(), 2.0f * boss.getScaleRatio(), boss.isEnragedVisual());
                }
            }

            double eff = boss.getEffectiveScale();
            HitWindow[] wins = timeline.hitWindows();
            for (int h = 0; h < Math.min(HEAD_COUNT, wins.length); h++) {
                if (!boss.isHeadAlive(h)) continue;
                if (wins[h].crossedImpact(prevT, currT)) {
                    Vec3 anchor = target != null && target.isAlive()
                            ? target.position()
                            : boss.position().add(boss.getLookAngle().scale(12.0 * eff));
                    Vec3 origin = boss.getHeadWorldPosition(h);
                    float dmg = SpiritHydraConfig.get().soulSpitDamage * boss.skillDamageScale();
                    float gravity = 0.04f;
                    int flightTicks = 24;

                    double spreadAng = (h - 1.5) * 0.45;
                    Vec3 landSpot = anchor.add(Math.cos(spreadAng) * (h == 1 ? 0.5 : 2.4 * eff),
                            0.2, Math.sin(spreadAng) * (h == 1 ? 0.5 : 2.4 * eff));
                    Vec3 diff = landSpot.subtract(origin);
                    Vec3 vel = new Vec3(diff.x / flightTicks,
                            (diff.y + 0.5 * gravity * flightTicks * flightTicks) / flightTicks,
                            diff.z / flightTicks);

                    level.playSound(null, BlockPos.containing(origin), ModSounds.HYDRA_SPIT, SoundSource.HOSTILE, 2.3f, 0.80f + h * 0.05f);
                    AbyssalOrbEntity.spawnParabolic(level, boss, origin, vel, gravity,
                            0.85f * (float) eff, boss.isEnragedVisual() ? COLOR_ENRAGED_VIOLET : COLOR_SOUL_SKY,
                            dmg, 3.0f * (float) eff, false, false, true);
                }
            }
        }
    }

    // --- SKILL 4: TAIL / BODY SLAM (`slam`, 36t, impact 17, active 16..20) ---
    private static class TailBodySlamGoal extends BaseSyncedHydraSkillGoal {
        private final Set<UUID> hitPlayers = new HashSet<>();
        private boolean warnSent = false;

        public TailBodySlamGoal(HydraBossEntity boss) {
            super(boss, SkillState.TAIL_BODY_SLAM);
        }

        @Override
        public void start() {
            super.start();
            hitPlayers.clear();
            warnSent = false;
            boss.setAllHeadsAnimationLocked(true);
        }

        @Override
        protected void onSyncedTick(ServerLevel level, @Nullable LivingEntity target, double prevT, double currT) {
            Vec3 forward = boss.getLookAngle().multiply(1, 0, 1);
            if (forward.lengthSqr() < 1e-4) forward = new Vec3(0, 0, 1);
            forward = forward.normalize();
            double eff = boss.getEffectiveScale();
            double range = (boss.phase >= 2 ? 12.0 : 10.0) * eff;
            HitWindow win = timeline.hitWindows()[0];

            if (!warnSent && currT >= 1.0) {
                warnSent = true;
                level.playSound(null, boss.blockPosition(), ModSounds.HYDRA_ROAR, SoundSource.HOSTILE, 2.5f, 0.70f);
                ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_SLAM_TELEGRAPH,
                        boss.position(), forward, (float) range, boss.isEnragedVisual());
            }

            if (win.crossedImpact(prevT, currT)) {
                level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.8f, 0.65f);
                ModNetworking.sendShakeNearby(level, boss.position(), 72.0, 2.1f, 14);
                EnergySlashEntity.spawn(level, boss, boss.position().add(0, 1.2 * eff, 0), forward, 1.5f, 8.5f * (float) eff, 0f,
                        boss.isEnragedVisual() ? COLOR_ENRAGED_VIOLET : COLOR_SPIRIT_CYAN, 0f, false);
                ShockwaveRingEntity.spawn(level, boss.position(), (float) range * 0.85f,
                        boss.isEnragedVisual() ? COLOR_ENRAGED_ICE : COLOR_SOUL_SKY, 16);
                ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_SLAM_IMPACT,
                        boss.position(), forward, (float) range, boss.isEnragedVisual());
            }

            if (win.isActive(prevT, currT)) {
                float dmg = SpiritHydraConfig.get().tailBodySlamDamage * boss.skillDamageScale();
                AABB box = boss.getBoundingBox().inflate(range);
                for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    Vec3 toP = p.position().subtract(boss.position());
                    double d = toP.horizontalDistance();
                    if (d <= range && hitPlayers.add(p.getUUID())) {
                        Vec3 hDir = d > 1e-3 ? new Vec3(toP.x, 0, toP.z).normalize() : forward;
                        if (d < 5.0 * eff || Math.abs(hDir.dot(forward)) > 0.20) {
                            p.hurt(boss.damageSources().mobAttack(boss), dmg);
                            p.setDeltaMovement(p.getDeltaMovement().add(hDir.x * 1.25, 0.92, hDir.z * 1.25));
                            p.hurtMarked = true;
                        }
                    }
                }
            }
        }
    }

    // --- SKILL 5: SUMMON WISPS (`summon_wisps`, 36t, impact 18) ---
    private static class SummonWispsGoal extends BaseSyncedHydraSkillGoal {
        private boolean warnSent = false;

        public SummonWispsGoal(HydraBossEntity boss) {
            super(boss, SkillState.SUMMON_WISPS);
        }

        @Override
        public void start() {
            super.start();
            warnSent = false;
            boss.setAllHeadsAnimationLocked(true);
        }

        @Override
        protected void onSyncedTick(ServerLevel level, @Nullable LivingEntity target, double prevT, double currT) {
            double eff = boss.getEffectiveScale();
            HitWindow win = timeline.hitWindows()[0];

            if (!warnSent && currT >= 1.0) {
                warnSent = true;
                level.playSound(null, boss.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.6f, 0.72f);
                ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_NOVA_RING,
                        boss.position().add(0, 1.0 * eff, 0), boss.getLookAngle(), 5.5f * (float) eff, boss.isEnragedVisual());
            }

            if (win.crossedImpact(prevT, currT)) {
                int cap = Math.max(0, SpiritHydraConfig.get().maxActiveWisps - boss.activeWisps.size());
                int count = Math.min(cap, 2 + level.random.nextInt(3));
                double ringR = 4.8 * eff;
                for (int i = 0; i < count; i++) {
                    Vex vex = EntityType.VEX.create(level);
                    if (vex != null) {
                        double a = (Math.PI * 2.0 * i) / Math.max(1, count);
                        double sx = boss.getX() + Math.cos(a) * ringR;
                        double sz = boss.getZ() + Math.sin(a) * ringR;
                        vex.moveTo(sx, boss.getY() + 2.0 * eff, sz, level.random.nextFloat() * 360f, 0f);
                        vex.finalizeSpawn(level, level.getCurrentDifficultyAt(vex.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
                        vex.setCustomName(Component.literal("Spirit Wisp").withStyle(ChatFormatting.AQUA));
                        vex.setBoundOrigin(boss.blockPosition());
                        vex.setLimitedLife(20 * 45);
                        if (target != null) vex.setTarget(target);
                        level.addFreshEntity(vex);
                        boss.activeWisps.add(vex.getUUID());
                    }
                }
                ShockwaveRingEntity.spawn(level, boss.position(), 8.5f * (float) eff, COLOR_SPIRIT_CYAN, 16);
            }
        }
    }

    // --- SKILL 6 (Phase 2): ALL-HEAD BARRAGE (`all_head_barrage`, 44t, 2 volleys at 16 & 28) ---
    private static class AllHeadBarrageGoal extends BaseSyncedHydraSkillGoal {
        private boolean warnSent = false;

        public AllHeadBarrageGoal(HydraBossEntity boss) {
            super(boss, SkillState.ALL_HEAD_BARRAGE);
        }

        @Override
        public void start() {
            super.start();
            warnSent = false;
            boss.setAllHeadsAnimationLocked(true);
        }

        @Override
        protected void onSyncedTick(ServerLevel level, @Nullable LivingEntity target, double prevT, double currT) {
            double eff = boss.getEffectiveScale();
            if (!warnSent && currT >= 1.0) {
                warnSent = true;
                level.playSound(null, boss.blockPosition(), ModSounds.HYDRA_ROAR, SoundSource.HOSTILE, 2.8f, 0.85f);
                for (int h : boss.getAliveHeadIndices()) {
                    ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_SPIT_CHARGE,
                            boss.getHeadWorldPosition(h), boss.getLookAngle(), 2.4f * boss.getScaleRatio(), true);
                }
            }

            HitWindow[] wins = timeline.hitWindows();
            for (int vIdx = 0; vIdx < wins.length; vIdx++) {
                if (wins[vIdx].crossedImpact(prevT, currT)) {
                    Vec3 anchor = target != null && target.isAlive()
                            ? target.position()
                            : boss.position().add(boss.getLookAngle().scale(10.0 * eff));
                    float dmg = SpiritHydraConfig.get().allHeadBarrageDamage * boss.skillDamageScale();
                    List<Integer> alive = boss.getAliveHeadIndices();
                    level.playSound(null, boss.blockPosition(), ModSounds.HYDRA_SPIT, SoundSource.HOSTILE, 2.8f, 0.72f);

                    for (int idx = 0; idx < alive.size(); idx++) {
                        int h = alive.get(idx);
                        Vec3 origin = boss.getHeadWorldPosition(h);
                        double ang = (Math.PI * 2.0 * idx) / Math.max(1, alive.size()) + (vIdx * 0.42);
                        double rad = (idx == 0) ? 0.6 : ((2.5 + level.random.nextDouble() * 5.2) * eff);
                        Vec3 spot = anchor.add(Math.cos(ang) * rad, 0.05, Math.sin(ang) * rad);

                        MagicCircleEntity.spawn(level, boss, spot, 2.6f * (float) eff, 22, COLOR_ENRAGED_VIOLET, dmg * 0.85f, false, false);
                        float grav = 0.045f;
                        int flight = 22;
                        Vec3 diff = spot.subtract(origin);
                        Vec3 vel = new Vec3(diff.x / flight, (diff.y + 0.5 * grav * flight * flight) / flight, diff.z / flight);
                        AbyssalOrbEntity.spawnParabolic(level, boss, origin, vel, grav,
                                0.75f * (float) eff, COLOR_ENRAGED_ICE, dmg, 2.4f * (float) eff, false, false, false);
                    }
                }
            }
        }
    }

    // --- SKILL 7 (Phase 2): SPIRIT NOVA (`spirit_nova`, 48t, expanding jumpable ring over 18..42) ---
    private static class SpiritNovaGoal extends BaseSyncedHydraSkillGoal {
        private double curR = 0.0;
        private int lastRingVfxStep = -1;
        private boolean warnSent = false;
        private final Set<UUID> hit = new HashSet<>();

        public SpiritNovaGoal(HydraBossEntity boss) {
            super(boss, SkillState.SPIRIT_NOVA);
        }

        @Override
        public void start() {
            super.start();
            curR = 0.0;
            lastRingVfxStep = -1;
            warnSent = false;
            hit.clear();
            boss.setAllHeadsAnimationLocked(true);
        }

        @Override
        protected void onSyncedTick(ServerLevel level, @Nullable LivingEntity target, double prevT, double currT) {
            double eff = boss.getEffectiveScale();
            double maxR = 17.5 * eff;
            HitWindow win = timeline.hitWindows()[0];

            if (!warnSent && currT >= 1.0) {
                warnSent = true;
                level.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.8f, 0.65f);
                ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_WRATH_CHARGE,
                        boss.position().add(0, 1.0 * eff, 0), boss.getLookAngle(), 8.0f * (float) eff, true);
            }

            if (win.crossedImpact(prevT, currT)) {
                level.playSound(null, boss.blockPosition(), ModSounds.HYDRA_ROAR, SoundSource.HOSTILE, 3.2f, 0.60f);
                ModNetworking.sendShakeNearby(level, boss.position(), 88.0, 2.4f, 16);
                ShockwaveRingEntity.spawn(level, boss.position().add(0, 0.25, 0), (float) maxR, COLOR_ENRAGED_VIOLET, 24);
                ShockwaveRingEntity.spawn(level, boss.position().add(0, 0.45, 0), (float) maxR, COLOR_ENRAGED_ICE, 24);
            }

            if (win.isActive(prevT, currT)) {
                double prevR = curR;
                double frac = Mth.clamp((currT - win.hitStartTick()) / Math.max(1.0, win.hitEndTick() - win.hitStartTick()), 0.0, 1.0);
                curR = frac * maxR;

                int vStep = (int) Math.floor((currT - win.hitStartTick()) / 4.0);
                if (vStep > lastRingVfxStep) {
                    lastRingVfxStep = vStep;
                    ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_NOVA_RING,
                            boss.position().add(0, 0.45, 0), boss.getLookAngle(), (float) curR, true);
                }

                float dmg = SpiritHydraConfig.get().spiritNovaDamage * boss.skillDamageScale();
                AABB box = new AABB(boss.getX() - curR - 4, boss.getY() - 4, boss.getZ() - curR - 4,
                        boss.getX() + curR + 4, boss.getY() + 6, boss.getZ() + curR + 4);
                for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    double hd = Math.hypot(p.getX() - boss.getX(), p.getZ() - boss.getZ());
                    boolean inRing = hd >= Math.max(0.0, prevR - 1.6 * eff) && hd <= curR + 1.6 * eff;
                    boolean jumpedOver = !p.onGround() && (p.getY() - boss.getY()) > 1.05;
                    if (inRing && !jumpedOver && hit.add(p.getUUID())) {
                        p.hurt(boss.damageSources().mobAttack(boss), dmg);
                        Vec3 push = p.position().subtract(boss.position());
                        Vec3 n = push.horizontalDistanceSqr() > 1e-4 ? new Vec3(push.x, 0, push.z).normalize() : new Vec3(1, 0, 0);
                        p.setDeltaMovement(p.getDeltaMovement().add(n.x * 1.25, 0.55, n.z * 1.25));
                        p.hurtMarked = true;
                    }
                }
            }
        }
    }

    // --- SKILL 8 (Phase 2): SOUL CHAIN (`soul_chain`, 40t, lash+pull at 14, finisher bite at 27) ---
    private static class SoulChainGoal extends BaseSyncedHydraSkillGoal {
        private int chainHead = 1;
        @Nullable
        private UUID chainedPlayerId = null;
        private boolean warnSent = false;
        private final Set<UUID> biteHit = new HashSet<>();

        public SoulChainGoal(HydraBossEntity boss) {
            super(boss, SkillState.SOUL_CHAIN);
        }

        @Override
        public void start() {
            super.start();
            List<Integer> alive = boss.getAliveHeadIndices();
            chainHead = alive.contains(1) ? 1 : (alive.contains(2) ? 2 : alive.get(0));
            boss.setHeadAnimationLock(1, true);
            boss.setHeadAnimationLock(2, true);
            chainedPlayerId = null;
            warnSent = false;
            biteHit.clear();
        }

        @Override
        protected void onSyncedTick(ServerLevel level, @Nullable LivingEntity target, double prevT, double currT) {
            double eff = boss.getEffectiveScale();
            Vec3 headPos = boss.getHeadWorldPosition(chainHead);
            HitWindow lashWin = timeline.hitWindows()[0];
            HitWindow biteWin = timeline.hitWindows()[1];

            if (target != null && target.isAlive() && currT < biteWin.hitStartTick()) {
                boss.smoothRotateToward(target.position().subtract(boss.position()), 8.0f,
                        (float) SpiritHydraConfig.get().phase2TurnSpeedMultiplier);
            }
            if (!warnSent && currT >= 1.0) {
                warnSent = true;
                level.playSound(null, BlockPos.containing(headPos), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2.4f, 0.65f);
                if (target != null && target.isAlive()) {
                    ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_CHAIN_LINE,
                            headPos, target.position().add(0, 1.0, 0).subtract(headPos), 1.0f, true);
                }
            }

            if (lashWin.crossedImpact(prevT, currT) && target instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator()) {
                if (boss.distanceTo(p) <= 24.0 * eff) {
                    chainedPlayerId = p.getUUID();
                    float pullDmg = SpiritHydraConfig.get().soulChainPullDamage * boss.skillDamageScale();
                    p.hurt(boss.damageSources().mobAttack(boss), pullDmg);
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                    level.playSound(null, p.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2.4f, 0.68f);
                }
            }

            if (currT >= lashWin.impactTick() && currT < biteWin.hitStartTick() - 1 && chainedPlayerId != null) {
                Entity e = level.getEntity(chainedPlayerId);
                if (e instanceof Player p && p.isAlive()) {
                    Vec3 pullGoal = boss.position().add(boss.getLookAngle().multiply(1, 0, 1).normalize().scale(4.2 * eff));
                    Vec3 pullVec = pullGoal.subtract(p.position());
                    if (pullVec.length() > 1.8) {
                        p.setDeltaMovement(pullVec.normalize().scale(1.15).add(0, 0.18, 0));
                        p.hurtMarked = true;
                    }
                    if (((int) currT) % 4 == 0) {
                        ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_CHAIN_LINE,
                                headPos, p.position().add(0, 1.0, 0).subtract(headPos), 1.0f, true);
                    }
                }
            }

            if (biteWin.crossedImpact(prevT, currT)) {
                Vec3 biteCenter = boss.getHeadWorldPosition(chainHead);
                level.playSound(null, BlockPos.containing(biteCenter), ModSounds.HYDRA_BITE, SoundSource.HOSTILE, 2.8f, 0.65f);
                ModNetworking.sendShakeNearby(level, biteCenter, 48.0, 1.8f, 12);
                ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_BITE_IMPACT,
                        biteCenter, boss.getLookAngle(), 4.2f * boss.getScaleRatio(), true);
            }

            if (biteWin.isActive(prevT, currT)) {
                Vec3 biteCenter = boss.getHeadWorldPosition(chainHead);
                float biteDmg = SpiritHydraConfig.get().soulChainBiteDamage * boss.skillDamageScale();
                double hitR = 4.6 * eff;
                AABB box = new AABB(biteCenter.x - hitR, boss.getY() - 2, biteCenter.z - hitR,
                        biteCenter.x + hitR, biteCenter.y + 4, biteCenter.z + hitR);
                for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    if (p.position().distanceTo(biteCenter) <= hitR && biteHit.add(p.getUUID())) {
                        p.hurt(boss.damageSources().mobAttack(boss), biteDmg);
                    }
                }
            }
        }
    }

    // --- SKILL 9 (ULTIMATE Phase 2): WRATH OF MANY HEADS (`wrath_of_many_heads`, 64t, charge 0..36, 360° rampage 38..44) ---
    private static class WrathOfManyHeadsGoal extends BaseSyncedHydraSkillGoal {
        private boolean announced = false;
        private int lastChargePulse = -1;
        private final Set<UUID> hitPlayers = new HashSet<>();

        public WrathOfManyHeadsGoal(HydraBossEntity boss) {
            super(boss, SkillState.WRATH_OF_MANY_HEADS);
        }

        @Override
        public void start() {
            super.start();
            announced = false;
            lastChargePulse = -1;
            hitPlayers.clear();
            boss.setAllHeadsAnimationLocked(true);
        }

        @Override
        protected int restAfterSkill() {
            return 34;
        }

        @Override
        protected void onSyncedTick(ServerLevel level, @Nullable LivingEntity target, double prevT, double currT) {
            boss.setDeltaMovement(Vec3.ZERO);
            double eff = boss.getEffectiveScale();
            double ringR = 12.5 * eff;
            HitWindow win = timeline.hitWindows()[0];

            if (!announced && currT >= 1.0) {
                announced = true;
                boss.announceToNearbyWithRadius(level,
                        Component.literal("☠ TUYỆT KỸ SPIRIT HYDRA: WRATH OF MANY HEADS — CẢ 4 ĐẦU SẮP CẮN XÉ 360°! ☠")
                                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                        SpiritHydraConfig.get().bossBarRadius);
                level.playSound(null, boss.blockPosition(), ModSounds.HYDRA_PHASE_CHANGE, SoundSource.HOSTILE, 3.2f, 0.68f);
            }

            if (currT < win.hitStartTick()) {
                int pulse = (int) Math.floor(currT / 8.0);
                if (pulse > lastChargePulse) {
                    lastChargePulse = pulse;
                    ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_WRATH_CHARGE,
                            boss.position().add(0, 0.6, 0), boss.getLookAngle(), (float) ringR, true);
                }
                return;
            }

            if (win.crossedImpact(prevT, currT)) {
                level.playSound(null, boss.blockPosition(), ModSounds.HYDRA_BITE, SoundSource.HOSTILE, 3.4f, 0.58f);
                level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.62f);
                ModNetworking.sendShakeNearby(level, boss.position(), 100.0, 3.4f, 26);

                ShockwaveRingEntity.spawn(level, boss.position(), (float) (ringR * 1.25), COLOR_ENRAGED_VIOLET, 24);
                ShockwaveRingEntity.spawn(level, boss.position().add(0, 1.0 * eff, 0), (float) ringR, COLOR_ENRAGED_ICE, 20);
                ModNetworking.sendHydraVfxNearby(level, HydraVfxPayload.KIND_WRATH_IMPACT,
                        boss.position().add(0, 1.0, 0), boss.getLookAngle(), (float) ringR, true);
            }

            if (win.isActive(prevT, currT)) {
                float dmg = SpiritHydraConfig.get().wrathOfManyHeadsDamage * boss.skillDamageScale();
                AABB box = new AABB(boss.getX() - ringR, boss.getY() - 5, boss.getZ() - ringR,
                        boss.getX() + ringR, boss.getY() + 7 * eff, boss.getZ() + ringR);
                for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    double d = Math.hypot(p.getX() - boss.getX(), p.getZ() - boss.getZ());
                    if (d <= ringR && hitPlayers.add(p.getUUID())) {
                        p.hurt(boss.damageSources().mobAttack(boss), dmg);
                        Vec3 away = p.position().subtract(boss.position());
                        Vec3 n = away.horizontalDistanceSqr() > 1e-4 ? new Vec3(away.x, 0, away.z).normalize() : new Vec3(1, 0, 0);
                        p.setDeltaMovement(p.getDeltaMovement().add(n.x * 1.5, 0.9, n.z * 1.5));
                        p.hurtMarked = true;
                    }
                }
            }
        }
    }
}
