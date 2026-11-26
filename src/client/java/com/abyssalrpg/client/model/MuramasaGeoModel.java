package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.renderer.MuramasaGeoRenderer;
import com.abyssalrpg.item.MuramasaItem;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel 2 biến thể song thủ cho **Yêu Đao Muramasa Katana & Vỏ Kiếm Rồng Đỏ (Saya)**:
 * - `KATANA` (Tay Phải): Lưỡi thép rèn đen Damascus khắc chữ `村正`, cạnh lưỡi vàng phát sáng rực (`#FFE820`),
 *   tsuba sao quỷ 12 mũi sắt cổ, chuôi da đỏ quấn vải đen đan quả trám & nắp kashira mặt quỷ.
 * - `SCABBARD` (Tay Trái khi ô tay phụ trống): Vỏ kiếm sơn mài đen uốn cong vẽ rồng đỏ & khóa miệng sắt cổ.
 */
public final class MuramasaGeoModel extends GeoModel<MuramasaItem> {
    public enum Variant {
        KATANA,
        SCABBARD
    }

    public static final ResourceLocation MODEL_KATANA = AbyssalRpgMod.id("geo/item/muramasa.geo.json");
    public static final ResourceLocation MODEL_SCABBARD = AbyssalRpgMod.id("geo/item/muramasa_scabbard.geo.json");
    public static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/item/muramasa.animation.json");

    public static final ResourceLocation TEX_KATANA = AbyssalRpgMod.id("textures/item/muramasa_model.png");
    public static final ResourceLocation TEX_SCABBARD = AbyssalRpgMod.id("textures/item/muramasa_scabbard_model.png");

    public static final ResourceLocation GLOW_KATANA = AbyssalRpgMod.id("textures/item/muramasa_model_glowmask.png");
    public static final ResourceLocation GLOW_SCABBARD = AbyssalRpgMod.id("textures/item/muramasa_scabbard_model_glowmask.png");

    private static Variant variantOf(@Nullable GeoRenderer<MuramasaItem> renderer) {
        return renderer instanceof MuramasaGeoRenderer mr ? mr.getActiveVariant() : Variant.KATANA;
    }

    public static ResourceLocation glowFor(Variant variant) {
        return variant == Variant.SCABBARD ? GLOW_SCABBARD : GLOW_KATANA;
    }

    @Override
    public ResourceLocation getModelResource(MuramasaItem animatable, @Nullable GeoRenderer<MuramasaItem> renderer) {
        return variantOf(renderer) == Variant.SCABBARD ? MODEL_SCABBARD : MODEL_KATANA;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(MuramasaItem animatable) {
        return MODEL_KATANA;
    }

    @Override
    public ResourceLocation getTextureResource(MuramasaItem animatable, @Nullable GeoRenderer<MuramasaItem> renderer) {
        return variantOf(renderer) == Variant.SCABBARD ? TEX_SCABBARD : TEX_KATANA;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(MuramasaItem animatable) {
        return TEX_KATANA;
    }

    @Override
    public ResourceLocation getAnimationResource(MuramasaItem animatable) {
        return ANIMATION;
    }
}
