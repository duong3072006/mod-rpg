package com.abyssalrpg.client.model;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.config.JormungandrConfig;
import com.abyssalrpg.entity.mythic.JormungandrBossEntity;
import com.abyssalrpg.entity.mythic.jormungandr.SerpentPath;
import com.abyssalrpg.entity.mythic.jormungandr.SerpentPath.SegmentPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * GeckoLib 4.8.3 GeoModel kiến trúc bone PHẲNG cho Boss Jörmungandr:
 * - Đặt vị trí trung điểm, góc xoay và scale TUYỆT ĐỐI từng frame cho `head`, `segment_1..54`, `tail_tip`
 *   từ {@link SerpentPath} đã nội suy Catmull-Rom trên Client.
 * - Tách riêng độ thon mặt cắt (`diameterScale` -> X/Y) với chiều dài động (`lengthScale` -> Z)
 *   để các đốt luôn lồng khít 28–40% ở mọi khúc cua, dốc địa hình và chóp đuôi Phase 2.
 * - Sinh VFX gọn gắn chính xác vào các locator bộ phận (`mouth_venom`, `hood_l_edge`, `hood_r_edge`,
 *   `tail_tip_fx`) và bụi mặt đất khi trườn nhanh, bật/tắt qua `jormungandrFancyEffects`.
 */
public class JormungandrGeoModel extends GeoModel<JormungandrBossEntity> {
    private static final ResourceLocation MODEL = AbyssalRpgMod.id("geo/entity/jormungandr.geo.json");
    private static final ResourceLocation ANIMATION = AbyssalRpgMod.id("animations/entity/jormungandr.animation.json");
    private static final ResourceLocation TEX_NORMAL = AbyssalRpgMod.id("textures/entity/jormungandr.png");
    private static final ResourceLocation TEX_ENRAGED = AbyssalRpgMod.id("textures/entity/jormungandr_enraged.png");

    // Hạt bụi màu gắn locator (nhẹ, không dùng xoáy potion trôi lơ lửng)
    private static final DustParticleOptions VENOM_DRIP_GREEN =
            new DustParticleOptions(new Vector3f(0.42f, 0.92f, 0.34f), 0.75f);
    private static final DustParticleOptions VENOM_MIST_VIOLET =
            new DustParticleOptions(new Vector3f(0.62f, 0.38f, 0.94f), 0.80f);
    private static final DustParticleOptions HOOD_MIST_P1 =
            new DustParticleOptions(new Vector3f(0.71f, 0.62f, 0.96f), 0.85f);
    private static final DustParticleOptions HOOD_MIST_P2 =
            new DustParticleOptions(new Vector3f(0.86f, 0.56f, 1.00f), 1.05f);
    private static final DustParticleOptions TAIL_SPARK_P2 =
            new DustParticleOptions(new Vector3f(0.84f, 0.52f, 1.00f), 0.72f);

    private final SegmentPose[] poseBuffer = new SegmentPose[SerpentPath.N_MAX + 2];
    private long lastFxTick = -1L;

    public JormungandrGeoModel() {
        for (int i = 0; i < poseBuffer.length; i++) {
            poseBuffer[i] = new SegmentPose();
        }
    }

    @Override
    public ResourceLocation getModelResource(JormungandrBossEntity animatable, GeoRenderer<JormungandrBossEntity> renderer) {
        return MODEL;
    }

    @Override
    @Deprecated
    public ResourceLocation getModelResource(JormungandrBossEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(JormungandrBossEntity animatable, GeoRenderer<JormungandrBossEntity> renderer) {
        return animatable.isEnragedVisual() ? TEX_ENRAGED : TEX_NORMAL;
    }

    @Override
    @Deprecated
    public ResourceLocation getTextureResource(JormungandrBossEntity animatable) {
        return animatable.isEnragedVisual() ? TEX_ENRAGED : TEX_NORMAL;
    }

    @Override
    public ResourceLocation getAnimationResource(JormungandrBossEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(JormungandrBossEntity animatable, long instanceId,
                                    AnimationState<JormungandrBossEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        JormungandrConfig cfg = JormungandrConfig.get();
        SerpentPath path = animatable.getSerpentPath();
        float partialTick = animationState.getPartialTick();
        float visualScale = Math.max(0.25f, cfg.getEffectiveVisualScale());
        float phaseThickness = animatable.getPhaseThicknessFactor();
        int activeSegs = animatable.getActiveSegmentCount();

        if (!path.hasClientSynced()) {
            hideUnusedSegments(activeSegs);
            return;
        }

        double ex = Mth.lerp(partialTick, animatable.xo, animatable.getX());
        double ey = Mth.lerp(partialTick, animatable.yo, animatable.getY());
        double ez = Mth.lerp(partialTick, animatable.zo, animatable.getZ());
        float bodyYawDeg = Mth.rotLerp(partialTick, animatable.yBodyRotO, animatable.yBodyRot);
        double psi = bodyYawDeg * Mth.DEG_TO_RAD;
        double cosPsi = Math.cos(psi);
        double sinPsi = Math.sin(psi);

        long gameTick = animatable.level().getGameTime();
        path.sampleClientPoses(gameTick, partialTick, phaseThickness, visualScale, this.poseBuffer);

        // 1. Đặt bone `head` từ node 0 + phóng đồng bộ toàn bộ cụm đầu (jaw, mắt, răng, sừng, lưỡi, mang cổ) theo headScaleMultiplier
        float extraHeadYawDeg = 0.0f;
        float extraHeadPitchRad = 0.0f;
        GeoBone headBone = getAnimationProcessor().getBone(JormungandrBossEntity.HEAD_BONE);
        if (headBone != null) {
            SegmentPose hp = this.poseBuffer[0];
            placeFlatBone(headBone, hp, ex, ey, ez, cosPsi, sinPsi, bodyYawDeg, visualScale, false);
            float headScale = phaseThickness * cfg.headScaleMultiplier;
            headBone.updateScale(headScale, headScale, headScale);

            if (!animatable.isHeadLockedByAnimation()) {
                EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
                if (data != null) {
                    extraHeadYawDeg = Mth.clamp(Mth.wrapDegrees(data.netHeadYaw()), -45.0f, 45.0f);
                    float lookPitchDeg = Mth.clamp(Mth.wrapDegrees(data.headPitch()), -30.0f, 30.0f);
                    extraHeadPitchRad = lookPitchDeg * Mth.DEG_TO_RAD;
                    headBone.setRotY(headBone.getRotY() + extraHeadYawDeg * Mth.DEG_TO_RAD);
                    headBone.setRotX(Mth.clamp(headBone.getRotX() + extraHeadPitchRad,
                            -45.0f * Mth.DEG_TO_RAD, 45.0f * Mth.DEG_TO_RAD));
                }
            }
        }

        // 2. Đặt toàn bộ đốt thân phẳng `segment_1 .. segment_N_MAX`
        //    scaleX/scaleY = diameterScale (độ thon), scaleZ = lengthScale (co giãn động chống hở khe!)
        for (int i = 1; i <= SerpentPath.N_MAX; i++) {
            GeoBone seg = getAnimationProcessor().getBone(JormungandrBossEntity.SEGMENT_PREFIX + i);
            if (seg == null) continue;
            if (i > activeSegs) {
                seg.setHidden(true);
                continue;
            }
            seg.setHidden(false);
            SegmentPose sp = this.poseBuffer[i];
            placeFlatBone(seg, sp, ex, ey, ez, cosPsi, sinPsi, bodyYawDeg, visualScale, true);
        }

        // 3. Đặt bone đầu đuôi phẳng `tail_tip`
        GeoBone tailBone = getAnimationProcessor().getBone(JormungandrBossEntity.TAIL_TIP_BONE);
        if (tailBone != null) {
            tailBone.setHidden(false);
            SegmentPose tp = this.poseBuffer[activeSegs + 1];
            placeFlatBone(tailBone, tp, ex, ey, ez, cosPsi, sinPsi, bodyYawDeg, visualScale, true);
            boolean rattle = animatable.isEnragedVisual()
                    || animatable.getSkillState() == JormungandrBossEntity.SkillState.TAIL_SLAM
                    || animatable.getSkillState() == JormungandrBossEntity.SkillState.HOOD_FLARE;
            if (rattle) {
                float shake = Mth.sin((animatable.tickCount + partialTick) * 1.45f) * 0.22f;
                tailBone.setRotY(tailBone.getRotY() + shake);
            }
        }

        // 4. Hiệu ứng hạt gọn gắn đúng các locator bộ phận (mỗi tick 1 lần, bật/tắt qua config)
        if (cfg.jormungandrFancyEffects && gameTick != this.lastFxTick
                && animatable.isAlive() && !animatable.isBurrowed()
                && path.getClientInterpolatedBurrow(gameTick, partialTick) < 0.25f) {
            this.lastFxTick = gameTick;
            emitLocatorAnchoredEffects(animatable, path, gameTick, partialTick,
                    activeSegs, visualScale, phaseThickness, extraHeadYawDeg, extraHeadPitchRad);
        }
    }

    private void emitLocatorAnchoredEffects(JormungandrBossEntity entity, SerpentPath path,
                                            long gameTick, float partialTick, int activeSegs,
                                            float visualScale, float phaseThickness,
                                            float extraHeadYawDeg, float extraHeadPitchRad) {
        Level level = entity.level();
        RandomSource rng = entity.getRandom();
        boolean enraged = entity.isEnragedVisual();
        float raise = path.getClientInterpolatedRaise(gameTick, partialTick);
        float slither = path.getClientSlitherStrength();
        SegmentPose hp = this.poseBuffer[0];
        float headYaw = Mth.wrapDegrees(hp.yawDeg - extraHeadYawDeg);
        float headPitch = Mth.clamp(hp.pitchRad + extraHeadPitchRad, -0.78f, 0.78f);
        float headTotalScale = visualScale * phaseThickness * JormungandrConfig.get().headScaleMultiplier;

        // Locator 1: `mouth_venom` [0, -7.8, -25.8] -> giọt/hơi độc xanh-tím nhẹ rỉ từ đầu răng nanh
        int mouthInterval = enraged ? 4 : 7;
        if ((gameTick + entity.getId()) % mouthInterval == 0) {
            double sideX = rng.nextBoolean() ? 5.6 : -5.6;
            Vec3 mouthPos = localBoneOffsetToWorld(hp, headYaw, headPitch, sideX, -7.8, -25.8, headTotalScale);
            DustParticleOptions drip = (enraged && rng.nextInt(3) == 0) ? VENOM_MIST_VIOLET : VENOM_DRIP_GREEN;
            level.addParticle(drip,
                    mouthPos.x + (rng.nextDouble() - 0.5) * 0.14,
                    mouthPos.y,
                    mouthPos.z + (rng.nextDouble() - 0.5) * 0.14,
                    0.0, -0.05, 0.0);
        }

        // Locator 2 & 3: `hood_l_edge` / `hood_r_edge` -> luồng sương tím mảnh dọc mép mang cổ khi ngẩng cổ hoặc Phase 2
        if (raise > 0.45f || enraged) {
            int hoodInterval = enraged ? 3 : 6;
            if ((gameTick + entity.getId()) % hoodInterval == 0) {
                boolean leftSide = ((gameTick / hoodInterval) & 1L) == 0L;
                double hx = leftSide ? 32.0 : -32.0;
                double hy = -10.0 - rng.nextDouble() * 18.0;
                double hz = 12.0 + rng.nextDouble() * 8.0;
                Vec3 hoodEdge = localBoneOffsetToWorld(hp, headYaw, headPitch, hx, hy, hz, headTotalScale);
                level.addParticle(enraged ? HOOD_MIST_P2 : HOOD_MIST_P1,
                        hoodEdge.x, hoodEdge.y, hoodEdge.z,
                        (rng.nextDouble() - 0.5) * 0.025, 0.025, (rng.nextDouble() - 0.5) * 0.025);
            }
        }

        // Locator 4: `tail_tip_fx` -> tia tím cổ đại nhẹ tại chóp đuôi ở Phase 2
        if (enraged && (gameTick + entity.getId()) % 7 == 0) {
            SegmentPose tp = this.poseBuffer[activeSegs + 1];
            Vec3 tailTip = localBoneOffsetToWorld(tp, tp.yawDeg, tp.pitchRad,
                    0.0, 0.5, 36.0 * tp.lengthScale, visualScale * tp.diameterScale);
            level.addParticle(TAIL_SPARK_P2,
                    tailTip.x, tailTip.y, tailTip.z,
                    (rng.nextDouble() - 0.5) * 0.03, 0.03, (rng.nextDouble() - 0.5) * 0.03);
        }

        // Bụi đất nhẹ dưới các đốt thân tiếp xúc mặt đất khi trườn nhanh (tối đa 2 hạt mỗi 4 tick)
        if (slither > 0.35f && (gameTick + entity.getId()) % 4 == 0 && activeSegs >= 12) {
            int startSeg = Math.min(activeSegs, SerpentPath.REAR_NECK_SEGMENTS + 1);
            for (int k = 0; k < 2; k++) {
                int segIdx = startSeg + rng.nextInt(Math.max(1, activeSegs - startSeg));
                SegmentPose sp = this.poseBuffer[segIdx];
                float rad = JormungandrConfig.get().getScaledBaseBodyDiameter() * 0.5f * sp.diameterScale;
                double groundY = sp.y - rad * 0.85;
                BlockPos below = BlockPos.containing(sp.x, groundY - 0.15, sp.z);
                BlockState st = level.getBlockState(below);
                if (!st.isAir() && st.getFluidState().isEmpty()) {
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st),
                            sp.x + (rng.nextDouble() - 0.5) * rad * 1.1,
                            groundY + 0.08,
                            sp.z + (rng.nextDouble() - 0.5) * rad * 1.1,
                            (rng.nextDouble() - 0.5) * 0.08, 0.05, (rng.nextDouble() - 0.5) * 0.08);
                }
            }
        }
    }

    /** Chuyển offset cục bộ (model px, -Z trước, +X trái, +Y trên) của bone phẳng sang tọa độ thế giới. */
    private static Vec3 localBoneOffsetToWorld(SegmentPose pose, float yawDeg, float pitchRad,
                                               double lx, double ly, double lz, float scaleFactor) {
        double pxToBlk = scaleFactor / 16.0;
        double cosP = Math.cos(pitchRad);
        double sinP = Math.sin(pitchRad);
        // Xoay quanh trục +X của model theo pitchRad
        double y1 = ly * cosP - lz * sinP;
        double z1 = ly * sinP + lz * cosP;

        double yRad = yawDeg * Mth.DEG_TO_RAD;
        double fwdX = -Math.sin(yRad);
        double fwdZ = Math.cos(yRad);
        double leftX = -Math.cos(yRad);
        double leftZ = -Math.sin(yRad);

        double wx = pose.x + (leftX * lx + fwdX * (-z1)) * pxToBlk;
        double wy = pose.y + y1 * pxToBlk;
        double wz = pose.z + (leftZ * lx + fwdZ * (-z1)) * pxToBlk;
        return new Vec3(wx, wy, wz);
    }

    private void hideUnusedSegments(int activeSegs) {
        for (int i = activeSegs + 1; i <= SerpentPath.N_MAX; i++) {
            GeoBone seg = getAnimationProcessor().getBone(JormungandrBossEntity.SEGMENT_PREFIX + i);
            if (seg != null) seg.setHidden(true);
        }
    }

    /**
     * Đặt vị trí, góc xoay và scale TUYỆT ĐỐI cho bone phẳng có pivot `[0, 0, 0]`:
     * - Trục X, Y: `pose.diameterScale` (độ thon mặt cắt từ cổ xuống đuôi).
     * - Trục Z: `pose.lengthScale` (`(dist3D / worldBaseSegLen) * overlapFactor`, TUYỆT ĐỐI không nhân taper).
     */
    private static void placeFlatBone(GeoBone bone, SegmentPose pose,
                                      double ex, double ey, double ez,
                                      double cosPsi, double sinPsi, float bodyYawDeg,
                                      float visualScale, boolean applyBodyScale) {
        double dx = pose.x - ex;
        double dy = pose.y - ey;
        double dz = pose.z - ez;

        double mx = -(dx * cosPsi + dz * sinPsi);
        double my = dy;
        double mz = dx * sinPsi - dz * cosPsi;

        float pxScale = 16.0f / visualScale;
        bone.setPosX((float) (-mx * pxScale));
        bone.setPosY((float) (my * pxScale));
        bone.setPosZ((float) (mz * pxScale));

        float relYawDeg = Mth.wrapDegrees(bodyYawDeg - pose.yawDeg);
        bone.setRotY(relYawDeg * Mth.DEG_TO_RAD);
        bone.setRotX(pose.pitchRad);
        bone.setRotZ(0.0f);

        if (applyBodyScale) {
            float d = pose.diameterScale;
            float l = pose.lengthScale;
            bone.updateScale(d, d, l);
        }
    }
}
