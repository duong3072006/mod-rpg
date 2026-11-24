package com.abyssalrpg.entity;

import com.abyssalrpg.entity.vfx.EnergySlashEntity;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.ModNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Dire Shadow Fenrir Beast (Ma Thú Hắc Ám Fenrir):
 * Fires colossal 3D Violet Crescent Claw Waves, Pounce Shockwave Slams,
 * Phantom Blink Rune Detonations, and Phase 2 8-Direction Crescent Tempest!
 */
public class AbyssalKnightBossEntity extends AbstractAbyssalBoss {
    private static final int COLOR_VIOLET = 0xB84DFF;
    private int skillIndex = 0;

    public AbyssalKnightBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.PURPLE);
    }

    @Override
    protected int getDefaultSpawnEggFloor() {
        return 5;
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, 360.0)
                .add(Attributes.ATTACK_DAMAGE, 15.5)
                .add(Attributes.MOVEMENT_SPEED, 0.35);
    }

    @Override
    protected void onPhaseTransition(ServerLevel level, int newPhase) {
        super.onPhaseTransition(level, newPhase);
        ShockwaveRingEntity.spawn(level, this.position(), 11.0f, COLOR_VIOLET, 16);
        ModNetworking.sendShakeNearby(level, this.position(), 32.0, 1.8f, 12);
        this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1));
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 1));
        this.summonFloorMinions(level, Math.min(4, 2 + floor / 5));
    }

    @Override
    protected int executeBossSkill(ServerLevel level, LivingEntity target) {
        float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (phase >= 2 ? 1.55 : 1.25));
        int move = skillIndex % (phase >= 2 ? 4 : 3);
        skillIndex++;

        switch (move) {
            case 0 -> {
                // SKILL 1: Crossed X-Shaped Colossal 3D Violet Claw Waves!
                startCastAnimation(1, 22);
                Vec3 origin = this.position().add(0, 1.8, 0);
                Vec3 dir = target.position().add(0, 1.0, 0).subtract(origin).normalize();
                level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.7f, 0.55f);
                EnergySlashEntity.spawn(level, this, origin, dir, 1.25f, 5.5f, 28f, COLOR_VIOLET, dmg, false);
                EnergySlashEntity.spawn(level, this, origin, dir, 1.25f, 5.5f, -28f, 0xE879F9, dmg, false);
                if (phase >= 2) {
                    scheduleAction(8, null, () -> {
                        if (!target.isAlive()) return;
                        Vec3 d2 = target.position().add(0, 1.0, 0).subtract(this.position().add(0, 1.8, 0)).normalize();
                        EnergySlashEntity.spawn(level, this, this.position().add(0, 1.8, 0), d2, 1.35f, 6.8f, 0f, COLOR_VIOLET, dmg * 1.1f, false);
                    });
                }
            }
            case 1 -> {
                // SKILL 2: Predatory Sky Pounce -> 3D Expanding Double Shockwave Ring + Screen Shake!
                startCastAnimation(2, 26);
                Vec3 jump = target.position().subtract(this.position()).normalize().scale(1.25).add(0, 1.1, 0);
                this.setDeltaMovement(jump);
                this.hurtMarked = true;
                level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.7f);

                scheduleAction(15, null, () -> {
                    Vec3 slam = this.position();
                    level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.7f, 0.6f);
                    ModNetworking.sendShakeNearby(level, slam, 30.0, 2.0f, 12);
                    ShockwaveRingEntity.spawn(level, slam, 10.5f, COLOR_VIOLET, 14);
                    ShockwaveRingEntity.spawn(level, slam.add(0, 0.4, 0), 7.0f, 0xF5C2FF, 10);
                    damagePlayersInRadius(level, slam, 9.0, dmg * 1.25f, 0.85);
                });
            }
            case 2 -> {
                // SKILL 3: Phantom Blink Ambush + 3D Violet Magic Rune Pillars!
                startCastAnimation(1, 26);
                for (int b = 0; b < 2; b++) {
                    scheduleAction(b * 12 + 2, null, () -> {
                        if (!target.isAlive()) return;
                        double angle = level.random.nextDouble() * Math.PI * 2.0;
                        Vec3 dest = target.position().add(Math.cos(angle) * 3.5, 0.1, Math.sin(angle) * 3.5);
                        this.teleportTo(dest.x, dest.y, dest.z);
                        level.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.6f);
                        ShockwaveRingEntity.spawn(level, dest, 5.5f, COLOR_VIOLET, 10);
                        MagicCircleEntity.spawn(level, this, target.position(), 3.6f, 18, COLOR_VIOLET, dmg, false, true);
                    });
                }
            }
            default -> {
                // SKILL 4 (Phase 2): 8-Way Radial 3D Crescent Claw Tempest!
                startCastAnimation(3, 36);
                level.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.4f, 1.0f);
                ModNetworking.sendShakeNearby(level, this.position(), 30.0, 1.5f, 10);
                ShockwaveRingEntity.spawn(level, this.position(), 12.0f, COLOR_VIOLET, 15);
                for (int i = 0; i < 8; i++) {
                    double a = (Math.PI * 2.0 * i) / 8.0;
                    Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                    EnergySlashEntity.spawn(level, this, this.position().add(0, 1.6, 0), dir, 1.15f, 4.8f, 0f, COLOR_VIOLET, dmg * 0.9f, false);
                }
            }
        }
        return phase >= 2 ? 52 : 75;
    }

    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        if (phase >= 2) {
            level.sendParticles(ParticleTypes.DRAGON_BREATH, this.getX(), this.getY() + 1.5, this.getZ(), 3, 0.9, 0.9, 0.9, 0.01);
        }
    }
}
