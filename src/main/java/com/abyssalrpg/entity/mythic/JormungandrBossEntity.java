package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.config.JormungandrConfig;
import com.abyssalrpg.entity.mythic.jormungandr.JormungandrHitboxPartEntity;
import com.abyssalrpg.entity.mythic.jormungandr.JormungandrHitboxPartEntity.PartZone;
import com.abyssalrpg.entity.mythic.jormungandr.JormungandrSkillGoals;
import com.abyssalrpg.entity.mythic.jormungandr.SerpentPath;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.JormungandrPathSyncPayload;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BOSS JÖRMUNGANDR — Rắn Hổ Mang Thần Thoại Khổng Lồ (GeckoLib 4.8.3 + Kiến
 * trúc thân phẳng SerpentPath).
 *
 * Tái sử dụng trọn bộ hạ tầng dùng chung của mod:
 * - {@link AbstractGeoMythicBoss}: chuẩn hóa góc chống xoay đầu, khóa chuyển
 * phase, hồi máu ngoài giao tranh.
 * - {@link com.abyssalrpg.api.BossHealthHolder} + `LivingEntityMixin`: máu tối
 * đa 1800 -> 2520 vượt trần 1024.
 * - {@link com.abyssalrpg.api.BossDefenseHolder} + override
 * `getDamageAfterArmorAbsorb`: phòng thủ 30 -> 42 vượt trần 30.
 * - {@link SerpentPath} + {@link JormungandrPathSyncPayload}: Server tính đường
 * dẫn thân (cuộn, ngẩng cổ,
 * Constrict Coil, Ouroboros Ring có lỗ hổng quay chậm) và đồng bộ 20 điểm lượng
 * tử hóa sang Client mỗi 2 tick.
 * - 12 Proxy Hitbox vô hình ({@link JormungandrHitboxPartEntity}) dọc thân với
 * hệ số sát thương theo vùng:
 * Đầu ×1.5, Thân ×1.0, 1/3 cuối đuôi ×0.75.
 */
public class JormungandrBossEntity extends AbstractGeoMythicBoss {
    // --- Hằng số tên bone PHẲNG & locator hiệu ứng ---
    public static final String HEAD_BONE = "head";
    public static final String JAW_BONE = "jaw";
    public static final String TONGUE_BONE = "tongue";
    public static final String HORN_L_BONE = "horn_l";
    public static final String HORN_R_BONE = "horn_r";
    public static final String HOOD_L_BONE = "hood_l";
    public static final String HOOD_R_BONE = "hood_r";
    public static final String SEGMENT_PREFIX = "segment_"; // segment_1 .. segment_54 (phẳng, không cha-con)
    public static final String TAIL_TIP_BONE = "tail_tip";
    public static final String LOC_MOUTH_VENOM = "mouth_venom";
    public static final String LOC_HOOD_L_EDGE = "hood_l_edge";
    public static final String LOC_HOOD_R_EDGE = "hood_r_edge";
    public static final String LOC_TAIL_TIP_FX = "tail_tip_fx";

    /** Hitbox chính nhỏ quanh vùng đầu để collision nhẹ, không lag chunk. */
    public static final float CORE_HEAD_WIDTH = 2.4f;
    public static final float CORE_HEAD_HEIGHT = 2.4f;
    public static final float CORE_EYE_HEIGHT = 1.8f;

    // --- Màu VFX (tông rắn hổ mang đen bóng / tím mang cổ / độc xanh-tím) ---
    public static final int COLOR_SERPENT_VIOLET = 0x8A74D9;
    public static final int COLOR_HOOD_LAVENDER = 0xB7A6EE;
    public static final int COLOR_VENOM_GREEN = 0x7CE64F;
    public static final int COLOR_CRIMSON_EYE = 0xE01818;
    public static final int COLOR_ENRAGED_WHITE_VIOLET = 0xDFB8FF;

    private static final ResourceLocation PHASE2_DAMAGE_MOD_ID = AbyssalRpgMod.id("jormungandr_phase2_damage");
    private static final ResourceLocation PHASE2_SPEED_MOD_ID = AbyssalRpgMod.id("jormungandr_phase2_speed");

    // --- 17 Animation theo danh sách mục 9.5 ---
    private static final RawAnimation ANIM_IDLE_COIL = RawAnimation.begin().thenLoop("animation.jormungandr.idle_coil");
    private static final RawAnimation ANIM_REAR_UP = RawAnimation.begin().thenLoop("animation.jormungandr.rear_up");
    private static final RawAnimation ANIM_SLITHER = RawAnimation.begin().thenLoop("animation.jormungandr.slither");
    private static final RawAnimation ANIM_DEATH = RawAnimation.begin().thenPlayAndHold("animation.jormungandr.death");

    private static final RawAnimation ANIM_STRIKE = RawAnimation.begin().thenPlay("animation.jormungandr.strike");
    private static final RawAnimation ANIM_SPIT = RawAnimation.begin().thenPlay("animation.jormungandr.spit");
    private static final RawAnimation ANIM_TAIL_SLAM = RawAnimation.begin().thenPlay("animation.jormungandr.tail_slam");
    private static final RawAnimation ANIM_CONSTRICT = RawAnimation.begin().thenPlay("animation.jormungandr.constrict");
    private static final RawAnimation ANIM_FLARE = RawAnimation.begin().thenPlay("animation.jormungandr.flare");
    private static final RawAnimation ANIM_BURROW = RawAnimation.begin().thenPlay("animation.jormungandr.burrow");
    private static final RawAnimation ANIM_EMERGE = RawAnimation.begin().thenPlay("animation.jormungandr.emerge");
    private static final RawAnimation ANIM_RAPID_STRIKE = RawAnimation.begin()
            .thenPlay("animation.jormungandr.rapid_strike");
    private static final RawAnimation ANIM_NOVA = RawAnimation.begin().thenPlay("animation.jormungandr.nova");
    private static final RawAnimation ANIM_RAIN = RawAnimation.begin().thenPlay("animation.jormungandr.rain");
    private static final RawAnimation ANIM_OUROBOROS = RawAnimation.begin().thenPlay("animation.jormungandr.ouroboros");
    private static final RawAnimation ANIM_PHASE_CHANGE = RawAnimation.begin()
            .thenPlay("animation.jormungandr.phase_change");
    private static final RawAnimation ANIM_GROW = RawAnimation.begin().thenPlay("animation.jormungandr.grow");

    public enum SkillState {
        IDLE(0),
        // Phase 1 (6 skill)
        STRIKE_BITE(1),
        VENOM_SPIT(2),
        TAIL_SLAM(3),
        CONSTRICT_COIL(4),
        HOOD_FLARE(5),
        BURROW(6),
        // Phase 2 (4 skill mới/nâng cấp)
        TOXIC_NOVA(7),
        RAPID_STRIKE(8),
        VENOM_RAIN(9),
        OUROBOROS_RING(10),
        // Hệ thống
        PHASE_CHANGE(11),
        DEATH(12);

        public final int id;

        SkillState(int id) {
            this.id = id;
        }

        public static SkillState byId(int id) {
            for (SkillState s : values()) {
                if (s.id == id)
                    return s;
            }
            return IDLE;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_SKILL_STATE = SynchedEntityData
            .defineId(JormungandrBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTIVE_SEGMENTS = SynchedEntityData
            .defineId(JormungandrBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BURROWED = SynchedEntityData
            .defineId(JormungandrBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HEAD_ANIM_LOCKED = SynchedEntityData
            .defineId(JormungandrBossEntity.class, EntityDataSerializers.BOOLEAN);

    private static volatile boolean debugLoggingEnabled = false;

    private final SerpentPath serpentPath = new SerpentPath();
    private final List<JormungandrHitboxPartEntity> proxyParts = new ArrayList<>();
    private final EnumMap<SkillState, Integer> skillCooldowns = new EnumMap<>(SkillState.class);
    private final List<VenomPuddle> venomPuddles = new ArrayList<>();
    private final Map<UUID, Long> contactHitCooldowns = new HashMap<>();

    private SkillState lastSkill1 = SkillState.IDLE;
    private SkillState lastSkill2 = SkillState.IDLE;
    private int globalSkillRestTicks = 30;
    private boolean phase2StatsApplied = false;
    private boolean pathInitialized = false;
    private boolean wantRearUp = true;
    private int outOfCombatTicks = 0;

    // Chống kẹt địa hình > 3 giây (60 ticks)
    private Vec3 lastProgressPos = Vec3.ZERO;
    private int stuckTicks = 0;
    private int stuckRecoveryCooldown = 0;

    // Chống double-hit qua 2 proxy chồng nhau trong cùng 1 tick
    private long lastPartHitTick = -1L;
    @Nullable
    private UUID lastPartHitAttacker = null;

    private static final class VenomPuddle {
        final Vec3 pos;
        final float radius;
        final float tickDamage;
        int ticksLeft;

        VenomPuddle(Vec3 pos, float radius, float tickDamage, int ticksLeft) {
            this.pos = pos;
            this.radius = radius;
            this.tickDamage = tickDamage;
            this.ticksLeft = ticksLeft;
        }
    }

    @SuppressWarnings("this-escape")
    public JormungandrBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        this.moveControl = new SerpentSteeringMoveControl(this);
        this.xpReward = 1800;
        this.noCulling = true;
        applyConfiguredPhase1BaseStats();
        this.refreshDimensions();
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        JormungandrConfig cfg = JormungandrConfig.get();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, VANILLA_SAFE_MAX_HEALTH_ATTR)
                .add(Attributes.ATTACK_DAMAGE, cfg.strikeBiteDamage)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0)
                .add(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed)
                .add(Attributes.FOLLOW_RANGE, cfg.detectionRadius)
                .add(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance)
                .add(Attributes.STEP_HEIGHT, 3.0)
                .add(Attributes.SCALE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKILL_STATE, SkillState.IDLE.id);
        builder.define(DATA_ACTIVE_SEGMENTS, JormungandrConfig.get().segmentCountPhase1);
        builder.define(DATA_BURROWED, false);
        builder.define(DATA_HEAD_ANIM_LOCKED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new BossLockGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        JormungandrSkillGoals.registerAll(this, this.goalSelector);
        this.goalSelector.addGoal(5, new SafeLookAtPlayerGoal(this, 56.0f));
        this.goalSelector.addGoal(6, new SafeRandomLookGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    private void applyConfiguredPhase1BaseStats() {
        JormungandrConfig cfg = JormungandrConfig.get();
        setAttr(Attributes.MAX_HEALTH, VANILLA_SAFE_MAX_HEALTH_ATTR);
        setAttr(Attributes.ATTACK_DAMAGE, cfg.strikeBiteDamage);
        setAttr(Attributes.ARMOR, 0.0);
        setAttr(Attributes.ARMOR_TOUGHNESS, 0.0);
        setAttr(Attributes.MOVEMENT_SPEED, cfg.baseMoveSpeed);
        setAttr(Attributes.FOLLOW_RANGE, cfg.detectionRadius);
        setAttr(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance);
        setAttr(Attributes.STEP_HEIGHT, 3.0);
        setAttr(Attributes.SCALE, 1.0);

        if (!this.hasEnteredPhase2) {
            setActiveSegmentCount(cfg.segmentCountPhase1);
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
        this.xpReward = 1800;
        Component title = getStandaloneBossTitle();
        this.setCustomName(title);
        this.setCustomNameVisible(true);
        this.bossEvent.setName(title);
        this.bossEvent.setColor(BossEvent.BossBarColor.BLUE);
        this.triggerSummonEntrance();
    }

    @Override
    protected Component getStandaloneBossTitle() {
        return Component.translatable("entity.abyssalrpg.jormungandr")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
    }

    // --- BossDefenseHolder hooks ---
    @Override
    public float getBossToughness() {
        return JormungandrConfig.get().bossToughness;
    }

    @Override
    public float getMaxDamageReduction() {
        return JormungandrConfig.get().maxDamageReduction;
    }

    @Override
    public float getMinDamagePerHit() {
        return JormungandrConfig.get().minDamagePerHit;
    }

    // --- Hitbox nhỏ ở đầu (đồng bộ theo headScaleMultiplier) + Culling box bao trọn toàn bộ SerpentPath ---
    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        float hsc = JormungandrConfig.get().getEffectiveHeadScale();
        return EntityDimensions.scalable(CORE_HEAD_WIDTH * hsc, CORE_HEAD_HEIGHT * hsc)
                .withEyeHeight(CORE_EYE_HEIGHT * hsc);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.serpentPath.computeTotalBoundingBox(this.position());
    }

    // --- Accessors ---
    public SerpentPath getSerpentPath() {
        return this.serpentPath;
    }

    public SkillState getSkillState() {
        return SkillState.byId(this.entityData.get(DATA_SKILL_STATE));
    }

    public void setSkillState(SkillState st) {
        this.entityData.set(DATA_SKILL_STATE, st.id);
    }

    public int getActiveSegmentCount() {
        return this.entityData.get(DATA_ACTIVE_SEGMENTS);
    }

    public void setActiveSegmentCount(int count) {
        int clamped = Mth.clamp(count, 8, SerpentPath.N_MAX);
        this.entityData.set(DATA_ACTIVE_SEGMENTS, clamped);
        this.serpentPath.setActiveSegments(clamped);
    }

    public boolean isBurrowed() {
        return this.entityData.get(DATA_BURROWED);
    }

    public void setBurrowed(boolean burrowed) {
        this.entityData.set(DATA_BURROWED, burrowed);
    }

    public void setWantRearUp(boolean rear) {
        this.wantRearUp = rear;
    }

    public boolean isWantingRearUp() {
        return this.wantRearUp;
    }

    public boolean isHeadLockedByAnimation() {
        if (this.isRotationLocked() || !this.isAlive() || isBurrowed())
            return true;
        if (this.entityData.get(DATA_HEAD_ANIM_LOCKED))
            return true;
        SkillState st = getSkillState();
        return st == SkillState.STRIKE_BITE
                || st == SkillState.VENOM_SPIT
                || st == SkillState.HOOD_FLARE
                || st == SkillState.RAPID_STRIKE
                || st == SkillState.TOXIC_NOVA
                || st == SkillState.VENOM_RAIN
                || st == SkillState.OUROBOROS_RING
                || st == SkillState.PHASE_CHANGE
                || st == SkillState.DEATH;
    }

    public void setHeadAnimationLocked(boolean locked) {
        this.entityData.set(DATA_HEAD_ANIM_LOCKED, locked);
    }

    public float getPhaseThicknessFactor() {
        if (this.phase >= 2 || this.hasEnteredPhase2) {
            return JormungandrConfig.get().phase2ThicknessScale;
        }
        return 1.0f;
    }

    public float getEffectiveThicknessScale() {
        return JormungandrConfig.get().getEffectiveVisualScale() * getPhaseThicknessFactor();
    }

    /** Vị trí miệng thực tế trong thế giới (đồng bộ theo `visualScaleMultiplier * headScaleMultiplier`) cho skill phun độc. */
    public Vec3 getMouthWorldPos() {
        JormungandrConfig cfg = JormungandrConfig.get();
        Vec3 headNode = this.serpentPath.getServerNode(0);
        double rad = this.yBodyRot * Mth.DEG_TO_RAD;
        double fwdX = -Math.sin(rad);
        double fwdZ = Math.cos(rad);
        double hScale = cfg.getEffectiveHeadScale();
        double offFwd = 1.62 * hScale;
        return new Vec3(headNode.x + fwdX * offFwd, headNode.y - 0.30 * hScale, headNode.z + fwdZ * offFwd);
    }

    /** Tắt khói ambient rời rạc kế thừa từ lớp cha — toàn bộ VFX của Jörmungandr gắn chặt theo locator bộ phận. */
    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        // Không sinh particle trôi lơ lửng không gắn vào bộ phận
    }

    public static boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled || JormungandrConfig.get().debugLogs;
    }

    public static void setDebugLoggingEnabled(boolean enabled) {
        debugLoggingEnabled = enabled;
    }

    public List<JormungandrHitboxPartEntity> getActiveProxyParts() {
        return this.proxyParts;
    }

    public int getGlobalSkillRestTicks() {
        return this.globalSkillRestTicks;
    }

    public void setGlobalSkillRestTicks(int ticks) {
        this.globalSkillRestTicks = ticks <= 0 ? 0 : Math.max(8, Math.round(ticks * 0.65f));
    }

    public int getSkillCooldown(SkillState state) {
        return this.skillCooldowns.getOrDefault(state, 0);
    }

    public boolean isSkillReady(SkillState state) {
        return getSkillCooldown(state) <= 0;
    }

    public void setSkillCooldown(SkillState state, int phase1Ticks) {
        int ticks;
        if (state == SkillState.OUROBOROS_RING) {
            ticks = Math.max(400, phase1Ticks); // Ultimate cố định tối đa mỗi 60 giây
        } else if (this.phase >= 2 || this.hasEnteredPhase2) {
            ticks = Math.max(25, (int) Math.round(phase1Ticks / JormungandrConfig.get().phase2StatMultiplier));
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
            return (float) (phase1Damage * JormungandrConfig.get().phase2StatMultiplier);
        }
        return phase1Damage;
    }

    public int scaledTelegraph(int phase1Ticks) {
        JormungandrConfig cfg = JormungandrConfig.get();
        if (this.phase >= 2 || this.hasEnteredPhase2) {
            return Math.max(cfg.minTelegraphTicks, Math.round(phase1Ticks / 1.30f));
        }
        return Math.max(16, phase1Ticks); // Phase 1 telegraph 0.8 - 1.8 giây
    }

    public void finishSkill(int restTicks) {
        setHeadAnimationLocked(false);
        setBurrowed(false);
        this.serpentPath.setOverrideNone();
        setSkillState(SkillState.IDLE);
        setGlobalSkillRestTicks(restTicks);
        snapSyncPrevAngles();
    }

    public void playActionAnim(String key) {
        this.triggerAnim("action", key);
    }

    public void playScaledSound(ServerLevel level, SoundEvent sound, float vol, float pitch) {
        level.playSound(null, this.getX(), this.getY() + 2.5, this.getZ(), sound, SoundSource.HOSTILE, vol * 1.8f,
                pitch);
    }

    public void resetPathAfterSnap(Vec3 pos) {
        this.serpentPath.resetAt(pos, this.yBodyRot, getActiveSegmentCount(), JormungandrConfig.get().getScaledSegmentSpacing());
        this.snapSyncPrevAngles();
        if (this.level() instanceof ServerLevel sl) {
            broadcastPathSync(sl);
        }
    }

    public void applyClientPathSync(JormungandrPathSyncPayload payload) {
        this.serpentPath.applyClientSyncPayload(payload, this.level().getGameTime());
    }

    // =========================================================================
    // HỆ THỐNG 12 PROXY HITBOX DỌC SERPENTPATH (ĐẦU x1.5, THÂN x1.0, ĐUÔI x0.75)
    // =========================================================================
    private void ensureAndSyncProxyParts(ServerLevel level) {
        JormungandrConfig cfg = JormungandrConfig.get();
        int desired = cfg.proxyCount;
        int activeSegs = getActiveSegmentCount();
        boolean activeHit = this.serpentPath.getBurrowDepth() < 0.70f;

        if (this.proxyParts.size() != desired) {
            discardAllProxyParts();
            for (int i = 0; i < desired; i++) {
                PartZone z = zoneForProxy(i, desired);
                this.proxyParts.add(JormungandrHitboxPartEntity.create(level, this, i, z, 2.0f));
            }
        }

        for (int i = 0; i < desired; i++) {
            JormungandrHitboxPartEntity part = this.proxyParts.get(i);
            PartZone zone = zoneForProxy(i, desired);
            if (part == null || part.isRemoved()) {
                part = JormungandrHitboxPartEntity.create(level, this, i, zone, 2.0f);
                this.proxyParts.set(i, part);
            }
            part.setZone(zone);
            part.setProxyActive(activeHit);

            // Rải đều từ đốt 0 (đầu) -> đốt cuối (đuôi), đồng bộ đúng theo visualScaleMultiplier
            double frac = i / (double) Math.max(1, desired - 1);
            int nodeIdx = Mth.clamp((int) Math.round(frac * (activeSegs + 1)), 0, activeSegs + 1);
            Vec3 nodePos = this.serpentPath.getServerNode(nodeIdx);
            float vScale = cfg.getEffectiveVisualScale();
            float diameter = Math.max(1.1f * vScale, this.serpentPath.computeRadiusAt(nodeIdx, cfg) * 2.05f * getPhaseThicknessFactor());
            if (zone == PartZone.HEAD) {
                diameter = Math.max(2.6f * cfg.getEffectiveHeadScale(), diameter * cfg.headScaleMultiplier);
            }
            part.setPartSize(diameter, diameter);
            part.syncCenterFromPath(nodePos.x, nodePos.y, nodePos.z, this.yBodyRot);
        }
    }

    private static PartZone zoneForProxy(int proxyIdx, int totalProxies) {
        if (proxyIdx == 0)
            return PartZone.HEAD;
        // 1/3 cuối đuôi chịu x0.75 sát thương
        if (proxyIdx >= (totalProxies * 2) / 3)
            return PartZone.TAIL;
        return PartZone.BODY;
    }

    private void discardAllProxyParts() {
        for (JormungandrHitboxPartEntity p : this.proxyParts) {
            if (p != null && !p.isRemoved()) {
                p.discard();
            }
        }
        this.proxyParts.clear();
    }

    /**
     * Chuyển tiếp sát thương từ proxy hitbox dọc thân về Boss chính với hệ số vùng
     * (Head x1.5 / Body x1.0 / Tail x0.75).
     */
    public boolean hurtFromPart(JormungandrHitboxPartEntity part, DamageSource source, float amount) {
        if (this.level().isClientSide || !this.isAlive()) {
            return false;
        }
        long now = this.level().getGameTime();
        UUID attackerId = source.getEntity() != null ? source.getEntity().getUUID() : null;
        if (now == this.lastPartHitTick && attackerId != null && attackerId.equals(this.lastPartHitAttacker)) {
            return false;
        }
        this.lastPartHitTick = now;
        this.lastPartHitAttacker = attackerId;

        float mult = part.getZoneDamageMultiplier();
        float scaledAmt = amount * mult;
        if (isDebugLoggingEnabled()) {
            AbyssalRpgMod.LOGGER.info(
                    "[Jormungandr#{} MULTIPART] proxy#{} zone={} mult={} raw={} -> scaled={} source={}",
                    this.getId(), part.getProxyIndex(), part.getZone().label,
                    String.format("%.2f", mult), String.format("%.2f", amount), String.format("%.2f", scaledAmt),
                    source.getMsgId());
        }
        return this.hurt(source, scaledAmt);
    }

    // =========================================================================
    // SÁT THƯƠNG, MIỄN NHIỄM ĐỘC & CHẶN CÁI CHẾT CHUYỂN PHASE 2
    // =========================================================================
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.is(MobEffects.POISON)) {
            return false; // Miễn nhiễm độc 100%
        }
        return super.canBeAffected(effect);
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
        if (isBurrowed() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }

        if (!this.level().isClientSide && !this.hasEnteredPhase2
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float threshold = Math.max(1.0f,
                    (float) (this.getMaxHealth() * JormungandrConfig.get().phase2TriggerHpRatio));
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
        JormungandrConfig cfg = JormungandrConfig.get();
        this.hasEnteredPhase2 = true;
        this.entityData.set(DATA_HAS_ENTERED_PHASE2, true);
        this.transitionStartHp = Math.max(1.0f, Math.min(this.getHealth(), minFloorHp));
        this.setHealth(this.transitionStartHp);

        this.transitionTotalTicks = cfg.phaseTransitionTicks; // 110 ticks = 5.5 giây
        this.entityData.set(DATA_PHASE_TRANS_TICKS, this.transitionTotalTicks);
        this.setSkillState(SkillState.PHASE_CHANGE);
        this.setHeadAnimationLocked(true);
        this.setBurrowed(false);
        this.wantRearUp = true;
        this.serpentPath.setOverrideNone();

        lockCurrentRotation();
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.playActionAnim("phase_change");

        if (this.level() instanceof ServerLevel sl) {
            playScaledSound(sl, ModSounds.JORMUNGANDR_PHASE_CHANGE, 2.2f, 0.72f);
            ModNetworking.sendShakeNearby(sl, this.position(), 72.0, 2.4f, 45);
            ShockwaveRingEntity.spawn(sl, this.position(), 20.0f, COLOR_SERPENT_VIOLET, 20);
            announceToNearbyWithRadius(sl, Component.literal(
                    "☠ JÖRMUNGANDR lột xác cuồng bạo — thân rắn kéo dài ×1.4 và bùng phát sức mạnh PHASE 2!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), cfg.bossBarRadius);
        }
    }

    private void tickPhaseTransition(ServerLevel level) {
        int ticksLeft = this.entityData.get(DATA_PHASE_TRANS_TICKS);
        if (ticksLeft <= 0)
            return;

        JormungandrConfig cfg = JormungandrConfig.get();
        this.setDeltaMovement(Vec3.ZERO);
        this.getNavigation().stop();
        this.normalizeAnglesAndSyncPrev();

        int elapsed = this.transitionTotalTicks - ticksLeft;
        float progress = Mth.clamp((float) elapsed / Math.max(1, this.transitionTotalTicks), 0.0f, 1.0f);

        // Hồi dần HP lên 2520
        float targetP2MaxHp = (float) (cfg.maxHealth * cfg.phase2StatMultiplier);
        if (getBossMaxHealth() < targetP2MaxHp - 1.0f) {
            setBossMaxHealth(targetP2MaxHp);
        }
        float desiredHp = Mth.lerp(progress, this.transitionStartHp, targetP2MaxHp);
        if (desiredHp > this.getHealth()) {
            this.setHealth(Math.min(targetP2MaxHp, desiredHp));
        }

        // Kéo dài thân dần từ đuôi (28 -> 39 đốt) kèm sóng lượn & lột da rơi vảy
        int p1Segs = cfg.segmentCountPhase1;
        int p2Segs = Mth.clamp(Math.round(p1Segs * cfg.lengthFactorPhase2), p1Segs, SerpentPath.N_MAX);
        int curSegs = Math.round(Mth.lerp(progress, p1Segs, p2Segs));
        if (curSegs != getActiveSegmentCount()) {
            setActiveSegmentCount(curSegs);
            if (elapsed == Math.round(this.transitionTotalTicks * 0.45f)) {
                this.playActionAnim("grow");
            }
        }

        // Mốc 35%: Lột da (vảy cũ văng thành particle) -> đổi texture Enraged + BossBar
        // tím-đỏ
        if (elapsed == Math.round(this.transitionTotalTicks * 0.35f)) {
            this.entityData.set(DATA_ENRAGED_VISUAL, true);
            this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
            playScaledSound(level, ModSounds.JORMUNGANDR_ROAR, 2.3f, 0.68f);
            ModNetworking.sendShakeNearby(level, this.position(), 76.0, 2.8f, 32);
            ShockwaveRingEntity.spawn(level, this.position(), 24.0f, COLOR_ENRAGED_WHITE_VIOLET, 18);
            for (int i = 0; i <= getActiveSegmentCount(); i += 3) {
                Vec3 p = this.serpentPath.getServerNode(i);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACKSTONE.defaultBlockState()),
                        p.x, p.y + 0.5, p.z, 8, 0.9, 0.6, 0.9, 0.16);
                level.sendParticles(ParticleTypes.DRAGON_BREATH, p.x, p.y + 0.6, p.z, 6, 0.8, 0.5, 0.8, 0.04);
            }
        }

        if (ticksLeft % 4 == 0) {
            Vec3 tail = this.serpentPath.getTailTipPos();
            level.sendParticles(ParticleTypes.DRAGON_BREATH, tail.x, tail.y + 0.5, tail.z, 6, 0.8, 0.5, 0.8, 0.03);
            level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 4.0, this.getZ(), 4, 1.5, 1.8, 1.5,
                    0.04);
        }

        logSerpentDebug("PHASE_TRANS", ticksLeft);

        ticksLeft--;
        this.entityData.set(DATA_PHASE_TRANS_TICKS, ticksLeft);
        if (ticksLeft <= 0) {
            finalizePhase2Stats(true);
            this.setSkillState(SkillState.IDLE);
            this.setHeadAnimationLocked(false);
            this.setGlobalSkillRestTicks(24);
            this.snapSyncPrevAngles();
            playScaledSound(level, ModSounds.JORMUNGANDR_FLARE, 2.2f, 0.72f);
            ShockwaveRingEntity.spawn(level, this.position(), 26.0f, COLOR_HOOD_LAVENDER, 18);
        }
    }

    public void finalizePhase2Stats(boolean healToFull) {
        JormungandrConfig cfg = JormungandrConfig.get();
        syncPhaseData(2);
        this.hasEnteredPhase2 = true;
        this.phase2StatsApplied = true;
        this.entityData.set(DATA_HAS_ENTERED_PHASE2, true);
        this.entityData.set(DATA_ENRAGED_VISUAL, true);
        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);

        int p2Segs = Mth.clamp(Math.round(cfg.segmentCountPhase1 * cfg.lengthFactorPhase2),
                cfg.segmentCountPhase1, SerpentPath.N_MAX);
        setActiveSegmentCount(p2Segs);

        float p2MaxHp = (float) (cfg.maxHealth * cfg.phase2StatMultiplier);
        setBossMaxHealth(p2MaxHp);
        if (healToFull) {
            this.setHealth(p2MaxHp);
        }

        // Defense 30 -> 42 (vẫn bị kẹp bởi maxDamageReduction = 0.75)
        float p2Def = (float) (cfg.defense * cfg.phase2StatMultiplier);
        setBossDefense(p2Def);

        double addMult = Math.max(0.0, cfg.phase2StatMultiplier - 1.0); // +0.40 -> x1.40
        applyPermanentMultiplier(Attributes.ATTACK_DAMAGE, PHASE2_DAMAGE_MOD_ID, addMult);
        applyPermanentMultiplier(Attributes.MOVEMENT_SPEED, PHASE2_SPEED_MOD_ID, addMult);
    }

    // =========================================================================
    // VÒNG LẶP SERVER AI, VŨNG ĐỘC, SÁT THƯƠNG TIẾP XÚC THÂN & ĐỒNG BỘ PAYLOAD
    // =========================================================================
    public void addVenomPuddle(Vec3 pos, float radius, float tickDamage, int durationTicks) {
        int max = JormungandrConfig.get().maxVenomPuddles;
        while (this.venomPuddles.size() >= max) {
            this.venomPuddles.remove(0);
        }
        this.venomPuddles.add(new VenomPuddle(pos, radius, tickDamage, durationTicks));
    }

    public void logSerpentDebug(String tag, int extraInfo) {
        if (!isDebugLoggingEnabled())
            return;
        float sampleDmg = 15.0f;
        AbyssalRpgMod.LOGGER.info(
                "[Jormungandr#{} {} t={}] yaw={} (prev={}) body={} (prev={}) head={} (prev={}) netHead={} | segs={} len={}m | def={} effPts={} red={}% | proxies={} HP={}/{}",
                this.getId(), tag, extraInfo,
                String.format("%.2f", this.getYRot()), String.format("%.2f", this.yRotO),
                String.format("%.2f", this.yBodyRot), String.format("%.2f", this.yBodyRotO),
                String.format("%.2f", this.yHeadRot), String.format("%.2f", this.yHeadRotO),
                String.format("%.2f", Mth.wrapDegrees(this.yHeadRot - this.yBodyRot)),
                getActiveSegmentCount(), String.format("%.1f", this.serpentPath.getTotalLengthBlocks()),
                String.format("%.1f", getBossDefense()),
                String.format("%.2f", computeEffectiveDefensePoints(sampleDmg)),
                String.format("%.1f", computeEffectiveDamageReduction(sampleDmg) * 100.0f),
                this.proxyParts.size(),
                String.format("%.1f", this.getHealth()), String.format("%.1f", this.getMaxHealth()));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel sl))
            return;
        JormungandrConfig cfg = JormungandrConfig.get();

        if (!this.pathInitialized) {
            this.pathInitialized = true;
            this.serpentPath.resetAt(this.position(), this.yBodyRot, getActiveSegmentCount(), cfg.getScaledSegmentSpacing());
        }

        syncBossBarPlayers(sl, cfg.bossBarRadius);

        // Cập nhật SerpentPath mỗi tick (Server nguồn chân lý)
        double horizSpeed = this.getDeltaMovement().horizontalDistance();
        boolean rearFlag = this.wantRearUp || isTransitioningPhase()
                || getSkillState() == SkillState.STRIKE_BITE
                || getSkillState() == SkillState.VENOM_SPIT
                || getSkillState() == SkillState.HOOD_FLARE
                || getSkillState() == SkillState.TOXIC_NOVA
                || getSkillState() == SkillState.VENOM_RAIN;
        this.serpentPath.tickServer(sl, this.position(), this.yBodyRot, horizSpeed, rearFlag, isBurrowed());

        // Đồng bộ 12 proxy hitbox bám theo SerpentPath mỗi tick
        ensureAndSyncProxyParts(sl);

        // Gửi custom payload SerpentPath mỗi 2 tick cho tất cả người chơi đang theo dõi
        // trong 192 block
        if (this.tickCount % 2 == 0) {
            broadcastPathSync(sl);
        }

        if (this.isSpawningIn()) {
            return;
        }

        if (this.globalSkillRestTicks > 0)
            this.globalSkillRestTicks--;
        if (this.stuckRecoveryCooldown > 0)
            this.stuckRecoveryCooldown--;
        if (!this.skillCooldowns.isEmpty()) {
            int cdStep = (this.tickCount % 2 == 0) ? 2 : 1;
            for (Map.Entry<SkillState, Integer> e : this.skillCooldowns.entrySet()) {
                if (e.getValue() > 0)
                    e.setValue(Math.max(0, e.getValue() - cdStep));
            }
        }

        if (this.isTransitioningPhase()) {
            tickPhaseTransition(sl);
            return;
        }

        // Sát thương tiếp xúc thân nhẹ (6 HP / P2 8.4 HP, cooldown 20 tick mỗi người)
        if (!isBurrowed() && this.tickCount % 3 == 0 && cfg.contactDamage > 0.0f) {
            tickBodyContactDamage(sl, cfg);
        }

        tickVenomPuddles(sl, cfg);

        this.outOfCombatTicks = tickOutOfCombatRegen(
                sl, this.outOfCombatTicks,
                cfg.outOfCombatHealRadius, cfg.outOfCombatHealDelayTicks, cfg.outOfCombatHealPerSecond);

        tickStuckRecovery(sl);

        if (isDebugLoggingEnabled() && this.tickCount % 20 == 0) {
            logSerpentDebug("TICK", this.tickCount);
        }
    }

    private void broadcastPathSync(ServerLevel level) {
        JormungandrPathSyncPayload payload = this.serpentPath.buildSyncPayload(this.getId(), this.position());
        double maxDistSq = 192.0 * 192.0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) <= maxDistSq) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private void tickBodyContactDamage(ServerLevel level, JormungandrConfig cfg) {
        long now = level.getGameTime();
        float dmg = scaledDamage(cfg.contactDamage);
        boolean ouroboros = (getSkillState() == SkillState.OUROBOROS_RING);
        int poisonTicks = Math.min(80, cfg.venomDurationTicks / 2);

        for (JormungandrHitboxPartEntity part : this.proxyParts) {
            if (part == null || part.isRemoved() || !part.isPickable())
                continue;
            AABB box = part.getBoundingBox().inflate(0.25);
            for (Player p : level.getEntitiesOfClass(Player.class, box,
                    pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                long lastHit = this.contactHitCooldowns.getOrDefault(p.getUUID(), -1000L);
                if (now - lastHit < cfg.contactCooldownTicks)
                    continue;
                this.contactHitCooldowns.put(p.getUUID(), now);
                p.hurt(level.damageSources().mobAttack(this), dmg);
                Vec3 away = p.position().subtract(part.position());
                Vec3 horiz = new Vec3(away.x, 0.0, away.z);
                Vec3 dir = horiz.lengthSqr() > 1e-4 ? horiz.normalize() : new Vec3(0, 0, 1);
                p.setDeltaMovement(p.getDeltaMovement().add(dir.x * 0.45, 0.22, dir.z * 0.45));
                p.hurtMarked = true;
                if (ouroboros) {
                    p.addEffect(new MobEffectInstance(MobEffects.POISON, poisonTicks, 1));
                }
            }
        }
        if (this.contactHitCooldowns.size() > 64) {
            this.contactHitCooldowns.entrySet().removeIf(e -> now - e.getValue() > 200L);
        }
    }

    private void tickVenomPuddles(ServerLevel level, JormungandrConfig cfg) {
        if (this.venomPuddles.isEmpty())
            return;
        int poisonTicks = Math.min(80, cfg.venomDurationTicks / 2);
        List<VenomPuddle> snapshot = new ArrayList<>(this.venomPuddles);
        for (VenomPuddle puddle : snapshot) {
            if (!this.venomPuddles.contains(puddle))
                continue;
            puddle.ticksLeft--;
            if (puddle.ticksLeft <= 0) {
                this.venomPuddles.remove(puddle);
                continue;
            }
            if (puddle.ticksLeft % 4 == 0) {
                level.sendParticles(ParticleTypes.ITEM_SLIME,
                        puddle.pos.x, puddle.pos.y + 0.1, puddle.pos.z,
                        4, puddle.radius * 0.65, 0.05, puddle.radius * 0.65, 0.01);
            }
            if (puddle.ticksLeft % 15 == 0) {
                AABB box = new AABB(
                        puddle.pos.x - puddle.radius, puddle.pos.y - 0.5, puddle.pos.z - puddle.radius,
                        puddle.pos.x + puddle.radius, puddle.pos.y + 1.8, puddle.pos.z + puddle.radius);
                for (Player p : level.getEntitiesOfClass(Player.class, box,
                        pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    if (Math.hypot(p.getX() - puddle.pos.x, p.getZ() - puddle.pos.z) <= puddle.radius) {
                        com.abyssalrpg.combat.KatanaCombatHandler.runUnparryableMagic(() ->
                                p.hurt(level.damageSources().mobAttack(this), puddle.tickDamage));
                        p.addEffect(new MobEffectInstance(MobEffects.POISON, poisonTicks, 1));
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
        double dist = this.distanceTo(target);
        if (dist <= 9.0 || dist > JormungandrConfig.get().detectionRadius) {
            this.stuckTicks = 0;
            this.lastProgressPos = this.position();
            return;
        }
        if (this.position().distanceToSqr(this.lastProgressPos) >= 1.0) {
            this.stuckTicks = 0;
            this.lastProgressPos = this.position();
            return;
        }
        this.stuckTicks++;
        if (this.stuckTicks >= 60 && this.stuckRecoveryCooldown <= 0) {
            this.stuckTicks = 0;
            this.stuckRecoveryCooldown = 100;
            Vec3 toT = target.position().subtract(this.position());
            Vec3 h = new Vec3(toT.x, 0.0, toT.z);
            Vec3 dir = h.lengthSqr() > 1e-4 ? h.normalize() : new Vec3(0, 0, 1);
            Vec3 desired = this.position().add(dir.scale(Math.min(12.0, Math.max(6.0, dist - 7.0))));
            Vec3 safe = findSafeLandingPosition(level, desired, this.position(), 7);
            if (safe.distanceToSqr(this.position()) > 4.0) {
                this.teleportTo(safe.x, safe.y, safe.z);
                facePositionImmediately(target.position());
                resetPathAfterSnap(safe);
            }
            this.lastProgressPos = this.position();
        }
    }

    @Override
    public void die(DamageSource source) {
        this.setSkillState(SkillState.DEATH);
        this.setHeadAnimationLocked(true);
        this.setBurrowed(false);
        this.serpentPath.setOverrideNone();
        discardAllProxyParts();
        this.venomPuddles.clear();
        if (this.level() instanceof ServerLevel sl) {
            playScaledSound(sl, ModSounds.JORMUNGANDR_DEATH, 2.4f, 0.72f);
            ModNetworking.sendShakeNearby(sl, this.position(), 72.0, 2.6f, 36);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        discardAllProxyParts();
        super.remove(reason);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.JORMUNGANDR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.JORMUNGANDR_DEATH;
    }

    // =========================================================================
    // NBT SAVE / LOAD (KHÔI PHỤC PHASE 2, ĐỘ DÀI THÂN, DEFENSE, TEXTURE, BOSSBAR)
    // =========================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saveSharedGeoBossData(tag);
        tag.putBoolean("JormPhase2Applied", this.phase2StatsApplied);
        tag.putInt("JormActiveSegments", getActiveSegmentCount());
        tag.putInt("JormUltimateCd", getSkillCooldown(SkillState.OUROBOROS_RING));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadSharedGeoBossData(tag, null, null, null, null, null, null);
        this.phase2StatsApplied = tag.getBoolean("JormPhase2Applied");
        if (tag.contains("JormActiveSegments")) {
            setActiveSegmentCount(tag.getInt("JormActiveSegments"));
        }
        if (tag.contains("JormUltimateCd")) {
            this.skillCooldowns.put(SkillState.OUROBOROS_RING, Math.max(0, tag.getInt("JormUltimateCd")));
        }

        if (this.hasEnteredPhase2 && !this.isTransitioningPhase()) {
            float savedHp = this.getHealth();
            finalizePhase2Stats(false);
            this.setHealth(Mth.clamp(savedHp, 1.0f, this.getMaxHealth()));
        } else if (getBossDefense() <= 0.0f) {
            setBossDefense(JormungandrConfig.get().defense);
        }
        this.pathInitialized = false;
        this.snapSyncPrevAngles();
    }

    // =========================================================================
    // ĐĂNG KÝ GECKOLIB CONTROLLERS (MOVEMENT + 14 ACTION TRIGGERABLE)
    // =========================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (!this.isAlive() || getSkillState() == SkillState.DEATH) {
                return state.setAndContinue(ANIM_DEATH);
            }
            if (this.isTransitioningPhase()) {
                return state.setAndContinue(ANIM_REAR_UP);
            }
            if (state.isMoving() || this.getDeltaMovement().horizontalDistanceSqr() > 0.004) {
                return state.setAndContinue(ANIM_SLITHER);
            }
            if (this.getTarget() != null || this.serpentPath.getRaiseBlend() > 0.65f) {
                return state.setAndContinue(ANIM_REAR_UP);
            }
            return state.setAndContinue(ANIM_IDLE_COIL);
        }));

        controllers.add(new AnimationController<>(this, "action", 3, state -> PlayState.STOP)
                .triggerableAnim("strike", ANIM_STRIKE)
                .triggerableAnim("spit", ANIM_SPIT)
                .triggerableAnim("tail_slam", ANIM_TAIL_SLAM)
                .triggerableAnim("constrict", ANIM_CONSTRICT)
                .triggerableAnim("flare", ANIM_FLARE)
                .triggerableAnim("burrow", ANIM_BURROW)
                .triggerableAnim("emerge", ANIM_EMERGE)
                .triggerableAnim("rapid_strike", ANIM_RAPID_STRIKE)
                .triggerableAnim("nova", ANIM_NOVA)
                .triggerableAnim("rain", ANIM_RAIN)
                .triggerableAnim("ouroboros", ANIM_OUROBOROS)
                .triggerableAnim("phase_change", ANIM_PHASE_CHANGE)
                .triggerableAnim("grow", ANIM_GROW));
    }

    // =========================================================================
    // DIRECT-STEERING MOVE CONTROL VỚI GIA TỐC & BÁN KÍNH QUAY TỐI THIỂU CHỐNG GẬP
    // THÂN
    // =========================================================================
    public static final class SerpentSteeringMoveControl extends MoveControl {
        private final JormungandrBossEntity boss;
        private float currentSpeed = 0.0f;

        public SerpentSteeringMoveControl(JormungandrBossEntity boss) {
            super(boss);
            this.boss = boss;
        }

        @Override
        public void tick() {
            if (boss.isRotationLocked()) {
                this.currentSpeed = 0.0f;
                boss.setZza(0.0f);
                boss.setXxa(0.0f);
                return;
            }
            if (this.operation != Operation.MOVE_TO) {
                // Giảm tốc mượt khi dừng
                this.currentSpeed = Mth.lerp(0.20f, this.currentSpeed, 0.0f);
                boss.setZza(this.currentSpeed);
                return;
            }
            this.operation = Operation.WAIT;

            double dx = this.wantedX - boss.getX();
            double dz = this.wantedZ - boss.getZ();
            if (dx * dx + dz * dz < 0.25) {
                this.currentSpeed = Mth.lerp(0.25f, this.currentSpeed, 0.0f);
                boss.setZza(this.currentSpeed);
                return;
            }

            Vec3 desiredDir = new Vec3(dx, 0.0, dz).normalize();
            Vec3 steerDir = avoidSolidWall(desiredDir);

            // Gia tốc mượt tới tốc độ mục tiêu
            float targetSpeed = (float) (this.speedModifier * boss.getAttributeValue(Attributes.MOVEMENT_SPEED));
            this.currentSpeed = Mth.lerp(0.16f, this.currentSpeed, targetSpeed);

            // Giới hạn bán kính quay tối thiểu theo độ dày thân đã scale (`R_min = scaledDiameter * 1.45`) -> không gập gấp!
            JormungandrConfig cfg = JormungandrConfig.get();
            double minTurnRadius = Math.max(2.6, cfg.getScaledBaseBodyDiameter() * 1.45);
            float physicalMaxDeg = (float) (Math.toDegrees(Math.max(0.18, this.currentSpeed) / minTurnRadius));
            float baseMaxTurn = Mth.clamp(physicalMaxDeg * 1.35f, 4.5f, 8.5f);

            boss.smoothRotateToward(steerDir, baseMaxTurn, (float) cfg.phase2TurnSpeedMultiplier);
            boss.setSpeed(this.currentSpeed);
            boss.setZza(this.currentSpeed);

            if (boss.onGround() && isStepAhead(steerDir)) {
                boss.getJumpControl().jump();
            }
        }

        private Vec3 avoidSolidWall(Vec3 dir) {
            if (!isSolidWallAhead(dir, 2.8))
                return dir;
            Vec3 left = rotateY(dir, Math.toRadians(50.0));
            Vec3 right = rotateY(dir, Math.toRadians(-50.0));
            boolean lBlk = isSolidWallAhead(left, 3.0);
            boolean rBlk = isSolidWallAhead(right, 3.0);
            if (!lBlk && rBlk)
                return left;
            if (!rBlk && lBlk)
                return right;
            return (boss.getId() % 2 == 0) ? left : right;
        }

        private boolean isStepAhead(Vec3 dir) {
            Level lvl = boss.level();
            BlockPos ahead = BlockPos.containing(boss.getX() + dir.x * 2.2, boss.getY() + 0.5,
                    boss.getZ() + dir.z * 2.2);
            BlockState st = lvl.getBlockState(ahead);
            boolean blocked = !st.getCollisionShape(lvl, ahead).isEmpty() && !st.is(BlockTags.LEAVES);
            boolean openAbove = lvl.getBlockState(ahead.above(3)).getCollisionShape(lvl, ahead.above(3)).isEmpty();
            return blocked && openAbove;
        }

        private boolean isSolidWallAhead(Vec3 dir, double dist) {
            Level lvl = boss.level();
            BlockPos base = BlockPos.containing(boss.getX() + dir.x * dist, boss.getY() + 0.8,
                    boss.getZ() + dir.z * dist);
            int solid = 0;
            for (int y = 0; y < 4; y++) {
                BlockState st = lvl.getBlockState(base.above(y));
                if (!st.getCollisionShape(lvl, base.above(y)).isEmpty()
                        && !st.is(BlockTags.LEAVES) && !st.is(BlockTags.REPLACEABLE)) {
                    solid++;
                }
            }
            return solid >= 3;
        }
    }
}
