package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.renderer.ExcaliburGeoRenderer;
import com.abyssalrpg.item.ExcaliburItem;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel song hình thái cho **Excalibur (Golden)** và **Excalibur Morgan (Saber Alter)**:
 * - `GOLDEN`: Thân kiếm ánh vàng kim & bạc rực rỡ, chắn tay chữ V vàng – xanh hoàng gia, glowmask vàng trắng tinh khiết.
 * - `MORGAN`: Thân kiếm hắc diện thạch khắc 4 vòng cổ ngữ phát sáng tím sẫm + lõi đỏ thẫm, glowmask cổ ngữ chọn lọc.
 */
public final class ExcaliburGeoModel extends GeoModel<ExcaliburItem> {
    public enum Variant {
        GOLDEN,
        MORGAN
    }

    public static final ResourceLocation MODEL_GOLDEN = AbyssalRpgMod.id("geo/item/excalibur.geo.json");
    public static final ResourceLocation MODEL_MORGAN = AbyssalRpgMod.id("geo/item/excalibur_morgan.geo.json");
    public static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/item/excalibur.animation.json");

    public static final ResourceLocation TEX_GOLDEN = AbyssalRpgMod.id("textures/item/excalibur_golden_model.png");
    public static final ResourceLocation TEX_MORGAN = AbyssalRpgMod.id("textures/item/excalibur_morgan_model.png");

    public static final ResourceLocation GLOW_GOLDEN = AbyssalRpgMod.id("textures/item/excalibur_golden_model_glowmask.png");
    public static final ResourceLocation GLOW_MORGAN = AbyssalRpgMod.id("textures/item/excalibur_morgan_model_glowmask.png");

    private static Variant variantOf(@Nullable GeoRenderer<ExcaliburItem> renderer) {
        return renderer instanceof ExcaliburGeoRenderer er ? er.getActiveVariant() : Variant.GOLDEN;
    }

    public static ResourceLocation glowFor(Variant variant) {
        return variant == Variant.MORGAN ? GLOW_MORGAN : GLOW_GOLDEN;
    }

    @Override
    public ResourceLocation getModelResource(ExcaliburItem animatable, @Nullable GeoRenderer<ExcaliburItem> renderer) {
        return variantOf(renderer) == Variant.MORGAN ? MODEL_MORGAN : MODEL_GOLDEN;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(ExcaliburItem animatable) {
        return MODEL_GOLDEN;
    }

    @Override
    public ResourceLocation getTextureResource(ExcaliburItem animatable, @Nullable GeoRenderer<ExcaliburItem> renderer) {
        return variantOf(renderer) == Variant.MORGAN ? TEX_MORGAN : TEX_GOLDEN;
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
