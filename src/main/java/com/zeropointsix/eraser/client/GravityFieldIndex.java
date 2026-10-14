package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class GravityFieldIndex {
    // ClientLevel can join a replacement before firing leave for the old same-ID entity.
    private static final Set<GravityFieldEntity> FIELDS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<GravityFieldEntity> VIEW = Collections.unmodifiableSet(FIELDS);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof GravityFieldEntity field)
            FIELDS.add(field);
    }

    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof GravityFieldEntity field)
            FIELDS.remove(field);
    }

    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide())
            FIELDS.removeIf(field -> field.level() == event.getLevel());
    }

    public static Set<GravityFieldEntity> fields() { return VIEW; }

    private GravityFieldIndex() { }
}
