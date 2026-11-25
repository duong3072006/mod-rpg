package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.model.NightmareArmorModel;
import com.abyssalrpg.item.NightmareArmorItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib 4.8.3 `GeoArmorRenderer` cho Bộ Giáp Nightmare Knight v4:
 * - `entityCutoutNoCull`: hiển thị 2 mặt sắc nét cho đôi cánh dơi đỏ rách, áo choàng toàn lưng & khố trước.
 * - `AutoGlowingGeoLayer`: chỉ phát sáng fullbright cho Trái Tim **Nightmare Core** trong lồng ngực mở
 *   (`textures/armor/nightmare_armor_glowmask.png` — toàn bộ phần giáp còn lại giữ nguyên 0 phát sáng).
 */
public final class NightmareArmorRenderer extends GeoArmorRenderer<NightmareArmorItem> {
    public NightmareArmorRenderer() {
        super(new NightmareArmorModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(NightmareArmorItem animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
