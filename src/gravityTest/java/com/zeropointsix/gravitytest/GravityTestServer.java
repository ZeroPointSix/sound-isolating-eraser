package com.zeropointsix.gravitytest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.zeropointsix.eraser.gravity.GravityEquipment;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import com.zeropointsix.eraser.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;

@Mod.EventBusSubscriber(modid = "gravity_qa")
public final class GravityTestServer {
    public static final String WEARER = "GravityWearer";
    public static final String OBSERVER = "GravityObserver";
    public static final String MOVING_NAME = "gravityqa_moving";
    public static final String STILL_NAME = "gravityqa_still";
    public static final String PHASE_PREFIX = "GRAVITY_QA_PHASE:";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RUN_TICKS = 12000;
    private static MinecraftServer activeServer;
    private static Cow movingCow;
    private static Cow stillCow;
    private static boolean ready;
    private static boolean moving = true;
    private static long motionTicks;
    private static int startedAt;
    private static int stopAt = -1;
    private static String phase = "waiting";
    private static final List<Entity> senseFixtures = new ArrayList<>();
    private static final List<ItemEntity> stressItems = new ArrayList<>();
    private static GravityFieldEntity testField;
    private static int fieldStartedAt;
    private static Cow effectCow;
    private static Zombie effectZombie;
    private static float cowHealth;
    private static float zombieHealth;
    private static float playerHealth;

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gravityqa")
                .requires(GravityTestServer::allowed)
                .then(Commands.literal("phase")
                        .then(Commands.argument("word", StringArgumentType.word())
                                .executes(context -> changePhase(context.getSource(),
                                        StringArgumentType.getString(context, "word")))))
                .then(Commands.literal("finish")
                        .executes(context -> finish(context.getSource().getServer()))));
    }

    private static boolean allowed(CommandSourceStack source) {
        if (!source.getServer().isDedicatedServer()) return false;
        if (source.hasPermission(2)) return true;
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        String name = player.getGameProfile().getName();
        return WEARER.equals(name) || OBSERVER.equals(name);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isDedicatedServer()) return;
        if (server != activeServer) {
            activeServer = server;
            ready = false;
            moving = true;
            movingCow = null;
            stillCow = null;
            motionTicks = 0;
            startedAt = server.getTickCount();
            stopAt = -1;
            phase = "waiting";
            senseFixtures.clear();
            stressItems.clear();
            testField = null;
            LOGGER.info("GRAVITY_QA_SERVER_WAITING");
        }
        if (stopAt >= 0 && server.getTickCount() >= stopAt) {
            server.halt(false);
            return;
        }
        if (stopAt < 0 && server.getTickCount() - startedAt >= MAX_RUN_TICKS) {
            LOGGER.error("GRAVITY_QA_SERVER_TIMEOUT phase={} ready={}", phase, ready);
            server.halt(false);
            return;
        }
        if (!ready) {
            ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
            ServerPlayer observer = server.getPlayerList().getPlayerByName(OBSERVER);
            if (wearer == null || observer == null || !initialize(server, wearer, observer)) return;
            ready = true;
            announce(server, "ready");
        }
        if (movingCow != null && movingCow.isAlive() && !movingCow.isRemoved()) {
            long sample = moving ? motionTicks++ : Math.max(0, motionTicks - 1);
            movingCow.setDeltaMovement(Vec3.ZERO);
            movingCow.setPos(0.5 + Math.sin(sample * 0.08) * 1.2, 65, 8.5);
        }
        if (stillCow != null && stillCow.isAlive() && !stillCow.isRemoved()) {
            stillCow.setDeltaMovement(Vec3.ZERO);
            stillCow.setPos(2.5, 65, 8.5);
        }
        for (int i = 0; i < senseFixtures.size(); i++) {
            Entity entity = senseFixtures.get(i);
            if (!entity.isAlive()) continue;
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setPos(-2 + i + (i == 4 ? 0 : Math.sin(motionTicks * 0.08) * 0.8),
                    i == 0 || i == 3 ? 65 : 66, 9.5);
        }
        for (int i = 0; i < stressItems.size(); i++) {
            ItemEntity entity = stressItems.get(i);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setPos(-6 + (i % 20) * 0.6 + (motionTicks % 20) * 0.05,
                    66.5, 6 + (i / 20) * 0.5);
        }
        try {
            verifyFieldEffects(server);
        } catch (Throwable failure) {
            LOGGER.error("GRAVITY_QA_EFFECT_FAILURE", failure);
            try {
                Files.writeString(Path.of(System.getProperty("gravity.qa.results"), "server.failed"), failure.toString());
            } catch (Exception writeFailure) {
                LOGGER.error("Cannot record test failure", writeFailure);
            }
            stopAt = server.getTickCount() + 20;
        }
    }

    private static boolean initialize(MinecraftServer server, ServerPlayer wearer, ServerPlayer observer) {
        var wearerInventory = CuriosApi.getCuriosInventory(wearer).resolve().orElse(null);
        var observerInventory = CuriosApi.getCuriosInventory(observer).resolve().orElse(null);
        if (wearerInventory == null || observerInventory == null) return false;
        var necklace = wearerInventory.getStacksHandler("necklace").orElse(null);
        if (necklace == null || necklace.getStacks().getSlots() == 0) return false;

        ServerLevel level = server.overworld();
        level.setDayTime(18000);
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        for (int x = -5; x <= 8; x++) {
            for (int z = -2; z <= 12; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState());
                for (int y = 65; y <= 70; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int y = 65; y <= 68; y++) {
                level.setBlockAndUpdate(new BlockPos(x, y, 4), Blocks.STONE.defaultBlockState());
            }
        }
        level.getEntitiesOfClass(Entity.class, new AABB(-16, 60, -16, 16, 80, 20), entity ->
                !(entity instanceof ServerPlayer) && entity.hasCustomName()
                        && entity.getName().getString().startsWith("gravityqa_"))
                .forEach(Entity::discard);
        movingCow = spawnCow(level, MOVING_NAME, 0.5);
        stillCow = spawnCow(level, STILL_NAME, 2.5);
        addSenseFixture(level, EntityType.ZOMBIE.create(level), "zombie");
        addSenseFixture(level, new ItemEntity(level, 0, 66, 9.5, new ItemStack(Items.DIAMOND)), "item");
        addSenseFixture(level, EntityType.ARROW.create(level), "arrow");
        addSenseFixture(level, EntityType.MINECART.create(level), "minecart");
        addSenseFixture(level, new ItemEntity(level, 2, 66, 9.5, new ItemStack(Items.EMERALD)), "still_item");

        preparePlayer(wearer, level, 0.5, 0);
        preparePlayer(observer, level, 5.5, 30);
        wearerInventory.getCurios().values().forEach(handler -> {
            for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                handler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
            }
            for (int i = 0; i < handler.getCosmeticStacks().getSlots(); i++) {
                handler.getCosmeticStacks().setStackInSlot(i, ItemStack.EMPTY);
            }
        });
        observerInventory.getCurios().values().forEach(handler -> {
            for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                handler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
            }
            for (int i = 0; i < handler.getCosmeticStacks().getSlots(); i++) {
                handler.getCosmeticStacks().setStackInSlot(i, ItemStack.EMPTY);
            }
        });
        necklace.getStacks().setStackInSlot(0, new ItemStack(ModItems.GRAVITY_JADE_PENDANT.get()));
        if (!GravityEquipment.isEquipped(wearer) || GravityEquipment.isEquipped(observer)) {
            throw new IllegalStateException("GRAVITY_QA_EQUIPMENT_SETUP_FAILED");
        }
        LOGGER.info("GRAVITY_QA_FIXTURE_READY moving={} still={} wearer={} observer={}",
                movingCow.getId(), stillCow.getId(), wearer.getId(), observer.getId());
        return true;
    }

    private static void preparePlayer(ServerPlayer player, ServerLevel level, double x, float yaw) {
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        player.getPersistentData().remove("sound_isolating_eraser:gravity_cooldown");
        player.getCooldowns().removeCooldown(ModItems.GRAVITY_JADE_PENDANT.get());
        player.teleportTo(level, x, 65, 0.5, yaw, 0);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static Cow spawnCow(ServerLevel level, String name, double x) {
        Cow cow = EntityType.COW.create(level);
        if (cow == null) throw new IllegalStateException("GRAVITY_QA_COW_CREATION_FAILED");
        cow.setNoAi(true);
        cow.setNoGravity(true);
        // The prescribed paths approach within one cow width; disable collision pushes.
        cow.noPhysics = true;
        cow.setPersistenceRequired();
        cow.setSilent(true);
        cow.setCustomName(Component.literal(name));
        cow.setCustomNameVisible(false);
        cow.addTag(name);
        cow.setPos(x, 65, 8.5);
        if (!level.addFreshEntity(cow)) throw new IllegalStateException("GRAVITY_QA_COW_SPAWN_FAILED");
        return cow;
    }

    private static int changePhase(CommandSourceStack source, String next) {
        MinecraftServer server = source.getServer();
        ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
        if (!ready || wearer == null) {
            source.sendFailure(Component.literal("GRAVITY_QA_NOT_READY"));
            return 0;
        }
        switch (next) {
            case "stress" -> {
                if (stressItems.isEmpty()) {
                    for (int i = 0; i < 200; i++) {
                        ItemEntity item = new ItemEntity(server.overworld(), -6 + (i % 20) * 0.6,
                                66.5, 6 + (i / 20) * 0.5, new ItemStack(Items.DIAMOND));
                        item.setNoGravity(true);
                        item.noPhysics = true;
                        item.setUnlimitedLifetime();
                        item.setPickUpDelay(32767);
                        item.setCustomName(Component.literal("gravityqa_stress_" + i));
                        item.getItem().getOrCreateTag().putInt("GravityQA", i);
                        item.addTag("gravityqa_stress");
                        server.overworld().addFreshEntity(item);
                        stressItems.add(item);
                    }
                }
            }
            case "stop" -> {
                moving = false;
                if (movingCow != null) movingCow.setDeltaMovement(Vec3.ZERO);
            }
            case "start" -> moving = true;
            case "unequip", "equip" -> {
                var necklace = CuriosApi.getCuriosInventory(wearer).resolve().orElseThrow()
                        .getStacksHandler("necklace").orElseThrow().getStacks();
                necklace.setStackInSlot(0, "equip".equals(next)
                        ? new ItemStack(ModItems.GRAVITY_JADE_PENDANT.get()) : ItemStack.EMPTY);
            }
            default -> { }
        }
        announce(server, next);
        return 1;
    }

    private static void announce(MinecraftServer server, String next) {
        phase = next;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getPersistentData().putString("gravityqa_phase", next);
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal(PHASE_PREFIX + next), false);
        LOGGER.info("{}{}", PHASE_PREFIX, next);
    }

    private static void addSenseFixture(ServerLevel level, Entity entity, String kind) {
        if (entity == null) throw new IllegalStateException("Missing fixture " + kind);
        entity.setNoGravity(true);
        entity.noPhysics = true;
        entity.setSilent(true);
        entity.setCustomName(Component.literal("gravityqa_" + kind));
        entity.setCustomNameVisible(false);
        if (entity instanceof Zombie zombie) zombie.setNoAi(true);
        if (entity instanceof ItemEntity item) {
            item.setUnlimitedLifetime();
            item.setPickUpDelay(32767);
        }
        int index = senseFixtures.size();
        entity.setPos(-2 + index, index == 0 || index == 3 ? 65 : 66, 9.5);
        level.addFreshEntity(entity);
        senseFixtures.add(entity);
    }

    private static void verifyFieldEffects(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        if (testField == null) {
            var fields = level.getEntitiesOfClass(GravityFieldEntity.class, new AABB(-16, 60, -16, 16, 80, 20));
            if (fields.isEmpty()) return;
            testField = fields.get(0);
            fieldStartedAt = server.getTickCount();
            AABB box = testField.fieldBounds();
            server.getPlayerList().broadcastSystemMessage(Component.literal("GRAVITY_QA_BOUNDS:"
                    + box.minX + "," + box.minY + "," + box.minZ + "," + box.maxX + "," + box.maxY + "," + box.maxZ), false);
        }
        int elapsed = server.getTickCount() - fieldStartedAt;
        ServerPlayer wearer = server.getPlayerList().getPlayerByName(WEARER);
        ServerPlayer observer = server.getPlayerList().getPlayerByName(OBSERVER);
        if (wearer == null || observer == null) return;
        Vec3 center = testField.fieldBounds().getCenter();
        if (elapsed == 55) {
            wearer.setGameMode(GameType.SURVIVAL);
            wearer.setHealth(20);
            wearer.teleportTo(level, center.x, 65, center.z, 0, 0);
            observer.setHealth(20);
            observer.teleportTo(level, center.x + 1, 65, center.z, 0, 0);
            effectCow = EntityType.COW.create(level);
            effectCow.setNoAi(true);
            effectCow.setNoGravity(true);
            effectCow.setPos(center.x - 1, 65, center.z);
            level.addFreshEntity(effectCow);
            effectZombie = EntityType.ZOMBIE.create(level);
            effectZombie.setNoAi(true);
            effectZombie.setNoGravity(true);
            effectZombie.setPos(center.x, 65, center.z + 1);
            level.addFreshEntity(effectZombie);
            cowHealth = effectCow.getHealth();
            zombieHealth = effectZombie.getHealth();
            playerHealth = wearer.getHealth();
        }
        if (elapsed == 96) {
            for (LivingEntity entity : List.of(effectCow, effectZombie, wearer, observer)) {
                check(entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                        && entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 2,
                        "Slowness III for " + entity.getType());
            }
            check(effectCow.getHealth() == cowHealth - 2, "Cow receives two 1-damage pulses");
            check(effectZombie.getHealth() < zombieHealth && effectZombie.getHealth() > zombieHealth - 2,
                    "Zombie receives periodic damage with vanilla armor mitigation");
            check(wearer.getHealth() == playerHealth - 2, "Survival caster receives two 1-damage pulses");
            check(observer.getHealth() == 20, "Creative observer retains vanilla damage immunity");
            effectCow.setPos(-5, 65, 0);
            effectZombie.setPos(-4, 65, 0);
            wearer.teleportTo(level, 0.5, 65, 0.5, 0, 0);
            observer.teleportTo(level, 5.5, 65, 0.5, 30, 0);
            wearer.setGameMode(GameType.CREATIVE);
        }
        if (elapsed == 106) {
            for (LivingEntity entity : List.of(effectCow, effectZombie, wearer, observer)) {
                check(!entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Slowness expires after leaving: " + entity.getType());
            }
            Files.writeString(Path.of(System.getProperty("gravity.qa.results"), "server-effects.pass"), "passed\n");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        LOGGER.info("GRAVITY_E2E_ASSERT server: {}", message);
    }

    private static int finish(MinecraftServer server) {
        if (stopAt >= 0) return 1;
        LOGGER.info("GRAVITY_QA_SERVER_FINISHED phase={} elapsedTicks={}",
                phase, server.getTickCount() - startedAt);
        announce(server, "finished");
        // Allow both clients to receive the final system message before shutdown.
        stopAt = server.getTickCount() + 20;
        return 1;
    }

    private GravityTestServer() { }
}
