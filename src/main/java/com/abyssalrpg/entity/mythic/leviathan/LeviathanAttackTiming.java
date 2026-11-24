package com.abyssalrpg.entity.mythic.leviathan;

import com.abyssalrpg.config.LeviathanConfig;
import com.abyssalrpg.entity.mythic.LeviathanBossEntity.SkillState;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bảng định nghĩa timeline thống nhất — Một Nguồn Chân Lý Duy Nhất (Single Source of Truth)
 * giữa `leviathan.animation.json` và cửa sổ sát thương / VFX trên Server của Boss Leviathan v2.
 *
 * Quy ước cứng (20 tick = 1.00 giây ở tốc độ 1.0x):
 * - `totalTicks` khớp tuyệt đối 1:1 với `Math.round(animation_length * 20)` trong `leviathan.animation.json`.
 * - Mỗi `HitWindow(hitStartTick, hitEndTick, impactTick)` khớp chính xác khung hình quật đuôi / lao cắn /
 *   khai hỏa tia năng lượng / gầm sóng xung kích / đập Tidal Wrath -> 0 tick lệch pha giữa hình ảnh và sát thương!
 * - Tốc độ phát animation (`getAnimSpeed`) dùng chung cho cả Server (`animTick += speed`) lẫn GeckoLib Client
 *   (`AnimationController.setAnimationSpeedHandler`) kèm `transitionLength = 2 ticks`.
 */
public final class LeviathanAttackTiming {
    private LeviathanAttackTiming() {}

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

    /** Timeline đầy đủ của 1 kỹ năng Leviathan. */
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

    private static final Map<SkillState, SkillTimeline> TABLE = new EnumMap<>(SkillState.class);

    static {
        // --- 4 Kỹ năng Phase 1 (khớp 1:1 animation_length * 20 trong leviathan.animation.json) ---
        reg(new SkillTimeline(SkillState.TAIL_SLAM, "tail_slam", 36, 14, true,
                new HitWindow(15, 19, 16)));

        reg(new SkillTimeline(SkillState.BITE_CHARGE, "bite_charge", 42, 12, true,
                new HitWindow(16, 32, 16)));

        reg(new SkillTimeline(SkillState.ENERGY_BEAM, "energy_beam", 56, 14, true,
                new HitWindow(18, 44, 18)));

        reg(new SkillTimeline(SkillState.SUMMON_MINIONS, "summon_minions", 36, 12, true,
                new HitWindow(16, 18, 16)));

        // --- 4 Kỹ năng Phase 2 ---
        reg(new SkillTimeline(SkillState.WHIRLPOOL, "whirlpool", 84, 14, false,
                new HitWindow(16, 76, 16)));

        reg(new SkillTimeline(SkillState.ROAR_SHOCKWAVE, "roar_shockwave", 50, 16, true,
                new HitWindow(18, 42, 18)));

        reg(new SkillTimeline(SkillState.STORM_BARRAGE, "storm_barrage", 48, 12, true,
                new HitWindow(14, 15, 14),
                new HitWindow(24, 25, 24),
                new HitWindow(34, 35, 34)));

        reg(new SkillTimeline(SkillState.TIDAL_WRATH, "tidal_wrath", 52, 18, false,
                new HitWindow(22, 36, 36)));

        // --- Trạng thái đặc biệt (luôn chạy cố định tốc độ 1.0x, không lệch timing chuyển phase 4.5s) ---
        reg(new SkillTimeline(SkillState.PHASE_CHANGE, "phase_change", 90, 0, false));
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
     * Tốc độ phát animation đồng bộ 100% giữa Server và GeckoLib Client:
     * - Phase 1: `1.0f`
     * - Phase 2: `cfg.phase2AttackAnimSpeed` (mặc định `1.18f`) cho các đòn đánh nhanh (mọi telegraph luôn >= 11 tick),
     *   còn `WHIRLPOOL`, `TIDAL_WRATH`, `PHASE_CHANGE`, `DEATH` giữ chuẩn `1.0f`.
     */
    public static float getAnimSpeed(SkillState state, boolean phase2) {
        SkillTimeline tl = TABLE.get(state);
        if (tl == null || !tl.allowSpeedup() || !phase2) {
            return 1.0f;
        }
        return LeviathanConfig.get().phase2AttackAnimSpeed;
    }
}

