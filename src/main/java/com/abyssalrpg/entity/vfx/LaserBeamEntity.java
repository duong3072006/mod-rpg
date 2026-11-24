package com.abyssalrpg.entity.vfx;

import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Continuous 3D Volumetric Dragon-Breath / Soul Annihilation Laser Beam!
 */
public class LaserBeamEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_END_X = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_END_Y = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_END_Z = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MAX_LIFE = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.INT);

    private float damagePerHit = 10.0f;
    private boolean ignite = false;
    private UUID ownerUuid;

    public LaserBeamEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static LaserBeamEntity spawn(ServerLevel level, LivingEntity owner, Vec3 start, Vec3 end,
                                        float radius, int rgbColor, int durationTicks, float damagePerHit, boolean ignite) {
        LaserBeamEntity e = new LaserBeamEntity(ModEntities.LASER_BEAM, level);
        e.setPos(start.x, start.y, start.z);
        e.entityData.set(DATA_END_X, (float) end.x);
        e.entityData.set(DATA_END_Y, (float) end.y);
        e.entityData.set(DATA_END_Z, (float) end.z);
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_COLOR, rgbColor);
        e.entityData.set(DATA_MAX_LIFE, durationTicks);
        e.damagePerHit = damagePerHit;
        e.ignite = ignite;
        e.ownerUuid = owner != null ? owner.getUUID() : null;
        level.addFreshEntity(e);
        ModNetworking.sendShakeNearby(level, start, 34.0, 1.4f, durationTicks);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_END_X, 0f);
        builder.define(DATA_END_Y, 0f);
        builder.define(DATA_END_Z, 0f);
        builder.define(DATA_RADIUS, 0.7f);
        builder.define(DATA_COLOR, 0x3BF5D6);
        builder.define(DATA_MAX_LIFE, 22);
    }

    public Vec3 getEndPos() { return new Vec3(entityData.get(DATA_END_X), entityData.get(DATA_END_Y), entityData.get(DATA_END_Z)); }
    public float getBeamRadius() { return entityData.get(DATA_RADIUS); }
    public int getColor() { return entityData.get(DATA_COLOR); }
    public int getMaxLife() { return entityData.get(DATA_MAX_LIFE); }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 128 * 128;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) this.level();
        Vec3 start = this.position();
        Vec3 end = getEndPos();

        if (this.tickCount % 4 == 1) {
            Entity owner = ownerUuid != null ? serverLevel.getEntity(ownerUuid) : null;
            Vec3 diff = end.subtract(start);
            double len = diff.length();
            Vec3 dir = len > 0.01 ? diff.normalize() : new Vec3(0, 0, 1);
            float hitR = getBeamRadius() * 2.2f;
            for (double d = 1.0; d <= len; d += 1.8) {
                Vec3 pt = start.add(dir.scale(d));
                AABB box = new AABB(pt.x - hitR, pt.y - hitR, pt.z - hitR, pt.x + hitR, pt.y + hitR, pt.z + hitR);
                if (owner instanceof Player pOwner) {
                    if (damagePerHit > 0.0f) {
                        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                                e -> e != pOwner && e.isAlive())) {
                            target.hurt(serverLevel.damageSources().playerAttack(pOwner), damagePerHit);
                            if (ignite) target.igniteForSeconds(5);
                        }
                    }
                } else {
                    for (Player victim : serverLevel.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                        victim.hurt(owner instanceof LivingEntity l ? serverLevel.damageSources().mobAttack(l) : serverLevel.damageSources().magic(), damagePerHit);
                        if (ignite) victim.igniteForSeconds(5);
                    }
                }
            }
            serverLevel.sendParticles(ignite ? ParticleTypes.FLAME : ParticleTypes.END_ROD,
                    end.x, end.y, end.z, 8, 0.6, 0.6, 0.6, 0.06);
        }

        if (this.tickCount >= getMaxLife()) {
            this.discard();
        }
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

