package com.zeropointsix.eraser.yuji.city;

import com.zeropointsix.eraser.yuji.YujiContent;
import com.zeropointsix.eraser.yuji.YujiKind;
import com.zeropointsix.eraser.yuji.YujiMonster;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class RainCityEvents {
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("yuji")
                .then(Commands.literal("enter").requires(s -> s.hasPermission(2)).executes(c -> RainCityEntry.request(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("leave").requires(s -> s.hasPermission(2)).executes(c -> leave(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("status").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    CompoundTag state = RainCityData.get(p.serverLevel()).players.get(p.getUUID());
                    c.getSource().sendSuccess(() -> Component.translatable("message.qihai.city.status",
                            state == null ? 0 : RainCityRules.day(p.level().getGameTime() - state.getLong("Entered")), state == null ? 0 : state.getInt("Kills"),
                            state == null ? 0 : Integer.bitCount(state.getInt("Marks"))), false); return 1;
                })));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        RainCityEntry.tick(e.getServer());
        ServerLevel city = e.getServer().getLevel(RainCityContent.LEVEL);
        if (city == null || city.players().isEmpty()) return;
        RainCityData data = RainCityData.get(city);
        List<ServerPlayer> players = city.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
        long elapsed = players.isEmpty() ? data.cityTime : 0;
        for (ServerPlayer p : players) {
            CompoundTag state = data.players.get(p.getUUID());
            if (state == null || !state.getBoolean("Active")) {
                // Operator dimension teleports still receive a durable, safe overworld return point.
                state = new CompoundTag(); state.putBoolean("Active", true); state.putLong("Entered", city.getGameTime());
                BlockPos home = e.getServer().overworld().getSharedSpawnPos();
                state.putDouble("HomeX", home.getX() + .5); state.putDouble("HomeY", home.getY()); state.putDouble("HomeZ", home.getZ() + .5);
                state.putLong("Arrival", RainCityTemplates.HOSPITAL.offset(16, 1, 16).asLong());
                data.players.put(p.getUUID(), state); data.setDirty();
            }
            elapsed = Math.max(elapsed, Math.max(0, city.getGameTime() - state.getLong("Entered")));
        }
        // ServerLevel#setDayTime/#setWeatherParameters are no-ops here (DerivedLevelData); keep the clock in saved data.
        if (data.cityTime != elapsed) { data.cityTime = elapsed; data.setDirty(); }
        if (city.getGameTime() % 20 == 0) {
            for (ServerPlayer p : city.players()) RainCityNetwork.send(p, elapsed, data.bellAt < 0 ? -1 : city.getGameTime() - data.bellAt);
            ensureScene(city, data);
        }
        if (!data.ringing()) RainCitySpawns.tick(city, data, players, elapsed);
        else {
            if (data.open(city.getGameTime())) {
                BlockPos light = new BlockPos(0, 11, 0);
                if (city.hasChunkAt(light)) {
                    var state = city.getBlockState(light);
                    if (state.is(RainCityContent.LIGHT.get()) && !state.getValue(RainCityBlocks.HolyLightBlock.LIT))
                        city.setBlock(light, state.setValue(RainCityBlocks.HolyLightBlock.LIT, true), 3);
                }
                if (city.getGameTime() % 4 == 0) city.sendParticles(RainCityContent.HOLY_LIGHT.get(), 0, 16, 0, 12, 5, 3, 5, .05);
                for (ServerPlayer p : players) if (p.getX() * p.getX() + p.getZ() * p.getZ() <= 49 && p.getY() >= 10 && p.getY() < 142
                        && city.getGameTime() >= data.players.get(p.getUUID()).getLong("ReturnRetryAt")) leave(p);
            }
        }
    }
    private static void ensureScene(ServerLevel city, RainCityData data) {
        if (!city.hasChunkAt(new BlockPos(0, 123, 0))) return;
        if (data.buddha == null) {
            RecliningBuddha buddha = RainCityContent.BUDDHA.get().create(city);
            if (buddha != null) { buddha.moveTo(0, 123, 0); if (city.addFreshEntity(buddha)) { data.buddha = buddha.getUUID(); data.setDirty(); } }
        }
        if (!data.guardiansSpawned && !data.ringing() && city.hasChunkAt(RainCityTemplates.BELL)) {
            for (int i = 0; i < 7; i++) {
                if ((data.guardianMask & (1 << i)) == 0 && spawnGuardian(city, YujiKind.YUCHI,
                        RainCityTemplates.BOTTOM_GUARDIANS.get(i), true)) data.guardianMask |= 1 << i;
            }
            for (int i = 0; i < RainCityTemplates.RITUAL_GUARDIANS.size(); i++) if ((data.guardianMask & (1 << (i + 7))) == 0
                    && spawnGuardian(city, YujiKind.XIEYIN, RainCityTemplates.RITUAL_GUARDIANS.get(i), false)) data.guardianMask |= 1 << (i + 7);
            data.guardiansSpawned = data.guardianMask == 1023; data.setDirty();
        }
    }
    private static boolean spawnGuardian(ServerLevel city, YujiKind kind, BlockPos pos, boolean white) {
        if (!city.hasChunkAt(pos)) return false;
        YujiMonster mob = YujiContent.MOBS.get(kind).get().create(city);
        if (mob == null) return false;
        mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        if (!city.noCollision(mob)) return false;
        mob.finalizeSpawn(city, city.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null, null);
        mob.getPersistentData().putBoolean("YujiGuardian", true); mob.getPersistentData().putBoolean("YujiWhiteRobe", white);
        mob.setWhiteRobe(white);
        mob.setPersistenceRequired(); return city.addFreshEntity(mob);
    }
    @SubscribeEvent public static void kills(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof YujiMonster mob) || mob.kind() == YujiKind.SHIFOTOU || !RainCityContent.isCity(mob.level())
                || !(e.getSource().getEntity() instanceof ServerPlayer player) || !RainCityContent.isCity(player.level())) return;
        if (mob.getPersistentData().getBoolean("YujiCityCredited")) return;
        RainCityData data = RainCityData.get(player.serverLevel());
        CompoundTag state = data.players.get(player.getUUID());
        if (state == null || !state.getBoolean("Active") || data.ringing()) return;
        mob.getPersistentData().putBoolean("YujiCityCredited", true);
        state.putInt("Kills", Math.min(1_000_000, state.getInt("Kills") + 1));
        int bit = 1 << mob.kind().ordinal();
        ItemStack mark = new ItemStack(RainCityContent.MARKS.get(mob.kind()).get());
        if ((state.getInt("Marks") & bit) == 0 || !player.getInventory().contains(mark)) mob.spawnAtLocation(mark);
        state.putInt("Marks", state.getInt("Marks") | bit);
        data.setDirty();
    }
    public static boolean eligible(net.minecraft.world.entity.player.Player p, CompoundTag state) {
        if (state == null || state.getInt("Kills") < 50 || (state.getInt("Marks") & 1023) != 1023) return false;
        return RainCityContent.MARKS.values().stream().allMatch(item -> p.getInventory().contains(new ItemStack(item.get())));
    }
    @SubscribeEvent public static void bell(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p) || !RainCityContent.isCity(p.level())) return;
        BlockPos pos = e.getPos();
        if (Math.abs(pos.getX()) > 7 || Math.abs(pos.getZ()) > 7 || pos.getY() < 10 || pos.getY() > 26) return;
        var clicked = p.level().getBlockState(pos);
        if (!clicked.is(Blocks.CUT_COPPER) && !clicked.is(Blocks.OXIDIZED_CUT_COPPER) && !clicked.is(Blocks.WEATHERED_COPPER)
                && !clicked.is(Blocks.EXPOSED_CUT_COPPER) && !clicked.is(Blocks.WEATHERED_CUT_COPPER) && !clicked.is(Blocks.OXIDIZED_COPPER)) return;
        RainCityData data = RainCityData.get(p.serverLevel());
        boolean ready = eligible(p, data.players.get(p.getUUID()));
        e.setCanceled(true); e.setCancellationResult(InteractionResult.SUCCESS);
        p.level().playSound(null, pos, ready ? SoundEvents.BELL_RESONATE : SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4, ready ? .5F : .25F);
        if (!ready) { p.displayClientMessage(Component.translatable("message.qihai.city.locked"), true); return; }
        if (data.ringing()) return;
        data.bellAt = p.level().getGameTime(); data.setDirty();
        for (Entity entity : p.serverLevel().getAllEntities())
            if (entity instanceof YujiMonster && entity.getPersistentData().getBoolean("YujiGuardian")) entity.discard();
    }
    @SubscribeEvent public static void spawning(EntityJoinLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !RainCityContent.isCity(level) || e.loadedFromDisk()
                || !(e.getEntity() instanceof YujiMonster mob)) return;
        if (RainCityData.get(level).ringing()) { e.setCanceled(true); return; }
        int day = RainCityRules.day(RainCityData.get(level).cityTime);
        if (!mob.getPersistentData().contains("YujiCityDay")) {
            if (mob.kind() == YujiKind.TANYU) mob.setLayers(RainCityRules.layers(day), false);
            RainCitySpawns.grow(mob, day);
        }
        if (mob.summoner() != null && level.getEntity(mob.summoner()) instanceof YujiMonster parent
                && parent.getPersistentData().getBoolean("YujiHorde")) mob.getPersistentData().putBoolean("YujiHorde", true);
    }
    @SubscribeEvent public static void damage(LivingHurtEvent e) {
        if (e.getSource().getEntity() instanceof YujiMonster mob && RainCityContent.isCity(mob.level()) && mob.getPersistentData().contains("YujiCityDay"))
            e.setAmount((float) (e.getAmount() * RainCityRules.strength(mob.getPersistentData().getInt("YujiCityDay"))));
    }
    public static boolean leave(ServerPlayer p) {
        if (!RainCityContent.isCity(p.level())) return false;
        RainCityData data = RainCityData.get(p.serverLevel());
        CompoundTag state = data.players.get(p.getUUID());
        ServerLevel home = p.server.overworld();
        BlockPos fallback = home.getSharedSpawnPos();
        double x = state == null ? fallback.getX() + .5 : state.getDouble("HomeX");
        double y = state == null ? fallback.getY() : state.getDouble("HomeY");
        double z = state == null ? fallback.getZ() + .5 : state.getDouble("HomeZ");
        Vec3 destination = RainCityPlacement.home(home, new Vec3(x, y, z), fallback);
        if (destination == null) {
            if (state != null) { state.putLong("ReturnRetryAt", p.level().getGameTime() + 20); data.setDirty(); }
            p.displayClientMessage(Component.translatable("commands.teleport.invalidPosition"), true);
            return false;
        }
        p.teleportTo(home, destination.x, destination.y, destination.z, state == null ? 0 : state.getFloat("HomeYaw"), state == null ? 0 : state.getFloat("HomePitch"));
        if (p.serverLevel() != home) return false;
        p.setDeltaMovement(0, 0, 0); p.fallDistance = 0;
        if (state != null) { state.putBoolean("Active", false); state.putInt("Horde", 0); data.setDirty(); }
        return true;
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        RainCityData data = RainCityData.get(p.serverLevel());
        CompoundTag state = data.players.get(p.getUUID());
        if (state != null && state.getBoolean("Active")) {
            if (!data.ringing()) RainCityEntry.recoverAfterDeath(p);
            else { state.putBoolean("Active", false); state.putInt("Horde", 0); data.setDirty(); }
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { RainCityEntry.cancel(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { RainCityEntry.clear(); }
    private RainCityEvents() { }
}
