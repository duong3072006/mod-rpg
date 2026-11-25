package com.abyssalrpg.client;

import com.abyssalrpg.client.anim.ExcaliburAnimManager;
import com.abyssalrpg.client.renderer.vfx.ExcaliburVfxRenderer;
import com.abyssalrpg.combat.ExcaliburMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.item.ExcaliburItem;
import com.abyssalrpg.network.ExcaliburActionC2SPayload;
import com.abyssalrpg.network.ExcaliburSyncPayload;
import com.abyssalrpg.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Cầu nối Client cho **Excalibur & Excalibur Morgan**:
 * - Xử lý LMB chém gió / Hold-to-Slash 3 nhịp combo hiệp sĩ.
 * - Xử lý phím `[R]` gồng 4.0s Dark Mana (hỗ trợ cả nhấn giữ `[R]` & bấm kích hoạt `[R]`,
 *   chia sẻ an toàn cùng keybind `[R]` không xung đột với Togetsu!).
 * - Sinh hào quang & đốm sao 4 cánh mật độ chuẩn Togetsu dọc theo thân kiếm (Vàng Trắng cho Golden Excalibur,
 *   Hắc Ám Đen–Tím Sẫm ôm sát lưỡi kiếm cho Excalibur Morgan!).
 * - Nhận gói đồng bộ S2C kích hoạt chùm tia năng lượng 3D, vệt chém lưỡi liềm, cột ánh sáng & vòng xoáy ma lực!
 */
public final class ExcaliburClientState {
    private static final DustParticleOptions GOLD_MOTE =
            new DustParticleOptions(new Vector3f(1.00F, 0.88F, 0.34F), 0.86F);
    private static final DustParticleOptions WHITE_HOLY =
            new DustParticleOptions(new Vector3f(1.00F, 0.99F, 0.90F), 0.76F);
    private static final DustParticleOptions MORGAN_SHADOW =
            new DustParticleOptions(new Vector3f(0.10F, 0.03F, 0.05F), 1.02F);
    private static final DustParticleOptions MORGAN_CRIMSON =
            new DustParticleOptions(new Vector3f(0.98F, 0.10F, 0.16F), 0.92F);

    private static final java.util.Map<Integer, Float> SYNCED_ENERGY = new java.util.HashMap<>();
    private static long lastAirSwingTick = -100L;
    private static int localPredictedStep = 0;
    private static boolean wasAttackDown = false;
    private static boolean localChanneling = false;

    private ExcaliburClientState() {}

    public static float getSyncedEnergy(int playerId, ItemStack stack) {
        Float synced = SYNCED_ENERGY.get(playerId);
        return synced != null ? synced : ExcaliburItem.getEnergy(stack);
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
        if (curAlpha > 0.001F && cur.lockedSkill) {
            return;
        }
        long tick = ExcaliburAnimManager.getClientTick();
        if (tick - lastAirSwingTick < 7L) {
            return;
        }
        if (tick - lastAirSwingTick > 44L) {
            localPredictedStep = 0;
        }
        lastAirSwingTick = tick;

        ExcaliburMoveType move = ExcaliburMoveType.comboByStep(localPredictedStep);
        localPredictedStep = (localPredictedStep + 1) % 3;
        ExcaliburAnimManager.triggerMove(player.getId(), move, move.baseDisplayTicks);
        // Sinh ngay vệt chém bám sát người chơi không trễ 1 tick!
        boolean morgan = ExcaliburItem.isMorgan(stack);
        ExcaliburVfxRenderer.spawnSlashArc(player.getId(),
                player.position().add(0.0, player.getBbHeight() * 0.62, 0.0),
                player.getYRot(), player.getXRot(), move, morgan, tick);
        ClientPlayNetworking.send(new ExcaliburActionC2SPayload(ExcaliburActionC2SPayload.ACTION_AIR_SLASH, -1));
    }

    public static void onTransformKeyPressed(LocalPlayer player) {
        if (!player.getMainHandItem().is(ModItems.EXCALIBUR) || player.isSpectator()) {
            return;
        }
        ClientPlayNetworking.send(new ExcaliburActionC2SPayload(ExcaliburActionC2SPayload.ACTION_CHARGE_START, -1));
    }

    public static void tickClient(Minecraft mc) {
        ExcaliburAnimManager.tickClient(mc);
        long tick = ExcaliburAnimManager.getClientTick();

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            wasAttackDown = false;
            if (level == null) {
                SYNCED_ENERGY.clear();
            }
            return;
        }

        // 1. Sinh hào quang bám CHUẨN ĐỘNG TÁC KIẾM (mọi tick khi đang chém để liền mạch, 2 tick/lần khi đứng thủ!)
        if (WeaponsConfig.get().enableSlashVfx) {
            spawnBladeAurasForAllPlayers(mc, level, tick);
        }

        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.EXCALIBUR) || mc.screen != null || player.isSpectator()) {
            wasAttackDown = false;
            if (localChanneling) {
                localChanneling = false;
                ClientPlayNetworking.send(new ExcaliburActionC2SPayload(ExcaliburActionC2SPayload.ACTION_CHARGE_CANCEL, -1));
            }
            return;
        }

        // 2. Support Hold-to-Slash cleanly when holding LMB (respects block mining)
        boolean attackDown = mc.options.keyAttack.isDown();
        boolean justPressed = attackDown && !wasAttackDown;
        wasAttackDown = attackDown;
        HitResult hit = mc.hitResult;
        if (attackDown && !justPressed && WeaponsConfig.get().enableHoldToSlash
                && (hit == null || hit.getType() == HitResult.Type.MISS)) {
            onClientAirSwing(mc);
        }
    }

    private static void spawnBladeAurasForAllPlayers(Minecraft mc, ClientLevel level, long tick) {
        RandomSource rng = level.random;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Player player : level.players()) {
            ItemStack main = player.getMainHandItem();
            if (!main.is(ModItems.EXCALIBUR) || player.isInvisible() || player.isSpectator()) {
                continue;
            }
            boolean morgan = ExcaliburItem.isMorgan(main);
            float actionAlpha = ExcaliburAnimManager.getActionProgress(player.getId(), 0.0F);
            boolean isSwinging = actionAlpha > 0.01F;

            // Khi đứng yên ở góc nhìn thứ 1: không phun hạt trôi lơ lửng ngay giữa tâm ngắm!
            if (player == mc.player && firstPerson && !isSwinging) {
                continue;
            }
            if (!isSwinging && (tick & 1L) != 0L) {
                continue;
            }

            float yawRad = (isSwinging ? player.getYRot() : player.yBodyRot) * Mth.DEG_TO_RAD;
            double fwdX = -Mth.sin(yawRad);
            double fwdZ = Mth.cos(yawRad);
            double rightX = -Mth.cos(yawRad);
            double rightZ = -Mth.sin(yawRad);

            Vec3 base;
            Vec3 tip;
            Vec3 trailVel = Vec3.ZERO;

            if (isSwinging) {
                ExcaliburMoveType move = ExcaliburAnimManager.getMoveType(player.getId());
                Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.62, 0.0);
                if (move == ExcaliburMoveType.COMBO_1 || move == ExcaliburMoveType.COMBO_2 || move == ExcaliburMoveType.COMBO_3) {
                    // Tính chính xác vị trí lưỡi kiếm ĐANG DI CHUYỂN theo cung chém để aura & sao bám dính lưỡi kiếm!
                    float startDeg = (move == ExcaliburMoveType.COMBO_1) ? -78.0F : ((move == ExcaliburMoveType.COMBO_2) ? 84.0F : -86.0F);
                    float endDeg = (move == ExcaliburMoveType.COMBO_1) ? 82.0F : ((move == ExcaliburMoveType.COMBO_2) ? -84.0F : 80.0F);
                    float rollRad = ((move == ExcaliburMoveType.COMBO_1) ? -34.0F : ((move == ExcaliburMoveType.COMBO_2) ? 14.0F : 90.0F)) * Mth.DEG_TO_RAD;
                    float delay = (move == ExcaliburMoveType.COMBO_3) ? 0.24F : 0.14F;
                    float p = Mth.clamp((actionAlpha - delay) / 0.44F, 0.0F, 1.0F);
                    float sweep = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p) * (1.0F - p);
                    float ang = Mth.lerp(sweep, startDeg, endDeg) * Mth.DEG_TO_RAD;
                    // Hướng cục bộ trên mặt phẳng chém -> xoay roll -> xoay yaw ra tọa độ thế giới
                    double lx = Mth.sin(ang);
                    double lz = Mth.cos(ang);
                    double cr = Math.cos(rollRad), sr = Math.sin(rollRad);
                    double rx = lx * cr;
                    double ry = lx * sr;
                    Vec3 dir = new Vec3(rightX * rx + fwdX * lz, ry, rightZ * rx + fwdZ * lz).normalize();
                    base = center.add(dir.scale(0.62));
                    tip = center.add(dir.scale(1.98));
                    // Vận tốc văng nhẹ theo tiếp tuyến chiều chém tạo vệt hào quang cuốn mượt!
                    float dirSign = Math.signum(endDeg - startDeg);
                    double tx = Mth.cos(ang) * dirSign * cr;
                    double ty = Mth.cos(ang) * dirSign * sr;
                    double tz = -Mth.sin(ang) * dirSign;
                    trailVel = new Vec3(rightX * tx + fwdX * tz, ty, rightZ * tx + fwdZ * tz).scale(0.060);
                } else if (move == ExcaliburMoveType.MORGAN_CHANNEL) {
                    // Kiếm dựng thẳng trước ngực khi gồng biến đổi [R]
                    base = player.position().add(fwdX * 0.42, 0.92, fwdZ * 0.42);
                    tip = base.add(0.0, 1.15, 0.0);
                } else {
                    // ULT skyward raise / slam
                    boolean raised = actionAlpha < 0.625F;
                    base = player.position().add(fwdX * 0.35, raised ? 1.70 : 1.05, fwdZ * 0.35);
                    tip = raised ? base.add(0.0, 1.30, 0.0) : base.add(fwdX * 1.70, -0.32, fwdZ * 1.70);
                }
            } else if (player.isSprinting()) {
                base = player.position().add(rightX * 0.29 - fwdX * 0.46, 0.76, rightZ * 0.29 - fwdZ * 0.46);
                tip = player.position().add(rightX * 0.59 - fwdX * 1.60, 0.66, rightZ * 0.59 - fwdZ * 1.60);
            } else if (morgan) {
                base = player.position().add(rightX * 0.34 + fwdX * 0.59, 0.75, rightZ * 0.34 + fwdZ * 0.59);
                tip = player.position().add(rightX * 0.81 + fwdX * 1.58, 0.29, rightZ * 0.81 + fwdZ * 1.58);
            } else {
                base = player.position().add(rightX * 0.07 + fwdX * 0.76, 1.30, rightZ * 0.07 + fwdZ * 0.76);
                tip = player.position().add(-rightX * 0.24 + fwdX * 1.64, 2.04, -rightZ * 0.24 + fwdZ * 1.64);
            }

            int moteCount = isSwinging ? 3 : 2;
            for (int k = 0; k < moteCount; k++) {
                double u = isSwinging ? (0.35 + rng.nextDouble() * 0.65) : (0.12 + rng.nextDouble() * 0.86);
                double spread = isSwinging ? 0.10 : 0.16;
                double px = Mth.lerp(u, base.x, tip.x) + (rng.nextDouble() - 0.5) * spread;
                double py = Mth.lerp(u, base.y, tip.y) + (rng.nextDouble() - 0.5) * spread;
                double pz = Mth.lerp(u, base.z, tip.z) + (rng.nextDouble() - 0.5) * spread;
                if (morgan) {
                    level.addParticle((k == 0) ? MORGAN_SHADOW : MORGAN_CRIMSON, px, py, pz,
                            trailVel.x, 0.014 + trailVel.y, trailVel.z);
                } else {
                    level.addParticle((k == 0) ? GOLD_MOTE : WHITE_HOLY, px, py, pz,
                            trailVel.x, 0.018 + trailVel.y, trailVel.z);
                }
            }
            int starCount = isSwinging ? 2 : 1;
            for (int s = 0; s < starCount; s++) {
                double su = isSwinging ? (0.45 + rng.nextDouble() * 0.55) : (0.15 + rng.nextDouble() * 0.82);
                double sx = Mth.lerp(su, base.x, tip.x) + (rng.nextDouble() - 0.5) * 0.14;
                double sy = Mth.lerp(su, base.y, tip.y) + (rng.nextDouble() - 0.5) * 0.14;
                double sz = Mth.lerp(su, base.z, tip.z) + (rng.nextDouble() - 0.5) * 0.14;
                if (morgan) {
                    ExcaliburVfxRenderer.spawnSparkle(sx, sy, sz,
                            (float) trailVel.x, 0.012F + (float) trailVel.y, (float) trailVel.z,
                            0.105F + rng.nextFloat() * 0.060F, 255, 42, 54, tick, isSwinging ? 7.5F : 9.0F);
                } else {
                    ExcaliburVfxRenderer.spawnSparkle(sx, sy, sz,
                            (float) trailVel.x, 0.015F + (float) trailVel.y, (float) trailVel.z,
                            0.100F + rng.nextFloat() * 0.060F, 255, 226, 98, tick, isSwinging ? 7.5F : 9.0F);
                }
            }
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
                ExcaliburVfxRenderer.spawnSlashArc(p.playerId(), pos, p.yawDeg(), p.pitchDeg(), move, p.morgan(), tick);
            }
            case ExcaliburSyncPayload.EVENT_HIT_STOP -> {
                ExcaliburAnimManager.triggerHitStop(p.playerId(), p.durationTicks());
            }
            case ExcaliburSyncPayload.EVENT_ULT_START -> {
                ExcaliburAnimManager.triggerMove(p.playerId(), move, p.durationTicks());
                // Khi gồng RMB: cột ánh sáng vàng (Normal) hoặc cột ánh sáng tím (Morgan — khớp luồng chém tím từ trời!)
                ExcaliburVfxRenderer.spawnChargePillar(p.playerId(), p.morgan(), false, tick, 15.0F);
            }
            case ExcaliburSyncPayload.EVENT_BEAM_FIRE -> {
                ExcaliburVfxRenderer.stopChargePillar(p.playerId());
                ExcaliburVfxRenderer.spawnEnergyBeam(p.playerId(), pos, p.yawDeg(), p.pitchDeg(), p.rangeBlocks(), p.morgan(), tick);
            }
            case ExcaliburSyncPayload.EVENT_ENERGY_SYNC -> {
                SYNCED_ENERGY.put(p.playerId(), Mth.clamp(p.chargeProgress() * 100.0F, 0.0F, 100.0F));
            }
            case ExcaliburSyncPayload.EVENT_CHARGE_START -> {
                if (isLocal) {
                    localChanneling = true;
                }
                ExcaliburAnimManager.triggerMove(p.playerId(), ExcaliburMoveType.MORGAN_CHANNEL, p.durationTicks());
                ExcaliburVfxRenderer.spawnChargePillar(p.playerId(), true, true, tick, p.durationTicks());
            }
            case ExcaliburSyncPayload.EVENT_CHARGE_TICK -> {
                ExcaliburAnimManager.updateChannelProgress(p.playerId(), p.chargeProgress());
            }
            case ExcaliburSyncPayload.EVENT_CHARGE_CANCEL -> {
                if (isLocal) {
                    localChanneling = false;
                }
                ExcaliburAnimManager.cancelChannel(p.playerId());
                ExcaliburVfxRenderer.stopChargePillar(p.playerId());
            }
            case ExcaliburSyncPayload.EVENT_MORGAN_AWAKEN -> {
                if (isLocal) {
                    localChanneling = false;
                }
                ExcaliburVfxRenderer.stopChargePillar(p.playerId());
                ExcaliburAnimManager.triggerMove(p.playerId(), ExcaliburMoveType.MORGAN_AWAKEN, p.durationTicks());
                // Explosive burst of crimson-red 4-point sparkles on Morgan awakening!
                RandomSource rng = level.random;
                for (int i = 0; i < 26; i++) {
                    double ang = rng.nextDouble() * Mth.TWO_PI;
                    double spd = 0.08 + rng.nextDouble() * 0.18;
                    ExcaliburVfxRenderer.spawnSparkle(
                            pos.x, pos.y + 0.9 + rng.nextDouble() * 0.8, pos.z,
                            (float) (Math.cos(ang) * spd), 0.04F + rng.nextFloat() * 0.08F, (float) (Math.sin(ang) * spd),
                            0.19F + rng.nextFloat() * 0.12F, 255, 38, 48, tick, 14.0F);
                }
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0, pos.z, 0.0, 0.0, 0.0);
            }
            case ExcaliburSyncPayload.EVENT_MORGAN_REVERT -> {
                ExcaliburVfxRenderer.stopChargePillar(p.playerId());
                RandomSource rng = level.random;
                for (int i = 0; i < 16; i++) {
                    double ang = rng.nextDouble() * Mth.TWO_PI;
                    ExcaliburVfxRenderer.spawnSparkle(
                            pos.x + Math.cos(ang) * 0.8, pos.y + 0.9, pos.z + Math.sin(ang) * 0.8,
                            0.0F, 0.04F, 0.0F, 0.14F, 255, 224, 96, tick, 12.0F);
                }
            }
            default -> {
            }
        }
    }
}
