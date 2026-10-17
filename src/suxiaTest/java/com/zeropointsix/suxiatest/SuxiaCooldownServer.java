package com.zeropointsix.suxiatest;

import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.suxia.SuxiaMotion;
import com.zeropointsix.eraser.suxia.SuxiaSummonEntity;
import com.zeropointsix.eraser.suxia.SuxiaSummons;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/** Supplemental real-client evidence only; never writes watch or ledger cooldowns. */
public final class SuxiaCooldownServer {
    static final boolean ENABLED = Boolean.getBoolean("suxia.qa.cooldownOnly");
    private static final boolean DEATH = Boolean.getBoolean("suxia.qa.deathCooldown");
    private static final int EXPECTED_DURATION = DEATH ? 12000 : 3600;
    private static final Path OUT = Path.of(System.getProperty("suxia.qa.results"));
    private static ServerPlayer player;
    private static UUID watchId, ownerId, droppedId;
    private static long deadline, priorClock, lifecycleClock = -1;
    private static int stage, ticks, pickedTick;
    private static boolean attempted, failed;
    private static UUID teleportId;
    private static Vec3 previousPosition;
    private static double initialDistance, maximumDistance, maximumStep;

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try { Files.writeString(OUT.resolve("server.failed"), error.toString()); } catch (Exception ignored) { }
    }
    private static void pass(String name, String evidence) throws Exception {
        Files.writeString(OUT.resolve("server-" + name + ".pass"), evidence + "\n");
        System.out.println("SUXIA_COOLDOWN_PASS=" + name + " " + evidence);
    }
    private static void signal(String name, int next) {
        player.sendSystemMessage(Component.literal("SUXIA_COOLDOWN:" + name));
        stage = next; ticks = 0; attempted = false;
    }
    static void prepare(ServerStartedEvent event) {
        try {
            var level = event.getServer().overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
            for (var pos : BlockPos.betweenClosed(-8, 64, -20, 8, 64, 0))
                level.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            for (var pos : BlockPos.betweenClosed(24, 64, -20, 40, 64, 0))
                level.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            var saved = OUT.resolve("cooldown-restart-state");
            if (Files.exists(saved)) {
                var lines = Files.readAllLines(saved);
                watchId = UUID.fromString(lines.get(0)); ownerId = UUID.fromString(lines.get(1));
                deadline = Long.parseLong(lines.get(2)); priorClock = Long.parseLong(lines.get(3));
                require(event.getServer().getPlayerList().getPlayerCount() == 0 && player == null,
                        "SavedData is observed before any player login or inventory bind");
                var entry = SuxiaSummons.get(event.getServer()).find(watchId);
                require(entry != null && entry.owner.equals(ownerId) && !entry.active()
                        && entry.cooldown == deadline && entry.cooldownDuration == EXPECTED_DURATION,
                        "disk SavedData restored exact watch entry before inventory exists");
                long restoredClock = SuxiaMotion.clock(level);
                require(restoredClock >= priorClock && deadline > restoredClock,
                        "saved clock is monotonic and cooldown positive before login");
                pass("ledger-prelogin", "players=0 watch=" + watchId + " owner=" + ownerId
                        + " until=" + entry.cooldown + " duration=" + entry.cooldownDuration
                        + " clock=" + restoredClock + " priorClock=" + priorClock + " inventoryBind=false");
                stage = 20;
            }
            System.out.println("SUXIA_COOLDOWN_SERVER_READY stage=" + stage);
        } catch (Throwable error) { fail(error); }
    }
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getGameProfile().getName().equals("SuxiaTester")) return;
        player = p; ticks = 0;
        try {
            if (stage == 0) {
                p.setGameMode(GameType.CREATIVE);
                p.teleportTo(p.serverLevel(), 0, 65, -12, 0, 0);
                p.getInventory().selected = 0;
                p.connection.send(new ClientboundSetCarriedItemPacket(0));
                p.getInventory().setItem(0, new ItemStack(ModItems.SUXIA_POCKET_WATCH.get()));
                p.getInventory().setItem(1, new ItemStack(ModItems.SUXIA_POCKET_WATCH.get()));
            } else {
                require(stage == 10 || stage == 20, "unexpected reconnect stage=" + stage);
                require(p.getUUID().equals(ownerId), "same real player identity");
                var tag = p.getMainHandItem().getTag();
                require(tag != null && tag.getUUID("WatchUUID").equals(watchId)
                        && tag.getLong("CooldownUntil") == deadline, "persisted watch identity/deadline at login");
                if (stage == 10) stage = 11;
            }
        } catch (Throwable error) { fail(error); }
    }
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() == player) player = null;
    }
    static void used(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide && event.getEntity() == player && stage == 2 && !DEATH)
            lifecycleClock = SuxiaMotion.clock(event.getLevel());
        if (!event.getLevel().isClientSide && event.getEntity() == player
                && (stage == 3 || stage == 6 || stage == 12 || stage == 21)) attempted = true;
    }
    private static String verify(String name) throws Exception {
        var tag = player.getMainHandItem().getTag();
        var ledger = SuxiaSummons.get(player.getServer());
        var entry = ledger.find(watchId);
        long now = SuxiaMotion.clock(player.level()), remaining = deadline - now;
        require(tag != null && tag.getUUID("WatchUUID").equals(watchId), "same WatchUUID " + name);
        require(tag.getUUID("Owner").equals(ownerId), "same owner " + name);
        require(entry != null && entry.cooldown == deadline && tag.getLong("CooldownUntil") == deadline,
                "same exact ledger/item deadline " + name);
        require(now >= priorClock && remaining > 0, "positive cooldown and monotonic saved clock " + name);
        require(!entry.active() && ledger.active(ownerId) == null && !tag.getBoolean("Open"), "no active summon " + name);
        require(tag.getInt("CooldownRemaining") > 0
                && Math.abs(tag.getInt("CooldownRemaining") - remaining) <= 21, "inventory countdown sync " + name);
        require(entry.cooldownDuration == EXPECTED_DURATION && tag.getInt("CooldownDuration") == EXPECTED_DURATION,
                "exact expected cooldown duration");
        require(!player.getCooldowns().isOnCooldown(ModItems.SUXIA_POCKET_WATCH.get()), "no global item gate");
        String evidence = "watch=" + watchId + " owner=" + ownerId + " clock=" + now + " until=" + deadline
                + " remaining=" + remaining + " itemRemaining=" + tag.getInt("CooldownRemaining")
                + " duration=" + entry.cooldownDuration + " active=false";
        priorClock = now;
        System.out.println("SUXIA_COOLDOWN_STATE=" + name + " " + evidence);
        return evidence;
    }
    static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || player == null) return;
        try {
            if (stage == 0 && !Files.exists(OUT.resolve("client-cool-ready.signal"))) return;
            ticks++;
            var ledger = SuxiaSummons.get(player.getServer());
            var active = ledger.active(player.getUUID());
            if (stage == 0) signal("cool-ready", 1);
            else if (stage == 1 && active != null) {
                watchId = active.watch; ownerId = player.getUUID();
                var entity = player.serverLevel().getEntity(active.summon);
                require(entity instanceof SuxiaSummonEntity, "actual item-use spawned summon");
                teleportId = entity.getUUID(); previousPosition = entity.position();
                player.teleportTo(player.serverLevel(), 32, 65, -12, 0, 0);
                initialDistance = entity.distanceTo(player); maximumDistance = initialDistance; maximumStep = 0;
                require(initialDistance > 12 && initialDistance < 64, "isolate 12-64 path");
                signal("cool-teleport", 8);
            } else if (stage == 8) {
                require(active != null && teleportId.equals(active.summon), "same active summon throughout teleport");
                var entity = (SuxiaSummonEntity) player.serverLevel().getEntity(teleportId);
                require(entity != null && entity.isAlive() && !entity.isNoAi() && entity.getTarget() == null,
                        "real noncombat FollowOwner AI remains enabled");
                double distance = entity.distanceTo(player);
                maximumDistance = Math.max(maximumDistance, distance);
                maximumStep = Math.max(maximumStep, entity.position().distanceTo(previousPosition));
                previousPosition = entity.position();
                require(maximumDistance < 64 && ledger.find(watchId).cooldown == 0, "no >64 fallback or cooldown");
                if (distance <= 6 && maximumStep > 8) {
                    pass("teleport-12-64", "sameEntity=" + teleportId + " initialDistance=" + initialDistance
                            + " maxDistance=" + maximumDistance + " finalDistance=" + distance
                            + " maxOneTickStep=" + maximumStep + " observedTicks=" + ticks + " ai=true cooldown=0");
                    if (DEATH) {
                        lifecycleClock = SuxiaMotion.clock(player.level());
                        require(entity.hurt(entity.damageSources().genericKill(), Float.MAX_VALUE) && !entity.isAlive(),
                                "real lethal damage invokes normal death lifecycle");
                        require(player.hasEffect(MobEffects.CONFUSION) && player.hasEffect(MobEffects.WEAKNESS),
                                "death backlash effects applied");
                        require(player.getEffect(MobEffects.CONFUSION).getDuration() == 100
                                && player.getEffect(MobEffects.WEAKNESS).getDuration() == 200,
                                "exact death backlash durations");
                        pass("death-stimulus", "damage=genericKill health=" + entity.getHealth()
                                + " nausea=" + player.getEffect(MobEffects.CONFUSION).getDuration()
                                + " weakness=" + player.getEffect(MobEffects.WEAKNESS).getDuration());
                        signal("cool-death", 2);
                    } else signal("cool-summoned", 2);
                }
            } else if (stage == 2 && active == null) {
                deadline = ledger.find(watchId).cooldown;
                require(lifecycleClock >= 0 && deadline == lifecycleClock + EXPECTED_DURATION,
                        "exact cooldown deadline from the real lifecycle event");
                require(deadline - SuxiaMotion.clock(player.level()) > EXPECTED_DURATION - 100,
                        "real lifecycle started full expected cooldown");
                pass("cool-start", verify(DEATH ? "lethal damage" : "recall"));
                signal("cool-before", 3);
            } else if (stage == 3 && attempted && Files.exists(OUT.resolve("client-cool-before.pass"))) {
                pass("cool-before-rejected", verify("actual use rejected before boundaries"));
                signal("cool-drop", 4);
            } else if (stage == 4) {
                var items = player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8),
                        item -> item.getItem().hasTag() && item.getItem().getTag().hasUUID("WatchUUID")
                                && item.getItem().getTag().getUUID("WatchUUID").equals(watchId));
                if (!items.isEmpty()) {
                    var item = items.get(0); droppedId = item.getUUID();
                    require(player.getInventory().getItem(0).isEmpty(), "actual drop removed inventory item");
                    require(item.getItem().getTag().getLong("CooldownUntil") == deadline, "drop entity retains deadline");
                    pass("cool-dropped", "ItemEntity=" + droppedId + " watch=" + watchId + " until=" + deadline
                            + " clock=" + SuxiaMotion.clock(player.level()) + " itemRemaining=" + item.getItem().getTag().getInt("CooldownRemaining"));
                    stage = 5; ticks = 0;
                }
            } else if (stage == 5) {
                var item = player.serverLevel().getEntity(droppedId);
                if (item instanceof ItemEntity && ticks == 30)
                    player.teleportTo(player.serverLevel(), item.getX(), item.getY(), item.getZ(), 0, 0);
                if (!player.getInventory().getItem(0).isEmpty() && item == null) {
                    System.out.println("SUXIA_COOLDOWN_PICKUP_OBSERVED clock=" + SuxiaMotion.clock(player.level())
                            + " itemRemaining=" + player.getMainHandItem().getTag().getInt("CooldownRemaining"));
                    pickedTick = ticks; stage = 7;
                }
            } else if (stage == 7 && ticks - pickedTick >= 20) {
                // Let the existing 20-tick inventory updater run; do not sync or mutate it from QA.
                pass("cool-picked", verify("natural ItemEntity pickup after normal inventory tick"));
                signal("cool-picked", 6);
            } else if (stage == 6 && attempted && Files.exists(OUT.resolve("client-cool-picked.pass"))) {
                pass("cool-picked-rejected", verify("actual use rejected after pickup"));
                signal("cool-disconnect-rejoin", 10);
            } else if (stage == 11 && ticks >= 40) {
                pass("cool-rejoined", verify("real reconnect"));
                signal("cool-rejoined", 12);
            } else if (stage == 12 && attempted && Files.exists(OUT.resolve("client-cool-rejoined.pass"))) {
                pass("cool-rejoined-rejected", verify("actual use rejected after reconnect"));
                Files.writeString(OUT.resolve("cooldown-restart-state"), watchId + "\n" + ownerId + "\n" + deadline + "\n" + priorClock + "\n");
                pass("ready-restart", verify("before real JVM stop"));
                signal("cool-disconnect-restart", 30);
            } else if (stage == 20 && ticks >= 40) {
                pass("cool-restarted", verify("new JVM loaded SavedData and player inventory"));
                signal("cool-restarted", 21);
            } else if (stage == 21 && attempted && Files.exists(OUT.resolve("client-cool-restarted.pass"))) {
                pass("cool-complete", verify("actual use rejected after JVM restart")); stage = 40;
            }
            if (ticks > 300 && stage != 10 && stage != 30 && stage != 40)
                throw new AssertionError("Cooldown behavior stage exceeded 300 ticks: " + stage);
        } catch (Throwable error) { fail(error); }
    }
}
