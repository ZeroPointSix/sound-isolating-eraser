package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.eternalnight.CityDoorActions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DoorBlock.class)
public abstract class CityDoorMixin {
    @Redirect(method = {"use", "neighborChanged"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean cityChanged(Level level, BlockPos pos, BlockState state, int flags) {
        // DoorBlock.use overwrites its state parameter, so RETURN cannot observe the closed state.
        BlockState before = level.getBlockState(pos);
        boolean changed = level.setBlock(pos, state, flags);
        if (changed) CityDoorActions.opened(level, pos, before);
        return changed;
    }
}
