package com.abyssalrpg.client;

import com.abyssalrpg.client.anim.SeaWeaponsAnimManager;
import com.abyssalrpg.client.renderer.vfx.SeaWeaponsVfxRenderer;
import com.abyssalrpg.combat.SeaWeaponsMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.network.SeaWeaponsActionC2SPayload;
import com.abyssalrpg.network.SeaWeaponsSyncPayload;
import com.abyssalrpg.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cầu nối Client cho **Hải Thần Đinh Ba (Tay Phải) & Đèn Lồng Biển (Tay Trái)**:
 * - LMB chém gió / Hold-to-Slash 3 nhịp Đinh Ba.
 * - Phím `[R]` (hoặc `Shift + RMB`) triệu hồi **Xoáy Nước Đại Dương** từ Đinh Ba tay phải.
 * - `RMB` triệu hồi **Sóng Triều** từ Đèn Lồng tay trái.
 * - Sinh đốm sao biển xanh ngọc & hạt sương sáng bám quanh Đinh Ba và Đèn Lồng!
 */
public final class SeaWeaponsClientState {
    private static long lastAirSwingTick = -100L;
    private static int localPredictedStep = 0;
    private static boolean wasAttackDown = false;

    private SeaWeaponsClientState() {}

    public static void onClientAirSwing(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null
                || player.isSpectator() || player.isHandsBusy()) {
            return;
        }
        if (!player.getMainHandItem().is(ModItems.SEA_TRIDENT)) return;
        SeaWeaponsMoveType cur = SeaWeaponsAnimManager.getMoveType(player.getId());
        if (SeaWeaponsAnimManager.getActionProgress(player.getId(), 0.0F) > 0.001F && cur.lockedSkill) {
            return;
        }
        long tick = SeaWeaponsAnimManager.getClientTick();
        if (tick - lastAirSwingTick < 7L) return;
        if (tick - lastAirSwingTick > 44L) localPredictedStep = 0;
        lastAirSwingTick = tick;

        SeaWeaponsMoveType move = SeaWeaponsMoveType.comboByStep(localPredictedStep);
        localPredictedStep = (localPredictedStep + 1) % 3;
        SeaWeaponsAnimManager.triggerMove(player.getId(), move, move.baseDisplayTicks);
        SeaWeaponsVfxRenderer.spawnSlash(player.getId(),
                player.position().add(0.0, player.getBbHeight() * 0.60, 0.0),
                player.getYRot(), move, tick);
        ClientPlayNetworking.send(new SeaWeaponsActionC2SPayload(SeaWeaponsActionC2SPayload.ACTION_AIR_SLASH));
    }

    public static void onWhirlpoolKeyPressed(LocalPlayer player) {
        if (!player.getMainHandItem().is(ModItems.SEA_TRIDENT) || player.isSpectator()) return;
        ClientPlayNetworking.send(new SeaWeaponsActionC2SPayload(SeaWeaponsActionC2SPayload.ACTION_CAST_WHIRLPOOL));
    }

    public static void tickClient(Minecraft mc) {
        SeaWeaponsAnimManager.tickClient(mc);
        long tick = SeaWeaponsAnimManager.getClientTick();

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            wasAttackDown = false;
            return;
        }

        if (WeaponsConfig.get().enableSlashVfx && (tick % 3L) == 0L) {
            spawnAmbientOceanSparkles(mc, level, tick);
        }

        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.SEA_TRIDENT) || mc.screen != null || player.isSpectator()) {
            wasAttackDown = false;
            return;
        }

        boolean attackDown = mc.options.keyAttack.isDown();
        boolean justPressed = attackDown && !wasAttackDown;
        wasAttackDown = attackDown;
        HitResult hit = mc.hitResult;
        if (attackDown && !justPressed && WeaponsConfig.get().enableHoldToSlash
                && (hit == null || hit.getType() == HitResult.Type.MISS)) {
            onClientAirSwing(mc);
        }
    }

    private static void spawnAmbientOceanSparkles(Minecraft mc, ClientLevel level, long tick) {
        RandomSource rng = level.random;
        boolean fp = mc.options.getCameraType().isFirstPerson();
        for (Player p : level.players()) {
            if (!p.getMainHandItem().is(ModItems.SEA_TRIDENT) || p.isInvisible() || p.isSpectator()) continue;
            if (p == mc.player && fp) continue;

            float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
            double fwdX = -Mth.sin(yaw), fwdZ = Mth.cos(yaw);
            double rightX = -Mth.cos(yaw), rightZ = -Mth.sin(yaw);

            // Hạt sao nước 4 cánh thanh mảnh quanh mũi Đinh Ba hướng về trước bên phải
            double tx = p.getX() + rightX * 0.44 + fwdX * (0.48 + rng.nextDouble() * 0.48) + (rng.nextDouble() - 0.5) * 0.18;
            double ty = p.getY() + 0.84 + rng.nextDouble() * 0.34;
            double tz = p.getZ() + rightZ * 0.44 + fwdZ * (0.48 + rng.nextDouble() * 0.48) + (rng.nextDouble() - 0.5) * 0.18;
            SeaWeaponsVfxRenderer.spawnSparkle(tx, ty, tz, 0.0F, 0.015F, 0.0F,
                    0.068F + rng.nextFloat() * 0.032F, 128, 236, 255, tick, 9.5F);

            // Hạt ánh sáng lam dịu quanh Đèn Lồng (tay trái nếu ô phụ trống!)
            if (p.getOffhandItem().isEmpty()) {
                double lx = p.getX() - rightX * 0.44 + fwdX * 0.34 + (rng.nextDouble() - 0.5) * 0.18;
                double ly = p.getY() + 0.58 + rng.nextDouble() * 0.34;
                double lz = p.getZ() - rightZ * 0.44 + fwdZ * 0.34 + (rng.nextDouble() - 0.5) * 0.18;
                SeaWeaponsVfxRenderer.spawnSparkle(lx, ly, lz, 0.0F, 0.014F, 0.0F,
                        0.060F + rng.nextFloat() * 0.028F, 96, 214, 255, tick, 10.5F);
            }
        }
    }

    public static void handleSyncPayload(ClientLevel level, SeaWeaponsSyncPayload p) {
        long tick = SeaWeaponsAnimManager.getClientTick();
        SeaWeaponsMoveType move = SeaWeaponsMoveType.byId(p.moveId());
        Vec3 pos = new Vec3(p.x(), p.y(), p.z());
        Minecraft mc = Minecraft.getInstance();
        boolean isLocal = mc.player != null && mc.player.getId() == p.playerId();
        RandomSource rng = level.random;

        switch (p.eventType()) {
            case SeaWeaponsSyncPayload.EVENT_SLASH -> {
                if (!isLocal || SeaWeaponsAnimManager.getActionProgress(p.playerId(), 0.0F) <= 0.01F) {
                    SeaWeaponsAnimManager.triggerMove(p.playerId(), move, p.durationTicks());
                }
                SeaWeaponsVfxRenderer.spawnSlash(p.playerId(), pos, p.yawDeg(), move, tick);
            }
            case SeaWeaponsSyncPayload.EVENT_TIDAL_WAVE -> {
                SeaWeaponsAnimManager.triggerMove(p.playerId(), SeaWeaponsMoveType.LANTERN_TIDAL_WAVE, 22.0F);
                SeaWeaponsVfxRenderer.spawnTidalWave(pos, p.yawDeg(), p.param1(), p.param2(), tick, p.durationTicks());
                for (int i = 0; i < 28; i++) {
                    SeaWeaponsVfxRenderer.spawnSparkle(
                            pos.x + (rng.nextDouble() - 0.5) * 5.2,
                            pos.y + 0.6 + rng.nextDouble() * 3.8,
                            pos.z + (rng.nextDouble() - 0.5) * 5.2,
                            0.0F, 0.04F + rng.nextFloat() * 0.05F, 0.0F,
                            0.14F + rng.nextFloat() * 0.09F, 160, 246, 255, tick, 16.0F);
                }
            }
            case SeaWeaponsSyncPayload.EVENT_TRIDENT_VOLLEY -> {
                SeaWeaponsAnimManager.triggerMove(p.playerId(), SeaWeaponsMoveType.TRIDENT_WHIRLPOOL, 28.0F);
                int targetId = Math.round(p.param1());
                SeaWeaponsVfxRenderer.spawnTridentVolley(p.playerId(), pos, targetId, p.yawDeg(), p.durationTicks(), tick);
                Entity caster = level.getEntity(p.playerId());
                Vec3 origin = (caster != null) ? caster.position() : pos;
                for (int i = 0; i < 24; i++) {
                    double ang = rng.nextDouble() * Mth.TWO_PI;
                    double rad = 0.6 + rng.nextDouble() * 2.4;
                    SeaWeaponsVfxRenderer.spawnSparkle(
                            origin.x + Math.cos(ang) * rad, origin.y + 2.2 + rng.nextDouble() * 1.6, origin.z + Math.sin(ang) * rad,
                            0.0F, 0.045F + rng.nextFloat() * 0.04F, 0.0F,
                            0.12F + rng.nextFloat() * 0.07F, 130, 240, 255, tick, 15.0F);
                }
            }
            case SeaWeaponsSyncPayload.EVENT_WHIRLPOOL -> {
                SeaWeaponsAnimManager.triggerMove(p.playerId(), SeaWeaponsMoveType.TRIDENT_WHIRLPOOL, 20.0F);
                SeaWeaponsVfxRenderer.spawnWhirlpool(pos, p.param1(), p.param2(), tick, p.durationTicks());
                for (int i = 0; i < 20; i++) {
                    double ang = rng.nextDouble() * Mth.TWO_PI;
                    double rad = 1.2 + rng.nextDouble() * p.param1();
                    SeaWeaponsVfxRenderer.spawnSparkle(
                            pos.x + Math.cos(ang) * rad, pos.y + 0.4 + rng.nextDouble() * 2.5, pos.z + Math.sin(ang) * rad,
                            0.0F, 0.04F, 0.0F, 0.14F + rng.nextFloat() * 0.08F, 95, 224, 255, tick, 18.0F);
                }
            }
            case SeaWeaponsSyncPayload.EVENT_MAELSTROM_BURST -> {
                boolean supercharged = p.param2() > 0.5F;
                SeaWeaponsVfxRenderer.spawnMaelstromBurst(pos, p.param1(), supercharged, tick, p.durationTicks());
                for (int i = 0; i < (supercharged ? 32 : 18); i++) {
                    double ang = rng.nextDouble() * Mth.TWO_PI;
                    double spd = 0.08 + rng.nextDouble() * 0.22;
                    SeaWeaponsVfxRenderer.spawnSparkle(
                            pos.x, pos.y + 0.8 + rng.nextDouble() * 2.4, pos.z,
                            (float) (Math.cos(ang) * spd), 0.08F + rng.nextFloat() * 0.14F, (float) (Math.sin(ang) * spd),
                            0.18F + rng.nextFloat() * 0.12F, supercharged ? 220 : 120, 248, 255, tick, 16.0F);
                }
                level.addParticle(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.2, pos.z, 0.0, 0.0, 0.0);
            }
            case SeaWeaponsSyncPayload.EVENT_HIT_STOP -> SeaWeaponsAnimManager.triggerHitStop(p.playerId(), p.durationTicks());
            default -> {}
        }
    }
}
