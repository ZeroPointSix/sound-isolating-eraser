package com.zeropointsix.eraser.eternalnight;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class NightConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue ENTRY_BASE, ENTRY_DAILY, ENTRY_CAP, EXIT_CHANCE;
    public static final ForgeConfigSpec.IntValue DOOR_GUARANTEE, EXIT_SECONDS, SHADOW_CAP, NEON_CAP;
    public static final ForgeConfigSpec.BooleanValue SPAWNING, RESET_ON_DEATH, DISABLE_GROWTH;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTRY_DIMENSIONS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("eternalNight");
        ENABLED = b.comment("Natural entry also requires the original city templates to be installed.")
                .define("enabled", true);
        ENTRY_DIMENSIONS = b.defineListAllowEmpty("entryDimensions", List.of("minecraft:overworld"),
                value -> value instanceof String s && net.minecraft.resources.ResourceLocation.tryParse(s) != null);
        ENTRY_BASE = b.defineInRange("entryBase", 0.02, 0.0, 1.0);
        ENTRY_DAILY = b.defineInRange("entryPerSleeplessDay", 0.08, 0.0, 1.0);
        ENTRY_CAP = b.defineInRange("entryCap", 0.50, 0.0, 1.0);
        EXIT_CHANCE = b.defineInRange("newDoorExitChance", 0.01, 0.0, 1.0);
        DOOR_GUARANTEE = b.defineInRange("doorGuarantee", 100, 1, 10000);
        EXIT_SECONDS = b.defineInRange("sharedExitSeconds", 300, 1, 3600);
        SPAWNING = b.define("spawnCityMonsters", true);
        SHADOW_CAP = b.defineInRange("nearbyShadowCap", 10, 0, 100);
        NEON_CAP = b.defineInRange("nearbyNeonCap", 12, 0, 100);
        RESET_ON_DEATH = b.comment("v0.1 proposed default: leave the city on death; vanilla drops/keepInventory are unchanged.")
                .define("returnAndResetOnDeath", true);
        DISABLE_GROWTH = b.define("disablePlantGrowth", true);
        b.pop();
        SPEC = b.build();
    }

    private NightConfig() { }
}
