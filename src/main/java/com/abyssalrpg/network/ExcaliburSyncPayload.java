package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói S2C đồng bộ animation, 3 nhánh combo mở rộng, Royal Parry Counter & Vòng Phép Fairy Tail của **Excalibur**.
 */
public record ExcaliburSyncPayload(
        byte eventType,
        int playerId,
        byte moveId,
        float durationTicks,
        double x,
        double y,
        double z,
        float yawDeg,
        float pitchDeg,
        float rangeBlocks,
        float extraValue
) implements CustomPacketPayload {
    public static final byte EVENT_SLASH = 0;
    public static final byte EVENT_ULT_START = 1;
    public static final byte EVENT_BEAM_FIRE = 2;
    public static final byte EVENT_HIT_STOP = 3;
    public static final byte EVENT_SEAL_UNLOCK = 5;
    public static final byte EVENT_STRIKE_AIR_BLAST = 6;
    public static final byte EVENT_GROUND_WAVE = 7;
    public static final byte EVENT_FALLING_STAR_IMPACT = 8;
    public static final byte EVENT_ROYAL_PARRY = 9;
    public static final byte EVENT_GUARD_CROSS_HIT = 10;
    public static final byte EVENT_GUARD_OVERHEAD_IMPACT = 11;
    public static final byte EVENT_ROYAL_COUNTER_SLASH = 12;
    public static final byte EVENT_SHEATHE = 13;
    public static final byte EVENT_SHEATHE_CLINK = 14;
    public static final byte EVENT_AVALON_PARRY_GUARD = 15;
    public static final byte EVENT_AVALON_PARRY_COUNTER = 16;

    public static final Type<ExcaliburSyncPayload> TYPE =
            new Type<>(AbyssalRpgMod.id("excalibur_sync_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExcaliburSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.eventType);
                buf.writeVarInt(p.playerId);
                buf.writeByte(p.moveId);
                buf.writeFloat(p.durationTicks);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.yawDeg);
                buf.writeFloat(p.pitchDeg);
                buf.writeFloat(p.rangeBlocks);
                buf.writeFloat(p.extraValue);
            },
            buf -> new ExcaliburSyncPayload(
                    buf.readByte(),
                    buf.readVarInt(),
                    buf.readByte(),
                    buf.readFloat(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
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
