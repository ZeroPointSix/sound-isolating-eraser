package com.zeropointsix.eraser.gravity;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server settings are synchronized by Forge so preview and effect use the same dimensions. */
public final class GravityConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue RANGE, FADE, LIMIT, HEIGHT, DURATION, COOLDOWN;
    public static final ForgeConfigSpec.IntValue SLOWNESS, MIN_DISTANCE, MAX_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue THRESHOLD, DAMAGE, SURFACE_CHANCE;
    public static final int SIZE = 5;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder().push("gravity_jade");
        RANGE = b.defineInRange("gravitySenseRange", 16, 1, 64);
        THRESHOLD = b.defineInRange("movementThreshold", 0.02, 0.0001, 2.0);
        FADE = b.defineInRange("senseFadeTicks", 4, 0, 40);
        LIMIT = b.defineInRange("maxHighlightedEntities", 64, 1, 256);
        HEIGHT = b.defineInRange("gravityFieldHeight", 5, 1, 31);
        DURATION = b.defineInRange("gravityFieldDurationTicks", 300, 20, 1200);
        DAMAGE = b.defineInRange("gravityDamagePerSecond", 1.0, 0.0, 100.0);
        SLOWNESS = b.defineInRange("gravitySlownessAmplifier", 1, 0, 4);
        COOLDOWN = b.defineInRange("gravityFieldCooldownTicks", 600, 20, 72000);
        MIN_DISTANCE = b.defineInRange("targetMinDistance", 3, 1, 64);
        MAX_DISTANCE = b.defineInRange("targetMaxDistance", 20, 1, 64);
        SURFACE_CHANCE = b.defineInRange("surfaceBreakChance", 0.25, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    public static int minDistance() {
        return Math.min(MIN_DISTANCE.get(), MAX_DISTANCE.get());
    }

    private GravityConfig() { }
}
