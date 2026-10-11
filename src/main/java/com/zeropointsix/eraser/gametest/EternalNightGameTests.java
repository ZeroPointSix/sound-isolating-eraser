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
    @GameTest(template = "empty", batch = "eternal_night", timeoutTicks = 200)
    public static void cityRealPlayerDoorInteractions(GameTestHelper h) {
        ServerLevel city = h.getLevel().getServer().getLevel(EternalNightContent.LEVEL);
        net.minecraft.server.level.ServerPlayer player = h.makeMockServerPlayerInLevel();
        io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel(player.connection.connection);
        player.connection.connection.setProtocol(net.minecraft.network.ConnectionProtocol.PLAY);
        BlockPos first = new BlockPos(2048, 120, 2048);
        try {
            player.teleportTo(city, first.getX() + 0.5, first.getY(), first.getZ() - 2, 0, 0);
            player.getPersistentData().remove(NightEvents.PLAYER_TAG);
            installDoor(city, first, Blocks.OAK_DOOR);
            installDoor(city, first.east(3), Blocks.BIRCH_DOOR);
            use(player, first.above());
            use(player, first);
            use(player, first);
            use(player, first.east(3));
            h.assertTrue(NightEvents.playerData(player).getLongArray("Doors").length == 2,
                    "actual upper/lower/repeated interactions and two doors in one tick count exactly twice");

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
