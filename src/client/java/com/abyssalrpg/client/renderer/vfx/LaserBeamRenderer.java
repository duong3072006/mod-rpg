package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.entity.vfx.LaserBeamEntity;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders a Continuous 3D Volumetric Dragon-Breath / Soul-Annihilation Laser Beam:
 * Rotating plasma outer sheath + white-hot core + muzzle arcane sigil + impact blast flare!
 */
public class LaserBeamRenderer extends EntityRenderer<LaserBeamEntity> {
    public LaserBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(LaserBeamEntity entity, Frustum frustum, double x, double y, double z) {
        return true; // Long 32-block beams must never frustum-cull prematurely!
    }

    @Override
    public void render(LaserBeamEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        Vec3 delta = entity.getEndPos().subtract(entity.position());
        float length = (float) delta.length();
        if (length < 0.05f) return;

        float age = entity.tickCount + partialTick;
        float maxLife = Math.max(4f, entity.getMaxLife());
        float envIn = Mth.clamp(age / 3.0f, 0f, 1f);
        float envOut = Mth.clamp((maxLife - age) / 4.5f, 0f, 1f);
        float envelope = Math.min(envIn, envOut);
        if (envelope <= 0.01f) return;

        float pulse = 1.0f + Mth.sin(age * 1.4f) * 0.14f;
        float radius = entity.getBeamRadius() * envelope * pulse;
        int rgb = entity.getColor();
        int cr = VfxRenderHelper.r(rgb), cg = VfxRenderHelper.g(rgb), cb = VfxRenderHelper.b(rgb);
        float scroll = -age * 0.35f;

        Vector3f dir = new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize();
        Quaternionf alignY = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), dir);

        poseStack.pushPose();
        poseStack.mulPose(alignY);

        // 1. Muzzle Casting Arcane Magic Circle right in front of the Boss's jaw!
        VertexConsumer sigilGlow = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.MAGIC_CIRCLE_TEX));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 18f));
        VfxRenderHelper.drawHorizontalQuad(sigilGlow, poseStack.last(), 0.15f, radius * 2.6f,
                VfxRenderHelper.mixWhite(cr, 0.55f), VfxRenderHelper.mixWhite(cg, 0.55f), VfxRenderHelper.mixWhite(cb, 0.55f),
                (int) (envelope * 235));
        poseStack.popPose();

        // 2. Outer Rotating Colored Plasma Sheath (10-sided volumetric cylinder)
        VertexConsumer beamTrans = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.BEAM_TEX));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 20f));
        VfxRenderHelper.drawCylinder(beamTrans, poseStack.last(), 10,
                0f, radius * 1.15f, length, radius * 1.35f,
                scroll, scroll + length * 0.28f, cr, cg, cb, (int) (envelope * 205));
        poseStack.popPose();

        // 3. Counter-Rotating Mid Spiral Halo
        VertexConsumer beamGlow = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.BEAM_TEX));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * 30f));
        VfxRenderHelper.drawCylinder(beamGlow, poseStack.last(), 8,
                0f, radius * 0.85f, length, radius * 0.95f,
                scroll * 1.4f, scroll * 1.4f + length * 0.34f,
                VfxRenderHelper.mixWhite(cr, 0.35f), VfxRenderHelper.mixWhite(cg, 0.35f), VfxRenderHelper.mixWhite(cb, 0.35f),
                (int) (envelope * 195));
        poseStack.popPose();

        // 4. Inner Blinding White-Hot Laser Core!
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 38f));
        VfxRenderHelper.drawCylinder(beamGlow, poseStack.last(), 6,
                0f, radius * 0.45f, length, radius * 0.45f,
                scroll * 2.0f, scroll * 2.0f + length * 0.40f,
                VfxRenderHelper.mixWhite(cr, 0.85f), VfxRenderHelper.mixWhite(cg, 0.85f), VfxRenderHelper.mixWhite(cb, 0.85f),
                (int) (envelope * 255));
        poseStack.popPose();

        // 5. Impact Energy Core Flare at beam endpoint!
        poseStack.pushPose();
        poseStack.translate(0f, length, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 22f));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 16f));
        VfxRenderHelper.drawOctahedron(beamGlow, poseStack.last(), radius * 1.65f,
                VfxRenderHelper.mixWhite(cr, 0.70f), VfxRenderHelper.mixWhite(cg, 0.70f), VfxRenderHelper.mixWhite(cb, 0.70f),
                (int) (envelope * 235));
        poseStack.popPose();

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(LaserBeamEntity entity) {
        return VfxRenderHelper.BEAM_TEX;
    }
}

