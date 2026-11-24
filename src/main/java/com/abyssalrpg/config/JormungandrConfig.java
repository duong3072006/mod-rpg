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
 * Cấu hình JSON cho Boss Jörmungandr (lưu tại `config/abyssalrpg_jormungandr.json`).
 *
 * =========================================================================
 * 1. BẢNG KIỂM TRA SÁT THƯƠNG THEO GIÁP VANILLA (MC 1.21.1, ĐỘ KHÓ NORMAL, KHÔNG ENCHANT):
 *    Công thức vanilla `CombatRules.getDamageAfterAbsorb`:
 *      effectiveDefense = min(20, max(armor / 5, armor - (4 * dmg) / (toughness + 8)))
 *      damageTaken      = dmg * (1 - effectiveDefense / 25)
 *    (Người chơi máu 20 HP = 10 tim; DamageSource = `mobAttack`, KHÔNG bypass giáp, KHÔNG `setHealth`)
 * -------------------------------------------------------------------------
 * Bộ giáp (Armor / Toughness)  | P1 Nặng (22)   | P1 AoE (16) | P1 Tiếp xúc (6) | P2 Nặng (30.8)  | P2 AoE (22.4) | P2 Tiếp xúc (8.4)
 * -------------------------------------------------------------------------
 * Không giáp      ( 0 /  0)    | 22.00 (CHẾT)   | 16.00 (4.0) |  6.00 (14.0)    | 30.80 (CHẾT)    | 22.40 (CHẾT)  |  8.40 (11.6)
 * Giáp Da full    ( 7 /  0)    | 20.77 (CHẾT)   | 15.10 (4.9) |  5.04 (15.0)    | 29.08 (CHẾT)    | 21.15 (CHẾT)  |  7.46 (12.5)
 * Giáp Sắt full   (15 /  0)    | 18.48 (1.52 HP)| 11.52 (8.5) |  3.12 (16.9)    | 27.10 (CHẾT)    | 18.82 (1.18)  |  4.77 (15.2)
 * Giáp Kim Cương  (20 /  8)    |  9.24 (10.8 HP)|  5.76 (14.2)|  1.56 (18.4)    | 15.65 (4.35 HP) |  9.50 (10.5)  |  2.39 (17.6)
 * Giáp Netherite  (20 / 12)    |  8.27 (11.7 HP)|  5.25 (14.8)|  1.49 (18.5)    | 13.75 (6.25 HP) |  8.47 (11.5)  |  2.26 (17.7)
 * -------------------------------------------------------------------------
 * KIỂM CHỨNG ĐẠT CHUẨN THIẾT KẾ NGAY TẠI ĐÒN NẶNG P1 = 22.0 & P2 = 30.8:
 * - P1 Nặng (22.0): Giáp Sắt đủ bộ nhận **18.48 HP** -> còn **1.52 HP (< 2 máu, "gần one-shot")**;
 *   Giáp Da / Không giáp chết ngay 1 đòn; Giáp Kim Cương còn **10.76 HP (~nửa máu)**!
 * - P2 Nặng (30.8 = 22 × 1.4): Giáp Sắt trở xuống chết ngay 1 đòn (27.10+); Giáp Kim Cương còn **4.35 HP (~4 máu)**!
 *
 * SAI SỐ KHI CÓ ENCHANT / BUFF / ĐỘ KHÓ KHÁC:
 * - Protection IV full 4 món (EPF = 16 -> giảm tiếp 64% sát thương sau giáp):
 *     P1 Nặng (22) trên Sắt = 6.65 HP; P2 Nặng (30.8) trên Kim Cương = 5.63 HP.
 * - Resistance I..IV: giảm tiếp 20% mỗi cấp; Absorption cộng khiên vàng hấp thụ trước 20 HP.
 * - Độ khó Hard: vanilla nhân `mobAttack` thêm x1.5 (P1 Nặng 22 -> 33.0: Sắt chết ngay, Kim Cương nhận 17.49 HP còn 2.5 HP).
 *
 * =========================================================================
 * 2. BẢNG ƯỚC TÍNH THỜI GIAN HẠ GỤC (TTK) — KIẾM NETHERITE SHARPNESS V (MC 1.21.1):
 *    - Sát thương gốc: Thường = 11.0 (8 + 3) | Crit = 15.0 (8 × 1.5 + 3).
 *    - Tổng HP xuyên 2 phase: 1710 (P1 tới ngưỡng 5% = 90 HP) + 2520 (P2) = 4,230 HP.
 *    - Defense 30 (P1) và 42 (P2), toughness = 0 -> mọi đòn kiếm đều chạm trần `maxDamageReduction`.
 *    - Lưu ý hệ số bộ phận (`partDamageMultiplier`): đánh ĐẦU (x1.5) giảm 33% TTK; đánh ĐUÔI (x0.75) tăng 33% TTK!
 * -------------------------------------------------------------------------
 * maxDamageReduction | Thân x1.0 (Thường/Crit) | Số đòn Thân (Thường/Crit) | TTK 1.6/s Thân (Thường/Crit) | TTK Thực chiến 0.9/s (Crit Thân / Crit Đầu x1.5)
 * -------------------------------------------------------------------------
 * 0.60 (60% giảm)    | 4.40 / 6.00 HP          |   962 /   705 đòn         | 10.0 phút /  7.3 phút        | ~13.1 phút (Thân) /  ~8.7 phút (Đầu)
 * 0.75 (75% giảm)*   | 2.75 / 3.75 HP          | 1,539 / 1,128 đòn         | 16.0 phút / 11.8 phút        | ~20.9 phút (Thân) / ~13.9 phút (Đầu)
 * 0.85 (85% giảm)    | 1.65 / 2.25 HP          | 2,564 / 1,880 đòn         | 26.7 phút / 19.6 phút        | ~34.8 phút (Thân) / ~23.2 phút (Đầu)
 * -------------------------------------------------------------------------
 * (*) Mặc định 0.75 — khuyến khích người chơi nhắm điểm yếu ĐẦU (x1.5 sát thương) khi rắn ngẩng cổ hoặc sau đòn Strike Bite!
 * =========================================================================
 */
public final class JormungandrConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("abyssalrpg_jormungandr.json");

    private static JormungandrConfig INSTANCE = new JormungandrConfig();

    public int configVersion = 3;

    // --- Kích thước & mật độ đốt (visualScaleMultiplier = 1.30 tăng chi tiết sắc nét, đồng bộ path/hitbox/skill) ---
    public float scale = 1.0f;                      // 16 model px = 1 block ở visualScaleMultiplier = 1.0
    public float visualScaleMultiplier = 1.30f;     // Hệ số phóng tổng thể (mặc định 1.30, chỉnh được trong config)
    public float headScaleMultiplier = 1.35f;       // Hệ số phóng riêng cụm đầu (head + jaw/mắt/răng/sừng/mang cổ, mặc định 1.35)
    public int segmentCountPhase1 = 34;             // Phase 1: 34 đốt (Phase 2 x1.4 = 48 đốt) -> vòng cuộn tròn mượt
    public float segmentSpacing = 0.96f;            // Khoảng cách cơ sở trước visualScaleMultiplier (x1.30 ≈ 1.25 blk)
    public float segmentOverlapFactor = 1.34f;      // Chồng mép động trục Z (1.28–1.40) đảm bảo không hở đốt ở mọi góc
    public float baseBodyDiameter = 2.28f;          // Đường kính cơ sở gần đầu (x1.30 ≈ 2.96 block)
    public float rearUpHeight = 5.20f;              // Độ cao ngẩng cổ cơ sở (x1.30 ≈ 6.76 block)
    public float coilRadius = 4.8f;                 // Bán kính cuộn cơ sở khi đứng yên (x1.30 ≈ 6.24 block)
    public float lengthFactorPhase2 = 1.40f;        // Phase 2 dài x1.4 (34 -> 48 đốt)
    public float phase2ThicknessScale = 1.05f;      // Độ dày Phase 2 giữ nguyên hoặc tăng nhẹ <= 1.1
    public int proxyCount = 12;                     // Số proxy hitbox dọc thân (10-14 proxy)
    public boolean jormungandrFancyEffects = true;  // Bật/tắt VFX gắn locator bộ phận (nanh độc, mép mang cổ, bụi trườn)

    // --- Chỉ số Phase 1 ---
    public double maxHealth = 1800.0;               // Phase 2 = 1800 * 1.4 = 2520 HP (qua BossHealthHolder)
    public float defense = 30.0f;                   // Phase 2 = 30 * 1.4 = 42 (qua BossDefenseHolder)
    public float bossToughness = 0.0f;
    public float maxDamageReduction = 0.75f;        // Mặc định 0.75 (kẹp cứng <= 0.90)
    public float minDamagePerHit = 0.5f;
    public double knockbackResistance = 1.0;
    public double baseMoveSpeed = 0.34;             // Phase 2 = x1.4 -> 0.476
    public double detectionRadius = 112.0;
    public double bossBarRadius = 120.0;

    // --- Hệ số sát thương theo bộ phận (Multipart) & Tiếp xúc thân ---
    public float headDamageMultiplier = 1.50f;      // Điểm yếu đầu x1.5
    public float bodyDamageMultiplier = 1.00f;      // Thân giữa x1.0
    public float tailDamageMultiplier = 0.75f;      // 1/3 cuối đuôi x0.75
    public float contactDamage = 6.0f;              // Sát thương tiếp xúc thân (P2: 8.4), cooldown 20 tick/người
    public int contactCooldownTicks = 20;

    // --- Phase 2 "Cuồng bạo" ---
    public double phase2TriggerHpRatio = 0.05;      // <= 5% maxHP (90 HP) hoặc đòn chí mạng <= 1 HP
    public double phase2StatMultiplier = 1.40;
    public double phase2TurnSpeedMultiplier = 1.20; // Không nhân 1.4 để tránh giật góc
    public int phaseTransitionTicks = 110;          // 5.5 giây (5-6 giây) bất tử chuyển phase + mọc dài thân
    public int minTelegraphTicks = 10;              // Telegraph tối thiểu 0.5 giây

    // --- Độc (Venom) & Địa hình ---
    public int venomDurationTicks = 160;            // Poison II tối đa 8 giây (160 ticks)
    public int maxVenomPuddles = 10;                // Giới hạn số vũng độc đồng thời
    public boolean terrainDamageEnabled = false;    // Mặc định KHÔNG phá khối

    // --- Sát thương từng skill Phase 1 (Phase 2 tự nhân phase2StatMultiplier = 1.4x) ---
    public float strikeBiteDamage = 22.0f;          // Đòn nặng (P2: 30.8)
    public float venomSpitDamage = 14.0f;           // Cầu độc (P2: 19.6)
    public float venomPuddleTickDamage = 6.0f;      // Tick vũng độc (P2: 8.4)
    public float tailSlamDamage = 22.0f;            // Quật đuôi nặng (P2: 30.8)
    public float constrictDamage = 16.0f;           // Siết vòng AoE (P2: 22.4)
    public float hoodFlareDamage = 10.0f;           // Sóng xòe mang cổ (P2: 14.0)
    public float burrowEruptionDamage = 22.0f;      // Trồi đất đòn nặng (P2: 30.8)
    public float toxicNovaDamage = 16.0f;           // Phase 2 Toxic Nova AoE (x1.4 -> 22.4)
    public float rapidStrikeDamage = 16.0f;         // Phase 2 Rapid Strike mỗi nhát (x1.4 -> 22.4)
    public float venomRainDamage = 14.0f;           // Phase 2 Venom Rain mỗi cầu (x1.4 -> 19.6)
    public float ouroborosCenterStrikeDamage = 22.0f; // Phase 2 Ultimate chốt tâm (x1.4 -> 30.8)

    // --- Bán kính & Cooldown Phase 1 (ticks; Phase 2 chia 1.4 trừ Ultimate cố định 60s) ---
    public double constrictRadius = 8.0;
    public double ouroborosRadius = 14.0;
    public double ouroborosGapWidth = 6.5;          // Lỗ hổng tối thiểu >= 6 block
    public int strikeBiteCooldown = 100;
    public int venomSpitCooldown = 140;
    public int tailSlamCooldown = 130;
    public int constrictCooldown = 220;
    public int hoodFlareCooldown = 150;
    public int burrowCooldown = 240;
    public int toxicNovaCooldown = 190;
    public int rapidStrikeCooldown = 180;
    public int venomRainCooldown = 230;
    public double ultimateHpThreshold = 0.20;
    public int ultimateCooldownTicks = 1200;        // 60 giây cố định

    // --- Hồi máu ngoài giao tranh & Debug ---
    public double outOfCombatHealRadius = 128.0;
    public int outOfCombatHealDelayTicks = 600;
    public float outOfCombatHealPerSecond = 15.0f;
    public boolean debugLogs = false;

    public static JormungandrConfig get() {
        return INSTANCE;
    }

    /** Tỉ lệ hiển thị & hình học tổng hợp (`scale * visualScaleMultiplier`). */
    public float getEffectiveVisualScale() {
        return this.scale * this.visualScaleMultiplier;
    }

    /** Tỉ lệ hiển thị & hitbox riêng cho cụm đầu (`getEffectiveVisualScale() * headScaleMultiplier`). */
    public float getEffectiveHeadScale() {
        return getEffectiveVisualScale() * this.headScaleMultiplier;
    }

    public float getScaledSegmentSpacing() {
        return this.segmentSpacing * getEffectiveVisualScale();
    }

    public float getScaledBaseBodyDiameter() {
        return this.baseBodyDiameter * getEffectiveVisualScale();
    }

    public float getScaledRearUpHeight() {
        return this.rearUpHeight * getEffectiveVisualScale();
    }

    public float getScaledCoilRadius() {
        return this.coilRadius * getEffectiveVisualScale();
    }

    public double getScaledConstrictRadius() {
        return this.constrictRadius * this.visualScaleMultiplier;
    }

    public double getScaledOuroborosRadius() {
        return this.ouroborosRadius * this.visualScaleMultiplier;
    }

    public double getScaledOuroborosGapWidth() {
        return Math.max(6.5, this.ouroborosGapWidth * this.visualScaleMultiplier);
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                JsonElement el = JsonParser.parseReader(reader);
                if (el != null && el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    JormungandrConfig loaded = GSON.fromJson(obj, JormungandrConfig.class);
                    if (loaded != null) {
                        boolean migrated = false;
                        if (loaded.configVersion < 2) {
                            // Nâng cấp v1 -> v2: giữ nguyên toàn bộ chỉ số chiến đấu, áp thông số hình học & mật độ đốt mới
                            loaded.configVersion = 2;
                            loaded.visualScaleMultiplier = 1.30f;
                            loaded.segmentCountPhase1 = 34;
                            loaded.segmentSpacing = 0.96f;
                            loaded.segmentOverlapFactor = 1.34f;
                            loaded.coilRadius = 4.8f;
                            loaded.jormungandrFancyEffects = true;
                            migrated = true;
                        }
                        if (loaded.configVersion < 3) {
                            // Nâng cấp v2 -> v3: giữ nguyên toàn bộ chỉ số & thân cuộn, áp tỷ lệ phóng cụm đầu 1.35x
                            loaded.configVersion = 3;
                            loaded.headScaleMultiplier = 1.35f;
                            migrated = true;
                        }
                        loaded.sanitize();
                        INSTANCE = loaded;
                        if (migrated) {
                            save();
                        }
                        AbyssalRpgMod.LOGGER.info("Đã nạp cấu hình Jörmungandr v{} từ {}", loaded.configVersion, CONFIG_PATH);
                        return;
                    }
                }
            } catch (Exception e) {
                AbyssalRpgMod.LOGGER.warn("Lỗi đọc cấu hình Jörmungandr, dùng mặc định: {}", e.getMessage());
            }
        }
        INSTANCE = new JormungandrConfig();
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
            AbyssalRpgMod.LOGGER.warn("Không thể lưu cấu hình Jörmungandr: {}", e.getMessage());
        }
    }

    private void sanitize() {
        this.scale = Math.max(0.5f, Math.min(3.0f, this.scale));
        this.visualScaleMultiplier = Math.max(0.5f, Math.min(2.5f, this.visualScaleMultiplier));
        this.headScaleMultiplier = Math.max(1.0f, Math.min(1.75f, this.headScaleMultiplier));
        this.segmentCountPhase1 = Math.max(16, Math.min(38, this.segmentCountPhase1));
        this.segmentSpacing = Math.max(0.55f, Math.min(2.2f, this.segmentSpacing));
        this.segmentOverlapFactor = Math.max(1.15f, Math.min(1.60f, this.segmentOverlapFactor));
        this.baseBodyDiameter = Math.max(1.2f, Math.min(4.0f, this.baseBodyDiameter));
        this.rearUpHeight = Math.max(2.5f, Math.min(10.0f, this.rearUpHeight));
        this.coilRadius = Math.max(3.0f, Math.min(10.0f, this.coilRadius));
        this.lengthFactorPhase2 = Math.max(1.0f, Math.min(1.50f, this.lengthFactorPhase2));
        this.phase2ThicknessScale = Math.max(1.0f, Math.min(1.10f, this.phase2ThicknessScale));
        this.proxyCount = Math.max(8, Math.min(14, this.proxyCount));
        this.maxHealth = Math.max(400.0, Math.min(50000.0, this.maxHealth));
        this.defense = Math.max(0.0f, Math.min(200.0f, this.defense));
        this.bossToughness = Math.max(0.0f, Math.min(100.0f, this.bossToughness));
        this.maxDamageReduction = Math.max(0.10f, Math.min(0.90f, this.maxDamageReduction));
        this.minDamagePerHit = Math.max(0.1f, Math.min(10.0f, this.minDamagePerHit));
        this.knockbackResistance = Math.max(0.0, Math.min(1.0, this.knockbackResistance));
        this.baseMoveSpeed = Math.max(0.16, Math.min(0.75, this.baseMoveSpeed));
        this.detectionRadius = Math.max(48.0, Math.min(192.0, this.detectionRadius));
        this.bossBarRadius = Math.max(64.0, Math.min(220.0, this.bossBarRadius));
        this.headDamageMultiplier = Math.max(1.0f, Math.min(2.5f, this.headDamageMultiplier));
        this.bodyDamageMultiplier = Math.max(0.5f, Math.min(2.0f, this.bodyDamageMultiplier));
        this.tailDamageMultiplier = Math.max(0.25f, Math.min(1.5f, this.tailDamageMultiplier));
        this.contactDamage = Math.max(0.0f, Math.min(20.0f, this.contactDamage));
        this.contactCooldownTicks = Math.max(10, Math.min(60, this.contactCooldownTicks));
        this.phase2TriggerHpRatio = Math.max(0.005, Math.min(0.25, this.phase2TriggerHpRatio));
        this.phase2StatMultiplier = Math.max(1.0, Math.min(2.5, this.phase2StatMultiplier));
        this.phase2TurnSpeedMultiplier = Math.max(1.0, Math.min(1.8, this.phase2TurnSpeedMultiplier));
        this.phaseTransitionTicks = Math.max(100, Math.min(125, this.phaseTransitionTicks));
        this.minTelegraphTicks = Math.max(10, Math.min(40, this.minTelegraphTicks));
        this.venomDurationTicks = Math.max(40, Math.min(200, this.venomDurationTicks));
        this.maxVenomPuddles = Math.max(3, Math.min(16, this.maxVenomPuddles));
        this.constrictRadius = Math.max(5.0, Math.min(14.0, this.constrictRadius));
        this.ouroborosRadius = Math.max(10.0, Math.min(22.0, this.ouroborosRadius));
        this.ouroborosGapWidth = Math.max(6.0, Math.min(12.0, this.ouroborosGapWidth));
        this.ultimateHpThreshold = Math.max(0.05, Math.min(0.40, this.ultimateHpThreshold));
        this.ultimateCooldownTicks = Math.max(400, Math.min(2400, this.ultimateCooldownTicks));
    }
}

