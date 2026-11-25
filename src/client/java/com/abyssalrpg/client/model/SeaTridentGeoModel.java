package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.renderer.SeaTridentGeoRenderer;
import com.abyssalrpg.item.SeaTridentItem;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel song mô hình cho **Hải Thần Đinh Ba & Đăng Hải**:
 * - Tay Phải (`Variant.TRIDENT`): `sea_trident.geo.json` + `sea_trident_model.png` + `sea_trident_model_glowmask.png`.
 * - Tay Trái (`Variant.LANTERN`): `sea_lantern.geo.json` + `sea_lantern_model.png` + `sea_lantern_model_glowmask.png`.
 */
public final class SeaTridentGeoModel extends GeoModel<SeaTridentItem> {
    public enum Variant {
        TRIDENT,
        LANTERN
    }

    private static final ResourceLocation GEO_TRIDENT = AbyssalRpgMod.id("geo/item/sea_trident.geo.json");
    private static final ResourceLocation GEO_LANTERN = AbyssalRpgMod.id("geo/item/sea_lantern.geo.json");

    private static final ResourceLocation TEX_TRIDENT = AbyssalRpgMod.id("textures/item/sea_trident_model.png");
    private static final ResourceLocation TEX_LANTERN = AbyssalRpgMod.id("textures/item/sea_lantern_model.png");

    private static final ResourceLocation GLOW_TRIDENT = AbyssalRpgMod.id("textures/item/sea_trident_model_glowmask.png");
    private static final ResourceLocation GLOW_LANTERN = AbyssalRpgMod.id("textures/item/sea_lantern_model_glowmask.png");

    private static final ResourceLocation ANIM = AbyssalRpgMod.id("animations/item/sea_weapons.animation.json");

    private static Variant variantOf(@Nullable GeoRenderer<SeaTridentItem> renderer) {
        return renderer instanceof SeaTridentGeoRenderer sr ? sr.getActiveVariant() : Variant.TRIDENT;
    }

    public static ResourceLocation glowFor(Variant variant) {
        return variant == Variant.LANTERN ? GLOW_LANTERN : GLOW_TRIDENT;
    }

    @Override
    public ResourceLocation getModelResource(SeaTridentItem animatable, @Nullable GeoRenderer<SeaTridentItem> renderer) {
        return variantOf(renderer) == Variant.LANTERN ? GEO_LANTERN : GEO_TRIDENT;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(SeaTridentItem animatable) {
        return GEO_TRIDENT;
    }

    @Override
    public ResourceLocation getTextureResource(SeaTridentItem animatable, @Nullable GeoRenderer<SeaTridentItem> renderer) {
        return variantOf(renderer) == Variant.LANTERN ? TEX_LANTERN : TEX_TRIDENT;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(SeaTridentItem animatable) {
        return TEX_TRIDENT;
    }

    @Override
    public ResourceLocation getAnimationResource(SeaTridentItem animatable) {
        return ANIM;
    }
}
