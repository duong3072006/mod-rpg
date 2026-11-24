package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.model.CinderDragonGeoModel;
import com.abyssalrpg.entity.mythic.CinderDragonBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib 4.8.3 GeoEntityRenderer cho Boss Cinder Dragon — Hỏa Long Giáp Đá:
 * - Phóng đại theo `CinderDragonConfig.get().getEffectiveScale()` (mặc định `4.5f` = 60% Nightmare Knight).
 * - Dùng `entityCutoutNoCull` để màng cánh đỏ gỉ cháy alpha-cutout hiển thị sắc nét cả 2 mặt.
 * - `AutoGlowingGeoLayer` fullbright tự chuyển từ `cinder_dragon_glowmask.png` (Phase 1: mắt, họng, rãnh cổ, lõi đuôi)
 *   sang `cinder_dragon_enraged_glowmask.png` (Phase 2: rực toàn bộ vết nứt dung nham & viền lửa cánh),
 *   đồng thời tăng độ sáng rực mỗi khi nạp hơi khè lửa (`getThroatGlow()`).
 * - Tắt hoàn toàn aura particle thừa lúc `idle` -> 0 lag trên máy tầm trung.
 */
public final class CinderDragonGeoRenderer extends GeoEntityRenderer<CinderDragonBossEntity> {
    public CinderDragonGeoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CinderDragonGeoModel());
        this.shadowRadius = 4.6f;
        this.addRenderLayer(new CinderDragonEmissiveLayer(this));
    }

    @Override
    public RenderType getRenderType(CinderDragonBossEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public boolean shouldRender(CinderDragonBossEntity entity, Frustum camera, double camX, double camY, double camZ) {
        if (entity.noCulling) {
            return true;
        }
        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        if (dx * dx + dy * dy + dz * dz < 64.0 * 64.0) {
            return true; // Đứng ngay dưới bụng / cạnh thân rồng nhìn lên tuyệt đối không bao giờ bị cull!
        }
        if (super.shouldRender(entity, camera, camX, camY, camZ)) {
            return true;
        }
        return camera.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    protected float getDeathMaxRotation(CinderDragonBossEntity animatable) {
        return 0.0f;
    }

    @Override
    public void preRender(PoseStack poseStack, CinderDragonBossEntity animatable, BakedGeoModel model,
                          @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        if (!isReRender) {
            // Scale nằm trọn bên trong pushPose của defaultRender -> model & glowmask khớp 100%,
            // còn name tag bên renderFinal giữ nguyên cỡ chữ 1x sắc nét không bị phóng phồng!
            float scale = animatable.getConfiguredScale();
            poseStack.scale(scale, scale, scale);
        }
    }

    @Override
    public void render(CinderDragonBossEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        this.shadowRadius = 4.6f * entity.getScaleRatio();
        float safeYaw = Mth.wrapDegrees(entityYaw);
        super.render(entity, safeYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    protected void renderNameTag(CinderDragonBossEntity entity, Component displayName, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight, float partialTick) {
        poseStack.pushPose();
        // Đẩy name tag 1x lên cao hơn đỉnh đầu/sừng (~10.5 block thay vì chìm giữa thân collision box 4.6 block)
        poseStack.translate(0.0, 5.8 * entity.getScaleRatio(), 0.0);
        super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight, partialTick);
        poseStack.popPose();
    }

    @Override
    public void renderFinal(PoseStack poseStack, CinderDragonBossEntity animatable, BakedGeoModel model,
                            MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                            int packedLight, int packedOverlay, int colour) {
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);

        model.getBone(CinderDragonBossEntity.MOUTH_FLAME_BONE).ifPresent(bone -> {
            Vector3d wp = bone.getWorldPosition();
            if (Double.isFinite(wp.x) && Double.isFinite(wp.y) && Double.isFinite(wp.z)) {
                animatable.updateClientMouthFlamePos(wp.x, wp.y, wp.z);
            }
        });
    }

    private static final class CinderDragonEmissiveLayer extends AutoGlowingGeoLayer<CinderDragonBossEntity> {
        CinderDragonEmissiveLayer(GeoRenderer<CinderDragonBossEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, CinderDragonBossEntity animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            RenderType glowType = getRenderType(animatable, bufferSource);
            if (glowType == null) return;

            float timeSec = (animatable.tickCount + partialTick) / 20.0f;
            float breath = 0.5f - 0.5f * Mth.cos(timeSec * (Mth.TWO_PI / 2.4f));
            float charge = animatable.getThroatGlow();
            boolean hot = animatable.isEnragedVisual() || animatable.isTransitioningPhase() || charge > 0.45f;
            float pulse = hot ? Mth.lerp(breath, 0.90f, 1.00f) : Mth.lerp(breath, 0.72f, 0.96f);

            int ch = Mth.clamp(Math.round(pulse * 255.0f), 140, 255);
            int packedColor = (ch << 24) | (ch << 16) | (ch << 8) | ch;
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, packedColor);
        }
    }
}

