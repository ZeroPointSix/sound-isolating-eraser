package com.zeropointsix.gravitytest;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.zeropointsix.eraser.block.EraserAnchorBlock;
import com.zeropointsix.eraser.block.EraserWallBlock;
import com.zeropointsix.eraser.eraser.EraserMode;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import com.zeropointsix.eraser.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
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

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("round2qa")
                .requires(s -> s.getServer().isDedicatedServer() && s.getEntity() instanceof ServerPlayer p
                        && p.getGameProfile().getName().equals(GravityTestServer.WEARER))
                .then(Commands.literal("clear").executes(c -> {
                    clear(c.getSource().getServer());
                    return 1;
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
                                for (BlockPos pos : BlockPos.betweenClosed(38, 65, -6, 50, 69, 8)) {
                                    var state = player.level().getBlockState(pos);
                                    if (state.getBlock() instanceof EraserAnchorBlock) anchors++;
                                    if (EraserWallBlock.isEraserWall(state)) walls++;
                                }
                                int expected = index == 5 ? 0 : index == 0 ? 1 : index == 4 ? 12 : 5;
                                check(anchors == expected && walls == expected * 5, "server complete drawing " + index);
                                if (index == 5) mark("server-eraser.pass", "all five native placements and server mode cycle verified\n");
                                else c.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                        Component.literal("ROUND2_ERASER_CHECK:" + index), false);
                                return 1;
                            } catch (Throwable failure) { fail(failure); return 0; }
                        }))));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pressureDone) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
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
