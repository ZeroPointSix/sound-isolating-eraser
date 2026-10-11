package com.zeropointsix.neontest;

import com.zeropointsix.eraser.client.NeonTumorModel;
import com.zeropointsix.eraser.client.NeonTumorRenderer;
import com.zeropointsix.eraser.neon.NeonTumorEntity;
import com.zeropointsix.eraser.registry.ModEffects;
import com.zeropointsix.eraser.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HashMap;
import com.mojang.blaze3d.platform.NativeImage;
import software.bernie.geckolib.event.GeoRenderEvent;

@Mod.EventBusSubscriber(modid = "neon_qa", value = Dist.CLIENT)
public final class NeonTestClient {
    private static final Path RESULTS = Path.of(System.getProperty("neon.qa.results"));
    private static String phase = "";
    private static int ticks;
    private static boolean failed, firstFrame, captured;
    private static NativeImage previousFrame;
    private static final HashMap<Integer, float[]> breathing = new HashMap<>();

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("NEON_QA:")) {
            phase = text.substring(8);
            ticks = 0;
            firstFrame = false;
            captured = false;
            breathing.clear();
            if (phase.equals("escape")) {
                // Keep lifecycle probes responsive on the software-rendered CI client.
                var mc = Minecraft.getInstance();
                mc.options.renderDistance().set(2);
                mc.options.particles().set(net.minecraft.client.ParticleStatus.MINIMAL);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 640, 360);
            }
            event.setCanceled(true);
        }
    }

    private static void fail(Throwable failure) {
        failure.printStackTrace();
        failed = true;
        if (previousFrame != null) previousFrame.close();
        previousFrame = null;
        Minecraft.getInstance().options.keyShift.setDown(false);
        try { Files.writeString(RESULTS.resolve("client.failed"), failure.toString()); } catch (Exception ignored) { }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || phase.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DisconnectedScreen) {
            fail(new AssertionError("Unexpected disconnect during " + phase + ": "
                    + mc.screen.getNarrationMessage().getString()));
            return;
        }
        if (mc.player == null || mc.level == null) return;
        try {
            ticks++;
            if (ticks == 1) { mc.setScreen(null); mc.mouseHandler.grabMouse(); }
            if (phase.equals("escape")) {
                if (ticks == 5) mc.options.keyShift.setDown(true);
                if (ticks == 12) {
                    require(mc.player.getVehicle() instanceof NeonTumorEntity, "real sneak input cannot dismount");
                    mc.options.keyShift.setDown(false);
                    mc.gameRenderer.pick(1);
                    require(mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == mc.player.getVehicle(),
                            "real first-person ray pick can hit own captor");
                    mc.gameMode.attack(mc.player, ((EntityHitResult) mc.hitResult).getEntity());
                    mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    Files.writeString(RESULTS.resolve("client-escape.pass"), "Sneak and actual camera ray pick/attack packet passed.\n");
                }
            } else if ((phase.equals("released-death") || phase.equals("released-timeout")) && ticks == 10) {
                require(!mc.player.isPassenger(), "client accepts authoritative " + phase + " dismount");
                Files.writeString(RESULTS.resolve("client-" + phase + ".pass"), "Real client authoritative dismount passed.\n");
            } else if (phase.equals("dimension") && ticks >= 20) {
                if (mc.level.dimension().equals(net.minecraft.world.level.Level.NETHER)) {
                    require(!mc.player.isPassenger(), "real client dimension transfer releases captor");
                    Files.writeString(RESULTS.resolve("client-dimension.pass"), "Real client reached Nether without mount.\n");
                    phase = "";
                }
            } else if (phase.equals("logout")) {
                require(ticks < 60, "logout phase must synchronize a real captor before disconnect");
                if (mc.player.getVehicle() instanceof NeonTumorEntity) {
                    mc.level.disconnect();
                    mc.clearLevel();
                    mc.setScreen(new TitleScreen());
                    phase = "";
                    Files.writeString(RESULTS.resolve("client-logout.pass"), "Disconnected a real client while swallowed.\n");
                }
            } else if (phase.equals("rejoined") && ticks == 30) {
                require(!mc.player.isPassenger(), "rejoined client is not mounted");
                Files.writeString(RESULTS.resolve("client-rejoined.pass"), "Real client rejoined outside captor.\n");
            }
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent public static void modelRendered(GeoRenderEvent.Entity.Post event) {
        if (failed || !phase.equals("gallery") || ticks < 80 || !(event.getEntity() instanceof NeonTumorEntity tumor)) return;
        try {
            float scale = event.getModel().getBone("bubble_01").orElseThrow().getScaleX();
            float[] range = breathing.computeIfAbsent(tumor.getId(), id -> new float[]{scale, scale, 0});
            range[0] = Math.min(range[0], scale);
            range[1] = Math.max(range[1], scale);
            range[2]++;
            for (String bone : new String[]{"pod_1", "pod_2", "pod_3"}) {
                require(event.getModel().getBone(bone).orElseThrow().isHidden(), "idle tendrils hidden: " + bone);
            }
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !failed && phase.equals("baseline") && ticks >= 100 && !captured) {
            var mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.screen != null) return;
            try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                var pos = mc.player.blockPosition();
                int brightness = mc.level.getMaxLocalRawBrightness(pos);
                if (brightness < 11) {
                    Files.writeString(RESULTS.resolve("client-lighting.txt"), "ticks=" + ticks + ", raw=" + brightness
                            + ", sky=" + mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos));
                    require(ticks < 400, "sunlit control scene must receive bright client light data");
                    return;
                }
                screenshot.writeToFile(RESULTS.resolve("baseline.png"));
                Files.writeString(RESULTS.resolve("client-baseline.pass"), "sky="
                        + mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos)
                        + ", block=" + mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos)
                        + ", day=" + mc.level.getDayTime() + ", raw=" + mc.level.getMaxLocalRawBrightness(pos));
                captured = true;
            } catch (Throwable failure) { fail(failure); }
            return;
        }
        if (event.phase != TickEvent.Phase.END || failed || !phase.equals("gallery") || ticks < 140 || captured) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || firstFrame && ticks < 193) return;
        try {
            int count = 0;
            NeonTumorModel model = new NeonTumorModel();
            for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof NeonTumorEntity tumor)) continue;
                count++;
                require(mc.getEntityRenderDispatcher().getRenderer(tumor) instanceof NeonTumorRenderer, "GeckoLib renderer registered");
                mc.getResourceManager().getResourceOrThrow(model.getTextureResource(tumor));
                mc.getResourceManager().getResourceOrThrow(model.getGlowTexture(tumor));
            }
            require(count == 9, "all nine size/color combinations synchronized; got " + count);
            var egg = (ForgeSpawnEggItem) ModItems.NEON_TUMOR_SPAWN_EGG.get();
            require(egg.getColor(0) == 0xFFFFFF && egg.getColor(1) == 0xFFFFFF, "art spawn egg is not tinted");
            mc.getResourceManager().getResourceOrThrow(new ResourceLocation("sound_isolating_eraser", "textures/mob_effect/corroded.png"));
            require(ModEffects.CORRODED.get().getDisplayName().getString().equals("腐蚀"), "Chinese effect name loaded");
            try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                var colors = new HashSet<Integer>();
                for (int x = 0; x < screenshot.getWidth(); x += 5) for (int y = 0; y < screenshot.getHeight(); y += 5) colors.add(screenshot.getPixelRGBA(x, y));
                require(colors.size() > 150, "actual client framebuffer is nonblank");
                screenshot.writeToFile(RESULTS.resolve(firstFrame ? "gallery-animated.png" : "gallery.png"));
                if (firstFrame) {
                    StringBuilder poses = new StringBuilder();
                    breathing.forEach((id, range) -> poses.append(id).append(": min=").append(range[0])
                            .append(", max=").append(range[1]).append(", frames=").append(range[2]).append('\n'));
                    Files.writeString(RESULTS.resolve("rendered-breathing.txt"), poses);
                    require(breathing.size() == 9, "all nine entities actually rendered: " + breathing.size());
                    for (float[] range : breathing.values()) {
                        require(range[2] > 5 && range[1] - range[0] > 0.02,
                                "actual rendered breathing cycle: min=" + range[0] + ", max=" + range[1] + ", frames=" + range[2]);
                    }
                    int changed = 0;
                    for (int x = 0; x < screenshot.getWidth(); x++) for (int y = 0; y < screenshot.getHeight(); y++) {
                        if (screenshot.getPixelRGBA(x, y) != previousFrame.getPixelRGBA(x, y)) changed++;
                    }
                    require(changed > 100, "rendered animation changes framebuffer pixels");
                    previousFrame.close();
                    previousFrame = null;
                } else {
                    previousFrame = Screenshot.takeScreenshot(mc.getMainRenderTarget());
                }
            }
            if (!firstFrame) firstFrame = true;
            else {
                captured = true;
                Files.writeString(RESULTS.resolve("client-gallery.pass"), "Nine synchronized combinations, GeckoLib renderer, textures, glow masks, untinted egg, Chinese effect and nonblank frames passed.\n");
            }
        } catch (Throwable failure) { fail(failure); }
    }
}
