package com.zeropointsix.eraser.neon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

public final class TendrilSlamGoal extends Goal {
    private final NeonTumorEntity tumor;
    private LivingEntity victim;
    private int elapsed;

    public TendrilSlamGoal(NeonTumorEntity tumor) {
        this.tumor = tumor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        LivingEntity target = tumor.getTarget();
        return tumor.canSlam() && tumor.canPursue(target) && !tumor.isEngulfing()
                && tumor.distanceToSqr(target) <= 3.5 * 3.5 && tumor.hasLineOfSight(target);
    }
    @Override public boolean canContinueToUse() { return elapsed < 14 && tumor.isAlive() && !tumor.isDormant() && !tumor.isEngulfing(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        victim = tumor.getTarget();
        elapsed = 0;
        tumor.getNavigation().stop();
        tumor.startSlam();
    }
    @Override public void tick() {
        elapsed++;
        if (victim == null) return;
        tumor.getLookControl().setLookAt(victim, 30, 30);
        if (elapsed == 10 && victim.isAlive() && tumor.canPursue(victim)
                && tumor.distanceToSqr(victim) <= 3.5 * 3.5 && tumor.hasLineOfSight(victim)) {
            victim.hurt(tumor.damageSources().mobAttack(tumor), 5);
        }
    }
    @Override public void stop() { victim = null; tumor.stopSlam(); }
}
