package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.eraser.EraserNetwork;
import com.zeropointsix.eraser.rednails.RedNailsItem;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.function.BiConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class RedNailsClient {
    public static final KeyMapping KEY = new KeyMapping("key." + ModMain.MOD_ID + ".red_nails",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories." + ModMain.MOD_ID);
    private static boolean down, blocked, renderersReady;
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (!renderersReady && mc.getOverlay() == null && mc.getEntityRenderDispatcher() != null) {
            bindGhostRenderers((type, provider) -> EntityRenderers.register(type, provider));
            mc.getEntityRenderDispatcher().onResourceManagerReload(mc.getResourceManager());
            renderersReady = true;
            System.out.println("RED_NAILS_RENDERERS rebuilt after overlay");
        }
        if (mc.player == null || mc.getConnection() == null) { down = false; ticks = 0; blocked = true; return; }
        boolean key = KEY.isDown();
        if (!key) blocked = false;
        boolean held = key && mc.screen == null && mc.isWindowActive() && !blocked;
        if (down && !held) {
            EraserNetwork.CHANNEL.sendToServer(new EraserNetwork.RedNailsKey(false));
            ticks = 0;
            if (key) blocked = true;
        }
        if (held && !down) EraserNetwork.CHANNEL.sendToServer(new EraserNetwork.RedNailsKey(true));
        down = held;
        if (!held || RedNailsItem.findWorn(mc.player).isEmpty() || ++ticks > 20) return;
        int count = Math.min(5, ticks / 4);
        mc.player.displayClientMessage(Component.translatable(RedNailsItem.MESSAGE + "chanting",
                "\u25a0".repeat(count) + "\u25a1".repeat(5 - count)), true);
        mc.level.addParticle(ParticleTypes.DRIPPING_WATER, mc.player.getX() + (Math.random() - 0.5),
                mc.player.getY() + 1, mc.player.getZ() + (Math.random() - 0.5), 0, 0, 0);
        if (ticks % 5 == 0) mc.player.playSound(SoundEvents.GENERIC_SWIM, 0.2F, 0.5F);
    }
    @SuppressWarnings("unchecked")
    static void bindGhostRenderers(BiConsumer<EntityType<? extends Mob>, EntityRendererProvider<Mob>> register) {
        register.accept(ModEntities.WATER_GHOST.get(), WaterGhostRenderer::new);
        register.accept(ModEntities.WATER_GHOST_SUMMON.get(), WaterGhostRenderer::new);
        for (String path : new String[]{"water_ghost", "water_ghost_summon"}) {
            BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(ModMain.MOD_ID, path)).ifPresent(type ->
                    register.accept((EntityType<? extends Mob>) type, WaterGhostRenderer::new));
        }
    }
    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(KEY); }
        @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(WaterGhostModel.LAYER, WaterGhostModel::createBodyLayer);
        }
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            bindGhostRenderers((type, provider) -> event.registerEntityRenderer(type, provider));
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager ->
                    bindGhostRenderers((type, provider) -> EntityRenderers.register(type, provider)));
        }
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                RedNailsItem.keyName = KEY::getTranslatedKeyMessage;
                ItemProperties.register(ModItems.RED_NAILS.get(), new ResourceLocation(ModMain.MOD_ID, "fallen_nails"),
                        (stack, level, entity, seed) -> RedNailsItem.coolingCount(stack) / 4F);
            });
        }
    }
    private RedNailsClient() { }
}
