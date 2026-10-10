package com.zeropointsix.eraser.shaxia;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

public final class ShaxiaConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue BASE_DAMAGE, ATTACK_SPEED, NORMAL_BONUS, TRUE_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue RESTORE_MISSING, ALLOW_FAKE_PLAYERS, SCALE_COOLDOWN,
            ENEMY_FALLBACK, REQUIRE_PLAYER_INTENT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> INCLUDE, EXCLUDE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Server-authoritative values; restart the world after editing. Durability is fixed at 1561.");
        b.push("item");
        BASE_DAMAGE = b.defineInRange("baseAttackDamage", 4.0, 1.0, 1000.0);
        ATTACK_SPEED = b.defineInRange("attackSpeed", 2.4, 0.1, 20.0);
        b.pop().push("combat");
        NORMAL_BONUS = b.defineInRange("normalBonus", 2.5, 0.0, 1000.0);
        TRUE_DAMAGE = b.comment("Bypasses armor only; protection, resistance, absorption and totems still apply.")
                .defineInRange("trueDamage", 2.0, 0.0, 1000.0);
        SCALE_COOLDOWN = b.define("scaleWithCooldown", true);
        ALLOW_FAKE_PLAYERS = b.define("allowFakePlayers", false);
        b.pop().push("target");
        ENEMY_FALLBACK = b.define("enemyFallback", true);
        REQUIRE_PLAYER_INTENT = b.define("conditionalRequiresPlayerIntent", true);
        INCLUDE = b.defineListAllowEmpty("includeEntityTypes", List.of(), ShaxiaConfig::validId);
        EXCLUDE = b.defineListAllowEmpty("excludeEntityTypes", List.of(), ShaxiaConfig::validId);
        b.pop().push("factory");
        RESTORE_MISSING = b.define("restoreMissing", true);
        b.pop();
        SPEC = b.build();
    }

    private static boolean validId(Object value) {
        return value instanceof String id && ResourceLocation.tryParse(id) != null;
    }

    public static double number(ForgeConfigSpec.DoubleValue value, double fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    public static boolean flag(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    public static Set<String> ids(ForgeConfigSpec.ConfigValue<List<? extends String>> list) {
        if (!SPEC.isLoaded()) return Set.of();
        Set<String> ids = new HashSet<>();
        for (String entry : list.get()) {
            ResourceLocation location = ResourceLocation.tryParse(entry);
            if (location != null) ids.add(location.toString());
        }
        return ids;
    }

    private ShaxiaConfig() {}
}
