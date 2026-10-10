package com.zeropointsix.eraser.neon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import java.util.EnumSet;

public final class SeekLightGoal extends Goal {
    private final NeonTumorEntity tumor;
    private long nextCheck;
    private Path path;

    public SeekLightGoal(NeonTumorEntity tumor) {
        this.tumor = tumor;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public boolean canUse() {
        if (tumor.isDormant() || tumor.getTarget() != null || tumor.isEngulfing()) return false;
        long now = tumor.level().getGameTime();
        if (now < nextCheck) return false;
        nextCheck = now + 200;
        Level level = tumor.level();
        BlockPos origin = tumor.blockPosition();
        int bestLight = level.getMaxLocalRawBrightness(origin);
        path = null;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -2, -8), origin.offset(8, 2, 8))) {
            if (pos.distSqr(origin) > 64 || !level.hasChunkAt(pos)) continue;
            int brightness = level.getMaxLocalRawBrightness(pos);
            if (brightness <= bestLight || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
            Path candidate = tumor.getNavigation().createPath(pos.immutable(), 0);
            if (candidate != null && candidate.canReach()) {
                path = candidate;
                bestLight = brightness;
            }
        }
        return path != null;
    }
    @Override public boolean canContinueToUse() { return !tumor.isDormant() && tumor.getTarget() == null && !tumor.getNavigation().isDone(); }
    @Override public void start() { tumor.getNavigation().moveTo(path, 0.6); }
    @Override public void stop() { tumor.getNavigation().stop(); path = null; }
}
