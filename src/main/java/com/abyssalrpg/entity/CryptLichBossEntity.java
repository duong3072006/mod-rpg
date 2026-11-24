package com.abyssalrpg.entity;

import com.abyssalrpg.entity.vfx.AbyssalOrbEntity;
import com.abyssalrpg.entity.vfx.LaserBeamEntity;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Broodmother Spider Horror (Nhện Chúa Hầm Mộ):
 * 3D Cyan Hexagram Magic Circles + Continuous 3D Piercing Soul Laser Beam +
 * Spinning 3D Necrotic Plasma Orbs + Phase 2 Soul Drain Nova!
 */
public class CryptLichBossEntity extends AbstractAbyssalBoss {
    private static final int COLOR_CYAN = 0x22F2D6;
    private int castCycle = 0;

    public CryptLichBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
    }

    @Override
    protected int getDefaultSpawnEggFloor() {
        return 7;
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, 320.0)
                .add(Attributes.ATTACK_DAMAGE, 14.5)
                .add(Attributes.MOVEMENT_SPEED, 0.33);
    }

    @Override
    protected void onPhaseTransition(ServerLevel level, int newPhase) {
        super.onPhaseTransition(level, newPhase);
        ShockwaveRingEntity.spawn(level, this.position(), 11.5f, COLOR_CYAN, 16);
        ModNetworking.sendShakeNearby(level, this.position(), 32.0, 1.6f, 10);
        this.heal(this.getMaxHealth() * 0.14f);
        this.summonFloorMinions(level, Math.min(5, 3 + floor / 4));
    }

    @Override
    protected int executeBossSkill(ServerLevel level, LivingEntity target) {
        float spellDmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (phase >= 2 ? 1.50 : 1.20));
        int mode = castCycle % (phase >= 2 ? 4 : 3);
        castCycle++;

        switch (mode) {
            case 0 -> {
                // SKILL 1: 4..6 Counter-Rotating 3D Cyan Arcane Magic Circles -> Sky Soul Pillars!
                startCastAnimation(2, 28);
                level.playSound(null, this.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5f, 0.7f);
                MagicCircleEntity.spawn(level, this, target.position(), 3.6f, 22, COLOR_CYAN, spellDmg * 1.1f, false, true);
                int extra = phase >= 2 ? 5 : 3;
                for (int i = 0; i < extra; i++) {
                    double ang = (Math.PI * 2.0 * i) / extra + level.random.nextDouble() * 0.4;
                    double rad = 4.5 + level.random.nextDouble() * 4.5;
                    Vec3 spot = target.position().add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad);
                    MagicCircleEntity.spawn(level, this, spot, 3.3f, 22 + i * 3, COLOR_CYAN, spellDmg, false, true);
                }
            }
            case 1 -> {
                // SKILL 2: Continuous Volumetric 3D Cyan Soul Annihilation Laser Beam!
                startCastAnimation(1, 32);
                level.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.6f, 1.05f);
                scheduleAction(14,
                        () -> {
                            Vec3 m = this.position().add(0, 1.8, 0);
                            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.x, m.y, m.z, 5, 0.5, 0.5, 0.5, 0.03);
                        },
                        () -> {
                            Vec3 start = this.position().add(0, 1.8, 0);
                            Vec3 aim = target.position().add(0, 1.0, 0).subtract(start).normalize();
                            Vec3 end = start.add(aim.scale(26.0));
                            level.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.8f, 0.85f);
                            LaserBeamEntity.spawn(level, this, start, end, 0.75f, COLOR_CYAN, 22, spellDmg * 0.7f, false);
                        });
            }
            case 2 -> {
                // SKILL 3: 5..7 Spinning 3D Crystalline Cyan Necrotic Orbs Fan Barrage!
                startCastAnimation(1, 24);
                level.playSound(null, this.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.5f, 0.85f);
                Vec3 origin = this.position().add(0, 2.0, 0);
                Vec3 aim = target.position().add(0, 1.0, 0).subtract(origin).normalize();
                int count = phase >= 2 ? 7 : 5;
                for (int i = 0; i < count; i++) {
                    double spread = (i - (count - 1) / 2.0) * 0.16;
                    Vec3 dir = new Vec3(
                            aim.x * Math.cos(spread) - aim.z * Math.sin(spread),
                            aim.y,
                            aim.x * Math.sin(spread) + aim.z * Math.cos(spread)
                    ).normalize().scale(0.95);
                    AbyssalOrbEntity.spawn(level, this, origin, dir, 0.85f, COLOR_CYAN, spellDmg * 0.85f, 3.0f, false, true);
                }
            }
            default -> {
                // SKILL 4 (Phase 2): Soul Drain Beams + Giant 3D Expanding Cyan Shockwave!
                startCastAnimation(3, 30);
                level.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.4f, 1.15f);
                ShockwaveRingEntity.spawn(level, this.position(), 13.0f, COLOR_CYAN, 16);
                ModNetworking.sendShakeNearby(level, this.position(), 32.0, 1.7f, 10);
                AABB area = this.getBoundingBox().inflate(14.0);
                Vec3 core = this.position().add(0, 1.8, 0);
                int drained = 0;
                for (Player p : level.getEntitiesOfClass(Player.class, area, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    LaserBeamEntity.spawn(level, this, core, p.position().add(0, 1.0, 0), 0.35f, COLOR_CYAN, 12, spellDmg * 0.5f, false);
                    p.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                    drained++;
                }
                if (drained > 0) this.heal(this.getMaxHealth() * 0.08f * drained);
                damagePlayersInRadius(level, this.position(), 9.5, spellDmg, 0.55);
            }
        }
        return phase >= 2 ? 52 : 74;
    }

    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        if (phase >= 2) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.5, this.getZ(), 3, 0.9, 0.8, 0.9, 0.01);
        }
    }
}
