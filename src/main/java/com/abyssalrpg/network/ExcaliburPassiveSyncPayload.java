package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói S2C đồng bộ trạng thái năng lượng & nội tại của **Excalibur** (thuần mạng, 0 rung/giật kiếm):
 * - `energy`: Năng lượng hiện tại (`0..100`, mặc định `0`; kết liễu mỗi kẻ địch `+5`).
 * - `sealMask`: Bitmask 13 Lời Thề Phong Ấn (`0..8191`).
 * - `flags`:
 *   + bit 0 (`FLAG_INVISIBLE`): Trạng thái tàng hình.
 *   + bit 2 (`FLAG_UNSHEATHED`): Trạng thái rút kiếm khỏi vỏ.
 * - `ultCooldownTicks`: Thời gian hồi chiêu Tuyệt kỹ còn lại sau khi dùng skill (`0..500` ticks = `25s`).
 */
public record ExcaliburPassiveSyncPayload(
        int playerId,
        float energy,
        short sealMask,
        byte flags,
        short ultCooldownTicks
) implements CustomPacketPayload {
    public static final byte FLAG_INVISIBLE  = 1 << 0;
    public static final byte FLAG_UNSHEATHED = 1 << 2;

    public static final Type<ExcaliburPassiveSyncPayload> TYPE =
            new Type<>(AbyssalRpgMod.id("excalibur_passive_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExcaliburPassiveSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.playerId);
                buf.writeFloat(p.energy);
                buf.writeShort(p.sealMask);
                buf.writeByte(p.flags);
                buf.writeShort(p.ultCooldownTicks);
            },
            buf -> new ExcaliburPassiveSyncPayload(
                    buf.readVarInt(),
                    buf.readFloat(),
                    buf.readShort(),
                    buf.readByte(),
                    buf.readShort()
            )
    );

    public boolean invisible() {
        return (flags & FLAG_INVISIBLE) != 0;
    }

    public boolean unsheathed() {
        return (flags & FLAG_UNSHEATHED) != 0;
    }

    public int unlockedSeals() {
        return Integer.bitCount(sealMask & 0x1FFF);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
