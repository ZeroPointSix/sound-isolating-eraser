package com.zeropointsix.eraser.yuji.flare;

import com.mojang.serialization.Codec;
import com.zeropointsix.eraser.yuji.YujiContent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FlareContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "qihai");
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "qihai");
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "qihai");
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, "qihai");
    public static final RegistryObject<SimpleParticleType> RED_SPARK = PARTICLES.register("flare_spark_red", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> YELLOW_SPARK = PARTICLES.register("flare_spark_yellow", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> GREEN_SPARK = PARTICLES.register("flare_spark_green", () -> new SimpleParticleType(true));
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "qihai");
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "qihai");
    public static final RegistryObject<Item> GUN = ITEMS.register("flare_gun", FlareGunItem::new);
    public static final RegistryObject<Item> RED = ammo("flare_red");
    public static final RegistryObject<Item> YELLOW = ammo("flare_yellow");
    public static final RegistryObject<Item> GREEN = ammo("flare_green");
    public static final RegistryObject<Block> CRATE = BLOCKS.register("supply_crate", () -> new SupplyCrateBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD)));
    public static final RegistryObject<Item> CRATE_ITEM = ITEMS.register("supply_crate", () -> new BlockItem(CRATE.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<SupplyCrateBlockEntity>> CRATE_ENTITY = BLOCK_ENTITIES.register("supply_crate",
            () -> BlockEntityType.Builder.of(SupplyCrateBlockEntity::new, CRATE.get()).build(null));
    public static final RegistryObject<EntityType<FlareProjectile>> FLARE = ENTITIES.register("flare", () ->
            EntityType.Builder.<FlareProjectile>of(FlareProjectile::new, MobCategory.MISC).sized(.25F, .25F)
                    .clientTrackingRange(10).updateInterval(1).fireImmune().build("qihai:flare"));
    public static final RegistryObject<EntityType<FlareDecoy>> DECOY = ENTITIES.register("flare_decoy", () ->
            EntityType.Builder.<FlareDecoy>of(FlareDecoy::new, MobCategory.MISC).sized(.6F, 1.8F)
                    .clientTrackingRange(10).updateInterval(1).fireImmune().build("qihai:flare_decoy"));
    public static final RegistryObject<EntityType<FallingSupplyCrate>> FALLING_CRATE = ENTITIES.register("falling_supply_crate", () ->
            EntityType.Builder.<FallingSupplyCrate>of(FallingSupplyCrate::new, MobCategory.MISC).sized(.9F, .9F)
                    .clientTrackingRange(12).updateInterval(1).fireImmune().build("qihai:falling_supply_crate"));
    public static final RegistryObject<Codec<FlareLootModifier>> LOOT_MODIFIER = LOOT.register("flare_supplies", () -> FlareLootModifier.CODEC);

    private static RegistryObject<Item> ammo(String id) {
        return ITEMS.register(id, () -> new Item(new Item.Properties().stacksTo(16)) {
            @Override public int getMaxStackSize(ItemStack stack) {
                return com.zeropointsix.eraser.yuji.YujiConfig.SPEC.isLoaded() ? FlareConfig.STACK_SIZE.get() : 16;
            }
        });
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus); BLOCKS.register(bus); ENTITIES.register(bus); BLOCK_ENTITIES.register(bus); LOOT.register(bus); PARTICLES.register(bus);
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(FlareGunNetwork::register));
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> e.put(DECOY.get(), FlareDecoy.attributes().build()));
        MinecraftForge.EVENT_BUS.register(FlareEvents.class);
    }

    public static int color(ItemStack stack) {
        return stack.is(RED.get()) ? 0 : stack.is(YELLOW.get()) ? 1 : stack.is(GREEN.get()) ? 2 : -1;
    }
    public static Item ammunition(int color) { return color == 1 ? YELLOW.get() : color == 2 ? GREEN.get() : RED.get(); }
    private FlareContent() { }
}
