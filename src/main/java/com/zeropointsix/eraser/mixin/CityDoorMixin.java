package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.eternalnight.CityDoorActions;
import com.zeropointsix.eraser.eternalnight.CitySavedData;
import com.zeropointsix.eraser.eternalnight.NightEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

// Dual MCP+SRG names with remap=false; see CityBlockTickMixin. Exactly one handler of the
// group matches per environment, and @Group(min = 2) still fails loudly if neither does.
@Mixin(DoorBlock.class)
public abstract class CityDoorMixin {
    @Group(name = "citySetBlock", min = 2)
    @Redirect(method = {"use", "neighborChanged"}, remap = false, require = 0, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean cityChanged(Level level, BlockPos pos, BlockState state, int flags) {
        return observe(level, pos, state, flags);
    }

    @Group(name = "citySetBlock", min = 2)
    @Redirect(method = {"m_6227_", "m_6861_"}, remap = false, require = 0, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/Level;m_7731_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean cityChangedSrg(Level level, BlockPos pos, BlockState state, int flags) {
        return observe(level, pos, state, flags);
    }

    private static boolean observe(Level level, BlockPos pos, BlockState state, int flags) {
        // DoorBlock.use overwrites its state parameter, so RETURN cannot observe the closed state.
        BlockState before = level.getBlockState(pos);
        // An active shared exit must stay open for its whole window: block every close
        // (player click, button/lever re-press, redstone) until the exit expires.
        if (before.getBlock() instanceof DoorBlock && before.getValue(DoorBlock.OPEN)
                && !state.getValue(DoorBlock.OPEN) && level instanceof ServerLevel server
                && CitySavedData.get(server).active(NightEvents.lowerDoor(pos, before), server.getGameTime()))
            return false;
        boolean changed = level.setBlock(pos, state, flags);
        if (changed) CityDoorActions.opened(level, pos, before);
        return changed;
    }
}
