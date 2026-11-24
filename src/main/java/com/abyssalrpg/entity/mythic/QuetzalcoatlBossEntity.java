package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.entity.AbstractAbyssalBoss;
import com.abyssalrpg.entity.vfx.AbyssalOrbEntity;
import com.abyssalrpg.entity.vfx.EnergySlashEntity;
import com.abyssalrpg.entity.vfx.LaserBeamEntity;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * QUETZALCOATL — Aztec Feathered Sky-Serpent God!
 * True aerial flight weaving majestically through storm clouds, unleashing Golden-Cyan Lightning
 * Magic Circles, Rainbow-Wind Crescent Waves, and Emerald-Turquoise Tempest Laser Beams.
 */
public class QuetzalcoatlBossEntity extends AbstractAbyssalBoss {
    private static final int COLOR_EMERALD = 0x2BFF7A;
    private static final int COLOR_CYAN = 0x20F0FF;
    private static final int COLOR_GOLD = 0xFFCC29;
    private static final int COLOR_MAGENTA = 0xFF2E93;

    private int cycle = 0;
    private float orbitAngle = 0f;
    private Vec3 homeAnchor = null;

    @SuppressWarnings("this-escape")
    public QuetzalcoatlBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
        this.setNoGravity(true);
    }

    @Override protected int getDefaultSpawnEggFloor() { return 14; }
    @Override public float getColossalScale(int floor) { return Math.min(5.0f, 4.25f + (floor - 1) * 0.045f); }
    @Override protected Component getStandaloneBossTitle() {
        return Component.literal("✦ QUETZALCOATL — Aztec Feathered Sky God ✦").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return createBaseBossAttributes()
                .add(Attributes.MAX_HEALTH, 520.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.38)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        this.setNoGravity(true);
        this.fallDistance = 0f;
        if (homeAnchor == null) {
            homeAnchor = arenaCenter != null ? Vec3.atBottomCenterOf(arenaCenter) : this.position();
        }
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().x, 0.72, this.getDeltaMovement().z);
        }

        LivingEntity target = this.getTarget();
        Vec3 anchor = (target != null && target.isAlive()) ? target.position() : homeAnchor;
        orbitAngle += (phase >= 2 ? 0.048f : 0.036f);
        double orbitRadius = (arenaCenter != null) ? 10.0 : 15.0;
        double desiredHoverY = anchor.y + (arenaCenter != null ? 7.5 : 12.0) + Math.sin(this.tickCount * 0.08) * 2.6;

        BlockPos checkHead = BlockPos.containing(this.getX(), this.getY() + 6.5, this.getZ());
        if (!serverLevel.getBlockState(checkHead).isAir()) {
            desiredHoverY = Math.min(desiredHoverY, this.getY() - 1.5);
        }

        Vec3 desired = new Vec3(
                anchor.x + Math.cos(orbitAngle) * orbitRadius,
                desiredHoverY,
                anchor.z + Math.sin(orbitAngle) * orbitRadius
        );
        Vec3 toDesired = desired.subtract(this.position());
        double dist = toDesired.length();
        Vec3 vel = dist > 0.4 ? toDesired.normalize().scale(Math.min(0.76, dist * 0.14)) : Vec3.ZERO;
        this.setDeltaMovement(this.getDeltaMovement().lerp(vel, 0.20));

        Vec3 lookVec = (getCastState() > 0 && target != null) ? target.position().subtract(this.position()) : vel;
        if (lookVec.horizontalDistanceSqr() > 0.002) {
            float targetYaw = (float) (Mth.atan2(lookVec.z, lookVec.x) * Mth.RAD_TO_DEG) - 90.0f;
            this.setYRot(Mth.rotLerp(0.16f, this.getYRot(), targetYaw));
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.calculateEntityAnimation(false);
    }

    @Override
    protected void onPhaseTransition(ServerLevel level, int newPhase) {
        super.onPhaseTransition(level, newPhase);
        ShockwaveRingEntity.spawn(level, this.position(), 15.0f, COLOR_CYAN, 16);
        ShockwaveRingEntity.spawn(level, this.position().add(0, 1.0, 0), 11.0f, COLOR_GOLD, 14);
        ModNetworking.sendShakeNearby(level, this.position(), 40.0, 2.2f, 14);
    }

    @Override
    protected int executeBossSkill(ServerLevel level, LivingEntity target) {
        float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (phase >= 2 ? 1.45 : 1.15));
        int move = cycle % 4;
        cycle++;

        switch (move) {
            case 0 -> {
                // SKILL 1: Aztec Sun-Tempest Twin Beam (Cyan + Emerald-Gold Sky Laser!)
                startCastAnimation(2, 32);
                level.playSound(null, this.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.5f, 1.1f);
                scheduleAction(10, null, () -> {
                    Vec3 mouth = this.position().add(0, 2.5, 0);
                    Vec3 dir = target.position().add(0, 1.0, 0).subtract(mouth).normalize();
                    LaserBeamEntity.spawn(level, this, mouth, mouth.add(dir.scale(34.0)), 1.0f, COLOR_CYAN, 22, dmg * 0.72f, false);
                    LaserBeamEntity.spawn(level, this, mouth, mouth.add(dir.scale(34.0)), 0.45f, COLOR_GOLD, 22, 0f, false);
                    ShockwaveRingEntity.spawn(level, target.position(), 7.5f, COLOR_EMERALD, 12);
                });
            }
            case 1 -> {
                // SKILL 2: Storm-God Lightning Magic Circle Constellation!
                startCastAnimation(2, 30);
                level.playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.6f, 1.25f);
                int count = phase >= 2 ? 7 : 5;
                int[] cols = { COLOR_CYAN, COLOR_GOLD, COLOR_EMERALD, COLOR_MAGENTA };
                for (int i = 0; i < count; i++) {
                    double a = (Math.PI * 2.0 * i) / count;
                    double r = (i == 0) ? 0.0 : (5.0 + level.random.nextDouble() * 5.0);
                    Vec3 spot = target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    MagicCircleEntity.spawn(level, this, spot, 3.6f, 18 + i * 2, cols[i % cols.length], dmg, true, false);
                }
            }
            case 2 -> {
                // SKILL 3: Rainbow-Plume Wind Crescent Barrage!
                startCastAnimation(1, 26);
                level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.8f, 1.15f);
                Vec3 origin = this.position().add(0, 2.2, 0);
                Vec3 aim = target.position().add(0, 1.0, 0).subtract(origin).normalize();
                double[] spreads = {-0.38, -0.14, 0.14, 0.38};
                int[] cols = { COLOR_MAGENTA, COLOR_CYAN, COLOR_EMERALD, COLOR_GOLD };
                for (int i = 0; i < spreads.length; i++) {
                    double ang = spreads[i];
                    Vec3 dir = new Vec3(aim.x * Math.cos(ang) - aim.z * Math.sin(ang), aim.y, aim.x * Math.sin(ang) + aim.z * Math.cos(ang)).normalize();
                    EnergySlashEntity.spawn(level, this, origin, dir, 1.4f, 6.8f, (i - 1.5f) * 18f, cols[i], dmg * 0.9f, false);
                }
            }
            default -> {
                // SKILL 4: Emerald Storm-Orb Volley + Sky Shockwave!
                startCastAnimation(3, 28);
                level.playSound(null, this.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.6f, 1.1f);
                Vec3 origin = this.position().add(0, 2.5, 0);
                Vec3 aim = target.position().add(0, 1.0, 0).subtract(origin).normalize();
                for (int i = -2; i <= 2; i++) {
                    double ang = i * 0.22;
                    Vec3 dir = new Vec3(aim.x * Math.cos(ang) - aim.z * Math.sin(ang), aim.y, aim.x * Math.sin(ang) + aim.z * Math.cos(ang)).normalize();
                    AbyssalOrbEntity.spawn(level, this, origin, dir.scale(1.15), 0.95f, (i % 2 == 0) ? COLOR_CYAN : COLOR_EMERALD, dmg * 0.85f, 3.4f, false, false);
                }
                ShockwaveRingEntity.spawn(level, this.position(), 11.0f, COLOR_GOLD, 14);
            }
        }
        return phase >= 2 ? 52 : 68;
    }

    @Override
    protected void spawnAmbientAura(ServerLevel level) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 2.0, this.getZ(), 4, 2.2, 1.2, 2.2, 0.03);
    }
}

