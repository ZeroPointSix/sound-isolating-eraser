package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zeropointsix.eraser.gravity.GravityConfig;
import com.zeropointsix.eraser.gravity.GravityEquipment;
import com.zeropointsix.eraser.gravity.GravityGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public final class GravityWorldRenderer {
    public static void render(RenderLevelStageEvent event, boolean aiming) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !GravityEquipment.isEquipped(mc.player)) return;
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        try {
            if (aiming) {
                AABB box = GravityGeometry.bounds(GravityClient.target(mc, event.getPartialTick()), GravityConfig.HEIGHT.get());
                fill(pose, buffers.getBuffer(GravityRenderTypes.FILL), box, 0.065F);
                LevelRenderer.renderLineBox(pose, buffers.getBuffer(GravityRenderTypes.LINES), box, 0.88F, 1F, 0.97F, 0.8F);
            }
            for (Entity entity : GravitySense.targets()) {
                if (entity instanceof LivingEntity || entity.isRemoved()) continue;
                float pt = event.getPartialTick();
                AABB box = entity.getBoundingBox().move(
                        Mth.lerp(pt, entity.xOld, entity.getX()) - entity.getX(),
                        Mth.lerp(pt, entity.yOld, entity.getY()) - entity.getY(),
                        Mth.lerp(pt, entity.zOld, entity.getZ()) - entity.getZ());
                LevelRenderer.renderLineBox(pose, buffers.getBuffer(GravityRenderTypes.LINES), box, 1F, 1F, 1F, 0.95F);
            }
            buffers.endBatch(GravityRenderTypes.FILL);
            buffers.endBatch(GravityRenderTypes.LINES);
        } finally {
            pose.popPose();
        }
    }

    private static void fill(PoseStack pose, VertexConsumer vertices, AABB box, float alpha) {
        float x0 = (float) box.minX, x1 = (float) box.maxX;
        float y0 = (float) box.minY, y1 = (float) box.maxY;
        float z0 = (float) box.minZ, z1 = (float) box.maxZ;
        float[][] faces = {
                {x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0},
                {x0,y0,z1, x0,y1,z1, x1,y1,z1, x1,y0,z1},
                {x0,y0,z0, x0,y1,z0, x0,y1,z1, x0,y0,z1},
                {x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0},
                {x0,y0,z0, x0,y0,z1, x1,y0,z1, x1,y0,z0},
                {x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1}
        };
        for (float[] face : faces) {
            for (int i = 0; i < 12; i += 3) {
                vertices.vertex(pose.last().pose(), face[i], face[i+1], face[i+2])
                        .color(0.88F, 1F, 0.97F, alpha).endVertex();
            }
        }
    }

    private GravityWorldRenderer() { }
}
