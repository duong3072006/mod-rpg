package com.abyssalrpg.entity;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.entity.vfx.ShockwaveRingEntity;
import com.abyssalrpg.network.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractAbyssalBoss extends Monster {
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(AbstractAbyssalBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CAST_STATE =
            SynchedEntityData.defineId(AbstractAbyssalBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CAST_TICKS =
            SynchedEntityData.defineId(AbstractAbyssalBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RESTING =
            SynchedEntityData.defineId(AbstractAbyssalBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SPAWN_TICKS =
            SynchedEntityData.defineId(AbstractAbyssalBoss.class, EntityDataSerializers.INT);

    public static final int SPAWN_ANIM_DURATION = 64;

    protected int floor = 1;
    protected int phase = 1;
    protected int skillCooldown = 45;
    protected int idleRestTicks = 0;
    protected boolean summonVfxStarted = false;
    protected BlockPos arenaCenter = null;
    protected boolean dungeonBoss = false;
    protected boolean configured = false;

    protected final ServerBossEvent bossEvent;
    protected final List<DelayedAction> scheduledActions = new ArrayList<>();

    protected static class DelayedAction {
        int ticksLeft;
        final Runnable onTick;
        final Runnable onTrigger;

        public DelayedAction(int delayTicks, Runnable onTick, Runnable onTrigger) {
            this.ticksLeft = delayTicks;
            this.onTick = onTick;
            this.onTrigger = onTrigger;
        }
    }

    @SuppressWarnings("this-escape")
    protected AbstractAbyssalBoss(EntityType<? extends Monster> type, Level level, BossEvent.BossBarColor barColor) {
        super(type, level);
        this.bossEvent = new ServerBossEvent(this.getDisplayName(), barColor, BossEvent.BossBarOverlay.NOTCHED_12);
        this.bossEvent.setVisible(false); // Replaced by our custom Ornate RPG Boss Bar HUD!
        this.xpReward = 350;
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_CAST_STATE, 0);
        builder.define(DATA_CAST_TICKS, 0);
        builder.define(DATA_RESTING, false);
        builder.define(DATA_SPAWN_TICKS, 0);
    }

    public boolean isResting() {
        return this.entityData.get(DATA_RESTING);
    }

    public int getSpawnAnimTicks() {
        return this.entityData.get(DATA_SPAWN_TICKS);
    }

    public boolean isSpawningIn() {
        return getSpawnAnimTicks() > 0;
    }

    public float getSpawnProgress(float partialTick) {
        int ticks = getSpawnAnimTicks();
        if (ticks <= 0) return 1.0f;
        float rem = Math.max(0f, ticks - partialTick);
        return Math.max(0f, Math.min(1f, 1.0f - (rem / (float) SPAWN_ANIM_DURATION)));
    }

    public boolean isFlyingBoss() {
        return this.isNoGravity();
    }

    public int getSummonColorRgb() {
        return switch (this.bossEvent.getColor()) {
            case GREEN -> 0x22F58C;
            case BLUE -> 0x1AE8FF;
            case RED -> 0xFF3232;
            case YELLOW -> 0xFF8C1A;
            case PURPLE -> 0xB855FF;
            default -> 0x2AF5D6;
        };
    }

    public void triggerSummonEntrance() {
        this.entityData.set(DATA_SPAWN_TICKS, SPAWN_ANIM_DURATION);
        this.entityData.set(DATA_CAST_STATE, 3);
        this.entityData.set(DATA_CAST_TICKS, SPAWN_ANIM_DURATION);
        this.summonVfxStarted = false;
    }

    public int getSyncedPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    protected void syncPhaseData(int newPhase) {
        this.phase = Math.max(1, newPhase);
        this.entityData.set(DATA_PHASE, this.phase);
    }

    public int getCastState() {
        return this.entityData.get(DATA_CAST_STATE);
    }

    public int getCastTicks() {
        return this.entityData.get(DATA_CAST_TICKS);
    }

    protected void startCastAnimation(int state, int durationTicks) {
        this.entityData.set(DATA_CAST_STATE, state);
        this.entityData.set(DATA_CAST_TICKS, durationTicks);
    }

    protected void scheduleAction(int delayTicks, Runnable onTick, Runnable onTrigger) {
        this.scheduledActions.add(new DelayedAction(delayTicks, onTick, onTrigger));
    }

    public static AttributeSupplier.Builder createBaseBossAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 320.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.90);
    }

    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 18;
    private static final ResourceLocation MINION_HP_MOD = AbyssalRpgMod.id("boss_minion_hp");
    private static final ResourceLocation MINION_DMG_MOD = AbyssalRpgMod.id("boss_minion_dmg");
    private static final ResourceLocation MINION_ARMOR_MOD = AbyssalRpgMod.id("boss_minion_armor");
    private static final List<EntityType<?>> DEFAULT_MINION_POOL = List.of(
            EntityType.WITHER_SKELETON, EntityType.SKELETON, EntityType.BLAZE, EntityType.VINDICATOR
    );

    protected int getDefaultSpawnEggFloor() {
        return 6;
    }

    public float getColossalScale(int floor) {
        int tier = Math.max(MIN_TIER, Math.min(MAX_TIER, floor));
        return Math.min(4.65f, 3.35f + (tier - 1) * 0.048f + (tier == MAX_TIER ? 0.30f : 0f));
    }

    @Nullable
    protected Component getStandaloneBossTitle() {
        return null;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnData);
        if (!this.configured) {
            configureForFloor(getDefaultSpawnEggFloor(), null, false);
        }
        return data;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.RandomStrollGoal(this, 0.9, 25));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 28.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return super.doHurtTarget(target);
    }

    public void configureForFloor(int floor, BlockPos arenaCenter, boolean dungeonBoss) {
        this.configured = true;
        this.floor = Math.max(MIN_TIER, Math.min(MAX_TIER, floor));
        this.arenaCenter = arenaCenter;
        this.dungeonBoss = dungeonBoss;

        int f = this.floor;
        boolean milestone = (f == 6 || f == 12 || f == 18);
        double hpMult = 1.0 + (f - 1) * 0.42 + (f >= 10 ? (f - 9) * 0.35 : 0.0) + (milestone ? 0.65 : 0.0);
        double dmgMult = 1.0 + (f - 1) * 0.24 + (f >= 13 ? (f - 12) * 0.18 : 0.0) + (milestone ? 0.35 : 0.0);
        double armorBonus = Math.min(20.0, (f - 1) * 1.15);

        // Empowered boss HP & attack stats scaled cleanly by boss tier
        double hp = Math.round(220.0 * hpMult * 1.35);
        double dmg = Math.round(11.0 * dmgMult * 1.30 * 10.0) / 10.0;

        setAttr(Attributes.MAX_HEALTH, hp);
        this.setHealth(this.getMaxHealth());
        setAttr(Attributes.ATTACK_DAMAGE, dmg);
        setAttr(Attributes.ARMOR, Math.min(26.0, 8.0 + armorBonus));
        setAttr(Attributes.ARMOR_TOUGHNESS, Math.min(14.0, 4.0 + f * 0.55));
        setAttr(Attributes.MOVEMENT_SPEED, 0.30 + Math.min(0.11, f * 0.0055));
        setAttr(Attributes.SCALE, getColossalScale(this.floor));

        this.xpReward = (140 + (f * f * 24) + (milestone ? 600 : 0)) / 2;

        Component standalone = getStandaloneBossTitle();
        ChatFormatting nameColor = switch (this.bossEvent.getColor()) {
            case PURPLE -> ChatFormatting.LIGHT_PURPLE;
            case BLUE -> ChatFormatting.AQUA;
            case YELLOW -> ChatFormatting.GOLD;
            case GREEN -> ChatFormatting.GREEN;
            case RED -> ChatFormatting.RED;
            default -> ChatFormatting.WHITE;
        };
        Component bossTitle = standalone != null
                ? standalone
                : this.getType().getDescription().copy().withStyle(nameColor, ChatFormatting.BOLD);
        this.setCustomName(bossTitle);
        this.setCustomNameVisible(true);
        this.bossEvent.setName(bossTitle);
        this.triggerSummonEntrance();
    }

    protected void setAttr(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double val) {
        AttributeInstance inst = this.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(val);
        }
    }

    public int getFloor() { return floor; }
    public int getPhase() { return phase; }
    public boolean isDungeonBoss() { return dungeonBoss; }
    public BlockPos getArenaCenter() { return arenaCenter; }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // Auto-configure bosses spawned via /summon and upgrade scale of already-saved bosses!
        if (!this.configured) {
            configureForFloor(getDefaultSpawnEggFloor(), null, false);
        } else if (this.tickCount % 40 == 1) {
            float targetScale = getColossalScale(this.floor);
            AttributeInstance scaleInst = this.getAttribute(Attributes.SCALE);
            if (scaleInst != null && Math.abs(scaleInst.getBaseValue() - targetScale) > 0.05) {
                scaleInst.setBaseValue(targetScale);
            }
        }

        // Cinematic Multi-Stage Earth-Shattering Boss Emergence Animation (3.2 seconds — NO magic circle!)
        int spawnT = getSpawnAnimTicks();
        if (spawnT > 0) {
            this.setDeltaMovement(0, this.isFlyingBoss() ? 0 : Math.min(0, this.getDeltaMovement().y), 0);
            int rgb = getSummonColorRgb();

            // Sample actual ground block below the emerging boss so flying debris matches the exact temple/biome!
            BlockState groundState = serverLevel.getBlockState(this.blockPosition().below());
            if (groundState.isAir() || !groundState.getFluidState().isEmpty()) {
                groundState = Blocks.DEEPSLATE.defaultBlockState();
            }
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, groundState);

            if (!this.summonVfxStarted) {
                this.summonVfxStarted = true;
                // Stage 1: Subterranean seismic rumble & initial ground fracture (NO MagicCircle!)
                serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.5f, 0.62f);
                serverLevel.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.8f, 0.55f);
                serverLevel.sendParticles(debris, this.getX(), this.getY() + 0.25, this.getZ(), 70, 2.4, 0.35, 2.4, 0.22);
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 0.3, this.getZ(), 24, 2.0, 0.25, 2.0, 0.04);
                ModNetworking.sendShakeNearby(serverLevel, this.position(), 44.0, 1.55f, 20);
            }

            float prog = 1.0f - (spawnT / (float) SPAWN_ANIM_DURATION);

            // Stage 2: Continuous earth-rupturing rock fountains, dust billows & elemental cracks as the god heaves up!
            if (prog < 0.78f) {
                double ringR = 1.6 + prog * 2.8;
                int count = prog < 0.25f ? 14 : 24;
                for (int i = 0; i < 4; i++) {
                    double ang = this.random.nextDouble() * Math.PI * 2.0;
                    double rad = ringR * (0.45 + this.random.nextDouble() * 0.65);
                    double dx = this.getX() + Math.cos(ang) * rad;
                    double dz = this.getZ() + Math.sin(ang) * rad;
                    serverLevel.sendParticles(debris, dx, this.getY() + 0.25, dz, count / 4, 0.25, 0.55, 0.25, 0.30);
                }
                if (spawnT % 2 == 0) {
                    serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 0.4, this.getZ(),
                            5, ringR * 0.65, 0.35, ringR * 0.65, 0.05);
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 0.35, this.getZ(),
                            6, ringR * 0.7, 0.4, ringR * 0.7, 0.07);
                }
                if (spawnT % 6 == 0) {
                    serverLevel.playSound(null, this.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.9f, 0.52f + prog * 0.25f);
                    ModNetworking.sendShakeNearby(serverLevel, this.position(), 36.0, 0.85f + prog * 0.65f, 8);
                }
            }

            // Stage 3: Climax Ground-Break & Sky-Splitting Awakening Roar!
            if (spawnT == 18) {
                ShockwaveRingEntity.spawn(serverLevel, this.position().add(0, 0.25, 0), 17.0f, rgb, 18);
                serverLevel.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.6f, 0.66f);
                serverLevel.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5f, 0.78f);
                serverLevel.sendParticles(debris, this.getX(), this.getY() + 1.2, this.getZ(), 120, 3.0, 1.8, 3.0, 0.42);
                serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 2.2, this.getZ(), 2, 1.4, 1.2, 1.4, 0.05);
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 32, 2.8, 0.8, 2.8, 0.08);
                ModNetworking.sendShakeNearby(serverLevel, this.position(), 52.0, 2.6f, 20);
            }

            this.entityData.set(DATA_SPAWN_TICKS, spawnT - 1);
            if (spawnT - 1 <= 0) {
                this.entityData.set(DATA_CAST_STATE, 0);
                this.entityData.set(DATA_CAST_TICKS, 0);
                // Winged Sky Gods launch powerfully upward into open flight right after breaking free from the earth!
                if (this.isFlyingBoss()) {
                    this.setDeltaMovement(0, 0.85, 0);
                }
            }
            return;
        }

        // Tick cast animation timer
        int ct = getCastTicks();
        if (ct > 0) {
            this.entityData.set(DATA_CAST_TICKS, ct - 1);
            if (ct - 1 <= 0) {
                this.entityData.set(DATA_CAST_STATE, 0);
            }
        }

        // Tick delayed/telegraphed boss abilities
        Iterator<DelayedAction> it = scheduledActions.iterator();
        while (it.hasNext()) {
            DelayedAction action = it.next();
            if (action.onTick != null) {
                action.onTick.run();
            }
            action.ticksLeft--;
            if (action.ticksLeft <= 0) {
                if (action.onTrigger != null) {
                    action.onTrigger.run();
                }
                it.remove();
            }
        }

        // Arena tether
        if (arenaCenter != null && this.blockPosition().distSqr(arenaCenter) > 36 * 36) {
            this.teleportTo(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1.0, arenaCenter.getZ() + 0.5);
            this.setDeltaMovement(Vec3.ZERO);
        }

        // Phase transitions
        float ratio = this.getHealth() / this.getMaxHealth();
        int desiredPhase = computePhase(ratio);
        if (desiredPhase > this.phase) {
            this.phase = desiredPhase;
            this.entityData.set(DATA_PHASE, this.phase);
            onPhaseTransition(serverLevel, this.phase);
        }

        if (this.tickCount % 4 == 0) {
            spawnAmbientAura(serverLevel);
        }

        LivingEntity target = this.getTarget();
        boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 0.0012;
        if (target == null && !moving && this.getCastState() == 0 && this.hurtTime <= 0) {
            idleRestTicks = Math.min(200, idleRestTicks + 1);
        } else {
            idleRestTicks = 0;
        }
        boolean shouldRest = idleRestTicks >= 55;
        if (this.entityData.get(DATA_RESTING) != shouldRest) {
            this.entityData.set(DATA_RESTING, shouldRest);
        }

        if (target != null && target.isAlive()) {
            if (skillCooldown > 0) {
                skillCooldown--;
            } else {
                skillCooldown = executeBossSkill(serverLevel, target);
            }
        }
    }

    protected int computePhase(float hpRatio) {
        return hpRatio <= 0.55f ? 2 : 1;
    }

    protected void onPhaseTransition(ServerLevel level, int newPhase) {
        startCastAnimation(3, 30);
        level.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5f, 0.85f);
        spawnParticleRing(level, this.position().add(0, 1.2, 0), ParticleTypes.SOUL_FIRE_FLAME, 6.0, 64);
        spawnParticleRing(level, this.position().add(0, 2.2, 0), ParticleTypes.END_ROD, 4.5, 48);
        damagePlayersInRadius(level, this.position(), 7.0, (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.9), 0.7);
        announceToNearby(level, Component.translatable("message.abyssalrpg.boss_phase", this.getDisplayName(), newPhase)
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    protected abstract int executeBossSkill(ServerLevel level, LivingEntity target);

    protected void spawnAmbientAura(ServerLevel level) {
        level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 1.5, this.getZ(), 2, 0.6, 0.8, 0.6, 0.01);
    }

    protected void spawnParticleRing(ServerLevel level, Vec3 center, ParticleOptions particle, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i) / count;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            level.sendParticles(particle, x, center.y, z, 2, 0.08, 0.15, 0.08, 0.02);
        }
    }

    protected void spawnParticleBeam(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, double step) {
        Vec3 diff = to.subtract(from);
        double len = diff.length();
        Vec3 dir = diff.normalize();
        for (double d = 0; d <= len; d += step) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(particle, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.01);
        }
    }

    protected void spawnPillar(ServerLevel level, Vec3 base, ParticleOptions particle, double height, int density) {
        for (double y = 0; y <= height; y += 0.45) {
            level.sendParticles(particle, base.x, base.y + y, base.z, density, 0.35, 0.15, 0.35, 0.03);
        }
    }

    protected void damagePlayersInRadius(ServerLevel level, Vec3 center, double radius, float damage, double knockUp) {
        AABB box = new AABB(center.x - radius, center.y - 3, center.z - radius,
                center.x + radius, center.y + 5, center.z + radius);
        for (Player player : level.getEntitiesOfClass(Player.class, box, p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
            if (player.distanceToSqr(center) <= radius * radius) {
                player.hurt(this.damageSources().mobAttack(this), damage);
                if (knockUp > 0) {
                    Vec3 away = player.position().subtract(center);
                    double len = Math.max(0.1, away.length());
                    player.setDeltaMovement(player.getDeltaMovement().add(away.x / len * 0.9, knockUp, away.z / len * 0.9));
                    player.hurtMarked = true;
                }
            }
        }
    }

    protected void damagePlayersAlongLine(ServerLevel level, Vec3 start, Vec3 dir, double length, double width, float damage, double knockUp) {
        for (double d = 1.0; d <= length; d += 1.5) {
            Vec3 pt = start.add(dir.scale(d));
            damagePlayersInRadius(level, pt, width, damage, knockUp);
        }
    }

    public static void scaleSummonedMinion(Mob mob, int tier, boolean elite) {
        int clamped = Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
        double hpMult = (1.0 + (clamped - 1) * 0.28) * (elite ? 1.45 : 1.0);
        double dmgMult = (1.0 + (clamped - 1) * 0.16) * (elite ? 1.20 : 1.0);
        double armorBonus = Math.min(12.0, (clamped - 1) * 0.65) + (elite ? 3.0 : 0.0);

        AttributeInstance maxHp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.removeModifier(MINION_HP_MOD);
            maxHp.addPermanentModifier(new AttributeModifier(MINION_HP_MOD, hpMult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            mob.setHealth(mob.getMaxHealth());
        }
        AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.removeModifier(MINION_DMG_MOD);
            dmg.addPermanentModifier(new AttributeModifier(MINION_DMG_MOD, dmgMult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(MINION_ARMOR_MOD);
            armor.addPermanentModifier(new AttributeModifier(MINION_ARMOR_MOD, armorBonus, AttributeModifier.Operation.ADD_VALUE));
        }
        if (elite) {
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        }
        mob.setPersistenceRequired();
    }

    protected void summonFloorMinions(ServerLevel level, int count) {
        for (int i = 0; i < count; i++) {
            EntityType<?> chosen = DEFAULT_MINION_POOL.get(level.random.nextInt(DEFAULT_MINION_POOL.size()));
            Entity entity = chosen.create(level);
            if (entity instanceof Mob mob) {
                double angle = (Math.PI * 2.0 * i) / Math.max(1, count);
                double sx = this.getX() + Math.cos(angle) * 4.5;
                double sz = this.getZ() + Math.sin(angle) * 4.5;
                mob.moveTo(sx, this.getY() + 0.5, sz, level.random.nextFloat() * 360f, 0f);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
                scaleSummonedMinion(mob, this.floor, i == 0);
                level.addFreshEntity(mob);
                level.sendParticles(ParticleTypes.PORTAL, sx, this.getY() + 1.0, sz, 20, 0.35, 0.6, 0.35, 0.06);
            }
        }
    }

    protected void announceToNearby(ServerLevel level, Component msg) {
        AABB box = this.getBoundingBox().inflate(52.0);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            player.displayClientMessage(msg, false);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isSpawningIn() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false; // Invulnerable during dramatic summon emergence (still allows /kill)!
        }
        return super.hurt(source, amount);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        // Custom Ornate RPG Boss Bar HUD renders directly on client — keep vanilla BossBar hidden!
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        if (name != null) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    /**
     * Hook for multi-entity bosses (such as Cerberus Phase 2) to identify the final defeating entity.
     */
    protected boolean shouldAwardBossDefeatRewards() {
        return true;
    }

    @Override
    public void die(DamageSource damageSource) {
        this.scheduledActions.clear();
        super.die(damageSource);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("AbyssConfigured", this.configured);
        tag.putInt("AbyssFloor", this.floor);
        tag.putInt("AbyssPhase", this.phase);
        tag.putBoolean("DungeonBoss", this.dungeonBoss);
        if (this.arenaCenter != null) {
            tag.putInt("ArenaX", this.arenaCenter.getX());
            tag.putInt("ArenaY", this.arenaCenter.getY());
            tag.putInt("ArenaZ", this.arenaCenter.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.configured = tag.getBoolean("AbyssConfigured");
        if (tag.contains("AbyssFloor")) {
            this.floor = Math.max(MIN_TIER, Math.min(MAX_TIER, tag.getInt("AbyssFloor")));
        }
        if (tag.contains("AbyssPhase")) {
            this.phase = Math.max(1, tag.getInt("AbyssPhase"));
            this.entityData.set(DATA_PHASE, this.phase);
        }
        this.dungeonBoss = tag.getBoolean("DungeonBoss");
        if (tag.contains("ArenaX")) {
            this.arenaCenter = new BlockPos(tag.getInt("ArenaX"), tag.getInt("ArenaY"), tag.getInt("ArenaZ"));
        }
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }
}
