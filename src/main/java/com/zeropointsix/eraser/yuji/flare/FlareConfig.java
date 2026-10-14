package com.zeropointsix.eraser.yuji.flare;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FlareConfig {
    public static final double MIN_DESCENT_SPEED = .05;
    public static ForgeConfigSpec.IntValue STACK_SIZE, COOLDOWN, RED_DELAY, RED_LIFETIME,
            TAUNT_INTERVAL, YELLOW_DELAY, YELLOW_LIFETIME, LIGHT_RADIUS, GREEN_DELAY,
            DROP_HEIGHT, DROP_ATTEMPTS, EMPTY_LIFETIME, LOOT_ROLLS, SUPPLY_GUNS,
            SUPPLY_RED, SUPPLY_YELLOW, SUPPLY_GREEN;
    public static ForgeConfigSpec.DoubleValue RED_HEALTH, RED_ARMOR, RED_KNOCKBACK,
            TAUNT_RADIUS, DROP_MIN, DROP_MAX, DESCENT_SPEED, COMMON_CHANCE, GREEN_CHANCE;
    public static ForgeConfigSpec.BooleanValue CREATIVE_FREE, ALL_DIMENSIONS;

    public static void define(ForgeConfigSpec.Builder b) {
        b.push("flare");
        STACK_SIZE = b.defineInRange("ammunitionStackSize", 16, 1, 64);
        COOLDOWN = b.defineInRange("cooldownTicks", 20, 1, 1200);
        CREATIVE_FREE = b.define("creativeDoesNotConsume", true);
        ALL_DIMENSIONS = b.define("allowAllDimensions", true);
        RED_DELAY = b.defineInRange("redFlightTicks", 60, 1, 1200);
        RED_LIFETIME = b.defineInRange("decoyLifetimeTicks", 600, 1, 72000);
        RED_HEALTH = b.defineInRange("decoyHealth", 30., 1., 1000.);
        RED_ARMOR = b.defineInRange("decoyArmor", 6., 0., 30.);
        RED_KNOCKBACK = b.defineInRange("decoyKnockbackResistance", 1., 0., 1.);
        TAUNT_RADIUS = b.defineInRange("tauntRadius", 24., 1., 64.);
        TAUNT_INTERVAL = b.defineInRange("tauntIntervalTicks", 10, 1, 100);
        YELLOW_DELAY = b.defineInRange("yellowFlightTicks", 30, 1, 1200);
        YELLOW_LIFETIME = b.defineInRange("illuminationTicks", 1200, 1, 72000);
        LIGHT_RADIUS = b.defineInRange("illuminationRadius", 24, 1, 32);
        GREEN_DELAY = b.defineInRange("airdropDelayTicks", 80, 1, 1200);
        DROP_MIN = b.defineInRange("airdropMinDistance", 8., 1., 64.);
        DROP_MAX = b.defineInRange("airdropMaxDistance", 24., 1., 64.);
        DROP_HEIGHT = b.defineInRange("airdropHeight", 40, 4, 128);
        DROP_ATTEMPTS = b.defineInRange("airdropLocationAttempts", 48, 1, 256);
        DESCENT_SPEED = b.defineInRange("airdropDescentSpeed", .35, MIN_DESCENT_SPEED, 1.);
        EMPTY_LIFETIME = b.defineInRange("emptyCrateTicks", 100, 1, 72000);
        LOOT_ROLLS = b.defineInRange("airdropLootRolls", 1, 1, 27);
        SUPPLY_GUNS = b.defineInRange("supplyGuns", 1, 0, 8);
        SUPPLY_RED = b.defineInRange("supplyRed", 2, 0, 64);
        SUPPLY_YELLOW = b.defineInRange("supplyYellow", 2, 0, 64);
        SUPPLY_GREEN = b.defineInRange("supplyGreen", 1, 0, 64);
        COMMON_CHANCE = b.defineInRange("cityRedYellowChance", .08, 0., 1.);
        GREEN_CHANCE = b.defineInRange("mallGreenChance", .02, 0., 1.);
        b.pop();
    }

    private FlareConfig() { }
}
