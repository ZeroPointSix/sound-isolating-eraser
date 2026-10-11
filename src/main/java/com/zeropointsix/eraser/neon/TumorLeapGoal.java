package com.zeropointsix.eraser.neon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

public final class TumorLeapGoal extends Goal {
    private final NeonTumorEntity tumor;
    private LivingEntity victim;
    private int elapsed;

    public TumorLeapGoal(NeonTumorEntity tumor) {
        this.tumor = tumor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override public boolean canUse() {
        LivingEntity target = tumor.getTarget();
        if (!tumor.canLeap() || !tumor.canPursue(target) || !tumor.onGround()
                || !target.onGround() || tumor.isEngulfing() || !tumor.hasLineOfSight(target)) return false;
        double distance = tumor.distanceToSqr(target);
        return distance >= 4 && distance <= 25;
    }
    @Override public boolean canContinueToUse() { return elapsed < 12 && tumor.isAlive() && !tumor.isDormant() && !tumor.isEngulfing(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        victim = tumor.getTarget();
        elapsed = 0;
        tumor.getNavigation().stop();
        tumor.startLeap();
    }
    @Override public void tick() {
        elapsed++;
        if (victim == null || !victim.isAlive()) return;
        tumor.getLookControl().setLookAt(victim, 30, 30);
        if (elapsed == 4 && tumor.onGround()) {
            Vec3 direction = victim.position().subtract(tumor.position()).multiply(1, 0, 1).normalize();
            tumor.setDeltaMovement(direction.scale(0.65).add(0, 0.35, 0));
            tumor.hasImpulse = true;
        }
        tumor.contact(victim);
    }
    @Override public void stop() { victim = null; }
}
