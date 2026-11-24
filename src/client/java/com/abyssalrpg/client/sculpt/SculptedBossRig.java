package com.abyssalrpg.client.sculpt;

import com.abyssalrpg.entity.AbstractAbyssalBoss;

/**
 * Animated 3D Boss Rig (hierarchical skeletal bones + smooth frame-rate-independent biomechanical animator).
 */
public interface SculptedBossRig<T extends AbstractAbyssalBoss> {
    SculptedBone root();

    void animate(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                 float netHeadYaw, float headPitch, BossAnimState state);
}
