package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.renderer.DualBladeGeoRenderer;
import com.abyssalrpg.item.DualBladeItem;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel song sắc cho Dual Knife (`abyssalrpg:dual_knife`) trên nền Netherite Đen rèn ánh thép (`128x128`):
 * - **Tay Phải (`MAINHAND` — Lưỡi Sóng Đỏ Huyết):** bản lưỡi uy lực, 5 cung lượn sóng răng sắc phát sáng Đỏ Huyết, chắn tay góc cạnh viền đồng cổ.
 * - **Tay Trái (`OFFHAND` — Nanh Xanh Kim Cương):** dáng cong thanh mảnh mượt mà, chuôi có mũi nhọn (`pommel spike`), mép cắt & mạch sáng Xanh Kim Cương.
 */
public final class DualBladeGeoModel extends GeoModel<DualBladeItem> {
    public static final ResourceLocation MODEL_RIGHT = AbyssalRpgMod.id("geo/item/dual_knife.geo.json");
    public static final ResourceLocation MODEL_LEFT = AbyssalRpgMod.id("geo/item/dual_knife_left.geo.json");
    public static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/item/dual_knife.animation.json");
    public static final ResourceLocation TEXTURE_RIGHT = AbyssalRpgMod.id("textures/item/dual_knife_model.png");
    public static final ResourceLocation TEXTURE_LEFT = AbyssalRpgMod.id("textures/item/dual_knife_left_model.png");
    public static final ResourceLocation GLOW_RIGHT = AbyssalRpgMod.id("textures/item/dual_knife_model_glowmask.png");
    public static final ResourceLocation GLOW_LEFT = AbyssalRpgMod.id("textures/item/dual_knife_left_model_glowmask.png");

    private static boolean isLeftHand(@Nullable GeoRenderer<DualBladeItem> renderer) {
        return renderer instanceof DualBladeGeoRenderer dual && dual.isRenderingLeftHand();
    }

    @Override
    public ResourceLocation getModelResource(DualBladeItem animatable, @Nullable GeoRenderer<DualBladeItem> renderer) {
        return isLeftHand(renderer) ? MODEL_LEFT : MODEL_RIGHT;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(DualBladeItem animatable) {
        return MODEL_RIGHT;
    }

    @Override
    public ResourceLocation getTextureResource(DualBladeItem animatable, @Nullable GeoRenderer<DualBladeItem> renderer) {
        return isLeftHand(renderer) ? TEXTURE_LEFT : TEXTURE_RIGHT;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(DualBladeItem animatable) {
        return TEXTURE_RIGHT;
    }

    @Override
    public ResourceLocation getAnimationResource(DualBladeItem animatable) {
        return ANIMATION;
    }
}
