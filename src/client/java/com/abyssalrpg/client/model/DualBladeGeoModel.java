package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.item.DualBladeItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel cho Dual Knife (`abyssalrpg:dual_knife`) chất liệu Netherite Đen Rèn Damascus pha Mép Cắt Xanh Dương Kim Cương (`64x64`):
 * - Thân Netherite ăn sáng/bóng thật; chỉ mép cắt xanh kim cương, chóp mũi & rãnh khảm mảnh phát sáng trên `_glowmask.png`.
 */
public final class DualBladeGeoModel extends GeoModel<DualBladeItem> {
    public static final ResourceLocation MODEL = AbyssalRpgMod.id("geo/item/dual_knife.geo.json");
    public static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/item/dual_knife.animation.json");
    public static final ResourceLocation TEXTURE = AbyssalRpgMod.id("textures/item/dual_knife_model.png");
    public static final ResourceLocation GLOW = AbyssalRpgMod.id("textures/item/dual_knife_model_glowmask.png");

    @Override
    public ResourceLocation getModelResource(DualBladeItem animatable, GeoRenderer<DualBladeItem> renderer) {
        return MODEL;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(DualBladeItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(DualBladeItem animatable, GeoRenderer<DualBladeItem> renderer) {
        return TEXTURE;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(DualBladeItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(DualBladeItem animatable) {
        return ANIMATION;
    }
}
