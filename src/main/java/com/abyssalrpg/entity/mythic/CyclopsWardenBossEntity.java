package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * BOSS CYCLOPS WARDEN — Khổng Lồ Một Mắt Giam Tù (`abyssalrpg:cyclops_warden`):
 * - Kích thước cỡ trung bình (~3.8 block), 1,000 HP, Defense 10, Sát thương ~10 dame.
 * - Vác Đại Chùy Chiến 6 Cánh viền bạc & đeo Lồng Sắt Giam Tù sau lưng chứa Thương Nhân bị xích.
 * - Cơ chế đặc trưng: Khi Boss bị tiêu diệt, xiềng xích vỡ tan, cửa lồng sắt bật mở và
 *   **Người bị xích trong lồng được giải thoát** ngay tại vị trí lồng kèm hiệu ứng & quà cảm ơn!
 */
public class CyclopsWardenBossEntity extends AbstractGeoMythicBoss {
    // Tăng kích thước 1.3x theo yêu cầu (1.08 * 1.3 ≈ 1.40f -> ~5.0 block cao uy nghi!)
    public static final float RENDER_SCALE = 1.40f;
    public static final float HITBOX_WIDTH = 2.55f;
    public static final float HITBOX_HEIGHT = 4.65f;
    public static final float HITBOX_EYE_HEIGHT = 3.85f;

    public static final float BASE_MAX_HEALTH = 1000.0f;
    public static final float BASE_DEFENSE = 10.0f;
    public static final double BASE_ATTACK_DAMAGE = 10.0;

    // Tên các bone khớp 100% với `assets/abyssalrpg/geo/entity/cyclops_warden.geo.json` V2 (55 khớp!)
    public static final String NECK_BONE = "neck";
    public static final String HEAD_BONE = "head";
    public static final String EYELID_UP_BONE = "eyelid_up";
    public static final String EYELID_LO_BONE = "eyelid_lo";
    public static final String CHAIN_1_BONE = "chain_1";
    public static final String CHAIN_2_BONE = "chain_2";
    public static final String CHAIN_3_BONE = "chain_3";
    public static final String CAGE_BODY_BONE = "cage_body";
    public static final String CAGE_DOOR_BONE = "cage_door";
    public static final String PRISONER_BONE = "prisoner";
    public static final String PRISONER_HEAD_BONE = "prisoner_head";
    public static final String KILT_FRONT_C_BONE = "kilt_front_c";
    public static final String KILT_FRONT_L_BONE = "kilt_front_l";
    public static final String KILT_FRONT_R_BONE = "kilt_front_r";
    public static final String KILT_SIDE_L_BONE = "kilt_side_l";
    public static final String KILT_SIDE_R_BONE = "kilt_side_r";
    public static final String KILT_BACK_BONE = "kilt_back";

    public static final int COLOR_EARTH_SHOCK = 0xD98245;
    public static final int COLOR_CRIMSON_EYE = 0xE82424;
    public static final int COLOR_LIBERATION_GOLD = 0xFFD24A;

    private static final DustParticleOptions CRIMSON_GLARE_DUST =
            new DustParticleOptions(new Vector3f(0.95f, 0.16f, 0.16f), 1.45f);
    private static final ResourceLocation PHASE2_SPEED_MOD_ID = AbyssalRpgMod.id("cyclops_warden_p2_speed");

    // 8 Animation GeckoLib 4.8.3 (1 controller thống nhất, 0 xung đột!)
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.cyclops_warden.idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("animation.cyclops_warden.walk");
    private static final RawAnimation ANIM_EMERGE = RawAnimation.begin().thenPlayAndHold("animation.cyclops_warden.emerge");
    private static final RawAnimation ANIM_DEATH = RawAnimation.begin().thenPlayAndHold("animation.cyclops_warden.death");

    private static final RawAnimation ANIM_MACE_SLAM = RawAnimation.begin().thenPlay("animation.cyclops_warden.mace_slam");
    private static final RawAnimation ANIM_MACE_SWEEP = RawAnimation.begin().thenPlay("animation.cyclops_warden.mace_sweep");
    private static final RawAnimation ANIM_BRACER_BASH = RawAnimation.begin().thenPlay("animation.cyclops_warden.bracer_bash");
    private static final RawAnimation ANIM_ROAR = RawAnimation.begin().thenPlay("animation.cyclops_warden.roar");

    private static final int DEATH_DURATION_TICKS = 40;
    private static final int CAGE_BREAK_DEATH_TICK = 14;

    public enum SkillState {
        IDLE(0),
        MACE_SLAM(1),
        MACE_SWEEP(2),
        BRACER_BASH(3),
        ROAR(4),
        DEATH(5);

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
            SynchedEntityData.defineId(CyclopsWardenBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_NONCE =
            SynchedEntityData.defineId(CyclopsWardenBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PRISONER_RELEASED =
            SynchedEntityData.defineId(CyclopsWardenBossEntity.class, EntityDataSerializers.BOOLEAN);

    private int cycle = 0;
    private int actionTicksRemaining = 0;
    private boolean prisonerReleased = false;
    private boolean phase2Applied = false;

    // Client-only smooth tracking state
    private int lastClientActionNonce = -1;
    private float clientHeadYawDeg = 0.0f;
    private float clientHeadPitchDeg = 0.0f;
    private int clientReleaseTick = -1;

    @SuppressWarnings("this-escape")
    public CyclopsWardenBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
        this.xpReward = 380;
        initializeBossHealth(BASE_MAX_HEALTH);
        setBossDefense(BASE_DEFENSE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKILL_STATE, SkillState.IDLE.id);
        builder.define(DATA_ACTION_NONCE, 0);
        builder.define(DATA_PRISONER_RELEASED, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new LargeGeoGroundNavigation(this, level);
    }

    @Override
    protected int getDefaultSpawnEggFloor() {
        return 6;
    }

    @Override
    public float getColossalScale(int floor) {
        return 1.0f; // Giữ đúng cỡ trung bình (~3.8 block) qua RENDER_SCALE, không phóng đại theo floor!
    }

    @Override
    protected Component getStandaloneBossTitle() {
        return Component.literal("⛓ Cyclops Warden — Khổng Lồ Giam Tù ⛓")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
                .add(Attributes.ARMOR, BASE_DEFENSE)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 44.0)
                .add(Attributes.STEP_HEIGHT, 1.6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.92);
    }

    @Override
    public void configureForFloor(int floor, BlockPos arenaCenter, boolean dungeonBoss) {
        super.configureForFloor(floor, arenaCenter, dungeonBoss);
        // Khóa đúng chỉ số yêu cầu: 1,000 HP, Def 10, sát thương ~10 dame, size cỡ trung bình!
        initializeBossHealth(BASE_MAX_HEALTH);
        setBossDefense(BASE_DEFENSE);
        setAttr(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE);
        setAttr(Attributes.ARMOR, BASE_DEFENSE);
        setAttr(Attributes.ARMOR_TOUGHNESS, 4.0);
        setAttr(Attributes.MOVEMENT_SPEED, 0.30);
        setAttr(Attributes.STEP_HEIGHT, 1.6);
        setAttr(Attributes.SCALE, 1.0);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(3.5, 2.5, 3.5);
    }

    public SkillState getSkillState() {
        return SkillState.byId(this.entityData.get(DATA_SKILL_STATE));
    }

    public boolean isPrisonerReleased() {
        return this.prisonerReleased || this.entityData.get(DATA_PRISONER_RELEASED);
    }

    public float getClientCageDoorOpenProgress(float partialTick) {
        if (!isPrisonerReleased()) {
            this.clientReleaseTick = -1;
            return 0.0f;
        }
        if (this.clientReleaseTick < 0) {
            this.clientReleaseTick = this.tickCount;
        }
        float elapsed = (this.tickCount - this.clientReleaseTick) + partialTick;
        float u = Mth.clamp(elapsed / 8.0f, 0.0f, 1.0f);
        return 1.0f - (1.0f - u) * (1.0f - u) * (1.0f - u);
    }

    private void beginAction(SkillState state, int durationTicks) {
        this.entityData.set(DATA_SKILL_STATE, state.id);
        this.entityData.set(DATA_ACTION_NONCE, this.entityData.get(DATA_ACTION_NONCE) + 1);
        this.actionTicksRemaining = durationTicks;
        startCastAnimation(state.id, durationTicks);
    }

    public boolean isHeadLockedByAnimation() {
        if (!this.isAlive() || this.isSpawningIn() || this.isTransitioningPhase() || this.isRotationLocked()) {
            return true;
        }
        return getSkillState() != SkillState.IDLE;
    }

    public void stepClientHeadTracking(float targetYawDeg, float targetPitchDeg) {
        this.clientHeadYawDeg = Mth.clamp(Mth.approachDegrees(this.clientHeadYawDeg, targetYawDeg, 4.0f), -34.0f, 34.0f);
        this.clientHeadPitchDeg = Mth.clamp(Mth.approachDegrees(this.clientHeadPitchDeg, targetPitchDeg, 3.5f), -20.0f, 20.0f);
    }

    public float getClientHeadYawDeg() {
        return this.clientHeadYawDeg;
    }

    public float getClientHeadPitchDeg() {
        return this.clientHeadPitchDeg;
    }

    @Override
    protected void customServerAiStep() {
        if (this.actionTicksRemaining > 0) {
            this.actionTicksRemaining--;
            if (this.actionTicksRemaining <= 0 && getSkillState() != SkillState.DEATH) {
                this.entityData.set(DATA_SKILL_STATE, SkillState.IDLE.id);
            }
        }
        super.customServerAiStep();
    }

    @Override
    protected void onPhaseTransition(ServerLevel level, int newPhase) {
        super.onPhaseTransition(level, newPhase);
        if (newPhase >= 2 && !this.phase2Applied) {
            this.phase2Applied = true;
            this.hasEnteredPhase2 = true;
            this.entityData.set(DATA_HAS_ENTERED_PHASE2, true);
            this.entityData.set(DATA_ENRAGED_VISUAL, true);
            applyPermanentMultiplier(Attributes.MOVEMENT_SPEED, PHASE2_SPEED_MOD_ID, 0.16);
            beginAction(SkillState.ROAR, 30);
            level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.58f);
            ShockwaveRingEntity.spawn(level, this.position().add(0, 0.3, 0), 7.5f, COLOR_CRIMSON_EYE, 14);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isSpawningIn()) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected int executeBossSkill(ServerLevel level, LivingEntity target) {
        boolean p2 = this.phase >= 2 || this.hasEnteredPhase2;
        float baseDmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE); // ~10.0
        double dist = this.distanceTo(target);
        int move = (dist < 4.5 && cycle % 3 == 1) ? 2 : (cycle % 4);
        cycle++;

        switch (move) {
            case 0 -> {
                // SKILL 1: Overhead 6-Flange War-Mace Slam (đập búa sấm sét rạn đất phía trước!)
                beginAction(SkillState.MACE_SLAM, 26);
                level.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.8f, 0.62f);
                scheduleAction(14, null, () -> {
                    if (!this.isAlive()) return;
                    Vec3 slamPos = modelPointToWorld(0.0f, 2.0f, -30.0f, RENDER_SCALE);
                    level.playSound(null, BlockPos.containing(slamPos), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6f, 0.55f);
                    level.playSound(null, BlockPos.containing(slamPos), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 0.72f);
                    ShockwaveRingEntity.spawn(level, slamPos.add(0, 0.25, 0), 6.2f, COLOR_EARTH_SHOCK, 12);
                    BlockState ground = level.getBlockState(BlockPos.containing(slamPos).below());
                    if (ground.isAir()) ground = Blocks.COARSE_DIRT.defaultBlockState();
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                            slamPos.x, slamPos.y + 0.35, slamPos.z, 34, 1.6, 0.5, 1.6, 0.22);
                    ModNetworking.sendShakeNearby(level, slamPos, 26.0, 1.35f, 10);
                    damagePlayersInRadius(level, slamPos, 5.2, baseDmg * (p2 ? 1.25f : 1.15f), 0.95);
                });
            }
            case 1 -> {
                // SKILL 2: Heavy Horizontal Mace Sweep (quét ngang chùy 6 cánh rộng!)
                beginAction(SkillState.MACE_SWEEP, 24);
                level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.6f, 0.70f);
                scheduleAction(13, null, () -> {
                    if (!this.isAlive()) return;
                    Vec3 hitCenter = modelPointToWorld(0.0f, 18.0f, -24.0f, RENDER_SCALE);
                    level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.9f, 0.58f);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, hitCenter.x, hitCenter.y, hitCenter.z, 3, 1.2, 0.3, 1.2, 0.0);
                    ShockwaveRingEntity.spawn(level, this.position().add(0, 0.8, 0), 5.6f, COLOR_EARTH_SHOCK, 10);
                    damagePlayersInRadius(level, hitCenter, 5.4, baseDmg * (p2 ? 1.12f : 1.00f), 1.10);
                });
            }
            case 2 -> {
                // SKILL 3: Spiked Bracer Shield Bash (húc cẳng tay giáp 6 đinh tầm gần!)
                beginAction(SkillState.BRACER_BASH, 21);
                level.playSound(null, this.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.6f, 0.60f);
                Vec3 toT = target.position().subtract(this.position());
                Vec3 horiz = new Vec3(toT.x, 0.0, toT.z);
                if (horiz.lengthSqr() > 1e-4) {
                    Vec3 dir = horiz.normalize();
                    this.setDeltaMovement(dir.x * 0.85, 0.16, dir.z * 0.85);
                    this.hurtMarked = true;
                }
                scheduleAction(11, null, () -> {
                    if (!this.isAlive()) return;
                    Vec3 bashPos = modelPointToWorld(+8.0f, 18.0f, -20.0f, RENDER_SCALE);
                    level.playSound(null, this.blockPosition(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.7f, 0.65f);
                    level.sendParticles(ParticleTypes.CRIT, bashPos.x, bashPos.y, bashPos.z, 18, 0.9, 0.6, 0.9, 0.14);
                    damagePlayersInRadius(level, bashPos, 4.4, baseDmg * 0.98f, 1.25);
                });
            }
            default -> {
                // SKILL 4: Cyclops War Roar (gầm vang mắt đỏ rực, chấn động vòng tròn xung quanh!)
                beginAction(SkillState.ROAR, 30);
                level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.60f);
                scheduleAction(14, null, () -> {
                    if (!this.isAlive()) return;
                    ShockwaveRingEntity.spawn(level, this.position().add(0, 0.4, 0), 7.2f, COLOR_CRIMSON_EYE, 14);
                    level.sendParticles(CRIMSON_GLARE_DUST, this.getX(), this.getY() + 2.6, this.getZ(), 22, 1.5, 0.8, 1.5, 0.05);
                    ModNetworking.sendShakeNearby(level, this.position(), 28.0, 1.15f, 10);
                    damagePlayersInRadius(level, this.position(), 6.2, baseDmg * 0.90f, 1.05);
                });
            }
        }
        return p2 ? 42 : 54;
    }

    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        if (this.phase >= 2 || isEnragedVisual()) {
            Vec3 eyePos = modelPointToWorld(0.0f, 45.6f, -11.0f, RENDER_SCALE);
            level.sendParticles(CRIMSON_GLARE_DUST, eyePos.x, eyePos.y, eyePos.z, 2, 0.15, 0.12, 0.15, 0.01);
        }
    }

    // =========================================================================
    // CƠ CHẾ ĐẶC BIỆT: KHI BOSS CHẾT, GIẢI THOÁT NGƯỜI BỊ XÍCH TRONG LỒNG SẮT!
    // =========================================================================
    @Override
    public void die(DamageSource source) {
        this.entityData.set(DATA_SKILL_STATE, SkillState.DEATH.id);
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.level() instanceof ServerLevel serverLevel && !this.prisonerReleased && this.deathTime >= CAGE_BREAK_DEATH_TICK) {
            releaseCaptiveFromCage(serverLevel);
        }
        if (this.deathTime >= DEATH_DURATION_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel serverLevel && !this.prisonerReleased) {
                releaseCaptiveFromCage(serverLevel);
            }
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        // An toàn tuyệt đối: nếu boss bị xóa sớm sau khi chết (vd: unload chunk), vẫn đảm bảo người tù được giải thoát!
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel
                && this.isDeadOrDying() && !this.prisonerReleased && reason == RemovalReason.KILLED) {
            releaseCaptiveFromCage(serverLevel);
        }
        super.remove(reason);
    }

    /**
     * Phá tung xiềng xích & cửa lồng sắt sau lưng Cyclops, ẩn model dân làng trong lồng và
     * triệu hồi thực thể Dân Làng Được Giải Thoát (`EntityType.VILLAGER`) + tặng quà cảm ơn!
     */
    private void releaseCaptiveFromCage(ServerLevel level) {
        if (this.prisonerReleased) return;
        this.prisonerReleased = true;
        this.entityData.set(DATA_PRISONER_RELEASED, true);

        // Tính tọa độ thực của lồng sắt sau lưng boss và tìm mặt đất an toàn ngay cạnh để thả dân làng!
        Vec3 rawCagePos = modelPointToWorld(+4.4f, 18.0f, +14.5f, RENDER_SCALE);
        Vec3 safePos = findSafeLandingPosition(level, rawCagePos, this.position().add(0.0, 0.5, 0.0), 4);

        // 1. Hiệu ứng âm thanh & hạt phá vỡ xích sắt + mở toang cửa lồng + tiếng dân làng ăn mừng!
        level.playSound(null, BlockPos.containing(safePos), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 2.2f, 0.85f);
        level.playSound(null, BlockPos.containing(safePos), SoundEvents.IRON_DOOR_OPEN, SoundSource.NEUTRAL, 2.0f, 0.78f);
        level.playSound(null, BlockPos.containing(safePos), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.8f, 1.05f);
        level.playSound(null, BlockPos.containing(safePos), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.4f, 1.15f);

        BlockParticleOption ironDebris = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BARS.defaultBlockState());
        level.sendParticles(ironDebris, safePos.x, safePos.y + 0.9, safePos.z, 36, 0.65, 0.75, 0.65, 0.20);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, safePos.x, safePos.y + 1.1, safePos.z, 28, 0.6, 0.8, 0.6, 0.18);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, safePos.x, safePos.y + 1.3, safePos.z, 18, 0.7, 0.7, 0.7, 0.04);
        ShockwaveRingEntity.spawn(level, safePos.add(0, 0.25, 0), 5.2f, COLOR_LIBERATION_GOLD, 12);

        // 2. Sinh Dân Làng Plains áo choàng nâu khớp 100% model bị nhốt trong lồng!
        Villager freed = EntityType.VILLAGER.create(level);
        if (freed != null) {
            float faceYaw = wrapAngle(this.yBodyRot + 180.0f);
            freed.moveTo(safePos.x, safePos.y + 0.1, safePos.z, faceYaw, 0.0f);
            freed.setVillagerData(freed.getVillagerData()
                    .setType(VillagerType.PLAINS)
                    .setProfession(VillagerProfession.NONE));
            freed.setCustomName(Component.literal("✦ Dân Làng Được Giải Thoát ✦")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            freed.setCustomNameVisible(true);
            freed.setHealth(freed.getMaxHealth());
            // Bọc bảo vệ ngắn hạn để không chết oan bởi lửa/hiệu ứng rơi vãi ngay lúc vừa thoát lồng!
            freed.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 3, false, false, true));
            freed.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 2, false, false, true));
            freed.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1, false, false, true));
            freed.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0, false, false, true));
            freed.setDeltaMovement(0.0, 0.34, 0.0);
            level.addFreshEntity(freed);
        }

        // 3. Quà cảm ơn từ dân làng được cứu (Ngọc Lục Bảo + Táo Vàng!)
        int emeraldCount = 5 + this.random.nextInt(4);
        ItemEntity emeralds = new ItemEntity(level, safePos.x, safePos.y + 0.6, safePos.z,
                new ItemStack(Items.EMERALD, emeraldCount));
        emeralds.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.2, 0.32, (this.random.nextDouble() - 0.5) * 0.2);
        level.addFreshEntity(emeralds);

        ItemEntity apple = new ItemEntity(level, safePos.x, safePos.y + 0.6, safePos.z,
                new ItemStack(Items.GOLDEN_APPLE, 1 + this.random.nextInt(2)));
        apple.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.2, 0.36, (this.random.nextDouble() - 0.5) * 0.2);
        level.addFreshEntity(apple);

        // 4. Thông báo ăn mừng tới người chơi gần đó
        Component msg = Component.literal("✦ Xiềng xích vỡ tan! Dân làng bị giam trong lồng sắt đã được giải thoát và tặng quà cảm ơn!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(safePos, safePos).inflate(48.0))) {
            player.sendSystemMessage(msg);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saveSharedGeoBossData(tag);
        tag.putBoolean("CyclopsPrisonerReleased", this.prisonerReleased);
        tag.putBoolean("CyclopsPhase2Applied", this.phase2Applied);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadSharedGeoBossData(tag, null, null, null, null, null, null);
        this.prisonerReleased = tag.getBoolean("CyclopsPrisonerReleased");
        this.entityData.set(DATA_PRISONER_RELEASED, this.prisonerReleased);
        this.phase2Applied = tag.getBoolean("CyclopsPhase2Applied");
        this.snapSyncPrevAngles();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 1 AnimationController thống nhất duy nhất (0 xung đột bone, chuyển cảnh êm 3 tick!)
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            SkillState skill = getSkillState();
            if (!this.isAlive() || skill == SkillState.DEATH) {
                return state.setAndContinue(ANIM_DEATH);
            }
            if (this.isSpawningIn()) {
                return state.setAndContinue(ANIM_EMERGE);
            }
            if (skill != SkillState.IDLE) {
                int nonce = this.entityData.get(DATA_ACTION_NONCE);
                if (nonce != this.lastClientActionNonce) {
                    this.lastClientActionNonce = nonce;
                    state.getController().forceAnimationReset();
                }
                RawAnimation anim = rawAnimationForSkill(skill);
                if (anim != null) {
                    return state.setAndContinue(anim);
                }
            }
            if (state.isMoving() || this.getDeltaMovement().horizontalDistanceSqr() > 0.0020) {
                return state.setAndContinue(ANIM_WALK);
            }
            return state.setAndContinue(ANIM_IDLE);
        }));
    }

    @Nullable
    private static RawAnimation rawAnimationForSkill(SkillState skill) {
        return switch (skill) {
            case MACE_SLAM -> ANIM_MACE_SLAM;
            case MACE_SWEEP -> ANIM_MACE_SWEEP;
            case BRACER_BASH -> ANIM_BRACER_BASH;
            case ROAR -> ANIM_ROAR;
            default -> null;
        };
    }
}
