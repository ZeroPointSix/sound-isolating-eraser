package com.zeropointsix.gravitytest;

import com.mojang.blaze3d.platform.NativeImage;
import com.zeropointsix.eraser.client.WingsClientData;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Client driver for the wind-thunder wings two-client E2E.
 * Inert unless -Dwings.qa.role is wingswearer / wingsobserver.
 */
@Mod.EventBusSubscriber(modid = "gravity_qa", value = Dist.CLIENT)
public final class WingsQaClient {
    private static final String ROLE = System.getProperty("wings.qa.role");
    private static final Path RESULTS = ROLE == null ? null
            : Path.of(System.getProperty("wings.qa.results", "build/wings-e2e"));
    private static String phase = "";
    private static int stage;
    private static int ticks;
    private static int totalTicks;
    private static int wearerDoneAt = -1;
    private static boolean finished;
    private static int foodAtDeploy = -1;
    private static double markX = Double.NaN;
    private static double markZ = Double.NaN;
    private static NativeImage stowedFrame;

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent event) {
        if (ROLE == null) return;
        String message = event.getMessage().getString();
        if (message.startsWith("WINGS_QA_PHASE:")) {
            phase = message.substring("WINGS_QA_PHASE:".length());
            if (phase.equals("wearerDone")) wearerDoneAt = ticks;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (ROLE == null || event.phase != TickEvent.Phase.END || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++totalTicks > 9000) throw new AssertionError("Timed out in stage " + stage);
            if (mc.player == null || mc.level == null) return;
            if (stage == 0) {
                if (!phase.equals("ready")) return;
                mc.setScreen(null);
                GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.mouseHandler.grabMouse();
                stage = ROLE.equals("wingswearer") ? 1 : 50;
                ticks = 0;
                return;
            }
            ticks++;
            if (stage == 1) exerciseWearer(mc);
            if (stage == 50) exerciseObserver(mc);
            if (stage == 99 && Files.exists(RESULTS.resolve("wings-observer.pass"))) {
                mc.player.connection.sendCommand("wingsqa finish");
                mark("wings-wearer.pass");
                finished = true;
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                capture(mc, "failed");
                Files.writeString(RESULTS.resolve("wings-" + ROLE + ".failed"), failure.toString());
            } catch (Exception ignored) { }
            finished = true;
        }
    }

    private static void exerciseWearer(Minecraft mc) throws Exception {
        Player p = mc.player;
        switch (ticks) {
            case 1 -> {
                require(p.getItemBySlot(EquipmentSlot.CHEST)
                                .is(com.zeropointsix.eraser.registry.ModItems.WIND_THUNDER_WINGS.get()),
                        "wings equipped in chest slot");
                require(!p.onGround(), "wearer starts airborne");
                stowedFrame = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            }
            case 70 -> {
                WingsClientData.WingInfo w = info(p);
                require(w != null && w.deployed(), "double-space deploys wings (server sync)");
                require(p.isNoGravity(), "deployed wearer is gravity-free");
                require(hudDrawn(mc), "wings HUD plate draws bottom-right (pixel diff vs stowed)");
                capture(mc, "deployed");
                foodAtDeploy = p.getFoodData().getFoodLevel();
            }
            case 95 -> require(info(p) != null && info(p).tier() == 1,
                    "C cycles tier hover→cruise (synced)");
            case 200 -> {
                require(p.getFoodData().getFoodLevel() < foodAtDeploy,
                        "flight drains real hunger (food " + foodAtDeploy + "→"
                                + p.getFoodData().getFoodLevel() + ")");
                command(mc, "hungerDone");
            }
            case 205 -> { mark(p); nativeInput("key", "r"); }
            case 225 -> require(moved(p) > 10,
                    "R blinks forward ~24 blocks (real teleport, client-authoritative)"
                            + " moved=" + moved(p));
            case 250 -> {
                mark(p);
                // four rapid presses inside one chain window: exactly 3 fire, 4th locks out
                nativeInput("key", "--delay", "80", "r", "r", "r", "r");
            }
            case 276 -> {
                require(moved(p) > 40, "chain window fires 3 more blinks (~72 blocks)");
                mark(p);
            }
            case 282 -> nativeInput("key", "r");
            case 296 -> require(moved(p) < 2.0, "4th chained blink is locked out");
            case 298 -> command(mc, "zap");
            case 320 -> {
                require(p.getFoodData().getFoodLevel() == 20,
                        "lightning strike refills hunger");
                require(info(p) != null && info(p).chargedUntil() > mc.level.getGameTime(),
                        "strike grants charged window (synced to HUD orb)");
            }
            case 330 -> {
                capture(mc, "charged-hud");
                command(mc, "wearerDone");
            }
            case 340 -> stage = 99;
            default -> {
                // xdotool 原生输入偶发丢失：重发按键，成功即止。重试间隔 ≥20t，保证
                // 上一轮 sync 已回——间隔过近的双击会读到过期状态把展开切换成收起。
                if ((ticks == 8 || ticks == 30 || ticks == 50)
                        && (info(p) == null || !info(p).deployed())) {
                    nativeInput("key", "--delay", "150", "space", "space");
                } else if (ticks >= 75 && ticks <= 85 && ticks % 5 == 0
                        && info(p) != null && info(p).tier() == 0) {
                    nativeInput("key", "c");
                } else if (ticks > 205 && ticks < 225 && ticks % 6 == 1 && moved(p) < 5) {
                    nativeInput("key", "r");
                } else if (ticks == 262 && moved(p) < 30) {
                    // 整串重发：上一串若部分丢失，链窗已过期可重新开始
                    nativeInput("key", "--delay", "80", "r", "r", "r", "r");
                }
            }
        }
    }

    private static void exerciseObserver(Minecraft mc) throws Exception {
        Player remote = null;
        for (Player other : mc.level.players()) {
            if (other.getName().getString().equals(WingsQaServer.WEARER)) remote = other;
        }
        if (remote == null) return;
        // Multiplayer wing-state sync: remote deployed/tier arrive via SyncWingsPacket.
        WingsClientData.WingInfo w = WingsClientData.get(remote);
        if (w != null && w.deployed() && wearerDoneAt > 0 && ticks - wearerDoneAt > 8) {
            capture(mc, "wearer-wings");
            mark("wings-observer.pass");
            finished = true;
        }
    }

    private static void mark(Player p) {
        markX = p.getX();
        markZ = p.getZ();
    }

    private static double moved(Player p) {
        return Math.hypot(p.getX() - markX, p.getZ() - markZ);
    }

    private static boolean hudDrawn(Minecraft mc) {
        // wings_hud backplate renders at gui (w-72, h-30), 64x16 gui px; diff vs the stowed frame.
        double scale = mc.getWindow().getGuiScale();
        int x0 = (int) ((mc.getWindow().getGuiScaledWidth() - 72) * scale);
        int y0 = (int) ((mc.getWindow().getGuiScaledHeight() - 30) * scale);
        try (NativeImage now = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int changed = 0, checked = 0;
            for (int dy = 0; dy < 32 && y0 + dy < now.getHeight(); dy++) {
                for (int dx = 0; dx < 128 && x0 + dx < now.getWidth(); dx++) {
                    int a = stowedFrame.getPixelRGBA(x0 + dx, y0 + dy);
                    int b = now.getPixelRGBA(x0 + dx, y0 + dy);
                    int delta = Math.max(Math.abs((a & 255) - (b & 255)),
                            Math.max(Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)),
                                    Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))));
                    checked++;
                    if (delta >= 24) changed++;
                }
            }
            stowedFrame.close();
            stowedFrame = null;
            System.out.println("WINGS_HUD_PIXELS changed=" + changed + "/" + checked);
            return checked > 0 && changed > 120;
        }
    }

    private static WingsClientData.WingInfo info(Player p) {
        return WingsClientData.get(p);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("WINGS_E2E_ASSERT " + ROLE + ": " + message);
    }

    private static void nativeInput(String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "xdotool";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).inheritIO().start();
        if (process.waitFor() != 0) throw new AssertionError("xdotool failed");
    }

    private static void command(Minecraft mc, String word) {
        mc.player.connection.sendCommand("wingsqa phase " + word);
    }

    private static void mark(String name) throws Exception {
        Files.writeString(RESULTS.resolve(name), "passed\n");
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(RESULTS.resolve(ROLE + "-" + name + ".png"));
        }
    }

    private WingsQaClient() { }
}
