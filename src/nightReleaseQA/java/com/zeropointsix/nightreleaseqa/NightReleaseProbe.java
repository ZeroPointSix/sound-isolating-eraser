package com.zeropointsix.nightreleaseqa;

import com.zeropointsix.eraser.eternalnight.CitySavedData;
import com.zeropointsix.eraser.eternalnight.EternalNightContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Runs against the shipped JAR in an installed, SRG-named Forge server. */
@Mod("night_release_qa")
public final class NightReleaseProbe {
    private ServerLevel city;
    private BlockPos doorPos;
    private BlockPos ironPos;
    private long until;
    private boolean waiting;

    public NightReleaseProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void started(ServerStartedEvent event) {
        var server = event.getServer();
        try {
            require(FMLEnvironment.production, "must use an installed production-named runtime, not userdev");
            var names = new java.util.HashSet<String>();
            for (var method : DoorBlock.class.getDeclaredMethods()) {
                names.add(method.getName());
            }
            require(names.containsAll(java.util.Set.of("m_153165_", "m_6227_", "m_6861_"))
                    && !names.contains("setOpen"), "DoorBlock must expose all three SRG entry points");
            city = server.getLevel(EternalNightContent.LEVEL);
            require(city != null, "real city dimension loaded from shipped JAR");
            BlockPos pos = new BlockPos(4, 150, 4);
            doorPos = pos;
            city.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            installDoor(pos, Blocks.OAK_DOOR);
            var door = (DoorBlock) city.getBlockState(pos).getBlock();
            door.setOpen(null, city, city.getBlockState(pos), pos, true);
            require(city.getBlockState(pos).getValue(DoorBlock.OPEN), "control: ordinary door can open");
            CitySavedData data = CitySavedData.get(city);
            long now = city.getGameTime();
            until = now + 20;
            data.open(pos, until);
            door.setOpen(null, city, city.getBlockState(pos), pos, false);
            require(city.getBlockState(pos).getValue(DoorBlock.OPEN), "active shared exit rejects setOpen(false)");
            require(city.getBlockState(pos.above()).getValue(DoorBlock.OPEN), "upper leaf also remains open");
            door.use(city.getBlockState(pos), city, pos, null, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                            net.minecraft.core.Direction.NORTH, pos, false));
            require(city.getBlockState(pos).getValue(DoorBlock.OPEN), "active shared exit rejects wood click close");
            ironPos = pos.east(4);
            installDoor(ironPos, Blocks.IRON_DOOR);
            city.setBlock(ironPos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            require(city.getBlockState(ironPos).getValue(DoorBlock.OPEN), "control: redstone opens ordinary iron door");
            data.open(ironPos, until);
            city.setBlock(ironPos.west(), Blocks.AIR.defaultBlockState(), 3);
            require(city.getBlockState(ironPos).getValue(DoorBlock.OPEN), "active shared exit rejects redstone close");
            require(city.getBlockState(ironPos.above()).getValue(DoorBlock.OPEN), "iron upper leaf remains open");
            // Reopening cannot shorten an existing window. Wait for real server ticks.
            data.open(pos, now - 1);
            require(data.active(pos, now), "older expiry cannot shorten the active exit window");
            System.out.println("NIGHT_RELEASE_DOOR_LOCK_ACTIVE production=true SRG=m_153165_ now=" + now + " until=" + until);
            waiting = true;
        } catch (Throwable failure) {
            failed(failure);
            server.halt(false);
        }
    }

    private void tick(TickEvent.ServerTickEvent event) {
        if (!waiting || event.phase != TickEvent.Phase.END || city.getGameTime() < until) return;
        waiting = false;
        try {
            BlockPos pos = doorPos;
            var door = (DoorBlock) city.getBlockState(pos).getBlock();
            CitySavedData data = CitySavedData.get(city);
            long now = city.getGameTime();
            require(city.isLoaded(pos), "expiry fixture stays loaded in the installed server");
            data.activeExits(city, now);
            require(!data.active(pos, now), "real server time passed the exit window");
            require(!city.getBlockState(pos).getValue(DoorBlock.OPEN), "expiry closes the real lower leaf");
            require(!city.getBlockState(pos.above()).getValue(DoorBlock.OPEN), "expiry closes the real upper leaf");
            require(!city.getBlockState(ironPos).getValue(DoorBlock.OPEN), "expiry closes iron lower leaf");
            require(!city.getBlockState(ironPos.above()).getValue(DoorBlock.OPEN), "expiry closes iron upper leaf");
            door.setOpen(null, city, city.getBlockState(pos), pos, true);
            door.setOpen(null, city, city.getBlockState(pos), pos, false);
            require(!city.getBlockState(pos).getValue(DoorBlock.OPEN), "control: inactive door can close");
            city.setBlock(ironPos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            require(city.getBlockState(ironPos).getValue(DoorBlock.OPEN), "control: expired iron door can open");
            city.setBlock(ironPos.west(), Blocks.AIR.defaultBlockState(), 3);
            require(!city.getBlockState(ironPos).getValue(DoorBlock.OPEN), "control: expired iron door can close");
            System.out.println("NIGHT_RELEASE_DOOR_ENTRYPOINTS_PASS production=true setOpen=true woodUse=true ironNeighbor=true bothLeavesExpire=true");
            System.out.println("NIGHT_RELEASE_DOOR_LOCK_PASS production=true SRG=m_153165_ activeCloseBlocked=true expiredClosed=true inactiveCloseAllowed=true now=" + now + " until=" + until);
        } catch (Throwable failure) {
            failed(failure);
        } finally {
            city.getServer().halt(false);
        }
    }

    private void installDoor(BlockPos pos, net.minecraft.world.level.block.Block door) {
        city.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        city.setBlock(pos, door.defaultBlockState(), 3);
        city.setBlock(pos.above(), door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    private static void failed(Throwable failure) {
        System.err.println("NIGHT_RELEASE_DOOR_LOCK_FAIL " + failure);
        failure.printStackTrace();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
