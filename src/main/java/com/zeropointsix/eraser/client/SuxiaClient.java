package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SuxiaClient {
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SuxiaModel.LAYER, SuxiaModel::createBodyLayer);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SUXIA.get(), SuxiaRenderer::new);
        event.registerEntityRenderer(ModEntities.SUXIA_SUMMON.get(), SuxiaRenderer::new);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.SUXIA_POCKET_WATCH.get(),
                new ResourceLocation(ModMain.MOD_ID, "open"),
                (stack, level, entity, seed) -> stack.hasTag() && stack.getTag().getBoolean("Open") ? 1F : 0F));
    }

    @Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
    public static final class Input {
        @SubscribeEvent public static void use(InputEvent.InteractionKeyMappingTriggered event) {
            var mc = Minecraft.getInstance();
            if (!event.isUseItem() || mc.player == null) return;
            var stack = mc.player.getItemInHand(event.getHand());
            if (!stack.is(ModItems.SUXIA_POCKET_WATCH.get()) || !stack.hasTag()
                    || !mc.player.getCooldowns().isOnCooldown(stack.getItem())) return;
            var tag = stack.getTag();
            if (!tag.hasUUID("Owner") || !tag.getUUID("Owner").equals(mc.player.getUUID()) || tag.getBoolean("Open")) return;
            // Vanilla rejects use before Item.use while its type-wide cooldown is active.
            long seconds = Math.max(0, (tag.getInt("CooldownRemaining") + 19) / 20);
            mc.player.displayClientMessage(Component.translatable(
                    "message.sound_isolating_eraser.suxia_pocket_watch.cooldown",
                    String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)), true);
        }
        private Input() { }
    }
    private SuxiaClient() { }
}
