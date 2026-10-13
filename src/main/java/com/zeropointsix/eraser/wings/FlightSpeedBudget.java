package com.zeropointsix.eraser.wings;

/** Bounded server-tick allowance for client movement packets arriving in bursts. */
public final class FlightSpeedBudget {
    private boolean initialized;
    private long lastTick;
    private double remaining;

    public boolean accept(double distance, double perTick, long tick) {
        if (!Double.isFinite(distance) || distance < 0
                || !Double.isFinite(perTick * 2) || perTick <= 0) {
            remaining = 0;
            lastTick = initialized ? Math.max(lastTick, tick) : tick;
            initialized = true;
            return false;
        }
        double capacity = perTick * 2;
        if (!initialized) {
            remaining = capacity;
            lastTick = tick;
            initialized = true;
        } else {
            remaining = Math.min(remaining, capacity);
            if (tick < lastTick) {
                remaining = 0;
                return false;
            }
            if (tick > lastTick) {
                // Refill from elapsed server ticks, never packet count, capped at two ticks.
                int elapsed = tick == lastTick + 1 ? 1 : 2;
                remaining = Math.min(capacity, remaining + perTick * elapsed);
                lastTick = tick;
            }
        }
        if (distance > remaining + 1.0e-9) {
            remaining = 0;
            return false;
        }
        remaining = Math.max(0, remaining - distance);
        return true;
    }
}
