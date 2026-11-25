package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.item.SpiritHydraArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * GeoModel 3D cho Bộ Giáp Spirit Hydra (`geo/armor/spirit_hydra_armor.geo.json`):
 * - Nguồn điều khiển animation DUY NHẤT phía Client (0 áp chồng `.animation.json`, tích phân pha mượt đơn điệu):
 *   1. Đôi vây mang 5 nan trên mũ (`hydra_crest_l/r`): phập phồng đối xứng nhẹ.
 *   2. Lõi tim tinh thể ngực (`hydra_heart_core`) & 11 gai lưng (`hydra_back_spines`): nhịp tim đập kép `lub-dub`.
 *   3. 3 đầu Hồn Xà phụ (`L`, `R`, `C`): ngoài nhịp thở tự nhiên còn có bộ máy hành vi sinh động —
 *      tự động luân phiên chơi đùa/cọ mũi với nhau (`PLAY_NUDGE_L_C`, `PLAY_NUDGE_R_C`, `PLAY_BANTER_L_R`)
 *      và phun hơi lửa linh hồn xanh lam KHÔNG GÂY SÁT THƯƠNG (`BREATHE_SOLO`, `BREATHE_TRIO`) kèm
 *      tính toán động học thuận (FK) chính xác vị trí & hướng miệng cả 3 đầu cho hệ hạt lửa xanh!
 *   4. Đuôi Hồn Xà 6 đốt (`tail_1..tail_6`): sóng truyền pha mượt, nâng nhẹ khi chạy/nhảy/ngồi.
 */
public final class SpiritHydraArmorModel extends GeoModel<SpiritHydraArmorItem> {
    public static final ResourceLocation GEO = AbyssalRpgMod.id("geo/armor/spirit_hydra_armor.geo.json");
    public static final ResourceLocation TEXTURE = AbyssalRpgMod.id("textures/armor/spirit_hydra_armor.png");
    public static final ResourceLocation ANIM = AbyssalRpgMod.id("animations/armor/spirit_hydra_armor.animation.json");

    public static final int HEAD_LEFT = 0;
    public static final int HEAD_RIGHT = 1;
    public static final int HEAD_CENTER = 2;

    private static final float DEG_TO_RAD = Mth.DEG_TO_RAD;
    private static final float TWO_PI = Mth.TWO_PI;
    private static final float NECK_SEG2_LAG = 0.42F;
    private static final float NECK_HEAD_LAG = 0.80F;
    private static final float TAIL_PHASE_LAG = 0.56F;

    // Tọa độ gốc (Bedrock px) khớp 1:1 với `spirit_hydra_armor.geo.json` cho động học thuận (FK) 3 miệng rồng & chóp đuôi!
    private static final Vector3f[] TAIL_PIVS = {
            new Vector3f(  0.00F, 12.25F,  +2.65F),
            new Vector3f( -1.75F,  8.45F,  +6.60F),
            new Vector3f( -4.55F,  5.65F, +10.55F),
            new Vector3f( -7.55F,  5.45F, +13.45F),
            new Vector3f( -9.85F,  8.05F, +14.85F),
            new Vector3f(-10.65F, 12.40F, +14.75F)
    };
    private static final Vector3f TAIL_TIP_REST = new Vector3f(-10.44F, 22.07F, +12.15F);

    // 3 bộ góc mẫu đã kiểm chứng FK 0 xuyên thân lưng cho 3 tư thế đuôi chơi đùa cùng 3 đầu Hồn Xà:
    // Mỗi phần tử là {rotX_deg, rotY_deg, rotZ_deg} cho từng đốt `tail_1 .. tail_6`.
    private static final float[][] TAIL_POSE_TEASE_R = {
            { -24.0F, -14.0F, +4.0F },
            { -14.0F,  -4.0F, +4.0F },
            {  -2.0F,  +2.0F, +2.0F },
            {  +6.0F,  +4.0F,  0.0F },
            {  +8.0F,  +4.0F,  0.0F },
            {  +6.0F,  +2.0F,  0.0F }
    };
    private static final float[][] TAIL_POSE_TEASE_L = {
            { -16.0F, +94.0F, -7.0F },
            { -10.0F, +24.0F, -4.0F },
            {  -2.0F, +12.0F, -2.0F },
            {  +6.0F,  -4.0F,  0.0F },
            {  +8.0F,  -4.0F,  0.0F },
            {  +6.0F,  -2.0F,  0.0F }
    };
    private static final float[][] TAIL_POSE_TEASE_C = {
            { -44.0F, +62.0F, -6.0F },
            { -18.0F,  +8.0F, -2.0F },
            {   0.0F,  +2.0F,  0.0F },
            { +12.0F,   0.0F,  0.0F },
            { +12.0F,   0.0F,  0.0F },
            {  +8.0F,   0.0F,  0.0F }
    };

    private static final Vector3f PIV_L1 = new Vector3f(+2.55F, 20.30F, +3.10F);
    private static final Vector3f PIV_L2 = new Vector3f(+9.60F, 27.15F, +4.45F);
    private static final Vector3f PIV_LH = new Vector3f(+14.95F, 27.80F, -0.20F);
    private static final Vector3f MUZZLE_L_REST = new Vector3f(+19.15F, 26.85F, -3.45F);
    private static final Vector3f DIR_L_REST = new Vector3f(+0.822F, -0.180F, -0.540F).normalize();

    private static final Vector3f PIV_R1 = new Vector3f(-2.55F, 20.30F, +3.10F);
    private static final Vector3f PIV_R2 = new Vector3f(-9.60F, 27.15F, +4.45F);
    private static final Vector3f PIV_RH = new Vector3f(-14.95F, 27.80F, -0.20F);
    private static final Vector3f MUZZLE_R_REST = new Vector3f(-19.15F, 26.85F, -3.45F);
    private static final Vector3f DIR_R_REST = new Vector3f(-0.822F, -0.180F, -0.540F).normalize();

    private static final Vector3f PIV_C1 = new Vector3f(0.00F, 20.80F, +2.85F);
    private static final Vector3f PIV_C2 = new Vector3f(0.00F, 32.75F, +9.85F);
    private static final Vector3f PIV_CH = new Vector3f(0.00F, 40.95F, +2.00F);
    private static final Vector3f MUZZLE_C_REST = new Vector3f(0.00F, 40.25F, -4.15F);
    private static final Vector3f DIR_C_REST = new Vector3f(0.00F, -0.060F, -0.998F).normalize();

    private enum HydraEmote {
        IDLE,
        PLAY_NUDGE_L_C,
        PLAY_NUDGE_R_C,
        PLAY_BANTER_L_R,
        PLAY_TAIL_TEASE_R,
        PLAY_TAIL_TEASE_L,
        PLAY_TAIL_TRIO_CHASE,
        BREATHE_SOLO,
        BREATHE_TRIO
    }

    /** Dữ liệu đầu ra thuần đọc cho Renderer phát hạt lửa xanh vô hại đúng từ miệng 3 đầu & chóp đuôi phát sáng! */
    public static final class HeadBreathSample {
        public final Vector3f[] muzzleModelPx = { new Vector3f(), new Vector3f(), new Vector3f() };
        public final Vector3f[] dirModel = { new Vector3f(), new Vector3f(), new Vector3f() };
        public final float[] breathStrength = new float[3];
        public final float[] playSparkStrength = new float[3];
        public final Vector3f tailTipModelPx = new Vector3f();
        public float tailPlaySpark = 0.0F;
        public float maxBreath = 0.0F;
        public double lastParticleEmitSec = Double.NEGATIVE_INFINITY;
    }

    private final Map<Entity, EntityAnimState> entityStates = new WeakHashMap<>();

    private static final class EntityAnimState {
        double lastTimeSec = Double.NaN;
        double hydraPhase = 0.0;
        double tailPhase = 0.0;
        double heartPhase = 0.0;
        float lastHeartPulse = 0.0F;
        float smoothMove = 0.0F;
        float smoothAir = 0.0F;
        float smoothCrouch = 0.0F;
        float smoothYawLag = 0.0F;

        // Trạng thái chơi đùa (3 đầu & đuôi) và phun lửa xanh vô hại của Bộ Giáp Spirit Hydra
        HydraEmote activeEmote = HydraEmote.IDLE;
        float emoteElapsed = 0.0F;
        float emoteDuration = 2.6F;
        float cooldownSec = 1.6F;
        int emoteCounter = 0;
        int soloBreathHead = HEAD_LEFT;
        final HeadBreathSample breathSample = new HeadBreathSample();
    }

    /** Trả về cường độ nhịp tim đập hiện tại (`0.0F..1.0F`, sóng kép lub-dub) để đồng bộ độ sáng emissive trong renderer. */
    public float getCurrentHeartbeatPulse(Entity entity) {
        if (entity == null) {
            return 0.0F;
        }
        EntityAnimState st = this.entityStates.get(entity);
        return st != null ? st.lastHeartPulse : 0.0F;
    }

    /** Trả về dữ liệu vị trí miệng + hướng phun + cường độ hơi lửa xanh (vô hại) cho 3 đầu Hồn Xà của entity. */
    public HeadBreathSample getHeadBreathSample(Entity entity) {
        if (entity == null) {
            return null;
        }
        EntityAnimState st = this.entityStates.get(entity);
        return st != null ? st.breathSample : null;
    }

    @Override
    public ResourceLocation getModelResource(SpiritHydraArmorItem animatable,
            GeoRenderer<SpiritHydraArmorItem> renderer) {
        return GEO;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(SpiritHydraArmorItem animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(SpiritHydraArmorItem animatable,
            GeoRenderer<SpiritHydraArmorItem> renderer) {
        return TEXTURE;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(SpiritHydraArmorItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(SpiritHydraArmorItem animatable) {
        return ANIM;
    }

    @Override
    public void setCustomAnimations(SpiritHydraArmorItem animatable, long instanceId,
            AnimationState<SpiritHydraArmorItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone crestL = getAnimationProcessor().getBone("hydra_crest_l");
        GeoBone crestR = getAnimationProcessor().getBone("hydra_crest_r");
        GeoBone heartCore = getAnimationProcessor().getBone("hydra_heart_core");
        GeoBone backSpines = getAnimationProcessor().getBone("hydra_back_spines");
        GeoBone neckL1 = getAnimationProcessor().getBone("hydra_neck_l1");
        GeoBone neckL2 = getAnimationProcessor().getBone("hydra_neck_l2");
        GeoBone headL = getAnimationProcessor().getBone("hydra_head_l");
        GeoBone neckR1 = getAnimationProcessor().getBone("hydra_neck_r1");
        GeoBone neckR2 = getAnimationProcessor().getBone("hydra_neck_r2");
        GeoBone headR = getAnimationProcessor().getBone("hydra_head_r");
        GeoBone neckC1 = getAnimationProcessor().getBone("hydra_neck_c1");
        GeoBone neckC2 = getAnimationProcessor().getBone("hydra_neck_c2");
        GeoBone headC = getAnimationProcessor().getBone("hydra_head_c");
        GeoBone tail1 = getAnimationProcessor().getBone("tail_1");
        GeoBone tail2 = getAnimationProcessor().getBone("tail_2");
        GeoBone tail3 = getAnimationProcessor().getBone("tail_3");
        GeoBone tail4 = getAnimationProcessor().getBone("tail_4");
        GeoBone tail5 = getAnimationProcessor().getBone("tail_5");
        GeoBone tail6 = getAnimationProcessor().getBone("tail_6");

        if (crestL == null && heartCore == null && backSpines == null && neckL1 == null && neckC1 == null && tail1 == null) {
            return;
        }

        Entity entity = animationState.getData(DataTickets.ENTITY);
        float partialTick = animationState.getPartialTick();

        float move = 0.0F;
        float air = 0.0F;
        float crouch = 0.0F;
        float yawLag = 0.0F;
        float hydraPhase = 0.0F;
        float tailPhase = 0.0F;
        float heartPulse = 0.0F;
        float heartAmp = 1.0F;

        if (entity instanceof LivingEntity living) {
            EntityAnimState st = this.entityStates.computeIfAbsent(living, e -> new EntityAnimState());
            double nowSec = (living.tickCount + partialTick) / 20.0;
            double dt;
            if (Double.isNaN(st.lastTimeSec)) {
                dt = 0.0;
                st.lastTimeSec = nowSec;
            } else {
                dt = nowSec - st.lastTimeSec;
                if (dt < -0.5 || dt > 1.0) {
                    dt = 0.0;
                    st.lastTimeSec = nowSec;
                } else if (dt > 0.0) {
                    dt = Math.min(dt, 0.08);
                    st.lastTimeSec = nowSec;
                } else {
                    dt = 0.0;
                }
            }

            float rawWalk = Mth.clamp(living.walkAnimation.speed(partialTick), 0.0F, 1.0F);
            float targetMove = Mth.clamp(rawWalk * (living.isSprinting() ? 1.0F : 0.78F), 0.0F, 1.0F);
            float targetAir = living.onGround() ? 0.0F : 1.0F;
            float targetCrouch = living.isCrouching() ? 1.0F : 0.0F;
            float bodyYawDelta = Mth.wrapDegrees(living.yBodyRot - living.yBodyRotO);
            float targetYawLag = Mth.clamp(bodyYawDelta * 0.0020F, -0.045F, 0.045F);
            float maxHp = Math.max(1.0F, living.getMaxHealth());
            float lowHpStress = Mth.clamp((0.55F - (living.getHealth() / maxHp)) / 0.45F, 0.0F, 1.0F);

            if (dt > 0.0) {
                float alphaMove = 1.0F - (float) Math.exp(-dt / 0.22);
                float alphaAir = 1.0F - (float) Math.exp(-dt / 0.14);
                float alphaYaw = 1.0F - (float) Math.exp(-dt / 0.18);
                st.smoothMove = Mth.lerp(alphaMove, st.smoothMove, targetMove);
                st.smoothAir = Mth.lerp(alphaAir, st.smoothAir, targetAir);
                st.smoothCrouch = Mth.lerp(alphaMove, st.smoothCrouch, targetCrouch);
                st.smoothYawLag = Mth.lerp(alphaYaw, st.smoothYawLag, targetYawLag);

                float hydraGroundOmega = Mth.lerp(st.smoothMove, TWO_PI / 3.4F, TWO_PI / 1.9F);
                float hydraOmega = Mth.lerp(st.smoothAir, hydraGroundOmega, TWO_PI / 1.35F);
                st.hydraPhase = (st.hydraPhase + dt * hydraOmega) % (TWO_PI * 1024.0);

                float tailGroundOmega = Mth.lerp(st.smoothMove, TWO_PI / 2.6F, TWO_PI / 1.30F);
                float tailOmega = Mth.lerp(st.smoothAir, tailGroundOmega, TWO_PI / 1.15F);
                st.tailPhase = (st.tailPhase + dt * tailOmega) % (TWO_PI * 1024.0);

                // Tần số nhịp tim thực (đơn vị chu kỳ/giây): đứng yên ~0.90 Hz (~54 BPM), chạy/bay ~1.30 Hz, thấp máu ~1.65 Hz (~100 BPM)!
                float heartBps = Mth.lerp(Math.max(st.smoothMove, st.smoothAir * 0.85F), 0.90F, 1.32F) + lowHpStress * 0.35F;
                st.heartPhase = (st.heartPhase + dt * heartBps) % 1.0;

                // Bộ máy hành vi chơi đùa & phun lửa xanh vô hại cho 3 đầu Hồn Xà (mượt tuyệt đối, luân phiên sinh động!)
                updateHydraPlayState(st, living, (float) dt);
            }

            move = st.smoothMove;
            air = st.smoothAir;
            crouch = st.smoothCrouch;
            yawLag = st.smoothYawLag;
            hydraPhase = (float) st.hydraPhase;
            tailPhase = (float) st.tailPhase;
            heartPulse = computeCardiacPulse((float) st.heartPhase);
            heartAmp = 1.0F + lowHpStress * 0.22F;
            st.lastHeartPulse = heartPulse;
        }

        // Animation NHỊP TIM ĐẬP KÉP (`lub-dub`) trên lõi tinh thể ngực (`hydra_heart_core`) & nhịp phồng nhẹ gai sau lưng (`hydra_back_spines`)!
        if (heartCore != null) {
            float p = heartPulse * heartAmp;
            heartCore.setScaleX(1.0F + p * 0.24F);
            heartCore.setScaleY(1.0F + p * 0.14F);
            heartCore.setScaleZ(1.0F + p * 0.55F);
        }
        if (backSpines != null) {
            float sp = heartPulse * heartAmp;
            backSpines.setScaleX(1.0F + sp * 0.05F);
            backSpines.setScaleY(1.0F + sp * 0.03F);
            backSpines.setScaleZ(1.0F + sp * 0.11F);
        }

        // Đôi mang quạt 5 nan 2 bên mũ (`hydra_crest_l/r`) phập phồng đối xứng nhẹ quanh khớp gò má giống Boss Spirit Hydra!
        float crestFlareRad = (Mth.sin(hydraPhase) * Mth.lerp(move, 2.2F, 3.6F) + air * 2.8F) * DEG_TO_RAD;
        applyMirroredBone(crestL, crestR, 0.0F, crestFlareRad, 0.0F);

        float s1 = Mth.sin(hydraPhase);
        float s2 = Mth.sin(hydraPhase - NECK_SEG2_LAG);
        float s3 = Mth.sin(hydraPhase - NECK_HEAD_LAG);
        float c1 = Mth.cos(hydraPhase);
        float c2 = Mth.cos(hydraPhase - NECK_SEG2_LAG);

        // Chuyển động nền tự nhiên của 3 đầu (góc độ tính theo độ trước khi cộng lớp animation chơi đùa / phun lửa xanh!)
        float l1Pitch = -crouch * 3.0F + s1 * Mth.lerp(move, 1.8F, 3.0F);
        float l1Yaw   = Mth.lerp(move, 2.0F, 5.5F) - air * 4.5F + s1 * Mth.lerp(move, 2.8F, 5.0F);
        float l1Roll  = -(air * 3.5F + c1 * Mth.lerp(move, 1.8F, 3.2F));
        float l2Pitch = s2 * Mth.lerp(move, 2.0F, 3.4F);
        float l2Yaw   = Mth.lerp(move, 1.0F, 3.0F) - air * 2.5F + s2 * Mth.lerp(move, 3.2F, 5.5F);
        float l2Roll  = -(air * 2.0F + c2 * Mth.lerp(move, 2.0F, 3.5F));
        float lhPitch = s3 * Mth.lerp(move, 2.8F, 4.5F);
        float lhYaw   = s3 * Mth.lerp(move, 3.8F, 6.2F);
        float lhRoll  = 0.0F;

        float r1Pitch = l1Pitch;
        float r1Yaw   = -l1Yaw;
        float r1Roll  = -l1Roll;
        float r2Pitch = l2Pitch;
        float r2Yaw   = -l2Yaw;
        float r2Roll  = -l2Roll;
        float rhPitch = lhPitch;
        float rhYaw   = -lhYaw;
        float rhRoll  = 0.0F;

        float centerBreath1 = Mth.sin(hydraPhase + 1.05F);
        float centerBreath2 = Mth.sin(hydraPhase + 1.05F - NECK_SEG2_LAG);
        float centerBreathH = Mth.sin(hydraPhase + 1.05F - NECK_HEAD_LAG);
        float centerSway = Mth.cos(hydraPhase * 0.5F);

        float c1Pitch = -move * 2.8F - air * 3.0F - crouch * 2.5F + centerBreath1 * 2.2F;
        float c1Yaw   = centerSway * 2.4F + (yawLag * 0.35F) / DEG_TO_RAD;
        float c1Roll  = 0.0F;
        float c2Pitch = centerBreath2 * 2.8F + move * 1.4F;
        float c2Yaw   = centerSway * 2.6F + (yawLag * 0.45F) / DEG_TO_RAD;
        float c2Roll  = 0.0F;
        float chPitch = centerBreathH * 3.8F;
        float chYaw   = centerSway * 3.5F + (yawLag * 0.55F) / DEG_TO_RAD;
        float chRoll  = 0.0F;

        // Chuyển động ĐUÔI 6 ĐỐT ĐUNG ĐƯA TỰ NHIÊN (sóng số 8 sống động + quán tính quay người + uốn lượn chơi đùa với 3 đầu!)
        float[] tailPitch = new float[6];
        float[] tailYaw = new float[6];
        float[] tailRoll = new float[6];

        // Trọng số pha trộn tư thế đuôi chơi đùa cùng 3 đầu (`0` = đung đưa tự nhiên, `1` = tương tác trực tiếp với 3 đầu!)
        float tailPlayWeight = 0.0F;
        float tailExciteMul = 1.0F;
        float tailBraceLift = 0.0F;
        float[] tailPlayPitch = new float[6];
        float[] tailPlayYaw = new float[6];
        float[] tailPlayRoll = new float[6];

        EntityAnimState st = entity instanceof LivingEntity ? this.entityStates.get(entity) : null;
        if (st != null) {
            HeadBreathSample sample = st.breathSample;
            for (int i = 0; i < 3; i++) {
                sample.breathStrength[i] = 0.0F;
                sample.playSparkStrength[i] = 0.0F;
            }
            sample.tailPlaySpark = 0.0F;
            sample.maxBreath = 0.0F;

            // Giảm nhẹ biên độ đùa khi đang chạy nước rút hoặc nhảy để dáng luôn gọn gàng, bung toàn lực khi đứng/đi!
            float calmWeight = 1.0F - Mth.clamp(move * 0.55F + air * 0.70F, 0.0F, 0.75F);
            if (st.activeEmote != HydraEmote.IDLE && st.emoteDuration > 0.05F) {
                float u = Mth.clamp(st.emoteElapsed / st.emoteDuration, 0.0F, 1.0F);
                // Đường bao vào-ra cực mượt (`smoothstep` ở 18% đầu và 20% cuối) -> 0 giật khung!
                float envIn = smoothStep(0.0F, 0.18F, u);
                float envOut = 1.0F - smoothStep(0.80F, 1.0F, u);
                float env = envIn * envOut * calmWeight;
                float bob2 = Mth.sin(u * TWO_PI * 2.5F);
                float bob3 = Mth.sin(u * TWO_PI * 3.0F);

                switch (st.activeEmote) {
                    case PLAY_NUDGE_L_C -> {
                        // Đầu Trái (L) vươn lên uốn vào trong cọ mũi đùa với Đầu Giữa (C); Đầu Phải (R) nghiêng đầu hóng!
                        float nudge = Mth.sin(u * TWO_PI * 2.0F);
                        l1Pitch += (+6.0F + nudge * 5.0F) * env;
                        l1Yaw   += (+20.0F + bob2 * 4.5F) * env;
                        l1Roll  += (+13.0F) * env;
                        l2Pitch += (+4.0F - nudge * 5.0F) * env;
                        l2Yaw   += (+22.0F + nudge * 6.0F) * env;
                        l2Roll  += (+9.0F) * env;
                        lhPitch += (nudge * 15.0F) * env;
                        lhYaw   += (+26.0F + bob2 * 8.0F) * env;
                        lhRoll  += (+12.0F * bob2) * env;

                        c1Pitch += (-8.5F - nudge * 4.5F) * env;
                        c1Yaw   += (-18.0F - bob2 * 4.0F) * env;
                        c1Roll  += (-11.0F) * env;
                        c2Pitch += (-6.0F + nudge * 5.0F) * env;
                        c2Yaw   += (-18.0F) * env;
                        chPitch += (-nudge * 15.0F - 6.0F) * env;
                        chYaw   += (-26.0F - bob2 * 7.0F) * env;
                        chRoll  += (-14.0F * bob2) * env;

                        // Đầu Phải (R) quay nhẹ vào & nghiêng đầu tò mò nhìn 2 anh em đùa; đuôi vẫy vui theo nhịp!
                        r1Yaw   += (-10.0F) * env;
                        rhYaw   += (-18.0F) * env;
                        rhRoll  += (+18.0F) * env;
                        rhPitch += (+6.0F) * env;
                        tailExciteMul = 1.0F + 0.45F * env;

                        // Bật tia lửa xanh lách tách vui nhộn khi 2 mũi rồng cọ vào nhau!
                        float spark = Math.max(0.0F, Math.abs(nudge) - 0.55F) * 2.2F * env;
                        sample.playSparkStrength[HEAD_LEFT] = spark;
                        sample.playSparkStrength[HEAD_CENTER] = spark;
                    }
                    case PLAY_NUDGE_R_C -> {
                        // Gương đối xứng: Đầu Phải (R) & Đầu Giữa (C) cọ mũi chơi đùa; Đầu Trái (L) nghiêng đầu tò mò xem!
                        float nudge = Mth.sin(u * TWO_PI * 2.0F);
                        r1Pitch += (+6.0F + nudge * 5.0F) * env;
                        r1Yaw   += (-20.0F - bob2 * 4.5F) * env;
                        r1Roll  += (-13.0F) * env;
                        r2Pitch += (+4.0F - nudge * 5.0F) * env;
                        r2Yaw   += (-22.0F - nudge * 6.0F) * env;
                        r2Roll  += (-9.0F) * env;
                        rhPitch += (nudge * 15.0F) * env;
                        rhYaw   += (-26.0F - bob2 * 8.0F) * env;
                        rhRoll  += (-12.0F * bob2) * env;

                        c1Pitch += (-8.5F - nudge * 4.5F) * env;
                        c1Yaw   += (+18.0F + bob2 * 4.0F) * env;
                        c1Roll  += (+11.0F) * env;
                        c2Pitch += (-6.0F + nudge * 5.0F) * env;
                        c2Yaw   += (+18.0F) * env;
                        chPitch += (-nudge * 15.0F - 6.0F) * env;
                        chYaw   += (+26.0F + bob2 * 7.0F) * env;
                        chRoll  += (+14.0F * bob2) * env;

                        l1Yaw   += (+10.0F) * env;
                        lhYaw   += (+18.0F) * env;
                        lhRoll  += (-18.0F) * env;
                        lhPitch += (+6.0F) * env;
                        tailExciteMul = 1.0F + 0.45F * env;

                        float spark = Math.max(0.0F, Math.abs(nudge) - 0.55F) * 2.2F * env;
                        sample.playSparkStrength[HEAD_RIGHT] = spark;
                        sample.playSparkStrength[HEAD_CENTER] = spark;
                    }
                    case PLAY_BANTER_L_R -> {
                        // Đầu Trái & Đầu Phải quay mặt vào nhau "cãi nhau / đớp đùa" lệch pha, phì nhẹ tia lửa xanh; Đầu Giữa ngó trái-phải can ngăn!
                        l1Yaw   += (+24.0F) * env;
                        l2Yaw   += (+20.0F + bob3 * 6.0F) * env;
                        lhYaw   += (+28.0F) * env;
                        lhPitch += (bob3 * 16.0F) * env;
                        lhRoll  += (+10.0F * bob3) * env;

                        r1Yaw   += (-24.0F) * env;
                        r2Yaw   += (-20.0F - bob3 * 6.0F) * env;
                        rhYaw   += (-28.0F) * env;
                        rhPitch += (-bob3 * 16.0F) * env;
                        rhRoll  += (-10.0F * bob3) * env;

                        // Đầu Giữa quay trái -> phải -> trái như trọng tài theo dõi; đuôi vẫy nhanh hứng thú!
                        float lookLR = Mth.sin(u * TWO_PI * 1.5F);
                        c1Yaw   += (lookLR * 20.0F) * env;
                        c2Yaw   += (lookLR * 18.0F) * env;
                        chYaw   += (lookLR * 28.0F) * env;
                        c1Pitch += (-7.0F) * env;
                        chPitch += (-10.0F - Math.abs(lookLR) * 5.0F) * env;
                        tailExciteMul = 1.0F + 0.55F * env;

                        // Phì nhẹ tia lửa xanh tinh nghịch mỗi khi đầu L hoặc R gật mỏ "hù" nhau!
                        sample.playSparkStrength[HEAD_LEFT]  = Math.max(0.0F, bob3 - 0.45F) * 1.8F * env;
                        sample.playSparkStrength[HEAD_RIGHT] = Math.max(0.0F, -bob3 - 0.45F) * 1.8F * env;
                    }
                    case PLAY_TAIL_TEASE_R -> {
                        // ĐUÔI UỐN CAO SANG BÊN PHẢI VẪY CHÓP SÁNG TRÊU ĐẦU PHẢI (R) — Đầu R quay ngoắt lại rượt đớp đùa, C & L hóng xem!
                        float flick = Mth.sin(u * TWO_PI * 3.5F);
                        float nip = Mth.sin(u * TWO_PI * 3.5F - 0.85F);
                        tailPlayWeight = env;
                        for (int i = 0; i < 6; i++) {
                            float tipMul = (i >= 3) ? (i - 2) * 0.42F : 0.15F;
                            tailPlayPitch[i] = TAIL_POSE_TEASE_R[i][0] + flick * 11.0F * tipMul;
                            tailPlayYaw[i]   = TAIL_POSE_TEASE_R[i][1] - flick * 13.0F * tipMul;
                            tailPlayRoll[i]  = TAIL_POSE_TEASE_R[i][2] + Mth.cos(u * TWO_PI * 3.5F) * 6.0F * tipMul;
                        }

                        // Đầu Phải (R) quay ngoắt ra sau (+yaw) gật mỏ đùa đuổi theo chóp đuôi phát sáng!
                        r1Yaw   += (+24.0F + nip * 6.0F) * env;
                        r1Pitch += (-5.0F + nip * 4.5F) * env;
                        r2Yaw   += (+22.0F + nip * 8.0F) * env;
                        r2Pitch += (-6.0F - nip * 4.0F) * env;
                        rhYaw   += (+30.0F + nip * 10.0F) * env;
                        rhPitch += (-10.0F + nip * 16.0F) * env;
                        rhRoll  += (+14.0F * nip) * env;

                        // Đầu Giữa (C) & Đầu Trái (L) nghiêng sang phải nhìn R vờn đuôi!
                        c1Yaw   += (+18.0F) * env;
                        c2Yaw   += (+16.0F) * env;
                        chYaw   += (+26.0F) * env;
                        chPitch += (-12.0F) * env;
                        chRoll  += (+12.0F) * env;

                        l1Yaw   += (+12.0F) * env;
                        lhYaw   += (+20.0F) * env;
                        lhRoll  += (-14.0F) * env;

                        sample.playSparkStrength[HEAD_RIGHT] = Math.max(0.0F, Math.abs(nip) - 0.45F) * 1.9F * env;
                        sample.tailPlaySpark = (0.55F + 0.45F * Math.abs(flick)) * env;
                    }
                    case PLAY_TAIL_TEASE_L -> {
                        // ĐUÔI QUÉT VÒNG MƯỢT QUA LƯNG SANG BÊN TRÁI TRÊU ĐẦU TRÁI (L) — Đầu L quay lại rượt vờn chóp đuôi phát sáng!
                        float flick = Mth.sin(u * TWO_PI * 3.5F);
                        float nip = Mth.sin(u * TWO_PI * 3.5F - 0.85F);
                        tailPlayWeight = env;
                        for (int i = 0; i < 6; i++) {
                            float tipMul = (i >= 3) ? (i - 2) * 0.42F : 0.15F;
                            tailPlayPitch[i] = TAIL_POSE_TEASE_L[i][0] + flick * 11.0F * tipMul;
                            tailPlayYaw[i]   = TAIL_POSE_TEASE_L[i][1] + flick * 13.0F * tipMul;
                            tailPlayRoll[i]  = TAIL_POSE_TEASE_L[i][2] - Mth.cos(u * TWO_PI * 3.5F) * 6.0F * tipMul;
                        }

                        // Đầu Trái (L) quay ra sau (-yaw) vờn đớp theo chóp đuôi phát sáng!
                        l1Yaw   += (-24.0F - nip * 6.0F) * env;
                        l1Pitch += (-5.0F + nip * 4.5F) * env;
                        l2Yaw   += (-22.0F - nip * 8.0F) * env;
                        l2Pitch += (-6.0F - nip * 4.0F) * env;
                        lhYaw   += (-30.0F - nip * 10.0F) * env;
                        lhPitch += (-10.0F + nip * 16.0F) * env;
                        lhRoll  += (-14.0F * nip) * env;

                        // Đầu Giữa (C) & Đầu Phải (R) nghiêng sang trái nhìn L vờn đuôi!
                        c1Yaw   += (-18.0F) * env;
                        c2Yaw   += (-16.0F) * env;
                        chYaw   += (-26.0F) * env;
                        chPitch += (-12.0F) * env;
                        chRoll  += (-12.0F) * env;

                        r1Yaw   += (-12.0F) * env;
                        rhYaw   += (-20.0F) * env;
                        rhRoll  += (+14.0F) * env;

                        sample.playSparkStrength[HEAD_LEFT] = Math.max(0.0F, Math.abs(nip) - 0.45F) * 1.9F * env;
                        sample.tailPlaySpark = (0.55F + 0.45F * Math.abs(flick)) * env;
                    }
                    case PLAY_TAIL_TRIO_CHASE -> {
                        // MÀN ĐẠI TIỆC: Đuôi vút vòng cung cao PHẢI -> GIỮA ĐỈNH -> TRÁI trêu lần lượt CẢ 3 ĐẦU rượt đuổi chóp đuôi!
                        float wR = (1.0F - smoothStep(0.28F, 0.46F, u));
                        float wL = smoothStep(0.54F, 0.72F, u);
                        float wC = Math.max(0.0F, 1.0F - wR - wL);
                        float flick = Mth.sin(u * TWO_PI * 5.0F);
                        float nip = Mth.sin(u * TWO_PI * 5.0F - 0.75F);
                        tailPlayWeight = env;

                        for (int i = 0; i < 6; i++) {
                            float baseP = TAIL_POSE_TEASE_R[i][0] * wR + TAIL_POSE_TEASE_C[i][0] * wC + TAIL_POSE_TEASE_L[i][0] * wL;
                            float baseY = TAIL_POSE_TEASE_R[i][1] * wR + TAIL_POSE_TEASE_C[i][1] * wC + TAIL_POSE_TEASE_L[i][1] * wL;
                            float baseR = TAIL_POSE_TEASE_R[i][2] * wR + TAIL_POSE_TEASE_C[i][2] * wC + TAIL_POSE_TEASE_L[i][2] * wL;
                            float tipMul = (i >= 3) ? (i - 2) * 0.40F : 0.14F;
                            tailPlayPitch[i] = baseP + flick * 10.0F * tipMul;
                            tailPlayYaw[i]   = baseY + flick * 12.0F * tipMul;
                            tailPlayRoll[i]  = baseR + Mth.cos(u * TWO_PI * 5.0F) * 6.0F * tipMul;
                        }

                        // 1) Giai đoạn Phải (`wR`): Đầu Phải quay lại rượt đớp chóp đuôi; 2 đầu kia nhìn theo!
                        r1Yaw   += (+24.0F * wR - 14.0F * wC - 10.0F * wL) * env;
                        r2Yaw   += (+20.0F * wR) * env;
                        rhYaw   += (+28.0F * wR - 18.0F * wC - 16.0F * wL) * env;
                        rhPitch += ((-8.0F + nip * 15.0F) * wR + 14.0F * wC) * env;
                        rhRoll  += (+14.0F * nip * wR) * env;

                        // 2) Giai đoạn Giữa (`wC`): Đầu Giữa ngửa cổ ra sau vờn chóp đuôi đang vẫy sau vương miện!
                        c1Pitch += (+14.0F * wC - 6.0F * (wR + wL)) * env;
                        c2Pitch += (+12.0F * wC) * env;
                        c1Yaw   += (+18.0F * wR - 18.0F * wL) * env;
                        chYaw   += (+24.0F * wR - 24.0F * wL + nip * 14.0F * wC) * env;
                        chPitch += ((+22.0F + nip * 14.0F) * wC - 10.0F * (wR + wL)) * env;
                        chRoll  += (nip * 16.0F * wC) * env;

                        // 3) Giai đoạn Trái (`wL`): Đầu Trái quay lại rượt đớp chóp đuôi!
                        l1Yaw   += (-24.0F * wL + 14.0F * wC + 10.0F * wR) * env;
                        l2Yaw   += (-20.0F * wL) * env;
                        lhYaw   += (-28.0F * wL + 18.0F * wC + 16.0F * wR) * env;
                        lhPitch += ((-8.0F + nip * 15.0F) * wL + 14.0F * wC) * env;
                        lhRoll  += (-14.0F * nip * wL) * env;

                        float snap = Math.max(0.0F, Math.abs(nip) - 0.40F) * 1.9F * env;
                        sample.playSparkStrength[HEAD_RIGHT]  = snap * wR;
                        sample.playSparkStrength[HEAD_CENTER] = snap * wC;
                        sample.playSparkStrength[HEAD_LEFT]   = snap * wL;
                        sample.tailPlaySpark = (0.70F + 0.30F * Math.abs(flick)) * env;
                    }
                    case BREATHE_SOLO -> {
                        // 1 đầu lấy đà rướn cổ ra sau (+rotX) rồi lao tới (-rotX) quét quạt phun luồng LỬA LINH HỒN XANH vô hại!
                        float windup = smoothStep(0.0F, 0.22F, u) * (1.0F - smoothStep(0.22F, 0.34F, u));
                        float fireEnv = smoothStep(0.24F, 0.36F, u) * (1.0F - smoothStep(0.76F, 0.90F, u));
                        float sweep = Mth.sin(((u - 0.24F) / 0.66F) * Mth.PI * 2.0F);
                        float w = calmWeight;
                        tailBraceLift = fireEnv * 8.0F * w;

                        if (st.soloBreathHead == HEAD_LEFT) {
                            l1Pitch += (+14.0F * windup - 10.0F * fireEnv) * w;
                            l1Yaw   += (-10.0F * windup + sweep * 14.0F * fireEnv) * w;
                            l2Pitch += (+10.0F * windup - 8.0F * fireEnv) * w;
                            lhPitch += (+14.0F * windup - 6.0F * fireEnv) * w;
                            lhYaw   += (sweep * 22.0F * fireEnv) * w;
                            // 2 đầu còn lại quay nhìn sang đầu Trái phun lửa!
                            c1Yaw   += (-14.0F * fireEnv) * w;
                            chYaw   += (-22.0F * fireEnv) * w;
                            rhYaw   += (-12.0F * fireEnv) * w;
                            sample.breathStrength[HEAD_LEFT] = fireEnv * w;
                        } else if (st.soloBreathHead == HEAD_RIGHT) {
                            r1Pitch += (+14.0F * windup - 10.0F * fireEnv) * w;
                            r1Yaw   += (+10.0F * windup - sweep * 14.0F * fireEnv) * w;
                            r2Pitch += (+10.0F * windup - 8.0F * fireEnv) * w;
                            rhPitch += (+14.0F * windup - 6.0F * fireEnv) * w;
                            rhYaw   += (-sweep * 22.0F * fireEnv) * w;
                            c1Yaw   += (+14.0F * fireEnv) * w;
                            chYaw   += (+22.0F * fireEnv) * w;
                            lhYaw   += (+12.0F * fireEnv) * w;
                            sample.breathStrength[HEAD_RIGHT] = fireEnv * w;
                        } else {
                            c1Pitch += (+16.0F * windup - 9.0F * fireEnv) * w;
                            c2Pitch += (+12.0F * windup - 7.0F * fireEnv) * w;
                            chPitch += (+14.0F * windup - 4.0F * fireEnv) * w;
                            c1Yaw   += (sweep * 14.0F * fireEnv) * w;
                            chYaw   += (sweep * 24.0F * fireEnv) * w;
                            // 2 đầu vai ngước nhìn đầu Giữa biểu diễn!
                            lhPitch += (+14.0F * fireEnv) * w;
                            rhPitch += (+14.0F * fireEnv) * w;
                            sample.breathStrength[HEAD_CENTER] = fireEnv * w;
                        }
                    }
                    case BREATHE_TRIO -> {
                        // CẢ 3 ĐẦU đồng loạt ưỡn cổ lấy hơi (+rotX) rồi phun quạt LỬA XANH LINH HỒN vô hại rực rỡ!
                        float windup = smoothStep(0.0F, 0.24F, u) * (1.0F - smoothStep(0.24F, 0.35F, u));
                        float fireEnv = smoothStep(0.26F, 0.38F, u) * (1.0F - smoothStep(0.76F, 0.90F, u));
                        float sweep = Mth.sin(((u - 0.26F) / 0.64F) * Mth.PI * 2.0F);
                        float w = calmWeight;
                        tailBraceLift = fireEnv * 12.0F * w;

                        l1Pitch += (+14.0F * windup - 8.0F * fireEnv) * w;
                        l2Pitch += (+10.0F * windup - 6.0F * fireEnv) * w;
                        lhPitch += (+14.0F * windup - 4.0F * fireEnv) * w;
                        lhYaw   += (-10.0F * fireEnv + sweep * 18.0F * fireEnv) * w;

                        r1Pitch += (+14.0F * windup - 8.0F * fireEnv) * w;
                        r2Pitch += (+10.0F * windup - 6.0F * fireEnv) * w;
                        rhPitch += (+14.0F * windup - 4.0F * fireEnv) * w;
                        rhYaw   += (+10.0F * fireEnv - sweep * 18.0F * fireEnv) * w;

                        c1Pitch += (+16.0F * windup - 5.0F * fireEnv) * w;
                        c2Pitch += (+12.0F * windup - 4.0F * fireEnv) * w;
                        chPitch += (+14.0F * windup + 2.0F * fireEnv) * w;
                        chYaw   += (sweep * 20.0F * fireEnv) * w;

                        sample.breathStrength[HEAD_LEFT]   = fireEnv * w;
                        sample.breathStrength[HEAD_RIGHT]  = fireEnv * w;
                        sample.breathStrength[HEAD_CENTER] = fireEnv * w;
                    }
                    case IDLE -> {}
                }

                sample.maxBreath = Math.max(
                        Math.max(sample.breathStrength[0], sample.breathStrength[1]),
                        Math.max(sample.breathStrength[2], Math.max(sample.tailPlaySpark,
                                Math.max(sample.playSparkStrength[0],
                                        Math.max(sample.playSparkStrength[1], sample.playSparkStrength[2])))));
            }

            // Áp góc xoay cuối cùng lên 9 bone cổ/đầu và tính FK chính xác vị trí + hướng 3 miệng rồng!
            setBoneDeg(neckL1, l1Pitch, l1Yaw, l1Roll);
            setBoneDeg(neckL2, l2Pitch, l2Yaw, l2Roll);
            setBoneDeg(headL,  lhPitch, lhYaw, lhRoll);

            setBoneDeg(neckR1, r1Pitch, r1Yaw, r1Roll);
            setBoneDeg(neckR2, r2Pitch, r2Yaw, r2Roll);
            setBoneDeg(headR,  rhPitch, rhYaw, rhRoll);

            setBoneDeg(neckC1, c1Pitch, c1Yaw, c1Roll);
            setBoneDeg(neckC2, c2Pitch, c2Yaw, c2Roll);
            setBoneDeg(headC,  chPitch, chYaw, chRoll);

            if (sample.maxBreath > 0.02F) {
                computeChainMuzzleFK(PIV_L1, l1Pitch, l1Yaw, l1Roll, PIV_L2, l2Pitch, l2Yaw, l2Roll,
                        PIV_LH, lhPitch, lhYaw, lhRoll, MUZZLE_L_REST, DIR_L_REST,
                        sample.muzzleModelPx[HEAD_LEFT], sample.dirModel[HEAD_LEFT]);
                computeChainMuzzleFK(PIV_R1, r1Pitch, r1Yaw, r1Roll, PIV_R2, r2Pitch, r2Yaw, r2Roll,
                        PIV_RH, rhPitch, rhYaw, rhRoll, MUZZLE_R_REST, DIR_R_REST,
                        sample.muzzleModelPx[HEAD_RIGHT], sample.dirModel[HEAD_RIGHT]);
                computeChainMuzzleFK(PIV_C1, c1Pitch, c1Yaw, c1Roll, PIV_C2, c2Pitch, c2Yaw, c2Roll,
                        PIV_CH, chPitch, chYaw, chRoll, MUZZLE_C_REST, DIR_C_REST,
                        sample.muzzleModelPx[HEAD_CENTER], sample.dirModel[HEAD_CENTER]);
            }
        } else {
            setBoneDeg(neckL1, l1Pitch, l1Yaw, l1Roll);
            setBoneDeg(neckL2, l2Pitch, l2Yaw, l2Roll);
            setBoneDeg(headL,  lhPitch, lhYaw, lhRoll);
            setBoneDeg(neckR1, r1Pitch, r1Yaw, r1Roll);
            setBoneDeg(neckR2, r2Pitch, r2Yaw, r2Roll);
            setBoneDeg(headR,  rhPitch, rhYaw, rhRoll);
            setBoneDeg(neckC1, c1Pitch, c1Yaw, c1Roll);
            setBoneDeg(neckC2, c2Pitch, c2Yaw, c2Roll);
            setBoneDeg(headC,  chPitch, chYaw, chRoll);
        }

        // ==================== ĐUÔI 6 ĐỐT ĐUNG ĐƯA HÌNH SỐ 8 SỐNG ĐỘNG + CHƠI ĐÙA CÙNG 3 ĐẦU HYDRA ====================
        // Gốc đuôi lệch nhẹ về tâm (`+12°` base center sway) rồi vung sóng truyền pha mượt từ đốt 1 -> đốt 6:
        // chóp đuôi vẽ đường số 8 uyển chuyển rộng ~15 px sau lưng (đứng yên nhịp nhàng, chạy vung mạnh, nhảy vút cao)!
        float[] yawBaseCenter = { +11.0F, +2.0F, 0.0F, 0.0F, 0.0F, 0.0F };
        float[] yawAmpsIdle   = { 21.0F, 10.5F,  9.0F,  8.5F,  9.0F,  9.5F };
        float[] yawAmpsMove   = { 26.0F, 13.5F, 12.5F, 12.0F, 13.0F, 13.5F };
        float[] pitchAmps     = {  3.2F,  2.8F,  2.6F,  3.2F,  4.0F,  4.8F };
        float[] rollAmps      = {  3.0F,  2.4F,  2.2F,  2.0F,  2.4F,  3.0F };
        float[] lagWeights    = { 0.45F, 0.60F, 0.72F, 0.80F, 0.75F, 0.60F };
        float[] liftWeights   = { 0.45F, 0.34F, 0.22F, 0.05F, -0.06F, -0.08F };

        float tailLiftDeg = 4.0F + move * 7.5F + air * 12.5F + crouch * 6.0F + tailBraceLift;
        float yawLagDeg = (yawLag / DEG_TO_RAD) * 2.4F;

        for (int i = 0; i < 6; i++) {
            float segPhase = tailPhase - TAIL_PHASE_LAG * i;
            float wave1 = Mth.sin(segPhase);
            float wave2 = Mth.sin(segPhase * 2.0F + 0.65F);
            float fig8Pitch = Mth.cos(segPhase * 2.0F - 0.35F);

            float swayAmpY = Mth.lerp(move, yawAmpsIdle[i], yawAmpsMove[i]) * tailExciteMul;
            float tipFlick = (i >= 4) ? (wave2 * (i == 5 ? 5.2F : 3.2F) * tailExciteMul) : 0.0F;

            float natYaw = yawBaseCenter[i] + wave1 * swayAmpY + tipFlick + yawLagDeg * lagWeights[i];
            float natPitch = -(tailLiftDeg * liftWeights[i]) + fig8Pitch * pitchAmps[i] * (1.0F + move * 0.35F);
            float natRoll = -Mth.cos(segPhase) * rollAmps[i] * (1.0F + move * 0.30F);

            // Pha trộn cực mượt giữa sóng đung đưa tự nhiên và tư thế đuôi uốn lên chơi đùa với 3 đầu!
            tailPitch[i] = Mth.lerp(tailPlayWeight, natPitch, tailPlayPitch[i]);
            tailYaw[i]   = Mth.lerp(tailPlayWeight, natYaw,   tailPlayYaw[i]);
            tailRoll[i]  = Mth.lerp(tailPlayWeight, natRoll,  tailPlayRoll[i]);
        }

        setBoneDeg(tail1, tailPitch[0], tailYaw[0], tailRoll[0]);
        setBoneDeg(tail2, tailPitch[1], tailYaw[1], tailRoll[1]);
        setBoneDeg(tail3, tailPitch[2], tailYaw[2], tailRoll[2]);
        setBoneDeg(tail4, tailPitch[3], tailYaw[3], tailRoll[3]);
        setBoneDeg(tail5, tailPitch[4], tailYaw[4], tailRoll[4]);
        setBoneDeg(tail6, tailPitch[5], tailYaw[5], tailRoll[5]);

        if (st != null && st.breathSample.tailPlaySpark > 0.02F) {
            computeTailTipFK(tailPitch, tailYaw, tailRoll, st.breathSample.tailTipModelPx);
        }
    }

    /**
     * Sóng nhịp tim thực dạng 2 đỉnh `lub-dub` (`phase01` ∈ [0, 1)):
     * - Nhịp 1 (`LUB`, 0.00..0.17): bật mạnh lên 1.00
     * - Khoảng nghỉ ngắn (0.17..0.23): hạ nhanh về 0.14
     * - Nhịp 2 (`DUB`, 0.23..0.40): dội nhẹ lên 0.68
     * - Hồi giãn (0.40..0.58) rồi nghỉ êm về 0.00 trước chu kỳ kế tiếp.
     */
    private static float computeCardiacPulse(float phase01) {
        if (phase01 < 0.17F) {
            float s = Mth.sin((phase01 / 0.17F) * Mth.PI);
            return s * s;
        }
        if (phase01 < 0.23F) {
            float t = (phase01 - 0.17F) / 0.06F;
            return Mth.lerp(t, 0.16F, 0.12F);
        }
        if (phase01 < 0.40F) {
            float s = Mth.sin(((phase01 - 0.23F) / 0.17F) * Mth.PI);
            return 0.12F + 0.56F * (s * s);
        }
        if (phase01 < 0.58F) {
            float t = (phase01 - 0.40F) / 0.18F;
            return 0.12F * (1.0F - t) * (1.0F - t);
        }
        return 0.0F;
    }

    private static void updateHydraPlayState(EntityAnimState st, LivingEntity living, float dt) {
        if (st.activeEmote != HydraEmote.IDLE) {
            st.emoteElapsed += dt;
            if (st.emoteElapsed >= st.emoteDuration) {
                st.activeEmote = HydraEmote.IDLE;
                st.emoteElapsed = 0.0F;
                // Nghỉ 1.8–3.4 giây (đuôi vẫn đung đưa số 8 mượt) rồi chọn màn chơi đùa / vờn đuôi / phun lửa kế tiếp!
                int h = mixHash(living.getId(), st.emoteCounter * 31 + 17);
                st.cooldownSec = 1.8F + ((h & 0xFF) / 255.0F) * 1.6F;
            }
            return;
        }

        st.cooldownSec -= dt;
        if (st.cooldownSec > 0.0F) {
            return;
        }

        // Luân phiên sinh động giữa: 3 đầu vờn đuôi (TEASE_R, TEASE_L, TRIO_CHASE), 3 đầu đùa nhau (L-C, R-C, L-R) và phun lửa xanh vô hại!
        int step = st.emoteCounter++;
        int pick = Math.floorMod(mixHash(living.getId(), step * 73 + 11), 9);
        switch (pick) {
            case 0 -> {
                st.activeEmote = HydraEmote.PLAY_TAIL_TRIO_CHASE;
                st.emoteDuration = 5.4F;
            }
            case 1 -> {
                st.activeEmote = HydraEmote.PLAY_NUDGE_L_C;
                st.emoteDuration = 3.8F;
            }
            case 2 -> {
                st.activeEmote = HydraEmote.PLAY_TAIL_TEASE_R;
                st.emoteDuration = 4.2F;
            }
            case 3 -> {
                st.activeEmote = HydraEmote.BREATHE_SOLO;
                st.soloBreathHead = Math.floorMod(step, 3);
                st.emoteDuration = 3.4F;
            }
            case 4 -> {
                st.activeEmote = HydraEmote.PLAY_TAIL_TEASE_L;
                st.emoteDuration = 4.4F;
            }
            case 5 -> {
                st.activeEmote = HydraEmote.PLAY_NUDGE_R_C;
                st.emoteDuration = 3.8F;
            }
            case 6 -> {
                st.activeEmote = HydraEmote.PLAY_BANTER_L_R;
                st.emoteDuration = 4.2F;
            }
            case 7 -> {
                st.activeEmote = HydraEmote.BREATHE_TRIO;
                st.emoteDuration = 4.0F;
            }
            default -> {
                st.activeEmote = HydraEmote.BREATHE_SOLO;
                st.soloBreathHead = Math.floorMod(step + 1, 3);
                st.emoteDuration = 3.4F;
            }
        }
        st.emoteElapsed = 0.0F;
    }

    private static int mixHash(int a, int b) {
        int x = a * 0x45d9f3b ^ (b + 0x9e3779b9);
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        return (x >>> 16) ^ x;
    }

    private static float smoothStep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / Math.max(1e-4F, edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static void setBoneDeg(GeoBone bone, float pitchDeg, float yawDeg, float rollDeg) {
        if (bone == null) {
            return;
        }
        bone.setRotX(pitchDeg * DEG_TO_RAD);
        bone.setRotY(yawDeg * DEG_TO_RAD);
        bone.setRotZ(rollDeg * DEG_TO_RAD);
    }

    /**
     * Động học thuận (FK) chính xác 100% theo thứ tự xoay `Z -> Y -> X` của GeckoLib cho chuỗi 3 khớp (`neck1 -> neck2 -> head`)
     * để xác định đúng tọa độ & hướng miệng rồng trong hệ tọa độ Bedrock px.
     */
    private static void computeChainMuzzleFK(
            Vector3f piv1, float p1Deg, float y1Deg, float r1Deg,
            Vector3f piv2, float p2Deg, float y2Deg, float r2Deg,
            Vector3f pivH, float pHDeg, float yHDeg, float rHDeg,
            Vector3f restMuzzle, Vector3f restDir,
            Vector3f outMuzzle, Vector3f outDir) {
        Matrix4f m = new Matrix4f().identity();
        applyBonePivotRotation(m, piv1, p1Deg, y1Deg, r1Deg);
        applyBonePivotRotation(m, piv2, p2Deg, y2Deg, r2Deg);
        applyBonePivotRotation(m, pivH, pHDeg, yHDeg, rHDeg);
        m.transformPosition(restMuzzle.x, restMuzzle.y, restMuzzle.z, outMuzzle);
        m.transformDirection(restDir.x, restDir.y, restDir.z, outDir);
        if (outDir.lengthSquared() > 1e-6F) {
            outDir.normalize();
        }
    }

    private static void applyBonePivotRotation(Matrix4f m, Vector3f piv, float pitchDeg, float yawDeg, float rollDeg) {
        m.translate(piv.x, piv.y, piv.z);
        if (Math.abs(rollDeg) > 1e-4F) {
            m.rotateZ(rollDeg * DEG_TO_RAD);
        }
        if (Math.abs(yawDeg) > 1e-4F) {
            m.rotateY(yawDeg * DEG_TO_RAD);
        }
        if (Math.abs(pitchDeg) > 1e-4F) {
            m.rotateX(pitchDeg * DEG_TO_RAD);
        }
        m.translate(-piv.x, -piv.y, -piv.z);
    }

    private static void applyMirroredBone(GeoBone left, GeoBone right, float pitchRad, float foldYawRad,
            float liftRollRad) {
        if (left != null) {
            left.setRotX(pitchRad);
            left.setRotY(foldYawRad);
            left.setRotZ(-liftRollRad);
        }
        if (right != null) {
            right.setRotX(pitchRad);
            right.setRotY(-foldYawRad);
            right.setRotZ(liftRollRad);
        }
    }

    /** Động học thuận (FK) 6 đốt cho chóp đuôi phát sáng để Renderer rải tia sáng băng vô hại khi đuôi vờn 3 đầu! */
    private static void computeTailTipFK(float[] pitchDeg, float[] yawDeg, float[] rollDeg, Vector3f outTip) {
        Matrix4f m = new Matrix4f().identity();
        for (int i = 0; i < 6; i++) {
            applyBonePivotRotation(m, TAIL_PIVS[i], pitchDeg[i], yawDeg[i], rollDeg[i]);
        }
        m.transformPosition(TAIL_TIP_REST.x, TAIL_TIP_REST.y, TAIL_TIP_REST.z, outTip);
    }
}
