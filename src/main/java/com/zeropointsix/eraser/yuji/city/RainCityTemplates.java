package com.zeropointsix.eraser.yuji.city;

import com.zeropointsix.eraser.eternalnight.CityLayout;
import com.zeropointsix.eraser.yuji.YujiContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public final class RainCityTemplates {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RainCityTemplates.class);
    private static final java.util.Set<String> MISSING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static final int GROUND = RainCityGenerator.SURFACE;
    public static final BlockPos HOSPITAL = new BlockPos(80, GROUND, 80);
    public static final BlockPos BELL = new BlockPos(0, 12, 0);
    public static final List<BlockPos> BOTTOM_GUARDIANS = List.of(new BlockPos(15, 10, -2), new BlockPos(8, 10, 12),
            new BlockPos(-4, 10, 13), new BlockPos(-12, 10, 7), new BlockPos(-13, 10, -7), new BlockPos(-5, 10, -15), new BlockPos(9, 10, -13));
    public static final List<BlockPos> RITUAL_GUARDIANS = List.of(new BlockPos(-35, 73, -4), new BlockPos(-47, 73, -2), new BlockPos(-60, 73, -2));
    public static final List<String> BUILDINGS = List.of("shop_grocery", "shop_pharmacy", "shop_noodle", "shop_hardware", "shop_tea",
            "res_tile_low", "res_tile_mid", "res_brick", "res_concrete_high", "office_rain");
    private record Piece(String name, BlockPos pos, Rotation rotation) {
        Piece(String name, int x, int y, int z) { this(name, new BlockPos(x, y, z), Rotation.NONE); }
    }
    // Stack order comes from index_in_stack in the supplied structure_stats.json.
    private static final List<Piece> TEMPLE = List.of(
            new Piece("temple/main_hall", -24, GROUND, -24),
            new Piece("shaft/shaft_mouth", -24, GROUND - 16, -24),
            new Piece("shaft/shaft_section_plain", -24, GROUND - 32, -24),
            new Piece("shaft/shaft_section_niche", -24, GROUND - 48, -24),
            new Piece("shaft/shaft_section_ritual", -24, GROUND - 64, -24),
            new Piece("shaft/shaft_section_elevator", -24, GROUND - 80, -24),
            new Piece("shaft/shaft_section_niche_b", -24, GROUND - 96, -24),
            new Piece("shaft/shaft_bottom", -32, 8, -32),
            new Piece("shaft/bronze_bell", -7, 10, -7),
            new Piece("shaft/ritual_room_karma", new BlockPos(-39, 72, -7), Rotation.CLOCKWISE_90),
            new Piece("shaft/ritual_room_soul", new BlockPos(-52, 72, -7), Rotation.CLOCKWISE_90),
            new Piece("shaft/ritual_room_golden_buddha", new BlockPos(-65, 72, -7), Rotation.CLOCKWISE_90),
            new Piece("temple/temple_gate", -10, GROUND, -52),
            new Piece("temple/temple_hall", -56, GROUND, 32),
            new Piece("temple/pagoda", 40, GROUND, 32),
            new Piece("temple/cloister_straight", -24, GROUND, 40),
            new Piece("temple/cloister_straight", -8, GROUND, 40),
            new Piece("temple/cloister_straight", 8, GROUND, 40));

    public static String district(BlockPos p) {
        if (shaft(p)) return "shaft";
        if (Math.abs(p.getX()) < 64 && Math.abs(p.getZ()) < 64) return "temple";
        if (p.getY() < GROUND - 8) return "subway";
        if (p.distSqr(HOSPITAL.offset(16, 0, 16)) < 80 * 80) return "hospital";
        int bx = Math.floorDiv(p.getX(), 64), bz = Math.floorDiv(p.getZ(), 64);
        return Math.floorMod(bx + bz, 4) == 0 ? "mall" : "old_town";
    }
    public static boolean shaft(BlockPos p) { return p.getY() < GROUND && Math.abs(p.getX()) <= 68 && Math.abs(p.getZ()) <= 36; }
    public static void place(WorldGenRegion region, ChunkAccess chunk) {
        int cx = chunk.getPos().x, cz = chunk.getPos().z, x = cx * 16, z = cz * 16;
        BoundingBox clip = new BoundingBox(x, region.getMinBuildHeight(), z, x + 15, region.getMaxBuildHeight() - 1, z + 15);
        if (x < 64 && x + 15 >= -80 && z < 64 && z + 15 >= -64) {
            if (z == -32 || z == -64) placeOne(region, new Piece("streets/plaza_incense", x, GROUND - 1, z), clip);
            for (Piece piece : TEMPLE) placeOne(region, piece, clip);
            // All ritual rooms connect through a three-block-high gallery, never through solid backing walls.
            for (int px = Math.max(-66, x); px <= Math.min(-19, x + 15); px++)
                for (int pz = Math.max(-1, z); pz <= Math.min(1, z + 15); pz++)
                    for (int y = 73; y <= 75; y++) region.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 18);
            return;
        }
        if (x >= 80 && x < 112 && z >= 80 && z < 112) {
            placeOne(region, new Piece("buildings/hospital", HOSPITAL, Rotation.NONE), clip);
            return;
        }
        int lx = Math.floorMod(cx, 4), lz = Math.floorMod(cz, 4), bx = Math.floorDiv(cx, 4), bz = Math.floorDiv(cz, 4);
        long hash = CityLayout.hash(region.getSeed(), cx, cz);
        if (lx == 0 || lz == 0) {
            String road = lx == 0 && lz == 0 ? "street_cross" : lx == 0 ? "street_straight_ns"
                    : switch ((int) Math.floorMod(hash, 3)) { case 0 -> "street_busstop"; case 1 -> "street_shrine"; default -> "street_straight"; };
            placeOne(region, new Piece("streets/" + road, x, GROUND - 1, z), clip);
            // Reuse the repository's existing subway kit, in this dimension only.
            placeResource(region, "subway/" + (lx == 0 && lz == 0 ? "tunnel_split" : "tunnel_straight"),
                    new BlockPos(x, 88, z), lx == 0 ? Rotation.CLOCKWISE_90 : Rotation.NONE, clip);
            if (lx == 0 && lz == 0) for (int y = 90; y <= GROUND; y++) {
                region.setBlock(new BlockPos(x + 3, y, z + 3), Blocks.STONE_BRICKS.defaultBlockState(), 18);
                region.setBlock(new BlockPos(x + 4, y, z + 3), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, net.minecraft.core.Direction.EAST), 18);
            }
        } else if (Math.floorMod(bx + bz, 4) == 0 && lx <= 2 && lz <= 2) {
            placeOne(region, new Piece("buildings/mall_rain", (bx * 4 + 1) * 16, GROUND, (bz * 4 + 1) * 16), clip);
        } else if (lx == 3 && lz == 3) {
            placeOne(region, new Piece("streets/plaza_flooded", x, GROUND - 1, z), clip);
            String pose = List.of("seated", "standing", "bust").get((int) Math.floorMod(hash, 3));
            placeOne(region, new Piece("statues/buddha_" + pose + "_medium", new BlockPos(x + 3, GROUND + 1, z + 3), facingCenter(x, z)), clip);
        } else {
            placeOne(region, new Piece("buildings/" + BUILDINGS.get((int) Math.floorMod(hash, BUILDINGS.size())), x, GROUND, z), clip);
        }
    }
    public static Rotation facingCenter(int x, int z) {
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
        return z > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
    }
    private static void placeOne(WorldGenRegion region, Piece piece, BoundingBox clip) {
        placeResource(region, "yuji/" + piece.name, piece.pos, piece.rotation, clip);
    }
    /** Resolves a city template; a missing or unreadable one is logged once and skipped instead of crashing worldgen. */
    public static java.util.Optional<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate> lookup(
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager, String resource) {
        java.util.Optional<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate> found;
        try { found = manager.get(YujiContent.id(resource)); }
        catch (RuntimeException failure) { found = java.util.Optional.empty(); }
        if (found.isEmpty() && MISSING.add(resource)) LOGGER.warn("Missing Yuji city template {}; skipping it", resource);
        return found;
    }
    private static void placeResource(WorldGenRegion region, String resource, BlockPos pos, Rotation rotation, BoundingBox clip) {
        var found = lookup(region.getLevel().getStructureManager(), resource);
        if (found.isEmpty()) return;
        var template = found.get();
        var settings = new StructurePlaceSettings().setIgnoreEntities(true).setKeepLiquids(false).setKnownShape(true).setRotation(rotation);
        BlockPos adjusted = template.getZeroPositionWithTransform(pos, Mirror.NONE, rotation);
        if (!template.getBoundingBox(settings, adjusted).intersects(clip)) return;
        settings.setBoundingBox(clip);
        if (resource.equals("yuji/buildings/hospital")) settings.addProcessor(new net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor() {
            @Override public net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo processBlock(
                    net.minecraft.world.level.LevelReader level, BlockPos origin, BlockPos pivot,
                    net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo raw,
                    net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo placed, StructurePlaceSettings ignored) {
                if (placed.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                    var facing = placed.state().getValue(net.minecraft.world.level.block.BedBlock.FACING).getOpposite();
                    return new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                            placed.pos(), placed.state().setValue(net.minecraft.world.level.block.BedBlock.FACING, facing), placed.nbt());
                }
                return placed;
            }
            @Override protected net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType<?> getType() {
                return net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType.NOP;
            }
        });
        if (resource.startsWith("yuji/shaft/ritual_room_")) settings.addProcessor(new net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor() {
            @Override public net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo processBlock(
                    net.minecraft.world.level.LevelReader level, BlockPos origin, BlockPos pivot,
                    net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo raw,
                    net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo placed, StructurePlaceSettings ignored) {
                // The supplied five-block interior cannot contain the existing 6.3-block Xieyin.
                // Extend its top wall/air band, retaining every original mural and the immutable NBT.
                if (raw.pos().getY() == 5 && clip.isInside(placed.pos())) {
                    region.setBlock(placed.pos().above(), placed.state(), 18);
                    region.setBlock(placed.pos().above(2), placed.state(), 18);
                }
                if (raw.pos().getY() == 6) return new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                        placed.pos().above(2), placed.state(), placed.nbt());
                return placed;
            }
            @Override protected net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType<?> getType() {
                return net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType.NOP;
            }
        });
        template.placeInWorld(region, adjusted, adjusted, settings, RandomSource.create(CityLayout.hash(region.getSeed(), pos.getX(), pos.getZ())), 18);
    }
    private RainCityTemplates() { }
}
