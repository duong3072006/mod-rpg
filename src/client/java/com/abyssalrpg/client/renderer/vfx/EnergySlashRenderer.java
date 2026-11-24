package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.entity.vfx.EnergySlashEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Renders a Colossal 3D Curved Glowing Crescent Energy Wave Blade tearing through the air!
 * Double-layered translucent aura + blazing additive white-hot crescent edge!
 */
public class EnergySlashRenderer extends EntityRenderer<EnergySlashEntity> {
    public EnergySlashRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(EnergySlashEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(EnergySlashEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        float lifeFrac = Mth.clamp(age / 26.0f, 0f, 1f);
        float alpha = lifeFrac < 0.12f ? (lifeFrac / 0.12f) : (1.0f - Math.max(0f, (lifeFrac - 0.65f) / 0.35f));
        if (alpha <= 0.01f) return;

        float width = entity.getSlashWidth() * (0.85f + lifeFrac * 0.40f);
        float depth = width * 0.55f;
        int rgb = entity.getColor();
        int cr = VfxRenderHelper.r(rgb), cg = VfxRenderHelper.g(rgb), cb = VfxRenderHelper.b(rgb);

        poseStack.pushPose();
        // Align local +Z along entity travel yaw + vertical pitch + custom diagonal roll!
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        Vec3 vel = entity.getDeltaMovement();
        double horiz = Math.hypot(vel.x, vel.z);
        if (horiz > 0.01) {
            float pitchDeg = (float) Math.toDegrees(Math.atan2(-vel.y, horiz));
            poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getSlashRoll()));

        VertexConsumer transVc = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.ENERGY_SLASH_TEX));
        VertexConsumer glowVc = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.ENERGY_SLASH_TEX));

        // Build curved 3D swept crescent arc mesh (10 arched segments!)
        drawCrescentArc(transVc, poseStack.last(), width, depth, 0.0f, cr, cg, cb, (int) (alpha * 225));
        // Thick volumetric cross-blade bevel (tilted +/- 14 deg so it reads boldly from ANY camera angle!)
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(15f));
        drawCrescentArc(glowVc, poseStack.last(), width * 0.96f, depth * 0.90f, 0.03f,
                VfxRenderHelper.mixWhite(cr, 0.62f), VfxRenderHelper.mixWhite(cg, 0.62f), VfxRenderHelper.mixWhite(cb, 0.62f),
                (int) (alpha * 245));
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(-15f));
        drawCrescentArc(glowVc, poseStack.last(), width * 0.96f, depth * 0.90f, -0.03f,
                VfxRenderHelper.mixWhite(cr, 0.62f), VfxRenderHelper.mixWhite(cg, 0.62f), VfxRenderHelper.mixWhite(cb, 0.62f),
                (int) (alpha * 245));
        poseStack.popPose();

        poseStack.popPose();
    }

    private void drawCrescentArc(VertexConsumer vc, PoseStack.Pose pose, float width, float depth, float yOff,
                                 int r, int g, int b, int a) {
        int segs = 10;
        float halfW = width * 0.5f;
        for (int i = 0; i < segs; i++) {
            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;
            float x0 = (t0 - 0.5f) * 2.0f * halfW;
            float x1 = (t1 - 0.5f) * 2.0f * halfW;
            // Smooth forward bow of crescent leading edge
            float bow0 = Mth.sin(t0 * Mth.PI) * depth * 0.72f;
            float bow1 = Mth.sin(t1 * Mth.PI) * depth * 0.72f;
            // Subtle 3D crescent thickness arch along Y
            float yArc0 = Mth.sin(t0 * Mth.PI) * 0.25f;
            float yArc1 = Mth.sin(t1 * Mth.PI) * 0.25f;

            VfxRenderHelper.addDoubleQuad(vc, pose,
                    x0, yOff + yArc0, bow0 + depth * 0.35f, t0, 0f,
                    x1, yOff + yArc1, bow1 + depth * 0.35f, t1, 0f,
                    x1, yOff - yArc1 * 0.4f, bow1 - depth * 0.85f, t1, 1f,
                    x0, yOff - yArc0 * 0.4f, bow0 - depth * 0.85f, t0, 1f,
                    r, g, b, a);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(EnergySlashEntity entity) {
        return VfxRenderHelper.ENERGY_SLASH_TEX;
    }
}

