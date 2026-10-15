package com.zeropointsix.eraser.yuji.flare;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;

public final class FlarePlacement {
    public static boolean positionDecoy(ServerLevel level, FlareDecoy decoy, Vec3 origin, @Nullable HitResult hit) {
        Vec3 preferred = origin;
        if (hit instanceof BlockHitResult block) {
            Direction face = block.getDirection();
            double clearance = face == Direction.DOWN ? decoy.getBbHeight() + .01
                    : face == Direction.UP ? .01 : decoy.getBbWidth() / 2. + .01;
            preferred = preferred.add(Vec3.atLowerCornerOf(face.getNormal()).scale(clearance));
        }
        if (clearDecoyPosition(level, decoy, preferred, hit)) return true;
        // Fractional-height impacts need continuous clearance, not only whole-block offsets.
        Vec3 center = preferred.add(0, decoy.getBbHeight() / 2., 0);
        var free = level.findFreePosition(decoy,
                Shapes.create(AABB.ofSize(center, 2, decoy.getBbHeight() * 2., 2)), center,
                decoy.getBbWidth() + .02, decoy.getBbHeight() + .02, decoy.getBbWidth() + .02);
        if (free.isPresent() && clearDecoyPosition(level, decoy,
                free.get().add(0, -decoy.getBbHeight() / 2., 0), hit)) return true;
        // Corners, low ceilings, and entity impacts may need a nearby full-height space.
        // Do not create a suffocating decoy if the local space cannot fit it.
        for (int distance = 1; distance <= 3; distance++)
            for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                if (Math.abs(x) + Math.abs(y) + Math.abs(z) != distance) continue;
                if (clearDecoyPosition(level, decoy, preferred.add(x, y, z), hit)) return true;
            }
        return false;
    }

    private static boolean clearDecoyPosition(ServerLevel level, FlareDecoy decoy, Vec3 pos, @Nullable HitResult hit) {
        decoy.setPos(pos);
        var bounds = decoy.getBoundingBox();
        if (bounds.minY < level.getMinBuildHeight() || bounds.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(bounds)) return false;
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ)))
            if (!level.hasChunkAt(block)) return false;
        if (hit instanceof EntityHitResult entity && bounds.intersects(entity.getEntity().getBoundingBox())) return false;
        return level.noCollision(decoy, bounds);
    }

    public static boolean openSky(Level level, BlockPos origin) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (!level.hasChunkAt(origin.offset(x, 0, z))
                    || level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX() + x, origin.getZ() + z) > origin.getY() + 1)
                return false;
        return true;
    }

    @Nullable public static Player owner(ServerLevel level, @Nullable UUID id) {
        if (id == null) return null;
        Player online = level.getPlayerByUUID(id);
        return online != null ? online : FakePlayerFactory.get(level, new GameProfile(id, "[Flare]"));
    }

    public static boolean allowed(ServerLevel level, BlockPos pos, @Nullable Player player) {
        return player != null && player.mayBuild() && level.hasChunkAt(pos)
                && !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.mayInteract(player, pos);
    }

    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player) {
        return place(level, pos, state, player, true);
    }

    public static boolean canPlace(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player) {
        return place(level, pos, state, player, false);
    }

    public static boolean canRenew(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player) {
        if (!allowed(level, pos, player) || !level.getBlockState(pos).equals(state)) return false;
        // Renewal changes only the expiry; consult protection without replacing the block or notifying observers.
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return !ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)
                && level.getBlockState(pos).equals(state);
    }

    private static boolean place(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player, boolean commit) {
        if (!allowed(level, pos, player) || !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()) return false;
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        boolean capture = level.captureBlockSnapshots;
        boolean committed = false;
        level.captureBlockSnapshots = false;
        try {
            if (!level.setBlock(pos, state, Block.UPDATE_KNOWN_SHAPE)) return false;
            if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)) return false;
            if (!commit) return true;
            committed = true;
            level.markAndNotifyBlock(pos, level.getChunkAt(pos), snapshot.getReplacedBlock(), state, Block.UPDATE_ALL, 512);
            return true;
        } finally {
            // The snapshot helper's false flag still updates shapes and observers.
            // Only air can be replaced here, so restore it with the same silent flags.
            if (!committed) {
                // A provisional container must not drop contents or notify comparators.
                level.removeBlockEntity(pos);
                level.setBlock(pos, snapshot.getReplacedBlock(), Block.UPDATE_KNOWN_SHAPE);
            }
            level.captureBlockSnapshots = capture;
        }
    }

    public static boolean safeGround(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos.above(3))
                || !level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.above()).isAir() || !level.getFluidState(pos).isEmpty()) return false;
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) > pos.getY()) return false;
        BlockPos floor = pos.below();
        if (!level.getFluidState(floor).isEmpty() || level.getBlockState(floor).is(net.minecraft.tags.BlockTags.LEAVES)
                || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        // A narrow shaft has a very different surface height in an adjacent column.
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos near = pos.offset(x, 0, z);
            if (!level.hasChunkAt(near) || Math.abs(level.getHeight(Heightmap.Types.MOTION_BLOCKING, near.getX(), near.getZ()) - pos.getY()) > 2) return false;
        }
        return true;
    }

    @Nullable public static BlockPos landing(ServerLevel level, Vec3 center, Player player) {
        double min = Math.min(FlareConfig.DROP_MIN.get(), FlareConfig.DROP_MAX.get());
        double max = Math.max(FlareConfig.DROP_MIN.get(), FlareConfig.DROP_MAX.get());
        for (int i = 0; i < FlareConfig.DROP_ATTEMPTS.get(); i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(min * min + level.random.nextDouble() * (max * max - min * min));
            BlockPos column = BlockPos.containing(center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius);
            double dx = column.getX() + .5 - center.x, dz = column.getZ() + .5 - center.z;
            if (dx * dx + dz * dz < min * min || dx * dx + dz * dz > max * max || !level.hasChunkAt(column)) continue;
            BlockPos pos = new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()), column.getZ());
            if (!safeGround(level, pos) || !allowed(level, pos, player)) continue;
            // Probe the actual placement event, then restore the air before the descent.
            if (canPlace(level, pos, FlareContent.CRATE.get().defaultBlockState(), player)) {
                return pos;
            }
        }
        return null;
    }
    private FlarePlacement() { }
}
