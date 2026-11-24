package com.abyssalrpg.entity.mythic.jormungandr;

import com.abyssalrpg.config.JormungandrConfig;
import com.abyssalrpg.entity.mythic.AbstractGeoMythicBoss;
import com.abyssalrpg.entity.mythic.JormungandrBossEntity;
import com.abyssalrpg.entity.mythic.JormungandrBossEntity.SkillState;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
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
 * Toàn bộ 10 kỹ năng (6 Phase 1 + 4 Phase 2 bao gồm Ultimate "Ouroboros Ring" bản đầy đủ thu nhỏ vòng + lỗ hổng quay chậm),
 * bộ chọn skill chống lặp 3 lần và AI steering trực tiếp của Boss Jörmungandr.
 */
public final class JormungandrSkillGoals {
    private JormungandrSkillGoals() {}

    public static void registerAll(JormungandrBossEntity boss, GoalSelector selector) {
        // Priority 1: 6 Kỹ năng Phase 1
        selector.addGoal(1, new StrikeBiteGoal(boss));
        selector.addGoal(1, new VenomSpitGoal(boss));
        selector.addGoal(1, new TailSlamGoal(boss));
        selector.addGoal(1, new ConstrictCoilGoal(boss));
        selector.addGoal(1, new HoodFlareGoal(boss));
        selector.addGoal(1, new BurrowGoal(boss));

        // Priority 1: 4 Kỹ năng Phase 2 "Cuồng bạo"
        selector.addGoal(1, new ToxicNovaGoal(boss));
        selector.addGoal(1, new RapidStrikeGoal(boss));
        selector.addGoal(1, new VenomRainGoal(boss));
        selector.addGoal(1, new OuroborosRingGoal(boss));

        // Priority 2: Bộ chọn skill theo khoảng cách, trọng số, cooldown, không lặp 3 lần liên tiếp
        selector.addGoal(2, new JormungandrSkillSelectorGoal(boss));

        // Priority 3: Di chuyển steering trực tiếp
        selector.addGoal(3, new JormungandrDirectChaseGoal(boss));
    }

    private abstract static class BaseJormSkillGoal extends Goal {
        protected final JormungandrBossEntity boss;
        protected final SkillState state;
        protected final String animKey;
        protected int ticks = 0;

        protected BaseJormSkillGoal(JormungandrBossEntity boss, SkillState state, String animKey) {
            this.boss = boss;
            this.state = state;
            this.animKey = animKey;
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
            this.ticks = 0;
            boss.getNavigation().stop();
            if (animKey != null && !animKey.isEmpty()) {
                boss.playActionAnim(animKey);
            }
        }

        @Override
        public void stop() {
            boss.setHeadAnimationLocked(false);
            boss.setBurrowed(false);
            boss.getSerpentPath().setOverrideNone();
            if (boss.getSkillState() == state) {
                boss.setSkillState(SkillState.IDLE);
            }
        }

        @Override
        public void tick() {
            if (!(boss.level() instanceof ServerLevel sl)) return;
            ticks++;
            onSkillTick(sl, boss.getTarget(), ticks);
        }

        protected abstract void onSkillTick(ServerLevel level, LivingEntity target, int t);

        protected void completeStandardRest() {
            int rest = 30 + boss.getRandom().nextInt(21); // 1.5 - 2.5 giây
            boss.finishSkill(rest);
        }

        protected void faceTarget(LivingEntity target, float baseDegPerTick) {
            if (target != null && target.isAlive()) {
                boss.smoothRotateToward(target.position().subtract(boss.position()),
                        baseDegPerTick, (float) JormungandrConfig.get().phase2TurnSpeedMultiplier);
            }
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

        protected static final DustParticleOptions TELEGRAPH_VIOLET =
                new DustParticleOptions(new Vector3f(0.68f, 0.45f, 0.96f), 1.35f);
        protected static final DustParticleOptions TELEGRAPH_ENRAGED =
                new DustParticleOptions(new Vector3f(0.88f, 0.54f, 1.00f), 1.55f);

        protected void spawnLineTelegraph(ServerLevel level, Vec3 start, Vec3 dir, double len, double step) {
            int n = Math.min(18, Math.max(4, (int) (len / step)));
            DustParticleOptions dust = boss.isEnragedVisual() ? TELEGRAPH_ENRAGED : TELEGRAPH_VIOLET;
            for (int i = 1; i <= n; i++) {
                Vec3 p = start.add(dir.scale(i * step));
                level.sendParticles(dust, p.x, p.y + 0.25, p.z, 2, 0.22, 0.04, 0.22, 0.0);
            }
        }

        protected void applyVenom(Player p, int ticksCap) {
            int dur = Math.min(ticksCap, JormungandrConfig.get().venomDurationTicks);
            if (dur > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.POISON, dur, 1));
            }
        }

        protected Vec3 groundAt(ServerLevel level, double x, double z, double hintY) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (Math.abs(y - hintY) > 10.0) y = Mth.floor(hintY);
            return new Vec3(x, y, z);
        }
    }

    // =========================================================================
    // 1. STRIKE BITE — Ngẩng cổ, xòe mang, lùi đầu telegraph -> phóng thẳng đòn nặng (22 / P2 30.8)
    // =========================================================================
    private static final class StrikeBiteGoal extends BaseJormSkillGoal {
        private Vec3 lockedFwd = new Vec3(0, 0, 1);
        private int telegraphTicks = 22;

        StrikeBiteGoal(JormungandrBossEntity boss) {
            super(boss, SkillState.STRIKE_BITE, "strike");
        }

        @Override
        public void start() {
            super.start();
            this.telegraphTicks = boss.scaledTelegraph(22);
            this.lockedFwd = horizontalForward(boss.getTarget());
            boss.setWantRearUp(true);
            boss.setHeadAnimationLocked(true);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, int t) {
            float sMult = JormungandrConfig.get().visualScaleMultiplier;
            double reach = 15.0 * sMult;
            if (t < telegraphTicks) {
                if (t < telegraphTicks - 6) {
                    faceTarget(target, 6.0f);
                    this.lockedFwd = horizontalForward(target);
                }
                if (t % 2 == 0) {
                    spawnLineTelegraph(level, boss.position(), lockedFwd, reach, 1.5 * sMult);
                }
                return;
            }
            if (t == telegraphTicks) {
                boss.setWantRearUp(false);
                boss.playScaledSound(level, ModSounds.JORMUNGANDR_STRIKE, 2.0f, 0.78f);
                ModNetworking.sendShakeNearby(level, boss.position(), 36.0 * sMult, 1.6f, 12);
                // Phóng đầu lao thẳng theo đường thẳng báo trước
                boss.setDeltaMovement(lockedFwd.x * 1.45, 0.12, lockedFwd.z * 1.45);
                boss.hurtMarked = true;

                float dmg = boss.scaledDamage(JormungandrConfig.get().strikeBiteDamage);
                double halfW = 2.1 * sMult;
                Set<UUID> hit = new HashSet<>();
                for (double d = 1.5 * sMult; d <= reach; d += 2.0 * sMult) {
                    Vec3 p = boss.position().add(lockedFwd.scale(d));
                    level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 1.4 * sMult, p.z, 4, 0.5, 0.4, 0.5, 0.05);
                    AABB box = new AABB(p.x - halfW, p.y - 1.0, p.z - halfW, p.x + halfW, p.y + 4.2 * sMult, p.z + halfW);
                    for (Player pl : level.getEntitiesOfClass(Player.class, box,
                            v -> v.isAlive() && !v.isCreative() && !v.isSpectator())) {
                        if (hit.add(pl.getUUID())) {
                            pl.hurt(level.damageSources().mobAttack(boss), dmg);
                            applyVenom(pl, 120);
                            pl.setDeltaMovement(pl.getDeltaMovement().add(lockedFwd.x * 1.1, 0.45, lockedFwd.z * 1.1));
                            pl.hurtMarked = true;
                        }
                    }
                }
            }
            if (t >= telegraphTicks + 14) {
                boss.setSkillCooldown(SkillState.STRIKE_BITE, JormungandrConfig.get().strikeBiteCooldown);
                completeStandardRest();
            }
        }
    }

    // =========================================================================
    // 2. VENOM SPIT — Nhổ 3 cầu độc parabol tạo vũng độc nhỏ (bán kính 2, 4.5s, Poison II)
    // =========================================================================
    private static final class VenomSpitGoal extends BaseJormSkillGoal {
        private int fireTick = 18;

        VenomSpitGoal(JormungandrBossEntity boss) {
            super(boss, SkillState.VENOM_SPIT, "spit");
        }

        @Override
        public void start() {
            super.start();
            this.fireTick = boss.scaledTelegraph(18);
            boss.setWantRearUp(true);
            boss.setHeadAnimationLocked(true);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, int t) {
            faceTarget(target, 6.0f);
            if (t == fireTick) {
                JormungandrConfig cfg = JormungandrConfig.get();
                Vec3 headPos = boss.getMouthWorldPos();
                Vec3 targetPos = (target != null && target.isAlive())
                        ? target.position().add(0.0, 0.8, 0.0)
                        : headPos.add(horizontalForward(null).scale(16.0));
                Vec3 diff = targetPos.subtract(headPos);
                double horiz = Math.max(4.0, Math.hypot(diff.x, diff.z));
                double flightTicks = Mth.clamp(horiz / 0.85, 16.0, 40.0);
                Vec3 baseVel = new Vec3(
                        diff.x / flightTicks,
                        (diff.y + 0.5 * 0.055 * flightTicks * flightTicks) / flightTicks,
                        diff.z / flightTicks
                );

                boss.playScaledSound(level, ModSounds.JORMUNGANDR_SPIT, 1.9f, 0.85f);
                float dmg = boss.scaledDamage(cfg.venomSpitDamage);
                float puddleDmg = boss.scaledDamage(cfg.venomPuddleTickDamage);
                for (int i = -1; i <= 1; i++) {
                    Vec3 spread = rotateY(new Vec3(baseVel.x, 0, baseVel.z), Math.toRadians(i * 11.0));
                    Vec3 vel = new Vec3(spread.x, baseVel.y + (i == 0 ? 0.0 : 0.05), spread.z);
                    VenomProjectileEntity.spawn(level, boss, headPos, vel, dmg, puddleDmg, boss.isEnragedVisual());
                }
            }
            if (t >= fireTick + 14) {
                boss.setSkillCooldown(SkillState.VENOM_SPIT, JormungandrConfig.get().venomSpitCooldown);
                completeStandardRest();
            }
        }
    }

    // =========================================================================
    // 3. TAIL SLAM — Quật đuôi quét hình quạt rộng phía sau, hất tung, đòn nặng (22 / P2 30.8)
    // =========================================================================
    private static final class TailSlamGoal extends BaseJormSkillGoal {
        private Vec3 tailOrigin = Vec3.ZERO;
        private Vec3 sweepDir = new Vec3(0, 0, -1);
        private int slamTick = 24;

        TailSlamGoal(JormungandrBossEntity boss) {
            super(boss, SkillState.TAIL_SLAM, "tail_slam");
        }

        @Override
        public void start() {
            super.start();
            this.slamTick = boss.scaledTelegraph(24);
            this.tailOrigin = boss.getSerpentPath().getTailTipPos();
            LivingEntity t = boss.getTarget();
            Vec3 toT = (t != null) ? t.position().subtract(this.tailOrigin) : boss.getLookAngle().scale(-1.0);
            Vec3 h = new Vec3(toT.x, 0.0, toT.z);
            this.sweepDir = h.lengthSqr() > 1e-4 ? h.normalize() : new Vec3(0, 0, -1);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, int t) {
            float sMult = JormungandrConfig.get().visualScaleMultiplier;
            double range = 13.0 * sMult;
            if (t < slamTick) {
                if (t % 3 == 0) {
                    DustParticleOptions dust = boss.isEnragedVisual() ? TELEGRAPH_ENRAGED : TELEGRAPH_VIOLET;
                    for (int r = -3; r <= 3; r++) {
                        Vec3 ray = rotateY(sweepDir, Math.toRadians(r * 22.0));
                        for (double d = 3.0 * sMult; d <= range * 0.92; d += 3.0 * sMult) {
                            Vec3 p = tailOrigin.add(ray.scale(d));
                            level.sendParticles(dust, p.x, tailOrigin.y + 0.25, p.z, 1, 0.18, 0.04, 0.18, 0.0);
                        }
                    }
                }
                return;
            }
            if (t == slamTick) {
                float dmg = boss.scaledDamage(JormungandrConfig.get().tailSlamDamage);
                boss.playScaledSound(level, ModSounds.JORMUNGANDR_SLAM, 2.2f, 0.65f);
                ModNetworking.sendShakeNearby(level, tailOrigin, 44.0 * sMult, 2.2f, 16);
                ShockwaveRingEntity.spawn(level, tailOrigin, (float) range, JormungandrBossEntity.COLOR_SERPENT_VIOLET, 14);

                double cosLim = Math.cos(Math.toRadians(80.0));
                AABB box = new AABB(tailOrigin.x - range, tailOrigin.y - 2.5, tailOrigin.z - range,
                        tailOrigin.x + range, tailOrigin.y + 6.0 * sMult, tailOrigin.z + range);
                for (Player pl : level.getEntitiesOfClass(Player.class, box,
                        v -> v.isAlive() && !v.isCreative() && !v.isSpectator())) {
                    Vec3 diff = new Vec3(pl.getX() - tailOrigin.x, 0.0, pl.getZ() - tailOrigin.z);
                    double dist = diff.length();
                    if (dist > range) continue;
                    if (dist > 1.5 && diff.normalize().dot(sweepDir) < cosLim) continue;
                    pl.hurt(level.damageSources().mobAttack(boss), dmg);
                    Vec3 push = dist > 0.2 ? diff.normalize() : sweepDir;
                    pl.setDeltaMovement(pl.getDeltaMovement().add(push.x * 1.4, 0.92, push.z * 1.4));
                    pl.hurtMarked = true;
                }
            }
            if (t >= slamTick + 14) {
                boss.setSkillCooldown(SkillState.TAIL_SLAM, JormungandrConfig.get().tailSlamCooldown);
                completeStandardRest();
            }
        }
    }

    // =========================================================================
    // 4. CONSTRICT COIL — Thân cuộn thành vòng bán kính 8 block quanh người chơi, siết dần 2.5s
    // =========================================================================
    private static final class ConstrictCoilGoal extends BaseJormSkillGoal {
        private Vec3 center = Vec3.ZERO;
        private int telegraphTicks = 18;
        private static final int CONSTRICT_TICKS = 50; // 2.5 giây

        ConstrictCoilGoal(JormungandrBossEntity boss) {
            super(boss, SkillState.CONSTRICT_COIL, "constrict");
        }

        @Override
        public void start() {
            super.start();
            this.telegraphTicks = boss.scaledTelegraph(18);
            LivingEntity t = boss.getTarget();
            this.center = (t != null) ? t.position() : boss.position();
            if (boss.level() instanceof ServerLevel sl) {
                float r = (float) JormungandrConfig.get().getScaledConstrictRadius();
                MagicCircleEntity.spawn(sl, boss, center, r, telegraphTicks + CONSTRICT_TICKS,
                        JormungandrBossEntity.COLOR_SERPENT_VIOLET, 0.0f, false, false);
            }
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, int t) {
            JormungandrConfig cfg = JormungandrConfig.get();
            float sMult = cfg.visualScaleMultiplier;
            float startR = (float) cfg.getScaledConstrictRadius();
            if (t < telegraphTicks) {
                faceTarget(target, 5.0f);
                return;
            }
            int elapsed = t - telegraphTicks;
            if (elapsed <= CONSTRICT_TICKS) {
                float frac = elapsed / (float) CONSTRICT_TICKS;
                float curR = Mth.lerp(frac, startR, 3.0f * sMult);
                // Thân rắn thực tế cuộn & siết quanh `center` qua SerpentPath!
                boss.getSerpentPath().setConstrictRing(center, curR);
                if (elapsed % 8 == 0) {
                    boss.playScaledSound(level, ModSounds.JORMUNGANDR_AMBIENT, 1.5f, 0.72f + frac * 0.25f);
                }
            }
            if (elapsed == CONSTRICT_TICKS) {
                float dmg = boss.scaledDamage(cfg.constrictDamage);
                boss.playScaledSound(level, ModSounds.JORMUNGANDR_SLAM, 2.0f, 0.78f);
                ModNetworking.sendShakeNearby(level, center, 38.0 * sMult, 1.8f, 14);
                ShockwaveRingEntity.spawn(level, center, 5.5f * sMult, JormungandrBossEntity.COLOR_HOOD_LAVENDER, 14);

                double hitR = 3.8 * sMult;
                AABB box = new AABB(center.x - hitR - 0.5, center.y - 2.0, center.z - hitR - 0.5,
                        center.x + hitR + 0.5, center.y + 4.0 * sMult, center.z + hitR + 0.5);
                for (Player pl : level.getEntitiesOfClass(Player.class, box,
                        v -> v.isAlive() && !v.isCreative() && !v.isSpectator())) {
                    if (Math.hypot(pl.getX() - center.x, pl.getZ() - center.z) <= hitR) {
                        pl.hurt(level.damageSources().mobAttack(boss), dmg);
                        applyVenom(pl, 100);
                        pl.setDeltaMovement(pl.getDeltaMovement().add(0.0, 0.75, 0.0));
                        pl.hurtMarked = true;
                    }
                }
                boss.getSerpentPath().setOverrideNone();
            }
            if (elapsed >= CONSTRICT_TICKS + 12) {
                boss.setSkillCooldown(SkillState.CONSTRICT_COIL, JormungandrConfig.get().constrictCooldown);
                completeStandardRest();
            }
        }
    }

    // =========================================================================
    // 5. HOOD FLARE — Xòe mang cổ tạo sóng đẩy lùi quanh đầu + Slowness I (2s)
    // =========================================================================
    private static final class HoodFlareGoal extends BaseJormSkillGoal {
        private int flareTick = 16;

        HoodFlareGoal(JormungandrBossEntity boss) {
            super(boss, SkillState.HOOD_FLARE, "flare");
        }

        @Override
        public void start() {
            super.start();
            this.flareTick = boss.scaledTelegraph(16);
            boss.setWantRearUp(true);
            boss.setHeadAnimationLocked(true);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, int t) {
            faceTarget(target, 6.0f);
            if (t == flareTick) {
                float sMult = JormungandrConfig.get().visualScaleMultiplier;
                Vec3 origin = boss.position();
                float dmg = boss.scaledDamage(JormungandrConfig.get().hoodFlareDamage);
                boss.playScaledSound(level, ModSounds.JORMUNGANDR_FLARE, 2.2f, 0.85f);
                ModNetworking.sendShakeNearby(level, origin, 42.0 * sMult, 1.5f, 12);
                ShockwaveRingEntity.spawn(level, origin.add(0, 1.8 * sMult, 0), 12.0f * sMult, JormungandrBossEntity.COLOR_HOOD_LAVENDER, 14);

                double radius = 11.5 * sMult;
                AABB box = new AABB(origin.x - radius, origin.y - 2.0, origin.z - radius,
                        origin.x + radius, origin.y + 8.0 * sMult, origin.z + radius);
                for (Player pl : level.getEntitiesOfClass(Player.class, box,
                        v -> v.isAlive() && !v.isCreative() && !v.isSpectator())) {
                    double dist = Math.hypot(pl.getX() - origin.x, pl.getZ() - origin.z);
                    if (dist > radius) continue;
                    pl.hurt(level.damageSources().mobAttack(boss), dmg);
                    pl.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0)); // Slowness I 2 giây
                    Vec3 away = new Vec3(pl.getX() - origin.x, 0.0, pl.getZ() - origin.z);
                    Vec3 dir = away.lengthSqr() > 1e-4 ? away.normalize() : new Vec3(0, 0, 1);
                    pl.setDeltaMovement(pl.getDeltaMovement().add(dir.x * 1.65, 0.48, dir.z * 1.65));
                    pl.hurtMarked = true;
                }
            }
            if (t >= flareTick + 14) {
                boss.setSkillCooldown(SkillState.HOOD_FLARE, JormungandrConfig.get().hoodFlareCooldown);
                completeStandardRest();
            }
        }
    }

    // =========================================================================
    // 6. BURROW — Lặn xuống đất (không phá khối), di chuyển ngầm 1.5-2s có vệt bụi -> trồi lên đòn nặng
    // =========================================================================
    private static final class BurrowGoal extends BaseJormSkillGoal {
        private static final int SUBMERGE_TICK = 16;
        private static final int WARN_TICK = 46;    // Sau 1.5s di chuyển ngầm -> khóa điểm & cảnh báo 1.0s (20 ticks)
        private static final int EMERGE_TICK = 66;
        private Vec3 emergePos = Vec3.ZERO;

        BurrowGoal(JormungandrBossEntity boss) {
            super(boss, SkillState.BURROW, "burrow");
        }

        @Override
        public void start() {
            super.start();
            this.emergePos = boss.position();
            boss.setWantRearUp(false);
            if (boss.level() instanceof ServerLevel sl) {
                boss.playScaledSound(sl, ModSounds.JORMUNGANDR_BURROW, 2.0f, 0.65f);
            }
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, int t) {
            if (t == SUBMERGE_TICK) {
                boss.setBurrowed(true);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                        boss.getX(), boss.getY() + 0.5, boss.getZ(), 28, 1.5, 0.6, 1.5, 0.14);
            } else if (t > SUBMERGE_TICK && t < WARN_TICK) {
                // Di chuyển ngầm mượt về phía mục tiêu kèm vệt nứt/bụi trên mặt đất
                if (target != null && target.isAlive()) {
                    Vec3 diff = target.position().subtract(boss.position());
                    Vec3 h = new Vec3(diff.x, 0.0, diff.z);
                    if (h.length() > 1.2) {
                        Vec3 step = h.normalize().scale(0.85);
                        Vec3 next = groundAt(level, boss.getX() + step.x, boss.getZ() + step.z, boss.getY());
                        boss.setPos(next.x, next.y, next.z);
                    }
                    this.emergePos = AbstractGeoMythicBoss.findSafeLandingPosition(level, target.position(), boss.position(), 5);
                }
                if (t % 3 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()),
                            boss.getX(), boss.getY() + 0.2, boss.getZ(), 10, 1.1, 0.2, 1.1, 0.12);
                }
            } else if (t == WARN_TICK) {
                float sMult = JormungandrConfig.get().visualScaleMultiplier;
                // Khóa vị trí trồi lên + hiện vòng cảnh báo trước đúng 1.0 giây (20 ticks)
                MagicCircleEntity.spawn(level, boss, emergePos, 6.0f * sMult, EMERGE_TICK - WARN_TICK,
                        JormungandrBossEntity.COLOR_CRIMSON_EYE, 0.0f, false, false);
                boss.playScaledSound(level, ModSounds.JORMUNGANDR_BURROW, 1.8f, 0.85f);
            } else if (t > WARN_TICK && t < EMERGE_TICK) {
                if (t % 3 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                            emergePos.x, emergePos.y + 0.2, emergePos.z, 10, 2.4, 0.25, 2.4, 0.14);
                }
            } else if (t == EMERGE_TICK) {
                float sMult = JormungandrConfig.get().visualScaleMultiplier;
                boss.setBurrowed(false);
                boss.teleportTo(emergePos.x, emergePos.y, emergePos.z);
                if (target != null) boss.facePositionImmediately(target.position());
                boss.resetPathAfterSnap(emergePos);
                boss.setWantRearUp(true);
                boss.playActionAnim("emerge");
                boss.playScaledSound(level, ModSounds.JORMUNGANDR_SLAM, 2.4f, 0.62f);
                ModNetworking.sendShakeNearby(level, emergePos, 52.0 * sMult, 2.6f, 20);
                ShockwaveRingEntity.spawn(level, emergePos, 11.0f * sMult, JormungandrBossEntity.COLOR_SERPENT_VIOLET, 16);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()),
                        emergePos.x, emergePos.y + 1.0, emergePos.z, 40, 2.8 * sMult, 1.8 * sMult, 2.8 * sMult, 0.24);

                float dmg = boss.scaledDamage(JormungandrConfig.get().burrowEruptionDamage);
                double hitR = 6.2 * sMult;
                AABB box = new AABB(emergePos.x - hitR - 0.4, emergePos.y - 2.0, emergePos.z - hitR - 0.4,
                        emergePos.x + hitR + 0.4, emergePos.y + 6.0 * sMult, emergePos.z + hitR + 0.4);
                for (Player pl : level.getEntitiesOfClass(Player.class, box,
                        v -> v.isAlive() && !v.isCreative() && !v.isSpectator())) {
                    if (Math.hypot(pl.getX() - emergePos.x, pl.getZ() - emergePos.z) <= hitR) {
                        pl.hurt(level.damageSources().mobAttack(boss), dmg);
                        applyVenom(pl, 120);
                        pl.setDeltaMovement(pl.getDeltaMovement().add(0.0, 1.05, 0.0));
                        pl.hurtMarked = true;
                    }
                }
            }
            if (t >= EMERGE_TICK + 18) {
                boss.setSkillCooldown(SkillState.BURROW, JormungandrConfig.get().burrowCooldown);
                completeStandardRest();
            }
        }
    }

    // =========================================================================
    // 7. TOXIC NOVA (Phase 2) — Sương độc mở rộng thành vòng tròn từ đầu (nhảy để né)
    // =========================================================================
    private static final class ToxicNovaGoal extends BaseJormSkillGoal {
        private int novaTick = 18;
        private final Set<UUID> hit = new HashSet<>();

        ToxicNovaGoal(JormungandrBossEntity boss) {
            super(boss, SkillState.TOXIC_NOVA, "nova");
        }

        @Override
        public void start() {
            super.start();
            this.novaTick = boss.scaledTelegraph(18);
            this.hit.clear();
            boss.setWantRearUp(true);
            boss.setHeadAnimationLocked(true);
            if (boss.level() instanceof ServerLevel sl) {
                float sMult = JormungandrConfig.get().visualScaleMultiplier;
                MagicCircleEntity.spawn(sl, boss, boss.position(), 16.0f * sMult, novaTick,
                        JormungandrBossEntity.COLOR_VENOM_GREEN, 0.0f, false, false);
            }
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, int t) {
            float sMult = JormungandrConfig.get().visualScaleMultiplier;
            if (t == novaTick) {
                boss.playScaledSound(level, ModSounds.JORMUNGANDR_SPIT, 2.2f, 0.60f);
                ShockwaveRingEntity.spawn(level, boss.position(), 18.0f * sMult, JormungandrBossEntity.COLOR_VENOM_GREEN, 20);
            }
            if (t >= novaTick && t <= novaTick + 18) {
                float r = (2.0f + ((t - novaTick) / 18.0f) * 16.0f) * sMult;
                float dmg = boss.scaledDamage(JormungandrConfig.get().toxicNovaDamage);
                Vec3 c = boss.position();
                AABB box = new AABB(c.x - r - 1.5, c.y - 1.0, c.z - r - 1.5, c.x + r + 1.5, c.y + 1.65, c.z + r + 1.5);
                for (Player pl : level.getEntitiesOfClass(Player.class, box,
                        v -> v.isAlive() && !v.isCreative() && !v.isSpectator())) {
                    double dist = Math.hypot(pl.getX() - c.x, pl.getZ() - c.z);
                    // Sóng tầm thấp (<= 1.45 block trên mặt đất) -> người chơi NHẢY ĐÚNG LÚC sẽ né hoàn toàn!
                    if (Math.abs(dist - r) <= 1.8 && pl.getY() <= c.y + 1.15 && hit.add(pl.getUUID())) {
                        pl.hurt(level.damageSources().mobAttack(boss), dmg);
                        applyVenom(pl, 80); // Poison II ngắn (4 giây)
                    }
                }
            }
            if (t >= novaTick + 24) {
                boss.setSkillCooldown(SkillState.TOXIC_NOVA, JormungandrConfig.get().toxicNovaCooldown);
                completeStandardRest();
            }
        }
    }

    // =========================================================================
    // 8. RAPID STRIKE (Phase 2) — Chuỗi 3 đòn Strike Bite liên tiếp, mỗi đòn có telegraph ngắn riêng
    // =========================================================================
    private static final class RapidStrikeGoal extends BaseJormSkillGoal {
        private static final int[] STRIKE_TICKS = { 16, 36, 56 };
        private Vec3 strikeFwd = new Vec3(0, 0, 1);

        RapidStrikeGoal(JormungandrBossEntity boss) {
            super(boss, SkillState.RAPID_STRIKE, "rapid_strike");
        }

        @Override
        public void start() {
            super.start();
            boss.setWantRearUp(true);
            boss.setHeadAnimationLocked(true);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, int t) {
            faceTarget(target, 8.0f);
            this.strikeFwd = horizontalForward(target);

            float sMult = JormungandrConfig.get().visualScaleMultiplier;
            double reach = 14.0 * sMult;
            for (int st : STRIKE_TICKS) {
                if (t == st - 10 || t == st - 5) {
                    spawnLineTelegraph(level, boss.position(), strikeFwd, reach, 1.8 * sMult);
                }
                if (t == st) {
                    float dmg = boss.scaledDamage(JormungandrConfig.get().rapidStrikeDamage);
                    boss.playScaledSound(level, ModSounds.JORMUNGANDR_STRIKE, 2.0f, 0.90f);
                    boss.setDeltaMovement(strikeFwd.x * 1.15, 0.1, strikeFwd.z * 1.15);
                    boss.hurtMarked = true;

                    double halfW = 2.0 * sMult;
                    Set<UUID> hit = new HashSet<>();
                    for (double d = 1.5 * sMult; d <= reach; d += 2.2 * sMult) {
                        Vec3 p = boss.position().add(strikeFwd.scale(d));
                        AABB box = new AABB(p.x - halfW, p.y - 1.0, p.z - halfW, p.x + halfW, p.y + 4.0 * sMult, p.z + halfW);
                        for (Player pl : level.getEntitiesOfClass(Player.class, box,
                                v -> v.isAlive() && !v.isCreative() && !v.isSpectator())) {
                            if (hit.add(pl.getUUID())) {
                                pl.hurt(level.damageSources().mobAttack(boss), dmg);
                                applyVenom(pl, 100);
                            }
                        }
                    }
                }
            }
            if (t >= 70) {
                boss.setSkillCooldown(SkillState.RAPID_STRIKE, JormungandrConfig.get().rapidStrikeCooldown);
                completeStandardRest();
            }
        }
    }

    // =========================================================================
    // 9. VENOM RAIN (Phase 2) — Mưa cầu độc rơi quanh người chơi, có vòng cảnh báo dưới đất
    // =========================================================================
    private static final class VenomRainGoal extends BaseJormSkillGoal {
        private int startTick = 16;

        VenomRainGoal(JormungandrBossEntity boss) {
            super(boss, SkillState.VENOM_RAIN, "rain");
        }

        @Override
        public void start() {
            super.start();
            this.startTick = boss.scaledTelegraph(16);
            boss.setWantRearUp(true);
            boss.setHeadAnimationLocked(true);
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, int t) {
            faceTarget(target, 5.0f);
            if (t == startTick || t == startTick + 16) {
                JormungandrConfig cfg = JormungandrConfig.get();
                Vec3 center = (target != null && target.isAlive()) ? target.position() : boss.position();
                float dmg = boss.scaledDamage(cfg.venomRainDamage);
                float puddleDmg = boss.scaledDamage(cfg.venomPuddleTickDamage);
                boss.playScaledSound(level, ModSounds.JORMUNGANDR_SPIT, 2.1f, 0.72f);

                // 2 đợt × 4 cầu = 8 cầu tối đa -> MSPT rất nhẹ
                for (int i = 0; i < 4; i++) {
                    double ang = (Math.PI * 2.0 * i / 4.0) + level.random.nextDouble() * 0.8;
                    double r = (i == 0) ? 0.6 : (3.0 + level.random.nextDouble() * 7.5);
                    Vec3 g = groundAt(level, center.x + Math.cos(ang) * r, center.z + Math.sin(ang) * r, center.y);
                    MagicCircleEntity.spawn(level, boss, g, 2.2f, 20,
                            JormungandrBossEntity.COLOR_VENOM_GREEN, 0.0f, false, false);
                    Vec3 sky = g.add(0.0, 14.0, 0.0);
                    VenomProjectileEntity.spawn(level, boss, sky, new Vec3(0.0, -0.25, 0.0), dmg, puddleDmg, true);
                }
            }
            if (t >= startTick + 32) {
                boss.setSkillCooldown(SkillState.VENOM_RAIN, JormungandrConfig.get().venomRainCooldown);
                completeStandardRest();
            }
        }
    }

    // =========================================================================
    // 10. ULTIMATE "OUROBOROS RING" (Phase 2, HP < 20%, tối đa mỗi 60s)
    //     Bản ĐẦY ĐỦ: Lặn -> trồi tạo vòng bán kính 14 block CÓ 1 LỖ HỔNG >= 6.5 block quay chậm,
    //     thu nhỏ dần trong 5.5 giây -> chốt phóng đầu vào tâm (đòn nặng + vòng cảnh báo)!
    // =========================================================================
    private static final class OuroborosRingGoal extends BaseJormSkillGoal {
        private static final int BURROW_TICKS = 16;
        private static final int RING_DURATION = 110;  // 5.5 giây vòng thu nhỏ dần + lỗ hổng quay chậm
        private static final int CENTER_WARN_TICKS = 22;
        private Vec3 ringCenter = Vec3.ZERO;
        private float gapAngleRad = 0.0f;

        OuroborosRingGoal(JormungandrBossEntity boss) {
            super(boss, SkillState.OUROBOROS_RING, "ouroboros");
        }

        @Override
        public void start() {
            super.start();
            LivingEntity t = boss.getTarget();
            this.ringCenter = (t != null) ? t.position() : boss.position();
            this.gapAngleRad = boss.getRandom().nextFloat() * Mth.TWO_PI;
            boss.setHeadAnimationLocked(true);
            boss.setBurrowed(true);
            if (boss.level() instanceof ServerLevel sl) {
                boss.playScaledSound(sl, ModSounds.JORMUNGANDR_ROAR, 2.5f, 0.58f);
                ModNetworking.sendShakeNearby(sl, ringCenter, 80.0, 2.2f, 20);
            }
        }

        @Override
        protected void onSkillTick(ServerLevel level, LivingEntity target, int t) {
            JormungandrConfig cfg = JormungandrConfig.get();
            float sMult = cfg.visualScaleMultiplier;
            float startR = (float) cfg.getScaledOuroborosRadius();
            float gapW = (float) cfg.getScaledOuroborosGapWidth();

            if (t < BURROW_TICKS) {
                return;
            }
            if (t == BURROW_TICKS) {
                boss.setBurrowed(false);
                boss.playScaledSound(level, ModSounds.JORMUNGANDR_SLAM, 2.4f, 0.62f);
                ShockwaveRingEntity.spawn(level, ringCenter, startR, JormungandrBossEntity.COLOR_ENRAGED_WHITE_VIOLET, 22);
            }

            int ringT = t - BURROW_TICKS;
            if (ringT >= 0 && ringT <= RING_DURATION) {
                float frac = ringT / (float) RING_DURATION;
                // Thu nhỏ dần đồng bộ theo visualScaleMultiplier; lỗ hổng rộng >= 6.5 * sMult quay chậm
                float curR = Mth.lerp(frac, startR, 5.5f * sMult);
                this.gapAngleRad = (this.gapAngleRad + 0.038f) % Mth.TWO_PI;
                boss.getSerpentPath().setOuroborosRing(ringCenter, curR, this.gapAngleRad, gapW);

                // Gắn vị trí thực thể đầu về đầu vòng để culling & sound luôn đúng tâm giao tranh
                Vec3 headNode = boss.getSerpentPath().getServerNode(0);
                boss.setPos(headNode.x, headNode.y, headNode.z);
                boss.facePositionImmediately(ringCenter);

                // Dấu hiệu xanh sáng rõ trên mặt đất ở LỖ HỔNG giúp người chơi nhìn rõ đường thoát!
                if (ringT % 4 == 0) {
                    double gx = ringCenter.x + Math.cos(gapAngleRad) * curR;
                    double gz = ringCenter.z + Math.sin(gapAngleRad) * curR;
                    level.sendParticles(ParticleTypes.END_ROD, gx, ringCenter.y + 0.6, gz, 5, 1.2, 0.4, 1.2, 0.02);
                }
                if (ringT == RING_DURATION - CENTER_WARN_TICKS) {
                    MagicCircleEntity.spawn(level, boss, ringCenter, 6.2f * sMult, CENTER_WARN_TICKS,
                            JormungandrBossEntity.COLOR_CRIMSON_EYE, 0.0f, false, false);
                }
                return;
            }

            if (ringT == RING_DURATION + 1) {
                // Đòn chốt cuối: phóng đầu thẳng vào tâm vòng (đòn nặng 30.8!)
                boss.getSerpentPath().setOverrideNone();
                boss.teleportTo(ringCenter.x, ringCenter.y, ringCenter.z);
                boss.resetPathAfterSnap(ringCenter);
                boss.playActionAnim("strike");
                boss.playScaledSound(level, ModSounds.JORMUNGANDR_STRIKE, 2.6f, 0.58f);
                ModNetworking.sendShakeNearby(level, ringCenter, 84.0 * sMult, 3.2f, 26);
                ShockwaveRingEntity.spawn(level, ringCenter, 12.0f * sMult, JormungandrBossEntity.COLOR_CRIMSON_EYE, 18);

                float dmg = boss.scaledDamage(cfg.ouroborosCenterStrikeDamage);
                double hitR = 6.2 * sMult;
                AABB box = new AABB(ringCenter.x - hitR - 0.5, ringCenter.y - 2.0, ringCenter.z - hitR - 0.5,
                        ringCenter.x + hitR + 0.5, ringCenter.y + 5.5 * sMult, ringCenter.z + hitR + 0.5);
                for (Player pl : level.getEntitiesOfClass(Player.class, box,
                        v -> v.isAlive() && !v.isCreative() && !v.isSpectator())) {
                    if (Math.hypot(pl.getX() - ringCenter.x, pl.getZ() - ringCenter.z) <= hitR) {
                        pl.hurt(level.damageSources().mobAttack(boss), dmg);
                        applyVenom(pl, 160);
                        pl.setDeltaMovement(pl.getDeltaMovement().add(0.0, 1.0, 0.0));
                        pl.hurtMarked = true;
                    }
                }
            }

            if (ringT >= RING_DURATION + 18) {
                boss.setSkillCooldown(SkillState.OUROBOROS_RING, cfg.ultimateCooldownTicks);
                completeStandardRest();
            }
        }
    }

    // =========================================================================
    // BỘ CHỌN KỸ NĂNG (KHOẢNG CÁCH, TRỌNG SỐ, COOLDOWN, KHÔNG LẶP 3 LẦN LIÊN TIẾP)
    // =========================================================================
    private static final class JormungandrSkillSelectorGoal extends Goal {
        private final JormungandrBossEntity boss;
        private record Candidate(SkillState state, int weight) {}

        JormungandrSkillSelectorGoal(JormungandrBossEntity boss) {
            this.boss = boss;
        }

        @Override
        public boolean canUse() {
            if (!boss.isAlive() || boss.isRotationLocked()) return false;
            if (boss.getSkillState() != SkillState.IDLE) return false;
            if (boss.getGlobalSkillRestTicks() > 0) return false;
            LivingEntity t = boss.getTarget();
            return t != null && t.isAlive() && boss.distanceTo(t) <= JormungandrConfig.get().detectionRadius;
        }

        @Override
        public void start() {
            LivingEntity target = boss.getTarget();
            if (target == null) return;
            double dist = boss.distanceTo(target);
            boolean p2 = boss.getSyncedPhase() >= 2 || boss.hasEnteredPhase2();
            JormungandrConfig cfg = JormungandrConfig.get();

            // Ưu tiên Ultimate "Ouroboros Ring" khi Phase 2 + HP < 20% + hết hồi chiêu 60s
            if (p2 && (boss.getHealth() / Math.max(1.0f, boss.getMaxHealth())) < cfg.ultimateHpThreshold
                    && boss.isSkillReady(SkillState.OUROBOROS_RING) && dist <= 32.0) {
                boss.recordUsedSkill(SkillState.OUROBOROS_RING);
                boss.setSkillState(SkillState.OUROBOROS_RING);
                return;
            }

            float sMult = cfg.visualScaleMultiplier;
            List<Candidate> pool = new ArrayList<>();
            if (dist <= 15.0 * sMult) addIfReady(pool, SkillState.STRIKE_BITE, 34);
            if (dist <= 11.0 * sMult) addIfReady(pool, SkillState.HOOD_FLARE, 24);
            if (dist >= 7.0 * sMult && dist <= 34.0 * sMult) addIfReady(pool, SkillState.VENOM_SPIT, 28);
            if (dist <= 18.0 * sMult) addIfReady(pool, SkillState.CONSTRICT_COIL, 22);
            if (dist >= 10.0 * sMult && dist <= 36.0 * sMult) addIfReady(pool, SkillState.BURROW, 22);
            if (boss.getSerpentPath().getTailTipPos().distanceTo(target.position()) <= 13.0 * sMult) {
                addIfReady(pool, SkillState.TAIL_SLAM, 36);
            }

            if (p2) {
                if (dist <= 15.5 * sMult) addIfReady(pool, SkillState.RAPID_STRIKE, 38);
                if (dist <= 16.5 * sMult) addIfReady(pool, SkillState.TOXIC_NOVA, 32);
                if (dist >= 8.0 * sMult && dist <= 38.0 * sMult) addIfReady(pool, SkillState.VENOM_RAIN, 30);
            }

            if (pool.isEmpty()) return;
            int sum = 0;
            for (Candidate c : pool) sum += c.weight;
            int roll = boss.getRandom().nextInt(sum);
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
    // DIRECT-STEERING CHASE GOAL (HẠ ĐẦU TRƯỜN KHI ĐUỔI, NGẨNG CỔ KHI ĐỨNG CHỜ)
    // =========================================================================
    private static final class JormungandrDirectChaseGoal extends Goal {
        private final JormungandrBossEntity boss;

        JormungandrDirectChaseGoal(JormungandrBossEntity boss) {
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
        public boolean canContinueToUse() { return canUse(); }

        @Override
        public void tick() {
            LivingEntity target = boss.getTarget();
            if (target == null || !target.isAlive()) return;
            double dist = boss.distanceTo(target);
            if (dist > 9.0) {
                boss.setWantRearUp(false); // Hạ đầu sát đất trườn nhanh
                boss.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.15);
            } else {
                boss.setWantRearUp(true); // Dừng ngẩng cổ hổ mang đe dọa & chờ skill
                boss.smoothRotateToward(target.position().subtract(boss.position()),
                        6.0f, (float) JormungandrConfig.get().phase2TurnSpeedMultiplier);
            }
        }
    }
}

