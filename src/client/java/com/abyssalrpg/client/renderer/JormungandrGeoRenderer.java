package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.model.JormungandrGeoModel;
import com.abyssalrpg.config.JormungandrConfig;
import com.abyssalrpg.entity.mythic.JormungandrBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib 4.8.3 GeoEntityRenderer cho Boss Jörmungandr:
 * - Override `shouldRender` kiểm tra theo hộp culling tổng của toàn bộ
 * `SerpentPath`
 * để thân rắn dài 32-45 block không biến mất khi chỉ nhìn 1 đoạn thân/đuôi.
 * - Ẩn hoàn toàn khi đang lặn sâu dưới đất (`burrowDepth >= 0.92`).
 * - `AutoGlowingGeoLayer` phát sáng mắt đỏ / trong miệng / mép mang cổ (và vết
 * nứt tím-trắng ở Phase 2).
 */
public final class JormungandrGeoRenderer extends GeoEntityRenderer<JormungandrBossEntity> {
    public JormungandrGeoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new JormungandrGeoModel());
        this.shadowRadius = 1.8f;
        this.addRenderLayer(new SerpentEmissiveLayer(this));
    }

    @Override
    public boolean shouldRender(JormungandrBossEntity entity, Frustum camera, double camX, double camY, double camZ) {
        if (entity.isBurrowed()
                && entity.getSerpentPath().getClientInterpolatedBurrow(entity.level().getGameTime(), 0.0f) > 0.94f) {
            return false;
        }
        if (super.shouldRender(entity, camera, camX, camY, camZ)) {
            return true;
        }
        return camera.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    protected float getDeathMaxRotation(JormungandrBossEntity animatable) {
        return 0.0f;
    }

    @Override
    public void render(JormungandrBossEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity.isBurrowed() && entity.getSerpentPath().getClientInterpolatedBurrow(entity.level().getGameTime(),
                partialTick) > 0.94f) {
            return;
        }

        float sc = JormungandrConfig.get().getEffectiveVisualScale();
        this.shadowRadius = 1.6f * sc;

        poseStack.pushPose();
        poseStack.scale(sc, sc, sc);
        float safeYaw = Mth.wrapDegrees(entityYaw);
        super.render(entity, safeYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    private static final class SerpentEmissiveLayer extends AutoGlowingGeoLayer<JormungandrBossEntity> {
        SerpentEmissiveLayer(GeoRenderer<JormungandrBossEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, JormungandrBossEntity animatable, BakedGeoModel bakedModel,
                @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                float partialTick, int packedLight, int packedOverlay) {
            RenderType glowType = getRenderType(animatable, bufferSource);
            if (glowType == null)
                return;

            float sec = (animatable.tickCount + partialTick) / 20.0f;
            float breath = 0.5f - 0.5f * Mth.cos(sec * (Mth.TWO_PI / 2.6f));
            boolean hot = animatable.isEnragedVisual() || animatable.isTransitioningPhase();
            // Giữ độ sáng fullbright tối đa để đôi mắt khe dọc & gân mang cổ rực nổi bật ngay cả giữa trời nắng ban ngày
            float pulse = hot ? Mth.lerp(breath, 0.96f, 1.00f) : Mth.lerp(breath, 0.90f, 1.00f);

            int ch = Mth.clamp(Math.round(pulse * 255.0f), 220, 255);
            int packedColor = (ch << 24) | (ch << 16) | (ch << 8) | ch;
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay,
                    packedColor);
        }
    }
}
