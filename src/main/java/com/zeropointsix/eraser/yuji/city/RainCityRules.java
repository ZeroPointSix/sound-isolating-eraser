package com.zeropointsix.eraser.yuji.city;

public final class RainCityRules {
    public static final long DAY = 24000;
    public static final long FIRST_NIGHT = 12000;

    public static int day(long elapsed) {
        return elapsed < FIRST_NIGHT ? 0 : (int) Math.min(1_000_000, 1 + (elapsed - FIRST_NIGHT) / DAY);
    }

    public static boolean night(long elapsed) { return Math.floorMod(elapsed, DAY) >= FIRST_NIGHT; }
    public static int cap(int day, boolean night, int maximum) {
        return (int) Math.min(maximum, Math.ceil((day == 0 ? 30 : 60L + 25L * (day - 1)) * (night ? 1.5 : 1)));
    }
    public static int horde(int day, double multiplier) {
        return day <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.round((30L + 20L * day) * (day % 3 == 0 ? 2 : 1) * multiplier));
    }
    public static double specialWeight(int day) { return day == 0 ? 0 : 1 + .35 * (day - 1); }
    public static double strength(int day) { return 1 + .05 * Math.min(20, Math.max(0, day)); }
    public static double speed(int day) { return 1 + .05 * Math.min(3, Math.max(0, day) / 5); }
    public static int layers(int day) { return Math.min(6, 1 + Math.max(0, day) / 3); }
    public static int chennu(int day) { return day == 0 ? 0 : 1 + (day - 1) / 2; }
    private RainCityRules() { }
}
