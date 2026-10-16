package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.suxia.SuxiaSummons;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
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

    public static float cooldownFraction(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || tag.getBoolean("Open")) return 0;
        int duration = tag.getInt("CooldownDuration") == SuxiaSummons.DEATH_COOLDOWN
                ? SuxiaSummons.DEATH_COOLDOWN : SuxiaSummons.COOLDOWN;
        return Mth.clamp((float) tag.getInt("CooldownRemaining") / duration, 0, 1);
    }

    @SubscribeEvent public static void decorations(RegisterItemDecorationsEvent event) {
        event.register(ModItems.SUXIA_POCKET_WATCH.get(), (graphics, font, stack, x, y) -> {
            float fraction = cooldownFraction(stack);
            if (fraction > 0) {
                // Same translucent mask as vanilla, but isolated to the synchronized stack.
                graphics.fill(RenderType.guiOverlay(), x, y + Mth.floor(16 * (1 - fraction)),
                        x + 16, y + 16, 0x7FFFFFFF);
            }
            return false;
        });
    }
    private SuxiaClient() { }
}
