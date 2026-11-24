package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.model.AbyssalKnightModel;
import com.abyssalrpg.entity.AbyssalKnightBossEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class AbyssalKnightRenderer extends MobRenderer<AbyssalKnightBossEntity, AbyssalKnightModel> {
    private static final ResourceLocation TEXTURE = AbyssalRpgMod.id("textures/entity/abyssal_knight_boss.png");
    private static final RenderType GLOW_LAYER = RenderType.eyes(AbyssalRpgMod.id("textures/entity/abyssal_knight_boss_glow.png"));

    @SuppressWarnings("this-escape")
    public AbyssalKnightRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AbyssalKnightModel(ctx.bakeLayer(AbyssalKnightModel.LAYER_LOCATION)), 2.1f);
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW_LAYER;
            }
        });
        this.addLayer(new BossEnergyAuraLayer<>(this, 0xB855FF));
    }

    @Nullable
    @Override
    protected RenderType getRenderType(AbyssalKnightBossEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityCutoutNoCull(TEXTURE);
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssalKnightBossEntity entity) {
        return TEXTURE;
    }
}
