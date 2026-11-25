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
 *    Độ bền 111x (×3 Netherite: 1221/1776/1665/1443); Full Set 4/4: Kháng Lửa, Đề Kháng I, Sức Mạnh II, Tốc Độ I, Nhảy Cao I, +16.0 Máu Tối Đa (+8 tim).
 * 3. **Spirit Hydra Armor**: Defense 3/8/6/3 (= 20), Toughness 3.0 (= 12.0), KB Res 0.10 (= 40%),
 *    Độ bền 37x (407/592/555/481); Full Set 4/4: Hồi Phục II, Thở Dưới Nước, +4.0 Máu Tối Đa,
 *    Độc II (3s) + Hồn Lửa Xanh (3s) khi đánh trúng.
 * 4. **Cerberus Armor**: Defense 3/8/6/3 (= 20), Toughness 3.0 (= 12.0), KB Res 0.10 (= 40%),
 *    Độ bền 37x (407/592/555/481); Full Set 4/4: Kháng Lửa, Tốc Độ I, Sức Mạnh I, +4.0 Máu Tối Đa,
 *    đòn đánh thiêu đốt Lửa Địa Ngục 4s.
 */
public final class ArmorConfig {
    public static final int CURRENT_CONFIG_VERSION = 8;

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

    // --- Chỉ số Spirit Hydra Armor Material (chuẩn Netherite 37x, chỉnh được độc lập) ---
    public int hydraHelmetDefense = 3;
    public int hydraChestplateDefense = 8;
    public int hydraLeggingsDefense = 6;
    public int hydraBootsDefense = 3;
    public float hydraToughness = 3.0f;
    public float hydraKnockbackResistance = 0.10f;
    public int hydraDurabilityMultiplier = 37;
    public int hydraEnchantmentValue = 18;
    /** Tỉ lệ gốc rơi Trái Tim Hồn Xà (`spirit_hydra_heart`) khi Boss Spirit Hydra chết thật Phase 2 (mặc định 0.05 = 5%). */
    public float hydraHeartDropChance = 0.05f;

    // --- Full Set Bonus 4/4: Spirit Hydra Set ---
    public boolean hydraEnableSetBonus = true;
    /** Cấp Hồi Phục II (`MobEffects.REGENERATION`, amp 1) khi mặc đủ 4/4 Spirit Hydra Armor. */
    public boolean hydraSetBonusRegeneration = true;
    public int hydraSetBonusRegenerationAmplifier = 1;
    /** Cấp Thở Dưới Nước (`MobEffects.WATER_BREATHING`, amp 0) khi mặc đủ 4/4 Spirit Hydra Armor. */
    public boolean hydraSetBonusWaterBreathing = true;
    /** Máu tối đa cộng thêm (`ADD_VALUE` trên `Attributes.MAX_HEALTH`, mặc định +4.0 HP = +2 tim). */
    public double hydraSetBonusMaxHealth = 4.0;
    /** Thời gian gây Độc II khi đánh trúng mục tiêu (mặc định 3s, 0 để tắt). */
    public int hydraSetBonusPoisonSeconds = 3;
    public int hydraSetBonusPoisonAmplifier = 1;
    /** Thời gian thiêu đốt Hồn Lửa Xanh khi đánh trúng mục tiêu (mặc định 3s, 0 để tắt). */
    public int hydraSetBonusSoulFlameSeconds = 3;
    public int hydraSetBonusTickInterval = 20;
    public int hydraSetBonusEffectDurationTicks = 60;

    // --- Chỉ số Cerberus Armor Material (chuẩn Netherite 37x, chỉnh được độc lập) ---
    public int cerberusHelmetDefense = 3;
    public int cerberusChestplateDefense = 8;
    public int cerberusLeggingsDefense = 6;
    public int cerberusBootsDefense = 3;
    public float cerberusToughness = 3.0f;
    public float cerberusKnockbackResistance = 0.10f;
    public int cerberusDurabilityMultiplier = 37;
    public int cerberusEnchantmentValue = 18;
    /** Tỉ lệ gốc rơi Trái Tim Cerberus (`cerberus_heart`) khi hạ gục Boss Cerberus Phase 2 (mặc định 0.05 = 5%). */
    public float cerberusHeartDropChance = 0.05f;

    // --- Full Set Bonus 4/4: Cerberus Set ---
    public boolean cerberusEnableSetBonus = true;
    public boolean cerberusSetBonusFireResistance = true;
    public boolean cerberusSetBonusSpeed = true;
    public int cerberusSetBonusSpeedAmplifier = 0;
    public boolean cerberusSetBonusStrength = true;
    public int cerberusSetBonusStrengthAmplifier = 0;
    public double cerberusSetBonusMaxHealth = 4.0;
    public int cerberusSetBonusIgniteSeconds = 4;
    public int cerberusSetBonusTickInterval = 20;
    public int cerberusSetBonusEffectDurationTicks = 60;

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

        this.hydraHelmetDefense = Math.max(1, Math.min(20, this.hydraHelmetDefense));
        this.hydraChestplateDefense = Math.max(1, Math.min(24, this.hydraChestplateDefense));
        this.hydraLeggingsDefense = Math.max(1, Math.min(22, this.hydraLeggingsDefense));
        this.hydraBootsDefense = Math.max(1, Math.min(20, this.hydraBootsDefense));
        this.hydraToughness = Math.max(0.0f, Math.min(12.0f, this.hydraToughness));
        this.hydraKnockbackResistance = Math.max(0.0f, Math.min(0.25f, this.hydraKnockbackResistance));
        this.hydraDurabilityMultiplier = Math.max(15, Math.min(120, this.hydraDurabilityMultiplier));
        this.hydraEnchantmentValue = Math.max(1, Math.min(30, this.hydraEnchantmentValue));
        if (!Float.isFinite(this.hydraHeartDropChance)) {
            this.hydraHeartDropChance = 0.05f;
        }
        this.hydraHeartDropChance = Math.max(0.0f, Math.min(1.0f, this.hydraHeartDropChance));
        this.hydraSetBonusRegenerationAmplifier = Math.max(0, Math.min(4, this.hydraSetBonusRegenerationAmplifier));
        if (!Double.isFinite(this.hydraSetBonusMaxHealth)) {
            this.hydraSetBonusMaxHealth = 4.0;
        }
        this.hydraSetBonusMaxHealth = Math.max(0.0, Math.min(40.0, this.hydraSetBonusMaxHealth));
        this.hydraSetBonusPoisonSeconds = Math.max(0, Math.min(20, this.hydraSetBonusPoisonSeconds));
        this.hydraSetBonusPoisonAmplifier = Math.max(0, Math.min(4, this.hydraSetBonusPoisonAmplifier));
        this.hydraSetBonusSoulFlameSeconds = Math.max(0, Math.min(20, this.hydraSetBonusSoulFlameSeconds));
        this.hydraSetBonusTickInterval = Math.max(1, Math.min(40, this.hydraSetBonusTickInterval));
        this.hydraSetBonusEffectDurationTicks = Math.max(40, Math.min(200, this.hydraSetBonusEffectDurationTicks));

        this.cerberusHelmetDefense = Math.max(1, Math.min(20, this.cerberusHelmetDefense));
        this.cerberusChestplateDefense = Math.max(1, Math.min(24, this.cerberusChestplateDefense));
        this.cerberusLeggingsDefense = Math.max(1, Math.min(22, this.cerberusLeggingsDefense));
        this.cerberusBootsDefense = Math.max(1, Math.min(20, this.cerberusBootsDefense));
        this.cerberusToughness = Math.max(0.0f, Math.min(12.0f, this.cerberusToughness));
        this.cerberusKnockbackResistance = Math.max(0.0f, Math.min(0.25f, this.cerberusKnockbackResistance));
        this.cerberusDurabilityMultiplier = Math.max(15, Math.min(120, this.cerberusDurabilityMultiplier));
        this.cerberusEnchantmentValue = Math.max(1, Math.min(30, this.cerberusEnchantmentValue));
        if (!Float.isFinite(this.cerberusHeartDropChance)) {
            this.cerberusHeartDropChance = 0.05f;
        }
        this.cerberusHeartDropChance = Math.max(0.0f, Math.min(1.0f, this.cerberusHeartDropChance));
        this.cerberusSetBonusSpeedAmplifier = Math.max(0, Math.min(4, this.cerberusSetBonusSpeedAmplifier));
        this.cerberusSetBonusStrengthAmplifier = Math.max(0, Math.min(4, this.cerberusSetBonusStrengthAmplifier));
        if (!Double.isFinite(this.cerberusSetBonusMaxHealth)) {
            this.cerberusSetBonusMaxHealth = 4.0;
        }
        this.cerberusSetBonusMaxHealth = Math.max(0.0, Math.min(40.0, this.cerberusSetBonusMaxHealth));
        this.cerberusSetBonusIgniteSeconds = Math.max(0, Math.min(20, this.cerberusSetBonusIgniteSeconds));
        this.cerberusSetBonusTickInterval = Math.max(1, Math.min(40, this.cerberusSetBonusTickInterval));
        this.cerberusSetBonusEffectDurationTicks = Math.max(40, Math.min(200, this.cerberusSetBonusEffectDurationTicks));
    }
}

