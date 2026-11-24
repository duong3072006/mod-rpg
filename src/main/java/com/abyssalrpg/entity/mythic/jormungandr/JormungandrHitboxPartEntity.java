package com.abyssalrpg.entity.mythic.jormungandr;

import com.abyssalrpg.config.JormungandrConfig;
import com.abyssalrpg.entity.mythic.JormungandrBossEntity;
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
 * Thực thể proxy hitbox vô hình dọc thân Boss Jörmungandr (10-14 proxy dọc theo {@link SerpentPath}).
 *
 * Đặc tính:
 * - KHÔNG lưu NBT (`noSave()` + `shouldBeSaved()=false`), KHÔNG AI, KHÔNG rơi đồ, KHÔNG va chạm địa hình.
 * - Nhận mọi đòn cận chiến, cung tên, trident và projectile của mod khác; chuyển tiếp toàn bộ về boss chính
 *   (giữ nguyên attacker, DamageSource, knockback = 0) với hệ số sát thương theo bộ phận:
 *   + ĐẦU (`HEAD`): ×1.5 (điểm yếu)
 *   + THÂN (`BODY`): ×1.0
 *   + 1/3 CUỐI ĐUÔI (`TAIL`): ×0.75
 * - Tự tắt `isPickable` khi boss đang lặn sâu dưới đất (`burrowDepth > 0.70`), tự hủy khi boss chết/despawn/đổi dimension.
 */
public class JormungandrHitboxPartEntity extends Entity {
    public enum PartZone {
        HEAD(0, "head"),
        BODY(1, "body"),
        TAIL(2, "tail");

        public final int id;
        public final String label;
        PartZone(int id, String label) { this.id = id; this.label = label; }
        public static PartZone byId(int id) {
            return switch (id) {
                case 0 -> HEAD;
                case 2 -> TAIL;
                default -> BODY;
            };
        }
    }

    private static final EntityDataAccessor<Float> DATA_WIDTH =
            SynchedEntityData.defineId(JormungandrHitboxPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(JormungandrHitboxPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ZONE =
            SynchedEntityData.defineId(JormungandrHitboxPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PARENT_ID =
            SynchedEntityData.defineId(JormungandrHitboxPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE =
            SynchedEntityData.defineId(JormungandrHitboxPartEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private JormungandrBossEntity parentCache;
    @Nullable
    private UUID parentUuid;
    private int proxyIndex = 0;

    public JormungandrHitboxPartEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public static JormungandrHitboxPartEntity create(ServerLevel level, JormungandrBossEntity parent,
                                                     int index, PartZone zone, float diameter) {
        JormungandrHitboxPartEntity p = new JormungandrHitboxPartEntity(ModEntities.JORMUNGANDR_HITBOX_PART, level);
        p.parentCache = parent;
        p.parentUuid = parent.getUUID();
        p.proxyIndex = index;
        p.entityData.set(DATA_ZONE, zone.id);
        p.entityData.set(DATA_PARENT_ID, parent.getId());
        p.setPartSize(diameter, diameter);
        p.setPos(parent.getX(), parent.getY(), parent.getZ());
        level.addFreshEntity(p);
        return p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WIDTH, 2.0f);
        builder.define(DATA_HEIGHT, 2.0f);
        builder.define(DATA_ZONE, PartZone.BODY.id);
        builder.define(DATA_PARENT_ID, -1);
        builder.define(DATA_ACTIVE, true);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_WIDTH.equals(key) || DATA_HEIGHT.equals(key)) {
            this.refreshDimensions();
        }
    }

    public void setPartSize(float width, float height) {
        float w = Math.max(0.75f, width);
        float h = Math.max(0.75f, height);
        if (Math.abs(getPartWidth() - w) > 0.03f || Math.abs(getPartHeight() - h) > 0.03f) {
            this.entityData.set(DATA_WIDTH, w);
            this.entityData.set(DATA_HEIGHT, h);
            this.refreshDimensions();
        }
    }

    public void setZone(PartZone zone) {
        if (this.entityData.get(DATA_ZONE) != zone.id) {
            this.entityData.set(DATA_ZONE, zone.id);
        }
    }

    public void setProxyActive(boolean active) {
        if (this.entityData.get(DATA_ACTIVE) != active) {
            this.entityData.set(DATA_ACTIVE, active);
        }
    }

    public float getPartWidth() { return this.entityData.get(DATA_WIDTH); }
    public float getPartHeight() { return this.entityData.get(DATA_HEIGHT); }
    public PartZone getZone() { return PartZone.byId(this.entityData.get(DATA_ZONE)); }
    public int getProxyIndex() { return this.proxyIndex; }

    public float getZoneDamageMultiplier() {
        JormungandrConfig cfg = JormungandrConfig.get();
        return switch (getZone()) {
            case HEAD -> cfg.headDamageMultiplier;
            case TAIL -> cfg.tailDamageMultiplier;
            default -> cfg.bodyDamageMultiplier;
        };
    }

    @Nullable
    public JormungandrBossEntity getParentBoss() {
        if (this.parentCache != null && !this.parentCache.isRemoved() && this.parentCache.level() == this.level()) {
            return this.parentCache;
        }
        int pid = this.entityData.get(DATA_PARENT_ID);
        if (pid >= 0) {
            Entity e = this.level().getEntity(pid);
            if (e instanceof JormungandrBossEntity j && !j.isRemoved()) {
                this.parentCache = j;
                return j;
            }
        }
        if (this.level() instanceof ServerLevel sl && this.parentUuid != null) {
            Entity e = sl.getEntity(this.parentUuid);
            if (e instanceof JormungandrBossEntity j && !j.isRemoved()) {
                this.parentCache = j;
                this.entityData.set(DATA_PARENT_ID, j.getId());
                return j;
            }
        }
        return null;
    }

    public void syncCenterFromPath(double cx, double cy, double cz, float yawDeg) {
        float h = getPartHeight();
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.setPos(cx, cy - h * 0.5, cz);
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
            JormungandrBossEntity parent = getParentBoss();
            if (parent == null || parent.isRemoved() || !parent.isAlive() || parent.level() != this.level()) {
                this.discard();
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || !this.entityData.get(DATA_ACTIVE)) {
            return false;
        }
        JormungandrBossEntity parent = getParentBoss();
        if (parent == null || parent.isRemoved() || !parent.isAlive()) {
            this.discard();
            return false;
        }
        if (source.getEntity() == parent || source.getDirectEntity() == parent) {
            return false;
        }
        return parent.hurtFromPart(this, source, amount);
    }

    @Override public boolean isPickable() { return this.entityData.get(DATA_ACTIVE); }
    @Override public boolean isAttackable() { return this.entityData.get(DATA_ACTIVE); }
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

