package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.anim.SeaWeaponsAnimManager;
import com.abyssalrpg.client.model.SeaTridentGeoModel;
import com.abyssalrpg.client.model.SeaTridentGeoModel.Variant;
import com.abyssalrpg.item.SeaTridentItem;
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
 * GeckoLib 4.8.3 GeoItemRenderer song thủ cho **Hải Thần Đinh Ba (Tay Phải) & Đèn Lồng Biển (Tay Trái)**:
 * - `GUI`: Icon 32×32 sắc nét hiển thị đồng thời Đinh Ba & Đèn Lồng.
 * - `GROUND` / `FIXED`: Hiển thị bộ đôi Đinh Ba + Đèn Lồng cạnh nhau đúng bố cục Sketchfab!
 * - `THIRD_PERSON_RIGHT_HAND` / `FIRST_PERSON_RIGHT_HAND`: Đinh Ba Biển với dải sóng nước xoắn 360° phát sáng chọn lọc.
 * - `THIRD_PERSON_LEFT_HAND` / `FIRST_PERSON_LEFT_HAND`: Đèn Lồng Biển treo gọn từ khoen sắt với lõi lam phát sáng nhịp thở!
 */
public final class SeaTridentGeoRenderer extends GeoItemRenderer<SeaTridentItem> {
    private static final ResourceLocation GUI_ICON = AbyssalRpgMod.id("textures/item/sea_trident.png");
    private static int currentRenderEntityId = -1;

    private Variant activeVariant = Variant.TRIDENT;
    private float glowBoost = 0.0F;

    public SeaTridentGeoRenderer() {
        super(new SeaTridentGeoModel());
        this.addRenderLayer(new SeaEmissiveLayer(this));
    }

    public static void setCurrentRenderEntityId(int entityId) {
        currentRenderEntityId = entityId;
    }

    public Variant getActiveVariant() {
        return this.activeVariant;
    }

    public float getGlowBoost() {
        return this.glowBoost;
    }

    @Override
    public RenderType getRenderType(SeaTridentItem animatable, ResourceLocation texture,
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

        if (transformType == ItemDisplayContext.GROUND
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD
                || transformType == ItemDisplayContext.NONE) {
            // Hiển thị cả Đinh Ba (trái) & Đèn Lồng (phải) đúng bố cục Sketchfab!
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.translate(-0.14F, 0.06F, 0.0F);
            poseStack.scale(0.68F, 0.68F, 0.68F);
            poseStack.mulPose(Axis.YP.rotationDegrees(16.0F));
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.TRIDENT, 0.0F);
            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.translate(0.28F, 0.08F, 0.06F);
            poseStack.scale(0.66F, 0.66F, 0.66F);
            poseStack.mulPose(Axis.YP.rotationDegrees(-28.0F));
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, Variant.LANTERN, 0.0F);
            poseStack.popPose();
            return;
        }

        boolean isLeftHand = (transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        boolean isFirstPerson = (transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);

        int entityId = currentRenderEntityId;
        if (entityId == -1) {
            var lp = Minecraft.getInstance().player;
            if (lp != null && lp.getMainHandItem() == stack) {
                entityId = lp.getId();
            }
        }
        float boost = (entityId != -1) ? SeaWeaponsAnimManager.getCastGlowBoost(entityId, isLeftHand) : 0.0F;
        Variant variant = isLeftHand ? Variant.LANTERN : Variant.TRIDENT;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        if (isLeftHand) {
            // Đèn Lồng Biển ở Tay Trái: treo gọn từ khoen sắt, hướng rêu tảo & lõi phát sáng đẹp mắt!
            if (isFirstPerson) {
                poseStack.translate(0.00F, 0.15F, -0.12F);
                poseStack.mulPose(Axis.YP.rotationDegrees(-22.0F));
                poseStack.scale(0.85F, 0.85F, 0.85F);
            } else {
                // F5: giữ ma trận xoay sạch để SeaWeaponsAnimManager khóa chuẩn phương thẳng đứng & đung đưa trọng lực!
                poseStack.scale(0.84F, 0.84F, 0.84F);
            }
        } else {
            // Đinh Ba Biển ở Tay Phải:
            // - Góc nhìn 1st-Person: Cầm chéo góc dưới–phải kiểu Đinh Ba Vanilla (nghiêng ZP +24°, ngả mũi XP -16°,
            //   xoay phối cảnh 3/4 YP 132°), gọn chắc tay & thoáng tầm nhìn trung tâm!
            // - Góc nhìn F5 3rd-Person: giữ Ry(180°) & scale(1.02) đồng bộ 1:1 với góc cầm ngang dựng đứng 3 mũi của SeaWeaponsAnimManager!
            if (isFirstPerson) {
                poseStack.translate(0.03F, -0.02F, -0.09F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(24.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(-16.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(132.0F));
                poseStack.scale(0.90F, 0.90F, 0.90F);
            } else {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                poseStack.scale(1.02F, 1.02F, 1.02F);
            }
        }
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderVariant(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay, variant, boost);
        poseStack.popPose();
    }

    private void renderVariant(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                               MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                               Variant variant, float boost) {
        Variant prevVar = this.activeVariant;
        float prevBoost = this.glowBoost;
        this.activeVariant = variant;
        this.glowBoost = boost;
        try {
            super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
        } finally {
            this.activeVariant = prevVar;
            this.glowBoost = prevBoost;
        }
    }

    private static void renderCrispGuiIcon(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource) {
        poseStack.pushPose();
        RenderType type = RenderType.entityTranslucent(GUI_ICON);
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(bufferSource, type, true, stack.hasFoil());
        PoseStack.Pose pose = poseStack.last();
        float z = 0.5F;
        vc.addVertex(pose, 0.0F, 0.0F, z).setColor(255, 255, 255, 255).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 1.0F, 0.0F, z).setColor(255, 255, 255, 255).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 1.0F, 1.0F, z).setColor(255, 255, 255, 255).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 0.0F, 1.0F, z).setColor(255, 255, 255, 255).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
        poseStack.popPose();
    }

    private static final class SeaEmissiveLayer extends GeoRenderLayer<SeaTridentItem> {
        SeaEmissiveLayer(SeaTridentGeoRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, SeaTridentItem animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource,
                           @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!(getRenderer() instanceof SeaTridentGeoRenderer sr)) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            float timeSec = (mc.level != null ? ((mc.level.getGameTime() % 240000L) + partialTick) : partialTick) / 20.0F;
            float basePulse = 0.82F + 0.18F * Mth.sin(timeSec * 3.0F);
            float intensity = Mth.clamp(basePulse + sr.getGlowBoost() * 0.45F, 0.0F, 1.0F);
            int c = Mth.clamp(Math.round(255.0F * intensity), 0, 255);
            int tint = FastColor.ARGB32.color(255, c, c, 255);

            ResourceLocation glowTex = SeaTridentGeoModel.glowFor(sr.getActiveVariant());
            RenderType glowType = RenderType.eyes(glowTex);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, tint);
        }
    }
}
