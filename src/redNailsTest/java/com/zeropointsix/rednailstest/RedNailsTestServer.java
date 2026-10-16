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
    private static final Path RESULTS = Path.of(System.getProperty("rednails.qa.results"));
    private static ServerPlayer player;
    private static int stage, ticks;
    private static boolean failed;
    private static UUID itemId;
    private static long deadline;
    private static WaterGhostEntity swimmer;
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void pass(String name, String evidence) throws Exception {
        Files.writeString(RESULTS.resolve("server-" + name + ".pass"), evidence + "\n");
        System.out.println("RED_NAILS_QA_PASS=" + name + " " + evidence);
    }
    private static void signal(String phase) {
        ticks = 0; player.sendSystemMessage(Component.literal("RED_NAILS_QA:" + phase));
    }
    private static boolean captured(String phase) { return Files.exists(RESULTS.resolve("client-" + phase + ".pass")); }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try { Files.writeString(RESULTS.resolve("server.failed"), error.toString()); } catch (Exception ignored) { }
    }
    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        var level = event.getServer().overworld();
        level.setDayTime(6000); level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, event.getServer());
        for (var pos : BlockPos.betweenClosed(-16, 69, -20, 16, 69, 20)) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        if (Files.exists(RESULTS.resolve("expected-state"))) {
            try {
                var saved = Files.readAllLines(RESULTS.resolve("expected-state"));
                itemId = UUID.fromString(saved.get(0)); deadline = Long.parseLong(saved.get(1)); stage = 10;
            } catch (Throwable error) { fail(error); }
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getGameProfile().getName().equals("NailsTester")) return;
        player = p; ticks = 0;
        p.setGameMode(GameType.SURVIVAL);
        p.teleportTo(p.serverLevel(), 0.5, 70, -10.5, 0, 0);
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
            if (++ticks > 1800) throw new AssertionError("QA stage timeout: " + stage);
            if (stage == 0) {
                if (ticks >= 30 && !equipRing()) return;
                if (ticks >= 60 && Files.exists(RESULTS.resolve("client-ready.signal"))) {
                    stage = 1; signal("input");
                }
            } else if (stage == 1) {
                var stack = RedNailsItem.findWorn(player);
                var entry = RedNailsState.get(player.getServer()).read(stack);
                if (ticks == 20) require(entry == null, "Short real-client key hold must not summon");
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
                pass("backlash", "One actual death, HP16 through diamond armor, three survivors, independent 12000-tick deadline.");
                player.getInventory().setItem(0, RedNailsItem.findWorn(player).copy());
                stage = 3; signal("backlash");
            } else if (stage == 3 && captured("backlash")) {
                stage = 4; signal("recall");
            } else if (stage == 4) {
                var entry = RedNailsState.get(player.getServer()).find(itemId);
                if (entry.active() == 0 && ticks > 30) {
                    require(entry.ready[1] == deadline && entry.ready[0] == 0 && entry.ready[2] == 0 && entry.ready[3] == 0,
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
                pass("restart", "Actual server restart/reconnect retains ItemId, owner, exact deadline and zero active summons.");
                stage = 11; signal("restored");
            } else if (stage == 11) ticks = 0;
        } catch (Throwable error) { fail(error); }
    }
}
