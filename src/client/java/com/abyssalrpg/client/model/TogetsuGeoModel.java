package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.renderer.TogetsuGeoRenderer;
import com.abyssalrpg.item.TogetsuItem;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel 3 biến thể cho **Togetsu (Trăng Non · Trăng Khuyết / Đại Đao Song Nguyệt)**:
 * - `WAXING` (Tay Trái — Lưỡi Trắng Ngọc, dải ruy-băng đen).
 * - `WANING` (Tay Phải — Lưỡi Đen Obsidian, dải ruy-băng trắng).
 * - `FUSED`  (Hợp Nhất **Song Nguyệt · Sōgengetsu** — 1 thanh Đại Đao Trăng Khuyết khổng lồ tay cầm vòng D kép!).
 */
public final class TogetsuGeoModel extends GeoModel<TogetsuItem> {
    public enum Variant {
        WAXING,
        WANING,
        FUSED
    }

    public static final ResourceLocation MODEL_WAXING = AbyssalRpgMod.id("geo/item/togetsu_waxing.geo.json");
    public static final ResourceLocation MODEL_WANING = AbyssalRpgMod.id("geo/item/togetsu_waning.geo.json");
    public static final ResourceLocation MODEL_FUSED = AbyssalRpgMod.id("geo/item/togetsu_fused.geo.json");
    public static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/item/togetsu.animation.json");

    public static final ResourceLocation TEX_WAXING = AbyssalRpgMod.id("textures/item/togetsu_waxing_model.png");
    public static final ResourceLocation TEX_WANING = AbyssalRpgMod.id("textures/item/togetsu_waning_model.png");
    public static final ResourceLocation TEX_FUSED = AbyssalRpgMod.id("textures/item/togetsu_fused_model.png");

    public static final ResourceLocation GLOW_WAXING = AbyssalRpgMod.id("textures/item/togetsu_waxing_model_glowmask.png");
    public static final ResourceLocation GLOW_WANING = AbyssalRpgMod.id("textures/item/togetsu_waning_model_glowmask.png");
    public static final ResourceLocation GLOW_FUSED = AbyssalRpgMod.id("textures/item/togetsu_fused_model_glowmask.png");

    private static Variant variantOf(@Nullable GeoRenderer<TogetsuItem> renderer) {
        return renderer instanceof TogetsuGeoRenderer tr ? tr.getActiveVariant() : Variant.WANING;
    }

    public static ResourceLocation glowFor(Variant variant) {
        return switch (variant) {
            case WAXING -> GLOW_WAXING;
            case FUSED -> GLOW_FUSED;
            case WANING -> GLOW_WANING;
        };
    }

    @Override
    public ResourceLocation getModelResource(TogetsuItem animatable, @Nullable GeoRenderer<TogetsuItem> renderer) {
        return switch (variantOf(renderer)) {
            case WAXING -> MODEL_WAXING;
            case FUSED -> MODEL_FUSED;
            case WANING -> MODEL_WANING;
        };
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(TogetsuItem animatable) {
        return MODEL_WANING;
    }

    @Override
    public ResourceLocation getTextureResource(TogetsuItem animatable, @Nullable GeoRenderer<TogetsuItem> renderer) {
        return switch (variantOf(renderer)) {
            case WAXING -> TEX_WAXING;
            case FUSED -> TEX_FUSED;
            case WANING -> TEX_WANING;
        };
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(TogetsuItem animatable) {
        return TEX_WANING;
    }

    @Override
    public ResourceLocation getAnimationResource(TogetsuItem animatable) {
        return ANIMATION;
    }
}
