package com.zeropointsix.eraser.shadow;

public final class ShadowLight {
    public static final int DARK = 0;
    public static final int DIM = 1;
    public static final int BRIGHT = 2;
    public static final int SPAWN_TICKS = 24;
    public static final int DIM_RETREAT_TICKS = 60;
    public static final int RETREAT_COOLDOWN_TICKS = 40;

    public static int tier(int brightness) {
        return brightness >= 11 ? BRIGHT : brightness >= 7 ? DIM : DARK;
    }

    public static double speedMultiplier(int tier) {
        return tier == BRIGHT ? 0.25D : tier == DIM ? 0.6D : 1D;
    }

    public static float damageMultiplier(int tier) {
        return tier == BRIGHT ? 2F : tier == DIM ? 1F : 0.5F;
    }

    private ShadowLight() { }
}
