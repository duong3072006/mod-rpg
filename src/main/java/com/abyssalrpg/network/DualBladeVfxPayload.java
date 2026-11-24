package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin S2C siêu gọn đồng bộ VFX sự kiện đặc biệt của Dual Blade (ví dụ tia sáng Chí Mạng vàng-xanh ngọc).
 */
public record DualBladeVfxPayload(
        byte kind,
        int attackerId,
        byte comboStep,
        byte powerTier,
        double x,
        double y,
        double z,
        float dirX,
        float dirY,
        float dirZ
) implements CustomPacketPayload {
    public static final byte KIND_SLASH = 0;
    /** Tia sáng vàng-xanh ngọc sắc gọn khi kích hoạt Enchant Critical Strike (x1.5 sát thương). */
    public static final byte KIND_CRIT_STRIKE = 1;

    public static final CustomPacketPayload.Type<DualBladeVfxPayload> TYPE =
            new CustomPacketPayload.Type<>(AbyssalRpgMod.id("dual_knife_vfx_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DualBladeVfxPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public DualBladeVfxPayload decode(RegistryFriendlyByteBuf buf) {
                    byte kind = buf.readByte();
                    int attackerId = ByteBufCodecs.VAR_INT.decode(buf);
                    byte comboStep = buf.readByte();
                    byte powerTier = buf.readByte();
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    float dirX = buf.readFloat();
                    float dirY = buf.readFloat();
                    float dirZ = buf.readFloat();
                    return new DualBladeVfxPayload(kind, attackerId, comboStep, powerTier, x, y, z, dirX, dirY, dirZ);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, DualBladeVfxPayload p) {
                    buf.writeByte(p.kind);
                    ByteBufCodecs.VAR_INT.encode(buf, p.attackerId);
                    buf.writeByte(p.comboStep);
                    buf.writeByte(p.powerTier);
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                    buf.writeFloat(p.dirX);
                    buf.writeFloat(p.dirY);
                    buf.writeFloat(p.dirZ);
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
