package com.zeropointsix.eraser.registry;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.item.SoundIsolatingEraserItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ModMain.MOD_ID);

    public static final RegistryObject<SoundIsolatingEraserItem> SOUND_ISOLATING_ERASER =
            ITEMS.register("sound_isolating_eraser",
                    // Any positive durability makes the stack damageable; the real
                    // max is read from config at runtime via getMaxDamage.
                    () -> new SoundIsolatingEraserItem(new Item.Properties().durability(1)));

    private ModItems() {
    }
}
