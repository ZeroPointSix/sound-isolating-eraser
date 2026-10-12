package com.zeropointsix.eraser.yuji.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class RainCityGenerator extends ChunkGenerator {
    public static final int SURFACE = 128;
    public static final Codec<RainCityGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(RainCityGenerator::getBiomeSource)).apply(i, RainCityGenerator::new));
    public RainCityGenerator(BiomeSource source) { super(source); }
    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return 384; }
    @Override public int getMinY() { return -64; }
    @Override public int getSeaLevel() { return -64; }
    @Override public void applyCarvers(WorldGenRegion r, long seed, RandomState random, BiomeManager b, StructureManager s, ChunkAccess c, GenerationStep.Carving step) { }
    @Override public void spawnOriginalMobs(WorldGenRegion r) { }
    @Override public void applyBiomeDecoration(WorldGenLevel l, ChunkAccess c, StructureManager s) { }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        var pos = new BlockPos.MutableBlockPos();
        var surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        var floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < SURFACE; y++) {
            BlockState state = y == -64 ? Blocks.BEDROCK.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
            chunk.setBlockState(pos, state, false); surface.update(x, y, z, state); floor.update(x, y, z, state);
        }
        return CompletableFuture.completedFuture(chunk);
    }
    @Override public void buildSurface(WorldGenRegion region, StructureManager s, RandomState r, ChunkAccess chunk) { RainCityTemplates.place(region, chunk); }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types t, LevelHeightAccessor l, RandomState r) { return SURFACE; }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor l, RandomState r) {
        BlockState[] blocks = new BlockState[384]; Arrays.fill(blocks, Blocks.AIR.defaultBlockState());
        Arrays.fill(blocks, 0, SURFACE + 64, Blocks.STONE_BRICKS.defaultBlockState()); blocks[0] = Blocks.BEDROCK.defaultBlockState();
        return new NoiseColumn(-64, blocks);
    }
    @Override public void addDebugScreenInfo(List<String> info, RandomState r, BlockPos p) { info.add("Yuji Fodu: " + RainCityTemplates.district(p)); }
}
