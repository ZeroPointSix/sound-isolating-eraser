package com.zeropointsix.suxiatest;

import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.suxia.SuxiaEntity;
import com.zeropointsix.eraser.suxia.SuxiaMotion;
import com.zeropointsix.eraser.suxia.SuxiaPocketWatchItem;
import com.zeropointsix.eraser.suxia.SuxiaSummons;
import com.zeropointsix.eraser.suxia.SuxiaSummonEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "suxia_qa")
public final class SuxiaTestServer {
    private static final boolean IDLE_ONLY = Boolean.getBoolean("suxia.qa.idleOnly");
    private static int idleStableTicks;
    private static final Path RESULTS = Path.of(System.getProperty("suxia.qa.results"));
    private static ServerPlayer player;
    private static int stage, ticks;
    private static UUID summoned, firstWatch, secondWatch, hostileId;
    private static SuxiaEntity hostile;
    private static final List<SuxiaEntity> gallery = new ArrayList<>();
    private static boolean coolingAttempt;
    private static long secondCooldown;
    private static long summonUntil;
    private static int recallTick = -1;
    private static boolean failed;

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void signal(String phase) {
        ticks = 0;
        player.sendSystemMessage(Component.literal("SUXIA_QA:" + phase));
        System.out.println("SUXIA_QA_PHASE=" + phase);
    }
    private static void pass(String phase, String evidence) throws Exception {
        Files.writeString(RESULTS.resolve("server-" + phase + ".pass"), evidence + "\n");
        System.out.println("SUXIA_QA_PASS=" + phase + " " + evidence);
    }
    private static CompoundTag state(SuxiaEntity mob) { return mob.saveWithoutId(new CompoundTag()); }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try { Files.writeString(RESULTS.resolve("server.failed"), error.toString()); } catch (Exception ignored) { }
    }

    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        level.setDayTime(6000); level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        for (BlockPos pos : BlockPos.betweenClosed(-16, 64, -20, 48, 64, 16)) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
        if (Files.exists(RESULTS.resolve("restart-state"))) {
            try {
                var saved = Files.readAllLines(RESULTS.resolve("restart-state"));
                hostileId = UUID.fromString(saved.get(0));
                secondWatch = UUID.fromString(saved.get(1));
                level.setChunkForced(Integer.parseInt(saved.get(2)), Integer.parseInt(saved.get(3)), true);
                secondCooldown = Long.parseLong(saved.get(4));
                stage = 20;
            } catch (Throwable error) { fail(error); }
        }
    }

    private static SuxiaEntity display(ServerLevel level, int x) {
        var entity = ModEntities.SUXIA.get().create(level);
        entity.setNoAi(true); entity.setPersistenceRequired();
        entity.moveTo(x, 65, 0, 180, 0); entity.setYHeadRot(180); entity.yBodyRot = 180;
        level.addFreshEntity(entity);
        gallery.add(entity);
        return entity;
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getGameProfile().getName().equals("SuxiaTester")) return;
        player = p; ticks = 0;
        try {
            if (stage == 7 || stage == 20) {
                var watch = p.getMainHandItem();
                require(watch.is(ModItems.SUXIA_POCKET_WATCH.get()) && watch.getTag().getLong("CooldownUntil") == secondCooldown,
                        "exact saved watch cooldown deadline survives actual reconnect");
                require(watch.getTag().getUUID("WatchUUID").equals(secondWatch), "same second watch survives reconnect/restart");
                p.setGameMode(GameType.CREATIVE);
                if (stage == 7) stage = 8;
                return;
            }
            require(stage == 0, "unexpected login");
            p.setGameMode(GameType.CREATIVE);
            p.getInventory().selected = 0;
            p.connection.send(new ClientboundSetCarriedItemPacket(0));
            p.teleportTo(p.serverLevel(), 0, 65, -12, 0, 5);
            p.getInventory().setItem(0, new ItemStack(ModItems.SUXIA_POCKET_WATCH.get()));
            p.getInventory().setItem(1, new ItemStack(ModItems.SUXIA_POCKET_WATCH.get()));
            p.getInventory().setItem(2, new ItemStack(ModItems.SUXIA_SPAWN_EGG.get()));
            if (IDLE_ONLY) return;
            display(p.serverLevel(), -3).setAggressive(true);
            display(p.serverLevel(), 0).setCrawling(true);
            display(p.serverLevel(), 3).setPlayingDead(true);
        } catch (Throwable error) { fail(error); }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (stage == 6 && event.getEntity() == player) { stage = 7; player = null; }
    }

    @SubscribeEvent public static void used(PlayerInteractEvent.RightClickItem event) {
        if ((stage == 2 || stage == 4) && event.getEntity() == player && !event.getLevel().isClientSide) {
            var tag = event.getItemStack().getTag();
            UUID expected = stage == 2 ? firstWatch : secondWatch;
            if (tag != null && tag.hasUUID("WatchUUID") && tag.getUUID("WatchUUID").equals(expected)) recallTick = ticks;
        }
        if (stage == 5 && event.getEntity() == player && !event.getLevel().isClientSide) coolingAttempt = true;
    }

    private static void idleTick() throws Exception {
        if (stage == 0 && ticks == 60) { signal("idle-ready"); stage = 1; }
        if (stage == 1 || stage == 3) {
            var active = SuxiaSummons.get(player.getServer()).active(player.getUUID());
            var entity = active == null ? null : player.serverLevel().getEntity(active.summon);
            boolean ready = false;
            if (entity instanceof SuxiaSummonEntity summon) {
                var behind = player.position().subtract(net.minecraft.world.phys.Vec3.directionFromRotation(0, player.getYRot()).scale(2));
                float yaw = stage == 1 ? 0 : 90, pitch = stage == 1 ? -20 : 35;
                ready = summon.getOwner() == player && summon.getTarget() == null && !summon.isAggressive()
                        && summon.position().subtract(behind).horizontalDistanceSqr() <= 0.75 * 0.75
                        && Math.abs(summon.getY() - player.getY()) < 0.5 && summon.getNavigation().isDone()
                        && Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot() - yaw)) < 1
                        && Math.abs(player.getXRot() - pitch) < 1
                        && Math.abs(net.minecraft.util.Mth.wrapDegrees(summon.getYHeadRot() - yaw)) < 5
                        && Math.abs(summon.getXRot() - pitch) < 5;
                if (ready && ++idleStableTicks >= 20) {
                    String name = stage == 1 ? "idle-stationary" : "idle-turned";
                    pass(name, "Actual item-use/look packets; owner=" + player.position() + " yaw=" + player.getYRot()
                            + " pitch=" + player.getXRot() + " summon=" + summon.position()
                            + " headYaw=" + summon.getYHeadRot() + " pitch=" + summon.getXRot()
                            + "; rear error <=0.75, view error <5 degrees, navigation stopped for 20 ticks.");
                    signal(name); stage++;
                }
            }
            if (!ready) idleStableTicks = 0;
        } else if (stage == 2 && Files.exists(RESULTS.resolve("client-idle-stationary.pass"))) {
            idleStableTicks = 0;
            signal("idle-turning"); stage = 3;
        } else if (stage == 4 && Files.exists(RESULTS.resolve("client-idle-turned.pass"))) {
            pass("idle-complete", "Real client summon, stationary rear positioning and owner look-packet turn verified.");
            stage = 5;
        }
        if (ticks > 300 && stage != 5) throw new AssertionError("Idle QA stage stalled: " + stage);
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || player == null) return;
        try {
            // The existing runner deadline bounds loading; behavior ticks start
            // only after the actual client renders the world and receives its watch.
            if (IDLE_ONLY && stage == 0 && !Files.exists(RESULTS.resolve("client-idle-ready.signal"))) return;
            ticks++;
            if (IDLE_ONLY) { idleTick(); return; }
            var ledger = SuxiaSummons.get(player.getServer());
            var active = ledger.active(player.getUUID());
            if (stage == 0 && ticks == 60) { signal("gallery"); stage = 1; }
            else if (stage == 1 && active != null) {
                summoned = active.summon;
                firstWatch = active.watch;
                summonUntil = active.until; recallTick = -1;
                require(active.until - SuxiaMotion.clock(player.level()) > 1100, "real use packet starts sixty-second summon");
                signal("summoned"); stage = 2;
            } else if (stage == 2 && active == null) {
                require(recallTick >= 0 && ticks - recallTick <= 2 && SuxiaMotion.clock(player.level()) + 200 < summonUntil,
                        "same-watch recall packet removes summon immediately, well before natural expiry");
                require(player.serverLevel().getEntity(summoned) == null, "real recall removes entity");
                var watch = player.getMainHandItem();
                require(watch.getTag().getInt("CooldownRemaining") > 3500 && !watch.getTag().getBoolean("Open"), "real recall closes watch and starts cooldown");
                pass("lifecycle", "Actual client summon/recall packets, entity lifecycle and cooldown verified.");
                signal("recalled"); stage = 3;
            } else if (stage == 3 && active != null) {
                secondWatch = active.watch;
                require(!secondWatch.equals(firstWatch), "actual slot switch uses another WatchUUID");
                require(ledger.find(firstWatch).cooldown > SuxiaMotion.clock(player.level()), "first watch still cooling while second summons");
                require(!player.getCooldowns().isOnCooldown(ModItems.SUXIA_POCKET_WATCH.get()), "no type-level use gate");
                summoned = active.summon;
                summonUntil = active.until; recallTick = -1;
                pass("multi-watch", "A remains cooling; normal client slot-switch and use packet summons B without clearing cooldowns.");
                signal("second-summoned"); stage = 4;
            } else if (stage == 4 && active == null) {
                require(recallTick >= 0 && ticks - recallTick <= 2 && SuxiaMotion.clock(player.level()) + 200 < summonUntil,
                        "second recall packet removes summon immediately, well before natural expiry");
                require(player.serverLevel().getEntity(summoned) == null, "second summon recalled");
                require(ledger.find(secondWatch).cooldown > SuxiaMotion.clock(player.level()), "second cooldown starts independently");
                secondCooldown = ledger.find(secondWatch).cooldown;
                signal("second-recalled"); stage = 5;
            } else if (stage == 5 && coolingAttempt) {
                require(active == null && ledger.find(secondWatch).cooldown > SuxiaMotion.clock(player.level()), "server rejects actual cooling-watch packet");
                pass("cooldown-rejected", "Actual cooling-watch use packet rejected by its ledger; no summon created.");
                signal("cooldown-rejected"); stage = 6;
            } else if (stage == 8 && ticks == 60) {
                require(!player.getCooldowns().isOnCooldown(ModItems.SUXIA_POCKET_WATCH.get())
                        && player.getMainHandItem().getTag().getLong("CooldownUntil") == secondCooldown,
                        "login restores exact per-watch deadline without global gate");
                pass("rejoin", "Actual reconnect retains watch UUID, binding and individual cooldown.");
                signal("rejoined"); stage = 9;
            } else if (stage == 9 && Files.exists(RESULTS.resolve("client-rejoined.pass"))) {
                gallery.forEach(SuxiaEntity::discard);
                var level = player.serverLevel();
                var before = level.getEntitiesOfClass(SuxiaEntity.class, new net.minecraft.world.phys.AABB(23, 64, -1, 26, 69, 2));
                require(before.isEmpty(), "command fixture is empty");
                int result = player.getServer().getCommands().performPrefixedCommand(
                        player.getServer().createCommandSourceStack().withPermission(4),
                        "summon sound_isolating_eraser:suxia 24 65 0 {}");
                require(result == 1, "actual empty-NBT summon command");
                hostile = level.getEntitiesOfClass(SuxiaEntity.class, new net.minecraft.world.phys.AABB(23, 64, -1, 26, 69, 2)).get(0);
                hostileId = hostile.getUUID();
                require(hostile.isPersistenceRequired(), "empty-NBT command persistent");
                ticks = 0; stage = 10;
            } else if (stage == 10 && hostile.isPlayingDead()) {
                player.teleportTo(player.serverLevel(), hostile.getX(), hostile.getY(), hostile.getZ() - 9, 0, 5);
                pass("sleep", "Live hostile naturally entered play-dead after 600 quiet ticks.");
                signal("hostile-sleep"); stage = 11;
            } else if (stage == 11 && Files.exists(RESULTS.resolve("client-hostile-sleep.pass"))) {
                var chunk = hostile.chunkPosition();
                player.serverLevel().setChunkForced(chunk.x, chunk.z, true);
                player.teleportTo(player.serverLevel(), hostile.getX() + 160, hostile.getY(), hostile.getZ(), 90, 5);
                ticks = 0; stage = 12;
            } else if (stage == 12 && ticks >= 100) {
                require(hostile.isAlive() && player.serverLevel().getEntity(hostileId) == hostile,
                        "loaded command hostile survives player beyond 128 blocks");
                require(player.serverLevel().isPositionEntityTicking(hostile.blockPosition()), "far hostile remains in an entity-ticking chunk");
                pass("persistence", "Empty-NBT command entity remained loaded/alive 100 ticks with the only player 160 blocks away.");
                player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                player.setHealth(1000);
                player.setGameMode(GameType.SURVIVAL);
                player.teleportTo(player.serverLevel(), hostile.getX(), hostile.getY(), hostile.getZ() - 2.8, 0, 5);
                ticks = 0; stage = 13;
            } else if (stage == 13 && !hostile.isPlayingDead()) {
                require(hostile.getTarget() == player, "normal survival proximity wakes and acquires player");
                require(state(hostile).getLong("SuxiaAmbushUntil") > SuxiaMotion.clock(player.level())
                        || player.getHealth() < 1000, "wake arms ambush or first strike already consumed it");
                pass("wake", "Live survival player within three blocks wakes hostile, acquires target and arms ambush.");
                signal("hostile-awake"); stage = 14;
            } else if (stage == 14 && Files.exists(RESULTS.resolve("client-hostile-awake.pass"))) {
                player.setGameMode(GameType.CREATIVE);
                player.teleportTo(player.serverLevel(), hostile.getX(), hostile.getY(), hostile.getZ() - 9, 0, 5);
                hostile.setPlayingDead(true);
                SuxiaPocketWatchItem.syncInventory(player);
                var chunk = hostile.chunkPosition();
                Files.writeString(RESULTS.resolve("restart-state"), hostileId + "\n" + secondWatch + "\n" + chunk.x + "\n" + chunk.z + "\n" + secondCooldown + "\n");
                pass("ready-restart", "Prepared real world save with sleeping persistent hostile and both cooling watches.");
                stage = 15;
            } else if (stage == 20 && ticks >= 40) {
                hostile = (SuxiaEntity) player.serverLevel().getEntity(hostileId);
                require(hostile != null && hostile.isPlayingDead() && hostile.isPersistenceRequired(), "hostile identity, sleep and persistence survive actual server restart");
                var entry = ledger.find(secondWatch);
                require(entry != null && !entry.active() && entry.cooldown == secondCooldown, "exact world ledger deadline survives actual process restart");
                player.teleportTo(player.serverLevel(), hostile.getX(), hostile.getY(), hostile.getZ() - 9, 0, 5);
                pass("restart", "Actual dedicated-server JVM restart restored same hostile UUID/sleep/persistence and watch ledger cooldown.");
                signal("restored"); stage = 21;
            } else if (stage == 21 && Files.exists(RESULTS.resolve("client-restored.pass"))) {
                player.setGameMode(GameType.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.teleportTo(player.serverLevel(), hostile.getX(), hostile.getY(), hostile.getZ() - 2.8, 0, 5);
                ticks = 0; stage = 22;
            } else if (stage == 22 && !hostile.isPlayingDead()) {
                require(hostile.getUUID().equals(hostileId) && hostile.getTarget() == player,
                        "restored hostile wakes and resumes targeting same live player");
                pass("restored-wake", "Restored persistent hostile woke again after actual restart and survival-player approach.");
                signal("restored-awake"); stage = 23;
            }
            if (ticks > 1800 && stage != 15 && stage != 23) throw new AssertionError("QA stage stalled: " + stage);
        } catch (Throwable error) { fail(error); }
    }
}
