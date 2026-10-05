package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class GravityFieldRenderer extends EntityRenderer<GravityFieldEntity> {
    public GravityFieldRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void render(GravityFieldEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(GravityRenderTypes.LINES),
                entity.fieldBounds().move(-entity.getX(), -entity.getY(), -entity.getZ()),
                0.85F, 0.93F, 0.91F, 0.22F);
    }

    @Override
    public ResourceLocation getTextureLocation(GravityFieldEntity entity) {
        return new ResourceLocation(ModMain.MOD_ID, "textures/item/gravity_jade_pendant.png");
    }
}
