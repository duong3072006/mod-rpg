package com.abyssalrpg.client.mixin;

import com.abyssalrpg.client.DualBladeClientState;
import com.abyssalrpg.client.TogetsuClientState;
import com.abyssalrpg.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bắt trực tiếp cú bấm Chuột Trái khi chém gió (`HitResult.Type.MISS` — không có quái)
 * TRƯỚC khi `Minecraft.startAttack()` của vanilla gọi `player.resetAttackStrengthTicker()`
 * và thiết lập `missTime = 10`, giúp `Dual Knife` luôn kích hoạt đầy đủ chuỗi 5 nhịp animation,
 * Sprint Thrust, Air Slash, vệt chém Ribbon & đồng bộ Multiplayer ngay cả khi không chỉ vào quái
 * (KHÔNG cản trở đào block khi `HitResult.Type.BLOCK`).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Shadow
    @Nullable
    public Screen screen;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void abyssalrpg$onDualKnifeAirAttackStart(CallbackInfoReturnable<Boolean> cir) {
        if (this.player == null || this.screen != null || this.player.isSpectator() || this.player.isHandsBusy()) {
            return;
        }
        boolean isDualKnife = this.player.getMainHandItem().is(ModItems.DUAL_KNIFE);
        boolean isTogetsu = this.player.getMainHandItem().is(ModItems.TOGETSU);
        if (!isDualKnife && !isTogetsu) {
            return;
        }
        // Chỉ bắt chém gió (không có quái / không đào block); đánh quái & đào block giữ nguyên luồng chuẩn!
        if (this.hitResult == null || this.hitResult.getType() == HitResult.Type.MISS) {
            if (isDualKnife) {
                DualBladeClientState.onClientAirSwing((Minecraft) (Object) this);
            } else {
                TogetsuClientState.onClientAirSwing((Minecraft) (Object) this);
            }
        }
    }
}
