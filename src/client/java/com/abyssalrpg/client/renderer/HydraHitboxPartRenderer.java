package com.abyssalrpg.client.renderer;

import com.abyssalrpg.entity.mythic.hydra.HydraHitboxPartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer vô hình cho thực thể proxy hitbox {@link HydraHitboxPartEntity}.
 * Không vẽ mesh nhưng hiển thị khung hitbox chuẩn từng đầu/cổ/thân/đuôi khi bật F3+B.
 */
public final class HydraHitboxPartRenderer extends EntityRenderer<HydraHitboxPartEntity> {
    @SuppressWarnings("deprecation")
    private static final ResourceLocation ATLAS = TextureAtlas.LOCATION_BLOCKS;

    public HydraHitboxPartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(HydraHitboxPartEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Vô hình (hiện khung khi bật F3+B)
    }

    @Override
    public ResourceLocation getTextureLocation(HydraHitboxPartEntity entity) {
        return ATLAS;
    }
}

