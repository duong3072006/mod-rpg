package com.abyssalrpg.combat;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.api.BossHealthHolder;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.NightmareArmorItem;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Xử lý Full Set Bonus 4/4 của Bộ Giáp Nightmare Knight phía server:
 * 1. **Kháng Lửa I** (`MobEffects.FIRE_RESISTANCE`, amp 0)
 * 2. **Tốc Độ II** (`MobEffects.MOVEMENT_SPEED`, amp 1)
 * 3. **Sức Mạnh II** (`MobEffects.DAMAGE_BOOST`, amp 1)
 * 4. **Đề Kháng II** (`MobEffects.DAMAGE_RESISTANCE`, amp 1)
 * 5. **Hấp Thụ I** (`MobEffects.ABSORPTION`, amp 0 = +2 tim vàng; giữ ổn định khi còn giáp vàng & tự hồi sau ~8s nếu vỡ!)
 * 6. **Hồi Máu I** (`MobEffects.REGENERATION`, amp 0; làm mới chuẩn chu kỳ `120t -> <=25t` để nhịp hồi chuẩn xác 50 tick/nửa tim!)
 * 7. **+20.0 Máu Tối Đa (+10 tim)** qua `AttributeModifier` cố định `abyssalrpg:nightmare_armor_set_health`
 *    (`ADD_VALUE`), không cộng dồn khi relog/đổi world, gỡ ngay khi tháo 1 món + clamp máu an toàn,
 *    và không bao giờ tác động lên `BossHealthHolder`.
 * (Tuyệt đối không ghi đè các hiệu ứng potion cấp cao hơn đang có trên người chơi.)
 */
public final class NightmareArmorHandler {
    public static final ResourceLocation SET_HEALTH_MODIFIER_ID = AbyssalRpgMod.id("nightmare_armor_set_health");

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private NightmareArmorHandler() {}

    public static int countEquippedPieces(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof NightmareArmorItem) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasFullSet(LivingEntity entity) {
        return countEquippedPieces(entity) >= 4;
    }

    /**
     * Tick trên server:
     * - Đồng bộ `MAX_HEALTH` modifier tức thì mỗi tick (0 delay khi mặc/tháo 1 món, chi phí ~4 slot checks).
     * - Cấp mượt 2 hiệu ứng potion (`FIRE_RESISTANCE` + `DAMAGE_RESISTANCE`) đúng chu kỳ `setBonusTickInterval` (20 ticks).
     */
    public static void tickServer(MinecraftServer server) {
        ArmorConfig cfg = ArmorConfig.get();
        int interval = Math.max(1, cfg.setBonusTickInterval);
        boolean applyEffectsThisTick = (server.getTickCount() % interval) == 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive()) {
                continue;
            }
            syncMaxHealthModifier(player);
            if (applyEffectsThisTick) {
                applyPeriodicSetEffects(player);
            }
        }
    }

    /** Đồng bộ ngay lập tức cả máu tối đa lẫn 2 hiệu ứng (dùng khi join, respawn, đổi world hoặc audit test). */
    public static void syncPlayerImmediate(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        syncMaxHealthModifier(entity);
        applyPeriodicSetEffects(entity);
    }

    /**
     * Gắn/gỡ sạch `AttributeModifier` `abyssalrpg:nightmare_armor_set_health` (`ADD_VALUE`):
     * - Đủ 4/4 & bật config: chỉ thêm nếu chưa có (hoặc cập nhật nếu config đổi), không bao giờ cộng dồn.
     * - Thiếu bất kỳ món nào / tắt config: gỡ ngay modifier và clamp `health <= maxHealth`.
     * - Tuyệt đối bỏ qua thực thể `BossHealthHolder` để không xung đột với Mixin máu Boss.
     */
    public static void syncMaxHealthModifier(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || entity instanceof BossHealthHolder) {
            return;
        }
        AttributeInstance maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr == null) {
            return;
        }

        ArmorConfig cfg = ArmorConfig.get();
        boolean shouldHaveBonus = cfg.enableSetBonus && cfg.setBonusMaxHealth > 0.0 && hasFullSet(entity);
        AttributeModifier existing = maxHealthAttr.getModifier(SET_HEALTH_MODIFIER_ID);

        if (shouldHaveBonus) {
            double bonus = cfg.setBonusMaxHealth;
            if (existing == null) {
                maxHealthAttr.addPermanentModifier(new AttributeModifier(
                        SET_HEALTH_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            } else if (Math.abs(existing.amount() - bonus) > 1e-6
                    || existing.operation() != AttributeModifier.Operation.ADD_VALUE) {
                maxHealthAttr.removeModifier(SET_HEALTH_MODIFIER_ID);
                maxHealthAttr.addPermanentModifier(new AttributeModifier(
                        SET_HEALTH_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            }
        } else if (existing != null) {
            maxHealthAttr.removeModifier(SET_HEALTH_MODIFIER_ID);
            float newMax = entity.getMaxHealth();
            if (entity.getHealth() > newMax) {
                entity.setHealth(newMax);
            }
        }
    }

    /**
     * Cấp toàn bộ hiệu ứng potion khi mặc đủ 4/4 Bộ Giáp Nightmare Knight:
     * - Speed II, Strength II, Resistance II (+ Fire Resistance nếu bật trong config): làm mới mượt `60t`.
     * - Regeneration I: cấp `120t` & chỉ làm mới khi `<= 25t` để giữ đúng nhịp hồi vanilla 50 tick/nửa tim!
     * - Absorption I: cấp `160t` (+2 tim vàng); khi còn tim vàng giữ ổn định không nhấp nháy, khi vỡ tim vàng
     *   để bộ đếm chạy hết (~6–8s) rồi tự động hồi lại 2 tim vàng mới (tránh spam bất tử mỗi giây & không đè Táo Vàng!).
     */
    public static void applyPeriodicSetEffects(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.enableSetBonus || !hasFullSet(entity)) {
            return;
        }

        int duration = Math.max(40, cfg.setBonusEffectDurationTicks);

        if (cfg.setBonusFireResistance) {
            refreshAmbientEffect(entity, MobEffects.FIRE_RESISTANCE, duration, 0);
        }
        if (cfg.setBonusSpeed) {
            refreshAmbientEffect(entity, MobEffects.MOVEMENT_SPEED, duration, Math.max(0, cfg.setBonusSpeedAmplifier));
        }
        if (cfg.setBonusStrength) {
            refreshAmbientEffect(entity, MobEffects.DAMAGE_BOOST, duration, Math.max(0, cfg.setBonusStrengthAmplifier));
        }
        if (cfg.setBonusResistance) {
            refreshAmbientEffect(entity, MobEffects.DAMAGE_RESISTANCE, duration, Math.max(0, cfg.setBonusResistanceAmplifier));
        }
        if (cfg.setBonusRegeneration) {
            int regenAmp = Math.max(0, cfg.setBonusRegenerationAmplifier);
            MobEffectInstance existingRegen = entity.getEffect(MobEffects.REGENERATION);
            if (existingRegen == null
                    || existingRegen.getAmplifier() < regenAmp
                    || (existingRegen.getAmplifier() == regenAmp && existingRegen.getDuration() <= 25)) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, regenAmp, true, false, true));
            }
        }
        if (cfg.setBonusAbsorption) {
            int absAmp = Math.max(0, cfg.setBonusAbsorptionAmplifier);
            MobEffectInstance existingAbs = entity.getEffect(MobEffects.ABSORPTION);
            if (existingAbs == null || existingAbs.getAmplifier() < absAmp) {
                entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, absAmp, true, false, true));
            } else if (existingAbs.getAmplifier() == absAmp
                    && entity.getAbsorptionAmount() > 0.0F
                    && existingAbs.getDuration() < 120) {
                entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, absAmp, true, false, true));
            }
        }
    }

    private static void refreshAmbientEffect(LivingEntity entity, Holder<MobEffect> effect, int duration, int targetAmp) {
        MobEffectInstance existing = entity.getEffect(effect);
        if (existing == null
                || existing.getAmplifier() < targetAmp
                || (existing.getAmplifier() == targetAmp && existing.getDuration() < duration)) {
            entity.addEffect(new MobEffectInstance(effect, duration, targetAmp, true, false, true));
        }
    }
}

