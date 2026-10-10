package com.zeropointsix.eraser.registry;

import com.zeropointsix.eraser.ModMain;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ModMain.MOD_ID);

    public static final RegistryObject<SimpleParticleType> WIND_RIBBON =
            PARTICLES.register("wind_ribbon", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> THUNDER_ARC =
            PARTICLES.register("thunder_arc", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> IMPACT_RING =
            PARTICLES.register("impact_ring", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TRAIL_DOT =
            PARTICLES.register("trail_dot", () -> new SimpleParticleType(false));

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
