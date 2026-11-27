package com.abyssalrpg.client.anim;

import com.abyssalrpg.client.ClientRpgState;
import com.abyssalrpg.client.KatanaClientState;
import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.client.renderer.vfx.ExcaliburVfxRenderer;
import com.abyssalrpg.client.renderer.vfx.KatanaVfxRenderer;
import com.abyssalrpg.combat.KatanaMoveType;
import com.abyssalrpg.item.KatanaItem;
import com.abyssalrpg.registry.ModItems;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Hệ thống Rig Animation Toàn Thân 2 Khớp (Khuỷu tay + Đầu gối) + IK 2 tay bám `Kashira` / `Koiguchi`
 * cho **Damascus Katana**:
 * - Chuỗi đánh thường 4 hit (`HIT_1_DRAW`, `HIT_2_DOWN_DIAG`, `HIT_3_UP_DIAG`, `HIT_4_FINISHER`) + `CHIBURI_NOTO`.
 * - 5 Nhánh Combo Mở Rộng:
 *   1. `THRUST` (Katate-tsuki đâm xuyên giáp 1 tay, tay trái vung ra sau + hitstop `0.08s`).
 *   2. `DELAY_SWEEP_360` (Quét tầm thấp `Body Y -4u` + Xoay người `360°` quanh trục Y!).
 *   3. `IAIDO_CHARGE` & `IAIDO_BLINK` (Nén khí trong vỏ `1.5u` -> Blink snap quay lưng duỗi kiếm chéo phải-dưới -> Noto tra kiếm!).
 *   4. `AIR_1_LAUNCH` -> `AIR_2_MID_SWEEP` -> `AIR_3_SLAM` (Lộn vòng hất tung -> Chém ngang treo không -> Bổ thiên thạch cắm đất!).
 *   5. `DEFLECT` -> `PARRY_COUNTER` (Đỡ chéo `45°` `Yaw -20°` -> Vặn hông `+45°` phản chém chí mạng!).
 */
public final class KatanaAnimManager {
    private record KeyPose(
            float rootY,
            float bodyX, float bodyY, float bodyZ,
            float headX, float headY,
            float rLegX, float lLegX, float rKneeX, float lKneeX,
            float rArmX, float rArmY, float rArmZ, float rElbX, float rElbY, float rElbZ, float rPosX, float rPosZ,
            float lArmX, float lArmY, float lArmZ, float lElbX, float lElbY, float lElbZ, float lPosX, float lPosZ,
            float bladeP, float bladeR, float bladeY, float bladeOffY
    ) {}

    // --- 0. Dáng nền (Sheathed Idle & Chudan-no-kamae) ---
    private static final KeyPose KP_SHEATHED_IDLE = new KeyPose(
            +0.0000F, +0.000F, +0.000F, +0.000F, +0.000F, +0.000F,
            +0.140F, -0.140F, +0.00F, +0.00F,
            -0.560F, -0.740F, +0.060F, -0.440F, +0.000F, -0.340F, +0.00F, -0.40F,
            +0.312F, +0.930F, +0.090F, -1.137F, +0.000F, +0.009F, +0.02F, +0.18F,
            -176.98F, +50.36F, -122.02F, +0.000F);

    private static final KeyPose KP_CHUDAN_STANCE = new KeyPose(
            -0.1250F, +0.070F, +0.262F, +0.000F, -0.035F, -0.209F,
            -0.436F, +0.436F, +0.34F, +0.26F,
            -0.920F, -0.340F, +0.060F, -0.300F, +0.000F, -0.180F, +0.00F, -0.45F,
            -0.848F, +0.754F, +0.128F, -0.043F, +0.000F, +0.186F, -0.25F, -0.39F,
            -21.26F, -12.41F, +157.94F, -0.050F);

    // --- 1. HIT 1: Rút kiếm chém lướt ngang hơi chếch lên (0.40s / 8 ticks) ---
    private static final KeyPose KP_H1_UNLOCK = new KeyPose(
            -0.0938F, +0.087F, -0.262F, +0.000F, +0.070F, +0.175F,
            +0.244F, -0.244F, +0.28F, +0.28F,
            -0.740F, -0.680F, +0.200F, -0.500F, +0.000F, -0.440F, +0.40F, -0.48F,
            +0.297F, +0.828F, +0.036F, -1.383F, +0.000F, -0.070F, +0.02F, +0.21F,
            +167.72F, +56.04F, -128.75F, +0.000F);

    private static final KeyPose KP_H1_MID = new KeyPose(
            -0.1125F, +0.105F, +0.175F, +0.000F, -0.035F, -0.140F,
            -0.244F, +0.244F, +0.36F, +0.28F,
            -0.900F, -0.320F, +0.260F, -0.580F, +0.000F, -0.480F, +0.00F, -0.42F,
            +1.049F, +0.013F, +0.100F, -1.192F, +0.000F, -0.015F, -0.02F, +0.00F,
            -73.38F, -4.69F, +76.35F, -0.040F);

    private static final KeyPose KP_H1_END_HOLD = new KeyPose(
            -0.1000F, +0.070F, +0.611F, +0.000F, -0.035F, -0.489F,
            -0.436F, +0.384F, +0.36F, +0.26F,
            -1.280F, +0.420F, +0.340F, -0.540F, +0.000F, -0.280F, +0.00F, -0.22F,
            +0.661F, +0.318F, +0.080F, -1.124F, +0.000F, -0.009F, -0.01F, +0.06F,
            -104.49F, -54.61F, +87.84F, -0.040F);

    // --- 2. HIT 2: Chém chéo Trên Phải -> Dưới Trái (0.35s / 7 ticks) ---
    private static final KeyPose KP_H2_WINDUP = new KeyPose(
            -0.0750F, -0.087F, +0.698F, +0.000F, +0.035F, -0.559F,
            -0.244F, +0.279F, +0.34F, +0.24F,
            -2.060F, +0.100F, +0.140F, -0.540F, +0.000F, -0.140F, +0.42F, -0.25F,
            -1.972F, +0.937F, -0.008F, +0.000F, +0.000F, +0.159F, -0.60F, -1.20F,
            -22.60F, -31.42F, -150.71F, -0.050F);

    private static final KeyPose KP_H2_MID = new KeyPose(
            -0.1125F, +0.209F, +0.035F, +0.000F, -0.105F, -0.035F,
            +0.175F, -0.314F, +0.34F, +0.42F,
            -0.820F, -0.240F, +0.100F, -0.480F, +0.000F, -0.140F, +0.15F, -0.45F,
            -1.058F, +0.913F, +0.032F, +0.000F, +0.000F, +0.109F, -0.22F, -0.43F,
            -56.77F, -29.27F, -141.37F, -0.050F);

    private static final KeyPose KP_H2_END = new KeyPose(
            -0.1250F, +0.209F, -0.611F, +0.000F, -0.105F, +0.489F,
            +0.419F, -0.524F, +0.24F, +0.48F,
            -0.380F, -0.340F, -0.060F, -0.880F, +0.000F, +0.260F, +0.40F, -0.42F,
            -1.221F, +1.064F, +0.095F, +0.000F, +0.000F, +0.163F, -0.60F, -1.20F,
            -112.04F, +29.73F, -130.68F, -0.050F);

    // --- 3. HIT 3: Chém hất ngược Dưới Trái -> Trên Phải (0.35s / 7 ticks) ---
    private static final KeyPose KP_H3_DIP_FLIP = new KeyPose(
            -0.1562F, +0.279F, -0.611F, -0.087F, -0.140F, +0.489F,
            +0.454F, -0.559F, +0.48F, +0.62F,
            -0.340F, -0.360F, -0.080F, -0.820F, +0.000F, +0.220F, +0.42F, -0.40F,
            -1.138F, +1.075F, +0.093F, +0.000F, +0.000F, +0.164F, -0.60F, -1.20F,
            -102.91F, +27.58F, +48.45F, -0.050F);

    private static final KeyPose KP_H3_MID = new KeyPose(
            -0.0625F, +0.070F, +0.070F, +0.000F, -0.035F, -0.070F,
            -0.314F, +0.279F, +0.30F, +0.24F,
            -1.420F, -0.060F, +0.160F, -0.340F, +0.000F, -0.140F, +0.15F, -0.40F,
            -1.718F, +1.012F, +0.034F, +0.000F, +0.000F, +0.162F, -0.60F, -1.20F,
            -123.24F, +7.92F, +52.92F, -0.050F);

    private static final KeyPose KP_H3_END_HIGH = new KeyPose(
            +0.0187F, -0.105F, +0.698F, +0.000F, +0.035F, -0.559F,
            -0.611F, +0.524F, +0.24F, +0.16F,
            -2.120F, +0.140F, +0.180F, -0.560F, +0.000F, -0.140F, +0.42F, -0.24F,
            -2.072F, +0.940F, -0.084F, -0.078F, +0.000F, +0.409F, -0.04F, -0.17F,
            -88.87F, -41.59F, +29.84F, -0.050F);

    // --- 4. HIT 4: Xoay người bổ dọc dứt điểm (0.55s / 11 ticks) ---
    private static final KeyPose KP_H4_OVERHEAD = new KeyPose(
            +0.0625F, -0.314F, +0.000F, +0.000F, -0.175F, +0.000F,
            -0.209F, +0.244F, +0.16F, +0.16F,
            -2.880F, +0.180F, +0.340F, -0.360F, +0.000F, -0.140F, +0.60F, -0.15F,
            -2.371F, +0.910F, -0.247F, -0.285F, +0.000F, +0.448F, +0.02F, +0.13F,
            -34.16F, -20.97F, +160.39F, -0.050F);

    private static final KeyPose KP_H4_MID = new KeyPose(
            -0.0938F, +0.175F, +0.000F, +0.000F, -0.070F, +0.000F,
            -0.489F, +0.419F, +0.56F, +0.44F,
            -0.740F, -0.280F, -0.040F, -0.440F, +0.000F, +0.060F, +0.40F, -0.45F,
            -0.999F, +0.959F, +0.047F, +0.000F, +0.000F, +0.140F, -0.45F, -0.77F,
            -57.57F, -13.54F, +177.79F, -0.050F);

    private static final KeyPose KP_H4_IMPACT = new KeyPose(
            -0.2188F, +0.489F, +0.000F, +0.000F, -0.140F, +0.000F,
            -0.803F, +0.698F, +0.88F, +0.68F,
            -0.160F, -0.260F, -0.060F, -0.920F, +0.000F, +0.100F, +0.45F, -0.45F,
            -0.891F, +0.868F, +0.182F, -0.224F, +0.000F, +0.359F, -0.42F, -0.23F,
            -115.30F, -6.77F, -167.59F, -0.050F);

    // --- 5. Vẩy máu & Tra kiếm vào vỏ (Chiburi & Sheathe — 0.90s / 18 ticks) ---
    private static final KeyPose KP_CHIB_P1A_TEMPLE = new KeyPose(
            -0.0312F, +0.000F, +0.140F, +0.000F, +0.000F, -0.105F,
            -0.209F, +0.209F, +0.14F, +0.14F,
            -2.050F, +0.160F, +0.300F, -0.780F, +0.000F, -0.180F, +0.00F, -0.20F,
            +0.656F, +0.277F, +0.095F, -1.106F, +0.000F, -0.007F, -0.02F, +0.10F,
            -127.14F, -27.49F, +158.68F, -0.040F);

    private static final KeyPose KP_CHIB_P1B_FLICK = new KeyPose(
            -0.0312F, +0.035F, -0.105F, +0.000F, +0.000F, +0.070F,
            -0.175F, +0.175F, +0.12F, +0.12F,
            -0.580F, +0.340F, +0.360F, -0.760F, +0.000F, -0.160F, +0.00F, -0.15F,
            +0.873F, +0.080F, +0.124F, -1.158F, +0.000F, -0.015F, -0.02F, +0.06F,
            -127.49F, -27.13F, +159.54F, -0.040F);

    private static final KeyPose KP_CHIB_P2_NOTO_TIP = new KeyPose(
            -0.0312F, +0.087F, -0.175F, +0.000F, +0.209F, -0.349F,
            -0.105F, +0.140F, +0.10F, +0.10F,
            -0.920F, +0.260F, +0.140F, -0.440F, +0.000F, -0.340F, +0.00F, -0.42F,
            +0.708F, -0.145F, +0.116F, -1.092F, +0.000F, -0.003F, -0.02F, +0.08F,
            +139.93F, +49.77F, -145.82F, -0.020F);

    private static final KeyPose KP_CHIB_P3A_PAUSE = new KeyPose(
            -0.0125F, +0.052F, -0.105F, +0.000F, +0.105F, -0.175F,
            +0.105F, -0.105F, +0.05F, +0.05F,
            -0.640F, -0.680F, +0.100F, -0.480F, +0.000F, -0.400F, +0.20F, -0.44F,
            +0.287F, +0.856F, +0.069F, -1.260F, +0.000F, -0.040F, +0.02F, +0.18F,
            +174.96F, +50.41F, -124.11F, +0.000F);

    // --- 6. NHÁNH MỞ RỘNG 1: Đâm Xuyên Giáp (Thrust / Katate-tsuki — 0.50s / 10 ticks) ---
    private static final KeyPose KP_THRUST_COIL = new KeyPose(
            -0.1562F, +0.175F, -0.785F, +0.000F, -0.105F, +0.628F,
            +0.593F, -0.489F, +0.62F, +0.56F,
            +0.420F, +0.060F, +0.200F, -1.420F, +0.000F, -0.180F, +0.10F, +0.55F,
            -0.466F, +1.450F, +0.550F, +0.000F, +0.000F, +0.232F, -0.80F, -0.27F,
            -59.36F, +7.62F, -88.19F, -0.040F);

    private static final KeyPose KP_THRUST_EXTEND = new KeyPose(
            -0.1250F, +0.349F, -0.262F, +0.000F, -0.209F, +0.209F,
            -0.698F, +0.803F, +0.48F, +0.24F,
            -1.550F, -0.120F, +0.040F, -0.060F, +0.000F, -0.020F, +0.25F, -0.95F,
            +0.785F, -0.180F, -0.436F, -0.180F, +0.000F, +0.000F, +0.15F, +0.55F,
            -93.40F, -7.97F, -87.57F, +0.040F);

    // --- 7. NHÁNH MỞ RỘNG 2: Bạt Kiếm Liên Hoàn (Low Sweep + 360° Spin — 0.55s / 11 ticks) ---
    private static final KeyPose KP_LOW_SWEEP_START = new KeyPose(
            -0.2500F, +0.419F, +0.611F, +0.000F, -0.244F, -0.489F,
            -0.768F, +0.628F, +0.86F, +0.78F,
            -0.580F, +0.440F, +0.260F, -0.620F, +0.000F, -0.180F, +0.15F, -0.15F,
            -1.181F, +1.109F, +0.050F, +0.000F, +0.000F, +0.163F, -0.80F, -1.20F,
            -85.22F, -47.29F, -78.15F, -0.040F);

    private static final KeyPose KP_LOW_SWEEP_END = new KeyPose(
            -0.2500F, +0.454F, -0.960F, +0.000F, -0.244F, +0.733F,
            +0.663F, -0.768F, +0.82F, +0.86F,
            -0.480F, -0.580F, -0.060F, -0.680F, +0.000F, +0.240F, +0.48F, -0.38F,
            -1.060F, +0.995F, +0.042F, +0.000F, +0.000F, +0.157F, -0.80F, -1.20F,
            -65.88F, +38.53F, -81.12F, -0.040F);

    private static final KeyPose KP_SPIN_360_HOLD = new KeyPose(
            -0.0625F, +0.140F, -0.436F, +0.000F, -0.070F, +0.349F,
            -0.419F, +0.419F, +0.30F, +0.26F,
            -1.120F, -0.180F, +0.240F, -0.220F, +0.000F, -0.180F, +0.20F, -0.55F,
            -1.261F, +0.876F, +0.007F, +0.000F, +0.000F, +0.098F, -0.34F, -0.77F,
            -77.42F, -29.82F, -80.21F, -0.020F);

    // --- 8. NHÁNH MỞ RỘNG 3: Nhất Kiếm Tất Sát (Iaido Blink Snap Pose) ---
    private static final KeyPose KP_IAIDO_BLINK_POSE = new KeyPose(
            -0.0750F, +0.105F, +0.663F, +0.000F, +0.000F, -0.524F,
            -0.489F, +0.454F, +0.24F, +0.22F,
            +0.480F, +0.720F, +0.440F, -0.260F, +0.000F, -0.100F, +0.00F, +0.10F,
            +1.241F, +0.042F, +0.132F, -1.294F, +0.000F, -0.010F, -0.01F, +0.00F,
            -87.54F, -38.16F, -62.23F, -0.040F);

    // --- 9. NHÁNH MỞ RỘNG 4: Không Chiến (Aerial 1 Launch -> Aerial 2 Hang -> Aerial 3 Meteor Slam) ---
    private static final KeyPose KP_AIR1_START = new KeyPose(
            -0.0625F, +0.175F, -0.524F, +0.000F, -0.105F, +0.384F,
            +0.524F, -0.314F, +0.65F, +0.72F,
            -0.340F, -0.340F, -0.060F, -0.820F, +0.000F, +0.220F, +0.40F, -0.40F,
            -1.184F, +1.126F, +0.021F, +0.000F, +0.000F, +0.130F, -0.80F, -1.08F,
            -98.14F, +18.09F, +34.34F, -0.050F);

    private static final KeyPose KP_AIR1_PEAK = new KeyPose(
            +0.0938F, -0.349F, -0.524F, +0.000F, -0.140F, +0.384F,
            -0.559F, +0.733F, +0.45F, +0.65F,
            -2.580F, +0.140F, +0.260F, -0.360F, +0.000F, -0.140F, +0.48F, -0.22F,
            -2.273F, +0.785F, -0.277F, -0.198F, +0.000F, +0.484F, +0.26F, -0.08F,
            -56.51F, -31.99F, -175.86F, -0.050F);

    private static final KeyPose KP_AIR2_START = new KeyPose(
            +0.0312F, +0.070F, +0.785F, +0.000F, -0.035F, -0.611F,
            -0.419F, +0.593F, +0.55F, +0.68F,
            -1.320F, +0.440F, +0.240F, -0.440F, +0.000F, -0.260F, +0.20F, -0.22F,
            -1.670F, +1.004F, +0.016F, +0.000F, +0.000F, +0.162F, -0.80F, -1.20F,
            -116.64F, -47.66F, -73.97F, -0.040F);

    private static final KeyPose KP_AIR2_END = new KeyPose(
            +0.0312F, +0.105F, -0.698F, +0.000F, -0.035F, +0.559F,
            +0.524F, -0.489F, +0.60F, +0.55F,
            -0.960F, -0.560F, -0.060F, -0.620F, +0.000F, +0.260F, +0.50F, -0.42F,
            -1.436F, +0.936F, +0.011F, +0.000F, +0.000F, +0.152F, -0.80F, -1.20F,
            -89.47F, +41.65F, -84.00F, -0.040F);

    private static final KeyPose KP_AIR3_DIVE = new KeyPose(
            +0.0625F, +0.524F, +0.000F, +0.000F, -0.262F, +0.000F,
            +0.698F, +0.436F, +0.80F, +0.72F,
            -2.880F, +0.180F, +0.340F, -0.340F, +0.000F, -0.140F, +0.60F, -0.15F,
            -2.488F, +0.367F, -0.650F, -0.714F, +0.000F, +0.650F, +0.35F, +0.09F,
            -74.98F, -28.38F, +178.62F, -0.050F);

    private static final KeyPose KP_AIR3_IMPACT = new KeyPose(
            -0.2375F, +0.559F, +0.000F, +0.000F, -0.279F, +0.000F,
            -0.803F, +0.733F, +0.90F, +0.78F,
            -0.220F, -0.240F, -0.060F, -0.860F, +0.000F, +0.100F, +0.45F, -0.45F,
            -1.162F, +0.976F, +0.128F, +0.000F, +0.000F, +0.110F, -0.49F, -0.86F,
            -136.81F, -1.31F, -167.43F, -0.050F);

    // --- 10. 5 THẾ ĐỠ KENJUTSU & PHẢN CÔNG (6 Guard KeyPoses Verified + Parry Counter) ---
    /** Thế 1: Uke-nagashi (Khung chữ V mở rộng chuẩn kiếm đạo — Body Y -0.8u, chân chạm khít mặt đất, RightArm -75°/Roll+15°, LeftArm -65° ôm Kashira, Tsuba ngang cổ/vai phải, lưỡi cong tự nhiên lên trên & cạnh sắc hướng ra ngoài địch, mặt lộ 100%!) */
    private static final KeyPose KP_GUARD_UKE_NAGASHI = new KeyPose(
            -0.0500F, +0.1047F, +0.3840F, +0.000F, -0.0698F, +0.0000F,
            +0.2793F, -0.3142F, +0.0000F, +0.3142F,
            -1.3090F, -0.1745F, +0.2618F, -0.1396F, +0.000F, -0.0698F, +0.00F, -0.80F,
            -1.1345F, +0.7679F, -0.1745F, -0.1745F, +0.000F, +0.0698F, -0.40F, -0.90F,
            -28.44F, -52.35F, +136.31F, -0.080F);

    /** Thế 2: Ten-no-uke / Kasa-uke (Đỡ Trên Cao — Ô Che Mưa) — Sống kiếm Mune hướng thẳng lên trời đón đòn bổ! */
    private static final KeyPose KP_GUARD_KASA_UKE = new KeyPose(
            -0.1562F, -0.140F, +0.175F, +0.000F, -0.209F, -0.140F,
            +0.524F, -0.454F, +0.54F, +0.56F,
            -2.380F, -0.440F, +0.100F, -0.520F, +0.000F, -0.460F, +1.15F, -0.52F,
            -2.645F, +1.450F, -0.330F, +0.000F, +0.000F, +0.189F, -0.80F, -1.10F,
            -167.94F, +34.06F, -150.13F, -0.040F);

    /** Thế 3A: Suriage Parry — GĐ 1 (0.00–0.06s): Hạ thấp hai tay về hông trái, mũi kiếm hướng chếch lên! */
    private static final KeyPose KP_SURIAGE_LOW = new KeyPose(
            -0.1250F, +0.175F, -0.489F, +0.000F, -0.105F, +0.384F,
            +0.489F, -0.454F, +0.46F, +0.48F,
            -0.620F, -0.440F, -0.060F, -0.660F, +0.000F, +0.160F, +0.44F, -0.44F,
            -0.956F, +0.978F, +0.002F, +0.000F, +0.000F, +0.138F, -0.78F, -1.20F,
            -38.97F, +2.84F, -103.06F, -0.040F);

    /** Thế 3B: Suriage Parry — GĐ 2 (0.06–0.20s): Xoay giật mạnh phải (+35°), hất chéo từ dưới trái lên trên phải! */
    private static final KeyPose KP_SURIAGE_UP = new KeyPose(
            -0.0750F, +0.105F, +0.611F, +0.000F, -0.070F, -0.489F,
            -0.524F, +0.559F, +0.34F, +0.30F,
            -1.660F, +0.260F, +0.260F, -0.400F, +0.000F, -0.220F, +0.36F, -0.44F,
            -1.777F, +0.903F, -0.047F, +0.000F, +0.000F, +0.126F, -0.76F, -1.20F,
            -70.54F, -51.42F, +38.33F, -0.040F);

    /** Thế 4: Mune-oshi (Đỡ Một Tay Đẩy Sống Kiếm) — Sụp sâu -3.5u, bàn tay trái áp chặt sống lưng Mune trợ lực! */
    private static final KeyPose KP_GUARD_MUNE_OSHI = new KeyPose(
            -0.2188F, +0.349F, -0.140F, +0.000F, -0.140F, +0.105F,
            +0.663F, -0.593F, +0.74F, +0.78F,
            -0.820F, -0.260F, +0.180F, -0.560F, +0.000F, -0.480F, +0.44F, -0.52F,
            -1.798F, +0.497F, -0.033F, +0.000F, +0.000F, +0.157F, -0.80F, -1.20F,
            -1.99F, +7.48F, +126.91F, -0.040F);

    /** Thế 5: Saya-uke (Đẩy vỏ sang sườn trái Pos X +1.0, nghiêng chéo ~40° cong tự nhiên lên trên qua ngực dưới cách đầu >4.5px, mặt & tóc mái lộ 100%, chân chạm khít mặt đất Y -0.8u!) */
    private static final KeyPose KP_GUARD_SAYA_UKE = new KeyPose(
            -0.0500F, -0.0349F, +0.3840F, +0.000F, +0.0349F, +0.0000F,
            +0.2793F, -0.3142F, +0.0000F, +0.3142F,
            -0.7854F, -0.3840F, +0.2618F, -0.1745F, +0.000F, -0.0698F, +0.00F, -0.20F,
            -1.0472F, -0.2618F, -0.4363F, -0.9774F, +0.000F, +0.1745F, +1.00F, -0.80F,
            +17.94F, +36.13F, -141.08F, +0.000F);

    private static final KeyPose KP_COUNTER_MID = new KeyPose(
            -0.0938F, +0.209F, +0.244F, +0.000F, -0.105F, -0.175F,
            -0.524F, +0.489F, +0.42F, +0.36F,
            -1.220F, -0.100F, +0.160F, -0.380F, +0.000F, -0.140F, +0.20F, -0.45F,
            -1.438F, +0.942F, +0.002F, +0.000F, +0.000F, +0.103F, -0.40F, -0.78F,
            -83.03F, -24.43F, +50.33F, -0.040F);

    private static final KeyPose KP_COUNTER_END = new KeyPose(
            -0.0625F, +0.175F, +0.785F, +0.000F, -0.070F, -0.628F,
            -0.663F, +0.593F, +0.34F, +0.28F,
            -1.880F, +0.340F, +0.260F, -0.480F, +0.000F, -0.160F, +0.30F, -0.25F,
            -2.019F, +1.087F, +0.003F, +0.000F, +0.000F, +0.126F, -0.58F, -0.81F,
            -113.98F, -48.51F, +68.90F, -0.040F);

    // --- 11. Chạy nước rút (`Sprint`) ---
    private static final float SPRINT_P = 147.63F, SPRINT_R = -22.62F, SPRINT_Y = 170.58F;
    private static final float SPRINT_OFF_X = -0.0300F, SPRINT_OFF_Y = 0.0295F, SPRINT_OFF_Z = 0.0490F;

    private static final class KatanaAnimState {
        float prevStanceBlend = 0.0F;
        float stanceBlend = 0.0F;
        float prevDrawBlend = 0.0F;
        float drawBlend = 0.0F;
        float prevGuardBlend = 0.0F;
        float guardBlend = 0.0F;
        float prevGuardKasaBlend = 0.0F;
        float guardKasaBlend = 0.0F;
        float prevSayaGuardBlend = 0.0F;
        float sayaGuardBlend = 0.0F;
        float ukeSlideDipTicks = 0.0F;
        float prevIaidoChargeBlend = 0.0F;
        float iaidoChargeBlend = 0.0F;
        boolean iaidoChargingSynced = false;
        float prevSprintBlend = 0.0F;
        float sprintBlend = 0.0F;
        long sprintGraceUntilTick = -1000L;

        boolean active = false;
        KatanaMoveType move = KatanaMoveType.HIT_1_DRAW;
        boolean hit1FromSheathed = false;
        long startTick = -1000L;
        float durationTicks = 8.0F;
        float hitStopRemaining = 0.0F;
        long lastSeenTick = 0L;
        boolean playedChiburiCue = false;
        boolean playedNotoClick = false;
        boolean playedHit4Shake = false;
        boolean airSlamLanded = false;
        long airSlamLandTick = -1000L;

        final FullBodyPose sheathedPose = new FullBodyPose();
        final FullBodyPose drawnPose = new FullBodyPose();
        final FullBodyPose iaidoChargePose = new FullBodyPose();
        final FullBodyPose guardPose = new FullBodyPose();
        final FullBodyPose guardAltPose = new FullBodyPose();
        final FullBodyPose sprintPose = new FullBodyPose();
        final FullBodyPose basePose = new FullBodyPose();
        final FullBodyPose actionPose = new FullBodyPose();
        final FullBodyPose blendFrom = new FullBodyPose();
        final FullBodyPose lastPose = new FullBodyPose();
        final FullBodyPose outPose = new FullBodyPose();
        boolean hasBlendFrom = false;
    }

    private static final Map<Integer, KatanaAnimState> STATES = new HashMap<>();
    private static long clientTickCounter = 0L;

    private KatanaAnimManager() {}

    public static long getClientTick() {
        return clientTickCounter;
    }

    public static void clearAll() {
        STATES.clear();
    }

    public static boolean isHoldingKatana(@Nullable Player player) {
        return player != null && player.getMainHandItem().is(ModItems.KATANA);
    }

    public static boolean isMoveActive(int playerId, KatanaMoveType move) {
        KatanaAnimState st = STATES.get(playerId);
        return st != null && st.active && st.move == move;
    }

    public static boolean isAirSlamDiving(int playerId) {
        KatanaAnimState st = STATES.get(playerId);
        return st != null && st.active && st.move == KatanaMoveType.AIR_3_SLAM && !st.airSlamLanded;
    }

    public static boolean isAirSlamLanded(int playerId) {
        KatanaAnimState st = STATES.get(playerId);
        return st != null && st.active && st.move == KatanaMoveType.AIR_3_SLAM && st.airSlamLanded;
    }

    public static boolean isHit1FromSheathed(int playerId) {
        KatanaAnimState st = STATES.get(playerId);
        return st != null && st.active && st.move == KatanaMoveType.HIT_1_DRAW && st.hit1FromSheathed;
    }

    public static void setIaidoCharging(int playerId, boolean charging) {
        KatanaAnimState st = STATES.computeIfAbsent(playerId, k -> new KatanaAnimState());
        st.iaidoChargingSynced = charging;
    }

    public static void triggerAirSlamLanded(int playerId) {
        KatanaAnimState st = STATES.get(playerId);
        if (st != null && st.active && st.move == KatanaMoveType.AIR_3_SLAM) {
            st.airSlamLanded = true;
            st.airSlamLandTick = clientTickCounter;
            st.startTick = clientTickCounter;
            st.durationTicks = 6.0F; // 0.30s giữ khung cắm kiếm phập xuống mặt đất + rút gọn!
        }
    }

    @Nullable
    public static FullBodyPose getLastComputedPose(int playerId) {
        KatanaAnimState st = STATES.get(playerId);
        return (st != null && (st.stanceBlend > 0.002F || st.prevStanceBlend > 0.002F || st.active)) ? st.lastPose : null;
    }

    public static float getIaidoChargeBlend(int playerId, float partialTick) {
        KatanaAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        return smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevIaidoChargeBlend, st.iaidoChargeBlend), 0.0F, 1.0F));
    }

    public static float getDrawBlend(int playerId, float partialTick) {
        KatanaAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        if (st.active && st.move == KatanaMoveType.HIT_1_DRAW) {
            if (!st.hit1FromSheathed) {
                return 1.0F;
            }
            float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
            if (alpha < 0.30F) {
                return smootherstep(alpha / 0.30F) * 0.09F;
            }
            if (alpha < 0.45F) {
                return Mth.lerp(easeOutExpo((alpha - 0.30F) / 0.15F), 0.09F, 1.0F);
            }
            return 1.0F;
        }
        if (st.active && st.move == KatanaMoveType.IAIDO_BLINK) {
            float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
            if (alpha < 0.50F) {
                return 1.0F;
            }
            if (alpha < 0.90F) {
                return Mth.lerp(smootherstep((alpha - 0.50F) / 0.40F), 1.0F, 0.085F);
            }
            return Mth.lerp(smootherstep((alpha - 0.90F) / 0.10F), 0.085F, 0.0F);
        }
        if (st.active && st.move == KatanaMoveType.CHIBURI_NOTO) {
            float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
            if (alpha < 0.611F) {
                return 1.0F;
            }
            if (alpha < 0.889F) {
                return Mth.lerp(smootherstep((alpha - 0.611F) / 0.278F), 1.0F, 0.085F);
            }
            return Mth.lerp(smootherstep(Mth.clamp((alpha - 0.889F) / 0.095F, 0.0F, 1.0F)), 0.085F, 0.0F);
        }
        if (st.active && st.move == KatanaMoveType.GUARD_SAYA_UKE_HIT) {
            return 0.0F;
        }
        float saya = getSayaGuardBlend(playerId, partialTick);
        if (saya > 0.20F) {
            return 0.0F; // Saya-uke: toàn bộ thanh kiếm nằm nguyên trong vỏ chắn chéo trước ngực!
        }
        float charge = getIaidoChargeBlend(playerId, partialTick);
        if (charge > 0.02F) {
            return charge * 0.095F; // Ngón cái đẩy lộ 1.5 units lưỡi kiếm sáng loáng khỏi miệng vỏ Koiguchi!
        }
        return smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevDrawBlend, st.drawBlend), 0.0F, 1.0F));
    }

    public static float getBladeRevealFraction(int playerId, float partialTick) {
        KatanaAnimState st = STATES.get(playerId);
        if (st == null) {
            return 1.0F;
        }
        if (st.active && st.move == KatanaMoveType.GUARD_SAYA_UKE_HIT) {
            return 0.0F;
        }
        if (!st.active) {
            if (getSayaGuardBlend(playerId, partialTick) > 0.20F) {
                return 0.0F;
            }
            float charge = getIaidoChargeBlend(playerId, partialTick);
            return charge > 0.02F ? (0.095F * charge) : 1.0F;
        }
        float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
        if (st.move == KatanaMoveType.HIT_1_DRAW && st.hit1FromSheathed) {
            if (alpha < 0.30F) {
                return smootherstep(alpha / 0.30F) * 0.09F;
            }
            if (alpha < 0.45F) {
                return Mth.lerp(easeOutExpo((alpha - 0.30F) / 0.15F), 0.09F, 1.00F);
            }
            return 1.0F;
        }
        if (st.move == KatanaMoveType.IAIDO_BLINK) {
            if (alpha < 0.50F) {
                return 1.0F;
            }
            if (alpha < 0.90F) {
                return Mth.lerp(smootherstep((alpha - 0.50F) / 0.40F), 0.99F, 0.085F);
            }
            return Mth.lerp(smootherstep((alpha - 0.90F) / 0.10F), 0.085F, 0.0F);
        }
        if (st.move == KatanaMoveType.CHIBURI_NOTO) {
            if (alpha < 0.611F) {
                return 1.0F;
            }
            if (alpha < 0.889F) {
                return Mth.lerp(smootherstep((alpha - 0.611F) / 0.278F), 0.99F, 0.085F);
            }
            return Mth.lerp(smootherstep(Mth.clamp((alpha - 0.889F) / 0.095F, 0.0F, 1.0F)), 0.085F, 0.0F);
        }
        return 1.0F;
    }

    public static float getHipScabbardPitchDeltaDeg(int playerId, float partialTick) {
        KatanaAnimState st = STATES.get(playerId);
        if (st == null || !st.active) {
            return 0.0F;
        }
        float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
        if (st.move == KatanaMoveType.HIT_1_DRAW && st.hit1FromSheathed) {
            if (alpha < 0.72F) {
                return +40.0F;
            }
            return Mth.lerp(smootherstep((alpha - 0.72F) / 0.28F), +40.0F, 0.0F);
        }
        if (st.move == KatanaMoveType.IAIDO_BLINK) {
            if (alpha < 0.28F) {
                return +36.0F; // Vỏ hướng chếch ra sau cực ngầu sau cú Blink!
            }
            if (alpha < 0.50F) {
                return Mth.lerp(smootherstep((alpha - 0.28F) / 0.22F), +36.0F, -18.0F);
            }
            return -18.0F;
        }
        if (st.move == KatanaMoveType.CHIBURI_NOTO) {
            if (alpha >= 0.278F && alpha < 0.611F) {
                return Mth.lerp(smootherstep((alpha - 0.278F) / 0.333F), 0.0F, -20.0F);
            }
        }
        return 0.0F;
    }

    public static float getHipScabbardOffsetZ(int playerId, float partialTick) {
        KatanaAnimState st = STATES.get(playerId);
        if (st == null || !st.active) {
            return 0.0F;
        }
        float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
        if (st.move == KatanaMoveType.HIT_1_DRAW && st.hit1FromSheathed) {
            if (alpha < 0.72F) {
                return +0.12F;
            }
            return Mth.lerp(smootherstep((alpha - 0.72F) / 0.28F), +0.12F, 0.0F);
        }
        if (st.move == KatanaMoveType.IAIDO_BLINK) {
            return alpha < 0.28F ? +0.14F : -0.08F;
        }
        if (st.move == KatanaMoveType.CHIBURI_NOTO) {
            if (alpha >= 0.278F && alpha < 0.611F) {
                return Mth.lerp(smootherstep((alpha - 0.278F) / 0.333F), 0.0F, -0.10F);
            }
        }
        return 0.0F;
    }

    public static float getGuardBlend(int playerId, float partialTick) {
        KatanaAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        return smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevGuardBlend, st.guardBlend), 0.0F, 1.0F));
    }

    public static float getGuardKasaBlend(int playerId, float partialTick) {
        KatanaAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        return smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevGuardKasaBlend, st.guardKasaBlend), 0.0F, 1.0F));
    }

    public static float getSayaGuardBlend(int playerId, float partialTick) {
        KatanaAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        return smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevSayaGuardBlend, st.sayaGuardBlend), 0.0F, 1.0F));
    }

    public static float getUkeSlideDip(int playerId) {
        KatanaAnimState st = STATES.get(playerId);
        return st != null ? Mth.clamp(st.ukeSlideDipTicks / 4.0F, 0.0F, 1.0F) : 0.0F;
    }

    public static void triggerUkeNagashiHit(int playerId) {
        KatanaAnimState st = STATES.computeIfAbsent(playerId, k -> new KatanaAnimState());
        st.ukeSlideDipTicks = 4.0F; // Block Impact — 0.20s (4 ticks)!
    }

    public static float getSprintBlend(int playerId, float partialTick) {
        KatanaAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        return smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevSprintBlend, st.sprintBlend), 0.0F, 1.0F));
    }

    public static float getActionProgress(int playerId, float partialTick) {
        KatanaAnimState st = STATES.get(playerId);
        if (st == null || !st.active) {
            return 0.0F;
        }
        float elapsed = (clientTickCounter - st.startTick) + partialTick;
        float alpha = elapsed / Math.max(1.0F, st.durationTicks);
        return (alpha >= 0.0F && alpha < 1.0F) ? alpha : 0.0F;
    }

    public static KatanaMoveType getMoveType(int playerId) {
        KatanaAnimState st = STATES.get(playerId);
        return st != null ? st.move : KatanaMoveType.HIT_1_DRAW;
    }

    public static boolean shouldDockScabbardAtHip(Player player, float partialTick) {
        if (!isHoldingKatana(player)) {
            return false;
        }
        KatanaAnimState st = STATES.get(player.getId());
        if (st == null) {
            return KatanaItem.isDrawn(player.getMainHandItem());
        }
        if (st.active && st.move == KatanaMoveType.CHIBURI_NOTO) {
            float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
            return alpha < 0.611F;
        }
        if (st.active && st.move == KatanaMoveType.IAIDO_BLINK) {
            float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
            return alpha < 0.50F;
        }
        if (getBladeRevealFraction(player.getId(), partialTick) < 0.995F) {
            return false;
        }
        return getDrawBlend(player.getId(), partialTick) >= 0.72F;
    }

    public static float getGlowBoost(int playerId, float partialTick) {
        KatanaAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        if (st.active && (st.move == KatanaMoveType.DEFLECT || st.move == KatanaMoveType.PARRY_COUNTER
                || st.move == KatanaMoveType.IAIDO_BLINK || st.move == KatanaMoveType.THRUST
                || st.move == KatanaMoveType.HIT_1_DRAW || st.move == KatanaMoveType.HIT_4_FINISHER
                || st.move == KatanaMoveType.AIR_3_SLAM)) {
            return 1.0F;
        }
        float charge = getIaidoChargeBlend(playerId, partialTick);
        float guard = Mth.clamp(Mth.lerp(partialTick, st.prevGuardBlend, st.guardBlend), 0.0F, 1.0F);
        return Math.max(charge * 0.95F, guard * 0.65F);
    }

    public static void triggerMove(int playerId, KatanaMoveType move, float durationTicks) {
        triggerMove(playerId, move, durationTicks, move == KatanaMoveType.HIT_1_DRAW);
    }

    public static void triggerMove(int playerId, KatanaMoveType move, float durationTicks, boolean fromSheathed) {
        KatanaAnimState st = STATES.computeIfAbsent(playerId, k -> new KatanaAnimState());
        st.blendFrom.copyFrom(st.lastPose);
        st.hasBlendFrom = (st.stanceBlend > 0.05F) && move != KatanaMoveType.IAIDO_BLINK;
        st.active = true;
        st.move = move;
        st.hit1FromSheathed = (move == KatanaMoveType.HIT_1_DRAW) && fromSheathed;
        st.startTick = clientTickCounter;
        st.durationTicks = Math.max(4.0F, durationTicks);
        st.hitStopRemaining = 0.0F;
        st.lastSeenTick = clientTickCounter;
        st.playedChiburiCue = false;
        st.playedNotoClick = false;
        st.playedHit4Shake = false;
        st.airSlamLanded = false;
        st.airSlamLandTick = -1000L;
        if (move == KatanaMoveType.IAIDO_BLINK) {
            st.iaidoChargingSynced = false;
            st.iaidoChargeBlend = 0.0F;
            st.prevIaidoChargeBlend = 0.0F;
            st.drawBlend = 1.0F;
            st.prevDrawBlend = 1.0F;
        } else if (move == KatanaMoveType.GUARD_SAYA_UKE_HIT) {
            st.drawBlend = 0.0F;
            st.prevDrawBlend = 0.0F;
        } else if (st.hit1FromSheathed) {
            st.drawBlend = 0.0F;
            st.prevDrawBlend = 0.0F;
        } else if (move == KatanaMoveType.CHIBURI_NOTO) {
            st.drawBlend = 1.0F;
            st.prevDrawBlend = 1.0F;
        } else {
            st.drawBlend = 1.0F;
            st.prevDrawBlend = Math.max(st.prevDrawBlend, 0.85F);
        }
    }

    public static void triggerHitStop(int playerId, float freezeTicks) {
        KatanaAnimState st = STATES.get(playerId);
        if (st != null && st.active) {
            st.hitStopRemaining = Math.max(st.hitStopRemaining, Mth.clamp(freezeTicks, 0.5F, 2.5F));
        }
    }

    public static void tickClient(Minecraft mc) {
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        clientTickCounter++;

        for (Player player : mc.level.players()) {
            boolean holding = isHoldingKatana(player);
            KatanaAnimState st = STATES.get(player.getId());
            if (!holding && st == null) {
                continue;
            }
            if (st == null) {
                st = new KatanaAnimState();
                boolean initDrawn = KatanaClientState.isDrawn(player.getId(), player.getMainHandItem());
                st.drawBlend = initDrawn ? 1.0F : 0.0F;
                st.prevDrawBlend = st.drawBlend;
                STATES.put(player.getId(), st);
            }
            st.lastSeenTick = clientTickCounter;
            st.prevStanceBlend = st.stanceBlend;
            st.prevDrawBlend = st.drawBlend;
            st.prevGuardBlend = st.guardBlend;
            st.prevGuardKasaBlend = st.guardKasaBlend;
            st.prevSayaGuardBlend = st.sayaGuardBlend;
            st.prevIaidoChargeBlend = st.iaidoChargeBlend;
            st.prevSprintBlend = st.sprintBlend;
            if (st.ukeSlideDipTicks > 0.0F) {
                st.ukeSlideDipTicks = Math.max(0.0F, st.ukeSlideDipTicks - 1.0F);
            }

            ItemStack main = player.getMainHandItem();
            if (!holding && (main.is(ModItems.DUAL_KNIFE) || main.is(ModItems.TOGETSU) || main.is(ModItems.EXCALIBUR))) {
                st.stanceBlend = 0.0F;
                st.prevStanceBlend = 0.0F;
                st.guardBlend = 0.0F;
                st.prevGuardBlend = 0.0F;
                st.guardKasaBlend = 0.0F;
                st.prevGuardKasaBlend = 0.0F;
                st.sayaGuardBlend = 0.0F;
                st.prevSayaGuardBlend = 0.0F;
                st.iaidoChargeBlend = 0.0F;
                st.prevIaidoChargeBlend = 0.0F;
                st.iaidoChargingSynced = false;
                st.sprintBlend = 0.0F;
                st.prevSprintBlend = 0.0F;
                st.active = false;
                st.hasBlendFrom = false;
                continue;
            }

            boolean stanceActive = holding && !player.isSleeping() && !player.isSwimming() && !player.isFallFlying();
            st.stanceBlend = stanceActive
                    ? Math.min(1.0F, st.stanceBlend + 0.25F)
                    : Math.max(0.0F, st.stanceBlend - 0.22F);

            boolean isDrawnSynced = KatanaClientState.isDrawn(player.getId(), main);
            boolean usingMain = holding && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND;

            // Thế 5 (Saya-uke): Giữ RMB khi chưa rút kiếm -> Nhấc vỏ chắn chéo ngực (kể cả khi đang tụ lực Iaido!)
            boolean sayaGuardNow = holding && usingMain && !isDrawnSynced
                    && (!st.active || st.move == KatanaMoveType.GUARD_SAYA_UKE_HIT);
            st.sayaGuardBlend = sayaGuardNow
                    ? Math.min(1.0F, st.sayaGuardBlend + 0.34F)
                    : Math.max(0.0F, st.sayaGuardBlend - 0.28F);

            boolean iaidoChargingNow = holding && !st.active && st.iaidoChargingSynced && !isDrawnSynced;
            st.iaidoChargeBlend = iaidoChargingNow
                    ? Math.min(1.0F, st.iaidoChargeBlend + 0.28F)
                    : Math.max(0.0F, st.iaidoChargeBlend - 0.30F);

            boolean guarding = holding && usingMain && isDrawnSynced;
            if (guarding && st.active && st.move == KatanaMoveType.CHIBURI_NOTO) {
                st.active = false;
                st.hasBlendFrom = false;
            }
            st.guardBlend = guarding
                    ? Math.min(1.0F, st.guardBlend + 0.34F)
                    : Math.max(0.0F, st.guardBlend - 0.26F);

            boolean kasaActive = guarding && player.getXRot() < -30.0F;
            st.guardKasaBlend = kasaActive
                    ? Math.min(1.0F, st.guardKasaBlend + 0.36F)
                    : Math.max(0.0F, st.guardKasaBlend - 0.26F);

            boolean drawnNow = holding && !sayaGuardNow && !iaidoChargingNow
                    && (guarding
                    || (st.active && st.move != KatanaMoveType.CHIBURI_NOTO
                        && st.move != KatanaMoveType.IAIDO_BLINK && st.move != KatanaMoveType.GUARD_SAYA_UKE_HIT)
                    || isDrawnSynced);
            st.drawBlend = drawnNow
                    ? Math.min(1.0F, st.drawBlend + 0.26F)
                    : Math.max(0.0F, st.drawBlend - 0.16F);

            boolean movingFast = player.walkAnimation.speed() > 0.18F
                    || player.getDeltaMovement().horizontalDistanceSqr() > 0.006;
            boolean sprintingNow = holding && player.isSprinting() && !guarding && !sayaGuardNow && !iaidoChargingNow
                    && !player.isCrouching() && !player.isPassenger() && !st.active;
            if (sprintingNow) {
                st.sprintGraceUntilTick = clientTickCounter + 6L;
            }
            boolean sprintActive = holding && !guarding && !sayaGuardNow && !iaidoChargingNow && !player.isCrouching()
                    && !player.isPassenger() && !st.active
                    && (sprintingNow || (clientTickCounter <= st.sprintGraceUntilTick && movingFast));
            st.sprintBlend = sprintActive
                    ? Math.min(1.0F, st.sprintBlend + 0.18F)
                    : Math.max(0.0F, st.sprintBlend - 0.16F);

            if (st.hitStopRemaining > 0.0F) {
                st.hitStopRemaining = Math.max(0.0F, st.hitStopRemaining - 1.0F);
                st.startTick++;
            }

            if (st.active) {
                float elapsed = clientTickCounter - st.startTick;
                float alpha = Mth.clamp(elapsed / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);

                // Local landing detection fallback for AIR_3_SLAM (instant snap without waiting for packet!)
                if (st.move == KatanaMoveType.AIR_3_SLAM && !st.airSlamLanded && player.onGround() && elapsed >= 1.0F) {
                    st.airSlamLanded = true;
                    st.airSlamLandTick = clientTickCounter;
                    st.startTick = clientTickCounter;
                    st.durationTicks = 6.0F;
                    if (player == mc.player) {
                        ClientRpgState.triggerShake(0.75F, 5);
                    }
                    elapsed = 0.0F;
                }

                if (st.move == KatanaMoveType.HIT_4_FINISHER && holding && !st.playedHit4Shake && alpha >= 0.45F) {
                    st.playedHit4Shake = true;
                    if (player == mc.player) {
                        ClientRpgState.triggerShake(0.42F, 4);
                    }
                }

                if (st.move == KatanaMoveType.CHIBURI_NOTO && holding) {
                    if (!st.playedChiburiCue && alpha >= 0.09F) {
                        st.playedChiburiCue = true;
                        mc.level.playLocalSound(player.getX(), player.getY() + 1.1, player.getZ(),
                                ModSounds.KATANA_SLASH, SoundSource.PLAYERS, 0.50F, 1.66F, false);
                        Vec3 anchor = player.position().add(0.0, player.getBbHeight() * 0.64, 0.0);
                        KatanaVfxRenderer.spawnSlashArc(player.getId(), anchor,
                                player.getYRot(), player.getXRot(), KatanaMoveType.CHIBURI_NOTO, false, clientTickCounter);
                        spawnChiburiDroplets(player, clientTickCounter);
                    }
                    if (!st.playedNotoClick && alpha >= 0.94F) {
                        st.playedNotoClick = true;
                        mc.level.playLocalSound(player.getX(), player.getY() + 1.0, player.getZ(),
                                ModSounds.KATANA_SHEATH, SoundSource.PLAYERS, 1.10F, 1.18F, false);
                    }
                }

                if (elapsed >= st.durationTicks || !holding) {
                    if (st.move == KatanaMoveType.CHIBURI_NOTO || st.move == KatanaMoveType.IAIDO_BLINK) {
                        st.drawBlend = 0.0F;
                        st.prevDrawBlend = 0.0F;
                    }
                    st.active = false;
                    st.hasBlendFrom = false;
                    st.airSlamLanded = false;
                }
            }
        }

        Iterator<Map.Entry<Integer, KatanaAnimState>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            KatanaAnimState st = it.next().getValue();
            if ((clientTickCounter - st.lastSeenTick) > 60L
                    || (st.stanceBlend <= 0.001F && st.prevStanceBlend <= 0.001F && !st.active)) {
                it.remove();
            }
        }
    }

    private static void spawnChiburiDroplets(Player player, long tick) {
        float yawRad = player.yBodyRot * Mth.DEG_TO_RAD;
        double fwdX = -Mth.sin(yawRad);
        double fwdZ = Mth.cos(yawRad);
        double rightX = -Mth.cos(yawRad);
        double rightZ = -Mth.sin(yawRad);
        Vec3 base = player.position().add(rightX * 0.68 + fwdX * 0.55, 1.25, rightZ * 0.68 + fwdZ * 0.55);
        for (int i = 0; i < 8; i++) {
            float u = i / 7.0F;
            boolean crimson = (i % 3 != 0);
            ExcaliburVfxRenderer.spawnSparkle(
                    base.x + rightX * (u * 0.55),
                    base.y - u * 0.42,
                    base.z + rightZ * (u * 0.55),
                    (float) (rightX * (0.09 + u * 0.06) + fwdX * 0.03),
                    -0.06F - u * 0.04F,
                    (float) (rightZ * (0.09 + u * 0.06) + fwdZ * 0.03),
                    crimson ? 0.075F : 0.065F,
                    crimson ? 215 : 230,
                    crimson ? 36 : 245,
                    crimson ? 42 : 255,
                    tick, 7.0F);
        }
    }

    @Nullable
    public static FullBodyPose evaluatePose(Player player, float partialTick, float limbSwingAmount, float ageInTicks) {
        KatanaAnimState st = STATES.get(player.getId());
        if (st == null) {
            return null;
        }
        float stance = Mth.clamp(Mth.lerp(partialTick, st.prevStanceBlend, st.stanceBlend), 0.0F, 1.0F);
        if (stance <= 0.002F && !st.active) {
            return null;
        }

        float draw = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevDrawBlend, st.drawBlend), 0.0F, 1.0F));
        float guard = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevGuardBlend, st.guardBlend), 0.0F, 1.0F));
        float guardKasa = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevGuardKasaBlend, st.guardKasaBlend), 0.0F, 1.0F));
        float sayaGuard = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevSayaGuardBlend, st.sayaGuardBlend), 0.0F, 1.0F));
        float iaidoCharge = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevIaidoChargeBlend, st.iaidoChargeBlend), 0.0F, 1.0F));
        float sprint = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevSprintBlend, st.sprintBlend), 0.0F, 1.0F));
        float walkSpeed = player.walkAnimation.speed(partialTick);

        fillKeyPose(st.sheathedPose, KP_SHEATHED_IDLE, 1.0F, 0.0F);
        fillKeyPose(st.drawnPose, KP_CHUDAN_STANCE, 1.0F, 0.85F);
        st.basePose.copyFrom(st.sheathedPose);
        if (draw > 0.001F) {
            st.basePose.blendFrom(st.drawnPose, 1.0F - draw);
        }

        // Nhịp thở tự nhiên khi giữ thế đỡ (1.5s = 30 ticks, EaseInOutSine) giúp dáng thủ luôn sống động!
        float guardBreath = 0.5F - 0.5F * Mth.cos(((ageInTicks % 30.0F) / 30.0F) * Mth.TWO_PI);

        // Thế 5: Saya-uke (Đẩy vỏ sang sườn trái Pos X +1.0, chéo gọn qua ngực dưới cách đầu >4.5px, mặt & tóc mái lộ 100%, hai chân chạm khít mặt đất!)
        float sheathedBlock = Math.max(sayaGuard, iaidoCharge);
        if (sheathedBlock > 0.001F) {
            fillKeyPose(st.iaidoChargePose, KP_GUARD_SAYA_UKE, 1.0F, 1.0F);
            st.iaidoChargePose.rightArmPosY = +1.00F;
            st.iaidoChargePose.leftArmPosY = -0.50F;
            st.iaidoChargePose.leftLegY = -0.1396F;   // LeftLeg Yaw -8°
            st.iaidoChargePose.rightLegY = +0.2618F;  // RightLeg Yaw +15°
            st.iaidoChargePose.leftLegZ = -0.0349F;
            st.iaidoChargePose.rightLegZ = +0.0349F;
            st.iaidoChargePose.leftLegPosY = -0.50F;  // Đế giày trước chạm khít mặt đất Y = 0.00!
            st.iaidoChargePose.rightLegPosY = -0.35F; // Đế giày sau chạm khít mặt đất Y = 0.00!
            st.iaidoChargePose.leftLegPosZ = -1.50F;
            st.iaidoChargePose.rightLegPosZ = +1.50F;
            // Nhịp thở nhẹ 1.5s đồng bộ thân trên (không nhấc gót chân & không tách tay khỏi chuôi/vỏ!)
            st.iaidoChargePose.bodyX += 0.014F * guardBreath;
            st.iaidoChargePose.rightArmPosY += 0.12F * guardBreath;
            st.iaidoChargePose.leftArmPosY += 0.12F * guardBreath;
            st.iaidoChargePose.fovOffsetDeg = -3.4F;
            st.basePose.blendFrom(st.iaidoChargePose, 1.0F - sheathedBlock);
        }

        if (sprint > 0.001F && draw > 0.5F) {
            fillUprightSprintPose(st.sprintPose);
            st.basePose.blendFrom(st.sprintPose, 1.0F - (sprint * draw));
        }

        // Thế 1 (Uke-nagashi chữ V mở rộng, Tsuba ngang cổ/vai phải, tay trái ôm Kashira, mặt lộ 100%) <-> Thế 2 (Kasa-uke khi ngước nhìn Pitch < -30°)
        if (guard > 0.001F) {
            fillKeyPose(st.guardPose, KP_GUARD_UKE_NAGASHI, 1.0F, 1.0F);
            st.guardPose.leftArmPosY = +0.40F;
            st.guardPose.leftLegY = -0.1396F;         // LeftLeg Yaw -8°
            st.guardPose.rightLegY = +0.2618F;        // RightLeg Yaw +15°
            st.guardPose.leftLegZ = -0.0349F;
            st.guardPose.rightLegZ = +0.0349F;
            st.guardPose.leftLegPosY = -0.50F;        // Đế giày trước chạm khít mặt đất Y = 0.00!
            st.guardPose.rightLegPosY = -0.35F;       // Đế giày sau chạm khít mặt đất Y = 0.00!
            st.guardPose.leftLegPosZ = -1.50F;        // LeftLeg Position Z: -1.5
            st.guardPose.rightLegPosZ = +1.50F;       // RightLeg Position Z: +1.5
            // Nhịp thở nhẹ 1.5s đồng bộ 2 tay cùng thân trên (không nhấc gót & không bung tay khỏi Kashira!)
            st.guardPose.bodyX += 0.014F * guardBreath;
            st.guardPose.rightArmPosY += 0.12F * guardBreath;
            st.guardPose.leftArmPosY += 0.12F * guardBreath;
            // Chuyển động "Nhún đỡ" (Block Impact — 0.20s / 4 ticks):
            // 2 frames đầu giật lùi Body Z +1.0u & ép 2 tay + kiếm sát vào người thêm 5°, sau đó bật nảy trở lại!
            if (st.ukeSlideDipTicks > 0.01F) {
                float u = 1.0F - Mth.clamp(st.ukeSlideDipTicks / 4.0F, 0.0F, 1.0F);
                float impact = (u < 0.50F)
                        ? easeOutQuart(u / 0.50F)
                        : (1.0F - smootherstep((u - 0.50F) / 0.50F));
                st.guardPose.rootTranslateZ += impact * (1.0F / 16.0F); // Body giật lùi Position Z +1.0 unit!
                st.guardPose.bodyX += impact * 0.052F;
                st.guardPose.rightArmX += impact * 0.0873F;             // Ép sát vào người thêm +5°!
                st.guardPose.leftArmX += impact * 0.0873F;
                st.guardPose.rightArmPosZ += impact * 0.55F;
                st.guardPose.leftArmPosZ += impact * 0.55F;
                st.guardPose.rightBladePitchDeg += impact * 5.0F;
                st.guardPose.rightBladeRollDeg += impact * 6.0F;
            }
            if (guardKasa > 0.001F) {
                fillKeyPose(st.guardAltPose, KP_GUARD_KASA_UKE, 1.0F, 0.94F);
                st.guardPose.blendFrom(st.guardAltPose, 1.0F - guardKasa);
            }
            st.guardPose.fovOffsetDeg = -3.0F;
            st.basePose.blendFrom(st.guardPose, 1.0F - guard);
        }

        FullBodyPose out = st.outPose;
        if (st.active) {
            float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
            fillActionPose(st.actionPose, st.move, alpha, st.hit1FromSheathed, st.airSlamLanded, ageInTicks);
            out.copyFrom(st.actionPose);

            if (st.move == KatanaMoveType.CHIBURI_NOTO || st.move == KatanaMoveType.IAIDO_BLINK) {
                if (alpha < 0.06F && st.hasBlendFrom) {
                    out.blendFrom(st.blendFrom, smootherstep(alpha / 0.06F));
                } else if (alpha > 0.94F) {
                    out.blendFrom(st.sheathedPose, 1.0F - smootherstep((alpha - 0.94F) / 0.06F));
                }
            } else {
                if (alpha < 0.08F && st.hasBlendFrom) {
                    out.blendFrom(st.blendFrom, smootherstep(alpha / 0.08F));
                } else if (st.move.isGuardReaction() && alpha > 0.84F) {
                    out.blendFrom(st.basePose, 1.0F - smootherstep((alpha - 0.84F) / 0.16F));
                }
            }
            out.isAttacking = true;
            out.overrideVanilla = true;
        } else {
            out.copyFrom(st.basePose);
            out.isAttacking = false;
            boolean guardOverride = (guard > 0.05F || sheathedBlock > 0.05F);
            out.overrideVanilla = guardOverride;
            out.armWeight = guardOverride ? 1.0F : smootherstep(stance);
            out.bodyX *= out.armWeight;
            out.bodyY *= out.armWeight;
            out.bodyZ *= out.armWeight;
            out.headAddX *= out.armWeight;
            out.headAddY *= out.armWeight;
            out.headAddZ *= out.armWeight;
            out.rightArmPosX *= out.armWeight;
            out.rightArmPosY *= out.armWeight;
            out.rightArmPosZ *= out.armWeight;
            out.leftArmPosX *= out.armWeight;
            out.leftArmPosY *= out.armWeight;
            out.leftArmPosZ *= out.armWeight;
            if (guardOverride) {
                // Ưu tiên OVERRIDE cao nhất khi giữ Chuột Phải: tắt hoàn toàn Vanilla Arm/Leg Bobbing lúc đứng yên!
                float standFactor = (walkSpeed < 0.06F) ? 1.0F : (1.0F - Mth.clamp((walkSpeed - 0.06F) * 2.2F, 0.0F, 1.0F));
                out.legWeight = Math.max(0.65F, standFactor);
            } else {
                float standFactor = 1.0F - Mth.clamp(walkSpeed * 2.5F, 0.0F, 1.0F);
                out.legWeight = smootherstep(stance) * standFactor * (1.0F - sprint);
            }
            out.kneeWeight *= out.legWeight;
            out.rootTranslateY *= out.legWeight;
            out.rootTranslateZ *= out.armWeight;
            out.rightLegPosY *= out.legWeight;
            out.rightLegPosZ *= out.legWeight;
            out.leftLegPosY *= out.legWeight;
            out.leftLegPosZ *= out.legWeight;
        }

        st.lastPose.copyFrom(out);
        return out;
    }

    private static void fillActionPose(FullBodyPose p, KatanaMoveType move, float alpha,
                                       boolean hit1FromSheathed, boolean airSlamLanded, float ageInTicks) {
        p.reset();
        switch (move) {
            case HIT_1_DRAW -> {
                if (alpha < 0.30F) {
                    float u = smootherstep(alpha / 0.30F);
                    lerpKeyPose(p, hit1FromSheathed ? KP_SHEATHED_IDLE : KP_CHUDAN_STANCE, KP_H1_UNLOCK, u, 1.0F, 0.90F, 0.94F);
                } else if (alpha < 0.45F) {
                    float u = easeOutExpo((alpha - 0.30F) / 0.15F);
                    lerpKeyPose(p, KP_H1_UNLOCK, KP_H1_MID, u, 1.0F, 0.96F, 0.96F);
                    p.fovOffsetDeg = u * 2.4F;
                } else if (alpha < 0.60F) {
                    float u = easeOutExpo((alpha - 0.45F) / 0.15F);
                    lerpKeyPose(p, KP_H1_MID, KP_H1_END_HOLD, u, 1.0F, 0.96F, 0.96F);
                    p.firstPersonCameraRollDeg = Mth.sin(u * Mth.PI) * +2.4F;
                    p.fovOffsetDeg = 2.4F + Mth.sin(u * Mth.PI) * 1.4F;
                } else if (alpha < 0.85F) {
                    fillKeyPose(p, KP_H1_END_HOLD, 1.0F, 0.94F);
                    p.armWeight = 1.0F;
                    p.legWeight = 0.94F;
                } else {
                    float u = smootherstep((alpha - 0.85F) / 0.15F);
                    lerpKeyPose(p, KP_H1_END_HOLD, KP_CHUDAN_STANCE, u, 1.0F, 0.90F, 0.90F);
                }
            }
            case HIT_2_DOWN_DIAG -> {
                if (alpha < 0.286F) {
                    float u = smootherstep(alpha / 0.286F);
                    lerpKeyPose(p, KP_H1_END_HOLD, KP_H2_WINDUP, u, 1.0F, 0.92F, 0.94F);
                } else if (alpha < 0.571F) {
                    float s = easeOutExpo((alpha - 0.286F) / 0.285F);
                    if (s < 0.5F) {
                        lerpKeyPose(p, KP_H2_WINDUP, KP_H2_MID, s * 2.0F, 1.0F, 0.96F, 0.96F);
                    } else {
                        lerpKeyPose(p, KP_H2_MID, KP_H2_END, (s - 0.5F) * 2.0F, 1.0F, 0.96F, 0.96F);
                    }
                    p.firstPersonCameraRollDeg = Mth.sin(s * Mth.PI) * -2.6F;
                    p.fovOffsetDeg = Mth.sin(s * Mth.PI) * 2.6F;
                } else if (alpha < 0.829F) {
                    fillKeyPose(p, KP_H2_END, 1.0F, 0.96F);
                    p.armWeight = 1.0F;
                    p.legWeight = 0.96F;
                    float b = (alpha - 0.571F) / 0.258F;
                    if (b < 0.65F) {
                        float bounce = Mth.sin(b * 22.0F) * (float) Math.exp(-b * 6.5F);
                        p.rightBladePitchDeg += bounce * 4.5F;
                        p.rightElbowX += bounce * 0.05F;
                    }
                } else {
                    float u = smootherstep((alpha - 0.829F) / 0.171F);
                    lerpKeyPose(p, KP_H2_END, KP_CHUDAN_STANCE, u, 1.0F, 0.90F, 0.90F);
                }
            }
            case HIT_3_UP_DIAG -> {
                if (alpha < 0.229F) {
                    float u = smootherstep(alpha / 0.229F);
                    lerpKeyPose(p, KP_H2_END, KP_H3_DIP_FLIP, u, 1.0F, 0.98F, 0.96F);
                } else if (alpha < 0.514F) {
                    float s = easeOutExpo((alpha - 0.229F) / 0.285F);
                    if (s < 0.5F) {
                        lerpKeyPose(p, KP_H3_DIP_FLIP, KP_H3_MID, s * 2.0F, 1.0F, 0.96F, 0.96F);
                    } else {
                        lerpKeyPose(p, KP_H3_MID, KP_H3_END_HIGH, (s - 0.5F) * 2.0F, 1.0F, 0.92F, 0.96F);
                    }
                    p.firstPersonCameraRollDeg = Mth.sin(s * Mth.PI) * +2.8F;
                    p.fovOffsetDeg = Mth.sin(s * Mth.PI) * 2.8F;
                } else if (alpha < 0.800F) {
                    fillKeyPose(p, KP_H3_END_HIGH, 1.0F, 0.92F);
                    p.armWeight = 1.0F;
                    p.legWeight = 0.92F;
                } else {
                    float u = smootherstep((alpha - 0.800F) / 0.200F);
                    lerpKeyPose(p, KP_H3_END_HIGH, KP_CHUDAN_STANCE, u, 1.0F, 0.88F, 0.88F);
                }
            }
            case HIT_4_FINISHER -> {
                if (alpha < 0.327F) {
                    float u = smootherstep(alpha / 0.327F);
                    lerpKeyPose(p, KP_H3_END_HIGH, KP_H4_OVERHEAD, u, 1.0F, 0.88F, 0.95F);
                } else if (alpha < 0.545F) {
                    float s = easeOutQuart((alpha - 0.327F) / 0.218F);
                    if (s < 0.48F) {
                        lerpKeyPose(p, KP_H4_OVERHEAD, KP_H4_MID, s / 0.48F, 1.0F, 1.0F, 1.0F);
                    } else {
                        lerpKeyPose(p, KP_H4_MID, KP_H4_IMPACT, (s - 0.48F) / 0.52F, 1.0F, 1.0F, 1.0F);
                    }
                    p.fovOffsetDeg = Mth.sin(s * Mth.PI) * 5.6F;
                } else if (alpha < 0.727F) {
                    fillKeyPose(p, KP_H4_IMPACT, 1.0F, 1.0F);
                    p.armWeight = 1.0F;
                    p.legWeight = 1.0F;
                    float v = (alpha - 0.545F) / 0.182F;
                    float vibDeg = Mth.sin(v * Mth.TWO_PI * 2.5F) * (1.0F - v * 0.45F) * 2.2F;
                    float vibRad = vibDeg * Mth.DEG_TO_RAD;
                    p.rightBladePitchDeg += vibDeg;
                    p.rightArmX += vibRad;
                    p.leftArmX += vibRad;
                } else {
                    float u = smootherstep((alpha - 0.727F) / 0.273F);
                    lerpKeyPose(p, KP_H4_IMPACT, KP_CHUDAN_STANCE, u, 1.0F, 0.95F, 0.95F);
                }
            }
            case THRUST -> {
                // Nhánh 1 (0.50s / 10 ticks):
                // 0.00-0.18s (0.00..0.36): Thu chuôi sát hông phải (Yaw -45°, Body Y -2.5u)
                // 0.18-0.30s (0.36..0.60): Phóng người đâm một tay Katate-tsuki (EaseOutExpo, tay trái buông ra sau!)
                // 0.30-0.38s (0.60..0.76): Giữ mũi kiếm cắm tại điểm xa nhất 0.08s (Hitstop)
                // 0.38-0.50s (0.76..1.00): Thu tay phải & tay trái nắm lại chuôi về Chudan!
                if (alpha < 0.36F) {
                    float u = smootherstep(alpha / 0.36F);
                    lerpKeyPose(p, KP_CHUDAN_STANCE, KP_THRUST_COIL, u, 1.0F, 0.92F, 0.94F);
                    p.fovOffsetDeg = -2.2F * u;
                } else if (alpha < 0.60F) {
                    float u = easeOutExpo((alpha - 0.36F) / 0.24F);
                    lerpKeyPose(p, KP_THRUST_COIL, KP_THRUST_EXTEND, u, 1.0F, 0.96F, 0.96F);
                    p.fovOffsetDeg = Mth.lerp(u, -2.2F, +5.4F);
                } else if (alpha < 0.76F) {
                    float v = (alpha - 0.60F) / 0.16F;
                    fillKeyPose(p, KP_THRUST_EXTEND, 1.0F, 0.96F);
                    p.armWeight = 1.0F;
                    p.legWeight = 0.96F;
                    p.rightArmPosZ -= Mth.sin(v * Mth.TWO_PI * 2.0F) * 0.08F;
                    p.fovOffsetDeg = 5.4F * (1.0F - v * 0.35F);
                } else {
                    float u = smootherstep((alpha - 0.76F) / 0.24F);
                    lerpKeyPose(p, KP_THRUST_EXTEND, KP_CHUDAN_STANCE, u, 1.0F, 0.90F, 0.90F);
                }
            }
            case DELAY_SWEEP_360 -> {
                // Nhánh 2 (0.55s / 11 ticks):
                // Đòn A (0.00-0.25s = alpha 0.00..0.455): Sụp thấp Y -4.0u, quét ngang sát đất (Yaw +35° -> -55°)
                // Đòn B (0.25-0.55s = alpha 0.455..1.00): Bật dậy xoay tròn 360° trục Y (rootYawDeg: 0° -> -360°) quét AoE!
                if (alpha < 0.14F) {
                    float u = easeOutQuart(alpha / 0.14F);
                    lerpKeyPose(p, KP_H1_END_HOLD, KP_LOW_SWEEP_START, u, 1.0F, 0.96F, 0.96F);
                } else if (alpha < 0.455F) {
                    float u = easeOutExpo((alpha - 0.14F) / 0.315F);
                    lerpKeyPose(p, KP_LOW_SWEEP_START, KP_LOW_SWEEP_END, u, 1.0F, 0.98F, 0.98F);
                    p.firstPersonCameraRollDeg = Mth.sin(u * Mth.PI) * -3.5F;
                } else if (alpha < 0.90F) {
                    float spinT = (alpha - 0.455F) / 0.445F;
                    float rise = easeOutQuart(Mth.clamp(spinT / 0.25F, 0.0F, 1.0F));
                    lerpKeyPose(p, KP_LOW_SWEEP_END, KP_SPIN_360_HOLD, rise, 1.0F, 0.94F, 0.94F);
                    p.rootYawDeg = -360.0F * smootherstep(spinT);
                    p.fovOffsetDeg = Mth.sin(spinT * Mth.PI) * 5.0F;
                    p.firstPersonCameraRollDeg = Mth.sin(spinT * Mth.PI) * -4.2F;
                } else {
                    float u = smootherstep((alpha - 0.90F) / 0.10F);
                    lerpKeyPose(p, KP_SPIN_360_HOLD, KP_CHUDAN_STANCE, u, 1.0F, 0.88F, 0.88F);
                }
            }
            case IAIDO_BLINK -> {
                // Nhánh 3 (0.70s / 14 ticks sau thả nút = 0.40s..1.10s):
                // 0.00..0.24: Chuyển đột ngột sang dáng quay lưng duỗi kiếm chéo xuống phải (Pitch +30°, Yaw +45°)!
                // 0.24..0.52: Đưa mũi kiếm chạm cổ vỏ (Noto guide)
                // 0.52..0.90: Trượt chậm rãi vào vỏ
                // 0.90..1.00: Đóng chốt "tách" kích nổ sát thương trễ ở 1.05s!
                if (alpha < 0.24F) {
                    fillKeyPose(p, KP_IAIDO_BLINK_POSE, 1.0F, 0.94F);
                    p.armWeight = 1.0F;
                    p.legWeight = 0.94F;
                    p.fovOffsetDeg = 7.5F * (1.0F - alpha / 0.24F);
                } else if (alpha < 0.52F) {
                    float u = smootherstep((alpha - 0.24F) / 0.28F);
                    lerpKeyPose(p, KP_IAIDO_BLINK_POSE, KP_CHIB_P2_NOTO_TIP, u, 1.0F, 0.72F, 0.80F);
                } else if (alpha < 0.90F) {
                    float push = smootherstep((alpha - 0.52F) / 0.38F);
                    lerpKeyPose(p, KP_CHIB_P2_NOTO_TIP, KP_CHIB_P3A_PAUSE, push, 1.0F, 0.45F, 0.70F);
                } else {
                    float lock = smootherstep((alpha - 0.90F) / 0.10F);
                    lerpKeyPose(p, KP_CHIB_P3A_PAUSE, KP_SHEATHED_IDLE, lock, 1.0F, (1.0F - lock) * 0.25F, 0.65F);
                }
            }
            case AIR_1_LAUNCH -> {
                // Nhánh 4A (0.30s / 6 ticks): Ngả ra sau (-20°), xoay nhẹ -30°, chém vòng cung hất tung từ dưới lên đỉnh đầu!
                if (alpha < 0.22F) {
                    float u = easeOutQuart(alpha / 0.22F);
                    lerpKeyPose(p, KP_CHUDAN_STANCE, KP_AIR1_START, u, 1.0F, 0.90F, 0.92F);
                } else if (alpha < 0.74F) {
                    float u = easeOutExpo((alpha - 0.22F) / 0.52F);
                    lerpKeyPose(p, KP_AIR1_START, KP_AIR1_PEAK, u, 1.0F, 0.92F, 0.94F);
                    p.rootPitchDeg = -18.0F * Mth.sin(u * Mth.PI);
                    p.fovOffsetDeg = Mth.sin(u * Mth.PI) * 3.5F;
                } else {
                    fillKeyPose(p, KP_AIR1_PEAK, 1.0F, 0.90F);
                    p.armWeight = 1.0F;
                    p.legWeight = 0.90F;
                }
            }
            case AIR_2_MID_SWEEP -> {
                // Nhánh 4B (0.25s / 5 ticks): Xoay ngang Yaw +45°, chém ngang dứt khoát Phải->Trái giữ lơ lửng trên không!
                if (alpha < 0.24F) {
                    float u = easeOutQuart(alpha / 0.24F);
                    lerpKeyPose(p, KP_AIR1_PEAK, KP_AIR2_START, u, 1.0F, 0.90F, 0.92F);
                } else if (alpha < 0.78F) {
                    float u = easeOutExpo((alpha - 0.24F) / 0.54F);
                    lerpKeyPose(p, KP_AIR2_START, KP_AIR2_END, u, 1.0F, 0.92F, 0.92F);
                    p.firstPersonCameraRollDeg = Mth.sin(u * Mth.PI) * -3.0F;
                    p.fovOffsetDeg = Mth.sin(u * Mth.PI) * 3.2F;
                } else {
                    fillKeyPose(p, KP_AIR2_END, 1.0F, 0.90F);
                    p.armWeight = 1.0F;
                    p.legWeight = 0.90F;
                }
            }
            case AIR_3_SLAM -> {
                // Nhánh 4C (Meteor Air Slam):
                // Khi đang lao xuống: 2 tay giơ thẳng kiếm quá đầu (-165°), gập thân +30° cắm đầu lao xuống!
                // Khi chạm đất: chuyển ngay sang frame cắm phập kiếm xuống mặt đất (-15°) + rung chấn!
                if (!airSlamLanded) {
                    float u = easeOutQuart(Mth.clamp(alpha * 5.0F, 0.0F, 1.0F));
                    lerpKeyPose(p, KP_AIR2_END, KP_AIR3_DIVE, u, 1.0F, 0.94F, 0.96F);
                    p.fovOffsetDeg = 6.5F * u;
                } else if (alpha < 0.65F) {
                    float impactT = alpha / 0.65F;
                    fillKeyPose(p, KP_AIR3_IMPACT, 1.0F, 1.0F);
                    p.armWeight = 1.0F;
                    p.legWeight = 1.0F;
                    float shiver = Mth.sin(impactT * Mth.TWO_PI * 3.0F) * (1.0F - impactT);
                    p.rightBladePitchDeg += shiver * 2.5F;
                    p.bodyX += shiver * 0.03F;
                    p.fovOffsetDeg = 5.5F * (1.0F - impactT);
                } else {
                    float u = smootherstep((alpha - 0.65F) / 0.35F);
                    lerpKeyPose(p, KP_AIR3_IMPACT, KP_CHUDAN_STANCE, u, 1.0F, 0.90F, 0.90F);
                }
            }
            case DEFLECT -> {
                // Thế 3 — Suriage / Sekiro Parry (0.25s / 5 ticks):
                // GĐ 1 (0.00-0.06s = 0..30%): Hạ thấp hai tay về hông trái (-45°), mũi kiếm hướng chếch lên!
                // GĐ 2 (0.06-0.20s = 30..100%): Xoay giật mạnh sang phải (+35°), hất chéo từ dưới trái lên trên phải gạt bật vũ khí địch!
                if (alpha < 0.30F) {
                    fillKeyPose(p, KP_SURIAGE_LOW, 1.0F, 0.94F);
                    p.armWeight = 1.0F;
                    p.legWeight = 0.94F;
                    p.fovOffsetDeg = -2.0F;
                } else {
                    float t = easeOutExpo((alpha - 0.30F) / 0.70F);
                    lerpKeyPose(p, KP_SURIAGE_LOW, KP_SURIAGE_UP, t, 1.0F, 0.96F, 0.96F);
                    p.firstPersonCameraRollDeg = Mth.sin(t * Mth.PI) * +3.8F;
                    p.fovOffsetDeg = Mth.sin(t * Mth.PI) * 5.2F;
                }
            }
            case GUARD_KASA_IMPACT -> {
                // Thế 2 — Kasa-uke Impact Frame (0.22s = 4.5 ticks): Toàn bộ tay và kiếm sụp xuống 1.5u trong 2 frames rồi bật nảy lên!
                fillKeyPose(p, KP_GUARD_KASA_UKE, 1.0F, 0.96F);
                p.armWeight = 1.0F;
                p.legWeight = 0.96F;
                float dip = (alpha < 0.44F)
                        ? easeOutQuart(alpha / 0.44F)
                        : (1.0F - smootherstep((alpha - 0.44F) / 0.56F));
                p.rootTranslateY -= dip * 0.094F;
                p.rightArmX += dip * 0.24F;
                p.leftArmX += dip * 0.24F;
                p.rightBladePitchDeg -= dip * 7.5F;
                p.bodyX += dip * 0.08F;
                p.fovOffsetDeg = -3.8F * dip;
            }
            case GUARD_MUNE_OSHI -> {
                // Thế 4 — Mune-oshi Heavy Brace (0.40s = 8 ticks): Sụp sâu -3.5u, bàn tay trái áp chặt sống lưng Mune trượt lùi 1 block!
                fillKeyPose(p, KP_GUARD_MUNE_OSHI, 1.0F, 0.98F);
                p.armWeight = 1.0F;
                p.legWeight = 0.98F;
                float braceFade = 1.0F - smootherstep(Mth.clamp((alpha - 0.55F) / 0.45F, 0.0F, 1.0F));
                float vib = Mth.sin(ageInTicks * 4.5F) * braceFade;
                p.rightBladePitchDeg += vib * 2.0F;
                p.bodyX += vib * 0.024F;
                p.fovOffsetDeg = -4.4F * braceFade;
            }
            case GUARD_SAYA_UKE_HIT -> {
                // Thế 5 — Saya-uke Block Impact (0.20s = 4 ticks):
                // 2 frames đầu giật lùi Body Z +1.0u & ép tay + vỏ sát vào người thêm 5°, sau đó bật nảy trở lại!
                fillKeyPose(p, KP_GUARD_SAYA_UKE, 1.0F, 1.0F);
                p.armWeight = 1.0F;
                p.legWeight = 1.0F;
                p.rightArmPosY = +1.00F;
                p.leftArmPosY = -0.50F;
                p.leftLegY = -0.1396F;
                p.rightLegY = +0.2618F;
                p.leftLegZ = -0.0349F;
                p.rightLegZ = +0.0349F;
                p.leftLegPosY = -0.50F;
                p.rightLegPosY = -0.35F;
                p.leftLegPosZ = -1.50F;
                p.rightLegPosZ = +1.50F;
                float impact = (alpha < 0.50F)
                        ? easeOutQuart(alpha / 0.50F)
                        : (1.0F - smootherstep((alpha - 0.50F) / 0.50F));
                p.rootTranslateZ += impact * (1.0F / 16.0F); // Body Position Z: +1.0 unit!
                p.bodyX += impact * 0.052F;
                p.rightArmX += impact * 0.0873F;             // Ép sát vào người thêm +5°!
                p.leftArmX += impact * 0.0873F;
                p.rightArmPosZ += impact * 0.55F;
                p.leftArmPosZ += impact * 0.55F;
                p.rightBladePitchDeg += impact * 5.0F;
                p.rightBladeRollDeg += impact * 6.0F;
                p.fovOffsetDeg = impact * 3.2F;
            }
            case PARRY_COUNTER -> {
                // Nhánh 5B (0.20-0.45s = 5 ticks): Vặn hông cực gắt phản chém ngược vào cổ/ngực!
                if (alpha < 0.48F) {
                    float s = easeOutExpo(alpha / 0.48F);
                    if (s < 0.5F) {
                        lerpKeyPose(p, KP_SURIAGE_UP, KP_COUNTER_MID, s * 2.0F, 1.0F, 0.96F, 0.96F);
                    } else {
                        lerpKeyPose(p, KP_COUNTER_MID, KP_COUNTER_END, (s - 0.5F) * 2.0F, 1.0F, 0.96F, 0.96F);
                    }
                    p.firstPersonCameraRollDeg = Mth.sin(s * Mth.PI) * +4.0F;
                    p.fovOffsetDeg = Mth.sin(s * Mth.PI) * 6.2F;
                } else if (alpha < 0.80F) {
                    fillKeyPose(p, KP_COUNTER_END, 1.0F, 0.94F);
                    p.armWeight = 1.0F;
                    p.legWeight = 0.94F;
                } else {
                    float u = smootherstep((alpha - 0.80F) / 0.20F);
                    lerpKeyPose(p, KP_COUNTER_END, KP_CHUDAN_STANCE, u, 1.0F, 0.88F, 0.88F);
                }
            }
            case CHIBURI_NOTO -> {
                if (alpha < 0.089F) {
                    float u = smootherstep(alpha / 0.089F);
                    lerpKeyPose(p, KP_CHUDAN_STANCE, KP_CHIB_P1A_TEMPLE, u, 1.0F, 0.70F, 0.78F);
                } else if (alpha < 0.167F) {
                    float flick = easeOutExpo((alpha - 0.089F) / 0.078F);
                    lerpKeyPose(p, KP_CHIB_P1A_TEMPLE, KP_CHIB_P1B_FLICK, flick, 1.0F, 0.65F, 0.76F);
                } else if (alpha < 0.278F) {
                    fillKeyPose(p, KP_CHIB_P1B_FLICK, 1.0F, 0.65F);
                    p.armWeight = 1.0F;
                    p.legWeight = 0.76F;
                } else if (alpha < 0.611F) {
                    float u = smootherstep((alpha - 0.278F) / 0.333F);
                    lerpKeyPose(p, KP_CHIB_P1B_FLICK, KP_CHIB_P2_NOTO_TIP, u, 1.0F, 0.55F, 0.72F);
                } else if (alpha < 0.889F) {
                    float push = smootherstep((alpha - 0.611F) / 0.278F);
                    lerpKeyPose(p, KP_CHIB_P2_NOTO_TIP, KP_CHIB_P3A_PAUSE, push, 1.0F, 0.35F, 0.68F);
                } else {
                    float lock = smootherstep((alpha - 0.889F) / 0.111F);
                    lerpKeyPose(p, KP_CHIB_P3A_PAUSE, KP_SHEATHED_IDLE, lock, 1.0F, (1.0F - lock) * 0.25F, 0.65F * (1.0F - lock * 0.5F));
                }
            }
        }
    }

    private static void fillKeyPose(FullBodyPose p, KeyPose k, float elbowW, float kneeW) {
        p.reset();
        p.rootTranslateY = k.rootY;
        p.bodyX = k.bodyX;
        p.bodyY = k.bodyY;
        p.bodyZ = k.bodyZ;
        p.headAddX = k.headX;
        p.headAddY = k.headY;

        p.rightLegX = k.rLegX;
        p.leftLegX = k.lLegX;
        p.rightLegZ = +0.10F;
        p.leftLegZ = -0.10F;
        p.rightKneeX = k.rKneeX;
        p.leftKneeX = k.lKneeX;
        p.kneeWeight = kneeW;

        p.rightArmX = k.rArmX;
        p.rightArmY = k.rArmY;
        p.rightArmZ = k.rArmZ;
        p.rightElbowX = k.rElbX;
        p.rightElbowY = k.rElbY;
        p.rightElbowZ = k.rElbZ;
        p.rightArmPosX = k.rPosX;
        p.rightArmPosZ = k.rPosZ;

        p.leftArmX = k.lArmX;
        p.leftArmY = k.lArmY;
        p.leftArmZ = k.lArmZ;
        p.leftElbowX = k.lElbX;
        p.leftElbowY = k.lElbY;
        p.leftElbowZ = k.lElbZ;
        p.leftArmPosX = k.lPosX;
        p.leftArmPosZ = k.lPosZ;
        p.elbowWeight = elbowW;

        p.rightBladePitchDeg = k.bladeP;
        p.rightBladeRollDeg = k.bladeR;
        p.rightBladeYawDeg = k.bladeY;
        p.rightBladeOffY = k.bladeOffY;
    }

    private static void lerpKeyPose(FullBodyPose p, KeyPose a, KeyPose b, float t,
                                    float elbowW, float kneeW, float legW) {
        p.reset();
        p.armWeight = 1.0F;
        p.legWeight = legW;
        p.elbowWeight = elbowW;
        p.kneeWeight = kneeW;

        p.rootTranslateY = Mth.lerp(t, a.rootY, b.rootY);
        p.bodyX = Mth.lerp(t, a.bodyX, b.bodyX);
        p.bodyY = Mth.lerp(t, a.bodyY, b.bodyY);
        p.bodyZ = Mth.lerp(t, a.bodyZ, b.bodyZ);
        p.headAddX = Mth.lerp(t, a.headX, b.headX);
        p.headAddY = Mth.lerp(t, a.headY, b.headY);

        p.rightLegX = Mth.lerp(t, a.rLegX, b.rLegX);
        p.leftLegX = Mth.lerp(t, a.lLegX, b.lLegX);
        p.rightLegZ = +0.11F;
        p.leftLegZ = -0.11F;
        p.rightKneeX = Mth.lerp(t, a.rKneeX, b.rKneeX);
        p.leftKneeX = Mth.lerp(t, a.lKneeX, b.lKneeX);

        p.rightArmX = Mth.lerp(t, a.rArmX, b.rArmX);
        p.rightArmY = Mth.lerp(t, a.rArmY, b.rArmY);
        p.rightArmZ = Mth.lerp(t, a.rArmZ, b.rArmZ);
        p.rightElbowX = Mth.lerp(t, a.rElbX, b.rElbX);
        p.rightElbowY = Mth.lerp(t, a.rElbY, b.rElbY);
        p.rightElbowZ = Mth.lerp(t, a.rElbZ, b.rElbZ);
        p.rightArmPosX = Mth.lerp(t, a.rPosX, b.rPosX);
        p.rightArmPosZ = Mth.lerp(t, a.rPosZ, b.rPosZ);

        p.leftArmX = Mth.lerp(t, a.lArmX, b.lArmX);
        p.leftArmY = Mth.lerp(t, a.lArmY, b.lArmY);
        p.leftArmZ = Mth.lerp(t, a.lArmZ, b.lArmZ);
        p.leftElbowX = Mth.lerp(t, a.lElbX, b.lElbX);
        p.leftElbowY = Mth.lerp(t, a.lElbY, b.lElbY);
        p.leftElbowZ = Mth.lerp(t, a.lElbZ, b.lElbZ);
        p.leftArmPosX = Mth.lerp(t, a.lPosX, b.lPosX);
        p.leftArmPosZ = Mth.lerp(t, a.lPosZ, b.lPosZ);

        p.rightBladePitchDeg = Mth.rotLerp(t, a.bladeP, b.bladeP);
        p.rightBladeRollDeg = Mth.rotLerp(t, a.bladeR, b.bladeR);
        p.rightBladeYawDeg = Mth.rotLerp(t, a.bladeY, b.bladeY);
        p.rightBladeOffY = Mth.lerp(t, a.bladeOffY, b.bladeOffY);
    }

    private static void fillUprightSprintPose(FullBodyPose p) {
        p.reset();
        p.rightArmX = -0.80F;
        p.rightArmY = -0.52F;
        p.rightArmZ = +0.12F;
        p.rightArmPosZ = +0.50F;

        p.leftArmX = -0.98F;
        p.leftArmY = +0.72F;
        p.leftArmZ = -0.10F;
        p.leftArmPosZ = -0.50F;

        p.rightBladePitchDeg = SPRINT_P;
        p.rightBladeRollDeg = SPRINT_R;
        p.rightBladeYawDeg = SPRINT_Y;
        p.rightBladeOffX = SPRINT_OFF_X;
        p.rightBladeOffY = SPRINT_OFF_Y;
        p.rightBladeOffZ = SPRINT_OFF_Z;
        p.fovOffsetDeg = 2.2F;
    }

    private static float smootherstep(float t) {
        float x = Mth.clamp(t, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    private static float easeOutQuart(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u * u * u;
    }

    private static float easeOutExpo(float t) {
        float x = Mth.clamp(t, 0.0F, 1.0F);
        return (x >= 1.0F) ? 1.0F : (1.0F - (float) Math.pow(2.0, -10.0 * x));
    }
}
