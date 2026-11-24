package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.renderer.TogetsuGeoRenderer;
import com.abyssalrpg.registry.ModItems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-Person True Two-Handed & Blade Cutting-Plane Alignment Mixin (`ItemInHandLayer`):
 * - Tự động nhân bản thanh song đao sang tay trái khi tay phụ trống (ẩn khi cầm khiên/totem/block).
 * - Căn góc mặt lưỡi song đao khớp 100% với `DualBladeAnimManager` (nằm phẳng ngang trong cú 360° Cyclone,
 *   nghiêng chữ X ở Nhịp 4 Finisher, xòe chéo sau lưng ở tư thế thủ đứng yên).
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @WrapOperation(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack abyssalrpg$mirrorDualBladeToEmptyOffhand(LivingEntity entity, Operation<ItemStack> original) {
        ItemStack realOffhand = original.call(entity);
        if (realOffhand.isEmpty()) {
            ItemStack main = entity.getMainHandItem();
            if (main.is(ModItems.DUAL_KNIFE)) {
                return main;
            }
            if (main.is(ModItems.TOGETSU) && entity instanceof Player player) {
                float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                if (TogetsuAnimManager.shouldMirrorOffhand(player, main, pt)) {
                    return main;
                }
            }
        }
        return realOffhand;
    }

    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            )
    )
    private void abyssalrpg$alignDualBladeCuttingPlane(LivingEntity entity, ItemStack stack,
                                                       ItemDisplayContext displayContext, HumanoidArm arm,
                                                       PoseStack poseStack, MultiBufferSource buffer,
                                                       int packedLight, CallbackInfo ci) {
        boolean isDualKnife = stack.is(ModItems.DUAL_KNIFE);
        boolean isTogetsu = stack.is(ModItems.TOGETSU);
        if ((!isDualKnife && !isTogetsu) || !(entity instanceof Player player) || !player.getOffhandItem().isEmpty()) {
            return;
        }
        if (isTogetsu) {
            TogetsuGeoRenderer.setCurrentRenderEntityId(player.getId());
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        FullBodyPose pose = isDualKnife
                ? DualBladeAnimManager.evaluatePose(player, partialTick, 0.0F, player.tickCount + partialTick)
                : TogetsuAnimManager.evaluatePose(player, partialTick, 0.0F, player.tickCount + partialTick);
        if (pose == null) {
            return;
        }

        boolean isLeft = (arm == HumanoidArm.LEFT);
        float pitch = isLeft ? pose.leftBladePitchDeg : pose.rightBladePitchDeg;
        float roll = isLeft ? pose.leftBladeRollDeg : pose.rightBladeRollDeg;
        float yaw = isLeft ? pose.leftBladeYawDeg : pose.rightBladeYawDeg;

        if (Math.abs(pitch) > 0.05F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }
        if (Math.abs(roll) > 0.05F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        }
        if (Math.abs(yaw) > 0.05F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        }
    }

    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    shift = At.Shift.AFTER
            )
    )
    private void abyssalrpg$resetTogetsuRenderEntity(LivingEntity entity, ItemStack stack,
                                                     ItemDisplayContext displayContext, HumanoidArm arm,
                                                     PoseStack poseStack, MultiBufferSource buffer,
                                                     int packedLight, CallbackInfo ci) {
        if (stack.is(ModItems.TOGETSU)) {
            TogetsuGeoRenderer.setCurrentRenderEntityId(-1);
        }
    }
}
