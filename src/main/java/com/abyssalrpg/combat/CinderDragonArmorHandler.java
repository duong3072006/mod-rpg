package com.abyssalrpg.combat;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.api.BossHealthHolder;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.CinderDragonArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Xử lý Full Set Bonus 4/4 của Bộ Giáp Cinder Dragon phía server:
 * 1. Kháng Lửa (`MobEffects.FIRE_RESISTANCE`, amp 0): làm mới mượt mỗi `20 tick`, duration `60 tick`,
 *    `ambient = true`, `visible = false`, `showIcon = true` (0 nhấp nháy HUD, không ghi đè potion dài/cao hơn).
 * 2. Tăng sát thương đòn đánh (`+3.0` Attack Damage mặc định qua `AttributeModifier` cố định
 *    `abyssalrpg:cinder_dragon_armor_set_attack_damage`, `ADD_VALUE`, không cộng dồn khi relog/đổi world,
 *    gỡ ngay lập tức khi tháo bất kỳ 1 món nào, tương thích hoàn hảo với `Dual Knife` & `BossHealthHolder`).
 * 3. Đốt cháy nhẹ (`3s` mặc định) mục tiêu khi người mặc đủ 4/4 đánh trúng (chỉ phía server).
 */
public final class CinderDragonArmorHandler {
    public static final ResourceLocation SET_ATTACK_DAMAGE_MODIFIER_ID =
            AbyssalRpgMod.id("cinder_dragon_armor_set_attack_damage");

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private CinderDragonArmorHandler() {}

    public static int countEquippedPieces(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof CinderDragonArmorItem) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasFullSet(LivingEntity entity) {
        return countEquippedPieces(entity) >= 4;
    }

    public static void tickServer(MinecraftServer server) {
        ArmorConfig cfg = ArmorConfig.get();
        int interval = Math.max(1, cfg.cinderSetBonusTickInterval);
        boolean applyEffectsThisTick = (server.getTickCount() % interval) == 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive()) {
                continue;
            }
            syncAttackDamageModifier(player);
            if (applyEffectsThisTick) {
                applyPeriodicSetEffects(player);
            }
        }
    }

    public static void syncPlayerImmediate(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        syncAttackDamageModifier(entity);
        applyPeriodicSetEffects(entity);
    }

    /**
     * Gắn/gỡ sạch `AttributeModifier` `abyssalrpg:cinder_dragon_armor_set_attack_damage` (`ADD_VALUE`):
     * - Đủ 4/4 & bật config: chỉ thêm nếu chưa có (hoặc cập nhật khi config đổi), không cộng dồn khi relog/đổi world.
     * - Thiếu bất kỳ món nào / tắt config: gỡ ngay modifier.
     * - Bỏ qua thực thể `BossHealthHolder`.
     */
    public static void syncAttackDamageModifier(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || entity instanceof BossHealthHolder) {
            return;
        }
        AttributeInstance atkAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atkAttr == null) {
            return;
        }

        ArmorConfig cfg = ArmorConfig.get();
        boolean shouldHaveBonus = cfg.cinderEnableSetBonus
                && cfg.cinderSetBonusAttackDamageBonus > 0.0
                && hasFullSet(entity);
        AttributeModifier existing = atkAttr.getModifier(SET_ATTACK_DAMAGE_MODIFIER_ID);

        if (shouldHaveBonus) {
            double bonus = cfg.cinderSetBonusAttackDamageBonus;
            if (existing == null) {
                atkAttr.addPermanentModifier(new AttributeModifier(
                        SET_ATTACK_DAMAGE_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            } else if (Math.abs(existing.amount() - bonus) > 1e-6
                    || existing.operation() != AttributeModifier.Operation.ADD_VALUE) {
                atkAttr.removeModifier(SET_ATTACK_DAMAGE_MODIFIER_ID);
                atkAttr.addPermanentModifier(new AttributeModifier(
                        SET_ATTACK_DAMAGE_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            }
        } else if (existing != null) {
            atkAttr.removeModifier(SET_ATTACK_DAMAGE_MODIFIER_ID);
        }
    }

    public static void applyPeriodicSetEffects(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.cinderEnableSetBonus || !cfg.cinderSetBonusFireResistance || !hasFullSet(entity)) {
            return;
        }

        int duration = Math.max(40, cfg.cinderSetBonusEffectDurationTicks);
        MobEffectInstance existingFire = entity.getEffect(MobEffects.FIRE_RESISTANCE);
        if (existingFire == null || (existingFire.getAmplifier() == 0 && existingFire.getDuration() < duration)) {
            entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, duration, 0, true, false, true));
        }
    }

    /**
     * Đốt cháy mục tiêu `3s` (cấu hình `cinderSetBonusIgniteSeconds`) khi người mặc đủ 4/4 Cinder Dragon Armor
     * gây sát thương thành công lên mục tiêu phía server (tương thích Dual Knife & mọi vũ khí).
     */
    public static void onTargetHurtByAttacker(LivingEntity victim, DamageSource source) {
        if (victim == null || victim.level().isClientSide || !victim.isAlive() || source == null) {
            return;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.cinderEnableSetBonus || cfg.cinderSetBonusIgniteSeconds <= 0) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        if (hasFullSet(attacker)) {
            victim.igniteForSeconds(cfg.cinderSetBonusIgniteSeconds);
        }
    }
}

