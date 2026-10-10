package com.zeropointsix.neontest;

import com.zeropointsix.eraser.client.NeonTumorModel;
import com.zeropointsix.eraser.client.NeonTumorRenderer;
import com.zeropointsix.eraser.neon.NeonTumorEntity;
import com.zeropointsix.eraser.registry.ModEffects;
import com.zeropointsix.eraser.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
import com.mojang.blaze3d.platform.NativeImage;

@Mod.EventBusSubscriber(modid = "neon_qa", value = Dist.CLIENT)
public final class NeonTestClient {
    private static final Path RESULTS = Path.of(System.getProperty("neon.qa.results"));
    private static String phase = "";
    private static int ticks;
    private static boolean failed, firstFrame, captured;
    private static NativeImage previousFrame;
    private static float previousBubbleScale;

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("NEON_QA:")) {
            phase = text.substring(8);
            ticks = 0;
            firstFrame = false;
            captured = false;
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
                }
            } else if (phase.equals("logout") && ticks == 12) {
                require(mc.player.getVehicle() instanceof NeonTumorEntity, "disconnect occurs while inside captor");
                mc.level.disconnect();
                mc.clearLevel();
                mc.setScreen(new TitleScreen());
                phase = "";
                Files.writeString(RESULTS.resolve("client-logout.pass"), "Disconnected a real client while swallowed.\n");
            } else if (phase.equals("rejoined") && ticks == 30) {
                require(!mc.player.isPassenger(), "rejoined client is not mounted");
                Files.writeString(RESULTS.resolve("client-rejoined.pass"), "Real client rejoined outside captor.\n");
            }
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || !phase.equals("gallery") || ticks < 45 || captured) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || firstFrame && ticks < 65) return;
        try {
            int count = 0;
            NeonTumorModel model = new NeonTumorModel();
            NeonTumorRenderer rendered = null;
            for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof NeonTumorEntity tumor)) continue;
                count++;
                require(mc.getEntityRenderDispatcher().getRenderer(tumor) instanceof NeonTumorRenderer, "GeckoLib renderer registered");
                rendered = (NeonTumorRenderer) mc.getEntityRenderDispatcher().getRenderer(tumor);
                mc.getResourceManager().getResourceOrThrow(model.getTextureResource(tumor));
                mc.getResourceManager().getResourceOrThrow(model.getGlowTexture(tumor));
            }
            require(count == 9, "all nine size/color combinations synchronized; got " + count);
            float bubbleScale = rendered.getGeoModel().getBone("bubble_01").orElseThrow().getScaleX();
            for (String bone : new String[]{"pod_1", "pod_2", "pod_3"}) {
                require(rendered.getGeoModel().getBone(bone).orElseThrow().isHidden(), "idle tendrils hidden: " + bone);
            }
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
                    require(Math.abs(bubbleScale - previousBubbleScale) > 0.002, "actual rendered breathing bone changed scale");
                    int changed = 0;
                    for (int x = 0; x < screenshot.getWidth(); x++) for (int y = 0; y < screenshot.getHeight(); y++) {
                        if (screenshot.getPixelRGBA(x, y) != previousFrame.getPixelRGBA(x, y)) changed++;
                    }
                    require(changed > 100, "rendered animation changes framebuffer pixels");
                    previousFrame.close();
                    previousFrame = null;
                } else {
                    previousBubbleScale = bubbleScale;
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
