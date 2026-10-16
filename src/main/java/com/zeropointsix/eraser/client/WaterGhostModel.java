package com.zeropointsix.eraser.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * 女水鬼 —— 《骇夜：我在恐怖游戏里暴富》【珍贵回忆：徐佳蔚的红指甲】召唤出的水鬼。
 * 召唤版 water_ghost_summon 和敌对版 water_ghost 共用这个模型。
 *
 * 由 gen_ghost.py 生成，源工程为 water_ghost.bbmodel（Blockbench Modded Entity，box UV，贴图 128x128）。
 * Mojang 官方映射，Minecraft 1.20.1（Forge / NeoForge / Fabric+Mojmap 通用）。
 *
 * 渲染类型必须是 entityCutoutNoCull：湿发和破烂的下摆是带透明镂空的面片。
 * 骨骼：root > body > head > (jaw, hair)；body > arm > forearm（肘，手和 5 片指甲挂在前臂上）；root > leg > shin。
 * "_r" = 实体自身右侧。
 *
 * 动作：岸上一摇一摆地挪；水里整个身子放平游（蛙泳式划臂、交替踢腿、头发向后飘）；
 * 扑抓是右手的红指甲斜劈；抓人时双手前伸死死扣住，嘴张到最大。
 */
public class WaterGhostModel<T extends Entity> extends HierarchicalModel<T> {
	public static final ModelLayerLocation LAYER =
			new ModelLayerLocation(new ResourceLocation("sound_isolating_eraser", "water_ghost"), "main");

	/** 实体实现此接口即可驱动游泳和抓人姿态（不实现则始终是岸上姿态）。 */
	public interface PoseSource {
		/** 0 = 在岸上站着或走，1 = 在水里平着游。建议在实体里做插值。 */
		float waterGhostSwimAmount(float partialTick);

		/** 0 = 正常，1 = 双手前伸死死抓住目标（拖人下水、按住目标时）。 */
		default float waterGhostGrabAmount(float partialTick) {
			return 0F;
		}
	}

	private static final float SWIM_PITCH = 82F;    // 游泳时整个身子向前放平的角度
	private static final float SWIM_LIFT = 5F;      // 游泳时 root 抬高（像素），身子落在碰撞箱中间
	private static final float SWIM_BACK = 17F;     // 游泳时 root 向后挪（像素），让身子中段落在实体位置上
	private static final float HIP_DROP = 6F;       // 两腿岔开时髋部下沉的系数（像素）

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart hair;
	private final ModelPart armR;
	private final ModelPart armL;
	private final ModelPart forearmR;
	private final ModelPart forearmL;
	private final ModelPart legR;
	private final ModelPart legL;
	private final ModelPart shinR;
	private final ModelPart shinL;

	public WaterGhostModel(ModelPart bakedRoot) {
		super(RenderType::entityCutoutNoCull);
		this.root = bakedRoot.getChild("root");
		this.body = this.root.getChild("body");
		this.head = this.body.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.hair = this.head.getChild("hair");
		this.armR = this.body.getChild("arm_r");
		this.armL = this.body.getChild("arm_l");
		this.forearmR = this.armR.getChild("forearm_r");
		this.forearmL = this.armL.getChild("forearm_l");
		this.legR = this.root.getChild("leg_r");
		this.legL = this.root.getChild("leg_l");
		this.shinR = this.legR.getChild("shin_r");
		this.shinL = this.legL.getChild("shin_l");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition parts = mesh.getRoot();

		PartDefinition root = parts.addOrReplaceChild("root", CubeListBuilder.create(),
				PartPose.offset(0.0F, 24.0F, 0.0F));
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(23, 0).addBox(-7.0F, -12.0F, -5.0F, 14.0F, 12.0F, 10.0F, CubeDeformation.NONE) // belly
				.texOffs(76, 60).addBox(-5.5F, -10.0F, -7.0F, 11.0F, 9.0F, 2.0F, CubeDeformation.NONE) // gut
				.texOffs(0, 60).addBox(-6.0F, -15.0F, -4.0F, 12.0F, 3.0F, 8.0F, CubeDeformation.NONE) // chest
				.texOffs(0, 44).addBox(-7.5F, -3.0F, -5.5F, 15.0F, 4.0F, 11.0F, CubeDeformation.NONE), // hem
				PartPose.offsetAndRotation(0.0F, -11.0F, 0.0F, 0.10472F, 0.0F, 0.0F));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 24).addBox(-5.0F, -11.0F, -5.0F, 10.0F, 9.0F, 10.0F, CubeDeformation.NONE), // skull
				PartPose.offsetAndRotation(0.0F, -15.0F, -1.0F, -0.069813F, 0.0F, 0.15708F));
		PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create()
				.texOffs(41, 60).addBox(-4.5F, 0.0F, -8.0F, 9.0F, 3.0F, 8.0F, CubeDeformation.NONE), // jaw
				PartPose.offset(0.0F, -2.0F, 3.5F));
		PartDefinition hair = head.addOrReplaceChild("hair", CubeListBuilder.create()
				.texOffs(41, 24).addBox(-5.5F, -1.0F, -10.5F, 11.0F, 6.0F, 11.0F, CubeDeformation.NONE) // hair_cap
				.texOffs(72, 0).addBox(-5.6F, 1.0F, -9.5F, 0.0F, 13.0F, 9.0F, CubeDeformation.NONE) // hair_side_r
				.texOffs(72, 0).addBox(5.6F, 1.0F, -9.5F, 0.0F, 13.0F, 9.0F, CubeDeformation.NONE) // hair_side_l
				.texOffs(40, 72).addBox(-5.0F, 2.0F, -10.6F, 10.0F, 6.0F, 0.0F, CubeDeformation.NONE), // hair_front
				PartPose.offset(0.0F, -11.0F, 5.0F));
		PartDefinition hair_long = hair.addOrReplaceChild("hair_long", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-5.5F, -1.0F, 0.0F, 11.0F, 23.0F, 0.0F, CubeDeformation.NONE), // hair_back
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.8F, 0.10472F, 0.0F, 0.0F));
		PartDefinition arm_r = body.addOrReplaceChild("arm_r", CubeListBuilder.create()
				.texOffs(53, 44).addBox(-2.5F, -1.0F, -2.5F, 5.0F, 9.0F, 5.0F, CubeDeformation.NONE), // upper_r
				PartPose.offsetAndRotation(-8.0F, -13.0F, 0.0F, -0.174533F, 0.0F, 0.15708F));
		PartDefinition forearm_r = arm_r.addOrReplaceChild("forearm_r", CubeListBuilder.create()
				.texOffs(97, 44).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, CubeDeformation.NONE) // forearm_r
				.texOffs(103, 60).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 3.0F, 5.0F, CubeDeformation.NONE) // hand_r
				.texOffs(61, 72).addBox(1.5F, 11.0F, -2.5F, 1.0F, 5.0F, 1.0F, CubeDeformation.NONE) // nail_r1
				.texOffs(61, 72).addBox(0.15F, 11.0F, -2.5F, 1.0F, 5.0F, 1.0F, CubeDeformation.NONE) // nail_r2
				.texOffs(61, 72).addBox(-1.15F, 11.0F, -2.5F, 1.0F, 5.0F, 1.0F, CubeDeformation.NONE) // nail_r3
				.texOffs(61, 72).addBox(-2.5F, 11.0F, -2.5F, 1.0F, 5.0F, 1.0F, CubeDeformation.NONE) // nail_r4
				.texOffs(66, 72).addBox(1.5F, 11.0F, 0.5F, 1.0F, 4.0F, 1.0F, CubeDeformation.NONE), // thumb_r
				PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, -0.383972F, 0.0F, -0.069813F));
		PartDefinition arm_l = body.addOrReplaceChild("arm_l", CubeListBuilder.create()
				.texOffs(53, 44).addBox(-2.5F, -1.0F, -2.5F, 5.0F, 9.0F, 5.0F, CubeDeformation.NONE), // upper_l
				PartPose.offsetAndRotation(8.0F, -13.0F, 0.0F, -0.174533F, 0.0F, -0.15708F));
		PartDefinition forearm_l = arm_l.addOrReplaceChild("forearm_l", CubeListBuilder.create()
				.texOffs(97, 44).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, CubeDeformation.NONE) // forearm_l
				.texOffs(103, 60).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 3.0F, 5.0F, CubeDeformation.NONE) // hand_l
				.texOffs(61, 72).addBox(-2.5F, 11.0F, -2.5F, 1.0F, 5.0F, 1.0F, CubeDeformation.NONE) // nail_l1
				.texOffs(61, 72).addBox(-1.15F, 11.0F, -2.5F, 1.0F, 5.0F, 1.0F, CubeDeformation.NONE) // nail_l2
				.texOffs(61, 72).addBox(0.15F, 11.0F, -2.5F, 1.0F, 5.0F, 1.0F, CubeDeformation.NONE) // nail_l3
				.texOffs(61, 72).addBox(1.5F, 11.0F, -2.5F, 1.0F, 5.0F, 1.0F, CubeDeformation.NONE) // nail_l4
				.texOffs(66, 72).addBox(-2.5F, 11.0F, 0.5F, 1.0F, 4.0F, 1.0F, CubeDeformation.NONE), // thumb_l
				PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, -0.383972F, 0.0F, 0.069813F));
		PartDefinition leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create()
				.texOffs(74, 44).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 6.0F, 6.0F, CubeDeformation.NONE), // thigh_r
				PartPose.offset(-3.5F, -11.0F, 0.0F));
		PartDefinition shin_r = leg_r.addOrReplaceChild("shin_r", CubeListBuilder.create()
				.texOffs(21, 72).addBox(-2.0F, 0.0F, -2.5F, 4.0F, 3.0F, 5.0F, CubeDeformation.NONE) // shin_r
				.texOffs(0, 72).addBox(-2.0F, 3.0F, -3.5F, 4.0F, 2.0F, 6.0F, CubeDeformation.NONE), // foot_r
				PartPose.offset(0.0F, 6.0F, 0.0F));
		PartDefinition leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create()
				.texOffs(74, 44).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 6.0F, 6.0F, CubeDeformation.NONE), // thigh_l
				PartPose.offset(3.5F, -11.0F, 0.0F));
		PartDefinition shin_l = leg_l.addOrReplaceChild("shin_l", CubeListBuilder.create()
				.texOffs(21, 72).addBox(-2.0F, 0.0F, -2.5F, 4.0F, 3.0F, 5.0F, CubeDeformation.NONE) // shin_l
				.texOffs(0, 72).addBox(-2.0F, 3.0F, -3.5F, 4.0F, 2.0F, 6.0F, CubeDeformation.NONE), // foot_l
				PartPose.offset(0.0F, 6.0F, 0.0F));

		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public ModelPart root() {
		return this.root;
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);
		final float deg = Mth.DEG_TO_RAD;
		float partial = ageInTicks - entity.tickCount;
		float swim = entity instanceof com.zeropointsix.eraser.rednails.WaterGhostPoseSource s1 ? Mth.clamp(s1.waterGhostSwimAmount(partial), 0F, 1F) : 0F;
		float grab = entity instanceof com.zeropointsix.eraser.rednails.WaterGhostPoseSource s2 ? Mth.clamp(s2.waterGhostGrabAmount(partial), 0F, 1F) : 0F;
		float land = 1F - swim;

		// ================= 岸上：泡胀的身子一摇一摆地挪 =================
		float phase = limbSwing * 0.6662F * 0.7F;              // 腿短身子重，步子又慢又碎
		float amt = Math.min(1F, limbSwingAmount);
		float swing = Mth.cos(phase) * amt;
		this.body.y += Mth.sin(ageInTicks * 0.07F) * 0.3F;     // 鼓胀的身子一起一伏
		this.body.zRot += Mth.sin(phase) * 0.16F * amt + Mth.sin(ageInTicks * 0.03F) * 0.03F;
		this.body.xRot += amt * 0.10F;
		float kneeR = Math.max(0F, Mth.sin(phase)) * 0.7F * amt;   // 往前迈的那条腿屈膝抬脚
		float kneeL = Math.max(0F, -Mth.sin(phase)) * 0.7F * amt;
		this.legR.xRot += swing * 0.6F;
		this.legL.xRot -= swing * 0.6F;
		this.shinR.xRot += kneeR;
		this.shinL.xRot += kneeL;
		this.root.y += HIP_DROP * (1F - Mth.cos(0.6F * swing));  // 两腿岔开时髋部下沉，脚不离地
		float sway = Mth.sin(ageInTicks * 0.05F);
		this.armR.xRot += -swing * 0.2F + sway * 0.06F;          // 长胳膊垂着晃
		this.armL.xRot += swing * 0.2F - sway * 0.06F;
		this.armR.zRot += Mth.sin(ageInTicks * 0.04F) * 0.04F;
		this.armL.zRot -= Mth.sin(ageInTicks * 0.04F + 1F) * 0.04F;
		this.head.zRot += Mth.sin(ageInTicks * 0.025F) * 0.12F;   // 脑袋耷拉着慢慢晃
		this.head.xRot += Mth.sin(ageInTicks * 0.031F) * 0.05F;
		this.hair.xRot += Mth.sin(ageInTicks * 0.05F) * 0.03F;
		this.head.yRot += netHeadYaw * deg * 0.6F;
		this.head.xRot += headPitch * deg * 0.5F;
		float mouth = 0.10F + 0.06F * (0.5F + 0.5F * Mth.sin(ageInTicks * 0.045F));   // 嘴一直半张着，像在笑

		// ================= 水里：身子放平，蛙泳式划臂、交替踢腿 =================
		if (swim > 0F) {
			float stroke = ageInTicks * 0.16F + limbSwing * 0.25F;
			float sweep = 0.5F - 0.5F * Mth.cos(stroke);        // 0 = 双臂前伸并拢，1 = 划到两侧
			float kick = Mth.sin(ageInTicks * 0.32F);
			blendRot(this.body, swim, 0F, 0F, Mth.sin(stroke) * 0.05F);
			blendRot(this.head, swim, -62F * deg + headPitch * deg * 0.3F, netHeadYaw * deg * 0.4F, Mth.sin(ageInTicks * 0.025F) * 0.08F);
			blendRot(this.hair, swim, 1.25F + Mth.sin(ageInTicks * 0.12F) * 0.12F, 0F, Mth.sin(ageInTicks * 0.09F) * 0.06F);
			// 双臂举过头顶时 zRot 的"向外"方向反过来：右臂取负、左臂取正
			blendRot(this.armR, swim, -2.95F + 0.25F * sweep, 0F, -0.15F - 1.25F * sweep);
			blendRot(this.armL, swim, -2.95F + 0.25F * sweep, 0F, 0.15F + 1.25F * sweep);
			blendRot(this.forearmR, swim, -0.55F * Mth.sin(stroke), 0F, 0F);
			blendRot(this.forearmL, swim, -0.55F * Mth.sin(stroke), 0F, 0F);
			blendRot(this.legR, swim, 0.15F + kick * 0.45F, 0F, 0.08F);
			blendRot(this.legL, swim, 0.15F - kick * 0.45F, 0F, -0.08F);
			blendRot(this.shinR, swim, 0.35F + 0.3F * Mth.sin(ageInTicks * 0.32F + 1F), 0F, 0F);
			blendRot(this.shinL, swim, 0.35F - 0.3F * Mth.sin(ageInTicks * 0.32F + 1F), 0F, 0F);
			blend(this.root, swim, 0F, 24F - SWIM_LIFT, SWIM_BACK);
			blendRot(this.root, swim, SWIM_PITCH * deg, 0F, 0F);
			mouth += 0.08F * swim;
		}

		// ================= 扑抓：右手的红指甲像刀一样斜劈下来 =================
		if (this.attackTime > 0F) {
			float t = this.attackTime;
			float raise = Mth.sin(t * Mth.PI);
			float slash = Mth.sin((1F - (1F - t) * (1F - t)) * Mth.PI);
			this.armR.xRot += raise * (-1.7F * land + 0.9F * swim);     // 水里：从头顶往身前斜下方劈
			this.armR.zRot += slash * (-0.7F * land + 0.5F * swim);
			this.armR.yRot += raise * 0.35F;
			this.forearmR.xRot -= raise * 0.5F;
			this.armL.xRot += raise * (-0.6F * land + 0.3F * swim);
			this.body.yRot += slash * 0.22F;
			mouth += raise * 0.55F;
		}

		// ================= 抓人：双手前伸死死扣住，嘴张到最大 =================
		if (grab > 0F) {
			float shake = Mth.sin(ageInTicks * 1.1F) * 0.05F;
			float gx = Mth.lerp(swim, -1.35F, -2.55F);
			float gz = Mth.lerp(swim, -0.30F, 0.25F);                 // 双手往中间合拢
			blendRot(this.armR, grab, gx + shake, -0.15F, gz);
			blendRot(this.armL, grab, gx - shake, 0.15F, -gz);
			blendRot(this.forearmR, grab, -0.45F, 0F, 0.15F);
			blendRot(this.forearmL, grab, -0.45F, 0F, -0.15F);
			this.body.xRot += grab * 0.18F * land;
			mouth += grab * 0.6F;
		}
		this.jaw.xRot += Math.min(mouth, 1.05F);
	}

	private static void blend(ModelPart p, float t, float x, float y, float z) {
		p.x = Mth.lerp(t, p.x, x);
		p.y = Mth.lerp(t, p.y, y);
		p.z = Mth.lerp(t, p.z, z);
	}

	private static void blendRot(ModelPart p, float t, float x, float y, float z) {
		p.xRot = Mth.lerp(t, p.xRot, x);
		p.yRot = Mth.lerp(t, p.yRot, y);
		p.zRot = Mth.lerp(t, p.zRot, z);
	}
}
