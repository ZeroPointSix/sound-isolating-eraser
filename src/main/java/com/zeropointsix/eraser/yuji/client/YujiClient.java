package com.zeropointsix.eraser.yuji.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.event.GeoRenderEvent;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID, value = Dist.CLIENT)
public final class YujiClient {
    private static int lookTarget = -1, lookTicks;
    private static final Map<YujiMonster, CueClock> CLOCKS = new WeakHashMap<>();
    private static final Map<YujiMonster, FollowingSound> LOOPS = new WeakHashMap<>();
    private static final Map<YujiMonster, Map<String, Vec3>> BONES = new WeakHashMap<>();
    private static final Map<String, Integer> LENGTHS = new HashMap<>();
    private static final JsonObject CUES = read("/assets/qihai/yuji_cues.json");
    private static Object lastLevel;
    static {
        for (YujiKind kind : YujiKind.values()) {
            JsonObject animations = read("/assets/qihai/animations/" + kind.id() + ".animation.json").getAsJsonObject("animations");
            animations.entrySet().forEach(entry -> LENGTHS.put(entry.getKey(), Math.max(1,
                    Math.round(entry.getValue().getAsJsonObject().get("animation_length").getAsFloat() * 20))));
        }
    }
    private static JsonObject read(String path) {
        try (var stream = YujiClient.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing Yuji asset " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public static void look(int entityId, boolean turn) { lookTarget = turn ? entityId : -1; lookTicks = turn ? 10 : 0; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (lastLevel != mc.level) {
            LOOPS.values().forEach(FollowingSound::finish); LOOPS.clear(); CLOCKS.clear(); BONES.clear(); lookTarget = -1; lastLevel = mc.level;
        }
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        LOOPS.entrySet().removeIf(entry -> {
            if (!entry.getKey().isAlive() || entry.getKey().isRemoved() || entry.getValue().isStopped()) {
                entry.getValue().finish(); return true;
            }
            return false;
        });
        if (lookTicks-- > 0 && lookTarget >= 0 && mc.player.hasEffect(YujiContent.ENTHRALLED.get())) {
            Entity source = mc.level.getEntity(lookTarget);
            if (source != null && source.isAlive() && source.distanceToSqr(mc.player) <= 256) {
                Vec3 delta = source.getEyePosition().subtract(mc.player.getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
                float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
                mc.player.setYRot(Mth.approachDegrees(mc.player.getYRot(), yaw, 2));
                mc.player.setXRot(Mth.approachDegrees(mc.player.getXRot(), pitch, 1.5F));
            }
        }
        for (Entity entity : mc.level.entitiesForRendering()) if (entity instanceof YujiMonster monster) {
            cue(monster);
            if ((monster.kind() == YujiKind.QIYU || monster.kind() == YujiKind.SHIFOTOU)
                    && monster.isAlive() && monster.distanceToSqr(mc.player) < (monster.kind() == YujiKind.QIYU ? 1024 : 4096)) {
                FollowingSound old = LOOPS.get(monster);
                if (old == null || old.isStopped()) {
                    SoundEvent sound = YujiContent.sound(monster.kind() == YujiKind.QIYU ? "entity.qihai.qiyu.flute" : "music.qihai.shifotou_battle");
                    if (sound != null) { FollowingSound next = new FollowingSound(monster, sound); LOOPS.put(monster, next); mc.getSoundManager().play(next); }
                }
            }
        }
    }
    @SubscribeEvent public static void rendered(GeoRenderEvent.Entity.Post event) {
        if (!(event.getEntity() instanceof YujiMonster monster)) return;
        JsonObject actions = CUES.getAsJsonObject(monster.kind().id());
        if (actions == null) return;
        Map<String, Vec3> positions = new HashMap<>();
        for (JsonElement action : actions.asMap().values()) for (JsonElement value : action.getAsJsonArray()) {
            String name = value.getAsJsonObject().get("bone").getAsString();
            event.getModel().getBone(name).ifPresent(bone -> {
                var point = bone.getWorldPosition();
                positions.put(name, new Vec3(point.x, point.y, point.z));
            });
        }
        BONES.put(monster, positions);
    }
    private static void cue(YujiMonster monster) {
        boolean moving = monster.getDeltaMovement().horizontalDistanceSqr() > .0001;
        String action = monster.isDeadOrDying() ? "death" : monster.action().isEmpty() ? monster.locomotion(moving) : monster.action();
        CueClock clock = CLOCKS.computeIfAbsent(monster, ignored -> new CueClock());
        if (!clock.action.equals(action) || clock.serial != monster.actionSerial()) {
            clock.action = action; clock.serial = monster.actionSerial(); clock.age = 0;
        }
        int duration = LENGTHS.getOrDefault("animation." + monster.kind().id() + "." + action, 40);
        int age = monster.action().isEmpty() && !monster.isDeadOrDying() ? clock.age % duration : clock.age;
        clock.age++;
        JsonObject actions = CUES.getAsJsonObject(monster.kind().id());
        if (actions == null || !actions.has(action)) return;
        for (JsonElement value : actions.getAsJsonArray(action)) {
            JsonObject cue = value.getAsJsonObject();
            if (Math.round(cue.get("t").getAsFloat() * 20) != age) continue;
            Vec3 origin = monster.position().add(0, monster.getBbHeight() * .65, 0);
            // Gecko shares a model among same-kind entities; retain each entity's rendered transforms.
            Vec3 point = BONES.getOrDefault(monster, Map.of()).get(cue.get("bone").getAsString());
            if (point != null && point.distanceTo(monster.position()) < monster.getBbHeight() * 2 + 4) origin = point;
            if (cue.has("off")) {
                JsonArray off = cue.getAsJsonArray("off");
                origin = origin.add(new Vec3(off.get(0).getAsDouble(), off.get(1).getAsDouble(), off.get(2).getAsDouble()).scale(monster.modelScale() / 16.).yRot((float) Math.toRadians(-monster.getYRot())));
            }
            var particle = YujiContent.PARTICLE.get(cue.get("vfx").getAsString());
            int count = cue.has("count") ? cue.get("count").getAsInt() : 4;
            double spread = cue.has("spread") ? cue.get("spread").getAsDouble() / 48 : .15;
            double speed = cue.has("speed") ? cue.get("speed").getAsDouble() / 320 : .06;
            Vec3 direction = Vec3.ZERO;
            if (cue.has("dir")) {
                JsonArray vector = cue.getAsJsonArray("dir");
                direction = new Vec3(vector.get(0).getAsDouble(), vector.get(1).getAsDouble(), vector.get(2).getAsDouble())
                        .normalize().scale(speed).yRot((float) Math.toRadians(-monster.getYRot()));
            }
            if (particle != null) for (int i = 0; i < count; i++) {
                monster.level().addParticle(particle.get(), origin.x + (Math.random() - .5) * spread,
                        origin.y, origin.z + (Math.random() - .5) * spread, direction.x + (Math.random() - .5) * speed,
                        direction.y + .03, direction.z + (Math.random() - .5) * speed);
            }
            if (cue.has("sound") && monster.kind() != YujiKind.WANGYU && monster.kind() != YujiKind.QIYU && !action.equals("death")) {
                SoundEvent sound = YujiContent.sound("entity.qihai." + monster.kind().id() + "." + cue.get("sound").getAsString());
                if (sound != null) monster.level().playLocalSound(monster.getX(), monster.getY(), monster.getZ(), sound, SoundSource.HOSTILE, 1, 1, false);
            }
        }
    }
    @SubscribeEvent public static void maskSound(PlaySoundEvent event) {
        var sound = event.getSound(); Minecraft mc = Minecraft.getInstance();
        if (sound == null || sound.getSource() != SoundSource.HOSTILE || sound.isLooping() || mc.player == null || mc.level == null
                || sound.getLocation().getPath().contains("qiyu.flute")) return;
        boolean flute = !mc.level.getEntitiesOfClass(YujiMonster.class, mc.player.getBoundingBox().inflate(32),
                y -> y.kind() == YujiKind.QIYU && y.isAlive() && y.distanceToSqr(mc.player) <= 1024).isEmpty();
        if (flute) event.setSound(sound instanceof TickableSoundInstance ticking ? new MaskedTickingSound(ticking) : new MaskedSound(sound));
    }
    private static class MaskedSound implements SoundInstance {
        protected final SoundInstance original;
        MaskedSound(SoundInstance original) { this.original = original; }
        @Override public ResourceLocation getLocation() { return original.getLocation(); }
        @Override public WeighedSoundEvents resolve(SoundManager manager) { return original.resolve(manager); }
        @Override public Sound getSound() { return original.getSound(); }
        @Override public SoundSource getSource() { return original.getSource(); }
        @Override public boolean isLooping() { return original.isLooping(); }
        @Override public boolean isRelative() { return original.isRelative(); }
        @Override public int getDelay() { return original.getDelay(); }
        // Forge invokes PlaySoundEvent before resolve(), so sampling volume inside the event can crash.
        @Override public float getVolume() { return original.getVolume() * .3F; }
        @Override public float getPitch() { return original.getPitch(); }
        @Override public double getX() { return original.getX(); }
        @Override public double getY() { return original.getY(); }
        @Override public double getZ() { return original.getZ(); }
        @Override public Attenuation getAttenuation() { return original.getAttenuation(); }
        @Override public boolean canPlaySound() { return original.canPlaySound(); }
        @Override public boolean canStartSilent() { return original.canStartSilent(); }
    }
    private static final class MaskedTickingSound extends MaskedSound implements TickableSoundInstance {
        private final TickableSoundInstance ticking;
        MaskedTickingSound(TickableSoundInstance original) { super(original); ticking = original; }
        @Override public void tick() { ticking.tick(); }
        @Override public boolean isStopped() { return ticking.isStopped(); }
    }
    private static final class CueClock { private String action = ""; private int serial = -1, age; }
    private static final class FollowingSound extends AbstractTickableSoundInstance {
        private final YujiMonster source;
        FollowingSound(YujiMonster source, SoundEvent sound) {
            super(sound, source.kind() == YujiKind.QIYU ? SoundSource.HOSTILE : SoundSource.MUSIC, RandomSource.create());
            this.source = source; looping = true; delay = 0; volume = 1; update();
        }
        private void update() { x = source.getX(); y = source.getY(); z = source.getZ(); }
        void finish() { stop(); }
        @Override public void tick() {
            var player = Minecraft.getInstance().player;
            if (!source.isAlive() || source.isRemoved() || player == null || source.distanceToSqr(player) > (source.kind() == YujiKind.QIYU ? 1024 : 4096)) stop();
            else update();
        }
    }
    private YujiClient() {}
}
