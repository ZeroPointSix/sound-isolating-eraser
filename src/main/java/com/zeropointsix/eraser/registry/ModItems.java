package com.zeropointsix.eraser.registry;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.item.SoundIsolatingEraserItem;
import com.zeropointsix.eraser.item.GravityJadePendantItem;
import com.zeropointsix.eraser.item.WindThunderWingsItem;
import com.zeropointsix.eraser.item.ThunderFeatherItem;
import com.zeropointsix.eraser.item.EnhancementPillPackItem;
import com.zeropointsix.eraser.item.ShaxiadaoItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ModMain.MOD_ID);

    public static final RegistryObject<GravityJadePendantItem> GRAVITY_JADE_PENDANT =
            ITEMS.register("gravity_jade_pendant", GravityJadePendantItem::new);

    public static final RegistryObject<EnhancementPillPackItem> ENHANCEMENT_PILL_PACK =
            ITEMS.register("enhancement_pill_pack", EnhancementPillPackItem::new);
    public static final RegistryObject<Item> EMPTY_PILL_PACK =
            ITEMS.register("empty_pill_pack", () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<ShaxiadaoItem> SHAXIADAO =
            ITEMS.register("shaxiadao", ShaxiadaoItem::new);

    public static final RegistryObject<SoundIsolatingEraserItem> SOUND_ISOLATING_ERASER =
            ITEMS.register("sound_isolating_eraser",
                    // Any positive durability makes the stack damageable; the real
                    // max is read from config at runtime via getMaxDamage.
                    () -> new SoundIsolatingEraserItem(new Item.Properties().durability(1)));

    public static final RegistryObject<WindThunderWingsItem> WIND_THUNDER_WINGS =
            ITEMS.register("wind_thunder_wings",
                    () -> new WindThunderWingsItem(new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.EPIC)
                            .fireResistant()));

    // 雷鹏骨羽 — crafting material, canon: thunder-roc bone feather.
    public static final RegistryObject<ThunderFeatherItem> THUNDER_FEATHER =
            ITEMS.register("thunder_feather",
                    () -> new ThunderFeatherItem(new Item.Properties()
                            .stacksTo(16)
                            .rarity(Rarity.RARE)));

    private ModItems() {
    }

    public static final RegistryObject<Item> NEON_TUMOR_SPAWN_EGG =
            ITEMS.register("neon_tumor_spawn_egg", () -> new ForgeSpawnEggItem(
                    ModEntities.NEON_TUMOR, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));
}
