package com.zeropointsix.eraser.memory;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-owned combat tuning; hostile variants keep their original attributes. */
public final class SummonCombatConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue GUARD_RADIUS, SUXIA_DAMAGE, SUXIA_SPEED;
    public static final ForgeConfigSpec.DoubleValue WATER_GHOST_DAMAGE, WATER_GHOST_SPEED, WATER_GHOST_SWIM_SPEED;
    public static final ForgeConfigSpec.DoubleValue WATER_GHOST_GRAB_DAMAGE;
    public static final ForgeConfigSpec.IntValue SCAN_TICKS, SUXIA_ATTACK_TICKS, WATER_GHOST_ATTACK_TICKS;
    public static final ForgeConfigSpec.IntValue WATER_GHOST_REGENERATION, WATER_GHOST_GRAB_TICKS;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("guard");
        GUARD_RADIUS = builder.comment("Automatic hostile acquisition radius, centered on the owner (blocks).")
                .defineInRange("radius", 16.0, 1.0, 32.0);
        SCAN_TICKS = builder.comment("Ticks between target scans. Owner attack/defence always outranks ambient hostiles.")
                .defineInRange("scanTicks", 5, 1, 20);
        builder.pop().push("suxia");
        SUXIA_DAMAGE = builder.defineInRange("attackDamage", 20.0, 0.0, 100.0);
        SUXIA_SPEED = builder.defineInRange("movementSpeed", 0.32, 0.05, 1.0);
        SUXIA_ATTACK_TICKS = builder.comment("Melee interval in game ticks; minimum 10 respects vanilla hurt immunity.")
                .defineInRange("attackIntervalTicks", 12, 10, 100);
        builder.pop().push("waterGhost");
        WATER_GHOST_DAMAGE = builder.defineInRange("attackDamage", 8.0, 0.0, 100.0);
        WATER_GHOST_SPEED = builder.defineInRange("movementSpeed", 0.30, 0.05, 1.0);
        WATER_GHOST_SWIM_SPEED = builder.defineInRange("swimSpeed", 0.42, 0.05, 1.0);
        WATER_GHOST_ATTACK_TICKS = builder.defineInRange("attackIntervalTicks", 14, 10, 100);
        WATER_GHOST_REGENERATION = builder.comment("Regeneration amplifier: 0 = I, 1 = II.")
                .defineInRange("regenerationAmplifier", 1, 0, 4);
        WATER_GHOST_GRAB_DAMAGE = builder.defineInRange("grabDamage", 4.0, 0.0, 100.0);
        WATER_GHOST_GRAB_TICKS = builder.defineInRange("grabDamageIntervalTicks", 14, 10, 100);
        builder.pop();
        SPEC = builder.build();
    }

    public static double value(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
    public static int value(ForgeConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
    public static double guardRadius() { return value(GUARD_RADIUS); }
    private SummonCombatConfig() { }
}
