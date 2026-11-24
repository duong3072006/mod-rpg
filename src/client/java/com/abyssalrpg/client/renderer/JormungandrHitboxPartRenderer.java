package com.abyssalrpg.client.renderer;

import com.abyssalrpg.entity.mythic.jormungandr.JormungandrHitboxPartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer vô hình cho proxy hitbox dọc thân Jörmungandr (hiện rõ khi bật F3+B).
 */
public final class JormungandrHitboxPartRenderer extends EntityRenderer<JormungandrHitboxPartEntity> {
    @SuppressWarnings("deprecation")
    private static final ResourceLocation ATLAS = TextureAtlas.LOCATION_BLOCKS;

    public JormungandrHitboxPartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(JormungandrHitboxPartEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Vô hình
    }

    @Override
    public ResourceLocation getTextureLocation(JormungandrHitboxPartEntity entity) {
        return ATLAS;
    }
}

