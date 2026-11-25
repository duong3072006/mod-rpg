package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
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
 * First-Person Camera Tilt & Ultimate / Flash Dash FOV Mixin (`GameRenderer`).
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
            pose = TogetsuAnimManager.evaluatePose(player, partialTicks, 0.0F, player.tickCount + partialTicks);
        }
        if (pose == null) {
            pose = ExcaliburAnimManager.evaluatePose(player, partialTicks, 0.0F, player.tickCount + partialTicks);
        }
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
        if (pose == null) {
            pose = TogetsuAnimManager.evaluatePose(player, partialTicks, 0.0F, player.tickCount + partialTicks);
        }
        if (pose == null) {
            pose = ExcaliburAnimManager.evaluatePose(player, partialTicks, 0.0F, player.tickCount + partialTicks);
        }
        if (pose == null || Math.abs(pose.fovOffsetDeg) < 0.05F) {
            return original;
        }
        return Mth.clamp(original + pose.fovOffsetDeg, 35.0, 125.0);
    }
}
