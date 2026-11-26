package com.abyssalrpg.client;

import com.abyssalrpg.client.anim.TogetsuAnimManager;
import com.abyssalrpg.client.gui.TogetsuColorScreen;
import com.abyssalrpg.client.renderer.vfx.TogetsuVfxRenderer;
import com.abyssalrpg.combat.TogetsuCombatHandler;
import com.abyssalrpg.combat.TogetsuMoveType;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.item.TogetsuGlowStyle;
import com.abyssalrpg.item.TogetsuItem;
import com.abyssalrpg.network.TogetsuActionC2SPayload;
import com.abyssalrpg.network.TogetsuColorC2SPayload;
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
    private static final DustParticleOptions SPRINT_WIND_WHITE =
            new DustParticleOptions(new Vector3f(0.96F, 0.98F, 1.00F), 0.58F);
    private static final DustParticleOptions SPRINT_WIND_BLACK =
            new DustParticleOptions(new Vector3f(0.10F, 0.12F, 0.18F), 0.62F);
    private static final DustParticleOptions LUNAR_CYAN =
            new DustParticleOptions(new Vector3f(0.55F, 0.90F, 1.00F), 1.00F);

    private static KeyMapping fuseKey;
    private static KeyMapping skillKey;
    private static KeyMapping colorKey;
    private static long lastAirSwingTick = -100L;
    private static long lastHoldPacketTick = -100L;
    private static long lastRightSlashTick = -100L;
    private static long lastAnyDualSlashTick = -100L;
    private static boolean wasAttackKeyDown = false;
    private static boolean wasUseKeyDown = false;
    private static int localPredictedStep = 0;
    private static int localLeftVariant = 0;
    private static int localRightVariant = 0;

    private TogetsuClientState() {}

    public static void initKeybinds() {
        fuseKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.abyssalrpg.togetsu_fuse",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "key.categories.abyssalrpg"
        ));
        skillKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.abyssalrpg.togetsu_skill",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_4,
                "key.categories.abyssalrpg"
        ));
        colorKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.abyssalrpg.togetsu_color",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                "key.categories.abyssalrpg"
        ));
    }

    public static String getFuseKeyDisplayName() {
        return fuseKey != null ? fuseKey.getTranslatedKeyMessage().getString() : "R";
    }

    public static String getSkillKeyDisplayName() {
        return skillKey != null ? skillKey.getTranslatedKeyMessage().getString() : "Button 4";
    }

    public static String getColorKeyDisplayName() {
        return colorKey != null ? colorKey.getTranslatedKeyMessage().getString() : "C";
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
        float curAlpha = TogetsuAnimManager.getActionProgress(player.getId(), 0.0F);
        if (curAlpha > 0.001F && (cur.isLockedSkill() || (cur == TogetsuMoveType.DUAL_5 && curAlpha < 0.84F))) {
            return;
        }

        boolean fused = TogetsuItem.isFused(stack);
        long tick = TogetsuAnimManager.getClientTick();
        if (!fused && tick - lastAnyDualSlashTick > 48L) {
            localPredictedStep = 0;
        }
        double actualSpd = Math.max(1.0, player.getAttributeValue(Attributes.ATTACK_SPEED));
        // Song Nguyệt: khóa chính xác 6 ticks (0.30 giây) mới chém được 1 nhát!
        long minInterval = fused ? 6L : Math.max(5L, Math.round(20.0 / actualSpd) - 2L);
        if (tick - lastAirSwingTick < minInterval) {
            return;
        }
        lastAirSwingTick = tick;
        lastHoldPacketTick = tick;
        lastAnyDualSlashTick = tick;

        // Chuột Trái ở dạng Song Đao = Kiếm Trái (Trăng Non — Lưỡi Trắng!)
        TogetsuMoveType move = fused
                ? TogetsuMoveType.fusedByStep(localPredictedStep)
                : TogetsuMoveType.resolveDualHandMove(false, player.onGround(), player.isInWater(),
                        player.fallDistance, player.isSprinting(), localPredictedStep, localLeftVariant);
        float dur = fused ? move.baseDisplayTicks : TogetsuCombatHandler.computeDualComboDurationTicks(actualSpd, move);
        TogetsuAnimManager.triggerLocalOptimistic(player, move, dur);
        if (!fused) {
            localLeftVariant = (localLeftVariant + 1) & 1;
        }
        advanceLocalStep(move, fused);
        ClientPlayNetworking.send(new TogetsuActionC2SPayload(TogetsuActionC2SPayload.ACTION_AIR_SLASH, -1));
    }

    private static void advanceLocalStep(TogetsuMoveType move, boolean fused) {
        if (!fused && (move == TogetsuMoveType.DUAL_SPRINT_THRUST || move == TogetsuMoveType.DUAL_AIR_SLASH)) {
            localPredictedStep = 1;
        } else {
            localPredictedStep = (localPredictedStep + 1) % (fused ? TogetsuMoveType.FUSED_COMBO_COUNT : TogetsuMoveType.DUAL_COMBO_COUNT);
        }
    }

    public static void tickClient(Minecraft mc) {
        TogetsuAnimManager.tickClient(mc);
        long tick = TogetsuAnimManager.getClientTick();

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            wasAttackKeyDown = false;
            wasUseKeyDown = false;
            return;
        }

        // 1. Xử lý phím [R] Hợp Nhất / Tách Song Nguyệt (Togetsu) hoặc Gồng 4.0s Dark Mana (Excalibur Morgan)!
        while (fuseKey != null && fuseKey.consumeClick()) {
            if (mc.screen == null && !player.isSpectator()) {
                if (player.getMainHandItem().is(ModItems.TOGETSU)) {
                    ClientPlayNetworking.send(new TogetsuActionC2SPayload(TogetsuActionC2SPayload.ACTION_TOGGLE_FUSE, -1));
                } else if (player.getMainHandItem().is(ModItems.EXCALIBUR)) {
                    ExcaliburClientState.onTransformKeyPressed(player);
                } else if (player.getMainHandItem().is(ModItems.SEA_TRIDENT)) {
                    SeaWeaponsClientState.onWhirlpoolKeyPressed(player);
                } else if (player.getMainHandItem().is(ModItems.MURAMASA)) {
                    MuramasaClientState.onBloodOathKeyPressed(player);
                }
            }
        }

        // 1b. Xử lý phím [V] Kỹ Năng Đặc Biệt (Song Đao: Lao Tới Địch 15 block / Shift+V: Crescent Wave | Song Nguyệt: Vorpal / Shift+V: Wave)!
        while (skillKey != null && skillKey.consumeClick()) {
            if (mc.screen == null && !player.isSpectator() && player.getMainHandItem().is(ModItems.TOGETSU)) {
                ClientPlayNetworking.send(new TogetsuActionC2SPayload(
                        TogetsuActionC2SPayload.ACTION_CAST_SKILL,
                        player.isShiftKeyDown() ? 1 : 0));
            }
        }

        // 1c. Xử lý phím [C] Mở bảng chọn & tự pha màu điểm sáng Togetsu (Shift + [C] đổi nhanh preset kế tiếp!)
        while (colorKey != null && colorKey.consumeClick()) {
            if (mc.screen == null && !player.isSpectator() && player.getMainHandItem().is(ModItems.TOGETSU)) {
                if (player.isShiftKeyDown()) {
                    ClientPlayNetworking.send(new TogetsuColorC2SPayload((byte) 0, 0, 0, true));
                } else {
                    mc.setScreen(new TogetsuColorScreen(player.getMainHandItem()));
                }
            }
        }

        // 2. Hạt ánh trăng & đốm sao lấp lánh siêu sắc bám sát 2 lưỡi kiếm ở thế thủ Dual Idle + vệt gió lướt khi chạy nước rút!
        if (WeaponsConfig.get().enableSlashVfx) {
            if ((tick & 1L) == 0L) {
                spawnIdleLunarWisps(mc, level, player, tick);
                spawnSprintBladeTrails(mc, level, player, tick);
            }
        }

        // 3. Điều khiển Chém Song Kiếm (Chuột Trái = Kiếm Trái Trăng Non | Chuột Phải = Kiếm Phải Trăng Khuyết) & Song Nguyệt
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.TOGETSU) || mc.screen != null || player.isSpectator()) {
            wasAttackKeyDown = false;
            wasUseKeyDown = false;
            return;
        }

        TogetsuMoveType cur = TogetsuAnimManager.getMoveType(player.getId());
        float curAlpha = TogetsuAnimManager.getActionProgress(player.getId(), 0.0F);
        if (curAlpha > 0.001F && (cur.isLockedSkill() || (cur == TogetsuMoveType.DUAL_5 && curAlpha < 0.84F))) {
            wasAttackKeyDown = mc.options.keyAttack.isDown();
            wasUseKeyDown = mc.options.keyUse.isDown();
            return;
        }

        boolean fused = TogetsuItem.isFused(stack);
        double actualSpd = Math.max(1.2, player.getAttributeValue(Attributes.ATTACK_SPEED));
        HitResult hit = mc.hitResult;

        // 3a. CHUỘT PHẢI Ở DẠNG SONG ĐAO -> CHÉM KIẾM PHẢI (TRĂNG KHUYẾT — LƯỠI ĐEN)!
        boolean useDown = mc.options.keyUse.isDown();
        boolean justPressedUse = useDown && !wasUseKeyDown;
        wasUseKeyDown = useDown;
        if (!fused && useDown && (hit == null || hit.getType() != HitResult.Type.BLOCK)) {
            boolean allowRight = justPressedUse || WeaponsConfig.get().enableHoldToSlash;
            if (allowRight && (tick - lastRightSlashTick) >= 6L && (tick - lastAnyDualSlashTick) >= 3L) {
                if (tick - lastAnyDualSlashTick > 48L) {
                    localPredictedStep = 0;
                }
                lastRightSlashTick = tick;
                lastAnyDualSlashTick = tick;
                int rightTargetId = (hit instanceof EntityHitResult ehr && ehr.getEntity().isAlive() && ehr.getEntity().isAttackable())
                        ? ehr.getEntity().getId()
                        : -1;
                TogetsuMoveType rMove = TogetsuMoveType.resolveDualHandMove(
                        true, player.onGround(), player.isInWater(), player.fallDistance,
                        player.isSprinting(), localPredictedStep, localRightVariant);
                float rDur = TogetsuCombatHandler.computeDualComboDurationTicks(actualSpd, rMove);
                TogetsuAnimManager.triggerLocalOptimistic(player, rMove, rDur);
                localRightVariant = (localRightVariant + 1) & 1;
                advanceLocalStep(rMove, false);
                ClientPlayNetworking.send(new TogetsuActionC2SPayload(TogetsuActionC2SPayload.ACTION_RIGHT_SLASH, rightTargetId));
            }
        }

        // 3b. CHUỘT TRÁI (Dạng Song Đao = Kiếm Trái Trăng Non | Dạng Song Nguyệt = 3-hit combo 0.3s)
        boolean attackDown = mc.options.keyAttack.isDown();
        boolean justPressed = attackDown && !wasAttackKeyDown;
        wasAttackKeyDown = attackDown;
        if (!attackDown) {
            return;
        }

        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return;
        }

        if (!fused && tick - lastAnyDualSlashTick > 48L) {
            localPredictedStep = 0;
        }

        if (justPressed && hit instanceof EntityHitResult) {
            if (fused && (tick - lastHoldPacketTick) < 6L) {
                return;
            }
            lastHoldPacketTick = tick;
            lastAirSwingTick = tick;
            lastAnyDualSlashTick = tick;
            TogetsuMoveType move = fused
                    ? TogetsuMoveType.fusedByStep(localPredictedStep)
                    : TogetsuMoveType.resolveDualHandMove(false, player.onGround(), player.isInWater(),
                            player.fallDistance, player.isSprinting(), localPredictedStep, localLeftVariant);
            float dur = fused ? move.baseDisplayTicks : TogetsuCombatHandler.computeDualComboDurationTicks(actualSpd, move);
            TogetsuAnimManager.triggerLocalOptimistic(player, move, dur);
            if (!fused) {
                localLeftVariant = (localLeftVariant + 1) & 1;
            }
            advanceLocalStep(move, fused);
            return;
        }

        if (!justPressed && !WeaponsConfig.get().enableHoldToSlash) {
            return;
        }
        long holdMinTicks = fused ? 6L : 3L;
        if (player.getAttackStrengthScale(0.0F) < (fused ? 0.92F : 0.95F) || (tick - lastHoldPacketTick) < holdMinTicks) {
            return;
        }

        lastHoldPacketTick = tick;
        lastAnyDualSlashTick = tick;
        int targetId = -1;
        if (hit instanceof EntityHitResult ehr && ehr.getEntity().isAlive() && ehr.getEntity().isAttackable()) {
            targetId = ehr.getEntity().getId();
        } else {
            lastAirSwingTick = tick;
        }

        TogetsuMoveType move = fused
                ? TogetsuMoveType.fusedByStep(localPredictedStep)
                : TogetsuMoveType.resolveDualHandMove(false, player.onGround(), player.isInWater(),
                        player.fallDistance, player.isSprinting(), localPredictedStep, localLeftVariant);
        float dur = fused ? move.baseDisplayTicks : TogetsuCombatHandler.computeDualComboDurationTicks(actualSpd, move);
        TogetsuAnimManager.triggerLocalOptimistic(player, move, dur);
        if (!fused) {
            localLeftVariant = (localLeftVariant + 1) & 1;
        }
        advanceLocalStep(move, fused);

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
            if (m == TogetsuMoveType.DUAL_SPRINT_THRUST || m == TogetsuMoveType.DUAL_AIR_SLASH) {
                localPredictedStep = 1;
            } else if (m == TogetsuMoveType.DUAL_1) {
                localRightVariant = 1;
            } else if (m == TogetsuMoveType.DUAL_3) {
                localRightVariant = 0;
            } else if (m == TogetsuMoveType.DUAL_2) {
                localLeftVariant = 1;
            } else if (m == TogetsuMoveType.DUAL_4) {
                localLeftVariant = 0;
            } else if (m == TogetsuMoveType.DUAL_5) {
                localPredictedStep = 0;
            } else if (m.id >= TogetsuMoveType.FUSED_1.id && m.id <= TogetsuMoveType.FUSED_3.id) {
                localPredictedStep = ((m.id - TogetsuMoveType.FUSED_1.id) + 1) % TogetsuMoveType.FUSED_COMBO_COUNT;
            } else if (m == TogetsuMoveType.FUSE_TRANSFORM || m == TogetsuMoveType.UNFUSE_SPLIT) {
                localPredictedStep = 0;
                localLeftVariant = 0;
                localRightVariant = 0;
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
            case TogetsuSyncPayload.VFX_MOON_MARK -> {
                TogetsuVfxRenderer.spawnMoonMarkRing(pos, tick);
                spawnLunarParticles(level, pos, true);
                spawnLunarParticles(level, pos, false);
            }
            case TogetsuSyncPayload.VFX_MORPH_FINISHER_IMPACT -> {
                TogetsuAnimManager.triggerHitStop(p.playerId(), 1.6F); // 0.08s hit-stop impact frame!
                TogetsuVfxRenderer.spawnFusionShockwave(pos, true, tick);
                spawnLunarParticles(level, pos, true);
                spawnLunarParticles(level, pos, false);
            }
            case TogetsuSyncPayload.VFX_CRESCENT_WAVE_PROJ -> {
                TogetsuVfxRenderer.spawnSpinningCrescentWave(pos, p.yawDeg(), p.pitchDeg(), p.powerScale(), tick);
                spawnLunarParticles(level, pos, true);
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

    private static void spawnSprintBladeTrails(Minecraft mc, ClientLevel level, LocalPlayer localPlayer, long tick) {
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        AABB box = localPlayer.getBoundingBox().inflate(28.0);
        RandomSource rng = level.random;
        for (Player p : level.getEntitiesOfClass(Player.class, box,
                pl -> pl.isAlive() && pl.getMainHandItem().is(ModItems.TOGETSU) && pl.getOffhandItem().isEmpty())) {
            if (p == localPlayer && firstPerson) {
                continue;
            }
            if (TogetsuAnimManager.isTogetsuAttacking(p)
                    || TogetsuAnimManager.getSprintBlend(p.getId(), 0.0F) < 0.45F) {
                continue;
            }
            ItemStack st = p.getMainHandItem();
            boolean fused = TogetsuAnimManager.shouldRenderAsFused(p.getId(), st, 0.0F);
            float timeSec = (level.getGameTime() % 240000L) / 20.0F;
            TogetsuGlowStyle glowStyle = TogetsuItem.getGlowStyle(st);
            int priRgb = glowStyle.resolvePrimaryRgb(TogetsuItem.getCustomPrimaryRgb(st), timeSec);
            int secRgb = glowStyle.resolveSecondaryRgb(TogetsuItem.getCustomSecondaryRgb(st), timeSec);
            float yawRad = p.yBodyRot * Mth.DEG_TO_RAD;
            double rightX = -Math.cos(yawRad);
            double rightZ = -Math.sin(yawRad);
            double backX = Math.sin(yawRad);
            double backZ = -Math.cos(yawRad);
            double vx = backX * 0.055;
            double vz = backZ * 0.055;

            if (fused) {
                // Song Nguyệt Ground-Drag Sprint (khớp Ảnh 2): tia lửa, bụi gió & đốm sao lấp lánh dọc lưỡi kiếm cày sát đất!
                double tipX = p.getX() + rightX * 0.72 + backX * 0.78 + (rng.nextDouble() - 0.5) * 0.08;
                double tipY = p.getY() + 0.08 + rng.nextDouble() * 0.06;
                double tipZ = p.getZ() + rightZ * 0.72 + backZ * 0.78 + (rng.nextDouble() - 0.5) * 0.08;
                level.addParticle(((tick & 2L) == 0L) ? SPRINT_WIND_WHITE : SPRINT_WIND_BLACK,
                        tipX, tipY, tipZ, vx, 0.025, vz);
                // Đốm sao 4 cánh 2 sắc (xanh băng + tím, tự đổi theo màu [C]) bay lấp lánh dọc thân kiếm kéo sau lưng!
                float uF = 0.15F + rng.nextFloat() * 0.85F;
                double rDrag = Mth.lerp(uF, 0.52F, 0.78F) + (rng.nextDouble() - 0.5) * 0.10;
                double yDrag = Mth.lerp(uF, 0.64F, 0.12F) + (rng.nextDouble() - 0.5) * 0.08;
                double bDrag = Mth.lerp(uF, 0.05F, 0.86F) + (rng.nextDouble() - 0.5) * 0.08;
                TogetsuVfxRenderer.spawnBladeSparkle(
                        new Vec3(p.getX() + rightX * rDrag + backX * bDrag, p.getY() + yDrag, p.getZ() + rightZ * rDrag + backZ * bDrag),
                        ((tick & 2L) == 0L) ? priRgb : secRgb, tick);
                if ((tick % 4L) == 0L) {
                    level.addParticle(ParticleTypes.CRIT,
                            tipX, tipY + 0.04, tipZ,
                            vx * 0.9 + (rng.nextDouble() - 0.5) * 0.04, 0.07, vz * 0.9 + (rng.nextDouble() - 0.5) * 0.04);
                }
            } else {
                // TOGETSU_DUAL_SPRINT: kéo vệt sao 4 cánh tím/magenta (lưỡi Đen tay phải) & xanh băng cyan (lưỡi Trắng tay trái)
                // trực tiếp từ 2 mũi đao Reverse Grip lướt ngược về sau (khớp 1:1 tọa độ mô phỏng SimTogetsuSprint)!
                Vec3 trailVel = new Vec3(backX * 0.145, 0.012, backZ * 0.145);

                // Mũi lưỡi Đen tay phải: base=(+0.59, 0.98, back 0.26) -> tip=(+0.86, 1.03, back 0.95)
                float uR = 0.62F + rng.nextFloat() * 0.38F;
                double rRight = Mth.lerp(uR, 0.59F, 0.86F) + (rng.nextDouble() - 0.5) * 0.06;
                double rY = Mth.lerp(uR, 0.98F, 1.03F) + (rng.nextDouble() - 0.5) * 0.06;
                double rBack = Mth.lerp(uR, 0.26F, 0.95F) + (rng.nextDouble() - 0.5) * 0.05;
                Vec3 rPos = new Vec3(p.getX() + rightX * rRight + backX * rBack, p.getY() + rY,
                        p.getZ() + rightZ * rRight + backZ * rBack);
                TogetsuVfxRenderer.spawnBladeSparkle(rPos,
                        trailVel.add((rng.nextDouble() - 0.5) * 0.025, (rng.nextDouble() - 0.35) * 0.02,
                                (rng.nextDouble() - 0.5) * 0.025),
                        secRgb, tick);

                // Mũi lưỡi Trắng tay trái: base=(-0.59, 0.98, back 0.26) -> tip=(-0.86, 1.03, back 0.95)
                float uL = 0.62F + rng.nextFloat() * 0.38F;
                double lRight = Mth.lerp(uL, -0.59F, -0.86F) + (rng.nextDouble() - 0.5) * 0.06;
                double lY = Mth.lerp(uL, 0.98F, 1.03F) + (rng.nextDouble() - 0.5) * 0.06;
                double lBack = Mth.lerp(uL, 0.26F, 0.95F) + (rng.nextDouble() - 0.5) * 0.05;
                Vec3 lPos = new Vec3(p.getX() + rightX * lRight + backX * lBack, p.getY() + lY,
                        p.getZ() + rightZ * lRight + backZ * lBack);
                TogetsuVfxRenderer.spawnBladeSparkle(lPos,
                        trailVel.add((rng.nextDouble() - 0.5) * 0.025, (rng.nextDouble() - 0.35) * 0.02,
                                (rng.nextDouble() - 0.5) * 0.025),
                        priRgb, tick);

                // Vệt bụi aura & gió lướt mảnh phía sau đúng 2 mũi kiếm
                if ((tick & 1L) == 0L) {
                    level.addParticle(SPRINT_WIND_BLACK, rPos.x, rPos.y, rPos.z, vx, 0.010, vz);
                    level.addParticle(SPRINT_WIND_WHITE, lPos.x, lPos.y, lPos.z, vx, 0.010, vz);
                }
                if ((tick % 3L) == 0L) {
                    level.addParticle(dustFromRgb(secRgb, 0.52F), rPos.x, rPos.y, rPos.z, vx * 0.85, 0.010, vz * 0.85);
                    level.addParticle(dustFromRgb(priRgb, 0.52F), lPos.x, lPos.y, lPos.z, vx * 0.85, 0.010, vz * 0.85);
                }
            }
        }
    }

    private static DustParticleOptions dustFromRgb(int rgb, float scale) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        return new DustParticleOptions(new Vector3f(r, g, b), scale);
    }

    private static void spawnIdleLunarWisps(Minecraft mc, ClientLevel level, LocalPlayer localPlayer, long clientTick) {
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        AABB box = localPlayer.getBoundingBox().inflate(24.0);
        RandomSource rng = level.random;
        float timeSec = (level.getGameTime() % 240000L) / 20.0F;
        boolean sixTick = (clientTick % 6L) == 0L;
        for (Player p : level.getEntitiesOfClass(Player.class, box,
                pl -> pl.isAlive() && pl.getMainHandItem().is(ModItems.TOGETSU) && pl.getOffhandItem().isEmpty())) {
            if (p == localPlayer && firstPerson) {
                continue;
            }
            ItemStack st = p.getMainHandItem();
            boolean fused = TogetsuItem.isFused(st);
            boolean ready = TogetsuItem.getFusionGauge(st) >= 99.5F;
            TogetsuGlowStyle glowStyle = TogetsuItem.getGlowStyle(st);
            int priRgb = glowStyle.resolvePrimaryRgb(TogetsuItem.getCustomPrimaryRgb(st), timeSec);
            int secRgb = glowStyle.resolveSecondaryRgb(TogetsuItem.getCustomSecondaryRgb(st), timeSec);
            float yawRad = p.yBodyRot * Mth.DEG_TO_RAD;
            double fwdX = -Math.sin(yawRad);
            double fwdZ = Math.cos(yawRad);
            double rightX = -Math.cos(yawRad);
            double rightZ = -Math.sin(yawRad);

            if (fused) {
                // Dạng Kết Hợp (Song Nguyệt): đốm sao sáng 4 cánh 2 màu (xanh băng + tím / màu tùy chỉnh [C])
                // lấp lánh dọc toàn bộ lưỡi kiếm cong 1 tay bên hông phải!
                if (!TogetsuAnimManager.isTogetsuAttacking(p) && !p.isSprinting()) {
                    // Đốm sáng kênh Chính (lưỡi trắng cong ngoài - priRgb)
                    float u1 = 0.10F + rng.nextFloat() * 0.90F;
                    double r1 = Mth.lerp(u1, 0.48F, 0.68F) + (rng.nextDouble() - 0.5) * 0.14;
                    double y1 = Mth.lerp(u1, 0.88F, 0.56F) + (rng.nextDouble() - 0.5) * 0.12;
                    double f1 = Mth.lerp(u1, 0.10F, 1.32F) + (rng.nextDouble() - 0.5) * 0.12;
                    TogetsuVfxRenderer.spawnBladeSparkle(
                            new Vec3(p.getX() + rightX * r1 + fwdX * f1, p.getY() + y1, p.getZ() + rightZ * r1 + fwdZ * f1),
                            priRgb, clientTick);

                    // Đốm sáng kênh Phụ (lưỡi đen bên trong - secRgb)
                    float u2 = 0.12F + rng.nextFloat() * 0.88F;
                    double r2 = Mth.lerp(u2, 0.46F, 0.64F) + (rng.nextDouble() - 0.5) * 0.14;
                    double y2 = Mth.lerp(u2, 0.92F, 0.60F) + (rng.nextDouble() - 0.5) * 0.12;
                    double f2 = Mth.lerp(u2, 0.14F, 1.24F) + (rng.nextDouble() - 0.5) * 0.12;
                    TogetsuVfxRenderer.spawnBladeSparkle(
                            new Vec3(p.getX() + rightX * r2 + fwdX * f2, p.getY() + y2, p.getZ() + rightZ * r2 + fwdZ * f2),
                            secRgb, clientTick);
                }

                if (!sixTick) {
                    continue;
                }
                DustParticleOptions priDust = dustFromRgb(priRgb, 0.88F);
                DustParticleOptions secDust = dustFromRgb(secRgb, 0.88F);
                // Khí thế Song Nguyệt mang đúng 2 màu ánh sáng người chơi đã chọn!
                for (int k = 0; k < 2; k++) {
                    double ang = (level.getGameTime() * 0.28) + k * Math.PI + rng.nextDouble() * 0.7;
                    double rad = 0.68 + rng.nextDouble() * 0.42;
                    level.addParticle((k == 0) ? priDust : secDust,
                            p.getX() + Math.cos(ang) * rad, p.getY() + 0.15 + rng.nextDouble() * 1.45, p.getZ() + Math.sin(ang) * rad,
                            0.0, 0.065 + rng.nextDouble() * 0.04, 0.0);
                }
            } else if (!TogetsuAnimManager.isTogetsuAttacking(p) && !p.isSprinting()) {
                // Thế thủ Sunraku chĩa thẳng 2 lưỡi ra trước TOGETSU_DUAL_IDLE (0 clipping giáp):
                // đốm sao 4 cánh + hào quang cel-shaded bám chuẩn 2 đường lưỡi chĩa ngang về trước (Đen bên phải & Trắng bên trái)!
                int burst = (p.getDeltaMovement().horizontalDistanceSqr() < 0.0025) ? 2 : 1;
                for (int k = 0; k < burst; k++) {
                    // 1) Lưỡi Đen Tay Phải (chĩa thẳng ra trước, tím/magenta secRgb):
                    float uR = 0.08F + rng.nextFloat() * 0.90F;
                    double rOffR = Mth.lerp(uR, 0.51F, 0.54F) + (rng.nextDouble() - 0.5) * 0.07;
                    double yOffR = Mth.lerp(uR, 1.03F, 1.06F) + (rng.nextDouble() - 0.5) * 0.07;
                    double fOffR = Mth.lerp(uR, 0.47F, 1.21F) + (rng.nextDouble() - 0.5) * 0.06;
                    TogetsuVfxRenderer.spawnBladeSparkle(
                            new Vec3(p.getX() + rightX * rOffR + fwdX * fOffR, p.getY() + yOffR, p.getZ() + rightZ * rOffR + fwdZ * fOffR),
                            secRgb, clientTick);

                    // 2) Lưỡi Trắng Tay Trái (chĩa thẳng ra trước, xanh băng priRgb):
                    float uL = 0.08F + rng.nextFloat() * 0.90F;
                    double rOffL = Mth.lerp(uL, -0.34F, -0.37F) + (rng.nextDouble() - 0.5) * 0.07;
                    double yOffL = Mth.lerp(uL, 1.00F, 1.03F) + (rng.nextDouble() - 0.5) * 0.07;
                    double fOffL = Mth.lerp(uL, 0.64F, 1.38F) + (rng.nextDouble() - 0.5) * 0.06;
                    TogetsuVfxRenderer.spawnBladeSparkle(
                            new Vec3(p.getX() + rightX * rOffL + fwdX * fOffL, p.getY() + yOffL, p.getZ() + rightZ * rOffL + fwdZ * fOffL),
                            priRgb, clientTick);
                }

                // Hào quang aura mềm dọc 2 lưỡi kiếm khi đứng thủ
                if ((clientTick % 4L) == 0L) {
                    float uA = 0.18F + rng.nextFloat() * 0.74F;
                    level.addParticle(dustFromRgb(secRgb, 0.46F),
                            p.getX() + rightX * Mth.lerp(uA, 0.51F, 0.54F) + fwdX * Mth.lerp(uA, 0.50F, 1.18F),
                            p.getY() + Mth.lerp(uA, 1.03F, 1.06F),
                            p.getZ() + rightZ * Mth.lerp(uA, 0.51F, 0.54F) + fwdZ * Mth.lerp(uA, 0.50F, 1.18F),
                            0.0, 0.012, 0.0);
                    level.addParticle(dustFromRgb(priRgb, 0.46F),
                            p.getX() + rightX * Mth.lerp(uA, -0.34F, -0.37F) + fwdX * Mth.lerp(uA, 0.67F, 1.35F),
                            p.getY() + Mth.lerp(uA, 1.00F, 1.03F),
                            p.getZ() + rightZ * Mth.lerp(uA, -0.34F, -0.37F) + fwdZ * Mth.lerp(uA, 0.67F, 1.35F),
                            0.0, 0.012, 0.0);
                }

                if (sixTick && ready && rng.nextInt(3) == 0) {
                    level.addParticle(dustFromRgb(priRgb, 0.55F), p.getX(), p.getY() + 1.15, p.getZ(),
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

    /** Vòng tròn hạt chớp đen–trắng sắc nét ngay trước ngực (1.2 block phía trước) ở mốc 0.05s Hit 4 Morphing Finisher! */
    public static void spawnMorphFlashRingParticles(ClientLevel level, Player player) {
        Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 center = new Vec3(player.getX(), player.getEyeY() - 0.32, player.getZ()).add(look.scale(1.2));
        int count = 24;
        double rad = 0.72;
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2.0 * i) / count;
            double ca = Math.cos(ang);
            double sa = Math.sin(ang);
            Vec3 p = center.add(right.scale(ca * rad)).add(up.scale(sa * rad));
            Vec3 vel = right.scale(ca * 0.08).add(up.scale(sa * 0.08)).add(look.scale(0.04));
            level.addParticle((i % 2 == 0) ? WAXING_WHITE : WANING_OBSIDIAN, p.x, p.y, p.z, vel.x, vel.y, vel.z);
            if (i % 4 == 0) {
                level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, vel.x * 1.4, vel.y * 1.4, vel.z * 1.4);
            }
        }
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
