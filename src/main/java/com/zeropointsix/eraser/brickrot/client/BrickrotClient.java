package com.zeropointsix.eraser.brickrot.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.brickrot.BrickrotContent;
import com.zeropointsix.eraser.brickrot.BrickrotPart;
import com.zeropointsix.eraser.brickrot.BrickrotWallEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BrickrotClient {
    private BrickrotClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BrickrotContent.WALL.get(), HeadRenderer::new);
    }

    private static ResourceLocation resource(String path) { return new ResourceLocation(ModMain.MOD_ID, path); }

    private static final class Model<T extends Entity & GeoEntity> extends GeoModel<T> {
        @Override public ResourceLocation getModelResource(T entity) {
            String name = entity instanceof BrickrotPart part ? part.modelName() : "brickrot_head";
            return resource("geo/" + name + ".geo.json");
        }
        @Override public ResourceLocation getTextureResource(T entity) { return resource("textures/entity/brickrot.png"); }
        @Override public ResourceLocation getAnimationResource(T entity) { return resource("animations/brickrot.animation.json"); }
    }

    private static final class HeadRenderer extends GeoEntityRenderer<BrickrotWallEntity> {
        private final GeoEntityRenderer<BrickrotPart> body;

        private HeadRenderer(EntityRendererProvider.Context context) {
            super(context, new Model<>());
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            body = new GeoEntityRenderer<>(context, new Model<>());
            body.addRenderLayer(new AutoGlowingGeoLayer<>(body));
            shadowRadius = 1.3F;
        }

        @Override public void render(BrickrotWallEntity head, float yaw, float partial, PoseStack poses,
                                     MultiBufferSource buffers, int light) {
            if (head.underground()) return;
            super.render(head, yaw, partial, poses, buffers, light);
            double x = Mth.lerp(partial, head.xo, head.getX());
            double y = Mth.lerp(partial, head.yo, head.getY());
            double z = Mth.lerp(partial, head.zo, head.getZ());
            for (BrickrotPart part : head.getParts()) {
                poses.pushPose();
                poses.translate(Mth.lerp(partial, part.xo, part.getX()) - x,
                        Mth.lerp(partial, part.yo, part.getY()) - y,
                        Mth.lerp(partial, part.zo, part.getZ()) - z);
                if (part.index() == 9) poses.scale(0.72F, 0.72F, 0.72F);
                body.render(part, Mth.rotLerp(partial, part.yRotO, part.getYRot()), partial, poses, buffers, light);
                poses.popPose();
            }
        }
    }
}
