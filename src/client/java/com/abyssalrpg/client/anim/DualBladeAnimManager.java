package com.abyssalrpg.client.anim;

import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.renderer.vfx.DualBladeTrailRenderer;
import com.abyssalrpg.combat.DualBladeMoveType;
import com.abyssalrpg.network.DualBladeComboPayload;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Hệ thống Rig Animation Toàn Thân 5 Nhịp Cực Rõ Nét (`DualBladeAnimManager` v2):
 * - Mỗi nhịp kéo dài `11.5–14.0` tick có 3 pha rõ ràng:
 *   + `0% -> 40%`: Bùng nổ cực nhanh tới biên độ lớn (`35°–42°` đổ người, xoay vai `±75°`, sải tay vắt chéo tối đa, thế chân tấn/đá rõ nét).
 *   + `40% -> 80%`: Giữ dáng (`Pose Hold` ~4–5 tick) để mắt người chơi thấy rõ mồn một dáng kiếm sĩ ở góc nhìn F5!
 *   + `80% -> 100%`: Thu mượt về thế thủ song đao (hoặc tự cross-fade nối đà nếu đánh tiếp ở tick 8).
 * - Chuỗi 5 Nhịp phân biệt cực rõ bằng mắt thường:
 *   1. `COMBO_1` (11.5t): Đổ người bước lướt chém chéo rộng Tay Phải (Phải trên -> Trái dưới).
 *   2. `COMBO_2` (11.5t): Xoay bật hông chém hất ngược Tay Trái (Trái dưới -> Phải trên).
 *   3. `COMBO_3` (12.5t): 2 tay quét đồng thời 2 vòng cung chéo ngược chiều (*Ancient Sculk Twinblade*).
 *   4. `COMBO_4` (14.0t): Hạ trọng tâm XOAY TRÒN TOÀN THÂN `360°` cực rõ + AOE 3.5 block.
 *   5. `COMBO_5` (13.5t): Finisher — Bật nhảy nhẹ xoắn thân rồi vung chéo cả 2 đao bổ chữ X mạnh xuống!
 * - Đòn phụ: `SPRINT_THRUST` (11.0t), `AIR_SLASH` (11.5t) + `SKILL_DASH` (9.0t cắt ngang khi tiếp đất).
 */
public final class DualBladeAnimManager {
    private static final float STANCE_BLEND_IN = 0.22F;  // ~4.5 ticks vào thế thủ vắt chéo 2 tầng
    private static final float STANCE_BLEND_OUT = 0.20F; // ~5 ticks trả mượt sạch về vanilla
    private static final float SPRINT_BLEND_IN = 0.25F;  // ~4 ticks vào mượt tư thế chạy Naruto Shinobi
    private static final float SPRINT_BLEND_OUT = 0.20F; // ~5 ticks thu mượt về thế thủ 2 tầng khi dừng

    // Bộ góc lưỡi giải chính xác 1:1 theo ma trận Minecraft 1.21.1 cho tư thế chạy Naruto (2 lưỡi xuôi thẳng ra sau đối xứng, 0 xuyên tay!)
    private static final float NARUTO_RIGHT_BLADE_PITCH = -115.96F;
    private static final float NARUTO_RIGHT_BLADE_ROLL = -11.47F;
    private static final float NARUTO_RIGHT_BLADE_YAW = -155.77F;
    private static final float NARUTO_LEFT_BLADE_PITCH = 135.10F;
    private static final float NARUTO_LEFT_BLADE_ROLL = -23.59F;
    private static final float NARUTO_LEFT_BLADE_YAW = 5.23F;

    private static final Map<Integer, DualBladeAnimState> STATES = new HashMap<>();
    private static long clientTick = 0L;
    private static ClientLevel lastLevel = null;

    private DualBladeAnimManager() {}

    public static long getClientTick() {
        return clientTick;
    }

    public static boolean isDualWieldReady(Player player) {
        return player != null
                && player.isAlive()
                && player.getMainHandItem().is(ModItems.DUAL_KNIFE)
                && player.getOffhandItem().isEmpty()
                && !player.isFallFlying()
                && !player.isSwimming()
                && !player.isSleeping()
                && !player.isAutoSpinAttack();
    }

    /** Kiểm tra người chơi có đang đánh bằng Dual Blade hay không (để HumanoidModelMixin tắt hẳn vanilla swing!). */
    public static boolean isDualBladeAttacking(Player player) {
        if (!isDualWieldReady(player)) {
            return false;
        }
        DualBladeAnimState st = STATES.get(player.getId());
        return st != null && st.attacking;
    }

    public static void clearAll() {
        STATES.clear();
        DualBladeTrailRenderer.clear();
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
            boolean ready = isDualWieldReady(player);
            DualBladeAnimState st = STATES.get(player.getId());
            if (!ready && st == null) {
                continue;
            }
            if (st == null) {
                st = new DualBladeAnimState();
                st.wasOnGround = player.onGround();
                STATES.put(player.getId(), st);
            }
            st.lastSeenTick = clientTick;
            st.prevStanceBlend = st.stanceBlend;
            st.prevSprintBlend = st.sprintBlend;

            if (ready) {
                st.stanceBlend = Math.min(1.0F, st.stanceBlend + STANCE_BLEND_IN);
                boolean movingFast = player.walkAnimation.speed() > 0.22F
                        || player.getDeltaMovement().horizontalDistanceSqr() > 0.008;
                boolean sprintingNow = !player.isCrouching() && player.isSprinting() && movingFast;
                if (sprintingNow) {
                    st.sprintGraceUntilTick = clientTick + 6L;
                }
                boolean narutoActive = !player.isCrouching() && (sprintingNow
                        || (clientTick <= st.sprintGraceUntilTick && movingFast));
                if (narutoActive) {
                    st.sprintBlend = Math.min(1.0F, st.sprintBlend + SPRINT_BLEND_IN);
                } else {
                    st.sprintBlend = Math.max(0.0F, st.sprintBlend - SPRINT_BLEND_OUT);
                }
                // Tự động nối nhịp cắt ngang gọn khi tiếp đất ngay sau AIR_SLASH
                if (!st.wasOnGround && player.onGround() && clientTick <= st.pendingAirLandingUntilTick) {
                    st.pendingAirLandingUntilTick = -1000L;
                    startMove(st, player, DualBladeMoveType.SKILL_DASH, DualBladeMoveType.SKILL_DASH.baseDisplayTicks,
                            st.animSpeedMult, player.getYRot(), st.powerTier);
                }
            } else {
                st.stanceBlend = Math.max(0.0F, st.stanceBlend - STANCE_BLEND_OUT);
                st.sprintBlend = Math.max(0.0F, st.sprintBlend - SPRINT_BLEND_OUT);
                st.attacking = false;
                st.hasTransitionFrom = false;
                st.pendingAirLandingUntilTick = -1000L;
            }
            st.wasOnGround = player.onGround();

            if (st.attacking && (clientTick - st.startTick) >= st.durationTicks + 1.5F) {
                st.attacking = false;
                st.hasTransitionFrom = false;
            }
        }

        if ((clientTick & 31L) == 0L && !STATES.isEmpty()) {
            STATES.entrySet().removeIf(e -> {
                DualBladeAnimState s = e.getValue();
                return (clientTick - s.lastSeenTick) > 60L
                        || (s.stanceBlend <= 0.001F && s.prevStanceBlend <= 0.001F && !s.attacking);
            });
        }
    }

    public static void onComboPacket(ClientLevel level, DualBladeComboPayload payload) {
        DualBladeAnimState st = STATES.computeIfAbsent(payload.playerId(), id -> new DualBladeAnimState());
        st.lastSeenTick = clientTick;
        DualBladeMoveType move = payload.moveType();

        if (payload.hitConnected()) {
            DualBladeTrailRenderer.spawnImpactSparks(
                    new Vec3(payload.hitX(), payload.hitY(), payload.hitZ()), clientTick);
        }

        boolean recentSameOptimistic = st.attacking
                && st.moveType == move
                && (clientTick - st.startTick) <= 2L;

        Entity ent = level.getEntity(payload.playerId());
        if (!recentSameOptimistic) {
            startMove(st, ent instanceof Player p ? p : null, move,
                    payload.durationTicks(), payload.animSpeedMult(), payload.attackYaw(), payload.powerTier());
        } else {
            st.durationTicks = Mth.clamp(payload.durationTicks(), 4.5F, 15.5F);
            st.animSpeedMult = Mth.clamp(payload.animSpeedMult(), 0.5F, 2.5F);
            st.powerTier = payload.powerTier();
        }
    }

    public static void triggerLocalOptimistic(LocalPlayer player, DualBladeMoveType move,
                                              float durationTicks, float speedMult) {
        DualBladeAnimState st = STATES.computeIfAbsent(player.getId(), id -> new DualBladeAnimState());
        st.lastSeenTick = clientTick;
        startMove(st, player, move, durationTicks, speedMult, player.getYRot(), st.powerTier);
    }

    private static void startMove(DualBladeAnimState st, @Nullable Player player, DualBladeMoveType move,
                                  float durationTicks, float animSpeedMult, float attackYaw, int powerTier) {
        boolean chained = st.attacking && (clientTick - st.startTick) < st.durationTicks + 4.5F;
        if (chained) {
            st.blendFromPose.copyFrom(st.lastComputedPose);
            st.hasTransitionFrom = true;
        } else {
            st.hasTransitionFrom = false;
        }
        st.attacking = true;
        st.moveType = move;
        st.startTick = clientTick;
        st.durationTicks = Mth.clamp(durationTicks, 4.5F, 15.5F);
        st.animSpeedMult = Mth.clamp(animSpeedMult, 0.5F, 2.5F);
        st.attackYaw = attackYaw;
        st.powerTier = powerTier;

        if (move == DualBladeMoveType.AIR_SLASH) {
            st.pendingAirLandingUntilTick = clientTick + 16L;
        } else if (move != DualBladeMoveType.SKILL_DASH) {
            st.pendingAirLandingUntilTick = -1000L;
        }

        if (player != null && move != DualBladeMoveType.FLASH_DASH_CHARGE && move != DualBladeMoveType.FLASH_DASH_LAUNCH) {
            DualBladeTrailRenderer.spawnMoveTrail(
                    player.getId(),
                    player.position().add(0.0, player.getBbHeight() * 0.54, 0.0),
                    attackYaw,
                    player.getXRot(),
                    move,
                    st.durationTicks,
                    powerTier,
                    clientTick
            );
        }
    }

    public static float getAttackProgress(int playerId, float partialTick) {
        DualBladeAnimState st = STATES.get(playerId);
        if (st == null || !st.isAttackActive(clientTick, partialTick)) {
            return 0.0F;
        }
        return st.getAttackAlpha(clientTick, partialTick);
    }

    public static DualBladeMoveType getMoveType(int playerId) {
        DualBladeAnimState st = STATES.get(playerId);
        return st != null ? st.moveType : DualBladeMoveType.COMBO_1;
    }

    /** Hệ số mượt [0..1] của tư thế chạy Naruto Shinobi (dùng đồng bộ cả F5 lẫn góc nhìn Thứ 1). */
    public static float getSprintBlend(int playerId, float partialTick) {
        DualBladeAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        float s = st.getSprintBlend(partialTick) * st.getStanceBlend(partialTick);
        return s * s * (3.0F - 2.0F * s);
    }

    @Nullable
    public static FullBodyPose evaluatePose(Player player, float partialTick, float limbSwingAmount, float ageInTicks) {
        DualBladeAnimState st = STATES.get(player.getId());
        if (st == null) {
            return null;
        }
        float stance = st.getStanceBlend(partialTick);
        if (stance <= 0.001F) {
            return null;
        }
        float smoothStance = stance * stance * (3.0F - 2.0F * stance);

        FullBodyPose pose = st.reusablePose;
        pose.reset();

        // --- A. TƯ THẾ SÁT THỦ VẮT CHÉO 2 TẦNG RÕ RỆT (`Combat Stance` — 0 RUNG LẮC IDLE!) ---
        float breath = 0.0F;
        // Dùng thống nhất `walkAnimation.speed(partialTick)` để cả PlayerRenderer, HumanoidModel & ItemInHandLayer nhận cùng moveWeight!
        float walkSpeed = player.walkAnimation.speed(partialTick);
        float moveWeight = Mth.clamp(walkSpeed * 1.35F, 0.0F, 1.0F);
        float walkPhase = player.walkAnimation.position(partialTick);
        float strideSway = Mth.sin(walkPhase * 0.6662F) * moveWeight;
        float strideBob = 0.0F;

        // Đầu cúi gằm +18.3° (`+0.32 rad`) gườm mắt sát khí; thân hơi gù (`+0.20 rad`), xoay hông ~12.6° (`+0.22 rad`)!
        pose.rootPitchDeg = (-6.0F - moveWeight * 3.0F) * smoothStance;
        pose.rootTranslateY = (-0.060F * (1.0F - moveWeight * 0.55F) + strideBob) * smoothStance;
        pose.bodyX = (0.20F * (1.0F - moveWeight * 0.25F)) * smoothStance;
        pose.bodyY = (0.22F + strideSway * 0.10F) * smoothStance;
        pose.headAddX = (0.32F + moveWeight * 0.03F) * smoothStance;
        pose.headAddY = (-0.18F - strideSway * 0.05F) * smoothStance;

        // TẦNG TRÊN — Tay Trái + Dao Trái (`Reverse Grip`): giữ nguyên 100% pose F5 2 tầng!
        pose.leftArmX = (-1.54F + breath * 0.55F + strideBob * 1.4F) * smoothStance;
        pose.leftArmY = (0.92F + strideSway * 0.05F) * smoothStance;
        pose.leftArmZ = (-0.15F - breath * 0.25F) * smoothStance;

        // TẦNG DƯỚI — Tay Phải + Dao Phải (`Standard Grip`): giữ nguyên 100% pose F5 2 tầng!
        pose.rightArmX = (-0.78F - breath * 0.55F - strideBob * 1.6F) * smoothStance;
        pose.rightArmY = (-0.96F + strideSway * 0.05F) * smoothStance;
        pose.rightArmZ = (0.26F + breath * 0.25F) * smoothStance;

        // Bộ góc chính xác giải 1:1 theo ma trận Minecraft (`generate_dual_knife_model.py` -> `ItemInHandLayerMixin`):
        pose.leftBladePitchDeg = 28.36F * smoothStance;
        pose.leftBladeRollDeg = 7.29F * smoothStance;
        pose.leftBladeYawDeg = -30.71F * smoothStance;

        pose.rightBladePitchDeg = 137.92F * smoothStance;
        pose.rightBladeRollDeg = -44.06F * smoothStance;
        pose.rightBladeYawDeg = -173.09F * smoothStance;

        // 2 chân mở thế tấn sát thủ khi đứng yên; khi di chuyển/chạy trả hoàn toàn về bước chạy vanilla tự nhiên!
        float rawSprint = st.getSprintBlend(partialTick);
        float naruto = rawSprint * rawSprint * (3.0F - 2.0F * rawSprint);

        float idleLeg = Mth.clamp(1.0F - moveWeight * 1.20F, 0.0F, 1.0F) * (1.0F - naruto) * smoothStance;
        pose.rightLegX = 0.30F * idleLeg;
        pose.rightLegY = -0.22F * idleLeg;
        pose.rightLegZ = 0.14F * idleLeg;
        pose.leftLegX = -0.26F * idleLeg;
        pose.leftLegY = 0.22F * idleLeg;
        pose.leftLegZ = -0.14F * idleLeg;

        pose.armWeight = smoothStance;
        pose.legWeight = idleLeg * 0.92F;

        // --- A2. TƯ THẾ CHẠY NARUTO SHINOBI (hòa mượt từ Combat Stance 2 tầng khi chạy nước rút!) ---
        // Đổ thân khí động học -24°, ngẩng mặt nhìn thẳng phía trước, 2 tay & 2 thanh dao duỗi vút đối xứng ra sau lưng!
        if (naruto > 0.001F) {
            float n = naruto;
            float nBob = strideBob * 1.4F;
            float nSway = strideSway * 0.08F;

            pose.rootPitchDeg = Mth.lerp(n, pose.rootPitchDeg, -24.0F * smoothStance);
            pose.rootTranslateY = Mth.lerp(n, pose.rootTranslateY, (-0.08F + strideBob * 0.85F) * smoothStance);
            pose.bodyX = Mth.lerp(n, pose.bodyX, 0.12F * smoothStance);
            pose.bodyY = Mth.lerp(n, pose.bodyY, nSway * smoothStance);
            pose.headAddX = Mth.lerp(n, pose.headAddX, -0.26F * smoothStance);
            pose.headAddY = Mth.lerp(n, pose.headAddY, -nSway * 0.5F * smoothStance);

            pose.rightArmX = Mth.lerp(n, pose.rightArmX, (1.28F + nBob + nSway) * smoothStance);
            pose.rightArmY = Mth.lerp(n, pose.rightArmY, -0.14F * smoothStance);
            pose.rightArmZ = Mth.lerp(n, pose.rightArmZ, (0.34F + breath * 0.4F) * smoothStance);

            pose.leftArmX = Mth.lerp(n, pose.leftArmX, (1.28F + nBob - nSway) * smoothStance);
            pose.leftArmY = Mth.lerp(n, pose.leftArmY, 0.14F * smoothStance);
            pose.leftArmZ = Mth.lerp(n, pose.leftArmZ, (-0.34F - breath * 0.4F) * smoothStance);

            pose.rightBladePitchDeg = Mth.rotLerp(n, pose.rightBladePitchDeg, NARUTO_RIGHT_BLADE_PITCH * smoothStance);
            pose.rightBladeRollDeg = Mth.rotLerp(n, pose.rightBladeRollDeg, NARUTO_RIGHT_BLADE_ROLL * smoothStance);
            pose.rightBladeYawDeg = Mth.rotLerp(n, pose.rightBladeYawDeg, NARUTO_RIGHT_BLADE_YAW * smoothStance);

            pose.leftBladePitchDeg = Mth.rotLerp(n, pose.leftBladePitchDeg, NARUTO_LEFT_BLADE_PITCH * smoothStance);
            pose.leftBladeRollDeg = Mth.rotLerp(n, pose.leftBladeRollDeg, NARUTO_LEFT_BLADE_ROLL * smoothStance);
            pose.leftBladeYawDeg = Mth.rotLerp(n, pose.leftBladeYawDeg, NARUTO_LEFT_BLADE_YAW * smoothStance);
        }

        // --- B. CHUỖI 5 NHỊP ĐỒNG BỘ HOÀN HẢO VỚI TƯ THẾ ĐỨNG 2 TẦNG LẪN TƯ THẾ CHẠY NARUTO ---
        if (!st.isAttackActive(clientTick, partialTick)) {
            st.lastComputedPose.copyFrom(pose);
            return pose;
        }

        // Lưu khung hiện tại (Combat Stance 2 tầng khi đứng, hoặc Naruto Run Pose khi đang chạy!)
        // -> Mọi cú chém khi chạy sẽ vung bật tay TỪ SAU LƯNG ra chém rồi vuốt thu TRỰC TIẾP VỀ LẠI NARUTO RUN!
        st.idleStancePose.copyFrom(pose);

        float alpha = st.getAttackAlpha(clientTick, partialTick);
        float sweep = DualBladeAnimState.poseHoldSweep(alpha);
        float w = smoothStance;
        float runDamp = 1.0F - moveWeight * 0.45F;

        pose.isAttacking = true;
        pose.armWeight = 1.0F;
        // Tách riêng thân trên & thân dưới khi chạy: khi chạy Naruto 2 chân bơm nhịp chạy tự nhiên 100%!
        pose.legWeight = w * Mth.clamp(1.0F - moveWeight * 1.15F, 0.0F, 1.0F) * (1.0F - naruto);
        pose.rightBladePitchDeg = 0.0F;
        pose.rightBladeRollDeg = 0.0F;
        pose.rightBladeYawDeg = 0.0F;
        pose.leftBladePitchDeg = 0.0F;
        pose.leftBladeRollDeg = 0.0F;
        pose.leftBladeYawDeg = 0.0F;

        switch (st.moveType) {
            case COMBO_1 -> applyHit1LungeRightCleave(pose, sweep, w, runDamp, naruto);
            case COMBO_2 -> applyHit2ReverseLeftRising(pose, sweep, w, runDamp, naruto);
            case COMBO_3 -> applyHit3TwinOpposingDoubleArc(pose, sweep, w, runDamp, naruto);
            case COMBO_4 -> applyHit4LowCrouchCyclone360(pose, alpha, w, runDamp, naruto);
            case COMBO_5 -> applyHit5AirborneTwistXRendSlam(pose, alpha, w, runDamp, moveWeight, naruto);
            case SPRINT_THRUST -> applySprintTwinFlashThrust(pose, alpha, w, naruto);
            case AIR_SLASH -> applyAirCrossCut(pose, alpha, w);
            case SKILL_DASH -> applyLandingHorizontalFollowUp(pose, sweep, w, runDamp);
            case FLASH_DASH_CHARGE -> applyFlashDashCharge(pose, alpha, w);
            case FLASH_DASH_LAUNCH -> applyFlashDashLaunch(pose, alpha, w);
            case FLASH_DASH_END -> applyFlashDashEndBrake(pose, alpha, w);
        }

        // Cross-fade 1 lớp mượt: vào đòn trực tiếp từ Naruto Run / Combat Stance và thu mượt thẳng về lại Naruto Run / Combat Stance!
        if (st.moveType != DualBladeMoveType.COMBO_4 && alpha < 0.16F) {
            float enterT = DualBladeAnimState.easeOutCubic(alpha / 0.16F);
            pose.blendFrom(st.hasTransitionFrom ? st.blendFromPose : st.idleStancePose, enterT);
        } else if (alpha > 0.74F && st.moveType != DualBladeMoveType.FLASH_DASH_CHARGE
                && st.moveType != DualBladeMoveType.FLASH_DASH_LAUNCH) {
            float backT = DualBladeAnimState.easeInOutCubic((alpha - 0.74F) / 0.26F);
            pose.blendFrom(st.idleStancePose, 1.0F - backT);
            pose.isAttacking = true;
        }

        st.lastComputedPose.copyFrom(pose);
        return pose;
    }

    /**
     * Nhịp 1 (`11.5t`): Khi đang chạy Naruto, giữ độ đổ người khí động học `-24°` & tay trái duỗi vút sau lưng kiểu Naruto,
     * đồng thời tay phải vung bật từ sau lưng chém chéo cực sắc qua phía trước!
     */
    private static void applyHit1LungeRightCleave(FullBodyPose p, float sweep, float w, float runDamp, float naruto) {
        float rootYaw = Mth.lerp(sweep, -54.0F, 58.0F) * (0.68F + 0.32F * runDamp);
        p.rootYawDeg = rootYaw * w;
        p.rootPitchDeg = Mth.lerp(naruto, -18.0F * runDamp - 6.0F, -24.0F) * w;
        p.rootRollDeg = (-10.0F * runDamp) * w;
        p.rootTranslateY = Mth.lerp(naruto, -0.11F * runDamp, -0.08F) * w;

        p.bodyX = Mth.lerp(naruto, 0.18F * runDamp + 0.06F, 0.14F) * w;
        p.bodyY = Mth.lerp(sweep, -0.36F, 0.44F) * w;
        p.bodyZ = (-0.08F * runDamp) * w;
        p.headAddY = Mth.clamp(-rootYaw * Mth.DEG_TO_RAD * 0.58F, -0.78F, 0.78F) * w;
        p.headAddX = Mth.lerp(naruto, 0.20F * runDamp + 0.08F, -0.20F) * w;

        // Tay phải vung chéo sắc từ trên-phải (-2.38, hoặc bật từ sau lưng khi chạy) xuống sâu dưới-trái (-0.44)
        p.rightArmX = Mth.lerp(sweep, Mth.lerp(naruto, -2.38F, -1.85F), -0.44F) * w;
        p.rightArmY = Mth.lerp(sweep, -1.05F, 1.36F) * w;
        p.rightArmZ = (1.18F + 0.16F) * w;

        // Tay trái giữ dáng duỗi vút sau lưng chuẩn Naruto khi đang chạy!
        p.leftArmX = Mth.lerp(naruto, 0.82F, 1.26F) * w;
        p.leftArmY = Mth.lerp(naruto, -0.58F, 0.12F) * w;
        p.leftArmZ = Mth.lerp(naruto, -0.76F, -0.40F) * w;

        p.rightLegX = -0.62F * w;
        p.rightLegY = -0.24F * w;
        p.rightLegZ = 0.18F * w;
        p.leftLegX = 0.82F * w;
        p.leftLegY = 0.22F * w;
        p.leftLegZ = -0.16F * w;

        p.rightBladePitchDeg = -60.0F * w;
        p.rightBladeRollDeg = 66.0F * w;
        p.leftBladePitchDeg = Mth.rotLerp(naruto, 26.0F, NARUTO_LEFT_BLADE_PITCH) * w;
        p.leftBladeRollDeg = Mth.rotLerp(naruto, -22.0F, NARUTO_LEFT_BLADE_ROLL) * w;
        p.leftBladeYawDeg = Mth.rotLerp(naruto, 0.0F, NARUTO_LEFT_BLADE_YAW) * w;
        p.firstPersonCameraRollDeg = -2.8F * w;
    }

    /**
     * Nhịp 2 (`11.5t` — Reverse-Grip Backhand Left Slash): Khi đang chạy Naruto, tay phải vuốt thẳng về sau lưng kiểu Shinobi,
     * còn tay trái cầm ngược bật quét backhand rộng ra trước–trái cực mượt!
     */
    private static void applyHit2ReverseLeftRising(FullBodyPose p, float sweep, float w, float runDamp, float naruto) {
        float rootYaw = Mth.lerp(sweep, 52.0F, -60.0F) * (0.68F + 0.32F * runDamp);
        p.rootYawDeg = rootYaw * w;
        p.rootPitchDeg = Mth.lerp(naruto, -16.0F * runDamp - 6.0F, -24.0F) * w;
        p.rootRollDeg = (11.0F * runDamp) * w;
        p.rootTranslateY = Mth.lerp(naruto, -0.10F * runDamp, -0.08F) * w;

        p.bodyX = Mth.lerp(naruto, 0.16F * runDamp + 0.06F, 0.14F) * w;
        p.bodyY = Mth.lerp(sweep, 0.42F, -0.46F) * w;
        p.bodyZ = (0.09F * runDamp) * w;
        p.headAddY = Mth.clamp(-rootYaw * Mth.DEG_TO_RAD * 0.58F, -0.78F, 0.78F) * w;
        p.headAddX = Mth.lerp(naruto, 0.18F * runDamp + 0.06F, -0.20F) * w;

        p.leftArmX = Mth.lerp(sweep, -1.52F, -0.82F) * w;
        p.leftArmY = Mth.lerp(sweep, -1.08F, 1.28F) * w;
        p.leftArmZ = -1.16F * w;

        // Tay phải vuốt ngược về sau lưng giữ dáng Naruto Run liền mạch!
        p.rightArmX = Mth.lerp(naruto, 0.78F, 1.26F) * w;
        p.rightArmY = Mth.lerp(naruto, 0.56F, -0.12F) * w;
        p.rightArmZ = Mth.lerp(naruto, 0.74F, 0.40F) * w;

        p.leftLegX = -0.60F * w;
        p.leftLegY = 0.26F * w;
        p.leftLegZ = -0.18F * w;
        p.rightLegX = 0.80F * w;
        p.rightLegY = -0.24F * w;
        p.rightLegZ = 0.16F * w;

        p.leftBladePitchDeg = 18.0F * w;
        p.leftBladeRollDeg = -64.0F * w;
        p.leftBladeYawDeg = 20.0F * w;
        p.rightBladePitchDeg = Mth.rotLerp(naruto, 24.0F, NARUTO_RIGHT_BLADE_PITCH) * w;
        p.rightBladeRollDeg = Mth.rotLerp(naruto, 24.0F, NARUTO_RIGHT_BLADE_ROLL) * w;
        p.rightBladeYawDeg = Mth.rotLerp(naruto, 0.0F, NARUTO_RIGHT_BLADE_YAW) * w;
        p.firstPersonCameraRollDeg = 3.0F * w;
    }

    /**
     * Nhịp 3 (`12.5t` — Twin Opposing Double-Arc): Giữ nguyên góc đổ Naruto Run khi đang chạy để 0 giật người!
     */
    private static void applyHit3TwinOpposingDoubleArc(FullBodyPose p, float sweep, float w, float runDamp, float naruto) {
        float rootYaw = Mth.lerp(sweep, -30.0F, 34.0F) * (0.70F + 0.30F * runDamp);
        p.rootYawDeg = rootYaw * w;
        p.rootPitchDeg = Mth.lerp(naruto, -20.0F * runDamp - 6.0F, -24.0F) * w;
        p.rootRollDeg = (-12.0F * runDamp) * w;
        p.rootTranslateY = Mth.lerp(naruto, -0.12F * runDamp, -0.08F) * w;

        p.bodyX = Mth.lerp(naruto, 0.20F * runDamp + 0.06F, 0.14F) * w;
        p.bodyY = Mth.lerp(sweep, -0.26F, 0.30F) * w;
        p.bodyZ = (-0.08F * runDamp) * w;
        p.headAddX = Mth.lerp(naruto, 0.22F * runDamp + 0.08F, -0.20F) * w;
        p.headAddY = Mth.clamp(-rootYaw * Mth.DEG_TO_RAD * 0.50F, -0.58F, 0.58F) * w;

        p.leftArmX = Mth.lerp(sweep, -1.48F, -2.18F) * w;
        p.leftArmY = Mth.lerp(sweep, -0.88F, 1.24F) * w;
        p.leftArmZ = -1.32F * w;

        p.rightArmX = Mth.lerp(sweep, -1.32F, -0.40F) * w;
        p.rightArmY = Mth.lerp(sweep, 1.08F, -1.28F) * w;
        p.rightArmZ = 1.32F * w;

        p.rightLegX = -0.68F * w;
        p.rightLegZ = 0.20F * w;
        p.leftLegX = 0.96F * w;
        p.leftLegZ = -0.18F * w;

        p.leftBladePitchDeg = 20.0F * w;
        p.leftBladeRollDeg = -66.0F * w;
        p.leftBladeYawDeg = 16.0F * w;
        p.rightBladePitchDeg = -58.0F * w;
        p.rightBladeRollDeg = 72.0F * w;
        p.firstPersonCameraRollDeg = -3.1F * w;
    }

    /**
     * Nhịp 4 (`14.0t` — `360°` Shinobi Cyclone Spin-Sweep): Xoay tròn 360° liền mạch ngay trên đà chạy Naruto!
     */
    private static void applyHit4LowCrouchCyclone360(FullBodyPose p, float alpha, float w, float runDamp, float naruto) {
        float spin = DualBladeAnimState.cyclone360Spin(alpha);
        float env = DualBladeAnimState.poseHoldEnvelope(alpha);
        p.rootYawDeg = (360.0F * spin) * w;
        p.rootPitchDeg = Mth.lerp(naruto, (-18.0F * runDamp - 6.0F) * env, -22.0F) * w;
        p.rootRollDeg = (-10.0F * Mth.sin(spin * Mth.TWO_PI) * runDamp * env) * w;
        p.rootTranslateY = Mth.lerp(naruto, -0.14F * runDamp * env, -0.09F) * w;

        p.bodyX = Mth.lerp(naruto, (0.18F * runDamp + 0.06F) * env, 0.14F) * w;
        p.bodyY = (0.18F * env) * w;
        p.bodyZ = 0.0F;
        p.headAddX = Mth.lerp(naruto, (0.20F * runDamp + 0.06F) * env, -0.20F) * w;
        p.headAddY = 0.0F;

        p.rightArmX = -1.44F * w;
        p.leftArmX = -1.30F * w;
        p.rightArmZ = 1.48F * w;
        p.leftArmZ = -1.42F * w;
        p.rightArmY = -0.20F * w;
        p.leftArmY = 0.30F * w;

        p.rightLegX = -0.64F * w;
        p.leftLegX = 0.62F * w;
        p.rightLegZ = 0.28F * w;
        p.leftLegZ = -0.28F * w;

        p.rightBladePitchDeg = -70.0F * w;
        p.rightBladeRollDeg = 84.0F * w;
        p.leftBladePitchDeg = 14.0F * w;
        p.leftBladeRollDeg = -74.0F * w;
        p.leftBladeYawDeg = 18.0F * w;
        p.firstPersonCameraRollDeg = -3.2F * Mth.sin(spin * Mth.TWO_PI) * env * w;
    }

    /**
     * Nhịp 5 Finisher (`13.5t` — Airborne Asymmetric X-Rend Slam): đồng bộ liền mạch với đà chạy Naruto!
     */
    private static void applyHit5AirborneTwistXRendSlam(FullBodyPose p, float alpha, float w, float runDamp, float moveWeight, float naruto) {
        float leap = Mth.sin(Mth.clamp(alpha / 0.44F, 0.0F, 1.0F) * Mth.PI);
        float windup = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.24F, 0.0F, 1.0F));
        float slam = alpha < 0.22F
                ? 0.0F
                : (alpha < 0.46F
                    ? DualBladeAnimState.easeOutQuart((alpha - 0.22F) / 0.24F)
                    : 1.0F);

        p.rootTranslateY = (0.22F * leap - Mth.lerp(naruto, 0.12F * runDamp, 0.08F) * slam) * w;
        p.rootYawDeg = Mth.lerp(slam, -34.0F, 18.0F) * (0.72F + 0.28F * runDamp) * w;
        p.rootPitchDeg = (Mth.lerp(naruto, 10.0F, -10.0F) * leap * (1.0F - slam) - Mth.lerp(naruto, 20.0F * runDamp + 6.0F, 24.0F) * slam) * w;
        p.rootRollDeg = (-20.0F * leap * (1.0F - slam * 0.55F)) * w;

        p.bodyX = Mth.lerp(naruto, -0.10F * leap + (0.20F * runDamp + 0.06F) * slam, 0.14F) * w;
        p.bodyY = Mth.lerp(slam, -0.28F, 0.14F) * w;
        p.bodyZ = (-0.12F * leap) * w;
        p.headAddX = Mth.lerp(naruto, 0.20F * slam, -0.22F) * w;

        float rightArmX = Mth.lerp(slam, -2.65F * windup, -0.58F);
        float leftArmX = Mth.lerp(slam, -2.20F * windup, -0.92F);
        float armZ = Mth.lerp(slam, 0.90F * windup, -0.40F);
        float armY = Mth.lerp(slam, -0.30F * windup, 1.10F);
        p.rightArmX = rightArmX * w;
        p.leftArmX = leftArmX * w;
        p.rightArmZ = armZ * w;
        p.leftArmZ = -armZ * w;
        p.rightArmY = armY * w;
        p.leftArmY = -armY * w;

        p.legWeight = w * Math.max(leap * 0.80F, Mth.clamp(1.0F - moveWeight * 1.15F, 0.0F, 1.0F) * (1.0F - naruto));
        p.leftLegX = Mth.lerp(slam, -1.18F * leap, 0.68F) * w;
        p.rightLegX = Mth.lerp(slam, 0.45F * leap, -0.66F) * w;
        p.rightLegZ = 0.20F * w;
        p.leftLegZ = -0.20F * w;

        p.rightBladePitchDeg = (-54.0F * slam) * w;
        p.rightBladeRollDeg = (48.0F * slam) * w;
        p.leftBladePitchDeg = (22.0F * slam) * w;
        p.leftBladeRollDeg = (-54.0F * slam) * w;
        p.leftBladeYawDeg = (14.0F * slam) * w;
        p.firstPersonCameraRollDeg = 3.0F * Mth.sin(Mth.clamp((alpha - 0.18F) / 0.52F, 0.0F, 1.0F) * Mth.PI) * w;
    }

    /**
     * Sprint Opening Slash (`11.0t` — Naruto Whip-Out Shinobi Cross-Slash!):
     * Giữ trọn tư thế đổ người khí động học Naruto Run (`-24°`, mắt nhìn thẳng, chân bơm nhịp chạy tự nhiên 100%):
     * - 2 cánh tay vung bật trực tiếp TỪ PHÍA SAU LƯNG (`+1.28 rad`) lao lên chém chéo lệch pha cực sắc:
     *   Tay Phải (Đỏ–Đen) quất vắt chéo trước (`0%->36%`), Tay Trái (Đen–Bạc) quất ngược nối tiếp (`14%->52%`),
     *   rồi vuốt mượt 2 cánh tay trở lại sau lưng về đúng dáng Naruto Run (hoặc nối thẳng sang `COMBO_2..5`)!
     */
    private static void applySprintTwinFlashThrust(FullBodyPose p, float alpha, float w, float naruto) {
        float whipR = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.36F, 0.0F, 1.0F));
        float whipL = DualBladeAnimState.easeOutQuart(Mth.clamp((alpha - 0.14F) / 0.38F, 0.0F, 1.0F));

        // Đồng bộ tuyệt đối thân trên & góc đầu với tư thế chạy Naruto (-24°, bodyX=0.14, headAddX=-0.24)
        p.rootPitchDeg = -24.0F * w;
        p.rootYawDeg = (Mth.lerp(whipR, -26.0F, 24.0F) - Mth.lerp(whipL, 0.0F, 36.0F)) * w;
        p.rootRollDeg = (-7.0F + 14.0F * whipL) * w;
        p.rootTranslateY = -0.08F * w;

        p.bodyX = 0.14F * w;
        p.bodyY = (Mth.lerp(whipR, -0.22F, 0.24F) - 0.28F * whipL) * w;
        p.bodyZ = (-0.05F + 0.10F * whipL) * w;
        p.headAddX = -0.24F * w;
        p.headAddY = Mth.clamp(-p.rootYawDeg * Mth.DEG_TO_RAD * 0.58F, -0.62F, 0.62F);

        // Tay Phải bật mạnh từ sau lưng kiểu Naruto (+1.28 -> -1.52 -> -0.58) vắt chéo qua ngực!
        p.rightArmX = Mth.lerp(whipR, 0.85F * Math.max(0.5F, naruto), -0.64F) * w
                - 0.95F * Mth.sin(whipR * Mth.PI) * w;
        p.rightArmY = Mth.lerp(whipR, -0.55F, 1.18F) * w;
        p.rightArmZ = Mth.lerp(whipR, 0.65F, 0.58F) * w;

        // Tay Trái chờ sau lưng Naruto (+1.26) trong 0.14 pha đầu, rồi quất ngược backhand sắc ngang!
        p.leftArmX = Mth.lerp(whipL, 1.24F, -0.82F) * w - 0.55F * Mth.sin(whipL * Mth.PI) * w;
        p.leftArmY = Mth.lerp(whipL, 0.14F, 1.20F) * w;
        p.leftArmZ = Mth.lerp(whipL, -0.36F, -1.20F) * w;

        p.rightLegX = -0.52F * w;
        p.leftLegX = 0.68F * w;

        p.rightBladePitchDeg = Mth.rotLerp(whipR, NARUTO_RIGHT_BLADE_PITCH, -62.0F) * w;
        p.rightBladeRollDeg = Mth.rotLerp(whipR, NARUTO_RIGHT_BLADE_ROLL, 64.0F) * w;
        p.rightBladeYawDeg = Mth.rotLerp(whipR, NARUTO_RIGHT_BLADE_YAW, 0.0F) * w;

        p.leftBladePitchDeg = Mth.rotLerp(whipL, NARUTO_LEFT_BLADE_PITCH, 20.0F) * w;
        p.leftBladeRollDeg = Mth.rotLerp(whipL, NARUTO_LEFT_BLADE_ROLL, -60.0F) * w;
        p.leftBladeYawDeg = Mth.rotLerp(whipL, NARUTO_LEFT_BLADE_YAW, 18.0F) * w;
        p.firstPersonCameraRollDeg = Mth.lerp(whipL, -2.5F, 2.3F) * w;
    }

    /** Jump / Air Attack (`11.5t` — Aerial Cross-Cut): xoay trên không `180°` chém hất chéo 2 tay rõ nét! */
    private static void applyAirCrossCut(FullBodyPose p, float alpha, float w) {
        float spin = Mth.clamp(DualBladeAnimState.poseHoldSweep(alpha), 0.0F, 1.0F);
        p.rootYawDeg = (180.0F * spin) * w;
        p.rootPitchDeg = -22.0F * w;
        p.rootRollDeg = 18.0F * w;

        p.bodyX = 0.18F * w;
        p.rightArmX = Mth.lerp(spin, -0.64F, -2.30F) * w;
        p.leftArmX = Mth.lerp(spin, -0.72F, -2.10F) * w;
        p.rightArmZ = 1.24F * w;
        p.leftArmZ = -1.24F * w;
        p.rightArmY = -0.76F * w;
        p.leftArmY = 0.76F * w;

        p.legWeight = w * 0.90F;
        p.rightLegX = -0.82F * w;
        p.leftLegX = 0.58F * w;

        p.rightBladePitchDeg = -62.0F * w;
        p.rightBladeRollDeg = 60.0F * w;
        // Sửa góc lưỡi tay trái Reverse Grip (không để -66° xuyên tay!)
        p.leftBladePitchDeg = 22.0F * w;
        p.leftBladeRollDeg = -62.0F * w;
        p.leftBladeYawDeg = 16.0F * w;
        p.firstPersonCameraRollDeg = 3.0F * w;
    }

    /** Nhịp cắt ngang tự động khi tiếp đất ngay sau AIR_SLASH (`SKILL_DASH`, `9.0t`). */
    private static void applyLandingHorizontalFollowUp(FullBodyPose p, float sweep, float w, float runDamp) {
        p.rootYawDeg = Mth.lerp(sweep, -52.0F, 52.0F) * (0.70F + 0.30F * runDamp) * w;
        p.rootPitchDeg = (-18.0F * runDamp - 6.0F) * w;
        p.rootTranslateY = (-0.12F * runDamp) * w;

        p.bodyX = (0.18F * runDamp + 0.06F) * w;
        p.rightArmX = -1.42F * w;
        p.leftArmX = -1.36F * w;
        p.rightArmZ = 1.38F * w;
        p.leftArmZ = -1.38F * w;
        p.rightArmY = Mth.lerp(sweep, -0.96F, 1.18F) * w;
        p.leftArmY = Mth.lerp(sweep, 0.96F, -1.18F) * w;

        p.rightLegX = -0.58F * w;
        p.leftLegX = 0.66F * w;
        p.rightBladePitchDeg = -66.0F * w;
        p.rightBladeRollDeg = 78.0F * w;
        p.leftBladePitchDeg = 18.0F * w;
        p.leftBladeRollDeg = -74.0F * w;
        p.leftBladeYawDeg = 18.0F * w;
        p.firstPersonCameraRollDeg = -2.8F * w;
    }

    /**
     * Pha 1 Flash Dash (`6.5t`): Vào tư thế chạy thấp nén đà cực ngầu — khuỵu sâu trọng tâm (`-0.34`),
     * đổ chúi người `~48°`: tay phải (Standard Grip) duỗi thẳng ra sau lưng, tay trái (Reverse Grip)
     * hạ thấp sát mạn sườn trái sao cho CẢ 2 MŨI ĐAO CHĨA THẲNG NGƯỢC RA SAU LƯNG song song cực đẹp!
     */
    private static void applyFlashDashCharge(FullBodyPose p, float alpha, float w) {
        float coil = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.45F, 0.0F, 1.0F));
        p.rootTranslateY = (-0.34F * coil) * w;
        p.rootPitchDeg = (-48.0F * coil) * w;
        p.rootYawDeg = (-12.0F * coil) * w;
        p.rootRollDeg = 0.0F;

        p.bodyX = (0.58F * coil) * w;
        p.bodyY = (-0.16F * coil) * w;
        p.bodyZ = 0.0F;
        p.headAddX = (0.52F * coil) * w;
        p.headAddY = (0.14F * coil) * w;

        // Tay phải (cầm thẳng) duỗi thẳng ra sau lưng; tay trái (cầm ngược) ghìm thấp sát mạn sườn trái -> 2 lưỡi cùng vút thẳng về sau!
        p.rightArmX = (1.52F * coil) * w;
        p.leftArmX = (-0.52F * coil) * w;
        p.rightArmY = (-0.18F * coil) * w;
        p.leftArmY = (0.28F * coil) * w;
        p.rightArmZ = (0.42F * coil) * w;
        p.leftArmZ = (-0.58F * coil) * w;

        // Khuỵu gối sâu thế xuất phát lao phóng
        p.rightLegX = (-0.98F * coil) * w;
        p.leftLegX = (1.16F * coil) * w;
        p.rightLegZ = (0.18F * coil) * w;
        p.leftLegZ = (-0.18F * coil) * w;
        p.legWeight = 1.0F;

        // Cả 2 mũi đao Netherite–Kim Cương Xanh chĩa thẳng ngược về sau song song đầy sát khí!
        p.rightBladePitchDeg = 38.0F * coil * w;
        p.rightBladeRollDeg = 18.0F * coil * w;
        p.leftBladePitchDeg = 22.0F * coil * w;
        p.leftBladeRollDeg = -16.0F * coil * w;
        p.fovOffsetDeg = -6.5F * coil * w;
    }

    /**
     * Pha 2 Flash Dash (`7.5t` — 18 Block): Phóng lướt siêu tốc 18 block — đổ chúi khí động học `52°`,
     * 2 mũi đao thuận–ngược xé gió thẳng về sau lưng!
     */
    private static void applyFlashDashLaunch(FullBodyPose p, float alpha, float w) {
        p.rootTranslateY = (-0.30F) * w;
        p.rootPitchDeg = (-52.0F) * w;
        p.rootYawDeg = 0.0F;
        p.rootRollDeg = 0.0F;

        p.bodyX = (0.62F) * w;
        p.bodyY = 0.0F;
        p.bodyZ = 0.0F;
        p.headAddX = (0.56F) * w;

        p.rightArmX = (1.68F) * w;
        p.leftArmX = (-0.64F) * w;
        p.rightArmY = (-0.10F) * w;
        p.leftArmY = (0.24F) * w;
        p.rightArmZ = (0.52F) * w;
        p.leftArmZ = (-0.66F) * w;

        p.rightLegX = (-0.78F) * w;
        p.leftLegX = (1.28F) * w;
        p.rightLegZ = (0.14F) * w;
        p.leftLegZ = (-0.14F) * w;
        p.legWeight = 1.0F;

        p.rightBladePitchDeg = 46.0F * w;
        p.rightBladeRollDeg = 22.0F * w;
        p.leftBladePitchDeg = 26.0F * w;
        p.leftBladeRollDeg = -18.0F * w;
        p.fovOffsetDeg = (12.5F * (1.0F - alpha * 0.22F)) * w;
    }

    /**
     * Pha 3 Flash Dash (`7.0t`): Hãm chân hạ thấp xoay chéo 2 kiếm (1 thuận + 1 ngược) dứt khoát khi 10 nhát chém bùng nổ!
     */
    private static void applyFlashDashEndBrake(FullBodyPose p, float alpha, float w) {
        float env = DualBladeAnimState.poseHoldEnvelope(alpha);
        float snap = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha / 0.34F, 0.0F, 1.0F));

        p.rootTranslateY = (-0.22F * env) * w;
        p.rootPitchDeg = (-32.0F * env) * w;
        p.rootYawDeg = (42.0F * snap * env) * w;
        p.rootRollDeg = (-10.0F * env) * w;

        p.bodyX = (0.36F * env) * w;
        p.bodyY = (0.38F * snap * env) * w;
        p.headAddX = (0.28F * env) * w;
        p.headAddY = (-0.42F * snap * env) * w;

        // Tay phải vung chéo thuận khóa rộng, tay trái cầm ngược bung quét backhand dứt khoát!
        p.rightArmX = Mth.lerp(snap, 1.25F, -1.28F) * env * w;
        p.leftArmX = Mth.lerp(snap, -0.55F, -1.16F) * env * w;
        p.rightArmY = Mth.lerp(snap, -0.35F, 0.92F) * env * w;
        p.leftArmY = Mth.lerp(snap, 0.35F, 1.10F) * env * w;
        p.rightArmZ = Mth.lerp(snap, 0.45F, 1.28F) * env * w;
        p.leftArmZ = Mth.lerp(snap, -0.58F, -1.22F) * env * w;

        p.rightLegX = (-0.72F * env) * w;
        p.leftLegX = (0.78F * env) * w;
        p.rightLegZ = (0.26F * env) * w;
        p.leftLegZ = (-0.26F * env) * w;
        p.legWeight = 1.0F;

        p.rightBladePitchDeg = (-62.0F * env) * w;
        p.rightBladeRollDeg = (68.0F * env) * w;
        p.leftBladePitchDeg = (18.0F * env) * w;
        p.leftBladeRollDeg = (-64.0F * env) * w;
        p.firstPersonCameraRollDeg = -3.4F * env * w;
        p.fovOffsetDeg = (4.5F * (1.0F - snap) * env) * w;
    }
}
