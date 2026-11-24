package com.abyssalrpg.entity.vfx;

import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Gorgeous 3D Nested Rotating Crystalline Energy Meteor / Plasma Orb Projectile.
 * Replaces ugly vanilla SmallFireballs & WitherSkulls!
 */
public class AbyssalOrbEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(AbyssalOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(AbyssalOrbEntity.class, EntityDataSerializers.INT);

    private Vec3 velocity = Vec3.ZERO;
    private float damage = 12.0f;
    private float blastRadius = 3.2f;
    private boolean ignite = false;
    private boolean wither = false;
    private float gravity = 0.0f;
    private boolean soulSlowZone = false;
    private UUID ownerUuid;

    public AbyssalOrbEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static AbyssalOrbEntity spawn(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 vel,
                                         float size, int rgbColor, float damage, float blastRadius, boolean ignite, boolean wither) {
        return spawnParabolic(level, owner, origin, vel, 0.0f, size, rgbColor, damage, blastRadius, ignite, wither, false);
    }

    /**
     * Phóng cầu hồn theo quỹ đạo parabol (có trọng lực `gravity`) và tùy chọn tạo vùng linh hồn gây chậm khi nổ.
     */
    public static AbyssalOrbEntity spawnParabolic(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 vel,
                                                  float gravity, float size, int rgbColor, float damage, float blastRadius,
                                                  boolean ignite, boolean wither, boolean soulSlowZone) {
        AbyssalOrbEntity e = new AbyssalOrbEntity(ModEntities.ABYSSAL_ORB, level);
        e.setPos(origin.x, origin.y, origin.z);
        e.velocity = vel;
        e.setDeltaMovement(vel);
        e.gravity = gravity;
        e.entityData.set(DATA_SIZE, size);
        e.entityData.set(DATA_COLOR, rgbColor);
        e.damage = damage;
        e.blastRadius = blastRadius;
        e.ignite = ignite;
        e.wither = wither;
        e.soulSlowZone = soulSlowZone;
        e.ownerUuid = owner != null ? owner.getUUID() : null;
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, 0.9f);
        builder.define(DATA_COLOR, 0xFF6A14);
    }

    public float getOrbSize() { return entityData.get(DATA_SIZE); }
    public int getColor() { return entityData.get(DATA_COLOR); }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.setPos(this.getX() + this.getDeltaMovement().x, this.getY() + this.getDeltaMovement().y, this.getZ() + this.getDeltaMovement().z);
            return;
        }
        if (gravity > 0.0f) {
            velocity = velocity.add(0.0, -gravity, 0.0);
        }
        this.setDeltaMovement(velocity);
        Vec3 next = this.position().add(velocity);
        this.setPos(next.x, next.y, next.z);

        ServerLevel serverLevel = (ServerLevel) this.level();
        serverLevel.sendParticles(ignite ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
                this.getX(), this.getY(), this.getZ(), 3, 0.25, 0.25, 0.25, 0.02);

        boolean hitSolid = !serverLevel.getBlockState(this.blockPosition()).isAir();
        boolean hitPlayer = !serverLevel.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(1.2),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()).isEmpty();

        if (hitSolid || hitPlayer || this.tickCount >= 95) {
            explode(serverLevel);
            this.discard();
        }
    }

    private void explode(ServerLevel level) {
        level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.25f, 0.85f);
        ModNetworking.sendShakeNearby(level, this.position(), 22.0, 1.0f, 6);
        ShockwaveRingEntity.spawn(level, this.position().add(0, 0.2, 0), blastRadius * 1.4f, getColor(), 10);
        level.sendParticles(ignite ? ParticleTypes.LAVA : ParticleTypes.SOUL,
                this.getX(), this.getY() + 0.4, this.getZ(), 20, 0.8, 0.8, 0.8, 0.08);

        Entity owner = ownerUuid != null ? level.getEntity(ownerUuid) : null;
        if (soulSlowZone) {
            AreaEffectCloud cloud = new AreaEffectCloud(level, this.getX(), this.getY() + 0.15, this.getZ());
            if (owner instanceof LivingEntity l) {
                cloud.setOwner(l);
            }
            cloud.setParticle(ParticleTypes.SOUL_FIRE_FLAME);
            cloud.setRadius(Math.max(2.5f, blastRadius * 0.9f));
            cloud.setRadiusPerTick(-0.01f);
            cloud.setDuration(100);
            cloud.setWaitTime(5);
            cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1));
            level.addFreshEntity(cloud);
        }
        AABB box = new AABB(this.getX() - blastRadius, this.getY() - blastRadius, this.getZ() - blastRadius,
                this.getX() + blastRadius, this.getY() + blastRadius, this.getZ() + blastRadius);
        for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
            if (p.distanceToSqr(this) <= blastRadius * blastRadius) {
                p.hurt(owner instanceof LivingEntity l ? level.damageSources().mobAttack(l) : level.damageSources().magic(), damage);
                if (ignite) p.igniteForSeconds(5);
                if (wither) p.addEffect(new MobEffectInstance(MobEffects.WITHER, 90, 1));
                if (soulSlowZone) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            }
        }
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

