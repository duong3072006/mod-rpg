package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.model.CerberusGeoModel;
import com.abyssalrpg.config.CerberusConfig;
import com.abyssalrpg.entity.mythic.CerberusBossEntity;
import com.abyssalrpg.entity.mythic.cerberus.CerberusForm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib 4.8.3 GeoEntityRenderer cho Boss Cerberus v4:
 * - Dùng `RenderType.entityCutoutNoCull` cho cả model nền lẫn `entityTranslucentEmissive` cho lớp phát sáng
 *   nhằm hiển thị 2 mặt sắc nét chóp lửa đuôi alpha-cutout 3 cánh (`tail_flame`) fullbright 0 particle!
 * - Phóng đại động theo `CerberusBossEntity.getEffectiveScale()` (`scale * visualScaleMultiplier * splitScaleFactor`).
 * - Lớp phát sáng `BreathingEmissiveLayer` đồng bộ nhịp thở 3.2s cho mắt, khoang miệng, sẹo dung nham & ngọn lửa đuôi.
 * - Phát 1-2 tàn lửa client cực nhẹ mỗi ~14 ticks quanh đầu đuôi / miệng khi người chơi ở trong bán kính 40 block (0 CPU Server).
 */
public final class CerberusGeoRenderer extends GeoEntityRenderer<CerberusBossEntity> {
    public CerberusGeoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CerberusGeoModel());
        this.shadowRadius = 2.15f * CerberusBossEntity.SCALE * CerberusConfig.get().visualScaleMultiplier;
        this.addRenderLayer(new BreathingEmissiveLayer(this));
    }

    @Override
    public RenderType getRenderType(CerberusBossEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        // Cutout 2 mặt (NO_CULL) để 3 cánh ngọn lửa alpha-cutout ở đầu đuôi sắc nét từ mọi góc nhìn!
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public boolean shouldRender(CerberusBossEntity entity, Frustum camera, double camX, double camY, double camZ) {
        if (entity.isShadowCloaked() || entity.getSplitStage() == 2) {
            return false; // Ẩn hoàn toàn khi hóa cầu lửa bay hoặc tàng hình Shadow Step
        }
        if (super.shouldRender(entity, camera, camX, camY, camZ)) {
            return true;
        }
        return camera.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    protected float getDeathMaxRotation(CerberusBossEntity animatable) {
        return 0.0f;
    }

    @Override
    public void render(CerberusBossEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity.isShadowCloaked() || entity.getSplitStage() == 2) {
            return;
        }

        float effScale = entity.getEffectiveScale();
        float totalRenderScale = CerberusBossEntity.BASE_RENDER_SCALE * effScale;
        this.shadowRadius = 2.15f * entity.getGameplayScale();

        poseStack.pushPose();

        // Hiệu ứng trồi lên khi summon ban đầu (Phase 1)
        if (entity.isSpawningIn()) {
            float p = entity.getSpawnProgress(partialTick);
            float emergeT = Mth.clamp(p / 0.72f, 0f, 1f);
            float ease = emergeT * emergeT * (3f - 2f * emergeT);
            float yRise = -(1f - ease) * 4.5f * entity.getGameplayScale();
            poseStack.translate(0.0, yRise, 0.0);
            poseStack.mulPose(Axis.XP.rotationDegrees((1f - ease) * -18.0f));
        }

        poseStack.scale(totalRenderScale, totalRenderScale, totalRenderScale);

        // Truyền yaw đã chuẩn hóa [-180, 180] chống nội suy quay nhiều vòng
        float safeYaw = Mth.wrapDegrees(entityYaw);
        super.render(entity, safeYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    public void renderFinal(PoseStack poseStack, CerberusBossEntity animatable, BakedGeoModel model,
                            MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                            int packedLight, int packedOverlay, int colour) {
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);

        boolean single = animatable.getCerberusForm().isSplitSingle();
        for (int i = 0; i < CerberusBossEntity.HEAD_COUNT; i++) {
            if (single && i != CerberusBossEntity.SINGLE_ACTIVE_HEAD_INDEX) continue;
            final int headIdx = i;
            model.getBone(CerberusBossEntity.HEAD_BONES[i]).ifPresent(bone -> {
                Vector3d wp = bone.getWorldPosition();
                if (Double.isFinite(wp.x) && Double.isFinite(wp.y) && Double.isFinite(wp.z)) {
                    animatable.updateClientHeadWorldPosition(headIdx, wp.x, wp.y, wp.z);
                }
            });
        }

        // Tàn lửa idle siêu nhẹ chỉ chạy phía Client (1-2 hạt mỗi 14 ticks khi người chơi ở gần <= 40 block, 0 băng thông Server!)
        if (animatable.isAlive() && !animatable.isShadowCloaked() && animatable.getSplitStage() != 2
                && CerberusConfig.get().getEffectiveParticleMultiplier() > 0.01f
                && animatable.consumeClientAmbientEmberTick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null && mc.player.distanceToSqr(animatable) <= 40.0 * 40.0) {
                CerberusForm form = animatable.getCerberusForm();
                ParticleOptions ember = (form == CerberusForm.SHADOW_HEAD)
                        ? ParticleTypes.WITCH : ParticleTypes.SMALL_FLAME;
                Vec3 tailPos = animatable.getTailFireWorldPosition();
                mc.level.addParticle(ember, tailPos.x, tailPos.y + 0.15, tailPos.z, 0.0, 0.04, 0.0);
                if (animatable.getSkillState() == CerberusBossEntity.SkillState.IDLE) {
                    int hIdx = form.isSplitSingle()
                            ? CerberusBossEntity.SINGLE_ACTIVE_HEAD_INDEX
                            : Math.floorMod(animatable.tickCount / 14, CerberusBossEntity.HEAD_COUNT);
                    Vec3 mouthPos = animatable.getHeadWorldPosition(hIdx);
                    mc.level.addParticle(ParticleTypes.SMOKE, mouthPos.x, mouthPos.y, mouthPos.z, 0.0, 0.025, 0.0);
                }
            }
        }
    }

    /**
     * Lớp phát sáng nhịp thở cho `*_glowmask.png` (dùng `entityTranslucentEmissive` -> NO_CULL 2 mặt + hỗ trợ alpha pulse):
     * - Idle Phase 1: dao động mượt 0.72..1.00 khớp chu kỳ thở 3.2s (64 ticks).
     * - Phase 2 / Enraged / Breath / Howl / Nova: sáng mạnh 0.92..1.00.
     */
    private static final class BreathingEmissiveLayer extends AutoGlowingGeoLayer<CerberusBossEntity> {
        BreathingEmissiveLayer(GeoRenderer<CerberusBossEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, CerberusBossEntity animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            ResourceLocation baseTex = getTextureResource(animatable);
            if (baseTex == null) return;
            ResourceLocation glowTex = baseTex.withPath(p -> p.endsWith(".png")
                    ? p.substring(0, p.length() - 4) + "_glowmask.png" : p + "_glowmask.png");
            RenderType glowType = RenderType.entityTranslucentEmissive(glowTex);

            float pulse = 1.0f;
            if (CerberusConfig.get().cerberusFancyEffects) {
                float timeSec = (animatable.tickCount + partialTick) / 20.0f;
                float breath = 0.5f - 0.5f * Mth.cos(timeSec * (Mth.TWO_PI / 3.2f));
                boolean hotState = animatable.isEnragedVisual()
                        || animatable.hasEnteredPhase2()
                        || animatable.isTransitioningPhase()
                        || animatable.getSkillState() == CerberusBossEntity.SkillState.HELLFIRE_BREATH
                        || animatable.getSkillState() == CerberusBossEntity.SkillState.HOWL
                        || animatable.getSkillState() == CerberusBossEntity.SkillState.HELLFIRE_NOVA;
                pulse = hotState ? Mth.lerp(breath, 0.92f, 1.00f) : Mth.lerp(breath, 0.72f, 1.00f);
            }

            int alpha = Mth.clamp(Math.round(pulse * 255.0f), 140, 255);
            int packedColor = (alpha << 24) | 0x00FFFFFF;
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, packedColor);
        }
    }
}
