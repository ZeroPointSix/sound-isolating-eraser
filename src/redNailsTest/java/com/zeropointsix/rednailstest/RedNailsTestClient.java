package com.zeropointsix.rednailstest;

import com.zeropointsix.eraser.client.RedNailsClient;
import com.zeropointsix.eraser.client.WaterGhostModel;
import com.zeropointsix.eraser.rednails.*;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.ModMain;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "red_nails_qa", value = Dist.CLIENT)
public final class RedNailsTestClient {
    private static final boolean PURSUIT = Boolean.getBoolean("rednails.qa.pursuit");
    private static final Path RESULTS = Path.of(System.getProperty("rednails.qa.results"));
    private static String phase = "";
    private static int ticks, frame, longPresses, longPhase, longPhaseTicks;
    private static boolean failed, captured, shortAcked;
    private static boolean shoreAttackSent, shoreClientGrabSeen, wildClientGrabSeen;
    private static Vec3 walkStart;
    private static ArmorStand camera;
    private static final HashMap<Integer, Integer> rendered = new HashMap<>();
    private static final HashMap<Integer, float[]> poses = new HashMap<>();
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        Minecraft.getInstance().options.keyUp.setDown(false);
        Minecraft.getInstance().options.keySprint.setDown(false);
        RedNailsClient.KEY.setDown(false);
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(RESULTS.resolve("client-failure.png"));
        } catch (Exception ignored) { }
        try { Files.writeString(RESULTS.resolve("client.failed"), error.toString()); } catch (Exception ignored) { }
    }
    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("RED_NAILS_QA:")) {
            if (phase.equals("recall")) {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.setShiftKeyDown(false);
                    player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                }
            }
            phase = text.substring("RED_NAILS_QA:".length()); ticks = 0; captured = false; shortAcked = false;
            Minecraft.getInstance().options.keyUp.setDown(false);
            Minecraft.getInstance().options.keySprint.setDown(false);
            walkStart = null; shoreAttackSent = false;
            longPresses = 0; longPhase = 0; longPhaseTicks = 0;
            rendered.clear(); poses.clear(); RedNailsClient.KEY.setDown(false); event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGH) public static void tick(TickEvent.ClientTickEvent event) {
        if (Boolean.getBoolean("rednails.qa.acceptance")) return;
        if (event.phase != TickEvent.Phase.END || failed) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (mc.screen instanceof DisconnectedScreen) {
                fail(new AssertionError("Native client disconnected during phase=" + phase));
            }
            return;
        }
        try {
            var dispatcher = mc.getEntityRenderDispatcher();
            for (var entity : mc.level.entitiesForRendering()) {
                if (dispatcher.getRenderer(entity) == null) {
                    throw new AssertionError("Missing renderer type="
                            + net.minecraft.world.entity.EntityType.getKey(entity.getType())
                            + " class=" + entity.getClass().getName()
                            + " summonIdentity=" + (entity.getType() == ModEntities.WATER_GHOST_SUMMON.get())
                            + " ghostIdentity=" + (entity.getType() == ModEntities.WATER_GHOST.get()));
                }
            }
            if (phase.isEmpty()) {
                if (mc.getOverlay() != null) return;
                if (RedNailsItem.findWorn(mc.player).isEmpty() || !mc.level.hasChunkAt(mc.player.blockPosition())) return;
                GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.setScreen(null);
                mc.mouseHandler.grabMouse();
                if (!mc.isWindowActive()) return;
                Files.writeString(RESULTS.resolve("client-ready.signal"),
                        "overlay=null windowActive=true curios synchronized.\n");
                System.out.println("RED_NAILS_CLIENT_READY overlay=null windowActive=true");
                return;
            }
            ticks++;
            mc.options.hideGui = phase.equals("guard") || phase.equals("water");
            if (phase.equals("input") || phase.equals("recall") || phase.equals("shore")
                    || phase.startsWith("wild")) {
                GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.setScreen(null);
                mc.mouseHandler.grabMouse();
                if (!mc.isWindowActive()) {
                    RedNailsClient.KEY.setDown(false);
                    if (!shortAcked) ticks = 0;
                    else if (longPhase == 1) longPhaseTicks = 0;
                    return;
                }
            }
            if (phase.equals("input")) {
                if (!shortAcked) {
                    if (ticks == 1) RedNailsClient.KEY.setDown(true);
                    if (ticks == 10) RedNailsClient.KEY.setDown(false);
                    if (ticks == 11) {
                        Files.writeString(RESULTS.resolve("client-short-press.signal"),
                                "short hold released after production client tick windowActive=true\n");
                    }
                    if (ticks >= 11 && Files.exists(RESULTS.resolve("server-short-press.ack"))) {
                        shortAcked = true;
                        ticks = 0;
                        longPresses = 0;
                        longPhase = 0;
                        longPhaseTicks = 0;
                        RedNailsClient.KEY.setDown(false);
                    }
                    return;
                }
                if (longPresses >= 4) {
                    RedNailsClient.KEY.setDown(false);
                    return;
                }
                if (longPhase == 0) {
                    RedNailsClient.KEY.setDown(false);
                    if (++longPhaseTicks >= 20) { longPhase = 1; longPhaseTicks = 0; }
                    return;
                }
                if (longPhase == 1) {
                    if (longPhaseTicks == 0) {
                        System.out.println("RED_NAILS_QA_LONG_PRESS=" + (longPresses + 1));
                    }
                    RedNailsClient.KEY.setDown(true);
                    // Client and server tick clocks can diverge under software-rendering load.
                    if (++longPhaseTicks >= 25 && (!PURSUIT || Files.exists(RESULTS.resolve(
                            "server-long-press-" + (longPresses + 1) + ".ack")))) {
                        RedNailsClient.KEY.setDown(false);
                        longPhase = 2;
                        longPhaseTicks = 0;
                    }
                    return;
                }
                RedNailsClient.KEY.setDown(false);
                if (++longPhaseTicks >= 15) {
                    longPresses++;
                    longPhase = 1;
                    longPhaseTicks = 0;
                }
            }
            if (phase.equals("recall")) {
                if (ticks == 1) {
                    mc.player.setShiftKeyDown(true);
                    mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                    RedNailsClient.KEY.setDown(true);
                }
                if (ticks == 40) {
                    RedNailsClient.KEY.setDown(false); mc.player.setShiftKeyDown(false);
                    mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                }
            }
            if (PURSUIT && phase.equals("wild-ready")) {
                mc.setCameraEntity(mc.player);
                mc.player.setYRot(180); mc.player.setYHeadRot(180); mc.player.setXRot(0);
                mc.options.keyUp.setDown(false);
                if (!Files.exists(RESULTS.resolve("client-wild-ready.signal"))) {
                    Files.writeString(RESULTS.resolve("client-wild-ready.signal"),
                            "Real client holds position pending natural pursuit; player=" + mc.player.position() + "\n");
                }
                return;
            }
            if (PURSUIT && phase.equals("wild-watch")) {
                mc.setCameraEntity(mc.player);
                mc.player.setYRot(14); mc.player.setYHeadRot(14); mc.player.setXRot(15);
                mc.options.keyUp.setDown(false);
                if (ticks >= 60 && !Files.exists(RESULTS.resolve("client-wild-watch.pass"))) {
                    boolean seen = false;
                    for (var entity : mc.level.entitiesForRendering()) {
                        if (entity instanceof WaterGhostEntity ghost) {
                            require(ghost.waterGhostGrabAmount(1) < 0.01F, "watching ghost is not grabbing on the actual client");
                            seen = true;
                        }
                    }
                    require(seen, "watching wild ghost is present in the actual client world");
                    Files.writeString(RESULTS.resolve("client-wild-watch.pass"),
                            "Real connected client observed watching ghost without grab for " + ticks + " client ticks.\n");
                }
                return;
            }
            if (PURSUIT && (phase.equals("wild-escape") || phase.equals("wild-return"))) {
                mc.setCameraEntity(mc.player);
                double targetZ = mc.player.getZ() > -6 ? -6 : -16;
                double dx = 0.5 - mc.player.getX(), dz = targetZ - mc.player.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                mc.player.setYRot(yaw); mc.player.setYHeadRot(yaw); mc.player.setXRot(0);
                boolean moving = phase.equals("wild-escape") && mc.player.getZ() > -15.5;
                mc.options.keyUp.setDown(moving); mc.options.keySprint.setDown(moving);
                if (ticks % 10 == 0) System.out.println("RED_NAILS_NATIVE_ESCAPE phase=" + phase
                        + " tick=" + ticks + " moving=" + moving + " player=" + mc.player.position());
                return;
            }
            if (PURSUIT && phase.equals("wild-lurk")) {
                mc.setCameraEntity(mc.player);
                for (var entity : mc.level.entitiesForRendering()) if (entity instanceof WaterGhostEntity ghost) {
                    Vec3 delta = ghost.position().subtract(mc.player.getEyePosition());
                    float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
                    mc.player.setYRot(yaw); mc.player.setYHeadRot(yaw);
                    mc.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
                    if (ticks >= 40 && ghost.waterGhostSwimAmount(1) > 0.9F && ghost.waterGhostGrabAmount(1) < 0.01F) {
                        Files.writeString(RESULTS.resolve("client-wild-lurk.pass"),
                                "Actual client sees returned ghost swimming without grab; ticks=" + ticks + "\n");
                    }
                }
                return;
            }
            if (PURSUIT && (phase.equals("shore") || phase.equals("wild"))) {
                for (var entity : mc.level.entitiesForRendering()) {
                    if (phase.equals("shore") && !shoreAttackSent
                            && entity instanceof net.minecraft.world.entity.monster.Pillager victim
                            && victim.distanceToSqr(mc.player) < 9) {
                        mc.player.setYRot(90); mc.player.setYHeadRot(90); mc.player.setXRot(0);
                        Files.writeString(RESULTS.resolve("client-shore-attack.signal"), "Actual game-mode attack packet target=" + victim.getUUID()
                                + " player=" + mc.player.position() + " victim=" + victim.position() + "\n");
                        mc.gameMode.attack(mc.player, victim);
                        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                        shoreAttackSent = true;
                    }
                    if (entity instanceof WaterGhostPoseSource source) {
                        if (phase.equals("shore") && source.waterGhostGrabAmount(1) > 0.9F
                                && source.waterGhostSwimAmount(1) > 0.9F) shoreClientGrabSeen = true;
                        if (phase.equals("wild") && source.waterGhostGrabAmount(1) > 0.9F) wildClientGrabSeen = true;
                        if (ticks % 10 == 0) System.out.println("RED_NAILS_NATIVE_CLIENT_SAMPLE phase=" + phase + " frame=" + frame
                                + " player=" + mc.player.position() + " entity=" + entity.getUUID() + " position=" + entity.position()
                                + " grab=" + source.waterGhostGrabAmount(1) + " swim=" + source.waterGhostSwimAmount(1));
                    }
                }
                if (phase.equals("wild")) {
                    require(Files.exists(RESULTS.resolve("server-wild-started.pass")),
                            "keyboard movement waits for natural server pursuit");
                    mc.setCameraEntity(mc.player);
                    mc.player.setYRot(180); mc.player.setYHeadRot(180); mc.player.setXRot(0);
                    if (walkStart == null) walkStart = mc.player.position();
                    mc.options.keyUp.setDown(!wildClientGrabSeen && mc.player.position().subtract(walkStart).horizontalDistance() < 3);
                    return;
                }
            }
            Vec3 center = phase.equals("water") ? new Vec3(-1, 72, 6) : mc.player.position().add(0, 1.2, 0);
            Vec3 at = center.add(7, phase.equals("water") ? 5 : 7, -9);
            if (camera == null) camera = new ArmorStand(mc.level, at.x, at.y, at.z);
            camera.setPos(at); camera.xo = at.x; camera.yo = at.y; camera.zo = at.z;
            Vec3 toward = center.subtract(camera.getEyePosition());
            camera.setYRot((float) Math.toDegrees(Math.atan2(toward.z, toward.x)) - 90);
            camera.setXRot((float) -Math.toDegrees(Math.atan2(toward.y, toward.horizontalDistance())));
            camera.yRotO = camera.getYRot(); camera.xRotO = camera.getXRot();
            if (!phase.equals("input") && !phase.equals("recall")) mc.setCameraEntity(camera);
            else mc.setCameraEntity(mc.player);
        } catch (Throwable error) { fail(error); }
    }
    @SubscribeEvent public static void model(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity() instanceof WaterGhostPoseSource source && event.getRenderer().getModel() instanceof WaterGhostModel<?> model) {
            rendered.put(event.getEntity().getId(), frame);
            poses.put(event.getEntity().getId(), new float[]{model.root().xRot, source.waterGhostSwimAmount(1), source.waterGhostGrabAmount(1)});
        }
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) { frame++; return; }
        if (failed || captured || ticks < 15 || !(phase.equals("guard") || phase.equals("backlash")
                || phase.equals("recalled") || phase.equals("water") || phase.equals("restored")
                || phase.equals("shore-complete") || phase.equals("wild-complete"))) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        try {
            var stack = RedNailsItem.findWorn(mc.player);
            require(!stack.isEmpty(), "Actual client sees functional Curios item");
            if (phase.equals("shore-complete")) require(shoreClientGrabSeen, "real client observed synchronized normal-AI swimming grab");
            if (phase.equals("wild-complete")) require(wildClientGrabSeen, "real client observed normal wild-AI player grab");
            if (phase.equals("guard") && rendered.values().stream().filter(f -> f == frame).count() < 4) {
                if (ticks < 300) return;
                throw new AssertionError("Four live guardians must render in current frame");
            }
            if (phase.equals("water")) {
                boolean grab = poses.entrySet().stream().anyMatch(e -> rendered.getOrDefault(e.getKey(), -1) == frame
                        && e.getValue()[0] > 1 && e.getValue()[1] > 0.9 && e.getValue()[2] > 0.9);
                if (!grab && ticks < 80) return;
                require(grab, "Current real frame shows synchronized swim and grab pose");
            }
            if (phase.equals("backlash") || phase.equals("restored")) {
                require(RedNailsItem.coolingCount(stack) == (PURSUIT ? 3 : 1), "Client receives actual torn-nail count");
                var predicate = ItemProperties.getProperty(stack.getItem(), new ResourceLocation(ModMain.MOD_ID, "fallen_nails"));
                require(predicate != null && predicate.call(stack, mc.level, mc.player, 0) == (PURSUIT ? 0.75F : 0.25F), "Item texture reflects actual torn nails");
                var lines = stack.getTooltipLines(mc.player, TooltipFlag.NORMAL).stream()
                        .map(Component::getString).toList();
                String tooltip = String.join("\n", lines);
                require(lines.stream().noneMatch(line -> line.contains("tooltip." + ModMain.MOD_ID)),
                        "Chinese tooltips resolve");
                require(lines.size() > 2 && lines.get(1).equals("\u73cd\u8d35\u56de\u5fc6"),
                        "precious-memory label immediately below item name");
                Files.writeString(RESULTS.resolve(phase + "-tooltip.txt"), tooltip);
            }
            for (String asset : new String[]{"textures/entity/water_ghost/water_ghost.png", "textures/entity/water_ghost/water_ghost_eyes.png", "textures/item/red_nails.png"})
                require(mc.getResourceManager().getResource(new ResourceLocation(ModMain.MOD_ID, asset)).isPresent(), "Runtime texture exists: " + asset);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                var colors = new HashSet<Integer>();
                for (int x = 0; x < image.getWidth(); x += 4) for (int y = 0; y < image.getHeight(); y += 4) colors.add(image.getPixelRGBA(x, y));
                require(colors.size() > 100, "Nonblank actual OpenGL framebuffer");
                image.writeToFile(RESULTS.resolve(phase + ".png"));
            }
            Files.writeString(RESULTS.resolve("client-" + phase + ".pass"), "Actual client/frame=" + frame + "; poses=" + poses.size() + "; item=" + stack + "\n");
            captured = true;
        } catch (Throwable error) { fail(error); }
    }
}
