package com.abyssalrpg.combat;

import com.abyssalrpg.item.SeaTridentItem;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.network.SeaWeaponsActionC2SPayload;
import com.abyssalrpg.network.SeaWeaponsSyncPayload;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trung tâm chiến đấu Server-authoritative cho **Hải Thần Đinh Ba & Đèn Lồng Biển (`abyssalrpg:sea_trident`)**:
 * 1. **Combo 3 Nhịp Đinh Ba (LMB):**
 *    - `THRUST_1`: Đâm thẳng tốc độ cao tầm 4.25 block (`1.00×` sát thương).
 *    - `SWEEP_2`: Quét ngang sóng lam diện rộng (`1.12×` sát thương + đánh lan 3.8 block).
 *    - `TRIDENT_SLAM_3`: Xoay giáng đinh ba tạo sóng nước đập AoE 4.4 block (`1.30×` sát thương + hit-stop).
 * 2. **Đèn Lồng Tay Trái — Triệu Hồi Sóng Thần (Tsunami, RMB, hồi 7.0s):**
 *    - Đèn Lồng bùng sáng triệu hồi **Sóng Thần 3D khổng lồ** rộng `9.6` block × cao `5.8` block lao đi `22` block trong `28` tick,
 *      gây `1.75×` sát thương, hất tung & cuốn phăng mọi kẻ địch, dập lửa và gây `Slowness II (5s) + Weakness I (4s)`!
 * 3. **Đinh Ba Tay Phải — Ngũ Thần Kích (Giơ Đinh Ba Lên Trời Phóng 5 Đinh Ba, `[R]` / `Shift+RMB`, hồi 7.0s):**
 *    - Người chơi giơ Đinh Ba thẳng lên trời, triệu hồi **5 Đinh Ba Năng Lượng 3D** xếp vòng cung trên không,
 *      lần lượt phóng liên hoàn (đối xứng ngoài -> trong -> mũi trung tâm kết liễu) lao thẳng khóa mục tiêu tới `26` block!
 *    - 4 mũi đầu gây `0.62×` dame/mũi + đánh lan nước, mũi thứ 5 trung tâm gây `1.15×` dame + kích nổ cột nước & để lại vùng Ấn Hải Thần 4s!
 * 4. **Cộng Hưởng Hải Thần (Tsunami × 5-Trident Cataclysm):**
 *    - Khi **Sóng Thần** quét qua vùng vừa bị **5 Đinh Ba** đánh trúng (hoặc ngược lại) -> kích nổ **Đại Hồng Thủy Hải Thần**
 *      tăng `+60%` sát thương nổ tung bán kính `7.5` block + rung màn hình cực mạnh!
 */
public final class SeaWeaponsCombatHandler {
    public static final int TIDAL_WAVE_COOLDOWN_TICKS = 140; // 7.0s
    public static final int WHIRLPOOL_COOLDOWN_TICKS = 140;  // 7.0s
    public static final float TIDAL_WAVE_RANGE = 22.0F;
    public static final float TIDAL_WAVE_WIDTH = 9.6F;
    public static final int TIDAL_WAVE_DURATION_TICKS = 28;
    public static final float VOLLEY_MAX_RANGE = 26.0F;
    public static final int VOLLEY_COUNT = 5;
    public static final int[] VOLLEY_LAUNCH_TICKS = {8, 10, 13, 15, 18};
    private static final int COMBO_RESET_TICKS = 44;

    private static final DustParticleOptions OCEAN_FOAM =
            new DustParticleOptions(new Vector3f(0.84F, 0.98F, 1.00F), 1.65F);
    private static final DustParticleOptions OCEAN_CYAN =
            new DustParticleOptions(new Vector3f(0.22F, 0.72F, 1.00F), 1.75F);

    private static final Map<UUID, PlayerSeaState> PLAYER_STATES = new HashMap<>();
    private static final List<ActiveTidalWave> ACTIVE_WAVES = new ArrayList<>();
    private static final List<ActiveTridentVolley> ACTIVE_VOLLEYS = new ArrayList<>();
    private static final List<AbyssalMarkZone> MARK_ZONES = new ArrayList<>();
    private static final ThreadLocal<ActiveHitContext> ACTIVE_HIT = new ThreadLocal<>();

    private SeaWeaponsCombatHandler() {}

    private static final class PlayerSeaState {
        int nextComboStep = 0;
        long lastSwingTick = -1000L;
    }

    public static final class ActiveHitContext {
        final ServerPlayer player;
        final ServerLevel level;
        final SeaWeaponsMoveType move;
        final float baseDamage;
        final Entity target;

        ActiveHitContext(ServerPlayer player, ServerLevel level, SeaWeaponsMoveType move, float baseDamage, Entity target) {
            this.player = player;
            this.level = level;
            this.move = move;
            this.baseDamage = baseDamage;
            this.target = target;
        }
    }

    private static final class ActiveTidalWave {
        final UUID ownerId;
        final int ownerEntityId;
        final ServerLevel level;
        final Vec3 origin;
        final Vec3 forward; // horizontal unit vector
        final Vec3 right;
        final float yawDeg;
        final float damage;
        final Set<Integer> hitIds = new HashSet<>();
        int tick = 0;
        boolean boostedWhirlpool = false;

        ActiveTidalWave(ServerPlayer owner, ServerLevel level, Vec3 origin, Vec3 forward, float yawDeg, float damage) {
            this.ownerId = owner.getUUID();
            this.ownerEntityId = owner.getId();
            this.level = level;
            this.origin = origin;
            this.forward = forward;
            this.right = new Vec3(-forward.z, 0.0, forward.x);
            this.yawDeg = yawDeg;
            this.damage = damage;
        }
    }

    private static final class ActiveTridentVolley {
        final UUID ownerId;
        final int ownerEntityId;
        final ServerLevel level;
        final int targetEntityId; // -1 if fixed world point
        Vec3 targetPos;
        final float baseDamage;
        final int flightTicks;
        final boolean[] launched = new boolean[VOLLEY_COUNT];
        final boolean[] impacted = new boolean[VOLLEY_COUNT];
        int tick = 0;

        ActiveTridentVolley(ServerPlayer owner, ServerLevel level, int targetEntityId, Vec3 targetPos,
                            float baseDamage, int flightTicks) {
            this.ownerId = owner.getUUID();
            this.ownerEntityId = owner.getId();
            this.level = level;
            this.targetEntityId = targetEntityId;
            this.targetPos = targetPos;
            this.baseDamage = baseDamage;
            this.flightTicks = flightTicks;
        }
    }

    private static final class AbyssalMarkZone {
        final UUID ownerId;
        final int ownerEntityId;
        final ServerLevel level;
        final Vec3 center;
        final float bonusDamage;
        int ticksLeft;
        boolean triggered = false;

        AbyssalMarkZone(UUID ownerId, int ownerEntityId, ServerLevel level, Vec3 center, float bonusDamage, int ticksLeft) {
            this.ownerId = ownerId;
            this.ownerEntityId = ownerEntityId;
            this.level = level;
            this.center = center;
            this.bonusDamage = bonusDamage;
            this.ticksLeft = ticksLeft;
        }
    }

    private static float resolveBaseDamage(ServerPlayer player) {
        double attr = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return (float) Math.max(SeaTridentItem.ATTACK_DAMAGE, attr);
    }

    @Nullable
    public static ActiveHitContext beginPlayerAttack(Player player, Entity target) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer sp)
                || !(player.level() instanceof ServerLevel sl)) {
            return null;
        }
        ItemStack stack = sp.getMainHandItem();
        if (!stack.is(ModItems.SEA_TRIDENT)) {
            return null;
        }
        PlayerSeaState st = PLAYER_STATES.computeIfAbsent(sp.getUUID(), k -> new PlayerSeaState());
        long now = sl.getGameTime();
        if (now - st.lastSwingTick > COMBO_RESET_TICKS) {
            st.nextComboStep = 0;
        }
        st.lastSwingTick = now;

        SeaWeaponsMoveType move = SeaWeaponsMoveType.comboByStep(st.nextComboStep);
        st.nextComboStep = (st.nextComboStep + 1) % 3;

        float baseDmg = resolveBaseDamage(sp);
        broadcastSlash(sp, sl, move);
        ActiveHitContext ctx = new ActiveHitContext(sp, sl, move, baseDmg, target);
        ACTIVE_HIT.set(ctx);
        return ctx;
    }

    public static float modifyAttackDamage(float amount) {
        ActiveHitContext ctx = ACTIVE_HIT.get();
        if (ctx == null) {
            return amount;
        }
        float mult = switch (ctx.move) {
            case THRUST_1 -> 1.00F;
            case SWEEP_2 -> 1.12F;
            case TRIDENT_SLAM_3 -> 1.30F;
            default -> 1.00F;
        };
        return amount * mult;
    }

    public static void finishPlayerAttack(ActiveHitContext ctx) {
        ACTIVE_HIT.remove();
        if (ctx == null || !ctx.player.isAlive()) {
            return;
        }
        ServerPlayer sp = ctx.player;
        ServerLevel sl = ctx.level;
        Vec3 look = sp.getLookAngle();
        Vec3 horiz = new Vec3(look.x, 0.0, look.z);
        Vec3 dir = horiz.lengthSqr() > 1e-4 ? horiz.normalize() : Vec3.directionFromRotation(0.0F, sp.getYRot());

        if (ctx.move == SeaWeaponsMoveType.SWEEP_2) {
            // Quét sóng lan diện rộng 3.8 block trước mặt!
            Vec3 sweepCenter = sp.position().add(dir.scale(2.1)).add(0.0, 1.0, 0.0);
            AABB box = AABB.ofSize(sweepCenter, 4.6, 2.6, 4.6);
            float cleaveDmg = ctx.baseDamage * 0.72F;
            for (LivingEntity mob : sl.getEntitiesOfClass(LivingEntity.class, box, e -> isValidTarget(sp, e))) {
                if (mob == ctx.target) continue;
                mob.hurt(sl.damageSources().playerAttack(sp), cleaveDmg);
                mob.push(dir.x * 0.36, 0.14, dir.z * 0.36);
            }
            sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.85F, 1.25F);
        } else if (ctx.move == SeaWeaponsMoveType.TRIDENT_SLAM_3) {
            // Đòn giáng kết liễu tạo sóng nước AoE 4.4 block + hit-stop nhẹ!
            Vec3 impact = (ctx.target != null && ctx.target.isAlive())
                    ? ctx.target.position().add(0.0, 0.35, 0.0)
                    : sp.position().add(dir.scale(2.6)).add(0.0, 0.35, 0.0);
            AABB box = AABB.ofSize(impact, 5.4, 3.2, 5.4);
            float splashDmg = ctx.baseDamage * 0.85F;
            for (LivingEntity mob : sl.getEntitiesOfClass(LivingEntity.class, box, e -> isValidTarget(sp, e))) {
                if (mob != ctx.target) {
                    mob.hurt(sl.damageSources().playerAttack(sp), splashDmg);
                }
                mob.push(0.0, 0.32, 0.0);
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            }
            sl.sendParticles(ParticleTypes.SPLASH, impact.x, impact.y + 0.4, impact.z, 24, 1.4, 0.5, 1.4, 0.18);
            sl.playSound(null, impact.x, impact.y, impact.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.65F, 1.45F);
            ModNetworking.sendShakeNearby(sl, impact, 10.0, 0.45F, 5);
            ModNetworking.sendSeaWeaponsSyncNearby(sl, impact, new SeaWeaponsSyncPayload(
                    SeaWeaponsSyncPayload.EVENT_HIT_STOP, sp.getId(), ctx.move.id, 1.5F,
                    impact.x, impact.y, impact.z, sp.getYRot(), sp.getXRot(), 0.0F, 0.0F));
        }
    }

    public static void handleActionPacket(ServerPlayer player, SeaWeaponsActionC2SPayload payload) {
        if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel sl)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.SEA_TRIDENT)) {
            return;
        }
        switch (payload.actionType()) {
            case SeaWeaponsActionC2SPayload.ACTION_AIR_SLASH -> {
                PlayerSeaState st = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerSeaState());
                long now = sl.getGameTime();
                if (now - st.lastSwingTick < 6L) {
                    return;
                }
                if (now - st.lastSwingTick > COMBO_RESET_TICKS) {
                    st.nextComboStep = 0;
                }
                st.lastSwingTick = now;
                SeaWeaponsMoveType move = SeaWeaponsMoveType.comboByStep(st.nextComboStep);
                st.nextComboStep = (st.nextComboStep + 1) % 3;
                broadcastSlash(player, sl, move);
            }
            case SeaWeaponsActionC2SPayload.ACTION_CAST_TIDAL_WAVE -> tryCastTidalWave(player, sl, stack);
            case SeaWeaponsActionC2SPayload.ACTION_CAST_WHIRLPOOL -> tryCastWhirlpool(player, sl, stack);
            default -> {}
        }
    }

    /** Đèn Lồng Tay Trái — Triệu Hồi Sóng Thần Khổng Lồ (Tsunami, RMB)! */
    public static boolean tryCastTidalWave(ServerPlayer player, ServerLevel sl, ItemStack stack) {
        if (!player.getOffhandItem().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.abyssalrpg.sea_trident.need_offhand")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        long now = sl.getGameTime();
        long cdUntil = SeaTridentItem.getWaveCooldownUntil(stack);
        if (!player.getAbilities().instabuild && now < cdUntil) {
            float sec = (cdUntil - now) / 20.0F;
            player.displayClientMessage(Component.translatable("message.abyssalrpg.sea_trident.wave_cd",
                    String.format(java.util.Locale.ROOT, "%.1f", sec)).withStyle(ChatFormatting.AQUA), true);
            return false;
        }
        if (!player.getAbilities().instabuild) {
            SeaTridentItem.setWaveCooldownUntil(stack, now + TIDAL_WAVE_COOLDOWN_TICKS);
            stack.hurtAndBreak(5, player, LivingEntity.getSlotForHand(InteractionHand.MAIN_HAND));
        }

        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        Vec3 forward = flat.lengthSqr() > 1e-4 ? flat.normalize() : Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 origin = player.position().add(forward.scale(1.4));
        float waveDamage = resolveBaseDamage(player) * 1.75F;

        ACTIVE_WAVES.add(new ActiveTidalWave(player, sl, origin, forward, player.getYRot(), waveDamage));
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1.55F, 0.62F);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.45F, 0.58F);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.15F, 0.72F);
        ModNetworking.sendShakeNearby(sl, origin, 20.0, 0.95F, 12);
        ModNetworking.sendSeaWeaponsSyncNearby(sl, origin, new SeaWeaponsSyncPayload(
                SeaWeaponsSyncPayload.EVENT_TIDAL_WAVE, player.getId(), SeaWeaponsMoveType.LANTERN_TIDAL_WAVE.id,
                TIDAL_WAVE_DURATION_TICKS, origin.x, origin.y, origin.z,
                player.getYRot(), 0.0F, TIDAL_WAVE_RANGE, TIDAL_WAVE_WIDTH));
        player.displayClientMessage(Component.translatable("message.abyssalrpg.sea_trident.cast_wave")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
        return true;
    }

    /** Đinh Ba Tay Phải — Giơ Đinh Ba Lên Trời triệu hồi 5 Đinh Ba 3D phóng liên hoàn vào mục tiêu (`[R]` / `Shift+RMB`)! */
    public static boolean tryCastWhirlpool(ServerPlayer player, ServerLevel sl, ItemStack stack) {
        long now = sl.getGameTime();
        long cdUntil = SeaTridentItem.getWhirlpoolCooldownUntil(stack);
        if (!player.getAbilities().instabuild && now < cdUntil) {
            float sec = (cdUntil - now) / 20.0F;
            player.displayClientMessage(Component.translatable("message.abyssalrpg.sea_trident.whirlpool_cd",
                    String.format(java.util.Locale.ROOT, "%.1f", sec)).withStyle(ChatFormatting.BLUE), true);
            return false;
        }
        if (!player.getAbilities().instabuild) {
            SeaTridentItem.setWhirlpoolCooldownUntil(stack, now + WHIRLPOOL_COOLDOWN_TICKS);
            stack.hurtAndBreak(5, player, LivingEntity.getSlotForHand(InteractionHand.MAIN_HAND));
        }

        VolleyTarget target = findVolleyTarget(player, sl, VOLLEY_MAX_RANGE);
        double dist = player.position().distanceTo(target.pos);
        int flightTicks = Mth.clamp((int) Math.round(5.0 + dist * 0.24), 6, 11);
        float base = resolveBaseDamage(player);
        ACTIVE_VOLLEYS.add(new ActiveTridentVolley(player, sl, target.entityId, target.pos, base, flightTicks));

        // Âm thanh giơ đinh ba lên trời triệu hồi 5 mũi thương thần!
        sl.playSound(null, player.getX(), player.getY() + 1.8, player.getZ(),
                SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.45F, 1.12F);
        sl.playSound(null, player.getX(), player.getY() + 1.8, player.getZ(),
                SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.35F, 0.85F);
        sl.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 2.4, player.getZ(), 26, 1.4, 0.5, 1.4, 0.16);
        ModNetworking.sendShakeNearby(sl, player.position(), 16.0, 0.55F, 8);
        ModNetworking.sendSeaWeaponsSyncNearby(sl, player.position(), new SeaWeaponsSyncPayload(
                SeaWeaponsSyncPayload.EVENT_TRIDENT_VOLLEY, player.getId(), SeaWeaponsMoveType.TRIDENT_WHIRLPOOL.id,
                (float) flightTicks, target.pos.x, target.pos.y, target.pos.z,
                player.getYRot(), player.getXRot(), (float) target.entityId, (float) VOLLEY_COUNT));
        player.displayClientMessage(Component.translatable("message.abyssalrpg.sea_trident.cast_whirlpool")
                .withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD), true);
        return true;
    }

    private record VolleyTarget(int entityId, Vec3 pos) {}

    private static VolleyTarget findVolleyTarget(ServerPlayer player, ServerLevel sl, double maxDist) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(maxDist));

        // Ưu tiên khóa mục tiêu sống trong nón tầm nhìn tới 26 block (chọn mục tiêu sát tâm ngắm nhất!)
        AABB search = player.getBoundingBox().expandTowards(look.scale(maxDist)).inflate(2.4);
        LivingEntity bestMob = null;
        double bestScore = -1.0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, search, m -> isValidTarget(player, m))) {
            Vec3 center = e.position().add(0.0, e.getBbHeight() * 0.55, 0.0);
            Vec3 toMob = center.subtract(eye);
            double d = toMob.length();
            if (d > maxDist || d < 0.2) continue;
            double dot = toMob.scale(1.0 / d).dot(look);
            if (dot > 0.84) {
                double score = dot - d * 0.004;
                if (score > bestScore) {
                    bestScore = score;
                    bestMob = e;
                }
            }
        }
        if (bestMob != null) {
            return new VolleyTarget(bestMob.getId(), bestMob.position().add(0.0, bestMob.getBbHeight() * 0.55, 0.0));
        }

        BlockHitResult bhr = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        Vec3 spot = (bhr.getType() != HitResult.Type.MISS) ? bhr.getLocation() : eye.add(look.scale(16.0));
        BlockPos bp = BlockPos.containing(spot);
        for (int dy = 0; dy <= 4; dy++) {
            BlockPos check = bp.below(dy);
            if (!sl.getBlockState(check).isAir() || !sl.getFluidState(check).isEmpty()) {
                return new VolleyTarget(-1, new Vec3(spot.x, check.getY() + 1.0, spot.z));
            }
        }
        return new VolleyTarget(-1, spot);
    }

    private static void broadcastSlash(ServerPlayer player, ServerLevel sl, SeaWeaponsMoveType move) {
        Vec3 anchor = player.position().add(0.0, player.getBbHeight() * 0.62, 0.0);
        ModNetworking.sendSeaWeaponsSyncNearby(sl, anchor, new SeaWeaponsSyncPayload(
                SeaWeaponsSyncPayload.EVENT_SLASH, player.getId(), move.id, move.baseDisplayTicks,
                anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot(), 0.0F, 0.0F));
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.78F,
                move == SeaWeaponsMoveType.TRIDENT_SLAM_3 ? 0.85F : 1.18F);
    }

    public static void tickServer(MinecraftServer server) {
        // 1. Nội tại Đèn Lồng Biển: khi cầm Đinh Ba (ô tay phụ trống -> Đèn Lồng cháy sáng ở tay trái),
        //    ban Thở Dưới Nước + tăng tốc bơi & nhìn xuyên nước khi lặn!
        if ((server.getTickCount() % 15) == 0) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if (sp.isAlive() && sp.getMainHandItem().is(ModItems.SEA_TRIDENT) && sp.getOffhandItem().isEmpty()) {
                    sp.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false, false));
                    if (sp.isInWaterOrRain()) {
                        sp.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, false, false));
                        sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false, false));
                    }
                }
            }
        }

        // 2. Cập nhật các Sóng Thần Khổng Lồ đang lao đi (Lantern Tsunami — 9.6w × 5.8h × 22r)
        Iterator<ActiveTidalWave> waveIt = ACTIVE_WAVES.iterator();
        while (waveIt.hasNext()) {
            ActiveTidalWave w = waveIt.next();
            w.tick++;
            if (w.tick > TIDAL_WAVE_DURATION_TICKS) {
                waveIt.remove();
                continue;
            }
            float progress = w.tick / (float) TIDAL_WAVE_DURATION_TICKS;
            double dist = progress * TIDAL_WAVE_RANGE;
            Vec3 crestPos = w.origin.add(w.forward.scale(dist));
            ServerPlayer owner = w.level.getServer().getPlayerList().getPlayer(w.ownerId);

            // Kiểm tra Cộng Hưởng Hải Thần (Sóng Thần quét qua vùng vừa bị 5 Đinh Ba đánh dấu!)
            if (!w.boostedWhirlpool) {
                for (AbyssalMarkZone zone : MARK_ZONES) {
                    if (zone.level == w.level && !zone.triggered
                            && zone.center.distanceToSqr(crestPos) <= 7.5 * 7.5) {
                        zone.triggered = true;
                        w.boostedWhirlpool = true;
                        triggerAbyssalCataclysm(zone, owner);
                        break;
                    }
                }
            }

            // Quét sát thương & cuốn phăng hất tung kẻ địch theo tường Sóng Thần rộng 9.6 block × cao 6.0 block!
            AABB box = AABB.ofSize(crestPos.add(0.0, 2.6, 0.0), TIDAL_WAVE_WIDTH + 1.4, 6.2, TIDAL_WAVE_WIDTH + 1.4);
            for (LivingEntity mob : w.level.getEntitiesOfClass(LivingEntity.class, box, e -> isValidTarget(owner, e))) {
                if (w.hitIds.contains(mob.getId())) continue;
                Vec3 rel = mob.position().subtract(crestPos);
                double along = rel.dot(w.forward);
                double lateral = Math.abs(rel.dot(w.right));
                if (Math.abs(along) <= 3.1 && lateral <= (TIDAL_WAVE_WIDTH * 0.52)) {
                    w.hitIds.add(mob.getId());
                    if (owner != null) {
                        mob.hurt(w.level.damageSources().playerAttack(owner), w.damage);
                    } else {
                        mob.hurt(w.level.damageSources().magic(), w.damage);
                    }
                    mob.clearFire();
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                    mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                    mob.setDeltaMovement(w.forward.x * 1.45, 0.58, w.forward.z * 1.45);
                    mob.hurtMarked = true;
                }
            }

            // Bọt sóng thần cuồn cuộn bắn tung dọc đỉnh & chân sóng!
            if ((w.tick & 1) == 0) {
                for (int s = -3; s <= 3; s++) {
                    Vec3 p = crestPos.add(w.right.scale(s * 1.42));
                    w.level.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 1.8, p.z, 4, 0.45, 0.9, 0.45, 0.16);
                    w.level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 4.4 - Math.abs(s) * 0.35, p.z, 1, 0.25, 0.20, 0.25, 0.03);
                    w.level.sendParticles((Math.abs(s) <= 1) ? OCEAN_FOAM : OCEAN_CYAN, p.x, p.y + 3.4, p.z, 1, 0.25, 0.35, 0.25, 0.02);
                }
            }
            if ((w.tick % 7) == 0) {
                w.level.playSound(null, crestPos.x, crestPos.y + 2.0, crestPos.z,
                        SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.25F, 0.68F);
            }
        }

        // 3. Cập nhật 5 Đinh Ba 3D phóng liên hoàn vào mục tiêu (Trident Sky Volley)
        Iterator<ActiveTridentVolley> vIt = ACTIVE_VOLLEYS.iterator();
        while (vIt.hasNext()) {
            ActiveTridentVolley v = vIt.next();
            v.tick++;
            ServerPlayer owner = v.level.getServer().getPlayerList().getPlayer(v.ownerId);

            // Live-track mục tiêu di chuyển cho đến khi mũi cuối chạm đích!
            if (v.targetEntityId >= 0) {
                Entity te = v.level.getEntity(v.targetEntityId);
                if (te instanceof LivingEntity living && living.isAlive()) {
                    v.targetPos = living.position().add(0.0, living.getBbHeight() * 0.55, 0.0);
                }
            }

            boolean allDone = true;
            for (int i = 0; i < VOLLEY_COUNT; i++) {
                int launchTick = VOLLEY_LAUNCH_TICKS[i];
                int impactTick = launchTick + v.flightTicks;
                if (!v.launched[i] && v.tick >= launchTick) {
                    v.launched[i] = true;
                    Vec3 src = (owner != null) ? owner.position().add(0.0, 2.8, 0.0) : v.targetPos;
                    float pitch = (i == VOLLEY_COUNT - 1) ? 0.82F : (1.06F + i * 0.08F);
                    v.level.playSound(null, src.x, src.y, src.z,
                            SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.15F, pitch);
                }
                if (!v.impacted[i] && v.tick >= impactTick) {
                    v.impacted[i] = true;
                    boolean finisher = (i == VOLLEY_COUNT - 1);
                    float dmg = finisher ? (v.baseDamage * 1.15F) : (v.baseDamage * 0.62F);
                    double rad = finisher ? 3.8 : 2.4;
                    AABB hitBox = AABB.ofSize(v.targetPos, rad * 2.0, rad * 1.8, rad * 2.0);
                    for (LivingEntity mob : v.level.getEntitiesOfClass(LivingEntity.class, hitBox, e -> isValidTarget(owner, e))) {
                        // Xóa i-frame ngắn để cả 5 mũi Đinh Ba liên hoàn đều gây sát thương trọn vẹn!
                        mob.invulnerableTime = 0;
                        if (owner != null) {
                            mob.hurt(v.level.damageSources().playerAttack(owner), dmg);
                        } else {
                            mob.hurt(v.level.damageSources().magic(), dmg);
                        }
                        mob.clearFire();
                        if (finisher) {
                            mob.setDeltaMovement(mob.getDeltaMovement().x * 0.35, 0.62, mob.getDeltaMovement().z * 0.35);
                            mob.hurtMarked = true;
                            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                        }
                    }
                    v.level.sendParticles(ParticleTypes.SPLASH, v.targetPos.x, v.targetPos.y, v.targetPos.z,
                            finisher ? 40 : 16, finisher ? 1.6 : 0.7, finisher ? 1.2 : 0.6, finisher ? 1.6 : 0.7, 0.22);
                    v.level.sendParticles(finisher ? OCEAN_FOAM : OCEAN_CYAN, v.targetPos.x, v.targetPos.y + 0.3, v.targetPos.z,
                            finisher ? 12 : 4, 0.6, 0.5, 0.6, 0.04);
                    v.level.playSound(null, v.targetPos.x, v.targetPos.y, v.targetPos.z,
                            finisher ? SoundEvents.TRIDENT_THUNDER.value() : SoundEvents.TRIDENT_HIT,
                            SoundSource.PLAYERS, finisher ? 1.35F : 1.05F, finisher ? 1.18F : (1.05F + i * 0.06F));
                    if (finisher) {
                        ModNetworking.sendShakeNearby(v.level, v.targetPos, 18.0, 0.82F, 9);
                        ModNetworking.sendSeaWeaponsSyncNearby(v.level, v.targetPos, new SeaWeaponsSyncPayload(
                                SeaWeaponsSyncPayload.EVENT_MAELSTROM_BURST, v.ownerEntityId, SeaWeaponsMoveType.TRIDENT_WHIRLPOOL.id,
                                18.0F, v.targetPos.x, v.targetPos.y - 0.5, v.targetPos.z, 0.0F, 0.0F, 5.4F, 0.0F));
                        // Đặt vùng Ấn Hải Thần 4s để cộng hưởng nổ tung khi Sóng Thần quét qua!
                        MARK_ZONES.add(new AbyssalMarkZone(v.ownerId, v.ownerEntityId, v.level, v.targetPos, v.baseDamage * 1.35F, 80));
                    }
                }
                if (!v.impacted[i]) {
                    allDone = false;
                }
            }
            if (allDone) {
                vIt.remove();
            }
        }

        // 4. Dọn vùng Ấn Hải Thần chờ cộng hưởng Sóng Thần
        Iterator<AbyssalMarkZone> mzIt = MARK_ZONES.iterator();
        while (mzIt.hasNext()) {
            AbyssalMarkZone mz = mzIt.next();
            mz.ticksLeft--;
            if (mz.triggered || mz.ticksLeft <= 0) {
                mzIt.remove();
            }
        }
    }

    private static void triggerAbyssalCataclysm(AbyssalMarkZone zone, @Nullable ServerPlayer owner) {
        AABB box = AABB.ofSize(zone.center.add(0.0, 2.0, 0.0), 15.0, 8.5, 15.0);
        for (LivingEntity mob : zone.level.getEntitiesOfClass(LivingEntity.class, box, e -> isValidTarget(owner, e))) {
            mob.invulnerableTime = 0;
            if (owner != null) {
                mob.hurt(zone.level.damageSources().playerAttack(owner), zone.bonusDamage);
            } else {
                mob.hurt(zone.level.damageSources().magic(), zone.bonusDamage);
            }
            mob.setDeltaMovement(mob.getDeltaMovement().x * 0.4, 1.05, mob.getDeltaMovement().z * 0.4);
            mob.hurtMarked = true;
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
        }
        zone.level.playSound(null, zone.center.x, zone.center.y, zone.center.z,
                SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.50F, 0.92F);
        ModNetworking.sendShakeNearby(zone.level, zone.center, 24.0, 1.25F, 14);
        ModNetworking.sendSeaWeaponsSyncNearby(zone.level, zone.center, new SeaWeaponsSyncPayload(
                SeaWeaponsSyncPayload.EVENT_MAELSTROM_BURST, zone.ownerEntityId, SeaWeaponsMoveType.LANTERN_TIDAL_WAVE.id,
                24.0F, zone.center.x, zone.center.y - 0.5, zone.center.z, 0.0F, 0.0F, 8.5F, 1.0F));
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("message.abyssalrpg.sea_trident.maelstrom_synergy")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }
    }

    private static boolean isValidTarget(@Nullable ServerPlayer owner, LivingEntity e) {
        if (!e.isAlive() || !e.isAttackable() || e.isSpectator()) {
            return false;
        }
        if (owner != null && (e == owner || owner.isAlliedTo(e))) {
            return false;
        }
        return true;
    }
}
