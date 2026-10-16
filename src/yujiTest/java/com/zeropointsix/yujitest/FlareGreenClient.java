package com.zeropointsix.yujitest;

import com.zeropointsix.eraser.yuji.flare.FallingSupplyCrate;
import com.zeropointsix.eraser.yuji.flare.FlareContent;
import com.zeropointsix.eraser.yuji.flare.FlareProjectile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "yuji_qa", value = Dist.CLIENT)
public final class FlareGreenClient {
    private static final Path RESULTS = Path.of(System.getProperty("yuji.qa.results"));
    private static final Set<String> captured = new HashSet<>();
    private static String phase = "";
    private static int ticks, frames;
    private static BlockPos landing;
    private static Entity subject;
    private static boolean fired, opened, failed;

    private static boolean enabled() { return Boolean.getBoolean("flare.qa.green"); }
    private static void fail(Throwable failure) {
        failed = true; failure.printStackTrace();
        try { Files.writeString(RESULTS.resolve("client-green.failed"), failure.toString()); } catch (Exception ignored) { }
    }
    @SubscribeEvent public static void message(ClientChatReceivedEvent event) {
        if (!enabled() || !event.getMessage().getString().startsWith("FLARE_GREEN:")) return;
        String[] fields = event.getMessage().getString().split(":");
        phase = fields[1]; ticks = 0; frames = 0;
        if (fields.length == 5) landing = new BlockPos(Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4]));
        event.setCanceled(true);
    }

    private static void look(Vec3 target) {
        var player = Minecraft.getInstance().player;
        Vec3 delta = target.subtract(player.getEyePosition());
        player.setYRot((float) (Mth.atan2(delta.z, delta.x) * 180 / Math.PI) - 90);
        player.setXRot((float) -(Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * 180 / Math.PI));
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!enabled() || failed || phase.isEmpty() || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameMode == null) return;
        try {
            ticks++;
            if (phase.equals("armed") && ticks >= 60 && !fired && mc.screen == null
                    && mc.player.getMainHandItem().is(FlareContent.GUN.get())
                    && mc.player.getOffhandItem().is(FlareContent.GREEN.get())) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.swing(InteractionHand.MAIN_HAND); fired = true;
            }
            subject = null;
            for (Entity entity : mc.level.entitiesForRendering())
                if (phase.equals("flight") && entity instanceof FlareProjectile flare && flare.color() == 2
                        || phase.equals("descent") && entity instanceof FallingSupplyCrate) { subject = entity; break; }
            if (subject != null) look(subject.position().add(0, .4, 0));
            if (phase.equals("landed") && !opened) {
                look(Vec3.atCenterOf(landing));
                if (captured.contains("landed")) {
                    var hit = new BlockHitResult(Vec3.atCenterOf(landing), Direction.UP, landing, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                    mc.player.swing(InteractionHand.MAIN_HAND); opened = true; ticks = 0;
                }
            }
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (!enabled() || failed || phase.isEmpty() || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ++frames < 10) return;
        try {
            String capture = null;
            if ((phase.equals("flight") || phase.equals("descent")) && subject != null && ticks >= 12
                    && mc.screen == null && mc.player.hasLineOfSight(subject)) capture = phase;
            if (phase.equals("landed") && !opened && ticks >= 20 && mc.screen == null
                    && mc.level.getBlockState(landing).is(FlareContent.CRATE.get())) capture = "landed";
            if (opened && ticks >= 20 && mc.screen instanceof ContainerScreen
                    && mc.player.containerMenu.slots.stream().limit(27).anyMatch(slot -> slot.hasItem())) capture = "open";
            if (capture == null || captured.contains(capture)) return;
            try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                shot.writeToFile(RESULTS.resolve("green-" + capture + ".png"));
            }
            Files.writeString(RESULTS.resolve("client-green-" + capture + ".pass"),
                    "phase=" + capture + " frames=" + frames + " ticks=" + ticks
                            + " camera=" + mc.player.position() + " subject=" + (subject == null ? landing : subject.position()) + "\n");
            System.out.println("FLARE_GREEN_CAPTURE " + capture);
            captured.add(capture);
            if (captured.containsAll(Set.of("flight", "descent", "landed", "open")))
                Files.writeString(RESULTS.resolve("client-green.pass"), "actual useItem/useItemOn; four native framebuffer captures; nonempty chest menu\n");
        } catch (Throwable failure) { fail(failure); }
    }
}
