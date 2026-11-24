package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.model.CinderDragonArmorModel;
import com.abyssalrpg.item.CinderDragonArmorItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib 4.8.3 `GeoArmorRenderer` cho Bộ Giáp Cinder Dragon:
 * - `entityCutoutNoCull`: hiển thị 2 mặt sắc nét cho đôi cánh rồng cắt rách răng cưa & hàng gai sống lưng siêu mỏng.
 * - `AutoGlowingGeoLayer`: chỉ phát sáng fullbright tinh tế tại khe lõi dung nham giữa ngực và lõi than ở chóp đuôi
 *   (`textures/armor/cinder_dragon_armor_glowmask.png` — 0 phát sáng lên mũ/cánh/tay/chân).
 * - Kế thừa đầy đủ xoay xương từ `HumanoidModel` sau `PlayerRendererMixin` & `HumanoidModelMixin`
 *   -> tương thích xoay liền khối 100% với `Dual Knife` thế 2 tầng, lướt 15 block & xoay 360°.
 */
public final class CinderDragonArmorRenderer extends GeoArmorRenderer<CinderDragonArmorItem> {
    public CinderDragonArmorRenderer() {
        super(new CinderDragonArmorModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(CinderDragonArmorItem animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
}

