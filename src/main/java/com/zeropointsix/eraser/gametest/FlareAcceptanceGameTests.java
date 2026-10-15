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
    private static Fixture fixture;

    private static final class Fixture {
        final ServerLevel level;
        final BlockPos center;
        final Set<Long> chunks = new HashSet<>(), ownedTickets = new HashSet<>();
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final List<Entity> entities = new ArrayList<>();
        final Set<Long> originalLights = new HashSet<>();
        final GameTestConfigs.LiveConfig config;

        Fixture(GameTestHelper h, int slot, boolean arena) {
            level = h.getLevel();
            center = new BlockPos(8200 + slot * 128, 200, 8200);
            config = GameTestConfigs.sandbox(YujiConfig.SPEC);
            for (Tag tag : FlareLights.get(level).save(new CompoundTag()).getList("Lights", Tag.TAG_COMPOUND))
                originalLights.add(((CompoundTag) tag).getLong("Pos"));
            for (int x = (center.getX() - 16) >> 4; x <= (center.getX() + 16) >> 4; x++)
                for (int z = (center.getZ() - 16) >> 4; z <= (center.getZ() + 16) >> 4; z++) {
                    long key = ChunkPos.asLong(x, z);
                    h.assertTrue(!level.getForcedChunks().contains(key), "acceptance area has no foreign forced ticket");
                    chunks.add(key);
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
                level.setChunkForced(c.x, c.z, true); ownedTickets.add(key); level.getChunk(c.x, c.z);
            }
        }

        void release() {
            for (long key : ownedTickets) {
                ChunkPos c = new ChunkPos(key); level.setChunkForced(c.x, c.z, false);
            }
            ownedTickets.clear();
        }

        boolean ticking() {
            return chunks.stream().allMatch(key -> {
                ChunkPos c = new ChunkPos(key);
                return level.isPositionEntityTicking(new BlockPos(c.getMinBlockX() + 8, center.getY(), c.getMinBlockZ() + 8));
            });
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
            config.close(); release();
        }
    }

    private static void close() { if (fixture != null) { fixture.close(); fixture = null; } }
    @AfterBatch(batch = "flare_acceptance_vanilla") public static void afterVanilla(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_acceptance_yuji") public static void afterYuji(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_acceptance_shadow") public static void afterShadow(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_acceptance_unload") public static void afterUnload(ServerLevel level) { close(); }

    private static void ready(GameTestHelper h, int slot, boolean arena, Runnable action) {
        fixture = new Fixture(h, slot, arena);
        // CI runners sometimes need a few ticks before forced chunks become entity-ticking.
        h.startSequence().thenIdle(40)
                .thenWaitUntil(() -> h.assertTrue(fixture.ticking(), "acceptance chunks entity-ticking"))
                .thenWaitUntil(() -> h.assertTrue(!arena || fixture.level.getMaxLocalRawBrightness(fixture.center) <= 6,
                        "roofed arena is dark before any flare"))
                .thenExecute(action);
    }

    private static FlareDecoy decoy(GameTestHelper h) {
        var found = fixture.level.getEntitiesOfClass(FlareDecoy.class, new AABB(fixture.center).inflate(14));
        h.assertTrue(found.size() == 1, "exactly one naturally activated decoy exists");
        return found.get(0);
    }

    private static void transfer(GameTestHelper h, Mob mob) {
        FlareConfig.RED_LIFETIME.set(80);
        h.assertTrue(FlareConfig.TAUNT_INTERVAL.get() == 10, "default ten-tick taunt cadence retained");
        ServerPlayer player = fixture.player();
        BlockPos start = fixture.center.west(8);
        mob.setPersistenceRequired(); fixture.add(mob, start);
        h.assertTrue(!mob.isNoAi() && mob.getTarget() == null, "no target or AI action injected");
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(mob.getTarget() == player, "live AI first acquires the survival player");
            h.assertTrue(mob.position().distanceTo(Vec3.atBottomCenterOf(start)) > 1, "mob actually pursues player before red flare");
        }).thenExecute(() -> {
            var flare = fixture.shoot(0, player, fixture.center);
            h.startSequence().thenWaitUntil(() -> h.assertTrue(flare.isRemoved(), "red projectile impacts naturally"))
                    .thenExecute(() -> {
                        FlareDecoy target = decoy(h);
                        h.assertTrue(mob.getTarget() == target && target.tickCount <= 10,
                                "pursuit transfers to decoy within ten ticks, age=" + target.tickCount);
                        System.out.println("FLARE_TARGET_TRANSFER mob=" + mob.getType().toShortString()
                                + " from=player to=decoy decoyTicks=" + target.tickCount);
                        h.startSequence().thenWaitUntil(() -> h.assertTrue(target.getHealth() < target.getMaxHealth()
                                        && target.getLastHurtByMob() == mob, "transferred mob naturally reaches and attacks decoy"))
                                .thenWaitUntil(() -> h.assertTrue(target.isRemoved(), "decoy ends by production expiry or combat"))
                                .thenExecute(() -> {
                                    Vec3 endedAt = mob.position();
                                    h.startSequence().thenWaitUntil(() -> h.assertTrue(player.isAlive() && mob.getTarget() == player
                                                    && mob.position().distanceTo(endedAt) > 1,
                                            "live AI reacquires and resumes pursuit of original player"))
                                            .thenExecute(() -> System.out.println("FLARE_TARGET_REACQUIRED mob="
                                                    + mob.getType().toShortString() + " to=player travelled=" + mob.position().distanceTo(endedAt)))
                                            .thenSucceed();
                                });
                    });
        });
    }

    @GameTest(templateNamespace = "flare_acceptance_tests", template = "empty", batch = "flare_acceptance_vanilla", timeoutTicks = 1200)
    public static void flareRedTransfersVanillaPursuitAndReacquiresPlayer(GameTestHelper h) {
        ready(h, 0, true, () -> transfer(h, EntityType.ZOMBIE.create(fixture.level)));
    }

    @GameTest(templateNamespace = "flare_acceptance_tests", template = "empty", batch = "flare_acceptance_yuji", timeoutTicks = 1200)
    public static void flareRedTransfersYujiPursuitAndReacquiresPlayer(GameTestHelper h) {
        ready(h, 1, true, () -> transfer(h, YujiContent.MOBS.get(YujiKind.SHASHENG).get().create(fixture.level)));
    }

    @GameTest(templateNamespace = "flare_acceptance_tests", template = "empty", batch = "flare_acceptance_shadow", timeoutTicks = 1200)
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
                                    h.succeed();
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

    @GameTest(templateNamespace = "flare_acceptance_tests", template = "empty", batch = "flare_acceptance_unload", timeoutTicks = 1600)
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
                                .thenSucceed();
                    });
        });
    }

    private FlareAcceptanceGameTests() { }
}
