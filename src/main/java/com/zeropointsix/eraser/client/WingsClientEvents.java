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
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
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
        public static void reloadModels(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(WingsMesh.INSTANCE);
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
                WingsClientData.tickAnimation(p, w);
            }
            WingsFlight.tick(p, w != null ? w
                    : WingsClientData.localOrFallback(p));
            // slow other players' anims too (for render)；并按同步状态给远端玩家
            // 本地生成持续飞行特效/风声（服务器不广播普通飞行 FX，客户端各自表现）
            for (Player other : mc.level.players()) {
                if (other == p) continue;
                WingsClientData.WingInfo o = WingsClientData.get(other);
                if (o != null) {
                    WingsClientData.tickAnimation(other, o);
                    WingsFlight.remoteFx(other, o);
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
