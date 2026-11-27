package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin Server → Client đồng bộ hoạt ảnh, trạng thái rút/tra vỏ & VFX của **Damascus Katana**
 * (bao gồm 5 nhánh combo mở rộng: Thrust, 360° Delay Sweep, Iaido Judgment Cut, Aerial Slam & Parry Counter).
 */
public record KatanaSyncPayload(
        byte eventType,
        int playerId,
        byte moveId,
        float durationTicks,
        double x,
        double y,
        double z,
        float yawDeg,
        float pitchDeg,
        boolean drawn,
        float extraValue
) implements CustomPacketPayload {
    public static final byte EVENT_SLASH = 0;
    public static final byte EVENT_DRAW = 1;
    public static final byte EVENT_SHEATH = 2;
    public static final byte EVENT_DEFLECT = 3;
    public static final byte EVENT_HIT_STOP = 4;
    public static final byte EVENT_STATE = 5;
    public static final byte EVENT_IAIDO_CHARGE = 6;
    public static final byte EVENT_IAIDO_READY = 7;
    public static final byte EVENT_JUDGMENT_CUT = 8;
    public static final byte EVENT_AIR_SLAM_IMPACT = 9;
    public static final byte EVENT_GUARD_UKE_HIT = 10;
    public static final byte EVENT_GUARD_KASA_IMPACT = 11;
    public static final byte EVENT_GUARD_MUNE_OSHI = 12;
    public static final byte EVENT_GUARD_SAYA_UKE = 13;

    public static final CustomPacketPayload.Type<KatanaSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(AbyssalRpgMod.id("katana_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KatanaSyncPayload> CODEC = StreamCodec.of(
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
                buf.writeBoolean(p.drawn);
                buf.writeFloat(p.extraValue);
            },
            buf -> new KatanaSyncPayload(
                    buf.readByte(),
                    buf.readVarInt(),
                    buf.readByte(),
                    buf.readFloat(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readFloat()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
