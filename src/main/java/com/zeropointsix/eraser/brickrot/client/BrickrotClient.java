package com.zeropointsix.eraser.brickrot.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import software.bernie.geckolib.core.animation.AnimationState;
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
        @Override public void setCustomAnimations(T entity, long instance, AnimationState<T> state) {
            super.setCustomAnimations(entity, instance, state);
            if (entity instanceof BrickrotPart part && (part.index() == 2 || part.index() == 3)) {
                var lamp = getAnimationProcessor().getBone("debris_lamp");
                var bench = getAnimationProcessor().getBone("debris_bench");
                if (lamp != null) lamp.setHidden(part.index() == 3);
                if (bench != null) bench.setHidden(part.index() == 2);
            }
        }
    }

    private static class DirectionalRenderer<T extends Entity & GeoEntity> extends GeoEntityRenderer<T> {
        private DirectionalRenderer(EntityRendererProvider.Context context) {
            super(context, new Model<>());
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override protected void applyRotations(T entity, PoseStack poses, float age, float yaw, float partial) {
            super.applyRotations(entity, poses, age, yaw, partial);
            float pitch = entity instanceof BrickrotWallEntity head
                    ? head.action() == BrickrotWallEntity.Action.DIVE
                        ? Math.min(70, (head.actionElapsed() + partial) * 7) : 0
                    : Mth.rotLerp(partial, entity.xRotO, entity.getXRot());
            float center = entity instanceof BrickrotPart part
                    ? part.index() == 1 ? 21F / 16 : part.index() >= 8 ? 15F / 16 : 20F / 16
                    : 24F / 16;
            poses.translate(0, center, 0);
            poses.mulPose(Axis.XP.rotationDegrees(-pitch));
            poses.translate(0, -center, 0);
        }

        // The supplied death clip already collapses the head and body in place.
        @Override protected float getDeathMaxRotation(T entity) { return 0; }
    }

    private static final class HeadRenderer extends DirectionalRenderer<BrickrotWallEntity> {
        private final GeoEntityRenderer<BrickrotPart> body;

        private HeadRenderer(EntityRendererProvider.Context context) {
            super(context);
            body = new DirectionalRenderer<>(context);
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
