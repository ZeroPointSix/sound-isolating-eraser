package com.zeropointsix.eraser.registry;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ModMain.MOD_ID);

    public static final RegistryObject<SoundEvent> WIND_LOOP =
            register("wind_loop");
    public static final RegistryObject<SoundEvent> THUNDER_BOOM =
            register("thunder_boom");
    public static final RegistryObject<SoundEvent> THUNDER_BLINK =
            register("thunder_blink");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(
                        new ResourceLocation(ModMain.MOD_ID, name)));
    }

    private ModSounds() {
    }
}
