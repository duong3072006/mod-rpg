package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin S2C siêu gọn kích hoạt hiệu ứng VFX phía Client cho Boss Cerberus v4
 * (thay thế 100% vòng lặp `ServerLevel.sendParticles` trên Server -> tối ưu MSPT khi cả 3 cá thể Phase 2 cùng giao chiến!).
 */
public record CerberusVfxPayload(
        byte kind,
        double x,
        double y,
        double z,
        float dirX,
        float dirY,
        float dirZ,
        float radius,
        byte formId
) implements CustomPacketPayload {
    public static final byte KIND_BITE_TELEGRAPH = 1;
    public static final byte KIND_BITE_IMPACT = 2;
    public static final byte KIND_BREATH_TELEGRAPH = 3;
    public static final byte KIND_BREATH_STREAM = 4;
    public static final byte KIND_SLAM_TELEGRAPH = 5;
    public static final byte KIND_SLAM_IMPACT = 6;
    public static final byte KIND_POUNCE_IMPACT = 7;
    public static final byte KIND_HOWL_CHARGE = 8;
    public static final byte KIND_DASH_TELEGRAPH = 9;
    public static final byte KIND_DASH_TRAIL = 10;
    public static final byte KIND_NOVA_CHARGE = 11;
    public static final byte KIND_NOVA_RING = 12;
    public static final byte KIND_SHADOW_VANISH = 13;
    public static final byte KIND_SHADOW_WARN = 14;
    public static final byte KIND_SHADOW_APPEAR = 15;
    public static final byte KIND_PHASE_SPIRAL = 16;
    public static final byte KIND_SPLIT_EXPLOSION = 17;
    public static final byte KIND_SPLIT_FIREBALL = 18;
    public static final byte KIND_ROLE_BURST = 19;
    public static final byte KIND_FOOTSTEP_DUST = 20;
    public static final byte KIND_FIRE_TRAIL = 21;
    public static final byte KIND_HELLHOUND_SUMMON = 22;
    public static final byte KIND_DEATH_CLIMAX = 23;

    public static final Type<CerberusVfxPayload> TYPE = new Type<>(AbyssalRpgMod.id("cerberus_vfx"));

    public static final StreamCodec<FriendlyByteBuf, CerberusVfxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.dirX);
                buf.writeFloat(p.dirY);
                buf.writeFloat(p.dirZ);
                buf.writeFloat(p.radius);
                buf.writeByte(p.formId);
            },
            buf -> new CerberusVfxPayload(
                    buf.readByte(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readByte()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

