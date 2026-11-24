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
 * GeckoLib 4.8.3 GeoItemRenderer cho Dual Knife Song Sắc (`Đỏ–Đen Răng Sóng Tay Phải + Đen–Bạc Móng Vuốt Đồng Tay Trái`):
 * - `GUI`: vẽ icon 32x32 chữ X sắc nét (đủ cả 2 thanh song dao) kèm foil glint.
 * - `GROUND` / `FIXED` (ném ra đất, treo Item Frame) / `HEAD` / `NONE`: vẽ **ĐỦ CẢ 2 THANH** bắt chéo hình chữ X 3D
 *   (tách nhẹ 2 mặt theo trục X để không trùng mặt Z-fighting, 2 mép lưỡi cắt hướng ra ngoài nổi bật cả thanh Đỏ–Đen lẫn Đen–Bạc!).
 * - **Góc nhìn Thứ 3 (F5 / Multiplayer):**
 *   + Giữ nguyên 100% ma trận thế thủ vắt chéo 2 tầng (`THIRD_PERSON_LEFT_HAND` giữ nguyên `XP(112°), YP(180°), ZP(-8°)`).
 *   + Tay Trái tầng trên render thanh Đen–Bạc Móng Vuốt Đồng (`dual_knife_left.geo.json`), Tay Phải tầng dưới render thanh Đỏ–Đen Răng Sóng (`dual_knife.geo.json`).
 * - **Góc nhìn Thứ 1 (First Person):**
 *   + Tách biệt hoàn toàn khỏi F5: cả 2 tay giữ tư thế cầm kiếm cơ bản đối xứng của Vanilla thông qua `dual_knife.json`.
 * - Lớp `DualBladeEmissiveLayer` phát sáng chọn lọc đúng mép cắt & chi tiết của từng thanh.
 */
public final class DualBladeGeoRenderer extends GeoItemRenderer<DualBladeItem> {
    private static final ResourceLocation GUI_ICON_TEXTURE = AbyssalRpgMod.id("textures/item/dual_knife.png");

    private boolean renderingLeftHand;

    public DualBladeGeoRenderer() {
        super(new DualBladeGeoModel());
        this.addRenderLayer(new DualBladeEmissiveLayer(this));
    }

    public boolean isRenderingLeftHand() {
        return this.renderingLeftHand;
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

        // Tỷ lệ hiển thị Dual Knife (mặc định `knifeRenderScale = 0.92` từ WeaponsConfig)
        float knifeScale = WeaponsConfig.get().knifeRenderScale;

        // Khi ném ra đất (GROUND), treo khung tranh (FIXED), đội đầu (HEAD) hoặc hiển thị độc lập (NONE):
        // vẽ ĐỦ CẢ 2 THANH bắt chéo hình chữ X 3D thay vì chỉ hiện 1 thanh!
        if (transformType == ItemDisplayContext.GROUND
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD
                || transformType == ItemDisplayContext.NONE) {
            renderDualDisplayPair(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, knifeScale);
            return;
        }

        boolean isLeftHand = (transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        boolean reverseLeftThird = (transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        if (reverseLeftThird) {
            // GIỮ NGUYÊN 100% Reverse Grip tay trái cho Góc nhìn Thứ 3 (F5) — khớp tuyệt đối thế thủ vắt chéo 2 tầng!
            poseStack.translate(-0.025F, 0.0F, 0.010F);
            poseStack.mulPose(Axis.XP.rotationDegrees(112.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-8.0F));
        }
        poseStack.scale(knifeScale, knifeScale, knifeScale);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderSingleBlade(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, isLeftHand);
        poseStack.popPose();
    }

    private void renderDualDisplayPair(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                                       MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                                       float knifeScale) {
        // Thanh 1: Dao Đỏ–Đen Răng Sóng (nghiêng sang trái -Z, cạnh cắt đỏ hướng ra ngoài, dịch nhẹ +X tránh Z-fighting)
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(knifeScale, knifeScale, knifeScale);
        poseStack.translate(0.055F, 0.26F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-36.0F));
        poseStack.translate(0.0F, -0.26F, 0.0F);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderSingleBlade(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, false);
        poseStack.popPose();

        // Thanh 2: Dao Đen–Bạc Móng Vuốt Đồng & Vòng Đen (xoay đối xứng sang phải +Z, cạnh bạc & móng đồng hướng ra ngoài, dịch nhẹ -X)
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(knifeScale, knifeScale, knifeScale);
        poseStack.translate(-0.055F, 0.26F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-36.0F));
        poseStack.translate(0.0F, -0.26F, 0.0F);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderSingleBlade(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, true);
        poseStack.popPose();
    }

    private void renderSingleBlade(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                                   MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                                   boolean leftHandModel) {
        boolean prevLeft = this.renderingLeftHand;
        this.renderingLeftHand = leftHandModel;
        try {
            super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
        } finally {
            this.renderingLeftHand = prevLeft;
        }
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

    /** Lớp phát sáng chọn lọc: CHỈ mép cắt & mạch sáng của từng thanh (Xanh Kim Cương tay trái / Đỏ Huyết tay phải) phát sáng! */
    private static final class DualBladeEmissiveLayer extends GeoRenderLayer<DualBladeItem> {
        DualBladeEmissiveLayer(DualBladeGeoRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, DualBladeItem animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            boolean isLeft = getRenderer() instanceof DualBladeGeoRenderer dual && dual.isRenderingLeftHand();
            ResourceLocation glowTex = isLeft ? DualBladeGeoModel.GLOW_LEFT : DualBladeGeoModel.GLOW_RIGHT;
            RenderType glowType = RenderType.eyes(glowTex);
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
