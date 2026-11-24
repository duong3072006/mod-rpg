package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.entity.AbstractAbyssalBoss;
import com.abyssalrpg.entity.vfx.AbyssalOrbEntity;
import com.abyssalrpg.entity.vfx.EnergySlashEntity;
import com.abyssalrpg.entity.vfx.LaserBeamEntity;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * APEP — Ancient Egyptian Chaos Cobra-Serpent (3 Heterochromatic Eyes: Red, Emerald & Jade Third Eye)!
 * Fires Twin Red-Fire & Chaos-Emerald Eye Beams, Desert Lightning Magic Circles, and Coiling Chaos Shockwaves.
 */
public class ApepBossEntity extends AbstractAbyssalBoss {
    private static final int COLOR_JADE = 0x1BFF9E;
    private static final int COLOR_FIRE = 0xFF3E14;

    private int cycle = 0;

    public ApepBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
    }

    @Override protected int getDefaultSpawnEggFloor() { return 11; }
    @Override public float getColossalScale(int floor) { return Math.min(4.85f, 4.05f + (floor - 1) * 0.045f); }
    @Override protected Component getStandaloneBossTitle() {
        return Component.literal("☠ APEP — Egyptian Chaos Serpent ☠").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, 480.0)
                .add(Attributes.ATTACK_DAMAGE, 19.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void onPhaseTransition(ServerLevel level, int newPhase) {
        super.onPhaseTransition(level, newPhase);
        ShockwaveRingEntity.spawn(level, this.position(), 14.0f, COLOR_JADE, 16);
        ShockwaveRingEntity.spawn(level, this.position().add(0, 0.8, 0), 10.0f, COLOR_FIRE, 12);
        ModNetworking.sendShakeNearby(level, this.position(), 36.0, 2.2f, 14);
    }

    @Override
    protected int executeBossSkill(ServerLevel level, LivingEntity target) {
        float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (phase >= 2 ? 1.45 : 1.18));
        int move = cycle % 4;
        cycle++;

        switch (move) {
            case 0 -> {
                // SKILL 1: Dual Heterochromatic Eye-Beam (Fiery Red + Chaos Jade Parallel Lasers!)
                startCastAnimation(2, 32);
                level.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.7f, 0.85f);
                scheduleAction(10, null, () -> {
                    Vec3 headPos = this.position().add(0, 5.2, 0);
                    Vec3 aim = target.position().add(0, 1.0, 0).subtract(headPos).normalize();
                    Vec3 side = aim.cross(new Vec3(0, 1, 0)).normalize().scale(1.1);
                    LaserBeamEntity.spawn(level, this, headPos.add(side), headPos.add(side).add(aim.scale(30.0)), 0.72f, COLOR_FIRE, 22, dmg * 0.65f, true);
                    LaserBeamEntity.spawn(level, this, headPos.subtract(side), headPos.subtract(side).add(aim.scale(30.0)), 0.72f, COLOR_JADE, 22, dmg * 0.65f, false);
                    ShockwaveRingEntity.spawn(level, target.position(), 7.0f, COLOR_JADE, 12);
                });
            }
            case 1 -> {
                // SKILL 2: Third-Eye Desert Chaos Ritual (Alternating Jade & Fire Lightning Circles!)
                startCastAnimation(2, 30);
                level.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.4f, 0.95f);
                int rings = phase >= 2 ? 6 : 4;
                for (int i = 0; i < rings; i++) {
                    double ang = (Math.PI * 2.0 * i) / rings;
                    double rad = (i == 0) ? 0.0 : 6.0;
                    Vec3 spot = target.position().add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad);
                    MagicCircleEntity.spawn(level, this, spot, 3.6f, 18 + i * 3, (i % 2 == 0) ? COLOR_JADE : COLOR_FIRE, dmg, true, true);
                }
            }
            case 2 -> {
                // SKILL 3: Twin Venom-Flame Fang Crescent Slash + Chaos Orbs!
                startCastAnimation(1, 24);
                level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 1.6f, 0.7f);
                Vec3 origin = this.position().add(0, 3.8, 0);
                Vec3 aim = target.position().add(0, 1.0, 0).subtract(origin).normalize();
                EnergySlashEntity.spawn(level, this, origin, aim, 1.35f, 7.0f, 32f, COLOR_JADE, dmg, false);
                EnergySlashEntity.spawn(level, this, origin, aim, 1.35f, 7.0f, -32f, COLOR_FIRE, dmg, true);
                for (int i = -1; i <= 1; i++) {
                    double ang = i * 0.28;
                    Vec3 dir = new Vec3(aim.x * Math.cos(ang) - aim.z * Math.sin(ang), aim.y, aim.x * Math.sin(ang) + aim.z * Math.cos(ang)).normalize();
                    AbyssalOrbEntity.spawn(level, this, origin, dir.scale(1.05), 1.0f, i == 0 ? COLOR_JADE : COLOR_FIRE, dmg * 0.85f, 3.5f, i != 0, true);
                }
            }
            default -> {
                // SKILL 4: Colossal Cobra Coil Ground Slam!
                startCastAnimation(3, 28);
                level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.8f, 0.55f);
                ModNetworking.sendShakeNearby(level, this.position(), 36.0, 2.3f, 12);
                ShockwaveRingEntity.spawn(level, this.position(), 13.5f, COLOR_JADE, 15);
                ShockwaveRingEntity.spawn(level, this.position().add(0, 0.5, 0), 9.0f, COLOR_FIRE, 12);
                damagePlayersInRadius(level, this.position(), 11.0, dmg * 1.15f, 0.85);
            }
        }
        return phase >= 2 ? 54 : 70;
    }

    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 3.5, this.getZ(), 3, 1.4, 1.8, 1.4, 0.01);
    }
}

