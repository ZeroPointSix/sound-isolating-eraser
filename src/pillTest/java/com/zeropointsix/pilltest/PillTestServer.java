package com.zeropointsix.pilltest;

import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.gametest.GameTestConfigs;
import com.zeropointsix.eraser.pill.PillProvider;
import com.zeropointsix.eraser.pill.PillState;
import com.zeropointsix.eraser.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = "pill_qa")
public final class PillTestServer {
    private static final Path RESULTS = Path.of(System.getProperty("pill.qa.results"));
    private static String phase = "waiting";
    private static int phaseTicks;
    private static boolean sawUse;
    private static boolean finished;
    private static int expectedDoseTicks = 400;
    private static GameTestConfigs.LiveConfig liveConfig;

    private static PillState state(ServerPlayer player) {
        return player.getCapability(PillProvider.CAPABILITY).resolve().orElseThrow();
    }

    private static void require(boolean value, String text) {
        if (!value) throw new AssertionError(text);
    }

    private static String clockEvidence(ServerPlayer player) {
        PillState clock = state(player);
        return "alive=" + player.isAlive() + " health=" + player.getHealth()
                + " activeDuration=" + clock.activeDuration() + " remaining=" + clock.remainingTicks()
                + " withdrawalDuration=" + clock.withdrawalDuration() + " withdrawal=" + clock.withdrawalTicks()
                + " configuredDose=" + CommonConfig.PILL_DURATION.get()
                + " configuredWithdrawal=" + CommonConfig.WITHDRAWAL_DURATION.get();
    }

    private static void requireConfiguredDose(ServerPlayer player) {
        PillState clock = state(player);
        require(clock.activeDuration() == expectedDoseTicks && clock.remainingTicks() > 0
                        && clock.remainingTicks() <= expectedDoseTicks && clock.withdrawalDuration() == 2000,
                "native swallow uses the isolated fixture durations: " + clockEvidence(player));
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        if (liveConfig != null) {
            liveConfig.close();
            liveConfig = null;
        }
    }

    private static void phase(String next) {
        phase = next;
        phaseTicks = 0;
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal("PILL_QA:" + next));
        }
        ServerPlayer user = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerByName("PillUser");
        System.out.println("PILL_QA_PHASE=" + next + " " + clockEvidence(user));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isDedicatedServer()) return;
        ServerPlayer a = server.getPlayerList().getPlayerByName("PillUser");
        ServerPlayer b = server.getPlayerList().getPlayerByName("PillObserver");
        if (a == null || b == null) return;
        try {
            phaseTicks++;
            require(phaseTicks < 2400, "phase timed out: " + phase + " " + clockEvidence(a));
            require(a.isAlive() && b.isAlive(), "native fixture players remain alive: user " + clockEvidence(a)
                    + "; observer " + clockEvidence(b));
            if (phase.equals("waiting")) {
                // Live ConfigValue.set() autosaves and can race Forge's asynchronous file reload.
                liveConfig = GameTestConfigs.sandbox(CommonConfig.SPEC);
                CommonConfig.PILL_DURATION.set(expectedDoseTicks);
                CommonConfig.WITHDRAWAL_DURATION.set(2000);
                if (Boolean.getBoolean("pill.qa.restart")) {
                    require(state(a).active() && state(a).remainingTicks() <= 4000, "real server restart retains active capability");
                    require(state(a).activeDuration() == 4000 && state(a).withdrawalDuration() == 2000,
                            "real restart retains the consumed durations: " + clockEvidence(a));
                    require(a.getInventory().getItem(0).is(ModItems.EMPTY_PILL_PACK.get()), "empty pack persists through restart");
                    require(!state(b).active() && !state(b).withdrawing(), "observer remains clean after restart");
                    phase("restarted");
                    Files.writeString(RESULTS.resolve("server-restart.pass"), "Real save, server shutdown and restart retained active state and inventory.\n");
                    finished = true;
                    return;
                }
                var level = server.overworld();
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState());
                }
                for (ServerPlayer player : new ServerPlayer[]{a, b}) {
                    player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent();
                    player.teleportTo(level, player == a ? 0.5 : 2.5, 65, 0.5, 0, -10);
                    player.setHealth(20);
                    player.getFoodData().setFoodLevel(20);
                }
                ItemStack pack = new ItemStack(ModItems.ENHANCEMENT_PILL_PACK.get());
                pack.setDamageValue(10);
                a.getInventory().setItem(0, pack);
                a.getInventory().selected = 0;
                a.inventoryMenu.broadcastChanges();
                phase("cancel");
                return;
            }
            require(CommonConfig.PILL_DURATION.get() == expectedDoseTicks && CommonConfig.WITHDRAWAL_DURATION.get() == 2000,
                    "fixture durations remain isolated from file reload: " + clockEvidence(a));
            require(!state(b).active() && !state(b).withdrawing(), "A's dose never changes B's authoritative state");
            if (phase.equals("active") && phaseTicks >= 400) {
                require(state(a).withdrawing(), "configured active clock reaches withdrawal within 400 ticks: " + clockEvidence(a));
            }
            if (phase.equals("active") && phaseTicks == 80) {
                float health = a.getHealth();
                require(a.hurt(a.damageSources().generic(), 5) && a.getHealth() < health,
                        "active pill still permits normal damage on a real player");
            }
            if (phase.equals("cancel")) {
                sawUse |= a.isUsingItem();
                if (sawUse && !a.isUsingItem()) {
                    require(!state(a).active() && a.getInventory().getItem(0).getDamageValue() == 10, "native early release does not consume");
                    phase("eat");
                }
            } else if (phase.equals("eat") && state(a).active()) {
                requireConfiguredDose(a);
                require(a.getInventory().getItem(0).getDamageValue() == 11, "native full swallow consumes exactly one");
                require(!a.addEffect(new MobEffectInstance(MobEffects.POISON, 200)), "A rejects poison during active phase");
                require(b.addEffect(new MobEffectInstance(MobEffects.POISON, 60)), "B is not immune");
                phase("active");
            } else if (phase.equals("active") && state(a).withdrawing()) {
                require(!a.hasEffect(MobEffects.DAMAGE_BOOST) && a.hasEffect(MobEffects.DARKNESS)
                        && a.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "same-tick withdrawal replaces boosts");
                a.getInventory().setItem(1, new ItemStack(Items.MILK_BUCKET));
                a.inventoryMenu.broadcastChanges();
                phase("milk");
            } else if (phase.equals("milk") && a.getInventory().getItem(1).is(Items.BUCKET)) {
                require(state(a).withdrawing(), "native milk leaves withdrawal clock running");
                phase("milk_wait");
            } else if (phase.equals("milk_wait") && phaseTicks >= 42) {
                require(state(a).withdrawing() && a.hasEffect(MobEffects.DARKNESS), "withdrawal returns within two seconds after milk");
                expectedDoseTicks = 4000;
                CommonConfig.PILL_DURATION.set(expectedDoseTicks);
                phase("redose");
            } else if (phase.equals("redose") && state(a).active()) {
                requireConfiguredDose(a);
                require(!state(a).withdrawing() && !a.hasEffect(MobEffects.DARKNESS), "redose clears withdrawal");
                require(a.getInventory().getItem(0).is(ModItems.EMPTY_PILL_PACK.get()), "last native dose replaces pack with empty item");
                server.getPlayerList().saveAll();
                Files.writeString(RESULTS.resolve("server-cycle.pass"), "Native cancel/eat, immunity, cliff, milk, redose, empty pack, multiplayer isolation passed.\n");
                phase("saved");
                finished = true;
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve("server.failed"), failure.toString()); } catch (Exception ignored) { }
            finished = true;
        }
    }
}
