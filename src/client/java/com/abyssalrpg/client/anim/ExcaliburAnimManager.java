package com.abyssalrpg.client.anim;

import com.abyssalrpg.client.ClientRpgState;
import com.abyssalrpg.client.ExcaliburClientState;
import com.abyssalrpg.client.anim.DualBladeAnimState.FullBodyPose;
import com.abyssalrpg.combat.ExcaliburMoveType;
import com.abyssalrpg.item.ExcaliburItem;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Hệ thống Rig Animation Toàn Thân 2 Khớp (Khuỷu tay + Đầu gối) chuẩn **Royal Swordplay + Vòng Phép Fairy Tail**
 * cho **Excalibur** (100% chân chạm đất `Y = 0.00`, tắt Vanilla Bobbing khi đứng chiến đấu & đỡ kiếm):
 * - **1. Combat Idle:** Mở rộng góc vai (`Body Yaw -28°, PosY -0.8u, Pitch +4°`), `Excalibur` dựng chéo `42°` trước vai phải, chân chạm đất 100%.
 * - **2. Chuỗi 4 Đòn Thường Royal Swordplay:**
 *   - `HIT_1_SWEEP` (`0.35s`): Lấy đà `Yaw +32°` → Xoay giật `Yaw -35°` chém ngang tầm ngực → Dừng bên trái ngang vai.
 *   - `HIT_2_RISING` (`0.35s`): Hạ cổ tay ngang hông trái (`Pitch +18°`) → Bung xoay `Yaw +38°` chân phải bước chéo (`-24°`) chém hất ngược.
 *   - `HIT_3_THRUST` (`0.40s`): Thu chuôi sát sườn phải (`Pitch -90°`, `Body Y -1.5`) → Lao đâm thẳng (`Pitch +16°`).
 *   - `HIT_4_SLAM` (`0.50s`): Bật nhảy nhẹ (`Body Y +2.0`) nâng thẳng qua đỉnh đầu (`Pitch -160°`) → Bổ dọc tới đầu gối (`Pitch +22°`).
 * - **3. 3 Nhánh Combo Mở Rộng:** `STRIKE_AIR` (`0.45s`), `GOLDEN_CRESCENT` (`0.60s` xoay `360°` + bổ phóng kiếm khí), `AIR_1_DIAG` → `AIR_2_DIAG` → `AIR_3_FALLING_STAR`.
 * - **4. Tuyệt Kỹ `ULT_EXCALIBUR` (`2.60s` / `52t`):** `0.00–0.90s` dựng thẳng `90°` → `0.90–1.20s` ngả sau `-12° / -125°` → `1.20–1.35s` bổ ngang `+20° / -85°` → `1.35–2.30s` giữ tia → `2.30–2.60s` thu về Combat Idle.
 * - **5. Đỡ Kiếm & Royal Parry Counter:** Đỡ Trực Diện (`45°`, lộ mặt 100%) | Đỡ Trên Cao (`Pitch < -30°`, ngang trên đỉnh đầu) | Phản Đòn 2 Nhịp (`0.00–0.15s` gạt `+35°` → `0.15–0.40s` chém ngang `-40°`).
 */
public final class ExcaliburAnimManager {
    private static final float D = Mth.DEG_TO_RAD;

    private record KeyPose(
            float rootY,
            float bodyX, float bodyY, float bodyZ,
            float headX, float headY,
            float rLegX, float rLegY, float rLegZ, float rKneeX, float rLegPosY, float rLegPosZ,
            float lLegX, float lLegY, float lLegZ, float lKneeX, float lLegPosY, float lLegPosZ,
            float rArmX, float rArmY, float rArmZ, float rElbX, float rElbZ, float rPosX, float rPosY, float rPosZ,
            float lArmX, float lArmY, float lArmZ, float lElbX, float lElbZ, float lPosX, float lPosY, float lPosZ,
            float bladeP, float bladeR, float bladeY, float bladeOffX, float bladeOffY, float bladeOffZ
    ) {
        KeyPose(float rootY,
                float bodyX, float bodyY, float bodyZ,
                float headX, float headY,
                float rLegX, float rLegY, float rLegZ, float rKneeX, float rLegPosY, float rLegPosZ,
                float lLegX, float lLegY, float lLegZ, float lKneeX, float lLegPosY, float lLegPosZ,
                float rArmX, float rArmY, float rArmZ, float rElbX, float rElbZ, float rPosX, float rPosY, float rPosZ,
                float lArmX, float lArmY, float lArmZ, float lElbX, float lElbZ, float lPosX, float lPosY, float lPosZ,
                float bladeP, float bladeR, float bladeY, float bladeOffY) {
            this(rootY, bodyX, bodyY, bodyZ, headX, headY,
                    rLegX, rLegY, rLegZ, rKneeX, rLegPosY, rLegPosZ,
                    lLegX, lLegY, lLegZ, lKneeX, lLegPosY, lLegPosZ,
                    rArmX, rArmY, rArmZ, rElbX, rElbZ, rPosX, rPosY, rPosZ,
                    lArmX, lArmY, lArmZ, lElbX, lElbZ, lPosX, lPosY, lPosZ,
                    bladeP, bladeR, bladeY, 0.0F, bladeOffY, 0.0F);
        }
    }

    private static final float SPRINT_P = 147.63F, SPRINT_R = -22.62F, SPRINT_Y = 170.58F;
    private static final float SPRINT_OFF_X = 0.0049F, SPRINT_OFF_Y = -0.0027F, SPRINT_OFF_Z = -0.0022F;

    // --- 1. DÁNG ĐỨNG CHỜ THANH THOÁT (Tay trái buông tự nhiên đặt lên cổ vỏ Avalon bên hông trái – Tay phải buông lỏng sườn phải!) ---
    private static final KeyPose KP_SHEATHED_IDLE = new KeyPose(
            -0.0250F, +2.0F * D, -10.0F * D, 0.0F, +2.0F * D, 0.0F,
            +10.0F * D, -4.0F * D, +1.5F * D, 0.0F, 0.00F, 0.00F,
            -10.0F * D, -8.0F * D, -1.5F * D, +10.0F * D, 0.00F, 0.00F,
            +5.0F * D, -4.0F * D, +12.0F * D, -6.0F * D, 0.0F, 0.00F, 0.00F, 0.00F,
            -15.0F * D, +12.0F * D, -14.0F * D, -10.0F * D, +4.0F * D, 0.00F, 0.00F, 0.00F,
            0.0F, 0.0F, 0.0F, 0.0F);

    // Dáng chiến đấu rút kiếm (42°) dùng làm mốc nối đà giữa các đòn — chân xoay đồng chiều thân trên, không tách hông!
    private static final KeyPose KP_COMBAT_IDLE = new KeyPose(
            -0.0500F, +4.0F * D, -28.0F * D, 0.0F, -3.0F * D, 0.0F,
            +15.0F * D, -10.0F * D, +2.0F * D, 0.0F, 0.00F, 0.00F,
            -16.0F * D, -14.0F * D, -2.0F * D, +16.0F * D, 0.00F, 0.00F,
            -75.0F * D, -14.0F * D, +18.0F * D, -8.0F * D, -4.0F * D, +0.00F, +0.00F, -1.20F,
            -62.0F * D, +40.0F * D, -12.0F * D, -10.0F * D, +4.0F * D, -0.30F, +0.30F, -1.20F,
            -17.74F, -58.83F, -158.65F, -0.080F);

    // --- 2. CHUỖI 4 ĐÒN THƯỜNG ROYAL SWORDPLAY ---
    private static final KeyPose KP_H1_WIND = new KeyPose(
            -0.0500F, +5.0F * D, +32.0F * D, 0.0F, -3.0F * D, 0.0F,
            +15.0F * D, +18.0F * D, +2.0F * D, 0.0F, -0.40F, +1.20F,
            -16.0F * D, -10.0F * D, -2.0F * D, +16.0F * D, -0.57F, -1.20F,
            -88.0F * D, +18.0F * D, +22.0F * D, -14.0F * D, -12.0F * D, +0.20F, +0.00F, -1.00F,
            -82.0F * D, +62.0F * D, -10.0F * D, -10.0F * D, +8.0F * D, -1.00F, +0.20F, -1.60F,
            -118.57F, -50.83F, +24.27F, -0.040F);

    private static final KeyPose KP_H1_MID = new KeyPose(
            -0.0625F, +8.0F * D, 0.0F, 0.0F, -5.0F * D, 0.0F,
            +15.0F * D, +16.0F * D, +2.0F * D, 0.0F, -0.42F, +1.20F,
            -18.0F * D, -10.0F * D, -2.0F * D, +18.0F * D, -0.55F, -1.20F,
            -87.0F * D, -12.0F * D, +10.0F * D, -14.0F * D, -2.0F * D, +0.35F, +0.10F, -1.15F,
            -80.0F * D, +42.0F * D, -12.0F * D, -12.0F * D, +8.0F * D, -0.60F, +0.25F, -1.25F,
            -106.0F, -12.0F, +12.0F, -0.040F);

    private static final KeyPose KP_H1_END = new KeyPose(
            -0.0500F, +10.0F * D, -35.0F * D, 0.0F, -6.0F * D, 0.0F,
            +15.0F * D, +18.0F * D, +2.0F * D, 0.0F, -0.40F, +1.20F,
            -16.0F * D, -10.0F * D, -2.0F * D, +16.0F * D, -0.57F, -1.20F,
            -86.0F * D, -42.0F * D, -4.0F * D, -14.0F * D, +8.0F * D, +0.50F, +0.20F, -1.20F,
            -78.0F * D, +22.0F * D, -16.0F * D, -12.0F * D, +8.0F * D, -0.20F, +0.30F, -0.90F,
            -97.23F, +25.90F, -0.28F, -0.040F);

    private static final KeyPose KP_H2_DIP = new KeyPose(
            -0.0500F, +18.0F * D, -34.0F * D, 0.0F, -8.0F * D, 0.0F,
            +15.0F * D, +18.0F * D, +2.0F * D, 0.0F, -0.40F, +1.20F,
            -16.0F * D, -10.0F * D, -2.0F * D, +16.0F * D, -0.57F, -1.20F,
            -44.0F * D, -34.0F * D, -4.0F * D, -26.0F * D, +10.0F * D, +0.50F, +0.60F, -1.00F,
            -46.0F * D, +32.0F * D, -10.0F * D, -18.0F * D, +8.0F * D, -0.30F, +0.60F, -1.20F,
            -106.68F, +21.13F, +52.68F, -0.040F);

    private static final KeyPose KP_H2_END = new KeyPose(
            -0.0500F, -4.0F * D, +38.0F * D, 0.0F, -2.0F * D, 0.0F,
            -24.0F * D, +12.0F * D, +2.0F * D, +24.0F * D, -0.68F, -1.50F,
            +16.0F * D, -10.0F * D, -2.0F * D, 0.0F, -0.40F, +1.20F,
            -124.0F * D, +14.0F * D, -10.0F * D, -14.0F * D, +8.0F * D, +0.20F, -0.40F, -1.00F,
            -112.0F * D, +56.0F * D, -8.0F * D, -12.0F * D, +6.0F * D, -0.90F, -0.20F, -1.50F,
            -84.40F, -19.31F, -115.75F, -0.040F);

    private static final KeyPose KP_H3_COIL = new KeyPose(
            -0.0938F, +10.0F * D, +35.0F * D, 0.0F, -6.0F * D, 0.0F,
            +22.0F * D, +18.0F * D, +2.0F * D, 0.0F, -0.95F, +1.80F,
            -24.0F * D, -12.0F * D, -2.0F * D, +24.0F * D, -1.15F, -1.80F,
            +10.0F * D, +8.0F * D, +14.0F * D, -96.0F * D, -8.0F * D, +0.00F, +0.40F, +1.20F,
            -46.0F * D, +62.0F * D, -6.0F * D, -32.0F * D, +10.0F * D, -1.00F, +0.60F, -0.60F,
            -82.92F, +5.95F, +6.47F, -0.020F);

    private static final KeyPose KP_H3_THRUST = new KeyPose(
            -0.0750F, +16.0F * D, -18.0F * D, 0.0F, -12.0F * D, 0.0F,
            +24.0F * D, +14.0F * D, +2.0F * D, 0.0F, -0.75F, +2.00F,
            -26.0F * D, -8.0F * D, -2.0F * D, +26.0F * D, -0.90F, -2.00F,
            -88.0F * D, -12.0F * D, +6.0F * D, -4.0F * D, -2.0F * D, +0.30F, +0.20F, -2.20F,
            -82.0F * D, +42.0F * D, -6.0F * D, -12.0F * D, +4.0F * D, -0.70F, +0.40F, -1.40F,
            -90.81F, -14.11F, +5.64F, +0.020F);

    private static final KeyPose KP_H4_LEAP = new KeyPose(
            +0.1250F, -8.0F * D, 0.0F, 0.0F, -4.0F * D, 0.0F,
            +24.0F * D, +8.0F * D, +2.0F * D, +36.0F * D, 0.00F, +0.40F,
            -18.0F * D, -6.0F * D, -2.0F * D, +34.0F * D, 0.00F, -0.40F,
            -166.0F * D, 0.0F, -16.0F * D, -6.0F * D, -46.0F * D, -0.20F, -1.80F, -0.60F,
            -164.0F * D, 0.0F, +16.0F * D, -6.0F * D, +46.0F * D, +0.20F, -1.40F, -0.60F,
            -53.22F, -20.75F, -23.10F, +0.000F);

    private static final KeyPose KP_H4_SLAM = new KeyPose(
            -0.0938F, +22.0F * D, 0.0F, 0.0F, -12.0F * D, 0.0F,
            +22.0F * D, +14.0F * D, +2.0F * D, 0.0F, -0.95F, +1.80F,
            -24.0F * D, -8.0F * D, -2.0F * D, +24.0F * D, -1.15F, -1.80F,
            -34.0F * D, -18.0F * D, +6.0F * D, -26.0F * D, -10.0F * D, +0.40F, +0.40F, -1.40F,
            -28.0F * D, +34.0F * D, -6.0F * D, -22.0F * D, +10.0F * D, -0.40F, +0.60F, -1.20F,
            -85.90F, -22.08F, +8.43F, -0.020F);

    // --- 3. KHÔNG CHIẾN (Aerial Judgment: Air 1, Air 2, Air 3 Plunge & Land) ---
    private static final KeyPose KP_AIR2_END = new KeyPose(
            0.0000F, -6.0F * D, +34.0F * D, 0.0F, 0.0F, 0.0F,
            -22.0F * D, +10.0F * D, +2.0F * D, +32.0F * D, 0.00F, 0.00F,
            +24.0F * D, -8.0F * D, -2.0F * D, +36.0F * D, 0.00F, 0.00F,
            -120.0F * D, +14.0F * D, +18.0F * D, -16.0F * D, -8.0F * D, +0.30F, +0.00F, -0.80F,
            -106.0F * D, +48.0F * D, -12.0F * D, -14.0F * D, +8.0F * D, -0.40F, +0.20F, -0.80F,
            -56.80F, -42.17F, -162.81F, -0.059F);

    private static final KeyPose KP_AIR3_PLUNGE = new KeyPose(
            0.0000F, +10.0F * D, 0.0F, 0.0F, +16.0F * D, 0.0F,
            -14.0F * D, +6.0F * D, +2.0F * D, +18.0F * D, 0.00F, 0.00F,
            +18.0F * D, -6.0F * D, -2.0F * D, +22.0F * D, 0.00F, 0.00F,
            -44.0F * D, -26.0F * D, +10.0F * D, -24.0F * D, -18.0F * D, +0.50F, +0.00F, -1.20F,
            -38.0F * D, +32.0F * D, -10.0F * D, -22.0F * D, +18.0F * D, -0.50F, +0.20F, -1.00F,
            -145.12F, -11.05F, +36.62F, -0.020F);

    private static final KeyPose KP_AIR3_LAND = new KeyPose(
            -0.1125F, +26.0F * D, 0.0F, 0.0F, -14.0F * D, 0.0F,
            +26.0F * D, +14.0F * D, +2.0F * D, 0.0F, -1.10F, +2.00F,
            -28.0F * D, -10.0F * D, -2.0F * D, +28.0F * D, -1.35F, -2.00F,
            -26.0F * D, -26.0F * D, +8.0F * D, -24.0F * D, -16.0F * D, +0.50F, +0.40F, -1.40F,
            -20.0F * D, +32.0F * D, -8.0F * D, -22.0F * D, +16.0F * D, -0.50F, +0.60F, -1.20F,
            -127.83F, -12.86F, +30.78F, -0.020F);

    // --- 4. TUYỆT KỸ EXCALIBUR (Fairy Tail Magic Circle: P1 90° -> P2 -12°/-125° -> P3 +20°/-85°) ---
    private static final KeyPose KP_ULT_P1 = new KeyPose(
            0.0000F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
            +8.0F * D, +6.0F * D, +2.0F * D, 0.0F, 0.00F, +0.40F,
            -8.0F * D, -6.0F * D, -2.0F * D, +8.0F * D, 0.00F, -0.40F,
            -68.0F * D, -28.0F * D, +10.0F * D, -34.0F * D, -24.0F * D, +0.50F, +0.00F, -0.60F,
            -60.0F * D, +32.0F * D, -10.0F * D, -28.0F * D, +24.0F * D, -0.50F, +0.80F, -0.60F,
            -6.04F, +2.33F, +130.32F, +0.000F);

    private static final KeyPose KP_ULT_P2 = new KeyPose(
            0.0000F, -12.0F * D, 0.0F, 0.0F, -6.0F * D, 0.0F,
            +14.0F * D, +10.0F * D, +2.0F * D, 0.0F, 0.00F, +0.80F,
            -14.0F * D, -8.0F * D, -2.0F * D, +14.0F * D, 0.00F, -0.80F,
            -172.0F * D, 0.0F, -16.0F * D, -8.0F * D, -46.0F * D, -0.20F, -2.00F, -0.40F,
            -170.0F * D, 0.0F, +16.0F * D, -8.0F * D, +46.0F * D, +0.20F, -1.60F, -0.40F,
            -48.36F, -18.90F, -24.03F, +0.000F);

    private static final KeyPose KP_ULT_P3 = new KeyPose(
            -0.0750F, +20.0F * D, 0.0F, 0.0F, -14.0F * D, 0.0F,
            +18.0F * D, +15.0F * D, +2.0F * D, 0.0F, -0.50F, +1.60F,
            -22.0F * D, -8.0F * D, -2.0F * D, +22.0F * D, -0.65F, -1.60F,
            -66.0F * D, -26.0F * D, +6.0F * D, -18.0F * D, -10.0F * D, +0.50F, +0.00F, -1.20F,
            -56.0F * D, +38.0F * D, -6.0F * D, -16.0F * D, +10.0F * D, -0.50F, +0.40F, -0.30F,
            -75.87F, -35.01F, -2.17F, -0.020F);

    // --- 5. ĐỠ KIẾM & ROYAL PARRY COUNTER (A: Front Guard 45° | B: Overhead Guard | C: Beat 1 +35° -> Beat 2 -40°) ---
    private static final KeyPose KP_FRONT_GUARD = new KeyPose(
            -0.0500F, +5.0F * D, -22.0F * D, 0.0F, -3.0F * D, 0.0F,
            +16.0F * D, -10.0F * D, +2.0F * D, 0.0F, 0.00F, 0.00F,
            -18.0F * D, -12.0F * D, -2.0F * D, +18.0F * D, 0.00F, 0.00F,
            -80.0F * D, -14.0F * D, +22.0F * D, -6.0F * D, -4.0F * D, +0.00F, +0.50F, -1.50F,
            -60.0F * D, +44.0F * D, -12.0F * D, -10.0F * D, +4.0F * D, -0.30F, +0.60F, -1.50F,
            -24.07F, -53.24F, -160.51F, -0.080F);

    private static final KeyPose KP_OVERHEAD_GUARD = new KeyPose(
            -0.0500F, -4.0F * D, -14.0F * D, 0.0F, -8.0F * D, 0.0F,
            +16.0F * D, -6.0F * D, +2.0F * D, 0.0F, 0.00F, 0.00F,
            -18.0F * D, -10.0F * D, -2.0F * D, +18.0F * D, 0.00F, 0.00F,
            -152.0F * D, -4.0F * D, -14.0F * D, -8.0F * D, -44.0F * D, -0.20F, -2.40F, -2.40F,
            -150.0F * D, +4.0F * D, +12.0F * D, -10.0F * D, +46.0F * D, +0.20F, -2.20F, -2.40F,
            -110.74F, +46.02F, +142.84F, -0.020F);

    private static final KeyPose KP_PARRY_BEAT1 = new KeyPose(
            -0.0500F, +6.0F * D, +35.0F * D, 0.0F, -4.0F * D, 0.0F,
            +14.0F * D, +20.0F * D, +2.0F * D, 0.0F, 0.00F, 0.00F,
            -16.0F * D, +18.0F * D, -2.0F * D, +16.0F * D, 0.00F, 0.00F,
            -84.0F * D, +8.0F * D, +16.0F * D, -12.0F * D, -8.0F * D, +0.20F, +0.20F, -1.20F,
            -76.0F * D, +56.0F * D, -14.0F * D, -8.0F * D, +6.0F * D, -1.00F, +0.40F, -1.60F,
            -60.10F, -50.80F, -107.51F, -0.080F);

    private static final KeyPose KP_PARRY_BEAT2 = new KeyPose(
            -0.0625F, +14.0F * D, -40.0F * D, 0.0F, -8.0F * D, 0.0F,
            +16.0F * D, -18.0F * D, +2.0F * D, 0.0F, 0.00F, 0.00F,
            -18.0F * D, -22.0F * D, -2.0F * D, +18.0F * D, 0.00F, 0.00F,
            -82.0F * D, -44.0F * D, -6.0F * D, -16.0F * D, +8.0F * D, +0.40F, +0.20F, -1.10F,
            -76.0F * D, +20.0F * D, -16.0F * D, -14.0F * D, +8.0F * D, -0.20F, +0.30F, -0.80F,
            -98.23F, +23.86F, -2.06F, -0.020F);

    // --- 5D. AVALON PARRY & RÚT KIẾM PHẢN CÔNG IAIDO (0.65s / 13t): Hai chân đồng bộ Yaw +18..+20° theo thân (+35°), PosZ=0 liền khối thắt lưng! ---
    private static final KeyPose KP_AVALON_PARRY_GUARD = new KeyPose(
            -0.0625F, -6.0F * D, +35.0F * D, 0.0F, +4.0F * D, 0.0F,
            +14.0F * D, +20.0F * D, +2.0F * D, +6.0F * D, 0.00F, 0.00F,
            -16.0F * D, +18.0F * D, -2.0F * D, +16.0F * D, 0.00F, 0.00F,
            -55.0F * D, -16.0F * D, +15.0F * D, -28.0F * D, -14.0F * D, +0.40F, +0.50F, -1.20F,
            -85.0F * D, +16.0F * D, -20.0F * D, -20.0F * D, +8.0F * D, -0.20F, +0.10F, -1.50F,
            0.0F, 0.0F, 0.0F, 0.0F);

    private static final KeyPose KP_AVALON_COUNTER_START = new KeyPose(
            -0.0625F, +4.0F * D, -22.0F * D, 0.0F, -2.0F * D, 0.0F,
            +14.0F * D, -10.0F * D, +2.0F * D, +4.0F * D, 0.00F, 0.00F,
            -16.0F * D, -14.0F * D, -2.0F * D, +16.0F * D, 0.00F, 0.00F,
            -75.0F * D, -45.0F * D, +10.0F * D, -16.0F * D, -6.0F * D, +0.60F, +0.20F, -1.20F,
            +15.0F * D, +14.0F * D, -14.0F * D, -10.0F * D, 0.0F, 0.00F, 0.00F, +0.60F,
            -79.54F, +3.87F, +5.02F, -0.040F);

    private static final KeyPose KP_AVALON_COUNTER_MID = new KeyPose(
            -0.0625F, +10.0F * D, +8.0F * D, 0.0F, -6.0F * D, 0.0F,
            +14.0F * D, +6.0F * D, +2.0F * D, +4.0F * D, 0.00F, 0.00F,
            -16.0F * D, +4.0F * D, -2.0F * D, +16.0F * D, 0.00F, 0.00F,
            -75.0F * D, +6.0F * D, +16.0F * D, -10.0F * D, -4.0F * D, 0.00F, 0.00F, -1.50F,
            +25.0F * D, +15.0F * D, -16.0F * D, -12.0F * D, 0.0F, 0.00F, 0.00F, +1.00F,
            -84.85F, -1.51F, +14.93F, -0.040F);

    private static final KeyPose KP_AVALON_COUNTER_END = new KeyPose(
            -0.0625F, +10.0F * D, +45.0F * D, 0.0F, -6.0F * D, 0.0F,
            +14.0F * D, +22.0F * D, +2.0F * D, +4.0F * D, 0.00F, 0.00F,
            -16.0F * D, +20.0F * D, -2.0F * D, +16.0F * D, 0.00F, 0.00F,
            -75.0F * D, +56.0F * D, +22.0F * D, -8.0F * D, -4.0F * D, -0.40F, 0.00F, -0.80F,
            +25.0F * D, +15.0F * D, -16.0F * D, -12.0F * D, 0.0F, 0.00F, 0.00F, +1.20F,
            -100.71F, -16.57F, +11.83F, -0.040F);

    // --- 6. CẤT KIẾM VÀO VỎ AVALON (0.55s / 11t): GĐ 1 Đón vỏ (-42,+22,-18 / -85,-25,+35) -> GĐ 2 Trượt vào vỏ (-35,-15) -> GĐ 3 Thu về Idle ---
    private static final KeyPose KP_SHEATHE_P1 = new KeyPose(
            -0.0375F, +4.0F * D, -16.0F * D, 0.0F, +6.0F * D, 0.0F,
            +12.0F * D, -6.0F * D, +1.5F * D, 0.0F, 0.00F, 0.00F,
            -12.0F * D, -10.0F * D, -1.5F * D, +12.0F * D, 0.00F, 0.00F,
            -85.0F * D, -25.0F * D, +35.0F * D, -26.0F * D, -12.0F * D, 0.00F, 0.00F, -0.80F,
            -42.0F * D, +22.0F * D, -18.0F * D, -22.0F * D, +10.0F * D, 0.00F, 0.00F, -0.60F,
            -145.0F, +32.0F, -42.0F, -0.020F);

    private static final KeyPose KP_SHEATHE_P2 = new KeyPose(
            -0.03125F, +3.0F * D, -14.0F * D, 0.0F, +4.0F * D, 0.0F,
            +11.0F * D, -6.0F * D, +1.5F * D, 0.0F, 0.00F, 0.00F,
            -11.0F * D, -8.0F * D, -1.5F * D, +11.0F * D, 0.00F, 0.00F,
            -35.0F * D, -15.0F * D, +16.0F * D, -36.0F * D, -20.0F * D, +0.60F, +0.30F, -0.80F,
            -36.0F * D, +18.0F * D, -16.0F * D, -18.0F * D, +8.0F * D, 0.00F, 0.00F, -0.40F,
            0.0F, 0.0F, 0.0F, 0.0F);

    private static final class ExcaliburAnimState {
        boolean unsheathed = false;
        float prevStanceBlend = 0.0F;
        float stanceBlend = 0.0F;
        float prevGuardBlend = 0.0F;
        float guardBlend = 0.0F;
        float prevGuardOverheadBlend = 0.0F;
        float guardOverheadBlend = 0.0F;
        float crossHitRecoilTicks = 0.0F;
        float prevSprintBlend = 0.0F;
        float sprintBlend = 0.0F;
        float prevSheathedRunBlend = 0.0F;
        float sheathedRunBlend = 0.0F;
        float prevCrouchBlend = 0.0F;
        float crouchBlend = 0.0F;
        long sprintGraceUntilTick = -1000L;

        boolean active = false;
        ExcaliburMoveType move = ExcaliburMoveType.HIT_1_SWEEP;
        long startTick = -1000L;
        float durationTicks = 7.0F;
        float hitStopRemaining = 0.0F;
        long lastSeenTick = 0L;
        boolean playedHit4Shake = false;
        boolean playedUltStampShake = false;
        boolean fallingStarLanded = false;

        final FullBodyPose idlePose = new FullBodyPose();
        final FullBodyPose sheathedRunPose = new FullBodyPose();
        final FullBodyPose crouchPose = new FullBodyPose();
        final FullBodyPose guardPose = new FullBodyPose();
        final FullBodyPose guardAltPose = new FullBodyPose();
        final FullBodyPose sprintPose = new FullBodyPose();
        final FullBodyPose actionPose = new FullBodyPose();
        final FullBodyPose blendFrom = new FullBodyPose();
        final FullBodyPose lastPose = new FullBodyPose();
        final FullBodyPose outPose = new FullBodyPose();
        boolean hasBlendFrom = false;
    }

    private static final Map<Integer, ExcaliburAnimState> STATES = new HashMap<>();
    private static long clientTickCounter = 0L;

    private ExcaliburAnimManager() {}

    public static long getClientTick() {
        return clientTickCounter;
    }

    public static void clearAll() {
        STATES.clear();
    }

    public static boolean isHoldingExcalibur(@Nullable Player player) {
        return player != null && player.getMainHandItem().is(ModItems.EXCALIBUR);
    }

    public static void setUnsheathed(int playerId, boolean unsheathed) {
        ExcaliburAnimState st = STATES.computeIfAbsent(playerId, k -> new ExcaliburAnimState());
        st.unsheathed = unsheathed;
    }

    public static boolean isUnsheathed(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        return st != null && st.unsheathed;
    }

    public static boolean isMoveActive(int playerId, ExcaliburMoveType move) {
        ExcaliburAnimState st = STATES.get(playerId);
        return st != null && st.active && st.move == move;
    }

    /**
     * Trả về `true` khi Excalibur đang nằm (hoặc đang trượt vào) bên trong vỏ Avalon bên hông trái:
     * - GĐ 1 Cất Kiếm (`0.00–0.18s`): kiếm còn ở tay phải đưa mũi tới miệng vỏ -> `false`.
     * - GĐ 2 & 3 Cất Kiếm (`0.18–0.55s`): kiếm trượt dọc trục lọt vào trong vỏ Avalon -> `true`.
     * - Đứng chờ & Chạy khi `is_unsheathed == false`: kiếm luôn tra gọn trong vỏ Avalon -> `true`!
     */
    public static boolean isSwordInHipAvalon(int playerId, float partialTick) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st == null) {
            return true;
        }
        if (st.active) {
            if (st.move == ExcaliburMoveType.SHEATHE_AVALON) {
                float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
                return alpha >= 0.327F;
            }
            return false;
        }
        if (st.guardBlend >= 0.05F) {
            return !st.unsheathed;
        }
        return !st.unsheathed;
    }

    /** Trả về true khi đang ở trạng thái tra kiếm trong vỏ Avalon bên hông trái. */
    public static boolean isSheathedIdle(int playerId) {
        return isSwordInHipAvalon(playerId, 0.0F);
    }

    /**
     * Điền 7 tham số vị trí & góc xoay của Vỏ Avalon gắn trên hông trái (`hm.body`):
     * `[0..2] = (tx, ty, tz)`, `[3..5] = (rxDeg, ryDeg, rzDeg)`, `[6] = slideOffsetPx` (độ trượt dọc trục vỏ khi cất kiếm).
     */
    public static void fillHipAvalonTransform(int playerId, float partialTick, float[] out7) {
        // Gắn sát khít bề mặt thắt lưng trái (X=+4.72px -> mép trong chạm áo/váy ở +4.17px, Y=+10.5px, Z=-0.88px, không hở khoảng không!),
        // đuôi vỏ chúc ra sau 38° (XP +52°), áp phẳng dọc theo đùi trái (+8°, -5°)!
        float tx = +0.295F, ty = +0.656F, tz = -0.055F;
        float rx = 52.0F, ry = +8.0F, rz = -5.0F;
        float slidePx = 0.0F;

        ExcaliburAnimState st = STATES.get(playerId);
        if (st != null) {
            if (st.active && st.move == ExcaliburMoveType.SHEATHE_AVALON) {
                float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
                if (alpha < 0.327F) {
                    // GĐ 1 (0.00-0.18s): Tay trái nâng & xoay miệng vỏ Avalon ra đón mũi kiếm
                    float u = easeOutCubic(alpha / 0.327F);
                    tx = Mth.lerp(u, +0.295F, +0.160F);
                    ty = Mth.lerp(u, +0.656F, +0.450F);
                    tz = Mth.lerp(u, -0.055F, -0.440F);
                    rx = Mth.lerp(u, 52.0F, 48.0F);
                    ry = Mth.lerp(u, +8.0F, -16.0F);
                    rz = Mth.lerp(u, -5.0F, +8.0F);
                } else if (alpha < 0.764F) {
                    // GĐ 2 (0.18-0.42s): Lưỡi Excalibur trượt thẳng dọc trục vào trong vỏ Avalon -> khóa khít ở mốc 0.40s (alpha 0.727)!
                    float u = smootherstep((alpha - 0.327F) / 0.437F);
                    float slideU = easeOutCubic(Mth.clamp((alpha - 0.327F) / 0.400F, 0.0F, 1.0F));
                    tx = Mth.lerp(u, +0.160F, +0.240F);
                    ty = Mth.lerp(u, +0.450F, +0.560F);
                    tz = Mth.lerp(u, -0.440F, -0.220F);
                    rx = Mth.lerp(u, 48.0F, 50.0F);
                    ry = Mth.lerp(u, -16.0F, -2.0F);
                    rz = Mth.lerp(u, +8.0F, +0.0F);
                    slidePx = Mth.lerp(slideU, -16.5F, 0.0F);
                } else {
                    // GĐ 3 (0.42-0.55s): Hạ vỏ Avalon cùng thanh kiếm đã khóa gọn sát thắt lưng trái!
                    float u = smootherstep((alpha - 0.764F) / 0.236F);
                    tx = Mth.lerp(u, +0.240F, +0.295F);
                    ty = Mth.lerp(u, +0.560F, +0.656F);
                    tz = Mth.lerp(u, -0.220F, -0.055F);
                    rx = Mth.lerp(u, 50.0F, 52.0F);
                    ry = Mth.lerp(u, -2.0F, +8.0F);
                    rz = Mth.lerp(u, +0.0F, -5.0F);
                }
            } else if (st.active && st.move == ExcaliburMoveType.AVALON_PARRY_COUNTER) {
                // Giật mạnh vỏ Avalon từ thế gạt chéo 45° trước ngực về sát hông trái khi rút kiếm phản công!
                float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
                if (alpha < 0.280F) {
                    float u = easeOutExpo(alpha / 0.280F);
                    tx = Mth.lerp(u, +0.347F, +0.320F);
                    ty = Mth.lerp(u, +0.263F, +0.630F);
                    tz = Mth.lerp(u, -0.475F, -0.020F);
                    rx = Mth.rotLerp(u, +173.1F, +40.0F);
                    ry = Mth.rotLerp(u, 0.0F, +12.0F);
                    rz = Mth.rotLerp(u, +45.8F, -5.0F);
                } else if (alpha < 0.620F) {
                    tx = +0.320F;
                    ty = +0.630F;
                    tz = -0.020F;
                    rx = +40.0F;
                    ry = +12.0F;
                    rz = -5.0F;
                } else {
                    float u = smootherstep((alpha - 0.620F) / 0.380F);
                    tx = Mth.lerp(u, +0.320F, +0.295F);
                    ty = Mth.lerp(u, +0.630F, +0.656F);
                    tz = Mth.lerp(u, -0.020F, -0.055F);
                    rx = Mth.rotLerp(u, +40.0F, +52.0F);
                    ry = Mth.rotLerp(u, +12.0F, +8.0F);
                    rz = Mth.rotLerp(u, -5.0F, -5.0F);
                }
            } else if (!st.unsheathed) {
                float run = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevSheathedRunBlend, st.sheathedRunBlend), 0.0F, 1.0F));
                if (run > 0.001F) {
                    // Chạy khi kiếm trong vỏ (đồng nhất dáng chạy tra vỏ Katana): nâng vỏ Avalon gọn bên hông trái
                    // để tay trái ôm khít cổ vỏ và tay phải vươn qua eo nắm chặt chuôi Excalibur sẵn sàng rút kiếm!
                    tx = Mth.lerp(run, +0.295F, +0.2906F);
                    ty = Mth.lerp(run, +0.656F, +0.5969F);
                    tz = Mth.lerp(run, -0.055F, -0.0781F);
                    rx = Mth.lerp(run, 52.0F, 66.0F);
                    ry = Mth.lerp(run, +8.0F, +24.0F);
                    rz = Mth.lerp(run, -5.0F, -8.0F);
                }
                float crouch = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevCrouchBlend, st.crouchBlend), 0.0F, 1.0F));
                if (crouch > 0.001F) {
                    // Ngồi Shift lúc kiếm trong vỏ (đồng nhất dáng ngồi Katana): bù góc nghiêng cúi thân 0.5 rad
                    // để cổ vỏ Avalon bám khít tay trái, chuôi Excalibur bám khít tay phải & đuôi cao thoáng mặt cỏ!
                    tx = Mth.lerp(crouch, tx, +0.290F);
                    ty = Mth.lerp(crouch, ty, +0.487F);
                    tz = Mth.lerp(crouch, tz, -0.355F);
                    rx = Mth.lerp(crouch, rx, +42.0F);
                    ry = Mth.lerp(crouch, ry, +24.0F);
                    rz = Mth.lerp(crouch, rz, -8.0F);
                }
                float guard = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevGuardBlend, st.guardBlend), 0.0F, 1.0F));
                if (guard > 0.001F) {
                    // GĐ 1 Avalon Parry Guard: dựng chéo vỏ Avalon góc 45° trước ngực (cạnh hẹp hướng ra đón đòn, thoáng mặt & tâm ngắm +)!
                    tx = Mth.lerp(guard, tx, +0.347F);
                    ty = Mth.lerp(guard, ty, +0.263F);
                    tz = Mth.lerp(guard, tz, -0.475F);
                    rx = Mth.rotLerp(guard, rx, +173.1F);
                    ry = Mth.rotLerp(guard, ry, 0.0F);
                    rz = Mth.rotLerp(guard, rz, +45.8F);
                }
            }
        }

        out7[0] = tx;
        out7[1] = ty;
        out7[2] = tz;
        out7[3] = rx;
        out7[4] = ry;
        out7[5] = rz;
        out7[6] = slidePx;
    }

    /** Luôn gắn vỏ Avalon thon gọn bên hông trái (có kiếm tra bên trong khi đứng chờ/chạy tra vỏ, hoặc vỏ rỗng khi rút kiếm)! */
    public static boolean shouldDockHipAvalon(@Nullable Player player) {
        return isHoldingExcalibur(player) && !player.isInvisible();
    }

    public static boolean isFallingStarDiving(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        return st != null && st.active && st.move == ExcaliburMoveType.AIR_3_FALLING_STAR && !st.fallingStarLanded;
    }

    public static boolean isFallingStarLanded(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        return st != null && st.active && st.move == ExcaliburMoveType.AIR_3_FALLING_STAR && st.fallingStarLanded;
    }

    public static void triggerFallingStarLanded(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st != null && st.active && st.move == ExcaliburMoveType.AIR_3_FALLING_STAR) {
            st.fallingStarLanded = true;
            st.startTick = clientTickCounter;
            st.durationTicks = 6.0F; // 0.30s giữ kiếm cắm trên vòng phép 2D!
        }
    }

    @Nullable
    public static FullBodyPose getLastComputedPose(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        return (st != null && (st.stanceBlend > 0.002F || st.prevStanceBlend > 0.002F || st.active)) ? st.lastPose : null;
    }

    public static float getActionProgress(int playerId, float partialTick) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st == null || !st.active) {
            return 0.0F;
        }
        float elapsed = (clientTickCounter - st.startTick) + partialTick;
        float alpha = elapsed / Math.max(1.0F, st.durationTicks);
        return (alpha >= 0.0F && alpha < 1.0F) ? alpha : 0.0F;
    }

    public static ExcaliburMoveType getMoveType(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        return st != null ? st.move : ExcaliburMoveType.HIT_1_SWEEP;
    }

    public static float getGuardBlend(int playerId, float partialTick) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        return smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevGuardBlend, st.guardBlend), 0.0F, 1.0F));
    }

    public static float getGuardOverheadBlend(int playerId, float partialTick) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        return smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevGuardOverheadBlend, st.guardOverheadBlend), 0.0F, 1.0F));
    }

    public static float getCrossHitRecoil(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        return st != null ? Mth.clamp(st.crossHitRecoilTicks / 3.0F, 0.0F, 1.0F) : 0.0F;
    }

    public static void triggerCrossGuardHit(int playerId) {
        ExcaliburAnimState st = STATES.computeIfAbsent(playerId, k -> new ExcaliburAnimState());
        st.crossHitRecoilTicks = 3.0F;
    }

    public static float getSprintBlend(int playerId, float partialTick) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        return smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevSprintBlend, st.sprintBlend), 0.0F, 1.0F));
    }

    public static boolean isCastingUltimate(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        return st != null && st.active && st.move.isUltimate();
    }

    public static float getGlowBoost(int playerId) {
        ExcaliburAnimState st = STATES.get(playerId);
        if (st == null) {
            return 0.0F;
        }
        if (st.active) {
            float alpha = Mth.clamp((clientTickCounter - st.startTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
            if (st.move.isUltimate()) {
                if (alpha < 0.346F) return Mth.lerp(alpha / 0.346F, 0.45F, 0.92F);
                if (alpha < 0.885F) return 1.0F;
                return 1.0F - Mth.clamp((alpha - 0.885F) / 0.115F, 0.0F, 1.0F);
            }
            if (st.move == ExcaliburMoveType.AIR_3_FALLING_STAR || st.move == ExcaliburMoveType.GOLDEN_CRESCENT
                    || st.move == ExcaliburMoveType.ROYAL_PARRY_COUNTER
                    || st.move == ExcaliburMoveType.AVALON_PARRY_COUNTER) {
                return 0.85F;
            }
            if (st.move == ExcaliburMoveType.HIT_3_THRUST || st.move == ExcaliburMoveType.HIT_4_SLAM
                    || st.move == ExcaliburMoveType.STRIKE_AIR) {
                return Mth.sin(alpha * Mth.PI) * 0.55F;
            }
        }
        return st.guardBlend * 0.35F;
    }

    public static void triggerMove(int playerId, ExcaliburMoveType move, float durationTicks) {
        ExcaliburAnimState st = STATES.computeIfAbsent(playerId, k -> new ExcaliburAnimState());
        st.blendFrom.copyFrom(st.lastPose);
        st.hasBlendFrom = (st.stanceBlend > 0.05F);
        st.active = true;
        st.move = move;
        st.startTick = clientTickCounter;
        st.durationTicks = Math.max(4.0F, durationTicks);
        st.hitStopRemaining = 0.0F;
        st.lastSeenTick = clientTickCounter;
        st.playedHit4Shake = false;
        st.playedUltStampShake = false;
        st.fallingStarLanded = false;
    }

    public static void triggerHitStop(int playerId, float freezeTicks) {
        ExcaliburAnimState st = STATES.get(playerId);
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
            boolean holding = isHoldingExcalibur(player);
            ExcaliburAnimState st = STATES.get(player.getId());
            if (!holding && st == null) {
                continue;
            }
            if (st == null) {
                st = new ExcaliburAnimState();
                STATES.put(player.getId(), st);
            }
            st.lastSeenTick = clientTickCounter;
            st.prevStanceBlend = st.stanceBlend;
            st.prevGuardBlend = st.guardBlend;
            st.prevGuardOverheadBlend = st.guardOverheadBlend;
            st.prevSprintBlend = st.sprintBlend;
            st.prevSheathedRunBlend = st.sheathedRunBlend;
            st.prevCrouchBlend = st.crouchBlend;
            if (st.crossHitRecoilTicks > 0.0F) {
                st.crossHitRecoilTicks = Math.max(0.0F, st.crossHitRecoilTicks - 1.0F);
            }

            ItemStack main = player.getMainHandItem();
            if (!holding && (main.is(ModItems.DUAL_KNIFE) || main.is(ModItems.TOGETSU) || main.is(ModItems.KATANA))) {
                st.stanceBlend = 0.0F;
                st.prevStanceBlend = 0.0F;
                st.guardBlend = 0.0F;
                st.prevGuardBlend = 0.0F;
                st.guardOverheadBlend = 0.0F;
                st.prevGuardOverheadBlend = 0.0F;
                st.sprintBlend = 0.0F;
                st.prevSprintBlend = 0.0F;
                st.sheathedRunBlend = 0.0F;
                st.prevSheathedRunBlend = 0.0F;
                st.crouchBlend = 0.0F;
                st.prevCrouchBlend = 0.0F;
                st.active = false;
                st.hasBlendFrom = false;
                continue;
            }

            boolean stanceActive = holding && !player.isSleeping() && !player.isSwimming() && !player.isFallFlying();
            st.stanceBlend = stanceActive
                    ? Math.min(1.0F, st.stanceBlend + 0.24F)
                    : Math.max(0.0F, st.stanceBlend - 0.22F);

            boolean guarding = holding && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    && (!st.active || (!st.move.isUltimate() && !st.move.isSheathing()
                    && st.move != ExcaliburMoveType.ROYAL_PARRY_COUNTER
                    && st.move != ExcaliburMoveType.AVALON_PARRY_COUNTER));
            st.guardBlend = guarding
                    ? Math.min(1.0F, st.guardBlend + 0.36F)
                    : Math.max(0.0F, st.guardBlend - 0.26F);

            // Đồng bộ trạng thái is_unsheathed: khi đang tra vỏ bấm RMB -> GIỮ NGUYÊN `is_unsheathed = false` để dùng Avalon Parry Guard!
            if (holding) {
                if (st.active && !st.move.isSheathing()) {
                    st.unsheathed = true;
                } else if (!st.active) {
                    st.unsheathed = ExcaliburClientState.isUnsheathed(player.getId(), main) || ExcaliburItem.isUnsheathed(main);
                }
            }

            boolean guardOverheadActive = guarding && st.unsheathed && player.getXRot() < -30.0F;
            st.guardOverheadBlend = guardOverheadActive
                    ? Math.min(1.0F, st.guardOverheadBlend + 0.36F)
                    : Math.max(0.0F, st.guardOverheadBlend - 0.26F);

            double horizSpeed = Math.sqrt(player.getDeltaMovement().horizontalDistanceSqr());
            boolean movingFast = player.walkAnimation.speed() > 0.18F || horizSpeed > 0.08;
            boolean sprintingNow = holding && player.isSprinting() && !guarding && !player.isCrouching()
                    && !player.isPassenger() && (!st.active || !st.move.lockedSkill);
            if (sprintingNow) {
                st.sprintGraceUntilTick = clientTickCounter + 6L;
            }
            boolean drawnSprintActive = holding && st.unsheathed && !guarding && !player.isCrouching() && !player.isPassenger()
                    && (!st.active || !st.move.lockedSkill)
                    && (sprintingNow || (clientTickCounter <= st.sprintGraceUntilTick && movingFast));
            st.sprintBlend = drawnSprintActive
                    ? Math.min(1.0F, st.sprintBlend + 0.18F)
                    : Math.max(0.0F, st.sprintBlend - 0.15F);

            // Chạy khi kiếm đang trong vỏ (`is_unsheathed == false`, đồng nhất dáng chạy tra vỏ của Katana khi đi/chạy)
            boolean sheathedRunActive = holding && !st.unsheathed && !guarding && !player.isCrouching() && !player.isPassenger()
                    && !st.active && !player.isSwimming() && !player.isFallFlying()
                    && (player.isSprinting() || horizSpeed > 0.08 || player.walkAnimation.speed() > 0.20F);
            st.sheathedRunBlend = sheathedRunActive
                    ? Math.min(1.0F, st.sheathedRunBlend + 0.28F)
                    : Math.max(0.0F, st.sheathedRunBlend - 0.26F);

            // Ngồi Shift (đồng nhất dáng ngồi cân đối của Katana cho cả lúc tra vỏ lẫn rút kiếm)
            boolean crouchActive = holding && player.isCrouching() && !guarding && !player.isPassenger()
                    && !st.active && !player.isSwimming() && !player.isFallFlying();
            st.crouchBlend = crouchActive
                    ? Math.min(1.0F, st.crouchBlend + 0.34F)
                    : Math.max(0.0F, st.crouchBlend - 0.30F);

            if (st.hitStopRemaining > 0.0F) {
                st.hitStopRemaining = Math.max(0.0F, st.hitStopRemaining - 1.0F);
                st.startTick++;
            }

            if (st.active) {
                float elapsed = clientTickCounter - st.startTick;
                float alpha = Mth.clamp(elapsed / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);

                if (st.move == ExcaliburMoveType.AIR_3_FALLING_STAR && !st.fallingStarLanded
                        && player.onGround() && elapsed >= 1.0F) {
                    st.fallingStarLanded = true;
                    st.startTick = clientTickCounter;
                    st.durationTicks = 6.0F;
                    if (player == mc.player) {
                        ClientRpgState.triggerShake(0.65F, 5);
                    }
                    elapsed = 0.0F;
                }

                if (st.move == ExcaliburMoveType.HIT_4_SLAM && holding && !st.playedHit4Shake && alpha >= 0.56F) {
                    st.playedHit4Shake = true;
                    if (player == mc.player) {
                        ClientRpgState.triggerShake(0.42F, 4);
                    }
                }

                if (st.move == ExcaliburMoveType.ULT_EXCALIBUR && holding) {
                    if (!st.playedUltStampShake && alpha >= 0.462F) {
                        st.playedUltStampShake = true;
                        if (player == mc.player) {
                            ClientRpgState.triggerShake(0.55F, 5);
                        }
                    }
                    if (alpha >= 0.519F && alpha <= 0.885F && ((clientTickCounter & 1L) == 0L)) {
                        if (player == mc.player) {
                            ClientRpgState.triggerShake(0.95F, 4);
                        } else if (mc.player != null && player.distanceToSqr(mc.player) < 576.0) {
                            ClientRpgState.triggerShake(0.45F, 3);
                        }
                    }
                }

                if (elapsed >= st.durationTicks || !holding) {
                    st.active = false;
                    st.hasBlendFrom = false;
                    st.fallingStarLanded = false;
                }
            }
        }

        Iterator<Map.Entry<Integer, ExcaliburAnimState>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            ExcaliburAnimState st = it.next().getValue();
            if ((clientTickCounter - st.lastSeenTick) > 60L
                    || (st.stanceBlend <= 0.001F && st.prevStanceBlend <= 0.001F && !st.active)) {
                it.remove();
            }
        }
    }

    @Nullable
    public static FullBodyPose evaluatePose(Player player, float partialTick, float limbSwingAmount, float ageInTicks) {
        ExcaliburAnimState st = STATES.get(player.getId());
        if (st == null) {
            return null;
        }
        float stance = Mth.clamp(Mth.lerp(partialTick, st.prevStanceBlend, st.stanceBlend), 0.0F, 1.0F);
        if (stance <= 0.002F && !st.active) {
            return null;
        }

        float guard = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevGuardBlend, st.guardBlend), 0.0F, 1.0F));
        float guardOverhead = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevGuardOverheadBlend, st.guardOverheadBlend), 0.0F, 1.0F));
        float sprint = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevSprintBlend, st.sprintBlend), 0.0F, 1.0F));
        float sheathedRun = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevSheathedRunBlend, st.sheathedRunBlend), 0.0F, 1.0F));
        float crouch = smootherstep(Mth.clamp(Mth.lerp(partialTick, st.prevCrouchBlend, st.crouchBlend), 0.0F, 1.0F));
        float walkSpeed = player.walkAnimation.speed(partialTick);

        // State Machine ưu tiên chuẩn:
        // - is_unsheathed == false: idle_sheathed <-> run_sheathed / crouch (giống Katana)
        // - is_unsheathed == true:  KP_COMBAT_IDLE <-> Noble Sprint / crouch (giống Katana) <-> Royal Guard
        if (st.unsheathed) {
            applyKeyPose(st.idlePose, KP_COMBAT_IDLE, 1.0F, 1.0F, 1.0F);
            if (sprint > 0.001F) {
                fillNobleSprintPose(st.sprintPose);
                st.idlePose.blendFrom(st.sprintPose, 1.0F - sprint);
            }
            if (crouch > 0.001F) {
                fillKatanaStyleCrouchPose(st.crouchPose, true);
                st.idlePose.blendFrom(st.crouchPose, 1.0F - crouch);
            }
        } else {
            applyKeyPose(st.idlePose, KP_SHEATHED_IDLE, 1.0F, 1.0F, 1.0F);
            if (sheathedRun > 0.001F) {
                fillSheathedRunPose(st.sheathedRunPose);
                st.idlePose.blendFrom(st.sheathedRunPose, 1.0F - sheathedRun);
            }
            if (crouch > 0.001F) {
                fillKatanaStyleCrouchPose(st.crouchPose, false);
                st.idlePose.blendFrom(st.crouchPose, 1.0F - crouch);
            }
        }
        if (guard > 0.001F) {
            if (!st.unsheathed) {
                // Avalon Parry Guard (`is_unsheathed == false`): Đưa vỏ Avalon lên gạt chéo 45° trước ngực + tay phải nắm chặt chuôi Excalibur nạp lực!
                applyKeyPose(st.guardPose, KP_AVALON_PARRY_GUARD, 1.0F, 1.0F, 1.0F);
                if (st.crossHitRecoilTicks > 0.01F) {
                    float rec = Mth.sin(Mth.clamp(st.crossHitRecoilTicks / 3.0F, 0.0F, 1.0F) * Mth.PI);
                    st.guardPose.rootTranslateY -= rec * 0.024F;
                    st.guardPose.bodyX -= rec * 0.05F;
                    st.guardPose.leftArmX += rec * 0.08F;
                }
            } else {
                // A. Đỡ Trực Diện (45° trước ngực, lộ mặt 100%)
                applyKeyPose(st.guardPose, KP_FRONT_GUARD, 1.0F, 1.0F, 1.0F);
                if (st.crossHitRecoilTicks > 0.01F) {
                    float rec = Mth.sin(Mth.clamp(st.crossHitRecoilTicks / 3.0F, 0.0F, 1.0F) * Mth.PI);
                    st.guardPose.rootTranslateY -= rec * 0.025F;
                    st.guardPose.bodyX += rec * 0.05F;
                    st.guardPose.rightBladePitchDeg -= rec * 4.5F;
                }
                // B. Đỡ Trên Cao (Pitch < -30° -> Hai tay nâng qua trán, kiếm nằm ngang mặt phẳng rộng trên đỉnh đầu)
                if (guardOverhead > 0.001F) {
                    applyKeyPose(st.guardAltPose, KP_OVERHEAD_GUARD, 1.0F, 1.0F, 1.0F);
                    st.guardPose.blendFrom(st.guardAltPose, 1.0F - guardOverhead);
                }
            }
            st.guardPose.fovOffsetDeg = -2.4F;
            st.idlePose.blendFrom(st.guardPose, 1.0F - guard);
        }

        FullBodyPose out = st.outPose;
        if (st.active) {
            float alpha = Mth.clamp(((clientTickCounter - st.startTick) + partialTick) / Math.max(1.0F, st.durationTicks), 0.0F, 1.0F);
            fillActionPose(st.actionPose, st.move, alpha, ageInTicks, st.fallingStarLanded);
            out.copyFrom(st.actionPose);

            if (alpha < 0.12F && st.hasBlendFrom
                    && st.move != ExcaliburMoveType.ROYAL_PARRY_COUNTER
                    && st.move != ExcaliburMoveType.AVALON_PARRY_COUNTER) {
                out.blendFrom(st.blendFrom, smootherstep(alpha / 0.12F));
            } else if (!st.move.isUltimate()
                    && !st.move.isSheathing()
                    && st.move != ExcaliburMoveType.HIT_4_SLAM
                    && st.move != ExcaliburMoveType.AIR_3_FALLING_STAR
                    && st.move != ExcaliburMoveType.GOLDEN_CRESCENT
                    && st.move != ExcaliburMoveType.STRIKE_AIR
                    && st.move != ExcaliburMoveType.ROYAL_PARRY_COUNTER
                    && st.move != ExcaliburMoveType.AVALON_PARRY_COUNTER
                    && alpha > 0.86F) {
                out.blendFrom(st.idlePose, 1.0F - smootherstep((alpha - 0.86F) / 0.14F));
            }
            out.isAttacking = true;
            out.overrideVanilla = true;
        } else {
            out.copyFrom(st.idlePose);
            out.isAttacking = false;
            float sw = smootherstep(stance);
            float standFactor = 1.0F - Mth.clamp(walkSpeed * 2.5F, 0.0F, 1.0F);
            out.armWeight = sw;
            out.elbowWeight = sw * (1.0F - sprint);
            out.rootRollDeg *= sw;
            float rootWeight = (guard > 0.25F) ? 1.0F : standFactor * (1.0F - Math.max(sprint, sheathedRun));
            out.rootTranslateY *= sw * rootWeight;
            out.bodyX *= sw;
            out.bodyY *= sw;
            out.bodyZ *= sw;
            out.headAddX *= sw;
            out.headAddY *= sw;
            out.headAddZ *= sw;
            out.rightArmPosX *= sw;
            out.rightArmPosY *= sw;
            out.rightArmPosZ *= sw;
            out.leftArmPosX *= sw;
            out.leftArmPosY *= sw;
            out.leftArmPosZ *= sw;
            float baseLeg = (guard > 0.25F) ? Math.max(0.85F, standFactor) : standFactor * (1.0F - Math.max(sprint, sheathedRun));
            out.legWeight = sw * baseLeg;
            out.kneeWeight = out.legWeight * (st.unsheathed ? 1.0F : (1.0F - crouch));
            // Tắt rung tay/chân vanilla khi Đỡ hoặc Đứng Chờ yên vị; khi chạy tra vỏ hoặc ngồi Shift giữ nhịp chân vanilla tự nhiên y hệt Katana!
            out.overrideVanilla = (sw > 0.75F)
                    && (guard > 0.25F || (standFactor > 0.65F && sprint < 0.15F && sheathedRun < 0.15F && crouch < 0.15F));
        }

        st.lastPose.copyFrom(out);
        return out;
    }

    private static void fillActionPose(FullBodyPose p, ExcaliburMoveType move, float alpha,
                                       float ageInTicks, boolean fallingStarLanded) {
        switch (move) {
            case HIT_1_SWEEP -> {
                // 0.00-0.10s (0..0.286): Rút vặn phải +32° | 0.10-0.22s (0.286..0.629): Xoay giật trái -35° | 0.22-0.35s: Giữ vai trái
                if (alpha < 0.286F) {
                    lerpKeyPose(p, KP_SHEATHED_IDLE, KP_H1_WIND, easeOutCubic(alpha / 0.286F), 1.0F, 0.85F, 0.85F);
                } else if (alpha < 0.457F) {
                    lerpKeyPose(p, KP_H1_WIND, KP_H1_MID, easeOutQuart((alpha - 0.286F) / 0.171F), 1.0F, 0.88F, 0.88F);
                } else if (alpha < 0.629F) {
                    lerpKeyPose(p, KP_H1_MID, KP_H1_END, easeOutQuart((alpha - 0.457F) / 0.172F), 1.0F, 0.88F, 0.88F);
                } else {
                    float hold = (alpha - 0.629F) / 0.371F;
                    applyKeyPose(p, KP_H1_END, 1.0F, 0.85F, 0.85F);
                    p.bodyY -= Mth.sin(hold * Mth.PI) * 0.04F;
                }
                p.firstPersonCameraRollDeg = Mth.sin(Mth.clamp((alpha - 0.28F) / 0.36F, 0.0F, 1.0F) * Mth.PI) * -2.4F;
            }
            case HIT_2_RISING -> {
                // 0.00-0.08s (0..0.229): Hạ cổ tay hông trái (+18°) | 0.08-0.20s (0.229..0.571): Bung xoay phải (+38°) bước chân phải (-24°)
                if (alpha < 0.229F) {
                    lerpKeyPose(p, KP_H1_END, KP_H2_DIP, easeOutCubic(alpha / 0.229F), 1.0F, 0.86F, 0.86F);
                } else if (alpha < 0.571F) {
                    lerpKeyPose(p, KP_H2_DIP, KP_H2_END, easeOutQuart((alpha - 0.229F) / 0.342F), 1.0F, 0.88F, 0.88F);
                } else {
                    float hold = (alpha - 0.571F) / 0.429F;
                    applyKeyPose(p, KP_H2_END, 1.0F, 0.84F, 0.84F);
                    p.bodyY += Mth.sin(hold * Mth.PI) * 0.04F;
                }
                p.firstPersonCameraRollDeg = Mth.sin(Mth.clamp((alpha - 0.22F) / 0.36F, 0.0F, 1.0F) * Mth.PI) * +2.6F;
            }
            case HIT_3_THRUST -> {
                // 0.00-0.12s (0..0.300): Thu chuôi sát sườn phải (-90°, Body Y -1.5) | 0.12-0.26s (0.300..0.650): Lao đâm (+16°)
                if (alpha < 0.300F) {
                    lerpKeyPose(p, KP_H2_END, KP_H3_COIL, easeOutCubic(alpha / 0.300F), 1.0F, 0.90F, 0.90F);
                } else if (alpha < 0.650F) {
                    float t = easeOutExpo((alpha - 0.300F) / 0.350F);
                    lerpKeyPose(p, KP_H3_COIL, KP_H3_THRUST, t, 1.0F, 0.92F, 0.92F);
                    p.fovOffsetDeg = Mth.sin(t * Mth.PI) * 4.2F;
                } else {
                    float t = smootherstep((alpha - 0.650F) / 0.350F);
                    lerpKeyPose(p, KP_H3_THRUST, KP_COMBAT_IDLE, t * 0.55F, 1.0F, 0.86F, 0.86F);
                }
            }
            case HIT_4_SLAM -> {
                // 0.00-0.18s (0..0.360): Bật nhảy nhẹ (+2.0), nâng thẳng qua đỉnh đầu (-160°) | 0.18-0.30s (0.360..0.600): Bổ dọc xuống đầu gối (+22°)
                if (alpha < 0.360F) {
                    lerpKeyPose(p, KP_H3_THRUST, KP_H4_LEAP, easeOutCubic(alpha / 0.360F), 1.0F, 0.88F, 0.88F);
                } else if (alpha < 0.600F) {
                    float t = easeOutQuart((alpha - 0.360F) / 0.240F);
                    lerpKeyPose(p, KP_H4_LEAP, KP_H4_SLAM, t, 1.0F, 0.94F, 0.94F);
                    p.fovOffsetDeg = Mth.sin(t * Mth.PI) * 4.8F;
                } else if (alpha < 0.820F) {
                    float stopT = (alpha - 0.600F) / 0.220F;
                    applyKeyPose(p, KP_H4_SLAM, 1.0F, 0.94F, 0.94F);
                    float shiver = Mth.sin(stopT * Mth.TWO_PI * 2.5F) * (1.0F - stopT);
                    p.rightBladePitchDeg += shiver * 1.6F;
                    p.bodyX += shiver * 0.02F;
                } else {
                    lerpKeyPose(p, KP_H4_SLAM, KP_COMBAT_IDLE, smootherstep((alpha - 0.820F) / 0.180F), 1.0F, 0.88F, 0.88F);
                }
            }
            case STRIKE_AIR -> {
                // Nhánh A (0.45s / 9t): 0.00-0.20s (0..0.444) lấy đà phải (+35°, -90°) -> 0.20-0.35s (0.444..0.778) đâm hết sải tay -> 0.778..1.00 thu kiếm!
                if (alpha < 0.444F) {
                    float u = easeOutCubic(alpha / 0.444F);
                    lerpKeyPose(p, KP_COMBAT_IDLE, KP_H3_COIL, u, 1.0F, 0.92F, 0.92F);
                    p.fovOffsetDeg = -2.8F * u;
                } else if (alpha < 0.778F) {
                    float u = easeOutExpo((alpha - 0.444F) / 0.334F);
                    lerpKeyPose(p, KP_H3_COIL, KP_H3_THRUST, u, 1.0F, 0.94F, 0.94F);
                    p.fovOffsetDeg = Mth.lerp(u, -2.8F, +5.4F);
                } else {
                    lerpKeyPose(p, KP_H3_THRUST, KP_COMBAT_IDLE, smootherstep((alpha - 0.778F) / 0.222F), 1.0F, 0.88F, 0.88F);
                }
            }
            case GOLDEN_CRESCENT -> {
                // Nhánh B (0.60s / 12t):
                // 0.00-0.28s (0..0.467): Quét tròn 360° (Body Yaw -35° -> -395°)
                // 0.28-0.60s (0.467..1.00): Nâng qua đầu (0.467..0.667) -> Bổ thẳng xuống đất (0.667..0.817) -> Thu thế (0.817..1.00)
                if (alpha < 0.467F) {
                    float spinT = alpha / 0.467F;
                    applyKeyPose(p, KP_H1_END, 1.0F, 0.92F, 0.92F);
                    p.rootYawDeg = -360.0F * smootherstep(spinT);
                    p.fovOffsetDeg = Mth.sin(spinT * Mth.PI) * 3.8F;
                    p.firstPersonCameraRollDeg = Mth.sin(spinT * Mth.PI) * -3.6F;
                } else if (alpha < 0.667F) {
                    float u = easeOutCubic((alpha - 0.467F) / 0.200F);
                    lerpKeyPose(p, KP_H1_END, KP_H4_LEAP, u, 1.0F, 0.92F, 0.92F);
                } else if (alpha < 0.817F) {
                    float u = easeOutQuart((alpha - 0.667F) / 0.150F);
                    lerpKeyPose(p, KP_H4_LEAP, KP_H4_SLAM, u, 1.0F, 0.96F, 0.96F);
                    p.fovOffsetDeg = Mth.sin(u * Mth.PI) * 4.8F;
                } else {
                    lerpKeyPose(p, KP_H4_SLAM, KP_COMBAT_IDLE, smootherstep((alpha - 0.817F) / 0.183F), 1.0F, 0.88F, 0.88F);
                }
            }
            case AIR_1_DIAG -> {
                // Nhánh C1 (0.25s / 5t): Chém chéo Trên Phải -> Dưới Trái trên không
                if (alpha < 0.24F) {
                    lerpKeyPose(p, KP_COMBAT_IDLE, KP_AIR2_END, easeOutCubic(alpha / 0.24F), 1.0F, 0.88F, 0.88F);
                } else {
                    float u = easeOutExpo(Mth.clamp((alpha - 0.24F) / 0.56F, 0.0F, 1.0F));
                    lerpKeyPose(p, KP_AIR2_END, KP_H2_DIP, u, 1.0F, 0.90F, 0.90F);
                    p.firstPersonCameraRollDeg = Mth.sin(u * Mth.PI) * -3.0F;
                }
            }
            case AIR_2_DIAG -> {
                // Nhánh C2 (0.25s / 5t): Chém chéo ngược Dưới Trái -> Trên Phải trên không
                float u = easeOutExpo(Mth.clamp(alpha / 0.72F, 0.0F, 1.0F));
                lerpKeyPose(p, KP_H2_DIP, KP_AIR2_END, u, 1.0F, 0.90F, 0.90F);
                p.firstPersonCameraRollDeg = Mth.sin(u * Mth.PI) * +3.0F;
            }
            case AIR_3_FALLING_STAR -> {
                // Nhánh C3: Chĩa thẳng mũi kiếm xuống đất (Pitch -10°) lao vút xuống -> chạm đất giữ trên vòng phép 2D 0.3s!
                if (!fallingStarLanded) {
                    float u = easeOutQuart(Mth.clamp(alpha * 5.0F, 0.0F, 1.0F));
                    lerpKeyPose(p, KP_AIR2_END, KP_AIR3_PLUNGE, u, 1.0F, 0.92F, 0.94F);
                    p.fovOffsetDeg = 6.2F * u;
                } else if (alpha < 0.65F) {
                    float impT = alpha / 0.65F;
                    applyKeyPose(p, KP_AIR3_LAND, 1.0F, 0.98F, 0.98F);
                    float shiver = Mth.sin(impT * Mth.TWO_PI * 2.5F) * (1.0F - impT);
                    p.rightBladePitchDeg += shiver * 2.0F;
                    p.fovOffsetDeg = 4.8F * (1.0F - impT);
                } else {
                    lerpKeyPose(p, KP_AIR3_LAND, KP_COMBAT_IDLE, smootherstep((alpha - 0.65F) / 0.35F), 1.0F, 0.90F, 0.90F);
                }
            }
            case ROYAL_PARRY_COUNTER -> {
                // 5C — Royal Parry Counter 2 nhịp liên hoàn (0.40s / 8 ticks):
                // Nhịp 1 (0.00-0.15s = 0.000..0.375): Xoay giật Body Yaw +35° phải, gạt văng vũ khí địch!
                // Nhịp 2 (0.15-0.40s = 0.375..1.000): Vặn ngược thân sang trái Yaw -40° tung nhát chém ngang 200% ST!
                if (alpha < 0.375F) {
                    float u = easeOutExpo(alpha / 0.375F);
                    lerpKeyPose(p, KP_FRONT_GUARD, KP_PARRY_BEAT1, u, 1.0F, 0.96F, 0.96F);
                    p.firstPersonCameraRollDeg = Mth.sin(u * Mth.PI) * +3.4F;
                    p.fovOffsetDeg = -2.4F * Mth.sin(u * Mth.PI);
                } else if (alpha < 0.560F) {
                    float u = easeOutQuart((alpha - 0.375F) / 0.185F);
                    lerpKeyPose(p, KP_PARRY_BEAT1, KP_H1_MID, u, 1.0F, 0.96F, 0.96F);
                    p.fovOffsetDeg = Mth.lerp(u, 0.0F, +5.8F);
                    p.firstPersonCameraRollDeg = Mth.lerp(u, +1.5F, -4.2F);
                } else if (alpha < 0.800F) {
                    float u = easeOutExpo((alpha - 0.560F) / 0.240F);
                    lerpKeyPose(p, KP_H1_MID, KP_PARRY_BEAT2, u, 1.0F, 0.96F, 0.96F);
                    p.fovOffsetDeg = 5.8F * (1.0F - u * 0.45F);
                    p.firstPersonCameraRollDeg = -4.2F * (1.0F - u * 0.5F);
                } else {
                    lerpKeyPose(p, KP_PARRY_BEAT2, KP_COMBAT_IDLE, smootherstep((alpha - 0.800F) / 0.200F), 1.0F, 0.90F, 0.90F);
                }
            }
            case AVALON_PARRY_COUNTER -> {
                // PHẦN I — Avalon Parry & Rút Kiếm Phản Công Iaido (0.65s / 13 ticks):
                // 0.000..0.140: Bứt tốc giật vỏ Avalon ra sau hông trái & rút Excalibur khỏi miệng vỏ!
                // 0.140..0.520: Bùng nổ chém ngang cực rộng từ Trái -> Phải ngang tầm ngực!
                // 0.520..0.640: Giữ đà phản công sắc lẹm!
                // 0.640..1.000 (0.45s–0.65s): Thu kiếm mượt về KP_COMBAT_IDLE (rút kiếm), sẵn sàng nối combo 4 hit!
                if (alpha < 0.140F) {
                    float u = easeOutExpo(alpha / 0.140F);
                    lerpKeyPose(p, KP_AVALON_PARRY_GUARD, KP_AVALON_COUNTER_START, u, 1.0F, 0.96F, 0.96F);
                    p.fovOffsetDeg = Mth.lerp(u, -2.4F, +5.8F);
                    p.firstPersonCameraRollDeg = +2.8F * (1.0F - u);
                } else if (alpha < 0.320F) {
                    float u = easeOutQuart((alpha - 0.140F) / 0.180F);
                    lerpKeyPose(p, KP_AVALON_COUNTER_START, KP_AVALON_COUNTER_MID, u, 1.0F, 0.96F, 0.96F);
                    p.fovOffsetDeg = +5.8F;
                    p.firstPersonCameraRollDeg = Mth.lerp(u, -2.5F, +3.8F);
                } else if (alpha < 0.520F) {
                    float u = easeOutExpo((alpha - 0.320F) / 0.200F);
                    lerpKeyPose(p, KP_AVALON_COUNTER_MID, KP_AVALON_COUNTER_END, u, 1.0F, 0.96F, 0.96F);
                    p.fovOffsetDeg = +5.8F * (1.0F - u * 0.40F);
                    p.firstPersonCameraRollDeg = +3.8F * (1.0F - u * 0.50F);
                } else if (alpha < 0.640F) {
                    float hold = (alpha - 0.520F) / 0.120F;
                    applyKeyPose(p, KP_AVALON_COUNTER_END, 1.0F, 0.94F, 0.94F);
                    p.fovOffsetDeg = +3.2F * (1.0F - hold);
                    p.firstPersonCameraRollDeg = +1.9F * (1.0F - hold);
                } else {
                    float rec = smootherstep((alpha - 0.640F) / 0.360F);
                    lerpKeyPose(p, KP_AVALON_COUNTER_END, KP_COMBAT_IDLE, rec, 1.0F, 0.90F, 0.90F);
                }
            }
            case GUARD_OVERHEAD_IMPACT -> {
                applyKeyPose(p, KP_OVERHEAD_GUARD, 1.0F, 1.0F, 1.0F);
                float dip = (alpha < 0.45F)
                        ? easeOutQuart(alpha / 0.45F)
                        : (1.0F - smootherstep((alpha - 0.45F) / 0.55F));
                p.rootTranslateY -= dip * 0.065F;
                p.rightArmX += dip * 0.18F;
                p.leftArmX += dip * 0.18F;
                p.rightBladePitchDeg -= dip * 6.0F;
                p.bodyX += dip * 0.06F;
                p.fovOffsetDeg = -3.2F * dip;
            }
            case SHEATHE_AVALON -> {
                // PHẦN I — Cất Kiếm Vào Vỏ Avalon (0.55s / 11 ticks):
                // GĐ 1 (0.00–0.18s = 0.000..0.327): Đưa vỏ Avalon ra đón (-42,+22,-18), tay phải xoay cổ tay hướng mũi vào miệng vỏ (-85,-25,+35)
                // GĐ 2 (0.18–0.42s = 0.327..0.764): Kéo khuỷu phải (-35,-15) đẩy thẳng lưỡi trượt vào vỏ Avalon -> khóa "Clink!" ở 0.40s (0.727)!
                // GĐ 3 (0.42–0.55s = 0.764..1.000): Buông tay phải về sườn phải (+5,+12), hạ tay trái & vỏ về Idle (-15,-14)!
                if (alpha < 0.327F) {
                    float u = easeOutCubic(alpha / 0.327F);
                    lerpKeyPose(p, KP_COMBAT_IDLE, KP_SHEATHE_P1, u, 1.0F, 0.90F, 0.90F);
                } else if (alpha < 0.764F) {
                    float u = smootherstep((alpha - 0.327F) / 0.437F);
                    lerpKeyPose(p, KP_SHEATHE_P1, KP_SHEATHE_P2, u, 1.0F, 0.90F, 0.90F);
                    if (alpha >= 0.700F) {
                        float clink = Mth.sin(((alpha - 0.700F) / 0.064F) * Mth.PI);
                        p.leftArmX += clink * 0.03F;
                        p.rightArmX += clink * 0.03F;
                    }
                } else {
                    float u = smootherstep((alpha - 0.764F) / 0.236F);
                    lerpKeyPose(p, KP_SHEATHE_P2, KP_SHEATHED_IDLE, u, 1.0F, 0.90F, 0.90F);
                }
            }
            case ULT_EXCALIBUR -> {
                // 4. Tuyệt Kỹ Excalibur (2.60s / 52 ticks):
                if (alpha < 0.346F) {
                    float raise = easeOutCubic(Mth.clamp(alpha / 0.120F, 0.0F, 1.0F));
                    lerpKeyPose(p, KP_COMBAT_IDLE, KP_ULT_P1, raise, 1.0F, 0.96F, 0.96F);
                    if (alpha > 0.22F) {
                        float chargeVib = (alpha - 0.22F) / 0.126F;
                        p.rightBladeRollDeg += Mth.sin(ageInTicks * 2.5F) * 0.45F * chargeVib;
                    }
                    p.fovOffsetDeg = -4.8F * raise;
                } else if (alpha < 0.462F) {
                    float tilt = smootherstep((alpha - 0.346F) / 0.116F);
                    lerpKeyPose(p, KP_ULT_P1, KP_ULT_P2, tilt, 1.0F, 0.96F, 0.96F);
                    p.fovOffsetDeg = Mth.lerp(tilt, -4.8F, -7.8F);
                } else if (alpha < 0.519F) {
                    float cleave = easeOutQuart((alpha - 0.462F) / 0.057F);
                    lerpKeyPose(p, KP_ULT_P2, KP_ULT_P3, cleave, 1.0F, 0.98F, 0.98F);
                    p.fovOffsetDeg = Mth.lerp(cleave, -7.8F, +8.8F);
                } else if (alpha < 0.885F) {
                    applyKeyPose(p, KP_ULT_P3, 1.0F, 0.98F, 0.98F);
                    float recoil = Mth.sin(ageInTicks * 3.8F);
                    float recoil2 = Mth.cos(ageInTicks * 5.0F);
                    p.rightArmX += recoil * 0.018F;
                    p.leftArmX += recoil * 0.018F;
                    p.rightBladePitchDeg += recoil2 * 1.15F;
                    p.bodyX += recoil * 0.012F;
                    p.fovOffsetDeg = 8.8F + recoil * 0.55F;
                } else {
                    float rec = smootherstep((alpha - 0.885F) / 0.115F);
                    lerpKeyPose(p, KP_ULT_P3, KP_COMBAT_IDLE, rec, 1.0F, 0.92F, 0.92F);
                    p.fovOffsetDeg = 8.8F * (1.0F - rec);
                }
            }
        }
    }

    private static void applyKeyPose(FullBodyPose p, KeyPose k, float elbowW, float kneeW, float legW) {
        p.reset();
        p.armWeight = 1.0F;
        p.legWeight = legW;
        p.elbowWeight = elbowW;
        p.kneeWeight = kneeW;

        p.rootTranslateY = k.rootY;
        p.bodyX = k.bodyX;
        p.bodyY = k.bodyY;
        p.bodyZ = k.bodyZ;
        p.headAddX = k.headX;
        p.headAddY = k.headY;

        p.rightLegX = k.rLegX;
        p.rightLegY = k.rLegY;
        p.rightLegZ = k.rLegZ;
        p.rightKneeX = k.rKneeX;
        p.rightLegPosY = k.rLegPosY;
        p.rightLegPosZ = k.rLegPosZ;

        p.leftLegX = k.lLegX;
        p.leftLegY = k.lLegY;
        p.leftLegZ = k.lLegZ;
        p.leftKneeX = k.lKneeX;
        p.leftLegPosY = k.lLegPosY;
        p.leftLegPosZ = k.lLegPosZ;

        p.rightArmX = k.rArmX;
        p.rightArmY = k.rArmY;
        p.rightArmZ = k.rArmZ;
        p.rightElbowX = k.rElbX;
        p.rightElbowZ = k.rElbZ;
        p.rightArmPosX = k.rPosX;
        p.rightArmPosY = k.rPosY;
        p.rightArmPosZ = k.rPosZ;

        p.leftArmX = k.lArmX;
        p.leftArmY = k.lArmY;
        p.leftArmZ = k.lArmZ;
        p.leftElbowX = k.lElbX;
        p.leftElbowZ = k.lElbZ;
        p.leftArmPosX = k.lPosX;
        p.leftArmPosY = k.lPosY;
        p.leftArmPosZ = k.lPosZ;

        p.rightBladePitchDeg = k.bladeP;
        p.rightBladeRollDeg = k.bladeR;
        p.rightBladeYawDeg = k.bladeY;
        p.rightBladeOffX = k.bladeOffX;
        p.rightBladeOffY = k.bladeOffY;
        p.rightBladeOffZ = k.bladeOffZ;
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
        p.rightLegY = Mth.lerp(t, a.rLegY, b.rLegY);
        p.rightLegZ = Mth.lerp(t, a.rLegZ, b.rLegZ);
        p.rightKneeX = Mth.lerp(t, a.rKneeX, b.rKneeX);
        p.rightLegPosY = Mth.lerp(t, a.rLegPosY, b.rLegPosY);
        p.rightLegPosZ = Mth.lerp(t, a.rLegPosZ, b.rLegPosZ);

        p.leftLegX = Mth.lerp(t, a.lLegX, b.lLegX);
        p.leftLegY = Mth.lerp(t, a.lLegY, b.lLegY);
        p.leftLegZ = Mth.lerp(t, a.lLegZ, b.lLegZ);
        p.leftKneeX = Mth.lerp(t, a.lKneeX, b.lKneeX);
        p.leftLegPosY = Mth.lerp(t, a.lLegPosY, b.lLegPosY);
        p.leftLegPosZ = Mth.lerp(t, a.lLegPosZ, b.lLegPosZ);

        p.rightArmX = Mth.lerp(t, a.rArmX, b.rArmX);
        p.rightArmY = Mth.lerp(t, a.rArmY, b.rArmY);
        p.rightArmZ = Mth.lerp(t, a.rArmZ, b.rArmZ);
        p.rightElbowX = Mth.lerp(t, a.rElbX, b.rElbX);
        p.rightElbowZ = Mth.lerp(t, a.rElbZ, b.rElbZ);
        p.rightArmPosX = Mth.lerp(t, a.rPosX, b.rPosX);
        p.rightArmPosY = Mth.lerp(t, a.rPosY, b.rPosY);
        p.rightArmPosZ = Mth.lerp(t, a.rPosZ, b.rPosZ);

        p.leftArmX = Mth.lerp(t, a.lArmX, b.lArmX);
        p.leftArmY = Mth.lerp(t, a.lArmY, b.lArmY);
        p.leftArmZ = Mth.lerp(t, a.lArmZ, b.lArmZ);
        p.leftElbowX = Mth.lerp(t, a.lElbX, b.lElbX);
        p.leftElbowZ = Mth.lerp(t, a.lElbZ, b.lElbZ);
        p.leftArmPosX = Mth.lerp(t, a.lPosX, b.lPosX);
        p.leftArmPosY = Mth.lerp(t, a.lPosY, b.lPosY);
        p.leftArmPosZ = Mth.lerp(t, a.lPosZ, b.lPosZ);

        p.rightBladePitchDeg = Mth.rotLerp(t, a.bladeP, b.bladeP);
        p.rightBladeRollDeg = Mth.rotLerp(t, a.bladeR, b.bladeR);
        p.rightBladeYawDeg = Mth.rotLerp(t, a.bladeY, b.bladeY);
        p.rightBladeOffX = Mth.lerp(t, a.bladeOffX, b.bladeOffX);
        p.rightBladeOffY = Mth.lerp(t, a.bladeOffY, b.bladeOffY);
        p.rightBladeOffZ = Mth.lerp(t, a.bladeOffZ, b.bladeOffZ);
    }

    /**
     * Dáng chạy khi kiếm trong vỏ (`run_sheathed`) — đồng nhất dáng chạy tra vỏ của **Damascus Katana**:
     * - **Body & Head:** Giữ thẳng tự nhiên (`0°`), không gập lưng.
     * - **Legs:** Nhường hoàn toàn cho hoạt ảnh chạy vanilla (`legWeight = 0`, `kneeWeight = 0`) mượt mà theo tốc độ di chuyển.
     * - **RightArm & LeftArm:** Tay phải vươn gọn qua trước eo nắm chặt chuôi Excalibur sẵn sàng rút kiếm,
     *   tay trái gập khuỷu ôm khít cổ vỏ Avalon bên hông trái (khớp 100% vị trí vỏ, không xuyên thân).
     */
    private static void fillSheathedRunPose(FullBodyPose p) {
        p.reset();
        p.armWeight = 1.0F;
        p.legWeight = 0.0F;
        p.elbowWeight = 1.0F;
        p.kneeWeight = 0.0F;

        p.rootTranslateY = 0.0F;
        p.bodyX = 0.0F;
        p.bodyY = 0.0F;
        p.bodyZ = 0.0F;
        p.headAddX = 0.0F;
        p.headAddY = 0.0F;

        // RightArm: Vươn qua trước eo nắm chuôi Excalibur sẵn sàng rút kiếm (giống Katana KP_SHEATHED_IDLE)
        p.rightArmX = -0.920F;
        p.rightArmY = -0.894F;
        p.rightArmZ = +0.080F;
        p.rightElbowX = -0.170F;
        p.rightElbowZ = -0.220F;
        p.rightArmPosX = +0.50F;
        p.rightArmPosZ = -0.55F;

        // LeftArm: Gập khuỷu ôm khít cổ vỏ Avalon bên hông trái (giống Katana KP_SHEATHED_IDLE)
        p.leftArmX = +0.541F;
        p.leftArmY = +1.050F;
        p.leftArmZ = -0.020F;
        p.leftElbowX = -1.093F;
        p.leftElbowZ = +0.010F;
        p.leftArmPosX = +0.02F;
        p.leftArmPosZ = +0.18F;
    }

    /**
     * Dáng ngồi Shift của **Excalibur** — đồng nhất dáng ngồi của **Damascus Katana**:
     * - Khi tra trong vỏ (`!drawn`): Thân & đầu thẳng cân đối (`0°`) nhận trọn độ cúi vanilla không vặn hông,
     *   chân đứng thẳng (`+0.14 / -0.14`, không gập gối) & hòa mượt bước đi cúi vanilla, 2 tay đặt sẵn trên chuôi và cổ vỏ Avalon.
     * - Khi đã rút kiếm (`drawn`): Thân hạ gọn không vặn lệch mạnh, giữ kiếm 2 tay phía trước theo dáng Chudan của Katana.
     */
    private static void fillKatanaStyleCrouchPose(FullBodyPose p, boolean drawn) {
        if (!drawn) {
            fillSheathedRunPose(p);
            p.legWeight = 1.0F;
            p.kneeWeight = 0.0F;
            p.rightLegX = +0.140F;
            p.leftLegX = -0.140F;
            p.rightLegZ = +0.10F;
            p.leftLegZ = -0.10F;
            return;
        }
        applyKeyPose(p, KP_COMBAT_IDLE, 1.0F, 0.85F, 1.0F);
        p.rootTranslateY = 0.0F;
        p.bodyX = +0.070F;
        p.bodyY = +0.140F;
        p.bodyZ = 0.0F;
        p.headAddX = -0.035F;
        p.headAddY = -0.105F;
        p.rightLegX = -0.436F;
        p.leftLegX = +0.436F;
        p.rightLegY = 0.0F;
        p.leftLegY = 0.0F;
        p.rightLegZ = +0.10F;
        p.leftLegZ = -0.10F;
        p.rightKneeX = +0.34F;
        p.leftKneeX = +0.26F;
    }

    private static void fillNobleSprintPose(FullBodyPose p) {
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
        p.fovOffsetDeg = 2.4F;
    }

    private static float smootherstep(float t) {
        float x = Mth.clamp(t, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    private static float easeOutCubic(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u * u;
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
