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

/**
 * 风雷翅物品渲染：物品栏、掉落和手持显示 0.4 格三维微缩翼；展示框显示完整翼形。
 */
public class WingsItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE = WingsMesh.TEXTURE;

    private WingsModel model;

    public WingsItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        if (this.model == null) {
            this.model = new WingsModel();
        }
        // Same 6.4-block reference mesh; the closed magical miniature spans 0.4 blocks.
        boolean hero = ctx == ItemDisplayContext.FIXED;
        this.model.setupWings(hero ? 1f : 0f, 0f, 0f, 0f);

        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f); // item space: 0..1 盒中心
        switch (ctx) {
            case GUI -> {
                pose.scale(1.8f, 1.8f, 1.8f);
                pose.mulPose(Axis.XP.rotationDegrees(24f));
                pose.mulPose(Axis.YP.rotationDegrees(38f));
                pose.translate(0f, -0.1f, 0f);
            }
            case GROUND -> {
                // ItemEntityRenderer owns smooth partial-tick spin and bob. Do not double-rotate.
                pose.translate(0f, -0.02f, -0.29f);
            }
            case FIXED -> { // 展示框：完整翼形
                pose.scale(0.12f, 0.12f, 0.12f);
                pose.mulPose(Axis.YP.rotationDegrees(180f));
                pose.translate(0f, -0.15f, 0f);
            }
            default -> { // 第一/第三人称手持、头部
                boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                        || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                pose.scale(1.2f, 1.2f, 1.2f);
                pose.mulPose(Axis.YP.rotationDegrees(left ? -40f : 40f));
                pose.translate(0f, -0.15f, 0f);
            }
        }

        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers,
                RenderType.entityTranslucent(TEXTURE), true, stack.hasFoil());
        this.model.renderToBuffer(pose, vc, light, overlay, 1f, 1f, 1f, 1f);
        pose.popPose();
    }
}
