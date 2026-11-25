package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.item.CerberusArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * GeoModel 3D cho Bộ Giáp Cerberus (`geo/armor/cerberus_armor.geo.json`):
 * - Nguồn điều khiển animation duy nhất phía Client (tích phân pha mượt đơn điệu, 0 giật):
 *   1. Tai sói trên mũ (`cerberus_ear_r/l`): nhúc nhích cảnh giác theo nhịp thở & di chuyển.
 *   2. Lõi magma ngực (`cerberus_magma_core`): nhịp đập lửa địa ngục rực cháy (đồng bộ độ sáng emissive!).
 *   3. Hai đầu chó săn vai (`cerberus_shoulder_head_r/l` & `cerberus_shoulder_jaw_r/l`): hít thở gầm gừ
 *      và hé hàm nhe răng nanh luân phiên trái/phải (mạnh hơn khi người chơi đang chiến đấu!).
 *   4. Ngọn lửa pixel gót giày (`cerberus_boot_flame_r/l`): bập bùng phập phồng và nghiêng theo tốc độ chạy.
 */
public final class CerberusArmorModel extends GeoModel<CerberusArmorItem> {
    public static final ResourceLocation GEO = AbyssalRpgMod.id("geo/armor/cerberus_armor.geo.json");
    public static final ResourceLocation TEXTURE = AbyssalRpgMod.id("textures/armor/cerberus_armor.png");
    public static final ResourceLocation ANIM = AbyssalRpgMod.id("animations/armor/cerberus_armor.animation.json");

    private static final float DEG_TO_RAD = Mth.DEG_TO_RAD;
    private static final float TWO_PI = Mth.TWO_PI;

    private final Map<Entity, AnimState> states = new WeakHashMap<>();

    private static final class AnimState {
        double lastTimeSec = Double.NaN;
        double phase = 0.0;
        double flamePhase = 0.0;
        float smoothMove = 0.0F;
        float magmaPulse = 0.5F;
        double lastParticleSec = Double.NEGATIVE_INFINITY;
    }

    public float getMagmaPulse(Entity entity) {
        if (entity == null) {
            return 0.55F;
        }
        AnimState st = this.states.get(entity);
        return st != null ? st.magmaPulse : 0.55F;
    }

    public boolean shouldEmitFootParticles(Entity entity, double nowSec, double minIntervalSec) {
        if (entity == null) {
            return false;
        }
        AnimState st = this.states.computeIfAbsent(entity, k -> new AnimState());
        if (nowSec - st.lastParticleSec < minIntervalSec && nowSec >= st.lastParticleSec) {
            return false;
        }
        st.lastParticleSec = nowSec;
        return true;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(CerberusArmorItem animatable) {
        return GEO;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(CerberusArmorItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CerberusArmorItem animatable) {
        return ANIM;
    }

    @Override
    public ResourceLocation getModelResource(CerberusArmorItem animatable, GeoRenderer<CerberusArmorItem> renderer) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(CerberusArmorItem animatable, GeoRenderer<CerberusArmorItem> renderer) {
        return TEXTURE;
    }

    @Override
    public void setCustomAnimations(CerberusArmorItem animatable, long instanceId,
            AnimationState<CerberusArmorItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        Entity entity = animationState.getData(DataTickets.ENTITY);
        if (entity == null) {
            return;
        }
        AnimState st = this.states.computeIfAbsent(entity, k -> new AnimState());
        double timeSec = (entity.tickCount + animationState.getPartialTick()) / 20.0;

        float dt = 0.0F;
        if (Double.isNaN(st.lastTimeSec) || timeSec < st.lastTimeSec || (timeSec - st.lastTimeSec) > 0.5) {
            st.lastTimeSec = timeSec;
        } else {
            dt = (float) (timeSec - st.lastTimeSec);
            st.lastTimeSec = timeSec;
        }

        float targetMove = 0.0F;
        boolean combatActive = false;
        if (entity instanceof LivingEntity living) {
            double hSpeed = Math.hypot(living.getDeltaMovement().x, living.getDeltaMovement().z);
            targetMove = Mth.clamp((float) (hSpeed * 4.5), 0.0F, 1.0F);
            if (living.isSprinting()) {
                targetMove = Math.max(targetMove, 0.90F);
            }
            combatActive = living.swinging || living.hurtTime > 0;
        }

        float lerpAlpha = dt > 0.0F ? (1.0F - (float) Math.exp(-dt * 8.0)) : 0.0F;
        st.smoothMove = Mth.lerp(lerpAlpha, st.smoothMove, targetMove);
        st.phase = (st.phase + dt * (1.85 + st.smoothMove * 1.45)) % 10000.0;
        st.flamePhase = (st.flamePhase + dt * (7.5 + st.smoothMove * 4.0)) % 10000.0;

        // 1. Nhịp đập dung nham nóng bỏng (`cerberus_magma_core`)
        float wave = 0.5F + 0.5F * Mth.sin((float) (st.phase * 1.35));
        float pulse = wave * wave;
        if (combatActive) {
            pulse = Mth.clamp(pulse + 0.25F, 0.0F, 1.0F);
        }
        st.magmaPulse = pulse;

        GeoBone magmaCore = getAnimationProcessor().getBone("cerberus_magma_core");
        if (magmaCore != null) {
            float sc = 0.96F + pulse * 0.10F;
            magmaCore.setScaleX(sc);
            magmaCore.setScaleY(sc);
            magmaCore.setScaleZ(0.96F + pulse * 0.14F);
        }

        // 2. Cặp sừng cong phân đốt trên mũ rung nhẹ đầy uy lực theo nhịp thở & chạy
        float hornSway = Mth.sin((float) (st.phase * 0.90)) * 1.1F
                + Mth.sin((float) (st.phase * 2.8)) * (st.smoothMove * 1.6F);
        GeoBone earR = getAnimationProcessor().getBone("cerberus_ear_r");
        GeoBone earL = getAnimationProcessor().getBone("cerberus_ear_l");
        if (earR != null) {
            earR.setRotX((-st.smoothMove * 3.5F + hornSway * 0.4F) * DEG_TO_RAD);
            earR.setRotZ((hornSway) * DEG_TO_RAD);
        }
        if (earL != null) {
            earL.setRotX((-st.smoothMove * 3.5F - hornSway * 0.4F) * DEG_TO_RAD);
            earL.setRotZ((-hornSway) * DEG_TO_RAD);
        }

        // 3. Hai đầu phụ Cerberus trên vai (xoay chéo 48° về trước-ngoài, 1 mắt đỏ cam / 1 mắt tím nhẹ) thở gầm & há hàm đúng trục mõm!
        float snarlBoost = combatActive ? 1.0F : 0.0F;
        float headRBreath = Mth.sin((float) st.phase);
        float headLBreath = Mth.sin((float) (st.phase + 2.15));
        float jawROpen = Math.max(0.0F, Mth.sin((float) (st.phase * 0.72))) * (9.5F + snarlBoost * 11.0F);
        float jawLOpen = Math.max(0.0F, Mth.sin((float) (st.phase * 0.72 + Math.PI))) * (9.5F + snarlBoost * 11.0F);

        final float cos48 = 0.6691F;
        final float sin48 = 0.7431F;

        GeoBone shHeadR = getAnimationProcessor().getBone("cerberus_shoulder_head_r");
        GeoBone shJawR = getAnimationProcessor().getBone("cerberus_shoulder_jaw_r");
        if (shHeadR != null) {
            float pitchR = headRBreath * 2.4F + snarlBoost * 3.0F;
            shHeadR.setRotX((-pitchR * cos48) * DEG_TO_RAD);
            shHeadR.setRotZ((+pitchR * sin48) * DEG_TO_RAD);
            shHeadR.setRotY((Mth.cos((float) (st.phase * 0.6)) * 3.0F) * DEG_TO_RAD);
        }
        if (shJawR != null) {
            shJawR.setRotX((-jawROpen * cos48) * DEG_TO_RAD);
            shJawR.setRotZ((+jawROpen * sin48) * DEG_TO_RAD);
        }

        GeoBone shHeadL = getAnimationProcessor().getBone("cerberus_shoulder_head_l");
        GeoBone shJawL = getAnimationProcessor().getBone("cerberus_shoulder_jaw_l");
        if (shHeadL != null) {
            float pitchL = headLBreath * 2.4F + snarlBoost * 3.0F;
            shHeadL.setRotX((-pitchL * cos48) * DEG_TO_RAD);
            shHeadL.setRotZ((-pitchL * sin48) * DEG_TO_RAD);
            shHeadL.setRotY((-Mth.cos((float) (st.phase * 0.6 + 1.5)) * 3.0F) * DEG_TO_RAD);
        }
        if (shJawL != null) {
            shJawL.setRotX((-jawLOpen * cos48) * DEG_TO_RAD);
            shJawL.setRotZ((-jawLOpen * sin48) * DEG_TO_RAD);
        }

        // 4. Ngọn lửa pixel gót giày bập bùng phập phồng & nghiêng nhẹ khi chạy!
        float flR = Mth.sin((float) st.flamePhase);
        float flL = Mth.sin((float) (st.flamePhase + TWO_PI * 0.37F));
        GeoBone flameR = getAnimationProcessor().getBone("cerberus_boot_flame_r");
        GeoBone flameL = getAnimationProcessor().getBone("cerberus_boot_flame_l");
        if (flameR != null) {
            flameR.setScaleY(0.92F + 0.16F * (0.5F + 0.5F * flR) + st.smoothMove * 0.12F);
            flameR.setScaleX(0.95F + 0.08F * Mth.cos((float) st.flamePhase));
            flameR.setRotX((-st.smoothMove * 14.0F + flR * 4.5F) * DEG_TO_RAD);
        }
        if (flameL != null) {
            flameL.setScaleY(0.92F + 0.16F * (0.5F + 0.5F * flL) + st.smoothMove * 0.12F);
            flameL.setScaleX(0.95F + 0.08F * Mth.cos((float) (st.flamePhase + 1.8)));
            flameL.setRotX((-st.smoothMove * 14.0F + flL * 4.5F) * DEG_TO_RAD);
        }
    }
}

