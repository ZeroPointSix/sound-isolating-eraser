package com.zeropointsix.flareguntest;

import com.zeropointsix.eraser.yuji.flare.FlareContent;
import com.zeropointsix.eraser.yuji.flare.client.FlareGunRenderer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.gui.LoadingErrorScreen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "flare_gun_qa", value = Dist.CLIENT)
public final class FlareGunTestClient {
    private static final Path RESULTS = Path.of(System.getProperty("flare.gun.qa.results"));
    private static final String ROLE = System.getProperty("flare.gun.qa.role", "client");
    private static final boolean OBSERVER = ROLE.equals("observer");
    private static final Set<String> animated = new HashSet<>();
    private static String phase = "";
    private static int ticks, lastFrame = -1, reports, launches, clicks, flashes, flames, smoke;
    private static int completedFires, completedEmpty, completedRefills, samples, bootTicks;
    private static boolean failed, connecting;

    private static Path file(String name) { return RESULTS.resolve(ROLE + "-" + name); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(ROLE + ": " + phase + ": " + message);
    }

    private static void fail(Throwable failure) {
        failed = true;
        failure.printStackTrace();
        try { Files.writeString(RESULTS.resolve(ROLE + ".failed"), failure.toString()); } catch (Exception ignored) { }
    }

    private static void finishPhase() throws Exception {
        if (phase.isEmpty() || phase.equals("ready")) return;
        require(samples > 8, "Insufficient rendered samples: " + samples);
        if (phase.endsWith("fire")) {
            require(reports == 1 && launches == 1, "Expected exactly one of each firing sound");
            require(flashes == 1 && flames == 1 && smoke == 1, "Expected exactly one of each muzzle particle packet");
            require(animated.contains(phase), "Missing rendered recoil");
            completedFires++;
        } else {
            require(reports == 0 && launches == 0, "Rejected/load phase emitted firing audio");
            require(flashes == 0 && flames == 0 && smoke == 0, "Rejected/load phase emitted muzzle particles");
            if (phase.endsWith("empty")) {
                require(!animated.contains(phase), "Empty gun animated");
                require(clicks == 1, "Expected one permitted empty-gun click, got " + clicks);
                completedEmpty++;
            } else {
                require(animated.contains(phase), "Missing rendered break-open loading");
                if (phase.endsWith("refill")) completedRefills++;
            }
        }
        Files.writeString(file("assertions.log"), "PASS " + phase + " samples=" + samples
                + " blast=" + reports + " launch=" + launches + " emptyClick=" + clicks
                + " flash=" + flashes + " flame=" + flames + " smoke=" + smoke + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();
        if (!message.startsWith("FLARE_GUN_QA:")) return;
        event.setCanceled(true);
        if (failed) return;
        try {
            finishPhase();
            phase = message.substring("FLARE_GUN_QA:".length());
            ticks = reports = launches = clicks = flashes = flames = smoke = samples = 0;
            lastFrame = -1;
            var mc = Minecraft.getInstance();
            mc.options.setCameraType(!OBSERVER && phase.contains("third") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
            if (!OBSERVER) {
                mc.options.mainHand().set(phase.startsWith("left") ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
                mc.options.broadcastOptions();
            }
            if (phase.equals("ready")) {
                mc.setScreen(null);
                mc.mouseHandler.grabMouse();
                // Observe decoded packets before vanilla consumes them; never alter the packets.
                mc.getConnection().getConnection().channel().pipeline().addBefore("packet_handler", "flare-gun-qa",
                        new ChannelInboundHandlerAdapter() {
                            @Override public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
                                if (message instanceof ClientboundLevelParticlesPacket packet) mc.execute(() -> particle(packet));
                                super.channelRead(ctx, message);
                            }
                        });
                mc.getConnection().sendChat("flare_gun_qa_ready");
                Files.writeString(RESULTS.resolve(ROLE + ".ready"), "real client connected\n");
            }
            if (phase.equals("done")) {
                require(completedFires == 4 && completedEmpty == 2 && completedRefills == 2,
                        "Missing complete right/left, first/third, empty or cooldown-refill cases");
                Files.writeString(RESULTS.resolve(ROLE + ".pass"), "PASS 4 rendered shots, 2 empty cases, 2 cooldown refills; both sound layers and all muzzle packets asserted per phase; handed muzzle coordinates checked.\n");
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void particle(ClientboundLevelParticlesPacket packet) {
        if (failed || phase.isEmpty() || phase.equals("ready")) return;
        var type = packet.getParticle().getType();
        if (type != ParticleTypes.FLASH && type != ParticleTypes.SMALL_FLAME && type != ParticleTypes.SMOKE) return;
        try {
            if (type == ParticleTypes.FLASH) flashes++;
            if (type == ParticleTypes.SMALL_FLAME) flames++;
            if (type == ParticleTypes.SMOKE) smoke++;
            require(phase.endsWith("fire"), "Muzzle packet in rejected/load phase: " + type);
            var mc = Minecraft.getInstance();
            var actor = mc.level.players().stream().filter(p -> p.getGameProfile().getName().equals("FlareTester")).findFirst().orElseThrow();
            double yaw = Math.toRadians(actor.getYRot());
            int side = phase.startsWith("left") ? -1 : 1;
            Vec3 expected = actor.getEyePosition().add(actor.getLookAngle().scale(.7))
                    .add(new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(side * .22)).add(0, -.18, 0);
            Vec3 actual = new Vec3(packet.getX(), packet.getY(), packet.getZ());
            require(actual.distanceTo(expected) < .04, "Handed muzzle packet misaligned: " + actual + " expected " + expected);
            Files.writeString(file("muzzle.csv"), phase + "," + type + "," + actual.x + "," + actual.y + "," + actual.z + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent public static void sound(PlaySoundEvent event) {
        if (event.getSound() == null || failed) return;
        String sound = event.getSound().getLocation().toString();
        if (sound.equals("minecraft:entity.firework_rocket.blast")) reports++;
        if (sound.equals("minecraft:entity.firework_rocket.launch")) launches++;
        if (sound.equals("minecraft:block.dispenser.fail")) clicks++;
        if (!phase.endsWith("fire") && (reports > 0 || launches > 0))
            fail(new AssertionError(phase + ": Unexpected firing sound " + sound));
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed) return;
        var mc = Minecraft.getInstance();
        if (phase.isEmpty()) {
            bootTicks++;
            try {
                if (bootTicks % 100 == 0 && mc.getOverlay() == null) {
                    System.out.println("FLARE_GUN_BOOT screen=" + mc.screen);
                    try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                        image.writeToFile(file("bootstrap.png"));
                    }
                }
                if (mc.screen instanceof LoadingErrorScreen)
                    throw new AssertionError("Forge loading error/warning screen: " + mc.screen.getNarrationMessage().getString());
                if (mc.screen instanceof DisconnectedScreen)
                    throw new AssertionError(mc.screen.getNarrationMessage().getString());
                if (!connecting && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)
                        && mc.getOverlay() == null) {
                    connecting = true;
                    String address = "127.0.0.1:" + System.getProperty("flare.gun.qa.port", "25594");
                    ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(address),
                            new ServerData("Flare QA", address, false), false);
                }
            } catch (Throwable failure) { fail(failure); }
            return;
        }
        if (mc.player == null || mc.gameMode == null) return;
        ticks++;
        if (!OBSERVER && ticks == 10) {
            if (phase.endsWith("refill"))
                mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0));
            else if (phase.endsWith("fire") || phase.endsWith("empty"))
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || ticks == lastFrame || ticks > 95
                || phase.isEmpty() || phase.equals("ready") || phase.equals("done")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            lastFrame = ticks;
            samples++;
            var renderer = (FlareGunRenderer) IClientItemExtensions.of(FlareContent.GUN.get()).getCustomRenderer();
            var root = renderer.getGeoModel().getBone("root").orElseThrow();
            var barrel = renderer.getGeoModel().getBone("barrel").orElseThrow();
            float angle = phase.endsWith("load") || phase.endsWith("refill") ? barrel.getRotX() : root.getRotX();
            if (Math.abs(angle) > .08F) animated.add(phase);
            if (phase.endsWith("empty") || (phase.endsWith("refill") && ticks <= 18))
                require(Math.abs(root.getRotX()) < .02F && Math.abs(barrel.getRotX()) < .02F,
                        "Rejected shot/early cooldown refill animated: " + root.getRotX() + "," + barrel.getRotX());
            Files.writeString(file("poses.csv"), phase + "," + ticks + "," + root.getRotX() + "," + barrel.getRotX() + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (Set.of(0, 5, 10, 12, 14, 18, 22, 28, 35, 45, 55).contains(ticks)) {
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(file(String.format("%s-%03d.png", phase, ticks)));
                }
            }
        } catch (Throwable failure) { fail(failure); }
    }
}
