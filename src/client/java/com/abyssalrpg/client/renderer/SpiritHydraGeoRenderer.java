package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.model.SpiritHydraGeoModel;
import com.abyssalrpg.config.SpiritHydraConfig;
import com.abyssalrpg.entity.mythic.HydraBossEntity;
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
 * GeckoLib 4.8.3 GeoEntityRenderer cho Boss Spirit Hydra 4 đầu:
 * - Dùng `RenderType.entityCutoutNoCull` để màng mang quạt & tua cổ alpha-cutout hiển thị sắc nét cả 2 mặt.
 * - `HydraEmissiveLayer` (fullbright `LightTexture.FULL_BRIGHT`) tự nạp `spirit_hydra_glowmask.png` (Phase 1)
 *   và `spirit_hydra_enraged_glowmask.png` (Phase 2) với nhịp sáng thở mượt.
 * - Phóng đại linh hoạt qua `entity.getTotalRenderScale()` (`BASE_RENDER_SCALE * scale * visualScaleMultiplier`).
 * - Particle idle siêu gọn chống lag: tối đa 1–2 hạt hồn bay lên mỗi 12 tick quanh đầu sống / mỏm cổ cụt (phía Client).
 */
public final class SpiritHydraGeoRenderer extends GeoEntityRenderer<HydraBossEntity> {
    private long lastIdleParticleTick = -1L;

    public SpiritHydraGeoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SpiritHydraGeoModel());
        this.shadowRadius = 2.8f * HydraBossEntity.SCALE;
        this.addRenderLayer(new HydraEmissiveLayer(this));
    }

    @Override
    public RenderType getRenderType(HydraBossEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public boolean shouldRender(HydraBossEntity entity, Frustum camera, double camX, double camY, double camZ) {
        if (super.shouldRender(entity, camera, camX, camY, camZ)) {
            return true;
        }
        return camera.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    protected float getDeathMaxRotation(HydraBossEntity animatable) {
        return 0.0f;
    }

    @Override
    public void render(HydraBossEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float totalScale = entity.getTotalRenderScale();
        this.shadowRadius = 2.8f * entity.getEffectiveScale();

        poseStack.pushPose();
        if (entity.isSpawningIn()) {
            float p = entity.getSpawnProgress(partialTick);
            float emergeT = Mth.clamp(p / 0.72f, 0f, 1f);
            float ease = emergeT * emergeT * (3f - 2f * emergeT);
            float roarPulse = (p > 0.64f && p < 0.96f) ? Mth.sin(((p - 0.64f) / 0.32f) * Mth.PI) : 0f;
            float yRise = (-(1f - ease) * 4.8f + roarPulse * 0.60f) * entity.getEffectiveScale();
            float tremor = (1f - emergeT) * 0.12f * entity.getEffectiveScale();
            float age = entity.tickCount + partialTick;
            poseStack.translate(Mth.sin(age * 3.8f) * tremor, yRise, Mth.cos(age * 3.2f) * tremor);
            poseStack.mulPose(Axis.XP.rotationDegrees((1f - ease) * -20.0f - roarPulse * 10.0f));
        }

        poseStack.scale(totalScale, totalScale, totalScale);
        float safeYaw = Mth.wrapDegrees(entityYaw);
        super.render(entity, safeYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    public void renderFinal(PoseStack poseStack, HydraBossEntity animatable, BakedGeoModel model,
                            MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                            int packedLight, int packedOverlay, int colour) {
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);

        long gt = animatable.level().getGameTime();
        var lp = Minecraft.getInstance().player;
        boolean emitIdle = lp != null && lp.distanceToSqr(animatable) <= 42.0 * 42.0
                && animatable.isAlive()
                && SpiritHydraConfig.get().getEffectiveParticleMultiplier() > 0.01f
                && gt != this.lastIdleParticleTick
                && (gt + animatable.getId()) % 12 == 0;
        if (emitIdle) {
            this.lastIdleParticleTick = gt;
        }

        int emitted = 0;
        for (int i = 0; i < HydraBossEntity.HEAD_COUNT; i++) {
            final int headIndex = i;
            if (animatable.isHeadAlive(i)) {
                model.getBone(HydraBossEntity.HEAD_BONES[i]).ifPresent(bone -> {
                    Vector3d wp = bone.getWorldPosition();
                    if (Double.isFinite(wp.x) && Double.isFinite(wp.y) && Double.isFinite(wp.z)) {
                        animatable.updateClientHeadWorldPosition(headIndex, wp.x, wp.y, wp.z);
                    }
                });
                // Tối đa 1-2 hạt hồn bay lên nhẹ nhàng mỗi 12 tick quanh đầu sống
                if (emitIdle && emitted < 2 && ((int) (gt / 12) + i) % HydraBossEntity.HEAD_COUNT == 0) {
                    var hp = animatable.getHeadWorldPosition(i);
                    animatable.level().addParticle(
                            animatable.isEnragedVisual() ? ParticleTypes.SCULK_SOUL : ParticleTypes.SOUL_FIRE_FLAME,
                            hp.x, hp.y + 0.3, hp.z, 0.0, 0.04, 0.0);
                    emitted++;
                }
            } else if (emitIdle && emitted < 2) {
                // Mỏm cổ bị chặt rỉ khói hồn nhẹ mỗi 12 tick
                int stumpIdx = i;
                model.getBone(HydraBossEntity.STUMP_BONES[stumpIdx]).ifPresent(bone -> {
                    Vector3d wp = bone.getWorldPosition();
                    if (Double.isFinite(wp.x) && Double.isFinite(wp.y) && Double.isFinite(wp.z)) {
                        animatable.level().addParticle(ParticleTypes.SOUL, wp.x, wp.y + 0.25, wp.z, 0.0, 0.05, 0.0);
                    }
                });
                emitted++;
            }
        }
    }

    private static final class HydraEmissiveLayer extends AutoGlowingGeoLayer<HydraBossEntity> {
        HydraEmissiveLayer(GeoRenderer<HydraBossEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, HydraBossEntity animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            RenderType glowType = getRenderType(animatable, bufferSource);
            if (glowType == null) return;

            float timeSec = (animatable.tickCount + partialTick) / 20.0f;
            float breath = 0.5f - 0.5f * Mth.cos(timeSec * (Mth.TWO_PI / 3.2f));
            boolean hot = animatable.isEnragedVisual() || animatable.isTransitioningPhase()
                    || animatable.getSkillState() != HydraBossEntity.SkillState.IDLE;
            float pulse = hot ? Mth.lerp(breath, 0.90f, 1.00f) : Mth.lerp(breath, 0.78f, 1.00f);

            int ch = Mth.clamp(Math.round(pulse * 255.0f), 140, 255);
            int packedColor = (ch << 24) | (ch << 16) | (ch << 8) | ch;
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, packedColor);
        }
    }
}
