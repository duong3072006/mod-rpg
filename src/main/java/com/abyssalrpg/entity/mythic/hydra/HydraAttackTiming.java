package com.abyssalrpg.entity.mythic.hydra;

import com.abyssalrpg.config.SpiritHydraConfig;
import com.abyssalrpg.entity.mythic.HydraBossEntity.SkillState;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bảng định nghĩa timeline thống nhất — Một Nguồn Chân Lý Duy Nhất (Single Source of Truth)
 * giữa `spirit_hydra.animation.json` và cửa sổ sát thương / VFX trên Server của Boss Spirit Hydra 4 đầu.
 *
 * Quy ước cứng (20 tick = 1.00 giây ở tốc độ 1.0x):
 * - `totalTicks` khớp tuyệt đối 1:1 với `Math.round(animation_length * 20)` trong `spirit_hydra.animation.json`.
 * - Mỗi `HitWindow(hitStartTick, hitEndTick, impactTick)` khớp chính xác khung hình đầu phóng cắn / đuôi quất /
 *   luồng hơi hồn quét ngang / sóng tròn nổ ra -> 0 tick lệch pha giữa hình ảnh và sát thương!
 * - Tốc độ phát animation (`getAnimSpeed`) dùng chung cho cả Server (`animTick += speed`) lẫn GeckoLib Client
 *   (`AnimationController.setAnimationSpeedHandler`) kèm `transitionLength = 1–2 ticks`.
 */
public final class HydraAttackTiming {
    private HydraAttackTiming() {}

    /** Cửa sổ khung hình chủ động `[hitStartTick, hitEndTick]` và mốc chạm đích `impactTick`. */
    public record HitWindow(int hitStartTick, int hitEndTick, int impactTick) {
        public boolean isActive(double prevAnimTick, double currAnimTick) {
            return currAnimTick >= hitStartTick && prevAnimTick <= hitEndTick;
        }

        public boolean crossedImpact(double prevAnimTick, double currAnimTick) {
            return prevAnimTick < impactTick && currAnimTick >= impactTick;
        }

        public double impactSeconds() {
            return impactTick / 20.0;
        }
    }

    /** Timeline đầy đủ của 1 kỹ năng Spirit Hydra. */
    public record SkillTimeline(
            SkillState state,
            String animKey,
            int totalTicks,
            int lockAimTick,
            boolean allowSpeedup,
            HitWindow... hitWindows
    ) {
        public double animationLengthSeconds() {
            return totalTicks / 20.0;
        }
    }

    /** Timeline cố định cho đòn cắn độc lập từng đầu (`bite_1..bite_4`, 1.10s = 22 ticks). */
    public static final int HEAD_BITE_TOTAL_TICKS = 22;
    public static final HitWindow HEAD_BITE_WINDOW = new HitWindow(11, 14, 12);

    /** Mốc thời gian 4 đầu mọc lại trong `phase_change` (cách nhau đúng 0.35s = 7 ticks). */
    public static final int[] PHASE2_REGROW_TICKS = { 38, 45, 52, 59 };

    private static final Map<SkillState, SkillTimeline> TABLE = new EnumMap<>(SkillState.class);

    static {
        // --- 5 Kỹ năng Phase 1 (khớp 1:1 animation_length * 20 trong spirit_hydra.animation.json) ---
        reg(new SkillTimeline(SkillState.TRIPLE_BITE, "triple_bite", 52, 9, true,
                new HitWindow(11, 14, 12),
                new HitWindow(23, 26, 24),
                new HitWindow(35, 38, 36)));

        reg(new SkillTimeline(SkillState.SPIRIT_BREATH, "breath", 56, 13, true,
                new HitWindow(16, 44, 16)));

        reg(new SkillTimeline(SkillState.SOUL_SPIT, "spit", 32, 10, true,
                new HitWindow(13, 14, 13),
                new HitWindow(16, 17, 16),
                new HitWindow(19, 20, 19),
                new HitWindow(22, 23, 22)));

        reg(new SkillTimeline(SkillState.TAIL_BODY_SLAM, "slam", 36, 13, true,
                new HitWindow(16, 20, 17)));

        reg(new SkillTimeline(SkillState.SUMMON_WISPS, "summon_wisps", 36, 14, true,
                new HitWindow(18, 20, 18)));

        // --- 4 Kỹ năng Phase 2 ---
        reg(new SkillTimeline(SkillState.ALL_HEAD_BARRAGE, "all_head_barrage", 44, 13, true,
                new HitWindow(16, 18, 16),
                new HitWindow(28, 30, 28)));

        reg(new SkillTimeline(SkillState.SPIRIT_NOVA, "spirit_nova", 48, 15, true,
                new HitWindow(18, 42, 18)));

        reg(new SkillTimeline(SkillState.SOUL_CHAIN, "soul_chain", 40, 11, true,
                new HitWindow(14, 16, 14),
                new HitWindow(26, 29, 27)));

        reg(new SkillTimeline(SkillState.WRATH_OF_MANY_HEADS, "wrath_of_many_heads", 64, 34, false,
                new HitWindow(38, 44, 39)));

        // --- Trạng thái đặc biệt (luôn chạy tốc độ 1.0x cố định) ---
        reg(new SkillTimeline(SkillState.HEAD_STAGGER, "head_cut", 25, 0, false));
        reg(new SkillTimeline(SkillState.PHASE_CHANGE, "phase_change", 100, 0, false));
        reg(new SkillTimeline(SkillState.DEATH, "death", 60, 0, false));
    }

    private static void reg(SkillTimeline tl) {
        TABLE.put(tl.state(), tl);
    }

    public static SkillTimeline forSkill(SkillState state) {
        return TABLE.get(state);
    }

    public static Map<SkillState, SkillTimeline> allTimelines() {
        return TABLE;
    }

    /**
     * Tốc độ animation ĐỒNG BỘ 100% giữa Server và Client:
     * - Phase 1: `1.0f + severedHeads * attackSpeedBonusPerLostHead` (+8% mỗi đầu đã mất, kẹp tối đa `1.30f`).
     * - Phase 2: `cfg.phase2AttackAnimSpeed` (mặc định `1.18f`), giữ mọi telegraph luôn đọc rõ (>= 9 ticks).
     * - Các clip cố định (`PHASE_CHANGE`, `HEAD_STAGGER`, `WRATH_OF_MANY_HEADS`, `DEATH`): luôn `1.0f`.
     */
    public static float getAnimSpeed(SkillState state, boolean phase2, int severedHeads) {
        SkillTimeline tl = TABLE.get(state);
        if (tl == null || !tl.allowSpeedup()) {
            return 1.0f;
        }
        return computeAttackAnimSpeed(phase2, severedHeads);
    }

    public static float computeAttackAnimSpeed(boolean phase2, int severedHeads) {
        SpiritHydraConfig cfg = SpiritHydraConfig.get();
        if (phase2) {
            return cfg.phase2AttackAnimSpeed;
        }
        float bonus = (float) (Math.max(0, severedHeads) * cfg.attackSpeedBonusPerLostHead);
        return Math.min(1.30f, 1.0f + bonus);
    }
}

