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
 * Cấu hình JSON cho bộ Giáp Nightmare Knight Endgame (`config/abyssalrpg_armor.json`).
 * - Chỉ số giáp 4 món (4 / 10 / 8 / 4 = 26 Defense, Toughness 4.0/món, KB Res 0.15/món, Độ bền 48x, Enchantability 18).
 * - Full Set Bonus 4/4 (`Nightmare Sovereign`): Miễn Darkness, Kháng Lửa giao chiến, Giảm 12% sát thương cận chiến/Boss,
 *   Phản Gai 20% sát thương cận chiến (trần 15.0 dmg, hồi 20 ticks).
 */
public final class ArmorConfig {
    public static final int CURRENT_CONFIG_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("abyssalrpg_armor.json");

    private static ArmorConfig INSTANCE = new ArmorConfig();

    public int configVersion = CURRENT_CONFIG_VERSION;

    // --- Chỉ số Nightmare Knight Armor Material ---
    public int helmetDefense = 4;
    public int chestplateDefense = 10;
    public int leggingsDefense = 8;
    public int bootsDefense = 4;
    public float toughness = 4.0f;
    public float knockbackResistance = 0.15f;
    public int durabilityMultiplier = 48;
    public int enchantmentValue = 18;

    // --- Full Set Bonus 4/4: Nightmare Sovereign ---
    public boolean enableSetBonus = true;
    /** Miễn nhiễm hoàn toàn hiệu ứng Darkness (khắc chế Nightmare Realm của Nightmare Knight). */
    public boolean darknessImmunity = true;
    /** Kháng lửa nhẹ khi đang cháy hoặc giao chiến (không spam hạt potion). */
    public boolean combatFireResistance = true;
    /** Giảm thêm % sát thương từ đòn cận chiến / Boss khi mặc đủ 4/4 (mặc định 0.12 = 12%). */
    public float meleeBossDamageReduction = 0.12f;
    /** Tỉ lệ phản sát thương gai cận chiến (mặc định 0.20 = 20% sát thương nhận vào). */
    public float spikedRetaliationRatio = 0.20f;
    /** Trần sát thương phản gai tối đa mỗi lần kích hoạt (mặc định 15.0 dmg). */
    public float spikedRetaliationCap = 15.0f;
    /** Cooldown phản gai mỗi người chơi tính theo tick (mặc định 20 ticks = 1.0s). */
    public int spikedRetaliationCooldownTicks = 20;
    /** Bật/tắt 3-5 tia lửa đỏ nhỏ gọn khi kích hoạt Phản Gai (0 spam hạt khi đứng yên). */
    public boolean enableRetaliationSparks = true;

    public static ArmorConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                JsonElement el = JsonParser.parseReader(reader);
                if (el != null && el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    ArmorConfig loaded = GSON.fromJson(obj, ArmorConfig.class);
                    if (loaded != null) {
                        boolean migrated = false;
                        if (loaded.configVersion < CURRENT_CONFIG_VERSION) {
                            loaded.configVersion = CURRENT_CONFIG_VERSION;
                            migrated = true;
                        }
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
        this.meleeBossDamageReduction = Math.max(0.0f, Math.min(0.50f, this.meleeBossDamageReduction));
        this.spikedRetaliationRatio = Math.max(0.0f, Math.min(0.50f, this.spikedRetaliationRatio));
        this.spikedRetaliationCap = Math.max(1.0f, Math.min(60.0f, this.spikedRetaliationCap));
        this.spikedRetaliationCooldownTicks = Math.max(5, Math.min(200, this.spikedRetaliationCooldownTicks));
    }
}

