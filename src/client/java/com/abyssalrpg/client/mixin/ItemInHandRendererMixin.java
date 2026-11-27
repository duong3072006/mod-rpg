package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState;
import com.abyssalrpg.client.anim.KatanaAnimManager;
import com.abyssalrpg.client.anim.MuramasaAnimManager;
import com.abyssalrpg.client.anim.SeaWeaponsAnimManager;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.renderer.ExcaliburFirstPersonRigRenderer;
import com.abyssalrpg.client.renderer.ExcaliburGeoRenderer;
import com.abyssalrpg.client.renderer.KatanaItemRenderer;
import com.abyssalrpg.client.renderer.MuramasaGeoRenderer;
import com.abyssalrpg.client.renderer.SeaTridentGeoRenderer;
import com.abyssalrpg.client.renderer.TogetsuGeoRenderer;
import com.abyssalrpg.combat.DualBladeMoveType;
import com.abyssalrpg.combat.MuramasaMoveType;
import com.abyssalrpg.combat.SeaWeaponsMoveType;
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
 * First-Person 5-Hit Pose Hold + Sprint Thrust + Air Slash Mixin
 * (`ItemInHandRenderer` v2):
 * - Nhân bản cặp song đao 2 tay khi tay phụ trống.
 * - Tăng mạnh biên độ vung + đồng bộ 3 pha Pose Hold (`0%->40%` bùng nổ,
 * `40%->80%` giữ dáng rõ, `80%->100%` thu)
 * để cả 5 nhịp phân biệt tức thì ngay cả ở góc nhìn Thứ 1 không cần bật F5!
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Unique
    private boolean abyssalrpg$wasHoldingCustomWeapon;

    @Unique
    private boolean abyssalrpg$wasHoldingExcalibur;

    @Unique
    private boolean abyssalrpg$savedBob;

    @Unique
    private float abyssalrpg$savedXBobO;

    @Unique
    private float abyssalrpg$savedXBob;

    @Unique
    private float abyssalrpg$savedYBobO;

    @Unique
    private float abyssalrpg$savedYBob;

    @Unique
    private static boolean abyssalrpg$isCustomWeapon(ItemStack stack) {
        return stack.is(ModItems.DUAL_KNIFE)
                || stack.is(ModItems.TOGETSU)
                || stack.is(ModItems.EXCALIBUR)
                || stack.is(ModItems.KATANA)
                || stack.is(ModItems.SEA_TRIDENT)
                || stack.is(ModItems.MURAMASA);
    }

    @Unique
    private boolean abyssalrpg$wasOffhandExcalibur;

    @Unique
    private boolean abyssalrpg$wasHoldingKatana;

    @Unique
    private boolean abyssalrpg$lockKatanaEquip;

    @Unique
    private int abyssalrpg$lastSelectedSlot = -1;

    @org.spongepowered.asm.mixin.injection.Inject(method = "tick", at = @At("HEAD"))
    private void abyssalrpg$captureExcaliburBeforeTick(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        this.abyssalrpg$wasHoldingCustomWeapon = abyssalrpg$isCustomWeapon(this.mainHandItem);
        this.abyssalrpg$wasHoldingExcalibur = this.mainHandItem.is(ModItems.EXCALIBUR);
        this.abyssalrpg$wasOffhandExcalibur = this.offHandItem.is(ModItems.EXCALIBUR);
        this.abyssalrpg$wasHoldingKatana = this.mainHandItem.is(ModItems.KATANA);

        // Chặn trước mọi xung tụt tay do đổi reference ItemStack ngay đầu tick!
        net.minecraft.client.player.LocalPlayer lp = net.minecraft.client.Minecraft.getInstance().player;
        if (lp != null && lp.getInventory().selected == this.abyssalrpg$lastSelectedSlot) {
            ItemStack held = lp.getMainHandItem();
            if (this.abyssalrpg$wasHoldingExcalibur && held.is(ModItems.EXCALIBUR)) {
                this.mainHandItem = held;
                this.mainHandHeight = 1.0F;
                this.oMainHandHeight = 1.0F;
            }
            if (this.abyssalrpg$wasHoldingKatana && held.is(ModItems.KATANA)) {
                this.mainHandItem = held;
                this.mainHandHeight = 1.0F;
                this.oMainHandHeight = 1.0F;
            }
            ItemStack offHeld = lp.getOffhandItem();
            if (this.abyssalrpg$wasOffhandExcalibur && offHeld.is(ModItems.EXCALIBUR)) {
                this.offHandItem = offHeld;
                this.offHandHeight = 1.0F;
                this.oOffHandHeight = 1.0F;
            }
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "tick", at = @At("TAIL"))
    private void abyssalrpg$stabilizeExcaliburHandHeight(
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        net.minecraft.client.player.LocalPlayer lp = net.minecraft.client.Minecraft.getInstance().player;
        if (lp == null) {
            this.abyssalrpg$lastSelectedSlot = -1;
            return;
        }
        int curSlot = lp.getInventory().selected;
        boolean sameSlot = (curSlot == this.abyssalrpg$lastSelectedSlot);
        this.abyssalrpg$lastSelectedSlot = curSlot;
        ItemStack held = lp.getMainHandItem();
        ItemStack offHeld = lp.getOffhandItem();

        if (sameSlot && this.abyssalrpg$wasHoldingCustomWeapon
                && abyssalrpg$isCustomWeapon(held)
                && held.getItem() == this.mainHandItem.getItem()) {
            // QUY TẮC CHỐNG RUNG 0%: Khóa cứng cả tay phải & tay phụ (0 tụt tay, 0 giật khi cập nhật cooldown/NBT!)
            this.mainHandItem = held;
            this.mainHandHeight = 1.0F;
            this.oMainHandHeight = 1.0F;
            if (offHeld.isEmpty()) {
                this.offHandHeight = 1.0F;
                this.oOffHandHeight = 1.0F;
            }
            this.abyssalrpg$lockKatanaEquip = held.is(ModItems.KATANA);
        } else {
            this.abyssalrpg$lockKatanaEquip = false;
        }

        if (this.abyssalrpg$wasOffhandExcalibur && offHeld.is(ModItems.EXCALIBUR)) {
            this.offHandItem = offHeld;
            this.offHandHeight = 1.0F;
            this.oOffHandHeight = 1.0F;
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void abyssalrpg$preventSwordUseDip(InteractionHand hand,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (hand == InteractionHand.MAIN_HAND
                && (this.mainHandItem.is(ModItems.EXCALIBUR)
                || this.mainHandItem.is(ModItems.KATANA))) {
            ci.cancel();
        } else if (hand == InteractionHand.OFF_HAND && this.offHandItem.is(ModItems.EXCALIBUR)) {
            ci.cancel();
        }
    }

    /**
     * QUY TẮC CHỐNG RUNG 0%: Triệt tiêu hoàn toàn độ trễ lắc chuột (`xBob/yBob`) của Vanilla trong
     * `renderHandsWithItems` khi đang cầm vũ khí custom -> vũ khí đứng im chắc tay khi xoay góc nhìn!
     */
    @org.spongepowered.asm.mixin.injection.Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void abyssalrpg$lockCameraSwayBeforeHands(
            float partialTicks, PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer,
            net.minecraft.client.player.LocalPlayer player, int combinedLight,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        this.abyssalrpg$savedBob = false;
        if (player != null && abyssalrpg$isCustomWeapon(player.getMainHandItem())) {
            this.abyssalrpg$savedXBobO = player.xBobO;
            this.abyssalrpg$savedXBob = player.xBob;
            this.abyssalrpg$savedYBobO = player.yBobO;
            this.abyssalrpg$savedYBob = player.yBob;
            float vx = player.getViewXRot(partialTicks);
            float vy = player.getViewYRot(partialTicks);
            player.xBobO = vx;
            player.xBob = vx;
            player.yBobO = vy;
            player.yBob = vy;
            this.abyssalrpg$savedBob = true;
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void abyssalrpg$restoreCameraSwayAfterHands(
            float partialTicks, PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer,
            net.minecraft.client.player.LocalPlayer player, int combinedLight,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (this.abyssalrpg$savedBob && player != null) {
            player.xBobO = this.abyssalrpg$savedXBobO;
            player.xBob = this.abyssalrpg$savedXBob;
            player.yBobO = this.abyssalrpg$savedYBobO;
            player.yBob = this.abyssalrpg$savedYBob;
            this.abyssalrpg$savedBob = false;
        }
    }

    @WrapOperation(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
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
            Operation<Void> original) {
        ItemStack mainStack = player.getMainHandItem();
        boolean dualWieldActive = mainStack.is(ModItems.DUAL_KNIFE) && player.getOffhandItem().isEmpty();
        boolean togetsuActive = mainStack.is(ModItems.TOGETSU) && player.getOffhandItem().isEmpty();
        boolean excaliburActive = (hand == InteractionHand.MAIN_HAND) && stack.is(ModItems.EXCALIBUR);
        boolean katanaActive = (hand == InteractionHand.MAIN_HAND) && stack.is(ModItems.KATANA);
        boolean seaActive = mainStack.is(ModItems.SEA_TRIDENT)
                && (hand == InteractionHand.MAIN_HAND || player.getOffhandItem().isEmpty());
        boolean muramasaActive = mainStack.is(ModItems.MURAMASA)
                && (hand == InteractionHand.MAIN_HAND || player.getOffhandItem().isEmpty());

        if (muramasaActive) {
            boolean isOffhand = (hand == InteractionHand.OFF_HAND);
            ItemStack effectiveStack = isOffhand ? mainStack : stack;
            float effectiveEquip = isOffhand
                    ? (1.0F - Mth.lerp(partialTicks, this.oMainHandHeight, this.mainHandHeight))
                    : equippedProgress;
            float alpha = MuramasaAnimManager.getActionProgress(player.getId(), partialTicks);
            MuramasaMoveType move = MuramasaAnimManager.getMoveType(player.getId());
            float sprint = MuramasaAnimManager.getSprintBlend(player.getId(), partialTicks);

            poseStack.pushPose();
            if (alpha > 0.001F) {
                float ret = (alpha > 0.78F) ? (1.0F - DualBladeAnimState.easeInOutCubic((alpha - 0.78F) / 0.22F)) : 1.0F;
                switch (move) {
                    case COMBO_1 -> {
                        if (!isOffhand) {
                            float rise = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.16F, 0.0F, 1.0F));
                            float sweep = DualBladeAnimState.easeInOutCubic(Mth.clamp((alpha - 0.12F) / 0.44F, 0.0F, 1.0F));
                            poseStack.translate(
                                    Mth.lerp(sweep, 0.24F * rise, -0.74F) * ret,
                                    Mth.lerp(sweep, 0.24F * rise, -0.26F) * ret,
                                    Mth.lerp(sweep, -0.06F * rise, -0.34F) * ret);
                            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -28.0F * rise, 80.0F) * ret));
                            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(sweep, 16.0F * rise, -64.0F) * ret));
                            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(sweep, -18.0F * rise, 52.0F) * ret));
                        } else {
                            poseStack.translate(-0.08F * ret, -0.05F * ret, 0.08F * ret);
                        }
                    }
                    case COMBO_2 -> {
                        if (!isOffhand) {
                            float enter = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.10F, 0.0F, 1.0F));
                            float sweep = DualBladeAnimState.easeInOutCubic(Mth.clamp((alpha - 0.08F) / 0.46F, 0.0F, 1.0F));
                            float startScale = Mth.lerp(enter, 0.85F, 1.0F);
                            poseStack.translate(
                                    Mth.lerp(sweep, -0.74F * startScale, 0.38F) * ret,
                                    Mth.lerp(sweep, -0.26F * startScale, 0.22F) * ret,
                                    Mth.lerp(sweep, -0.34F * startScale, -0.24F) * ret);
                            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, 80.0F * startScale, -54.0F) * ret));
                            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(sweep, -64.0F * startScale, -22.0F) * ret));
                            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(sweep, 52.0F * startScale, -62.0F) * ret));
                        } else {
                            poseStack.translate(-0.06F * ret, -0.04F * ret, 0.06F * ret);
                        }
                    }
                    case COMBO_3 -> {
                        if (!isOffhand) {
                            float wind = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.16F, 0.0F, 1.0F));
                            float sweep = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.14F) / 0.44F, 0.0F, 1.0F));
                            poseStack.translate(
                                    Mth.lerp(sweep, 0.34F * wind, -0.82F) * ret,
                                    Mth.lerp(sweep, 0.06F * wind, -0.10F) * ret,
                                    Mth.lerp(sweep, -0.10F * wind, -0.38F) * ret);
                            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -46.0F * wind, 92.0F) * ret));
                            poseStack.mulPose(Axis.XP.rotationDegrees(-74.0F * ret));
                            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(sweep, -36.0F * wind, 78.0F) * ret));
                        } else {
                            poseStack.translate(-0.10F * ret, -0.08F * ret, 0.10F * ret);
                        }
                    }
                    case IAIJUTSU_DRAW -> {
                        if (alpha < 0.30F) {
                            float c = DualBladeAnimState.easeOutCubic(alpha / 0.30F);
                            if (!isOffhand) {
                                poseStack.translate(-0.56F * c, -0.18F * c, -0.18F * c);
                                poseStack.mulPose(Axis.YP.rotationDegrees(62.0F * c));
                                poseStack.mulPose(Axis.XP.rotationDegrees(-46.0F * c));
                                poseStack.mulPose(Axis.ZP.rotationDegrees(48.0F * c));
                            } else {
                                poseStack.translate(0.24F * c, 0.06F * c, -0.18F * c);
                                poseStack.mulPose(Axis.YP.rotationDegrees(-24.0F * c));
                            }
                        } else {
                            float draw = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.30F) / 0.26F, 0.0F, 1.0F));
                            if (!isOffhand) {
                                poseStack.translate(
                                        Mth.lerp(draw, -0.68F, 0.44F) * ret,
                                        Mth.lerp(draw, -0.18F, 0.08F) * ret,
                                        Mth.lerp(draw, -0.18F, -0.36F) * ret);
                                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(draw, 72.0F, -78.0F) * ret));
                                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(draw, -46.0F, -64.0F) * ret));
                                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(draw, 48.0F, -76.0F) * ret));
                            } else {
                                poseStack.translate(
                                        Mth.lerp(draw, 0.24F, -0.12F) * ret,
                                        Mth.lerp(draw, 0.06F, -0.10F) * ret,
                                        Mth.lerp(draw, -0.18F, 0.12F) * ret);
                            }
                        }
                    }
                    case BLOOD_OATH_AWAKEN -> {
                        float b = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.38F, 0.0F, 1.0F));
                        float side = isOffhand ? -1.0F : 1.0F;
                        poseStack.translate(side * 0.18F * b * ret, -0.10F * b * ret, -0.08F * ret);
                        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -22.0F * b * ret));
                    }
                }
            } else if (sprint > 0.001F) {
                if (!isOffhand) {
                    poseStack.translate(0.06F * sprint, -0.06F * sprint, 0.06F * sprint);
                    poseStack.mulPose(Axis.XP.rotationDegrees(14.0F * sprint));
                } else {
                    poseStack.translate(-0.04F * sprint, -0.04F * sprint, 0.04F * sprint);
                }
            }
            // Tắt hoàn toàn vanilla swingProgress & equip bob cho Muramasa -> 0 giật khung cuối đòn chém!
            float effectiveSwing = 0.0F;
            float finalEquip = (alpha > 0.001F) ? 0.0F : effectiveEquip;
            MuramasaGeoRenderer.setCurrentRenderEntityId(player.getId());
            try {
                original.call(instance, player, partialTicks, pitch, hand, effectiveSwing, effectiveStack,
                        finalEquip, poseStack, buffer, combinedLight);
            } finally {
                MuramasaGeoRenderer.setCurrentRenderEntityId(-1);
                poseStack.popPose();
            }
            return;
        }

        if (seaActive) {
            boolean isOffhand = (hand == InteractionHand.OFF_HAND);
            ItemStack effectiveStack = isOffhand ? mainStack : stack;
            float effectiveEquip = isOffhand
                    ? (1.0F - Mth.lerp(partialTicks, this.oMainHandHeight, this.mainHandHeight))
                    : equippedProgress;
            float alpha = SeaWeaponsAnimManager.getActionProgress(player.getId(), partialTicks);
            SeaWeaponsMoveType move = SeaWeaponsAnimManager.getMoveType(player.getId());
            float sprint = SeaWeaponsAnimManager.getSprintBlend(player.getId(), partialTicks);

            poseStack.pushPose();
            if (alpha > 0.001F) {
                float ret = (alpha > 0.78F) ? (1.0F - DualBladeAnimState.easeInOutCubic((alpha - 0.78F) / 0.22F)) : 1.0F;
                switch (move) {
                    case THRUST_1 -> {
                        if (!isOffhand) {
                            float thrust = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.14F) / 0.36F, 0.0F, 1.0F));
                            poseStack.translate(Mth.lerp(thrust, 0.12F, -0.18F) * ret, Mth.lerp(thrust, -0.06F, 0.06F) * ret,
                                    Mth.lerp(thrust, 0.16F, -0.62F) * ret);
                            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(thrust, 10.0F, -68.0F) * ret));
                        }
                    }
                    case SWEEP_2 -> {
                        if (!isOffhand) {
                            float sweep = DualBladeAnimState.easeInOutCubic(Mth.clamp((alpha - 0.10F) / 0.48F, 0.0F, 1.0F));
                            poseStack.translate(Mth.lerp(sweep, 0.28F, -0.78F) * ret, -0.14F * ret, -0.34F * ret);
                            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -36.0F, 86.0F) * ret));
                            poseStack.mulPose(Axis.XP.rotationDegrees(-62.0F * ret));
                            poseStack.mulPose(Axis.ZP.rotationDegrees(62.0F * ret));
                        }
                    }
                    case TRIDENT_SLAM_3 -> {
                        if (!isOffhand) {
                            float raise = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.26F, 0.0F, 1.0F));
                            float slam = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.24F) / 0.36F, 0.0F, 1.0F));
                            poseStack.translate(-0.18F * ret, Mth.lerp(slam, 0.36F * raise, -0.44F) * ret, -0.38F * ret);
                            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(slam, 28.0F * raise, -84.0F) * ret));
                        }
                    }
                    case LANTERN_TIDAL_WAVE -> {
                        // Đèn Lồng tay trái nâng cao rực sáng rồi đẩy mạnh về trước gọi SÓNG THẦN!
                        float raise = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.28F, 0.0F, 1.0F));
                        float surge = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.26F) / 0.34F, 0.0F, 1.0F));
                        if (isOffhand) {
                            poseStack.translate(Mth.lerp(surge, 0.20F * raise, 0.42F) * ret,
                                    Mth.lerp(surge, 0.36F * raise, 0.12F) * ret,
                                    Mth.lerp(surge, -0.14F * raise, -0.52F) * ret);
                            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(surge, -16.0F * raise, -38.0F) * ret));
                        } else {
                            poseStack.translate(0.14F * raise * ret, -0.12F * ret, 0.14F * ret);
                        }
                    }
                    case TRIDENT_WHIRLPOOL -> {
                        // Giơ Đinh Ba tay phải THẲNG LÊN TRỜI triệu hồi 5 Đinh Ba 3D rồi vung nhẹ chỉ lệnh!
                        float raise = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.24F, 0.0F, 1.0F));
                        float cmd = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.62F) / 0.20F, 0.0F, 1.0F));
                        if (!isOffhand) {
                            poseStack.translate(
                                    Mth.lerp(cmd, -0.16F * raise, -0.22F) * ret,
                                    Mth.lerp(cmd, 0.44F * raise, -0.10F) * ret,
                                    Mth.lerp(cmd, -0.16F * raise, -0.44F) * ret);
                            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(cmd, -22.0F * raise, -8.0F) * ret));
                            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(cmd, 24.0F * raise, -44.0F) * ret));
                        } else {
                            poseStack.translate(-0.08F * ret, 0.08F * raise * ret, -0.10F * ret);
                        }
                    }
                }
            } else if (sprint > 0.001F) {
                if (!isOffhand) {
                    poseStack.translate(0.08F * sprint, -0.08F * sprint, 0.08F * sprint);
                    poseStack.mulPose(Axis.XP.rotationDegrees(18.0F * sprint));
                } else {
                    poseStack.translate(-0.05F * sprint, -0.04F * sprint, -0.04F * sprint);
                }
            }
            float effectiveSwing = 0.0F;
            float finalEquip = (alpha > 0.001F) ? 0.0F : effectiveEquip;
            SeaTridentGeoRenderer.setCurrentRenderEntityId(player.getId());
            try {
                original.call(instance, player, partialTicks, pitch, hand, effectiveSwing, effectiveStack,
                        finalEquip, poseStack, buffer, combinedLight);
            } finally {
                SeaTridentGeoRenderer.setCurrentRenderEntityId(-1);
                poseStack.popPose();
            }
            return;
        }

        if (katanaActive) {
            // Toàn bộ dáng Đỡ Chéo Cao, Tra Vỏ Iaido, Rút Kiếm, Combo 3 nhịp & Gạt Đòn góc nhìn thứ 1
            // được tính chuẩn trong Camera-Space quanh chuôi kiếm tại KatanaItemRenderer (không xoay lệch tâm Camera!).
            float alpha = KatanaAnimManager.getActionProgress(player.getId(), partialTicks);
            float guard = KatanaAnimManager.getGuardBlend(player.getId(), partialTicks);
            float sayaGuard = KatanaAnimManager.getSayaGuardBlend(player.getId(), partialTicks);
            float iaidoCharge = KatanaAnimManager.getIaidoChargeBlend(player.getId(), partialTicks);
            float effectiveSwing = 0.0F;
            float effectiveEquip = (this.abyssalrpg$lockKatanaEquip || alpha > 0.001F || guard > 0.05F || sayaGuard > 0.05F || iaidoCharge > 0.05F) ? 0.0F : equippedProgress;
            KatanaItemRenderer.setCurrentRenderEntityId(player.getId());
            try {
                original.call(instance, player, partialTicks, pitch, hand, effectiveSwing, stack,
                        effectiveEquip, poseStack, buffer, combinedLight);
            } finally {
                KatanaItemRenderer.setCurrentRenderEntityId(-1);
            }
            return;
        }

        if (excaliburActive) {
            // Kiến trúc đồng bộ 100% F5 ↔ Góc nhìn thứ nhất:
            // Hủy render Item Display JSON mặc định; vẽ trực tiếp cây xương 2 tay gập khuỷu, Excalibur 3D & vỏ Avalon 3D
            // của góc nhìn thứ ba ngay trước tầm mắt (ẩn đầu & thân, bù trừ offset Combat Idle/Blocking & Pitch Clamp 0.75f)!
            ExcaliburGeoRenderer.setCurrentRenderEntityId(player.getId());
            try {
                ExcaliburFirstPersonRigRenderer.render(player, stack, partialTicks, pitch, poseStack, buffer, combinedLight);
            } finally {
                ExcaliburGeoRenderer.setCurrentRenderEntityId(-1);
            }
            return;
        }
        // Khi tay chính đang cầm Excalibur và tay phụ trống -> không vẽ thêm cánh tay trái vanilla trùng lặp
        // vì ExcaliburFirstPersonRigRenderer đã vẽ đủ cả 2 cánh tay 2 khớp đồng bộ với F5!
        if (hand == InteractionHand.OFF_HAND && stack.isEmpty() && mainStack.is(ModItems.EXCALIBUR)) {
            return;
        }
        // Khi cầm Excalibur ở tay phụ: hủy hiệu ứng vung/nâng tay mặc định của vanilla (tương đương applyForgeHandTransform = true)!
        if (hand == InteractionHand.OFF_HAND && stack.is(ModItems.EXCALIBUR)) {
            ExcaliburGeoRenderer.setCurrentRenderEntityId(player.getId());
            try {
                original.call(instance, player, partialTicks, pitch, hand, 0.0F, stack,
                        0.0F, poseStack, buffer, combinedLight);
            } finally {
                ExcaliburGeoRenderer.setCurrentRenderEntityId(-1);
            }
            return;
        }

        if (!dualWieldActive && !togetsuActive) {
            original.call(instance, player, partialTicks, pitch, hand, swingProgress, stack,
                    equippedProgress, poseStack, buffer, combinedLight);
            return;
        }

        if (togetsuActive) {
            boolean isOffhand = (hand == InteractionHand.OFF_HAND);
            boolean fusedRender = TogetsuAnimManager.shouldRenderAsFused(player.getId(), mainStack, partialTicks);
            if (isOffhand && fusedRender) {
                // Dạng Đại Đao Song Nguyệt chỉ hiển thị 1 thanh Đại Đao khổng lồ duy nhất ở tay
                // phải!
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
                float env = DualBladeAnimState.poseHoldEnvelope(alpha);
                float sprintCarry = sprint * (1.0F - env);
                if (sprintCarry > 0.001F) {
                    if (!fusedRender && move.isDualComboMove()) {
                        abyssalrpg$applyFirstPersonNarutoSprintTuck(poseStack, isOffhand, sprintCarry);
                    } else if (fusedRender && !move.isLockedSkill()) {
                        poseStack.translate(0.03F * sprintCarry, -0.04F * sprintCarry, -0.03F * sprintCarry);
                        poseStack.mulPose(Axis.XP.rotationDegrees(6.0F * sprintCarry));
                        poseStack.mulPose(Axis.ZP.rotationDegrees(-4.0F * sprintCarry));
                    }
                }
                abyssalrpg$applyFirstPersonTogetsuPose(poseStack, isOffhand, move, alpha);
            } else if (sprint > 0.001F) {
                if (fusedRender) {
                    // Hạ nhẹ góc cầm Đại Đao khi chạy nước rút mà không kéo lưỡi dài sát mặt phẳng
                    // cắt gần của camera
                    poseStack.translate(0.03F * sprint, -0.04F * sprint, -0.03F * sprint);
                    poseStack.mulPose(Axis.XP.rotationDegrees(6.0F * sprint));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-4.0F * sprint));
                } else {
                    // Đồng bộ 1:1 First-Person Naruto Run giống hệt Dual Knife!
                    abyssalrpg$applyFirstPersonNarutoSprintTuck(poseStack, isOffhand, sprint);
                }
            }
            float effectiveSwing = 0.0F;
            float finalEquip = (alpha > 0.001F) ? 0.0F : effectiveEquip;
            TogetsuGeoRenderer.setCurrentRenderEntityId(player.getId());
            try {
                original.call(instance, player, partialTicks, pitch, hand, effectiveSwing, effectiveStack,
                        finalEquip, poseStack, buffer, combinedLight);
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
            // Khi đang chạy Naruto và chém, đầu/cuối đòn chuyển mượt trực tiếp từ tư thế
            // thu dao Naruto Run!
            float sprintCarry = narutoSprint * (1.0F - env);
            if (sprintCarry > 0.001F && !move.isFlashDashPhase()) {
                abyssalrpg$applyFirstPersonNarutoSprintTuck(poseStack, isOffhand, sprintCarry);
            }
            abyssalrpg$applyFirstPersonNightfallPose(poseStack, isOffhand, move, comboAlpha);
        } else if (narutoSprint > 0.001F) {
            // Đồng bộ First Person với tư thế chạy Naruto: thu nhẹ 2 thanh dao xuôi thấp về
            // 2 bên mép dưới (0 che tâm ngắm!)
            abyssalrpg$applyFirstPersonNarutoSprintTuck(poseStack, isOffhand, narutoSprint);
        }
        // Khi đứng yên / đi bộ thường: giữ nguyên vị trí cầm kiếm cơ bản Vanilla gọn
        // gàng ở 2 góc dưới (0 vanilla swing snap, 0 rung giật)!
        float effectiveSwing = 0.0F;
        float finalEquip = (comboAlpha > 0.001F) ? 0.0F : effectiveEquip;
        original.call(instance, player, partialTicks, pitch, hand, effectiveSwing, effectiveStack,
                finalEquip, poseStack, buffer, combinedLight);
        poseStack.popPose();
    }

    @Unique
    private static void abyssalrpg$applyFirstPersonNarutoSprintTuck(PoseStack poseStack, boolean isOffhand,
            float sprint) {
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

        // Cả 2 tay ở góc nhìn Thứ 1 đều xuất phát mượt từ tư thế cầm kiếm cơ bản đối
        // xứng của Vanilla
        // (Tay phải góc dưới–phải Đỏ Huyết, Tay trái góc dưới–trái Xanh Kim Cương) rồi
        // trả sạch về vị trí gốc!
        switch (move) {
            case COMBO_1 -> {
                // Nhịp 1: Dao phải (Đỏ Huyết) chém chéo rộng từ phải–trên xuống trái–dưới; dao
                // trái giữ thế thu gọn!
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(sweep, 0.20F, -0.82F) * env, Mth.lerp(sweep, 0.22F, -0.26F) * env,
                            -0.30F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -28.0F, 82.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(66.0F * env));
                } else {
                    poseStack.translate(-0.10F * env, -0.06F * env, 0.10F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(14.0F * env));
                }
            }
            case COMBO_2 -> {
                // Nhịp 2: Dao trái (Xanh Kim Cương) chém chéo đối xứng từ trái–trên sang
                // phải–dưới; dao phải thu gọn!
                if (isOffhand) {
                    poseStack.translate(Mth.lerp(sweep, -0.20F, 0.82F) * env, Mth.lerp(sweep, 0.22F, -0.26F) * env,
                            -0.30F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, 28.0F, -82.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-66.0F * env));
                } else {
                    poseStack.translate(0.10F * env, -0.06F * env, 0.10F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(14.0F * env));
                }
            }
            case COMBO_3 -> {
                // Nhịp 3 (Twin Opposing Double-Arc): Dao phải cắt chéo thấp + dao trái quét
                // chéo cao song hành!
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(sweep, 0.24F, -0.74F) * env, Mth.lerp(sweep, 0.16F, -0.28F) * env,
                            -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, -32.0F, 78.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(72.0F * env));
                } else {
                    poseStack.translate(Mth.lerp(sweep, -0.24F, 0.74F) * env, Mth.lerp(sweep, -0.10F, 0.26F) * env,
                            -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(sweep, 32.0F, -78.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-64.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-72.0F * env));
                }
            }
            case COMBO_4 -> {
                // Nhịp 4 (360° Cyclone): 2 dao nằm ngang đối xứng quét vòng lốc xoáy rộng toàn
                // khung nhìn!
                float spin = DualBladeAnimState.cyclone360Spin(alpha);
                float cyc = Mth.sin(spin * Mth.TWO_PI);
                poseStack.translate(sideSign * (-0.30F * env + 0.20F * cyc), -0.14F * env, -0.32F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * (52.0F * env + 38.0F * cyc)));
                poseStack.mulPose(Axis.XP.rotationDegrees(-76.0F * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 78.0F * env));
            }
            case COMBO_5 -> {
                // Nhịp 5 Finisher (X-Rend Slam): Nhấc nhẹ cả 2 dao lên hai bên rồi bổ chéo đối
                // xứng khóa chữ X!
                float windup = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.24F, 0.0F, 1.0F));
                float slam = alpha < 0.22F ? 0.0F
                        : (alpha < 0.46F ? DualBladeAnimState.easeOutQuart((alpha - 0.22F) / 0.24F) : 1.0F);
                poseStack.translate(
                        sideSign * Mth.lerp(slam, 0.22F * windup, -0.58F) * env,
                        Mth.lerp(slam, 0.26F * windup, -0.30F) * env,
                        -0.36F * slam * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(slam, -24.0F * windup, 64.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(slam, 22.0F * windup, -78.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * Mth.lerp(slam, -18.0F * windup, 56.0F) * env));
            }
            case SPRINT_THRUST -> {
                // Sprint Opening Cross-Slash (First Person): 2 dao vung chém chéo lệch pha mượt
                // mà từ thế cầm cơ bản
                // (Tay phải Đỏ–Đen chém chéo trước, Tay trái Đen–Bạc quét nối tiếp ngay sau ->
                // đồng bộ 1:1 với F5 & Ribbon!)
                float slashR = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.36F, 0.0F, 1.0F));
                float slashL = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.14F) / 0.38F, 0.0F, 1.0F));
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(slashR, 0.18F, -0.72F) * env, Mth.lerp(slashR, 0.14F, -0.22F) * env,
                            -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(slashR, -24.0F, 72.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-64.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(60.0F * env));
                } else {
                    float lEnv = env * DualBladeAnimState.easeOutCubic(Mth.clamp((alpha - 0.08F) / 0.18F, 0.0F, 1.0F));
                    poseStack.translate(Mth.lerp(slashL, -0.18F, 0.72F) * lEnv, Mth.lerp(slashL, 0.10F, -0.16F) * lEnv,
                            -0.34F * lEnv);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(slashL, 24.0F, -72.0F) * lEnv));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-62.0F * lEnv));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-60.0F * lEnv));
                }
            }
            case FLASH_DASH_CHARGE -> {
                // Pha 1 Flash Dash (First Person): kéo thấp gọn 2 thanh dao nén đà sát mép
                // dưới–hai bên màn hình!
                float coil = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.45F, 0.0F, 1.0F));
                poseStack.translate(sideSign * 0.16F * coil, -0.22F * coil, 0.18F * coil);
                poseStack.mulPose(Axis.XP.rotationDegrees(30.0F * coil));
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * -18.0F * coil));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * -14.0F * coil));
            }
            case FLASH_DASH_LAUNCH -> {
                // Pha 2 Flash Dash (First Person, 15 block): 2 lưỡi song sắc nghiêng sát hai
                // bên xé gió siêu tốc!
                poseStack.translate(sideSign * 0.18F, -0.20F, 0.22F);
                poseStack.mulPose(Axis.XP.rotationDegrees(36.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * -22.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * -16.0F));
            }
            case FLASH_DASH_END -> {
                // Pha 3 Flash Dash (First Person): vung chéo 2 dao Đỏ Huyết + Xanh Kim Cương
                // khóa chữ X dứt khoát giữa màn hình!
                float snap = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.34F, 0.0F, 1.0F));
                poseStack.translate(
                        sideSign * Mth.lerp(snap, 0.18F, -0.46F) * env,
                        Mth.lerp(snap, -0.20F, -0.10F) * env,
                        Mth.lerp(snap, 0.18F, -0.34F) * env);
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
                // Nhát 1: CHỈ Tay Phải (Lưỡi Đen) chém chéo rộng Phải-Trên -> Trái-Dưới; Tay
                // Trái thu gọn!
                float cut = alpha < 0.14F ? 0.0F
                        : (alpha < 0.52F ? DualBladeAnimState.easeOutQuart((alpha - 0.14F) / 0.38F) : 1.0F);
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(cut, 0.24F, -0.78F) * env, Mth.lerp(cut, 0.24F, -0.26F) * env,
                            -0.32F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cut, -28.0F, 82.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(66.0F * env));
                } else {
                    poseStack.translate(-0.10F * env, -0.06F * env, 0.10F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(14.0F * env));
                }
            }
            case DUAL_2 -> {
                // Nhát 2: CHỈ Tay Trái (Lưỡi Trắng) chém chéo rộng Trái-Trên -> Phải-Dưới; Tay
                // Phải thu gọn!
                float cut = alpha < 0.14F ? 0.0F
                        : (alpha < 0.52F ? DualBladeAnimState.easeOutQuart((alpha - 0.14F) / 0.38F) : 1.0F);
                if (isOffhand) {
                    poseStack.translate(Mth.lerp(cut, -0.24F, 0.78F) * env, Mth.lerp(cut, 0.24F, -0.26F) * env,
                            -0.32F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cut, 28.0F, -82.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-66.0F * env));
                } else {
                    poseStack.translate(0.10F * env, -0.06F * env, 0.10F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(14.0F * env));
                }
            }
            case DUAL_3 -> {
                // Nhát 3: CHỈ Tay Phải (Lưỡi Đen) chém hất ngược Trái-Dưới -> Phải-Trên!
                float cut = alpha < 0.14F ? 0.0F
                        : (alpha < 0.54F ? DualBladeAnimState.easeOutQuart((alpha - 0.14F) / 0.40F) : 1.0F);
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(cut, -0.68F, 0.30F) * env, Mth.lerp(cut, -0.28F, 0.32F) * env,
                            -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cut, 66.0F, -32.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-62.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(56.0F * env));
                } else {
                    poseStack.translate(-0.10F * env, -0.08F * env, 0.12F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(16.0F * env));
                }
            }
            case DUAL_4 -> {
                // Nhát 4: CHỈ Tay Trái (Lưỡi Trắng) chém hất ngược Phải-Dưới -> Trái-Trên (+
                // Moon Mark)!
                float cut = alpha < 0.14F ? 0.0F
                        : (alpha < 0.54F ? DualBladeAnimState.easeOutQuart((alpha - 0.14F) / 0.40F) : 1.0F);
                if (isOffhand) {
                    poseStack.translate(Mth.lerp(cut, 0.68F, -0.30F) * env, Mth.lerp(cut, -0.28F, 0.32F) * env,
                            -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cut, -66.0F, 32.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-62.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-56.0F * env));
                } else {
                    poseStack.translate(0.10F * env, -0.08F * env, 0.12F * env);
                    poseStack.mulPose(Axis.XP.rotationDegrees(16.0F * env));
                }
            }
            case CRESCENT_WAVE_SLASH -> {
                // Signature RMB — Quick Crescent Wave Draw-Slash (Trái -> Phải phẳng phóng sóng
                // lưỡi liềm xoay!)
                float cut = alpha < 0.14F ? 0.0F
                        : (alpha < 0.44F ? DualBladeAnimState.easeOutQuart((alpha - 0.14F) / 0.30F) : 1.0F);
                poseStack.translate(Mth.lerp(cut, -0.96F, 0.34F) * env,
                        Mth.lerp(cut, -0.06F, 0.08F) * env, -0.44F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cut, -52.0F, -116.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(38.0F * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(100.0F * env));
            }
            case DUAL_5 -> {
                // NHÁT 5 ĐÒN ĐÁNH ĐẶC BIỆT (Auto-Fuse Combined Sword): Chớp biến hình giơ cao
                // đỉnh đầu bổ chéo cực nặng!
                float wind = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.28F, 0.0F, 1.0F));
                float cleave = alpha < 0.30F ? 0.0F
                        : (alpha < 0.64F ? DualBladeAnimState.easeOutQuart((alpha - 0.30F) / 0.34F) : 1.0F);
                poseStack.translate(Mth.lerp(cleave, -0.18F * wind, 0.42F) * env,
                        Mth.lerp(cleave, 0.42F * wind, -0.44F) * env, -0.38F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cleave, 18.0F * wind, -42.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(cleave, 28.0F * wind, -82.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(cleave, 16.0F * wind, -28.0F) * env));
            }
            case DUAL_SPRINT_THRUST -> {
                // Giống 1:1 Dual Knife SPRINT_THRUST (First Person): 2 dao vung chém chéo lệch
                // pha mượt mà!
                float slashR = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.36F, 0.0F, 1.0F));
                float slashL = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.14F) / 0.38F, 0.0F, 1.0F));
                if (!isOffhand) {
                    poseStack.translate(Mth.lerp(slashR, 0.18F, -0.72F) * env, Mth.lerp(slashR, 0.14F, -0.22F) * env,
                            -0.34F * env);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(slashR, -24.0F, 72.0F) * env));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-64.0F * env));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(60.0F * env));
                } else {
                    float lEnv = env * DualBladeAnimState.easeOutCubic(Mth.clamp((alpha - 0.08F) / 0.18F, 0.0F, 1.0F));
                    poseStack.translate(Mth.lerp(slashL, -0.18F, 0.72F) * lEnv, Mth.lerp(slashL, 0.10F, -0.16F) * lEnv,
                            -0.34F * lEnv);
                    poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(slashL, 24.0F, -72.0F) * lEnv));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-62.0F * lEnv));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-60.0F * lEnv));
                }
            }
            case DUAL_AIR_SLASH, DUAL_LANDING_DASH -> {
                // Giống 1:1 Dual Knife AIR_SLASH / SKILL_DASH (First Person)!
                poseStack.translate(sideSign * Mth.lerp(sweep, 0.22F, -0.66F) * env, -0.12F * env, -0.30F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(sweep, -24.0F, 72.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 64.0F * env));
            }
            case FUSE_TRANSFORM -> {
                // Kéo 2 cây đao vào giữa màn hình ghép khớp ở 50% rồi nâng Đại Đao Song Nguyệt
                // rực sáng!
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
                // Song Nguyệt Nhát 1 (0.3s): Bổ cắm chéo từ Phải-Trên thẳng xuống Trái-Dưới!
                float cut = alpha < 0.14F ? 0.0F
                        : (alpha < 0.50F ? DualBladeAnimState.easeOutQuart((alpha - 0.14F) / 0.36F) : 1.0F);
                poseStack.translate(Mth.lerp(cut, 0.26F, -0.38F) * env,
                        Mth.lerp(cut, 0.42F, -0.52F) * env, -0.38F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cut, -14.0F, 34.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(cut, 26.0F, -92.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(cut, -14.0F, 26.0F) * env));
            }
            case FUSED_2 -> {
                // Song Nguyệt Nhát 2 (0.3s): Bổ cắm chéo ngược từ Trái-Trên thẳng xuống
                // Phải-Dưới!
                float cut = alpha < 0.14F ? 0.0F
                        : (alpha < 0.50F ? DualBladeAnimState.easeOutQuart((alpha - 0.14F) / 0.36F) : 1.0F);
                poseStack.translate(Mth.lerp(cut, -0.34F, 0.28F) * env,
                        Mth.lerp(cut, 0.42F, -0.52F) * env, -0.38F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cut, 24.0F, -24.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(cut, 26.0F, -92.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(cut, 22.0F, -22.0F) * env));
            }
            case FUSED_3 -> {
                // Song Nguyệt Nhát 3 (0.3s): Chém hất vòng cung chéo thanh thoát từ Thấp-Trái
                // lên Cao-Phải!
                float cut = alpha < 0.12F ? 0.0F
                        : (alpha < 0.50F ? DualBladeAnimState.easeOutQuart((alpha - 0.12F) / 0.38F) : 1.0F);
                poseStack.translate(Mth.lerp(cut, -0.78F, 0.34F) * env,
                        Mth.lerp(cut, -0.28F, 0.30F) * env, -0.40F * env);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cut, -38.0F, -104.0F) * env));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(cut, 22.0F, 42.0F) * env));
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(cut, 74.0F, 112.0F) * env));
            }
            case CRESCENT_VORPAL_CHARGE -> {
                // Gồng lực rút kiếm Zenitsu góc nhìn Thứ 1: thu Đại Đao đúng chiều về hông
                // dưới-trái, rung tụ điện tăng dần!
                float enter = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.16F, 0.0F, 1.0F));
                float vib = Mth.sin(alpha * 135.0F) * (0.006F + 0.018F * alpha);
                poseStack.translate(-0.56F * enter + vib, 0.02F * enter - 0.06F * alpha + vib * 0.5F, -0.10F * enter);
                poseStack.mulPose(Axis.YP.rotationDegrees(36.0F * enter));
                poseStack.mulPose(Axis.XP.rotationDegrees(-14.0F * enter));
                poseStack.mulPose(Axis.ZP.rotationDegrees(46.0F * enter + vib * 140.0F));
            }
            case CRESCENT_VORPAL -> {
                // Bùng nổ rút kiếm Iaijutsu ngang Trái -> Phải (giải ma trận chuẩn: LƯỠI SẮC
                // TRẮNG DẪN ĐẦU 100% SANG PHẢI, dotEdge=+0.94..1.00, minZ>=0.41!)
                float cut = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.26F, 0.0F, 1.0F));
                float rec = alpha < 0.65F ? 1.0F : (1.0F - DualBladeAnimState.easeInOutCubic((alpha - 0.65F) / 0.35F));
                poseStack.translate(Mth.lerp(cut, -1.14F, 0.36F) * rec, Mth.lerp(cut, -0.04F, 0.10F) * rec,
                        -0.48F * rec);
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(cut, -54.4F, -116.5F) * rec));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(cut, 38.2F, 37.3F) * rec));
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(cut, 103.5F, 100.5F) * rec));
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
                        Mth.lerp(thrust, 0.14F, -0.68F) * endFade);
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(thrust, -24.0F, -84.0F) * endFade));
                poseStack.mulPose(Axis.YP.rotationDegrees(sideSign * Mth.lerp(thrust, -10.0F, 26.0F) * endFade));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideSign * 16.0F * endFade));
            }
        }
    }
}
