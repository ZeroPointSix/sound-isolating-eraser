package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.block.EraserAnchorBlock;
import com.zeropointsix.eraser.block.EraserBarrierBlock;
import com.zeropointsix.eraser.block.EraserWallBlock;
import com.zeropointsix.eraser.registry.ModBlocks;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EraserGameTests {
    private EraserGameTests() {
    }

    private static FakePlayer playerAt(GameTestHelper h, Vec3 pos) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(),
                new GameProfile(UUID.randomUUID(), "EraserQA"));
        player.setPos(pos);
        return player;
    }

    private static FakePlayer armed(GameTestHelper h, Vec3 pos) {
        FakePlayer player = playerAt(h, pos);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.SOUND_ISOLATING_ERASER.get()));
        return player;
    }

    private static BlockHitResult faceHit(BlockPos pos, Direction face) {
        Vec3 target = Vec3.atCenterOf(pos).add(
                face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return new BlockHitResult(target, face, pos, false);
    }

    private static InteractionResult click(FakePlayer player, BlockPos pos, Direction face) {
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                faceHit(pos, face));
        return ModItems.SOUND_ISOLATING_ERASER.get().useOn(context);
    }

    private static void assertColumn(GameTestHelper h, BlockPos base, Direction markFace) {
        ServerLevel level = h.getLevel();
        BlockState anchor = level.getBlockState(base);
        h.assertTrue(anchor.is(ModBlocks.ERASER_ANCHOR.get()),
                "anchor cell must be the eraser anchor");
        h.assertTrue(anchor.getValue(EraserAnchorBlock.FACING) == markFace,
                "anchor must keep the clicked-face direction");
        for (int i = 1; i < ModMain.MAX_HEIGHT; i++) {
            BlockState cell = level.getBlockState(base.above(i));
            h.assertTrue(cell.is(ModBlocks.ERASER_BARRIER.get()),
                    "cell +" + i + " must be an eraser barrier");
            h.assertTrue(cell.getValue(EraserBarrierBlock.LEVEL) == i,
                    "cell +" + i + " must record level " + i);
        }
    }

    private static void assertColumnGone(GameTestHelper h, BlockPos base) {
        ServerLevel level = h.getLevel();
        for (int i = 0; i < ModMain.MAX_HEIGHT; i++) {
            h.assertTrue(!EraserWallBlock.isEraserWall(level.getBlockState(base.above(i))),
                    "cell +" + i + " must be cleared");
        }
    }

    private static void raiseColumn(GameTestHelper h, BlockPos floor) {
        FakePlayer player = armed(h, Vec3.atCenterOf(floor.above(3)));
        h.assertTrue(click(player, floor, Direction.UP).consumesAction(),
                "clicking clear ground must raise the column");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void groundClickRaisesColumn(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        FakePlayer player = armed(h, Vec3.atCenterOf(floor.above(3)));
        ItemStack stack = player.getMainHandItem();
        h.assertTrue(click(player, floor, Direction.UP).consumesAction(),
                "ground click must succeed");
        assertColumn(h, floor.above(), Direction.DOWN);
        h.assertTrue(stack.getDamageValue() == 1,
                "a successful column must consume one durability point");
        h.assertTrue(!level.getBlockState(floor.above())
                .getCollisionShape(level, floor.above()).isEmpty(),
                "the wall must have a full collision shape");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sideClickMarksWallSide(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos wall = h.absolutePos(new BlockPos(5, 1, 5));
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        FakePlayer player = armed(h, Vec3.atCenterOf(wall.north(2)));
        h.assertTrue(click(player, wall, Direction.NORTH).consumesAction(),
                "wall click must succeed");
        assertColumn(h, wall.north(), Direction.SOUTH);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void downFaceClickFails(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos ceiling = h.absolutePos(new BlockPos(8, 6, 8));
        level.setBlockAndUpdate(ceiling, Blocks.STONE.defaultBlockState());
        FakePlayer player = armed(h, Vec3.atCenterOf(ceiling.below(2)));
        h.assertTrue(click(player, ceiling, Direction.DOWN) == InteractionResult.FAIL,
                "clicking a block's underside must fail");
        h.assertTrue(level.getBlockState(ceiling.below()).isAir(),
                "failed placement must leave the world untouched");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blockedCellFailsAtomically(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(11, 1, 2));
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(floor.above(3), Blocks.STONE.defaultBlockState());
        FakePlayer player = armed(h, Vec3.atCenterOf(floor.above(6)));
        ItemStack stack = player.getMainHandItem();
        h.assertTrue(click(player, floor, Direction.UP) == InteractionResult.FAIL,
                "an obstructed cell must fail the whole placement");
        for (int i = 1; i <= ModMain.MAX_HEIGHT; i++) {
            if (i == 3) {
                continue;
            }
            h.assertTrue(level.getBlockState(floor.above(i)).isAir(),
                    "cell +" + i + " must stay empty after atomic failure");
        }
        h.assertTrue(stack.getDamageValue() == 0,
                "a failed placement must not consume durability");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void occupantBlocksPlacement(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(14, 1, 2));
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        Zombie squatter = h.spawn(EntityType.ZOMBIE, new BlockPos(14, 2, 2));
        squatter.setNoAi(true);
        FakePlayer player = armed(h, Vec3.atCenterOf(floor.above(6)));
        h.assertTrue(click(player, floor, Direction.UP) == InteractionResult.FAIL,
                "a living entity inside the column must fail placement");
        h.assertTrue(level.getBlockState(floor.above()).isAir(),
                "failed placement must not wall the entity in");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void breakingBarrierKillsColumn(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(2, 1, 5));
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        raiseColumn(h, floor);
        level.destroyBlock(floor.above(3), true);
        assertColumnGone(h, floor.above());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void breakingAnchorKillsColumn(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(5, 1, 5));
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        raiseColumn(h, floor);
        level.destroyBlock(floor.above(), true);
        assertColumnGone(h, floor.above());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void adjacentColumnsAreIndependent(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos floorA = h.absolutePos(new BlockPos(8, 1, 5));
        BlockPos floorB = floorA.east();
        level.setBlockAndUpdate(floorA, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(floorB, Blocks.STONE.defaultBlockState());
        raiseColumn(h, floorA);
        raiseColumn(h, floorB);
        level.destroyBlock(floorA.above(2), true);
        assertColumnGone(h, floorA.above());
        assertColumn(h, floorB.above(), Direction.DOWN);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void explosionKillsColumn(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(11, 1, 5));
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        raiseColumn(h, floor);
        Vec3 center = Vec3.atCenterOf(floor.above(3));
        level.explode(null, level.damageSources().explosion(null, null), null,
                center.x, center.y, center.z, 2.5F, false, Level.ExplosionInteraction.BLOCK);
        assertColumnGone(h, floor.above());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void orphanBarrierSelfDestructs(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos rogue = h.absolutePos(new BlockPos(14, 4, 5));
        level.setBlock(rogue, ModBlocks.ERASER_BARRIER.get().defaultBlockState()
                .setValue(EraserBarrierBlock.LEVEL, 1), Block.UPDATE_ALL);
        h.assertTrue(level.getBlockState(rogue.below()).isAir(),
                "precondition: no anchor below the orphan cell");
        h.runAfterDelay(20, () -> {
            h.assertTrue(level.getBlockState(rogue).isAir(),
                    "an unanchored barrier must destroy itself within a few ticks");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wallBlocksFluidsAndPistons(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(2, 1, 8));
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        raiseColumn(h, floor);
        for (int i = 1; i <= ModMain.MAX_HEIGHT; i++) {
            BlockState cell = level.getBlockState(floor.above(i));
            h.assertTrue(cell.getFluidState().isEmpty(), "wall cells must not be waterlogged");
            h.assertTrue(!cell.canBeReplaced(Fluids.WATER),
                    "fluids must not be able to flow into cell +" + i);
            h.assertTrue(cell.getPistonPushReaction() == PushReaction.BLOCK,
                    "pistons must not move cell +" + i);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wallDoesNotDropItems(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(5, 1, 8));
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        raiseColumn(h, floor);
        level.destroyBlock(floor.above(), true);
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,
                new AABB(floor.above()).inflate(1.0)).isEmpty(),
                "wall cells must not drop items");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void mobSeesThroughButCannotCross(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 6; x <= 8; x++) {
            for (int z = 4; z <= 8; z++) {
                level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 1, z)),
                        Blocks.STONE.defaultBlockState());
            }
        }
        for (int z = 4; z <= 8; z++) {
            raiseColumn(h, h.absolutePos(new BlockPos(7, 1, z)));
        }
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
        FakePlayer bait = playerAt(h,
                Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 2, 6))));
        h.assertTrue(zombie.getSensing().hasLineOfSight(bait),
                "the transparent wall must not block mob line of sight");
        h.assertTrue(!level.getBlockState(h.absolutePos(new BlockPos(7, 3, 6)))
                .getCollisionShape(level, h.absolutePos(new BlockPos(7, 3, 6))).isEmpty(),
                "the wall still collides like a solid block");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void ceilingHeightFailsCleanly(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos rel = new BlockPos(10, 1, 10);
        BlockPos floor = new BlockPos(h.absolutePos(rel).getX(),
                level.getMaxBuildHeight() - 2, h.absolutePos(rel).getZ());
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        FakePlayer player = armed(h, Vec3.atCenterOf(floor));
        h.assertTrue(click(player, floor, Direction.UP) == InteractionResult.FAIL,
                "columns that would exceed the world ceiling must fail");
        h.assertTrue(level.getBlockState(floor.above()).isAir(),
                "failed placement must leave cells empty");
        h.succeed();
    }
}
