package com.zeropointsix.gravitytest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.zeropointsix.eraser.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/** Server authority driver for the wind-thunder wings E2E. Inert unless -Dwings.qa.role=server. */
@Mod.EventBusSubscriber(modid = "gravity_qa")
public final class WingsQaServer {
    public static final String WEARER = "WingsWearer";
    public static final String OBSERVER = "WingsObserver";
    private static final String PREFIX = "WINGS_QA_PHASE:";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RUN_TICKS = 12000;
    private static boolean ready;
    private static int readyAt;
    private static int startedAt = -1;
    private static int stopAt = -1;
    private static boolean hungerChecked;
    private static ServerPlayer wearerRef;

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wingsqa")
                .requires(WingsQaServer::allowed)
                .then(Commands.literal("phase")
                        .then(Commands.argument("word", StringArgumentType.word())
                                .executes(context -> changePhase(context.getSource(),
                                        StringArgumentType.getString(context, "word")))))
                .then(Commands.literal("finish")
                        .executes(context -> finish(context.getSource().getServer()))));
    }

    private static boolean allowed(CommandSourceStack source) {
        if (!source.getServer().isDedicatedServer()) return false;
        if (source.hasPermission(2)) return true;
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        String name = player.getGameProfile().getName();
        return WEARER.equals(name) || OBSERVER.equals(name);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!"server".equals(System.getProperty("wings.qa.role"))) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isDedicatedServer()) return;
        if (startedAt < 0) {
            startedAt = server.getTickCount();
            LOGGER.info("WINGS_QA_SERVER_WAITING");
        }
        if (stopAt >= 0 && server.getTickCount() >= stopAt) {
            server.halt(false);
            return;
        }
        if (server.getTickCount() - startedAt >= MAX_RUN_TICKS) {
            fail(server, "WINGS_QA_SERVER_TIMEOUT");
            return;
        }
        if (!ready) {
            ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
            ServerPlayer observer = server.getPlayerList().getPlayerByName(OBSERVER);
            if (wearer == null || observer == null) return;
            initialize(server, wearer, observer);
            ready = true;
            readyAt = server.getTickCount();
            announce(server, "ready");
            return;
        }
        try {
            if (!hungerChecked && server.getTickCount() - readyAt >= 235) {
                hungerChecked = true;
                int food = wearerRef.getFoodData().getFoodLevel();
                check(food < 20, "deployed wings drain real hunger server-side (food=" + food + ")");
                Files.writeString(results().resolve("wings-server.pass"), "passed\n");
            }
        } catch (Throwable failure) {
            fail(server, failure.toString());
        }
    }

    private static void initialize(MinecraftServer server, ServerPlayer wearer, ServerPlayer observer) {
        ServerLevel level = server.overworld();
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        for (int x = -10; x <= 100; x++) {
            for (int z = -8; z <= 20; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState());
                for (int y = 65; y <= 115; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        // wearer: survival, hungry-drainable, wings already equipped, airborne facing +X
        wearer.setGameMode(GameType.SURVIVAL);
        wearer.getInventory().clearContent();
        wearer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.WIND_THUNDER_WINGS.get()));
        wearer.getFoodData().setFoodLevel(20);
        wearer.getFoodData().setSaturation(0f);
        wearer.teleportTo(level, 0.5, 100, 0.5, -90f, 0f);
        wearer.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        wearerRef = wearer;
        // observer: creative vantage east of the wearer flight path
        observer.setGameMode(GameType.CREATIVE);
        observer.teleportTo(level, 15.5, 90, 12.5, -120f, -15f);
        observer.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        LOGGER.info("WINGS_QA_FIXTURE_READY");
    }

    private static int changePhase(CommandSourceStack source, String next) {
        MinecraftServer server = source.getServer();
        if (!ready) {
            source.sendFailure(Component.literal("WINGS_QA_NOT_READY"));
            return 0;
        }
        switch (next) {
            case "zap" -> {
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                if (wearer != null) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server.overworld());
                    bolt.setPos(wearer.position());
                    server.overworld().addFreshEntity(bolt);
                }
            }
            case "wearerDone" -> {
                // Pull the observer next to the wearer so its screenshot frames the synced wings.
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                ServerPlayer observer = server.getPlayerList().getPlayerByName(OBSERVER);
                if (wearer != null && observer != null) {
                    observer.teleportTo(server.overworld(),
                            wearer.getX() + 3, wearer.getY() + 1, wearer.getZ() + 3,
                            45f, 10f);
                }
            }
            default -> { }
        }
        announce(server, next);
        return 1;
    }

    private static void announce(MinecraftServer server, String next) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(PREFIX + next), false);
        LOGGER.info("{}{}", PREFIX, next);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        LOGGER.info("WINGS_E2E_ASSERT server: {}", message);
    }

    private static Path results() {
        return Path.of(System.getProperty("wings.qa.results", "build/wings-e2e"));
    }

    private static void fail(MinecraftServer server, String message) {
        LOGGER.error("WINGS_QA_FAILURE {}", message);
        try {
            Files.writeString(results().resolve("wings-server.failed"), message + "\n");
        } catch (Exception ignored) { }
        stopAt = server.getTickCount() + 20;
    }

    private static int finish(MinecraftServer server) {
        if (stopAt >= 0) return 1;
        LOGGER.info("WINGS_QA_SERVER_FINISHED");
        announce(server, "finished");
        stopAt = server.getTickCount() + 20;
        return 1;
    }

    private WingsQaServer() { }
}
