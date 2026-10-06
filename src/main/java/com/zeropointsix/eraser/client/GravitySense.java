package com.zeropointsix.eraser.client;

import com.zeropointsix.eraser.gravity.GravityConfig;
import com.zeropointsix.eraser.gravity.GravityEquipment;
import com.zeropointsix.eraser.gravity.GravityFieldEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class GravitySense {
    private record Sample(Entity entity, Vec3 position, long sampledAt, long movedAt) { }
    private static final Map<Integer, Sample> SAMPLES = new HashMap<>();
    private static final Set<Integer> HIGHLIGHTED = new HashSet<>();
    private static List<Entity> targets = List.of();
    private static ClientLevel lastLevel;

    public static void tick(Minecraft mc) {
        if (mc.level != lastLevel || mc.player == null || !mc.player.isAlive()
                || !GravityEquipment.isEquipped(mc.player)) {
            SAMPLES.clear();
            HIGHLIGHTED.clear();
            targets = List.of();
            lastLevel = mc.level;
            return;
        }
        long tick = mc.level.getGameTime();
        if ((tick & 1) != 0) return;
        Set<Integer> present = new HashSet<>();
        List<Entity> moving = new ArrayList<>();
        double threshold = GravityConfig.THRESHOLD.get();
        for (Entity entity : mc.level.getEntities(mc.player,
                mc.player.getBoundingBox().inflate(GravityConfig.RANGE.get()),
                e -> !e.isRemoved() && !(e instanceof GravityFieldEntity))) {
            int id = entity.getId();
            present.add(id);
            Sample previous = SAMPLES.get(id);
            long movedAt = Long.MIN_VALUE / 2;
            if (previous != null && previous.entity() == entity) {
                movedAt = previous.movedAt();
                long elapsed = tick - previous.sampledAt();
                if (elapsed > 0 && entity.position().distanceToSqr(previous.position())
                        >= threshold * threshold * elapsed * elapsed) movedAt = tick;
            }
            SAMPLES.put(id, new Sample(entity, entity.position(), tick, movedAt));
            if (movedAt == tick || tick - movedAt < GravityConfig.FADE.get()) moving.add(entity);
        }
        SAMPLES.keySet().retainAll(present);
        moving.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        targets = List.copyOf(moving.subList(0, Math.min(moving.size(), GravityConfig.LIMIT.get())));
        HIGHLIGHTED.clear();
        targets.forEach(e -> HIGHLIGHTED.add(e.getId()));
    }

    public static boolean outlines(Entity entity) {
        if (!(entity instanceof LivingEntity) || !HIGHLIGHTED.contains(entity.getId())) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.level == lastLevel && entity.level() == mc.level
                && mc.player != null && mc.player.isAlive() && GravityEquipment.isEquipped(mc.player)
                && !entity.isRemoved();
    }

    public static List<Entity> targets() { return targets; }

    private GravitySense() { }
}
