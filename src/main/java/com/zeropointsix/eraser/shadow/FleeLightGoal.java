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
    public static final int POSITIONS_PER_TICK = 1024;
    public static final int PATH_TARGETS_PER_TICK = 64;
    private static final List<BlockPos> SEARCH_OFFSETS = offsets();
    private final ShadowTanglerEntity mob;
    private int nextSearch;
    private BlockPos destination;
    private BlockPos searchOrigin;
    private int searchCursor;
    private int lastSearchPositions;
    private int lastPathTargets;

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
        resetSearch();
        nextSearch = 0;
    }

    @Override
    public void tick() {
        lastSearchPositions = 0;
        lastPathTargets = 0;
        mob.setTarget(null);
        if (--nextSearch > 0) return;
        if (destination != null && mob.brightnessAt(destination) <= 6 && !mob.getNavigation().isDone()) {
            nextSearch = 20;
            return;
        }
        // createPath may otherwise reuse the old path when its target becomes bright.
        mob.getNavigation().stop();
        destination = null;
        BlockPos origin = mob.blockPosition();
        if (!origin.equals(searchOrigin)) {
            searchOrigin = origin.immutable();
            searchCursor = 0;
        }
        Set<BlockPos> candidates = new LinkedHashSet<>();
        // Continue the same nearest-first search across ticks, including sealed refuges.
        while (searchCursor < SEARCH_OFFSETS.size() && lastSearchPositions < POSITIONS_PER_TICK
                && candidates.size() < PATH_TARGETS_PER_TICK) {
            BlockPos pos = searchOrigin.offset(SEARCH_OFFSETS.get(searchCursor++));
            lastSearchPositions++;
            if (!mob.level().hasChunkAt(pos) || !mob.level().isInWorldBounds(pos)
                    || mob.brightnessAt(pos) > 6) continue;
            var support = mob.level().getBlockState(pos.below()).getCollisionShape(mob.level(), pos.below());
            if (support.isEmpty()) continue;
            double groundY = pos.getY() - 1 + support.max(Direction.Axis.Y);
            Vec3 offset = new Vec3(pos.getX() + 0.5, groundY, pos.getZ() + 0.5).subtract(mob.position());
            if (!mob.level().noCollision(mob, mob.getBoundingBox().move(offset))) continue;
            candidates.add(pos);
        }
        lastPathTargets = candidates.size();
        // Bound both the world scan and the multi-target pathfinder's heuristic work.
        Path path = candidates.isEmpty() ? null : mob.getNavigation().createPath(candidates, 0);
        if (path != null && path.canReach() && mob.brightnessAt(path.getTarget()) <= 6
                && mob.getNavigation().moveTo(path, 1D)) {
            destination = path.getTarget();
            resetSearch();
            nextSearch = 20;
            return;
        }
        if (searchCursor == SEARCH_OFFSETS.size()) {
            resetSearch();
            nextSearch = 20;
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        destination = null;
        resetSearch();
    }

    public int lastSearchPositions() { return lastSearchPositions; }
    public int lastPathTargets() { return lastPathTargets; }

    private void resetSearch() {
        searchOrigin = null;
        searchCursor = 0;
    }
}
