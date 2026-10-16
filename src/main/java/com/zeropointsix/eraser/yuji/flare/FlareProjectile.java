package com.zeropointsix.eraser.yuji.flare;

import net.minecraft.core.BlockPos;
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

public final class FlareProjectile extends ThrowableProjectile implements ItemSupplier {
    private static final byte BURST_EVENT = 76;
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
        Vec3 previous = position();
        if (lit()) setDeltaMovement(0, -.012, 0);
        if (color() == 2 && greenLandingSearch != null) setDeltaMovement(Vec3.ZERO);
        super.tick();
        if (isRemoved()) return;
        age++;
        if (level().isClientSide) { FlareEffects.trail(level(), previous, position(), color()); return; }
        if (lit()) { if (level().getGameTime() >= extinguishAt) discard(); return; }
        if (age % 2 == 0 && greenLandingSearch == null && getDeltaMovement().lengthSqr() > .01
                && (color() != 2 || age < FlareConfig.GREEN_DELAY.get()) && level() instanceof ServerLevel server) {
            var player = getOwner() instanceof net.minecraft.world.entity.player.Player owner
                    ? owner : FlarePlacement.owner(server, shooter);
            FlareLights.get(server).flight(server, blockPosition(), player);
        }
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
                if (server.addFreshEntity(decoy)) { decoy.taunt(); server.broadcastEntityEvent(this, BURST_EVENT); }
            }
            discard();
        } else if (color() == 1) {
            FlareLights.get(server).illuminate(server, blockPosition(), FlarePlacement.owner(server, shooter));
            entityData.set(LIT, true); extinguishAt = server.getGameTime() + FlareConfig.YELLOW_LIFETIME.get();
            server.broadcastEntityEvent(this, BURST_EVENT);
            playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 1, 1.2F);
        } else {
            var owner = FlarePlacement.owner(server, shooter);
            if (owner == null) { discard(); return; }
            if (greenLandingSearch == null) greenLandingSearch = new FlarePlacement.LandingSearch(position());
            BlockPos landing = greenLandingSearch.advance(server, owner);
            if (landing == null && !greenLandingSearch.finished()) return;
            if (landing != null) {
                FallingSupplyCrate crate = new FallingSupplyCrate(FlareContent.FALLING_CRATE.get(), server);
                crate.prepare(landing, shooter);
                if (server.addFreshEntity(crate)) server.broadcastEntityEvent(this, BURST_EVENT);
            } else owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.qihai.flare.no_landing"), true);
            discard();
        }
    }
    public static void particles(Level level, Vec3 pos, int color) {
        FlareEffects.trail(level, pos, pos, color);
    }
    @Override public void handleEntityEvent(byte event) {
        if (event == BURST_EVENT) FlareEffects.burst(level(), position(), color());
        else super.handleEntityEvent(event);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putInt("FlareColor", color()); tag.putInt("FlareAge", age);
        tag.putBoolean("FlareLit", lit()); tag.putLong("ExtinguishAt", extinguishAt);
        if (shooter != null) tag.putUUID("Shooter", shooter);
        if (greenLandingSearch != null) tag.put("GreenLandingSearch", greenLandingSearch.save());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); setColor(tag.getInt("FlareColor")); age = Math.max(0, tag.getInt("FlareAge"));
        entityData.set(LIT, tag.getBoolean("FlareLit")); extinguishAt = tag.getLong("ExtinguishAt");
        shooter = tag.hasUUID("Shooter") ? tag.getUUID("Shooter") : null;
        greenLandingSearch = tag.contains("GreenLandingSearch", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? FlarePlacement.LandingSearch.load(tag.getCompound("GreenLandingSearch"), position()) : null;
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
