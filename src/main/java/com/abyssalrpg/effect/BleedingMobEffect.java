package com.abyssalrpg.effect;

import com.abyssalrpg.combat.DualBladeCombatHandler;
import com.abyssalrpg.config.WeaponsConfig;
import com.abyssalrpg.registry.ModDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Hiệu ứng Chảy Máu (`abyssalrpg:bleeding`, màu đỏ thẫm `#A81018`):
 * - Mỗi 20 ticks (1 giây) gây `2.0 × level` sát thương chảy máu (cấp I = 2.0, cấp V = 10.0, cộng dồn tối đa theo Hemorrhage).
 * - Dùng bộ đếm thời gian riêng mỗi thực thể để việc làm mới thời lượng liên tục ở tốc đánh 3.0 hit/s KHÔNG làm kích đúp tick.
 * - KHÔNG reset `invulnerableTime` (không nuốt nhịp đánh của người chơi) và KHÔNG gây giật lùi (`no_knockback` + bảo toàn vận tốc).
 * - Hoạt động xuyên suốt trên cả mob thường lẫn Boss máu lớn triển khai `BossHealthHolder` (qua proxy forwarding về boss chính).
 */
public final class BleedingMobEffect extends MobEffect {
    public static final int COLOR_HEX = 0xA81018;

    public BleedingMobEffect() {
        super(MobEffectCategory.HARMFUL, COLOR_HEX);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Kiểm tra nhịp 20 tick thật bằng gameTime trong applyEffectTick để không bị double-tick khi refresh duration liên tục
        return duration > 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || !entity.isAlive()) {
            return true;
        }
        WeaponsConfig cfg = WeaponsConfig.get();
        int interval = Math.max(5, cfg.bleedTickInterval);
        if (!DualBladeCombatHandler.consumeBleedTickReady(entity, interval)) {
            return true;
        }

        int stacks = Math.max(1, amplifier + 1);
        float deepWoundsBonus = DualBladeCombatHandler.getBleedDeepWoundsBonus(entity);
        float damage = stacks * cfg.bleedDamagePerStack * (1.0F + deepWoundsBonus);
        Entity attacker = DualBladeCombatHandler.resolveBleedAttacker(entity);
        DamageSource source = ModDamageTypes.bleeding(entity.level(), attacker);

        // Lưu & khôi phục invulnerableTime + hurtTime + vận tốc để Bleeding KHÔNG ăn mất i-frame của người chơi và 0 giật lùi
        int savedInvuln = entity.invulnerableTime;
        int savedHurtTime = entity.hurtTime;
        Vec3 savedMotion = entity.getDeltaMovement();

        entity.hurt(source, damage);

        if (entity.isAlive()) {
            entity.invulnerableTime = savedInvuln;
            entity.hurtTime = savedHurtTime;
            entity.setDeltaMovement(savedMotion);
        }
        return true;
    }
}

