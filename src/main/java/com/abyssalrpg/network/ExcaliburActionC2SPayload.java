package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói C2S gửi hành động Excalibur / Excalibur Morgan từ Client lên Server:
 * - {@link #ACTION_AIR_SLASH}: LMB chém gió / giữ chuột chém liên hoàn 3 nhịp.
 * - {@link #ACTION_ULTIMATE}: RMB kích hoạt tuyệt kỹ chùm sáng EXCALIBUR! / EXCALIBUR MORGAN!.
 * - {@link #ACTION_CHARGE_START}: Bắt đầu gồng 4.0s Dark Mana [R] (hỗ trợ cả nhấn giữ & bấm kích hoạt).
 * - {@link #ACTION_CHARGE_CANCEL}: Hủy gồng Dark Mana (nếu người chơi bấm hủy / đổi vũ khí).
 */
public record ExcaliburActionC2SPayload(byte actionId, int targetEntityId) implements CustomPacketPayload {
    public static final byte ACTION_AIR_SLASH = 0;
    public static final byte ACTION_ULTIMATE = 1;
    public static final byte ACTION_CHARGE_START = 2;
    public static final byte ACTION_CHARGE_CANCEL = 3;

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
