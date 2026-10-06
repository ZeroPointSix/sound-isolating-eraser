package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.wings.net.SyncWingsPacket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Client-side mirror of server wing states (for render + HUD). */
public final class WingsClientData {
    public record WingInfo(boolean deployed, int tier, long chargedUntil, long blinkCooldownUntil,
                           float deployAnim, int lastSeenTier, double hoverY) {
        public WingInfo withAnim(float a) {
            return new WingInfo(deployed, tier, chargedUntil, blinkCooldownUntil, a, lastSeenTier, hoverY);
        }
        public WingInfo withTier(int t) {
            return new WingInfo(deployed, tier, chargedUntil, blinkCooldownUntil, deployAnim, t, hoverY);
        }
    }

    private static final Map<Integer, WingInfo> STATES = new ConcurrentHashMap<>();
    private static int flashTicks = 0;

    private WingsClientData() {}

    public static void apply(SyncWingsPacket p) {
        STATES.compute(p.entityId(), (id, old) -> {
            float anim = old == null ? (p.deployed() ? 0f : 1f) : old.deployAnim();
            int seen = old == null ? p.tier() : old.tier();
            return new WingInfo(p.deployed(), p.tier(), p.chargedUntil(),
                    p.blinkCooldownUntil(), anim, seen, p.hoverY());
        });
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == p.entityId()) {
            mc.player.setNoGravity(p.deployed());
        }
        if (oldTierChanged(p)) {
            onTierChanged(p);
        }
    }

    private static boolean oldTierChanged(SyncWingsPacket p) {
        WingInfo w = STATES.get(p.entityId());
        return w != null && w.lastSeenTier() != p.tier();
    }

    private static void onTierChanged(SyncWingsPacket p) {
        WingInfo w = STATES.get(p.entityId());
        if (w == null) return;
        int prev = w.lastSeenTier();
        STATES.put(p.entityId(), w.withTier(p.tier()));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getId() != p.entityId() || mc.level == null) return;
        if (p.tier() >= 2 && p.tier() > prev) {
            WingsFlight.sonicBoom(mc.player);
        }
        mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                        "message.sound_isolating_eraser.tier",
                        net.minecraft.network.chat.Component.translatable(
                                "sound_isolating_eraser.tier." + p.tier())), true);
    }

    public static WingInfo get(int entityId) {
        return STATES.get(entityId);
    }

    public static WingInfo get(Player p) {
        return STATES.get(p.getId());
    }

    public static void setAnim(int entityId, float v) {
        STATES.computeIfPresent(entityId, (id, w) -> w.withAnim(v));
    }

    public static void flash(int ticks) { flashTicks = ticks; }
    public static int flashTicks() { return flashTicks; }
    public static void tickFlash() { if (flashTicks > 0) flashTicks--; }

    public static void clear() { STATES.clear(); }

    public static WingInfo localOrFallback(Player p) {
        WingInfo w = get(p);
        if (w != null) return w;
        return new WingInfo(false, 0, 0, 0, 1f, 0, 0);
    }
}
