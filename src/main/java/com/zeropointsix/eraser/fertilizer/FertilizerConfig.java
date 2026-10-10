package com.zeropointsix.eraser.fertilizer;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FertilizerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue CAPACITY, BONE_MEAL, THRESHOLD, TREE_COUNT,
            GROVE_RADIUS, MEADOW_RADIUS, AURA_THRESHOLD, NUTRITION, REGEN_TICKS;
    public static final ForgeConfigSpec.DoubleValue HEAL, SATURATION;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        CAPACITY = b.defineInRange("bagCapacity", 64, 1, 4096);
        BONE_MEAL = b.defineInRange("boneMealAttempts", 10, 1, 64);
        THRESHOLD = b.comment("Trial values: 5 or 10 successful uses on the same plant.")
                .defineInRange("groveThreshold", 5, 2, 100);
        TREE_COUNT = b.comment("Total trees including the original tree; finalized Notion table: 15.")
                .defineInRange("groveTreeCount", 15, 1, 32);
        GROVE_RADIUS = b.defineInRange("groveRadius", 24, 8, 32);
        MEADOW_RADIUS = b.defineInRange("meadowRadius", 12, 1, 24);
        AURA_THRESHOLD = b.defineInRange("auraThreshold", 10, 1, 125);
        NUTRITION = b.defineInRange("fruitNutrition", 8, 1, 20);
        SATURATION = b.defineInRange("fruitSaturation", 1.2, 0.0, 2.0);
        HEAL = b.comment("Finalized functional paragraph: 6 HP; old summary is obsolete.")
                .defineInRange("fruitHealHP", 6.0, 0.0, 20.0);
        REGEN_TICKS = b.defineInRange("fruitRegenerationTicks", 3600, 1, 72000);
        SPEC = b.build();
    }

    private FertilizerConfig() {}
    public static <T> T value(ForgeConfigSpec.ConfigValue<T> option) {
        return SPEC.isLoaded() ? option.get() : option.getDefault();
    }
}
