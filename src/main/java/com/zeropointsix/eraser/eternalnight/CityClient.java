package com.zeropointsix.eraser.eternalnight;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CityClient {
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                EternalNightContent.NEON_SIGN.get(), net.minecraft.client.renderer.RenderType.cutout()));
    }
    @SubscribeEvent public static void dimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(EternalNightContent.ID, new DimensionSpecialEffects(Float.NaN, false,
                DimensionSpecialEffects.SkyType.NONE, false, false) {
            @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
                return new Vec3(0.012, 0.018, 0.032);
            }
            @Override public boolean isFoggyAt(int x, int z) { return true; }
        });
    }
    private CityClient() { }
}
