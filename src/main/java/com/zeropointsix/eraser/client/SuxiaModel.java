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
import com.zeropointsix.eraser.suxia.SuxiaPoseSource;

/**
 * 素霞 —— 《骇夜：我在恐怖游戏里暴富》【珍贵回忆：素霞的怀表】的召唤物。
 *
 * 由 gen_suxia.py 生成，源工程为 suxia.bbmodel（Blockbench Modded Entity，box UV，贴图 128x128）。
 * Mojang 官方映射，Minecraft 1.20.1（Forge / NeoForge / Fabric+Mojmap 通用）。
 *
 * 渲染类型必须是 entityCutoutNoCull：头发是带透明镂空的双面面片。
 * 骨骼：root > body > neck > head；body > arm > forearm（肘）；root > leg > shin（膝）。"_r" = 实体自身右侧。
 *
 * 爬行是蜘蛛式的：四肢向外撇开、肘和膝高高拱起，手脚落点由两段式 IK 解算，对角两两交替快速挪动。
 */
public class SuxiaModel<T extends Entity> extends HierarchicalModel<T> {
	public static final ModelLayerLocation LAYER =
			new ModelLayerLocation(new ResourceLocation("sound_isolating_eraser", "suxia"), "main");


	// ---- 爬行参数（单位：模型像素，1 格 = 16） ----
	private static final float CRAWL_HIP_HEIGHT = 7F;      // 爬行时髋部离地高度
	private static final float LEG_REACH = 13F;            // 小腿+鞋：膝枢轴到鞋底
	private static final float ARM_REACH = 20F;            // 前臂：肘枢轴到爪尖
	private static final float GAIT_STRIDE = 4.5F;         // 每步前后摆幅
	private static final float GAIT_LIFT = 3.5F;           // 抬手抬脚高度
	private static final float GAIT_SPEED = 1.5F;          // 爬行步频倍率（比走路更碎更快）
	private static final float PLAY_DEAD_Y = 19.4F;  // 装死时 root 的 y，让肚子刚好贴地

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart armR;
	private final ModelPart armL;
	private final ModelPart forearmR;
	private final ModelPart forearmL;
	private final ModelPart legR;
	private final ModelPart legL;
	private final ModelPart shinR;
	private final ModelPart shinL;

	public SuxiaModel(ModelPart bakedRoot) {
		super(RenderType::entityCutoutNoCull);
		this.root = bakedRoot.getChild("root");
		this.body = this.root.getChild("body");
		this.neck = this.body.getChild("neck");
		this.head = this.neck.getChild("head");
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
				.texOffs(0, 0).addBox(-3.0F, -13.0F, -1.5F, 6.0F, 16.0F, 3.0F, CubeDeformation.NONE) // torso
				.texOffs(42, 20).addBox(-3.5F, 0.0F, -2.0F, 7.0F, 3.0F, 4.0F, CubeDeformation.NONE) // hem
				.texOffs(65, 20).addBox(-2.5F, -6.0F, -3.5F, 5.0F, 5.0F, 2.0F, CubeDeformation.NONE) // belly
				.texOffs(18, 34).addBox(-1.5F, -5.0F, -4.5F, 3.0F, 3.0F, 1.0F, CubeDeformation.NONE) // belly_round
				.texOffs(97, 20).addBox(-1.5F, -14.0F, -1.5F, 3.0F, 2.0F, 3.0F, CubeDeformation.NONE) // collar
				.texOffs(65, 0).addBox(-3.5F, -13.0F, 1.75F, 7.0F, 15.0F, 0.0F, CubeDeformation.NONE), // hair_long
				PartPose.offsetAndRotation(0.0F, -24.0F, 0.0F, 0.314159F, 0.0F, 0.0F));
		PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
				.texOffs(33, 20).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 6.0F, 2.0F, CubeDeformation.NONE), // neck_skin
				PartPose.offsetAndRotation(0.0F, -13.0F, 0.0F, 0.383972F, 0.0F, 0.0F));
		PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(80, 0).addBox(-3.0F, -8.0F, -3.0F, 6.0F, 8.0F, 6.0F, CubeDeformation.NONE) // skull
				.texOffs(19, 0).addBox(-3.5F, -8.5F, -3.5F, 7.0F, 9.0F, 7.0F, CubeDeformation.NONE) // hair
				.texOffs(48, 0).addBox(-4.0F, -7.0F, -4.0F, 8.0F, 16.0F, 0.0F, CubeDeformation.NONE) // hair_front
				.texOffs(18, 20).addBox(-3.5F, -8.0F, 3.75F, 7.0F, 10.0F, 0.0F, CubeDeformation.NONE), // hair_back
				PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, -0.523599F, 0.0F, 0.20944F));
		PartDefinition arm_r = body.addOrReplaceChild("arm_r", CubeListBuilder.create()
				.texOffs(105, 0).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 12.0F, 2.0F, CubeDeformation.NONE), // sleeve_r
				PartPose.offsetAndRotation(-4.0F, -12.0F, 0.0F, -0.418879F, 0.0F, 0.122173F));
		PartDefinition forearm_r = arm_r.addOrReplaceChild("forearm_r", CubeListBuilder.create()
				.texOffs(110, 20).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F, CubeDeformation.NONE) // cuff_r
				.texOffs(9, 20).addBox(-1.0F, 2.0F, -1.0F, 2.0F, 10.0F, 2.0F, CubeDeformation.NONE) // forearm_r
				.texOffs(0, 34).addBox(-1.0F, 12.0F, -1.0F, 2.0F, 3.0F, 2.0F, CubeDeformation.NONE) // hand_r
				.texOffs(93, 20).addBox(-0.5F, 15.0F, -1.0F, 1.0F, 5.0F, 0.5F, CubeDeformation.NONE) // claw_r1
				.texOffs(93, 20).addBox(-0.5F, 15.0F, -0.25F, 1.0F, 5.0F, 0.5F, CubeDeformation.NONE) // claw_r2
				.texOffs(93, 20).addBox(-0.5F, 15.0F, 0.5F, 1.0F, 5.0F, 0.5F, CubeDeformation.NONE), // claw_r3
				PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, -0.139626F, 0.0F, -0.05236F));
		PartDefinition arm_l = body.addOrReplaceChild("arm_l", CubeListBuilder.create()
				.texOffs(105, 0).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 12.0F, 2.0F, CubeDeformation.NONE), // sleeve_l
				PartPose.offsetAndRotation(4.0F, -12.0F, 0.0F, -0.418879F, 0.0F, -0.122173F));
		PartDefinition forearm_l = arm_l.addOrReplaceChild("forearm_l", CubeListBuilder.create()
				.texOffs(110, 20).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F, CubeDeformation.NONE) // cuff_l
				.texOffs(9, 20).addBox(-1.0F, 2.0F, -1.0F, 2.0F, 10.0F, 2.0F, CubeDeformation.NONE) // forearm_l
				.texOffs(9, 34).addBox(-1.0F, 12.0F, -1.0F, 2.0F, 3.0F, 2.0F, CubeDeformation.NONE) // hand_l
				.texOffs(93, 20).addBox(-0.5F, 15.0F, -1.0F, 1.0F, 5.0F, 0.5F, CubeDeformation.NONE) // claw_l1
				.texOffs(93, 20).addBox(-0.5F, 15.0F, -0.25F, 1.0F, 5.0F, 0.5F, CubeDeformation.NONE) // claw_l2
				.texOffs(93, 20).addBox(-0.5F, 15.0F, 0.5F, 1.0F, 5.0F, 0.5F, CubeDeformation.NONE), // claw_l3
				PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, -0.139626F, 0.0F, 0.05236F));
		PartDefinition leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create()
				.texOffs(114, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F, CubeDeformation.NONE), // thigh_r
				PartPose.offset(-1.5F, -24.0F, 0.0F));
		PartDefinition shin_r = leg_r.addOrReplaceChild("shin_r", CubeListBuilder.create()
				.texOffs(0, 20).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F, CubeDeformation.NONE) // shin_r
				.texOffs(80, 20).addBox(-1.0F, 11.0F, -2.5F, 2.0F, 2.0F, 4.0F, CubeDeformation.NONE), // shoe_r
				PartPose.offset(0.0F, 11.0F, 0.0F));
		PartDefinition leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create()
				.texOffs(114, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F, CubeDeformation.NONE), // thigh_l
				PartPose.offset(1.5F, -24.0F, 0.0F));
		PartDefinition shin_l = leg_l.addOrReplaceChild("shin_l", CubeListBuilder.create()
				.texOffs(0, 20).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F, CubeDeformation.NONE) // shin_l
				.texOffs(80, 20).addBox(-1.0F, 11.0F, -2.5F, 2.0F, 2.0F, 4.0F, CubeDeformation.NONE), // shoe_l
				PartPose.offset(0.0F, 11.0F, 0.0F));

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
		float crawl = entity instanceof SuxiaPoseSource src ? Mth.clamp(src.suxiaCrawlAmount(partial), 0F, 1F) : 0F;
		float playDead = entity instanceof SuxiaPoseSource src2 ? Mth.clamp(src2.suxiaPlayDeadAmount(partial), 0F, 1F) : 0F;
		float stand = 1F - crawl;

		// ================= 站立：佝偻、长臂下垂、歪头 =================
		float phase = limbSwing * 0.6662F * 0.8F;              // 腿长，步子慢而大
		float swing = Mth.cos(phase) * limbSwingAmount;
		float bob = Mth.cos(ageInTicks * 0.09F) * 0.04F + 0.04F;
		this.body.zRot += Mth.sin(ageInTicks * 0.05F) * 0.03F;
		this.body.yRot += Mth.sin(phase) * 0.06F * limbSwingAmount;
		this.armR.zRot += bob;
		this.armL.zRot -= bob;
		this.forearmR.xRot += Mth.sin(ageInTicks * 0.067F) * 0.06F;
		this.forearmL.xRot -= Mth.sin(ageInTicks * 0.067F) * 0.06F;
		// 慢慢晃的歪头，外加每 4 秒一次的神经质抽动
		float twitchT = (ageInTicks % 80F) / 4F;
		float twitch = twitchT < 1F ? Mth.sin(twitchT * Mth.PI) * 0.35F : 0F;
		this.head.zRot += Mth.sin(ageInTicks * 0.035F) * 0.12F + twitch;

		// 走路：大腿大步摆，小腿在向前迈时弯膝；手臂几乎不摆
		this.legR.xRot += swing * 0.75F;
		this.legL.xRot -= swing * 0.75F;
		this.shinR.xRot += Math.max(0F, Mth.sin(phase)) * 0.9F * limbSwingAmount;
		this.shinL.xRot += Math.max(0F, -Mth.sin(phase)) * 0.9F * limbSwingAmount;
		this.armR.xRot -= swing * 0.25F;
		this.armL.xRot += swing * 0.25F;
		this.root.y += 24F * (1F - Mth.cos(0.75F * swing));   // 两腿岔开时髋部下沉，脚不离地

		// 扑抓（attackTime 由 LivingEntityRenderer 填入）：双爪举起、肘部收起，再向前猛扑
		if (this.attackTime > 0F) {
			float t = this.attackTime;
			float raise = Mth.sin(t * Mth.PI);
			float slash = Mth.sin((1F - (1F - t) * (1F - t)) * Mth.PI);
			this.armR.xRot -= raise * 2.0F + slash * 0.4F;
			this.armL.xRot -= raise * 2.0F + slash * 0.4F;
			this.armR.yRot -= raise * 0.3F;
			this.armL.yRot += raise * 0.3F;
			this.forearmR.xRot += -raise * 0.7F + slash * 0.6F;
			this.forearmL.xRot += -raise * 0.7F + slash * 0.6F;
			this.neck.xRot += slash * 0.35F;
			this.body.xRot += slash * 0.2F;
		}

		// ================= 爬行：蜘蛛式，IK 落脚 =================
		if (crawl > 0F) {
			float gait = limbSwing * 0.6662F * GAIT_SPEED;
			float amp = Math.min(1F, limbSwingAmount * 1.6F);
			float drop = 24F - CRAWL_HIP_HEIGHT + Mth.sin(gait * 2F) * 0.6F * amp;
			float lunge = this.attackTime > 0F ? Mth.sin(this.attackTime * Mth.PI) : 0F;   // 爬行时攻击：上身抬起，双爪前扑
			float bodyX = (85F - 15F * lunge) * deg;
			float bodyY = Mth.sin(gait) * 0.10F * amp;

			blend(this.root, crawl, this.root.x, 24F + drop, this.root.z);
			blendRot(this.body, crawl, bodyX, bodyY, 0F);
			blendRot(this.neck, crawl, -15F * deg, Mth.sin(gait * 0.5F) * 0.15F, 0F);
			blendRot(this.head, crawl, (-70F + 10F * lunge) * deg, 0F, 25F * deg + twitch);

			float ground = -drop;                                    // 地面在 root 局部坐标里的 y（Java 的 y 向下）
			float[] fwd = {0F, 0F, -1F};
			// 对角步态：右前 + 左后 一组，左前 + 右后 一组
			float pa = gait, pb = gait + Mth.PI;
			float[] footR = gaitTarget(-13F, ground, 6F, pb, amp);
			float[] footL = gaitTarget(13F, ground, 6F, pa, amp);
			float[] handR = gaitTarget(-14F, ground, -22F, pa, amp);
			float[] handL = gaitTarget(14F, ground, -22F, pb, amp);
			handR[1] -= 6F * lunge; handR[2] -= 10F * lunge;
			handL[1] -= 6F * lunge; handL[2] -= 10F * lunge;

			// 腿：父级是 root（无旋转），直接在 root 局部坐标里解
			solveLimb(this.legR, this.shinR, footR, new float[] {-0.6F, -1F, 0.3F}, fwd, LEG_REACH, crawl);
			solveLimb(this.legL, this.shinL, footL, new float[] {0.6F, -1F, 0.3F}, fwd, LEG_REACH, crawl);

			// 手：父级是 body（已前倾），先把目标换到 body 局部坐标
			float[] b = eulerZYX(bodyX, bodyY, 0F);
			float[] bodyPivot = {this.body.x, this.body.y, this.body.z};
			solveLimb(this.armR, this.forearmR, toLocal(b, bodyPivot, handR), mulT(b, new float[] {-0.6F, -1F, 0.4F}), mulT(b, fwd), ARM_REACH, crawl);
			solveLimb(this.armL, this.forearmL, toLocal(b, bodyPivot, handL), mulT(b, new float[] {0.6F, -1F, 0.4F}), mulT(b, fwd), ARM_REACH, crawl);
		}

		// 视线（站立、爬行通用）
		this.head.yRot += netHeadYaw * deg * 0.7F;
		this.neck.yRot += netHeadYaw * deg * 0.3F;
		this.head.xRot += headPitch * deg * 0.5F;

		// ================= 装死：脸朝下摊开四肢，头歪向一侧 =================
		if (playDead > 0F) {
			blend(this.root, playDead, 0F, PLAY_DEAD_Y, 24F);
			blendRot(this.root, playDead, 90F * deg, 0F, 0F);
			blendRot(this.body, playDead, 0F, 0F, 0F);
			blendRot(this.neck, playDead, 0F, 0F, 0F);
			blendRot(this.head, playDead, 0F, 80F * deg, 0F);
			blendRot(this.armR, playDead, 0F, 0F, 70F * deg);
			blendRot(this.armL, playDead, 0F, 0F, -70F * deg);
			blendRot(this.forearmR, playDead, 0F, 0F, -35F * deg);
			blendRot(this.forearmL, playDead, 0F, 0F, 35F * deg);
			blendRot(this.legR, playDead, 0F, 0F, 18F * deg);
			blendRot(this.legL, playDead, 0F, 0F, -18F * deg);
			blendRot(this.shinR, playDead, 0F, 0F, 0F);
			blendRot(this.shinL, playDead, 0F, 0F, 0F);
		}
	}

	// ------------------------------------------------------------------ 爬行用的小工具

	/** 手/脚在 root 局部坐标里的目标点：stance 时贴地向后划，swing 时抬起向前。 */
	private static float[] gaitTarget(float x, float ground, float z, float phase, float amp) {
		float lift = Math.max(0F, Mth.sin(phase)) * GAIT_LIFT * amp;
		return new float[] {x, ground - lift, z + Mth.cos(phase) * GAIT_STRIDE * amp};
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

	/**
	 * 两段式 IK。target / pole / ref 都在 upper 父级的局部坐标里。
	 * upper 的局部 +Y 指向关节（长度取 lower 的枢轴偏移），lower 的局部 +Y 指向末端（长度 reach）。
	 */
	private static void solveLimb(ModelPart upper, ModelPart lower, float[] target, float[] pole, float[] ref,
								  float reach, float weight) {
		float l1 = lower.y, l2 = reach;
		float[] p = {upper.x, upper.y, upper.z};
		float[] dir = sub(target, p);
		float d = len(dir);
		if (d < 1.0E-4F) {
			dir = new float[] {0F, 1F, 0F};
			d = 1.0E-4F;
		} else {
			dir = scale(dir, 1F / d);
		}
		float dc = Mth.clamp(d, Math.abs(l1 - l2) + 0.01F, l1 + l2 - 0.01F);
		float a = (l1 * l1 - l2 * l2 + dc * dc) / (2F * dc);
		float h = Mth.sqrt(Math.max(0F, l1 * l1 - a * a));
		float[] n = normalize(sub(pole, scale(dir, dot(pole, dir))));
		float[] knee = add(add(p, scale(dir, a)), scale(n, h));
		float[] end = add(p, scale(dir, dc));
		float[] ru = basis(normalize(sub(knee, p)), ref);
		float[] rl = basis(normalize(sub(end, knee)), ref);
		float[] eu = toEulerZYX(ru);
		float[] el = toEulerZYX(mulTM(ru, rl));
		blendRot(upper, weight, eu[0], eu[1], eu[2]);
		blendRot(lower, weight, el[0], el[1], el[2]);
	}

	/** 局部 +Y 对齐 y，局部 -Z 尽量对齐 ref（肢体正面朝前）。返回行主序 3x3，列为局部轴。 */
	private static float[] basis(float[] y, float[] ref) {
		float[] f = sub(ref, scale(y, dot(ref, y)));
		if (len(f) < 1.0E-3F) {
			f = sub(new float[] {0F, -1F, 0F}, scale(y, -y[1]));
		}
		float[] z = scale(normalize(f), -1F);
		float[] x = cross(y, z);
		return new float[] {x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2]};
	}

	/** 与 ModelPart / PoseStack 相同的旋转顺序：R = Rz * Ry * Rx。 */
	private static float[] eulerZYX(float x, float y, float z) {
		float cx = Mth.cos(x), sx = Mth.sin(x), cy = Mth.cos(y), sy = Mth.sin(y), cz = Mth.cos(z), sz = Mth.sin(z);
		return new float[] {
				cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
				sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
				-sy, cy * sx, cy * cx};
	}

	private static float[] toEulerZYX(float[] m) {
		float y = (float) Math.asin(Mth.clamp(-m[6], -1F, 1F));
		float x = (float) Math.atan2(m[7], m[8]);
		float z = (float) Math.atan2(m[3], m[0]);
		return new float[] {x, y, z};
	}

	private static float[] toLocal(float[] rot, float[] origin, float[] v) {
		return mulT(rot, sub(v, origin));
	}

	private static float[] mulT(float[] m, float[] v) {
		return new float[] {
				m[0] * v[0] + m[3] * v[1] + m[6] * v[2],
				m[1] * v[0] + m[4] * v[1] + m[7] * v[2],
				m[2] * v[0] + m[5] * v[1] + m[8] * v[2]};
	}

	private static float[] mulTM(float[] a, float[] b) {
		float[] r = new float[9];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				r[i * 3 + j] = a[i] * b[j] + a[3 + i] * b[3 + j] + a[6 + i] * b[6 + j];
			}
		}
		return r;
	}

	private static float[] add(float[] a, float[] b) { return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
	private static float[] sub(float[] a, float[] b) { return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
	private static float[] scale(float[] a, float s) { return new float[] {a[0] * s, a[1] * s, a[2] * s}; }
	private static float dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
	private static float len(float[] a) { return Mth.sqrt(dot(a, a)); }
	private static float[] normalize(float[] a) { float l = len(a); return l < 1.0E-6F ? new float[] {0F, 1F, 0F} : scale(a, 1F / l); }
	private static float[] cross(float[] a, float[] b) {
		return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}
}
