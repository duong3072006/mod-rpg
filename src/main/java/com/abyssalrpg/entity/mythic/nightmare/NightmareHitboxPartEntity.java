package com.abyssalrpg.entity.mythic.nightmare;

import com.abyssalrpg.entity.mythic.NightmareKnightBossEntity;
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
 * Thực thể proxy hitbox vô hình cho hệ thống Multipart của Boss Nightmare Knight (Phương án A).
 *
 * Đặc tính bắt buộc:
 * - KHÔNG lưu NBT (`EntityType.Builder.noSave()` + `shouldBeSaved() = false`), không summon trực tiếp (`noSummon()`).
 * - KHÔNG AI, KHÔNG rơi đồ, KHÔNG cản pathfinding (`noPhysics = true`, `canBeCollidedWith() = false`, `isPushable() = false`).
 * - Nhận mọi loại va chạm đòn đánh gần, cung tên, trident, và projectile của mod khác thông qua `isPickable() = true`
 *   và `isAttackable() = true`, sau đó chuyển tiếp nguyên vẹn `DamageSource` + attacker sang Boss cha (`knockback = 0`).
 * - Đồng bộ `width` / `height` qua DataTracker để client raycast & F3+B hiển thị đúng từng bộ phận (đầu/ngực/tay/chân).
 */
public class NightmareHitboxPartEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_WIDTH =
            SynchedEntityData.defineId(NightmareHitboxPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(NightmareHitboxPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_PART_NAME =
            SynchedEntityData.defineId(NightmareHitboxPartEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_PARENT_ID =
            SynchedEntityData.defineId(NightmareHitboxPartEntity.class, EntityDataSerializers.INT);

    @Nullable
    private NightmareKnightBossEntity parentCache;
    @Nullable
    private UUID parentUuid;

    public NightmareHitboxPartEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public static NightmareHitboxPartEntity create(ServerLevel level, NightmareKnightBossEntity parent,
                                                   String partName, float width, float height) {
        NightmareHitboxPartEntity part = new NightmareHitboxPartEntity(ModEntities.NIGHTMARE_HITBOX_PART, level);
        part.parentCache = parent;
        part.parentUuid = parent.getUUID();
        part.entityData.set(DATA_PART_NAME, partName);
        part.entityData.set(DATA_PARENT_ID, parent.getId());
        part.setPartDimensions(width, height);
        part.setPos(parent.getX(), parent.getY(), parent.getZ());
        level.addFreshEntity(part);
        return part;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WIDTH, 2.0f);
        builder.define(DATA_HEIGHT, 2.0f);
        builder.define(DATA_PART_NAME, "chest");
        builder.define(DATA_PARENT_ID, -1);
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

    @Nullable
    public NightmareKnightBossEntity getParentBoss() {
        if (this.parentCache != null && !this.parentCache.isRemoved()) {
            return this.parentCache;
        }
        int pid = this.entityData.get(DATA_PARENT_ID);
        if (pid >= 0) {
            Entity e = this.level().getEntity(pid);
            if (e instanceof NightmareKnightBossEntity nk && !nk.isRemoved()) {
                this.parentCache = nk;
                return nk;
            }
        }
        if (this.level() instanceof ServerLevel sl && this.parentUuid != null) {
            Entity e = sl.getEntity(this.parentUuid);
            if (e instanceof NightmareKnightBossEntity nk && !nk.isRemoved()) {
                this.parentCache = nk;
                this.entityData.set(DATA_PARENT_ID, nk.getId());
                return nk;
            }
        }
        return null;
    }

    /** Cập nhật vị trí hitbox bám theo tọa độ bone mỗi tick. */
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
            NightmareKnightBossEntity parent = getParentBoss();
            if (parent == null || parent.isRemoved() || !parent.isAlive()) {
                this.discard();
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) {
            return false;
        }
        NightmareKnightBossEntity parent = getParentBoss();
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
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
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

