package com.abyssalrpg.client;

import com.abyssalrpg.client.anim.DualBladeAnimManager;
import com.abyssalrpg.client.renderer.vfx.DualBladeTrailRenderer;
import com.abyssalrpg.combat.DualBladeCombatHandler;
import com.abyssalrpg.combat.DualBladeMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.network.DualBladeComboPayload;
import com.abyssalrpg.network.DualBladeDashVfxPayload;
import com.abyssalrpg.network.DualBladeSlashC2SPayload;
import com.abyssalrpg.network.DualBladeVfxPayload;
import com.abyssalrpg.registry.ModEffects;
import com.abyssalrpg.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Cầu nối Client cho hệ thống Song Đao Netherite–Kim Cương (Standard Right + Reverse Left Grip + Flash Dash 18 Block):
 * - Cập nhật `DualBladeAnimManager` (5 nhịp + Sprint Thrust + Air Slash + 3 pha Flash Dash 18 block).
 * - Điều khiển Hold-to-Slash chuẩn nhịp cooldown >= 95% (không cản đào block).
 * - Hạt khí Xanh Kim Cương (tay phải thuận) & Sương Netherite–Lam (tay trái ngược) siêu nhẹ ở F5 + hạt giọt máu Bleeding.
 */
public final class DualBladeClientState {
    private static final DustParticleOptions CRIMSON_WISP =
            new DustParticleOptions(new Vector3f(0.92F, 0.12F, 0.20F), 0.78F);
    private static final DustParticleOptions SILVER_ICE_WISP =
            new DustParticleOptions(new Vector3f(0.62F, 0.88F, 0.98F), 0.78F);
    private static final DustParticleOptions BLOOD_DARK =
            new DustParticleOptions(new Vector3f(0.66F, 0.06F, 0.09F), 0.88F);
    private static final DustParticleOptions CRIT_ICE_SILVER =
            new DustParticleOptions(new Vector3f(0.84F, 0.96F, 1.00F), 1.05F);

    private static long lastHoldPacketTick = -100L;
    private static long lastAirSwingTick = -100L;
    private static boolean wasAttackKeyDown = false;
    private static int localPredictedStep = 0;

    private DualBladeClientState() {}

    /**
     * Được gọi trực tiếp từ `MinecraftAttackMixin` tại `@At("HEAD")` của `Minecraft.startAttack()`
     * TRƯỚC khi vanilla reset `attackStrengthTicker` về 0 — đảm bảo mỗi cú click Chuột Trái chém gió
     * (không có quái) đều kích hoạt ngay animation 5 nhịp / Sprint Thrust / Air Slash + vệt chém Ribbon!
     */
    public static void onClientAirSwing(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null
                || player.isSpectator() || player.isHandsBusy()) {
            return;
        }
        if (!player.getMainHandItem().is(ModItems.DUAL_KNIFE)) {
            return;
        }
        if (DualBladeAnimManager.getMoveType(player.getId()).isFlashDashPhase()
                && DualBladeAnimManager.getAttackProgress(player.getId(), 0.0F) > 0.001F) {
            return;
        }

        long tick = DualBladeAnimManager.getClientTick();
        double actualSpd = Math.max(1.2, player.getAttributeValue(Attributes.ATTACK_SPEED));
        long minAirIntervalTicks = Math.max(5L, Math.round(20.0 / actualSpd) - 2L);
        if (tick - lastAirSwingTick < minAirIntervalTicks) {
            return;
        }

        lastAirSwingTick = tick;
        lastHoldPacketTick = tick;
        boolean offhandOccupied = !player.getOffhandItem().isEmpty();
        DualBladeMoveType move = DualBladeCombatHandler.resolveMoveType(player, localPredictedStep, offhandOccupied);
        float dur = DualBladeCombatHandler.computeComboDurationTicks(actualSpd, move);

        DualBladeAnimManager.triggerLocalOptimistic(player, move, dur, (float) (actualSpd / 2.5));
        advancePredictedStep(move, offhandOccupied);
        ClientPlayNetworking.send(new DualBladeSlashC2SPayload(-1));
    }

    public static void tickClient(Minecraft mc) {
        DualBladeAnimManager.tickClient(mc);
        long tick = DualBladeAnimManager.getClientTick();

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            wasAttackKeyDown = false;
            return;
        }

        // 1. Hạt giọt máu siêu nhẹ cho mục tiêu dính Bleeding (1–2 hạt mỗi 9 ticks, thuần Client)
        if (WeaponsConfig.get().enableBleedParticles && (tick % 9L) == 0L) {
            spawnNearbyBleedDroplets(level, player);
        }

        // 2. Luồng khí Đỏ Huyết (tay phải) & Vàng Kim (tay trái) siêu nhẹ ở F5 / Multiplayer
        if (WeaponsConfig.get().enableSlashVfx && (tick % 5L) == 0L) {
            spawnIdleBladeWisps(mc, level, player);
        }

        // 3. Cơ chế Hold-to-Slash & click lẻ đồng bộ 5 nhịp + Sprint/Air
        if (!player.getMainHandItem().is(ModItems.DUAL_KNIFE) || mc.screen != null || player.isSpectator()) {
            wasAttackKeyDown = false;
            return;
        }

        // Không chen nhịp đánh thường khi đang trong 3 pha chiêu Chuột Phải Flash Dash
        if (DualBladeAnimManager.getMoveType(player.getId()).isFlashDashPhase()
                && DualBladeAnimManager.getAttackProgress(player.getId(), 0.0F) > 0.001F) {
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

        boolean offhandOccupied = !player.getOffhandItem().isEmpty();
        double actualSpd = Math.max(1.2, player.getAttributeValue(Attributes.ATTACK_SPEED));

        if (justPressed && hit instanceof EntityHitResult) {
            DualBladeMoveType move = DualBladeCombatHandler.resolveMoveType(player, localPredictedStep, offhandOccupied);
            float dur = DualBladeCombatHandler.computeComboDurationTicks(actualSpd, move);
            DualBladeAnimManager.triggerLocalOptimistic(player, move, dur, (float) (actualSpd / 2.5));
            advancePredictedStep(move, offhandOccupied);
            return;
        }

        if (!justPressed && !WeaponsConfig.get().enableHoldToSlash) {
            return;
        }
        if (player.getAttackStrengthScale(0.0F) < 0.95F) {
            return;
        }
        if (tick - lastHoldPacketTick < 3L) {
            return;
        }

        lastHoldPacketTick = tick;
        int targetId = -1;
        if (hit instanceof EntityHitResult ehr && ehr.getEntity().isAlive() && ehr.getEntity().isAttackable()) {
            targetId = ehr.getEntity().getId();
        } else {
            lastAirSwingTick = tick;
        }

        DualBladeMoveType move = DualBladeCombatHandler.resolveMoveType(player, localPredictedStep, offhandOccupied);
        float dur = DualBladeCombatHandler.computeComboDurationTicks(actualSpd, move);
        DualBladeAnimManager.triggerLocalOptimistic(player, move, dur, (float) (actualSpd / 2.5));
        advancePredictedStep(move, offhandOccupied);

        player.swing(InteractionHand.MAIN_HAND);
        player.resetAttackStrengthTicker();
        ClientPlayNetworking.send(new DualBladeSlashC2SPayload(targetId));
    }

    private static void advancePredictedStep(DualBladeMoveType move, boolean offhandOccupied) {
        if (offhandOccupied) {
            localPredictedStep = 0;
        } else if (move == DualBladeMoveType.SPRINT_THRUST || move == DualBladeMoveType.AIR_SLASH) {
            localPredictedStep = 1;
        } else {
            localPredictedStep = (localPredictedStep + 1) % DualBladeMoveType.MAIN_COMBO_COUNT;
        }
    }

    public static void handleComboPayload(ClientLevel level, DualBladeComboPayload payload) {
        DualBladeAnimManager.onComboPacket(level, payload);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == payload.playerId()) {
            DualBladeMoveType m = payload.moveType();
            if (m == DualBladeMoveType.SPRINT_THRUST || m == DualBladeMoveType.AIR_SLASH) {
                localPredictedStep = 1;
            } else if (m.id < DualBladeMoveType.MAIN_COMBO_COUNT) {
                localPredictedStep = (m.id + 1) % DualBladeMoveType.MAIN_COMBO_COUNT;
            }
        }
    }

    public static void handleVfxPayload(ClientLevel level, DualBladeVfxPayload p) {
        long tick = DualBladeAnimManager.getClientTick();
        if (p.kind() == DualBladeVfxPayload.KIND_CRIT_STRIKE) {
            Vec3 pos = new Vec3(p.x(), p.y(), p.z());
            DualBladeTrailRenderer.spawnCritBurst(pos, tick);
            spawnCritParticles(level, pos);
        }
    }

    public static void handleDashVfxPayload(ClientLevel level, DualBladeDashVfxPayload p) {
        DualBladeTrailRenderer.spawnFlashDashTrailAndFlurry(p, DualBladeAnimManager.getClientTick());
    }

    private static void spawnIdleBladeWisps(Minecraft mc, ClientLevel level, LocalPlayer localPlayer) {
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        AABB box = localPlayer.getBoundingBox().inflate(24.0);
        RandomSource rng = level.random;
        for (Player p : level.getEntitiesOfClass(Player.class, box,
                pl -> pl.isAlive() && pl.getMainHandItem().is(ModItems.DUAL_KNIFE) && pl.getOffhandItem().isEmpty())) {
            if (p == localPlayer && firstPerson) {
                continue;
            }
            float yawRad = p.yBodyRot * Mth.DEG_TO_RAD;
            double rightX = -Math.cos(yawRad);
            double rightZ = -Math.sin(yawRad);
            boolean rightHandSide = rng.nextBoolean();
            double side = rightHandSide ? 1.0 : -1.0;
            double bx = p.getX() + rightX * (0.64 * side) + (rng.nextDouble() - 0.5) * 0.22;
            double by = p.getY() + 0.80 + rng.nextDouble() * 0.58;
            double bz = p.getZ() + rightZ * (0.64 * side) + (rng.nextDouble() - 0.5) * 0.22;

            // Tay phải tỏa khí Đỏ Huyết, tay trái tỏa ánh sương Bạc–Lam Lạnh!
            level.addParticle(rightHandSide ? CRIMSON_WISP : SILVER_ICE_WISP, bx, by, bz,
                    (rng.nextDouble() - 0.5) * 0.014, 0.020 + rng.nextDouble() * 0.016, (rng.nextDouble() - 0.5) * 0.014);
        }
    }

    private static void spawnNearbyBleedDroplets(ClientLevel level, LocalPlayer player) {
        AABB box = player.getBoundingBox().inflate(24.0);
        RandomSource rng = level.random;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                ent -> ent.isAlive() && ent.hasEffect(ModEffects.BLEEDING))) {
            int count = 1 + (rng.nextInt(3) == 0 ? 1 : 0);
            double w = Math.min(2.2, Math.max(0.45, e.getBbWidth() * 0.55));
            double h = Math.min(3.5, Math.max(0.6, e.getBbHeight() * 0.75));
            for (int i = 0; i < count; i++) {
                double px = e.getX() + (rng.nextDouble() - 0.5) * w;
                double py = e.getY() + 0.25 + rng.nextDouble() * h;
                double pz = e.getZ() + (rng.nextDouble() - 0.5) * w;
                level.addParticle(BLOOD_DARK, px, py, pz, 0.0, -0.05, 0.0);
            }
        }
    }

    private static void spawnCritParticles(ClientLevel level, Vec3 pos) {
        if (!WeaponsConfig.get().enableSlashVfx) {
            return;
        }
        RandomSource rng = level.random;
        for (int i = 0; i < 6; i++) {
            double ang = (Math.PI * 2.0 * i) / 6.0;
            DustParticleOptions dust = (i % 2 == 0) ? CRIT_ICE_SILVER : CRIMSON_WISP;
            level.addParticle(dust,
                    pos.x + Math.cos(ang) * 0.35,
                    pos.y + (rng.nextDouble() - 0.5) * 0.30,
                    pos.z + Math.sin(ang) * 0.35,
                    Math.cos(ang) * 0.12, 0.08 + rng.nextDouble() * 0.05, Math.sin(ang) * 0.12);
        }
        level.addParticle(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 0.0, 0.12, 0.0);
    }
}
