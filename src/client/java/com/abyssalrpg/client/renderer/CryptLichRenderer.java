package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.model.CryptLichModel;
import com.abyssalrpg.entity.CryptLichBossEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class CryptLichRenderer extends MobRenderer<CryptLichBossEntity, CryptLichModel> {
    private static final ResourceLocation TEXTURE = AbyssalRpgMod.id("textures/entity/crypt_lich_boss.png");
    private static final RenderType GLOW_LAYER = RenderType.eyes(AbyssalRpgMod.id("textures/entity/crypt_lich_boss_glow.png"));

    @SuppressWarnings("this-escape")
    public CryptLichRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CryptLichModel(ctx.bakeLayer(CryptLichModel.LAYER_LOCATION)), 2.2f);
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW_LAYER;
            }
        });
        this.addLayer(new BossEnergyAuraLayer<>(this, 0x2AF5D6));
    }

    @Nullable
    @Override
    protected RenderType getRenderType(CryptLichBossEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityCutoutNoCull(TEXTURE);
    }

    @Override
    public ResourceLocation getTextureLocation(CryptLichBossEntity entity) {
        return TEXTURE;
    }
}
