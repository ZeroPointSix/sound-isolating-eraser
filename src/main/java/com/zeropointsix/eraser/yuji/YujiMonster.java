package com.zeropointsix.eraser.yuji;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class YujiMonster extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> LAYERS = SynchedEntityData.defineId(YujiMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> ACTION = SynchedEntityData.defineId(YujiMonster.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ACTION_SERIAL = SynchedEntityData.defineId(YujiMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> LOOT = SynchedEntityData.defineId(YujiMonster.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> WHITE_ROBE = SynchedEntityData.defineId(YujiMonster.class, EntityDataSerializers.BOOLEAN);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent boss = new ServerBossEvent(Component.translatable("entity.qihai.shifotou"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private final List<ItemStack> hoard = new ArrayList<>();
    private final YujiKind kind;
    private UUID grudge;
    private int attackCooldown, skillCooldown = 20, actionTicks, actionLength, burstCooldown, fleeTicks, followTicks, startledTicks;
    private UUID actionTarget;
    private Vec3 falseVoice;
    private int voiceWindow, missedVoices, deathAge, animationSerial = -1;
    private boolean stompBurst;
    /** Qiyu summoning: cast interval, alive cap per summoner, scan radius. */
    public static final int SUMMON_INTERVAL = 200, SUMMON_CAP = 4;
    private static final double SUMMON_RANGE = 32;
    private static final YujiKind[] SUMMONABLE = {YujiKind.YUCHI, YujiKind.SHASHENG, YujiKind.TOUDAO, YujiKind.EKOU,
            YujiKind.WANGYU, YujiKind.LIANGSHE, YujiKind.XIEYIN, YujiKind.TANYU};
    private int summonCooldown = 60;
    @Nullable private UUID summoner;

    public YujiMonster(YujiKind kind, EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.kind = kind;
        xpReward = kind.giant() ? 50 : 5;
        if (kind == YujiKind.TANYU) setLayers(2, false);
        if (kind == YujiKind.SHIFOTOU) {
            setHealth(getMaxHealth());
            // Ground paths can reject tall walls before the boss ever reaches them.
            navigation = new net.minecraft.world.entity.ai.navigation.WallClimberNavigation(this, level);
        }
        if (kind == YujiKind.YUCHI && navigation instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground)
            ground.setCanOpenDoors(false);
        refreshDimensions();
    }
    @Override public double getAttributeValue(Attribute attribute) {
        if (kind != YujiKind.SHIFOTOU || attribute != Attributes.MAX_HEALTH && attribute != Attributes.ARMOR)
            return super.getAttributeValue(attribute);
        // Vanilla caps MAX_HEALTH at 1024 and ARMOR at 30; only this boss needs more.
        // Retain the normal modifier order without changing the global attribute range.
        var instance = getAttribute(attribute);
        double base = instance.getBaseValue();
        for (var modifier : instance.getModifiers(AttributeModifier.Operation.ADDITION)) base += modifier.getAmount();
        double value = base;
        for (var modifier : instance.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE)) value += base * modifier.getAmount();
        for (var modifier : instance.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL)) value *= 1 + modifier.getAmount();
        return Double.isFinite(value) ? Mth.clamp(value, 1, 1000000) : 1;
    }
    @Override public float getWalkTargetValue(BlockPos pos, net.minecraft.world.level.LevelReader level) {
        if (kind == YujiKind.WANGYU) return 15 - level.getMaxLocalRawBrightness(pos);
        if (kind == YujiKind.QIYU) return super.getWalkTargetValue(pos, level) + (float) Mth.clamp(pos.getY() - getY(), -8, 8);
        return super.getWalkTargetValue(pos, level);
    }
    public YujiKind kind() { return kind; }
    public boolean whiteRobe() { return kind == YujiKind.YUCHI && entityData.get(WHITE_ROBE); }
    public void setWhiteRobe(boolean value) { entityData.set(WHITE_ROBE, kind == YujiKind.YUCHI && value); }
    public int layers() { return entityData.get(LAYERS); }
    public ItemStack displayedLoot() { return entityData.get(LOOT); }
    public String action() { return entityData.get(ACTION); }
    public int actionSerial() { return entityData.get(ACTION_SERIAL); }
    public boolean crawling() { return kind == YujiKind.YUCHI && getHealth() < getMaxHealth() * .3F; }
    public static boolean vulnerable(Player p) { return p.isAlive() && !p.isCreative() && !p.isSpectator(); }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LAYERS, 2); entityData.define(ACTION, "");
        entityData.define(ACTION_SERIAL, 0); entityData.define(LOOT, ItemStack.EMPTY);
        entityData.define(WHITE_ROBE, false);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new Goal() {
            { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
            @Override public boolean canUse() { return getTarget() != null && getTarget().isAlive(); }
            @Override public boolean requiresUpdateEveryTick() { return true; }
            @Override public void tick() { pursue(); }
            @Override public void stop() { getNavigation().stop(); }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .6));
    }
    private void pursue() {
        LivingEntity target = getTarget();
        if (target == null || startledTicks > 0) { getNavigation().stop(); return; }
        getLookControl().setLookAt(target, 20, 20);
        double distance = distanceTo(target);
        double stop = kind == YujiKind.YUCHI ? Math.max(1, 6 - followTicks / 200.) : 0;
        if (kind == YujiKind.LIANGSHE || kind == YujiKind.XIEYIN) stop = 9;
        if (kind == YujiKind.WANGYU) stop = target instanceof Player p && lookingAt(p, getEyePosition(), 70) ? 9 : 1;
        if (fleeTicks > 0 || (stop == 9 && distance < 8)) {
            if (tickCount % 10 == 0) {
                Vec3 away = position().subtract(target.position()).normalize().scale(7).add(position());
                getNavigation().moveTo(away.x, away.y, away.z, 1.2);
            }
        } else if (distance > stop + .5 && tickCount % 10 == 0) {
            getNavigation().moveTo(target, kind == YujiKind.TANYU && distance < 8 ? 1.36 : 1);
        } else if (distance <= stop + .5) getNavigation().stop();
        if (kind == YujiKind.QIYU && !onGround()) setDeltaMovement(getDeltaMovement().multiply(1, .75, 1));
        if (fleeTicks == 0 && attackCooldown == 0 && actionTicks == 0 && kind.damage > 0
                && distance <= getBbWidth() + target.getBbWidth() + .8 && hasLineOfSight(target)) {
            String action = switch (kind) {
                case SHASHENG -> "chop"; case TOUDAO -> "snatch"; case TANYU -> "grab_bite";
                case CHENNU -> "stomp"; case SHIFOTOU -> random.nextBoolean() ? "slam" : "sweep";
                case QIYU -> "strike"; case LIANGSHE -> "scratch"; default -> "claw";
            };
            begin(action, kind.giant() ? 40 : 24, target);
            attackCooldown = kind.giant() ? 70 : 32;
        }
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (attackCooldown > 0) attackCooldown--;
        if (burstCooldown > 0) burstCooldown--;
        if (fleeTicks > 0) fleeTicks--;
        if (startledTicks > 0) startledTicks--;
        if (kind == YujiKind.CHENNU) {
            Player player = grudgePlayer();
            if (player == null && getPersistentData().getBoolean("YujiHorde") && com.zeropointsix.eraser.yuji.city.RainCityContent.isCity(level())
                    && getTarget() instanceof Player waveTarget) player = waveTarget;
            setTarget(player != null && vulnerable(player) ? player : null);
        } else if (kind == YujiKind.SHIFOTOU) {
            if (getTarget() == null || !getTarget().isAlive() || distanceToSqr(getTarget()) > 64 * 64
                    || getTarget() instanceof Player p && !vulnerable(p)) {
                LivingEntity pick = nearbyPlayers(64).stream().filter(this::hasLineOfSight)
                        .min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
                if (pick == null) pick = level().getEntitiesOfClass(IronGolem.class, getBoundingBox().inflate(64), g -> g.isAlive())
                        .stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
                setTarget(pick);
            }
        } else if (getTarget() == null || !getTarget().isAlive() || distanceToSqr(getTarget()) > (getPersistentData().getBoolean("YujiHorde") && com.zeropointsix.eraser.yuji.city.RainCityContent.isCity(level()) ? 128 * 128 : 40 * 40)
                || getTarget() instanceof Player p && !vulnerable(p)) {
            setTarget(nearbyPlayers(kind == YujiKind.WANGYU ? 24 : 32).stream()
                    .filter(p -> kind == YujiKind.EKOU || kind == YujiKind.LIANGSHE || kind == YujiKind.XIEYIN || hasLineOfSight(p))
                    .min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null));
        }
        if (kind == YujiKind.YUCHI) {
            if (getTarget() != null) followTicks++;
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(crawling() ? .08 : kind.speed);
            if (tickCount % 20 == 0 && getTarget() instanceof Player p && getDeltaMovement().horizontalDistanceSqr() > .0001) {
                for (Monster mob : level().getEntitiesOfClass(Monster.class, getBoundingBox().inflate(24), m -> m != this && m.hasLineOfSight(this))) {
                    if (!(mob instanceof YujiMonster y) || (y.kind != YujiKind.YUCHI && y.kind != YujiKind.CHENNU)) mob.setTarget(p);
                }
            }
        }
        if (kind == YujiKind.XIEYIN && startledTicks == 0 && tickCount % 10 == 0) {
            for (Player p : nearbyPlayers(4)) if (p.hasLineOfSight(this) && lookingAt(p, getEyePosition(), 20)) {
                startledTicks = 60; begin("curl", 60, null); break;
            }
        }
        if (actionTicks > 0) {
            int elapsed = actionLength - actionTicks;
            resolveAction(elapsed);
            if (--actionTicks == 0) entityData.set(ACTION, "");
        }
        if (--skillCooldown <= 0) { useSkill(); }
        if (kind == YujiKind.QIYU && --summonCooldown <= 0) {
            if (actionTicks == 0 && canSummon()) { begin("summon", 30, getTarget()); summonCooldown = SUMMON_INTERVAL; }
            else summonCooldown = 20;
        }
        if (kind == YujiKind.TANYU && tickCount % 10 == 0) collectItems();
        if (kind == YujiKind.SHIFOTOU) {
            boss.setProgress(getHealth() / getMaxHealth());
            boss.setName(getDisplayName());
            if (horizontalCollision && getTarget() != null) setDeltaMovement(getDeltaMovement().x, .2, getDeltaMovement().z);
            if (tickCount % 20 == 0) {
                for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox(), YujiMonster::vulnerable)) {
                    p.hurt(damageSources().mobAttack(this), 10);
                }
            }
        }
        if (kind == YujiKind.CHENNU && getTarget() != null && tickCount % 10 == 0
                && ForgeEventFactory.getMobGriefingEvent(level(), this)) {
            for (BlockPos pos : BlockPos.betweenClosed(blockPosition().offset(-2, 0, -2), blockPosition().offset(2, 5, 2))) {
                BlockState state = level().getBlockState(pos);
                if ((state.is(BlockTags.LEAVES) || state.is(net.minecraftforge.common.Tags.Blocks.GLASS)
                        || state.is(net.minecraftforge.common.Tags.Blocks.GLASS_PANES)) && level().getBlockEntity(pos) == null
                        && ForgeEventFactory.onEntityDestroyBlock(this, pos, state)) level().destroyBlock(pos, false, this);
            }
        }
        if (voiceWindow > 0 && getTarget() instanceof ServerPlayer p) {
            voiceWindow--;
            if (lookingAt(p, falseVoice, 15)) { YujiAfflictions.weakness(p, this, 160, 1); voiceWindow = 0; }
        }
    }
    public List<Player> nearbyPlayers(double range) {
        return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(range), p -> vulnerable(p) && distanceToSqr(p) <= range * range);
    }
    @Nullable private Player grudgePlayer() {
        if (grudge == null) return null;
        Player player = level().getPlayerByUUID(grudge);
        if (player == null && getTarget() instanceof Player target && grudge.equals(target.getUUID())) player = target;
        // An unloaded player must not let a second attacker overwrite the saved grudge.
        if (player != null && (!player.isAlive() || distanceToSqr(player) > 128 * 128)) {
            grudge = null;
            return null;
        }
        return player;
    }
    public static boolean lookingAt(Player player, Vec3 point, double degrees) {
        return point != null && player.getLookAngle().dot(point.subtract(player.getEyePosition()).normalize()) >= Math.cos(Math.toRadians(degrees));
    }
    private void useSkill() {
        skillCooldown = 80;
        switch (kind) {
            case EKOU -> {
                LivingEntity target = getTarget();
                // Never cut an ongoing chant/volley short; that would silently drop its second hit.
                if (actionTicks > 0) { skillCooldown = 10; return; }
                if (target != null && distanceToSqr(target) <= 24 * 24 && hasLineOfSight(target)) begin("chant", 40, target);
                else if (target != null && distanceToSqr(target) <= 256 && hasLineOfSight(target)) begin("spike_volley", 28, target);
                skillCooldown = 60;
            }
            case LIANGSHE -> {
                for (Player p : nearbyPlayers(20)) YujiAfflictions.apply(p, this, "betrayed");
                begin("whisper", 28, getTarget()); skillCooldown = 300;
            }
            case XIEYIN -> {
                if (startledTicks == 0) {
                    for (Player p : nearbyPlayers(16)) YujiAfflictions.apply(p, this, "enthralled");
                    begin("lure", 30, getTarget());
                } else skillCooldown = 10;
            }
            case WANGYU -> { mimic(); skillCooldown = 120 + random.nextInt(81); }
            case QIYU -> {
                // Busy striking/summoning: retry shortly instead of silently skipping a whole aura cycle.
                if (actionTicks == 0) { begin("bless", 30, null); skillCooldown = 240; } else skillCooldown = 10;
            }
            case CHENNU -> {
                if (actionTicks == 0 && !nearbyPlayers(4).isEmpty()) { begin("stomp", 36, getTarget()); stompBurst = true; }
                skillCooldown = 80;
            }
            case SHIFOTOU -> {
                if (getTarget() != null) begin("gaze", 40, getTarget());
                skillCooldown = 400;
            }
            default -> { }
        }
    }
    public void begin(String name, int duration, @Nullable LivingEntity target) {
        if (!isAlive()) return;
        entityData.set(ACTION, name); entityData.set(ACTION_SERIAL, actionSerial() + 1);
        actionTicks = duration; actionLength = duration; actionTarget = target == null ? null : target.getUUID();
        stompBurst = false;
    }
    private void resolveAction(int tick) {
        LivingEntity target = actionTarget == null ? null : ((ServerLevel) level()).getEntity(actionTarget) instanceof LivingEntity l ? l : null;
        switch (action()) {
            case "chop" -> { if (tick == 6 || tick == 12) strike(target, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), tick == 12); }
            case "grab_bite" -> {
                if (tick == 12) {
                    // A lagging client can skip an entire action serial; send the bite at the authoritative hit tick.
                    playSound(sound("bite"), 1, 1);
                    strike(target, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), false);
                }
            }
            case "snatch", "claw", "scratch", "strike" -> {
                if (tick == 6 && strike(target, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), false)
                        && kind == YujiKind.TOUDAO && target instanceof Player p) steal(p);
            }
            case "spike_volley" -> { if (tick == 11 && target != null) volley(target, false); }
            case "stomp" -> { if (tick == 12) { if (stompBurst) areaDamage(4, 30, true); else strike(target, 20, false); } }
            case "slam" -> { if (tick == 16) { areaDamage(8, 30, true); strike(target, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * .4F, true); } }
            case "sweep" -> { if (tick == 12) areaDamage(8, 60, true); }
            case "gaze" -> {
                if (tick == 12 && target != null && target.isAlive() && distanceToSqr(target) <= 32 * 32) {
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60));
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                }
            }
            case "chant" -> {
                if ((tick == 15 || tick == 32) && target != null && target.isAlive()
                        && distanceToSqr(target) <= 26 * 26 && hasLineOfSight(target))
                    target.hurt(damageSources().indirectMagic(this, this), 6);
            }
            case "bless" -> { if (tick == 15) blessAllies(); }
            case "summon" -> { if (tick == 18) summonAllies(); }
            default -> { }
        }
    }
    private void blessAllies() {
        MobEffectInstance[] buffs = {new MobEffectInstance(MobEffects.DAMAGE_BOOST, 220, 0),
                new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 220, 0),
                new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 220, 0),
                new MobEffectInstance(MobEffects.REGENERATION, 220, 0)};
        for (YujiMonster ally : level().getEntitiesOfClass(YujiMonster.class, getBoundingBox().inflate(16), y -> y != this))
            for (MobEffectInstance buff : buffs) ally.addEffect(new MobEffectInstance(buff));
    }
    @Nullable public UUID summoner() { return summoner; }
    public int livingSummons() {
        return level().getEntitiesOfClass(YujiMonster.class, getBoundingBox().inflate(SUMMON_RANGE),
                y -> y.isAlive() && getUUID().equals(y.summoner)).size();
    }
    public boolean canSummon() {
        return level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                && (getTarget() != null && getTarget().isAlive() || !nearbyPlayers(SUMMON_RANGE).isEmpty())
                && livingSummons() < SUMMON_CAP;
    }
    /** Calls one or two random Yuji kinds (never Qiyu or the giants) beside itself, up to SUMMON_CAP alive. */
    public int summonAllies() {
        if (!(level() instanceof ServerLevel server)) return 0;
        int room = SUMMON_CAP - livingSummons(), wanted = Math.min(room, 1 + random.nextInt(2)), made = 0;
        for (int attempt = 0; made < wanted && attempt < 12; attempt++) {
            YujiKind pick = SUMMONABLE[random.nextInt(SUMMONABLE.length)];
            YujiMonster mob = YujiContent.MOBS.get(pick).get().create(server);
            if (mob == null) continue;
            double angle = random.nextDouble() * Math.PI * 2, radius = 2 + random.nextDouble() * 2;
            mob.moveTo(getX() + Math.cos(angle) * radius, Math.floor(getY()), getZ() + Math.sin(angle) * radius, random.nextFloat() * 360, 0);
            if (!server.noCollision(mob) || !server.getBlockState(mob.blockPosition().below()).isSolid()) { mob.discard(); continue; }
            mob.finalizeSpawn(server, server.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            mob.summoner = getUUID();
            if (getTarget() != null) mob.setTarget(getTarget());
            server.addFreshEntityWithPassengers(mob);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, mob.getX(), mob.getY() + .5, mob.getZ(), 16, .4, .6, .4, .02);
            made++;
        }
        return made;
    }
    private boolean strike(@Nullable LivingEntity target, float damage, boolean secondHit) {
        if (target == null || !target.isAlive() || target instanceof Player p && !vulnerable(p)
                || distanceTo(target) > getBbWidth() + target.getBbWidth() + 1 || !hasLineOfSight(target)) return false;
        int invulnerability = target.invulnerableTime;
        if (secondHit) target.invulnerableTime = 0;
        boolean hit = target.hurt(damageSources().mobAttack(this), damage);
        if (!hit && secondHit) target.invulnerableTime = invulnerability;
        return hit;
    }
    @Override public boolean doHurtTarget(Entity target) {
        if (kind == YujiKind.YUCHI || kind == YujiKind.XIEYIN || kind == YujiKind.EKOU) return false;
        return super.doHurtTarget(target);
    }
    private void areaDamage(double range, float damage, boolean launch) {
        List<LivingEntity> victims = new ArrayList<>(nearbyPlayers(range));
        // Giants also crush defending iron golems caught in the shockwave.
        if (kind.giant()) victims.addAll(level().getEntitiesOfClass(IronGolem.class, getBoundingBox().inflate(range), LivingEntity::isAlive));
        for (LivingEntity p : victims) if (p.hurt(damageSources().mobAttack(this), damage) && launch) {
            p.push(0, .65, 0); p.hurtMarked = true;
        }
    }
    public void volley(LivingEntity target, boolean burst) {
        int count = burst ? 8 : 5;
        Vec3 aim = target.getEyePosition().subtract(getEyePosition()).normalize();
        for (int i = 0; i < count; i++) {
            double angle = burst ? i * Math.PI / 4 : Math.toRadians(-15 + 7.5 * i);
            Vec3 direction = burst ? new Vec3(Math.cos(angle), .08, Math.sin(angle)) : aim.yRot((float) angle);
            BoneSpike spike = new BoneSpike(YujiContent.BONE_SPIKE.get(), level());
            spike.setOwner(this); spike.setPos(getX(), getY() + getBbHeight() * .6, getZ());
            spike.shoot(direction.x, direction.y, direction.z, 1.6F, 0);
            level().addFreshEntity(spike);
        }
    }
    public void steal(Player player) {
        int slot = -1, score = -1;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            int candidate = stack.isEmpty() ? -1 : (stack.isEnchanted() ? 10 : 0) + stack.getRarity().ordinal();
            if (candidate > score) { score = candidate; slot = i; }
        }
        if (slot < 0) return;
        ItemStack stolen = player.getInventory().removeItemNoUpdate(slot);
        hoard.add(stolen); entityData.set(LOOT, stolen.copy());
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
        fleeTicks = 200; setPersistenceRequired();
    }
    private void collectItems() {
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1), i -> i.isAlive() && !i.hasPickUpDelay())) {
            ItemStack stack = item.getItem().copy();
            if (stack.is(Items.ROTTEN_FLESH)) { setLayers(layers() + 1, true); stack.shrink(1); begin("eat", 24, null); }
            if (!stack.isEmpty()) hoard.add(stack);
            take(item, item.getItem().getCount()); item.discard(); setPersistenceRequired();
        }
    }
    public void setLayers(int value, boolean heal) {
        int old = layers(), next = Mth.clamp(value, 0, YujiConfig.SPEC.isLoaded() ? YujiConfig.MAX_LAYERS.get() : 12);
        entityData.set(LAYERS, next);
        if (!level().isClientSide) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(30 + next * 4);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4 + next * .5);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.25 * (next >= 8 ? .9 : 1));
            setHealth(Math.min(getHealth(), getMaxHealth()));
            if (heal && next > old) heal((next - old) * 4);
        }
        refreshDimensions();
    }
    public void absorb(int layers) { setLayers(layers() + layers, true); begin("absorb", 24, null); }
    public float modelScale() {
        if (kind == YujiKind.SHIFOTOU) return 5;
        if (kind == YujiKind.XIEYIN) return 3;
        return kind == YujiKind.TANYU ? 1 + .02F * layers() : 1;
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        if (kind == null) return super.getDimensions(pose);
        return EntityDimensions.scalable(kind.width * modelScale(), crawling() ? .6F : kind.height * modelScale());
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LAYERS.equals(key)) refreshDimensions();
        if (kind == YujiKind.YUCHI && Math.abs(getBbHeight() - (crawling() ? .6F : kind.height)) > .01) refreshDimensions();
    }
    @Override public boolean hurt(DamageSource source, float damage) {
        boolean accepted = super.hurt(source, damage);
        if (!accepted || level().isClientSide || !isAlive()) return accepted;
        if (kind == YujiKind.EKOU && burstCooldown == 0) { burstCooldown = 20; volley(this, true); begin("burst", 16, null); }
        if (kind == YujiKind.TOUDAO) {
            begin("scream", 20, null);
            for (Player p : nearbyPlayers(8)) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60));
        }
        if (kind == YujiKind.CHENNU && source.getEntity() instanceof Player p && vulnerable(p)) {
            Player remembered = grudgePlayer();
            if (grudge == null) {
                grudge = p.getUUID(); setTarget(p); setPersistenceRequired(); begin("roar", 30, p);
            } else {
                setTarget(remembered != null && vulnerable(remembered) ? remembered : null);
            }
        }
        if (kind == YujiKind.TANYU) {
            if (damage >= 6) { setLayers(layers() - 1, false); begin("peel", 20, null); }
            if (source.getEntity() instanceof LivingEntity attacker) {
                for (YujiMonster friend : level().getEntitiesOfClass(YujiMonster.class, getBoundingBox().inflate(24), y -> y.kind == YujiKind.TANYU)) friend.setTarget(attacker);
            }
        }
        return true;
    }
    @Override public boolean isInvulnerableTo(DamageSource source) {
        return kind == YujiKind.SHIFOTOU && (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL))
                || super.isInvulnerableTo(source);
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return kind != YujiKind.QIYU && kind != YujiKind.SHIFOTOU && super.causeFallDamage(distance, multiplier, source);
    }
    @Override public boolean onClimbable() { return kind == YujiKind.SHIFOTOU && horizontalCollision || super.onClimbable(); }
    @Override public boolean removeWhenFarAway(double distance) { return hoard.isEmpty() && grudge == null && !kind.giant(); }
    @Override protected boolean shouldDespawnInPeaceful() { return hoard.isEmpty() && super.shouldDespawnInPeaceful(); }
    @Override public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && dead) {
            // Player property is restitution, not loot controlled by doMobLoot.
            List<ItemStack> returned = new ArrayList<>(hoard);
            hoard.clear(); entityData.set(LOOT, ItemStack.EMPTY);
            for (ItemStack stack : returned) spawnAtLocation(stack);
            YujiAfflictions.releaseSource(this);
        }
    }
    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); if (kind == YujiKind.SHIFOTOU) boss.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); boss.removePlayer(p); }
    @Override public void remove(RemovalReason reason) { super.remove(reason); boss.removeAllPlayers(); }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                                 @Nullable SpawnGroupData group, @Nullable CompoundTag nbt) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, group, nbt);
        if (kind == YujiKind.TANYU) { setLayers(reason == MobSpawnType.SPAWN_EGG ? 1 + random.nextInt(3) : 2, false); setHealth(getMaxHealth()); }
        return result;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putInt("YujiLayers", layers()); tag.putInt("YujiFlee", fleeTicks);
        tag.putBoolean("YujiWhiteRobe", whiteRobe());
        tag.putInt("YujiFollow", followTicks); if (summoner != null) tag.putUUID("YujiSummoner", summoner); if (grudge != null) tag.putUUID("YujiGrudge", grudge);
        ListTag items = new ListTag(); hoard.forEach(stack -> items.add(stack.save(new CompoundTag()))); tag.put("YujiHoard", items);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        if (kind == YujiKind.TANYU) setLayers(tag.contains("YujiLayers") ? tag.getInt("YujiLayers") : 2, false);
        super.readAdditionalSaveData(tag);
        setWhiteRobe(tag.getBoolean("YujiWhiteRobe"));
        if (kind == YujiKind.TANYU && !tag.contains("Health")) setHealth(getMaxHealth());
        fleeTicks = Mth.clamp(tag.getInt("YujiFlee"), 0, 200); followTicks = Mth.clamp(tag.getInt("YujiFollow"), 0, 1000);
        grudge = tag.hasUUID("YujiGrudge") ? tag.getUUID("YujiGrudge") : null;
        summoner = tag.hasUUID("YujiSummoner") ? tag.getUUID("YujiSummoner") : null;
        hoard.clear(); ListTag items = tag.getList("YujiHoard", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) { ItemStack stack = ItemStack.of(items.getCompound(i)); if (!stack.isEmpty()) hoard.add(stack); }
        entityData.set(LOOT, hoard.isEmpty() ? ItemStack.EMPTY : hoard.get(hoard.size() - 1).copy());
        if (!hoard.isEmpty() || grudge != null) setPersistenceRequired();
    }
    private void mimic() {
        if (!(getTarget() instanceof ServerPlayer player) || distanceToSqr(player) > 24 * 24) return;
        falseVoice = null;
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = 6 + random.nextDouble() * 4;
            Vec3 point = player.position().add(Math.cos(angle) * distance, 1, Math.sin(angle) * distance);
            if (!level().clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(), point,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType()
                    .equals(net.minecraft.world.phys.HitResult.Type.MISS)) { falseVoice = point; break; }
        }
        if (falseVoice == null) return;
        SoundEvent[] voices = {SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.CHEST_OPEN, SoundEvents.PLAYER_HURT,
                SoundEvents.VILLAGER_HURT, SoundEvents.WOLF_WHINE, SoundEvents.STONE_STEP};
        player.connection.send(new ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(voices[random.nextInt(voices.length)]),
                SoundSource.HOSTILE, falseVoice.x, falseVoice.y, falseVoice.z, 1, 1, random.nextLong()));
        if (player.hasLineOfSight(this) && lookingAt(player, getEyePosition(), 25)) missedVoices = 0; else missedVoices++;
        YujiAfflictions.weakness(player, this, missedVoices >= 3 ? 400 : 160, missedVoices >= 3 ? 1 : 0);
        voiceWindow = 40; begin("mimic", 24, player);
    }
    @Override protected SoundEvent getAmbientSound() { return kind == YujiKind.QIYU ? null : sound(kind == YujiKind.SHIFOTOU ? "grind" : "ambient"); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return sound("hurt"); }
    @Override protected SoundEvent getDeathSound() { return sound("death"); }
    private SoundEvent sound(String action) { return YujiContent.sound("entity.qihai." + kind.id() + "." + action); }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { if (kind != YujiKind.TOUDAO && sound("step") != null) playSound(sound("step"), .5F, 1); }
    @Override public int getAmbientSoundInterval() { return kind == YujiKind.QIYU ? 298 : 160; }
    @Override protected void tickDeath() { if (++deathAge >= (kind.giant() ? 60 : 16)) super.tickDeath(); }
    public String locomotion(boolean moving) {
        if (!moving) return kind == YujiKind.QIYU ? "idle_play" : "idle";
        return switch (kind) {
            case SHASHENG -> "run"; case TOUDAO -> "scuttle"; case QIYU -> "glide";
            case SHIFOTOU -> horizontalCollision ? "climb" : "crawl";
            case TANYU -> getTarget() != null && distanceToSqr(getTarget()) < 64 ? "crawl_charge" : "walk";
            case YUCHI -> crawling() ? "crawl" : "walk"; default -> "walk";
        };
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 3, state -> {
            String action = isDeadOrDying() ? "death" : !action().isEmpty() ? action() : locomotion(state.isMoving());
            if (animationSerial != actionSerial()) { state.getController().forceAnimationReset(); animationSerial = actionSerial(); }
            String animation = "animation." + kind.id() + "." + action;
            return state.setAndContinue(isDeadOrDying() ? RawAnimation.begin().thenPlayAndHold(animation)
                    : !action().isEmpty() ? RawAnimation.begin().thenPlay(animation) : RawAnimation.begin().thenLoop(animation));
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
