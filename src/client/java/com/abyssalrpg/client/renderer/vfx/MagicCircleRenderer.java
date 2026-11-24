package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.entity.vfx.MagicCircleEntity;
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
 * Renders 2 counter-rotating glowing 3D Arcane Magic Sigil Circles on the ground,
 * rising runes during charge, and a colossal 28-block Sky-Piercing 3D Energy Pillar on eruption!
 */
public class MagicCircleRenderer extends EntityRenderer<MagicCircleEntity> {
    public MagicCircleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(MagicCircleEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(MagicCircleEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        float radius = entity.getRadius();
        int rgb = entity.getColor();
        int cr = VfxRenderHelper.r(rgb), cg = VfxRenderHelper.g(rgb), cb = VfxRenderHelper.b(rgb);
        int chargeTicks = Math.max(1, entity.getChargeTicks());
        boolean erupted = entity.isErupted();

        float chargeProg = Mth.clamp(age / chargeTicks, 0f, 1f);
        float eruptAge = Math.max(0f, age - chargeTicks);
        float fade = erupted ? Mth.clamp(1.0f - eruptAge / 14.0f, 0f, 1f) : 1.0f;
        if (fade <= 0.01f) return;

        float popScale = erupted ? (1.0f + eruptAge * 0.025f) : (0.25f + 0.75f * (1f - (1f - Math.min(1f, age / 6f)) * (1f - Math.min(1f, age / 6f))));
        float curRadius = radius * popScale;
        float spinSpeed = 2.8f + chargeProg * 5.2f;

        // 1. Primary Ground Arcane Sigil Circle (clockwise)
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * spinSpeed));
        int baseAlpha = (int) (fade * (185 + 65 * chargeProg));
        VertexConsumer circleTrans = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.MAGIC_CIRCLE_TEX));
        VfxRenderHelper.drawHorizontalQuad(circleTrans, poseStack.last(), 0.03f, curRadius, cr, cg, cb, baseAlpha);
        VertexConsumer circleGlow = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.MAGIC_CIRCLE_TEX));
        VfxRenderHelper.drawHorizontalQuad(circleGlow, poseStack.last(), 0.045f, curRadius * 0.98f,
                VfxRenderHelper.mixWhite(cr, 0.45f), VfxRenderHelper.mixWhite(cg, 0.45f), VfxRenderHelper.mixWhite(cb, 0.45f),
                (int) (fade * 215));
        poseStack.popPose();

        // 2. Counter-Rotating Elevated Inner Sigil Halo (rises as charge nears eruption!)
        poseStack.pushPose();
        float liftY = 0.16f + chargeProg * 0.55f + (erupted ? eruptAge * 0.12f : 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * spinSpeed * 1.45f));
        VfxRenderHelper.drawHorizontalQuad(circleGlow, poseStack.last(), liftY, curRadius * 0.72f,
                VfxRenderHelper.mixWhite(cr, 0.65f), VfxRenderHelper.mixWhite(cg, 0.65f), VfxRenderHelper.mixWhite(cb, 0.65f),
                (int) (fade * (140 + 90 * chargeProg)));
        poseStack.popPose();

        // 3. Charging Convergence Cylinder (rising translucent rune preview wall right before blast!)
        if (!erupted && chargeProg > 0.35f) {
            float previewAlpha = ((chargeProg - 0.35f) / 0.65f) * 115f;
            float scrollV = -age * 0.12f;
            VertexConsumer beamTrans = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.BEAM_TEX));
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 4f));
            VfxRenderHelper.drawCylinder(beamTrans, poseStack.last(), 10,
                    0.05f, curRadius * 0.88f, 2.2f + chargeProg * 3.5f, curRadius * (0.88f - chargeProg * 0.45f),
                    scrollV, scrollV + 2.0f, cr, cg, cb, (int) previewAlpha);
            poseStack.popPose();
        }

        // 4. ERUPTION: SKY-PIERCING 28-BLOCK VOLUMETRIC 3D LIGHT PILLAR!
        if (erupted) {
            float pillarFade = Mth.clamp(1.0f - (eruptAge / 13.5f), 0f, 1f);
            float pillarRad = radius * (0.68f * pillarFade + 0.14f);
            float pillarHeight = 28.0f;
            float scroll = -age * 0.28f;

            // Outer glowing colored plasma pillar
            VertexConsumer beamTrans = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.BEAM_TEX));
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 16f));
            VfxRenderHelper.drawCylinder(beamTrans, poseStack.last(), 12,
                    0.0f, pillarRad * 1.15f, pillarHeight, pillarRad * 0.82f,
                    scroll, scroll + 4.5f, cr, cg, cb, (int) (pillarFade * 210));
            poseStack.popPose();

            // Inner blinding white-hot laser core!
            VertexConsumer beamCore = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.BEAM_TEX));
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-age * 24f));
            VfxRenderHelper.drawCylinder(beamCore, poseStack.last(), 8,
                    0.0f, pillarRad * 0.48f, pillarHeight, pillarRad * 0.34f,
                    scroll * 1.5f, scroll * 1.5f + 5.0f,
                    VfxRenderHelper.mixWhite(cr, 0.78f), VfxRenderHelper.mixWhite(cg, 0.78f), VfxRenderHelper.mixWhite(cb, 0.78f),
                    (int) (pillarFade * 255));
            poseStack.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(MagicCircleEntity entity) {
        return VfxRenderHelper.MAGIC_CIRCLE_TEX;
    }
}

