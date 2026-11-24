package com.abyssalrpg.item;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.combat.TogetsuCombatHandler;
import com.abyssalrpg.registry.ModEnchantments;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
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
import java.util.Locale;

/**
 * **Togetsu (兎月 — Trăng Non & Trăng Khuyết / Dạng Hợp Nhất: Song Nguyệt · Sōgengetsu)** — Vũ khí biến hình 2 dạng:
 * - Độ bền: **2031** (ngang kiếm Netherite), chống cháy.
 * - **Dạng Song Kiếm tách rời (`32.0` dame, tốc đánh `2.4`):**
 *   + **Trăng Non (Lưỡi Trắng):** Mỗi đòn Chí Mạng trừ **`1 HP` (nửa tim)** để cộng **`+30%` sát thương** cho đòn kế tiếp.
 *   + **Trăng Khuyết (Lưỡi Đen):** Mỗi đòn Chí Mạng hồi **`1 HP` (nửa tim)** tạo vòng lặp cân bằng máu.
 *   + **Thanh đo Hợp Nhất (`fusion_charge`, `0–100`):** Mỗi đòn Chí Mạng cộng **`+15` điểm** (tự giảm dần nếu không đánh sau `5s`);
 *     đạt `100` điểm tự bật ánh sáng Enchantment Glint (`isFoil`)!
 *   + **Chuột Phải (có địch trong 12.5 block):** Lao Đè 12 Liên Đâm Vô Địch (xuyên `10%` DEF, `×1.6` sát thương).
 * - **Dạng Hợp Nhất — Song Nguyệt (`Twin Moon`, phím `R` hoặc Chuột Phải khi đầy `100` điểm, duy trì `15s` = `300` ticks, hồi đổi dạng `6s`):**
 *   + **Đại Đao Trăng Khuyết (`48.0` dame = `×1.5`)**, tăng tầm đánh `ENTITY_INTERACTION_RANGE` thêm **`+1.5` block** (`3.0 -> 4.5` block) + chém lan diện rộng (`Sweeping Edge`).
 *   + **Nội tại Kẻ Thách Thức / Nghịch Lý Cấp Độ:** So sánh Max HP & Giáp của mục tiêu -> tăng mạnh tốc đánh, chí mạng & sát thương (Warden `500 HP` đạt `+80%`, tối đa **`+100%`**).
 *   + **Chiêu Tất Sát (`Shift + Chuột Phải`, hồi `7s`) — Crescent Vorpal:** Phóng lưỡi liềm trắng–đen xuyên **`15` block**,
 *     gây **sát thương chuẩn xuyên 100% giáp = `200%` sát thương gốc** + **100% Chí Mạng** + đẩy lùi nhẹ, sau đó tách ngay về dạng Song Kiếm & dính `Weakness I` (`3s`).
 */
public final class TogetsuItem extends Item implements GeoItem {
    public static final double DUAL_DAMAGE = 32.0;
    public static final double FUSED_DAMAGE = 48.0; // 32.0 * 1.5
    public static final double DUAL_ATTACK_SPEED = 2.4;
    public static final double FUSED_ATTACK_SPEED = 1.5;
    public static final double FUSED_BONUS_REACH = 1.5; // 3.0 -> 4.5 blocks
    public static final int NETHERITE_DURABILITY = 2031;

    private static final ResourceLocation REACH_MODIFIER_ID = AbyssalRpgMod.id("togetsu_fused_reach");

    private static final String TAG_FUSED = "isMerged";
    private static final String TAG_FUSED_LEGACY = "TogetsuFused";
    private static final String TAG_GAUGE = "fusion_charge";
    private static final String TAG_GAUGE_LEGACY = "TogetsuGauge";
    private static final String TAG_FUSION_END_TICK = "fusion_end_tick";
    private static final String TAG_EMPOWERED = "TogetsuWaxingEmpowered";
    private static final String TAG_EMPOWER_BONUS = "TogetsuWaxingBonus";
    private static final String TAG_TRANSFORM_CD_UNTIL = "TogetsuTransformCdUntil";
    private static final String TAG_PIN_CD_UNTIL = "TogetsuPinCdUntil";
    private static final String TAG_VORPAL_CD_UNTIL = "TogetsuVorpalCdUntil";

    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("togetsu.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @SuppressWarnings("this-escape")
    public TogetsuItem() {
        super(new Item.Properties()
                .durability(NETHERITE_DURABILITY)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .attributes(createAttributeModifiers(false))
                .component(DataComponents.TOOL, createToolProperties()));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static ItemAttributeModifiers createAttributeModifiers(boolean fused) {
        double dmg = fused ? FUSED_DAMAGE : DUAL_DAMAGE;
        double spd = fused ? FUSED_ATTACK_SPEED : DUAL_ATTACK_SPEED;
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, dmg - 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, spd - 4.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
        if (fused) {
            builder.add(
                    Attributes.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(REACH_MODIFIER_ID, FUSED_BONUS_REACH, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        return builder.build();
    }

    private static Tool createToolProperties() {
        return new Tool(
                List.of(
                        Tool.Rule.minesAndDrops(List.of(Blocks.COBWEB), 15.0F),
                        Tool.Rule.overrideSpeed(BlockTags.SWORD_EFFICIENT, 1.5F)),
                1.0F,
                2);
    }

    // --- State Helpers trên ItemStack (đồng bộ tự động Client/Server qua DataComponents.CUSTOM_DATA!) ---

    public static boolean isFused(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return false;
        CompoundTag tag = cd.copyTag();
        return tag.contains(TAG_FUSED) ? tag.getBoolean(TAG_FUSED) : tag.getBoolean(TAG_FUSED_LEGACY);
    }

    public static float getFusionGauge(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0F;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0.0F;
        CompoundTag tag = cd.copyTag();
        float val = tag.contains(TAG_GAUGE) ? tag.getFloat(TAG_GAUGE) : tag.getFloat(TAG_GAUGE_LEGACY);
        return Mth.clamp(val, 0.0F, 100.0F);
    }

    public static long getFusionEndTick(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null ? cd.copyTag().getLong(TAG_FUSION_END_TICK) : 0L;
    }

    public static int getFusionTicksRemaining(ItemStack stack, long nowGameTime) {
        if (!isFused(stack)) return 0;
        long endTick = getFusionEndTick(stack);
        long diff = endTick - nowGameTime;
        if (diff <= 0L || diff > TogetsuCombatHandler.FUSION_DURATION_TICKS + 40L) {
            return 0;
        }
        return (int) diff;
    }

    public static boolean isWaxingEmpowered(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().getBoolean(TAG_EMPOWERED);
    }

    public static float getWaxingEmpowerBonus(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0F;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null ? Mth.clamp(cd.copyTag().getFloat(TAG_EMPOWER_BONUS), 0.0F, 1.0F) : 0.0F;
    }

    public static void setFusionState(ItemStack stack, boolean fused, float gauge) {
        setFusionState(stack, fused, gauge, fused ? getFusionEndTick(stack) : 0L);
    }

    public static void setFusionState(ItemStack stack, boolean fused, float gauge, long fusionEndTick) {
        if (stack == null || stack.isEmpty()) return;
        float clamped = Mth.clamp(gauge, 0.0F, 100.0F);
        long endTick = fused ? fusionEndTick : 0L;
        boolean wasFused = isFused(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean(TAG_FUSED, fused);
            tag.putFloat(TAG_GAUGE, clamped);
            tag.putLong(TAG_FUSION_END_TICK, endTick);
            tag.remove(TAG_FUSED_LEGACY);
            tag.remove(TAG_GAUGE_LEGACY);
        });
        if (wasFused != fused) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, createAttributeModifiers(fused));
        }
    }

    /** Đảm bảo vật phẩm Togetsu đã hợp nhất từ save cũ cũng được cập nhật tốc đánh 1.5 ngay lập tức. */
    public static void ensureFusedAttributesUpToDate(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isFused(stack)) return;
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        double expectedSpdMod = FUSED_ATTACK_SPEED - 4.0;
        if (mods != null) {
            for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
                if (entry.attribute().equals(Attributes.ATTACK_SPEED)
                        && entry.modifier().id().equals(Item.BASE_ATTACK_SPEED_ID)) {
                    if (Math.abs(entry.modifier().amount() - expectedSpdMod) > 1e-4) {
                        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, createAttributeModifiers(true));
                    }
                    return;
                }
            }
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, createAttributeModifiers(true));
    }

    public static void setWaxingEmpower(ItemStack stack, boolean active, float bonusFraction) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean(TAG_EMPOWERED, active);
            tag.putFloat(TAG_EMPOWER_BONUS, active ? Mth.clamp(bonusFraction, 0.0F, 1.0F) : 0.0F);
        });
    }

    public static void addFusionGauge(ItemStack stack, float delta) {
        setFusionState(stack, isFused(stack), getFusionGauge(stack) + delta, getFusionEndTick(stack));
    }

    public static long getTransformCooldownUntil(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null ? cd.copyTag().getLong(TAG_TRANSFORM_CD_UNTIL) : 0L;
    }

    public static void setTransformCooldownUntil(ItemStack stack, long untilGameTime) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(TAG_TRANSFORM_CD_UNTIL, untilGameTime));
    }

    public static int getTransformCooldownRemainingTicks(ItemStack stack, long nowGameTime) {
        long until = getTransformCooldownUntil(stack);
        long diff = until - nowGameTime;
        if (diff <= 0L || diff > TogetsuCombatHandler.TRANSFORM_COOLDOWN_TICKS + 40L) {
            return 0;
        }
        return (int) diff;
    }

    public static void setPinCooldownUntil(ItemStack stack, long untilGameTime) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(TAG_PIN_CD_UNTIL, untilGameTime));
    }

    public static int getPinCooldownRemainingTicks(ItemStack stack, long nowGameTime) {
        if (stack == null || stack.isEmpty()) return 0;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        long diff = cd.copyTag().getLong(TAG_PIN_CD_UNTIL) - nowGameTime;
        if (diff <= 0L || diff > TogetsuCombatHandler.PIN_FLURRY_COOLDOWN_TICKS + 40L) {
            return 0;
        }
        return (int) diff;
    }

    public static void setVorpalCooldownUntil(ItemStack stack, long untilGameTime) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(TAG_VORPAL_CD_UNTIL, untilGameTime));
    }

    public static int getVorpalCooldownRemainingTicks(ItemStack stack, long nowGameTime) {
        if (stack == null || stack.isEmpty()) return 0;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        long diff = cd.copyTag().getLong(TAG_VORPAL_CD_UNTIL) - nowGameTime;
        if (diff <= 0L || diff > TogetsuCombatHandler.VORPAL_CHARGE_TICKS + TogetsuCombatHandler.VORPAL_COOLDOWN_TICKS + 40L) {
            return 0;
        }
        return (int) diff;
    }

    /**
     * Ngăn vanilla tụt tay (re-equip dip) khi thanh nộ `fusion_charge` tăng/giảm hoặc cập nhật cooldown/buff trên `CUSTOM_DATA`.
     * Chỉ cho phép hiệu ứng nâng tay khi đổi vật phẩm khác hoặc biến hình giữa Song Đao <-> Song Nguyệt!
     */
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return !oldStack.is(ModItems.TOGETSU)
                || !newStack.is(ModItems.TOGETSU)
                || isFused(oldStack) != isFused(newStack);
    }

    /** Khi `fusion_charge` đạt 100 điểm hoặc đang ở dạng Hợp Nhất Song Nguyệt -> tự bật hiệu ứng Enchantment Glint! */
    @Override
    public boolean isFoil(ItemStack stack) {
        return getFusionGauge(stack) >= 99.5F || isFused(stack) || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (isFused(stack)) {
            return Component.translatable("item.abyssalrpg.togetsu.fused").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        }
        return Component.translatable("item.abyssalrpg.togetsu").withStyle(ChatFormatting.WHITE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        // 1. Dạng Hợp Nhất (Song Nguyệt): chỉ cần Chuột Phải (RMB) -> Tuyệt kỹ Crescent Vorpal (gồng 2s, hồi 7s độc lập với dạng tách!)
        if (isFused(stack)) {
            int vorpalRem = getVorpalCooldownRemainingTicks(stack, level.getGameTime());
            if (vorpalRem > 0) {
                if (!level.isClientSide) {
                    String remStr = String.format(Locale.ROOT, "%.1f", Math.max(0.1F, vorpalRem / 20.0F));
                    player.displayClientMessage(
                            Component.translatable("message.abyssalrpg.togetsu.skill_cooldown", remStr)
                                    .withStyle(ChatFormatting.YELLOW),
                            true);
                }
                return InteractionResultHolder.pass(stack);
            }
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                if (!TogetsuCombatHandler.tryCastCrescentVorpal(sp, stack)) {
                    return InteractionResultHolder.fail(stack);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // 2. Dạng Song Đao tách rời:
        //    - Nếu có kẻ địch phía trước -> Lao Đè 12 Liên Đâm Vô Địch (trừ khi người chơi đang Shift + đầy 100 điểm muốn Hợp Nhất!).
        //    - Nếu KHÔNG có kẻ địch phía trước (hoặc đang Shift) và đã đủ 100 điểm Hợp Nhất -> Kích hoạt Hợp Nhất Song Nguyệt ngay!
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (!TogetsuCombatHandler.handleDualRightClick(sp, stack)) {
                return InteractionResultHolder.fail(stack);
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
        return 18;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(Items.NETHERITE_INGOT) || repair.is(ModEnchantments.DUAL_KNIFE_REPAIR_ITEMS) || super.isValidRepairItem(toRepair, repair);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean fused = isFused(stack);
        int gaugePct = Math.round(getFusionGauge(stack));
        if (fused) {
            tooltip.add(Component.translatable("tooltip.abyssalrpg.togetsu.mode_fused", "15.0")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            tooltip.add(Component.translatable("tooltip.abyssalrpg.togetsu.underdog")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.abyssalrpg.togetsu.reach_sweep")
                    .withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("tooltip.abyssalrpg.togetsu.vorpal")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.translatable("tooltip.abyssalrpg.togetsu.mode_dual", gaugePct)
                    .withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("tooltip.abyssalrpg.togetsu.waxing")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.abyssalrpg.togetsu.waning")
                    .withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.abyssalrpg.togetsu.charge_rule")
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.abyssalrpg.togetsu.pin_flurry")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("tooltip.abyssalrpg.togetsu.fuse_hint")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (isWaxingEmpowered(stack)) {
            int bonusPct = Math.round(getWaxingEmpowerBonus(stack) * 100.0F);
            tooltip.add(Component.translatable("tooltip.abyssalrpg.togetsu.empowered", bonusPct)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle_controller", 2, state -> state.setAndContinue(ANIM_IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
