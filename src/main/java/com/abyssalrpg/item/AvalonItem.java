package com.abyssalrpg.item;

import com.abyssalrpg.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * **Avalon — Vỏ Kiếm Thánh Kiếm (The Everdistant Utopia):**
 * Khi mang theo bên mình (túi đồ hoặc tay phụ) cùng với **Excalibur** để duy trì kết nối ma lực:
 * - **Ngừng lão hóa & Miễn nhiễm suy tàn:** Thanh tẩy liên tục Độc, Héo Mòn (Wither), Đói Khát, Suy Yếu & Mệt Mỏi.
 * - **Tái tạo mọi vết thương:** Tự động hồi phục máu cực nhanh liên tục ngay cả khi đang chiến đấu.
 * - **Bất tử thụ động:** Kể cả khi nhận đòn chí mạng phá hủy tim, Avalon lập tức kích hoạt Kết Giới Tiên Giới,
 *   ngăn chặn cái chết và tái tạo toàn vẹn 100% sinh lực (hồi lõi 15s, rút ngắn còn 6s khi hấp thụ Sinh Lực Hành Tinh)!
 */
public final class AvalonItem extends Item {
    public AvalonItem() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant());
    }

    /** Kiểm tra người chơi có đang mang Vỏ Kiếm Avalon trong túi đồ hoặc tay phụ không. */
    public static boolean hasAvalon(Player player) {
        if (player == null) {
            return false;
        }
        if (player.getOffhandItem().is(ModItems.AVALON) || player.getMainHandItem().is(ModItems.AVALON)) {
            return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.AVALON)) {
                return true;
            }
        }
        return false;
    }

    /** Kiểm tra người chơi có duy trì kết nối ma lực với Excalibur (đang cầm trên tay hoặc giữ trên thanh Hotbar). */
    public static boolean hasExcaliburManaLink(Player player) {
        if (player == null) {
            return false;
        }
        if (player.getMainHandItem().is(ModItems.EXCALIBUR) || player.getOffhandItem().is(ModItems.EXCALIBUR)) {
            return true;
        }
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(ModItems.EXCALIBUR)) {
                return true;
            }
        }
        return false;
    }

    /** Avalon chỉ phát huy bất tử thụ động miễn là còn kết nối ma lực với thanh Thánh Kiếm Excalibur! */
    public static boolean isAvalonActive(Player player) {
        return hasAvalon(player) && hasExcaliburManaLink(player);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.abyssalrpg.avalon")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("tooltip.abyssalrpg.avalon.subtitle")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.avalon.link")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.avalon.regen")
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.abyssalrpg.avalon.immortal")
                .withStyle(ChatFormatting.GOLD));
    }
}
