package com.zeropointsix.flareguntest;

import com.zeropointsix.eraser.yuji.flare.FlareConfig;
import com.zeropointsix.eraser.yuji.flare.FlareContent;
import com.zeropointsix.eraser.yuji.flare.FlareProjectile;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "flare_gun_qa")
public final class FlareGunTestServer {
    private static final Path RESULTS = Path.of(System.getProperty("flare.gun.qa.results"));
    private static ServerPlayer actor;
    private static int ticks, shots;
    private static boolean ready, finished;

    @SubscribeEvent public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getGameProfile().getName().equals("FlareTester")) return;
        actor = player;
        var level = player.serverLevel();
        FlareConfig.ALL_DIMENSIONS.set(true);
        FlareConfig.CREATIVE_FREE.set(false);
        FlareConfig.COOLDOWN.set(20);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
        level.setDayTime(6000);
        level.setWeatherParameters(12000, 0, false, false);
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++)
            level.setBlock(new BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, .5, 80, .5, 0, -5);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlareContent.GUN.get()));
        player.inventoryMenu.broadcastChanges();
        player.sendSystemMessage(Component.literal("FLARE_GUN_QA:ready"));
    }

    @SubscribeEvent public static void chat(ServerChatEvent event) {
        if (event.getPlayer() == actor && event.getRawText().equals("flare_gun_qa_ready")) {
            event.setCanceled(true);
            ready = true;
        }
    }

    @SubscribeEvent public static void projectile(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof FlareProjectile flare && flare.getOwner() == actor)
            shots++;
    }

    private static void phase(String name, boolean load) {
        if (load) actor.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FlareContent.RED.get()));
        actor.inventoryMenu.broadcastChanges();
        actor.sendSystemMessage(Component.literal("FLARE_GUN_QA:" + name));
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ready || finished || actor == null) return;
        try {
            ticks++;
            if (ticks == 20) phase("first-load", true);
            if (ticks == 100) phase("first-fire", false);
            if (ticks == 180) {
                if (shots != 1 || !actor.getOffhandItem().isEmpty()) throw new AssertionError("First shot/cooldown/consumption: " + shots);
                phase("third-load", true);
            }
            if (ticks == 260) phase("third-fire", false);
            if (ticks == 340) phase("empty", false);
            if (ticks == 420) {
                if (shots != 2 || !actor.getOffhandItem().isEmpty()) throw new AssertionError("Duplicate/empty shot: " + shots);
                Files.writeString(RESULTS.resolve("server.pass"), "Dedicated server: 2 actual player shots, 2 cartridges consumed; cooldown/empty attempts rejected.\n");
                phase("done", false);
                finished = true;
            }
        } catch (Throwable failure) {
            finished = true;
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve("server.failed"), failure.toString()); } catch (Exception ignored) { }
        }
    }
}
