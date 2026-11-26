package com.abyssalrpg.client.anim;

import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.renderer.vfx.SeaWeaponsVfxRenderer;
import com.abyssalrpg.combat.SeaWeaponsMoveType;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Hệ thống Rig Animation Toàn Thân cho cặp vũ khí biển **Hải Thần Đinh Ba (Tay Phải) & Đèn Lồng Biển (Tay Trái)**:
 * - **Đứng / Đi bộ:** Tay phải cầm Đinh Ba uy phong ngang hông-trước; tay trái nâng nhẹ Đèn Lồng Biển phía trước-trái soi đường!
 * - **Chạy nước rút:** Tay phải kẹp Đinh Ba xuôi lùi sau xé gió; tay trái giữ Đèn Lồng gọn bên trước-trái.
 * - **Combo 3 nhịp Đinh Ba (LMB):** Đâm thẳng (`THRUST_1`) -> Quét ngang sóng lam (`SWEEP_2`) -> Giáng đinh ba (`TRIDENT_SLAM_3`).
 * - **Sóng Triều (RMB — Đèn Lồng tay trái):** Tay trái giơ cao Đèn Lồng sáng rực rồi đẩy mạnh về trước dựng Tường Sóng Triều 18 block!
 * - **Xoáy Nước (`[R]` / `Shift+RMB` — Đinh Ba tay phải):** Tay phải xoay giơ cao Đinh Ba rồi đâm cắm thẳng xuống đáy triệu hồi Xoáy Nước!
 */
public final class SeaWeaponsAnimManager {
    private static final float STANCE_BLEND_RATE = 0.16F;
    private static final float SPRINT_BLEND_RATE = 0.18F;

    private static final class PlayerState {
        float prevStance = 0.0F;
        float stance = 0.0F;
        float prevSprint = 0.0F;
        float sprint = 0.0F;

        boolean attacking = false;
        SeaWeaponsMoveType move = SeaWeaponsMoveType.THRUST_1;
        long startTick = -1000L;
        float durationTicks = 10.0F;
        float hitStopLeft = 0.0F;
        float hitStopAcc = 0.0F;

        final FullBodyPose idlePose = new FullBodyPose();
        final FullBodyPose lastComputedPose = new FullBodyPose();
        final FullBodyPose reusablePose = new FullBodyPose();
        long lastSeenTick = 0L;

        float getStance(float pt) {
            return Mth.clamp(Mth.lerp(pt, prevStance, stance), 0.0F, 1.0F);
        }

        float getSprint(float pt) {
            return Mth.clamp(Mth.lerp(pt, prevSprint, sprint), 0.0F, 1.0F);
        }

        float getElapsed(long tick, float pt) {
            float raw = (tick - startTick) + pt;
            float frozen = hitStopAcc + (hitStopLeft > 0.0F ? Math.min(pt, hitStopLeft) : 0.0F);
            return Math.max(0.0F, raw - frozen);
        }

        boolean isActive(long tick, float pt) {
            if (!attacking) return false;
            float e = getElapsed(tick, pt);
            return e >= -0.25F && e < durationTicks;
        }

        float getAlpha(long tick, float pt) {
            if (!attacking || durationTicks <= 0.1F) return 0.0F;
            return Mth.clamp(getElapsed(tick, pt) / durationTicks, 0.0F, 1.0F);
        }
    }

    private static final Map<Integer, PlayerState> STATES = new HashMap<>();
    private static long clientTick = 0L;
    private static ClientLevel lastLevel = null;

    private SeaWeaponsAnimManager() {}

    public static long getClientTick() {
        return clientTick;
    }

    public static boolean isHoldingSeaWeapons(@Nullable Player player) {
        return player != null
                && player.isAlive()
                && player.getMainHandItem().is(ModItems.SEA_TRIDENT)
                && !player.isFallFlying()
                && !player.isSwimming()
                && !player.isSleeping()
                && !player.isAutoSpinAttack();
    }

    public static boolean shouldMirrorLanternOffhand(@Nullable Player player) {
        return player != null
                && player.getMainHandItem().is(ModItems.SEA_TRIDENT)
                && player.getOffhandItem().isEmpty();
    }

    @Nullable
    public static FullBodyPose getLastComputedPose(int playerId) {
        PlayerState st = STATES.get(playerId);
        return (st != null && st.stance > 0.002F) ? st.lastComputedPose : null;
    }

    public static float getActionProgress(int playerId, float pt) {
        PlayerState st = STATES.get(playerId);
        return (st != null && st.isActive(clientTick, pt)) ? st.getAlpha(clientTick, pt) : 0.0F;
    }

    public static SeaWeaponsMoveType getMoveType(int playerId) {
        PlayerState st = STATES.get(playerId);
        return st != null ? st.move : SeaWeaponsMoveType.THRUST_1;
    }

    public static float getSprintBlend(int playerId, float pt) {
        PlayerState st = STATES.get(playerId);
        return st != null ? smootherstep(st.getSprint(pt) * st.getStance(pt)) : 0.0F;
    }

    public static float getCastGlowBoost(int playerId, boolean isLantern) {
        PlayerState st = STATES.get(playerId);
        if (st == null || !st.isActive(clientTick, 0.0F)) return 0.0F;
        float a = st.getAlpha(clientTick, 0.0F);
        if (isLantern && st.move == SeaWeaponsMoveType.LANTERN_TIDAL_WAVE) {
            return Mth.sin(a * Mth.PI);
        }
        if (!isLantern && st.move == SeaWeaponsMoveType.TRIDENT_WHIRLPOOL) {
            return Mth.sin(a * Mth.PI);
        }
        return 0.0F;
    }

    public static void triggerMove(int playerId, SeaWeaponsMoveType move, float durationTicks) {
        PlayerState st = STATES.computeIfAbsent(playerId, id -> new PlayerState());
        st.lastSeenTick = clientTick;
        st.attacking = true;
        st.move = move;
        st.startTick = clientTick;
        st.durationTicks = Mth.clamp(durationTicks, 5.0F, 40.0F);
        st.hitStopLeft = 0.0F;
        st.hitStopAcc = 0.0F;
    }

    public static void triggerHitStop(int playerId, float ticks) {
        PlayerState st = STATES.get(playerId);
        if (st != null && st.attacking) {
            st.hitStopLeft = Math.max(st.hitStopLeft, Mth.clamp(ticks, 0.5F, 3.0F));
        }
    }

    public static void clearAll() {
        STATES.clear();
        SeaWeaponsVfxRenderer.clear();
    }

    public static void tickClient(Minecraft mc) {
        clientTick++;
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            lastLevel = level;
            clearAll();
        }
        if (level == null || mc.isPaused()) return;

        for (Player player : level.players()) {
            boolean holding = isHoldingSeaWeapons(player);
            PlayerState st = STATES.get(player.getId());
            if (!holding && st == null) continue;
            if (st == null) {
                st = new PlayerState();
                STATES.put(player.getId(), st);
            }
            st.lastSeenTick = clientTick;
            st.prevStance = st.stance;
            st.prevSprint = st.sprint;

            if (holding) {
                st.stance = Math.min(1.0F, st.stance + STANCE_BLEND_RATE);
                boolean sprinting = !player.isCrouching() && player.isSprinting()
                        && (player.walkAnimation.speed() > 0.20F || player.getDeltaMovement().horizontalDistanceSqr() > 0.008);
                st.sprint = sprinting
                        ? Math.min(1.0F, st.sprint + SPRINT_BLEND_RATE)
                        : Math.max(0.0F, st.sprint - SPRINT_BLEND_RATE);
                if (st.attacking && st.hitStopLeft > 0.0F) {
                    float step = Math.min(1.0F, st.hitStopLeft);
                    st.hitStopAcc += step;
                    st.hitStopLeft = Math.max(0.0F, st.hitStopLeft - step);
                }
                if (st.attacking && st.getElapsed(clientTick, 0.0F) >= st.durationTicks + 1.0F) {
                    st.attacking = false;
                }
            } else {
                st.stance = Math.max(0.0F, st.stance - STANCE_BLEND_RATE);
                st.sprint = Math.max(0.0F, st.sprint - SPRINT_BLEND_RATE);
                st.attacking = false;
                if (st.stance <= 0.001F) {
                    st.lastComputedPose.reset();
                }
            }
        }
        if ((clientTick & 31L) == 0L && !STATES.isEmpty()) {
            STATES.entrySet().removeIf(e ->
                    (clientTick - e.getValue().lastSeenTick) > 60L
                            || (e.getValue().stance <= 0.001F && e.getValue().prevStance <= 0.001F && !e.getValue().attacking));
        }
    }

    @Nullable
    public static FullBodyPose evaluatePose(Player player, float partialTick, float limbSwingAmount, float ageInTicks) {
        PlayerState st = STATES.get(player.getId());
        if (st == null) return null;
        float stance = smootherstep(st.getStance(partialTick));
        if (stance <= 0.002F) {
            st.lastComputedPose.reset();
            return null;
        }

        float sprint = smootherstep(st.getSprint(partialTick));
        float walkSpeed = Mth.clamp(player.walkAnimation.speed(partialTick), 0.0F, 1.0F);
        float walkPhase = player.walkAnimation.position(partialTick);
        boolean crouching = player.isCrouching();
        boolean hasLantern = player.getOffhandItem().isEmpty();
        FullBodyPose pose = st.reusablePose;
        pose.reset();

        // 1. Tư thế cầm Đinh Ba tự nhiên mềm mại (chuẩn cầm Đinh Ba ngang hông phải với 3 mũi dựng dọc & khuỷu tay gập nhẹ!)
        //    + Tay trái cầm khoen Đèn Lồng Biển buông rủ tự nhiên có độ lắc trọng lực nhẹ!
        buildIdlePose(st.idlePose, stance, sprint, walkSpeed, walkPhase, crouching, hasLantern, ageInTicks);
        pose.copyFrom(st.idlePose);

        // 2. Action Animations (3-Hit Trident Combo + Lantern Tidal Wave + Trident Whirlpool!)
        if (st.isActive(clientTick, partialTick)) {
            float alpha = st.getAlpha(clientTick, partialTick);
            applyActionPose(pose, st.idlePose, st.move, alpha, hasLantern);
        }
        st.lastComputedPose.copyFrom(pose);
        return pose;
    }

    private static void buildIdlePose(FullBodyPose out, float stance, float sprint, float walkSpeed,
                                      float walkPhase, boolean crouching, boolean hasLantern, float ageInTicks) {
        out.reset();
        // Quy chuẩn Chống Rung 0%: giữ tay & vũ khí đứng im chắc tay khi idle (0 lắc nhịp thở!)
        float breathe = 0.0F;
        float forearmLag = 0.0F;
        float walkBlend = walkSpeed * (1.0F - sprint);
        float strideR = Mth.cos(walkPhase * 0.6662F + Mth.PI) * 0.17F * walkBlend;
        float strideL = Mth.cos(walkPhase * 0.6662F) * 0.14F * walkBlend;
        float sprintStride = Mth.cos(walkPhase * 0.6662F) * 0.20F * sprint;
        float crouchDrop = crouching ? 0.10F : 0.0F;

        // Thân & đầu thả lỏng nhẹ tự nhiên (không cứng đơ)
        float rYaw = Mth.lerp(sprint, -6.0F, 0.0F);
        float rPitch = Mth.lerp(sprint, 0.0F, -8.0F);
        out.rootYawDeg = rYaw * stance;
        out.rootPitchDeg = rPitch * stance;
        out.bodyX = Mth.lerp(sprint, 0.04F + breathe * 0.014F, 0.20F) * stance;
        out.bodyY = Mth.lerp(sprint, -0.08F, 0.0F) * stance;
        out.headAddY = (rYaw * Mth.DEG_TO_RAD + 0.08F * (1.0F - sprint)) * stance;
        out.headAddX = (rPitch * Mth.DEG_TO_RAD - breathe * 0.008F) * stance;

        // Thế chân đứng tự nhiên mềm (tự động hòa mượt về bước chân vanilla khi đi bộ/chạy/ngồi!)
        float idleLeg = stance * Mth.clamp(1.0F - walkSpeed * 2.4F, 0.0F, 1.0F) * (1.0F - sprint) * (crouching ? 0.0F : 1.0F);
        out.legWeight = idleLeg;
        out.rightLegX = -0.06F;
        out.rightLegY = 0.0F;
        out.rightLegZ = 0.06F;
        out.leftLegX = 0.07F;
        out.leftLegY = 0.0F;
        out.leftLegZ = -0.06F;

        // Khớp khuỷu 2 khúc mềm mại (TogetsuBentArmRig) cho cả 2 tay & tay áo giáp ngực!
        out.elbowWeight = stance;

        // Tay phải (Hải Thần Đinh Ba): thả lỏng cạnh hông, khuỷu gập tự nhiên ~20°, nắm chặt giữa cán bọc da,
        // 3 mũi đinh ba xòe DỰNG ĐỨNG hướng về trước và đuôi cán vươn gọn ra sau hông chuẩn Đinh Ba Vanilla!
        out.rightArmX = Mth.lerp(sprint, -0.14F + breathe * 0.018F + strideR + crouchDrop, 0.22F - sprintStride);
        out.rightArmY = Mth.lerp(sprint, -0.04F, -0.06F);
        out.rightArmZ = Mth.lerp(sprint, 0.11F + breathe * 0.012F, 0.18F);
        out.rightElbowX = Mth.lerp(sprint, -0.34F + forearmLag * 0.024F - Math.max(0.0F, -strideR) * 0.30F, -0.26F);
        out.rightElbowY = 0.0F;
        out.rightElbowZ = Mth.lerp(sprint, -0.04F, -0.03F);
        out.rightBladePitchDeg = Mth.lerp(sprint, -21.48F - strideR * 12.0F, -32.0F);
        out.rightBladeRollDeg = Mth.lerp(sprint, -0.49F, 4.0F);
        out.rightBladeYawDeg = Mth.lerp(sprint, 101.21F, 102.0F);
        out.rightBladeOffX = -0.0054F;
        out.rightBladeOffY = -0.1542F;
        out.rightBladeOffZ = 0.0F;

        // Tay trái (Đèn Lồng Biển): nâng khuỷu ~26° thoải mái bên hông trái, khoen treo khóa chuẩn cổ tay & lắc nhẹ theo quán tính!
        if (hasLantern) {
            float lSwing = strideL + sprintStride;
            out.leftArmX = Mth.lerp(sprint, -0.22F - breathe * 0.016F + strideL + crouchDrop, -0.34F + sprintStride);
            out.leftArmY = Mth.lerp(sprint, 0.08F, 0.06F);
            out.leftArmZ = Mth.lerp(sprint, -0.14F - breathe * 0.012F, -0.20F);
            out.leftElbowX = Mth.lerp(sprint, -0.44F - forearmLag * 0.022F, -0.38F);
            out.leftElbowY = 0.0F;
            out.leftElbowZ = Mth.lerp(sprint, 0.05F, 0.04F);
            float lanternPendulum = forearmLag * 1.6F - lSwing * Mth.RAD_TO_DEG * 0.62F;
            out.leftBladePitchDeg = Mth.lerp(sprint, 52.90F, 58.0F) + lanternPendulum;
            out.leftBladeRollDeg = 5.19F;
            out.leftBladeYawDeg = 33.67F;
            out.leftBladeOffX = -0.0651F;
            out.leftBladeOffY = -0.0690F;
            out.leftBladeOffZ = 0.0916F;
        } else {
            out.leftArmX = -0.12F + strideL;
            out.leftArmZ = -0.10F;
            out.leftElbowX = -0.22F;
            out.leftElbowZ = 0.03F;
        }
        out.armWeight = stance;
    }

    private static void applyActionPose(FullBodyPose out, FullBodyPose idle, SeaWeaponsMoveType move,
                                        float alpha, boolean hasLantern) {
        float ret = (alpha > 0.78F) ? (1.0F - DualBladeAnimState.easeInOutCubic((alpha - 0.78F) / 0.22F)) : 1.0F;
        out.isAttacking = true;
        out.armWeight = 1.0F;
        out.legWeight = 0.0F;
        // Giữ khớp khuỷu gập tự nhiên nhẹ trong suốt đòn đánh để chuyển động uyển chuyển (0 gãy khớp!)
        out.elbowWeight = 0.65F;
        out.rightBladeYawDeg = 101.21F;
        out.rightBladeRollDeg = -0.49F;
        out.rightBladeOffX = -0.0054F;
        out.rightBladeOffY = -0.1542F;
        out.rightBladeOffZ = 0.0F;

        switch (move) {
            case THRUST_1 -> {
                // Đâm trường thương Đinh Ba thẳng tắp về trước!
                float wind = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.20F, 0.0F, 1.0F));
                float thrust = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.16F) / 0.34F, 0.0F, 1.0F));
                out.bodyY = Mth.lerp(thrust, -0.34F * wind, 0.40F) * ret;
                out.bodyX = Mth.lerp(thrust, 0.02F, 0.18F) * ret;
                out.rightArmX = Mth.lerp(thrust, -0.10F, -1.38F);
                out.rightArmY = Mth.lerp(thrust, -0.20F, -0.08F);
                out.rightArmZ = Mth.lerp(thrust, 0.32F, 0.08F);
                out.rightElbowX = Mth.lerp(thrust, -0.52F, -0.14F);
                out.rightBladePitchDeg = Mth.lerp(thrust, -16.0F, 26.0F);
                out.leftArmX = hasLantern ? Mth.lerp(thrust, -0.52F, -0.26F) : -0.35F;
                out.leftArmZ = -0.24F;
                out.leftElbowX = -0.42F;
            }
            case SWEEP_2 -> {
                // Quét ngang rộng Đinh Ba từ phải sang trái cắt vệt sóng lam!
                float sweep = DualBladeAnimState.easeInOutCubic(Mth.clamp((alpha - 0.10F) / 0.48F, 0.0F, 1.0F));
                out.bodyY = Mth.lerp(sweep, -0.52F, 0.58F) * ret;
                out.rightArmX = Mth.lerp(sweep, -0.95F, -1.08F);
                out.rightArmY = Mth.lerp(sweep, -0.82F, 0.72F);
                out.rightArmZ = Mth.lerp(sweep, 0.58F, -0.22F);
                out.rightElbowX = -0.24F;
                out.rightBladePitchDeg = 12.0F;
                out.rightBladeRollDeg = Mth.lerp(sweep, -28.0F, 36.0F);
                out.leftArmX = hasLantern ? -0.44F : -0.28F;
                out.leftArmZ = -0.28F;
                out.leftElbowX = -0.44F;
            }
            case TRIDENT_SLAM_3 -> {
                // Nâng Đinh Ba lên đỉnh đầu bổ giáng mạnh xuống tạo sóng đập AoE!
                float raise = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.26F, 0.0F, 1.0F));
                float slam = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.24F) / 0.36F, 0.0F, 1.0F));
                out.bodyX = Mth.lerp(slam, -0.12F * raise, 0.32F) * ret;
                out.rightArmX = Mth.lerp(slam, -2.35F * raise, -0.35F);
                out.rightArmY = -0.10F;
                out.rightArmZ = Mth.lerp(slam, 0.24F, 0.12F);
                out.rightElbowX = Mth.lerp(slam, -0.36F, -0.20F);
                out.rightBladePitchDeg = Mth.lerp(slam, -44.0F, 24.0F);
                out.leftArmX = hasLantern ? Mth.lerp(slam, -0.78F, -0.34F) : -0.32F;
                out.leftArmZ = -0.24F;
                out.leftElbowX = -0.44F;
            }
            case LANTERN_TIDAL_WAVE -> {
                // Đèn Lồng Tay Trái giơ cao bùng sáng rồi quét đẩy mạnh về trước triệu hồi SÓNG THẦN khổng lồ!
                float raise = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.28F, 0.0F, 1.0F));
                float surge = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.26F) / 0.34F, 0.0F, 1.0F));
                out.bodyY = Mth.lerp(surge, 0.38F * raise, -0.44F) * ret;
                out.bodyX = Mth.lerp(surge, -0.10F * raise, 0.20F) * ret;
                out.leftArmX = Mth.lerp(surge, -2.08F * raise, -1.28F);
                out.leftArmY = Mth.lerp(surge, -0.18F, 0.14F);
                out.leftArmZ = Mth.lerp(surge, -0.34F, -0.10F);
                out.leftElbowX = Mth.lerp(surge, -0.28F, -0.16F);
                out.leftBladePitchDeg = Mth.lerp(surge, 106.0F * raise, 82.0F);
                // Tay phải giữ Đinh Ba kéo gọn ra sau giữ thăng bằng oai vệ
                out.rightArmX = Mth.lerp(surge, 0.22F * raise, 0.40F);
                out.rightArmZ = 0.30F;
                out.rightElbowX = -0.30F;
                out.rightBladePitchDeg = -34.0F;
            }
            case TRIDENT_WHIRLPOOL -> {
                // Giơ Đinh Ba Tay Phải THẲNG LÊN TRỜI triệu hồi 5 Đinh Ba 3D bay khóa mục tiêu, rồi vung nhẹ chỉ lệnh!
                float raise = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.24F, 0.0F, 1.0F));
                float cmd = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.62F) / 0.20F, 0.0F, 1.0F));
                float tremor = (alpha > 0.20F && alpha < 0.64F) ? (Mth.sin(alpha * 42.0F) * 0.025F) : 0.0F;
                out.bodyX = Mth.lerp(cmd, -0.18F * raise, 0.14F) * ret;
                out.bodyY = Mth.lerp(cmd, -0.22F * raise, 0.24F) * ret;
                out.headAddX = Mth.lerp(cmd, -0.24F * raise, 0.04F) * ret;
                // Tay phải vươn thẳng đứng lên trời (-3.02 rad ≈ -173°), mũi đinh ba chĩa thẳng thiên đỉnh (-90°)
                out.rightArmX = Mth.lerp(cmd, Mth.lerp(raise, -0.14F, -3.02F) + tremor, -1.32F);
                out.rightArmY = Mth.lerp(cmd, -0.06F * raise, 0.06F);
                out.rightArmZ = Mth.lerp(cmd, 0.14F * raise, 0.12F);
                out.rightElbowX = Mth.lerp(cmd, Mth.lerp(raise, -0.34F, -0.10F), -0.18F);
                out.rightBladePitchDeg = Mth.lerp(cmd, Mth.lerp(raise, -21.48F, -90.0F), -10.0F);
                out.rightBladeRollDeg = -0.49F;
                // Tay trái ôm nhẹ Đèn Lồng ngang ngực tạo thế Hải Thần uy nghiêm
                out.leftArmX = hasLantern ? Mth.lerp(cmd, -0.58F * raise, -0.36F) : -0.32F;
                out.leftArmZ = -0.22F;
                out.leftElbowX = -0.52F;
            }
        }
        if (ret < 0.99F) {
            out.blendFrom(idle, ret);
        }
    }

    private static float smootherstep(float t) {
        float x = Mth.clamp(t, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }
}
