package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.shadow.ShadowLight;
import com.zeropointsix.eraser.shadow.ShadowTanglerEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class ShadowTanglerModel extends GeoModel<ShadowTanglerEntity> {
    @Override public ResourceLocation getModelResource(ShadowTanglerEntity entity) {
        return ShadowTanglerEntity.id("geo/shadow_tangler.geo.json");
    }
    @Override public ResourceLocation getAnimationResource(ShadowTanglerEntity entity) {
        return ShadowTanglerEntity.id("animations/shadow_tangler.animation.json");
    }
    @Override public ResourceLocation getTextureResource(ShadowTanglerEntity entity) {
        return ShadowTanglerEntity.id(entity.getLightTier() == ShadowLight.BRIGHT
                ? "textures/entity/shadow_tangler_crystal.png" : "textures/entity/shadow_tangler.png");
    }
}
