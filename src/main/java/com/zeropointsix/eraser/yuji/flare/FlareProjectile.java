package com.zeropointsix.eraser.yuji.flare;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

public final class FlareProjectile extends ThrowableProjectile implements ItemSupplier {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(FlareProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LIT = SynchedEntityData.defineId(FlareProjectile.class, EntityDataSerializers.BOOLEAN);
    private int age;
    private long extinguishAt = -1;
    private java.util.UUID shooter;
    private FlarePlacement.LandingSearch greenLandingSearch;
    public FlareProjectile(EntityType<? extends FlareProjectile> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData() { entityData.define(COLOR, 0); entityData.define(LIT, false); }
    public int color() { return entityData.get(COLOR); }
    public void setColor(int color) { entityData.set(COLOR, Math.max(0, Math.min(2, color))); }
    public boolean lit() { return entityData.get(LIT); }
    @Override public void setOwner(net.minecraft.world.entity.Entity owner) {
        super.setOwner(owner); shooter = owner == null ? null : owner.getUUID();
    }
    @Override protected float getGravity() { return lit() ? 0 : .035F; }
    @Override public ItemStack getItem() { return new ItemStack(FlareContent.ammunition(color())); }
    @Override public void tick() {
        if (lit()) setDeltaMovement(0, -.012, 0);
        if (color() == 2 && greenLandingSearch != null) setDeltaMovement(Vec3.ZERO);
        super.tick();
        if (isRemoved()) return;
        age++;
        if (level().isClientSide) { particles(level(), position(), color()); return; }
        if (lit()) { if (level().getGameTime() >= extinguishAt) discard(); return; }
        if (color() == 0 && age >= FlareConfig.RED_DELAY.get()
                || color() == 1 && (age >= FlareConfig.YELLOW_DELAY.get() || age > 1 && getDeltaMovement().y <= 0)
                || color() == 2 && age >= FlareConfig.GREEN_DELAY.get()) activate();
    }
    @Override protected void onHit(HitResult hit) {
        if (lit()) { setDeltaMovement(Vec3.ZERO); return; }
        if (level().isClientSide) return;
        setPos(hit.getLocation());
        if (color() == 2) setDeltaMovement(Vec3.ZERO);
        else activate(hit);
    }
    public void activate() { activate(null); }
    private void activate(@javax.annotation.Nullable HitResult hit) {
        if (!(level() instanceof ServerLevel server) || isRemoved() || lit()) return;
        if (color() == 0) {
            FlareDecoy decoy = new FlareDecoy(FlareContent.DECOY.get(), server);
            if (FlarePlacement.positionDecoy(server, decoy, position(), hit)) {
                decoy.ignite();
                if (server.addFreshEntity(decoy)) decoy.taunt();
            }
            discard();
        } else if (color() == 1) {
            FlareLights.get(server).illuminate(server, blockPosition(), FlarePlacement.owner(server, shooter));
            entityData.set(LIT, true); extinguishAt = server.getGameTime() + FlareConfig.YELLOW_LIFETIME.get();
            playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 1, 1.2F);
        } else {
            var owner = FlarePlacement.owner(server, shooter);
            if (owner == null) { discard(); return; }
            if (greenLandingSearch == null) greenLandingSearch = new FlarePlacement.LandingSearch(position());
            BlockPos landing = greenLandingSearch.advance(server, owner);
            if (landing == null && !greenLandingSearch.finished()) return;
            if (landing != null) {
                FallingSupplyCrate crate = new FallingSupplyCrate(FlareContent.FALLING_CRATE.get(), server);
                crate.prepare(landing, shooter); server.addFreshEntity(crate);
            } else owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.qihai.flare.no_landing"), true);
            discard();
        }
    }
    public static void particles(Level level, Vec3 pos, int color) {
        Vector3f rgb = color == 1 ? new Vector3f(1, .9F, .25F) : color == 2 ? new Vector3f(.2F, 1, .35F) : new Vector3f(1, .15F, .1F);
        level.addParticle(new DustParticleOptions(rgb, 1.4F), pos.x, pos.y, pos.z, 0, .04, 0);
        level.addParticle(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 0, .015, 0);
        if (level.random.nextBoolean()) level.addParticle(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 0, .025, 0);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putInt("FlareColor", color()); tag.putInt("FlareAge", age);
        tag.putBoolean("FlareLit", lit()); tag.putLong("ExtinguishAt", extinguishAt);
        if (shooter != null) tag.putUUID("Shooter", shooter);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); setColor(tag.getInt("FlareColor")); age = Math.max(0, tag.getInt("FlareAge"));
        entityData.set(LIT, tag.getBoolean("FlareLit")); extinguishAt = tag.getLong("ExtinguishAt");
        shooter = tag.hasUUID("Shooter") ? tag.getUUID("Shooter") : null;
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
