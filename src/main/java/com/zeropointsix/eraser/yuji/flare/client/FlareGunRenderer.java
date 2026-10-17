package com.zeropointsix.eraser.yuji.flare.client;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.flare.FlareGunItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class FlareGunRenderer extends GeoItemRenderer<FlareGunItem> {
    public FlareGunRenderer() { super(new Model()); }

    public net.minecraft.world.item.ItemDisplayContext perspective() { return renderPerspective; }

    private static final class Model extends GeoModel<FlareGunItem> {
        @Override public ResourceLocation getModelResource(FlareGunItem item) {
            return YujiContent.id("geo/flare_gun.geo.json");
        }
        @Override public ResourceLocation getTextureResource(FlareGunItem item) {
            return YujiContent.id("textures/item/flare_gun.png");
        }
        @Override public ResourceLocation getAnimationResource(FlareGunItem item) {
            return YujiContent.id("animations/flare_gun.animation.json");
        }
    }
}
