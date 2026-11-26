package com.abyssalrpg.client.anim;

import com.abyssalrpg.client.TogetsuClientState;
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
 * Hệ thống Rig Animation Toàn Thân 2 Hình Thái cho **Togetsu (Trăng Non & Trăng
 * Khuyết / Hợp Nhất Song Nguyệt)**:
 * 1. **Dạng Song Đao (`!fused`):**
 * - Pose đứng/đi chuẩn **Sunraku** trong ảnh mẫu (`SUNRAKU_LEFT_ANGLES` +
 * `SUNRAKU_RIGHT_ANGLES` giải 1:1).
 * - Chạy nhanh (`Sprint`): chuyển mượt sang tư thế chạy **Naruto Run** tốc độ
 * cao, chân chạy hoàn toàn tự nhiên!
 * - 4 nhịp chém song nguyệt nhanh sắc (`DUAL_1..4`), hòa chân tự nhiên khi vừa
 * chạy vừa chém!
 * 2. **Animation Hợp Nhất (`FUSE_TRANSFORM` 18t) & Tách (`UNFUSE_SPLIT` 12t)
 * khi bấm phím `R`:**
 * - `FUSE_TRANSFORM`: `0%..50%` kéo 2 cây đao Trắng–Đen xoắn áp sát trước ngực
 * -> tại mốc `50%` lóe sáng biến thành **1 thanh Đại Đao Song Nguyệt khổng lồ**
 * -> `50%..100%` vung nặng vác lên vai vào thế Đại Kiếm!
 * - `UNFUSE_SPLIT`: `0%..40%` đưa Đại Đao ra trước ngực vặn khớp tách -> tại
 * mốc `40%` tách thành 2 cây đao Trắng–Đen -> `40%..100%` bung 2 tay về tư thế
 * Sunraku!
 * 3. **Dạng Đại Đao Song Nguyệt (`fused`):**
 * - Pose cầm **Đại Kiếm 2 Tay** chuẩn (`FUSED_BLADE_ANGLES` giải 1:1, tay trái
 * ôm chặt vòng tay cầm D kép).
 * - 3 nhịp bổ/quét Đại Kiếm cực nặng (`FUSED_1..3`) + Tuyệt kỹ tối thượng
 * **`CRESCENT_VORPAL`** (nén lực 6 tick zoom FOV `-8.5°` rồi chém phóng sóng 22
 * block bung FOV `+10.5°`!).
 */
public final class TogetsuAnimManager {
    private static final float STANCE_BLEND_IN = 0.16F;
    private static final float STANCE_BLEND_OUT = 0.16F;
    private static final float FUSED_BLEND_RATE = 0.18F;
    private static final float SPRINT_BLEND_IN = 0.22F;
    private static final float SPRINT_BLEND_OUT = 0.18F;

    // Bộ góc lưỡi CHÍNH THỨC dáng thủ Sunraku chĩa thẳng 2 lưỡi ra trước (-Z) với
    // TogetsuBentArmRig:
    // 0 clipping giáp/thân (rBlade=0 lBlade=0), cả 2 lưỡi cầm thuận hướng thẳng
    // ngang về trước (không chúc xuống hay quay ngang!):
    // - Tay phải (-0.58, -0.06, +0.30, z=-0.3) + khuỷu mềm (-0.60, -0.06, -0.08):
    // lưỡi Đen chĩa thẳng trước!
    // - Tay trái (-0.44, +0.08, -0.42, z=-0.3) + khuỷu mềm (-0.66, +0.06, +0.08):
    // lưỡi Trắng chĩa thẳng trước!
    private static final float SUNRAKU_LEFT_PITCH = 45.38F;
    private static final float SUNRAKU_LEFT_ROLL = -3.87F;
    private static final float SUNRAKU_LEFT_YAW = -145.30F;
    private static final float SUNRAKU_LEFT_OFF_X = 0.0773F;
    private static final float SUNRAKU_LEFT_OFF_Y = -0.0763F;
    private static final float SUNRAKU_LEFT_OFF_Z = -0.0799F;

    private static final float SUNRAKU_RIGHT_PITCH = -62.26F;
    private static final float SUNRAKU_RIGHT_ROLL = 1.88F;
    private static final float SUNRAKU_RIGHT_YAW = 2.45F;
    private static final float SUNRAKU_RIGHT_OFF_X = 0.0001F;
    private static final float SUNRAKU_RIGHT_OFF_Y = -0.1003F;
    private static final float SUNRAKU_RIGHT_OFF_Z = -0.0682F;

    // Hằng số gốc cho dáng thủ Sunraku chĩa thẳng ra trước TOGETSU_DUAL_IDLE +
    // TogetsuBentArmRig
    private static final float SUNRAKU_R_ARM_X = -0.58F;
    private static final float SUNRAKU_R_ARM_Y = -0.06F;
    private static final float SUNRAKU_R_ARM_Z = 0.30F;
    private static final float SUNRAKU_R_ARM_FWD_Z = -0.3F;
    private static final float SUNRAKU_R_ELB_X = -0.60F;
    private static final float SUNRAKU_R_ELB_Y = -0.06F;
    private static final float SUNRAKU_R_ELB_Z = -0.08F;

    private static final float SUNRAKU_L_ARM_X = -0.44F;
    private static final float SUNRAKU_L_ARM_Y = 0.08F;
    private static final float SUNRAKU_L_ARM_Z = -0.42F;
    private static final float SUNRAKU_L_ARM_FWD_Z = -0.3F;
    private static final float SUNRAKU_L_ELB_X = -0.66F;
    private static final float SUNRAKU_L_ELB_Y = 0.06F;
    private static final float SUNRAKU_L_ELB_Z = 0.08F;

    // Bộ góc chạy nước rút Sunraku TOGETSU_DUAL_SPRINT + khuỷu mềm ~17.5°
    // (TogetsuBentArmRig):
    // 2 cánh tay vẩy ngược (xRot=0.82±0.12, yRot=∓0.10, zRot=±0.28, vai vanilla
    // ±5,2,0),
    // 2 lưỡi Reverse Grip lướt ngược twin-wing cực gọn sau cẳng tay (0 clipping
    // thân/đùi/giáp ở mọi pha chạy!).
    private static final float DUAL_RUN_R_ELB_X = -0.30F;
    private static final float DUAL_RUN_R_ELB_Y = 0.0F;
    private static final float DUAL_RUN_R_ELB_Z = 0.06F;
    private static final float DUAL_RUN_L_ELB_X = -0.30F;
    private static final float DUAL_RUN_L_ELB_Y = 0.0F;
    private static final float DUAL_RUN_L_ELB_Z = -0.06F;

    private static final float DUAL_RUN_LEFT_PITCH = 113.07F;
    private static final float DUAL_RUN_LEFT_ROLL = -37.46F;
    private static final float DUAL_RUN_LEFT_YAW = 13.23F;
    private static final float DUAL_RUN_LEFT_OFF_X = -0.0620F;
    private static final float DUAL_RUN_LEFT_OFF_Y = 0.0368F;
    private static final float DUAL_RUN_LEFT_OFF_Z = 0.1124F;

    private static final float DUAL_RUN_RIGHT_PITCH = -136.76F;
    private static final float DUAL_RUN_RIGHT_ROLL = -9.58F;
    private static final float DUAL_RUN_RIGHT_YAW = -140.14F;
    private static final float DUAL_RUN_RIGHT_OFF_X = -0.0727F;
    private static final float DUAL_RUN_RIGHT_OFF_Y = 0.0674F;
    private static final float DUAL_RUN_RIGHT_OFF_Z = 0.1088F;

    // Pose Song Nguyệt 1 Tay (tỉ lệ 0.75F) chuẩn Forward Grip: chuôi tua đỏ gọn sau
    // cổ tay,
    // mũi nhọn & cạnh lưỡi trắng cong ngoài vươn thẳng về TRƯỚC (0 móc ngược ra sau
    // khuỷu tay, bC=0 aC=0 hC=0 yMin=0.45!)
    private static final float FUSED_BLADE_PITCH = -8.57F;
    private static final float FUSED_BLADE_ROLL = -6.40F;
    private static final float FUSED_BLADE_YAW = -166.75F;

    // Pose Chạy Song Nguyệt Cắm Lưỡi Kéo Sát Đất (khớp 1:1 Ảnh 2: mũi kiếm cong
    // chếch xuống-phải kéo sát mặt cỏ yMin=0.03!)
    private static final float FUSED_RUN_BLADE_PITCH = -85.10F;
    private static final float FUSED_RUN_BLADE_ROLL = 13.97F;
    private static final float FUSED_RUN_BLADE_YAW = -88.58F;

    // Pose Gồng Rút Kiếm Zenitsu (Hekireki Issen / Iaijutsu — áp gọn hông trái vươn
    // sau, cạnh lưỡi đúng chiều!)
    private static final float CHARGE_BLADE_PITCH = -162.55F;
    private static final float CHARGE_BLADE_ROLL = 44.82F;
    private static final float CHARGE_BLADE_YAW = -16.75F;

    // Pose Bùng Nổ Rút Kiếm Ngang (Hekireki Issen — quét ngang phẳng 180° Trái ->
    // Phải, góc nắm tự nhiên 0 dính cẳng tay!)
    private static final float VORPAL_START_PITCH = -62.61F;
    private static final float VORPAL_START_ROLL = 34.84F;
    private static final float VORPAL_START_YAW = 97.14F;
    private static final float VORPAL_END_PITCH = -72.72F;
    private static final float VORPAL_END_ROLL = 36.35F;
    private static final float VORPAL_END_YAW = 107.82F;

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
        float hitStopTicksLeft = 0.0F;
        float hitStopAccumulated = 0.0F;
        boolean morphFlashSpawned = false;

        boolean hasTransitionFrom = false;
        final FullBodyPose idlePose = new FullBodyPose();
        final FullBodyPose blendFromPose = new FullBodyPose();
        final FullBodyPose lastComputedPose = new FullBodyPose();
        final FullBodyPose reusablePose = new FullBodyPose();
        boolean wasOnGround = true;
        long pendingAirLandingUntilTick = -1000L;
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

        float getEffectiveElapsed(long tick, float pt) {
            float raw = (tick - startTick) + pt;
            float frozen = hitStopAccumulated + (hitStopTicksLeft > 0.0F ? Math.min(pt, hitStopTicksLeft) : 0.0F);
            return Math.max(0.0F, raw - frozen);
        }

        boolean isActive(long tick, float pt) {
            if (!attacking)
                return false;
            float elapsed = getEffectiveElapsed(tick, pt);
            return elapsed >= -0.25F && elapsed < durationTicks;
        }

        float getAlpha(long tick, float pt) {
            if (!attacking || durationTicks <= 0.1F)
                return 0.0F;
            float elapsed = getEffectiveElapsed(tick, pt);
            return Mth.clamp(elapsed / durationTicks, 0.0F, 1.0F);
        }
    }

    private static final Map<Integer, PlayerState> STATES = new HashMap<>();
    private static long clientTick = 0L;
    private static ClientLevel lastLevel = null;

    private TogetsuAnimManager() {
    }

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

    @Nullable
    public static FullBodyPose getLastComputedPose(int playerId) {
        PlayerState st = STATES.get(playerId);
        return st != null ? st.lastComputedPose : null;
    }

    public static void triggerHitStop(int playerId, float ticks) {
        PlayerState st = STATES.get(playerId);
        if (st != null && st.attacking) {
            st.hitStopTicksLeft = Math.max(st.hitStopTicksLeft, Mth.clamp(ticks, 0.5F, 3.0F));
        }
    }

    /**
     * Quyết định thời điểm hiển thị 1 thanh Kiếm Cong Song Nguyệt (`true`) hay 2
     * thanh Song Đao (`false`):
     * - Eclipse Hit 4 (`DUAL_4` — Morphing Finisher): tự động ghép mượt sang Kiếm
     * Cong 1 Tay ở `14%..90%` rồi tự tách về Song Đao!
     * - Signature RMB (`CRESCENT_WAVE_SLASH`): tự chuyển nhanh sang Kiếm Cong 1 Tay
     * trong `0%..85%` để rút kiếm phóng sóng!
     * - `FUSE_TRANSFORM` / `UNFUSE_SPLIT` / `CRESCENT_VORPAL`: đồng bộ chính xác
     * mốc ghép/tách!
     */
    public static boolean shouldRenderAsFused(int playerId, ItemStack stack, float partialTick) {
        PlayerState st = STATES.get(playerId);
        if (st != null && st.isActive(clientTick, partialTick)) {
            float alpha = st.getAlpha(clientTick, partialTick);
            if (st.moveType == TogetsuMoveType.DUAL_5) {
                return alpha >= 0.08F && alpha < 0.94F;
            }
            if (st.moveType == TogetsuMoveType.CRESCENT_WAVE_SLASH) {
                return alpha < 0.85F || TogetsuItem.isFused(stack);
            }
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
        if (st == null)
            return 0.0F;
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
                st.wasOnGround = player.onGround();
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
                    st.sprintGraceUntilTick = clientTick + 6L;
                }
                boolean sprintActive = !player.isCrouching() && (sprintingNow
                        || (clientTick <= st.sprintGraceUntilTick && movingFast));
                st.sprintBlend = sprintActive
                        ? Math.min(1.0F, st.sprintBlend + SPRINT_BLEND_IN)
                        : Math.max(0.0F, st.sprintBlend - SPRINT_BLEND_OUT);

                // Cập nhật hit-stop 0.08s (~1.6 ticks) cho cú chém kết liễu Hit 4!
                if (st.attacking && st.hitStopTicksLeft > 0.0F) {
                    float step = Math.min(1.0F, st.hitStopTicksLeft);
                    st.hitStopAccumulated += step;
                    st.hitStopTicksLeft = Math.max(0.0F, st.hitStopTicksLeft - step);
                }

                // Tự động lóe vòng sáng 3D + vòng tròn hạt chớp đen–trắng trước ngực (1.2
                // block) ở mốc 0.05s Hit 5 Đòn Đánh Đặc Biệt (DUAL_5)!
                if (st.attacking && st.moveType == TogetsuMoveType.DUAL_5 && !st.morphFlashSpawned
                        && st.getAlpha(clientTick, 0.5F) >= 0.08F) {
                    st.morphFlashSpawned = true;
                    float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
                    TogetsuVfxRenderer.spawnFusionShockwave(
                            player.position().add(-Math.sin(yawRad) * 1.2, player.getEyeHeight() - 0.32,
                                    Math.cos(yawRad) * 1.2),
                            true, clientTick);
                    TogetsuClientState.spawnMorphFlashRingParticles(level, player);
                }

                // Cho phép hủy sớm khung hồi chiêu khi chạy nước rút (giữ trọn 86% cho Hit 5
                // Đòn Đặc Biệt!)
                float cancelAt = (st.moveType == TogetsuMoveType.DUAL_5) ? 0.86F : 0.62F;
                if (st.attacking && !st.moveType.isLockedSkill() && sprintingNow
                        && st.getAlpha(clientTick, 0.0F) >= cancelAt) {
                    st.attacking = false;
                    st.hasTransitionFrom = false;
                }

                // Tự động nối nhịp cắt ngang gọn khi tiếp đất ngay sau DUAL_AIR_SLASH!
                if (!targetFused && !st.wasOnGround && player.onGround()
                        && clientTick <= st.pendingAirLandingUntilTick) {
                    st.pendingAirLandingUntilTick = -1000L;
                    startMove(st, player, TogetsuMoveType.DUAL_LANDING_DASH,
                            TogetsuMoveType.DUAL_LANDING_DASH.baseDisplayTicks, player.getYRot());
                }
            } else {
                if (player.getMainHandItem().is(ModItems.DUAL_KNIFE)) {
                    st.stanceBlend = 0.0F;
                    st.prevStanceBlend = 0.0F;
                    st.sprintBlend = 0.0F;
                    st.prevSprintBlend = 0.0F;
                    st.lastComputedPose.reset();
                } else {
                    st.stanceBlend = Math.max(0.0F, st.stanceBlend - STANCE_BLEND_OUT);
                    st.sprintBlend = Math.max(0.0F, st.sprintBlend - SPRINT_BLEND_OUT);
                }
                st.attacking = false;
                st.hasTransitionFrom = false;
                st.hitStopTicksLeft = 0.0F;
                st.hitStopAccumulated = 0.0F;
                st.pendingAirLandingUntilTick = -1000L;
                if (st.stanceBlend <= 0.002F && st.prevStanceBlend <= 0.002F) {
                    st.lastComputedPose.reset();
                }
            }
            st.wasOnGround = player.onGround();

            if (st.attacking && st.getEffectiveElapsed(clientTick, 0.0F) >= st.durationTicks + 1.5F) {
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

    public static void triggerLocalOptimistic(LocalPlayer player, TogetsuMoveType move, float durationTicks) {
        PlayerState st = STATES.computeIfAbsent(player.getId(), id -> new PlayerState());
        st.lastSeenTick = clientTick;
        startMove(st, player, move, durationTicks, player.getYRot());
    }

    public static void onSyncPacket(ClientLevel level, TogetsuSyncPayload payload) {
        PlayerState st = STATES.computeIfAbsent(payload.playerId(), id -> new PlayerState());
        st.lastSeenTick = clientTick;
        TogetsuMoveType move = TogetsuMoveType.byId(payload.moveId());

        // Bỏ qua lặp animation nếu client vừa dự đoán cục bộ trong vòng 2 tick (nhưng
        // VFX đặc biệt vẫn xử lý ở ClientState!)
        boolean recentSame = st.attacking && st.moveType == move && (clientTick - st.startTick) <= 2L;
        Entity ent = level.getEntity(payload.playerId());
        if (!recentSame || move.isLockedSkill()) {
            // Không restart CRESCENT_VORPAL_CHARGE khi nhận các nhịp mạch năng lượng 10..70
            // ticks!
            if (move == TogetsuMoveType.CRESCENT_VORPAL_CHARGE
                    && st.attacking && st.moveType == TogetsuMoveType.CRESCENT_VORPAL_CHARGE) {
                return;
            }
            // Không restart DUAL_PIN_FLURRY ở từng gói đâm 1..12 (chỉ bắt đầu 1 lần ở cú đè
            // xuống VFX_PIN_IMPACT!)
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
        st.durationTicks = Mth.clamp(durationTicks, 4.5F, 86.0F);
        st.attackYaw = yawDeg;
        st.hitStopTicksLeft = 0.0F;
        st.hitStopAccumulated = 0.0F;
        st.morphFlashSpawned = false;

        if (move == TogetsuMoveType.DUAL_AIR_SLASH) {
            st.pendingAirLandingUntilTick = clientTick + 16L;
        } else if (move != TogetsuMoveType.DUAL_LANDING_DASH) {
            st.pendingAirLandingUntilTick = -1000L;
        }

        if (player != null && !move.isLockedSkill()) {
            TogetsuVfxRenderer.spawnSlashTrail(
                    player.getId(),
                    player.position().add(0.0, player.getBbHeight() * 0.56, 0.0),
                    yawDeg,
                    player.getXRot(),
                    move,
                    st.durationTicks,
                    clientTick);
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
            st.lastComputedPose.reset();
            return null;
        }

        float fused = st.getFusedBlend(partialTick);
        float sprint = st.getSprintBlend(partialTick);
        // Đường cong Quintic Smootherstep (vận tốc & gia tốc = 0 ở cả 2 đầu) -> chuyển
        // dáng cực kỳ mềm mượt, tuyệt đối không khựng!
        float sSmooth = smootherstep(stance);
        float fSmooth = smootherstep(fused);
        float spSmooth = smootherstep(sprint);
        // Dùng thống nhất `walkAnimation.speed/position` như DualBladeAnimManager để
        // PlayerRenderer, HumanoidModel & ItemInHandLayer khớp 100%!
        float walkSpeed = player.walkAnimation.speed(partialTick);
        float walkPhase = player.walkAnimation.position(partialTick);

        FullBodyPose pose = st.reusablePose;
        pose.reset();

        // 1. Dựng tư thế nghỉ chuẩn (Song Đao gập khuỷu 2 khúc mềm mượt & Shadow Sprint
        // <->
        // Đại Đao Song Nguyệt!)
        buildIdlePose(st.idlePose, sSmooth, fSmooth, spSmooth, walkSpeed, walkPhase, ageInTicks, player.isCrouching());
        pose.copyFrom(st.idlePose);

        // 2. Đè Rig Animation tấn công / biến hình nếu đang kích hoạt
        if (st.isActive(clientTick, partialTick)) {
            float alpha = st.getAlpha(clientTick, partialTick);
            if (st.moveType.isDualComboMove()) {
                // Reset góc lưỡi & thân khớp 1:1 `DualBladeAnimManager.evaluatePose` trước khi
                // dựng từng nhịp!
                pose.rootYawDeg = 0.0F;
                pose.rootPitchDeg = 0.0F;
                pose.rootRollDeg = 0.0F;
                pose.rootTranslateY = 0.0F;
                pose.bodyX = 0.0F;
                pose.bodyY = 0.0F;
                pose.bodyZ = 0.0F;
                pose.headAddX = 0.0F;
                pose.headAddY = 0.0F;
                pose.rightLegX = pose.rightLegY = pose.rightLegZ = 0.0F;
                pose.leftLegX = pose.leftLegY = pose.leftLegZ = 0.0F;
                pose.rightBladePitchDeg = pose.rightBladeRollDeg = pose.rightBladeYawDeg = 0.0F;
                pose.leftBladePitchDeg = pose.leftBladeRollDeg = pose.leftBladeYawDeg = 0.0F;
            }
            applyActionPose(pose, st.idlePose, st.moveType, alpha, walkSpeed, spSmooth);

            // Cross-fade mượt mà khi vào combo (reset ngay khớp vai về vanilla ±5, 2, 0!)
            // và thu êm về TOGETSU_DUAL_IDLE!
            if (st.moveType.isDualComboMove()) {
                float enterWin = (st.moveType == TogetsuMoveType.DUAL_5) ? 0.08F : 0.10F;
                float exitStart = (st.moveType == TogetsuMoveType.DUAL_5) ? 0.80F : 0.76F;
                if (alpha < enterWin) {
                    float enterT = smootherstep(alpha / enterWin);
                    pose.blendFrom(st.hasTransitionFrom ? st.blendFromPose : st.idlePose, enterT);
                    // Trả sạch offset vai Z về 0.0F ngay khi vung combo để vòng cung chém luôn cân
                    // tâm!
                    pose.rightArmPosX = pose.rightArmPosZ = 0.0F;
                    pose.leftArmPosX = pose.leftArmPosZ = 0.0F;
                } else if (alpha > exitStart) {
                    float backT = smootherstep((alpha - exitStart) / (1.0F - exitStart));
                    pose.blendFrom(st.idlePose, 1.0F - backT);
                    pose.isAttacking = true;
                }
            } else if (st.hasTransitionFrom && alpha < 0.16F) {
                float chainT = smootherstep(alpha / 0.16F);
                pose.blendFrom(st.blendFromPose, chainT);
                pose.rightArmPosX = pose.rightArmPosZ = 0.0F;
                pose.leftArmPosX = pose.leftArmPosZ = 0.0F;
            }
            if (sSmooth < 0.99F) {
                scalePose(pose, sSmooth);
            }
        }

        st.lastComputedPose.copyFrom(pose);
        return pose;
    }

    private static float smootherstep(float t) {
        float x = Mth.clamp(t, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    private static void buildIdlePose(FullBodyPose out, float stance, float fused, float sprint,
            float walkSpeed, float walkPhase, float ageInTicks, boolean crouching) {
        out.reset();
        // Quy chuẩn Chống Rung 0%: giữ thân & vũ khí đứng im chắc tay khi idle (0 nhịp lắc rung!)
        float breathe = 0.0F;
        float torsoBreath = 0.0F;
        float forearmSway = 0.0F;
        float moveWeight = Mth.clamp(walkSpeed * 1.35F, 0.0F, 1.0F);
        float strideCos = Mth.cos(walkPhase * 0.6662F);
        float strideSway = Mth.sin(walkPhase * 0.6662F) * moveWeight;
        float torsoTwist = strideCos * 0.08F * walkSpeed;
        float strideBob = 0.0F;
        float idleLeg = Mth.clamp(1.0F - moveWeight * 1.20F, 0.0F, 1.0F) * (1.0F - sprint);
        float crouchScale = crouching ? 0.55F : 1.0F;

        // A. Tư thế thủ Sunraku chĩa thẳng 2 lưỡi ra trước (-Z) (TOGETSU_DUAL_IDLE ->
        // TOGETSU_DUAL_RUN):
        // - Thân hơi nghiêng năng động (rootYaw=-14°, body.xRot=0.14f,
        // body.yRot=-0.08f), đầu phản xoay nhìn thẳng 0°!
        // - Thế chân tấn rộng linh hoạt (rightLeg=(-0.18, 0, +0.12), leftLeg=(+0.20, 0,
        // -0.12))!
        float dRootYaw = -14.0F * (1.0F - moveWeight * 0.45F);
        float dRootPitch = (-1.2F * moveWeight) * crouchScale;
        float dRootY = (-0.055F * (1.0F - moveWeight * 0.45F) + torsoBreath * 0.30F + strideBob) * crouchScale;
        float dBodyX = (0.14F + torsoBreath * 0.80F) * crouchScale;
        float dBodyY = Mth.lerp(moveWeight * 0.55F, -0.08F, 0.0F) + torsoTwist;
        float dBodyZ = 0.0F;
        float dHeadX = (-0.03F * (1.0F - moveWeight * 0.50F)) * crouchScale + dRootPitch * Mth.DEG_TO_RAD;
        float dHeadY = (dRootYaw * Mth.DEG_TO_RAD) * (1.0F - sprint);
        float dHeadZ = 0.0F;

        float dRArmX = SUNRAKU_R_ARM_X + torsoBreath + strideSway * 0.085F;
        float dRArmY = SUNRAKU_R_ARM_Y + strideSway * 0.030F;
        float dRArmZ = SUNRAKU_R_ARM_Z - torsoBreath * 0.35F;

        float dLArmX = SUNRAKU_L_ARM_X - torsoBreath * 0.85F - strideSway * 0.095F;
        float dLArmY = SUNRAKU_L_ARM_Y + strideSway * 0.030F;
        float dLArmZ = SUNRAKU_L_ARM_Z + torsoBreath * 0.35F;

        // TOGETSU_DUAL_SPRINT (Chạy nước rút phong cách Sunraku, 0 clipping
        // thân/đùi/giáp):
        // - Thân đổ nhanh về trước (body.xRot=0.28f, rootPitch=-11.5°), xoắn thân nhịp
        // bước (body.yRot=cos*0.08*walkSpeed),
        // đầu phản góc (head.xRot=-0.22f -> tầm nhìn khóa thẳng ngang 1.1°)!
        // - 2 tay vẩy ngược & mở nhẹ tránh đùi (rightArm=(0.82+cos*0.12*walkSpeed,
        // -0.10, +0.28),
        // leftArm=(0.82-cos*0.12*walkSpeed, +0.10, -0.28)) + khuỷu gập tự nhiên ~17.5°
        // qua TogetsuBentArmRig!
        if (sprint > 0.001F) {
            float armSwing = strideCos * 0.12F * walkSpeed;
            dRootYaw = Mth.lerp(sprint, dRootYaw, 0.0F);
            dRootPitch = Mth.lerp(sprint, dRootPitch, -11.5F);
            dRootY = Mth.lerp(sprint, dRootY, -0.055F + strideBob * 0.70F);
            dBodyX = Mth.lerp(sprint, dBodyX, 0.28F);
            dBodyY = Mth.lerp(sprint, dBodyY, torsoTwist);
            dHeadX = Mth.lerp(sprint, dHeadX, -0.22F);
            dHeadY = Mth.lerp(sprint, dHeadY, 0.0F);
            dRArmX = Mth.lerp(sprint, dRArmX, 0.82F + armSwing);
            dRArmY = Mth.lerp(sprint, dRArmY, -0.10F);
            dRArmZ = Mth.lerp(sprint, dRArmZ, 0.28F);
            dLArmX = Mth.lerp(sprint, dLArmX, 0.82F - armSwing);
            dLArmY = Mth.lerp(sprint, dLArmY, 0.10F);
            dLArmZ = Mth.lerp(sprint, dLArmZ, -0.28F);
        }

        // B. Tư thế Đứng & Đi Bộ Kiếm Cong 1 Tay Song Nguyệt (0.75F — Forward Grip,
        // cạnh trắng sắc hướng thẳng về trước!):
        // - Thân hơi nghiêng tự nhiên (rootYaw=-12°, bodyY=+0.12 -> cùng chiều chuẩn!),
        // đầu phản xoay -12° nhìn thẳng 0°!
        // - Tay phải hạ tự nhiên bên hông (+0.18 rad, mở +0.32 rad), chuôi tua đỏ gọn
        // sau cổ tay, lưỡi cong trắng hướng trước!
        float fRootYaw = -12.0F * (1.0F - moveWeight * 0.35F);
        float fRootPitch = (-1.5F * moveWeight) * crouchScale;
        float fRootY = (-0.02F + breathe * 0.006F + strideBob * 0.60F) * crouchScale;
        float fBodyX = (0.05F + breathe * 0.010F) * crouchScale;
        float fBodyY = 0.12F + strideSway * 0.08F;
        float fBodyZ = 0.0F;
        float fHeadX = fRootPitch * Mth.DEG_TO_RAD;
        float fHeadY = fRootYaw * Mth.DEG_TO_RAD; // Hủy đúng rootYaw để mắt luôn nhìn thẳng 0.0°!
        float fRArmX = 0.12F + breathe * 0.020F + strideSway * 0.14F;
        float fRArmY = -0.06F;
        float fRArmZ = 0.34F - breathe * 0.014F;
        float fLArmX = -0.48F - breathe * 0.022F - strideSway * 0.22F;
        float fLArmY = 0.36F + strideSway * 0.04F;
        float fLArmZ = 0.10F + breathe * 0.012F;

        // Chạy Song Nguyệt Cắm Lưỡi Kéo Sát Mặt Đất (khớp 1:1 Ảnh 2: tay phải hạ thấp
        // ngang đùi ngoài,
        // mũi kiếm cong trắng–đen cắm chếch xuống-phải kéo sát mặt cỏ yMin=0.03, đầu
        // nhìn thẳng 0°!)
        if (sprint > 0.001F) {
            float dashBob = Mth.sin(walkPhase * 0.6662F * 2.0F) * 0.045F * moveWeight;
            fRootYaw = Mth.lerp(sprint, fRootYaw, 0.0F);
            fRootPitch = Mth.lerp(sprint, fRootPitch, -14.0F);
            fRootY = Mth.lerp(sprint, fRootY, -0.06F + strideBob * 0.65F);
            fBodyX = Mth.lerp(sprint, fBodyX, 0.12F);
            fBodyY = Mth.lerp(sprint, fBodyY, 0.10F + strideSway * 0.06F);
            fBodyZ = 0.0F;
            fHeadX = Mth.lerp(sprint, fHeadX, -14.0F * Mth.DEG_TO_RAD); // Đầu giữ thẳng 0° nhìn phía trước!
            fHeadY = Mth.lerp(sprint, fHeadY, -strideSway * 0.04F);
            fRArmX = Mth.lerp(sprint, fRArmX, 0.44F + dashBob); // hạ thấp gọn bên hông/đùi kéo kiếm sát đất!
            fRArmY = Mth.lerp(sprint, fRArmY, -0.10F);
            fRArmZ = Mth.lerp(sprint, fRArmZ, 0.38F); // mở nhẹ ngoài đùi phải khớp Ảnh 2!
            fLArmX = Mth.lerp(sprint, fLArmX, 0.18F - strideSway * 0.55F);
            fLArmY = Mth.lerp(sprint, fLArmY, 0.06F);
            fLArmZ = Mth.lerp(sprint, fLArmZ, -0.14F);
        }

        // Hòa mượt giữa 2 dạng!
        out.rootYawDeg = Mth.lerp(fused, dRootYaw, fRootYaw) * stance;
        out.rootPitchDeg = Mth.lerp(fused, dRootPitch, fRootPitch) * stance;
        out.rootTranslateY = Mth.lerp(fused, dRootY, fRootY) * stance;
        out.bodyX = Mth.lerp(fused, dBodyX, fBodyX) * stance;
        out.bodyY = Mth.lerp(fused, dBodyY, fBodyY) * stance;
        out.bodyZ = Mth.lerp(fused, dBodyZ, fBodyZ) * stance;
        out.headAddX = Mth.lerp(fused, dHeadX, fHeadX) * stance;
        out.headAddY = Mth.lerp(fused, dHeadY, fHeadY) * stance;
        out.headAddZ = Mth.lerp(fused, dHeadZ, 0.0F) * stance;

        out.rightArmX = Mth.lerp(fused, dRArmX, fRArmX);
        out.rightArmY = Mth.lerp(fused, dRArmY, fRArmY);
        out.rightArmZ = Mth.lerp(fused, dRArmZ, fRArmZ);

        out.leftArmX = Mth.lerp(fused, dLArmX, fLArmX);
        out.leftArmY = Mth.lerp(fused, dLArmY, fLArmY);
        out.leftArmZ = Mth.lerp(fused, dLArmZ, fLArmZ);

        // Khớp khuỷu mềm TogetsuBentArmRig nội suy mượt giữa Đứng (37°/27°) <-> Chạy
        // nước rút (~17.5°);
        // vai hoàn trả tuyệt đối về vanilla (±5.0f, 2.0f, 0.0f) khi chạy nước rút / hợp
        // nhất / tung combo!
        float dualBentWeight = (1.0F - fused) * stance;
        float idlePivotWeight = dualBentWeight * (1.0F - sprint);
        out.elbowWeight = dualBentWeight;
        out.rightElbowX = Mth.lerp(sprint, SUNRAKU_R_ELB_X + forearmSway, DUAL_RUN_R_ELB_X);
        out.rightElbowY = Mth.lerp(sprint, SUNRAKU_R_ELB_Y, DUAL_RUN_R_ELB_Y);
        out.rightElbowZ = Mth.lerp(sprint, SUNRAKU_R_ELB_Z - forearmSway * 0.35F, DUAL_RUN_R_ELB_Z);
        out.leftElbowX = Mth.lerp(sprint, SUNRAKU_L_ELB_X - forearmSway * 0.85F, DUAL_RUN_L_ELB_X);
        out.leftElbowY = Mth.lerp(sprint, SUNRAKU_L_ELB_Y, DUAL_RUN_L_ELB_Y);
        out.leftElbowZ = Mth.lerp(sprint, SUNRAKU_L_ELB_Z + forearmSway * 0.35F, DUAL_RUN_L_ELB_Z);

        out.rightArmPosX = 0.0F;
        out.rightArmPosZ = SUNRAKU_R_ARM_FWD_Z * idlePivotWeight;
        out.leftArmPosX = 0.0F;
        out.leftArmPosZ = SUNRAKU_L_ARM_FWD_Z * idlePivotWeight;

        out.rightBladeOffX = Mth.lerp(sprint, SUNRAKU_RIGHT_OFF_X, DUAL_RUN_RIGHT_OFF_X) * dualBentWeight;
        out.rightBladeOffY = Mth.lerp(sprint, SUNRAKU_RIGHT_OFF_Y, DUAL_RUN_RIGHT_OFF_Y) * dualBentWeight;
        out.rightBladeOffZ = Mth.lerp(sprint, SUNRAKU_RIGHT_OFF_Z, DUAL_RUN_RIGHT_OFF_Z) * dualBentWeight;
        out.leftBladeOffX = Mth.lerp(sprint, SUNRAKU_LEFT_OFF_X, DUAL_RUN_LEFT_OFF_X) * dualBentWeight;
        out.leftBladeOffY = Mth.lerp(sprint, SUNRAKU_LEFT_OFF_Y, DUAL_RUN_LEFT_OFF_Y) * dualBentWeight;
        out.leftBladeOffZ = Mth.lerp(sprint, SUNRAKU_LEFT_OFF_Z, DUAL_RUN_LEFT_OFF_Z) * dualBentWeight;

        out.armWeight = stance;

        // Thế chân tấn Sunraku linh hoạt (rightLeg=(-0.18, 0, +0.12), leftLeg=(+0.20,
        // 0, -0.12)),
        // trả 100% bước đi/chạy tự nhiên khi di chuyển!
        out.leftLegX = Mth.lerp(fused, 0.20F, -0.12F);
        out.leftLegY = Mth.lerp(fused, 0.0F, 0.10F);
        out.leftLegZ = Mth.lerp(fused, -0.12F, -0.08F);
        out.rightLegX = Mth.lerp(fused, -0.18F, 0.14F);
        out.rightLegY = Mth.lerp(fused, 0.0F, -0.10F);
        out.rightLegZ = Mth.lerp(fused, 0.12F, 0.08F);
        out.legWeight = idleLeg * Mth.lerp(fused, 1.0F, 0.92F) * stance;

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
        float moveWeight = Mth.clamp(limbSwingAmount * 1.35F, 0.0F, 1.0F);
        float runDamp = 1.0F - moveWeight * 0.45F;
        float moveFrac = Mth.clamp(limbSwingAmount * 2.0F, 0.0F, 1.0F);
        float standLegWeight = (1.0F - moveFrac * 0.88F) * (1.0F - sprint);

        out.isAttacking = true;
        out.armWeight = 1.0F;
        out.headAddZ = 0.0F;
        out.rightArmPosX = 0.0F;
        out.rightArmPosZ = 0.0F;
        out.leftArmPosX = 0.0F;
        out.leftArmPosZ = 0.0F;
        out.rightBladeOffX = out.rightBladeOffY = out.rightBladeOffZ = 0.0F;
        out.leftBladeOffX = out.leftBladeOffY = out.leftBladeOffZ = 0.0F;
        out.elbowWeight = 0.0F;
        out.rightElbowX = out.rightElbowY = out.rightElbowZ = 0.0F;
        out.leftElbowX = out.leftElbowY = out.leftElbowZ = 0.0F;

        switch (move) {
            case DUAL_1 -> {
                // Nhát 1 Song Đao: CHÉM XUỐNG TAY PHẢI (Lưỡi Đen: Phải-Trên -> Giữa ->
                // Trái-Dưới,
                // cạnh sắc dẫn hướng chém xuống mượt tự nhiên!), tay trái giữ lưỡi chĩa thẳng
                // về trước!
                float s = snappyStrikeSweep(alpha, 0.16F, 0.56F);
                float rootYaw = Mth.lerp(s, -22.0F, 22.0F) * (0.76F + 0.24F * runDamp);
                out.rootYawDeg = rootYaw;
                out.rootPitchDeg = 0.0F;
                out.rootRollDeg = Mth.lerp(s, 3.0F, -3.0F) * runDamp;
                out.rootTranslateY = -0.045F * runDamp;

                out.bodyX = 0.14F;
                out.bodyY = Mth.lerp(s, 0.20F, -0.22F);
                out.bodyZ = 0.0F;
                out.headAddX = 0.0F;
                out.headAddY = Mth.clamp(rootYaw * Mth.DEG_TO_RAD, -0.85F, 0.85F);

                // Tay Phải bổ chéo mượt Phải-Trên -> Giữa -> Trái-Dưới!
                out.rightArmX = lerp3(s, -2.10F, -1.35F, -0.72F);
                out.rightArmY = lerp3(s, 0.52F, -0.05F, -0.56F);
                out.rightArmZ = lerp3(s, 0.36F, 0.04F, -0.30F);

                // Tay Trái giữ gọn lưỡi Trắng chĩa thẳng về trước!
                out.leftArmX = -0.52F;
                out.leftArmY = -0.14F;
                out.leftArmZ = -0.34F;

                out.rightBladePitchDeg = rotLerp3(s, -91.50F, -79.76F, -80.94F);
                out.rightBladeRollDeg = rotLerp3(s, 5.61F, -5.08F, -26.44F);
                out.rightBladeYawDeg = rotLerp3(s, 79.40F, 41.08F, 46.15F);
                out.leftBladePitchDeg = rotLerp3(s, 78.65F, 81.49F, 81.78F);
                out.leftBladeRollDeg = rotLerp3(s, 5.37F, 16.23F, 27.59F);
                out.leftBladeYawDeg = rotLerp3(s, -149.74F, -168.40F, 172.61F);
                out.firstPersonCameraRollDeg = -2.4F * env;

                out.legWeight = Mth.clamp(1.0F - moveWeight * 1.15F, 0.0F, 1.0F) * (1.0F - sprint);
                out.rightLegX = Mth.lerp(s, 0.30F, -0.36F);
                out.rightLegY = -0.12F;
                out.rightLegZ = 0.14F;
                out.leftLegX = Mth.lerp(s, -0.30F, 0.36F);
                out.leftLegY = 0.12F;
                out.leftLegZ = -0.14F;
            }
            case DUAL_2 -> {
                // Nhát 2 Song Đao: CHÉM XUỐNG TAY TRÁI (Lưỡi Trắng: Trái-Trên -> Giữa ->
                // Phải-Dưới,
                // cạnh sắc dẫn hướng chém xuống mượt đối xứng!), tay phải giữ lưỡi chĩa thẳng
                // về trước!
                float s = snappyStrikeSweep(alpha, 0.16F, 0.56F);
                float rootYaw = Mth.lerp(s, 22.0F, -22.0F) * (0.76F + 0.24F * runDamp);
                out.rootYawDeg = rootYaw;
                out.rootPitchDeg = 0.0F;
                out.rootRollDeg = Mth.lerp(s, -3.0F, 3.0F) * runDamp;
                out.rootTranslateY = -0.045F * runDamp;

                out.bodyX = 0.14F;
                out.bodyY = Mth.lerp(s, -0.20F, 0.22F);
                out.bodyZ = 0.0F;
                out.headAddX = 0.0F;
                out.headAddY = Mth.clamp(rootYaw * Mth.DEG_TO_RAD, -0.85F, 0.85F);

                // Tay Trái bổ chéo mượt Trái-Trên -> Giữa -> Phải-Dưới!
                out.leftArmX = lerp3(s, -2.10F, -1.35F, -0.72F);
                out.leftArmY = lerp3(s, -0.52F, 0.05F, 0.56F);
                out.leftArmZ = lerp3(s, -0.36F, -0.04F, 0.30F);

                // Tay Phải giữ gọn lưỡi Đen chĩa thẳng về trước!
                out.rightArmX = -0.52F;
                out.rightArmY = 0.14F;
                out.rightArmZ = 0.34F;

                out.leftBladePitchDeg = rotLerp3(s, 50.22F, 40.04F, 51.13F);
                out.leftBladeRollDeg = rotLerp3(s, 69.02F, 32.34F, 23.65F);
                out.leftBladeYawDeg = rotLerp3(s, -122.98F, -149.93F, -127.08F);
                out.rightBladePitchDeg = rotLerp3(s, -36.30F, -30.25F, -21.47F);
                out.rightBladeRollDeg = rotLerp3(s, -17.84F, 3.28F, 23.50F);
                out.rightBladeYawDeg = rotLerp3(s, 16.78F, 19.39F, 24.94F);
                out.firstPersonCameraRollDeg = 2.4F * env;

                out.legWeight = Mth.clamp(1.0F - moveWeight * 1.15F, 0.0F, 1.0F) * (1.0F - sprint);
                out.rightLegX = Mth.lerp(s, -0.30F, 0.36F);
                out.rightLegY = -0.12F;
                out.rightLegZ = 0.14F;
                out.leftLegX = Mth.lerp(s, 0.30F, -0.36F);
                out.leftLegY = 0.12F;
                out.leftLegZ = -0.14F;
            }
            case DUAL_3 -> {
                // Nhát 3 Song Đao: CHÉM HẤT LÊN TAY PHẢI (Lưỡi Đen: Trái-Dưới -> Giữa ->
                // Phải-Trên,
                // lật cạnh sắc hướng LÊN TRÊN đúng chiều chém móc!), tay trái giữ lưỡi hướng
                // trước!
                float s = snappyStrikeSweep(alpha, 0.16F, 0.58F);
                float rootYaw = Mth.lerp(s, 20.0F, -22.0F) * (0.76F + 0.24F * runDamp);
                out.rootYawDeg = rootYaw;
                out.rootPitchDeg = 0.0F;
                out.rootRollDeg = Mth.lerp(s, -2.5F, 3.0F) * runDamp;
                out.rootTranslateY = -0.045F * runDamp;

                out.bodyX = 0.10F;
                out.bodyY = Mth.lerp(s, -0.20F, 0.22F);
                out.bodyZ = 0.0F;
                out.headAddX = 0.0F;
                out.headAddY = Mth.clamp(rootYaw * Mth.DEG_TO_RAD, -0.85F, 0.85F);

                // Tay Phải hất ngược mượt Trái-Dưới -> Giữa -> Phải-Trên!
                out.rightArmX = lerp3(s, -0.72F, -1.38F, -2.12F);
                out.rightArmY = lerp3(s, -0.54F, 0.06F, 0.54F);
                out.rightArmZ = lerp3(s, -0.28F, 0.06F, 0.36F);

                // Tay Trái giữ lưỡi Trắng gọn phía trước chờ Nhát 4!
                out.leftArmX = -0.44F;
                out.leftArmY = -0.14F;
                out.leftArmZ = -0.36F;

                out.rightBladePitchDeg = rotLerp3(s, -78.70F, -75.75F, -91.29F);
                out.rightBladeRollDeg = rotLerp3(s, -24.79F, 0.92F, 2.99F);
                out.rightBladeYawDeg = rotLerp3(s, -135.49F, -139.57F, -101.59F);
                out.leftBladePitchDeg = rotLerp3(s, 86.54F, 85.93F, 82.87F);
                out.leftBladeRollDeg = rotLerp3(s, 26.95F, 16.84F, 6.06F);
                out.leftBladeYawDeg = rotLerp3(s, 175.84F, -166.94F, -148.35F);
                out.firstPersonCameraRollDeg = 2.6F * env;

                out.legWeight = Mth.clamp(1.0F - moveWeight * 1.15F, 0.0F, 1.0F) * (1.0F - sprint);
                out.rightLegX = Mth.lerp(s, -0.32F, 0.34F);
                out.leftLegX = Mth.lerp(s, 0.32F, -0.34F);
                out.rightLegZ = 0.14F;
                out.leftLegZ = -0.14F;
            }
            case DUAL_4 -> {
                // Nhát 4 Song Đao: CHÉM HẤT LÊN TAY TRÁI (Lưỡi Trắng: Phải-Dưới -> Giữa ->
                // Trái-Trên
                // + gắn Moon Mark, lật cạnh sắc hướng LÊN TRÊN đúng chiều chém móc!), tay phải
                // giữ lưỡi hướng trước!
                float s = snappyStrikeSweep(alpha, 0.16F, 0.58F);
                float rootYaw = Mth.lerp(s, -20.0F, 22.0F) * (0.76F + 0.24F * runDamp);
                out.rootYawDeg = rootYaw;
                out.rootPitchDeg = 0.0F;
                out.rootRollDeg = Mth.lerp(s, 2.5F, -3.0F) * runDamp;
                out.rootTranslateY = -0.045F * runDamp;

                out.bodyX = 0.10F;
                out.bodyY = Mth.lerp(s, 0.20F, -0.22F);
                out.bodyZ = 0.0F;
                out.headAddX = 0.0F;
                out.headAddY = Mth.clamp(rootYaw * Mth.DEG_TO_RAD, -0.85F, 0.85F);

                // Tay Trái hất ngược mượt Phải-Dưới -> Giữa -> Trái-Trên!
                out.leftArmX = lerp3(s, -0.72F, -1.38F, -2.12F);
                out.leftArmY = lerp3(s, 0.54F, -0.06F, -0.54F);
                out.leftArmZ = lerp3(s, 0.28F, -0.06F, -0.36F);

                // Tay Phải giữ lưỡi Đen gọn phía trước sẵn sàng nối Nhát 5!
                out.rightArmX = -0.44F;
                out.rightArmY = 0.14F;
                out.rightArmZ = 0.36F;

                out.leftBladePitchDeg = rotLerp3(s, -175.23F, 167.06F, 118.33F);
                out.leftBladeRollDeg = rotLerp3(s, -45.39F, -33.75F, -61.31F);
                out.leftBladeYawDeg = rotLerp3(s, -5.14F, 25.34F, 66.51F);
                out.rightBladePitchDeg = rotLerp3(s, -18.15F, -26.16F, -32.61F);
                out.rightBladeRollDeg = rotLerp3(s, 20.56F, 2.22F, -18.79F);
                out.rightBladeYawDeg = rotLerp3(s, 25.02F, 20.48F, 18.06F);
                out.firstPersonCameraRollDeg = -2.6F * env;

                out.legWeight = Mth.clamp(1.0F - moveWeight * 1.15F, 0.0F, 1.0F) * (1.0F - sprint);
                out.rightLegX = Mth.lerp(s, 0.32F, -0.34F);
                out.leftLegX = Mth.lerp(s, -0.32F, 0.34F);
                out.rightLegZ = 0.14F;
                out.leftLegZ = -0.14F;
            }
            case DUAL_5 -> {
                // NHÁT 5 — ĐÒN ĐÁNH ĐẶC BIỆT (Eclipse Morphing Finisher — Auto-Fuse Kiếm Cong
                // 0.75F, 0.52s + 0.08s Hit-Stop):
                // - 0.00s..0.15s: Chớp vòng hạt đen–trắng trước ngực (1.2 block), tự ghép sang
                // Kiếm Cong 0.75F, giơ cao đỉnh đầu (-2.4, -0.3, -0.2), tay trái kéo ngực
                // (-1.2, +0.6), ngả thân (-0.15)!
                // - 0.15s..0.34s: Bổ chém 180° uy lực từ đỉnh đầu cắm chéo xuống (-0.35,
                // +0.75), dồn thân (+0.25, +0.35), đầu khóa 0.0°, ×1.60 dame + hất tung + AoE
                // 3.5 block!
                // - 0.34s..0.52s: Giữ dáng follow-through + hit-stop rồi tự tách mượt về Song
                // Đao Sunraku!
                float windUp = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.22F, 0.0F, 1.0F));
                float cleave = snappyStrikeSweep(alpha, 0.30F, 0.64F);
                float leap = Mth.sin(Mth.clamp(alpha / 0.42F, 0.0F, 1.0F) * Mth.PI);
                float rootYaw = Mth.lerp(cleave, 14.0F * windUp, -22.0F);

                out.rootYawDeg = rootYaw;
                out.rootPitchDeg = 0.0F;
                out.rootRollDeg = Mth.lerp(cleave, -3.0F * windUp, 4.0F);
                out.rootTranslateY = 0.16F * leap - 0.06F * cleave;

                out.bodyX = lerp3(cleave, -0.15F * windUp, 0.12F, 0.25F);
                out.bodyY = lerp3(cleave, -0.18F * windUp, 0.08F, 0.35F);
                out.bodyZ = 0.0F;
                out.headAddX = 0.0F;
                out.headAddY = Mth.clamp(rootYaw * Mth.DEG_TO_RAD, -0.85F, 0.85F);

                out.rightArmX = lerp3(cleave, -2.40F, -1.30F, -0.35F);
                out.rightArmY = lerp3(cleave, -0.30F, 0.22F, 0.75F);
                out.rightArmZ = lerp3(cleave, -0.20F, 0.36F, 0.44F);

                out.leftArmX = lerp3(cleave, -1.20F, 0.18F, 0.52F);
                out.leftArmY = lerp3(cleave, 0.60F, -0.12F, -0.22F);
                out.leftArmZ = lerp3(cleave, -0.10F, -0.42F, -0.50F);

                out.rightBladePitchDeg = rotLerp3(cleave, -99.52F, -110.41F, -77.18F);
                out.rightBladeRollDeg = rotLerp3(cleave, -17.90F, 0.08F, 6.18F);
                out.rightBladeYawDeg = rotLerp3(cleave, 127.58F, 164.62F, 155.97F);
                out.leftBladePitchDeg = SUNRAKU_LEFT_PITCH;
                out.leftBladeRollDeg = SUNRAKU_LEFT_ROLL;
                out.leftBladeYawDeg = SUNRAKU_LEFT_YAW;

                out.firstPersonCameraRollDeg = Mth.lerp(cleave, -2.5F, 3.8F) * env;
                out.fovOffsetDeg = Mth.lerp(cleave, -3.0F * leap, 5.5F) * env;

                out.legWeight = Math.max(leap * 0.85F,
                        Mth.clamp(1.0F - moveWeight * 1.15F, 0.0F, 1.0F) * (1.0F - sprint));
                out.rightLegX = Mth.lerp(cleave, -0.42F, -0.48F);
                out.leftLegX = Mth.lerp(cleave, 0.34F, 0.52F);
                out.rightLegZ = 0.20F;
                out.leftLegZ = -0.20F;
            }
            case DUAL_SPRINT_THRUST -> {
                // Đòn mở màn trực tiếp từ Shadow Sprint: 2 tay rút bật mượt từ sau hông (+0.68
                // rad) quét vút tới trước (-1.42..-0.78 rad), đầu thẳng 0°!
                float whipR = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.36F, 0.0F, 1.0F));
                float whipL = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.14F) / 0.38F, 0.0F, 1.0F));

                float rootYaw = Mth.lerp(whipR, -22.0F, 22.0F) + Mth.lerp(whipL, 0.0F, -24.0F);
                out.rootPitchDeg = -5.0F;
                out.rootYawDeg = rootYaw;
                out.rootRollDeg = 3.0F - 6.0F * whipL;
                out.rootTranslateY = -0.06F;

                out.bodyX = 0.20F;
                out.bodyY = Mth.lerp(whipR, 0.20F, -0.20F) + 0.22F * whipL;
                out.bodyZ = 0.0F;
                out.headAddX = -5.0F * Mth.DEG_TO_RAD;
                out.headAddY = Mth.clamp(rootYaw * Mth.DEG_TO_RAD, -0.85F, 0.85F);

                out.rightArmX = Mth.lerp(whipR, 0.68F * Math.max(0.55F, sprint), -0.82F)
                        - 0.72F * Mth.sin(whipR * Mth.PI);
                out.rightArmY = Mth.lerp(whipR, 0.32F, -0.42F);
                out.rightArmZ = Mth.lerp(whipR, 0.28F, 0.48F);

                out.leftArmX = Mth.lerp(whipL, 0.68F, -0.88F) - 0.62F * Mth.sin(whipL * Mth.PI);
                out.leftArmY = Mth.lerp(whipL, -0.32F, 0.42F);
                out.leftArmZ = Mth.lerp(whipL, -0.28F, -0.48F);

                out.legWeight = Mth.clamp(1.0F - moveWeight * 1.15F, 0.0F, 1.0F) * (1.0F - sprint);
                out.rightLegX = -0.42F;
                out.leftLegX = 0.48F;

                out.rightBladePitchDeg = Mth.rotLerp(whipR, DUAL_RUN_RIGHT_PITCH, -83.81F);
                out.rightBladeRollDeg = Mth.rotLerp(whipR, DUAL_RUN_RIGHT_ROLL, 17.26F);
                out.rightBladeYawDeg = Mth.rotLerp(whipR, DUAL_RUN_RIGHT_YAW, 75.96F);

                out.leftBladePitchDeg = Mth.rotLerp(whipL, DUAL_RUN_LEFT_PITCH, -172.66F);
                out.leftBladeRollDeg = Mth.rotLerp(whipL, DUAL_RUN_LEFT_ROLL, 28.86F);
                out.leftBladeYawDeg = Mth.rotLerp(whipL, DUAL_RUN_LEFT_YAW, 5.97F);
                out.firstPersonCameraRollDeg = Mth.lerp(whipL, -2.4F, 2.2F);
            }
            case DUAL_AIR_SLASH -> {
                // Đòn nhảy chém trên không: xoay 180° phẳng rộng ngoài thân, thân thẳng đứng &
                // đầu 0°!
                float spin = Mth.clamp(sweep, 0.0F, 1.0F);
                out.rootYawDeg = 180.0F * spin;
                out.rootPitchDeg = -4.0F;
                out.rootRollDeg = 6.0F;

                out.bodyX = 0.16F;
                out.headAddX = -4.0F * Mth.DEG_TO_RAD;
                out.headAddY = Mth.clamp(Mth.wrapDegrees(out.rootYawDeg) * Mth.DEG_TO_RAD * 0.45F, -0.85F, 0.85F);
                out.rightArmX = Mth.lerp(spin, -0.78F, -1.68F);
                out.leftArmX = Mth.lerp(spin, -0.82F, -1.64F);
                out.rightArmZ = 0.92F;
                out.leftArmZ = -0.92F;
                out.rightArmY = -0.22F;
                out.leftArmY = 0.22F;

                out.legWeight = 0.90F;
                out.rightLegX = -0.58F;
                out.leftLegX = 0.44F;

                out.rightBladePitchDeg = -139.32F;
                out.rightBladeRollDeg = -8.42F;
                out.rightBladeYawDeg = 160.89F;
                out.leftBladePitchDeg = -29.17F;
                out.leftBladeRollDeg = -23.79F;
                out.leftBladeYawDeg = -171.59F;
                out.firstPersonCameraRollDeg = 2.8F;
            }
            case DUAL_LANDING_DASH -> {
                // Nhịp cắt ngang tự động khi tiếp đất ngay sau DUAL_AIR_SLASH (2 tay mở rộng
                // phẳng ngang, thân thẳng đứng, đầu 0°!)
                float rootYaw = Mth.lerp(sweep, -34.0F, 34.0F) * (0.72F + 0.28F * runDamp);
                out.rootYawDeg = rootYaw;
                out.rootPitchDeg = -4.0F;
                out.rootTranslateY = -0.06F * runDamp;

                out.bodyX = 0.18F;
                out.bodyY = Mth.lerp(sweep, 0.22F, -0.22F);
                out.headAddX = -4.0F * Mth.DEG_TO_RAD;
                out.headAddY = Mth.clamp(rootYaw * Mth.DEG_TO_RAD, -0.85F, 0.85F);
                out.rightArmX = -1.36F;
                out.leftArmX = -1.32F;
                out.rightArmZ = 0.82F;
                out.leftArmZ = -0.82F;
                out.rightArmY = Mth.lerp(sweep, 0.55F, -0.42F);
                out.leftArmY = Mth.lerp(sweep, -0.55F, 0.42F);

                out.legWeight = Mth.clamp(1.0F - moveWeight * 1.15F, 0.0F, 1.0F) * (1.0F - sprint);
                out.rightLegX = -0.44F;
                out.leftLegX = 0.50F;
                out.rightBladePitchDeg = -139.32F;
                out.rightBladeRollDeg = -8.42F;
                out.rightBladeYawDeg = 160.89F;
                out.leftBladePitchDeg = -29.17F;
                out.leftBladeRollDeg = -23.79F;
                out.leftBladeYawDeg = -171.59F;
                out.firstPersonCameraRollDeg = -2.6F;
            }
            case FUSE_TRANSFORM -> {
                // Animation Hợp Nhất khi bấm [R] (18 ticks):
                // 0..50%: Kéo mượt 2 cây đao Trắng–Đen từ dáng Sunraku xoắn áp chặt trước ngực
                // -> 50%
                // LÓE SÁNG GHÉP KHỚP -> 50..100%: Hạ gọn về 1H Sword Idle!
                if (alpha < 0.50F) {
                    float t = smootherstep(alpha / 0.50F);
                    float invT = 1.0F - t;
                    out.rootPitchDeg = Mth.lerp(t, 0.0F, -4.0F);
                    out.rootTranslateY = Mth.lerp(t, -0.02F, -0.04F);
                    out.rootYawDeg = 0.0F;
                    out.bodyX = Mth.lerp(t, 0.14F, 0.12F);
                    out.bodyY = Mth.lerp(t, -0.20F, 0.0F);
                    out.bodyX = Mth.lerp(t, 0.05F, 0.12F);
                    out.bodyY = Mth.lerp(t, -0.15F, 0.0F);
                    out.bodyZ = 0.0F;
                    out.headAddX = Mth.lerp(t, -0.05F, out.rootPitchDeg * Mth.DEG_TO_RAD);
                    out.headAddY = Mth.lerp(t, 0.22F, 0.0F);
                    out.headAddZ = 0.0F;
                    out.rightArmX = Mth.lerp(t, SUNRAKU_R_ARM_X, -1.55F);
                    out.rightArmY = Mth.lerp(t, SUNRAKU_R_ARM_Y, 0.42F);
                    out.rightArmZ = Mth.lerp(t, SUNRAKU_R_ARM_Z, 0.12F);
                    out.leftArmX = Mth.lerp(t, SUNRAKU_L_ARM_X, -1.55F);
                    out.leftArmY = Mth.lerp(t, SUNRAKU_L_ARM_Y, -0.42F);
                    out.leftArmZ = Mth.lerp(t, SUNRAKU_L_ARM_Z, -0.12F);
                    out.elbowWeight = invT;
                    out.rightElbowX = SUNRAKU_R_ELB_X;
                    out.rightElbowY = SUNRAKU_R_ELB_Y;
                    out.rightElbowZ = SUNRAKU_R_ELB_Z;
                    out.leftElbowX = SUNRAKU_L_ELB_X;
                    out.leftElbowY = SUNRAKU_L_ELB_Y;
                    out.leftElbowZ = SUNRAKU_L_ELB_Z;
                    out.rightArmPosZ = SUNRAKU_R_ARM_FWD_Z * invT;
                    out.leftArmPosZ = SUNRAKU_L_ARM_FWD_Z * invT;
                    out.rightBladeOffX = SUNRAKU_RIGHT_OFF_X * invT;
                    out.rightBladeOffY = SUNRAKU_RIGHT_OFF_Y * invT;
                    out.rightBladeOffZ = SUNRAKU_RIGHT_OFF_Z * invT;
                    out.leftBladeOffX = SUNRAKU_LEFT_OFF_X * invT;
                    out.leftBladeOffY = SUNRAKU_LEFT_OFF_Y * invT;
                    out.leftBladeOffZ = SUNRAKU_LEFT_OFF_Z * invT;
                    out.rightBladePitchDeg = Mth.rotLerp(t, SUNRAKU_RIGHT_PITCH, -18.0F);
                    out.rightBladeRollDeg = Mth.rotLerp(t, SUNRAKU_RIGHT_ROLL, 90.0F);
                    out.rightBladeYawDeg = Mth.rotLerp(t, SUNRAKU_RIGHT_YAW, 0.0F);
                    out.leftBladePitchDeg = Mth.rotLerp(t, SUNRAKU_LEFT_PITCH, 20.0F);
                    out.leftBladeRollDeg = Mth.rotLerp(t, SUNRAKU_LEFT_ROLL, -60.0F);
                    out.leftBladeYawDeg = Mth.rotLerp(t, SUNRAKU_LEFT_YAW, 0.0F);
                    out.fovOffsetDeg = -5.0F * t;
                } else {
                    float settle = smootherstep((alpha - 0.50F) / 0.50F);
                    out.rootYawDeg = Mth.lerp(settle, 0.0F, -12.0F);
                    out.rootPitchDeg = Mth.lerp(settle, -4.0F, 0.0F);
                    out.rootTranslateY = Mth.lerp(settle, -0.04F, -0.02F);
                    out.bodyX = Mth.lerp(settle, 0.14F, 0.05F);
                    out.bodyY = Mth.lerp(settle, -0.18F, 0.12F);
                    out.bodyZ = 0.0F;
                    out.headAddX = out.rootPitchDeg * Mth.DEG_TO_RAD;
                    out.headAddY = out.rootYawDeg * Mth.DEG_TO_RAD;
                    // Hạ gọn thanh kiếm cong 1 tay về Combat Idle Stance (0 clip, chuẩn Forward
                    // Grip!)
                    out.rightArmX = Mth.lerp(settle, -1.55F, 0.12F);
                    out.rightArmY = Mth.lerp(settle, 0.45F, -0.06F);
                    out.rightArmZ = Mth.lerp(settle, -0.12F, 0.34F);
                    out.leftArmX = Mth.lerp(settle, -1.55F, -0.48F);
                    out.leftArmY = Mth.lerp(settle, -0.45F, 0.36F);
                    out.leftArmZ = Mth.lerp(settle, 0.12F, 0.10F);
                    out.rightBladePitchDeg = Mth.rotLerp(settle, -12.0F, FUSED_BLADE_PITCH);
                    out.rightBladeRollDeg = Mth.rotLerp(settle, 15.0F, FUSED_BLADE_ROLL);
                    out.rightBladeYawDeg = Mth.rotLerp(settle, 165.0F, FUSED_BLADE_YAW);
                    out.fovOffsetDeg = (1.0F - settle) * 6.5F;
                }
                applyAttackLegs(out, idle, standLegWeight * 0.90F, 0.14F, -0.12F, 0.08F);
            }
            case UNFUSE_SPLIT -> {
                // Animation Tách Song Nguyệt -> Song Đao (12 ticks), thu êm về dáng thủ mềm
                // mượt!
                if (alpha < 0.40F) {
                    float t = smootherstep(alpha / 0.40F);
                    out.rightArmX = Mth.lerp(t, 0.12F, -1.52F);
                    out.rightArmY = Mth.lerp(t, -0.06F, 0.45F);
                    out.rightArmZ = Mth.lerp(t, 0.34F, -0.10F);
                    out.leftArmX = Mth.lerp(t, -0.48F, -1.52F);
                    out.leftArmY = Mth.lerp(t, 0.36F, -0.45F);
                    out.leftArmZ = Mth.lerp(t, 0.10F, 0.10F);
                    out.rightBladePitchDeg = Mth.rotLerp(t, FUSED_BLADE_PITCH, 0.0F);
                    out.rightBladeRollDeg = Mth.rotLerp(t, FUSED_BLADE_ROLL, 0.0F);
                    out.rightBladeYawDeg = Mth.rotLerp(t, FUSED_BLADE_YAW, 0.0F);
                } else {
                    float t = smootherstep((alpha - 0.40F) / 0.60F);
                    out.rootYawDeg = 0.0F;
                    out.rootTranslateY = Mth.lerp(t, 0.0F, -0.032F);
                    out.bodyX = Mth.lerp(t, 0.0F, 0.10F);
                    out.bodyY = Mth.lerp(t, 0.0F, -0.22F);
                    out.bodyZ = 0.0F;
                    out.headAddX = Mth.lerp(t, 0.0F, -0.05F);
                    out.headAddY = Mth.lerp(t, 0.0F, 0.22F);
                    out.headAddZ = 0.0F;
                    out.rightArmX = Mth.lerp(t, -1.25F, SUNRAKU_R_ARM_X);
                    out.rightArmY = Mth.lerp(t, -0.25F, SUNRAKU_R_ARM_Y);
                    out.rightArmZ = Mth.lerp(t, 0.30F, SUNRAKU_R_ARM_Z);
                    out.leftArmX = Mth.lerp(t, -1.25F, SUNRAKU_L_ARM_X);
                    out.leftArmY = Mth.lerp(t, 0.25F, SUNRAKU_L_ARM_Y);
                    out.leftArmZ = Mth.lerp(t, -0.30F, SUNRAKU_L_ARM_Z);
                    out.elbowWeight = t;
                    out.rightElbowX = SUNRAKU_R_ELB_X;
                    out.rightElbowY = SUNRAKU_R_ELB_Y;
                    out.rightElbowZ = SUNRAKU_R_ELB_Z;
                    out.leftElbowX = SUNRAKU_L_ELB_X;
                    out.leftElbowY = SUNRAKU_L_ELB_Y;
                    out.leftElbowZ = SUNRAKU_L_ELB_Z;
                    out.rightArmPosZ = SUNRAKU_R_ARM_FWD_Z * t;
                    out.leftArmPosZ = SUNRAKU_L_ARM_FWD_Z * t;
                    out.rightBladeOffX = SUNRAKU_RIGHT_OFF_X * t;
                    out.rightBladeOffY = SUNRAKU_RIGHT_OFF_Y * t;
                    out.rightBladeOffZ = SUNRAKU_RIGHT_OFF_Z * t;
                    out.leftBladeOffX = SUNRAKU_LEFT_OFF_X * t;
                    out.leftBladeOffY = SUNRAKU_LEFT_OFF_Y * t;
                    out.leftBladeOffZ = SUNRAKU_LEFT_OFF_Z * t;
                    out.rightBladePitchDeg = Mth.rotLerp(t, 0.0F, SUNRAKU_RIGHT_PITCH);
                    out.rightBladeRollDeg = Mth.rotLerp(t, 0.0F, SUNRAKU_RIGHT_ROLL);
                    out.rightBladeYawDeg = Mth.rotLerp(t, 0.0F, SUNRAKU_RIGHT_YAW);
                    out.leftBladePitchDeg = Mth.rotLerp(t, 0.0F, SUNRAKU_LEFT_PITCH);
                    out.leftBladeRollDeg = Mth.rotLerp(t, 0.0F, SUNRAKU_LEFT_ROLL);
                    out.leftBladeYawDeg = Mth.rotLerp(t, 0.0F, SUNRAKU_LEFT_YAW);
                }
            }
            case FUSED_1 -> {
                // Song Nguyệt Nhát 1 (0.3s cadence) — Bổ Chém Cắm Xuống Phải -> Trái (Downward
                // Plunging Cleave!):
                // Giơ cao đỉnh đầu-phải (-2.28) rồi bổ cắm thẳng xuống mặt đất (-0.42), đầu
                // khóa 0°!
                float s = snappyStrikeSweep(alpha, 0.14F, 0.50F);
                float e = snappyEnvelope(alpha, 0.08F, 0.78F);
                float rootYaw = Mth.lerp(s, -16.0F, 18.0F);

                out.rootYawDeg = Mth.lerp(e, idle.rootYawDeg, rootYaw);
                out.rootPitchDeg = Mth.lerp(e, idle.rootPitchDeg, 0.0F);
                out.rootTranslateY = Mth.lerp(e, idle.rootTranslateY, lerp3(s, 0.0F, -0.04F, -0.07F));
                out.bodyX = Mth.lerp(e, idle.bodyX, lerp3(s, -0.10F, 0.16F, 0.24F));
                out.bodyY = Mth.lerp(e, idle.bodyY, lerp3(s, 0.18F, 0.0F, -0.20F));
                out.headAddX = 0.0F;
                out.headAddY = Mth.lerp(e, idle.headAddY, Mth.clamp(rootYaw * Mth.DEG_TO_RAD, -0.85F, 0.85F));

                out.rightArmX = Mth.lerp(e, idle.rightArmX, lerp3(s, -2.28F, -1.28F, -0.42F));
                out.rightArmY = Mth.lerp(e, idle.rightArmY, lerp3(s, 0.54F, 0.12F, -0.26F));
                out.rightArmZ = Mth.lerp(e, idle.rightArmZ, lerp3(s, 0.62F, 0.44F, 0.48F));

                out.leftArmX = Mth.lerp(e, idle.leftArmX, lerp3(s, -0.64F, 0.22F, 0.48F));
                out.leftArmY = Mth.lerp(e, idle.leftArmY, lerp3(s, 0.42F, -0.12F, -0.20F));
                out.leftArmZ = Mth.lerp(e, idle.leftArmZ, lerp3(s, -0.26F, -0.42F, -0.48F));

                out.rightBladePitchDeg = Mth.rotLerp(e, idle.rightBladePitchDeg,
                        rotLerp3(s, -104.29F, -105.26F, -82.16F));
                out.rightBladeRollDeg = Mth.rotLerp(e, idle.rightBladeRollDeg, rotLerp3(s, 5.13F, 35.50F, 35.24F));
                out.rightBladeYawDeg = Mth.rotLerp(e, idle.rightBladeYawDeg, rotLerp3(s, -103.17F, -141.26F, -130.15F));
                out.firstPersonCameraRollDeg = -2.4F * e;
                applyAttackLegs(out, idle, e * standLegWeight, Mth.lerp(s, 0.32F, -0.44F), Mth.lerp(s, -0.28F, 0.48F),
                        0.20F);
            }
            case FUSED_2 -> {
                // Song Nguyệt Nhát 2 (0.3s cadence) — Bổ Chém Cắm Xuống Trái -> Phải (Reverse
                // Downward Plunging Cleave!):
                // Giơ cao đỉnh đầu-trái (-2.26) rồi bổ cắm thẳng xuống chéo phải (-0.42), đầu
                // khóa 0°!
                float s = snappyStrikeSweep(alpha, 0.14F, 0.50F);
                float e = snappyEnvelope(alpha, 0.08F, 0.78F);
                float rootYaw = Mth.lerp(s, 16.0F, -18.0F);

                out.rootYawDeg = Mth.lerp(e, idle.rootYawDeg, rootYaw);
                out.rootPitchDeg = Mth.lerp(e, idle.rootPitchDeg, 0.0F);
                out.rootTranslateY = Mth.lerp(e, idle.rootTranslateY, lerp3(s, 0.0F, -0.04F, -0.07F));
                out.bodyX = Mth.lerp(e, idle.bodyX, lerp3(s, -0.10F, 0.16F, 0.24F));
                out.bodyY = Mth.lerp(e, idle.bodyY, lerp3(s, -0.18F, 0.0F, 0.22F));
                out.headAddX = 0.0F;
                out.headAddY = Mth.lerp(e, idle.headAddY, Mth.clamp(rootYaw * Mth.DEG_TO_RAD, -0.85F, 0.85F));

                out.rightArmX = Mth.lerp(e, idle.rightArmX, lerp3(s, -2.26F, -1.28F, -0.42F));
                out.rightArmY = Mth.lerp(e, idle.rightArmY, lerp3(s, -0.22F, 0.18F, 0.58F));
                out.rightArmZ = Mth.lerp(e, idle.rightArmZ, lerp3(s, 0.46F, 0.42F, 0.48F));

                out.leftArmX = Mth.lerp(e, idle.leftArmX, lerp3(s, -0.72F, 0.20F, 0.48F));
                out.leftArmY = Mth.lerp(e, idle.leftArmY, lerp3(s, 0.46F, -0.12F, -0.22F));
                out.leftArmZ = Mth.lerp(e, idle.leftArmZ, lerp3(s, -0.22F, -0.44F, -0.48F));

                out.rightBladePitchDeg = Mth.rotLerp(e, idle.rightBladePitchDeg,
                        rotLerp3(s, -103.13F, -116.33F, -103.16F));
                out.rightBladeRollDeg = Mth.rotLerp(e, idle.rightBladeRollDeg, rotLerp3(s, -30.42F, 4.14F, 8.60F));
                out.rightBladeYawDeg = Mth.rotLerp(e, idle.rightBladeYawDeg, rotLerp3(s, 169.81F, 173.33F, 134.95F));
                out.firstPersonCameraRollDeg = 2.4F * e;
                applyAttackLegs(out, idle, e * standLegWeight, Mth.lerp(s, -0.28F, 0.44F), Mth.lerp(s, 0.32F, -0.42F),
                        0.20F);
            }
            case FUSED_3 -> {
                // Song Nguyệt Nhát 3 (0.3s cadence) — Chém Hất Vòng Cung 1 Tay Thanh Thoát
                // (Low-Left -> High-Right Rising Crescent Sweep!):
                // Thay thế hoàn toàn kiểu bổ dọc 2 tay cục mịch: tay phải quét vòng cung rộng
                // mượt ngoài thân, tay trái đánh thăng bằng tự nhiên,
                // đầu khóa 0° nhìn thẳng mục tiêu, 0 xuyên đầu/thân (bC=0 hC=0)!
                float s = snappyStrikeSweep(alpha, 0.12F, 0.50F);
                float e = snappyEnvelope(alpha, 0.08F, 0.80F);
                float rootYaw = lerp3(s, 26.0F, 0.0F, -28.0F);

                out.rootYawDeg = Mth.lerp(e, idle.rootYawDeg, rootYaw);
                out.rootPitchDeg = Mth.lerp(e, idle.rootPitchDeg, 0.0F);
                out.rootTranslateY = Mth.lerp(e, idle.rootTranslateY, lerp3(s, -0.05F, -0.02F, 0.02F));
                out.bodyX = Mth.lerp(e, idle.bodyX, lerp3(s, 0.14F, 0.10F, 0.08F));
                out.bodyY = Mth.lerp(e, idle.bodyY, lerp3(s, -0.24F, 0.0F, 0.26F));
                out.headAddX = 0.0F;
                out.headAddY = Mth.lerp(e, idle.headAddY, Mth.clamp(rootYaw * Mth.DEG_TO_RAD, -0.85F, 0.85F));

                out.rightArmX = Mth.lerp(e, idle.rightArmX, lerp3(s, -0.92F, -1.26F, -1.52F));
                out.rightArmY = Mth.lerp(e, idle.rightArmY, lerp3(s, -0.32F, 0.26F, 0.86F));
                out.rightArmZ = Mth.lerp(e, idle.rightArmZ, lerp3(s, 0.44F, 0.56F, 0.44F));

                out.leftArmX = Mth.lerp(e, idle.leftArmX, lerp3(s, -0.52F, 0.14F, 0.44F));
                out.leftArmY = Mth.lerp(e, idle.leftArmY, lerp3(s, 0.36F, -0.14F, -0.20F));
                out.leftArmZ = Mth.lerp(e, idle.leftArmZ, lerp3(s, -0.22F, -0.44F, -0.48F));

                out.rightBladePitchDeg = Mth.rotLerp(e, idle.rightBladePitchDeg,
                        rotLerp3(s, -58.44F, -70.86F, -77.37F));
                out.rightBladeRollDeg = Mth.rotLerp(e, idle.rightBladeRollDeg, rotLerp3(s, -16.44F, -25.81F, -24.69F));
                out.rightBladeYawDeg = Mth.rotLerp(e, idle.rightBladeYawDeg, rotLerp3(s, 89.10F, 95.57F, 84.23F));
                out.firstPersonCameraRollDeg = 2.6F * e;
                out.fovOffsetDeg = Mth.lerp(s, -1.5F, 3.0F) * e;
                applyAttackLegs(out, idle, e * standLegWeight, Mth.lerp(s, -0.32F, 0.42F),
                        Mth.lerp(s, 0.34F, -0.44F), 0.20F);
            }
            case CRESCENT_WAVE_SLASH -> {
                // Signature RMB — Quick Crescent Wave Draw-Slash (Trái -> Phải):
                // Đồng bộ 100% rootYaw (+32° -> -34°) & bodyY (-0.28 -> +0.30), thân đứng vững
                // (bodyX=0.16, rootPitch=-3°, head=0°),
                // lưỡi trắng sắc phẳng ngang tuyệt đối (dotE=+1.00, 0 clip!) phóng sóng lưỡi
                // liềm xoay tròn Trắng–Đen!
                float s = snappyStrikeSweep(alpha, 0.16F, 0.48F);
                float e = snappyEnvelope(alpha, 0.10F, 0.78F);
                float rootYaw = Mth.lerp(s, 32.0F, -34.0F);
                float targetPitch = Mth.lerp(s, -3.0F, -2.0F);
                float bodyY = Mth.lerp(s, -0.28F, 0.30F);

                out.rootYawDeg = Mth.lerp(e, idle.rootYawDeg, rootYaw);
                out.rootPitchDeg = Mth.lerp(e, idle.rootPitchDeg, targetPitch);
                out.rootTranslateY = Mth.lerp(e, idle.rootTranslateY, Mth.lerp(s, -0.07F, -0.05F));
                out.bodyX = Mth.lerp(e, idle.bodyX, Mth.lerp(s, 0.16F, 0.14F));
                out.bodyY = Mth.lerp(e, idle.bodyY, bodyY);
                out.headAddX = Mth.lerp(e, idle.headAddX, targetPitch * Mth.DEG_TO_RAD); // Đầu khóa thẳng 0.0°!
                out.headAddY = Mth.lerp(e, idle.headAddY, Mth.clamp(rootYaw * Mth.DEG_TO_RAD, -0.85F, 0.85F));

                out.rightArmX = Mth.lerp(e, idle.rightArmX, Mth.lerp(s, -1.24F, -1.50F));
                out.rightArmY = Mth.lerp(e, idle.rightArmY, Mth.lerp(s, -0.24F, 0.96F));
                out.rightArmZ = Mth.lerp(e, idle.rightArmZ, Mth.lerp(s, 0.52F, 0.44F));

                out.leftArmX = Mth.lerp(e, idle.leftArmX, Mth.lerp(s, -0.62F, 0.48F));
                out.leftArmY = Mth.lerp(e, idle.leftArmY, Mth.lerp(s, 0.44F, -0.20F));
                out.leftArmZ = Mth.lerp(e, idle.leftArmZ, Mth.lerp(s, -0.18F, -0.48F));

                float invE = 1.0F - e;
                out.elbowWeight = idle.elbowWeight * invE;
                out.rightElbowX = idle.rightElbowX;
                out.rightElbowY = idle.rightElbowY;
                out.rightElbowZ = idle.rightElbowZ;
                out.leftElbowX = idle.leftElbowX;
                out.leftElbowY = idle.leftElbowY;
                out.leftElbowZ = idle.leftElbowZ;
                out.rightArmPosZ = idle.rightArmPosZ * invE;
                out.leftArmPosZ = idle.leftArmPosZ * invE;
                out.rightBladeOffX = idle.rightBladeOffX * invE;
                out.rightBladeOffY = idle.rightBladeOffY * invE;
                out.rightBladeOffZ = idle.rightBladeOffZ * invE;
                out.leftBladeOffX = idle.leftBladeOffX * invE;
                out.leftBladeOffY = idle.leftBladeOffY * invE;
                out.leftBladeOffZ = idle.leftBladeOffZ * invE;

                out.rightBladePitchDeg = Mth.rotLerp(e, idle.rightBladePitchDeg, Mth.rotLerp(s, -77.79F, -106.99F));
                out.rightBladeRollDeg = Mth.rotLerp(e, idle.rightBladeRollDeg, Mth.rotLerp(s, -29.84F, -3.75F));
                out.rightBladeYawDeg = Mth.rotLerp(e, idle.rightBladeYawDeg, Mth.rotLerp(s, 122.77F, 105.86F));
                out.firstPersonCameraRollDeg = 3.2F * e;
                out.fovOffsetDeg = Mth.lerp(s, -2.0F, 4.5F) * e;
                applyAttackLegs(out, idle, e * standLegWeight, Mth.lerp(s, 0.46F, -0.48F), Mth.lerp(s, -0.42F, 0.46F),
                        0.22F);
            }
            case CRESCENT_VORPAL_CHARGE -> {
                // PHA GỒNG LỰC RÚT KIẾM ZENITSU 3 GIÂY (60 TICKS — HEKIREKI ISSEN / IAIJUTSU
                // CROUCH DRAW):
                // - Khuỵu thấp dồn trọng tâm, chân trái trụ trước, chân phải đạp dài ra sau như
                // lò xo!
                // - Tay phải vắt ngang bụng xuống HÔNG TRÁI nắm chắc cán kiếm, tay trái giữ sát
                // cổ kiếm hông trái!
                // - Đại đao nằm xuôi gọn dọc hông trái vươn ra sau-trái (0 cầm ngược, 0 xuyên
                // thân), rung điện tăng dần, FOV co -11°!
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

                // Giữ chặt thế khuỵu tấn rút kiếm Zenitsu (chân trái bước sâu trước, chân phải
                // đạp dài sau!)
                out.legWeight = enter;
                out.leftLegX = Mth.lerp(enter, idle.leftLegX, -0.82F);
                out.leftLegY = Mth.lerp(enter, idle.leftLegY, -0.18F);
                out.leftLegZ = Mth.lerp(enter, idle.leftLegZ, -0.22F);
                out.rightLegX = Mth.lerp(enter, idle.rightLegX, 0.88F);
                out.rightLegY = Mth.lerp(enter, idle.rightLegY, 0.18F);
                out.rightLegZ = Mth.lerp(enter, idle.rightLegZ, 0.24F);
            }
            case CRESCENT_VORPAL -> {
                // PHA BÙNG NỔ RÚT KIẾM IAIJUTSU TỐI THƯỢNG SAU 3 GIÂY GỒNG (18 TICKS — kiểm
                // chứng 3-frame phẳng ngang: dotEdge=+0.97..0.99, hClip=0!):
                // Rút phăng Đại Đao ngang 180° phẳng từ trái bung rộng sang mạn phải + bung FOV
                // +13° rồi tách về 2 thanh!
                if (alpha < 0.65F) {
                    float cut = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.28F, 0.0F, 1.0F));
                    out.rootYawDeg = Mth.lerp(cut, 46.0F, -52.0F);
                    out.rootPitchDeg = Mth.lerp(cut, -18.0F, -12.0F);
                    out.rootTranslateY = Mth.lerp(cut, -0.24F, -0.12F);
                    out.bodyX = Mth.lerp(cut, 0.28F, 0.20F);
                    out.bodyY = Mth.lerp(cut, -0.34F, 0.52F);
                    out.bodyZ = Mth.lerp(cut, 0.04F, -0.04F);
                    out.headAddX = Mth.lerp(cut, -0.20F, -0.10F);
                    out.headAddY = Mth.lerp(cut, -0.25F, 0.38F);

                    out.rightArmX = Mth.lerp(cut, -1.46F, -1.52F);
                    out.rightArmY = Mth.lerp(cut, -0.24F, 1.08F);
                    out.rightArmZ = 0.12F;

                    out.leftArmX = Mth.lerp(cut, -0.36F, 0.58F);
                    out.leftArmY = Mth.lerp(cut, 0.18F, -0.22F);
                    out.leftArmZ = Mth.lerp(cut, -0.14F, -0.48F);

                    out.rightBladePitchDeg = Mth.rotLerp(cut, VORPAL_START_PITCH, VORPAL_END_PITCH);
                    out.rightBladeRollDeg = Mth.rotLerp(cut, VORPAL_START_ROLL, VORPAL_END_ROLL);
                    out.rightBladeYawDeg = Mth.rotLerp(cut, VORPAL_START_YAW, VORPAL_END_YAW);
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
                    out.rootYawDeg = Mth.lerp(rec, -52.0F, idle.rootYawDeg);
                    out.rootPitchDeg = Mth.lerp(rec, -12.0F, idle.rootPitchDeg);
                    out.rootTranslateY = Mth.lerp(rec, -0.12F, idle.rootTranslateY);
                    out.bodyX = Mth.lerp(rec, 0.20F, idle.bodyX);
                    out.bodyY = Mth.lerp(rec, 0.52F, idle.bodyY);
                    out.bodyZ = Mth.lerp(rec, -0.04F, idle.bodyZ);
                    out.headAddX = Mth.lerp(rec, -0.10F, idle.headAddX);
                    out.headAddY = Mth.lerp(rec, 0.38F, idle.headAddY);

                    out.rightArmX = Mth.lerp(rec, -1.52F, idle.rightArmX);
                    out.rightArmY = Mth.lerp(rec, 1.08F, idle.rightArmY);
                    out.rightArmZ = Mth.lerp(rec, 0.12F, idle.rightArmZ);

                    out.leftArmX = Mth.lerp(rec, 0.58F, idle.leftArmX);
                    out.leftArmY = Mth.lerp(rec, -0.22F, idle.leftArmY);
                    out.leftArmZ = Mth.lerp(rec, -0.48F, idle.leftArmZ);

                    out.rightBladePitchDeg = Mth.rotLerp(rec, VORPAL_END_PITCH, idle.rightBladePitchDeg);
                    out.rightBladeRollDeg = Mth.rotLerp(rec, VORPAL_END_ROLL, idle.rightBladeRollDeg);
                    out.rightBladeYawDeg = Mth.rotLerp(rec, VORPAL_END_YAW, idle.rightBladeYawDeg);
                    out.firstPersonCameraRollDeg = (1.0F - rec) * 4.4F;
                    out.fovOffsetDeg = (1.0F - rec) * 13.0F;
                    applyAttackLegs(out, idle, (1.0F - rec) * standLegWeight, -0.68F, 0.58F, 0.24F);
                }
            }
            case DUAL_PIN_LUNGE -> {
                // Pha 1 Chiêu Dạng Thường: Duỗi khuỷu mượt lao tốc độ cao vồ xuống đè kẻ địch!
                float t = smootherstep(alpha);
                float ew = idle.elbowWeight * (1.0F - t);
                out.rootPitchDeg = Mth.lerp(t, idle.rootPitchDeg, -30.0F);
                out.rootTranslateY = Mth.lerp(t, idle.rootTranslateY, -0.18F);
                out.bodyX = Mth.lerp(t, idle.bodyX, 0.46F);
                out.bodyY = Mth.lerp(t, idle.bodyY, 0.0F);
                out.bodyZ = Mth.lerp(t, idle.bodyZ, 0.0F);
                out.headAddX = Mth.lerp(t, idle.headAddX, -0.26F);
                out.rightArmX = Mth.lerp(t, idle.rightArmX, 0.95F);
                out.rightArmY = Mth.lerp(t, idle.rightArmY, 0.28F);
                out.rightArmZ = Mth.lerp(t, idle.rightArmZ, 0.48F);
                out.leftArmX = Mth.lerp(t, idle.leftArmX, 0.95F);
                out.leftArmY = Mth.lerp(t, idle.leftArmY, -0.28F);
                out.leftArmZ = Mth.lerp(t, idle.leftArmZ, -0.48F);
                out.elbowWeight = ew;
                out.rightElbowX = idle.rightElbowX;
                out.rightElbowY = idle.rightElbowY;
                out.rightElbowZ = idle.rightElbowZ;
                out.leftElbowX = idle.leftElbowX;
                out.leftElbowY = idle.leftElbowY;
                out.leftElbowZ = idle.leftElbowZ;
                out.rightArmPosZ = idle.rightArmPosZ * (1.0F - t);
                out.leftArmPosZ = idle.leftArmPosZ * (1.0F - t);
                out.rightBladeOffX = idle.rightBladeOffX * (1.0F - t);
                out.rightBladeOffY = idle.rightBladeOffY * (1.0F - t);
                out.rightBladeOffZ = idle.rightBladeOffZ * (1.0F - t);
                out.leftBladeOffX = idle.leftBladeOffX * (1.0F - t);
                out.leftBladeOffY = idle.leftBladeOffY * (1.0F - t);
                out.leftBladeOffZ = idle.leftBladeOffZ * (1.0F - t);
                out.rightBladePitchDeg = Mth.rotLerp(t, idle.rightBladePitchDeg, -105.0F);
                out.rightBladeRollDeg = Mth.rotLerp(t, idle.rightBladeRollDeg, 0.0F);
                out.rightBladeYawDeg = Mth.rotLerp(t, idle.rightBladeYawDeg, 0.0F);
                out.leftBladePitchDeg = Mth.rotLerp(t, idle.leftBladePitchDeg, 22.0F);
                out.leftBladeRollDeg = Mth.rotLerp(t, idle.leftBladeRollDeg, -16.0F);
                out.leftBladeYawDeg = Mth.rotLerp(t, idle.leftBladeYawDeg, 0.0F);
                out.fovOffsetDeg = 8.5F * t;
                applyAttackLegs(out, idle, 0.95F, 0.72F, -0.64F, 0.24F);
            }
            case DUAL_PIN_FLURRY -> {
                // Pha 2 Chiêu Dạng Thường: Khuỵu thấp đè chặt mục tiêu & ĐÂM LIÊN THANH 12 NHÁT
                // SO LE TRẮNG–ĐEN VÔ ĐỊCH, cuối đòn gập khuỷu thu mượt về thế thủ!
                float endFade = alpha > 0.84F ? (1.0F - smootherstep((alpha - 0.84F) / 0.16F)) : 1.0F;
                float back = 1.0F - endFade;
                // 6 chu kỳ sin = 12 đỉnh đâm so le 2 tay khớp 1:1 với 12 nhát server!
                float wave = Mth.sin(alpha * Mth.TWO_PI * 6.0F);
                float thrustR = Math.max(0.0F, wave);
                float thrustL = Math.max(0.0F, -wave);

                out.rootPitchDeg = Mth.lerp(endFade, idle.rootPitchDeg, -24.0F);
                out.rootTranslateY = Mth.lerp(endFade, idle.rootTranslateY, -0.26F);
                out.bodyX = Mth.lerp(endFade, idle.bodyX, 0.44F);
                out.bodyY = Mth.lerp(endFade, idle.bodyY, wave * 0.34F);
                out.bodyZ = Mth.lerp(endFade, idle.bodyZ, 0.0F);
                out.headAddX = Mth.lerp(endFade, idle.headAddX, 0.22F);
                out.headAddY = Mth.lerp(endFade, idle.headAddY, -wave * 0.24F);

                // Tay phải (Trăng Khuyết Đen) & Tay trái (Trăng Non Trắng) luân phiên đâm xối
                // xả xuống mục tiêu!
                out.rightArmX = Mth.lerp(endFade, idle.rightArmX, Mth.lerp(thrustR, -0.62F, -1.92F));
                out.rightArmY = Mth.lerp(endFade, idle.rightArmY, Mth.lerp(thrustR, -0.32F, 0.22F));
                out.rightArmZ = Mth.lerp(endFade, idle.rightArmZ, Mth.lerp(thrustR, 0.38F, -0.06F));

                out.leftArmX = Mth.lerp(endFade, idle.leftArmX, Mth.lerp(thrustL, -0.62F, -1.92F));
                out.leftArmY = Mth.lerp(endFade, idle.leftArmY, Mth.lerp(thrustL, 0.32F, -0.22F));
                out.leftArmZ = Mth.lerp(endFade, idle.leftArmZ, Mth.lerp(thrustL, -0.38F, 0.06F));

                out.elbowWeight = idle.elbowWeight * back;
                out.rightElbowX = idle.rightElbowX;
                out.rightElbowY = idle.rightElbowY;
                out.rightElbowZ = idle.rightElbowZ;
                out.leftElbowX = idle.leftElbowX;
                out.leftElbowY = idle.leftElbowY;
                out.leftElbowZ = idle.leftElbowZ;
                out.rightArmPosZ = idle.rightArmPosZ * back;
                out.leftArmPosZ = idle.leftArmPosZ * back;
                out.rightBladeOffX = idle.rightBladeOffX * back;
                out.rightBladeOffY = idle.rightBladeOffY * back;
                out.rightBladeOffZ = idle.rightBladeOffZ * back;
                out.leftBladeOffX = idle.leftBladeOffX * back;
                out.leftBladeOffY = idle.leftBladeOffY * back;
                out.leftBladeOffZ = idle.leftBladeOffZ * back;

                // Mũi 2 lưỡi chĩa thẳng về trước hướng đâm xuyên (tay trái giữ Reverse Grip
                // chuẩn Dual Knife!)
                out.rightBladePitchDeg = Mth.rotLerp(endFade, idle.rightBladePitchDeg, 62.0F);
                out.rightBladeRollDeg = Mth.rotLerp(endFade, idle.rightBladeRollDeg, 10.0F);
                out.rightBladeYawDeg = Mth.rotLerp(endFade, idle.rightBladeYawDeg, 0.0F);

                out.leftBladePitchDeg = Mth.rotLerp(endFade, idle.leftBladePitchDeg, 24.0F);
                out.leftBladeRollDeg = Mth.rotLerp(endFade, idle.leftBladeRollDeg, -42.0F);
                out.leftBladeYawDeg = Mth.rotLerp(endFade, idle.leftBladeYawDeg, 14.0F);

                out.firstPersonCameraRollDeg = wave * 2.8F * endFade;
                out.fovOffsetDeg = 4.5F * endFade;
                applyAttackLegs(out, idle, 0.96F * endFade, -0.68F, 0.72F, 0.28F);
            }
        }
    }

    /**
     * Snappy sword easing: fast acceleration during strike (0.08s–0.12s), smooth
     * ease-out deceleration into follow-through hold.
     */
    private static float snappyStrikeSweep(float alpha, float windupEnd, float strikeEnd) {
        if (alpha <= windupEnd) {
            return 0.0F;
        }
        if (alpha < strikeEnd) {
            float t = (alpha - windupEnd) / Math.max(0.01F, strikeEnd - windupEnd);
            return DualBladeAnimState.easeOutQuart(t);
        }
        return 1.0F;
    }

    private static float snappyEnvelope(float alpha, float enterEnd, float holdEnd) {
        if (alpha < enterEnd) {
            return DualBladeAnimState.easeOutCubic(alpha / Math.max(0.01F, enterEnd));
        }
        if (alpha <= holdEnd) {
            return 1.0F;
        }
        return 1.0F - DualBladeAnimState.easeInOutCubic((alpha - holdEnd) / Math.max(0.01F, 1.0F - holdEnd));
    }

    private static float lerp3(float t, float a, float b, float c) {
        if (t <= 0.5F) {
            return Mth.lerp(t * 2.0F, a, b);
        }
        return Mth.lerp((t - 0.5F) * 2.0F, b, c);
    }

    private static float rotLerp3(float t, float a, float b, float c) {
        if (t <= 0.5F) {
            return Mth.rotLerp(t * 2.0F, a, b);
        }
        return Mth.rotLerp((t - 0.5F) * 2.0F, b, c);
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
        pose.headAddZ *= s;
        pose.armWeight *= s;
        pose.elbowWeight *= s;
        pose.rightArmPosX *= s;
        pose.rightArmPosZ *= s;
        pose.leftArmPosX *= s;
        pose.leftArmPosZ *= s;
        pose.rightBladeOffX *= s;
        pose.rightBladeOffY *= s;
        pose.rightBladeOffZ *= s;
        pose.leftBladeOffX *= s;
        pose.leftBladeOffY *= s;
        pose.leftBladeOffZ *= s;
        pose.legWeight *= s;
        pose.firstPersonCameraRollDeg *= s;
        pose.fovOffsetDeg *= s;
    }
}
