package com.zeropointsix.eraser.wings;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import com.zeropointsix.eraser.item.WindThunderWingsItem;

/** Server-authoritative per-player wing state, kept in persistent NBT. */
public final class WingsState {
    public static final int HOVER = 0, CRUISE = 1, BOOST = 2, STORM = 3;
    public static final int TIER_COUNT = 4;

    private static final String ROOT = "sound_isolating_eraser";
    private static final String DEPLOYED = "deployed";
    private static final String TIER = "tier";
    private static final String PREV_TIER = "prev_tier";
    private static final String HOVER_Y = "hover_anchor_y";
    private static final String BLINK_TIMES = "blink_times";
    private static final String BLINK_CD = "blink_cd_until";
    private static final String CHARGED = "charged_until";
    private static final String WALL_HIT = "wall_hit_at";

    private WingsState() {}

    private static CompoundTag root(Player p) {
        CompoundTag all = p.getPersistentData();
        if (!all.contains(ROOT)) all.put(ROOT, new CompoundTag());
        return all.getCompound(ROOT);
    }

    public static boolean deployed(Player p) { return root(p).getBoolean(DEPLOYED); }
    public static int tier(Player p) { return Math.min(root(p).getInt(TIER), STORM); }
    public static int prevTier(Player p) { return Math.max(root(p).getInt(PREV_TIER), CRUISE); }
    public static double hoverY(Player p) { return root(p).getDouble(HOVER_Y); }
    public static long blinkCooldownUntil(Player p) { return root(p).getLong(BLINK_CD); }
    public static long chargedUntil(Player p) { return root(p).getLong(CHARGED); }
    public static long wallHitAt(Player p) { return root(p).getLong(WALL_HIT); }
    public static long[] blinkTimes(Player p) { return root(p).getLongArray(BLINK_TIMES); }

    public static void setDeployed(Player p, boolean v) {
        CompoundTag t = root(p);
        boolean old = t.getBoolean(DEPLOYED);
        t.putBoolean(DEPLOYED, v);
        if (v && !old) {
            t.putInt(PREV_TIER, Math.max(t.getInt(TIER), CRUISE));
            t.putInt(TIER, HOVER);
            t.putDouble(HOVER_Y, p.getY());
        }
        if (!v) {
            p.setNoGravity(false);
        }
    }

    public static void setTier(Player p, int tier) {
        CompoundTag t = root(p);
        t.putInt(PREV_TIER, Math.max(t.getInt(TIER), CRUISE));
        t.putInt(TIER, Math.min(Math.max(tier, 0), STORM));
        if (tier == HOVER) t.putDouble(HOVER_Y, p.getY());
    }

    public static void setHoverAnchor(Player p, double y) { root(p).putDouble(HOVER_Y, y); }
    public static void setBlinkCooldownUntil(Player p, long t) { root(p).putLong(BLINK_CD, t); }
    public static void setChargedUntil(Player p, long t) { root(p).putLong(CHARGED, t); }
    public static void setWallHitAt(Player p, long t) { root(p).putLong(WALL_HIT, t); }
    public static void setBlinkTimes(Player p, long[] t) { root(p).putLongArray(BLINK_TIMES, t); }

    public static boolean charged(Player p) {
        return p.level().getGameTime() < chargedUntil(p);
    }

    public static boolean wearing(Player p) {
        return p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof WindThunderWingsItem;
    }

    public static boolean flying(Player p) {
        return wearing(p) && deployed(p) && !p.onGround();
    }
}
