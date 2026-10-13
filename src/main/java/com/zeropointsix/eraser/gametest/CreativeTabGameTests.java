package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModCreativeTabs;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeTabGameTests {
    @GameTest(template = "empty")
    public static void creativeTabContainsAllItemsAndDefaultData(GameTestHelper helper) {
        var tab = ModCreativeTabs.QIJIE.get();
        var parameters = new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess());
        Set<ResourceLocation> expected = new HashSet<>();
        ForgeRegistries.ITEMS.getKeys().stream().filter(ModCreativeTabs::owns).forEach(expected::add);
        helper.assertTrue(!expected.isEmpty(), "mod items are registered before building the tab");
        for (int pass = 0; pass < 2; pass++) {
            tab.buildContents(parameters);
            Set<ResourceLocation> actual = new HashSet<>();
            for (ItemStack stack : tab.getDisplayItems()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                helper.assertTrue(stack.getCount() == 1 && actual.add(id), "single, unique entry: " + id);
                helper.assertTrue(ItemStack.isSameItemSameTags(stack, stack.getItem().getDefaultInstance()),
                        "default item data preserved: " + id);
                helper.assertTrue(tab.getSearchTabDisplayItems().stream().anyMatch(search ->
                        ItemStack.isSameItemSameTags(stack, search)), "searchable entry: " + id);
            }
            helper.assertTrue(actual.equals(expected), "all registered items in both namespaces: " + actual.size());
            helper.assertTrue(tab.getDisplayItems().stream().anyMatch(stack ->
                    stack.is(ModItems.SHAXIADAO.get()) && stack.isEnchanted()), "knife keeps innate enchantment");
        }
        helper.succeed();
    }

    private CreativeTabGameTests() { }
}
