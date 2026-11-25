package com.abyssalrpg.combat;

/**
 * Các nhịp tấn công & kỹ năng biển của cặp vũ khí **Hải Thần Đinh Ba & Đèn Lồng Biển (`abyssalrpg:sea_trident`)**:
 * - Tay Phải: **Đinh Ba Biển** (3 nhịp đâm/quét/giáng + kỹ năng Giơ Đinh Ba Lên Trời triệu hồi **5 Đinh Ba Phóng Mục Tiêu** `[R]` / `Shift+RMB`).
 * - Tay Trái: **Đèn Lồng Biển** (kỹ năng triệu hồi **Sóng Thần** khổng lồ `RMB` + nội tại Hộ Mệnh Đại Dương).
 */
public enum SeaWeaponsMoveType {
    THRUST_1(0, 10.0F, false),
    SWEEP_2(1, 11.0F, false),
    TRIDENT_SLAM_3(2, 14.0F, false),
    LANTERN_TIDAL_WAVE(3, 22.0F, true),
    TRIDENT_WHIRLPOOL(4, 28.0F, true);

    public final byte id;
    public final float baseDisplayTicks;
    public final boolean lockedSkill;

    SeaWeaponsMoveType(int id, float baseDisplayTicks, boolean lockedSkill) {
        this.id = (byte) id;
        this.baseDisplayTicks = baseDisplayTicks;
        this.lockedSkill = lockedSkill;
    }

    public static SeaWeaponsMoveType byId(byte id) {
        for (SeaWeaponsMoveType t : values()) {
            if (t.id == id) {
                return t;
            }
        }
        return THRUST_1;
    }

    public static SeaWeaponsMoveType comboByStep(int step) {
        return switch (Math.floorMod(step, 3)) {
            case 0 -> THRUST_1;
            case 1 -> SWEEP_2;
            default -> TRIDENT_SLAM_3;
        };
    }
}
