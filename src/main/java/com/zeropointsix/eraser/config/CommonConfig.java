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
}
