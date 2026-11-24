package com.abyssalrpg.entity.mythic;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.api.BossDefenseHolder;
import com.abyssalrpg.api.BossHealthHolder;
import com.abyssalrpg.entity.AbstractAbyssalBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * Lớp cha dùng chung cho toàn bộ Mythic Boss sử dụng GeckoLib 4.x (Leviathan, Spirit Hydra, ...).
 *
 * Tập trung hóa các hạ tầng cốt lõi để tái sử dụng không copy-paste:
 * 1. Hệ thống chuẩn hóa góc & xoay theo cung ngắn nhất (`wrapAngle`, `approachAngle`, `alignPrevAngle`,
 *    `normalizeAnglesAndSyncPrev`, `smoothRotateToward`, `SafeGeoLookControl`, `BossLockGoal`)
 *    chống tuyệt đối lỗi đầu quay nhiều vòng khi chuyển phase hoặc bị choáng.
 * 2. Hệ thống đồng bộ DataTracker & NBT cho Phase 2 cận tử (`hasEnteredPhase2`, `enragedVisual`,
 *    `phaseTransitionTicks`, `lockedYaw`).
 * 3. Helper áp `EntityAttributeModifier` (`ADD_MULTIPLIED_BASE`, ID cố định) an toàn idempotent khi reload NBT,
 *    ước lượng sát thương sau giáp vanilla (`CombatRules.getDamageAfterAbsorb`), đồng bộ BossBar bán kính lớn
 *    và đảm bảo thời gian telegraph tối thiểu >= 0.4s.
 */
public abstract class AbstractGeoMythicBoss extends AbstractAbyssalBoss implements GeoEntity, BossHealthHolder, BossDefenseHolder {
    /** Giới hạn trần an toàn cho `Attributes.MAX_HEALTH` vanilla (RangedAttribute max = 1024.0). */
    public static final double VANILLA_SAFE_MAX_HEALTH_ATTR = 1024.0;

    protected static final EntityDataAccessor<Float> DATA_BOSS_MAX_HEALTH =
            SynchedEntityData.defineId(AbstractGeoMythicBoss.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> DATA_BOSS_DEFENSE =
            SynchedEntityData.defineId(AbstractGeoMythicBoss.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Integer> DATA_PHASE_TRANS_TICKS =
            SynchedEntityData.defineId(AbstractGeoMythicBoss.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Boolean> DATA_HAS_ENTERED_PHASE2 =
            SynchedEntityData.defineId(AbstractGeoMythicBoss.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Boolean> DATA_ENRAGED_VISUAL =
            SynchedEntityData.defineId(AbstractGeoMythicBoss.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Float> DATA_LOCKED_YAW =
            SynchedEntityData.defineId(AbstractGeoMythicBoss.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    protected boolean hasEnteredPhase2 = false;
    protected float transitionStartHp = 1.0f;
    protected int transitionTotalTicks = 90;

    @SuppressWarnings("this-escape")
    protected AbstractGeoMythicBoss(EntityType<? extends Monster> type, Level level, BossEvent.BossBarColor barColor) {
        super(type, level, barColor);
        this.lookControl = new SafeGeoLookControl(this);
        this.setPersistenceRequired();
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        // Mặc định 0.0f để trong constructor vanilla `LivingEntity.<init>`, Mixin rơi an toàn về attribute vanilla
        builder.define(DATA_BOSS_MAX_HEALTH, 0.0f);
        // Mặc định 0.0f để các boss cũ (Leviathan/Hydra/Cerberus) tiếp tục dùng giáp vanilla bình thường
        builder.define(DATA_BOSS_DEFENSE, 0.0f);
        builder.define(DATA_PHASE_TRANS_TICKS, 0);
        builder.define(DATA_HAS_ENTERED_PHASE2, false);
        builder.define(DATA_ENRAGED_VISUAL, false);
        builder.define(DATA_LOCKED_YAW, 0.0f);
    }

    // --- Hệ thống máu tối đa riêng vượt trần 1024 (BossHealthHolder + LivingEntityMixin) ---
    @Override
    public float getBossMaxHealth() {
        if (this.entityData == null) {
            return 0.0f;
        }
        return this.entityData.get(DATA_BOSS_MAX_HEALTH);
    }

    @Override
    public void setBossMaxHealth(float value) {
        float sanitized = (Float.isFinite(value) && value > 0.0f) ? value : 0.0f;
        if (this.entityData != null) {
            this.entityData.set(DATA_BOSS_MAX_HEALTH, sanitized);
        }
        if (sanitized > 0.0f && this.getHealth() > sanitized) {
            this.setHealth(sanitized);
        }
    }

    // --- Hệ thống phòng thủ riêng vượt trần vanilla 30 (BossDefenseHolder + override getDamageAfterArmorAbsorb) ---
    @Override
    public float getBossDefense() {
        if (this.entityData == null) {
            return 0.0f;
        }
        return this.entityData.get(DATA_BOSS_DEFENSE);
    }

    @Override
    public void setBossDefense(float value) {
        float sanitized = (Float.isFinite(value) && value > 0.0f) ? value : 0.0f;
        if (this.entityData != null) {
            this.entityData.set(DATA_BOSS_DEFENSE, sanitized);
        }
    }

    /**
     * Tính điểm phòng thủ hiệu dụng theo công thức vanilla `CombatRules`, có hỗ trợ `armorPierceFraction` (`0.05/0.12/0.20`):
     * - `effectiveDef = def * (1 - pierce)`
     * - `effectiveTough = tough * (1 - pierce)`
     * - `effectiveMaxRed = maxDamageReduction * (1 - pierce)` (đảm bảo Xuyên Thủ tác động chính xác kể cả Boss Defense 40+ chạm trần 70%!).
     */
    public float computeEffectiveDefensePoints(float rawDamage) {
        return computeEffectiveDefensePoints(rawDamage, 0.0f);
    }

    public float computeEffectiveDefensePoints(float rawDamage, float armorPierceFraction) {
        float def = Math.max(0.0f, getBossDefense());
        if (def <= 0.0f) {
            return 0.0f;
        }
        float pierce = Mth.clamp(armorPierceFraction, 0.0f, 0.90f);
        float effDef = def * (1.0f - pierce);
        float effTough = Math.max(0.0f, getBossToughness()) * (1.0f - pierce);
        float maxRed = Mth.clamp(getMaxDamageReduction(), 0.0f, 0.90f) * (1.0f - pierce);
        float maxPoints = maxRed * 25.0f;
        float denom = 2.0f + effTough / 4.0f;
        float rawPoints = Math.max(effDef / 5.0f, effDef - Math.max(0.0f, rawDamage) / denom);
        return Mth.clamp(rawPoints, 0.0f, maxPoints);
    }

    /** Trả về hệ số giảm sát thương thực tế trong khoảng [0, 0.90] ứng với đòn có sát thương `sampleDamage`. */
    public float computeEffectiveDamageReduction(float sampleDamage) {
        return computeEffectiveDefensePoints(sampleDamage, 0.0f) / 25.0f;
    }

    /** Áp công thức phòng thủ tùy chỉnh, tôn trọng `BYPASSES_ARMOR`, `Armor Pierce` của Dual Blade và sàn `minDamagePerHit`. */
    protected float computeCustomDefenseDamage(DamageSource source, float rawDamage) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return rawDamage;
        }
        float pierce = com.abyssalrpg.combat.DualBladeCombatHandler.getActiveArmorPierceFraction(this, source);
        float reduction = computeEffectiveDefensePoints(rawDamage, pierce) / 25.0f;
        float reduced = rawDamage * (1.0f - reduction);
        float minDmg = Math.max(0.0f, getMinDamagePerHit());
        return rawDamage > 0.0f ? Math.max(minDmg, reduced) : 0.0f;
    }

    /**
     * Ghi đè trực tiếp `LivingEntity.getDamageAfterArmorAbsorb(DamageSource, float)` (đã kiểm chứng bằng `javap`:
     * method `protected` và KHÔNG `final` trong Mojang Mappings 1.21.1, không cần thêm Mixin).
     * Khi `getBossDefense() <= 0`, tự động fallback hoàn toàn về giáp vanilla cho Leviathan/Hydra/Cerberus.
     */
    @Override
    protected float getDamageAfterArmorAbsorb(DamageSource source, float damageAmount) {
        if (getBossDefense() <= 0.0f) {
            return super.getDamageAfterArmorAbsorb(source, damageAmount);
        }
        return computeCustomDefenseDamage(source, damageAmount);
    }

    /** Khởi tạo máu tối đa riêng và bơm đầy máu về 100% (gọi sau super() và trong configureForFloor). */
    protected void initializeBossHealth(float maxHp) {
        if (Float.isFinite(maxHp) && maxHp > 0.0f) {
            setBossMaxHealth(maxHp);
            this.setHealth(this.getMaxHealth());
        }
    }

    protected void removeAttributeModifierIfPresent(Holder<Attribute> attr, ResourceLocation id) {
        AttributeInstance inst = this.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(id);
        }
    }

    // --- Trạng thái đồng bộ Phase 2 & Khóa xoay ---
    public int getPhaseTransitionTicks() {
        return this.entityData.get(DATA_PHASE_TRANS_TICKS);
    }

    public boolean isTransitioningPhase() {
        return this.entityData.get(DATA_PHASE_TRANS_TICKS) > 0;
    }

    public boolean hasEnteredPhase2() {
        return this.hasEnteredPhase2 || this.entityData.get(DATA_HAS_ENTERED_PHASE2);
    }

    public boolean isEnragedVisual() {
        return this.entityData.get(DATA_ENRAGED_VISUAL);
    }

    /**
     * Khi trả về true: toàn bộ AI di chuyển/nhìn bị khóa, góc yaw/bodyYaw/headYaw cố định ở `DATA_LOCKED_YAW`.
     * Lớp con có thể mở rộng (ví dụ Spirit Hydra khóa thêm trong lúc chặt đầu / choáng stagger).
     */
    public boolean isRotationLocked() {
        return this.isTransitioningPhase() || this.isSpawningIn();
    }

    /**
     * Khóa cứng góc hiện tại đã chuẩn hóa về [-180, 180] và đồng bộ sang client qua DataTracker.
     */
    protected void lockCurrentRotation() {
        lockYawTo(this.getYRot());
    }

    /**
     * Khóa cứng về một góc yaw cụ thể đã chuẩn hóa [-180, 180] và đồng bộ tức thì prev* + DataTracker.
     */
    protected void lockYawTo(float yawDeg) {
        float locked = wrapAngle(yawDeg);
        this.entityData.set(DATA_LOCKED_YAW, locked);
        this.setYRot(locked);
        this.yRotO = locked;
        this.yBodyRot = locked;
        this.yBodyRotO = locked;
        this.yHeadRot = locked;
        this.yHeadRotO = locked;
        this.setXRot(0.0f);
        this.xRotO = 0.0f;
    }

    /**
     * Đổi tức thời toàn bộ góc xoay (yaw/bodyYaw/headYaw + prev*) để nhìn về một điểm mà không gây xoay nhiều vòng.
     */
    public void facePositionImmediately(Vec3 targetPos) {
        Vec3 diff = targetPos.subtract(this.position());
        if (diff.horizontalDistanceSqr() < 1e-4) {
            snapSyncPrevAngles();
            return;
        }
        float yaw = wrapAngle((float) (Mth.atan2(diff.z, diff.x) * Mth.RAD_TO_DEG) - 90.0f);
        this.entityData.set(DATA_LOCKED_YAW, yaw);
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
        this.yHeadRot = yaw;
        this.yHeadRotO = yaw;
        this.setXRot(0.0f);
        this.xRotO = 0.0f;
    }

    /**
     * Chuyển đổi tọa độ điểm trong không gian Bedrock GeoModel (đơn vị 1/16 block, -Z hướng trước, +X hướng trái)
     * sang tọa độ thế giới thật theo `yBodyRot` và hệ số render tổng `totalRenderScale`.
     */
    protected Vec3 modelPointToWorld(float modelX, float modelY, float modelZ, double totalRenderScale) {
        double unitScale = totalRenderScale / 16.0;
        double rad = this.yBodyRot * Mth.DEG_TO_RAD;
        double fwdX = -Math.sin(rad);
        double fwdZ = Math.cos(rad);
        double leftX = Math.cos(rad);
        double leftZ = Math.sin(rad);

        double forwardUnits = -modelZ;
        double wx = this.getX() + (leftX * modelX + fwdX * forwardUnits) * unitScale;
        double wy = this.getY() + (modelY * unitScale);
        double wz = this.getZ() + (leftZ * modelX + fwdZ * forwardUnits) * unitScale;
        return new Vec3(wx, wy, wz);
    }

    /**
     * Tìm điểm đáp mặt đất an toàn quanh `desired`: có nền rắn phía dưới, không bị kẹt trong block đặc,
     * không rơi vào nước sâu (>= 2 block nước) hoặc dung nham. Dùng chung cho cầu lửa tách phase, nhảy vồ và dịch chuyển.
     */
    public static Vec3 findSafeLandingPosition(ServerLevel level, Vec3 desired, Vec3 fallback, int searchRadius) {
        int baseX = Mth.floor(desired.x);
        int baseY = Mth.floor(desired.y);
        int baseZ = Mth.floor(desired.z);

        Vec3 direct = tryFindSafeColumnY(level, baseX, baseY, baseZ);
        if (direct != null) return direct;

        int maxR = Math.max(2, searchRadius);
        for (int r = 1; r <= maxR; r++) {
            for (int step = 0; step < 12; step++) {
                double ang = (Math.PI * 2.0 * step) / 12.0;
                int tx = baseX + Mth.floor(Math.cos(ang) * r);
                int tz = baseZ + Mth.floor(Math.sin(ang) * r);
                Vec3 cand = tryFindSafeColumnY(level, tx, baseY, tz);
                if (cand != null) return cand;
            }
        }
        return fallback;
    }

    private static Vec3 tryFindSafeColumnY(ServerLevel level, int x, int hintY, int z) {
        int minY = Math.max(level.getMinBuildHeight() + 2, hintY - 8);
        int maxY = Math.min(level.getMaxBuildHeight() - 4, hintY + 10);

        // Thử ưu tiên gần hintY trước, sau đó kiểm tra mặt thoáng Heightmap
        for (int dy = 0; dy <= 10; dy++) {
            for (int sign = 0; sign < 2; sign++) {
                if (dy == 0 && sign == 1) continue;
                int y = hintY + (sign == 0 ? dy : -dy);
                if (y < minY || y > maxY) continue;
                if (isSafeStandPos(level, new BlockPos(x, y, z))) {
                    return new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (surfaceY >= level.getMinBuildHeight() + 2 && surfaceY <= level.getMaxBuildHeight() - 4) {
            BlockPos sp = new BlockPos(x, surfaceY, z);
            if (isSafeStandPos(level, sp)) {
                return new Vec3(x + 0.5, surfaceY, z + 0.5);
            }
        }
        return null;
    }

    private static boolean isSafeStandPos(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState floor = level.getBlockState(below);
        if (floor.isAir() || floor.getCollisionShape(level, below).isEmpty()) return false;
        if (floor.getFluidState().is(FluidTags.LAVA) || floor.getFluidState().is(FluidTags.WATER)) return false;

        // Tránh nước sâu & dung nham tại thân/chân
        for (int h = 0; h < 3; h++) {
            BlockPos check = pos.above(h);
            BlockState st = level.getBlockState(check);
            if (!st.getCollisionShape(level, check).isEmpty()) return false;
            if (st.getFluidState().is(FluidTags.LAVA)) return false;
            if (h >= 1 && st.getFluidState().is(FluidTags.WATER)) return false; // Nước sâu >= 2 block
        }
        return true;
    }

    // =========================================================================
    // HÀM TIỆN ÍCH CHUẨN HÓA GÓC & XOAY CUNG NGẮN NHẤT (CHỐNG XOAY ĐẦU)
    // =========================================================================
    public static float wrapAngle(float degrees) {
        return Mth.wrapDegrees(degrees);
    }

    public static float approachAngle(float current, float target, float maxStepDeg) {
        float cur = Mth.wrapDegrees(current);
        float diff = Mth.wrapDegrees(target - cur);
        return Mth.wrapDegrees(cur + Mth.clamp(diff, -maxStepDeg, maxStepDeg));
    }

    public static float alignPrevAngle(float prevAngle, float currentAngle) {
        return currentAngle - Mth.wrapDegrees(currentAngle - prevAngle);
    }

    /**
     * Chuẩn hóa yaw/bodyYaw/headYaw về [-180, 180] và căn chỉnh prev* theo cung ngắn nhất mỗi tick.
     */
    protected void normalizeAnglesAndSyncPrev() {
        if (this.isRotationLocked()) {
            float locked = wrapAngle(this.entityData.get(DATA_LOCKED_YAW));
            this.setYRot(locked);
            this.yRotO = locked;
            this.yBodyRot = locked;
            this.yBodyRotO = locked;
            this.yHeadRot = locked;
            this.yHeadRotO = locked;
            this.setXRot(0.0f);
            this.xRotO = 0.0f;
            return;
        }

        float wYaw = wrapAngle(this.getYRot());
        this.setYRot(wYaw);
        this.yRotO = alignPrevAngle(this.yRotO, wYaw);

        float wBody = wrapAngle(this.yBodyRot);
        this.yBodyRot = wBody;
        this.yBodyRotO = alignPrevAngle(this.yBodyRotO, wBody);

        float relHead = Mth.clamp(Mth.wrapDegrees(this.yHeadRot - wBody), -45.0f, 45.0f);
        float wHead = wrapAngle(wBody + relHead);
        this.yHeadRot = wHead;
        this.yHeadRotO = alignPrevAngle(this.yHeadRotO, wHead);

        float clampedPitch = Mth.clamp(Mth.wrapDegrees(this.getXRot()), -30.0f, 30.0f);
        this.setXRot(clampedPitch);
        this.xRotO = alignPrevAngle(this.xRotO, clampedPitch);
    }

    public void smoothRotateToward(Vec3 direction, float baseMaxDegPerTick, float phase2TurnMult) {
        if (this.isRotationLocked()) return;
        if (direction.horizontalDistanceSqr() < 1e-4) return;

        float turnMult = (this.phase >= 2) ? phase2TurnMult : 1.0f;
        float maxStep = baseMaxDegPerTick * turnMult;

        float targetYaw = wrapAngle((float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0f);
        float newYaw = approachAngle(this.getYRot(), targetYaw, maxStep);
        float newBody = approachAngle(this.yBodyRot, newYaw, maxStep * 0.85f);
        float rawHead = approachAngle(this.yHeadRot, targetYaw, maxStep * 1.15f);
        float clampedRelHead = Mth.clamp(Mth.wrapDegrees(rawHead - newBody), -45.0f, 45.0f);
        float newHead = wrapAngle(newBody + clampedRelHead);

        this.setYRot(newYaw);
        this.yRotO = alignPrevAngle(this.yRotO, newYaw);
        this.yBodyRot = newBody;
        this.yBodyRotO = alignPrevAngle(this.yBodyRotO, newBody);
        this.yHeadRot = newHead;
        this.yHeadRotO = alignPrevAngle(this.yHeadRotO, newHead);
    }

    public void snapSyncPrevAngles() {
        float wYaw = wrapAngle(this.getYRot());
        float wBody = wrapAngle(this.yBodyRot);
        float wHead = wrapAngle(this.yHeadRot);
        this.setYRot(wYaw);
        this.yRotO = wYaw;
        this.yBodyRot = wBody;
        this.yBodyRotO = wBody;
        this.yHeadRot = wHead;
        this.yHeadRotO = wHead;
    }

    @Override
    public void tick() {
        super.tick();
        normalizeAnglesAndSyncPrev();
    }

    // =========================================================================
    // HELPER DÙNG CHUNG: ATTRIBUTE MODIFIER, DAMAGE, BOSSBAR, TELEGRAPH, NBT
    // =========================================================================
    protected void applyPermanentMultiplier(Holder<Attribute> attr, ResourceLocation id, double amount) {
        AttributeInstance inst = this.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(id);
            if (amount > 0.0) {
                inst.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    protected float estimatePostArmorDamage(DamageSource source, float rawDamage) {
        if (getBossDefense() > 0.0f) {
            return computeCustomDefenseDamage(source, rawDamage);
        }
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return rawDamage;
        }
        return CombatRules.getDamageAfterAbsorb(
                this, rawDamage, source,
                (float) this.getArmorValue(),
                (float) this.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
        );
    }

    /**
     * Hồi máu dần khi không có người chơi sống (không Spectator) trong bán kính `radius` quá `delayTicks`.
     * Trả về bộ đếm `outOfCombatTicks` mới.
     */
    protected int tickOutOfCombatRegen(ServerLevel level, int currentIdleTicks,
                                       double radius, int delayTicks, float healPerSecond) {
        if (this.isTransitioningPhase() || !this.isAlive()) {
            return 0;
        }
        double radSq = radius * radius;
        boolean playerNearby = false;
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(this) <= radSq) {
                playerNearby = true;
                break;
            }
        }
        if (playerNearby) {
            return 0;
        }
        int next = currentIdleTicks + 1;
        if (next >= delayTicks && next % 20 == 0 && healPerSecond > 0.0f && this.getHealth() < this.getMaxHealth()) {
            this.heal(healPerSecond);
        }
        return next;
    }

    protected void syncBossBarPlayers(ServerLevel level, double radius) {
        double radSq = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && player.distanceToSqr(this) <= radSq) {
                this.bossEvent.addPlayer(player);
            } else {
                this.bossEvent.removePlayer(player);
            }
        }
        this.bossEvent.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0f, 1f));
    }

    protected void announceToNearbyWithRadius(ServerLevel level, Component msg, double radius) {
        AABB box = this.getBoundingBox().inflate(radius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            player.displayClientMessage(msg, false);
        }
    }

    protected int computeSafeTelegraph(int baseTicks, int minTicks) {
        int floor = Math.max(8, minTicks); // Tối thiểu 0.4s (8 ticks)
        if (this.phase >= 2) {
            return Math.max(floor, Math.round(baseTicks / 1.25f));
        }
        return Math.max(floor, baseTicks);
    }

    protected static Vec3 rotateY(Vec3 v, double rad) {
        double c = Math.cos(rad), s = Math.sin(rad);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    protected void logGeoRotationDebug(String tag, int ticksLeft, boolean forceInfo) {
        float netHead = Mth.wrapDegrees(this.yHeadRot - this.yBodyRot);
        if (forceInfo) {
            AbyssalRpgMod.LOGGER.info(
                    "[{}#{} rem={}] yaw={} (prev={}), bodyYaw={} (prev={}), headYaw={} (prev={}), netHeadYaw={}",
                    tag, this.getId(), ticksLeft,
                    String.format("%.2f", this.getYRot()), String.format("%.2f", this.yRotO),
                    String.format("%.2f", this.yBodyRot), String.format("%.2f", this.yBodyRotO),
                    String.format("%.2f", this.yHeadRot), String.format("%.2f", this.yHeadRotO),
                    String.format("%.2f", netHead)
            );
        } else if (AbyssalRpgMod.LOGGER.isDebugEnabled()) {
            AbyssalRpgMod.LOGGER.debug(
                    "[{}#{} rem={}] yaw={}, bodyYaw={}, headYaw={}, netHeadYaw={}",
                    tag, this.getId(), ticksLeft, this.getYRot(), this.yBodyRot, this.yHeadRot, netHead
            );
        }
    }

    @Override
    public float getColossalScale(int floor) {
        return 1.0f; // Model & EntityType GeckoLib tự quản lý SCALE trực tiếp
    }

    @Override
    protected int computePhase(float hpRatio) {
        return this.hasEnteredPhase2 ? 2 : 1;
    }

    @Override
    protected void onPhaseTransition(ServerLevel level, int newPhase) {
        // Các boss GeckoLib điều phối trực tiếp bằng chuỗi cận tử của riêng mình
    }

    @Override
    protected int executeBossSkill(ServerLevel level, net.minecraft.world.entity.LivingEntity target) {
        return 40;
    }

    // Miễn nhiễm dòng chảy, ngạt nước, sát thương rơi; miễn knockback khi đang khóa chuyển phase
    @Override public boolean isPushedByFluid() { return false; }
    @Override protected int decreaseAirSupply(int currentAir) { return currentAir; }
    @Override public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) { return false; }

    @Override
    public void knockback(double strength, double x, double z) {
        if (this.isRotationLocked()) return;
        super.knockback(strength, x, z);
    }

    protected void saveSharedGeoBossData(CompoundTag tag) {
        tag.putFloat("BossMaxHealth", getBossMaxHealth());
        tag.putFloat("BossDefense", getBossDefense());
        tag.putBoolean("GeoEnteredPhase2", this.hasEnteredPhase2);
        tag.putBoolean("GeoEnragedVisual", isEnragedVisual());
        tag.putInt("GeoPhaseTransTicks", this.entityData.get(DATA_PHASE_TRANS_TICKS));
        tag.putFloat("GeoTransStartHp", this.transitionStartHp);
        tag.putInt("GeoTransTotalTicks", this.transitionTotalTicks);
        tag.putFloat("GeoLockedYaw", this.entityData.get(DATA_LOCKED_YAW));
    }

    protected void loadSharedGeoBossData(CompoundTag tag, String legacyEnteredKey, String legacyEnragedKey,
                                         String legacyTransKey, String legacyStartHpKey, String legacyTotalKey,
                                         String legacyYawKey) {
        if (tag.contains("BossMaxHealth", Tag.TAG_ANY_NUMERIC)) {
            float savedMax = tag.getFloat("BossMaxHealth");
            if (Float.isFinite(savedMax) && savedMax > 0.0f) {
                setBossMaxHealth(savedMax);
            }
        }
        if (tag.contains("BossDefense", Tag.TAG_ANY_NUMERIC)) {
            float savedDef = tag.getFloat("BossDefense");
            if (Float.isFinite(savedDef) && savedDef >= 0.0f) {
                setBossDefense(savedDef);
            }
        }

        boolean entered = tag.getBoolean("GeoEnteredPhase2")
                || (legacyEnteredKey != null && tag.getBoolean(legacyEnteredKey))
                || this.phase >= 2;
        this.hasEnteredPhase2 = entered;
        this.entityData.set(DATA_HAS_ENTERED_PHASE2, entered);

        boolean enragedVis;
        if (tag.contains("GeoEnragedVisual")) {
            enragedVis = tag.getBoolean("GeoEnragedVisual");
        } else if (legacyEnragedKey != null && tag.contains(legacyEnragedKey)) {
            enragedVis = tag.getBoolean(legacyEnragedKey);
        } else {
            enragedVis = entered;
        }
        this.entityData.set(DATA_ENRAGED_VISUAL, enragedVis);

        int transTicks = tag.contains("GeoPhaseTransTicks")
                ? tag.getInt("GeoPhaseTransTicks")
                : (legacyTransKey != null ? tag.getInt(legacyTransKey) : 0);
        this.entityData.set(DATA_PHASE_TRANS_TICKS, Math.max(0, transTicks));

        if (tag.contains("GeoTransStartHp")) this.transitionStartHp = tag.getFloat("GeoTransStartHp");
        else if (legacyStartHpKey != null && tag.contains(legacyStartHpKey)) this.transitionStartHp = tag.getFloat(legacyStartHpKey);

        if (tag.contains("GeoTransTotalTicks")) this.transitionTotalTicks = Math.max(40, tag.getInt("GeoTransTotalTicks"));
        else if (legacyTotalKey != null && tag.contains(legacyTotalKey)) this.transitionTotalTicks = Math.max(40, tag.getInt(legacyTotalKey));

        if (tag.contains("GeoLockedYaw")) this.entityData.set(DATA_LOCKED_YAW, wrapAngle(tag.getFloat("GeoLockedYaw")));
        else if (legacyYawKey != null && tag.contains(legacyYawKey)) this.entityData.set(DATA_LOCKED_YAW, wrapAngle(tag.getFloat(legacyYawKey)));
    }

    // =========================================================================
    // CONTROL & GOAL DÙNG CHUNG CHỐNG XOAY ĐẦU KHI CHUYỂN PHASE / STAGGER
    // =========================================================================
    protected static class SafeGeoLookControl extends LookControl {
        private final AbstractGeoMythicBoss boss;

        public SafeGeoLookControl(AbstractGeoMythicBoss boss) {
            super(boss);
            this.boss = boss;
        }

        @Override
        public void tick() {
            if (boss.isRotationLocked()) {
                boss.normalizeAnglesAndSyncPrev();
                return;
            }
            super.tick();
            boss.normalizeAnglesAndSyncPrev();
        }
    }

    protected static class BossLockGoal extends Goal {
        private final AbstractGeoMythicBoss boss;

        public BossLockGoal(AbstractGeoMythicBoss boss) {
            this.boss = boss;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return boss.isRotationLocked();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            boss.getNavigation().stop();
            boss.setDeltaMovement(Vec3.ZERO);
            boss.normalizeAnglesAndSyncPrev();
        }

        @Override
        public void tick() {
            boss.setDeltaMovement(Vec3.ZERO);
            boss.normalizeAnglesAndSyncPrev();
        }
    }

    protected static class SafeLookAtPlayerGoal extends LookAtPlayerGoal {
        private final AbstractGeoMythicBoss boss;

        public SafeLookAtPlayerGoal(AbstractGeoMythicBoss boss, float range) {
            super(boss, Player.class, range);
            this.boss = boss;
        }

        @Override
        public boolean canUse() {
            return !boss.isRotationLocked() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !boss.isRotationLocked() && super.canContinueToUse();
        }
    }

    protected static class SafeRandomLookGoal extends RandomLookAroundGoal {
        private final AbstractGeoMythicBoss boss;

        public SafeRandomLookGoal(AbstractGeoMythicBoss boss) {
            super(boss);
            this.boss = boss;
        }

        @Override
        public boolean canUse() {
            return !boss.isRotationLocked() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !boss.isRotationLocked() && super.canContinueToUse();
        }
    }

    /**
     * GroundPathNavigation dùng chung cho Boss GeckoLib kích thước lớn (tầm tìm đường rộng, tự nổi qua nước).
     */
    protected static class LargeGeoGroundNavigation extends GroundPathNavigation {
        public LargeGeoGroundNavigation(Mob mob, Level level) {
            super(mob, level);
            this.setCanFloat(true);
            this.setMaxVisitedNodesMultiplier(4.0f);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            return super.createPathFinder(Math.max(maxVisitedNodes, 64));
        }
    }
}

