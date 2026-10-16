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

/**
 * 素霞渲染器（MC 1.20.1，Mojang 映射）。召唤版 suxia_summon 和敌对版 suxia 共用：
 *   event.registerEntityRenderer(ModEntities.SUXIA.get(), SuxiaRenderer::new);
 *   event.registerEntityRenderer(ModEntities.SUXIA_SUMMON.get(), SuxiaRenderer::new);
 * - 平时用 suxia.png；isAggressive() 时换成 suxia_angry.png（眼睛赤红），并叠加发光眼睛层。
 * - 爬行、装死等姿态全部由 SuxiaModel.setupAnim 处理，实体实现 SuxiaPoseSource 即可。
 * - 阴影半径 0.6。
 */
public class SuxiaRenderer<T extends Mob> extends MobRenderer<T, SuxiaModel<T>> {
	private static final ResourceLocation TEXTURE = new ResourceLocation("sound_isolating_eraser", "textures/entity/suxia/suxia.png");
	private static final ResourceLocation TEXTURE_ANGRY = new ResourceLocation("sound_isolating_eraser", "textures/entity/suxia/suxia_angry.png");
	private static final RenderType EYES = RenderType.eyes(new ResourceLocation("sound_isolating_eraser", "textures/entity/suxia/suxia_eyes.png"));

	public SuxiaRenderer(EntityRendererProvider.Context context) {
		super(context, new SuxiaModel<>(context.bakeLayer(SuxiaModel.LAYER)), 0.6F);
		this.addLayer(new AngryEyesLayer<>(this));
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return entity.isAggressive() ? TEXTURE_ANGRY : TEXTURE;
	}

	/** 发怒时眼睛自发光（夜里也能看到一点红光）。 */
	static class AngryEyesLayer<T extends Mob> extends EyesLayer<T, SuxiaModel<T>> {
		AngryEyesLayer(RenderLayerParent<T, SuxiaModel<T>> parent) {
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
