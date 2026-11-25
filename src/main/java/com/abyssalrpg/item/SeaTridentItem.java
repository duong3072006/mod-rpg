package com.abyssalrpg.item;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.combat.SeaWeaponsCombatHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
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
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * **Hải Thần Đinh Ba & Đăng Hải (`abyssalrpg:sea_trident`)** — Cặp vũ khí biển song thủ GeckoLib 4.8.3:
 * - **Tay Phải:** Đinh Ba Hải Thần (dải sóng nước xoắn 360° quanh thân) — Combo 3 nhịp tầm xa (`+1.25` block)
 *   + Kỹ năng **`[R]` / `Shift + Chuột Phải`**: **Triệu Hồi Xoáy Nước Đại Dương** (hút xoáy mục tiêu 4s rồi phun cột nước nổ tung!).
 * - **Tay Trái (khi ô tay phụ trống):** Đèn Lồng Biển Sâu (rêu tảo & lõi lam phát sáng) — Kỹ năng **`Chuột Phải`**:
 *   **Triệu Hồi Sóng Triều** (dựng tường sóng cuộn 18 block cuốn phăng kẻ địch, dập lửa & làm chậm!).
 * - **Cộng Hưởng Bão Biển (Maelstrom Combo):** Khi **Sóng Triều (Đèn)** lao xuyên qua **Xoáy Nước (Đinh Ba)** đang hoạt động,
 *   kích nổ **Siêu Xoáy Đại Dương** tăng `+60%` sát thương nổ tung diện rộng!
 */
public final class SeaTridentItem extends Item implements GeoItem {
    public static final double ATTACK_DAMAGE = 34.0;
    public static final double ATTACK_SPEED = 2.0;
    public static final double BONUS_REACH = 1.25;
    public static final int MAX_DURABILITY = 2400;

    public static final String TAG_WAVE_CD_UNTIL = "SeaWaveCdUntil";
    public static final String TAG_WHIRLPOOL_CD_UNTIL = "SeaWhirlpoolCdUntil";

    private static final ResourceLocation REACH_MOD_ID = AbyssalRpgMod.id("sea_trident_reach");
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.sea_weapons.idle");

    public static Supplier<String> clientWhirlpoolKeyName = () -> "R";
    public static BooleanSupplier clientIsOffhandOccupied = () -> false;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SeaTridentItem() {
        super(new Properties()
                .stacksTo(1)
                .durability(MAX_DURABILITY)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .attributes(createAttributes()));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE - 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED - 4.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(REACH_MOD_ID, BONUS_REACH, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .build();
    }

    public static long getWaveCooldownUntil(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null ? cd.copyTag().getLong(TAG_WAVE_CD_UNTIL) : 0L;
    }

    public static void setWaveCooldownUntil(ItemStack stack, long tick) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putLong(TAG_WAVE_CD_UNTIL, tick);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static long getWhirlpoolCooldownUntil(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null ? cd.copyTag().getLong(TAG_WHIRLPOOL_CD_UNTIL) : 0L;
    }

    public static void setWhirlpoolCooldownUntil(ItemStack stack, long tick) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putLong(TAG_WHIRLPOOL_CD_UNTIL, tick);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            boolean triggered;
            if (sp.isShiftKeyDown()) {
                triggered = SeaWeaponsCombatHandler.tryCastWhirlpool(sp, sl, stack);
            } else {
                triggered = SeaWeaponsCombatHandler.tryCastTidalWave(sp, sl, stack);
            }
            return triggered ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack originalStack, ItemStack updatedStack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String keyR = clientWhirlpoolKeyName.get();
        tooltip.add(Component.translatable("tooltip.abyssalrpg.sea_trident.subtitle")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.sea_trident.combo")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.sea_trident.lantern_wave")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.sea_trident.trident_whirlpool", keyR)
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.sea_trident.synergy")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.sea_trident.passive")
                .withStyle(ChatFormatting.DARK_AQUA));
        if (clientIsOffhandOccupied.getAsBoolean()) {
            tooltip.add(Component.translatable("tooltip.abyssalrpg.sea_trident.offhand_warn")
                    .withStyle(ChatFormatting.RED));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "sea_weapons_idle", 0, state -> state.setAndContinue(IDLE_ANIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
