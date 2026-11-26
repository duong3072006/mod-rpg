package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.client.anim.MuramasaAnimManager;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.registry.ModItems;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * All-Perspectives (1st, 2nd F5-Back & 3rd F5-Front) Zero-Jitter Weapon View-Bob Suppression + FOV Mixin (`GameRenderer`).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    Minecraft minecraft;

    @Unique
    private static boolean abyssalrpg$isHoldingCustomWeapon(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        return main.is(ModItems.DUAL_KNIFE)
                || main.is(ModItems.TOGETSU)
                || main.is(ModItems.EXCALIBUR)
                || main.is(ModItems.SEA_TRIDENT)
                || main.is(ModItems.MURAMASA);
    }

    /**
     * QUY TẮC CHỐNG RUNG TUYỆT ĐỐI (CẢ GÓC NHÌN 1, 2 VÀ 3):
     * Hủy trực tiếp `GameRenderer.bobView` ngay đầu hàm khi cầm bất kỳ vũ khí custom nào ->
     * triệt tiêu 100% rung bước đi trong cả `renderItemInHand` (góc nhìn 1) lẫn `renderLevel` (góc nhìn 2 & 3 F5)!
     */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void abyssalrpg$suppressCustomWeaponViewBob(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        if (player != null && abyssalrpg$isHoldingCustomWeapon(player)) {
            ci.cancel();
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
        if (pose == null) {
            pose = MuramasaAnimManager.evaluatePose(player, partialTicks, 0.0F, player.tickCount + partialTicks);
        }
        if (pose == null || Math.abs(pose.fovOffsetDeg) < 0.05F) {
            return original;
        }
        return Mth.clamp(original + pose.fovOffsetDeg, 35.0, 125.0);
    }
}
