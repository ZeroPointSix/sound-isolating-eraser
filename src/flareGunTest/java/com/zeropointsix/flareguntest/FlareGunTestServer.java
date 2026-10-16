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
    private static ServerPlayer actor, observer;
    private static int ticks, shots;
    private static boolean actorReady, observerReady, finished;

    @SubscribeEvent public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String name = player.getGameProfile().getName();
        if (!name.equals("FlareTester") && !name.equals("FlareObserver")) return;
        boolean watching = name.equals("FlareObserver");
        if (watching) observer = player; else actor = player;
        var level = player.serverLevel();
        FlareConfig.ALL_DIMENSIONS.set(true);
        FlareConfig.CREATIVE_FREE.set(false);
        FlareConfig.COOLDOWN.set(60);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
        level.setDayTime(6000);
        level.setWeatherParameters(12000, 0, false, false);
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++)
            level.setBlock(new BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, .5, 80, watching ? 4 : .5, watching ? 180 : 0, watching ? 5 : -5);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        if (!watching) player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlareContent.GUN.get()));
        player.setInvulnerable(true);
        player.inventoryMenu.broadcastChanges();
        player.sendSystemMessage(Component.literal("FLARE_GUN_QA:ready"));
    }

    @SubscribeEvent public static void chat(ServerChatEvent event) {
        if ((event.getPlayer() == actor || event.getPlayer() == observer) && event.getRawText().equals("flare_gun_qa_ready")) {
            event.setCanceled(true);
            if (event.getPlayer() == actor) actorReady = true; else observerReady = true;
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
        observer.sendSystemMessage(Component.literal("FLARE_GUN_QA:" + name));
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !actorReady || !observerReady || finished
                || actor == null || observer == null || !Files.exists(RESULTS.resolve("recording.ready"))) return;
        try {
            ticks++;
            int cycle = ticks < 440 ? ticks : ticks - 420;
            String hand = ticks < 440 ? "right" : "left";
            int previousShots = ticks < 440 ? 0 : 2;
            if (cycle == 20) phase(hand + "-first-load", true);
            if (cycle == 100) phase(hand + "-first-fire", false);
            if (cycle == 140) {
                if (shots != previousShots + 1 || !actor.getOffhandItem().isEmpty()
                        || !actor.getCooldowns().isOnCooldown(FlareContent.GUN.get()))
                    throw new AssertionError("First shot/consumption/cooldown: " + shots);
                phase(hand + "-cooldown-refill", true);
            }
            if (cycle == 240) {
                if (shots != previousShots + 1 || actor.getOffhandItem().getCount() != 1)
                    throw new AssertionError("Cooldown shot consumed cartridge or spawned projectile");
                phase(hand + "-third-fire", false);
            }
            if (cycle == 340) phase(hand + "-empty", false);
            if (ticks == 860) {
                if (shots != 4 || !actor.getOffhandItem().isEmpty()) throw new AssertionError("Duplicate/empty shot: " + shots);
                Files.writeString(RESULTS.resolve("server.pass"), "Dedicated server: 4 actual player shots, 4 cartridges consumed; two cooldown-refill and two empty attempts rejected; two clients synchronized.\n");
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
