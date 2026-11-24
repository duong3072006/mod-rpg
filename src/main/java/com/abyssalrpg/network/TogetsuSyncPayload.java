package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.combat.TogetsuMoveType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin S2C đồng bộ animation toàn thân & hiệu ứng VFX 3D của **Togetsu / Song Nguyệt**:
 * - `VFX_NONE (0)`: chỉ phát nhịp chém + ribbon thường.
 * - `VFX_WAXING_CRIT (1)`: hiệu ứng Trăng Non (Trắng rút máu cường hóa đòn kế).
 * - `VFX_WANING_CRIT (2)`: hiệu ứng Trăng Khuyết (Đen hồi máu cân bằng).
 * - `VFX_FUSE_BURST (3)`: bùng nổ vòng trăng Âm–Dương khi ghép thành Đại Đao Song Nguyệt.
 * - `VFX_UNFUSE_BURST (4)`: hiệu ứng tách đôi lại về 2 thanh đao.
 * - `VFX_CRESCENT_VORPAL_WAVE (5)`: phóng luồng năng lượng Trăng Khuyết khổng lồ **Chí Mệnh Tam Nhật Nguyệt** (22 block)!
 */
public record TogetsuSyncPayload(
        int playerId,
        byte moveId,
        float durationTicks,
        boolean fused,
        byte vfxKind,
        double x,
        double y,
        double z,
        float yawDeg,
        float pitchDeg,
        float powerScale
) implements CustomPacketPayload {
    public static final byte VFX_NONE = 0;
    public static final byte VFX_WAXING_CRIT = 1;
    public static final byte VFX_WANING_CRIT = 2;
    public static final byte VFX_FUSE_BURST = 3;
    public static final byte VFX_UNFUSE_BURST = 4;
    public static final byte VFX_CRESCENT_VORPAL_WAVE = 5;
    public static final byte VFX_PIN_IMPACT = 6;
    public static final byte VFX_PIN_STAB_WAXING = 7;
    public static final byte VFX_PIN_STAB_WANING = 8;
    public static final byte VFX_VORPAL_CHARGE_PULSE = 9;

    public static final CustomPacketPayload.Type<TogetsuSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(AbyssalRpgMod.id("togetsu_sync_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TogetsuSyncPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public TogetsuSyncPayload decode(RegistryFriendlyByteBuf buf) {
                    return new TogetsuSyncPayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            buf.readByte(),
                            buf.readFloat(),
                            buf.readBoolean(),
                            buf.readByte(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat()
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, TogetsuSyncPayload p) {
                    ByteBufCodecs.VAR_INT.encode(buf, p.playerId);
                    buf.writeByte(p.moveId);
                    buf.writeFloat(p.durationTicks);
                    buf.writeBoolean(p.fused);
                    buf.writeByte(p.vfxKind);
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                    buf.writeFloat(p.yawDeg);
                    buf.writeFloat(p.pitchDeg);
                    buf.writeFloat(p.powerScale);
                }
            };

    public TogetsuMoveType moveType() {
        return TogetsuMoveType.byId(this.moveId);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
