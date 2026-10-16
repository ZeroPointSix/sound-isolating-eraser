package com.zeropointsix.eraser.yuji.flare.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Loaded only through Forge's client item initialization hook. */
public final class FlareGunClientExtensions implements IClientItemExtensions {
    private FlareGunRenderer renderer;

    @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) renderer = new FlareGunRenderer();
        return renderer;
    }

    @Override public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm,
            ItemStack stack, float partialTick, float equipProcess, float swingProcess) {
        int side = arm == HumanoidArm.LEFT ? -1 : 1;
        pose.translate(side * .48, -.48 - .35 * equipProcess, -.75);
        pose.mulPose(Axis.YP.rotationDegrees(side * -4));
        return true;
    }
}
