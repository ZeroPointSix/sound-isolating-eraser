package com.zeropointsix.eraser.neon;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import java.util.EnumSet;

public final class EngulfGoal extends Goal {
    private final NeonTumorEntity tumor;

    public EngulfGoal(NeonTumorEntity tumor) {
        this.tumor = tumor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override public boolean canUse() { return tumor.isEngulfing() || tumor.canEngulf(tumor.getTarget()); }
    @Override public boolean canContinueToUse() { return tumor.isEngulfing() && tumor.isAlive(); }
    @Override public void start() {
        if (!tumor.isEngulfing() && tumor.getTarget() instanceof Player player) tumor.beginEngulf(player);
        tumor.getNavigation().stop();
    }
    @Override public void stop() { tumor.releasePassenger(); }
}
