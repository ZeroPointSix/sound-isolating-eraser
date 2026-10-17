package com.zeropointsix.rednailstest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.client.RedNailsClient;
import com.zeropointsix.eraser.rednails.RedNailsItem;
import com.zeropointsix.eraser.rednails.WaterGhostEntity;
import com.zeropointsix.eraser.rednails.WaterGhostSummonEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.FluidTags;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "red_nails_qa", value = Dist.CLIENT)
public final class RedNailsAcceptanceClient {
    private static final boolean ENABLED = Boolean.getBoolean("rednails.qa.acceptance");
    private static final Path RESULTS = Path.of(System.getProperty("rednails.qa.results"));
    private static volatile String phase = "";
    private static int ticks, attacks, clientTicks, vHeldTicks;
    private static boolean failed;
    private static AcceptanceObservations.BoatHistory boatHistory = new AcceptanceObservations.BoatHistory();
    private static volatile String captureCase = "";
    private static long captureTraceTick, captureTraceSequence;
    private static boolean motionRecorded;
    private static final InputConstants.Key LIFECYCLE_KEY = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_O);
    private static InputConstants.Key originalKey;
    private static int rebindTicks, oldKeyHeldTicks;
    private static SpeedClient speedClient;
    private static final class SpeedClient {
        final String name;
        final double[] recentSteps = new double[20];
        final JsonArray tickSamples = new JsonArray();
        int windowCount, windowIndex, samples, chaseTicks, chaseForwardTicks, chaseModeTicks;
        int lastTick = -1, firstTick = -1;
        double chaseDistance, chaseMax;
        Vec3 lastPlayer, lastBoat;
        String boatUuid = "", fixtureBoatUuid = "";
        SpeedClient(String name) { this.name = name; }
        boolean swimmer() { return name.equals("swimmer"); }
    }
    private static void reset() {
        var mc = Minecraft.getInstance();
        mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(false); mc.options.keySprint.setDown(false);
        mc.options.keyShift.setDown(false);
        RedNailsClient.KEY.setDown(false);
        if (originalKey != null) {
            KeyMapping.set(originalKey, false);
            KeyMapping.set(LIFECYCLE_KEY, false);
        }
        if (mc.player != null && mc.player.isShiftKeyDown()) {
            mc.player.setShiftKeyDown(false);
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        }
    }
    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        if (!ENABLED) return;
        String text = event.getMessage().getString();
        if (text.startsWith("RED_NAILS_ACCEPTANCE:")) {
            String next = text.substring("RED_NAILS_ACCEPTANCE:".length());
            String nextCase = captureCase(next);
            // A server phase notification must not release native boat input mid-capture.
            boolean keepLifecycleKey = phase.equals("lifecycle-gui-start") && next.equals("lifecycle-gui-open")
                    || phase.equals("lifecycle-gui-open") && next.equals("lifecycle-gui-closed");
            boolean keepSpeedInput = !speedCase(phase).isEmpty() && speedCase(phase).equals(speedCase(next))
                    && phase.endsWith("-warmup") && next.endsWith("-chase");
            if (!keepLifecycleKey && !keepSpeedInput && (nextCase.isEmpty() || !nextCase.equals(captureCase))) reset();
            if (next.endsWith("-ready")) {
                captureCase = nextCase;
                boatHistory = new AcceptanceObservations.BoatHistory();
                motionRecorded = false;
            }
            phase = next; ticks = 0; attacks = 0; vHeldTicks = 0;
            if (!nextCase.isEmpty()) {
                try { captureDiagnostic("PHASE_RECEIVED", next); }
                catch (Exception error) { throw new IllegalStateException("Cannot record capture phase receipt", error); }
            }
            event.setCanceled(true);
        }
    }
    private static String captureCase(String value) {
        for (String name : new String[] {"boat", "bottom", "damage", "front-bottom", "front"}) {
            if (value.startsWith(name + "-")) return name;
        }
        return "";
    }
    private static String speedCase(String value) {
        for (String name : new String[] {"swimmer", "boat", "calibration"}) {
            if (value.startsWith("speed-" + name + "-")) return name;
        }
        return "";
    }
    private static void observeBoat(Minecraft mc) throws Exception {
        if (captureCase.isEmpty() || motionRecorded) return;
        if (mc.player.getVehicle() instanceof Boat nativeBoat) {
            boatHistory.observe(nativeBoat.getUUID().toString(), clientTicks, nativeBoat.getX(), nativeBoat.getY(),
                    nativeBoat.getZ(), mc.options.keyUp.isDown() && mc.player.input.forwardImpulse > 0);
            Files.writeString(RESULTS.resolve("acceptance-" + captureCase + "-client-motion.csv"),
                    clientTicks + "," + boatHistory.uuid + "," + nativeBoat.getX() + "," + nativeBoat.getY()
                            + "," + nativeBoat.getZ() + "," + boatHistory.lastStep + "," + boatHistory.forward
                            + "," + boatHistory.movingTicks + "," + phase + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else if (boatHistory.lastTick >= 0) {
            JsonObject proof = new JsonObject();
            proof.addProperty("boatUuid", boatHistory.uuid);
            proof.addProperty("lastMountedClientTick", boatHistory.lastTick);
            proof.addProperty("dismountedClientTick", clientTicks);
            proof.addProperty("consecutiveMovingTicks", boatHistory.movingTicks);
            proof.addProperty("lastStep", boatHistory.lastStep);
            proof.addProperty("forwardInput", boatHistory.forward);
            proof.addProperty("movingAtDismount", boatHistory.movingAtDismount(clientTicks));
            proof.addProperty("lastMountedPosition", boatHistory.x + "," + boatHistory.y + "," + boatHistory.z);
            proof.addProperty("dismountedPosition", mc.player.position().toString());
            signal(captureCase + "-capture-motion", proof.toString());
            motionRecorded = true;
        }
    }
    private static void signal(String name, String body) throws Exception {
        Path target = RESULTS.resolve("acceptance-client-" + name + ".signal");
        Path pending = RESULTS.resolve("acceptance-client-" + name + ".pending");
        Files.writeString(pending, body + "\n");
        Files.move(pending, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    private static JsonObject diagnosticVector(Vec3 value) {
        var json = new JsonObject();
        json.addProperty("x", value.x); json.addProperty("y", value.y); json.addProperty("z", value.z);
        return json;
    }
    private static JsonObject captureClientState(Minecraft mc) {
        var json = new JsonObject();
        json.addProperty("traceTick", captureTraceTick); json.addProperty("clientTick", clientTicks);
        json.addProperty("phase", phase); json.addProperty("phaseTicks", ticks);
        json.addProperty("levelTick", mc.level.getGameTime());
        json.addProperty("activeWindow", mc.isWindowActive());
        json.addProperty("screen", mc.screen == null ? "" : mc.screen.getClass().getSimpleName());
        json.add("position", diagnosticVector(mc.player.position()));
        json.add("velocity", diagnosticVector(mc.player.getDeltaMovement()));
        json.addProperty("forwardKey", mc.options.keyUp.isDown());
        json.addProperty("jumpKey", mc.options.keyJump.isDown());
        json.addProperty("sprintKey", mc.options.keySprint.isDown());
        json.addProperty("forwardImpulse", mc.player.input.forwardImpulse);
        json.addProperty("leftImpulse", mc.player.input.leftImpulse);
        json.addProperty("jumping", mc.player.input.jumping);
        json.addProperty("sprinting", mc.player.isSprinting()); json.addProperty("swimming", mc.player.isSwimming());
        json.addProperty("pose", mc.player.getPose().toString()); json.addProperty("onGround", mc.player.onGround());
        json.addProperty("bodyWet", mc.player.isInWater()); json.addProperty("eyesWet", mc.player.isEyeInFluid(FluidTags.WATER));
        json.addProperty("passenger", mc.player.isPassenger());
        json.addProperty("horizontalCollision", mc.player.horizontalCollision);
        json.addProperty("verticalCollision", mc.player.verticalCollision);
        json.addProperty("air", mc.player.getAirSupply());
        Boat fixtureBoat = mc.player.getVehicle() instanceof Boat mounted ? mounted : null;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Boat candidate && candidate.getUUID().toString().equals(boatHistory.uuid)) fixtureBoat = candidate;
            if (entity instanceof WaterGhostEntity observedGhost) {
                json.addProperty("ghostId", observedGhost.getId());
                json.add("ghostPosition", diagnosticVector(observedGhost.position()));
                json.add("ghostVelocity", diagnosticVector(observedGhost.getDeltaMovement()));
            }
        }
        json.addProperty("fixtureBoatVisible", fixtureBoat != null);
        json.addProperty("boatUuid", fixtureBoat == null ? boatHistory.uuid : fixtureBoat.getUUID().toString());
        if (fixtureBoat != null) {
            json.addProperty("boatId", fixtureBoat.getId());
            json.add("boatPosition", diagnosticVector(fixtureBoat.position()));
            json.add("boatVelocity", diagnosticVector(fixtureBoat.getDeltaMovement()));
        }
        return json;
    }
    private static synchronized void appendCaptureTrace(String stream, JsonObject json) throws Exception {
        json.addProperty("sequence", ++captureTraceSequence);
        json.addProperty("clockScope", "client-process");
        json.addProperty("monotonicNanos", System.nanoTime());
        Files.writeString(RESULTS.resolve("acceptance-" + captureCase + "-" + stream + ".jsonl"), json + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private static void captureDiagnostic(String edge, String receivedPhase) throws Exception {
        var mc = Minecraft.getInstance();
        if (captureCase.isEmpty() || mc.level == null || mc.player == null) return;
        var json = captureClientState(mc); json.addProperty("edge", edge);
        if (!receivedPhase.isEmpty()) json.addProperty("receivedPhase", receivedPhase);
        appendCaptureTrace("client-state", json);
        if (edge.equals("END")) signal(captureCase + "-diagnostic", json.toString());
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void captureDiagnosticTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || failed || captureCase.isEmpty() || !captureCase(phase).equals(captureCase)) return;
        if (event.phase == TickEvent.Phase.START) captureTraceTick++;
        try { captureDiagnostic(event.phase.name(), ""); }
        catch (Exception error) { throw new IllegalStateException("Cannot record continuous capture diagnostics", error); }
    }
    public static void traceCapturePacket(String edge, Object packet) {
        if (!ENABLED || captureCase.isEmpty() || !captureCase(phase).equals(captureCase)) return;
        var mc = Minecraft.getInstance();
        boolean mainThread = mc.isSameThread();
        var json = new JsonObject();
        json.addProperty("edge", mainThread ? "APPLY_" + edge : "RECEIVE_" + edge);
        json.addProperty("mainThread", mainThread); json.addProperty("phase", phase);
        json.addProperty("packetIdentity", System.identityHashCode(packet));
        json.addProperty("packet", packet.getClass().getSimpleName());
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket motion) {
            json.addProperty("entityId", motion.getId());
            json.addProperty("wireXa", motion.getXa()); json.addProperty("wireYa", motion.getYa()); json.addProperty("wireZa", motion.getZa());
            json.addProperty("wireVelocityScale", 8000);
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket position) {
            json.addProperty("wireX", position.getX()); json.addProperty("wireY", position.getY()); json.addProperty("wireZ", position.getZ());
            json.addProperty("relative", position.getRelativeArguments().toString());
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetPassengersPacket passengers) {
            json.addProperty("vehicleId", passengers.getVehicle());
            var ids = new JsonArray(); for (int id : passengers.getPassengers()) ids.add(id);
            json.add("passengers", ids);
        }
        if (mainThread && mc.level != null && mc.player != null) {
            json.addProperty("playerId", mc.player.getId());
            json.add("state", captureClientState(mc));
        }
        try { appendCaptureTrace("client-packets", json); }
        catch (Exception error) { throw new IllegalStateException("Cannot record native capture packet", error); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || failed || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            clientTicks++;
            if (phase.equals("lifecycle-disconnected")) return;
            if (mc.screen instanceof DisconnectedScreen) throw new AssertionError("native acceptance disconnected phase=" + phase);
            if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            GLFW.glfwFocusWindow(mc.getWindow().getWindow());
            if (phase.startsWith("lifecycle-")) {
                if (!mc.isWindowActive()) return;
                ticks++;
                lifecycleTick(mc);
                return;
            }
            mc.setScreen(null); mc.mouseHandler.grabMouse();
            if (!mc.isWindowActive()) return;
            ticks++;
            if (!speedCase(phase).isEmpty()) {
                speedTick(mc);
                return;
            }
            observeBoat(mc);
            if (phase.isEmpty()) {
                signal("ready", "real client loaded, active window, real world");
                return;
            }
            if (phase.endsWith("-ready") || phase.endsWith("-moving")) {
                if (mc.player.getVehicle() instanceof Boat) mc.options.keyUp.setDown(true);
                return;
            }
            if (phase.endsWith("-held")) {
                if (ticks == 20) screenshot(phase);
                if (phase.startsWith("damage")) {
                    if (!mc.player.isPassenger()) mc.options.keyUp.setDown(false);
                    for (var entity : mc.level.entitiesForRendering()) {
                        if (entity instanceof WaterGhostEntity ghost && ghost.waterGhostGrabAmount(1) > 0.5
                                && ghost.distanceToSqr(mc.player) < 16 && ticks >= 10 + attacks * 22
                                && mc.player.getAttackStrengthScale(0) >= 0.95 && attacks < 2) {
                            mc.gameMode.attack(mc.player, ghost);
                            mc.player.swing(InteractionHand.MAIN_HAND); attacks++;
                            signal("damage-attack-" + attacks, "real client attack packet tick=" + ticks);
                        }
                    }
                } else {
                    mc.options.keyUp.setDown(true); mc.options.keyJump.setDown(true); mc.options.keySprint.setDown(true);
                    JsonObject input = new JsonObject();
                    input.addProperty("clientTick", clientTicks);
                    input.addProperty("forwardImpulse", mc.player.input.forwardImpulse);
                    input.addProperty("jumping", mc.player.input.jumping);
                    input.addProperty("sprintKey", mc.options.keySprint.isDown());
                    input.addProperty("passenger", mc.player.isPassenger());
                    input.addProperty("position", mc.player.position().toString());
                    signal(phase.replace("-held", "-resist"), input.toString());
                }
                return;
            }
            if (phase.endsWith("-complete")) {
                reset();
                if (ticks == 5) {
                    String name = phase.replace("-complete", "");
                    screenshot(name);
                    signal(name, "real client completed " + name + " position=" + mc.player.position());
                }
                return;
            }
            if (phase.endsWith("-released")) {
                if (ticks == 1) { mc.player.setYRot(90); mc.player.setXRot(-20); }
                mc.options.keyUp.setDown(true); mc.options.keyJump.setDown(true); mc.options.keySprint.setDown(true);
                JsonObject input = new JsonObject();
                input.addProperty("clientTick", clientTicks);
                input.addProperty("phaseTicks", ticks);
                input.addProperty("forwardImpulse", mc.player.input.forwardImpulse);
                input.addProperty("jumping", mc.player.input.jumping);
                signal(phase + "-input", input.toString());
                if (ticks == 30) screenshot(phase);
                return;
            }
            if (phase.equals("coexist")) {
                RedNailsClient.KEY.setDown(ticks > 5);
                vHeldTicks = RedNailsClient.KEY.isDown() ? vHeldTicks + 1 : 0;
                signal("coexist-input", Integer.toString(vHeldTicks));
                if (ticks == 20) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            } else if (phase.equals("item-recall")) {
                mc.options.keyShift.setDown(true);
                RedNailsClient.KEY.setDown(ticks > 5);
            } else if (phase.equals("item-resummon")) {
                RedNailsClient.KEY.setDown(ticks > 5);
            } else if (phase.equals("watch-recall")) {
                if (ticks == 10) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            } else if (phase.equals("complete") && ticks == 5) {
                reset(); restoreLifecycleKey(); screenshot("complete");
                Files.writeString(RESULTS.resolve("acceptance-client.pass"), "Real input, native world and OpenGL frames completed.\n");
            }
        } catch (Throwable error) {
            failed = true; reset(); restoreLifecycleKey(); error.printStackTrace();
            try { Files.writeString(RESULTS.resolve("acceptance-client.failed"), error.toString()); }
            catch (Exception ignored) { }
            try { screenshot("failure"); }
            catch (Exception ignored) { }
        }
    }
    private static String speedPosition(Vec3 pos) { return pos == null ? ",," : pos.x + "," + pos.y + "," + pos.z; }
    private static void speedTick(Minecraft mc) throws Exception {
        String name = speedCase(phase);
        if (speedClient == null || !speedClient.name.equals(name)) speedClient = new SpeedClient(name);
        var run = speedClient;
        boolean chase = phase.endsWith("-chase");
        boolean finish = phase.endsWith("-finish");
        boolean driving = !finish && (!name.equals("calibration") || chase);
        mc.options.keyUp.setDown(driving);
        mc.options.keySprint.setDown(driving && run.swimmer());
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
        if (run.firstTick < 0) {
            run.firstTick = clientTicks;
            mc.player.setYRot(0); mc.player.setXRot(0);
        }
        Boat nativeBoat = mc.player.getVehicle() instanceof Boat mounted ? mounted : null;
        if (nativeBoat != null && run.fixtureBoatUuid.isEmpty()) run.fixtureBoatUuid = nativeBoat.getUUID().toString();
        boolean consecutive = run.lastTick >= 0 && clientTicks == run.lastTick + 1;
        Vec3 playerStep = consecutive && run.lastPlayer != null ? mc.player.position().subtract(run.lastPlayer) : Vec3.ZERO;
        Vec3 boatStep = consecutive && nativeBoat != null && run.lastBoat != null
                && run.boatUuid.equals(nativeBoat.getUUID().toString()) ? nativeBoat.position().subtract(run.lastBoat) : Vec3.ZERO;
        Vec3 movement = run.swimmer() ? playerStep : boatStep;
        boolean forward = driving && mc.options.keyUp.isDown() && mc.player.input.forwardImpulse > 0;
        boolean modeValid = forward && (run.swimmer()
                ? mc.player.isSwimming() && mc.player.isSprinting() && mc.options.keySprint.isDown()
                    && mc.player.isEyeInFluid(FluidTags.WATER) && !mc.player.isPassenger()
                : nativeBoat != null && nativeBoat.isInWater() && run.fixtureBoatUuid.equals(nativeBoat.getUUID().toString()));
        if (modeValid && consecutive && movement.z > 0.05 && Math.abs(movement.x) < 0.01 && Math.abs(movement.y) < 0.05) {
            run.recentSteps[run.windowIndex++ % run.recentSteps.length] = movement.horizontalDistance();
            run.windowCount = Math.min(run.recentSteps.length, run.windowCount + 1);
        } else { run.windowCount = 0; run.windowIndex = 0; }
        double min = Double.POSITIVE_INFINITY, max = 0, sum = 0;
        for (int i = 0; i < run.windowCount; i++) {
            min = Math.min(min, run.recentSteps[i]); max = Math.max(max, run.recentSteps[i]); sum += run.recentSteps[i];
        }
        boolean stable = run.windowCount == run.recentSteps.length && max - min <= 0.005;
        var sample = new JsonObject();
        sample.addProperty("clientTick", clientTicks);
        sample.addProperty("phase", phase);
        sample.addProperty("consecutive", consecutive);
        sample.addProperty("forwardInput", forward);
        sample.addProperty("modeValid", modeValid);
        sample.addProperty("boatUuid", nativeBoat == null ? "" : nativeBoat.getUUID().toString());
        sample.addProperty("targetForwardStep", movement.z);
        sample.addProperty("stable", stable);
        run.tickSamples.add(sample);
        if (chase) {
            run.chaseTicks++;
            if (forward) run.chaseForwardTicks++;
            if (modeValid) run.chaseModeTicks++;
            if (modeValid && consecutive) {
                run.samples++; run.chaseDistance += movement.horizontalDistance();
                run.chaseMax = Math.max(run.chaseMax, movement.horizontalDistance());
            }
        }
        WaterGhostEntity observedGhost = null;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof WaterGhostEntity candidate && candidate.isAlive()) {
                observedGhost = candidate;
                break;
            }
        }
        JsonObject input = new JsonObject();
        input.addProperty("clientTick", clientTicks);
        input.addProperty("clientWorldTick", mc.level.getGameTime());
        input.addProperty("phase", phase);
        input.addProperty("phaseTicks", ticks);
        input.addProperty("forwardInput", forward);
        input.addProperty("modeValid", modeValid);
        input.addProperty("stable", stable);
        input.addProperty("stableWindowSamples", run.windowCount);
        input.addProperty("stableWindowMinBlocksPerTick", run.windowCount == 0 ? 0 : min);
        input.addProperty("stableWindowMaxBlocksPerTick", max);
        input.addProperty("stableWindowMeanBlocksPerTick", run.windowCount == 0 ? 0 : sum / run.windowCount);
        input.addProperty("swimming", mc.player.isSwimming());
        input.addProperty("sprinting", mc.player.isSprinting());
        input.addProperty("upKey", mc.options.keyUp.isDown());
        input.addProperty("sprintKey", mc.options.keySprint.isDown());
        input.addProperty("forwardImpulse", mc.player.input.forwardImpulse);
        input.addProperty("passenger", mc.player.isPassenger());
        input.addProperty("boatUuid", nativeBoat == null ? "" : nativeBoat.getUUID().toString());
        input.addProperty("playerPosition", mc.player.position().toString());
        input.addProperty("boatPosition", nativeBoat == null ? "" : nativeBoat.position().toString());
        signal("speed-" + name + "-input", input.toString());
        var path = RESULTS.resolve("acceptance-speed-" + name + "-client.csv");
        if (!Files.exists(path)) Files.writeString(path,
                "clientTick,clientWorldTick,phaseTick,phase,playerX,playerY,playerZ,playerStep3d,playerStepHorizontal,pose,swimming,sprinting,eyeWater,upKey,sprintKey,jumpKey,forwardImpulse,passenger,boatUuid,boatX,boatY,boatZ,boatStepHorizontal,ghostUuid,ghostX,ghostY,ghostZ,ghostSwimAmount,ghostGrabAmount,stable,stableSamples,consecutive,forwardInput,modeValid,fixtureBoatUuid,targetForwardStep\n");
        Files.writeString(path, clientTicks + "," + mc.level.getGameTime() + "," + ticks + "," + phase
                + "," + speedPosition(mc.player.position()) + "," + playerStep.length() + "," + playerStep.horizontalDistance()
                + "," + mc.player.getPose() + "," + mc.player.isSwimming() + "," + mc.player.isSprinting()
                + "," + mc.player.isEyeInFluid(FluidTags.WATER) + "," + mc.options.keyUp.isDown()
                + "," + mc.options.keySprint.isDown() + "," + mc.options.keyJump.isDown()
                + "," + mc.player.input.forwardImpulse + "," + mc.player.isPassenger()
                + "," + (nativeBoat == null ? "" : nativeBoat.getUUID())
                + "," + speedPosition(nativeBoat == null ? null : nativeBoat.position()) + "," + boatStep.horizontalDistance()
                + "," + (observedGhost == null ? "" : observedGhost.getUUID())
                + "," + speedPosition(observedGhost == null ? null : observedGhost.position())
                + "," + (observedGhost == null ? 0 : observedGhost.waterGhostSwimAmount(1))
                + "," + (observedGhost == null ? 0 : observedGhost.waterGhostGrabAmount(1))
                + "," + stable + "," + run.windowCount + "," + consecutive + "," + forward + "," + modeValid
                + "," + run.fixtureBoatUuid + "," + movement.z + "\n", StandardOpenOption.APPEND);
        run.lastTick = clientTicks; run.lastPlayer = mc.player.position();
        run.lastBoat = nativeBoat == null ? null : nativeBoat.position();
        run.boatUuid = nativeBoat == null ? "" : nativeBoat.getUUID().toString();
        if (finish && ticks >= 5 && !Files.exists(RESULTS.resolve("acceptance-client-speed-" + name + "-finished.signal"))) {
            if (!renderedScreenshot("speed-" + name)) return;
            var report = new JsonObject();
            report.addProperty("units", "consecutive client END tick position differences; no wall-clock speed");
            report.addProperty("firstClientTick", run.firstTick);
            report.addProperty("lastClientTick", clientTicks);
            report.addProperty("chaseTicks", run.chaseTicks);
            report.addProperty("chaseForwardTicks", run.chaseForwardTicks);
            report.addProperty("chaseValidModeTicks", run.chaseModeTicks);
            report.addProperty("nativeTargetSpeedSamples", run.samples);
            report.addProperty("nativeTargetMaxBlocksPerTick", run.chaseMax);
            report.addProperty("nativeTargetMeanBlocksPerTick", run.samples == 0 ? 0 : run.chaseDistance / run.samples);
            report.addProperty("fixtureBoatUuid", run.fixtureBoatUuid);
            report.add("tickSamples", run.tickSamples.deepCopy());
            signal("speed-" + name + "-finished", report.toString());
        }
    }
    private static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static boolean bindLifecycleKey(Minecraft mc) throws Exception {
        if (originalKey != null) return true;
        var ring = RedNailsItem.findWorn(mc.player);
        if (ring.isEmpty()) return false;
        originalKey = RedNailsClient.KEY.getKey();
        require(originalKey.getValue() == GLFW.GLFW_KEY_V, "isolated native lifecycle starts from default V binding");
        // Use the same in-memory remapping and dispatch table as the controls screen.
        // Do not save options or inject the production activation packet directly.
        RedNailsClient.KEY.setKey(LIFECYCLE_KEY);
        KeyMapping.resetMapping();
        var lines = ring.getTooltipLines(mc.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
        var key = RedNailsClient.KEY.getTranslatedKeyMessage();
        String tip = "tooltip." + ModMain.MOD_ID + ".red_nails.";
        String usage = Component.translatable(tip + "usage", key).getString();
        String recall = Component.translatable(tip + "recall", key).getString();
        String oldUsage = Component.translatable(tip + "usage", originalKey.getDisplayName()).getString();
        require(lines.contains(usage) && lines.contains(recall) && !usage.equals(oldUsage) && !lines.contains(oldUsage)
                        && lines.stream().noneMatch(line -> line.contains(tip)),
                "real synchronized item tooltip resolves both remapped activation and recall keys");
        JsonObject proof = new JsonObject();
        proof.addProperty("original", originalKey.getName());
        proof.addProperty("binding", LIFECYCLE_KEY.getName());
        proof.addProperty("tooltip", String.join("\n", lines));
        signal(phase.equals("lifecycle-rejoin") ? "lifecycle-tooltip-rejoin" : "lifecycle-tooltip", proof.toString());
        return true;
    }
    private static void restoreLifecycleKey() {
        if (originalKey == null) return;
        KeyMapping.set(LIFECYCLE_KEY, false);
        RedNailsClient.KEY.setKey(originalKey);
        KeyMapping.resetMapping();
        originalKey = null;
    }
    private static void mappedKey(boolean down) { KeyMapping.set(LIFECYCLE_KEY, down); }
    private static JsonObject lifecycleInput(Minecraft mc) {
        JsonObject proof = new JsonObject();
        proof.addProperty("clientTick", clientTicks);
        proof.addProperty("phaseTicks", ticks);
        proof.addProperty("binding", RedNailsClient.KEY.getKey().getName());
        proof.addProperty("mappedDown", RedNailsClient.KEY.isDown());
        proof.addProperty("screenOpen", mc.screen instanceof InventoryScreen);
        return proof;
    }
    private static boolean recordLifecycleState(Minecraft mc, String name, int expected, String dimension) throws Exception {
        var ring = RedNailsItem.findWorn(mc.player);
        if (ring.isEmpty() || !ring.hasTag() || !ring.getTag().hasUUID("ItemId") || !ring.getTag().hasUUID("Owner")) return false;
        var out = ring.getTag().getList("NailOut", Tag.TAG_COMPOUND);
        int active = 0, worldSummons = 0;
        String ghost = "";
        for (int i = 0; i < out.size(); i++) if (out.getCompound(i).hasUUID("Ghost")) active++;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof WaterGhostSummonEntity summon && !summon.isRemoved()
                    && mc.player.getUUID().equals(summon.getOwnerUUID())) {
                worldSummons++;
                ghost = summon.getUUID().toString();
            }
        }
        if (active != expected || worldSummons != expected || !mc.level.dimension().location().toString().equals(dimension)
                || !mc.level.hasChunkAt(mc.player.blockPosition())) return false;
        JsonObject proof = lifecycleInput(mc);
        proof.addProperty("itemId", ring.getTag().getUUID("ItemId").toString());
        proof.addProperty("owner", ring.getTag().getUUID("Owner").toString());
        proof.addProperty("dimension", mc.level.dimension().location().toString());
        proof.addProperty("active", active);
        proof.addProperty("worldSummons", worldSummons);
        proof.addProperty("ghost", ghost);
        proof.addProperty("health", mc.player.getHealth());
        proof.addProperty("cooling", RedNailsItem.coolingCount(ring));
        proof.addProperty("backlash", mc.player.hasEffect(MobEffects.DARKNESS)
                || mc.player.hasEffect(MobEffects.CONFUSION) || mc.player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN));
        if (!Files.exists(RESULTS.resolve("acceptance-client-" + name + ".signal"))) {
            // A rejoin packet can arrive before the first world frame is drawn.
            // Keep the same nonblank requirement and wait under the stage timeout.
            if (ticks < 20 || !renderedScreenshot(name)) return false;
        }
        signal(name, proof.toString());
        return true;
    }
    private static void lifecycleTick(Minecraft mc) throws Exception {
        if (!bindLifecycleKey(mc)) return;
        if (!phase.equals("lifecycle-gui-open")) {
            mc.setScreen(null);
            mc.mouseHandler.grabMouse();
        }
        if (phase.equals("lifecycle-rebind")) {
            rebindTicks++;
            boolean oldHeld = rebindTicks > 5 && rebindTicks <= 40;
            KeyMapping.set(originalKey, oldHeld);
            if (oldHeld) oldKeyHeldTicks++;
            require(!RedNailsClient.KEY.isDown(), "dispatching original V cannot press the remapped O action");
            JsonObject proof = lifecycleInput(mc);
            proof.addProperty("oldHeldTicks", oldKeyHeldTicks);
            proof.addProperty("complete", rebindTicks >= 45);
            signal("lifecycle-rebind", proof.toString());
        } else if (phase.equals("lifecycle-gui-start")) {
            mappedKey(ticks > 5);
        } else if (phase.equals("lifecycle-gui-open")) {
            if (!(mc.screen instanceof InventoryScreen)) mc.setScreen(new InventoryScreen(mc.player));
            mappedKey(true);
            signal("lifecycle-gui-open", lifecycleInput(mc).toString());
            if (ticks == 10) screenshot("lifecycle-gui-open");
        } else if (phase.equals("lifecycle-gui-closed")) {
            mappedKey(true);
            signal("lifecycle-gui-closed", lifecycleInput(mc).toString());
        } else if (phase.equals("lifecycle-summon") || phase.equals("lifecycle-logout-summon")
                || phase.equals("lifecycle-resummon")) {
            mappedKey(ticks > 5);
            if (phase.equals("lifecycle-resummon")) recordLifecycleState(mc, "lifecycle-resummon-active", 1, "minecraft:the_nether");
        } else if (phase.equals("lifecycle-dimension-ready")) {
            recordLifecycleState(mc, phase, 1, "minecraft:overworld");
        } else if (phase.equals("lifecycle-dimension-observe") || phase.equals("lifecycle-rejoin")) {
            recordLifecycleState(mc, phase, 0, "minecraft:the_nether");
        } else if (phase.equals("lifecycle-logout")) {
            if (!recordLifecycleState(mc, "lifecycle-logout-active", 1, "minecraft:the_nether")) return;
            reset(); restoreLifecycleKey();
            // A genuine disconnect lets the dedicated server perform its ordinary
            // logout/save path. The runner launches a second client for rejoin.
            mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
            phase = "lifecycle-disconnected";
            Files.writeString(RESULTS.resolve("acceptance-client-lifecycle-logout.pass"),
                    "Actual client disconnected from the live server with an active red-nails summon; title screen reached.\n");
        } else if (phase.equals("lifecycle-final-recall")) {
            mc.options.keyShift.setDown(true);
            mappedKey(ticks > 5);
        }
    }
    private static void screenshot(String name) throws Exception {
        if (!renderedScreenshot(name)) throw new AssertionError("nonblank native OpenGL frame required");
    }
    private static boolean renderedScreenshot(String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            java.util.HashSet<Integer> colors = new java.util.HashSet<>();
            for (int y = 0; y < image.getHeight(); y += 8) for (int x = 0; x < image.getWidth(); x += 8) colors.add(image.getPixelRGBA(x, y));
            if (colors.size() <= 100) return false;
            image.writeToFile(RESULTS.resolve("acceptance-" + name + ".png"));
            return true;
        }
    }
}
