package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.model.NightmareKnightGeoModel;
import com.abyssalrpg.config.NightmareKnightConfig;
import com.abyssalrpg.entity.mythic.NightmareKnightBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib 4.8.3 GeoEntityRenderer cho Boss Nightmare Knight:
 * - Phóng đại tuyến tính theo `NightmareKnightConfig.get().scale` (mặc định `7.5f` = 3x mốc boss cũ `2.5f`).
 * - `AutoGlowingGeoLayer` tự chọn `nightmare_knight_glowmask.png` (Phase 1: mắt + khe giáp)
 *   và `nightmare_knight_enraged_glowmask.png` (Phase 2: mắt + khe giáp nứt + lưỡi kiếm bốc lửa đỏ).
 * - `shouldRender` kiểm tra theo `getBoundingBoxForCulling()` mở rộng để không biến mất khi chỉ nhìn 1 phần thân ~19.5 block.
 * - Theo dõi tọa độ thế giới thật của bone `sword_tip` phục vụ VFX đầu kiếm trên client.
 */
public final class NightmareKnightGeoRenderer extends GeoEntityRenderer<NightmareKnightBossEntity> {
    private long lastTipEmberTick = -1L;

    public NightmareKnightGeoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new NightmareKnightGeoModel());
        this.shadowRadius = 4.2f;
        this.addRenderLayer(new NightmareEmissiveLayer(this));
    }

    /** Dùng cutout không loại mặt sau để màng cánh rách, áo choàng & khố vải có lỗ trong suốt hiển thị sắc nét 2 mặt. */
    @Override
    public RenderType getRenderType(NightmareKnightBossEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public boolean shouldRender(NightmareKnightBossEntity entity, Frustum camera, double camX, double camY, double camZ) {
        if (super.shouldRender(entity, camera, camX, camY, camZ)) {
            return true;
        }
        return camera.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    protected float getDeathMaxRotation(NightmareKnightBossEntity animatable) {
        return 0.0f;
    }

    @Override
    public void render(NightmareKnightBossEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float scale = entity.getConfiguredScale();
        this.shadowRadius = 4.2f * entity.getScaleRatio();

        poseStack.pushPose();
        if (entity.isSpawningIn()) {
            float p = entity.getSpawnProgress(partialTick);
            float emergeT = Mth.clamp(p / 0.75f, 0.0f, 1.0f);
            float ease = emergeT * emergeT * (3.0f - 2.0f * emergeT);
            poseStack.translate(0.0, -(1.0f - ease) * 14.0f * entity.getScaleRatio(), 0.0);
            poseStack.mulPose(Axis.XP.rotationDegrees((1.0f - ease) * -14.0f));
        }

        poseStack.scale(scale, scale, scale);
        float safeYaw = Mth.wrapDegrees(entityYaw);
        super.render(entity, safeYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    public void renderFinal(PoseStack poseStack, NightmareKnightBossEntity animatable, BakedGeoModel model,
                            MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                            int packedLight, int packedOverlay, int colour) {
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);

        model.getBone(NightmareKnightBossEntity.SWORD_TIP_BONE).ifPresent(bone -> {
            Vector3d wp = bone.getWorldPosition();
            if (Double.isFinite(wp.x) && Double.isFinite(wp.y) && Double.isFinite(wp.z)) {
                animatable.updateClientSwordTipPos(wp.x, wp.y, wp.z);
                // Tối giản aura nền: 0 hạt khi đang đi bộ; tối đa 1 tia lửa mỗi 20 tick khi đứng yên Phase 2 & người chơi <= 24 block!
                long gt = animatable.level().getGameTime();
                boolean moving = animatable.getDeltaMovement().horizontalDistanceSqr() > 0.002;
                var lp = Minecraft.getInstance().player;
                boolean nearPlayer = lp != null && lp.distanceToSqr(animatable) <= 24.0 * 24.0;
                if (!moving && nearPlayer && animatable.isEnragedVisual() && animatable.isAlive()
                        && NightmareKnightConfig.get().getEffectiveParticleMultiplier() > 0.01f
                        && gt != this.lastTipEmberTick && (gt + animatable.getId()) % 20 == 0) {
                    this.lastTipEmberTick = gt;
                    animatable.level().addParticle(ParticleTypes.FLAME, wp.x, wp.y, wp.z, 0.0, 0.015, 0.0);
                }
            }
        });
    }

    private static final class NightmareEmissiveLayer extends AutoGlowingGeoLayer<NightmareKnightBossEntity> {
        NightmareEmissiveLayer(GeoRenderer<NightmareKnightBossEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, NightmareKnightBossEntity animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            RenderType glowType = getRenderType(animatable, bufferSource);
            if (glowType == null) return;

            float timeSec = (animatable.tickCount + partialTick) / 20.0f;
            float breath = 0.5f - 0.5f * Mth.cos(timeSec * (Mth.TWO_PI / 2.8f));
            boolean hot = animatable.isEnragedVisual() || animatable.isTransitioningPhase() || animatable.isRealmActive();
            float pulse = hot ? Mth.lerp(breath, 0.88f, 1.00f) : Mth.lerp(breath, 0.68f, 0.96f);

            int ch = Mth.clamp(Math.round(pulse * 255.0f), 110, 255);
            int packedColor = (ch << 24) | (ch << 16) | (ch << 8) | ch;
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, packedColor);
        }
    }
}

