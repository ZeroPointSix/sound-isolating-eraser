package com.zeropointsix.gravitytest;

import com.mojang.blaze3d.platform.InputConstants;
import com.zeropointsix.eraser.block.EraserAnchorBlock;
import com.zeropointsix.eraser.block.EraserWallBlock;
import com.zeropointsix.eraser.client.EraserClient;
import com.zeropointsix.eraser.eraser.EraserMode;
import com.zeropointsix.eraser.eraser.EraserPlacement;
import com.zeropointsix.eraser.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "gravity_qa", value = Dist.CLIENT)
public final class RoundTwoTestClient {
    private static final Path RESULTS = Path.of(System.getProperty("gravity.qa.results"));
    private static final boolean WEARER = "wearer".equals(System.getProperty("gravity.qa.role"));
    private static boolean ready;
    private static int ticks;
    private static int mode;
    private static int observerMode = -1;
    private static int observerDelay;
    private static int observedModes;
    private static List<BlockPos> footprint;
    private static long renderedFrames;
    private static long inputReadyFrame;

    @SubscribeEvent
    public static void rendered(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) renderedFrames++;
    }

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.equals("ROUND2_ERASER_READY")) { ready = true; event.setCanceled(true); }
        if (text.startsWith("ROUND2_ERASER_CHECK:")) {
            observerMode = Integer.parseInt(text.substring("ROUND2_ERASER_CHECK:".length()));
            observerDelay = 0;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ready) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            if (!WEARER) {
                if (observerMode >= 0 && ++observerDelay == 10) {
                    int anchors = 0;
                    for (var pos : BlockPos.betweenClosed(38,65,-6,50,65,8))
                        if (mc.level.getBlockState(pos).getBlock() instanceof EraserAnchorBlock) anchors++;
                    check(anchors == (observerMode == 0 ? 1 : observerMode == 4 ? 12 : 5), "observer receives shape " + observerMode);
                    observedModes |= 1 << observerMode;
                    Files.writeString(RESULTS.resolve("observer-eraser-mode-" + observerMode + ".pass"), "passed\n");
                    if (observedModes == 31) Files.writeString(RESULTS.resolve("observer-eraser.pass"), "all five drawings synchronized\n");
                }
                return;
            }
            if (renderedFrames < inputReadyFrame) return;
            ticks++;
            if (mode == 5) {
                verifyAtomic(mc);
                return;
            }
            if (ticks == 1) {
                mc.setScreen(null);
                GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.mouseHandler.grabMouse();
            }
            if (ticks == 10) {
                check(mc.player.getMainHandItem().is(ModItems.SOUND_ISOLATING_ERASER.get()), "held eraser synchronized");
                check(EraserMode.read(mc.player.getMainHandItem()).ordinal() == mode, "native key selects mode " + mode);
                if (mode == 0) check(EraserClient.CYCLE.getKey().getValue() == GLFW.GLFW_KEY_R, "default mode key is R");
                check(mc.hitResult instanceof BlockHitResult, "surface selected");
                var hit = (BlockHitResult) mc.hitResult;
                footprint = EraserMode.values()[mode].bases(hit.getBlockPos().relative(hit.getDirection()), mc.player.getDirection());
                check(EraserPlacement.fits(mc.level, mc.player, mc.player.getMainHandItem(), footprint, hit.getDirection()), "valid preview footprint");
                capture(mc, "preview-" + mode);
            }
            if (ticks == 20) input("click", "3");
            if (ticks == 45) {
                for (var base : footprint) for (int y = 0; y < 5; y++)
                    check(EraserWallBlock.isEraserWall(mc.level.getBlockState(base.above(y))), "native click places full height");
                mc.player.connection.sendCommand("round2qa verify " + mode);
                capture(mc, "placed-" + mode);
            }
            if (ticks == 80) {
                if (!Files.exists(RESULTS.resolve("observer-eraser-mode-" + mode + ".pass"))) { ticks--; return; }
                mc.player.connection.sendCommand("round2qa clear");
            }
            if (ticks == 100) {
                if (mode == 3) {
                    EraserClient.CYCLE.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Y));
                    KeyMapping.resetMapping();
                }
                input("key", mode >= 3 ? "y" : "r");
            }
            if (ticks == 120) {
                if (++mode < 5) ticks = 0;
                else {
                    check(EraserMode.read(mc.player.getMainHandItem()) == EraserMode.POINT, "rebound Y cycles ring back to point");
                    mc.player.connection.sendCommand("round2qa verify 5");
                    mc.player.connection.sendCommand("round2qa obstruct");
                    ticks = 0;
                }
            }
        } catch (Throwable failure) {
            ready = false;
            failure.printStackTrace();
            try { Files.writeString(RESULTS.resolve((WEARER ? "wearer" : "observer") + "-round2.failed"), failure.toString()); }
            catch (Exception ignored) { }
        }
    }

    private static void verifyAtomic(Minecraft mc) throws Exception {
        if (ticks == 10) {
            boolean blocked = footprint.stream().anyMatch(base -> mc.level.getBlockState(base.above(4)).is(net.minecraft.world.level.block.Blocks.STONE));
            if (!blocked || EraserMode.read(mc.player.getMainHandItem()) != EraserMode.RING) { ticks--; return; }
            var hit = (BlockHitResult) mc.hitResult;
            var selected = EraserMode.RING.bases(hit.getBlockPos().relative(hit.getDirection()), mc.player.getDirection());
            check(new HashSet<>(selected).equals(new HashSet<>(footprint)), "invalid native click still targets the obstructed ring");
            check(!EraserPlacement.fits(mc.level, mc.player, mc.player.getMainHandItem(), footprint, hit.getDirection()), "one obstructed ring cell makes preview invalid");
            capture(mc, "invalid-preview");
        }
        if (ticks == 20) input("click", "3");
        if (ticks == 50) {
            for (var base : footprint) for (int y = 0; y < 5; y++)
                check(!EraserWallBlock.isEraserWall(mc.level.getBlockState(base.above(y))), "native invalid drawing leaves no partial wall");
            capture(mc, "invalid-result");
            mc.player.connection.sendCommand("round2qa atomic");
            Files.writeString(RESULTS.resolve("wearer-eraser.pass"), "R/Y native keys, five previews/placements, invalid whole-ring rejection passed\n");
            ready = false;
        }
    }

    private static void input(String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "xdotool";
        System.arraycopy(args, 0, command, 1, args.length);
        check(new ProcessBuilder(command).inheritIO().start().waitFor() == 0, "native input accepted");
        inputReadyFrame = renderedFrames + 2;
    }
    private static void capture(Minecraft mc, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(RESULTS.resolve("eraser-" + name + ".png"));
        }
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        System.out.println("ROUND2_E2E_ASSERT client: " + message);
    }
}
