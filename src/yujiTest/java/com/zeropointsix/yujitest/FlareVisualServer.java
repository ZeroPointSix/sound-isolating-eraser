package com.zeropointsix.yujitest;

import com.zeropointsix.eraser.yuji.flare.FallingSupplyCrate;
import com.zeropointsix.eraser.yuji.flare.FlareConfig;
import com.zeropointsix.eraser.yuji.flare.FlareContent;
import com.zeropointsix.eraser.yuji.flare.FlareDecoy;
import com.zeropointsix.eraser.yuji.flare.FlareProjectile;
import com.zeropointsix.eraser.yuji.flare.SupplyCrateBlockEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "yuji_qa")
public final class FlareVisualServer {
    private static final Path RESULTS = Path.of(System.getProperty("yuji.qa.results"));
    private static final String[] COLORS = {"red", "yellow", "green"};
    private static final Set<BlockPos> redFlightLights = new LinkedHashSet<>();
    private static final StringBuilder evidence = new StringBuilder();
    private static String phase = "waiting";
    private static int color, elapsed, phaseTicks, lightSamples, maximumLight;
    private static FlareProjectile flare;
    private static FlareDecoy decoy;
    private static FallingSupplyCrate falling;
    private static BlockPos landing;
    private static Vec3 activation;

    private static boolean enabled() { return Boolean.getBoolean("flare.qa.visuals"); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static BlockPos center() { return new BlockPos(color * 128, 65, 0); }
    private static AABB area() { return new AABB(center()).inflate(64); }
    private static boolean captured(String name) { return Files.exists(RESULTS.resolve("client-visual-" + name + ".pass")); }
    private static void signal(ServerPlayer player, String next, Vec3 target) {
        phase = next; phaseTicks = 0;
        player.sendSystemMessage(Component.literal("FLARE_VISUALS:" + COLORS[color] + "-" + next
                + ":" + target.x + ":" + target.y + ":" + target.z));
        System.out.println("FLARE_VISUAL_PHASE " + COLORS[color] + "-" + next + " target=" + target);
    }

    @SubscribeEvent public static void prepare(ServerStartedEvent event) {
        if (!enabled()) return;
        var level = event.getServer().overworld();
        level.setDayTime(18000);
        level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        for (int index = 0; index < COLORS.length; index++) {
            int x = index * 128;
            for (int cx = (x - 64) >> 4; cx <= (x + 64) >> 4; cx++)
                for (int cz = -4; cz <= 4; cz++) level.setChunkForced(cx, cz, true);
            for (BlockPos pos : BlockPos.betweenClosed(x - 44, 64, -44, x + 44, 64, 44))
                level.setBlockAndUpdate(pos, ((Math.floorDiv(pos.getX(), 4) + Math.floorDiv(pos.getZ(), 4)) & 1) == 0
                        ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
        }
    }

    private static void arm(ServerPlayer player) {
        var origin = center();
        player.teleportTo(player.serverLevel(), origin.getX() + .5, 65, .5, 0, -65);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlareContent.GUN.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FlareContent.ammunition(color), 2));
        flare = null; decoy = null; falling = null; landing = null; lightSamples = 0; maximumLight = 0;
        signal(player, "armed", Vec3.atCenterOf(origin));
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)
                || !player.getGameProfile().getName().equals("FlareVisualQA")) return;
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().mayfly = true; player.getAbilities().flying = true;
        player.onUpdateAbilities();
        arm(player);
    }

    private static void sampleFlightLight(ServerPlayer player) {
        var level = player.serverLevel();
        int brightness = 0;
        boolean source = false;
        for (BlockPos pos : BlockPos.betweenClosed(flare.blockPosition().offset(-3, -3, -3), flare.blockPosition().offset(3, 3, 3))) {
            brightness = Math.max(brightness, level.getBrightness(LightLayer.BLOCK, pos));
            var state = level.getBlockState(pos);
            if (state.is(Blocks.LIGHT) && state.getValue(LightBlock.LEVEL) == 15) {
                source = true;
                if (color == 0) redFlightLights.add(pos.immutable());
            }
        }
        maximumLight = Math.max(maximumLight, brightness);
        if (source && brightness >= 12) lightSamples++;
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!enabled() || event.phase != TickEvent.Phase.END || phase.equals("done") || phase.equals("failed")) return;
        var player = event.getServer().getPlayerList().getPlayerByName("FlareVisualQA");
        if (player == null) return;
        try {
            require(++elapsed < 2400, "three-color client evidence timed out in " + COLORS[color] + "-" + phase);
            phaseTicks++;
            var level = player.serverLevel();
            require(FlareConfig.RED_DELAY.get() == 60 && FlareConfig.YELLOW_DELAY.get() == 30
                    && FlareConfig.GREEN_DELAY.get() == 80 && FlareConfig.TAUNT_RADIUS.get() == 24,
                    "visual evidence preserves production activation delays and taunt radius");
            if (phase.equals("armed")) {
                var projectiles = level.getEntitiesOfClass(FlareProjectile.class, area(), entity -> entity.color() == color && !entity.lit());
                if (projectiles.isEmpty()) return;
                require(projectiles.size() == 1 && player.getOffhandItem().getCount() == 1, "one actual client shot consumes one round");
                flare = projectiles.get(0);
                player.teleportTo(level, center().getX() + 12.5, 78, 14.5, 140, -25);
                signal(player, "flight", flare.position());
            }
            if (phase.equals("flight")) {
                if (!flare.isRemoved() && !flare.lit()) sampleFlightLight(player);
                boolean active = color == 1 ? flare.lit() : flare.isRemoved();
                if (!active) return;
                require(captured(COLORS[color] + "-flight"), "client rendered the actual " + COLORS[color] + " flight before activation");
                require(lightSamples >= 2 && maximumLight >= 12, "actual " + COLORS[color]
                        + " flight needs level-15 sources and block light >=12; samples=" + lightSamples + " maximum=" + maximumLight);
                evidence.append(COLORS[color]).append(" shot=1 lightSamples=").append(lightSamples)
                        .append(" maximumBlockLight=").append(maximumLight).append('\n');
                activation = flare.position();
                if (color == 0) {
                    var decoys = level.getEntitiesOfClass(FlareDecoy.class, area());
                    require(decoys.size() == 1, "red activation creates exactly one decoy"); decoy = decoys.get(0);
                } else if (color == 2) {
                    var crates = level.getEntitiesOfClass(FallingSupplyCrate.class, area());
                    require(crates.size() == 1, "green activation creates exactly one falling crate"); falling = crates.get(0);
                    landing = BlockPos.containing(falling.getX(), 65, falling.getZ());
                }
                signal(player, "activation", activation);
                return;
            }
            if (phase.equals("activation") && captured(COLORS[color] + "-activation") && phaseTicks >= 30) {
                if (color == 0) {
                    if (!decoy.onGround()) return;
                    Vec3 target = decoy.position().add(0, .3, 0);
                    player.teleportTo(level, target.x, target.y + 38, target.z + 38, 180, 45);
                    signal(player, "radius", target);
                } else if (color == 1) {
                    require(level.getBrightness(LightLayer.BLOCK, flare.blockPosition()) >= 12, "yellow activation illuminates actual world blocks at night");
                    player.teleportTo(level, activation.x + 13, activation.y + 5, activation.z + 16, 140, 15);
                    signal(player, "night-light", activation);
                } else {
                    player.teleportTo(level, falling.getX() + 9, 84, falling.getZ() + 12, 140, 20);
                    signal(player, "descent", falling.position());
                }
            }
            if (phase.equals("radius") && captured("red-radius") && phaseTicks >= 30) {
                require(!redFlightLights.isEmpty() && redFlightLights.stream().noneMatch(pos -> level.getBlockState(pos).is(Blocks.LIGHT)),
                        "temporary red flight sources expire after activation");
                evidence.append("red radius=").append(FlareConfig.TAUNT_RADIUS.get()).append(" temporaryFlightLightsRemoved=true\n");
                color++; arm(player);
            } else if (phase.equals("night-light") && captured("yellow-night-light") && phaseTicks >= 30) {
                color++; arm(player);
            } else if (phase.equals("descent") && level.getBlockEntity(landing) instanceof SupplyCrateBlockEntity) {
                require(falling.isRemoved() && captured("green-descent"), "native client captured natural green crate descent");
                player.teleportTo(level, landing.getX() + 3, 67, landing.getZ() + 4, 140, 20);
                signal(player, "landed", Vec3.atCenterOf(landing));
            } else if (phase.equals("landed") && captured("green-landed") && Files.exists(RESULTS.resolve("client-visuals.pass"))) {
                require(level.getBlockEntity(landing) instanceof SupplyCrateBlockEntity, "green visual sample reached real landing");
                Files.writeString(RESULTS.resolve("server-visuals.pass"), evidence + "three actual client shots; night=18000; native screenshots; green landed\n");
                System.out.println("FLARE_VISUAL_SERVER_PASSED " + evidence.toString().replace('\n', ' '));
                phase = "done";
            }
        } catch (Throwable failure) {
            phase = "failed"; failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve("server-visuals.failed"), failure.toString()); } catch (Exception ignored) { }
        }
    }
}
