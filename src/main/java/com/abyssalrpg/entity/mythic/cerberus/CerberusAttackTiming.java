package com.abyssalrpg.entity.mythic.cerberus;

import com.abyssalrpg.config.CerberusConfig;
import com.abyssalrpg.entity.mythic.CerberusBossEntity.SkillState;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bảng định nghĩa timeline thống nhất — Một Nguồn Chân Lý Duy Nhất (Single Source of Truth)
 * giữa `cerberus.animation.json` và cửa sổ sát thương / VFX trên Server của Boss Cerberus v4.
 *
 * Quy ước cứng (20 tick = 1.00 giây ở tốc độ 1.0x):
 * - `totalTicks` khớp tuyệt đối 1:1 với `Math.round(animation_length * 20)` trong `cerberus.animation.json`.
 * - Mỗi `HitWindow(hitStartTick, hitEndTick, impactTick)` khớp chính xác khung hình cắn / phun lửa /
 *   vồ đáp đất / đập chân / sóng lửa / lao húc / ám sát -> 0 tick lệch pha giữa hình ảnh và sát thương!
 * - Tốc độ phát animation (`getAnimSpeed`) dùng chung cho cả Server (`animTick += speed`) lẫn GeckoLib Client
 *   (`AnimationController.setAnimationSpeedHandler`) kèm `transitionLength = 2 ticks`.
 */
public final class CerberusAttackTiming {
    private CerberusAttackTiming() {}

    /** Thời lượng & mốc chạm chuẩn của 1 clip cắn đơn (`bite_1` / `bite_2` / `bite_3`: 0.80s = 16t, impact 9t). */
    public static final int BITE_CLIP_TICKS = 16;
    public static final int BITE_IMPACT_TICK = 9;
    public static final int LAND_SPAWN_TICKS = 32;

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

    /** Timeline đầy đủ của 1 kỹ năng Cerberus. */
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
        // --- Phase 1 & Dùng chung (khớp 1:1 animation_length * 20 trong cerberus.animation.json) ---
        reg(new SkillTimeline(SkillState.TRIPLE_BITE, "bite_1", 16, 7, false,
                new HitWindow(8, 10, 9)));

        reg(new SkillTimeline(SkillState.HELLFIRE_BREATH, "breath", 52, 14, true,
                new HitWindow(18, 44, 18)));

        reg(new SkillTimeline(SkillState.POUNCE, "pounce", 40, 14, true,
                new HitWindow(16, 30, 30)));

        reg(new SkillTimeline(SkillState.GROUND_SLAM, "slam", 36, 14, true,
                new HitWindow(18, 20, 18)));

        reg(new SkillTimeline(SkillState.HOWL, "howl", 40, 14, true,
                new HitWindow(18, 20, 18)));

        // --- Phase 2 chuyên biệt theo vai trò ---
        reg(new SkillTimeline(SkillState.BITE_COMBO, "bite_2", 16, 7, true,
                new HitWindow(8, 10, 9)));

        reg(new SkillTimeline(SkillState.WRATH_CHARGE, "dash", 32, 12, true,
                new HitWindow(14, 26, 14)));

        reg(new SkillTimeline(SkillState.FLAME_RAIN, "howl", 40, 12, true,
                new HitWindow(16, 17, 16),
                new HitWindow(28, 29, 28)));

        reg(new SkillTimeline(SkillState.HELLFIRE_NOVA, "nova", 42, 14, false,
                new HitWindow(18, 40, 18)));

        reg(new SkillTimeline(SkillState.SHADOW_DASH, "dash", 32, 12, true,
                new HitWindow(14, 26, 14)));

        reg(new SkillTimeline(SkillState.SHADOW_STEP, "shadow_step", 24, 12, false,
                new HitWindow(13, 16, 13)));

        reg(new SkillTimeline(SkillState.PHANTOM_CLAW, "bite_3", 16, 7, true,
                new HitWindow(8, 10, 9)));

        // --- Combo nhóm 3 cá thể ---
        reg(new SkillTimeline(SkillState.PACK_HUNT, "pack_hunt", 48, 16, false,
                new HitWindow(18, 28, 18)));

        // --- Trạng thái đặc biệt (cố định tốc độ 1.0x) ---
        reg(new SkillTimeline(SkillState.PHASE_CHANGE, "phase_change", 36, 0, false));
        reg(new SkillTimeline(SkillState.DEATH, "death", 55, 0, false));
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
     * - Phase 2: `cfg.phase2AttackAnimSpeed` (mặc định `1.15f`) cho các đòn đánh nhanh được phép tăng tốc;
     *   các chiêu nhạy timing (`HELLFIRE_NOVA`, `SHADOW_STEP`, `PACK_HUNT`, `PHASE_CHANGE`, `DEATH`) giữ `1.0f`.
     */
    public static float getAnimSpeed(SkillState state, boolean phase2) {
        SkillTimeline tl = TABLE.get(state);
        if (tl == null || !tl.allowSpeedup() || !phase2) {
            return 1.0f;
        }
        return CerberusConfig.get().phase2AttackAnimSpeed;
    }
}

