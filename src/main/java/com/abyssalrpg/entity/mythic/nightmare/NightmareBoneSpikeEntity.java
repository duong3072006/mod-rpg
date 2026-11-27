package com.abyssalrpg.entity.mythic.nightmare;

import com.abyssalrpg.entity.mythic.NightmareKnightBossEntity;
import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.network.NightmareVfxPayload;
import com.abyssalrpg.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Thực thể Gai Xương (Bone Spike) dùng chung cho 4 skill của Nightmare Knight:
 * - Chế độ `ERUPT`: báo hiệu dưới mặt đất `chargeTicks`, sau đó trồi mạnh lên
 * gây sát thương + hất tung rồi rút xuống.
 * - Chế độ `CAGE` (Bone Cage): trồi thành vòng cột xương có va chạm cứng quanh
 * người chơi vài giây,
 * PHÁ ĐƯỢC BẰNG CÁCH ĐÁNH (1-2 nhát kiếm hoặc mũi tên, `cageHp = 12.0`), tự rút
 * xuống sau thời hạn — không giam chết.
 *
 * Đăng ký `.noSave().noSummon()` để không bao giờ để lại rác trong thế giới khi
 * save/reload.
 */
public class NightmareBoneSpikeEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData
            .defineId(NightmareBoneSpikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData
            .defineId(NightmareBoneSpikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CHARGE_TICKS = SynchedEntityData
            .defineId(NightmareBoneSpikeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME_TICKS = SynchedEntityData
            .defineId(NightmareBoneSpikeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CAGE_MODE = SynchedEntityData
            .defineId(NightmareBoneSpikeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData
            .defineId(NightmareBoneSpikeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_HP = SynchedEntityData.defineId(NightmareBoneSpikeEntity.class,
            EntityDataSerializers.FLOAT);

    @Nullable
    private UUID ownerUuid;
    private float damage = 24.0f;
    private boolean erupted = false;
    private final Set<UUID> hitPlayers = new HashSet<>();

    public NightmareBoneSpikeEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static NightmareBoneSpikeEntity spawnEruption(ServerLevel level, LivingEntity owner, Vec3 pos,
            float height, float radius, int chargeTicks,
            int holdTicks, float damage, boolean enraged) {
        NightmareBoneSpikeEntity spike = new NightmareBoneSpikeEntity(ModEntities.NIGHTMARE_BONE_SPIKE, level);
        spike.setPos(pos.x, pos.y, pos.z);
        spike.ownerUuid = owner != null ? owner.getUUID() : null;
        spike.damage = damage;
        spike.entityData.set(DATA_HEIGHT, Math.max(1.5f, height));
        spike.entityData.set(DATA_RADIUS, Math.max(0.55f, radius));
        spike.entityData.set(DATA_CHARGE_TICKS, Math.max(4, chargeTicks));
        spike.entityData.set(DATA_LIFETIME_TICKS, Math.max(16, chargeTicks + holdTicks + 10));
        spike.entityData.set(DATA_CAGE_MODE, false);
        spike.entityData.set(DATA_ENRAGED, enraged);
        spike.entityData.set(DATA_HP, 20.0f);
        spike.setYRot(level.random.nextFloat() * 360.0f);
        spike.refreshDimensions();
        level.addFreshEntity(spike);
        return spike;
    }

    public static NightmareBoneSpikeEntity spawnCagePillar(ServerLevel level, LivingEntity owner, Vec3 pos,
            float height, int chargeTicks, int durationTicks,
            float eruptDamage, float breakHp, boolean enraged) {
        NightmareBoneSpikeEntity spike = new NightmareBoneSpikeEntity(ModEntities.NIGHTMARE_BONE_SPIKE, level);
        spike.setPos(pos.x, pos.y, pos.z);
        spike.ownerUuid = owner != null ? owner.getUUID() : null;
        spike.damage = eruptDamage;
        spike.entityData.set(DATA_HEIGHT, Math.max(3.2f, height));
        spike.entityData.set(DATA_RADIUS, 0.72f);
        spike.entityData.set(DATA_CHARGE_TICKS, Math.max(8, chargeTicks));
        spike.entityData.set(DATA_LIFETIME_TICKS, Math.max(40, chargeTicks + durationTicks));
        spike.entityData.set(DATA_CAGE_MODE, true);
        spike.entityData.set(DATA_ENRAGED, enraged);
        spike.entityData.set(DATA_HP, Math.max(4.0f, breakHp));
        spike.setYRot(level.random.nextFloat() * 360.0f);
        spike.refreshDimensions();
        level.addFreshEntity(spike);
        return spike;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HEIGHT, 4.0f);
        builder.define(DATA_RADIUS, 0.85f);
        builder.define(DATA_CHARGE_TICKS, 16);
        builder.define(DATA_LIFETIME_TICKS, 48);
        builder.define(DATA_CAGE_MODE, false);
        builder.define(DATA_ENRAGED, false);
        builder.define(DATA_HP, 12.0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_HEIGHT.equals(key) || DATA_RADIUS.equals(key)) {
            this.refreshDimensions();
        }
    }

    public float getSpikeHeight() {
        return this.entityData.get(DATA_HEIGHT);
    }

    public float getSpikeRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public int getChargeTicks() {
        return this.entityData.get(DATA_CHARGE_TICKS);
    }

    public int getLifetimeTicks() {
        return this.entityData.get(DATA_LIFETIME_TICKS);
    }

    public boolean isCageMode() {
        return this.entityData.get(DATA_CAGE_MODE);
    }

    public boolean isEnraged() {
        return this.entityData.get(DATA_ENRAGED);
    }

    public float getSpikeHp() {
        return this.entityData.get(DATA_HP);
    }

    public float getEmergeFraction(float partialTick) {
        float age = this.tickCount + partialTick;
        int charge = Math.max(1, getChargeTicks());
        int life = Math.max(charge + 10, getLifetimeTicks());
        if (age < charge) {
            return 0.08f * Mth.clamp(age / charge, 0.0f, 1.0f);
        }
        float afterCharge = age - charge;
        if (afterCharge < 5.0f) {
            float t = afterCharge / 5.0f;
            return Mth.lerp(1.0f - (1.0f - t) * (1.0f - t), 0.08f, 1.0f);
        }
        float remaining = life - age;
        if (remaining < 8.0f) {
            return Mth.clamp(remaining / 8.0f, 0.0f, 1.0f);
        }
        return 1.0f;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float r = getSpikeRadius();
        return EntityDimensions.scalable(r * 2.0f, getSpikeHeight());
    }

    @Override
    public void tick() {
        super.tick();
        int charge = getChargeTicks();
        int life = getLifetimeTicks();

        if (this.level().isClientSide) {
            return;
        }
        ServerLevel sl = (ServerLevel) this.level();

        if (this.tickCount < charge) {
            return; // 0 server sendParticles trong pha charge -> tối ưu FPS/MSPT
        }

        if (!this.erupted) {
            this.erupted = true;
            onErupt(sl);
        }

        if (this.tickCount >= life) {
            this.discard();
        }
    }

    private void onErupt(ServerLevel level) {
        float r = getSpikeRadius();
        float h = getSpikeHeight();
        level.playSound(null, this.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.3f, 0.62f);
        ModNetworking.sendNightmareVfxNearby(level, NightmareVfxPayload.KIND_SPIKE_ERUPT,
                this.position(), Vec3.ZERO, r, isEnraged());

        if (this.damage <= 0.01f) {
            return;
        }
        Entity owner = this.ownerUuid != null ? level.getEntity(this.ownerUuid) : null;
        double hitR = r + 0.65;
        AABB box = new AABB(this.getX() - hitR, this.getY() - 0.5, this.getZ() - hitR,
                this.getX() + hitR, this.getY() + h + 0.5, this.getZ() + hitR);
        for (Player p : level.getEntitiesOfClass(Player.class, box,
                pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
            if (Math.hypot(p.getX() - this.getX(), p.getZ() - this.getZ()) <= hitR && hitPlayers.add(p.getUUID())) {
                DamageSource src = (owner instanceof LivingEntity l)
                        ? level.damageSources().mobAttack(l)
                        : level.damageSources().magic();
                com.abyssalrpg.combat.KatanaCombatHandler.runUnparryableMagic(() -> p.hurt(src, this.damage));
                double up = isCageMode() ? 0.25 : 0.72;
                p.setDeltaMovement(p.getDeltaMovement().add(0.0, up, 0.0));
                p.hurtMarked = true;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!isCageMode() || this.level().isClientSide || amount <= 0.0f) {
            return false;
        }
        if (source.getEntity() instanceof NightmareKnightBossEntity) {
            return false;
        }
        float nextHp = getSpikeHp() - amount;
        this.entityData.set(DATA_HP, nextHp);
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, this.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, 1.15f);
            ModNetworking.sendNightmareVfxNearby(sl, NightmareVfxPayload.KIND_SPIKE_ERUPT,
                    this.position(), Vec3.ZERO, getSpikeRadius(), isEnraged());
            if (nextHp <= 0.0f) {
                sl.playSound(null, this.blockPosition(), SoundEvents.SKELETON_DEATH, SoundSource.BLOCKS, 0.9f, 0.75f);
                this.discard();
            }
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return isCageMode() && this.tickCount >= getChargeTicks();
    }

    @Override
    public boolean isAttackable() {
        return isCageMode();
    }

    @Override
    public boolean canBeCollidedWith() {
        return isCageMode() && this.tickCount >= getChargeTicks();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
