package com.zeropointsix.eraser.mixin;

import com.zeropointsix.eraser.block.EraserWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The design spec requires mobs to see and target players through the wall,
 * while entities still collide with it. Vanilla {@code hasLineOfSight} clips
 * against the COLLISION shape, so a full-collision wall would be as opaque as
 * stone to every mob. This injects the identical ray walk but treats eraser
 * wall cells as shapeless; every other block keeps its vanilla behavior.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityWallVisibilityMixin {
    @Inject(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void eraser$seeThroughEraserWalls(Entity target, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (target.level() != self.level()) {
            cir.setReturnValue(false);
            return;
        }
        Vec3 from = new Vec3(self.getX(), self.getEyeY(), self.getZ());
        Vec3 to = new Vec3(target.getX(), target.getEyeY(), target.getZ());
        if (to.distanceTo(from) > 128.0D) {
            cir.setReturnValue(false);
            return;
        }
        BlockGetter level = self.level();
        CollisionContext collision = CollisionContext.of(self);
        BlockHitResult hit = BlockGetter.traverseBlocks(from, to, null,
                (ctx, pos) -> rayClipCell(level, collision, from, to, pos),
                ctx -> null);
        cir.setReturnValue(hit == null);
    }

    private static BlockHitResult rayClipCell(BlockGetter level, CollisionContext collision,
            Vec3 from, Vec3 to, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = EraserWallBlock.isEraserWall(state)
                ? Shapes.empty()
                : state.getCollisionShape(level, pos, collision);
        return level.clipWithInteractionOverride(from, to, pos, shape, state);
    }
}
