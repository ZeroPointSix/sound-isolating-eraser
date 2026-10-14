package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.yuji.flare.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("yuji_tests")
@PrefixGameTestTemplate(false)
public final class FlareIntegrationGameTests {
    private static Player player(GameTestHelper h, BlockPos pos) {
        Player player = h.makeMockSurvivalPlayer();
        player.setPos(Vec3.atBottomCenterOf(pos));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlareContent.GUN.get()));
        return player;
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_full_lighting", timeoutTicks = 1300)
    public static void flareDefaultRadiusAndProductionCleanup(GameTestHelper h) {
        var level = h.getLevel(); BlockPos center = h.absolutePos(new BlockPos(4, 70, 4));
        for (int x = -32; x <= 32; x += 16) for (int z = -32; z <= 32; z += 16) level.getChunk(center.offset(x, 0, z));
        h.assertTrue(FlareConfig.LIGHT_RADIUS.get() == 24 && FlareConfig.YELLOW_LIFETIME.get() == 1200, "default radius and lifetime");
        Player player = player(h, center);
        var flare = new FlareProjectile(FlareContent.FLARE.get(), level);
        flare.setColor(1); flare.setOwner(player); flare.setPos(Vec3.atCenterOf(center)); level.addFreshEntity(flare);
        flare.activate();
        var data = FlareLights.get(level);
        List<BlockPos> positions = new ArrayList<>();
        for (Tag tag : data.save(new CompoundTag()).getList("Lights", Tag.TAG_COMPOUND)) {
            BlockPos pos = BlockPos.of(((CompoundTag) tag).getLong("Pos"));
            if (pos.distSqr(center) <= 24 * 24) positions.add(pos);
        }
        h.assertTrue(!positions.isEmpty() && flare.lit(), "production projectile creates durable light field");
        h.runAfterDelay(20, () -> {
            int checked = 0;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-24, -24, -24), center.offset(24, 24, 24))) {
                if (pos.distSqr(center) > 24 * 24) continue;
                if (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(Blocks.LIGHT)) {
                    h.assertTrue(level.getBrightness(LightLayer.BLOCK, pos) >= 12, "default-radius block light at " + pos);
                    checked++;
                }
            }
            h.assertTrue(checked > 50000, "full 24-block sphere was measured");
        });
        h.runAfterDelay(1215, () -> {
            // No manual cleanup: this verifies the registered LevelTickEvent path.
            for (BlockPos pos : positions) h.assertTrue(!level.getBlockState(pos).is(Blocks.LIGHT), "production timer removes owned source");
            h.assertTrue(flare.isRemoved(), "illumination projectile expires too"); h.succeed();
        });
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_full_airdrop", timeoutTicks = 420)
    public static void flareGreenFlightRandomLandingAndLoot(GameTestHelper h) {
        var level = h.getLevel(); BlockPos center = h.absolutePos(new BlockPos(4, 40, 4));
        // A separate high platform isolates the default 8-24 block search radius.
        List<BlockPos> floor = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-27, -1, -27), center.offset(27, -1, 27))) {
            BlockPos pos = p.immutable(); level.getChunk(pos); level.setBlock(pos, Blocks.STONE.defaultBlockState(), 16); floor.add(pos);
        }
        Player player = player(h, center); player.setXRot(80); player.setYRot(130);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FlareContent.GREEN.get(), 2));
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(center), "firing chunk ready"))
                .thenExecute(() -> {
                    FlareContent.GUN.get().use(level, player, InteractionHand.MAIN_HAND);
                    h.assertTrue(player.getOffhandItem().getCount() == 1, "outdoor green shot consumes one round");
                    h.runAfterDelay(70, () -> h.assertTrue(level.getEntitiesOfClass(FallingSupplyCrate.class,
                            new AABB(center).inflate(60)).isEmpty(), "airdrop not spawned before four seconds"));
                }).thenWaitUntil(() -> h.assertTrue(!level.getEntitiesOfClass(FallingSupplyCrate.class,
                        new AABB(center).inflate(60)).isEmpty(), "green flight produces falling airdrop"))
                .thenExecute(() -> {
                    var crate = level.getEntitiesOfClass(FallingSupplyCrate.class, new AABB(center).inflate(60)).get(0);
                    double dx = crate.getX() - (center.getX() + .5), dz = crate.getZ() - (center.getZ() + .5);
                    h.assertTrue(dx * dx + dz * dz >= 64 && dx * dx + dz * dz <= 576, "random touchdown is within 8-24 horizontal blocks");
                    BlockPos touchdown = BlockPos.containing(crate.getX(), center.getY(), crate.getZ());
                    h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getBlockEntity(touchdown) instanceof SupplyCrateBlockEntity,
                            "actual descent creates container"))
                            .thenExecute(() -> {
                                var block = (SupplyCrateBlockEntity) level.getBlockEntity(touchdown);
                                h.assertTrue(block.saveWithoutMetadata().contains("LootTable"), "loot remains lazy at touchdown");
                                block.createMenu(1, player.getInventory(), player);
                                h.assertTrue(!block.isEmpty(), "first opening gives real weighted loot");
                                block.clearContent(); level.removeBlock(touchdown, false);
                                for (BlockPos pos : floor) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 16);
                                h.succeed();
                            });
                });
    }
    private FlareIntegrationGameTests() { }
}
