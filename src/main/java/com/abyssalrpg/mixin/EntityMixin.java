package com.abyssalrpg.mixin;

import com.abyssalrpg.combat.ExcaliburCombatHandler;
import com.abyssalrpg.combat.KatanaCombatHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Móc nối vào {@link Entity#deflection(Projectile)} để Katana và Excalibur gạt mũi tên bay sang hướng khác
 * trước khi mũi tên gây sát thương hoặc phản ngược sát thương về phía kẻ bắn.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "deflection", at = @At("HEAD"), cancellable = true)
    private void abyssalrpg$katanaDeflectArrows(Projectile projectile,
                                                CallbackInfoReturnable<ProjectileDeflection> cir) {
        if ((Object) this instanceof Player player) {
            ProjectileDeflection deflect = KatanaCombatHandler.tryGetArrowDeflection(player, projectile);
            if (deflect == null) {
                deflect = ExcaliburCombatHandler.tryGetArrowDeflection(player, projectile);
            }
            if (deflect != null) {
                cir.setReturnValue(deflect);
            }
        }
    }
}
