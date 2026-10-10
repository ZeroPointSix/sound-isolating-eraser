package com.zeropointsix.eraser.registry;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import com.zeropointsix.eraser.shadow.ShadowTanglerEntity;
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

    public static final RegistryObject<EntityType<ShadowTanglerEntity>> SHADOW_TANGLER =
            ENTITIES.register("shadow_tangler", () -> EntityType.Builder
                    .of(ShadowTanglerEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 2.0F).clientTrackingRange(8)
                    .build(ModMain.MOD_ID + ":shadow_tangler"));
}
