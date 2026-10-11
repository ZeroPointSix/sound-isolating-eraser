package com.zeropointsix.eraser.yuji;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class YujiContent {
    public static final String ID = "qihai";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ID);
    public static final Map<YujiKind, RegistryObject<EntityType<YujiMonster>>> MOBS = new EnumMap<>(YujiKind.class);
    public static final Map<String, RegistryObject<SoundEvent>> SOUND = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<SimpleParticleType>> PARTICLE = new LinkedHashMap<>();
    public static final RegistryObject<MobEffect> CURSED = effect("cursed", 0x781722);
    public static final RegistryObject<MobEffect> BETRAYED = effect("betrayed", 0xB04654);
    public static final RegistryObject<MobEffect> ENTHRALLED = effect("enthralled", 0xE2DCD6);
    public static final RegistryObject<EntityType<BoneSpike>> BONE_SPIKE = ENTITIES.register("bone_spike",
            () -> EntityType.Builder.<BoneSpike>of(BoneSpike::new, MobCategory.MISC).sized(.25F, .25F)
                    .clientTrackingRange(8).updateInterval(1).build("qihai:bone_spike"));
    static {
        for (YujiKind kind : YujiKind.values()) {
            RegistryObject<EntityType<YujiMonster>> entity = ENTITIES.register(kind.id(), () -> {
                var builder = EntityType.Builder.<YujiMonster>of((type, level) -> new YujiMonster(kind, type, level), MobCategory.MONSTER)
                        .sized(kind.width, kind.height).clientTrackingRange(kind.giant() ? 16 : 10).updateInterval(1);
                if (kind == YujiKind.SHIFOTOU) builder.fireImmune();
                return builder.build(ID + ":" + kind.id());
            });
            MOBS.put(kind, entity);
            ITEMS.register(kind.id() + "_spawn_egg", () -> new ForgeSpawnEggItem(entity, kind.eggBase, kind.eggSpot, new Item.Properties()));
        }
        try (var stream = YujiContent.class.getResourceAsStream("/assets/qihai/sounds.json")) {
            if (stream == null) throw new IllegalStateException("Missing Yuji sound manifest");
            for (String sound : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().keySet()) {
                SOUND.put(sound, SOUNDS.register(sound, () -> SoundEvent.createVariableRangeEvent(id(sound))));
            }
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        for (String particle : new String[]{"karma_wisp", "blade_slash", "snatch_glint", "scream_ring", "curse_glyph",
                "bone_shard", "dust_ring", "skin_flake", "voice_ripple", "discord_twist", "sweet_haze", "ink_note",
                "stone_debris", "black_ooze", "eye_glow", "betray_mark", "curse_aura", "lure_pull", "skin_absorb"}) {
            PARTICLE.put(particle, PARTICLES.register(particle, () -> new SimpleParticleType(false)));
        }
    }
    public static ResourceLocation id(String path) { return new ResourceLocation(ID, path); }
    private static RegistryObject<MobEffect> effect(String name, int color) {
        return EFFECTS.register(name, () -> new MobEffect(MobEffectCategory.HARMFUL, color) {
            @Override public java.util.List<ItemStack> getCurativeItems() { return java.util.List.of(); }
        });
    }
    public static SoundEvent sound(String name) {
        var sound = SOUND.get(name);
        return sound == null ? null : sound.get();
    }
    public static void register(IEventBus bus) {
        ENTITIES.register(bus); ITEMS.register(bus); EFFECTS.register(bus); SOUNDS.register(bus); PARTICLES.register(bus);
        bus.addListener(YujiContent::attributes);
        bus.addListener(YujiContent::creative);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, YujiConfig.SPEC, "qihai-yuji-server.toml");
        YujiNetwork.register();
    }
    private static void attributes(EntityAttributeCreationEvent event) { MOBS.forEach((kind, type) -> event.put(type.get(), kind.attributes())); }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) ITEMS.getEntries().forEach(item -> event.accept(item.get()));
    }
    private YujiContent() {}
}
