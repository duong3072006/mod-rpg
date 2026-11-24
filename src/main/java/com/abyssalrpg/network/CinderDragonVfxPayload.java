package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Payload S2C siêu gọn gom toàn bộ VFX khè lửa, tia lửa, cầu lửa, cảnh báo và vụ nổ của Cinder Dragon
 * để Client tự render hạt tối giản theo `CinderDragonConfig.vfxQuality` (0 vòng lặp `ServerLevel.sendParticles` mỗi tick!).
 */
public record CinderDragonVfxPayload(
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
    public static final byte KIND_BREATH_TELEGRAPH = 1;
    public static final byte KIND_BREATH_CONE = 2;
    public static final byte KIND_FIREBALL_TARGET = 3;
    public static final byte KIND_FIREBALL_IMPACT = 4;
    public static final byte KIND_FAN_TELEGRAPH = 5;
    public static final byte KIND_WING_SLASH = 6;
    public static final byte KIND_TAIL_ARC = 7;
    public static final byte KIND_STOMP_RING = 8;
    public static final byte KIND_SKYFALL_STRIP = 9;
    public static final byte KIND_BEAM_CHARGE = 10;
    public static final byte KIND_BEAM_STREAM = 11;
    public static final byte KIND_DIVE_TARGET = 12;
    public static final byte KIND_DIVE_IMPACT = 13;
    public static final byte KIND_SUPERNOVA_CHARGE = 14;
    public static final byte KIND_SUPERNOVA_BURST = 15;
    public static final byte KIND_PHASE_BURST = 16;
    public static final byte KIND_FIRE_PATCH = 17;
    public static final byte KIND_DEATH_BURST = 18;

    public static final Type<CinderDragonVfxPayload> TYPE = new Type<>(AbyssalRpgMod.id("cinder_dragon_vfx"));

    public static final StreamCodec<FriendlyByteBuf, CinderDragonVfxPayload> CODEC = StreamCodec.of(
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
            buf -> new CinderDragonVfxPayload(
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

