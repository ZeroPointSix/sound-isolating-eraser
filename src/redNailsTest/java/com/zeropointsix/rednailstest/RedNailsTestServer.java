package com.zeropointsix.rednailstest;

import com.zeropointsix.eraser.rednails.*;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

@Mod.EventBusSubscriber(modid = "red_nails_qa")
public final class RedNailsTestServer {
    private static final boolean PURSUIT = Boolean.getBoolean("rednails.qa.pursuit");
    private static final Path RESULTS = Path.of(System.getProperty("rednails.qa.results"));
    private static ServerPlayer player;
    private static int stage, ticks;
    private static boolean failed, shortPressAcked, shortPressSeen, shortPressReleased;
    private static int shortPressQuiet;
    private static UUID itemId;
    private static long deadline, stageNanos;
    private static WaterGhostEntity swimmer;
    private static net.minecraft.world.entity.monster.Pillager shoreVictim;
    private static WaterGhostSummonEntity shoreGhost;
    private static boolean shoreGrabbed, wildGrabbed;
    private static long waterAt = -1, wildGrabAt = -1;
    private static double waterY, minWaterY, wildStartX, wildStartZ;
    private static double wildPlayerMoveBeforeGrab, wildGhostMoveBeforeGrab;
    private static int airTicks, extraAirTicks, previousAir = -1;
    private static net.minecraft.world.phys.Vec3 wildPlayerStart;
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void pass(String name, String evidence) throws Exception {
        Files.writeString(RESULTS.resolve("server-" + name + ".pass"), evidence + "\n");
        System.out.println("RED_NAILS_QA_PASS=" + name + " " + evidence);
    }
    private static void signal(String phase) {
        ticks = 0;
        stageNanos = System.nanoTime();
        player.sendSystemMessage(Component.literal("RED_NAILS_QA:" + phase));
        System.out.println("RED_NAILS_QA_STAGE=" + phase);
    }
    private static boolean captured(String phase) { return Files.exists(RESULTS.resolve("client-" + phase + ".pass")); }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try { Files.writeString(RESULTS.resolve("server.failed"), error.toString()); } catch (Exception ignored) { }
    }
    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        if (Boolean.getBoolean("rednails.qa.acceptance")) return;
        var level = event.getServer().overworld();
        level.setDayTime(6000); level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, event.getServer());
        for (var pos : BlockPos.betweenClosed(-16, 69, -20, 16, 69, 20)) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        if (PURSUIT) {
            for (var pos : BlockPos.betweenClosed(-16, 70, -20, 16, 76, 20)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setDefaultSpawnPos(new BlockPos(0, 70, 0), 0);
            level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, event.getServer());
        }
        if (Files.exists(RESULTS.resolve("expected-state"))) {
            try {
                var saved = Files.readAllLines(RESULTS.resolve("expected-state"));
                itemId = UUID.fromString(saved.get(0)); deadline = Long.parseLong(saved.get(1)); stage = 10;
            } catch (Throwable error) { fail(error); }
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (Boolean.getBoolean("rednails.qa.acceptance")) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getGameProfile().getName().equals("NailsTester")) return;
        player = p; ticks = 0; stageNanos = System.nanoTime();
        p.setGameMode(GameType.SURVIVAL);
        if (!PURSUIT) p.teleportTo(p.serverLevel(), 0.5, 70, -10.5, 0, 0);
        if (stage == 0) {
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
            p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() == player) player = null;
    }
    private static boolean equipRing() {
        var inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        var ring = inventory == null ? null : inventory.getStacksHandler("ring").orElse(null);
        if (ring == null) return false;
        if (RedNailsItem.findWorn(player).isEmpty()) {
            ring.getStacks().setStackInSlot(0, new ItemStack(ModItems.RED_NAILS.get()));
        }
        return !RedNailsItem.findWorn(player).isEmpty();
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || player == null || failed) return;
        if (player.hasDisconnected() || player.isRemoved()) { player = null; return; }
        try {
            ticks++;
            // Wall-clock, not tick count: catch-up after a join hitch can burn 1800 ticks
            // in a few seconds before the real client has processed RED_NAILS_QA:input.
            if (System.nanoTime() - stageNanos > 300_000_000_000L) {
                var current = RedNailsState.get(player.getServer()).read(RedNailsItem.findWorn(player));
                throw new AssertionError("QA stage timeout: " + stage + " active=" + (current == null ? 0 : current.active())
                        + " pressAt=" + RedNailsInput.pressedAt(player.getUUID())
                        + " releaseAt=" + RedNailsInput.releasedAt(player.getUUID())
                        + " serverClock=" + RedNailsState.clock(player.getServer()));
            }
            if (PURSUIT && pursuitTick()) return;
            if (stage == 0) {
                if (ticks >= 30 && !equipRing()) return;
                if (ticks >= 60 && Files.exists(RESULTS.resolve("client-ready.signal"))) {
                    stage = 1; signal("input");
                }
            } else if (stage == 1) {
                var stack = RedNailsItem.findWorn(player);
                var entry = RedNailsState.get(player.getServer()).read(stack);
                if (!shortPressAcked) {
                    UUID id = player.getUUID();
                    if (!shortPressSeen) {
                        if (RedNailsInput.pressedAt(id) == Long.MIN_VALUE) return;
                        shortPressSeen = true;
                    }
                    if (!shortPressReleased) {
                        if (RedNailsInput.releasedAt(id) < RedNailsInput.pressedAt(id)) return;
                        require(!RedNailsInput.chanting(id), "Release packet cleared the chant");
                        require(entry == null, "Short real-client key hold must not summon");
                        shortPressReleased = true;
                        shortPressQuiet = 0;
                        return;
                    }
                    shortPressQuiet++;
                    require(entry == null && !RedNailsInput.chanting(id),
                            "No-summon interval after observed press/release");
                    if (shortPressQuiet < 15) return;
                    Files.writeString(RESULTS.resolve("server-short-press.ack"),
                            "pressAt=" + RedNailsInput.pressedAt(id)
                                    + " releaseAt=" + RedNailsInput.releasedAt(id)
                                    + " quietTicks=" + shortPressQuiet
                                    + " chanting=false entry=null\n");
                    shortPressAcked = true;
                    return;
                }
                if (PURSUIT && entry != null && entry.active() > 0) {
                    var ack = RESULTS.resolve("server-long-press-" + entry.active() + ".ack");
                    if (!Files.exists(ack)) {
                        long pressAt = RedNailsInput.pressedAt(player.getUUID());
                        long now = RedNailsState.clock(player.getServer());
                        require(RedNailsInput.chanting(player.getUUID()) && now - pressAt >= 20,
                                "Actual server hold reaches the unchanged 20-tick threshold");
                        String evidence = "active=" + entry.active() + " pressAt=" + pressAt
                                + " observedAt=" + now + " heldTicks=" + (now - pressAt) + "\n";
                        Files.writeString(ack, evidence);
                        System.out.print("RED_NAILS_LONG_PRESS_ACK " + evidence);
                    }
                }
                if (entry != null && entry.active() == 4) {
                    itemId = entry.item;
                    require(entry.owner.equals(player.getUUID()) && stack.getTag().getBoolean("FirstUseShown"), "Bound first-use state");
                    for (int n : RedNailsState.ORDER) {
                        var g = RedNailsItem.resolve(player.getServer(), entry.ghosts[n]);
                        require(g != null && g.nail() == n && g.position().distanceToSqr(g.guardPosition(player)) < 1,
                                "Actual summons occupy four owner-relative guard positions");
                    }
                    pass("input", "Real client key mapping -> channel -> server chant -> four bound summons; short hold rejected.");
                    stage = 2; signal("guard");
                }
            } else if (stage == 2 && captured("guard")) {
                var entry = RedNailsState.get(player.getServer()).find(itemId);
                var ghost = RedNailsItem.resolve(player.getServer(), entry.ghosts[1]);
                ghost.hurt(ghost.damageSources().genericKill(), 1000);
                require(Math.abs(player.getHealth() - 16) < 0.01 && entry.active() == 3, "Diamond armor does not absorb backlash");
                deadline = entry.ready[1];
                require(deadline == RedNailsState.clock(player.getServer()) + 12000, "Exact ten-minute cooldown");
                if (PURSUIT) {
                    for (int nail : new int[]{2, 3}) {
                        var additional = RedNailsItem.resolve(player.getServer(), entry.ghosts[nail]);
                        require(additional != null, "live guardian exists for repeated backlash");
                        additional.hurt(additional.damageSources().genericKill(), 1000);
                        require(entry.ready[nail] == RedNailsState.clock(player.getServer()) + 12000,
                                "each dead guardian retains its exact production cooldown");
                    }
                    require(Math.abs(player.getHealth() - 8) < 0.01 && entry.active() == 1,
                            "three actual deaths apply three armor-bypassing backlash hits");
                    pass("backlash", "Three actual guardian deaths; diamond-armored owner HP20->16->8, one survivor, exact 12000-tick per-nail deadlines.");
                } else pass("backlash", "One actual death, HP16 through diamond armor, three survivors, independent 12000-tick deadline.");
                player.getInventory().setItem(0, RedNailsItem.findWorn(player).copy());
                stage = 3; signal("backlash");
            } else if (stage == 3 && captured("backlash")) {
                stage = 4; signal("recall");
            } else if (stage == 4) {
                var entry = RedNailsState.get(player.getServer()).find(itemId);
                if (entry.active() == 0 && ticks > 30) {
                    require(entry.ready[1] == deadline && entry.ready[0] == 0
                                    && (PURSUIT ? entry.ready[2] == deadline && entry.ready[3] == deadline
                                            : entry.ready[2] == 0 && entry.ready[3] == 0),
                            "Network sneak recall does not penalize survivors");
                    pass("recall", "Actual sneak command + V key packets recall all survivors without adding cooldown.");
                    stage = 5; signal("recalled");
                }
            } else if (stage == 5 && captured("recalled")) {
                var level = player.serverLevel();
                for (var pos : BlockPos.betweenClosed(-4, 70, 2, 4, 74, 10)) level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
                swimmer = ModEntities.WATER_GHOST.get().create(level);
                swimmer.moveTo(0.5, 72, 6.5, 180, 0); swimmer.setNoAi(true); level.addFreshEntity(swimmer);
                var dry = ModEntities.WATER_GHOST.get().create(level);
                dry.moveTo(-6, 70, 6.5, 180, 0); dry.setNoAi(true); dry.setYHeadRot(180); dry.yBodyRot = 180; level.addFreshEntity(dry);
                var victim = EntityType.VILLAGER.create(level); victim.setNoAi(true); victim.moveTo(0.5, 72, 5.4, 180, 0); level.addFreshEntity(victim);
                swimmer.setTarget(victim); swimmer.setYHeadRot(180); swimmer.yBodyRot = 180;
                stage = 6; signal("water");
            } else if (stage == 6) {
                if (ticks == 10) swimmer.ghostBody().grab.start();
                if (ticks >= 10 && swimmer.ghostBody().grab.held() != null) swimmer.ghostBody().grab.tick();
                if (captured("water")) {
                    require(swimmer.ghostBody().swimming(), "Real fluid state enables swimming dimensions");
                    Files.writeString(RESULTS.resolve("expected-state"), itemId + "\n" + deadline + "\n");
                    pass("water", "Actual fluid, synchronized swimming/grab state and client-rendered gallery.");
                    stage = 7;
                }
            } else if (stage == 7) {
                ticks = 0;
            } else if (stage == 10 && ticks > 40) {
                if (!equipRing()) return;
                var stack = RedNailsItem.findWorn(player);
                var entry = RedNailsState.get(player.getServer()).read(stack);
                require(entry != null && itemId.equals(entry.item) && entry.owner.equals(player.getUUID()), "Curios item and owner survive real restart");
                require(entry.ready[1] == deadline && entry.active() == 0, "Cooldown deadline and recall survive restart");
                if (PURSUIT) require(entry.ready[0] == 0 && entry.ready[2] == deadline && entry.ready[3] == deadline,
                        "All three exact cooldown deadlines and the recalled nail survive restart");
                pass("restart", "Actual server restart/reconnect retains ItemId, owner, exact deadlines and zero active summons; ready="
                        + java.util.Arrays.toString(entry.ready));
                stage = 11; signal("restored");
            } else if (stage == 11) ticks = 0;
        } catch (Throwable error) { fail(error); }
    }

    private static boolean pursuitTick() throws Exception {
        var level = player.serverLevel();
        if (stage == 3 && captured("backlash")) {
            var entry = RedNailsState.get(player.getServer()).find(itemId);
            shoreGhost = RedNailsItem.resolve(player.getServer(), entry.ghosts[0]);
            require(shoreGhost != null && !shoreGhost.isNoAi() && !shoreGhost.isInWater(), "surviving guardian starts on dry land with normal AI");
            // Create the shore after normal V summons, so water-first spawn selection cannot bypass the bank.
            for (var pos : BlockPos.betweenClosed(-8, 60, 1, -1, 69, 8)) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            for (int x = -7; x <= -2; x++) for (int z = 2; z <= 7; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 60, z), Blocks.STONE.defaultBlockState());
                for (int y = 61; y <= 69; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
            }
            shoreVictim = EntityType.PILLAGER.create(level);
            require(shoreVictim != null && !shoreVictim.canBreatheUnderwater(), "ordinary oxygen-dependent prey fixture");
            shoreVictim.moveTo(-1.8, 70, 0.5, 90, 0);
            shoreVictim.finalizeSpawn(level, level.getCurrentDifficultyAt(shoreVictim.blockPosition()),
                    net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
            require(shoreVictim.getMainHandItem().is(Items.CROSSBOW), "vanilla spawn equips a functioning pillager threat");
            level.addFreshEntity(shoreVictim);
            signal("shore"); stage = 30;
            return true;
        }
        if (stage == 30) {
            require(shoreGhost.isAlive() && shoreVictim.isAlive() && !shoreGhost.isNoAi(), "normal-AI shore participants remain alive");
            var grab = shoreGhost.ghostBody().grab;
            boolean held = grab.held() == shoreVictim;
            if (!shoreGrabbed && held) {
                require(!shoreGhost.ghostBody().swimming(), "first AI grab occurs on dry bank");
                require(player.getLastHurtMob() == shoreVictim || shoreVictim.getTarget() == player,
                        "ordinary owner attack or live enemy threat selected the target");
                shoreGrabbed = true;
                System.out.println("NATIVE_SHORE_GRAB t=" + level.getGameTime() + " ghost=" + shoreGhost.position() + " victim=" + shoreVictim.position());
            }
            if (shoreGrabbed && waterAt < 0 && held && shoreGhost.ghostBody().swimming() && shoreGhost.isInWater() && shoreVictim.isInWater()) {
                waterAt = level.getGameTime(); waterY = shoreGhost.getY(); minWaterY = waterY;
                System.out.println("NATIVE_SHORE_ENTRY t=" + waterAt + " ghost=" + shoreGhost.position() + " victim=" + shoreVictim.position());
            }
            if (waterAt >= 0 && held) {
                minWaterY = Math.min(minWaterY, shoreGhost.getY());
                if (shoreVictim.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) && shoreGhost.getY() < waterY - 0.5 && airTicks < 10) {
                    int air = shoreVictim.getAirSupply();
                    if (previousAir >= 0) { airTicks++; if (previousAir - air >= 3) extraAirTicks++; }
                    previousAir = air;
                }
            }
            if (ticks % 10 == 0) System.out.println("NATIVE_SHORE_TRACE t=" + level.getGameTime() + " held=" + held
                    + " ghost=" + shoreGhost.position() + " victim=" + shoreVictim.position() + " air=" + shoreVictim.getAirSupply()
                    + " selected=" + (shoreGhost.getTarget() == shoreVictim) + " threatening=" + (shoreVictim.getTarget() == player)
                    + " ownerAttacked=" + (player.getLastHurtMob() == shoreVictim) + " victimHealth=" + shoreVictim.getHealth());
            if (shoreGrabbed && !held) {
                long dt = level.getGameTime() - waterAt;
                long cooldown = grab.cooldownUntil() - level.getGameTime();
                double descent = waterY - minWaterY;
                require(waterAt >= 0 && dt >= 99 && dt <= 101, "natural shore water-stage lifetime remains 100 ticks: " + dt);
                require(airTicks == 10 && extraAirTicks == 10, "all ten submerged samples retain extra -3 oxygen: " + extraAirTicks);
                require(descent >= 5.5 && descent <= 6.2, "deep shore descent retains six-block cap: " + descent);
                require(cooldown >= 150 && cooldown <= 160 && WaterGhostGrab.grabbable(shoreVictim), "natural release clears lease and starts 160-tick cooldown");
                pass("shore", "Normal AI + real client V summons; dt=" + dt + " extraDrainTicks=" + extraAirTicks + " descent=" + descent
                        + " cooldown=" + cooldown + " ghost=" + shoreGhost.position() + " victim=" + shoreVictim.position());
                shoreVictim.discard();
                signal("shore-complete"); stage = 31;
            }
            return true;
        }
        if (stage == 31) {
            if (captured("shore-complete")) { signal("recall"); stage = 4; }
            return true;
        }
        if (stage == 5 && captured("recalled")) {
            signal("wild-ready"); stage = 34;
            return true;
        }
        if (stage == 34 && Files.exists(RESULTS.resolve("client-wild-ready.signal"))) {
            swimmer = ModEntities.WATER_GHOST.get().create(level);
            require(swimmer != null && !swimmer.isNoAi(), "wild water ghost retains normal AI");
            swimmer.moveTo(-4.5, 70, 0.5, -90, 0);
            level.addFreshEntity(swimmer);
            wildStartX = swimmer.getX(); wildStartZ = swimmer.getZ(); wildPlayerStart = player.position();
            wildPlayerMoveBeforeGrab = 0; wildGhostMoveBeforeGrab = 0;
            require(!player.isInWater() && !swimmer.isInWater() && swimmer.nearWater(player, 2)
                            && swimmer.distanceToSqr(player) <= 36 && swimmer.hasLineOfSight(player),
                    "wild fixture begins on dry bank, within six blocks and two blocks of water, with line of sight");
            stage = 33;
            return true;
        }
        if (stage == 33) {
            require(swimmer.isAlive() && player.isAlive() && !swimmer.isNoAi(), "wild start retains normal AI");
            require(player.position().subtract(wildPlayerStart).horizontalDistance() < 0.1
                            && !player.isInWater() && swimmer.nearWater(player, 2),
                    "client must hold the legal bank position until pursuit starts");
            require(swimmer.ghostBody().grab.held() == null, "pursuit must start before the moving-player grab");
            double movement = Math.hypot(swimmer.getX() - wildStartX, swimmer.getZ() - wildStartZ);
            if (swimmer.getTarget() == player
                    && swimmer.mayPursue() && !swimmer.getNavigation().isDone() && movement > 0.05) {
                // Observe natural selection and movement before allowing the client to leave near-water range.
                pass("wild-started", "Natural target + active navigation + displacement=" + movement
                        + " t=" + level.getGameTime() + " ghost=" + swimmer.position() + " player=" + player.position()
                        + " nearWater=" + swimmer.nearWater(player, 2) + " distance=" + swimmer.distanceTo(player));
                signal("wild"); stage = 32;
            }
            return true;
        }
        if (stage == 32) {
            require(swimmer.isAlive() && player.isAlive() && !swimmer.isNoAi(), "normal-AI wild pursuit participants remain alive");
            boolean held = swimmer.ghostBody().grab.held() == player;
            if (!wildGrabbed && !held) {
                // Sample before grab physics can contribute to either displacement.
                wildPlayerMoveBeforeGrab = Math.max(wildPlayerMoveBeforeGrab,
                        player.position().subtract(wildPlayerStart).horizontalDistance());
                wildGhostMoveBeforeGrab = Math.max(wildGhostMoveBeforeGrab,
                        Math.hypot(swimmer.getX() - wildStartX, swimmer.getZ() - wildStartZ));
            }
            if (ticks % 10 == 0) System.out.println("NATIVE_WILD_TRACE t=" + level.getGameTime()
                    + " selected=" + (swimmer.getTarget() == player) + " held=" + held
                    + " ghost=" + swimmer.position() + " player=" + player.position()
                    + " nearWater=" + swimmer.nearWater(player, 2));
            if (!wildGrabbed && held) {
                require(swimmer.getTarget() == player && !swimmer.ghostBody().swimming(), "wild ghost naturally pursues and grabs real land player");
                require(wildGhostMoveBeforeGrab > 1 && wildPlayerMoveBeforeGrab >= 1,
                        "both normal AI and real keyboard-driven player moved before grab");
                wildGrabbed = true; wildGrabAt = level.getGameTime();
                System.out.println("NATIVE_WILD_GRAB t=" + wildGrabAt + " ghost=" + swimmer.position() + " player=" + player.position()
                        + " ghostMoveBeforeGrab=" + wildGhostMoveBeforeGrab + " playerMoveBeforeGrab=" + wildPlayerMoveBeforeGrab);
            }
            if (wildGrabbed && !held) {
                long dt = level.getGameTime() - wildGrabAt;
                require(dt >= 59 && dt <= 61, "wild dry grab retains 60-tick duration: " + dt);
                pass("wild", "Normal wild AI pursued a real keyboard-moving player and naturally released; dt=" + dt + " health=" + player.getHealth()
                        + " ghostMoveBeforeGrab=" + wildGhostMoveBeforeGrab + " playerMoveBeforeGrab=" + wildPlayerMoveBeforeGrab);
                swimmer.discard();
                Files.writeString(RESULTS.resolve("expected-state"), itemId + "\n" + deadline + "\n");
                signal("wild-complete"); stage = 7;
            }
            return true;
        }
        return false;
    }
}
