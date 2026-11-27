package com.abyssalrpg.client;

import com.abyssalrpg.combat.CerberusArmorHandler;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.network.CerberusArmorActionC2SPayload;
import com.abyssalrpg.network.CerberusArmorSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trạng thái phía Client cho 2 thanh đao cánh tay co rút của Bộ Giáp Cerberus:
 * - Mặc định mọi entity đều trả `false` (2 thanh đao ẩn gọn bên trong cánh tay).
 * - Khi người chơi mặc đủ 4/4 Giáp Cerberus và ấn Chuột Phải lúc 2 tay trống -> gửi C2S lên server.
 * - Nhận gói S2C `CerberusArmorSyncPayload` để bật/tắt mượt mà cho cả bản thân và người chơi khác.
 */
public final class CerberusArmorClientState {
    private static final Map<Integer, Boolean> ACTIVE_BLADES = new ConcurrentHashMap<>();
    private static ClientLevel lastLevel = null;
    private static int lastRequestTick = -100;

    private CerberusArmorClientState() {}

    public static void handleSyncPayload(ClientLevel level, CerberusArmorSyncPayload payload) {
        if (level != lastLevel) {
            ACTIVE_BLADES.clear();
            lastLevel = level;
        }
        if (payload.bladesActive()) {
            ACTIVE_BLADES.put(payload.entityId(), Boolean.TRUE);
        } else {
            ACTIVE_BLADES.remove(payload.entityId());
        }
    }

    public static void tickClient(Minecraft client) {
        if (client.level == null) {
            if (!ACTIVE_BLADES.isEmpty() || lastLevel != null) {
                ACTIVE_BLADES.clear();
                lastLevel = null;
                lastRequestTick = -100;
            }
            return;
        }
        if (client.level != lastLevel) {
            ACTIVE_BLADES.clear();
            lastLevel = client.level;
            lastRequestTick = -100;
        }
    }

    public static boolean isBladesActive(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !CerberusArmorHandler.hasFullSet(living)) {
            return false;
        }
        return Boolean.TRUE.equals(ACTIVE_BLADES.get(entity.getId()));
    }

    public static void onClientEmptyHandRightClick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null || player.isSpectator() || player.isHandsBusy()) {
            return;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return;
        }
        if (!ArmorConfig.get().cerberusEnableSetBonus || !CerberusArmorHandler.hasFullSet(player)) {
            return;
        }
        int nowTick = player.tickCount;
        int minGap = isBladesActive(player) ? 20 : 6;
        if (nowTick - lastRequestTick < minGap && nowTick >= lastRequestTick) {
            return;
        }
        lastRequestTick = nowTick;
        ClientPlayNetworking.send(new CerberusArmorActionC2SPayload(CerberusArmorActionC2SPayload.ACTION_ACTIVATE_BLADES));
    }
}
