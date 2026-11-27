package com.abyssalrpg.client;

import com.abyssalrpg.client.anim.KatanaAnimManager;
import com.abyssalrpg.client.renderer.vfx.ExcaliburVfxRenderer;
import com.abyssalrpg.client.renderer.vfx.KatanaVfxRenderer;
import com.abyssalrpg.combat.KatanaCombatHandler;
import com.abyssalrpg.combat.KatanaMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.item.KatanaItem;
import com.abyssalrpg.network.KatanaActionC2SPayload;
import com.abyssalrpg.network.KatanaSyncPayload;
import com.abyssalrpg.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Cầu nối Client & bộ nhận diện Input Trigger cho **Damascus Katana (`abyssalrpg:katana`)**:
 * - Phím `[G]`: Rút kiếm (`Nukitsuke`) / Vẩy máu & Tra kiếm (`Chiburi & Noto`).
 * - **Nhánh 1 (Hold Attack sau Hit 1/2):** Giữ chuột trái `>= 5` ticks -> gửi `ACTION_HEAVY_THRUST` (Đâm Xuyên Giáp Katate-tsuki).
 * - **Nhánh 2 (Delay Combo):** Sau Hit 1 đợi `>= 0.30s` (Tick 10–28) rồi bấm đánh -> Quét tầm thấp + Xoay 360° AoE.
 * - **Nhánh 3 (Iaido Blink — Nhất Kiếm Tất Sát):** Bấm phím `[R]` -> Lóe sao 4 cánh & lướt xuyên Judgment Cut + kích nổ sát thương trễ!
 * - **Nhánh 4 (Aerial Combo):** Bấm đánh khi ở trên không -> 3 đòn Không chiến (Hất tung -> Treo trên không -> Bổ thiên thạch!).
 * - **Nhánh 5 (Parry Counter):** Bấm đánh ngay trong cửa sổ `0.75s` sau khi đỡ chuẩn nhịp (`DEFLECT`) -> Phản Công Chí Mạng!
 */
public final class KatanaClientState {
    private static KeyMapping sheathKey;
    private static final Map<Integer, Boolean> SYNCED_DRAWN = new HashMap<>();
    private static final Map<Integer, Long> IAIDO_CHARGING = new HashMap<>();
    private static final Map<Integer, Long> IAIDO_COOLDOWN_UNTIL = new HashMap<>();

    private static long lastAirSwingTick = -100L;
    private static KatanaMoveType lastLocalMove = KatanaMoveType.HIT_1_DRAW;
    private static int localPredictedStep = 0;

    private static int localAirStep = 0;
    private static long lastLocalAirTick = -100L;
    private static long parryCounterUntilTick = -1000L;

    private static boolean wasAttackDown = false;
    private static int attackHoldTicks = 0;
    private static boolean thrustTriggeredThisHold = false;

    private KatanaClientState() {}

    public static void initKeybinds() {
        sheathKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.abyssalrpg.katana_sheath",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.abyssalrpg"
        ));
    }

    public static String getSheathKeyDisplayName() {
        return sheathKey != null ? sheathKey.getTranslatedKeyMessage().getString() : "G";
    }

    public static boolean isDrawn(int playerId, ItemStack stack) {
        Boolean synced = SYNCED_DRAWN.get(playerId);
        return synced != null ? synced : KatanaItem.isDrawn(stack);
    }

    public static boolean isIaidoCharging(int playerId) {
        return IAIDO_CHARGING.containsKey(playerId);
    }

    public static boolean isIaidoOnCooldown(int playerId) {
        Long until = IAIDO_COOLDOWN_UNTIL.get(playerId);
        long now = KatanaAnimManager.getClientTick();
        if (until != null && now < until) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getId() == playerId) {
                float remSec = (until - now) / 20.0F;
                mc.player.displayClientMessage(Component.literal(
                        "§c✦ Nhất Kiếm Tất Sát đang hồi chiêu: §e" + String.format(Locale.ROOT, "%.1fs", remSec)), true);
            }
            return true;
        }
        return false;
    }

    /** Phím `[R]` kích hoạt trực tiếp tuyệt kỹ **Nhất Kiếm Tất Sát (`IAIDO_BLINK`)** — chỉ dùng được khi kiếm chưa rút ra và không đang đỡ bằng vỏ! */
    public static void onIaidoBlinkKeyPressed(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null
                || player.isSpectator() || player.isHandsBusy()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.KATANA)) {
            return;
        }
        if (KatanaAnimManager.isAirSlamDiving(player.getId())) {
            return;
        }
        if (isDrawn(player.getId(), stack)) {
            player.displayClientMessage(Component.literal(
                    "§c✦ Chỉ có thể dùng Nhất Kiếm Tất Sát khi kiếm chưa rút ra! §7(Bấm [" + getSheathKeyDisplayName() + "] để tra kiếm)"), true);
            return;
        }
        if (player.isUsingItem() || KatanaAnimManager.isMoveActive(player.getId(), KatanaMoveType.GUARD_SAYA_UKE_HIT)) {
            player.displayClientMessage(Component.literal(
                    "§c✦ Không thể dùng kỹ năng để phản đòn khi đang đỡ bằng vỏ kiếm!"), true);
            return;
        }
        if (isIaidoOnCooldown(player.getId())) {
            return;
        }
        ClientPlayNetworking.send(new KatanaActionC2SPayload(KatanaActionC2SPayload.ACTION_IAIDO_BLINK));
    }

    public static void onClientAirSwing(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null
                || player.isSpectator() || player.isHandsBusy()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.KATANA)) {
            return;
        }
        if (KatanaAnimManager.isAirSlamDiving(player.getId())) {
            return;
        }
        long tick = KatanaAnimManager.getClientTick();

        // 1. Ưu tiên cao nhất: Phản Công Chớp Nhoáng ngay sau Parry (`PARRY_COUNTER`), hoạt động kể cả khi vừa nhả RMB!
        if (tick <= parryCounterUntilTick) {
            triggerLocalParryCounter(player, tick);
            return;
        }
        if (player.isUsingItem()) {
            return;
        }

        // Khung hủy đòn (Cancel Window)
        if (tick - lastAirSwingTick < lastLocalMove.cancelAfterTicks) {
            return;
        }

        boolean wasSheathed = !isDrawn(player.getId(), stack);
        KatanaMoveType move;

        // 2. Nhánh Không Chiến (`!player.onGround()`): Hit 1 Hất tung -> Hit 2 Chém ngang treo không -> Hit 3 Bổ thiên thạch!
        if (!player.onGround() && !player.isInWater() && !player.isFallFlying() && !player.isPassenger()) {
            if (tick - lastLocalAirTick > 22L) {
                localAirStep = 0;
            }
            move = KatanaMoveType.aerialByStep(localAirStep);
            localAirStep = Math.min(2, localAirStep + 1);
            lastLocalAirTick = tick;
            localPredictedStep = 0;
        } else if (wasSheathed) {
            move = KatanaMoveType.HIT_1_DRAW;
            localPredictedStep = 1;
        } else {
            long sinceLast = tick - lastAirSwingTick;
            // 3. Nhánh Bạt Kiếm Liên Hoàn (Delay Combo): Hit 1 -> Đợi >= 0.30s (Tick 10..28) -> Bấm đánh!
            if (lastLocalMove == KatanaMoveType.HIT_1_DRAW
                    && sinceLast >= KatanaCombatHandler.DELAY_COMBO_MIN_TICKS
                    && sinceLast <= KatanaCombatHandler.DELAY_COMBO_MAX_TICKS) {
                move = KatanaMoveType.DELAY_SWEEP_360;
                localPredictedStep = 0;
            } else {
                if (sinceLast > 32L) {
                    localPredictedStep = 0;
                }
                move = KatanaMoveType.comboByStep(localPredictedStep);
                localPredictedStep = (localPredictedStep + 1) % 4;
            }
        }

        lastLocalMove = move;
        lastAirSwingTick = tick;

        SYNCED_DRAWN.put(player.getId(), true);
        KatanaAnimManager.triggerMove(player.getId(), move, move.baseDisplayTicks, wasSheathed && move == KatanaMoveType.HIT_1_DRAW);
        spawnClientSlashVfx(player, move, wasSheathed && move == KatanaMoveType.HIT_1_DRAW, tick);
        applyLocalMoveKinematics(player, move);
        ClientPlayNetworking.send(new KatanaActionC2SPayload(KatanaActionC2SPayload.ACTION_AIR_SLASH));
    }

    private static void triggerLocalParryCounter(LocalPlayer player, long tick) {
        parryCounterUntilTick = -1000L;
        lastLocalMove = KatanaMoveType.PARRY_COUNTER;
        lastAirSwingTick = tick;
        localPredictedStep = 0;
        SYNCED_DRAWN.put(player.getId(), true);
        KatanaAnimManager.triggerMove(player.getId(), KatanaMoveType.PARRY_COUNTER,
                KatanaMoveType.PARRY_COUNTER.baseDisplayTicks, false);
        spawnClientSlashVfx(player, KatanaMoveType.PARRY_COUNTER, false, tick);
        applyLocalMoveKinematics(player, KatanaMoveType.PARRY_COUNTER);
        ClientPlayNetworking.send(new KatanaActionC2SPayload(KatanaActionC2SPayload.ACTION_PARRY_COUNTER));
    }

    private static void triggerLocalHeavyThrust(LocalPlayer player, long tick) {
        thrustTriggeredThisHold = true;
        lastLocalMove = KatanaMoveType.THRUST;
        lastAirSwingTick = tick;
        localPredictedStep = 0;
        KatanaAnimManager.triggerMove(player.getId(), KatanaMoveType.THRUST,
                KatanaMoveType.THRUST.baseDisplayTicks, false);
        spawnClientSlashVfx(player, KatanaMoveType.THRUST, false, tick);
        applyLocalMoveKinematics(player, KatanaMoveType.THRUST);
        ClientPlayNetworking.send(new KatanaActionC2SPayload(KatanaActionC2SPayload.ACTION_HEAVY_THRUST));
    }

    private static void spawnClientSlashVfx(Player player, KatanaMoveType move, boolean fromSheathed, long tick) {
        Vec3 anchor = player.position().add(0.0, player.getBbHeight() * (move == KatanaMoveType.HIT_1_DRAW ? 0.66 : 0.60), 0.0);
        if (move == KatanaMoveType.THRUST) {
            KatanaVfxRenderer.spawnThrustShockwave(player.getId(), anchor, player.getYRot(), player.getXRot(), tick);
        } else if (move == KatanaMoveType.PARRY_COUNTER) {
            KatanaVfxRenderer.spawnCounterSlash(player.getId(), anchor, player.getYRot(), player.getXRot(), tick);
        } else if (move != KatanaMoveType.AIR_3_SLAM && move != KatanaMoveType.IAIDO_BLINK) {
            KatanaVfxRenderer.spawnSlashArc(player.getId(), anchor, player.getYRot(), player.getXRot(),
                    move, fromSheathed || move == KatanaMoveType.HIT_4_FINISHER, tick);
        }
    }

    private static void applyLocalMoveKinematics(LocalPlayer player, KatanaMoveType move) {
        if (player.isPassenger() || player.isSwimming() || player.isFallFlying()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = (flat.lengthSqr() < 1e-4) ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
        Vec3 cur = player.getDeltaMovement();

        switch (move) {
            case AIR_1_LAUNCH -> {
                player.setDeltaMovement(cur.x * 0.65 + flat.x * 0.14, Math.max(cur.y, 0.28), cur.z * 0.65 + flat.z * 0.14);
                return;
            }
            case AIR_2_MID_SWEEP -> {
                player.setDeltaMovement(cur.x * 0.55 + flat.x * 0.12, 0.05, cur.z * 0.55 + flat.z * 0.12);
                return;
            }
            case AIR_3_SLAM -> {
                player.setDeltaMovement(flat.x * 0.20, -1.2, flat.z * 0.20);
                return;
            }
            case THRUST -> {
                player.setDeltaMovement(cur.x * 0.35 + flat.x * 0.36, cur.y, cur.z * 0.35 + flat.z * 0.36);
                return;
            }
            case PARRY_COUNTER -> {
                player.setDeltaMovement(cur.x * 0.35 + flat.x * 0.28, cur.y, cur.z * 0.35 + flat.z * 0.28);
                return;
            }
            default -> {
            }
        }
        if (player.isShiftKeyDown()) {
            return;
        }
        double impulse = switch (move) {
            case HIT_1_DRAW -> 0.175;
            case HIT_2_DOWN_DIAG -> 0.160;
            case HIT_3_UP_DIAG -> 0.175;
            case HIT_4_FINISHER -> 0.215;
            case DELAY_SWEEP_360 -> 0.180;
            default -> 0.0;
        };
        if (impulse > 0.0 && cur.horizontalDistanceSqr() < 0.18) {
            player.setDeltaMovement(cur.x + flat.x * impulse, cur.y, cur.z + flat.z * impulse);
        }
    }

    public static void tickClient(Minecraft mc) {
        KatanaAnimManager.tickClient(mc);
        long tick = KatanaAnimManager.getClientTick();

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            wasAttackDown = false;
            attackHoldTicks = 0;
            thrustTriggeredThisHold = false;
            if (level == null) {
                SYNCED_DRAWN.clear();
                IAIDO_CHARGING.clear();
                IAIDO_COOLDOWN_UNTIL.clear();
            }
            return;
        }

        if (WeaponsConfig.get().enableSlashVfx && (tick & 1L) == 0L) {
            spawnEdgeSparkles(mc, level, tick);
        }

        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.KATANA)) {
            SYNCED_DRAWN.put(player.getId(), false);
            IAIDO_CHARGING.remove(player.getId());
            wasAttackDown = false;
            attackHoldTicks = 0;
            thrustTriggeredThisHold = false;
            while (sheathKey != null && sheathKey.consumeClick()) {
                if (stack.is(ModItems.EXCALIBUR) && mc.screen == null && !player.isSpectator()) {
                    ExcaliburClientState.onSheatheKeyPressed(mc);
                }
            }
            return;
        }
        if (mc.screen != null || player.isSpectator()) {
            wasAttackDown = false;
            attackHoldTicks = 0;
            thrustTriggeredThisHold = false;
            while (sheathKey != null && sheathKey.consumeClick()) {
                if (stack.is(ModItems.EXCALIBUR) && mc.screen == null && !player.isSpectator()) {
                    ExcaliburClientState.onSheatheKeyPressed(mc);
                }
            }
            return;
        }

        if (player.onGround() && !KatanaAnimManager.isAirSlamDiving(player.getId())) {
            localAirStep = 0;
        }

        // Phím [G] chủ động rút kiếm (`Nukitsuke`) hoặc vẩy máu & tra kiếm về vỏ (`Chiburi & Noto`)
        if (sheathKey != null && sheathKey.consumeClick() && !KatanaAnimManager.isAirSlamDiving(player.getId())) {
            boolean nextDrawn = !isDrawn(player.getId(), stack);
            SYNCED_DRAWN.put(player.getId(), nextDrawn);
            IAIDO_CHARGING.remove(player.getId());
            if (nextDrawn) {
                lastAirSwingTick = tick;
                lastLocalMove = KatanaMoveType.HIT_1_DRAW;
                localPredictedStep = 1;
                KatanaAnimManager.triggerMove(player.getId(), KatanaMoveType.HIT_1_DRAW,
                        KatanaMoveType.HIT_1_DRAW.baseDisplayTicks, true);
                spawnClientSlashVfx(player, KatanaMoveType.HIT_1_DRAW, true, tick);
                applyLocalMoveKinematics(player, KatanaMoveType.HIT_1_DRAW);
            } else {
                parryCounterUntilTick = -1000L;
                KatanaAnimManager.triggerMove(player.getId(), KatanaMoveType.CHIBURI_NOTO,
                        KatanaMoveType.CHIBURI_NOTO.baseDisplayTicks, false);
            }
            ClientPlayNetworking.send(new KatanaActionC2SPayload(KatanaActionC2SPayload.ACTION_TOGGLE_SHEATH));
        }

        boolean attackDown = mc.options.keyAttack.isDown();
        boolean justPressed = attackDown && !wasAttackDown;
        wasAttackDown = attackDown;

        if (attackDown) {
            attackHoldTicks++;
        } else {
            attackHoldTicks = 0;
            thrustTriggeredThisHold = false;
        }

        // Nhánh 5 (Parry Counter): Bấm đánh ngay sau khi đỡ chuẩn nhịp (hoạt động ngay cả khi còn giữ/vừa nhả RMB!)
        if (justPressed && tick <= parryCounterUntilTick) {
            triggerLocalParryCounter(player, tick);
            return;
        }

        // Nhánh 1 (Hold Attack Thrust): Sau Hit 1 hoặc Hit 2 -> giữ chuột trái >= 5 ticks -> Đâm Xuyên Giáp Katate-tsuki!
        boolean drawn = isDrawn(player.getId(), stack);
        boolean holdThrustEligible = drawn && player.onGround() && !player.isUsingItem()
                && !KatanaAnimManager.isAirSlamDiving(player.getId())
                && (lastLocalMove == KatanaMoveType.HIT_1_DRAW || lastLocalMove == KatanaMoveType.HIT_2_DOWN_DIAG)
                && (tick - lastAirSwingTick) >= lastLocalMove.cancelAfterTicks
                && (tick - lastAirSwingTick) <= 26L;

        if (attackDown && !thrustTriggeredThisHold && holdThrustEligible && attackHoldTicks >= 5) {
            triggerLocalHeavyThrust(player, tick);
            return;
        }

        HitResult hit = mc.hitResult;
        // Giữ chuột chém liên tục chỉ kích khi KHÔNG nằm trong cửa sổ Hold-Thrust sau Hit 1/2
        boolean allowAutoHold = WeaponsConfig.get().enableHoldToSlash
                && !(lastLocalMove == KatanaMoveType.HIT_1_DRAW || lastLocalMove == KatanaMoveType.HIT_2_DOWN_DIAG);
        if (attackDown && !justPressed && !player.isUsingItem() && allowAutoHold
                && (hit == null || hit.getType() == HitResult.Type.MISS)) {
            onClientAirSwing(mc);
        }
    }

    private static void spawnEdgeSparkles(Minecraft mc, ClientLevel level, long tick) {
        RandomSource rng = level.random;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Player player : level.players()) {
            ItemStack main = player.getMainHandItem();
            if (!main.is(ModItems.KATANA) || player.isInvisible() || player.isSpectator()) {
                continue;
            }
            if (player == mc.player && firstPerson) {
                continue;
            }
            float draw = KatanaAnimManager.getDrawBlend(player.getId(), 0.0F);
            float guard = KatanaAnimManager.getGuardBlend(player.getId(), 0.0F);
            if (draw < 0.5F || (guard < 0.35F && (tick & 3L) != 0L)) {
                continue;
            }
            float yawRad = player.yBodyRot * Mth.DEG_TO_RAD;
            double fwdX = -Mth.sin(yawRad);
            double fwdZ = Mth.cos(yawRad);
            double rightX = -Mth.cos(yawRad);
            double rightZ = -Mth.sin(yawRad);

            Vec3 p;
            if (guard > 0.35F) {
                double u = rng.nextDouble();
                Vec3 hi = player.position().add(rightX * 0.18 + fwdX * 0.50, 1.48, rightZ * 0.18 + fwdZ * 0.50);
                Vec3 lo = player.position().add(-rightX * 0.72 + fwdX * 0.62, 0.36, -rightZ * 0.72 + fwdZ * 0.62);
                p = hi.lerp(lo, u);
            } else {
                double u = 0.22 + rng.nextDouble() * 0.78;
                p = player.position().add(fwdX * (0.46 + u * 1.12), 1.06 + u * 1.12, fwdZ * (0.46 + u * 1.12));
            }
            ExcaliburVfxRenderer.spawnSparkle(
                    p.x + (rng.nextDouble() - 0.5) * 0.08,
                    p.y + (rng.nextDouble() - 0.5) * 0.08,
                    p.z + (rng.nextDouble() - 0.5) * 0.08,
                    0.0F, 0.015F, 0.0F,
                    (guard > 0.35F ? 0.095F : 0.070F) + rng.nextFloat() * 0.035F,
                    115, 240, 255, tick, 8.0F);
        }
    }

    public static void handleSyncPayload(ClientLevel level, KatanaSyncPayload p) {
        long tick = KatanaAnimManager.getClientTick();
        KatanaMoveType move = KatanaMoveType.byId(p.moveId());
        Vec3 pos = new Vec3(p.x(), p.y(), p.z());
        Minecraft mc = Minecraft.getInstance();
        boolean isLocal = mc.player != null && mc.player.getId() == p.playerId();
        SYNCED_DRAWN.put(p.playerId(), p.drawn());

        switch (p.eventType()) {
            case KatanaSyncPayload.EVENT_SLASH -> {
                if (move == KatanaMoveType.IAIDO_BLINK) {
                    IAIDO_CHARGING.remove(p.playerId());
                    IAIDO_COOLDOWN_UNTIL.put(p.playerId(), tick + KatanaCombatHandler.IAIDO_BLINK_COOLDOWN_TICKS);
                    KatanaAnimManager.setIaidoCharging(p.playerId(), false);
                }
                boolean alreadyPredicted = isLocal && KatanaAnimManager.isMoveActive(p.playerId(), move)
                        && KatanaAnimManager.getActionProgress(p.playerId(), 0.0F) > 0.01F
                        && KatanaAnimManager.getActionProgress(p.playerId(), 0.0F) < 0.65F;
                if (!alreadyPredicted) {
                    boolean fromSheathed = p.extraValue() > 0.5F;
                    if (isLocal) {
                        lastAirSwingTick = tick;
                        lastLocalMove = move;
                        localPredictedStep = switch (move) {
                            case HIT_1_DRAW -> 1;
                            case HIT_2_DOWN_DIAG -> 2;
                            case HIT_3_UP_DIAG -> 3;
                            default -> 0;
                        };
                    }
                    KatanaAnimManager.triggerMove(p.playerId(), move, p.durationTicks(), fromSheathed);
                    if (move == KatanaMoveType.THRUST) {
                        KatanaVfxRenderer.spawnThrustShockwave(p.playerId(), pos, p.yawDeg(), p.pitchDeg(), tick);
                    } else if (move == KatanaMoveType.PARRY_COUNTER) {
                        KatanaVfxRenderer.spawnCounterSlash(p.playerId(), pos, p.yawDeg(), p.pitchDeg(), tick);
                    } else if (move != KatanaMoveType.AIR_3_SLAM && move != KatanaMoveType.IAIDO_BLINK) {
                        boolean flash = fromSheathed || move == KatanaMoveType.HIT_4_FINISHER;
                        KatanaVfxRenderer.spawnSlashArc(p.playerId(), pos, p.yawDeg(), p.pitchDeg(), move, flash, tick);
                    }
                }
            }
            case KatanaSyncPayload.EVENT_DEFLECT -> {
                if (isLocal) {
                    parryCounterUntilTick = tick + KatanaCombatHandler.PARRY_COUNTER_WINDOW_TICKS;
                }
                KatanaAnimManager.triggerMove(p.playerId(), KatanaMoveType.DEFLECT, p.durationTicks(), false);
                KatanaVfxRenderer.spawnDeflectBurst(pos, p.yawDeg(), p.pitchDeg(), p.extraValue() > 0.5F, tick);
                KatanaVfxRenderer.spawnSlashArc(p.playerId(), pos, p.yawDeg(), p.pitchDeg(),
                        KatanaMoveType.DEFLECT, true, tick);
            }
            case KatanaSyncPayload.EVENT_GUARD_UKE_HIT -> {
                KatanaAnimManager.triggerUkeNagashiHit(p.playerId());
                KatanaVfxRenderer.spawnUkeSlideSparks(pos, tick);
            }
            case KatanaSyncPayload.EVENT_GUARD_KASA_IMPACT -> {
                KatanaAnimManager.triggerMove(p.playerId(), KatanaMoveType.GUARD_KASA_IMPACT, p.durationTicks(), false);
                KatanaVfxRenderer.spawnKasaImpactSparks(pos, tick);
            }
            case KatanaSyncPayload.EVENT_GUARD_MUNE_OSHI -> {
                KatanaAnimManager.triggerMove(p.playerId(), KatanaMoveType.GUARD_MUNE_OSHI, p.durationTicks(), false);
                KatanaVfxRenderer.spawnMuneOshiBrace(pos, p.yawDeg(), tick);
            }
            case KatanaSyncPayload.EVENT_GUARD_SAYA_UKE -> {
                KatanaAnimManager.triggerMove(p.playerId(), KatanaMoveType.GUARD_SAYA_UKE_HIT, p.durationTicks(), false);
                KatanaVfxRenderer.spawnUkeSlideSparks(pos, tick);
            }
            case KatanaSyncPayload.EVENT_HIT_STOP -> {
                KatanaAnimManager.triggerHitStop(p.playerId(), p.durationTicks());
            }
            case KatanaSyncPayload.EVENT_SHEATH -> {
                IAIDO_CHARGING.remove(p.playerId());
                KatanaAnimManager.setIaidoCharging(p.playerId(), false);
                if (p.extraValue() > 0.5F
                        && (!isLocal || !KatanaAnimManager.isMoveActive(p.playerId(), KatanaMoveType.CHIBURI_NOTO))) {
                    KatanaAnimManager.triggerMove(p.playerId(), KatanaMoveType.CHIBURI_NOTO,
                            p.durationTicks() > 8.0F ? p.durationTicks() : KatanaMoveType.CHIBURI_NOTO.baseDisplayTicks, false);
                }
            }
            case KatanaSyncPayload.EVENT_IAIDO_CHARGE -> {
                boolean active = p.extraValue() > 0.5F;
                if (active) {
                    IAIDO_CHARGING.put(p.playerId(), tick);
                } else {
                    IAIDO_CHARGING.remove(p.playerId());
                }
                KatanaAnimManager.setIaidoCharging(p.playerId(), active);
            }
            case KatanaSyncPayload.EVENT_IAIDO_READY -> {
                // Ngôi sao 4 cánh lóe sáng tại miệng vỏ Koiguchi báo hiệu tụ lực Iaido hoàn tất (0.35s)!
                KatanaVfxRenderer.spawnKoiguchiGlint(p.playerId(), pos, p.yawDeg(), tick);
            }
            case KatanaSyncPayload.EVENT_JUDGMENT_CUT -> {
                // 3–5 vệt chém chéo đan xen trên không trung tại vị trí cũ của mục tiêu (hoặc kích nổ khi extraValue > 0.5)!
                KatanaVfxRenderer.spawnJudgmentCut(pos, p.yawDeg(), p.extraValue() > 0.5F, tick);
            }
            case KatanaSyncPayload.EVENT_AIR_SLAM_IMPACT -> {
                KatanaAnimManager.triggerAirSlamLanded(p.playerId());
                KatanaVfxRenderer.spawnGroundShockwave(pos, tick);
            }
            case KatanaSyncPayload.EVENT_DRAW, KatanaSyncPayload.EVENT_STATE -> {
            }
            default -> {
            }
        }
    }
}
