package com.abyssalrpg.network;

import com.abyssalrpg.AbyssalRpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 1 Payload S2C duy nhất cho toàn bộ hiệu ứng thị giác của chiêu Chuột Phải "Lướt Chém 10 Nhát" (0 spam sendParticles!):
 * - Vẽ 2 dải lụa song song Đỏ–Đen & Vàng–Đen kéo suốt quãng đường lướt `(start -> end)` (10 block).
 * - Vẽ 10 vệt chém chéo sắc lẹm Đỏ–Đen & Vàng–Đen cắt đan xen ngay trên thân từng mục tiêu trong `targetCenters`!
 */
public record DualBladeDashVfxPayload(
        int playerId,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        float yawDeg,
        List<Vec3> targetCenters
) implements CustomPacketPayload {
    public static final int MAX_SYNC_TARGETS = 24;

    public static final CustomPacketPayload.Type<DualBladeDashVfxPayload> TYPE =
            new CustomPacketPayload.Type<>(AbyssalRpgMod.id("dual_knife_dash_vfx_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DualBladeDashVfxPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public DualBladeDashVfxPayload decode(RegistryFriendlyByteBuf buf) {
                    int playerId = ByteBufCodecs.VAR_INT.decode(buf);
                    double sx = buf.readDouble();
                    double sy = buf.readDouble();
                    double sz = buf.readDouble();
                    double ex = buf.readDouble();
                    double ey = buf.readDouble();
                    double ez = buf.readDouble();
                    float yaw = buf.readFloat();
                    int count = Math.min(MAX_SYNC_TARGETS, Math.max(0, ByteBufCodecs.VAR_INT.decode(buf)));
                    List<Vec3> targets = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        targets.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
                    }
                    return new DualBladeDashVfxPayload(playerId, sx, sy, sz, ex, ey, ez, yaw, targets);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, DualBladeDashVfxPayload p) {
                    ByteBufCodecs.VAR_INT.encode(buf, p.playerId);
                    buf.writeDouble(p.startX);
                    buf.writeDouble(p.startY);
                    buf.writeDouble(p.startZ);
                    buf.writeDouble(p.endX);
                    buf.writeDouble(p.endY);
                    buf.writeDouble(p.endZ);
                    buf.writeFloat(p.yawDeg);
                    int count = Math.min(MAX_SYNC_TARGETS, p.targetCenters.size());
                    ByteBufCodecs.VAR_INT.encode(buf, count);
                    for (int i = 0; i < count; i++) {
                        Vec3 v = p.targetCenters.get(i);
                        buf.writeDouble(v.x);
                        buf.writeDouble(v.y);
                        buf.writeDouble(v.z);
                    }
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

