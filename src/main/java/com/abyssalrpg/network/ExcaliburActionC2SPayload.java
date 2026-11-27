package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói C2S gửi hành động Excalibur từ Client lên Server:
 * - {@link #ACTION_AIR_SLASH}: LMB đánh thường / tự phân nhánh Golden Crescent Wave (Delay 0.3s) & Aerial Judgment.
 * - {@link #ACTION_ULTIMATE}: RMB kích hoạt tuyệt kỹ EXCALIBUR! khi tích đủ 100 năng lượng.
 * - {@link #ACTION_STRIKE_AIR}: Giữ chuột trái sau Hit 1 hoặc Hit 2 -> Strike Air Thrust (5 blocks, phá khiên).
 */
public record ExcaliburActionC2SPayload(byte actionId, int targetEntityId) implements CustomPacketPayload {
    public static final byte ACTION_AIR_SLASH = 0;
    public static final byte ACTION_ULTIMATE = 1;
    public static final byte ACTION_STRIKE_AIR = 2;
    public static final byte ACTION_SHEATHE = 3;

    public static final Type<ExcaliburActionC2SPayload> TYPE =
            new Type<>(AbyssalRpgMod.id("excalibur_action_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExcaliburActionC2SPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.actionId);
                buf.writeVarInt(p.targetEntityId);
            },
            buf -> new ExcaliburActionC2SPayload(buf.readByte(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
