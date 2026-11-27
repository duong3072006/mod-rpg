package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin S2C đồng bộ trạng thái co rút / bung ra của 2 thanh đao cánh tay Bộ Giáp Cerberus
 * cho chính người chơi và mọi người chơi khác trong tầm nhìn (multiplayer-safe).
 */
public record CerberusArmorSyncPayload(int entityId, boolean bladesActive, int remainingTicks) implements CustomPacketPayload {
    public static final Type<CerberusArmorSyncPayload> TYPE =
            new Type<>(AbyssalRpgMod.id("cerberus_armor_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CerberusArmorSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.entityId);
                        buf.writeBoolean(p.bladesActive);
                        buf.writeVarInt(p.remainingTicks);
                    },
                    buf -> new CerberusArmorSyncPayload(
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
