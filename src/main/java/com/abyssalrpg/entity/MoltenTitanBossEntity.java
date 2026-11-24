package com.abyssalrpg.entity;

import com.abyssalrpg.entity.vfx.AbyssalOrbEntity;
import com.abyssalrpg.entity.vfx.EnergySlashEntity;
import com.abyssalrpg.entity.vfx.LaserBeamEntity;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.ModNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Volcanic 3-Headed Magma Hydra (Hỏa Long Hydra 3 Đầu):
 * 3-Headed Synchronized 3D Volcanic Laser Beams, Sky Meteor Comets onto Fiery 3D Magic Circles,
 * Triple Expanding 3D Lava Shockwave Stomps, and Phase 2 Reflective Magma Shield!
 */
public class MoltenTitanBossEntity extends AbstractAbyssalBoss {
    private static final int COLOR_MAGMA = 0xFF6A14;
    private static final int COLOR_SOLAR = 0xFFAE2B;
    private int attackCounter = 0;
    private int magmaShieldTicks = 0;

    public MoltenTitanBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
    }

    @Override
    protected int getDefaultSpawnEggFloor() {
        return 9;
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, 440.0)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (magmaShieldTicks > 0) {
            magmaShieldTicks--;
            if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 18 == 0) {
                ShockwaveRingEntity.spawn(serverLevel, this.position(), 4.5f, COLOR_MAGMA, 10);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && magmaShieldTicks > 0 && !this.level().isClientSide) {
            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity living && attacker != this) {
                living.igniteForSeconds(5);
                living.hurt(this.damageSources().onFire(), Math.max(4.5f, amount * 0.35f));
            }
        }
        return result;
    }

    @Override
    protected void onPhaseTransition(ServerLevel level, int newPhase) {
        super.onPhaseTransition(level, newPhase);
        this.magmaShieldTicks = 300;
        ShockwaveRingEntity.spawn(level, this.position(), 13.0f, COLOR_MAGMA, 16);
        ModNetworking.sendShakeNearby(level, this.position(), 36.0, 2.2f, 14);
    }

    @Override
    protected int executeBossSkill(ServerLevel level, LivingEntity target) {
        float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (phase >= 2 ? 1.50 : 1.20));
        int mode = attackCounter % 3;
        attackCounter++;

        switch (mode) {
            case 0 -> {
                // SKILL 1: Triple Expanding 3D Lava Shockwave Stomps (5 -> 10 -> 15 blocks + heavy camera shake!)
                startCastAnimation(2, 34);
                level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.7f, 0.55f);
                final Vec3 center = this.position();
                float[] radii = {6.0f, 10.5f, 15.0f};
                for (int i = 0; i < radii.length; i++) {
                    final int wave = i;
                    final float r = radii[i];
                    scheduleAction(i * 8 + 4, null, () -> {
                        level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6f, 0.55f);
                        ModNetworking.sendShakeNearby(level, center, 34.0, 2.0f, 10);
                        ShockwaveRingEntity.spawn(level, center, r, wave == 1 ? COLOR_SOLAR : COLOR_MAGMA, 14);
                        damagePlayersInRadius(level, center, r, dmg * (1.1f - wave * 0.1f), 0.9);
                    });
                }
            }
            case 1 -> {
                // SKILL 2: Sky Meteor Rain (3D Flaming Meteor Orbs + 3D Fiery Rune Eruption Circles!)
                startCastAnimation(3, 30);
                level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.6f, 0.6f);
                int meteors = phase >= 2 ? 6 : 4;
                for (int i = 0; i < meteors; i++) {
                    double ox = (i == 0) ? 0 : (level.random.nextDouble() - 0.5) * 13.0;
                    double oz = (i == 0) ? 0 : (level.random.nextDouble() - 0.5) * 13.0;
                    Vec3 spot = target.position().add(ox, 0, oz);
                    MagicCircleEntity.spawn(level, this, spot, 3.6f, 18 + i * 3, COLOR_MAGMA, dmg, false, false);
                    Vec3 skyStart = spot.add(5.0, 16.0, 4.0);
                    Vec3 fallVel = spot.subtract(skyStart).normalize().scale(1.1);
                    AbyssalOrbEntity.spawn(level, this, skyStart, fallVel, 1.25f, COLOR_SOLAR, dmg * 0.9f, 3.8f, true, false);
                }
            }
            default -> {
                // SKILL 3: 3-Headed Synchronized 3D Volcanic Flame Beams & Crescent Magma Waves!
                startCastAnimation(1, 28);
                level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.8f, 0.6f);
                Vec3 origin = this.position().add(0, 3.2, 0);
                Vec3 aim = target.position().add(0, 1.0, 0).subtract(origin).normalize();
                double[] angles = {-0.38, 0.0, 0.38};
                for (double ang : angles) {
                    Vec3 dir = new Vec3(
                            aim.x * Math.cos(ang) - aim.z * Math.sin(ang),
                            aim.y,
                            aim.x * Math.sin(ang) + aim.z * Math.cos(ang)
                    ).normalize();
                    LaserBeamEntity.spawn(level, this, origin, origin.add(dir.scale(24.0)), 0.65f, COLOR_MAGMA, 18, dmg * 0.55f, true);
                    EnergySlashEntity.spawn(level, this, origin, dir, 1.15f, 5.2f, 0f, COLOR_SOLAR, dmg * 0.9f, true);
                }
                if (phase >= 2) this.magmaShieldTicks = 160;
            }
        }
        return phase >= 2 ? 58 : 82;
    }

    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        if (phase >= 2) {
            level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 2.0, this.getZ(), 3, 1.1, 1.0, 1.1, 0.01);
        }
    }
}
