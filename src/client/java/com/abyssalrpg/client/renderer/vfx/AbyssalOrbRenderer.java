package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.entity.vfx.AbyssalOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Renders a Stunning 3D Spinning Crystalline Plasma Orb surrounded by
 * Counter-Rotating Gyroscopic Arcane Rune Halo Rings!
 */
public class AbyssalOrbRenderer extends EntityRenderer<AbyssalOrbEntity> {
    public AbyssalOrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(AbyssalOrbEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        float size = entity.getOrbSize();
        float pulse = 1.0f + Mth.sin(age * 0.55f) * 0.12f;
        int rgb = entity.getColor();
        int cr = VfxRenderHelper.r(rgb), cg = VfxRenderHelper.g(rgb), cb = VfxRenderHelper.b(rgb);

        VertexConsumer beamTrans = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.BEAM_TEX));
        VertexConsumer beamGlow = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.BEAM_TEX));
        VertexConsumer ringGlow = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.MAGIC_CIRCLE_TEX));

        // 1. Inner White-Hot Spinning Crystal Core
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 14f));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 9f));
        VfxRenderHelper.drawOctahedron(beamGlow, poseStack.last(), size * 0.46f * pulse,
                VfxRenderHelper.mixWhite(cr, 0.80f), VfxRenderHelper.mixWhite(cg, 0.80f), VfxRenderHelper.mixWhite(cb, 0.80f), 255);
        poseStack.popPose();

        // 2. Outer Counter-Rotating Translucent Plasma Shell
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * 11f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 13f));
        VfxRenderHelper.drawOctahedron(beamTrans, poseStack.last(), size * 0.82f * pulse,
                cr, cg, cb, 195);
        poseStack.popPose();

        // 3. Two Orthogonal Gyroscopic Spinning Arcane Halo Rings!
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(35f + age * 10f));
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 15f));
        VfxRenderHelper.drawHorizontalQuad(ringGlow, poseStack.last(), 0f, size * 1.45f,
                VfxRenderHelper.mixWhite(cr, 0.40f), VfxRenderHelper.mixWhite(cg, 0.40f), VfxRenderHelper.mixWhite(cb, 0.40f), 210);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(-40f - age * 12f));
        poseStack.mulPose(Axis.XP.rotationDegrees(90f + age * 7f));
        VfxRenderHelper.drawHorizontalQuad(ringGlow, poseStack.last(), 0f, size * 1.25f,
                cr, cg, cb, 185);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssalOrbEntity entity) {
        return VfxRenderHelper.BEAM_TEX;
    }
}

