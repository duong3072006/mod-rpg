package com.abyssalrpg.combat;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.api.BossHealthHolder;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.CinderDragonArmorItem;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
 * Xử lý Full Set Bonus 4/4 của Bộ Giáp Cinder Dragon phía server:
 * 1. **Kháng Lửa I** (`MobEffects.FIRE_RESISTANCE`, amp 0)
 * 2. **Sức Mạnh II** (`MobEffects.DAMAGE_BOOST`, amp 1)
 * 3. **Tốc Độ I** (`MobEffects.MOVEMENT_SPEED`, amp 0)
 * 4. **Nhảy Cao I** (`MobEffects.JUMP`, amp 0)
 * 5. **+16.0 Máu Tối Đa (+8 tim)** qua `AttributeModifier` cố định `abyssalrpg:cinder_dragon_armor_set_health`
 *    (`ADD_VALUE`), không cộng dồn khi relog/đổi world, gỡ ngay khi tháo 1 món + clamp máu an toàn, không chạm `BossHealthHolder`.
 * (Giữ thêm móc cấu hình tùy chọn `cinderSetBonusAttackDamageBonus` & `cinderSetBonusIgniteSeconds`.)
 */
public final class CinderDragonArmorHandler {
    public static final ResourceLocation SET_HEALTH_MODIFIER_ID =
            AbyssalRpgMod.id("cinder_dragon_armor_set_health");
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
            syncMaxHealthModifier(player);
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
        syncMaxHealthModifier(entity);
        syncAttackDamageModifier(entity);
        applyPeriodicSetEffects(entity);
    }

    /**
     * Gắn/gỡ sạch `AttributeModifier` `abyssalrpg:cinder_dragon_armor_set_health` (`+16.0 HP = +8 tim`, `ADD_VALUE`):
     * - Đủ 4/4 & bật config: chỉ thêm nếu chưa có (hoặc cập nhật nếu config đổi), không cộng dồn khi relog/đổi world.
     * - Thiếu bất kỳ món nào / tắt config: gỡ ngay modifier và clamp `health <= maxHealth`.
     * - Bỏ qua thực thể `BossHealthHolder`.
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
        boolean shouldHaveBonus = cfg.cinderEnableSetBonus
                && cfg.cinderSetBonusMaxHealth > 0.0
                && hasFullSet(entity);
        AttributeModifier existing = maxHealthAttr.getModifier(SET_HEALTH_MODIFIER_ID);

        if (shouldHaveBonus) {
            double bonus = cfg.cinderSetBonusMaxHealth;
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
        if (!cfg.cinderEnableSetBonus || !hasFullSet(entity)) {
            return;
        }

        int duration = Math.max(40, cfg.cinderSetBonusEffectDurationTicks);
        if (cfg.cinderSetBonusFireResistance) {
            refreshAmbientEffect(entity, MobEffects.FIRE_RESISTANCE, duration, 0);
        }
        if (cfg.cinderSetBonusResistance) {
            refreshAmbientEffect(entity, MobEffects.DAMAGE_RESISTANCE, duration, Math.max(0, cfg.cinderSetBonusResistanceAmplifier));
        }
        if (cfg.cinderSetBonusStrength) {
            refreshAmbientEffect(entity, MobEffects.DAMAGE_BOOST, duration, Math.max(0, cfg.cinderSetBonusStrengthAmplifier));
        }
        if (cfg.cinderSetBonusSpeed) {
            refreshAmbientEffect(entity, MobEffects.MOVEMENT_SPEED, duration, Math.max(0, cfg.cinderSetBonusSpeedAmplifier));
        }
        if (cfg.cinderSetBonusJump) {
            refreshAmbientEffect(entity, MobEffects.JUMP, duration, Math.max(0, cfg.cinderSetBonusJumpAmplifier));
        }
    }

    /**
     * Tính sát thương thực tế vượt ngưỡng giới hạn cứng `30` giáp / `20` toughness của Vanilla
     * cho người mặc các mảnh **Cinder Dragon Armor** (×2.5 giáp: `8/20/15/7 = 50` Armor, `7.5×4 = 30` Toughness):
     * - Tránh bị Boss đánh 80–200 dame xuyên thủng giáp như giáp vanilla!
     * - Giới hạn giảm sát thương tối đa ở `88%` (cộng thêm Đề Kháng I `-20%` khi mặc đủ 4 món -> tank cực trâu mà vẫn công bằng).
     * - Vẫn tôn trọng đầy đủ tỉ lệ xuyên giáp `pierceFraction` từ đòn đánh của kẻ địch!
     */
    public static float applyUncappedCinderArmorMitigation(LivingEntity victim, float rawDamage, float vanillaResult, float pierceFraction) {
        int pieces = countEquippedPieces(victim);
        if (pieces <= 0 || rawDamage <= 0.0F) {
            return vanillaResult;
        }
        ArmorConfig cfg = ArmorConfig.get();
        float totalArmor = 0.0F;
        float totalTough = 0.0F;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (victim.getItemBySlot(slot).getItem() instanceof CinderDragonArmorItem) {
                totalArmor += switch (slot) {
                    case HEAD -> cfg.cinderHelmetDefense;
                    case CHEST -> cfg.cinderChestplateDefense;
                    case LEGS -> cfg.cinderLeggingsDefense;
                    case FEET -> cfg.cinderBootsDefense;
                    default -> 0;
                };
                totalTough += cfg.cinderToughness;
            }
        }
        // Chỉ kích hoạt vượt ngưỡng khi tổng giáp thực của các mảnh Cinder vượt trên mốc 20 chuẩn vanilla
        if (totalArmor <= 20.0F) {
            return vanillaResult;
        }
        float keep = Math.max(0.0F, 1.0F - pierceFraction);
        float effArmor = totalArmor * keep;
        float effTough = totalTough * keep;
        if (effArmor <= 0.0F) {
            return rawDamage;
        }
        float f = 2.0F + effTough / 4.0F;
        float effectiveDef = Math.max(effArmor * 0.20F, effArmor - rawDamage / f);
        // Quy đổi giảm sát thương: mốc 20 đạt 80%, vượt lên tới 50 đạt tối đa 88% để tank Boss cực vững!
        // Đồng thời nhân trần giảm sát thương với (1 - pierceFraction) để Xuyên Giáp 36% của Boss & 100% Vorpal tác động chính xác!
        float baseReduction = Math.min(0.88F, (Math.min(effectiveDef, 20.0F) / 25.0F)
                + Math.max(0.0F, effectiveDef - 20.0F) * 0.0028F);
        float reduction = Math.min(baseReduction, 0.88F * keep);
        float mitigated = rawDamage * (1.0F - reduction);
        return Math.min(vanillaResult, mitigated);
    }

    private static void refreshAmbientEffect(LivingEntity entity, Holder<MobEffect> effect, int duration, int targetAmp) {
        MobEffectInstance existing = entity.getEffect(effect);
        // Tuyệt đối không ghi đè hiệu ứng cấp cao hơn (ví dụ Speed II từ Song Nguyệt / thuốc cấp cao hơn) hoặc thời lượng dài hơn!
        if (existing == null
                || existing.getAmplifier() < targetAmp
                || (existing.getAmplifier() == targetAmp && existing.getDuration() < duration)) {
            entity.addEffect(new MobEffectInstance(effect, duration, targetAmp, true, false, true));
        }
    }

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
