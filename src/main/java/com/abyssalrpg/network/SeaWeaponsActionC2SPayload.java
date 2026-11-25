package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin C2S cho cặp vũ khí biển **Đinh Ba & Đèn Lồng (`abyssalrpg:sea_trident`)**:
 * - `ACTION_AIR_SLASH = 0`: Chém gió / Hold-to-Slash 3 nhịp Đinh Ba.
 * - `ACTION_CAST_TIDAL_WAVE = 1`: Đèn Lồng tay trái triệu hồi Sóng Triều (`RMB`).
 * - `ACTION_CAST_WHIRLPOOL = 2`: Đinh Ba tay phải triệu hồi Xoáy Nước (`[R]` hoặc `Shift+RMB`).
 */
public record SeaWeaponsActionC2SPayload(byte actionType) implements CustomPacketPayload {
    public static final byte ACTION_AIR_SLASH = 0;
    public static final byte ACTION_CAST_TIDAL_WAVE = 1;
    public static final byte ACTION_CAST_WHIRLPOOL = 2;

    public static final Type<SeaWeaponsActionC2SPayload> TYPE =
            new Type<>(AbyssalRpgMod.id("sea_weapons_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SeaWeaponsActionC2SPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeByte(p.actionType),
                    buf -> new SeaWeaponsActionC2SPayload(buf.readByte())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
