package com.zeropointsix.eraser.eternalnight;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import java.util.stream.Stream;

public final class CityBiomeSource extends BiomeSource {
    public static final Codec<CityBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Biome.CODEC.fieldOf("residential").forGetter(s -> s.residential),
            Biome.CODEC.fieldOf("commercial").forGetter(s -> s.commercial),
            Biome.CODEC.fieldOf("industrial").forGetter(s -> s.industrial)
    ).apply(instance, CityBiomeSource::new));
    private final Holder<Biome> residential, commercial, industrial;

    public CityBiomeSource(Holder<Biome> residential, Holder<Biome> commercial, Holder<Biome> industrial) {
        this.residential = residential;
        this.commercial = commercial;
        this.industrial = industrial;
    }
    @Override protected Codec<? extends BiomeSource> codec() { return CODEC; }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() { return Stream.of(residential, commercial, industrial); }
    @Override public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return switch (CityLayout.district(x * 4, z * 4)) {
            case RESIDENTIAL -> residential;
            case COMMERCIAL -> commercial;
            case INDUSTRIAL -> industrial;
        };
    }
}
