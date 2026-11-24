package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.model.LeviathanGeoModel;
import com.abyssalrpg.config.LeviathanConfig;
import com.abyssalrpg.entity.mythic.LeviathanBossEntity;
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
 * GeckoLib 4.8.3 GeoEntityRenderer cho Boss Leviathan v2 (4 tay vuốt dài, vây lá lửa alpha-cutout liền mạch):
 * - Override `getRenderType` -> `RenderType.entityCutoutNoCull` để toàn bộ màng vây lửa phẳng mỏng
 *   (đuôi, sống lưng, 4 cẳng tay, vây má, chùm vây cằm, chóp đuôi) hiển thị sắc nét cả 2 mặt.
 * - `LeviathanEmissiveLayer` fullbright (`LightTexture.FULL_BRIGHT`) tự khớp `leviathan_glowmask.png` (P1)
 *   và `leviathan_enraged_glowmask.png` (P2) với nhịp thở ánh sáng tự nhiên cho mắt, đốm lưng, khe mang & mép vây lửa.
 * - Phóng đại linh hoạt qua `entity.getTotalRenderScale()` (`BASE_RENDER_SCALE * SCALE * visualScaleMultiplier`).
 * - Particle idle tối giản hoàn toàn phía Client: tối đa 1–2 hạt bọt nước/tàn lửa mỗi 14 tick quanh đầu/đuôi.
 */
public final class LeviathanGeoRenderer extends GeoEntityRenderer<LeviathanBossEntity> {
    private long lastIdleParticleTick = -1L;

    public LeviathanGeoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new LeviathanGeoModel());
        this.shadowRadius = 2.6f * LeviathanBossEntity.SCALE;
        this.addRenderLayer(new LeviathanEmissiveLayer(this));
    }

    @Override
    public RenderType getRenderType(LeviathanBossEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public boolean shouldRender(LeviathanBossEntity entity, Frustum camera, double camX, double camY, double camZ) {
        if (super.shouldRender(entity, camera, camX, camY, camZ)) {
            return true;
        }
        return camera.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    protected float getDeathMaxRotation(LeviathanBossEntity animatable) {
        return 0.0f;
    }

    @Override
    public void render(LeviathanBossEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float totalScale = entity.getTotalRenderScale();
        this.shadowRadius = 2.6f * entity.getEffectiveScale();

        poseStack.pushPose();

        // Hiệu ứng trồi lên từ mặt đất (tỷ lệ theo getEffectiveScale())
        if (entity.isSpawningIn()) {
            float p = entity.getSpawnProgress(partialTick);
            float emergeT = Mth.clamp(p / 0.72f, 0f, 1f);
            float ease = emergeT * emergeT * (3f - 2f * emergeT);
            float roarPulse = (p > 0.64f && p < 0.96f) ? Mth.sin(((p - 0.64f) / 0.32f) * Mth.PI) : 0f;
            float yRise = (-(1f - ease) * 5.2f + roarPulse * 0.75f) * entity.getEffectiveScale();
            float tremor = (1f - emergeT) * 0.14f * entity.getEffectiveScale();
            float age = entity.tickCount + partialTick;
            poseStack.translate(Mth.sin(age * 3.8f) * tremor, yRise, Mth.cos(age * 3.2f) * tremor);
            poseStack.mulPose(Axis.XP.rotationDegrees((1f - ease) * -24.0f - roarPulse * 14.0f));
        }

        poseStack.scale(totalScale, totalScale, totalScale);

        // Truyền yaw đã chuẩn hóa để tránh client nội suy lệch vòng
        float safeYaw = Mth.wrapDegrees(entityYaw);
        super.render(entity, safeYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    public void renderFinal(PoseStack poseStack, LeviathanBossEntity animatable, BakedGeoModel model,
                            MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                            int packedLight, int packedOverlay, int colour) {
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);

        model.getBone("head").ifPresent(bone -> {
            Vector3d wp = bone.getWorldPosition();
            if (Double.isFinite(wp.x) && Double.isFinite(wp.y) && Double.isFinite(wp.z)) {
                animatable.updateClientHeadWorldPosition(wp.x, wp.y, wp.z);
            }
        });

        long gt = animatable.level().getGameTime();
        var lp = Minecraft.getInstance().player;
        if (lp != null && lp.distanceToSqr(animatable) <= 44.0 * 44.0
                && animatable.isAlive()
                && LeviathanConfig.get().getEffectiveParticleMultiplier() > 0.01f
                && gt != this.lastIdleParticleTick
                && (gt + animatable.getId()) % 14 == 0) {
            this.lastIdleParticleTick = gt;
            var headPos = animatable.getMouthWorldPosition();
            animatable.level().addParticle(
                    animatable.isEnragedVisual() ? ParticleTypes.FLAME : ParticleTypes.BUBBLE_POP,
                    headPos.x, headPos.y + 0.35, headPos.z, 0.0, 0.04, 0.0);
            model.getBone("tail_8").ifPresent(tailBone -> {
                Vector3d tp = tailBone.getWorldPosition();
                if (Double.isFinite(tp.x) && Double.isFinite(tp.y) && Double.isFinite(tp.z)) {
                    animatable.level().addParticle(
                            animatable.isEnragedVisual() ? ParticleTypes.FLAME : ParticleTypes.SPLASH,
                            tp.x, tp.y + 0.2, tp.z, 0.0, 0.03, 0.0);
                }
            });
        }
    }

    private static final class LeviathanEmissiveLayer extends AutoGlowingGeoLayer<LeviathanBossEntity> {
        LeviathanEmissiveLayer(GeoRenderer<LeviathanBossEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, LeviathanBossEntity animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            RenderType glowType = getRenderType(animatable, bufferSource);
            if (glowType == null) return;

            float timeSec = (animatable.tickCount + partialTick) / 20.0f;
            float breath = 0.5f - 0.5f * Mth.cos(timeSec * (Mth.TWO_PI / 3.0f));
            boolean hot = animatable.isEnragedVisual() || animatable.isTransitioningPhase()
                    || animatable.getSkillState() != LeviathanBossEntity.SkillState.IDLE;
            float pulse = hot ? Mth.lerp(breath, 0.90f, 1.00f) : Mth.lerp(breath, 0.78f, 1.00f);

            int ch = Mth.clamp(Math.round(pulse * 255.0f), 150, 255);
            int packedColor = (ch << 24) | (ch << 16) | (ch << 8) | ch;
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, packedColor);
        }
    }
}
