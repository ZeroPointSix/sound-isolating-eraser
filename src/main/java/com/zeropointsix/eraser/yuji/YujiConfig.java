package com.zeropointsix.eraser.yuji;

import net.minecraftforge.common.ForgeConfigSpec;

public final class YujiConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue AFFECT_PETS, AFFECT_VILLAGERS, CAMERA_TURN;
    public static final ForgeConfigSpec.DoubleValue HOSTILE_BUFF, PULL_STRENGTH, MAX_CURSE_DAMAGE;
    public static final ForgeConfigSpec.IntValue MAX_LAYERS;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("betrayed");
        AFFECT_PETS = b.define("affectPets", true);
        AFFECT_VILLAGERS = b.define("affectVillagers", true);
        HOSTILE_BUFF = b.defineInRange("hostileBuff", .3, 0, 2);
        b.pop().push("enthralled");
        PULL_STRENGTH = b.defineInRange("pullStrength", 1., 0, 3);
        CAMERA_TURN = b.define("cameraTurn", true);
        b.pop().push("cursed");
        MAX_CURSE_DAMAGE = b.defineInRange("maxDamage", 3., 1, 20);
        b.pop().push("tanyu");
        MAX_LAYERS = b.defineInRange("maxLayers", 12, 1, 12);
        b.pop();
        SPEC = b.build();
    }
    private YujiConfig() {}
}
