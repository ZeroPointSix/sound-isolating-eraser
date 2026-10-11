package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.neon.NeonTumorEntity;
import com.zeropointsix.eraser.registry.ModEntities;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NeonTumorRenderer extends GeoEntityRenderer<NeonTumorEntity> {
    public NeonTumorRenderer(EntityRendererProvider.Context context) {
        super(context, new NeonTumorModel());
        addRenderLayer(new GlowLayer(this));
    }

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.NEON_TUMOR.get(), NeonTumorRenderer::new);
    }

    @Override public RenderType getRenderType(NeonTumorEntity entity, ResourceLocation texture,
                                             MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override public void preRender(PoseStack pose, NeonTumorEntity entity, BakedGeoModel model,
                                     MultiBufferSource buffers, VertexConsumer buffer, boolean reRender,
                                     float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
        this.scaleWidth = entity.getModelScale();
        this.scaleHeight = entity.getModelScale();
        this.shadowRadius = entity.getBbWidth() * 0.4F;
        for (String name : new String[]{"pod_1", "pod_2", "pod_3"}) {
            model.getBone(name).ifPresent(bone -> bone.setHidden(!entity.isSlamming()));
        }
        super.preRender(pose, entity, model, buffers, buffer, reRender, partialTick, light, overlay, red, green, blue, alpha);
    }

    @Override protected float getDeathMaxRotation(NeonTumorEntity entity) { return 0; }

    private static final class GlowLayer extends GeoRenderLayer<NeonTumorEntity> {
        private GlowLayer(NeonTumorRenderer renderer) { super(renderer); }

        @Override public void render(PoseStack pose, NeonTumorEntity entity, BakedGeoModel model,
                                     RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                                     float partialTick, int light, int overlay) {
            if (entity.isInvisible()) return;
            float alpha = switch (entity.getLightTier()) {
                case DARK -> 0.16F;
                case DIM -> 0.36F;
                case BRIGHT -> 0.85F + 0.15F * (float) Math.sin((entity.tickCount + partialTick) * Math.PI / 20);
            };
            ResourceLocation texture = ((NeonTumorModel) getGeoModel()).getGlowTexture(entity);
            RenderType glow = RenderType.entityTranslucentEmissive(texture);
            getRenderer().reRender(model, pose, buffers, entity, glow, buffers.getBuffer(glow), partialTick,
                    15728880, OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha);
        }
    }
}
