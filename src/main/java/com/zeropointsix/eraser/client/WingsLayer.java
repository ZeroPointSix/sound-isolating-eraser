package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;

public class WingsLayer extends RenderLayer<AbstractClientPlayer,
        net.minecraft.client.model.PlayerModel<AbstractClientPlayer>> {
    private final WingsModel model = new WingsModel();

    public WingsLayer(PlayerRenderer renderer) { super(renderer); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float tickDelta, float ageInTicks, float netHeadYaw, float headPitch) {
        var stack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (player.isInvisible() || player.isSpectator()
                || !(stack.getItem() instanceof com.zeropointsix.eraser.item.WindThunderWingsItem)) return;
        WingsMotion.Pose motion = WingsClientData.renderPose(player, tickDelta);
        if (WingsClientEvents.isInventoryPreview() && player == Minecraft.getInstance().player) {
            // Paper-doll: half-open so the 6.4-span mesh fits the portrait without
            // collapsing to the 0.4 miniature (which vanishes in the 1.8-tall doll).
            model.setupWings(0.55f, 0.12f, 0f, 0.25f);
        } else {
            model.setupWings(motion.deployment(), motion.sweep(), motion.phase(), motion.amplitude());
        }
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffers,
                RenderType.entityTranslucent(WingsMesh.TEXTURE), false, stack.hasFoil());
        model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();
    }
}
