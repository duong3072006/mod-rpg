package com.abyssalrpg.combat;

/**
 * Danh sách chiêu thức đầy đủ của Dual Blade:
 * - `COMBO_1..COMBO_5`: Chuỗi 5 nhịp sắc nét (`11.5–14.0` tick có Pose Hold
 * 40%–80%).
 * - `SPRINT_THRUST` & `AIR_SLASH` (+ `SKILL_DASH` cắt ngang tiếp đất).
 * - Chiêu Chuột Phải "Crimson-Gold Flash Dash" 3 pha:
 * + `FLASH_DASH_CHARGE` (6.5t): Khuỵu thấp đổ người ~48°, duỗi thẳng 2 song đao
 * ra sau lưng nén đà!
 * + `FLASH_DASH_LAUNCH` (5.5t): Phóng lướt 10 block xuyên kẻ địch & boss với 2
 * dải Đỏ–Đen / Vàng–Đen song song!
 * + `FLASH_DASH_END` (7.0t): Hãm chân hạ thấp xoay chéo 2 kiếm dứt khoát khi 10
 * nhát chém bùng nổ!
 */
public enum DualBladeMoveType {
    COMBO_1((byte) 0, 11.5F),
    COMBO_2((byte) 1, 11.5F),
    COMBO_3((byte) 2, 12.5F),
    COMBO_4((byte) 3, 14.0F),
    COMBO_5((byte) 4, 13.5F),
    SPRINT_THRUST((byte) 5, 11.0F),
    AIR_SLASH((byte) 6, 11.5F),
    SKILL_DASH((byte) 7, 9.0F),
    FLASH_DASH_CHARGE((byte) 8, 6.5F),
    FLASH_DASH_LAUNCH((byte) 9, 6.5F),
    FLASH_DASH_END((byte) 10, 7.0F);

    public static final int MAIN_COMBO_COUNT = 5;

    public final byte id;
    public final float baseDisplayTicks;

    DualBladeMoveType(byte id, float baseDisplayTicks) {
        this.id = id;
        this.baseDisplayTicks = baseDisplayTicks;
    }

    public static DualBladeMoveType byId(byte id) {
        return switch (id) {
            case 0 -> COMBO_1;
            case 1 -> COMBO_2;
            case 2 -> COMBO_3;
            case 3 -> COMBO_4;
            case 4 -> COMBO_5;
            case 5 -> SPRINT_THRUST;
            case 6 -> AIR_SLASH;
            case 7 -> SKILL_DASH;
            case 8 -> FLASH_DASH_CHARGE;
            case 9 -> FLASH_DASH_LAUNCH;
            case 10 -> FLASH_DASH_END;
            default -> COMBO_1;
        };
    }

    public static DualBladeMoveType comboByIndex(int idx) {
        return switch (Math.floorMod(idx, MAIN_COMBO_COUNT)) {
            case 0 -> COMBO_1;
            case 1 -> COMBO_2;
            case 2 -> COMBO_3;
            case 3 -> COMBO_4;
            default -> COMBO_5;
        };
    }

    public boolean isHeavyFinisherOrCyclone() {
        return this == COMBO_4 || this == COMBO_5;
    }

    public boolean isFlashDashPhase() {
        return this == FLASH_DASH_CHARGE || this == FLASH_DASH_LAUNCH || this == FLASH_DASH_END;
    }
}
