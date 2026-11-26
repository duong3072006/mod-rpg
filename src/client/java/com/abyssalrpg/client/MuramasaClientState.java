package com.abyssalrpg.client;

import com.abyssalrpg.client.anim.MuramasaAnimManager;
import com.abyssalrpg.client.renderer.vfx.MuramasaVfxRenderer;
import com.abyssalrpg.combat.MuramasaMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.item.MuramasaItem;
import com.abyssalrpg.network.MuramasaActionC2SPayload;
import com.abyssalrpg.network.MuramasaSyncPayload;
import com.abyssalrpg.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Cầu nối Client cho **Yêu Đao Muramasa Katana**:
 * - Hỗ trợ LMB chém gió / Hold-to-Slash 3 nhịp Katana mượt mà.
 * - Hỗ trợ phím `[R]` kích hoạt Huyết Thệ Yêu Đao (chia sẻ an toàn cùng keybind `[R]`).
 * - Sinh tia lửa cạnh lưỡi vàng (`#FFE820`) & huyết quang đỏ thẫm bám chuẩn tọa độ Katana!
 */
public final class MuramasaClientState {
    private static final DustParticleOptions YELLOW_SPARK =
            new DustParticleOptions(new Vector3f(1.00F, 0.94F, 0.18F), 0.72F);
    private static final DustParticleOptions CRIMSON_MIST =
            new DustParticleOptions(new Vector3f(0.86F, 0.08F, 0.14F), 0.88F);

    private static long lastAirSwingTick = -100L;
    private static int localPredictedStep = 0;
    private static boolean wasAttackDown = false;

    // Trạng thái hiệu ứng Mù Lòa Flash Trắng Toàn Màn Hình 10s (200 tick) khi chém Chuột Phải Muramasa!
    private static float flashMaxTicks = 200.0F;
    private static float prevFlashTicksLeft = 0.0F;
    private static float flashTicksLeft = 0.0F;

    private MuramasaClientState() {}

    public static void triggerWhiteFlash(float durationTicks) {
        float dur = Mth.clamp(durationTicks, 20.0F, 400.0F);
        flashMaxTicks = dur;
        flashTicksLeft = dur;
        prevFlashTicksLeft = dur;
    }

    public static void clearWhiteFlash() {
        flashTicksLeft = 0.0F;
        prevFlashTicksLeft = 0.0F;
    }

    /**
     * Độ đục lớp phủ Flash Trắng toàn màn hình [0..1] nội suy mượt theo từng frame (120–240 FPS):
     * - 0.0s .. 0.15s: bùng sáng tức thì lên 100% trắng xóa.
     * - 0.15s .. 3.0s: giữ mù lòa trắng đặc (~100% -> 95%).
     * - 3.0s .. 6.5s: màn sương trắng chói giảm dần (95% -> 48%).
     * - 6.5s .. 10.0s: tan mượt hoàn toàn về 0% (trả lại tầm nhìn trong trẻo).
     */
    public static float getWhiteFlashAlpha(float partialTick) {
        if (flashTicksLeft <= 0.001F && prevFlashTicksLeft <= 0.001F) {
            return 0.0F;
        }
        float rem = Math.max(0.0F, Mth.lerp(partialTick, prevFlashTicksLeft, flashTicksLeft));
        if (rem <= 0.001F) {
            return 0.0F;
        }
        float elapsedFrac = 1.0F - Mth.clamp(rem / Math.max(1.0F, flashMaxTicks), 0.0F, 1.0F);
        if (elapsedFrac < 0.015F) {
            return Mth.clamp(elapsedFrac / 0.015F, 0.25F, 1.0F);
        }
        if (elapsedFrac < 0.30F) {
            float u = (elapsedFrac - 0.015F) / 0.285F;
            return Mth.lerp(u, 1.00F, 0.95F);
        }
        if (elapsedFrac < 0.65F) {
            float u = (elapsedFrac - 0.30F) / 0.35F;
            return Mth.lerp(u * u * (3.0F - 2.0F * u), 0.95F, 0.48F);
        }
        float u = Mth.clamp((elapsedFrac - 0.65F) / 0.35F, 0.0F, 1.0F);
        float easeOut = 1.0F - u;
        return 0.48F * easeOut * easeOut;
    }

    /** Mức độ bùng chớp viền vàng-trắng lúc mở đầu flashbang (0..1.2s đầu). */
    public static float getWhiteFlashGlare(float partialTick) {
        if (flashTicksLeft <= 0.001F && prevFlashTicksLeft <= 0.001F) {
            return 0.0F;
        }
        float rem = Math.max(0.0F, Mth.lerp(partialTick, prevFlashTicksLeft, flashTicksLeft));
        float elapsedTicks = Math.max(0.0F, flashMaxTicks - rem);
        if (elapsedTicks >= 24.0F) {
            return 0.0F;
        }
        return 1.0F - (elapsedTicks / 24.0F);
    }

    public static void onClientAirSwing(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null
                || player.isSpectator() || player.isHandsBusy()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.MURAMASA)) {
            return;
        }
        MuramasaMoveType cur = MuramasaAnimManager.getMoveType(player.getId());
        if (MuramasaAnimManager.getActionProgress(player.getId(), 0.0F) > 0.001F && cur.lockedSkill) {
            return;
        }
        long tick = MuramasaAnimManager.getClientTick();
        if (tick - lastAirSwingTick < 6L) {
            return;
        }
        if (tick - lastAirSwingTick > 42L) {
            localPredictedStep = 0;
        }
        lastAirSwingTick = tick;

        MuramasaMoveType move = MuramasaMoveType.comboByStep(localPredictedStep);
        localPredictedStep = (localPredictedStep + 1) % 3;
        MuramasaAnimManager.triggerMove(player.getId(), move, move.baseDisplayTicks);
        MuramasaVfxRenderer.spawnSlashArc(player.getId(),
                player.position().add(0.0, player.getBbHeight() * 0.62, 0.0),
                player.getYRot(), player.getXRot(), move, MuramasaItem.isAwakened(stack), tick);
        ClientPlayNetworking.send(new MuramasaActionC2SPayload(MuramasaActionC2SPayload.ACTION_AIR_SLASH, -1));
    }

    public static void onBloodOathKeyPressed(LocalPlayer player) {
        if (!player.getMainHandItem().is(ModItems.MURAMASA) || player.isSpectator()) {
            return;
        }
        ClientPlayNetworking.send(new MuramasaActionC2SPayload(MuramasaActionC2SPayload.ACTION_BLOOD_OATH, -1));
    }

    public static void tickClient(Minecraft mc) {
        MuramasaAnimManager.tickClient(mc);
        long tick = MuramasaAnimManager.getClientTick();

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            clearWhiteFlash();
            wasAttackDown = false;
            return;
        }
        if (mc.isPaused()) {
            wasAttackDown = false;
            return;
        }
        if (!player.isAlive()) {
            clearWhiteFlash();
        } else {
            prevFlashTicksLeft = flashTicksLeft;
            if (flashTicksLeft > 0.0F) {
                flashTicksLeft = Math.max(0.0F, flashTicksLeft - 1.0F);
            }
        }

        if (WeaponsConfig.get().enableSlashVfx) {
            spawnBladeAuras(mc, level, tick);
        }

        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.MURAMASA) || mc.screen != null || player.isSpectator()) {
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

    private static void spawnBladeAuras(Minecraft mc, ClientLevel level, long tick) {
        RandomSource rng = level.random;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Player player : level.players()) {
            ItemStack main = player.getMainHandItem();
            if (!main.is(ModItems.MURAMASA) || player.isInvisible() || player.isSpectator()) {
                continue;
            }
            boolean awakened = MuramasaItem.isAwakened(main);
            float alpha = MuramasaAnimManager.getActionProgress(player.getId(), 0.0F);
            boolean swinging = alpha > 0.01F;
            if (player == mc.player && firstPerson && !swinging) {
                continue;
            }
            if (!swinging && (tick & 1L) != 0L) {
                continue;
            }
            float yawRad = (swinging ? player.getYRot() : player.yBodyRot) * Mth.DEG_TO_RAD;
            double fwdX = -Mth.sin(yawRad), fwdZ = Mth.cos(yawRad);
            double rightX = -Mth.cos(yawRad), rightZ = -Mth.sin(yawRad);

            Vec3 base, tip;
            if (player.isSprinting() && !swinging) {
                // Verified SimMuramasaPreview SPRINT anchor: base=(r=0.73, y=0.78, f=-0.40) -> tip=(r=1.24, y=0.46, f=-1.45)
                base = player.position().add(rightX * 0.73 - fwdX * 0.40, 0.78, rightZ * 0.73 - fwdZ * 0.40);
                tip = player.position().add(rightX * 1.24 - fwdX * 1.45, 0.46, rightZ * 1.24 - fwdZ * 1.45);
            } else {
                // Verified SimMuramasaPreview IDLE anchor: base=(r=0.67, y=0.74, f=0.30) -> tip=(r=1.08, y=0.45, f=1.40)
                base = player.position().add(rightX * 0.67 + fwdX * 0.30, 0.74, rightZ * 0.67 + fwdZ * 0.30);
                tip = player.position().add(rightX * 1.08 + fwdX * 1.40, 0.45, rightZ * 1.08 + fwdZ * 1.40);
            }

            if (awakened || (tick % 4L == 0L)) {
                double u = 0.20 + rng.nextDouble() * 0.78;
                double px = Mth.lerp(u, base.x, tip.x) + (rng.nextDouble() - 0.5) * 0.12;
                double py = Mth.lerp(u, base.y, tip.y) + (rng.nextDouble() - 0.5) * 0.12;
                double pz = Mth.lerp(u, base.z, tip.z) + (rng.nextDouble() - 0.5) * 0.12;
                level.addParticle(awakened ? CRIMSON_MIST : YELLOW_SPARK, px, py, pz, 0.0, 0.015, 0.0);
                if (awakened) {
                    MuramasaVfxRenderer.spawnSparkle(px, py, pz, 0.0F, 0.016F, 0.0F,
                            0.095F + rng.nextFloat() * 0.05F, 255, 224, 52, tick, 8.0F);
                }
            }
        }
    }

    public static void handleSyncPayload(ClientLevel level, MuramasaSyncPayload p) {
        long tick = MuramasaAnimManager.getClientTick();
        MuramasaMoveType move = MuramasaMoveType.byId(p.moveId());
        Vec3 pos = new Vec3(p.x(), p.y(), p.z());
        Minecraft mc = Minecraft.getInstance();
        boolean isLocal = mc.player != null && mc.player.getId() == p.playerId();

        switch (p.eventType()) {
            case MuramasaSyncPayload.EVENT_SLASH -> {
                if (!isLocal || MuramasaAnimManager.getActionProgress(p.playerId(), 0.0F) <= 0.01F) {
                    MuramasaAnimManager.triggerMove(p.playerId(), move, p.durationTicks());
                }
                MuramasaVfxRenderer.spawnSlashArc(p.playerId(), pos, p.yawDeg(), p.pitchDeg(), move, p.awakened(), tick);
            }
            case MuramasaSyncPayload.EVENT_IAI_START -> {
                MuramasaAnimManager.triggerMove(p.playerId(), MuramasaMoveType.IAIJUTSU_DRAW, p.durationTicks());
            }
            case MuramasaSyncPayload.EVENT_IAI_SLASH -> {
                MuramasaVfxRenderer.spawnIaiWave(pos, p.yawDeg(), p.rangeBlocks(), p.awakened(), tick);
                RandomSource rng = level.random;
                for (int i = 0; i < 16; i++) {
                    double ang = rng.nextDouble() * Mth.TWO_PI;
                    double spd = 0.08 + rng.nextDouble() * 0.16;
                    MuramasaVfxRenderer.spawnSparkle(pos.x, pos.y + 0.2, pos.z,
                            (float) (Math.cos(ang) * spd), 0.04F, (float) (Math.sin(ang) * spd),
                            0.14F, 255, (i % 2 == 0) ? 236 : 44, (i % 2 == 0) ? 46 : 52, tick, 11.0F);
                }
            }
            case MuramasaSyncPayload.EVENT_BLOOD_OATH_AWAKEN -> {
                MuramasaAnimManager.triggerMove(p.playerId(), MuramasaMoveType.BLOOD_OATH_AWAKEN, p.durationTicks());
                RandomSource rng = level.random;
                for (int i = 0; i < 24; i++) {
                    double ang = rng.nextDouble() * Mth.TWO_PI;
                    double spd = 0.09 + rng.nextDouble() * 0.18;
                    MuramasaVfxRenderer.spawnSparkle(pos.x, pos.y + 1.0, pos.z,
                            (float) (Math.cos(ang) * spd), 0.05F + rng.nextFloat() * 0.06F, (float) (Math.sin(ang) * spd),
                            0.17F, 255, (i % 3 == 0) ? 236 : 36, (i % 3 == 0) ? 44 : 46, tick, 13.0F);
                }
            }
            case MuramasaSyncPayload.EVENT_FLASH_BLIND -> {
                if (mc.player != null && mc.player.isAlive()) {
                    double maxR = Math.max(16.0, p.rangeBlocks());
                    boolean inRange = isLocal || mc.player.position().distanceToSqr(pos) <= maxR * maxR;
                    if (inRange) {
                        triggerWhiteFlash(p.durationTicks());
                    }
                }
            }
            default -> {}
        }
    }
}
