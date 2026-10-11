package com.zeropointsix.eraser.eternalnight;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.*;

public final class CityTemplates {
    public static final List<String> BUILDINGS = List.of("res_apartment_low", "res_apartment_mid", "res_apartment_high",
            "res_tenement", "office_dark", "office_tower", "shop_neon", "warehouse", "mall", "civic_center", "townhall", "library");

    private CityTemplates() { }
    public static ResourceLocation id(String path) { return new ResourceLocation("qihai", path); }
    public static boolean available(ServerLevel level) {
        return BUILDINGS.stream().allMatch(name -> level.getStructureManager().get(id("buildings/" + name)).isPresent());
    }

    public static void place(WorldGenRegion region, ChunkAccess chunk) {
        int tx = chunk.getPos().x, tz = chunk.getPos().z;
        int x = tx * 16, z = tz * 16;
        int lx = Math.floorMod(tx, 4), lz = Math.floorMod(tz, 4);
        BoundingBox clip = new BoundingBox(x, region.getMinBuildHeight(), z, x + 15, region.getMaxBuildHeight() - 1, z + 15);
        String district = CityLayout.district(x, z) == CityLayout.District.COMMERCIAL ? "com" : "res";
        if (lx == 0 || lz == 0) {
            String street = lx == 0 && lz == 0 ? "street_cross_" : lx == 0 ? "street_straight_ns_" : "street_straight_";
            placeOne(region, "streets/" + street + district, new BlockPos(x, CityLayout.surface(x, z) - 1, z), clip, true, null);
            placeOne(region, "subway/tunnel_straight", new BlockPos(x, CityChunkGenerator.SUBWAY_Y, z), clip, false, null,
                    lx == 0 ? Rotation.CLOCKWISE_90 : Rotation.NONE);
            // A cross intersection is assembled from two supplied straight tunnel pieces.
            if (lx == 0 && lz == 0) placeOne(region, "subway/tunnel_straight", new BlockPos(x, CityChunkGenerator.SUBWAY_Y, z),
                    clip, false, null, Rotation.NONE);
        } else {
            int bx = Math.floorDiv(tx, 4), bz = Math.floorDiv(tz, 4);
            boolean large = Math.floorMod(CityLayout.hash(region.getSeed(), bx, bz), 4) == 0;
            if (large && lx <= 2 && lz <= 2) {
                String[] choices = {"mall", "civic_center", "townhall", "library"};
                String name = choices[(int) Math.floorMod(CityLayout.hash(region.getSeed() + 1, bx, bz), choices.length)];
                placeOne(region, "buildings/" + name, new BlockPos((bx * 4 + 1) * 16, CityLayout.plateau(bx, bz), (bz * 4 + 1) * 16),
                        clip, false, loot(name));
            } else if (lx == 3 && lz == 3 && Math.floorMod(CityLayout.hash(region.getSeed() + 2, bx, bz), 6) == 0) {
                int top = CityLayout.plateau(bx, bz);
                placeOne(region, "subway/station_platform_stairs", new BlockPos(x, CityChunkGenerator.SUBWAY_Y, z), clip, false, null);
                for (int y = CityChunkGenerator.SUBWAY_Y + 6; y < top - 5; y += 6)
                    placeOne(region, "subway/station_shaft", new BlockPos(x, y, z), clip, false, null);
                placeOne(region, "subway/station_entrance", new BlockPos(x, top, z), clip, false, null);
            } else {
                String name = CityLayout.building(region.getSeed(), tx, tz);
                placeOne(region, "buildings/" + name, new BlockPos(x, CityLayout.plateau(bx, bz), z), clip, false, loot(name));
            }
        }
    }

    private static String loot(String name) {
        return switch (name) {
            case "mall" -> "mall";
            case "civic_center" -> "weapons_high";
            case "townhall" -> "armor_high";
            case "shop_neon" -> "ranged";
            case "warehouse", "office_dark", "office_tower" -> "tools";
            default -> "common";
        };
    }

    private static void placeOne(WorldGenRegion region, String path, BlockPos origin, BoundingBox clip, boolean slope, String loot) {
        placeOne(region, path, origin, clip, slope, loot, Rotation.NONE);
    }

    private static void placeOne(WorldGenRegion region, String path, BlockPos origin, BoundingBox clip, boolean slope, String loot, Rotation rotation) {
        Optional<StructureTemplate> optional = region.getLevel().getStructureManager().get(id(path));
        if (optional.isEmpty()) return;
        StructureTemplate template = optional.get();
        long seed = CityLayout.hash(region.getSeed(), origin.getX(), origin.getZ()) ^ path.hashCode();
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true).setKeepLiquids(false)
                .setKnownShape(true).setRotation(rotation).setRandom(RandomSource.create(seed));
        BlockPos adjusted = template.getZeroPositionWithTransform(origin, Mirror.NONE, rotation);
        Set<Long> keep = chooseContainers(template, settings, adjusted, seed);
        settings.setBoundingBox(clip).addProcessor(new PlacementProcessor(keep, loot, seed, slope, origin.getY()));
        template.placeInWorld(region, adjusted, adjusted, settings, RandomSource.create(seed), 2 | 16);
    }

    private static Set<Long> chooseContainers(StructureTemplate template, StructurePlaceSettings settings, BlockPos origin, long seed) {
        List<BlockPos> candidates = new ArrayList<>();
        for (Block block : List.of(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL))
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, block)) candidates.add(info.pos());
        candidates.sort(Comparator.comparingLong(pos -> CityLayout.hash(seed, pos.getX(), pos.getZ()) ^ pos.getY()));
        int count = Math.min(candidates.size(), 1 + (int) Math.floorMod(seed, 4));
        Set<Long> keep = new HashSet<>();
        for (int i = 0; i < count; i++) keep.add(candidates.get(i).asLong());
        return keep;
    }

    private static final class PlacementProcessor extends StructureProcessor {
        private final Set<Long> keep;
        private final String loot;
        private final long seed;
        private final boolean slope;
        private final int originY;
        private PlacementProcessor(Set<Long> keep, String loot, long seed, boolean slope, int originY) {
            this.keep = keep; this.loot = loot; this.seed = seed; this.slope = slope; this.originY = originY;
        }
        @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin, BlockPos pivot,
                StructureTemplate.StructureBlockInfo raw, StructureTemplate.StructureBlockInfo placed, StructurePlaceSettings settings) {
            BlockPos pos = placed.pos();
            BlockState state = placed.state();
            CompoundTag tag = placed.nbt() == null ? null : placed.nbt().copy();
            if (slope) pos = new BlockPos(pos.getX(), pos.getY() + CityLayout.surface(pos.getX(), pos.getZ()) - 1 - originY, pos.getZ());
            if (!state.getFluidState().isEmpty()) {
                if (state.hasProperty(BlockStateProperties.WATERLOGGED)) state = state.setValue(BlockStateProperties.WATERLOGGED, false);
                else state = Blocks.AIR.defaultBlockState();
            }
            if (state.getBlock() instanceof BushBlock || state.getBlock() instanceof LeavesBlock) state = Blocks.AIR.defaultBlockState();
            if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SAND) || state.is(Blocks.GRAVEL))
                state = Blocks.GRAY_CONCRETE.defaultBlockState();
            if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) {
                if (loot == null || !keep.contains(placed.pos().asLong())) return new StructureTemplate.StructureBlockInfo(pos, Blocks.AIR.defaultBlockState(), null);
                tag = new CompoundTag();
                tag.putString("LootTable", ModMain.MOD_ID + ":chests/eternal_night/" + loot);
                tag.putLong("LootTableSeed", seed ^ pos.asLong());
            }
            return new StructureTemplate.StructureBlockInfo(pos, state, tag);
        }
        @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.NOP; }
    }
}
