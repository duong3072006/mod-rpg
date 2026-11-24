package com.abyssalrpg.entity.vfx;

import com.abyssalrpg.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Pure 3D Expanding Glowing Ground Shockwave Ring + Rising Energy Wall!
 */
public class ShockwaveRingEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_MAX_RADIUS = SynchedEntityData.defineId(ShockwaveRingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ShockwaveRingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MAX_LIFE = SynchedEntityData.defineId(ShockwaveRingEntity.class, EntityDataSerializers.INT);

    public ShockwaveRingEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static ShockwaveRingEntity spawn(ServerLevel level, Vec3 pos, float maxRadius, int rgbColor, int durationTicks) {
        ShockwaveRingEntity e = new ShockwaveRingEntity(ModEntities.SHOCKWAVE_RING, level);
        e.setPos(pos.x, pos.y + 0.08, pos.z);
        e.entityData.set(DATA_MAX_RADIUS, maxRadius);
        e.entityData.set(DATA_COLOR, rgbColor);
        e.entityData.set(DATA_MAX_LIFE, Math.max(4, durationTicks));
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MAX_RADIUS, 8.0f);
        builder.define(DATA_COLOR, 0xFF6A14);
        builder.define(DATA_MAX_LIFE, 14);
    }

    public float getMaxRadius() { return entityData.get(DATA_MAX_RADIUS); }
    public int getColor() { return entityData.get(DATA_COLOR); }
    public int getMaxLife() { return entityData.get(DATA_MAX_LIFE); }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount >= getMaxLife()) {
            this.discard();
        }
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

