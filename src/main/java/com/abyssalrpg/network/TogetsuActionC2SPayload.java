package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin C2S cho vũ khí **Togetsu**:
 * - `ACTION_AIR_SLASH (0)`: chém gió hoặc giữ chuột trái đồng bộ animation + âm thanh + combo.
 * - `ACTION_TOGGLE_FUSE (1)`: nhấn phím `R` để Hợp Nhất Song Nguyệt (`Sōgengetsu`) hoặc tách lại thành 2 thanh đao.
 */
public record TogetsuActionC2SPayload(byte action, int targetEntityId) implements CustomPacketPayload {
    public static final byte ACTION_AIR_SLASH = 0;
    public static final byte ACTION_TOGGLE_FUSE = 1;

    public static final CustomPacketPayload.Type<TogetsuActionC2SPayload> TYPE =
            new CustomPacketPayload.Type<>(AbyssalRpgMod.id("togetsu_action_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TogetsuActionC2SPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE, TogetsuActionC2SPayload::action,
                    ByteBufCodecs.VAR_INT, TogetsuActionC2SPayload::targetEntityId,
                    TogetsuActionC2SPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
