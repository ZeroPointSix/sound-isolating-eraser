package com.zeropointsix.eraser.rednails;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Press/release protocol: the server, not a claimed client duration, authorizes a chant. */
public final class RedNailsInput {
    private static final Map<UUID, Chant> CHANTS = new HashMap<>();
    private static final class Chant {
        final ItemStack stack;
        final long start;
        boolean fired;
        Chant(ItemStack stack, long start) { this.stack = stack; this.start = start; }
    }
    public static void key(ServerPlayer player, boolean down) {
        if (!down) { cancel(player.getUUID()); return; }
        if (CHANTS.containsKey(player.getUUID()) || !player.isAlive() || player.isSpectator()) return;
        var stack = RedNailsItem.findWorn(player);
        if (stack.isEmpty()) { RedNailsItem.message(player, "not_worn"); return; }
        CHANTS.put(player.getUUID(), new Chant(stack, RedNailsState.clock(player.getServer())));
    }
    public static void tick(ServerPlayer player) {
        var chant = CHANTS.get(player.getUUID());
        if (chant == null) return;
        if (!player.isAlive() || player.isSpectator() || player.containerMenu != player.inventoryMenu
                || chant.stack != RedNailsItem.findWorn(player)) { cancel(player.getUUID()); return; }
        if (!chant.fired && RedNailsState.clock(player.getServer()) - chant.start >= 20) {
            chant.fired = true;
            RedNailsItem.activate(player);
        }
    }
    public static void cancel(UUID player) { CHANTS.remove(player); }
    public static void clear() { CHANTS.clear(); }
    private RedNailsInput() { }
}
