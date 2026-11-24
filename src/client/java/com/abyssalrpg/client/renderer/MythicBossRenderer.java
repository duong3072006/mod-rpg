package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.AbstractAbyssalBoss;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Shared high-detail renderer for all 7 Mythological Titan Bosses:
 * double-sided alpha-cutout base pass + full-bright Emissive Eyes/Glow pass + Phase 2/3 Swirling Aura!
 */
public class MythicBossRenderer<T extends AbstractAbyssalBoss, M extends EntityModel<T>> extends MobRenderer<T, M> {
    private final ResourceLocation texture;
    private final RenderType glowLayer;

    @SuppressWarnings("this-escape")
    public MythicBossRenderer(EntityRendererProvider.Context ctx, M model, String texKey, float shadowRadius, int auraRgb) {
        super(ctx, model, shadowRadius);
        this.texture = AbyssalRpgMod.id("textures/entity/" + texKey + ".png");
        this.glowLayer = RenderType.eyes(AbyssalRpgMod.id("textures/entity/" + texKey + "_glow.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return glowLayer;
            }
        });
        this.addLayer(new BossEnergyAuraLayer<>(this, auraRgb));
    }

    @Nullable
    @Override
    protected RenderType getRenderType(T entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityCutoutNoCull(this.texture);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return this.texture;
    }
}

