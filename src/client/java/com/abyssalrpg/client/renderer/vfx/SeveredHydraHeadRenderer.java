package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.entity.vfx.SeveredHydraHeadEntity;
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
 * Renderer cho entity cosmetic đầu Spirit Hydra bị chặt rơi xuống đất (vỏ đá xám tro + lõi mắt/sừng xanh băng phát sáng).
 */
public final class SeveredHydraHeadRenderer extends EntityRenderer<SeveredHydraHeadEntity> {
    public SeveredHydraHeadRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SeveredHydraHeadEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        float lifeFrac = Mth.clamp(age / (float) SeveredHydraHeadEntity.LIFETIME_TICKS, 0.0f, 1.0f);
        float fade = (lifeFrac > 0.78f) ? Mth.clamp((1.0f - lifeFrac) / 0.22f, 0.0f, 1.0f) : 1.0f;
        if (fade <= 0.02f) return;

        float sc = entity.getVisualScale() * (0.72f + 0.28f * fade);
        boolean enraged = entity.isEnraged();
        int bodyRgb = enraged ? 0x3A2E4D : 0x2F3540;
        int eyeRgb = enraged ? 0xB46BFF : 0x4DF4FF;
        int br = VfxRenderHelper.r(bodyRgb), bg = VfxRenderHelper.g(bodyRgb), bb = VfxRenderHelper.b(bodyRgb);
        int er = VfxRenderHelper.r(eyeRgb), eg = VfxRenderHelper.g(eyeRgb), eb = VfxRenderHelper.b(eyeRgb);
        int alpha = Math.max(20, Math.round(fade * 235f));

        poseStack.pushPose();
        poseStack.translate(0.0, 0.36 * sc, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.wrapDegrees(yaw)));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getSpinPitch()));

        // Hộp sọ đầu rồng thuôn dài xám đá
        VertexConsumer shell = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.BEAM_TEX));
        poseStack.pushPose();
        poseStack.scale(0.78f * sc, 0.58f * sc, 1.48f * sc);
        VfxRenderHelper.drawOctahedron(shell, poseStack.last(), 0.54f, br, bg, bb, alpha);
        poseStack.popPose();

        // Mắt & sừng xanh băng phát sáng rực rỡ
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.BEAM_TEX));
        poseStack.pushPose();
        poseStack.translate(0.0, 0.12 * sc, -0.22 * sc);
        poseStack.scale(0.56f * sc, 0.32f * sc, 0.52f * sc);
        VfxRenderHelper.drawOctahedron(glow, poseStack.last(), 0.36f, er, eg, eb, alpha);
        poseStack.popPose();

        poseStack.popPose();

        // Vòng năng lượng hồn rỉ ra quanh đầu rơi trên mặt đất
        VertexConsumer ring = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.MAGIC_CIRCLE_TEX));
        poseStack.pushPose();
        poseStack.translate(0.0, 0.06, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 4.5f));
        VfxRenderHelper.drawHorizontalQuad(ring, poseStack.last(), 0.0f, 0.92f * sc,
                er, eg, eb, Math.max(10, Math.round(fade * 145f)));
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(SeveredHydraHeadEntity entity) {
        return VfxRenderHelper.BEAM_TEX;
    }
}
