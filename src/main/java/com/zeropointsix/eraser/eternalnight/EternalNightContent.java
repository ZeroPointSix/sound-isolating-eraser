package com.zeropointsix.eraser.eternalnight;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class EternalNightContent {
    private static final DeferredRegister<com.mojang.serialization.Codec<? extends net.minecraft.world.level.biome.BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, ModMain.MOD_ID);
    private static final DeferredRegister<com.mojang.serialization.Codec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, ModMain.MOD_ID);
    static {
        BIOME_SOURCES.register("eternal_night", () -> CityBiomeSource.CODEC);
        CHUNK_GENERATORS.register("eternal_night", () -> CityChunkGenerator.CODEC);
    }
    public static final ResourceLocation ID = new ResourceLocation(ModMain.MOD_ID, "eternal_night");
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ID);
    // Retain the supplied namespace so the original NBT palettes and blockstate JSON remain byte-identical.
    public static final DeferredRegister<Block> ART_BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "qihai");
    private static final DeferredRegister<net.minecraft.world.item.Item> ART_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "qihai");
    public static final RegistryObject<Block> NEON_SIGN = ART_BLOCKS.register("neon_sign", NeonSignBlock::new);
    public static final RegistryObject<Block> EMERGENCY_LIGHT = ART_BLOCKS.register("emergency_light",
            () -> new Block(BlockBehaviour.Properties.of().strength(1.5F).lightLevel(state -> 4)));
    static {
        // Use the supplied pink item model rather than introduce an unprovided icon.
        ART_ITEMS.register("neon_sign_pink", () -> new net.minecraft.world.item.BlockItem(NEON_SIGN.get(), new net.minecraft.world.item.Item.Properties()));
        ART_ITEMS.register("emergency_light", () -> new net.minecraft.world.item.BlockItem(EMERGENCY_LIGHT.get(), new net.minecraft.world.item.Item.Properties()));
    }

    public static void register(IEventBus bus) {
        ART_BLOCKS.register(bus);
        ART_ITEMS.register(bus);
        BIOME_SOURCES.register(bus);
        CHUNK_GENERATORS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, NightConfig.SPEC, "eternal-night-server.toml");
        MinecraftForge.EVENT_BUS.register(NightEvents.class);
        MinecraftForge.EVENT_BUS.register(CitySpawns.class);
    }

    public static boolean isCity(Level level) { return level.dimension().equals(LEVEL); }
    private EternalNightContent() { }
}
