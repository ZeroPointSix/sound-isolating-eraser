package com.zeropointsix.eraser.yuji.city;

import net.minecraftforge.common.ForgeConfigSpec;

public final class RainCityConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue CAP, DISTANCE, SPAWN_BUDGET;
    public static final ForgeConfigSpec.DoubleValue HORDE_MULTIPLIER;
    static {
        var b = new ForgeConfigSpec.Builder();
        CAP = b.defineInRange("ambientCap", 300, 0, 2000);
        DISTANCE = b.defineInRange("spawnDistance", 96, 24, 128);
        SPAWN_BUDGET = b.comment("Spawn attempts per tick, including queued hordes; does not change their total size.")
                .defineInRange("spawnBudgetPerTick", 4, 1, 32);
        HORDE_MULTIPLIER = b.defineInRange("hordeMultiplier", 1., 0., 10.);
        SPEC = b.build();
    }
    private RainCityConfig() { }
}
