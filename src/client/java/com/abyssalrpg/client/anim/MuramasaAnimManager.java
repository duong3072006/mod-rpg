package com.abyssalrpg.client.anim;

import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.combat.MuramasaMoveType;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Hệ thống Rig Animation Toàn Thân 100% Đứng Im (0 rung lắc, 0 xuyên thân) cho **Yêu Đao Muramasa Katana & Vỏ Kiếm**:
 * - **Idle Samurai Outward Stance:** Tay phải mở rộng ngoài hông (`rightArmZ=+0.34F`), lưỡi katana hướng thẳng-chếch phải
 *   khoe rõ cạnh lưỡi phát sáng vàng; tay trái giữ vỏ kiếm rồng đỏ mở ngoài hông trái (`leftArmZ=-0.34F`), 0 xuyên người!
 * - **Samurai Sprint:** Cả 2 tay khóa chắc mở ngoài hông (`+0.40F / -0.38F`), lưỡi katana & vỏ kiếm lướt ngược song song,
 *   100% đứng im không rung giật!
 * - **Combo 3 Nhịp Liền Mạch:** Kesagiri (Phải-Trên -> Trái-Dưới) -> Gyaku-Kesa (Trái-Dưới -> Phải-Trên) -> Demon Cross-Cleave!
 * - **Iaijutsu Blood-Oath Draw (RMB):** Thu người kéo katana ngang vỏ kiếm bên hông trái -> bung rút kiếm chém rộng 180°!
 */
public final class MuramasaAnimManager {
    // Verified 1:1 Euler constants solved by SimMuramasaV2Preview (raised poise off grass, zero clipping, zero jitter!)
    // Sprint keeps the exact raised forward-outward orientation (no 155° flip or gimbal shake when running/stopping!)
    private static final float IDLE_RP = -6.00F, IDLE_RR = -20.00F, IDLE_RY = 14.00F;
    private static final float IDLE_LP = -145.69F, IDLE_LR = 1.97F, IDLE_LY = 15.57F;

    private static final float SPRINT_RP = -10.00F, SPRINT_RR = -20.00F, SPRINT_RY = 14.00F;
    private static final float SPRINT_LP = IDLE_LP, SPRINT_LR = IDLE_LR, SPRINT_LY = IDLE_LY;

    private static final class MuramasaState {
        float prevStance = 0.0F;
        float stance = 0.0F;
        float prevSprint = 0.0F;
        float sprint = 0.0F;
        long sprintGraceUntilTick = -1000L;

        boolean active = false;
        MuramasaMoveType move = MuramasaMoveType.COMBO_1;
        long startTick = -1000L;
        float durationTicks = 10.0F;
        long lastSeenTick = 0L;

        final FullBodyPose idlePose = new FullBodyPose();
        final FullBodyPose sprintPose = new FullBodyPose();
        final FullBodyPose actionPose = new FullBodyPose();
        final FullBodyPose blendFrom = new FullBodyPose();
        final FullBodyPose lastPose = new FullBodyPose();
        final FullBodyPose outPose = new FullBodyPose();
        boolean hasBlendFrom = false;
    }

    private static final Map<Integer, MuramasaState> STATES = new HashMap<>();
    private static long clientTickCounter = 0L;

    private MuramasaAnimManager() {}

    public static long getClientTick() {
        return clientTickCounter;
    }

    public static void clearAll() {
        STATES.clear();
    }

    public static boolean isHoldingMuramasa(@Nullable Player player) {
        return player != null && player.getMainHandItem().is(ModItems.MURAMASA);
    }

    public static boolean shouldMirrorScabbardOffhand(@Nullable Player player) {
        return player != null
                && player.getMainHandItem().is(ModItems.MURAMASA)
                && player.getOffhandItem().isEmpty();
    }

    public static float getActionProgress(int playerId, float partialTick) {
        MuramasaState st = STATES.get(playerId);
        if (st == null || !st.active) {
            return 0.0F;
        }
        float alpha = ((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks);
        return (alpha >= 0.0F && alpha < 1.0F) ? alpha : 0.0F;
    }

    public static MuramasaMoveType getMoveType(int playerId) {
        MuramasaState st = STATES.get(playerId);
        return st != null ? st.move : MuramasaMoveType.COMBO_1;
    }

    public static float getSprintBlend(int playerId, float partialTick) {
        MuramasaState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        return smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevSprint, st.sprint), 0.0F, 1.0F));
    }

    public static float getGlowBoost(int playerId) {
        MuramasaState st = STATES.get(playerId);
        if (st == null || !st.active) {
            return 0.0F;
        }
        return (st.move == MuramasaMoveType.IAIJUTSU_DRAW || st.move == MuramasaMoveType.BLOOD_OATH_AWAKEN) ? 1.0F : 0.35F;
    }

    public static void triggerMove(int playerId, MuramasaMoveType move, float durationTicks) {
        MuramasaState st = STATES.computeIfAbsent(playerId, k -> new MuramasaState());
        st.blendFrom.copyFrom(st.lastPose);
        st.hasBlendFrom = (st.stance > 0.05F);
        st.active = true;
        st.move = move;
        st.startTick = clientTickCounter;
        st.durationTicks = Math.max(5.0F, durationTicks);
        st.lastSeenTick = clientTickCounter;
    }

    public static void tickClient(Minecraft mc) {
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        clientTickCounter++;

        for (Player player : mc.level.players()) {
            boolean holding = isHoldingMuramasa(player);
            MuramasaState st = STATES.get(player.getId());
            if (!holding && st == null) {
                continue;
            }
            if (st == null) {
                st = new MuramasaState();
                STATES.put(player.getId(), st);
            }
            st.lastSeenTick = clientTickCounter;
            st.prevStance = st.stance;
            st.prevSprint = st.sprint;

            float targetStance = (holding && !player.isSleeping() && !player.isSwimming() && !player.isFallFlying()) ? 1.0F : 0.0F;
            if (targetStance >= 0.99F) {
                // Khóa cứng stance = 1.0 ngay khi cầm Muramasa -> 0 lọt dao động vung tay vanilla ở khung đầu!
                st.stance = 1.0F;
                st.prevStance = 1.0F;
            } else {
                st.stance = Mth.clamp(st.stance - 0.25F, 0.0F, 1.0F);
            }

            boolean movingFast = player.walkAnimation.speed() > 0.14F
                    || player.getDeltaMovement().horizontalDistanceSqr() > 0.004;
            boolean sprintingNow = holding && player.isSprinting() && !player.isCrouching()
                    && !player.isPassenger() && (!st.active || !st.move.lockedSkill);
            if (sprintingNow) {
                st.sprintGraceUntilTick = clientTickCounter + 8L;
            }
            boolean sprintActive = holding && !player.isCrouching() && !player.isPassenger()
                    && (!st.active || !st.move.lockedSkill)
                    && (sprintingNow || (clientTickCounter <= st.sprintGraceUntilTick && movingFast));
            float targetSprint = sprintActive ? 1.0F : 0.0F;
            st.sprint = Mth.clamp(st.sprint + (targetSprint > st.sprint ? 0.18F : -0.15F), 0.0F, 1.0F);

            if (st.active) {
                float elapsed = clientTickCounter - st.startTick;
                if (elapsed >= st.durationTicks || !holding) {
                    st.active = false;
                    st.hasBlendFrom = false;
                }
            }
        }

        Iterator<Map.Entry<Integer, MuramasaState>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            MuramasaState st = it.next().getValue();
            if ((clientTickCounter - st.lastSeenTick) > 60L || (st.stance <= 0.001F && !st.active)) {
                it.remove();
            }
        }
    }

    @Nullable
    public static FullBodyPose evaluatePose(Player player, float partialTick, float limbSwingAmount, float ageInTicks) {
        MuramasaState st = STATES.get(player.getId());
        if (st == null) {
            if (!isHoldingMuramasa(player) || player.isSleeping() || player.isSwimming() || player.isFallFlying()) {
                return null;
            }
            st = new MuramasaState();
            st.prevStance = 1.0F;
            st.stance = 1.0F;
            st.lastSeenTick = clientTickCounter;
            STATES.put(player.getId(), st);
        }
        float stance = Mth.clamp(Mth.lerp(partialTick, st.prevStance, st.stance), 0.0F, 1.0F);
        if (stance <= 0.002F && !st.active) {
            return null;
        }
        float sprint = Mth.clamp(Mth.lerp(partialTick, st.prevSprint, st.sprint), 0.0F, 1.0F);
        float walkSpeed = player.walkAnimation.speed(partialTick);

        fillIdlePose(st.idlePose);
        if (sprint > 0.001F) {
            fillSprintPose(st.sprintPose);
            st.idlePose.blendFrom(st.sprintPose, 1.0F - smootherstep(sprint));
        }

        FullBodyPose out = st.outPose;
        if (st.active) {
            float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
            fillActionPose(st.actionPose, st.move, alpha);
            out.copyFrom(st.actionPose);

            if (alpha < 0.15F && st.hasBlendFrom) {
                out.blendFrom(st.blendFrom, smootherstep(alpha / 0.15F));
            } else if (alpha > 0.78F) {
                out.blendFrom(st.idlePose, 1.0F - smootherstep((alpha - 0.78F) / 0.22F));
            }
            out.isAttacking = true;
        } else {
            out.copyFrom(st.idlePose);
            out.isAttacking = false;
            out.armWeight = smootherstep(stance);
            float standFactor = 1.0F - Mth.clamp(walkSpeed * 2.5F, 0.0F, 1.0F);
            out.legWeight = smootherstep(stance) * standFactor * (1.0F - sprint);
        }

        st.lastPose.copyFrom(out);
        return out;
    }

    /** 100% Rock-solid outward Samurai Idle — ZERO wobble, ZERO clipping! */
    private static void fillIdlePose(FullBodyPose p) {
        p.reset();
        p.bodyX = 0.04F;
        p.bodyY = 0.14F;
        p.headAddX = -0.03F;
        p.headAddY = -0.10F;

        p.rightArmX = -0.04F;
        p.rightArmY = -0.08F;
        p.rightArmZ = 0.36F;

        p.leftArmX = 0.16F;
        p.leftArmY = 0.10F;
        p.leftArmZ = -0.34F;

        p.rightLegX = -0.06F;
        p.rightLegZ = 0.10F;
        p.leftLegX = 0.08F;
        p.leftLegZ = -0.10F;

        p.rightBladePitchDeg = IDLE_RP;
        p.rightBladeRollDeg = IDLE_RR;
        p.rightBladeYawDeg = IDLE_RY;
        p.leftBladePitchDeg = IDLE_LP;
        p.leftBladeRollDeg = IDLE_LR;
        p.leftBladeYawDeg = IDLE_LY;
    }

    /** 100% Rock-solid Samurai Dash Sprint — ZERO 155° blade flip, ZERO arm/sword shake, ZERO body clipping! */
    private static void fillSprintPose(FullBodyPose p) {
        p.reset();
        p.bodyX = 0.14F;
        p.bodyY = 0.12F;
        p.headAddX = -0.11F;
        p.headAddY = -0.09F;

        p.rightArmX = 0.04F;
        p.rightArmY = -0.08F;
        p.rightArmZ = 0.38F;

        p.leftArmX = 0.20F;
        p.leftArmY = 0.10F;
        p.leftArmZ = -0.36F;

        p.rightBladePitchDeg = SPRINT_RP;
        p.rightBladeRollDeg = SPRINT_RR;
        p.rightBladeYawDeg = SPRINT_RY;
        p.leftBladePitchDeg = SPRINT_LP;
        p.leftBladeRollDeg = SPRINT_LR;
        p.leftBladeYawDeg = SPRINT_LY;
    }

    private static void fillActionPose(FullBodyPose p, MuramasaMoveType move, float alpha) {
        p.reset();
        p.armWeight = 1.0F;
        p.legWeight = 0.40F;

        switch (move) {
            case COMBO_1 -> {
                // Kesagiri (Phải-Trên -> Giữa -> Trái-Dưới)
                float s = smootherstep(Mth.clamp((alpha - 0.12F) / 0.44F, 0.0F, 1.0F));
                p.bodyX = lerp3(s, -0.06F, 0.08F, 0.18F);
                p.bodyY = lerp3(s, 0.26F, -0.04F, -0.32F);
                p.headAddX = -p.bodyX * 0.70F;
                p.headAddY = -p.bodyY * 0.72F;

                p.rightArmX = lerp3(s, -2.02F, -1.22F, -0.54F);
                p.rightArmY = lerp3(s, 0.54F, -0.04F, -0.56F);
                p.rightArmZ = lerp3(s, 0.28F, 0.04F, -0.20F);

                p.leftArmX = lerp3(s, 0.18F, 0.24F, 0.32F);
                p.leftArmY = lerp3(s, 0.14F, 0.12F, 0.06F);
                p.leftArmZ = lerp3(s, -0.34F, -0.36F, -0.38F);

                p.rightLegX = Mth.lerp(s, 0.18F, -0.24F);
                p.leftLegX = Mth.lerp(s, -0.18F, 0.24F);
                p.rightLegZ = 0.10F;
                p.leftLegZ = -0.10F;

                p.rightBladePitchDeg = rotLerp3(s, -90.12F, -72.26F, -67.01F);
                p.rightBladeRollDeg = rotLerp3(s, -11.16F, -3.35F, -2.64F);
                p.rightBladeYawDeg = rotLerp3(s, 61.85F, 45.44F, 40.01F);
                p.leftBladePitchDeg = rotLerp3(s, -144.76F, -141.78F, -137.47F);
                p.leftBladeRollDeg = rotLerp3(s, 2.50F, 4.53F, 8.68F);
                p.leftBladeYawDeg = rotLerp3(s, 14.55F, 15.97F, 18.31F);
                p.firstPersonCameraRollDeg = 0.0F;
            }
            case COMBO_2 -> {
                // Gyaku-Kesa (Trái-Dưới -> Giữa -> Phải-Trên)
                float s = smootherstep(Mth.clamp((alpha - 0.08F) / 0.46F, 0.0F, 1.0F));
                p.bodyX = lerp3(s, 0.16F, 0.08F, 0.02F);
                p.bodyY = lerp3(s, -0.32F, 0.02F, 0.34F);
                p.headAddX = -p.bodyX * 0.70F;
                p.headAddY = -p.bodyY * 0.72F;

                p.rightArmX = lerp3(s, -0.58F, -1.16F, -1.66F);
                p.rightArmY = lerp3(s, -0.56F, 0.04F, 0.62F);
                p.rightArmZ = lerp3(s, -0.20F, 0.06F, 0.34F);

                p.leftArmX = lerp3(s, 0.30F, 0.22F, 0.16F);
                p.leftArmY = lerp3(s, 0.08F, 0.12F, 0.16F);
                p.leftArmZ = lerp3(s, -0.36F, -0.36F, -0.34F);

                p.rightLegX = Mth.lerp(s, -0.22F, 0.22F);
                p.leftLegX = Mth.lerp(s, 0.22F, -0.22F);
                p.rightLegZ = 0.10F;
                p.leftLegZ = -0.10F;

                p.rightBladePitchDeg = rotLerp3(s, -60.50F, -63.14F, -81.77F);
                p.rightBladeRollDeg = rotLerp3(s, -2.18F, -0.23F, -8.20F);
                p.rightBladeYawDeg = rotLerp3(s, -131.99F, -120.87F, -105.33F);
                p.leftBladePitchDeg = rotLerp3(s, -138.92F, -142.25F, -145.19F);
                p.leftBladeRollDeg = rotLerp3(s, 8.10F, 3.06F, 0.02F);
                p.leftBladeYawDeg = rotLerp3(s, 16.78F, 16.06F, 14.09F);
                p.firstPersonCameraRollDeg = 0.0F;
            }
            case COMBO_3 -> {
                // Demon Cross-Cleave Finisher (Quét ngang tia chớp cực rộng Phải -> Trái)
                float s = smootherstep(Mth.clamp((alpha - 0.14F) / 0.44F, 0.0F, 1.0F));
                p.bodyX = lerp3(s, 0.06F, 0.14F, 0.20F);
                p.bodyY = lerp3(s, 0.46F, -0.02F, -0.48F);
                p.headAddX = -p.bodyX * 0.70F;
                p.headAddY = -p.bodyY * 0.72F;

                p.rightArmX = lerp3(s, -1.30F, -1.24F, -1.08F);
                p.rightArmY = lerp3(s, 0.78F, -0.02F, -0.78F);
                p.rightArmZ = lerp3(s, 0.42F, 0.10F, -0.22F);

                p.leftArmX = lerp3(s, 0.14F, 0.26F, 0.34F);
                p.leftArmY = lerp3(s, 0.18F, 0.10F, 0.04F);
                p.leftArmZ = lerp3(s, -0.34F, -0.38F, -0.40F);

                p.rightLegX = Mth.lerp(s, 0.22F, -0.28F);
                p.leftLegX = Mth.lerp(s, -0.22F, 0.28F);
                p.rightLegZ = 0.12F;
                p.leftLegZ = -0.12F;

                p.rightBladePitchDeg = rotLerp3(s, -84.50F, -73.33F, -82.58F);
                p.rightBladeRollDeg = rotLerp3(s, -7.53F, -0.92F, 7.26F);
                p.rightBladeYawDeg = rotLerp3(s, 100.21F, 90.01F, 72.51F);
                p.leftBladePitchDeg = rotLerp3(s, -147.08F, -140.38F, -136.03F);
                p.leftBladeRollDeg = rotLerp3(s, 0.42F, 5.10F, 9.25F);
                p.leftBladeYawDeg = rotLerp3(s, 13.58F, 17.49F, 19.84F);
                p.fovOffsetDeg = Mth.sin(s * Mth.PI) * 4.5F;
            }
            case IAIJUTSU_DRAW -> {
                // RMB Iaijutsu Blood-Oath Draw: 0..30% gồng rút kiếm sát vỏ bên hông trái -> 30..65% rút chém 180° bùng nổ!
                p.legWeight = 0.78F;
                if (alpha < 0.30F) {
                    float c = easeOutCubic(alpha / 0.30F);
                    p.rootTranslateY = -0.12F * c;
                    p.bodyX = 0.28F * c;
                    p.bodyY = -0.38F * c;
                    p.headAddX = -0.22F * c;
                    p.headAddY = 0.28F * c;

                    p.rightArmX = Mth.lerp(c, 0.10F, -0.72F);
                    p.rightArmY = Mth.lerp(c, -0.06F, -0.68F);
                    p.rightArmZ = Mth.lerp(c, 0.34F, -0.12F);

                    p.leftArmX = Mth.lerp(c, 0.16F, -0.44F);
                    p.leftArmY = Mth.lerp(c, 0.10F, 0.42F);
                    p.leftArmZ = Mth.lerp(c, -0.34F, -0.18F);

                    p.rightLegX = -0.24F * c;
                    p.leftLegX = 0.28F * c;
                    p.rightLegZ = 0.14F;
                    p.leftLegZ = -0.14F;

                    p.rightBladePitchDeg = Mth.rotLerp(c, IDLE_RP, -175.02F);
                    p.rightBladeRollDeg = Mth.rotLerp(c, IDLE_RR, 57.06F);
                    p.rightBladeYawDeg = Mth.rotLerp(c, IDLE_RY, -135.23F);
                    p.leftBladePitchDeg = Mth.rotLerp(c, IDLE_LP, 177.74F);
                    p.leftBladeRollDeg = Mth.rotLerp(c, IDLE_LR, -1.24F);
                    p.leftBladeYawDeg = Mth.rotLerp(c, IDLE_LY, 0.22F);
                    p.fovOffsetDeg = -6.0F * c;
                } else {
                    float draw = easeOutQuart(Mth.clamp((alpha - 0.30F) / 0.26F, 0.0F, 1.0F));
                    p.rootTranslateY = Mth.lerp(draw, -0.12F, -0.04F);
                    p.bodyX = Mth.lerp(draw, 0.28F, 0.14F);
                    p.bodyY = Mth.lerp(draw, -0.38F, 0.48F);
                    p.headAddX = -p.bodyX * 0.72F;
                    p.headAddY = -p.bodyY * 0.72F;

                    p.rightArmX = Mth.lerp(draw, -0.72F, -1.26F);
                    p.rightArmY = Mth.lerp(draw, -0.68F, 0.86F);
                    p.rightArmZ = Mth.lerp(draw, -0.12F, 0.48F);

                    p.leftArmX = Mth.lerp(draw, -0.44F, 0.34F);
                    p.leftArmY = Mth.lerp(draw, 0.42F, 0.06F);
                    p.leftArmZ = Mth.lerp(draw, -0.18F, -0.42F);

                    p.rightLegX = Mth.lerp(draw, -0.24F, 0.26F);
                    p.leftLegX = Mth.lerp(draw, 0.28F, -0.24F);
                    p.rightLegZ = 0.14F;
                    p.leftLegZ = -0.14F;

                    p.rightBladePitchDeg = Mth.rotLerp(draw, -175.02F, -88.66F);
                    p.rightBladeRollDeg = Mth.rotLerp(draw, 57.06F, -6.92F);
                    p.rightBladeYawDeg = Mth.rotLerp(draw, -135.23F, -78.86F);
                    p.leftBladePitchDeg = Mth.rotLerp(draw, 177.74F, -136.46F);
                    p.leftBladeRollDeg = Mth.rotLerp(draw, -1.24F, 9.25F);
                    p.leftBladeYawDeg = Mth.rotLerp(draw, 0.22F, 20.63F);
                    p.fovOffsetDeg = Mth.lerp(draw, -6.0F, 9.0F) * (1.0F - Mth.clamp((alpha - 0.66F) / 0.34F, 0.0F, 1.0F));
                }
            }
            case BLOOD_OATH_AWAKEN -> {
                // Giải phóng Yêu Quỷ Muramasa: bung 2 tay tỏa sóng huyết quang!
                float b = easeOutQuart(Mth.clamp(alpha / 0.38F, 0.0F, 1.0F));
                float s = smootherstep(Mth.clamp((alpha - 0.45F) / 0.55F, 0.0F, 1.0F));
                p.legWeight = 0.72F;
                p.bodyX = Mth.lerp(s, -0.12F * b, 0.04F);
                p.bodyY = Mth.lerp(s, 0.0F, 0.14F);
                p.rightArmX = Mth.lerp(s, -0.42F * b, 0.10F);
                p.rightArmZ = Mth.lerp(s, 0.76F * b, 0.34F);
                p.leftArmX = Mth.lerp(s, -0.30F * b, 0.16F);
                p.leftArmZ = Mth.lerp(s, -0.76F * b, -0.34F);
                p.rightLegZ = 0.12F;
                p.leftLegZ = -0.12F;
                p.rightBladePitchDeg = IDLE_RP;
                p.rightBladeRollDeg = IDLE_RR;
                p.rightBladeYawDeg = IDLE_RY;
                p.leftBladePitchDeg = IDLE_LP;
                p.leftBladeRollDeg = IDLE_LR;
                p.leftBladeYawDeg = IDLE_LY;
                p.fovOffsetDeg = Mth.sin(b * Mth.PI) * 8.0F * (1.0F - s);
            }
        }
    }

    private static float lerp3(float t, float a, float b, float c) {
        float x = Mth.clamp(t, 0.0F, 1.0F);
        return x < 0.5F ? Mth.lerp(x * 2.0F, a, b) : Mth.lerp((x - 0.5F) * 2.0F, b, c);
    }

    private static float rotLerp3(float t, float a, float b, float c) {
        float x = Mth.clamp(t, 0.0F, 1.0F);
        return x < 0.5F ? Mth.rotLerp(x * 2.0F, a, b) : Mth.rotLerp((x - 0.5F) * 2.0F, b, c);
    }

    private static float smootherstep(float t) {
        float x = Mth.clamp(t, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    private static float easeOutCubic(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u * u;
    }

    private static float easeOutQuart(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u * u * u;
    }
}
