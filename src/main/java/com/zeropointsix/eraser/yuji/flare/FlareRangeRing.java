package com.zeropointsix.eraser.yuji.flare;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared geometry for the rendered, server-synchronized decoy boundary. */
public final class FlareRangeRing {
    public static final int SEGMENTS = 128;
    public static final double HEIGHT = .12;
    public static final double HALF_WIDTH = .24;

    public static Vec3 point(double radius, int index) {
        double angle = Math.PI * 2 * Math.floorMod(index, SEGMENTS) / SEGMENTS;
        return new Vec3(Math.cos(angle) * radius, HEIGHT, Math.sin(angle) * radius);
    }

    public static Vec3 vertex(double radius, double halfWidth, int segment, int corner) {
        return point(radius + (corner < 2 ? -halfWidth : halfWidth),
                segment + (corner == 1 || corner == 2 ? 1 : 0));
    }

    public static AABB bounds(Vec3 center, double radius) {
        double extent = radius + HALF_WIDTH;
        return new AABB(center.x - extent, center.y, center.z - extent,
                center.x + extent, center.y + HEIGHT + .02, center.z + extent);
    }

    private FlareRangeRing() { }
}
