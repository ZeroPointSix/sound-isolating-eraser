package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.eternalnight.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
            h.assertTrue(!player.isSleeping(), "deep sleep wakes before vanilla skips the night");
            h.assertTrue(CityEntryQueue.pending(player) || EternalNightContent.isCity(player.level()),
                    "deep sleep schedules entry at 100 ticks without blocking");
        });
        h.runAfterDelay(1390, cleanup);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(EternalNightContent.isCity(player.level()),
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

    @GameTest(template = "empty", batch = "eternal_night")
    public static void cityDeathDropsAndRespawnsAtOwnBed(GameTestHelper h) {
        var session = login(h);
        var player = session.player();
        var server = h.getLevel().getServer();
        ServerLevel city = server.getLevel(EternalNightContent.LEVEL);
        boolean keepInventory = city.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        boolean reset = NightConfig.RESET_ON_DEATH.get();
        try {
            city.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(false, server);
            NightConfig.RESET_ON_DEATH.set(true);
            BlockPos bed = new BlockPos(-128, 90, -128);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                h.getLevel().setBlock(bed.offset(dx, -1, dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            var bedState = Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH);
            h.getLevel().setBlock(bed, bedState.setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
            h.getLevel().setBlock(bed.north(), bedState.setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
            player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, bed.north(), 0, false, false);
            BlockPos death = new BlockPos(4096, 120, 4096);
            city.setBlock(death.below(), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            player.teleportTo(city, death.getX() + 0.5, death.getY(), death.getZ() + 0.5, 0, 0);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
            CompoundTag data = new CompoundTag();
            data.putLongArray("Doors", new long[]{12, 34}); data.putBoolean("Visiting", true);
            player.getPersistentData().put(NightEvents.PLAYER_TAG, data);
            var dropArea = new net.minecraft.world.phys.AABB(death).inflate(3);
            int priorDrops = city.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, dropArea,
                    item -> item.getItem().is(net.minecraft.world.item.Items.DIAMOND))
                    .stream().mapToInt(item -> item.getItem().getCount()).sum();
            h.assertTrue(player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE) && !player.isAlive(), "real damage causes death");
            h.assertTrue(NightEvents.playerData(player).getBoolean("ReturnAfterDeath"), "death return marker is persisted before respawn");
            h.assertTrue(NightEvents.playerData(player).getLongArray("Doors").length == 0, "death clears prior door history");
            h.assertTrue(city.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    dropArea, item -> item.getItem().is(net.minecraft.world.item.Items.DIAMOND))
                    .stream().mapToInt(item -> item.getItem().getCount()).sum() == priorDrops + 7, "vanilla death drops inventory exactly once");
            var respawned = server.getPlayerList().respawn(player, false);
            h.assertTrue(respawned.serverLevel() == h.getLevel() && respawned.blockPosition().distSqr(bed) < 25,
                    "real respawn returns to the original overworld bed");
            h.assertTrue(NightEvents.playerData(respawned).isEmpty(), "respawn clears visit and death marker");
            h.assertTrue(respawned.getInventory().isEmpty(), "death return does not duplicate dropped items");
            h.succeed();
        } finally {
            city.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(keepInventory, server);
            NightConfig.RESET_ON_DEATH.set(reset);
            var current = server.getPlayerList().getPlayer(player.getUUID());
            if (current != null) server.getPlayerList().remove(current);
            session.channel().finishAndReleaseAll();
        }
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
}
