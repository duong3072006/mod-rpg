package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.client.model.ExcaliburGeoModel;
import com.abyssalrpg.client.model.ExcaliburGeoModel.Variant;
import com.abyssalrpg.item.ExcaliburItem;
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
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * GeckoLib 4.8.3 GeoItemRenderer song hình thái cho **Excalibur & Excalibur Morgan**:
 * - `GUI`: tự động chuyển icon 32×32 sắc nét (`excalibur.png` / `excalibur_morgan.png`).
 * - `THIRD_PERSON_RIGHT_HAND`: tỷ lệ chuẩn `0.85x` theo `excalibur.json`, bám khớp lòng bàn tay, 0 chạm đất.
 * - `FIRST_PERSON_RIGHT_HAND`: dáng cầm trường kiếm hiệp sĩ hào hùng, 0 che tâm ngắm.
 * - `ExcaliburEmissiveLayer`: lớp phát sáng chọn lọc full-bright (`RenderType.eyes`) cho lõi vàng trắng thánh khiết
 *   & các vòng cổ ngữ tím sẫm + đỏ thẫm của Excalibur Morgan!
 */
public final class ExcaliburGeoRenderer extends GeoItemRenderer<ExcaliburItem> {
    private static final ResourceLocation GUI_ICON_GOLDEN = AbyssalRpgMod.id("textures/item/excalibur.png");
    private static final ResourceLocation GUI_ICON_MORGAN = AbyssalRpgMod.id("textures/item/excalibur_morgan.png");

    private static int currentRenderEntityId = -1;

    private Variant activeVariant = Variant.GOLDEN;
    private float chargeBoost = 0.0F;

    public ExcaliburGeoRenderer() {
        super(new ExcaliburGeoModel());
        this.addRenderLayer(new ExcaliburEmissiveLayer(this));
        this.addRenderLayer(new ExcaliburHighlightLayer(this));
    }

    public static void setCurrentRenderEntityId(int entityId) {
        currentRenderEntityId = entityId;
    }

    public Variant getActiveVariant() {
        return this.activeVariant;
    }

    public float getChargeBoost() {
        return this.chargeBoost;
    }

    @Override
    public RenderType getRenderType(ExcaliburItem animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        boolean isMorgan = ExcaliburItem.isMorgan(stack);
        if (transformType == ItemDisplayContext.GUI) {
            renderCrispGuiIcon(stack, isMorgan ? GUI_ICON_MORGAN : GUI_ICON_GOLDEN, poseStack, bufferSource);
            return;
        }

        Variant variant = isMorgan ? Variant.MORGAN : Variant.GOLDEN;
        int entityId = currentRenderEntityId;
        if (entityId == -1) {
            var lp = Minecraft.getInstance().player;
            if (lp != null && lp.getMainHandItem() == stack) {
                entityId = lp.getId();
            }
        }
        float boost = (entityId != -1) ? ExcaliburAnimManager.getGlowBoost(entityId) : 0.0F;

        if (transformType == ItemDisplayContext.GROUND
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD
                || transformType == ItemDisplayContext.NONE) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(1.06F, 1.08F, 1.06F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-18.0F));
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, variant, boost);
            poseStack.popPose();
            return;
        }

        boolean isFirstPerson = (transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        if (isFirstPerson) {
            // Tỷ lệ gọn 2/3 cân đối sắc nét cho cả 2 dạng (thấy rõ chắn chữ V, highlight & cổ ngữ, 0 quá dài hay che tâm ngắm!)
            poseStack.translate(0.24F, -0.26F, -0.26F);
            poseStack.mulPose(Axis.YP.rotationDegrees(188.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(4.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(13.0F));
            poseStack.scale(1.00F, 0.96F, 1.00F);
        }
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, variant, boost);
        poseStack.popPose();
    }

    private void renderVariant(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                               MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                               Variant variant, float boost) {
        Variant prevVar = this.activeVariant;
        float prevBoost = this.chargeBoost;
        this.activeVariant = variant;
        this.chargeBoost = boost;

        int effectiveLight = packedLight;
        if (variant == Variant.GOLDEN) {
            int blk = Math.max(LightTexture.block(packedLight), 13);
            int sky = Math.max(LightTexture.sky(packedLight), 14);
            effectiveLight = LightTexture.pack(blk, sky);
        }
        try {
            super.renderByItem(stack, transformType, poseStack, bufferSource, effectiveLight, packedOverlay);
        } finally {
            this.activeVariant = prevVar;
            this.chargeBoost = prevBoost;
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

    private static final class ExcaliburEmissiveLayer extends GeoRenderLayer<ExcaliburItem> {
        ExcaliburEmissiveLayer(ExcaliburGeoRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, ExcaliburItem animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource,
                           @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!(getRenderer() instanceof ExcaliburGeoRenderer er)) {
                return;
            }
            Variant var = er.getActiveVariant();
            float boost = er.getChargeBoost();
            Minecraft mc = Minecraft.getInstance();
            float timeSec = (mc.level != null ? ((mc.level.getGameTime() % 240000L) + partialTick) : partialTick) / 20.0F;

            float speed = (var == Variant.MORGAN) ? 4.8F : 2.8F;
            float baseLo = (var == Variant.MORGAN) ? 0.84F : 0.80F;
            float pulse = Mth.lerp(0.5F - 0.5F * Mth.cos(timeSec * speed), baseLo, 1.00F);
            pulse = Mth.clamp(pulse + boost * 0.25F, 0.0F, 1.0F);

            int r = Mth.clamp(Math.round(255.0F * pulse), 0, 255);
            int g = Mth.clamp(Math.round(255.0F * pulse), 0, 255);
            int b = Mth.clamp(Math.round(255.0F * pulse), 0, 255);
            int packedCol = FastColor.ARGB32.color(255, r, g, b);

            ResourceLocation glowTex = ExcaliburGeoModel.glowFor(var);
            RenderType glowType = RenderType.eyes(glowTex);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, packedCol);
        }
    }

    /**
     * Lớp Highlight / Rim-Glow 3D nổi bật bao quanh lưỡi kiếm cho CẢ 2 DẠNG:
     * - Golden Excalibur: viền ánh sáng Vàng–Trắng Thánh Khiết tỏa rực sắc nét dọc mép lưỡi & chắn tay!
     * - Excalibur Morgan: viền ánh sáng Đỏ Thẫm (Crimson Highlight) rực cháy quanh thân hắc diện thạch!
     */
    private static final class ExcaliburHighlightLayer extends GeoRenderLayer<ExcaliburItem> {
        ExcaliburHighlightLayer(ExcaliburGeoRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, ExcaliburItem animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource,
                           @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!(getRenderer() instanceof ExcaliburGeoRenderer er)) {
                return;
            }
            Variant var = er.getActiveVariant();
            float boost = er.getChargeBoost();
            Minecraft mc = Minecraft.getInstance();
            float timeSec = (mc.level != null ? ((mc.level.getGameTime() % 240000L) + partialTick) : partialTick) / 20.0F;
            float shimmer = Mth.lerp(0.5F + 0.5F * Mth.sin(timeSec * (var == Variant.MORGAN ? 4.2F : 3.0F)), 0.72F, 1.0F);
            float intensity = Mth.clamp(shimmer + boost * 0.28F, 0.0F, 1.0F);

            int r, g, b;
            if (var == Variant.MORGAN) {
                r = Mth.clamp(Math.round(255.0F * intensity), 0, 255);
                g = Mth.clamp(Math.round(42.0F * intensity), 0, 255);
                b = Mth.clamp(Math.round(52.0F * intensity), 0, 255);
            } else {
                r = Mth.clamp(Math.round(255.0F * intensity), 0, 255);
                g = Mth.clamp(Math.round(234.0F * intensity), 0, 255);
                b = Mth.clamp(Math.round(128.0F * intensity), 0, 255);
            }
            int col = FastColor.ARGB32.color(255, r, g, b);

            ResourceLocation glowTex = ExcaliburGeoModel.glowFor(var);
            RenderType rimType = RenderType.eyes(glowTex);

            poseStack.pushPose();
            // Phóng nhẹ 4.5% quanh tâm lưỡi tạo viền Highlight ánh sáng mượt mà 3D (không che chi tiết cổ ngữ!)
            poseStack.scale(1.045F, 1.012F, 1.045F);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, rimType,
                    bufferSource.getBuffer(rimType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, col);
            poseStack.popPose();
        }
    }
}
