package com.zeropointsix.eraser.item;

import com.google.gson.JsonParser;
import com.zeropointsix.eraser.ModMain;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Registry IDs are the stable contract shared by tooltips, tests and the optional guidebook. */
public final class ItemGuideCatalog {
    public static final List<String> SECTIONS = List.of("summary", "usage", "controls", "cooldown", "cost", "lore");
    public record Entry(ResourceLocation id, String module, String category, String textStatus, List<String> controls) {
        public String key(String section) {
            return "guide." + id.getNamespace() + "." + id.getPath() + "." + section;
        }
    }

    private static final Map<ResourceLocation, Entry> ENTRIES;
    static {
        try (var stream = ItemGuideCatalog.class.getResourceAsStream(
                "/assets/" + ModMain.MOD_ID + "/item_guides.json")) {
            if (stream == null) throw new IllegalStateException("Missing item guide inventory");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();
            for (var element : root.getAsJsonArray("items")) {
                var json = element.getAsJsonObject();
                var id = new ResourceLocation(json.get("id").getAsString());
                var controls = new java.util.ArrayList<String>();
                for (var key : json.getAsJsonArray("controls")) controls.add(key.getAsString());
                var entry = new Entry(id, json.get("module").getAsString(), json.get("category").getAsString(),
                        json.get("text_status").getAsString(), List.copyOf(controls));
                if (entries.put(id, entry) != null) throw new IllegalStateException("Duplicate item guide: " + id);
            }
            ENTRIES = java.util.Collections.unmodifiableMap(entries);
        } catch (java.io.IOException | RuntimeException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static Map<ResourceLocation, Entry> entries() { return ENTRIES; }
    public static Entry find(ItemStack stack) { return ENTRIES.get(ForgeRegistries.ITEMS.getKey(stack.getItem())); }
    private ItemGuideCatalog() { }
}
