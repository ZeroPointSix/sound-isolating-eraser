package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.client.WingsClientData;
import com.zeropointsix.eraser.wings.WingsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class WingsLayer extends RenderLayer<AbstractClientPlayer,
        net.minecraft.client.model.PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LOCATION = new ModelLayerLocation(
            new ResourceLocation(ModMain.MOD_ID, "wings"), "main");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ModMain.MOD_ID, "textures/entity/wings.png");

    private final WingsModel model;

    public WingsLayer(PlayerRenderer renderer) {
        super(renderer);
        EntityModelSet models = Minecraft.getInstance().getEntityModels();
        this.model = new WingsModel(models.bakeLayer(LOCATION));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float tickDelta, float ageInTicks, float netHeadYaw, float headPitch) {
        // 所有渲染路径都先验真实装备：未穿戴/已脱下绝不渲染（含收起翼）——
        // 只靠同步状态会残留：没穿翅的玩家也可能有 WingInfo（同步包不区分穿戴）
        if (!(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                .getItem() instanceof com.zeropointsix.eraser.item.WindThunderWingsItem)) {
            return;
        }
        WingsClientData.WingInfo info = WingsClientData.get(player);
        if (info == null) {
            // fallback: folded wings before first sync packet arrives
            info = new WingsClientData.WingInfo(false, 0, 0, 0, 0, 0f, 0, 0);
        }

        Vec3 vel = player.getDeltaMovement();
        double vmax = Math.max(1, WingsConfig.SERVER.tierSpeed(3) / 20.0); // m/s→bpt
        float sweep = (float) Math.min(1.0, vel.length() / vmax);
        boolean glide = info.tier() == com.zeropointsix.eraser.wings.WingsState.CRUISE
                || (info.tier() >= com.zeropointsix.eraser.wings.WingsState.BOOST
                && vel.length() > 0.4); // 8 m/s = 0.4 bpt

        // 扇动：频率按秒换算（Notion §5.2 悬停约 0.8Hz 慢扇）；逐羽相位滞后由模型负责。
        // 悬停大扇、御风滑翔微振、疾风快扇、神霄近乎硬翼滑翔（极速几乎不扇）
        float seconds = ageInTicks / 20f;
        float flapPhase = 0f;
        float flapAmp = 0f;
        if (info.deployed()) {
            int tier = info.tier();
            if (tier == com.zeropointsix.eraser.wings.WingsState.HOVER) {
                flapPhase = seconds * (float) (Math.PI * 2) * 0.8f;
                flapAmp = 0.6f;
            } else if (tier == com.zeropointsix.eraser.wings.WingsState.BOOST) {
                flapPhase = seconds * (float) (Math.PI * 2) * 4f;
                flapAmp = 0.18f;
            } else if (tier == com.zeropointsix.eraser.wings.WingsState.STORM) {
                flapPhase = seconds * (float) (Math.PI * 2) * 1.2f;
                flapAmp = 0.05f;
            } else {
                flapPhase = seconds * (float) (Math.PI * 2) * 0.8f;
                flapAmp = 0.08f;
            }
        }

        model.setupWings(info.deployAnim(), sweep, flapPhase, flapAmp, glide);

        pose.pushPose();
        this.getParentModel().body.translateAndRotate(pose);
        pose.translate(0, -0.05, 0);
        VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(
                buffers, RenderType.entityTranslucent(TEXTURE), false,
                player.tickCount % 40 < 20);
        model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY,
                1f, 1f, 1f, 1f);
        pose.popPose();
    }
}
