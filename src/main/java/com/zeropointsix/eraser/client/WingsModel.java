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
import net.minecraft.util.Mth;

/**
 * 风雷翅背部模型（B 包剪影的等价实现）：每侧覆羽带 + 3 根次级飞羽 + 5 根主飞羽
 * 独立 ModelPart，扇动时逐羽相位滞后（翼尖滞后最大），展开时沿覆羽带扇形打开。
 * 贴图 64x64：覆羽带(0,0)-(32,16) / 飞羽条带(32,0)-(64,16) / 背带(0,16) / 收翼(32,16)。
 */
public class WingsModel extends Model {
    private static final int SECONDARIES = 3;
    private static final int PRIMARIES = 5;

    private final ModelPart root;
    private final ModelPart harness;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart[] leftSecondaries = new ModelPart[SECONDARIES];
    private final ModelPart[] leftPrimaries = new ModelPart[PRIMARIES];
    private final ModelPart[] rightSecondaries = new ModelPart[SECONDARIES];
    private final ModelPart[] rightPrimaries = new ModelPart[PRIMARIES];
    private final ModelPart foldedLeft;
    private final ModelPart foldedRight;

    public WingsModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.root = root;
        this.harness = root.getChild("harness");
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
        for (int i = 0; i < SECONDARIES; i++) {
            this.leftSecondaries[i] = leftWing.getChild("ls" + i);
            this.rightSecondaries[i] = rightWing.getChild("rs" + i);
        }
        for (int i = 0; i < PRIMARIES; i++) {
            this.leftPrimaries[i] = leftWing.getChild("lp" + i);
            this.rightPrimaries[i] = rightWing.getChild("rp" + i);
        }
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

        // 翼展方向：左翼 +x 展开，右翼 -x 展开（镜像）
        PartDefinition lw = root.addOrReplaceChild("left_wing",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(0, -2, -0.4f, 6, 4, 0.8f),
                PartPose.offset(2.5f, 2, 2.2f));
        PartDefinition rw = root.addOrReplaceChild("right_wing",
                CubeListBuilder.create().texOffs(0, 0).mirror()
                        .addBox(-6, -2, -0.4f, 6, 4, 0.8f),
                PartPose.offset(-2.5f, 2, 2.2f));

        // 次级飞羽：覆羽带中段向外，长度递减
        for (int i = 0; i < SECONDARIES; i++) {
            lw.addOrReplaceChild("ls" + i,
                    CubeListBuilder.create().texOffs(2, 8 + i * 2)
                            .addBox(0, -0.5f, -0.25f, 5.5f - i * 0.6f, 1.0f, 0.5f),
                    PartPose.offset(4.5f + i * 0.5f, 0.9f - i * 0.5f, 0));
            rw.addOrReplaceChild("rs" + i,
                    CubeListBuilder.create().texOffs(2, 8 + i * 2).mirror()
                            .addBox(-(5.5f - i * 0.6f), -0.5f, -0.25f, 5.5f - i * 0.6f, 1.0f, 0.5f),
                    PartPose.offset(-4.5f - i * 0.5f, 0.9f - i * 0.5f, 0));
        }
        // 主飞羽：翼尖扇形排布，越长越靠下（羽尖下垂展开）
        for (int i = 0; i < PRIMARIES; i++) {
            float len = 8.5f - i * 0.4f;
            lw.addOrReplaceChild("lp" + i,
                    CubeListBuilder.create().texOffs(32, i * 3)
                            .addBox(0, -0.5f, -0.2f, len, 1.0f, 0.4f),
                    PartPose.offset(6.0f, 0.6f - i * 0.7f, 0));
            rw.addOrReplaceChild("rp" + i,
                    CubeListBuilder.create().texOffs(32, i * 3).mirror()
                            .addBox(-len, -0.5f, -0.2f, len, 1.0f, 0.4f),
                    PartPose.offset(-6.0f, 0.6f - i * 0.7f, 0));
        }

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
     * @param deploy    0..1 展开动画
     * @param sweep     0..1 → 0..60° 后掠
     * @param flapPhase 扇动相位（弧度，已含时间项）
     * @param flapAmp   扇动幅度（悬停大、巡航小、疾风微振）
     * @param glide     true = 滑翔姿态（巡航/高速）
     */
    public void setupWings(float deploy, float sweep, float flapPhase, float flapAmp,
                           boolean glide) {
        float spread = Mth.lerp(deploy, 0.08f, 1.0f);
        float sweepRad = sweep * (float) Math.toRadians(60);
        float baseY = Mth.lerp(spread, 0.95f, -0.35f); // folded → fan out
        float primY = Mth.lerp(spread, 0.5f, -0.12f);

        leftWing.yRot = baseY + sweepRad;
        leftWing.zRot = -0.15f - flapAmp * Mth.sin(flapPhase) * 0.35f;
        rightWing.yRot = -baseY - sweepRad;
        rightWing.zRot = 0.15f + flapAmp * Mth.sin(flapPhase) * 0.35f;

        float foldPitch = glide ? 0.35f : 0.15f;
        leftWing.xRot = foldPitch;
        rightWing.xRot = foldPitch;

        // 逐羽相位滞后：主飞羽从根部到翼尖滞后递增（0.35 rad/根），次级较小
        for (int i = 0; i < PRIMARIES; i++) {
            float lag = flapAmp * Mth.sin(flapPhase - i * 0.35f);
            float fanY = primY + sweepRad * 0.6f - i * 0.06f * spread; // 扇形收拢
            leftPrimaries[i].yRot = fanY;
            leftPrimaries[i].zRot = -0.08f - lag * (0.55f + i * 0.12f);
            rightPrimaries[i].yRot = -fanY;
            rightPrimaries[i].zRot = 0.08f + lag * (0.55f + i * 0.12f);
        }
        for (int i = 0; i < SECONDARIES; i++) {
            float lag = flapAmp * Mth.sin(flapPhase - i * 0.3f);
            float fanY = primY + sweepRad * 0.5f - i * 0.04f * spread;
            leftSecondaries[i].yRot = fanY;
            leftSecondaries[i].zRot = -0.06f - lag * 0.4f;
            rightSecondaries[i].yRot = -fanY;
            rightSecondaries[i].zRot = 0.06f + lag * 0.4f;
        }

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
