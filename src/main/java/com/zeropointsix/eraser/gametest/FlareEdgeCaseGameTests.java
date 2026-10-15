package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.yuji.YujiConfig;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("flare_edge_tests")
@PrefixGameTestTemplate(false)
public final class FlareEdgeCaseGameTests {
    private static Fixture fixture;

    private static final class Fixture {
        final ServerLevel level;
        final BlockPos center;
        final Player owner;
        final Set<Long> chunks = new HashSet<>(), addedTickets = new HashSet<>();
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final List<Entity> entities = new ArrayList<>();
        GameTestConfigs.LiveConfig config;
        boolean prepared, completed;

        Fixture(GameTestHelper h) {
            level = h.getLevel();
            BlockPos reference = h.absolutePos(new BlockPos(3, 0, 3));
            for (int x = (reference.getX() - 10) >> 4; x <= (reference.getX() + 10) >> 4; x++)
                for (int z = (reference.getZ() - 10) >> 4; z <= (reference.getZ() + 10) >> 4; z++) {
                    long key = ChunkPos.asLong(x, z); chunks.add(key);
                    if (!level.getForcedChunks().contains(key)) { level.setChunkForced(x, z, true); addedTickets.add(key); }
                    level.getChunk(x, z);
                }
            int surface = level.getMinBuildHeight();
            for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++)
                surface = Math.max(surface, level.getHeight(Heightmap.Types.MOTION_BLOCKING, reference.getX() + x, reference.getZ() + z));
            int ceiling = level.getMaxBuildHeight() - 136;
            center = new BlockPos(reference.getX(), Math.min(surface + 2, ceiling), reference.getZ());
            System.out.println("FLARE_EDGE_AREA reference=" + reference + " surface=" + surface
                    + " center=" + center + " maxHeight=" + level.getMaxBuildHeight());
            h.assertTrue(center.getY() + 128 < level.getMaxBuildHeight() - 4, "fixture permits full 128-block descent: " + center);
            // Random mountainous terrain must not clamp the configured 128-block
            // drop. Clear the test column and restore every changed block later.
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-9, 0, -9),
                    new BlockPos(center.getX() + 9, level.getMaxBuildHeight() - 1, center.getZ() + 9)))
                if (!level.getBlockState(p).isAir()) set(p, Blocks.AIR.defaultBlockState());
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, -1, -8), center.offset(8, -1, 8)))
                set(p, Blocks.STONE.defaultBlockState());
            owner = h.makeMockSurvivalPlayer();
            owner.setPos(Vec3.atBottomCenterOf(center.offset(-7, 0, -7)));
        }

        void set(BlockPos pos, BlockState state) {
            previous.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, 16);
        }

        boolean ticking() {
            return chunks.stream().allMatch(key -> {
                ChunkPos c = new ChunkPos(key);
                return level.isPositionEntityTicking(new BlockPos(c.getMinBlockX() + 8, center.getY(), c.getMinBlockZ() + 8));
            });
        }

        <T extends Entity> T add(T entity, Vec3 position) {
            entity.setPos(position); level.addFreshEntity(entity); entities.add(entity); return entity;
        }

        FlareProjectile shoot(int color, Vec3 offset, Vec3 motion) {
            var flare = new FlareProjectile(FlareContent.FLARE.get(), level);
            flare.setColor(color); flare.setOwner(owner);
            add(flare, Vec3.atBottomCenterOf(center).add(offset)); flare.setDeltaMovement(motion); return flare;
        }

        void configure() { config = GameTestConfigs.sandbox(YujiConfig.SPEC); }

        void close() {
            entities.forEach(Entity::discard);
            level.getEntitiesOfClass(FlareDecoy.class, new AABB(center).inflate(12)).forEach(Entity::discard);
            previous.forEach((pos, state) -> {
                if (level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate) crate.clearContent();
                level.setBlock(pos, state, 16);
            });
            if (config != null) config.close();
            for (long key : addedTickets) { ChunkPos c = new ChunkPos(key); level.setChunkForced(c.x, c.z, false); }
        }
    }

    private static void close() { if (fixture != null) { fixture.close(); fixture = null; } }
    @AfterBatch(batch = "flare_edge_wall") public static void afterWall(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_edge_lowceiling") public static void afterLowCeiling(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_edge_floor") public static void afterFloor(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_edge_ceiling") public static void afterCeiling(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_edge_entity") public static void afterEntity(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_edge_flight") public static void afterFlight(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_edge_yellow") public static void afterYellow(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_edge_slow") public static void afterSlow(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_edge_reload") public static void afterReload(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_edge_ai_vanilla") public static void afterVanillaAi(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_edge_ai_yuji") public static void afterYujiAi(ServerLevel level) { close(); }

    private static void ready(GameTestHelper h, Runnable run) {
        ready(h, 300, run);
    }

    private static void ready(GameTestHelper h, int behaviorTicks, Runnable run) {
        long startedAt = h.getLevel().getGameTime();
        fixture = new Fixture(h);
        Fixture preparedFixture = fixture;
        h.runAfterDelay(1200, () -> h.assertTrue(preparedFixture.prepared, "edge preparation finishes within 1200 ticks"));
        h.startSequence().thenWaitUntil(() -> h.assertTrue(preparedFixture.ticking(), "fixture chunks entity-ticking"))
                .thenExecute(() -> {
                    preparedFixture.prepared = true;
                    System.out.println("FLARE_EDGE_READY preparationTicks="
                            + (preparedFixture.level.getGameTime() - startedAt) + " behaviorTicks=" + behaviorTicks);
                    h.runAfterDelay(behaviorTicks, () -> h.assertTrue(preparedFixture.completed,
                            "edge behavior finishes within its original " + behaviorTicks + " tick budget"));
                    run.run();
                });
    }

    private static void finish(GameTestHelper h) { fixture.completed = true; h.succeed(); }

    private static FlareDecoy decoy(GameTestHelper h) {
        var found = fixture.level.getEntitiesOfClass(FlareDecoy.class, new AABB(fixture.center).inflate(12));
        h.assertTrue(found.size() == 1, "one projectile produces exactly one decoy, found " + found.size());
        return found.get(0);
    }

    private static void assertHealthy(GameTestHelper h, FlareDecoy decoy) {
        h.assertTrue(!decoy.isRemoved() && decoy.getHealth() == decoy.getMaxHealth(), "unattacked decoy retains full health");
        h.assertTrue(fixture.level.noCollision(decoy, decoy.getBoundingBox()), "decoy bounding box does not intersect blocks");
        h.assertTrue(!decoy.isInWall(), "decoy eye is outside suffocating blocks");
    }

    private static void verifyImpact(GameTestHelper h, FlareProjectile flare) {
        h.runAfterDelay(12, () -> {
            h.assertTrue(flare.isRemoved(), "actual projectile impact activates red flare before timeout");
            var decoy = decoy(h); assertHealthy(h, decoy);
            h.runAfterDelay(45, () -> { assertHealthy(h, decoy); finish(h); });
        });
    }

    @GameTest(templateNamespace = "flare_edge_tests", template = "empty", batch = "flare_edge_wall", timeoutTicks = 1600)
    public static void flareRedWallImpactDoesNotSuffocate(GameTestHelper h) {
        ready(h, () -> {
            for (BlockPos p : BlockPos.betweenClosed(fixture.center.offset(3, 0, -3), fixture.center.offset(3, 5, 3)))
                fixture.set(p, Blocks.STONE.defaultBlockState());
            verifyImpact(h, fixture.shoot(0, new Vec3(0, 1.4, 0), new Vec3(1, 0, 0)));
        });
    }

    @GameTest(templateNamespace = "flare_edge_tests", template = "empty", batch = "flare_edge_floor", timeoutTicks = 1600)
    public static void flareRedFloorImpactHasClearance(GameTestHelper h) {
        ready(h, () -> verifyImpact(h, fixture.shoot(0, new Vec3(0, 4, 0), new Vec3(0, -1, 0))));
    }

    @GameTest(templateNamespace = "flare_edge_tests", template = "empty", batch = "flare_edge_lowceiling", timeoutTicks = 1600)
    public static void flareRedWallUnderLowCeilingFindsClearance(GameTestHelper h) {
        ready(h, () -> {
            for (BlockPos p : BlockPos.betweenClosed(fixture.center.offset(-3, 2, -3), fixture.center.offset(3, 2, 3)))
                fixture.set(p, Blocks.STONE.defaultBlockState());
            for (BlockPos p : BlockPos.betweenClosed(fixture.center.offset(3, 0, -3), fixture.center.offset(3, 1, 3)))
                fixture.set(p, Blocks.STONE.defaultBlockState());
            verifyImpact(h, fixture.shoot(0, new Vec3(0, 1.4, 0), new Vec3(1, 0, 0)));
        });
    }

    @GameTest(templateNamespace = "flare_edge_tests", template = "empty", batch = "flare_edge_ceiling", timeoutTicks = 1600)
    public static void flareRedCeilingImpactHasClearance(GameTestHelper h) {
        ready(h, () -> {
            for (BlockPos p : BlockPos.betweenClosed(fixture.center.offset(-3, 4, -3), fixture.center.offset(3, 4, 3)))
                fixture.set(p, Blocks.STONE.defaultBlockState());
            verifyImpact(h, fixture.shoot(0, new Vec3(0, 1, 0), new Vec3(0, 1.5, 0)));
        });
    }

    @GameTest(templateNamespace = "flare_edge_tests", template = "empty", batch = "flare_edge_entity", timeoutTicks = 1600)
    public static void flareRedEntityImpactActivatesOnce(GameTestHelper h) {
        ready(h, () -> {
            var cow = fixture.add(EntityType.COW.create(fixture.level), Vec3.atBottomCenterOf(fixture.center.east(3)));
            cow.setNoAi(true);
            verifyImpact(h, fixture.shoot(0, new Vec3(0, 1, 0), new Vec3(1, 0, 0)));
        });
    }

    private static void verifyNaturalPursuit(GameTestHelper h, Mob attacker) {
        // Shade the real AI arena so sunlight damage cannot interfere with pursuit.
        for (BlockPos p : BlockPos.betweenClosed(fixture.center.offset(-8, 3, -2), fixture.center.offset(8, 3, 2)))
            fixture.set(p, Blocks.STONE.defaultBlockState());
        Vec3 start = Vec3.atBottomCenterOf(fixture.center.west(6));
        attacker.setPersistenceRequired(); fixture.add(attacker, start);
        h.assertTrue(!attacker.isNoAi() && attacker.getTarget() == null, "real mob begins with active AI and no injected target");
        var flare = fixture.shoot(0, new Vec3(2, 1, 0), new Vec3(0, -.5, 0));
        h.runAfterDelay(12, () -> {
            h.assertTrue(flare.isRemoved(), "real red projectile hits the arena floor");
            var target = decoy(h); assertHealthy(h, target);
            h.startSequence().thenWaitUntil(() -> {
                h.assertTrue(attacker.getTarget() == target, "scheduled taunt selects the real decoy");
                h.assertTrue(attacker.position().distanceTo(start) > 3 && attacker.distanceTo(target) < 3,
                        "live navigation closes the eight-block gap, distance=" + attacker.distanceTo(target));
                h.assertTrue(target.isAlive() && target.getHealth() < target.getMaxHealth()
                                && target.getLastHurtByMob() == attacker,
                        "natural mob attack damages the decoy, health=" + target.getHealth());
            }).thenExecute(() -> {
                System.out.println("FLARE_AI_PURSUIT_PASSED mob=" + attacker.getType().toShortString()
                        + " ticks=" + attacker.tickCount + " travelled=" + attacker.position().distanceTo(start)
                        + " distance=" + attacker.distanceTo(target) + " decoyHealth=" + target.getHealth());
                finish(h);
            });
        });
    }

    @GameTest(templateNamespace = "flare_edge_tests", template = "empty", batch = "flare_edge_ai_vanilla", timeoutTicks = 1700)
    public static void flareRedVanillaZombieNaturallyPursuesAndAttacks(GameTestHelper h) {
        ready(h, 400, () -> verifyNaturalPursuit(h, EntityType.ZOMBIE.create(fixture.level)));
    }

    @GameTest(templateNamespace = "flare_edge_tests", template = "empty", batch = "flare_edge_ai_yuji", timeoutTicks = 1700)
    public static void flareRedYujiNaturallyPursuesAndAttacks(GameTestHelper h) {
        ready(h, 400, () -> verifyNaturalPursuit(h, YujiContent.MOBS.get(YujiKind.SHASHENG).get().create(fixture.level)));
    }

    @GameTest(templateNamespace = "flare_edge_tests", template = "empty", batch = "flare_edge_flight", timeoutTicks = 1600)
    public static void flareRedNaturalFlightActivatesAtSixtyTicks(GameTestHelper h) {
        ready(h, () -> {
            var flare = fixture.shoot(0, new Vec3(0, 3, 0), new Vec3(0, 1.5, 0));
            h.runAfterDelay(59, () -> h.assertTrue(!flare.isRemoved(), "red remains airborne before 60 ticks"));
            h.runAfterDelay(62, () -> {
                h.assertTrue(flare.isRemoved(), "red natural timer activates without direct activate call");
                var found = fixture.level.getEntitiesOfClass(FlareDecoy.class, new AABB(fixture.center).inflate(65));
                h.assertTrue(found.size() == 1, "red flight produces one decoy");
                fixture.entities.addAll(found); assertHealthy(h, found.get(0)); finish(h);
            });
        });
    }

    @GameTest(templateNamespace = "flare_edge_tests", template = "empty", batch = "flare_edge_yellow", timeoutTicks = 1600)
    public static void flareYellowNaturalApexAndDeadline(GameTestHelper h) {
        ready(h, () -> {
            fixture.configure(); FlareConfig.LIGHT_RADIUS.set(3); FlareConfig.YELLOW_LIFETIME.set(30);
            var apex = fixture.shoot(1, new Vec3(-3, 4, 0), new Vec3(0, .1, 0));
            var deadline = fixture.shoot(1, new Vec3(3, 4, 0), new Vec3(0, 2, 0));
            h.runAfterDelay(10, () -> h.assertTrue(apex.lit() && !deadline.lit(), "low flight lights at apex before deadline"));
            h.runAfterDelay(29, () -> h.assertTrue(!deadline.lit(), "high flight remains unlit before 30 ticks"));
            h.runAfterDelay(32, () -> h.assertTrue(deadline.lit(), "high flight lights at 30-tick deadline"));
            h.runAfterDelay(80, () -> {
                h.assertTrue(apex.isRemoved() && deadline.isRemoved(), "both natural light projectiles expire");
                h.assertTrue(FlareLights.get(fixture.level).size() == 0, "production cleanup removes their light records");
                finish(h);
            });
        });
    }

    private static FallingSupplyCrate drop(GameTestHelper h) {
        fixture.configure(); FlareConfig.DROP_HEIGHT.set(128); FlareConfig.DESCENT_SPEED.set(.05);
        fixture.previous.putIfAbsent(fixture.center, fixture.level.getBlockState(fixture.center));
        var crate = new FallingSupplyCrate(FlareContent.FALLING_CRATE.get(), fixture.level);
        crate.prepare(fixture.center, fixture.owner.getUUID());
        h.assertTrue(crate.getY() - fixture.center.getY() == 128, "full configured height, not build-limit clamped");
        fixture.level.addFreshEntity(crate); fixture.entities.add(crate); return crate;
    }

    private static void assertLanded(GameTestHelper h) {
        h.assertTrue(fixture.level.getBlockEntity(fixture.center) instanceof SupplyCrateBlockEntity, "slow descent reaches safe ground and places crate");
        var block = (SupplyCrateBlockEntity) fixture.level.getBlockEntity(fixture.center);
        h.assertTrue(block.saveWithoutMetadata().contains("LootTable"), "touchdown retains unopened lazy loot");
        block.unpackLootTable(fixture.owner);
        h.assertTrue(!block.isEmpty(), "slow airdrop has real datapack loot"); finish(h);
    }

    @GameTest(templateNamespace = "flare_edge_tests", template = "empty", batch = "flare_edge_slow", timeoutTicks = 4300)
    public static void flareHighestSlowestDropReachesGround(GameTestHelper h) {
        ready(h, 3000, () -> {
            var crate = drop(h);
            h.runAfterDelay(5, () -> {
                CompoundTag saved = new CompoundTag(); crate.saveWithoutId(saved); crate.discard();
                h.assertTrue(saved.getInt("MaxAge") > 2560, "slow descent has an extended lifetime before reload");
                var restored = new FallingSupplyCrate(FlareContent.FALLING_CRATE.get(), fixture.level);
                restored.load(saved);
                CompoundTag roundtrip = new CompoundTag(); restored.saveWithoutId(roundtrip);
                h.assertTrue(roundtrip.getInt("Age") == saved.getInt("Age")
                        && roundtrip.getInt("MaxAge") == saved.getInt("MaxAge")
                        && restored.position().equals(crate.position()), "same-speed reload preserves extended budget, age and position");
                fixture.level.addFreshEntity(restored); fixture.entities.add(restored);
                h.runAfterDelay(2400, () -> h.assertTrue(!restored.isRemoved(), "legal 2560-tick descent survives old 2400-tick cutoff"));
                h.runAfterDelay(2565, () -> assertLanded(h));
            });
        });
    }

    @GameTest(templateNamespace = "flare_edge_tests", template = "empty", batch = "flare_edge_reload", timeoutTicks = 4300)
    public static void flareSlowDropReloadAndLiveSpeedChange(GameTestHelper h) {
        ready(h, 3000, () -> {
            var original = drop(h); FlareConfig.DESCENT_SPEED.set(1.0);
            h.runAfterDelay(5, () -> {
                FlareConfig.DESCENT_SPEED.set(.05);
                CompoundTag saved = new CompoundTag(); original.saveWithoutId(saved); original.discard();
                h.assertTrue(saved.getInt("MaxAge") > 2560, "lifetime already covers a live slowdown before reload");
                var restored = new FallingSupplyCrate(FlareContent.FALLING_CRATE.get(), fixture.level);
                restored.load(saved);
                CompoundTag roundtrip = new CompoundTag(); restored.saveWithoutId(roundtrip);
                h.assertTrue(roundtrip.getInt("Age") == saved.getInt("Age") && restored.position().equals(original.position()), "age and position survive entity save/load");
                h.assertTrue(roundtrip.getInt("MaxAge") == saved.getInt("MaxAge"), "saved descent budget survives reload unchanged");
                saved.remove("MaxAge"); restored.load(saved);
                fixture.level.addFreshEntity(restored); fixture.entities.add(restored);
                h.runAfterDelay(2400, () -> h.assertTrue(!restored.isRemoved(), "reloaded slowed crate survives past old age limit"));
                h.runAfterDelay(2490, () -> assertLanded(h));
            });
        });
    }

    private FlareEdgeCaseGameTests() { }
}
