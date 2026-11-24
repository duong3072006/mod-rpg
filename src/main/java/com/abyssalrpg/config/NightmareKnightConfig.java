package com.abyssalrpg.config;

import com.abyssalrpg.AbyssalRpgMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cấu hình JSON cho Boss Nightmare Knight (lưu tại `config/abyssalrpg_nightmare_knight.json`).
 *
 * =========================================================================
 * 1. BẢNG KIỂM TRA SÁT THƯƠNG THEO GIÁP VANILLA (MC 1.21.1, ĐỘ KHÓ NORMAL, KHÔNG ENCHANT):
 *    Công thức vanilla `CombatRules.getDamageAfterAbsorb`:
 *      effectiveDefense = min(20, max(armor / 5, armor - (4 * dmg) / (toughness + 8)))
 *      damageTaken      = dmg * (1 - effectiveDefense / 25)
 *    (Người chơi máu gốc 20.0 HP = 10 tim; DamageSource = `mobAttack`, KHÔNG bypass giáp, KHÔNG `setHealth`)
 * -------------------------------------------------------------------------
 * Bộ giáp (Armor / Toughness)  | P1 Nặng (34)   | P1 AoE (24) | P1 Thường (16) | P2 Nặng (61.2) | P2 AoE (43.2) | P2 Thường (28.8)
 * -------------------------------------------------------------------------
 * Không giáp      ( 0 /  0)    | 34.00 (CHẾT)   | 24.00 (CHẾT)| 16.00 (4.0 HP) | 61.20 (CHẾT)   | 43.20 (CHẾT)  | 28.80 (CHẾT)
 * Giáp Da full    ( 7 /  0)    | 32.10 (CHẾT)   | 22.66 (CHẾT)| 15.10 (4.9 HP) | 57.77 (CHẾT)   | 40.78 (CHẾT)  | 27.19 (CHẾT)
 * Giáp Sắt full   (15 /  0)    | 29.92 (CHẾT)   | 21.12 (CHẾT)| 11.52 (8.5 HP) | 53.86 (CHẾT)   | 38.02 (CHẾT)  | 25.34 (CHẾT)
 * Giáp Kim Cương  (20 /  8)    | 18.36 (1.64 HP)| 10.56 (9.4) |  5.76 (14.2 HP)| 49.69 (CHẾT)   | 27.30 (CHẾT)  | 14.75 (5.25 HP)
 * Giáp Netherite  (20 / 12)    | 16.05 (3.95 HP)|  9.41 (10.6)|  5.25 (14.8 HP)| 42.20 (CHẾT)   | 23.57 (CHẾT)  | 13.06 (6.94 HP)
 * -------------------------------------------------------------------------
 * KIỂM CHỨNG ĐẠT CHUẨN THIẾT KẾ NGAY TẠI ĐÒN NẶNG = 34.0:
 * - Full Kim Cương không enchant (Normal): nhận 18.36 HP -> còn lại **1.64 HP (0.82 tim — dưới 1 tim, "gần one-shot")**!
 * - Full Sắt / Da / Không giáp: nhận 29.92..34.00 >= 20 HP -> **ONE-SHOT**.
 * - Full Netherite không enchant: nhận 16.05 HP -> còn 3.95 HP (~2 tim).
 *
 * SAI SỐ KHI CÓ ENCHANT / BUFF / ĐỘ KHÓ KHÁC:
 * - Protection IV full 4 món (EPF = 16 -> giảm thêm 64% sát thương sau giáp):
 *     P1 Nặng (34) trên Kim Cương = 18.36 * 0.36 = 6.61 HP; P2 Nặng (61.2) trên Netherite = 42.20 * 0.36 = 15.19 HP (sống sót!).
 * - Resistance I..IV: giảm tiếp 20% mỗi cấp (Resistance I = x0.80, Resistance II = x0.60).
 * - Absorption (Golden Apple / Totem): cộng khiên máu vàng hấp thụ trực tiếp trước khi trừ 20 HP gốc.
 * - Độ khó Hard: vanilla nhân sát thương `mobAttack` của quái thêm x1.5 -> P1 Nặng thành 51.0 (Kim Cương nhận 36.21 HP -> one-shot nếu không Protection/khiên).
 *   Độ khó Easy: vanilla nhân x0.5 + 1.
 *
 * =========================================================================
 * 2. BẢNG ƯỚC TÍNH THỜI GIAN HẠ GỤC (TTK) — KIẾM NETHERITE SHARPNESS V (MC 1.21.1):
 *    - Sát thương gốc kiếm Netherite Sharpness V:
 *        Đánh thường = 8.0 + 3.0 = 11.0 raw | Đánh chí mạng (Crit) = 8.0 * 1.5 + 3.0 = 15.0 raw.
 *        (Tốc độ đánh tối đa 1.6 đòn/giây; tốc độ thực chiến có né telegraph ~0.90 đòn/giây).
 *    - Tổng HP cần đánh xuyên 2 phase: 3800 HP (Phase 1 xuống ngưỡng 5% = 200 HP) + 7200 HP (Phase 2) = 11,000 HP.
 *    - Với Defense 40 (P1) và 72 (P2), toughness = 0: `def - dmg/2` >= 32.5 > mọi trần `maxDamageReduction * 25` (15..20),
 *      nên toàn bộ đòn kiếm đều bị kẹp ở đúng mức `maxDamageReduction`:
 * -------------------------------------------------------------------------
 * maxDamageReduction | Dmg/đòn (Thường / Crit) | Số đòn (Thường / Crit) | TTK Liên tục 1.6/s (Thường / Crit) | TTK Thực chiến 0.9/s (Crit)
 * -------------------------------------------------------------------------
 * 0.60 (60% giảm)    | 4.40 / 6.00 HP          | 2,500 / 1,834 đòn      | 26.0 phút / 19.1 phút              | ~34.0 phút (solo vanilla)
 * 0.70 (70% giảm)*   | 3.30 / 4.50 HP          | 3,334 / 2,445 đòn      | 34.7 phút / 25.5 phút              | ~45.3 phút (solo vanilla)
 * 0.80 (80% giảm)    | 2.20 / 3.00 HP          | 5,000 / 3,667 đòn      | 52.1 phút / 38.2 phút              | ~67.9 phút (solo vanilla)
 * -------------------------------------------------------------------------
 * (*) Mặc định 0.70 — raid boss cấp cuối dành cho vũ khí RPG bậc cao (T7-T10) / Strength II / tổ đội 2-4 người
 *     (tổ đội 3 người dùng vũ khí RPG + Strength II hạ trong ~5-8 phút). Giảm về 0.60 nếu muốn solo nhanh bằng đồ vanilla.
 * =========================================================================
 */
public final class NightmareKnightConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("abyssalrpg_nightmare_knight.json");

    private static NightmareKnightConfig INSTANCE = new NightmareKnightConfig();

    public enum VfxQuality {
        MINIMAL(0.20f),
        LOW(0.45f),
        MEDIUM(0.85f);

        public final float presetMultiplier;
        VfxQuality(float presetMultiplier) {
            this.presetMultiplier = presetMultiplier;
        }
    }

    public int configVersion = 3;

    // --- Kích thước: 3x mốc SCALE = 2.5f của Leviathan/Spirit Hydra/Cerberus cơ sở ---
    /** SCALE tuyến tính = 3 * 2.5f = 7.5f (chiều cao hiển thị ~19.5 block = 3x mốc 6.5 block). */
    public float scale = 7.5f;
    /** Tốc độ phát animation + tiến trình đòn đánh đồng bộ ở Phase 2 (1.0x–1.35x, mặc định 1.20x). */
    public float phase2AttackAnimSpeed = 1.20f;
    /** Bật chế độ tối ưu VFX giảm mạnh hạt particle (mặc định bật để ổn định FPS/MSPT). */
    public boolean reducedVfxMode = true;
    /** Preset chất lượng VFX mặc định MINIMAL (có thể đổi LOW / MEDIUM trên máy mạnh). */
    public VfxQuality vfxQuality = VfxQuality.MINIMAL;
    /** Hệ số mật độ hạt particle mặc định 0.20 (chống tụt FPS trên máy tầm trung). */
    public float particleMultiplier = 0.20f;
    /** Bật rung camera cực nhẹ đúng nhịp chân đặt xuống kiểu Pacific Rim (tắt được trong config). */
    public boolean footstepScreenShakeEnabled = true;

    // --- Chỉ số Phase 1 ---
    public double maxHealth = 4000.0;               // Phase 2 = 4000 * 1.8 = 7200 HP (qua BossHealthHolder)
    public float defense = 40.0f;                   // Phase 2 = 40 * 1.8 = 72 (qua BossDefenseHolder, vượt trần vanilla 30)
    public float bossToughness = 0.0f;              // Độ cứng giáp tùy chỉnh trong `2 + toughness / 4`
    public float maxDamageReduction = 0.70f;        // Trần giảm sát thương mặc định 70% (kẹp cứng <= 0.90)
    public float minDamagePerHit = 0.5f;            // Sàn sát thương tối thiểu mỗi đòn
    public double knockbackResistance = 1.0;
    public double baseMoveSpeed = 0.28;             // Phase 2 = x1.8 -> 0.504
    public double stepHeight = 3.8;                 // Leo bậc lớn cho thân khổng lồ
    public double detectionRadius = 128.0;
    public double bossBarRadius = 140.0;
    public float soundRangeMultiplier = 2.5f;
    public float screenShakeRadiusMultiplier = 2.2f;

    // --- Địa hình & Hồi máu ngoài giao tranh ---
    /** Mặc định KHÔNG phá terrain; khi bật chỉ phá lá, cỏ, hoa, dây leo, lớp tuyết mềm trên đường đi. */
    public boolean terrainDamageEnabled = false;
    public double outOfCombatHealRadius = 128.0;
    public int outOfCombatHealDelayTicks = 600;     // 30 giây không có người chơi trong 128 block
    public float outOfCombatHealPerSecond = 25.0f;  // Hồi dần 25 HP/giây ngoài giao tranh

    // --- Phase 2 "Cuồng bạo" ---
    public double phase2TriggerHpRatio = 0.05;      // <= 5% maxHP (200 HP) hoặc đòn đưa xuống <= 1 HP
    public double phase2StatMultiplier = 1.80;      // HP, Defense, Damage, MoveSpeed x1.8; Cooldown /1.8
    public double phase2TurnSpeedMultiplier = 1.30; // Tốc độ xoay tối đa x1.3 (không x1.8 để tránh giật)
    public int phaseTransitionTicks = 120;          // 6.0 giây (5-7 giây) bất tử chuyển phase
    public int minTelegraphTicks = 10;              // Telegraph tối thiểu 0.5 giây (10 ticks) ở Phase 2

    // --- Nightmare Realm (Skill #11 Phase 2) ---
    public double nightmareRealmRadius = 36.0;
    public boolean nightmareRealmEffectsEnabled = true;
    public float nightmareRealmBossHealPerSecond = 8.0f;

    // --- Sát thương từng skill Phase 1 (Phase 2 tự nhân phase2StatMultiplier = 1.8x) ---
    public float greatswordSlashDamage = 34.0f;     // Đòn nặng (P2: ~61.2)
    public float overheadSmashDamage = 34.0f;       // Đòn nặng (P2: ~61.2)
    public float overheadShockwaveDamage = 24.0f;   // Sóng quạt diện rộng (P2: ~43.2)
    public float thrustDamage = 34.0f;              // Đòn nặng tầm xa (P2: ~61.2)
    public float clawSwipeDamage = 16.0f;           // Đòn thường 2 nhát (P2: ~28.8)
    public float leapImpactDamage = 34.0f;          // Đáp xuống nặng (P2: ~61.2)
    public float leapWaveDamage = 24.0f;            // Sóng xung kích khi đáp (P2: ~43.2)
    public float boneSpikeDamage = 24.0f;           // Gai xương trồi lên (P2: ~43.2)
    public float swordWaveDamage = 24.0f;           // Sóng kiếm mặt đất (P2: ~43.2)
    public float boneCageEruptDamage = 14.0f;       // Sát thương nhẹ khi lồng mọc (không giam chết)
    // Phase 2 skills (giá trị gốc Phase 1 -> tự nhân x1.8 khi thi triển ở Phase 2)
    public float nightmareComboHitDamage = 20.0f;   // Chuỗi 3-4 đòn chém (P2: 36/nhát, nhát cuối x1.35)
    public float spikeStormDamage = 22.0f;          // Mưa gai xương (P2: ~39.6)
    public float spinSlashDamage = 24.0f;           // Xoáy chém 360° (P2: ~43.2)
    public float flameSwordWaveDamage = 24.0f;      // Sóng kiếm lửa (P2: ~43.2)
    public float flameTrailTickDamage = 10.0f;      // Vệt lửa mặt đất (P2: ~18)
    public float ultimateBurstDamage = 34.0f;       // Ultimate Nightmare Descent (P2: ~61.2)
    public float ultimateWaveDamage = 24.0f;        // 3 lớp sóng xung kích Ultimate (P2: ~43.2)

    // --- Cooldown Phase 1 (ticks; Phase 2 tự chia cho phase2StatMultiplier = 1.8) ---
    public int greatswordSlashCooldown = 110;
    public int overheadSmashCooldown = 140;
    public int thrustCooldown = 130;
    public int clawSwipeCooldown = 80;
    public int leapSmashCooldown = 190;
    public int boneSpikeEruptionCooldown = 150;
    public int swordWaveCooldown = 130;
    public int boneCageCooldown = 260;
    public int nightmareComboCooldown = 200;
    public int spikeStormCooldown = 260;
    public int nightmareRealmCooldown = 460;
    public int spinSlashCooldown = 190;
    public int flameSwordWaveCooldown = 170;
    public double ultimateHpThreshold = 0.20;       // Chỉ dùng dưới 20% HP ở Phase 2
    public int ultimateCooldownTicks = 1200;        // Tối đa 1 lần mỗi 60 giây (cố định)

    // --- Debug xoay & phòng thủ (/abyss nightmare debug) ---
    public boolean debugRotationLogs = false;

    public static NightmareKnightConfig get() {
        return INSTANCE;
    }

    /** Hệ số hạt thực tế tổng hợp giữa `vfxQuality` và `particleMultiplier` (mặc định 0.20 ở MINIMAL). */
    public float getEffectiveParticleMultiplier() {
        VfxQuality q = this.vfxQuality != null ? this.vfxQuality : VfxQuality.MINIMAL;
        if (q == VfxQuality.MINIMAL) {
            return Math.max(0.0f, Math.min(0.35f, this.particleMultiplier));
        }
        return Math.max(this.particleMultiplier, q.presetMultiplier);
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                JsonElement el = JsonParser.parseReader(reader);
                if (el != null && el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    NightmareKnightConfig loaded = GSON.fromJson(obj, NightmareKnightConfig.class);
                    if (loaded != null) {
                        boolean migrated = false;
                        if (loaded.configVersion < 2) {
                            loaded.configVersion = 2;
                            loaded.phase2AttackAnimSpeed = 1.20f;
                            loaded.reducedVfxMode = true;
                            migrated = true;
                        }
                        if (loaded.configVersion < 3) {
                            loaded.configVersion = 3;
                            loaded.vfxQuality = VfxQuality.MINIMAL;
                            loaded.particleMultiplier = 0.20f;
                            loaded.footstepScreenShakeEnabled = true;
                            migrated = true;
                        }
                        loaded.sanitize();
                        INSTANCE = loaded;
                        if (migrated) {
                            save();
                        }
                        AbyssalRpgMod.LOGGER.info("Đã nạp cấu hình Nightmare Knight v{} từ {}", loaded.configVersion, CONFIG_PATH);
                        return;
                    }
                }
            } catch (Exception e) {
                AbyssalRpgMod.LOGGER.warn("Lỗi đọc cấu hình Nightmare Knight, dùng giá trị mặc định: {}", e.getMessage());
            }
        }
        INSTANCE = new NightmareKnightConfig();
        INSTANCE.sanitize();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            AbyssalRpgMod.LOGGER.warn("Không thể lưu cấu hình Nightmare Knight: {}", e.getMessage());
        }
    }

    private void sanitize() {
        this.scale = Math.max(2.0f, Math.min(16.0f, this.scale));
        this.phase2AttackAnimSpeed = Math.max(1.0f, Math.min(1.35f, this.phase2AttackAnimSpeed));
        if (this.vfxQuality == null) {
            this.vfxQuality = VfxQuality.MINIMAL;
        }
        this.particleMultiplier = Math.max(0.0f, Math.min(1.0f, this.particleMultiplier));
        this.maxHealth = Math.max(500.0, Math.min(100000.0, this.maxHealth));
        this.defense = Math.max(0.0f, Math.min(500.0f, this.defense));
        this.bossToughness = Math.max(0.0f, Math.min(100.0f, this.bossToughness));
        this.maxDamageReduction = Math.max(0.10f, Math.min(0.90f, this.maxDamageReduction));
        this.minDamagePerHit = Math.max(0.1f, Math.min(10.0f, this.minDamagePerHit));
        this.knockbackResistance = Math.max(0.0, Math.min(1.0, this.knockbackResistance));
        this.baseMoveSpeed = Math.max(0.15, Math.min(0.65, this.baseMoveSpeed));
        this.stepHeight = Math.max(1.0, Math.min(6.0, this.stepHeight));
        this.detectionRadius = Math.max(48.0, Math.min(256.0, this.detectionRadius));
        this.bossBarRadius = Math.max(64.0, Math.min(256.0, this.bossBarRadius));
        this.soundRangeMultiplier = Math.max(1.0f, Math.min(5.0f, this.soundRangeMultiplier));
        this.screenShakeRadiusMultiplier = Math.max(1.0f, Math.min(4.0f, this.screenShakeRadiusMultiplier));
        this.outOfCombatHealRadius = Math.max(32.0, Math.min(256.0, this.outOfCombatHealRadius));
        this.outOfCombatHealDelayTicks = Math.max(100, Math.min(6000, this.outOfCombatHealDelayTicks));
        this.outOfCombatHealPerSecond = Math.max(0.0f, Math.min(200.0f, this.outOfCombatHealPerSecond));
        this.phase2TriggerHpRatio = Math.max(0.005, Math.min(0.25, this.phase2TriggerHpRatio));
        this.phase2StatMultiplier = Math.max(1.0, Math.min(3.0, this.phase2StatMultiplier));
        this.phase2TurnSpeedMultiplier = Math.max(1.0, Math.min(2.0, this.phase2TurnSpeedMultiplier));
        this.phaseTransitionTicks = Math.max(100, Math.min(140, this.phaseTransitionTicks));
        this.minTelegraphTicks = Math.max(10, Math.min(40, this.minTelegraphTicks));
        this.nightmareRealmRadius = Math.max(12.0, Math.min(96.0, this.nightmareRealmRadius));
        this.nightmareRealmBossHealPerSecond = Math.max(0.0f, Math.min(100.0f, this.nightmareRealmBossHealPerSecond));
        this.ultimateHpThreshold = Math.max(0.05, Math.min(0.50, this.ultimateHpThreshold));
        this.ultimateCooldownTicks = Math.max(400, Math.min(3600, this.ultimateCooldownTicks));
    }
}

