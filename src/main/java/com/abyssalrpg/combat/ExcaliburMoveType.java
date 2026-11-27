package com.abyssalrpg.combat;

/**
 * Hệ thống chiến đấu chuẩn **Royal Swordplay + Vòng Phép Fairy Tail** cho **Excalibur**:
 * - **Chuỗi 4 đòn thường:** `HIT_1_SWEEP` (0.35s) → `HIT_2_RISING` (0.35s) → `HIT_3_THRUST` (0.40s) → `HIT_4_SLAM` (0.50s).
 * - **3 Nhánh Combo Mở Rộng (Tối giản VFX):**
 *   1. **Strike Air Thrust (`STRIKE_AIR`, `0.45s` / `9t`):** Hold Attack sau Hit 1/2 → Lấy đà (0.20s) + Đâm bộc phá (0.15s) phóng 1 vòng xung kích vàng nhạt/trắng bay 5 blocks, đẩy lùi & phá khiên.
 *   2. **Golden Crescent Wave (`GOLDEN_CRESCENT`, `0.60s` / `12t`):** Delay Combo (Hit 1 → đợi 0.30s → Đánh) → Quét tròn 360° (0.28s) + Bổ phóng 1 lưỡi liềm ánh sáng vàng mảnh trượt mặt đất 7 blocks.
 *   3. **Aerial Judgment (`AIR_1_DIAG` → `AIR_2_DIAG` → `AIR_3_FALLING_STAR`):** Chém chéo 2 nhịp giữ lơ lửng (`motionY = 0.05`) → Chĩa mũi kiếm xuống (`Pitch -10°`) lao cắm đất (`motionY = -1.3`) kích hoạt 1 vòng phép 2D vàng dưới mũi kiếm trong 0.3s.
 * - **Đỡ Kiếm & Phản Đòn Hoàng Gia (`ROYAL_PARRY_COUNTER`, `0.40s` / `8t`):**
 *   Đỡ chuẩn nhịp (`<= 0.18s` trước khi trúng đòn) → Nhịp 1 (0.00–0.15s) gạt chệch `+35°` lóe vòng phép vàng + tiếng "Klang!" → Nhịp 2 (0.15–0.40s) vặn ngược `-40°` chém ngang gây `200%` sát thương & làm choáng `0.8s`!
 * - **Tuyệt kỹ (`ULT_EXCALIBUR`, `2.60s` / `52t`):** Đủ `100/100` năng lượng → `[RMB]` khai mở Vòng Phép Fairy Tail 2D dưới chân & 3 vòng phép dựng dọc trục bắn phóng chùm tia lõi trắng `20 blocks` (`1500%` sát thương chuẩn)!
 */
public enum ExcaliburMoveType {
    /** Hit 1 — Chém ngang tầm trung từ phải sang trái (0.35s / 7 ticks). */
    HIT_1_SWEEP(0, 7.0F, 1.00F, 4L, false),
    /** Hit 2 — Chém chéo hất ngược từ dưới trái lên trên phải (0.35s / 7 ticks). */
    HIT_2_RISING(1, 7.0F, 1.08F, 4L, false),
    /** Hit 3 — Đâm bộc phá tiến tới 0.65 block + 1 vòng sóng xung kích vàng mỏng (0.40s / 8 ticks). */
    HIT_3_THRUST(2, 8.0F, 1.18F, 5L, false),
    /** Hit 4 — Bật nhảy nhẹ & bổ dọc dứt điểm + vệt chém vàng thẳng đứng sắc nét (0.50s / 10 ticks). */
    HIT_4_SLAM(3, 10.0F, 1.40F, 8L, false),
    /** Tuyệt kỹ — Excalibur kết hợp Vòng Phép Fairy Tail 2D & Chùm Tia 20 blocks (2.60s / 52 ticks). */
    ULT_EXCALIBUR(4, 52.0F, 15.00F, 52L, true),
    /** Nhánh A — Strike Air Thrust (Hold Attack sau Hit 1/2): 0.20s lấy đà + 0.15s đâm phóng vòng xung kích 5 blocks & phá khiên. */
    STRIKE_AIR(5, 9.0F, 1.55F, 7L, false),
    /** Nhánh B — Golden Crescent Wave (Delay 0.3s sau Hit 1): 0.28s xoay 360° + 0.32s bổ phóng lưỡi liềm vàng mảnh 7 blocks. */
    GOLDEN_CRESCENT(6, 12.0F, 1.75F, 10L, false),
    /** Nhánh C1 — Không Chiến 1 (0.25s / 5 ticks): Chém chéo trên không giữ độ cao (motionY = 0.05). */
    AIR_1_DIAG(7, 5.0F, 1.05F, 3L, false),
    /** Nhánh C2 — Không Chiến 2 (0.25s / 5 ticks): Chém chéo đảo chiều trên không giữ độ cao (motionY = 0.05). */
    AIR_2_DIAG(8, 5.0F, 1.15F, 3L, false),
    /** Nhánh C3 — Lao Cắm Kiếm: Chĩa mũi kiếm xuống (Pitch -10°), lao (motionY = -1.3), chạm đất bật vòng phép 2D vàng 0.3s. */
    AIR_3_FALLING_STAR(9, 24.0F, 2.00F, 8L, true),
    /** Phản Đòn Hoàng Gia 2 Nhịp liên hoàn (0.40s / 8 ticks): 0.00–0.15s gạt chệch (+35°) + 0.15–0.40s chém ngang (-40°) 200% ST & choáng 0.8s! */
    ROYAL_PARRY_COUNTER(10, 8.0F, 2.00F, 7L, false),
    /** Đỡ Trên Cao — Impact Frame (0.22s / 4.5 ticks): Đón đòn bổ từ trên cao khi nhìn lên (Pitch < -30°). */
    GUARD_OVERHEAD_IMPACT(11, 4.5F, 1.00F, 1L, false),
    /** Cất Kiếm Vào Vỏ Avalon (0.55s / 11 ticks): 0.00–0.18s đón vỏ → 0.18–0.42s trượt kiếm ("Clink!" 0.40s) → 0.42–0.55s thu thế. */
    SHEATHE_AVALON(12, 11.0F, 0.00F, 11L, false),
    /** Rút Kiếm Phản Công Từ Vỏ Avalon (0.50s / 10 ticks sau pha gạt 0.15s = tổng 0.65s): Chém ngang Trái→Phải 220% ST, Choáng 1.0s, +15 NL! */
    AVALON_PARRY_COUNTER(13, 10.0F, 2.20F, 8L, false);

    public final int id;
    public final float baseDisplayTicks;
    public final float damageMultiplier;
    public final long cancelAfterTicks;
    public final boolean lockedSkill;

    ExcaliburMoveType(int id, float baseDisplayTicks, float damageMultiplier, long cancelAfterTicks, boolean lockedSkill) {
        this.id = id;
        this.baseDisplayTicks = baseDisplayTicks;
        this.damageMultiplier = damageMultiplier;
        this.cancelAfterTicks = cancelAfterTicks;
        this.lockedSkill = lockedSkill;
    }

    public boolean isComboMove() {
        return this == HIT_1_SWEEP || this == HIT_2_RISING || this == HIT_3_THRUST || this == HIT_4_SLAM;
    }

    public boolean isAerialMove() {
        return this == AIR_1_DIAG || this == AIR_2_DIAG || this == AIR_3_FALLING_STAR;
    }

    public boolean isUltimate() {
        return this == ULT_EXCALIBUR;
    }

    public boolean isGuardReaction() {
        return this == ROYAL_PARRY_COUNTER || this == GUARD_OVERHEAD_IMPACT || this == AVALON_PARRY_COUNTER;
    }

    public boolean isSheathing() {
        return this == SHEATHE_AVALON;
    }

    public static ExcaliburMoveType byId(int id) {
        for (ExcaliburMoveType t : values()) {
            if (t.id == id) {
                return t;
            }
        }
        return HIT_1_SWEEP;
    }

    public static ExcaliburMoveType comboByStep(int step) {
        return switch (Math.floorMod(step, 4)) {
            case 0 -> HIT_1_SWEEP;
            case 1 -> HIT_2_RISING;
            case 2 -> HIT_3_THRUST;
            default -> HIT_4_SLAM;
        };
    }

    public static ExcaliburMoveType aerialByStep(int step) {
        return switch (Math.floorMod(step, 3)) {
            case 0 -> AIR_1_DIAG;
            case 1 -> AIR_2_DIAG;
            default -> AIR_3_FALLING_STAR;
        };
    }
}
