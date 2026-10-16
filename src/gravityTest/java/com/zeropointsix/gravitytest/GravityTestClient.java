package com.zeropointsix.gravitytest;

import com.mojang.blaze3d.platform.InputConstants;
import com.zeropointsix.eraser.client.GravityClient;
import com.zeropointsix.eraser.client.GravitySense;
import com.zeropointsix.eraser.gravity.GravityEquipment;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import com.zeropointsix.eraser.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "gravity_qa", value = Dist.CLIENT)
public final class GravityTestClient {
    private static final Path RESULTS = Path.of(System.getProperty("gravity.qa.results"));
    private static final String ROLE = System.getProperty("gravity.qa.role");
    private static String phase = "";
    private static int stage;
    private static int ticks;
    private static int totalTicks;
    private static int selected;
    private static boolean finished;
    private static boolean sawField;
    private static final Set<String> sensedKinds = new HashSet<>();
    private static net.minecraft.world.phys.Vec3 castCenter;
    private static AABB serverBounds;
    private static long renderedFrames;
    private static long inputReadyFrame;
    private static long stressStarted;
    private static long stressFrames;
    private static int stressPeak;

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();
        if (message.startsWith("GRAVITY_QA_PHASE:")) {
            phase = message.substring("GRAVITY_QA_PHASE:".length());
            // Harness coordination is not gameplay chat and must not obscure pixel probes.
            event.setCanceled(true);
        }
        if (message.startsWith("GRAVITY_QA_BOUNDS:")) {
            String[] coordinates = message.substring("GRAVITY_QA_BOUNDS:".length()).split(",");
            serverBounds = new AABB(Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1]),
                    Double.parseDouble(coordinates[2]), Double.parseDouble(coordinates[3]),
                    Double.parseDouble(coordinates[4]), Double.parseDouble(coordinates[5]));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void rendered(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) renderedFrames++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            // The category matrix now exercises 654 real hovers per client before gameplay.
            if (++totalTicks > (Boolean.getBoolean("qiwu.qa") ? 30000 : 6000))
                throw new AssertionError("Timed out in stage " + stage + ", phase " + phase);
            if (mc.player == null || mc.level == null) return;
            Entity moving = named(mc, "gravityqa_moving");
            Entity still = named(mc, "gravityqa_still");
            if (stage == 0) {
                if (!phase.equals("ready") || moving == null || still == null) return;
                if (ROLE.equals("wearer") && !GravityEquipment.isEquipped(mc.player)) return;
                if (!QijieInventoryProbe.tick(mc, renderedFrames, RESULTS, ROLE)) return;
                if (!ItemCategoryClientProbe.tick(mc, renderedFrames, RESULTS, ROLE)) return;
                mc.setScreen(null);
                GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.mouseHandler.grabMouse();
                stage = 1;
                ticks = 0;
                return;
            }
            ticks++;
            if (stage == 1 && ROLE.equals("wearer")) {
                for (Entity target : GravitySense.targets()) sensedKinds.add(target.getName().getString());
            }
            if (stage == 1 && ticks >= 80) {
                require(moving != null && still != null, "fixture entities are tracked");
                require(!moving.isCurrentlyGlowing() && !still.isCurrentlyGlowing(), "no synchronized glowing flags");
                require(!GravitySense.outlines(still), "stationary entity must not outline");
                if (ROLE.equals("observer")) {
                    require(!GravityEquipment.isEquipped(mc.player), "observer does not wear necklace");
                    require(!mc.shouldEntityAppearGlowing(moving), "private outline must not leak to observer");
                    require(GravitySense.targets().isEmpty(), "observer has no private sense targets");
                    capture(mc, "private");
                    mark("observer-private.pass");
                    stage = 3;
                } else {
                    // The sinusoidal fixture briefly falls below the real speed threshold at turns.
                    if (!mc.shouldEntityAppearGlowing(moving)) return;
                    for (String kind : new String[]{"zombie", "item", "arrow", "minecart"}) {
                        require(sensedKinds.contains("gravityqa_" + kind), "moving " + kind + " sensed through wall");
                    }
                    require(!sensedKinds.contains("gravityqa_still_item"), "stationary ItemEntity never outlines");
                    require(mc.shouldEntityAppearGlowing(moving), "moving entity outlines through wall for wearer");
                    require(moving.getTeamColor() == 0xFFFFFF, "sensed model outline is white");
                    if (!Files.exists(RESULTS.resolve("observer-private.pass"))) return;
                    capture(mc, "private");
                    selected = mc.player.getInventory().selected;
                    stage = 2;
                }
                ticks = 0;
            }
            if (stage == 2) exerciseWearer(mc, moving);
            if (stage == 3) exerciseObserver(mc, moving);
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                capture(mc, "failed");
                Files.writeString(RESULTS.resolve(ROLE + ".failed"), failure.toString());
            } catch (Exception ignored) { }
            finished = true;
        }
    }

    private static void exerciseWearer(Minecraft mc, Entity moving) throws Exception {
        // Catch-up ticks can run before GLFW polls the xdotool event. Keep the
        // exact gameplay assertions, but first allow two real rendered frames.
        if (renderedFrames < inputReadyFrame) { ticks--; return; }
        switch (ticks) {
            case 1 -> command(mc, "unequip");
            case 20 -> {
                if (!phase.equals("unequip") || GravityEquipment.isEquipped(mc.player)) { ticks--; return; }
                require(!GravitySense.outlines(moving), "unequip revokes private sense");
                nativeInput("keydown", "v");
            }
            case 35 -> {
                require(fields(mc) == 0 && !mc.player.getCooldowns().isOnCooldown(ModItems.GRAVITY_JADE_PENDANT.get()),
                        "unequipped native V press creates no field or cooldown");
                nativeInput("keyup", "v");
                command(mc, "equip");
            }
            case 60 -> {
                if (!phase.equals("equip") || !GravityEquipment.isEquipped(mc.player)) { ticks--; return; }
                command(mc, "stop");
            }
            case 85 -> {
                require(!GravitySense.outlines(moving), "outline disappears after movement stops");
                command(mc, "start");
            }
            case 100 -> {
                castCenter = mc.player.position();
                nativeInput("keydown", "v");
            }
            case 115 -> {
                if (serverBounds == null) { ticks--; return; }
                require(GravityClient.ACTIVATE.isDown(), "native V remains physically held");
                require(fields(mc) == 1, "native V press creates a field before key release");
                GravityFieldEntity field = field(mc);
                require(field.position().distanceToSqr(castCenter) < 0.001, "server casts at exact player feet");
                require(field.fieldBounds().equals(serverBounds), "wearer bounds equal server authority");
                require(mc.player.getCooldowns().isOnCooldown(ModItems.GRAVITY_JADE_PENDANT.get()), "cast synchronizes cooldown");
                GravityVisualProbe.request("active", field);
                mark("wearer-cast.pass");
                selected = mc.player.getInventory().selected;
            }
            case 120 -> nativeInput("click", "4");
            case 130 -> {
                require(mc.player.getInventory().selected != selected, "scroll changes hotbar normally while V is held");
                nativeInput("mousedown", "3");
            }
            case 132 -> nativeInput("mouseup", "3");
            case 140 -> nativeInput("keydown", "Escape");
            case 141 -> nativeInput("keyup", "Escape");
            case 150 -> {
                require(fields(mc) == 1, "right-click and Escape do not cancel an active domain");
                mc.setScreen(null);
                mc.mouseHandler.grabMouse();
                mc.player.setYRot(mc.player.getYRot() + 90);
                require(field(mc).position().distanceToSqr(castCenter) < 0.001, "turning does not move the domain");
                mc.player.setYRot(mc.player.getYRot() - 90);
                nativeInput("keyup", "v");
            }
            case 180 -> {
                GravityClient.ACTIVATE.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B));
                KeyMapping.resetMapping();
                nativeInput("keydown", "b");
            }
            case 195 -> {
                require(fields(mc) == 1, "cooldown blocks another native-key press");
                nativeInput("keyup", "b");
            }
            case 500 -> {
                if (!Files.exists(RESULTS.resolve("server-effects.pass")) || fields(mc) != 0) { ticks--; return; }
                require(mc.player.getCooldowns().isOnCooldown(ModItems.GRAVITY_JADE_PENDANT.get()),
                        "60-second cooldown outlasts the 15-second field");
                command(mc, "reset-cooldown");
            }
            case 520 -> {
                if (!phase.equals("reset-cooldown")
                        || mc.player.getCooldowns().isOnCooldown(ModItems.GRAVITY_JADE_PENDANT.get())) { ticks--; return; }
                nativeInput("keydown", "b");
            }
            case 535 -> {
                require(GravityClient.ACTIVATE.isDown() && fields(mc) == 1, "rebound B activates before release");
                nativeInput("keyup", "b");
                mark("wearer-remap.pass");
            }
            case 900 -> {
                if (fields(mc) != 0) { ticks--; return; }
                command(mc, "stress");
                stressStarted = System.nanoTime();
                stressFrames = renderedFrames;
            }
            case 1000 -> {
                double fps = (renderedFrames - stressFrames) * 1_000_000_000D / (System.nanoTime() - stressStarted);
                require(stressPeak == 64, "200 moving ItemEntity stress reaches but never exceeds the nearest-64 cap");
                require(fps >= 5, "200-item software-rendered stress remains responsive (>=5 rendered FPS)");
                System.out.println("GRAVITY_E2E_STRESS items=200 peak=" + stressPeak + " rendered_fps=" + fps);
                capture(mc, "stress");
                mark("wearer-stress.pass");
                mark("wearer.pass");
                finished = true;
            }
            default -> { }
        }
        if (ticks > 900) {
            int count = GravitySense.targets().size();
            require(count <= 64, "highlight cap remains bounded");
            stressPeak = Math.max(stressPeak, count);
        }
    }

    private static void exerciseObserver(Minecraft mc, Entity moving) throws Exception {
        require(GravitySense.targets().isEmpty() && (moving == null || !mc.shouldEntityAppearGlowing(moving)), "private sensing remains wearer-only");
        if (fields(mc) == 1 && !sawField && serverBounds != null) {
            GravityFieldEntity field = mc.level.getEntitiesOfClass(GravityFieldEntity.class,
                    mc.player.getBoundingBox().inflate(64)).get(0);
            require(field.fieldBounds().equals(serverBounds), "observer field bounds equal server authority");
            sawField = true;
            GravityVisualProbe.request("active", field);
        }
        if (sawField && fields(mc) == 0 && Files.exists(RESULTS.resolve("wearer.pass"))) {
            mark("observer.pass");
            finished = true;
        }
    }

    private static Entity named(Minecraft mc, String name) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getName().getString().equals(name)) return entity;
        }
        return null;
    }

    private static long fields(Minecraft mc) {
        return mc.level.getEntitiesOfClass(GravityFieldEntity.class, mc.player.getBoundingBox().inflate(32)).size();
    }

    private static GravityFieldEntity field(Minecraft mc) {
        return mc.level.getEntitiesOfClass(GravityFieldEntity.class, mc.player.getBoundingBox().inflate(64)).get(0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("GRAVITY_E2E_ASSERT " + ROLE + ": " + message);
    }

    private static void nativeInput(String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "xdotool";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).inheritIO().start();
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new AssertionError("xdotool exited with " + exitCode);
        inputReadyFrame = renderedFrames + 2;
    }

    private static void command(Minecraft mc, String phase) { mc.player.connection.sendCommand("gravityqa phase " + phase); }

    private static void mark(String name) throws Exception { Files.writeString(RESULTS.resolve(name), "passed\n"); }

    private static void capture(Minecraft mc, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(RESULTS.resolve(ROLE + "-" + name + ".png"));
        }
    }

    private GravityTestClient() { }
}
