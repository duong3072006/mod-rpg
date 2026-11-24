package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState;
import com.abyssalrpg.combat.DualBladeMoveType;
import com.abyssalrpg.registry.ModItems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * First-Person 5-Hit Pose Hold + Sprint Thrust + Air Slash Mixin (`ItemInHandRenderer` v2):
 * - Nhân bản cặp song đao 2 tay khi tay phụ trống.
 * - Tăng mạnh biên độ vung + đồng bộ 3 pha Pose Hold (`0%->40%` bùng nổ, `40%->80%` giữ dáng rõ, `80%->100%` thu)
 *   để cả 5 nhịp phân biệt tức thì ngay cả ở góc nhìn Thứ 1 không cần bật F5!
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @WrapOperation(
            method = "renderHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            )
    )
    private void abyssalrpg$renderDualBladeBothHands(
            ItemInHandRenderer instance,
            AbstractClientPlayer player,
            float partialTicks,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equippedProgress,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int combinedLight,
            Operation<Void> original
    ) {
        ItemStack mainStack = player.getMainHandItem();
        boolean dualWieldActive = mainStack.is(ModItems.DUAL_KNIFE) && player.getOffhandItem().isEmpty();

        if (!dualWieldActive) {
            original.call(instance, player, partialTicks, pitch, hand, swingProgress, stack,
                    equippedProgress, poseStack, buffer, combinedLight);
            return;
        }

        boolean isOffhand = (hand == InteractionHand.OFF_HAND);
        ItemStack effectiveStack = isOffhand ? mainStack : stack;
        float effectiveEquip = isOffhand
                ? (1.0F - Mth.lerp(partialTicks, this.oMainHandHeight, this.mainHandHeight))
                : equippedProgress;

        float comboAlpha = DualBladeAnimManager.getAttackProgress(player.getId(), partialTicks);
        DualBladeMoveType move = DualBladeAnimManager.getMoveType(player.getId());

        poseStack.pushPose();
        if (comboAlpha > 0.001F) {
            abyssalrpg$applyFirstPersonNightfallPose(poseStack, isOffhand, move, comboAlpha);
        } else if (isOffhand) {
            // Góc nhìn Thứ 1 — Tầng TRÊN tay trái: góc trên-trái, lưỡi nghiêng chéo xuống-trái sát rìa (0 che tâm ngắm!)
            poseStack.translate(-0.16F, 0.14F, -0.06F);
            poseStack.mulPose(Axis.YP.rotationDegrees(-16.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(14.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-28.0F));
        } else {
            // Góc nhìn Thứ 1 — Tầng DƯỚI tay phải: góc dưới-phải, hướng ngang sang phải mũi hơi vểnh lên (0 che tâm ngắm!)
            poseStack.translate(0.14F, -0.15F, -0.05F);
            poseStack.mulPose(Axis.YP.rotationDegrees(-26.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-12.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-42.0F));
        }
        // Tắt hoàn toàn vanilla swingProgress khi animation Dual Blade đang hoạt động để tránh triệt tiêu biên độ!
        float effectiveSwing = (comboAlpha > 0.001F) ? 0.0F : (isOffhand ? 0.0F : swingProgress);
        original.call(instance, player, partialTicks, pitch, hand, effectiveSwing, effectiveStack,
                effectiveEquip, poseStack, buffer, combinedLight);
        poseStack.popPose();
    }

    @Unique
    private static void abyssalrpg$applyFirstPersonNightfallPose(PoseStack poseStack, boolean isOffhand,
                                                                 DualBladeMoveType move, float alpha) {
        float env = DualBladeAnimState.poseHoldEnvelope(alpha);
        float sweep = DualBladeAnimState.poseHoldSweep(alpha);
        float sideSign = isOffhand ? -1.0F : 1.0F;

        switch (move) {
            case COMBO_1 -> {
                // Nhịp 1: Đao phải (Standard Grip) vắt chéo rộng toàn màn hình từ phải-trên xuống trái-dưới + giữ dáng!
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(sweep, 0.30F, -1.02F) * env, Mth.lerp(sweep, 0.24F, -0.34F) * env, -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -36.0F, 98.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-74.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(76.0F * env));
                } else {
                    poseStack.translate(-0.14F * env, 0.04F * env, 0.14F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-16.0F * env));
                }
            }
            case COMBO_2 -> {
                // Nhịp 2: Đao trái Cầm Ngược (Reverse Grip) vung chém backhand sắc lạnh từ giữa-phải quét bung rộng sang trái!
                if (isOffhand) {
                    poseStack.translate(Mth.lerp(sweep, 0.72F, -0.44F) * env, Mth.lerp(sweep, 0.18F, -0.10F) * env, -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -76.0F, 64.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(26.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-72.0F * env));
                } else {
                    poseStack.translate(0.14F * env, -0.10F * env, 0.16F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(22.0F * env));
                }
            }
            case COMBO_3 -> {
                // Nhịp 3 (Twin Opposing Double-Arc): Đao phải thuận cắt thấp chéo + đao trái ngược quét backhand cao đối pha!
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(sweep, 0.34F, -0.84F) * env, Mth.lerp(sweep, 0.20F, -0.32F) * env, -0.38F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -40.0F, 88.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-74.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(82.0F * env));
                } else {
                    poseStack.translate(Mth.lerp(sweep, 0.56F, -0.36F) * env, Mth.lerp(sweep, -0.06F, 0.28F) * env, -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -58.0F, 52.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(22.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-76.0F * env));
                }
            }
            case COMBO_4 -> {
                // Nhịp 4 (360° Cyclone): 2 đao (1 thuận + 1 ngược) nằm ngang quét lốc xoáy rộng toàn khung nhìn!
                float spin = DualBladeAnimState.cyclone360Spin(alpha);
                float cyc = Mth.sin(spin * Mth.TWO_PI);
                poseStack.translate(sideSign * (-0.38F * env + 0.24F * cyc), (isOffhand ? -0.08F : -0.22F) * env, -0.38F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * (58.0F * env + 44.0F * cyc)));
                poseStack.mulPose(Axis.XP.rotationDegrees((isOffhand ? 20.0F : -84.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 84.0F * env));
            }
            case COMBO_5 -> {
                // Nhịp 5 Finisher (Asymmetric X-Rend Slam): Đao phải thuận bổ mạnh + đao trái ngược xé chéo khóa chữ X!
                float windup = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.24F, 0.0F, 1.0F));
                float slam = alpha < 0.22F ? 0.0F : (alpha < 0.46F ? DualBladeAnimState.easeOutQuart((alpha - 0.22F) / 0.24F) : 1.0F);
                poseStack.translate(
                        sideSign * Mth.lerp(slam, 0.30F * windup, -0.68F) * env,
                        Mth.lerp(slam, 0.32F * windup, isOffhand ? -0.24F : -0.40F) * env,
                        -0.40F * slam * env
                );
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(slam, -28.0F * windup, 72.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(slam, 28.0F * windup, isOffhand ? 24.0F : -86.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * Mth.lerp(slam, -22.0F * windup, 60.0F) * env));
            }
            case SPRINT_THRUST -> {
                // Sprint Twin Flash-Thrust: đâm lao thẳng 2 nhịp về trước
                float thrust = Mth.sin(Math.min(1.0F, alpha / 0.65F) * Mth.TWO_PI);
                float phase = isOffhand ? -thrust : thrust;
                poseStack.translate(sideSign * -0.14F * env, (isOffhand ? -0.02F : -0.12F) * env, (-0.60F * env - 0.16F * phase));
                poseStack.mulPose(Axis.XP.rotationDegrees((isOffhand ? 18.0F : -86.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 16.0F * env));
            }
            case FLASH_DASH_CHARGE -> {
                // Pha 1 Flash Dash (First Person): kéo thấp 2 thanh Netherite–Kim Cương nén đà sát mép dưới-sau!
                float coil = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.45F, 0.0F, 1.0F));
                poseStack.translate(sideSign * 0.24F * coil, (isOffhand ? -0.26F : -0.40F) * coil, 0.26F * coil);
                poseStack.mulPose(Axis.XP.rotationDegrees((isOffhand ? 18.0F : 42.0F) * coil));
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * -26.0F * coil));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * -22.0F * coil));
            }
            case FLASH_DASH_LAUNCH -> {
                // Pha 2 Flash Dash (First Person, 18 block): 2 lưỡi xé gió siêu tốc hai bên mép màn hình!
                poseStack.translate(sideSign * 0.28F, isOffhand ? -0.22F : -0.36F, 0.32F);
                poseStack.mulPose(Axis.XP.rotationDegrees(isOffhand ? 24.0F : 52.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * -30.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * -24.0F));
            }
            case FLASH_DASH_END -> {
                // Pha 3 Flash Dash (First Person): vung chéo 2 kiếm Netherite–Kim Cương Xanh khóa chữ X dứt khoát giữa màn hình!
                float snap = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.34F, 0.0F, 1.0F));
                poseStack.translate(
                        sideSign * Mth.lerp(snap, 0.24F, -0.52F) * env,
                        Mth.lerp(snap, -0.26F, isOffhand ? -0.02F : -0.12F) * env,
                        Mth.lerp(snap, 0.22F, -0.38F) * env
                );
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(snap, -26.0F, 68.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(snap, 32.0F, isOffhand ? 22.0F : -76.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * Mth.lerp(snap, -18.0F, 62.0F) * env));
            }
            default -> {
                // Air Slash / Landing Horizontal Follow-Up
                poseStack.translate(sideSign * Mth.lerp(sweep, 0.28F, -0.74F) * env, (isOffhand ? -0.06F : -0.16F) * env, -0.34F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(sweep, -28.0F, 78.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees((isOffhand ? 22.0F : -74.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 70.0F * env));
            }
        }
    }
}
