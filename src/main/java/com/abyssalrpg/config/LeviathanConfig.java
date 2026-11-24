package com.abyssalrpg.config;

import com.abyssalrpg.AbyssalRpgMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cấu hình JSON cho Boss Leviathan (lưu tại config/abyssalrpg_leviathan.json).
 *
 * =========================================================================
 * BẢNG KIỂM TRA SÁT THƯƠNG PHASE 1 THEO CÔNG THỨC GIÁP VANILLA (MC 1.21.1):
 *   effectiveDefense = min(20, max(armor / 5, armor - (4 * dmg) / (toughness + 8)))
 *   damageTaken      = dmg * (1 - effectiveDefense / 25)
 *   (Người chơi máu tối đa 20 HP, độ khó Bình thường, không enchant, nguồn mobAttack)
 * -------------------------------------------------------------------------
 * Loại giáp (Armor / Toughness) | Đòn nặng 30 | Shockwave 20 | Thường 15 | Đòn nặng P2 (45)
 * -------------------------------------------------------------------------
 * Không giáp      ( 0 /  0)     | 30.00 (CHẾT)| 20.00 (CHẾT) | 15.00     | 45.00 (CHẾT)
 * Giáp Da full    ( 7 /  0)     | 28.32 (CHẾT)| 18.88 (sống) | 14.16     | 42.48 (CHẾT)
 * Giáp Sắt full   (15 /  0)     | 26.40 (CHẾT)| 16.00 (sống) | 10.50     | 39.60 (CHẾT)
 * Giáp Kim Cương  (20 /  8)     | 15.00 (SỐNG)|  8.00 (sống) |  5.25     | 29.25 (cần Prot)
 * Giáp Netherite  (20 / 12)     | 13.20 (SỐNG)|  7.20 (sống) |  4.80     | 25.20 (cần Prot)
 * =========================================================================
 */
public final class LeviathanConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("abyssalrpg_leviathan.json");

    private static final int CURRENT_CONFIG_VERSION = 3;
    private static LeviathanConfig INSTANCE = createDefault();

    public enum VfxQuality {
        OFF(0.0f),
        MINIMAL(0.35f),
        NORMAL(1.0f);

        private final float scale;

        VfxQuality(float scale) {
            this.scale = scale;
        }

        public float scale() {
            return scale;
        }
    }

    // --- Phiên bản schema config (để 0 mặc định để Gson nhận diện file cũ khi nâng cấp) ---
    public int configVersion = 0;

    // --- Hệ số kích thước tổng (GIỮ NGUYÊN scale = 2.5f + tinh chỉnh thị giác mới visualScaleMultiplier = 1.10f) ---
    public float scale = 2.5f;
    public float visualScaleMultiplier = 1.10f;  // Tinh chỉnh độ đồ sộ bề thế model v2 (mặc định 1.10x, đồng bộ hitbox/tầm đánh/culling)
    public float phase2AttackAnimSpeed = 1.18f;  // Tốc độ phát animation đòn đánh nhanh ở Phase 2 (đồng bộ 0-tick Server & Client)
    public VfxQuality vfxQuality = VfxQuality.MINIMAL;
    public float particleMultiplier = 0.25f;     // Hệ số mật độ hạt VFX phía Client (giảm >75% hạt thừa, tôn glowmask fullbright)

    // --- Chỉ số cơ bản Phase 1 ---
    public double maxHealth = 1500.0;
    public double armor = 20.0;                  // Phase 2 (x1.5) = 30.0 (đúng trần giáp vanilla 30)
    public double armorToughness = 8.0;
    public double knockbackResistance = 1.0;
    public double baseMoveSpeed = 0.36;
    public double stepHeight = 2.5;
    public double detectionRadius = 96.0;        // Follow range lớn tương xứng thân 2.5x
    public double bossBarRadius = 104.0;         // Bossbar hiển thị bán kính >100 block
    public int maxActiveMinions = 6;

    // --- Kích hoạt & Hệ số Phase 2 "Cuồng bạo" (kích hoạt khi gần chết <= 5% HP hoặc <= 1 HP) ---
    public double phase2TriggerHpRatio = 0.05;   // 5% HP tối đa (75 HP) hoặc đòn chí mạng đưa xuống <= 1 HP
    public double phase2StatMultiplier = 1.50;   // Phase 2 = x1.5 (HP 1500->2250 qua BossHealthHolder; Giáp 20->30, ST & Tốc độ qua ADD_MULTIPLIED_BASE +0.5)
    public double phase2TurnSpeedMultiplier = 1.25; // Tốc độ xoay tối đa x1.25 (không x1.5 để tránh giật)
    public int phaseTransitionTicks = 90;        // 4.5 giây bất tử chuyển Phase 2 & hồi đầy 100% HP
    public int minTelegraphTicks = 8;            // Thời gian cảnh báo tối thiểu 0.4 giây (8 ticks)

    // --- Sát thương Phase 1 (Phase 2 tự nhân phase2StatMultiplier = 1.5x) ---
    public float meleeDamage = 15.0f;            // Đòn thường: 12-18 (Phase 2: 22.5)
    public float tailSlamDamage = 30.0f;         // Đòn nặng: 30 -> one-shot giáp sắt trở xuống (Phase 2: 45)
    public float biteChargeDamage = 30.0f;       // Đòn nặng: 30 -> one-shot giáp sắt trở xuống (Phase 2: 45)
    public float beamTickDamage = 14.0f;         // Đòn tick: 12-18 (Phase 2: 21)
    public float whirlpoolTickDamage = 12.0f;    // Đòn tick: 12-18 (Phase 2: 18)
    public float roarShockwaveDamage = 20.0f;    // Skill rộng: 20 (Phase 2: 30)
    public float stormBarrageDamage = 18.0f;     // Đòn mưa bão: 18 (Phase 2: 27)
    public float tidalWrathDamage = 30.0f;       // Đòn đập Ultimate: 30 (Phase 2: 45)

    // --- Thời gian hồi chiêu Phase 1 (ticks, 20 ticks = 1s; Phase 2 chia cho 1.5) ---
    public int tailSlamCooldown = 120;           // 6s -> P2: 4s
    public int biteChargeCooldown = 140;         // 7s -> P2: 4.65s
    public int beamCooldown = 180;               // 9s -> P2: 6s
    public int summonCooldown = 360;             // 18s -> P2: 12s
    public int whirlpoolCooldown = 260;          // 13s -> P2: 8.65s
    public int roarShockwaveCooldown = 200;      // 10s -> P2: 6.65s
    public int stormBarrageCooldown = 240;       // 12s -> P2: 8s
    public int tidalWrathCooldown = 1200;        // 60s -> P2: 40s

    // --- Bật log xoay đầu từng tick khi chuyển phase (cũng bật/tắt bằng lệnh /leviathan debug) ---
    public boolean debugRotationLogs = false;

    private static LeviathanConfig createDefault() {
        LeviathanConfig cfg = new LeviathanConfig();
        cfg.configVersion = CURRENT_CONFIG_VERSION;
        return cfg;
    }

    public static LeviathanConfig get() {
        return INSTANCE;
    }

    public float getEffectiveParticleMultiplier() {
        VfxQuality q = this.vfxQuality != null ? this.vfxQuality : VfxQuality.MINIMAL;
        return Math.max(0.0f, Math.min(2.0f, this.particleMultiplier * q.scale()));
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                LeviathanConfig loaded = GSON.fromJson(reader, LeviathanConfig.class);
                if (loaded != null) {
                    if (loaded.configVersion < 2) {
                        AbyssalRpgMod.LOGGER.info("Phát hiện cấu hình Leviathan v1 cũ, tự động nâng cấp sang v3 (2.5x / Model 4 tay vây lửa / VFX nhẹ).");
                        INSTANCE = createDefault();
                        save();
                        return;
                    }
                    if (loaded.configVersion < CURRENT_CONFIG_VERSION) {
                        // Bảo toàn 100% chỉ số chiến đấu v2 của người dùng, bổ sung tham số hình ảnh & VFX v3!
                        loaded.configVersion = CURRENT_CONFIG_VERSION;
                        if (loaded.visualScaleMultiplier <= 0.0f) loaded.visualScaleMultiplier = 1.10f;
                        if (loaded.phase2AttackAnimSpeed <= 0.0f) loaded.phase2AttackAnimSpeed = 1.18f;
                        if (loaded.vfxQuality == null) loaded.vfxQuality = VfxQuality.MINIMAL;
                        if (loaded.particleMultiplier <= 0.0f) loaded.particleMultiplier = 0.25f;
                        loaded.sanitize();
                        INSTANCE = loaded;
                        save();
                        AbyssalRpgMod.LOGGER.info("Đã nâng cấp cấu hình Leviathan v2 -> v3 (bảo toàn 100% chỉ số chiến đấu).");
                        return;
                    }
                    loaded.sanitize();
                    INSTANCE = loaded;
                    AbyssalRpgMod.LOGGER.info("Đã nạp cấu hình Leviathan v3 từ {}", CONFIG_PATH);
                    return;
                }
            } catch (Exception e) {
                AbyssalRpgMod.LOGGER.warn("Lỗi đọc cấu hình Leviathan, dùng giá trị mặc định: {}", e.getMessage());
            }
        }
        INSTANCE = createDefault();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            AbyssalRpgMod.LOGGER.warn("Không thể lưu cấu hình Leviathan: {}", e.getMessage());
        }
    }

    private void sanitize() {
        this.scale = Math.max(0.5f, Math.min(6.0f, this.scale));
        this.visualScaleMultiplier = Math.max(0.75f, Math.min(1.60f, this.visualScaleMultiplier));
        this.phase2AttackAnimSpeed = Math.max(1.00f, Math.min(1.35f, this.phase2AttackAnimSpeed));
        if (this.vfxQuality == null) this.vfxQuality = VfxQuality.MINIMAL;
        this.particleMultiplier = Math.max(0.0f, Math.min(2.0f, this.particleMultiplier));
        this.maxHealth = Math.max(200.0, Math.min(50000.0, this.maxHealth));
        this.armor = Math.max(0.0, Math.min(30.0, this.armor));
        this.armorToughness = Math.max(0.0, Math.min(20.0, this.armorToughness));
        this.knockbackResistance = Math.max(0.0, Math.min(1.0, this.knockbackResistance));
        this.stepHeight = Math.max(0.6, Math.min(6.0, this.stepHeight));
        this.detectionRadius = Math.max(32.0, Math.min(192.0, this.detectionRadius));
        this.bossBarRadius = Math.max(64.0, Math.min(256.0, this.bossBarRadius));
        this.maxActiveMinions = Math.max(1, Math.min(12, this.maxActiveMinions));
        this.phase2TriggerHpRatio = Math.max(0.005, Math.min(0.25, this.phase2TriggerHpRatio));
        this.phase2StatMultiplier = Math.max(1.0, Math.min(3.0, this.phase2StatMultiplier));
        this.phase2TurnSpeedMultiplier = Math.max(1.0, Math.min(2.0, this.phase2TurnSpeedMultiplier));
        this.phaseTransitionTicks = Math.max(40, Math.min(200, this.phaseTransitionTicks));
        this.minTelegraphTicks = Math.max(8, Math.min(40, this.minTelegraphTicks));
        this.tidalWrathCooldown = Math.max(200, this.tidalWrathCooldown);
    }
}
