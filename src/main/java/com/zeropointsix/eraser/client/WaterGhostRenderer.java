package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import com.zeropointsix.eraser.client.WaterGhostModel;

/**
 * 女水鬼渲染器（MC 1.20.1，Mojang 映射）。召唤版 water_ghost_summon 和敌对版 water_ghost 共用：
 *   event.registerEntityRenderer(ModEntities.WATER_GHOST.get(), WaterGhostRenderer::new);
 *   event.registerEntityRenderer(ModEntities.WATER_GHOST_SUMMON.get(), WaterGhostRenderer::new);
 * - 贴图 water_ghost.png；isAggressive() 时叠加发光的眼睛层（泡白的眼珠在水里泛着青光）。
 * - 游泳、抓人等姿态全部由 WaterGhostModel.setupAnim 处理，实体实现 WaterGhostModel.PoseSource 即可。
 *   不要使用原版 Pose.SWIMMING，也不要在这里额外旋转身体。
 * - 阴影半径 0.8。
 */
public class WaterGhostRenderer<T extends Mob> extends MobRenderer<T, WaterGhostModel<T>> {
	private static final ResourceLocation TEXTURE = new ResourceLocation("sound_isolating_eraser", "textures/entity/water_ghost/water_ghost.png");
	private static final RenderType EYES = RenderType.eyes(new ResourceLocation("sound_isolating_eraser", "textures/entity/water_ghost/water_ghost_eyes.png"));

	public WaterGhostRenderer(EntityRendererProvider.Context context) {
		super(context, new WaterGhostModel<>(context.bakeLayer(WaterGhostModel.LAYER)), 0.8F);
		this.addLayer(new GlowingEyesLayer<>(this));
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return TEXTURE;
	}

	/** 发现猎物时眼睛自发光（夜里、水下都能看到两点青白的光）。 */
	static class GlowingEyesLayer<T extends Mob> extends EyesLayer<T, WaterGhostModel<T>> {
		GlowingEyesLayer(RenderLayerParent<T, WaterGhostModel<T>> parent) {
			super(parent);
		}

		@Override
		public RenderType renderType() {
			return EYES;
		}

		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
						   float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
						   float netHeadYaw, float headPitch) {
			if (entity.isAggressive()) {
				super.render(poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
			}
		}
	}
}
