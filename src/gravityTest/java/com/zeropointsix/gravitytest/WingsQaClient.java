package com.zeropointsix.gravitytest;

import com.mojang.blaze3d.platform.NativeImage;
import com.zeropointsix.eraser.client.WingsClientData;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Client driver for the wind-thunder wings two-client E2E.
 * Inert unless -Dwings.qa.role is wingswearer / wingsobserver.
 */
@Mod.EventBusSubscriber(modid = "gravity_qa", value = Dist.CLIENT)
public final class WingsQaClient {
    private static final String ROLE = System.getProperty("wings.qa.role");
    private static final boolean SHOWCASE = "showcase".equals(System.getProperty("wings.qa.mode"));
    private static final Path RESULTS = ROLE == null ? null
            : Path.of(System.getProperty("wings.qa.results", "build/wings-e2e"));
    private static String phase = "";
    private static int stage;
    private static int ticks;
    private static int totalTicks;
    private static int wearerDoneAt = -1;
    private static int itemDropAt = -1;
    private static boolean wearerVisualOk;
    private static boolean remoteFxOk;
    private static boolean finished;
    private static int foodAtDeploy = -1;
    private static double markX = Double.NaN;
    private static double markZ = Double.NaN;
    private static NativeImage stowedFrame;
    private static int deployAnimFrame = -1;
    private static String reviewPhase = "";
    private static int reviewPhaseTicks;

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent event) {
        if (ROLE == null) return;
        String message = event.getMessage().getString();
        if (message.startsWith("WINGS_QA_PHASE:")) {
            phase = message.substring("WINGS_QA_PHASE:".length());
            if (phase.equals("wearerDone")) wearerDoneAt = ticks;
            if (phase.equals("itemDrop")) itemDropAt = ticks;
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
                if (SHOWCASE && !Files.exists(RESULTS.resolve("recording.ready"))) return;
                mc.setScreen(null);
                GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.mouseHandler.grabMouse();
                stage = ROLE.equals("wingswearer") ? 1 : 50;
                ticks = 0;
                return;
            }
            ticks++;
            if (stage == 1) {
                if (SHOWCASE) showcaseWearer(mc); else exerciseWearer(mc);
            }
            if (stage == 50) {
                if (SHOWCASE) showcaseObserver(mc); else exerciseObserver(mc);
            }
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
            case 297 -> require(info(p) != null
                            && info(p).blinkLockUntil() > mc.level.getGameTime(),
                    "160t chain lock is synced to HUD data (blinkLockUntil)");
            case 298 -> command(mc, "zap");
            case 320 -> {
                require(p.getFoodData().getFoodLevel() == 20,
                        "lightning strike refills hunger");
                require(info(p) != null && info(p).chargedUntil() > mc.level.getGameTime(),
                        "strike grants charged window (synced to HUD orb)");
            }
            case 322 -> command(mc, "handitem");
            case 326 -> {
                capture(mc, "handheld");
                require(handItemDrawn(mc), "held wings render as 3D item in first person");
            }
            case 328 -> nativeInput("key", "e");
            case 332 -> {
                capture(mc, "inventory");
                nativeInput("key", "Escape");
            }
            case 333 -> command(mc, "wearerDone"); // observer flank-tracking window opens
            // Capture at wearerDone+15 (~t348) must stay in clear air. Storm FX and
            // flood both paint a cyan blob that used to be the whole screenshot.
            case 360 -> command(mc, "storm");
            case 361 -> nativeInput("keydown", "w");
            case 367 -> capture(mc, "storm-flight");
            case 371 -> nativeInput("keyup", "w");
            case 390 -> command(mc, "flood"); // after observer since>35 timeout (~t368)
            case 398 -> require(p.isInWater()
                            && info(p) != null && info(p).deployed(),
                    "entering water while deployed keeps wings open (Notion §4.6)");
            case 400 -> command(mc, "itemDrop");
            case 410 -> stage = 99;
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
                // 收展连续帧：展开动画过渡中逐 tick 截图（deployAnim 0→1 ≈ 6 tick）
                WingsClientData.WingInfo w = info(p);
                if (w != null && w.deployed() && w.deployAnim() > 0.01f
                        && w.deployAnim() < 0.99f && deployAnimFrame < 3) {
                    deployAnimFrame++;
                    capture(mc, "deploy-frame-" + deployAnimFrame);
                }
            }
        }
    }

    /**
     * 录屏编排（showcase 模式）：一场约 110s 的脚本化飞行表演，覆盖
     * F5 背视展开→悬停大扇→御风滑翔→疾风→神霄→雷击充能→三连雷遁→
     * 收翼落地→正面视角→手持→物品栏→看掉落物。与 exerciseWearer 同构，
     * 但目标是把镜头画面拍好看而不是断言。
     */
    private static void showcaseWearer(Minecraft mc) throws Exception {
        Player p = mc.player;
        switch (ticks) {
            case 1 -> {
                require(p.getItemBySlot(EquipmentSlot.CHEST)
                                .is(com.zeropointsix.eraser.registry.ModItems.WIND_THUNDER_WINGS.get()),
                        "showcase: wings equipped");
                nativeInput("key", "F5"); // 第三人称背视：翼模型正对镜头
            }
            // Showcase staging uses the real server action; native keybinds remain tested by E2E.
            case 30 -> command(mc, "showcaseDeploy");
            case 100 -> {
                require(info(p) != null && info(p).deployed(), "showcase wings actually deployed");
                command(mc, "reviewBack");
            }
            case 140 -> command(mc, "reviewSide");
            case 180 -> command(mc, "reviewTop");
            case 220 -> command(mc, "reviewBack");
            case 240 -> command(mc, "showcaseCruise");
            case 265 -> require(info(p) != null && info(p).tier() == 1, "showcase cruise synced");
            case 245 -> nativeInput("keydown", "w");
            case 505 -> nativeInput("keyup", "w");          // 13s 滑翔
            case 540 -> command(mc, "showcaseBoost");
            case 565 -> require(info(p) != null && info(p).tier() == 2, "showcase boost synced");
            case 545 -> nativeInput("keydown", "w");
            case 620 -> command(mc, "feed");               // 喂饱防中途迫降
            case 705 -> nativeInput("keyup", "w");          // 8s 冲刺
            case 740 -> command(mc, "showcaseStorm");
            case 765 -> require(info(p) != null && info(p).tier() == 3, "showcase storm synced");
            case 745 -> nativeInput("keydown", "w");
            case 865 -> nativeInput("keyup", "w");          // 6s 极速
            case 900 -> command(mc, "zap");                // 雷击充能：特效 + 回饥饿
            case 940 -> nativeInput("key", "--delay", "80", "r", "r", "r"); // 三连雷遁
            case 1020 -> command(mc, "above");             // 传送到掉落点上空俯视
            case 1040 -> command(mc, "showcaseStow");
            case 1070 -> require(info(p) != null && !info(p).deployed(), "showcase wings actually stowed");
            case 1300 -> command(mc, "feed");
            case 1100 -> nativeInput("key", "F5");         // 正面视角
            case 1130 -> capture(mc, "showcase-folded");
            case 1160 -> {
                nativeInput("key", "F5");                  // 第一人称
                command(mc, "handitem");                   // 手持风雷翅
            }
            case 1220 -> nativeInput("key", "e");          // 物品栏：背甲槽 + 模型
            case 1200 -> capture(mc, "showcase-handheld");
            case 1280 -> capture(mc, "showcase-inventory");
            case 1340 -> nativeInput("key", "Escape");
            case 1380 -> command(mc, "itemDrop");          // 掉落物落在面前 9 格
            case 1460 -> command(mc, "wearerDone");        // 观察者贴脸看背甲收翼
            case 1500 -> command(mc, "feed");
            case 1560 -> {
                mc.player.connection.sendCommand("wingsqa finish");
                mark("wings-wearer.pass");
                finished = true;
            }
            default -> { }
        }
    }

    /** showcase 观察者：纯镜头架（服务端每 tick 传送跟拍），等佩戴者演完。 */
    private static void showcaseObserver(Minecraft mc) throws Exception {
        if (!phase.equals(reviewPhase)) {
            reviewPhase = phase;
            reviewPhaseTicks = 0;
        }
        if (++reviewPhaseTicks == 20 && phase.startsWith("review")) {
            Player remote = mc.level.players().stream()
                    .filter(p -> p.getName().getString().equals(WingsQaServer.WEARER))
                    .findFirst().orElse(null);
            WingsClientData.WingInfo w = remote == null ? null : info(remote);
            require(w != null && w.deployed() && w.deployAnim() >= 0.99f,
                    "reference capture requires visible deployed remote state: " + phase);
            capture(mc, phase);
        }
        if (phase.equals("itemDrop") && reviewPhaseTicks == 20) capture(mc, "showcase-ground-item");
        if (Files.exists(RESULTS.resolve("wings-wearer.pass"))) {
            mark("wings-observer.pass");
            finished = true;
        }
    }

    private static void exerciseObserver(Minecraft mc) throws Exception {
        Player remote = null;
        for (Player other : mc.level.players()) {
            if (other.getName().getString().equals(WingsQaServer.WEARER)) remote = other;
        }
        // Multiplayer wing-state sync: remote deployed/tier arrive via SyncWingsPacket.
        if (remote != null && wearerDoneAt > 0) {
            WingsClientData.WingInfo w = WingsClientData.get(remote);
            long since = ticks - wearerDoneAt;
            if (since >= 15 && !wearerVisualOk) {
                // 状态同步和画面证据分开：投影进视锥 → ROI 非天空像素 → 才记为视觉证据。
                // 客户端插值/跟拍可能要几 tick 才到位，窗口内重试；超时才判失败
                double[] proj = projectToScreen(mc, remote);
                if (proj != null) {
                    int contrast = roiContrast(mc, proj[0], proj[1], 14);
                    if (contrast > 30) {
                        capture(mc, "wearer-wings");
                        System.out.println("WINGS_E2E_ASSERT wingsobserver: "
                                + "wearer silhouette verified in frustum at "
                                + (int) proj[0] + "," + (int) proj[1]
                                + " contrast=" + contrast);
                        wearerVisualOk = true;
                    }
                }
                if (since > 35 && !wearerVisualOk) {
                    capture(mc, "wearer-wings-failed");
                    throw new AssertionError("wearer not verifiably visible on observer screen"
                            + " proj=" + java.util.Arrays.toString(proj)
                            + " cam=" + mc.gameRenderer.getMainCamera().getPosition()
                            + " wearer=" + remote.position());
                }
            }
            if (since > 25 && since <= 50 && !remoteFxOk) {
                // 远端飞行特效：STORM 飞行中观察者客户端应能看到持续粒子生成
                String counts = mc.particleEngine.countParticles();
                System.out.println("WINGS_OBSERVER_PARTICLES " + counts);
                if (w != null && w.tier() == 3 && counts.matches(".*[1-9].*")) {
                    remoteFxOk = true;
                    capture(mc, "wearer-storm-fx");
                    System.out.println("WINGS_E2E_ASSERT wingsobserver: "
                            + "remote wearer storm flight emits visible particles");
                }
            }
        }
        if (itemDropAt > 0 && ticks - itemDropAt == 20) {
            // 掉落物：服务端把观察者传送到正对掉落翅膀的位置，断言画面中心 ROI
            // 出现非天空对比度（0.55 格收翼掉落物），才记录截图证据
            double fbW = mc.getMainRenderTarget().width;
            double fbH = mc.getMainRenderTarget().height;
            capture(mc, "ground-item");
            require(roiContrast(mc, fbW / 2, fbH / 2, 40) > 25,
                    "dropped wings item renders at screen center (ROI contrast)");
            mark("wings-observer.pass");
            finished = true;
        }
    }

    /** Project a world position to framebuffer pixels; null when behind/outside view. */
    private static double[] projectToScreen(Minecraft mc, net.minecraft.world.entity.Entity e) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 rel = e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(cam.getPosition());
        // camera.rotation() 是 billboard 的 view→world 朝向；world→view 取其共轭
        // （conjugate 原地修改，必须复制一份——不要污染相机自身朝向）
        Vector3f v = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
        new Quaternionf(cam.rotation()).conjugate().transform(v);
        // 注意：MC 的 view 空间前向是 +Z（rotation() 把 view +Z 映到世界前向），
        // 不是 GL 惯例的 -Z——可见 ⇔ v.z > 0
        if (v.z <= 0.05f) return null;
        int fbW = mc.getMainRenderTarget().width;
        int fbH = mc.getMainRenderTarget().height;
        double fovV = Math.toRadians(mc.options.fov().get());
        double tanV = Math.tan(fovV / 2);
        double tanH = tanV * ((double) fbW / fbH);
        double ndcX = (v.x / v.z) / tanH;
        double ndcY = (v.y / v.z) / tanV;
        if (Math.abs(ndcX) > 1 || Math.abs(ndcY) > 1) return null;
        return new double[]{(ndcX + 1) / 2 * fbW, (1 - ndcY) / 2 * fbH, -v.z};
    }

    /** Max channel delta inside a small box — nonzero means non-sky pixels present. */
    private static int roiContrast(Minecraft mc, double cx, double cy, int r) throws Exception {
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int dy = -r; dy <= r; dy += 2) {
                for (int dx = -r; dx <= r; dx += 2) {
                    int x = (int) cx + dx, y = (int) cy + dy;
                    if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) continue;
                    int argb = img.getPixelRGBA(x, y);
                    int lum = ((argb >> 16) & 255) + ((argb >> 8) & 255) + (argb & 255);
                    lo = Math.min(lo, lum);
                    hi = Math.max(hi, lum);
                }
            }
            int contrast = lo == Integer.MAX_VALUE ? 0 : hi - lo;
            System.out.println("WINGS_ROI_CONTRAST @" + (int) cx + "," + (int) cy + "=" + contrast);
            return contrast;
        }
    }

    private static void mark(Player p) {
        markX = p.getX();
        markZ = p.getZ();
    }

    private static double moved(Player p) {
        return Math.hypot(p.getX() - markX, p.getZ() - markZ);
    }

    private static boolean handItemDrawn(Minecraft mc) {
        // 手持判定：第一人称下手持物品画在右下象限；对比截图该区域是否非空
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int w = img.getWidth(), h = img.getHeight();
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int dy = 0; dy < h / 4; dy += 3) {
                for (int dx = 0; dx < w / 4; dx += 3) {
                    int argb = img.getPixelRGBA(w * 3 / 4 + dx, h * 3 / 4 + dy);
                    int lum = ((argb >> 16) & 255) + ((argb >> 8) & 255) + (argb & 255);
                    lo = Math.min(lo, lum);
                    hi = Math.max(hi, lum);
                }
            }
            int contrast = hi - lo;
            System.out.println("WINGS_HAND_ROI contrast=" + contrast);
            return contrast > 20;
        }
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
