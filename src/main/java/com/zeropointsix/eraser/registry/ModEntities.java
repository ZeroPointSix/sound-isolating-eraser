package com.zeropointsix.eraser.registry;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import com.zeropointsix.eraser.neon.NeonTumorEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ModMain.MOD_ID);
    public static final RegistryObject<EntityType<GravityFieldEntity>> GRAVITY_FIELD =
            ENTITIES.register("gravity_field", () -> EntityType.Builder
                    .<GravityFieldEntity>of(GravityFieldEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F).clientTrackingRange(64).updateInterval(20)
                    .setShouldReceiveVelocityUpdates(false)
                    .build(ModMain.MOD_ID + ":gravity_field"));

    private ModEntities() { }

    public static final RegistryObject<EntityType<NeonTumorEntity>> NEON_TUMOR =
            ENTITIES.register("neon_tumor", () -> EntityType.Builder
                    .of(NeonTumorEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 1.1F).clientTrackingRange(10)
                    .build(ModMain.MOD_ID + ":neon_tumor"));
}
