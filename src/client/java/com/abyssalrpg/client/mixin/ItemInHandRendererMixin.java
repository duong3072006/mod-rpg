package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.renderer.TogetsuGeoRenderer;
import com.abyssalrpg.combat.DualBladeMoveType;
import com.abyssalrpg.combat.TogetsuMoveType;
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
        boolean togetsuActive = mainStack.is(ModItems.TOGETSU) && player.getOffhandItem().isEmpty();

        if (!dualWieldActive && !togetsuActive) {
            original.call(instance, player, partialTicks, pitch, hand, swingProgress, stack,
                    equippedProgress, poseStack, buffer, combinedLight);
            return;
        }

        if (togetsuActive) {
            boolean isOffhand = (hand == InteractionHand.OFF_HAND);
            boolean fusedRender = TogetsuAnimManager.shouldRenderAsFused(player.getId(), mainStack, partialTicks);
            if (isOffhand && fusedRender) {
                // Dạng Đại Đao Song Nguyệt chỉ hiển thị 1 thanh Đại Đao khổng lồ duy nhất ở tay phải!
                return;
            }
            ItemStack effectiveStack = isOffhand ? mainStack : stack;
            float effectiveEquip = isOffhand
                    ? (1.0F - Mth.lerp(partialTicks, this.oMainHandHeight, this.mainHandHeight))
                    : equippedProgress;

            float alpha = TogetsuAnimManager.getActionProgress(player.getId(), partialTicks);
            TogetsuMoveType move = TogetsuAnimManager.getMoveType(player.getId());
            float sprint = TogetsuAnimManager.getSprintBlend(player.getId(), partialTicks);

            poseStack.pushPose();
            if (alpha > 0.001F) {
                abyssalrpg$applyFirstPersonTogetsuPose(poseStack, isOffhand, move, alpha);
            } else if (sprint > 0.001F) {
                float sideSign = isOffhand ? -1.0F : 1.0F;
                poseStack.translate(sideSign * 0.06F * sprint, -0.08F * sprint, 0.06F * sprint);
                poseStack.mulPose(Axis.XP.rotationDegrees(18.0F * sprint));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * -10.0F * sprint));
            }
            float effectiveSwing = (alpha > 0.001F) ? 0.0F : (isOffhand ? 0.0F : swingProgress);
            TogetsuGeoRenderer.setCurrentRenderEntityId(player.getId());
            try {
                original.call(instance, player, partialTicks, pitch, hand, effectiveSwing, effectiveStack,
                        effectiveEquip, poseStack, buffer, combinedLight);
            } finally {
                TogetsuGeoRenderer.setCurrentRenderEntityId(-1);
                poseStack.popPose();
            }
            return;
        }

        boolean isOffhand = (hand == InteractionHand.OFF_HAND);
        ItemStack effectiveStack = isOffhand ? mainStack : stack;
        float effectiveEquip = isOffhand
                ? (1.0F - Mth.lerp(partialTicks, this.oMainHandHeight, this.mainHandHeight))
                : equippedProgress;

        float comboAlpha = DualBladeAnimManager.getAttackProgress(player.getId(), partialTicks);
        DualBladeMoveType move = DualBladeAnimManager.getMoveType(player.getId());
        float narutoSprint = DualBladeAnimManager.getSprintBlend(player.getId(), partialTicks);

        poseStack.pushPose();
        if (comboAlpha > 0.001F) {
            float env = DualBladeAnimState.poseHoldEnvelope(comboAlpha);
            // Khi đang chạy Naruto và chém, đầu/cuối đòn chuyển mượt trực tiếp từ tư thế thu dao Naruto Run!
            float sprintCarry = narutoSprint * (1.0F - env);
            if (sprintCarry > 0.001F && !move.isFlashDashPhase()) {
                abyssalrpg$applyFirstPersonNarutoSprintTuck(poseStack, isOffhand, sprintCarry);
            }
            abyssalrpg$applyFirstPersonNightfallPose(poseStack, isOffhand, move, comboAlpha);
        } else if (narutoSprint > 0.001F) {
            // Đồng bộ First Person với tư thế chạy Naruto: thu nhẹ 2 thanh dao xuôi thấp về 2 bên mép dưới (0 che tâm ngắm!)
            abyssalrpg$applyFirstPersonNarutoSprintTuck(poseStack, isOffhand, narutoSprint);
        }
        // Khi đứng yên / đi bộ thường: giữ nguyên vị trí cầm kiếm cơ bản Vanilla gọn gàng ở 2 góc dưới!
        float effectiveSwing = (comboAlpha > 0.001F) ? 0.0F : (isOffhand ? 0.0F : swingProgress);
        original.call(instance, player, partialTicks, pitch, hand, effectiveSwing, effectiveStack,
                effectiveEquip, poseStack, buffer, combinedLight);
        poseStack.popPose();
    }

    @Unique
    private static void abyssalrpg$applyFirstPersonNarutoSprintTuck(PoseStack poseStack, boolean isOffhand, float sprint) {
        float sideSign = isOffhand ? -1.0F : 1.0F;
        poseStack.translate(sideSign * 0.07F * sprint, -0.09F * sprint, 0.08F * sprint);
        poseStack.mulPose(Axis.XP.rotationDegrees(20.0F * sprint));
        poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * -12.0F * sprint));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * -12.0F * sprint));
    }

    @Unique
    private static void abyssalrpg$applyFirstPersonNightfallPose(PoseStack poseStack, boolean isOffhand,
                                                                 DualBladeMoveType move, float alpha) {
        float env = DualBladeAnimState.poseHoldEnvelope(alpha);
        float sweep = DualBladeAnimState.poseHoldSweep(alpha);
        float sideSign = isOffhand ? -1.0F : 1.0F;

        // Cả 2 tay ở góc nhìn Thứ 1 đều xuất phát mượt từ tư thế cầm kiếm cơ bản đối xứng của Vanilla
        // (Tay phải góc dưới–phải Đỏ Huyết, Tay trái góc dưới–trái Xanh Kim Cương) rồi trả sạch về vị trí gốc!
        switch (move) {
            case COMBO_1 -> {
                // Nhịp 1: Dao phải (Đỏ Huyết) chém chéo rộng từ phải–trên xuống trái–dưới; dao trái giữ thế thu gọn!
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(sweep, 0.20F, -0.82F) * env, Mth.lerp(sweep, 0.22F, -0.26F) * env, -0.30F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -28.0F, 82.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(66.0F * env));
                } else {
                    poseStack.translate(-0.10F * env, -0.06F * env, 0.10F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(14.0F * env));
                }
            }
            case COMBO_2 -> {
                // Nhịp 2: Dao trái (Xanh Kim Cương) chém chéo đối xứng từ trái–trên sang phải–dưới; dao phải thu gọn!
                if (isOffhand) {
                    poseStack.translate(Mth.lerp(sweep, -0.20F, 0.82F) * env, Mth.lerp(sweep, 0.22F, -0.26F) * env, -0.30F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, 28.0F, -82.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-66.0F * env));
                } else {
                    poseStack.translate(0.10F * env, -0.06F * env, 0.10F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(14.0F * env));
                }
            }
            case COMBO_3 -> {
                // Nhịp 3 (Twin Opposing Double-Arc): Dao phải cắt chéo thấp + dao trái quét chéo cao song hành!
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(sweep, 0.24F, -0.74F) * env, Mth.lerp(sweep, 0.16F, -0.28F) * env, -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -32.0F, 78.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(72.0F * env));
                } else {
                    poseStack.translate(Mth.lerp(sweep, -0.24F, 0.74F) * env, Mth.lerp(sweep, -0.10F, 0.26F) * env, -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, 32.0F, -78.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-64.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-72.0F * env));
                }
            }
            case COMBO_4 -> {
                // Nhịp 4 (360° Cyclone): 2 dao nằm ngang đối xứng quét vòng lốc xoáy rộng toàn khung nhìn!
                float spin = DualBladeAnimState.cyclone360Spin(alpha);
                float cyc = Mth.sin(spin * Mth.TWO_PI);
                poseStack.translate(sideSign * (-0.30F * env + 0.20F * cyc), -0.14F * env, -0.32F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * (52.0F * env + 38.0F * cyc)));
                poseStack.mulPose(Axis.XP.rotationDegrees(-76.0F * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 78.0F * env));
            }
            case COMBO_5 -> {
                // Nhịp 5 Finisher (X-Rend Slam): Nhấc nhẹ cả 2 dao lên hai bên rồi bổ chéo đối xứng khóa chữ X!
                float windup = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.24F, 0.0F, 1.0F));
                float slam = alpha < 0.22F ? 0.0F : (alpha < 0.46F ? DualBladeAnimState.easeOutQuart((alpha - 0.22F) / 0.24F) : 1.0F);
                poseStack.translate(
                        sideSign * Mth.lerp(slam, 0.22F * windup, -0.58F) * env,
                        Mth.lerp(slam, 0.26F * windup, -0.30F) * env,
                        -0.36F * slam * env
                );
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(slam, -24.0F * windup, 64.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(slam, 22.0F * windup, -78.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * Mth.lerp(slam, -18.0F * windup, 56.0F) * env));
            }
            case SPRINT_THRUST -> {
                // Sprint Opening Cross-Slash (First Person): 2 dao vung chém chéo lệch pha mượt mà từ thế cầm cơ bản
                // (Tay phải Đỏ–Đen chém chéo trước, Tay trái Đen–Bạc quét nối tiếp ngay sau -> đồng bộ 1:1 với F5 & Ribbon!)
                float slashR = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.36F, 0.0F, 1.0F));
                float slashL = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.14F) / 0.38F, 0.0F, 1.0F));
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(slashR, 0.18F, -0.72F) * env, Mth.lerp(slashR, 0.14F, -0.22F) * env, -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(slashR, -24.0F, 72.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-64.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(60.0F * env));
                } else {
                    float lEnv = env * DualBladeAnimState.easeOutCubic(Mth.clamp((alpha - 0.08F) / 0.18F, 0.0F, 1.0F));
                    poseStack.translate(Mth.lerp(slashL, -0.18F, 0.72F) * lEnv, Mth.lerp(slashL, 0.10F, -0.16F) * lEnv, -0.34F * lEnv);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(slashL, 24.0F, -72.0F) * lEnv));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-62.0F * lEnv));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-60.0F * lEnv));
                }
            }
            case FLASH_DASH_CHARGE -> {
                // Pha 1 Flash Dash (First Person): kéo thấp gọn 2 thanh dao nén đà sát mép dưới–hai bên màn hình!
                float coil = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.45F, 0.0F, 1.0F));
                poseStack.translate(sideSign * 0.16F * coil, -0.22F * coil, 0.18F * coil);
                poseStack.mulPose(Axis.XP.rotationDegrees(30.0F * coil));
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * -18.0F * coil));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * -14.0F * coil));
            }
            case FLASH_DASH_LAUNCH -> {
                // Pha 2 Flash Dash (First Person, 15 block): 2 lưỡi song sắc nghiêng sát hai bên xé gió siêu tốc!
                poseStack.translate(sideSign * 0.18F, -0.20F, 0.22F);
                poseStack.mulPose(Axis.XP.rotationDegrees(36.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * -22.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * -16.0F));
            }
            case FLASH_DASH_END -> {
                // Pha 3 Flash Dash (First Person): vung chéo 2 dao Đỏ Huyết + Xanh Kim Cương khóa chữ X dứt khoát giữa màn hình!
                float snap = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.34F, 0.0F, 1.0F));
                poseStack.translate(
                        sideSign * Mth.lerp(snap, 0.18F, -0.46F) * env,
                        Mth.lerp(snap, -0.20F, -0.10F) * env,
                        Mth.lerp(snap, 0.18F, -0.34F) * env
                );
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(snap, -20.0F, 62.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(snap, 26.0F, -72.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * Mth.lerp(snap, -14.0F, 56.0F) * env));
            }
            default -> {
                // Air Slash / Landing Horizontal Follow-Up
                poseStack.translate(sideSign * Mth.lerp(sweep, 0.22F, -0.66F) * env, -0.12F * env, -0.30F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(sweep, -24.0F, 72.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 64.0F * env));
            }
        }
    }

    @Unique
    private static void abyssalrpg$applyFirstPersonTogetsuPose(PoseStack poseStack, boolean isOffhand,
                                                               TogetsuMoveType move, float alpha) {
        float env = DualBladeAnimState.poseHoldEnvelope(alpha);
        float sweep = DualBladeAnimState.poseHoldSweep(alpha);
        float sideSign = isOffhand ? -1.0F : 1.0F;

        switch (move) {
            case DUAL_1 -> {
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(sweep, 0.22F, -0.82F) * env, Mth.lerp(sweep, 0.20F, -0.26F) * env, -0.32F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -28.0F, 82.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(64.0F * env));
                } else {
                    poseStack.translate(-0.10F * env, -0.06F * env, 0.10F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(14.0F * env));
                }
            }
            case DUAL_2 -> {
                if (isOffhand) {
                    poseStack.translate(Mth.lerp(sweep, -0.22F, 0.82F) * env, Mth.lerp(sweep, 0.20F, -0.26F) * env, -0.32F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, 28.0F, -82.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-64.0F * env));
                } else {
                    poseStack.translate(0.10F * env, -0.06F * env, 0.10F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(14.0F * env));
                }
            }
            case DUAL_3 -> {
                poseStack.translate(sideSign * Mth.lerp(sweep, 0.24F, -0.74F) * env,
                        (isOffhand ? Mth.lerp(sweep, -0.10F, 0.26F) : Mth.lerp(sweep, 0.18F, -0.26F)) * env,
                        -0.34F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(sweep, -30.0F, 78.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(-66.0F * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 70.0F * env));
            }
            case DUAL_4 -> {
                float wind = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.26F, 0.0F, 1.0F));
                float slam = alpha < 0.24F ? 0.0F : (alpha < 0.50F ? DualBladeAnimState.easeOutQuart((alpha - 0.24F) / 0.26F) : 1.0F);
                poseStack.translate(sideSign * Mth.lerp(slam, 0.22F * wind, -0.56F) * env,
                        Mth.lerp(slam, 0.28F * wind, -0.32F) * env, -0.36F * slam * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(slam, -24.0F * wind, 66.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(slam, 22.0F * wind, -78.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * Mth.lerp(slam, -18.0F * wind, 58.0F) * env));
            }
            case FUSE_TRANSFORM -> {
                // Kéo 2 cây đao vào giữa màn hình ghép khớp ở 50% rồi nâng Đại Đao Song Nguyệt rực sáng!
                if (alpha < 0.50F) {
                    float t = DualBladeAnimState.easeInOutCubic(alpha / 0.50F);
                    poseStack.translate(sideSign * -0.44F * t, -0.04F * t, -0.26F * t);
                    poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * 52.0F * t));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 42.0F * t));
                } else {
                    float t = DualBladeAnimState.easeOutCubic((alpha - 0.50F) / 0.50F);
                    poseStack.translate(Mth.lerp(t, -0.32F, 0.0F), Mth.lerp(t, 0.14F, 0.0F), Mth.lerp(t, -0.22F, 0.0F));
                    poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(t, -36.0F, 0.0F)));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(t, 28.0F, 0.0F)));
                }
            }
            case UNFUSE_SPLIT -> {
                float t = Mth.sin(alpha * Mth.PI);
                poseStack.translate(sideSign * -0.28F * t, -0.05F * t, -0.18F * t);
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 36.0F * t));
            }
            case FUSED_1 -> {
                float wind = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.24F, 0.0F, 1.0F));
                float cut = alpha < 0.20F ? 0.0F : (alpha < 0.50F ? DualBladeAnimState.easeOutQuart((alpha - 0.20F) / 0.30F) : 1.0F);
                poseStack.translate(Mth.lerp(cut, 0.24F * wind, -0.78F) * env,
                        Mth.lerp(cut, 0.34F * wind, -0.38F) * env, -0.34F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cut, -22.0F * wind, 72.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(cut, 28.0F * wind, -76.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(cut, -18.0F * wind, 62.0F) * env));
            }
            case FUSED_2 -> {
                poseStack.translate(Mth.lerp(sweep, -0.68F, 0.42F) * env, Mth.lerp(sweep, -0.22F, 0.14F) * env, -0.36F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, 76.0F, -54.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(-74.0F * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(-74.0F * env));
            }
            case FUSED_3 -> {
                poseStack.translate(Mth.lerp(sweep, 0.32F, -0.66F) * env, Mth.lerp(sweep, -0.34F, 0.32F) * env, -0.34F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -36.0F, 68.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(sweep, -18.0F, -78.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(54.0F * env));
            }
            case CRESCENT_VORPAL_CHARGE -> {
                // Gồng lực rút kiếm Zenitsu 3s góc nhìn Thứ 1: thu Đại Đao đúng chiều về hông dưới-trái, rung tụ điện tăng dần!
                float enter = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.16F, 0.0F, 1.0F));
                float vib = Mth.sin(alpha * 135.0F) * (0.006F + 0.018F * alpha);
                poseStack.translate(-0.56F * enter + vib, 0.02F * enter - 0.06F * alpha + vib * 0.5F, -0.10F * enter);
                poseStack.mulPose(Axis.YP.rotationDegrees(36.0F * enter));
                poseStack.mulPose(Axis.XP.rotationDegrees(-14.0F * enter));
                poseStack.mulPose(Axis.ZP.rotationDegrees(46.0F * enter + vib * 140.0F));
            }
            case CRESCENT_VORPAL -> {
                // Bùng nổ rút kiếm Iaijutsu ngang toàn màn hình từ Trái -> Phải sau 3s gồng!
                float cut = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.26F, 0.0F, 1.0F));
                float rec = alpha < 0.65F ? 1.0F : (1.0F - DualBladeAnimState.easeInOutCubic((alpha - 0.65F) / 0.35F));
                poseStack.translate(Mth.lerp(cut, -0.56F, 0.72F) * rec, Mth.lerp(cut, -0.04F, 0.14F) * rec, Mth.lerp(cut, -0.10F, -0.44F) * rec);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cut, 36.0F, -76.0F) * rec));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(cut, -14.0F, -64.0F) * rec));
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(cut, 46.0F, -68.0F) * rec));
            }
            case DUAL_PIN_LUNGE -> {
                float t = DualBladeAnimState.easeOutCubic(alpha);
                poseStack.translate(sideSign * 0.14F * t, -0.22F * t, -0.28F * t);
                poseStack.mulPose(Axis.XP.rotationDegrees(-48.0F * t));
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * 14.0F * t));
            }
            case DUAL_PIN_FLURRY -> {
                // 12 nhát đâm so le cực tốc trước mặt ở góc nhìn Thứ 1!
                float endFade = alpha > 0.88F ? (1.0F - (alpha - 0.88F) / 0.12F) : 1.0F;
                float wave = Mth.sin(alpha * Mth.TWO_PI * 6.0F);
                float thrust = Math.max(0.0F, isOffhand ? -wave : wave);
                poseStack.translate(
                        sideSign * Mth.lerp(thrust, 0.14F, -0.22F) * endFade,
                        Mth.lerp(thrust, -0.12F, -0.26F) * endFade,
                        Mth.lerp(thrust, 0.14F, -0.68F) * endFade
                );
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(thrust, -24.0F, -84.0F) * endFade));
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(thrust, -10.0F, 26.0F) * endFade));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 16.0F * endFade));
            }
        }
    }
}
