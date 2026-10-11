package com.zeropointsix.nighttest;

import com.zeropointsix.eraser.eternalnight.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Mod.EventBusSubscriber(modid = "night_qa")
public final class NightTestServer {
    private static final boolean ENABLED = "server".equals(System.getProperty("night.qa.role"));
    private static final Path RESULTS = Path.of(System.getProperty("night.qa.results", "build/night-e2e"));
    private static final Set<UUID> CAPTURED = new HashSet<>();
    private static final Set<UUID> READY = new HashSet<>();
    private static final Set<UUID> SLEPT = new HashSet<>();
    private static final Set<UUID> DEEP_SLEEP = new HashSet<>();
    private static final Map<UUID, BlockPos> BEDS = new HashMap<>();
    private static ServerPlayer owner, observer;
    private static BlockPos door;
    private static int phase;
    private static long lastEntryTick, longestEntryTick;
    private static int entryTicks;

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if (!ENABLED) return;
        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSourceStack>literal("nightqa")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("ready").executes(context -> {
                    READY.add(context.getSource().getPlayerOrException().getUUID()); return 1;
                }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("captured").executes(context -> {
                    CAPTURED.add(context.getSource().getPlayerOrException().getUUID()); return 1;
                })));
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void sleepTick(TickEvent.PlayerTickEvent event) {
        if (ENABLED && event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && BEDS.containsKey(player.getUUID()) && player.isSleepingLongEnough()) {
            if (DEEP_SLEEP.add(player.getUUID()))
                System.out.println("NIGHT_DEEP_SLEEP " + player.getGameProfile().getName() + " reached 100 ticks");
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || phase == 99) return;
        try {
            var server = event.getServer();
            if (phase == -2 || phase == -1) {
                long gap = System.nanoTime() - lastEntryTick;
                longestEntryTick = Math.max(longestEntryTick, gap);
                require(gap < 10_000_000_000L, "entry keeps server responsive: tick gap " + gap / 1_000_000 + " ms");
                lastEntryTick = System.nanoTime();
                entryTicks++;
            }
            if (phase == 0) {
                owner = server.getPlayerList().getPlayers().stream().filter(p -> p.getGameProfile().getName().equals("NightOwner")).findFirst().orElse(null);
                observer = server.getPlayerList().getPlayers().stream().filter(p -> p.getGameProfile().getName().equals("NightObserver")).findFirst().orElse(null);
                if (owner == null || observer == null) return;
                NightConfig.SPAWNING.set(false);
                NightConfig.ENTRY_BASE.set(1.0);
                NightConfig.ENTRY_CAP.set(1.0);
                server.getPlayerList().setViewDistance(2);
                server.overworld().setDayTime(18000);
                server.overworld().updateSkyBrightness();
                prepareBed(owner, -8); prepareBed(observer, 8);
                lastEntryTick = System.nanoTime();
                say(owner, "SLEEP"); say(observer, "SLEEP");
                phase = -2;
            } else if (phase == -2) {
                if (owner.isSleeping()) SLEPT.add(owner.getUUID());
                if (observer.isSleeping()) SLEPT.add(observer.getUUID());
                if (SLEPT.size() != 2) return;
                lastEntryTick = System.nanoTime();
                phase = -1;
            } else if (phase == -1 && EternalNightContent.isCity(owner.level()) && EternalNightContent.isCity(observer.level())) {
                require(DEEP_SLEEP.size() == 2, "both real players reached the 100-tick deep-sleep threshold");
                require(entryTicks >= 2, "server advances while cold city prepares");
                System.out.println("NIGHT_ENTRY_RESPONSIVE ticks=" + entryTicks + " maxGapMs=" + longestEntryTick / 1_000_000);
                require(owner.position().distanceTo(observer.position()) <= 48, "same-night cohort remains within 48 blocks");
                setupInteractionFixture(owner.serverLevel(), owner.blockPosition());
                say(owner, "READY"); say(observer, "READY");
                phase = 1;
            } else if (phase == 1 && READY.size() == 2) {
                say(owner, "OPEN"); phase = 2;
            } else if (phase == 2 && CitySavedData.get(owner.server.getLevel(EternalNightContent.LEVEL)).active(door, owner.serverLevel().getGameTime())) {
                require(NightEvents.playerData(owner).getLongArray("Doors").length == 100, "native right click opens 100th unique door");
                say(owner, "WHITE"); say(observer, "WHITE");
                phase = 3;
            } else if (phase == 3 && CAPTURED.size() == 2) {
                say(owner, "WALK"); phase = 4;
            } else if (phase == 4 && owner.level().dimension().equals(Level.OVERWORLD)) {
                assertHome(owner);
                observer.teleportTo(observer.serverLevel(), door.getX() + 0.5, door.getY(), door.getZ() - 2.5, 0, 12);
                say(observer, "WALK"); phase = 5;
            } else if (phase == 5 && observer.level().dimension().equals(Level.OVERWORLD)) {
                assertHome(observer);
                var city = owner.server.getLevel(EternalNightContent.LEVEL);
                require(CitySavedData.get(city).active(door, city.getGameTime()), "exit remains shared after both returns");
                Files.writeString(RESULTS.resolve("server-cycle.pass"), "PASS: native bed clicks and deep sleep, responsive cold-world same-night entry, native 100th door, shared exit, distinct beds, inventory and counter reset; max entry tick gap " + longestEntryTick / 1_000_000 + " ms\n");
                phase = 99;
            }
        } catch (Throwable error) {
            error.printStackTrace();
            try { Files.writeString(RESULTS.resolve("server.failed"), error.toString()); } catch (Exception ignored) { }
            phase = 99;
        }
    }

    private static void prepareBed(ServerPlayer player, int x) {
        ServerLevel world = player.server.overworld();
        BlockPos bed = new BlockPos(x, 65, 0);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -4; dz <= 4; dz++)
            world.setBlock(bed.offset(dx, -1, dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        var state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        world.setBlock(bed, state.setValue(BedBlock.PART, BedPart.FOOT), 2);
        world.setBlock(bed.north(), state.setValue(BedBlock.PART, BedPart.HEAD), 2);
        player.setRespawnPosition(Level.OVERWORLD, bed.north(), 0, false, false);
        BEDS.put(player.getUUID(), bed.north());
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(world, x + 0.5, bed.getY(), bed.getZ() - 3, 0, 24);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
    }

    private static void setupInteractionFixture(ServerLevel city, BlockPos base) {
        // QA-only flat viewing fixture. All displayed art comes from the verified original kit.
        for (int dx = -6; dx <= 6; dx++) for (int dz = -2; dz <= 8; dz++) {
            city.setBlock(base.offset(dx, -1, dz), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
            for (int dy = 0; dy <= 4; dy++) city.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        }
        int index = 0;
        for (var color : NeonSignBlock.Color.values()) {
            city.setBlock(base.offset(index++ - 2, 1, 6), EternalNightContent.NEON_SIGN.get().defaultBlockState()
                    .setValue(NeonSignBlock.COLOR, color).setValue(NeonSignBlock.FACING, Direction.NORTH), 2);
        }
        city.setBlock(base.offset(-3, 1, 6), EternalNightContent.EMERGENCY_LIGHT.get().defaultBlockState(), 2);
        door = base.offset(0, 0, 3);
        var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        city.setBlock(door, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        city.setBlock(door.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        long[] prior = new long[99];
        for (int i = 0; i < prior.length; i++) prior[i] = new BlockPos(i, -60, -10000).asLong();
        var data = NightEvents.playerData(owner); data.putLongArray("Doors", prior);
        owner.getPersistentData().put(NightEvents.PLAYER_TAG, data);
        owner.teleportTo(city, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 12);
        observer.teleportTo(city, base.getX() + 2.5, base.getY(), base.getZ() + 0.5, 33.7F, 10);
    }
    private static void assertHome(ServerPlayer player) {
        require(player.blockPosition().distSqr(BEDS.get(player.getUUID())) < 25, "returns to personal bed");
        require(NightEvents.playerData(player).isEmpty(), "return clears personal door history");
        require(player.getInventory().getItem(0).is(Items.DIAMOND) && player.getInventory().getItem(0).getCount() == 7, "inventory preserved");
    }
    private static void say(ServerPlayer player, String phase) { player.sendSystemMessage(Component.literal("NIGHT_QA_" + phase)); }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
