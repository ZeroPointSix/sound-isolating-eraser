package com.zeropointsix.eraser.shadow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class FleeLightGoal extends Goal {
    private static final List<BlockPos> SEARCH_OFFSETS = offsets();
    private final ShadowTanglerEntity mob;
    private int nextSearch;
    private int searchCursor;
    private BlockPos origin;
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
        origin = mob.blockPosition();
        destination = null;
        searchCursor = 0;
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
        if (origin == null || origin.distSqr(mob.blockPosition()) > 4) {
            origin = mob.blockPosition();
            searchCursor = 0;
        }
        int attemptedPaths = 0;
        for (; searchCursor < SEARCH_OFFSETS.size(); searchCursor++) {
            BlockPos pos = origin.offset(SEARCH_OFFSETS.get(searchCursor));
            if (!mob.level().hasChunkAt(pos) || !mob.level().isInWorldBounds(pos)
                    || mob.brightnessAt(pos) > 6) continue;
            var support = mob.level().getBlockState(pos.below()).getCollisionShape(mob.level(), pos.below());
            if (support.isEmpty()) continue;
            double groundY = pos.getY() - 1 + support.max(Direction.Axis.Y);
            Vec3 offset = new Vec3(pos.getX() + 0.5, groundY, pos.getZ() + 0.5).subtract(mob.position());
            if (!mob.level().noCollision(mob, mob.getBoundingBox().move(offset))) continue;
            Path path = mob.getNavigation().createPath(pos, 0);
            if (path != null && path.canReach()) {
                destination = pos;
                mob.getNavigation().moveTo(path, 1D);
                searchCursor = 0;
                return;
            }
            // Bound expensive path searches; resume the same nearest-first scan next second.
            if (++attemptedPaths >= 16) { ++searchCursor; return; }
        }
        searchCursor = 0;
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        destination = null;
        origin = null;
    }
}
