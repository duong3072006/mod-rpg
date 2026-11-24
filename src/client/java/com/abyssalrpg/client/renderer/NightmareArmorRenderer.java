package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.model.NightmareArmorModel;
import com.abyssalrpg.item.NightmareArmorItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * GeckoLib 4.8.3 `GeoArmorRenderer` cho Bộ Giáp Nightmare Knight (KHÔNG glowmask / KHÔNG AutoGlowingGeoLayer):
 * - Kế thừa đầy đủ biến đổi xương từ `HumanoidModel` sau `PlayerRendererMixin` & `HumanoidModelMixin`
 *   -> tương thích tự động 100% với tư thế 2 tầng, xoay 360° Cyclone và Flash Dash của Dual Knife.
 * - `entityCutoutNoCull` giúp vạt áo choàng đỏ rượu siêu mỏng cắt răng cưa 2 mép & gấu áo hiển thị 2 mặt sắc nét.
 */
public final class NightmareArmorRenderer extends GeoArmorRenderer<NightmareArmorItem> {
    public NightmareArmorRenderer() {
        super(new NightmareArmorModel());
    }

    @Override
    public RenderType getRenderType(NightmareArmorItem animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
