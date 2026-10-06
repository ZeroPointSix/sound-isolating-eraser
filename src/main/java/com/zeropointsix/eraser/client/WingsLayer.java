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
        WingsClientData.WingInfo info = WingsClientData.get(player);
        if (info == null) {
            // fallback: assume folded wings if item is worn (before first sync)
            if (!(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                    .getItem() instanceof com.zeropointsix.eraser.item.WindThunderWingsItem)) {
                return;
            }
            info = new WingsClientData.WingInfo(false, 0, 0, 0, 0f, 0, 0);
        }

        Vec3 vel = player.getDeltaMovement();
        double vmax = Math.max(1, WingsConfig.SERVER.tierSpeed(3));
        float sweep = (float) Math.min(1.0, vel.length() / vmax);
        boolean glide = info.tier() == com.zeropointsix.eraser.wings.WingsState.CRUISE
                || (info.tier() >= com.zeropointsix.eraser.wings.WingsState.BOOST
                && vel.length() > 8);

        float flap = 0;
        if (info.tier() == com.zeropointsix.eraser.wings.WingsState.HOVER
                && info.deployed()) {
            flap = Mth.sin(ageInTicks * (float) Math.PI * 0.8f) * 0.6f;
        }

        model.setupWings(info.deployAnim(), sweep, flap, glide, tickDelta);

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
