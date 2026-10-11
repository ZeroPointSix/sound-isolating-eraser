package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.eternalnight.CityDoorActions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

// Dual MCP+SRG names with remap=false; see CityBlockTickMixin and CityDoorMixin.
@Mixin(ServerPlayerGameMode.class)
public abstract class CityPlayerInteractionMixin {
    @Group(name = "cityUse", min = 1)
    @Redirect(method = "useItemOn", remap = false, require = 0, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/block/state/BlockState;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult cityUse(BlockState state, Level level, Player player, InteractionHand hand, BlockHitResult hit) {
        return observe(state, level, player, hand, hit);
    }

    @Group(name = "cityUse", min = 1)
    @Redirect(method = "m_7179_", remap = false, require = 0, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/block/state/BlockState;m_60664_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult cityUseSrg(BlockState state, Level level, Player player, InteractionHand hand, BlockHitResult hit) {
        return observe(state, level, player, hand, hit);
    }

    private static InteractionResult observe(BlockState state, Level level, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) CityDoorActions.begin(serverPlayer, level, hit.getBlockPos());
        try {
            return state.use(level, player, hand, hit);
        } finally {
            CityDoorActions.end();
        }
    }
}
