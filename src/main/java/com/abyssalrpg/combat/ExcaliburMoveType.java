package com.abyssalrpg.combat;

/**
 * Các pha đòn đánh, tuyệt kỹ chùm sáng và biến thân của **Excalibur / Excalibur Morgan**.
 */
public enum ExcaliburMoveType {
    /** Hit 1 — Diagonal Golden / Dark Cleave (0.52s = 10.5 ticks, nối đà mượt trực tiếp sang Hit 2 ở tick 7!). */
    COMBO_1(0, 10.5F, 1.00F, false),
    /** Hit 2 — Reverse Fluid Knight Sweep (0.52s = 10.5 ticks, xuất phát ngay điểm kết Hit 1 -> nối đà lên Hit 3!). */
    COMBO_2(1, 10.5F, 1.10F, false),
    /** Hit 3 — Heavy Two-Handed Overhead Judgment Slam (0.62s = 12.5 ticks, có 0.08s hit-stop!). */
    COMBO_3(2, 12.5F, 1.35F, false),
    /** Normal Ultimate (RMB — 'EXCALIBUR!'): 0.65s Skyward Charge + 0.10s Apex Flash + 0.25s Slam + 0.20s Follow-Through = 24 ticks (1.20s). */
    ULT_EXCALIBUR(3, 24.0F, 2.60F, true),
    /** Morgan Ultimate (RMB — 'EXCALIBUR MORGAN!'): 24 ticks (1.20s), chùm tia Đen–Tím 18 block diện rộng! */
    ULT_MORGAN(4, 24.0F, 3.65F, true),
    /** 4.0s Dark Mana Gathering Channel ([R] 80 ticks). */
    MORGAN_CHANNEL(5, 80.0F, 0.0F, true),
    /** Explosive Awakening Burst at 4.0s (14 ticks = 0.70s). */
    MORGAN_AWAKEN(6, 14.0F, 0.0F, true);

    public final int id;
    public final float baseDisplayTicks;
    public final float damageMultiplier;
    public final boolean lockedSkill;

    ExcaliburMoveType(int id, float baseDisplayTicks, float damageMultiplier, boolean lockedSkill) {
        this.id = id;
        this.baseDisplayTicks = baseDisplayTicks;
        this.damageMultiplier = damageMultiplier;
        this.lockedSkill = lockedSkill;
    }

    public boolean isComboMove() {
        return this == COMBO_1 || this == COMBO_2 || this == COMBO_3;
    }

    public boolean isUltimate() {
        return this == ULT_EXCALIBUR || this == ULT_MORGAN;
    }

    public static ExcaliburMoveType byId(int id) {
        for (ExcaliburMoveType t : values()) {
            if (t.id == id) {
                return t;
            }
        }
        return COMBO_1;
    }

    public static ExcaliburMoveType comboByStep(int step) {
        return switch (Math.floorMod(step, 3)) {
            case 0 -> COMBO_1;
            case 1 -> COMBO_2;
            default -> COMBO_3;
        };
    }
}
