package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói S2C đồng bộ animation, vệt chém vàng-đỏ, lướt rút kiếm Iaijutsu & bùng nổ Huyết Thệ của Muramasa Katana.
 */
public record MuramasaSyncPayload(
        byte eventType,
        int playerId,
        byte moveId,
        boolean awakened,
        float durationTicks,
        double x,
        double y,
        double z,
        float yawDeg,
        float pitchDeg,
        float rangeBlocks
) implements CustomPacketPayload {
    public static final byte EVENT_SLASH = 0;
    public static final byte EVENT_IAI_START = 1;
    public static final byte EVENT_IAI_SLASH = 2;
    public static final byte EVENT_BLOOD_OATH_AWAKEN = 3;
    public static final byte EVENT_BLOOD_OATH_END = 4;
    public static final byte EVENT_FLASH_BLIND = 5;

    public static final Type<MuramasaSyncPayload> TYPE =
            new Type<>(AbyssalRpgMod.id("muramasa_sync_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MuramasaSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.eventType);
                buf.writeVarInt(p.playerId);
                buf.writeByte(p.moveId);
                buf.writeBoolean(p.awakened);
                buf.writeFloat(p.durationTicks);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.yawDeg);
                buf.writeFloat(p.pitchDeg);
                buf.writeFloat(p.rangeBlocks);
            },
            buf -> new MuramasaSyncPayload(
                    buf.readByte(),
                    buf.readVarInt(),
                    buf.readByte(),
                    buf.readBoolean(),
                    buf.readFloat(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
