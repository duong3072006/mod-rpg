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
 * Cấu hình JSON cho Boss Cerberus (lưu tại config/abyssalrpg_cerberus.json).
 *
 * =========================================================================
 * BẢNG KIỂM TRA SÁT THƯƠNG PHASE 1 & PHASE 2 THEO CÔNG THỨC GIÁP VANILLA (MC 1.21.1):
 *   effectiveDefense = min(20, max(armor / 5, armor - (4 * dmg) / (toughness + 8)))
 *   damageTaken      = dmg * (1 - effectiveDefense / 25)
 *   (Người chơi máu 20 HP, độ khó Bình thường, KHÔNG enchant, DamageSource mobAttack)
 * -------------------------------------------------------------------------
 * Bộ giáp (Armor / Toughness)  | Đòn nặng 30 | AoE 20  | Thường 15 | Đòn nặng P2 (45) | AoE P2 (30)
 * -------------------------------------------------------------------------
 * Không giáp      ( 0 /  0)    | 30.00 (CHẾT)| 20.00(X)| 15.00     | 45.00 (CHẾT)     | 30.00 (CHẾT)
 * Giáp Da full    ( 7 /  0)    | 28.32 (CHẾT)| 18.88   | 14.16     | 42.48 (CHẾT)     | 28.32 (CHẾT)
 * Giáp Sắt full   (15 /  0)    | 26.40 (CHẾT)| 16.00   | 10.50     | 39.60 (CHẾT)     | 26.40 (CHẾT)
 * Giáp Kim Cương  (20 /  8)    | 15.00 (SỐNG)|  8.00   |  5.25     | 29.25 (CHẾT*)    | 15.00 (SỐNG)
 * Giáp Netherite  (20 / 12)    | 13.20 (SỐNG)|  7.20   |  4.80     | 25.20 (CHẾT*)    | 13.20 (SỐNG)
 * -------------------------------------------------------------------------
 * (*) Kết quả đạt đúng yêu cầu thiết kế:
 * - Giáp Sắt đủ bộ không enchant chịu 26.40 >= 20 HP từ đòn nặng Phase 1 (30) -> CHẾT NGAY 1 ĐÒN.
 * - Giáp Kim Cương (15.00) và Netherite (13.20) sống sót qua 1 đòn nặng Phase 1.
 * - Phase 2 đòn nặng x1.5 = 45 đủ hạ người chơi không enchant / không đỡ khiên -> buộc né hoặc dùng giáp RPG/Protection.
 * =========================================================================
 */
public final class CerberusConfig {
    public enum SplitHealthMode {
        /** Mặc định: chia đều tổng máu Phase 2 (3000 / 3 = 1000 HP mỗi cá thể, hồi đầy). */
        SPLIT_EQUAL,
        /** Rất khó: mỗi cá thể có đủ 3000 HP riêng (dùng BossHealthHolder vượt trần 1024; tổng 9000 HP). */
        FULL_EACH,
        /** Một thanh máu chung 3000 HP: sát thương vào bất kỳ cá thể nào trừ chung vào quỹ, cạn quỹ cả 3 cùng chết. */
        SHARED_POOL
    }

    public enum VfxQuality {
        /** Tắt toàn bộ hạt VFX client (chỉ giữ model glowmask fullbright & chóp lửa đuôi 3 cánh). */
        OFF,
        /** Mặc định gọn sắc, siêu nhẹ FPS/MSPT khi cả 3 cá thể Phase 2 cùng giao chiến. */
        MINIMAL,
        /** Cân bằng vừa mắt. */
        NORMAL,
        /** Đầy đủ cho máy cấu hình mạnh. */
        HIGH
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("abyssalrpg_cerberus.json");

    private static CerberusConfig INSTANCE = new CerberusConfig();

    /** Phiên bản schema config (v4: model v4 3 đầu hướng thẳng chuẩn Blockbench, scale=1.49, visualScaleMultiplier=1.10, VFX Client payload). */
    public int configVersion = 4;

    // --- Kích thước & Model ---
    /** Hệ số scale cơ sở Phase 1 = targetHeight (6.5 blk) / nativeHeight (4.3625 blk) = 1.49f. */
    public float scale = 1.49f;
    /** Hệ số nhân phóng to nhẹ toàn bộ Cerberus (mặc định 1.10 -> cao 7.15 blk, hitbox 8.80 x 7.15; Phase 2 x0.75 -> 6.60 x 5.36). */
    public float visualScaleMultiplier = 1.10f;
    /** Tỉ lệ kích thước dạng 1 đầu Phase 2 so với dạng 3 đầu (mặc định 0.75f, chỉnh được lên 1.0f). */
    public float splitScaleFactor = 0.75f;
    /** true: dùng cerberus_single.geo.json cho Phase 2; false: dùng chung cerberus.geo.json và ẩn bone head_1/head_3/neck_1/neck_3. */
    public boolean useSeparateSingleGeo = true;
    /** Tốc độ phát animation đòn đánh nhanh ở Phase 2 (đồng bộ 1:1 giữa Server timeline & GeckoLib Client). */
    public float phase2AttackAnimSpeed = 1.15f;

    // --- Hiệu ứng nâng cao & HUD (tắt/bật độc lập, 100% Client-side VFX) ---
    /** Bật/tắt toàn bộ hiệu ứng nâng cao (bụi bước chân, mảnh đá slam/pounce, nhịp thở glowmask, tàn lửa client). */
    public boolean cerberusFancyEffects = true;
    /** Chất lượng hiệu ứng hạt Client (mặc định MINIMAL để mượt tuyệt đối khi 3 cá thể Phase 2 cùng đánh). */
    public VfxQuality vfxQuality = VfxQuality.MINIMAL;
    /** Hệ số nhân số hạt Client VFX cơ sở (mặc định 0.25f). */
    public float particleMultiplier = 0.25f;
    /** Rung màn hình rất nhẹ cho người chơi gần mỗi bước chân khi boss đi bộ. */
    public boolean footstepShakeEnabled = true;
    /** Cường độ rung bước chân (mặc định rất nhẹ 0.22f, giới hạn <= 0.6f). */
    public float footstepShakeIntensity = 0.22f;
    /** Tùy chọn vẽ thêm khung texture thanh máu riêng cho Cerberus trên client (mặc định TẮT để tránh xung đột HUD/resource pack). */
    public boolean cerberusCustomBossBarFrame = false;

    // --- Chỉ số cơ bản Phase 1 (dạng 3 đầu) ---
    public double maxHealth = 2000.0;               // Phase 2 tổng = 2000 * 1.5 = 3000 HP (qua BossHealthHolder)
    public double armor = 18.0;                     // Phase 2 (x1.5) = 27.0 (dưới trần vanilla 30)
    public double armorToughness = 6.0;
    public double knockbackResistance = 1.0;
    public double baseMoveSpeed = 0.34;
    public double stepHeight = 2.5;
    public double detectionRadius = 96.0;
    public double bossBarRadius = 104.0;

    // --- Phase 2 "Cuồng bạo": tách 3 cá thể 1 đầu ---
    public double phase2TriggerHpRatio = 0.05;      // <= 5% maxHP (100 HP) hoặc đòn chí mạng <= 1 HP
    public double phase2StatMultiplier = 1.50;      // Giáp 18->27, Sát thương x1.5, Tốc độ x1.5, Cooldown /1.5
    public double phase2TurnSpeedMultiplier = 1.25; // Tốc độ xoay tối đa x1.25
    public int phaseTransitionTicks = 100;          // 5.0 giây (4-6 giây) bất tử chuyển phase + bay 3 cầu lửa + đáp đất
    public int minTelegraphTicks = 8;               // Telegraph tối thiểu 0.4 giây (8 ticks)
    public SplitHealthMode splitHealthMode = SplitHealthMode.SPLIT_EQUAL;
    public int maxSimultaneousHeavyAttackers = 1;   // Attack token: tối đa 1 cá thể tung đòn nặng cùng lúc
    public int maxActiveHellhounds = 6;             // Tổng Hellhound tối đa toàn nhóm cùng lúc
    public float splitLandingDamage = 4.0f;         // Sát thương nổ nhẹ khi cầu lửa chạm đất (0 = chỉ hiệu ứng)
    public boolean lootOnlyOnFinalMemberDeath = true; // Chỉ cá thể cuối cùng của nhóm rơi loot & XP

    // --- Sát thương Phase 1 (Phase 2 tự nhân phase2StatMultiplier = 1.5x) ---
    public float meleeDamage = 15.0f;               // Đòn thường: 12-18 (P2: 22.5)
    public float tripleBiteLightDamage = 16.0f;     // 2 cú cắn mở đầu Triple Bite (P2: 24)
    public float tripleBiteHeavyDamage = 30.0f;     // Cú cắn chốt đòn nặng (P2: 45)
    public float hellfireBreathTickDamage = 14.0f;  // Phun lửa tick hình nón (P2: 21)
    public float pounceImpactDamage = 30.0f;        // Đòn vồ trực tiếp nặng (P2: 45)
    public float pounceWaveDamage = 20.0f;          // Sóng nhỏ diện rộng khi đáp (P2: 30)
    public float groundSlamDamage = 30.0f;          // Đập chân hình quạt nặng (P2: 45)
    public float wrathChargeDamage = 30.0f;         // ALPHA_HEAD lao húc nặng (P2: 45)
    public float flameRainOrbDamage = 16.0f;        // FIRE_HEAD mưa cầu lửa (P2: 24)
    public float hellfireNovaDamage = 20.0f;        // FIRE_HEAD sóng lửa nhảy né (P2: 30)
    public float shadowDashDamage = 20.0f;          // SHADOW_HEAD lao đường thẳng (P2: 30)
    public float shadowStepDamage = 18.0f;          // SHADOW_HEAD ám sát sau lưng (P2: 27)
    public float phantomClawDamage = 13.0f;         // SHADOW_HEAD 3 nhát cào nhanh (P2: 19.5)
    public float packHuntStrikeDamage = 28.0f;      // Combo 3 hướng Pack Hunt (P2: 42)

    // --- Cooldown Phase 1 (ticks, Phase 2 tự chia cho 1.5) ---
    public int tripleBiteCooldown = 100;
    public int hellfireBreathCooldown = 150;
    public int pounceCooldown = 140;
    public int groundSlamCooldown = 130;
    public int howlCooldown = 340;
    public int wrathChargeCooldown = 150;
    public int flameRainCooldown = 180;
    public int hellfireNovaCooldown = 190;
    public int shadowDashCooldown = 130;
    public int shadowStepCooldown = 160;
    public int phantomClawCooldown = 90;
    public int packHuntCooldown = 800;              // 40 giây cố định cho combo nhóm 3 cá thể

    // --- Debug xoay đầu & trạng thái nhóm (/abyss cerberus debug) ---
    public boolean debugRotationLogs = false;

    public static CerberusConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                JsonElement rootEl = JsonParser.parseReader(reader);
                if (rootEl != null && rootEl.isJsonObject()) {
                    JsonObject obj = rootEl.getAsJsonObject();
                    // Phải đọc khóa configVersion trực tiếp từ JsonObject vì Gson không ghi đè field default khi file cũ thiếu khóa!
                    int rawVersion = obj.has("configVersion") ? obj.get("configVersion").getAsInt() : 1;
                    CerberusConfig loaded = GSON.fromJson(obj, CerberusConfig.class);
                    if (loaded != null) {
                        boolean needsSave = rawVersion < 4
                                || !obj.has("visualScaleMultiplier")
                                || !obj.has("cerberusFancyEffects")
                                || !obj.has("vfxQuality")
                                || !obj.has("particleMultiplier")
                                || !obj.has("phase2AttackAnimSpeed")
                                || !obj.has("footstepShakeEnabled")
                                || !obj.has("footstepShakeIntensity")
                                || !obj.has("cerberusCustomBossBarFrame");
                        if (rawVersion < 4) {
                            AbyssalRpgMod.LOGGER.info(
                                    "Nâng cấp cấu hình Cerberus v{} -> v4 (model v4: scale=1.49, visualScaleMultiplier=1.10, VFX Client payload; giữ nguyên 100% chỉ số chiến đấu).",
                                    rawVersion);
                            loaded.scale = 1.49f;
                            if (!obj.has("visualScaleMultiplier") || Math.abs(loaded.visualScaleMultiplier - 1.25f) < 0.01f) {
                                loaded.visualScaleMultiplier = 1.10f;
                            }
                        }
                        if (!obj.has("cerberusFancyEffects")) loaded.cerberusFancyEffects = true;
                        if (!obj.has("vfxQuality")) loaded.vfxQuality = VfxQuality.MINIMAL;
                        if (!obj.has("particleMultiplier")) loaded.particleMultiplier = 0.25f;
                        if (!obj.has("phase2AttackAnimSpeed")) loaded.phase2AttackAnimSpeed = 1.15f;
                        if (!obj.has("footstepShakeEnabled")) loaded.footstepShakeEnabled = true;
                        if (!obj.has("footstepShakeIntensity")) loaded.footstepShakeIntensity = 0.22f;
                        if (!obj.has("cerberusCustomBossBarFrame")) loaded.cerberusCustomBossBarFrame = false;
                        loaded.configVersion = 4;
                        loaded.sanitize();
                        INSTANCE = loaded;
                        if (needsSave) {
                            save();
                        }
                        AbyssalRpgMod.LOGGER.info("Đã nạp cấu hình Cerberus v{} từ {}", loaded.configVersion, CONFIG_PATH);
                        return;
                    }
                }
            } catch (Exception e) {
                AbyssalRpgMod.LOGGER.warn("Lỗi đọc cấu hình Cerberus, dùng giá trị mặc định: {}", e.getMessage());
            }
        }
        INSTANCE = new CerberusConfig();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            AbyssalRpgMod.LOGGER.warn("Không thể lưu cấu hình Cerberus: {}", e.getMessage());
        }
    }

    public float getEffectiveParticleMultiplier() {
        if (!cerberusFancyEffects || vfxQuality == VfxQuality.OFF) return 0.0f;
        float base = Math.max(0.0f, Math.min(1.5f, particleMultiplier));
        return switch (vfxQuality) {
            case OFF -> 0.0f;
            case MINIMAL -> base;
            case NORMAL -> Math.min(1.0f, base * 2.2f);
            case HIGH -> Math.min(1.5f, base * 3.8f);
        };
    }

    private void sanitize() {
        this.scale = Math.max(0.5f, Math.min(6.0f, this.scale));
        this.visualScaleMultiplier = Math.max(0.75f, Math.min(2.0f, this.visualScaleMultiplier));
        this.splitScaleFactor = Math.max(0.4f, Math.min(1.25f, this.splitScaleFactor));
        this.phase2AttackAnimSpeed = Math.max(1.0f, Math.min(1.40f, this.phase2AttackAnimSpeed));
        if (this.vfxQuality == null) {
            this.vfxQuality = VfxQuality.MINIMAL;
        }
        this.particleMultiplier = Math.max(0.0f, Math.min(1.5f, this.particleMultiplier));
        this.footstepShakeIntensity = Math.max(0.0f, Math.min(0.60f, this.footstepShakeIntensity));
        this.maxHealth = Math.max(400.0, Math.min(50000.0, this.maxHealth));
        this.armor = Math.max(0.0, Math.min(30.0, this.armor));
        this.armorToughness = Math.max(0.0, Math.min(20.0, this.armorToughness));
        this.knockbackResistance = Math.max(0.0, Math.min(1.0, this.knockbackResistance));
        this.stepHeight = Math.max(0.6, Math.min(6.0, this.stepHeight));
        this.detectionRadius = Math.max(32.0, Math.min(192.0, this.detectionRadius));
        this.bossBarRadius = Math.max(64.0, Math.min(256.0, this.bossBarRadius));
        this.phase2TriggerHpRatio = Math.max(0.005, Math.min(0.25, this.phase2TriggerHpRatio));
        this.phase2StatMultiplier = Math.max(1.0, Math.min(3.0, this.phase2StatMultiplier));
        this.phase2TurnSpeedMultiplier = Math.max(1.0, Math.min(2.0, this.phase2TurnSpeedMultiplier));
        this.phaseTransitionTicks = Math.max(80, Math.min(140, this.phaseTransitionTicks));
        this.minTelegraphTicks = Math.max(8, Math.min(40, this.minTelegraphTicks));
        if (this.splitHealthMode == null) {
            this.splitHealthMode = SplitHealthMode.SPLIT_EQUAL;
        }
        this.maxSimultaneousHeavyAttackers = Math.max(1, Math.min(3, this.maxSimultaneousHeavyAttackers));
        this.maxActiveHellhounds = Math.max(1, Math.min(12, this.maxActiveHellhounds));
        this.splitLandingDamage = Math.max(0.0f, Math.min(20.0f, this.splitLandingDamage));
        this.packHuntCooldown = Math.max(200, Math.min(2400, this.packHuntCooldown));
    }
}

