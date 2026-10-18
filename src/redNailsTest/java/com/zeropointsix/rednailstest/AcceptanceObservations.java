package com.zeropointsix.rednailstest;

/** Pure QA bookkeeping; it never moves entities or substitutes for native input. */
final class AcceptanceObservations {
    private AcceptanceObservations() { }

    static final double SPEED_CADENCE_DRIFT = 0.10;
    static final int SPEED_BOUNDARY_TICKS = 2;

    static boolean speedRateMatches(double measured, double expected, int serverTicks) {
        return serverTicks > 0 && expected > 0 && Double.isFinite(expected) && Double.isFinite(measured)
                && Math.abs(measured - expected) <= expected
                    * (SPEED_CADENCE_DRIFT + (double) SPEED_BOUNDARY_TICKS / serverTicks);
    }

    static boolean maintainsWarmupSpeed(double measured, double warmup, int serverTicks) {
        // Natural hits can accelerate a swimmer. They must not count as client slowdown.
        return serverTicks > 0 && warmup > 0 && Double.isFinite(warmup) && Double.isFinite(measured)
                && (measured >= warmup || speedRateMatches(measured, warmup, serverTicks));
    }

    static boolean requiresRearCatch(String scenario) {
        return switch (scenario) {
            case "swimmer" -> true;
            case "boat", "calibration" -> false;
            default -> throw new IllegalArgumentException("Unknown speed scenario: " + scenario);
        };
    }

    static int vanillaAir(int air) {
        int next = air - 1;
        return next == -20 ? 0 : next;
    }

    static int grabAir(int air) {
        int next = air - 3;
        return next <= -20 ? 0 : next;
    }

    static boolean validAirChange(int before, int after) {
        // The two entities can tick in either order; each decrement has its own reset.
        return after == grabAir(vanillaAir(before)) || after == vanillaAir(grabAir(before));
    }

    static final class BoatHistory {
        String uuid = "";
        int lastTick = -1;
        int movingTicks;
        double x, y, z, lastStep;
        boolean forward;

        void observe(String id, int tick, double nextX, double nextY, double nextZ, boolean driving) {
            boolean continuous = uuid.equals(id) && tick == lastTick + 1;
            lastStep = continuous ? Math.hypot(nextX - x, nextZ - z) : 0;
            movingTicks = continuous && forward && driving && lastStep > 0.01 ? movingTicks + 1 : 0;
            uuid = id; lastTick = tick; x = nextX; y = nextY; z = nextZ; forward = driving;
        }

        boolean movingAtDismount(int tick) {
            return tick == lastTick + 1 && forward && movingTicks >= 3;
        }
    }
}
