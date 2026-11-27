package com.abyssalrpg.mixin;

import com.abyssalrpg.combat.DualBladeCombatHandler;
import com.abyssalrpg.combat.ExcaliburCombatHandler;
import com.abyssalrpg.combat.KatanaCombatHandler;
import com.abyssalrpg.combat.MuramasaCombatHandler;
import com.abyssalrpg.combat.SeaWeaponsCombatHandler;
import com.abyssalrpg.combat.TogetsuCombatHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Móc nối an toàn vào `Player.attack(Entity)` cho Dual Blade, Togetsu, Excalibur & Katana.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Unique
    private DualBladeCombatHandler.ActiveHitContext abyssalrpg$activeDualBladeHit;

    @Unique
    private TogetsuCombatHandler.ActiveHitContext abyssalrpg$activeTogetsuHit;

    @Unique
    private ExcaliburCombatHandler.ActiveHitContext abyssalrpg$activeExcaliburHit;

    @Unique
    private SeaWeaponsCombatHandler.ActiveHitContext abyssalrpg$activeSeaHit;

    @Unique
    private MuramasaCombatHandler.ActiveHitContext abyssalrpg$activeMuramasaHit;

    @Unique
    private KatanaCombatHandler.ActiveHitContext abyssalrpg$activeKatanaHit;

    @Inject(method = "attack", at = @At("HEAD"))
    private void abyssalrpg$beginDualBladeAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        this.abyssalrpg$activeDualBladeHit = DualBladeCombatHandler.beginPlayerAttack(self, target);
        this.abyssalrpg$activeTogetsuHit = TogetsuCombatHandler.beginPlayerAttack(self, target);
        this.abyssalrpg$activeExcaliburHit = ExcaliburCombatHandler.beginPlayerAttack(self, target);
        this.abyssalrpg$activeSeaHit = SeaWeaponsCombatHandler.beginPlayerAttack(self, target);
        this.abyssalrpg$activeMuramasaHit = MuramasaCombatHandler.beginPlayerAttack(self, target);
        this.abyssalrpg$activeKatanaHit = KatanaCombatHandler.beginPlayerAttack(self, target);
    }

    @ModifyArg(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            ),
            index = 1
    )
    private float abyssalrpg$scaleDualBladeAttackDamage(float amount) {
        if (this.abyssalrpg$activeDualBladeHit != null) {
            return DualBladeCombatHandler.modifyAttackDamage(amount);
        }
        if (this.abyssalrpg$activeTogetsuHit != null) {
            return TogetsuCombatHandler.modifyAttackDamage(amount);
        }
        if (this.abyssalrpg$activeExcaliburHit != null) {
            return ExcaliburCombatHandler.modifyAttackDamage(amount);
        }
        if (this.abyssalrpg$activeSeaHit != null) {
            return SeaWeaponsCombatHandler.modifyAttackDamage(amount);
        }
        if (this.abyssalrpg$activeMuramasaHit != null) {
            return MuramasaCombatHandler.modifyAttackDamage(amount);
        }
        if (this.abyssalrpg$activeKatanaHit != null) {
            return KatanaCombatHandler.modifyAttackDamage(amount);
        }
        return amount;
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void abyssalrpg$finishDualBladeAttack(Entity target, CallbackInfo ci) {
        if (this.abyssalrpg$activeDualBladeHit != null) {
            DualBladeCombatHandler.ActiveHitContext ctx = this.abyssalrpg$activeDualBladeHit;
            this.abyssalrpg$activeDualBladeHit = null;
            DualBladeCombatHandler.finishPlayerAttack(ctx);
        }
        if (this.abyssalrpg$activeTogetsuHit != null) {
            TogetsuCombatHandler.ActiveHitContext ctx = this.abyssalrpg$activeTogetsuHit;
            this.abyssalrpg$activeTogetsuHit = null;
            TogetsuCombatHandler.finishPlayerAttack(ctx);
        }
        if (this.abyssalrpg$activeExcaliburHit != null) {
            ExcaliburCombatHandler.ActiveHitContext ctx = this.abyssalrpg$activeExcaliburHit;
            this.abyssalrpg$activeExcaliburHit = null;
            ExcaliburCombatHandler.finishPlayerAttack(ctx);
        }
        if (this.abyssalrpg$activeSeaHit != null) {
            SeaWeaponsCombatHandler.ActiveHitContext ctx = this.abyssalrpg$activeSeaHit;
            this.abyssalrpg$activeSeaHit = null;
            SeaWeaponsCombatHandler.finishPlayerAttack(ctx);
        }
        if (this.abyssalrpg$activeMuramasaHit != null) {
            MuramasaCombatHandler.ActiveHitContext ctx = this.abyssalrpg$activeMuramasaHit;
            this.abyssalrpg$activeMuramasaHit = null;
            MuramasaCombatHandler.finishPlayerAttack(ctx);
        }
        if (this.abyssalrpg$activeKatanaHit != null) {
            KatanaCombatHandler.ActiveHitContext ctx = this.abyssalrpg$activeKatanaHit;
            this.abyssalrpg$activeKatanaHit = null;
            KatanaCombatHandler.finishPlayerAttack(ctx);
        }
    }
}

