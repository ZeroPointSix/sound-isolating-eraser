package com.zeropointsix.eraser.yuji;

import com.zeropointsix.eraser.ModMain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.PrepareRamNearestTarget;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.behavior.RamTarget;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.goat.GoatAi;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class YujiAfflictions {
    private static final String ROOT = "QihaiAfflictions", INFLUENCE = "QihaiBetrayal";
    private static final UUID SPEED = UUID.fromString("a17de001-5350-4000-8000-000000000001");
    private static final UUID ATTACK = UUID.fromString("a17de001-5350-4000-8000-000000000002");
    private static final UUID FOLLOW = UUID.fromString("a17de001-5350-4000-8000-000000000003");
    private static final String[] TYPES = {"cursed", "betrayed", "enthralled", "weakness"};
    private static final Map<Goat, GoatRamTargets> goatRamTargets = new WeakHashMap<>();
    private static CompoundTag root(Player player) {
        if (!player.getPersistentData().contains(ROOT)) player.getPersistentData().put(ROOT, new CompoundTag());
        return player.getPersistentData().getCompound(ROOT);
    }
    private static CompoundTag entries(Player p, String type) {
        CompoundTag r = root(p);
        if (!r.contains(type)) r.put(type, new CompoundTag());
        return r.getCompound(type);
    }
    private static MobEffect effect(String type) {
        return switch (type) {
            case "cursed" -> YujiContent.CURSED.get(); case "betrayed" -> YujiContent.BETRAYED.get();
            case "enthralled" -> YujiContent.ENTHRALLED.get(); default -> MobEffects.WEAKNESS;
        };
    }
    public static void apply(Player p, YujiMonster source, String type) {
        if (p.level().isClientSide || !YujiMonster.vulnerable(p)) return;
        CompoundTag sources = entries(p, type);
        String id = source.getStringUUID();
        if (!sources.contains(id)) {
            CompoundTag state = new CompoundTag(); state.putUUID("Source", source.getUUID()); sources.put(id, state);
        }
        CompoundTag state = sources.getCompound(id);
        if (type.equals("betrayed")) state.putInt("Remaining", 400);
        if (!type.equals("weakness")) p.addEffect(new MobEffectInstance(effect(type), type.equals("betrayed") ? 400 : 100, 0, false, true));
    }
    public static void weakness(Player player, YujiMonster source, int duration, int amplifier) {
        if (player.level().isClientSide || !YujiMonster.vulnerable(player)) return;
        apply(player, source, "weakness");
        CompoundTag state = entries(player, "weakness").getCompound(source.getStringUUID());
        int previousAmplifier = state.getInt("Amplifier");
        if (amplifier >= previousAmplifier || state.getInt("Remaining") <= 0) {
            state.putInt("Remaining", amplifier == previousAmplifier ? Math.max(duration, state.getInt("Remaining")) : duration);
            state.putInt("Amplifier", amplifier);
        }
        reconcileWeakness(player);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) tickPlayer(event.player);
    }
    public static void tickPlayer(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.getPersistentData().contains(ROOT)) return;
        CompoundTag r = root(player);
        if (!YujiMonster.vulnerable(player)) { clear(player); return; }
        if (r.getInt("SneakCooldown") > 0) r.putInt("SneakCooldown", r.getInt("SneakCooldown") - 1);
        if (r.contains("PreviousWeakness")) {
            CompoundTag previous = r.getCompound("PreviousWeakness");
            previous.putInt("Duration", Math.max(0, previous.getInt("Duration") - 1));
        }
        if (r.getInt("OwnedWeakDuration") > 0) r.putInt("OwnedWeakDuration", r.getInt("OwnedWeakDuration") - 1);
        YujiMonster curseSource = null, lureSource = null;
        int curseAge = -1, lureAge = -1;
        for (String type : TYPES) {
            CompoundTag states = entries(player, type);
            boolean hadSource = !states.isEmpty();
            for (String key : new ArrayList<>(states.getAllKeys())) {
                CompoundTag state = states.getCompound(key);
                Entity entity = state.hasUUID("Source") ? level.getEntity(state.getUUID("Source")) : null;
                if (!(entity instanceof YujiMonster source) || !source.isAlive()) { states.remove(key); continue; }
                double distance = source.distanceToSqr(player);
                int age = state.getInt("Age");
                switch (type) {
                    case "cursed" -> {
                        int outside = distance > 144 ? state.getInt("Outside") + 1 : 0;
                        state.putInt("Outside", outside);
                        if (outside >= 60) { states.remove(key); continue; }
                        if (outside == 0) state.putInt("Age", ++age);
                        if (age > curseAge) { curseAge = age; curseSource = source; }
                    }
                    case "enthralled" -> {
                        if (distance > 256) { states.remove(key); continue; }
                        age = Math.min(120, age + 1); state.putInt("Age", age);
                        if (age > lureAge) { lureAge = age; lureSource = source; }
                    }
                    default -> {
                        int left = state.getInt("Remaining") - 1; state.putInt("Remaining", left);
                        if (left <= 0) states.remove(key);
                    }
                }
            }
            if (hadSource && states.isEmpty()) removeEffect(player, type);
        }
        if (!entries(player, "weakness").isEmpty()) reconcileWeakness(player);
        if (curseSource != null) {
            refresh(player, YujiContent.CURSED.get(), 0);
            int ticks = r.getInt("CurseClock") + 1;
            if (ticks % 30 == 0) player.hurt(player.damageSources().indirectMagic(curseSource, curseSource),
                    (float) Math.min(YujiConfig.MAX_CURSE_DAMAGE.get(), 1 + .5 * (curseAge / 100)));
            if (ticks % 40 == 0) recruit(player, false);
            r.putInt("CurseClock", ticks % 1200);
        } else r.remove("CurseClock");
        if (!entries(player, "betrayed").isEmpty()) {
            refresh(player, YujiContent.BETRAYED.get(), 0);
            if (player.tickCount % 20 == 0) recruit(player, true);
        }
        if (lureSource != null) {
            int strength = Math.min(2, lureAge / 60);
            int sneak = player.isShiftKeyDown() ? r.getInt("Sneaking") + 1 : 0;
            r.putInt("Sneaking", sneak);
            if (sneak >= 40 && r.getInt("SneakCooldown") == 0) {
                r.putInt("Sneaking", 0); r.putInt("SneakCooldown", 120);
                for (String key : new ArrayList<>(entries(player, "enthralled").getAllKeys())) {
                    CompoundTag state = entries(player, "enthralled").getCompound(key);
                    if (state.getInt("Age") < 60) entries(player, "enthralled").remove(key);
                    else state.putInt("Age", Math.max(0, state.getInt("Age") - 60));
                }
                if (entries(player, "enthralled").isEmpty()) { removeEffect(player, "enthralled"); return; }
                strength = Math.max(0, strength - 1);
            }
            refresh(player, YujiContent.ENTHRALLED.get(), strength);
            Vec3 direction = lureSource.position().subtract(player.position()).multiply(1, 0, 1).normalize();
            Vec3 velocity = player.getDeltaMovement();
            double backward = velocity.dot(direction);
            if (backward < 0) velocity = velocity.subtract(direction.scale(backward * .6));
            double pull = new double[]{.8, 1.4, 2.0}[strength] / 20 * YujiConfig.PULL_STRENGTH.get();
            // Pull has a speed cap, so repeated velocity packets cannot accumulate unbounded acceleration.
            if (velocity.dot(direction) < pull) velocity = velocity.add(direction.scale(pull - Math.max(0, velocity.dot(direction))));
            player.setDeltaMovement(velocity); player.hurtMarked = true;
            if (strength >= 1 && player.tickCount % 20 == 0 && cramped(player)) {
                r.putBoolean("Crushing", true);
                try { player.hurt(player.damageSources().cramming(), 2); } finally { r.remove("Crushing"); }
            }
            if (player instanceof ServerPlayer server && player.tickCount % 4 == 0) {
                YujiNetwork.look(server, lureSource.getId(), strength >= 1 && YujiConfig.CAMERA_TURN.get());
            }
        } else r.remove("Sneaking");
    }
    public static boolean cramped(Player player) {
        BlockPos foot = player.blockPosition();
        return blocked(player, foot.east()) && blocked(player, foot.west())
                || blocked(player, foot.north()) && blocked(player, foot.south());
    }
    private static boolean blocked(Player player, BlockPos pos) {
        var level = player.level();
        var state = level.getBlockState(pos);
        var above = level.getBlockState(pos.above());
        return state.isCollisionShapeFullBlock(level, pos) && above.isCollisionShapeFullBlock(level, pos.above());
    }
    private static void refresh(Player player, MobEffect effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 30 || current.getAmplifier() != amplifier) {
            if (current != null && current.getAmplifier() > amplifier) player.removeEffect(effect);
            player.addEffect(new MobEffectInstance(effect, 60, amplifier));
        }
    }
    private static void removeEffect(Player p, String type) {
        if (!type.equals("weakness")) p.removeEffect(effect(type));
        else reconcileWeakness(p);
        if (type.equals("enthralled") && p instanceof ServerPlayer server) YujiNetwork.look(server, -1, false);
        if (type.equals("betrayed")) restoreFollowers(p);
    }
    private static void clear(Player p) {
        for (String type : TYPES) if (!entries(p, type).isEmpty()) {
            root(p).put(type, new CompoundTag()); removeEffect(p, type);
        }
        p.getPersistentData().remove(ROOT);
    }
    private static void reconcileWeakness(Player player) {
        CompoundTag r = root(player);
        MobEffectInstance current = player.getEffect(MobEffects.WEAKNESS);
        boolean ours = current != null && r.contains("OwnedWeakDuration")
                && current.getAmplifier() == r.getInt("OwnedWeakAmplifier")
                && Math.abs(current.getDuration() - r.getInt("OwnedWeakDuration")) <= 2;
        if (current != null && !ours) r.put("PreviousWeakness", current.save(new CompoundTag()));
        MobEffectInstance chosen = MobEffectInstance.load(r.getCompound("PreviousWeakness"));
        if (chosen != null && chosen.getDuration() <= 0) chosen = null;
        CompoundTag states = entries(player, "weakness");
        for (String key : states.getAllKeys()) {
            CompoundTag state = states.getCompound(key);
            int duration = state.getInt("Remaining"), amplifier = state.getInt("Amplifier");
            if (duration > 0 && (chosen == null || amplifier > chosen.getAmplifier()
                    || amplifier == chosen.getAmplifier() && duration > chosen.getDuration())) {
                chosen = new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier);
            }
        }
        // Rebuild from live sources instead of assuming addEffect accepted a shorter refresh.
        r.putBoolean("UpdatingWeakness", true);
        try {
            if (current != null) player.removeEffect(MobEffects.WEAKNESS);
            if (chosen != null) player.addEffect(chosen);
        } finally { r.remove("UpdatingWeakness"); }
        if (states.isEmpty()) {
            r.remove("PreviousWeakness"); r.remove("OwnedWeakDuration"); r.remove("OwnedWeakAmplifier");
        } else if (chosen != null) {
            r.putInt("OwnedWeakDuration", chosen.getDuration()); r.putInt("OwnedWeakAmplifier", chosen.getAmplifier());
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void effectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect() != MobEffects.WEAKNESS || !(event.getEntity() instanceof Player player)
                || player.level().isClientSide || !player.getPersistentData().contains(ROOT)) return;
        CompoundTag data = root(player);
        if (data.getBoolean("UpdatingWeakness")) return;
        data.put("weakness", new CompoundTag());
        data.remove("PreviousWeakness"); data.remove("OwnedWeakDuration"); data.remove("OwnedWeakAmplifier");
    }
    public static void releaseSource(YujiMonster source) {
        if (!(source.level() instanceof ServerLevel level)) return;
        for (ServerPlayer player : level.players()) {
            if (!player.getPersistentData().contains(ROOT)) continue;
            for (String type : TYPES) {
                CompoundTag states = entries(player, type);
                boolean owned = states.contains(source.getStringUUID()); states.remove(source.getStringUUID());
                if (owned && states.isEmpty()) removeEffect(player, type);
                else if (owned && type.equals("weakness")) reconcileWeakness(player);
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void damage(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof YujiMonster monster) {
            float amount = event.getAmount();
            if (monster.kind() == YujiKind.TANYU) amount *= 1 - .04F * monster.layers();
            if (monster.kind() == YujiKind.CHENNU) {
                if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) amount *= .5F;
                if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) amount *= 2;
            }
            if (monster.kind() == YujiKind.SHIFOTOU && !event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)
                    && !event.getSource().is(DamageTypes.GENERIC_KILL)) amount *= event.getSource().is(DamageTypeTags.IS_EXPLOSION) ? .3F : .1F;
            event.setAmount(amount);
        }
        if (event.getAmount() > 0 && event.getEntity() instanceof Player player && !root(player).getBoolean("Crushing")
                && !entries(player, "enthralled").isEmpty()) {
            root(player).put("enthralled", new CompoundTag()); removeEffect(player, "enthralled");
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player player) clear(player);
        if (dead instanceof YujiMonster monster && monster.kind() == YujiKind.TANYU) {
            level.getEntitiesOfClass(YujiMonster.class, dead.getBoundingBox().inflate(8),
                    y -> y != dead && y.isAlive() && y.kind() == YujiKind.TANYU && y.distanceToSqr(dead) <= 64)
                    .stream().min(Comparator.comparingDouble(y -> y.distanceToSqr(dead))).ifPresent(y -> y.absorb(3));
        } else if (event.getSource().getEntity() instanceof YujiMonster killer && killer.isAlive() && killer.kind() == YujiKind.TANYU) killer.absorb(1);
    }
    private static boolean eligible(Mob mob, Player p, boolean betrayal) {
        if (mob instanceof YujiMonster y && (y.kind() == YujiKind.YUCHI || y.kind() == YujiKind.CHENNU || y.kind() == YujiKind.XIEYIN)) return false;
        if (mob instanceof Enemy) return true;
        if (!betrayal) return false;
        if (mob instanceof TamableAnimal pet && pet.isTame()) return p.getUUID().equals(pet.getOwnerUUID()) && YujiConfig.AFFECT_PETS.get();
        if (mob instanceof AbstractVillager) return YujiConfig.AFFECT_VILLAGERS.get();
        return mob instanceof NeutralMob || mob instanceof IronGolem || mob instanceof SnowGolem
                || mob instanceof net.minecraft.world.entity.animal.Panda
                || mob instanceof net.minecraft.world.entity.animal.goat.Goat
                || mob instanceof net.minecraft.world.entity.animal.horse.Llama;
    }
    public static void recruit(Player player, boolean betrayal) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32),
                m -> m.isAlive() && m.distanceToSqr(player) <= 1024 && eligible(m, player, betrayal))) {
            if (!betrayal) { mob.setTarget(player); mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60)); continue; }
            CompoundTag data = mob.getPersistentData().getCompound(INFLUENCE);
            if (data.isEmpty()) {
                if (mob.getTarget() != null) data.putUUID("PreviousTarget", mob.getTarget().getUUID());
                if (mob instanceof TamableAnimal pet) data.putBoolean("WasSitting", pet.isOrderedToSit());
                if (mob instanceof NeutralMob neutral) {
                    if (neutral.getPersistentAngerTarget() != null) data.putUUID("PreviousAnger", neutral.getPersistentAngerTarget());
                    data.putInt("PreviousAngerTime", neutral.getRemainingPersistentAngerTime());
                }
            }
            boolean changedVictim = data.hasUUID("Player") && !data.getUUID("Player").equals(player.getUUID());
            data.putUUID("Player", player.getUUID()); data.putLong("Until", player.level().getGameTime() + 45);
            mob.getPersistentData().put(INFLUENCE, data);
            if (mob instanceof Goat goat) {
                if (!data.getBoolean("GoatRamOwned")) {
                    data.putBoolean("GoatRamOwned", true);
                    if (!data.contains("GoatRamPreviousCooldownUntil")) {
                        data.putLong("GoatRamPreviousCooldownUntil", player.level().getGameTime()
                                + goat.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS).orElse(0));
                    }
                    stopGoatRam(goat, true);
                    installGoatRamTargeting(goat);
                    goat.getBrain().eraseMemory(MemoryModuleType.RAM_COOLDOWN_TICKS);
                } else if (changedVictim) {
                    stopGoatRam(goat, false);
                    installGoatRamTargeting(goat);
                } else if (!data.getBoolean("GoatRamCompleted")
                        && !goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET)
                        && goat.getBrain().getRunningBehaviors().stream().noneMatch(behavior ->
                                behavior instanceof PrepareRamNearestTarget<?> || behavior instanceof RamTarget)) {
                    // Vanilla prepare stop() writes a 600-tick lockout when an attempt cannot finish.
                    // Keep retrying while betrayal owns this goat so a later clear path can still charge.
                    goat.getBrain().eraseMemory(MemoryModuleType.RAM_COOLDOWN_TICKS);
                }
            }
            if (mob instanceof Enemy) {
                modifier(mob, Attributes.MOVEMENT_SPEED, SPEED, YujiConfig.HOSTILE_BUFF.get());
                modifier(mob, Attributes.ATTACK_DAMAGE, ATTACK, YujiConfig.HOSTILE_BUFF.get());
                modifier(mob, Attributes.FOLLOW_RANGE, FOLLOW, 1);
            }
            if (!(mob instanceof AbstractVillager) && !(mob instanceof Cat) && !(mob instanceof Parrot)
                    && !(mob instanceof Goat)) mob.setTarget(player);
            if (mob instanceof TamableAnimal pet) pet.setOrderedToSit(pet instanceof Cat || pet instanceof Parrot);
            if (mob instanceof NeutralMob neutral) { neutral.setPersistentAngerTarget(player.getUUID()); neutral.setRemainingPersistentAngerTime(60); }
            ((ServerLevel) player.level()).sendParticles(YujiContent.PARTICLE.get("betray_mark").get(), mob.getX(), mob.getY() + mob.getBbHeight() + .2, mob.getZ(), 2, .1, .1, .1, 0);
        }
    }
    private static void modifier(Mob mob, Attribute attribute, UUID id, double value) {
        var instance = mob.getAttribute(attribute);
        if (instance != null && instance.getModifier(id) == null) instance.addTransientModifier(new AttributeModifier(id, "qihai.betrayed", value, AttributeModifier.Operation.MULTIPLY_BASE));
    }
    private static Player influencedPlayer(Mob mob) {
        CompoundTag data = mob.getPersistentData().getCompound(INFLUENCE);
        Player p = data.hasUUID("Player") ? mob.level().getPlayerByUUID(data.getUUID("Player")) : null;
        return p != null && YujiMonster.vulnerable(p) && p.hasEffect(YujiContent.BETRAYED.get())
                && data.getLong("Until") > mob.level().getGameTime() ? p : null;
    }
    public static void restore(Mob mob) {
        CompoundTag data = mob.getPersistentData().getCompound(INFLUENCE);
        if (data.isEmpty()) return;
        if (mob instanceof Goat goat && data.getBoolean("GoatRamOwned")) {
            stopGoatRam(goat, false);
            // Vanilla preparation retains a private candidate on stop; do not resume that old victim later.
            installGoatRamTargeting(goat);
            int previous = (int) Math.min(Integer.MAX_VALUE,
                    Math.max(0, data.getLong("GoatRamPreviousCooldownUntil") - goat.level().getGameTime()));
            int cooldown = Math.max(previous, goat.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS).orElse(0));
            if (cooldown > 0) goat.getBrain().setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, cooldown);
            else goat.getBrain().eraseMemory(MemoryModuleType.RAM_COOLDOWN_TICKS);
        }
        if (mob.getTarget() != null && data.hasUUID("Player") && mob.getTarget().getUUID().equals(data.getUUID("Player"))) {
            Entity previous = data.hasUUID("PreviousTarget") ? ((ServerLevel) mob.level()).getEntity(data.getUUID("PreviousTarget")) : null;
            mob.setTarget(previous instanceof LivingEntity l && l.isAlive() ? l : null);
            if (!(mob instanceof Goat)) mob.getNavigation().stop();
        }
        if (mob instanceof TamableAnimal pet) pet.setOrderedToSit(data.getBoolean("WasSitting"));
        if (mob instanceof NeutralMob neutral && data.hasUUID("Player") && data.getUUID("Player").equals(neutral.getPersistentAngerTarget())) {
            neutral.setPersistentAngerTarget(data.hasUUID("PreviousAnger") ? data.getUUID("PreviousAnger") : null);
            neutral.setRemainingPersistentAngerTime(data.getInt("PreviousAngerTime"));
        }
        var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED); if (speed != null) speed.removeModifier(SPEED);
        var attack = mob.getAttribute(Attributes.ATTACK_DAMAGE); if (attack != null) attack.removeModifier(ATTACK);
        var follow = mob.getAttribute(Attributes.FOLLOW_RANGE); if (follow != null) follow.removeModifier(FOLLOW);
        mob.getPersistentData().remove(INFLUENCE);
    }
    private static void restoreFollowers(Player p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        for (Entity entity : com.zeropointsix.eraser.yuji.city.RainCitySpawns.snapshot(level)) if (entity instanceof Mob mob) {
            CompoundTag data = mob.getPersistentData().getCompound(INFLUENCE);
            if (data.hasUUID("Player") && data.getUUID("Player").equals(p.getUUID())) restore(mob);
        }
    }
    @SubscribeEvent public static void mobTick(LivingEvent.LivingTickEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof Mob mob
                && mob.getPersistentData().contains(INFLUENCE) && influencedPlayer(mob) == null) restore(mob);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        if (mob instanceof Goat goat) {
            CompoundTag influence = goat.getPersistentData().getCompound(INFLUENCE);
            boolean legacy = !influence.getBoolean("GoatRamOwned") && influence.hasUUID("Player")
                    && influence.contains("GoatRamPreviousCooldownUntil");
            if (legacy) influence.putBoolean("GoatRamOwned", true);
            // Keep vanilla ram intact until betrayal actually owns the goat. Rebuilding every
            // spawn brain was aborting PrepareRam in the same tick and writing a 600-tick lockout.
            if (influence.getBoolean("GoatRamOwned")) installGoatRamTargeting(goat);
            // Legacy saves prove cooldown ownership, not ownership of arbitrary new runtime brain memories.
            if (!legacy && influence.getBoolean("GoatRamOwned")
                    && goat.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET)) {
                var targets = goatRamTargets.computeIfAbsent(goat, key -> new GoatRamTargets());
                targets.ram = goat.getBrain().getMemory(MemoryModuleType.RAM_TARGET).orElse(null);
                targets.walk = goat.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                targets.look = goat.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).orElse(null);
            }
            return;
        }
        if (mob instanceof Enemy) {
            // Lock the victim but preserve native ranged, charging and exploding attacks.
            mob.targetSelector.addGoal(0, new Goal() {
                { setFlags(EnumSet.of(Flag.TARGET)); }
                @Override public boolean canUse() { return influencedPlayer(mob) != null; }
                @Override public boolean requiresUpdateEveryTick() { return true; }
                @Override public void tick() { mob.setTarget(influencedPlayer(mob)); }
            });
            return;
        }
        mob.goalSelector.addGoal(0, new Goal() {
            private int attackTicks;
            { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
            @Override public boolean canUse() { return influencedPlayer(mob) != null; }
            @Override public boolean requiresUpdateEveryTick() { return true; }
            @Override public void tick() {
                Player player = influencedPlayer(mob); if (player == null) return;
                if (mob instanceof Cat || mob instanceof Parrot) { mob.getNavigation().stop(); return; }
                if (mob instanceof AbstractVillager) {
                    if (mob.tickCount % 10 == 0) {
                        Vec3 away = mob.position().subtract(player.position()).normalize().scale(12).add(mob.position());
                        mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
                    }
                    return;
                }
                mob.setTarget(player); mob.getLookControl().setLookAt(player);
                if (mob.tickCount % 10 == 0) mob.getNavigation().moveTo(player, 1);
                if (--attackTicks <= 0 && mob.hasLineOfSight(player)) {
                    if (mob instanceof RangedAttackMob ranged && mob.distanceToSqr(player) < 100) { ranged.performRangedAttack(player, 1); attackTicks = 30; }
                    else if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null
                            && mob.distanceTo(player) < mob.getBbWidth() + player.getBbWidth() + .7) {
                        mob.doHurtTarget(player); attackTicks = 20;
                    }
                }
            }
        });
    }
    @SubscribeEvent public static void trade(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof AbstractVillager && event.getEntity().hasEffect(YujiContent.BETRAYED.get()) && YujiConfig.AFFECT_VILLAGERS.get()) {
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL); event.setCanceled(true);
        }
    }
    private static void installGoatRamTargeting(Goat goat) {
        var brain = goat.getBrain();
        var builder = brain.createBuilder();
        boolean changed = false;
        for (var activities : builder.getAvailableBehaviorsByPriority().values()) {
            var behaviors = activities.get(Activity.RAM);
            if (behaviors == null) continue;
            if (behaviors.removeIf(behavior -> behavior.getClass() == PrepareRamNearestTarget.class
                    || behavior instanceof BetrayalGoatPreparation)) {
                behaviors.add(new BetrayalGoatPreparation(goat));
                changed = true;
            }
            if (behaviors.removeIf(behavior -> behavior.getClass() == RamTarget.class || behavior instanceof BetrayalGoatRam)) {
                behaviors.add(new BetrayalGoatRam(goat));
                changed = true;
            }
        }
        if (changed) {
            // BrainBuilder copies the behavior sets but retains all untouched behavior instances.
            brain.removeAllBehaviors();
            brain.copyFromBuilder(builder);
        }
    }
    private static TargetingConditions goatRamTargeting(Goat goat, boolean chooseVictim) {
        return TargetingConditions.forCombat().selector(candidate -> {
            Player victim = chooseVictim ? influencedPlayer(goat) : null;
            return candidate.getType() != EntityType.GOAT
                    && candidate.level().getWorldBorder().isWithinBounds(candidate.getBoundingBox())
                    && (victim == null || candidate == victim);
        });
    }
    private static final class GoatRamTargets {
        private WalkTarget walk;
        private PositionTracker look;
        private Vec3 ram;
    }
    private static final class BetrayalGoatPreparation extends PrepareRamNearestTarget<Goat> {
        BetrayalGoatPreparation(Goat goat) {
            super(g -> g.isScreamingGoat() ? 100 : 600, GoatAi.RAM_MIN_DISTANCE, GoatAi.RAM_MAX_DISTANCE,
                    1.25F, goatRamTargeting(goat, true), GoatAi.RAM_PREPARE_TIME,
                    g -> g.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_PREPARE_RAM : SoundEvents.GOAT_PREPARE_RAM);
        }
        @Override protected void tick(ServerLevel level, Goat goat, long time) {
            var brain = goat.getBrain();
            var walk = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            var look = brain.getMemory(MemoryModuleType.LOOK_TARGET).orElse(null);
            var ram = brain.getMemory(MemoryModuleType.RAM_TARGET).orElse(null);
            super.tick(level, goat, time);
            if (!goat.getPersistentData().getCompound(INFLUENCE).getBoolean("GoatRamOwned")) return;
            var owned = goatRamTargets.computeIfAbsent(goat, key -> new GoatRamTargets());
            if (brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) != walk) owned.walk = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            if (brain.getMemory(MemoryModuleType.LOOK_TARGET).orElse(null) != look) owned.look = brain.getMemory(MemoryModuleType.LOOK_TARGET).orElse(null);
            if (brain.getMemory(MemoryModuleType.RAM_TARGET).orElse(null) != ram) owned.ram = brain.getMemory(MemoryModuleType.RAM_TARGET).orElse(null);
        }
    }
    private static final class BetrayalGoatRam extends RamTarget {
        BetrayalGoatRam(Goat goat) {
            super(g -> g.isScreamingGoat() ? UniformInt.of(100, 300) : UniformInt.of(600, 6000),
                    goatRamTargeting(goat, false), 3.0F,
                    g -> g.isBaby() ? GoatAi.BABY_RAM_KNOCKBACK_FORCE : GoatAi.ADULT_RAM_KNOCKBACK_FORCE,
                    g -> g.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_RAM_IMPACT : SoundEvents.GOAT_RAM_IMPACT,
                    g -> g.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_HORN_BREAK : SoundEvents.GOAT_HORN_BREAK);
        }
        @Override protected void start(ServerLevel level, Goat goat, long time) {
            super.start(level, goat, time);
            CompoundTag influence = goat.getPersistentData().getCompound(INFLUENCE);
            if (influence.getBoolean("GoatRamOwned")) {
                influence.putBoolean("GoatRamCompleted", true);
                var owned = goatRamTargets.computeIfAbsent(goat, key -> new GoatRamTargets());
                owned.walk = goat.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                owned.ram = goat.getBrain().getMemory(MemoryModuleType.RAM_TARGET).orElse(null);
            }
        }
    }
    private static void stopGoatRam(Goat goat, boolean takeover) {
        var brain = goat.getBrain();
        var level = (ServerLevel) goat.level();
        boolean active = brain.hasMemoryValue(MemoryModuleType.RAM_TARGET);
        for (var behavior : brain.getRunningBehaviors()) {
            if (behavior instanceof PrepareRamNearestTarget<?> || behavior instanceof RamTarget) {
                behavior.doStop(level, goat, level.getGameTime());
                active = true;
            }
        }
        var owned = goatRamTargets.remove(goat);
        boolean ownedInfluence = goat.getPersistentData().getCompound(INFLUENCE).getBoolean("GoatRamOwned");
        boolean force = takeover && active;
        if (force || ownedInfluence || (owned != null && owned.ram != null
                && brain.getMemory(MemoryModuleType.RAM_TARGET).orElse(null) == owned.ram)) {
            brain.eraseMemory(MemoryModuleType.RAM_TARGET);
        }
        // Vanilla timeout retains these memories. Erase only the exact values written by our ram behavior.
        if (force || owned != null && owned.walk != null && brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == owned.walk) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.PATH);
            goat.getNavigation().stop();
        }
        if (force || owned != null && owned.look != null && brain.getMemory(MemoryModuleType.LOOK_TARGET).orElse(null) == owned.look) {
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        if (active) level.broadcastEntityEvent(goat, (byte) 59);
    }
    private YujiAfflictions() {}
}
