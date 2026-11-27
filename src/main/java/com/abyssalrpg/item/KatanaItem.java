package com.abyssalrpg.item;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.combat.KatanaCombatHandler;
import com.abyssalrpg.registry.ModItems;
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
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Supplier;

/**
 * **Cyber Katana (`abyssalrpg:katana`)**:
 * - **Sát thương cơ bản:** `12` | **Độ bền:** `2031` (ngang kiếm Netherite) | **Tốc đánh:** `2.0`.
 * - **Dạng đứng (tra trong vỏ — Iaido):** Tay trái cầm vỏ, tay phải đặt trên chuôi kiếm vào tư thế chuẩn bị rút kiếm.
 * - **Khi rút kiếm (`[G]` hoặc tấn công / đỡ):** Hai tay nắm chặt chuôi kiếm, lưỡi kiếm cong hướng thẳng về phía kẻ địch.
 * - **Giữ `[Chuột Phải]` — Tư thế Đỡ Chéo Cao:**
 *   - Gạt đòn đánh vũ khí cận chiến trúng thân kiếm: loại bỏ `100%` sát thương & phản lại **`×2` sát thương gốc với `20%` xuyên giáp**!
 *   - Gạt mũi tên bay lệch sang hướng khác (không phản sát thương). Không thể đỡ kỹ năng phép thuật!
 */
public final class KatanaItem extends Item {
    public static final double ATTACK_DAMAGE = 12.0;
    public static final double ATTACK_SPEED = 2.0;
    public static final double BONUS_REACH = 0.75;
    public static final int MAX_DURABILITY = 2031; // Độ bền ngang kiếm Netherite!

    public static final String TAG_DRAWN = "KatanaDrawn";

    private static final ResourceLocation REACH_MOD_ID = AbyssalRpgMod.id("katana_reach");

    /** Cung cấp tên phím tắt rút/tra kiếm hiển thị động trên tooltip phía Client. */
    public static Supplier<String> clientSheathKeyName = () -> "G";

    /** Cung cấp tên phím tắt kích hoạt Nhất Kiếm Tất Sát (`IAIDO_BLINK`) phía Client. */
    public static Supplier<String> clientIaidoKeyName = () -> "R";

    /** Kiểm tra phía Client xem Nhất Kiếm Tất Sát (Iaido Blink) có đang hồi chiêu `6s` hay không. */
    public static java.util.function.IntPredicate clientIaidoOnCooldown = playerId -> false;

    public KatanaItem() {
        super(new Properties()
                .stacksTo(1)
                .durability(MAX_DURABILITY)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .attributes(createAttributes()));
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

    private static CompoundTag readTag(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    private static void writeTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Mặc định `false` (tra trong vỏ — tư thế Iaido chuẩn bị rút kiếm). */
    public static boolean isDrawn(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = readTag(stack);
        return tag.getBoolean(TAG_DRAWN);
    }

    public static void setDrawn(ItemStack stack, boolean drawn) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = readTag(stack);
        if (tag.contains(TAG_DRAWN) && tag.getBoolean(TAG_DRAWN) == drawn) {
            return;
        }
        tag.putBoolean(TAG_DRAWN, drawn);
        writeTag(stack, tag);
    }

    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return !oldStack.is(ModItems.KATANA) || !newStack.is(ModItems.KATANA);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.abyssalrpg.katana")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    @Override
    public int getEnchantmentValue() {
        return 20;
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
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // Dùng animation đỡ chéo cao 2 tay tùy biến trong KatanaAnimManager & ItemInHandRendererMixin
        return UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            // Luôn cho phép giữ RMB kể cả khi chưa rút kiếm để vào thế Saya-uke (Đỡ bằng vỏ khẩn cấp)!
        } else if (player instanceof ServerPlayer sp) {
            if (!KatanaCombatHandler.onStartUse(sp)) {
                return InteractionResultHolder.pass(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) {
            int heldTicks = Math.max(0, this.getUseDuration(stack, entity) - timeCharged);
            KatanaCombatHandler.onReleaseUse(sp, heldTicks);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String sheathKey = clientSheathKeyName.get();
        String iaidoKey = clientIaidoKeyName.get();
        tooltip.add(Component.literal("✦ ").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("tooltip.abyssalrpg.katana.subtitle")
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.katana.sheathed_stance", sheathKey)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.katana.drawn_stance")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.katana.parry_guard")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.katana.parry_counter")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.katana.stats", iaidoKey)
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
