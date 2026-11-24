package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin S2C siêu gọn kích hoạt hiệu ứng VFX phía Client cho Boss Leviathan v2
 * (thay thế hoàn toàn toàn bộ vòng lặp `ServerLevel.sendParticles` trên Server để giảm MSPT & băng thông mạng).
 */
public record LeviathanVfxPayload(
        byte kind,
        double x,
        double y,
        double z,
        float dirX,
        float dirY,
        float dirZ,
        float radius,
        boolean enraged
) implements CustomPacketPayload {
    public static final byte KIND_TAIL_TELEGRAPH = 1;
    public static final byte KIND_TAIL_IMPACT = 2;
    public static final byte KIND_BITE_TELEGRAPH = 3;
    public static final byte KIND_BITE_DASH = 4;
    public static final byte KIND_BEAM_TELEGRAPH = 5;
    public static final byte KIND_SUMMON_RING = 6;
    public static final byte KIND_WHIRLPOOL_RING = 7;
    public static final byte KIND_WHIRLPOOL_SPIRAL = 8;
    public static final byte KIND_ROAR_TELEGRAPH = 9;
    public static final byte KIND_ROAR_WAVE = 10;
    public static final byte KIND_STORM_CHARGE = 11;
    public static final byte KIND_TIDAL_TELEGRAPH = 12;
    public static final byte KIND_TIDAL_IMPACT = 13;
    public static final byte KIND_PHASE_PULSE = 14;
    public static final byte KIND_PHASE_CLIMAX = 15;
    public static final byte KIND_DEATH_BURST = 16;
    public static final byte KIND_TELEPORT_SPLASH = 17;
    public static final byte KIND_MELEE_SWEEP = 18;

    public static final Type<LeviathanVfxPayload> TYPE = new Type<>(AbyssalRpgMod.id("leviathan_vfx"));

    public static final StreamCodec<FriendlyByteBuf, LeviathanVfxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.dirX);
                buf.writeFloat(p.dirY);
                buf.writeFloat(p.dirZ);
                buf.writeFloat(p.radius);
                buf.writeBoolean(p.enraged);
            },
            buf -> new LeviathanVfxPayload(
                    buf.readByte(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

