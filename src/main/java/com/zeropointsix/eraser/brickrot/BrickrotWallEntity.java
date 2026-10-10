package com.zeropointsix.eraser.brickrot;

import com.zeropointsix.eraser.ModMain;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class BrickrotWallEntity extends Monster implements GeoEntity {
    public enum Action { IDLE, SCAN, TRACK, WINDUP, CHARGE, BITE, SWEEP, DIVE, UNDERGROUND, WARNING, EMERGE, STAGGER }
    public static final TagKey<Block> STAGGER_BLOCKS = TagKey.create(Registries.BLOCK,
            new ResourceLocation(ModMain.MOD_ID, "brickrot_stagger_blocks"));
    public static final TagKey<Block> FRAGILE_BLOCKS = TagKey.create(Registries.BLOCK,
            new ResourceLocation(ModMain.MOD_ID, "brickrot_fragile_blocks"));
    public static final TagKey<Item> LIGHT_ITEMS = TagKey.create(Registries.ITEM,
            new ResourceLocation(ModMain.MOD_ID, "brickrot_light_items"));
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(
            BrickrotWallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BREACHED = SynchedEntityData.defineId(
            BrickrotWallEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double[] LENGTHS = {2.5, 2, 2.5, 2.5, 2.5, 2.5, 2.5, 2.5, 2.25, 1.6};
    private final BrickrotPart[] parts = new BrickrotPart[9];
    private final BrickrotTrail trail = new BrickrotTrail();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent boss = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS);
    private final Set<UUID> struck = new HashSet<>();
    private Entity quarry;
    private Vec3 chargeDirection = Vec3.ZERO;
    private Vec3 safeSurface;
    private Vec3 emergeAt;
    private int actionTicks;
    private int biteCooldown;
    private int sweepCooldown;
    private int chargeCooldown;
    private int sinceBurrow = 400;
    private double chargeDistance;
    private boolean movedThisTick;
    private int deathHold;

    public BrickrotWallEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 50;
        setMaxUpStep(1);
        for (int i = 0; i < parts.length; i++) parts[i] = new BrickrotPart(this, i + 1);
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 300)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ACTION, Action.SCAN.ordinal());
        entityData.define(BREACHED, false);
    }
    @Override public boolean isMultipartEntity() { return true; }
    @Override public BrickrotPart[] getParts() { return parts; }
    @Override public void setId(int id) {
        super.setId(id);
        if (parts != null) for (int i = 0; i < parts.length; i++)
            if (parts[i] != null) parts[i].setId(id + i + 1);
    }
    public Action action() { return Action.values()[entityData.get(ACTION)]; }
    public boolean phaseTwo() { return entityData.get(BREACHED); }
    public boolean underground() { return action() == Action.UNDERGROUND || action() == Action.WARNING; }
    private boolean burrowing() { return action() == Action.DIVE || underground() || action() == Action.EMERGE; }
    @Override public boolean isPickable() { return isAlive() && !underground(); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(26); }

    @Override public void tick() {
        movedThisTick = false;
        super.tick();
        if (!level().isClientSide) {
            if (getHealth() < getMaxHealth() / 2) entityData.set(BREACHED, true);
            boss.setProgress(getHealth() / getMaxHealth());
            boss.setName(getDisplayName());
            boss.setVisible(isAlive());
        }
        trail.record(position(), forward());
        double offset = 0;
        for (int i = 0; i < parts.length; i++) {
            offset += (LENGTHS[i] + LENGTHS[i + 1]) * 0.45;
            BrickrotPart part = parts[i];
            Vec3 point = trail.sample(offset);
            Vec3 direction = trail.sample(Math.max(0, offset - 0.5)).subtract(point);
            part.xo = part.getX(); part.yo = part.getY(); part.zo = part.getZ();
            part.yRotO = part.getYRot(); part.xRotO = part.getXRot();
            part.setPos(point);
            part.setYRot(yaw(direction));
            part.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
            part.tickCount = tickCount;
        }
        if (!level().isClientSide && isAlive() && movedThisTick && tickCount % 20 == 0) crush();
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        getNavigation().stop();
        biteCooldown = Math.max(0, biteCooldown - 1);
        sweepCooldown = Math.max(0, sweepCooldown - 1);
        chargeCooldown = Math.max(0, chargeCooldown - 1);
        sinceBurrow++;
        actionTicks++;
        switch (action()) {
            case STAGGER -> {
                if (actionTicks >= 100) {
                    radial(position(), 4, 6, 1.8, new HashSet<>());
                    playSound(SoundEvents.RAVAGER_ROAR, 1, 0.6F);
                    transition(Action.SCAN);
                }
            }
            case SCAN -> {
                if (actionTicks >= (phaseTwo() ? 30 : 50)) {
                    quarry = findQuarry();
                    setTarget(quarry instanceof LivingEntity living ? living : null);
                    transition(quarry == null ? Action.IDLE : Action.TRACK);
                }
            }
            case IDLE -> {
                if (actionTicks % 20 == 0 && findQuarry() != null) transition(Action.SCAN);
                else if (actionTicks % 100 < 40) {
                    if (actionTicks % 100 == 1) setYRot(getYRot() + random.nextInt(90) - 45);
                    moveHorizontal(forward().scale(0.12));
                }
            }
            case TRACK -> track();
            case WINDUP -> {
                dust(position());
                if (actionTicks >= 10) {
                    Entity refreshed = findQuarry();
                    if (refreshed != null) quarry = refreshed;
                    if (valid(quarry)) beginCharge(quarry.position());
                    else transition(Action.SCAN);
                }
            }
            case CHARGE -> charge();
            case BITE -> {
                if (actionTicks == 6) {
                    Set<UUID> victims = new HashSet<>();
                    for (LivingEntity entity : victims(getBoundingBox().inflate(4)))
                        if (position().distanceToSqr(entity.position()) <= 16
                                && inFront(entity.position(), 0.5)) strike(entity, position(), 12, 0.8, victims);
                }
                if (actionTicks >= 16) transition(Action.TRACK);
            }
            case SWEEP -> {
                if (actionTicks >= 12 && actionTicks <= 15) {
                    BrickrotPart part = parts[3 + actionTicks - 12];
                    radial(part.position(), 6, 10, 1.2, struck);
                }
                if (actionTicks >= 40) transition(Action.TRACK);
            }
            case DIVE -> {
                setPos(position().add(0, -0.4, 0));
                dust(safeSurface);
                if (actionTicks >= 60) transition(Action.UNDERGROUND);
            }
            case UNDERGROUND -> {
                Vec3 destination = emergeAt.add(0, -4, 0);
                Vec3 difference = destination.subtract(position());
                setPos(position().add(difference.normalize().scale(Math.min(1.2, difference.length()))));
                dust(new Vec3(getX(), safeSurface.y, getZ()));
                if (difference.length() <= 1.2) transition(Action.WARNING);
                else if (actionTicks > 160) {
                    setPos(safeSurface);
                    transition(Action.SCAN);
                }
            }
            case WARNING -> {
                dust(emergeAt);
                if (actionTicks % 5 == 0) playSound(SoundEvents.STONE_BREAK, 0.8F, 0.5F);
                if (actionTicks >= 20) transition(Action.EMERGE);
            }
            case EMERGE -> {
                double t = Math.min(1, actionTicks / 20.0);
                setPos(emergeAt.add(0, -4 + 4 * t + Math.sin(Math.PI * t) * 2, 0));
                if (actionTicks == 10) radial(emergeAt, 4, 14, 1.2, new HashSet<>());
                if (actionTicks >= 20) {
                    setPos(emergeAt);
                    transition(Action.SCAN);
                }
            }
        }
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    private void track() {
        if (!valid(quarry) || (tickCount % 10 == 0 && !detectable(quarry))) {
            transition(Action.SCAN);
            return;
        }
        if (tickCount % 10 == 0) {
            Entity brighter = findQuarry();
            if (brighter instanceof ItemEntity) quarry = brighter;
        }
        double distance = distanceTo(quarry);
        if ((distance > 24 && sinceBurrow >= 100 || sinceBurrow >= 400) && beginBurrow(quarry)) return;
        if (sweepCooldown == 0 && !(quarry instanceof ItemEntity)) {
            for (int i = 3; i <= 6; i++) if (parts[i].distanceToSqr(quarry) <= 36) {
                sweepCooldown = 100;
                transition(Action.SWEEP);
                playSound(SoundEvents.STONE_PLACE, 1, 0.5F);
                return;
            }
        }
        if (distance <= 4 && inFront(quarry.position(), 0.5) && biteCooldown == 0) {
            if (quarry instanceof ItemEntity) { transition(Action.SCAN); return; }
            biteCooldown = 30;
            transition(Action.BITE);
        } else if (distance >= 6 && distance <= 24 && chargeCooldown == 0
                && inFront(quarry.position(), 0.5)) transition(Action.WINDUP);
        else {
            float targetYaw = yaw(quarry.position().subtract(position()));
            setYRot(Mth.approachDegrees(getYRot(), targetYaw, (float) Math.toDegrees(0.3 / 10)));
            moveHorizontal(forward().scale(0.3));
        }
    }

    public void beginCharge(Vec3 target) {
        chargeDirection = target.subtract(position()).multiply(1, 0, 1).normalize();
        if (chargeDirection.lengthSqr() < 0.5) { transition(Action.SCAN); return; }
        setYRot(yaw(chargeDirection));
        chargeDistance = 0;
        chargeCooldown = 60;
        transition(Action.CHARGE);
    }

    private void charge() {
        double speed = phaseTwo() ? 1.08 : 0.9;
        Vec3 step = chargeDirection.scale(Math.min(speed, 24 - chargeDistance));
        AABB ahead = getBoundingBox().expandTowards(step).deflate(0.02);
        boolean stopped = false;
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(ahead.minX), Mth.floor(ahead.minY), Mth.floor(ahead.minZ),
                Mth.floor(ahead.maxX), Mth.floor(ahead.maxY), Mth.floor(ahead.maxZ))) {
            if (!level().hasChunkAt(pos)) { stopped = true; continue; }
            BlockState state = level().getBlockState(pos);
            boolean solid = state.getCollisionShape(level(), pos).toAabbs().stream()
                    .anyMatch(box -> box.move(pos).intersects(ahead));
            if (solid && state.is(STAGGER_BLOCKS)) { stagger(); return; }
            if (state.is(FRAGILE_BLOCKS) && ForgeEventFactory.getMobGriefingEvent(level(), this)
                    && ForgeEventFactory.onEntityDestroyBlock(this, pos, state)) {
                if (!level().destroyBlock(pos, true, this) && solid) stopped = true;
            } else if (solid) stopped = true;
        }
        if (stopped || chargeDistance >= 24) { transition(Action.SCAN); return; }
        Vec3 previous = position();
        AABB before = getBoundingBox();
        moveHorizontal(step);
        Vec3 movement = position().subtract(previous);
        chargeDistance += movement.horizontalDistance();
        for (LivingEntity entity : victims(before.expandTowards(movement)))
            strike(entity, previous, 16, 2, struck);
        dust(position());
        if (horizontalCollision || movement.horizontalDistanceSqr() < 0.0001 || chargeDistance >= 24)
            transition(Action.SCAN);
    }

    public void stagger() {
        transition(Action.STAGGER);
        setDeltaMovement(Vec3.ZERO);
        playSound(SoundEvents.RAVAGER_STUNNED, 1, 0.6F);
    }

    public boolean beginBurrow(Entity target) {
        if (!valid(target) || getY() - 26 <= level().getMinBuildHeight()) return false;
        Vec3 found = null;
        BlockPos feet = target.blockPosition();
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos at = feet.offset(0, dy, 0);
            Vec3 point = Vec3.atBottomCenterOf(at);
            AABB box = getDimensions(getPose()).makeBoundingBox(point);
            if (level().hasChunkAt(at) && level().getWorldBorder().isWithinBounds(box)
                    && !level().getBlockState(at.below()).getCollisionShape(level(), at.below()).isEmpty()
                    && !level().getBlockCollisions(this, box).iterator().hasNext()
                    && !level().containsAnyLiquid(box)) { found = point; break; }
        }
        if (found == null) return false;
        safeSurface = position();
        emergeAt = found;
        sinceBurrow = 0;
        transition(Action.DIVE);
        return true;
    }

    private void transition(Action next) {
        entityData.set(ACTION, next.ordinal());
        actionTicks = 0;
        struck.clear();
        noPhysics = burrowing();
        setNoGravity(burrowing());
        setDeltaMovement(Vec3.ZERO);
    }

    private void moveHorizontal(Vec3 movement) {
        AABB target = getBoundingBox().move(movement);
        if (!level().getWorldBorder().isWithinBounds(target)
                || !level().hasChunkAt(BlockPos.containing(position().add(movement)))) return;
        Vec3 before = position();
        move(MoverType.SELF, movement);
        movedThisTick |= position().subtract(before).horizontalDistanceSqr() > 0.0001;
        setDeltaMovement(new Vec3(0, getDeltaMovement().y, 0));
    }

    private boolean valid(Entity entity) {
        return entity != null && entity.isAlive() && !entity.isSpectator()
                && !(entity instanceof Player player && player.isCreative()) && distanceToSqr(entity) <= 64 * 64;
    }

    public static boolean luminous(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(LIGHT_ITEMS) || stack.getItem() instanceof BlockItem item
                && item.getBlock().defaultBlockState().getLightEmission() > 0);
    }

    public boolean detectable(Entity entity) {
        if (!valid(entity)) return false;
        double distance = distanceToSqr(entity);
        if (entity instanceof ItemEntity item) return luminous(item.getItem()) && distance <= 1600;
        if (entity == getLastHurtByMob() && tickCount - getLastHurtByMobTimestamp() < 100) return true;
        boolean held = entity instanceof LivingEntity living
                && (luminous(living.getMainHandItem()) || luminous(living.getOffhandItem()));
        int range = held ? 40 : level().getMaxLocalRawBrightness(entity.blockPosition()) >= 11 ? 24 : 8;
        return distance <= range * range && clearLine(getEyePosition(), entity.getEyePosition());
    }

    public Entity findQuarry() {
        Entity best = null;
        double score = Double.MAX_VALUE;
        for (Entity candidate : level().getEntities(this, getBoundingBox().inflate(40), this::valid)) {
            if (!(candidate instanceof ItemEntity || candidate instanceof Player
                    || candidate instanceof AbstractVillager || candidate instanceof IronGolem
                    || candidate == getLastHurtByMob()) || !detectable(candidate)) continue;
            double candidateScore = distanceToSqr(candidate) + (candidate instanceof ItemEntity ? 0 : 10000);
            if (candidateScore < score) { best = candidate; score = candidateScore; }
        }
        return best;
    }

    private Vec3 forward() { return Vec3.directionFromRotation(0, getYRot()); }
    private static float yaw(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }
    private boolean inFront(Vec3 target, double cosine) {
        return target.subtract(position()).multiply(1, 0, 1).normalize().dot(forward()) >= cosine;
    }
    private boolean clearLine(Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    private java.util.List<LivingEntity> victims(AABB bounds) {
        return level().getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity != this
                && !(entity instanceof BrickrotWallEntity) && entity.isAlive() && !entity.isSpectator()
                && !(entity instanceof Player player && player.isCreative()));
    }
    private void strike(LivingEntity victim, Vec3 origin, float damage, double power, Set<UUID> hits) {
        if (hits.contains(victim.getUUID()) || !clearLine(origin.add(0, 1, 0), victim.getEyePosition())) return;
        if (victim.hurt(damageSources().mobAttack(this), damage)) {
            hits.add(victim.getUUID());
            Vec3 away = victim.position().subtract(origin).multiply(1, 0, 1).normalize().scale(power);
            victim.push(away.x, action() == Action.EMERGE ? 0.9 : 0.35, away.z);
            victim.hurtMarked = true;
        }
    }
    private void radial(Vec3 origin, double radius, float damage, double power, Set<UUID> hits) {
        for (LivingEntity victim : victims(new AABB(origin, origin).inflate(radius)))
            if (victim.distanceToSqr(origin) <= radius * radius) strike(victim, origin, damage, power, hits);
    }
    private void crush() {
        if (burrowing() || action() == Action.STAGGER) return;
        Set<UUID> hits = new HashSet<>();
        for (BrickrotPart part : parts) for (LivingEntity victim : victims(part.getBoundingBox()))
            strike(victim, part.position(), 4, 0.2, hits);
    }
    private void dust(Vec3 position) {
        if (position != null && level() instanceof ServerLevel server && tickCount % 2 == 0)
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    position.x, position.y + 0.1, position.z, 6, 1, 0.1, 1, 0.05);
    }

    @Override public boolean hurt(DamageSource source, float amount) { return hurtPart(0, source, amount); }
    public boolean hurtPart(int part, DamageSource source, float amount) {
        if (level().isClientSide || !isAlive() || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.IN_WALL)
                || (underground() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))) return false;
        float multiplier = source.is(DamageTypeTags.IS_EXPLOSION) ? 2
                : source.is(DamageTypeTags.IS_PROJECTILE) ? 0.25F
                : part == 0 ? 1 : part == 1 ? (phaseTwo() ? 2 : 1) : 0.5F;
        if (action() == Action.STAGGER) multiplier *= 1.5F;
        boolean hit = super.hurt(source, amount * multiplier);
        if (hit && getHealth() < getMaxHealth() / 2) entityData.set(BREACHED, true);
        if (hit && source.getEntity() instanceof LivingEntity attacker && valid(attacker)) {
            setTarget(attacker);
            quarry = attacker;
        }
        return hit;
    }

    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player); boss.addPlayer(player);
    }
    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player); boss.removePlayer(player);
    }
    @Override public void remove(RemovalReason reason) {
        super.remove(reason);
        if (boss != null) boss.removeAllPlayers();
    }
    @Override protected void tickDeath() {
        // Keep vanilla removal/experience handling after the delayed body collapse has finished.
        if (deathHold++ < 18) return;
        super.tickDeath();
    }
    @Override protected ResourceLocation getDefaultLootTable() {
        return new ResourceLocation(ModMain.MOD_ID, "entities/brickrot_wall");
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("BrickrotBreached", phaseTwo());
        if (burrowing() && safeSurface != null)
            tag.put("Pos", newDoubleList(safeSurface.x, safeSurface.y, safeSurface.z));
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(BREACHED, tag.getBoolean("BrickrotBreached") || getHealth() < getMaxHealth() / 2);
        transition(Action.SCAN);
        trail.reset(position(), forward());
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "head", 2, state -> {
            String name = !isAlive() ? "head_death" : switch (action()) {
                case STAGGER -> "head_stagger";
                case EMERGE -> "head_emerge";
                case SCAN -> "head_scan";
                case CHARGE, WINDUP, DIVE, UNDERGROUND, WARNING -> "head_charge";
                case BITE -> "head_bite";
                default -> "head_idle";
            };
            return state.setAndContinue(!isAlive() ? RawAnimation.begin().thenPlayAndHold("animation.brickrot." + name)
                    : name.equals("head_bite") || name.equals("head_emerge")
                    ? RawAnimation.begin().thenPlay("animation.brickrot." + name)
                    : RawAnimation.begin().thenLoop("animation.brickrot." + name));
        }));
    }
}
