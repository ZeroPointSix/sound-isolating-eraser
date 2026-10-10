package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.shadow.ShadowTanglerEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShadowTanglerRenderer extends GeoEntityRenderer<ShadowTanglerEntity> {
    public ShadowTanglerRenderer(EntityRendererProvider.Context context) {
        super(context, new ShadowTanglerModel());
        shadowRadius = 0.35F;
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SHADOW_TANGLER.get(), ShadowTanglerRenderer::new);
    }

    @Override
    public RenderType getRenderType(ShadowTanglerEntity entity, ResourceLocation texture,
            MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override protected float getDeathMaxRotation(ShadowTanglerEntity entity) { return 0F; }
}
