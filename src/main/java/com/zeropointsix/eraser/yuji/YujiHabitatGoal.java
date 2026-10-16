package com.zeropointsix.eraser.yuji;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Reachable hiding places, without altering either monster's collision dimensions. */
final class YujiHabitatGoal extends Goal {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int SEARCH_RADIUS = 12, SEARCH_INTERVAL = 80, PATH_BUDGET = 16;
    private final YujiMonster mob;
    private Path path;
    private Vec3 destination, lastProgress;
    private int nextSearch, stalledTicks, elapsed, searchCursor;

    YujiHabitatGoal(YujiMonster mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        if (mob.kind() != YujiKind.LIANGSHE && mob.kind() != YujiKind.XIEYIN) return false;
        if (mob.habitatMovementPaused() || mob.tickCount < nextSearch) return false;
        nextSearch = mob.tickCount + SEARCH_INTERVAL;
        if (suitable(mob.position())) {
            destination = mob.position();
            path = null;
            return true;
        }
        var candidates = new ArrayList<Vec3>();
        BlockPos origin = mob.blockPosition();
        // Path uses a width-rounded node offset, which matters for the 1.8-block-wide Xieyin.
        double offset = (int) (mob.getBbWidth() + 1) * .5;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -3, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, 3, SEARCH_RADIUS))) {
            Vec3 point = new Vec3(pos.getX() + offset, pos.getY(), pos.getZ() + offset);
            if (suitable(point)) candidates.add(point);
        }
        candidates.sort(Comparator.comparingDouble(mob::distanceToSqr));
        for (int i = 0; i < Math.min(PATH_BUDGET, candidates.size()); i++) {
            Vec3 point = candidates.get((searchCursor + i) % candidates.size());
            BlockPos node = BlockPos.containing(point.x - offset, point.y, point.z - offset);
            Path candidate = mob.getNavigation().createPath(node, 0);
            if (candidate == null || !candidate.canReach() || candidate.getNodeCount() == 0) continue;
            Vec3 end = candidate.getEntityPosAtNode(mob, candidate.getNodeCount() - 1);
            if (!suitable(end)) continue;
            destination = end;
            path = candidate;
            searchCursor = 0;
            LOGGER.debug("Yuji habitat selected: kind={}, origin={}, destination={}, candidates={}",
                    mob.kind(), mob.position(), destination, candidates.size());
            return true;
        }
        searchCursor = candidates.isEmpty() ? 0 : (searchCursor + PATH_BUDGET) % candidates.size();
        LOGGER.debug("Yuji habitat unavailable: kind={}, origin={}, candidates={}, next={}",
                mob.kind(), mob.position(), candidates.size(), searchCursor);
        return false;
    }

    @Override public void start() {
        elapsed = stalledTicks = 0;
        lastProgress = mob.position();
        mob.getNavigation().stop();
        if (path != null) mob.getNavigation().moveTo(path, 1);
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public boolean canContinueToUse() {
        return destination != null && mob.isAlive() && stalledTicks < 100
                && (elapsed % 10 != 0 || suitable(destination))
                && (arrived() || mob.habitatMovementPaused() || !mob.getNavigation().isDone());
    }

    @Override public void tick() {
        elapsed++;
        mob.attendHabitatTarget();
        if (mob.habitatMovementPaused()) {
            mob.getNavigation().stop();
            return;
        }
        if (arrived()) {
            mob.getNavigation().stop();
            stalledTicks = 0;
        } else {
            if (mob.getNavigation().isDone()) mob.getNavigation().moveTo(destination.x, destination.y, destination.z, 1);
            if (mob.position().distanceToSqr(lastProgress) > .04) {
                lastProgress = mob.position();
                stalledTicks = 0;
            } else stalledTicks++;
        }
    }

    @Override public void stop() {
        mob.getNavigation().stop();
        path = null;
        destination = null;
        nextSearch = mob.tickCount + 20;
    }

    private boolean arrived() {
        return mob.distanceToSqr(destination) < .36 && suitable(mob.position());
    }

    private boolean suitable(Vec3 point) {
        Level level = mob.level();
        BlockPos feet = BlockPos.containing(point);
        if (!level.hasChunksAt(feet.offset(-5, -1, -5), feet.offset(5, 10, 5))) return false;
        if (mob.getTarget() != null && mob.getTarget().isAlive()
                && mob.getTarget().distanceToSqr(point) > (mob.kind() == YujiKind.LIANGSHE ? 400 : 256)) return false;
        if (!level.getFluidState(feet).isEmpty()) return false;
        AABB body = mob.getBoundingBox().move(point.subtract(mob.position()));
        if (mob.kind() == YujiKind.XIEYIN) {
            if (!narrow(feet, Direction.EAST, Direction.NORTH) && !narrow(feet, Direction.NORTH, Direction.EAST)) return false;
        } else {
            boolean roof = false;
            for (int y = Mth.ceil(body.maxY); y <= Mth.ceil(body.maxY) + 3; y++)
                if (solid(new BlockPos(feet.getX(), y, feet.getZ()))) { roof = true; break; }
            if (!roof) return false;
            int walls = 0;
            for (Direction direction : Direction.Plane.HORIZONTAL)
                if (wallDistance(feet, direction, 5) > 0) walls++;
            if (walls < 3) return false;
        }
        if (!level.getWorldBorder().isWithinBounds(body) || !level.noCollision(mob, body)) return false;
        // All corners need support: a narrow ledge is not a usable giant hiding place.
        for (double x : new double[]{body.minX + .01, body.maxX - .01})
            for (double z : new double[]{body.minZ + .01, body.maxZ - .01}) {
                BlockPos ground = BlockPos.containing(x, body.minY - .01, z);
                if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) return false;
            }
        return true;
    }

    private boolean narrow(BlockPos feet, Direction across, Direction along) {
        for (int offset = -1; offset <= 1; offset++) {
            BlockPos sample = feet.relative(along, offset);
            int a = wallDistance(sample, across, 3), b = wallDistance(sample, across.getOpposite(), 3);
            if (a == 0 || b == 0 || a + b > 4) return false;
        }
        return true;
    }

    private int wallDistance(BlockPos feet, Direction direction, int range) {
        for (int distance = 1; distance <= range; distance++) {
            BlockPos wall = feet.relative(direction, distance);
            if (solid(wall) && solid(wall.above())) return distance;
        }
        return 0;
    }

    private boolean solid(BlockPos pos) {
        var state = mob.level().getBlockState(pos);
        return !state.is(BlockTags.LEAVES) && state.isCollisionShapeFullBlock(mob.level(), pos);
    }
}
