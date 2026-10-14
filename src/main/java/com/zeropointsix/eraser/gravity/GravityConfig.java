package com.zeropointsix.eraser.gravity;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server settings are synchronized by Forge for public field rendering. */
public final class GravityConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue RANGE, FADE, LIMIT, RADIUS, BELOW, ABOVE, DURATION, COOLDOWN;
    public static final ForgeConfigSpec.IntValue SLOWNESS, ALLY_LINGER;
    public static final ForgeConfigSpec.DoubleValue THRESHOLD, SURFACE_CHANCE, HOVER_HEIGHT, SLAM_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAX_PERCENT, CAP_DAMAGE, REFERENCE_HEALTH, REFERENCE_PERCENT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder().push("gravity_jade");
        RANGE = b.defineInRange("gravitySenseRange", 16, 1, 64);
        THRESHOLD = b.defineInRange("movementThreshold", 0.02, 0.0001, 2.0);
        FADE = b.defineInRange("senseFadeTicks", 4, 0, 40);
        LIMIT = b.defineInRange("maxHighlightedEntities", 64, 1, 256);
        RADIUS = b.defineInRange("gravityFieldRadius", 16, 1, 64);
        BELOW = b.defineInRange("gravityFieldBelow", 8, 0, 64);
        ABOVE = b.defineInRange("gravityFieldAbove", 16, 1, 64);
        DURATION = b.defineInRange("gravityFieldDurationTicks", 300, 20, 1200);
        MAX_PERCENT = b.defineInRange("maxPercent", 0.20, 0.0001, 1.0);
        CAP_DAMAGE = b.defineInRange("capDamage", 10.0, 0.0, 1000.0);
        REFERENCE_HEALTH = b.defineInRange("referenceHealth", 300.0, 1.0, 10000000.0);
        REFERENCE_PERCENT = b.defineInRange("referencePercent", 0.01, 0.000001, 1.0);
        SLOWNESS = b.defineInRange("gravitySlownessAmplifier", 4, 0, 4);
        COOLDOWN = b.defineInRange("gravityFieldCooldownTicks", 1200, 20, 72000);
        HOVER_HEIGHT = b.defineInRange("levitationHoverHeight", 3.0, 0.0, 16.0);
        SLAM_MULTIPLIER = b.defineInRange("slamDamageMultiplier", 3.0, 0.0, 20.0);
        ALLY_LINGER = b.defineInRange("allyBuffLingerTicks", 80, 1, 1200);
        SURFACE_CHANCE = b.defineInRange("surfaceBreakChance", 0.25, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private GravityConfig() { }
}
