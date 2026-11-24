package com.abyssalrpg.client.renderer;

import com.abyssalrpg.entity.mythic.cinder.CinderDragonHitboxPartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** Renderer vô hình cho proxy hitbox multipart {@link CinderDragonHitboxPartEntity} (hiện khung khi bật F3+B). */
public final class CinderDragonHitboxPartRenderer extends EntityRenderer<CinderDragonHitboxPartEntity> {
    @SuppressWarnings("deprecation")
    private static final ResourceLocation ATLAS = TextureAtlas.LOCATION_BLOCKS;

    public CinderDragonHitboxPartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(CinderDragonHitboxPartEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Vô hình — chỉ lộ khung hitbox khi nhấn F3+B
    }

    @Override
    public ResourceLocation getTextureLocation(CinderDragonHitboxPartEntity entity) {
        return ATLAS;
    }
}

