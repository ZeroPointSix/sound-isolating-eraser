package com.zeropointsix.shaxiatest;

import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.shaxia.ShaxiaStacks;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = "shaxia_qa")
public final class ShaxiaTestServer {
    private static final Path RESULTS = Path.of(System.getProperty("shaxia.qa.results"));
    private static final String[] STAGES = {"hostile", "ordinary", "calm", "angry", "armored"};
    private static final float[] EXPECTED = {8.5F, 4, 4, 8.5F, 4.145F};
    private static String phase = "waiting";
    private static int stage, ticks, attacks;
    private static Mob target;
    private static float initialHealth;
    private static boolean finished;

    private static void require(boolean value, String text) {
        if (!value) throw new AssertionError(text);
    }

    private static void phase(String next) {
        phase = next;
        ticks = 0;
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal("SHAXIA_QA:" + next));
        }
        System.out.println("SHAXIA_QA_PHASE=" + next);
    }

    private static void prepare(ServerPlayer user, ServerPlayer observer) {
        if (target != null) target.discard();
        var level = user.serverLevel();
        target = switch (stage) {
            case 1 -> EntityType.COW.create(level);
            case 2, 3 -> EntityType.ENDERMAN.create(level);
            default -> EntityType.CREEPER.create(level);
        };
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.moveTo(0.5, 65, 2.5, 180, 0);
        if (stage == 3) target.setTarget(observer);
        if (stage == 4) target.getAttribute(Attributes.ARMOR).setBaseValue(20);
        initialHealth = target.getHealth();
        level.addFreshEntity(target);
        user.teleportTo(level, 0.5, 65, 0.5, 0, 18);
        attacks = 0;
        phase(STAGES[stage]);
    }

    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide && event.getTarget() == target
                && event.getEntity().getName().getString().equals("ShaxiaUser")) attacks++;
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isDedicatedServer()) return;
        ServerPlayer user = server.getPlayerList().getPlayerByName("ShaxiaUser");
        ServerPlayer observer = server.getPlayerList().getPlayerByName("ShaxiaObserver");
        if (user == null || observer == null) return;
        try {
            ticks++;
            require(ticks < 2400, "native input phase timed out: " + phase);
            if (phase.equals("waiting")) {
                if (Boolean.getBoolean("shaxia.qa.restart")) {
                    require(ShaxiaStacks.active(user.getMainHandItem()) && user.getMainHandItem().getDamageValue() == 5,
                            "real server restart must preserve damage and innate enchantment");
                    require(ShaxiaStacks.active(observer.getMainHandItem()) && observer.getMainHandItem().getDamageValue() == 0,
                            "observer inventory remains independent after restart");
                    phase("restarted");
                    Files.writeString(RESULTS.resolve("server-restart.pass"), "Real save and server restart preserved both inventories.\n");
                    finished = true;
                    return;
                }
                var level = server.overworld();
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                for (int x = -5; x <= 5; x++) for (int z = -5; z <= 7; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState());
                }
                for (ServerPlayer player : new ServerPlayer[]{user, observer}) {
                    player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent();
                    player.teleportTo(level, player == user ? 0.5 : 3.5, 65, 0.5, 0, 18);
                    player.setHealth(20);
                    player.getFoodData().setFoodLevel(20);
                    player.getInventory().setItem(0, ModItems.SHAXIADAO.get().getDefaultInstance());
                    player.getInventory().selected = 0;
                    player.inventoryMenu.broadcastChanges();
                }
                prepare(user, observer);
                return;
            }
            if (attacks == 0) return;
            require(attacks == 1, "one native click must produce one authoritative attack");
            float damage = initialHealth - target.getHealth();
            require(Math.abs(damage - EXPECTED[stage]) < 0.01F,
                    phase + " native damage expected " + EXPECTED[stage] + ", got " + damage);
            require(user.getMainHandItem().getDamageValue() == stage + 1, "one durability per native strike");
            require(observer.getMainHandItem().getDamageValue() == 0, "observer durability cannot change");
            Files.writeString(RESULTS.resolve(phase + ".pass"), "Native click, server damage=" + damage + ", durability=" + (stage + 1) + "\n");
            stage++;
            if (stage < STAGES.length) {
                prepare(user, observer);
            } else {
                target.discard();
                server.getPlayerList().saveAll();
                Files.writeString(RESULTS.resolve("server-cycle.pass"), "Five native attacks and multiplayer inventory isolation passed.\n");
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
