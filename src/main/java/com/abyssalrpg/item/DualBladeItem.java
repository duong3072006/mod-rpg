package com.abyssalrpg.item;

import com.abyssalrpg.combat.DualBladeCombatHandler;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.registry.ModEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Song Đao Hai Tay Netherite–Kim Cương (`abyssalrpg:dual_blade`) — Cầm Thẳng
 * Tay Phải + Cầm Ngược Tay Trái:
 * - Chỉ số cơ bản: `30.0` Attack Damage, `2.5` Attack Speed (`8` ticks/đòn), độ
 * bền `1561`, Bleeding `abyssalrpg:bleeding`.
 * - Chuột Trái: Combo 5 nhịp EpicFight-Nightfall + Hold-to-Slash + 4 Enchant
 * chuyên biệt (`Lifesteal`, `Armor Pierce`, `Critical Strike`, `Deep Wounds`).
 * - Chuột Phải (`use`): Chiêu thức "Lướt Chém 10 Nhát" (`Netherite–Diamond
 * Flash Dash`) — nén đà, phóng `18` block
 * xuyên kẻ địch/boss (chặn tường rắn), nổ 10 hit tức thì `×1.5` dame cơ bản +
 * xuyên `10%` phòng thủ (cộng dồn `Armor Pierce`).
 */
public final class DualBladeItem extends Item implements GeoItem {
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("dual_knife.idle");

    public static volatile BooleanSupplier clientIsOffhandOccupied = () -> false;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @SuppressWarnings("this-escape")
    public DualBladeItem() {
        super(createProperties());
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    private static Item.Properties createProperties() {
        WeaponsConfig cfg = WeaponsConfig.get();
        return new Item.Properties()
                .durability(cfg.dualBladeDurability)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .attributes(createAttributeModifiers(cfg.dualBladeDamage, cfg.dualBladeAttackSpeed))
                .component(DataComponents.TOOL, createToolProperties());
    }

    public static ItemAttributeModifiers createAttributeModifiers(double totalDamage, double totalAttackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                Item.BASE_ATTACK_DAMAGE_ID,
                                totalDamage - 1.0,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                Item.BASE_ATTACK_SPEED_ID,
                                totalAttackSpeed - 4.0,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static Tool createToolProperties() {
        return new Tool(
                List.of(
                        Tool.Rule.minesAndDrops(List.of(Blocks.COBWEB), 15.0F),
                        Tool.Rule.overrideSpeed(BlockTags.SWORD_EFFICIENT, 1.5F)),
                1.0F,
                2);
    }

    /**
     * Kích hoạt chiêu Chuột Phải: Lướt Chém 10 Block — 10 Hit Tức Thì
     * (`Crimson-Gold Flash Dash`).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (WeaponsConfig.get().requireEmptyOffhand && !player.getOffhandItem().isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.abyssalrpg.dual_knife.dash_need_empty_offhand")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (player.getCooldowns().isOnCooldown(this) || DualBladeCombatHandler.isPlayerDashing(player)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            boolean started = DualBladeCombatHandler.startFlashDash(serverPlayer, stack);
            if (!started) {
                return InteractionResultHolder.pass(stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(ModEnchantments.DUAL_KNIFE_REPAIR_ITEMS) || super.isValidRepairItem(toRepair, repair);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WeaponsConfig cfg = WeaponsConfig.get();
        tooltip.add(Component.translatable("tooltip.abyssalrpg.dual_knife.two_handed")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.dual_knife.bleeding")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.dual_knife.combo")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.dual_knife.flash_dash",
                Math.round(cfg.dashDistance),
                cfg.dashHitCount,
                String.format(java.util.Locale.ROOT, "%.1f", cfg.dashDamageMultiplier),
                Math.round(cfg.dashArmorPierceBonus * 100.0F),
                String.format(java.util.Locale.ROOT, "%.0f", cfg.dashCooldownTicks / 20.0))
                .withStyle(ChatFormatting.AQUA));

        if (cfg.requireEmptyOffhand) {
            boolean occupied = false;
            try {
                occupied = clientIsOffhandOccupied != null && clientIsOffhandOccupied.getAsBoolean();
            } catch (Throwable ignored) {
            }
            if (occupied) {
                tooltip.add(Component.translatable("tooltip.abyssalrpg.dual_knife.offhand_warning",
                        String.format(java.util.Locale.ROOT, "%.1f", cfg.offhandOccupiedAttackSpeed))
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            } else {
                tooltip.add(Component.translatable("tooltip.abyssalrpg.dual_knife.offhand_hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers
                .add(new AnimationController<>(this, "idle_controller", 2, state -> state.setAndContinue(ANIM_IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
