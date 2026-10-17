package com.zeropointsix.eraser.yuji.flare.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.yuji.flare.FlareGunPose;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.util.RenderUtils;

/** Client-only attachment to the rendered barrel, including arm, item and animated bone transforms. */
@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class FlareGunMuzzleClient {
    private static final Map<Long, Shot> SHOTS = new HashMap<>();
    private static final Matrix4f VIEW_TO_WORLD = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static ClientLevel level;

    private static final class Shot {
        final int owner;
        final long expires;
        Particle flash;
        boolean emitted;
        Shot(int owner, long expires) { this.owner = owner; this.expires = expires; }
    }

    public static void shot(int owner, long stack) {
        var current = Minecraft.getInstance().level;
        if (current == null) return;
        if (level != current) { SHOTS.clear(); level = current; }
        SHOTS.put(stack, new Shot(owner, current.getGameTime() + 10));
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var current = Minecraft.getInstance().level;
        if (level != current) { SHOTS.clear(); level = current; }
        if (current != null) SHOTS.values().removeIf(shot -> shot.expires < current.getGameTime());
    }

    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        VIEW_TO_WORLD.set(event.getPoseStack().last().pose()).invert();
        camera = event.getCamera().getPosition();
    }

    @SubscribeEvent public static void before(GeoRenderEvent.Item.Pre event) {
        if (event.getRenderer() instanceof FlareGunRenderer renderer)
            renderer.getGeoModel().getBone("barrel").ifPresent(bone -> bone.setTrackingMatrices(true));
    }

    @SubscribeEvent public static void after(GeoRenderEvent.Item.Post event) {
        if (!(event.getRenderer() instanceof FlareGunRenderer renderer) || level == null) return;
        ItemDisplayContext view = renderer.perspective();
        boolean first = view == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || view == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        if (!first && view != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && view != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) return;
        long stack = GeoItem.getId(renderer.getCurrentItemStack());
        Shot shot = SHOTS.get(stack);
        if (shot == null || shot.expires < level.getGameTime()
                || !(level.getEntity(shot.owner) instanceof Player player)
                || GeoItem.getId(player.getMainHandItem()) != stack) return;

        Vec3 muzzle;
        if (first) {
            // Preserve the accepted first-person camera-space placement.
            if (player != Minecraft.getInstance().player) return;
            muzzle = FlareGunPose.muzzle(player);
        } else {
            var bone = renderer.getGeoModel().getBone("barrel").orElseThrow();
            PoseStack barrel = new PoseStack();
            barrel.mulPoseMatrix(new Matrix4f(event.getPoseStack().last().pose()).mul(bone.getModelSpaceMatrix()));
            // GeckoLib's tracked item matrix is the parent transform, before this bone's pivot/rotation.
            RenderUtils.prepMatrixForBone(barrel, bone);
            Vector3f point = barrel.last().pose().transformPosition(new Vector3f(0, 10F / 16, -8.4F / 16));
            VIEW_TO_WORLD.transformPosition(point);
            muzzle = camera.add(point.x(), point.y(), point.z());
        }
        if (!shot.emitted) {
            shot.emitted = true;
            var engine = Minecraft.getInstance().particleEngine;
            shot.flash = engine.createParticle(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 0, 0, 0);
            java.util.ArrayList<Particle> flames = new java.util.ArrayList<>();
            java.util.ArrayList<Particle> smoke = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) flames.add(engine.createParticle(ParticleTypes.SMALL_FLAME,
                    muzzle.x, muzzle.y, muzzle.z, 0, .015, 0));
            for (int i = 0; i < 2; i++) smoke.add(engine.createParticle(ParticleTypes.SMOKE,
                    muzzle.x, muzzle.y, muzzle.z, 0, .012, 0));
            MinecraftForge.EVENT_BUS.post(new Emitted(player.getId(), view, shot.flash, flames, smoke));
        } else if (shot.flash != null && shot.flash.isAlive()) {
            shot.flash.setPos(muzzle.x, muzzle.y, muzzle.z);
        }
    }

    /** Provides the actual created particles to client integrations, not a predicted network coordinate. */
    public static final class Emitted extends Event {
        public final int owner;
        public final ItemDisplayContext view;
        public final Particle flash;
        public final List<Particle> flames, smoke;
        private Emitted(int owner, ItemDisplayContext view, Particle flash, List<Particle> flames, List<Particle> smoke) {
            this.owner = owner; this.view = view; this.flash = flash;
            this.flames = java.util.Collections.unmodifiableList(flames);
            this.smoke = java.util.Collections.unmodifiableList(smoke);
        }
    }

    private FlareGunMuzzleClient() { }
}
