package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.fertilizer.*;
import com.zeropointsix.eraser.yuji.city.RainCityContent;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** A player-built sapling on any vanilla dirt-tag soil grows a fertile tree, in the overworld and in the city. */
@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FertilizerSoilGameTests {
    static InteractionResult grow(ServerLevel level, BlockPos sapling, Block soil, Block saplingBlock) {
        level.getChunk(sapling);
        for (BlockPos p : BlockPos.betweenClosed(sapling.offset(-6, 0, -6), sapling.offset(6, 24, 6)))
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos p : BlockPos.betweenClosed(sapling.offset(-6, -10, -6), sapling.offset(6, -1, 6)))
            level.setBlock(p, (p.getY() == sapling.getY() - 1 ? soil : Blocks.DIRT).defaultBlockState(), 2);
        level.setBlock(sapling, saplingBlock.defaultBlockState(), 2);
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SoilQA"));
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(Vec3.atCenterOf(sapling.offset(4, 0, 4)));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FertilizerContent.BAG.get()));
        return p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(sapling), Direction.UP, sapling, false)));
    }

    static void check(GameTestHelper h, ServerLevel level, BlockPos sapling, Block soil, String where) {
        InteractionResult result = grow(level, sapling, soil, Blocks.ACACIA_SAPLING);
        h.assertTrue(result.consumesAction() && level.getBlockState(sapling).is(FertilizerContent.LOG.get()),
                "acacia sapling on " + soil + " in " + where + " grows: result=" + result
                        + " root=" + level.getBlockState(sapling));
    }

    @GameTest(template = "empty", batch = "fertilizer_soil_overworld")
    public static void acaciaOnGrassAndMyceliumInOverworld(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        check(h, level, h.absolutePos(new BlockPos(1, 30, 1)).offset(400, 0, 0), Blocks.GRASS_BLOCK, "overworld");
        BlockPos mycelium = h.absolutePos(new BlockPos(1, 30, 1)).offset(440, 0, 0);
        check(h, level, mycelium, Blocks.MYCELIUM, "overworld");
        h.assertTrue(level.getBlockState(mycelium.below()).is(Blocks.ROOTED_DIRT), "trunk roots into the former mycelium");
        check(h, level, h.absolutePos(new BlockPos(1, 30, 1)).offset(480, 0, 0), Blocks.PODZOL, "overworld");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "fertilizer_soil_overworld")
    public static void failuresExplainThemselves(GameTestHelper h) {
        String key = "message.sound_isolating_eraser.fertilizer.";
        h.assertTrue(FertilizerBagItem.failure(Blocks.STONE.defaultBlockState()).equals(key + "invalid_target"), "stone");
        h.assertTrue(FertilizerBagItem.failure(Blocks.ACACIA_SAPLING.defaultBlockState()).equals(key + "no_room"), "sapling");
        h.assertTrue(FertilizerBagItem.failure(((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT)
                .getStateForAge(7)).equals(key + "mature"), "ripe wheat");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "fertilizer_soil_city")
    public static void acaciaOnGrassAndMyceliumInRainCity(GameTestHelper h) {
        ServerLevel city = h.getLevel().getServer().getLevel(RainCityContent.LEVEL);
        h.assertTrue(city != null, "city dimension is loaded");
        check(h, city, new BlockPos(600, 80, 600), Blocks.GRASS_BLOCK, "yuji_fodu");
        check(h, city, new BlockPos(640, 80, 600), Blocks.MYCELIUM, "yuji_fodu");
        h.succeed();
    }
}
