package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiConfig;
import com.zeropointsix.eraser.yuji.flare.FallingSupplyCrate;
import com.zeropointsix.eraser.yuji.flare.FlareConfig;
import com.zeropointsix.eraser.yuji.flare.FlareContent;
import com.zeropointsix.eraser.yuji.flare.FlarePlacement;
import com.zeropointsix.eraser.yuji.flare.FlareProjectile;
import com.zeropointsix.eraser.yuji.flare.SupplyCrateBlockEntity;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("flare_green_tests")
@PrefixGameTestTemplate(false)
public final class FlareGreenGameTests {
    private static Fixture fixture;

    private static final class Fixture {
        final ServerLevel level;
        final BlockPos center = new BlockPos(9600, 240, 9600);
        final AABB bounds = new AABB(center).inflate(60);
        final Set<Long> chunks = new HashSet<>(), forced = new HashSet<>();
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final GameTestConfigs.LiveConfig config = GameTestConfigs.sandbox(YujiConfig.SPEC);
        final ServerPlayer player;
        Consumer<BlockEvent.EntityPlaceEvent> guard;

        Fixture(GameTestHelper h) {
            level = h.getLevel();
            for (int x = (center.getX() - 28) >> 4; x <= (center.getX() + 28) >> 4; x++)
                for (int z = (center.getZ() - 28) >> 4; z <= (center.getZ() + 28) >> 4; z++) {
                    ChunkPos chunk = new ChunkPos(x, z);
                    long key = chunk.toLong(); chunks.add(key);
                    if (!level.getForcedChunks().contains(key)) {
                        level.setChunkForced(x, z, true); forced.add(key);
                    }
                    level.getChunkSource().addRegionTicket(TicketType.PLAYER, chunk, 2, chunk);
                    level.getChunk(x, z);
                }
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-27, -1, -27), center.offset(27, -1, 27)))
                set(p, Blocks.STONE.defaultBlockState());
            var profile = new GameProfile(UUID.randomUUID(), "GreenFlareTest");
            player = new ServerPlayer(level.getServer(), level, profile);
            player.connection = new FakePlayer(level, profile).connection;
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(Vec3.atBottomCenterOf(center));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlareContent.GUN.get()));
            level.addNewPlayer(player);
            // The headless player's connection only sinks packets; advance its vanilla
            // cooldown clock once per server test tick, as the network-driven player tick would.
            h.onEachTick(() -> { if (!player.isRemoved()) player.getCooldowns().tick(); });
        }

        boolean ticking() {
            return chunks.stream().allMatch(key -> {
                ChunkPos c = new ChunkPos(key);
                return level.isPositionEntityTicking(new BlockPos(c.getMinBlockX() + 8, center.getY(), c.getMinBlockZ() + 8));
            });
        }

        void set(BlockPos pos, BlockState state) {
            previous.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, 16);
        }

        void close() {
            if (guard != null) MinecraftForge.EVENT_BUS.unregister(guard);
            level.getEntitiesOfClass(FlareProjectile.class, bounds).forEach(Entity::discard);
            level.getEntitiesOfClass(FallingSupplyCrate.class, bounds).forEach(Entity::discard);
            player.discard();
            previous.forEach((pos, state) -> {
                if (level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate) crate.clearContent();
                level.setBlock(pos, state, 16);
            });
            for (long key : chunks) {
                ChunkPos c = new ChunkPos(key);
                level.getChunkSource().removeRegionTicket(TicketType.PLAYER, c, 2, c);
                if (forced.contains(key)) level.setChunkForced(c.x, c.z, false);
            }
            config.close();
        }
    }

    private static void close() { if (fixture != null) { fixture.close(); fixture = null; } }
    @AfterBatch(batch = "flare_green_repeat") public static void afterRepeat(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_green_sparse") public static void afterSparse(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_green_collision") public static void afterCollision(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_green_protection") public static void afterProtection(ServerLevel level) { close(); }

    private static void ready(GameTestHelper h, Runnable action) {
        fixture = new Fixture(h);
        h.assertTrue(FlareConfig.GREEN_DELAY.get() == 80 && FlareConfig.DROP_MIN.get() == 8
                && FlareConfig.DROP_MAX.get() == 24 && FlareConfig.DROP_ATTEMPTS.get() == 48,
                "green regressions use production flight/search defaults");
        h.startSequence().thenWaitUntil(() -> {
            if (!fixture.ticking()) {
                long deadline = System.nanoTime() + 50_000_000L;
                fixture.level.getServer().managedBlock(() -> fixture.ticking() || System.nanoTime() >= deadline);
            }
            h.assertTrue(fixture.ticking(), "complete landing annulus entity-ticking");
        }).thenExecute(() -> {
            h.assertTrue(FlarePlacement.openSky(fixture.level, fixture.center), "fixture is open terrain");
            action.run();
        });
    }

    private static FlareProjectile shoot(GameTestHelper h) {
        Fixture f = fixture;
        f.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FlareContent.GREEN.get(), 2));
        h.assertTrue(!f.player.getCooldowns().isOnCooldown(FlareContent.GUN.get()), "real player cooldown has expired");
        FlareContent.GUN.get().use(f.level, f.player, InteractionHand.MAIN_HAND);
        h.assertTrue(f.player.getOffhandItem().getCount() == 1, "one outdoor green round consumed");
        var flares = f.level.getEntitiesOfClass(FlareProjectile.class, f.bounds);
        h.assertTrue(flares.size() == 1, "one live projectile per shot");
        return flares.get(0);
    }

    private static void awaitDrop(GameTestHelper h, FlareProjectile flare, Runnable complete) {
        Fixture f = fixture;
        long firedAt = f.level.getGameTime();
        boolean[] landed = {false};
        h.runAfterDelay(79, () -> {
            h.assertTrue(!flare.isRemoved(), "green projectile survives until the 80 tick deadline");
            h.assertTrue(f.level.getEntitiesOfClass(FallingSupplyCrate.class, f.bounds).isEmpty(), "no early airdrop");
        });
        h.runAfterDelay(85, () -> {
            var crates = f.level.getEntitiesOfClass(FallingSupplyCrate.class, f.bounds);
            h.assertTrue(flare.isRemoved() && crates.size() == 1, "exactly one falling crate after green activation");
            FallingSupplyCrate falling = crates.get(0);
            BlockPos touchdown = BlockPos.containing(falling.getX(), f.center.getY(), falling.getZ());
            double dx = falling.getX() - (f.center.getX() + .5), dz = falling.getZ() - (f.center.getZ() + .5);
            h.assertTrue(dx * dx + dz * dz >= 64 && dx * dx + dz * dz <= 576, "landing remains in the 8-24 block annulus");
            f.previous.putIfAbsent(touchdown, f.level.getBlockState(touchdown));
            h.startSequence().thenWaitUntil(() -> {
                h.assertTrue(f.level.getGameTime() - firedAt <= 230, "default descent completes within 230 ticks of firing");
                h.assertTrue(f.level.getBlockEntity(touchdown) instanceof SupplyCrateBlockEntity, "natural descent creates a supply crate");
            }).thenExecute(() -> {
                var crate = (SupplyCrateBlockEntity) f.level.getBlockEntity(touchdown);
                h.assertTrue(falling.isRemoved(), "falling entity becomes a single container");
                h.assertTrue(crate.saveWithoutMetadata().getString("LootTable").equals(YujiContent.id("chests/yuji_airdrop").toString()), "PR1 retains the original deferred loot table");
                crate.createMenu(1, f.player.getInventory(), f.player);
                h.assertTrue(!crate.isEmpty(), "actual first opening yields loot");
                crate.clearContent(); f.level.setBlock(touchdown, Blocks.AIR.defaultBlockState(), 16);
                landed[0] = true;
                complete.run();
            });
        });
        h.runAfterDelay(231, () -> h.assertTrue(landed[0], "the shot landed and opened before its deadline"));
    }

    @GameTest(templateNamespace = "flare_green_tests", template = "empty", batch = "flare_green_repeat", timeoutTicks = 8500)
    public static void greenOpenTerrainThirtyFlights(GameTestHelper h) {
        ready(h, () -> repeat(h, 0));
    }

    private static void repeat(GameTestHelper h, int completed) {
        if (completed == 30) {
            System.out.println("T5_GREEN_OPEN_TERRAIN_PASSED shots=30 spawned=30 landed=30 opened=30");
            h.succeed(); return;
        }
        awaitDrop(h, shoot(h), () -> repeat(h, completed + 1));
    }

    @GameTest(templateNamespace = "flare_green_tests", template = "empty", batch = "flare_green_sparse", timeoutTicks = 1500)
    public static void greenSparseValidLandingIsNotLostToRandomAttempts(GameTestHelper h) {
        ready(h, () -> {
            Fixture f = fixture;
            // One exposed solid block among water columns: legal ground exists, but random probes can miss it.
            for (BlockPos p : BlockPos.betweenClosed(f.center.offset(-27, -1, -27), f.center.offset(27, -1, 27)))
                f.set(p, Blocks.WATER.defaultBlockState());
            f.set(f.center.below(), Blocks.STONE.defaultBlockState());
            BlockPos legal = f.center.east(12);
            f.set(legal.below(), Blocks.STONE.defaultBlockState());
            h.assertTrue(FlarePlacement.safeGround(f.level, legal), "an actual safe landing column exists");
            h.assertTrue(FlarePlacement.canPlace(f.level, legal, FlareContent.CRATE.get().defaultBlockState(), f.player),
                    "the real Forge placement event allows the landing");
            // This isolated batch makes the random-probe miss reproducible, without mocking terrain or placement.
            f.level.random.setSeed(0L);
            var search = new FlarePlacement.LandingSearch(Vec3.atBottomCenterOf(f.center));
            BlockPos first = search.advance(f.level, f.player);
            System.out.println("T5_GREEN_SPARSE_SEARCH attempts=" + FlareConfig.DROP_ATTEMPTS.get()
                    + " seed=0 legal=" + legal + " randomResult=" + first);
            h.assertTrue(first == null && !search.finished(), "the reproduced random miss remains pending, not rejected");
            h.startSequence().thenWaitUntil(() -> {
                BlockPos landing = search.advance(f.level, f.player);
                h.assertTrue(legal.equals(landing), "bounded fallback must find the only safe, permitted landing");
                System.out.println("T5_GREEN_SPARSE_FALLBACK_PASSED landing=" + landing);
            }).thenSucceed();
        });
    }

    @GameTest(templateNamespace = "flare_green_tests", template = "empty", batch = "flare_green_collision", timeoutTicks = 1700)
    public static void greenIndoorRejectsButEarlyImpactSurvivesUntilActivation(GameTestHelper h) {
        ready(h, () -> {
            Fixture f = fixture;
            BlockPos roof = f.center.above(5);
            f.set(roof, Blocks.GLASS.defaultBlockState());
            f.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FlareContent.GREEN.get(), 2));
            FlareContent.GUN.get().use(f.level, f.player, InteractionHand.MAIN_HAND);
            h.assertTrue(f.player.getOffhandItem().getCount() == 2
                    && !f.player.getCooldowns().isOnCooldown(FlareContent.GUN.get())
                    && f.level.getEntitiesOfClass(FlareProjectile.class, f.bounds).isEmpty(), "indoor refusal consumes no round or cooldown");
            f.set(roof, Blocks.AIR.defaultBlockState());
            FlareProjectile flare = shoot(h);
            f.set(roof, Blocks.GLASS.defaultBlockState());
            h.runAfterDelay(20, () -> h.assertTrue(!flare.isRemoved() && flare.getY() < roof.getY(), "early roof collision did not discard the green projectile"));
            awaitDrop(h, flare, () -> {
                System.out.println("T5_GREEN_COLLISION_PASSED indoorRejected=true survived79=true spawned=1 landed=1 opened=1");
                h.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "flare_green_tests", template = "empty", batch = "flare_green_protection", timeoutTicks = 2100)
    public static void greenSearchNeverBypassesPlacementProtection(GameTestHelper h) {
        ready(h, () -> {
            Fixture f = fixture;
            FlareConfig.DROP_MAX.set(64.0);
            Set<BlockPos> denied = new HashSet<>();
            Map<Long, Integer> eventsPerTick = new LinkedHashMap<>();
            f.guard = event -> {
                if (event.getEntity() == f.player && event.getPlacedBlock().is(FlareContent.CRATE.get())) {
                    denied.add(event.getPos().immutable()); event.setCanceled(true);
                    eventsPerTick.merge(f.level.getGameTime(), 1, Integer::sum);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, f.guard);
            FlareProjectile flare = shoot(h);
            h.runAfterDelay(85, () -> h.assertTrue(!flare.isRemoved(), "search exhaustion keeps the green flare pending"));
            h.startSequence().thenWaitUntil(() -> h.assertTrue(flare.isRemoved(), "bounded search eventually exhausts the protected annulus"))
                    .thenExecute(() -> {
                h.assertTrue(f.level.getEntitiesOfClass(FallingSupplyCrate.class, f.bounds).isEmpty(), "protected search spawns no crate");
                h.assertTrue(!denied.isEmpty(), "real Forge protection events were consulted");
                int maxEvents = eventsPerTick.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                h.assertTrue(maxEvents <= 48 && eventsPerTick.size() > 1, "maximum-radius fallback preserves the 48-probe per-tick budget");
                for (BlockPos pos : denied) h.assertTrue(f.level.getBlockState(pos).isAir()
                        && f.level.getBlockEntity(pos) == null, "rejected probes restore air and remove provisional containers");
                System.out.println("T5_GREEN_PROTECTION_PASSED radius=64 deniedColumns=" + denied.size()
                        + " maxEventsPerTick=" + maxEvents + " provisionalCrates=0");
                h.succeed();
            });
        });
    }

    private FlareGreenGameTests() { }
}
