package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin Client → Server cho **Damascus Katana**:
 * - `ACTION_AIR_SLASH` (`0`): Chém gió / đánh thường nối nhịp (tự nhận diện Không chiến, Delay 360° & Phản đòn).
 * - `ACTION_TOGGLE_SHEATH` (`1`): Phím tắt `[G]` rút kiếm / tra kiếm vào vỏ.
 * - `ACTION_HEAVY_THRUST` (`2`): Giữ chuột trái sau Hit 1 hoặc Hit 2 -> Đâm Xuyên Giáp (`Katate-tsuki`).
 * - `ACTION_PARRY_COUNTER` (`3`): Nhấp đánh ngay sau khi gạt đòn (`DEFLECT`) -> Phản Công Chí Mạng (`PARRY_COUNTER`).
 * - `ACTION_IAIDO_BLINK` (`4`): Phím `[R]` kích hoạt tuyệt kỹ **Nhất Kiếm Tất Sát (`IAIDO_BLINK`)**!
 */
public record KatanaActionC2SPayload(byte actionId) implements CustomPacketPayload {
    public static final byte ACTION_AIR_SLASH = 0;
    public static final byte ACTION_TOGGLE_SHEATH = 1;
    public static final byte ACTION_HEAVY_THRUST = 2;
    public static final byte ACTION_PARRY_COUNTER = 3;
    public static final byte ACTION_IAIDO_BLINK = 4;

    public static final CustomPacketPayload.Type<KatanaActionC2SPayload> TYPE =
            new CustomPacketPayload.Type<>(AbyssalRpgMod.id("katana_action_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KatanaActionC2SPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeByte(p.actionId),
            buf -> new KatanaActionC2SPayload(buf.readByte())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
