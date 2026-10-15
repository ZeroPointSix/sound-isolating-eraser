package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.yuji.YujiAfflictions;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PrepareRamNearestTarget;
import net.minecraft.world.entity.ai.behavior.RamTarget;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("yuji_tests")
@PrefixGameTestTemplate(false)
public final class YujiBetrayalSpeciesGameTests {
    private static final List<GoatFixture> fixtures = new ArrayList<>();
    private static Difficulty previousDifficulty;
    private static boolean previousDifficultyLocked;

    @BeforeBatch(batch = "yuji_betrayal_species")
    public static void prepareSpeciesFixtures(ServerLevel level) {
        previousDifficulty = level.getDifficulty();
        previousDifficultyLocked = level.getLevelData().isDifficultyLocked();
        level.getServer().setDifficultyLocked(false);
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
    }

    @AfterBatch(batch = "yuji_betrayal_species")
    public static void restoreSpeciesFixtures(ServerLevel level) {
        try {
            fixtures.forEach(GoatFixture::close);
        } finally {
            fixtures.clear();
            if (previousDifficulty != null) {
                level.getServer().setDifficulty(previousDifficulty, true);
                level.getServer().setDifficultyLocked(previousDifficultyLocked);
                previousDifficulty = null;
            }
        }
    }

    private static final class GoatFixture {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos origin;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        final ServerPlayer player;
        final ServerPlayer witness;
        final Cow cow;
        final List<YujiMonster> sources = new ArrayList<>();
        final List<Goat> reloaded = new ArrayList<>();
        final List<Object> listeners = new ArrayList<>();
        Goat goat;
        Vec3 goatStart;
        long beginTime;
        long cooldownDeadline;
        int ramTick = -1;
        boolean sawVictimPreparation;
        boolean closed;

        GoatFixture(GameTestHelper h, int layer) {
            helper = h;
            level = h.getLevel();
            BlockPos template = h.absolutePos(BlockPos.ZERO);
            origin = new BlockPos(template.getX(), level.getMaxBuildHeight() - 16 - layer * 40, template.getZ());
            for (BlockPos relative : BlockPos.betweenClosed(0, 0, 0, 15, 5, 15)) {
                BlockPos pos = origin.offset(relative).immutable();
                original.put(pos, level.getBlockState(pos));
                level.setBlock(pos, (relative.getY() == 0 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            }
            player = createPlayer("YujiGoatVictim", 8.5, 5.5);
            witness = createPlayer("YujiGoatWitness", 5.5, 10.5);
            cow = EntityType.COW.create(level);
            h.assertTrue(cow != null, "vanilla distraction cow is available");
            cow.moveTo(origin.getX() + 11.5, origin.getY() + 1, origin.getZ() + 10.5);
            cow.setNoAi(true);
            cow.setPersistenceRequired();
            h.assertTrue(level.addFreshEntity(cow), "distraction cow joins the actual level");
            fixtures.add(this);
            for (int tick = 1; tick < 600; tick++) {
                final int currentTick = tick;
                h.runAtTickTime(tick, () -> {
                    if (closed) return;
                    // Ordinary server players are ticked here; this is a server integration test, not a TCP client.
                    player.tick();
                    player.doTick();
                    witness.tick();
                    witness.doTick();
                    if (goat == null) return;
                    h.assertTrue(goat.isAlive() && !goat.isNoAi() && !goat.isNoGravity(), "goat keeps live AI and gravity");
                    h.assertTrue(level.noCollision(goat), "goat never phases through fixture geometry");
                    if (goat.getBrain().getRunningBehaviors().stream().anyMatch(b -> b instanceof PrepareRamNearestTarget<?>)) {
                        var look = goat.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).orElse(null);
                        if (look != null && goat.getPersistentData().contains("QihaiBetrayal")) {
                            h.assertTrue(look.currentPosition().distanceToSqr(player.getEyePosition()) < .01,
                                    "native preparation must select the victim, not the nearer cow or witness");
                            sawVictimPreparation = true;
                        }
                    }
                    if (goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET) && ramTick < 0) {
                        ramTick = currentTick;
                        h.assertTrue(sawVictimPreparation, "charge follows actual victim-targeted preparation");
                        h.assertTrue(level.getGameTime() - beginTime >= 20, "native ram includes its 20-tick preparation");
                        Vec3 ram = goat.getBrain().getMemory(MemoryModuleType.RAM_TARGET).orElseThrow();
                        h.assertTrue(ram.distanceToSqr(player.position()) < 2,
                                "the naturally chosen charge endpoint is at the victim, not a distraction");
                    }
                    if (player.getHealth() < 20) h.assertTrue(ramTick >= 0,
                            "damage follows native preparation and charge, not synthetic melee");
                    h.assertTrue(cow.getHealth() == cow.getMaxHealth() && witness.getHealth() == 20,
                            "off-path cow and unaffected witness are not attacked");
                    if (currentTick % 20 == 0) System.out.println("YUJI_GOAT_TRACE tick=" + currentTick
                            + ", pos=" + goat.position() + ", ram=" + goat.getBrain().getMemory(MemoryModuleType.RAM_TARGET)
                            + ", cooldown=" + goat.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS)
                            + ", health=" + player.getHealth() + ", influenced=" + goat.getPersistentData().contains("QihaiBetrayal")
                            + ", senses=" + sensorStatus());
                });
            }
        }

        private ServerPlayer createPlayer(String name, double x, double z) {
            ServerPlayer created = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
            created.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), created);
            created.setGameMode(GameType.SURVIVAL);
            created.moveTo(origin.getX() + x, origin.getY() + 1, origin.getZ() + z);
            created.getFoodData().setFoodLevel(17);
            level.addNewPlayer(created);
            helper.assertTrue(level.getPlayerByUUID(created.getUUID()) == created, "ordinary server player is registered in this level");
            return created;
        }

        void spawnGoat() {
            goat = EntityType.GOAT.create(level);
            helper.assertTrue(goat != null, "vanilla goat entity is available");
            goat.moveTo(origin.getX() + 8.5, origin.getY() + 1, origin.getZ() + 10.5);
            goat.setPersistenceRequired();
            goat.setScreamingGoat(false);
            goat.getBrain().setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, 1000);
            goat.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, 1000);
            helper.assertTrue(level.addFreshEntity(goat), "vanilla goat joins the real server level");
        }

        String sensorStatus() {
            var visible = goat.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(null);
            return "present=" + (visible != null)
                    + "/onGround=" + goat.onGround()
                    + "/victim=" + (visible != null && visible.contains(player))
                    + "/cow=" + (visible != null && visible.contains(cow))
                    + "/witness=" + (visible != null && visible.contains(witness))
                    + "/targetable=" + TargetingConditions.forCombat().test(goat, player)
                    + "/victimPos=" + player.position();
        }

        void sensorsReady() {
            var visible = goat.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(null);
            helper.assertTrue(goat.onGround(),
                    "real goat must finish falling onto the runway before native path preparation: " + sensorStatus());
            helper.assertTrue(visible != null && visible.contains(player) && visible.contains(cow) && visible.contains(witness),
                    "actual goat sensor must see victim, cow and witness before recruitment: " + sensorStatus());
            helper.assertTrue(TargetingConditions.forCombat().test(goat, player),
                    "victim is eligible for native combat targeting before recruitment: " + sensorStatus());
            helper.assertTrue(goat.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS).orElse(0) > 0,
                    "native idle cooldown remains held during sensor warm-up");
            System.out.println("YUJI_GOAT_SENSOR_READY pos=" + goat.position() + ", " + sensorStatus());
        }

        void begin(boolean multipleSources) {
            sensorsReady();
            // Re-establish the measured starting pose after live idle AI warms its sensors, before any attack.
            goat.moveTo(origin.getX() + 8.5, origin.getY() + 1, origin.getZ() + 10.5);
            goat.setDeltaMovement(Vec3.ZERO);
            goat.getNavigation().stop();
            helper.assertTrue(goat.distanceToSqr(cow) < goat.distanceToSqr(player)
                            && goat.distanceToSqr(witness) < goat.distanceToSqr(player),
                    "both distractions start nearer than the affected player");
            helper.assertTrue(goat.hasLineOfSight(cow) && goat.hasLineOfSight(witness) && goat.hasLineOfSight(player),
                    "victim and distractions are all visible, so selection cannot pass by occlusion");
            goatStart = goat.position();
            beginTime = level.getGameTime();
            cooldownDeadline = beginTime + goat.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS).orElseThrow();
            for (int i = 0; i < (multipleSources ? 2 : 1); i++) {
                YujiMonster source = YujiContent.MOBS.get(YujiKind.LIANGSHE).get().create(level);
                helper.assertTrue(source != null, "Liangshe source entity is available");
                source.moveTo(origin.getX() + 2.5, origin.getY() + 1, origin.getZ() + 2.5 + i);
                source.setNoAi(true);
                helper.assertTrue(level.addFreshEntity(source), "Liangshe source joins the server level");
                sources.add(source);
                YujiAfflictions.apply(player, source, "betrayed");
            }
            YujiAfflictions.recruit(player, true);
            YujiAfflictions.recruit(player, true);
            helper.assertTrue(goat.goalSelector.getAvailableGoals().stream().noneMatch(g ->
                            g.getGoal().getClass().getEnclosingClass() == YujiAfflictions.class),
                    "betrayed goat must not receive synthetic melee");
            helper.assertTrue(!goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_COOLDOWN_TICKS),
                    "first recruitment releases the idle cooldown for native preparation");
            helper.assertTrue(goat.getPersistentData().getCompound("QihaiBetrayal").getLong("GoatRamPreviousCooldownUntil")
                            == cooldownDeadline,
                    "repeat recruitment preserves the original cooldown deadline");
        }

        void killSource(int index) {
            YujiMonster source = sources.get(index);
            helper.assertTrue(source.hurt(source.damageSources().genericKill(), 10000), "source is killed through actual damage");
            helper.assertTrue(!source.isAlive(), "source really died");
        }

        void blockPreparedStart() {
            var walk = goat.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
            BlockPos pos = BlockPos.containing(walk.getTarget().currentPosition());
            helper.assertTrue(original.containsKey(pos) && original.containsKey(pos.above()), "ram start is inside the recorded fixture");
            helper.assertTrue(!goat.getBoundingBox().intersects(new AABB(pos)), "changed terrain does not overlap the live goat");
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
        }

        void restored() {
            helper.assertTrue(!player.hasEffect(YujiContent.BETRAYED.get()), "last source death clears betrayal");
            helper.assertTrue(!goat.getPersistentData().contains("QihaiBetrayal"), "goat influence ownership is removed");
            helper.assertTrue(!goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET), "owned charge memory is cancelled");
            helper.assertTrue(goat.getBrain().getRunningBehaviors().stream().noneMatch(behavior ->
                    behavior instanceof PrepareRamNearestTarget<?> || behavior instanceof RamTarget), "owned native ram behaviors are stopped");
            long remaining = cooldownDeadline - level.getGameTime();
            helper.assertTrue(goat.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS).orElse(0) >= remaining - 2,
                    "previous ram cooldown is restored with elapsed time, never reset to zero");
        }

        void close() {
            if (closed) return;
            closed = true;
            if (goat != null) { YujiAfflictions.restore(goat); goat.discard(); }
            reloaded.forEach(loaded -> { YujiAfflictions.restore(loaded); loaded.discard(); });
            listeners.forEach(MinecraftForge.EVENT_BUS::unregister);
            sources.forEach(YujiMonster::discard);
            player.discard();
            witness.discard();
            cow.discard();
            original.forEach((pos, state) -> level.setBlock(pos, state, 3));
        }
    }

    public static void naturallyRamsAndLastSourceRestores(GameTestHelper h) {
        GoatFixture fixture = new GoatFixture(h, 0);
        float[] healthAfter = {20};
        long[] nativeCooldownUntil = {0};
        h.startSequence().thenIdle(80).thenExecute(fixture::spawnGoat).thenWaitUntil(fixture::sensorsReady)
                .thenExecute(() -> fixture.begin(true)).thenWaitUntil(() -> {
            h.assertTrue(fixture.ramTick >= 0, "betrayed goat must naturally prepare a native charge");
        }).thenExecute(() -> {
            fixture.killSource(0);
            h.assertTrue(fixture.player.hasEffect(YujiContent.BETRAYED.get()), "one remaining source keeps betrayal active");
            h.assertTrue(fixture.goat.getPersistentData().contains("QihaiBetrayal"), "one source must not restore the goat early");
        }).thenWaitUntil(() -> {
            h.assertTrue(fixture.player.getHealth() < 20, "native ram must cause actual server-player health loss");
            h.assertTrue(fixture.player.getLastDamageSource() != null
                            && fixture.player.getLastDamageSource().getEntity() == fixture.goat,
                    "actual damage source is the living vanilla goat");
            h.assertTrue(fixture.goat.distanceToSqr(fixture.goatStart) > 9, "goat physically travelled into its victim");
            h.assertTrue(fixture.goat.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS).orElse(0) >= 590,
                    "finished native charge creates the ordinary non-screaming goat cooldown");
        }).thenExecute(() -> {
            healthAfter[0] = fixture.player.getHealth();
            nativeCooldownUntil[0] = fixture.level.getGameTime()
                    + fixture.goat.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS).orElseThrow();
        }).thenExecuteFor(60, () -> {
            int remaining = fixture.goat.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS).orElse(0);
            h.assertTrue(Math.abs(fixture.level.getGameTime() + remaining - nativeCooldownUntil[0]) <= 2,
                    "20-tick recruitment neither erases nor restarts the native post-ram cooldown");
            h.assertTrue(!fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET), "cooldown prevents another charge");
            h.assertTrue(fixture.player.getHealth() == healthAfter[0], "cooldown prevents repeated synthetic melee damage");
        }).thenExecute(() -> {
            fixture.killSource(1);
            fixture.restored();
        }).thenExecuteFor(60, () -> {
            fixture.restored();
            h.assertTrue(fixture.player.getHealth() == healthAfter[0], "no delayed ram damage after final source death");
        }).thenExecute(fixture::close).thenSucceed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_betrayal_species", timeoutTicks = 400)
    public static void betrayedGoatCancelledPreparationRestoresCooldown(GameTestHelper h) {
        GoatFixture fixture = new GoatFixture(h, 1);
        h.startSequence().thenIdle(80).thenExecute(fixture::spawnGoat).thenWaitUntil(fixture::sensorsReady)
                .thenExecute(() -> fixture.begin(false)).thenWaitUntil(() -> {
            h.assertTrue(fixture.sawVictimPreparation, "goat naturally enters victim-targeted preparation");
            h.assertTrue(fixture.goat.getBrain().getRunningBehaviors().stream()
                            .anyMatch(behavior -> behavior instanceof PrepareRamNearestTarget<?>),
                    "betrayed goat must enter vanilla preparation, not ordinary melee");
            h.assertTrue(fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "native preparation requests an actual approach path");
        }).thenExecute(() -> {
            h.assertTrue(!fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET), "abort occurs before charge is ready");
            fixture.killSource(0);
            fixture.restored();
            h.assertTrue(!fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                            && !fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                    "cancelled preparation leaves no owned walk or look target");
        }).thenExecuteFor(60, () -> {
            fixture.restored();
            h.assertTrue(fixture.player.getHealth() == 20, "cancelled native preparation never damages the player");
        }).thenExecute(fixture::close).thenSucceed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_betrayal_species", timeoutTicks = 450)
    public static void betrayedGoatCancelledReadyChargeRestoresCooldown(GameTestHelper h) {
        GoatFixture fixture = new GoatFixture(h, 2);
        h.startSequence().thenIdle(80).thenExecute(fixture::spawnGoat).thenWaitUntil(fixture::sensorsReady)
                .thenExecute(() -> fixture.begin(false)).thenWaitUntil(() -> {
            h.assertTrue(fixture.ramTick >= 0 && fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET),
                    "native preparation must create the live owned charge memory before cancellation");
        }).thenExecute(() -> {
            h.assertTrue(fixture.player.getHealth() == 20, "source is removed before the charge hits");
            fixture.killSource(0);
            fixture.restored();
            h.assertTrue(!fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                            && !fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                    "cancelling a live native charge clears its owned movement and look targets");
        }).thenExecuteFor(60, () -> {
            fixture.restored();
            h.assertTrue(fixture.player.getHealth() == 20, "cancelled live charge never inflicts a delayed hit");
        }).thenExecute(fixture::close).thenSucceed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_betrayal_species", timeoutTicks = 500)
    public static void betrayedGoatTimedOutPreparationCleansOnlyOwnedTargets(GameTestHelper h) {
        GoatFixture stale = new GoatFixture(h, 3);
        GoatFixture replaced = new GoatFixture(h, 4);
        List<GoatFixture> pair = List.of(stale, replaced);
        boolean[] blocked = new boolean[2];
        for (int tick = 81; tick < 400; tick++) h.runAtTickTime(tick, () -> {
            for (int i = 0; i < pair.size(); i++) {
                GoatFixture fixture = pair.get(i);
                if (blocked[i] || fixture.closed || fixture.goat == null) continue;
                if (fixture.sawVictimPreparation && fixture.goat.getBrain().getRunningBehaviors().stream()
                        .anyMatch(behavior -> behavior instanceof PrepareRamNearestTarget<?>)
                        && fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                        && !fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET)) {
                    fixture.blockPreparedStart();
                    blocked[i] = true;
                }
            }
        });
        h.startSequence().thenIdle(80).thenExecute(() -> pair.forEach(GoatFixture::spawnGoat))
                .thenWaitUntil(() -> pair.forEach(GoatFixture::sensorsReady))
                .thenExecute(() -> pair.forEach(fixture -> fixture.begin(false))).thenWaitUntil(() -> {
            h.assertTrue(blocked[0] && blocked[1], "terrain blocks both naturally chosen preparation starts");
        }).thenWaitUntil(() -> {
            for (GoatFixture fixture : pair) {
                h.assertTrue(fixture.level.getGameTime() - fixture.beginTime >= 160, "wait for the native preparation timeout");
                h.assertTrue(fixture.goat.getBrain().getRunningBehaviors().stream()
                                .noneMatch(behavior -> behavior instanceof PrepareRamNearestTarget<?> || behavior instanceof RamTarget),
                        "native preparation has actually stopped before source removal");
                h.assertTrue(fixture.goat.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS).orElse(0) > 0,
                        "vanilla failure produced its normal cooldown");
                h.assertTrue(!fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET), "unreachable preparation cannot charge");
            }
        }).thenExecute(() -> {
            h.assertTrue(stale.goat.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                    "negative control retains the actual native look memory after timeout");
            stale.killSource(0);
            stale.restored();
            h.assertTrue(!stale.goat.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET)
                            && !stale.goat.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "source removal clears the stopped preparation's residual owned targets");

            // Ownership counterexample, not a claimed naturally occurring behavior: newer values must survive.
            BlockPos other = replaced.origin.offset(4, 1, 4);
            WalkTarget walk = new WalkTarget(other, .8F, 0);
            BlockPosTracker look = new BlockPosTracker(other);
            replaced.goat.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walk);
            replaced.goat.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, look);
            replaced.killSource(0);
            replaced.restored();
            h.assertTrue(replaced.goat.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == walk,
                    "a newer unrelated walk target is not erased");
            h.assertTrue(replaced.goat.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).orElse(null) == look,
                    "a newer unrelated look target is not erased");
        }).thenExecuteFor(40, () -> {
            for (GoatFixture fixture : pair) {
                fixture.restored();
                h.assertTrue(fixture.player.getHealth() == 20, "failed preparation produces no delayed hit after removal");
            }
        }).thenExecute(() -> pair.forEach(GoatFixture::close)).thenSucceed();
    }

    @GameTest(templateNamespace = "yuji_tests", template = "empty", batch = "yuji_betrayal_species", timeoutTicks = 500)
    public static void betrayedGoatLegacyNbtRestoresWithoutRecruitment(GameTestHelper h) {
        GoatFixture fixture = new GoatFixture(h, 5);
        CompoundTag[] saved = {null};
        long[] originalDeadline = {0};
        long[] observedAt = {0};
        int[] restoredCooldown = {0};
        boolean[] observed = {false};
        boolean[] preserved = {false};
        h.startSequence().thenIdle(80).thenExecute(fixture::spawnGoat).thenWaitUntil(fixture::sensorsReady)
                .thenExecute(() -> fixture.begin(false)).thenWaitUntil(() -> {
            h.assertTrue(fixture.ramTick >= 0 && fixture.goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET),
                    "legacy-load regression starts from a real naturally prepared charge");
        }).thenExecute(() -> {
            saved[0] = fixture.goat.saveWithoutId(new CompoundTag());
            CompoundTag legacy = saved[0].getCompound("ForgeData").getCompound("QihaiBetrayal");
            h.assertTrue(legacy.hasUUID("Player") && legacy.contains("GoatRamPreviousCooldownUntil"),
                    "actual entity NBT contains the persisted victim and cooldown deadline");
            originalDeadline[0] = legacy.getLong("GoatRamPreviousCooldownUntil");
            legacy.remove("GoatRamOwned");
            fixture.goat.discard();
            fixture.goat = null;
            fixture.killSource(0);
            h.assertTrue(!fixture.player.hasEffect(YujiContent.BETRAYED.get()), "source is gone before the saved goat rejoins");
        }).thenIdle(2).thenExecute(() -> {
            Goat loaded = EntityType.GOAT.create(fixture.level);
            h.assertTrue(loaded != null, "ordinary goat is available for NBT reload");
            loaded.load(saved[0]);
            h.assertTrue(!loaded.getPersistentData().getCompound("QihaiBetrayal").getBoolean("GoatRamOwned"),
                    "fixture really uses the old schema without the new ownership flag");
            h.assertTrue(!loaded.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET)
                            && !loaded.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                            && !loaded.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                    "vanilla transient preparation memories are not fabricated across NBT reload");
            fixture.reloaded.add(loaded);
            h.assertTrue(fixture.level.addFreshEntity(loaded), "NBT-loaded goat rejoins through the actual level join event");
            h.assertTrue(loaded.getPersistentData().getCompound("QihaiBetrayal").getBoolean("GoatRamOwned"),
                    "joining migrates a legacy cooldown record without waiting for recruitment");
            h.assertTrue(!loaded.isNoAi() && !loaded.isNoGravity(), "reloaded goat retains ordinary AI and gravity");

            // A later behavior may supply fresh targets before the next real entity tick. They are not ours.
            WalkTarget walk = new WalkTarget(fixture.origin.offset(4, 1, 4), .8F, 0);
            BlockPosTracker look = new BlockPosTracker(fixture.origin.offset(4, 1, 4));
            Vec3 ram = Vec3.atBottomCenterOf(fixture.origin.offset(4, 1, 4));
            loaded.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walk);
            loaded.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, look);
            loaded.getBrain().setMemory(MemoryModuleType.RAM_TARGET, ram);
            Consumer<LivingEvent.LivingTickEvent> listener = event -> {
                if (event.getEntity() != loaded || observed[0] || loaded.getPersistentData().contains("QihaiBetrayal")) return;
                // Observe after production cleanup but before native Brain behaviors can consume the new values.
                observed[0] = true;
                observedAt[0] = fixture.level.getGameTime();
                restoredCooldown[0] = loaded.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS).orElse(0);
                preserved[0] = loaded.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == walk
                        && loaded.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).orElse(null) == look
                        && loaded.getBrain().getMemory(MemoryModuleType.RAM_TARGET).orElse(null) == ram;
            };
            fixture.listeners.add(listener);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, listener);
        }).thenWaitUntil(() -> {
            h.assertTrue(observed[0], "actual entity tick cleans the expired legacy influence without another recruitment");
        }).thenExecute(() -> {
            h.assertTrue(Math.abs(restoredCooldown[0] - (originalDeadline[0] - observedAt[0])) <= 2,
                    "legacy cleanup restores the remaining saved cooldown deadline");
            h.assertTrue(preserved[0], "legacy cleanup preserves all later unrelated target memories");
        }).thenExecuteFor(40, () -> {
            h.assertTrue(fixture.player.getHealth() == 20, "expired legacy influence never revives its pre-save charge");
        }).thenExecute(fixture::close).thenSucceed();
    }

    private YujiBetrayalSpeciesGameTests() {}
}
