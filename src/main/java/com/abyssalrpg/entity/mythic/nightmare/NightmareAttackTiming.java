package com.abyssalrpg.entity.mythic.nightmare;

import com.abyssalrpg.config.NightmareKnightConfig;
import com.abyssalrpg.entity.mythic.NightmareKnightBossEntity.SkillState;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bảng định nghĩa timeline thống nhất — Một Nguồn Chân Lý Duy Nhất (Single Source of Truth)
 * giữa `nightmare_knight.animation.json` và logic gây sát thương/VFX trên Server của Nightmare Knight.
 *
 * Quy ước cứng (20 tick = 1.00 giây ở tốc độ 1.0x):
 * - `totalTicks` PHẢI khớp tuyệt đối `animation_length * 20` trong `nightmare_knight.animation.json`.
 * - Mỗi `HitWindow(startTick, endTick, impactTick)` tương ứng chính xác khung hình lưỡi kiếm quét ngang /
 *   đập chạm đất / đâm đạt tầm cực đại.
 * - Ở Phase 2, cả Server (`animTick += animSpeed`) lẫn Client (`AnimationController.setAnimationSpeedHandler`)
 *   dùng CHUNG đúng 1 hằng số tốc độ `getAnimSpeed(state, isPhase2)` -> độ lệch timing = 0 tick ở cả 2 Phase!
 */
public final class NightmareAttackTiming {
    private NightmareAttackTiming() {}

    /** Một cửa sổ khung hình chủ động (active frames) của 1 nhát chém/đập/phóng. */
    public record HitWindow(int startTick, int endTick, int impactTick) {
        public boolean isActive(double prevAnimTick, double currAnimTick) {
            return currAnimTick >= startTick && prevAnimTick <= endTick;
        }

        public boolean crossedImpact(double prevAnimTick, double currAnimTick) {
            return prevAnimTick < impactTick && currAnimTick >= impactTick;
        }

        public double impactSeconds() {
            return impactTick / 20.0;
        }
    }

    /** Cấu trúc timeline hoàn chỉnh cho 1 kỹ năng của Nightmare Knight. */
    public record SkillTimeline(
            SkillState state,
            String animKey,
            int totalTicks,
            int lockAimTick,
            boolean allowPhase2Speedup,
            HitWindow... hitWindows
    ) {
        public double animationLengthSeconds() {
            return totalTicks / 20.0;
        }
    }

    private static final Map<SkillState, SkillTimeline> TABLE = new EnumMap<>(SkillState.class);

    static {
        // --- 8 Kỹ năng Phase 1 (khớp 100% animation_length * 20 và khung hình strike trong nightmare_knight.animation.json) ---
        reg(new SkillTimeline(SkillState.GREATSWORD_SLASH, "slash", 32, 12, true,
                new HitWindow(18, 21, 18)));
        reg(new SkillTimeline(SkillState.OVERHEAD_SMASH, "overhead_smash", 40, 18, true,
                new HitWindow(24, 27, 24),
                new HitWindow(27, 29, 27))); // Sóng nứt đất trễ 3 tick sau cú đập
        reg(new SkillTimeline(SkillState.THRUST, "thrust", 34, 13, true,
                new HitWindow(19, 22, 19)));
        reg(new SkillTimeline(SkillState.CLAW_SWIPE, "claw_swipe", 34, 9, true,
                new HitWindow(14, 16, 14),
                new HitWindow(24, 26, 24)));
        reg(new SkillTimeline(SkillState.LEAP_SMASH, "leap", 48, 14, true,
                new HitWindow(34, 37, 34))); // Nhảy ở tick 16, đáp & đập đất ở tick 34
        reg(new SkillTimeline(SkillState.BONE_SPIKE_ERUPTION, "spike_eruption", 34, 14, true,
                new HitWindow(19, 20, 19)));
        reg(new SkillTimeline(SkillState.SWORD_WAVE, "sword_wave", 32, 14, true,
                new HitWindow(18, 19, 18)));
        reg(new SkillTimeline(SkillState.BONE_CAGE, "cage", 36, 16, true,
                new HitWindow(20, 21, 20)));

        // --- 6 Kỹ năng Phase 2 ---
        reg(new SkillTimeline(SkillState.NIGHTMARE_COMBO, "combo", 76, 11, true,
                new HitWindow(16, 18, 16),
                new HitWindow(30, 32, 30),
                new HitWindow(44, 46, 44),
                new HitWindow(60, 63, 60)));
        reg(new SkillTimeline(SkillState.SPIKE_STORM, "storm", 56, 12, true,
                new HitWindow(16, 17, 16),
                new HitWindow(30, 31, 30),
                new HitWindow(44, 45, 44)));
        reg(new SkillTimeline(SkillState.NIGHTMARE_REALM, "realm", 38, 14, true,
                new HitWindow(20, 21, 20)));
        reg(new SkillTimeline(SkillState.SPIN_SLASH, "spin_slash", 38, 12, true,
                new HitWindow(16, 19, 16),
                new HitWindow(24, 27, 24)));
        reg(new SkillTimeline(SkillState.FLAME_SWORD_WAVE, "sword_wave", 32, 14, true,
                new HitWindow(18, 19, 18)));
        reg(new SkillTimeline(SkillState.NIGHTMARE_DESCENT, "ultimate", 84, 40, false,
                new HitWindow(48, 51, 48),
                new HitWindow(58, 61, 58),
                new HitWindow(68, 71, 68)));

        // Chuyển phase luôn chạy đúng 1.0x trong 120 tick (6.00s)
        reg(new SkillTimeline(SkillState.PHASE_CHANGE, "phase_change", 120, 0, false));
    }

    private static void reg(SkillTimeline tl) {
        TABLE.put(tl.state(), tl);
    }

    public static SkillTimeline forSkill(SkillState state) {
        return TABLE.get(state);
    }

    /**
     * Tốc độ đồng bộ TUYỆT ĐỐI giữa Server và Client cho kỹ năng `state`.
     * - Phase 1 hoặc chiêu cố định (`PHASE_CHANGE`, `NIGHTMARE_DESCENT`): `1.0f`.
     * - Phase 2 các chiêu đánh: `cfg.phase2AttackAnimSpeed` (mặc định `1.20f`, đảm bảo mọi windup >= 10 ticks).
     */
    public static float getAnimSpeed(SkillState state, boolean phase2) {
        SkillTimeline tl = TABLE.get(state);
        if (tl == null || !phase2 || !tl.allowPhase2Speedup()) {
            return 1.0f;
        }
        return NightmareKnightConfig.get().phase2AttackAnimSpeed;
    }
}

