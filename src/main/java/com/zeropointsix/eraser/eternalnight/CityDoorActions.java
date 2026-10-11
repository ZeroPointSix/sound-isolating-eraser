package com.zeropointsix.eraser.eternalnight;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.ScheduledTick;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Causal attribution follows an actual player action and its scheduled redstone ticks. */
public final class CityDoorActions {
    private record Action(ServerLevel level, UUID player, long until) { }
    private record TickKey(BlockPos pos, Block block) { }
    private record Pending(Action action, long triggerTick) { }
    private static final ThreadLocal<Action> CURRENT = new ThreadLocal<>();
    private static final Map<ServerLevel, Map<TickKey, Pending>> SCHEDULED = new HashMap<>();

    public static void begin(ServerPlayer player, Level level, BlockPos pos) {
        CURRENT.remove();
        if (!(level instanceof ServerLevel server) || !EternalNightContent.isCity(level)) return;
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof DoorBlock || block instanceof ButtonBlock || block instanceof LeverBlock)
            CURRENT.set(new Action(server, player.getUUID(), level.getGameTime() + 200));
    }

    public static void end() { CURRENT.remove(); }

    public static void scheduled(Object scheduler, ScheduledTick<?> tick) {
        Action action = CURRENT.get();
        if (action == null || !(tick.type() instanceof Block block) || scheduler != action.level.getBlockTicks()
                || tick.triggerTick() > action.until || action.level.getBlockTicks().hasScheduledTick(tick.pos(), block)
                || action.level.getBlockTicks().willTickThisTick(tick.pos(), block)) return;
        Map<TickKey, Pending> pending = SCHEDULED.computeIfAbsent(action.level, unused -> new HashMap<>());
        pending.values().removeIf(value -> value.action.until < action.level.getGameTime());
        pending.putIfAbsent(new TickKey(tick.pos().immutable(), block), new Pending(action, tick.triggerTick()));
    }

    public static void beginScheduled(ServerLevel level, BlockPos pos, Block block) {
        CURRENT.remove();
        Map<TickKey, Pending> pending = SCHEDULED.get(level);
        if (pending == null) return;
        TickKey key = new TickKey(pos, block);
        Pending scheduled = pending.get(key);
        if (scheduled == null || scheduled.triggerTick > level.getGameTime()) return;
        pending.remove(key);
        if (scheduled.action.until >= level.getGameTime()) CURRENT.set(scheduled.action);
    }

    public static void opened(Level level, BlockPos pos, BlockState before) {
        Action action = CURRENT.get();
        if (action == null || action.level != level || before.getValue(DoorBlock.OPEN)) return;
        BlockState after = level.getBlockState(pos);
        if (!(after.getBlock() instanceof DoorBlock) || !after.getValue(DoorBlock.OPEN)) return;
        ServerPlayer player = action.level.getServer().getPlayerList().getPlayer(action.player);
        if (player != null && player.serverLevel() == level && player.isAlive())
            NightEvents.onDoorOpened(player, NightEvents.lowerDoor(pos, after));
    }

    public static void clear() { CURRENT.remove(); SCHEDULED.clear(); }
    private CityDoorActions() { }
}
