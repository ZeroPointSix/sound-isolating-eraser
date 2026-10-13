package com.zeropointsix.eraser.yuji.city;

import com.mojang.serialization.Codec;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class RainCityContent {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, YujiContent.id("yuji_fodu"));
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "qihai");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "qihai");
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "qihai");
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, "qihai");
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, "qihai");
    public static final RegistryObject<Block> INCENSE = BLOCKS.register("incense_burner", () -> new Block(BlockBehaviour.Properties.of().strength(2).noOcclusion()));
    public static final RegistryObject<Block> CORPSE = BLOCKS.register("wall_corpse", RainCityBlocks.WallCorpseBlock::new);
    public static final RegistryObject<Block> MURAL = BLOCKS.register("mural", RainCityBlocks.MuralBlock::new);
    public static final RegistryObject<Block> LIGHT = BLOCKS.register("holy_light_source", RainCityBlocks.HolyLightBlock::new);
    public static final RegistryObject<SimpleParticleType> HOLY_LIGHT = PARTICLES.register("holy_light", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> BLACK_SPLASH = PARTICLES.register("black_splash", () -> new SimpleParticleType(false));
    public static final RegistryObject<EntityType<RecliningBuddha>> BUDDHA = ENTITIES.register("reclining_buddha", () -> EntityType.Builder
            .<RecliningBuddha>of(RecliningBuddha::new, MobCategory.MISC).sized(40, 12).clientTrackingRange(16).updateInterval(1)
            .fireImmune().build("qihai:reclining_buddha"));
    public static final Map<YujiKind, RegistryObject<Item>> MARKS = new EnumMap<>(YujiKind.class);
    static {
        GENERATORS.register("yuji_fodu", () -> RainCityGenerator.CODEC);
        for (YujiKind kind : YujiKind.values()) if (kind != YujiKind.SHIFOTOU)
            MARKS.put(kind, ITEMS.register("karma_mark_" + kind.id(), () -> new Item(new Item.Properties())));
        for (RegistryObject<Block> block : java.util.List.of(INCENSE, CORPSE, MURAL, LIGHT))
            ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); PARTICLES.register(bus); GENERATORS.register(bus);
        bus.addListener(RainCityContent::creative);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RainCityConfig.SPEC, "qihai-city-server.toml");
        MinecraftForge.EVENT_BUS.register(RainCityEvents.class);
        RainCityNetwork.register();
    }
    private static void creative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) ITEMS.getEntries().forEach(i -> e.accept(i.get()));
    }
    public static boolean isCity(Level level) { return level.dimension().equals(LEVEL); }
    private RainCityContent() { }
}
