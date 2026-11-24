package com.abyssalrpg.entity.mythic.cinder;

import com.abyssalrpg.entity.mythic.CinderDragonBossEntity;
import com.abyssalrpg.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Proxy hitbox vô hình cho hệ thống Multipart của Boss Cinder Dragon:
 * - Bám theo đầu/cổ (`×1.25`), thân ngực (`×1.0`), cánh trái/phải (`×0.85`), và đuôi (`×0.85`).
 * - KHÔNG lưu NBT (`noSave()` + `shouldBeSaved() = false`), KHÔNG AI, KHÔNG va chạm địa hình (`noPhysics = true`).
 * - Chuyển tiếp toàn bộ sát thương (cận chiến, cung tên, trident, projectile mod) về boss chính với `knockback = 0`.
 * - Tự hủy tức thì nếu boss chết, bị `/kill`, despawn hoặc đổi dimension -> 0 rò rỉ proxy.
 */
public class CinderDragonHitboxPartEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_WIDTH =
            SynchedEntityData.defineId(CinderDragonHitboxPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(CinderDragonHitboxPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_PART_NAME =
            SynchedEntityData.defineId(CinderDragonHitboxPartEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_PARENT_ID =
            SynchedEntityData.defineId(CinderDragonHitboxPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE_MULT =
            SynchedEntityData.defineId(CinderDragonHitboxPartEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    private CinderDragonBossEntity parentCache;
    @Nullable
    private UUID parentUuid;

    public CinderDragonHitboxPartEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public static CinderDragonHitboxPartEntity create(ServerLevel level, CinderDragonBossEntity parent,
                                                      String partName, float width, float height, float dmgMult) {
        CinderDragonHitboxPartEntity part = new CinderDragonHitboxPartEntity(ModEntities.CINDER_DRAGON_HITBOX_PART, level);
        part.parentCache = parent;
        part.parentUuid = parent.getUUID();
        part.entityData.set(DATA_PART_NAME, partName);
        part.entityData.set(DATA_PARENT_ID, parent.getId());
        part.entityData.set(DATA_DAMAGE_MULT, dmgMult);
        part.setPartDimensions(width, height);
        part.setPos(parent.getX(), parent.getY(), parent.getZ());
        level.addFreshEntity(part);
        return part;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WIDTH, 2.4f);
        builder.define(DATA_HEIGHT, 2.4f);
        builder.define(DATA_PART_NAME, "body_chest");
        builder.define(DATA_PARENT_ID, -1);
        builder.define(DATA_DAMAGE_MULT, 1.0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_WIDTH.equals(key) || DATA_HEIGHT.equals(key)) {
            this.refreshDimensions();
        }
    }

    public void setPartDimensions(float width, float height) {
        float w = Math.max(0.4f, width);
        float h = Math.max(0.4f, height);
        if (Math.abs(getPartWidth() - w) > 0.02f || Math.abs(getPartHeight() - h) > 0.02f) {
            this.entityData.set(DATA_WIDTH, w);
            this.entityData.set(DATA_HEIGHT, h);
            this.refreshDimensions();
        }
    }

    public float getPartWidth() {
        return this.entityData.get(DATA_WIDTH);
    }

    public float getPartHeight() {
        return this.entityData.get(DATA_HEIGHT);
    }

    public String getPartName() {
        return this.entityData.get(DATA_PART_NAME);
    }

    public float getDamageMultiplier() {
        return this.entityData.get(DATA_DAMAGE_MULT);
    }

    @Nullable
    public CinderDragonBossEntity getParentBoss() {
        if (this.parentCache != null && !this.parentCache.isRemoved() && this.parentCache.level() == this.level()) {
            return this.parentCache;
        }
        int pid = this.entityData.get(DATA_PARENT_ID);
        if (pid >= 0) {
            Entity e = this.level().getEntity(pid);
            if (e instanceof CinderDragonBossEntity cd && !cd.isRemoved()) {
                this.parentCache = cd;
                return cd;
            }
        }
        if (this.level() instanceof ServerLevel sl && this.parentUuid != null) {
            Entity e = sl.getEntity(this.parentUuid);
            if (e instanceof CinderDragonBossEntity cd && !cd.isRemoved()) {
                this.parentCache = cd;
                this.entityData.set(DATA_PARENT_ID, cd.getId());
                return cd;
            }
        }
        return null;
    }

    public void syncPositionFromBone(double centerX, double bottomY, double centerZ, float yawDeg) {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.setPos(centerX, bottomY, centerZ);
        this.setYRot(yawDeg);
        this.yRotO = yawDeg;
        this.setDeltaMovement(0.0, 0.0, 0.0);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(getPartWidth(), getPartHeight());
    }

    @Override
    public void tick() {
        this.baseTick();
        if (!this.level().isClientSide) {
            CinderDragonBossEntity parent = getParentBoss();
            if (parent == null || parent.isRemoved() || !parent.isAlive() || parent.level() != this.level()) {
                this.discard();
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) {
            return false;
        }
        CinderDragonBossEntity parent = getParentBoss();
        if (parent == null || parent.isRemoved() || !parent.isAlive()) {
            this.discard();
            return false;
        }
        if (source.getEntity() == parent || source.getDirectEntity() == parent) {
            return false;
        }
        return parent.hurtFromPart(this, source, amount);
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isAttackable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean fireImmune() { return true; }

    @Override
    public boolean is(Entity entity) {
        return this == entity || (this.parentCache != null && this.parentCache == entity);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

