package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.anim.TogetsuBentArmRig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Đồng bộ tay áo giáp ngực vanilla (`EquipmentSlot.CHEST`) gập khuỷu theo cánh tay 2 khúc khi cầm Togetsu Dual Idle!
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> {
    @Unique
    private boolean abyssalrpg$bentChestActive;

    @Unique
    private FullBodyPose abyssalrpg$bentArmorPose;

    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V",
            at = @At("HEAD")
    )
    private void abyssalrpg$beginBentChestCheck(PoseStack poseStack, MultiBufferSource buffer, T entity,
                                                EquipmentSlot slot, int packedLight, A model, CallbackInfo ci) {
        this.abyssalrpg$bentChestActive = false;
        this.abyssalrpg$bentArmorPose = null;
        if (slot == EquipmentSlot.CHEST && entity instanceof Player player) {
            FullBodyPose pose = TogetsuAnimManager.getLastComputedPose(player.getId());
            if (pose != null && pose.elbowWeight > 0.003F) {
                this.abyssalrpg$bentChestActive = true;
                this.abyssalrpg$bentArmorPose = pose;
            }
        }
    }

    @Inject(method = "setPartVisibility(Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/world/entity/EquipmentSlot;)V", at = @At("TAIL"))
    private void abyssalrpg$hideRigidChestSleeves(A model, EquipmentSlot slot, CallbackInfo ci) {
        if (this.abyssalrpg$bentChestActive && slot == EquipmentSlot.CHEST) {
            model.rightArm.visible = false;
            model.leftArm.visible = false;
        }
    }

    @Inject(
            method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/HumanoidModel;ILnet/minecraft/resources/ResourceLocation;)V",
            at = @At("TAIL")
    )
    private void abyssalrpg$renderBentChestSleeves(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                   A model, int color, ResourceLocation texture, CallbackInfo ci) {
        if (this.abyssalrpg$bentChestActive && this.abyssalrpg$bentArmorPose != null) {
            TogetsuBentArmRig.renderBentArmorSleeves(
                    poseStack, buffer.getBuffer(RenderType.armorCutoutNoCull(texture)),
                    packedLight, OverlayTexture.NO_OVERLAY, color,
                    model.rightArm, model.leftArm, this.abyssalrpg$bentArmorPose
            );
        }
    }

    @Inject(
            method = "renderGlint(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/HumanoidModel;)V",
            at = @At("TAIL")
    )
    private void abyssalrpg$renderBentChestGlint(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                 A model, CallbackInfo ci) {
        if (this.abyssalrpg$bentChestActive && this.abyssalrpg$bentArmorPose != null) {
            TogetsuBentArmRig.renderBentArmorSleeves(
                    poseStack, buffer.getBuffer(RenderType.armorEntityGlint()),
                    packedLight, OverlayTexture.NO_OVERLAY, -1,
                    model.rightArm, model.leftArm, this.abyssalrpg$bentArmorPose
            );
        }
    }

    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V",
            at = @At("RETURN")
    )
    private void abyssalrpg$endBentChestCheck(PoseStack poseStack, MultiBufferSource buffer, T entity,
                                              EquipmentSlot slot, int packedLight, A model, CallbackInfo ci) {
        this.abyssalrpg$bentChestActive = false;
        this.abyssalrpg$bentArmorPose = null;
    }
}
