package com.zeropointsix.eraser.wings;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Straight-line blink using the player's full body and vanilla collision shapes. */
final class BlinkDestination {
    private static final double CLEARANCE = 0.001;

    private BlinkDestination() {}

    static Optional<Vec3> find(ServerPlayer player, double distance) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.position();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 movement = direction.scale(distance);
        if (!Double.isFinite(movement.x) || !Double.isFinite(movement.y)
                || !Double.isFinite(movement.z) || movement.lengthSqr() < CLEARANCE * CLEARANCE) {
            return Optional.empty();
        }

        AABB body = player.getBoundingBox();
        AABB swept = body.expandTowards(movement);
        WorldBorder border = level.getWorldBorder();
        // Check before querying collision shapes: those queries may request chunks.
        if (swept.minY < level.getMinBuildHeight() || swept.maxY > level.getMaxBuildHeight()
                || swept.minX < border.getMinX() || swept.maxX > border.getMaxX()
                || swept.minZ < border.getMinZ() || swept.maxZ > border.getMaxZ()
                || !level.hasChunksAt(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                        BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) {
            return Optional.empty();
        }
        if (!level.noCollision(player, body)) return Optional.empty();

        double allowed = clearDistance(level, player, body, start, direction, distance);
        if (allowed < CLEARANCE) return Optional.empty();
        Vec3 delta = direction.scale(allowed);
        if (!level.noCollision(player, body.move(delta))) return Optional.empty();
        return Optional.of(start.add(delta));
    }

    private static double clearDistance(ServerLevel level, ServerPlayer player, AABB body,
            Vec3 start, Vec3 direction, double distance) {
        // Short continuous sweeps bound the queried volume for long diagonal blinks.
        // This is not point sampling: every shape between adjacent endpoints is tested.
        for (double travelled = 0; travelled < distance; travelled += 4.0) {
            double length = Math.min(4.0, distance - travelled);
            Vec3 segmentStart = start.add(direction.scale(travelled));
            Vec3 segmentEnd = segmentStart.add(direction.scale(length));
            AABB sweep = body.move(direction.scale(travelled)).expandTowards(direction.scale(length));
            double allowed = segmentDistance(level, player, body, start, segmentStart, segmentEnd, length, sweep);
            if (allowed < length) return travelled + allowed;
        }
        return distance;
    }

    private static double segmentDistance(ServerLevel level, ServerPlayer player, AABB body,
            Vec3 feet, Vec3 start, Vec3 end, double distance, AABB swept) {
        double allowed = distance;
        for (VoxelShape shape : level.getCollisions(player, swept)) {
            for (AABB obstacle : shape.toAabbs()) {
                // Expand each obstacle by the player's extents, then use Minecraft's
                // ray/AABB intersection. This sweeps the whole body without stepping,
                // sliding around corners, or skipping thin collision shapes.
                AABB expanded = new AABB(
                        obstacle.minX - (body.maxX - feet.x),
                        obstacle.minY - (body.maxY - feet.y),
                        obstacle.minZ - (body.maxZ - feet.z),
                        obstacle.maxX + (feet.x - body.minX),
                        obstacle.maxY + (feet.y - body.minY),
                        obstacle.maxZ + (feet.z - body.minZ));
                AABB interior = expanded.deflate(1.0E-6);
                // Vanilla clip excludes both endpoints. Include the shared endpoint
                // of adjacent sweeps while the inset keeps tangential contact free.
                if (containsEndpoint(interior, start)) return 0;
                Optional<Vec3> hit = interior.clip(start, end);
                if (hit.isEmpty() && containsEndpoint(interior, end)) hit = Optional.of(end);
                if (hit.isPresent()) {
                    allowed = Math.min(allowed, Math.max(0, start.distanceTo(hit.get()) - CLEARANCE));
                    if (allowed < CLEARANCE) return 0;
                }
            }
        }
        return allowed;
    }

    private static boolean containsEndpoint(AABB box, Vec3 point) {
        return point.x >= box.minX && point.x <= box.maxX
                && point.y >= box.minY && point.y <= box.maxY
                && point.z >= box.minZ && point.z <= box.maxZ;
    }
}
