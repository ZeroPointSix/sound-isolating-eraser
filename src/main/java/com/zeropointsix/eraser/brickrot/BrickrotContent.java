package com.zeropointsix.eraser.brickrot;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BrickrotContent {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ModMain.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ModMain.MOD_ID);
    public static final RegistryObject<EntityType<BrickrotWallEntity>> WALL = ENTITIES.register(
            "brickrot_wall", () -> EntityType.Builder.of(BrickrotWallEntity::new, MobCategory.MONSTER)
                    .sized(3, 3).fireImmune().clientTrackingRange(8).updateInterval(1)
                    .build(ModMain.MOD_ID + ":brickrot_wall"));
    public static final RegistryObject<Item> EGG = ITEMS.register("brickrot_spawn_egg",
            () -> new BrickrotSpawnEggItem(new Item.Properties()));

    private BrickrotContent() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        bus.addListener(BrickrotContent::attributes);
        bus.addListener(BrickrotContent::creative);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(WALL.get(), BrickrotWallEntity.attributes().build());
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(EGG.get());
    }
}
