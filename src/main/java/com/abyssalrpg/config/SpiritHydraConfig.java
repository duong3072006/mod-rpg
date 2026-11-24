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
 * Cấu hình JSON cho Boss Spirit Hydra 4 Đầu (lưu tại `config/abyssalrpg_spirit_hydra.json`, phiên bản v2).
 *
 * - Phase 1: 2000 HP chia đều đúng 4 đầu (`4 x 500 HP`), Armor `15`, Toughness `6`, đòn nặng `30`.
 * - Đánh trúng trực tiếp từng đầu/cổ nhân `headHitDamageMultiplier = 1.15` trừ thẳng vào đầu đó;
 *   đánh thân/chân/đuôi trừ vào đầu còn sống có máu thấp nhất.
 * - Mỗi đầu bị chặt tăng `+8%` tốc độ ra đòn cho các đầu còn lại.
 * - Phase 2 "Cuồng bạo": cận tử (`<= 5%` hoặc `<= 1 HP`) -> bất tử `5.0s`, hồi `3000 HP` (`x1.5`, tương đương `750 HP/đầu`),
 *   mọc lại cả 4 đầu cách nhau `0.35s` (`7 ticks`), Armor `22.5`, đòn nặng `45`, mặc định `allowHeadCutInPhase2 = false`.
 */
public final class SpiritHydraConfig {
    private static final int CURRENT_CONFIG_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("abyssalrpg_spirit_hydra.json");

    public enum VfxQuality {
        MINIMAL,
        LOW,
        MEDIUM,
        HIGH
    }

    private static SpiritHydraConfig INSTANCE = createDefault();

    /** Mặc định `0` để file config v1 cũ (không có trường `configVersion`) luôn được tự động migrate lên v2! */
    public int configVersion = 0;

    // --- Hệ số kích thước ngang / nhỉnh nhẹ Leviathan (`SCALE = 2.5f`, `visualScaleMultiplier = 1.15f`) ---
    public float scale = 2.5f;
    public float visualScaleMultiplier = 1.15f;

    // --- Chỉ số cơ bản Phase 1 (2000 HP chia đều 4 đầu = 500 HP/đầu) ---
    public double phase1MaxHealth = 2000.0;       // Phase 2 (x1.5) -> 3000 HP (750 HP/đầu)
    public double armor = 15.0;                   // Phase 2 (x1.5) -> 22.5
    public double armorToughness = 6.0;
    public double knockbackResistance = 1.0;
    public double baseMoveSpeed = 0.30;
    public double stepHeight = 2.5;
    public double detectionRadius = 96.0;
    public double bossBarRadius = 104.0;
    public int maxActiveWisps = 6;

    // --- Cơ chế 4 đầu (500 HP/đầu) & chặt đầu ---
    public double headHitDamageMultiplier = 1.15;     // x1.15 khi đánh trực tiếp vào proxy đầu/cổ
    public int headSeverDelayTicks = 6;               // Khoảng cách chặt nối tiếp nếu 1 đòn lớn hạ nhiều đầu (~0.3s)
    public int headSeverStaggerTicks = 25;            // Khựng đau bất tử khi mất đầu (`1.25s`, khớp `head_cut` = 25t)
    public double hydraFangDropChance = 0.85;         // Tỉ lệ rơi Spirit Hydra Fang khi chặt từng đầu
    public double attackSpeedBonusPerLostHead = 0.08; // +8% tốc độ đánh mỗi đầu bị chặt
    public boolean allowHeadCutInPhase2 = false;      // Tắt chặt đầu ở Phase 2 theo mặc định

    // --- Cơ chế Phase 2 "Cuồng bạo" cận tử ---
    public double phase2TriggerHpRatio = 0.05;        // <= 5% Phase 1 maxHP (100 HP) hoặc <= 1 HP
    public double phase2StatMultiplier = 1.50;        // HP 2000->3000; Giáp 15->22.5; ST 30->45; Tốc chạy x1.5
    public float phase2AttackAnimSpeed = 1.18f;       // Tốc độ phát anim đánh Phase 2 đồng bộ 0-tick Server <-> Client
    public double phase2TurnSpeedMultiplier = 1.25;
    public int phaseTransitionTicks = 100;            // 5.0 giây bất tử (khớp 1:1 `phase_change` = 5.00s)
    public int headRegrowIntervalTicks = 7;           // Mọc lại từng đầu cách nhau đúng 0.35s (7 ticks)
    public int minTelegraphTicks = 8;

    // --- Sát thương Phase 1 (Phase 2 tự nhân `phase2StatMultiplier = 1.5x`) ---
    public float meleeDamage = 15.0f;                 // Đòn thường: 15 (P2: 22.5)
    public float headSlotBiteDamage = 18.0f;          // Đòn cắn độc lập từng đầu: 18 (P2: 27)
    public float tripleBiteDamage = 30.0f;            // Đòn nặng: 30 (P2: 45)
    public float spiritBreathTickDamage = 14.0f;      // Quét hơi hồn: 14/tick (P2: 21)
    public float soulSpitDamage = 16.0f;              // Cầu hồn parabol: 16 (P2: 24)
    public float tailBodySlamDamage = 30.0f;          // Quật đuôi/đập thân nặng: 30 (P2: 45)
    public float allHeadBarrageDamage = 16.0f;        // Mưa đạn hồn (P2 x1.5 = 24)
    public float spiritNovaDamage = 20.0f;            // Sóng tròn nhảy né (P2 x1.5 = 30)
    public float soulChainPullDamage = 12.0f;         // Xích kéo (P2 x1.5 = 18)
    public float soulChainBiteDamage = 30.0f;         // Cắn bồi sau xích (P2 x1.5 = 45)
    public float wrathOfManyHeadsDamage = 30.0f;      // Ultimate 360° (P2 x1.5 = 45)

    // --- Cooldown Phase 1 (ticks, Phase 2 chia 1.5) ---
    public int tripleBiteCooldown = 105;
    public int spiritBreathCooldown = 145;
    public int soulSpitCooldown = 135;
    public int tailBodySlamCooldown = 125;
    public int summonWispsCooldown = 340;
    public int allHeadBarrageCooldown = 210;
    public int spiritNovaCooldown = 195;
    public int soulChainCooldown = 205;
    public int wrathCooldown = 1200;                  // 60 giây (Ultimate HP < 20%)

    // --- Tối ưu hiệu ứng chống lag (ưu tiên glowmask fullbright, gom VFX qua payload nhẹ) ---
    public VfxQuality vfxQuality = VfxQuality.MINIMAL;
    public float particleMultiplier = 0.25f;

    // --- Debug xoay đầu, bể máu 4 đầu & proxy hitbox (/hydra debug) ---
    public boolean debugRotationLogs = false;

    private static SpiritHydraConfig createDefault() {
        SpiritHydraConfig cfg = new SpiritHydraConfig();
        cfg.configVersion = CURRENT_CONFIG_VERSION;
        return cfg;
    }

    public static SpiritHydraConfig get() {
        return INSTANCE;
    }

    public float getEffectiveParticleMultiplier() {
        float qMult = switch (this.vfxQuality == null ? VfxQuality.MINIMAL : this.vfxQuality) {
            case MINIMAL -> 0.55f;
            case LOW -> 0.75f;
            case MEDIUM -> 1.0f;
            case HIGH -> 1.35f;
        };
        return Math.max(0.0f, Math.min(2.0f, this.particleMultiplier * qMult));
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                SpiritHydraConfig loaded = GSON.fromJson(reader, SpiritHydraConfig.class);
                if (loaded != null) {
                    boolean migrated = loaded.migrateIfNeeded();
                    loaded.sanitize();
                    INSTANCE = loaded;
                    if (migrated) {
                        save();
                        AbyssalRpgMod.LOGGER.info("Đã nâng cấp cấu hình Spirit Hydra lên v{} (2000 HP / 4 đầu, visualScaleMultiplier=1.15, regrow=7t)", CURRENT_CONFIG_VERSION);
                    } else {
                        AbyssalRpgMod.LOGGER.info("Đã nạp cấu hình Spirit Hydra v{} từ {}", loaded.configVersion, CONFIG_PATH);
                    }
                    return;
                }
            } catch (Exception e) {
                AbyssalRpgMod.LOGGER.warn("Lỗi đọc cấu hình Spirit Hydra, dùng mặc định: {}", e.getMessage());
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
            AbyssalRpgMod.LOGGER.warn("Không thể lưu cấu hình Spirit Hydra: {}", e.getMessage());
        }
    }

    private boolean migrateIfNeeded() {
        if (this.configVersion >= CURRENT_CONFIG_VERSION) {
            return false;
        }
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.phase1MaxHealth = 2000.0;
        this.visualScaleMultiplier = 1.15f;
        this.headHitDamageMultiplier = 1.15;
        this.hydraFangDropChance = 0.85;
        this.attackSpeedBonusPerLostHead = 0.08;
        this.headRegrowIntervalTicks = 7;
        this.allowHeadCutInPhase2 = false;
        this.phase2AttackAnimSpeed = 1.18f;
        this.vfxQuality = VfxQuality.MINIMAL;
        this.particleMultiplier = 0.25f;
        return true;
    }

    private void sanitize() {
        this.scale = Math.max(0.5f, Math.min(6.0f, this.scale));
        this.visualScaleMultiplier = Math.max(0.6f, Math.min(2.5f, this.visualScaleMultiplier));
        this.phase1MaxHealth = Math.max(400.0, Math.min(50000.0, this.phase1MaxHealth));
        this.armor = Math.max(0.0, Math.min(20.0, this.armor));
        this.headHitDamageMultiplier = Math.max(1.0, Math.min(2.5, this.headHitDamageMultiplier));
        this.headSeverDelayTicks = Math.max(2, Math.min(40, this.headSeverDelayTicks));
        this.headSeverStaggerTicks = Math.max(10, Math.min(60, this.headSeverStaggerTicks));
        this.hydraFangDropChance = Math.max(0.0, Math.min(1.0, this.hydraFangDropChance));
        this.attackSpeedBonusPerLostHead = Math.max(0.0, Math.min(0.25, this.attackSpeedBonusPerLostHead));
        this.phase2TriggerHpRatio = Math.max(0.005, Math.min(0.25, this.phase2TriggerHpRatio));
        this.phase2StatMultiplier = Math.max(1.0, Math.min(3.0, this.phase2StatMultiplier));
        this.phase2AttackAnimSpeed = Math.max(1.0f, Math.min(1.40f, this.phase2AttackAnimSpeed));
        this.phase2TurnSpeedMultiplier = Math.max(1.0, Math.min(2.0, this.phase2TurnSpeedMultiplier));
        this.phaseTransitionTicks = Math.max(80, Math.min(120, this.phaseTransitionTicks));
        this.headRegrowIntervalTicks = Math.max(2, Math.min(20, this.headRegrowIntervalTicks));
        this.minTelegraphTicks = Math.max(8, Math.min(40, this.minTelegraphTicks));
        this.maxActiveWisps = Math.max(1, Math.min(12, this.maxActiveWisps));
        this.bossBarRadius = Math.max(64.0, Math.min(256.0, this.bossBarRadius));
        this.particleMultiplier = Math.max(0.0f, Math.min(2.0f, this.particleMultiplier));
        if (this.vfxQuality == null) {
            this.vfxQuality = VfxQuality.MINIMAL;
        }
    }
}
