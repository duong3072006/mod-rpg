package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.client.anim.KatanaAnimManager;
import com.abyssalrpg.client.anim.MuramasaAnimManager;
import com.abyssalrpg.client.anim.SeaWeaponsAnimManager;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.anim.TogetsuBentArmRig;
import com.abyssalrpg.client.renderer.CerberusArmorRenderer;
import com.abyssalrpg.item.CerberusArmorItem;
import com.abyssalrpg.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

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
        ItemStack heldMain = player.getMainHandItem();
        FullBodyPose pose = null;
        if (heldMain.is(ModItems.MURAMASA)) {
            pose = MuramasaAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        } else if (heldMain.is(ModItems.SEA_TRIDENT)) {
            pose = SeaWeaponsAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        } else if (heldMain.is(ModItems.EXCALIBUR)) {
            pose = ExcaliburAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        } else if (heldMain.is(ModItems.KATANA)) {
            pose = KatanaAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        } else if (heldMain.is(ModItems.TOGETSU)) {
            pose = TogetsuAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        } else if (heldMain.is(ModItems.DUAL_KNIFE)) {
            pose = DualBladeAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        }
        if (pose == null) {
            pose = DualBladeAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        }
        if (pose == null) {
            pose = TogetsuAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        }
        if (pose == null) {
            pose = ExcaliburAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
        }
        if (pose == null) {
            pose = KatanaAnimManager.evaluatePose(player, partialTick, 0.0F, bob);
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
        if (Math.abs(pose.rootTranslateY) > 1e-4F || Math.abs(pose.rootTranslateZ) > 1e-4F) {
            poseStack.translate(0.0, pose.rootTranslateY, pose.rootTranslateZ);
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

    @Inject(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void abyssalrpg$beginFirstPersonHandOrCerberusArm(PoseStack poseStack, MultiBufferSource buffer,
                                                              int combinedLight, AbstractClientPlayer player,
                                                              ModelPart rendererArm, ModelPart rendererArmwear,
                                                              CallbackInfo ci) {
        // Ngăn HumanoidModelMixin / PlayerModelMixin ẩn hoặc xoay cánh tay vanilla bằng rig F5 khi vẽ tay góc nhìn thứ nhất!
        TogetsuBentArmRig.setRenderingFirstPersonHand(true);
        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chestStack.getItem() instanceof CerberusArmorItem)) {
            return;
        }
        PlayerRenderer self = (PlayerRenderer) (Object) this;
        PlayerModel<AbstractClientPlayer> playerModel = self.getModel();
        boolean isRightArm = (rendererArm == playerModel.rightArm);
        Object armorRenderer = GeoRenderProvider.of(chestStack)
                .getGeoArmorRenderer(player, chestStack, EquipmentSlot.CHEST, playerModel);
        if (armorRenderer instanceof CerberusArmorRenderer cerberusRenderer) {
            try {
                playerModel.attackTime = 0.0F;
                playerModel.crouching = false;
                playerModel.swimAmount = 0.0F;
                playerModel.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
                rendererArm.xRot = 0.0F;
                cerberusRenderer.renderFirstPersonArm(
                        poseStack, buffer, combinedLight, player, playerModel, chestStack, isRightArm);
            } finally {
                TogetsuBentArmRig.setRenderingFirstPersonHand(false);
            }
            ci.cancel();
        }
    }

    @Inject(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
            at = @At("RETURN")
    )
    private void abyssalrpg$endFirstPersonHandRender(PoseStack poseStack, MultiBufferSource buffer, int combinedLight,
                                                     AbstractClientPlayer player, ModelPart rendererArm,
                                                     ModelPart rendererArmwear, CallbackInfo ci) {
        TogetsuBentArmRig.setRenderingFirstPersonHand(false);
    }
}

