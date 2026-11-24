package com.abyssalrpg.mixin;

import com.abyssalrpg.api.BossDefenseHolder;
import com.abyssalrpg.api.BossHealthHolder;
import com.abyssalrpg.combat.BossCombatEnhancer;
import com.abyssalrpg.combat.CinderDragonArmorHandler;
import com.abyssalrpg.combat.DualBladeCombatHandler;
import com.abyssalrpg.combat.TogetsuCombatHandler;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin cho `LivingEntity`:
 * 1. Gỡ bỏ giới hạn cứng 1024.0 của `Attributes.MAX_HEALTH` cho Boss `BossHealthHolder`.
 * 2. Miễn sát thương va chạm & rơi ngắn (5–7 tick dash i-frame) khi người chơi đang phóng chiêu Chuột Phải Flash Dash.
 * 3. Áp dụng `abyssalrpg:armor_pierce` (`5/12/20%`) & `+10%` xuyên thủ của Flash Dash cho giáp vanilla
 *    (cộng dồn đồng bộ với công thức trên `BossDefenseHolder`).
 * 4. Ghi nhận sát thương thực tế sau trừ giáp/phòng thủ & kháng phép để `abyssalrpg:lifesteal` hồi máu chính xác.
 * 5. Áp dụng rút ngắn i-frame (7 ticks) độc quyền cho đòn chém trực tiếp đầy lực của Dual Blade.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyReturnValue(method = "getMaxHealth", at = @At("RETURN"))
    private float abyssalrpg$overrideBossMaxHealth(float original) {
        if (this instanceof BossHealthHolder holder) {
            float bossMax = holder.getBossMaxHealth();
            if (bossMax > 0.0f) {
                return bossMax;
            }
        }
        return original;
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void abyssalrpg$restoreBossMaxHealthBeforeVanillaHealth(CompoundTag tag, CallbackInfo ci) {
        if (this instanceof BossHealthHolder holder && tag.contains("BossMaxHealth", Tag.TAG_ANY_NUMERIC)) {
            float savedMax = tag.getFloat("BossMaxHealth");
            if (Float.isFinite(savedMax) && savedMax > 0.0f) {
                holder.setBossMaxHealth(savedMax);
            }
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void abyssalrpg$cancelDamageDuringFlashDash(DamageSource source, float amount,
                                                        CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide && (DualBladeCombatHandler.isDashInvulnerable(self, source)
                || TogetsuCombatHandler.isPinInvulnerable(self, source))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("RETURN"), cancellable = true)
    private void abyssalrpg$applyVanillaArmorPierce(DamageSource source, float damageAmount,
                                                    CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        // Boss dùng BossDefenseHolder (> 0) đã xử lý Xuyên Thủ trực tiếp trong AbstractGeoMythicBoss.computeCustomDefenseDamage
        if (self instanceof BossDefenseHolder holder && holder.getBossDefense() > 0.0f) {
            return;
        }
        float pierce = Math.max(
                Math.max(
                        DualBladeCombatHandler.getActiveArmorPierceFraction(self, source),
                        TogetsuCombatHandler.getActiveArmorPierceFraction(self, source)),
                BossCombatEnhancer.getBossArmorPierceFraction(self, source));
        float current = cir.getReturnValueF();
        if (pierce > 0.0f) {
            float armor = self.getArmorValue();
            if (armor > 0.0f) {
                float effArmor = armor * (1.0f - pierce);
                float effTough = (float) self.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * (1.0f - pierce);
                float piercedDamage = CombatRules.getDamageAfterAbsorb(self, damageAmount, source, effArmor, effTough);
                current = Math.max(current, piercedDamage);
            }
        }
        float adjusted = CinderDragonArmorHandler.applyUncappedCinderArmorMitigation(self, damageAmount, current, pierce);
        if (Math.abs(adjusted - cir.getReturnValueF()) > 1e-4f) {
            cir.setReturnValue(adjusted);
        }
    }

    @Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
    private void abyssalrpg$recordPostMitigationDamage(DamageSource source, float damageAmount,
                                                       CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        DualBladeCombatHandler.recordPostMitigationDamage(self, source, cir.getReturnValueF());
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void abyssalrpg$applyDualBladeHurtResistance(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            LivingEntity self = (LivingEntity) (Object) this;
            DualBladeCombatHandler.onLivingHurtReturn(self, source, true);
            TogetsuCombatHandler.onLivingHurtReturn(self, source, true);
            CinderDragonArmorHandler.onTargetHurtByAttacker(self, source);
            BossCombatEnhancer.onLivingHurtByBoss(self, source, amount);
        }
    }
}
