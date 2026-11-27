package com.abyssalrpg.entity.mythic.cerberus;

import com.abyssalrpg.config.CerberusConfig;
import com.abyssalrpg.entity.mythic.AbstractGeoMythicBoss;
import com.abyssalrpg.entity.mythic.CerberusBossEntity;
import com.abyssalrpg.entity.mythic.CerberusBossEntity.SkillState;
import com.abyssalrpg.entity.mythic.cerberus.CerberusAttackTiming.HitWindow;
import com.abyssalrpg.entity.mythic.cerberus.CerberusAttackTiming.SkillTimeline;
import com.abyssalrpg.entity.mythic.cerberus.CerberusGroupManager.EncounterGroup;
import com.abyssalrpg.entity.vfx.AbyssalOrbEntity;
import com.abyssalrpg.entity.vfx.EnergySlashEntity;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.CerberusVfxPayload;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Toàn bộ AI Goals cho Boss Cerberus v4 (Phase 1 TRIPLE + 3 vai trò Phase 2 + Combo nhóm Pack Hunt + Flank/Strafe):
 * - Đồng bộ tuyệt đối 1:1 (0 tick drift) với {@link CerberusAttackTiming} và `cerberus.animation.json`.
 * - 0 vòng lặp `ServerLevel.sendParticles`: toàn bộ VFX gửi qua 1 gói tin gọn {@link CerberusVfxPayload} sang Client.
 * - Giữ nguyên 100% cơ chế Attack Token (`maxSimultaneousHeavyAttackers = 1`), sát thương, cooldown và vai trò chiến đấu!
 */
public final class CerberusSkillGoals {
    private CerberusSkillGoals() {}

    public static void registerAll(CerberusBossEntity boss, GoalSelector selector) {
        // Priority 1: Thực thi skill đang kích hoạt (chiếm MOVE & LOOK)
        selector.addGoal(1, new TripleBiteGoal(boss));
        selector.addGoal(1, new HellfireBreathGoal(boss));
        selector.addGoal(1, new PounceGoal(boss));
        selector.addGoal(1, new GroundSlamGoal(boss));
        selector.addGoal(1, new HowlGoal(boss));
        selector.addGoal(1, new BiteComboGoal(boss));
        selector.addGoal(1, new WrathChargeGoal(boss));
        selector.addGoal(1, new FlameRainGoal(boss));
        selector.addGoal(1, new HellfireNovaGoal(boss));
        selector.addGoal(1, new ShadowDashGoal(boss));
        selector.addGoal(1, new ShadowStepGoal(boss));
        selector.addGoal(1, new PhantomClawGoal(boss));
        selector.addGoal(1, new PackHuntGoal(boss));

        // Priority 2: Bộ chọn skill theo vai trò, khoảng cách, trọng số, attack token & chống lặp 3 lần
        selector.addGoal(2, new CerberusSkillSelectorGoal(boss));

        // Priority 3: Di chuyển truy đuổi / bao vây vòng quanh (strafe/flank khi không giữ heavy token)
        selector.addGoal(3, new CerberusChaseFlankGoal(boss));
    }

    // =========================================================================
    // LỚP CHA CHUNG ĐỒNG BỘ 1:1 VỚI CerberusAttackTiming (0 TICK DRIFT + TRẢ TOKEN AN TOÀN)
    // =========================================================================
    private abstract static class BaseCerberusSkillGoal extends Goal {
        protected final CerberusBossEntity boss;
        protected final SkillState state;
        protected final SkillTimeline timeline;
        protected final boolean autoPlayStartAnim;
        protected final boolean holdsHeavyToken;
        protected final boolean holdsUltimateLock;
        protected int rawTicks = 0;
        protected double prevAnimTick = 0.0;
        protected double animTick = 0.0;
        protected float animSpeed = 1.0f;

        protected BaseCerberusSkillGoal(CerberusBossEntity boss, SkillState state, boolean autoPlayStartAnim,
                                        boolean holdsHeavyToken, boolean holdsUltimateLock) {
            this.boss = boss;
            this.state = state;
            this.timeline = CerberusAttackTiming.forSkill(state);
            this.autoPlayStartAnim = autoPlayStartAnim;
            this.holdsHeavyToken = holdsHeavyToken;
            this.holdsUltimateLock = holdsUltimateLock;
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
            this.animSpeed = CerberusAttackTiming.getAnimSpeed(state, boss.getPhase() >= 2);
            boss.getNavigation().stop();
            if (autoPlayStartAnim && timeline != null && !timeline.animKey().isEmpty()) {
                boss.playActionAnim(timeline.animKey(), this.animSpeed);
            }
        }

        @Override
        public void stop() {
            boss.setAllHeadsAnimationLocked(false);
            boss.setShadowCloaked(false);
            if (boss.getSkillState() == state) {
                boss.setSkillState(SkillState.IDLE);
            }
            EncounterGroup group = boss.getGroup();
            if (group != null) {
                if (holdsHeavyToken) group.releaseHeavyToken(boss.getUUID());
                if (holdsUltimateLock) group.releaseUltimateLock(boss.getUUID());
            }
        }

        @Override
        public void tick() {
            if (!(boss.level() instanceof ServerLevel level)) return;
            rawTicks++;
            prevAnimTick = animTick;
            animTick += animSpeed;
            onSkillTick(level, boss.getTarget(), prevAnimTick, animTick, rawTicks);
        }

        protected abstract void onSkillTick(ServerLevel level, LivingEntity target,
                                            double prevT, double curT, int rawTick);

        protected void complete(int restTicks) {
            boss.finishSkill(restTicks, holdsHeavyToken, holdsUltimateLock);
        }

        protected byte formId() {
            return (byte) boss.getCerberusForm().id();
        }

        protected void faceTarget(LivingEntity target, float baseDegPerTick) {
            if (target != null && target.isAlive()) {
                boss.smoothRotateToward(target.position().subtract(boss.position()),
                        baseDegPerTick, (float) CerberusConfig.get().phase2TurnSpeedMultiplier);
            }
        }

        protected Vec3 horizontalForward(LivingEntity target) {
            Vec3 v = (target != null && target.isAlive())
                    ? target.position().subtract(boss.position())
                    : boss.getLookAngle();
            Vec3 h = new Vec3(v.x, 0.0, v.z);
            return h.lengthSqr() > 1e-4 ? h.normalize() : new Vec3(0, 0, 1);
        }

        protected void hurtPlayersInBox(ServerLevel level, Vec3 center, double radius, float dmg, double knockUp, boolean ignite) {
            AABB box = new AABB(center.x - radius, center.y - 2.5, center.z - radius,
                    center.x + radius, center.y + 4.5, center.z + radius);
            for (Player p : level.getEntitiesOfClass(Player.class, box,
                    pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                if (p.position().distanceTo(center) <= radius) {
                    p.hurt(boss.damageSources().mobAttack(boss), dmg);
                    if (ignite) p.igniteForSeconds(4);
                    if (knockUp > 0.0) {
                        Vec3 away = p.position().subtract(center);
                        Vec3 h = away.horizontalDistanceSqr() > 1e-4 ? new Vec3(away.x, 0, away.z).normalize() : new Vec3(1, 0, 0);
                        p.setDeltaMovement(p.getDeltaMovement().add(h.x * 0.9, knockUp, h.z * 0.9));
                        p.hurtMarked = true;
                    }
                }
            }
        }
    }

    // =========================================================================
    // BỘ CHỌN SKILL THEO KHOẢNG CÁCH, VAI TRÒ, TRỌNG SỐ & ATTACK TOKEN
    // =========================================================================
    private static final class CerberusSkillSelectorGoal extends Goal {
        private final CerberusBossEntity boss;

        CerberusSkillSelectorGoal(CerberusBossEntity boss) {
            this.boss = boss;
        }

        private record WeightedSkill(SkillState state, int weight, int baseCooldown, boolean heavy) {}

        @Override
        public boolean canUse() {
            if (!boss.isAlive() || boss.isRotationLocked() || boss.getSkillState() != SkillState.IDLE) return false;
            if (boss.getGlobalSkillRestTicks() > 0) return false;
            LivingEntity t = boss.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() {
            if (!(boss.level() instanceof ServerLevel level)) return;
            LivingEntity target = boss.getTarget();
            if (target == null || !target.isAlive()) return;

            CerberusConfig cfg = CerberusConfig.get();
            CerberusForm form = boss.getCerberusForm();
            double sc = boss.getGameplayScale();
            double dist = boss.distanceTo(target);
            EncounterGroup group = boss.getGroup();

            // 1. Ưu tiên kiểm tra combo nhóm Pack Hunt khi đủ 3 cá thể Phase 2 sống
            if (form == CerberusForm.ALPHA_HEAD && group != null && group.getAliveMemberCount() >= 3
                    && group.getPackHuntCooldownTicks() <= 0 && dist <= 16.0 * sc
                    && level.random.nextFloat() < 0.35f) {
                if (group.tryStartPackHunt(level, boss, target)) {
                    return;
                }
            }

            List<WeightedSkill> pool = new ArrayList<>();
            int houndCap = group != null ? group.getRemainingHellhoundCapacity(level.getServer()) : 0;

            switch (form) {
                case TRIPLE -> {
                    if (dist <= 6.8 * sc) {
                        addIfReady(pool, SkillState.TRIPLE_BITE, 36, cfg.tripleBiteCooldown, false);
                        addIfReady(pool, SkillState.GROUND_SLAM, 28, cfg.groundSlamCooldown, false);
                    }
                    if (dist <= 11.0 * sc) {
                        addIfReady(pool, SkillState.HELLFIRE_BREATH, 26, cfg.hellfireBreathCooldown, false);
                    }
                    if (dist >= 4.2 * sc && dist <= 15.0 * sc) {
                        addIfReady(pool, SkillState.POUNCE, 26, cfg.pounceCooldown, false);
                    }
                    if (houndCap >= 2) {
                        addIfReady(pool, SkillState.HOWL, 16, cfg.howlCooldown, false);
                    }
                }
                case ALPHA_HEAD -> {
                    if (dist <= 6.5 * sc) {
                        addIfReady(pool, SkillState.BITE_COMBO, 34, cfg.tripleBiteCooldown, false);
                        addIfReady(pool, SkillState.GROUND_SLAM, 30, cfg.groundSlamCooldown, true);
                    }
                    if (dist >= 4.0 * sc && dist <= 16.0 * sc) {
                        addIfReady(pool, SkillState.WRATH_CHARGE, 32, cfg.wrathChargeCooldown, true);
                    }
                    if (houndCap >= 2) {
                        addIfReady(pool, SkillState.HOWL, 14, cfg.howlCooldown, false);
                    }
                }
                case FIRE_HEAD -> {
                    if (dist <= 12.0 * sc) {
                        addIfReady(pool, SkillState.HELLFIRE_BREATH, 32, cfg.hellfireBreathCooldown, false);
                    }
                    if (dist <= 14.0 * sc) {
                        addIfReady(pool, SkillState.HELLFIRE_NOVA, 26, cfg.hellfireNovaCooldown, true);
                    }
                    if (dist >= 4.0 * sc && dist <= 22.0 * sc) {
                        addIfReady(pool, SkillState.FLAME_RAIN, 30, cfg.flameRainCooldown, true);
                    }
                }
                case SHADOW_HEAD -> {
                    if (dist <= 6.0 * sc) {
                        addIfReady(pool, SkillState.PHANTOM_CLAW, 34, cfg.phantomClawCooldown, false);
                    }
                    if (dist >= 3.5 * sc && dist <= 18.0 * sc) {
                        addIfReady(pool, SkillState.SHADOW_STEP, 28, cfg.shadowStepCooldown, false);
                        addIfReady(pool, SkillState.SHADOW_DASH, 28, cfg.shadowDashCooldown, true);
                        addIfReady(pool, SkillState.POUNCE, 22, cfg.pounceCooldown, true);
                    }
                }
            }

            if (pool.isEmpty()) return;

            // Lọc đòn nặng theo Attack Token trong Phase 2: tối đa 1 cá thể tung đòn nặng cùng lúc!
            boolean allowHeavy = !form.isSplitSingle();
            if (form.isSplitSingle() && group != null) {
                boolean hasHeavyCandidate = pool.stream().anyMatch(WeightedSkill::heavy);
                if (hasHeavyCandidate) {
                    allowHeavy = group.tryAcquireHeavyToken(boss.getUUID(), level.getGameTime());
                }
            }

            List<WeightedSkill> usable = new ArrayList<>();
            for (WeightedSkill ws : pool) {
                if (!ws.heavy || allowHeavy) {
                    usable.add(ws);
                }
            }
            if (usable.isEmpty()) {
                if (allowHeavy && form.isSplitSingle() && group != null) {
                    group.releaseHeavyToken(boss.getUUID());
                }
                return;
            }

            int totalWeight = 0;
            for (WeightedSkill ws : usable) totalWeight += ws.weight;
            int roll = level.random.nextInt(Math.max(1, totalWeight));
            WeightedSkill chosen = usable.get(0);
            for (WeightedSkill ws : usable) {
                roll -= ws.weight;
                if (roll < 0) {
                    chosen = ws;
                    break;
                }
            }

            if (form.isSplitSingle() && group != null && allowHeavy && !chosen.heavy) {
                group.releaseHeavyToken(boss.getUUID());
            }

            boss.recordUsedSkill(chosen.state);
            boss.setSkillCooldown(chosen.state, chosen.baseCooldown);
            boss.setSkillState(chosen.state);
        }

        private void addIfReady(List<WeightedSkill> pool, SkillState s, int weight, int baseCd, boolean heavy) {
            if (boss.getSkillCooldown(s) <= 0 && !boss.wasUsedTwiceInARow(s)) {
                pool.add(new WeightedSkill(s, weight, baseCd, heavy));
            }
        }
    }

    // =========================================================================
    // DI CHUYỂN TRUY ĐUỔI & BAO VÂY VÒNG QUANH (FLANK / STRAFE TRONG PHASE 2)
    // =========================================================================
    private static final class CerberusChaseFlankGoal extends Goal {
        private final CerberusBossEntity boss;
        private int repathTicks = 0;
        private int meleeCooldown = 0;

        CerberusChaseFlankGoal(CerberusBossEntity boss) {
            this.boss = boss;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return boss.isAlive() && !boss.isRotationLocked()
                    && boss.getSkillState() == SkillState.IDLE
                    && boss.getTarget() != null && boss.getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() { return canUse(); }

        @Override
        public void tick() {
            LivingEntity target = boss.getTarget();
            if (target == null || !(boss.level() instanceof ServerLevel level)) return;
            if (meleeCooldown > 0) meleeCooldown--;

            CerberusForm form = boss.getCerberusForm();
            double sc = boss.getGameplayScale();
            double dist = boss.distanceTo(target);
            boss.smoothRotateToward(target.position().subtract(boss.position()), 9.0f,
                    (float) CerberusConfig.get().phase2TurnSpeedMultiplier);

            if (--repathTicks <= 0) {
                repathTicks = 10;
                if (form.isSplitSingle()) {
                    double desiredDist = (form == CerberusForm.FIRE_HEAD) ? (8.5 * sc)
                            : (form == CerberusForm.SHADOW_HEAD ? (5.5 * sc) : (3.6 * sc));
                    double orbitRad = Math.toRadians(form.flankAngleDeg()) + (boss.tickCount * 0.025);
                    Vec3 flankPos = target.position().add(Math.cos(orbitRad) * desiredDist, 0.0, Math.sin(orbitRad) * desiredDist);
                    boss.getNavigation().moveTo(flankPos.x, target.getY(), flankPos.z, 1.15);
                } else {
                    boss.getNavigation().moveTo(target, 1.05);
                }
            }

            if (meleeCooldown <= 0 && dist <= 3.6 * sc) {
                meleeCooldown = boss.getPhase() >= 2 ? 22 : 30;
                boss.playActionAnim("bite_2", boss.getPhase() >= 2 ? CerberusConfig.get().phase2AttackAnimSpeed : 1.0f);
                Vec3 headPos = boss.getHeadWorldPosition(CerberusBossEntity.SINGLE_ACTIVE_HEAD_INDEX);
                level.playSound(null, BlockPos.containing(headPos), ModSounds.CERBERUS_BITE, SoundSource.HOSTILE, 1.8f, 0.90f);
                target.hurt(boss.damageSources().mobAttack(boss), CerberusConfig.get().meleeDamage * boss.skillDamageScale());
            }
        }
    }

    // =========================================================================
    // PHASE 1 SKILL 1: TRIPLE BITE (3 đầu cắn liên hoàn khớp chính xác từng clip bite_1/3/2 = 16t, impact @ 9t!)
    // =========================================================================
    private static final class TripleBiteGoal extends BaseCerberusSkillGoal {
        private static final int[] ORDER = { 0, 2, 1 }; // Trái (bite_1) -> Phải (bite_3) -> Giữa (bite_2 chốt nặng 30)
        private int step = 0;
        private double stepPrevT = 0.0;
        private double stepCurT = 0.0;

        TripleBiteGoal(CerberusBossEntity boss) {
            super(boss, SkillState.TRIPLE_BITE, false, false, false);
        }

        @Override
        public void start() {
            super.start();
            step = 0;
            stepPrevT = 0.0;
            stepCurT = 0.0;
            beginBiteStep(0);
        }

        private void beginBiteStep(int s) {
            int headIdx = ORDER[s];
            boss.setAllHeadsAnimationLocked(false);
            boss.setHeadAnimationLock(headIdx, true);
            boss.playActionAnim("bite_" + (headIdx + 1), this.animSpeed);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            if (step >= 3) {
                complete(24);
                return;
            }
            stepPrevT = stepCurT;
            stepCurT += this.animSpeed;

            int headIdx = ORDER[step];
            double sc = boss.getGameplayScale();
            Vec3 headPos = boss.getHeadWorldPosition(headIdx);
            Vec3 fwd = horizontalForward(target);

            if (stepCurT < CerberusAttackTiming.BITE_IMPACT_TICK) {
                faceTarget(target, 10.0f);
                if (rawTick % 3 == 1) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_BITE_TELEGRAPH,
                            new Vec3(headPos.x, boss.getY() + 0.55, headPos.z), fwd, (float) (5.2 * sc), formId());
                }
            }
            if (stepPrevT < CerberusAttackTiming.BITE_IMPACT_TICK && stepCurT >= CerberusAttackTiming.BITE_IMPACT_TICK) {
                level.playSound(null, BlockPos.containing(headPos), ModSounds.CERBERUS_BITE, SoundSource.HOSTILE,
                        step == 2 ? 2.6f : 2.1f, step == 2 ? 0.72f : 0.92f);
                Vec3 strikePt = headPos.add(fwd.scale(2.6 * sc));
                float dmg = (step == 2 ? CerberusConfig.get().tripleBiteHeavyDamage : CerberusConfig.get().tripleBiteLightDamage)
                        * boss.skillDamageScale();
                hurtPlayersInBox(level, strikePt, 2.8 * sc, dmg, step == 2 ? 0.55 : 0.18, step == 2);
                ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_BITE_IMPACT,
                        strikePt, fwd, (float) (2.8 * sc), formId());
                if (step == 2) {
                    ModNetworking.sendShakeNearby(level, strikePt, 52.0, 1.8f, 12);
                }
            }
            if (stepCurT >= CerberusAttackTiming.BITE_CLIP_TICKS) {
                step++;
                stepPrevT = 0.0;
                stepCurT = 0.0;
                if (step < 3) {
                    beginBiteStep(step);
                } else {
                    complete(24);
                }
            }
        }
    }

    // =========================================================================
    // SKILL 2 (P1 & FIRE_HEAD): HELLFIRE BREATH (`breath` 52t, impact window 18..44t)
    // =========================================================================
    private static final class HellfireBreathGoal extends BaseCerberusSkillGoal {
        private int breathHead = 1;
        private Vec3 coneDir = new Vec3(0, 0, 1);
        private int nextDamageTick = 18;

        HellfireBreathGoal(CerberusBossEntity boss) {
            super(boss, SkillState.HELLFIRE_BREATH, true, false, false);
        }

        @Override
        public void start() {
            super.start();
            breathHead = boss.getCerberusForm().isSplitSingle()
                    ? CerberusBossEntity.SINGLE_ACTIVE_HEAD_INDEX
                    : boss.getRandom().nextInt(CerberusBossEntity.HEAD_COUNT);
            boss.setHeadAnimationLock(breathHead, true);
            coneDir = horizontalForward(boss.getTarget());
            nextDamageTick = timeline.hitWindows()[0].hitStartTick();
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            HitWindow win = timeline.hitWindows()[0];
            double sc = boss.getGameplayScale();
            double coneLen = 10.5 * sc;
            double halfAngleCos = Math.cos(Math.toRadians(27.0));
            Vec3 origin = boss.getHeadWorldPosition(breathHead);

            if (rawTick == 1) {
                level.playSound(null, BlockPos.containing(origin), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 2.4f, 0.65f);
            }
            if (curT < win.hitStartTick()) {
                if (target != null && target.isAlive() && curT <= timeline.lockAimTick()) {
                    coneDir = coneDir.lerp(horizontalForward(target), 0.25).normalize();
                    boss.smoothRotateToward(coneDir, 7.0f, (float) CerberusConfig.get().phase2TurnSpeedMultiplier);
                }
                if (rawTick % 4 == 1) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_BREATH_TELEGRAPH,
                            origin, coneDir, (float) coneLen, formId());
                }
                return;
            }
            if (win.crossedImpact(prevT, curT)) {
                level.playSound(null, BlockPos.containing(origin), ModSounds.CERBERUS_BREATH, SoundSource.HOSTILE, 2.7f, 0.78f);
            }
            if (win.isActive(prevT, curT)) {
                if (target != null && target.isAlive()) {
                    coneDir = coneDir.lerp(horizontalForward(target), 0.05).normalize();
                }
                if (rawTick % 2 == 0) {
                    double dropY = Math.max(0.5, origin.y - boss.getY() - 0.7);
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_BREATH_STREAM,
                            origin, new Vec3(coneDir.x, dropY, coneDir.z), (float) coneLen, formId());
                }
                if (curT >= nextDamageTick) {
                    nextDamageTick += 6;
                    float dmg = CerberusConfig.get().hellfireBreathTickDamage * boss.skillDamageScale();
                    AABB box = new AABB(origin.x - coneLen, boss.getY() - 2, origin.z - coneLen,
                            origin.x + coneLen, origin.y + 4, origin.z + coneLen);
                    for (Player p : level.getEntitiesOfClass(Player.class, box,
                            pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                        Vec3 toP = p.position().subtract(origin);
                        double hd = toP.horizontalDistance();
                        if (hd > 0.5 && hd <= coneLen) {
                            Vec3 hDir = new Vec3(toP.x, 0, toP.z).normalize();
                            if (hDir.dot(coneDir) >= halfAngleCos) {
                                com.abyssalrpg.combat.KatanaCombatHandler.runUnparryableMagic(() ->
                                        p.hurt(boss.damageSources().mobAttack(boss), dmg));
                                p.igniteForSeconds(3);
                            }
                        }
                    }
                    if (boss.getCerberusForm() == CerberusForm.FIRE_HEAD) {
                        Vec3 patch = origin.add(coneDir.scale(coneLen * 0.55));
                        boss.addFireTrailPatch(new Vec3(patch.x, boss.getY(), patch.z), 50);
                    }
                }
            }
            if (curT >= timeline.totalTicks()) {
                complete(24);
            }
        }
    }

    // =========================================================================
    // SKILL 3 (P1 & SHADOW_HEAD): POUNCE (`pounce` 40t, nhảy 16..30t, giáng đất đúng @ 30t!)
    // =========================================================================
    private static final class PounceGoal extends BaseCerberusSkillGoal {
        private Vec3 startPos = Vec3.ZERO;
        private Vec3 landPos = Vec3.ZERO;
        private boolean launched = false;

        PounceGoal(CerberusBossEntity boss) {
            super(boss, SkillState.POUNCE, true, true, false);
        }

        @Override
        public void start() {
            super.start();
            launched = false;
            startPos = boss.position();
            LivingEntity t = boss.getTarget();
            Vec3 desired = (t != null && t.isAlive()) ? t.position() : boss.position().add(boss.getLookAngle().scale(8.0));
            if (boss.level() instanceof ServerLevel sl) {
                landPos = AbstractGeoMythicBoss.findSafeLandingPosition(sl, desired, boss.position(), 5);
                HitWindow win = timeline.hitWindows()[0];
                int warnTicks = Math.max(12, Math.round(win.impactTick() / this.animSpeed));
                MagicCircleEntity.spawn(sl, boss, landPos, 3.6f * boss.getRelativeScaleFactor(),
                        warnTicks, boss.getCerberusForm().accentRgb(), 0.0f, false, false);
            } else {
                landPos = desired;
            }
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            HitWindow win = timeline.hitWindows()[0];
            double sc = boss.getGameplayScale();
            if (curT < win.hitStartTick()) {
                if (curT <= timeline.lockAimTick()) {
                    faceTarget(target, 11.0f);
                }
                return;
            }
            if (!launched) {
                launched = true;
                startPos = boss.position();
                level.playSound(null, boss.blockPosition(), ModSounds.CERBERUS_ROAR, SoundSource.HOSTILE, 2.4f, 1.05f);
            }
            if (curT < win.impactTick()) {
                float u = (float) Math.min(1.0, (curT - win.hitStartTick()) / Math.max(1.0, win.impactTick() - win.hitStartTick()));
                Vec3 pos = startPos.lerp(landPos, u).add(0.0, Math.sin(u * Math.PI) * (3.2 * sc / 2.0), 0.0);
                boss.setPos(pos.x, pos.y, pos.z);
                return;
            }
            if (win.crossedImpact(prevT, curT)) {
                boss.setPos(landPos.x, landPos.y, landPos.z);
                boss.snapSyncPrevAngles();
                level.playSound(null, BlockPos.containing(landPos), ModSounds.CERBERUS_SLAM, SoundSource.HOSTILE, 2.7f, 0.80f);
                ModNetworking.sendShakeNearby(level, landPos, 64.0 * boss.getRelativeScaleFactor(), 2.0f, 14);
                ShockwaveRingEntity.spawn(level, landPos, (float) (6.2 * sc), boss.getCerberusForm().accentRgb(), 14);
                ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_POUNCE_IMPACT,
                        landPos, Vec3.ZERO, (float) (4.6 * sc), formId());

                float directDmg = CerberusConfig.get().pounceImpactDamage * boss.skillDamageScale();
                float waveDmg = CerberusConfig.get().pounceWaveDamage * boss.skillDamageScale();
                double innerR = 2.8 * sc;
                double outerR = 5.8 * sc;
                AABB box = new AABB(landPos.x - outerR, landPos.y - 2, landPos.z - outerR,
                        landPos.x + outerR, landPos.y + 4, landPos.z + outerR);
                for (Player p : level.getEntitiesOfClass(Player.class, box,
                        pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    double d = Math.hypot(p.getX() - landPos.x, p.getZ() - landPos.z);
                    if (d <= outerR) {
                        p.hurt(boss.damageSources().mobAttack(boss), d <= innerR ? directDmg : waveDmg);
                        Vec3 away = p.position().subtract(landPos);
                        Vec3 h = away.horizontalDistanceSqr() > 1e-4 ? new Vec3(away.x, 0, away.z).normalize() : new Vec3(1, 0, 0);
                        p.setDeltaMovement(p.getDeltaMovement().add(h.x * 1.1, 0.72, h.z * 1.1));
                        p.hurtMarked = true;
                    }
                }
            }
            if (curT >= timeline.totalTicks()) {
                complete(24);
            }
        }
    }

    // =========================================================================
    // SKILL 4 (P1 & ALPHA_HEAD): GROUND SLAM (`slam` 36t, impact đúng @ 18t!)
    // =========================================================================
    private static final class GroundSlamGoal extends BaseCerberusSkillGoal {
        private Vec3 slamDir = new Vec3(0, 0, 1);

        GroundSlamGoal(CerberusBossEntity boss) {
            super(boss, SkillState.GROUND_SLAM, true, true, false);
        }

        @Override
        public void start() {
            super.start();
            slamDir = horizontalForward(boss.getTarget());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            HitWindow win = timeline.hitWindows()[0];
            double sc = boss.getGameplayScale();
            double range = 9.5 * sc;

            if (curT < win.impactTick()) {
                if (curT <= timeline.lockAimTick()) {
                    slamDir = slamDir.lerp(horizontalForward(target), 0.25).normalize();
                    boss.smoothRotateToward(slamDir, 8.0f, (float) CerberusConfig.get().phase2TurnSpeedMultiplier);
                }
                if (rawTick % 4 == 1) {
                    float teleR = (float) (1.8 * sc + (curT / win.impactTick()) * (range - 1.8 * sc));
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_SLAM_TELEGRAPH,
                            boss.position(), slamDir, teleR, formId());
                }
                return;
            }
            if (win.crossedImpact(prevT, curT)) {
                float dmg = CerberusConfig.get().groundSlamDamage * boss.skillDamageScale();
                level.playSound(null, boss.blockPosition(), ModSounds.CERBERUS_SLAM, SoundSource.HOSTILE, 2.9f, 0.68f);
                ModNetworking.sendShakeNearby(level, boss.position(), 76.0 * boss.getRelativeScaleFactor(), 2.3f, 16);
                EnergySlashEntity.spawn(level, boss, boss.position().add(0, 0.9 * sc, 0), slamDir,
                        1.4f, (float) (6.8 * sc), 0f, boss.getCerberusForm().accentRgb(), 0f, false);
                ShockwaveRingEntity.spawn(level, boss.position(), (float) range * 0.85f, boss.getCerberusForm().accentRgb(), 16);
                ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_SLAM_IMPACT,
                        boss.position().add(slamDir.scale(2.8 * boss.getRelativeScaleFactor())), slamDir, (float) (range * 0.72), formId());

                AABB box = boss.getBoundingBox().inflate(range);
                for (Player p : level.getEntitiesOfClass(Player.class, box,
                        pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    Vec3 toP = p.position().subtract(boss.position());
                    double d = toP.horizontalDistance();
                    if (d <= range) {
                        Vec3 hDir = d > 1e-3 ? new Vec3(toP.x, 0, toP.z).normalize() : slamDir;
                        if (d < 3.0 * sc || hDir.dot(slamDir) >= 0.35) {
                            p.hurt(boss.damageSources().mobAttack(boss), dmg);
                            p.setDeltaMovement(p.getDeltaMovement().add(hDir.x * 1.25, 0.95, hDir.z * 1.25));
                            p.hurtMarked = true;
                        }
                    }
                }
            }
            if (curT >= timeline.totalTicks()) {
                complete(26);
            }
        }
    }

    // =========================================================================
    // SKILL 5 (P1 & ALPHA_HEAD): HOWL (`howl` 40t, gọi 2-3 Hellhound đúng @ 18t!)
    // =========================================================================
    private static final class HowlGoal extends BaseCerberusSkillGoal {
        HowlGoal(CerberusBossEntity boss) {
            super(boss, SkillState.HOWL, true, false, false);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            HitWindow win = timeline.hitWindows()[0];
            if (rawTick == 1) {
                level.playSound(null, boss.blockPosition(), ModSounds.CERBERUS_HOWL, SoundSource.HOSTILE, 3.0f, 0.78f);
            }
            if (curT < win.impactTick()) {
                if (rawTick % 4 == 1) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_HOWL_CHARGE,
                            boss.position().add(0, 1.6 * boss.getEffectiveScale(), 0), Vec3.ZERO,
                            (float) (2.6 * boss.getEffectiveScale()), formId());
                }
                return;
            }
            if (win.crossedImpact(prevT, curT)) {
                int want = boss.getCerberusForm().isSplitSingle() ? 2 : (2 + level.random.nextInt(2));
                boss.summonHellhounds(level, want, target);
                ShockwaveRingEntity.spawn(level, boss.position(), 7.5f * boss.getEffectiveScale(), boss.getCerberusForm().accentRgb(), 14);
                ModNetworking.sendShakeNearby(level, boss.position(), 56.0, 1.4f, 12);
            }
            if (curT >= timeline.totalTicks()) {
                complete(28);
            }
        }
    }

    // =========================================================================
    // PHASE 2 ALPHA_HEAD: BITE COMBO (3 nhịp `bite_2`, mỗi nhịp 16t impact @ 9t) & WRATH CHARGE (`dash` 32t)
    // =========================================================================
    private static final class BiteComboGoal extends BaseCerberusSkillGoal {
        private int step = 0;
        private double stepPrevT = 0.0;
        private double stepCurT = 0.0;

        BiteComboGoal(CerberusBossEntity boss) {
            super(boss, SkillState.BITE_COMBO, false, false, false);
        }

        @Override
        public void start() {
            super.start();
            step = 0;
            stepPrevT = 0.0;
            stepCurT = 0.0;
            boss.setAllHeadsAnimationLocked(true);
            boss.playActionAnim("bite_2", this.animSpeed);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            if (step >= 3) {
                complete(22);
                return;
            }
            stepPrevT = stepCurT;
            stepCurT += this.animSpeed;
            double sc = boss.getGameplayScale();
            faceTarget(target, 12.0f);

            if (stepPrevT < CerberusAttackTiming.BITE_IMPACT_TICK && stepCurT >= CerberusAttackTiming.BITE_IMPACT_TICK) {
                Vec3 fwd = horizontalForward(target);
                boss.setDeltaMovement(fwd.scale(0.55).add(0, 0.08, 0));
                Vec3 headPos = boss.getHeadWorldPosition(CerberusBossEntity.SINGLE_ACTIVE_HEAD_INDEX);
                level.playSound(null, BlockPos.containing(headPos), ModSounds.CERBERUS_BITE, SoundSource.HOSTILE, 2.3f, 0.82f + step * 0.08f);
                Vec3 strikePt = headPos.add(fwd.scale(2.0 * sc));
                float dmg = CerberusConfig.get().tripleBiteLightDamage * boss.skillDamageScale();
                hurtPlayersInBox(level, strikePt, 2.6 * sc, dmg, 0.22, true);
                ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_BITE_IMPACT,
                        strikePt, fwd, (float) (2.6 * sc), formId());
            }
            if (stepCurT >= CerberusAttackTiming.BITE_CLIP_TICKS) {
                step++;
                stepPrevT = 0.0;
                stepCurT = 0.0;
                if (step < 3) {
                    boss.playActionAnim("bite_2", this.animSpeed);
                } else {
                    complete(22);
                }
            }
        }
    }

    private static final class WrathChargeGoal extends BaseCerberusSkillGoal {
        private Vec3 chargeDir = new Vec3(0, 0, 1);
        private final Set<UUID> hit = new HashSet<>();

        WrathChargeGoal(CerberusBossEntity boss) {
            super(boss, SkillState.WRATH_CHARGE, true, true, false);
        }

        @Override
        public void start() {
            super.start();
            hit.clear();
            chargeDir = horizontalForward(boss.getTarget());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            HitWindow win = timeline.hitWindows()[0];
            double sc = boss.getGameplayScale();
            if (curT < win.hitStartTick()) {
                if (curT <= timeline.lockAimTick()) {
                    chargeDir = chargeDir.lerp(horizontalForward(target), 0.30).normalize();
                    boss.smoothRotateToward(chargeDir, 12.0f, (float) CerberusConfig.get().phase2TurnSpeedMultiplier);
                }
                if (rawTick % 3 == 1) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_DASH_TELEGRAPH,
                            boss.position(), chargeDir, (float) (14.0 * boss.getRelativeScaleFactor()), formId());
                }
                return;
            }
            if (win.crossedImpact(prevT, curT)) {
                level.playSound(null, boss.blockPosition(), ModSounds.CERBERUS_ROAR, SoundSource.HOSTILE, 2.8f, 0.72f);
            }
            if (win.isActive(prevT, curT)) {
                boss.setDeltaMovement(chargeDir.scale(1.45).add(0, boss.getDeltaMovement().y * 0.2, 0));
                if (rawTick % 2 == 0) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_DASH_TRAIL,
                            boss.position(), chargeDir, (float) (2.2 * boss.getRelativeScaleFactor()), formId());
                }
                float dmg = CerberusConfig.get().wrathChargeDamage * boss.skillDamageScale();
                AABB box = boss.getBoundingBox().inflate(1.6 * sc);
                for (Player p : level.getEntitiesOfClass(Player.class, box,
                        pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    if (hit.add(p.getUUID())) {
                        p.hurt(boss.damageSources().mobAttack(boss), dmg);
                        p.setDeltaMovement(chargeDir.scale(1.35).add(0, 0.75, 0));
                        p.hurtMarked = true;
                        ModNetworking.sendShakeNearby(level, p.position(), 44.0, 1.8f, 10);
                    }
                }
                return;
            }
            boss.setDeltaMovement(Vec3.ZERO);
            if (curT >= timeline.totalTicks()) {
                complete(24);
            }
        }
    }

    // =========================================================================
    // PHASE 2 FIRE_HEAD: FLAME RAIN (`howl` 40t, 2 đợt @ 16t & 28t) & HELLFIRE NOVA (`nova` 42t, 18..40t)
    // =========================================================================
    private static final class FlameRainGoal extends BaseCerberusSkillGoal {
        FlameRainGoal(CerberusBossEntity boss) {
            super(boss, SkillState.FLAME_RAIN, true, true, false);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            HitWindow w0 = timeline.hitWindows()[0];
            HitWindow w1 = timeline.hitWindows()[1];
            faceTarget(target, 9.0f);

            if (curT < w0.impactTick()) {
                if (rawTick % 4 == 1) {
                    Vec3 hp = boss.getHeadWorldPosition(CerberusBossEntity.SINGLE_ACTIVE_HEAD_INDEX);
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_HOWL_CHARGE,
                            hp, Vec3.ZERO, 1.4f, formId());
                }
                return;
            }
            boolean wave0 = w0.crossedImpact(prevT, curT);
            boolean wave1 = w1.crossedImpact(prevT, curT);
            if (wave0 || wave1) {
                Vec3 anchor = (target != null && target.isAlive()) ? target.position() : boss.position().add(horizontalForward(null).scale(8.0));
                Vec3 headPos = boss.getHeadWorldPosition(CerberusBossEntity.SINGLE_ACTIVE_HEAD_INDEX);
                float dmg = CerberusConfig.get().flameRainOrbDamage * boss.skillDamageScale();
                level.playSound(null, boss.blockPosition(), ModSounds.CERBERUS_SPLIT_BURST, SoundSource.HOSTILE, 2.2f, 1.05f);

                float rel = boss.getRelativeScaleFactor();
                for (int i = 0; i < 4; i++) {
                    double ang = (Math.PI * 2.0 * i) / 4.0 + (wave0 ? 0.0 : 0.78);
                    double rad = (i == 0) ? 0.5 : (3.2 + level.random.nextDouble() * 4.5) * rel;
                    Vec3 spot = anchor.add(Math.cos(ang) * rad, 0.05, Math.sin(ang) * rad);
                    MagicCircleEntity.spawn(level, boss, spot, 2.3f * rel, 20, CerberusBossEntity.COLOR_FIRE_GOLD, dmg * 0.7f, false, false);

                    float grav = 0.045f;
                    int flight = 20;
                    Vec3 diff = spot.subtract(headPos);
                    Vec3 vel = new Vec3(diff.x / flight, (diff.y + 0.5 * grav * flight * flight) / flight, diff.z / flight);
                    AbyssalOrbEntity.spawnParabolic(level, boss, headPos, vel, grav,
                            0.65f * rel, CerberusBossEntity.COLOR_FIRE_GOLD,
                            dmg, 2.4f * rel, true, false, false);
                    boss.addFireTrailPatch(spot, 50);
                }
            }
            if (curT >= timeline.totalTicks()) {
                complete(26);
            }
        }
    }

    private static final class HellfireNovaGoal extends BaseCerberusSkillGoal {
        private double curR = 0.0;
        private final Set<UUID> hit = new HashSet<>();

        HellfireNovaGoal(CerberusBossEntity boss) {
            super(boss, SkillState.HELLFIRE_NOVA, true, true, false);
        }

        @Override
        public void start() {
            super.start();
            curR = 0.0;
            hit.clear();
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            HitWindow win = timeline.hitWindows()[0];
            double sc = boss.getGameplayScale();
            double maxR = 13.0 * sc;
            if (curT < win.hitStartTick()) {
                if (rawTick % 4 == 1) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_NOVA_CHARGE,
                            boss.position(), Vec3.ZERO, (float) (2.6 * sc), formId());
                }
                return;
            }
            if (win.crossedImpact(prevT, curT)) {
                level.playSound(null, boss.blockPosition(), ModSounds.CERBERUS_SPLIT_BURST, SoundSource.HOSTILE, 2.9f, 0.72f);
                ModNetworking.sendShakeNearby(level, boss.position(), 76.0, 2.2f, 16);
                ShockwaveRingEntity.spawn(level, boss.position().add(0, 0.25, 0), (float) maxR, CerberusBossEntity.COLOR_FIRE_GOLD, 24);
            }
            if (win.isActive(prevT, curT)) {
                double prevR = curR;
                double span = Math.max(1.0, win.hitEndTick() - win.hitStartTick());
                curR = Math.min(1.0, (curT - win.hitStartTick()) / span) * maxR;
                if (rawTick % 2 == 0) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_NOVA_RING,
                            boss.position(), Vec3.ZERO, (float) curR, formId());
                }
                float dmg = CerberusConfig.get().hellfireNovaDamage * boss.skillDamageScale();
                AABB box = new AABB(boss.getX() - curR - 3, boss.getY() - 3, boss.getZ() - curR - 3,
                        boss.getX() + curR + 3, boss.getY() + 5, boss.getZ() + curR + 3);
                for (Player p : level.getEntitiesOfClass(Player.class, box,
                        pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    double hd = Math.hypot(p.getX() - boss.getX(), p.getZ() - boss.getZ());
                    boolean inRing = hd >= Math.max(0.0, prevR - 1.4 * sc) && hd <= curR + 1.4 * sc;
                    boolean jumpedOver = !p.onGround() && (p.getY() - boss.getY()) > 1.05;
                    if (inRing && !jumpedOver && hit.add(p.getUUID())) {
                        com.abyssalrpg.combat.KatanaCombatHandler.runUnparryableMagic(() ->
                                p.hurt(boss.damageSources().mobAttack(boss), dmg));
                        p.igniteForSeconds(4);
                    }
                }
                return;
            }
            if (curT >= timeline.totalTicks()) {
                complete(26);
            }
        }
    }

    // =========================================================================
    // PHASE 2 SHADOW_HEAD: SHADOW DASH (`dash` 32t), SHADOW STEP (`shadow_step` 24t) & PHANTOM CLAW (`bite_3` x3)
    // =========================================================================
    private static final class ShadowDashGoal extends BaseCerberusSkillGoal {
        private Vec3 dir = new Vec3(0, 0, 1);
        private final Set<UUID> hit = new HashSet<>();

        ShadowDashGoal(CerberusBossEntity boss) {
            super(boss, SkillState.SHADOW_DASH, true, true, false);
        }

        @Override
        public void start() {
            super.start();
            hit.clear();
            dir = horizontalForward(boss.getTarget());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            HitWindow win = timeline.hitWindows()[0];
            double sc = boss.getGameplayScale();
            if (curT < win.hitStartTick()) {
                if (curT <= timeline.lockAimTick()) {
                    dir = dir.lerp(horizontalForward(target), 0.35).normalize();
                    boss.smoothRotateToward(dir, 14.0f, (float) CerberusConfig.get().phase2TurnSpeedMultiplier);
                }
                if (rawTick % 3 == 1) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_DASH_TELEGRAPH,
                            boss.position(), dir, (float) (15.0 * boss.getRelativeScaleFactor()), formId());
                }
                return;
            }
            if (win.crossedImpact(prevT, curT)) {
                level.playSound(null, boss.blockPosition(), ModSounds.CERBERUS_SHADOW_STEP, SoundSource.HOSTILE, 2.4f, 0.85f);
            }
            if (win.isActive(prevT, curT)) {
                boss.setDeltaMovement(dir.scale(1.85).add(0, boss.getDeltaMovement().y * 0.2, 0));
                if (rawTick % 2 == 0) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_DASH_TRAIL,
                            boss.position(), dir, (float) (2.0 * boss.getRelativeScaleFactor()), formId());
                }
                float dmg = CerberusConfig.get().shadowDashDamage * boss.skillDamageScale();
                AABB box = boss.getBoundingBox().inflate(1.5 * sc);
                for (Player p : level.getEntitiesOfClass(Player.class, box,
                        pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    if (hit.add(p.getUUID())) {
                        p.hurt(boss.damageSources().mobAttack(boss), dmg);
                    }
                }
                return;
            }
            boss.setDeltaMovement(Vec3.ZERO);
            if (curT >= timeline.totalTicks()) {
                complete(22);
            }
        }
    }

    private static final class ShadowStepGoal extends BaseCerberusSkillGoal {
        private Vec3 appearPos = Vec3.ZERO;

        ShadowStepGoal(CerberusBossEntity boss) {
            super(boss, SkillState.SHADOW_STEP, true, false, false);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            HitWindow win = timeline.hitWindows()[0];
            if (rawTick == 1) {
                boss.setShadowCloaked(true);
                level.playSound(null, boss.blockPosition(), ModSounds.CERBERUS_SHADOW_STEP, SoundSource.HOSTILE, 2.2f, 1.15f);
                ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_SHADOW_VANISH,
                        boss.position(), Vec3.ZERO, 2.4f * boss.getRelativeScaleFactor(), formId());

                Vec3 behind;
                if (target != null && target.isAlive()) {
                    Vec3 look = target.getLookAngle().multiply(1, 0, 1);
                    if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
                    behind = target.position().subtract(look.normalize().scale(3.2));
                } else {
                    behind = boss.position().add(boss.getLookAngle().scale(4.0));
                }
                appearPos = AbstractGeoMythicBoss.findSafeLandingPosition(level, behind, boss.position(), 5);
                MagicCircleEntity.spawn(level, boss, appearPos, 2.8f * boss.getRelativeScaleFactor(), 12,
                        CerberusBossEntity.COLOR_SHADOW_VIOLET, 0.0f, false, false);
                return;
            }
            if (curT < win.impactTick()) {
                if (rawTick % 3 == 0) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_SHADOW_WARN,
                            appearPos, Vec3.ZERO, 2.0f * boss.getRelativeScaleFactor(), formId());
                }
                return;
            }
            if (win.crossedImpact(prevT, curT)) {
                boss.setPos(appearPos.x, appearPos.y, appearPos.z);
                boss.setShadowCloaked(false);
                if (target != null && target.isAlive()) {
                    boss.facePositionImmediately(target.position());
                } else {
                    boss.snapSyncPrevAngles();
                }
                level.playSound(null, BlockPos.containing(appearPos), ModSounds.CERBERUS_BITE, SoundSource.HOSTILE, 2.4f, 1.12f);
                ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_SHADOW_APPEAR,
                        appearPos, Vec3.ZERO, 2.5f * boss.getRelativeScaleFactor(), formId());
                float dmg = CerberusConfig.get().shadowStepDamage * boss.skillDamageScale();
                hurtPlayersInBox(level, appearPos.add(horizontalForward(target).scale(1.8 * boss.getRelativeScaleFactor())),
                        3.2 * boss.getGameplayScale() / 2.0, dmg, 0.25, false);
            }
            if (curT >= timeline.totalTicks()) {
                complete(22);
            }
        }
    }

    private static final class PhantomClawGoal extends BaseCerberusSkillGoal {
        private int step = 0;
        private double stepPrevT = 0.0;
        private double stepCurT = 0.0;

        PhantomClawGoal(CerberusBossEntity boss) {
            super(boss, SkillState.PHANTOM_CLAW, false, false, false);
        }

        @Override
        public void start() {
            super.start();
            step = 0;
            stepPrevT = 0.0;
            stepCurT = 0.0;
            boss.setAllHeadsAnimationLocked(true);
            boss.playActionAnim("bite_2", this.animSpeed);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            if (step >= 3) {
                complete(20);
                return;
            }
            stepPrevT = stepCurT;
            stepCurT += this.animSpeed;
            double sc = boss.getGameplayScale();
            faceTarget(target, 14.0f);

            if (stepPrevT < CerberusAttackTiming.BITE_IMPACT_TICK && stepCurT >= CerberusAttackTiming.BITE_IMPACT_TICK) {
                Vec3 fwd = horizontalForward(target);
                Vec3 strike = boss.position().add(fwd.scale(2.4 * sc)).add(0, 0.9, 0);
                level.playSound(null, BlockPos.containing(strike), ModSounds.CERBERUS_BITE, SoundSource.HOSTILE, 2.0f, 1.25f + step * 0.08f);
                EnergySlashEntity.spawn(level, boss, strike, fwd, 1.6f, (float) (3.2 * sc), 0f,
                        CerberusBossEntity.COLOR_SHADOW_VIOLET, 0f, false);
                hurtPlayersInBox(level, strike, 2.6 * sc, CerberusConfig.get().phantomClawDamage * boss.skillDamageScale(), 0.1, false);
            }
            if (stepCurT >= CerberusAttackTiming.BITE_CLIP_TICKS) {
                step++;
                stepPrevT = 0.0;
                stepCurT = 0.0;
                if (step < 3) {
                    boss.playActionAnim("bite_2", this.animSpeed);
                } else {
                    complete(20);
                }
            }
        }
    }

    // =========================================================================
    // COMBO NHÓM 3 CÁ THỂ: PACK HUNT (`pack_hunt` 48t, trì hoãn tuần tự theo slot 0/16/32t rồi chạy đúng clip!)
    // =========================================================================
    private static final class PackHuntGoal extends BaseCerberusSkillGoal {
        private Vec3 dashDir = new Vec3(0, 0, 1);
        private boolean acquiredSlotToken = false;
        private boolean clipLaunched = false;
        private double clipPrevT = 0.0;
        private double clipCurT = 0.0;
        private final Set<UUID> hit = new HashSet<>();

        PackHuntGoal(CerberusBossEntity boss) {
            super(boss, SkillState.PACK_HUNT, false, true, true);
        }

        @Override
        public void start() {
            super.start();
            hit.clear();
            acquiredSlotToken = false;
            clipLaunched = false;
            clipPrevT = 0.0;
            clipCurT = 0.0;
            dashDir = horizontalForward(boss.getTarget());
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, double prevT, double curT, int rawTick) {
            LivingEntity resolvedTarget = target;
            if (resolvedTarget == null && boss.getPackHuntTargetId() != null) {
                Entity e = level.getEntity(boss.getPackHuntTargetId());
                if (e instanceof LivingEntity le && le.isAlive()) resolvedTarget = le;
            }
            if (resolvedTarget == null || !resolvedTarget.isAlive()) {
                complete(20);
                return;
            }

            int delay = Math.max(0, boss.getPackHuntDelayTicks()); // 0 (Shadow), 16 (Fire), 32 (Alpha) -> cách nhau 0.8s
            if (rawTick <= delay) {
                dashDir = horizontalForward(resolvedTarget);
                boss.smoothRotateToward(dashDir, 14.0f, (float) CerberusConfig.get().phase2TurnSpeedMultiplier);
                if (rawTick % 4 == 1) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_DASH_TELEGRAPH,
                            boss.position(), dashDir, (float) (12.0 * boss.getRelativeScaleFactor()), formId());
                }
                return;
            }

            if (!clipLaunched) {
                clipLaunched = true;
                boss.playActionAnim(timeline.animKey(), 1.0f);
            }
            clipPrevT = clipCurT;
            clipCurT += 1.0;

            HitWindow win = timeline.hitWindows()[0];
            double sc = boss.getGameplayScale();

            if (clipCurT < win.hitStartTick()) {
                if (clipCurT <= timeline.lockAimTick()) {
                    dashDir = horizontalForward(resolvedTarget);
                    boss.smoothRotateToward(dashDir, 14.0f, (float) CerberusConfig.get().phase2TurnSpeedMultiplier);
                }
                if (rawTick % 3 == 1) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_DASH_TELEGRAPH,
                            boss.position(), dashDir, (float) (12.0 * boss.getRelativeScaleFactor()), formId());
                }
                return;
            }
            if (win.crossedImpact(clipPrevT, clipCurT)) {
                EncounterGroup grp = boss.getGroup();
                if (grp != null) {
                    acquiredSlotToken = grp.tryAcquireHeavyToken(boss.getUUID(), level.getGameTime());
                }
                level.playSound(null, boss.blockPosition(), ModSounds.CERBERUS_ROAR, SoundSource.HOSTILE, 2.5f,
                        0.85f + boss.getPackHuntSlotIndex() * 0.12f);
            }
            if (win.isActive(clipPrevT, clipCurT)) {
                boss.setDeltaMovement(dashDir.scale(1.65).add(0, boss.getDeltaMovement().y * 0.2, 0));
                if (rawTick % 2 == 0) {
                    ModNetworking.sendCerberusVfxNearby(level, CerberusVfxPayload.KIND_DASH_TRAIL,
                            boss.position(), dashDir, (float) (2.2 * boss.getRelativeScaleFactor()), formId());
                }
                float dmg = CerberusConfig.get().packHuntStrikeDamage * boss.skillDamageScale();
                AABB box = boss.getBoundingBox().inflate(1.6 * sc);
                for (Player p : level.getEntitiesOfClass(Player.class, box,
                        pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    if (hit.add(p.getUUID())) {
                        p.hurt(boss.damageSources().mobAttack(boss), dmg);
                        if (boss.getCerberusForm() == CerberusForm.FIRE_HEAD) p.igniteForSeconds(4);
                        p.setDeltaMovement(dashDir.scale(1.1).add(0, 0.5, 0));
                        p.hurtMarked = true;
                        ModNetworking.sendShakeNearby(level, p.position(), 48.0, 1.6f, 10);
                    }
                }
                return;
            }
            boss.setDeltaMovement(Vec3.ZERO);
            if (acquiredSlotToken && boss.getGroup() != null) {
                boss.getGroup().releaseHeavyToken(boss.getUUID());
                acquiredSlotToken = false;
            }
            if (clipCurT >= timeline.totalTicks()) {
                complete(26);
            }
        }
    }
}
