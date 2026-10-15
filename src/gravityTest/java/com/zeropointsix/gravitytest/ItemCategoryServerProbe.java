package com.zeropointsix.gravitytest;

import com.zeropointsix.eraser.registry.ModItemTags;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Test-mod only: reloads a real on-disk data pack and waits for both remote clients. */
@Mod.EventBusSubscriber(modid = "gravity_qa")
public final class ItemCategoryServerProbe {
    private static final String[] PHASES = {"baseline", "daoju", "both", "restored"};
    private static int phase;
    private static volatile boolean reloading;
    private static volatile boolean tagsReloaded;
    private static boolean supplied;
    private static boolean finished;

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("qiwu.qa") || finished || event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isDedicatedServer()) return;
        Path results = Path.of(System.getProperty("gravity.qa.results"));
        try {
            if (!supplied) {
                for (String role : new String[]{"wearer", "observer"}) {
                    if (!Files.exists(results.resolve(role + "-inventory.pass"))) return;
                }
                for (String name : new String[]{GravityTestServer.WEARER, GravityTestServer.OBSERVER}) {
                    var player = server.getPlayerList().getPlayerByName(name);
                    if (player == null) return;
                    player.setGameMode(GameType.SURVIVAL);
                    String[] ids = {"super_fertilizer_bag", "gravity_jade_pendant", "shaxiadao", "sound_isolating_eraser"};
                    for (int i = 0; i < ids.length; i++) {
                        player.getInventory().setItem(i, ForgeRegistries.ITEMS.getValue(
                                new ResourceLocation("sound_isolating_eraser", ids[i])).getDefaultInstance());
                    }
                    player.getInventory().setItem(4, Items.STICK.getDefaultInstance());
                    player.inventoryMenu.broadcastChanges();
                }
                Files.writeString(results.resolve("server-category-baseline.pass"), "server inventory fixture supplied\n");
                supplied = true;
            }
            if (reloading) {
                if (!tagsReloaded) return;
                reloading = false;
                var stick = Items.STICK.getDefaultInstance();
                if (stick.is(ModItemTags.QIWU) != (phase == 2)
                        || stick.is(ModItemTags.DAOJU) != (phase == 1 || phase == 2)) {
                    throw new AssertionError("Server tags did not reload in phase " + PHASES[phase]);
                }
                Files.writeString(results.resolve("server-category-" + PHASES[phase] + ".pass"),
                        "real server /reload completed: " + PHASES[phase] + "\n");
                System.out.println("ITEM_CATEGORY_SERVER_RELOAD " + PHASES[phase]);
            }
            for (String role : new String[]{"wearer", "observer"}) {
                if (!Files.exists(results.resolve(role + "-category-" + PHASES[phase] + ".pass"))) return;
            }
            if (phase == 3) {
                for (String name : new String[]{GravityTestServer.WEARER, GravityTestServer.OBSERVER}) {
                    var player = server.getPlayerList().getPlayerByName(name);
                    if (player == null) throw new AssertionError("Client disconnected before fixture cleanup");
                    for (int i = 0; i < 5; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
                    player.setGameMode(GameType.CREATIVE);
                    player.inventoryMenu.broadcastChanges();
                }
                Files.writeString(results.resolve("server-category.pass"), "three real data pack reloads passed\n");
                finished = true;
                return;
            }
            phase++;
            Path tags = server.getWorldPath(LevelResource.DATAPACK_DIR)
                    .resolve("qiwu_category_qa/data/sound_isolating_eraser/tags/items");
            writeTag(tags, "qiwu", phase == 2);
            writeTag(tags, "daoju", phase == 1 || phase == 2);
            server.getPackRepository().reload();
            var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
            if (!selected.contains("file/qiwu_category_qa")) selected.add("file/qiwu_category_qa");
            server.getPackRepository().setSelected(selected);
            tagsReloaded = false;
            reloading = true;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(results.resolve("server-category.failed"), failure.toString()); }
            catch (Exception ignored) { }
            finished = true;
        }
    }

    @SubscribeEvent
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (reloading && event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            tagsReloaded = true;
        }
    }

    private static void writeTag(Path directory, String name, boolean includeStick) throws Exception {
        Files.writeString(directory.resolve(name + ".json"),
                "{\"replace\":false,\"values\":" + (includeStick ? "[\"minecraft:stick\"]" : "[]") + "}");
    }

    private ItemCategoryServerProbe() { }
}
