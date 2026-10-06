package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.client.WingsHud;
import com.zeropointsix.eraser.client.WingsLayer;
import com.zeropointsix.eraser.wings.WingsConfig;
import com.zeropointsix.eraser.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client wiring: layer, HUD, FOV, particles, per-tick flight physics. */
public final class WingsClientEvents {
    private WingsClientEvents() {}

    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                LivingEntityRenderer<?, ?> r = event.getSkin(skin);
                if (r instanceof PlayerRenderer pr) {
                    pr.addLayer(new WingsLayer(pr));
                }
            }
        }

        @SubscribeEvent
        public static void layerDefs(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(
                    com.zeropointsix.eraser.client.WingsLayer.LOCATION,
                    com.zeropointsix.eraser.client.WingsModel::createLayer);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(),
                    "wings_hud", WingsHud::render);
        }

        @SubscribeEvent
        public static void particles(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.WIND_RIBBON.get(),
                    sprites -> (type, level, x, y, z, xd, yd, zd) ->
                            new WingsParticles.Ribbon(level, x, y, z, xd, yd, zd, sprites));
            event.registerSpriteSet(ModParticles.THUNDER_ARC.get(),
                    sprites -> (type, level, x, y, z, xd, yd, zd) ->
                            new WingsParticles.Arc(level, x, y, z, xd, yd, zd, sprites));
            event.registerSpriteSet(ModParticles.IMPACT_RING.get(),
                    sprites -> (type, level, x, y, z, xd, yd, zd) ->
                            new WingsParticles.Ring(level, x, y, z, xd, yd, zd, sprites));
            event.registerSpriteSet(ModParticles.TRAIL_DOT.get(),
                    sprites -> (type, level, x, y, z, xd, yd, zd) ->
                            new WingsParticles.Dot(level, x, y, z, xd, yd, zd, sprites));
        }
    }

    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            WingsClientData.tickFlash();
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null) return;
            WingsClientData.WingInfo w = WingsClientData.get(p);
            if (w != null) {
                // deploy animation: 0.3s ease (6 ticks)
                float anim = w.deployAnim();
                float target = w.deployed() ? 1f : 0f;
                float step = 1f / 6f;
                anim += (target > anim ? step : -step);
                anim = Math.max(0, Math.min(1, anim));
                if (anim != w.deployAnim()) WingsClientData.setAnim(p.getId(), anim);
            }
            WingsFlight.tick(p, w != null ? w
                    : WingsClientData.localOrFallback(p));
            // slow other players' anims too (for render)
            for (Player other : mc.level.players()) {
                if (other == p) continue;
                WingsClientData.WingInfo o = WingsClientData.get(other);
                if (o != null) {
                    float a = o.deployAnim();
                    float t = o.deployed() ? 1f : 0f;
                    float na = Math.max(0, Math.min(1, a + (t > a ? 1f / 6f : -1f / 6f)));
                    if (na != a) WingsClientData.setAnim(other.getId(), na);
                }
            }
        }

        /** FOV modifier is a multiplier; +15° at ~70° base ≈ ×1.21. */
        @SubscribeEvent
        public static void onFov(ComputeFovModifierEvent event) {
            if (!WingsConfig.CLIENT.fovBoost.get()) return;
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) return;
            WingsClientData.WingInfo w = WingsClientData.get(p);
            if (w == null || !w.deployed()) return;
            double speed = p.getDeltaMovement().length();
            double ratio = Math.min(1.0, speed / Math.max(1, WingsConfig.SERVER.tierSpeed(3) / 20.0));
            double mult = 1.0 + ratio * WingsConfig.CLIENT.fovBoostMax.get() / 70.0;
            event.setNewFovModifier((float) (event.getFovModifier() * mult));
        }
    }
}
