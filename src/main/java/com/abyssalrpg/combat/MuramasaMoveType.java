package com.abyssalrpg.combat;

/**
 * Các pha đòn đánh, rút kiếm Iaijutsu và thức tỉnh Huyết Thệ của **Yêu Đao Muramasa Katana**.
 */
public enum MuramasaMoveType {
    /** Hit 1 — Kesagiri: Chém chéo sắc lẹm Phải-Trên -> Trái-Dưới (10.0t). */
    COMBO_1(0, 10.0F, 1.00F, false),
    /** Hit 2 — Gyaku-Kesa: Chém ngược liền mạch từ điểm kết Hit 1 Trái-Dưới -> Phải-Trên (10.0t). */
    COMBO_2(1, 10.0F, 1.12F, false),
    /** Hit 3 — Demon Cross-Cleave: Quét ngang huyết quang toàn lực + Hút máu (12.0t). */
    COMBO_3(2, 12.0F, 1.40F, false),
    /** RMB — Iaijutsu: Blood-Oath Draw (Thu kiếm vào vỏ -> Lướt rút kiếm 180° rạch sóng vàng-đỏ, 16.0t). */
    IAIJUTSU_DRAW(3, 16.0F, 2.20F, true),
    /** [R] / Shift+RMB — Muramasa Blood Oath Awakening (Giải phóng yêu quỷ Muramasa 12s, 14.0t). */
    BLOOD_OATH_AWAKEN(4, 14.0F, 0.0F, true);

    public final int id;
    public final float baseDisplayTicks;
    public final float damageMultiplier;
    public final boolean lockedSkill;

    MuramasaMoveType(int id, float baseDisplayTicks, float damageMultiplier, boolean lockedSkill) {
        this.id = id;
        this.baseDisplayTicks = baseDisplayTicks;
        this.damageMultiplier = damageMultiplier;
        this.lockedSkill = lockedSkill;
    }

    public boolean isComboMove() {
        return this == COMBO_1 || this == COMBO_2 || this == COMBO_3;
    }

    public static MuramasaMoveType byId(int id) {
        for (MuramasaMoveType m : values()) {
            if (m.id == id) {
                return m;
            }
        }
        return COMBO_1;
    }

    public static MuramasaMoveType comboByStep(int step) {
        return switch (Math.floorMod(step, 3)) {
            case 0 -> COMBO_1;
            case 1 -> COMBO_2;
            default -> COMBO_3;
        };
    }
}
