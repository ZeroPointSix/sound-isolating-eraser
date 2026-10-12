package com.zeropointsix.eraser.yuji.city;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.yuji.YujiContent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RainCityClient {
    private static long elapsed, bellAge = -1;
    public static void update(long time, long age) { elapsed = time; bellAge = age; }
    private static boolean inCity() { return Minecraft.getInstance().level != null && RainCityContent.isCity(Minecraft.getInstance().level); }
    private static boolean black() { return elapsed >= RainCityRules.FIRST_NIGHT; }
    private static boolean airFog(Camera camera) {
        if (!inCity() || camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return false;
        return !(camera.getEntity() instanceof net.minecraft.world.entity.LivingEntity entity)
                || (!entity.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) && !entity.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(RainCityContent.BUDDHA.get(), BuddhaRenderer::new); }
    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(RainCityContent.HOLY_LIGHT.get(), sprites -> (p, l, x, y, z, dx, dy, dz) -> new CityParticle(l, x, y, z, dx, dy, dz, sprites, true));
        e.registerSpriteSet(RainCityContent.BLACK_SPLASH.get(), sprites -> (p, l, x, y, z, dx, dy, dz) -> new CityParticle(l, x, y, z, dx, dy, dz, sprites, false));
    }
    @SubscribeEvent public static void dimension(RegisterDimensionSpecialEffectsEvent e) {
        e.register(RainCityContent.LEVEL.location(), new DimensionSpecialEffects(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, false) {
            @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return black() ? new Vec3(35 / 255., 38 / 255., 44 / 255.) : new Vec3(125 / 255., 133 / 255., 143 / 255.); }
            @Override public boolean isFoggyAt(int x, int z) { return true; }
            @Override public boolean renderSnowAndRain(ClientLevel level, int ticks, float partial, LightTexture light, double x, double y, double z) {
                rain(level, ticks + partial, x, y, z); return true;
            }
            @Override public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
                if (!black()) return false;
                if (ticks % 2 == 0) {
                    Vec3 eye = camera.getPosition();
                    BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(eye.x + level.random.nextInt(17) - 8, eye.y, eye.z + level.random.nextInt(17) - 8));
                    if (Math.abs(pos.getY() - eye.y) < 8) level.addParticle(RainCityContent.BLACK_SPLASH.get(), pos.getX() + .5, pos.getY() + .05, pos.getZ() + .5, 0, .03, 0);
                    if (ticks % 80 == 0) level.playLocalSound(eye.x, eye.y, eye.z, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, .2F, .8F, false);
                }
                return true;
            }
        });
    }
    private static void rain(ClientLevel level, float time, double camX, double camY, double camZ) {
        var tess = Tesselator.getInstance(); var b = tess.getBuilder();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, YujiContent.id("textures/environment/" + (black() ? "rain_black.png" : "rain_grey.png")));
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int ox = (int) Math.floor(camX), oz = (int) Math.floor(camZ);
        for (int x = ox - 10; x <= ox + 10; x++) for (int z = oz - 10; z <= oz + 10; z++) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            double low = Math.max(top, camY - 10), high = Math.max(top, camY + 10);
            if (high <= low) continue;
            double rx = x + .5 - camX, rz = z + .5 - camZ, distance = Math.sqrt(rx * rx + rz * rz);
            if (distance < .1 || distance > 10) continue;
            double dx = -rz / distance * .5, dz = rx / distance * .5;
            float alpha = (float) ((1 - distance / 12) * .75), v = time * .035F + ((x * 31 + z * 17) & 31) / 32F;
            b.vertex(rx - dx, high - camY, rz - dz).uv(0, (float) high / 4 + v).color(1F, 1F, 1F, alpha).endVertex();
            b.vertex(rx + dx, high - camY, rz + dz).uv(1, (float) high / 4 + v).color(1F, 1F, 1F, alpha).endVertex();
            b.vertex(rx + dx, low - camY, rz + dz).uv(1, (float) low / 4 + v).color(1F, 1F, 1F, alpha).endVertex();
            b.vertex(rx - dx, low - camY, rz - dz).uv(0, (float) low / 4 + v).color(1F, 1F, 1F, alpha).endVertex();
        }
        tess.end(); RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend();
    }
    private static final class BuddhaRenderer extends GeoEntityRenderer<RecliningBuddha> {
        BuddhaRenderer(EntityRendererProvider.Context context) {
            super(context, new GeoModel<>() {
                @Override public ResourceLocation getModelResource(RecliningBuddha e) { return YujiContent.id("geo/reclining_buddha.geo.json"); }
                @Override public ResourceLocation getTextureResource(RecliningBuddha e) { return YujiContent.id("textures/entity/reclining_buddha.png"); }
                @Override public ResourceLocation getAnimationResource(RecliningBuddha e) { return YujiContent.id("animations/reclining_buddha.animation.json"); }
            });
            scaleWidth = scaleHeight = 2; shadowRadius = 0;
        }
    }
    private static final class CityParticle extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final boolean light;
        CityParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, boolean light) {
            super(level, x, y, z, dx, dy, dz); this.sprites = sprites; this.light = light;
            xd = dx; yd = dy; zd = dz; lifetime = light ? 32 : 8; quadSize = light ? .3F : .08F;
            gravity = light ? -.03F : .2F; setSpriteFromAge(sprites);
        }
        @Override public void tick() { super.tick(); setSpriteFromAge(sprites); }
        @Override public int getLightColor(float partial) { return light ? 15728880 : super.getLightColor(partial); }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    }
    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
    public static final class Runtime {
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            if (!inCity()) { elapsed = 0; bellAge = -1; }
            else if (!Minecraft.getInstance().isPaused()) { elapsed++; if (bellAge >= 0) bellAge++; }
        }
        @SubscribeEvent public static void fog(ViewportEvent.RenderFog e) {
            if (!airFog(e.getCamera())) return;
            e.setNearPlaneDistance(0); e.setFarPlaneDistance(black() ? 32 : 64); e.setCanceled(true);
        }
        @SubscribeEvent public static void color(ViewportEvent.ComputeFogColor e) {
            if (!airFog(e.getCamera())) return;
            int color = black() ? 0x23262C : 0x7D858F;
            e.setRed(((color >> 16) & 255) / 255F); e.setGreen(((color >> 8) & 255) / 255F); e.setBlue((color & 255) / 255F);
        }
        @SubscribeEvent public static void beam(RenderLevelStageEvent e) {
            if (!inCity() || bellAge < 92 || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
            var pose = e.getPoseStack(); var camera = e.getCamera().getPosition();
            pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
            var matrix = pose.last().pose(); var tess = Tesselator.getInstance(); var b = tess.getBuilder();
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, YujiContent.id("textures/entity/holy_light_beam.png"));
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int axis = 0; axis < 2; axis++) {
                float x = axis == 0 ? 7 : 0, z = axis == 0 ? 0 : 7;
                b.vertex(matrix, -x, 10, -z).uv(0, 1).color(1F, 1F, 1F, .8F).endVertex();
                b.vertex(matrix, x, 10, z).uv(1, 1).color(1F, 1F, 1F, .8F).endVertex();
                b.vertex(matrix, x, 142, z).uv(1, 0).color(1F, 1F, 1F, .5F).endVertex();
                b.vertex(matrix, -x, 142, -z).uv(0, 0).color(1F, 1F, 1F, .5F).endVertex();
            }
            tess.end(); RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend(); pose.popPose();
        }
    }
    private RainCityClient() { }
}
