package com.zeropointsix.eraser.memory;

import java.util.function.IntSupplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

public class SummonMeleeAttackGoal extends MeleeAttackGoal {
    private final IntSupplier interval;
    private long nextAttackAt;

    public SummonMeleeAttackGoal(PathfinderMob mob, double speed, IntSupplier interval) {
        super(mob, speed, true);
        this.interval = interval;
    }

    @Override protected int getAttackInterval() { return Math.max(1, interval.getAsInt()); }

    @Override protected void checkAndPerformAttack(LivingEntity target, double distanceSquared) {
        long now = mob.level().getGameTime();
        // Use server ticks: vanilla's private cooldown is not configurable through its interval getter.
        if (distanceSquared <= getAttackReachSqr(target) && now >= nextAttackAt) {
            nextAttackAt = now + getAttackInterval();
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
        }
    }
}
