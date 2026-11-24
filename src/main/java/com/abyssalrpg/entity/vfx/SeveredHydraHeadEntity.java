package com.abyssalrpg.entity.vfx;

import com.abyssalrpg.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Entity cosmetic cho đầu Spirit Hydra bị chặt rơi xuống đất (không va chạm, tồn tại 10s rồi tan thành hạt hồn).
 * Tối ưu chống lag: particle rỉ nhẹ chạy hoàn toàn phía Client, Server chỉ xử lý vật lý & biến mất sau 10s.
 */
public class SeveredHydraHeadEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(SeveredHydraHeadEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED =
            SynchedEntityData.defineId(SeveredHydraHeadEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SPIN_PITCH =
            SynchedEntityData.defineId(SeveredHydraHeadEntity.class, EntityDataSerializers.FLOAT);

    public static final int LIFETIME_TICKS = 200; // 10 giây
    private int ageTicks = 0;

    public SeveredHydraHeadEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = false;
    }

    public static SeveredHydraHeadEntity spawn(ServerLevel level, Vec3 pos, Vec3 velocity, float yaw, float scale, boolean enraged) {
        SeveredHydraHeadEntity e = new SeveredHydraHeadEntity(ModEntities.SEVERED_HYDRA_HEAD, level);
        e.setPos(pos.x, pos.y, pos.z);
        e.setYRot(yaw);
        e.yRotO = yaw;
        e.setDeltaMovement(velocity);
        e.entityData.set(DATA_SCALE, scale);
        e.entityData.set(DATA_ENRAGED, enraged);
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SCALE, 1.0f);
        builder.define(DATA_ENRAGED, false);
        builder.define(DATA_SPIN_PITCH, 0.0f);
    }

    public float getVisualScale() { return this.entityData.get(DATA_SCALE); }
    public boolean isEnraged() { return this.entityData.get(DATA_ENRAGED); }
    public float getSpinPitch() { return this.entityData.get(DATA_SPIN_PITCH); }

    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }

    @Override
    public void tick() {
        super.tick();
        this.ageTicks++;

        Vec3 vel = this.getDeltaMovement();
        if (!this.onGround()) {
            vel = vel.add(0.0, -0.055, 0.0);
            if (!this.level().isClientSide) {
                this.entityData.set(DATA_SPIN_PITCH, this.entityData.get(DATA_SPIN_PITCH) + 12.0f);
            }
        } else {
            vel = vel.multiply(0.65, 0.0, 0.65);
        }
        this.setDeltaMovement(vel);
        this.move(MoverType.SELF, vel);

        if (this.level().isClientSide) {
            if (this.ageTicks % 10 == 0) {
                this.level().addParticle(isEnraged() ? ParticleTypes.SCULK_SOUL : ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX(), this.getY() + 0.4, this.getZ(), 0.0, 0.04, 0.0);
            }
        } else if (this.ageTicks >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.ageTicks = tag.getInt("Age");
        if (tag.contains("Scale")) this.entityData.set(DATA_SCALE, tag.getFloat("Scale"));
        this.entityData.set(DATA_ENRAGED, tag.getBoolean("Enraged"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.ageTicks);
        tag.putFloat("Scale", getVisualScale());
        tag.putBoolean("Enraged", isEnraged());
    }
}
