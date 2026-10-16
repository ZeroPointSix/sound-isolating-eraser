package com.zeropointsix.eraser.memory;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Shared NBT contract for soul-bound memories; no client-only classes. */
public final class PreciousMemories {
    public static boolean accepts(ItemStack stack, Player player) {
        var tag = stack.getTag();
        return tag == null || !tag.hasUUID("Owner") || tag.getUUID("Owner").equals(player.getUUID());
    }

    public static void bind(ItemStack stack, Player player, UUID owner) {
        var tag = stack.getOrCreateTag();
        tag.putUUID("Owner", owner);
        if (!tag.contains("OwnerName")) tag.putString("OwnerName", player.getName().getString());
    }

    public static void firstUse(ItemStack stack, Player player, String prefix, int lines) {
        var tag = stack.getOrCreateTag();
        if (tag.getBoolean("FirstUseShown")) return;
        for (int i = 1; i <= lines; i++) player.displayClientMessage(
                Component.translatable(prefix + "first_use_" + i).withStyle(ChatFormatting.DARK_RED), false);
        tag.putBoolean("FirstUseShown", true);
    }

    private PreciousMemories() { }
}
