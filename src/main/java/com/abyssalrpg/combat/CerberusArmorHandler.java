package com.abyssalrpg.combat;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.api.BossHealthHolder;
import com.abyssalrpg.config.ArmorConfig;
import com.abyssalrpg.item.CerberusArmorItem;
import com.abyssalrpg.network.CerberusArmorActionC2SPayload;
import com.abyssalrpg.network.CerberusArmorSyncPayload;
import com.abyssalrpg.network.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Xử lý Full Set Bonus 4/4 & Kỹ Năng Lưỡi Đao Cánh Tay của Bộ Giáp Cerberus phía server:
 * 1. Kháng Lửa (`MobEffects.FIRE_RESISTANCE`)
 * 2. Tốc Độ I (`MobEffects.MOVEMENT_SPEED`, amp 0)
 * 3. Sức Mạnh I (`MobEffects.DAMAGE_BOOST`, amp 0) — mặc định khi mặc giáp (2 lưỡi đao co rút ẩn trong tay).
 * 4. Ấn Chuột Phải khi 2 tay trống (`ACTION_ACTIVATE_BLADES`): nâng Sức Mạnh lên **Sức Mạnh II (1 phút = 1200 ticks)**
 *    và bung 2 lưỡi đao từ cánh tay; hết 1 phút (hoặc tháo giáp / chết / mất buff) lưỡi đao tự co rút vào tay!
 * 5. +4.0 Máu Tối Đa (+2 tim) qua `AttributeModifier` cố định `abyssalrpg:cerberus_armor_set_health` (`ADD_VALUE`),
 *    không cộng dồn khi relog/đổi world, gỡ ngay khi tháo 1 món + clamp máu an toàn, không chạm `BossHealthHolder`.
 * 6. Đòn đánh thiêu đốt bằng Lửa Địa Ngục (`4s`) kèm tia lửa địa ngục gọn nhẹ.
 */
public final class CerberusArmorHandler {
    public static final ResourceLocation SET_HEALTH_MODIFIER_ID =
            AbyssalRpgMod.id("cerberus_armor_set_health");

    /** 1 phút = 60 giây = 1200 ticks. */
    public static final int BLADE_OVERDRIVE_DURATION_TICKS = 60 * 20;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Lưu số tick còn lại của trạng thái bung 2 lưỡi đao (Strength II) theo UUID người chơi. */
    private static final Map<UUID, Integer> BLADE_REMAINING_TICKS = new ConcurrentHashMap<>();

    private CerberusArmorHandler() {}

    public static int countEquippedPieces(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof CerberusArmorItem) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasFullSet(LivingEntity entity) {
        return countEquippedPieces(entity) >= 4;
    }

    public static boolean isBladesActive(UUID playerId) {
        Integer rem = BLADE_REMAINING_TICKS.get(playerId);
        return rem != null && rem > 0;
    }

    private static int getOverdriveStrengthAmplifier(ArmorConfig cfg) {
        return Math.max(1, cfg.cerberusSetBonusStrengthAmplifier + 1);
    }

    public static void handleActionPacket(ServerPlayer player, CerberusArmorActionC2SPayload payload) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        if (payload.actionType() != CerberusArmorActionC2SPayload.ACTION_ACTIVATE_BLADES) {
            return;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.cerberusEnableSetBonus || !hasFullSet(player)) {
            return;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return;
        }

        UUID id = player.getUUID();
        Integer existingRem = BLADE_REMAINING_TICKS.get(id);
        if (existingRem != null && existingRem > 0) {
            int secs = (existingRem + 19) / 20;
            String formatted = String.format("%d:%02d", secs / 60, secs % 60);
            player.displayClientMessage(
                    Component.translatable("message.abyssalrpg.cerberus_blades.active", formatted)
                            .withStyle(ChatFormatting.GOLD),
                    true
            );
            return;
        }

        int targetAmp = getOverdriveStrengthAmplifier(cfg);
        BLADE_REMAINING_TICKS.put(id, BLADE_OVERDRIVE_DURATION_TICKS);

        // Nâng Sức Mạnh I thành Sức Mạnh II trong đúng 1 phút (1200 ticks)
        player.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_BOOST,
                BLADE_OVERDRIVE_DURATION_TICKS,
                targetAmp,
                false,
                true,
                true
        ));

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 0.85F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.70F, 1.15F);
        level.sendParticles(
                ParticleTypes.FLAME,
                player.getX(), player.getY() + 1.15, player.getZ(),
                16, 0.55, 0.35, 0.55, 0.05
        );
        level.sendParticles(
                ParticleTypes.LAVA,
                player.getX(), player.getY() + 1.15, player.getZ(),
                6, 0.45, 0.25, 0.45, 0.04
        );

        ModNetworking.sendCerberusArmorSyncNearby(
                level, player, new CerberusArmorSyncPayload(player.getId(), true, BLADE_OVERDRIVE_DURATION_TICKS));

        player.displayClientMessage(
                Component.translatable("message.abyssalrpg.cerberus_blades.deployed")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                true
        );
    }

    public static void tickServer(MinecraftServer server) {
        ArmorConfig cfg = ArmorConfig.get();
        int interval = Math.max(1, cfg.cerberusSetBonusTickInterval);
        boolean applyEffectsThisTick = (server.getTickCount() % interval) == 0;
        int overdriveAmp = getOverdriveStrengthAmplifier(cfg);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            Integer rem = BLADE_REMAINING_TICKS.get(id);

            if (!player.isAlive()) {
                if (rem != null) {
                    BLADE_REMAINING_TICKS.remove(id);
                    ModNetworking.sendCerberusArmorSyncNearby(
                            player.serverLevel(), player, new CerberusArmorSyncPayload(player.getId(), false, 0));
                }
                continue;
            }

            syncMaxHealthModifier(player);

            if (rem != null) {
                boolean fullSet = cfg.cerberusEnableSetBonus && hasFullSet(player);
                MobEffectInstance strEffect = player.getEffect(MobEffects.DAMAGE_BOOST);
                boolean hasOverdriveBuff = strEffect != null && strEffect.getAmplifier() >= overdriveAmp;

                if (!fullSet || !hasOverdriveBuff || rem <= 1) {
                    BLADE_REMAINING_TICKS.remove(id);
                    if (!fullSet && hasOverdriveBuff) {
                        player.removeEffect(MobEffects.DAMAGE_BOOST);
                    }
                    ServerLevel level = player.serverLevel();
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.85F, 0.72F);
                    ModNetworking.sendCerberusArmorSyncNearby(
                            level, player, new CerberusArmorSyncPayload(player.getId(), false, 0));
                    if (fullSet) {
                        player.displayClientMessage(
                                Component.translatable("message.abyssalrpg.cerberus_blades.retracted")
                                        .withStyle(ChatFormatting.GRAY),
                                true
                        );
                        applyPeriodicSetEffects(player);
                    }
                } else {
                    int nextRem = rem - 1;
                    BLADE_REMAINING_TICKS.put(id, nextRem);
                    if ((nextRem % 40) == 0) {
                        ModNetworking.sendCerberusArmorSyncNearby(
                                player.serverLevel(), player, new CerberusArmorSyncPayload(player.getId(), true, nextRem));
                    }
                }
            }

            if (applyEffectsThisTick) {
                applyPeriodicSetEffects(player);
            }
        }
    }

    public static void onPlayerJoin(ServerPlayer joining) {
        if (joining == null) {
            return;
        }
        syncPlayerImmediate(joining);
        // Đồng bộ trạng thái đao của mọi người chơi cùng dimension đang kích hoạt tới người mới vào
        ServerLevel level = joining.serverLevel();
        for (ServerPlayer other : level.players()) {
            Integer rem = BLADE_REMAINING_TICKS.get(other.getUUID());
            if (rem != null && rem > 0) {
                ServerPlayNetworking.send(joining, new CerberusArmorSyncPayload(other.getId(), true, rem));
            }
        }
    }

    public static void onPlayerRespawn(ServerPlayer newPlayer) {
        if (newPlayer == null) {
            return;
        }
        BLADE_REMAINING_TICKS.remove(newPlayer.getUUID());
        syncPlayerImmediate(newPlayer);
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        if (player != null) {
            BLADE_REMAINING_TICKS.remove(player.getUUID());
        }
    }

    public static void syncPlayerImmediate(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        syncMaxHealthModifier(entity);
        applyPeriodicSetEffects(entity);
        if (entity instanceof ServerPlayer sp) {
            Integer rem = BLADE_REMAINING_TICKS.get(sp.getUUID());
            boolean active = rem != null && rem > 0 && hasFullSet(sp);
            if (!active && rem != null) {
                BLADE_REMAINING_TICKS.remove(sp.getUUID());
            }
            ModNetworking.sendCerberusArmorSyncNearby(
                    sp.serverLevel(), sp, new CerberusArmorSyncPayload(sp.getId(), active, active ? rem : 0));
        }
    }

    public static void syncMaxHealthModifier(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || entity instanceof BossHealthHolder) {
            return;
        }
        AttributeInstance maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr == null) {
            return;
        }

        ArmorConfig cfg = ArmorConfig.get();
        boolean shouldHaveBonus = cfg.cerberusEnableSetBonus
                && cfg.cerberusSetBonusMaxHealth > 0.0
                && hasFullSet(entity);
        AttributeModifier existing = maxHealthAttr.getModifier(SET_HEALTH_MODIFIER_ID);

        if (shouldHaveBonus) {
            double bonus = cfg.cerberusSetBonusMaxHealth;
            if (existing == null) {
                maxHealthAttr.addPermanentModifier(new AttributeModifier(
                        SET_HEALTH_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            } else if (Math.abs(existing.amount() - bonus) > 1e-6
                    || existing.operation() != AttributeModifier.Operation.ADD_VALUE) {
                maxHealthAttr.removeModifier(SET_HEALTH_MODIFIER_ID);
                maxHealthAttr.addPermanentModifier(new AttributeModifier(
                        SET_HEALTH_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            }
        } else if (existing != null) {
            maxHealthAttr.removeModifier(SET_HEALTH_MODIFIER_ID);
            float newMax = entity.getMaxHealth();
            if (entity.getHealth() > newMax) {
                entity.setHealth(newMax);
            }
        }
    }

    public static void applyPeriodicSetEffects(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.cerberusEnableSetBonus || !hasFullSet(entity)) {
            return;
        }

        int duration = Math.max(40, cfg.cerberusSetBonusEffectDurationTicks);
        if (cfg.cerberusSetBonusFireResistance) {
            refreshAmbientEffect(entity, MobEffects.FIRE_RESISTANCE, duration, 0);
        }
        if (cfg.cerberusSetBonusSpeed) {
            refreshAmbientEffect(entity, MobEffects.MOVEMENT_SPEED, duration, Math.max(0, cfg.cerberusSetBonusSpeedAmplifier));
        }
        if (cfg.cerberusSetBonusStrength) {
            refreshAmbientEffect(entity, MobEffects.DAMAGE_BOOST, duration, Math.max(0, cfg.cerberusSetBonusStrengthAmplifier));
        }
    }

    private static void refreshAmbientEffect(LivingEntity entity, Holder<MobEffect> effect, int duration, int targetAmp) {
        MobEffectInstance existing = entity.getEffect(effect);
        if (existing == null
                || existing.getAmplifier() < targetAmp
                || (existing.getAmplifier() == targetAmp && existing.getDuration() < duration)) {
            entity.addEffect(new MobEffectInstance(effect, duration, targetAmp, true, false, true));
        }
    }

    /**
     * Khi người mặc đủ 4/4 Cerberus Armor đánh trúng mục tiêu phía server:
     * thiêu đốt mục tiêu bằng Lửa Địa Ngục (`cerberusSetBonusIgniteSeconds`, mặc định 4s) + tia lửa gọn nhẹ.
     */
    public static void onTargetHurtByAttacker(LivingEntity victim, DamageSource source) {
        if (victim == null || victim.level().isClientSide || !victim.isAlive() || source == null) {
            return;
        }
        ArmorConfig cfg = ArmorConfig.get();
        if (!cfg.cerberusEnableSetBonus || cfg.cerberusSetBonusIgniteSeconds <= 0) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        if (!hasFullSet(attacker)) {
            return;
        }

        int targetFireTicks = cfg.cerberusSetBonusIgniteSeconds * 20;
        if (victim.getRemainingFireTicks() < targetFireTicks) {
            victim.igniteForSeconds(cfg.cerberusSetBonusIgniteSeconds);
        }
        if (victim.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.FLAME,
                    victim.getX(),
                    victim.getY() + victim.getBbHeight() * 0.55,
                    victim.getZ(),
                    6,
                    victim.getBbWidth() * 0.35,
                    victim.getBbHeight() * 0.25,
                    victim.getBbWidth() * 0.35,
                    0.03
            );
        }
    }
}

