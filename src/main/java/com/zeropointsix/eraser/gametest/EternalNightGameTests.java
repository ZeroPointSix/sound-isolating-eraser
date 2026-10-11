package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.eternalnight.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("eternal_night_tests")
@PrefixGameTestTemplate(false)
public final class EternalNightGameTests {
    private static GameTestConfigs.LiveConfig liveConfig;

    @BeforeBatch(batch = "eternal_night")
    public static void isolateConfig(ServerLevel level) {
        liveConfig = GameTestConfigs.sandbox(NightConfig.SPEC);
        // SavedData lives on the shared city level and leaks between tests
        // (exits, boss reservations) — reset it so batch order cannot matter.
        ServerLevel city = level.getServer().getLevel(EternalNightContent.LEVEL);
        if (city != null) CitySavedData.get(city).clearForTesting();
    }

    @AfterBatch(batch = "eternal_night")
    public static void restoreConfig(ServerLevel level) {
        if (liveConfig != null) {
            liveConfig.close();
            liveConfig = null;
        }
    }

    @GameTest(template = "empty", batch = "eternal_night", timeoutTicks = 1400)
    public static void cityDeepSleepAndReturnPreservePlayer(GameTestHelper h) {
        ServerLevel overworld = h.getLevel();
        var session = login(h);
        var player = session.player();
        var channel = session.channel();
        double base = NightConfig.ENTRY_BASE.get(), cap = NightConfig.ENTRY_CAP.get();
        long time = overworld.getDayTime();
        int distance = player.server.getPlayerList().getViewDistance();
        java.util.concurrent.atomic.AtomicBoolean cleaned = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean entryChecked = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup = () -> {
            if (!cleaned.compareAndSet(false, true)) return;
            CityEntryQueue.cancel(player.getUUID());
            NightConfig.ENTRY_BASE.set(base); NightConfig.ENTRY_CAP.set(cap);
            overworld.setDayTime(time);
            player.server.getPlayerList().setViewDistance(distance);
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        };
        try {
        player.server.getPlayerList().setViewDistance(2);
        NightConfig.ENTRY_BASE.set(1.0);
        NightConfig.ENTRY_CAP.set(1.0);
        overworld.setDayTime(18000);
        overworld.updateSkyBrightness();
        BlockPos bed = h.absolutePos(new BlockPos(1, 2, 1));
        overworld.setBlock(bed.below(), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        overworld.setBlock(bed.north().below(), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        var state = Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH);
        overworld.setBlock(bed, state.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
        overworld.setBlock(bed.north(), state.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
        player.connection.teleport(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0, 0);
        player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
        player.getStats().setValue(player, net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST), 96000);
        var result = player.startSleepInBed(bed.north());
        h.assertTrue(result.right().isPresent(), "real valid bed sleep starts: " + result);
        // An embedded channel is not in ServerConnectionListener, which normally calls doTick.
        h.onEachTick(() -> {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) == player) player.doTick();
        });
        BlockPos savedBed = player.getRespawnPosition();
        h.runAfterDelay(105, () -> {
            h.assertTrue(NightConfig.ENTRY_BASE.get() == 1.0 && NightConfig.ENTRY_CAP.get() == 1.0,
                    "isolated entry probability remains deterministic through deep sleep");
            h.assertTrue(!player.isSleeping(), "deep sleep wakes before vanilla skips the night");
            h.assertTrue(CityEntryQueue.pending(player) || EternalNightContent.isCity(player.level()),
                    "deep sleep schedules entry at 100 ticks without blocking");
            entryChecked.set(true);
        });
        h.runAfterDelay(1390, cleanup);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(entryChecked.get() && EternalNightContent.isCity(player.level()),
                "asynchronous deep-sleep entry completes")).thenExecute(() -> {
            try {
                h.assertTrue(overworld.getDayTime() < 24000, "entry happens before vanilla skips the night");
                h.assertTrue(player.getInventory().getItem(0).getCount() == 7, "entry preserves inventory");
                h.assertTrue(savedBed.equals(player.getRespawnPosition()), "entry preserves original bed");
                h.assertTrue(CityLayout.street(player.getBlockX(), player.getBlockZ()), "arrives on a city street");
                var data = NightEvents.playerData(player);
                data.putLongArray("Doors", new long[]{12, 34});
                player.getPersistentData().put(NightEvents.PLAYER_TAG, data);
                CompoundTag saved = player.saveWithoutId(new CompoundTag());
                var reloaded = new net.minecraft.server.level.ServerPlayer(player.server, player.serverLevel(), player.getGameProfile());
                reloaded.load(saved);
                h.assertTrue(NightEvents.playerData(reloaded).getLongArray("Doors").length == 2,
                        "actual player save/load preserves door history");
                NightEvents.returnHome(player);
                h.assertTrue(player.serverLevel() == overworld, "returns to overworld");
                h.assertTrue(player.blockPosition().distSqr(savedBed) < 25, "returns beside own bed");
                h.assertTrue(NightEvents.playerData(player).isEmpty(), "return clears history");
                h.assertTrue(player.getInventory().getItem(0).getCount() == 7, "return preserves inventory");
            } finally {
                cleanup.run();
            }
        }).thenSucceed();
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "eternal_night", timeoutTicks = 240)
    public static void cityGeneratedSubwayAndShaftConnect(GameTestHelper h) {
        ServerLevel city = h.getLevel().getServer().getLevel(EternalNightContent.LEVEL);
        int bx = 0;
        while (!CityTemplates.hasStation(city.getSeed(), bx, 0) && bx < 100) bx++;
        h.assertTrue(bx < 100, "deterministic nearby station exists");
        int x = bx * 64, z = 48;
        for (int dx = 0; dx <= 71; dx++) {
            BlockPos feet = new BlockPos(x + dx, CityChunkGenerator.SUBWAY_Y + 2, z + 7);
            h.assertTrue(city.getBlockState(feet).isAir() && city.getBlockState(feet.above()).isAir(),
                    "station line remains open across chunk seams at " + feet);
        }
        for (int dz = 0; dz <= 64; dz++) {
            BlockPos feet = new BlockPos(x + 8, CityChunkGenerator.SUBWAY_Y + 2, dz);
            h.assertTrue(city.getBlockState(feet).isAir() && city.getBlockState(feet.above()).isAir(),
                    "north/south line and both junctions stay open at " + feet);
        }
        int surface = CityLayout.plateau(bx, 0);
        for (int y = CityChunkGenerator.SUBWAY_Y + 2; y <= surface; y++) {
            BlockPos ladder = new BlockPos(x + 48 + 4, y, z + 13);
            h.assertTrue(city.getBlockState(ladder).is(Blocks.LADDER), "continuous climb to surface at " + ladder);
            h.assertTrue(city.getBlockState(ladder.west()).isSolid(), "ladder has continuous support");
        }
        h.succeed();
    }

    private record TestSession(net.minecraft.server.level.ServerPlayer player,
            io.netty.channel.embedded.EmbeddedChannel channel) { }

    @GameTest(template = "empty", batch = "eternal_night")
    public static void cityPendingEntryIsUniqueAndLogoutCancels(GameTestHelper h) {
        var session = login(h);
        var player = session.player();
        try {
            CompoundTag prior = new CompoundTag();
            prior.putLongArray("Doors", new long[]{12, 34});
            player.getPersistentData().put(NightEvents.PLAYER_TAG, prior.copy());
            h.assertTrue(NightEvents.enter(player) && NightEvents.enter(player), "duplicate requests are accepted once");
            h.assertTrue(CityEntryQueue.pending(player) && player.serverLevel() == h.getLevel(),
                    "request does not synchronously teleport");
            var attempt = new net.minecraftforge.event.entity.player.PlayerSleepInBedEvent(player, java.util.Optional.of(player.blockPosition()));
            NightEvents.sleep(attempt);
            h.assertTrue(attempt.getResultStatus() == net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM,
                    "pending entry rejects another sleep that could advance the night");
            h.assertTrue(NightEvents.playerData(player).equals(prior), "waiting preserves prior player data");
            player.server.getPlayerList().remove(player);
            h.assertTrue(!CityEntryQueue.pending(player), "real logout releases the pending request");
            h.succeed();
        } finally {
            CityEntryQueue.cancel(player.getUUID());
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            session.channel().finishAndReleaseAll();
        }
    }

    private static TestSession login(GameTestHelper h) {
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,
                    net.minecraft.network.PacketSendListener callback) { }
        };
        // Forge's login hook needs a channel before PlayerList.placeNewPlayer, unlike vanilla's helper.
        var channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
        connection.setProtocol(net.minecraft.network.ConnectionProtocol.PLAY);
        UUID uuid = UUID.randomUUID();
        var player = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(uuid, "City" + uuid.toString().substring(0, 8)));
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return new TestSession(player, channel);
    }

    @GameTest(template = "empty", batch = "eternal_night", timeoutTicks = 200)
    public static void cityRealPlayerDoorInteractions(GameTestHelper h) {
        ServerLevel city = h.getLevel().getServer().getLevel(EternalNightContent.LEVEL);
        var session = login(h);
        var player = session.player();
        var channel = session.channel();
        BlockPos first = new BlockPos(2048, 120, 2048);
        try {
            // This test also runs repeatedly in the same developer world.
            for (int x = -1; x <= 4; x++) for (int z = -1; z <= 9; z++) {
                city.setBlock(first.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                for (int y = 0; y <= 3; y++) city.setBlock(first.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
            player.teleportTo(city, first.getX() + 0.5, first.getY(), first.getZ() - 2, 0, 0);
            h.assertTrue(CitySpawns.withinCountedRange(player, first), "nearby candidate counted");
            h.assertTrue(!CitySpawns.withinCountedRange(player, first.above(80)), "out-of-range roof cannot bypass cap");
            player.connection.teleport(first.getX() + 0.5, 42, first.getZ() + 0.5, 0, 0);
            h.assertTrue(!CitySpawns.withinCountedRange(player, new BlockPos(first.getX(), 80, first.getZ())),
                    "underground players cannot trigger surface spawns");
            player.connection.teleport(first.getX() + 0.5, first.getY(), first.getZ() - 2, 0, 0);
            player.getPersistentData().remove(NightEvents.PLAYER_TAG);
            installDoor(city, first, Blocks.OAK_DOOR);
            installDoor(city, first.east(3), Blocks.BIRCH_DOOR);
            use(player, first.above());
            h.assertTrue(city.getBlockState(first).getValue(DoorBlock.OPEN), "real upper-half use opens the door");
            use(player, first);
            use(player, first);
            use(player, first.east(3));
            h.assertTrue(NightEvents.playerData(player).getLongArray("Doors").length == 2,
                    "actual upper/lower/repeated interactions count twice; got "
                            + NightEvents.playerData(player).getLongArray("Doors").length);

            BlockPos iron = first.south(4);
            installDoor(city, iron, Blocks.IRON_DOOR);
            BlockPos lever = iron.east();
            city.setBlock(lever.below(), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            city.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACE,
                    net.minecraft.world.level.block.state.properties.AttachFace.FLOOR), 3);
            use(player, lever);
            h.assertTrue(city.getBlockState(iron).getValue(DoorBlock.OPEN), "real lever opens iron door");
            h.assertTrue(NightEvents.playerData(player).getLongArray("Doors").length == 3, "lever has causal player credit");

            BlockPos uncredited = first.south(8);
            installDoor(city, uncredited, Blocks.IRON_DOOR);
            // Move within the ~6-block interaction reach and clear the target cell so the
            // placement deterministically lands (city generation can occupy it).
            player.connection.teleport(uncredited.getX() + 0.5, uncredited.getY(), uncredited.getZ() - 1.5, 0, 0);
            city.setBlock(uncredited.east(), Blocks.AIR.defaultBlockState(), 3);
            player.setShiftKeyDown(true);
            net.minecraft.world.item.ItemStack redstone = new net.minecraft.world.item.ItemStack(Blocks.REDSTONE_BLOCK);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, redstone);
            player.gameMode.useItemOn(player, city, redstone, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(uncredited), Direction.EAST, uncredited, false));
            h.assertTrue(city.getBlockState(uncredited).getValue(DoorBlock.OPEN), "placed redstone powers test door");
            h.assertTrue(NightEvents.playerData(player).getLongArray("Doors").length == 3,
                    "item placement is not attributed as a button or lever action");
            h.succeed();
        } finally {
            h.getLevel().getServer().getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }

    private static void installDoor(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block door) {
        level.setBlock(pos.below(), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        level.setBlock(pos, door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(pos.above(), door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
    }

    private static void use(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        player.connection.teleport(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5, 0, 0);
        player.gameMode.useItemOn(player, player.serverLevel(), net.minecraft.world.item.ItemStack.EMPTY,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    }

    @GameTest(template = "empty", batch = "eternal_night")
    public static void cityGeneratedContainersAreLazyAndNeverRefill(GameTestHelper h) {
        ServerLevel city = h.getLevel().getServer().getLevel(EternalNightContent.LEVEL);
        int bx = 64;
        while (Math.floorMod(CityLayout.hash(city.getSeed(), bx, 0), 4) != 0) bx++;
        var containers = new java.util.ArrayList<net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity>();
        for (int dx = 1; dx <= 2; dx++) for (int dz = 1; dz <= 2; dz++) {
            var chunk = city.getChunk(bx * 4 + dx, dz);
            for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                var entity = city.getBlockEntity(pos);
                if (entity instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container)
                    containers.add(container);
            }
        }
        h.assertTrue(containers.size() >= 1 && containers.size() <= 4,
                "one generated large building has 1..4 containers across its four chunks; got " + containers.size());
        for (var container : containers) {
            CompoundTag before = container.saveWithFullMetadata();
            try {
            h.assertTrue(before.getString("LootTable").startsWith(ModMain.MOD_ID + ":chests/eternal_night/"),
                    "generated container still has deferred city loot before first opening");
            h.assertTrue(!before.contains("Items"), "generator does not prefill inventory");
            container.unpackLootTable(null);
            h.assertTrue(!container.isEmpty(), "actual server loot table produces equipment");
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                var stack = container.getItem(slot);
                h.assertTrue(!stack.isEdible() && !(stack.getItem() instanceof net.minecraft.world.item.PotionItem)
                        && !stack.is(net.minecraft.world.item.Items.WATER_BUCKET), "no food, potion or water in actual loot");
            }
            container.clearContent();
            container.unpackLootTable(null);
            h.assertTrue(container.isEmpty(), "empty container never refills");
            CompoundTag emptied = container.saveWithFullMetadata();
            h.assertTrue(!emptied.contains("LootTable"), "consumed loot marker is removed from saved NBT");
            var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                    container.getBlockPos(), container.getBlockState(), emptied);
            h.assertTrue(restored instanceof net.minecraft.world.Container c && c.isEmpty(), "empty inventory survives NBT reload");
            } finally {
                container.load(before);
                container.setChanged();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "eternal_night", timeoutTicks = 300)
    public static void cityDeathDropsAndRespawnsAtOwnBed(GameTestHelper h) {
        var session = login(h);
        var player = session.player();
        var server = h.getLevel().getServer();
        ServerLevel city = server.getLevel(EternalNightContent.LEVEL);
        boolean keepInventory = city.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        boolean reset = NightConfig.RESET_ON_DEATH.get();
        var observed = new java.util.ArrayList<net.minecraftforge.event.entity.living.LivingDropsEvent>();
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingDropsEvent> listener = event -> {
            if (event.getEntity() == player) observed.add(event);
        };
        java.util.concurrent.atomic.AtomicBoolean cleaned = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup = () -> {
            if (!cleaned.compareAndSet(false, true)) return;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
            observed.forEach(event -> event.getDrops().forEach(net.minecraft.world.entity.Entity::discard));
            city.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(keepInventory, server);
            NightConfig.RESET_ON_DEATH.set(reset);
            var current = server.getPlayerList().getPlayer(player.getUUID());
            if (current != null) server.getPlayerList().remove(current);
            session.channel().finishAndReleaseAll();
        };
        try {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                    true, net.minecraftforge.event.entity.living.LivingDropsEvent.class, listener);
            city.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(false, server);
            NightConfig.RESET_ON_DEATH.set(true);
            BlockPos bed = new BlockPos(-128, 90, -128);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                h.getLevel().setBlock(bed.offset(dx, -1, dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                for (int dy = 0; dy <= 3; dy++) h.getLevel().setBlock(bed.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
            var bedState = Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH);
            h.getLevel().setBlock(bed, bedState.setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
            h.getLevel().setBlock(bed.north(), bedState.setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
            player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, bed.north(), 0, false, false);
            h.assertTrue(net.minecraft.world.entity.player.Player.findRespawnPositionAndUseSpawnBlock(
                    h.getLevel(), bed.north(), 0, false, true).isPresent(), "bed fixture is safe for this world seed");
            BlockPos death = new BlockPos(4096, 120, 4096);
            city.setBlock(death.below(), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            player.teleportTo(city, death.getX() + 0.5, death.getY(), death.getZ() + 0.5, 0, 0);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
            CompoundTag data = new CompoundTag();
            data.putLongArray("Doors", new long[]{12, 34}); data.putBoolean("Visiting", true);
            player.getPersistentData().put(NightEvents.PLAYER_TAG, data);
            h.runAfterDelay(290, cleanup);
            h.startSequence().thenWaitUntil(() -> h.assertTrue(city.isPositionEntityTicking(death),
                    "death fixture chunk is entity-ticking")).thenExecute(() -> {
            try {
            h.assertTrue(player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE) && !player.isAlive(), "real damage causes death");
            h.assertTrue(NightEvents.playerData(player).getBoolean("ReturnAfterDeath"), "death return marker is persisted before respawn");
            h.assertTrue(NightEvents.playerData(player).getLongArray("Doors").length == 0, "death clears prior door history");
            h.assertTrue(observed.size() == 1 && !observed.get(0).isCanceled(), "one uncancelled real death-drop event");
            int diamonds = 0;
            for (var drop : observed.get(0).getDrops()) {
                String evidence = "id=" + drop.getUUID() + " count=" + drop.getItem().getCount()
                        + " removed=" + drop.isRemoved() + " visible=" + (city.getEntity(drop.getUUID()) == drop);
                System.out.println("CITY_DEATH_DROP " + evidence);
                h.assertTrue(drop.getItem().is(net.minecraft.world.item.Items.DIAMOND), "only the configured diamond inventory drops");
                h.assertTrue(!drop.isRemoved() && drop.level() == city && city.getEntity(drop.getUUID()) == drop,
                        "death item actually entered the world: " + evidence);
                diamonds += drop.getItem().getCount();
            }
            h.assertTrue(diamonds == 7, "vanilla death drops exactly seven diamonds, got " + diamonds);
            var respawned = server.getPlayerList().respawn(player, false);
            h.assertTrue(respawned.serverLevel() == h.getLevel() && respawned.blockPosition().distSqr(bed) < 25,
                    "real respawn returns to the original overworld bed: actual=" + respawned.blockPosition()
                            + " dimension=" + respawned.level().dimension().location());
            h.assertTrue(NightEvents.playerData(respawned).isEmpty(), "respawn clears visit and death marker");
            h.assertTrue(respawned.getInventory().isEmpty(), "death return does not duplicate dropped items");
            } finally {
                cleanup.run();
            }
            }).thenSucceed();
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "eternal_night", timeoutTicks = 1400)
    public static void cityBuildingsReachableAcrossDistricts(GameTestHelper h) {
        ServerLevel city = h.getLevel().getServer().getLevel(EternalNightContent.LEVEL);
        long seed = city.getSeed();
        java.util.EnumMap<CityLayout.District, long[]> blocks = new java.util.EnumMap<>(CityLayout.District.class);
        for (int i = 0; i < 400 && blocks.size() < CityLayout.District.values().length; i++) {
            int bx = i % 20, bz = i / 20;
            blocks.computeIfAbsent(CityLayout.district(bx * 64 + 32, bz * 64 + 32), k -> new long[]{bx, bz});
        }
        h.assertTrue(blocks.size() == CityLayout.District.values().length,
                "sampled area contains every district: " + blocks.keySet());
        int checked = 0;
        for (var entry : blocks.entrySet()) {
            int bx = (int) entry.getValue()[0], bz = (int) entry.getValue()[1];
            // Replicate CityTemplates.place's per-tile routing to find every placed building.
            boolean large = Math.floorMod(CityLayout.hash(seed, bx, bz), 4) == 0;
            java.util.List<String> placements = new java.util.ArrayList<>();
            if (large) {
                String name = new String[]{"mall", "civic_center", "townhall", "library"}[
                        (int) Math.floorMod(CityLayout.hash(seed + 1, bx, bz), 4)];
                placements.add(name + "@" + ((bx * 4 + 1) * 16) + "," + CityLayout.plateau(bx, bz) + "," + ((bz * 4 + 1) * 16));
            }
            for (int lx = 1; lx <= 3; lx++) for (int lz = 1; lz <= 3; lz++) {
                boolean coveredByLarge = large && lx <= 2 && lz <= 2;
                int tx = bx * 4 + lx, tz = bz * 4 + lz;
                boolean station = lx == 3 && lz == 3 && CityTemplates.hasStation(seed, bx, bz);
                boolean plaza = lx == 3 && lz == 1 && Math.floorMod(CityLayout.hash(seed + 3, bx, bz), 4) == 0;
                if (!coveredByLarge && !station && !plaza)
                    placements.add(CityLayout.building(seed, tx, tz) + "@" + tx * 16 + "," + CityLayout.plateau(bx, bz) + "," + tz * 16);
            }
            for (String spec : placements) {
                String name = spec.split("@")[0];
                BlockPos origin = new BlockPos(Integer.parseInt(spec.split("@")[1].split(",")[0]),
                        Integer.parseInt(spec.split("@")[1].split(",")[1]),
                        Integer.parseInt(spec.split("@")[1].split(",")[2]));
                var template = city.getStructureManager()
                        .get(CityTemplates.id("buildings/" + name)).orElseThrow();
                var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
                java.util.List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo> doors
                        = new java.util.ArrayList<>();
                for (var block : net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValues())
                    if (block instanceof DoorBlock)
                        for (var info : template.filterBlocks(origin, settings, block))
                            if (info.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                                doors.add(info);
                h.assertTrue(!doors.isEmpty(), entry.getKey() + " building " + name
                        + " at " + origin + " template declares a door");
                boolean reached = false;
                for (var door : doors) {
                    Direction facing = door.state().getValue(DoorBlock.FACING);
                    for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
                        BlockPos outside = door.pos().relative(side);
                        if (reachedStreet(city, outside)) { reached = true; break; }
                    }
                    if (reached) break;
                }
                h.assertTrue(reached, entry.getKey() + " building " + name + " at " + origin
                        + " has a door that walks to a street");
                System.out.println("CITY_REACH district=" + entry.getKey() + " building=" + name
                        + " origin=" + origin + " doors=" + doors.size());
                checked++;
            }
        }
        h.assertTrue(checked >= blocks.size(), "at least one building verified per district");
        h.succeed();
    }

    /** Feet level a walker stands at in column (x,z), nearest the given y within a one-block step. */
    private static int floorNear(ServerLevel level, int x, int z, int y) {
        for (int dy = 1; dy >= -4; dy--) {
            int feet = y + dy;
            BlockPos below = new BlockPos(x, feet - 1, z);
            if (feet - 1 < level.getMinBuildHeight() || feet + 1 >= level.getMaxBuildHeight()) continue;
            if (level.getBlockState(below).isSolidRender(level, below)
                    && !level.getBlockState(below.above()).isSolidRender(level, below.above())
                    && !level.getBlockState(below.above(2)).isSolidRender(level, below.above(2)))
                return feet;
        }
        return Integer.MIN_VALUE;
    }

    /** Breadth-first walk over open, step-up-or-down-at-most-one ground to a street tile. */
    private static boolean reachedStreet(ServerLevel level, BlockPos start) {
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        queue.add(start);
        int budget = 12000;
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPos pos = queue.poll();
            if (!seen.add(pos.asLong())) continue;
            if (CityLayout.street(pos.getX(), pos.getZ())) return true;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(direction);
                int nextY = floorNear(level, next.getX(), next.getZ(), pos.getY());
                if (nextY == Integer.MIN_VALUE || Math.abs(nextY - pos.getY()) > 1) continue;
                queue.add(new BlockPos(next.getX(), nextY, next.getZ()));
            }
        }
        return false;
    }

    @GameTest(template = "empty", batch = "eternal_night")
    public static void cityMissingShadowNeverUsesRegistryDefault(GameTestHelper h) {
        net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation(ModMain.MOD_ID, "shadow_tangler");
        boolean registered = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.containsKey(id);
        h.assertTrue(registered == (CitySpawns.shadowType() != null), "missing PR18 must skip spawn, never use default pig");
        h.succeed();
    }
    @GameTest(template = "empty", batch = "eternal_night")
    public static void citySaveRoundtripAndExitExpiry(GameTestHelper h) {
        CitySavedData data = new CitySavedData();
        BlockPos door = new BlockPos(-32, 70, -17);
        UUID boss = UUID.randomUUID();
        data.open(door, 6000);
        h.assertTrue(data.reserveBoss(CityLayout.region(-10, 2), boss), "first boss reserved");
        CitySavedData restored = CitySavedData.load(data.save(new CompoundTag()));
        h.assertTrue(restored.active(door, 5999), "shared exit survives restart before expiry");
        h.assertTrue(!restored.active(door, 6000), "exit expires exactly at deadline");
        h.assertTrue(restored.activeExits(6000).isEmpty(), "expired entries removed");
        h.assertTrue(!restored.reserveBoss(CityLayout.region(-10, 2), UUID.randomUUID()), "unloaded boss still owns region");
        restored.releaseBoss(boss);
        h.assertTrue(restored.reserveBoss(CityLayout.region(-10, 2), UUID.randomUUID()), "death releases region");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "eternal_night")
    public static void cityDoorCanonicalizationAndWalkThrough(GameTestHelper h) {
        BlockPos lower = new BlockPos(-3, 80, 4);
        h.assertTrue(NightEvents.lowerDoor(lower.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)).equals(lower), "upper and lower are one door");
        Vec3 center = Vec3.atBottomCenterOf(lower);
        h.assertTrue(NightEvents.crossedDoor(center.add(0, 0, -0.4), center.add(0, 0, 0.4), lower, Direction.NORTH), "crossing activates exit");
        h.assertTrue(!NightEvents.crossedDoor(center.add(0, 0, -0.4), center.add(0, 0, -0.2), lower, Direction.NORTH), "approaching does not activate");
        h.assertTrue(!NightEvents.crossedDoor(center.add(4, 0, -0.4), center.add(4, 0, 0.4), lower, Direction.NORTH), "crossing elsewhere is not a door");
        h.assertTrue(!NightEvents.crossedDoor(center, center, lower, Direction.NORTH), "standing still is not crossing");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "eternal_night")
    public static void cityDoorHistoryNbtAndPity(GameTestHelper h) {
        NightRules.Doors doors = new NightRules.Doors(new long[0]);
        for (int i = 1; i <= 99; i++) h.assertTrue(!doors.open(i, 100, 0.01, 0.99).exit(), "no early exit for failing rolls");
        CompoundTag player = new CompoundTag();
        player.putLongArray("Doors", doors.save());
        NightRules.Doors restored = new NightRules.Doors(player.getLongArray("Doors"));
        h.assertTrue(!restored.open(99, 100, 0.01, 0).counted(), "repeated door cannot roll again after reload");
        h.assertTrue(restored.open(100, 100, 0.01, 0.99).exit(), "100th unique door guarantees exit");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "eternal_night")
    public static void cityDimensionAndArtContract(GameTestHelper h) {
        ServerLevel city = h.getLevel().getServer().getLevel(EternalNightContent.LEVEL);
        h.assertTrue(city != null, "dimension registered");
        h.assertTrue(!city.dimensionType().hasSkyLight() && city.dimensionType().hasFixedTime(), "fixed night without skylight");
        h.assertTrue(city.dimensionType().ambientLight() == 0 && !city.dimensionType().hasRaids(), "darkness and no raids");
        h.assertTrue(!city.dimensionType().respawnAnchorWorks(), "anchors disabled");
        h.assertTrue(CityTemplates.available(city), "all 12 original building templates installed");
        h.assertTrue(EternalNightContent.NEON_SIGN.get().defaultBlockState().getLightEmission() == 12, "art neon has light 12");
        h.assertTrue(EternalNightContent.EMERGENCY_LIGHT.get().defaultBlockState().getLightEmission() == 4, "emergency light has light 4");
        h.succeed();
    }

    /** Spec bans in the city: sleep problem, bed/anchor click, spawn set, growth, vanilla mobs. */
    @GameTest(template = "empty", batch = "eternal_night", timeoutTicks = 120)
    public static void cityEventInterceptionCoversSpec(GameTestHelper h) {
        ServerLevel city = h.getLevel().getServer().getLevel(EternalNightContent.LEVEL);
        BlockPos pos = new BlockPos(0, 70, 0);
        var player = login(h).player();
        player.teleportTo(city, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);

        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;

        var sleep = new net.minecraftforge.event.entity.player.PlayerSleepInBedEvent(player, java.util.Optional.of(pos));
        bus.post(sleep);
        h.assertTrue(sleep.getResultStatus() == net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM,
                "sleep resolves OTHER_PROBLEM in city");

        for (var bed : new net.minecraft.world.level.block.Block[] { Blocks.RED_BED, Blocks.RESPAWN_ANCHOR }) {
            city.setBlock(pos, bed.defaultBlockState(), 3);
            var click = new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                    player, net.minecraft.world.InteractionHand.MAIN_HAND, pos,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            bus.post(click);
            h.assertTrue(click.isCanceled(), bed + " interaction canceled in city");
            city.removeBlock(pos, false);
        }

        var setSpawn = new net.minecraftforge.event.entity.player.PlayerSetSpawnEvent(player, EternalNightContent.LEVEL, pos, false);
        bus.post(setSpawn);
        h.assertTrue(setSpawn.isCanceled(), "spawn point cannot be set in city");

        city.setBlock(pos, Blocks.WHEAT.defaultBlockState(), 3);
        var crop = new net.minecraftforge.event.level.BlockEvent.CropGrowEvent.Pre(city, pos,
                Blocks.WHEAT.defaultBlockState());
        bus.post(crop);
        h.assertTrue(crop.getResult() == net.minecraftforge.eventbus.api.Event.Result.DENY,
                "crop growth denied in city");
        var oakFeature = city.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getHolderOrThrow(net.minecraft.data.worldgen.features.TreeFeatures.OAK);
        var tree = new net.minecraftforge.event.level.SaplingGrowTreeEvent(city,
                net.minecraft.util.RandomSource.create(0), pos, oakFeature);
        bus.post(tree);
        h.assertTrue(tree.getResult() == net.minecraftforge.eventbus.api.Event.Result.DENY,
                "tree growth denied in city");
        var bonemeal = new net.minecraftforge.event.entity.player.BonemealEvent(player, city, pos,
                Blocks.WHEAT.defaultBlockState(), new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.BONE_MEAL));
        bus.post(bonemeal);
        h.assertTrue(bonemeal.isCanceled(), "bonemeal canceled in city");
        city.removeBlock(pos, false);

        var zombie = new net.minecraft.world.entity.monster.Zombie(city);
        var spawn = new net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn(zombie, city,
                0, 70, 0, city.getCurrentDifficultyAt(pos),
                net.minecraft.world.entity.MobSpawnType.NATURAL, null, null, null);
        bus.post(spawn);
        h.assertTrue(spawn.isSpawnCancelled(), "vanilla natural spawn denied in city");
        h.succeed();
    }
}
