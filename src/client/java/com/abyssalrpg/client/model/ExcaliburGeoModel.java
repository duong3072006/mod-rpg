package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.item.ExcaliburItem;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel đơn hình thái cho **Excalibur (Thánh Kiếm Vàng Kim)**.
 */
public final class ExcaliburGeoModel extends GeoModel<ExcaliburItem> {
    public static final ResourceLocation MODEL_GOLDEN = AbyssalRpgMod.id("geo/item/excalibur.geo.json");
    public static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/item/excalibur.animation.json");
    public static final ResourceLocation TEX_GOLDEN = AbyssalRpgMod.id("textures/item/excalibur_golden_model.png");
    public static final ResourceLocation GLOW_GOLDEN = AbyssalRpgMod.id("textures/item/excalibur_golden_model_glowmask.png");

    @Override
    public ResourceLocation getModelResource(ExcaliburItem animatable, @Nullable GeoRenderer<ExcaliburItem> renderer) {
        return MODEL_GOLDEN;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(ExcaliburItem animatable) {
        return MODEL_GOLDEN;
    }

    @Override
    public ResourceLocation getTextureResource(ExcaliburItem animatable, @Nullable GeoRenderer<ExcaliburItem> renderer) {
        return TEX_GOLDEN;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(ExcaliburItem animatable) {
        return TEX_GOLDEN;
    }

    @Override
    public ResourceLocation getAnimationResource(ExcaliburItem animatable) {
        return ANIMATION;
    }
}
