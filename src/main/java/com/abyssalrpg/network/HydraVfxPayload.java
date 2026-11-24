package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin S2C siêu gọn kích hoạt hiệu ứng VFX phía Client cho Boss Spirit Hydra 4 đầu
 * (thay thế hoàn toàn các vòng lặp `ServerLevel.sendParticles` gây lag mạng & tăng MSPT).
 */
public record HydraVfxPayload(
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
    public static final byte KIND_BITE_TELEGRAPH = 1;
    public static final byte KIND_BITE_IMPACT = 2;
    public static final byte KIND_BREATH_TELEGRAPH = 3;
    public static final byte KIND_BREATH_STREAM = 4;
    public static final byte KIND_SPIT_CHARGE = 5;
    public static final byte KIND_SLAM_TELEGRAPH = 6;
    public static final byte KIND_SLAM_IMPACT = 7;
    public static final byte KIND_HEAD_SEVER = 8;
    public static final byte KIND_HEAD_REGROW = 9;
    public static final byte KIND_NOVA_RING = 10;
    public static final byte KIND_CHAIN_LINE = 11;
    public static final byte KIND_WRATH_CHARGE = 12;
    public static final byte KIND_WRATH_IMPACT = 13;
    public static final byte KIND_PHASE_PULSE = 14;
    public static final byte KIND_DEATH_BURST = 15;

    public static final Type<HydraVfxPayload> TYPE = new Type<>(AbyssalRpgMod.id("hydra_vfx"));

    public static final StreamCodec<FriendlyByteBuf, HydraVfxPayload> CODEC = StreamCodec.of(
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
            buf -> new HydraVfxPayload(
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

