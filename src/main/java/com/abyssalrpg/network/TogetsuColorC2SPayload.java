package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin C2S đồng bộ phong cách & mã màu điểm sáng nổi 3D của **Togetsu** lên server
 * (lưu vào `DataComponents.CUSTOM_DATA` của thanh kiếm đang cầm và đồng bộ tự động trong Multiplayer).
 */
public record TogetsuColorC2SPayload(byte styleId, int primaryRgb, int secondaryRgb, boolean cycleNext)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TogetsuColorC2SPayload> TYPE =
            new CustomPacketPayload.Type<>(AbyssalRpgMod.id("togetsu_color_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TogetsuColorC2SPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE, TogetsuColorC2SPayload::styleId,
                    ByteBufCodecs.INT, TogetsuColorC2SPayload::primaryRgb,
                    ByteBufCodecs.INT, TogetsuColorC2SPayload::secondaryRgb,
                    ByteBufCodecs.BOOL, TogetsuColorC2SPayload::cycleNext,
                    TogetsuColorC2SPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
