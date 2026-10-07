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
        // ModelPart 顶点已在 Cube.compile 内 /16 归一化为方块单位，这里只做展示尺度换算。
        // 姿态取舍：收翼体 ~0.7 方块粗实（适合物品展示），半开翼是 2px 薄羽片
        // （近 2 格翼展、任何角度都是一条线）——除展示框外一律用收起姿态
        boolean hero = ctx == ItemDisplayContext.FIXED;
        this.model.setupWings(hero ? 0.8f : 0f, hero ? 0.15f : 0f, 0f, 0f, false);

        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f); // item space: 0..1 盒中心
        switch (ctx) {
            case GUI -> {
                pose.scale(0.9f, 0.9f, 0.9f);
                pose.mulPose(Axis.XP.rotationDegrees(24f));
                pose.mulPose(Axis.YP.rotationDegrees(38f));
                pose.translate(0f, -0.1f, 0f);
            }
            case GROUND -> {
                float spin = (Minecraft.getInstance().level != null
                        ? Minecraft.getInstance().level.getGameTime() % 360 : 0) * 4f;
                pose.scale(0.8f, 0.8f, 0.8f);
                pose.mulPose(Axis.YP.rotationDegrees(spin));
                pose.translate(0f, -0.15f, 0f);
            }
            case FIXED -> { // 展示框：半开展翼英雄视角
                pose.scale(0.42f, 0.42f, 0.42f);
                pose.mulPose(Axis.YP.rotationDegrees(180f));
                pose.translate(0f, -0.15f, 0f);
            }
            default -> { // 第一/第三人称手持、头部
                boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                        || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                pose.scale(0.85f, 0.85f, 0.85f);
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
