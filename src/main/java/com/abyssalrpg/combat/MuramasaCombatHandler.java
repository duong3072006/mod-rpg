package com.abyssalrpg.combat;

import com.abyssalrpg.item.MuramasaItem;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.network.MuramasaActionC2SPayload;
import com.abyssalrpg.network.MuramasaSyncPayload;
import com.abyssalrpg.registry.ModItems;
import com.abyssalrpg.registry.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trung tâm chiến đấu Server-authoritative cho **Yêu Đao Muramasa Katana (`abyssalrpg:muramasa`)**:
 * - **Combo 3 Nhịp Katana:** Kesagiri (1.00×) -> Gyaku-Kesa (1.12×) -> Demon Cross-Cleave (1.40× + Hút máu 1.5 HP).
 * - **RMB — Nhất Đao Huyết Nguyện (Iaijutsu Quick-Draw, hồi 5.5s):** 5 tick gồng rút kiếm -> lướt chém 9 block
 *   (miễn sát thương khi lướt) quét sóng lưỡi vàng-đỏ 14 block gây 2.2× sát thương, xuyên 35% giáp & hồi 2 HP/địch!
 * - **[R] / Shift+RMB — Huyết Thệ Yêu Đao (12s, hồi 28s):** Hiến tế 4 HP (không gây tử vong) giải phóng yêu quỷ
 *   Muramasa: +35% sát thương, Speed II, 15% hút máu mọi đòn chém!
 */
public final class MuramasaCombatHandler {
    public static final int IAI_COOLDOWN_TICKS = 110;       // 5.5s
    public static final int IAI_FLASH_BLIND_TICKS = 200;    // 10.0s white-screen flashbang & blindness!
    public static final int BLOOD_OATH_DURATION_TICKS = 240; // 12.0s
    public static final int BLOOD_OATH_COOLDOWN_TICKS = 560; // 28.0s
    public static final int IAI_DRAW_FIRE_TICK = 5;         // 0.25s crouch-coil -> explosive draw dash!
    public static final int IAI_TOTAL_TICKS = 16;
    private static final int COMBO_RESET_TICKS = 42;

    private static final DustParticleOptions MURAMASA_YELLOW =
            new DustParticleOptions(new Vector3f(1.00F, 0.94F, 0.18F), 1.35F);
    private static final DustParticleOptions MURAMASA_CRIMSON =
            new DustParticleOptions(new Vector3f(0.86F, 0.08F, 0.14F), 1.45F);
    private static final DustParticleOptions MURAMASA_DARK =
            new DustParticleOptions(new Vector3f(0.10F, 0.06F, 0.10F), 1.40F);

    private static final Map<UUID, PlayerComboState> COMBO_STATES = new HashMap<>();
    private static final Map<UUID, ActiveIaijutsu> ACTIVE_IAI = new HashMap<>();
    private static final Map<UUID, Long> DASH_IFRAME_UNTIL = new HashMap<>();
    private static final ThreadLocal<ActiveHitContext> ACTIVE_HIT = new ThreadLocal<>();
    private static final ThreadLocal<Float> ACTIVE_PIERCE = new ThreadLocal<>();

    private MuramasaCombatHandler() {}

    private static final class PlayerComboState {
        int nextStep = 0;
        long lastSwingTick = -1000L;
    }

    private static final class ActiveIaijutsu {
        final ServerPlayer player;
        final ServerLevel level;
        final boolean awakened;
        int tick = 0;
        boolean fired = false;

        ActiveIaijutsu(ServerPlayer player, ServerLevel level, boolean awakened) {
            this.player = player;
            this.level = level;
            this.awakened = awakened;
        }
    }

    public static final class ActiveHitContext {
        public final Player player;
        public final Entity rawTarget;
        @Nullable
        public final LivingEntity realTarget;
        public final ItemStack stack;
        public final boolean awakened;
        public final MuramasaMoveType move;

        ActiveHitContext(Player player, Entity rawTarget, @Nullable LivingEntity realTarget,
                         ItemStack stack, boolean awakened, MuramasaMoveType move) {
            this.player = player;
            this.rawTarget = rawTarget;
            this.realTarget = realTarget;
            this.stack = stack;
            this.awakened = awakened;
            this.move = move;
        }
    }

    public static boolean isHoldingMuramasa(@Nullable LivingEntity entity) {
        return entity != null && entity.getMainHandItem().is(ModItems.MURAMASA);
    }

    public static boolean isDashInvulnerable(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        Long until = DASH_IFRAME_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() <= until && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    public static float getActiveArmorPierceFraction(LivingEntity target, DamageSource source) {
        Float p = ACTIVE_PIERCE.get();
        return p != null ? p : 0.0F;
    }

    @Nullable
    public static ActiveHitContext beginPlayerAttack(Player player, Entity target) {
        if (player.level().isClientSide || !isHoldingMuramasa(player)) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        boolean awakened = MuramasaItem.isAwakened(stack);
        PlayerComboState st = COMBO_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerComboState());
        long now = player.level().getGameTime();
        if (now - st.lastSwingTick > COMBO_RESET_TICKS) {
            st.nextStep = 0;
        }
        MuramasaMoveType move = MuramasaMoveType.comboByStep(st.nextStep);
        st.nextStep = (st.nextStep + 1) % 3;
        st.lastSwingTick = now;

        LivingEntity living = (target instanceof LivingEntity le) ? le : null;
        ActiveHitContext ctx = new ActiveHitContext(player, target, living, stack, awakened, move);
        ACTIVE_HIT.set(ctx);
        if (awakened || move == MuramasaMoveType.COMBO_3) {
            ACTIVE_PIERCE.set(awakened ? 0.25F : 0.15F);
        }
        return ctx;
    }

    public static float modifyAttackDamage(float amount) {
        ActiveHitContext ctx = ACTIVE_HIT.get();
        if (ctx == null) {
            return amount;
        }
        return amount * ctx.move.damageMultiplier;
    }

    public static void finishPlayerAttack(ActiveHitContext ctx) {
        ACTIVE_HIT.remove();
        ACTIVE_PIERCE.remove();
        if (!(ctx.player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) {
            return;
        }
        boolean landed = ctx.realTarget != null && ctx.realTarget.hurtTime > 0;
        if (landed && ctx.realTarget != null) {
            if (ctx.awakened) {
                float heal = (float) (MuramasaItem.AWAKENED_ATTACK_DAMAGE * ctx.move.damageMultiplier * 0.15);
                sp.heal(Math.min(5.0F, Math.max(1.0F, heal)));
                ctx.realTarget.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0, false, true));
            } else if (ctx.move == MuramasaMoveType.COMBO_3) {
                sp.heal(1.5F);
            }
        }
        executeSlashEffects(sp, level, ctx.move, ctx.awakened, landed);
    }

    public static void handleActionPacket(ServerPlayer player, MuramasaActionC2SPayload payload) {
        if (player == null || !player.isAlive() || player.isSpectator()
                || !(player.level() instanceof ServerLevel level) || !isHoldingMuramasa(player)) {
            return;
        }
        switch (payload.actionId()) {
            case MuramasaActionC2SPayload.ACTION_AIR_SLASH -> handleAirSlash(player, level);
            case MuramasaActionC2SPayload.ACTION_IAIJUTSU -> tryCastIaijutsu(player, level);
            case MuramasaActionC2SPayload.ACTION_BLOOD_OATH -> tryActivateBloodOath(player, level);
            default -> {}
        }
    }

    private static void handleAirSlash(ServerPlayer player, ServerLevel level) {
        if (ACTIVE_IAI.containsKey(player.getUUID())) {
            return;
        }
        long now = level.getGameTime();
        PlayerComboState st = COMBO_STATES.computeIfAbsent(player.getUUID(), k -> new PlayerComboState());
        if (now - st.lastSwingTick < 6L) {
            return;
        }
        if (now - st.lastSwingTick > COMBO_RESET_TICKS) {
            st.nextStep = 0;
        }
        MuramasaMoveType move = MuramasaMoveType.comboByStep(st.nextStep);
        st.nextStep = (st.nextStep + 1) % 3;
        st.lastSwingTick = now;

        boolean awakened = MuramasaItem.isAwakened(player.getMainHandItem());
        player.swing(InteractionHand.MAIN_HAND, true);
        boolean hitAny = performArcSlash(player, level, move, awakened, null);
        executeSlashEffects(player, level, move, awakened, hitAny);
    }

    private static boolean performArcSlash(ServerPlayer player, ServerLevel level, MuramasaMoveType move,
                                           boolean awakened, @Nullable LivingEntity exclude) {
        Vec3 look = player.getLookAngle().normalize();
        double reach = awakened ? 4.4 : 3.8;
        Vec3 center = player.getEyePosition().add(look.scale(1.9));
        AABB box = new AABB(center, center).inflate(reach * 0.65);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e != exclude && e.isAlive() && e.isAttackable() && !e.isAlliedTo(player));
        if (targets.isEmpty()) {
            return false;
        }
        float baseDmg = (float) (awakened ? MuramasaItem.AWAKENED_ATTACK_DAMAGE : MuramasaItem.BASE_ATTACK_DAMAGE);
        float dmg = baseDmg * move.damageMultiplier * 0.90F;
        boolean hitAny = false;
        ACTIVE_PIERCE.set(awakened ? 0.25F : (move == MuramasaMoveType.COMBO_3 ? 0.15F : 0.0F));
        try {
            for (LivingEntity foe : targets) {
                Vec3 toFoe = foe.position().add(0, foe.getBbHeight() * 0.5, 0).subtract(player.getEyePosition());
                if (toFoe.lengthSqr() > (reach + 0.5) * (reach + 0.5)) {
                    continue;
                }
                if (toFoe.normalize().dot(look) < (move == MuramasaMoveType.COMBO_3 ? 0.05 : 0.24)) {
                    continue;
                }
                if (foe.hurt(level.damageSources().playerAttack(player), dmg)) {
                    hitAny = true;
                    double kb = (move == MuramasaMoveType.COMBO_3) ? 0.60 : 0.32;
                    foe.push(look.x * kb, move == MuramasaMoveType.COMBO_3 ? 0.22 : 0.10, look.z * kb);
                    level.sendParticles(awakened ? MURAMASA_CRIMSON : MURAMASA_YELLOW,
                            foe.getX(), foe.getY() + foe.getBbHeight() * 0.55, foe.getZ(), 7, 0.35, 0.35, 0.35, 0.04);
                    if (awakened) {
                        player.heal(Math.min(4.0F, dmg * 0.15F));
                        foe.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0, false, true));
                    } else if (move == MuramasaMoveType.COMBO_3) {
                        player.heal(1.5F);
                    }
                }
            }
        } finally {
            ACTIVE_PIERCE.remove();
        }
        return hitAny;
    }

    private static void executeSlashEffects(ServerPlayer player, ServerLevel level,
                                            MuramasaMoveType move, boolean awakened, boolean landedHit) {
        float pitch = switch (move) {
            case COMBO_1 -> awakened ? 1.18F : 1.32F;
            case COMBO_2 -> awakened ? 1.08F : 1.24F;
            default -> awakened ? 0.88F : 1.04F;
        };
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                ModSounds.DUAL_BLADE_SLASH, SoundSource.PLAYERS, move == MuramasaMoveType.COMBO_3 ? 1.15F : 0.92F, pitch);

        Vec3 anchor = player.position().add(0.0, player.getBbHeight() * 0.62, 0.0);
        if (move == MuramasaMoveType.COMBO_3) {
            performArcSlash(player, level, move, awakened, null);
        }

        ModNetworking.sendMuramasaSyncNearby(level, player.position(), new MuramasaSyncPayload(
                MuramasaSyncPayload.EVENT_SLASH, player.getId(), (byte) move.id, awakened,
                move.baseDisplayTicks, anchor.x, anchor.y, anchor.z,
                player.getYRot(), player.getXRot(), 2.1F));
    }

    /** RMB — Nhất Đao Huyết Nguyện (Iaijutsu Blood-Oath Draw): gồng nhanh 0.25s -> lướt rút kiếm 9 block + sóng 14 block! */
    public static void tryCastIaijutsu(ServerPlayer player, ServerLevel level) {
        if (!isHoldingMuramasa(player) || ACTIVE_IAI.containsKey(player.getUUID())) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        long now = level.getGameTime();
        long cdUntil = MuramasaItem.getIaiCooldownUntil(stack);
        if (now < cdUntil && !player.getAbilities().instabuild) {
            String rem = String.format(Locale.ROOT, "%.1f", (cdUntil - now) / 20.0F);
            player.displayClientMessage(Component.translatable("message.abyssalrpg.muramasa.iai_cd", rem), true);
            return;
        }
        boolean awakened = MuramasaItem.isAwakened(stack);
        MuramasaItem.setIaiCooldownUntil(stack, now + IAI_COOLDOWN_TICKS);
        ACTIVE_IAI.put(player.getUUID(), new ActiveIaijutsu(player, level, awakened));
        DASH_IFRAME_UNTIL.put(player.getUUID(), now + 14L);

        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                ModSounds.DUAL_BLADE_DASH_CHARGE, SoundSource.PLAYERS, 1.15F, awakened ? 0.85F : 1.12F);
        player.displayClientMessage(Component.translatable("message.abyssalrpg.muramasa.cast_iai"), true);

        ModNetworking.sendMuramasaSyncNearby(level, player.position(), new MuramasaSyncPayload(
                MuramasaSyncPayload.EVENT_IAI_START, player.getId(), (byte) MuramasaMoveType.IAIJUTSU_DRAW.id, awakened,
                IAI_TOTAL_TICKS, player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), 14.0F));
    }

    /** [R] / Shift+RMB — Huyết Thệ Yêu Đao (Muramasa Demon Possession, 12s, hồi 28s). */
    public static void tryActivateBloodOath(ServerPlayer player, ServerLevel level) {
        if (!isHoldingMuramasa(player)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        long now = level.getGameTime();
        if (MuramasaItem.isAwakened(stack)) {
            String rem = String.format(Locale.ROOT, "%.1f", Math.max(0L, MuramasaItem.getAwakenEndTick(stack) - now) / 20.0F);
            player.displayClientMessage(Component.translatable("message.abyssalrpg.muramasa.oath_active", rem), true);
            return;
        }
        long cdUntil = MuramasaItem.getAwakenCooldownUntil(stack);
        if (now < cdUntil && !player.getAbilities().instabuild) {
            String rem = String.format(Locale.ROOT, "%.1f", (cdUntil - now) / 20.0F);
            player.displayClientMessage(Component.translatable("message.abyssalrpg.muramasa.oath_cd", rem), true);
            return;
        }

        // Blood Oath sacrifice: 4 HP (2 hearts), strictly non-lethal (always leaves at least 2 HP)
        if (!player.getAbilities().instabuild && player.getHealth() > 6.0F) {
            player.setHealth(player.getHealth() - 4.0F);
        }
        MuramasaItem.setAwakenedState(stack, true, now + BLOOD_OATH_DURATION_TICKS, 0L);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BLOOD_OATH_DURATION_TICKS, 1, false, true, true));

        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                ModSounds.MORGAN_TRANSFORM, SoundSource.PLAYERS, 1.35F, 1.05F);
        level.sendParticles(MURAMASA_CRIMSON, player.getX(), player.getY() + 1.1, player.getZ(), 32, 1.1, 0.9, 1.1, 0.10);
        level.sendParticles(MURAMASA_YELLOW, player.getX(), player.getY() + 1.1, player.getZ(), 28, 1.1, 0.9, 1.1, 0.12);
        level.sendParticles(MURAMASA_DARK, player.getX(), player.getY() + 1.0, player.getZ(), 24, 1.2, 0.8, 1.2, 0.08);
        ModNetworking.sendShakeNearby(level, player.position(), 18.0, 0.72F, 8);
        player.displayClientMessage(Component.translatable("message.abyssalrpg.muramasa.oath_awaken"), true);

        ModNetworking.sendMuramasaSyncNearby(level, player.position(), new MuramasaSyncPayload(
                MuramasaSyncPayload.EVENT_BLOOD_OATH_AWAKEN, player.getId(), (byte) MuramasaMoveType.BLOOD_OATH_AWAKEN.id,
                true, MuramasaMoveType.BLOOD_OATH_AWAKEN.baseDisplayTicks,
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), 5.0F));
    }

    private static void fireIaijutsuDash(ServerPlayer player, ServerLevel level, boolean awakened) {
        Vec3 flatLook = Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
        Vec3 start = player.position().add(0.0, 1.0, 0.0);

        // Explosive forward samurai dash impulse (~9 blocks feel with momentum!)
        player.setDeltaMovement(flatLook.x * 1.95, 0.14, flatLook.z * 1.95);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;

        level.playSound(null, start.x, start.y, start.z,
                ModSounds.DUAL_BLADE_DASH_WHOOSH, SoundSource.PLAYERS, 1.45F, awakened ? 0.86F : 1.08F);
        level.playSound(null, start.x, start.y, start.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.35F, awakened ? 0.75F : 1.05F);
        level.playSound(null, start.x, start.y, start.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.50F, 1.75F);
        level.sendParticles(ParticleTypes.FLASH, start.x + flatLook.x * 2.0, start.y + 0.4, start.z + flatLook.z * 2.0,
                1, 0.0, 0.0, 0.0, 0.0);
        ModNetworking.sendShakeNearby(level, start, 20.0, awakened ? 0.75F : 0.55F, 8);

        float baseDmg = (float) (awakened ? MuramasaItem.AWAKENED_ATTACK_DAMAGE : MuramasaItem.BASE_ATTACK_DAMAGE);
        float dmg = baseDmg * MuramasaMoveType.IAIJUTSU_DRAW.damageMultiplier;
        double range = awakened ? 16.0 : 14.0;
        double halfWidth = awakened ? 2.6 : 2.2;

        Set<UUID> hitIds = new HashSet<>();
        ACTIVE_PIERCE.set(0.35F);
        try {
            int steps = 16;
            for (int i = 1; i <= steps; i++) {
                double d = (i / (double) steps) * range;
                Vec3 pt = start.add(flatLook.scale(d));
                if ((i & 1) == 0) {
                    level.sendParticles(MURAMASA_YELLOW, pt.x, pt.y, pt.z, 4, 0.6, 0.25, 0.6, 0.04);
                    level.sendParticles(MURAMASA_CRIMSON, pt.x, pt.y, pt.z, 4, 0.6, 0.25, 0.6, 0.04);
                    if (i % 4 == 0) {
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                        level.sendParticles(ParticleTypes.FLASH, pt.x, pt.y + 0.3, pt.z, 1, 0, 0, 0, 0);
                    }
                }
                AABB box = new AABB(pt.x - halfWidth, pt.y - 1.4, pt.z - halfWidth,
                        pt.x + halfWidth, pt.y + 1.8, pt.z + halfWidth);
                for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != player && e.isAlive() && e.isAttackable() && !e.isAlliedTo(player))) {
                    if (!hitIds.add(foe.getUUID())) {
                        continue;
                    }
                    if (foe.hurt(level.damageSources().playerAttack(player), dmg)) {
                        foe.push(flatLook.x * 0.75, 0.26, flatLook.z * 0.75);
                        foe.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, awakened ? 1 : 0, false, true));
                        // Hiệu ứng mù lòa 10s (200 tick) + choáng chậm + lóe sáng ngay trước mặt mục tiêu trúng chém!
                        foe.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, IAI_FLASH_BLIND_TICKS, 0, false, true, true));
                        foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, true, true));
                        level.sendParticles(ParticleTypes.FLASH, foe.getX(), foe.getEyeY(), foe.getZ(), 1, 0, 0, 0, 0);
                        player.heal(awakened ? 3.0F : 2.0F);
                    }
                }
            }
        } finally {
            ACTIVE_PIERCE.remove();
        }

        ModNetworking.sendMuramasaSyncNearby(level, start, new MuramasaSyncPayload(
                MuramasaSyncPayload.EVENT_IAI_SLASH, player.getId(), (byte) MuramasaMoveType.IAIJUTSU_DRAW.id, awakened,
                14.0F, start.x, start.y, start.z, player.getYRot(), 0.0F, (float) range));
        // Kích hoạt hiệu ứng Mù Lòa Flash Trắng Toàn Màn Hình suốt 10 giây (200 tick) cho người chém & mọi mục tiêu trong tầm!
        ModNetworking.sendMuramasaSyncNearby(level, start, new MuramasaSyncPayload(
                MuramasaSyncPayload.EVENT_FLASH_BLIND, player.getId(), (byte) MuramasaMoveType.IAIJUTSU_DRAW.id, awakened,
                (float) IAI_FLASH_BLIND_TICKS, start.x, start.y, start.z, player.getYRot(), 0.0F, (float) range + 4.0F));
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveIaijutsu>> it = ACTIVE_IAI.entrySet().iterator();
        while (it.hasNext()) {
            ActiveIaijutsu cast = it.next().getValue();
            ServerPlayer p = cast.player;
            if (!p.isAlive() || p.isRemoved() || !isHoldingMuramasa(p)) {
                it.remove();
                continue;
            }
            cast.tick++;
            if (cast.tick == IAI_DRAW_FIRE_TICK && !cast.fired) {
                cast.fired = true;
                fireIaijutsuDash(p, cast.level, cast.awakened);
            }
            if (cast.tick >= IAI_TOTAL_TICKS) {
                it.remove();
            }
        }

        // Tick 12s Blood Oath expiration across all players cleanly
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            long now = level.getGameTime();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack st = player.getInventory().getItem(i);
                if (!st.is(ModItems.MURAMASA) || !MuramasaItem.isAwakened(st)) {
                    continue;
                }
                if (now >= MuramasaItem.getAwakenEndTick(st)) {
                    MuramasaItem.setAwakenedState(st, false, 0L, now + BLOOD_OATH_COOLDOWN_TICKS);
                    player.displayClientMessage(Component.translatable("message.abyssalrpg.muramasa.oath_end"), true);
                    level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.85F, 0.90F);
                    ModNetworking.sendMuramasaSyncNearby(level, player.position(), new MuramasaSyncPayload(
                            MuramasaSyncPayload.EVENT_BLOOD_OATH_END, player.getId(), (byte) 0, false,
                            0.0F, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), 0.0F));
                }
            }
        }
    }
}
