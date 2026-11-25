package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.model.CyclopsWardenGeoModel;
import com.abyssalrpg.entity.mythic.CyclopsWardenBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * GeckoLib 4.8.3 GeoEntityRenderer cho Boss Cyclops Warden (Khổng Lồ Một Mắt Giam Tù):
 * - `RenderType.entityCutoutNoCull`: hiển thị 2 mặt sắc nét cho khố tua rua alpha-cutout & song lồng sắt.
 * - `CyclopsEyeEmissiveLayer`: phát sáng nhịp thở cho con mắt kim cương đỏ rực & mép thép chùy chiến.
 * - Kích thước cỡ trung bình chuẩn `CyclopsWardenBossEntity.RENDER_SCALE` (~3.8 block).
 */
public final class CyclopsWardenGeoRenderer extends GeoEntityRenderer<CyclopsWardenBossEntity> {
    public CyclopsWardenGeoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CyclopsWardenGeoModel());
        this.shadowRadius = 1.15f;
        this.addRenderLayer(new CyclopsEyeEmissiveLayer(this));
    }

    @Override
    public RenderType getRenderType(CyclopsWardenBossEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public boolean shouldRender(CyclopsWardenBossEntity entity, Frustum camera, double camX, double camY, double camZ) {
        if (super.shouldRender(entity, camera, camX, camY, camZ)) {
            return true;
        }
        return camera.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    protected float getDeathMaxRotation(CyclopsWardenBossEntity animatable) {
        return 0.0f; // Animation death tự ngã sấp xuống đất để bật mở lồng sắt giải thoát người tù!
    }

    @Override
    public void render(CyclopsWardenBossEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float scale = CyclopsWardenBossEntity.RENDER_SCALE;
        this.shadowRadius = 1.15f * scale;

        poseStack.pushPose();
        if (entity.isSpawningIn()) {
            float p = entity.getSpawnProgress(partialTick);
            float emergeT = Mth.clamp(p / 0.72f, 0.0f, 1.0f);
            float ease = emergeT * emergeT * (3.0f - 2.0f * emergeT);
            poseStack.translate(0.0, -(1.0f - ease) * 2.6f, 0.0);
            poseStack.mulPose(Axis.XP.rotationDegrees((1.0f - ease) * -10.0f));
        }

        poseStack.scale(scale, scale, scale);
        float safeYaw = Mth.wrapDegrees(entityYaw);
        super.render(entity, safeYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    private static final class CyclopsEyeEmissiveLayer extends AutoGlowingGeoLayer<CyclopsWardenBossEntity> {
        CyclopsEyeEmissiveLayer(GeoRenderer<CyclopsWardenBossEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, CyclopsWardenBossEntity animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            ResourceLocation baseTex = getTextureResource(animatable);
            if (baseTex == null) return;
            ResourceLocation glowTex = baseTex.withPath(p -> p.endsWith(".png")
                    ? p.substring(0, p.length() - 4) + "_glowmask.png" : p + "_glowmask.png");
            RenderType glowType = RenderType.entityTranslucentEmissive(glowTex);

            float timeSec = (animatable.tickCount + partialTick) / 20.0f;
            float wave = 0.5f - 0.5f * Mth.cos(timeSec * (Mth.TWO_PI / 2.8f));
            boolean p2 = animatable.isEnragedVisual() || animatable.hasEnteredPhase2();
            float pulse = p2 ? Mth.lerp(wave, 0.88f, 1.00f) : Mth.lerp(wave, 0.76f, 0.96f);
            int alpha = Mth.clamp(Math.round(pulse * 255.0f), 170, 255);
            int packedColor = (alpha << 24) | 0x00FFFFFF;

            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, packedColor);
        }
    }
}
