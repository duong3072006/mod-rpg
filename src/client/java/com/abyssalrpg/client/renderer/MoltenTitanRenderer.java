package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.model.MoltenTitanModel;
import com.abyssalrpg.entity.MoltenTitanBossEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class MoltenTitanRenderer extends MobRenderer<MoltenTitanBossEntity, MoltenTitanModel> {
    private static final ResourceLocation TEXTURE = AbyssalRpgMod.id("textures/entity/molten_titan_boss.png");
    private static final RenderType GLOW_LAYER = RenderType.eyes(AbyssalRpgMod.id("textures/entity/molten_titan_boss_glow.png"));

    @SuppressWarnings("this-escape")
    public MoltenTitanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MoltenTitanModel(ctx.bakeLayer(MoltenTitanModel.LAYER_LOCATION)), 2.7f);
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW_LAYER;
            }
        });
        this.addLayer(new BossEnergyAuraLayer<>(this, 0xFF6B14));
    }

    @Nullable
    @Override
    protected RenderType getRenderType(MoltenTitanBossEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityCutoutNoCull(TEXTURE);
    }

    @Override
    public ResourceLocation getTextureLocation(MoltenTitanBossEntity entity) {
        return TEXTURE;
    }
}
