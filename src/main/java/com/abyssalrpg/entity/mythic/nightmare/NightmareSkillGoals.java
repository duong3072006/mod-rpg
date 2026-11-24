package com.abyssalrpg.entity.mythic.nightmare;

import com.abyssalrpg.config.NightmareKnightConfig;
import com.abyssalrpg.entity.mythic.AbstractGeoMythicBoss;
import com.abyssalrpg.entity.mythic.NightmareKnightBossEntity;
import com.abyssalrpg.entity.mythic.NightmareKnightBossEntity.SkillState;
import com.abyssalrpg.entity.mythic.nightmare.NightmareAttackTiming.HitWindow;
import com.abyssalrpg.entity.mythic.nightmare.NightmareAttackTiming.SkillTimeline;
import com.abyssalrpg.entity.vfx.EnergySlashEntity;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.network.NightmareVfxPayload;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bộ 14 kỹ năng (8 Phase 1 + 6 Phase 2) của Boss Nightmare Knight — Phiên bản v3:
 * 1. ĐỒNG BỘ 0-TICK VỚI ANIMATION: Mọi kỹ năng chạy hoàn toàn từ {@link NightmareAttackTiming}
 *    (Single Source of Truth); `animTick` tăng đúng bằng `animSpeed` đồng bộ với Client (`transitionLength = 0`),
 *    quét sát thương xuyên suốt cửa sổ active frames `[hitStartTick, hitEndTick]` có `Set<UUID>` chống double-hit.
 * 2. ÁP LỰC CHIẾN ĐẤU SOULSBORNE: Lunge bước lướt áp sát trong pha windup, dư chấn nứt đất, chuỗi follow-up,
 *    nhịp nghỉ ngắn và trừng phạt thả diều tầm xa.
 * 3. VFX SIÊU NHẸ (0 `ServerLevel.sendParticles` loop): Mọi hiệu ứng kích hoạt qua đúng 1 gói tin
 *    {@link NightmareVfxPayload} gọn nhẹ sang Client, tuân thủ `vfxQuality = MINIMAL` / `particleMultiplier = 0.20`.
 */
public final class NightmareSkillGoals {
    private NightmareSkillGoals() {}

    public static void registerAll(NightmareKnightBossEntity boss, GoalSelector selector) {
        selector.addGoal(1, new GreatswordSlashGoal(boss));
        selector.addGoal(1, new OverheadSmashGoal(boss));
        selector.addGoal(1, new ThrustGoal(boss));
        selector.addGoal(1, new ClawSwipeGoal(boss));
        selector.addGoal(1, new LeapSmashGoal(boss));
        selector.addGoal(1, new BoneSpikeEruptionGoal(boss));
        selector.addGoal(1, new SwordWaveGoal(boss));
        selector.addGoal(1, new BoneCageGoal(boss));

        selector.addGoal(1, new NightmareComboGoal(boss));
        selector.addGoal(1, new SpikeStormGoal(boss));
        selector.addGoal(1, new NightmareRealmGoal(boss));
        selector.addGoal(1, new SpinSlashGoal(boss));
        selector.addGoal(1, new FlameSwordWaveGoal(boss));
        selector.addGoal(1, new NightmareDescentGoal(boss));

        selector.addGoal(2, new NightmareSkillSelectorGoal(boss));
        selector.addGoal(3, new NightmareDirectChaseGoal(boss));
    }

    private abstract static class BaseNightmareSkillGoal extends Goal {
        protected final NightmareKnightBossEntity boss;
        protected final SkillState state;
        protected final SkillTimeline timeline;

        protected int rawTicks = 0;
        protected double prevAnimTick = 0.0;
        protected double animTick = 0.0;
        protected float animSpeed = 1.0f;
        protected final List<Set<UUID>> swingHitSets = new ArrayList<>();

        protected BaseNightmareSkillGoal(NightmareKnightBossEntity boss, SkillState state) {
            this.boss = boss;
            this.state = state;
            this.timeline = NightmareAttackTiming.forSkill(state);
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return boss.isAlive() && !boss.isRotationLocked() && boss.getSkillState() == state;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.rawTicks = 0;
            this.prevAnimTick = 0.0;
            this.animTick = 0.0;
            boolean p2 = boss.getSyncedPhase() >= 2 || boss.hasEnteredPhase2();
            this.animSpeed = NightmareAttackTiming.getAnimSpeed(this.state, p2);

            this.swingHitSets.clear();
            int winCount = Math.max(1, this.timeline.hitWindows().length);
            for (int i = 0; i < winCount; i++) {
                this.swingHitSets.add(new HashSet<>());
            }

            boss.getNavigation().stop();
            // Kích hoạt đồng bộ DataTracker + reset 0-tick blend trên Client ngay tại tick bắt đầu
            boss.beginSyncedActionAnimation(this.state, this.animSpeed);
            onSkillStart();
        }

        protected void onSkillStart() {}

        @Override
        public void stop() {
            boss.setHeadAnimationLocked(false);
            if (boss.getSkillState() == state) {
                boss.setSkillState(SkillState.IDLE);
            }
        }

        @Override
        public void tick() {
            if (!(boss.level() instanceof ServerLevel sl)) return;
            this.rawTicks++;
            this.prevAnimTick = this.animTick;
            this.animTick += this.animSpeed;
            onSkillTick(sl, boss.getTarget());
            if (this.animTick >= this.timeline.totalTicks() && boss.getSkillState() == this.state) {
                onSkillFinish(sl, boss.getTarget());
            }
        }

        protected abstract void onSkillTick(ServerLevel level, LivingEntity target);
        protected abstract void onSkillFinish(ServerLevel level, LivingEntity target);

        protected HitWindow window(int index) {
            return this.timeline.hitWindows()[index];
        }

        protected Set<UUID> hitSet(int index) {
            return this.swingHitSets.get(index);
        }

        protected boolean isPhase2() {
            return boss.getSyncedPhase() >= 2 || boss.hasEnteredPhase2();
        }

        protected void completePressuredRest() {
            int rest = isPhase2()
                    ? (8 + boss.getRandom().nextInt(7))   // Phase 2: 0.40s - 0.70s áp lực liên tục
                    : (16 + boss.getRandom().nextInt(9)); // Phase 1: 0.80s - 1.20s dứt khoát
            boss.finishSkill(rest);
        }

        protected void chainNextSkill(SkillState nextSkill, int shortRestTicks) {
            boss.recordUsedSkill(nextSkill);
            boss.finishSkill(shortRestTicks);
            if (shortRestTicks <= 0) {
                boss.setSkillState(nextSkill);
            }
        }

        protected void faceTarget(LivingEntity target, float baseDegPerTick) {
            if (target != null && target.isAlive()) {
                boss.smoothRotateToward(target.position().subtract(boss.position()),
                        baseDegPerTick, (float) NightmareKnightConfig.get().phase2TurnSpeedMultiplier);
            }
        }

        /** Bước lướt áp sát (Soulsborne lunge) trong pha windup để boss không chém vào khoảng không. */
        protected void applyWindupLunge(LivingEntity target, Vec3 dir, double speed, double minDistStop) {
            if (!boss.onGround()) return;
            if (target != null && target.isAlive()) {
                double d = Math.hypot(target.getX() - boss.getX(), target.getZ() - boss.getZ());
                if (d <= minDistStop) return;
            }
            double p2Boost = isPhase2() ? 1.18 : 1.0;
            boss.setDeltaMovement(dir.x * speed * p2Boost, boss.getDeltaMovement().y, dir.z * speed * p2Boost);
            boss.hurtMarked = true;
        }

        protected Vec3 horizontalForward(LivingEntity target) {
            Vec3 v = (target != null && target.isAlive())
                    ? target.position().subtract(boss.position())
                    : boss.getLookAngle();
            Vec3 h = new Vec3(v.x, 0.0, v.z);
            return h.lengthSqr() > 1e-4 ? h.normalize() : new Vec3(0, 0, 1);
        }

        protected static Vec3 rotateY(Vec3 v, double rad) {
            double c = Math.cos(rad), s = Math.sin(rad);
            return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
        }

        protected Vec3 groundAt(ServerLevel level, double x, double z, double hintY) {
            int ix = Mth.floor(x);
            int iz = Mth.floor(z);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
            if (Math.abs(y - hintY) > 10.0) {
                y = Mth.floor(hintY);
            }
            return new Vec3(x, y, z);
        }

        /** Gửi 1 gói tin cảnh báo đường thẳng gọn nhẹ sang Client (0 vòng lặp sendParticles trên server). */
        protected void spawnCompactLineTelegraph(ServerLevel level, Vec3 start, Vec3 dir, double length) {
            ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_LINE_TELEGRAPH,
                    start, dir, (float) length, boss.isEnragedVisual());
        }

        /** Gửi 1 gói tin cảnh báo cung quạt gọn nhẹ sang Client (0 vòng lặp sendParticles trên server). */
        protected void spawnCompactFanTelegraph(ServerLevel level, Vec3 origin, Vec3 forward,
                                                double range, double halfAngleDeg) {
            ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_FAN_TELEGRAPH,
                    origin, forward, (float) range, boss.isEnragedVisual());
        }

        /** Quét sát thương hình quạt xuyên suốt active hit window với `hitSet` chống đánh đúp. */
        protected void sweepFanWindow(ServerLevel level, int swingIdx, Vec3 origin, Vec3 forward, double range,
                                      double halfAngleDeg, float dmg, double knockUp, double knockBack) {
            HitWindow w = window(swingIdx);
            Set<UUID> hit = hitSet(swingIdx);
            double cosLimit = Math.cos(Math.toRadians(halfAngleDeg));
            AABB box = new AABB(origin.x - range, origin.y - 3.0, origin.z - range,
                    origin.x + range, origin.y + 9.0, origin.z + range);
            for (Player p : level.getEntitiesOfClass(Player.class, box,
                    pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                Vec3 diff = new Vec3(p.getX() - origin.x, 0.0, p.getZ() - origin.z);
                double dist = diff.length();
                if (dist > range) continue;
                if (dist > 1.5 && diff.normalize().dot(forward) < cosLimit) continue;
                if (hit.add(p.getUUID())) {
                    p.hurt(level.damageSources().mobAttack(boss), dmg);
                    Vec3 pushDir = dist > 0.2 ? diff.normalize() : forward;
                    p.setDeltaMovement(p.getDeltaMovement().add(pushDir.x * knockBack, knockUp, pushDir.z * knockBack));
                    p.hurtMarked = true;
                    boss.logAttackTiming(state.name(), swingIdx + 1, animTick, rawTicks, animSpeed,
                            w.startTick(), w.endTick(), w.impactTick(), "DAMAGE_APPLIED");
                }
            }
        }

        /** Quét sát thương vòng tròn xuyên suốt active hit window với `hitSet` chống đánh đúp. */
        protected void sweepRadiusWindow(ServerLevel level, int swingIdx, Vec3 center, double radius,
                                         double maxHeightDelta, float dmg, double knockUp, double knockBack) {
            HitWindow w = window(swingIdx);
            Set<UUID> hit = hitSet(swingIdx);
            AABB box = new AABB(center.x - radius, center.y - 2.5, center.z - radius,
                    center.x + radius, center.y + maxHeightDelta, center.z + radius);
            for (Player p : level.getEntitiesOfClass(Player.class, box,
                    pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                double dx = p.getX() - center.x;
                double dz = p.getZ() - center.z;
                double dist = Math.hypot(dx, dz);
                if (dist > radius) continue;
                if (Math.abs(p.getY() - center.y) > maxHeightDelta) continue;
                if (hit.add(p.getUUID())) {
                    p.hurt(level.damageSources().mobAttack(boss), dmg);
                    Vec3 push = dist > 0.2 ? new Vec3(dx / dist, 0.0, dz / dist) : Vec3.ZERO;
                    p.setDeltaMovement(p.getDeltaMovement().add(push.x * knockBack, knockUp, push.z * knockBack));
                    p.hurtMarked = true;
                    boss.logAttackTiming(state.name(), swingIdx + 1, animTick, rawTicks, animSpeed,
                            w.startTick(), w.endTick(), w.impactTick(), "DAMAGE_APPLIED");
                }
            }
        }
    }

    // =========================================================================
    // 1. GREATSWORD SLASH — Lunge áp sát trong windup -> Quét ngang active [18..21]
    // =========================================================================
    private static final class GreatswordSlashGoal extends BaseNightmareSkillGoal {
        private Vec3 lockedFwd = new Vec3(0, 0, 1);

        GreatswordSlashGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.GREATSWORD_SLASH);
        }

        @Override
        protected void onSkillStart() {
            this.lockedFwd = horizontalForward(boss.getTarget());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            HitWindow w = window(0);
            if (animTick < timeline.lockAimTick()) {
                faceTarget(target, 6.5f);
                this.lockedFwd = horizontalForward(target);
                // Bước lướt áp sát trong pha đầu windup
                if (animTick >= 4.0) {
                    applyWindupLunge(target, lockedFwd, 0.44, 5.5);
                }
            } else if (animTick < w.startTick()) {
                // Giữ đà nhẹ tiến vào tầm chém trước khoảnh khắc lưỡi kiếm quét
                applyWindupLunge(target, lockedFwd, 0.26, 4.5);
            }

            if (rawTicks == 2) {
                spawnCompactFanTelegraph(level, boss.position(), lockedFwd, 15.5, 80.0);
            }

            if (w.crossedImpact(prevAnimTick, animTick)) {
                boss.playScaledSound(level, ModSounds.NIGHTMARE_SLASH, 2.0f, 0.72f);
                boss.triggerScaledShake(level, 28.0, 1.8f, 12);
                boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                        w.startTick(), w.endTick(), w.impactTick(), "IMPACT");
                ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_SLASH_ARC,
                        boss.position(), lockedFwd, 15.0f, boss.isEnragedVisual());
            }
            if (w.isActive(prevAnimTick, animTick)) {
                float dmg = boss.scaledDamage(NightmareKnightConfig.get().greatswordSlashDamage);
                sweepFanWindow(level, 0, boss.position(), lockedFwd, 15.8, 84.0, dmg, 0.88, 1.32);
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.GREATSWORD_SLASH, NightmareKnightConfig.get().greatswordSlashCooldown);
            completePressuredRest();
        }
    }

    // =========================================================================
    // 2. OVERHEAD SMASH — Bước lướt + Bổ đại kiếm active [24..27] + Sóng nứt đất trễ [27..29]
    // =========================================================================
    private static final class OverheadSmashGoal extends BaseNightmareSkillGoal {
        private Vec3 lockedFwd = new Vec3(0, 0, 1);
        private Vec3 slamPos = Vec3.ZERO;

        OverheadSmashGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.OVERHEAD_SMASH);
        }

        @Override
        protected void onSkillStart() {
            this.lockedFwd = horizontalForward(boss.getTarget());
            this.slamPos = boss.position().add(lockedFwd.scale(10.0));
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            HitWindow wSlam = window(0);
            HitWindow wWave = window(1);

            if (animTick < timeline.lockAimTick()) {
                faceTarget(target, 5.5f);
                this.lockedFwd = horizontalForward(target);
                if (animTick >= 5.0) {
                    applyWindupLunge(target, lockedFwd, 0.42, 7.0);
                }
                this.slamPos = groundAt(level, boss.getX() + lockedFwd.x * 10.0, boss.getZ() + lockedFwd.z * 10.0, boss.getY());
            }

            if (rawTicks == 2 || rawTicks == 12) {
                spawnCompactFanTelegraph(level, slamPos, lockedFwd, 18.0, 48.0);
            }

            if (wSlam.crossedImpact(prevAnimTick, animTick)) {
                this.slamPos = groundAt(level, boss.getX() + lockedFwd.x * 10.0, boss.getZ() + lockedFwd.z * 10.0, boss.getY());
                boss.playScaledSound(level, ModSounds.NIGHTMARE_SLAM, 2.2f, 0.65f);
                boss.triggerScaledShake(level, 36.0, 2.4f, 16);
                ShockwaveRingEntity.spawn(level, slamPos, 16.0f,
                        boss.isEnragedVisual() ? NightmareKnightBossEntity.COLOR_ENRAGED_FLAME : NightmareKnightBossEntity.COLOR_NIGHTMARE_CRIMSON, 14);
                ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_SLAM_IMPACT,
                        slamPos, lockedFwd, 14.0f, boss.isEnragedVisual());
                boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                        wSlam.startTick(), wSlam.endTick(), wSlam.impactTick(), "IMPACT");
            }
            if (wSlam.isActive(prevAnimTick, animTick)) {
                NightmareKnightConfig cfg = NightmareKnightConfig.get();
                sweepRadiusWindow(level, 0, slamPos, 5.8, 4.5, boss.scaledDamage(cfg.overheadSmashDamage), 0.78, 0.95);
            }

            // Sóng nứt đất trễ 3 tick sau cú đập -> phạt né lùi thẳng, thưởng né ngang!
            if (wWave.crossedImpact(prevAnimTick, animTick)) {
                boss.logAttackTiming(state.name(), 2, animTick, rawTicks, animSpeed,
                        wWave.startTick(), wWave.endTick(), wWave.impactTick(), "IMPACT_WAVE");
            }
            if (wWave.isActive(prevAnimTick, animTick)) {
                NightmareKnightConfig cfg = NightmareKnightConfig.get();
                sweepFanWindow(level, 1, slamPos, lockedFwd, 19.5, 52.0, boss.scaledDamage(cfg.overheadShockwaveDamage), 0.55, 1.12);
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.OVERHEAD_SMASH, NightmareKnightConfig.get().overheadSmashCooldown);
            completePressuredRest();
        }
    }

    // =========================================================================
    // 3. THRUST — Đâm lao xuyên thẳng có gia tốc lướt, active [19..22]
    // =========================================================================
    private static final class ThrustGoal extends BaseNightmareSkillGoal {
        private Vec3 lockedFwd = new Vec3(0, 0, 1);

        ThrustGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.THRUST);
        }

        @Override
        protected void onSkillStart() {
            this.lockedFwd = horizontalForward(boss.getTarget());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            HitWindow w = window(0);
            if (animTick < timeline.lockAimTick()) {
                faceTarget(target, 6.5f);
                this.lockedFwd = horizontalForward(target);
            }
            if (rawTicks == 2 || rawTicks == 10) {
                spawnCompactLineTelegraph(level, boss.position(), lockedFwd, 24.0);
            }

            if (w.crossedImpact(prevAnimTick, animTick)) {
                // Bật lao mạnh tới trước khi đâm đại kiếm!
                boss.setDeltaMovement(lockedFwd.x * (isPhase2() ? 1.25 : 1.0), 0.08, lockedFwd.z * (isPhase2() ? 1.25 : 1.0));
                boss.hurtMarked = true;
                boss.playScaledSound(level, ModSounds.NIGHTMARE_THRUST, 2.1f, 0.78f);
                boss.triggerScaledShake(level, 30.0, 1.9f, 12);
                boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                        w.startTick(), w.endTick(), w.impactTick(), "IMPACT");
                ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_THRUST_STREAK,
                        boss.position(), lockedFwd, 24.0f, boss.isEnragedVisual());
            }
            if (w.isActive(prevAnimTick, animTick)) {
                float dmg = boss.scaledDamage(NightmareKnightConfig.get().thrustDamage);
                Set<UUID> hit = hitSet(0);
                for (double d = 3.0; d <= 24.5; d += 2.6) {
                    Vec3 p = boss.position().add(lockedFwd.scale(d));
                    AABB box = new AABB(p.x - 2.35, p.y - 1.0, p.z - 2.35, p.x + 2.35, p.y + 5.5, p.z + 2.35);
                    for (Player pl : level.getEntitiesOfClass(Player.class, box,
                            v -> v.isAlive() && !v.isCreative() && !v.isSpectator())) {
                        if (hit.add(pl.getUUID())) {
                            pl.hurt(level.damageSources().mobAttack(boss), dmg);
                            pl.setDeltaMovement(pl.getDeltaMovement().add(lockedFwd.x * 1.6, 0.45, lockedFwd.z * 1.6));
                            pl.hurtMarked = true;
                            boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                                    w.startTick(), w.endTick(), w.impactTick(), "DAMAGE_APPLIED");
                        }
                    }
                }
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.THRUST, NightmareKnightConfig.get().thrustCooldown);
            completePressuredRest();
        }
    }

    // =========================================================================
    // 4. CLAW SWIPE — 2 nhát cào nhanh bước lướt liên tiếp [14..16] & [24..26] + có thể nối Slash/Thrust
    // =========================================================================
    private static final class ClawSwipeGoal extends BaseNightmareSkillGoal {
        private Vec3 fwd1 = new Vec3(0, 0, 1);
        private Vec3 fwd2 = new Vec3(0, 0, 1);

        ClawSwipeGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.CLAW_SWIPE);
        }

        @Override
        protected void onSkillStart() {
            this.fwd1 = horizontalForward(boss.getTarget());
            this.fwd2 = this.fwd1;
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            HitWindow w1 = window(0);
            HitWindow w2 = window(1);

            if (animTick < 10.0) {
                faceTarget(target, 7.0f);
                this.fwd1 = horizontalForward(target);
                applyWindupLunge(target, fwd1, 0.40, 4.5);
                if (rawTicks == 2) {
                    spawnCompactFanTelegraph(level, boss.position(), fwd1, 11.2, 68.0);
                }
            } else if (animTick >= w1.endTick() && animTick < 21.0) {
                faceTarget(target, 8.0f);
                this.fwd2 = horizontalForward(target);
                applyWindupLunge(target, fwd2, 0.45, 4.5);
            }

            float dmg = boss.scaledDamage(NightmareKnightConfig.get().clawSwipeDamage);
            if (w1.crossedImpact(prevAnimTick, animTick)) {
                boss.playScaledSound(level, ModSounds.NIGHTMARE_CLAW, 1.8f, 0.85f);
                ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_CLAW_ARC,
                        boss.position(), fwd1, 11.5f, boss.isEnragedVisual());
                boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                        w1.startTick(), w1.endTick(), w1.impactTick(), "IMPACT");
            }
            if (w1.isActive(prevAnimTick, animTick)) {
                sweepFanWindow(level, 0, boss.position(), fwd1, 11.5, 70.0, dmg, 0.35, 0.78);
            }

            if (w2.crossedImpact(prevAnimTick, animTick)) {
                boss.playScaledSound(level, ModSounds.NIGHTMARE_CLAW, 1.85f, 1.00f);
                ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_CLAW_ARC,
                        boss.position(), fwd2, 11.8f, boss.isEnragedVisual());
                boss.logAttackTiming(state.name(), 2, animTick, rawTicks, animSpeed,
                        w2.startTick(), w2.endTick(), w2.impactTick(), "IMPACT");
            }
            if (w2.isActive(prevAnimTick, animTick)) {
                sweepFanWindow(level, 1, boss.position(), fwd2, 11.8, 72.0, dmg, 0.40, 0.85);
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.CLAW_SWIPE, NightmareKnightConfig.get().clawSwipeCooldown);
            // Tạo áp lực combo Soulsborne: nếu mục tiêu còn gần -> 45% (P1) / 65% (P2) nối thẳng Slash hoặc Thrust!
            if (target != null && target.isAlive() && boss.distanceTo(target) <= 18.0) {
                float chance = isPhase2() ? 0.65f : 0.45f;
                if (boss.getRandom().nextFloat() < chance) {
                    SkillState follow = (boss.distanceTo(target) <= 13.0 && boss.isSkillReady(SkillState.GREATSWORD_SLASH))
                            ? SkillState.GREATSWORD_SLASH
                            : (boss.isSkillReady(SkillState.THRUST) ? SkillState.THRUST : null);
                    if (follow != null && !boss.isBlockedByRepeatLimit(follow)) {
                        chainNextSkill(follow, 0);
                        return;
                    }
                }
            }
            completePressuredRest();
        }
    }

    // =========================================================================
    // 5. LEAP SMASH — Bật nhảy cánh quỷ ở tick 16, đáp & đập đất active [34..37]
    // =========================================================================
    private static final class LeapSmashGoal extends BaseNightmareSkillGoal {
        private static final int JUMP_TICK = 16;
        private Vec3 landingPos = Vec3.ZERO;
        private boolean circleSpawned = false;
        private boolean launched = false;

        LeapSmashGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.LEAP_SMASH);
        }

        @Override
        protected void onSkillStart() {
            this.circleSpawned = false;
            this.launched = false;
            LivingEntity t = boss.getTarget();
            this.landingPos = (t != null) ? t.position() : boss.position().add(horizontalForward(null).scale(14.0));
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            HitWindow w = window(0);
            if (animTick < timeline.lockAimTick()) {
                faceTarget(target, 6.5f);
                if (target != null) {
                    Vec3 clamped = target.position();
                    Vec3 diff = clamped.subtract(boss.position());
                    if (diff.horizontalDistance() > 30.0) {
                        clamped = boss.position().add(new Vec3(diff.x, 0, diff.z).normalize().scale(30.0));
                    }
                    this.landingPos = AbstractGeoMythicBoss.findSafeLandingPosition(level, clamped, boss.position(), 5);
                }
            }
            if (!circleSpawned && animTick >= timeline.lockAimTick()) {
                circleSpawned = true;
                int warnDur = Math.max(12, Math.round((w.impactTick() - (float) animTick) / animSpeed));
                MagicCircleEntity.spawn(level, boss, landingPos, 9.5f, warnDur,
                        NightmareKnightBossEntity.COLOR_NIGHTMARE_CRIMSON, 0.0f, false, false);
            }
            if (!launched && animTick >= JUMP_TICK) {
                launched = true;
                boss.playScaledSound(level, ModSounds.NIGHTMARE_LEAP, 2.0f, 0.72f);
                Vec3 delta = landingPos.subtract(boss.position());
                double airServerTicks = Math.max(10.0, (w.impactTick() - JUMP_TICK) / animSpeed);
                boss.setDeltaMovement(delta.x / airServerTicks, 1.38, delta.z / airServerTicks);
                boss.hurtMarked = true;
            } else if (launched && animTick < w.impactTick()) {
                Vec3 delta = landingPos.subtract(boss.position());
                double remServerTicks = Math.max(1.0, (w.impactTick() - animTick) / animSpeed);
                double midAir = (JUMP_TICK + w.impactTick()) * 0.5;
                double vy = (animTick < midAir) ? 0.55 : -1.25;
                boss.setDeltaMovement(delta.x / remServerTicks, vy, delta.z / remServerTicks);
                boss.hurtMarked = true;
            }

            if (w.crossedImpact(prevAnimTick, animTick)) {
                boss.teleportTo(landingPos.x, landingPos.y, landingPos.z);
                boss.setDeltaMovement(Vec3.ZERO);
                boss.snapSyncPrevAngles();
                boss.playScaledSound(level, ModSounds.NIGHTMARE_SLAM, 2.4f, 0.60f);
                boss.triggerScaledShake(level, 46.0, 2.8f, 20);
                ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_SLAM_IMPACT,
                        landingPos, Vec3.ZERO, 11.5f, boss.isEnragedVisual());
                ShockwaveRingEntity.spawn(level, landingPos, 20.0f,
                        boss.isEnragedVisual() ? NightmareKnightBossEntity.COLOR_ENRAGED_FLAME : NightmareKnightBossEntity.COLOR_NIGHTMARE_CRIMSON, 16);
                boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                        w.startTick(), w.endTick(), w.impactTick(), "IMPACT");
            }
            if (w.isActive(prevAnimTick, animTick)) {
                NightmareKnightConfig cfg = NightmareKnightConfig.get();
                // Đòn chính ở lõi + sóng xung kích vòng ngoài (chống double-hit qua hitSet riêng từng vùng)
                sweepRadiusWindow(level, 0, landingPos, 7.6, 4.5, boss.scaledDamage(cfg.leapImpactDamage), 0.95, 1.15);
                sweepRadiusWindow(level, 0, landingPos, 17.5, 3.2, boss.scaledDamage(cfg.leapWaveDamage), 0.55, 1.25);
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.LEAP_SMASH, NightmareKnightConfig.get().leapSmashCooldown);
            if (target != null && target.isAlive() && boss.distanceTo(target) <= 14.0) {
                boss.finishSkill(isPhase2() ? 6 : 10); // Ép nhịp ngay sau khi đáp đất
            } else {
                completePressuredRest();
            }
        }
    }

    // =========================================================================
    // 6. BONE SPIKE ERUPTION — Nện chuôi kiếm tạo hàng gai xương trồi đuổi [19..20]
    // =========================================================================
    private static final class BoneSpikeEruptionGoal extends BaseNightmareSkillGoal {
        private Vec3 lockedFwd = new Vec3(0, 0, 1);

        BoneSpikeEruptionGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.BONE_SPIKE_ERUPTION);
        }

        @Override
        protected void onSkillStart() {
            this.lockedFwd = horizontalForward(boss.getTarget());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            HitWindow w = window(0);
            if (animTick < timeline.lockAimTick()) {
                faceTarget(target, 6.0f);
                this.lockedFwd = horizontalForward(target);
            }
            if (rawTicks == 2 || rawTicks == 10) {
                spawnCompactLineTelegraph(level, boss.position(), lockedFwd, 30.0);
            }
            if (w.crossedImpact(prevAnimTick, animTick)) {
                float dmg = boss.scaledDamage(NightmareKnightConfig.get().boneSpikeDamage);
                boss.playScaledSound(level, ModSounds.NIGHTMARE_SPIKE, 2.0f, 0.72f);
                boss.triggerScaledShake(level, 32.0, 1.6f, 12);
                boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                        w.startTick(), w.endTick(), w.impactTick(), "IMPACT");
                int count = NightmareKnightConfig.get().reducedVfxMode ? 7 : 9;
                for (int i = 0; i < count; i++) {
                    double dist = 5.0 + i * 3.6;
                    Vec3 g = groundAt(level, boss.getX() + lockedFwd.x * dist, boss.getZ() + lockedFwd.z * dist, boss.getY());
                    int delay = 7 + i * 2;
                    NightmareBoneSpikeEntity.spawnEruption(level, boss, g, 5.4f + i * 0.25f, 1.20f,
                            delay, 20, dmg, boss.isEnragedVisual());
                }
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.BONE_SPIKE_ERUPTION, NightmareKnightConfig.get().boneSpikeEruptionCooldown);
            completePressuredRest();
        }
    }

    // =========================================================================
    // 7. SWORD WAVE — Chém phóng sóng kiếm tốc độ cao đúng khoảnh khắc lưỡi vung [18..19]
    // =========================================================================
    private static final class SwordWaveGoal extends BaseNightmareSkillGoal {
        private Vec3 lockedFwd = new Vec3(0, 0, 1);

        SwordWaveGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.SWORD_WAVE);
        }

        @Override
        protected void onSkillStart() {
            this.lockedFwd = horizontalForward(boss.getTarget());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            HitWindow w = window(0);
            if (animTick < timeline.lockAimTick()) {
                faceTarget(target, 6.0f);
                this.lockedFwd = horizontalForward(target);
            }
            if (rawTicks == 2 || rawTicks == 10) {
                spawnCompactLineTelegraph(level, boss.position(), lockedFwd, 26.0);
            }
            if (w.crossedImpact(prevAnimTick, animTick)) {
                float dmg = boss.scaledDamage(NightmareKnightConfig.get().swordWaveDamage);
                boss.playScaledSound(level, ModSounds.NIGHTMARE_SWORD_WAVE, 2.1f, 0.78f);
                Vec3 origin = boss.position().add(lockedFwd.scale(6.0)).add(0.0, 0.85, 0.0);
                EnergySlashEntity.spawn(level, boss, origin, lockedFwd, 1.48f, 7.5f, 0.0f,
                        NightmareKnightBossEntity.COLOR_NIGHTMARE_CRIMSON, dmg, false);
                boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                        w.startTick(), w.endTick(), w.impactTick(), "IMPACT");
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.SWORD_WAVE, NightmareKnightConfig.get().swordWaveCooldown);
            completePressuredRest();
        }
    }

    // =========================================================================
    // 8. BONE CAGE — Vòng 6-8 trụ xương phá được mọc ở tick 20, sau đó ép nhịp áp sát
    // =========================================================================
    private static final class BoneCageGoal extends BaseNightmareSkillGoal {
        private Vec3 cageCenter = Vec3.ZERO;

        BoneCageGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.BONE_CAGE);
        }

        @Override
        protected void onSkillStart() {
            LivingEntity t = boss.getTarget();
            this.cageCenter = (t != null) ? t.position() : boss.position().add(horizontalForward(null).scale(14.0));
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            HitWindow w = window(0);
            if (animTick < timeline.lockAimTick()) {
                faceTarget(target, 5.5f);
                if (target != null) {
                    this.cageCenter = target.position();
                }
            }
            if (rawTicks == 2 || rawTicks == 11) {
                ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_CAGE_TELEGRAPH,
                        cageCenter, Vec3.ZERO, 3.4f, boss.isEnragedVisual());
            }
            if (w.crossedImpact(prevAnimTick, animTick)) {
                float dmg = boss.scaledDamage(NightmareKnightConfig.get().boneCageEruptDamage);
                boss.playScaledSound(level, ModSounds.NIGHTMARE_SPIKE, 2.0f, 0.62f);
                boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                        w.startTick(), w.endTick(), w.impactTick(), "IMPACT");
                int pillars = NightmareKnightConfig.get().reducedVfxMode ? 6 : 8;
                for (int i = 0; i < pillars; i++) {
                    double ang = Math.PI * 2.0 * i / pillars;
                    Vec3 g = groundAt(level, cageCenter.x + Math.cos(ang) * 3.4, cageCenter.z + Math.sin(ang) * 3.4, cageCenter.y);
                    NightmareBoneSpikeEntity.spawnCagePillar(level, boss, g, 4.4f, 10, 85,
                            dmg, 12.0f, boss.isEnragedVisual());
                }
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.BONE_CAGE, NightmareKnightConfig.get().boneCageCooldown);
            boss.finishSkill(isPhase2() ? 6 : 10);
        }
    }

    // =========================================================================
    // 9. NIGHTMARE COMBO (Phase 2) — 4 đòn liên hoàn lunge áp sát [16..18, 30..32, 44..46, 60..63]
    // =========================================================================
    private static final class NightmareComboGoal extends BaseNightmareSkillGoal {
        private Vec3 strikeFwd = new Vec3(0, 0, 1);

        NightmareComboGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.NIGHTMARE_COMBO);
        }

        @Override
        protected void onSkillStart() {
            this.strikeFwd = horizontalForward(boss.getTarget());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            faceTarget(target, 8.0f);
            this.strikeFwd = horizontalForward(target);

            for (int idx = 0; idx < timeline.hitWindows().length; idx++) {
                HitWindow w = window(idx);
                boolean finisher = (idx == timeline.hitWindows().length - 1);

                // Bước lướt áp sát ngay trước mỗi nhát chém
                if (animTick >= w.startTick() - 6.0 && animTick < w.startTick()) {
                    applyWindupLunge(target, strikeFwd, finisher ? 0.50 : 0.42, 4.8);
                }
                if (prevAnimTick < w.startTick() - 5.0 && animTick >= w.startTick() - 5.0) {
                    spawnCompactFanTelegraph(level, boss.position(), strikeFwd, finisher ? 17.0 : 14.8, finisher ? 92.0 : 72.0);
                }

                if (w.crossedImpact(prevAnimTick, animTick)) {
                    boss.playScaledSound(level, finisher ? ModSounds.NIGHTMARE_SLAM : ModSounds.NIGHTMARE_SLASH,
                            2.1f, 0.72f + idx * 0.08f);
                    boss.triggerScaledShake(level, 30.0, finisher ? 2.4f : 1.3f, finisher ? 14 : 8);
                    ModNetworking.sendNightmareVfxNearby(level,
                            finisher ? NightmareVfxPayload.KIND_SLAM_IMPACT : NightmareVfxPayload.KIND_SLASH_ARC,
                            boss.position(), strikeFwd, finisher ? 16.0f : 14.5f, true);
                    if (finisher) {
                        ShockwaveRingEntity.spawn(level, boss.position().add(strikeFwd.scale(8.0)), 15.0f,
                                NightmareKnightBossEntity.COLOR_ENRAGED_FLAME, 12);
                    }
                    boss.logAttackTiming(state.name(), idx + 1, animTick, rawTicks, animSpeed,
                            w.startTick(), w.endTick(), w.impactTick(), "IMPACT");
                }
                if (w.isActive(prevAnimTick, animTick)) {
                    NightmareKnightConfig cfg = NightmareKnightConfig.get();
                    float dmg = boss.scaledDamage(cfg.nightmareComboHitDamage) * (finisher ? 1.35f : 1.0f);
                    sweepFanWindow(level, idx, boss.position(), strikeFwd, finisher ? 17.2 : 14.8,
                            finisher ? 95.0 : 74.0, dmg, finisher ? 0.95 : 0.35, finisher ? 1.45 : 0.78);
                }
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.NIGHTMARE_COMBO, NightmareKnightConfig.get().nightmareComboCooldown);
            completePressuredRest();
        }
    }

    // =========================================================================
    // 10. SPIKE STORM (Phase 2) — 3 đợt × 3 gai xương bám theo vị trí mục tiêu [16, 30, 44] (giảm lag 70%!)
    // =========================================================================
    private static final class SpikeStormGoal extends BaseNightmareSkillGoal {
        SpikeStormGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.SPIKE_STORM);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            faceTarget(target, 4.5f);
            for (int idx = 0; idx < timeline.hitWindows().length; idx++) {
                HitWindow w = window(idx);
                if (w.crossedImpact(prevAnimTick, animTick)) {
                    Vec3 center = (target != null && target.isAlive()) ? target.position() : boss.position();
                    float dmg = boss.scaledDamage(NightmareKnightConfig.get().spikeStormDamage);
                    boss.playScaledSound(level, ModSounds.NIGHTMARE_SPIKE, 2.0f, 0.68f + idx * 0.06f);
                    boss.logAttackTiming(state.name(), idx + 1, animTick, rawTicks, animSpeed,
                            w.startTick(), w.endTick(), w.impactTick(), "IMPACT");

                    int perWave = NightmareKnightConfig.get().reducedVfxMode ? 3 : 5;
                    for (int i = 0; i < perWave; i++) {
                        double ang = (Math.PI * 2.0 * i / perWave) + level.random.nextDouble() * 0.8;
                        double r = (i == 0) ? 0.4 : (3.6 + level.random.nextDouble() * 6.5);
                        Vec3 g = groundAt(level, center.x + Math.cos(ang) * r, center.z + Math.sin(ang) * r, center.y);
                        // Chỉ spawn 1 MagicCircle trung tâm đợt để cảnh báo rõ mà không tạo 15 entity VFX gây lag!
                        if (i == 0) {
                            MagicCircleEntity.spawn(level, boss, g, 4.2f, 15,
                                    NightmareKnightBossEntity.COLOR_ENRAGED_FLAME, 0.0f, false, false);
                        }
                        NightmareBoneSpikeEntity.spawnEruption(level, boss, g, 6.0f, 1.40f, 14, 18, dmg, true);
                    }
                }
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.SPIKE_STORM, NightmareKnightConfig.get().spikeStormCooldown);
            completePressuredRest();
        }
    }

    // =========================================================================
    // 11. NIGHTMARE REALM (Phase 2) — Dang rộng cánh quỷ kích hoạt vùng ác mộng ở tick 20
    // =========================================================================
    private static final class NightmareRealmGoal extends BaseNightmareSkillGoal {
        NightmareRealmGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.NIGHTMARE_REALM);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            faceTarget(target, 4.0f);
            HitWindow w = window(0);
            if (w.crossedImpact(prevAnimTick, animTick)) {
                boss.activateNightmareRealm(220);
                boss.playScaledSound(level, ModSounds.NIGHTMARE_REALM, 2.4f, 0.65f);
                boss.triggerScaledShake(level, 42.0, 2.0f, 18);
                ShockwaveRingEntity.spawn(level, boss.position(),
                        (float) Math.min(36.0, NightmareKnightConfig.get().nightmareRealmRadius),
                        NightmareKnightBossEntity.COLOR_OBSIDIAN_BLOOD, 20);
                boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                        w.startTick(), w.endTick(), w.impactTick(), "IMPACT");
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.NIGHTMARE_REALM, NightmareKnightConfig.get().nightmareRealmCooldown);
            completePressuredRest();
        }
    }

    // =========================================================================
    // 12. SPIN SLASH (Phase 2) — Xoáy chém 360° lướt đuổi mục tiêu, 2 vòng active [16..19] & [24..27]
    // =========================================================================
    private static final class SpinSlashGoal extends BaseNightmareSkillGoal {
        private boolean circleSpawned = false;

        SpinSlashGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.SPIN_SLASH);
        }

        @Override
        protected void onSkillStart() {
            this.circleSpawned = false;
            boss.setHeadAnimationLocked(true);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            HitWindow w1 = window(0);
            HitWindow w2 = window(1);

            if (!circleSpawned) {
                circleSpawned = true;
                int warnDur = Math.max(10, Math.round(w1.startTick() / animSpeed));
                MagicCircleEntity.spawn(level, boss, boss.position(), 15.2f, warnDur,
                        NightmareKnightBossEntity.COLOR_ENRAGED_FLAME, 0.0f, false, false);
            }

            // Lướt đuổi nhẹ về phía mục tiêu trong suốt pha xoáy (tạo áp lực chạy thoát/nhảy né)
            if (animTick >= w1.startTick() - 2.0 && animTick <= w2.endTick() && target != null && target.isAlive()) {
                Vec3 dir = horizontalForward(target);
                applyWindupLunge(target, dir, 0.36, 3.5);
            }

            float dmg = boss.scaledDamage(NightmareKnightConfig.get().spinSlashDamage);
            for (int idx = 0; idx < 2; idx++) {
                HitWindow w = window(idx);
                if (w.crossedImpact(prevAnimTick, animTick)) {
                    boss.playScaledSound(level, ModSounds.NIGHTMARE_SLASH, 2.2f, 0.68f + idx * 0.08f);
                    boss.triggerScaledShake(level, 34.0, 2.0f, 10);
                    ShockwaveRingEntity.spawn(level, boss.position().add(0, 1.5, 0), 16.5f,
                            NightmareKnightBossEntity.COLOR_ENRAGED_FLAME, 10);
                    boss.logAttackTiming(state.name(), idx + 1, animTick, rawTicks, animSpeed,
                            w.startTick(), w.endTick(), w.impactTick(), "IMPACT");
                }
                if (w.isActive(prevAnimTick, animTick)) {
                    sweepRadiusWindow(level, idx, boss.position(), 15.8, 3.2, dmg, 0.65, 1.35);
                }
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.SPIN_SLASH, NightmareKnightConfig.get().spinSlashCooldown);
            completePressuredRest();
        }
    }

    // =========================================================================
    // 13. FLAME SWORD WAVE (Phase 2) — Khớp 100% anim `sword_wave` ở tick 18 (hết lệch 10 tick của v1!)
    // =========================================================================
    private static final class FlameSwordWaveGoal extends BaseNightmareSkillGoal {
        private Vec3 lockedFwd = new Vec3(0, 0, 1);

        FlameSwordWaveGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.FLAME_SWORD_WAVE);
        }

        @Override
        protected void onSkillStart() {
            this.lockedFwd = horizontalForward(boss.getTarget());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            HitWindow w = window(0);
            if (animTick < timeline.lockAimTick()) {
                faceTarget(target, 6.5f);
                this.lockedFwd = horizontalForward(target);
            }
            if (rawTicks == 2 || rawTicks == 10) {
                spawnCompactLineTelegraph(level, boss.position(), lockedFwd, 30.0);
            }
            if (w.crossedImpact(prevAnimTick, animTick)) {
                NightmareKnightConfig cfg = NightmareKnightConfig.get();
                float waveDmg = boss.scaledDamage(cfg.flameSwordWaveDamage);
                float trailDmg = boss.scaledDamage(cfg.flameTrailTickDamage);
                boss.playScaledSound(level, ModSounds.NIGHTMARE_SWORD_WAVE, 2.3f, 0.68f);
                boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                        w.startTick(), w.endTick(), w.impactTick(), "IMPACT");

                for (int i = -1; i <= 1; i++) {
                    Vec3 dir = rotateY(lockedFwd, Math.toRadians(i * 14.0));
                    Vec3 origin = boss.position().add(dir.scale(6.0)).add(0.0, 0.9, 0.0);
                    EnergySlashEntity.spawn(level, boss, origin, dir, 1.55f, 8.5f, i * 10.0f,
                            NightmareKnightBossEntity.COLOR_ENRAGED_FLAME, waveDmg, true);
                }

                // Tối ưu vệt lửa: 4 cụm lớn cách đều thay vì 6 cụm dày -> nhẹ hơn nhưng phủ đúng đường chém
                int patches = cfg.reducedVfxMode ? 4 : 6;
                for (int k = 1; k <= patches; k++) {
                    Vec3 g = groundAt(level, boss.getX() + lockedFwd.x * (5.5 + k * 5.2),
                            boss.getZ() + lockedFwd.z * (5.5 + k * 5.2), boss.getY());
                    boss.addFlameTrailPatch(g, 2.4f, trailDmg, 65);
                }
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.FLAME_SWORD_WAVE, NightmareKnightConfig.get().flameSwordWaveCooldown);
            completePressuredRest();
        }
    }

    // =========================================================================
    // 14. ULTIMATE "NIGHTMARE DESCENT" (Phase 2, HP < 20%, 60s CD) — 3 đợt sóng đúng khung [48, 58, 68]
    // =========================================================================
    private static final class NightmareDescentGoal extends BaseNightmareSkillGoal {
        NightmareDescentGoal(NightmareKnightBossEntity boss) {
            super(boss, SkillState.NIGHTMARE_DESCENT);
        }

        @Override
        protected void onSkillStart() {
            boss.setHeadAnimationLocked(true);
            boss.setDeltaMovement(Vec3.ZERO);
            if (boss.level() instanceof ServerLevel sl) {
                boss.playScaledSound(sl, ModSounds.NIGHTMARE_ULTIMATE, 2.6f, 0.62f);
                MagicCircleEntity.spawn(sl, boss, boss.position(), 24.0f, window(0).impactTick(),
                        NightmareKnightBossEntity.COLOR_NIGHTMARE_CRIMSON, 0.0f, false, false);
            }
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target) {
            boss.setDeltaMovement(Vec3.ZERO);
            NightmareKnightConfig cfg = NightmareKnightConfig.get();
            HitWindow w0 = window(0);
            HitWindow w1 = window(1);
            HitWindow w2 = window(2);

            if (w0.crossedImpact(prevAnimTick, animTick)) {
                boss.playScaledSound(level, ModSounds.NIGHTMARE_SLAM, 2.8f, 0.52f);
                boss.triggerScaledShake(level, 64.0, 3.5f, 32);
                ShockwaveRingEntity.spawn(level, boss.position(), 26.0f, NightmareKnightBossEntity.COLOR_NIGHTMARE_CRIMSON, 16);
                ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_SLAM_IMPACT,
                        boss.position(), Vec3.ZERO, 22.0f, true);
                boss.logAttackTiming(state.name(), 1, animTick, rawTicks, animSpeed,
                        w0.startTick(), w0.endTick(), w0.impactTick(), "IMPACT");

                float spikeDmg = boss.scaledDamage(cfg.ultimateWaveDamage);
                int spikeCount = cfg.reducedVfxMode ? 8 : 12;
                for (int i = 0; i < spikeCount; i++) {
                    double ang = Math.PI * 2.0 * i / spikeCount;
                    double dist = (i % 2 == 0) ? 10.0 : 17.5;
                    Vec3 g = groundAt(level, boss.getX() + Math.cos(ang) * dist, boss.getZ() + Math.sin(ang) * dist, boss.getY());
                    NightmareBoneSpikeEntity.spawnEruption(level, boss, g, 7.0f, 1.55f, 6 + (i % 2) * 8, 20, spikeDmg, true);
                }
            }
            if (w0.isActive(prevAnimTick, animTick)) {
                sweepRadiusWindow(level, 0, boss.position(), 18.0, 7.0, boss.scaledDamage(cfg.ultimateBurstDamage), 1.1, 1.5);
            }

            if (w1.crossedImpact(prevAnimTick, animTick)) {
                boss.playScaledSound(level, ModSounds.NIGHTMARE_SLAM, 2.6f, 0.58f);
                ShockwaveRingEntity.spawn(level, boss.position(), 34.0f, NightmareKnightBossEntity.COLOR_ENRAGED_FLAME, 18);
                boss.logAttackTiming(state.name(), 2, animTick, rawTicks, animSpeed,
                        w1.startTick(), w1.endTick(), w1.impactTick(), "IMPACT");
            }
            if (w1.isActive(prevAnimTick, animTick)) {
                sweepRadiusWindow(level, 1, boss.position(), 26.0, 4.5, boss.scaledDamage(cfg.ultimateWaveDamage), 0.75, 1.35);
            }

            if (w2.crossedImpact(prevAnimTick, animTick)) {
                boss.playScaledSound(level, ModSounds.NIGHTMARE_SLAM, 2.6f, 0.64f);
                ShockwaveRingEntity.spawn(level, boss.position(), 42.0f, NightmareKnightBossEntity.COLOR_NIGHTMARE_CRIMSON, 20);
                boss.logAttackTiming(state.name(), 3, animTick, rawTicks, animSpeed,
                        w2.startTick(), w2.endTick(), w2.impactTick(), "IMPACT");
            }
            if (w2.isActive(prevAnimTick, animTick)) {
                sweepRadiusWindow(level, 2, boss.position(), 34.0, 4.0, boss.scaledDamage(cfg.ultimateWaveDamage), 0.70, 1.25);
            }
        }

        @Override
        protected void onSkillFinish(ServerLevel level, LivingEntity target) {
            boss.setSkillCooldown(SkillState.NIGHTMARE_DESCENT, NightmareKnightConfig.get().ultimateCooldownTicks);
            completePressuredRest();
        }
    }

    // =========================================================================
    // BỘ CHỌN KỸ NĂNG CHỦ ĐỘNG (PHẠT THẢ DIỀU TẦM XA + ÁP LỰC CẬN CHIẾN)
    // =========================================================================
    private static final class NightmareSkillSelectorGoal extends Goal {
        private final NightmareKnightBossEntity boss;
        private record Candidate(SkillState state, int weight) {}

        NightmareSkillSelectorGoal(NightmareKnightBossEntity boss) {
            this.boss = boss;
        }

        @Override
        public boolean canUse() {
            if (!boss.isAlive() || boss.isRotationLocked()) return false;
            if (boss.getSkillState() != SkillState.IDLE) return false;
            if (boss.getGlobalSkillRestTicks() > 0) return false;
            LivingEntity t = boss.getTarget();
            return t != null && t.isAlive() && boss.distanceTo(t) <= NightmareKnightConfig.get().detectionRadius;
        }

        @Override
        public void start() {
            LivingEntity target = boss.getTarget();
            if (target == null) return;
            double dist = boss.distanceTo(target);
            boolean p2 = boss.getSyncedPhase() >= 2 || boss.hasEnteredPhase2();
            NightmareKnightConfig cfg = NightmareKnightConfig.get();

            if (p2 && (boss.getHealth() / Math.max(1.0f, boss.getMaxHealth())) < cfg.ultimateHpThreshold
                    && boss.isSkillReady(SkillState.NIGHTMARE_DESCENT) && dist <= 36.0) {
                boss.recordUsedSkill(SkillState.NIGHTMARE_DESCENT);
                boss.setSkillState(SkillState.NIGHTMARE_DESCENT);
                return;
            }

            // Phạt mạnh người chơi thả diều ở tầm 16-36 block bằng Leap Smash / Thrust / Flame Wave / Bone Spike
            boolean kitingRange = dist > 16.0;
            List<Candidate> pool = new ArrayList<>();
            if (dist <= 12.0) addIfReady(pool, SkillState.CLAW_SWIPE, 32);
            if (dist <= 16.5) addIfReady(pool, SkillState.GREATSWORD_SLASH, 36);
            if (dist >= 5.5 && dist <= 21.0) addIfReady(pool, SkillState.OVERHEAD_SMASH, 32);
            if (dist >= 7.0 && dist <= 26.0) addIfReady(pool, SkillState.THRUST, kitingRange ? 40 : 30);
            if (dist >= 11.0 && dist <= 34.0) addIfReady(pool, SkillState.LEAP_SMASH, kitingRange ? 44 : 24);
            if (dist >= 8.0 && dist <= 36.0) addIfReady(pool, SkillState.BONE_SPIKE_ERUPTION, kitingRange ? 34 : 22);
            if (dist >= 10.0 && dist <= 42.0) addIfReady(pool, SkillState.SWORD_WAVE, p2 ? 12 : (kitingRange ? 36 : 24));
            if (dist >= 7.0 && dist <= 30.0) addIfReady(pool, SkillState.BONE_CAGE, 20);

            if (p2) {
                if (dist <= 18.0) addIfReady(pool, SkillState.NIGHTMARE_COMBO, 42);
                if (dist <= 17.0) addIfReady(pool, SkillState.SPIN_SLASH, 38);
                if (dist >= 8.0 && dist <= 40.0) addIfReady(pool, SkillState.SPIKE_STORM, kitingRange ? 38 : 30);
                if (dist >= 10.0 && dist <= 44.0) addIfReady(pool, SkillState.FLAME_SWORD_WAVE, kitingRange ? 44 : 32);
                if (!boss.isRealmActive() && dist <= 40.0) addIfReady(pool, SkillState.NIGHTMARE_REALM, 28);
            }

            if (pool.isEmpty()) return;

            int totalWeight = 0;
            for (Candidate c : pool) totalWeight += c.weight;
            int roll = boss.getRandom().nextInt(totalWeight);
            SkillState chosen = pool.get(0).state;
            for (Candidate c : pool) {
                roll -= c.weight;
                if (roll < 0) {
                    chosen = c.state;
                    break;
                }
            }
            boss.recordUsedSkill(chosen);
            boss.setSkillState(chosen);
        }

        private void addIfReady(List<Candidate> pool, SkillState st, int weight) {
            if (boss.isSkillReady(st) && !boss.isBlockedByRepeatLimit(st)) {
                pool.add(new Candidate(st, weight));
            }
        }
    }

    // =========================================================================
    // TRUY ĐUỔI CHỦ ĐỘNG (ÉP KHOẢNG CÁCH LIÊN TỤC KHÔNG ĐỨNG NHÌN)
    // =========================================================================
    private static final class NightmareDirectChaseGoal extends Goal {
        private final NightmareKnightBossEntity boss;

        NightmareDirectChaseGoal(NightmareKnightBossEntity boss) {
            this.boss = boss;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!boss.isAlive() || boss.isRotationLocked()) return false;
            if (boss.getSkillState() != SkillState.IDLE) return false;
            LivingEntity t = boss.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = boss.getTarget();
            if (target == null || !target.isAlive()) return;
            double dist = boss.distanceTo(target);
            boss.smoothRotateToward(target.position().subtract(boss.position()),
                    6.5f, (float) NightmareKnightConfig.get().phase2TurnSpeedMultiplier);

            boolean p2 = boss.getSyncedPhase() >= 2 || boss.hasEnteredPhase2();
            if (dist > 6.5) {
                double spdMult = p2 ? 1.32 : 1.22;
                boss.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), spdMult);
            }
        }
    }
}
