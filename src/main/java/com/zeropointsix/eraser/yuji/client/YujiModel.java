package com.zeropointsix.eraser.yuji.client;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiMonster;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class YujiModel extends GeoModel<YujiMonster> {
    @Override public ResourceLocation getModelResource(YujiMonster entity) { return YujiContent.id("geo/" + entity.kind().id() + ".geo.json"); }
    @Override public ResourceLocation getAnimationResource(YujiMonster entity) { return YujiContent.id("animations/" + entity.kind().id() + ".animation.json"); }
    @Override public ResourceLocation getTextureResource(YujiMonster entity) {
        return YujiContent.id("textures/entity/" + (entity.whiteRobe() ? "yuchi_white_robe" : entity.kind().id()) + ".png");
    }
}
