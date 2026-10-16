package com.zeropointsix.eraser.item;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModItemTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemCategoryTooltips {
    private ItemCategoryTooltips() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        var tooltip = event.getToolTip();
        if (stack.isEmpty() || tooltip.isEmpty()) {
            return;
        }

        MutableComponent label;
        if (stack.is(ModItemTags.PRECIOUS_MEMORY)) {
            label = Component.translatable("tooltip.sound_isolating_eraser.suxia_pocket_watch.category")
                    .withStyle(ChatFormatting.GOLD);
        } else if (stack.is(ModItemTags.QIWU)) {
            label = Component.translatable("tooltip.sound_isolating_eraser.qiwu")
                    .withStyle(ChatFormatting.GOLD);
        } else if (stack.is(ModItemTags.DAOJU)) {
            label = Component.translatable("tooltip.sound_isolating_eraser.daoju")
                    .withStyle(style -> style.withColor(0x8FAAC4));
        } else {
            return;
        }
        // Run after ordinary tooltip additions, preserving the name and all existing lines.
        tooltip.add(1, label);
    }
}
