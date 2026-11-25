package com.abyssalrpg.combat;

/**
 * Các động tác animation toàn thân & biến hình của vũ khí **Togetsu (Trăng Non & Trăng Khuyết / Hợp Nhất Song Nguyệt)**.
 */
public enum TogetsuMoveType {
    DUAL_1(0, 8.0F),                // Hit 1: Chém đơn Tay Phải (Lưỡi Đen Trăng Khuyết)
    DUAL_2(1, 8.0F),                // Hit 2: Chém đơn Tay Trái (Lưỡi Trắng Trăng Non)
    DUAL_3(2, 8.5F),                // Hit 3: Chém hất đơn Tay Phải (Lưỡi Đen)
    DUAL_4(3, 8.5F),                // Hit 4: Chém ngược đơn Tay Trái (Lưỡi Trắng + Moon Mark)
    FUSE_TRANSFORM(4, 18.0F),
    UNFUSE_SPLIT(5, 12.0F),
    FUSED_1(6, 7.5F),               // Song Nguyệt Hit 1: Bổ chém cắm xuống Phải -> Trái (0.3s cadence!)
    FUSED_2(7, 7.5F),               // Song Nguyệt Hit 2: Bổ chém cắm xuống Trái -> Phải (0.3s cadence!)
    FUSED_3(8, 8.0F),               // Song Nguyệt Hit 3: Chém hất vòng cung 1 tay thanh thoát Thấp-Trái -> Cao-Phải (0.3s cadence!)
    CRESCENT_VORPAL(9, 18.0F),
    DUAL_PIN_LUNGE(10, 6.0F),
    DUAL_PIN_FLURRY(11, 25.0F),
    CRESCENT_VORPAL_CHARGE(12, 40.0F),
    DUAL_5(13, 10.5F),              // Hit 5 Đòn Đánh Đặc Biệt: Auto-Fuse Eclipse Morphing Finisher (0.52s + hit-stop!)
    DUAL_SPRINT_THRUST(14, 9.5F),
    DUAL_AIR_SLASH(15, 10.5F),
    DUAL_LANDING_DASH(16, 8.5F),
    CRESCENT_WAVE_SLASH(17, 10.5F); // Signature RMB: Quick Draw-Slash launching spinning B&W crescent projectile wave!

    public static final int DUAL_COMBO_COUNT = 5;
    public static final int FUSED_COMBO_COUNT = 3;

    public final byte id;
    public final float baseDisplayTicks;

    TogetsuMoveType(int id, float baseDisplayTicks) {
        this.id = (byte) id;
        this.baseDisplayTicks = baseDisplayTicks;
    }

    public boolean isFusedMove() {
        return this == FUSED_1 || this == FUSED_2 || this == FUSED_3
                || this == CRESCENT_VORPAL || this == CRESCENT_VORPAL_CHARGE
                || this == CRESCENT_WAVE_SLASH;
    }

    public boolean isDualComboMove() {
        return this == DUAL_1 || this == DUAL_2 || this == DUAL_3 || this == DUAL_4 || this == DUAL_5
                || this == DUAL_SPRINT_THRUST || this == DUAL_AIR_SLASH || this == DUAL_LANDING_DASH;
    }

    public boolean isLockedSkill() {
        return this == FUSE_TRANSFORM || this == UNFUSE_SPLIT
                || this == CRESCENT_VORPAL || this == CRESCENT_VORPAL_CHARGE
                || this == DUAL_PIN_LUNGE || this == DUAL_PIN_FLURRY;
    }

    /**
     * Chọn đòn Song Đao theo đúng tay điều khiển:
     * - Chuột Trái (`rightBlade = false`): Kiếm Trái (Trăng Non — Lưỡi Trắng): luân phiên `DUAL_2` & `DUAL_4` (+ Moon Mark).
     * - Chuột Phải (`rightBlade = true`): Kiếm Phải (Trăng Khuyết — Lưỡi Đen): luân phiên `DUAL_1` & `DUAL_3`.
     * - Nhát thứ 5 tổng hợp (`step % 5 == 4`): tự động bùng nổ Đòn Đặc Biệt `DUAL_5` (Auto-Fuse Eclipse Finisher)!
     */
    public static TogetsuMoveType resolveDualHandMove(boolean rightBlade, boolean onGround, boolean inWater,
                                                      float fallDistance, boolean sprinting,
                                                      int totalStep, int handVariant) {
        if (!onGround && !inWater && fallDistance > 0.05F) {
            return DUAL_AIR_SLASH;
        }
        int mod = Math.floorMod(totalStep, DUAL_COMBO_COUNT);
        if (mod == 4) {
            return DUAL_5;
        }
        if (sprinting && onGround && mod == 0) {
            return DUAL_SPRINT_THRUST;
        }
        if (rightBlade) {
            return (Math.floorMod(handVariant, 2) == 0) ? DUAL_1 : DUAL_3;
        }
        return (Math.floorMod(handVariant, 2) == 0) ? DUAL_2 : DUAL_4;
    }

    /** Chọn đòn Eclipse Style (5 nhịp + Sprint Thrust + Air Slash)! */
    public static TogetsuMoveType resolveDualMove(boolean onGround, boolean inWater, float fallDistance,
                                                  boolean sprinting, int step) {
        return resolveDualHandMove(false, onGround, inWater, fallDistance, sprinting, step, step);
    }

    public static TogetsuMoveType dualByStep(int step) {
        return switch (Math.floorMod(step, DUAL_COMBO_COUNT)) {
            case 0 -> DUAL_1;
            case 1 -> DUAL_2;
            case 2 -> DUAL_3;
            case 3 -> DUAL_4;
            default -> DUAL_5;
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
