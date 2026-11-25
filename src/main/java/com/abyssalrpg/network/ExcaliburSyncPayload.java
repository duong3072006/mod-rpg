package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói S2C đồng bộ animation, vệt chém, vòng xoáy gồng Dark Mana, tia năng lượng EXCALIBUR! / EXCALIBUR MORGAN!
 * và trạng thái thức tỉnh Morgan tới toàn bộ Client trong bán kính 68 block.
 */
public record ExcaliburSyncPayload(
        byte eventType,
        int playerId,
        byte moveId,
        boolean morgan,
        float durationTicks,
        double x,
        double y,
        double z,
        float yawDeg,
        float pitchDeg,
        float rangeBlocks,
        float chargeProgress
) implements CustomPacketPayload {
    public static final byte EVENT_SLASH = 0;
    public static final byte EVENT_ULT_START = 1;
    public static final byte EVENT_BEAM_FIRE = 2;
    public static final byte EVENT_CHARGE_START = 3;
    public static final byte EVENT_CHARGE_TICK = 4;
    public static final byte EVENT_CHARGE_CANCEL = 5;
    public static final byte EVENT_MORGAN_AWAKEN = 6;
    public static final byte EVENT_MORGAN_REVERT = 7;
    public static final byte EVENT_HIT_STOP = 8;
    public static final byte EVENT_ENERGY_SYNC = 9;

    public static final Type<ExcaliburSyncPayload> TYPE =
            new Type<>(AbyssalRpgMod.id("excalibur_sync_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExcaliburSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.eventType);
                buf.writeVarInt(p.playerId);
                buf.writeByte(p.moveId);
                buf.writeBoolean(p.morgan);
                buf.writeFloat(p.durationTicks);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.yawDeg);
                buf.writeFloat(p.pitchDeg);
                buf.writeFloat(p.rangeBlocks);
                buf.writeFloat(p.chargeProgress);
            },
            buf -> new ExcaliburSyncPayload(
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
                    buf.readFloat(),
                    buf.readFloat()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
