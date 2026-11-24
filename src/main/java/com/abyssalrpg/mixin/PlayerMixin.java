package com.abyssalrpg.mixin;

import com.abyssalrpg.combat.DualBladeCombatHandler;
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
 * Móc nối an toàn vào `Player.attack(Entity)` cho Dual Blade:
 * - Khởi tạo ngữ cảnh đòn đánh trực tiếp (phân giải cả 4 hệ thống proxy hitbox Boss về thực thể Boss chính).
 * - Nhân sát thương `Executioner's Edge` (+10%/20%/30% dưới 40% HP) ngay trước khi gọi `Entity.hurt`.
 * - Kích hoạt Bleeding, Hemorrhage Blood Burst, Tempest Flurry, Crimson Siphon, Phantom Cross và đồng bộ combo 3 nhịp.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Unique
    private DualBladeCombatHandler.ActiveHitContext abyssalrpg$activeDualBladeHit;

    @Unique
    private TogetsuCombatHandler.ActiveHitContext abyssalrpg$activeTogetsuHit;

    @Inject(method = "attack", at = @At("HEAD"))
    private void abyssalrpg$beginDualBladeAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        this.abyssalrpg$activeDualBladeHit = DualBladeCombatHandler.beginPlayerAttack(self, target);
        this.abyssalrpg$activeTogetsuHit = TogetsuCombatHandler.beginPlayerAttack(self, target);
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
    }
}

