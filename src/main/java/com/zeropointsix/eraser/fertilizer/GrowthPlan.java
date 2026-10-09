package com.zeropointsix.eraser.fertilizer;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

/** Vanilla tree features write to a bounded buffer; only a complete batch touches the world. */
public final class GrowthPlan {
    private static final int LIMIT = 16000;
    private static final Map<Method, String> OPERATIONS = operations();
    private final ServerLevel level;
    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private final Set<Long> ownedLogs = new HashSet<>();
    private Set<Long> currentTrunk;

    public GrowthPlan(ServerLevel level) { this.level = level; }
    public void allowLogs(Collection<Long> logs) { ownedLogs.addAll(logs); }
    private void check(BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos))
            throw new UnsafePlacement();
    }
    public BlockState get(BlockPos pos) { check(pos); return blocks.getOrDefault(pos, level.getBlockState(pos)); }
    /** 位置在世界内、边界内且区块已加载；形状算法据此把越界处当作障碍而不是抛异常。 */
    public boolean writable(BlockPos pos) {
        return level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos);
    }
    /** 该位置的肥沃原木属于本次生长（已写入缓冲）或本树旧有的树干。 */
    public boolean ownsLog(BlockPos pos) {
        BlockState buffered = blocks.get(pos);
        return buffered != null ? buffered.is(FertilizerContent.LOG.get()) : ownedLogs.contains(pos.asLong());
    }
    private BlockState featureState(BlockPos pos) {
        BlockState state = get(pos);
        // Vanilla's clearance pass must see the old owned trunk as replaceable during upgrades.
        return !blocks.containsKey(pos) && ownedLogs.contains(pos.asLong())
                && state.is(FertilizerContent.LOG.get()) ? Blocks.AIR.defaultBlockState() : state;
    }
    public void put(BlockPos pos, BlockState state) {
        check(pos);
        if (blocks.size() >= LIMIT && !blocks.containsKey(pos)) throw new UnsafePlacement();
        BlockState before = get(pos);
        if (!before.equals(state)) {
            boolean soil = (state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK))
                    && (before.is(Blocks.DIRT) || before.is(Blocks.GRASS_BLOCK) || before.is(Blocks.ROOTED_DIRT));
            if (!soil && !before.isAir() && !before.canBeReplaced() && !before.is(BlockTags.LEAVES)
                    && !before.is(BlockTags.SAPLINGS)
                    && !(ownedLogs.contains(pos.asLong()) && before.is(FertilizerContent.LOG.get()))) throw new UnsafePlacement();
            if (!before.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) throw new UnsafePlacement();
        }
        blocks.put(pos.immutable(), state);
        if (currentTrunk != null && state.is(FertilizerContent.LOG.get())) currentTrunk.add(pos.asLong());
    }

    @SuppressWarnings("unchecked")
    private WorldGenLevel bufferedWorld() {
        return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class[]{WorldGenLevel.class},
                (proxy, method, args) -> {
                    String name = OPERATIONS.getOrDefault(method, "delegate");
                    if (name.equals("getBlockState")) return featureState((BlockPos) args[0]);
                    if (name.equals("getFluidState")) return featureState((BlockPos) args[0]).getFluidState();
                    if (name.equals("isEmptyBlock")) return featureState((BlockPos) args[0]).isAir();
                    if (name.equals("isStateAtPosition")) return ((Predicate<BlockState>) args[1]).test(featureState((BlockPos) args[0]));
                    if (name.equals("isFluidAtPosition")) return ((Predicate<net.minecraft.world.level.material.FluidState>) args[1]).test(featureState((BlockPos) args[0]).getFluidState());
                    if (name.equals("setBlock")) { put((BlockPos) args[0], (BlockState) args[1]); return true; }
                    if (name.equals("reject")) throw new UnsafePlacement();
                    if (name.equals("ignore")) return null;
                    try { return method.invoke(level, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
    }

    // Bind using compiled interface calls, so Forge remaps the references in production jars.
    // Comparing reflection method names to development names would bypass the buffer after reobfuscation.
    private static void bind(Map<Method, String> map, String operation, Consumer<WorldGenLevel> invocation) {
        WorldGenLevel recorder = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                new Class[]{WorldGenLevel.class}, (proxy, method, args) -> {
                    map.put(method, operation);
                    return method.getReturnType() == boolean.class ? false : null;
                });
        invocation.accept(recorder);
    }

    private static Map<Method, String> operations() {
        Map<Method, String> map = new HashMap<>();
        BlockPos p = BlockPos.ZERO;
        BlockState air = Blocks.AIR.defaultBlockState();
        bind(map, "getBlockState", w -> w.getBlockState(p));
        bind(map, "getFluidState", w -> w.getFluidState(p));
        bind(map, "isEmptyBlock", w -> w.isEmptyBlock(p));
        bind(map, "isStateAtPosition", w -> w.isStateAtPosition(p, s -> true));
        bind(map, "isFluidAtPosition", w -> w.isFluidAtPosition(p, s -> true));
        bind(map, "setBlock", w -> w.setBlock(p, air, 2));
        bind(map, "setBlock", w -> w.setBlock(p, air, 2, 512));
        bind(map, "reject", w -> w.removeBlock(p, false));
        bind(map, "reject", w -> w.destroyBlock(p, false));
        bind(map, "reject", w -> w.destroyBlock(p, false, null));
        bind(map, "reject", w -> w.destroyBlock(p, false, null, 512));
        bind(map, "reject", w -> w.addFreshEntity(null));
        bind(map, "ignore", w -> w.scheduleTick(p, Blocks.AIR, 1));
        bind(map, "ignore", w -> w.scheduleTick(p, Blocks.AIR, 1, net.minecraft.world.ticks.TickPriority.NORMAL));
        bind(map, "ignore", w -> w.scheduleTick(p, net.minecraft.world.level.material.Fluids.EMPTY, 1));
        bind(map, "ignore", w -> w.scheduleTick(p, net.minecraft.world.level.material.Fluids.EMPTY, 1, net.minecraft.world.ticks.TickPriority.NORMAL));
        bind(map, "ignore", w -> w.blockUpdated(p, Blocks.AIR));
        bind(map, "ignore", w -> w.neighborShapeChanged(Direction.UP, air, p, p, 2, 512));
        bind(map, "ignore", w -> w.levelEvent(0, p, 0));
        bind(map, "ignore", w -> w.levelEvent(null, 0, p, 0));
        var event = net.minecraft.world.level.gameevent.GameEvent.BLOCK_PLACE;
        bind(map, "ignore", w -> w.gameEvent(event, p, null));
        bind(map, "ignore", w -> w.gameEvent(event, net.minecraft.world.phys.Vec3.ZERO, null));
        bind(map, "ignore", w -> w.gameEvent(null, event, p));
        bind(map, "ignore", w -> w.gameEvent(null, event, net.minecraft.world.phys.Vec3.ZERO));
        bind(map, "ignore", w -> w.setCurrentlyGenerating(null));
        return Map.copyOf(map);
    }

    public Set<Long> tree(BlockPos root, int tier) {
        int height = tier == 1 ? 6 : tier == 2 ? 11 : 20;
        int radius = tier == 1 ? 2 : tier == 2 ? 4 : 8;
        int crown = tier == 1 ? 3 : tier == 2 ? 5 : 7;
        TreeConfiguration config = new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(FertilizerContent.LOG.get()), new StraightTrunkPlacer(height, 0, 0),
                BlockStateProvider.simple(FertilizerContent.LEAVES.get()),
                new BlobFoliagePlacer(ConstantInt.of(radius), ConstantInt.of(0), crown),
                new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build();
        // AbstractTreeGrower removes the sapling before invoking a tree feature; do so only in our buffer.
        if (get(root).is(BlockTags.SAPLINGS)) put(root, Blocks.AIR.defaultBlockState());
        currentTrunk = new HashSet<>();
        if (!Feature.TREE.place(config, bufferedWorld(), level.getChunkSource().getGenerator(), level.random, root)) throw new UnsafePlacement();
        int low = tier == 3 ? -2 : 0;
        int high = tier == 1 ? 0 : tier == 2 ? 1 : 2;
        for (int x = low; x <= high; x++) for (int z = low; z <= high; z++)
            for (int y = 0; y < height - 2; y++) put(root.offset(x, y, z), FertilizerContent.LOG.get().defaultBlockState());
        if (tier > 1) for (int y = height - crown + 1; y < height; y += 3)
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int distance = 1; distance < radius; distance++)
                    put(root.offset(dx * distance, y, dz * distance), FertilizerContent.LOG.get().defaultBlockState());
            }
        int depth = tier == 1 ? 1 : tier == 2 ? 4 : 8;
        for (int x = low; x <= high; x++) for (int z = low; z <= high; z++) for (int d = 1; d <= depth; d++) {
            BlockPos p = root.offset(x, -d, z);
            BlockState old = get(p);
            if (!old.is(Blocks.DIRT) && !old.is(Blocks.GRASS_BLOCK) && !old.is(Blocks.ROOTED_DIRT)) break;
            put(p, Blocks.ROOTED_DIRT.defaultBlockState());
        }
        Set<Long> result = currentTrunk;
        currentTrunk = null;
        return result;
    }

    private void supportLeaves() {
        Map<BlockPos, Integer> distances = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        blocks.forEach((pos, state) -> { if (state.is(FertilizerContent.LOG.get())) { distances.put(pos, 0); queue.add(pos); } });
        while (!queue.isEmpty()) {
            BlockPos p = queue.remove(); int distance = distances.get(p) + 1;
            if (distance > 6) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = p.relative(direction);
                BlockState state = blocks.get(next);
                if (state != null && state.is(FertilizerContent.LEAVES.get()) && !distances.containsKey(next)) {
                    distances.put(next, distance); queue.add(next);
                }
            }
        }
        blocks.replaceAll((pos, state) -> state.is(FertilizerContent.LEAVES.get())
                ? state.setValue(LeavesBlock.DISTANCE, distances.getOrDefault(pos, 7)) : state);
    }

    public boolean commit(Player player) {
        supportLeaves();
        Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        for (var entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey(); check(pos);
            if (player != null && (!player.mayBuild() || !level.mayInteract(player, pos))) return false;
            BlockState old = level.getBlockState(pos);
            if (old.equals(entry.getValue())) continue;
            if (entry.getValue().is(FertilizerContent.LOG.get()) && !old.is(FertilizerContent.LOG.get())
                    && !level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos), e -> e.isAlive() && !e.isSpectator()).isEmpty()) return false;
            original.put(pos, old);
        }
        if (original.isEmpty()) return false;
        boolean success = false;
        List<BlockPos> applied = new ArrayList<>();
        List<BlockSnapshot> snapshots = new ArrayList<>();
        boolean capture = level.captureBlockSnapshots;
        boolean externalTransaction = player == null && capture;
        int captureStart = level.capturedBlockSnapshots.size();
        level.captureBlockSnapshots = externalTransaction;
        // Provisional writes stay silent: flag 16 skips the neighbour shape updates that
        // would pop attachments (e.g. the upper half of a double plant) and their drops
        // before the protection events get a chance to cancel. Replay them after approval;
        // external transactions defer them through Forge's captured snapshots instead.
        FertilizerData savedData = null;
        Map<Long, FertilizerData.Plant> endangered = new HashMap<>();
        for (var entry : original.entrySet()) {
            // onRemove still fires on replaced fertile logs: remember plant records whose
            // roots sit in this batch so a cancelled commit can put them back.
            if (entry.getValue().is(FertilizerContent.LOG.get())) {
                if (savedData == null) savedData = FertilizerData.get(level);
                FertilizerData.Plant plant = savedData.at(level, entry.getKey());
                if (plant != null) endangered.put(plant.root.asLong(), plant);
            }
        }
        try {
            for (BlockPos pos : original.keySet()) {
                BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
                snapshots.add(snapshot);
                int flags = externalTransaction ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
                if (!level.setBlock(pos, blocks.get(pos), flags)) throw new UnsafePlacement();
                applied.add(pos);
                if (player != null && ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)) throw new UnsafePlacement();
            }
            if (player != null && snapshots.size() > 1
                    && ForgeEventFactory.onMultiBlockPlace(player, snapshots, Direction.UP)) throw new UnsafePlacement();
            if (!externalTransaction)
                for (BlockPos pos : original.keySet()) level.markAndNotifyBlock(pos, level.getChunkAt(pos),
                        original.get(pos), blocks.get(pos), Block.UPDATE_ALL, 512);
            success = true;
        } catch (UnsafePlacement ignored) {
            return false;
        } finally {
            try {
                if (!success) {
                    level.captureBlockSnapshots = false;
                    level.capturedBlockSnapshots.subList(captureStart, level.capturedBlockSnapshots.size()).clear();
                    for (int i = applied.size() - 1; i >= 0; i--) level.setBlock(applied.get(i), original.get(applied.get(i)), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    if (!endangered.isEmpty()) endangered.values().forEach(savedData::put);
                }
            } finally { level.captureBlockSnapshots = capture; }
        }
        return true;
    }

    public static final class UnsafePlacement extends RuntimeException {}
}
