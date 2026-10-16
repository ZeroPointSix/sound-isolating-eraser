package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.yuji.YujiConfig;
import com.zeropointsix.eraser.yuji.flare.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("yuji_tests")
@PrefixGameTestTemplate(false)
public final class FlareVisualGameTests {
    private static final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
    private static final ArrayList<net.minecraft.world.entity.Entity> entities = new ArrayList<>();
    private static Consumer<BlockEvent.EntityPlaceEvent> guard;
    private static void block(ServerLevel level, BlockPos pos, BlockState state) {
        previous.putIfAbsent(pos.immutable(), level.getBlockState(pos)); level.setBlock(pos, state, 18);
    }
    private static BlockPos center(GameTestHelper h) {
        BlockPos reference = h.absolutePos(new BlockPos(4, 4, 4));
        return new BlockPos(reference.getX(), 200, reference.getZ());
    }
    private static void restore(ServerLevel level) {
        if (guard != null) { MinecraftForge.EVENT_BUS.unregister(guard); guard = null; }
        entities.forEach(net.minecraft.world.entity.Entity::discard); entities.clear();
        previous.forEach((pos, state) -> {
            if (level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate) crate.clearContent();
            level.setBlock(pos, state, 18);
        });
        previous.clear();
    }
    @AfterBatch(batch = "flare_visual_lease") public static void afterLease(ServerLevel l) { restore(l); }
    @AfterBatch(batch = "flare_visual_protection") public static void afterProtection(ServerLevel l) { restore(l); }
    @AfterBatch(batch = "flare_visual_landing") public static void afterLanding(ServerLevel l) { restore(l); }
    @AfterBatch(batch = "flare_visual_budget") public static void afterBudget(ServerLevel l) { restore(l); }
    @AfterBatch(batch = "flare_visual_low") public static void afterLow(ServerLevel l) { restore(l); }
    @AfterBatch(batch = "flare_visual_impact") public static void afterImpact(ServerLevel l) { restore(l); }
    private static long deadline(FlareLights data, BlockPos pos) {
        for (Tag tag : data.save(new CompoundTag()).getList("Lights", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) tag;
            if (record.getLong("Pos") == pos.asLong()) return record.getLong("Expires");
        }
        return -1;
    }
    private static boolean quiet(FlareLights data, BlockPos pos) {
        for (Tag tag : data.save(new CompoundTag()).getList("Lights", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) tag;
            if (record.getLong("Pos") == pos.asLong()) return record.getBoolean("Quiet");
        }
        return false;
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_visual_lease", timeoutTicks = 80)
    public static void flightLightIsRealDurableAndDoesNotPulseObserver(GameTestHelper h) {
        var level = h.getLevel(); BlockPos pos = center(h), observer = pos.west();
        block(level, pos, Blocks.AIR.defaultBlockState());
        block(level, observer, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.EAST));
        Player player = h.makeMockSurvivalPlayer(); FlareLights data = new FlareLights();
        h.runAfterDelay(6, () -> {
            h.assertTrue(data.flight(level, pos, player), "flight places a protected light in free midair");
            h.assertTrue(level.getBlockState(pos).getValue(LightBlock.LEVEL) == 15, "real level-15 light source");
            h.assertTrue(!level.getBlockTicks().hasScheduledTick(observer, Blocks.OBSERVER), "flight placement cannot pulse observers");
            FlareLights loaded = FlareLights.load(data.save(new CompoundTag()));
            h.assertTrue(deadline(loaded, pos) == deadline(data, pos), "short lease survives save/reload");
            h.runAfterDelay(3, () -> h.assertTrue(level.getBrightness(LightLayer.BLOCK, pos.east()) >= 12,
                    "moving flare illuminates real neighbouring blocks"));
            h.runAfterDelay(12, () -> {
                loaded.cleanup(level);
                h.assertTrue(loaded.size() == 0 && level.getBlockState(pos).isAir(), "expired loaded flight light is reclaimed");
                h.assertTrue(!level.getBlockTicks().hasScheduledTick(observer, Blocks.OBSERVER), "flight cleanup cannot pulse observers");
                System.out.println("T5_FLIGHT_LEASE_PASSED blockLight>=12 reload=true observerPulses=0 cleaned=true"); h.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_visual_protection", timeoutTicks = 100)
    public static void flightRespectsProtectionForeignLightsAndYellowLifetime(GameTestHelper h) {
        var level = h.getLevel(); BlockPos pos = center(h); Player player = h.makeMockSurvivalPlayer();
        FlareLights data = new FlareLights();
        block(level, pos, Blocks.AIR.defaultBlockState());
        block(level, pos.above(), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        block(level, pos.east(), Blocks.DIAMOND_BLOCK.defaultBlockState());
        BlockPos water = pos.south(3);
        for (Direction face : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
            block(level, water.relative(face), Blocks.STONE.defaultBlockState());
        block(level, water, Blocks.WATER.defaultBlockState());
        h.assertTrue(!data.flight(level, pos, null), "ownerless flare cannot place light");
        player.getAbilities().mayBuild = false;
        h.assertTrue(!data.flight(level, pos, player), "build permission is respected");
        player.getAbilities().mayBuild = true;
        h.assertTrue(!data.flight(level, pos.above(), player) && !data.flight(level, pos.east(), player)
                && !data.flight(level, water, player), "foreign light, solid block and water are untouched");
        guard = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, guard);
        h.assertTrue(!data.flight(level, pos, player) && data.size() == 0 && level.getBlockState(pos).isAir(),
                "cancelled flight placement rolls back without a lease");
        MinecraftForge.EVENT_BUS.unregister(guard); guard = null;
        h.assertTrue(data.flight(level, pos, player), "allowed flight source created");
        long original = deadline(data, pos);
        guard = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, guard);
        h.assertTrue(!data.flight(level, pos, player) && deadline(data, pos) == original, "cancelled renewal cannot extend lease");
        MinecraftForge.EVENT_BUS.unregister(guard); guard = null;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)))
            previous.putIfAbsent(p.immutable(), level.getBlockState(p));
        try (var config = GameTestConfigs.sandbox(YujiConfig.SPEC)) {
            FlareConfig.LIGHT_RADIUS.set(1); FlareConfig.YELLOW_LIFETIME.set(40); data.illuminate(level, pos, player);
        }
        long persistent = deadline(data, pos);
        h.assertTrue(persistent >= level.getGameTime() + 40 && persistent > original, "yellow upgrades the short lease");
        h.assertTrue(!quiet(data, pos), "yellow promotion removes the cosmetic-only marker");
        h.assertTrue(data.flight(level, pos, player) && deadline(data, pos) == persistent, "flight does not shorten persistent yellow light");

        BlockPos denied = new BlockPos(Math.floorDiv(pos.getX(), 3) * 3 + 10, 200, Math.floorDiv(pos.getZ(), 3) * 3 + 10);
        BlockPos recreated = pos.west(6);
        for (BlockPos c : new BlockPos[]{denied, recreated})
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1)))
                block(level, p, Blocks.AIR.defaultBlockState());
        h.assertTrue(data.flight(level, denied, player) && data.flight(level, recreated, player), "second flight sources created");
        long rejectedExpiry = deadline(data, denied);
        level.setBlock(recreated, Blocks.AIR.defaultBlockState(), 18);
        // Make the stale record the only eligible cell, rather than assuming a coverage choice.
        for (Direction face : Direction.values()) block(level, recreated.relative(face), Blocks.STONE.defaultBlockState());
        h.assertTrue(level.getBlockState(recreated).isAir() && quiet(data, recreated),
                "removed flight source retains a stale short record before replacement");
        guard = event -> { if (event.getPos().equals(denied)) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, guard);
        try (var config = GameTestConfigs.sandbox(YujiConfig.SPEC)) {
            FlareConfig.LIGHT_RADIUS.set(1); FlareConfig.YELLOW_LIFETIME.set(40);
            data.illuminate(level, denied, player); data.illuminate(level, recreated, player);
        } finally { MinecraftForge.EVENT_BUS.unregister(guard); guard = null; }
        h.assertTrue(deadline(data, denied) == rejectedExpiry, "rejected short source was not extended");
        h.assertTrue(level.getBlockState(recreated).is(Blocks.LIGHT) && !quiet(data, recreated)
                        && deadline(data, recreated) >= level.getGameTime() + 40,
                "replacing a stale short record with yellow clears its quiet marker");
        h.runAfterDelay(12, () -> {
            data.cleanup(level);
            h.assertTrue(level.getBlockState(pos).is(Blocks.LIGHT), "promoted source outlives short lease");
            h.assertTrue(level.getBlockState(denied).isAir() && level.getBrightness(LightLayer.BLOCK, denied.east()) >= 12,
                    "allowed neighbouring yellow sources fill coverage after rejected short source expires");
        });
        h.runAfterDelay(44, () -> {
            data.cleanup(level);
            h.assertTrue(data.size() == 0 && level.getBlockState(pos).isAir(), "persistent owned sources eventually expire");
            h.assertTrue(level.getBlockState(pos.above()).is(Blocks.LIGHT) && level.getBlockState(pos.east()).is(Blocks.DIAMOND_BLOCK)
                    && level.getBlockState(water).is(Blocks.WATER), "foreign states survive cleanup");
            System.out.println("T5_FLIGHT_PROTECTION_PASSED placement=true renewal=true foreign=true yellowPromotion=true"); h.succeed();
        });
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_visual_landing", timeoutTicks = 100)
    public static void flightCannotBlockAnAlreadyChosenCrateLanding(GameTestHelper h) {
        var level = h.getLevel(); BlockPos pos = center(h); Player player = h.makeMockSurvivalPlayer();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            block(level, pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 3; y++) block(level, pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        h.assertTrue(FlarePlacement.safeGround(level, pos), "chosen crate destination starts safe");
        var crate = new FallingSupplyCrate(FlareContent.FALLING_CRATE.get(), level);
        crate.prepare(pos, player.getUUID()); crate.setPos(Vec3.atBottomCenterOf(pos.above(3))); entities.add(crate);
        var lights = FlareLights.get(level);
        h.assertTrue(!lights.flight(level, pos, player) && !lights.flight(level, pos.above(), player),
                "another flare cannot occupy either landing clearance block");
        h.assertTrue(FlarePlacement.safeGround(level, pos), "chosen landing remains safe after crossing flare");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(pos), "crate chunk ready"))
                .thenExecute(() -> h.assertTrue(level.addFreshEntity(crate), "crate starts natural descent"))
                .thenWaitUntil(() -> h.assertTrue(level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity, "crate naturally lands"))
                .thenExecute(() -> {
                    var landed = (SupplyCrateBlockEntity) level.getBlockEntity(pos); landed.createMenu(1, player.getInventory(), player);
                    h.assertTrue(crate.isRemoved() && !landed.isEmpty(), "crossed landing still opens with real loot");
                    System.out.println("T5_FLIGHT_LANDING_PASSED clearance=2 naturallyLanded=true opened=true");
                }).thenSucceed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_visual_budget", timeoutTicks = 100)
    public static void actualFlyingProjectileHasBoundedLightsAndCleansAfterRemoval(GameTestHelper h) {
        var level = h.getLevel(); BlockPos pos = center(h); Player player = h.makeMockSurvivalPlayer();
        Map<Long, Integer> placements = new HashMap<>(); var sources = new ArrayList<BlockPos>();
        guard = event -> {
            if (event.getEntity() == player && event.getPlacedBlock().is(Blocks.LIGHT)) {
                placements.merge(level.getGameTime(), 1, Integer::sum); sources.add(event.getPos().immutable());
                previous.putIfAbsent(event.getPos().immutable(), Blocks.AIR.defaultBlockState());
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, guard);
        var flare = new FlareProjectile(FlareContent.FLARE.get(), level);
        flare.setOwner(player); flare.setColor(2); flare.setPos(Vec3.atCenterOf(pos)); flare.setDeltaMovement(.3, .8, 0); entities.add(flare);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(pos), "flight chunk ready")).thenExecute(() -> {
            h.assertTrue(level.addFreshEntity(flare), "real projectile enters server tick loop");
            h.runAfterDelay(12, () -> {
                h.assertTrue(!sources.isEmpty() && placements.values().stream().allMatch(n -> n <= 1)
                        && placements.size() <= 6, "one flight placement attempt per two ticks per projectile");
                flare.discard();
                h.runAfterDelay(24, () -> {
                    h.assertTrue(sources.stream().noneMatch(p -> level.getBlockState(p).is(Blocks.LIGHT)), "real scheduled cleanup reclaims stopped flight");
                    System.out.println("T5_FLIGHT_BUDGET_PASSED interval=2 maxPerTick=1 stoppedCleanup=true"); h.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_visual_low", timeoutTicks = 100)
    public static void lowHorizontalRedFlightLightsTerrainWithoutBlockingLanding(GameTestHelper h) {
        var level = h.getLevel(); BlockPos pos = center(h); Player player = h.makeMockSurvivalPlayer();
        for (int x = -1; x <= 8; x++) for (int z = -1; z <= 1; z++) {
            block(level, pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 3; y++) block(level, pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        var flare = new FlareProjectile(FlareContent.FLARE.get(), level);
        flare.setOwner(player); flare.setColor(0); flare.setPos(Vec3.atBottomCenterOf(pos).add(0, 1.2, 0));
        flare.setDeltaMovement(.5, 0, 0); entities.add(flare);
        // Drive actual projectile physics; no light is directly inserted by this test.
        for (int i = 0; i < 4; i++) flare.tick();
        BlockPos near = flare.blockPosition(), source = new BlockPos(near.getX(), pos.getY() + 2, near.getZ());
        h.assertTrue(!flare.isRemoved() && level.getBlockState(source).is(Blocks.LIGHT), "horizontal low red shot has a nearby real light");
        h.assertTrue(level.isEmptyBlock(source.below()) && level.isEmptyBlock(source.below(2)), "two landing clearance cells remain free");
        flare.discard();
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getBrightness(LightLayer.BLOCK, source.below()) >= 12,
                "low source propagates actual block light without relaxing brightness threshold"))
                .thenExecute(() -> System.out.println("T5_FLIGHT_LOW_PASSED horizontalRed=true clearance=2 propagatedLight>=12"))
                .thenSucceed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "flare_visual_impact", timeoutTicks = 40)
    public static void greenEarlyCollisionPersistsBurstWithoutPrematureActivation(GameTestHelper h) {
        var level = h.getLevel(); BlockPos pos = center(h);
        block(level, pos.below(), Blocks.STONE.defaultBlockState());
        block(level, pos, Blocks.AIR.defaultBlockState());
        var flare = new FlareProjectile(FlareContent.FLARE.get(), level);
        flare.setOwner(h.makeMockSurvivalPlayer()); flare.setColor(2);
        flare.setPos(Vec3.atBottomCenterOf(pos).add(0, .2, 0)); flare.setDeltaMovement(0, -.5, 0); entities.add(flare);
        flare.tick();
        CompoundTag tag = flare.saveWithoutId(new CompoundTag());
        h.assertTrue(tag.getBoolean("FlareImpactBurst") && !flare.isRemoved() && tag.getInt("FlareAge") < 80,
                "actual early collision emits feedback without activating the delayed airdrop");
        var restored = new FlareProjectile(FlareContent.FLARE.get(), level); restored.load(tag); entities.add(restored);
        restored.tick();
        h.assertTrue(restored.saveWithoutId(new CompoundTag()).getBoolean("FlareImpactBurst") && !restored.isRemoved(),
                "persisted impact latch prevents repeated collision bursts after reload");
        System.out.println("T5_FLIGHT_IMPACT_PASSED earlyCollision=true burstLatched=true reload=true delayedActivation=true");
        h.succeed();
    }
}
