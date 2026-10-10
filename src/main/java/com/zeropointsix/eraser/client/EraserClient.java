package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.eraser.EraserMode;
import com.zeropointsix.eraser.eraser.EraserNetwork;
import com.zeropointsix.eraser.eraser.EraserPlacement;
import com.zeropointsix.eraser.eraser.EraserStroke;
import com.zeropointsix.eraser.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class EraserClient {
    public static final KeyMapping CYCLE = new KeyMapping("key.sound_isolating_eraser.eraser_mode",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
            "key.categories.sound_isolating_eraser");

    private static InteractionHand hand(Minecraft mc) {
        if (mc.player == null || mc.screen != null || !mc.player.isAlive() || mc.player.isSpectator()) return null;
        for (InteractionHand hand : InteractionHand.values()) {
            if (mc.player.getItemInHand(hand).is(ModItems.SOUND_ISOLATING_ERASER.get())) return hand;
        }
        return null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (CYCLE.consumeClick()) {
            InteractionHand hand = hand(mc);
            if (hand == null || mc.isPaused() || !mc.isWindowActive()) continue;
            // Predict only the held stack; the server validates the hand and syncs its authoritative tag.
            var stack = mc.player.getItemInHand(hand);
            EraserMode.read(stack).next().write(stack);
            EraserNetwork.CHANNEL.sendToServer(new EraserNetwork.Cycle(hand));
        }
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        InteractionHand hand = hand(mc);
        if (hand == null || mc.options.hideGui) return;
        Component label = EraserMode.read(mc.player.getItemInHand(hand)).label();
        event.getGuiGraphics().drawCenteredString(mc.font, label,
                mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2 + 18, 0xFFFFFF);
    }

    @SubscribeEvent
    public static void preview(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        InteractionHand hand = hand(mc);
        if (hand == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || hit.getDirection() == Direction.DOWN) return;
        var stack = mc.player.getItemInHand(hand);
        var bases = EraserMode.read(stack).bases(hit.getBlockPos().relative(hit.getDirection()), mc.player.getDirection());
        boolean valid = EraserPlacement.fits(mc.level, mc.player, stack, bases, hit.getDirection());
        float g = valid ? 1F : 0.2F, b = valid ? 1F : 0.2F;
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        try {
            for (var base : bases) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(base).expandTowards(0, EraserPlacement.height() - 1, 0),
                        1F, g, b, 0.3F);
                if (hit.getDirection() == Direction.UP) {
                    Vec3 center = Vec3.atBottomCenterOf(base).add(0, 0.015, 0);
                    EraserStroke stroke = EraserStroke.at(bases, base);
                    if (stroke == EraserStroke.POINT) line(pose, lines, center.add(-0.3,0,0), center.add(0.3,0,0), g,b);
                    for (int i = 0; i < 8; i++) {
                        if ((stroke.mask & (1 << i)) != 0) line(pose, lines, center,
                                center.add(EraserStroke.PORTS[i][0] * 0.5, 0, EraserStroke.PORTS[i][1] * 0.5), g,b);
                    }
                } else {
                    Direction right = hit.getDirection().getClockWise();
                    Vec3 center = Vec3.atCenterOf(base).add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(-0.485));
                    Vec3 offset = Vec3.atLowerCornerOf(right.getNormal()).scale(0.5);
                    line(pose, lines, center.subtract(offset), center.add(offset), g,b);
                }
            }
            buffers.endBatch(RenderType.lines());
        } finally { pose.popPose(); }
    }

    private static void line(PoseStack pose, VertexConsumer vertices, Vec3 from, Vec3 to, float g, float b) {
        Vec3 normal = to.subtract(from).normalize();
        for (Vec3 point : new Vec3[]{from, to}) {
            vertices.vertex(pose.last().pose(), (float) point.x, (float) point.y, (float) point.z)
                    .color(1F,g,b,1F).normal(pose.last().normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
        }
    }

    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(CYCLE); }
    }

    private EraserClient() { }
}
