package com.zeropointsix.eraser.gametest;

import com.google.gson.JsonParser;
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
            helper.assertTrue(lines.size() == 4 && lines.get(0) == name
                            && lines.get(2) == description && lines.get(3) == detail,
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
