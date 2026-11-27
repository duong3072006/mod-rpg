package com.abyssalrpg.entity.mythic.jormungandr;

import com.abyssalrpg.config.JormungandrConfig;
import com.abyssalrpg.entity.mythic.JormungandrBossEntity;
import com.abyssalrpg.registry.ModEntities;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Cầu độc bay theo cung parabol cho Venom Spit (Phase 1) và Venom Rain (Phase 2).
 * Khi chạm đất hoặc người chơi: gây sát thương `mobAttack`, áp `Poison II`, và sinh vũng độc giới hạn trên Boss.
 */
public class VenomProjectileEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED =
            SynchedEntityData.defineId(VenomProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private UUID ownerUuid;
    private Vec3 vel = Vec3.ZERO;
    private float impactDamage = 14.0f;
    private float puddleTickDamage = 6.0f;
    private int maxLife = 80;

    public VenomProjectileEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static VenomProjectileEntity spawn(ServerLevel level, JormungandrBossEntity owner,
                                              Vec3 origin, Vec3 initialVel,
                                              float impactDmg, float puddleDmg, boolean enraged) {
        VenomProjectileEntity p = new VenomProjectileEntity(ModEntities.VENOM_PROJECTILE, level);
        p.setPos(origin.x, origin.y, origin.z);
        p.ownerUuid = owner != null ? owner.getUUID() : null;
        p.vel = initialVel;
        p.impactDamage = impactDmg;
        p.puddleTickDamage = puddleDmg;
        p.entityData.set(DATA_ENRAGED, enraged);
        level.addFreshEntity(p);
        return p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ENRAGED, false);
    }

    public boolean isEnraged() {
        return this.entityData.get(DATA_ENRAGED);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel sl = (ServerLevel) this.level();
        // Trọng lực parabol
        this.vel = new Vec3(this.vel.x * 0.99, this.vel.y - 0.055, this.vel.z * 0.99);
        Vec3 next = this.position().add(this.vel);
        this.setPos(next.x, next.y, next.z);
        this.setDeltaMovement(this.vel);

        if (this.tickCount % 2 == 0) {
            sl.sendParticles(isEnraged() ? ParticleTypes.DRAGON_BREATH : ParticleTypes.ITEM_SLIME,
                    next.x, next.y, next.z, 3, 0.2, 0.2, 0.2, 0.01);
        }

        // Va chạm người chơi
        AABB box = new AABB(next.x - 1.1, next.y - 1.1, next.z - 1.1, next.x + 1.1, next.y + 1.1, next.z + 1.1);
        for (Player pl : sl.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
            splatter(sl, next, pl);
            return;
        }

        // Chạm khối rắn mặt đất
        BlockPos bp = BlockPos.containing(next);
        if (!sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty() || this.tickCount >= maxLife) {
            splatter(sl, new Vec3(next.x, bp.getY() + 1.0, next.z), null);
        }
    }

    private void splatter(ServerLevel level, Vec3 pos, @Nullable Player directHit) {
        Entity owner = this.ownerUuid != null ? level.getEntity(this.ownerUuid) : null;
        int venomTicks = Math.min(JormungandrConfig.get().venomDurationTicks, 160);

        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.3f, 0.72f);
        level.sendParticles(ParticleTypes.ITEM_SLIME, pos.x, pos.y + 0.2, pos.z, 14, 0.85, 0.25, 0.85, 0.08);
        if (isEnraged()) {
            level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y + 0.25, pos.z, 10, 0.75, 0.2, 0.75, 0.04);
        }

        if (directHit != null) {
            com.abyssalrpg.combat.KatanaCombatHandler.runUnparryableMagic(() ->
                    directHit.hurt(owner instanceof LivingEntity l
                            ? level.damageSources().mobAttack(l)
                            : level.damageSources().magic(), impactDamage));
            directHit.addEffect(new MobEffectInstance(MobEffects.POISON, venomTicks, 1));
        } else {
            AABB splash = new AABB(pos.x - 2.2, pos.y - 1.0, pos.z - 2.2, pos.x + 2.2, pos.y + 2.2, pos.z + 2.2);
            for (Player p : level.getEntitiesOfClass(Player.class, splash,
                    pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                com.abyssalrpg.combat.KatanaCombatHandler.runUnparryableMagic(() ->
                        p.hurt(owner instanceof LivingEntity l
                                ? level.damageSources().mobAttack(l)
                                : level.damageSources().magic(), impactDamage * 0.85f));
                p.addEffect(new MobEffectInstance(MobEffects.POISON, venomTicks, 1));
            }
        }

        if (owner instanceof JormungandrBossEntity jorm) {
            jorm.addVenomPuddle(pos, 2.0f, puddleTickDamage, 90); // 4.5 giây, bán kính 2
        }
        this.discard();
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

