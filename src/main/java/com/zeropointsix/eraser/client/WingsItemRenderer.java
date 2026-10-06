package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.zeropointsix.eraser.ModMain;

/**
 * 风雷翅物品渲染：物品栏/掉落/手持均显示真实 3D 翼模型（半展开英雄姿态），
 * 而非平面贴图——掉落物还会缓慢旋转扇动。
 */
public class WingsItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ModMain.MOD_ID, "textures/entity/wings.png");

    private WingsModel model;

    public WingsItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        if (this.model == null) {
            this.model = new WingsModel(Minecraft.getInstance().getEntityModels()
                    .bakeLayer(WingsLayer.LOCATION));
        }
        // 半展开英雄姿态：能看到覆羽带+飞羽层次
        this.model.setupWings(0.8f, 0.15f, 0f, 0f, false);

        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f); // item space: 0..1 盒中心
        switch (ctx) {
            case GUI -> {
                pose.scale(0.055f, 0.055f, 0.055f);
                pose.mulPose(Axis.XP.rotationDegrees(22f));
                pose.mulPose(Axis.YP.rotationDegrees(38f));
                pose.translate(0f, -1.5f, 0f);
            }
            case GROUND -> {
                float spin = (Minecraft.getInstance().level != null
                        ? Minecraft.getInstance().level.getGameTime() % 360 : 0) * 4f;
                pose.scale(0.045f, 0.045f, 0.045f);
                pose.mulPose(Axis.YP.rotationDegrees(spin));
                pose.translate(0f, -1.0f, 0f);
            }
            case FIXED -> { // 展示框
                pose.scale(0.05f, 0.05f, 0.05f);
                pose.mulPose(Axis.YP.rotationDegrees(180f));
                pose.translate(0f, -2.5f, 0f);
            }
            default -> { // 第一/第三人称手持、头部
                boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                        || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                pose.scale(0.06f, 0.06f, 0.06f);
                pose.mulPose(Axis.YP.rotationDegrees(left ? -40f : 40f));
                pose.translate(0f, -2.0f, 0f);
            }
        }

        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers,
                RenderType.entityTranslucent(TEXTURE), true, stack.hasFoil());
        this.model.renderToBuffer(pose, vc, light, overlay, 1f, 1f, 1f, 1f);
        pose.popPose();
    }
}
