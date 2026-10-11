package com.zeropointsix.eraser.eternalnight;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class CityClientRuntime {
    private static ResourceKey<Level> previous;
    private static int fadeTicks;
    private static java.util.List<net.minecraft.core.BlockPos> exits = java.util.List.of();

    public static void setExits(java.util.List<net.minecraft.core.BlockPos> doors) { exits = java.util.List.copyOf(doors); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) { previous = null; fadeTicks = 0; exits = java.util.List.of(); return; }
        ResourceKey<Level> dimension = client.level.dimension();
        if (!dimension.equals(previous) && dimension.equals(EternalNightContent.LEVEL)) fadeTicks = 40;
        else if (fadeTicks > 0) fadeTicks--;
        previous = dimension;
        if (!dimension.equals(EternalNightContent.LEVEL)) exits = java.util.List.of();
    }

    @SubscribeEvent public static void renderExits(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || client.level == null || !EternalNightContent.isCity(client.level) || exits.isEmpty()) return;
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var matrix = pose.last().pose();
        var tessellator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        var buffer = tessellator.getBuilder();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        for (var pos : exits) {
            var state = client.level.getBlockState(pos);
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                    || !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) continue;
            var facing = state.getValue(net.minecraft.world.level.block.DoorBlock.FACING);
            float dx = facing.getStepZ() * 0.48F, dz = -facing.getStepX() * 0.48F;
            float x = pos.getX() + 0.5F, y = pos.getY(), z = pos.getZ() + 0.5F;
            buffer.vertex(matrix, x - dx, y, z - dz).color(255, 255, 255, 240).endVertex();
            buffer.vertex(matrix, x + dx, y, z + dz).color(255, 255, 255, 240).endVertex();
            buffer.vertex(matrix, x + dx, y + 2, z + dz).color(255, 255, 255, 240).endVertex();
            buffer.vertex(matrix, x - dx, y + 2, z - dz).color(255, 255, 255, 240).endVertex();
        }
        tessellator.end();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        pose.popPose();
    }

    @SubscribeEvent public static void fade(RenderGuiEvent.Post event) {
        if (fadeTicks <= 0) return;
        Minecraft client = Minecraft.getInstance();
        int alpha = Math.min(255, fadeTicks * 255 / 40);
        event.getGuiGraphics().fill(0, 0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), alpha << 24);
    }
    private CityClientRuntime() { }
}
