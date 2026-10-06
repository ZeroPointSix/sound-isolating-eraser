package com.zeropointsix.eraser.gravity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GravityGeometry {
    public static AABB bounds(BlockPos center, int height) {
        int bottom = center.getY() - height / 2;
        return new AABB(center.getX() - 2, bottom, center.getZ() - 2,
                center.getX() + 3, bottom + height, center.getZ() + 3);
    }

    public static BlockPos target(Vec3 eye, Vec3 direction, double distance) {
        return BlockPos.containing(eye.add(direction.scale(distance)));
    }

    public static boolean inRange(Vec3 eye, BlockPos center, double min, double max) {
        // Validate the selected cell, not its center: floor-snapping can move a point by almost a block.
        double near = 0;
        double far = 0;
        double[] eyes = {eye.x, eye.y, eye.z};
        int[] cells = {center.getX(), center.getY(), center.getZ()};
        for (int axis = 0; axis < 3; axis++) {
            double low = cells[axis] - eyes[axis];
            double high = low + 1;
            double closest = low > 0 ? low : Math.min(high, 0);
            near += closest * closest;
            far += Math.max(low * low, high * high);
        }
        return near <= max * max + 1.0e-6 && far >= min * min - 1.0e-6;
    }

    private GravityGeometry() { }
}
