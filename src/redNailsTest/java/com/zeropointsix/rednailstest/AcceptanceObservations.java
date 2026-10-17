package com.zeropointsix.rednailstest;

/** Pure QA bookkeeping; it never moves entities or substitutes for native input. */
final class AcceptanceObservations {
    private AcceptanceObservations() { }

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
