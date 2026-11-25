package com.abyssalrpg.registry;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.mythic.CerberusBossEntity;
import com.abyssalrpg.entity.mythic.CinderDragonBossEntity;
import com.abyssalrpg.entity.mythic.CyclopsWardenBossEntity;
import com.abyssalrpg.entity.mythic.FenrirBossEntity;
import com.abyssalrpg.entity.mythic.HydraBossEntity;
import com.abyssalrpg.entity.mythic.JormungandrBossEntity;
import com.abyssalrpg.entity.mythic.LeviathanBossEntity;
import com.abyssalrpg.entity.mythic.NightmareKnightBossEntity;
import com.abyssalrpg.entity.mythic.cinder.CinderDragonHitboxPartEntity;
import com.abyssalrpg.entity.mythic.hydra.HydraHitboxPartEntity;
import com.abyssalrpg.entity.mythic.jormungandr.JormungandrHitboxPartEntity;
import com.abyssalrpg.entity.mythic.jormungandr.VenomProjectileEntity;
import com.abyssalrpg.entity.mythic.nightmare.NightmareBoneSpikeEntity;
import com.abyssalrpg.entity.mythic.nightmare.NightmareHitboxPartEntity;
import com.abyssalrpg.entity.vfx.AbyssalOrbEntity;
import com.abyssalrpg.entity.vfx.EnergySlashEntity;
import com.abyssalrpg.entity.vfx.LaserBeamEntity;
import com.abyssalrpg.entity.vfx.MagicCircleEntity;
import com.abyssalrpg.entity.vfx.SeveredHydraHeadEntity;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    // Core Colossal Boss (Cinder Dragon)
    public static final EntityType<CinderDragonBossEntity> CINDER_DRAGON = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("cinder_dragon"),
            EntityType.Builder.of(CinderDragonBossEntity::new, MobCategory.MONSTER)
                    .sized(CinderDragonBossEntity.CORE_COLLISION_WIDTH,
                           CinderDragonBossEntity.CORE_COLLISION_HEIGHT)
                    .eyeHeight(CinderDragonBossEntity.CORE_EYE_HEIGHT)
                    .fireImmune()
                    .clientTrackingRange(36) // 576 blocks bao trọn sải cánh ~31 block & tầm BossBar 140 block
                    .updateInterval(2)
                    .build()
    );
    public static final EntityType<CinderDragonHitboxPartEntity> CINDER_DRAGON_HITBOX_PART = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("cinder_dragon_hitbox_part"),
            EntityType.Builder.of(CinderDragonHitboxPartEntity::new, MobCategory.MISC)
                    .sized(3.2f, 3.2f)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(36)
                    .updateInterval(1)
                    .build()
    );

    // Legendary Mythological Titan Bosses (Fenrir, Spirit Hydra, Jormungandr, Leviathan, Cerberus, Nightmare Knight)
    public static final EntityType<FenrirBossEntity> FENRIR_BOSS = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("fenrir_boss"),
            EntityType.Builder.of(FenrirBossEntity::new, MobCategory.MONSTER)
                    .sized(FenrirBossEntity.HITBOX_WIDTH, FenrirBossEntity.HITBOX_HEIGHT)
                    .eyeHeight(FenrirBossEntity.HITBOX_EYE_HEIGHT)
                    .fireImmune()
                    .clientTrackingRange(24)
                    .updateInterval(2)
                    .build()
    );
    public static final EntityType<HydraBossEntity> HYDRA_BOSS = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("spirit_hydra"),
            EntityType.Builder.of(HydraBossEntity::new, MobCategory.MONSTER)
                    .sized(HydraBossEntity.BASE_WIDTH * HydraBossEntity.SCALE,
                           HydraBossEntity.BASE_HEIGHT * HydraBossEntity.SCALE)
                    .eyeHeight(HydraBossEntity.BASE_EYE_HEIGHT * HydraBossEntity.SCALE)
                    .fireImmune()
                    .clientTrackingRange(24) // 384 blocks cho Spirit Hydra 4 đầu kích thước ngang/nhỉnh Leviathan
                    .updateInterval(2)
                    .build()
    );
    public static final EntityType<HydraHitboxPartEntity> HYDRA_HITBOX_PART = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("hydra_hitbox_part"),
            EntityType.Builder.of(HydraHitboxPartEntity::new, MobCategory.MISC)
                    .sized(2.4f, 2.4f)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(24)
                    .updateInterval(1)
                    .build()
    );
    public static final EntityType<JormungandrBossEntity> JORMUNGANDR_BOSS = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("jormungandr"),
            EntityType.Builder.of(JormungandrBossEntity::new, MobCategory.MONSTER)
                    .sized(JormungandrBossEntity.CORE_HEAD_WIDTH, JormungandrBossEntity.CORE_HEAD_HEIGHT)
                    .eyeHeight(JormungandrBossEntity.CORE_EYE_HEIGHT)
                    .fireImmune()
                    .clientTrackingRange(32) // 512 blocks cho thân rắn khổng lồ dài 32-45 block
                    .updateInterval(2)
                    .build()
    );
    public static final EntityType<JormungandrHitboxPartEntity> JORMUNGANDR_HITBOX_PART = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("jormungandr_hitbox_part"),
            EntityType.Builder.of(JormungandrHitboxPartEntity::new, MobCategory.MISC)
                    .sized(2.0f, 2.0f)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .build()
    );
    public static final EntityType<VenomProjectileEntity> VENOM_PROJECTILE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("venom_projectile"),
            EntityType.Builder.of(VenomProjectileEntity::new, MobCategory.MISC)
                    .sized(0.9f, 0.9f)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(24)
                    .updateInterval(1)
                    .build()
    );
    public static final EntityType<LeviathanBossEntity> LEVIATHAN_BOSS = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("leviathan"),
            EntityType.Builder.of(LeviathanBossEntity::new, MobCategory.MONSTER)
                    .sized(LeviathanBossEntity.BASE_WIDTH * LeviathanBossEntity.SCALE,
                           LeviathanBossEntity.BASE_HEIGHT * LeviathanBossEntity.SCALE)
                    .eyeHeight(LeviathanBossEntity.BASE_EYE_HEIGHT * LeviathanBossEntity.SCALE)
                    .fireImmune()
                    .clientTrackingRange(24) // 24 chunks = 384 blocks để không mất boss khổng lồ khi ở xa
                    .updateInterval(2)
                    .build()
    );
    public static final EntityType<CerberusBossEntity> CERBERUS = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("cerberus"),
            EntityType.Builder.of(CerberusBossEntity::new, MobCategory.MONSTER)
                    .sized(CerberusBossEntity.BASE_WIDTH * CerberusBossEntity.SCALE,
                           CerberusBossEntity.BASE_HEIGHT * CerberusBossEntity.SCALE)
                    .eyeHeight(CerberusBossEntity.BASE_EYE_HEIGHT * CerberusBossEntity.SCALE)
                    .fireImmune()
                    .clientTrackingRange(25) // 25 chunks = 400 blocks (nhân đồng bộ theo visualScaleMultiplier=1.25)
                    .updateInterval(2)
                    .build()
    );
    public static final EntityType<NightmareKnightBossEntity> NIGHTMARE_KNIGHT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("nightmare_knight"),
            EntityType.Builder.of(NightmareKnightBossEntity::new, MobCategory.MONSTER)
                    .sized(NightmareKnightBossEntity.CORE_COLLISION_WIDTH,
                           NightmareKnightBossEntity.CORE_COLLISION_HEIGHT)
                    .eyeHeight(NightmareKnightBossEntity.CORE_EYE_HEIGHT)
                    .fireImmune()
                    .clientTrackingRange(32) // 32 chunks = 512 blocks cho boss khổng lồ cao ~19.5 block
                    .updateInterval(2)
                    .build()
    );
    public static final EntityType<NightmareHitboxPartEntity> NIGHTMARE_HITBOX_PART = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("nightmare_hitbox_part"),
            EntityType.Builder.of(NightmareHitboxPartEntity::new, MobCategory.MISC)
                    .sized(2.5f, 2.5f)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .build()
    );
    public static final EntityType<NightmareBoneSpikeEntity> NIGHTMARE_BONE_SPIKE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("nightmare_bone_spike"),
            EntityType.Builder.of(NightmareBoneSpikeEntity::new, MobCategory.MISC)
                    .sized(1.6f, 4.5f)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(24)
                    .updateInterval(2)
                    .build()
    );
    public static final EntityType<CyclopsWardenBossEntity> CYCLOPS_WARDEN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("cyclops_warden"),
            EntityType.Builder.of(CyclopsWardenBossEntity::new, MobCategory.MONSTER)
                    .sized(CyclopsWardenBossEntity.HITBOX_WIDTH, CyclopsWardenBossEntity.HITBOX_HEIGHT)
                    .eyeHeight(CyclopsWardenBossEntity.HITBOX_EYE_HEIGHT)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build()
    );

    // 5 Custom 3D Rendered Boss Spell & VFX Entities
    public static final EntityType<MagicCircleEntity> MAGIC_CIRCLE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("magic_circle"),
            EntityType.Builder.of(MagicCircleEntity::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).build()
    );
    public static final EntityType<EnergySlashEntity> ENERGY_SLASH = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("energy_slash"),
            EntityType.Builder.of(EnergySlashEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).clientTrackingRange(96).updateInterval(1).build()
    );
    public static final EntityType<AbyssalOrbEntity> ABYSSAL_ORB = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("abyssal_orb"),
            EntityType.Builder.of(AbyssalOrbEntity::new, MobCategory.MISC).sized(0.8f, 0.8f).clientTrackingRange(96).updateInterval(1).build()
    );
    public static final EntityType<LaserBeamEntity> LASER_BEAM = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("laser_beam"),
            EntityType.Builder.of(LaserBeamEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).clientTrackingRange(128).updateInterval(1).build()
    );
    public static final EntityType<ShockwaveRingEntity> SHOCKWAVE_RING = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("shockwave_ring"),
            EntityType.Builder.of(ShockwaveRingEntity::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).build()
    );
    public static final EntityType<SeveredHydraHeadEntity> SEVERED_HYDRA_HEAD = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, AbyssalRpgMod.id("severed_hydra_head"),
            EntityType.Builder.of(SeveredHydraHeadEntity::new, MobCategory.MISC).sized(0.85f, 0.85f).clientTrackingRange(12).updateInterval(2).build()
    );

    public static void init() {
        FabricDefaultAttributeRegistry.register(CINDER_DRAGON, CinderDragonBossEntity.createBossAttributes());
        FabricDefaultAttributeRegistry.register(FENRIR_BOSS, FenrirBossEntity.createBossAttributes());
        FabricDefaultAttributeRegistry.register(HYDRA_BOSS, HydraBossEntity.createBossAttributes());
        FabricDefaultAttributeRegistry.register(JORMUNGANDR_BOSS, JormungandrBossEntity.createBossAttributes());
        FabricDefaultAttributeRegistry.register(LEVIATHAN_BOSS, LeviathanBossEntity.createBossAttributes());
        FabricDefaultAttributeRegistry.register(CERBERUS, CerberusBossEntity.createBossAttributes());
        FabricDefaultAttributeRegistry.register(NIGHTMARE_KNIGHT, NightmareKnightBossEntity.createBossAttributes());
        FabricDefaultAttributeRegistry.register(CYCLOPS_WARDEN, CyclopsWardenBossEntity.createBossAttributes());
    }

    private ModEntities() {}
}
