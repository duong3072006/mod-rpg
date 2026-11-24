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
 * Cấu hình JSON cho các bộ Giáp Boss Endgame (`config/abyssalrpg_armor.json`):
 * 1. **Nightmare Knight Armor**: Defense 3/8/6/3 (= 20), Toughness 3.0 (= 12.0), KB Res 0.10 (= 40%),
 *    Độ bền 37x (407/592/555/481); Full Set 4/4: Kháng Lửa I, Kháng Cự II, +4.0 Máu Tối Đa.
 * 2. **Cinder Dragon Armor**: Defense 8/20/15/7 (= 50, ×2.5 Netherite!), Toughness 7.5 (= 30.0), KB Res 0.10 (= 40%),
 *    Độ bền 37x (407/592/555/481); Full Set 4/4: Kháng Lửa, Đề Kháng I, Sức Mạnh II, Tốc Độ I, Nhảy Cao I, +16.0 Máu Tối Đa (+8 tim).
 */
public final class ArmorConfig {
    public static final int CURRENT_CONFIG_VERSION = 5;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("abyssalrpg_armor.json");

    private static ArmorConfig INSTANCE = new ArmorConfig();

    public int configVersion = CURRENT_CONFIG_VERSION;

    // --- Chỉ số Nightmare Knight Armor Material (chuẩn Netherite 37x) ---
    public int helmetDefense = 3;
    public int chestplateDefense = 8;
    public int leggingsDefense = 6;
    public int bootsDefense = 3;
    public float toughness = 3.0f;
    public float knockbackResistance = 0.10f;
    public int durabilityMultiplier = 37;
    public int enchantmentValue = 18;

    // --- Full Set Bonus 4/4: Nightmare Set ---
    public boolean enableSetBonus = true;
    /** Cấp Kháng Lửa I (MobEffects.FIRE_RESISTANCE, amplifier 0) khi mặc đủ 4/4 món. */
    public boolean setBonusFireResistance = true;
    /** Cấp Kháng Cự II (MobEffects.DAMAGE_RESISTANCE, amplifier 1) khi mặc đủ 4/4 món. */
    public boolean setBonusResistance = true;
    /** Bậc hiệu ứng Kháng Cự (0 = I, 1 = II, mặc định 1 = Kháng Cự II; không ghi đè cấp cao hơn). */
    public int setBonusResistanceAmplifier = 1;
    /** Máu tối đa cộng thêm khi mặc đủ 4/4 món (mặc định 4.0 HP = 2 tim). */
    public double setBonusMaxHealth = 4.0;
    /** Chu kỳ làm mới hiệu ứng potion trên server (mặc định 20 ticks = 1.0s). */
    public int setBonusTickInterval = 20;
    /** Thời lượng mỗi lần cấp hiệu ứng potion (mặc định 60 ticks = 3.0s, tránh nhấp nháy HUD). */
    public int setBonusEffectDurationTicks = 60;

    // --- Chỉ số Cinder Dragon Armor Material (×2.5 lượng giáp chuẩn Netherite: 8/20/15/7 = 50 Armor, 7.5 Toughness/món!) ---
    public int cinderHelmetDefense = 8;
    public int cinderChestplateDefense = 20;
    public int cinderLeggingsDefense = 15;
    public int cinderBootsDefense = 7;
    public float cinderToughness = 7.5f;
    public float cinderKnockbackResistance = 0.10f;
    public int cinderDurabilityMultiplier = 37;
    public int cinderEnchantmentValue = 18;

    // --- Full Set Bonus 4/4: Cinder Dragon Set ---
    public boolean cinderEnableSetBonus = true;
    /** Cấp Kháng Lửa I (MobEffects.FIRE_RESISTANCE, amp 0) khi mặc đủ 4/4 Cinder Dragon Armor. */
    public boolean cinderSetBonusFireResistance = true;
    /** Cấp Đề Kháng I (MobEffects.DAMAGE_RESISTANCE, amp 0) khi mặc đủ 4/4 món. */
    public boolean cinderSetBonusResistance = true;
    public int cinderSetBonusResistanceAmplifier = 0;
    /** Cấp Sức Mạnh II (MobEffects.DAMAGE_BOOST, amp 1) khi mặc đủ 4/4 món. */
    public boolean cinderSetBonusStrength = true;
    public int cinderSetBonusStrengthAmplifier = 1;
    /** Cấp Tốc Độ I (MobEffects.MOVEMENT_SPEED, amp 0) khi mặc đủ 4/4 món. */
    public boolean cinderSetBonusSpeed = true;
    public int cinderSetBonusSpeedAmplifier = 0;
    /** Cấp Nhảy Cao I (MobEffects.JUMP, amp 0) khi mặc đủ 4/4 món. */
    public boolean cinderSetBonusJump = true;
    public int cinderSetBonusJumpAmplifier = 0;
    /** Máu tối đa cộng thêm khi mặc đủ 4/4 món (mặc định 16.0 HP = +8 tim!). */
    public double cinderSetBonusMaxHealth = 16.0;
    /** Sát thương đòn đánh cộng thêm tùy chọn (`ADD_VALUE` trên `Attributes.ATTACK_DAMAGE`, mặc định 0.0 vì đã cấp Sức Mạnh II). */
    public double cinderSetBonusAttackDamageBonus = 0.0;
    /** Thời gian đốt cháy mục tiêu tùy chọn tính theo giây (mặc định 0 để tắt). */
    public int cinderSetBonusIgniteSeconds = 0;
    /** Chu kỳ làm mới hiệu ứng phía server (mặc định 20 ticks = 1.0s). */
    public int cinderSetBonusTickInterval = 20;
    /** Thời lượng mỗi lần cấp hiệu ứng potion (mặc định 60 ticks = 3.0s, 0 nhấp nháy HUD). */
    public int cinderSetBonusEffectDurationTicks = 60;

    public static ArmorConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                JsonElement el = JsonParser.parseReader(reader);
                if (el != null && el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    int fileVersion = obj.has("configVersion") && obj.get("configVersion").isJsonPrimitive()
                            ? obj.get("configVersion").getAsInt()
                            : 0;
                    ArmorConfig loaded;
                    boolean migrated = false;
                    if (fileVersion < 2) {
                        // Nâng cấp tự động từ v1 (4/10/8/4, 48x, buff cũ) sang v3 mặc định mới
                        loaded = new ArmorConfig();
                        loaded.configVersion = CURRENT_CONFIG_VERSION;
                        migrated = true;
                        AbyssalRpgMod.LOGGER.info("Tự động nâng cấp ArmorConfig v{} -> v{} với thông số mới", fileVersion, CURRENT_CONFIG_VERSION);
                    } else if (fileVersion < CURRENT_CONFIG_VERSION) {
                        // Giữ nguyên tùy chỉnh Nightmare và tự nâng cấp nhóm Full Set Bonus 4/4 Cinder Dragon Armor v4
                        loaded = GSON.fromJson(obj, ArmorConfig.class);
                        if (loaded == null) {
                            loaded = new ArmorConfig();
                        }
                        if (fileVersion < 5) {
                            loaded.cinderHelmetDefense = 8;
                            loaded.cinderChestplateDefense = 20;
                            loaded.cinderLeggingsDefense = 15;
                            loaded.cinderBootsDefense = 7;
                            loaded.cinderToughness = 7.5f;
                            loaded.cinderSetBonusFireResistance = true;
                            loaded.cinderSetBonusResistance = true;
                            loaded.cinderSetBonusResistanceAmplifier = 0;
                            loaded.cinderSetBonusStrength = true;
                            loaded.cinderSetBonusStrengthAmplifier = 1;
                            loaded.cinderSetBonusSpeed = true;
                            loaded.cinderSetBonusSpeedAmplifier = 0;
                            loaded.cinderSetBonusJump = true;
                            loaded.cinderSetBonusJumpAmplifier = 0;
                            loaded.cinderSetBonusMaxHealth = 16.0;
                            loaded.cinderSetBonusAttackDamageBonus = 0.0;
                            loaded.cinderSetBonusIgniteSeconds = 0;
                        }
                        loaded.configVersion = CURRENT_CONFIG_VERSION;
                        migrated = true;
                        AbyssalRpgMod.LOGGER.info("Tự động cập nhật cấu hình Cinder Dragon Armor: ArmorConfig v{} -> v{}", fileVersion, CURRENT_CONFIG_VERSION);
                    } else {
                        loaded = GSON.fromJson(obj, ArmorConfig.class);
                    }
                    if (loaded != null) {
                        loaded.sanitize();
                        INSTANCE = loaded;
                        if (migrated) {
                            save();
                        }
                        AbyssalRpgMod.LOGGER.info("Đã nạp cấu hình ArmorConfig v{} từ {}", loaded.configVersion, CONFIG_PATH);
                        return;
                    }
                }
            } catch (Exception e) {
                AbyssalRpgMod.LOGGER.warn("Lỗi đọc ArmorConfig, dùng mặc định: {}", e.getMessage());
            }
        }
        INSTANCE = new ArmorConfig();
        INSTANCE.sanitize();
        save();
        AbyssalRpgMod.LOGGER.info("Đã tạo mới cấu hình ArmorConfig v{} tại {}", INSTANCE.configVersion, CONFIG_PATH);
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            AbyssalRpgMod.LOGGER.warn("Không thể lưu ArmorConfig tại {}: {}", CONFIG_PATH, e.getMessage());
        }
    }

    private void sanitize() {
        this.helmetDefense = Math.max(1, Math.min(20, this.helmetDefense));
        this.chestplateDefense = Math.max(1, Math.min(24, this.chestplateDefense));
        this.leggingsDefense = Math.max(1, Math.min(22, this.leggingsDefense));
        this.bootsDefense = Math.max(1, Math.min(20, this.bootsDefense));
        this.toughness = Math.max(0.0f, Math.min(12.0f, this.toughness));
        this.knockbackResistance = Math.max(0.0f, Math.min(0.25f, this.knockbackResistance));
        this.durabilityMultiplier = Math.max(15, Math.min(120, this.durabilityMultiplier));
        this.enchantmentValue = Math.max(1, Math.min(30, this.enchantmentValue));
        this.setBonusResistanceAmplifier = Math.max(0, Math.min(4, this.setBonusResistanceAmplifier));
        if (!Double.isFinite(this.setBonusMaxHealth)) {
            this.setBonusMaxHealth = 4.0;
        }
        this.setBonusMaxHealth = Math.max(0.0, Math.min(40.0, this.setBonusMaxHealth));
        this.setBonusTickInterval = Math.max(1, Math.min(40, this.setBonusTickInterval));
        this.setBonusEffectDurationTicks = Math.max(40, Math.min(200, this.setBonusEffectDurationTicks));

        this.cinderHelmetDefense = Math.max(1, Math.min(25, this.cinderHelmetDefense));
        this.cinderChestplateDefense = Math.max(1, Math.min(40, this.cinderChestplateDefense));
        this.cinderLeggingsDefense = Math.max(1, Math.min(35, this.cinderLeggingsDefense));
        this.cinderBootsDefense = Math.max(1, Math.min(25, this.cinderBootsDefense));
        this.cinderToughness = Math.max(0.0f, Math.min(20.0f, this.cinderToughness));
        this.cinderKnockbackResistance = Math.max(0.0f, Math.min(0.25f, this.cinderKnockbackResistance));
        this.cinderDurabilityMultiplier = Math.max(15, Math.min(120, this.cinderDurabilityMultiplier));
        this.cinderEnchantmentValue = Math.max(1, Math.min(30, this.cinderEnchantmentValue));
        this.cinderSetBonusResistanceAmplifier = Math.max(0, Math.min(4, this.cinderSetBonusResistanceAmplifier));
        this.cinderSetBonusStrengthAmplifier = Math.max(0, Math.min(4, this.cinderSetBonusStrengthAmplifier));
        this.cinderSetBonusSpeedAmplifier = Math.max(0, Math.min(4, this.cinderSetBonusSpeedAmplifier));
        this.cinderSetBonusJumpAmplifier = Math.max(0, Math.min(4, this.cinderSetBonusJumpAmplifier));
        if (!Double.isFinite(this.cinderSetBonusMaxHealth)) {
            this.cinderSetBonusMaxHealth = 16.0;
        }
        this.cinderSetBonusMaxHealth = Math.max(0.0, Math.min(60.0, this.cinderSetBonusMaxHealth));
        if (!Double.isFinite(this.cinderSetBonusAttackDamageBonus)) {
            this.cinderSetBonusAttackDamageBonus = 0.0;
        }
        this.cinderSetBonusAttackDamageBonus = Math.max(0.0, Math.min(20.0, this.cinderSetBonusAttackDamageBonus));
        this.cinderSetBonusIgniteSeconds = Math.max(0, Math.min(20, this.cinderSetBonusIgniteSeconds));
        this.cinderSetBonusTickInterval = Math.max(1, Math.min(40, this.cinderSetBonusTickInterval));
        this.cinderSetBonusEffectDurationTicks = Math.max(40, Math.min(200, this.cinderSetBonusEffectDurationTicks));
    }
}

