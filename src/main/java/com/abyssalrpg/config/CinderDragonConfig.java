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
 * Cấu hình JSON cho Boss Cinder Dragon — Hỏa Long Giáp Đá (`config/abyssalrpg_cinder_dragon.json`).
 *
 * =========================================================================
 * BẢNG KIỂM TRA SÁT THƯƠNG THEO GIÁP VANILLA (MC 1.21.1, KHÔNG ENCHANT, `mobAttack` KHÔNG BYPASS):
 *   Công thức vanilla `CombatRules.getDamageAfterAbsorb`:
 *     effDef = min(20, max(armor / 5, armor - (4 * dmg) / (toughness + 8)))
 *     taken  = dmg * (1 - effDef / 25)
 *   (Người chơi gốc 20.0 HP = 10 tim; không dùng `setHealth`; giáp + toughness + Protection/Resistance hoạt động đầy đủ.)
 *
 * [A] ĐỘ KHÓ NORMAL (hệ số vanilla 1.0x):
 * -------------------------------------------------------------------------------------------------------------
 * Bộ giáp (Armor / Tough) | P1 Nặng (31)   | P1 Thường (22) | P1 Lửa/5t (9) | P2 Nặng (52.7) | P2 Thường (37.4) | P2 Lửa/5t (15.3)
 * -------------------------------------------------------------------------------------------------------------
 * Không giáp   ( 0 /  0)  | 31.00 (CHẾT)   | 22.00 (CHẾT)   | 9.00 (+cháy)  | 52.70 (CHẾT)   | 37.40 (CHẾT)     | 15.30 (4.7 HP)
 * Giáp Da      ( 7 /  0)  | 29.26 (CHẾT)   | 20.77 (CHẾT)   | 7.20 (+cháy)  | 49.75 (CHẾT)   | 35.31 (CHẾT)     | 14.44 (5.6 HP)
 * Giáp Sắt     (15 /  0)  | 27.28 (CHẾT)   | 18.48 (1.5 HP) | 5.22 (+cháy)  | 46.38 (CHẾT)   | 32.91 (CHẾT)     | 10.80 (9.2 HP)
 * Giáp Kim Cương (20 / 8) | 15.81 (4.2 HP) |  9.24 (10.8HP) | 2.61 (+cháy)  | 40.53 (CHẾT)   | 22.07 (CHẾT)     |  5.39 (14.6 HP)
 * Giáp Netherite (20 /12) | 13.89 (6.1 HP) |  8.27 (11.7HP) | 2.45 (+cháy)  | 33.85 (CHẾT)   | 18.67 (1.3 HP)   |  4.93 (15.1 HP)
 * -------------------------------------------------------------------------------------------------------------
 * -> Giáp Sắt full bị one-shot bởi đòn nặng Phase 1 (`27.28 >= 20 HP`); Giáp Kim Cương còn ~2.1–3.1 tim (tăng lên ~7.2 tim
 *    với Protection II–IV); Giáp Netherite còn ~3.1 tim; Phase 2 "Vô Form" (×1.7) yêu cầu né telegraph / Protection IV / khiên!
 *
 * [B] ĐỘ KHÓ HARD (vanilla nhân sát thương `mobAttack` thêm 1.5x -> P1 Nặng = 46.5, Thường = 33.0, Lửa/5t = 13.5):
 * -------------------------------------------------------------------------------------------------------------
 * Bộ giáp (Armor / Tough) | P1 Nặng (46.5) | P1 Thường (33) | P1 Lửa/5t (13.5)| P2 Nặng (79.1) | P2 Thường (56.1) | P2 Lửa/5t (23.0)
 * -------------------------------------------------------------------------------------------------------------
 * Không giáp   ( 0 /  0)  | 46.50 (CHẾT)   | 33.00 (CHẾT)   | 13.50 (6.5 HP)  | 79.05 (CHẾT)   | 56.10 (CHẾT)     | 22.95 (CHẾT)
 * Giáp Da      ( 7 /  0)  | 43.90 (CHẾT)   | 31.15 (CHẾT)   | 12.74 (7.3 HP)  | 74.62 (CHẾT)   | 52.96 (CHẾT)     | 21.66 (CHẾT)
 * Giáp Sắt     (15 /  0)  | 40.92 (CHẾT)   | 29.04 (CHẾT)   |  8.91 (11.1 HP) | 69.56 (CHẾT)   | 49.37 (CHẾT)     | 19.37 (0.6 HP)
 * Giáp Kim Cương (20 / 8) | 30.92 (CHẾT)   | 17.49 (2.5 HP) |  4.52 (15.5 HP) | 66.40 (CHẾT)   | 44.95 (CHẾT)     |  9.86 (10.1 HP)
 * Giáp Netherite (20 /12) | 26.60 (CHẾT)   | 15.31 (4.7 HP) |  4.16 (15.8 HP) | 62.61 (CHẾT)   | 37.52 (CHẾT)     |  8.81 (11.2 HP)
 * -------------------------------------------------------------------------------------------------------------
 * =========================================================================
 */
public final class CinderDragonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("abyssalrpg_cinder_dragon.json");

    private static CinderDragonConfig INSTANCE = new CinderDragonConfig();

    public enum VfxQuality {
        MINIMAL(0.25f),
        LOW(0.50f),
        MEDIUM(0.90f);

        public final float presetMultiplier;

        VfxQuality(float presetMultiplier) {
            this.presetMultiplier = presetMultiplier;
        }
    }

    public int configVersion = 2;

    // --- Kích thước Rồng Cổ Đại (SCALE = 5.35f -> vai/đầu 8.0–8.8 block, dài ~25.5–26.0 block, sải cánh ~28.7–31.5 block) ---
    public float scale = 5.35f;
    public float visualScaleMultiplier = 1.0f;
    public float phase2AttackAnimSpeed = 1.20f;

    // --- Tối ưu VFX, Camera & Debug ---
    public boolean reducedVfxMode = true;
    public VfxQuality vfxQuality = VfxQuality.MINIMAL;
    public float particleMultiplier = 0.25f;
    public boolean enableFootstepShake = true;
    public boolean enableDebugCommands = false;
    public boolean debugLogs = false;

    // --- Chỉ số Phase 1 ---
    public double maxHealth = 2700.0;              // Phase 2 = 2700 * 1.7 = 4590 HP (qua BossHealthHolder)
    public float defense = 20.0f;                  // Phase 2 = 20 * 1.7 = 34 (qua BossDefenseHolder, vượt trần vanilla 30)
    public float bossToughness = 0.0f;
    public float maxDamageReduction = 0.70f;
    public float minDamagePerHit = 0.5f;
    public double knockbackResistance = 1.0;
    public double baseMoveSpeed = 0.31;
    public double baseFlySpeed = 0.58;
    public double stepHeight = 2.4;
    public double detectionRadius = 112.0;
    public double bossBarRadius = 140.0;
    public float soundRangeMultiplier = 2.2f;
    public float screenShakeRadiusMultiplier = 1.9f;

    // --- Địa hình & Hồi máu ngoài giao tranh ---
    public boolean terrainDamageEnabled = false;
    public double outOfCombatHealRadius = 140.0;
    public int outOfCombatHealDelayTicks = 600;
    public float outOfCombatHealPerSecond = 20.0f;

    // --- Phase 2 "Vô Form" (hệ số mặc định ×1.7, xoay ×1.25 — tách riêng cấu hình từng mục) ---
    public double phase2TriggerHpRatio = 0.05;     // <= 5% maxHP hoặc đòn đưa xuống <= 1 HP
    public double phase2HealthMultiplier = 1.70;   // 2700 -> 4590 HP
    public double phase2DefenseMultiplier = 1.70;  // 20 -> 34
    public double phase2DamageMultiplier = 1.70;   // Nặng 31 -> 52.7, Thường 18..22 -> 30.6..37.4, Lửa 9 -> 15.3
    public double phase2SpeedMultiplier = 1.70;    // Di chuyển & bay ×1.7
    public double phase2CooldownDivisor = 1.70;    // Cooldown chia 1.7
    public double phase2TurnSpeedMultiplier = 1.25;// Tốc độ xoay ×1.25 (tránh xoay giật)
    public int phaseTransitionTicks = 110;         // 5.5 giây bất tử biến hình
    public int minTelegraphTicks = 10;             // Telegraph tối thiểu >= 0.5 giây

    // --- Sát thương cơ sở Phase 1 (Phase 2 tự nhân phase2DamageMultiplier = 1.70) ---
    public float savageBiteDamage = 31.0f;         // Đòn nặng (P2: 52.7)
    public float tailCrescentSlamDamage = 31.0f;   // Đòn nặng (P2: 52.7)
    public float wingedStompDamage = 31.0f;        // Đòn nặng (P2: 52.7)
    public float cinderBreathTickDamage = 9.0f;    // Tick mỗi 5 tick trong luồng lửa + cháy 4s (P2: 15.3)
    public float wingClawSweepDamage = 22.0f;      // Đòn thường (P2: 37.4)
    public float magmaFireballDamage = 18.0f;      // Đòn thường (P2: 30.6)
    public float firePatchTickDamage = 5.0f;       // Vệt lửa ngắn 3-4s trên đất (P2: 8.5)
    // 4 chiêu độc quyền Phase 2 (giá trị nền P1 -> nhân x1.70 khi thi triển ở Phase 2)
    public float skyfallInfernoTickDamage = 9.0f;  // P2: 15.3 mỗi 5t
    public float skyfallLandingDamage = 24.0f;     // P2: 40.8
    public float scorchedBeamTickDamage = 12.0f;   // P2: 20.4 mỗi 4t
    public float cataclysmDiveDamage = 31.0f;      // P2: 52.7
    public float cataclysmWaveDamage = 20.0f;      // P2: 34.0
    public float ashenSupernovaDamage = 31.0f;     // P2: 52.7
    public float ashenEmberDropDamage = 20.0f;     // P2: 34.0

    // --- Cooldown Phase 1 (ticks; Phase 2 chia phase2CooldownDivisor = 1.70) ---
    public int cinderBreathCooldown = 140;
    public int magmaFireballCooldown = 120;
    public int savageBiteCooldown = 95;
    public int wingClawSweepCooldown = 105;
    public int tailCrescentSlamCooldown = 110;
    public int wingedStompCooldown = 145;
    // Phase 2 exclusives
    public int skyfallInfernoCooldown = 260;
    public int scorchedBeamCooldown = 220;
    public int cataclysmDiveCooldown = 240;
    public double ultimateHpThreshold = 0.25;      // Dưới 25% HP ở Phase 2
    public int ultimateCooldownTicks = 1200;       // 60 giây cố định

    public static CinderDragonConfig get() {
        return INSTANCE;
    }

    public float getEffectiveScale() {
        return this.scale * this.visualScaleMultiplier;
    }

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
                    CinderDragonConfig loaded = GSON.fromJson(obj, CinderDragonConfig.class);
                    if (loaded != null) {
                        boolean migrated = false;
                        if (loaded.configVersion < 2) {
                            if (Math.abs(loaded.scale - 4.5f) < 0.05f) {
                                loaded.scale = 5.35f;
                            }
                            if (loaded.stepHeight > 2.6 || loaded.stepHeight < 2.2) {
                                loaded.stepHeight = 2.4;
                            }
                            if (loaded.bossBarRadius < 136.0) {
                                loaded.bossBarRadius = 140.0;
                            }
                            if (loaded.detectionRadius < 108.0) {
                                loaded.detectionRadius = 112.0;
                            }
                            if (loaded.outOfCombatHealRadius < 136.0) {
                                loaded.outOfCombatHealRadius = 140.0;
                            }
                            loaded.enableFootstepShake = true;
                            loaded.configVersion = 2;
                            migrated = true;
                        }
                        loaded.sanitize();
                        INSTANCE = loaded;
                        if (migrated) {
                            save();
                        }
                        AbyssalRpgMod.LOGGER.info("Đã nạp cấu hình Cinder Dragon v{} từ {}", loaded.configVersion, CONFIG_PATH);
                        return;
                    }
                }
            } catch (Exception e) {
                AbyssalRpgMod.LOGGER.warn("Lỗi đọc cấu hình Cinder Dragon, dùng giá trị mặc định: {}", e.getMessage());
            }
        }
        INSTANCE = new CinderDragonConfig();
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
            AbyssalRpgMod.LOGGER.warn("Không thể lưu cấu hình Cinder Dragon: {}", e.getMessage());
        }
    }

    private void sanitize() {
        this.scale = Math.max(2.0f, Math.min(12.0f, this.scale));
        this.visualScaleMultiplier = Math.max(0.5f, Math.min(2.0f, this.visualScaleMultiplier));
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
        this.baseMoveSpeed = Math.max(0.15, Math.min(0.75, this.baseMoveSpeed));
        this.baseFlySpeed = Math.max(0.25, Math.min(1.20, this.baseFlySpeed));
        this.stepHeight = Math.max(1.0, Math.min(6.0, this.stepHeight));
        this.detectionRadius = Math.max(48.0, Math.min(256.0, this.detectionRadius));
        this.bossBarRadius = Math.max(64.0, Math.min(256.0, this.bossBarRadius));
        this.phase2TriggerHpRatio = Math.max(0.005, Math.min(0.25, this.phase2TriggerHpRatio));
        this.phase2HealthMultiplier = Math.max(1.0, Math.min(3.0, this.phase2HealthMultiplier));
        this.phase2DefenseMultiplier = Math.max(1.0, Math.min(3.0, this.phase2DefenseMultiplier));
        this.phase2DamageMultiplier = Math.max(1.0, Math.min(3.0, this.phase2DamageMultiplier));
        this.phase2SpeedMultiplier = Math.max(1.0, Math.min(2.5, this.phase2SpeedMultiplier));
        this.phase2CooldownDivisor = Math.max(1.0, Math.min(3.0, this.phase2CooldownDivisor));
        this.phase2TurnSpeedMultiplier = Math.max(1.0, Math.min(2.0, this.phase2TurnSpeedMultiplier));
        this.phaseTransitionTicks = Math.max(100, Math.min(140, this.phaseTransitionTicks));
        this.minTelegraphTicks = Math.max(10, Math.min(40, this.minTelegraphTicks));
        this.ultimateHpThreshold = Math.max(0.05, Math.min(0.50, this.ultimateHpThreshold));
        this.ultimateCooldownTicks = Math.max(400, Math.min(3600, this.ultimateCooldownTicks));
    }
}

