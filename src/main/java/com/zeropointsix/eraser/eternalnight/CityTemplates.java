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
    /** 3×3 civic overlay. Only the commercial district may place these. */
    public static final List<String> CIVIC_LARGE = List.of("mall", "civic_center", "townhall", "library");

    /** Every structure template the generator may place, per the supplied art pack. */
    public static final List<String> STRUCTURES = List.of(
            "buildings/res_apartment_low", "buildings/res_apartment_mid", "buildings/res_apartment_high",
            "buildings/res_tenement", "buildings/office_dark", "buildings/office_tower", "buildings/shop_neon",
            "buildings/warehouse", "buildings/mall", "buildings/civic_center", "buildings/townhall", "buildings/library",
            "plazas/plaza_fountain", "plazas/plaza_fountain_com", "plazas/plaza_parking", "plazas/plaza_station",
            "streets/street_bend_com", "streets/street_bend_res", "streets/street_cross_com", "streets/street_cross_res",
            "streets/street_end_com", "streets/street_end_res", "streets/street_straight_com",
            "streets/street_straight_ns_com", "streets/street_straight_ns_res", "streets/street_straight_res",
            "streets/street_t_com", "streets/street_t_res",
            "subway/station_entrance", "subway/station_platform", "subway/station_platform_stairs",
            "subway/station_shaft", "subway/tunnel_bend", "subway/tunnel_split", "subway/tunnel_straight");

    private CityTemplates() { }
    public static ResourceLocation id(String path) { return new ResourceLocation("qihai", path); }
    public static boolean available(ServerLevel level) {
        return STRUCTURES.stream().allMatch(name -> level.getStructureManager().get(id(name)).isPresent());
    }

    /**
     * A 3×3 mall/civic/townhall/library overlay is commercial-only.
     * Residential and industrial tiles keep {@link CityLayout#building}.
     */
    public static String largeCivic(long seed, int blockX, int blockZ) {
        CityLayout.District district = CityLayout.district(blockX * 64 + 32, blockZ * 64 + 32);
        if (district != CityLayout.District.COMMERCIAL) return null;
        if (Math.floorMod(CityLayout.hash(seed, blockX, blockZ), 4) != 0) return null;
        return CIVIC_LARGE.get((int) Math.floorMod(CityLayout.hash(seed + 1, blockX, blockZ), CIVIC_LARGE.size()));
    }

    public static void place(WorldGenRegion region, ChunkAccess chunk) {
        int tx = chunk.getPos().x, tz = chunk.getPos().z;
        int x = tx * 16, z = tz * 16;
        int lx = Math.floorMod(tx, 4), lz = Math.floorMod(tz, 4);
        int bx = Math.floorDiv(tx, 4), bz = Math.floorDiv(tz, 4);
        boolean station = lx == 3 && lz == 3 && hasStation(region.getSeed(), bx, bz);
        BoundingBox clip = new BoundingBox(x, region.getMinBuildHeight(), z, x + 15, region.getMaxBuildHeight() - 1, z + 15);
        String district = CityLayout.district(x, z) == CityLayout.District.COMMERCIAL ? "com" : "res";
        // The supplied split has openings on all four edges; overlapping two straights seals one line.
        if (lx == 0 && (lz == 0 || lz == 3)) {
            placeOne(region, "subway/tunnel_split", new BlockPos(x, CityChunkGenerator.SUBWAY_Y, z), clip, false, null);
        } else if (lx == 0 || lz == 0 || lz == 3) {
            placeOne(region, station ? "subway/station_platform_stairs" : "subway/tunnel_straight",
                    new BlockPos(x, CityChunkGenerator.SUBWAY_Y, z), clip, false, null,
                    lx == 0 ? Rotation.CLOCKWISE_90 : Rotation.NONE);
        }
        if (lx == 0 || lz == 0) {
            String street = lx == 0 && lz == 0 ? "street_cross_" : lx == 0 ? "street_straight_ns_" : "street_straight_";
            placeOne(region, "streets/" + street + district, new BlockPos(x, CityLayout.surface(x, z) - 1, z), clip, true, null);
        } else {
            String civic = largeCivic(region.getSeed(), bx, bz);
            if (civic != null && lx <= 2 && lz <= 2) {
                placeOne(region, "buildings/" + civic, new BlockPos((bx * 4 + 1) * 16, CityLayout.plateau(bx, bz), (bz * 4 + 1) * 16),
                        clip, false, loot(civic));
            } else if (station) {
                int top = CityLayout.plateau(bx, bz);
                BoundingBox underground = new BoundingBox(x, CityChunkGenerator.SUBWAY_Y + 6, z, x + 15, top - 1, z + 15);
                for (int y = CityChunkGenerator.SUBWAY_Y + 6; y < top; y += 6)
                    placeOne(region, "subway/station_shaft", new BlockPos(x, y, z), underground, false, null);
                placeOne(region, "plazas/plaza_station", new BlockPos(x, top - 1, z), clip, false, null);
                // The kit README explicitly permits a ladder to bridge its discontinuous stair pieces.
                for (int y = CityChunkGenerator.SUBWAY_Y + 2; y <= top; y++) {
                    region.setBlock(new BlockPos(x + 3, y, z + 13), Blocks.STONE_BRICKS.defaultBlockState(), 2 | 16);
                    region.setBlock(new BlockPos(x + 4, y, z + 13), Blocks.LADDER.defaultBlockState()
                            .setValue(LadderBlock.FACING, net.minecraft.core.Direction.EAST), 2 | 16);
                    region.setBlock(new BlockPos(x + 5, y, z + 13), Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            } else if (lx == 3 && lz == 1 && Math.floorMod(CityLayout.hash(region.getSeed() + 3, bx, bz), 4) == 0) {
                String plaza = Math.floorMod(CityLayout.hash(region.getSeed() + 4, bx, bz), 2) == 0
                        ? "plaza_parking" : "plaza_fountain" + (district.equals("com") ? "_com" : "");
                placeOne(region, "plazas/" + plaza, new BlockPos(x, CityLayout.plateau(bx, bz) - 1, z), clip, false, null);
            } else {
                String name = CityLayout.building(region.getSeed(), tx, tz);
                placeOne(region, "buildings/" + name, new BlockPos(x, CityLayout.plateau(bx, bz), z), clip, false, loot(name));
            }
        }
    }

    public static boolean hasStation(long seed, int blockX, int blockZ) {
        return Math.floorMod(CityLayout.hash(seed + 2, blockX, blockZ), 6) == 0;
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
