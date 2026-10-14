package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.yuji.flare.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("yuji_tests")
@PrefixGameTestTemplate(false)
public final class FlareIntegrationGameTests {
    private static TestArea lightArea, dropArea;

    private static final class TestArea {
        final ServerLevel level;
        final BlockPos center;
        final Set<Long> chunks = new HashSet<>(), addedTickets = new HashSet<>();
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final List<BlockPos> lights = new ArrayList<>();
        TestArea(GameTestHelper h, BlockPos reference, int clearance) {
            this.level = h.getLevel();
            for (int x = (reference.getX() - 28) >> 4; x <= (reference.getX() + 28) >> 4; x++)
                for (int z = (reference.getZ() - 28) >> 4; z <= (reference.getZ() + 28) >> 4; z++) {
                    long key = ChunkPos.asLong(x, z); chunks.add(key);
                    if (!level.getForcedChunks().contains(key)) { level.setChunkForced(x, z, true); addedTickets.add(key); }
                    level.getChunk(x, z);
                }
            int surface = level.getMinBuildHeight();
            for (int x = -28; x <= 28; x++) for (int z = -28; z <= 28; z++)
                surface = Math.max(surface, level.getHeight(Heightmap.Types.MOTION_BLOCKING, reference.getX() + x, reference.getZ() + z));
            this.center = new BlockPos(reference.getX(), Math.max(reference.getY(), surface + clearance), reference.getZ());
            h.assertTrue(center.getY() + 45 < level.getMaxBuildHeight(), "fixture has headroom above actual terrain: " + center + ", surface=" + surface);
            System.out.println("FLARE_TEST_AREA center=" + center + " surface=" + surface + " clearance=" + clearance);
        }
        boolean ticking() {
            return chunks.stream().allMatch(key -> {
                ChunkPos chunk = new ChunkPos(key);
                return level.isPositionEntityTicking(new BlockPos(chunk.getMinBlockX() + 8, center.getY(), chunk.getMinBlockZ() + 8));
            });
        }
        void remember(BlockPos pos) { previous.putIfAbsent(pos.immutable(), level.getBlockState(pos)); }
        void close() {
            level.getEntitiesOfClass(FlareProjectile.class, new AABB(center).inflate(65)).forEach(FlareProjectile::discard);
            level.getEntitiesOfClass(FallingSupplyCrate.class, new AABB(center).inflate(65)).forEach(FallingSupplyCrate::discard);
            for (BlockPos pos : lights) if (level.getBlockState(pos).is(Blocks.LIGHT)) level.removeBlock(pos, false);
            previous.forEach((pos, state) -> {
                if (level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate) crate.clearContent();
                level.setBlock(pos, state, 16);
            });
            FlareLights.get(level).cleanup(level);
            for (long key : addedTickets) { ChunkPos chunk = new ChunkPos(key); level.setChunkForced(chunk.x, chunk.z, false); }
        }
    }
    @AfterBatch(batch = "flare_full_lighting")
    public static void restoreLightArea(ServerLevel level) { if (lightArea != null) { lightArea.close(); lightArea = null; } }
    @AfterBatch(batch = "flare_full_airdrop")
    public static void restoreDropArea(ServerLevel level) { if (dropArea != null) { dropArea.close(); dropArea = null; } }

    private static Player player(GameTestHelper h, BlockPos pos) {
        Player player = h.makeMockSurvivalPlayer();
        player.setPos(Vec3.atBottomCenterOf(pos));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlareContent.GUN.get()));
        return player;
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_full_lighting", timeoutTicks = 1500)
    public static void flareDefaultRadiusAndProductionCleanup(GameTestHelper h) {
        var level = h.getLevel();
        lightArea = new TestArea(h, h.absolutePos(new BlockPos(4, 70, 4)), 26);
        BlockPos center = lightArea.center;
        h.assertTrue(FlareConfig.LIGHT_RADIUS.get() == 24 && FlareConfig.YELLOW_LIFETIME.get() == 1200, "default radius and lifetime");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(lightArea.ticking(), "full light field ticking")).thenExecute(() -> {
        Player player = player(h, center);
        var flare = new FlareProjectile(FlareContent.FLARE.get(), level);
        flare.setColor(1); flare.setOwner(player); flare.setPos(Vec3.atCenterOf(center)); level.addFreshEntity(flare);
        flare.activate();
        var data = FlareLights.get(level);
        List<BlockPos> positions = lightArea.lights;
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
            h.assertTrue(checked == 57777, "full 24-block sphere was measured: " + checked + " positions at " + center);
        });
        h.runAfterDelay(1190, () -> {
            for (BlockPos pos : positions) h.assertTrue(level.getBlockState(pos).is(Blocks.LIGHT), "light survives until sixty seconds");
            h.assertTrue(!flare.isRemoved(), "illumination projectile does not expire early");
        });
        h.runAfterDelay(1215, () -> {
            // No manual cleanup: this verifies the registered LevelTickEvent path.
            for (BlockPos pos : positions) h.assertTrue(!level.getBlockState(pos).is(Blocks.LIGHT), "production timer removes owned source");
            h.assertTrue(flare.isRemoved(), "illumination projectile expires too"); h.succeed();
        });
        });
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_full_airdrop", timeoutTicks = 420)
    public static void flareGreenFlightRandomLandingAndLoot(GameTestHelper h) {
        var level = h.getLevel();
        // A separate high platform isolates the default 8-24 block search radius.
        dropArea = new TestArea(h, h.absolutePos(new BlockPos(4, 40, 4)), 2);
        BlockPos center = dropArea.center;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-27, -1, -27), center.offset(27, -1, 27))) {
            BlockPos pos = p.immutable(); dropArea.remember(pos); level.setBlock(pos, Blocks.STONE.defaultBlockState(), 16);
        }
        Player player = player(h, center); player.setXRot(80); player.setYRot(130);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FlareContent.GREEN.get(), 2));
        h.assertTrue(FlareConfig.GREEN_DELAY.get() == 80 && FlareConfig.DROP_MIN.get() == 8 && FlareConfig.DROP_MAX.get() == 24,
                "default flight deadline and landing annulus");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(dropArea.ticking(), "all possible landing chunks ready"))
                .thenExecute(() -> {
                    h.assertTrue(FlarePlacement.openSky(level, center), "fixture is above every surrounding surface: " + center);
                    FlareContent.GUN.get().use(level, player, InteractionHand.MAIN_HAND);
                    h.assertTrue(player.getOffhandItem().getCount() == 1, "outdoor green shot consumes one round");
                    h.runAfterDelay(79, () -> h.assertTrue(level.getEntitiesOfClass(FallingSupplyCrate.class,
                            new AABB(center).inflate(60)).isEmpty(), "airdrop not spawned before four seconds"));
                    h.runAfterDelay(85, () -> h.assertTrue(!level.getEntitiesOfClass(FallingSupplyCrate.class,
                            new AABB(center).inflate(60)).isEmpty(), "airdrop spawned just after eighty ticks"));
                }).thenWaitUntil(() -> h.assertTrue(!level.getEntitiesOfClass(FallingSupplyCrate.class,
                        new AABB(center).inflate(60)).isEmpty(), "green flight produces falling airdrop"))
                .thenExecute(() -> {
                    var crate = level.getEntitiesOfClass(FallingSupplyCrate.class, new AABB(center).inflate(60)).get(0);
                    double dx = crate.getX() - (center.getX() + .5), dz = crate.getZ() - (center.getZ() + .5);
                    h.assertTrue(dx * dx + dz * dz >= 64 && dx * dx + dz * dz <= 576, "random touchdown is within 8-24 horizontal blocks");
                    BlockPos touchdown = BlockPos.containing(crate.getX(), center.getY(), crate.getZ());
                    dropArea.remember(touchdown);
                    h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getBlockEntity(touchdown) instanceof SupplyCrateBlockEntity,
                            "actual descent creates container"))
                            .thenExecute(() -> {
                                var block = (SupplyCrateBlockEntity) level.getBlockEntity(touchdown);
                                h.assertTrue(block.saveWithoutMetadata().contains("LootTable"), "loot remains lazy at touchdown");
                                block.createMenu(1, player.getInventory(), player);
                                h.assertTrue(!block.isEmpty(), "first opening gives real weighted loot");
                                h.succeed();
                            });
                });
    }
    private FlareIntegrationGameTests() { }
}
