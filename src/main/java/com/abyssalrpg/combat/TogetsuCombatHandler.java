package com.abyssalrpg.combat;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.api.BossDefenseHolder;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.entity.AbstractAbyssalBoss;
import com.abyssalrpg.item.TogetsuGlowStyle;
import com.abyssalrpg.item.TogetsuItem;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.network.TogetsuActionC2SPayload;
import com.abyssalrpg.network.TogetsuColorC2SPayload;
import com.abyssalrpg.network.TogetsuSyncPayload;
import com.abyssalrpg.registry.ModEnchantments;
import com.abyssalrpg.registry.ModItems;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trung tâm chiến đấu & nội tại Server-authoritative cho **Togetsu (兎月 — Trăng Non & Trăng Khuyết / Hợp Nhất Song Nguyệt)**:
 *
 * 1. **Nội tại Song Kiếm (Trạng thái tách rời — `32.0` dame, tốc đánh `2.4`, độ bền `2031`):**
 *    - **Trăng Non (Lưỡi Trắng):** Mỗi đòn Chí Mạng trừ đúng **`1.0 HP` (nửa tim**, sàn an toàn `1.0 HP`) để cộng **`+30%` sát thương** cho đòn đánh kế tiếp.
 *    - **Trăng Khuyết (Lưỡi Đen):** Hồi lại đúng **`1.0 HP` (nửa tim)** mỗi đòn Chí Mạng (luân phiên tạo vòng lặp cân bằng máu!).
 *    - **Thanh đo Hợp Nhất (`fusion_charge`, `0..100`):** Mỗi đòn Chí Mạng cộng **`+15` điểm** kèm tiếng keng kim loại + hạt ánh trăng trắng/đen.
 *      Nếu không tấn công trong **`5 giây` (`100` ticks)**, điểm `fusion_charge` tự giảm dần (`-4 điểm/giây`).
 *      Khi đạt `100` điểm -> tự phát sáng Enchantment Glint & hiện thông báo Action Bar `§b[Song Nguyệt Sẵn Sàng - Nhấn R / Chuột Phải]`.
 *    - **Chuột Phải (có địch phía trước `12.5` block):** **Lao Đè 12 Liên Đâm Vô Địch** (miễn nhiễm 100% sát thương suốt chiêu, xuyên `10%` DEF, `×1.6` sát thương/đòn).
 *
 * 2. **Kỹ năng chủ động — Hợp Nhất Song Nguyệt (`Twin Moon`, duy trì `15 giây` = `300` ticks, hồi đổi dạng `6s`):**
 *    - Kích hoạt bằng phím **`R`** (hoặc Chuột Phải khi đầy `100` điểm và không có mục tiêu lao đè / đang Shift).
 *    - Âm thanh kim loại va chạm mạnh (`PLAYER_ATTACK_CRIT` + `ANVIL_PLACE` pitch cao `1.65`) + bung vòng tròn hạt trắng–tím (`END_ROD` + `WITCH`).
 *    - **Tầm đánh (`ENTITY_INTERACTION_RANGE`):** tăng `+1.5` block (`3.0 -> 4.5` block) + **Chém Lan (`Sweeping Edge`)** diện rộng `4.5` block trên mọi đòn đánh thường.
 *    - **Nội tại Kẻ Thách Thức / Nghịch Lý Cấp Độ:** So sánh `MAX_HEALTH` & Giáp/BossDefense mục tiêu với người chơi
 *      -> đánh Warden `500 HP` đạt `+80%`, cộng chênh lệch giáp tối đa **`+100%` sát thương**, đồng thời tăng tốc đánh (+tối đa `40%`) & sát thương chí mạng!
 *
 * 3. **Chiêu Tất Sát — Crescent Vorpal (`Ngồi xuống Shift + Chuột Phải` khi đang Hợp Nhất, hồi `7s`):**
 *    - Phóng lưỡi liềm năng lượng trắng–đen thẳng **`15` block** xuyên mọi mục tiêu & cắt tan đạn phép địch + đẩy lùi nhẹ (`Knockback`).
 *    - Gây **sát thương chuẩn xuyên 100% giáp/phòng thủ Boss = `200%` sát thương cơ bản** và **chắc chắn 100% Chí Mạng**!
 *    - Sau khi tung chiêu: **tách ngay về dạng Song Kiếm** (`fusion_charge = 0`, hết Song Nguyệt) và áp hiệu ứng **Mệt Mỏi (`Weakness I`, `3` giây)** lên người chơi!
 */
public final class TogetsuCombatHandler {
    private static final int COMBO_RESET_TICKS = 48;
    public static final long TRANSFORM_COOLDOWN_TICKS = 120L;  // 6.0 giây mỗi lần đổi form
    public static final int FUSION_DURATION_TICKS = 300;       // 15.0 giây duy trì trạng thái Song Nguyệt
    public static final int VORPAL_COOLDOWN_TICKS = 140;       // 7.0 giây hồi chiêu Crescent Vorpal
    public static final int PIN_FLURRY_COOLDOWN_TICKS = 160;   // 8.0 giây hồi chiêu Lao Đè 12 Liên Đâm
    public static final int WAVE_SLASH_COOLDOWN_TICKS = 70;    // 3.5 giây hồi chiêu Signature RMB Crescent Wave Slash
    private static final double WAVE_SLASH_RANGE = 16.0;       // Tầm phóng sóng lưỡi liềm xoay tròn 16 block
    private static final int WAVE_SLASH_TRAVEL_TICKS = 11;     // Bay 16 block trong 11 ticks (~0.55s)
    private static final float WAVE_SLASH_DAMAGE_MULT = 1.85F; // 185% sát thương gốc + xuyên 25% DEF AoE dọc đường bay
    private static final long CHARGE_DECAY_IDLE_TICKS = 100L;  // 5.0 giây không đánh sẽ giảm dần fusion_charge
    private static final float CHARGE_DECAY_PER_TICK = 0.20F;  // -4.0 điểm / giây
    private static final float SLASH_CHARGE_GAIN = 10.0F;      // +10 điểm nguyện lực mỗi nhát chém ở dạng tách!
    private static final float WAXING_EMPOWER_BONUS = 0.30F;   // +30% sát thương đòn kế tiếp
    public static final int VORPAL_CHARGE_TICKS = 40;          // Gồng lực tư thế rút kiếm Zenitsu 2.0 giây (40 ticks)!
    private static final float VORPAL_BASE_MULT = 3.60F;       // 360% sát thương cơ bản (× 1.5 Crit, xuyên 100% DEF!)
    private static final int VORPAL_WEAKNESS_TICKS = 100;      // Dính Suy Yếu (Weakness I) trong 5 giây sau khi dùng!
    private static final double VORPAL_RANGE = 20.0;           // Tầm xa 20 block!
    private static final double PIN_SEARCH_RANGE = 15.0;       // Bán kính 15.0 block khóa mục tiêu Lao Tới Địch!
    private static final int PIN_TOTAL_HITS = 12;
    private static final float PIN_DAMAGE_MULT = 1.60F;
    private static final float PIN_BASE_ARMOR_PIERCE = 0.10F;

    private static final ResourceLocation UNDERDOG_SPEED_ID = AbyssalRpgMod.id("togetsu_underdog_speed");

    private static final Map<UUID, PlayerTogetsuState> PLAYER_STATES = new HashMap<>();
    private static final Map<UUID, PendingVorpalCast> ACTIVE_VORPALS = new HashMap<>();
    private static final Map<UUID, ActivePinFlurry> ACTIVE_PINS = new HashMap<>();
    private static final java.util.List<ActiveCrescentWave> ACTIVE_WAVES = new java.util.ArrayList<>();
    private static final ThreadLocal<ActiveHitContext> ACTIVE_HIT = new ThreadLocal<>();
    private static final ThreadLocal<Float> OVERRIDE_ARMOR_PIERCE = new ThreadLocal<>();

    private TogetsuCombatHandler() {}

    private static final class ActiveCrescentWave {
        final ServerPlayer owner;
        final ServerLevel level;
        final Vec3 origin;
        final Vec3 dir;
        final float damage;
        final float armorPierce;
        final Set<UUID> hitTargets = new HashSet<>();
        int tick = 0;

        ActiveCrescentWave(ServerPlayer owner, ServerLevel level, Vec3 origin, Vec3 dir, float damage, float armorPierce) {
            this.owner = owner;
            this.level = level;
            this.origin = origin;
            this.dir = dir;
            this.damage = damage;
            this.armorPierce = armorPierce;
        }
    }

    private static final class PlayerTogetsuState {
        int nextComboStep = 0;
        int leftHandVariant = 0;
        int rightHandVariant = 0;
        long lastSwingGameTime = -1000L;
        long lastRightSwingGameTime = -1000L;
        long lastLungeGameTime = -1000L;
        long lastCombatGameTime = -1000L;
        long underdogSpeedUntilTick = -1000L;
        /** Luân phiên Trăng Non (trừ 1 HP -> +30% đòn kế) & Trăng Khuyết (hồi 1 HP) tạo vòng lặp cân bằng máu! */
        boolean nextCritIsWaxing = true;
    }

    /** Tính thời lượng hiển thị combo 5 nhịp Song Đao (Nhịp 5 Đòn Đặc Biệt luôn giữ trọn 10.5 ticks ~0.52s)! */
    public static float computeDualComboDurationTicks(double actualAttackSpeed, TogetsuMoveType move) {
        if (move == TogetsuMoveType.DUAL_5) {
            return move.baseDisplayTicks; // 10.5F ticks exact cho Đòn Đánh Đặc Biệt nhát thứ 5!
        }
        double safeSpd = Math.max(1.2, actualAttackSpeed);
        float speedScale = (float) Mth.clamp(2.4 / safeSpd, 0.84, 1.25);
        return Mth.clamp(move.baseDisplayTicks * speedScale, 7.0F, 12.0F);
    }

    private static final class PendingVorpalCast {
        final ServerPlayer player;
        final ServerLevel level;
        int tick = 0;

        PendingVorpalCast(ServerPlayer player, ServerLevel level) {
            this.player = player;
            this.level = level;
        }
    }

    /** Trạng thái chiêu Chuột Phải Dạng Thường: Lao đến đè kẻ địch xuống & thực hiện 12 lần đâm vô địch! */
    private static final class ActivePinFlurry {
        final ServerPlayer player;
        final ServerLevel level;
        final Entity hitEntity;
        final LivingEntity realTarget;
        final Vec3 lungeStart;
        final float perStabDamage;
        final float armorPierce;
        int tick = 0;
        int hitsLanded = 0;

        ActivePinFlurry(ServerPlayer player, ServerLevel level, Entity hitEntity, LivingEntity realTarget,
                        float perStabDamage, float armorPierce) {
            this.player = player;
            this.level = level;
            this.hitEntity = hitEntity;
            this.realTarget = realTarget;
            this.lungeStart = player.position();
            this.perStabDamage = perStabDamage;
            this.armorPierce = armorPierce;
        }
    }

    public static final class ActiveHitContext {
        public final Player player;
        public final Entity rawTarget;
        @Nullable
        public final LivingEntity realTarget;
        public final ItemStack stack;
        public final boolean fused;
        public final TogetsuMoveType moveType;
        public final int comboStep;
        public final float underdogDamageBonus;
        public final float underdogGap;
        public final boolean directFullHit;
        public final boolean critTriggered;
        public final float critMultiplier;
        public final float waxingEmpowerApplied;
        public final float armorPierce;
        public boolean attackExecuted;
        public boolean hitLanded;
        public float finalRawDamage;

        ActiveHitContext(Player player, Entity rawTarget, @Nullable LivingEntity realTarget,
                         ItemStack stack, boolean fused, TogetsuMoveType moveType, int comboStep,
                         float underdogDamageBonus, float underdogGap, boolean directFullHit,
                         boolean critTriggered, float critMultiplier, float waxingEmpowerApplied,
                         float armorPierce) {
            this.player = player;
            this.rawTarget = rawTarget;
            this.realTarget = realTarget;
            this.stack = stack;
            this.fused = fused;
            this.moveType = moveType;
            this.comboStep = comboStep;
            this.underdogDamageBonus = underdogDamageBonus;
            this.underdogGap = underdogGap;
            this.directFullHit = directFullHit;
            this.critTriggered = critTriggered;
            this.critMultiplier = critMultiplier;
            this.waxingEmpowerApplied = waxingEmpowerApplied;
            this.armorPierce = armorPierce;
        }
    }

    public static boolean isHoldingTogetsu(@Nullable LivingEntity entity) {
        return entity != null && entity.getMainHandItem().is(ModItems.TOGETSU);
    }

    /** Miễn nhiễm 100% sát thương (vô địch) suốt pha lao tới đè địch & 12 nhát đâm liên thanh của Togetsu dạng thường! */
    public static boolean isPinInvulnerable(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Player player) || source == null) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        ActivePinFlurry pin = ACTIVE_PINS.get(player.getUUID());
        return pin != null && pin.tick <= 31;
    }

    /**
     * Tính **Nội tại Kẻ Thách Thức / Nghịch Lý Cấp Độ** (`0.0F .. 1.00F` -> tối đa **`+100%` sát thương**):
     * - So sánh `MAX_HEALTH` của mục tiêu với máu tối đa của người chơi (`>= 20 HP`):
     *   + Đánh Warden (`500 HP` / `20 HP` = `25×`) đạt chính xác **`+80%` (`0.80F`)** từ chênh lệch HP!
     * - So sánh thêm Giáp / `BossDefenseHolder` của mục tiêu so với người chơi: cộng tối đa **`+20%` (`0.20F`)**!
     * - Tổng khuếch đại tối đa **`+100%` (`1.00F`)** trước Boss / kẻ địch trâu hơn vượt bậc!
     */
    public static float computeUnderdogDamageBonus(Player player, @Nullable LivingEntity target) {
        if (target == null) {
            return 0.0F;
        }
        float playerMaxHp = Math.max(20.0F, player.getMaxHealth());
        float targetMaxHp = Math.max(1.0F, target.getMaxHealth());
        float hpBonus = 0.0F;
        if (targetMaxHp > playerMaxHp) {
            double ratio = targetMaxHp / (double) playerMaxHp;
            hpBonus = (float) Mth.clamp(Math.log(ratio) / Math.log(25.0), 0.0, 1.0) * 0.80F;
        }
        float targetDef = target.getArmorValue();
        if (target instanceof BossDefenseHolder holder) {
            targetDef += holder.getBossDefense();
        }
        float armorBonus = 0.0F;
        float defGap = targetDef - player.getArmorValue();
        if (defGap > 0.0F) {
            armorBonus = Mth.clamp(defGap / 30.0F, 0.0F, 1.0F) * 0.20F;
        }
        if (target instanceof AbstractAbyssalBoss) {
            hpBonus = Math.max(hpBonus, 0.60F);
        }
        return Mth.clamp(hpBonus + armorBonus, 0.0F, 1.00F);
    }

    public static float getActiveArmorPierceFraction(LivingEntity victim, DamageSource source) {
        if (source == null || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return 0.0F;
        }
        Float ov = OVERRIDE_ARMOR_PIERCE.get();
        if (ov != null && ov > 0.0F) {
            return ov;
        }
        ActiveHitContext ctx = ACTIVE_HIT.get();
        if (ctx != null && ctx.directFullHit && ctx.realTarget == victim && source.getEntity() == ctx.player) {
            return ctx.armorPierce;
        }
        return 0.0F;
    }

    private static void clearUnderdogAttackSpeed(Player player) {
        AttributeInstance inst = player.getAttribute(Attributes.ATTACK_SPEED);
        if (inst != null) {
            inst.removeModifier(UNDERDOG_SPEED_ID);
        }
    }

    // ==================== TICK SERVER: ĐẾM NGƯỢC 15s SONG NGUYỆT, GIẢM ĐIỂM SAU 5s, VORPAL & 12 LIÊN ĐÂM ====================

    public static void tickServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();
            PlayerTogetsuState st = PLAYER_STATES.get(player.getUUID());
            ItemStack main = player.getMainHandItem();

            // Gỡ mọi buff tốc đánh cũ (giữ chuẩn tốc đánh 1.5 ở dạng Hợp Nhất)
            if (st != null && st.underdogSpeedUntilTick > 0L) {
                clearUnderdogAttackSpeed(player);
                st.underdogSpeedUntilTick = -1000L;
            }

            if (!main.is(ModItems.TOGETSU)) {
                continue;
            }

            if (TogetsuItem.isFused(main)) {
                // Duy trì buff Sức Mạnh II (Strength II) & Tốc Độ II (Speed II) trong suốt thời gian ở form kết hợp!
                if ((now % 10L) == 0L) {
                    applyFusedPotionBuffs(player);
                    TogetsuItem.ensureFusedAttributesUpToDate(main);
                    clearUnderdogAttackSpeed(player);
                }
                // Trạng thái Song Nguyệt duy trì đúng 15 giây (300 ticks) dùng mốc fusion_end_tick cố định -> 0 rung tay FP!
                int left = TogetsuItem.getFusionTicksRemaining(main, now);
                if (left <= 0 && !ACTIVE_VORPALS.containsKey(player.getUUID())) {
                    endFusionState(player, main, true, false);
                }
            } else {
                // Dạng Song Đao: Nếu không tấn công trong 5 giây (100 ticks), điểm fusion_charge giảm dần (-4 điểm/giây)!
                float cur = TogetsuItem.getFusionGauge(main);
                if (cur > 0.0F && !ACTIVE_PINS.containsKey(player.getUUID()) && (now % 5L) == 0L) {
                    long lastCombat = (st != null) ? st.lastCombatGameTime : -1000L;
                    if (now - lastCombat > CHARGE_DECAY_IDLE_TICKS) {
                        float next = Math.max(0.0F, cur - (CHARGE_DECAY_PER_TICK * 5.0F));
                        TogetsuItem.setFusionState(main, false, next, 0L);
                    }
                }
            }
        }

        // 2. Thực thi pha Gồng Lực 3 giây (60 ticks — tư thế rút kiếm Zenitsu) & tung nhát chém Crescent Vorpal (360% cơ bản, 100% Crit, 100% xuyên DEF!)
        if (!ACTIVE_VORPALS.isEmpty()) {
            Iterator<Map.Entry<UUID, PendingVorpalCast>> it = ACTIVE_VORPALS.entrySet().iterator();
            while (it.hasNext()) {
                PendingVorpalCast cast = it.next().getValue();
                if (!cast.player.isAlive() || cast.player.isRemoved() || !isHoldingTogetsu(cast.player)
                        || !TogetsuItem.isFused(cast.player.getMainHandItem())) {
                    it.remove();
                    continue;
                }
                cast.tick++;
                // Giữ thế khuỵu Zenitsu vững chắc khi đang gồng lực (làm chậm bước chân nhưng vẫn xoay tâm ngắm tự do!)
                cast.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 3, false, false, false));

                if (cast.tick < VORPAL_CHARGE_TICKS) {
                    if (cast.tick % 4 == 0) {
                        float frac = cast.tick / (float) VORPAL_CHARGE_TICKS;
                        int pct = Math.round(frac * 100.0F);
                        if (cast.tick % 8 == 0) {
                            cast.level.playSound(null, cast.player.getX(), cast.player.getY() + 0.9, cast.player.getZ(),
                                    ModSounds.DUAL_BLADE_DASH_CHARGE, SoundSource.PLAYERS, 0.78F, 0.76F + frac * 0.88F);
                            ModNetworking.sendShakeNearby(cast.level, cast.player.position(), 14.0, 0.16F + frac * 0.24F, 4);
                        }
                        broadcastSync(cast.level, cast.player, TogetsuMoveType.CRESCENT_VORPAL_CHARGE, true,
                                TogetsuSyncPayload.VFX_VORPAL_CHARGE_PULSE,
                                cast.player.position().add(0.0, 0.78, 0.0), frac);
                        cast.player.displayClientMessage(
                                Component.translatable("message.abyssalrpg.togetsu.vorpal_charging", pct)
                                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                                true);
                    }
                } else {
                    executeCrescentVorpalWave(cast);
                    it.remove();
                }
            }
        }

        // 3. Thực thi chiêu Chuột Phải Dạng Thường: Lao Đè Kẻ Địch & 12 Lần Đâm Vô Địch (xuyên 10% DEF, x1.6 sát thương!)
        if (!ACTIVE_PINS.isEmpty()) {
            Iterator<Map.Entry<UUID, ActivePinFlurry>> it = ACTIVE_PINS.entrySet().iterator();
            while (it.hasNext()) {
                ActivePinFlurry pin = it.next().getValue();
                if (!tickActivePinFlurry(pin)) {
                    it.remove();
                }
            }
        }

        // 4. Thực thi sóng lưỡi liềm xoay tròn trắng–đen của Signature RMB (Crescent Wave Slash) bay AoE dọc 16 block!
        if (!ACTIVE_WAVES.isEmpty()) {
            Iterator<ActiveCrescentWave> wit = ACTIVE_WAVES.iterator();
            while (wit.hasNext()) {
                ActiveCrescentWave wave = wit.next();
                if (!tickActiveCrescentWave(wave)) {
                    wit.remove();
                }
            }
        }
    }

    private static boolean tickActiveCrescentWave(ActiveCrescentWave wave) {
        if (wave.owner.isRemoved() || !wave.owner.isAlive()) {
            return false;
        }
        wave.tick++;
        float prevFrac = (wave.tick - 1) / (float) WAVE_SLASH_TRAVEL_TICKS;
        float curFrac = wave.tick / (float) WAVE_SLASH_TRAVEL_TICKS;
        Vec3 segStart = wave.origin.add(wave.dir.scale(1.1 + prevFrac * (WAVE_SLASH_RANGE - 1.1)));
        Vec3 segEnd = wave.origin.add(wave.dir.scale(1.1 + curFrac * (WAVE_SLASH_RANGE - 1.1)));

        HitResult wall = wave.level.clip(new ClipContext(
                segStart, segEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wave.owner));
        boolean hitWall = (wall.getType() == HitResult.Type.BLOCK);
        Vec3 actualEnd = hitWall ? wall.getLocation() : segEnd;

        AABB box = new AABB(segStart, actualEnd).inflate(2.15, 1.35, 2.15);
        for (Projectile proj : wave.level.getEntitiesOfClass(Projectile.class, box,
                p -> p.isAlive() && p.getOwner() != wave.owner)) {
            if (distanceToSegmentSqr(proj.position(), segStart, actualEnd) <= 2.1 * 2.1) {
                proj.discard();
            }
        }

        OVERRIDE_ARMOR_PIERCE.set(wave.armorPierce);
        try {
            for (Entity cand : wave.level.getEntities(wave.owner, box, e -> e.isAlive() && e.isPickable() && e != wave.owner)) {
                if (distanceToSegmentSqr(cand.getBoundingBox().getCenter(), segStart, actualEnd) > 2.15 * 2.15) {
                    continue;
                }
                LivingEntity real = DualBladeCombatHandler.resolveRealLivingTarget(cand);
                if (real == null || !real.isAlive() || real == wave.owner || real.isAlliedTo(wave.owner)) {
                    continue;
                }
                if (!wave.hitTargets.add(real.getUUID())) {
                    continue;
                }
                real.invulnerableTime = 0;
                real.hurtTime = 0;
                cand.hurt(wave.owner.damageSources().playerAttack(wave.owner), wave.damage);
                real.push(wave.dir.x * 0.42, 0.24, wave.dir.z * 0.42);
                real.hurtMarked = true;
                Vec3 hp = real.getBoundingBox().getCenter();
                wave.level.sendParticles(ParticleTypes.CRIT, hp.x, hp.y, hp.z, 10, 0.30, 0.30, 0.30, 0.16);
                wave.level.playSound(null, hp.x, hp.y, hp.z,
                        ModSounds.DUAL_BLADE_CRIT, SoundSource.PLAYERS, 0.92F, 1.08F);
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
        }
        return !hitWall && wave.tick < WAVE_SLASH_TRAVEL_TICKS;
    }

    /** Signature RMB — Crescent Wave Slash: rút kiếm nhanh phóng sóng lưỡi liềm xoay tròn trắng–đen gây sát thương AoE dọc đường bay! */
    public static boolean tryCastCrescentWaveSlash(ServerPlayer player, ItemStack stack) {
        if (ACTIVE_PINS.containsKey(player.getUUID()) || ACTIVE_VORPALS.containsKey(player.getUUID())) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int rem = TogetsuItem.getWaveCooldownRemainingTicks(stack, now);
        if (rem > 0) {
            String remStr = String.format(Locale.ROOT, "%.1f", Math.max(0.1F, rem / 20.0F));
            player.displayClientMessage(
                    Component.translatable("message.abyssalrpg.togetsu.skill_cooldown", remStr)
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }
        boolean fused = TogetsuItem.isFused(stack);
        if (!fused && !player.getAbilities().instabuild && TogetsuItem.getFusionGauge(stack) < 99.5F) {
            return false;
        }

        if (!player.getAbilities().instabuild) {
            stack.hurtAndBreak(4, player, EquipmentSlot.MAINHAND);
            if (!fused) {
                // Tiêu thụ 35 điểm nộ khi tung Crescent Wave Slash từ trạng thái thanh nộ đầy ở dạng Song Đao!
                TogetsuItem.setFusionState(stack, false, Math.max(0.0F, TogetsuItem.getFusionGauge(stack) - 35.0F), 0L);
            }
        }
        TogetsuItem.setWaveCooldownUntil(stack, now + WAVE_SLASH_COOLDOWN_TICKS);
        player.getCooldowns().addCooldown(ModItems.TOGETSU, WAVE_SLASH_COOLDOWN_TICKS);
        PlayerTogetsuState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerTogetsuState());
        st.lastCombatGameTime = now;

        float baseDmg = (float) Math.max(fused ? TogetsuItem.FUSED_DAMAGE : TogetsuItem.DUAL_DAMAGE * 1.25,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float dmg = baseDmg * WAVE_SLASH_DAMAGE_MULT;
        int pierceLv = ModEnchantments.getLevel(level, stack, ModEnchantments.ARMOR_PIERCE);
        float armorPierce = Mth.clamp(0.25F + pierceLv * 0.07F, 0.25F, 0.70F);

        Vec3 dir = Vec3.directionFromRotation(Mth.clamp(player.getXRot(), -42.0F, 42.0F), player.getYRot()).normalize();
        Vec3 start = player.position().add(0.0, player.getBbHeight() * 0.66, 0.0);
        ACTIVE_WAVES.add(new ActiveCrescentWave(player, level, start, dir, dmg, armorPierce));

        level.playSound(null, player.getX(), player.getY() + 1.1, player.getZ(),
                ModSounds.DUAL_BLADE_CROSS_SLASH, SoundSource.PLAYERS, 1.10F, 0.88F);
        level.playSound(null, player.getX(), player.getY() + 1.1, player.getZ(),
                ModSounds.DUAL_BLADE_DASH_WHOOSH, SoundSource.PLAYERS, 0.95F, 1.22F);
        ModNetworking.sendShakeNearby(level, player.position(), 18.0, 0.42F, 5);
        broadcastSync(level, player, TogetsuMoveType.CRESCENT_WAVE_SLASH, true,
                TogetsuSyncPayload.VFX_CRESCENT_WAVE_PROJ, start, (float) WAVE_SLASH_RANGE);
        return true;
    }

    // ==================== PHÍM KỸ NĂNG [V]: LAO TỚI ĐỊCH 15 BLOCK / CRESCENT WAVE / CRESCENT VORPAL ====================

    public static boolean handleSkillKey(ServerPlayer player, boolean shiftHeld) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.TOGETSU) || ACTIVE_PINS.containsKey(player.getUUID()) || ACTIVE_VORPALS.containsKey(player.getUUID())) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();

        // 1. Dạng Hợp Nhất (Song Nguyệt):
        //    - [V]: Tuyệt Kỹ Tất Sát Crescent Vorpal (gồng 2s -> trảm 20 block xuyên 100% DEF!)
        //    - Shift + [V]: Trảm Sóng Nguyệt Thực Crescent Wave Slash (hồi 3.5s, giữ nguyên form!)
        if (TogetsuItem.isFused(stack)) {
            if (shiftHeld) {
                int waveRem = TogetsuItem.getWaveCooldownRemainingTicks(stack, now);
                if (waveRem > 0) {
                    String remStr = String.format(Locale.ROOT, "%.1f", Math.max(0.1F, waveRem / 20.0F));
                    player.displayClientMessage(
                            Component.translatable("message.abyssalrpg.togetsu.skill_cooldown", remStr)
                                    .withStyle(ChatFormatting.YELLOW),
                            true);
                    return false;
                }
                return tryCastCrescentWaveSlash(player, stack);
            }
            int vorpalRem = TogetsuItem.getVorpalCooldownRemainingTicks(stack, now);
            if (vorpalRem > 0) {
                String remStr = String.format(Locale.ROOT, "%.1f", Math.max(0.1F, vorpalRem / 20.0F));
                player.displayClientMessage(
                        Component.translatable("message.abyssalrpg.togetsu.skill_cooldown", remStr)
                                .withStyle(ChatFormatting.YELLOW),
                        true);
                return false;
            }
            return tryCastCrescentVorpal(player, stack);
        }

        // 2. Dạng Song Đao:
        //    - Shift + [V]: Phóng sóng Crescent Wave Slash (khi đủ Nguyện Lực hoặc Creative).
        //    - [V]: Lao Tới Địch trong bán kính 15 block + 12 Liên Đâm Vô Địch!
        boolean gaugeReady = TogetsuItem.getFusionGauge(stack) >= 99.5F || player.getAbilities().instabuild;
        if (shiftHeld) {
            int waveRem = TogetsuItem.getWaveCooldownRemainingTicks(stack, now);
            if (waveRem > 0) {
                String remStr = String.format(Locale.ROOT, "%.1f", Math.max(0.1F, waveRem / 20.0F));
                player.displayClientMessage(
                        Component.translatable("message.abyssalrpg.togetsu.skill_cooldown", remStr)
                                .withStyle(ChatFormatting.YELLOW),
                        true);
                return false;
            }
            if (!gaugeReady) {
                int pct = Math.round(TogetsuItem.getFusionGauge(stack));
                player.displayClientMessage(
                        Component.translatable("message.abyssalrpg.togetsu.gauge_not_full", pct)
                                .withStyle(ChatFormatting.YELLOW),
                        true);
                return false;
            }
            return tryCastCrescentWaveSlash(player, stack);
        }

        int pinRem = TogetsuItem.getPinCooldownRemainingTicks(stack, now);
        if (pinRem > 0) {
            String remStr = String.format(Locale.ROOT, "%.1f", Math.max(0.1F, pinRem / 20.0F));
            player.displayClientMessage(
                    Component.translatable("message.abyssalrpg.togetsu.skill_cooldown", remStr)
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }
        Entity hitEntity = findPinTarget(player, level);
        LivingEntity realTarget = DualBladeCombatHandler.resolveRealLivingTarget(hitEntity);
        if (hitEntity != null && realTarget != null && realTarget.isAlive()) {
            return startDualPinFlurry(player, stack, hitEntity, realTarget);
        }

        player.displayClientMessage(
                Component.translatable("message.abyssalrpg.togetsu.pin_no_target").withStyle(ChatFormatting.YELLOW),
                true);
        return false;
    }

    public static boolean handleDualRightClick(ServerPlayer player, ItemStack stack) {
        return handleSkillKey(player, player.isShiftKeyDown());
    }

    private static boolean startDualPinFlurry(ServerPlayer player, ItemStack stack, Entity hitEntity, LivingEntity realTarget) {
        if (!player.getOffhandItem().isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.abyssalrpg.togetsu.need_empty_offhand").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        double baseDmg = Math.max(TogetsuItem.DUAL_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float perStabDmg = (float) (baseDmg * PIN_DAMAGE_MULT); // x1.6 sát thương gốc (= 51.2 raw/hit!)
        int pierceLv = ModEnchantments.getLevel(level, stack, ModEnchantments.ARMOR_PIERCE);
        float totalPierce = Mth.clamp(PIN_BASE_ARMOR_PIERCE + pierceLv * 0.07F, PIN_BASE_ARMOR_PIERCE, 0.65F);

        if (!player.getAbilities().instabuild) {
            stack.hurtAndBreak(6, player, EquipmentSlot.MAINHAND);
        }
        long now = level.getGameTime();
        TogetsuItem.setPinCooldownUntil(stack, now + PIN_FLURRY_COOLDOWN_TICKS);
        player.getCooldowns().addCooldown(ModItems.TOGETSU, PIN_FLURRY_COOLDOWN_TICKS);
        PlayerTogetsuState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerTogetsuState());
        st.lastCombatGameTime = now;

        ActivePinFlurry pin = new ActivePinFlurry(player, level, hitEntity, realTarget, perStabDmg, totalPierce);
        ACTIVE_PINS.put(player.getUUID(), pin);

        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                ModSounds.DUAL_BLADE_DASH_WHOOSH, SoundSource.PLAYERS, 1.05F, 1.15F);
        broadcastSync(level, player, TogetsuMoveType.DUAL_PIN_LUNGE, false,
                TogetsuSyncPayload.VFX_NONE, player.position().add(0, 1.0, 0), 1.0F);
        return true;
    }

    @Nullable
    private static Entity findPinTarget(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        // Quét trọn bán kính 15.0 block xung quanh người chơi!
        AABB box = player.getBoundingBox().inflate(PIN_SEARCH_RANGE, 6.5, PIN_SEARCH_RANGE);

        Entity bestAimed = null;
        double bestAimedScore = Double.MAX_VALUE;
        Entity bestAnyHostile = null;
        double bestHostileDist = Double.MAX_VALUE;
        Entity bestAnyLiving = null;
        double bestLivingDist = Double.MAX_VALUE;

        for (Entity cand : level.getEntities(player, box, e -> e.isAlive() && e.isPickable() && e != player)) {
            LivingEntity real = DualBladeCombatHandler.resolveRealLivingTarget(cand);
            if (real == null || !real.isAlive() || real == player || real.isAlliedTo(player)) {
                continue;
            }
            Vec3 to = cand.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist < 0.05 || dist > PIN_SEARCH_RANGE) {
                continue;
            }
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot >= 0.40) {
                double score = dist * (1.38 - dot);
                if (score < bestAimedScore) {
                    bestAimedScore = score;
                    bestAimed = cand;
                }
            }
            boolean hostile = (real instanceof Enemy) || (real instanceof AbstractAbyssalBoss);
            if (hostile && dist < bestHostileDist) {
                bestHostileDist = dist;
                bestAnyHostile = cand;
            } else if (dist < bestLivingDist) {
                bestLivingDist = dist;
                bestAnyLiving = cand;
            }
        }
        // Ưu tiên 1: mục tiêu phía trước tầm ngắm -> Ưu tiên 2: quái thù địch/Boss gần nhất trong 15 block -> Ưu tiên 3: sinh vật gần nhất trong 15 block!
        if (bestAimed != null) return bestAimed;
        if (bestAnyHostile != null) return bestAnyHostile;
        return bestAnyLiving;
    }

    private static boolean tickActivePinFlurry(ActivePinFlurry pin) {
        ServerPlayer player = pin.player;
        ServerLevel level = pin.level;
        if (!player.isAlive() || player.isRemoved() || !isHoldingTogetsu(player)) {
            return false;
        }
        pin.tick++;
        player.fallDistance = 0.0F;
        PlayerTogetsuState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerTogetsuState());
        st.lastCombatGameTime = level.getGameTime();

        if (pin.tick > 28) {
            return pin.tick <= 30;
        }
        if (!pin.realTarget.isAlive() || pin.realTarget.isRemoved()) {
            return false;
        }

        Vec3 targetCenter = pin.hitEntity.isAlive()
                ? pin.hitEntity.getBoundingBox().getCenter()
                : pin.realTarget.getBoundingBox().getCenter();
        Vec3 fromTargetToPlayer = player.position().subtract(pin.realTarget.position());
        Vec3 horiz = new Vec3(fromTargetToPlayer.x, 0.0, fromTargetToPlayer.z);
        Vec3 approachDir = (horiz.lengthSqr() > 1e-4)
                ? horiz.normalize()
                : Vec3.directionFromRotation(0.0F, player.getYRot()).scale(-1.0);
        double standoff = Math.max(1.05, Math.min(2.4, pin.realTarget.getBbWidth() * 0.52 + 0.65));
        Vec3 pinAnchor = new Vec3(
                pin.realTarget.getX() + approachDir.x * standoff,
                pin.realTarget.getY(),
                pin.realTarget.getZ() + approachDir.z * standoff
        );

        // Pha 1 (ticks 1..4): Lao tốc độ cao áp sát và ĐÈ kẻ địch xuống đất!
        if (pin.tick <= 4) {
            float t = pin.tick / 4.0F;
            Vec3 desired = pin.lungeStart.lerp(pinAnchor, t);
            HitResult wall = level.clip(new ClipContext(
                    player.position().add(0, 0.9, 0), desired.add(0, 0.9, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (wall.getType() == HitResult.Type.BLOCK) {
                desired = wall.getLocation().subtract(desired.subtract(player.position()).normalize().scale(0.55));
                desired = new Vec3(desired.x, pinAnchor.y, desired.z);
            }
            float lookYaw = (float) (Math.toDegrees(Math.atan2(targetCenter.z - desired.z, targetCenter.x - desired.x)) - 90.0);
            player.teleportTo(level, desired.x, desired.y, desired.z, lookYaw, 18.0F);
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;

            if (pin.tick == 4) {
                pin.realTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 32, 6, false, false, true));
                pin.realTarget.setDeltaMovement(0.0, -0.45, 0.0);
                pin.realTarget.hurtMarked = true;
                level.playSound(null, targetCenter.x, targetCenter.y, targetCenter.z,
                        SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.15F, 0.72F);
                level.playSound(null, targetCenter.x, targetCenter.y, targetCenter.z,
                        ModSounds.DUAL_BLADE_CROSS_SLASH, SoundSource.PLAYERS, 1.0F, 0.85F);
                ModNetworking.sendShakeNearby(level, targetCenter, 16.0, 0.55F, 6);
                broadcastSync(level, player, TogetsuMoveType.DUAL_PIN_FLURRY, false,
                        TogetsuSyncPayload.VFX_PIN_IMPACT, targetCenter, 1.0F);
            }
            return true;
        }

        // Pha 2 (ticks 5..28): Đè chặt mục tiêu & thực hiện ĐÚNG 12 LẦN ĐÂM VÔ ĐỊCH (mỗi 2 ticks 1 nhát = 12 nhát!)
        pin.realTarget.setDeltaMovement(0.0, Math.min(pin.realTarget.getDeltaMovement().y, -0.22), 0.0);
        pin.realTarget.hurtMarked = true;

        if (player.position().distanceToSqr(pinAnchor) > 1.8 * 1.8) {
            float lookYaw = (float) (Math.toDegrees(Math.atan2(targetCenter.z - pinAnchor.z, targetCenter.x - pinAnchor.x)) - 90.0);
            player.teleportTo(level, pinAnchor.x, pinAnchor.y, pinAnchor.z, lookYaw, 16.0F);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        if ((pin.tick - 5) % 2 == 0 && pin.hitsLanded < PIN_TOTAL_HITS) {
            int stabIdx = pin.hitsLanded;
            pin.hitsLanded++;
            boolean waxingStab = (stabIdx % 2 == 0);
            ItemStack stack = player.getMainHandItem();

            float dmg = pin.perStabDamage;
            if (pin.hitsLanded == PIN_TOTAL_HITS && TogetsuItem.isWaxingEmpowered(stack)) {
                dmg *= (1.0F + TogetsuItem.getWaxingEmpowerBonus(stack));
                TogetsuItem.setWaxingEmpower(stack, false, 0.0F);
            }

            pin.realTarget.invulnerableTime = 0;
            pin.realTarget.hurtTime = 0;
            Entity dmgTarget = pin.hitEntity.isAlive() ? pin.hitEntity : pin.realTarget;
            OVERRIDE_ARMOR_PIERCE.set(pin.armorPierce);
            try {
                dmgTarget.hurt(player.damageSources().playerAttack(player), dmg);
            } finally {
                OVERRIDE_ARMOR_PIERCE.remove();
            }

            // Nạp nhẹ Thanh Hợp Nhất qua 12 nhát (+3.5 điểm/nhát)
            float prevGauge = TogetsuItem.getFusionGauge(stack);
            TogetsuItem.addFusionGauge(stack, 3.5F);
            if (prevGauge < 100.0F && TogetsuItem.getFusionGauge(stack) >= 100.0F) {
                notifyFusionReady(level, player);
            }

            float pitch = 1.02F + (stabIdx / (float) PIN_TOTAL_HITS) * 0.56F;
            level.playSound(null, targetCenter.x, targetCenter.y, targetCenter.z,
                    pin.hitsLanded == PIN_TOTAL_HITS ? ModSounds.DUAL_BLADE_CRIT : ModSounds.DUAL_BLADE_SLASH,
                    SoundSource.PLAYERS, pin.hitsLanded == PIN_TOTAL_HITS ? 1.05F : 0.78F, pitch);

            byte stabVfx = waxingStab ? TogetsuSyncPayload.VFX_PIN_STAB_WAXING : TogetsuSyncPayload.VFX_PIN_STAB_WANING;
            broadcastSync(level, player, TogetsuMoveType.DUAL_PIN_FLURRY, false, stabVfx, targetCenter, (stabIdx + 1) / 12.0F);

            if (pin.hitsLanded == PIN_TOTAL_HITS) {
                ModNetworking.sendShakeNearby(level, targetCenter, 16.0, 0.45F, 5);
                player.setDeltaMovement(approachDir.x * 0.36, 0.22, approachDir.z * 0.36);
                player.hurtMarked = true;
            }
        }

        return true;
    }

    private static void notifyFusionReady(ServerLevel level, Player player) {
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.45F);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.85F, 1.55F);
        player.displayClientMessage(
                Component.translatable("message.abyssalrpg.togetsu.gauge_ready").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                true);
    }

    // ==================== PHÍM R / TWIN MOON IGNITION (15 GIÂY · HỒI ĐỔI DẠNG 6 GIÂY) ====================

    public static void handleActionPacket(ServerPlayer player, TogetsuActionC2SPayload payload) {
        if (!player.isAlive() || player.isSpectator() || !isHoldingTogetsu(player)) {
            return;
        }
        if (payload.action() == TogetsuActionC2SPayload.ACTION_TOGGLE_FUSE) {
            handleToggleFuse(player);
        } else if (payload.action() == TogetsuActionC2SPayload.ACTION_AIR_SLASH) {
            handleAirSlash(player, payload.targetEntityId());
        } else if (payload.action() == TogetsuActionC2SPayload.ACTION_RIGHT_SLASH) {
            handleDualRightHandSlash(player, payload.targetEntityId());
        } else if (payload.action() == TogetsuActionC2SPayload.ACTION_CAST_SKILL) {
            handleSkillKey(player, payload.targetEntityId() != 0);
        }
    }

    public static void handleColorPacket(ServerPlayer player, TogetsuColorC2SPayload payload) {
        if (!player.isAlive() || player.isSpectator() || !isHoldingTogetsu(player)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        TogetsuGlowStyle targetStyle;
        int pri;
        int sec;
        if (payload.cycleNext()) {
            targetStyle = TogetsuItem.getGlowStyle(stack).nextPreset();
            pri = targetStyle.defaultPrimaryRgb;
            sec = targetStyle.defaultSecondaryRgb;
        } else {
            targetStyle = TogetsuGlowStyle.byId(payload.styleId());
            pri = payload.primaryRgb() & 0xFFFFFF;
            sec = payload.secondaryRgb() & 0xFFFFFF;
        }
        TogetsuItem.setGlowColor(stack, targetStyle, pri, sec);

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 1.35F);

        int showPri = targetStyle.resolvePrimaryRgb(pri, 0.0F);
        int showSec = targetStyle.resolveSecondaryRgb(sec, 0.0F);
        player.displayClientMessage(
                Component.translatable("message.abyssalrpg.togetsu.color_changed",
                        Component.literal("◆").withStyle(s -> s.withColor(showPri)),
                        Component.literal("◆").withStyle(s -> s.withColor(showSec)),
                        Component.translatable(targetStyle.translationKey()).withStyle(s -> s.withColor(showPri).withBold(true)))
                        .withStyle(ChatFormatting.WHITE),
                true);
    }

    private static void handleToggleFuse(ServerPlayer player) {
        if (ACTIVE_PINS.containsKey(player.getUUID()) || ACTIVE_VORPALS.containsKey(player.getUUID())) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!TogetsuItem.isFused(stack)) {
            tryActivateFusion(player, stack);
        } else {
            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();
            int remTicks = TogetsuItem.getTransformCooldownRemainingTicks(stack, now);
            if (remTicks > 0) {
                String sec = String.format(Locale.ROOT, "%.1f", remTicks / 20.0F);
                player.displayClientMessage(
                        Component.translatable("message.abyssalrpg.togetsu.transform_cooldown", sec)
                                .withStyle(ChatFormatting.YELLOW),
                        true);
                return;
            }
            TogetsuItem.setTransformCooldownUntil(stack, now + TRANSFORM_COOLDOWN_TICKS);
            endFusionState(player, stack, false, false);
        }
    }

    public static boolean tryActivateFusion(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();

        // Thời gian hồi giữa mỗi lần đổi form = 6.0 giây (120 ticks)!
        int remTicks = TogetsuItem.getTransformCooldownRemainingTicks(stack, now);
        if (remTicks > 0) {
            String sec = String.format(Locale.ROOT, "%.1f", remTicks / 20.0F);
            player.displayClientMessage(
                    Component.translatable("message.abyssalrpg.togetsu.transform_cooldown", sec)
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }
        if (!player.getOffhandItem().isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.abyssalrpg.togetsu.need_empty_offhand").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        float gauge = TogetsuItem.getFusionGauge(stack);
        if (!player.getAbilities().instabuild && gauge < 99.5F) {
            player.displayClientMessage(
                    Component.translatable("message.abyssalrpg.togetsu.gauge_not_full", Math.round(gauge))
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }

        PlayerTogetsuState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerTogetsuState());
        st.nextComboStep = 0;
        st.lastCombatGameTime = now;

        // Hợp nhất trong đúng 15 giây (300 ticks) với mốc fusion_end_tick cố định!
        TogetsuItem.setFusionState(stack, true, 100.0F, now + FUSION_DURATION_TICKS);
        TogetsuItem.setTransformCooldownUntil(stack, now + TRANSFORM_COOLDOWN_TICKS);

        // Buff ngay Tăng Sức Mạnh II (Strength II) & Tốc Độ II (Speed II) khi lên form kết hợp!
        applyFusedPotionBuffs(player);

        // Hồi chiêu 2 dạng độc lập: nếu đang hồi chiêu dạng tách thì khi hợp nhất có thể dùng Crescent Vorpal NGAY (không phải chờ!)
        int remVorpal = TogetsuItem.getVorpalCooldownRemainingTicks(stack, now);
        if (remVorpal > 0) {
            player.getCooldowns().addCooldown(ModItems.TOGETSU, remVorpal);
        } else {
            player.getCooldowns().removeCooldown(ModItems.TOGETSU);
        }

        // Twin Moon Ignition SFX: entity.player.attack.crit + block.anvil.place pitch cao!
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.15F, 0.95F);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.90F, 1.65F);
        ModNetworking.sendShakeNearby(level, player.position(), 18.0, 0.45F, 8);
        broadcastSync(level, player, TogetsuMoveType.FUSE_TRANSFORM, true,
                TogetsuSyncPayload.VFX_FUSE_BURST, player.position().add(0, 1.0, 0), 1.0F);
        player.displayClientMessage(
                Component.translatable("message.abyssalrpg.togetsu.fused_activated").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                true);
        return true;
    }

    private static void endFusionState(ServerPlayer player, ItemStack stack, boolean expiredNaturally, boolean fromVorpal) {
        ServerLevel level = player.serverLevel();
        PlayerTogetsuState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerTogetsuState());
        st.nextComboStep = 0;
        clearUnderdogAttackSpeed(player);
        clearFusedPotionBuffs(player);
        TogetsuItem.setFusionState(stack, false, 0.0F, 0L);

        // Khôi phục đúng thanh hồi chiêu độc lập của dạng tách (nếu có), không để hồi chiêu Vorpal chặn ngược dạng tách!
        int remPin = TogetsuItem.getPinCooldownRemainingTicks(stack, level.getGameTime());
        if (remPin > 0) {
            player.getCooldowns().addCooldown(ModItems.TOGETSU, remPin);
        } else {
            player.getCooldowns().removeCooldown(ModItems.TOGETSU);
        }

        if (!fromVorpal) {
            level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                    SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.85F, 1.30F);
            broadcastSync(level, player, TogetsuMoveType.UNFUSE_SPLIT, false,
                    TogetsuSyncPayload.VFX_UNFUSE_BURST, player.position().add(0, 1.0, 0), 1.0F);
            player.displayClientMessage(
                    Component.translatable(expiredNaturally
                            ? "message.abyssalrpg.togetsu.fusion_ended"
                            : "message.abyssalrpg.togetsu.unfused")
                            .withStyle(expiredNaturally ? ChatFormatting.GRAY : ChatFormatting.WHITE),
                    true);
        }
    }

    private static void applyFusedPotionBuffs(ServerPlayer player) {
        // Cấp Strength II (amp 1) & Speed II (amp 1) ngắn hạn tự gia hạn trong lúc giữ form Song Nguyệt
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false, true));
    }

    private static void clearFusedPotionBuffs(ServerPlayer player) {
        MobEffectInstance str = player.getEffect(MobEffects.DAMAGE_BOOST);
        if (str != null && str.getAmplifier() == 1 && str.getDuration() <= 45) {
            player.removeEffect(MobEffects.DAMAGE_BOOST);
        }
        MobEffectInstance spd = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (spd != null && spd.getAmplifier() == 1 && spd.getDuration() <= 45) {
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
        }
    }

    // ==================== CHIÊU TẤT SÁT: CRESCENT VORPAL (GỒNG LỰC 4s · 360% CƠ BẢN · 100% CRIT · XUYÊN 100% DEF) ====================

    public static boolean tryCastCrescentVorpal(ServerPlayer player, ItemStack stack) {
        if (!TogetsuItem.isFused(stack) || ACTIVE_VORPALS.containsKey(player.getUUID()) || ACTIVE_PINS.containsKey(player.getUUID())) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (!player.getAbilities().instabuild) {
            stack.hurtAndBreak(8, player, EquipmentSlot.MAINHAND);
        }
        // Gia hạn mốc fusion_end_tick tối thiểu đủ 3 giây gồng lực để không bị hết giờ giữa chừng!
        long minEnd = now + VORPAL_CHARGE_TICKS + 16L;
        if (TogetsuItem.getFusionEndTick(stack) < minEnd) {
            TogetsuItem.setFusionState(stack, true, 100.0F, minEnd);
        }

        // Thời gian hồi chiêu dạng kết hợp độc lập: đúng 7.0 giây (140 ticks) sau khi hoàn tất (cộng cả 3s gồng)
        TogetsuItem.setVorpalCooldownUntil(stack, now + VORPAL_CHARGE_TICKS + VORPAL_COOLDOWN_TICKS);
        player.getCooldowns().addCooldown(ModItems.TOGETSU, VORPAL_CHARGE_TICKS + VORPAL_COOLDOWN_TICKS);

        ACTIVE_VORPALS.put(player.getUUID(), new PendingVorpalCast(player, level));
        level.playSound(null, player.getX(), player.getY() + 0.9, player.getZ(),
                ModSounds.DUAL_BLADE_DASH_CHARGE, SoundSource.PLAYERS, 1.05F, 0.72F);
        level.playSound(null, player.getX(), player.getY() + 0.9, player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.85F, 0.86F);
        broadcastSync(level, player, TogetsuMoveType.CRESCENT_VORPAL_CHARGE, true,
                TogetsuSyncPayload.VFX_VORPAL_CHARGE_PULSE, player.position().add(0, 0.78, 0), 0.05F);
        player.displayClientMessage(
                Component.translatable("message.abyssalrpg.togetsu.vorpal_charging", 0)
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                true);
        return true;
    }

    private static void executeCrescentVorpalWave(PendingVorpalCast cast) {
        ServerPlayer player = cast.player;
        ServerLevel level = cast.level;
        ItemStack stack = player.getMainHandItem();

        Vec3 look = Vec3.directionFromRotation(Mth.clamp(player.getXRot(), -45.0F, 45.0F), player.getYRot()).normalize();
        Vec3 start = player.getEyePosition().add(0.0, -0.25, 0.0);
        Vec3 end = start.add(look.scale(VORPAL_RANGE));

        // Cắt dừng nếu đụng tường đá đặc, nhưng XUYÊN QUA mọi quái, Boss & đạn phép địch trong tầm 15 block!
        HitResult blockHit = level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double actualDist = (blockHit.getType() == HitResult.Type.BLOCK)
                ? Math.max(2.5, blockHit.getLocation().distanceTo(start))
                : VORPAL_RANGE;
        Vec3 actualEnd = start.add(look.scale(actualDist));

        // 1. Chém tan mọi đạn phép / vật thể phóng của kẻ địch dọc đường luồng trăng khuyết
        AABB sweepBox = new AABB(start, actualEnd).inflate(2.4);
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, sweepBox,
                proj -> proj.isAlive() && proj.getOwner() != player);
        for (Projectile proj : projectiles) {
            if (distanceToSegmentSqr(proj.position(), start, actualEnd) <= 2.3 * 2.3) {
                proj.discard();
            }
        }

        // 2. Sát thương chuẩn (100% xuyên giáp & BossDefense!) bằng 200% sát thương cơ bản + CHẮC CHẮN 100% CHÍ MẠNG (x1.5) + đẩy lùi nhẹ!
        float baseFusedDmg = (float) Math.max(TogetsuItem.FUSED_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        boolean hadWaxingEmpower = TogetsuItem.isWaxingEmpowered(stack);
        float empowerBonus = hadWaxingEmpower ? TogetsuItem.getWaxingEmpowerBonus(stack) : 0.0F;
        if (hadWaxingEmpower) {
            TogetsuItem.setWaxingEmpower(stack, false, 0.0F);
        }

        List<Entity> candidates = level.getEntities(player, sweepBox, e -> e.isAlive() && e.isPickable() && e != player);
        Set<UUID> seen = new LinkedHashSet<>();
        OVERRIDE_ARMOR_PIERCE.set(1.00F); // 100% Sát thương chuẩn xuyên giáp thường & BossDefense!
        try {
            for (Entity cand : candidates) {
                if (distanceToSegmentSqr(cand.getBoundingBox().getCenter(), start, actualEnd) > 2.25 * 2.25) {
                    continue;
                }
                LivingEntity real = DualBladeCombatHandler.resolveRealLivingTarget(cand);
                if (real == null || !real.isAlive() || real == player || real.isAlliedTo(player)) {
                    continue;
                }
                if (!seen.add(real.getUUID())) {
                    continue;
                }
                // 360% sát thương cơ bản × 1.5 (100% Crit) × (1 + Trăng Non nếu có) — Bỏ qua 100% DEF!
                float dmg = baseFusedDmg * VORPAL_BASE_MULT * 1.50F * (1.0F + empowerBonus);

                real.invulnerableTime = 0;
                real.hurtTime = 0;
                DamageSource src = player.damageSources().playerAttack(player);
                cand.hurt(src, dmg);

                // Đẩy lùi (Knockback) nhẹ quái vật theo hướng lưỡi liềm
                real.push(look.x * 0.65, 0.24, look.z * 0.65);
                real.hurtMarked = true;

                level.sendParticles(ParticleTypes.CRIT, real.getX(), real.getY() + real.getBbHeight() * 0.6, real.getZ(),
                        16, 0.40, 0.40, 0.40, 0.20);
                level.playSound(null, real.getX(), real.getY() + 1.0, real.getZ(),
                        ModSounds.DUAL_BLADE_CRIT, SoundSource.PLAYERS, 1.10F, 0.82F);
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
        }

        // Âm thanh + rung màn hình cực mạnh + phát VFX lưỡi liềm năng lượng trắng–đen 20 block!
        level.playSound(null, player.getX(), player.getY() + 1.1, player.getZ(),
                ModSounds.DUAL_BLADE_CROSS_SLASH, SoundSource.PLAYERS, 1.25F, 0.70F);
        level.playSound(null, player.getX(), player.getY() + 1.1, player.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.20F, 0.78F);
        ModNetworking.sendShakeNearby(level, player.position(), 28.0, 1.05F, 12);
        broadcastSync(level, player, TogetsuMoveType.CRESCENT_VORPAL, true,
                TogetsuSyncPayload.VFX_CRESCENT_VORPAL_WAVE, start, (float) actualDist);

        // Hậu quả: Quay ngay về trạng thái tách kiếm & dính Suy Yếu (Weakness I) trong 5 giây (100 ticks)!
        if (stack.is(ModItems.TOGETSU)) {
            endFusionState(player, stack, false, true);
        }
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, VORPAL_WEAKNESS_TICKS, 0, false, true, true));
        player.displayClientMessage(
                Component.translatable("message.abyssalrpg.togetsu.vorpal_exhausted").withStyle(ChatFormatting.LIGHT_PURPLE),
                true);
    }

    private static double distanceToSegmentSqr(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lenSqr = ab.lengthSqr();
        if (lenSqr < 1e-6) return p.distanceToSqr(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / lenSqr, 0.0, 1.0);
        return p.distanceToSqr(a.add(ab.scale(t)));
    }

    // ==================== CHÉM THƯỜNG & CHÉM GIÓ (NỘI TẠI TRĂNG NON / TRĂNG KHUYẾT / KẺ THÁCH THỨC) ====================

    @Nullable
    public static ActiveHitContext beginPlayerAttack(Player player, Entity rawTarget) {
        if (player.level().isClientSide || !isHoldingTogetsu(player)) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        boolean fused = TogetsuItem.isFused(stack);
        LivingEntity realTarget = DualBladeCombatHandler.resolveRealLivingTarget(rawTarget);
        float strength = player.getAttackStrengthScale(0.5F);
        boolean directFullHit = strength >= 0.72F;

        PlayerTogetsuState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerTogetsuState());
        long now = player.level().getGameTime();
        if (now - st.lastSwingGameTime > COMBO_RESET_TICKS) {
            st.nextComboStep = 0;
        }
        int step = st.nextComboStep;
        // Chuột Trái ở dạng Song Đao = Kiếm Trái (Trăng Non — Lưỡi Trắng: DUAL_2 / DUAL_4, nhát thứ 5 = DUAL_5 Đặc Biệt!)
        TogetsuMoveType moveType = fused
                ? TogetsuMoveType.fusedByStep(step)
                : TogetsuMoveType.resolveDualHandMove(false, player.onGround(), player.isInWater(),
                        player.fallDistance, player.isSprinting(), step, st.leftHandVariant);
        float underdogDmgBonus = computeUnderdogDamageBonus(player, realTarget);
        float gapRatio = underdogDmgBonus; // 0.0..1.0

        // Kiểm tra đòn Chí Mạng: nhảy chém vanilla (Crit) HOẶC xác suất Chí Mạng nội tại / Enchant Critical Strike!
        boolean vanillaFallCrit = player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater();
        int critLv = ModEnchantments.getLevel(player.level(), stack, ModEnchantments.CRITICAL_STRIKE);
        int pierceLv = ModEnchantments.getLevel(player.level(), stack, ModEnchantments.ARMOR_PIERCE);

        float critChance = fused
                ? (0.45F + gapRatio * 0.48F + critLv * 0.08F) // Song Nguyệt: 45% -> tới 95% khi đấu Boss!
                : (0.40F + gapRatio * 0.20F + critLv * 0.08F); // Song Đao: chém thường đứng đất vẫn có 40%..60%+ tỉ lệ Crit (không cần nhảy)!
        boolean critTriggered = directFullHit && realTarget != null
                && (vanillaFallCrit || player.level().random.nextFloat() < Mth.clamp(critChance, 0.0F, 0.95F));
        // Song Nguyệt tăng thêm sát thương Chí Mạng theo mức chênh lệch sức mạnh (1.60x -> 2.00x!)
        float critMult = critTriggered ? (fused ? (1.60F + gapRatio * 0.40F) : 1.50F) : 1.0F;

        // Tiêu thụ cường hóa +30% từ Trăng Non nếu đang tích sẵn!
        float empowerApplied = 0.0F;
        if (directFullHit && realTarget != null && TogetsuItem.isWaxingEmpowered(stack)) {
            empowerApplied = TogetsuItem.getWaxingEmpowerBonus(stack);
        }

        float armorPierce = Mth.clamp((fused ? 0.15F : 0.05F) + pierceLv * 0.07F, 0.0F, 0.65F);

        ActiveHitContext ctx = new ActiveHitContext(
                player, rawTarget, realTarget, stack, fused, moveType, step,
                underdogDmgBonus, gapRatio, directFullHit, critTriggered, critMult, empowerApplied, armorPierce
        );
        ACTIVE_HIT.set(ctx);
        return ctx;
    }

    public static float modifyAttackDamage(float originalAmount) {
        ActiveHitContext ctx = ACTIVE_HIT.get();
        if (ctx == null) {
            return originalAmount;
        }
        ctx.attackExecuted = true;
        float mult = 1.0F;
        // Dạng Song Nguyệt — Nội tại Kẻ Thách Thức / Nghịch Lý Cấp Độ: tối đa +100% sát thương (Warden 500 HP = +80%)!
        if (ctx.fused && ctx.underdogDamageBonus > 0.01F) {
            mult *= (1.0F + ctx.underdogDamageBonus);
        }
        // Cường hóa +30% sát thương cho đòn kế tiếp từ nội tại Trăng Non!
        if (ctx.waxingEmpowerApplied > 0.01F) {
            mult *= (1.0F + ctx.waxingEmpowerApplied);
        }
        // Đòn Đánh Đặc Biệt nhát thứ 5 (DUAL_5 — Auto-Fuse Eclipse Morphing Finisher): ×1.60 sát thương kết liễu!
        if (!ctx.fused && ctx.moveType == TogetsuMoveType.DUAL_5) {
            mult *= 1.60F;
        }
        // Nhân sát thương Chí Mạng (tránh nhân đúp 1.5x nếu vanilla fall-crit đã nhân!)
        if (ctx.critTriggered) {
            boolean vanillaFallCrit = ctx.player.fallDistance > 0.0F && !ctx.player.onGround()
                    && !ctx.player.onClimbable() && !ctx.player.isInWater();
            mult *= vanillaFallCrit ? (ctx.critMultiplier / 1.50F) : ctx.critMultiplier;
        }
        float modified = originalAmount * mult;
        ctx.finalRawDamage = modified;
        return modified;
    }

    public static void onLivingHurtReturn(LivingEntity victim, DamageSource source, boolean hurtSucceeded) {
        if (!hurtSucceeded || victim.level().isClientSide) {
            return;
        }
        ActiveHitContext ctx = ACTIVE_HIT.get();
        if (ctx == null || ctx.realTarget != victim || source.getEntity() != ctx.player) {
            return;
        }
        ctx.hitLanded = true;
        if (ctx.directFullHit) {
            int resTicks = ctx.fused ? 5 : 6; // Đảm bảo nhịp chém cắm 0.3s (6 ticks) của Song Nguyệt ăn sát thương 100% mỗi nhát!
            victim.invulnerableTime = 10 + resTicks;
            victim.hurtTime = Math.min(victim.hurtTime, resTicks);
        }
    }

    public static void finishPlayerAttack(@Nullable ActiveHitContext ctx) {
        if (ctx == null) {
            return;
        }
        ACTIVE_HIT.remove();
        if (!ctx.attackExecuted && !ctx.hitLanded) {
            return;
        }
        Player player = ctx.player;
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        long now = level.getGameTime();
        PlayerTogetsuState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerTogetsuState());
        st.lastSwingGameTime = now;
        if (!ctx.fused) {
            st.leftHandVariant = (st.leftHandVariant + 1) & 1;
        }
        if (!ctx.fused && (ctx.moveType == TogetsuMoveType.DUAL_SPRINT_THRUST || ctx.moveType == TogetsuMoveType.DUAL_AIR_SLASH)) {
            st.nextComboStep = 1;
        } else {
            st.nextComboStep = (ctx.comboStep + 1) % (ctx.fused ? TogetsuMoveType.FUSED_COMBO_COUNT : TogetsuMoveType.DUAL_COMBO_COUNT);
        }

        // Xóa buff Trăng Non sau khi đã xả vào đòn đánh
        if (ctx.waxingEmpowerApplied > 0.01F) {
            TogetsuItem.setWaxingEmpower(ctx.stack, false, 0.0F);
        }

        byte vfxKind = TogetsuSyncPayload.VFX_NONE;
        Vec3 vfxPos = (ctx.hitLanded && ctx.rawTarget != null)
                ? ctx.rawTarget.getBoundingBox().getCenter()
                : player.position().add(0, 1.0, 0);

        if (ctx.hitLanded && ctx.directFullHit && ctx.realTarget != null) {
            st.lastCombatGameTime = now; // Reset đồng hồ 5 giây giữ fusion_charge!

            if (!ctx.fused) {
                // Mỗi nhát chém trúng ở dạng tách chỉ cộng đúng +10 điểm nguyện lực!
                float prevGauge = TogetsuItem.getFusionGauge(ctx.stack);
                TogetsuItem.addFusionGauge(ctx.stack, SLASH_CHARGE_GAIN);
                level.playSound(null, vfxPos.x, vfxPos.y, vfxPos.z,
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.62F, 1.48F);
                if (prevGauge < 100.0F && TogetsuItem.getFusionGauge(ctx.stack) >= 100.0F) {
                    notifyFusionReady(level, player);
                }

                // Nội tại Chuột Trái (Kiếm Trái — Trăng Non): Chí Mạng trừ 1 HP (nửa tim) -> +30% sát thương đòn kế tiếp!
                if (ctx.critTriggered) {
                    level.sendParticles(ParticleTypes.CRIT, vfxPos.x, vfxPos.y, vfxPos.z, 10, 0.30, 0.30, 0.30, 0.16);
                    level.playSound(null, vfxPos.x, vfxPos.y, vfxPos.z,
                            ModSounds.DUAL_BLADE_CRIT, SoundSource.PLAYERS, 0.80F, 1.15F);

                    if (!player.getAbilities().instabuild && player.getHealth() > 1.5F) {
                        player.setHealth(Math.max(1.0F, player.getHealth() - 1.0F));
                    }
                    TogetsuItem.setWaxingEmpower(ctx.stack, true, WAXING_EMPOWER_BONUS);
                    vfxKind = TogetsuSyncPayload.VFX_WAXING_CRIT;
                    st.nextCritIsWaxing = false;
                }

                // Combo 5 nhịp Song Đao (1-4 chém luân phiên mỗi bên 1 nhát -> Nhát 5 ĐÒN ĐÁNH ĐẶC BIỆT!):
                // - Hit 4 (Chém ngược Tay Trái Trăng Non): gắn ấn Moon Mark sáng vòng tròn trên mục tiêu!
                // - Hit 5 (Đòn Đặc Biệt — Auto-Fuse Eclipse Morphing Finisher): hất tung mục tiêu + chém lan 3.5 block + rung màn hình + hit-stop 0.08s!
                if (ctx.moveType == TogetsuMoveType.DUAL_4) {
                    vfxKind = TogetsuSyncPayload.VFX_MOON_MARK;
                    level.playSound(null, vfxPos.x, vfxPos.y, vfxPos.z,
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.95F, 1.65F);
                } else if (ctx.moveType == TogetsuMoveType.DUAL_5) {
                    vfxKind = TogetsuSyncPayload.VFX_MORPH_FINISHER_IMPACT;
                    ctx.realTarget.push(0.0, 0.46, 0.0);
                    ctx.realTarget.hurtMarked = true;
                    triggerDualSpinCleave(level, player, ctx.realTarget, ctx.finalRawDamage * 0.72F, ctx.armorPierce, 3.50);
                    level.playSound(null, vfxPos.x, vfxPos.y, vfxPos.z,
                            ModSounds.DUAL_BLADE_CROSS_SLASH, SoundSource.PLAYERS, 1.18F, 0.72F);
                    ModNetworking.sendShakeNearby(level, vfxPos, 18.0, 0.56F, 5);
                }
            } else {
                // Dạng Song Nguyệt: giữ chuẩn tốc đánh 1.5 + Chém Lan (Sweeping Edge) diện rộng 4.5 block!
                if (ctx.critTriggered) {
                    level.playSound(null, vfxPos.x, vfxPos.y, vfxPos.z,
                            ModSounds.DUAL_BLADE_CRIT, SoundSource.PLAYERS, 1.0F, 0.88F);
                    vfxKind = TogetsuSyncPayload.VFX_WAXING_CRIT;
                }
                triggerGreatswordCleave(level, player, ctx.realTarget, ctx.finalRawDamage * 0.72F, ctx.armorPierce);
            }
        }

        if (!ctx.fused && ctx.directFullHit && player.getOffhandItem().isEmpty()) {
            applySafeDualComboLunge(level, player, st, ctx.moveType, now);
        }

        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                ctx.fused ? ModSounds.DUAL_BLADE_CROSS_SLASH : ModSounds.DUAL_BLADE_SLASH,
                SoundSource.PLAYERS, ctx.fused ? 1.0F : 0.82F, ctx.fused ? 0.78F : 1.12F);
        broadcastSync(level, player, ctx.moveType, ctx.fused, vfxKind, vfxPos, 1.0F);
    }

    /** Bước lướt combo an toàn + nhảy bật ở nhịp 5 giống 1:1 `DualBladeCombatHandler.applySafeComboLunge`! */
    private static void applySafeDualComboLunge(ServerLevel level, Player player, PlayerTogetsuState st,
                                                TogetsuMoveType moveType, long now) {
        WeaponsConfig cfg = WeaponsConfig.get();
        if (!cfg.enableComboLunge || now - st.lastLungeGameTime < 4L || player.isShiftKeyDown()) {
            return;
        }
        double base = (moveType == TogetsuMoveType.DUAL_SPRINT_THRUST) ? cfg.sprintLungeSpeed : cfg.comboLungeDistance;
        double mult = switch (moveType) {
            case DUAL_1 -> 0.95;
            case DUAL_2 -> 0.95;
            case DUAL_3 -> 1.05;
            case DUAL_4 -> 1.10;
            case DUAL_5 -> 1.25; // Nhảy bật uy lực cho Đòn Đánh Đặc Biệt nhát thứ 5!
            case DUAL_SPRINT_THRUST -> 1.22;
            case DUAL_AIR_SLASH -> 0.90;
            default -> 1.00;
        };
        double dist = Mth.clamp(base * mult, 0.0, 0.30);
        if (dist <= 0.01) {
            return;
        }

        float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
        double dx = -Math.sin(yawRad);
        double dz = Math.cos(yawRad);
        if (!DualBladeCombatHandler.isForwardStepSafe(level, player, dx, dz)) {
            return;
        }

        st.lastLungeGameTime = now;
        Vec3 cur = player.getDeltaMovement();
        double boostY = (moveType == TogetsuMoveType.DUAL_5 && player.onGround()) ? 0.24 : 0.0;
        player.setDeltaMovement(
                Mth.clamp(cur.x + dx * dist, -0.48, 0.48),
                Math.max(cur.y, boostY > 0.0 ? boostY : cur.y),
                Mth.clamp(cur.z + dz * dist, -0.48, 0.48)
        );
        player.hurtMarked = true;
    }

    /** Quét sát thương lan diện rộng cho Eclipse Hit 2 (360° Shadow Spin) & Hit 4 (Morphing Finisher)! */
    private static void triggerDualSpinCleave(ServerLevel level, Player player, LivingEntity primary,
                                              float cleaveDmg, float armorPierce, double radius) {
        AABB box = player.getBoundingBox().inflate(radius, 1.4, radius);
        List<Entity> cands = level.getEntities(player, box, e -> e.isAlive() && e.isPickable() && e != player);
        Set<UUID> seen = new HashSet<>();
        seen.add(primary.getUUID());
        int hits = 0;
        OVERRIDE_ARMOR_PIERCE.set(armorPierce);
        try {
            for (Entity cand : cands) {
                if (hits >= 5) break;
                LivingEntity real = DualBladeCombatHandler.resolveRealLivingTarget(cand);
                if (real == null || !real.isAlive() || real == player || real.isAlliedTo(player)) continue;
                if (!seen.add(real.getUUID())) continue;
                if (real.position().subtract(player.position()).horizontalDistanceSqr() > radius * radius) continue;
                cand.hurt(player.damageSources().playerAttack(player), cleaveDmg);
                if (radius > 3.0) {
                    real.push(0.0, 0.36, 0.0);
                    real.hurtMarked = true;
                }
                hits++;
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
        }
    }

    /** Chém lan diện rộng (Sweeping Edge) bán kính 4.5 block hình vòng cung cho mọi đòn đánh thường dạng Song Nguyệt! */
    private static void triggerGreatswordCleave(ServerLevel level, Player player, LivingEntity primary,
                                                float cleaveDmg, float armorPierce) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();

        AABB box = player.getBoundingBox().inflate(4.6, 1.6, 4.6);
        List<Entity> cands = level.getEntities(player, box, e -> e.isAlive() && e.isPickable() && e != player);
        Set<UUID> seen = new HashSet<>();
        seen.add(primary.getUUID());
        int hits = 0;
        OVERRIDE_ARMOR_PIERCE.set(armorPierce);
        try {
            for (Entity cand : cands) {
                if (hits >= 6) break;
                LivingEntity real = DualBladeCombatHandler.resolveRealLivingTarget(cand);
                if (real == null || !real.isAlive() || real == player || real.isAlliedTo(player)) continue;
                if (!seen.add(real.getUUID())) continue;
                Vec3 to = real.position().subtract(player.position());
                if (to.horizontalDistanceSqr() > 4.65 * 4.65) continue;
                if (to.normalize().dot(fwd) < -0.05) continue;
                cand.hurt(player.damageSources().playerAttack(player), cleaveDmg);
                hits++;
            }
        } finally {
            OVERRIDE_ARMOR_PIERCE.remove();
        }
    }

    private static void handleAirSlash(ServerPlayer player, int targetId) {
        ServerLevel level = player.serverLevel();
        ItemStack stack = player.getMainHandItem();
        boolean fused = TogetsuItem.isFused(stack);
        if (targetId >= 0) {
            if (player.getAttackStrengthScale(0.5F) < 0.90F) return;
            Entity target = level.getEntity(targetId);
            if (target == null || !target.isAlive() || target == player || !target.isAttackable()) return;
            double reach = player.entityInteractionRange() + 1.15;
            if (target.getBoundingBox().distanceToSqr(player.getEyePosition()) > reach * reach) return;
            player.attack(target);
            player.swing(InteractionHand.MAIN_HAND, true);
            return;
        }

        long now = level.getGameTime();
        PlayerTogetsuState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerTogetsuState());
        long minInterval = fused ? 6L : 3L; // Song Nguyệt: đúng 6 ticks = 0.30s; Song Đao: cho phép luân phiên LMB <-> RMB mượt!
        if (now - st.lastSwingGameTime < minInterval) {
            return;
        }
        if (now - st.lastSwingGameTime > COMBO_RESET_TICKS) {
            st.nextComboStep = 0;
        }
        float airStrength = player.getAttackStrengthScale(0.5F);
        int step = st.nextComboStep;
        // Chuột Trái ở dạng Song Đao = Kiếm Trái (Trăng Non — Lưỡi Trắng: DUAL_2 / DUAL_4, nhát thứ 5 = DUAL_5 Đặc Biệt!)
        TogetsuMoveType moveType = fused
                ? TogetsuMoveType.fusedByStep(step)
                : TogetsuMoveType.resolveDualHandMove(false, player.onGround(), player.isInWater(),
                        player.fallDistance, player.isSprinting(), step, st.leftHandVariant);
        st.lastSwingGameTime = now;
        if (!fused) {
            st.leftHandVariant = (st.leftHandVariant + 1) & 1;
        }
        if (!fused && (moveType == TogetsuMoveType.DUAL_SPRINT_THRUST || moveType == TogetsuMoveType.DUAL_AIR_SLASH)) {
            st.nextComboStep = 1;
        } else {
            st.nextComboStep = (step + 1) % (fused ? TogetsuMoveType.FUSED_COMBO_COUNT : TogetsuMoveType.DUAL_COMBO_COUNT);
        }

        player.resetAttackStrengthTicker();
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!fused && airStrength >= 0.85F && player.getOffhandItem().isEmpty()) {
            applySafeDualComboLunge(level, player, st, moveType, now);
        }
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                fused ? ModSounds.DUAL_BLADE_CROSS_SLASH : ModSounds.DUAL_BLADE_SLASH,
                SoundSource.PLAYERS, fused ? 0.95F : 0.78F, fused ? 0.76F : 1.12F);
        broadcastSync(level, player, moveType, fused, TogetsuSyncPayload.VFX_NONE, player.position().add(0, 1.0, 0), 1.0F);
    }

    /**
     * Chuột Phải ở dạng Song Đao — Chém trực tiếp bằng **Kiếm Phải (Trăng Khuyết — Lưỡi Đen)**:
     * - Luân phiên `DUAL_1` (ngang Phải->Trái) & `DUAL_3` (hất chéo Phải), nhát thứ 5 tổng hợp kích hoạt Đòn Đặc Biệt `DUAL_5`!
     * - Nội tại Trăng Khuyết khi Chí Mạng: hồi `+1.0 HP` (nửa tim), tạo vòng lặp cân bằng máu khi phối hợp Chuột Trái & Chuột Phải!
     */
    private static void handleDualRightHandSlash(ServerPlayer player, int targetId) {
        ItemStack stack = player.getMainHandItem();
        if (TogetsuItem.isFused(stack) || ACTIVE_PINS.containsKey(player.getUUID()) || ACTIVE_VORPALS.containsKey(player.getUUID())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        PlayerTogetsuState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerTogetsuState());
        if ((now - st.lastRightSwingGameTime) < 6L || (now - st.lastSwingGameTime) < 3L) {
            return;
        }
        if (now - st.lastSwingGameTime > COMBO_RESET_TICKS) {
            st.nextComboStep = 0;
        }

        int step = st.nextComboStep;
        TogetsuMoveType moveType = TogetsuMoveType.resolveDualHandMove(
                true, player.onGround(), player.isInWater(), player.fallDistance,
                player.isSprinting(), step, st.rightHandVariant);
        st.lastRightSwingGameTime = now;
        st.lastSwingGameTime = now;
        st.rightHandVariant = (st.rightHandVariant + 1) & 1;
        if (moveType == TogetsuMoveType.DUAL_SPRINT_THRUST || moveType == TogetsuMoveType.DUAL_AIR_SLASH) {
            st.nextComboStep = 1;
        } else {
            st.nextComboStep = (step + 1) % TogetsuMoveType.DUAL_COMBO_COUNT;
        }

        double reach = player.entityInteractionRange() + 1.05;
        Entity hitTarget = null;
        if (targetId >= 0) {
            Entity cand = level.getEntity(targetId);
            if (cand != null && cand.isAlive() && cand != player && cand.isAttackable()
                    && cand.getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach) {
                hitTarget = cand;
            }
        }
        if (hitTarget == null) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle().normalize();
            AABB box = player.getBoundingBox().inflate(reach, 1.5, reach);
            double bestScore = Double.MAX_VALUE;
            for (Entity cand : level.getEntities(player, box, e -> e.isAlive() && e.isPickable() && e.isAttackable() && e != player)) {
                LivingEntity real = DualBladeCombatHandler.resolveRealLivingTarget(cand);
                if (real == null || !real.isAlive() || real == player || real.isAlliedTo(player)) continue;
                Vec3 to = cand.getBoundingBox().getCenter().subtract(eye);
                double dist = to.length();
                if (dist > reach || dist < 0.05) continue;
                double dot = to.scale(1.0 / dist).dot(look);
                if (dot < 0.45) continue;
                double score = dist * (1.35 - dot);
                if (score < bestScore) {
                    bestScore = score;
                    hitTarget = cand;
                }
            }
        }

        byte vfxKind = TogetsuSyncPayload.VFX_NONE;
        Vec3 vfxPos = player.position().add(0, 1.0, 0);

        LivingEntity realTarget = hitTarget != null ? DualBladeCombatHandler.resolveRealLivingTarget(hitTarget) : null;
        if (hitTarget != null && realTarget != null && realTarget.isAlive()) {
            st.lastCombatGameTime = now;
            vfxPos = hitTarget.getBoundingBox().getCenter();
            DamageSource src = player.damageSources().playerAttack(player);
            float baseDmg = (float) Math.max(TogetsuItem.DUAL_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
            baseDmg = EnchantmentHelper.modifyDamage(level, stack, hitTarget, src, baseDmg);

            float empowerApplied = 0.0F;
            if (TogetsuItem.isWaxingEmpowered(stack)) {
                empowerApplied = TogetsuItem.getWaxingEmpowerBonus(stack);
                TogetsuItem.setWaxingEmpower(stack, false, 0.0F);
            }
            float gapRatio = computeUnderdogDamageBonus(player, realTarget);
            int critLv = ModEnchantments.getLevel(level, stack, ModEnchantments.CRITICAL_STRIKE);
            int pierceLv = ModEnchantments.getLevel(level, stack, ModEnchantments.ARMOR_PIERCE);
            boolean vanillaFallCrit = player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater();
            float critChance = Mth.clamp(0.40F + gapRatio * 0.20F + critLv * 0.08F, 0.0F, 0.95F);
            boolean crit = vanillaFallCrit || level.random.nextFloat() < critChance;
            float armorPierce = Mth.clamp(0.05F + pierceLv * 0.07F, 0.0F, 0.65F);

            float dmg = baseDmg;
            if (empowerApplied > 0.01F) dmg *= (1.0F + empowerApplied);
            if (moveType == TogetsuMoveType.DUAL_5) dmg *= 1.60F;
            if (crit) dmg *= 1.50F;

            realTarget.invulnerableTime = 0;
            OVERRIDE_ARMOR_PIERCE.set(armorPierce);
            boolean hurtOk;
            try {
                hurtOk = hitTarget.hurt(src, dmg);
            } finally {
                OVERRIDE_ARMOR_PIERCE.remove();
            }

            if (hurtOk) {
                int resTicks = 5;
                realTarget.invulnerableTime = 10 + resTicks;
                realTarget.hurtTime = Math.min(realTarget.hurtTime, resTicks);
                if (!player.getAbilities().instabuild) {
                    stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                }

                float prevGauge = TogetsuItem.getFusionGauge(stack);
                TogetsuItem.addFusionGauge(stack, SLASH_CHARGE_GAIN);
                level.playSound(null, vfxPos.x, vfxPos.y, vfxPos.z,
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.62F, 1.48F);
                if (prevGauge < 100.0F && TogetsuItem.getFusionGauge(stack) >= 100.0F) {
                    notifyFusionReady(level, player);
                }

                if (crit) {
                    level.sendParticles(ParticleTypes.CRIT, vfxPos.x, vfxPos.y, vfxPos.z, 10, 0.30, 0.30, 0.30, 0.16);
                    level.playSound(null, vfxPos.x, vfxPos.y, vfxPos.z,
                            ModSounds.DUAL_BLADE_CRIT, SoundSource.PLAYERS, 0.80F, 1.05F);
                    player.heal(1.0F);
                    vfxKind = TogetsuSyncPayload.VFX_WANING_CRIT;
                    st.nextCritIsWaxing = true;
                }

                if (moveType == TogetsuMoveType.DUAL_5) {
                    vfxKind = TogetsuSyncPayload.VFX_MORPH_FINISHER_IMPACT;
                    realTarget.push(0.0, 0.46, 0.0);
                    realTarget.hurtMarked = true;
                    triggerDualSpinCleave(level, player, realTarget, dmg * 0.72F, armorPierce, 3.50);
                    level.playSound(null, vfxPos.x, vfxPos.y, vfxPos.z,
                            ModSounds.DUAL_BLADE_CROSS_SLASH, SoundSource.PLAYERS, 1.18F, 0.72F);
                    ModNetworking.sendShakeNearby(level, vfxPos, 18.0, 0.56F, 5);
                }
            }
        }

        if (player.getOffhandItem().isEmpty()) {
            applySafeDualComboLunge(level, player, st, moveType, now);
        }
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                moveType == TogetsuMoveType.DUAL_5 ? ModSounds.DUAL_BLADE_CROSS_SLASH : ModSounds.DUAL_BLADE_SLASH,
                SoundSource.PLAYERS, 0.84F, 0.96F);
        broadcastSync(level, player, moveType, false, vfxKind, vfxPos, 1.0F);
    }

    private static void broadcastSync(ServerLevel level, Player player, TogetsuMoveType move,
                                      boolean fused, byte vfxKind, Vec3 pos, float powerScale) {
        float durTicks = move.isDualComboMove()
                ? computeDualComboDurationTicks(player.getAttributeValue(Attributes.ATTACK_SPEED), move)
                : move.baseDisplayTicks;
        TogetsuSyncPayload payload = new TogetsuSyncPayload(
                player.getId(),
                move.id,
                durTicks,
                fused,
                vfxKind,
                pos.x, pos.y, pos.z,
                player.getYRot(),
                player.getXRot(),
                powerScale
        );
        ModNetworking.sendTogetsuSyncNearby(level, player.position(), payload);
    }
}
