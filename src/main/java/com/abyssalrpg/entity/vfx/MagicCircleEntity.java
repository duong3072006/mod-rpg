package com.abyssalrpg.entity.vfx;

import com.abyssalrpg.network.ModNetworking;
import com.abyssalrpg.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 3D Glowing Rotating Arcane Magic Sigil Circle that charges on the ground
 * and erupts into a sky-piercing 3D Energy Pillar + optional Lightning Strike!
 */
public class MagicCircleEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(MagicCircleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(MagicCircleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHARGE_TICKS = SynchedEntityData.defineId(MagicCircleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ERUPTED = SynchedEntityData.defineId(MagicCircleEntity.class, EntityDataSerializers.BOOLEAN);

    private float damage = 14.0f;
    private boolean summonLightning = false;
    private boolean applyWither = false;
    private UUID ownerUuid;
    private int eruptedTicks = 0;

    public MagicCircleEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static MagicCircleEntity spawn(ServerLevel level, LivingEntity owner, Vec3 pos, float radius,
                                          int chargeTicks, int rgbColor, float damage, boolean lightning, boolean wither) {
        MagicCircleEntity e = new MagicCircleEntity(ModEntities.MAGIC_CIRCLE, level);
        e.setPos(pos.x, pos.y + 0.06, pos.z);
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_COLOR, rgbColor);
        e.entityData.set(DATA_CHARGE_TICKS, chargeTicks);
        e.damage = damage;
        e.summonLightning = lightning;
        e.applyWither = wither;
        e.ownerUuid = owner != null ? owner.getUUID() : null;
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 3.5f);
        builder.define(DATA_COLOR, 0xFF2E63);
        builder.define(DATA_CHARGE_TICKS, 22);
        builder.define(DATA_ERUPTED, false);
    }

    public float getRadius() { return entityData.get(DATA_RADIUS); }
    public int getColor() { return entityData.get(DATA_COLOR); }
    public int getChargeTicks() { return entityData.get(DATA_CHARGE_TICKS); }
    public boolean isErupted() { return entityData.get(DATA_ERUPTED); }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) this.level();
        int charge = getChargeTicks();
        if (!isErupted()) {
            if (this.tickCount >= charge) {
                detonate(serverLevel);
            }
        } else {
            eruptedTicks++;
            if (eruptedTicks >= 14) {
                this.discard();
            }
        }
    }

    private void detonate(ServerLevel level) {
        this.entityData.set(DATA_ERUPTED, true);
        float r = getRadius();
        level.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.2f, 1.15f);
        ModNetworking.sendShakeNearby(level, this.position(), 26.0, 1.3f, 8);

        if (summonLightning) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(this.getX(), this.getY(), this.getZ());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }

        ShockwaveRingEntity.spawn(level, this.position(), r * 1.45f, getColor(), 12);
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 24, r * 0.4, 2.5, r * 0.4, 0.08);

        if (damage <= 0.01f) {
            return; // Pure visual summoning circle — do not damage/launch nearby players!
        }

        Entity owner = ownerUuid != null ? level.getEntity(ownerUuid) : null;
        AABB box = new AABB(this.getX() - r, this.getY() - 1.5, this.getZ() - r,
                this.getX() + r, this.getY() + 8.0, this.getZ() + r);
        for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
            if (Math.hypot(p.getX() - this.getX(), p.getZ() - this.getZ()) <= r + 0.4) {
                p.hurt(owner instanceof LivingEntity l ? level.damageSources().mobAttack(l) : level.damageSources().magic(), damage);
                p.setDeltaMovement(p.getDeltaMovement().add(0, 0.55, 0));
                p.hurtMarked = true;
                if (applyWither) {
                    p.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                }
            }
        }
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

