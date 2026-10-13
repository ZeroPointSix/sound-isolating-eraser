package com.zeropointsix.eraser.gravity;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GravityGeometry {
    public static AABB bounds(Level level, Vec3 center, double radius, int below, int above) {
        var border = level.getWorldBorder();
        double x = Math.max(center.x - radius, border.getMinX());
        double y = Math.max(center.y - below, level.getMinBuildHeight());
        double z = Math.max(center.z - radius, border.getMinZ());
        return new AABB(x, y, z,
                Math.max(x, Math.min(center.x + radius, border.getMaxX())),
                Math.max(y, Math.min(center.y + above, level.getMaxBuildHeight())),
                Math.max(z, Math.min(center.z + radius, border.getMaxZ())));
    }

    public static boolean inCircle(Vec3 center, double radius, double x, double z) {
        double dx = x - center.x, dz = z - center.z;
        return dx * dx + dz * dz <= radius * radius;
    }

    public static boolean contains(Vec3 center, double radius, AABB bounds, Vec3 point) {
        return point.x >= bounds.minX && point.x <= bounds.maxX
                && point.z >= bounds.minZ && point.z <= bounds.maxZ
                && point.y >= bounds.minY && point.y < bounds.maxY
                && inCircle(center, radius, point.x, point.z);
    }

    public static boolean chunksLoaded(Level level, Vec3 center, double radius, AABB box) {
        if (box.getXsize() <= 0 || box.getYsize() <= 0 || box.getZsize() <= 0) return false;
        for (int x = Mth.floor(box.minX) >> 4; x <= (Mth.floor(Math.nextDown(box.maxX)) >> 4); x++) {
            for (int z = Mth.floor(box.minZ) >> 4; z <= (Mth.floor(Math.nextDown(box.maxZ)) >> 4); z++) {
                double nearX = Mth.clamp(center.x, x * 16.0, x * 16.0 + 16);
                double nearZ = Mth.clamp(center.z, z * 16.0, z * 16.0 + 16);
                if (inCircle(center, radius, nearX, nearZ) && !level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    private GravityGeometry() { }
}
