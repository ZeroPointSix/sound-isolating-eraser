package com.zeropointsix.keepertest;

import com.zeropointsix.eraser.keeper.KeeperData;
import com.zeropointsix.eraser.registry.ModEffects;
import com.zeropointsix.eraser.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = "keeper_qa")
public final class KeeperTestServer {
    private static final Path RESULTS = Path.of(System.getProperty("keeper.qa.results"));
    private static String phase = "waiting";
    private static int phaseTicks;
    private static boolean finished;

    private static void require(boolean value, String text) {
        if (!value) throw new AssertionError(text);
    }

    private static void phase(String next) {
        phase = next;
        phaseTicks = 0;
        var server = ServerLifecycleHooks.getCurrentServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal("KEEPER_QA:" + next));
        }
        System.out.println("KEEPER_QA_PHASE=" + next + flushLine(server));
    }

    private static String flushLine(net.minecraft.server.MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayerByName("KeeperUser");
        if (player == null) return "";
        boolean photo = hasPhoto(player);
        boolean inPhoto = player.hasEffect(ModEffects.IN_PHOTO.get());
        return " photo=" + photo + " inPhoto=" + inPhoto + " selected=" + player.getInventory().selected;
    }

    private static boolean hasPhoto(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.KEEPER_PHOTO.get()) && !KeeperData.blank(stack)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isDedicatedServer()) return;
        ServerPlayer player = server.getPlayerList().getPlayerByName("KeeperUser");
        if (player == null) return;
        try {
            phaseTicks++;
            require(phaseTicks < 2400, "phase timed out: " + phase);
            require(player.isAlive(), "fixture player remains alive");
            if (phase.equals("waiting")) {
                if (phaseTicks < 40) return;
                var level = server.overworld();
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, server);
                int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
                if (ground <= level.getMinBuildHeight() + 1) ground = Math.max(player.blockPosition().getY(), level.getMinBuildHeight() + 4);
                for (int x = -6; x <= 6; x++) {
                    for (int z = -6; z <= 6; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, ground, z), Blocks.STONE.defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(new BlockPos(0, ground + 1, 3), Blocks.RED_WOOL.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(1, ground + 1, 3), Blocks.GOLD_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(-1, ground + 1, 3), Blocks.LAPIS_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(0, ground + 2, 3), Blocks.OAK_LEAVES.defaultBlockState());
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.teleportTo(level, 0.5, ground + 1, 0.5, 0, 18);
                player.setHealth(20);
                player.getFoodData().setFoodLevel(20);
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                System.out.println("KEEPER_QA_GROUND=" + ground);
                player.getInventory().setItem(0, new ItemStack(ModItems.KEEPER_CAMERA.get()));
                player.getInventory().selected = 0;
                player.inventoryMenu.broadcastChanges();
                phase("baseline");
                return;
            }
            if (phase.equals("baseline") && phaseTicks >= 80) {
                phase("shoot");
            } else if (phase.equals("shoot") && hasPhoto(player) && phaseTicks >= 50) {
                if (!Files.exists(RESULTS.resolve("flash.pass")) && phaseTicks < 600) return;
                player.getInventory().selected = 1;
                player.inventoryMenu.broadcastChanges();
                phase("enter");
            } else if (phase.equals("enter") && player.hasEffect(ModEffects.IN_PHOTO.get()) && phaseTicks >= 20) {
                phase("photo");
            } else if (phase.equals("photo") && phaseTicks >= 30) {
                if (!(Files.exists(RESULTS.resolve("sepia.pass")) && Files.exists(RESULTS.resolve("hud.pass"))) && phaseTicks < 200) return;
                require(player.hasEffect(ModEffects.IN_PHOTO.get()), "photo session still active for HUD/filter");
                Files.writeString(RESULTS.resolve("server.pass"), "Dedicated server handed a real camera, captured a photo, and entered the photo session." + System.lineSeparator());
                finished = true;
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve("server.failed"), failure.toString()); } catch (Exception ignored) { }
            finished = true;
        }
    }
}
