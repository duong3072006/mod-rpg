package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.combat.DualBladeMoveType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Gói tin S2C đồng bộ chuỗi chiến đấu toàn thân kiểu EpicFight-Nightfall của Dual Blade:
 * - `playerId`: ID thực thể người chơi.
 * - `moveTypeId`: mã `DualBladeMoveType` (`COMBO_1..6`, `SPRINT_THRUST`, `AIR_SLASH`, `SKILL_DASH`).
 * - `durationTicks`: thời lượng animation tính động theo `Attack Speed` thực tế (3.0 -> 3.9+ khi có Tempest Flurry).
 * - `animSpeedMult`: hệ số tốc độ animation (`actualAttackSpeed / 3.0f`).
 * - `attackYaw`: góc hướng chém tại thời điểm ra đòn.
 * - `powerTier`: cấp Tempest Flurry hiện tại (`0..3`).
 * - `hitConnected` + `(hitX, hitY, hitZ)`: kích hoạt 5–8 tia lửa va chạm vuông đỏ-ngọc lục bảo đúng điểm chạm (0 gói phụ!).
 */
public record DualBladeComboPayload(
        int playerId,
        byte moveTypeId,
        float durationTicks,
        float animSpeedMult,
        float attackYaw,
        byte powerTier,
        boolean hitConnected,
        double hitX,
        double hitY,
        double hitZ
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DualBladeComboPayload> TYPE =
            new CustomPacketPayload.Type<>(AbyssalRpgMod.id("dual_knife_combo_s2c"));

    public DualBladeMoveType moveType() {
        return DualBladeMoveType.byId(this.moveTypeId);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DualBladeComboPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public DualBladeComboPayload decode(RegistryFriendlyByteBuf buf) {
                    int playerId = ByteBufCodecs.VAR_INT.decode(buf);
                    byte moveTypeId = buf.readByte();
                    float durationTicks = buf.readFloat();
                    float animSpeedMult = buf.readFloat();
                    float attackYaw = buf.readFloat();
                    byte powerTier = buf.readByte();
                    boolean hitConnected = buf.readBoolean();
                    double hx = hitConnected ? buf.readDouble() : 0.0;
                    double hy = hitConnected ? buf.readDouble() : 0.0;
                    double hz = hitConnected ? buf.readDouble() : 0.0;
                    return new DualBladeComboPayload(playerId, moveTypeId, durationTicks, animSpeedMult,
                            attackYaw, powerTier, hitConnected, hx, hy, hz);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, DualBladeComboPayload p) {
                    ByteBufCodecs.VAR_INT.encode(buf, p.playerId);
                    buf.writeByte(p.moveTypeId);
                    buf.writeFloat(p.durationTicks);
                    buf.writeFloat(p.animSpeedMult);
                    buf.writeFloat(p.attackYaw);
                    buf.writeByte(p.powerTier);
                    buf.writeBoolean(p.hitConnected);
                    if (p.hitConnected) {
                        buf.writeDouble(p.hitX);
                        buf.writeDouble(p.hitY);
                        buf.writeDouble(p.hitZ);
                    }
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
