package com.zeropointsix.eraser.yuji.flare;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class FlareEvents {
    public static final TagKey<Item> PROPS = TagKey.create(Registries.ITEM, new ResourceLocation("qihai", "props"));
    public static final TagKey<Item> ODDITIES = TagKey.create(Registries.ITEM, new ResourceLocation("qihai", "oddities"));
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level && level.getGameTime() % 10 == 0)
            FlareLights.get(level).cleanup(level);
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        if (event.getItemStack().is(PROPS)) event.getToolTip().add(Component.translatable("tooltip.qihai.category.prop"));
        else if (event.getItemStack().is(ODDITIES)) event.getToolTip().add(Component.translatable("tooltip.qihai.category.oddity"));
    }
    private FlareEvents() { }
}
