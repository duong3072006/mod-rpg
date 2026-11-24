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
 * Cấu hình JSON cho hệ thống vũ khí & hiệu ứng Dual Knife (`config/abyssalrpg_weapons.json`).
 * - Version 5: giữ nguyên `30.0` sát thương, `2.5` tốc đánh (`8` tick/đòn), `7` tick i-frame,
 *   bộ 4 Enchant mới, `dashDamageMultiplier = 1.5`, nerf tầm lướt `dashDistance = 15.0` block,
 *   và bổ sung `knifeRenderScale = 0.72` (tỷ lệ dao găm sát thủ gọn sắc, tùy chỉnh nhanh ±15%).
 */
public final class WeaponsConfig {
    public static final int CURRENT_CONFIG_VERSION = 5;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("abyssalrpg_weapons.json");

    private static WeaponsConfig INSTANCE = new WeaponsConfig();

    public int configVersion = CURRENT_CONFIG_VERSION;

    // --- Chỉ số gốc Dual Blade ---
    /** Tổng sát thương hiển thị & gây ra cơ sở (player base 1.0 + modifier +29.0 = 30.0). */
    public double dualBladeDamage = 30.0;
    /** Tốc độ đánh cơ sở khi tay phụ trống (player base 4.0 + modifier -1.5 = 2.5 hit/s = 8.0 ticks/đòn). */
    public double dualBladeAttackSpeed = 2.5;
    /** Độ bền chuẩn kiếm Kim Cương (1561). */
    public int dualBladeDurability = 1561;
    /** Bắt buộc tay phụ trống để đạt đầy đủ sức mạnh song đao (nếu cầm đồ tay phụ, tốc đánh tụt về offhandOccupiedAttackSpeed). */
    public boolean requireEmptyOffhand = true;
    /** Tốc độ đánh bị phạt khi tay phụ đang cầm vật phẩm khác và requireEmptyOffhand = true. */
    public double offhandOccupiedAttackSpeed = 1.6;
    /**
     * Số tick miễn nhiễm sát thương (hurt resistance) cho đòn chém trực tiếp đầy lực của Dual Blade.
     * Mặc định 7 ticks (invulnerableTime = 17 -> khớp chính xác nhịp 8 ticks/đòn ở speed 2.5, không nuốt hit).
     */
    public int dualBladeHurtResistanceTicks = 7;
    /** Cho phép giữ chuột trái (Hold-to-Slash) để chém liên hoàn chuẩn theo nhịp tốc độ đánh. */
    public boolean enableHoldToSlash = true;
    /** Bật/tắt VFX vòng cung lưỡi chém gọn nhẹ phía Client. */
    public boolean enableSlashVfx = true;
    /** Bật/tắt dải lụa thư pháp 3D Catmull-Rom Đỏ–Đen & Vàng–Đen (`DualBladeTrailRenderer`). */
    public boolean enableSlashRibbon = true;
    /** Bật/tắt độ nghiêng camera nhẹ (±3°–4° roll) ở góc nhìn thứ nhất khi chém Dual Blade. */
    public boolean firstPersonCameraTilt = true;
    /** Bật/tắt hiệu ứng co/giãn FOV nhẹ khi nén đà & phóng chiêu Lướt Chém 10 Block. */
    public boolean dashFovEffect = true;
    /** Tỷ lệ hiển thị 3D của Dual Knife trên tay (mặc định `0.72` = gọn sắc chuẩn song dao găm; tùy chỉnh nhanh ±15% từ 0.50..1.15). */
    public float knifeRenderScale = 0.72f;
    /** Bật/tắt hạt giọt máu nhẹ (1-2 hạt mỗi 9 ticks) trên mục tiêu đang dính Chảy Máu. */
    public boolean enableBleedParticles = true;

    // --- Combo 5 nhịp rõ nét: Nhịp 4 Cyclone 360° AOE & Root-Motion Lunge an toàn ---
    public double cycloneRadius = 3.5;
    public int cycloneMaxTargets = 6;
    public boolean enableComboLunge = true;
    public double comboLungeDistance = 0.18;
    public double sprintLungeSpeed = 0.22;

    // --- Chiêu Chuột Phải: Lướt Chém 15 Block — 10 Nhát Tức Thì (`Netherite-Diamond Flash Dash`) ---
    /** Quãng đường phóng lướt (mặc định 15.0 block trong 6 tick, sub-step 0.5 block × 30 bước, dừng gọn trước tường rắn, xuyên mob/boss). */
    public double dashDistance = 15.0;
    /** Thời gian hồi chiêu (mặc định 200 ticks = 10 giây, hiện cooldown chuẩn trên hotbar). */
    public int dashCooldownTicks = 200;
    /** Độ bền tiêu hao mỗi lần kích hoạt chiêu Lướt Chém (mặc định 5). */
    public int dashDurabilityCost = 5;
    /** Số nhát chém tức thì áp lên mỗi mục tiêu đã lướt qua (mặc định 10 nhát, 0 bị nuốt i-frame). */
    public int dashHitCount = 10;
    /** Bội số sát thương cơ sở của chiêu Lướt Chém (mặc định 1.5x -> 45.0 sát thương thô tại base 30). */
    public double dashDamageMultiplier = 1.5;
    /**
     * Cách áp dụng `dashDamageMultiplier`:
     * - `true` (mặc định): MỖI nhát trong 10 nhát gây `1.5 × dualBladeDamage` (`45.0` sát thương thô/hit).
     * - `false`: Tổng cả 10 nhát cộng lại bằng `1.5 × dualBladeDamage` (mỗi nhát = `1.5 × base / 10`).
     */
    public boolean dashMultiplierPerHit = true;
    /** Tỉ lệ xuyên giáp/phòng thủ mặc định của chiêu Lướt Chém (+10%, cộng dồn với Enchant Armor Pierce!). */
    public float dashArmorPierceBonus = 0.10f;
    /** Trần tổng lượng HP hồi được từ Lifesteal trong CẢ 1 lần Lướt Chém 10 Nhát (tránh hồi đầy 100% bất công). */
    public float maxDashLifestealTotal = 16.0f;

    // --- Hiệu ứng Bleeding (Chảy Máu mặc định của Dual Blade) ---
    public int bleedDurationTicks = 80;
    public int bleedTickInterval = 20;
    public float bleedDamagePerStack = 2.0f;
    public int bleedBaseMaxStacks = 5;

    // --- Bộ 4 Enchantment mới chuyên biệt cho Dual Blade (Lv I = 5% chuẩn) ---
    public float lifestealPctLv1 = 0.05f;
    public float lifestealPctLv2 = 0.10f;
    public float lifestealPctLv3 = 0.15f;
    public float maxLifestealPerHit = 15.0f;

    public float armorPiercePctLv1 = 0.05f;
    public float armorPiercePctLv2 = 0.12f;
    public float armorPiercePctLv3 = 0.20f;

    public float criticalChanceLv1 = 0.05f;
    public float criticalChanceLv2 = 0.10f;
    public float criticalChanceLv3 = 0.15f;
    public float criticalDamageMultiplier = 1.50f;

    public float deepWoundsBonusLv1 = 0.05f;
    public float deepWoundsBonusLv2 = 0.10f;
    public float deepWoundsBonusLv3 = 0.15f;

    public static WeaponsConfig get() {
        return INSTANCE;
    }

    public float getLifestealPercent(int level) {
        if (level <= 0) return 0.0f;
        return switch (level) {
            case 1 -> this.lifestealPctLv1;
            case 2 -> this.lifestealPctLv2;
            default -> this.lifestealPctLv3;
        };
    }

    public float getArmorPiercePercent(int level) {
        if (level <= 0) return 0.0f;
        return switch (level) {
            case 1 -> this.armorPiercePctLv1;
            case 2 -> this.armorPiercePctLv2;
            default -> this.armorPiercePctLv3;
        };
    }

    public float getCriticalChance(int level) {
        if (level <= 0) return 0.0f;
        return switch (level) {
            case 1 -> this.criticalChanceLv1;
            case 2 -> this.criticalChanceLv2;
            default -> this.criticalChanceLv3;
        };
    }

    public float getDeepWoundsBonus(int level) {
        if (level <= 0) return 0.0f;
        return switch (level) {
            case 1 -> this.deepWoundsBonusLv1;
            case 2 -> this.deepWoundsBonusLv2;
            default -> this.deepWoundsBonusLv3;
        };
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                JsonElement el = JsonParser.parseReader(reader);
                if (el != null && el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    WeaponsConfig loaded = GSON.fromJson(obj, WeaponsConfig.class);
                    if (loaded != null) {
                        boolean migrated = false;
                        if (loaded.configVersion < 2) {
                            loaded.dualBladeDamage = 30.0;
                            loaded.dualBladeAttackSpeed = 2.5;
                            loaded.dualBladeHurtResistanceTicks = 7;
                            migrated = true;
                        }
                        if (loaded.configVersion < 5) {
                            // Nâng cấp tự động sang chuẩn v5: Dual Knife (scale 0.72), dashDistance 15.0 block, dashDamageMultiplier 1.5
                            loaded.dashDamageMultiplier = 1.5;
                            loaded.dashDistance = 15.0;
                            loaded.knifeRenderScale = 0.72f;
                            loaded.configVersion = CURRENT_CONFIG_VERSION;
                            migrated = true;
                        }
                        loaded.sanitize();
                        INSTANCE = loaded;
                        if (migrated) {
                            save();
                            AbyssalRpgMod.LOGGER.info("Đã nâng cấp WeaponsConfig lên v{} (Dual Knife · scale={} · Flash Dash {}b x{}) tại {}",
                                    loaded.configVersion, loaded.knifeRenderScale, loaded.dashDistance, loaded.dashDamageMultiplier, CONFIG_PATH);
                        } else {
                            AbyssalRpgMod.LOGGER.info("Đã nạp cấu hình WeaponsConfig v{} từ {}", loaded.configVersion, CONFIG_PATH);
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                AbyssalRpgMod.LOGGER.warn("Lỗi đọc cấu hình WeaponsConfig, dùng giá trị mặc định: {}", e.getMessage());
            }
        }
        INSTANCE = new WeaponsConfig();
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
            AbyssalRpgMod.LOGGER.warn("Không thể lưu cấu hình WeaponsConfig: {}", e.getMessage());
        }
    }

    private void sanitize() {
        this.dualBladeDamage = Math.max(2.0, Math.min(500.0, this.dualBladeDamage));
        this.dualBladeAttackSpeed = Math.max(0.5, Math.min(8.0, this.dualBladeAttackSpeed));
        this.dualBladeDurability = Math.max(64, Math.min(100000, this.dualBladeDurability));
        this.offhandOccupiedAttackSpeed = Math.max(0.5, Math.min(this.dualBladeAttackSpeed, this.offhandOccupiedAttackSpeed));
        this.dualBladeHurtResistanceTicks = Math.max(3, Math.min(10, this.dualBladeHurtResistanceTicks));
        this.knifeRenderScale = Math.max(0.50f, Math.min(1.15f, this.knifeRenderScale));
        this.bleedDurationTicks = Math.max(20, Math.min(600, this.bleedDurationTicks));
        this.bleedTickInterval = Math.max(5, Math.min(60, this.bleedTickInterval));
        this.bleedDamagePerStack = Math.max(0.5f, Math.min(50.0f, this.bleedDamagePerStack));
        this.bleedBaseMaxStacks = Math.max(1, Math.min(20, this.bleedBaseMaxStacks));

        this.dashDistance = Math.max(3.0, Math.min(32.0, this.dashDistance));
        this.dashCooldownTicks = Math.max(20, Math.min(1200, this.dashCooldownTicks));
        this.dashDurabilityCost = Math.max(0, Math.min(50, this.dashDurabilityCost));
        this.dashHitCount = Math.max(1, Math.min(20, this.dashHitCount));
        this.dashDamageMultiplier = Math.max(0.5, Math.min(10.0, this.dashDamageMultiplier));
        this.dashArmorPierceBonus = Math.max(0.0f, Math.min(0.60f, this.dashArmorPierceBonus));
        this.maxDashLifestealTotal = Math.max(1.0f, Math.min(100.0f, this.maxDashLifestealTotal));

        this.lifestealPctLv1 = Math.max(0.01f, Math.min(0.50f, this.lifestealPctLv1));
        this.lifestealPctLv2 = Math.max(0.01f, Math.min(0.50f, this.lifestealPctLv2));
        this.lifestealPctLv3 = Math.max(0.01f, Math.min(0.50f, this.lifestealPctLv3));
        this.maxLifestealPerHit = Math.max(1.0f, Math.min(200.0f, this.maxLifestealPerHit));

        this.armorPiercePctLv1 = Math.max(0.01f, Math.min(0.80f, this.armorPiercePctLv1));
        this.armorPiercePctLv2 = Math.max(0.01f, Math.min(0.80f, this.armorPiercePctLv2));
        this.armorPiercePctLv3 = Math.max(0.01f, Math.min(0.80f, this.armorPiercePctLv3));

        this.criticalChanceLv1 = Math.max(0.01f, Math.min(1.00f, this.criticalChanceLv1));
        this.criticalChanceLv2 = Math.max(0.01f, Math.min(1.00f, this.criticalChanceLv2));
        this.criticalChanceLv3 = Math.max(0.01f, Math.min(1.00f, this.criticalChanceLv3));
        this.criticalDamageMultiplier = Math.max(1.10f, Math.min(4.00f, this.criticalDamageMultiplier));

        this.deepWoundsBonusLv1 = Math.max(0.01f, Math.min(1.00f, this.deepWoundsBonusLv1));
        this.deepWoundsBonusLv2 = Math.max(0.01f, Math.min(1.00f, this.deepWoundsBonusLv2));
        this.deepWoundsBonusLv3 = Math.max(0.01f, Math.min(1.00f, this.deepWoundsBonusLv3));

        this.cycloneRadius = Math.max(1.5, Math.min(8.0, this.cycloneRadius));
        this.cycloneMaxTargets = Math.max(1, Math.min(16, this.cycloneMaxTargets));
        this.comboLungeDistance = Math.max(0.0, Math.min(0.28, this.comboLungeDistance));
        this.sprintLungeSpeed = Math.max(0.0, Math.min(0.35, this.sprintLungeSpeed));
    }
}
