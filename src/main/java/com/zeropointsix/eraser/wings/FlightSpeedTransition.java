package com.zeropointsix.eraser.wings;

/** Server-authorized inertia envelope; changing a tier never refills movement credit. */
public final class FlightSpeedTransition {
    private static final int SYNC_GRACE_TICKS = 4;
    private double initialSpeed;
    private double target;
    private double retention;
    private long graceUntil;
    private boolean initialized;

    public double speed(double targetSpeed, long tick) {
        if (!initialized) return targetSpeed;
        long elapsed = Math.max(0, tick - graceUntil);
        double inherited = target + Math.max(0, initialSpeed - target) * Math.pow(retention, elapsed);
        return Math.max(targetSpeed, inherited);
    }

    public void change(double oldSpeed, double newSpeed, double acceleration, long tick) {
        double inherited = speed(oldSpeed, tick);
        // Carry an existing decay deadline through lower-tier switches. Otherwise
        // toggling HOVER/CRUISE could renew the old STORM grace forever.
        boolean fresh = !initialized || inherited <= oldSpeed + 1.0e-6;
        initialSpeed = Math.max(newSpeed, inherited);
        target = newSpeed;
        retention = 1.0 - Math.max(0.0, Math.min(1.0, acceleration / 20.0));
        graceUntil = fresh ? tick + SYNC_GRACE_TICKS : Math.max(tick, graceUntil);
        initialized = initialSpeed > newSpeed;
    }
}
