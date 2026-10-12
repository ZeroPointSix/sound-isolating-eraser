package com.zeropointsix.eraser.shadow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class FleeLightGoal extends Goal {
    private static final List<BlockPos> SEARCH_OFFSETS = offsets();
    private final ShadowTanglerEntity mob;
    private int nextSearch;
    private BlockPos destination;

    public FleeLightGoal(ShadowTanglerEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static List<BlockPos> offsets() {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(-16, -16, -16, 16, 16, 16)) {
            if (pos.distSqr(BlockPos.ZERO) <= 256) result.add(pos.immutable());
        }
        result.sort(Comparator.comparingDouble(pos -> pos.distSqr(BlockPos.ZERO)));
        return List.copyOf(result);
    }

    @Override public boolean canUse() { return !mob.isSpawning() && mob.wantsToFlee(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        mob.setTarget(null);
        mob.getNavigation().stop();
        destination = null;
        nextSearch = 0;
    }

    @Override
    public void tick() {
        mob.setTarget(null);
        if (--nextSearch > 0) return;
        nextSearch = 20;
        if (destination != null && mob.brightnessAt(destination) <= 6 && !mob.getNavigation().isDone()) return;
        // createPath may otherwise reuse the old path when its target becomes bright.
        mob.getNavigation().stop();
        BlockPos origin = mob.blockPosition();
        Set<BlockPos> candidates = new LinkedHashSet<>();
        for (BlockPos offsetPos : SEARCH_OFFSETS) {
            BlockPos pos = origin.offset(offsetPos);
            if (!mob.level().hasChunkAt(pos) || !mob.level().isInWorldBounds(pos)
                    || mob.brightnessAt(pos) > 6) continue;
            var support = mob.level().getBlockState(pos.below()).getCollisionShape(mob.level(), pos.below());
            if (support.isEmpty()) continue;
            double groundY = pos.getY() - 1 + support.max(Direction.Axis.Y);
            Vec3 offset = new Vec3(pos.getX() + 0.5, groundY, pos.getZ() + 0.5).subtract(mob.position());
            if (!mob.level().noCollision(mob, mob.getBoundingBox().move(offset))) continue;
            candidates.add(pos);
        }
        // One bounded multi-target search avoids spending seconds on nearer but sealed dark spaces.
        Path path = candidates.isEmpty() ? null : mob.getNavigation().createPath(candidates, 0);
        if (path != null && path.canReach() && mob.brightnessAt(path.getTarget()) <= 6
                && mob.getNavigation().moveTo(path, 1D)) {
            destination = path.getTarget();
            return;
        }
        destination = null;
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        destination = null;
    }
}
