package com.abyssalrpg.client.renderer;

import com.abyssalrpg.client.renderer.vfx.VfxRenderHelper;
import com.abyssalrpg.entity.AbstractAbyssalBoss;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Swirling Energy Aura overlay rendered across Boss anatomy during Phase 2 / Phase 3
 * and whenever the Boss channels an ultimate spell (castState > 0)!
 */
public class BossEnergyAuraLayer<T extends AbstractAbyssalBoss, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final int rgbColor;

    public BossEnergyAuraLayer(RenderLayerParent<T, M> parent, int rgbColor) {
        super(parent);
        this.rgbColor = rgbColor;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        int phase = entity.getSyncedPhase();
        int cast = entity.getCastState();
        if (phase < 2 && cast <= 0) return;

        float t = entity.tickCount + partialTick;
        float intensity = (phase >= 3 || cast > 0) ? 0.85f : 0.52f;
        float pulse = intensity * (0.78f + 0.22f * Mth.sin(t * 0.25f));

        int r = Mth.clamp((int) (((rgbColor >> 16) & 0xFF) * pulse), 0, 255);
        int g = Mth.clamp((int) (((rgbColor >> 8) & 0xFF) * pulse), 0, 255);
        int b = Mth.clamp((int) ((rgbColor & 0xFF) * pulse), 0, 255);
        int packedArgb = FastColor.ARGB32.color(255, r, g, b);

        float u = (t * 0.014f) % 1.0f;
        float v = (t * 0.011f) % 1.0f;

        VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(VfxRenderHelper.AURA_SWIRL_TEX, u, v));
        poseStack.pushPose();
        // Slight outward expansion centered cleanly around boss mid-torso so aura hugs scales without z-fighting
        poseStack.translate(0.0f, 0.75f, 0.0f);
        poseStack.scale(1.035f, 1.035f, 1.035f);
        poseStack.translate(0.0f, -0.75f, 0.0f);
        this.getParentModel().renderToBuffer(poseStack, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, packedArgb);
        poseStack.popPose();
    }
}

