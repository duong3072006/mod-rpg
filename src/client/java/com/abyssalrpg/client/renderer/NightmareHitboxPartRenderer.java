package com.abyssalrpg.client.renderer;

import com.abyssalrpg.entity.mythic.nightmare.NightmareHitboxPartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer vô hình cho proxy hitbox multipart {@link NightmareHitboxPartEntity}.
 * Không vẽ bất kỳ mesh nào nhưng cho phép nhấn F3+B để nhìn chính xác từng hitbox đầu/ngực/tay/chân.
 */
public final class NightmareHitboxPartRenderer extends EntityRenderer<NightmareHitboxPartEntity> {
    @SuppressWarnings("deprecation")
    private static final ResourceLocation ATLAS = TextureAtlas.LOCATION_BLOCKS;

    public NightmareHitboxPartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(NightmareHitboxPartEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Vô hình (chỉ hiện khung hitbox khi bật F3+B)
    }

    @Override
    public ResourceLocation getTextureLocation(NightmareHitboxPartEntity entity) {
        return ATLAS;
    }
}

