package com.zeropointsix.eraser.yuji.city;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class RainCitySpawns {
    private static final UUID HEALTH = UUID.fromString("557c94c8-e8d4-443c-a860-8b075f3ea860");
    private static final UUID SPEED = UUID.fromString("557c94c8-e8d4-443c-a860-8b075f3ea861");
    public static void tick(ServerLevel city, RainCityData data, List<ServerPlayer> players, long elapsed) {
        if (city.getDifficulty() == Difficulty.PEACEFUL || !city.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || data.ringing()) return;
        int day = RainCityRules.day(elapsed), budget = RainCityConfig.SPAWN_BUDGET.get();
        List<ServerPlayer> targets = players.stream().filter(p -> !p.isCreative() && !p.isSpectator()).toList();
        boolean second = city.getGameTime() % 20 == 0;
        if (second) for (var entity : city.getAllEntities()) if (entity instanceof YujiMonster mob && mob.getPersistentData().contains("YujiCityDay")) {
            if (mob.getPersistentData().getInt("YujiCityDay") < day) grow(mob, day);
            if (!mob.getPersistentData().getBoolean("YujiHorde") && !mob.getPersistentData().getBoolean("YujiGuardian")
                    && targets.stream().noneMatch(p -> p.distanceToSqr(mob) <= 96 * 96) && !mob.isPersistenceRequired()) mob.discard();
        }
        for (ServerPlayer p : targets) {
            CompoundTag state = data.players.get(p.getUUID());
            if (state == null) continue;
            if (RainCityRules.night(elapsed) && (!state.contains("LastWaveAt") || city.getGameTime() - state.getLong("LastWaveAt") >= 24000)) {
                state.putInt("LastWave", day);
                state.putLong("LastWaveAt", city.getGameTime());
                state.putInt("Horde", (int) Math.min(Integer.MAX_VALUE, (long) state.getInt("Horde") + RainCityRules.horde(day, RainCityConfig.HORDE_MULTIPLIER.get())));
                if (day % 3 == 0 && RainCityConfig.HORDE_MULTIPLIER.get() > 0) {
                    state.putInt("WaveChennu", Math.min(10000, state.getInt("WaveChennu") + 1));
                    state.putInt("WaveGreed", Math.min(100000, state.getInt("WaveGreed") + 6 + city.random.nextInt(5)));
                }
                data.setDirty();
            }
            if (second) {
                int cap = RainCityRules.cap(day, RainCityRules.night(elapsed), RainCityConfig.CAP.get());
                state.putInt("AmbientAttempts", cap / 20);
            }
        }
        // Rotate both players and queues so an impossible underground spawn cannot stall the server's other waves.
        if (targets.isEmpty()) return;
        for (int step = 0; step < budget; step++) {
            ServerPlayer p = targets.get((int) Math.floorMod(city.getGameTime() + step, targets.size()));
            CompoundTag state = data.players.get(p.getUUID());
            if (state == null) continue;
            String[] queues = {"Horde", "WaveChennu", "WaveGreed", "Ambient"};
            int cursor = Math.floorMod(state.getInt("SpawnCursor"), queues.length);
            String queue = null;
            for (int i = 0; i < queues.length; i++) {
                int index = (cursor + i) % queues.length;
                String candidate = queues[index];
                boolean available = candidate.equals("Ambient") ? state.getInt("AmbientAttempts") > 0 || state.getInt("AmbientGroup") > 0 : state.getInt(candidate) > 0;
                if (available) { queue = candidate; state.putInt("SpawnCursor", (index + 1) % queues.length); break; }
            }
            if (queue == null) continue;
            if (!queue.equals("Ambient")) {
                YujiKind kind = queue.equals("WaveChennu") ? YujiKind.CHENNU : queue.equals("WaveGreed") ? YujiKind.TANYU : waveKind(city, day);
                if (attempt(city, p, kind, day, true, state, true)) state.putInt(queue, state.getInt(queue) - 1);
                data.setDirty(); continue;
            }
            int cap = RainCityRules.cap(day, RainCityRules.night(elapsed), RainCityConfig.CAP.get());
            int count = city.getEntitiesOfClass(YujiMonster.class, p.getBoundingBox().inflate(RainCityConfig.DISTANCE.get()),
                    mob -> mob.isAlive() && !mob.getPersistentData().getBoolean("YujiHorde")).size();
            if (count >= cap) { state.putInt("AmbientAttempts", 0); state.putInt("AmbientGroup", 0); continue; }
            if (state.getInt("AmbientGroup") <= 0) {
                state.putInt("AmbientAttempts", state.getInt("AmbientAttempts") - 1);
                YujiKind kind = choose(city, day, RainCityRules.night(elapsed), RainCityTemplates.district(p.blockPosition()));
                if (day > 0 && city.random.nextInt(8) == 0 && regionalChennu(city, p) < RainCityRules.chennu(day)) kind = YujiKind.CHENNU;
                int group = Math.min(cap - count, (int) Math.max(1, Math.round((kind == YujiKind.TANYU ? 3 + city.random.nextInt(3) : kind == YujiKind.YUCHI ? 2 : 1) * (1 + .15 * day))));
                state.putInt("AmbientKind", kind.ordinal()); state.putInt("AmbientGroup", group);
            }
            YujiKind kind = YujiKind.values()[Math.floorMod(state.getInt("AmbientKind"), YujiKind.values().length)];
            attempt(city, p, kind, day, false, state, false);
            state.putInt("AmbientGroup", state.getInt("AmbientGroup") - 1); data.setDirty();
        }
    }
    private static int regionalChennu(ServerLevel city, ServerPlayer p) {
        int x = Math.floorDiv(p.getBlockX(), 256) * 256, z = Math.floorDiv(p.getBlockZ(), 256) * 256;
        return city.getEntitiesOfClass(YujiMonster.class, new AABB(x, -64, z, x + 256, 320, z + 256), m -> m.kind() == YujiKind.CHENNU && m.isAlive()).size();
    }
    private static YujiKind waveKind(ServerLevel city, int day) {
        int roll = city.random.nextInt(100);
        if (roll < 55) return YujiKind.YUCHI;
        if (roll < 80) return YujiKind.SHASHENG;
        if (roll < 85) return YujiKind.TOUDAO;
        double r = city.random.nextDouble() * 28;
        for (YujiKind kind : new YujiKind[]{YujiKind.EKOU, YujiKind.WANGYU, YujiKind.LIANGSHE, YujiKind.XIEYIN, YujiKind.QIYU}) {
            r -= baseWeight(kind); if (r < 0) return kind;
        }
        return YujiKind.QIYU;
    }
    public static double baseWeight(YujiKind kind) {
        return switch (kind) {
            case YUCHI -> 28; case SHASHENG -> 16; case TOUDAO -> 10; case TANYU -> 12;
            case EKOU -> 8; case WANGYU -> 7; case LIANGSHE -> 6; case XIEYIN -> 5; case QIYU -> 2; default -> 0;
        };
    }
    private static YujiKind choose(ServerLevel city, int day, boolean night, String district) {
        double[] weights = new double[YujiKind.values().length]; double total = 0;
        for (YujiKind kind : YujiKind.values()) {
            double w = baseWeight(kind);
            if (day == 0 && kind != YujiKind.YUCHI && kind != YujiKind.SHASHENG && kind != YujiKind.TOUDAO) w = 0;
            if (day > 0) {
                if (kind == YujiKind.SHASHENG) w += day - 1;
                else if (kind == YujiKind.TANYU) w += 1.5 * (day - 1);
                else if (kind.ordinal() >= YujiKind.EKOU.ordinal() && kind.ordinal() <= YujiKind.QIYU.ordinal()) w *= RainCityRules.specialWeight(day);
            }
            if (night && kind == YujiKind.TOUDAO) w *= 2;
            w *= switch (district) {
                case "hospital" -> kind == YujiKind.YUCHI ? 2 : kind == YujiKind.TOUDAO ? 1.5 : 1;
                case "old_town" -> kind == YujiKind.SHASHENG || kind == YujiKind.EKOU || kind == YujiKind.LIANGSHE ? 1.5 : 1;
                case "mall" -> kind == YujiKind.WANGYU ? 3 : 1;
                case "subway" -> kind == YujiKind.TANYU ? 4 : .5;
                case "temple" -> kind == YujiKind.XIEYIN || kind == YujiKind.QIYU ? 1.5 : 1;
                default -> 1;
            };
            weights[kind.ordinal()] = w; total += w;
        }
        double r = city.random.nextDouble() * total;
        for (YujiKind kind : YujiKind.values()) { r -= weights[kind.ordinal()]; if (r < 0) return kind; }
        return YujiKind.YUCHI;
    }
    private static boolean attempt(ServerLevel city, ServerPlayer target, YujiKind kind, int day, boolean horde, CompoundTag state, boolean wave) {
        int maximum = RainCityConfig.DISTANCE.get();
        double minimum = wave ? Math.min(64, maximum) : 24;
        double angle = city.random.nextDouble() * Math.PI * 2, radius = minimum + city.random.nextDouble() * Math.max(0, maximum - minimum);
        int x = (int) Math.floor(target.getX() + Math.cos(angle) * radius), z = (int) Math.floor(target.getZ() + Math.sin(angle) * radius);
        BlockPos probe = new BlockPos(x, RainCityGenerator.SURFACE, z);
        if (!city.hasChunkAt(probe)) return false;
        int y = city.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (kind == YujiKind.TANYU) y = 90;
        else if (kind == YujiKind.LIANGSHE || kind == YujiKind.XIEYIN) y = RainCityGenerator.SURFACE + 1 + 6 * city.random.nextInt(3);
        BlockPos pos = new BlockPos(x, y, z);
        if (RainCityTemplates.shaft(pos)) return false;
        if (city.getGameTime() - state.getLong("Entered") < 3600 && pos.distSqr(BlockPos.of(state.getLong("Arrival"))) <= 256) return false;
        final int spawnY = y;
        if (city.players().stream().anyMatch(p -> p.distanceToSqr(x + .5, spawnY, z + .5) < 24 * 24)) return false;
        if (!city.getBlockState(pos.below()).isFaceSturdy(city, pos.below(), Direction.UP) || !city.getFluidState(pos).isEmpty()) return false;
        if (kind == YujiKind.QIYU && y < RainCityGenerator.SURFACE + 40) return false;
        if ((kind == YujiKind.LIANGSHE || kind == YujiKind.XIEYIN) && city.canSeeSky(pos)) return false;
        if (kind == YujiKind.XIEYIN) {
            int walls = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) if (!city.getBlockState(pos.relative(d, 3)).isAir()) walls++;
            if (walls < 2) return false;
        }
        YujiMonster mob = YujiContent.MOBS.get(kind).get().create(city);
        if (mob == null) return false;
        mob.moveTo(x + .5, y, z + .5, city.random.nextFloat() * 360, 0);
        if (!city.noCollision(mob)) return false;
        mob.finalizeSpawn(city, city.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        if (kind == YujiKind.TANYU) mob.setLayers(RainCityRules.layers(day), false);
        if (!city.noCollision(mob)) return false;
        grow(mob, day); mob.setHealth(mob.getMaxHealth());
        mob.getPersistentData().putBoolean("YujiHorde", horde);
        if (horde) { mob.setTarget(target); mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(128); }
        return city.addFreshEntity(mob);
    }
    public static void grow(YujiMonster mob, int day) {
        float ratio = mob.getHealth() / mob.getMaxHealth();
        modify(mob, Attributes.MAX_HEALTH, HEALTH, RainCityRules.strength(day) - 1);
        modify(mob, Attributes.MOVEMENT_SPEED, SPEED, RainCityRules.speed(day) - 1);
        mob.setHealth(mob.getMaxHealth() * ratio); mob.getPersistentData().putInt("YujiCityDay", day);
    }
    private static void modify(YujiMonster mob, Attribute attribute, UUID id, double amount) {
        var instance = mob.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0) instance.addPermanentModifier(new AttributeModifier(id, "Yuji city day scaling", amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }
    private RainCitySpawns() { }
}
