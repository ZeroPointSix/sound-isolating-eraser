package com.zeropointsix.suxiatest;

import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.suxia.SuxiaEntity;
import com.zeropointsix.eraser.suxia.SuxiaMotion;
import com.zeropointsix.eraser.suxia.SuxiaSummons;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "suxia_qa")
public final class SuxiaTestServer {
    private static final Path RESULTS = Path.of(System.getProperty("suxia.qa.results"));
    private static ServerPlayer player;
    private static int stage, ticks;
    private static UUID summoned;
    private static boolean failed;

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void signal(String phase) { player.sendSystemMessage(Component.literal("SUXIA_QA:" + phase)); }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        try { Files.writeString(RESULTS.resolve("server.failed"), error.toString()); } catch (Exception ignored) { }
    }

    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        level.setDayTime(6000); level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        for (BlockPos pos : BlockPos.betweenClosed(-16, 64, -20, 16, 64, 16)) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
    }

    private static SuxiaEntity display(ServerLevel level, int x) {
        var entity = ModEntities.SUXIA.get().create(level);
        entity.setNoAi(true); entity.setPersistenceRequired();
        entity.moveTo(x, 65, 0, 180, 0); entity.setYHeadRot(180); entity.yBodyRot = 180;
        level.addFreshEntity(entity);
        return entity;
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getGameProfile().getName().equals("SuxiaTester")) return;
        player = p; ticks = 0;
        try {
            if (stage == 4) {
                var watch = p.getMainHandItem();
                require(watch.is(ModItems.SUXIA_POCKET_WATCH.get()) && watch.getTag().getLong("CooldownUntil") > SuxiaMotion.clock(p.level()), "saved watch cooldown survives actual reconnect");
                stage = 5;
                return;
            }
            require(stage == 0, "unexpected login");
            p.setGameMode(GameType.CREATIVE);
            p.teleportTo(p.serverLevel(), 0, 65, -12, 0, 5);
            p.getInventory().setItem(0, new ItemStack(ModItems.SUXIA_POCKET_WATCH.get()));
            p.getInventory().setItem(1, new ItemStack(ModItems.SUXIA_SPAWN_EGG.get()));
            display(p.serverLevel(), -3).setAggressive(true);
            display(p.serverLevel(), 0).setCrawling(true);
            display(p.serverLevel(), 3).setPlayingDead(true);
        } catch (Throwable error) { fail(error); }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (stage == 3 && event.getEntity() == player) { stage = 4; player = null; }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || player == null) return;
        try {
            ticks++;
            var ledger = SuxiaSummons.get(player.getServer());
            var active = ledger.active(player.getUUID());
            if (stage == 0 && ticks == 60) { signal("gallery"); stage = 1; }
            else if (stage == 1 && active != null) {
                summoned = active.summon;
                require(active.until - SuxiaMotion.clock(player.level()) > 1100, "real use packet starts sixty-second summon");
                signal("summoned"); stage = 2;
            } else if (stage == 2 && active == null) {
                require(player.serverLevel().getEntity(summoned) == null, "real recall removes entity");
                var watch = player.getMainHandItem();
                require(watch.getTag().getInt("CooldownRemaining") > 3500 && !watch.getTag().getBoolean("Open"), "real recall closes watch and starts cooldown");
                Files.writeString(RESULTS.resolve("server-lifecycle.pass"), "Actual client summon/recall packets, entity lifecycle and cooldown verified.\n");
                signal("recalled"); stage = 3;
            } else if (stage == 5 && ticks == 60) {
                require(player.getCooldowns().isOnCooldown(ModItems.SUXIA_POCKET_WATCH.get()), "login restores native cooldown");
                Files.writeString(RESULTS.resolve("server-rejoin.pass"), "Actual reconnect retains watch binding and cooldown.\n");
                signal("rejoined"); stage = 6;
            }
        } catch (Throwable error) { fail(error); }
    }
}
