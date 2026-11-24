package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.entity.mythic.nightmare.NightmareBoneSpikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer 3D hình chóp gai xương / hắc diện thạch nhọn cho {@link NightmareBoneSpikeEntity}
 * (dùng chung cho Bone Spike Eruption, Bone Cage, Spike Storm và Ultimate Nightmare Descent).
 */
public final class NightmareBoneSpikeRenderer extends EntityRenderer<NightmareBoneSpikeEntity> {
    public NightmareBoneSpikeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.45f;
    }

    @Override
    public boolean shouldRender(NightmareBoneSpikeEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(NightmareBoneSpikeEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float emerge = entity.getEmergeFraction(partialTick);
        float h = entity.getSpikeHeight() * emerge;
        float r = entity.getSpikeRadius();
        if (h < 0.08f) return;

        boolean enraged = entity.isEnraged();
        boolean cage = entity.isCageMode();

        int outerRgb = cage ? 0xD2C6B2 : (enraged ? 0x3A1216 : 0x261E24);
        int coreRgb = enraged ? 0xFF3A1A : 0xD91628;

        VertexConsumer outerVc = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.BEAM_TEX));
        VertexConsumer glowVc = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.SHOCKWAVE_TEX));

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));

        // Vỏ gai xương chóp lục giác nhọn
        VfxRenderHelper.drawCylinder(outerVc, poseStack.last(), 6,
                0.0f, r, h, 0.04f,
                0.0f, 1.0f,
                VfxRenderHelper.r(outerRgb), VfxRenderHelper.g(outerRgb), VfxRenderHelper.b(outerRgb), 240);

        // Vòng sáng khe nứt đỏ ở gốc gai
        VfxRenderHelper.drawCylinder(glowVc, poseStack.last(), 6,
                0.0f, r * 1.05f, h * 0.48f, r * 0.35f,
                0.0f, 1.0f,
                VfxRenderHelper.r(coreRgb), VfxRenderHelper.g(coreRgb), VfxRenderHelper.b(coreRgb),
                enraged ? 215 : 150);

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(NightmareBoneSpikeEntity entity) {
        return VfxRenderHelper.BEAM_TEX;
    }
}

