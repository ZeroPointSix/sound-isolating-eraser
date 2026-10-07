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
                           long blinkLockUntil, float deployAnim, int lastSeenTier, double hoverY) {
        public WingInfo withAnim(float a) {
            return new WingInfo(deployed, tier, chargedUntil, blinkCooldownUntil, blinkLockUntil,
                    a, lastSeenTier, hoverY);
        }
        public WingInfo withTier(int t) {
            return new WingInfo(deployed, tier, chargedUntil, blinkCooldownUntil, blinkLockUntil,
                    deployAnim, t, hoverY);
        }
    }

    private static final Map<Integer, WingInfo> STATES = new ConcurrentHashMap<>();
    private static final Map<Integer, WingsMotion> MOTION = new ConcurrentHashMap<>();
    private static int flashTicks = 0;

    private WingsClientData() {}

    public static void apply(SyncWingsPacket p) {
        STATES.compute(p.entityId(), (id, old) -> {
            float anim = old == null ? 0f : old.deployAnim();
            int seen = old == null ? p.tier() : old.tier();
            return new WingInfo(p.deployed(), p.tier(), p.chargedUntil(),
                    p.blinkCooldownUntil(), p.blinkLockUntil(), anim, seen, p.hoverY());
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
        if (mc.level == null) return;
        // 远端玩家升快档也要播冲击环+音爆（位置用实体本身的，声音按世界坐标定位）
        if (p.tier() >= 2 && p.tier() > prev) {
            Entity e = mc.level.getEntity(p.entityId());
            if (e instanceof Player player) WingsFlight.sonicBoom(player);
        }
        // 只有本机玩家弹档位提示
        if (mc.player != null && mc.player.getId() == p.entityId()) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.sound_isolating_eraser.tier",
                            net.minecraft.network.chat.Component.translatable(
                                    "sound_isolating_eraser.tier." + p.tier())), true);
        }
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

    public static void clear() { STATES.clear(); MOTION.clear(); flashTicks = 0; }

    public static void remove(int entityId) { STATES.remove(entityId); MOTION.remove(entityId); }

    public static void tickAnimation(Player player, WingInfo info) {
        WingsMotion motion = MOTION.computeIfAbsent(player.getId(), id -> new WingsMotion(info.deployAnim()));
        double maximum = Math.max(1, com.zeropointsix.eraser.wings.WingsConfig.SERVER.tierSpeed(3) / 20.0);
        // Remote player movement is interpolated from position packets, not local flight velocity.
        double speed = player == Minecraft.getInstance().player ? player.getDeltaMovement().length()
                : player.position().subtract(player.xo, player.yo, player.zo).length();
        motion.tick(info.deployed(), info.tier(), (float) (speed / maximum));
        setAnim(player.getId(), motion.deployment());
    }

    public static WingsMotion.Pose renderPose(Player player, float partialTick) {
        WingsMotion motion = MOTION.get(player.getId());
        return motion == null ? new WingsMotion.Pose(0, 0, 0, 0) : motion.sample(partialTick);
    }

    public static WingInfo localOrFallback(Player p) {
        WingInfo w = get(p);
        if (w != null) return w;
        return new WingInfo(false, 0, 0, 0, 0, 0f, 0, 0);
    }
}
