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
 * 风雷翅背部模型（B 包剪影的等价实现，加厚版）：每侧覆羽臂（覆羽带×3 排渐层
 * + 前缘条 + 关节端羽）+ 4 枚双段次级飞羽 + 7 枚双段主飞羽 + alula 指羽；
 * 每根飞羽由「粗羽根 + 细羽尖」两段方盒拼出锥形。独立 ModelPart，扇动时逐羽
 * 相位滞后（翼尖滞后最大），展开时沿覆羽带扇形打开。
 *
 * 贴图 64x64 布局（见 tools/generate_wings_textures.py entity_wings）：
 *   (0,0)-(32,24)  覆羽带：三排渐层 + 羽分线 + 金边
 *   (32,0)-(60,24) 主飞羽条带：7 列，羽轴金线 + 羽缘青线
 *   (0,24)-(24,32) 次级飞羽条带：4 列
 *   (24,24)-(32,32) alula / 前缘
 *   (0,32)-(16,48) 背甲板 + 青宝石
 *   (16,32)-(32,48) 背带/绑带
 *   (32,24)-(64,32) 翼尖/前缘描边
 *   (32,32)-(64,56) 收翼态贴图
 *   (48,48)-(64,64) 备用亮斑（blink 闪帧）
 */
public class WingsModel extends Model {
    private static final int SECONDARIES = 4;
    private static final int PRIMARIES = 7;

    private final ModelPart root;
    private final ModelPart harness;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart[] leftSecondaries = new ModelPart[SECONDARIES];
    private final ModelPart[] leftPrimaries = new ModelPart[PRIMARIES];
    private final ModelPart[] rightSecondaries = new ModelPart[SECONDARIES];
    private final ModelPart[] rightPrimaries = new ModelPart[PRIMARIES];
    private final ModelPart leftAlula;
    private final ModelPart rightAlula;
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
        this.leftAlula = leftWing.getChild("l_alula");
        this.rightAlula = rightWing.getChild("r_alula");
        this.foldedLeft = root.getChild("folded_left");
        this.foldedRight = root.getChild("folded_right");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 背甲：主甲板 + 上/下束带 + 肩部绑带 + 中央青金宝石
        PartDefinition harness = root.addOrReplaceChild("harness",
                CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-4, 0, 1, 8, 6, 1)          // 主甲板
                        .texOffs(16, 32).addBox(-4, -0.6f, 0.8f, 8, 1.2f, 1.2f) // 上束带
                        .texOffs(16, 38).addBox(-4, 5.4f, 0.8f, 8, 1.2f, 1.2f)  // 下束带
                        .texOffs(16, 44).addBox(-5.4f, 1, 0.8f, 1.4f, 4, 1.2f)  // 左肩绑带
                        .texOffs(26, 44).addBox(4, 1, 0.8f, 1.4f, 4, 1.2f)      // 右肩绑带
                        .texOffs(4, 40).addBox(-1, 2, 2, 2, 2, 0.8f),           // 中央宝石凸出
                PartPose.offset(0, 2, 0.6f));

        // 翼展方向：左翼 +x 展开，右翼 -x 展开（镜像）。
        // 覆羽臂：覆羽带三排渐层（上浅下深）+ 前缘加厚条 + 末端关节羽
        PartDefinition lw = root.addOrReplaceChild("left_wing",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(0, -2.4f, -0.5f, 6, 2.6f, 1.0f)   // 上排覆羽(亮)
                        .texOffs(0, 8).addBox(0, -0.2f, -0.4f, 6.5f, 1.8f, 0.9f) // 中排覆羽
                        .texOffs(0, 16).addBox(0.5f, 1.4f, -0.3f, 5.5f, 1.4f, 0.8f)// 下排覆羽(深)
                        .texOffs(26, 24).addBox(0, -2.6f, -0.8f, 6, 1.0f, 0.6f)  // 前缘加厚(亮金)
                        .texOffs(24, 28).addBox(5.6f, -0.6f, -0.3f, 1.6f, 1.4f, 0.6f), // 腕部关节羽
                PartPose.offset(2.5f, 2, 2.2f));
        PartDefinition rw = root.addOrReplaceChild("right_wing",
                CubeListBuilder.create().mirror()
                        .texOffs(0, 0).addBox(-6, -2.4f, -0.5f, 6, 2.6f, 1.0f)
                        .texOffs(0, 8).addBox(-6.5f, -0.2f, -0.4f, 6.5f, 1.8f, 0.9f)
                        .texOffs(0, 16).addBox(-6, 1.4f, -0.3f, 5.5f, 1.4f, 0.8f)
                        .texOffs(26, 24).addBox(-6, -2.6f, -0.8f, 6, 1.0f, 0.6f)
                        .texOffs(24, 28).addBox(-7.2f, -0.6f, -0.3f, 1.6f, 1.4f, 0.6f),
                PartPose.offset(-2.5f, 2, 2.2f));

        // alula 指羽：翼根前缘的小拇指羽，低速大扇时张开
        lw.addOrReplaceChild("l_alula",
                CubeListBuilder.create().texOffs(24, 26)
                        .addBox(0, -0.5f, -0.35f, 3.0f, 1.0f, 0.7f),
                PartPose.offset(2.2f, -1.6f, -0.3f));
        rw.addOrReplaceChild("r_alula",
                CubeListBuilder.create().texOffs(24, 26).mirror()
                        .addBox(-3.0f, -0.5f, -0.35f, 3.0f, 1.0f, 0.7f),
                PartPose.offset(-2.2f, -1.6f, -0.3f));

        // 次级飞羽：4 枚，每枚两段（粗羽根 + 收细羽尖），从覆羽带中段向外递减
        for (int i = 0; i < SECONDARIES; i++) {
            float rootLen = 5.6f - i * 0.5f;
            float tipLen = rootLen * 0.55f;
            int tex = i * 6;
            lw.addOrReplaceChild("ls" + i,
                    CubeListBuilder.create()
                            .texOffs(tex, 24).addBox(0, -0.7f, -0.3f, rootLen, 1.4f, 0.6f)
                            .texOffs(tex, 28).addBox(rootLen - 0.4f, -0.4f, -0.25f,
                                    tipLen + 0.4f, 0.8f, 0.5f),
                    PartPose.offset(4.6f + i * 0.4f, 1.1f - i * 0.55f, 0));
            rw.addOrReplaceChild("rs" + i,
                    CubeListBuilder.create().mirror()
                            .texOffs(tex, 24).addBox(-rootLen, -0.7f, -0.3f, rootLen, 1.4f, 0.6f)
                            .texOffs(tex, 28).addBox(-rootLen - tipLen, -0.4f, -0.25f,
                                    tipLen + 0.4f, 0.8f, 0.5f),
                    PartPose.offset(-4.6f - i * 0.4f, 1.1f - i * 0.55f, 0));
        }
        // 主飞羽：7 枚，沿上弧线扇形排布（越靠翼尖越高、越外张）；
        // 每枚「长粗根段 + 细尖段并微垂 0.35px」拼出锥形羽
        for (int i = 0; i < PRIMARIES; i++) {
            float len = 10.5f - i * 0.35f;
            float tipLen = len * 0.42f;
            int tu = 32 + i * 2;      // 主飞羽采样条带 (32,0)-(64,24)
            int tv = 2 + i * 2;
            lw.addOrReplaceChild("lp" + i,
                    CubeListBuilder.create()
                            .texOffs(tu, tv)
                            .addBox(0, -0.75f, -0.25f, len - tipLen * 0.4f, 1.5f, 0.5f)
                            .texOffs(tu, Math.min(tv + 8, 22))
                            .addBox(len - tipLen * 0.4f - 0.3f, -0.35f + 0.35f, -0.2f,
                                    tipLen + 0.3f, 0.7f, 0.4f),
                    PartPose.offset(6.4f, 0.6f - i * 0.45f, 0));
            rw.addOrReplaceChild("rp" + i,
                    CubeListBuilder.create().mirror()
                            .texOffs(tu, tv)
                            .addBox(-(len - tipLen * 0.4f), -0.75f, -0.25f,
                                    len - tipLen * 0.4f, 1.5f, 0.5f)
                            .texOffs(tu, Math.min(tv + 8, 22))
                            .addBox(-len - 0.0f, -0.35f + 0.35f, -0.2f,
                                    tipLen + 0.3f, 0.7f, 0.4f),
                    PartPose.offset(-6.4f, 0.6f - i * 0.45f, 0));
        }

        // 收翼态：两片折叠羽束叠在背甲上（收拢时替换展开翼，避免穿模）
        root.addOrReplaceChild("folded_left",
                CubeListBuilder.create().texOffs(32, 32)
                        .addBox(0, -1, -0.25f, 4, 6, 0.5f)
                        .texOffs(40, 32).addBox(0.6f, -1.4f, -0.5f, 3, 6.5f, 0.4f),
                PartPose.offset(1.5f, 4, 2.2f));
        root.addOrReplaceChild("folded_right",
                CubeListBuilder.create().texOffs(32, 32).mirror()
                        .addBox(-4, -1, -0.25f, 4, 6, 0.5f)
                        .texOffs(40, 32).addBox(-3.6f, -1.4f, -0.5f, 3, 6.5f, 0.4f),
                PartPose.offset(-1.5f, 4, 2.2f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * @param deploy    0..1 展开动画
     * @param sweep     0..1 → 0..60° 后掠
     * @param flapPhase 扇动相位（弧度，秒制 2π·Hz 由调用方喂）
     * @param flapAmp   扇动幅度（悬停大、御风微、疾风小、神霄近零）
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

        // alula：悬停大扇时张开（低速增升），高速收回
        float alulaOpen = flapAmp > 0.3f ? Mth.sin(flapPhase) * 0.5f + 0.5f : 0f;
        leftAlula.zRot = -0.3f - alulaOpen * 0.35f;
        rightAlula.zRot = 0.3f + alulaOpen * 0.35f;
        leftAlula.yRot = primY * 0.6f;
        rightAlula.yRot = -primY * 0.6f;

        // 逐羽相位滞后：主飞羽从根部到翼尖滞后递增（0.32 rad/根），次级较小；
        // 越靠翼尖的羽扇开越大并下垂越多
        for (int i = 0; i < PRIMARIES; i++) {
            float lag = flapAmp * Mth.sin(flapPhase - i * 0.32f);
            float fanY = primY + sweepRad * 0.6f - i * 0.055f * spread;
            float droop = i * 0.018f * spread;
            leftPrimaries[i].yRot = fanY;
            leftPrimaries[i].zRot = -0.03f - droop - lag * (0.5f + i * 0.11f);
            rightPrimaries[i].yRot = -fanY;
            rightPrimaries[i].zRot = 0.03f + droop + lag * (0.5f + i * 0.11f);
        }
        for (int i = 0; i < SECONDARIES; i++) {
            float lag = flapAmp * Mth.sin(flapPhase - i * 0.28f);
            float fanY = primY + sweepRad * 0.5f - i * 0.035f * spread;
            leftSecondaries[i].yRot = fanY;
            leftSecondaries[i].zRot = -0.05f - lag * 0.4f;
            rightSecondaries[i].yRot = -fanY;
            rightSecondaries[i].zRot = 0.05f + lag * 0.4f;
        }

        boolean open = deploy > 0.35f;
        leftWing.visible = open;
        rightWing.visible = open;
        foldedLeft.visible = !open;
        foldedRight.visible = !open;
        foldedLeft.xRot = 0.15f;
        foldedRight.xRot = 0.15f;
        foldedLeft.yRot = 0.25f;
        foldedRight.yRot = -0.25f;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light,
                               int overlay, float r, float g, float b, float a) {
        root.render(poseStack, consumer, light, overlay, r, g, b, a);
    }
}
