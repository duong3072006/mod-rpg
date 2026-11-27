package com.abyssalrpg.combat;

/**
 * Hệ thống chiến đấu toàn diện cho **Damascus Katana (`abyssalrpg:katana`)**:
 * - **Chuỗi 4 đòn thường liền mạch:** `HIT_1_DRAW` -> `HIT_2_DOWN_DIAG` -> `HIT_3_UP_DIAG` -> `HIT_4_FINISHER` (+ `CHIBURI_NOTO`).
 * - **5 Nhánh Combo Mở Rộng theo Input Trigger:**
 *   1. **Thrust (`THRUST`, `0.50s` / `10t`):** Sau Hit 1 hoặc Hit 2 -> Giữ chuột trái (`Hold Attack`) -> Đâm một tay Katate-tsuki tầm xa, xuyên `60%` giáp & phá khiên.
 *   2. **360° Sweep (`DELAY_SWEEP_360`, `0.55s` / `11t`):** Hit 1 -> Đợi `>= 0.30s` -> Đánh -> Quét tầm thấp (Đòn A) nối xoay người 360° AoE (Đòn B).
 *   3. **Iaido Blink (`IAIDO_BLINK`, `0.70s` / `14t` sau khi thả):** Đang tra vỏ -> Giữ Chuột Phải `>= 0.35s` -> Thả -> Lướt xuyên mục tiêu `2.8` blocks, chém Judgment Cut & nổ sát thương trễ lúc tra kiếm "tách"!
 *   4. **Aerial Combo (`AIR_1_LAUNCH` -> `AIR_2_MID_SWEEP` -> `AIR_3_SLAM`):** Nhảy trên không -> Đánh 3 lần (Hất tung -> Chém ngang giữ lơ lửng `motionY=0.05` -> Bổ thiên thạch `motionY=-1.2` tạo AoE mặt đất!).
 *   5. **Parry & Counter (`DEFLECT` -> `PARRY_COUNTER`, `0.25s` / `5t`):** Đỡ chuẩn nhịp -> Bấm đánh ngay lập tức -> Phản chém chí mạng (`×2.2` Crit) + làm choáng mục tiêu!
 */
public enum KatanaMoveType {
    /** Hit 1: Rút kiếm chém lướt ngang Trái -> Phải (0.40s = 8 ticks, cancel window từ Tick 5). */
    HIT_1_DRAW(0, 8.0F, 1.00F, 5L, false),
    /** Hit 2: Chém chéo Trên Phải -> Dưới Trái (0.35s = 7 ticks, cancel window từ Tick 4). */
    HIT_2_DOWN_DIAG(1, 7.0F, 1.08F, 4L, false),
    /** Hit 3: Chém hất ngược Dưới Trái -> Trên Phải (0.35s = 7 ticks, cancel window từ Tick 4). */
    HIT_3_UP_DIAG(2, 7.0F, 1.15F, 4L, false),
    /** Hit 4: Xoay người bổ dọc dứt điểm phá giáp (0.55s = 11 ticks, reset về Hit 1 sau Tick 9). */
    HIT_4_FINISHER(3, 11.0F, 1.50F, 9L, false),
    /** Tư thế đỡ gạt đòn chuẩn nhịp (0.20s = 4-5 ticks, mở cửa sổ phản công tức thì). */
    DEFLECT(4, 5.0F, 1.00F, 1L, false),
    /** Vẩy máu & Tra kiếm vào vỏ: Chiburi 45° -> Roll 180° Noto -> Khóa chốt "tách" ở 0.88s (0.90s = 18 ticks). */
    CHIBURI_NOTO(5, 18.0F, 0.00F, 4L, false),
    /** Nhánh 1 — Đâm Xuyên Giáp Katate-tsuki (0.50s = 10 ticks): Xuyên 60% giáp, phá khiên, hitstop 0.08s. */
    THRUST(6, 10.0F, 1.65F, 8L, false),
    /** Nhánh 2 — Bạt Kiếm Liên Hoàn (0.55s = 11 ticks): Quét thấp sát đất (0..0.25s) + Xoay 360° AoE (0.25..0.55s). */
    DELAY_SWEEP_360(7, 11.0F, 1.45F, 9L, false),
    /** Nhánh 3 — Nhất Kiếm Tất Sát (0.70s = 14 ticks sau thả nút): Lướt xuyên 7.0b + Judgment Cut + Noto nổ sát thương trễ! */
    IAIDO_BLINK(8, 14.0F, 2.40F, 12L, true),
    /** Nhánh 4A — Không Chiến Hit 1 (0.30s = 6 ticks): Chém lộn vòng hất tung mục tiêu lên không. */
    AIR_1_LAUNCH(9, 6.0F, 1.05F, 4L, false),
    /** Nhánh 4B — Không Chiến Hit 2 (0.25s = 5 ticks): Chém ngang giữ lơ lửng trên không (motionY = 0.05). */
    AIR_2_MID_SWEEP(10, 5.0F, 1.10F, 3L, false),
    /** Nhánh 4C — Không Chiến Đòn Chốt (Meteor Air Slam): Cắm đầu lao thẳng xuống (motionY = -1.2) -> cắm đất tạo AoE! */
    AIR_3_SLAM(11, 24.0F, 1.85F, 8L, true),
    /** Nhánh 5 — Phản Công Chớp Nhoáng sau Parry (0.25s = 5 ticks): Chí mạng ×2.2 Crit + làm choáng + tia lửa vàng/đỏ! */
    PARRY_COUNTER(12, 5.0F, 2.20F, 4L, false),
    /** Thế Đỡ 2 — Kasa-uke Impact Frame (0.22s = 4.5 ticks): Tay & kiếm sụp xuống 1.5u khi đỡ đòn bổ rồi bật nảy lên! */
    GUARD_KASA_IMPACT(13, 4.5F, 1.00F, 1L, false),
    /** Thế Đỡ 4 — Mune-oshi Heavy Brace (0.40s = 8 ticks): Bàn tay trái áp chặt sống kiếm trợ lực trượt lùi 1 block! */
    GUARD_MUNE_OSHI(14, 8.0F, 1.00F, 2L, false),
    /** Thế Đỡ 5 — Saya-uke Block Recoil (0.25s = 5 ticks): Chặn đòn khẩn cấp bằng vỏ kiếm, mở ngay Iaido & Batto-counter! */
    GUARD_SAYA_UKE_HIT(15, 5.0F, 1.00F, 1L, false);

    public final int id;
    public final float baseDisplayTicks;
    public final float damageMultiplier;
    public final long cancelAfterTicks;
    public final boolean lockedAction;

    KatanaMoveType(int id, float baseDisplayTicks, float damageMultiplier, long cancelAfterTicks, boolean lockedAction) {
        this.id = id;
        this.baseDisplayTicks = baseDisplayTicks;
        this.damageMultiplier = damageMultiplier;
        this.cancelAfterTicks = cancelAfterTicks;
        this.lockedAction = lockedAction;
    }

    public boolean isComboMove() {
        return this == HIT_1_DRAW || this == HIT_2_DOWN_DIAG || this == HIT_3_UP_DIAG || this == HIT_4_FINISHER;
    }

    public boolean isAerialMove() {
        return this == AIR_1_LAUNCH || this == AIR_2_MID_SWEEP || this == AIR_3_SLAM;
    }

    public boolean isGuardReaction() {
        return this == DEFLECT || this == GUARD_KASA_IMPACT || this == GUARD_MUNE_OSHI || this == GUARD_SAYA_UKE_HIT;
    }

    public static KatanaMoveType byId(int id) {
        for (KatanaMoveType t : values()) {
            if (t.id == id) {
                return t;
            }
        }
        return HIT_1_DRAW;
    }

    /** Chuỗi 4 đòn liền mạch: Hit 1 (Rút chém) -> Hit 2 (Chém chéo xuống) -> Hit 3 (Hất ngược lên) -> Hit 4 (Bổ dọc dứt điểm). */
    public static KatanaMoveType comboByStep(int step) {
        return switch (Math.floorMod(step, 4)) {
            case 0 -> HIT_1_DRAW;
            case 1 -> HIT_2_DOWN_DIAG;
            case 2 -> HIT_3_UP_DIAG;
            default -> HIT_4_FINISHER;
        };
    }

    public static KatanaMoveType aerialByStep(int step) {
        return switch (Math.floorMod(step, 3)) {
            case 0 -> AIR_1_LAUNCH;
            case 1 -> AIR_2_MID_SWEEP;
            default -> AIR_3_SLAM;
        };
    }
}
