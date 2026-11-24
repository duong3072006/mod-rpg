package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.model.DualBladeGeoModel;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.item.DualBladeItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * GeckoLib 4.8.3 GeoItemRenderer cho Song Đao Netherite–Kim Cương (`Standard Right + Reverse Left Grip`):
 * - `GUI`: vẽ icon 32x32 chữ X sắc nét (1 thuận + 1 ngược) kèm foil glint.
 * - **Tay Phải (`FIRST_PERSON_RIGHT_HAND` / `THIRD_PERSON_RIGHT_HAND`):** Cầm Thẳng (`Standard Grip`), mũi đao vươn về trước–lên.
 * - **Tay Trái (`FIRST_PERSON_LEFT_HAND` / `THIRD_PERSON_LEFT_HAND`):** Cầm Ngược (`Reverse Grip`), xoay 180° chuẩn tâm chuôi
 *   + đẩy nhẹ ra mặt ngoài cẳng tay trái sao cho lưỡi đao cong kéo ngược dọc theo mặt ngoài cẳng tay (0 xuyên cẳng tay!).
 * - Lớp `DualBladeEmissiveLayer` chỉ phát sáng chọn lọc đúng mép lưỡi Xanh Kim Cương, chóp mũi & rãnh khảm mảnh.
 */
public final class DualBladeGeoRenderer extends GeoItemRenderer<DualBladeItem> {
    private static final ResourceLocation GUI_ICON_TEXTURE = AbyssalRpgMod.id("textures/item/dual_knife.png");

    public DualBladeGeoRenderer() {
        super(new DualBladeGeoModel());
        this.addRenderLayer(new DualBladeEmissiveLayer(this));
    }

    @Override
    public RenderType getRenderType(DualBladeItem animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (transformType == ItemDisplayContext.GUI) {
            renderCrispGuiIcon(stack, poseStack, bufferSource);
            return;
        }

        // Thu nhỏ đúng tỷ lệ song dao găm sát thủ gọn sắc (mặc định `knifeRenderScale = 0.72` từ WeaponsConfig, chỉnh nhanh ±15%!)
        float knifeScale = WeaponsConfig.get().knifeRenderScale;
        boolean reverseLeftThird = (transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        boolean reverseLeftFirst = (transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        if (reverseLeftThird) {
            // Reverse Grip chuẩn hình học tay trái cho Dao Găm Sát Thủ gọn sắc (~0.79 block):
            // khớp 1:1 bộ giải ma trận `generate_dual_knife_model.py` (`LEFT_GEO_REV_R/T`) -> 0 xuyên ngực/mặt/giáp!
            poseStack.translate(-0.025F, 0.0F, 0.010F);
            poseStack.mulPose(Axis.XP.rotationDegrees(112.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-8.0F));
        } else if (reverseLeftFirst) {
            // Góc nhìn Thứ 1 tay trái: chuôi trên-trái, lưỡi dao găm thon dốc chéo gọn sát rìa trái màn hình (0 che tâm ngắm!)
            poseStack.translate(-0.02F, 0.08F, -0.02F);
            poseStack.mulPose(Axis.XP.rotationDegrees(148.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-12.0F));
        }
        poseStack.scale(knifeScale, knifeScale, knifeScale);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void renderCrispGuiIcon(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource) {
        poseStack.pushPose();
        RenderType type = RenderType.entityTranslucent(GUI_ICON_TEXTURE);
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(bufferSource, type, true, stack.hasFoil());
        PoseStack.Pose pose = poseStack.last();
        float z = 0.5F;

        vc.addVertex(pose, 0.0F, 0.0F, z)
                .setColor(255, 255, 255, 255).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 1.0F, 0.0F, z)
                .setColor(255, 255, 255, 255).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 1.0F, 1.0F, z)
                .setColor(255, 255, 255, 255).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 0.0F, 1.0F, z)
                .setColor(255, 255, 255, 255).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);

        poseStack.popPose();
    }

    /** Lớp phát sáng chọn lọc: CHỈ mép cắt xanh kim cương, chóp mũi & rãnh khảm mảnh phát sáng lạnh trong bóng tối! */
    private static final class DualBladeEmissiveLayer extends GeoRenderLayer<DualBladeItem> {
        DualBladeEmissiveLayer(DualBladeGeoRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, DualBladeItem animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            RenderType glowType = RenderType.eyes(DualBladeGeoModel.GLOW);
            Minecraft mc = Minecraft.getInstance();
            float timeSec = (mc.level != null ? (mc.level.getGameTime() + partialTick) : partialTick) / 20.0F;
            float pulse = Mth.lerp(0.5F - 0.5F * Mth.cos(timeSec * 2.8F), 0.90F, 1.00F);
            int ch = Mth.clamp(Math.round(pulse * 255.0F), 220, 255);
            int packedColor = (ch << 24) | (ch << 16) | (ch << 8) | ch;

            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, packedColor);
        }
    }
}
