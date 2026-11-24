package com.abyssalrpg.api;

/**
 * Interface dùng chung cho các Boss có chỉ số Phòng thủ (Defense) vượt trần vanilla
 * (`Attributes.ARMOR` bị kẹp tối đa 30.0 trong Minecraft 1.21.1).
 *
 * Giá trị được đồng bộ qua `SynchedEntityData` (DataTracker) và lưu/nạp vào NBT,
 * kết hợp công thức giảm sát thương theo dạng vanilla `CombatRules` nhưng dùng
 * `defense`, `bossToughness`, trần giảm sát thương `maxDamageReduction` (<= 0.90)
 * và sàn sát thương mỗi đòn `minDamagePerHit`.
 */
public interface BossDefenseHolder {
    /** Trả về chỉ số phòng thủ tùy chỉnh hiện tại (<= 0 nghĩa là fallback về giáp vanilla). */
    float getBossDefense();

    /** Cập nhật chỉ số phòng thủ tùy chỉnh (>= 0). */
    void setBossDefense(float value);

    /** Chỉ số độ cứng giáp tùy chỉnh dùng trong mẫu số `2 + toughness / 4`. */
    default float getBossToughness() {
        return 0.0f;
    }

    /** Hệ số giảm sát thương tối đa cho phép (mặc định 0.70, kẹp cứng không vượt quá 0.90). */
    default float getMaxDamageReduction() {
        return 0.70f;
    }

    /** Sát thương tối thiểu mỗi đòn trúng đích (mặc định 0.5 HP) để đòn nào cũng có tác dụng. */
    default float getMinDamagePerHit() {
        return 0.5f;
    }
}
