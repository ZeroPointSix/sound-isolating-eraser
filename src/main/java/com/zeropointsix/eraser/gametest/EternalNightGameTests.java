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

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EternalNightGameTests {
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
