package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.vfx.EnergySlashEntity;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * BOSS FENRIR — FEL V5 (Thần Sói Bão Tố, nguyên mẫu Fel trong *Tondemo Skill de Isekai Hourou Meshi*):
 * - 218 khớp nối zero-bind-rotation (`fenrir_boss.geo.json`), 11 animation sóng đa khớp mượt mà:
 *   TOÀN BỘ 70 sợi lông chỉa ra trên đuôi, dải gió lưng (5 khớp), bờm má (4 khớp), bờm cổ/sườn/hông (3 khớp),
 *   yếm ngực/cằm/đỉnh đầu & 28 sợi lông phủ 4 chân (2 khớp) đều là chuỗi đa khớp (164 khớp lông riêng) uốn sóng!
 * - Bộ lông trắng xanh băng tuyết chải sợi mượt đồng bộ toàn thân & 4 chân (`FUR_LEG_FEATHERED` riêng cho chân).
 * - Máu cơ bản 4,000 HP (`BASE_MAX_HEALTH = 4000.0f` qua `BossHealthHolder`).
 * - 6 kỹ năng Phong Ma Pháp & Trảo Huyết:
 *   1. Crimson Claw Wind-Rend (`CLAW_SLASH`)
 *   2. Gale Fang Lunge (`BITE_LUNGE`)
 *   3. Divine Gale Pounce (`POUNCE`)
 *   4. Fenrir Tempest Howl (`HOWL`)
 *   5. Divine Wind Barrage (`WIND_BARRAGE`)
 *   6. Sky-Shattering Gale Tornado (`TORNADO_SUMMON`): Triệu hồi 1–2 cột lốc xoáy di động hút và HẤT TUNG người chơi lên cao 12–16 block!
 */
public class FenrirBossEntity extends AbstractGeoMythicBoss {
    public static final float RENDER_SCALE = 1.85f;
    public static final float HITBOX_WIDTH = 4.6f;
    public static final float HITBOX_HEIGHT = 4.6f;
    public static final float HITBOX_EYE_HEIGHT = 3.9f;
    public static final float BASE_MAX_HEALTH = 4000.0f;

    // --- Hằng số tên bone khớp 100% với fenrir_boss.geo.json V7 (222 bones) ---
    public static final String ROOT_BONE = "root";
    public static final String BODY_BONE = "body";
    public static final String PELVIS_BONE = "pelvis";
    public static final String SPINE_1_BONE = "spine_1";
    public static final String SPINE_2_BONE = "spine_2";
    public static final String SPINE_3_BONE = "spine_3";
    public static final String CHEST_BONE = "chest";
    public static final String NECK_1_BONE = "neck_1";
    public static final String NECK_2_BONE = "neck_2";
    public static final String NECK_3_BONE = "neck_3";
    public static final String NECK_4_BONE = "neck_4";
    public static final String HEAD_BONE = "head";
    public static final String JAW_BONE = "jaw";
    public static final String MANE_TOP_BONE = "mane_top";
    public static final String EYELID_L_UP_BONE = "eyelid_l_up";
    public static final String EYELID_L_LO_BONE = "eyelid_l_lo";
    public static final String EYELID_R_UP_BONE = "eyelid_r_up";
    public static final String EYELID_R_LO_BONE = "eyelid_r_lo";

    // --- Bảng màu VFX chuẩn Fel Trắng Xanh Băng Tuyết + Huyết Đỏ ---
    public static final int COLOR_CRIMSON_CLAW = 0xD6222E;
    public static final int COLOR_DIVINE_GALE = 0x6CF2FF;
    public static final int COLOR_ICE_WHITE = 0xCBEAFF;
    public static final int COLOR_EMERALD_EYE = 0x54FA58;

    private static final DustParticleOptions TORNADO_CYAN_DUST =
            new DustParticleOptions(new Vector3f(0.45f, 0.94f, 1.0f), 1.85f);
    private static final DustParticleOptions TORNADO_SNOW_DUST =
            new DustParticleOptions(new Vector3f(0.92f, 0.98f, 1.0f), 1.55f);

    private static final ResourceLocation PHASE2_DAMAGE_MOD_ID = AbyssalRpgMod.id("fenrir_phase2_damage");
    private static final ResourceLocation PHASE2_SPEED_MOD_ID = AbyssalRpgMod.id("fenrir_phase2_speed");

    // --- 11 Animation GeckoLib ---
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("animation.fenrir_boss.idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("animation.fenrir_boss.walk");
    private static final RawAnimation ANIM_DEATH = RawAnimation.begin().thenPlayAndHold("animation.fenrir_boss.death");

    private static final RawAnimation ANIM_EMERGE = RawAnimation.begin().thenPlayAndHold("animation.fenrir_boss.emerge");
    private static final RawAnimation ANIM_CLAW_SLASH = RawAnimation.begin().thenPlay("animation.fenrir_boss.claw_slash");
    private static final RawAnimation ANIM_BITE_LUNGE = RawAnimation.begin().thenPlay("animation.fenrir_boss.bite_lunge");
    private static final RawAnimation ANIM_POUNCE = RawAnimation.begin().thenPlay("animation.fenrir_boss.pounce");
    private static final RawAnimation ANIM_HOWL = RawAnimation.begin().thenPlay("animation.fenrir_boss.howl");
    private static final RawAnimation ANIM_WIND_BARRAGE = RawAnimation.begin().thenPlay("animation.fenrir_boss.wind_barrage");
    private static final RawAnimation ANIM_TORNADO_CAST = RawAnimation.begin().thenPlay("animation.fenrir_boss.tornado_cast");
    private static final RawAnimation ANIM_PHASE_CHANGE = RawAnimation.begin().thenPlay("animation.fenrir_boss.phase_change");

    public enum SkillState {
        IDLE(0),
        CLAW_SLASH(1),
        BITE_LUNGE(2),
        POUNCE(3),
        HOWL(4),
        WIND_BARRAGE(5),
        TORNADO_SUMMON(6),
        PHASE_CHANGE(7),
        DEATH(8);

        public final int id;
        SkillState(int id) { this.id = id; }

        public static SkillState byId(int id) {
            for (SkillState s : values()) {
                if (s.id == id) return s;
            }
            return IDLE;
        }
    }

    /** Cột lốc xoáy Phong Thần tồn tại trên Server: di chuyển đuổi mục tiêu, vẽ phễu xoáy 12 block & hất tung người chơi lên cao! */
    private static final class ActiveWindTornado {
        Vec3 pos;
        Vec3 driftDir;
        int ticksLeft;
        final float sliceDamage;
        final float launchVy;
        float spinAngle;

        ActiveWindTornado(Vec3 startPos, Vec3 driftDir, int durationTicks, float sliceDamage, float launchVy) {
            this.pos = startPos;
            this.driftDir = driftDir;
            this.ticksLeft = durationTicks;
            this.sliceDamage = sliceDamage;
            this.launchVy = launchVy;
            this.spinAngle = 0.0f;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_SKILL_STATE =
            SynchedEntityData.defineId(FenrirBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_NONCE =
            SynchedEntityData.defineId(FenrirBossEntity.class, EntityDataSerializers.INT);

    private static final int PHASE2_TRANSITION_TICKS = 64;

    private int cycle = 0;
    private int actionTicksRemaining = 0;
    private boolean phase2StatsApplied = false;
    private final List<ActiveWindTornado> activeTornadoes = new ArrayList<>();

    // --- Trạng thái phía Client (head-tracking mượt + cache vị trí đầu) ---
    private int lastClientActionNonce = -1;
    private float clientTrackedHeadYawDeg = 0.0f;
    private float clientTrackedHeadPitchDeg = 0.0f;
    private Vec3 clientHeadWorldPos = Vec3.ZERO;
    private int lastClientAmbientTick = -100;

    @SuppressWarnings("this-escape")
    public FenrirBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        this.transitionTotalTicks = PHASE2_TRANSITION_TICKS;
        this.xpReward = 900;
        initializeBossHealth(BASE_MAX_HEALTH);
        setBossDefense(28.0f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKILL_STATE, SkillState.IDLE.id);
        builder.define(DATA_ACTION_NONCE, 0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new LargeGeoGroundNavigation(this, level);
    }

    @Override
    protected int getDefaultSpawnEggFloor() {
        return 15;
    }

    @Override
    public float getColossalScale(int floor) {
        return 1.0f;
    }

    @Override
    protected Component getStandaloneBossTitle() {
        return Component.literal("⚔ FEL — Fenrir, Thần Sói Bão Tố ⚔")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0)
                .add(Attributes.ATTACK_DAMAGE, 26.0)
                .add(Attributes.ARMOR, 20.0)
                .add(Attributes.ARMOR_TOUGHNESS, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.40)
                .add(Attributes.FOLLOW_RANGE, 58.0)
                .add(Attributes.STEP_HEIGHT, 2.6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public void configureForFloor(int floor, BlockPos arenaCenter, boolean dungeonBoss) {
        super.configureForFloor(floor, arenaCenter, dungeonBoss);
        int f = Math.max(MIN_TIER, Math.min(MAX_TIER, floor));
        // Đảm bảo HP tối thiểu luôn là 4,000 HP (tầng 15 mặc định = đúng 4000 HP, tầng cao hơn tăng nhẹ!)
        float maxHp = Math.max(BASE_MAX_HEALTH, BASE_MAX_HEALTH + Math.max(0, f - 15) * 200.0f);
        initializeBossHealth(maxHp);
        setBossDefense(26.0f + Math.min(14.0f, f * 0.75f));
        setAttr(Attributes.ATTACK_DAMAGE, 22.0 + f * 0.85);
        setAttr(Attributes.MOVEMENT_SPEED, 0.39 + Math.min(0.06, f * 0.003));
        setAttr(Attributes.STEP_HEIGHT, 2.6);
        setAttr(Attributes.SCALE, 1.0);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(10.0, 6.0, 10.0);
    }

    public SkillState getSkillState() {
        return SkillState.byId(this.entityData.get(DATA_SKILL_STATE));
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
        float maxStep = 4.0f;
        this.clientTrackedHeadYawDeg = Mth.clamp(
                Mth.approachDegrees(this.clientTrackedHeadYawDeg, targetYawDeg, maxStep), -38.0f, 38.0f);
        this.clientTrackedHeadPitchDeg = Mth.clamp(
                Mth.approachDegrees(this.clientTrackedHeadPitchDeg, targetPitchDeg, maxStep), -24.0f, 24.0f);
    }

    public float getClientTrackedHeadYawDeg() {
        return this.clientTrackedHeadYawDeg;
    }

    public float getClientTrackedHeadPitchDeg() {
        return this.clientTrackedHeadPitchDeg;
    }

    public void updateClientHeadWorldPos(double wx, double wy, double wz) {
        this.clientHeadWorldPos = new Vec3(wx, wy, wz);
    }

    public boolean consumeClientAmbientTick() {
        if (this.tickCount - this.lastClientAmbientTick >= 8) {
            this.lastClientAmbientTick = this.tickCount;
            return true;
        }
        return false;
    }

    public Vec3 getMouthWorldPos() {
        if (this.level().isClientSide && this.clientHeadWorldPos.lengthSqr() > 1.0) {
            return this.clientHeadWorldPos;
        }
        return modelPointToWorld(0.0f, 40.8f, -44.2f, RENDER_SCALE);
    }

    @Override
    protected void customServerAiStep() {
        if (this.level() instanceof ServerLevel sl) {
            tickActiveTornadoes(sl);

            int trans = getPhaseTransitionTicks();
            if (trans > 0) {
                this.setDeltaMovement(0.0, Math.min(0.0, this.getDeltaMovement().y), 0.0);
                this.getNavigation().stop();
                int next = trans - 1;
                this.entityData.set(DATA_PHASE_TRANS_TICKS, next);

                if (next % 6 == 0) {
                    sl.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 2.4, this.getZ(), 12, 2.8, 1.5, 2.8, 0.09);
                    sl.sendParticles(TORNADO_CYAN_DUST, this.getX(), this.getY() + 3.0, this.getZ(), 16, 2.8, 1.8, 2.8, 0.06);
                }
                if (next == 32) {
                    this.entityData.set(DATA_ENRAGED_VISUAL, true);
                    sl.playSound(null, this.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 2.4f, 0.52f);
                    sl.playSound(null, this.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.8f, 0.85f);
                    ShockwaveRingEntity.spawn(sl, this.position().add(0, 0.35, 0), 16.5f, COLOR_DIVINE_GALE, 18);
                    ShockwaveRingEntity.spawn(sl, this.position().add(0, 0.75, 0), 12.0f, COLOR_CRIMSON_CLAW, 14);
                    ModNetworking.sendShakeNearby(sl, this.position(), 44.0, 2.5f, 18);
                    damagePlayersInRadius(sl, this.position(), 11.5, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.15f, 1.4);
                    // Gọi ngay 2 cột lốc xoáy hộ vệ khi thức tỉnh Phase 2!
                    spawnTwinTornadoes(sl, this.getTarget(), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.45f, true);
                }
                if (next <= 0) {
                    applyPhase2Buffs();
                    this.entityData.set(DATA_SKILL_STATE, SkillState.IDLE.id);
                    this.snapSyncPrevAngles();
                }
                return;
            }
        }

        if (this.actionTicksRemaining > 0) {
            this.actionTicksRemaining--;
            if (this.actionTicksRemaining <= 0 && getSkillState() != SkillState.DEATH) {
                this.entityData.set(DATA_SKILL_STATE, SkillState.IDLE.id);
            }
        }

        super.customServerAiStep();
    }

    /** Cập nhật toàn bộ cột lốc xoáy Phong Thần đang hoạt động: vẽ phễu xoáy 12 block, hút & HẤT TUNG người chơi lên cao! */
    private void tickActiveTornadoes(ServerLevel level) {
        if (this.activeTornadoes.isEmpty()) return;

        LivingEntity focusTarget = this.getTarget();
        Iterator<ActiveWindTornado> it = this.activeTornadoes.iterator();
        while (it.hasNext()) {
            ActiveWindTornado t = it.next();
            t.ticksLeft--;
            if (t.ticksLeft <= 0) {
                ShockwaveRingEntity.spawn(level, t.pos.add(0, 0.4, 0), 5.2f, COLOR_ICE_WHITE, 8);
                it.remove();
                continue;
            }

            // Tự xoay & lướt đuổi mục tiêu mượt mà trên địa hình
            t.spinAngle += 0.58f;
            if (focusTarget != null && focusTarget.isAlive()) {
                Vec3 toT = focusTarget.position().subtract(t.pos);
                Vec3 horiz = new Vec3(toT.x, 0.0, toT.z);
                if (horiz.lengthSqr() > 1.2) {
                    t.driftDir = t.driftDir.scale(0.84).add(horiz.normalize().scale(0.16)).normalize();
                }
            }
            Vec3 nextPos = t.pos.add(t.driftDir.x * 0.21, 0.0, t.driftDir.z * 0.21);
            // Bám cao độ mặt đất nhẹ nhàng để không chui xuống lòng đất hay bay mất khi địa hình gồ ghề
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(nextPos.x), Mth.floor(nextPos.z));
            double clampedY = Mth.clamp(surfaceY, this.getY() - 5.0, this.getY() + 8.0);
            t.pos = new Vec3(nextPos.x, Mth.lerp(0.28, t.pos.y, clampedY), nextPos.z);

            // Hiệu ứng 3D phễu lốc xoáy cao 12 block (2 cánh xoắn ốc liên tục + mây + tia băng lam!)
            for (int ring = 0; ring < 8; ring++) {
                double frac = ring / 7.0;
                double y = t.pos.y + 0.35 + frac * 11.4;
                double radius = 1.15 + frac * 3.15;
                double a1 = t.spinAngle + frac * Math.PI * 3.4;
                double a2 = a1 + Math.PI;
                level.sendParticles(ParticleTypes.CLOUD,
                        t.pos.x + Math.cos(a1) * radius, y, t.pos.z + Math.sin(a1) * radius,
                        1, 0.14, 0.14, 0.14, 0.02);
                level.sendParticles(ring % 2 == 0 ? TORNADO_CYAN_DUST : TORNADO_SNOW_DUST,
                        t.pos.x + Math.cos(a2) * radius, y, t.pos.z + Math.sin(a2) * radius,
                        2, 0.18, 0.18, 0.18, 0.01);
            }
            if (t.ticksLeft % 4 == 0) {
                double midA = t.spinAngle * 1.4;
                level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        t.pos.x + Math.cos(midA) * 2.1, t.pos.y + 3.2, t.pos.z + Math.sin(midA) * 2.1,
                        1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        t.pos.x, t.pos.y + 5.5, t.pos.z, 5, 2.4, 4.0, 2.4, 0.06);
            }
            if (t.ticksLeft % 18 == 0) {
                ShockwaveRingEntity.spawn(level, t.pos.add(0, 0.35, 0), 4.8f, COLOR_DIVINE_GALE, 10);
                level.playSound(null, BlockPos.containing(t.pos), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                        SoundSource.HOSTILE, 1.35f, 0.72f + this.random.nextFloat() * 0.25f);
            }

            // Vật lý hút xoáy & HẤT TUNG người chơi lên trên cao!
            AABB box = new AABB(t.pos.x - 4.8, t.pos.y - 1.5, t.pos.z - 4.8,
                    t.pos.x + 4.8, t.pos.y + 14.5, t.pos.z + 4.8);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && e != this && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));

            for (LivingEntity victim : victims) {
                double dx = victim.getX() - t.pos.x;
                double dz = victim.getZ() - t.pos.z;
                double distSq = dx * dx + dz * dz;
                double dy = victim.getY() - t.pos.y;
                double coneRadius = 3.2 + Mth.clamp(dy / 12.0, 0.0, 1.0) * 1.8;
                if (distSq > coneRadius * coneRadius) continue;

                double dist = Math.max(0.25, Math.sqrt(distSq));
                // Vận tốc xoáy tròn tiếp tuyến + hút vào tâm phễu + đẩy cực mạnh lên trời!
                double tangX = -dz / dist;
                double tangZ = dx / dist;
                double pullX = -dx / dist;
                double pullZ = -dz / dist;

                Vec3 curVel = victim.getDeltaMovement();
                double targetVy;
                double outPush = 0.0;
                if (dy < 5.5) {
                    // Đang ở thấp / dưới đất -> HẤT MẠNH NGƯỢC LÊN TRỜI (~12-16 block!)
                    targetVy = Math.max(curVel.y, t.launchVy);
                } else if (dy < 11.0) {
                    // Đang ở giữa phễu -> tiếp tục nâng xoáy lên cao
                    targetVy = Math.max(curVel.y + 0.18, 0.78);
                } else {
                    // Đạt đỉnh lốc -> xoay văng nhẹ ra rìa
                    targetVy = Math.max(curVel.y, 0.35);
                    outPush = 0.48;
                }

                double vx = tangX * 0.42 + pullX * (0.24 - outPush);
                double vz = tangZ * 0.42 + pullZ * (0.24 - outPush);
                victim.setDeltaMovement(vx, targetVy, vz);
                victim.hurtMarked = true;
                victim.hasImpulse = true;

                if (t.ticksLeft % 12 == 0) {
                    victim.hurt(this.damageSources().mobAttack(this), t.sliceDamage);
                    level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                            SoundSource.HOSTILE, 1.0f, 1.35f);
                }
            }
        }
    }

    private void spawnTwinTornadoes(ServerLevel level, @Nullable LivingEntity target, float sliceDmg, boolean dual) {
        if (this.activeTornadoes.size() >= 4) return;
        Vec3 aimBase = (target != null && target.isAlive())
                ? target.position()
                : this.position().add(this.getLookAngle().scale(8.0));
        Vec3 forward = aimBase.subtract(this.position());
        Vec3 horiz = new Vec3(forward.x, 0.0, forward.z);
        Vec3 dir = horiz.lengthSqr() > 1e-4 ? horiz.normalize() : new Vec3(0.0, 0.0, 1.0);
        Vec3 right = new Vec3(-dir.z, 0.0, dir.x);

        if (!dual) {
            Vec3 spawnPos = this.position().add(dir.scale(Math.min(7.5, Math.max(3.5, horiz.length() * 0.65))));
            this.activeTornadoes.add(new ActiveWindTornado(spawnPos, dir, 100, sliceDmg, 1.48f));
            ShockwaveRingEntity.spawn(level, spawnPos.add(0, 0.4, 0), 7.5f, COLOR_DIVINE_GALE, 14);
        } else {
            for (int side : new int[]{-1, 1}) {
                Vec3 spawnPos = this.position().add(dir.scale(5.2)).add(right.scale(side * 4.2));
                Vec3 d = dir.add(right.scale(-side * 0.25)).normalize();
                this.activeTornadoes.add(new ActiveWindTornado(spawnPos, d, 110, sliceDmg * 1.15f, 1.62f));
                ShockwaveRingEntity.spawn(level, spawnPos.add(0, 0.4, 0), 8.2f, COLOR_DIVINE_GALE, 14);
            }
        }
        level.playSound(null, this.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.4f, 0.62f);
        ModNetworking.sendShakeNearby(level, this.position(), 38.0, 1.8f, 12);
    }

    @Override
    protected void onPhaseTransition(ServerLevel level, int newPhase) {
        super.onPhaseTransition(level, newPhase);
        if (newPhase >= 2 && !this.hasEnteredPhase2) {
            this.hasEnteredPhase2 = true;
            this.entityData.set(DATA_HAS_ENTERED_PHASE2, true);
            this.entityData.set(DATA_PHASE_TRANS_TICKS, PHASE2_TRANSITION_TICKS);
            this.lockCurrentRotation();
            this.getNavigation().stop();
            beginAction(SkillState.PHASE_CHANGE, PHASE2_TRANSITION_TICKS);
            level.playSound(null, this.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.0f, 0.72f);
            ShockwaveRingEntity.spawn(level, this.position().add(0, 0.3, 0), 12.0f, COLOR_ICE_WHITE, 14);
        }
    }

    private void applyPhase2Buffs() {
        this.entityData.set(DATA_ENRAGED_VISUAL, true);
        if (!this.phase2StatsApplied) {
            this.phase2StatsApplied = true;
            applyPermanentMultiplier(Attributes.ATTACK_DAMAGE, PHASE2_DAMAGE_MOD_ID, 0.35);
            applyPermanentMultiplier(Attributes.MOVEMENT_SPEED, PHASE2_SPEED_MOD_ID, 0.28);
            setBossDefense(getBossDefense() + 10.0f);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isSpawningIn() || this.isTransitioningPhase()) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected int executeBossSkill(ServerLevel level, LivingEntity target) {
        if (this.isTransitioningPhase()) {
            return 20;
        }
        boolean p2 = this.phase >= 2 || this.hasEnteredPhase2;
        float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (p2 ? 1.38 : 1.15));
        double dist = this.distanceTo(target);
        int move = (dist < 7.0 && cycle % 3 == 1) ? 1 : (cycle % 6);
        cycle++;

        switch (move) {
            case 0 -> {
                // SKILL 1 (MỚI!): Sky-Shattering Gale Tornado — Triệu hồi Lốc Xoáy đẩy người chơi lên cao trên trời!
                beginAction(SkillState.TORNADO_SUMMON, 40);
                level.playSound(null, this.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 2.2f, 0.64f);
                scheduleAction(15, null, () -> {
                    if (!this.isAlive()) return;
                    spawnTwinTornadoes(level, target, dmg * 0.42f, p2);
                });
            }
            case 1 -> {
                // SKILL 2: Gale Fang Lunge — Phóng nhanh ngoạm gió tầm gần
                beginAction(SkillState.BITE_LUNGE, 22);
                level.playSound(null, this.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.HOSTILE, 2.0f, 0.55f);
                Vec3 dash = target.position().subtract(this.position());
                Vec3 horiz = new Vec3(dash.x, 0.0, dash.z);
                Vec3 dir = horiz.lengthSqr() > 1e-4 ? horiz.normalize() : this.getLookAngle();
                this.setDeltaMovement(dir.x * 1.45, 0.35, dir.z * 1.45);
                this.hurtMarked = true;
                scheduleAction(10, null, () -> {
                    if (!this.isAlive()) return;
                    Vec3 bitePos = getMouthWorldPos();
                    level.playSound(null, this.blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2.0f, 0.65f);
                    ShockwaveRingEntity.spawn(level, bitePos, 6.8f, COLOR_ICE_WHITE, 10);
                    level.sendParticles(ParticleTypes.CLOUD, bitePos.x, bitePos.y, bitePos.z, 18, 1.4, 0.8, 1.4, 0.12);
                    damagePlayersInRadius(level, bitePos, 6.5, dmg * 1.18f, 1.15);
                });
            }
            case 2 -> {
                // SKILL 3: Crimson Claw Wind-Rend — Song/Tam trảo kiếm khí Đỏ Huyết & Phong Thần!
                beginAction(SkillState.CLAW_SLASH, 24);
                level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.8f, 0.76f);
                scheduleAction(10, null, () -> {
                    if (!this.isAlive() || target == null) return;
                    Vec3 origin = getMouthWorldPos().add(0, -0.5, 0);
                    Vec3 aim = target.position().add(0, 1.0, 0).subtract(origin).normalize();
                    EnergySlashEntity.spawn(level, this, origin, aim, 1.55f, 7.6f, 36f, COLOR_CRIMSON_CLAW, dmg, false);
                    EnergySlashEntity.spawn(level, this, origin, aim, 1.55f, 7.6f, -36f, COLOR_CRIMSON_CLAW, dmg, false);
                    if (p2) {
                        EnergySlashEntity.spawn(level, this, origin, aim, 1.72f, 8.8f, 0f, COLOR_DIVINE_GALE, dmg * 0.95f, false);
                    }
                    level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.65f);
                });
            }
            case 3 -> {
                // SKILL 4: Divine Gale Pounce — Bật vồ 4 chân giáng chấn động sóng gió lam-đỏ!
                beginAction(SkillState.POUNCE, 32);
                level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.7f, 0.78f);
                scheduleAction(8, null, () -> {
                    if (!this.isAlive() || target == null) return;
                    Vec3 jump = target.position().subtract(this.position());
                    Vec3 dir = new Vec3(jump.x, 0.0, jump.z).normalize();
                    this.setDeltaMovement(dir.x * 1.75, 0.96, dir.z * 1.75);
                    this.hurtMarked = true;
                });
                scheduleAction(21, null, () -> {
                    if (!this.isAlive()) return;
                    level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.62f);
                    ModNetworking.sendShakeNearby(level, this.position(), 38.0, 2.4f, 14);
                    ShockwaveRingEntity.spawn(level, this.position().add(0, 0.3, 0), 13.5f, COLOR_CRIMSON_CLAW, 14);
                    ShockwaveRingEntity.spawn(level, this.position().add(0, 0.6, 0), 9.5f, COLOR_DIVINE_GALE, 12);
                    damagePlayersInRadius(level, this.position(), 10.5, dmg * 1.30f, 1.05);
                });
            }
            case 4 -> {
                // SKILL 5: Divine Wind Barrage — Quạt 5–7 lưỡi gió bạc-lam cắt không khí!
                beginAction(SkillState.WIND_BARRAGE, 30);
                level.playSound(null, this.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.2f, 0.72f);
                scheduleAction(13, null, () -> {
                    if (!this.isAlive() || target == null) return;
                    Vec3 origin = getMouthWorldPos();
                    Vec3 baseAim = target.position().add(0, 1.0, 0).subtract(origin).normalize();
                    int blades = p2 ? 7 : 5;
                    int half = blades / 2;
                    for (int i = -half; i <= half; i++) {
                        double yawOff = Math.toRadians(i * 11.5);
                        double cos = Math.cos(yawOff);
                        double sin = Math.sin(yawOff);
                        Vec3 dir = new Vec3(
                                baseAim.x * cos - baseAim.z * sin,
                                baseAim.y * 0.85,
                                baseAim.x * sin + baseAim.z * cos
                        ).normalize();
                        int col = (i == 0) ? COLOR_DIVINE_GALE : COLOR_ICE_WHITE;
                        EnergySlashEntity.spawn(level, this, origin, dir, 1.65f, 6.8f, i * 8.0f, col, dmg * 0.88f, false);
                    }
                    level.playSound(null, this.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.6f, 1.15f);
                });
            }
            default -> {
                // SKILL 6: Fenrir Tempest Howl — Tiếng hú Thần Sói triệu hồi vòng ma pháp bão tố!
                beginAction(SkillState.HOWL, 36);
                level.playSound(null, this.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 2.4f, 0.52f);
                scheduleAction(12, null, () -> {
                    if (!this.isAlive() || target == null) return;
                    ShockwaveRingEntity.spawn(level, this.position().add(0, 1.2, 0), 11.0f, COLOR_DIVINE_GALE, 14);
                    int count = p2 ? 7 : 5;
                    for (int i = 0; i < count; i++) {
                        double a = (Math.PI * 2.0 * i) / count;
                        double r = (i == 0) ? 0.0 : 5.8;
                        Vec3 spot = target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                        int col = (i % 2 == 0) ? COLOR_DIVINE_GALE : COLOR_CRIMSON_CLAW;
                        MagicCircleEntity.spawn(level, this, spot, 3.6f, 18 + i * 2, col, dmg * 1.05f, true, false);
                    }
                });
            }
        }
        return p2 ? 42 : 58;
    }

    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        if (this.phase >= 2 || isEnragedVisual()) {
            level.sendParticles(TORNADO_CYAN_DUST, this.getX(), this.getY() + 2.8, this.getZ(), 4, 2.2, 1.5, 2.2, 0.03);
            level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1.6, this.getZ(), 2, 2.0, 0.8, 2.0, 0.02);
        }
    }

    @Override
    public void die(DamageSource source) {
        this.activeTornadoes.clear();
        this.entityData.set(DATA_SKILL_STATE, SkillState.DEATH.id);
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        this.activeTornadoes.clear();
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saveSharedGeoBossData(tag);
        tag.putBoolean("FenrirPhase2Applied", this.phase2StatsApplied);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadSharedGeoBossData(tag, null, null, null, null, null, null);
        this.phase2StatsApplied = tag.getBoolean("FenrirPhase2Applied");
        if (this.hasEnteredPhase2 && !this.isTransitioningPhase()) {
            applyPhase2Buffs();
        }
        this.snapSyncPrevAngles();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Một controller thống nhất duy nhất (0 xung đột bone giữa movement & action, chuyển cảnh êm 3 tick!)
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            SkillState skill = getSkillState();
            if (!this.isAlive() || skill == SkillState.DEATH) {
                return state.setAndContinue(ANIM_DEATH);
            }
            if (this.isSpawningIn()) {
                return state.setAndContinue(ANIM_EMERGE);
            }
            if (this.isTransitioningPhase()) {
                return state.setAndContinue(ANIM_PHASE_CHANGE);
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
            if (state.isMoving() || this.getDeltaMovement().horizontalDistanceSqr() > 0.0025) {
                return state.setAndContinue(ANIM_WALK);
            }
            return state.setAndContinue(ANIM_IDLE);
        }));
    }

    @Nullable
    private static RawAnimation rawAnimationForSkill(SkillState skill) {
        return switch (skill) {
            case CLAW_SLASH -> ANIM_CLAW_SLASH;
            case BITE_LUNGE -> ANIM_BITE_LUNGE;
            case POUNCE -> ANIM_POUNCE;
            case HOWL -> ANIM_HOWL;
            case WIND_BARRAGE -> ANIM_WIND_BARRAGE;
            case TORNADO_SUMMON -> ANIM_TORNADO_CAST;
            case PHASE_CHANGE -> ANIM_PHASE_CHANGE;
            default -> null;
        };
    }
}
