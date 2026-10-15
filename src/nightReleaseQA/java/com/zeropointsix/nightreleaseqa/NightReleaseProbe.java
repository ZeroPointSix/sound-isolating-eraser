package com.zeropointsix.nightreleaseqa;

import com.zeropointsix.eraser.eternalnight.CitySavedData;
import com.zeropointsix.eraser.eternalnight.EternalNightContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Runs against the shipped JAR in an installed, SRG-named Forge server. */
@Mod("night_release_qa")
public final class NightReleaseProbe {
    public NightReleaseProbe() { MinecraftForge.EVENT_BUS.addListener(this::started); }

    private void started(ServerStartedEvent event) {
        var server = event.getServer();
        try {
            require(FMLEnvironment.production, "must use an installed production-named runtime, not userdev");
            boolean srg = false, mcp = false;
            for (var method : DoorBlock.class.getDeclaredMethods()) {
                srg |= method.getName().equals("m_153165_");
                mcp |= method.getName().equals("setOpen");
            }
            require(srg && !mcp, "DoorBlock must expose SRG m_153165_, not MCP setOpen");
            ServerLevel city = server.getLevel(EternalNightContent.LEVEL);
            require(city != null, "real city dimension loaded from shipped JAR");
            BlockPos pos = new BlockPos(4, 150, 4);
            city.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            city.setBlock(pos, Blocks.OAK_DOOR.defaultBlockState(), 3);
            city.setBlock(pos.above(), Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
            var door = (DoorBlock) city.getBlockState(pos).getBlock();
            door.setOpen(null, city, city.getBlockState(pos), pos, true);
            require(city.getBlockState(pos).getValue(DoorBlock.OPEN), "control: ordinary door can open");
            CitySavedData data = CitySavedData.get(city);
            long now = city.getGameTime();
            data.open(pos, now + 100);
            door.setOpen(null, city, city.getBlockState(pos), pos, false);
            require(city.getBlockState(pos).getValue(DoorBlock.OPEN), "active shared exit rejects setOpen(false)");
            require(city.getBlockState(pos.above()).getValue(DoorBlock.OPEN), "upper leaf also remains open");
            data.open(pos, now - 1);
            data.activeExits(city, now);
            require(!city.getBlockState(pos).getValue(DoorBlock.OPEN), "expiry closes the real lower leaf");
            require(!city.getBlockState(pos.above()).getValue(DoorBlock.OPEN), "expiry closes the real upper leaf");
            door.setOpen(null, city, city.getBlockState(pos), pos, true);
            door.setOpen(null, city, city.getBlockState(pos), pos, false);
            require(!city.getBlockState(pos).getValue(DoorBlock.OPEN), "control: inactive door can close");
            System.out.println("NIGHT_RELEASE_DOOR_LOCK_PASS production=true SRG=m_153165_ activeCloseBlocked=true expiredClosed=true inactiveCloseAllowed=true");
        } catch (Throwable failure) {
            System.err.println("NIGHT_RELEASE_DOOR_LOCK_FAIL " + failure);
            failure.printStackTrace();
        } finally {
            server.halt(false);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
