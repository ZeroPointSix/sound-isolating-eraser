package com.zeropointsix.eraser.registry;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModItemTags {
    public static final TagKey<Item> PRECIOUS_MEMORY = TagKey.create(Registries.ITEM,
            new ResourceLocation(ModMain.MOD_ID, "precious_memory"));
    public static final TagKey<Item> QIWU = TagKey.create(Registries.ITEM,
            new ResourceLocation(ModMain.MOD_ID, "qiwu"));
    public static final TagKey<Item> DAOJU = TagKey.create(Registries.ITEM,
            new ResourceLocation(ModMain.MOD_ID, "daoju"));

    private ModItemTags() { }
}
