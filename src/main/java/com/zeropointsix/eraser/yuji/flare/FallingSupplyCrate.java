package com.zeropointsix.eraser.yuji.flare;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class FallingSupplyCrate extends Entity {
    private BlockPos destination = BlockPos.ZERO;
    private UUID owner;
    private int age;
    public FallingSupplyCrate(EntityType<? extends FallingSupplyCrate> type, Level level) { super(type, level); }
    public void prepare(BlockPos destination, UUID owner) {
        this.destination = destination.immutable(); this.owner = owner;
        setPos(destination.getX() + .5, Math.min(level().getMaxBuildHeight() - 4, destination.getY() + FlareConfig.DROP_HEIGHT.get()), destination.getZ() + .5);
    }
    @Override protected void defineSynchedData() { }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) { FlareProjectile.particles(level(), position().add(0, 1, 0), 2); return; }
        if (++age > 2400) { discard(); return; }
        setDeltaMovement(0, -FlareConfig.DESCENT_SPEED.get(), 0);
        move(MoverType.SELF, getDeltaMovement());
        if (onGround() || getY() <= destination.getY()) land();
    }
    public boolean land() {
        if (!(level() instanceof ServerLevel server) || isRemoved()) return false;
        boolean placed = false;
        if (Math.abs(getY() - destination.getY()) < 1.1 && FlarePlacement.safeGround(server, destination)) {
            placed = FlarePlacement.place(server, destination, FlareContent.CRATE.get().defaultBlockState(), FlarePlacement.owner(server, owner));
            if (placed && server.getBlockEntity(destination) instanceof SupplyCrateBlockEntity crate) {
                crate.setLootTable(com.zeropointsix.eraser.yuji.YujiContent.id("chests/yuji_airdrop"), random.nextLong());
                crate.setChanged(); playSound(SoundEvents.WOOD_PLACE, 1, .75F);
            }
        }
        discard(); return placed;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Destination", destination.asLong()); tag.putInt("Age", age);
        if (owner != null) tag.putUUID("Owner", owner);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        destination = BlockPos.of(tag.getLong("Destination")); age = tag.getInt("Age");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
