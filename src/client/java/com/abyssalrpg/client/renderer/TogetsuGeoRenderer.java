package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.model.TogetsuGeoModel;
import com.abyssalrpg.client.model.TogetsuGeoModel.Variant;
import com.abyssalrpg.item.TogetsuItem;
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
 * GeckoLib 4.8.3 GeoItemRenderer song hình thái cho **Togetsu (`abyssalrpg:togetsu`)**:
 * - `GUI`: tự đổi icon 32×32 sắc nét giữa cặp song đao (`togetsu.png`) và Đại Đao Song Nguyệt (`togetsu_fused.png`).
 * - `GROUND` / `FIXED` / `HEAD` / `NONE`:
 *   + Khi ở dạng tách rời: hiển thị ĐỦ CẢ 2 CÂY Trăng Non (Trắng) & Trăng Khuyết (Đen) bắt chéo chữ X 3D!
 *   + Khi ở dạng Hợp Nhất: hiển thị 1 thanh Đại Đao Song Nguyệt khổng lồ!
 * - **Cầm tay (F5 & First Person):**
 *   + Đồng bộ thời điểm chuyển đổi model ngay giữa pha animation Hợp Nhất (`FUSE_TRANSFORM` ở mốc 50%) và Tách (`UNFUSE_SPLIT` ở mốc 40%)!
 *   + Dạng tách: Tay Trái = **Trăng Non (Trắng)**, Tay Phải = **Trăng Khuyết (Đen)** (không đảo ngược gốc vì ma trận Sunraku giải trực tiếp!).
 *   + Dạng Hợp Nhất: Tay Phải = **Đại Đao Song Nguyệt (`Sōgengetsu`)** khổng lồ, Tay Trái ẩn bản sao để cầm bằng 2 tay!
 */
public final class TogetsuGeoRenderer extends GeoItemRenderer<TogetsuItem> {
    private static final ResourceLocation GUI_ICON_DUAL = AbyssalRpgMod.id("textures/item/togetsu.png");
    private static final ResourceLocation GUI_ICON_FUSED = AbyssalRpgMod.id("textures/item/togetsu_fused.png");

    private static int currentRenderEntityId = -1;

    private Variant activeVariant = Variant.WANING;
    private boolean currentEmpowered = false;

    public TogetsuGeoRenderer() {
        super(new TogetsuGeoModel());
        this.addRenderLayer(new TogetsuEmissiveLayer(this));
    }

    public static void setCurrentRenderEntityId(int entityId) {
        currentRenderEntityId = entityId;
    }

    public Variant getActiveVariant() {
        return this.activeVariant;
    }

    public boolean isCurrentEmpowered() {
        return this.currentEmpowered;
    }

    @Override
    public RenderType getRenderType(TogetsuItem animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        boolean stackFused = TogetsuItem.isFused(stack);
        if (transformType == ItemDisplayContext.GUI) {
            renderCrispGuiIcon(stack, stackFused ? GUI_ICON_FUSED : GUI_ICON_DUAL, poseStack, bufferSource);
            return;
        }

        boolean empowered = TogetsuItem.isWaxingEmpowered(stack);

        if (transformType == ItemDisplayContext.GROUND
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD
                || transformType == ItemDisplayContext.NONE) {
            if (stackFused) {
                poseStack.pushPose();
                poseStack.translate(0.5F, 0.5F, 0.5F);
                poseStack.scale(0.84F, 0.84F, 0.84F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-22.0F));
                poseStack.translate(-0.5F, -0.5F, -0.5F);
                renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.FUSED, empowered);
                poseStack.popPose();
            } else {
                renderDualDisplayPair(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, empowered);
            }
            return;
        }

        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        boolean renderFused = (currentRenderEntityId != -1)
                ? TogetsuAnimManager.shouldRenderAsFused(currentRenderEntityId, stack, partialTick)
                : stackFused;

        boolean isLeftHand = (transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        boolean isFirstPerson = (transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);

        if (renderFused) {
            // Ở trạng thái Đại Đao Song Nguyệt chỉ hiển thị 1 thanh Đại Đao duy nhất ở tay phải!
            if (isLeftHand) {
                return;
            }
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            if (isFirstPerson) {
                // Xoay xuôi đúng chiều ở Góc nhìn Thứ 1 (cạnh cắt sáng hướng lên-trước, hết bị ngược như ảnh cũ!)
                poseStack.translate(0.06F, -0.04F, -0.04F);
                poseStack.mulPose(Axis.YP.rotationDegrees(194.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(20.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(-12.0F));
                poseStack.scale(0.76F, 0.76F, 0.76F);
            } else {
                poseStack.scale(1.18F, 1.18F, 1.18F);
            }
            // Dịch phần tay cầm vô sát lòng bàn tay nhân vật hơn (`y -= 1.8px, z -= 0.55px`) để cầm cực chắc chắn!
            poseStack.translate(0.0F, -0.1125F, -0.0344F);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.FUSED, empowered);
            poseStack.popPose();
            return;
        }

        // Dạng Song Đao (thu gọn tỉ lệ thanh thoát 1:1 khớp với Dual Knife: `0.78F` F5 / `0.74F` FP!)
        Variant variant = isLeftHand ? Variant.WAXING : Variant.WANING;
        float scale = isFirstPerson ? 0.74F : 0.78F;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, variant, empowered);
        poseStack.popPose();
    }

    private void renderDualDisplayPair(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                                       MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                                       boolean empowered) {
        // Thanh 1: Trăng Khuyết (Waning Đen) nghiêng sang trái
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(0.78F, 0.78F, 0.78F);
        poseStack.translate(0.055F, 0.24F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-36.0F));
        poseStack.translate(0.0F, -0.24F, 0.0F);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.WANING, empowered);
        poseStack.popPose();

        // Thanh 2: Trăng Non (Waxing Trắng) đối xứng nghiêng sang phải tạo chữ X Âm–Dương!
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(0.78F, 0.78F, 0.78F);
        poseStack.translate(-0.055F, 0.24F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-36.0F));
        poseStack.translate(0.0F, -0.24F, 0.0F);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.WAXING, empowered);
        poseStack.popPose();
    }

    private void renderVariant(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                               MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                               Variant variant, boolean empowered) {
        Variant prevVar = this.activeVariant;
        boolean prevEmp = this.currentEmpowered;
        this.activeVariant = variant;
        this.currentEmpowered = empowered;
        try {
            super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
        } finally {
            this.activeVariant = prevVar;
            this.currentEmpowered = prevEmp;
        }
    }

    private static void renderCrispGuiIcon(ItemStack stack, ResourceLocation tex, PoseStack poseStack,
                                           MultiBufferSource bufferSource) {
        poseStack.pushPose();
        RenderType type = RenderType.entityTranslucent(tex);
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

    private static final class TogetsuEmissiveLayer extends GeoRenderLayer<TogetsuItem> {
        TogetsuEmissiveLayer(TogetsuGeoRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, TogetsuItem animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            if (!(getRenderer() instanceof TogetsuGeoRenderer tr)) {
                return;
            }
            Variant var = tr.getActiveVariant();
            ResourceLocation glowTex = TogetsuGeoModel.glowFor(var);
            RenderType glowType = RenderType.eyes(glowTex);

            Minecraft mc = Minecraft.getInstance();
            float timeSec = (mc.level != null ? (mc.level.getGameTime() + partialTick) : partialTick) / 20.0F;
            float basePulse = Mth.lerp(0.5F - 0.5F * Mth.cos(timeSec * (tr.isCurrentEmpowered() ? 6.5F : 2.6F)),
                    var == Variant.FUSED ? 0.90F : 0.78F, 1.00F);
            int ch = Mth.clamp(Math.round(basePulse * 255.0F), 195, 255);
            int packedColor = (ch << 24) | (ch << 16) | (ch << 8) | ch;

            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, packedColor);
        }
    }
}
