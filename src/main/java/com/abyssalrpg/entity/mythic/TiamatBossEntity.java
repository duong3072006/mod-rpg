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
 * TIAMAT — Mesopotamian Primordial 7-Headed Crowned Sea-Dragon Queen!
 * 3-Phase Deity Boss with 7 spike-crowned crimson-eyed dragon heads, unleashing
 * Primordial Hellfire Beams, 7-Head Meteor Rain, and Cataclysmic Abyssal Lightning Circles!
 */
public class TiamatBossEntity extends AbstractAbyssalBoss {
    private static final int COLOR_CRIMSON = 0xFF1F24;
    private static final int COLOR_EMBER = 0xFF7B1C;
    private static final int COLOR_SAPPHIRE = 0x2CE8FF;

    private int cycle = 0;

    public TiamatBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.RED);
    }

    @Override protected int getDefaultSpawnEggFloor() { return 18; }
    @Override public float getColossalScale(int floor) { return Math.min(5.15f, 4.30f + (floor - 1) * 0.048f); }
    @Override protected Component getStandaloneBossTitle() {
        return Component.literal("👑 TIAMAT — Primordial 7-Headed Dragon Queen 👑").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, 620.0)
                .add(Attributes.ATTACK_DAMAGE, 23.0)
                .add(Attributes.ARMOR, 18.0)
                .add(Attributes.ARMOR_TOUGHNESS, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected int computePhase(float hpRatio) {
        if (hpRatio <= 0.35f) return 3;
        if (hpRatio <= 0.68f) return 2;
        return 1;
    }

    @Override
    protected void onPhaseTransition(ServerLevel level, int newPhase) {
        super.onPhaseTransition(level, newPhase);
        ShockwaveRingEntity.spawn(level, this.position(), 17.0f, COLOR_CRIMSON, 18);
        ShockwaveRingEntity.spawn(level, this.position().add(0, 1.2, 0), 12.0f, COLOR_EMBER, 14);
        ModNetworking.sendShakeNearby(level, this.position(), 48.0, 2.6f, 18);
    }

    @Override
    protected int executeBossSkill(ServerLevel level, LivingEntity target) {
        float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (1.08 + 0.25 * phase));
        int move = cycle % 4;
        cycle++;

        switch (move) {
            case 0 -> {
                // SKILL 1: Primordial 3-Beam Hellfire Convergence (Crimson + Ember + Sapphire Core!)
                startCastAnimation(2, 36);
                level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0f, 0.55f);
                scheduleAction(12, null, () -> {
                    Vec3 head = this.position().add(0, 5.2, 0);
                    Vec3 aim = target.position().add(0, 1.0, 0).subtract(head).normalize();
                    LaserBeamEntity.spawn(level, this, head, head.add(aim.scale(35.0)), 1.05f, COLOR_CRIMSON, 24, dmg * 0.72f, true);
                    double[] offs = {-0.22, 0.22};
                    for (double ang : offs) {
                        Vec3 dir = new Vec3(aim.x * Math.cos(ang) - aim.z * Math.sin(ang), aim.y, aim.x * Math.sin(ang) + aim.z * Math.cos(ang)).normalize();
                        LaserBeamEntity.spawn(level, this, head, head.add(dir.scale(32.0)), 0.70f, COLOR_EMBER, 24, dmg * 0.55f, true);
                    }
                    ShockwaveRingEntity.spawn(level, target.position(), 8.5f, COLOR_CRIMSON, 14);
                });
            }
            case 1 -> {
                // SKILL 2: 7-Headed Hellfire Meteor Apocalypse!
                startCastAnimation(1, 30);
                level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 1.8f, 0.62f);
                Vec3 origin = this.position().add(0, 5.0, 0);
                Vec3 aim = target.position().add(0, 1.0, 0).subtract(origin).normalize();
                for (int i = -3; i <= 3; i++) {
                    double ang = i * 0.16;
                    Vec3 dir = new Vec3(aim.x * Math.cos(ang) - aim.z * Math.sin(ang), aim.y, aim.x * Math.sin(ang) + aim.z * Math.cos(ang)).normalize();
                    int col = (i == 0) ? COLOR_SAPPHIRE : ((i % 2 == 0) ? COLOR_CRIMSON : COLOR_EMBER);
                    AbyssalOrbEntity.spawn(level, this, origin.add(i * 0.8, 0, 0), dir.scale(1.15), 1.05f, col, dmg * 0.8f, 3.6f, true, true);
                }
            }
            case 2 -> {
                // SKILL 3: Primordial Sea-Storm Magic Circle Constellation!
                startCastAnimation(3, 32);
                level.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.6f, 0.78f);
                int count = 4 + phase * 2;
                for (int i = 0; i < count; i++) {
                    double a = (Math.PI * 2.0 * i) / count;
                    double r = (i == 0) ? 0.0 : (5.5 + level.random.nextDouble() * 5.0);
                    Vec3 spot = target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    MagicCircleEntity.spawn(level, this, spot, 3.8f, 18 + i * 2, (i % 3 == 0) ? COLOR_SAPPHIRE : ((i % 2 == 0) ? COLOR_CRIMSON : COLOR_EMBER), dmg, true, true);
                }
            }
            default -> {
                // SKILL 4: Twin Crossed Crimson-Flame Crescent Waves + Tidal Slam!
                startCastAnimation(2, 28);
                Vec3 origin = this.position().add(0, 4.2, 0);
                Vec3 aim = target.position().add(0, 1.0, 0).subtract(origin).normalize();
                EnergySlashEntity.spawn(level, this, origin, aim, 1.45f, 8.5f, 28f, COLOR_CRIMSON, dmg, true);
                EnergySlashEntity.spawn(level, this, origin, aim, 1.45f, 8.5f, -28f, COLOR_EMBER, dmg, true);
                ShockwaveRingEntity.spawn(level, this.position(), 14.0f, COLOR_CRIMSON, 16);
                ModNetworking.sendShakeNearby(level, this.position(), 40.0, 2.2f, 12);
                damagePlayersInRadius(level, this.position(), 11.5, dmg, 0.85);
            }
        }
        return switch (phase) {
            case 3 -> 44;
            case 2 -> 56;
            default -> 70;
        };
    }

    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 4.0, this.getZ(), 4, 2.2, 2.2, 2.2, 0.02);
    }
}

