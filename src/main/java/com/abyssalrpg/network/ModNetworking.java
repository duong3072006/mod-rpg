package com.abyssalrpg.network;

import com.abyssalrpg.combat.DualBladeCombatHandler;
import com.abyssalrpg.combat.TogetsuCombatHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ModNetworking {
    private ModNetworking() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(ScreenShakePayload.TYPE, ScreenShakePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(JormungandrPathSyncPayload.TYPE, JormungandrPathSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NightmareVfxPayload.TYPE, NightmareVfxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HydraVfxPayload.TYPE, HydraVfxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LeviathanVfxPayload.TYPE, LeviathanVfxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CerberusVfxPayload.TYPE, CerberusVfxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CinderDragonVfxPayload.TYPE, CinderDragonVfxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DualBladeVfxPayload.TYPE, DualBladeVfxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DualBladeComboPayload.TYPE, DualBladeComboPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DualBladeDashVfxPayload.TYPE, DualBladeDashVfxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TogetsuSyncPayload.TYPE, TogetsuSyncPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(DualBladeSlashC2SPayload.TYPE, DualBladeSlashC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DualBladeSlashC2SPayload.TYPE, (payload, context) ->
                context.server().execute(() -> DualBladeCombatHandler.handleSlashPacket(context.player(), payload)));

        PayloadTypeRegistry.playC2S().register(TogetsuActionC2SPayload.TYPE, TogetsuActionC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TogetsuActionC2SPayload.TYPE, (payload, context) ->
                context.server().execute(() -> TogetsuCombatHandler.handleActionPacket(context.player(), payload)));
    }

    public static void sendShakeNearby(ServerLevel level, Vec3 center, double radius, float intensity, int ticks) {
        AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            double dist = p.position().distanceTo(center);
            if (dist <= radius) {
                float scaled = (float) (intensity * (1.0 - (dist / radius) * 0.65));
                ServerPlayNetworking.send(p, new ScreenShakePayload(scaled, ticks));
            }
        }
    }

    /** Gửi đúng 1 gói tin VFX gọn nhẹ sang Client trong bán kính hiển thị (0 vòng lặp sendParticles trên server). */
    public static void sendNightmareVfxNearby(ServerLevel level, byte kind, Vec3 pos, Vec3 dir,
                                              float radius, boolean enraged) {
        double syncDist = 68.0;
        AABB box = new AABB(pos.x - syncDist, pos.y - 32.0, pos.z - syncDist,
                pos.x + syncDist, pos.y + 42.0, pos.z + syncDist);
        NightmareVfxPayload payload = new NightmareVfxPayload(
                kind, pos.x, pos.y, pos.z,
                (float) dir.x, (float) dir.z, radius, enraged);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (p.position().distanceToSqr(pos) <= syncDist * syncDist) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /** Gửi 1 gói tin VFX gọn nhẹ của Spirit Hydra sang Client (0 vòng lặp sendParticles trên server). */
    public static void sendHydraVfxNearby(ServerLevel level, byte kind, Vec3 pos, Vec3 dir,
                                          float radius, boolean enraged) {
        double syncDist = 72.0;
        AABB box = new AABB(pos.x - syncDist, pos.y - 36.0, pos.z - syncDist,
                pos.x + syncDist, pos.y + 44.0, pos.z + syncDist);
        HydraVfxPayload payload = new HydraVfxPayload(
                kind, pos.x, pos.y, pos.z,
                (float) dir.x, (float) dir.y, (float) dir.z, radius, enraged);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (p.position().distanceToSqr(pos) <= syncDist * syncDist) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /** Gửi 1 gói tin VFX siêu gọn của Leviathan sang Client (0 vòng lặp sendParticles trên server). */
    public static void sendLeviathanVfxNearby(ServerLevel level, byte kind, Vec3 pos, Vec3 dir,
                                              float radius, boolean enraged) {
        double syncDist = 88.0;
        AABB box = new AABB(pos.x - syncDist, pos.y - 44.0, pos.z - syncDist,
                pos.x + syncDist, pos.y + 54.0, pos.z + syncDist);
        LeviathanVfxPayload payload = new LeviathanVfxPayload(
                kind, pos.x, pos.y, pos.z,
                (float) dir.x, (float) dir.y, (float) dir.z, radius, enraged);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (p.position().distanceToSqr(pos) <= syncDist * syncDist) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /** Gửi 1 gói tin VFX siêu gọn của Cerberus v4 sang Client (0 vòng lặp sendParticles trên server). */
    public static void sendCerberusVfxNearby(ServerLevel level, byte kind, Vec3 pos, Vec3 dir,
                                             float radius, byte formId) {
        double syncDist = 78.0;
        AABB box = new AABB(pos.x - syncDist, pos.y - 36.0, pos.z - syncDist,
                pos.x + syncDist, pos.y + 48.0, pos.z + syncDist);
        CerberusVfxPayload payload = new CerberusVfxPayload(
                kind, pos.x, pos.y, pos.z,
                (float) dir.x, (float) dir.y, (float) dir.z, radius, formId);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (p.position().distanceToSqr(pos) <= syncDist * syncDist) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /** Gửi 1 gói tin VFX siêu gọn của Cinder Dragon sang Client (0 vòng lặp sendParticles trên server). */
    public static void sendCinderDragonVfxNearby(ServerLevel level, byte kind, Vec3 pos, Vec3 dir,
                                                 float radius, boolean enraged) {
        double syncDist = 88.0;
        AABB box = new AABB(pos.x - syncDist, pos.y - 44.0, pos.z - syncDist,
                pos.x + syncDist, pos.y + 54.0, pos.z + syncDist);
        CinderDragonVfxPayload payload = new CinderDragonVfxPayload(
                kind, pos.x, pos.y, pos.z,
                (float) dir.x, (float) dir.y, (float) dir.z, radius, enraged);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (p.position().distanceToSqr(pos) <= syncDist * syncDist) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /** Gửi 1 gói tin VFX & đồng bộ nhịp Dual Blade siêu gọn tới người chơi trong bán kính 48 block. */
    public static void sendDualBladeVfxNearby(ServerLevel level, DualBladeVfxPayload payload) {
        double syncDist = 48.0;
        Vec3 pos = new Vec3(payload.x(), payload.y(), payload.z());
        AABB box = new AABB(pos.x - syncDist, pos.y - 24.0, pos.z - syncDist,
                pos.x + syncDist, pos.y + 24.0, pos.z + syncDist);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (p.position().distanceToSqr(pos) <= syncDist * syncDist) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /** Gửi gói tin đồng bộ animation toàn thân (`DualBladeComboPayload`) tới player & mọi người chơi xung quanh (56 block). */
    public static void sendDualBladeComboNearby(ServerLevel level, Vec3 center, DualBladeComboPayload payload) {
        double syncDist = 56.0;
        AABB box = new AABB(center.x - syncDist, center.y - 28.0, center.z - syncDist,
                center.x + syncDist, center.y + 28.0, center.z + syncDist);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (p.position().distanceToSqr(center) <= syncDist * syncDist) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /** Gửi 1 gói tin VFX duy nhất cho chiêu Chuột Phải Lướt Chém 10 Nhát (2 dải lụa 10 block + 10 vệt chém trên từng mục tiêu). */
    public static void sendDualBladeDashVfxNearby(ServerLevel level, Vec3 center, DualBladeDashVfxPayload payload) {
        double syncDist = 64.0;
        AABB box = new AABB(center.x - syncDist, center.y - 32.0, center.z - syncDist,
                center.x + syncDist, center.y + 32.0, center.z + syncDist);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (p.position().distanceToSqr(center) <= syncDist * syncDist) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /** Gửi gói tin đồng bộ animation & VFX Togetsu / Song Nguyệt / Crescent Vorpal tới mọi người chơi trong bán kính 64 block. */
    public static void sendTogetsuSyncNearby(ServerLevel level, Vec3 center, TogetsuSyncPayload payload) {
        double syncDist = 64.0;
        AABB box = new AABB(center.x - syncDist, center.y - 32.0, center.z - syncDist,
                center.x + syncDist, center.y + 32.0, center.z + syncDist);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (p.position().distanceToSqr(center) <= syncDist * syncDist) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }
}
