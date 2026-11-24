package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ScreenShakePayload(float intensity, int durationTicks) implements CustomPacketPayload {
    public static final Type<ScreenShakePayload> TYPE = new Type<>(AbyssalRpgMod.id("screen_shake"));

    public static final StreamCodec<FriendlyByteBuf, ScreenShakePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.intensity);
                buf.writeVarInt(p.durationTicks);
            },
            buf -> new ScreenShakePayload(buf.readFloat(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

