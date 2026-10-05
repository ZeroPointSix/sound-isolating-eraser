package com.zeropointsix.gravitytest;

import com.mojang.blaze3d.platform.InputConstants;
import com.zeropointsix.eraser.client.GravityClient;
import com.zeropointsix.eraser.client.GravitySense;
import com.zeropointsix.eraser.gravity.GravityEquipment;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import com.zeropointsix.eraser.gravity.GravityGeometry;
import com.zeropointsix.eraser.registry.ModItems;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
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

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();
        if (message.startsWith("GRAVITY_QA_PHASE:")) phase = message.substring("GRAVITY_QA_PHASE:".length());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++totalTicks > 6000) throw new AssertionError("Timed out in stage " + stage + ", phase " + phase);
            if (mc.player == null || mc.level == null) return;
            Entity moving = named(mc, "gravityqa_moving");
            Entity still = named(mc, "gravityqa_still");
            if (stage == 0) {
                if (!phase.equals("ready") || moving == null || still == null) return;
                if (ROLE.equals("wearer") && !GravityEquipment.isEquipped(mc.player)) return;
                mc.setScreen(null);
                GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.mouseHandler.grabMouse();
                stage = 1;
                ticks = 0;
                return;
            }
            ticks++;
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
        switch (ticks) {
            case 1 -> nativeInput("keydown", "v");
            case 15 -> {
                require(aiming(), "native V key enters preview");
                require(fields(mc) == 0, "preview is not a public field");
                capture(mc, "preview");
            }
            case 20 -> nativeInput("click", "4");
            case 25 -> {
                require(mc.player.getInventory().selected == selected, "preview scroll must not change hotbar");
                require(GravityClient.target(mc, 1).equals(GravityGeometry.target(mc.player.getEyePosition(),
                        mc.player.getLookAngle(), 9)), "one scroll step adds exactly one block");
            }
            case 30 -> nativeInput("mousedown", "3");
            case 32 -> nativeInput("mouseup", "3");
            case 40 -> {
                requireCanceled(mc, "native right-click");
                nativeInput("keyup", "v");
            }
            case 50 -> nativeInput("keydown", "v");
            case 60 -> nativeInput("keydown", "Escape");
            case 61 -> nativeInput("keyup", "Escape");
            case 65 -> {
                requireCanceled(mc, "native Escape");
                nativeInput("keyup", "v");
                mc.setScreen(null);
                mc.mouseHandler.grabMouse();
            }
            case 75 -> nativeInput("keydown", "v");
            case 80 -> command(mc, "unequip");
            case 110 -> {
                requireCanceled(mc, "server unequip");
                require(!GravityEquipment.isEquipped(mc.player) && !GravitySense.outlines(moving), "unequip revokes private sense");
                nativeInput("keyup", "v");
                command(mc, "equip");
            }
            case 150 -> {
                require(GravityEquipment.isEquipped(mc.player), "Curios re-equip synchronized");
                command(mc, "stop");
            }
            case 180 -> {
                require(!GravitySense.outlines(moving), "outline disappears after movement stops");
                command(mc, "start");
            }
            case 210 -> {
                GravityClient.TARGET.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B));
                KeyMapping.resetMapping();
                nativeInput("keydown", "b");
            }
            case 225 -> require(aiming(), "rebound B key enters preview");
            case 235 -> nativeInput("keyup", "b");
            case 285 -> {
                require(fields(mc) == 1, "release creates one authoritative synchronized field");
                require(mc.player.getCooldowns().isOnCooldown(ModItems.GRAVITY_JADE_PENDANT.get()), "successful cast synchronizes cooldown");
                capture(mc, "active");
                mark("wearer-cast.pass");
            }
            case 300 -> nativeInput("keydown", "b");
            case 305 -> nativeInput("keyup", "b");
            case 320 -> {
                require(!aiming() && fields(mc) == 1, "cooldown blocks a repeated native-key cast");
            }
            case 650 -> {
                require(fields(mc) == 0, "field expires on wearer client");
                mark("wearer.pass");
                finished = true;
            }
            default -> { }
        }
    }

    private static void exerciseObserver(Minecraft mc, Entity moving) throws Exception {
        require(GravitySense.targets().isEmpty() && !mc.shouldEntityAppearGlowing(moving), "private sensing remains wearer-only");
        require(!aiming(), "another player's preview never activates observer controls");
        if (fields(mc) == 1 && !sawField) {
            sawField = true;
            capture(mc, "active");
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

    private static boolean aiming() throws Exception {
        Field field = GravityClient.class.getDeclaredField("aiming");
        field.setAccessible(true);
        return field.getBoolean(null);
    }

    private static void requireCanceled(Minecraft mc, String action) throws Exception {
        require(!aiming() && fields(mc) == 0
                && !mc.player.getCooldowns().isOnCooldown(ModItems.GRAVITY_JADE_PENDANT.get()), action + " cancels without cooldown");
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
