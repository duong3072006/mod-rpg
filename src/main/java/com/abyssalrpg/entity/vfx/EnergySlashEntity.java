package com.abyssalrpg.entity.vfx;

import com.abyssalrpg.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Colossal 3D Glowing Curved Crescent Energy Wave (Claw Slash / Flame Wave / Dragon Breath Blade).
 */
public class EnergySlashEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(EnergySlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(EnergySlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(EnergySlashEntity.class, EntityDataSerializers.INT);

    private Vec3 velocity = Vec3.ZERO;
    private float damage = 14.0f;
    private int maxLife = 26;
    private boolean ignite = false;
    private UUID ownerUuid;
    private final Set<UUID> hitEntities = new HashSet<>();

    public EnergySlashEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static EnergySlashEntity spawn(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 dir,
                                          float speed, float width, float rollDeg, int rgbColor, float damage, boolean ignite) {
        EnergySlashEntity e = new EnergySlashEntity(ModEntities.ENERGY_SLASH, level);
        Vec3 norm = dir.normalize();
        e.setPos(origin.x, origin.y, origin.z);
        e.velocity = norm.scale(speed);
        float yaw = (float) (Math.atan2(-norm.x, norm.z) * (180.0 / Math.PI));
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.entityData.set(DATA_WIDTH, width);
        e.entityData.set(DATA_ROLL, rollDeg);
        e.entityData.set(DATA_COLOR, rgbColor);
        e.damage = damage;
        e.ignite = ignite;
        e.ownerUuid = owner != null ? owner.getUUID() : null;
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WIDTH, 4.5f);
        builder.define(DATA_ROLL, 0.0f);
        builder.define(DATA_COLOR, 0xB04BFF);
    }

    public float getSlashWidth() { return entityData.get(DATA_WIDTH); }
    public float getSlashRoll() { return entityData.get(DATA_ROLL); }
    public int getColor() { return entityData.get(DATA_COLOR); }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.setPos(this.getX() + this.getDeltaMovement().x, this.getY() + this.getDeltaMovement().y, this.getZ() + this.getDeltaMovement().z);
            // Tối giản hạt phía Client: tối đa 1 tia lửa mảnh mỗi 5 tick (0 server sendParticles!)
            if (this.tickCount % 5 == 0) {
                this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY() + 0.2, this.getZ(), 0.0, 0.02, 0.0);
            }
            return;
        }
        this.setDeltaMovement(velocity);
        this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);

        ServerLevel serverLevel = (ServerLevel) this.level();

        float w = getSlashWidth() * 0.55f;
        Entity owner = ownerUuid != null ? serverLevel.getEntity(ownerUuid) : null;
        AABB box = new AABB(this.getX() - w, this.getY() - 1.8, this.getZ() - w,
                this.getX() + w, this.getY() + 2.2, this.getZ() + w);
        if (owner instanceof Player pOwner) {
            if (damage > 0.0f) {
                for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != pOwner && e.isAlive())) {
                    if (hitEntities.add(target.getUUID())) {
                        com.abyssalrpg.combat.KatanaCombatHandler.runUnparryableMagic(() ->
                                target.hurt(serverLevel.damageSources().playerAttack(pOwner), damage));
                        Vec3 playerPush = velocity.normalize().scale(0.65).add(0, 0.3, 0);
                        target.setDeltaMovement(target.getDeltaMovement().add(playerPush));
                        target.hurtMarked = true;
                        if (ignite) target.igniteForSeconds(5);
                    }
                }
            }
        } else {
            for (Player victim : serverLevel.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                if (hitEntities.add(victim.getUUID())) {
                    com.abyssalrpg.combat.KatanaCombatHandler.runUnparryableMagic(() ->
                            victim.hurt(owner instanceof LivingEntity l ? serverLevel.damageSources().mobAttack(l) : serverLevel.damageSources().magic(), damage));
                    Vec3 mobPush = velocity.normalize().scale(0.85).add(0, 0.4, 0);
                    victim.setDeltaMovement(victim.getDeltaMovement().add(mobPush));
                    victim.hurtMarked = true;
                    if (ignite) victim.igniteForSeconds(5);
                }
            }
        }

        if (this.tickCount >= maxLife) {
            this.discard();
        }
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

