package com.zeropointsix.eraser.brickrot;

import net.minecraft.world.phys.Vec3;

/** Pure settle curve for surfacing body parts. No world reads or writes. */
public final class BrickrotEmergence {
    private static final int HOLD_TICKS = 20;
    private static final int LERP_TICKS = 20;
    private static final int SETTLED_TICKS = 60;
    private static final double ZERO_LENGTH_SQR = 1.0E-12;

    private BrickrotEmergence() {}

    /**
     * Keeps {@code undergroundPoint} for the first 20 ticks, then lerps part
     * {@code partIndex} from tick {@code 20 + partIndex * 2} over 20 ticks to
     * {@code surfaceHead} minus the horizontal unit of {@code forward} times
     * {@code offset}. The final Y is not below {@code surfaceHead.y}. Every part is
     * settled by tick 60. A zero or vertical {@code forward} skips the offset.
     */
    public static Vec3 settle(Vec3 undergroundPoint, Vec3 surfaceHead, Vec3 forward,
                              double offset, int partIndex, int elapsedTicks) {
        Vec3 settled = settled(surfaceHead, forward, offset);
        if (elapsedTicks >= SETTLED_TICKS) return settled;
        if (elapsedTicks < HOLD_TICKS) return undergroundPoint;
        int start = HOLD_TICKS + Math.max(0, partIndex) * 2;
        if (elapsedTicks < start) return undergroundPoint;
        double t = Math.min(1.0, (elapsedTicks - start) / (double) LERP_TICKS);
        return undergroundPoint.lerp(settled, t);
    }

    private static Vec3 settled(Vec3 surfaceHead, Vec3 forward, double offset) {
        Vec3 horizontal = new Vec3(forward.x, 0.0, forward.z);
        double lengthSqr = horizontal.lengthSqr();
        Vec3 target = lengthSqr < ZERO_LENGTH_SQR ? surfaceHead
                : surfaceHead.subtract(horizontal.scale(offset / Math.sqrt(lengthSqr)));
        return target.y < surfaceHead.y ? new Vec3(target.x, surfaceHead.y, target.z) : target;
    }
}
