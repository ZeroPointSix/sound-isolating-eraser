package com.zeropointsix.gravitytest;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.zeropointsix.eraser.block.EraserAnchorBlock;
import com.zeropointsix.eraser.block.EraserWallBlock;
import com.zeropointsix.eraser.eraser.EraserMode;
import com.zeropointsix.eraser.eraser.EraserAggro;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import com.zeropointsix.eraser.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Loaded only by the existing opt-in, loopback-only two-client QA harness. */
@Mod.EventBusSubscriber(modid = "gravity_qa")
public final class RoundTwoTestServer {
    private static final Path RESULTS = Path.of(System.getProperty("gravity.qa.results"));
    private static GravityFieldEntity pressure;
    private static List<LivingEntity> victims;
    private static List<Vec3> positions;
    private static int started;
    private static boolean pressureDone;
    private static BlockPos ringCenter;
    private static BlockPos obstruction;
    private static Mob isolatedMob;
    private static Mob advancedMob;
    private static Mob boss;
    private static int isolationStage;
    private static int isolationSince;
    private static final BlockPos ISOLATION_WALL = new BlockPos(83,65,0);

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("round2qa")
                .requires(s -> s.getServer().isDedicatedServer() && s.getEntity() instanceof ServerPlayer p
                        && p.getGameProfile().getName().equals(GravityTestServer.WEARER))
                .then(Commands.literal("clear").executes(c -> {
                    clear(c.getSource().getServer());
                    return 1;
                }))
                .then(Commands.literal("obstruct").executes(c -> {
                    try {
                        var player = c.getSource().getPlayerOrException();
                        clear(c.getSource().getServer());
                        EraserMode.RING.write(player.getMainHandItem());
                        player.inventoryMenu.broadcastChanges();
                        var bases = EraserMode.RING.bases(ringCenter, player.getDirection());
                        obstruction = bases.get(bases.size() - 1).above(4);
                        player.level().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
                        return 1;
                    } catch (Throwable failure) { fail(failure); return 0; }
                }))
                .then(Commands.literal("atomic").executes(c -> {
                    try {
                        var player = c.getSource().getPlayerOrException();
                        for (BlockPos pos : BlockPos.betweenClosed(38,65,-6,50,69,8))
                            check(!EraserWallBlock.isEraserWall(player.level().getBlockState(pos)), "invalid native drawing leaves no partial wall");
                        check(player.level().getBlockState(obstruction).is(Blocks.STONE), "obstruction preserved");
                        check(player.getMainHandItem().getDamageValue() == 0, "rejected drawing costs no durability");
                        mark("server-atomic.pass", "native ring placement with one obstructed cell rejected entirely\n");
                        return 1;
                    } catch (Throwable failure) { fail(failure); return 0; }
                }))
                .then(Commands.literal("verify").then(Commands.argument("mode", IntegerArgumentType.integer(0, 5))
                        .executes(c -> {
                            try {
                                int index = IntegerArgumentType.getInteger(c, "mode");
                                var player = c.getSource().getPlayerOrException();
                                check(EraserMode.read(player.getMainHandItem()).ordinal() == index % 5,
                                        "server accepted mode " + index);
                                int anchors = 0;
                                int walls = 0;
                                int sumX = 0, sumZ = 0;
                                for (BlockPos pos : BlockPos.betweenClosed(38, 65, -6, 50, 69, 8)) {
                                    var state = player.level().getBlockState(pos);
                                    if (state.getBlock() instanceof EraserAnchorBlock) {
                                        anchors++;
                                        sumX += pos.getX();
                                        sumZ += pos.getZ();
                                    }
                                    if (EraserWallBlock.isEraserWall(state)) walls++;
                                }
                                int expected = index == 5 ? 0 : index == 0 ? 1 : index == 4 ? 12 : 5;
                                check(anchors == expected && walls == expected * 5, "server complete drawing " + index);
                                if (index == 4) ringCenter = new BlockPos(sumX / anchors,65,sumZ / anchors);
                                if (index == 5) mark("server-eraser.pass", "all five native placements and server mode cycle verified\n");
                                else c.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                        Component.literal("ROUND2_ERASER_CHECK:" + index), false);
                                return 1;
                            } catch (Throwable failure) { fail(failure); return 0; }
                        }))));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isDedicatedServer() && pressureDone) {
            try { verifyIsolation(server); } catch (Throwable failure) { isolationStage = -1; fail(failure); }
            return;
        }
        if (server == null || !server.isDedicatedServer()
                || !Files.exists(RESULTS.resolve("wearer.pass")) || !Files.exists(RESULTS.resolve("observer.pass"))) return;
        var wearer = server.getPlayerList().getPlayerByName(GravityTestServer.WEARER);
        var observer = server.getPlayerList().getPlayerByName(GravityTestServer.OBSERVER);
        if (wearer == null || observer == null) return;
        try {
            var level = server.overworld();
            if (pressure == null) {
                for (BlockPos pos : BlockPos.betweenClosed(59, 64, -5, 70, 64, 5))
                    level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                wearer.setGameMode(GameType.SURVIVAL);
                wearer.getFoodData().setFoodLevel(10);
                wearer.teleportTo(level, 64.5, 65, 1.7, 0, 0);
                observer.teleportTo(level, 70.5, 65, 0.5, 90, 0);
                var cow = EntityType.COW.create(level);
                var zombie = EntityType.ZOMBIE.create(level);
                victims = List.of(cow, zombie, wearer);
                positions = List.of(new Vec3(63.3,65,0.5), new Vec3(65.7,65,0.5), new Vec3(64.5,65,1.7));
                for (int i = 0; i < victims.size(); i++) {
                    var entity = victims.get(i);
                    entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
                    entity.getAttribute(Attributes.ARMOR).setBaseValue(0);
                    entity.setHealth(100);
                    entity.setDeltaMovement(Vec3.ZERO);
                    if (entity instanceof Mob mob) {
                        mob.setNoAi(true);
                        mob.setNoGravity(true);
                        mob.setPos(positions.get(i));
                        level.addFreshEntity(mob);
                    }
                }
                pressure = GravityFieldEntity.create(level, observer.getUUID(), new BlockPos(64,67,0));
                level.addFreshEntity(pressure);
                started = server.getTickCount();
            }
            int elapsed = server.getTickCount() - started;
            if (elapsed > 0 && elapsed <= 300 && elapsed % 20 == 0) {
                int pulses = elapsed / 20;
                for (int i = 0; i < victims.size(); i++) {
                    var entity = victims.get(i);
                    check(Math.abs(entity.getHealth() - (100 - pulses)) < 0.01,
                            "pulse " + pulses + " health=" + entity.getHealth() + " " + entity.getType());
                    check(entity.position().distanceToSqr(positions.get(i)) < 0.0025,
                            "pulse " + pulses + " no hurt displacement " + entity.getType());
                    check(pressure.fieldBounds().contains(entity.position()), "inside field");
                    check(entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 2, "Slowness III");
                }
            }
            if (elapsed == 310) {
                check(pressure.isRemoved(), "300-tick lifetime retained");
                for (var entity : victims) check(!entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "effect fades within 10 ticks");
                mark("server-pressure.pass", "15 pulses: Cow=85 Zombie=85 real Player=85; displacement <0.05 block; Slowness III; expiry/fade passed\n");
                victims.get(0).discard();
                victims.get(1).discard();
                pressureDone = true;
                clear(server);
                wearer.setGameMode(GameType.CREATIVE);
                wearer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOUND_ISOLATING_ERASER.get()));
                wearer.teleportTo(level, 44.5,65,-3.5,0,25);
                observer.teleportTo(level, 48.5,65,-3.5,35,25);
                server.getPlayerList().broadcastSystemMessage(Component.literal("ROUND2_ERASER_READY"), false);
            }
        } catch (Throwable failure) { pressureDone = true; fail(failure); }
    }

    private static void clear(MinecraftServer server) {
        for (BlockPos pos : BlockPos.betweenClosed(38, 64, -6, 50, 69, 8))
            server.overworld().setBlockAndUpdate(pos, pos.getY() == 64 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    private static void verifyIsolation(MinecraftServer server) throws Exception {
        if (isolationStage < 0 || !Files.exists(RESULTS.resolve("server-atomic.pass"))) return;
        var level = server.overworld();
        var player = server.getPlayerList().getPlayerByName(GravityTestServer.WEARER);
        if (player == null) return;
        int now = server.getTickCount();
        if (isolationStage == 0) {
            for (var pos : BlockPos.betweenClosed(77,64,-5,92,64,11)) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(level,86.5,65,0.5,90,0);
            isolatedMob = EntityType.ZOMBIE.create(level);
            isolatedMob.setPersistenceRequired();
            // Keep natural target AI active while holding the sensing geometry stable.
            isolatedMob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            isolatedMob.setPos(80.5,65,0.5);
            level.addFreshEntity(isolatedMob);
            isolationStage = 1;
            isolationSince = now;
        }
        if (now - isolationSince >= 160)
            throw new AssertionError("real AI isolation phase " + isolationStage + " timed out");
        if (isolationStage == 1 && isolatedMob.getTarget() == player) {
            check(true, "real zombie AI acquired connected survival player");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOUND_ISOLATING_ERASER.get()));
            EraserMode.ACROSS.write(player.getMainHandItem());
            check(player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atBottomCenterOf(ISOLATION_WALL), Direction.UP, ISOLATION_WALL.below(), false))).consumesAction(),
                    "real eraser use raises wall between AI zombie and real player");
            advancedMob = EntityType.VINDICATOR.create(level);
            boss = EntityType.WITHER.create(level);
            for (var entity : List.of(advancedMob, boss)) {
                entity.setNoAi(true);
                entity.setNoGravity(true);
                entity.setInvulnerable(true);
                entity.setPos(80.5,68,0.5);
                level.addFreshEntity(entity);
                entity.setTarget(player);
                check(!entity.getType().is(EraserAggro.ISOLATED), "advanced/boss excluded from tag");
            }
            isolationStage = 2;
            isolationSince = now;
        } else if (isolationStage == 2 && now - isolationSince >= 8) {
            check(EraserAggro.blocked(isolatedMob, player), "wall actually crosses sensing ray");
            check(isolatedMob.getTarget() == null, "wall clears existing AI target");
            isolatedMob.setTarget(player);
            check(isolatedMob.getTarget() == null, "wall rejects explicit reacquisition too");
            for (var entity : List.of(advancedMob, boss)) {
                check(entity.getTarget() == player, "advanced/boss retains target through wall");
                entity.discard();
            }
            player.teleportTo(level,86.5,65,9.5,90,0);
            isolationStage = 3;
            isolationSince = now;
        } else if (isolationStage == 3 && isolatedMob.getTarget() == player) {
            check(!EraserAggro.blocked(isolatedMob, player), "bypassing wall lets real AI rediscover player");
            player.teleportTo(level,86.5,65,0.5,90,0);
            isolationStage = 4;
            isolationSince = now;
        } else if (isolationStage == 4 && now - isolationSince >= 8) {
            check(isolatedMob.getTarget() == null, "returning behind wall isolates player again");
            level.destroyBlock(ISOLATION_WALL.above(2), false);
            isolationStage = 5;
            isolationSince = now;
        } else if (isolationStage == 5 && isolatedMob.getTarget() == player) {
            check(!EraserAggro.blocked(isolatedMob, player), "breaking column lets real AI rediscover player");
            isolatedMob.discard();
            mark("server-isolation.pass", "real Zombie AI: acquire, wall loss, bypass reacquire, wall loss, break reacquire; Vindicator/Wither retain target\n");
            isolationStage = -1;
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        System.out.println("ROUND2_E2E_ASSERT server: " + message);
    }

    private static void mark(String name, String text) throws Exception { Files.writeString(RESULTS.resolve(name), text); }
    private static void fail(Throwable failure) {
        failure.printStackTrace();
        try { mark("server-round2.failed", failure.toString()); } catch (Exception ignored) { }
    }
}
