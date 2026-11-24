package com.abyssalrpg.combat;

/**
 * Các động tác animation toàn thân & biến hình của vũ khí **Togetsu (Trăng Non & Trăng Khuyết / Hợp Nhất Song Nguyệt)**.
 */
public enum TogetsuMoveType {
    DUAL_1(0, 10.5F),
    DUAL_2(1, 10.5F),
    DUAL_3(2, 11.5F),
    DUAL_4(3, 12.5F),
    FUSE_TRANSFORM(4, 18.0F),
    UNFUSE_SPLIT(5, 12.0F),
    FUSED_1(6, 15.0F),
    FUSED_2(7, 15.0F),
    FUSED_3(8, 16.0F),
    CRESCENT_VORPAL(9, 18.0F),
    DUAL_PIN_LUNGE(10, 6.0F),
    DUAL_PIN_FLURRY(11, 25.0F),
    CRESCENT_VORPAL_CHARGE(12, 40.0F);

    public static final int DUAL_COMBO_COUNT = 4;
    public static final int FUSED_COMBO_COUNT = 3;

    public final byte id;
    public final float baseDisplayTicks;

    TogetsuMoveType(int id, float baseDisplayTicks) {
        this.id = (byte) id;
        this.baseDisplayTicks = baseDisplayTicks;
    }

    public boolean isFusedMove() {
        return this == FUSED_1 || this == FUSED_2 || this == FUSED_3
                || this == CRESCENT_VORPAL || this == CRESCENT_VORPAL_CHARGE;
    }

    public boolean isLockedSkill() {
        return this == FUSE_TRANSFORM || this == UNFUSE_SPLIT
                || this == CRESCENT_VORPAL || this == CRESCENT_VORPAL_CHARGE
                || this == DUAL_PIN_LUNGE || this == DUAL_PIN_FLURRY;
    }

    public static TogetsuMoveType dualByStep(int step) {
        return switch (Math.floorMod(step, DUAL_COMBO_COUNT)) {
            case 0 -> DUAL_1;
            case 1 -> DUAL_2;
            case 2 -> DUAL_3;
            default -> DUAL_4;
        };
    }

    public static TogetsuMoveType fusedByStep(int step) {
        return switch (Math.floorMod(step, FUSED_COMBO_COUNT)) {
            case 0 -> FUSED_1;
            case 1 -> FUSED_2;
            default -> FUSED_3;
        };
    }

    public static TogetsuMoveType byId(int id) {
        for (TogetsuMoveType m : values()) {
            if (m.id == id) return m;
        }
        return DUAL_1;
    }
}
