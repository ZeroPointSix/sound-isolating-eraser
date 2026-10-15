package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.shadow.ShadowLight;
import com.zeropointsix.eraser.shadow.ShadowTanglerEntity;
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
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("flare_acceptance_tests")
@PrefixGameTestTemplate(false)
public final class FlareAcceptanceGameTests {
    private static final TicketType<String> ACCEPTANCE_TICKET =
            TicketType.create("flare_acceptance", String::compareTo, 0);
    private static Fixture fixture;

    private static final class Fixture {
        final ServerLevel level;
        final BlockPos center;
        final Set<Long> chunks = new HashSet<>(), ownedTickets = new HashSet<>();
        final Set<Long> originalForced = new HashSet<>();
        final String ticketOwner = UUID.randomUUID().toString();
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final List<Entity> entities = new ArrayList<>();
        final Set<Long> originalLights = new HashSet<>();
        final GameTestConfigs.LiveConfig config;
        final int originalRedLifetime, originalYellowLifetime, originalLightRadius;
        boolean prepared, completed;
        java.util.function.Supplier<String> diagnostic = () -> "preparing";

        Fixture(GameTestHelper h, int slot, boolean arena) {
            level = h.getLevel();
            center = new BlockPos(8200 + slot * 128, 200, 8200);
            originalRedLifetime = FlareConfig.RED_LIFETIME.get();
            originalYellowLifetime = FlareConfig.YELLOW_LIFETIME.get();
            originalLightRadius = FlareConfig.LIGHT_RADIUS.get();
            config = GameTestConfigs.sandbox(YujiConfig.SPEC);
            for (Tag tag : FlareLights.get(level).save(new CompoundTag()).getList("Lights", Tag.TAG_COMPOUND))
                originalLights.add(((CompoundTag) tag).getLong("Pos"));
            for (int x = (center.getX() - 16) >> 4; x <= (center.getX() + 16) >> 4; x++)
                for (int z = (center.getZ() - 16) >> 4; z <= (center.getZ() + 16) >> 4; z++) {
                    long key = ChunkPos.asLong(x, z);
                    chunks.add(key);
                    if (level.getForcedChunks().contains(key)) originalForced.add(key);
                }
            load();
            if (arena) {
                for (BlockPos p : BlockPos.betweenClosed(center.offset(-14, -1, -14), center.offset(14, 4, 14))) {
                    boolean shell = p.getY() == center.getY() - 1 || p.getY() == center.getY() + 4
                            || Math.abs(p.getX() - center.getX()) == 14 || Math.abs(p.getZ() - center.getZ()) == 14;
                    set(p, shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            } else {
                for (BlockPos p : BlockPos.betweenClosed(center.offset(-4, -4, -4), center.offset(4, 4, 4)))
                    set(p, Blocks.AIR.defaultBlockState());
            }
        }

        void load() {
            for (long key : chunks) {
                ChunkPos c = new ChunkPos(key);
                level.setChunkForced(c.x, c.z, true);
                // Own a distinct ticket; never replace or remove a player's existing ticket.
                level.getChunkSource().addRegionTicket(ACCEPTANCE_TICKET, c, 2, ticketOwner);
                ownedTickets.add(key); level.getChunk(c.x, c.z);
            }
        }

        void release() {
            for (long key : ownedTickets) {
                ChunkPos c = new ChunkPos(key);
                if (!originalForced.contains(key)) level.setChunkForced(c.x, c.z, false);
                level.getChunkSource().removeRegionTicket(ACCEPTANCE_TICKET, c, 2, ticketOwner);
            }
            ownedTickets.clear();
        }

        boolean ticking() {
            ChunkPos centerChunk = new ChunkPos(center);
            return level.isPositionEntityTicking(center)
                    && chunks.stream().filter(key -> {
                        ChunkPos c = new ChunkPos(key);
                        return Math.abs(c.x - centerChunk.x) <= 1 && Math.abs(c.z - centerChunk.z) <= 1;
                    }).allMatch(key -> {
                        ChunkPos c = new ChunkPos(key);
                        return level.isPositionEntityTicking(new BlockPos(c.getMinBlockX() + 8, center.getY(), c.getMinBlockZ() + 8));
                    });
        }

        String preparationState() {
            List<String> pending = new ArrayList<>();
            for (long key : chunks) {
                ChunkPos c = new ChunkPos(key);
                BlockPos pos = new BlockPos(c.getMinBlockX() + 8, center.getY(), c.getMinBlockZ() + 8);
                if (!level.isPositionEntityTicking(pos)) pending.add(c.toString());
            }
            return "center=" + center + " ticking=" + ticking() + " pendingChunks=" + pending
                    + " raw=" + level.getMaxLocalRawBrightness(center)
                    + " sky=" + level.getBrightness(LightLayer.SKY, center)
                    + " block=" + level.getBrightness(LightLayer.BLOCK, center);
        }

        void awaitReady(boolean arena) {
            long started = System.nanoTime();
            long deadline = started + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
            // GameTestServer runs ticks without the normal 50 ms wait. Pump the real
            // chunk/light executor before starting the AI window, not simulated entity ticks.
            while (!ticking() || (arena && level.getMaxLocalRawBrightness(center) > 6)) {
                if (System.nanoTime() >= deadline)
                    throw new AssertionError("acceptance preparation exceeded 60 real seconds: " + preparationState());
                if (!level.getChunkSource().pollTask())
                    java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }
            System.out.println("FLARE_PREPARATION_BARRIER elapsedMs="
                    + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                    + " " + preparationState());
        }

        void set(BlockPos pos, BlockState state) {
            previous.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, 3);
        }

        <T extends Entity> T add(T entity, BlockPos pos) {
            entity.setPos(Vec3.atBottomCenterOf(pos)); level.addFreshEntity(entity); entities.add(entity); return entity;
        }

        ServerPlayer player() {
            var profile = new GameProfile(UUID.randomUUID(), "FlareAcceptance");
            var player = new ServerPlayer(level.getServer(), level, profile);
            // Only outbound packets use Forge's test sink; targeting and player state are real.
            player.connection = new FakePlayer(level, profile).connection;
            player.setGameMode(GameType.SURVIVAL);
            // Keep the mock survival player alive through melee. Entity/abilities
            // invulnerability would make the player untargetable and fake-green the
            // reacquire assertion; extra health does not change production flare AI.
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
            player.setHealth(400);
            player.getFoodData().setFoodLevel(20);
            player.setPos(Vec3.atBottomCenterOf(center.east(10)));
            level.addNewPlayer(player); entities.add(player);
            return player;
        }

        FlareProjectile shoot(int color, Player owner, BlockPos pos) {
            var flare = new FlareProjectile(FlareContent.FLARE.get(), level);
            flare.setColor(color); flare.setOwner(owner);
            add(flare, pos.above(2)); flare.setDeltaMovement(0, -.5, 0); return flare;
        }

        void close() {
            try {
                load();
                entities.forEach(Entity::discard);
                level.getEntitiesOfClass(FlareDecoy.class, new AABB(center).inflate(24)).forEach(Entity::discard);
                level.getEntitiesOfClass(FlareProjectile.class, new AABB(center).inflate(24)).forEach(Entity::discard);
                for (Tag tag : FlareLights.get(level).save(new CompoundTag()).getList("Lights", Tag.TAG_COMPOUND)) {
                    BlockPos pos = BlockPos.of(((CompoundTag) tag).getLong("Pos"));
                    if (!originalLights.contains(pos.asLong()) && pos.distSqr(center) <= 32 * 32
                            && level.getBlockState(pos).is(Blocks.LIGHT)) level.removeBlock(pos, false);
                }
                previous.forEach((pos, state) -> level.setBlock(pos, state, 3));
                FlareLights.get(level).cleanup(level);
            } finally {
                try { config.close(); } finally { release(); }
            }
            if (FlareConfig.RED_LIFETIME.get() != originalRedLifetime
                    || FlareConfig.YELLOW_LIFETIME.get() != originalYellowLifetime
                    || FlareConfig.LIGHT_RADIUS.get() != originalLightRadius)
                throw new AssertionError("acceptance configuration was not restored");
            for (long key : chunks)
                if (level.getForcedChunks().contains(key) != originalForced.contains(key))
                    throw new AssertionError("acceptance changed pre-existing forced chunk " + new ChunkPos(key));
            if (entities.stream().anyMatch(entity -> !entity.isRemoved()))
                throw new AssertionError("acceptance left an owned entity behind");
            System.out.println("FLARE_FIXTURE_RESTORED center=" + center
                    + " config=true forcedChunks=true ownedEntitiesRemoved=true");
        }
    }

    private static void close() {
        Fixture closing = fixture;
        fixture = null;
        if (closing != null) closing.close();
    }
    @AfterBatch(batch = "flare_acceptance_vanilla") public static void afterVanilla(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_acceptance_yuji") public static void afterYuji(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_acceptance_shadow") public static void afterShadow(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_acceptance_unload") public static void afterUnload(ServerLevel level) { close(); }

    private static void ready(GameTestHelper h, int slot, boolean arena, Runnable action) {
        long startedAt = h.getLevel().getGameTime();
        fixture = new Fixture(h, slot, arena);
        Fixture preparedFixture = fixture;
        preparedFixture.awaitReady(arena);
        for (int tick = 200; tick < 1200; tick += 200) {
            h.runAfterDelay(tick, () -> {
                if (!preparedFixture.prepared) System.out.println("FLARE_PREPARING slot=" + slot
                        + " elapsedTicks=" + (preparedFixture.level.getGameTime() - startedAt)
                        + " " + preparedFixture.preparationState());
            });
        }
        h.runAfterDelay(1200, () -> h.assertTrue(preparedFixture.prepared,
                "acceptance preparation finishes within 1200 ticks: " + preparedFixture.preparationState()));
        h.startSequence().thenIdle(40)
                .thenWaitUntil(() -> h.assertTrue(preparedFixture.ticking(), "acceptance chunks entity-ticking"))
                .thenWaitUntil(() -> h.assertTrue(!arena || preparedFixture.level.getMaxLocalRawBrightness(preparedFixture.center) <= 6,
                        "roofed arena is dark before any flare"))
                .thenExecute(() -> {
                    preparedFixture.prepared = true;
                    System.out.println("FLARE_ACCEPTANCE_READY slot=" + slot + " preparationTicks="
                            + (preparedFixture.level.getGameTime() - startedAt));
                    // World/light preparation is not part of the behavior's bounded observation window.
                    int behaviorTicks = arena ? 900 : 1600;
                    h.runAfterDelay(behaviorTicks, () -> h.assertTrue(preparedFixture.completed,
                            "acceptance behavior finishes within " + behaviorTicks + " ticks after preparation: "
                                    + preparedFixture.diagnostic.get()));
                    action.run();
                });
    }

    private static void finish(GameTestHelper h) { fixture.completed = true; h.succeed(); }

    private static FlareDecoy decoy(GameTestHelper h) {
        var found = fixture.level.getEntitiesOfClass(FlareDecoy.class, new AABB(fixture.center).inflate(14));
        h.assertTrue(found.size() == 1, "exactly one naturally activated decoy exists");
        return found.get(0);
    }

    private static void transfer(GameTestHelper h, Mob mob) {
        // Exercise the production lifetime; 80 ticks can expire before natural pathfinding/attack.
        int lifetime = 600;
        FlareConfig.RED_LIFETIME.set(lifetime);
        h.assertTrue(FlareConfig.TAUNT_INTERVAL.get() == 10, "default ten-tick taunt cadence retained");
        ServerPlayer player = fixture.player();
        BlockPos start = fixture.center.west(8);
        mob.setPersistenceRequired(); fixture.add(mob, start);
        String[] phase = {"initial pursuit"};
        fixture.diagnostic = () -> "phase=" + phase[0] + " mobPos=" + mob.position()
                + " mobHealth=" + mob.getHealth() + " playerHealth=" + player.getHealth()
                + " target=" + mob.getTarget() + " navigationDone=" + mob.getNavigation().isDone();
        h.assertTrue(!mob.isNoAi() && mob.getTarget() == null, "no target or AI action injected");
        long pursuitStartedAt = fixture.level.getGameTime();
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(mob.getTarget() == player, "live AI first acquires the survival player");
            h.assertTrue(mob.position().distanceTo(Vec3.atBottomCenterOf(start)) > 1, "mob actually pursues player before red flare");
        }).thenExecute(() -> {
            System.out.println("FLARE_PLAYER_PURSUIT mob=" + mob.getType().toShortString()
                    + " elapsedTicks=" + (fixture.level.getGameTime() - pursuitStartedAt) + " mobTicks=" + mob.tickCount);
            var flare = fixture.shoot(0, player, fixture.center);
            phase[0] = "projectile impact";
            h.startSequence().thenWaitUntil(() -> h.assertTrue(flare.isRemoved(), "red projectile impacts naturally"))
                    .thenExecute(() -> {
                        FlareDecoy target = decoy(h);
                        phase[0] = "attack decoy";
                        Fixture current = fixture;
                        h.runAfterDelay(lifetime + 1, () -> System.out.println("FLARE_DEADLINE_DIAGNOSTIC "
                                + current.diagnostic.get() + " decoyPos=" + target.position()
                                + " decoyHealth=" + target.getHealth() + " removed=" + target.isRemoved()
                                + " lastAttacker=" + target.getLastHurtByMob()));
                        CompoundTag saved = new CompoundTag(); target.addAdditionalSaveData(saved);
                        long remaining = saved.getLong("FlareExpires") - fixture.level.getGameTime();
                        h.assertTrue(remaining > 0 && remaining <= lifetime,
                                "production decoy retains the configured " + lifetime + "-tick deadline");
                        h.runAfterDelay(remaining + 1, () -> h.assertTrue(target.isRemoved(),
                                "decoy is removed at its original production deadline"));
                        h.assertTrue(mob.getTarget() == target && target.tickCount <= 10,
                                "pursuit transfers to decoy within ten ticks, age=" + target.tickCount);
                        System.out.println("FLARE_TARGET_TRANSFER mob=" + mob.getType().toShortString()
                                + " from=player to=decoy decoyTicks=" + target.tickCount);
                        h.startSequence().thenWaitUntil(() -> h.assertTrue(target.getHealth() < target.getMaxHealth()
                                        && target.getLastHurtByMob() == mob, "transferred mob naturally reaches and attacks decoy"))
                                .thenExecute(() -> { phase[0] = "decoy expiry";
                                    System.out.println("FLARE_DECOY_ATTACKED mob=" + mob.getType().toShortString()
                                            + " decoyTicks=" + target.tickCount + " health=" + target.getHealth()); })
                                .thenWaitUntil(() -> h.assertTrue(target.isRemoved(), "decoy ends by production expiry or combat"))
                                .thenExecute(() -> {
                                    Vec3 endedAt = mob.position();
                                    phase[0] = "reacquire player";
                                    player.setHealth(player.getMaxHealth());
                                    h.startSequence().thenWaitUntil(() -> {
                                                if (player.isAlive() && player.getHealth() < 40)
                                                    player.setHealth(player.getMaxHealth());
                                                h.assertTrue(player.isAlive() && mob.getTarget() == player
                                                                && mob.position().distanceTo(endedAt) > 1,
                                                        "live AI reacquires and resumes pursuit of original player");
                                            })
                                            .thenExecute(() -> System.out.println("FLARE_TARGET_REACQUIRED mob="
                                                    + mob.getType().toShortString() + " to=player travelled="
                                                    + mob.position().distanceTo(endedAt)
                                                    + " playerHealth=" + player.getHealth()))
                                            .thenExecute(() -> finish(h));
                                });
                    });
        });
    }

    @GameTest(templateNamespace = "flare_acceptance_tests", template = "empty", batch = "flare_acceptance_vanilla", timeoutTicks = 2200)
    public static void flareRedTransfersVanillaPursuitAndReacquiresPlayer(GameTestHelper h) {
        ready(h, 0, true, () -> transfer(h, EntityType.ZOMBIE.create(fixture.level)));
    }

    @GameTest(templateNamespace = "flare_acceptance_tests", template = "empty", batch = "flare_acceptance_yuji", timeoutTicks = 2200)
    public static void flareRedTransfersYujiPursuitAndReacquiresPlayer(GameTestHelper h) {
        ready(h, 1, true, () -> transfer(h, YujiContent.MOBS.get(YujiKind.SHASHENG).get().create(fixture.level)));
    }

    @GameTest(templateNamespace = "flare_acceptance_tests", template = "empty", batch = "flare_acceptance_shadow", timeoutTicks = 2200)
    public static void flareYellowNaturallyRepelsAndBurnsShadowTangler(GameTestHelper h) {
        ready(h, 2, true, () -> {
            FlareConfig.LIGHT_RADIUS.set(12); FlareConfig.YELLOW_LIFETIME.set(160);
            ServerPlayer player = fixture.player();
            var shadow = fixture.add(ModEntities.SHADOW_TANGLER.get().create(fixture.level), fixture.center.west(6));
            shadow.setPersistenceRequired();
            Vec3 start = shadow.position();
            h.startSequence().thenWaitUntil(() -> h.assertTrue(shadow.getTarget() == player
                            && shadow.getLightTier() == ShadowLight.DARK && shadow.position().distanceTo(start) > 1,
                    "live dark-adapted Shadow Tangler pursues player before yellow flare"))
                    .thenExecute(() -> {
                        var flare = fixture.shoot(1, player, shadow.blockPosition().east(2));
                        h.startSequence().thenWaitUntil(() -> h.assertTrue(flare.lit()
                                        && shadow.getLightTier() == ShadowLight.BRIGHT && shadow.getTarget() == null,
                                "actual yellow projectile makes Shadow bright and cancels pursuit"))
                                .thenWaitUntil(() -> h.assertTrue(shadow.isAlive() && shadow.getHealth() <= shadow.getMaxHealth() - 2
                                                && shadow.getLastDamageSource() != null
                                                && shadow.getLastDamageSource().is(ShadowTanglerEntity.LIGHT_BURN),
                                        "registered entity ticks apply real light-burn damage"))
                                .thenExecute(() -> {
                                    h.assertTrue(!shadow.isNoAi() && shadow.isRetreating() && shadow.getTarget() == null,
                                            "real AI remains in light retreat instead of chasing player");
                                    h.assertTrue(Math.abs(shadow.getAttributeValue(Attributes.MOVEMENT_SPEED) - .1) < .0001,
                                            "yellow field applies bright-light movement penalty");
                                    System.out.println("FLARE_YELLOW_SHADOW_PASSED light="
                                            + fixture.level.getBrightness(LightLayer.BLOCK, shadow.blockPosition())
                                            + " tier=" + shadow.getLightTier() + " health=" + shadow.getHealth()
                                            + " retreat=" + shadow.isRetreating() + " target=null");
                                    finish(h);
                                });
                    });
        });
    }

    private static Map<BlockPos, Long> records(ServerLevel level, BlockPos center) {
        Map<BlockPos, Long> result = new LinkedHashMap<>();
        for (Tag tag : FlareLights.get(level).save(new CompoundTag()).getList("Lights", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) tag;
            BlockPos pos = BlockPos.of(record.getLong("Pos"));
            if (pos.distSqr(center) <= 9) result.put(pos, record.getLong("Expires"));
        }
        return result;
    }

    @GameTest(templateNamespace = "flare_acceptance_tests", template = "empty", batch = "flare_acceptance_unload", timeoutTicks = 3000)
    public static void flareYellowPlacedLightExpiresWhileChunkUnloaded(GameTestHelper h) {
        ready(h, 3, false, () -> {
            FlareConfig.LIGHT_RADIUS.set(3); FlareConfig.YELLOW_LIFETIME.set(600);
            var flare = new FlareProjectile(FlareContent.FLARE.get(), fixture.level);
            flare.setColor(1); flare.setOwner(h.makeMockSurvivalPlayer());
            fixture.add(flare, fixture.center); flare.setDeltaMovement(0, .1, 0);
            h.startSequence().thenWaitUntil(() -> h.assertTrue(flare.lit(), "yellow activates through normal flight ticks"))
                    .thenExecute(() -> {
                        Map<BlockPos, Long> original = records(fixture.level, fixture.center);
                        h.assertTrue(!original.isEmpty(), "real placed light records exist before unload");
                        original.keySet().forEach(pos -> h.assertTrue(fixture.level.getBlockState(pos).is(Blocks.LIGHT),
                                "actual owned block exists before unload"));
                        long deadline = original.values().stream().mapToLong(Long::longValue).max().orElseThrow();
                        fixture.level.getChunkSource().save(true);
                        fixture.level.getDataStorage().save();
                        fixture.release();
                        h.startSequence().thenWaitUntil(() -> {
                            fixture.level.getChunkSource().tick(() -> true, true);
                            h.assertTrue(!fixture.level.hasChunkAt(fixture.center), "placed-light chunk actually unloads");
                        }).thenExecute(() -> {
                            h.assertTrue(fixture.level.getGameTime() < deadline, "chunk unloads before original light expiry");
                            System.out.println("FLARE_CHUNK_UNLOADED sources=" + original.size() + " deadline=" + deadline
                                    + " gameTime=" + fixture.level.getGameTime());
                        }).thenWaitUntil(() -> h.assertTrue(fixture.level.getGameTime() > deadline + 10, "original deadline passes while unloaded"))
                                .thenExecute(() -> {
                                    h.assertTrue(!fixture.level.hasChunkAt(fixture.center), "cleanup never force-loads expired light chunk");
                                    h.assertTrue(records(fixture.level, fixture.center).equals(original), "unloaded expiry retains original persisted records");
                                    System.out.println("FLARE_CHUNK_EXPIRED_UNLOADED records=" + original.size());
                                    fixture.load();
                                }).thenWaitUntil(() -> {
                                    for (BlockPos pos : original.keySet()) h.assertTrue(fixture.level.getBlockState(pos).isAir(),
                                            "production LevelTick cleanup removes reloaded expired block");
                                    h.assertTrue(records(fixture.level, fixture.center).isEmpty(), "reload clears expired records without manual cleanup");
                                }).thenExecute(() -> System.out.println("FLARE_CHUNK_RELOAD_CLEANUP_PASSED sources=" + original.size()))
                                .thenExecute(() -> finish(h));
                    });
        });
    }

    private FlareAcceptanceGameTests() { }
}
