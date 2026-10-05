package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.GravityConfig;
import com.zeropointsix.eraser.gravity.GravityEquipment;
import com.zeropointsix.eraser.gravity.GravityGeometry;
import com.zeropointsix.eraser.gravity.GravityNetwork;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class GravityClient {
    public static final KeyMapping TARGET = new KeyMapping("key.sound_isolating_eraser.gravity_target",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
            "key.categories.sound_isolating_eraser");
    private static boolean previousDown;
    private static boolean aiming;
    private static boolean cancelUse;
    private static double distance = 8;
    private static double scrollRemainder;
    private static ClientLevel lastLevel;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        cancelUse = false;
        if (mc.isPaused()) { aiming = false; return; }
        GravitySense.tick(mc);
        boolean down = TARGET.isDown();
        if (mc.level != lastLevel || mc.player == null || !mc.player.isAlive()
                || mc.player.isSpectator() || mc.screen != null || !mc.isWindowActive()
                || !GravityEquipment.isEquipped(mc.player)) {
            aiming = false;
            previousDown = down;
            lastLevel = mc.level;
            return;
        }
        if (down && !previousDown && !mc.player.getCooldowns().isOnCooldown(ModItems.GRAVITY_JADE_PENDANT.get())) {
            aiming = true;
            scrollRemainder = 0;
            distance = Math.max(GravityConfig.minDistance(), Math.min(8, GravityConfig.MAX_DISTANCE.get()));
        }
        if (!down && previousDown && aiming) {
            aiming = false;
            GravityNetwork.CHANNEL.sendToServer(new GravityNetwork.Activate(
                    target(mc, 1F), mc.level.dimension().location()));
        }
        previousDown = down;
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (aiming) {
            scrollRemainder += event.getScrollDelta();
            int steps = (int) scrollRemainder;
            scrollRemainder -= steps;
            distance = Math.max(GravityConfig.minDistance(),
                    Math.min(GravityConfig.MAX_DISTANCE.get(), distance + steps));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE && event.getAction() == GLFW.GLFW_PRESS) aiming = false;
    }

    @SubscribeEvent
    public static void mouse(InputEvent.MouseButton.Pre event) {
        if (aiming && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
            aiming = false;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        if ((aiming || cancelUse) && event.isUseItem()) {
            aiming = false;
            // Forge fires one use event per hand; consume both when use is rebound.
            cancelUse = true;
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    public static BlockPos target(Minecraft mc, float partialTick) {
        return GravityGeometry.target(mc.player.getEyePosition(partialTick), mc.player.getViewVector(partialTick), distance);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        GravityWorldRenderer.render(event, aiming);
    }

    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(TARGET); }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.GRAVITY_FIELD.get(), GravityFieldRenderer::new);
        }
    }

    private GravityClient() { }
}
