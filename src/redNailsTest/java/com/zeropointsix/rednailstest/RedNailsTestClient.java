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
    private static final Path RESULTS = Path.of(System.getProperty("rednails.qa.results"));
    private static String phase = "";
    private static int ticks, frame;
    private static boolean failed, captured, shortAcked;
    private static ArmorStand camera;
    private static final HashMap<Integer, Integer> rendered = new HashMap<>();
    private static final HashMap<Integer, float[]> poses = new HashMap<>();
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
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
            rendered.clear(); poses.clear(); RedNailsClient.KEY.setDown(false); event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGH) public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
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
            if (phase.equals("input") || phase.equals("recall")) {
                GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.setScreen(null);
                mc.mouseHandler.grabMouse();
                if (!mc.isWindowActive()) {
                    ticks = 0;
                    RedNailsClient.KEY.setDown(false);
                    return;
                }
            }
            if (phase.equals("input")) {
                if (!shortAcked) {
                    if (ticks == 1) RedNailsClient.KEY.setDown(true);
                    if (ticks == 10) {
                        RedNailsClient.KEY.setDown(false);
                        Files.writeString(RESULTS.resolve("client-short-press.signal"),
                                "short hold released windowActive=true\n");
                    }
                    if (ticks >= 10 && Files.exists(RESULTS.resolve("server-short-press.ack"))) {
                        shortAcked = true;
                        ticks = 0;
                        RedNailsClient.KEY.setDown(false);
                    }
                    return;
                }
                if (ticks == 1 || ticks == 25 || ticks == 65 || ticks == 105 || ticks == 145) RedNailsClient.KEY.setDown(true);
                if (ticks == 10 || ticks == 55 || ticks == 95 || ticks == 135 || ticks == 175) RedNailsClient.KEY.setDown(false);
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
                || phase.equals("recalled") || phase.equals("water") || phase.equals("restored"))) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        try {
            var stack = RedNailsItem.findWorn(mc.player);
            require(!stack.isEmpty(), "Actual client sees functional Curios item");
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
                require(RedNailsItem.coolingCount(stack) == 1, "Client receives one torn nail");
                var predicate = ItemProperties.getProperty(stack.getItem(), new ResourceLocation(ModMain.MOD_ID, "fallen_nails"));
                require(predicate != null && predicate.call(stack, mc.level, mc.player, 0) == 0.25F, "Item switches to three-nail texture");
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
