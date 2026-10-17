package com.zeropointsix.eraser.yuji.flare;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootTable;

public final class FlareAirdropLoot {
    public static final List<String> DEFAULT_TABLES = List.of(
            "minecraft:chests/ancient_city",
            "minecraft:chests/village/village_armorer",
            "minecraft:chests/village/village_butcher",
            "minecraft:chests/village/village_cartographer",
            "minecraft:chests/village/village_desert_house",
            "minecraft:chests/village/village_fisher",
            "minecraft:chests/village/village_fletcher",
            "minecraft:chests/village/village_mason",
            "minecraft:chests/village/village_plains_house",
            "minecraft:chests/village/village_savanna_house",
            "minecraft:chests/village/village_shepherd",
            "minecraft:chests/village/village_snowy_house",
            "minecraft:chests/village/village_taiga_house",
            "minecraft:chests/village/village_tannery",
            "minecraft:chests/village/village_temple",
            "minecraft:chests/village/village_toolsmith",
            "minecraft:chests/village/village_weaponsmith",
            "minecraft:chests/buried_treasure",
            "minecraft:chests/underwater_ruin_big",
            "minecraft:chests/underwater_ruin_small",
            "minecraft:chests/shipwreck_treasure",
            "minecraft:chests/desert_pyramid",
            "minecraft:chests/jungle_temple",
            "minecraft:chests/stronghold_corridor",
            "minecraft:chests/stronghold_crossing",
            "minecraft:chests/stronghold_library");

    public static boolean isChestTable(Object value) {
        if (!(value instanceof String name)) return false;
        ResourceLocation id = ResourceLocation.tryParse(name);
        return id != null && id.getNamespace().equals("minecraft") && id.getPath().startsWith("chests/");
    }

    private static List<ResourceLocation> available(ServerLevel level, List<? extends String> configured) {
        var tables = new LinkedHashSet<ResourceLocation>();
        for (String name : configured) {
            if (!isChestTable(name)) continue;
            ResourceLocation id = new ResourceLocation(name);
            if (level.getServer().getLootData().getLootTable(id) != LootTable.EMPTY) tables.add(id);
        }
        return new ArrayList<>(tables);
    }

    public static ResourceLocation choose(ServerLevel level, RandomSource random) {
        List<ResourceLocation> tables = available(level, FlareConfig.LOOT_TABLES.get());
        if (tables.isEmpty()) tables = available(level, DEFAULT_TABLES);
        // Keep a vanilla identifier even if a datapack removed every configured table.
        return tables.isEmpty() ? new ResourceLocation("minecraft", "chests/simple_dungeon")
                : tables.get(random.nextInt(tables.size()));
    }

    private FlareAirdropLoot() { }
}
