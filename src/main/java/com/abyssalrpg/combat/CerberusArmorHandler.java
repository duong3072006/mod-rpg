package com.abyssalrpg.combat;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.api.BossHealthHolder;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.CerberusArmorItem;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Xử lý Full Set Bonus 4/4 của Bộ Giáp Cerberus phía server:
 * 1. Kháng Lửa (`MobEffects.FIRE_RESISTANCE`)
 * 2. Tốc Độ I (`MobEffects.MOVEMENT_SPEED`, amp 0)
 * 3. Sức Mạnh I (`MobEffects.DAMAGE_BOOST`, amp 0)
 * 4. +4.0 Máu Tối Đa (+2 tim) qua `AttributeModifier` cố định `abyssalrpg:cerberus_armor_set_health` (`ADD_VALUE`),
 *    không cộng dồn khi relog/đổi world, gỡ ngay khi tháo 1 món + clamp máu an toàn, không chạm `BossHealthHolder`.
 * 5. Đòn đánh thiêu đốt bằng Lửa Địa Ngục (`4s`) kèm tia lửa địa ngục gọn nhẹ.
 */
public final class CerberusArmorHandler {
    public static final ResourceLocation SET_HEALTH_MODIFIER_ID =
            AbyssalRpgMod.id("cerberus_armor_set_health");

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private CerberusArmorHandler() {}

    public static int countEquippedPieces(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof CerberusArmorItem) {
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
        int interval = Math.max(1, cfg.cerberusSetBonusTickInterval);
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
        boolean shouldHaveBonus = cfg.cerberusEnableSetBonus
                && cfg.cerberusSetBonusMaxHealth > 0.0
                && hasFullSet(entity);
        AttributeModifier existing = maxHealthAttr.getModifier(SET_HEALTH_MODIFIER_ID);

        if (shouldHaveBonus) {
            double bonus = cfg.cerberusSetBonusMaxHealth;
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
        if (!cfg.cerberusEnableSetBonus || !hasFullSet(entity)) {
            return;
        }

        int duration = Math.max(40, cfg.cerberusSetBonusEffectDurationTicks);
        if (cfg.cerberusSetBonusFireResistance) {
            refreshAmbientEffect(entity, MobEffects.FIRE_RESISTANCE, duration, 0);
        }
        if (cfg.cerberusSetBonusSpeed) {
            refreshAmbientEffect(entity, MobEffects.MOVEMENT_SPEED, duration, Math.max(0, cfg.cerberusSetBonusSpeedAmplifier));
        }
        if (cfg.cerberusSetBonusStrength) {
            refreshAmbientEffect(entity, MobEffects.DAMAGE_BOOST, duration, Math.max(0, cfg.cerberusSetBonusStrengthAmplifier));
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

    /**
     * Khi người mặc đủ 4/4 Cerberus Armor đánh trúng mục tiêu phía server:
     * thiêu đốt mục tiêu bằng Lửa Địa Ngục (`cerberusSetBonusIgniteSeconds`, mặc định 4s) + tia lửa gọn nhẹ.
     */
    public static void onTargetHurtByAttacker(LivingEntity victim, DamageSource source) {
        if (victim == null || victim.level().isClientSide || !victim.isAlive() || source == null) {
            return;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.cerberusEnableSetBonus || cfg.cerberusSetBonusIgniteSeconds <= 0) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        if (!hasFullSet(attacker)) {
            return;
        }

        int targetFireTicks = cfg.cerberusSetBonusIgniteSeconds * 20;
        if (victim.getRemainingFireTicks() < targetFireTicks) {
            victim.igniteForSeconds(cfg.cerberusSetBonusIgniteSeconds);
        }
        if (victim.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.FLAME,
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

