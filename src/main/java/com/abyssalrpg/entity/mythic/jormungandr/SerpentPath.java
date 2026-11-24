package com.abyssalrpg.entity.mythic.jormungandr;

import com.abyssalrpg.config.JormungandrConfig;
import com.abyssalrpg.network.JormungandrPathSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Hệ thống đường dẫn thân rắn (SerpentPath — Server là nguồn chân lý, Client nội suy Catmull-Rom mượt).
 *
 * Kiến trúc khắc phục triệt để lỗi đứt đốt, chìm đất & cổ ngẩng bậc thang:
 * 1. Tách biệt tuyệt đối độ thon mặt cắt (`diameterScale` -> trục X/Y) với chiều dài động từng đốt
 *    (`lengthScale = (dist3D / worldBaseSegLen) * overlapFactor` -> trục Z), KHÔNG bao giờ thu ngắn Z theo taper.
 * 2. Đặt mỗi đốt tại trung điểm `(P[i-1] + P[i]) / 2` và tính hướng `yaw` (unwrapped) + `pitch`
 *    trực tiếp từ vector nối `P[i-1] - P[i]`.
 * 3. Vòng cuộn đứng yên (`idle_coil`) tích phân đúng theo độ dài cung (constant arc-length) — triệt tiêu kéo giãn đuôi.
 * 4. Đoạn cổ ngẩng (`REAR_NECK_SEGMENTS = 9`) dựng theo đường cong Cubic Bézier chữ S đơn điệu về phía trước,
 *    lấy mẫu đều theo độ dài cung — xóa sạch gấp khúc bậc thang và giữ vảy bụng hướng mặt trước-trong tự nhiên.
 * 5. Các đốt nằm trên mặt đất dùng `targetY = surfaceY + segmentRadius(i) * 0.85` kèm 3 lượt làm mượt
 *    không gian dọc chuỗi đốt + làm mượt theo tick để ôm dốc 1-2 block không chìm cỏ.
 * 6. Client tái tạo đường cong từ 28 điểm điều khiển đồng bộ bằng Catmull-Rom spline trước khi lấy mẫu đốt.
 */
public final class SerpentPath {
    public static final int N_MAX = 54;
    public static final int CONTROL_POINT_COUNT = 28;
    public static final int REAR_NECK_SEGMENTS = 9;
    /** Chiều dài cơ sở mỗi đốt trong model `.geo.json` (16 px = 1.0 block ở effectiveScale = 1.0). */
    public static final float BASE_SEGMENT_MODEL_LENGTH_PX = 16.0f;

    public enum OverrideMode {
        NONE,
        CONSTRICT_RING,
        OUROBOROS_RING
    }

    /** Trạng thái kết xuất từng đốt đã giải cho Client (flat GeoBone) và VFX. */
    public static final class SegmentPose {
        public double x, y, z;
        public float yawDeg;
        public float pitchRad;
        /** Hệ số độ thon CHỈ dùng cho trục X và Y. */
        public float diameterScale = 1.0f;
        /** Hệ số co giãn động CHỈ dùng cho trục Z (`(dist3D / worldBaseSegLen) * overlapFactor`). */
        public float lengthScale = 1.0f;
    }

    // Lịch sử arc-length trên Server (index 0 = vị trí mới nhất của gốc thân)
    private final List<Vec3> history = new ArrayList<>(256);
    // Mảng vị trí đốt thực tế trên Server (index 0 = đầu, 1..N = đốt thân, N+1 = chóp đuôi)
    private final Vec3[] serverNodes = new Vec3[N_MAX + 2];
    private final float[] groundYCache = new float[N_MAX + 2];
    private final double[] smoothedYWork = new double[N_MAX + 2];
    private final double[] smoothedYTemp = new double[N_MAX + 2];
    private final Vec3[] coilWork = new Vec3[N_MAX + 2];
    private final Vec3[] bezierLutPts = new Vec3[33];
    private final double[] bezierLutArc = new double[33];

    // Trạng thái hình thái trên Server
    private float raiseBlend = 1.0f;
    private float coilBlend = 1.0f;
    private float burrowDepth = 0.0f;
    private float slitherPhase = 0.0f;
    private float slitherStrength = 0.0f;
    private int activeSegments = 34;

    // Trạng thái ghi đè cho Constrict Coil & Ouroboros Ring
    private OverrideMode overrideMode = OverrideMode.NONE;
    private Vec3 ringCenter = Vec3.ZERO;
    private float ringRadius = 8.0f;
    private float ringGapAngleRad = 0.0f;
    private float ringGapWidthRad = 0.0f;
    private float ringBlend = 0.0f;

    // Bộ đệm nội suy 2 gói tin trên Client (không tự dựng lịch sử riêng)
    private final Vec3[] clientPrevCtrl = new Vec3[CONTROL_POINT_COUNT];
    private final Vec3[] clientCurrCtrl = new Vec3[CONTROL_POINT_COUNT];
    private final Vec3[] clientInterpCtrl = new Vec3[CONTROL_POINT_COUNT];
    private final Vec3[] clientSamplePts = new Vec3[N_MAX + 2];
    private boolean clientHasSynced = false;
    private long clientLastSyncTick = 0L;
    private float clientPrevRaise = 1.0f;
    private float clientCurrRaise = 1.0f;
    private float clientPrevBurrow = 0.0f;
    private float clientCurrBurrow = 0.0f;
    private float clientPrevSlitherPhase = 0.0f;
    private float clientCurrSlitherPhase = 0.0f;
    private float clientSlitherStrength = 0.0f;

    public SerpentPath() {
        for (int i = 0; i < serverNodes.length; i++) {
            serverNodes[i] = Vec3.ZERO;
            coilWork[i] = Vec3.ZERO;
            clientSamplePts[i] = Vec3.ZERO;
        }
        for (int i = 0; i < CONTROL_POINT_COUNT; i++) {
            clientPrevCtrl[i] = Vec3.ZERO;
            clientCurrCtrl[i] = Vec3.ZERO;
            clientInterpCtrl[i] = Vec3.ZERO;
        }
    }

    /**
     * Độ thon mặt cắt (CHỈ cho trục X/Y) từ cổ (0.0) đến chóp đuôi (1.0).
     * Cổ trên hơi đầy nhẹ kiểu rắn hổ mang rồi thon mượt dần về 0.22 ở chóp đuôi.
     */
    public static float computeTaperFactor(float frac) {
        float f = Mth.clamp(frac, 0.0f, 1.0f);
        float neckFull = 0.06f * (float) Math.exp(-Math.pow((f - 0.08f) / 0.075f, 2.0));
        float mainTaper = 1.0f - 0.78f * (float) Math.pow(f, 1.42);
        return Mth.clamp(mainTaper + neckFull, 0.22f, 1.06f);
    }

    /** Khởi tạo đường cong ban đầu dạng cuộn tự nhiên ngay khi spawn hoặc sau teleport/burrow. */
    public void resetAt(Vec3 origin, float yawDeg, int segments, float scaledSpacing) {
        JormungandrConfig cfg = JormungandrConfig.get();
        this.activeSegments = Mth.clamp(segments, 8, N_MAX);
        this.history.clear();
        double rad = yawDeg * Mth.DEG_TO_RAD;
        double backX = Math.sin(rad);
        double backZ = -Math.cos(rad);
        double rightX = Math.cos(rad);
        double rightZ = Math.sin(rad);

        double totalLen = (this.activeSegments + 4) * scaledSpacing;
        double step = Math.max(0.35, scaledSpacing * 0.35);
        int count = Math.max(24, (int) Math.ceil(totalLen / step));
        for (int k = 0; k <= count; k++) {
            double s = k * step;
            double wave = Math.sin(s * 0.28) * Math.min(3.5 * cfg.visualScaleMultiplier, s * 0.20);
            double px = origin.x + backX * s + rightX * wave;
            double pz = origin.z + backZ * s + rightZ * wave;
            this.history.add(new Vec3(px, origin.y, pz));
        }
        for (int i = 0; i < serverNodes.length; i++) {
            double dist = i * scaledSpacing;
            Vec3 p = sampleHistoryAtArcLength(dist);
            double radHere = computeRadiusAt(i, cfg);
            serverNodes[i] = new Vec3(p.x, p.y + radHere * 0.85, p.z);
            groundYCache[i] = (float) p.y;
        }
        syncClientBuffersImmediate(origin);
    }

    private void syncClientBuffersImmediate(Vec3 anchor) {
        int maxIdx = Math.max(1, this.activeSegments + 1);
        for (int c = 0; c < CONTROL_POINT_COUNT; c++) {
            double t = c / (double) (CONTROL_POINT_COUNT - 1);
            int idx = Mth.clamp((int) Math.round(t * maxIdx), 0, maxIdx);
            Vec3 p = serverNodes[idx] != null ? serverNodes[idx] : anchor;
            clientPrevCtrl[c] = p;
            clientCurrCtrl[c] = p;
        }
        clientHasSynced = true;
    }

    public void setOverrideNone() {
        this.overrideMode = OverrideMode.NONE;
    }

    public void setConstrictRing(Vec3 center, float radius) {
        this.overrideMode = OverrideMode.CONSTRICT_RING;
        this.ringCenter = center;
        this.ringRadius = Math.max(2.8f, radius);
        this.ringGapWidthRad = 0.0f;
    }

    public void setOuroborosRing(Vec3 center, float radius, float gapCenterAngleRad, float gapWidthBlocks) {
        this.overrideMode = OverrideMode.OUROBOROS_RING;
        this.ringCenter = center;
        this.ringRadius = Math.max(4.8f, radius);
        this.ringGapAngleRad = gapCenterAngleRad;
        this.ringGapWidthRad = Mth.clamp(gapWidthBlocks / this.ringRadius, 0.48f, 1.85f);
    }

    public void setActiveSegments(int segs) {
        this.activeSegments = Mth.clamp(segs, 8, N_MAX);
    }

    public int getActiveSegments() { return this.activeSegments; }
    public float getRaiseBlend() { return this.raiseBlend; }
    public float getBurrowDepth() { return this.burrowDepth; }
    public float getSlitherPhase() { return this.slitherPhase; }
    public float getSlitherStrength() { return this.slitherStrength; }
    public OverrideMode getOverrideMode() { return this.overrideMode; }

    public float getTotalLengthBlocks() {
        return this.activeSegments * JormungandrConfig.get().getScaledSegmentSpacing();
    }

    public Vec3 getServerNode(int index) {
        int clamped = Mth.clamp(index, 0, Math.min(serverNodes.length - 1, activeSegments + 1));
        return serverNodes[clamped] != null ? serverNodes[clamped] : Vec3.ZERO;
    }

    public Vec3 getTailTipPos() {
        return getServerNode(activeSegments + 1);
    }

    /**
     * Cập nhật toàn bộ đường dẫn thân rắn trên Server mỗi tick.
     */
    public void tickServer(Level level, Vec3 headGroundPos, float bodyYawDeg,
                           double horizSpeed, boolean wantRearUp, boolean burrowed) {
        JormungandrConfig cfg = JormungandrConfig.get();
        float vScale = cfg.getEffectiveVisualScale();
        float spacing = cfg.getScaledSegmentSpacing();
        float rearH = cfg.getScaledRearUpHeight();
        float coilRad = cfg.getScaledCoilRadius();
        float bodyDiam = cfg.getScaledBaseBodyDiameter();

        if (history.isEmpty()) {
            resetAt(headGroundPos, bodyYawDeg, this.activeSegments, spacing);
        }

        // 1. Ghi nhận lịch sử vị trí gốc đầu theo arc-length
        Vec3 latest = history.get(0);
        double moved = latest.distanceTo(headGroundPos);
        if (moved > 22.0 * vScale) {
            resetAt(headGroundPos, bodyYawDeg, this.activeSegments, spacing);
        } else if (moved >= 0.22) {
            history.add(0, headGroundPos);
        } else {
            history.set(0, headGroundPos);
        }

        // Cắt tỉa lịch sử theo chiều dài hiện tại + dự phòng
        double maxKeepLen = (this.activeSegments + 8) * spacing + 8.0 * vScale;
        double acc = 0.0;
        for (int i = 1; i < history.size(); i++) {
            acc += history.get(i - 1).distanceTo(history.get(i));
            if (acc > maxKeepLen && i + 2 < history.size()) {
                history.subList(i + 2, history.size()).clear();
                break;
            }
        }

        // 2. Chuyển mượt tham số tư thế
        boolean movingFast = horizSpeed > 0.075;
        float targetRaise = (burrowed || overrideMode != OverrideMode.NONE)
                ? 0.0f
                : (wantRearUp || !movingFast ? 1.0f : 0.14f);
        this.raiseBlend = Mth.lerp(0.11f, this.raiseBlend, targetRaise);

        float targetCoil = (!burrowed && overrideMode == OverrideMode.NONE && !movingFast) ? 1.0f : 0.0f;
        this.coilBlend = Mth.lerp(0.06f, this.coilBlend, targetCoil);

        float targetBurrow = burrowed ? 1.0f : 0.0f;
        this.burrowDepth = Mth.lerp(0.18f, this.burrowDepth, targetBurrow);

        float targetRing = (overrideMode != OverrideMode.NONE) ? 1.0f : 0.0f;
        this.ringBlend = Mth.lerp(0.16f, this.ringBlend, targetRing);

        float targetSlither = (movingFast && !burrowed && overrideMode == OverrideMode.NONE) ? 1.0f : 0.0f;
        this.slitherStrength = Mth.lerp(0.12f, this.slitherStrength, targetSlither);
        this.slitherPhase = (this.slitherPhase + (float) (0.14 + horizSpeed * 0.95)) % (Mth.TWO_PI * 16.0f);

        // 3. Hệ trục thân rắn tại gốc
        double bodyRad = bodyYawDeg * Mth.DEG_TO_RAD;
        double fwdX = -Math.sin(bodyRad);
        double fwdZ = Math.cos(bodyRad);
        double rightX = Math.cos(bodyRad);
        double rightZ = Math.sin(bodyRad);

        int totalNodes = this.activeSegments + 1;
        int neckCount = Math.min(REAR_NECK_SEGMENTS, Math.max(4, totalNodes - 2));

        // 3a. Nếu đang cuộn nghỉ -> dựng sẵn mảng xoắn ốc ĐỀU ĐỘ DÀI CUNG (0 kéo giãn ở đuôi!)
        if (this.coilBlend > 0.01f) {
            buildConstantArcLengthIdleCoil(headGroundPos, fwdX, fwdZ, rightX, rightZ,
                    totalNodes, neckCount, spacing, coilRad, rearH, bodyDiam);
        }

        // 3b. Giải tọa độ XZ phẳng + bám địa hình với đáy bụng đặt trên mặt cỏ (`surfaceY + radius * 0.85`)
        for (int i = 0; i <= totalNodes; i++) {
            double arcDist = i * spacing;
            Vec3 basePt = sampleHistoryAtArcLength(arcDist);

            if (this.coilBlend > 0.01f) {
                basePt = basePt.lerp(this.coilWork[i], this.coilBlend);
            }
            if (this.ringBlend > 0.01f && this.overrideMode != OverrideMode.NONE) {
                Vec3 ringPt = computeRingPoint(i, totalNodes);
                basePt = basePt.lerp(ringPt, this.ringBlend);
            }

            double surfaceY = sampleSafeTerrainY(level, basePt.x, basePt.z, groundYCache[i], headGroundPos.y);
            groundYCache[i] = (float) Mth.lerp(0.30, groundYCache[i], surfaceY);

            double bodyRadiusHere = computeRadiusAt(i, cfg);
            double targetY = groundYCache[i] + bodyRadiusHere * 0.85;
            smoothedYWork[i] = targetY;
            serverNodes[i] = new Vec3(basePt.x, targetY, basePt.z);
        }

        // 3c. Làm mượt không gian Y dọc chuỗi đốt (3 lượt trung bình có trọng số [0.25, 0.50, 0.25])
        //     giúp thân uốn mềm qua bậc dốc 1-2 block không gãy khúc và không chìm xuống đất
        for (int pass = 0; pass < 3; pass++) {
            smoothedYTemp[0] = smoothedYWork[0];
            smoothedYTemp[totalNodes] = smoothedYWork[totalNodes];
            for (int i = 1; i < totalNodes; i++) {
                double avg = 0.25 * smoothedYWork[i - 1] + 0.50 * smoothedYWork[i] + 0.25 * smoothedYWork[i + 1];
                // Chặn sàn chống chìm sâu khi đi qua gờ lồi
                double floorMin = groundYCache[i] + computeRadiusAt(i, cfg) * 0.74;
                smoothedYTemp[i] = Math.max(avg, floorMin);
            }
            System.arraycopy(smoothedYTemp, 0, smoothedYWork, 0, totalNodes + 1);
        }
        for (int i = 0; i <= totalNodes; i++) {
            Vec3 n = serverNodes[i];
            serverNodes[i] = new Vec3(n.x, smoothedYWork[i], n.z);
        }

        // 3d. Đường cong chữ S Cubic Bézier mượt cho đoạn cổ ngẩng (i = 0..neckCount)
        //     Luôn đơn điệu tiến về trước (d(fwd)/dt > 0) để yaw KHÔNG BAO GIỜ lật 180° và vảy bụng luôn quay ra trước!
        if (this.raiseBlend > 0.01f) {
            float smoothRaise = this.raiseBlend * this.raiseBlend * (3.0f - 2.0f * this.raiseBlend);
            Vec3 b0 = serverNodes[neckCount]; // Chân cổ nằm đúng trên mặt đất -> nối C1 liền mạch với thân cuộn
            double headRadius = computeRadiusAt(0, cfg);
            double headBaseY = Math.max(groundYCache[0], b0.y - computeRadiusAt(neckCount, cfg) * 0.85);
            Vec3 b3 = new Vec3(
                    headGroundPos.x + fwdX * (0.24 * rearH),
                    headBaseY + rearH + headRadius * 0.85,
                    headGroundPos.z + fwdZ * (0.24 * rearH)
            );

            // Hướng trục cổ ngang từ chân cổ b0 về đầu b3 (đảm bảo luôn đi về phía trước, không đảo chiều)
            double spanX = b3.x - b0.x;
            double spanZ = b3.z - b0.z;
            double spanHoriz = Math.hypot(spanX, spanZ);
            double nFwdX, nFwdZ;
            if (spanHoriz > 0.35) {
                nFwdX = spanX / spanHoriz;
                nFwdZ = spanZ / spanHoriz;
            } else {
                nFwdX = fwdX;
                nFwdZ = fwdZ;
            }
            double effSpan = Math.max(spanHoriz, 0.72 * rearH);
            double riseY = Math.max(1.5, b3.y - b0.y);

            // 4 điểm kiểm soát Cubic Bézier (đều tiến dương dọc nFwd -> đạo hàm ngang luôn dương -> 0 xoắn bụng!)
            Vec3 b1 = new Vec3(
                    b0.x + nFwdX * (effSpan * 0.48),
                    b0.y + riseY * 0.06,
                    b0.z + nFwdZ * (effSpan * 0.48)
            );
            Vec3 b2 = new Vec3(
                    b3.x - nFwdX * (effSpan * 0.42),
                    b0.y + riseY * 0.96,
                    b3.z - nFwdZ * (effSpan * 0.42)
            );

            // Dựng bảng độ dài cung (32 bước) để phân bố các đốt cổ ĐỀU 100% theo khoảng cách 3D
            int lutSteps = 32;
            bezierLutPts[0] = b0;
            bezierLutArc[0] = 0.0;
            for (int k = 1; k <= lutSteps; k++) {
                double t = k / (double) lutSteps;
                Vec3 pt = evalCubicBezier(b0, b1, b2, b3, t);
                bezierLutPts[k] = pt;
                bezierLutArc[k] = bezierLutArc[k - 1] + pt.distanceTo(bezierLutPts[k - 1]);
            }
            double totalNeckArc = Math.max(1e-4, bezierLutArc[lutSteps]);

            for (int i = 0; i < neckCount; i++) {
                // i = 0 -> tArc = 1.0 (đầu b3), i = neckCount -> tArc = 0.0 (chân cổ b0)
                double targetArc = ((neckCount - i) / (double) neckCount) * totalNeckArc;
                Vec3 curvePt = sampleBezierLut(targetArc, lutSteps);
                serverNodes[i] = serverNodes[i].lerp(curvePt, smoothRaise);
            }
        }

        // 3e. Hiệu ứng lặn xuống đất (không phá khối)
        if (this.burrowDepth > 0.01f) {
            double sink = this.burrowDepth * 5.2 * vScale;
            for (int i = 0; i <= totalNodes; i++) {
                Vec3 p = serverNodes[i];
                serverNodes[i] = new Vec3(p.x, p.y - sink, p.z);
            }
        }
    }

    /**
     * Dựng vòng cuộn đứng yên theo xoắn ốc Archimedean tích phân ĐÚNG ĐỘ DÀI CUNG (RK2 midpoint 4 bước/đốt).
     * Điểm chân cổ `neckCount` nối tiếp tuyến thẳng về phía sau `-fwd`, rồi xoắn đều ra ngoài với khoảng hở
     * lớn hơn đường kính thân -> không cắt chồng vòng và khoảng cách 2 đốt kế tiếp LUÔN bằng đúng `spacing`!
     */
    private void buildConstantArcLengthIdleCoil(Vec3 headPos, double fwdX, double fwdZ, double rightX, double rightZ,
                                                int totalNodes, int neckCount,
                                                float spacing, float coilRad, float rearH, float bodyDiam) {
        // Chân cổ đặt cách gốc đầu về phía sau ~0.55 * rearH để đường cong S vươn thẳng lên trên gốc đầu
        double footBack = Math.max(spacing * 2.2, rearH * 0.55);
        double footX = headPos.x - fwdX * footBack;
        double footZ = headPos.z - fwdZ * footBack;

        // Các đốt 0..neckCount ở trạng thái phẳng (trước khi Bézier nhấc lên) chạy thẳng từ đầu về chân cổ
        for (int i = 0; i <= neckCount; i++) {
            double frac = i / (double) Math.max(1, neckCount);
            double dist = frac * footBack;
            coilWork[i] = new Vec3(headPos.x - fwdX * dist, headPos.y, headPos.z - fwdZ * dist);
        }

        // Tâm xoắn ốc đặt lệch sang trái sao cho tại theta = +PI/2 (u = 0):
        //   P(0) = center + right * r0 = P_foot, và tiếp tuyến dP/du đi thẳng về -fwd (mượt 100% với cổ!)
        double r0 = Math.max(bodyDiam * 1.35, coilRad * 0.72);
        double b = (bodyDiam * 1.08) / Mth.TWO_PI; // Mỗi vòng xoắn nới rộng hơn đường kính thân -> không tự đè nhau
        double centerX = footX - rightX * r0;
        double centerZ = footZ - rightZ * r0;

        double u = 0.0;
        final int subSteps = 4;
        final double ds = spacing / (double) subSteps;
        for (int i = neckCount + 1; i <= totalNodes; i++) {
            for (int k = 0; k < subSteps; k++) {
                double r1 = r0 + b * u;
                double du1 = ds / Math.max(0.5, Math.hypot(r1, b));
                double rMid = r0 + b * (u + 0.5 * du1);
                u += ds / Math.max(0.5, Math.hypot(rMid, b));
            }
            double theta = (Math.PI * 0.5) + u;
            double r = r0 + b * u;
            double localRight = Math.sin(theta) * r;
            double localFwd = Math.cos(theta) * r;
            coilWork[i] = new Vec3(
                    centerX + rightX * localRight + fwdX * localFwd,
                    headPos.y,
                    centerZ + rightZ * localRight + fwdZ * localFwd
            );
        }
    }

    private static Vec3 evalCubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double u = 1.0 - t;
        double uu = u * u;
        double tt = t * t;
        double w0 = uu * u;
        double w1 = 3.0 * uu * t;
        double w2 = 3.0 * u * tt;
        double w3 = tt * t;
        return new Vec3(
                w0 * p0.x + w1 * p1.x + w2 * p2.x + w3 * p3.x,
                w0 * p0.y + w1 * p1.y + w2 * p2.y + w3 * p3.y,
                w0 * p0.z + w1 * p1.z + w2 * p2.z + w3 * p3.z
        );
    }

    private Vec3 sampleBezierLut(double targetArc, int steps) {
        if (targetArc <= 0.0) return bezierLutPts[0];
        if (targetArc >= bezierLutArc[steps]) return bezierLutPts[steps];
        for (int k = 1; k <= steps; k++) {
            if (bezierLutArc[k] >= targetArc) {
                double seg = Math.max(1e-6, bezierLutArc[k] - bezierLutArc[k - 1]);
                double f = (targetArc - bezierLutArc[k - 1]) / seg;
                return bezierLutPts[k - 1].lerp(bezierLutPts[k], f);
            }
        }
        return bezierLutPts[steps];
    }

    private Vec3 computeRingPoint(int idx, int totalNodes) {
        double frac = idx / (double) Math.max(1, totalNodes);
        double spanRad;
        double startRad;
        if (this.overrideMode == OverrideMode.OUROBOROS_RING) {
            spanRad = Math.max(Math.PI, Mth.TWO_PI - this.ringGapWidthRad);
            startRad = this.ringGapAngleRad + this.ringGapWidthRad * 0.5;
        } else {
            spanRad = Mth.TWO_PI * 0.90;
            startRad = 0.0;
        }
        double ang = startRad + frac * spanRad;
        return new Vec3(
                this.ringCenter.x + Math.cos(ang) * this.ringRadius,
                this.ringCenter.y,
                this.ringCenter.z + Math.sin(ang) * this.ringRadius
        );
    }

    private Vec3 sampleHistoryAtArcLength(double targetDist) {
        if (history.isEmpty()) return Vec3.ZERO;
        if (targetDist <= 0.0 || history.size() == 1) return history.get(0);

        double acc = 0.0;
        for (int i = 1; i < history.size(); i++) {
            Vec3 a = history.get(i - 1);
            Vec3 b = history.get(i);
            double seg = a.distanceTo(b);
            if (acc + seg >= targetDist && seg > 1e-4) {
                double t = (targetDist - acc) / seg;
                return a.lerp(b, t);
            }
            acc += seg;
        }
        return history.get(history.size() - 1);
    }

    private static double sampleSafeTerrainY(Level level, double x, double z, double prevY, double fallbackY) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            return prevY;
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        BlockPos surfPos = new BlockPos(bx, surfaceY, bz);
        if (level.getFluidState(surfPos).is(FluidTags.WATER)) {
            while (surfaceY < level.getMaxBuildHeight() - 2
                    && level.getFluidState(new BlockPos(bx, surfaceY + 1, bz)).is(FluidTags.WATER)) {
                surfaceY++;
            }
            surfaceY++;
        }
        double baseRef = (Math.abs(prevY - fallbackY) > 24.0) ? fallbackY : prevY;
        return Mth.clamp(surfaceY, baseRef - 3.0, baseRef + 3.0);
    }

    /** Bán kính mặt cắt thực tế (world blocks, đã nhân `visualScaleMultiplier`) tại đốt `nodeIdx`. */
    public float computeRadiusAt(int nodeIdx, JormungandrConfig cfg) {
        float frac = Mth.clamp(nodeIdx / (float) Math.max(1, this.activeSegments), 0.0f, 1.0f);
        return (cfg.getScaledBaseBodyDiameter() * 0.5f) * computeTaperFactor(frac);
    }

    public AABB computeTotalBoundingBox(Vec3 fallbackPos) {
        float pad = 4.2f * JormungandrConfig.get().getEffectiveVisualScale();
        double minX = fallbackPos.x - pad, minY = fallbackPos.y - 2.5, minZ = fallbackPos.z - pad;
        double maxX = fallbackPos.x + pad, maxY = fallbackPos.y + pad * 2.2, maxZ = fallbackPos.z + pad;
        if (clientHasSynced) {
            for (Vec3 p : clientCurrCtrl) {
                if (p == null) continue;
                minX = Math.min(minX, p.x - pad);
                minY = Math.min(minY, p.y - pad * 0.8);
                minZ = Math.min(minZ, p.z - pad);
                maxX = Math.max(maxX, p.x + pad);
                maxY = Math.max(maxY, p.y + pad * 1.2);
                maxZ = Math.max(maxZ, p.z + pad);
            }
        } else {
            int limit = Math.min(serverNodes.length - 1, activeSegments + 1);
            for (int i = 0; i <= limit; i++) {
                Vec3 p = serverNodes[i];
                if (p == null) continue;
                minX = Math.min(minX, p.x - pad);
                minY = Math.min(minY, p.y - pad * 0.8);
                minZ = Math.min(minZ, p.z - pad);
                maxX = Math.max(maxX, p.x + pad);
                maxY = Math.max(maxY, p.y + pad * 1.2);
                maxZ = Math.max(maxZ, p.z + pad);
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // =========================================================================
    // ĐÓNG GÓI PAYLOAD S2C (SERVER) & NỘI SUY CATMULL-ROM SPLINE (CLIENT)
    // =========================================================================
    public JormungandrPathSyncPayload buildSyncPayload(int entityId, Vec3 anchor) {
        short[] packed = new short[CONTROL_POINT_COUNT * 3];
        int maxIdx = Math.max(1, this.activeSegments + 1);
        for (int c = 0; c < CONTROL_POINT_COUNT; c++) {
            double f = (c / (double) (CONTROL_POINT_COUNT - 1)) * maxIdx;
            int i0 = Mth.clamp((int) Math.floor(f), 0, maxIdx);
            int i1 = Mth.clamp(i0 + 1, 0, maxIdx);
            double alpha = f - i0;
            Vec3 p = serverNodes[i0].lerp(serverNodes[i1], alpha);
            int dx = Mth.clamp(Math.round((float) ((p.x - anchor.x) * JormungandrPathSyncPayload.QUANT_SCALE)), Short.MIN_VALUE, Short.MAX_VALUE);
            int dy = Mth.clamp(Math.round((float) ((p.y - anchor.y) * JormungandrPathSyncPayload.QUANT_SCALE)), Short.MIN_VALUE, Short.MAX_VALUE);
            int dz = Mth.clamp(Math.round((float) ((p.z - anchor.z) * JormungandrPathSyncPayload.QUANT_SCALE)), Short.MIN_VALUE, Short.MAX_VALUE);
            packed[c * 3] = (short) dx;
            packed[c * 3 + 1] = (short) dy;
            packed[c * 3 + 2] = (short) dz;
        }
        return new JormungandrPathSyncPayload(
                entityId, this.activeSegments, this.raiseBlend, this.burrowDepth,
                this.slitherPhase, this.slitherStrength,
                anchor.x, anchor.y, anchor.z, packed
        );
    }

    public void applyClientSyncPayload(JormungandrPathSyncPayload payload, long clientGameTick) {
        this.activeSegments = Mth.clamp(payload.activeSegments(), 8, N_MAX);
        this.clientPrevRaise = this.clientHasSynced ? this.clientCurrRaise : payload.raiseBlend();
        this.clientCurrRaise = payload.raiseBlend();
        this.clientPrevBurrow = this.clientHasSynced ? this.clientCurrBurrow : payload.burrowDepth();
        this.clientCurrBurrow = payload.burrowDepth();
        this.clientPrevSlitherPhase = this.clientHasSynced ? this.clientCurrSlitherPhase : payload.slitherPhase();
        this.clientCurrSlitherPhase = payload.slitherPhase();
        this.clientSlitherStrength = payload.slitherStrength();

        int count = Math.min(CONTROL_POINT_COUNT, payload.packedOffsets().length / 3);
        double inv = 1.0 / JormungandrPathSyncPayload.QUANT_SCALE;
        for (int c = 0; c < CONTROL_POINT_COUNT; c++) {
            int src = Math.min(c, Math.max(0, count - 1));
            double px = payload.anchorX() + payload.packedOffsets()[src * 3] * inv;
            double py = payload.anchorY() + payload.packedOffsets()[src * 3 + 1] * inv;
            double pz = payload.anchorZ() + payload.packedOffsets()[src * 3 + 2] * inv;
            Vec3 worldPt = new Vec3(px, py, pz);
            this.clientPrevCtrl[c] = this.clientHasSynced ? this.clientCurrCtrl[c] : worldPt;
            this.clientCurrCtrl[c] = worldPt;
        }
        this.clientHasSynced = true;
        this.clientLastSyncTick = clientGameTick;
    }

    public boolean hasClientSynced() {
        return this.clientHasSynced;
    }

    public float getClientInterpolatedBurrow(long gameTick, float partialTick) {
        float t = computeClientSyncAlpha(gameTick, partialTick);
        return Mth.lerp(t, clientPrevBurrow, clientCurrBurrow);
    }

    public float getClientInterpolatedRaise(long gameTick, float partialTick) {
        float t = computeClientSyncAlpha(gameTick, partialTick);
        return Mth.lerp(t, clientPrevRaise, clientCurrRaise);
    }

    public float getClientSlitherStrength() {
        return this.clientSlitherStrength;
    }

    private float computeClientSyncAlpha(long gameTick, float partialTick) {
        float dt = (gameTick - this.clientLastSyncTick) + partialTick;
        return Mth.clamp(dt / 2.0f, 0.0f, 1.0f);
    }

    /**
     * Đánh giá đường cong Catmull-Rom spline qua 4 điểm kiểm soát (p0, p1, p2, p3) tại t ∈ [0, 1].
     */
    private static Vec3 evalCatmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double x = 0.5 * ((2.0 * p1.x)
                + (-p0.x + p2.x) * t
                + (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2
                + (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3);
        double y = 0.5 * ((2.0 * p1.y)
                + (-p0.y + p2.y) * t
                + (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2
                + (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3);
        double z = 0.5 * ((2.0 * p1.z)
                + (-p0.z + p2.z) * t
                + (2.0 * p0.z - 5.0 * p1.z + 4.0 * p2.z - p3.z) * t2
                + (-p0.z + 3.0 * p1.z - 3.0 * p2.z + p3.z) * t3);
        return new Vec3(x, y, z);
    }

    /**
     * Giải vị trí trung điểm + góc tiếp tuyến (unwrapped) + độ thon X/Y (`diameterScale`)
     * + độ co giãn động trục Z (`lengthScale = (dist3D / worldBaseSegLen) * overlapFactor`)
     * cho `head` (0), `segment_1..activeSegments` và `tail_tip` (activeSegments+1).
     */
    public void sampleClientPoses(long gameTick, float partialTick, float phaseThicknessMult,
                                  float visualScale, SegmentPose[] outPoses) {
        JormungandrConfig cfg = JormungandrConfig.get();
        float alpha = computeClientSyncAlpha(gameTick, partialTick);
        float interpRaise = Mth.lerp(alpha, clientPrevRaise, clientCurrRaise);
        float phase = Mth.lerp(alpha, clientPrevSlitherPhase, clientCurrSlitherPhase);
        float slither = this.clientSlitherStrength;
        int segCount = Math.min(outPoses.length - 2, this.activeSegments);
        int totalNodes = segCount + 1;

        // 1. Nội suy 28 điểm điều khiển giữa 2 gói tin
        for (int c = 0; c < CONTROL_POINT_COUNT; c++) {
            clientInterpCtrl[c] = clientPrevCtrl[c].lerp(clientCurrCtrl[c], alpha);
        }

        // 2. Lấy mẫu đường cong mượt bằng Catmull-Rom spline tại các điểm nút 0..totalNodes
        int lastC = CONTROL_POINT_COUNT - 1;
        for (int i = 0; i <= totalNodes; i++) {
            double u = (i / (double) Math.max(1, totalNodes)) * lastC;
            int idx = Mth.clamp((int) Math.floor(u), 0, lastC - 1);
            double t = Mth.clamp(u - idx, 0.0, 1.0);
            Vec3 p1 = clientInterpCtrl[idx];
            Vec3 p2 = clientInterpCtrl[idx + 1];
            Vec3 p0 = (idx > 0) ? clientInterpCtrl[idx - 1] : p1.add(p1.subtract(p2));
            Vec3 p3 = (idx + 2 <= lastC) ? clientInterpCtrl[idx + 2] : p2.add(p2.subtract(p1));
            clientSamplePts[i] = evalCatmullRom(p0, p1, p2, p3, t);
        }

        // 3. Áp sóng lượn ngang trườn TRƯỚC khi tính đoạn nối -> các đốt luôn bám khít kể cả khi uốn sóng mạnh!
        if (slither > 0.02f) {
            double ampScale = 0.48 * visualScale;
            for (int i = 2; i <= totalNodes; i++) {
                Vec3 ahead = clientSamplePts[i - 1];
                Vec3 behind = (i < totalNodes) ? clientSamplePts[i + 1] : clientSamplePts[i];
                Vec3 dir = ahead.subtract(behind);
                double h = Math.hypot(dir.x, dir.z);
                if (h > 1e-4) {
                    double nx = -dir.z / h;
                    double nz = dir.x / h;
                    double waveEnv = Math.min(1.0, (i - 1) / 5.0) * (1.0 - 0.30 * (i / (double) Math.max(1, totalNodes)));
                    double lat = Math.sin(i * 0.52 - phase) * ampScale * slither * waveEnv;
                    Vec3 cur = clientSamplePts[i];
                    clientSamplePts[i] = new Vec3(cur.x + nx * lat, cur.y, cur.z + nz * lat);
                }
            }
        }

        // Chiều dài thực 1 đốt ở scaleZ = 1.0 trong thế giới (16 px / 16 * visualScale = visualScale)
        double worldBaseSegLen = Math.max(0.25, (BASE_SEGMENT_MODEL_LENGTH_PX / 16.0) * visualScale);
        float overlap = cfg.segmentOverlapFactor;

        // 4. Đặt bone `head` (index 0) tại P[0], hướng theo P[0] - P[1] (giữ đầu thăng bằng khi ngẩng cổ)
        Vec3 headVec = clientSamplePts[0].subtract(clientSamplePts[1]);
        if (headVec.lengthSqr() < 1e-6) {
            headVec = new Vec3(0, 0, 1);
        }
        double headHoriz = Math.hypot(headVec.x, headVec.z);
        float headYaw = Mth.wrapDegrees((float) (Mth.atan2(headVec.z, headVec.x) * Mth.RAD_TO_DEG) - 90.0f);
        float rawHeadPitch = (float) Mth.atan2(headVec.y, Math.max(1e-4, headHoriz));
        // Khi ngẩng cổ, đầu hướng ngang về phía mục tiêu thay vì ngửa thẳng lên trời
        float headPitch = Mth.clamp(rawHeadPitch * (1.0f - 0.78f * interpRaise), -0.65f, 0.65f);

        SegmentPose headPose = outPoses[0];
        headPose.x = clientSamplePts[0].x;
        headPose.y = clientSamplePts[0].y;
        headPose.z = clientSamplePts[0].z;
        headPose.yawDeg = headYaw;
        headPose.pitchRad = headPitch;
        headPose.diameterScale = phaseThicknessMult;
        headPose.lengthScale = phaseThicknessMult;

        // 5. Đặt từng đốt thân `segment_1 .. segment_N` tại TRUNG ĐIỂM `(P[i-1] + P[i]) / 2`
        //    với hướng từ `P[i-1] - P[i]`, độ thon X/Y riêng và `scaleZ = (dist3D / worldBaseSegLen) * overlap`!
        float prevYaw = headYaw;
        for (int i = 1; i <= segCount; i++) {
            Vec3 front = clientSamplePts[i - 1];
            Vec3 back = clientSamplePts[i];
            Vec3 segVec = front.subtract(back);
            double dist3D = segVec.length();
            if (dist3D < 1e-5) {
                segVec = new Vec3(0, 0, 1);
                dist3D = worldBaseSegLen;
            }

            double horiz = Math.hypot(segVec.x, segVec.z);
            float rawYaw = (horiz > 1e-4)
                    ? Mth.wrapDegrees((float) (Mth.atan2(segVec.z, segVec.x) * Mth.RAD_TO_DEG) - 90.0f)
                    : prevYaw;
            float unwrappedYaw = prevYaw + Mth.wrapDegrees(rawYaw - prevYaw);
            prevYaw = unwrappedYaw;

            float pitchRad = Mth.clamp((float) Mth.atan2(segVec.y, Math.max(1e-4, horiz)), -1.28f, 1.28f);

            float frac = Mth.clamp((i - 0.5f) / (float) Math.max(1, segCount), 0.0f, 1.0f);
            float diamScale = computeTaperFactor(frac) * phaseThicknessMult;
            float dynScaleZ = Mth.clamp((float) ((dist3D / worldBaseSegLen) * overlap), 0.55f, 3.40f);

            SegmentPose pose = outPoses[i];
            pose.x = (front.x + back.x) * 0.5;
            pose.y = (front.y + back.y) * 0.5;
            pose.z = (front.z + back.z) * 0.5;
            pose.yawDeg = Mth.wrapDegrees(unwrappedYaw);
            pose.pitchRad = pitchRad;
            pose.diameterScale = Math.max(0.22f, diamScale);
            pose.lengthScale = dynScaleZ;
        }

        // 6. Đặt bone `tail_tip` (index segCount + 1) lồng khít vào đuôi đốt cuối `segment_N`
        Vec3 tailFront = clientSamplePts[segCount];
        Vec3 tailBack = clientSamplePts[totalNodes];
        Vec3 tailVec = tailFront.subtract(tailBack);
        double tailDist = tailVec.length();
        if (tailDist < 1e-5) {
            tailVec = clientSamplePts[Math.max(0, segCount - 1)].subtract(tailFront);
            tailDist = Math.max(worldBaseSegLen * 0.85, tailVec.length());
        }
        double tailHoriz = Math.hypot(tailVec.x, tailVec.z);
        float rawTailYaw = (tailHoriz > 1e-4)
                ? Mth.wrapDegrees((float) (Mth.atan2(tailVec.z, tailVec.x) * Mth.RAD_TO_DEG) - 90.0f)
                : prevYaw;
        float tailYaw = prevYaw + Mth.wrapDegrees(rawTailYaw - prevYaw);
        float tailPitch = Mth.clamp((float) Mth.atan2(tailVec.y, Math.max(1e-4, tailHoriz)), -1.15f, 1.15f);

        // Đẩy gốc `tail_tip` lồng nhẹ vào sau đốt cuối 15% để chóp sừng nối liền mạch 100%
        Vec3 tailDir = tailVec.lengthSqr() > 1e-6 ? tailVec.normalize() : new Vec3(0, 0, 1);
        Vec3 tailAnchor = tailFront.add(tailDir.scale(tailDist * 0.16));

        SegmentPose tailPose = outPoses[segCount + 1];
        tailPose.x = tailAnchor.x;
        tailPose.y = tailAnchor.y;
        tailPose.z = tailAnchor.z;
        tailPose.yawDeg = Mth.wrapDegrees(tailYaw);
        tailPose.pitchRad = tailPitch;
        tailPose.diameterScale = Math.max(0.22f, computeTaperFactor(1.0f) * phaseThicknessMult * 1.06f);
        tailPose.lengthScale = Mth.clamp((float) ((tailDist / worldBaseSegLen) * overlap), 0.75f, 2.25f);
    }
}
