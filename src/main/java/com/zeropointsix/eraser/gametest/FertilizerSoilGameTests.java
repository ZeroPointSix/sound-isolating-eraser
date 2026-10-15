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
    // Fixtures stay inside a 7x7 column around the sapling and are cleared afterwards, so they
    // never touch neighbouring GameTest structures.
    static void prepare(ServerLevel level, BlockPos sapling, Block soil) {
        level.getChunk(sapling);
        for (BlockPos p : BlockPos.betweenClosed(sapling.offset(-3, 0, -3), sapling.offset(3, 9, 3)))
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos p : BlockPos.betweenClosed(sapling.offset(-3, -2, -3), sapling.offset(3, -1, 3)))
            level.setBlock(p, (p.getY() == sapling.getY() - 1 ? soil : Blocks.DIRT).defaultBlockState(), 2);
        level.setBlock(sapling, Blocks.ACACIA_SAPLING.defaultBlockState(), 2);
    }

    static void clear(ServerLevel level, BlockPos sapling) {
        for (BlockPos p : BlockPos.betweenClosed(sapling.offset(-3, -2, -3), sapling.offset(3, 9, 3)))
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
    }

    static InteractionResult use(ServerLevel level, BlockPos sapling) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SoilQA"));
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(Vec3.atCenterOf(sapling.offset(3, 0, 3)));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FertilizerContent.BAG.get()));
        return p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(sapling), Direction.UP, sapling, false)));
    }

    static void check(GameTestHelper h, ServerLevel level, BlockPos sapling, Block soil, String where) {
        prepare(level, sapling, soil);
        InteractionResult result = use(level, sapling);
        boolean grew = level.getBlockState(sapling).is(FertilizerContent.LOG.get());
        boolean rooted = level.getBlockState(sapling.below()).is(Blocks.ROOTED_DIRT);
        String root = level.getBlockState(sapling).toString();
        clear(level, sapling);
        h.assertTrue(result.consumesAction() && grew && rooted,
                "acacia sapling on " + soil + " in " + where + " grows: result=" + result + " root=" + root);
    }

    private static BlockPos local(GameTestHelper h) { return h.absolutePos(new BlockPos(5, 3, 5)); }

    @GameTest(template = "empty", batch = "fertilizer_soil_grass")
    public static void acaciaOnGrassInOverworld(GameTestHelper h) {
        check(h, h.getLevel(), local(h), Blocks.GRASS_BLOCK, "overworld"); h.succeed();
    }

    @GameTest(template = "empty", batch = "fertilizer_soil_mycelium")
    public static void acaciaOnMyceliumInOverworld(GameTestHelper h) {
        check(h, h.getLevel(), local(h), Blocks.MYCELIUM, "overworld"); h.succeed();
    }

    @GameTest(template = "empty", batch = "fertilizer_soil_podzol")
    public static void acaciaOnPodzolInOverworld(GameTestHelper h) {
        check(h, h.getLevel(), local(h), Blocks.PODZOL, "overworld"); h.succeed();
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
        // Far outside the generated city; the fixture is cleared again afterwards.
        check(h, city, new BlockPos(3000, 120, 3000), Blocks.GRASS_BLOCK, "yuji_fodu");
        check(h, city, new BlockPos(3020, 120, 3000), Blocks.MYCELIUM, "yuji_fodu");
        h.succeed();
    }
}
