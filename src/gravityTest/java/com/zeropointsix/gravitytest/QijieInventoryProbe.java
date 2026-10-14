package com.zeropointsix.gravitytest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zeropointsix.eraser.client.GravityFieldIndex;
import com.zeropointsix.eraser.client.WingsItemRenderer;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import com.zeropointsix.eraser.registry.ModCreativeTabs;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.gui.CreativeTabsScreenPage;
import net.minecraftforge.registries.ForgeRegistries;

/** Development-only probe; runs in both real clients before the existing gameplay suite. */
final class QijieInventoryProbe {
    private static int stage;
    private static long readyFrame;
    private static CreativeModeInventoryScreen screen;

    static boolean tick(Minecraft mc, long frames, Path results, String role) throws Exception {
        if (stage == 0) {
            verifyEntityReplacement(mc);
            verifyWingSlotBounds();
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
            Files.writeString(results.resolve(role + "-inventory.pass"),
                    "all items, search, wing slot bounds and same-ID entity replacement passed\n");
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
        var pages = CreativeModeInventoryScreen.class.getDeclaredField("pages");
        pages.setAccessible(true);
        for (Object entry : (List<?>) pages.get(screen)) {
            var page = (CreativeTabsScreenPage) entry;
            if (page.getVisibleTabs().contains(tab)) {
                screen.setCurrentPage(page);
                break;
            }
        }
        require(screen.getCurrentPage().getVisibleTabs().contains(tab), "selected tab is on the visible page");
        Method select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
        select.setAccessible(true);
        select.invoke(screen, tab);
    }

    private static void verifyWingSlotBounds() {
        var bounds = new SlotBounds();
        new WingsItemRenderer().renderByItem(ModItems.WIND_THUNDER_WINGS.get().getDefaultInstance(),
                ItemDisplayContext.GUI, new PoseStack(), type -> bounds,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        require(bounds.vertices > 0 && bounds.minX >= 0 && bounds.maxX <= 1
                && bounds.minY >= 0 && bounds.maxY <= 1 && bounds.maxZ - bounds.minZ > 0.01,
                "three-dimensional wing mesh stays inside one inventory slot");
    }

    private static final class SlotBounds implements VertexConsumer {
        private int vertices;
        private double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        @Override public VertexConsumer vertex(double x, double y, double z) {
            vertices++;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }
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
