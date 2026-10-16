package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.brickrot.BrickrotContent;
import com.zeropointsix.eraser.config.SpawnCapConfig;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.spawn.SpawnCaps;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import com.zeropointsix.eraser.yuji.city.RainCityConfig;
import com.zeropointsix.eraser.yuji.city.RainCityData;
import com.zeropointsix.eraser.yuji.city.RainCitySpawns;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Synchronous config sandboxes keep the shared SERVER spec isolated from other batches. */
@GameTestHolder("yuji_tests")
@PrefixGameTestTemplate(false)
public final class SpawnCapGameTests {
    private static EntityType<YujiMonster> type() { return YujiContent.MOBS.get(YujiKind.YUCHI).get(); }
    private static BlockPos position(GameTestHelper h) { return h.absolutePos(new BlockPos(2, 4, 2)); }
    private static void limits(int dimension, int chunk) {
        SpawnCapConfig.DIMENSION_CAP.set(dimension);
        SpawnCapConfig.CHUNK_CAP.set(chunk);
        SpawnCapConfig.PER_LOADED_CHUNK.set(-1);
    }
    private static YujiMonster candidate(ServerLevel level, BlockPos pos, YujiKind kind) {
        YujiMonster mob = YujiContent.MOBS.get(kind).get().create(level);
        if (mob == null) throw new AssertionError("Yuji entity type creates a candidate");
        mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        mob.setNoAi(true); mob.setNoGravity(true);
        return mob;
    }
    private static void zeroPending(GameTestHelper h, BlockPos pos) {
        var state = SpawnCaps.snapshot(h.getLevel(), pos);
        h.assertTrue(state.pending() == 0 && state.chunkPending() == 0, "no leaked reservation: " + state);
    }
    private static java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> summonFloor(ServerLevel level, BlockPos pos) {
        var saved = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (BlockPos cursor : BlockPos.betweenClosed(pos.offset(-6, -1, -6), pos.offset(8, 6, 6))) {
            saved.put(cursor.immutable(), level.getBlockState(cursor));
            level.setBlockAndUpdate(cursor, cursor.getY() == pos.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        return saved;
    }
    private static void removeSummons(ServerLevel level, YujiMonster... parents) {
        for (Entity entity : RainCitySpawns.snapshot(level)) if (entity instanceof YujiMonster mob)
            for (YujiMonster parent : parents) if (parent.getUUID().equals(mob.summoner())) mob.discard();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capScalingDisabledZeroFloorAndOverflow(GameTestHelper h) {
        h.assertTrue(SpawnCapConfig.effectiveLimit(-1, -1, 128, 10) == -1, "both dimension bounds can be disabled");
        h.assertTrue(SpawnCapConfig.effectiveLimit(512, -1, 128, 10) == 512, "disabled scaling keeps fixed ceiling");
        h.assertTrue(SpawnCapConfig.effectiveLimit(512, 0, 128, 100) == 0, "zero scaling blocks even with floor");
        h.assertTrue(SpawnCapConfig.effectiveLimit(0, 2, 128, 100) == 0, "zero ceiling blocks");
        h.assertTrue(SpawnCapConfig.effectiveLimit(512, 2, 128, 10) == 128, "minimum protects small loaded sets");
        h.assertTrue(SpawnCapConfig.effectiveLimit(512, 2, 128, 100) == 200, "positive scaling uses loaded chunks");
        h.assertTrue(SpawnCapConfig.effectiveLimit(512, 2, 128, 1000) == 512, "fixed ceiling still applies");
        h.assertTrue(SpawnCapConfig.effectiveLimit(-1, 1_000_000, 0, Integer.MAX_VALUE) == Integer.MAX_VALUE, "multiplication cannot overflow negative");
        h.assertTrue(SpawnCapConfig.effectiveLimit(-1, 2, 128, -1) == 128, "negative input cannot create negative capacity");
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capJointReservationBoundariesAndRollback(GameTestHelper h) {
        var level = h.getLevel(); var pos = position(h);
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            var before = SpawnCaps.snapshot(level, pos);
            limits(before.entities() + 2, before.chunkEntities() + 1);
            try (var first = SpawnCaps.tryReserve(level, type(), pos)) {
                h.assertTrue(first != null, "cap-1 admits one");
                h.assertTrue(SpawnCaps.tryReserve(level, type(), pos) == null, "same chunk at cap rejects without consuming dimension room");
                try (var second = SpawnCaps.tryReserve(level, type(), pos.offset(16, 0, 0))) {
                    h.assertTrue(second != null, "different chunk can use the remaining dimension slot");
                    h.assertTrue(SpawnCaps.tryReserve(level, type(), pos.offset(32, 0, 0)) == null, "dimension at cap rejects cap+1");
                    var full = SpawnCaps.snapshot(level, pos);
                    h.assertTrue(full.pending() == 2 && full.chunkPending() == 1, "joint reservations are exact");
                }
            }
            zeroPending(h, pos);
            try {
                try (var reservation = SpawnCaps.tryReserve(level, type(), pos)) {
                    h.assertTrue(reservation != null, "rollback frees capacity");
                    throw new IllegalArgumentException("simulated candidate initialization failure");
                }
            } catch (IllegalArgumentException expected) {
                h.assertTrue(expected.getMessage().startsWith("simulated"), "only the intended failure is caught");
            }
            zeroPending(h, pos);
            var again = SpawnCaps.tryReserve(level, type(), pos);
            h.assertTrue(again != null, "exception rollback frees capacity");
            again.close(); again.close();
            zeroPending(h, pos);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capRealInsertionCommitMovementAndRemoval(GameTestHelper h) {
        var level = h.getLevel(); var pos = position(h); var other = pos.offset(16, 0, 0);
        level.getChunk(other);
        YujiMonster mob = candidate(level, pos, YujiKind.YUCHI);
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            var before = SpawnCaps.snapshot(level, pos); var otherBefore = SpawnCaps.snapshot(level, other);
            limits(before.entities() + 1, before.chunkEntities() + 1);
            try (var reservation = SpawnCaps.tryReserve(level, type(), pos)) {
                h.assertTrue(reservation != null, "reserve the last slot");
                h.assertTrue(!reservation.commit(mob), "uninserted candidate is not success");
                h.assertTrue(level.addFreshEntity(mob), "real insertion succeeds");
                h.assertTrue(reservation.commit(mob) && reservation.commit(mob), "commit is idempotent");
                SpawnCaps.join(new EntityJoinLevelEvent(mob, level));
                var added = SpawnCaps.snapshot(level, pos);
                h.assertTrue(added.entities() == before.entities() + 1 && added.chunkEntities() == before.chunkEntities() + 1,
                        "join plus explicit commit counts once: " + added);
                h.assertTrue(SpawnCaps.tryReserve(level, type(), other) == null, "committed last slot is unavailable");
            }
            mob.moveTo(other.getX() + .5, other.getY(), other.getZ() + .5);
            h.assertTrue(SpawnCaps.snapshot(level, pos).chunkEntities() == before.chunkEntities(), "moving releases the old chunk");
            h.assertTrue(SpawnCaps.snapshot(level, other).chunkEntities() == otherBefore.chunkEntities() + 1, "moving charges the new chunk");
            mob.discard();
            h.assertTrue(SpawnCaps.snapshot(level, pos).entities() == before.entities(), "discard releases dimension capacity");
            zeroPending(h, pos);
        } finally { mob.discard(); }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capCancelledJoinDoesNotCreatePhantomSuccess(GameTestHelper h) {
        var level = h.getLevel(); var pos = position(h);
        YujiMonster mob = candidate(level, pos, YujiKind.YUCHI);
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() == mob) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, cancel);
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            var before = SpawnCaps.snapshot(level, pos);
            limits(before.entities() + 1, -1);
            try (var reservation = SpawnCaps.tryReserve(level, type(), pos)) {
                h.assertTrue(reservation != null, "reserve before cancellable join");
                h.assertTrue(!level.addFreshEntity(mob), "Forge listener cancels actual insertion");
                h.assertTrue(!reservation.commit(mob), "cancelled event cannot count as success");
            }
            h.assertTrue(SpawnCaps.snapshot(level, pos).entities() == before.entities(), "cancelled join not counted");
            zeroPending(h, pos);
        } finally { MinecraftForge.EVENT_BUS.unregister(cancel); mob.discard(); }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capNegativeChunkCoordinatesAndMismatch(GameTestHelper h) {
        var level = h.getLevel(); var negative = new BlockPos(-1, 150, -1);
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            limits(-1, SpawnCaps.snapshot(level, negative).chunkEntities() + 1);
            try (var reservation = SpawnCaps.tryReserve(level, type(), negative)) {
                h.assertTrue(reservation != null, "negative chunk can reserve");
                h.assertTrue(SpawnCaps.tryReserve(level, type(), new BlockPos(-16, 150, -16)) == null, "-1 and -16 share physical chunk -1,-1");
                try (var next = SpawnCaps.tryReserve(level, type(), new BlockPos(-17, 150, -17))) {
                    h.assertTrue(next != null, "-17 belongs to the next negative chunk");
                }
            }
            var pos = position(h); var shifted = pos.offset(16, 0, 0);
            level.getChunk(shifted);
            YujiMonster mob = candidate(level, shifted, YujiKind.YUCHI);
            limits(-1, -1);
            try (var reservation = SpawnCaps.tryReserve(level, type(), pos)) {
                h.assertTrue(level.addFreshEntity(mob), "candidate inserted into a different chunk");
                h.assertTrue(!reservation.commit(mob), "reservation cannot be transferred to another chunk");
            } finally { mob.discard(); }
            zeroPending(h, negative);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capZeroDisabledAndConfigReenableNeverCull(GameTestHelper h) {
        var level = h.getLevel(); var pos = position(h); YujiMonster mob = candidate(level, pos, YujiKind.YUCHI);
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            limits(0, -1);
            h.assertTrue(SpawnCaps.tryReserve(level, type(), pos) == null, "zero dimension blocks");
            limits(-1, 0);
            h.assertTrue(SpawnCaps.tryReserve(level, type(), pos) == null, "zero chunk blocks");
            limits(-1, -1);
            try (var reservation = SpawnCaps.tryReserve(level, type(), pos)) {
                h.assertTrue(reservation != null && level.addFreshEntity(mob) && reservation.commit(mob), "disabled limits preserve insertion");
            }
            limits(0, 0);
            h.assertTrue(!mob.isRemoved() && level.getEntity(mob.getUUID()) == mob, "reenabling below population does not cull");
            h.assertTrue(SpawnCaps.tryReserve(level, type(), pos) == null, "reenabled zero blocks only new admission");
            zeroPending(h, pos);
        } finally { mob.discard(); }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capStoryExceptionsAndManualPopulation(GameTestHelper h) {
        var level = h.getLevel(); var pos = position(h);
        YujiMonster guardian = candidate(level, pos, YujiKind.YUCHI);
        guardian.getPersistentData().putBoolean("YujiGuardian", true);
        YujiMonster manual = candidate(level, pos, YujiKind.YUCHI);
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            var before = SpawnCaps.snapshot(level, pos); limits(0, 0);
            h.assertTrue(level.addFreshEntity(guardian) && level.addFreshEntity(manual), "manual and story insertions are not cancelled by caps");
            h.assertTrue(SpawnCaps.snapshot(level, pos).entities() == before.entities() + 1, "manual controlled entity counts; guardian does not");
            for (var exempt : List.of(EntityType.COW, BrickrotContent.WALL.get(), YujiContent.MOBS.get(YujiKind.SHIFOTOU).get())) {
                try (var reservation = SpawnCaps.tryReserve(level, exempt, pos)) {
                    h.assertTrue(reservation != null, "story and unrelated types remain exempt: " + exempt);
                }
            }
            zeroPending(h, pos);
        } finally { guardian.discard(); manual.discard(); }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capDimensionsDoNotShareReservations(GameTestHelper h) {
        var level = h.getLevel(); var other = level.getServer().getLevel(Level.NETHER); var pos = position(h);
        h.assertTrue(other != null, "Nether available");
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            limits(-1, 1);
            // No entities are inserted: distant positions only identify independent chunk budgets.
            var empty = new BlockPos(30_000_000 - 32, 150, 0);
            try (var first = SpawnCaps.tryReserve(level, type(), empty); var second = SpawnCaps.tryReserve(other, type(), empty)) {
                h.assertTrue(first != null && second != null, "same coordinates in different dimensions have separate budgets");
                h.assertTrue(SpawnCaps.snapshot(level, empty).chunkPending() == 1 && SpawnCaps.snapshot(other, empty).chunkPending() == 1,
                        "each dimension owns one pending admission");
            }
            zeroPending(h, pos);
            h.assertTrue(SpawnCaps.snapshot(other, empty).pending() == 0, "other dimension releases its reservation");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capSavedEntityReloadAboveLimitRemainsLegal(GameTestHelper h) {
        var level = h.getLevel(); var pos = position(h); YujiMonster original = candidate(level, pos, YujiKind.YUCHI);
        Entity reloaded = null;
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            var before = SpawnCaps.snapshot(level, pos); limits(0, 0);
            h.assertTrue(level.addFreshEntity(original), "manual spawn over zero cap remains legal");
            CompoundTag saved = new CompoundTag(); h.assertTrue(original.save(saved), "real entity NBT saved");
            UUID id = original.getUUID(); original.discard();
            reloaded = EntityType.loadEntityRecursive(saved, level, entity -> entity);
            h.assertTrue(reloaded != null && reloaded.getUUID().equals(id) && level.addFreshEntity(reloaded), "saved UUID can be loaded above configured cap");
            h.assertTrue(SpawnCaps.snapshot(level, pos).entities() == before.entities() + 1, "reload counted once");
            h.assertTrue(SpawnCaps.tryReserve(level, type(), pos) == null, "loaded population does not permit automatic overshoot");
            zeroPending(h, pos);
        } finally { original.discard(); if (reloaded != null) reloaded.discard(); }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capMultipleSummonersShareLastSlot(GameTestHelper h) {
        var level = h.getLevel(); var pos = position(h).above(64);
        YujiMonster first = candidate(level, pos, YujiKind.QIYU), second = candidate(level, pos.offset(2, 0, 0), YujiKind.QIYU);
        var saved = summonFloor(level, pos);
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            h.assertTrue(level.addFreshEntity(first) && level.addFreshEntity(second), "both summoners inserted");
            int before = SpawnCaps.snapshot(level, pos).entities(); limits(before + 1, -1);
            int made = 0;
            for (int cast = 0; cast < 8; cast++) made += first.summonAllies() + second.summonAllies();
            h.assertTrue(made == 1 && first.livingSummons() + second.livingSummons() == 1, "two parents jointly admit exactly one actual child: " + made);
            h.assertTrue(SpawnCaps.snapshot(level, pos).entities() == before + 1, "actual population consumes the last slot");
            zeroPending(h, pos);
        } finally {
            removeSummons(level, first, second);
            first.discard(); second.discard(); saved.forEach(level::setBlockAndUpdate);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capDeferredRainQueuesPreserveBalancesAndRotate(GameTestHelper h) {
        var level = h.getLevel(); var pos = position(h);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CapQueue"));
        player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        var data = new RainCityData(); var state = new CompoundTag();
        state.putInt("Horde", 3); state.putInt("WaveChennu", 2); state.putInt("WaveGreed", 4);
        state.putInt("AmbientGroup", 3); state.putInt("AmbientKind", YujiKind.YUCHI.ordinal());
        state.putInt("AmbientAttempts", 1); state.putLong("LastWaveAt", level.getGameTime());
        data.players.put(player.getUUID(), state);
        var difficulty = level.getDifficulty(); boolean spawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC); var rain = GameTestConfigs.sandbox(RainCityConfig.SPEC)) {
            limits(0, -1); RainCityConfig.SPAWN_BUDGET.set(3); RainCityConfig.CAP.set(2000);
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, level.getServer());
            for (int round = 0; round < 4; round++) {
                RainCitySpawns.tick(level, data, List.of(player), 0);
                h.assertTrue(state.getInt("SpawnCursor") == (3 * (round + 1)) % 4, "round-robin continues under full cap");
                h.assertTrue(state.getInt("Horde") == 3 && state.getInt("WaveChennu") == 2 && state.getInt("WaveGreed") == 4
                        && state.getInt("AmbientGroup") == 3, "cap backpressure preserves every queue balance: " + state);
            }
            zeroPending(h, pos);
        } finally {
            level.getServer().setDifficulty(difficulty, true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning, level.getServer());
            data.players.clear();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capOneSnapshotForRepeatedSameTickAdmissions(GameTestHelper h) {
        var level = h.getLevel(); var pos = position(h);
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            limits(-1, 1000000);
            long scans = SpawnCaps.snapshot(level, pos).scans();
            for (int i = 0; i < 32; i++) {
                try (var reservation = SpawnCaps.tryReserve(level, type(), pos)) {
                    h.assertTrue(reservation != null, "sequential rolled-back candidate fits");
                }
            }
            h.assertTrue(SpawnCaps.snapshot(level, pos).scans() == scans, "same-tick attempts do not each scan all entities");
            zeroPending(h, pos);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capShadowNeonAndYujiShareOneBudget(GameTestHelper h) {
        var level = h.getLevel(); var pos = position(h);
        var shadow = ModEntities.SHADOW_TANGLER.get().create(level);
        var neon = ModEntities.NEON_TUMOR.get().create(level);
        h.assertTrue(shadow != null && neon != null, "both city types create candidates");
        shadow.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        neon.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        shadow.setNoAi(true); shadow.setNoGravity(true); neon.setNoAi(true); neon.setNoGravity(true);
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            var before = SpawnCaps.snapshot(level, pos);
            limits(before.entities() + 1, before.chunkEntities() + 1);
            try (var reservation = SpawnCaps.tryReserve(level, shadow.getType(), pos)) {
                h.assertTrue(reservation != null && level.addFreshEntity(shadow) && reservation.commit(shadow), "shadow takes the last slot");
            }
            try (var yuji = SpawnCaps.tryReserve(level, type(), pos);
                 var blockedNeon = SpawnCaps.tryReserve(level, neon.getType(), pos)) {
                h.assertTrue(yuji == null && blockedNeon == null, "Yuji and neon cannot bypass the shared city budget");
            }
            shadow.discard();
            try (var reservation = SpawnCaps.tryReserve(level, neon.getType(), pos)) {
                h.assertTrue(reservation != null && level.addFreshEntity(neon) && reservation.commit(neon), "neon can consume the released shadow slot");
            }
            h.assertTrue(SpawnCaps.snapshot(level, pos).entities() == before.entities() + 1, "mixed types counted once each");
            zeroPending(h, pos);
        } finally { shadow.discard(); neon.discard(); }
        h.succeed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "spawn_caps")
    public static void capCancelledRealSummonsReportZeroAndRecover(GameTestHelper h) {
        var level = h.getLevel(); var pos = position(h).above(64);
        YujiMonster parent = candidate(level, pos, YujiKind.QIYU);
        var saved = summonFloor(level, pos);
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof YujiMonster mob && parent.getUUID().equals(mob.summoner())) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, cancel);
        try (var config = GameTestConfigs.sandbox(SpawnCapConfig.SPEC)) {
            h.assertTrue(level.addFreshEntity(parent), "parent inserted");
            int before = SpawnCaps.snapshot(level, pos).entities(); limits(before + 4, -1);
            h.assertTrue(parent.summonAllies() == 0 && parent.livingSummons() == 0, "void insertion with cancelled joins reports no successful summons");
            h.assertTrue(SpawnCaps.snapshot(level, pos).entities() == before, "failed group adds no phantom population");
            zeroPending(h, pos);
            MinecraftForge.EVENT_BUS.unregister(cancel);
            h.assertTrue(parent.summonAllies() > 0, "capacity is usable after cancelling listener is removed");
            zeroPending(h, pos);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel); removeSummons(level, parent);
            parent.discard(); saved.forEach(level::setBlockAndUpdate);
        }
        h.succeed();
    }
    private SpawnCapGameTests() { }
}
