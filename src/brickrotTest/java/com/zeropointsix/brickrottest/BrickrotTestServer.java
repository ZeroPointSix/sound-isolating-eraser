package com.zeropointsix.brickrottest;

import com.zeropointsix.eraser.brickrot.BrickrotContent;
import com.zeropointsix.eraser.brickrot.BrickrotPart;
import com.zeropointsix.eraser.brickrot.BrickrotWallEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = "brickrot_qa")
public final class BrickrotTestServer {
    private static final Path RESULTS = Path.of(System.getProperty("brickrot.qa.results"));
    private static String phase = "waiting";
    private static int ticks, hitPart = -1;
    private static boolean finished;
    private static BrickrotWallEntity head;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void phase(String next) {
        phase = next;
        ticks = 0;
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers())
            player.sendSystemMessage(Component.literal("BRICKROT_QA:" + next));
        System.out.println("BRICKROT_QA_PHASE=" + next);
    }

    private static void cameras(ServerPlayer user, ServerPlayer observer) {
        user.teleportTo(user.serverLevel(), 28.5, 77, 0.5, 90, 20);
        observer.teleportTo(observer.serverLevel(), -27.5, 77, 0.5, -90, 20);
    }

    private static boolean clientsPassed(String stage) {
        return Files.exists(RESULTS.resolve("user-" + stage + ".pass"))
                && Files.exists(RESULTS.resolve("observer-" + stage + ".pass"));
    }

    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide && event.getTarget() instanceof BrickrotPart part
                && part.getParent() == head) hitPart = part.index();
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isDedicatedServer()) return;
        var user = server.getPlayerList().getPlayerByName("BrickrotUser");
        var observer = server.getPlayerList().getPlayerByName("BrickrotObserver");
        if (user == null || observer == null) return;
        try {
            require(++ticks < 1600, "native phase timed out: " + phase);
            var level = server.overworld();
            if (phase.equals("waiting")) {
                for (ServerPlayer player : new ServerPlayer[]{user, observer}) {
                    player.setGameMode(GameType.CREATIVE);
                    player.setNoGravity(true);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                }
                if (Boolean.getBoolean("brickrot.qa.restart")) {
                    var heads = level.getEntities(BrickrotContent.WALL.get(), entity -> true);
                    require(heads.size() == 1, "only one persisted parent after restart");
                    head = heads.get(0);
                    require(head.phaseTwo() && head.getHealth() == 140 && head.getParts().length == 9,
                            "restart preserves phase, shared health and rebuilt body");
                    cameras(user, observer);
                    phase("restarted");
                    return;
                }
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                for (int x = -30; x <= 30; x++) for (int z = -22; z <= 22; z++)
                    level.setBlockAndUpdate(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState());
                user.getInventory().clearContent();
                user.getInventory().setItem(0, BrickrotContent.EGG.get().getDefaultInstance());
                user.getInventory().selected = 0;
                user.inventoryMenu.broadcastChanges();
                user.teleportTo(level, 0.5, 65, -3.5, 0, 65);
                observer.teleportTo(level, -27.5, 77, 0.5, -90, 20);
                phase("spawn");
            } else if (phase.equals("spawn")) {
                var heads = level.getEntities(BrickrotContent.WALL.get(), entity -> true);
                if (heads.isEmpty()) return;
                require(heads.size() == 1, "one native egg click creates one parent");
                head = heads.get(0);
                head.setNoAi(true);
                head.setNoGravity(true);
                head.setPersistenceRequired();
                head.moveTo(0.5, 65, 10.5, 0, 0);
                head.yBodyRot = 0;
                head.yBodyRotO = 0;
                cameras(user, observer);
                phase("intact");
            } else if (phase.equals("intact") && clientsPassed(phase)) {
                user.getInventory().setItem(0, Items.DIAMOND_SWORD.getDefaultInstance());
                user.inventoryMenu.broadcastChanges();
                user.teleportTo(level, 3.5, 65, head.getParts()[0].getZ(), 90, 0);
                phase("attack");
            } else if (phase.equals("attack") && head.getHealth() < 300) {
                require(hitPart == 1, "native attack reaches the neck multipart packet target");
                require(Math.abs(head.getHealth() - 293) < 0.01, "shared health loses seven damage exactly once");
                Files.writeString(RESULTS.resolve("native-interactions.pass"),
                        "Native right-click egg spawned one boss; left-click neck dealt 7 shared damage.\n");
                head.setHealth(140);
                cameras(user, observer);
                phase("breached");
            } else if (phase.equals("breached") && clientsPassed(phase)) {
                server.saveEverything(false, true, true);
                Files.writeString(RESULTS.resolve("server-cycle.pass"), "Native spawn, multipart attack and synchronized phase passed.\n");
                finished = true;
            } else if (phase.equals("restarted") && clientsPassed(phase)) {
                Files.writeString(RESULTS.resolve("server-restart.pass"), "Real server restart rebuilt nine parts and preserved health/phase.\n");
                finished = true;
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve("server.failed"), failure.toString()); } catch (Exception ignored) { }
            finished = true;
        }
    }
}
