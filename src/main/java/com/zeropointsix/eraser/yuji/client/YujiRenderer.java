package com.zeropointsix.eraser.yuji.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.yuji.BoneSpike;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class YujiRenderer extends GeoEntityRenderer<YujiMonster> {
    public YujiRenderer(EntityRendererProvider.Context context) {
        super(context, new YujiModel());
        addRenderLayer(new Glow(this));
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override protected ItemStack getStackForBone(GeoBone bone, YujiMonster entity) {
                return entity.kind() == YujiKind.TOUDAO && bone.getName().equals("loot") ? entity.displayedLoot() : null;
            }
            @Override protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, YujiMonster entity) {
                return ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            }
        });
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        YujiContent.MOBS.values().forEach(type -> event.registerEntityRenderer(type.get(), YujiRenderer::new));
        event.registerEntityRenderer(YujiContent.BONE_SPIKE.get(), SpikeRenderer::new);
    }
    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        YujiContent.PARTICLE.values().forEach(type -> event.registerSpriteSet(type.get(), sprites ->
                (options, level, x, y, z, dx, dy, dz) -> new YujiParticle(level, x, y, z, dx, dy, dz, sprites)));
    }
    @Override public RenderType getRenderType(YujiMonster entity, ResourceLocation texture, MultiBufferSource buffers, float tick) {
        return RenderType.entityCutoutNoCull(texture);
    }
    @Override public void preRender(PoseStack pose, YujiMonster entity, BakedGeoModel model, MultiBufferSource buffers,
                                    VertexConsumer buffer, boolean reRender, float tick, int light, int overlay,
                                    float red, float green, float blue, float alpha) {
        scaleWidth = scaleHeight = entity.modelScale(); shadowRadius = entity.getBbWidth() * .4F;
        for (int i = 1; i <= 6; i++) {
            final int layer = i;
            model.getBone("layer_" + i).ifPresent(bone -> bone.setHidden(layer > Math.min(6, entity.layers())));
        }
        for (GeoBone bone : model.topLevelBones()) track(bone);
        super.preRender(pose, entity, model, buffers, buffer, reRender, tick, light, overlay, red, green, blue, alpha);
    }
    private static void track(GeoBone bone) { bone.setTrackingMatrices(true); bone.getChildBones().forEach(YujiRenderer::track); }
    @Override protected float getDeathMaxRotation(YujiMonster entity) { return 0; }
    private static final class Glow extends GeoRenderLayer<YujiMonster> {
        Glow(YujiRenderer renderer) { super(renderer); }
        @Override public void render(PoseStack pose, YujiMonster entity, BakedGeoModel model, RenderType type,
                                     MultiBufferSource buffers, VertexConsumer buffer, float tick, int light, int overlay) {
            if (!entity.kind().giant() || entity.isInvisible()) return;
            RenderType glow = RenderType.entityTranslucentEmissive(YujiContent.id("textures/entity/" + entity.kind().id() + "_glowmask.png"));
            getRenderer().reRender(model, pose, buffers, entity, glow, buffers.getBuffer(glow), tick, 15728880, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
    }
    private static final class SpikeRenderer extends GeoEntityRenderer<BoneSpike> {
        SpikeRenderer(EntityRendererProvider.Context context) {
            super(context, new GeoModel<>() {
                @Override public ResourceLocation getModelResource(BoneSpike entity) { return YujiContent.id("geo/bone_spike.geo.json"); }
                @Override public ResourceLocation getTextureResource(BoneSpike entity) { return YujiContent.id("textures/entity/bone_spike.png"); }
                @Override public ResourceLocation getAnimationResource(BoneSpike entity) { return YujiContent.id("animations/bone_spike.animation.json"); }
            });
        }
        @Override public void preRender(PoseStack pose, BoneSpike entity, BakedGeoModel model, MultiBufferSource buffers,
                                        VertexConsumer buffer, boolean reRender, float tick, int light, int overlay,
                                        float red, float green, float blue, float alpha) {
            scaleWidth = scaleHeight = entity.stuck() ? 1.7F : 1.4F;
            shadowRadius = entity.getBbWidth() * .8F;
            super.preRender(pose, entity, model, buffers, buffer, reRender, tick, light, overlay, red, green, blue, alpha);
        }
        @Override protected void applyRotations(BoneSpike entity, PoseStack pose, float age, float yaw, float tick) {
            pose.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(-entity.getXRot()));
        }
    }
}
