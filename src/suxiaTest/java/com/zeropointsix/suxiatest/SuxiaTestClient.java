package com.zeropointsix.suxiatest;

import com.zeropointsix.eraser.client.SuxiaModel;
import com.zeropointsix.eraser.client.SuxiaRenderer;
import com.zeropointsix.eraser.client.SuxiaClient;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.suxia.SuxiaEntity;
import com.zeropointsix.eraser.suxia.SuxiaSummonEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "suxia_qa", value = Dist.CLIENT)
public final class SuxiaTestClient {
    private static final boolean IDLE_ONLY = Boolean.getBoolean("suxia.qa.idleOnly");
    private static net.minecraft.world.phys.Vec3 idleWalkStart;
    private static final Path RESULTS = Path.of(System.getProperty("suxia.qa.results"));
    private static String phase = "";
    private static int ticks;
    private static boolean failed, captured;
    private static final HashMap<Integer, float[]> poses = new HashMap<>();
    private static final HashMap<Integer, Integer> renderedFrames = new HashMap<>();
    private static int renderFrame;
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void fail(Throwable error) {
        failed = true; error.printStackTrace();
        var mc = Minecraft.getInstance();
        mc.options.keyUp.setDown(false);
        try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            screenshot.writeToFile(RESULTS.resolve("client-failure.png"));
            if (mc.player != null) Files.writeString(RESULTS.resolve("client-failure-camera.txt"),
                    "phase=" + phase + " player=" + mc.player.position()
                            + " yaw=" + mc.player.getYRot() + " pitch=" + mc.player.getXRot() + "\n");
        } catch (Exception captureError) {
            error.addSuppressed(captureError);
        }
        // Notify the runner only after evidence is saved; it terminates this JVM on failure.
        try { Files.writeString(RESULTS.resolve("client.failed"), error.toString()); } catch (Exception ignored) { }
    }
    private static boolean hasRenderedPose(SuxiaEntity mob) {
        var pose = poses.get(mob.getId());
        return pose != null && pose[2] > 5 && mob.tickCount - pose[3] > 40
                && renderedFrames.getOrDefault(mob.getId(), -1) == renderFrame;
    }
    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("SUXIA_QA:")) {
            phase = text.substring(9); ticks = 0; captured = false;
            Minecraft.getInstance().options.keyUp.setDown(false);
            poses.clear();
            renderedFrames.clear();
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || phase.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            ticks++;
            if (ticks == 1) { mc.setScreen(null); mc.mouseHandler.grabMouse(); }
            if (IDLE_ONLY) {
                idleTick(mc);
                return;
            }
            // F1 only for creature evidence; watch/cooldown captures retain their HUD.
            mc.options.hideGui = phase.startsWith("hostile-") || phase.startsWith("restored");
            if (phase.startsWith("hostile-") || phase.startsWith("restored")) {
                // Follow the live entity, including its lower sleeping pose and natural movement.
                for (var entity : mc.level.entitiesForRendering()) if (entity instanceof SuxiaEntity mob) {
                    var delta = mob.position().add(0, mob.getBbHeight() * .5, 0)
                            .subtract(mc.player.getEyePosition());
                    float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
                    float pitch = (float) -Math.toDegrees(Math.atan2(delta.y,
                            Math.hypot(delta.x, delta.z)));
                    mc.player.setYRot(yaw);
                    mc.player.setYHeadRot(yaw);
                    mc.player.setXRot(pitch);
                    mc.player.yRotO = yaw;
                    mc.player.xRotO = pitch;
                    break;
                }
            }
            if (captured && phase.equals("gallery")) {
                phase = "await-summon";
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            } else if (captured && (phase.equals("summoned") || phase.equals("second-summoned"))) {
                phase = "await-recall";
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            } else if (captured && phase.equals("recalled")) {
                mc.player.getInventory().selected = 1;
                phase = "await-second";
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            } else if (captured && phase.equals("second-recalled")) {
                phase = "await-cooldown-rejection";
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            } else if (captured && phase.equals("cooldown-rejected")) {
                mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); phase = "";
                Files.writeString(RESULTS.resolve("client-logout.pass"), "Actual client disconnected with cooling watch.\n");
            }
        } catch (Throwable error) { fail(error); }
    }
    private static void idleTick(Minecraft mc) throws Exception {
        mc.options.hideGui = true;
        // Keep LocalPlayer's ordinary position packets active during the native test.
        mc.setCameraEntity(mc.player);
        mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        if (phase.equals("idle-ready") && ticks == 1) {
            mc.player.setYRot(0); mc.player.setYHeadRot(0); mc.player.setXRot(-20);
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(0, -20, mc.player.onGround()));
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        } else if (phase.equals("idle-turning")) {
            mc.player.setYRot(90); mc.player.setYHeadRot(90); mc.player.setXRot(35);
            if (ticks == 1) mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(90, 35, mc.player.onGround()));
        } else if (phase.equals("idle-walking")) {
            mc.setCameraEntity(mc.player);
            mc.player.setYRot(90); mc.player.setYHeadRot(90); mc.player.setXRot(35);
            if (ticks == 1) idleWalkStart = mc.player.position();
            if (mc.player.position().subtract(idleWalkStart).horizontalDistance() < 4) {
                mc.options.keyUp.setDown(true);
            } else {
                mc.options.keyUp.setDown(false);
                Files.writeString(RESULTS.resolve("client-idle-walk-stopped.signal"),
                        "Actual forward key movement from=" + idleWalkStart + " to=" + mc.player.position() + "\n");
            }
            return;
        } else if (phase.equals("idle-recalling") && ticks == 1) {
            mc.setCameraEntity(mc.player);
            Files.writeString(RESULTS.resolve("client-idle-recall-input.signal"), "Actual item-use input after walking follow.\n");
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
    }

    private static void renderIdle() throws Exception {
        if (!(phase.equals("idle-stationary") || phase.equals("idle-turned")
                || phase.equals("idle-followed") || phase.equals("idle-recalled"))) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        if (phase.equals("idle-recalled")) {
            var watch = mc.player.getMainHandItem();
            require(watch.is(ModItems.SUXIA_POCKET_WATCH.get()) && watch.hasTag()
                    && !watch.getTag().getBoolean("Open") && watch.getTag().getInt("CooldownRemaining") > 0,
                    "actual client sees recalled watch with its cooldown");
            for (var entity : mc.level.entitiesForRendering()) {
                require(!(entity instanceof SuxiaSummonEntity summon)
                        || !mc.player.getUUID().equals(summon.getOwnerUUID()), "recalled summon absent on client");
            }
            try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                screenshot.writeToFile(RESULTS.resolve(phase + ".png"));
            }
            Files.writeString(RESULTS.resolve("client-idle-recalled.pass"), "Actual client sees same watch closed, cooldown active and summon absent.\n");
            captured = true;
            return;
        }
        SuxiaSummonEntity summon = null;
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof SuxiaSummonEntity candidate) summon = candidate;
        require(summon != null && mc.player.getUUID().equals(summon.getOwnerUUID()), "actual owner-bound summon synchronized");
        var pose = poses.get(summon.getId());
        boolean ready = pose != null && pose[2] > 5 && summon.tickCount - pose[3] > 40
                && renderedFrames.getOrDefault(summon.getId(), -1) == renderFrame;
        if (ticks < 300 && !ready) return;
        require(ready, "idle summon actually rendered in current frame");
        var behind = mc.player.position().subtract(net.minecraft.world.phys.Vec3.directionFromRotation(0, mc.player.getYRot()).scale(2));
        String evidence = "phase=" + phase + " owner=" + mc.player.position() + " yaw=" + mc.player.getYRot()
                + " pitch=" + mc.player.getXRot() + " summon=" + summon.position() + " headYaw=" + summon.getYHeadRot()
                + " pitch=" + summon.getXRot() + " bodyYaw=" + summon.yBodyRot + " entityYaw=" + summon.getYRot()
                + " modelHeadPitch=" + pose[0] + " renderFrame=" + renderFrame + "\n";
        Files.writeString(RESULTS.resolve(phase + "-camera.txt"), evidence);
        require(summon.position().subtract(behind).horizontalDistanceSqr() <= 0.75 * 0.75
                && Math.abs(summon.getY() - mc.player.getY()) < 0.5, "client sees summon two blocks behind owner");
        require(Math.abs(net.minecraft.util.Mth.wrapDegrees(summon.getYHeadRot() - mc.player.getYRot())) < 5
                && Math.abs(summon.getXRot() - mc.player.getXRot()) < 5, "client receives owner-mirrored head yaw and pitch: " + evidence);
        require(Math.abs(pose[0] - (-0.523599F + mc.player.getXRot() * net.minecraft.util.Mth.DEG_TO_RAD * 0.5F)) < 0.05,
                "rendered head model uses synchronized owner pitch");
        try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var colors = new HashSet<Integer>();
            for (int x = 0; x < screenshot.getWidth(); x += 4) for (int y = 0; y < screenshot.getHeight(); y += 4) colors.add(screenshot.getPixelRGBA(x, y));
            require(colors.size() > 100, "nonblank actual Minecraft framebuffer");
            screenshot.writeToFile(RESULTS.resolve(phase + ".png"));
        }
        Files.writeString(RESULTS.resolve("client-" + phase + ".pass"), evidence);
        captured = true;
    }

    @SubscribeEvent public static void model(RenderLivingEvent.Post<?, ?> event) {
        if (IDLE_ONLY) {
            if (!failed && event.getEntity() instanceof SuxiaSummonEntity summon
                    && event.getRenderer().getModel() instanceof SuxiaModel<?> model) {
                var head = model.root().getChild("body").getChild("neck").getChild("head");
                float[] pose = poses.computeIfAbsent(summon.getId(), id -> new float[]{head.xRot, 0, 0, summon.tickCount});
                pose[0] = head.xRot; pose[2]++;
                renderedFrames.put(summon.getId(), renderFrame);
            }
            return;
        }
        if (failed || !(phase.equals("gallery") || phase.startsWith("hostile-") || phase.startsWith("restored"))
                || !(event.getEntity() instanceof SuxiaEntity mob)
                || !(event.getRenderer().getModel() instanceof SuxiaModel<?> model)) return;
        try {
            var body = model.root().getChild("body");
            // Start the observation window when this entity is actually rendered, not at the phase packet.
            float[] range = poses.computeIfAbsent(mob.getId(), id -> new float[]{body.zRot, body.zRot, 0, mob.tickCount});
            range[0] = Math.min(range[0], body.zRot); range[1] = Math.max(range[1], body.zRot); range[2]++;
            renderedFrames.put(mob.getId(), renderFrame);
            if (!hasRenderedPose(mob)) return;
            if (mob.isCrawling()) require(body.xRot > 1.3F, "rendered crawl body leans forward");
            if (mob.isPlayingDead()) require(model.root().xRot > 1.5F, "rendered play-dead body lies flat");
            if (phase.endsWith("awake")) require(model.root().xRot < .1F, "rendered wake pose rises from the ground");
        } catch (Throwable error) { fail(error); }
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) { renderFrame++; return; }
        if (IDLE_ONLY && !failed && phase.isEmpty()) {
            var mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null && mc.screen == null
                    && mc.level.hasChunkAt(mc.player.blockPosition())
                    && mc.player.distanceToSqr(0.5, 65, -11.5) < 0.25
                    && mc.player.getMainHandItem().is(ModItems.SUXIA_POCKET_WATCH.get())) {
                try {
                    Files.writeString(RESULTS.resolve("client-idle-ready.signal"),
                            "Actual world, vanilla spawn and watch inventory rendered; frame=" + renderFrame + "\n");
                } catch (Throwable error) { fail(error); }
            }
            return;
        }
        if (event.phase != TickEvent.Phase.END || failed || captured || ticks < 100) return;
        if (IDLE_ONLY) {
            try { renderIdle(); } catch (Throwable error) { fail(error); }
            return;
        }
        if (!java.util.Set.of("gallery", "summoned", "recalled", "second-summoned", "second-recalled",
                "cooldown-rejected", "rejoined", "hostile-sleep", "hostile-awake", "restored", "restored-awake").contains(phase)) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        try {
            var watch = mc.player.getMainHandItem();
            require(watch.is(ModItems.SUXIA_POCKET_WATCH.get()), "watch synchronized in main hand");
            var tooltip = watch.getTooltipLines(mc.player, TooltipFlag.NORMAL);
            require(tooltip.size() > 2 && tooltip.get(1).getString().equals("\u73cd\u8d35\u56de\u5fc6"), "precious-memory label immediately below item name");
            ItemStack closed = new ItemStack(ModItems.SUXIA_POCKET_WATCH.get());
            ItemStack open = closed.copy(); open.getOrCreateTag().putBoolean("Open", true);
            var closedModel = mc.getItemRenderer().getModel(closed, mc.level, mc.player, 0);
            var openModel = mc.getItemRenderer().getModel(open, mc.level, mc.player, 0);
            require(closedModel != openModel, "open predicate selects different baked item model");
            require(!closedModel.getParticleIcon().contents().name().getPath().contains("missing")
                    && !openModel.getParticleIcon().contents().name().getPath().contains("missing"), "both item textures loaded");
            if (phase.equals("gallery")) {
                int count = 0;
                boolean allRendered = true;
                for (var entity : mc.level.entitiesForRendering()) if (entity instanceof SuxiaEntity mob) {
                    count++;
                    allRendered &= hasRenderedPose(mob);
                    require(mc.getEntityRenderDispatcher().getRenderer(mob) instanceof SuxiaRenderer<?>, "shared renderer registered");
                }
                boolean idleAnimated = poses.values().stream().anyMatch(v -> v[2] > 5 && v[1] - v[0] > .01);
                boolean ready = count == 3 && poses.size() == 3 && allRendered;
                if (ticks < 600 && (!ready || !idleAnimated)) return;
                require(ready, "all three poses actually rendered");
                require(idleAnimated, "standing idle animation changes rendered transforms");
                for (String name : new String[]{"suxia", "suxia_angry", "suxia_eyes"}) {
                    mc.getResourceManager().getResourceOrThrow(new ResourceLocation("sound_isolating_eraser", "textures/entity/suxia/" + name + ".png"));
                }
            } else if (phase.equals("summoned") || phase.equals("second-summoned")) {
                require(watch.getTag().getBoolean("Open") && watch.isBarVisible() && watch.getBarWidth() > 0, "real active watch and duration bar");
                boolean rendered = false;
                for (var entity : mc.level.entitiesForRendering()) if (entity instanceof SuxiaSummonEntity summon) {
                    rendered = mc.getEntityRenderDispatcher().getRenderer(summon) instanceof SuxiaRenderer<?>;
                }
                require(rendered, "summon synchronized and rendered by same model");
                if (phase.equals("second-summoned")) {
                    require(SuxiaClient.cooldownFraction(mc.player.getInventory().getItem(0)) > 0
                            && SuxiaClient.cooldownFraction(watch) == 0, "only first watch has a cooldown mask while second is active");
                }
            } else {
                boolean cooling = watch.getTag().getInt("CooldownRemaining") > 0;
                require(!watch.getTag().getBoolean("Open") && (SuxiaClient.cooldownFraction(watch) > 0) == cooling
                        && !mc.player.getCooldowns().isOnCooldown(watch.getItem()), "closed watch retains individual cooldown after " + phase);
                if (phase.equals("recalled") || phase.equals("second-recalled") || phase.equals("cooldown-rejected")) {
                    require(cooling, "newly recalled watch still cooling");
                }
                if (phase.startsWith("hostile-") || phase.startsWith("restored")) {
                    boolean found = false;
                    for (var entity : mc.level.entitiesForRendering()) if (entity instanceof SuxiaEntity mob) {
                        found = true;
                        if (ticks < 600 && !hasRenderedPose(mob)) return;
                        require(hasRenderedPose(mob),
                                "hostile actually rendered in " + phase + ": id=" + mob.getId()
                                        + " position=" + mob.position() + " rendered=" + poses.keySet());
                        require(mob.isPlayingDead() != phase.endsWith("awake"), "live sleep/wake pose synchronized in " + phase);
                    }
                    if (ticks < 600 && !found) return;
                    require(found, "hostile visible in " + phase);
                }
            }
            try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                var colors = new HashSet<Integer>();
                for (int x = 0; x < screenshot.getWidth(); x += 4) for (int y = 0; y < screenshot.getHeight(); y += 4) colors.add(screenshot.getPixelRGBA(x, y));
                require(colors.size() > 100, "nonblank actual Minecraft framebuffer");
                screenshot.writeToFile(RESULTS.resolve(phase + ".png"));
            }
            Files.writeString(RESULTS.resolve(phase + "-camera.txt"), "phase=" + phase + " player=" + mc.player.position()
                    + " yaw=" + mc.player.getYRot() + " pitch=" + mc.player.getXRot() + " renderFrame=" + renderFrame
                    + " entityFrames=" + renderedFrames + "\n");
            Files.writeString(RESULTS.resolve("client-" + phase + ".pass"), "Live Forge client rendering, synchronized item state and localization verified: " + phase + "\n");
            captured = true;
        } catch (Throwable error) { fail(error); }
    }
}
