package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.yuji.YujiConfig;
import com.zeropointsix.eraser.yuji.flare.FlareConfig;
import com.zeropointsix.eraser.yuji.flare.FlareLights;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("flare_permission_tests")
@PrefixGameTestTemplate(false)
public final class FlarePermissionGameTests {
    private static Fixture fixture;

    private static final class Fixture {
        final ServerLevel level;
        final BlockPos center, foreign;
        final Player owner, visitor;
        final FlareLights data;
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final Set<Long> tickets = new HashSet<>();

        Fixture(GameTestHelper h) {
            level = h.getLevel();
            BlockPos reference = h.absolutePos(new BlockPos(4, 4, 4));
            for (int x = (reference.getX() - 4) >> 4; x <= (reference.getX() + 4) >> 4; x++)
                for (int z = (reference.getZ() - 4) >> 4; z <= (reference.getZ() + 4) >> 4; z++) {
                    long key = ChunkPos.asLong(x, z);
                    if (!level.getForcedChunks().contains(key)) { level.setChunkForced(x, z, true); tickets.add(key); }
                    level.getChunk(x, z);
                }
            int surface = reference.getY();
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
                surface = Math.max(surface, level.getHeight(Heightmap.Types.MOTION_BLOCKING, reference.getX() + x, reference.getZ() + z));
            center = new BlockPos(reference.getX(), surface + 5, reference.getZ());
            h.assertTrue(center.getY() + 4 < level.getMaxBuildHeight(), "permission fixture fits build height");
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -4, -4), center.offset(4, 4, 4)))
                set(pos, Blocks.AIR.defaultBlockState());
            foreign = center.above(2);
            set(foreign, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
            owner = h.makeMockSurvivalPlayer(); visitor = h.makeMockSurvivalPlayer();
            data = FlareLights.get(level);
        }

        void set(BlockPos pos, BlockState state) {
            previous.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, 16);
        }

        void illuminate(Player player) {
            try (var config = GameTestConfigs.sandbox(YujiConfig.SPEC)) {
                FlareConfig.LIGHT_RADIUS.set(3); FlareConfig.YELLOW_LIFETIME.set(60);
                data.illuminate(level, center, player);
            }
        }

        Map<BlockPos, Long> deadlines() {
            Map<BlockPos, Long> result = new LinkedHashMap<>();
            for (Tag entry : data.save(new CompoundTag()).getList("Lights", Tag.TAG_COMPOUND)) {
                CompoundTag record = (CompoundTag) entry;
                BlockPos pos = BlockPos.of(record.getLong("Pos"));
                if (pos.distSqr(center) <= 9) result.put(pos, record.getLong("Expires"));
            }
            return result;
        }

        void close() {
            previous.forEach((pos, state) -> level.setBlock(pos, state, 16));
            data.cleanup(level);
            for (long key : tickets) { ChunkPos chunk = new ChunkPos(key); level.setChunkForced(chunk.x, chunk.z, false); }
        }
    }

    private static void close() { if (fixture != null) { fixture.close(); fixture = null; } }
    @AfterBatch(batch = "flare_permission_build") public static void afterBuild(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_permission_event") public static void afterEvent(ServerLevel level) { close(); }
    @AfterBatch(batch = "flare_permission_allowed") public static void afterAllowed(ServerLevel level) { close(); }

    private static void verifyRenewal(GameTestHelper h, boolean mayBuild, boolean denyEvent) {
        fixture = new Fixture(h);
        Fixture f = fixture;
        // Fix the observer geometry before lighting so the second shot only tests renewal.
        BlockPos source = new BlockPos(Math.floorDiv(f.center.getX() + 1, 3) * 3,
                Math.floorDiv(f.center.getY() + 1, 3) * 3, Math.floorDiv(f.center.getZ() + 1, 3) * 3);
        BlockPos observer = source.east();
        f.set(observer, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST));
        f.illuminate(f.owner);
        Map<BlockPos, Long> original = f.deadlines();
        h.assertTrue(original.containsKey(source) && !original.containsKey(f.foreign), "owner creates the observed managed light without claiming foreign light");
        h.assertTrue(f.level.getBlockTicks().hasScheduledTick(observer, Blocks.OBSERVER), "positive control: initial placement notifies the observer");
        long deadline = f.level.getGameTime() + 60;
        h.assertTrue(original.values().stream().allMatch(d -> d == deadline), "initial persisted deadlines match configured lifetime");
        f.visitor.getAbilities().mayBuild = mayBuild;
        boolean authorized = mayBuild && !denyEvent;
        int[] renewalEvents = {0};
        long[] renewedDeadline = {deadline};

        h.runAfterDelay(30, () -> {
            h.assertTrue(!f.level.getBlockState(observer).getValue(ObserverBlock.POWERED)
                    && !f.level.getBlockTicks().hasScheduledTick(observer, Blocks.OBSERVER), "initial observer pulse has settled before renewal");
            class RenewalProtectionGuard {
                @SubscribeEvent public void onRenewalPlace(BlockEvent.EntityPlaceEvent event) {
                    if (event.getEntity() == f.visitor && original.containsKey(event.getPos())) {
                        renewalEvents[0]++;
                        h.assertTrue(f.level.getBlockState(event.getPos()).is(Blocks.LIGHT), "renewal protection observes the existing light in place");
                        if (denyEvent) event.setCanceled(true);
                    }
                }
            }
            RenewalProtectionGuard guard = new RenewalProtectionGuard(); MinecraftForge.EVENT_BUS.register(guard);
            try { f.illuminate(f.visitor); } finally { MinecraftForge.EVENT_BUS.unregister(guard); }
            Map<BlockPos, Long> after = f.deadlines();
            renewedDeadline[0] = authorized ? f.level.getGameTime() + 60 : deadline;
            h.assertTrue(after.keySet().equals(original.keySet()), "overlap preserves source ownership and never claims foreign light");
            h.assertTrue(after.values().stream().allMatch(d -> d == renewedDeadline[0]),
                    "renewal respects authorization: mayBuild=" + mayBuild + " denyEvent=" + denyEvent
                            + " expectedDeadline=" + renewedDeadline[0] + " actual=" + after.values());
            h.assertTrue(renewalEvents[0] == (mayBuild ? original.size() : 0), "each allowed renewal reaches real Forge protection exactly once");
            h.assertTrue(original.keySet().stream().allMatch(pos -> f.level.getBlockState(pos).is(Blocks.LIGHT)), "renewal never removes existing lights");
            h.assertTrue(!f.level.getBlockTicks().hasScheduledTick(observer, Blocks.OBSERVER), "renewal does not schedule observer updates");
        });
        h.runAfterDelay(34, () -> h.assertTrue(!f.level.getBlockState(observer).getValue(ObserverBlock.POWERED), "renewal never pulses the observer"));
        h.runAfterDelay(58, () -> h.assertTrue(original.keySet().stream().allMatch(pos -> f.level.getBlockState(pos).is(Blocks.LIGHT)), "original lifetime is not shortened"));
        // The production LevelTickEvent cleans every ten ticks; no manual cleanup or clock injection.
        h.runAfterDelay(71, () -> {
            h.assertTrue(original.keySet().stream().allMatch(pos -> f.level.getBlockState(pos).is(Blocks.LIGHT) == authorized),
                    "only authorized renewal survives the original expiry and cleanup interval");
            h.assertTrue(authorized || f.deadlines().isEmpty(), "denied renewal records expire at the original deadline");
        });
        h.runAfterDelay(101, () -> {
            h.assertTrue(f.deadlines().isEmpty() && original.keySet().stream().allMatch(f.level::isEmptyBlock), "production cleanup removes all owned sources at the correct deadline");
            h.assertTrue(f.level.getBlockState(f.foreign).is(Blocks.LIGHT), "production cleanup preserves foreign light");
            System.out.println("FLARE_RENEWAL_PASSED mayBuild=" + mayBuild + " denyEvent=" + denyEvent
                    + " sources=" + original.size() + " protectionEvents=" + renewalEvents[0]
                    + " originalDeadline=" + deadline + " renewedDeadline=" + renewedDeadline[0]);
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "flare_permission_tests", template = "empty", batch = "flare_permission_build", timeoutTicks = 160)
    public static void flareYellowRenewalRejectsPlayerWithoutBuildPermission(GameTestHelper h) {
        verifyRenewal(h, false, false);
    }

    @GameTest(templateNamespace = "flare_permission_tests", template = "empty", batch = "flare_permission_event", timeoutTicks = 160)
    public static void flareYellowRenewalHonorsCanceledProtectionEvent(GameTestHelper h) {
        verifyRenewal(h, true, true);
    }

    @GameTest(templateNamespace = "flare_permission_tests", template = "empty", batch = "flare_permission_allowed", timeoutTicks = 160)
    public static void flareYellowAuthorizedRenewalExtendsWithoutBlockUpdates(GameTestHelper h) {
        verifyRenewal(h, true, false);
    }

    private FlarePermissionGameTests() { }
}
