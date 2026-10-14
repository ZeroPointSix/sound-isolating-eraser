package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.GravityEquipment;
import com.zeropointsix.eraser.gravity.GravityNetwork;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class GravityClient {
    // Keep the translation key so existing user key bindings survive the redesign.
    public static final KeyMapping ACTIVATE = new KeyMapping("key.sound_isolating_eraser.gravity_target",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
            "key.categories.sound_isolating_eraser");
    private static PoseStack worldView;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean pressed = false;
        while (ACTIVATE.consumeClick()) pressed = true;
        if (mc.isPaused()) return;
        GravitySense.tick(mc);
        if (!pressed || mc.level == null || mc.player == null || !mc.player.isAlive()
                || mc.player.isSpectator() || mc.screen != null || !mc.isWindowActive()
                || !GravityEquipment.isEquipped(mc.player)
                || mc.player.getCooldowns().isOnCooldown(ModItems.GRAVITY_JADE_PENDANT.get())) return;
        GravityNetwork.CHANNEL.sendToServer(new GravityNetwork.Activate());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            // AFTER_LEVEL exposes the projection stack; preserve the actual camera transform.
            worldView = new PoseStack();
            worldView.last().pose().set(event.getPoseStack().last().pose());
            worldView.last().normal().set(event.getPoseStack().last().normal());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && worldView != null) {
            GravityWorldRenderer.render(event, worldView);
            worldView = null;
        }
    }

    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(ACTIVATE); }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.GRAVITY_FIELD.get(), GravityFieldRenderer::new);
        }
    }

    private GravityClient() { }
}
