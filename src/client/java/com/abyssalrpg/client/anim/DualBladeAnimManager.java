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

            if (ready) {
                st.stanceBlend = Math.min(1.0F, st.stanceBlend + STANCE_BLEND_IN);
                // Tự động nối nhịp cắt ngang gọn khi tiếp đất ngay sau AIR_SLASH
                if (!st.wasOnGround && player.onGround() && clientTick <= st.pendingAirLandingUntilTick) {
                    st.pendingAirLandingUntilTick = -1000L;
                    startMove(st, player, DualBladeMoveType.SKILL_DASH, DualBladeMoveType.SKILL_DASH.baseDisplayTicks,
                            st.animSpeedMult, player.getYRot(), st.powerTier);
                }
            } else {
                st.stanceBlend = Math.max(0.0F, st.stanceBlend - STANCE_BLEND_OUT);
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

        // --- A. TƯ THẾ SÁT THỦ VẮT CHÉO 2 TẦNG RÕ RỆT (`Combat Stance` UDK v2 — Tách 2 tầng ~8.1 px / 0.51 block, 0 hình chữ M!) ---
        float breath = Mth.sin(ageInTicks * 0.11F) * 0.028F;
        float walkSpeed = Math.max(limbSwingAmount, player.walkAnimation.speed(partialTick));
        float moveWeight = Mth.clamp(walkSpeed * 1.40F, 0.0F, 1.0F);
        float walkPhase = player.walkAnimation.position(partialTick);
        float strideSway = Mth.sin(walkPhase * 0.6662F) * moveWeight;
        float strideBob = Math.abs(Mth.cos(walkPhase * 0.6662F)) * 0.028F * moveWeight;

        // Đầu cúi gằm +18.3° (`+0.32 rad`) gườm mắt sát khí; thân hơi gù (`+0.20 rad`), xoay hông ~12.6° (`+0.22 rad`)!
        pose.rootPitchDeg = (-6.0F - moveWeight * 3.0F) * smoothStance;
        pose.rootTranslateY = (-0.060F * (1.0F - moveWeight * 0.45F) + strideBob) * smoothStance;
        pose.bodyX = (0.20F + moveWeight * 0.04F) * smoothStance;
        pose.bodyY = (0.22F + strideSway * 0.12F) * smoothStance;
        pose.headAddX = (0.32F + moveWeight * 0.03F) * smoothStance;
        pose.headAddY = (-0.18F - strideSway * 0.06F) * smoothStance;

        // TẦNG TRÊN — Tay Trái + Dao Trái (`Reverse Grip`): đặt CAO HẲN ngang cằm/ngực trên (`xRot=-1.54, yRot=+0.92, zRot=-0.15`),
        // lưỡi dao thon dốc chéo ~39.9° chạy từ trên-trái xuống dưới-phải (chính diện), 0 đâm xuyên mặt/ngực/giáp!
        pose.leftArmX = (-1.54F + breath * 0.55F + strideBob * 1.5F) * smoothStance;
        pose.leftArmY = (0.92F + strideSway * 0.05F) * smoothStance;
        pose.leftArmZ = (-0.15F - breath * 0.25F) * smoothStance;

        // TẦNG DƯỚI — Tay Phải + Dao Phải (`Reverse Grip Horizontal`): hạ THẤP HẲN ngang bụng/thắt lưng (`xRot=-0.78, yRot=-0.96, zRot=+0.26`),
        // lưỡi dao duỗi NGANG HẲN sang phía bên phải nhân vật (trái màn hình) với mũi VỂNH LÊN +11.1°, 0 chúc đất!
        pose.rightArmX = (-0.78F - breath * 0.55F - strideBob * 1.8F) * smoothStance;
        pose.rightArmY = (-0.96F + strideSway * 0.05F) * smoothStance;
        pose.rightArmZ = (0.26F + breath * 0.25F) * smoothStance;

        // Bộ góc chính xác giải 1:1 theo ma trận Minecraft (`generate_dual_knife_model.py` -> `ItemInHandLayerMixin`):
        pose.leftBladePitchDeg = 28.36F * smoothStance;
        pose.leftBladeRollDeg = 7.29F * smoothStance;
        pose.leftBladeYawDeg = -30.71F * smoothStance;

        pose.rightBladePitchDeg = 137.92F * smoothStance;
        pose.rightBladeRollDeg = -44.06F * smoothStance;
        pose.rightBladeYawDeg = -173.09F * smoothStance;

        // 2 chân mở thế tấn sát thủ hơi chùng: chân trái bước nhẹ về trước (-0.26), chân phải lùi nhẹ về sau (+0.30)
        float idleLeg = (1.0F - moveWeight) * smoothStance;
        pose.rightLegX = 0.30F * idleLeg;
        pose.rightLegY = -0.22F * idleLeg;
        pose.rightLegZ = 0.14F * idleLeg;
        pose.leftLegX = -0.26F * idleLeg;
        pose.leftLegY = 0.22F * idleLeg;
        pose.leftLegZ = -0.14F * idleLeg;

        pose.armWeight = smoothStance;
        pose.legWeight = idleLeg * 0.92F;

        // --- B. CHUỖI 5 NHỊP RÕ NÉT (11–14 TICK + POSE HOLD 40%–80%) + SPRINT + AIR ---
        if (!st.isAttackActive(clientTick, partialTick)) {
            st.lastComputedPose.copyFrom(pose);
            return pose;
        }

        // Lưu lại khung Combat Stance 2 tầng để blend mượt vào đòn và trả mượt về thế thủ trong 4–6 tick cuối!
        st.idleStancePose.copyFrom(pose);

        float alpha = st.getAttackAlpha(clientTick, partialTick);
        float env = DualBladeAnimState.poseHoldEnvelope(alpha);
        float sweep = DualBladeAnimState.poseHoldSweep(alpha);
        float w = smoothStance;

        pose.isAttacking = true;
        pose.armWeight = 1.0F;
        pose.legWeight = w * (1.0F - moveWeight * 0.15F);

        switch (st.moveType) {
            case COMBO_1 -> applyHit1LungeRightCleave(pose, sweep, env, w);
            case COMBO_2 -> applyHit2ReverseLeftRising(pose, sweep, env, w);
            case COMBO_3 -> applyHit3TwinOpposingDoubleArc(pose, sweep, env, w);
            case COMBO_4 -> applyHit4LowCrouchCyclone360(pose, alpha, env, w);
            case COMBO_5 -> applyHit5AirborneTwistXRendSlam(pose, alpha, env, w);
            case SPRINT_THRUST -> applySprintTwinFlashThrust(pose, alpha, env, w);
            case AIR_SLASH -> applyAirCrossCut(pose, alpha, env, w);
            case SKILL_DASH -> applyLandingHorizontalFollowUp(pose, sweep, env, w);
            case FLASH_DASH_CHARGE -> applyFlashDashCharge(pose, alpha, w);
            case FLASH_DASH_LAUNCH -> applyFlashDashLaunch(pose, alpha, w);
            case FLASH_DASH_END -> applyFlashDashEndBrake(pose, alpha, w);
        }

        // Cross-fade mượt lúc bắt đầu đòn (từ đòn trước khi nối combo hoặc từ Combat Stance 2 tầng!)
        if (st.moveType != DualBladeMoveType.COMBO_4 && alpha < 0.16F) {
            float enterT = DualBladeAnimState.easeOutCubic(alpha / 0.16F);
            pose.blendFrom(st.hasTransitionFrom ? st.blendFromPose : st.idleStancePose, enterT);
        } else if (alpha > 0.78F && st.moveType != DualBladeMoveType.FLASH_DASH_CHARGE
                && st.moveType != DualBladeMoveType.FLASH_DASH_LAUNCH) {
            // Trả mượt từ pose kết đòn về lại Combat Stance 2 tầng trong ~3–5 tick cuối (0 giật xoay 360°!)
            float backT = DualBladeAnimState.easeInOutCubic((alpha - 0.78F) / 0.22F);
            pose.blendFrom(st.idleStancePose, 1.0F - backT);
            pose.isAttacking = true;
        }

        st.lastComputedPose.copyFrom(pose);
        return pose;
    }

    /**
     * Nhịp 1 (`11.5t`): Đổ chúi người ~38°, bước tấn dài, xoay vai ±75° từ phải -> trái,
     * Tay Phải vung chéo rộng hết cỡ từ trên-phải vắt sâu sang dưới-trái, giữ dáng kết đòn cực rõ!
     */
    private static void applyHit1LungeRightCleave(FullBodyPose p, float sweep, float env, float w) {
        float rootYaw = Mth.lerp(sweep, -72.0F, 76.0F) * env;
        p.rootYawDeg = rootYaw * w;
        p.rootPitchDeg = (-38.0F * env) * w;
        p.rootRollDeg = (-15.0F * env) * w;
        p.rootTranslateY = (-0.15F * env) * w;

        p.bodyX = 0.42F * env * w;
        p.bodyY = Mth.lerp(sweep, -0.45F, 0.56F) * env * w;
        p.bodyZ = -0.14F * env * w;
        p.headAddY = Mth.clamp(-rootYaw * Mth.DEG_TO_RAD * 0.50F, -0.75F, 0.75F) * w;
        p.headAddX = 0.34F * env * w;

        // Tay phải vắt chéo rộng tối đa từ trên-phải (-2.55 rad) xuống sâu dưới-trái (-0.42 rad, yaw +1.48 rad!)
        p.rightArmX = Mth.lerp(sweep, -2.55F, -0.42F) * env * w;
        p.rightArmY = Mth.lerp(sweep, -1.18F, 1.48F) * env * w;
        p.rightArmZ = (1.32F * env + 0.22F) * w;

        // Tay trái bung rộng ngược ra sau lưng làm đối trọng rõ nét
        p.leftArmX = (1.05F * env) * w;
        p.leftArmY = (-0.72F * env) * w;
        p.leftArmZ = (-0.86F * env - 0.16F) * w;

        // Tấn trước sâu, chân sau duỗi dài cực rõ
        p.rightLegX = (-0.68F * env) * w;
        p.rightLegY = (-0.32F * env) * w;
        p.rightLegZ = (0.22F * env) * w;
        p.leftLegX = (0.98F * env) * w;
        p.leftLegY = (0.28F * env) * w;
        p.leftLegZ = (-0.18F * env) * w;

        p.rightBladePitchDeg = -64.0F * env * w;
        p.rightBladeRollDeg = 72.0F * env * w;
        p.leftBladePitchDeg = 32.0F * env * w;
        p.leftBladeRollDeg = -28.0F * env * w;
        p.firstPersonCameraRollDeg = -3.6F * env * w;
    }

    /**
     * Nhịp 2 (`11.5t` — Reverse-Grip Backhand Left Slash): Xoay bật hông ngược,
     * Tay Trái Cầm Ngược vung chém trái tay (`backhand`) sắc lạnh từ ngực-phải quét bung ngang ra ngoài-trái!
     */
    private static void applyHit2ReverseLeftRising(FullBodyPose p, float sweep, float env, float w) {
        float rootYaw = Mth.lerp(sweep, 68.0F, -78.0F) * env;
        p.rootYawDeg = rootYaw * w;
        p.rootPitchDeg = (-34.0F * env) * w;
        p.rootRollDeg = (16.0F * env) * w;
        p.rootTranslateY = (-0.13F * env) * w;

        p.bodyX = 0.36F * env * w;
        p.bodyY = Mth.lerp(sweep, 0.52F, -0.58F) * env * w;
        p.bodyZ = 0.16F * env * w;
        p.headAddY = Mth.clamp(-rootYaw * Mth.DEG_TO_RAD * 0.50F, -0.75F, 0.75F) * w;
        p.headAddX = 0.28F * env * w;

        // Tay trái Cầm Ngược vắt từ vai-phải (-1.58 rad, yaw -1.18) quét backhand bung cực rộng sang trái-trên (-0.82 rad, yaw +1.36)!
        p.leftArmX = Mth.lerp(sweep, -1.58F, -0.82F) * env * w;
        p.leftArmY = Mth.lerp(sweep, -1.18F, 1.36F) * env * w;
        p.leftArmZ = (-1.24F * env - 0.18F) * w;

        // Tay phải thu mở mạnh về phía sau lưng làm đối trọng
        p.rightArmX = (0.96F * env) * w;
        p.rightArmY = (0.68F * env) * w;
        p.rightArmZ = (0.82F * env + 0.16F) * w;

        p.leftLegX = (-0.66F * env) * w;
        p.leftLegY = (0.34F * env) * w;
        p.leftLegZ = (-0.22F * env) * w;
        p.rightLegX = (0.96F * env) * w;
        p.rightLegY = (-0.30F * env) * w;
        p.rightLegZ = (0.18F * env) * w;

        // Căn góc mặt lưỡi cầm ngược nằm đúng mặt phẳng quét backhand (0 xuyên cẳng tay!)
        p.leftBladePitchDeg = 18.0F * env * w;
        p.leftBladeRollDeg = -68.0F * env * w;
        p.leftBladeYawDeg = 22.0F * env * w;
        p.rightBladePitchDeg = 28.0F * env * w;
        p.rightBladeRollDeg = 28.0F * env * w;
        p.firstPersonCameraRollDeg = 3.7F * env * w;
    }

    /**
     * Nhịp 3 (`12.5t` — *Ancient Sculk Twinblade*): Đổ chúi cực mạnh `~42°`, chân sau đá cao duỗi thẳng (`1.22 rad`),
     * CẢ 2 TAY quét đồng thời 2 vòng cung ngược chiều rộng tối đa rồi giữ dáng 2 cánh mở cực uy lực!
     */
    private static void applyHit3TwinOpposingDoubleArc(FullBodyPose p, float sweep, float env, float w) {
        p.rootYawDeg = Mth.lerp(sweep, -36.0F, 42.0F) * env * w;
        p.rootPitchDeg = (-42.0F * env) * w;
        p.rootRollDeg = (-18.0F * env) * w;
        p.rootTranslateY = (-0.18F * env) * w;

        p.bodyX = 0.46F * env * w;
        p.bodyY = Mth.lerp(sweep, -0.30F, 0.36F) * env * w;
        p.bodyZ = -0.14F * env * w;
        p.headAddX = 0.40F * env * w;
        p.headAddY = Mth.clamp(-p.rootYawDeg * Mth.DEG_TO_RAD * 0.45F, -0.58F, 0.58F);

        // Tay trái quét cao vòng lên trái; tay phải cắt thấp chéo vắt sang phải -> 2 vệt ngược chiều đan xen!
        p.leftArmX = Mth.lerp(sweep, -1.55F, -2.56F) * env * w;
        p.leftArmY = Mth.lerp(sweep, -0.95F, 1.38F) * env * w;
        p.leftArmZ = (-1.45F * env - 0.16F) * w;

        p.rightArmX = Mth.lerp(sweep, -1.35F, -0.35F) * env * w;
        p.rightArmY = Mth.lerp(sweep, 1.18F, -1.42F) * env * w;
        p.rightArmZ = (1.45F * env + 0.16F) * w;

        // Chân trước trụ thấp, chân sau đá duỗi thẳng cao ra sau cực dễ thấy ở F5!
        p.rightLegX = (-0.74F * env) * w;
        p.rightLegZ = (0.24F * env) * w;
        p.leftLegX = (1.22F * env) * w;
        p.leftLegZ = (-0.20F * env) * w;

        p.leftBladePitchDeg = -68.0F * env * w;
        p.leftBladeRollDeg = -78.0F * env * w;
        p.rightBladePitchDeg = -62.0F * env * w;
        p.rightBladeRollDeg = 76.0F * env * w;
        p.firstPersonCameraRollDeg = -3.9F * env * w;
    }

    /**
     * Nhịp 4 (`14.0t` — Low-Crouch `360°` Cyclone Spin-Sweep):
     * Khuỵu trọng tâm sâu (`-0.22`), đổ thân `35°`, dang rộng cả 2 cánh tay ngang ngực song song mặt đất
     * và XOAY TRÒN TOÀN THÂN `360°` siêu rõ ràng (`0%->62%` xoay, `62%->84%` hãm giữ thế, `84%->100%` thu)!
     */
    private static void applyHit4LowCrouchCyclone360(FullBodyPose p, float alpha, float env, float w) {
        float spin = DualBladeAnimState.cyclone360Spin(alpha);
        p.rootYawDeg = (360.0F * spin) * w;
        p.rootPitchDeg = (-35.0F * env) * w;
        p.rootRollDeg = (-12.0F * Mth.sin(spin * Mth.TWO_PI) * env) * w;
        p.rootTranslateY = (-0.22F * env) * w;

        p.bodyX = 0.40F * env * w;
        p.bodyY = (0.22F * env) * w;
        p.bodyZ = 0.0F;
        p.headAddX = 0.34F * env * w;
        p.headAddY = 0.0F;

        // Dang rộng 2 cánh tay ngang vai tạo vòng tròn xoay 360° (1 lưỡi thuận chĩa trước + 1 lưỡi ngược quét tiếp tuyến sau khuỷu!)
        p.rightArmX = (-1.48F * env) * w;
        p.leftArmX = (-1.32F * env) * w;
        p.rightArmZ = (1.56F * env) * w;
        p.leftArmZ = (-1.48F * env) * w;
        p.rightArmY = (-0.22F * env) * w;
        p.leftArmY = (0.34F * env) * w;

        // Khuỵu gối tấn sâu đối xứng
        p.rightLegX = (-0.72F * env) * w;
        p.leftLegX = (0.68F * env) * w;
        p.rightLegZ = (0.34F * env) * w;
        p.leftLegZ = (-0.34F * env) * w;

        p.rightBladePitchDeg = -74.0F * env * w;
        p.rightBladeRollDeg = 88.0F * env * w;
        p.leftBladePitchDeg = -14.0F * env * w;
        p.leftBladeRollDeg = -78.0F * env * w;
        p.firstPersonCameraRollDeg = -3.8F * Mth.sin(spin * Mth.TWO_PI) * env * w;
    }

    /**
     * Nhịp 5 Finisher (`13.5t` — Airborne Asymmetric X-Rend Slam: 1 Thuận Bổ + 1 Ngược Xé Chéo!):
     * - `0% -> 24%`: Bật nhảy nhẹ lên không (`+0.30`), xoắn nghiêng thân `32°`, co gối trái cực cao (`-1.38 rad`), giơ 2 đao lên đỉnh.
     * - `24% -> 46%`: Tay phải thuận bổ chéo cực mạnh từ trên xuống, tay trái cầm ngược xé chéo khóa chữ X bất đối xứng!
     * - `46% -> 82%`: Giữ dáng kết đòn khuỵu thấp 2 tay bắt chéo chữ X cực ngầu!
     */
    private static void applyHit5AirborneTwistXRendSlam(FullBodyPose p, float alpha, float env, float w) {
        float leap = Mth.sin(Mth.clamp(alpha / 0.44F, 0.0F, 1.0F) * Mth.PI);
        float windup = DualBladeAnimState.easeOutCubic(Mth.clamp(alpha / 0.24F, 0.0F, 1.0F));
        float slam = alpha < 0.22F
                ? 0.0F
                : (alpha < 0.46F
                    ? DualBladeAnimState.easeOutQuart((alpha - 0.22F) / 0.24F)
                    : 1.0F);

        p.rootTranslateY = (0.30F * leap - 0.18F * slam * env) * w;
        p.rootYawDeg = Mth.lerp(slam, -42.0F, 22.0F) * env * w;
        p.rootPitchDeg = (14.0F * leap * (1.0F - slam) - 40.0F * slam * env) * w;
        p.rootRollDeg = (-32.0F * leap * (1.0F - slam * 0.55F)) * w;

        p.bodyX = (-0.18F * leap + 0.44F * slam * env) * w;
        p.bodyY = Mth.lerp(slam, -0.36F, 0.16F) * env * w;
        p.bodyZ = (-0.22F * leap) * w;
        p.headAddX = (0.34F * slam * env) * w;

        // Tay phải thuận giơ cao trên đầu (-2.80) bổ sâu; tay trái cầm ngược vung cao chéo (-2.32) xé ngược chữ X!
        float rightArmX = Mth.lerp(slam, -2.80F * windup, -0.58F) * env;
        float leftArmX = Mth.lerp(slam, -2.32F * windup, -0.94F) * env;
        float armZ = Mth.lerp(slam, 0.96F * windup, -0.44F) * env;
        float armY = Mth.lerp(slam, -0.34F * windup, 1.18F) * env;
        p.rightArmX = rightArmX * w;
        p.leftArmX = leftArmX * w;
        p.rightArmZ = armZ * w;
        p.leftArmZ = -armZ * w;
        p.rightArmY = armY * w;
        p.leftArmY = -armY * w;

        // Co gối trái cực cao lúc nhảy, đạp chân tấn rộng lúc bổ xuống!
        p.leftLegX = Mth.lerp(slam, -1.38F * leap, 0.78F * env) * w;
        p.rightLegX = Mth.lerp(slam, 0.52F * leap, -0.76F * env) * w;
        p.rightLegZ = (0.24F * env) * w;
        p.leftLegZ = (-0.24F * env) * w;

        p.rightBladePitchDeg = (-56.0F * slam * env) * w;
        p.rightBladeRollDeg = (50.0F * slam * env) * w;
        p.leftBladePitchDeg = (20.0F * slam * env) * w;
        p.leftBladeRollDeg = (-56.0F * slam * env) * w;
        p.firstPersonCameraRollDeg = 3.6F * Mth.sin(Mth.clamp((alpha - 0.18F) / 0.52F, 0.0F, 1.0F) * Mth.PI) * w;
    }

    /** Sprint Attack (`11.0t` — Twin Flash-Thrust): lao chồm thấp `42°` đâm xiên song đao 2 nhịp cực mạnh + giữ thế đâm! */
    private static void applySprintTwinFlashThrust(FullBodyPose p, float alpha, float env, float w) {
        float pulse = 0.72F + 0.28F * Mth.sin(Math.min(1.0F, alpha / 0.65F) * Mth.TWO_PI);
        p.rootPitchDeg = (-42.0F * env) * w;
        p.rootYawDeg = (-18.0F * Mth.sin(Math.min(1.0F, alpha / 0.65F) * Mth.TWO_PI) * env) * w;
        p.rootTranslateY = (-0.18F * env) * w;

        p.bodyX = 0.46F * env * w;
        p.headAddX = 0.40F * env * w;

        p.rightArmX = (-1.68F * env * pulse) * w;
        p.leftArmX = (-1.64F * env * (1.28F - pulse * 0.28F)) * w;
        p.rightArmY = (-0.12F * env) * w;
        p.leftArmY = (0.12F * env) * w;
        p.rightArmZ = (0.18F * env) * w;
        p.leftArmZ = (-0.18F * env) * w;

        p.rightLegX = (-0.64F * env) * w;
        p.leftLegX = (1.10F * env) * w;

        p.rightBladePitchDeg = -86.0F * env * w;
        p.leftBladePitchDeg = -86.0F * env * w;
        p.rightBladeRollDeg = 14.0F * env * w;
        p.leftBladeRollDeg = -14.0F * env * w;
        p.firstPersonCameraRollDeg = -3.0F * Mth.sin(alpha * Mth.TWO_PI) * env * w;
    }

    /** Jump / Air Attack (`11.5t` — Aerial Cross-Cut): xoay trên không `180°` chém hất chéo 2 tay rõ nét! */
    private static void applyAirCrossCut(FullBodyPose p, float alpha, float env, float w) {
        float spin = DualBladeAnimState.poseHoldSweep(alpha);
        p.rootYawDeg = (180.0F * Mth.clamp(spin, 0.0F, 1.0F) * env) * w;
        p.rootPitchDeg = (-32.0F * env) * w;
        p.rootRollDeg = (26.0F * env) * w;

        p.bodyX = 0.28F * env * w;
        p.rightArmX = Mth.lerp(Mth.clamp(spin, 0.0F, 1.0F), -0.60F, -2.48F) * env * w;
        p.leftArmX = Mth.lerp(Mth.clamp(spin, 0.0F, 1.0F), -0.60F, -2.48F) * env * w;
        p.rightArmZ = (1.30F * env) * w;
        p.leftArmZ = (-1.30F * env) * w;
        p.rightArmY = (-0.82F * env) * w;
        p.leftArmY = (0.82F * env) * w;

        p.rightLegX = (-0.95F * env) * w;
        p.leftLegX = (0.68F * env) * w;

        p.rightBladePitchDeg = -66.0F * env * w;
        p.rightBladeRollDeg = 64.0F * env * w;
        p.leftBladePitchDeg = -66.0F * env * w;
        p.leftBladeRollDeg = -64.0F * env * w;
        p.firstPersonCameraRollDeg = 3.5F * env * w;
    }

    /** Nhịp cắt ngang tự động khi tiếp đất ngay sau AIR_SLASH (`SKILL_DASH`, `9.0t`). */
    private static void applyLandingHorizontalFollowUp(FullBodyPose p, float sweep, float env, float w) {
        p.rootYawDeg = Mth.lerp(sweep, -64.0F, 64.0F) * env * w;
        p.rootPitchDeg = (-36.0F * env) * w;
        p.rootTranslateY = (-0.18F * env) * w;

        p.bodyX = 0.38F * env * w;
        p.rightArmX = (-1.48F * env) * w;
        p.leftArmX = (-1.48F * env) * w;
        p.rightArmZ = (1.46F * env) * w;
        p.leftArmZ = (-1.46F * env) * w;
        p.rightArmY = Mth.lerp(sweep, -1.05F, 1.25F) * env * w;
        p.leftArmY = Mth.lerp(sweep, 1.05F, -1.25F) * env * w;

        p.rightLegX = (-0.64F * env) * w;
        p.leftLegX = (0.74F * env) * w;
        p.rightBladePitchDeg = -70.0F * env * w;
        p.rightBladeRollDeg = 82.0F * env * w;
        p.leftBladePitchDeg = -70.0F * env * w;
        p.leftBladeRollDeg = -82.0F * env * w;
        p.firstPersonCameraRollDeg = -3.2F * env * w;
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
