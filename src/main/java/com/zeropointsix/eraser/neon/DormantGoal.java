package com.zeropointsix.eraser.neon;

import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

public final class DormantGoal extends Goal {
    private final NeonTumorEntity tumor;

    public DormantGoal(NeonTumorEntity tumor) {
        this.tumor = tumor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override public boolean canUse() { return tumor.isDormant(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { tumor.getNavigation().stop(); tumor.setTarget(null); }
    @Override public void tick() {
        tumor.getNavigation().stop();
        tumor.setDeltaMovement(0, tumor.getDeltaMovement().y, 0);
        tumor.setTarget(null);
    }
}
