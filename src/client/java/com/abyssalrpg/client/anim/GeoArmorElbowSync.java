package com.abyssalrpg.client.anim;

import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Đồng bộ khớp khuỷu tay 2 khúc (`armorRightForearm` & `armorLeftForearm`, pivot chuẩn `[±5, 18, 0]`)
 * của các bộ Giáp 3D GeckoLib (`Nightmare`, `Cinder Dragon`, `Spirit Hydra`) với hệ thống cánh tay gập
 * `TogetsuBentArmRig` khi người chơi cầm Song Đao Togetsu!
 *
 * Quy ước dấu toán học khớp 100% `GeoArmorRenderer` (`scale(-1, -1, 1)`) + `RenderUtil.matchModelPartRot`:
 * - `geoBone.setRotX(-elbX)`
 * - `geoBone.setRotY(-elbY)`
 * - `geoBone.setRotZ(+elbZ)`
 * - Khi không gập khuỷu (`elbowWeight <= 0.003F`) hoặc trên entity khác: gán tuyệt đối `(0, 0, 0)` (0 tích lũy!).
 */
public final class GeoArmorElbowSync {
    public static final String RIGHT_FOREARM_BONE = "armorRightForearm";
    public static final String LEFT_FOREARM_BONE = "armorLeftForearm";

    private GeoArmorElbowSync() {
    }

    public static void applyBentElbows(AnimationProcessor<?> processor, @Nullable Entity entity) {
        if (processor == null) {
            return;
        }
        GeoBone rightForearm = processor.getBone(RIGHT_FOREARM_BONE);
        GeoBone leftForearm = processor.getBone(LEFT_FOREARM_BONE);
        if (rightForearm == null && leftForearm == null) {
            return;
        }

        float rX = 0.0F;
        float rY = 0.0F;
        float rZ = 0.0F;
        float lX = 0.0F;
        float lY = 0.0F;
        float lZ = 0.0F;

        if (entity instanceof Player player) {
            FullBodyPose pose = TogetsuAnimManager.isHoldingTogetsuReady(player)
                    ? TogetsuAnimManager.getLastComputedPose(player.getId()) : null;
            if ((pose == null || pose.elbowWeight <= 0.003F) && SeaWeaponsAnimManager.isHoldingSeaWeapons(player)) {
                pose = SeaWeaponsAnimManager.getLastComputedPose(player.getId());
            }
            if (pose != null && pose.elbowWeight > 0.003F) {
                float ew = pose.elbowWeight;
                rX = -(pose.rightElbowX * ew);
                rY = -(pose.rightElbowY * ew);
                rZ = +(pose.rightElbowZ * ew);

                lX = -(pose.leftElbowX * ew);
                lY = -(pose.leftElbowY * ew);
                lZ = +(pose.leftElbowZ * ew);
            }
        }

        if (rightForearm != null) {
            rightForearm.setRotX(rX);
            rightForearm.setRotY(rY);
            rightForearm.setRotZ(rZ);
        }
        if (leftForearm != null) {
            leftForearm.setRotX(lX);
            leftForearm.setRotY(lY);
            leftForearm.setRotZ(lZ);
        }
    }
}
