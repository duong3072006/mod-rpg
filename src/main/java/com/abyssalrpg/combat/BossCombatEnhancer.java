package com.abyssalrpg.combat;

import com.abyssalrpg.entity.AbstractAbyssalBoss;
import com.abyssalrpg.entity.mythic.AbstractGeoMythicBoss;
import com.abyssalrpg.entity.mythic.CerberusBossEntity;
import com.abyssalrpg.entity.mythic.CinderDragonBossEntity;
import com.abyssalrpg.entity.mythic.CyclopsWardenBossEntity;
import com.abyssalrpg.entity.mythic.FenrirBossEntity;
import com.abyssalrpg.entity.mythic.HydraBossEntity;
import com.abyssalrpg.entity.mythic.JormungandrBossEntity;
import com.abyssalrpg.entity.mythic.LeviathanBossEntity;
import com.abyssalrpg.entity.mythic.NightmareKnightBossEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hệ thống nâng cấp chiến đấu toàn diện phía server áp dụng đồng bộ cho TẤT CẢ 13 Boss (`AbstractAbyssalBoss`):
 * 1. **Xuyên 36% Giáp (`0.36F`)** trên mọi đòn đánh từ Boss (cận chiến, AoE, đạn/tia, gai xương, vệt lửa, sét đánh),
 *    hoạt động chuẩn trên cả giáp Vanilla lẫn bộ giáp Cinder Dragon uncapped.
 * 2. **Gắn Debuff theo hệ nguyên tố của từng Boss** lên người chơi trúng đòn (có cooldown nội bộ 2.0s chống spam).
 * 3. **Kỹ năng chủ động Sét Đánh có cảnh báo (Telegraphed Lightning Strike)** lên vị trí người chơi (1 tia P1, 2–3 tia liên hoàn P2).
 * 4. **Kỹ năng Triệu Hồi Mob đồng hệ có giới hạn mật độ an toàn** (chống lag, không cháy nắng ban ngày, tự lao vào mục tiêu).
 */
public final class BossCombatEnhancer {
    public static final float BOSS_ARMOR_PIERCE_FRACTION = 0.36F;
    public static final String BOSS_MINION_TAG = "abyssalrpg_boss_minion";

    private static final int DEBUFF_INTERNAL_COOLDOWN_TICKS = 40;
    private static final int LIGHTNING_TELEGRAPH_TICKS = 18;
    private static final double ACTIVE_COMBAT_RANGE_SQR = 48.0 * 48.0;

    private static final ThreadLocal<AbstractAbyssalBoss> ACTIVE_BOSS_SOURCE = new ThreadLocal<>();
    private static final Map<UUID, Long> LAST_DEBUFF_TICK = new HashMap<>();
    private static final Map<UUID, BossEscalationState> BOSS_STATES = new HashMap<>();
    private static final List<PendingLightningStrike> PENDING_STRIKES = new ArrayList<>();

    private enum BossArchetype {
        INFERNO(0xFF6A1A, ParticleTypes.FLAME, List.of(
                EntityType.BLAZE, EntityType.WITHER_SKELETON, EntityType.SKELETON)),
        NIGHTMARE_VOID(0xA855F7, ParticleTypes.WITCH, List.of(
                EntityType.WITHER_SKELETON, EntityType.VEX, EntityType.SKELETON)),
        VENOM_ABYSS(0x2AF598, ParticleTypes.ITEM_SLIME, List.of(
                EntityType.CAVE_SPIDER, EntityType.BOGGED, EntityType.DROWNED)),
        OCEAN_ABYSS(0x1AE8FF, ParticleTypes.BUBBLE_POP, List.of(
                EntityType.DROWNED, EntityType.GUARDIAN, EntityType.BOGGED)),
        APEX_TEMPEST(0x5CEEFF, ParticleTypes.ELECTRIC_SPARK, List.of(
                EntityType.STRAY, EntityType.VINDICATOR, EntityType.VEX));

        final int rgb;
        final ParticleOptions debuffParticle;
        final List<EntityType<?>> minionPool;

        BossArchetype(int rgb, ParticleOptions debuffParticle, List<EntityType<?>> minionPool) {
            this.rgb = rgb;
            this.debuffParticle = debuffParticle;
            this.minionPool = minionPool;
        }

        static BossArchetype of(AbstractAbyssalBoss boss) {
            if (boss instanceof CinderDragonBossEntity
                    || boss instanceof CerberusBossEntity) {
                return INFERNO;
            }
            if (boss instanceof NightmareKnightBossEntity
                    || boss instanceof HydraBossEntity) {
                return NIGHTMARE_VOID;
            }
            if (boss instanceof LeviathanBossEntity) {
                return OCEAN_ABYSS;
            }
            if (boss instanceof JormungandrBossEntity) {
                return VENOM_ABYSS;
            }
            if (boss instanceof FenrirBossEntity || boss instanceof CyclopsWardenBossEntity) {
                return APEX_TEMPEST;
            }
            return NIGHTMARE_VOID;
        }
    }

    private static final class BossEscalationState {
        int lightningCooldown;
        int summonCooldown;
        long lastSeenTick;

        BossEscalationState(int lightningCooldown, int summonCooldown, long lastSeenTick) {
            this.lightningCooldown = lightningCooldown;
            this.summonCooldown = summonCooldown;
            this.lastSeenTick = lastSeenTick;
        }
    }

    private static final class PendingLightningStrike {
        final ServerLevel level;
        final UUID bossUuid;
        final UUID targetPlayerUuid;
        int delayBeforeTelegraph;
        int telegraphTicksLeft;
        boolean telegraphStarted;
        Vec3 strikePos;
        final float damage;
        final boolean phase2;

        PendingLightningStrike(ServerLevel level, UUID bossUuid, UUID targetPlayerUuid,
                               int delayBeforeTelegraph, float damage, boolean phase2) {
            this.level = level;
            this.bossUuid = bossUuid;
            this.targetPlayerUuid = targetPlayerUuid;
            this.delayBeforeTelegraph = delayBeforeTelegraph;
            this.telegraphTicksLeft = LIGHTNING_TELEGRAPH_TICKS;
            this.telegraphStarted = false;
            this.strikePos = Vec3.ZERO;
            this.damage = damage;
            this.phase2 = phase2;
        }
    }

    private BossCombatEnhancer() {}

    // =========================================================================
    // 1. XUYÊN 36% GIÁP CHO MỌI ĐÒN ĐÁNH CỦA TẤT CẢ 13 BOSS
    // =========================================================================

    @Nullable
    public static AbstractAbyssalBoss resolveBossAttacker(@Nullable DamageSource source) {
        AbstractAbyssalBoss active = ACTIVE_BOSS_SOURCE.get();
        if (active != null) {
            return active;
        }
        if (source == null) {
            return null;
        }
        if (source.getEntity() instanceof AbstractAbyssalBoss boss) {
            return boss;
        }
        if (source.getDirectEntity() instanceof AbstractAbyssalBoss boss) {
            return boss;
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof AbstractAbyssalBoss boss) {
            return boss;
        }
        return null;
    }

    public static float getBossArmorPierceFraction(@Nullable LivingEntity victim, @Nullable DamageSource source) {
        if (victim == null || victim.level().isClientSide || victim instanceof AbstractAbyssalBoss) {
            return 0.0F;
        }
        return resolveBossAttacker(source) != null ? BOSS_ARMOR_PIERCE_FRACTION : 0.0F;
    }

    // =========================================================================
    // 2. GẮN DEBUFF THEO HỆ NGUYÊN TỐ KHI BOSS ĐÁNH TRÚNG NGƯỜI CHƠI
    // =========================================================================

    public static void onLivingHurtByBoss(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0.0F || !(victim instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
            return;
        }
        AbstractAbyssalBoss boss = resolveBossAttacker(source);
        if (boss == null) {
            return;
        }

        long now = player.level().getGameTime();
        Long prev = LAST_DEBUFF_TICK.get(player.getUUID());
        if (prev != null && (now - prev) < DEBUFF_INTERNAL_COOLDOWN_TICKS) {
            return;
        }
        LAST_DEBUFF_TICK.put(player.getUUID(), now);

        boolean p2 = isBossPhase2(boss);
        BossArchetype archetype = BossArchetype.of(boss);
        switch (archetype) {
            case INFERNO -> {
                applyDebuff(player, MobEffects.WITHER, p2 ? 90 : 80, p2 ? 1 : 0);
                applyDebuff(player, MobEffects.WEAKNESS, 100, 0);
                if (p2) {
                    applyDebuff(player, MobEffects.MOVEMENT_SLOWDOWN, 55, 0);
                }
            }
            case NIGHTMARE_VOID -> {
                applyDebuff(player, MobEffects.WITHER, 85, 1);
                applyDebuff(player, MobEffects.DARKNESS, p2 ? 75 : 60, 0);
                if (p2) {
                    applyDebuff(player, MobEffects.WEAKNESS, 90, 0);
                } else {
                    applyDebuff(player, MobEffects.DIG_SLOWDOWN, 80, 0);
                }
            }
            case VENOM_ABYSS, OCEAN_ABYSS -> {
                applyDebuff(player, MobEffects.POISON, 90, 1);
                applyDebuff(player, MobEffects.MOVEMENT_SLOWDOWN, 70, 1);
                applyDebuff(player, MobEffects.DIG_SLOWDOWN, 90, 0);
            }
            case APEX_TEMPEST -> {
                applyDebuff(player, ModEffects.BLEEDING, 100, p2 ? 1 : 0);
                applyDebuff(player, MobEffects.WEAKNESS, 90, 0);
                applyDebuff(player, MobEffects.MOVEMENT_SLOWDOWN, 70, 0);
            }
        }

        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(archetype.debuffParticle,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    10, 0.45, 0.55, 0.45, 0.04);
        }
    }

    private static void applyDebuff(Player player, Holder<MobEffect> effect, int durationTicks, int amplifier) {
        MobEffectInstance existing = player.getEffect(effect);
        if (existing != null && existing.getAmplifier() > amplifier && existing.getDuration() > 20) {
            return;
        }
        int finalDur = existing != null && existing.getAmplifier() == amplifier
                ? Math.max(existing.getDuration(), durationTicks)
                : durationTicks;
        player.addEffect(new MobEffectInstance(effect, finalDur, amplifier, false, true, true));
    }

    // =========================================================================
    // 3. KỸ NĂNG CHỦ ĐỘNG: SÉT ĐÁNH CẢNH BÁO & TRIỆU HỒI MOB ĐỒNG HỆ
    // =========================================================================

    public static void tickBoss(AbstractAbyssalBoss boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        if (!boss.isAlive() || boss.isRemoved() || boss.isSpawningIn()) {
            return;
        }
        if (boss instanceof AbstractGeoMythicBoss geo && geo.isTransitioningPhase()) {
            return;
        }

        LivingEntity rawTarget = boss.getTarget();
        if (!(rawTarget instanceof Player target) || !target.isAlive() || target.isCreative() || target.isSpectator()) {
            return;
        }
        if (boss.distanceToSqr(target) > ACTIVE_COMBAT_RANGE_SQR) {
            return;
        }

        long now = level.getGameTime();
        RandomSource rng = boss.getRandom();
        BossEscalationState state = BOSS_STATES.computeIfAbsent(boss.getUUID(), id ->
                new BossEscalationState(85 + rng.nextInt(55), 125 + rng.nextInt(65), now));
        state.lastSeenTick = now;

        boolean phase2 = isBossPhase2(boss);
        boolean cerberusSplit = boss instanceof CerberusBossEntity cerb && cerb.hasSplit();
        float splitMult = cerberusSplit ? 1.85F : 1.0F;

        if (state.lightningCooldown > 0) {
            state.lightningCooldown--;
        } else {
            triggerLightningBarrage(level, boss, target, phase2);
            int baseCd = phase2 ? (105 + rng.nextInt(40)) : (150 + rng.nextInt(50));
            state.lightningCooldown = Math.round(baseCd * splitMult);
        }

        if (state.summonCooldown > 0) {
            state.summonCooldown--;
        } else {
            boolean summoned = trySummonThematicMinions(level, boss, target, phase2, cerberusSplit);
            if (summoned) {
                int baseCd = phase2 ? (180 + rng.nextInt(50)) : (240 + rng.nextInt(60));
                state.summonCooldown = Math.round(baseCd * splitMult);
            } else {
                state.summonCooldown = 80;
            }
        }
    }

    private static void triggerLightningBarrage(ServerLevel level, AbstractAbyssalBoss boss, Player target, boolean phase2) {
        boolean stormSpecialist = boss instanceof CinderDragonBossEntity
                || boss instanceof NightmareKnightBossEntity
                || boss instanceof FenrirBossEntity;
        int boltCount = phase2 ? (stormSpecialist ? 3 : 2) : (stormSpecialist ? 2 : 1);

        double bossAtk = boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float strikeDamage = Mth.clamp((float) bossAtk * (phase2 ? 0.80F : 0.65F), 8.0F, 24.0F);

        level.playSound(null, boss.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.HOSTILE, 1.35F, 1.35F);

        for (int i = 0; i < boltCount; i++) {
            PENDING_STRIKES.add(new PendingLightningStrike(
                    level,
                    boss.getUUID(),
                    target.getUUID(),
                    i * 12,
                    strikeDamage,
                    phase2
            ));
        }
    }

    private static boolean trySummonThematicMinions(ServerLevel level, AbstractAbyssalBoss boss, Player target,
                                                    boolean phase2, boolean cerberusSplit) {
        int cap = cerberusSplit ? 4 : (phase2 ? 7 : 5);
        AABB scanBox = boss.getBoundingBox().inflate(42.0);
        List<Mob> existingMinions = level.getEntitiesOfClass(Mob.class, scanBox,
                m -> m.isAlive() && m.getTags().contains(BOSS_MINION_TAG));
        int available = cap - existingMinions.size();
        if (available <= 0) {
            return false;
        }

        int desired = Math.min(available, cerberusSplit ? 1 : (phase2 ? 3 : 2));
        BossArchetype archetype = BossArchetype.of(boss);
        RandomSource rng = level.random;
        int tier = Math.min(10, Math.max(2, boss.getFloor()));

        level.playSound(null, boss.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON,
                SoundSource.HOSTILE, 1.8F, 0.82F);
        ShockwaveRingEntity.spawn(level, boss.position().add(0, 0.3, 0), 6.5F, archetype.rgb, 14);

        int spawned = 0;
        for (int i = 0; i < desired; i++) {
            EntityType<?> chosen = archetype.minionPool.get(rng.nextInt(archetype.minionPool.size()));
            Entity created = chosen.create(level);
            if (!(created instanceof Mob mob)) {
                continue;
            }

            double angle = (Math.PI * 2.0 * i) / Math.max(1, desired) + rng.nextDouble() * 0.9;
            double dist = 4.5 + rng.nextDouble() * 3.5;
            Vec3 anchor = target.position().lerp(boss.position(), 0.45);
            Vec3 rawPos = new Vec3(anchor.x + Math.cos(angle) * dist, anchor.y + 0.5, anchor.z + Math.sin(angle) * dist);

            boolean flyingMinion = chosen == EntityType.BLAZE || chosen == EntityType.VEX;
            Vec3 spawnPos = flyingMinion
                    ? new Vec3(rawPos.x, Math.max(target.getY() + 2.2, boss.getY() + 2.0), rawPos.z)
                    : AbstractGeoMythicBoss.findSafeLandingPosition(level, rawPos, target.position(), 4);

            mob.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, rng.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            AbstractAbyssalBoss.scaleSummonedMinion(mob, tier, i == 0 && phase2);
            mob.addTag(BOSS_MINION_TAG);
            // Chống mob undead chết cháy dưới ánh nắng ban ngày
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0, true, false, false));
            if (mob instanceof Vex vex) {
                vex.setLimitedLife(20 * 28);
            }
            mob.setTarget(target);

            if (level.addFreshEntity(mob)) {
                spawned++;
                level.sendParticles(ParticleTypes.PORTAL, spawnPos.x, spawnPos.y + 1.0, spawnPos.z,
                        22, 0.45, 0.7, 0.45, 0.08);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spawnPos.x, spawnPos.y + 0.6, spawnPos.z,
                        10, 0.4, 0.4, 0.4, 0.04);
            }
        }
        return spawned > 0;
    }

    public static void tickServer(MinecraftServer server) {
        if (!PENDING_STRIKES.isEmpty()) {
            List<PendingLightningStrike> snapshot = new ArrayList<>(PENDING_STRIKES);
            for (PendingLightningStrike strike : snapshot) {
                if (!PENDING_STRIKES.contains(strike)) {
                    continue;
                }
                if (!tickPendingStrike(strike)) {
                    PENDING_STRIKES.remove(strike);
                }
            }
        }

        if (server.getTickCount() % 200 == 0) {
            long overworldTime = server.overworld().getGameTime();
            BOSS_STATES.entrySet().removeIf(e -> (overworldTime - e.getValue().lastSeenTick) > 600L);
            if (LAST_DEBUFF_TICK.size() > 128) {
                LAST_DEBUFF_TICK.clear();
            }
        }
    }

    private static boolean tickPendingStrike(PendingLightningStrike strike) {
        ServerLevel level = strike.level;
        Entity bossEnt = level.getEntity(strike.bossUuid);
        if (!(bossEnt instanceof AbstractAbyssalBoss boss) || !boss.isAlive() || boss.isRemoved()) {
            return false;
        }

        if (strike.delayBeforeTelegraph > 0) {
            strike.delayBeforeTelegraph--;
            return true;
        }

        if (!strike.telegraphStarted) {
            Entity targetEnt = level.getEntity(strike.targetPlayerUuid);
            if (!(targetEnt instanceof Player target) || !target.isAlive() || target.isCreative() || target.isSpectator()) {
                return false;
            }
            // Dự đoán nhẹ hướng chạy để người chơi vẫn né được bằng di chuyển / lướt
            Vec3 lead = target.getDeltaMovement().multiply(4.5, 0.0, 4.5);
            Vec3 predicted = target.position().add(lead);
            strike.strikePos = AbstractGeoMythicBoss.findSafeLandingPosition(level, predicted, target.position(), 3);
            strike.telegraphStarted = true;

            ShockwaveRingEntity.spawn(level, strike.strikePos.add(0, 0.16, 0), 3.4F, 0x5CEEFF, LIGHTNING_TELEGRAPH_TICKS);
            level.playSound(null, strike.strikePos.x, strike.strikePos.y, strike.strikePos.z,
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.4F, 1.55F);
        }

        if (strike.telegraphTicksLeft > 0) {
            if (strike.telegraphTicksLeft % 3 == 0) {
                for (double y = 0.2; y <= 6.2; y += 1.2) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            strike.strikePos.x, strike.strikePos.y + y, strike.strikePos.z,
                            3, 0.35, 0.25, 0.35, 0.04);
                }
                level.sendParticles(ParticleTypes.END_ROD,
                        strike.strikePos.x, strike.strikePos.y + 0.35, strike.strikePos.z,
                        4, 0.9, 0.12, 0.9, 0.01);
            }
            strike.telegraphTicksLeft--;
            return true;
        }

        executeLightningImpact(level, boss, strike.strikePos, strike.damage, strike.phase2);
        return false;
    }

    private static void executeLightningImpact(ServerLevel level, AbstractAbyssalBoss boss, Vec3 pos,
                                               float damage, boolean phase2) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(pos.x, pos.y, pos.z);
            // VisualOnly để KHÔNG thiêu cháy vật phẩm rơi của người chơi hay phá công trình đền thờ, sát thương kiểm soát chuẩn server + xuyên 36% giáp!
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }

        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.HOSTILE, 1.65F, 0.92F);
        ShockwaveRingEntity.spawn(level, pos.add(0, 0.2, 0), 4.4F, 0x8CF6FF, 12);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1.0, pos.z,
                32, 1.1, 1.4, 1.1, 0.22);
        ModNetworking.sendShakeNearby(level, pos, 30.0, 1.15F, 8);

        double radius = phase2 ? 3.4 : 3.0;
        AABB hitBox = new AABB(pos.x - radius, pos.y - 2.0, pos.z - radius,
                pos.x + radius, pos.y + 4.5, pos.z + radius);
        ACTIVE_BOSS_SOURCE.set(boss);
        try {
            DamageSource src = level.damageSources().mobAttack(boss);
            for (Player player : level.getEntitiesOfClass(Player.class, hitBox,
                    p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
                if (player.distanceToSqr(pos) <= radius * radius) {
                    KatanaCombatHandler.runUnparryableMagic(() -> player.hurt(src, damage));
                    applyDebuff(player, MobEffects.MOVEMENT_SLOWDOWN, 50, 0);
                }
            }
        } finally {
            ACTIVE_BOSS_SOURCE.remove();
        }
    }

    public static void onBossDied(AbstractAbyssalBoss boss) {
        if (boss == null) {
            return;
        }
        UUID id = boss.getUUID();
        BOSS_STATES.remove(id);
        PENDING_STRIKES.removeIf(s -> s.bossUuid.equals(id));
    }

    private static boolean isBossPhase2(AbstractAbyssalBoss boss) {
        return boss.getPhase() >= 2
                || (boss instanceof AbstractGeoMythicBoss geo && geo.hasEnteredPhase2());
    }
}
