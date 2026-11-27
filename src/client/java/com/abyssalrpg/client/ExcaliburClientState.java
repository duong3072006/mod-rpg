package com.abyssalrpg.client;

import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.client.renderer.vfx.ExcaliburVfxRenderer;
import com.abyssalrpg.combat.ExcaliburCombatHandler;
import com.abyssalrpg.combat.ExcaliburMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.item.ExcaliburItem;
import com.abyssalrpg.network.ExcaliburActionC2SPayload;
import com.abyssalrpg.network.ExcaliburPassiveSyncPayload;
import com.abyssalrpg.network.ExcaliburSyncPayload;
import com.abyssalrpg.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Cầu nối Client & bộ nhận diện Input Trigger cho **Excalibur — Royal Swordplay & Fairy Tail Magic Circle**:
 * - Chuỗi đánh thường 4 hit (`HIT_1_SWEEP` → `HIT_2_RISING` → `HIT_3_THRUST` → `HIT_4_SLAM`).
 * - **3 Nhánh Combo Mở Rộng:**
 *   1. **Strike Air Thrust:** Sau Hit 1/2 → Giữ Chuột Trái (`>= 4` ticks) → `ACTION_STRIKE_AIR` (5 blocks, phá khiên).
 *   2. **Golden Crescent Wave:** Hit 1 → Đợi `0.30s` (Tick 7–26) → Bấm đánh → Xoay 360° + Phóng lưỡi liềm vàng mảnh 7 blocks.
 *   3. **Aerial Judgment:** Trên không bấm đánh 3 lần (`AIR_1_DIAG` → `AIR_2_DIAG` → `AIR_3_FALLING_STAR`).
 * - **Royal Parry Counter:** Bấm giữ `[RMB]` chuẩn nhịp `<= 0.18s` trước khi trúng đòn → Tự động gạt chệch & chém phản 200% ST!
 */
public final class ExcaliburClientState {
    public static final class PassiveSnapshot {
        public float energy = 0.0F;
        public int sealMask = 0;
        public int unlockedSeals = 0;
        public boolean avalonLinked = false;
        public boolean unsheathed = false;
        public int ultCooldownTicks = 0;
        public int avalonCdTicks = 0;
        public long receivedClientTick = 0L;
    }

    private static final Map<Integer, PassiveSnapshot> PASSIVE_STATES = new HashMap<>();
    private static long lastAirSwingTick = -100L;
    private static ExcaliburMoveType lastLocalMove = ExcaliburMoveType.HIT_1_SWEEP;
    private static int localPredictedStep = 0;

    private static int localAirStep = 0;
    private static long lastLocalAirTick = -100L;

    private static boolean wasAttackDown = false;
    private static int attackHoldTicks = 0;
    private static boolean strikeAirTriggeredThisHold = false;

    private ExcaliburClientState() {}

    public static PassiveSnapshot getPassive(int playerId, ItemStack stack) {
        PassiveSnapshot snap = PASSIVE_STATES.get(playerId);
        if (snap != null) {
            return snap;
        }
        PassiveSnapshot def = new PassiveSnapshot();
        def.energy = ExcaliburItem.getEnergy(stack);
        def.unsheathed = ExcaliburItem.isUnsheathed(stack);
        return def;
    }

    public static boolean isUnsheathed(int playerId, ItemStack stack) {
        PassiveSnapshot snap = PASSIVE_STATES.get(playerId);
        return snap != null ? snap.unsheathed : ExcaliburItem.isUnsheathed(stack);
    }

    /** Phím kích hoạt Tuyệt kỹ `[R]`: gửi yêu cầu tung chùm tia Excalibur lên Server (kiểm tra đủ 100 NL & hết hồi chiêu 25s). */
    public static void onUltimateKeyPressed(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null
                || player.isSpectator() || player.isHandsBusy()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.EXCALIBUR)) {
            return;
        }
        if (ExcaliburAnimManager.isCastingUltimate(player.getId())) {
            return;
        }
        ClientPlayNetworking.send(new ExcaliburActionC2SPayload(ExcaliburActionC2SPayload.ACTION_ULTIMATE, -1));
    }

    /** Phím thủ công `[G]`: Tra Excalibur vào vỏ Avalon (0.55s) khi kiếm đang rút ra! */
    public static void onSheatheKeyPressed(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null
                || player.isSpectator() || player.isHandsBusy() || player.isUsingItem()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.EXCALIBUR)) {
            return;
        }
        if (!isUnsheathed(player.getId(), stack)) {
            return;
        }
        if (ExcaliburAnimManager.isCastingUltimate(player.getId())
                || ExcaliburAnimManager.isFallingStarDiving(player.getId())
                || ExcaliburAnimManager.isMoveActive(player.getId(), ExcaliburMoveType.SHEATHE_AVALON)
                || ExcaliburAnimManager.isMoveActive(player.getId(), ExcaliburMoveType.ROYAL_PARRY_COUNTER)
                || ExcaliburAnimManager.isMoveActive(player.getId(), ExcaliburMoveType.AVALON_PARRY_COUNTER)) {
            return;
        }
        PassiveSnapshot snap = PASSIVE_STATES.computeIfAbsent(player.getId(), k -> {
            PassiveSnapshot s = new PassiveSnapshot();
            s.energy = ExcaliburItem.getEnergy(stack);
            return s;
        });
        snap.unsheathed = false;
        ExcaliburAnimManager.setUnsheathed(player.getId(), false);
        ExcaliburAnimManager.triggerMove(player.getId(), ExcaliburMoveType.SHEATHE_AVALON,
                ExcaliburMoveType.SHEATHE_AVALON.baseDisplayTicks);
        ClientPlayNetworking.send(new ExcaliburActionC2SPayload(ExcaliburActionC2SPayload.ACTION_SHEATHE, -1));
    }

    public static boolean canCastUltimateClient(int playerId) {
        if (ExcaliburAnimManager.isCastingUltimate(playerId)) {
            return false;
        }
        PassiveSnapshot snap = PASSIVE_STATES.get(playerId);
        if (snap != null) {
            return snap.ultCooldownTicks <= 0 && snap.energy >= ExcaliburItem.MAX_ENERGY - 0.01F;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == playerId) {
            ItemStack main = mc.player.getMainHandItem();
            return ExcaliburItem.getSavedUltCooldownTicks(main) <= 0
                    && ExcaliburItem.getEnergy(main) >= ExcaliburItem.MAX_ENERGY - 0.01F;
        }
        return false;
    }

    public static void handlePassiveSync(ExcaliburPassiveSyncPayload p) {
        PassiveSnapshot snap = PASSIVE_STATES.computeIfAbsent(p.playerId(), k -> new PassiveSnapshot());
        snap.energy = Mth.clamp(p.energy(), 0.0F, ExcaliburItem.MAX_ENERGY);
        snap.sealMask = p.sealMask() & 0x1FFF;
        snap.unlockedSeals = p.unlockedSeals();
        snap.avalonLinked = p.avalonLinked();
        snap.unsheathed = p.unsheathed();
        snap.ultCooldownTicks = Math.max(0, p.ultCooldownTicks());
        snap.avalonCdTicks = Math.max(0, p.avalonCdTicks());
        snap.receivedClientTick = ExcaliburAnimManager.getClientTick();
        ExcaliburAnimManager.setUnsheathed(p.playerId(), p.unsheathed());
    }

    public static void onClientAirSwing(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null
                || player.isSpectator() || player.isHandsBusy()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.EXCALIBUR)) {
            return;
        }
        ExcaliburMoveType cur = ExcaliburAnimManager.getMoveType(player.getId());
        float curAlpha = ExcaliburAnimManager.getActionProgress(player.getId(), 0.0F);
        if ((curAlpha > 0.001F && (cur.lockedSkill
                || cur == ExcaliburMoveType.ROYAL_PARRY_COUNTER
                || cur == ExcaliburMoveType.AVALON_PARRY_COUNTER))
                || ExcaliburAnimManager.isFallingStarDiving(player.getId())) {
            return;
        }
        if (player.isUsingItem()) {
            return;
        }
        long tick = ExcaliburAnimManager.getClientTick();
        if (tick - lastAirSwingTick < lastLocalMove.cancelAfterTicks) {
            return;
        }

        ExcaliburMoveType move;
        // 1. Nhánh C: Không Chiến — Aerial Judgment (`!player.onGround()`)
        if (!player.onGround() && !player.isInWater() && !player.isFallFlying() && !player.isPassenger()) {
            if (tick - lastLocalAirTick > 22L) {
                localAirStep = 0;
            }
            move = ExcaliburMoveType.aerialByStep(localAirStep);
            localAirStep = Math.min(2, localAirStep + 1);
            lastLocalAirTick = tick;
            localPredictedStep = 0;
        } else {
            long sinceLast = tick - lastAirSwingTick;
            // 2. Nhánh B: Delay Combo (Hit 1 -> Đợi 0.30s [7–26t] -> Đánh -> Golden Crescent Wave!)
            if (lastLocalMove == ExcaliburMoveType.HIT_1_SWEEP
                    && sinceLast >= ExcaliburCombatHandler.DELAY_CRESCENT_MIN_TICKS
                    && sinceLast <= ExcaliburCombatHandler.DELAY_CRESCENT_MAX_TICKS) {
                move = ExcaliburMoveType.GOLDEN_CRESCENT;
                localPredictedStep = 0;
            } else {
                if (sinceLast > 36L) {
                    localPredictedStep = 0;
                }
                move = ExcaliburMoveType.comboByStep(localPredictedStep);
                localPredictedStep = (localPredictedStep + 1) % 4;
            }
        }

        lastLocalMove = move;
        lastAirSwingTick = tick;
        PassiveSnapshot snap = PASSIVE_STATES.computeIfAbsent(player.getId(), k -> new PassiveSnapshot());
        snap.unsheathed = true;
        ExcaliburAnimManager.setUnsheathed(player.getId(), true);
        ExcaliburAnimManager.triggerMove(player.getId(), move, move.baseDisplayTicks);
        applyLocalMoveKinematics(player, move);
        spawnClientMoveVfx(player, move, tick);
        ClientPlayNetworking.send(new ExcaliburActionC2SPayload(ExcaliburActionC2SPayload.ACTION_AIR_SLASH, -1));
    }

    private static void triggerLocalStrikeAir(LocalPlayer player, long tick) {
        strikeAirTriggeredThisHold = true;
        lastLocalMove = ExcaliburMoveType.STRIKE_AIR;
        lastAirSwingTick = tick;
        localPredictedStep = 0;
        PassiveSnapshot snap = PASSIVE_STATES.computeIfAbsent(player.getId(), k -> new PassiveSnapshot());
        snap.unsheathed = true;
        ExcaliburAnimManager.setUnsheathed(player.getId(), true);
        ExcaliburAnimManager.triggerMove(player.getId(), ExcaliburMoveType.STRIKE_AIR,
                ExcaliburMoveType.STRIKE_AIR.baseDisplayTicks);
        ClientPlayNetworking.send(new ExcaliburActionC2SPayload(ExcaliburActionC2SPayload.ACTION_STRIKE_AIR, -1));
    }

    private static void spawnClientMoveVfx(Player player, ExcaliburMoveType move, long tick) {
        if (move == ExcaliburMoveType.STRIKE_AIR || move == ExcaliburMoveType.AIR_3_FALLING_STAR) {
            return;
        }
        Vec3 anchor = player.position().add(0.0, player.getBbHeight() * 0.62, 0.0);
        ExcaliburVfxRenderer.spawnSlashArc(player.getId(), anchor, player.getYRot(), player.getXRot(), move, tick);
    }

    private static void applyLocalMoveKinematics(LocalPlayer player, ExcaliburMoveType move) {
        if (player.isPassenger() || player.isSwimming() || player.isFallFlying()) {
            return;
        }
        Vec3 fwd = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 cur = player.getDeltaMovement();
        switch (move) {
            case AIR_1_DIAG, AIR_2_DIAG -> {
                player.setDeltaMovement(cur.x * 0.60 + fwd.x * 0.14, 0.05, cur.z * 0.60 + fwd.z * 0.14);
                return;
            }
            case AIR_3_FALLING_STAR -> {
                player.setDeltaMovement(fwd.x * 0.14, -1.3, fwd.z * 0.14);
                return;
            }
            default -> {
            }
        }
        double impulse = switch (move) {
            case HIT_1_SWEEP -> 0.155;
            case HIT_2_RISING -> 0.140;
            case HIT_3_THRUST -> 0.360;
            case HIT_4_SLAM -> 0.155;
            case GOLDEN_CRESCENT -> 0.150;
            default -> 0.0;
        };
        if (impulse > 0.0) {
            double hopY = (move == ExcaliburMoveType.HIT_4_SLAM && player.onGround()) ? 0.13 : 0.0;
            player.setDeltaMovement(cur.x * 0.45 + fwd.x * impulse, cur.y + hopY, cur.z * 0.45 + fwd.z * impulse);
        }
    }

    public static void tickClient(Minecraft mc) {
        ExcaliburAnimManager.tickClient(mc);
        long tick = ExcaliburAnimManager.getClientTick();

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            wasAttackDown = false;
            attackHoldTicks = 0;
            strikeAirTriggeredThisHold = false;
            if (level == null) {
                PASSIVE_STATES.clear();
            }
            return;
        }

        for (PassiveSnapshot snap : PASSIVE_STATES.values()) {
            if (snap.ultCooldownTicks > 0) snap.ultCooldownTicks--;
            if (snap.avalonCdTicks > 0) snap.avalonCdTicks--;
        }

        if (WeaponsConfig.get().enableSlashVfx) {
            spawnMinimalBladeMotes(mc, level, tick);
        }

        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.EXCALIBUR) || mc.screen != null || player.isSpectator()) {
            wasAttackDown = false;
            attackHoldTicks = 0;
            strikeAirTriggeredThisHold = false;
            return;
        }

        if (player.onGround() && !ExcaliburAnimManager.isFallingStarDiving(player.getId()) && (tick - lastLocalAirTick) > 4L) {
            localAirStep = 0;
        }

        boolean attackDown = mc.options.keyAttack.isDown();
        boolean justPressed = attackDown && !wasAttackDown;
        wasAttackDown = attackDown;

        if (attackDown) {
            attackHoldTicks++;
        } else {
            attackHoldTicks = 0;
            strikeAirTriggeredThisHold = false;
        }

        // Nhánh A (Strike Air Thrust — Hold Attack): Sau Hit 1/2 giữ chuột trái >= 4 ticks -> Lấy đà + Đâm phóng vòng xung kích 5 blocks!
        boolean strikeAirEligible = player.onGround() && !player.isUsingItem()
                && !ExcaliburAnimManager.isCastingUltimate(player.getId())
                && !ExcaliburAnimManager.isFallingStarDiving(player.getId())
                && (lastLocalMove == ExcaliburMoveType.HIT_1_SWEEP || lastLocalMove == ExcaliburMoveType.HIT_2_RISING)
                && (tick - lastAirSwingTick) >= lastLocalMove.cancelAfterTicks
                && (tick - lastAirSwingTick) <= 24L;

        if (attackDown && !strikeAirTriggeredThisHold && strikeAirEligible && attackHoldTicks >= 4) {
            triggerLocalStrikeAir(player, tick);
            return;
        }

        HitResult hit = mc.hitResult;
        boolean allowAutoHold = WeaponsConfig.get().enableHoldToSlash
                && !(lastLocalMove == ExcaliburMoveType.HIT_1_SWEEP || lastLocalMove == ExcaliburMoveType.HIT_2_RISING);
        if (attackDown && !justPressed && !player.isUsingItem() && allowAutoHold
                && (hit == null || hit.getType() == HitResult.Type.MISS)) {
            onClientAirSwing(mc);
        }
    }

    /** Tối giản sạch sẽ: Chỉ thả vài đốm sáng vàng tinh tế dọc thân kiếm khi đang tụ lực Tuyệt Kỹ (hoặc khi đầy 100 NL), không khói/bụi! */
    private static void spawnMinimalBladeMotes(Minecraft mc, ClientLevel level, long tick) {
        if ((tick & 1L) != 0L) {
            return;
        }
        RandomSource rng = level.random;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Player player : level.players()) {
            ItemStack main = player.getMainHandItem();
            if (!main.is(ModItems.EXCALIBUR) || player.isInvisible() || player.isSpectator()) {
                continue;
            }
            boolean castingUlt = ExcaliburAnimManager.isCastingUltimate(player.getId());
            boolean fullEnergy = getPassive(player.getId(), main).energy >= ExcaliburItem.MAX_ENERGY - 0.5F;
            if (!castingUlt && (!fullEnergy || (tick & 3L) != 0L)) {
                continue;
            }
            if (player == mc.player && firstPerson && !castingUlt) {
                continue;
            }
            float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
            double fwdX = -Mth.sin(yawRad);
            double fwdZ = Mth.cos(yawRad);

            double sx = player.getX() + fwdX * (castingUlt ? 0.42 : 0.35) + (rng.nextDouble() - 0.5) * 0.24;
            double sy = player.getY() + (castingUlt ? (1.45 + rng.nextDouble() * 1.35) : (1.10 + rng.nextDouble() * 0.90));
            double sz = player.getZ() + fwdZ * (castingUlt ? 0.42 : 0.35) + (rng.nextDouble() - 0.5) * 0.24;
            ExcaliburVfxRenderer.spawnSparkle(sx, sy, sz,
                    0.0F, 0.032F + rng.nextFloat() * 0.022F, 0.0F,
                    castingUlt ? (0.095F + rng.nextFloat() * 0.045F) : 0.075F,
                    255, 236, 130, tick, 8.0F);
        }
    }

    public static void handleSyncPayload(ClientLevel level, ExcaliburSyncPayload p) {
        long tick = ExcaliburAnimManager.getClientTick();
        ExcaliburMoveType move = ExcaliburMoveType.byId(p.moveId());
        Vec3 pos = new Vec3(p.x(), p.y(), p.z());
        Minecraft mc = Minecraft.getInstance();
        boolean isLocal = mc.player != null && mc.player.getId() == p.playerId();

        switch (p.eventType()) {
            case ExcaliburSyncPayload.EVENT_SLASH -> {
                if (!isLocal || ExcaliburAnimManager.getActionProgress(p.playerId(), 0.0F) <= 0.01F) {
                    ExcaliburAnimManager.triggerMove(p.playerId(), move, p.durationTicks());
                }
                if (move != ExcaliburMoveType.STRIKE_AIR && move != ExcaliburMoveType.AIR_3_FALLING_STAR) {
                    ExcaliburVfxRenderer.spawnSlashArc(p.playerId(), pos, p.yawDeg(), p.pitchDeg(), move, tick);
                }
            }
            case ExcaliburSyncPayload.EVENT_HIT_STOP -> {
                ExcaliburAnimManager.triggerHitStop(p.playerId(), p.durationTicks());
            }
            case ExcaliburSyncPayload.EVENT_ULT_START -> {
                ExcaliburAnimManager.triggerMove(p.playerId(), ExcaliburMoveType.ULT_EXCALIBUR, p.durationTicks());
                ExcaliburVfxRenderer.spawnUltGroundMagicCircle(p.playerId(), tick, p.durationTicks());
            }
            case ExcaliburSyncPayload.EVENT_BEAM_FIRE -> {
                ExcaliburVfxRenderer.spawnExcaliburBeamAndCircles(p.playerId(), pos, p.yawDeg(), p.pitchDeg(),
                        p.rangeBlocks(), p.durationTicks(), tick);
            }
            case ExcaliburSyncPayload.EVENT_STRIKE_AIR_BLAST -> {
                ExcaliburVfxRenderer.spawnStrikeAirRing(pos, p.yawDeg(), p.pitchDeg(), p.rangeBlocks(), tick);
            }
            case ExcaliburSyncPayload.EVENT_GROUND_WAVE -> {
                ExcaliburVfxRenderer.spawnGoldenCrescentWave(pos, p.yawDeg(), p.rangeBlocks(), tick);
            }
            case ExcaliburSyncPayload.EVENT_FALLING_STAR_IMPACT -> {
                ExcaliburAnimManager.triggerFallingStarLanded(p.playerId());
                ExcaliburVfxRenderer.spawnAirImpactMagicCircle(pos, p.rangeBlocks(), tick, p.durationTicks());
            }
            case ExcaliburSyncPayload.EVENT_ROYAL_PARRY -> {
                ExcaliburAnimManager.triggerMove(p.playerId(), ExcaliburMoveType.ROYAL_PARRY_COUNTER, p.durationTicks());
                ExcaliburVfxRenderer.spawnParryMagicCircle(pos, p.yawDeg(), p.pitchDeg(), tick);
            }
            case ExcaliburSyncPayload.EVENT_ROYAL_COUNTER_SLASH -> {
                ExcaliburVfxRenderer.spawnSlashArc(p.playerId(), pos, p.yawDeg(), p.pitchDeg(),
                        ExcaliburMoveType.ROYAL_PARRY_COUNTER, tick);
            }
            case ExcaliburSyncPayload.EVENT_AVALON_PARRY_GUARD -> {
                PassiveSnapshot snap = PASSIVE_STATES.computeIfAbsent(p.playerId(), k -> new PassiveSnapshot());
                snap.unsheathed = false;
                ExcaliburAnimManager.setUnsheathed(p.playerId(), false);
            }
            case ExcaliburSyncPayload.EVENT_AVALON_PARRY_COUNTER -> {
                PassiveSnapshot snap = PASSIVE_STATES.computeIfAbsent(p.playerId(), k -> new PassiveSnapshot());
                snap.unsheathed = true;
                ExcaliburAnimManager.setUnsheathed(p.playerId(), true);
                ExcaliburAnimManager.triggerMove(p.playerId(), ExcaliburMoveType.AVALON_PARRY_COUNTER, p.durationTicks());
                // Lóe 1 vòng phép thuật 2D nhỏ màu vàng (Fairy Tail) ngay tại điểm va chạm trên vỏ Avalon + vệt chém ngang Trái->Phải!
                ExcaliburVfxRenderer.spawnParryMagicCircle(pos, p.yawDeg(), p.pitchDeg(), tick);
                Vec3 slashAnchor = pos;
                if (level.getEntity(p.playerId()) instanceof Player pl) {
                    slashAnchor = pl.position().add(0.0, pl.getBbHeight() * 0.62, 0.0);
                }
                ExcaliburVfxRenderer.spawnSlashArc(p.playerId(), slashAnchor, p.yawDeg(), p.pitchDeg(),
                        ExcaliburMoveType.AVALON_PARRY_COUNTER, tick);
                if (isLocal) {
                    localPredictedStep = 0;
                    lastLocalMove = ExcaliburMoveType.AVALON_PARRY_COUNTER;
                    lastAirSwingTick = tick;
                }
            }
            case ExcaliburSyncPayload.EVENT_GUARD_CROSS_HIT -> {
                ExcaliburAnimManager.triggerCrossGuardHit(p.playerId());
                ExcaliburVfxRenderer.spawnGuardSparks(pos, false, tick);
            }
            case ExcaliburSyncPayload.EVENT_GUARD_OVERHEAD_IMPACT -> {
                ExcaliburAnimManager.triggerMove(p.playerId(), ExcaliburMoveType.GUARD_OVERHEAD_IMPACT, p.durationTicks());
                ExcaliburVfxRenderer.spawnGuardSparks(pos, true, tick);
            }
            case ExcaliburSyncPayload.EVENT_SHEATHE -> {
                PassiveSnapshot snap = PASSIVE_STATES.computeIfAbsent(p.playerId(), k -> new PassiveSnapshot());
                snap.unsheathed = false;
                ExcaliburAnimManager.setUnsheathed(p.playerId(), false);
                if (!isLocal || !ExcaliburAnimManager.isMoveActive(p.playerId(), ExcaliburMoveType.SHEATHE_AVALON)) {
                    ExcaliburAnimManager.triggerMove(p.playerId(), ExcaliburMoveType.SHEATHE_AVALON, p.durationTicks());
                }
            }
            case ExcaliburSyncPayload.EVENT_SHEATHE_CLINK -> {
                // 3 đốm sáng vàng nhỏ lóe gọn ngay tại miệng vỏ Avalon đúng mốc 0.40s ("Clink!")
                RandomSource rng = level.random;
                for (int i = 0; i < 3; i++) {
                    double ang = (Mth.TWO_PI * i) / 3.0 + rng.nextDouble() * 0.6;
                    ExcaliburVfxRenderer.spawnSparkle(
                            pos.x + Math.cos(ang) * 0.05, pos.y + (rng.nextDouble() - 0.5) * 0.05, pos.z + Math.sin(ang) * 0.05,
                            (float) (Math.cos(ang) * 0.025), 0.024F + rng.nextFloat() * 0.015F, (float) (Math.sin(ang) * 0.025),
                            0.105F + rng.nextFloat() * 0.035F,
                            255, 240, 130, tick, 6.0F);
                }
            }
            case ExcaliburSyncPayload.EVENT_AVALON_REVIVE -> {
                ExcaliburVfxRenderer.spawnAvalonSanctuary(p.playerId(), tick, 24.0F);
                RandomSource rng = level.random;
                for (int i = 0; i < 24; i++) {
                    double ang = rng.nextDouble() * Mth.TWO_PI;
                    double rad = 0.6 + rng.nextDouble() * 1.2;
                    ExcaliburVfxRenderer.spawnSparkle(
                            pos.x + Math.cos(ang) * rad, pos.y + 0.3 + rng.nextDouble() * 1.6, pos.z + Math.sin(ang) * rad,
                            0.0F, 0.055F + rng.nextFloat() * 0.04F, 0.0F,
                            0.14F + rng.nextFloat() * 0.08F,
                            (i & 1) == 0 ? 120 : 255, (i & 1) == 0 ? 245 : 230, (i & 1) == 0 ? 255 : 120, tick, 16.0F);
                }
            }
            case ExcaliburSyncPayload.EVENT_SEAL_UNLOCK -> {
                for (int i = 0; i < 16; i++) {
                    double ang = (Mth.TWO_PI * i) / 16.0;
                    ExcaliburVfxRenderer.spawnSparkle(
                            pos.x + Math.cos(ang) * 1.15, pos.y + 0.9, pos.z + Math.sin(ang) * 1.15,
                            (float) (Math.cos(ang) * 0.07), 0.045F, (float) (Math.sin(ang) * 0.07),
                            0.14F, 255, 240, 145, tick, 12.0F);
                }
            }
            default -> {
            }
        }
    }
}
