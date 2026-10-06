package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 风雷翅背部模型：覆羽段 + 飞羽段（两翼镜像），收起时显示发光小翼。
 * 贴图 64x64：覆羽带(0,0)-(32,16) / 飞羽(32,0)-(64,16) / 背带(0,16) / 收翼(32,16)。
 */
public class WingsModel extends Model {
    private final ModelPart root;
    private final ModelPart leftWing;
    private final ModelPart leftPrimary;
    private final ModelPart rightWing;
    private final ModelPart rightPrimary;
    private final ModelPart foldedLeft;
    private final ModelPart foldedRight;
    private final ModelPart harness;

    public WingsModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.root = root;
        this.harness = root.getChild("harness");
        this.leftWing = root.getChild("left_wing");
        this.leftPrimary = leftWing.getChild("left_primary");
        this.rightWing = root.getChild("right_wing");
        this.rightPrimary = rightWing.getChild("right_primary");
        this.foldedLeft = root.getChild("folded_left");
        this.foldedRight = root.getChild("folded_right");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("harness",
                CubeListBuilder.create().texOffs(0, 16)
                        .addBox(-4, 0, 1, 8, 6, 1),
                PartPose.offset(0, 2, 0.6f));

        // left wing (viewer-left = player-right arm side; mirrored below)
        PartDefinition lw = root.addOrReplaceChild("left_wing",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(0, -2, -0.4f, 7, 4, 0.8f),
                PartPose.offset(2.5f, 2, 2.2f));
        lw.addOrReplaceChild("left_primary",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(0, -2, -0.25f, 9, 4, 0.5f),
                PartPose.offset(7, 0, 0));
        PartDefinition rw = root.addOrReplaceChild("right_wing",
                CubeListBuilder.create().texOffs(0, 0).mirror()
                        .addBox(-7, -2, -0.4f, 7, 4, 0.8f),
                PartPose.offset(-2.5f, 2, 2.2f));
        rw.addOrReplaceChild("right_primary",
                CubeListBuilder.create().texOffs(32, 0).mirror()
                        .addBox(-9, -2, -0.25f, 9, 4, 0.5f),
                PartPose.offset(-7, 0, 0));

        root.addOrReplaceChild("folded_left",
                CubeListBuilder.create().texOffs(32, 16)
                        .addBox(0, -1, -0.25f, 4, 6, 0.5f),
                PartPose.offset(1.5f, 4, 2.2f));
        root.addOrReplaceChild("folded_right",
                CubeListBuilder.create().texOffs(32, 16).mirror()
                        .addBox(-4, -1, -0.25f, 4, 6, 0.5f),
                PartPose.offset(-1.5f, 4, 2.2f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * @param deploy 0..1 deploy animation
     * @param sweep 0..1 → 0..60° swept back
     * @param flap  radians of flap oscillation (hover)
     * @param glide true = level glide pose (cruise)
     */
    public void setupWings(float deploy, float sweep, float flap, boolean glide,
                           float tickDelta) {
        float spread = Mth.lerp(deploy, 0.08f, 1.0f);
        float sweepRad = sweep * (float) Math.toRadians(60);
        float baseY = Mth.lerp(spread, 0.95f, -0.35f); // folded → fan out
        float primY = Mth.lerp(spread, 0.5f, -0.12f);

        leftWing.yRot = baseY + sweepRad;
        leftWing.zRot = -0.15f - flap * 0.35f;
        leftPrimary.yRot = primY + sweepRad * 0.6f;
        leftPrimary.zRot = -0.1f - flap * 0.25f;

        rightWing.yRot = -baseY - sweepRad;
        rightWing.zRot = 0.15f + flap * 0.35f;
        rightPrimary.yRot = -primY - sweepRad * 0.6f;
        rightPrimary.zRot = 0.1f + flap * 0.25f;

        float foldPitch = glide ? 0.35f : 0.15f;
        leftWing.xRot = foldPitch;
        rightWing.xRot = foldPitch;

        boolean open = deploy > 0.35f;
        leftWing.visible = open;
        rightWing.visible = open;
        foldedLeft.visible = !open;
        foldedRight.visible = !open;
        foldedLeft.xRot = 0.35f;
        foldedRight.xRot = 0.35f;
        foldedLeft.yRot = 0.4f;
        foldedRight.yRot = -0.4f;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light,
                               int overlay, float r, float g, float b, float a) {
        root.render(poseStack, consumer, light, overlay, r, g, b, a);
    }
}
