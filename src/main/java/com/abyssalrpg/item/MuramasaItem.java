package com.abyssalrpg.item;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.combat.MuramasaCombatHandler;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * **Yêu Đao Muramasa Katana (`abyssalrpg:muramasa`)** — Dựng 1:1 theo Sketchfab
 * "Muramasa Katana" (Samurai_Chad, `02d5eb68f2854f2e977a78aa38bdf34a`):
 * - Thép rèn đen Damascus khắc chữ `村正` ánh kim, cạnh lưỡi phát sáng vàng rực (`#FFE820`),
 *   tsuba sao quỷ 12 mũi sắt cổ, chuôi bọc da đỏ máu quấn vải đen đan quả trám & vỏ kiếm rồng đỏ ở tay trái!
 * - **Combo 3 Nhịp Katana (LMB):** Kesagiri -> Gyaku-Kesa -> Demon Cross-Cleave (hút máu & chảy máu).
 * - **Nhất Đao Huyết Nguyện (RMB, hồi 5.5s):** Rút kiếm Iaijutsu lướt xuyên 9 block chém sóng vàng-đỏ 14 block!
 * - **Huyết Thệ Yêu Đao (`[R]` / `Shift+RMB`, 12s, hồi 28s):** Hiến tế 2 tim giải phóng yêu quỷ Muramasa
 *   (+35% sát thương, Tốc Độ II, 15% Hút Máu & hào quang lưỡi vàng-đỏ rực cháy!).
 */
public final class MuramasaItem extends Item implements GeoItem {
    public static final double BASE_ATTACK_DAMAGE = 34.0;
    public static final double AWAKENED_ATTACK_DAMAGE = BASE_ATTACK_DAMAGE * 1.35; // 45.9 DMG!
    public static final double ATTACK_SPEED = 2.2;
    public static final double BONUS_REACH = 1.0;
    public static final int MAX_DURABILITY = 2200;

    public static final String TAG_AWAKENED = "MuramasaAwakened";
    public static final String TAG_AWAKEN_END = "MuramasaAwakenEnd";
    public static final String TAG_AWAKEN_CD = "MuramasaAwakenCd";
    public static final String TAG_IAI_CD = "MuramasaIaiCd";

    private static final ResourceLocation REACH_MOD_ID = AbyssalRpgMod.id("muramasa_reach");

    public static Supplier<String> clientAwakenKeyName = () -> "R";
    public static BooleanSupplier clientIsOffhandOccupied = () -> false;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MuramasaItem() {
        super(new Properties()
                .stacksTo(1)
                .durability(MAX_DURABILITY)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .attributes(createAttributes(false)));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static ItemAttributeModifiers createAttributes(boolean awakened) {
        double dmg = awakened ? AWAKENED_ATTACK_DAMAGE : BASE_ATTACK_DAMAGE;
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, dmg - 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED - 4.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(REACH_MOD_ID, awakened ? BONUS_REACH + 0.5 : BONUS_REACH,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .build();
    }

    private static CompoundTag readTag(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    private static void writeTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isAwakened(ItemStack stack) {
        return !stack.isEmpty() && readTag(stack).getBoolean(TAG_AWAKENED);
    }

    public static void setAwakenedState(ItemStack stack, boolean awakened, long endTick, long cdUntilTick) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = readTag(stack);
        tag.putBoolean(TAG_AWAKENED, awakened);
        tag.putLong(TAG_AWAKEN_END, endTick);
        if (cdUntilTick > 0L) {
            tag.putLong(TAG_AWAKEN_CD, cdUntilTick);
        }
        writeTag(stack, tag);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, createAttributes(awakened));
    }

    public static long getAwakenEndTick(ItemStack stack) {
        return stack.isEmpty() ? 0L : readTag(stack).getLong(TAG_AWAKEN_END);
    }

    public static long getAwakenCooldownUntil(ItemStack stack) {
        return stack.isEmpty() ? 0L : readTag(stack).getLong(TAG_AWAKEN_CD);
    }

    public static long getIaiCooldownUntil(ItemStack stack) {
        return stack.isEmpty() ? 0L : readTag(stack).getLong(TAG_IAI_CD);
    }

    public static void setIaiCooldownUntil(ItemStack stack, long tick) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = readTag(stack);
        tag.putLong(TAG_IAI_CD, tick);
        writeTag(stack, tag);
    }

    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return !oldStack.is(ModItems.MURAMASA) || !newStack.is(ModItems.MURAMASA);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (isAwakened(stack)) {
            return Component.translatable("item.abyssalrpg.muramasa.awakened")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }
        return Component.translatable("item.abyssalrpg.muramasa")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
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
        if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            if (sp.isShiftKeyDown()) {
                MuramasaCombatHandler.tryActivateBloodOath(sp, sl);
            } else {
                MuramasaCombatHandler.tryCastIaijutsu(sp, sl);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean awakened = isAwakened(stack);
        String keyR = clientAwakenKeyName.get();
        tooltip.add(Component.translatable(awakened
                        ? "tooltip.abyssalrpg.muramasa.subtitle_awakened"
                        : "tooltip.abyssalrpg.muramasa.subtitle")
                .withStyle(awakened ? ChatFormatting.RED : ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.muramasa.combo")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.muramasa.iaijutsu")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.muramasa.blood_oath", keyR)
                .withStyle(ChatFormatting.DARK_RED));
        if (clientIsOffhandOccupied.getAsBoolean()) {
            tooltip.add(Component.translatable("tooltip.abyssalrpg.muramasa.scabbard_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Static rock-solid model (0 GeckoLib bone wobble!)
        controllers.add(new AnimationController<>(this, "muramasa_static", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
