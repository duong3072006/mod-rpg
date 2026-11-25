package com.abyssalrpg.client.anim;

import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.combat.ExcaliburMoveType;
import com.abyssalrpg.item.ExcaliburItem;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Hệ thống Rig Animation Toàn Thân chuẩn Radians (`@At("TAIL")` của `HumanoidModel.setupAnim`) cho
 * **Excalibur (Golden)** và **Excalibur Morgan (Saber Alter)**:
 * - **Golden Chivalrous Idle:** `rightArm=(-0.95, -0.35, -0.15)`, `leftArm=(-0.75, +0.45, 0.0)`, `body.yRot=-0.15`, `head.xRot=0.0`.
 * - **Saber Alter Idle (Morgan):** `rightArm=(-0.35, 0.0, -0.25)`, `leftArm=(-0.15, 0.0, +0.25)`, `body.yRot=+0.20`, `head.xRot=0.0`,
 *   lưỡi kiếm hắc diện thạch cắm thấp cạnh chân phải (0 chạm đất, `minY=+0.167`).
 * - **Noble Knight Sprint:** `body.xRot=0.25`, `head.xRot=-0.22`, `rightArm=(+0.45, 0.0, -0.25)` kéo kiếm lướt phía sau (`minY=+0.479`),
 *   tay trái đánh tự nhiên đồng bộ bước chạy!
 * - **3-Hit Knight Combo:** Hit 1 Diagonal Cleave (0.35s), Hit 2 Horizontal Sweep (0.35s), Hit 3 Heavy Overhead Slam (0.45s + 0.08s hit-stop!).
 * - **Two-Handed Vertical Beam Ultimate (RMB):** 0.65s Skyward Charge 90° thẳng đứng (`rightArm=(-2.65, -0.35, -0.10)`,
 *   `leftArm=(-2.60, +0.35, +0.10)`) + 0.10s Apex Flash + Vertical Overhead Slam (`-2.65 -> -0.25`, `body.xRot=0.32`, `head.xRot=-0.28`)
 *   + 0.20s Follow-Through Hold!
 * - **4.0s Dark Mana Channel ([R]):** `rightArm.xRot=-1.10`, `leftArm.xRot=-1.00`, `body.xRot=0.10`, `head.xRot=0.0` + Awakening Burst!
 */
public final class ExcaliburAnimManager {
    // Verified solved blade Euler angles from SimExcaliburPreview (exact Minecraft HumanoidModel + ItemInHandLayer matrix math!)
    private static final float IDLE_GOLD_P = -19.27F, IDLE_GOLD_R = 4.29F, IDLE_GOLD_Y = 160.66F;
    private static final float SPRINT_P = -149.67F, SPRINT_R = -15.51F, SPRINT_Y = -164.77F;
    private static final float SKY_P = -63.80F, SKY_R = 5.38F, SKY_Y = -20.15F;
    private static final float SKY_OFF_X = -0.3639F, SKY_OFF_Y = -0.1485F, SKY_OFF_Z = -0.0873F;
    private static final float MORGAN_IDLE_P = -42.36F, MORGAN_IDLE_R = -28.58F, MORGAN_IDLE_Y = 164.84F;
    private static final float CHANNEL_P = 6.43F, CHANNEL_R = -2.23F, CHANNEL_Y = 157.63F;
    private static final float SLAM_P = -17.85F, SLAM_R = -12.58F, SLAM_Y = -179.23F;

    private static final class ExcaliburAnimState {
        float prevStanceBlend = 0.0F;
        float stanceBlend = 0.0F;
        float prevSprintBlend = 0.0F;
        float sprintBlend = 0.0F;
        long sprintGraceUntilTick = -1000L;
        float prevMorganBlend = 0.0F;
        float morganBlend = 0.0F;

        boolean active = false;
        ExcaliburMoveType move = ExcaliburMoveType.COMBO_1;
        long startTick = -1000L;
        float durationTicks = 7.0F;
        float hitStopRemaining = 0.0F;
        float channelProgress = 0.0F;
        boolean channeling = false;
        long lastSeenTick = 0L;

        final FullBodyPose idlePose = new FullBodyPose();
        final FullBodyPose sprintPose = new FullBodyPose();
        final FullBodyPose actionPose = new FullBodyPose();
        final FullBodyPose blendFrom = new FullBodyPose();
        final FullBodyPose lastPose = new FullBodyPose();
        final FullBodyPose outPose = new FullBodyPose();
        boolean hasBlendFrom = false;
    }

    private static final Map<Integer, ExcaliburAnimState> STATES = new HashMap<>();
    private static long clientTickCounter = 0L;

    private ExcaliburAnimManager() {}

    public static long getClientTick() {
        return clientTickCounter;
    }

    public static void clearAll() {
        STATES.clear();
    }

    public static boolean isHoldingExcalibur(@Nullable Player player) {
        return player != null && player.getMainHandItem().is(ModItems.EXCALIBUR);
    }

    public static float getActionProgress(int playerId, float partialTick) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st == null || !st.active) {
            return 0.0F;
        }
        if (st.channeling) {
            return Mth.clamp(st.channelProgress, 0.02F, 1.0F);
        }
        float elapsed = (clientTickCounter - st.startTick) + partialTick;
        float alpha = elapsed / Math.max(1.0F, st.durationTicks);
        return (alpha >= 0.0F && alpha < 1.0F) ? alpha : 0.0F;
    }

    public static float getChannelProgress(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        return (st != null && st.channeling) ? Mth.clamp(st.channelProgress, 0.0F, 1.0F) : 0.0F;
    }

    public static boolean isChanneling(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        return st != null && st.channeling;
    }

    public static ExcaliburMoveType getMoveType(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        return st != null ? st.move : ExcaliburMoveType.COMBO_1;
    }

    public static float getSprintBlend(int playerId, float partialTick) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        return smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevSprintBlend, st.sprintBlend), 0.0F, 1.0F));
    }

    public static float getGlowBoost(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        if (st.channeling) {
            return 0.35F + 0.65F * st.channelProgress;
        }
        if (st.active && (st.move.isUltimate() || st.move == ExcaliburMoveType.MORGAN_AWAKEN)) {
            return 1.0F;
        }
        return 0.0F;
    }

    public static void triggerMove(int playerId, ExcaliburMoveType move, float durationTicks) {
        ExcaliburAnimState st = STATES.computeIfAbsent(playerId, k -> new ExcaliburAnimState());
        st.blendFrom.copyFrom(st.lastPose);
        st.hasBlendFrom = (st.stanceBlend > 0.05F);
        st.active = true;
        st.channeling = (move == ExcaliburMoveType.MORGAN_CHANNEL);
        if (st.channeling) {
            st.channelProgress = 0.0F;
        }
        st.move = move;
        st.startTick = clientTickCounter;
        st.durationTicks = Math.max(4.0F, durationTicks);
        st.hitStopRemaining = 0.0F;
        st.lastSeenTick = clientTickCounter;
    }

    public static void updateChannelProgress(int playerId, float progress) {
        ExcaliburAnimState st = STATES.computeIfAbsent(playerId, k -> new ExcaliburAnimState());
        if (!st.active || !st.channeling) {
            triggerMove(playerId, ExcaliburMoveType.MORGAN_CHANNEL, 80.0F);
        }
        st.channelProgress = Mth.clamp(progress, 0.0F, 1.0F);
        st.lastSeenTick = clientTickCounter;
    }

    public static void cancelChannel(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st != null && st.channeling) {
            st.channeling = false;
            st.active = false;
            st.channelProgress = 0.0F;
        }
    }

    /** Kích hoạt hiệu ứng khựng chạm đòn 0.08s (~1.6 tick) cho Nhát 3 Heavy Overhead Judgment Slam! */
    public static void triggerHitStop(int playerId, float freezeTicks) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st != null && st.active) {
            st.hitStopRemaining = Math.max(st.hitStopRemaining, Mth.clamp(freezeTicks, 0.5F, 2.5F));
        }
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
            boolean holding = isHoldingExcalibur(player);
            ExcaliburAnimState st = STATES.get(player.getId());
            if (!holding && st == null) {
                continue;
            }
            if (st == null) {
                st = new ExcaliburAnimState();
                STATES.put(player.getId(), st);
            }
            st.lastSeenTick = clientTickCounter;
            st.prevStanceBlend = st.stanceBlend;
            st.prevSprintBlend = st.sprintBlend;
            st.prevMorganBlend = st.morganBlend;

            float targetStance = (holding && !player.isSleeping() && !player.isSwimming() && !player.isFallFlying()) ? 1.0F : 0.0F;
            st.stanceBlend = Mth.clamp(st.stanceBlend + (targetStance > st.stanceBlend ? 0.24F : -0.22F), 0.0F, 1.0F);

            ItemStack main = player.getMainHandItem();
            float targetMorgan = (holding && ExcaliburItem.isMorgan(main)) ? 1.0F : 0.0F;
            st.morganBlend = Mth.clamp(st.morganBlend + (targetMorgan > st.morganBlend ? 0.16F : -0.14F), 0.0F, 1.0F);

            boolean movingFast = player.walkAnimation.speed() > 0.18F
                    || player.getDeltaMovement().horizontalDistanceSqr() > 0.006;
            boolean sprintingNow = holding && player.isSprinting() && !player.isCrouching()
                    && !player.isPassenger() && (!st.active || !st.move.lockedSkill);
            if (sprintingNow) {
                st.sprintGraceUntilTick = clientTickCounter + 6L;
            }
            boolean sprintActive = holding && !player.isCrouching() && !player.isPassenger()
                    && (!st.active || !st.move.lockedSkill)
                    && (sprintingNow || (clientTickCounter <= st.sprintGraceUntilTick && movingFast));
            float targetSprint = sprintActive ? 1.0F : 0.0F;
            st.sprintBlend = Mth.clamp(st.sprintBlend + (targetSprint > st.sprintBlend ? 0.18F : -0.15F), 0.0F, 1.0F);

            if (st.hitStopRemaining > 0.0F) {
                st.hitStopRemaining = Math.max(0.0F, st.hitStopRemaining - 1.0F);
                st.startTick++; // Freeze timeline smoothly during 0.08s hit-stop!
            }

            if (st.active && !st.channeling) {
                float elapsed = clientTickCounter - st.startTick;
                if (elapsed >= st.durationTicks || !holding) {
                    st.active = false;
                    st.hasBlendFrom = false;
                }
            } else if (st.channeling && !holding) {
                st.channeling = false;
                st.active = false;
            }
        }

        Iterator<Map.Entry<Integer, ExcaliburAnimState>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            ExcaliburAnimState st = it.next().getValue();
            if ((clientTickCounter - st.lastSeenTick) > 60L || (st.stanceBlend <= 0.001F && !st.active)) {
                it.remove();
            }
        }
    }

    @Nullable
    public static FullBodyPose evaluatePose(Player player, float partialTick, float limbSwingAmount, float ageInTicks) {
        ExcaliburAnimState st = STATES.get(player.getId());
        if (st == null) {
            return null;
        }
        float stance = Mth.clamp(Mth.lerp(partialTick, st.prevStanceBlend, st.stanceBlend), 0.0F, 1.0F);
        if (stance <= 0.002F && !st.active) {
            return null;
        }

        float morgan = Mth.clamp(Mth.lerp(partialTick, st.prevMorganBlend, st.morganBlend), 0.0F, 1.0F);
        float sprint = Mth.clamp(Mth.lerp(partialTick, st.prevSprintBlend, st.sprintBlend), 0.0F, 1.0F);
        // Dùng thống nhất 1 nguồn thời gian & walkSpeed ở PlayerRenderer/HumanoidModel/ItemInHandLayer -> triệt tiêu 100% rung giật!
        float smoothTime = player.tickCount + partialTick;
        float walkSpeed = player.walkAnimation.speed(partialTick);

        // 1. Evaluate base idle stance (blending smoothly between Golden Chivalrous Idle & Saber Alter Idle!)
        fillIdlePose(st.idlePose, morgan, smoothTime);

        // 2. Blend into Noble Knight Sprint if sprinting!
        if (sprint > 0.001F) {
            fillNobleSprintPose(st.sprintPose, player, partialTick, walkSpeed);
            st.idlePose.blendFrom(st.sprintPose, 1.0F - smootherstep(sprint));
        }

        FullBodyPose out = st.outPose;
        if (st.active) {
            float alpha = st.channeling
                    ? st.channelProgress
                    : Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
            fillActionPose(st.actionPose, st.move, alpha, morgan);
            out.copyFrom(st.actionPose);

            // Smooth cross-fade entry (0..16%) & graceful exit (78..100%) — nối chiêu ở tick 7 bắt trọn đà đòn trước, 0 khựng về Idle!
            if (!st.channeling) {
                if (alpha < 0.16F && st.hasBlendFrom) {
                    out.blendFrom(st.blendFrom, smootherstep(alpha / 0.16F));
                } else if (alpha > 0.78F) {
                    out.blendFrom(st.idlePose, 1.0F - smootherstep((alpha - 0.78F) / 0.22F));
                }
            }
            out.isAttacking = true;
        } else {
            out.copyFrom(st.idlePose);
            out.isAttacking = false;
            out.armWeight = smootherstep(stance);
            // Only apply wide leg stance when standing still (release legs naturally when walking/sprinting!)
            float standFactor = 1.0F - Mth.clamp(walkSpeed * 2.5F, 0.0F, 1.0F);
            out.legWeight = smootherstep(stance) * standFactor * (1.0F - sprint);
        }

        st.lastPose.copyFrom(out);
        return out;
    }

    private static void fillIdlePose(FullBodyPose p, float morgan, float smoothTime) {
        p.reset();
        // Đứng im vững chãi khi cầm & tích năng lượng (0 dao động giật tay!)
        float m = smootherstep(morgan);

        // Golden Chivalrous Idle -> Saber Alter Idle (tỷ lệ kiếm gọn 2/3 [0.92, 0.86, 0.92], 0 chạm đất!)
        p.bodyY = Mth.lerp(m, -0.15F, +0.20F);
        p.bodyX = Mth.lerp(m, 0.02F, 0.03F);
        p.headAddX = 0.0F;
        p.headAddY = -p.bodyY * 0.55F;

        p.rightArmX = Mth.lerp(m, -0.95F, -0.42F);
        p.rightArmY = Mth.lerp(m, -0.35F, 0.00F);
        p.rightArmZ = Mth.lerp(m, -0.15F, -0.26F);

        p.leftArmX = Mth.lerp(m, -0.75F, -0.18F);
        p.leftArmY = Mth.lerp(m, +0.45F, 0.00F);
        p.leftArmZ = Mth.lerp(m, 0.00F, +0.25F);

        p.rightLegX = Mth.lerp(m, -0.08F, -0.06F);
        p.rightLegZ = Mth.lerp(m, +0.08F, +0.10F);
        p.leftLegX = Mth.lerp(m, +0.10F, +0.08F);
        p.leftLegZ = Mth.lerp(m, -0.08F, -0.10F);

        p.rightBladePitchDeg = Mth.rotLerp(m, IDLE_GOLD_P, MORGAN_IDLE_P);
        p.rightBladeRollDeg = Mth.rotLerp(m, IDLE_GOLD_R, MORGAN_IDLE_R);
        p.rightBladeYawDeg = Mth.rotLerp(m, IDLE_GOLD_Y, MORGAN_IDLE_Y);
    }

    private static void fillNobleSprintPose(FullBodyPose p, Player player, float partialTick, float walkSpeed) {
        p.reset();
        // Noble Knight Sprint: giữ cứng tay cầm kiếm vững chãi phía sau (triệt tiêu 100% rung lắc kiếm lúc chạy!),
        // chỉ tay trái đánh nhịp tự nhiên theo bước chạy!
        p.bodyX = 0.25F;
        p.bodyY = -0.12F;
        p.headAddX = -0.22F;

        float stridePhase = player.walkAnimation.position(partialTick) * 0.6662F;
        float strideAmp = Math.max(0.45F, walkSpeed);
        p.rightArmX = 0.52F;
        p.rightArmY = 0.0F;
        p.rightArmZ = -0.26F;
        p.leftArmX = Mth.cos(stridePhase) * 0.85F * strideAmp - 0.12F;
        p.leftArmY = 0.0F;
        p.leftArmZ = 0.08F;

        p.rightBladePitchDeg = SPRINT_P;
        p.rightBladeRollDeg = SPRINT_R;
        p.rightBladeYawDeg = SPRINT_Y;
    }

    private static void fillActionPose(FullBodyPose p, ExcaliburMoveType move, float alpha, float morgan) {
        p.reset();
        p.armWeight = 1.0F;
        p.legWeight = 0.42F;

        switch (move) {
            case COMBO_1 -> {
                // Hit 1 (Chém chéo Phải-Trên -> Giữa -> Trái-Dưới): nối mượt từ Idle (0..16%) -> chém dứt khoát (14..58%),
                // góc kiếm liên tục không xoay ngược 160°!
                float sweep = smootherstep(Mth.clamp((alpha - 0.14F) / 0.44F, 0.0F, 1.0F));
                p.bodyX = lerp3(sweep, -0.06F, 0.08F, 0.18F);
                p.bodyY = lerp3(sweep, 0.24F, -0.04F, -0.32F);
                p.headAddY = -p.bodyY * 0.72F;
                p.headAddX = -p.bodyX * 0.65F;

                p.rightArmX = lerp3(sweep, -1.96F, -1.24F, -0.58F);
                p.rightArmY = lerp3(sweep, 0.52F, -0.04F, -0.58F);
                p.rightArmZ = lerp3(sweep, 0.26F, -0.02F, -0.26F);

                p.leftArmX = lerp3(sweep, -0.55F, -0.10F, 0.28F);
                p.leftArmY = lerp3(sweep, 0.34F, 0.05F, -0.20F);
                p.leftArmZ = lerp3(sweep, 0.16F, 0.22F, 0.26F);

                p.rightLegX = Mth.lerp(sweep, 0.20F, -0.26F);
                p.leftLegX = Mth.lerp(sweep, -0.20F, 0.26F);
                p.rightLegZ = 0.12F;
                p.leftLegZ = -0.12F;

                p.rightBladePitchDeg = rotLerp3(sweep, -82.67F, -73.34F, -73.74F);
                p.rightBladeRollDeg = rotLerp3(sweep, -10.62F, -3.49F, -6.10F);
                p.rightBladeYawDeg = rotLerp3(sweep, 60.85F, 42.01F, 39.23F);
                p.firstPersonCameraRollDeg = Mth.sin(sweep * Mth.PI) * -2.2F;
            }
            case COMBO_2 -> {
                // Hit 2 (Quét ngược liền mạch Trái-Dưới -> Giữa -> Phải-Trên): xuất phát ĐÚNG tại điểm kết Hit 1 (0 giật về tâm!)
                float sweep = smootherstep(Mth.clamp((alpha - 0.08F) / 0.48F, 0.0F, 1.0F));
                p.bodyX = lerp3(sweep, 0.16F, 0.10F, 0.04F);
                p.bodyY = lerp3(sweep, -0.32F, 0.02F, 0.34F);
                p.headAddY = -p.bodyY * 0.72F;
                p.headAddX = -p.bodyX * 0.65F;

                p.rightArmX = lerp3(sweep, -0.62F, -1.16F, -1.62F);
                p.rightArmY = lerp3(sweep, -0.58F, 0.04F, 0.62F);
                p.rightArmZ = lerp3(sweep, -0.26F, 0.02F, 0.32F);

                p.leftArmX = lerp3(sweep, 0.24F, -0.08F, -0.42F);
                p.leftArmY = lerp3(sweep, -0.18F, 0.06F, 0.28F);
                p.leftArmZ = lerp3(sweep, 0.26F, 0.22F, 0.20F);

                p.rightLegX = Mth.lerp(sweep, -0.24F, 0.24F);
                p.leftLegX = Mth.lerp(sweep, 0.24F, -0.24F);
                p.rightLegZ = 0.12F;
                p.leftLegZ = -0.12F;

                p.rightBladePitchDeg = rotLerp3(sweep, -64.52F, -64.20F, -79.53F);
                p.rightBladeRollDeg = rotLerp3(sweep, -4.55F, -0.07F, -5.77F);
                p.rightBladeYawDeg = rotLerp3(sweep, 55.14F, 68.73F, 78.74F);
                p.firstPersonCameraRollDeg = Mth.sin(sweep * Mth.PI) * +2.4F;
            }
            case COMBO_3 -> {
                // Hit 3 (Bổ Thánh Quang 2 Tay Từ Đỉnh Đầu): vươn liền mạch từ Phải-Trên của Hit 2 lên đỉnh -> bổ thẳng dọc xuống đất!
                float slam = smootherstep(Mth.clamp((alpha - 0.24F) / 0.36F, 0.0F, 1.0F));

                p.bodyX = lerp3(slam, -0.14F, 0.08F, 0.30F);
                p.bodyY = 0.0F;
                p.headAddX = -p.bodyX * 0.78F;

                p.rightArmX = lerp3(slam, -2.42F, -1.35F, -0.26F);
                p.rightArmY = lerp3(slam, -0.22F, -0.20F, -0.18F);
                p.rightArmZ = lerp3(slam, -0.06F, -0.02F, 0.0F);

                p.leftArmX = lerp3(slam, -2.34F, -1.30F, -0.26F);
                p.leftArmY = lerp3(slam, 0.24F, 0.22F, 0.18F);
                p.leftArmZ = lerp3(slam, 0.06F, 0.02F, 0.0F);

                p.rightLegX = Mth.lerp(slam, -0.08F, -0.24F);
                p.leftLegX = Mth.lerp(slam, 0.08F, 0.28F);
                p.rightLegZ = 0.12F;
                p.leftLegZ = -0.12F;

                p.rightBladePitchDeg = rotLerp3(slam, -78.83F, -74.99F, -46.77F);
                p.rightBladeRollDeg = rotLerp3(slam, -3.39F, -11.40F, -8.78F);
                p.rightBladeYawDeg = rotLerp3(slam, 167.38F, 178.39F, -174.58F);
                p.fovOffsetDeg = Mth.sin(slam * Mth.PI) * 5.0F;
            }
            case ULT_EXCALIBUR, ULT_MORGAN -> {
                // Two-Handed Vertical Beam Ultimate (24 ticks = 1.20s):
                //   0.00..0.54 (0.00s - 0.65s): Skyward Charge 90° straight up!
                //   0.54..0.625 (0.65s - 0.75s): 0.10s Apex Flash!
                //   0.625..0.82 (0.75s - 0.98s): Vertical Overhead Slam (-2.65 -> -0.25, body.xRot=0.32, head=-0.28)!
                //   0.82..1.00 (0.98s - 1.20s): 0.20s+ Follow-Through Hold!
                p.legWeight = 0.85F;
                if (alpha < 0.625F) {
                    float raise = easeOutCubic(Mth.clamp(alpha / 0.28F, 0.0F, 1.0F));

                    p.bodyX = -0.05F * raise;
                    p.bodyY = 0.0F;
                    p.headAddX = 0.0F;

                    p.rightArmX = Mth.lerp(raise, -0.95F, -2.65F);
                    p.rightArmY = Mth.lerp(raise, -0.35F, -0.35F);
                    p.rightArmZ = -0.10F;

                    p.leftArmX = Mth.lerp(raise, -0.75F, -2.60F);
                    p.leftArmY = +0.35F;
                    p.leftArmZ = +0.10F;

                    p.rightLegZ = 0.12F;
                    p.leftLegZ = -0.12F;

                    p.rightBladePitchDeg = Mth.rotLerp(raise, IDLE_GOLD_P, SKY_P);
                    p.rightBladeRollDeg = Mth.rotLerp(raise, IDLE_GOLD_R, SKY_R);
                    p.rightBladeYawDeg = Mth.rotLerp(raise, IDLE_GOLD_Y, SKY_Y);
                    p.rightBladeOffX = SKY_OFF_X * raise;
                    p.rightBladeOffY = SKY_OFF_Y * raise;
                    p.rightBladeOffZ = SKY_OFF_Z * raise;
                    p.fovOffsetDeg = -5.5F * raise;
                } else {
                    float slam = easeOutQuart(Mth.clamp((alpha - 0.625F) / 0.18F, 0.0F, 1.0F));
                    p.bodyX = Mth.lerp(slam, -0.05F, 0.32F);
                    p.bodyY = 0.0F;
                    p.headAddX = Mth.lerp(slam, 0.0F, -0.28F);

                    p.rightArmX = Mth.lerp(slam, -2.65F, -0.25F);
                    p.rightArmY = Mth.lerp(slam, -0.35F, -0.22F);
                    p.rightArmZ = Mth.lerp(slam, -0.10F, 0.0F);

                    p.leftArmX = Mth.lerp(slam, -2.60F, -0.25F);
                    p.leftArmY = Mth.lerp(slam, +0.35F, +0.22F);
                    p.leftArmZ = Mth.lerp(slam, +0.10F, 0.0F);

                    p.rightLegX = Mth.lerp(slam, 0.0F, -0.24F);
                    p.leftLegX = Mth.lerp(slam, 0.0F, +0.30F);
                    p.rightLegZ = 0.12F;
                    p.leftLegZ = -0.12F;

                    p.rightBladePitchDeg = Mth.rotLerp(slam, SKY_P, SLAM_P);
                    p.rightBladeRollDeg = Mth.rotLerp(slam, SKY_R, SLAM_R);
                    p.rightBladeYawDeg = Mth.rotLerp(slam, SKY_Y, SLAM_Y);
                    p.rightBladeOffX = SKY_OFF_X * (1.0F - slam);
                    p.rightBladeOffY = SKY_OFF_Y * (1.0F - slam);
                    p.rightBladeOffZ = SKY_OFF_Z * (1.0F - slam);
                    p.fovOffsetDeg = Mth.lerp(slam, -5.5F, +9.5F) * (1.0F - Mth.clamp((alpha - 0.80F) / 0.20F, 0.0F, 1.0F));
                }
            }
            case MORGAN_CHANNEL -> {
                // 4.0s Dark Mana Gathering Stance ([R]): rightArm.xRot=-1.10, leftArm.xRot=-1.00, body.xRot=0.10, head.xRot=0.0!
                p.legWeight = 0.80F;
                p.bodyX = 0.10F;
                p.bodyY = 0.0F;
                p.headAddX = 0.0F;

                p.rightArmX = -1.10F;
                p.rightArmY = -0.38F;
                p.rightArmZ = -0.10F;

                p.leftArmX = -1.00F;
                p.leftArmY = +0.42F;
                p.leftArmZ = +0.10F;

                p.rightLegZ = 0.14F;
                p.leftLegZ = -0.14F;
                p.rightLegX = -0.10F;
                p.leftLegX = +0.10F;

                p.rightBladePitchDeg = CHANNEL_P;
                p.rightBladeRollDeg = CHANNEL_R;
                p.rightBladeYawDeg = CHANNEL_Y;
                p.fovOffsetDeg = -6.0F * alpha;
            }
            case MORGAN_AWAKEN -> {
                // Explosive Dark Awakening Burst at 4.0s -> settles cleanly into Saber Alter Idle!
                float burst = easeOutQuart(Mth.clamp(alpha / 0.35F, 0.0F, 1.0F));
                float settle = smootherstep(Mth.clamp((alpha - 0.45F) / 0.55F, 0.0F, 1.0F));
                p.legWeight = 0.85F;

                p.bodyX = Mth.lerp(settle, -0.14F * burst, 0.03F);
                p.bodyY = Mth.lerp(settle, 0.0F, 0.20F);
                p.headAddX = 0.0F;

                p.rightArmX = Mth.lerp(settle, -0.45F, -0.35F);
                p.rightArmY = 0.0F;
                p.rightArmZ = Mth.lerp(settle, -0.70F * burst, -0.25F);

                p.leftArmX = Mth.lerp(settle, -0.35F, -0.15F);
                p.leftArmY = 0.0F;
                p.leftArmZ = Mth.lerp(settle, +0.70F * burst, +0.25F);

                p.rightLegZ = 0.12F;
                p.leftLegZ = -0.12F;

                p.rightBladePitchDeg = MORGAN_IDLE_P;
                p.rightBladeRollDeg = MORGAN_IDLE_R;
                p.rightBladeYawDeg = MORGAN_IDLE_Y;
                p.fovOffsetDeg = Mth.sin(burst * Mth.PI) * 10.0F * (1.0F - settle);
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
