package com.abyssalrpg.entity.mythic.cinder;

import com.abyssalrpg.config.CinderDragonConfig;
import com.abyssalrpg.entity.mythic.AbstractGeoMythicBoss;
import com.abyssalrpg.entity.mythic.CinderDragonBossEntity;
import com.abyssalrpg.entity.mythic.CinderDragonBossEntity.SkillState;
import com.abyssalrpg.entity.vfx.LaserBeamEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.CinderDragonVfxPayload;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Toàn bộ 10 kỹ năng chiến đấu + AI di chuyển thông minh của Boss Cinder Dragon — Hỏa Long Giáp Đá.
 * - Đồng bộ 0-tick với `cinder_dragon.animation.json` thông qua {@link DragonAttackTiming}.
 * - 6 chiêu Phase 1 (nổi bật Cinder Breath quét ngang tính hướng thực từ `mouth_flame` + chắn tầm nhìn vật cản).
 * - 4 chiêu ĐỘC QUYỀN Phase 2 (Skyfall Inferno, Scorched Earth Beam, Cataclysm Wing Dive, Ashen Supernova).
 * - Mọi sát thương dùng `mobAttack` (không bypass giáp người chơi, không `setHealth`); `terrainDamageEnabled = false`.
 */
public final class CinderDragonSkillGoals {
    private CinderDragonSkillGoals() {}

    // =========================================================================
    // AI CHASE MẶT ĐẤT KẾT HỢP STEERING TRỰC TIẾP CHỐNG KẸT
    // =========================================================================
    public static final class DragonChaseGoal extends Goal {
        private final CinderDragonBossEntity boss;
        private int repathTicks = 0;

        public DragonChaseGoal(CinderDragonBossEntity boss) {
            this.boss = boss;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = boss.getTarget();
            return t != null && t.isAlive()
                    && !boss.isRotationLocked()
                    && !boss.isFlying()
                    && boss.getSkillState() == SkillState.IDLE;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity t = boss.getTarget();
            if (t == null) return;
            Vec3 toTarget = t.position().subtract(boss.position());
            boss.smoothRotateToward(toTarget, 7.5f, (float) CinderDragonConfig.get().phase2TurnSpeedMultiplier);
            double distSq = boss.distanceToSqr(t);
            double stopDist = 10.5 * boss.getScaleRatio();
            if (distSq <= stopDist * stopDist) {
                boss.getNavigation().stop();
                return;
            }
            if (--this.repathTicks <= 0) {
                this.repathTicks = 10;
                if (!boss.getNavigation().moveTo(t, 1.15)) {
                    // Steering trực tiếp khi pathfinder bị cây/đá cản
                    boss.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.15);
                }
            }
        }
    }

    // =========================================================================
    // LỚP CHA SKILL ĐỒNG BỘ 1:1 VỚI DragonAttackTiming
    // =========================================================================
    private abstract static class TimedDragonSkillGoal extends Goal {
        protected final CinderDragonBossEntity boss;
        protected final SkillState state;
        protected final DragonAttackTiming.SkillTimeline timeline;
        protected double prevAnimTick = 0.0;
        protected double currAnimTick = 0.0;
        protected float lockedYawDeg = 0.0f;
        protected final Set<UUID> hitOnce = new HashSet<>();

        protected TimedDragonSkillGoal(CinderDragonBossEntity boss, SkillState state) {
            this.boss = boss;
            this.state = state;
            this.timeline = DragonAttackTiming.forSkill(state);
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        protected abstract boolean matchesCondition(LivingEntity target, double distSq);
        protected abstract int baseCooldown();
        protected abstract void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double currT);
        protected void onSkillStart(ServerLevel level, LivingEntity target) {}
        protected void onSkillEnd(ServerLevel level) {}

        @Override
        public boolean canUse() {
            if (!boss.canUseSkill(this.state)) return false;
            LivingEntity t = boss.getTarget();
            if (t == null || !t.isAlive()) return false;
            return matchesCondition(t, boss.distanceToSqr(t));
        }

        @Override
        public boolean canContinueToUse() {
            return !boss.isRotationLocked()
                    && boss.isAlive()
                    && boss.getSkillState() == this.state
                    && this.currAnimTick < this.timeline.totalTicks();
        }

        @Override
        public void start() {
            this.prevAnimTick = 0.0;
            this.currAnimTick = 0.0;
            this.hitOnce.clear();
            boss.getNavigation().stop();
            boss.setSkillState(this.state);
            LivingEntity t = boss.getTarget();
            if (t != null) {
                Vec3 diff = t.position().subtract(boss.position());
                this.lockedYawDeg = AbstractGeoMythicBoss.wrapAngle(
                        (float) (Mth.atan2(diff.z, diff.x) * Mth.RAD_TO_DEG) - 90.0f);
            } else {
                this.lockedYawDeg = boss.yBodyRot;
            }
            if (boss.level() instanceof ServerLevel sl && t != null) {
                onSkillStart(sl, t);
            }
        }

        @Override
        public void tick() {
            if (!(boss.level() instanceof ServerLevel sl)) return;
            LivingEntity t = boss.getTarget();
            float speed = DragonAttackTiming.getAnimSpeed(this.state, boss.hasEnteredPhase2());
            this.prevAnimTick = this.currAnimTick;
            this.currAnimTick += speed;

            if (t != null && t.isAlive() && this.currAnimTick <= this.timeline.lockAimTick()) {
                Vec3 diff = t.position().subtract(boss.position());
                boss.smoothRotateToward(diff, 9.5f, (float) CinderDragonConfig.get().phase2TurnSpeedMultiplier);
                this.lockedYawDeg = boss.yBodyRot;
            }
            onSkillTick(sl, t, this.prevAnimTick, this.currAnimTick);
        }

        @Override
        public void stop() {
            if (boss.level() instanceof ServerLevel sl) {
                onSkillEnd(sl);
            }
            if (boss.getSkillState() == this.state) {
                boss.setSkillState(SkillState.IDLE);
            }
            boss.setThroatGlow(boss.hasEnteredPhase2() ? 0.35f : 0.0f);
            if (this.state == SkillState.ASHEN_SUPERNOVA) {
                boss.markUltimateUsed();
            } else {
                boss.markSkillUsed(this.state, baseCooldown());
            }
        }

        protected Vec3 forwardFromLockedYaw() {
            double rad = this.lockedYawDeg * Mth.DEG_TO_RAD;
            return new Vec3(-Math.sin(rad), 0.0, Math.cos(rad)).normalize();
        }

        protected void damageArc(ServerLevel level, Vec3 origin, Vec3 forward, double range,
                                 double halfAngleDeg, float dmg, double knockback, boolean ignite, boolean uniqueHit) {
            double cosLimit = Math.cos(Math.toRadians(halfAngleDeg));
            AABB box = new AABB(origin.x - range, origin.y - 3.0, origin.z - range,
                    origin.x + range, origin.y + 6.5, origin.z + range);
            for (Player p : level.getEntitiesOfClass(Player.class, box,
                    pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                if (uniqueHit && this.hitOnce.contains(p.getUUID())) continue;
                Vec3 toP = p.position().subtract(origin);
                double horiz = Math.hypot(toP.x, toP.z);
                if (horiz > range) continue;
                if (horiz > 0.6) {
                    double dot = (toP.x * forward.x + toP.z * forward.z) / horiz;
                    if (dot < cosLimit) continue;
                }
                if (uniqueHit) this.hitOnce.add(p.getUUID());
                p.hurt(boss.damageSources().mobAttack(boss), dmg);
                if (ignite) p.igniteForSeconds(4);
                if (knockback > 0.0) {
                    Vec3 kb = horiz > 0.1 ? new Vec3(toP.x / horiz, 0, toP.z / horiz) : forward;
                    p.setDeltaMovement(p.getDeltaMovement().add(kb.x * knockback, 0.42, kb.z * knockback));
                    p.hurtMarked = true;
                }
            }
        }

        protected void damageRadius(ServerLevel level, Vec3 center, double radius, float dmg,
                                    double knockUp, boolean ignite, boolean jumpableWave) {
            AABB box = new AABB(center.x - radius, center.y - 3.0, center.z - radius,
                    center.x + radius, center.y + 5.0, center.z + radius);
            for (Player p : level.getEntitiesOfClass(Player.class, box,
                    pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                double dist = Math.hypot(p.getX() - center.x, p.getZ() - center.z);
                if (dist > radius) continue;
                if (jumpableWave && (p.getY() - center.y) > 1.15) continue; // Nhảy qua sóng lửa được!
                p.hurt(boss.damageSources().mobAttack(boss), dmg);
                if (ignite) p.igniteForSeconds(4);
                if (knockUp > 0.0) {
                    Vec3 away = p.position().subtract(center);
                    double len = Math.max(0.1, Math.hypot(away.x, away.z));
                    p.setDeltaMovement(p.getDeltaMovement().add((away.x / len) * 0.85, knockUp, (away.z / len) * 0.85));
                    p.hurtMarked = true;
                }
            }
        }

        protected boolean hasLineOfSight(ServerLevel level, Vec3 from, Vec3 to) {
            HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
            return hit.getType() == HitResult.Type.MISS;
        }
    }

    // =========================================================================
    // 1. CINDER BREATH — Khè lửa quét ngang trái->phải từ locator `mouth_flame`
    // =========================================================================
    public static final class CinderBreathGoal extends TimedDragonSkillGoal {
        private int lastTickBucket = -1;

        public CinderBreathGoal(CinderDragonBossEntity boss) {
            super(boss, SkillState.CINDER_BREATH);
        }

        @Override
        protected boolean matchesCondition(LivingEntity target, double distSq) {
            double s = boss.getScaleRatio();
            double minR = 5.5 * s;
            double maxR = 34.0 * s;
            return distSq >= minR * minR && distSq <= maxR * maxR;
        }

        @Override
        protected int baseCooldown() {
            return CinderDragonConfig.get().cinderBreathCooldown;
        }

        @Override
        protected void onSkillStart(ServerLevel level, LivingEntity target) {
            this.lastTickBucket = -1;
            boss.setThroatGlow(1.0f);
            float breathLen = 28.0f * boss.getScaleRatio();
            level.playSound(null, boss.blockPosition(), ModSounds.CINDER_ROAR, SoundSource.HOSTILE, 2.8f, 0.76f);
            ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_BREATH_TELEGRAPH,
                    boss.getMouthFlameWorldPos(), forwardFromLockedYaw(), breathLen, boss.isEnragedVisual());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double currT) {
            boss.setThroatGlow(1.0f);
            double s = boss.getScaleRatio();
            double range = 28.0 * s;
            if (currT < 20.0) {
                boss.setBreathSweepState(this.lockedYawDeg, -36.0f);
                if (((int) currT) % 6 == 0 && ((int) prevT) != ((int) currT)) {
                    ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_BREATH_TELEGRAPH,
                            boss.getMouthFlameWorldPos(), forwardFromLockedYaw(), (float) range, boss.isEnragedVisual());
                }
                return;
            }
            if (currT <= 46.0) {
                float progress = (float) Mth.clamp((currT - 20.0) / 26.0, 0.0, 1.0);
                float sweepDeg = Mth.lerp(progress, -36.0f, +36.0f);
                boss.setBreathSweepState(this.lockedYawDeg, sweepDeg);
                Vec3 mouth = boss.getMouthFlameWorldPos();
                Vec3 dir = boss.getMouthFlameDirection();

                if (prevT < 20.0 && currT >= 20.0) {
                    level.playSound(null, boss.blockPosition(), ModSounds.CINDER_BREATH, SoundSource.HOSTILE, 3.0f, 0.75f);
                }

                // Gây sát thương đúng mỗi 5 tick trong luồng nón lửa (mở rộng tầm & độ rộng khớp rồng cổ đại, có kiểm tra vật cản!)
                int bucket = (int) Math.floor((currT - 20.0) / 5.0);
                if (bucket > this.lastTickBucket) {
                    this.lastTickBucket = bucket;
                    float tickDmg = boss.scaleSkillDamage(CinderDragonConfig.get().cinderBreathTickDamage);
                    double cosCone = Math.cos(Math.toRadians(26.0));
                    AABB box = new AABB(mouth.x - range, mouth.y - 11.0 * s, mouth.z - range,
                            mouth.x + range, mouth.y + 7.5 * s, mouth.z + range);
                    for (Player p : level.getEntitiesOfClass(Player.class, box,
                            pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                        Vec3 eye = p.position().add(0, 1.0, 0);
                        Vec3 toP = eye.subtract(mouth);
                        double len = toP.length();
                        if (len > range || len < 0.5) continue;
                        if (toP.normalize().dot(dir) >= cosCone && hasLineOfSight(level, mouth, eye)) {
                            p.hurt(boss.damageSources().mobAttack(boss), tickDmg);
                            p.igniteForSeconds(4);
                        }
                    }
                    ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_BREATH_CONE,
                            mouth, dir, (float) range, boss.isEnragedVisual());
                }
            }
        }
    }

    // =========================================================================
    // 2. MAGMA FIREBALL BARRAGE — Khạc 3 cầu lửa parabol có vòng cảnh báo & vệt lửa 3.5s
    // =========================================================================
    public static final class MagmaFireballBarrageGoal extends TimedDragonSkillGoal {
        private final List<Vec3> targetSpots = new ArrayList<>();
        private final boolean[] launched = new boolean[3];
        private final boolean[] detonated = new boolean[3];

        public MagmaFireballBarrageGoal(CinderDragonBossEntity boss) {
            super(boss, SkillState.MAGMA_FIREBALL_BARRAGE);
        }

        @Override
        protected boolean matchesCondition(LivingEntity target, double distSq) {
            double s = boss.getScaleRatio();
            double minR = 10.0 * s;
            double maxR = 46.0 * s;
            return distSq >= minR * minR && distSq <= maxR * maxR;
        }

        @Override
        protected int baseCooldown() {
            return CinderDragonConfig.get().magmaFireballCooldown;
        }

        @Override
        protected void onSkillStart(ServerLevel level, LivingEntity target) {
            this.targetSpots.clear();
            for (int i = 0; i < 3; i++) {
                this.launched[i] = false;
                this.detonated[i] = false;
            }
            boss.setThroatGlow(0.95f);
            level.playSound(null, boss.blockPosition(), ModSounds.CINDER_ROAR, SoundSource.HOSTILE, 2.4f, 0.82f);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double currT) {
            int[] launchTicks = {16, 24, 32};
            int[] impactTicks = {26, 34, 42};
            double s = boss.getScaleRatio();
            for (int i = 0; i < 3; i++) {
                if (!this.launched[i] && currT >= launchTicks[i]) {
                    this.launched[i] = true;
                    Vec3 base = (target != null && target.isAlive()) ? target.position() : boss.position().add(forwardFromLockedYaw().scale(18.0 * s));
                    Vec3 offset = new Vec3((boss.getRandom().nextDouble() - 0.5) * 5.2 * s, 0, (boss.getRandom().nextDouble() - 0.5) * 5.2 * s);
                    Vec3 spot = (i == 0) ? base : base.add(offset);
                    this.targetSpots.add(spot);
                    level.playSound(null, boss.blockPosition(), ModSounds.CINDER_FIREBALL, SoundSource.HOSTILE, 2.4f, 0.76f + i * 0.04f);
                    ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_FIREBALL_TARGET,
                            spot, boss.getMouthFlameWorldPos().subtract(spot), (float) (4.4 * s), boss.isEnragedVisual());
                }
                if (this.launched[i] && !this.detonated[i] && currT >= impactTicks[i] && i < this.targetSpots.size()) {
                    this.detonated[i] = true;
                    Vec3 spot = this.targetSpots.get(i);
                    float dmg = boss.scaleSkillDamage(CinderDragonConfig.get().magmaFireballDamage);
                    damageRadius(level, spot, 4.8 * s, dmg, 0.35, true, false);
                    boss.addFirePatch(spot, (float) (3.8 * s), 70, boss.scaleSkillDamage(CinderDragonConfig.get().firePatchTickDamage));
                    level.playSound(null, spot.x, spot.y, spot.z, ModSounds.CINDER_SLAM, SoundSource.HOSTILE, 2.0f, 0.80f);
                    ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_FIREBALL_IMPACT,
                            spot, Vec3.ZERO, (float) (4.8 * s), boss.isEnragedVisual());
                }
            }
        }
    }

    // =========================================================================
    // 3. SAVAGE BITE & HORN GORE — Rụt cổ lấy đà -> lao chồm táp mạnh (đòn nặng 31)
    // =========================================================================
    public static final class SavageBiteGoal extends TimedDragonSkillGoal {
        public SavageBiteGoal(CinderDragonBossEntity boss) {
            super(boss, SkillState.SAVAGE_BITE);
        }

        @Override
        protected boolean matchesCondition(LivingEntity target, double distSq) {
            double r = 20.0 * boss.getScaleRatio();
            return distSq <= r * r;
        }

        @Override
        protected int baseCooldown() {
            return CinderDragonConfig.get().savageBiteCooldown;
        }

        @Override
        protected void onSkillStart(ServerLevel level, LivingEntity target) {
            level.playSound(null, boss.blockPosition(), ModSounds.CINDER_ROAR, SoundSource.HOSTILE, 2.4f, 0.82f);
            ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_FAN_TELEGRAPH,
                    boss.position(), forwardFromLockedYaw(), 19.5f * boss.getScaleRatio(), boss.isEnragedVisual());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double currT) {
            DragonAttackTiming.HitWindow hw = this.timeline.hitWindows()[0];
            double s = boss.getScaleRatio();
            if (hw.crossedImpact(prevT, currT)) {
                Vec3 fwd = forwardFromLockedYaw();
                boss.setDeltaMovement(fwd.scale(0.72).add(0, 0.14, 0));
                boss.hurtMarked = true;
                level.playSound(null, boss.blockPosition(), ModSounds.CINDER_BITE, SoundSource.HOSTILE, 2.7f, 0.78f);
                ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_WING_SLASH,
                        boss.getMouthFlameWorldPos(), fwd, (float) (16.5 * s), boss.isEnragedVisual());
            }
            if (hw.isActive(prevT, currT)) {
                float dmg = boss.scaleSkillDamage(CinderDragonConfig.get().savageBiteDamage);
                damageArc(level, boss.position(), forwardFromLockedYaw(), 19.5 * s, 52.0, dmg, 0.90, false, true);
            }
        }
    }

    // =========================================================================
    // 4. WING CLAW SWEEP — Quét cánh móng vuốt hình quạt rộng hất văng
    // =========================================================================
    public static final class WingClawSweepGoal extends TimedDragonSkillGoal {
        public WingClawSweepGoal(CinderDragonBossEntity boss) {
            super(boss, SkillState.WING_CLAW_SWEEP);
        }

        @Override
        protected boolean matchesCondition(LivingEntity target, double distSq) {
            double r = 23.0 * boss.getScaleRatio();
            return distSq <= r * r;
        }

        @Override
        protected int baseCooldown() {
            return CinderDragonConfig.get().wingClawSweepCooldown;
        }

        @Override
        protected void onSkillStart(ServerLevel level, LivingEntity target) {
            level.playSound(null, boss.blockPosition(), ModSounds.CINDER_WING_FLAP, SoundSource.HOSTILE, 2.7f, 0.76f);
            ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_FAN_TELEGRAPH,
                    boss.position(), forwardFromLockedYaw(), 22.5f * boss.getScaleRatio(), boss.isEnragedVisual());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double currT) {
            DragonAttackTiming.HitWindow hw = this.timeline.hitWindows()[0];
            double s = boss.getScaleRatio();
            if (hw.crossedImpact(prevT, currT)) {
                level.playSound(null, boss.blockPosition(), ModSounds.CINDER_WING_SWEEP, SoundSource.HOSTILE, 2.9f, 0.80f);
                ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_WING_SLASH,
                        boss.position().add(0, 2.6, 0), forwardFromLockedYaw(), (float) (22.5 * s), boss.isEnragedVisual());
            }
            if (hw.isActive(prevT, currT)) {
                float dmg = boss.scaleSkillDamage(CinderDragonConfig.get().wingClawSweepDamage);
                damageArc(level, boss.position(), forwardFromLockedYaw(), 22.5 * s, 90.0, dmg, 1.50, false, true);
            }
        }
    }

    // =========================================================================
    // 5. TAIL CRESCENT SLAM — Quật đuôi chùy liềm (đòn nặng 31, ưu tiên khi sau lưng!)
    // =========================================================================
    public static final class TailCrescentSlamGoal extends TimedDragonSkillGoal {
        public TailCrescentSlamGoal(CinderDragonBossEntity boss) {
            super(boss, SkillState.TAIL_CRESCENT_SLAM);
        }

        @Override
        protected boolean matchesCondition(LivingEntity target, double distSq) {
            double r = 27.0 * boss.getScaleRatio();
            if (distSq > r * r) return false;
            Vec3 fwd = new Vec3(-Math.sin(boss.yBodyRot * Mth.DEG_TO_RAD), 0, Math.cos(boss.yBodyRot * Mth.DEG_TO_RAD));
            Vec3 toT = target.position().subtract(boss.position());
            double horiz = Math.max(0.1, Math.hypot(toT.x, toT.z));
            double dot = (toT.x * fwd.x + toT.z * fwd.z) / horiz;
            return dot < 0.35 || boss.getRandom().nextFloat() < 0.25f;
        }

        @Override
        protected int baseCooldown() {
            return CinderDragonConfig.get().tailCrescentSlamCooldown;
        }

        @Override
        protected void onSkillStart(ServerLevel level, LivingEntity target) {
            Vec3 rear = forwardFromLockedYaw().scale(-1.0);
            level.playSound(null, boss.blockPosition(), ModSounds.CINDER_WING_FLAP, SoundSource.HOSTILE, 2.4f, 0.74f);
            ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_FAN_TELEGRAPH,
                    boss.position(), rear, 26.5f * boss.getScaleRatio(), boss.isEnragedVisual());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double currT) {
            DragonAttackTiming.HitWindow hw = this.timeline.hitWindows()[0];
            Vec3 rear = forwardFromLockedYaw().scale(-1.0);
            double s = boss.getScaleRatio();
            if (hw.crossedImpact(prevT, currT)) {
                level.playSound(null, boss.blockPosition(), ModSounds.CINDER_SLAM, SoundSource.HOSTILE, 3.0f, 0.74f);
                if (CinderDragonConfig.get().enableFootstepShake) {
                    ModNetworking.sendShakeNearby(level, boss.position(), 48.0 * s, 1.65f, 14);
                }
                ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_TAIL_ARC,
                        boss.position().add(0, 2.2, 0), rear, (float) (26.5 * s), boss.isEnragedVisual());
            }
            if (hw.isActive(prevT, currT)) {
                float dmg = boss.scaleSkillDamage(CinderDragonConfig.get().tailCrescentSlamDamage);
                damageArc(level, boss.position(), rear, 26.5 * s, 118.0, dmg, 1.40, true, true);
            }
        }
    }

    // =========================================================================
    // 6. WINGED STOMP — Bật cánh giậm đất 2 chân trước tạo sóng xung kích gọn (đòn nặng 31)
    // =========================================================================
    public static final class WingedStompGoal extends TimedDragonSkillGoal {
        public WingedStompGoal(CinderDragonBossEntity boss) {
            super(boss, SkillState.WINGED_STOMP);
        }

        @Override
        protected boolean matchesCondition(LivingEntity target, double distSq) {
            double r = 17.5 * boss.getScaleRatio();
            return distSq <= r * r;
        }

        @Override
        protected int baseCooldown() {
            return CinderDragonConfig.get().wingedStompCooldown;
        }

        @Override
        protected void onSkillStart(ServerLevel level, LivingEntity target) {
            level.playSound(null, boss.blockPosition(), ModSounds.CINDER_WING_FLAP, SoundSource.HOSTILE, 2.8f, 0.74f);
            ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_STOMP_RING,
                    boss.position(), Vec3.ZERO, 14.5f * boss.getScaleRatio(), boss.isEnragedVisual());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double currT) {
            DragonAttackTiming.HitWindow hw = this.timeline.hitWindows()[0];
            double s = boss.getScaleRatio();
            if (hw.crossedImpact(prevT, currT)) {
                float dmg = boss.scaleSkillDamage(CinderDragonConfig.get().wingedStompDamage);
                double rad = 14.5 * s;
                damageRadius(level, boss.position(), rad, dmg, 0.75, false, false);
                level.playSound(null, boss.blockPosition(), ModSounds.CINDER_SLAM, SoundSource.HOSTILE, 3.1f, 0.72f);
                if (CinderDragonConfig.get().enableFootstepShake) {
                    ModNetworking.sendShakeNearby(level, boss.position(), 56.0 * s, 2.1f, 16);
                }
                ShockwaveRingEntity.spawn(level, boss.position().add(0, 0.4, 0), (float) rad, CinderDragonBossEntity.COLOR_CINDER_ORANGE, 12);
                ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_STOMP_RING,
                        boss.position(), Vec3.ZERO, (float) rad, boss.isEnragedVisual());
            }
        }
    }

    // =========================================================================
    // 7. SKYFALL INFERNO (ĐỘC QUYỀN PHASE 2) — Bay 10-14 block đảo đường khè dải biển lửa -> đáp nặng
    // =========================================================================
    public static final class SkyfallInfernoGoal extends TimedDragonSkillGoal {
        private Vec3 startGround = Vec3.ZERO;
        private Vec3 strafeStart = Vec3.ZERO;
        private Vec3 strafeEnd = Vec3.ZERO;
        private Vec3 landPos = Vec3.ZERO;
        private int lastStrafeBucket = -1;
        private boolean landed = false;

        public SkyfallInfernoGoal(CinderDragonBossEntity boss) {
            super(boss, SkillState.SKYFALL_INFERNO);
        }

        @Override
        protected boolean matchesCondition(LivingEntity target, double distSq) {
            return boss.hasEnteredPhase2() && distSq >= 64.0 && distSq <= 1600.0;
        }

        @Override
        protected int baseCooldown() {
            return CinderDragonConfig.get().skyfallInfernoCooldown;
        }

        @Override
        protected void onSkillStart(ServerLevel level, LivingEntity target) {
            this.lastStrafeBucket = -1;
            this.landed = false;
            this.startGround = boss.position();
            Vec3 tPos = target.position();
            Vec3 fwd = tPos.subtract(this.startGround);
            Vec3 dir = fwd.horizontalDistanceSqr() > 1.0 ? new Vec3(fwd.x, 0, fwd.z).normalize() : forwardFromLockedYaw();

            // Độ cao bay Skyfall Inferno đồng bộ kích thước rồng cổ đại: 14–18 block (mặc định 16.0 block)
            double s = boss.getScaleRatio();
            double cruiseY = Math.min(level.getMaxBuildHeight() - 12, tPos.y + Mth.clamp(16.0 * s, 14.0, 18.0));
            for (int dy = 4; dy <= 18; dy += 2) {
                if (!level.getBlockState(boss.blockPosition().above(dy)).isAir()) {
                    cruiseY = Math.min(cruiseY, boss.getY() + Math.max(4.0, dy - 3.5));
                    break;
                }
            }
            this.strafeStart = new Vec3(tPos.x - dir.x * 20.0 * s, cruiseY, tPos.z - dir.z * 20.0 * s);
            this.strafeEnd = new Vec3(tPos.x + dir.x * 20.0 * s, cruiseY, tPos.z + dir.z * 20.0 * s);
            this.landPos = AbstractGeoMythicBoss.findSafeLandingPosition(
                    level, tPos.add(dir.scale(12.0 * s)), this.startGround, 12);

            boss.setFlying(true, false);
            boss.setThroatGlow(1.0f);
            level.playSound(null, boss.blockPosition(), ModSounds.CINDER_ROAR, SoundSource.HOSTILE, 3.2f, 0.74f);
            ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_SKYFALL_STRIP,
                    new Vec3(tPos.x, tPos.y + 0.2, tPos.z), dir, (float) (22.0 * s), true);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double currT) {
            double s = boss.getScaleRatio();
            if (currT < 20.0) {
                Vec3 toStart = this.strafeStart.subtract(boss.position());
                boss.setDeltaMovement(toStart.scale(0.16));
                boss.smoothRotateToward(this.strafeEnd.subtract(this.strafeStart), 12.0f, 1.25f);
                if (((int) currT) % 7 == 0 && ((int) prevT) != ((int) currT)) {
                    Vec3 midG = new Vec3(0.5 * (this.strafeStart.x + this.strafeEnd.x),
                            this.landPos.y + 0.2, 0.5 * (this.strafeStart.z + this.strafeEnd.z));
                    ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_SKYFALL_STRIP,
                            midG, this.strafeEnd.subtract(this.strafeStart).normalize(), (float) (22.0 * s), true);
                }
            } else if (currT <= 72.0) {
                double u = Mth.clamp((currT - 20.0) / 52.0, 0.0, 1.0);
                Vec3 desired = this.strafeStart.lerp(this.strafeEnd, u);
                boss.setDeltaMovement(desired.subtract(boss.position()).scale(0.45));
                boss.smoothRotateToward(this.strafeEnd.subtract(this.strafeStart), 12.0f, 1.25f);

                if (currT >= 28.0 && currT <= 68.0) {
                    int bucket = (int) Math.floor((currT - 28.0) / 5.0);
                    if (bucket > this.lastStrafeBucket) {
                        this.lastStrafeBucket = bucket;
                        Vec3 groundSpot = new Vec3(boss.getX(), this.landPos.y, boss.getZ());
                        float tickDmg = boss.scaleSkillDamage(CinderDragonConfig.get().skyfallInfernoTickDamage);
                        damageRadius(level, groundSpot, 6.4 * s, tickDmg, 0.15, true, false);
                        boss.addFirePatch(groundSpot, (float) (5.0 * s), 80, boss.scaleSkillDamage(CinderDragonConfig.get().firePatchTickDamage));
                        level.playSound(null, groundSpot.x, groundSpot.y, groundSpot.z, ModSounds.CINDER_BREATH, SoundSource.HOSTILE, 2.5f, 0.74f);
                        ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_BREATH_CONE,
                                boss.getMouthFlameWorldPos(), groundSpot.subtract(boss.getMouthFlameWorldPos()).normalize(), (float) (20.0 * s), true);
                    }
                }
            } else {
                if (!this.landed) {
                    Vec3 toLand = this.landPos.subtract(boss.position());
                    boss.setDeltaMovement(toLand.scale(0.32));
                    if (currT >= 80.0 || toLand.lengthSqr() < 4.0) {
                        this.landed = true;
                        boss.setPos(this.landPos.x, this.landPos.y, this.landPos.z);
                        boss.setDeltaMovement(Vec3.ZERO);
                        boss.setFlying(false, false);
                        float landDmg = boss.scaleSkillDamage(CinderDragonConfig.get().skyfallLandingDamage);
                        double landRad = 11.5 * s;
                        damageRadius(level, this.landPos, landRad, landDmg, 0.70, true, false);
                        level.playSound(null, boss.blockPosition(), ModSounds.CINDER_SLAM, SoundSource.HOSTILE, 3.2f, 0.72f);
                        if (CinderDragonConfig.get().enableFootstepShake) {
                            ModNetworking.sendShakeNearby(level, boss.position(), 64.0 * s, 2.3f, 18);
                        }
                        ShockwaveRingEntity.spawn(level, this.landPos.add(0, 0.4, 0), (float) landRad, CinderDragonBossEntity.COLOR_CRIMSON_FLAME, 12);
                    }
                }
            }
        }

        @Override
        protected void onSkillEnd(ServerLevel level) {
            if (boss.isFlying()) {
                Vec3 safe = AbstractGeoMythicBoss.findSafeLandingPosition(level, this.landPos, this.startGround, 8);
                boss.setPos(safe.x, safe.y, safe.z);
                boss.setDeltaMovement(Vec3.ZERO);
                boss.setFlying(false, false);
            }
        }
    }

    // =========================================================================
    // 8. SCORCHED EARTH BEAM (ĐỘC QUYỀN PHASE 2) — Gom lõi trắng-cam 1.3s -> tia lửa áp suất cao khóa tuyến
    // =========================================================================
    public static final class ScorchedEarthBeamGoal extends TimedDragonSkillGoal {
        private Vec3 lockedBeamDir = new Vec3(0, 0, 1);
        private int lastBeamBucket = -1;
        private boolean beamSpawned = false;

        public ScorchedEarthBeamGoal(CinderDragonBossEntity boss) {
            super(boss, SkillState.SCORCHED_EARTH_BEAM);
        }

        @Override
        protected boolean matchesCondition(LivingEntity target, double distSq) {
            double s = boss.getScaleRatio();
            double minR = 8.0 * s;
            double maxR = 46.0 * s;
            return boss.hasEnteredPhase2() && distSq >= minR * minR && distSq <= maxR * maxR;
        }

        @Override
        protected int baseCooldown() {
            return CinderDragonConfig.get().scorchedBeamCooldown;
        }

        @Override
        protected void onSkillStart(ServerLevel level, LivingEntity target) {
            this.lastBeamBucket = -1;
            this.beamSpawned = false;
            boss.setThroatGlow(1.0f);
            this.lockedBeamDir = target.position().add(0, 1.0, 0).subtract(boss.getMouthFlameWorldPos()).normalize();
            level.playSound(null, boss.blockPosition(), ModSounds.CINDER_BEAM_CHARGE, SoundSource.HOSTILE, 3.0f, 0.78f);
            ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_BEAM_CHARGE,
                    boss.getMouthFlameWorldPos(), this.lockedBeamDir, 42.0f * boss.getScaleRatio(), true);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double currT) {
            boss.setThroatGlow(1.0f);
            double s = boss.getScaleRatio();
            double len = 42.0 * s;
            Vec3 mouth = boss.getMouthFlameWorldPos();
            if (currT <= 22.0 && target != null && target.isAlive()) {
                this.lockedBeamDir = target.position().add(0, 1.0, 0).subtract(mouth).normalize();
            }
            if (currT < 26.0) {
                if (((int) currT) % 6 == 0 && ((int) prevT) != ((int) currT)) {
                    ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_BEAM_CHARGE,
                            mouth, this.lockedBeamDir, (float) len, true);
                }
                return;
            }
            if (currT <= 48.0) {
                if (!this.beamSpawned) {
                    this.beamSpawned = true;
                    level.playSound(null, boss.blockPosition(), ModSounds.CINDER_BEAM_FIRE, SoundSource.HOSTILE, 3.4f, 0.74f);
                    if (CinderDragonConfig.get().enableFootstepShake) {
                        ModNetworking.sendShakeNearby(level, boss.position(), 72.0 * s, 2.1f, 20);
                    }
                    Vec3 end = mouth.add(this.lockedBeamDir.scale(len));
                    LaserBeamEntity.spawn(level, boss, mouth, end, (float) (1.25 * s), CinderDragonBossEntity.COLOR_CINDER_ORANGE, 22, 0.0f, true);
                    LaserBeamEntity.spawn(level, boss, mouth, end, (float) (0.55 * s), CinderDragonBossEntity.COLOR_WHITE_HOT, 22, 0.0f, false);
                }
                int bucket = (int) Math.floor((currT - 26.0) / 4.0);
                if (bucket > this.lastBeamBucket) {
                    this.lastBeamBucket = bucket;
                    float tickDmg = boss.scaleSkillDamage(CinderDragonConfig.get().scorchedBeamTickDamage);
                    double beamRadius = 2.85 * s;
                    AABB box = new AABB(mouth, mouth.add(this.lockedBeamDir.scale(len))).inflate(beamRadius + 0.6);
                    for (Player p : level.getEntitiesOfClass(Player.class, box,
                            pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                        Vec3 rel = p.position().add(0, 1.0, 0).subtract(mouth);
                        double proj = rel.dot(this.lockedBeamDir);
                        if (proj < 0.0 || proj > len) continue;
                        Vec3 closest = mouth.add(this.lockedBeamDir.scale(proj));
                        if (closest.distanceTo(p.position().add(0, 1.0, 0)) <= beamRadius && hasLineOfSight(level, mouth, closest)) {
                            p.hurt(boss.damageSources().mobAttack(boss), tickDmg);
                            p.igniteForSeconds(5);
                        }
                    }
                    ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_BEAM_STREAM,
                            mouth, this.lockedBeamDir, (float) len, true);
                }
            }
        }
    }

    // =========================================================================
    // 9. CATACLYSM WING DIVE (ĐỘC QUYỀN PHASE 2) — Vọt lên trời, khóa vòng 1.2s, lao bổ nhào + 2 lớp sóng nhảy qua được!
    // =========================================================================
    public static final class CataclysmWingDiveGoal extends TimedDragonSkillGoal {
        private Vec3 startGround = Vec3.ZERO;
        private Vec3 hoverPoint = Vec3.ZERO;
        private Vec3 lockedImpact = Vec3.ZERO;
        private boolean impacted = false;
        private boolean wave2Fired = false;

        public CataclysmWingDiveGoal(CinderDragonBossEntity boss) {
            super(boss, SkillState.CATACLYSM_WING_DIVE);
        }

        @Override
        protected boolean matchesCondition(LivingEntity target, double distSq) {
            return boss.hasEnteredPhase2() && distSq >= 49.0 && distSq <= 1296.0;
        }

        @Override
        protected int baseCooldown() {
            return CinderDragonConfig.get().cataclysmDiveCooldown;
        }

        @Override
        protected void onSkillStart(ServerLevel level, LivingEntity target) {
            this.impacted = false;
            this.wave2Fired = false;
            this.startGround = boss.position();
            this.lockedImpact = AbstractGeoMythicBoss.findSafeLandingPosition(level, target.position(), this.startGround, 8);
            double s = boss.getScaleRatio();
            double peakY = Math.min(level.getMaxBuildHeight() - 10, this.startGround.y + Mth.clamp(16.5 * s, 14.0, 18.0));
            for (int dy = 4; dy <= 18; dy += 2) {
                if (!level.getBlockState(boss.blockPosition().above(dy)).isAir()) {
                    peakY = Math.min(peakY, boss.getY() + Math.max(4.0, dy - 3.5));
                    break;
                }
            }
            this.hoverPoint = new Vec3(this.startGround.x, peakY, this.startGround.z);
            boss.setFlying(true, true);
            level.playSound(null, boss.blockPosition(), ModSounds.CINDER_WING_FLAP, SoundSource.HOSTILE, 3.0f, 0.74f);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double currT) {
            double s = boss.getScaleRatio();
            if (currT < 22.0) {
                boss.setDeltaMovement(this.hoverPoint.subtract(boss.position()).scale(0.20));
            } else if (currT < 46.0) {
                boss.setDeltaMovement(this.hoverPoint.subtract(boss.position()).scale(0.15));
                if (currT <= 34.0 && target != null && target.isAlive()) {
                    this.lockedImpact = AbstractGeoMythicBoss.findSafeLandingPosition(level, target.position(), this.startGround, 8);
                }
                boss.smoothRotateToward(this.lockedImpact.subtract(boss.position()), 14.0f, 1.25f);
                if (((int) currT) % 6 == 0 && ((int) prevT) != ((int) currT)) {
                    ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_DIVE_TARGET,
                            this.lockedImpact, Vec3.ZERO, (float) (11.0 * s), true);
                }
            } else if (!this.impacted) {
                Vec3 toImp = this.lockedImpact.subtract(boss.position());
                boss.setDeltaMovement(toImp.scale(0.45));
                if (currT >= 52.0 || toImp.lengthSqr() < 4.5) {
                    this.impacted = true;
                    boss.setPos(this.lockedImpact.x, this.lockedImpact.y, this.lockedImpact.z);
                    boss.setDeltaMovement(Vec3.ZERO);
                    boss.setFlying(false, false);
                    float impactDmg = boss.scaleSkillDamage(CinderDragonConfig.get().cataclysmDiveDamage);
                    double impRad = 11.0 * s;
                    damageRadius(level, this.lockedImpact, impRad, impactDmg, 0.90, true, false);
                    level.playSound(null, boss.blockPosition(), ModSounds.CINDER_SLAM, SoundSource.HOSTILE, 3.4f, 0.72f);
                    if (CinderDragonConfig.get().enableFootstepShake) {
                        ModNetworking.sendShakeNearby(level, boss.position(), 76.0 * s, 2.7f, 22);
                    }
                    ShockwaveRingEntity.spawn(level, this.lockedImpact.add(0, 0.4, 0), (float) impRad, CinderDragonBossEntity.COLOR_CINDER_ORANGE, 12);
                    ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_DIVE_IMPACT,
                            this.lockedImpact, Vec3.ZERO, (float) impRad, true);
                }
            } else if (!this.wave2Fired && currT >= 58.0) {
                this.wave2Fired = true;
                float waveDmg = boss.scaleSkillDamage(CinderDragonConfig.get().cataclysmWaveDamage);
                double waveRad = 20.5 * s;
                damageRadius(level, this.lockedImpact, waveRad, waveDmg, 0.55, true, true);
                ShockwaveRingEntity.spawn(level, this.lockedImpact.add(0, 0.35, 0), (float) waveRad, CinderDragonBossEntity.COLOR_CRIMSON_FLAME, 14);
                ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_DIVE_IMPACT,
                        this.lockedImpact, Vec3.ZERO, (float) waveRad, true);
            }
        }

        @Override
        protected void onSkillEnd(ServerLevel level) {
            if (boss.isFlying()) {
                Vec3 safe = AbstractGeoMythicBoss.findSafeLandingPosition(level, this.lockedImpact, this.startGround, 8);
                boss.setPos(safe.x, safe.y, safe.z);
                boss.setDeltaMovement(Vec3.ZERO);
                boss.setFlying(false, false);
            }
        }
    }

    // =========================================================================
    // 10. ULTIMATE "ASHEN SUPERNOVA" (ĐỘC QUYỀN PHASE 2, HP < 25%, CD 60s)
    // =========================================================================
    public static final class AshenSupernovaGoal extends TimedDragonSkillGoal {
        private final List<Vec3> emberSpots = new ArrayList<>();
        private boolean burstFired = false;
        private boolean embers1Fired = false;
        private boolean embers2Fired = false;

        public AshenSupernovaGoal(CinderDragonBossEntity boss) {
            super(boss, SkillState.ASHEN_SUPERNOVA);
        }

        @Override
        protected boolean matchesCondition(LivingEntity target, double distSq) {
            if (!boss.hasEnteredPhase2()) return false;
            float hpRatio = boss.getHealth() / Math.max(1.0f, boss.getMaxHealth());
            return hpRatio <= CinderDragonConfig.get().ultimateHpThreshold && distSq <= 1600.0;
        }

        @Override
        protected int baseCooldown() {
            return CinderDragonConfig.get().ultimateCooldownTicks;
        }

        @Override
        protected void onSkillStart(ServerLevel level, LivingEntity target) {
            this.burstFired = false;
            this.embers1Fired = false;
            this.embers2Fired = false;
            this.emberSpots.clear();
            boss.setThroatGlow(1.0f);
            double s = boss.getScaleRatio();
            level.playSound(null, boss.blockPosition(), ModSounds.CINDER_ULTIMATE, SoundSource.HOSTILE, 3.6f, 0.74f);
            if (CinderDragonConfig.get().enableFootstepShake) {
                ModNetworking.sendShakeNearby(level, boss.position(), 92.0 * s, 1.5f, 40);
            }
            for (int i = 0; i < 6; i++) {
                double ang = (Math.PI * 2.0 * i) / 6.0 + boss.getRandom().nextDouble() * 0.4;
                double r = (12.0 + boss.getRandom().nextDouble() * 14.0) * s;
                this.emberSpots.add(boss.position().add(Math.cos(ang) * r, 0.0, Math.sin(ang) * r));
            }
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double currT) {
            boss.setThroatGlow(1.0f);
            double s = boss.getScaleRatio();
            if (currT < 44.0) {
                if (((int) currT) % 8 == 0 && ((int) prevT) != ((int) currT)) {
                    ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_SUPERNOVA_CHARGE,
                            boss.position().add(0, 3.6, 0), Vec3.ZERO, (float) (23.0 * s), true);
                }
                if (((int) currT) == 36 && ((int) prevT) < 36) {
                    for (Vec3 sp : this.emberSpots) {
                        ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_FIREBALL_TARGET,
                                sp, Vec3.ZERO, (float) (5.0 * s), true);
                    }
                }
            } else if (!this.burstFired && currT >= 46.0) {
                this.burstFired = true;
                float burstDmg = boss.scaleSkillDamage(CinderDragonConfig.get().ashenSupernovaDamage);
                double burstRad = 23.5 * s;
                damageRadius(level, boss.position(), burstRad, burstDmg, 1.0, true, false);
                level.playSound(null, boss.blockPosition(), ModSounds.CINDER_ROAR, SoundSource.HOSTILE, 3.8f, 0.72f);
                if (CinderDragonConfig.get().enableFootstepShake) {
                    ModNetworking.sendShakeNearby(level, boss.position(), 108.0 * s, 3.0f, 30);
                }
                ShockwaveRingEntity.spawn(level, boss.position().add(0, 0.8, 0), (float) burstRad, CinderDragonBossEntity.COLOR_CINDER_ORANGE, 18);
                ShockwaveRingEntity.spawn(level, boss.position().add(0, 2.0, 0), (float) (17.5 * s), CinderDragonBossEntity.COLOR_WHITE_HOT, 14);
                ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_SUPERNOVA_BURST,
                        boss.position().add(0, 3.2, 0), Vec3.ZERO, (float) burstRad, true);
            } else if (!this.embers1Fired && currT >= 56.0) {
                this.embers1Fired = true;
                detonateEmbers(level, 0, 3);
            } else if (!this.embers2Fired && currT >= 66.0) {
                this.embers2Fired = true;
                detonateEmbers(level, 3, 6);
            }
        }

        private void detonateEmbers(ServerLevel level, int from, int to) {
            float dmg = boss.scaleSkillDamage(CinderDragonConfig.get().ashenEmberDropDamage);
            double s = boss.getScaleRatio();
            for (int i = from; i < Math.min(to, this.emberSpots.size()); i++) {
                Vec3 sp = this.emberSpots.get(i);
                damageRadius(level, sp, 5.2 * s, dmg, 0.45, true, false);
                boss.addFirePatch(sp, (float) (4.2 * s), 75, boss.scaleSkillDamage(CinderDragonConfig.get().firePatchTickDamage));
                level.playSound(null, sp.x, sp.y, sp.z, ModSounds.CINDER_SLAM, SoundSource.HOSTILE, 2.2f, 0.78f);
                ModNetworking.sendCinderDragonVfxNearby(level, CinderDragonVfxPayload.KIND_FIREBALL_IMPACT,
                        sp, Vec3.ZERO, (float) (5.2 * s), true);
            }
        }
    }
}

