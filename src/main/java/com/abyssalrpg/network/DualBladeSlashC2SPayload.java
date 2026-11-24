package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin C2S phục vụ cơ chế Hold-to-Slash (giữ chuột trái chém liên hoàn) & chém gió đồng bộ nhịp 3-beat:
 * - `targetEntityId >= 0`: tấn công mục tiêu trong tầm & đường nhìn (server tự xác thực item, cooldown >= 90%, tầm & LOS).
 * - `targetEntityId == -1`: chém gió theo nhịp (server xác thực cooldown >= 90% rồi phát nhịp combo + VFX + âm thanh).
 */
public record DualBladeSlashC2SPayload(int targetEntityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DualBladeSlashC2SPayload> TYPE =
            new CustomPacketPayload.Type<>(AbyssalRpgMod.id("dual_knife_slash_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DualBladeSlashC2SPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DualBladeSlashC2SPayload::targetEntityId,
                    DualBladeSlashC2SPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

