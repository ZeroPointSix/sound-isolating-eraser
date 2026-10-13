package com.zeropointsix.eraser.wings;

/** Server-authorized inertia envelope; changing a tier never refills movement credit. */
public final class FlightSpeedTransition {
    private static final int SYNC_GRACE_TICKS = 4;
    private double initialSpeed;
    private double target;
    private double retention;
    private long graceUntil;
    private long changedAt;
    private boolean initialized;

    public double speed(double targetSpeed, long tick) {
        if (!initialized) return targetSpeed;
        return Math.max(targetSpeed, inertia(tick));
    }

    private double inertia(long tick) {
        long start = initialSpeed > target ? graceUntil : changedAt;
        long elapsed = Math.max(0, tick - start);
        return target + (initialSpeed - target) * Math.pow(retention, elapsed);
    }

    public void change(double oldSpeed, double newSpeed, double acceleration, long tick) {
        if (initialized) tick = Math.max(tick, changedAt);
        double inherited = initialized ? inertia(tick) : oldSpeed;
        // A higher target authorizes its tier, but only elapsed server ticks can
        // turn it into inertia that survives a later downgrade. Same-tick H/H
        // therefore preserves both the speed history and its decay deadline.
        boolean accelerated = initialized && target > initialSpeed && tick > changedAt;
        boolean fresh = !initialized || accelerated;
        initialSpeed = inherited;
        target = newSpeed;
        retention = 1.0 - Math.max(0.0, Math.min(1.0, acceleration / 20.0));
        graceUntil = fresh ? tick + SYNC_GRACE_TICKS : Math.max(tick, graceUntil);
        changedAt = tick;
        initialized = true;
    }
}
