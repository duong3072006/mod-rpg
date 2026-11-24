package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin S2C đồng bộ đường dẫn thân rắn {@link com.abyssalrpg.entity.mythic.jormungandr.SerpentPath}
 * từ Server -> Client mỗi 2 tick cho các người chơi đang theo dõi Jörmungandr.
 *
 * Dùng lượng tử hóa `short` (1 đơn vị = 1/64 block = 1.56 cm, phạm vi ±512 block quanh gốc đầu):
 * 20 điểm điều khiển chỉ tốn ~120 byte/gói, đảm bảo hình ảnh thân rắn trên Client khớp chính xác
 * với chuỗi hitbox proxy trên Server mà không tự dựng lịch sử riêng lệch nhau.
 */
public record JormungandrPathSyncPayload(
        int entityId,
        int activeSegments,
        float raiseBlend,
        float burrowDepth,
        float slitherPhase,
        float slitherStrength,
        double anchorX,
        double anchorY,
        double anchorZ,
        short[] packedOffsets // [dx0, dy0, dz0, dx1, dy1, dz1, ...] (1/64 block relative to anchor)
) implements CustomPacketPayload {
    public static final float QUANT_SCALE = 64.0f;

    public static final Type<JormungandrPathSyncPayload> TYPE =
            new Type<>(AbyssalRpgMod.id("jormungandr_path_sync"));

    public static final StreamCodec<FriendlyByteBuf, JormungandrPathSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeVarInt(p.activeSegments);
                buf.writeFloat(p.raiseBlend);
                buf.writeFloat(p.burrowDepth);
                buf.writeFloat(p.slitherPhase);
                buf.writeFloat(p.slitherStrength);
                buf.writeDouble(p.anchorX);
                buf.writeDouble(p.anchorY);
                buf.writeDouble(p.anchorZ);
                int count = Math.min(36, p.packedOffsets.length / 3);
                buf.writeVarInt(count);
                for (int i = 0; i < count * 3; i++) {
                    buf.writeShort(p.packedOffsets[i]);
                }
            },
            buf -> {
                int entityId = buf.readVarInt();
                int activeSegs = buf.readVarInt();
                float raise = buf.readFloat();
                float burrow = buf.readFloat();
                float slitherPhase = buf.readFloat();
                float slitherStr = buf.readFloat();
                double ax = buf.readDouble();
                double ay = buf.readDouble();
                double az = buf.readDouble();
                int count = Math.max(0, Math.min(36, buf.readVarInt()));
                short[] packed = new short[count * 3];
                for (int i = 0; i < count * 3; i++) {
                    packed[i] = buf.readShort();
                }
                return new JormungandrPathSyncPayload(entityId, activeSegs, raise, burrow,
                        slitherPhase, slitherStr, ax, ay, az, packed);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

