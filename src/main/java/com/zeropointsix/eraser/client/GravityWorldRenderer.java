package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zeropointsix.eraser.gravity.GravityEquipment;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public final class GravityWorldRenderer {
    public static void render(RenderLevelStageEvent event, PoseStack pose) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean equipped = GravityEquipment.isEquipped(mc.player);
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        float[] color = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        try {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof GravityFieldEntity field && !field.isRemoved()
                        && event.getFrustum().isVisible(field.fieldBounds())) {
                    cylinder(pose, buffers.getBuffer(GravityRenderTypes.LINES), field);
                }
            }
            for (Entity entity : GravitySense.targets()) {
                if (!equipped) break;
                if (entity instanceof LivingEntity || entity.isRemoved()) continue;
                float pt = event.getPartialTick();
                AABB box = entity.getBoundingBox().move(
                        Mth.lerp(pt, entity.xOld, entity.getX()) - entity.getX(),
                        Mth.lerp(pt, entity.yOld, entity.getY()) - entity.getY(),
                        Mth.lerp(pt, entity.zOld, entity.getZ()) - entity.getZ());
                LevelRenderer.renderLineBox(pose, buffers.getBuffer(GravityRenderTypes.LINES), box, 1F, 1F, 1F, 0.95F);
            }
            buffers.endBatch(GravityRenderTypes.LINES);
        } finally {
            pose.popPose();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }

    private static void cylinder(PoseStack pose, VertexConsumer vertices, GravityFieldEntity field) {
        AABB box = field.fieldBounds();
        double ground = Mth.clamp(field.getY() + 0.05, box.minY, box.maxY);
        for (int i = 0; i < 96; i++) {
            double a = i * Math.PI / 48, b = (i + 1) * Math.PI / 48;
            double x0 = Mth.clamp(field.getX() + Math.cos(a) * field.radius(), box.minX, box.maxX);
            double z0 = Mth.clamp(field.getZ() + Math.sin(a) * field.radius(), box.minZ, box.maxZ);
            double x1 = Mth.clamp(field.getX() + Math.cos(b) * field.radius(), box.minX, box.maxX);
            double z1 = Mth.clamp(field.getZ() + Math.sin(b) * field.radius(), box.minZ, box.maxZ);
            line(pose, vertices, x0, ground, z0, x1, ground, z1, 0.70F);
            line(pose, vertices, x0, box.minY, z0, x1, box.minY, z1, 0.25F);
            line(pose, vertices, x0, box.maxY, z0, x1, box.maxY, z1, 0.25F);
            if (i % 8 == 0) line(pose, vertices, x0, box.minY, z0, x0, box.maxY, z0, 0.22F);
        }
    }

    private static void line(PoseStack pose, VertexConsumer vertices, double x0, double y0, double z0,
            double x1, double y1, double z1, float alpha) {
        Vec3 direction = new Vec3(x1 - x0, y1 - y0, z1 - z0);
        if (direction.lengthSqr() < 1.0e-10) return;
        direction = direction.normalize();
        vertex(pose, vertices, x0, y0, z0, direction, alpha);
        vertex(pose, vertices, x1, y1, z1, direction, alpha);
    }

    private static void vertex(PoseStack pose, VertexConsumer vertices, double x, double y, double z,
            Vec3 direction, float alpha) {
        vertices.vertex(pose.last().pose(), (float) x, (float) y, (float) z)
                .color(0.85F, 0.93F, 0.91F, alpha)
                .normal(pose.last().normal(), (float) direction.x, (float) direction.y, (float) direction.z).endVertex();
    }

    private GravityWorldRenderer() { }
}
