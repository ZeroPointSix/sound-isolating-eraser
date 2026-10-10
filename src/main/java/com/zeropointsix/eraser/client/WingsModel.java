package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** The same faceted mesh used by the existing orthographic reference. */
public final class WingsModel {
    private float deployment;
    private float sweep;
    private float phase;
    private float amplitude;

    public void setupWings(float deployment, float sweep, float phase, float amplitude) {
        this.deployment = deployment;
        this.sweep = sweep;
        this.phase = phase;
        this.amplitude = amplitude;
    }

    public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light,
                               int overlay, float r, float g, float b, float a) {
        pose.pushPose();
        // Reference is feet-origin / Y-up; player body is shoulders-origin / Y-down.
        // Two sign changes preserve face winding and normal orientation.
        pose.translate(0, 1.5, 0);
        pose.scale(-1, -1, 1);
        WingsMesh.INSTANCE.render(pose, vertices, light, overlay,
                deployment, phase, amplitude, sweep, a);
        pose.popPose();
    }
}
