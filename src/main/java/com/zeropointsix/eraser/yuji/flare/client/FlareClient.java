package com.zeropointsix.eraser.yuji.flare.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.flare.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FlareClient {
    private static final ModelLayerLocation DECOY = new ModelLayerLocation(YujiContent.id("flare_decoy"), "main");
    private static final ModelLayerLocation PARACHUTE = new ModelLayerLocation(YujiContent.id("flare_parachute"), "main");
    private static final ResourceLocation RED_TEXTURE = YujiContent.id("textures/entity/flare_decoy.png");
    private static final ResourceLocation PARACHUTE_TEXTURE = YujiContent.id("textures/entity/flare_parachute.png");
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FlareContent.FLARE.get(), context -> new ThrownItemRenderer<>(context, .8F, true));
        event.registerEntityRenderer(FlareContent.DECOY.get(), DecoyRenderer::new);
        event.registerEntityRenderer(FlareContent.FALLING_CRATE.get(), CrateRenderer::new);
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DECOY, () -> {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("stick", CubeListBuilder.create().texOffs(0, 0).addBox(-1, 3, -1, 2, 21, 2)
                    .texOffs(8, 0).addBox(-2, 1, -2, 4, 3, 4), PartPose.ZERO);
            return LayerDefinition.create(mesh, 32, 32);
        });
        event.registerLayerDefinition(PARACHUTE, () -> {
            MeshDefinition mesh = new MeshDefinition();
            CubeListBuilder cubes = CubeListBuilder.create().texOffs(0, 0).addBox(-20, -40, -20, 40, 2, 40)
                    .texOffs(0, 42).addBox(-15, -44, -15, 30, 4, 30);
            for (int x : new int[]{-7, 7}) for (int z : new int[]{-7, 7}) cubes.texOffs(120, 0).addBox(x, -38, z, 1, 38, 1);
            mesh.getRoot().addOrReplaceChild("canopy", cubes, PartPose.ZERO);
            return LayerDefinition.create(mesh, 128, 128);
        });
    }
    private static final class DecoyModel extends EntityModel<FlareDecoy> {
        private final ModelPart root;
        private DecoyModel(ModelPart root) { this.root = root; }
        @Override public void setupAnim(FlareDecoy entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) { }
        @Override public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, float red, float green, float blue, float alpha) {
            root.render(pose, buffer, light, overlay, red, green, blue, alpha);
        }
    }
    private static final class DecoyRenderer extends LivingEntityRenderer<FlareDecoy, DecoyModel> {
        private DecoyRenderer(EntityRendererProvider.Context context) { super(context, new DecoyModel(context.bakeLayer(DECOY)), .15F); }
        @Override public ResourceLocation getTextureLocation(FlareDecoy entity) { return RED_TEXTURE; }
        @Override protected int getBlockLightLevel(FlareDecoy entity, BlockPos pos) { return 15; }
    }
    private static final class CrateRenderer extends EntityRenderer<FallingSupplyCrate> {
        private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
        private final ModelPart canopy;
        private CrateRenderer(EntityRendererProvider.Context context) {
            super(context); blocks = context.getBlockRenderDispatcher(); canopy = context.bakeLayer(PARACHUTE); shadowRadius = .6F;
        }
        @Override public ResourceLocation getTextureLocation(FallingSupplyCrate entity) { return PARACHUTE_TEXTURE; }
        @Override public void render(FallingSupplyCrate entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose(); pose.translate(-.5, 0, -.5);
            blocks.renderSingleBlock(FlareContent.CRATE.get().defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose(); pose.pushPose(); pose.translate(0, 1, 0); pose.scale(1, -1, -1);
            canopy.render(pose, buffers.getBuffer(RenderType.entityCutout(PARACHUTE_TEXTURE)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose(); super.render(entity, yaw, partial, pose, buffers, light);
        }
    }
    private FlareClient() { }
}
