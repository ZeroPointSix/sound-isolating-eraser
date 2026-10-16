package com.zeropointsix.eraser.gametest;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.zeropointsix.eraser.item.ItemGuideCatalog;
import com.zeropointsix.eraser.item.ItemGuideTooltips;
import com.zeropointsix.eraser.registry.ModItemTags;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("qijie_tests")
@PrefixGameTestTemplate(false)
public final class ItemCategoryGameTests {
    private static final Set<ResourceLocation> EXPECTED_QIWU = Set.of(
            id("super_fertilizer_bag"), id("gravity_jade_pendant"),
            id("shaxiadao"), id("sound_isolating_eraser"));

    private static final Set<ResourceLocation> EXPECTED_DAOJU = Set.of(
            new ResourceLocation("qihai", "flare_gun"), new ResourceLocation("qihai", "flare_red"),
            new ResourceLocation("qihai", "flare_yellow"), new ResourceLocation("qihai", "flare_green"));

    @GameTest(template = "empty")
    public static void defaultTagsContainOnlyTheRequestedItems(GameTestHelper helper) {
        Set<ResourceLocation> actual = new HashSet<>();
        ForgeRegistries.ITEMS.forEach(item -> {
            ItemStack stack = new ItemStack(item);
            if (stack.is(ModItemTags.QIWU)) {
                actual.add(ForgeRegistries.ITEMS.getKey(item));
            }
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            helper.assertTrue(stack.is(ModItemTags.DAOJU) == EXPECTED_DAOJU.contains(key),
                    "daoju is exactly the flare gun and its three flares: " + key);
        });
        helper.assertTrue(actual.equals(EXPECTED_QIWU), "exactly the four requested qiwu: " + actual);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void qiwuEventPreservesNameRarityNbtAndExistingLines(GameTestHelper helper) {
        for (ResourceLocation id : EXPECTED_QIWU) {
            ItemStack stack = ForgeRegistries.ITEMS.getValue(id).getDefaultInstance();
            ItemStack originalStack = stack.copy();
            var rarity = stack.getRarity();
            Component name = stack.getHoverName().copy().withStyle(rarity.color);
            Component description = Component.literal("Existing description").withStyle(ChatFormatting.RED);
            Component detail = Component.literal("Existing detail").withStyle(ChatFormatting.ITALIC);
            List<Component> lines = new ArrayList<>(List.of(name, description, detail));
            post(stack, lines);
            helper.assertTrue(lines.size() == 6 && lines.get(0) == name
                            && lines.get(4) == description && lines.get(5) == detail,
                    "name and existing lines retain identity, style and order: " + id);
            assertLabel(helper, lines.get(1), "qiwu", 0xFFAA00);
            helper.assertTrue(stack.getRarity() == rarity
                            && ItemStack.isSameItemSameTags(stack, originalStack),
                    "rarity, enchantments, custom data and name are unchanged: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullTooltipHasExactlyOneClassificationBelowName(GameTestHelper helper) {
        for (ResourceLocation id : EXPECTED_QIWU) {
            ItemStack stack = ForgeRegistries.ITEMS.getValue(id).getDefaultInstance();
            for (TooltipFlag flag : List.of(TooltipFlag.Default.NORMAL, TooltipFlag.Default.ADVANCED)) {
                List<Component> lines = stack.getTooltipLines(null, flag);
                assertLabel(helper, lines.get(1), "qiwu", 0xFFAA00);
                helper.assertTrue(lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text
                                && text.getKey().equals("tooltip.sound_isolating_eraser.qiwu")).count() == 1,
                        "one category line in a real item tooltip: " + id);
                helper.assertTrue(lines.get(0).getStyle().getColor().getValue() == stack.getRarity().color.getColor(),
                        "vanilla name color is unchanged: " + id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dataDrivenDaojuAndDualTagPriorityRestoreRegistryState(GameTestHelper helper) {
        var holder = Items.STICK.builtInRegistryHolder();
        var originalTags = holder.tags().toList();
        try {
            var tags = new HashSet<>(originalTags);
            tags.add(ModItemTags.DAOJU);
            holder.bindTags(tags);
            List<Component> lines = new ArrayList<>(List.of(Component.literal("Stick")));
            post(new ItemStack(Items.STICK), lines);
            helper.assertTrue(lines.size() == 2, "a data-tagged vanilla item receives daoju");
            assertLabel(helper, lines.get(1), "daoju", 0x8FAAC4);

            tags.add(ModItemTags.QIWU);
            holder.bindTags(tags);
            lines = new ArrayList<>(List.of(Component.literal("Stick")));
            post(new ItemStack(Items.STICK), lines);
            helper.assertTrue(lines.size() == 2, "dual-tagged item receives only one classification");
            assertLabel(helper, lines.get(1), "qiwu", 0xFFAA00);
        } finally {
            holder.bindTags(originalTags);
        }
        helper.assertTrue(holder.tags().collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(originalTags)),
                "temporary test membership is fully restored");
        List<Component> restored = new ArrayList<>(List.of(Component.literal("Stick")));
        post(new ItemStack(Items.STICK), restored);
        helper.assertTrue(restored.size() == 1, "subsequent tooltip sees no stale classification");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unclassifiedAndEmptyTooltipsRemainUntouched(GameTestHelper helper) {
        List<Component> lines = new ArrayList<>(List.of(Component.literal("Stone"), Component.literal("Detail")));
        List<Component> original = List.copyOf(lines);
        post(new ItemStack(Items.STONE), lines);
        helper.assertTrue(lines.equals(original), "untagged items keep all lines");
        post(ItemStack.EMPTY, lines);
        helper.assertTrue(lines.equals(original), "empty stack leaves tooltip unchanged");
        List<Component> empty = new ArrayList<>();
        post(new ItemStack(ForgeRegistries.ITEMS.getValue(id("sound_isolating_eraser"))), empty);
        helper.assertTrue(empty.isEmpty(), "missing name line is safe during early tooltip generation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothLanguageFilesContainClassificationText(GameTestHelper helper) throws Exception {
        for (String language : List.of("en_us", "zh_cn")) {
            String path = "/assets/sound_isolating_eraser/lang/" + language + ".json";
            try (var stream = ItemCategoryGameTests.class.getResourceAsStream(path)) {
                helper.assertTrue(stream != null, "language resource exists: " + language);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                helper.assertTrue(json.get("tooltip.sound_isolating_eraser.qiwu").getAsString()
                                .equals(language.equals("en_us") ? "Curio" : "\u5947\u7269"), "qiwu translation: " + language);
                helper.assertTrue(json.get("tooltip.sound_isolating_eraser.daoju").getAsString()
                                .equals(language.equals("en_us") ? "Tool" : "\u9053\u5177"), "daoju translation: " + language);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guideInventoryMatchesEveryOwnedRegisteredItem(GameTestHelper helper) {
        Set<ResourceLocation> registered = new HashSet<>();
        ForgeRegistries.ITEMS.forEach(item -> {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (!Set.of("sound_isolating_eraser", "qihai").contains(id.getNamespace())) return;
            registered.add(id);
            var entry = ItemGuideCatalog.entries().get(id);
            helper.assertTrue(entry != null, "registered item has a guide: " + id);
            ItemStack stack = item.getDefaultInstance();
            String category = stack.is(ModItemTags.PRECIOUS_MEMORY) ? "precious_memory"
                    : stack.is(ModItemTags.QIWU) || stack.is(ModItemTags.ODDITIES) ? "qiwu"
                    : stack.is(ModItemTags.DAOJU) || stack.is(ModItemTags.PROPS) ? "daoju"
                    : item instanceof net.minecraft.world.item.SpawnEggItem ? "spawn_egg"
                    : item instanceof net.minecraft.world.item.BlockItem ? "block" : "material";
            helper.assertTrue(entry.category().equals(category), "tag/registry category matches: " + id);
            helper.assertTrue(!entry.module().isBlank()
                            && Set.of("partial", "placeholder", "complete").contains(entry.textStatus()),
                    "module and documentation status are explicit: " + id);
        });
        helper.assertTrue(!registered.isEmpty() && registered.equals(ItemGuideCatalog.entries().keySet()),
                "guide inventory equals the complete owned registry; no omissions or stale IDs");
        System.out.println("ITEM_GUIDE_REGISTRY_COVERAGE " + registered.size());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyGuideSectionExistsInBothLanguages(GameTestHelper helper) throws Exception {
        for (String language : List.of("en_us", "zh_cn")) {
            JsonObject translations = new JsonObject();
            for (String namespace : List.of("sound_isolating_eraser", "qihai")) {
                String path = "/assets/" + namespace + "/lang/" + language + ".json";
                try (var stream = ItemCategoryGameTests.class.getResourceAsStream(path)) {
                    helper.assertTrue(stream != null, "language resource exists: " + path);
                    var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                    json.entrySet().forEach(entry -> translations.add(entry.getKey(), entry.getValue()));
                }
            }
            for (var entry : ItemGuideCatalog.entries().values()) {
                for (String section : ItemGuideCatalog.SECTIONS) {
                    String key = entry.key(section);
                    helper.assertTrue(translations.has(key) && translations.get(key).isJsonPrimitive()
                                    && translations.get(key).getAsJsonPrimitive().isString()
                                    && !translations.get(key).getAsString().isBlank(),
                            "nonempty guide translation: " + language + " / " + key);
                    ItemStack stack = ForgeRegistries.ITEMS.getValue(entry.id()).getDefaultInstance();
                    int supplied = section.equals("controls") ? entry.controls().size()
                            : ItemGuideTooltips.arguments(entry, section, stack).length;
                    String value = translations.get(key).getAsString();
                    long placeholders = java.util.regex.Pattern.compile("%s").matcher(value).results().count();
                    helper.assertTrue(placeholders == supplied, "format arguments match: " + language + " / " + key);
                }
            }
            for (String hint : List.of("hold_both", "hold_shift", "hold_control")) {
                helper.assertTrue(translations.has("guide.sound_isolating_eraser." + hint), "modifier hint: " + hint);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modifierMatrixIsCompleteAndDoesNotMutateStacks(GameTestHelper helper) {
        for (var entry : ItemGuideCatalog.entries().values()) {
            ItemStack stack = ForgeRegistries.ITEMS.getValue(entry.id()).getDefaultInstance();
            stack.getOrCreateTag().putString("GuideSentinel", "unchanged");
            ItemStack original = stack.copy();
            for (int mode = 0; mode < 4; mode++) {
                boolean shift = (mode & 1) != 0;
                boolean control = (mode & 2) != 0;
                List<Component> lines = ItemGuideTooltips.describe(stack, shift, control,
                        name -> Component.literal("remapped:" + name));
                Set<String> keys = new HashSet<>();
                for (Component line : lines) {
                    helper.assertTrue(line.getContents() instanceof TranslatableContents, "all sections are translatable");
                    var text = (TranslatableContents) line.getContents();
                    helper.assertTrue(keys.add(text.getKey()), "no duplicate guide section: " + entry.id());
                    if (text.getKey().equals(entry.key("controls"))) {
                        helper.assertTrue(text.getArgs().length == entry.controls().size(), "all key bindings supplied");
                        for (int i = 0; i < text.getArgs().length; i++) {
                            helper.assertTrue(text.getArgs()[i] instanceof Component key
                                            && key.getString().equals("remapped:" + entry.controls().get(i)),
                                    "binding comes from resolver, not a hard-coded key");
                        }
                    }
                }
                helper.assertTrue(keys.contains(entry.key("summary")), "summary always visible");
                for (String section : List.of("usage", "controls", "cooldown", "cost")) {
                    helper.assertTrue(keys.contains(entry.key(section)) == shift, "Shift gate: " + section);
                }
                helper.assertTrue(keys.contains(entry.key("lore")) == control, "Ctrl gate");
                helper.assertTrue(lines.size() == 1 + (shift ? 4 : 0) + (control ? 1 : 0)
                                + (shift && control ? 0 : 1), "exact layer count");
            }
            helper.assertTrue(ItemStack.isSameItemSameTags(stack, original), "rendering never changes item NBT");
        }
        helper.assertTrue(ItemGuideTooltips.describe(new ItemStack(Items.STONE), true, true,
                Component::translatable).isEmpty(), "foreign items have no guide");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void migratedDynamicDetailsSurviveRealTooltipAssembly(GameTestHelper helper) {
        ItemStack pills = ForgeRegistries.ITEMS.getValue(id("enhancement_pill_pack")).getDefaultInstance();
        pills.setDamageValue(2);
        ItemStack watch = ForgeRegistries.ITEMS.getValue(id("suxia_pocket_watch")).getDefaultInstance();
        watch.getOrCreateTag().putUUID("Owner", java.util.UUID.randomUUID());
        watch.getOrCreateTag().putString("OwnerName", "GuideTest");
        watch.getOrCreateTag().putBoolean("Open", true);
        watch.getOrCreateTag().putInt("Remaining", 400);
        ItemStack coolingWatch = watch.copy();
        coolingWatch.getOrCreateTag().putBoolean("Open", false);
        coolingWatch.getOrCreateTag().putInt("CooldownRemaining", 1200);
        ItemStack nails = ForgeRegistries.ITEMS.getValue(id("red_nails")).getDefaultInstance();
        nails.getOrCreateTag().putUUID("Owner", java.util.UUID.randomUUID());
        nails.getOrCreateTag().putString("OwnerName", "GuideTest");
        nails.getOrCreateTag().putIntArray("NailLeft", new int[]{1200, 0, 0, 0});
        var summoned = new net.minecraft.nbt.ListTag();
        summoned.add(new net.minecraft.nbt.CompoundTag());
        var activeNail = new net.minecraft.nbt.CompoundTag();
        activeNail.putUUID("Ghost", java.util.UUID.randomUUID());
        summoned.add(activeNail);
        nails.getOrCreateTag().put("NailOut", summoned);
        String prefix = "tooltip.sound_isolating_eraser.";
        List<ItemStack> stacks = List.of(pills, watch, coolingWatch, nails);
        List<Set<String>> expected = List.of(Set.of(prefix + "pill_count"),
                Set.of(prefix + "suxia_pocket_watch.bound", prefix + "suxia_pocket_watch.active"),
                Set.of(prefix + "suxia_pocket_watch.bound", prefix + "suxia_pocket_watch.cooling"),
                Set.of(prefix + "red_nails.bound", prefix + "red_nails.nails", prefix + "red_nails.nail_cooling",
                        prefix + "red_nails.nail_out", prefix + "red_nails.nail_ready"));
        for (int mode = 0; mode < 4; mode++) {
            boolean shift = (mode & 1) != 0;
            boolean control = (mode & 2) != 0;
            Runnable restore = ItemGuideTooltips.configureClient(
                    () -> new ItemGuideTooltips.Modifiers(shift, control), Component::translatable);
            try {
                for (int i = 0; i < stacks.size(); i++) {
                    ItemStack stack = stacks.get(i);
                    ItemStack original = stack.copy();
                    List<Component> lines = stack.getTooltipLines(null, TooltipFlag.Default.NORMAL);
                    for (String key : expected.get(i)) {
                        helper.assertTrue(lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents text
                                        && text.getKey().equals(key)) == shift, "live details use Shift: " + key);
                    }
                    if (shift) {
                        for (Component line : lines) {
                            if (!(line.getContents() instanceof TranslatableContents text)) continue;
                            if (text.getKey().equals(prefix + "pill_count")) {
                                helper.assertTrue(text.getArgs()[0].equals(pills.getMaxDamage() - 2), "current pill count");
                            } else if (text.getKey().equals(prefix + "suxia_pocket_watch.active")) {
                                helper.assertTrue(text.getArgs()[0].equals(20), "current summon seconds");
                            } else if (text.getKey().equals(prefix + "suxia_pocket_watch.cooling")) {
                                helper.assertTrue(text.getArgs()[0].equals("1:00"), "current watch cooldown");
                            } else if (text.getKey().equals(prefix + "red_nails.nail_cooling")) {
                                helper.assertTrue(text.getArgs()[1].equals("1:00"), "current individual nail cooldown");
                            }
                        }
                    }
                    helper.assertTrue(ItemStack.isSameItemSameTags(stack, original), "live details never change item data");
                }
            } finally {
                restore.run();
            }
        }
        helper.succeed();
    }

    private static void post(ItemStack stack, List<Component> lines) {
        MinecraftForge.EVENT_BUS.post(new ItemTooltipEvent(stack, null, lines, TooltipFlag.Default.NORMAL));
    }

    private static void assertLabel(GameTestHelper helper, Component line, String category, int rgb) {
        helper.assertTrue(line.getContents() instanceof TranslatableContents text
                        && text.getKey().equals("tooltip.sound_isolating_eraser." + category),
                "classification uses the expected translation key: " + category);
        helper.assertTrue(line.getStyle().getColor() != null && line.getStyle().getColor().getValue() == rgb,
                "classification uses the specified color: " + category);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("sound_isolating_eraser", path);
    }

    private ItemCategoryGameTests() { }
}
