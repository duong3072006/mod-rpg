package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin S2C đồng bộ animation toàn thân & 3D VFX cho **Đinh Ba Biển & Đèn Lồng Biển**:
 * - `EVENT_SLASH`: Chém/đâm combo Đinh Ba 3 nhịp + vệt sóng nước 3D bám lưỡi.
 * - `EVENT_TIDAL_WAVE`: Đèn Lồng tay trái triệu hồi **Sóng Thần (Tsunami)** khổng lồ 9.6×5.8×22 block!
 * - `EVENT_WHIRLPOOL`: Xoáy nước phụ trợ (giữ tương thích).
 * - `EVENT_MAELSTROM_BURST`: Bùng nổ cột nước / cộng hưởng Sóng Thần × Ngũ Thần Kích!
 * - `EVENT_HIT_STOP`: Dừng khung nhẹ ở đòn kết thúc Hit 3!
 * - `EVENT_TRIDENT_VOLLEY`: Giơ Đinh Ba lên trời triệu hồi **5 Đinh Ba 3D** phóng liên hoàn vào mục tiêu!
 */
public record SeaWeaponsSyncPayload(
        byte eventType,
        int playerId,
        byte moveId,
        float durationTicks,
        double x,
        double y,
        double z,
        float yawDeg,
        float pitchDeg,
        float param1,
        float param2
) implements CustomPacketPayload {
    public static final byte EVENT_SLASH = 0;
    public static final byte EVENT_TIDAL_WAVE = 1;
    public static final byte EVENT_WHIRLPOOL = 2;
    public static final byte EVENT_MAELSTROM_BURST = 3;
    public static final byte EVENT_HIT_STOP = 4;
    public static final byte EVENT_TRIDENT_VOLLEY = 5;

    public static final Type<SeaWeaponsSyncPayload> TYPE =
            new Type<>(AbyssalRpgMod.id("sea_weapons_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SeaWeaponsSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeByte(p.eventType);
                        buf.writeVarInt(p.playerId);
                        buf.writeByte(p.moveId);
                        buf.writeFloat(p.durationTicks);
                        buf.writeDouble(p.x);
                        buf.writeDouble(p.y);
                        buf.writeDouble(p.z);
                        buf.writeFloat(p.yawDeg);
                        buf.writeFloat(p.pitchDeg);
                        buf.writeFloat(p.param1);
                        buf.writeFloat(p.param2);
                    },
                    buf -> new SeaWeaponsSyncPayload(
                            buf.readByte(),
                            buf.readVarInt(),
                            buf.readByte(),
                            buf.readFloat(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
