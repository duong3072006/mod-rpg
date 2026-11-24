package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin S2C siêu gọn kích hoạt hiệu ứng VFX phía Client cho Nightmare Knight (thay thế hoàn toàn mọi vòng lặp
 * `ServerLevel.sendParticles` từng tick -> tối ưu băng thông mạng và FPS/MSPT).
 */
public record NightmareVfxPayload(
        byte kind,
        double x,
        double y,
        double z,
        float dirX,
        float dirZ,
        float radius,
        boolean enraged
) implements CustomPacketPayload {
    public static final byte KIND_FAN_TELEGRAPH = 1;
    public static final byte KIND_LINE_TELEGRAPH = 2;
    public static final byte KIND_CAGE_TELEGRAPH = 3;
    public static final byte KIND_SLASH_ARC = 4;
    public static final byte KIND_THRUST_STREAK = 5;
    public static final byte KIND_CLAW_ARC = 6;
    public static final byte KIND_SLAM_IMPACT = 7;
    public static final byte KIND_SPIKE_ERUPT = 8;
    public static final byte KIND_PHASE_BURST = 9;
    public static final byte KIND_REALM_PULSE = 10;
    public static final byte KIND_FLAME_PATCH = 11;

    public static final Type<NightmareVfxPayload> TYPE = new Type<>(AbyssalRpgMod.id("nightmare_vfx"));

    public static final StreamCodec<FriendlyByteBuf, NightmareVfxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.dirX);
                buf.writeFloat(p.dirZ);
                buf.writeFloat(p.radius);
                buf.writeBoolean(p.enraged);
            },
            buf -> new NightmareVfxPayload(
                    buf.readByte(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
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

