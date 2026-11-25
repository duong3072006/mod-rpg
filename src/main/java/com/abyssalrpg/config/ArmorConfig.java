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
 * 1. **Nightmare Knight Armor**: Defense 8/20/15/7 (= 50, ×2.5 Netherite!), Toughness 7.5 (= 30.0), KB Res 0.10 (= 40%),
 *    Độ bền 111x (×3 Netherite: 1221/1776/1665/1443 — giống hệt Cinder Dragon Armor);
 *    Full Set 4/4: Kháng Lửa, Tốc Độ II, Sức Mạnh II, Đề Kháng II, Hấp Thụ I, Hồi Máu I, +20.0 Máu Tối Đa (+10 tim).
 * 2. **Cinder Dragon Armor**: Defense 8/20/15/7 (= 50, ×2.5 Netherite!), Toughness 7.5 (= 30.0), KB Res 0.10 (= 40%),
 *    Độ bền 111x (×3 Netherite: 1221/1776/1665/1443); Full Set 4/4: Kháng Lửa, Đề Kháng I, Sức Mạnh II, Tốc Độ I, Nhảy Cao I, +16.0 Máu Tối Đa (+8 tim).
 */
public final class ArmorConfig {
    public static final int CURRENT_CONFIG_VERSION = 8;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("abyssalrpg_armor.json");

    private static ArmorConfig INSTANCE = new ArmorConfig();

    public int configVersion = CURRENT_CONFIG_VERSION;

    // --- Chỉ số Nightmare Knight Armor Material (đồng bộ ngang Cinder Dragon Armor: 50 Armor, 7.5 Toughness/món, 111x độ bền!) ---
    public int helmetDefense = 8;
    public int chestplateDefense = 20;
    public int leggingsDefense = 15;
    public int bootsDefense = 7;
    public float toughness = 7.5f;
    public float knockbackResistance = 0.10f;
    public int durabilityMultiplier = 111;
    public int enchantmentValue = 18;

    // --- Full Set Bonus 4/4: Nightmare Set ---
    public boolean enableSetBonus = true;
    /** Cấp Kháng Lửa I (MobEffects.FIRE_RESISTANCE, amplifier 0) khi mặc đủ 4/4 món. */
    public boolean setBonusFireResistance = true;
    /** Cấp Tốc Độ II (MobEffects.MOVEMENT_SPEED, amplifier 1) khi mặc đủ 4/4 món. */
    public boolean setBonusSpeed = true;
    public int setBonusSpeedAmplifier = 1;
    /** Cấp Sức Mạnh II (MobEffects.DAMAGE_BOOST, amplifier 1) khi mặc đủ 4/4 món. */
    public boolean setBonusStrength = true;
    public int setBonusStrengthAmplifier = 1;
    /** Cấp Đề Kháng II (MobEffects.DAMAGE_RESISTANCE, amplifier 1) khi mặc đủ 4/4 món. */
    public boolean setBonusResistance = true;
    /** Bậc hiệu ứng Đề Kháng (0 = I, 1 = II, mặc định 1 = Đề Kháng II; không ghi đè cấp cao hơn). */
    public int setBonusResistanceAmplifier = 1;
    /** Cấp Hấp Thụ I (MobEffects.ABSORPTION, amplifier 0 = +2 tim vàng) khi mặc đủ 4/4 món. */
    public boolean setBonusAbsorption = true;
    public int setBonusAbsorptionAmplifier = 0;
    /** Cấp Hồi Máu I (MobEffects.REGENERATION, amplifier 0) khi mặc đủ 4/4 món. */
    public boolean setBonusRegeneration = true;
    public int setBonusRegenerationAmplifier = 0;
    /** Máu tối đa cộng thêm khi mặc đủ 4/4 món (mặc định 20.0 HP = +10 tim!). */
    public double setBonusMaxHealth = 20.0;
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
    public int cinderDurabilityMultiplier = 111;
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
                        if (fileVersion < 6) {
                            loaded.cinderDurabilityMultiplier = 111;
                        }
                        if (fileVersion < 7) {
                            loaded.helmetDefense = 8;
                            loaded.chestplateDefense = 20;
                            loaded.leggingsDefense = 15;
                            loaded.bootsDefense = 7;
                            loaded.toughness = 7.5f;
                            loaded.knockbackResistance = 0.10f;
                            loaded.durabilityMultiplier = 111;
                            loaded.setBonusFireResistance = true;
                            loaded.setBonusSpeed = true;
                            loaded.setBonusSpeedAmplifier = 1;
                            loaded.setBonusStrength = true;
                            loaded.setBonusStrengthAmplifier = 1;
                            loaded.setBonusResistance = true;
                            loaded.setBonusResistanceAmplifier = 1;
                            loaded.setBonusAbsorption = true;
                            loaded.setBonusAbsorptionAmplifier = 0;
                            loaded.setBonusRegeneration = true;
                            loaded.setBonusRegenerationAmplifier = 0;
                            loaded.setBonusMaxHealth = 20.0;
                        }
                        if (fileVersion < 8) {
                            loaded.setBonusFireResistance = true;
                        }
                        loaded.configVersion = CURRENT_CONFIG_VERSION;
                        migrated = true;
                        AbyssalRpgMod.LOGGER.info("Tự động cập nhật cấu hình giáp Boss: ArmorConfig v{} -> v{}", fileVersion, CURRENT_CONFIG_VERSION);
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
        this.helmetDefense = Math.max(1, Math.min(25, this.helmetDefense));
        this.chestplateDefense = Math.max(1, Math.min(40, this.chestplateDefense));
        this.leggingsDefense = Math.max(1, Math.min(35, this.leggingsDefense));
        this.bootsDefense = Math.max(1, Math.min(25, this.bootsDefense));
        this.toughness = Math.max(0.0f, Math.min(20.0f, this.toughness));
        this.knockbackResistance = Math.max(0.0f, Math.min(0.25f, this.knockbackResistance));
        this.durabilityMultiplier = Math.max(15, Math.min(200, this.durabilityMultiplier));
        this.enchantmentValue = Math.max(1, Math.min(30, this.enchantmentValue));
        this.setBonusSpeedAmplifier = Math.max(0, Math.min(4, this.setBonusSpeedAmplifier));
        this.setBonusStrengthAmplifier = Math.max(0, Math.min(4, this.setBonusStrengthAmplifier));
        this.setBonusResistanceAmplifier = Math.max(0, Math.min(4, this.setBonusResistanceAmplifier));
        this.setBonusAbsorptionAmplifier = Math.max(0, Math.min(4, this.setBonusAbsorptionAmplifier));
        this.setBonusRegenerationAmplifier = Math.max(0, Math.min(4, this.setBonusRegenerationAmplifier));
        if (!Double.isFinite(this.setBonusMaxHealth)) {
            this.setBonusMaxHealth = 20.0;
        }
        this.setBonusMaxHealth = Math.max(0.0, Math.min(60.0, this.setBonusMaxHealth));
        this.setBonusTickInterval = Math.max(1, Math.min(40, this.setBonusTickInterval));
        this.setBonusEffectDurationTicks = Math.max(40, Math.min(200, this.setBonusEffectDurationTicks));

        this.cinderHelmetDefense = Math.max(1, Math.min(25, this.cinderHelmetDefense));
        this.cinderChestplateDefense = Math.max(1, Math.min(40, this.cinderChestplateDefense));
        this.cinderLeggingsDefense = Math.max(1, Math.min(35, this.cinderLeggingsDefense));
        this.cinderBootsDefense = Math.max(1, Math.min(25, this.cinderBootsDefense));
        this.cinderToughness = Math.max(0.0f, Math.min(20.0f, this.cinderToughness));
        this.cinderKnockbackResistance = Math.max(0.0f, Math.min(0.25f, this.cinderKnockbackResistance));
        this.cinderDurabilityMultiplier = Math.max(15, Math.min(200, this.cinderDurabilityMultiplier));
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

