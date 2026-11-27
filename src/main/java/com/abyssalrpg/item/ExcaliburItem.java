package com.abyssalrpg.item;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.combat.ExcaliburCombatHandler;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
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

/**
 * **Excalibur — Thánh Kiếm Chiến Thắng Hứa Hẹn:**
 * - **Trạng thái tàng hình (0 năng lượng):** Khi thanh năng lượng ở mức `0`, lưỡi kiếm tự động rơi vào trạng thái tàng hình mờ ảo!
 * - **Tích tầng năng lượng & Sức Mạnh IV:** Đánh thường trúng địch được **`+2` năng lượng / đòn**, kết liễu kẻ địch được **`+5` năng lượng / mục tiêu** (tối đa `100`).
 *   Khi tích đủ `100` năng lượng, người dùng nhận ngay **Sức Mạnh IV** — và hiệu ứng này cộng hưởng trực tiếp vào tuyệt kỹ!
 * - **Tuyệt kỹ `[Chuột Phải]` (khi tích đủ `100` năng lượng):**
 *   + Giơ thẳng chuôi kiếm lên trời rồi bổ xuống 1 đòn đánh lan dạng **ánh sáng đèn pin chiếu ra** với phạm vi **`4×5`**, tầm xa **`20` block**.
 *   + Gây **sát thương `1500%` sát thương cơ bản** (cộng hưởng hiệu ứng & phù phép), **bỏ qua mọi loại phòng thủ** (giáp, Kháng cự, Protection, phòng thủ Boss), **không thể chặn bằng khiên**, và **hất văng** toàn bộ kẻ địch đi!
 *   + Hậu quả sau khi tung chiêu: nhận **Suy Yếu III (`15s`)**, **Đi Chậm II (`15s`)** và **không thể tích năng lượng trong `45s`**!
 */
public final class ExcaliburItem extends Item implements GeoItem {
    public static final double ATTACK_DAMAGE = 36.0;
    public static final double ATTACK_SPEED = 1.8;
    public static final double BONUS_REACH = 0.90;
    public static final int MAX_DURABILITY = 2500;
    public static final float MAX_ENERGY = 100.0F;
    public static final float ENERGY_PER_HIT = 2.0F;
    public static final float ENERGY_PER_KILL = 5.0F;

    public static final String TAG_ENERGY = "ExcaliburEnergy";
    public static final String TAG_UNSHEATHED = "is_unsheathed";
    public static final String TAG_ULT_COOLDOWN_TICKS = "ExcaliburUltCdTicks";

    private static final ResourceLocation REACH_MOD_ID = AbyssalRpgMod.id("excalibur_reach");

    /** Tên phím kích hoạt Tuyệt Kỹ phía Client (mặc định `R`, đồng bộ từ cài đặt phím). */
    public static java.util.function.Supplier<String> clientUltKeyName = () -> "R";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ExcaliburItem() {
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

    private static CompoundTag readTag(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    private static void writeTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Mặc định bằng `0` khi mới sở hữu Excalibur -> kiếm lập tức ở trạng thái tàng hình! */
    public static float getEnergy(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0F;
        }
        CompoundTag tag = readTag(stack);
        if (!tag.contains(TAG_ENERGY)) {
            return 0.0F;
        }
        return Mth.clamp(tag.getFloat(TAG_ENERGY), 0.0F, MAX_ENERGY);
    }

    public static void setEnergy(ItemStack stack, float value) {
        if (stack.isEmpty()) {
            return;
        }
        float clamped = Mth.clamp(value, 0.0F, MAX_ENERGY);
        CompoundTag tag = readTag(stack);
        if (tag.contains(TAG_ENERGY) && Math.abs(tag.getFloat(TAG_ENERGY) - clamped) < 0.05F) {
            return;
        }
        tag.putFloat(TAG_ENERGY, clamped);
        writeTag(stack, tag);
    }

    /** Trạng thái rút kiếm (`is_unsheathed` trong DataComponent 1.21.1): `false` = đang tra gọn trong vỏ Avalon. */
    public static boolean isUnsheathed(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = readTag(stack);
        return tag.getBoolean(TAG_UNSHEATHED);
    }

    public static void setUnsheathed(ItemStack stack, boolean unsheathed) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = readTag(stack);
        if (tag.contains(TAG_UNSHEATHED) && tag.getBoolean(TAG_UNSHEATHED) == unsheathed) {
            return;
        }
        tag.putBoolean(TAG_UNSHEATHED, unsheathed);
        writeTag(stack, tag);
    }

    /** Đọc/ghi thời gian hồi chiêu Tuyệt kỹ còn lại (chỉ dùng khi thoát/vào lại game, tránh làm giật tay trong giao tranh). */
    public static int getSavedUltCooldownTicks(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        CompoundTag tag = readTag(stack);
        return Math.max(0, tag.getInt(TAG_ULT_COOLDOWN_TICKS));
    }

    public static void setSavedUltCooldownTicks(ItemStack stack, int ticks) {
        if (stack.isEmpty()) {
            return;
        }
        int clamped = Math.max(0, ticks);
        CompoundTag tag = readTag(stack);
        if (clamped == 0) {
            if (!tag.contains(TAG_ULT_COOLDOWN_TICKS)) {
                return;
            }
            tag.remove(TAG_ULT_COOLDOWN_TICKS);
        } else {
            if (tag.contains(TAG_ULT_COOLDOWN_TICKS) && tag.getInt(TAG_ULT_COOLDOWN_TICKS) == clamped) {
                return;
            }
            tag.putInt(TAG_ULT_COOLDOWN_TICKS, clamped);
        }
        writeTag(stack, tag);
    }

    /** Ngăn vanilla tụt tay & giật kiếm (re-equip dip/jitter) mỗi khi cập nhật dữ liệu trên Excalibur. */
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return !oldStack.is(ModItems.EXCALIBUR) || !newStack.is(ModItems.EXCALIBUR);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.abyssalrpg.excalibur")
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
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) {
            ExcaliburCombatHandler.onUseTick(sp);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        // Chuột phải [RMB] CHỈ dùng để Đỡ Hoàng Gia / Avalon Parry; Tuyệt kỹ kích hoạt bằng phím [R]!
        if (level.isClientSide) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (player instanceof ServerPlayer sp) {
            if (ExcaliburCombatHandler.isActionLocked(sp.getUUID())) {
                return InteractionResultHolder.pass(stack);
            }
            ExcaliburCombatHandler.onStartRoyalGuard(sp);
            player.startUsingItem(hand);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("tooltip.abyssalrpg.excalibur.golden_form")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.excalibur.royal_guard")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.excalibur.kill_charge")
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.excalibur.controls_rmb", clientUltKeyName.get())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.excalibur.ult_pierce")
                .withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Trả PlayState.STOP để toàn bộ góc xoay do ExcaliburAnimManager điều khiển 1 nguồn duy nhất (0 xung đột xương GeckoLib!)
        controllers.add(new AnimationController<>(this, "idle_controller", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
