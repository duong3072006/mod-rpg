package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin C2S kích hoạt kỹ năng Giáp Cerberus khi người chơi ấn Chuột Phải lúc tay không giữ vật phẩm:
 * - `ACTION_ACTIVATE_BLADES = 1`: Nâng Strength thành Strength II trong 1 phút (1200 ticks) và bung 2 thanh đao cánh tay.
 */
public record CerberusArmorActionC2SPayload(byte actionType) implements CustomPacketPayload {
    public static final byte ACTION_ACTIVATE_BLADES = 1;

    public static final Type<CerberusArmorActionC2SPayload> TYPE =
            new Type<>(AbyssalRpgMod.id("cerberus_armor_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CerberusArmorActionC2SPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeByte(p.actionType),
                    buf -> new CerberusArmorActionC2SPayload(buf.readByte())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
