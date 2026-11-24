package com.abyssalrpg.combat;

import com.abyssalrpg.api.BossDefenseHolder;
import com.abyssalrpg.api.BossHealthHolder;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.NightmareArmorItem;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Xử lý Full Set Bonus 4/4 của Bộ Giáp Nightmare Knight (`Nightmare Sovereign`):
 * 1. Miễn nhiễm hoàn toàn `Darkness` (khắc chế trực tiếp Nightmare Realm của Boss Nightmare Knight).
 * 2. Kháng Lửa nhẹ (`Fire Resistance`) khi đang cháy hoặc giao chiến (không spam hạt potion).
 * 3. Giảm thêm `12%` sát thương từ đòn cận chiến & đòn Boss (cấu hình trong `abyssalrpg_armor.json`).
 * 4. Phản Gai (`Spiked Retaliation`): phản `20%` sát thương cận chiến (trần `15.0` dmg, cooldown `20` ticks/người chơi,
 *    chống đệ quy bằng `ThreadLocal`), kèm 4 tia lửa đỏ gọn chỉ khi kích hoạt (0 spam hạt đứng yên).
 */
public final class NightmareArmorHandler {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final DustParticleOptions RETALIATION_SPARK = new DustParticleOptions(
            new Vector3f(0.96f, 0.14f, 0.22f), 0.92f);

    private static final ThreadLocal<Boolean> RETALIATING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Map<UUID, Long> LAST_RETALIATION_TICK = new HashMap<>();

    private NightmareArmorHandler() {}

    public static int countEquippedPieces(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof NightmareArmorItem) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasFullSet(LivingEntity entity) {
        return countEquippedPieces(entity) >= 4;
    }

    /** Chạy nhẹ mỗi 10 tick cho toàn bộ player online (gần như 0 MSPT). */
    public static void tickServer(MinecraftServer server) {
        long tick = server.getTickCount();
        if ((tick & 7L) != 0L) {
            return;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.enableSetBonus) {
            return;
        }

        // Dọn cache cooldown cũ mỗi ~60 giây để không phình bộ nhớ trên server sống lâu
        if (tick % 1200L == 0L && !LAST_RETALIATION_TICK.isEmpty()) {
            long cutoff = tick - 600L;
            LAST_RETALIATION_TICK.entrySet().removeIf(e -> e.getValue() < cutoff);
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || !hasFullSet(player)) {
                continue;
            }
            if (cfg.darknessImmunity && player.hasEffect(MobEffects.DARKNESS)) {
                player.removeEffect(MobEffects.DARKNESS);
            }
            if (cfg.combatFireResistance) {
                boolean inCombat = player.isOnFire()
                        || (player.getLastHurtByMobTimestamp() > 0 && player.tickCount - player.getLastHurtByMobTimestamp() <= 200)
                        || (player.getLastHurtMobTimestamp() > 0 && player.tickCount - player.getLastHurtMobTimestamp() <= 200);
                if (inCombat) {
                    MobEffectInstance existing = player.getEffect(MobEffects.FIRE_RESISTANCE);
                    if (existing == null || existing.getDuration() < 30) {
                        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false, true));
                    }
                }
            }
        }
    }

    /** Chặn ngay hiệu ứng Darkness trước khi gắn lên người chơi mặc đủ 4/4 (0 nhấp nháy màn hình). */
    public static boolean shouldBlockMobEffect(LivingEntity entity, MobEffectInstance effect) {
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.enableSetBonus || !cfg.darknessImmunity || effect == null) {
            return false;
        }
        return effect.is(MobEffects.DARKNESS) && hasFullSet(entity);
    }

    /** Giảm thêm `12%` sát thương sau giáp/kháng phép đối với đòn cận chiến hoặc đòn từ Boss. */
    public static float applyFullSetDamageReduction(LivingEntity victim, DamageSource source, float postMagicDamage) {
        if (victim == null || victim.level().isClientSide || postMagicDamage <= 0.0f) {
            return postMagicDamage;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.enableSetBonus || cfg.meleeBossDamageReduction <= 0.0f || !hasFullSet(victim)) {
            return postMagicDamage;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return postMagicDamage;
        }
        if (isMeleeOrBossHit(source)) {
            float reduction = Mth.clamp(cfg.meleeBossDamageReduction, 0.0f, 0.50f);
            return postMagicDamage * (1.0f - reduction);
        }
        return postMagicDamage;
    }

    /**
     * Phản Gai (`Spiked Retaliation`) khi người mặc 4/4 trúng đòn cận chiến thực tế:
     * - Phản `20%` sát thương cận chiến nhận vào, trần `15.0` dmg, cooldown `20` ticks/người chơi.
     * - Chống vòng lặp vô hạn khi 2 người mặc Nightmare Armor đánh nhau bằng `RETALIATING`.
     * - Trả về sát thương phản thực tế (0 nếu không kích hoạt — tiện cho audit test).
     */
    public static float onVictimHurtReturn(LivingEntity victim, DamageSource source, float incomingAmount) {
        if (victim == null || victim.level().isClientSide || incomingAmount <= 0.0f || RETALIATING.get()) {
            return 0.0f;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.enableSetBonus || cfg.spikedRetaliationRatio <= 0.0f || !hasFullSet(victim)) {
            return 0.0f;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim || !attacker.isAlive()) {
            return 0.0f;
        }
        if (!isDirectMeleeHit(source, attacker)) {
            return 0.0f;
        }
        if (!(victim.level() instanceof ServerLevel sl)) {
            return 0.0f;
        }

        long now = sl.getGameTime();
        Long last = LAST_RETALIATION_TICK.get(victim.getUUID());
        if (last != null && (now - last) < cfg.spikedRetaliationCooldownTicks) {
            return 0.0f;
        }

        float reflected = Math.min(cfg.spikedRetaliationCap, incomingAmount * cfg.spikedRetaliationRatio);
        if (reflected < 0.25f) {
            return 0.0f;
        }

        LAST_RETALIATION_TICK.put(victim.getUUID(), now);
        RETALIATING.set(true);
        try {
            attacker.hurt(victim.damageSources().thorns(victim), reflected);
        } finally {
            RETALIATING.set(false);
        }

        if (cfg.enableRetaliationSparks) {
            sl.sendParticles(
                    RETALIATION_SPARK,
                    victim.getX(),
                    victim.getY() + victim.getBbHeight() * 0.62,
                    victim.getZ(),
                    4,
                    0.35, 0.28, 0.35,
                    0.02
            );
        }
        return reflected;
    }

    public static void clearCooldownForTest(UUID uuid) {
        LAST_RETALIATION_TICK.remove(uuid);
    }

    private static boolean isMeleeOrBossHit(DamageSource source) {
        boolean isBoss = (source.getEntity() instanceof BossHealthHolder)
                || (source.getEntity() instanceof BossDefenseHolder)
                || (source.getDirectEntity() instanceof BossHealthHolder)
                || (source.getDirectEntity() instanceof BossDefenseHolder);
        if (isBoss) {
            return true;
        }
        if (source.getEntity() instanceof LivingEntity attacker) {
            return isDirectMeleeHit(source, attacker);
        }
        return false;
    }

    private static boolean isDirectMeleeHit(DamageSource source, LivingEntity attacker) {
        return source.getDirectEntity() == attacker
                && !source.is(DamageTypeTags.IS_PROJECTILE)
                && !source.is(DamageTypeTags.IS_EXPLOSION)
                && !source.is(DamageTypeTags.BYPASSES_ARMOR);
    }
}

