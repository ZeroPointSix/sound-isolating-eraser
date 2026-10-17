package com.zeropointsix.eraser.keeper.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.keeper.KeeperData;
import com.zeropointsix.eraser.registry.ModEffects;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class KeeperVisuals {
    private static final ResourceLocation FILTER = new ResourceLocation(ModMain.MOD_ID, "shaders/post/keeper_photo.json");
    private static final ResourceLocation FRAME = new ResourceLocation(ModMain.MOD_ID, "textures/gui/photo_world_frame.png");
    private static final long FLASH_NANOS = 200_000_000L;
    private static PostChain filter;
    private static boolean failed;
    private static boolean awaitRelease;
    private static int width, height;
    private static long flashUntil;
    private static boolean flashPending;
    private static boolean flashFrameDrawn;
    private static final Set<UUID> HIDDEN = new HashSet<>();

    private static boolean active() {
        var player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(ModEffects.IN_PHOTO.get());
    }
    public static void flash() {
        flashUntil = 0;
        flashPending = true;
        flashFrameDrawn = false;
    }
    public static void framePresented() {
        if (flashPending && flashFrameDrawn) {
            // Rendering may queue work; only start the interval after the first buffer swap.
            flashUntil = System.nanoTime() + FLASH_NANOS;
            flashPending = false;
            flashFrameDrawn = false;
        }
    }
    public static void visibility(UUID player, boolean hidden) {
        if (hidden) HIDDEN.add(player); else HIDDEN.remove(player);
        var mc = Minecraft.getInstance();
        if (hidden && mc.player != null && mc.player.getUUID().equals(player)) awaitRelease = mc.options.keyUse.isDown();
    }
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && awaitRelease) { event.setCanceled(true); event.setSwingHand(false); }
    }
    private static void close() {
        if (filter != null) { filter.close(); filter = null; }
        width = 0; height = 0;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (!mc.options.keyUse.isDown()) awaitRelease = false;
        else if (mc.player != null && mc.player.isUsingItem()
                && mc.player.getUseItem().is(ModItems.KEEPER_PHOTO.get())
                && mc.player.getUseItemRemainingTicks() <= 1) awaitRelease = true;
        if (!active()) close();
        if (Minecraft.getInstance().player == null) { flashUntil = 0; flashPending = false; flashFrameDrawn = false; failed = false; awaitRelease = false; HIDDEN.clear(); }
    }
    @SubscribeEvent public static void hidePlayer(RenderPlayerEvent.Pre event) {
        if (HIDDEN.contains(event.getEntity().getUUID())
                || event.getEntity() == Minecraft.getInstance().player && event.getEntity().hasEffect(ModEffects.IN_PHOTO.get())) event.setCanceled(true);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !active() || failed) return;
        var mc = Minecraft.getInstance();
        try {
            if (filter == null) filter = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), FILTER);
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            if (width != w || height != h) { filter.resize(w, h); width = w; height = h; }
            filter.process(event.getPartialTick());
        } catch (Exception error) {
            failed = true; close();
            LogUtils.getLogger().warn("Keeper photo filter unavailable; using the tint fallback", error);
        } finally { mc.getMainRenderTarget().bindWrite(true); }
    }

    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> ItemProperties.register(ModItems.KEEPER_PHOTO.get(), new ResourceLocation(ModMain.MOD_ID, "fade"),
                    (stack, level, entity, seed) -> KeeperData.blank(stack) ? 1F : stack.getTag().getInt("Uses") / 8F));
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> { close(); failed = false; });
        }
        @SubscribeEvent public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("keeper_photo", (overlay, gui, partialTick, w, h) -> {
                var mc = Minecraft.getInstance();
                if (active()) {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    if (failed) gui.fill(0, 0, w, h, 0x407D5938);
                    gui.blit(FRAME, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
                    int remaining = mc.player.getEffect(ModEffects.IN_PHOTO.get()).getDuration();
                    var text = Component.translatable("hud." + ModMain.MOD_ID + ".keeper_photo.remaining", Math.max(0, (remaining + 19) / 20));
                    gui.drawCenteredString(mc.font, text, w / 2, h * 238 / 256 - mc.font.lineHeight / 2, 0x5A4632);
                    RenderSystem.disableBlend();
                }
                long now = System.nanoTime();
                long left = flashPending ? FLASH_NANOS : flashUntil == 0 ? 0 : flashUntil - now;
                if (left > 0) {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    gui.fill(0, 0, w, h, (Math.round(255F * Math.min(1F, left / (float) FLASH_NANOS)) << 24) | 0xFFFFFF);
                    if (flashPending) flashFrameDrawn = true;
                    RenderSystem.disableBlend();
                }
            });
        }
    }
    private KeeperVisuals() { }
}
