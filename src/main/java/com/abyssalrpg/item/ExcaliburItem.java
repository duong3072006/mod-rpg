package com.abyssalrpg.item;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.combat.ExcaliburCombatHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * **Excalibur — Sword of Promised Victory** & corrupted form **Excalibur Morgan (Saber Alter)**:
 * - **Normal Form (Golden Excalibur):** 36.0 Attack Damage, 1.8 Attack Speed, 3.75 Reach, 3-Hit Golden Combo,
 *   RMB `'EXCALIBUR!'` Two-Handed Vertical Golden Holy Beam (14 blocks).
 * - **Awakened Form (Excalibur Morgan — 4.0s `[R]` Charge, 20s Duration, 35s Cooldown):**
 *   +40% Damage Surge (`50.4` Attack Damage), 100% Knockback Resistance, Resistance II,
 *   Instant 40% Max HP Heal + Regeneration II (5s), Status Immunity (Poison/Wither/Slowness/Weakness),
 *   and RMB `'EXCALIBUR MORGAN!'` Pitch-Black & Dark-Violet Energy Beam (18 blocks, wider AoE, Wither + Slowness).
 */
public class ExcaliburItem extends Item implements GeoItem {
    public static final double GOLDEN_ATTACK_DAMAGE = 36.0;
    public static final double MORGAN_ATTACK_DAMAGE = GOLDEN_ATTACK_DAMAGE * 1.40; // +40% Damage Surge = 50.4!
    public static final double ATTACK_SPEED = 1.8;
    public static final int MAX_DURABILITY = 2500;

    public static final String TAG_MORGAN = "ExcaliburMorgan";
    public static final String TAG_MORGAN_END_TICK = "MorganEndTick";
    public static final String TAG_MORGAN_CD_UNTIL = "MorganCooldownUntil";
    public static final String TAG_ULT_CD_UNTIL = "UltCooldownUntil";
    public static final String TAG_ENERGY = "ExcaliburEnergy";

    private static final ResourceLocation REACH_MOD_ID = AbyssalRpgMod.id("excalibur_reach");
    private static final ResourceLocation KB_RESIST_MOD_ID = AbyssalRpgMod.id("excalibur_morgan_kb_resist");

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.excalibur.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ExcaliburItem() {
        super(new Properties()
                .stacksTo(1)
                .durability(MAX_DURABILITY)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .attributes(createAttributes(false)));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static ItemAttributeModifiers createAttributes(boolean morgan) {
        double damage = morgan ? MORGAN_ATTACK_DAMAGE : GOLDEN_ATTACK_DAMAGE;
        double bonusReach = morgan ? 1.25 : 0.75;
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage - 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED - 4.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(REACH_MOD_ID, bonusReach, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                );
        if (morgan) {
            builder.add(
                    Attributes.KNOCKBACK_RESISTANCE,
                    new AttributeModifier(KB_RESIST_MOD_ID, 1.0, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND
            );
        }
        return builder.build();
    }

    private static CompoundTag readTag(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    private static void writeTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isMorgan(ItemStack stack) {
        return !stack.isEmpty() && readTag(stack).getBoolean(TAG_MORGAN);
    }

    public static void setMorganState(ItemStack stack, boolean morgan, long morganEndTick, long cooldownUntilTick) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = readTag(stack);
        tag.putBoolean(TAG_MORGAN, morgan);
        tag.putLong(TAG_MORGAN_END_TICK, morganEndTick);
        if (cooldownUntilTick > 0L) {
            tag.putLong(TAG_MORGAN_CD_UNTIL, cooldownUntilTick);
        }
        writeTag(stack, tag);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, createAttributes(morgan));
    }

    public static long getMorganEndTick(ItemStack stack) {
        return stack.isEmpty() ? 0L : readTag(stack).getLong(TAG_MORGAN_END_TICK);
    }

    public static long getMorganCooldownUntil(ItemStack stack) {
        return stack.isEmpty() ? 0L : readTag(stack).getLong(TAG_MORGAN_CD_UNTIL);
    }

    public static long getUltCooldownUntil(ItemStack stack) {
        return stack.isEmpty() ? 0L : readTag(stack).getLong(TAG_ULT_CD_UNTIL);
    }

    public static void setUltCooldownUntil(ItemStack stack, long tick) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = readTag(stack);
        tag.putLong(TAG_ULT_CD_UNTIL, tick);
        writeTag(stack, tag);
    }

    public static float getEnergy(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0F;
        }
        CompoundTag tag = readTag(stack);
        if (!tag.contains(TAG_ENERGY)) {
            return 100.0F; // Bắt đầu sẵn sàng 100% khi mới nhận kiếm để người chơi thử ngay!
        }
        return net.minecraft.util.Mth.clamp(tag.getFloat(TAG_ENERGY), 0.0F, 100.0F);
    }

    public static void setEnergy(ItemStack stack, float value) {
        if (stack.isEmpty()) {
            return;
        }
        float clamped = net.minecraft.util.Mth.clamp(value, 0.0F, 100.0F);
        CompoundTag tag = readTag(stack);
        if (tag.contains(TAG_ENERGY) && Math.abs(tag.getFloat(TAG_ENERGY) - clamped) < 0.05F) {
            return;
        }
        tag.putFloat(TAG_ENERGY, clamped);
        writeTag(stack, tag);
    }

    public static void addEnergy(ItemStack stack, float delta) {
        setEnergy(stack, getEnergy(stack) + delta);
    }

    /**
     * Ngăn vanilla tụt tay & giật kiếm (re-equip dip/jitter) mỗi khi cập nhật thanh năng lượng,
     * hồi chiêu hay trạng thái biến hình trên DataComponents.CUSTOM_DATA!
     */
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return !oldStack.is(com.abyssalrpg.registry.ModItems.EXCALIBUR)
                || !newStack.is(com.abyssalrpg.registry.ModItems.EXCALIBUR);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (isMorgan(stack)) {
            return Component.translatable("item.abyssalrpg.excalibur_morgan")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }
        return Component.translatable("item.abyssalrpg.excalibur")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack);
    }

    @Override
    public int getEnchantmentValue() {
        return 22;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ExcaliburCombatHandler.tryStartUltimate(sp);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean morgan = isMorgan(stack);
        int energyPct = Math.round(getEnergy(stack));
        if (morgan) {
            tooltip.add(Component.literal("✦ ").withStyle(ChatFormatting.DARK_RED)
                    .append(Component.translatable("tooltip.abyssalrpg.excalibur.morgan_form")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            tooltip.add(Component.translatable("tooltip.abyssalrpg.excalibur.morgan_buffs")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable("tooltip.abyssalrpg.excalibur.golden_form")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
            tooltip.add(Component.literal("✦ Năng lượng biến đổi: " + energyPct + "%")
                    .withStyle(energyPct >= 100 ? ChatFormatting.RED : ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.abyssalrpg.excalibur.controls_rmb")
                .withStyle(morgan ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.excalibur.controls_r")
                .withStyle(ChatFormatting.RED));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle_controller", 2, state -> state.setAndContinue(IDLE_ANIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
