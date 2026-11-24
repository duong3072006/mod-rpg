package com.abyssalrpg.combat;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.api.BossHealthHolder;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.NightmareArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Xử lý Full Set Bonus 4/4 của Bộ Giáp Nightmare Knight phía server:
 * 1. Kháng Lửa (`MobEffects.FIRE_RESISTANCE`, cấp I / amp 0): làm mới mượt mỗi `20 tick`, duration `60 tick`,
 *    `ambient = true`, `visible = false`, `showIcon = true`.
 * 2. Kháng Cự II (`MobEffects.DAMAGE_RESISTANCE`, cấp II / amp 1): làm mới mượt cùng chu kỳ,
 *    tuyệt đối không ghi đè nếu người chơi đang có Kháng Cự cấp cao hơn (III+).
 * 3. +4.0 Máu Tối Đa (+2 tim) qua `AttributeModifier` cố định `abyssalrpg:nightmare_armor_set_health`
 *    (`ADD_VALUE`), không cộng dồn khi relog/đổi world, gỡ ngay khi tháo 1 món + clamp máu an toàn,
 *    và không bao giờ tác động lên `BossHealthHolder`.
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
     * Cấp 2 hiệu ứng potion khi mặc đủ 4/4 món:
     * - `FIRE_RESISTANCE` cấp I (`amplifier = 0`), `duration = 60t`, `ambient = true`, `visible = false`, `showIcon = true`.
     * - `DAMAGE_RESISTANCE` cấp II (`amplifier = 1`), cùng thông số; KHÔNG ghi đè nếu người chơi đang có Kháng Cự cấp cao hơn (III+).
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
            MobEffectInstance existingFire = entity.getEffect(MobEffects.FIRE_RESISTANCE);
            if (existingFire == null || (existingFire.getAmplifier() == 0 && existingFire.getDuration() < duration)) {
                entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, duration, 0, true, false, true));
            }
        }

        if (cfg.setBonusResistance) {
            int targetAmp = Math.max(0, cfg.setBonusResistanceAmplifier);
            MobEffectInstance existingRes = entity.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (existingRes == null
                    || existingRes.getAmplifier() < targetAmp
                    || (existingRes.getAmplifier() == targetAmp && existingRes.getDuration() < duration)) {
                entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, targetAmp, true, false, true));
            }
        }
    }
}

