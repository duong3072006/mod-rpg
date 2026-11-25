package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.model.FenrirGeoModel;
import com.abyssalrpg.entity.mythic.FenrirBossEntity;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib 4.8.3 GeoEntityRenderer cho Boss Fenrir — Fel (Thần Sói Bão Tố):
 * - `RenderType.entityCutoutNoCull`: hiển thị 2 mặt sắc nét lọn bờm má & vòng lông lửa đỏ alpha-cutout.
 * - `DivineGaleEmissiveLayer`: phát sáng nhịp thở cho mắt ngọc lục bảo, dấu sao trán & hào quang gió thần Phase 2.
 * - Phóng đại đồng bộ theo `FenrirBossEntity.RENDER_SCALE` (không phụ thuộc Attributes.SCALE) + hiệu ứng trồi mặt đất.
 */
public final class FenrirGeoRenderer extends GeoEntityRenderer<FenrirBossEntity> {
    public FenrirGeoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new FenrirGeoModel());
        this.shadowRadius = 2.6f;
        this.addRenderLayer(new DivineGaleEmissiveLayer(this));
    }

    @Override
    public RenderType getRenderType(FenrirBossEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public boolean shouldRender(FenrirBossEntity entity, Frustum camera, double camX, double camY, double camZ) {
        if (super.shouldRender(entity, camera, camX, camY, camZ)) {
            return true;
        }
        return camera.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    protected float getDeathMaxRotation(FenrirBossEntity animatable) {
        return 0.0f;
    }

    @Override
    public void render(FenrirBossEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float scale = FenrirBossEntity.RENDER_SCALE;
        this.shadowRadius = 2.4f * scale * 0.60f;

        poseStack.pushPose();

        if (entity.isSpawningIn()) {
            float p = entity.getSpawnProgress(partialTick);
            float emergeT = Mth.clamp(p / 0.72f, 0.0f, 1.0f);
            float ease = emergeT * emergeT * (3.0f - 2.0f * emergeT);
            poseStack.translate(0.0, -(1.0f - ease) * 3.8f, 0.0);
            poseStack.mulPose(Axis.XP.rotationDegrees((1.0f - ease) * -14.0f));
        }

        poseStack.scale(scale, scale, scale);
        float safeYaw = Mth.wrapDegrees(entityYaw);
        super.render(entity, safeYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    public void renderFinal(PoseStack poseStack, FenrirBossEntity animatable, BakedGeoModel model,
                            MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                            int packedLight, int packedOverlay, int colour) {
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);

        model.getBone(FenrirBossEntity.HEAD_BONE).ifPresent(bone -> {
            Vector3d wp = bone.getWorldPosition();
            if (Double.isFinite(wp.x) && Double.isFinite(wp.y) && Double.isFinite(wp.z)) {
                animatable.updateClientHeadWorldPos(wp.x, wp.y, wp.z);
            }
        });

        // Hạt gió trắng/tuyết siêu nhẹ phía Client khi người chơi ở gần <= 40 block (0 CPU server)
        if (animatable.isAlive() && animatable.consumeClientAmbientTick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null && mc.player.distanceToSqr(animatable) <= 40.0 * 40.0) {
                Vec3 base = animatable.position();
                double ang = (animatable.tickCount * 0.25) % (Math.PI * 2.0);
                double r = 2.6;
                mc.level.addParticle(
                        animatable.isEnragedVisual() ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SNOWFLAKE,
                        base.x + Math.cos(ang) * r, base.y + 1.8 + (animatable.getRandom().nextDouble() * 2.4),
                        base.z + Math.sin(ang) * r,
                        -Math.sin(ang) * 0.08, 0.04, Math.cos(ang) * 0.08
                );
            }
        }
    }

    private static final class DivineGaleEmissiveLayer extends AutoGlowingGeoLayer<FenrirBossEntity> {
        DivineGaleEmissiveLayer(GeoRenderer<FenrirBossEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, FenrirBossEntity animatable, BakedGeoModel bakedModel,
                           @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            ResourceLocation baseTex = getTextureResource(animatable);
            if (baseTex == null) return;
            ResourceLocation glowTex = baseTex.withPath(p -> p.endsWith(".png")
                    ? p.substring(0, p.length() - 4) + "_glowmask.png" : p + "_glowmask.png");
            RenderType glowType = RenderType.entityTranslucentEmissive(glowTex);

            float timeSec = (animatable.tickCount + partialTick) / 20.0f;
            float wave = 0.5f - 0.5f * Mth.cos(timeSec * (Mth.TWO_PI / 3.4f));
            boolean enraged = animatable.isEnragedVisual() || animatable.hasEnteredPhase2() || animatable.isTransitioningPhase();
            float pulse = enraged ? Mth.lerp(wave, 0.88f, 1.00f) : Mth.lerp(wave, 0.78f, 1.00f);

            int alpha = Mth.clamp(Math.round(pulse * 255.0f), 170, 255);
            int packedColor = (alpha << 24) | 0x00FFFFFF;
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                    bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, packedColor);
        }
    }
}
