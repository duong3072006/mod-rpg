package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.CerberusArmorClientState;
import com.abyssalrpg.client.DualBladeClientState;
import com.abyssalrpg.client.ExcaliburClientState;
import com.abyssalrpg.client.MuramasaClientState;
import com.abyssalrpg.client.SeaWeaponsClientState;
import com.abyssalrpg.client.TogetsuClientState;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Shadow
    @Nullable
    public Screen screen;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void abyssalrpg$onDualKnifeAirAttackStart(CallbackInfoReturnable<Boolean> cir) {
        if (this.player == null || this.screen != null || this.player.isSpectator() || this.player.isHandsBusy()) {
            return;
        }
        boolean isDualKnife = this.player.getMainHandItem().is(ModItems.DUAL_KNIFE);
        boolean isTogetsu = this.player.getMainHandItem().is(ModItems.TOGETSU);
        boolean isExcalibur = this.player.getMainHandItem().is(ModItems.EXCALIBUR);
        boolean isSeaTrident = this.player.getMainHandItem().is(ModItems.SEA_TRIDENT);
        boolean isMuramasa = this.player.getMainHandItem().is(ModItems.MURAMASA);
        if (!isDualKnife && !isTogetsu && !isExcalibur && !isSeaTrident && !isMuramasa) {
            return;
        }
        if (this.hitResult == null || this.hitResult.getType() == HitResult.Type.MISS) {
            if (isDualKnife) {
                DualBladeClientState.onClientAirSwing((Minecraft) (Object) this);
            } else if (isTogetsu) {
                TogetsuClientState.onClientAirSwing((Minecraft) (Object) this);
            } else if (isExcalibur) {
                ExcaliburClientState.onClientAirSwing((Minecraft) (Object) this);
            } else if (isSeaTrident) {
                SeaWeaponsClientState.onClientAirSwing((Minecraft) (Object) this);
            } else {
                MuramasaClientState.onClientAirSwing((Minecraft) (Object) this);
            }
        }
    }

    @Inject(method = "startUseItem", at = @At("TAIL"))
    private void abyssalrpg$onEmptyHandRightClickTail(CallbackInfo ci) {
        if (this.player == null || this.screen != null || this.player.isSpectator() || this.player.isHandsBusy()) {
            return;
        }
        if (this.player.getMainHandItem().isEmpty() && this.player.getOffhandItem().isEmpty()) {
            CerberusArmorClientState.onClientEmptyHandRightClick((Minecraft) (Object) this);
        }
    }
}
