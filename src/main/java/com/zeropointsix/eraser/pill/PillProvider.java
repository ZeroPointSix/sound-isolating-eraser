package com.zeropointsix.eraser.pill;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public final class PillProvider implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<PillState> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });
    private final PillState state = new PillState();
    private LazyOptional<PillState> optional = LazyOptional.of(() -> state);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        // Player.reviveCaps() revives the dispatcher, not its invalidated optionals.
        if (capability == CAPABILITY && !optional.isPresent()) optional = LazyOptional.of(() -> state);
        return CAPABILITY.orEmpty(capability, optional);
    }

    @Override
    public CompoundTag serializeNBT() { return state.save(); }

    @Override
    public void deserializeNBT(CompoundTag tag) { state.load(tag); }

    public void invalidate() { optional.invalidate(); }
}
