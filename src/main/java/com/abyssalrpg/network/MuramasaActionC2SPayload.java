package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói C2S gửi hành động Yêu Đao Muramasa từ Client lên Server:
 * - {@link #ACTION_AIR_SLASH}: LMB chém gió / giữ chuột chém liên hoàn 3 nhịp Katana.
 * - {@link #ACTION_IAIJUTSU}: RMB kích hoạt Nhất Đao Huyết Nguyện (Iaijutsu Quick-Draw).
 * - {@link #ACTION_BLOOD_OATH}: `[R]` / `Shift+RMB` kích hoạt Huyết Thệ Yêu Đao (12s Demon Possession).
 */
public record MuramasaActionC2SPayload(byte actionId, int targetEntityId) implements CustomPacketPayload {
    public static final byte ACTION_AIR_SLASH = 0;
    public static final byte ACTION_IAIJUTSU = 1;
    public static final byte ACTION_BLOOD_OATH = 2;

    public static final Type<MuramasaActionC2SPayload> TYPE =
            new Type<>(AbyssalRpgMod.id("muramasa_action_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MuramasaActionC2SPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.actionId);
                buf.writeVarInt(p.targetEntityId);
            },
            buf -> new MuramasaActionC2SPayload(buf.readByte(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
