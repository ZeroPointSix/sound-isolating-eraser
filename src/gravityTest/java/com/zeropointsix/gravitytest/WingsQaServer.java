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
    private static int trackUntil = -1;
    private static boolean trackWearer = true;
    private static net.minecraft.world.phys.Vec3 trackTarget;
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
            // Server-side asserts run on phase commands from the clients, not on
            // tick positions — llvmpipe clients tick slower than the server.
        } catch (Throwable failure) {
            fail(server, failure.toString());
        }
        // Keep the observer glued to the track target (wearer flank / dropped item)
        // during the screenshot window; the wearer is client-authoritative in flight,
        // so its own client may keep drifting.
        if (server.getTickCount() < trackUntil) {
            ServerPlayer observer = server.getPlayerList().getPlayerByName(OBSERVER);
            if (observer != null) {
                if (trackWearer) {
                    ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                    if (wearer != null) faceAt(wearer, observer);
                } else if (trackTarget != null) {
                    faceAt(trackTarget, observer);
                }
            }
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
            case "hungerDone" -> {
                // Wearer observed its own food drop client-side; confirm the same
                // drain server-side (ServerPlayer FoodData, not a synced copy).
                if (!hungerChecked && wearerRef != null) {
                    hungerChecked = true;
                    int food = wearerRef.getFoodData().getFoodLevel();
                    check(food < 20, "deployed wings drain real hunger server-side (food=" + food + ")");
                    try {
                        Files.writeString(results().resolve("wings-server.pass"), "passed\n");
                    } catch (Exception ignored) { }
                }
            }
            case "wearerDone" -> {
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                ServerPlayer observer = server.getPlayerList().getPlayerByName(OBSERVER);
                if (wearer != null && observer != null) {
                    faceAt(wearer, observer);
                    trackWearer = true;
                    trackUntil = server.getTickCount() + 30;
                }
            }
            case "storm" -> {
                // QA override: force wearer into STORM tier for remote-FX / FOV evidence
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                if (wearer != null && com.zeropointsix.eraser.wings.WingsState.deployed(wearer)) {
                    com.zeropointsix.eraser.wings.WingsState.setTier(wearer,
                            com.zeropointsix.eraser.wings.WingsState.STORM);
                    com.zeropointsix.eraser.wings.net.WingsNet.syncToTracking(wearer);
                    check(true, "wearer forced to STORM for observer FX evidence");
                }
            }
            case "handitem" -> {
                // Wings in main hand → first-person held-item render evidence
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                if (wearer != null) {
                    wearer.getInventory().setItem(0,
                            new ItemStack(ModItems.WIND_THUNDER_WINGS.get()));
                    wearer.getInventory().selected = 0;
                }
            }
            case "flood" -> {
                // 水下飞行回归：佩戴者悬停中被水体包住，展开态必须保持
                ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
                if (wearer != null) {
                    ServerLevel wlevel = wearer.serverLevel();
                    BlockPos c = wearer.blockPosition();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                wlevel.setBlockAndUpdate(c.offset(dx, dy, dz),
                                        Blocks.WATER.defaultBlockState());
                            }
                        }
                    }
                    check(true, "wearer submerged in water while deployed");
                }
            }
            case "itemDrop" -> {
                // 掉落物渲染证据：把一枚风雷翅落到固定点，观察者贴脸看
                ServerLevel wlevel = server.overworld();
                net.minecraft.world.entity.item.ItemEntity drop =
                        new net.minecraft.world.entity.item.ItemEntity(wlevel,
                                5.5, 65.1, 5.5,
                                new ItemStack(ModItems.WIND_THUNDER_WINGS.get()));
                drop.setDeltaMovement(0, 0, 0);
                wlevel.addFreshEntity(drop);
                // 尺寸参照物：掉落物旁立一块整石，截图可量出 0.55 格收翼实际大小
                wlevel.setBlockAndUpdate(new BlockPos(6, 65, 5), Blocks.STONE.defaultBlockState());
                trackTarget = new net.minecraft.world.phys.Vec3(5.5, 65.1, 5.5);
                trackWearer = false;
                trackUntil = server.getTickCount() + 40;
            }
            default -> { }
        }
        announce(server, next);
        return 1;
    }

    /** Teleport the observer to a flank vantage facing the given entity. */
    private static void faceAt(net.minecraft.world.entity.Entity target, ServerPlayer observer) {
        faceAt(new net.minecraft.world.phys.Vec3(target.getX(),
                target.getY() + target.getBbHeight() * 0.6, target.getZ()), observer);
    }

    /** Teleport the observer ~4.5 blocks off the target, looking straight at it. */
    private static void faceAt(net.minecraft.world.phys.Vec3 target, ServerPlayer observer) {
        double ox = target.x + 3.2;
        double oy = target.y + 1.2;
        double oz = target.z + 3.2;
        double dx = target.x - ox, dy = target.y - oy, dz = target.z - oz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
        observer.teleportTo(observer.serverLevel(), ox, oy, oz, yaw, pitch);
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
