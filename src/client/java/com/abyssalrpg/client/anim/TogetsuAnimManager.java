package com.abyssalrpg.client.anim;

import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.renderer.vfx.TogetsuVfxRenderer;
import com.abyssalrpg.combat.TogetsuMoveType;
import com.abyssalrpg.item.TogetsuItem;
import com.abyssalrpg.network.TogetsuSyncPayload;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Hệ thống Rig Animation Toàn Thân 2 Hình Thái cho **Togetsu (Trăng Non & Trăng Khuyết / Hợp Nhất Song Nguyệt)**:
 * 1. **Dạng Song Đao (`!fused`):**
 *    - Pose đứng/đi chuẩn **Sunraku** trong ảnh mẫu (`SUNRAKU_LEFT_ANGLES` + `SUNRAKU_RIGHT_ANGLES` giải 1:1).
 *    - Chạy nhanh (`Sprint`): chuyển mượt sang tư thế chạy **Naruto Run** tốc độ cao, chân chạy hoàn toàn tự nhiên!
 *    - 4 nhịp chém song nguyệt nhanh sắc (`DUAL_1..4`), hòa chân tự nhiên khi vừa chạy vừa chém!
 * 2. **Animation Hợp Nhất (`FUSE_TRANSFORM` 18t) & Tách (`UNFUSE_SPLIT` 12t) khi bấm phím `R`:**
 *    - `FUSE_TRANSFORM`: `0%..50%` kéo 2 cây đao Trắng–Đen xoắn áp sát trước ngực -> tại mốc `50%` lóe sáng biến thành **1 thanh Đại Đao Song Nguyệt khổng lồ** -> `50%..100%` vung nặng vác lên vai vào thế Đại Kiếm!
 *    - `UNFUSE_SPLIT`: `0%..40%` đưa Đại Đao ra trước ngực vặn khớp tách -> tại mốc `40%` tách thành 2 cây đao Trắng–Đen -> `40%..100%` bung 2 tay về tư thế Sunraku!
 * 3. **Dạng Đại Đao Song Nguyệt (`fused`):**
 *    - Pose cầm **Đại Kiếm 2 Tay** chuẩn (`FUSED_BLADE_ANGLES` giải 1:1, tay trái ôm chặt vòng tay cầm D kép).
 *    - 3 nhịp bổ/quét Đại Kiếm cực nặng (`FUSED_1..3`) + Tuyệt kỹ tối thượng **`CRESCENT_VORPAL`** (nén lực 6 tick zoom FOV `-8.5°` rồi chém phóng sóng 22 block bung FOV `+10.5°`!).
 */
public final class TogetsuAnimManager {
    private static final float STANCE_BLEND_IN = 0.24F;
    private static final float STANCE_BLEND_OUT = 0.20F;
    private static final float FUSED_BLEND_RATE = 0.22F;
    private static final float SPRINT_BLEND_IN = 0.24F;
    private static final float SPRINT_BLEND_OUT = 0.20F;

    // Bộ góc lưỡi giải 1:1 khớp TUYỆT ĐỐI thế thủ 2 tầng vắt chéo của Dual Knife (kiểm chứng trong togetsu_poses_preview.png!):
    // - Tay Trái (Trăng Non - Trắng): tầng trên cầm ngược (Reverse Grip), mặt thoáng 100%!
    // - Tay Phải (Trăng Khuyết - Đen): tầng dưới cầm thuận (Standard Grip)!
    private static final float SUNRAKU_LEFT_PITCH = 150.00F;
    private static final float SUNRAKU_LEFT_ROLL = 32.46F;
    private static final float SUNRAKU_LEFT_YAW = -158.63F;

    private static final float SUNRAKU_RIGHT_PITCH = 137.92F;
    private static final float SUNRAKU_RIGHT_ROLL = -44.06F;
    private static final float SUNRAKU_RIGHT_YAW = -173.09F;

    private static final float DUAL_RUN_LEFT_PITCH = -124.15F;
    private static final float DUAL_RUN_LEFT_ROLL = 21.89F;
    private static final float DUAL_RUN_LEFT_YAW = -173.03F;

    private static final float DUAL_RUN_RIGHT_PITCH = -137.34F;
    private static final float DUAL_RUN_RIGHT_ROLL = -24.07F;
    private static final float DUAL_RUN_RIGHT_YAW = 169.38F;

    // Pose Ichigo (vắt Đại Đao Song Nguyệt qua vai phải — mặt thoáng 100%, tay ôm chặt chuôi!)
    private static final float FUSED_BLADE_PITCH = 165.43F;
    private static final float FUSED_BLADE_ROLL = -53.58F;
    private static final float FUSED_BLADE_YAW = 46.52F;

    private static final float FUSED_RUN_BLADE_PITCH = -146.72F;
    private static final float FUSED_RUN_BLADE_ROLL = -21.06F;
    private static final float FUSED_RUN_BLADE_YAW = -30.59F;

    // Pose Gồng Rút Kiếm Zenitsu (Hekireki Issen / Iaijutsu — 3 giây: đại đao áp gọn hông trái vươn sau, 0 cầm ngược!)
    private static final float CHARGE_BLADE_PITCH = 169.26F;
    private static final float CHARGE_BLADE_ROLL = 70.02F;
    private static final float CHARGE_BLADE_YAW = -146.28F;

    // Pose Bùng Nổ Rút Kiếm Ngang (Hekireki Issen Follow-Through — quét phăng 180° từ hông trái sang mạn phải-trước!)
    private static final float VORPAL_SLASH_BLADE_PITCH = -91.53F;
    private static final float VORPAL_SLASH_BLADE_ROLL = -4.34F;
    private static final float VORPAL_SLASH_BLADE_YAW = -60.24F;

    private static final class PlayerState {
        float prevStanceBlend = 0.0F;
        float stanceBlend = 0.0F;
        float prevFusedBlend = 0.0F;
        float fusedBlend = 0.0F;
        float prevSprintBlend = 0.0F;
        float sprintBlend = 0.0F;
        long sprintGraceUntilTick = -1000L;

        boolean attacking = false;
        TogetsuMoveType moveType = TogetsuMoveType.DUAL_1;
        long startTick = -1000L;
        float durationTicks = 11.0F;
        float attackYaw = 0.0F;

        boolean hasTransitionFrom = false;
        final FullBodyPose idlePose = new FullBodyPose();
        final FullBodyPose blendFromPose = new FullBodyPose();
        final FullBodyPose lastComputedPose = new FullBodyPose();
        final FullBodyPose reusablePose = new FullBodyPose();
        long lastSeenTick = 0L;

        float getStanceBlend(float pt) {
            return Mth.clamp(Mth.lerp(pt, prevStanceBlend, stanceBlend), 0.0F, 1.0F);
        }

        float getFusedBlend(float pt) {
            return Mth.clamp(Mth.lerp(pt, prevFusedBlend, fusedBlend), 0.0F, 1.0F);
        }

        float getSprintBlend(float pt) {
            return Mth.clamp(Mth.lerp(pt, prevSprintBlend, sprintBlend), 0.0F, 1.0F);
        }

        boolean isActive(long tick, float pt) {
            if (!attacking) return false;
            float elapsed = (tick - startTick) + pt;
            return elapsed >= -0.25F && elapsed < durationTicks;
        }

        float getAlpha(long tick, float pt) {
            if (!attacking || durationTicks <= 0.1F) return 0.0F;
            float elapsed = (tick - startTick) + pt;
            return Mth.clamp(elapsed / durationTicks, 0.0F, 1.0F);
        }
    }

    private static final Map<Integer, PlayerState> STATES = new HashMap<>();
    private static long clientTick = 0L;
    private static ClientLevel lastLevel = null;

    private TogetsuAnimManager() {}

    public static long getClientTick() {
        return clientTick;
    }

    public static boolean isHoldingTogetsuReady(@Nullable Player player) {
        return player != null
                && player.isAlive()
                && player.getMainHandItem().is(ModItems.TOGETSU)
                && player.getOffhandItem().isEmpty()
                && !player.isFallFlying()
                && !player.isSwimming()
                && !player.isSleeping()
                && !player.isAutoSpinAttack();
    }

    public static boolean isTogetsuAttacking(@Nullable Player player) {
        if (!isHoldingTogetsuReady(player)) {
            return false;
        }
        PlayerState st = STATES.get(player.getId());
        return st != null && st.attacking;
    }

    /**
     * Quyết định thời điểm hiển thị 1 thanh Đại Đao Song Nguyệt (`true`) hay 2 thanh Song Đao (`false`):
     * - Trong pha `FUSE_TRANSFORM`: giữ 2 thanh riêng kéo ghép vào nhau ở `0%..50%`, đổi thành Đại Đao đúng mốc `50%`!
     * - Trong pha `UNFUSE_SPLIT`: giữ Đại Đao đang tách ở `0%..40%`, bung thành 2 thanh ở mốc `40%`!
     */
    public static boolean shouldRenderAsFused(int playerId, ItemStack stack, float partialTick) {
        PlayerState st = STATES.get(playerId);
        if (st != null && st.isActive(clientTick, partialTick)) {
            float alpha = st.getAlpha(clientTick, partialTick);
            if (st.moveType == TogetsuMoveType.FUSE_TRANSFORM) {
                return alpha >= 0.50F;
            }
            if (st.moveType == TogetsuMoveType.UNFUSE_SPLIT) {
                return alpha < 0.40F;
            }
            if (st.moveType == TogetsuMoveType.CRESCENT_VORPAL_CHARGE) {
                return true;
            }
            if (st.moveType == TogetsuMoveType.CRESCENT_VORPAL) {
                // Giữ model Đại Đao xuyên suốt cú chém kết liễu 0%..78% rồi tách về 2 thanh mượt mà!
                return alpha < 0.78F;
            }
        }
        return TogetsuItem.isFused(stack);
    }

    public static boolean shouldMirrorOffhand(Player player, ItemStack mainStack, float partialTick) {
        if (!mainStack.is(ModItems.TOGETSU) || !player.getOffhandItem().isEmpty()) {
            return false;
        }
        return !shouldRenderAsFused(player.getId(), mainStack, partialTick);
    }

    public static float getActionProgress(int playerId, float partialTick) {
        PlayerState st = STATES.get(playerId);
        if (st == null || !st.isActive(clientTick, partialTick)) {
            return 0.0F;
        }
        return st.getAlpha(clientTick, partialTick);
    }

    public static TogetsuMoveType getMoveType(int playerId) {
        PlayerState st = STATES.get(playerId);
        return st != null ? st.moveType : TogetsuMoveType.DUAL_1;
    }

    public static float getSprintBlend(int playerId, float partialTick) {
        PlayerState st = STATES.get(playerId);
        if (st == null) return 0.0F;
        float s = st.getSprintBlend(partialTick) * st.getStanceBlend(partialTick);
        return s * s * (3.0F - 2.0F * s);
    }

    public static void clearAll() {
        STATES.clear();
        TogetsuVfxRenderer.clear();
    }

    public static void tickClient(Minecraft mc) {
        clientTick++;
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            lastLevel = level;
            clearAll();
        }
        if (level == null || mc.isPaused()) {
            return;
        }

        for (Player player : level.players()) {
            boolean ready = isHoldingTogetsuReady(player);
            PlayerState st = STATES.get(player.getId());
            if (!ready && st == null) {
                continue;
            }
            if (st == null) {
                st = new PlayerState();
                boolean fused = TogetsuItem.isFused(player.getMainHandItem());
                st.fusedBlend = fused ? 1.0F : 0.0F;
                st.prevFusedBlend = st.fusedBlend;
                STATES.put(player.getId(), st);
            }
            st.lastSeenTick = clientTick;
            st.prevStanceBlend = st.stanceBlend;
            st.prevFusedBlend = st.fusedBlend;
            st.prevSprintBlend = st.sprintBlend;

            if (ready) {
                st.stanceBlend = Math.min(1.0F, st.stanceBlend + STANCE_BLEND_IN);
                boolean targetFused = shouldRenderAsFused(player.getId(), player.getMainHandItem(), 0.0F);
                st.fusedBlend = targetFused
                        ? Math.min(1.0F, st.fusedBlend + FUSED_BLEND_RATE)
                        : Math.max(0.0F, st.fusedBlend - FUSED_BLEND_RATE);

                boolean movingFast = player.walkAnimation.speed() > 0.22F
                        || player.getDeltaMovement().horizontalDistanceSqr() > 0.008;
                boolean sprintingNow = !player.isCrouching() && player.isSprinting() && movingFast;
                if (sprintingNow) {
                    st.sprintGraceUntilTick = clientTick + 5L;
                }
                boolean sprintActive = !player.isCrouching() && (sprintingNow
                        || (clientTick <= st.sprintGraceUntilTick && movingFast));
                st.sprintBlend = sprintActive
                        ? Math.min(1.0F, st.sprintBlend + SPRINT_BLEND_IN)
                        : Math.max(0.0F, st.sprintBlend - SPRINT_BLEND_OUT);
            } else {
                st.stanceBlend = Math.max(0.0F, st.stanceBlend - STANCE_BLEND_OUT);
                st.sprintBlend = Math.max(0.0F, st.sprintBlend - SPRINT_BLEND_OUT);
                st.attacking = false;
                st.hasTransitionFrom = false;
            }

            if (st.attacking && (clientTick - st.startTick) >= st.durationTicks + 1.5F) {
                st.attacking = false;
                st.hasTransitionFrom = false;
            }
        }

        if ((clientTick & 31L) == 0L && !STATES.isEmpty()) {
            STATES.entrySet().removeIf(e -> {
                PlayerState s = e.getValue();
                return (clientTick - s.lastSeenTick) > 60L
                        || (s.stanceBlend <= 0.001F && s.prevStanceBlend <= 0.001F && !s.attacking);
            });
        }
    }

    public static void triggerLocalOptimistic(LocalPlayer player, TogetsuMoveType move) {
        PlayerState st = STATES.computeIfAbsent(player.getId(), id -> new PlayerState());
        st.lastSeenTick = clientTick;
        startMove(st, player, move, move.baseDisplayTicks, player.getYRot());
    }

    public static void onSyncPacket(ClientLevel level, TogetsuSyncPayload payload) {
        PlayerState st = STATES.computeIfAbsent(payload.playerId(), id -> new PlayerState());
        st.lastSeenTick = clientTick;
        TogetsuMoveType move = TogetsuMoveType.byId(payload.moveId());

        // Bỏ qua lặp animation nếu client vừa dự đoán cục bộ trong vòng 2 tick (nhưng VFX đặc biệt vẫn xử lý ở ClientState!)
        boolean recentSame = st.attacking && st.moveType == move && (clientTick - st.startTick) <= 2L;
        Entity ent = level.getEntity(payload.playerId());
        if (!recentSame || move.isLockedSkill()) {
            // Không restart CRESCENT_VORPAL_CHARGE khi nhận các nhịp mạch năng lượng 10..70 ticks!
            if (move == TogetsuMoveType.CRESCENT_VORPAL_CHARGE
                    && st.attacking && st.moveType == TogetsuMoveType.CRESCENT_VORPAL_CHARGE) {
                return;
            }
            // Không restart DUAL_PIN_FLURRY ở từng gói đâm 1..12 (chỉ bắt đầu 1 lần ở cú đè xuống VFX_PIN_IMPACT!)
            if (move == TogetsuMoveType.DUAL_PIN_FLURRY
                    && (payload.vfxKind() == TogetsuSyncPayload.VFX_PIN_STAB_WAXING
                        || payload.vfxKind() == TogetsuSyncPayload.VFX_PIN_STAB_WANING)
                    && st.attacking && st.moveType == TogetsuMoveType.DUAL_PIN_FLURRY) {
                return;
            }
            startMove(st, ent instanceof Player p ? p : null, move, payload.durationTicks(), payload.yawDeg());
        }
    }

    private static void startMove(PlayerState st, @Nullable Player player, TogetsuMoveType move,
                                  float durationTicks, float yawDeg) {
        boolean chained = st.attacking && (clientTick - st.startTick) < st.durationTicks + 4.0F;
        if (chained) {
            st.blendFromPose.copyFrom(st.lastComputedPose);
            st.hasTransitionFrom = true;
        } else {
            st.hasTransitionFrom = false;
        }
        st.attacking = true;
        st.moveType = move;
        st.startTick = clientTick;
        st.durationTicks = Mth.clamp(durationTicks, 5.5F, 86.0F);
        st.attackYaw = yawDeg;

        if (player != null && !move.isLockedSkill()) {
            TogetsuVfxRenderer.spawnSlashTrail(
                    player.getId(),
                    player.position().add(0.0, player.getBbHeight() * 0.56, 0.0),
                    yawDeg,
                    player.getXRot(),
                    move,
                    st.durationTicks,
                    clientTick
            );
        }
    }

    @Nullable
    public static FullBodyPose evaluatePose(Player player, float partialTick, float limbSwingAmount, float ageInTicks) {
        PlayerState st = STATES.get(player.getId());
        if (st == null) {
            return null;
        }
        float stance = st.getStanceBlend(partialTick);
        if (stance <= 0.002F) {
            return null;
        }

        float fused = st.getFusedBlend(partialTick);
        float sprint = st.getSprintBlend(partialTick);
        float sSmooth = stance * stance * (3.0F - 2.0F * stance);
        float fSmooth = fused * fused * (3.0F - 2.0F * fused);
        float spSmooth = sprint * sprint * (3.0F - 2.0F * sprint);
        float moveAmt = Math.max(limbSwingAmount, player.walkAnimation.speed(partialTick));

        FullBodyPose pose = st.reusablePose;
        pose.reset();

        // 1. Dựng tư thế nghỉ chuẩn (Sunraku Song Đao <-> Đại Đao Song Nguyệt <-> Tư thế Chạy!)
        buildIdlePose(st.idlePose, sSmooth, fSmooth, spSmooth, moveAmt, ageInTicks, player.isCrouching());
        pose.copyFrom(st.idlePose);

        // 2. Đè Rig Animation tấn công / biến hình nếu đang kích hoạt
        if (st.isActive(clientTick, partialTick)) {
            float alpha = st.getAlpha(clientTick, partialTick);
            applyActionPose(pose, st.idlePose, st.moveType, alpha, moveAmt, spSmooth);

            // Nối đà mượt từ tư thế cũ nếu combo liên tiếp
            if (st.hasTransitionFrom && alpha < 0.22F) {
                float chainT = DualBladeAnimState.easeOutCubic(alpha / 0.22F);
                pose.blendFrom(st.blendFromPose, chainT);
            }
            if (sSmooth < 0.99F) {
                scalePose(pose, sSmooth);
            }
        }

        st.lastComputedPose.copyFrom(pose);
        return pose;
    }

    private static void buildIdlePose(FullBodyPose out, float stance, float fused, float sprint,
                                      float limbSwingAmount, float ageInTicks, boolean crouching) {
        out.reset();
        float breathe = Mth.sin(ageInTicks * 0.11F);
        float moveFrac = Mth.clamp(limbSwingAmount * 2.1F, 0.0F, 1.0F);
        float stillFrac = 1.0F - moveFrac;
        float crouchScale = crouching ? 0.55F : 1.0F;

        // A. Tư thế Song Đao vắt chéo 2 tầng giống 1:1 với Dual Knife (tầng trên tay trái cầm ngược, tầng dưới tay phải cầm thuận!)
        float dRootPitch = -6.0F * crouchScale;
        float dRootY = -0.060F * crouchScale + breathe * 0.008F;
        float dBodyX = 0.20F * crouchScale + breathe * 0.015F;
        float dBodyY = 0.22F;
        float dHeadX = 0.32F * crouchScale;
        float dHeadY = -0.18F;
        float dLArmX = -1.54F + breathe * 0.018F;
        float dLArmY = 0.92F;
        float dLArmZ = -0.15F - breathe * 0.008F;
        float dRArmX = -0.78F - breathe * 0.018F;
        float dRArmY = -0.96F;
        float dRArmZ = 0.26F + breathe * 0.008F;

        // Chạy kiểu Sunraku trong Shangri-La Frontier (dạng Song Đao: nghiêng săn mồi 18°, 2 lưỡi lướt gọn dọc hông đánh nhịp cực tự nhiên!)
        float runStride = Mth.sin(ageInTicks * 0.62F) * sprint;
        if (sprint > 0.001F) {
            dRootPitch = Mth.lerp(sprint, dRootPitch, -18.0F);
            dRootY = Mth.lerp(sprint, dRootY, -0.04F);
            dBodyX = Mth.lerp(sprint, dBodyX, 0.08F);
            dBodyY = Mth.lerp(sprint, dBodyY, 0.08F + runStride * 0.08F);
            dHeadX = Mth.lerp(sprint, dHeadX, -0.26F);
            dHeadY = Mth.lerp(sprint, dHeadY, 0.0F);
            dLArmX = Mth.lerp(sprint, dLArmX, 0.76F - runStride * 0.14F);
            dLArmY = Mth.lerp(sprint, dLArmY, -0.14F);
            dLArmZ = Mth.lerp(sprint, dLArmZ, -0.22F);
            dRArmX = Mth.lerp(sprint, dRArmX, 0.46F + runStride * 0.14F);
            dRArmY = Mth.lerp(sprint, dRArmY, 0.16F);
            dRArmZ = Mth.lerp(sprint, dRArmZ, 0.26F);
        }

        // B. Tư thế Đứng Ichigo (Shikai Zangetsu — khớp ảnh mẫu!):
        //    - Đứng thẳng tự tin xoay 3/4, mặt thoáng 100%, tay trái thả lỏng tự nhiên xuôi bên hông.
        //    - Tay phải giơ sát ngang thái dương phải nắm chặt chuôi Đại Đao vắt vòm cao qua sau vai trái!
        float fRootPitch = -1.5F * crouchScale;
        float fRootY = -0.01F + breathe * 0.006F;
        float fBodyX = 0.02F * crouchScale + breathe * 0.008F;
        float fBodyY = 0.16F;
        float fBodyZ = 0.0F;
        float fHeadX = 0.04F * crouchScale;
        float fHeadY = -0.16F;
        float fRArmX = 0.26F + breathe * 0.016F;
        float fRArmY = -0.14F;
        float fRArmZ = 2.62F - breathe * 0.014F;
        float fLArmX = 0.08F - breathe * 0.016F;
        float fLArmY = 0.0F;
        float fLArmZ = -0.14F;

        // Dáng chạy Đại Đao chuẩn Sunraku (đổ người lao 17°, tay phải kéo Đại Đao vút gọn ngoài mạn phải, tay trái đánh nhịp cân bằng!)
        if (sprint > 0.001F) {
            fRootPitch = Mth.lerp(sprint, fRootPitch, -17.0F);
            fRootY = Mth.lerp(sprint, fRootY, -0.04F);
            fBodyX = Mth.lerp(sprint, fBodyX, 0.08F);
            fBodyY = Mth.lerp(sprint, fBodyY, 0.16F + runStride * 0.08F);
            fBodyZ = 0.0F;
            fHeadX = Mth.lerp(sprint, fHeadX, -0.24F);
            fHeadY = Mth.lerp(sprint, fHeadY, -0.14F);
            fRArmX = Mth.lerp(sprint, fRArmX, 0.74F + runStride * 0.08F);
            fRArmY = Mth.lerp(sprint, fRArmY, -0.16F);
            fRArmZ = Mth.lerp(sprint, fRArmZ, 0.28F);
            fLArmX = Mth.lerp(sprint, fLArmX, -0.54F - runStride * 0.22F);
            fLArmY = Mth.lerp(sprint, fLArmY, 0.14F);
            fLArmZ = Mth.lerp(sprint, fLArmZ, -0.18F);
        }

        // Hòa mượt giữa 2 dạng!
        out.rootPitchDeg = Mth.lerp(fused, dRootPitch, fRootPitch) * stance;
        out.rootTranslateY = Mth.lerp(fused, dRootY, fRootY) * stance;
        out.bodyX = Mth.lerp(fused, dBodyX, fBodyX) * stance;
        out.bodyY = Mth.lerp(fused, dBodyY, fBodyY) * stance;
        out.bodyZ = Mth.lerp(fused, 0.0F, fBodyZ) * stance;
        out.headAddX = Mth.lerp(fused, dHeadX, fHeadX) * stance;
        out.headAddY = Mth.lerp(fused, dHeadY, fHeadY) * stance;

        out.rightArmX = Mth.lerp(fused, dRArmX, fRArmX);
        out.rightArmY = Mth.lerp(fused, dRArmY, fRArmY);
        out.rightArmZ = Mth.lerp(fused, dRArmZ, fRArmZ);

        out.leftArmX = Mth.lerp(fused, dLArmX, fLArmX);
        out.leftArmY = Mth.lerp(fused, dLArmY, fLArmY);
        out.leftArmZ = Mth.lerp(fused, dLArmZ, fLArmZ);

        out.armWeight = stance;

        // Chân thế đứng cân đối tự nhiên khi đứng yên (khớp thế tấn Dual Knife!), trả 100% bước chạy tự nhiên khi di chuyển!
        out.leftLegX = Mth.lerp(fused, -0.26F, -0.12F);
        out.leftLegY = Mth.lerp(fused, 0.22F, 0.10F);
        out.leftLegZ = Mth.lerp(fused, -0.14F, -0.08F);
        out.rightLegX = Mth.lerp(fused, 0.30F, 0.14F);
        out.rightLegY = Mth.lerp(fused, -0.22F, -0.10F);
        out.rightLegZ = Mth.lerp(fused, 0.14F, 0.08F);
        out.legWeight = stance * stillFrac * (1.0F - sprint);

        // Góc lưỡi 1:1 giải chính xác cho cả đứng & chạy Sunraku ở 2 dạng!
        float dualRPitch = Mth.rotLerp(sprint, SUNRAKU_RIGHT_PITCH, DUAL_RUN_RIGHT_PITCH);
        float dualRRoll = Mth.rotLerp(sprint, SUNRAKU_RIGHT_ROLL, DUAL_RUN_RIGHT_ROLL);
        float dualRYaw = Mth.rotLerp(sprint, SUNRAKU_RIGHT_YAW, DUAL_RUN_RIGHT_YAW);

        float dualLPitch = Mth.rotLerp(sprint, SUNRAKU_LEFT_PITCH, DUAL_RUN_LEFT_PITCH);
        float dualLRoll = Mth.rotLerp(sprint, SUNRAKU_LEFT_ROLL, DUAL_RUN_LEFT_ROLL);
        float dualLYaw = Mth.rotLerp(sprint, SUNRAKU_LEFT_YAW, DUAL_RUN_LEFT_YAW);

        float fusedRPitch = Mth.rotLerp(sprint, FUSED_BLADE_PITCH, FUSED_RUN_BLADE_PITCH);
        float fusedRRoll = Mth.rotLerp(sprint, FUSED_BLADE_ROLL, FUSED_RUN_BLADE_ROLL);
        float fusedRYaw = Mth.rotLerp(sprint, FUSED_BLADE_YAW, FUSED_RUN_BLADE_YAW);

        out.rightBladePitchDeg = Mth.rotLerp(fused, dualRPitch, fusedRPitch);
        out.rightBladeRollDeg = Mth.rotLerp(fused, dualRRoll, fusedRRoll);
        out.rightBladeYawDeg = Mth.rotLerp(fused, dualRYaw, fusedRYaw);

        out.leftBladePitchDeg = dualLPitch;
        out.leftBladeRollDeg = dualLRoll;
        out.leftBladeYawDeg = dualLYaw;
    }

    private static void applyActionPose(FullBodyPose out, FullBodyPose idle, TogetsuMoveType move,
                                        float alpha, float limbSwingAmount, float sprint) {
        float env = DualBladeAnimState.poseHoldEnvelope(alpha);
        float sweep = DualBladeAnimState.poseHoldSweep(alpha);
        float moveFrac = Mth.clamp(limbSwingAmount * 2.0F, 0.0F, 1.0F);
        float standLegWeight = (1.0F - moveFrac * 0.88F) * (1.0F - sprint);

        out.isAttacking = true;
        out.armWeight = 1.0F;

        switch (move) {
            case DUAL_1 -> {
                // Nhịp 1 Song Đao (Trăng Khuyết Tay Phải chém chéo rộng Phải-Trên -> Trái-Dưới)
                float bodyYaw = Mth.lerp(sweep, -0.58F, 0.72F);
                out.rootPitchDeg = Mth.lerp(env, idle.rootPitchDeg, -14.0F);
                out.bodyX = Mth.lerp(env, idle.bodyX, 0.24F);
                out.bodyY = Mth.lerp(env, idle.bodyY, bodyYaw);
                out.headAddY = -bodyYaw * 0.55F * env;

                out.rightArmX = Mth.lerp(env, idle.rightArmX, Mth.lerp(sweep, -2.35F, -0.45F));
                out.rightArmY = Mth.lerp(env, idle.rightArmY, Mth.lerp(sweep, -0.82F, 0.96F));
                out.rightArmZ = Mth.lerp(env, idle.rightArmZ, Mth.lerp(sweep, 0.65F, -0.35F));

                out.leftArmX = Mth.lerp(env, idle.leftArmX, -0.85F);
                out.leftArmY = Mth.lerp(env, idle.leftArmY, 0.45F);
                out.leftArmZ = Mth.lerp(env, idle.leftArmZ, -0.42F);

                out.rightBladePitchDeg = Mth.rotLerp(env, idle.rightBladePitchDeg, -22.0F);
                out.rightBladeRollDeg = Mth.rotLerp(env, idle.rightBladeRollDeg, 62.0F);
                out.rightBladeYawDeg = Mth.rotLerp(env, idle.rightBladeYawDeg, -18.0F);
                out.firstPersonCameraRollDeg = -2.2F * env;
                applyAttackLegs(out, idle, env * standLegWeight, -0.48F, 0.52F, 0.16F);
            }
            case DUAL_2 -> {
                // Nhịp 2 Song Đao (Trăng Non Tay Trắng quét rộng Trái -> Phải ngược chiều)
                float bodyYaw = Mth.lerp(sweep, 0.62F, -0.74F);
                out.rootPitchDeg = Mth.lerp(env, idle.rootPitchDeg, -13.0F);
                out.bodyX = Mth.lerp(env, idle.bodyX, 0.22F);
                out.bodyY = Mth.lerp(env, idle.bodyY, bodyYaw);
                out.headAddY = -bodyYaw * 0.55F * env;

                out.leftArmX = Mth.lerp(env, idle.leftArmX, Mth.lerp(sweep, -2.20F, -0.52F));
                out.leftArmY = Mth.lerp(env, idle.leftArmY, Mth.lerp(sweep, 0.85F, -0.96F));
                out.leftArmZ = Mth.lerp(env, idle.leftArmZ, Mth.lerp(sweep, -0.68F, 0.36F));

                out.rightArmX = Mth.lerp(env, idle.rightArmX, -0.80F);
                out.rightArmY = Mth.lerp(env, idle.rightArmY, -0.42F);
                out.rightArmZ = Mth.lerp(env, idle.rightArmZ, 0.40F);

                out.leftBladePitchDeg = Mth.rotLerp(env, idle.leftBladePitchDeg, -24.0F);
                out.leftBladeRollDeg = Mth.rotLerp(env, idle.leftBladeRollDeg, -62.0F);
                out.leftBladeYawDeg = Mth.rotLerp(env, idle.leftBladeYawDeg, 165.0F);
                out.firstPersonCameraRollDeg = 2.4F * env;
                applyAttackLegs(out, idle, env * standLegWeight, 0.52F, -0.46F, 0.16F);
            }
            case DUAL_3 -> {
                // Nhịp 3 Song Đao (Âm–Dương Song Nguyệt: 2 lưỡi Trắng–Đen xé ngang đồng thời!)
                out.rootPitchDeg = Mth.lerp(env, idle.rootPitchDeg, -16.0F);
                out.bodyX = Mth.lerp(env, idle.bodyX, 0.28F);
                out.bodyY = Mth.lerp(env, idle.bodyY, Mth.lerp(sweep, -0.45F, 0.45F));

                out.rightArmX = Mth.lerp(env, idle.rightArmX, Mth.lerp(sweep, -1.60F, -0.92F));
                out.rightArmY = Mth.lerp(env, idle.rightArmY, Mth.lerp(sweep, 0.72F, -1.05F));
                out.rightArmZ = Mth.lerp(env, idle.rightArmZ, Mth.lerp(sweep, -0.25F, 0.82F));

                out.leftArmX = Mth.lerp(env, idle.leftArmX, Mth.lerp(sweep, -1.60F, -0.92F));
                out.leftArmY = Mth.lerp(env, idle.leftArmY, Mth.lerp(sweep, -0.72F, 1.05F));
                out.leftArmZ = Mth.lerp(env, idle.leftArmZ, Mth.lerp(sweep, 0.25F, -0.82F));

                out.rightBladePitchDeg = Mth.rotLerp(env, idle.rightBladePitchDeg, -12.0F);
                out.rightBladeRollDeg = Mth.rotLerp(env, idle.rightBladeRollDeg, 78.0F);
                out.leftBladePitchDeg = Mth.rotLerp(env, idle.leftBladePitchDeg, -12.0F);
                out.leftBladeRollDeg = Mth.rotLerp(env, idle.leftBladeRollDeg, -78.0F);
                applyAttackLegs(out, idle, env * standLegWeight, -0.42F, 0.48F, 0.24F);
            }
            case DUAL_4 -> {
                // Nhịp 4 Finisher Song Đao (Bổ Chéo Trăng Khuyết Chữ X!)
                float wind = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.26F, 0.0F, 1.0F));
                float slam = alpha < 0.24F ? 0.0F : (alpha < 0.50F ? DualBladeAnimState.easeOutQuart((alpha - 0.24F) / 0.26F) : 1.0F);
                out.rootPitchDeg = Mth.lerp(env, idle.rootPitchDeg, Mth.lerp(slam, 10.0F * wind, -20.0F));
                out.rootTranslateY = Mth.lerp(env, idle.rootTranslateY, Mth.lerp(slam, 0.08F * wind, -0.08F));
                out.bodyX = Mth.lerp(env, idle.bodyX, Mth.lerp(slam, -0.18F * wind, 0.36F));

                out.rightArmX = Mth.lerp(env, idle.rightArmX, Mth.lerp(slam, -2.65F * wind, -0.58F));
                out.rightArmY = Mth.lerp(env, idle.rightArmY, Mth.lerp(slam, -0.40F * wind, 0.72F));
                out.rightArmZ = Mth.lerp(env, idle.rightArmZ, Mth.lerp(slam, 0.65F * wind, -0.22F));

                out.leftArmX = Mth.lerp(env, idle.leftArmX, Mth.lerp(slam, -2.65F * wind, -0.58F));
                out.leftArmY = Mth.lerp(env, idle.leftArmY, Mth.lerp(slam, 0.40F * wind, -0.72F));
                out.leftArmZ = Mth.lerp(env, idle.leftArmZ, Mth.lerp(slam, -0.65F * wind, 0.22F));

                out.rightBladePitchDeg = Mth.rotLerp(env, idle.rightBladePitchDeg, -36.0F);
                out.leftBladePitchDeg = Mth.rotLerp(env, idle.leftBladePitchDeg, -36.0F);
                out.fovOffsetDeg = Mth.lerp(slam, -2.0F * wind, 3.5F) * env;
                applyAttackLegs(out, idle, env * standLegWeight, -0.55F, 0.55F, 0.22F);
            }
            case FUSE_TRANSFORM -> {
                // Animation Hợp Nhất khi bấm [R] (18 ticks):
                // 0..50%: Kéo 2 cây đao Trắng–Đen xoắn áp chặt giữa ngực -> 50% LÓE SÁNG GHÉP KHỚP -> 50..100%: Vung nâng Đại Đao Song Nguyệt lên vai!
                if (alpha < 0.50F) {
                    float t = DualBladeAnimState.easeInOutCubic(alpha / 0.50F);
                    out.rootPitchDeg = Mth.lerp(t, -6.0F, -12.0F);
                    out.rootTranslateY = -0.06F;
                    out.bodyX = Mth.lerp(t, 0.20F, 0.24F);
                    out.bodyY = Mth.lerp(t, 0.22F, 0.0F);
                    out.rightArmX = Mth.lerp(t, -0.78F, -1.55F);
                    out.rightArmY = Mth.lerp(t, -0.96F, 0.58F);
                    out.rightArmZ = Mth.lerp(t, 0.26F, -0.12F);
                    out.leftArmX = Mth.lerp(t, -1.54F, -1.55F);
                    out.leftArmY = Mth.lerp(t, 0.92F, -0.58F);
                    out.leftArmZ = Mth.lerp(t, -0.15F, 0.12F);
                    out.rightBladePitchDeg = Mth.rotLerp(t, SUNRAKU_RIGHT_PITCH, -18.0F);
                    out.rightBladeRollDeg = Mth.rotLerp(t, SUNRAKU_RIGHT_ROLL, 90.0F);
                    out.leftBladePitchDeg = Mth.rotLerp(t, SUNRAKU_LEFT_PITCH, -18.0F);
                    out.leftBladeRollDeg = Mth.rotLerp(t, SUNRAKU_LEFT_ROLL, -90.0F);
                    out.fovOffsetDeg = -5.0F * t;
                } else {
                    float settle = DualBladeAnimState.easeOutCubic((alpha - 0.50F) / 0.50F);
                    out.rootPitchDeg = Mth.lerp(settle, -12.0F, -1.5F);
                    out.rootTranslateY = Mth.lerp(settle, -0.06F, -0.01F);
                    out.bodyX = Mth.lerp(settle, 0.20F, 0.02F);
                    out.bodyY = Mth.lerp(settle, -0.24F, 0.16F);
                    out.bodyZ = 0.0F;
                    out.headAddX = Mth.lerp(settle, 0.10F, 0.04F);
                    out.headAddY = Mth.lerp(settle, 0.0F, -0.16F);
                    // Vung tay phải vắt Đại Đao Song Nguyệt qua vai kiểu Ichigo & thả lỏng tay trái bên hông!
                    out.rightArmX = Mth.lerp(settle, -1.55F, 0.26F);
                    out.rightArmY = Mth.lerp(settle, 0.45F, -0.14F);
                    out.rightArmZ = Mth.lerp(settle, -0.12F, 2.62F);
                    out.leftArmX = Mth.lerp(settle, -1.55F, 0.08F);
                    out.leftArmY = Mth.lerp(settle, -0.45F, 0.0F);
                    out.leftArmZ = Mth.lerp(settle, 0.12F, -0.14F);
                    out.rightBladePitchDeg = Mth.rotLerp(settle, -12.0F, FUSED_BLADE_PITCH);
                    out.rightBladeRollDeg = Mth.rotLerp(settle, 15.0F, FUSED_BLADE_ROLL);
                    out.rightBladeYawDeg = Mth.rotLerp(settle, -40.0F, FUSED_BLADE_YAW);
                    out.fovOffsetDeg = (1.0F - settle) * 6.5F;
                }
                applyAttackLegs(out, idle, standLegWeight * 0.90F, 0.14F, -0.12F, 0.08F);
            }
            case UNFUSE_SPLIT -> {
                // Animation Tách Song Nguyệt -> Song Đao (12 ticks)
                if (alpha < 0.40F) {
                    float t = DualBladeAnimState.easeInOutCubic(alpha / 0.40F);
                    out.rightArmX = Mth.lerp(t, 0.26F, -1.52F);
                    out.rightArmY = Mth.lerp(t, -0.14F, 0.45F);
                    out.rightArmZ = Mth.lerp(t, 2.62F, -0.10F);
                    out.leftArmX = Mth.lerp(t, 0.08F, -1.52F);
                    out.leftArmY = Mth.lerp(t, 0.0F, -0.45F);
                    out.leftArmZ = Mth.lerp(t, -0.14F, 0.10F);
                    out.rightBladePitchDeg = Mth.rotLerp(t, FUSED_BLADE_PITCH, 0.0F);
                    out.rightBladeRollDeg = Mth.rotLerp(t, FUSED_BLADE_ROLL, 0.0F);
                    out.rightBladeYawDeg = Mth.rotLerp(t, FUSED_BLADE_YAW, 0.0F);
                } else {
                    float t = DualBladeAnimState.easeOutCubic((alpha - 0.40F) / 0.60F);
                    float spread = Mth.sin(t * Mth.PI);
                    out.rightArmX = Mth.lerp(t, -1.35F, -0.78F);
                    out.rightArmY = Mth.lerp(t, -0.65F, -0.96F) - spread * 0.35F;
                    out.rightArmZ = Mth.lerp(t, 0.65F, 0.26F) + spread * 0.35F;
                    out.leftArmX = Mth.lerp(t, -1.35F, -1.54F);
                    out.leftArmY = Mth.lerp(t, 0.65F, 0.92F) + spread * 0.35F;
                    out.leftArmZ = Mth.lerp(t, -0.65F, -0.15F) - spread * 0.35F;
                    out.rightBladePitchDeg = SUNRAKU_RIGHT_PITCH;
                    out.rightBladeRollDeg = SUNRAKU_RIGHT_ROLL;
                    out.rightBladeYawDeg = SUNRAKU_RIGHT_YAW;
                    out.leftBladePitchDeg = SUNRAKU_LEFT_PITCH;
                    out.leftBladeRollDeg = SUNRAKU_LEFT_ROLL;
                    out.leftBladeYawDeg = SUNRAKU_LEFT_YAW;
                }
            }
            case FUSED_1 -> {
                // Đại Đao Nhịp 1: Bổ chéo 2 tay từ vai phải trên giáng cực mạnh xuống trái dưới!
                float wind = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.24F, 0.0F, 1.0F));
                float cut = alpha < 0.20F ? 0.0F : (alpha < 0.50F ? DualBladeAnimState.easeOutQuart((alpha - 0.20F) / 0.30F) : 1.0F);
                float bodyYaw = Mth.lerp(cut, -0.52F * wind, 0.78F);

                out.rootPitchDeg = Mth.lerp(env, idle.rootPitchDeg, Mth.lerp(cut, 8.0F * wind, -18.0F));
                out.bodyX = Mth.lerp(env, idle.bodyX, Mth.lerp(cut, -0.14F * wind, 0.32F));
                out.bodyY = Mth.lerp(env, idle.bodyY, bodyYaw);
                out.headAddY = -bodyYaw * 0.55F * env;

                // 2 tay khóa chặt D-loop cầm Đại Kiếm giáng từ trên xuống!
                out.rightArmX = Mth.lerp(env, idle.rightArmX, Mth.lerp(cut, -2.75F * wind, -0.45F));
                out.rightArmY = Mth.lerp(env, idle.rightArmY, Mth.lerp(cut, -0.35F * wind, 0.85F));
                out.rightArmZ = Mth.lerp(env, idle.rightArmZ, Mth.lerp(cut, 0.42F * wind, -0.25F));

                out.leftArmX = Mth.lerp(env, idle.leftArmX, Mth.lerp(cut, -2.55F * wind, -0.58F));
                out.leftArmY = Mth.lerp(env, idle.leftArmY, Mth.lerp(cut, 0.65F * wind, 0.95F));
                out.leftArmZ = Mth.lerp(env, idle.leftArmZ, Mth.lerp(cut, -0.30F * wind, -0.35F));

                out.rightBladePitchDeg = Mth.rotLerp(env, idle.rightBladePitchDeg, Mth.lerp(cut, -45.0F, 24.0F));
                out.rightBladeRollDeg = Mth.rotLerp(env, idle.rightBladeRollDeg, 22.0F);
                out.rightBladeYawDeg = Mth.rotLerp(env, idle.rightBladeYawDeg, -18.0F);
                out.firstPersonCameraRollDeg = -3.0F * env;
                applyAttackLegs(out, idle, env * standLegWeight, -0.58F, 0.56F, 0.22F);
            }
            case FUSED_2 -> {
                // Đại Đao Nhịp 2: Quét ngang 2 tay 180° khổng lồ từ Trái-Dưới vung sang Phải-Trên!
                float bodyYaw = Mth.lerp(sweep, 0.82F, -0.84F);
                out.rootPitchDeg = Mth.lerp(env, idle.rootPitchDeg, -14.0F);
                out.bodyX = Mth.lerp(env, idle.bodyX, 0.26F);
                out.bodyY = Mth.lerp(env, idle.bodyY, bodyYaw);
                out.headAddY = -bodyYaw * 0.55F * env;

                out.rightArmX = Mth.lerp(env, idle.rightArmX, Mth.lerp(sweep, -1.10F, -1.65F));
                out.rightArmY = Mth.lerp(env, idle.rightArmY, Mth.lerp(sweep, 1.05F, -1.12F));
                out.rightArmZ = Mth.lerp(env, idle.rightArmZ, Mth.lerp(sweep, -0.45F, 0.68F));

                out.leftArmX = Mth.lerp(env, idle.leftArmX, Mth.lerp(sweep, -1.18F, -1.48F));
                out.leftArmY = Mth.lerp(env, idle.leftArmY, Mth.lerp(sweep, 0.92F, -0.25F));
                out.leftArmZ = Mth.lerp(env, idle.leftArmZ, Mth.lerp(sweep, -0.35F, 0.28F));

                out.rightBladePitchDeg = Mth.rotLerp(env, idle.rightBladePitchDeg, -12.0F);
                out.rightBladeRollDeg = Mth.rotLerp(env, idle.rightBladeRollDeg, -78.0F);
                out.rightBladeYawDeg = Mth.rotLerp(env, idle.rightBladeYawDeg, -32.0F);
                out.firstPersonCameraRollDeg = 3.2F * env;
                applyAttackLegs(out, idle, env * standLegWeight, 0.56F, -0.52F, 0.24F);
            }
            case FUSED_3 -> {
                // Đại Đao Nhịp 3: Quét ngược hất chéo Song Nguyệt từ Phải-Thấp lên Trái-Cao!
                float bodyYaw = Mth.lerp(sweep, -0.78F, 0.72F);
                out.rootPitchDeg = Mth.lerp(env, idle.rootPitchDeg, Mth.lerp(sweep, -18.0F, 6.0F));
                out.bodyX = Mth.lerp(env, idle.bodyX, Mth.lerp(sweep, 0.32F, -0.08F));
                out.bodyY = Mth.lerp(env, idle.bodyY, bodyYaw);
                out.headAddY = -bodyYaw * 0.55F * env;

                out.rightArmX = Mth.lerp(env, idle.rightArmX, Mth.lerp(sweep, -0.42F, -2.42F));
                out.rightArmY = Mth.lerp(env, idle.rightArmY, Mth.lerp(sweep, -0.85F, 0.78F));
                out.rightArmZ = Mth.lerp(env, idle.rightArmZ, Mth.lerp(sweep, 0.58F, -0.32F));

                out.leftArmX = Mth.lerp(env, idle.leftArmX, Mth.lerp(sweep, -0.55F, -2.20F));
                out.leftArmY = Mth.lerp(env, idle.leftArmY, Mth.lerp(sweep, 0.35F, 0.92F));
                out.leftArmZ = Mth.lerp(env, idle.leftArmZ, -0.20F);

                out.rightBladePitchDeg = Mth.rotLerp(env, idle.rightBladePitchDeg, -26.0F);
                out.rightBladeRollDeg = Mth.rotLerp(env, idle.rightBladeRollDeg, 58.0F);
                out.rightBladeYawDeg = Mth.rotLerp(env, idle.rightBladeYawDeg, -24.0F);
                out.firstPersonCameraRollDeg = -3.4F * env;
                applyAttackLegs(out, idle, env * standLegWeight, -0.52F, 0.58F, 0.24F);
            }
            case CRESCENT_VORPAL_CHARGE -> {
                // PHA GỒNG LỰC RÚT KIẾM ZENITSU 3 GIÂY (60 TICKS — HEKIREKI ISSEN / IAIJUTSU CROUCH DRAW):
                // - Khuỵu thấp dồn trọng tâm, chân trái trụ trước, chân phải đạp dài ra sau như lò xo!
                // - Tay phải vắt ngang bụng xuống HÔNG TRÁI nắm chắc cán kiếm, tay trái giữ sát cổ kiếm hông trái!
                // - Đại đao nằm xuôi gọn dọc hông trái vươn ra sau-trái (0 cầm ngược, 0 xuyên thân), rung điện tăng dần, FOV co -11°!
                float enter = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.16F, 0.0F, 1.0F));
                float vib = Mth.sin(alpha * 135.0F) * (0.012F + 0.036F * alpha);
                out.rootPitchDeg = Mth.lerp(enter, idle.rootPitchDeg, -25.0F - alpha * 3.0F);
                out.rootTranslateY = Mth.lerp(enter, idle.rootTranslateY, -0.28F - alpha * 0.04F);
                out.bodyX = Mth.lerp(enter, idle.bodyX, 0.34F + alpha * 0.06F);
                out.bodyY = Mth.lerp(enter, idle.bodyY, -0.32F - alpha * 0.08F);
                out.bodyZ = Mth.lerp(enter, idle.bodyZ, 0.04F);
                out.headAddX = Mth.lerp(enter, idle.headAddX, -0.34F);
                out.headAddY = Mth.lerp(enter, idle.headAddY, 0.26F + alpha * 0.06F);

                out.rightArmX = Mth.lerp(enter, idle.rightArmX, -0.64F + vib);
                out.rightArmY = Mth.lerp(enter, idle.rightArmY, -0.82F - alpha * 0.04F);
                out.rightArmZ = Mth.lerp(enter, idle.rightArmZ, -0.48F + vib * 0.7F);

                out.leftArmX = Mth.lerp(enter, idle.leftArmX, -0.36F - vib * 0.6F);
                out.leftArmY = Mth.lerp(enter, idle.leftArmY, 0.18F);
                out.leftArmZ = Mth.lerp(enter, idle.leftArmZ, -0.14F);

                out.rightBladePitchDeg = Mth.rotLerp(enter, idle.rightBladePitchDeg, CHARGE_BLADE_PITCH);
                out.rightBladeRollDeg = Mth.rotLerp(enter, idle.rightBladeRollDeg, CHARGE_BLADE_ROLL);
                out.rightBladeYawDeg = Mth.rotLerp(enter, idle.rightBladeYawDeg, CHARGE_BLADE_YAW);
                out.firstPersonCameraRollDeg = vib * 26.0F;
                out.fovOffsetDeg = -11.0F * (0.28F * enter + 0.72F * alpha);

                // Giữ chặt thế khuỵu tấn rút kiếm Zenitsu (chân trái bước sâu trước, chân phải đạp dài sau!)
                out.legWeight = enter;
                out.leftLegX = Mth.lerp(enter, idle.leftLegX, -0.82F);
                out.leftLegY = Mth.lerp(enter, idle.leftLegY, -0.18F);
                out.leftLegZ = Mth.lerp(enter, idle.leftLegZ, -0.22F);
                out.rightLegX = Mth.lerp(enter, idle.rightLegX, 0.88F);
                out.rightLegY = Mth.lerp(enter, idle.rightLegY, 0.18F);
                out.rightLegZ = Mth.lerp(enter, idle.rightLegZ, 0.24F);
            }
            case CRESCENT_VORPAL -> {
                // PHA BÙNG NỔ RÚT KIẾM IAIJUTSU TỐI THƯỢNG SAU 3 GIÂY GỒNG (18 TICKS):
                // Rút phăng Đại Đao ngang 180° từ hông trái bung rộng sang mạn phải-trước + bung FOV +13° rồi tách về 2 thanh!
                if (alpha < 0.65F) {
                    float cut = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.28F, 0.0F, 1.0F));
                    float bodyYaw = Mth.lerp(cut, -0.40F, 0.86F);
                    out.rootPitchDeg = Mth.lerp(cut, -28.0F, -18.0F);
                    out.rootTranslateY = Mth.lerp(cut, -0.32F, -0.12F);
                    out.bodyX = Mth.lerp(cut, 0.40F, 0.32F);
                    out.bodyY = bodyYaw;
                    out.bodyZ = Mth.lerp(cut, 0.04F, -0.06F);
                    out.headAddX = Mth.lerp(cut, -0.34F, -0.12F);
                    out.headAddY = Mth.lerp(cut, 0.32F, -0.58F);

                    out.rightArmX = Mth.lerp(cut, -0.64F, -1.22F);
                    out.rightArmY = Mth.lerp(cut, -0.86F, 0.96F);
                    out.rightArmZ = Mth.lerp(cut, -0.48F, 0.52F);

                    out.leftArmX = Mth.lerp(cut, -0.36F, 0.58F);
                    out.leftArmY = Mth.lerp(cut, 0.18F, -0.22F);
                    out.leftArmZ = Mth.lerp(cut, -0.14F, -0.48F);

                    out.rightBladePitchDeg = Mth.rotLerp(cut, CHARGE_BLADE_PITCH, VORPAL_SLASH_BLADE_PITCH);
                    out.rightBladeRollDeg = Mth.rotLerp(cut, CHARGE_BLADE_ROLL, VORPAL_SLASH_BLADE_ROLL);
                    out.rightBladeYawDeg = Mth.rotLerp(cut, CHARGE_BLADE_YAW, VORPAL_SLASH_BLADE_YAW);
                    out.firstPersonCameraRollDeg = 4.4F * cut;
                    out.fovOffsetDeg = Mth.lerp(cut, -11.0F, 13.0F);

                    out.legWeight = 1.0F;
                    out.leftLegX = Mth.lerp(cut, -0.82F, 0.58F);
                    out.leftLegY = -0.18F;
                    out.leftLegZ = -0.22F;
                    out.rightLegX = Mth.lerp(cut, 0.88F, -0.68F);
                    out.rightLegY = 0.18F;
                    out.rightLegZ = 0.26F;
                } else {
                    float rec = DualBladeAnimState.easeInOutCubic((alpha - 0.65F) / 0.35F);
                    out.rootPitchDeg = Mth.lerp(rec, -18.0F, idle.rootPitchDeg);
                    out.rootTranslateY = Mth.lerp(rec, -0.12F, idle.rootTranslateY);
                    out.bodyX = Mth.lerp(rec, 0.32F, idle.bodyX);
                    out.bodyY = Mth.lerp(rec, 0.86F, idle.bodyY);
                    out.bodyZ = Mth.lerp(rec, -0.06F, idle.bodyZ);
                    out.headAddX = Mth.lerp(rec, -0.12F, idle.headAddX);
                    out.headAddY = Mth.lerp(rec, -0.58F, idle.headAddY);

                    out.rightArmX = Mth.lerp(rec, -1.22F, idle.rightArmX);
                    out.rightArmY = Mth.lerp(rec, 0.96F, idle.rightArmY);
                    out.rightArmZ = Mth.lerp(rec, 0.52F, idle.rightArmZ);

                    out.leftArmX = Mth.lerp(rec, 0.58F, idle.leftArmX);
                    out.leftArmY = Mth.lerp(rec, -0.22F, idle.leftArmY);
                    out.leftArmZ = Mth.lerp(rec, -0.48F, idle.leftArmZ);

                    out.rightBladePitchDeg = Mth.rotLerp(rec, VORPAL_SLASH_BLADE_PITCH, idle.rightBladePitchDeg);
                    out.rightBladeRollDeg = Mth.rotLerp(rec, VORPAL_SLASH_BLADE_ROLL, idle.rightBladeRollDeg);
                    out.rightBladeYawDeg = Mth.rotLerp(rec, VORPAL_SLASH_BLADE_YAW, idle.rightBladeYawDeg);
                    out.firstPersonCameraRollDeg = (1.0F - rec) * 4.4F;
                    out.fovOffsetDeg = (1.0F - rec) * 13.0F;
                    applyAttackLegs(out, idle, (1.0F - rec) * standLegWeight, -0.68F, 0.58F, 0.24F);
                }
            }
            case DUAL_PIN_LUNGE -> {
                // Pha 1 Chiêu Dạng Thường: Lao tốc độ cao vồ xuống đè kẻ địch!
                float t = DualBladeAnimState.easeOutCubic(alpha);
                out.rootPitchDeg = Mth.lerp(t, -8.0F, -30.0F);
                out.rootTranslateY = -0.18F * t;
                out.bodyX = 0.46F * t;
                out.headAddX = -0.26F * t;
                out.rightArmX = Mth.lerp(t, idle.rightArmX, 0.95F);
                out.rightArmY = 0.28F;
                out.rightArmZ = 0.48F;
                out.leftArmX = Mth.lerp(t, idle.leftArmX, 0.95F);
                out.leftArmY = -0.28F;
                out.leftArmZ = -0.48F;
                out.rightBladePitchDeg = -105.0F;
                out.leftBladePitchDeg = -105.0F;
                out.fovOffsetDeg = 8.5F * t;
                applyAttackLegs(out, idle, 0.95F, 0.72F, -0.64F, 0.24F);
            }
            case DUAL_PIN_FLURRY -> {
                // Pha 2 Chiêu Dạng Thường: Khuỵu thấp đè chặt mục tiêu & ĐÂM LIÊN THANH 12 NHÁT SO LE TRẮNG–ĐEN VÔ ĐỊCH!
                float endFade = alpha > 0.88F ? (1.0F - (alpha - 0.88F) / 0.12F) : 1.0F;
                // 6 chu kỳ sin = 12 đỉnh đâm so le 2 tay khớp 1:1 với 12 nhát server!
                float wave = Mth.sin(alpha * Mth.TWO_PI * 6.0F);
                float thrustR = Math.max(0.0F, wave);
                float thrustL = Math.max(0.0F, -wave);

                out.rootPitchDeg = -24.0F * endFade;
                out.rootTranslateY = -0.26F * endFade;
                out.bodyX = 0.44F * endFade;
                out.bodyY = wave * 0.34F * endFade;
                out.headAddX = 0.22F * endFade;
                out.headAddY = -wave * 0.24F * endFade;

                // Tay phải (Trăng Khuyết Đen) & Tay trái (Trăng Non Trắng) luân phiên đâm xối xả xuống mục tiêu!
                out.rightArmX = Mth.lerp(endFade, idle.rightArmX, Mth.lerp(thrustR, -0.62F, -1.92F));
                out.rightArmY = Mth.lerp(endFade, idle.rightArmY, Mth.lerp(thrustR, -0.32F, 0.22F));
                out.rightArmZ = Mth.lerp(endFade, idle.rightArmZ, Mth.lerp(thrustR, 0.38F, -0.06F));

                out.leftArmX = Mth.lerp(endFade, idle.leftArmX, Mth.lerp(thrustL, -0.62F, -1.92F));
                out.leftArmY = Mth.lerp(endFade, idle.leftArmY, Mth.lerp(thrustL, 0.32F, -0.22F));
                out.leftArmZ = Mth.lerp(endFade, idle.leftArmZ, Mth.lerp(thrustL, -0.38F, 0.06F));

                // Mũi 2 lưỡi chĩa thẳng về trước hướng đâm xuyên!
                out.rightBladePitchDeg = Mth.rotLerp(endFade, idle.rightBladePitchDeg, 62.0F);
                out.rightBladeRollDeg = Mth.rotLerp(endFade, idle.rightBladeRollDeg, 10.0F);
                out.rightBladeYawDeg = Mth.rotLerp(endFade, idle.rightBladeYawDeg, 0.0F);

                out.leftBladePitchDeg = Mth.rotLerp(endFade, idle.leftBladePitchDeg, 62.0F);
                out.leftBladeRollDeg = Mth.rotLerp(endFade, idle.leftBladeRollDeg, -10.0F);
                out.leftBladeYawDeg = Mth.rotLerp(endFade, idle.leftBladeYawDeg, 0.0F);

                out.firstPersonCameraRollDeg = wave * 2.8F * endFade;
                out.fovOffsetDeg = 4.5F * endFade;
                applyAttackLegs(out, idle, 0.96F * endFade, -0.68F, 0.72F, 0.28F);
            }
        }
    }

    private static void applyAttackLegs(FullBodyPose out, FullBodyPose idle, float weight,
                                        float rightX, float leftX, float spreadZ) {
        out.legWeight = Math.max(idle.legWeight, Mth.clamp(weight, 0.0F, 1.0F));
        out.rightLegX = Mth.lerp(weight, idle.rightLegX, rightX);
        out.rightLegY = Mth.lerp(weight, idle.rightLegY, -0.14F);
        out.rightLegZ = Mth.lerp(weight, idle.rightLegZ, spreadZ);
        out.leftLegX = Mth.lerp(weight, idle.leftLegX, leftX);
        out.leftLegY = Mth.lerp(weight, idle.leftLegY, 0.14F);
        out.leftLegZ = Mth.lerp(weight, idle.leftLegZ, -spreadZ);
    }

    private static void scalePose(FullBodyPose pose, float s) {
        pose.rootYawDeg *= s;
        pose.rootPitchDeg *= s;
        pose.rootRollDeg *= s;
        pose.rootTranslateY *= s;
        pose.bodyX *= s;
        pose.bodyY *= s;
        pose.bodyZ *= s;
        pose.headAddX *= s;
        pose.headAddY *= s;
        pose.armWeight *= s;
        pose.legWeight *= s;
        pose.firstPersonCameraRollDeg *= s;
        pose.fovOffsetDeg *= s;
    }
}
