package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
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

/**
 * Renders a Smoothly Expanding 3D Glowing Ground Shockwave Ring + Surging Tapered Energy Wall!
 */
public class ShockwaveRingRenderer extends EntityRenderer<ShockwaveRingEntity> {
    public ShockwaveRingRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(ShockwaveRingEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ShockwaveRingEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        float maxLife = Math.max(4f, entity.getMaxLife());
        float t = Mth.clamp(age / maxLife, 0f, 1f);
        if (t >= 0.99f) return;

        // Cubic ease-out expansion for punchy explosive shockwave feel!
        float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
        float radius = 0.6f + (entity.getMaxRadius() - 0.6f) * eased;
        float fade = (1.0f - t) * (1.0f - t * 0.45f);

        int rgb = entity.getColor();
        int cr = VfxRenderHelper.r(rgb), cg = VfxRenderHelper.g(rgb), cb = VfxRenderHelper.b(rgb);

        VertexConsumer ringTrans = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.SHOCKWAVE_TEX));
        VertexConsumer ringGlow = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.SHOCKWAVE_TEX));
        VertexConsumer wallTrans = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.BEAM_TEX));

        // 1. Flat Ground Shockwave Disc (translucent colored + white-hot additive leading rim!)
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 6f));
        VfxRenderHelper.drawHorizontalQuad(ringTrans, poseStack.last(), 0.04f, radius, cr, cg, cb, (int) (fade * 220));
        VfxRenderHelper.drawHorizontalQuad(ringGlow, poseStack.last(), 0.06f, radius * 1.01f,
                VfxRenderHelper.mixWhite(cr, 0.65f), VfxRenderHelper.mixWhite(cg, 0.65f), VfxRenderHelper.mixWhite(cb, 0.65f),
                (int) (fade * 235));
        poseStack.popPose();

        // 2. Surging 3D Tapered Translucent Shockwave Wall!
        float wallHeight = Math.min(2.4f, entity.getMaxRadius() * 0.24f) * (1.0f - t * 0.55f);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * 8f));
        VfxRenderHelper.drawCylinder(wallTrans, poseStack.last(), 18,
                0.02f, radius * 0.88f, wallHeight, radius * 1.04f,
                0f, 1f, cr, cg, cb, (int) (fade * 145));
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ShockwaveRingEntity entity) {
        return VfxRenderHelper.SHOCKWAVE_TEX;
    }
}

