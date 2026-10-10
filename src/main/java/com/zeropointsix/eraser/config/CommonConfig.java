package com.zeropointsix.eraser.config;

import com.zeropointsix.eraser.ModMain;
import net.minecraftforge.common.ForgeConfigSpec;

/** Server-owned eraser rules are synchronized before a client enters the world. */
public final class CommonConfig {
    public static final ForgeConfigSpec SPEC;

    /** Cells raised per click, including the anchor cell. The core rule defaults to 5. */
    public static final ForgeConfigSpec.IntValue BARRIER_HEIGHT;
    /** One successful column costs 1 durability point. Demo default: 64. */
    public static final ForgeConfigSpec.IntValue ERASER_DURABILITY;
    public static final ForgeConfigSpec.IntValue PILL_PACK_USES, PILL_DURATION, WITHDRAWAL_DURATION, USE_DURATION;
    public static final ForgeConfigSpec.IntValue STRENGTH, SPEED, HASTE, RESISTANCE;
    public static final ForgeConfigSpec.IntValue WITHDRAWAL_SLOWNESS, WITHDRAWAL_FATIGUE, WITHDRAWAL_WEAKNESS;
    public static final ForgeConfigSpec.BooleanValue WITHDRAWAL_DARKNESS, WITHDRAWAL_NAUSEA;
    public static final ForgeConfigSpec.BooleanValue ALLOW_MILK_CURE, CONSUME_IN_CREATIVE, SHOW_PILL_HUD;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("eraser_wall");
        BARRIER_HEIGHT = builder
                .comment("Cells per wall column, anchor included. The LEVEL property supports at most "
                        + ModMain.MAX_HEIGHT + ", so the range is clamped to 1-" + ModMain.MAX_HEIGHT
                        + ". Wall hardness and blast resistance are fixed at the dirt-level 0.5 "
                        + "from the design spec (baked into block properties at registration).")
                .defineInRange("barrierHeight", ModMain.MAX_HEIGHT, 1, ModMain.MAX_HEIGHT);
        builder.pop();
        builder.push("eraser_item");
        ERASER_DURABILITY = builder
                .comment("Eraser durability. One successful column costs 1 point.")
                .defineInRange("eraserDurability", 64, 1, 32767);
        builder.pop();
        builder.push("enhancement_pill");
        PILL_PACK_USES = builder.defineInRange("pillPackUses", 12, 1, 64);
        PILL_DURATION = builder.defineInRange("pillDurationTicks", 48000, 1, 12000000);
        WITHDRAWAL_DURATION = builder.defineInRange("withdrawalDurationTicks", 12000, 1, 12000000);
        USE_DURATION = builder.defineInRange("useDurationTicks", 32, 1, 1200);
        STRENGTH = builder.defineInRange("strengthAmplifier", 1, 0, 10);
        SPEED = builder.defineInRange("speedAmplifier", 1, 0, 10);
        HASTE = builder.defineInRange("hasteAmplifier", 1, 0, 10);
        RESISTANCE = builder.defineInRange("resistanceAmplifier", 0, 0, 3);
        WITHDRAWAL_SLOWNESS = builder.defineInRange("withdrawalSlownessAmplifier", 1, 0, 5);
        WITHDRAWAL_FATIGUE = builder.defineInRange("withdrawalMiningFatigueAmplifier", 1, 0, 10);
        WITHDRAWAL_WEAKNESS = builder.defineInRange("withdrawalWeaknessAmplifier", 1, 0, 10);
        WITHDRAWAL_DARKNESS = builder.define("withdrawalDarknessEnabled", true);
        WITHDRAWAL_NAUSEA = builder.define("withdrawalNauseaEnabled", false);
        ALLOW_MILK_CURE = builder.define("allowMilkCureWithdrawal", false);
        CONSUME_IN_CREATIVE = builder.define("consumeInCreative", false);
        SHOW_PILL_HUD = builder.define("showHudTimer", true);
        builder.pop();
        SPEC = builder.build();
    }

    private CommonConfig() {
    }

    public static int barrierHeight() {
        return SPEC.isLoaded() ? BARRIER_HEIGHT.get() : ModMain.MAX_HEIGHT;
    }

    public static int durability() {
        return SPEC.isLoaded() ? ERASER_DURABILITY.get() : 64;
    }

    public static int value(ForgeConfigSpec.IntValue value, int fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    public static boolean value(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }
}
