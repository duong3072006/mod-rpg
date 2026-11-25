package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.renderer.ExcaliburGeoRenderer;
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
 * Third-Person True Two-Handed & Blade Cutting-Plane Alignment Mixin (`ItemInHandLayer`).
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD")
    )
    private void abyssalrpg$beginTogetsuEntityRender(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                     LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                     float partialTicks, float ageInTicks, float netHeadYaw,
                                                     float headPitch, CallbackInfo ci) {
        if (entity instanceof Player player) {
            if (player.getMainHandItem().is(ModItems.TOGETSU)) {
                TogetsuGeoRenderer.setCurrentRenderEntityId(player.getId());
            } else if (player.getMainHandItem().is(ModItems.EXCALIBUR)) {
                ExcaliburGeoRenderer.setCurrentRenderEntityId(player.getId());
            }
        }
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("RETURN")
    )
    private void abyssalrpg$endTogetsuEntityRender(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                   LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                   float partialTicks, float ageInTicks, float netHeadYaw,
                                                   float headPitch, CallbackInfo ci) {
        if (entity instanceof Player) {
            TogetsuGeoRenderer.setCurrentRenderEntityId(-1);
            ExcaliburGeoRenderer.setCurrentRenderEntityId(-1);
        }
    }

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
        boolean isExcalibur = stack.is(ModItems.EXCALIBUR);
        if ((!isDualKnife && !isTogetsu && !isExcalibur) || !(entity instanceof Player player)) {
            return;
        }
        if ((isDualKnife || isTogetsu) && !player.getOffhandItem().isEmpty()) {
            return;
        }
        if (isExcalibur && arm != HumanoidArm.RIGHT) {
            return;
        }
        if (isTogetsu) {
            TogetsuGeoRenderer.setCurrentRenderEntityId(player.getId());
        } else if (isExcalibur) {
            ExcaliburGeoRenderer.setCurrentRenderEntityId(player.getId());
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        FullBodyPose pose;
        if (isDualKnife) {
            pose = DualBladeAnimManager.evaluatePose(player, partialTick, 0.0F, player.tickCount + partialTick);
        } else if (isTogetsu) {
            pose = TogetsuAnimManager.evaluatePose(player, partialTick, 0.0F, player.tickCount + partialTick);
        } else {
            pose = ExcaliburAnimManager.evaluatePose(player, partialTick, 0.0F, player.tickCount + partialTick);
        }
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

        float offX = isLeft ? pose.leftBladeOffX : pose.rightBladeOffX;
        float offY = isLeft ? pose.leftBladeOffY : pose.rightBladeOffY;
        float offZ = isLeft ? pose.leftBladeOffZ : pose.rightBladeOffZ;
        if (Math.abs(offX) > 1e-4F || Math.abs(offY) > 1e-4F || Math.abs(offZ) > 1e-4F) {
            poseStack.translate(offX, offY, offZ);
        }
    }
}
