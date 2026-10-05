package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class GravityFieldRenderer extends EntityRenderer<GravityFieldEntity> {
    public GravityFieldRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void render(GravityFieldEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        // GravityWorldRenderer draws all public bounds after world composition.
    }

    @Override
    public ResourceLocation getTextureLocation(GravityFieldEntity entity) {
        return new ResourceLocation(ModMain.MOD_ID, "textures/item/gravity_jade_pendant.png");
    }
}
