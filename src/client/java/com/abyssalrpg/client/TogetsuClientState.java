package com.abyssalrpg.client;

import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.renderer.vfx.TogetsuVfxRenderer;
import com.abyssalrpg.combat.TogetsuMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.item.TogetsuItem;
import com.abyssalrpg.network.TogetsuActionC2SPayload;
import com.abyssalrpg.network.TogetsuSyncPayload;
import com.abyssalrpg.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Cầu nối Client cho **Togetsu (Trăng Non & Trăng Khuyết / Hợp Nhất Song Nguyệt — phím `R`)**:
 * - Đăng ký phím tắt **`R`** (`key.abyssalrpg.togetsu_fuse`) để kích hoạt animation & hợp nhất/tách Song Nguyệt.
 * - Hỗ trợ chém gió mượt (qua `MinecraftAttackMixin`), Hold-to-Slash theo tốc đánh 2 dạng, và nhận gói đồng bộ VFX/Crescent Vorpal.
 */
public final class TogetsuClientState {
    private static final DustParticleOptions WAXING_WHITE =
            new DustParticleOptions(new Vector3f(0.95F, 0.98F, 1.00F), 0.82F);
    private static final DustParticleOptions WANING_OBSIDIAN =
            new DustParticleOptions(new Vector3f(0.14F, 0.16F, 0.24F), 0.86F);
    private static final DustParticleOptions LUNAR_CYAN =
            new DustParticleOptions(new Vector3f(0.55F, 0.90F, 1.00F), 1.00F);

    private static KeyMapping fuseKey;
    private static long lastAirSwingTick = -100L;
    private static long lastHoldPacketTick = -100L;
    private static boolean wasAttackKeyDown = false;
    private static int localPredictedStep = 0;

    private TogetsuClientState() {}

    public static void initKeybinds() {
        fuseKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.abyssalrpg.togetsu_fuse",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "key.categories.abyssalrpg"
        ));
    }

    public static String getFuseKeyDisplayName() {
        return fuseKey != null ? fuseKey.getTranslatedKeyMessage().getString() : "R";
    }

    public static void onClientAirSwing(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null
                || player.isSpectator() || player.isHandsBusy()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.TOGETSU)) {
            return;
        }
        TogetsuMoveType cur = TogetsuAnimManager.getMoveType(player.getId());
        if (cur.isLockedSkill() && TogetsuAnimManager.getActionProgress(player.getId(), 0.0F) > 0.001F) {
            return;
        }

        long tick = TogetsuAnimManager.getClientTick();
        double actualSpd = Math.max(1.0, player.getAttributeValue(Attributes.ATTACK_SPEED));
        long minInterval = Math.max(5L, Math.round(20.0 / actualSpd) - 2L);
        if (tick - lastAirSwingTick < minInterval) {
            return;
        }
        lastAirSwingTick = tick;
        lastHoldPacketTick = tick;

        boolean fused = TogetsuItem.isFused(stack);
        TogetsuMoveType move = fused
                ? TogetsuMoveType.fusedByStep(localPredictedStep)
                : TogetsuMoveType.dualByStep(localPredictedStep);
        TogetsuAnimManager.triggerLocalOptimistic(player, move);
        localPredictedStep = (localPredictedStep + 1) % (fused ? TogetsuMoveType.FUSED_COMBO_COUNT : TogetsuMoveType.DUAL_COMBO_COUNT);
        ClientPlayNetworking.send(new TogetsuActionC2SPayload(TogetsuActionC2SPayload.ACTION_AIR_SLASH, -1));
    }

    public static void tickClient(Minecraft mc) {
        TogetsuAnimManager.tickClient(mc);
        long tick = TogetsuAnimManager.getClientTick();

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            wasAttackKeyDown = false;
            return;
        }

        // 1. Xử lý phím [R] Hợp Nhất / Tách Song Nguyệt!
        while (fuseKey != null && fuseKey.consumeClick()) {
            if (mc.screen == null && !player.isSpectator() && player.getMainHandItem().is(ModItems.TOGETSU)) {
                ClientPlayNetworking.send(new TogetsuActionC2SPayload(TogetsuActionC2SPayload.ACTION_TOGGLE_FUSE, -1));
            }
        }

        // 2. Hạt ánh trăng trắng–đen siêu nhẹ quanh người cầm Togetsu ở F5 / Multiplayer
        if (WeaponsConfig.get().enableSlashVfx && (tick % 6L) == 0L) {
            spawnIdleLunarWisps(mc, level, player);
        }

        // 3. Hold-to-Slash & chém quái đồng bộ cho Togetsu
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.TOGETSU) || mc.screen != null || player.isSpectator()) {
            wasAttackKeyDown = false;
            return;
        }

        TogetsuMoveType cur = TogetsuAnimManager.getMoveType(player.getId());
        if (cur.isLockedSkill() && TogetsuAnimManager.getActionProgress(player.getId(), 0.0F) > 0.001F) {
            wasAttackKeyDown = mc.options.keyAttack.isDown();
            return;
        }

        boolean attackDown = mc.options.keyAttack.isDown();
        boolean justPressed = attackDown && !wasAttackKeyDown;
        wasAttackKeyDown = attackDown;
        if (!attackDown) {
            return;
        }

        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return;
        }

        boolean fused = TogetsuItem.isFused(stack);
        if (justPressed && hit instanceof EntityHitResult) {
            TogetsuMoveType move = fused
                    ? TogetsuMoveType.fusedByStep(localPredictedStep)
                    : TogetsuMoveType.dualByStep(localPredictedStep);
            TogetsuAnimManager.triggerLocalOptimistic(player, move);
            localPredictedStep = (localPredictedStep + 1) % (fused ? TogetsuMoveType.FUSED_COMBO_COUNT : TogetsuMoveType.DUAL_COMBO_COUNT);
            return;
        }

        if (!justPressed && !WeaponsConfig.get().enableHoldToSlash) {
            return;
        }
        if (player.getAttackStrengthScale(0.0F) < 0.95F || (tick - lastHoldPacketTick) < 4L) {
            return;
        }

        lastHoldPacketTick = tick;
        int targetId = -1;
        if (hit instanceof EntityHitResult ehr && ehr.getEntity().isAlive() && ehr.getEntity().isAttackable()) {
            targetId = ehr.getEntity().getId();
        } else {
            lastAirSwingTick = tick;
        }

        TogetsuMoveType move = fused
                ? TogetsuMoveType.fusedByStep(localPredictedStep)
                : TogetsuMoveType.dualByStep(localPredictedStep);
        TogetsuAnimManager.triggerLocalOptimistic(player, move);
        localPredictedStep = (localPredictedStep + 1) % (fused ? TogetsuMoveType.FUSED_COMBO_COUNT : TogetsuMoveType.DUAL_COMBO_COUNT);

        player.swing(InteractionHand.MAIN_HAND);
        player.resetAttackStrengthTicker();
        ClientPlayNetworking.send(new TogetsuActionC2SPayload(TogetsuActionC2SPayload.ACTION_AIR_SLASH, targetId));
    }

    public static void handleSyncPayload(ClientLevel level, TogetsuSyncPayload p) {
        long tick = TogetsuAnimManager.getClientTick();
        TogetsuAnimManager.onSyncPacket(level, p);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == p.playerId()) {
            TogetsuMoveType m = TogetsuMoveType.byId(p.moveId());
            if (m.id >= TogetsuMoveType.DUAL_1.id && m.id <= TogetsuMoveType.DUAL_4.id) {
                localPredictedStep = (m.id + 1) % TogetsuMoveType.DUAL_COMBO_COUNT;
            } else if (m.id >= TogetsuMoveType.FUSED_1.id && m.id <= TogetsuMoveType.FUSED_3.id) {
                localPredictedStep = ((m.id - TogetsuMoveType.FUSED_1.id) + 1) % TogetsuMoveType.FUSED_COMBO_COUNT;
            } else if (m == TogetsuMoveType.FUSE_TRANSFORM || m == TogetsuMoveType.UNFUSE_SPLIT) {
                localPredictedStep = 0;
            }
        }

        Vec3 pos = new Vec3(p.x(), p.y(), p.z());
        switch (p.vfxKind()) {
            case TogetsuSyncPayload.VFX_WAXING_CRIT -> {
                TogetsuVfxRenderer.spawnLunarCritBurst(pos, true, tick);
                spawnLunarParticles(level, pos, true);
            }
            case TogetsuSyncPayload.VFX_WANING_CRIT -> {
                TogetsuVfxRenderer.spawnLunarCritBurst(pos, false, tick);
                spawnLunarParticles(level, pos, false);
            }
            case TogetsuSyncPayload.VFX_FUSE_BURST -> {
                TogetsuVfxRenderer.spawnFusionShockwave(pos, true, tick);
                spawnFusionBurstParticles(level, pos, true);
            }
            case TogetsuSyncPayload.VFX_UNFUSE_BURST -> {
                TogetsuVfxRenderer.spawnFusionShockwave(pos, false, tick);
                spawnFusionBurstParticles(level, pos, false);
            }
            case TogetsuSyncPayload.VFX_CRESCENT_VORPAL_WAVE -> {
                TogetsuVfxRenderer.spawnCrescentVorpalWave(pos, p.yawDeg(), p.pitchDeg(), p.powerScale(), tick);
                spawnFusionBurstParticles(level, pos, true);
            }
            case TogetsuSyncPayload.VFX_PIN_IMPACT -> {
                TogetsuVfxRenderer.spawnFusionShockwave(pos, false, tick);
                spawnFusionBurstParticles(level, pos, false);
            }
            case TogetsuSyncPayload.VFX_PIN_STAB_WAXING -> {
                TogetsuVfxRenderer.spawnPinStabFlash(pos, p.yawDeg(), true, Math.round(p.powerScale() * 12.0F), tick);
                spawnLunarParticles(level, pos, true);
            }
            case TogetsuSyncPayload.VFX_PIN_STAB_WANING -> {
                TogetsuVfxRenderer.spawnPinStabFlash(pos, p.yawDeg(), false, Math.round(p.powerScale() * 12.0F), tick);
                spawnLunarParticles(level, pos, false);
            }
            case TogetsuSyncPayload.VFX_VORPAL_CHARGE_PULSE -> {
                TogetsuVfxRenderer.spawnVorpalChargeImplosion(pos, tick);
                spawnVorpalChargeParticles(level, pos, p.powerScale());
            }
            default -> {
            }
        }
    }

    private static void spawnVorpalChargeParticles(ClientLevel level, Vec3 pos, float frac) {
        int count = 14 + Math.round(frac * 14.0F);
        double rad = 2.6 - frac * 0.8;
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2.0 * i) / count;
            double px = pos.x + Math.cos(ang) * rad;
            double py = pos.y + 0.1 + (i % 4) * 0.42;
            double pz = pos.z + Math.sin(ang) * rad;
            // Hút xoáy ngược vào trung tâm người chơi đang gồng lực!
            double vx = -Math.cos(ang) * 0.24;
            double vz = -Math.sin(ang) * 0.24;
            level.addParticle((i % 3 == 0) ? ParticleTypes.ELECTRIC_SPARK
                    : ((i % 2 == 0) ? ParticleTypes.END_ROD : ParticleTypes.WITCH),
                    px, py, pz, vx, 0.06, vz);
        }
    }

    private static void spawnIdleLunarWisps(Minecraft mc, ClientLevel level, LocalPlayer localPlayer) {
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        AABB box = localPlayer.getBoundingBox().inflate(24.0);
        RandomSource rng = level.random;
        for (Player p : level.getEntitiesOfClass(Player.class, box,
                pl -> pl.isAlive() && pl.getMainHandItem().is(ModItems.TOGETSU) && pl.getOffhandItem().isEmpty())) {
            if (p == localPlayer && firstPerson) {
                continue;
            }
            ItemStack st = p.getMainHandItem();
            boolean fused = TogetsuItem.isFused(st);
            boolean ready = TogetsuItem.getFusionGauge(st) >= 99.5F;
            float yawRad = p.yBodyRot * Mth.DEG_TO_RAD;
            double rightX = -Math.cos(yawRad);
            double rightZ = -Math.sin(yawRad);

            if (fused) {
                // Khí thế Climax Boost bốc lên quanh nhân vật ở dạng Hợp Nhất ngạo nghễ!
                for (int k = 0; k < 2; k++) {
                    double ang = (level.getGameTime() * 0.28) + k * Math.PI + rng.nextDouble() * 0.7;
                    double rad = 0.68 + rng.nextDouble() * 0.42;
                    level.addParticle((k == 0) ? WAXING_WHITE : LUNAR_CYAN,
                            p.getX() + Math.cos(ang) * rad, p.getY() + 0.15 + rng.nextDouble() * 1.45, p.getZ() + Math.sin(ang) * rad,
                            0.0, 0.065 + rng.nextDouble() * 0.04, 0.0);
                }
            } else {
                boolean leftWhite = rng.nextBoolean();
                double side = leftWhite ? -1.0 : 1.0;
                double bx = p.getX() + rightX * (0.62 * side) + (rng.nextDouble() - 0.5) * 0.18;
                double by = p.getY() + 0.82 + rng.nextDouble() * 0.55;
                double bz = p.getZ() + rightZ * (0.62 * side) + (rng.nextDouble() - 0.5) * 0.18;
                level.addParticle(leftWhite ? WAXING_WHITE : WANING_OBSIDIAN, bx, by, bz,
                        0.0, 0.02, 0.0);
                if (ready && rng.nextInt(3) == 0) {
                    level.addParticle(LUNAR_CYAN, p.getX(), p.getY() + 1.15, p.getZ(),
                            (rng.nextDouble() - 0.5) * 0.04, 0.03, (rng.nextDouble() - 0.5) * 0.04);
                }
            }
        }
    }

    private static void spawnLunarParticles(ClientLevel level, Vec3 pos, boolean waxing) {
        RandomSource rng = level.random;
        DustParticleOptions dust = waxing ? WAXING_WHITE : WANING_OBSIDIAN;
        for (int i = 0; i < 8; i++) {
            double ang = (Math.PI * 2.0 * i) / 8.0;
            level.addParticle(i % 3 == 0 ? LUNAR_CYAN : dust,
                    pos.x + Math.cos(ang) * 0.42, pos.y + (rng.nextDouble() - 0.5) * 0.35, pos.z + Math.sin(ang) * 0.42,
                    Math.cos(ang) * 0.14, 0.08, Math.sin(ang) * 0.14);
        }
        level.addParticle(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 0.0, 0.14, 0.0);
    }

    private static void spawnFusionBurstParticles(ClientLevel level, Vec3 pos, boolean fuse) {
        int count = fuse ? 28 : 14;
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2.0 * i) / count;
            double r = fuse ? 1.05 : 0.65;
            if (fuse) {
                // Vòng tròn hạt sáng trắng - tím (end_rod và witch) bung ra quanh người chơi khi Twin Moon Ignition!
                level.addParticle((i % 2 == 0) ? ParticleTypes.END_ROD : ParticleTypes.WITCH,
                        pos.x + Math.cos(ang) * r, pos.y + 0.15 + (i % 3) * 0.28, pos.z + Math.sin(ang) * r,
                        Math.cos(ang) * 0.22, 0.08 + (i % 2) * 0.05, Math.sin(ang) * 0.22);
            } else {
                DustParticleOptions d = (i % 3 == 0) ? LUNAR_CYAN : (i % 2 == 0 ? WAXING_WHITE : WANING_OBSIDIAN);
                level.addParticle(d,
                        pos.x + Math.cos(ang) * r, pos.y + 0.15 + (i % 3) * 0.25, pos.z + Math.sin(ang) * r,
                        Math.cos(ang) * 0.12, 0.08, Math.sin(ang) * 0.12);
            }
        }
    }
}
