package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.item.NightmareArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeoModel 3D cho Bộ Giáp Nightmare Knight (`geo/armor/nightmare_armor.geo.json`).
 * Điều khiển độ rủ/bay động mượt theo `partialTick` cho 3 đốt áo choàng 1 bên (`cape_upper`, `cape_mid`, `cape_lower`)
 * thuần phía Client (0 packet, 0 server tick, chống xuyên chân khi chạy/nhảy/cúi/chém Dual Knife!).
 */
public final class NightmareArmorModel extends GeoModel<NightmareArmorItem> {
    public static final ResourceLocation GEO = AbyssalRpgMod.id("geo/armor/nightmare_armor.geo.json");
    public static final ResourceLocation TEXTURE = AbyssalRpgMod.id("textures/armor/nightmare_armor.png");
    public static final ResourceLocation ANIM = AbyssalRpgMod.id("animations/armor/nightmare_armor.animation.json");

    @Override
    public ResourceLocation getModelResource(NightmareArmorItem animatable, GeoRenderer<NightmareArmorItem> renderer) {
        return GEO;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(NightmareArmorItem animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(NightmareArmorItem animatable, GeoRenderer<NightmareArmorItem> renderer) {
        return TEXTURE;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(NightmareArmorItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(NightmareArmorItem animatable) {
        return ANIM;
    }

    @Override
    public void setCustomAnimations(NightmareArmorItem animatable, long instanceId,
                                    AnimationState<NightmareArmorItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone capeUpper = getAnimationProcessor().getBone("cape_upper");
        GeoBone capeMid = getAnimationProcessor().getBone("cape_mid");
        GeoBone capeLower = getAnimationProcessor().getBone("cape_lower");
        if (capeUpper == null && capeMid == null && capeLower == null) {
            return;
        }

        Entity entity = animationState.getData(DataTickets.ENTITY);
        float partialTick = animationState.getPartialTick();

        float walkSpeed = 0.0F;
        float sprintBoost = 0.0F;
        float fallLift = 0.0F;
        float crouchClearance = 0.0F;
        float lateralSway = 0.0F;
        float timeSec = partialTick / 20.0F;

        if (entity instanceof LivingEntity living) {
            timeSec = (living.tickCount + partialTick) / 20.0F;
            walkSpeed = Mth.clamp(living.walkAnimation.speed(partialTick), 0.0F, 1.0F);
            if (living.isSprinting()) {
                sprintBoost = 0.28F;
            }
            double vy = living.getDeltaMovement().y;
            if (!living.onGround() && vy < -0.05) {
                fallLift = (float) Mth.clamp(-vy * 0.42, 0.0, 0.40);
            }
            if (living.isCrouching()) {
                crouchClearance = 0.22F;
            }
            // Nhịp vung tay / xoay thân nhẹ giúp áo choàng dập dờn theo chuyển động xoay khi múa Dual Knife
            float bodyYawDelta = Mth.wrapDegrees(living.yBodyRot - living.yBodyRotO);
            lateralSway = Mth.clamp(bodyYawDelta * 0.012F, -0.22F, 0.22F);
        }

        float windFreq = 2.8F + walkSpeed * 3.8F;
        float wave1 = Mth.sin(timeSec * windFreq) * (0.030F + walkSpeed * 0.045F);
        float wave2 = Mth.sin(timeSec * windFreq - 0.85F) * (0.045F + walkSpeed * 0.075F);
        float wave3 = Mth.sin(timeSec * windFreq - 1.70F) * (0.065F + walkSpeed * 0.105F);

        // Âm rotX (rad) đẩy vạt áo (-Y) hất nhẹ về phía sau (+Z), giữ khoảng hở sạch với chân
        float baseLift = 0.04F + walkSpeed * 0.34F + sprintBoost + fallLift + crouchClearance;
        if (capeUpper != null) {
            capeUpper.setRotX(-(baseLift * 0.55F + Math.max(0.0F, wave1)));
            capeUpper.setRotZ(lateralSway * 0.45F + Mth.cos(timeSec * 2.1F) * 0.02F);
        }
        if (capeMid != null) {
            capeMid.setRotX(-(baseLift * 0.32F + wave2));
            capeMid.setRotZ(lateralSway * 0.55F + Mth.cos(timeSec * 2.1F - 0.7F) * 0.03F);
        }
        if (capeLower != null) {
            capeLower.setRotX(-(baseLift * 0.26F + wave3));
            capeLower.setRotZ(lateralSway * 0.65F + Mth.cos(timeSec * 2.1F - 1.4F) * 0.04F);
        }
    }
}

