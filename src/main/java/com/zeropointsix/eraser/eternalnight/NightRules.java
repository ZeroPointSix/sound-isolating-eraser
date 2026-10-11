package com.zeropointsix.eraser.eternalnight;

import java.util.LinkedHashSet;
import java.util.Set;

/** Deterministic rules shared by the server adapters and regression checks. */
public final class NightRules {
    public static final long DAY_TICKS = 24000L;
    public static final int DEFAULT_GUARANTEE = 100;
    public static final long DEFAULT_EXIT_TICKS = 6000L;

    private NightRules() { }

    public static double entryProbability(long restTicks, double base, double daily, double cap) {
        probability(base);
        probability(daily);
        probability(cap);
        return Math.min(cap, base + daily * (Math.max(0L, restTicks) / DAY_TICKS));
    }

    public static boolean roll(double probability, double sample) {
        probability(probability);
        if (!Double.isFinite(sample) || sample < 0 || sample >= 1) {
            throw new IllegalArgumentException("Random sample must be in [0, 1)");
        }
        return sample < probability;
    }

    private static void probability(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("Probability must be in [0, 1]");
        }
    }

    public static int milestone(int count) {
        return count == 25 || count == 50 || count == 75 || count == 90 ? count : 0;
    }

    public record DoorResult(boolean counted, int count, boolean exit) { }

    public static final class Doors {
        private final Set<Long> opened = new LinkedHashSet<>();

        public Doors(long[] persisted) {
            for (long door : persisted) opened.add(door);
        }

        public DoorResult open(long canonicalDoor, int guarantee, double chance, double sample) {
            if (guarantee < 1 || guarantee > 10000) throw new IllegalArgumentException("Invalid guarantee");
            boolean lucky = roll(chance, sample);
            if (!opened.add(canonicalDoor)) return new DoorResult(false, opened.size(), false);
            return new DoorResult(true, opened.size(), opened.size() >= guarantee || lucky);
        }

        public int count() { return opened.size(); }
        public long[] save() { return opened.stream().mapToLong(Long::longValue).toArray(); }
    }
}
