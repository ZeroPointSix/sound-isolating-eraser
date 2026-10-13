package com.zeropointsix.gravitytest;

import com.zeropointsix.eraser.client.GravityFieldIndex;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import com.zeropointsix.eraser.registry.ModCreativeTabs;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.registries.ForgeRegistries;

/** Development-only probe; runs in both real clients before the existing gameplay suite. */
final class QijieInventoryProbe {
    private static int stage;
    private static long readyFrame;
    private static CreativeModeInventoryScreen screen;

    static boolean tick(Minecraft mc, long frames, Path results, String role) throws Exception {
        if (stage == 0) {
            verifyEntityReplacement(mc);
            screen = new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true);
            mc.setScreen(screen);
            select(ModCreativeTabs.QIJIE.get());
            Set<ResourceLocation> expected = new HashSet<>();
            ForgeRegistries.ITEMS.getKeys().stream()
                    .filter(id -> Set.of("sound_isolating_eraser", "qihai").contains(id.getNamespace()))
                    .forEach(expected::add);
            Set<ResourceLocation> actual = new HashSet<>();
            screen.getMenu().items.forEach(stack -> actual.add(ForgeRegistries.ITEMS.getKey(stack.getItem())));
            require(actual.equals(expected) && screen.getMenu().items.size() == actual.size(),
                    "creative inventory contains every mod item exactly once");
            require(!ModCreativeTabs.QIJIE.get().getDisplayName().getString().startsWith("itemGroup."),
                    "creative tab title is translated");
            readyFrame = frames + 3;
            stage = 1;
            return false;
        }
        if (stage == 1 && frames >= readyFrame) {
            capture(mc, results.resolve(role + "-qijie-tab.png"));
            select(BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.SEARCH.location()));
            var field = CreativeModeInventoryScreen.class.getDeclaredField("searchBox");
            field.setAccessible(true);
            ((EditBox) field.get(screen)).setValue(ModItems.THUNDER_FEATHER.get().getDescription().getString());
            Method refresh = CreativeModeInventoryScreen.class.getDeclaredMethod("refreshSearchResults");
            refresh.setAccessible(true);
            refresh.invoke(screen);
            require(screen.getMenu().items.stream().anyMatch(stack -> stack.is(ModItems.THUNDER_FEATHER.get())),
                    "global search finds the crafting feather");
            readyFrame = frames + 3;
            stage = 2;
            return false;
        }
        if (stage == 2 && frames >= readyFrame) {
            capture(mc, results.resolve(role + "-qijie-search.png"));
            mc.setScreen(null);
            screen = null;
            Files.writeString(results.resolve(role + "-inventory.pass"), "all items, search and same-ID entity replacement passed\n");
            stage = 3;
        }
        return stage == 3;
    }

    private static void verifyEntityReplacement(Minecraft mc) throws Exception {
        int id = Integer.MAX_VALUE - 1729;
        require(mc.level.getEntity(id) == null, "isolated probe entity ID is free");
        GravityFieldEntity first = ModEntities.GRAVITY_FIELD.get().create(mc.level);
        GravityFieldEntity replacement = ModEntities.GRAVITY_FIELD.get().create(mc.level);
        first.setId(id);
        replacement.setId(id);
        Method add = ClientLevel.class.getDeclaredMethod("addEntity", int.class, Entity.class);
        add.setAccessible(true);
        try {
            add.invoke(mc.level, id, first);
            add.invoke(mc.level, id, replacement);
            require(mc.level.getEntity(id) == replacement && GravityFieldIndex.fields().contains(replacement)
                    && !GravityFieldIndex.fields().contains(first), "same-ID field replacement remains indexed");
        } finally {
            mc.level.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }
        require(!GravityFieldIndex.fields().contains(first) && !GravityFieldIndex.fields().contains(replacement),
                "field removal releases both references");
    }

    private static void select(CreativeModeTab tab) throws Exception {
        Method select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
        select.setAccessible(true);
        select.invoke(screen, tab);
    }

    private static void capture(Minecraft mc, Path path) throws Exception {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("QIJIE_INVENTORY_ASSERT " + message);
    }

    private QijieInventoryProbe() { }
}
