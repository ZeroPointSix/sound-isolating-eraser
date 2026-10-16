package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.eraser.EraserNetwork;
import com.zeropointsix.eraser.rednails.RedNailsItem;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class RedNailsClient {
    public static final KeyMapping KEY = new KeyMapping("key." + ModMain.MOD_ID + ".red_nails",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories." + ModMain.MOD_ID);
    private static boolean down, blocked;
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { down = false; ticks = 0; blocked = true; return; }
        boolean key = KEY.isDown();
        if (!key) blocked = false;
        boolean qa = System.getProperty("rednails.qa.results") != null;
        boolean held = key && mc.screen == null && (qa || mc.isWindowActive()) && !blocked;
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
    @SubscribeEvent public static void entityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) ensureRenderer(event.getEntity());
    }
    public static void ensureRenderer(Entity entity) {
        if (entity != null) aliasLiveGhostType(entity.getType());
    }
    static boolean isGhostType(EntityType<?> type) {
        ResourceLocation key = EntityType.getKey(type);
        return key != null && ModMain.MOD_ID.equals(key.getNamespace())
                && ("water_ghost".equals(key.getPath()) || "water_ghost_summon".equals(key.getPath()));
    }
    @SuppressWarnings("unchecked")
    static Set<EntityType<? extends Mob>> ghostTypes() {
        var types = new LinkedHashSet<EntityType<? extends Mob>>();
        types.add(ModEntities.WATER_GHOST.get());
        types.add(ModEntities.WATER_GHOST_SUMMON.get());
        for (String path : new String[]{"water_ghost", "water_ghost_summon"}) {
            BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(ModMain.MOD_ID, path))
                    .ifPresent(type -> types.add((EntityType<? extends Mob>) type));
        }
        return types;
    }
    static boolean overlaysBaked(EntityModelSet models) {
        if (models == null) return false;
        try {
            models.bakeLayer(ModelLayers.EVOKER);
            models.bakeLayer(WaterGhostModel.LAYER);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
    static void attachGhostRenderers(EntityRendererProvider.Context context) {
        bindGhostRenderers(EntityRenderers::register);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (dispatcher == null) return;
        Map<EntityType<?>, EntityRenderer<?>> map = rendererMap(dispatcher);
        if (map == null) return;
        EntityRenderer<?> baked = null;
        for (EntityRenderer<?> renderer : map.values()) {
            if (renderer instanceof WaterGhostRenderer) { baked = renderer; break; }
        }
        if (baked == null && context != null && overlaysBaked(context.getModelSet())) {
            baked = new WaterGhostRenderer<>(context);
        }
        if (baked == null) return;
        Map<EntityType<?>, EntityRenderer<?>> copy = new IdentityHashMap<>(map);
        boolean changed = false;
        for (EntityType<? extends Mob> type : ghostTypes()) {
            if (!copy.containsKey(type)) {
                copy.put(type, baked);
                changed = true;
            }
        }
        if (changed) setRendererMap(dispatcher, copy);
    }
    @SuppressWarnings("unchecked")
    static void aliasLiveGhostType(EntityType<?> type) {
        if (!isGhostType(type)) return;
        EntityRenderers.register((EntityType<Mob>) type, WaterGhostRenderer::new);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (dispatcher == null) return;
        Map<EntityType<?>, EntityRenderer<?>> map = rendererMap(dispatcher);
        if (map == null || map.containsKey(type)) return;
        EntityRenderer<?> baked = null;
        for (EntityRenderer<?> renderer : map.values()) {
            if (renderer instanceof WaterGhostRenderer) { baked = renderer; break; }
        }
        if (baked == null) return;
        Map<EntityType<?>, EntityRenderer<?>> copy = new IdentityHashMap<>(map);
        copy.put(type, baked);
        setRendererMap(dispatcher, copy);
    }
    @SuppressWarnings("unchecked")
    static Map<EntityType<?>, EntityRenderer<?>> rendererMap(EntityRenderDispatcher dispatcher) {
        for (String name : new String[]{"renderers", "f_114362_"}) {
            try {
                Field field = EntityRenderDispatcher.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(dispatcher);
                if (value instanceof Map<?, ?> map) return (Map<EntityType<?>, EntityRenderer<?>>) map;
            } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }
    static void setRendererMap(EntityRenderDispatcher dispatcher, Map<EntityType<?>, EntityRenderer<?>> map) {
        for (String name : new String[]{"renderers", "f_114362_"}) {
            try {
                Field field = EntityRenderDispatcher.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(dispatcher, map);
                return;
            } catch (ReflectiveOperationException ignored) { }
        }
    }
    static void bindGhostRenderers(BiConsumer<EntityType<? extends Mob>, EntityRendererProvider<Mob>> register) {
        for (EntityType<? extends Mob> type : ghostTypes()) register.accept(type, WaterGhostRenderer::new);
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
                    bindGhostRenderers(EntityRenderers::register));
        }
        @SubscribeEvent public static void addLayers(EntityRenderersEvent.AddLayers event) {
            if (!overlaysBaked(event.getEntityModels())) return;
            attachGhostRenderers(event.getContext());
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
