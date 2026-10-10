package com.zeropointsix.eraser.brickrot;

import java.util.ArrayDeque;
import net.minecraft.world.phys.Vec3;

/** Arc-length sampling keeps the body spacing stable when the head changes speed. */
public final class BrickrotTrail {
    private final ArrayDeque<Vec3> points = new ArrayDeque<>();
    private Vec3 current = Vec3.ZERO;
    private Vec3 backwards = new Vec3(0, 0, -1);

    public void reset(Vec3 position, Vec3 forward) {
        points.clear();
        current = position;
        backwards = forward.normalize().scale(-1);
        points.addFirst(position);
    }

    public void record(Vec3 position, Vec3 forward) {
        if (points.isEmpty() || current.distanceToSqr(position) > 64 * 64) reset(position, forward);
        current = position;
        if (points.peekFirst().distanceToSqr(position) >= 0.25) points.addFirst(position);
        while (points.size() > 192) points.removeLast();
    }

    public Vec3 sample(double offset) {
        Vec3 previous = current;
        Vec3 direction = backwards;
        for (Vec3 point : points) {
            double length = previous.distanceTo(point);
            if (length > 1.0E-6) {
                direction = point.subtract(previous).scale(1 / length);
                if (offset <= length) return previous.add(direction.scale(offset));
                offset -= length;
            }
            previous = point;
        }
        return previous.add(direction.scale(offset));
    }
}
