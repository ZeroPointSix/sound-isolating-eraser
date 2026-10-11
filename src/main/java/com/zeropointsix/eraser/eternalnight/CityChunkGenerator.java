package com.zeropointsix.eraser.eternalnight;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class CityChunkGenerator extends ChunkGenerator {
    public static final int SUBWAY_Y = 40;
    public static final Codec<CityChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(CityChunkGenerator::getBiomeSource)
    ).apply(instance, CityChunkGenerator::new));

    public CityChunkGenerator(BiomeSource biomes) { super(biomes); }
    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return 384; }
    @Override public int getMinY() { return -64; }
    @Override public int getSeaLevel() { return -64; }
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                                      StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) { }
    @Override public void spawnOriginalMobs(WorldGenRegion region) { }
    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) { }

    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
            RandomState random, StructureManager structures, ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int worldX = chunk.getPos().getMinBlockX() + x, worldZ = chunk.getPos().getMinBlockZ() + z;
            int top = CityLayout.surface(worldX, worldZ);
            for (int y = getMinY(); y <= top; y++) {
                BlockState state = y == getMinY() ? Blocks.BEDROCK.defaultBlockState()
                        : y == top ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                pos.set(worldX, y, worldZ);
                chunk.setBlockState(pos, state, false);
                surface.update(x, y, z, state);
                ocean.update(x, y, z, state);
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
        CityTemplates.place(region, chunk);
    }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return CityLayout.surface(x, z) + 1;
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] column = new BlockState[getGenDepth()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        Arrays.fill(column, 0, CityLayout.surface(x, z) - getMinY(), Blocks.STONE_BRICKS.defaultBlockState());
        column[0] = Blocks.BEDROCK.defaultBlockState();
        column[CityLayout.surface(x, z) - getMinY()] = Blocks.GRAY_CONCRETE.defaultBlockState();
        return new NoiseColumn(getMinY(), column);
    }
    @Override public void addDebugScreenInfo(List<String> text, RandomState random, BlockPos pos) {
        text.add("Eternal Night: " + CityLayout.district(pos.getX(), pos.getZ()) + ", surface=" + CityLayout.surface(pos.getX(), pos.getZ()));
    }
}
