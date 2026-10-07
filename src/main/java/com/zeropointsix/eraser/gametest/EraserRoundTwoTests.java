package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.block.EraserAnchorBlock;
import com.zeropointsix.eraser.block.EraserWallBlock;
import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.eraser.*;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EraserRoundTwoTests {
    private static FakePlayer player(GameTestHelper h, BlockPos base, EraserMode mode) {
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "DrawQA"));
        p.setPos(Vec3.atCenterOf(base.above(8)));
        p.setYRot(180);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOUND_ISOLATING_ERASER.get()));
        mode.write(p.getMainHandItem());
        return p;
    }

    private static List<BlockPos> prepare(GameTestHelper h, BlockPos base, EraserMode mode) {
        var bases = mode.bases(base, Direction.NORTH);
        for (var pos : bases) h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        return bases;
    }

    private static InteractionResult click(FakePlayer p, BlockPos base) {
        return p.getMainHandItem().getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(base), Direction.UP, base.below(), false)));
    }

    private static void noWall(GameTestHelper h, List<BlockPos> bases) {
        for (var base : bases) for (int i = 0; i < 5; i++) {
            h.assertTrue(!EraserWallBlock.isEraserWall(h.getLevel().getBlockState(base.above(i))), "no partial drawing");
        }
    }

    @GameTest(template = "empty")
    public static void fiveShapesRotateAndHaveConnectedStrokes(GameTestHelper h) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (EraserMode mode : EraserMode.values()) {
                var bases = mode.bases(new BlockPos(-8, 64, -8), facing);
                int expected = mode == EraserMode.POINT ? 1 : mode == EraserMode.RING ? 12 : 5;
                h.assertTrue(bases.size() == expected && new HashSet<>(bases).size() == expected, "unique footprint size");
                for (var base : bases) {
                    h.assertTrue(base.getY() == 64, "all columns start at the selected surface height");
                    if (mode != EraserMode.POINT) h.assertTrue(EraserStroke.at(bases, base) != EraserStroke.POINT,
                            "every multi-column tile has connected chalk ports");
                }
            }
            var origin = new BlockPos(-8, 64, -8);
            h.assertTrue(EraserMode.ACROSS.bases(origin, facing).get(4).equals(origin.relative(facing.getClockWise(), 2)),
                    "across follows player right");
            h.assertTrue(EraserMode.FORWARD.bases(origin, facing).get(4).equals(origin.relative(facing, 2)),
                    "forward follows player facing");
        }
        ItemStack stack = new ItemStack(ModItems.SOUND_ISOLATING_ERASER.get());
        stack.getOrCreateTag().putInt("EraserDrawMode", 999);
        h.assertTrue(EraserMode.read(stack) == EraserMode.POINT, "invalid stored mode falls back safely");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyModePlacesFiveHighAndKeepsColumnCleanup(GameTestHelper h) {
        BlockPos base = h.absolutePos(new BlockPos(8, 2, 8));
        for (EraserMode mode : EraserMode.values()) {
            var bases = prepare(h, base, mode);
            var p = player(h, base, mode);
            h.assertTrue(click(p, base).consumesAction(), "mode must place: " + mode);
            h.assertTrue(p.getMainHandItem().getDamageValue() == bases.size(), "one durability per column");
            for (var pos : bases) for (int i = 0; i < 5; i++) {
                h.assertTrue(EraserWallBlock.isEraserWall(h.getLevel().getBlockState(pos.above(i))), "complete five-high columns");
            }
            h.getLevel().destroyBlock(bases.get(0).above(2), false);
            noWall(h, List.of(bases.get(0)));
            for (int i = 1; i < bases.size(); i++) {
                h.assertTrue(h.getLevel().getBlockState(bases.get(i)).getBlock() instanceof EraserAnchorBlock,
                        "breaking one column keeps the rest of the drawing");
                h.getLevel().destroyBlock(bases.get(i), false);
            }
            noWall(h, bases);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lastCellAndMissingSurfaceRejectWholeDrawing(GameTestHelper h) {
        BlockPos base = h.absolutePos(new BlockPos(8, 2, 8));
        var bases = prepare(h, base, EraserMode.RING);
        var p = player(h, base, EraserMode.RING);
        BlockPos last = bases.get(bases.size() - 1).above(4);
        for (var obstruction : List.of(Blocks.STONE, Blocks.WATER)) {
            h.getLevel().setBlockAndUpdate(last, obstruction.defaultBlockState());
            h.assertTrue(click(p, base) == InteractionResult.FAIL, "obstructed last cell rejects the whole ring");
            noWall(h, bases);
            h.assertTrue(p.getMainHandItem().getDamageValue() == 0, "failed drawing costs no durability");
            h.getLevel().removeBlock(last, false);
        }
        h.getLevel().removeBlock(bases.get(0).below(), false);
        h.assertTrue(click(p, base) == InteractionResult.FAIL, "unsupported chalk cannot float");
        noWall(h, bases);
        h.succeed();
    }

    public static final class CancelPlacement {
        private final FakePlayer player;
        int count;
        CancelPlacement(FakePlayer player) { this.player = player; }
        @SubscribeEvent public void cancel(BlockEvent.EntityMultiPlaceEvent event) {
            if (event.getEntity() == player) { count++; event.setCanceled(true); }
        }
    }

    @GameTest(template = "empty")
    public static void realItemStackPathRollsBackDoublePlantExactlyOnce(GameTestHelper h) {
        int height = CommonConfig.BARRIER_HEIGHT.get();
        CommonConfig.BARRIER_HEIGHT.set(2);
        try { assertWrappedPlantRollback(h); }
        finally { CommonConfig.BARRIER_HEIGHT.set(height); }
    }

    private static void assertWrappedPlantRollback(GameTestHelper h) {
        BlockPos base = h.absolutePos(new BlockPos(8, 2, 8));
        var bases = prepare(h, base, EraserMode.ACROSS);
        var p = player(h, base, EraserMode.ACROSS);
        var level = h.getLevel();
        BlockPos plant = bases.get(0);
        level.setBlockAndUpdate(plant.below(), Blocks.DIRT.defaultBlockState());
        var lower = Blocks.TALL_GRASS.defaultBlockState();
        var upper = lower.setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        level.setBlockAndUpdate(plant, lower);
        level.setBlockAndUpdate(plant.above(), upper);
        var listener = new CancelPlacement(p);
        MinecraftForge.EVENT_BUS.register(listener);
        try {
            var result = p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atBottomCenterOf(base), Direction.UP, base.below(), false)));
            h.assertTrue(result == InteractionResult.FAIL && listener.count == 1, "one cancellable placement event in real wrapper");
            noWall(h, bases);
            h.assertTrue(level.getBlockState(plant).equals(lower) && level.getBlockState(plant.above()).equals(upper),
                    "both original plant halves survive rollback");
            h.assertTrue(p.getMainHandItem().getDamageValue() == 0, "real wrapper preserves durability on cancel");
            h.assertTrue(!level.captureBlockSnapshots && !level.restoringBlockSnapshots, "capture flags restored");
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        var result = p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(base), Direction.UP, base.below(), false)));
        h.assertTrue(result.consumesAction() && p.getMainHandItem().getDamageValue() == 5,
                "real wrapper keeps successful cost and placed blocks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void protectionCancellationRestoresAllOriginalCells(GameTestHelper h) {
        BlockPos base = h.absolutePos(new BlockPos(8, 2, 8));
        var bases = prepare(h, base, EraserMode.ACROSS);
        var p = player(h, base, EraserMode.ACROSS);
        h.getLevel().setBlockAndUpdate(bases.get(0).below(), Blocks.DIRT.defaultBlockState());
        h.getLevel().setBlockAndUpdate(bases.get(0), Blocks.GRASS.defaultBlockState());
        var listener = new CancelPlacement(p);
        MinecraftForge.EVENT_BUS.register(listener);
        try {
            h.assertTrue(click(p, base) == InteractionResult.FAIL, "protection cancels the entire transaction");
            noWall(h, bases);
            h.assertTrue(h.getLevel().getBlockState(bases.get(0)).is(Blocks.GRASS), "replaceable plant is restored");
            h.assertTrue(p.getMainHandItem().getDamageValue() == 0, "protection rejection costs no durability");
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        h.assertTrue(click(p, base).consumesAction(), "listener cleanup allows a later placement");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void heldHandModesAreServerValidated(GameTestHelper h) {
        var p = player(h, h.absolutePos(new BlockPos(8, 2, 8)), EraserMode.POINT);
        h.assertTrue(!EraserNetwork.cycle(p, InteractionHand.OFF_HAND), "empty hand rejects mode packet");
        for (int i = 0; i < 5; i++) h.assertTrue(EraserNetwork.cycle(p, InteractionHand.MAIN_HAND), "held eraser cycles");
        h.assertTrue(EraserMode.read(p.getMainHandItem()) == EraserMode.POINT, "five cycles return to point");
        p.setItemInHand(InteractionHand.OFF_HAND, p.getMainHandItem().copy());
        h.assertTrue(EraserNetwork.cycle(p, InteractionHand.OFF_HAND), "offhand eraser cycles independently");
        h.assertTrue(EraserMode.read(p.getMainHandItem()) == EraserMode.POINT, "main hand mode is unchanged");
        h.assertTrue(EraserMode.read(p.getOffhandItem()) == EraserMode.ACROSS, "offhand mode persists in the stack");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aggroIsLocalAndReacquiresAfterBreakOrBypass(GameTestHelper h) {
        BlockPos base = h.absolutePos(new BlockPos(8, 2, 8));
        var p = player(h, base, EraserMode.POINT);
        prepare(h, base, EraserMode.POINT);
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 8));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        p.setPos(Vec3.atBottomCenterOf(base.east(2)));
        zombie.setTarget(p);
        h.assertTrue(zombie.getTarget() == p, "unblocked player can be targeted");
        h.assertTrue(click(p, base).consumesAction(), "wall can be raised between mob and player");
        var advanced = EntityType.VINDICATOR.create(h.getLevel());
        advanced.setPos(zombie.position());
        h.assertTrue(!EraserAggro.blocked(advanced, p) && advanced.hasLineOfSight(p), "advanced mobs are not isolated");
        h.assertTrue(!EntityType.WITHER.is(EraserAggro.ISOLATED) && !EntityType.ENDER_DRAGON.is(EraserAggro.ISOLATED),
                "bosses excluded by default");
        h.runAtTickTime(8, () -> {
            h.assertTrue(zombie.getTarget() == null, "existing player target is lost within five ticks");
            zombie.setTarget(p);
            h.assertTrue(zombie.getTarget() == null, "target acquisition through wall is blocked");
            p.setPos(Vec3.atBottomCenterOf(base.east(2).south(4)));
            zombie.setTarget(p);
            h.assertTrue(zombie.getTarget() == p, "bypassing wall permits target acquisition");
            p.setPos(Vec3.atBottomCenterOf(base.east(2)));
            h.getLevel().destroyBlock(base.above(2), false);
            h.assertTrue(!EraserAggro.blocked(zombie, p) && zombie.hasLineOfSight(p), "destroyed wall restores sensing");
            zombie.setTarget(p);
            h.assertTrue(zombie.getTarget() == p, "destroyed wall permits reacquisition");
            h.succeed();
        });
    }
}
