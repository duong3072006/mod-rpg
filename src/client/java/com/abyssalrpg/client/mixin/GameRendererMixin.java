package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.config.WeaponsConfig;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-Person Camera Tilt & Flash Dash FOV Mixin (`GameRenderer`):
 * 1. `bobHurt`: nghiêng camera cực nhẹ (`roll ±3°–4°`, `firstPersonCameraTilt`) theo từng nhịp chém mà không xoay 360°.
 * 2. `getFov`: co nhẹ FOV (`-6.5°`) khi nén đà Pha 1 Flash Dash & bung nhanh (`+11.5°`) lúc phóng 10 block (`dashFovEffect`).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    Minecraft minecraft;

    @Inject(method = "bobHurt", at = @At("TAIL"))
    private void abyssalrpg$applyDualBladeFirstPersonTilt(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (!WeaponsConfig.get().firstPersonCameraTilt) {
            return;
        }
        LocalPlayer player = this.minecraft.player;
        if (player == null || !this.minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        FullBodyPose pose = DualBladeAnimManager.evaluatePose(player, partialTicks, 0.0F, player.tickCount + partialTicks);
        if (pose == null) {
            return;
        }
        float rollDeg = Mth.clamp(pose.firstPersonCameraRollDeg, -4.0F, 4.0F);
        if (Math.abs(rollDeg) > 0.02F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(rollDeg));
        }
    }

    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private double abyssalrpg$applyFlashDashFov(double original, Camera activeRenderInfo, float partialTicks, boolean useFOVSetting) {
        if (!useFOVSetting || !WeaponsConfig.get().dashFovEffect) {
            return original;
        }
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return original;
        }
        FullBodyPose pose = DualBladeAnimManager.evaluatePose(player, partialTicks, 0.0F, player.tickCount + partialTicks);
        if (pose == null || Math.abs(pose.fovOffsetDeg) < 0.05F) {
            return original;
        }
        return Mth.clamp(original + pose.fovOffsetDeg, 35.0, 125.0);
    }
}
