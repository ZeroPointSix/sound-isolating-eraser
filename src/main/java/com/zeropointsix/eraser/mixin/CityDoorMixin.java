package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.eternalnight.CityDoorActions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoorBlock.class)
public abstract class CityDoorMixin {
    @Inject(method = "use", at = @At("RETURN"))
    private void cityUsed(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit,
                          CallbackInfoReturnable<InteractionResult> ci) { CityDoorActions.opened(level, pos, state); }
    @Inject(method = "neighborChanged", at = @At("RETURN"))
    private void cityPowered(BlockState state, Level level, BlockPos pos, Block block, BlockPos source, boolean moving,
                             CallbackInfo ci) { CityDoorActions.opened(level, pos, state); }
}
