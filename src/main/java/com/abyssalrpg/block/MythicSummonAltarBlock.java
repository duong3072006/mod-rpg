package com.abyssalrpg.block;

import com.abyssalrpg.block.entity.AltarEyeBlockEntity;
import com.abyssalrpg.entity.AbstractAbyssalBoss;
import com.abyssalrpg.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dedicated Mythological Temple Summoning Altar.
 * Each temple binds this altar to one of the 7 Mythological Titan Gods (0..6):
 *   0 = Quetzalcoatl (Aztec Feathered Sky God)
 *   1 = Apep (Egyptian Chaos Serpent)
 *   2 = Fenrir (Unbound Norse Wolf Titan)
 *   3 = Hydra (Seven-Headed Lernaean Serpent)
 *   4 = Jormungandr (The World Serpent)
 *   5 = Tiamat (Primordial 7-Headed Dragon Queen)
 *   6 = Leviathan (Abyssal Deep-Sea Titan)
 */
public class MythicSummonAltarBlock extends Block implements EntityBlock {
    public static final int GOD_QUETZALCOATL = 0;
    public static final int GOD_APEP = 1;
    public static final int GOD_FENRIR = 2;
    public static final int GOD_HYDRA = 3;
    public static final int GOD_JORMUNGANDR = 4;
    public static final int GOD_TIAMAT = 5;
    public static final int GOD_LEVIATHAN = 6;
    public static final int GOD_COUNT = 7;

    public static final IntegerProperty BOSS_TYPE = IntegerProperty.create("boss_type", 0, 6);

    @SuppressWarnings("this-escape")
    public MythicSummonAltarBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(BOSS_TYPE, GOD_QUETZALCOATL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BOSS_TYPE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AltarEyeBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int god = state.getValue(BOSS_TYPE);
        ParticleOptions p = switch (god) {
            case GOD_QUETZALCOATL -> ParticleTypes.HAPPY_VILLAGER;
            case GOD_APEP -> ParticleTypes.FLAME;
            case GOD_FENRIR -> ParticleTypes.SOUL_FIRE_FLAME;
            case GOD_HYDRA -> ParticleTypes.SOUL_FIRE_FLAME;
            case GOD_JORMUNGANDR -> ParticleTypes.SNOWFLAKE;
            case GOD_TIAMAT -> ParticleTypes.DRAGON_BREATH;
            default -> ParticleTypes.GLOW;
        };
        // Swirling outer halo particle ring around the floating 3D Eye!
        double angle = (level.getGameTime() * 0.22 + random.nextDouble() * 1.4) % (Math.PI * 2.0);
        double rad = 0.68 + random.nextDouble() * 0.28;
        double px = pos.getX() + 0.5 + Math.cos(angle) * rad;
        double py = pos.getY() + 1.35 + (random.nextDouble() - 0.5) * 0.55;
        double pz = pos.getZ() + 0.5 + Math.sin(angle) * rad;
        level.addParticle(p, px, py, pz, -Math.sin(angle) * 0.03, 0.02, Math.cos(angle) * 0.03);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.5 + Math.cos(angle + Math.PI) * 0.55,
                    pos.getY() + 1.50 + (random.nextDouble() - 0.5) * 0.35,
                    pos.getZ() + 0.5 + Math.sin(angle + Math.PI) * 0.55,
                    0.0, 0.03, 0.0);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            int bossType = state.getValue(BOSS_TYPE);

            // Shift + Right-Click in Creative cycles which Mythological God is bound to this Altar
            if (serverPlayer.isShiftKeyDown() && serverPlayer.isCreative()) {
                int next = (bossType + 1) % GOD_COUNT;
                serverLevel.setBlock(pos, state.setValue(BOSS_TYPE, next), Block.UPDATE_ALL);
                serverLevel.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.15f);
                serverPlayer.sendSystemMessage(Component.literal("✦ Mythic Altar attuned to: " + getGodTitle(next))
                        .withStyle(getGodColor(next), ChatFormatting.BOLD));
                return InteractionResult.SUCCESS;
            }

            EntityType<? extends AbstractAbyssalBoss> bossEntityType = getGodEntityType(bossType);

            // Guard against duplicate summons while the temple's god is already alive nearby
            AABB checkBox = new AABB(pos).inflate(56.0);
            List<? extends AbstractAbyssalBoss> existing = serverLevel.getEntities(
                    bossEntityType,
                    checkBox,
                    AbstractAbyssalBoss::isAlive
            );
            if (!existing.isEmpty()) {
                serverPlayer.sendSystemMessage(Component.literal("✖ " + getGodTitle(bossType) + " is already awakened nearby!")
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }

            AbstractAbyssalBoss boss = bossEntityType.create(serverLevel);
            if (boss != null) {
                // Shatter & remove the Mythic Summon Altar pedestal completely as the earth splits open!
                BlockState brokenAltar = serverLevel.getBlockState(pos);
                BlockState groundBeneath = serverLevel.getBlockState(pos.below());
                serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

                serverLevel.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 2.0f, 0.65f);
                serverLevel.playSound(null, pos, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 2.2f, 0.55f);
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, brokenAltar),
                        pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                        55, 0.65, 0.65, 0.65, 0.22);
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                                groundBeneath.isAir() ? Blocks.DEEPSLATE.defaultBlockState() : groundBeneath),
                        pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
                        75, 1.8, 0.45, 1.8, 0.28);

                // Spawn the Mythic God grounded at the exact shattered altar center so it bursts UP out of the earth!
                double spawnZ = pos.getZ() + 0.5 + (bossType == GOD_QUETZALCOATL ? 2.0 : 0.0);
                boss.moveTo(pos.getX() + 0.5, pos.getY() + 0.05, spawnZ, serverPlayer.getYRot() + 180.0f, 0.0f);
                boss.configureForFloor(getGodFloor(bossType), pos, false);
                serverLevel.addFreshEntity(boss);

                // Visual lightning strike splitting the sky as the altar shatters
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                if (bolt != null) {
                    bolt.moveTo(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5);
                    bolt.setVisualOnly(true);
                    serverLevel.addFreshEntity(bolt);
                }

                serverLevel.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 2.2f, 0.68f);

                Component announce = Component.literal("☠ THE EARTH SHATTERS — " + getGodTitle(bossType) + " RISES FROM BELOW!")
                        .withStyle(getGodColor(bossType), ChatFormatting.BOLD);
                for (ServerPlayer nearby : serverLevel.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(72.0))) {
                    nearby.sendSystemMessage(announce);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static EntityType<? extends AbstractAbyssalBoss> getGodEntityType(int bossType) {
        return switch (Math.floorMod(bossType, GOD_COUNT)) {
            case GOD_QUETZALCOATL -> ModEntities.QUETZALCOATL_BOSS;
            case GOD_APEP -> ModEntities.APEP_BOSS;
            case GOD_FENRIR -> ModEntities.FENRIR_BOSS;
            case GOD_HYDRA -> ModEntities.HYDRA_BOSS;
            case GOD_JORMUNGANDR -> ModEntities.JORMUNGANDR_BOSS;
            case GOD_TIAMAT -> ModEntities.TIAMAT_BOSS;
            default -> ModEntities.LEVIATHAN_BOSS;
        };
    }

    public static int getGodFloor(int bossType) {
        return switch (Math.floorMod(bossType, GOD_COUNT)) {
            case GOD_QUETZALCOATL -> 14;
            case GOD_APEP -> 14;
            case GOD_FENRIR -> 15;
            case GOD_HYDRA -> 15;
            case GOD_JORMUNGANDR -> 16;
            case GOD_TIAMAT -> 17;
            default -> 18;
        };
    }

    public static String getGodKey(int bossType) {
        return switch (Math.floorMod(bossType, GOD_COUNT)) {
            case GOD_QUETZALCOATL -> "quetzalcoatl";
            case GOD_APEP -> "apep";
            case GOD_FENRIR -> "fenrir";
            case GOD_HYDRA -> "hydra";
            case GOD_JORMUNGANDR -> "jormungandr";
            case GOD_TIAMAT -> "tiamat";
            default -> "leviathan";
        };
    }

    public static String getGodTitle(int bossType) {
        return switch (Math.floorMod(bossType, GOD_COUNT)) {
            case GOD_QUETZALCOATL -> "QUETZALCOATL (Aztec Sky God)";
            case GOD_APEP -> "APEP (Egyptian Chaos Serpent)";
            case GOD_FENRIR -> "FENRIR (Norse Wolf Titan)";
            case GOD_HYDRA -> "SPIRIT HYDRA (Nine-Headed Spectral Titan)";
            case GOD_JORMUNGANDR -> "JÖRMUNGANDR (The World Serpent)";
            case GOD_TIAMAT -> "TIAMAT (7-Headed Dragon Queen)";
            default -> "LEVIATHAN (Abyssal Sea Titan)";
        };
    }

    public static ChatFormatting getGodColor(int bossType) {
        return switch (Math.floorMod(bossType, GOD_COUNT)) {
            case GOD_QUETZALCOATL -> ChatFormatting.GREEN;
            case GOD_APEP -> ChatFormatting.GOLD;
            case GOD_FENRIR -> ChatFormatting.AQUA;
            case GOD_HYDRA -> ChatFormatting.AQUA;
            case GOD_JORMUNGANDR -> ChatFormatting.BLUE;
            case GOD_TIAMAT -> ChatFormatting.DARK_RED;
            default -> ChatFormatting.DARK_AQUA;
        };
    }

    public static int parseGodType(String key) {
        return switch (key.toLowerCase()) {
            case "quetzalcoatl", "aztec" -> GOD_QUETZALCOATL;
            case "apep", "egypt", "egyptian" -> GOD_APEP;
            case "fenrir", "norse", "wolf" -> GOD_FENRIR;
            case "hydra", "greek", "swamp" -> GOD_HYDRA;
            case "jormungandr", "world_serpent", "serpent" -> GOD_JORMUNGANDR;
            case "tiamat", "dragon", "babylon" -> GOD_TIAMAT;
            case "leviathan", "ocean", "sea" -> GOD_LEVIATHAN;
            default -> -1;
        };
    }
}

