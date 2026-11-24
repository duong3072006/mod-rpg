package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.entity.AbstractAbyssalBoss;
import com.abyssalrpg.entity.vfx.EnergySlashEntity;
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
 * FENRIR — Unbound Norse Werewolf-Beast Titan!
 * Massive hunched storm-wolf berserker with blazing crimson eyes and colossal clawed arms,
 * executing Thunder-Claw X-Slashes, Leap Ground-Shatters, and Ragnarok Storm Lightning Circles!
 */
public class FenrirBossEntity extends AbstractAbyssalBoss {
    private static final int COLOR_BLOOD = 0xFF2B1A;
    private static final int COLOR_STORM = 0xA8C8FF;

    private int cycle = 0;

    public FenrirBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.RED);
    }

    @Override protected int getDefaultSpawnEggFloor() { return 8; }
    @Override public float getColossalScale(int floor) { return Math.min(4.35f, 3.55f + (floor - 1) * 0.045f); }
    @Override protected Component getStandaloneBossTitle() {
        return Component.literal("⚔ FENRIR — Unbound Norse Wolf Titan ⚔").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, 460.0)
                .add(Attributes.ATTACK_DAMAGE, 21.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.37)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.95);
    }

    @Override
    protected void onPhaseTransition(ServerLevel level, int newPhase) {
        super.onPhaseTransition(level, newPhase);
        ShockwaveRingEntity.spawn(level, this.position(), 13.0f, COLOR_BLOOD, 14);
        ModNetworking.sendShakeNearby(level, this.position(), 34.0, 2.2f, 12);
    }

    @Override
    protected int executeBossSkill(ServerLevel level, LivingEntity target) {
        float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (phase >= 2 ? 1.48 : 1.20));
        int move = cycle % 3;
        cycle++;

        switch (move) {
            case 0 -> {
                // SKILL 1: Savage Twin-Claw X-Rending Crescent Waves!
                startCastAnimation(1, 24);
                level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.7f, 0.65f);
                Vec3 origin = this.position().add(0, 2.6, 0);
                Vec3 aim = target.position().add(0, 1.0, 0).subtract(origin).normalize();
                EnergySlashEntity.spawn(level, this, origin, aim, 1.45f, 7.2f, 38f, COLOR_BLOOD, dmg, false);
                EnergySlashEntity.spawn(level, this, origin, aim, 1.45f, 7.2f, -38f, COLOR_BLOOD, dmg, false);
                if (phase >= 2) {
                    EnergySlashEntity.spawn(level, this, origin, aim, 1.55f, 8.2f, 0f, COLOR_STORM, dmg * 0.9f, false);
                }
            }
            case 1 -> {
                // SKILL 2: Colossal Berserker Leap -> Thunderous Fist-Slam Crater!
                startCastAnimation(2, 30);
                level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.6f, 0.7f);
                Vec3 jump = target.position().subtract(this.position()).normalize().scale(1.65);
                this.setDeltaMovement(jump.x, 0.92, jump.z);
                this.hurtMarked = true;
                scheduleAction(14, null, () -> {
                    level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.9f, 0.55f);
                    ModNetworking.sendShakeNearby(level, this.position(), 36.0, 2.4f, 14);
                    ShockwaveRingEntity.spawn(level, this.position(), 12.5f, COLOR_BLOOD, 14);
                    ShockwaveRingEntity.spawn(level, this.position().add(0, 0.4, 0), 8.5f, COLOR_STORM, 10);
                    damagePlayersInRadius(level, this.position(), 10.0, dmg * 1.25f, 0.9);
                });
            }
            default -> {
                // SKILL 3: Ragnarok Howl — Lightning Magic Circles Around Prey!
                startCastAnimation(3, 30);
                level.playSound(null, this.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 2.0f, 0.5f);
                int count = phase >= 2 ? 6 : 4;
                for (int i = 0; i < count; i++) {
                    double a = (Math.PI * 2.0 * i) / count;
                    double r = (i == 0) ? 0.0 : 5.5;
                    Vec3 spot = target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    MagicCircleEntity.spawn(level, this, spot, 3.5f, 18 + i * 2, (i % 2 == 0) ? COLOR_BLOOD : COLOR_STORM, dmg, true, false);
                }
            }
        }
        return phase >= 2 ? 48 : 64;
    }

    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        if (phase >= 2) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 2.5, this.getZ(), 3, 1.3, 1.4, 1.3, 0.02);
        }
    }
}
