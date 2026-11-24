package com.abyssalrpg.client.renderer.vfx;

import com.abyssalrpg.entity.mythic.jormungandr.VenomProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer cầu độc phát sáng xanh-tím cho {@link VenomProjectileEntity}.
 */
public final class VenomProjectileRenderer extends EntityRenderer<VenomProjectileEntity> {
    public VenomProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.35f;
    }

    @Override
    public void render(VenomProjectileEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        int outer = entity.isEnraged() ? 0xB86BFF : 0x62E63C;
        int core = entity.isEnraged() ? 0xF0D8FF : 0xC8FF8A;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 16.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 11.0f));
        VertexConsumer vcOuter = buffers.getBuffer(RenderType.entityTranslucentEmissive(VfxRenderHelper.AURA_SWIRL_TEX));
        VfxRenderHelper.drawOctahedron(vcOuter, poseStack.last(), 0.58f,
                VfxRenderHelper.r(outer), VfxRenderHelper.g(outer), VfxRenderHelper.b(outer), 215);
        VertexConsumer vcCore = buffers.getBuffer(RenderType.eyes(VfxRenderHelper.SHOCKWAVE_TEX));
        VfxRenderHelper.drawOctahedron(vcCore, poseStack.last(), 0.34f,
                VfxRenderHelper.r(core), VfxRenderHelper.g(core), VfxRenderHelper.b(core), 255);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(VenomProjectileEntity entity) {
        return VfxRenderHelper.AURA_SWIRL_TEX;
    }
}

