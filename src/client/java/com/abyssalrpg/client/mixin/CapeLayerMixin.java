package com.abyssalrpg.client.mixin;

import com.abyssalrpg.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ẩn áo choàng cape vanilla khi mặc Áo Giáp Cinder Dragon (có đôi cánh rồng + gai sống lưng + đuôi 3D)
 * hoặc Áo Giáp Nightmare Knight (đã có áo choàng đỏ rách 3D riêng), tương tự cách vanilla ẩn cape khi đeo Elytra
 * — tránh cape người chơi đè màu đỏ/vàng lên mặt lưng giáp và cắt xuyên gốc đuôi rồng khi chạy.
 */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void abyssalrpg$hideVanillaCapeOnCustomBackArmor(PoseStack poseStack, MultiBufferSource buffer,
                                                            int packedLight, AbstractClientPlayer player,
                                                            float limbSwing, float limbSwingAmount,
                                                            float partialTicks, float ageInTicks,
                                                            float netHeadYaw, float headPitch, CallbackInfo ci) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.is(ModItems.CINDER_DRAGON_CHESTPLATE) || chest.is(ModItems.NIGHTMARE_CHESTPLATE)) {
            ci.cancel();
        }
    }
}

