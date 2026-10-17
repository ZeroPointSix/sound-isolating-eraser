package com.zeropointsix.eraser.yuji.flare.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zeropointsix.eraser.yuji.flare.FlareGunPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Loaded only through Forge's client item initialization hook. */
public final class FlareGunClientExtensions implements IClientItemExtensions {
    private static final HumanoidModel.ArmPose HOLD = HumanoidModel.ArmPose.create("FLARE_GUN_HOLD", false,
            (model, entity, arm) -> {
                var holdingArm = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
                holdingArm.xRot = -(float) Math.PI / 2 + model.head.xRot;
                holdingArm.yRot = model.head.yRot;
                holdingArm.zRot = 0;
            });
    private FlareGunRenderer renderer;

    @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) renderer = new FlareGunRenderer();
        return renderer;
    }

    @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return hand == InteractionHand.MAIN_HAND ? HOLD : null;
    }

    @Override public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm,
            ItemStack stack, float partialTick, float equipProcess, float swingProcess) {
        int side = arm == HumanoidArm.LEFT ? -1 : 1;
        // Camera-space hold: below the sight line, matching FlareGunPose muzzle axes.
        pose.translate(side * FlareGunPose.HAND_SIDE, -FlareGunPose.HAND_DOWN - .35 * equipProcess, -FlareGunPose.HAND_FORWARD);
        pose.mulPose(Axis.YP.rotationDegrees(side * FlareGunPose.HAND_YAW));
        pose.scale(FlareGunPose.HAND_SCALE, FlareGunPose.HAND_SCALE, FlareGunPose.HAND_SCALE);
        return true;
    }
}
