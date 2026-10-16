package com.zeropointsix.eraser.registry;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import com.zeropointsix.eraser.shadow.ShadowTanglerEntity;
import com.zeropointsix.eraser.neon.NeonTumorEntity;
import com.zeropointsix.eraser.suxia.SuxiaEntity;
import com.zeropointsix.eraser.suxia.SuxiaSummonEntity;
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

    public static final RegistryObject<EntityType<com.zeropointsix.eraser.rednails.WaterGhostEntity>> WATER_GHOST =
            ENTITIES.register("water_ghost", () -> EntityType.Builder.of(com.zeropointsix.eraser.rednails.WaterGhostEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 2.4F).clientTrackingRange(10).build(ModMain.MOD_ID + ":water_ghost"));
    public static final RegistryObject<EntityType<com.zeropointsix.eraser.rednails.WaterGhostSummonEntity>> WATER_GHOST_SUMMON =
            ENTITIES.register("water_ghost_summon", () -> EntityType.Builder.of(com.zeropointsix.eraser.rednails.WaterGhostSummonEntity::new, MobCategory.MISC)
                    .sized(1.2F, 2.4F).clientTrackingRange(10).build(ModMain.MOD_ID + ":water_ghost_summon"));

    public static final RegistryObject<EntityType<SuxiaEntity>> SUXIA =
            ENTITIES.register("suxia", () -> EntityType.Builder.of(SuxiaEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 2.9F).clientTrackingRange(10).build(ModMain.MOD_ID + ":suxia"));
    public static final RegistryObject<EntityType<SuxiaSummonEntity>> SUXIA_SUMMON =
            ENTITIES.register("suxia_summon", () -> EntityType.Builder.of(SuxiaSummonEntity::new, MobCategory.MISC)
                    .sized(0.6F, 2.9F).clientTrackingRange(10).build(ModMain.MOD_ID + ":suxia_summon"));

    public static final RegistryObject<EntityType<ShadowTanglerEntity>> SHADOW_TANGLER =
            ENTITIES.register("shadow_tangler", () -> EntityType.Builder
                    .of(ShadowTanglerEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 2.0F).clientTrackingRange(8)
                    .build(ModMain.MOD_ID + ":shadow_tangler"));

    public static final RegistryObject<EntityType<NeonTumorEntity>> NEON_TUMOR =
            ENTITIES.register("neon_tumor", () -> EntityType.Builder
                    .of(NeonTumorEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 1.1F).clientTrackingRange(10)
                    .build(ModMain.MOD_ID + ":neon_tumor"));
}
