package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin C2S cho vũ khí **Togetsu**:
 * - `ACTION_AIR_SLASH (0)`: Chuột Trái (Dạng Song Đao: Kiếm Trái Trăng Non; Dạng Song Nguyệt: 3-hit combo).
 * - `ACTION_TOGGLE_FUSE (1)`: Phím `[R]` để Hợp Nhất Song Nguyệt (`Sōgengetsu`) hoặc tách lại thành 2 thanh đao.
 * - `ACTION_RIGHT_SLASH (2)`: Chuột Phải ở dạng Song Đao — chém trực tiếp bằng **Kiếm Phải (Trăng Khuyết — Lưỡi Đen)**!
 * - `ACTION_CAST_SKILL (3)`: Phím `[V]` — thi triển Kỹ Năng Đặc Biệt (`targetEntityId == 1` khi giữ Shift -> Crescent Wave Slash;
 *   `0` -> Song Đao: Lao Tới Địch bán kính 15 block + 12 Liên Đâm Vô Địch / Song Nguyệt: Tuyệt Kỹ Crescent Vorpal!).
 */
public record TogetsuActionC2SPayload(byte action, int targetEntityId) implements CustomPacketPayload {
    public static final byte ACTION_AIR_SLASH = 0;
    public static final byte ACTION_TOGGLE_FUSE = 1;
    public static final byte ACTION_RIGHT_SLASH = 2;
    public static final byte ACTION_CAST_SKILL = 3;

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
