package com.zeropointsix.eraser.yuji.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RainCityPlacement {
    private static final int FALLBACK_RADIUS = 8;

    public static boolean safe(ServerLevel level, Vec3 feet) {
        if (!bounded(level, feet)) return false;
        AABB body = EntityType.PLAYER.getDimensions().makeBoundingBox(feet);
        BlockPos low = BlockPos.containing(body.minX, body.minY, body.minZ);
        BlockPos high = BlockPos.containing(Math.nextDown(body.maxX), Math.nextDown(body.maxY), Math.nextDown(body.maxZ));
        if (!level.hasChunkAt(low) || !level.hasChunkAt(high)
                || !level.hasChunkAt(new BlockPos(low.getX(), low.getY(), high.getZ()))
                || !level.hasChunkAt(new BlockPos(high.getX(), low.getY(), low.getZ()))) return false;
        for (BlockPos pos : BlockPos.betweenClosed(low, high))
            if (!level.getFluidState(pos).isEmpty()) return false;
        BlockPos floor = BlockPos.containing(feet).below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) && level.noCollision(body);
    }

    public static Vec3 home(ServerLevel level, Vec3 preferred, BlockPos fallback) {
        if (bounded(level, preferred)) {
            loadBodyChunks(level, preferred);
            if (safe(level, preferred)) return preferred;
        }
        // At most 289 columns and four fallback chunks; never expand an unsafe search indefinitely.
        for (int radius = 0; radius <= FALLBACK_RADIUS; radius++)
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos column = fallback.offset(dx, 0, dz);
                Vec3 probe = new Vec3(column.getX() + .5, level.getMinBuildHeight() + 1, column.getZ() + .5);
                if (!bounded(level, probe)) continue;
                level.getChunk(column.getX() >> 4, column.getZ() >> 4);
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                Vec3 candidate = Vec3.atBottomCenterOf(top);
                if (safe(level, candidate)) return candidate;
            }
        return null;
    }

    private static boolean bounded(ServerLevel level, Vec3 feet) {
        if (!Double.isFinite(feet.x) || !Double.isFinite(feet.y) || !Double.isFinite(feet.z)) return false;
        AABB body = EntityType.PLAYER.getDimensions().makeBoundingBox(feet);
        var border = level.getWorldBorder();
        return body.minY >= level.getMinBuildHeight() && body.maxY <= level.getMaxBuildHeight()
                && body.minX >= border.getMinX() && body.maxX <= border.getMaxX()
                && body.minZ >= border.getMinZ() && body.maxZ <= border.getMaxZ();
    }

    private static void loadBodyChunks(ServerLevel level, Vec3 feet) {
        AABB body = EntityType.PLAYER.getDimensions().makeBoundingBox(feet);
        int minX = BlockPos.containing(body.minX, body.minY, body.minZ).getX() >> 4;
        int minZ = BlockPos.containing(body.minX, body.minY, body.minZ).getZ() >> 4;
        int maxX = BlockPos.containing(Math.nextDown(body.maxX), body.minY, Math.nextDown(body.maxZ)).getX() >> 4;
        int maxZ = BlockPos.containing(Math.nextDown(body.maxX), body.minY, Math.nextDown(body.maxZ)).getZ() >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) level.getChunk(x, z);
    }

    private RainCityPlacement() { }
}
