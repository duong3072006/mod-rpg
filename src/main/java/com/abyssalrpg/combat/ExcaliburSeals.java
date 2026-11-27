package com.abyssalrpg.combat;

import com.abyssalrpg.entity.AbstractAbyssalBoss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Hệ thống **13 Lời Thề Phong Ấn (Restraints of the Round Table)** & Nhận diện **Mối Đe Dọa của Hành Tinh**
 * cho **Thánh Kiếm Excalibur**:
 * - Bình thường Excalibur luôn bị kìm hãm bởi 13 lớp khóa do Vua Arthur và 12 Hiệp sĩ Bàn Tròn đặt lên.
 * - Chỉ khi thỏa mãn **ít nhất 7/13 điều kiện** của các hiệp sĩ (`UNLOCK_THRESHOLD = 7`),
 *   thanh kiếm mới **Giải Phong Ấn** và bộc phát sức mạnh hủy diệt thực sự!
 */
public final class ExcaliburSeals {
    public static final int TOTAL_SEALS = 13;
    public static final int UNLOCK_THRESHOLD = 7;

    // 13 Bit vị trí tương ứng 13 Lời Thề của Vua Arthur & 12 Hiệp sĩ Bàn Tròn
    public static final int SEAL_BEDIVERE   = 1 << 0;  // Kẻ địch mạnh hơn bản thân
    public static final int SEAL_PALAMEDES  = 1 << 1;  // Một đối một / không ỷ đông hiếp yếu
    public static final int SEAL_LANCELOT   = 1 << 2;  // Không chiến đấu vì tư lợi / không hại người vô tội
    public static final int SEAL_MORDRED    = 1 << 3;  // Trận chiến chống lại cái Ác (quái vật/thù địch)
    public static final int SEAL_GALAHAD    = 1 << 4;  // Không ẩn nấp hèn nhát, trực diện đối mặt
    public static final int SEAL_GAHERIS    = 1 << 5;  // Không phải trận chiến vô nhân đạo
    public static final int SEAL_GARETH     = 1 << 6;  // Tôn trọng đạo nghĩa hiệp sĩ (giữ tâm thuần khiết)
    public static final int SEAL_AGRAVAIN   = 1 << 7;  // Đang trong giao tranh chính nghĩa thực sự
    public static final int SEAL_KAY        = 1 << 8;  // Bảo vệ đồng minh / dân làng / thú cưng
    public static final int SEAL_ARTHUR     = 1 << 9;  // Trận chiến vì sự sinh tồn của thế giới (Threat to the Planet)
    public static final int SEAL_GAWAIN     = 1 << 10; // Dưới ánh dương hoặc phá tan bóng đêm trước kẻ thù
    public static final int SEAL_TRISTAN    = 1 << 11; // Kiên cường trước nghịch cảnh (bị thương / bao vây / Boss)
    public static final int SEAL_PERCIVAL   = 1 << 12; // Lòng dũng cảm chân chính (áp sát trực diện kẻ địch)

    private ExcaliburSeals() {}

    public record Evaluation(int sealMask, int unlockedCount, boolean unsealed, boolean planetaryThreatNearby) {
        /** Hệ số nhân sức mạnh tuyệt kỹ theo số Phong Ấn đã giải (1.0x khi bị khóa; 1.85x -> 2.60x khi giải >=7/13!). */
        public float beamPowerMultiplier() {
            if (!unsealed) {
                return 1.0F;
            }
            int extra = Math.max(0, unlockedCount - UNLOCK_THRESHOLD);
            return 1.85F + (extra / (float) (TOTAL_SEALS - UNLOCK_THRESHOLD)) * 0.75F;
        }

        /** Hệ số xuyên giáp khi giải phong ấn (0.20 cơ bản -> 0.55..0.75 khi Giải Phong Ấn!). */
        public float beamArmorPierce() {
            if (!unsealed) {
                return 0.20F;
            }
            int extra = Math.max(0, unlockedCount - UNLOCK_THRESHOLD);
            return 0.55F + (extra / (float) (TOTAL_SEALS - UNLOCK_THRESHOLD)) * 0.20F;
        }
    }

    /**
     * Xác định một thực thể có phải là **"Mối đe dọa của Hành tinh" (Threat to the Planet)** hay không:
     * Boss Thần Thoại/Vực Thẳm (`AbstractAbyssalBoss`), Rồng Ender, Wither, Warden, Elder Guardian,
     * hoặc quái vật thù địch cấp đại họa có `MaxHP >= 250`.
     */
    public static boolean isThreatToThePlanet(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof AbstractAbyssalBoss
                || entity instanceof EnderDragon
                || entity instanceof WitherBoss
                || entity instanceof Warden
                || entity instanceof ElderGuardian) {
            return true;
        }
        return (entity instanceof Enemy) && entity.getMaxHealth() >= 250.0F;
    }

    /** Kiểm tra một thực thể có phải đồng minh được nhận buff Vương Quyền Khích Lệ không. */
    public static boolean isAlly(ServerPlayer wielder, LivingEntity candidate) {
        if (candidate == null || candidate == wielder || !candidate.isAlive()) {
            return false;
        }
        if (candidate instanceof Enemy) {
            return false;
        }
        if (candidate instanceof Player other) {
            return !other.isSpectator() && (wielder.isAlliedTo(other) || wielder.getTeam() == other.getTeam());
        }
        if (candidate instanceof TamableAnimal pet) {
            return pet.isTame() && (pet.isOwnedBy(wielder)
                    || (pet.getOwner() != null && wielder.isAlliedTo(pet.getOwner())));
        }
        return candidate instanceof IronGolem
                || candidate instanceof AbstractVillager
                || candidate instanceof Allay;
    }

    /** Đánh giá đồng thời cả 13 điều kiện của Hội Bàn Tròn & sự hiện diện của Mối Đe Dọa Hành Tinh. */
    public static Evaluation evaluate(ServerPlayer player, ServerLevel level,
                                      long nowTick, long lastCombatTick, long lastDishonorTick) {
        AABB scanBox = player.getBoundingBox().inflate(48.0);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, scanBox,
                e -> e != player && e.isAlive());

        boolean hasThreat = false;
        boolean hasStrongerFoe = false;
        boolean hasEvilFoe = false;
        boolean hasCloseFoe = false;
        int hostileCloseCount = 0;
        int allyCount = 0;
        double playerMaxHp = Math.max(20.0, player.getMaxHealth());

        for (LivingEntity e : nearby) {
            double distSq = e.distanceToSqr(player);
            if (isAlly(player, e)) {
                if (distSq <= 24.0 * 24.0) {
                    allyCount++;
                }
                continue;
            }
            boolean hostile = (e instanceof Enemy) || isThreatToThePlanet(e);
            if (!hostile) {
                continue;
            }
            if (isThreatToThePlanet(e)) {
                hasThreat = true;
                hasStrongerFoe = true;
                hasEvilFoe = true;
            }
            if (distSq <= 36.0 * 36.0) {
                hasEvilFoe = true;
                if (e.getMaxHealth() > playerMaxHp * 1.5) {
                    hasStrongerFoe = true;
                }
            }
            if (distSq <= 24.0 * 24.0) {
                hasCloseFoe = true;
                hostileCloseCount++;
            }
        }

        boolean inRecentCombat = (nowTick - lastCombatTick) <= 240L;
        boolean honorable = (nowTick - lastDishonorTick) > 300L;
        boolean daylight = level.isDay();

        int mask = 0;
        // 1. Bedivere: Kẻ địch mạnh hơn bản thân
        if (hasStrongerFoe) mask |= SEAL_BEDIVERE;
        // 2. Palamedes: Không ỷ đông hiếp yếu (đấu đơn độc hoặc đối đầu Boss / bầy đông)
        if (hasEvilFoe && (allyCount <= 1 || hasThreat || hostileCloseCount >= allyCount)) mask |= SEAL_PALAMEDES;
        // 3. Lancelot: Không chiến đấu vì tư lợi / giữ thanh danh (không sát hại dân lành/đồng minh)
        if (honorable) mask |= SEAL_LANCELOT;
        // 4. Mordred: Chiến đấu chống lại cái Ác
        if (hasEvilFoe) mask |= SEAL_MORDRED;
        // 5. Galahad: Đối diện quang minh (không ẩn thân hèn nhát bằng thuốc tàng hình)
        if (!player.hasEffect(MobEffects.INVISIBILITY)) mask |= SEAL_GALAHAD;
        // 6. Gaheris: Không vô nhân đạo
        if (honorable) mask |= SEAL_GAHERIS;
        // 7. Gareth: Giữ tâm thuần khiết hiệp sĩ
        mask |= SEAL_GARETH;
        // 8. Agravain: Đang trong giao tranh chính nghĩa thực sự
        if (inRecentCombat && hasEvilFoe) mask |= SEAL_AGRAVAIN;
        // 9. Kay: Chiến đấu bảo vệ đồng minh / dân làng (hoặc đứng chắn trước kẻ thù mạnh)
        if (allyCount > 0 && hasEvilFoe) mask |= SEAL_KAY;
        // 10. Arthur: Trận chiến vì sự sinh tồn của thế giới (Mối đe dọa của Hành tinh!)
        if (hasThreat) mask |= SEAL_ARTHUR;
        // 11. Gawain: Chiến đấu dưới ánh mặt trời hoặc phá tan bóng tối trước Mối Đe Dọa
        if ((daylight && level.canSeeSky(player.blockPosition())) || hasThreat) mask |= SEAL_GAWAIN;
        // 12. Tristan: Không khuất phục trước nghịch cảnh (máu dưới 85%, bị bao vây >=2 địch, hoặc đánh Boss)
        if (hasEvilFoe && (player.getHealth() < player.getMaxHealth() * 0.85F || hostileCloseCount >= 2 || hasThreat)) {
            mask |= SEAL_TRISTAN;
        }
        // 13. Percival: Dũng cảm áp sát trực diện kẻ địch trong cự ly giao chiến (<=24 block)
        if (hasCloseFoe) mask |= SEAL_PERCIVAL;

        int count = Integer.bitCount(mask);
        boolean unsealed = count >= UNLOCK_THRESHOLD;
        return new Evaluation(mask, count, unsealed, hasThreat);
    }
}
