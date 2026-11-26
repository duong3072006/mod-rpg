package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.client.anim.MuramasaAnimManager;
import com.abyssalrpg.client.anim.SeaWeaponsAnimManager;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Full-Body Root Transform Mixin (`PlayerRenderer.setupRotations`):
 * - Áp dụng xoay toàn thân 360° (`Axis.YP`), độ nghiêng dồn lực (`Axis.XP / Axis.ZP`) và hạ trọng tâm (`translate Y`)
 *   trực tiếp lên `PoseStack` gốc của `PlayerRenderer`.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(
            method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("TAIL")
    )
    private void abyssalrpg$applyEpicFightRootTransforms(AbstractClientPlayer player, PoseStack poseStack,
                                                         float bob, float yBodyRot, float partialTick,
                                                         float scale, CallbackInfo ci) {
        FullBodyPose pose = DualBladeAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        if (pose == null) {
            pose = TogetsuAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        }
        if (pose == null) {
            pose = ExcaliburAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        }
        if (pose == null) {
            pose = SeaWeaponsAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        }
        if (pose == null) {
            pose = MuramasaAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        }
        if (pose == null) {
            return;
        }
        if (Math.abs(pose.rootTranslateY) > 1e-4F) {
            poseStack.translate(0.0, pose.rootTranslateY, 0.0);
        }
        if (Math.abs(pose.rootYawDeg) > 1e-3F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(pose.rootYawDeg));
        }
        if (Math.abs(pose.rootPitchDeg) > 1e-3F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pose.rootPitchDeg));
        }
        if (Math.abs(pose.rootRollDeg) > 1e-3F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(pose.rootRollDeg));
        }
    }
}

