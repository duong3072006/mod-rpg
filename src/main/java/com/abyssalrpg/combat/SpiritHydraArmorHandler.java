package com.abyssalrpg.combat;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.api.BossHealthHolder;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.SpiritHydraArmorItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
 * Xử lý Full Set Bonus 4/4 của Bộ Giáp Spirit Hydra phía server:
 * 1. Hồi Phục II (`MobEffects.REGENERATION`, amp 1): làm mới mượt mỗi `20 tick`, duration `60 tick`,
 *    `ambient = true`, `visible = false`, `showIcon = true` (0 nhấp nháy HUD, không ghi đè cấp cao hơn).
 * 2. Thở Dưới Nước (`MobEffects.WATER_BREATHING`, amp 0): cùng chu kỳ mượt.
 * 3. +4.0 Máu Tối Đa (+2 tim) qua `AttributeModifier` cố định `abyssalrpg:spirit_hydra_armor_set_health`
 *    (`ADD_VALUE`), không cộng dồn khi relog/đổi world, gỡ ngay khi tháo 1 món + clamp máu an toàn,
 *    và không tác động lên `BossHealthHolder`.
 * 4. Đòn đánh gây Độc II (`3s`) + Hồn Lửa Xanh (`3s` đốt cháy + tia lửa xanh `SOUL_FIRE_FLAME` gọn) khi đánh trúng mục tiêu.
 */
public final class SpiritHydraArmorHandler {
    public static final ResourceLocation SET_HEALTH_MODIFIER_ID =
            AbyssalRpgMod.id("spirit_hydra_armor_set_health");

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private SpiritHydraArmorHandler() {}

    public static int countEquippedPieces(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof SpiritHydraArmorItem) {
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
        int interval = Math.max(1, cfg.hydraSetBonusTickInterval);
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

    public static void syncPlayerImmediate(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        syncMaxHealthModifier(entity);
        applyPeriodicSetEffects(entity);
    }

    public static void syncMaxHealthModifier(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || entity instanceof BossHealthHolder) {
            return;
        }
        AttributeInstance maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr == null) {
            return;
        }

        ArmorConfig cfg = ArmorConfig.get();
        boolean shouldHaveBonus = cfg.hydraEnableSetBonus
                && cfg.hydraSetBonusMaxHealth > 0.0
                && hasFullSet(entity);
        AttributeModifier existing = maxHealthAttr.getModifier(SET_HEALTH_MODIFIER_ID);

        if (shouldHaveBonus) {
            double bonus = cfg.hydraSetBonusMaxHealth;
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

    public static void applyPeriodicSetEffects(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.hydraEnableSetBonus || !hasFullSet(entity)) {
            return;
        }

        int duration = Math.max(40, cfg.hydraSetBonusEffectDurationTicks);

        if (cfg.hydraSetBonusRegeneration) {
            int targetAmp = Math.max(0, cfg.hydraSetBonusRegenerationAmplifier);
            MobEffectInstance existingRegen = entity.getEffect(MobEffects.REGENERATION);
            if (existingRegen == null
                    || existingRegen.getAmplifier() < targetAmp
                    || (existingRegen.getAmplifier() == targetAmp && existingRegen.getDuration() < duration)) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, targetAmp, true, false, true));
            }
        }

        if (cfg.hydraSetBonusWaterBreathing) {
            MobEffectInstance existingWb = entity.getEffect(MobEffects.WATER_BREATHING);
            if (existingWb == null || (existingWb.getAmplifier() == 0 && existingWb.getDuration() < duration)) {
                entity.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, duration, 0, true, false, true));
            }
        }
    }

    /**
     * Khi người mặc đủ 4/4 Spirit Hydra Armor đánh trúng mục tiêu phía server:
     * - Gây Độc II (`hydraSetBonusPoisonSeconds`, mặc định 3s, không ghi đè độc mạnh/dài hơn).
     * - Thiêu đốt bằng Hồn Lửa Xanh (`hydraSetBonusSoulFlameSeconds`, mặc định 3s + tia `SOUL_FIRE_FLAME` gọn nhẹ).
     */
    public static void onTargetHurtByAttacker(LivingEntity victim, DamageSource source) {
        if (victim == null || victim.level().isClientSide || !victim.isAlive() || source == null) {
            return;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.hydraEnableSetBonus) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        if (!hasFullSet(attacker)) {
            return;
        }

        if (cfg.hydraSetBonusPoisonSeconds > 0) {
            int poisonTicks = cfg.hydraSetBonusPoisonSeconds * 20;
            int poisonAmp = Math.max(0, cfg.hydraSetBonusPoisonAmplifier);
            MobEffectInstance existing = victim.getEffect(MobEffects.POISON);
            if (existing == null
                    || existing.getAmplifier() < poisonAmp
                    || (existing.getAmplifier() == poisonAmp && existing.getDuration() < poisonTicks)) {
                victim.addEffect(new MobEffectInstance(MobEffects.POISON, poisonTicks, poisonAmp, false, true, true));
            }
        }

        if (cfg.hydraSetBonusSoulFlameSeconds > 0) {
            int targetFireTicks = cfg.hydraSetBonusSoulFlameSeconds * 20;
            if (victim.getRemainingFireTicks() < targetFireTicks) {
                victim.igniteForSeconds(cfg.hydraSetBonusSoulFlameSeconds);
            }
            if (victim.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        victim.getX(),
                        victim.getY() + victim.getBbHeight() * 0.55,
                        victim.getZ(),
                        6,
                        victim.getBbWidth() * 0.35,
                        victim.getBbHeight() * 0.25,
                        victim.getBbWidth() * 0.35,
                        0.03
                );
            }
        }
    }
}

