package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.neon.NeonTumorEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class NeonTumorModel extends GeoModel<NeonTumorEntity> {
    private static final String[] COLORS = {"red", "blue", "purple"};
    private static final ResourceLocation MODEL = new ResourceLocation(ModMain.MOD_ID, "geo/neon_tumor.geo.json");
    private static final ResourceLocation ANIMATION = new ResourceLocation(ModMain.MOD_ID, "animations/neon_tumor.animation.json");

    @Override public ResourceLocation getModelResource(NeonTumorEntity entity) { return MODEL; }
    @Override public ResourceLocation getAnimationResource(NeonTumorEntity entity) { return ANIMATION; }
    @Override public ResourceLocation getTextureResource(NeonTumorEntity entity) {
        return new ResourceLocation(ModMain.MOD_ID, "textures/entity/neon_tumor_" + COLORS[entity.getVariant()] + ".png");
    }
    public ResourceLocation getGlowTexture(NeonTumorEntity entity) {
        return new ResourceLocation(ModMain.MOD_ID, "textures/entity/neon_tumor_" + COLORS[entity.getVariant()] + "_glowmask.png");
    }
}
