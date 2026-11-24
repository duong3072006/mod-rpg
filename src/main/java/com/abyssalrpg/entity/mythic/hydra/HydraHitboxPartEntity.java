package com.abyssalrpg.entity.mythic.hydra;

import com.abyssalrpg.entity.mythic.HydraBossEntity;
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
 * Thực thể proxy hitbox vô hình cho hệ thống Multipart 4 Đầu + Cổ + Thân/Chân/Đuôi của Spirit Hydra:
 * - 4 proxy Đầu (`head_1..4`, `headIndex = 0..3`) + 4 proxy Cổ (`neck_1..4`, `headIndex = 0..3`):
 *   + Chỉ nhận đòn khi đầu `headIndex` còn sống (`active == true`).
 *   + Chuyển tiếp đòn đánh trực tiếp vào bể máu `headHp[headIndex]` với hệ số `x1.15` (config được).
 * - 3 proxy Thân/Chân/Đuôi (`chest_legs`, `pelvis_legs`, `tail`, `headIndex = -1`):
 *   + Luôn nhận đòn bình thường (x1.0) và trừ vào đầu còn sống có máu thấp nhất hiện tại (không bao giờ soft-lock).
 * - Không lưu NBT (`noSave()`, `shouldBeSaved() = false`), không va chạm vật lý (`noPhysics = true`),
 *   hỗ trợ đầy đủ cận chiến, cung tên, đinh ba & projectile mod khác; hiển thị khung F3+B chuẩn xác.
 */
public class HydraHitboxPartEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_WIDTH =
            SynchedEntityData.defineId(HydraHitboxPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(HydraHitboxPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_PART_NAME =
            SynchedEntityData.defineId(HydraHitboxPartEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_PARENT_ID =
            SynchedEntityData.defineId(HydraHitboxPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEAD_INDEX =
            SynchedEntityData.defineId(HydraHitboxPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE =
            SynchedEntityData.defineId(HydraHitboxPartEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private HydraBossEntity parentCache;
    @Nullable
    private UUID parentUuid;

    public HydraHitboxPartEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public static HydraHitboxPartEntity create(ServerLevel level, HydraBossEntity parent,
                                               String partName, int headIndex, float width, float height) {
        HydraHitboxPartEntity part = new HydraHitboxPartEntity(ModEntities.HYDRA_HITBOX_PART, level);
        part.parentCache = parent;
        part.parentUuid = parent.getUUID();
        part.entityData.set(DATA_PART_NAME, partName);
        part.entityData.set(DATA_PARENT_ID, parent.getId());
        part.entityData.set(DATA_HEAD_INDEX, headIndex);
        part.entityData.set(DATA_ACTIVE, true);
        part.setPartDimensions(width, height);
        part.setPos(parent.getX(), parent.getY(), parent.getZ());
        level.addFreshEntity(part);
        return part;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WIDTH, 2.4f);
        builder.define(DATA_HEIGHT, 2.4f);
        builder.define(DATA_PART_NAME, "chest_legs");
        builder.define(DATA_PARENT_ID, -1);
        builder.define(DATA_HEAD_INDEX, -1);
        builder.define(DATA_ACTIVE, true);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_WIDTH.equals(key) || DATA_HEIGHT.equals(key) || DATA_ACTIVE.equals(key)) {
            this.refreshDimensions();
        }
    }

    public void setPartDimensions(float width, float height) {
        float w = Math.max(0.2f, width);
        float h = Math.max(0.2f, height);
        if (Math.abs(getPartWidth() - w) > 0.02f || Math.abs(getPartHeight() - h) > 0.02f) {
            this.entityData.set(DATA_WIDTH, w);
            this.entityData.set(DATA_HEIGHT, h);
            this.refreshDimensions();
        }
    }

    public void setPartActive(boolean active) {
        if (isPartActive() != active) {
            this.entityData.set(DATA_ACTIVE, active);
            this.refreshDimensions();
        }
    }

    public boolean isPartActive() {
        return this.entityData.get(DATA_ACTIVE);
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

    /** Trả về `0..3` nếu thuộc đầu/cổ thứ `1..4`, hoặc `-1` nếu là thân/chân/đuôi. */
    public int getHeadIndex() {
        return this.entityData.get(DATA_HEAD_INDEX);
    }

    public boolean isHeadOrNeckPart() {
        int idx = getHeadIndex();
        return idx >= 0 && idx < HydraBossEntity.HEAD_COUNT;
    }

    @Nullable
    public HydraBossEntity getParentBoss() {
        if (this.parentCache != null && !this.parentCache.isRemoved()) {
            return this.parentCache;
        }
        int pid = this.entityData.get(DATA_PARENT_ID);
        if (pid >= 0) {
            Entity e = this.level().getEntity(pid);
            if (e instanceof HydraBossEntity hydra && !hydra.isRemoved()) {
                this.parentCache = hydra;
                return hydra;
            }
        }
        if (this.level() instanceof ServerLevel sl && this.parentUuid != null) {
            Entity e = sl.getEntity(this.parentUuid);
            if (e instanceof HydraBossEntity hydra && !hydra.isRemoved()) {
                this.parentCache = hydra;
                this.entityData.set(DATA_PARENT_ID, hydra.getId());
                return hydra;
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
        if (!isPartActive()) {
            return EntityDimensions.scalable(0.1f, 0.1f);
        }
        return EntityDimensions.scalable(getPartWidth(), getPartHeight());
    }

    @Override
    public void tick() {
        this.baseTick();
        if (!this.level().isClientSide) {
            HydraBossEntity parent = getParentBoss();
            if (parent == null || parent.isRemoved() || !parent.isAlive()) {
                this.discard();
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || !isPartActive()) {
            return false;
        }
        HydraBossEntity parent = getParentBoss();
        if (parent == null || parent.isRemoved() || !parent.isAlive()) {
            this.discard();
            return false;
        }
        if (source.getEntity() == parent || source.getDirectEntity() == parent) {
            return false;
        }
        return parent.hurtFromPart(this, source, amount);
    }

    @Override
    public boolean isPickable() {
        return isPartActive();
    }

    @Override
    public boolean isAttackable() {
        return isPartActive();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || (this.parentCache != null && this.parentCache == entity);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

