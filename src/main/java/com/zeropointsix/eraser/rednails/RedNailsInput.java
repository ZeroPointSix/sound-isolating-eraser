package com.zeropointsix.eraser.rednails;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Press/release protocol: the server, not a claimed client duration, authorizes a chant. */
public final class RedNailsInput {
    private static final Map<UUID, Chant> CHANTS = new HashMap<>();
    private static final Map<UUID, Long> PRESSED_AT = new HashMap<>();
    private static final Map<UUID, Long> RELEASED_AT = new HashMap<>();
    private static final class Chant {
        final ItemStack stack;
        final long start;
        boolean fired;
        Chant(ItemStack stack, long start) { this.stack = stack; this.start = start; }
    }
    public static boolean chanting(UUID player) { return CHANTS.containsKey(player); }
    public static long pressedAt(UUID player) { return PRESSED_AT.getOrDefault(player, Long.MIN_VALUE); }
    public static long releasedAt(UUID player) { return RELEASED_AT.getOrDefault(player, Long.MIN_VALUE); }
    public static void key(ServerPlayer player, boolean down) {
        long now = RedNailsState.clock(player.getServer());
        if (!down) {
            RELEASED_AT.put(player.getUUID(), now);
            cancel(player.getUUID());
            return;
        }
        PRESSED_AT.put(player.getUUID(), now);
        if (CHANTS.containsKey(player.getUUID()) || !player.isAlive() || player.isSpectator()) return;
        var stack = RedNailsItem.findWorn(player);
        if (stack.isEmpty()) { RedNailsItem.message(player, "not_worn"); return; }
        CHANTS.put(player.getUUID(), new Chant(stack, now));
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
    public static void clear() { CHANTS.clear(); PRESSED_AT.clear(); RELEASED_AT.clear(); }
    private RedNailsInput() { }
}
