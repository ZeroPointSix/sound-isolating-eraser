package com.zeropointsix.eraser.registry;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.item.SoundIsolatingEraserItem;
import com.zeropointsix.eraser.item.GravityJadePendantItem;
import com.zeropointsix.eraser.item.EnhancementPillPackItem;
import net.minecraft.world.item.Item;
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

    public static final RegistryObject<SoundIsolatingEraserItem> SOUND_ISOLATING_ERASER =
            ITEMS.register("sound_isolating_eraser",
                    // Any positive durability makes the stack damageable; the real
                    // max is read from config at runtime via getMaxDamage.
                    () -> new SoundIsolatingEraserItem(new Item.Properties().durability(1)));

    private ModItems() {
    }
}
