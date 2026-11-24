package com.abyssalrpg.api;

/**
 * Interface cho các Boss sử dụng giới hạn máu tối đa riêng vượt trần vanilla 1024.0 của
 * `Attributes.MAX_HEALTH` (`RangedAttribute`, max = 1024.0).
 *
 * Được kết nối trực tiếp vào `LivingEntity.getMaxHealth()` (method `final`) và
 * `LivingEntity.readAdditionalSaveData(CompoundTag)` thông qua `LivingEntityMixin`.
 */
public interface BossHealthHolder {
    /**
     * Trả về máu tối đa riêng của boss. Trả về `<= 0.0f` nếu entity không dùng máu riêng
     * (khi đó `LivingEntityMixin` tự rơi về giá trị vanilla của `Attributes.MAX_HEALTH`).
     */
    float getBossMaxHealth();

    /**
     * Gán máu tối đa riêng của boss (đồng bộ qua `SynchedEntityData` / DataTracker sang client).
     */
    void setBossMaxHealth(float value);
}

