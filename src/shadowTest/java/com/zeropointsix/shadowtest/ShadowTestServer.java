package com.zeropointsix.shadowtest;

import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.shadow.ShadowTanglerEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = "shadow_qa")
public final class ShadowTestServer {
    private static final Path RESULTS = Path.of(System.getProperty("shadow.qa.results"));
    private static final BlockPos LIGHT = new BlockPos(0, 65, 3);
    private static String phase = "waiting";
    private static int ticks, attacks;
    private static ShadowTanglerEntity mob;
    private static boolean finished;

    private static void require(boolean value, String text) {
        if (!value) throw new AssertionError(text);
    }

    private static void phase(String next) {
        phase = next;
        ticks = 0;
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal("SHADOW_QA:" + next));
        }
        System.out.println("SHADOW_QA_PHASE=" + next);
    }

    private static void pass(String name, String message) throws Exception {
        Files.writeString(RESULTS.resolve(name + ".pass"), message + "\n");
    }

    private static void light(int level) {
        mob.level().setBlockAndUpdate(LIGHT, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level));
    }

    private static boolean capturedBoth(String name) {
        return Files.exists(RESULTS.resolve("user-" + name + ".pass"))
                && Files.exists(RESULTS.resolve("observer-" + name + ".pass"));
    }

    private static void placeCamera(ServerPlayer user, ServerPlayer observer) {
        user.teleportTo(user.serverLevel(), 0.5, 65, 0.5, 0, 8);
        observer.teleportTo(user.serverLevel(), 3.5, 65, 0.5, 45, 8);
    }

    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide && event.getTarget() == mob
                && event.getEntity().getName().getString().equals("ShadowUser")) attacks++;
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isDedicatedServer()) return;
        var user = server.getPlayerList().getPlayerByName("ShadowUser");
        var observer = server.getPlayerList().getPlayerByName("ShadowObserver");
        if (user == null || observer == null) return;
        try {
            require(++ticks < 2400, "phase timed out: " + phase);
            var level = server.overworld();
            if (phase.equals("waiting")) {
                if (Boolean.getBoolean("shadow.qa.restart")) {
                    level.getChunkAt(LIGHT);
                    var loaded = level.getEntitiesOfClass(ShadowTanglerEntity.class, new AABB(-8, 60, -8, 8, 74, 10)).stream()
                            .filter(entity -> entity.getTags().contains("shadow-qa-persist")).findFirst();
                    if (loaded.isEmpty()) return;
                    mob = loaded.get();
                    require(mob.getSpawnAge() == 24 && !mob.isSpawning(), "reload must not replay spawn");
                    require(Math.abs(mob.getHealth() - 15.75F) < 0.01F, "saved mob health preserved");
                    require(mob.getStringUUID().equals(Files.readString(RESULTS.resolve("saved-entity.uuid")).trim()),
                            "the same entity UUID survived restart");
                    placeCamera(user, observer);
                    phase("restarted");
                    pass("server-restart", "Real server restart preserved entity age, health and identity.");
                    finished = true;
                    return;
                }
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.setDayTime(6000);
                for (int x = -8; x <= 8; x++) for (int z = -8; z <= 10; z++) {
                    for (int y = 64; y <= 71; y++) {
                        boolean wall = y == 64 || y == 71 || x == -8 || x == 8 || z == -8 || z == 10;
                        level.setBlockAndUpdate(new BlockPos(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
                for (var player : new ServerPlayer[]{user, observer}) {
                    player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent();
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 24000, 0, false, false));
                    player.getInventory().setItem(0, ModItems.SHADOW_TANGLER_SPAWN_EGG.get().getDefaultInstance());
                    player.getInventory().setItem(1, ModItems.SHAXIADAO.get().getDefaultInstance());
                    player.getInventory().selected = 0;
                    player.inventoryMenu.broadcastChanges();
                }
                user.teleportTo(level, 0.5, 65, 0.5, 0, 30);
                observer.teleportTo(level, 3.5, 65, 0.5, 45, 8);
                phase("place");
                return;
            }
            if (phase.equals("place")) {
                var entities = level.getEntitiesOfClass(ShadowTanglerEntity.class, new AABB(-8, 60, -8, 8, 74, 10));
                if (entities.isEmpty()) return;
                require(entities.size() == 1, "one native egg use produces one entity");
                mob = entities.get(0);
                require(mob.isSpawning(), "egg uses real spawn lock");
                mob.setNoAi(true);
                mob.setPersistenceRequired();
                mob.moveTo(0.5, 65, 3.5, 180, 0);
                placeCamera(user, observer);
                pass("native-egg", "Native right click spawned one registered Shadow Tangler.");
                phase("spawn");
            } else if (phase.equals("spawn") && ticks >= 65) {
                require(!mob.isSpawning() && mob.getLightTier() == 0, "spawn finishes in dark");
                phase("dark");
            } else if (phase.equals("dark") && attacks > 0 && capturedBoth("dark")) {
                require(attacks == 1 && Math.abs(mob.getHealth() - 15.75F) < 0.01F, "native dark knife damage is 4.25");
                require(user.getMainHandItem().getDamageValue() == 1, "one native strike uses one durability");
                pass("native-knife", "Native left click: dark damage 4.25, durability 1.");
                mob.setDeltaMovement(0, 0, 0);
                mob.moveTo(0.5, 65, 3.5, 180, 0);
                light(8);
                phase("dim");
            } else if (phase.equals("dim") && ticks >= 85 && capturedBoth("dim")) {
                require(mob.getLightTier() == 1 && mob.isRetreating(), "dim state and retreat are authoritative");
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
                mob.setHealth(200);
                light(15);
                phase("bright");
            } else if (phase.equals("bright") && ticks >= 85 && capturedBoth("bright")) {
                require(mob.getLightTier() == 2 && mob.getHealth() < 200, "bright light burns and synchronizes");
                mob.hurt(mob.damageSources().genericKill(), 1000);
                require(mob.isRemoved(), "bright death removes immediately");
                phase("shattered");
            } else if (phase.equals("shattered") && ticks >= 45) {
                level.setBlockAndUpdate(LIGHT, Blocks.AIR.defaultBlockState());
                mob = ModEntities.SHADOW_TANGLER.get().create(level);
                mob.setNoAi(true);
                mob.setPersistenceRequired();
                mob.moveTo(0.5, 65, 3.5, 180, 0);
                level.addFreshEntity(mob);
                phase("respawn");
            } else if (phase.equals("respawn") && ticks >= 65) {
                mob.setHealth(15.75F);
                mob.addTag("shadow-qa-persist");
                Files.writeString(RESULTS.resolve("saved-entity.uuid"), mob.getStringUUID());
                server.saveEverything(false, true, true);
                pass("server-cycle", "Egg, native attack, light states, bright death and save passed.");
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
