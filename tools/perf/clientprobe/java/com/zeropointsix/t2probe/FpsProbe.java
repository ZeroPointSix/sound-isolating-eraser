package com.zeropointsix.t2probe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

@Mod(FpsProbe.ID)
public final class FpsProbe {
    public static final String ID = "t2_fps_probe";
    public FpsProbe() { }

    @Mod.EventBusSubscriber(modid = ID, value = Dist.CLIENT)
    public static final class Client {
        private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
        private static final Path OUT = Path.of(System.getProperty("t2.fps.out", "."));
        private static final int CAPACITY = 150_000;
        private static final long[] TIMES = new long[CAPACITY];
        private static final double[][] POSES = new double[5][CAPACITY];
        private static long start, duration, deadline, overhead;
        private static int frames, ticks;
        private static boolean armed, active, drawn;
        private static String label = "";
        private static ResourceLocation dimension;
        private static Map<String, Object> metadata;

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || ++ticks % 20 != 0 || active) return;
            try {
                if (armed) {
                    if (System.nanoTime() > deadline) throw new IllegalStateException("No valid world frame before deadline");
                    return;
                }
                Path request = OUT.resolve("fps-request.json");
                if (!Files.isRegularFile(request)) return;
                JsonObject input = JSON.fromJson(Files.readString(request), JsonObject.class);
                String next = input.get("label").getAsString();
                if (next.equals(label)) return;
                if (!next.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid label");
                label = next;
                dimension = new ResourceLocation(input.get("dimension").getAsString());
                int seconds = input.get("seconds").getAsInt();
                if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("Invalid duration");
                duration = seconds * 1_000_000_000L;
                deadline = System.nanoTime() + 30_000_000_000L;
                frames = 0;
                overhead = 0;
                armed = true;
            } catch (Exception error) {
                fail(error);
            }
        }

        @SubscribeEvent
        public static void world(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) drawn = true;
        }

        @SubscribeEvent
        public static void frame(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) { drawn = false; return; }
            if (!armed && !active) return;
            long callbackStart = System.nanoTime();
            try {
                Minecraft mc = Minecraft.getInstance();
                boolean valid = mc.level != null && mc.player != null && mc.screen == null
                        && mc.getOverlay() == null && !mc.isPaused() && drawn
                        && mc.level.dimension().location().equals(dimension) && mc.player.isAlive();
                if (!valid) {
                    if (active) throw new IllegalStateException("Invalid sampled frame: screen="
                            + (mc.screen == null ? "none" : mc.screen.getClass().getName())
                            + ", drawn=" + drawn + ", level=" + (mc.level == null ? "none" : mc.level.dimension().location()));
                    return;
                }
                if (!active) {
                    metadata = new LinkedHashMap<>();
                    metadata.put("label", label);
                    metadata.put("dimension", dimension.toString());
                    metadata.put("startUtc", Instant.now().toString());
                    metadata.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
                    metadata.put("vendor", GL11.glGetString(GL11.GL_VENDOR));
                    metadata.put("openGlVersion", GL11.glGetString(GL11.GL_VERSION));
                    metadata.put("width", mc.getWindow().getWidth());
                    metadata.put("height", mc.getWindow().getHeight());
                    metadata.put("framerateLimit", mc.options.framerateLimit().get());
                    metadata.put("vsync", mc.options.enableVsync().get());
                    metadata.put("renderDistance", mc.options.renderDistance().get());
                    metadata.put("simulationDistance", mc.options.simulationDistance().get());
                    metadata.put("graphics", mc.options.graphicsMode().get().toString());
                    metadata.put("particles", mc.options.particles().get().toString());
                    metadata.put("fov", mc.options.fov().get());
                    metadata.put("camera", mc.options.getCameraType().toString());
                    metadata.put("probe", "Forge RenderTickEvent END timestamps; not GPU-only timing");
                    Files.writeString(OUT.resolve(label + "-fps-started.json"), JSON.toJson(metadata));
                    start = System.nanoTime();
                    active = true;
                    armed = false;
                }
                if (frames == CAPACITY) throw new IllegalStateException("Frame buffer full");
                TIMES[frames] = System.nanoTime() - start;
                POSES[0][frames] = mc.player.getX();
                POSES[1][frames] = mc.player.getY();
                POSES[2][frames] = mc.player.getZ();
                POSES[3][frames] = mc.player.getYRot();
                POSES[4][frames] = mc.player.getXRot();
                frames++;
                overhead += System.nanoTime() - callbackStart;
                if (TIMES[frames - 1] - TIMES[0] >= duration) finish();
            } catch (Exception error) {
                fail(error);
            }
        }

        private static void finish() throws Exception {
            active = false;
            try (BufferedWriter output = Files.newBufferedWriter(OUT.resolve(label + "-frames.csv"))) {
                output.write("elapsedNanos,x,y,z,yaw,pitch\n");
                for (int i = 0; i < frames; i++) {
                    output.write(Long.toString(TIMES[i] - TIMES[0]));
                    for (double[] coordinate : POSES) output.write("," + coordinate[i]);
                    output.newLine();
                }
            }
            metadata.put("endUtc", Instant.now().toString());
            metadata.put("frames", frames);
            metadata.put("intervals", frames - 1);
            metadata.put("elapsedNanos", TIMES[frames - 1] - TIMES[0]);
            metadata.put("callbackNanosIncludingStartMetadata", overhead);
            metadata.put("validWorldFrames", frames);
            metadata.put("status", "complete");
            Files.writeString(OUT.resolve(label + "-fps-complete.json"), JSON.toJson(metadata));
        }

        private static void fail(Exception error) {
            active = false;
            armed = false;
            try {
                Files.writeString(OUT.resolve("fps-failure.json"), JSON.toJson(Map.of(
                        "label", label, "error", error.toString(), "frames", frames, "utc", Instant.now().toString())));
            } catch (Exception nested) {
                throw new IllegalStateException(nested);
            }
        }
    }
}
