package com.abyssalrpg.entity.mythic.cinder;

import com.abyssalrpg.config.CinderDragonConfig;
import com.abyssalrpg.entity.mythic.CinderDragonBossEntity.SkillState;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bảng định nghĩa timeline thống nhất (Single Source of Truth) giữa `cinder_dragon.animation.json`
 * và logic gây sát thương/VFX trên Server của Boss Cinder Dragon — Hỏa Long Giáp Đá.
 *
 * MỌI `totalTicks` khớp tuyệt đối `animation_length * 20` (sai lệch 0 tick ở cả Phase 1 và Phase 2).
 */
public final class DragonAttackTiming {
    private DragonAttackTiming() {}

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
        // --- 6 Kỹ năng Phase 1 (khớp tuyệt đối animation_length * 20 trong cinder_dragon.animation.json) ---
        // 1. Cinder Breath (2.80s = 56t): telegraph 0..20t (1.00s), quét phun lửa 20..46t (6 nhịp mỗi 5t)
        reg(new SkillTimeline(SkillState.CINDER_BREATH, "breath", 56, 16, true,
                new HitWindow(20, 46, 20)));
        // 2. Magma Fireball Barrage (2.20s = 44t): telegraph 0..16t (0.80s), 3 cầu lửa tại 16t, 24t, 32t
        reg(new SkillTimeline(SkillState.MAGMA_FIREBALL_BARRAGE, "fireball", 44, 12, true,
                new HitWindow(16, 17, 16),
                new HitWindow(24, 25, 24),
                new HitWindow(32, 33, 32)));
        // 3. Savage Bite & Horn Gore (1.80s = 36t): telegraph 0..18t (0.90s), cắn chí mạng 19t
        reg(new SkillTimeline(SkillState.SAVAGE_BITE, "bite", 36, 14, true,
                new HitWindow(18, 22, 19)));
        // 4. Wing Claw Sweep (1.80s = 36t): telegraph 0..16t (0.80s), quét cánh móng vuốt 18t
        reg(new SkillTimeline(SkillState.WING_CLAW_SWEEP, "wing_sweep", 36, 13, true,
                new HitWindow(17, 21, 18)));
        // 5. Tail Crescent Slam (2.00s = 40t): telegraph 0..18t (0.90s), quật chùy đuôi 20t
        reg(new SkillTimeline(SkillState.TAIL_CRESCENT_SLAM, "tail_slam", 40, 14, true,
                new HitWindow(19, 23, 20)));
        // 6. Winged Stomp (2.10s = 42t): lấy đà vỗ cánh 0..18t (0.90s), giậm đất chấn động 21t
        reg(new SkillTimeline(SkillState.WINGED_STOMP, "stomp", 42, 14, true,
                new HitWindow(20, 24, 21)));

        // --- 4 Kỹ năng ĐỘC QUYỀN Phase 2 (tốc độ 1.0x giữ chuẩn quỹ đạo bay & telegraph công bằng) ---
        // 7. Skyfall Inferno (4.60s = 92t): cất cánh 0..20t, khè biển lửa trên không 28..68t, đáp nặng 80t
        reg(new SkillTimeline(SkillState.SKYFALL_INFERNO, "skyfall_inferno", 92, 18, false,
                new HitWindow(28, 68, 28),
                new HitWindow(79, 82, 80)));
        // 8. Scorched Earth Beam (3.20s = 64t): gom lõi 0..26t (1.30s), bắn tia lửa áp suất cao 26..48t
        reg(new SkillTimeline(SkillState.SCORCHED_EARTH_BEAM, "beam", 64, 22, false,
                new HitWindow(26, 48, 26)));
        // 9. Cataclysm Wing Dive (3.80s = 76t): vọt lên 0..22t, khóa vòng cảnh báo 1.2s (22..46t), chạm đất 52t + sóng lớp 2 58t
        reg(new SkillTimeline(SkillState.CATACLYSM_WING_DIVE, "dive", 76, 44, false,
                new HitWindow(51, 54, 52),
                new HitWindow(57, 60, 58)));
        // 10. Ultimate "Ashen Supernova" (4.40s = 88t): tụ lực 2.2s (0..44t), bùng nổ 360° tại 46t + rơi tàn lửa 56t, 66t
        reg(new SkillTimeline(SkillState.ASHEN_SUPERNOVA, "supernova", 88, 42, false,
                new HitWindow(46, 49, 46),
                new HitWindow(56, 58, 56),
                new HitWindow(66, 68, 66)));

        // Biến hình Phase 2 "Vô Form" (5.50s = 110t cố định 1.0x)
        reg(new SkillTimeline(SkillState.PHASE_CHANGE, "phase_change", 110, 0, false));
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

    /** Tốc độ phát đồng bộ tuyệt đối giữa Server và Client -> độ lệch 0 tick ở cả 2 Phase. */
    public static float getAnimSpeed(SkillState state, boolean phase2) {
        SkillTimeline tl = TABLE.get(state);
        if (tl == null || !phase2 || !tl.allowPhase2Speedup()) {
            return 1.0f;
        }
        return CinderDragonConfig.get().phase2AttackAnimSpeed;
    }
}

