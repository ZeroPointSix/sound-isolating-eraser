package com.zeropointsix.eraser.keeper;

import com.zeropointsix.eraser.registry.ModEffects;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class KeeperSessions {
    private static final Set<UUID> TRANSFERRING = new HashSet<>();

    public static boolean active(Player player) {
        return player.level().isClientSide ? player.hasEffect(ModEffects.IN_PHOTO.get())
                : player.getPersistentData().contains(KeeperData.SESSION, Tag.TAG_COMPOUND);
    }

    public static boolean current(Player player, ItemStack stack) {
        if (!active(player) || !(stack.getItem() instanceof KeeperPhotoItem) || !stack.hasTag()
                || !stack.getTag().hasUUID("PhotoId")) return false;
        var session = player.getPersistentData().getCompound(KeeperData.SESSION);
        return session.hasUUID("PhotoId") && session.getUUID("PhotoId").equals(stack.getTag().getUUID("PhotoId"));
    }

    public static boolean transferring(Player player) { return TRANSFERRING.contains(player.getUUID()); }
    private static long clock(ServerPlayer player) { return player.server.overworld().getGameTime(); }

    public static boolean cooling(ServerPlayer player) {
        long remaining = player.getPersistentData().getLong(KeeperData.COOL_UNTIL) - clock(player);
        if (remaining <= 0 && !player.getCooldowns().isOnCooldown(ModItems.KEEPER_PHOTO.get())) return false;
        KeeperData.message(player, "photo", "cooldown", Math.max(1, (remaining + 19) / 20));
        return true;
    }

    public static void restoreCooldown(ServerPlayer player) {
        long remaining = player.getPersistentData().getLong(KeeperData.COOL_UNTIL) - clock(player);
        if (remaining > 0) player.getCooldowns().addCooldown(ModItems.KEEPER_PHOTO.get(), (int) Math.min(remaining, KeeperData.COOLDOWN));
        else player.getPersistentData().remove(KeeperData.COOL_UNTIL);
    }

    private static void cooldown(ServerPlayer player) {
        player.getPersistentData().putLong(KeeperData.COOL_UNTIL, clock(player) + KeeperData.COOLDOWN);
        player.getCooldowns().addCooldown(ModItems.KEEPER_PHOTO.get(), KeeperData.COOLDOWN);
    }

    @Nullable private static ServerLevel world(ServerPlayer player, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
    }

    @Nullable public static Vec3 safeSpot(ServerLevel level, Player player, Vec3 center) {
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) return null;
        if (free(level, player, center)) return center;
        Vec3 best = null;
        double nearest = Double.MAX_VALUE;
        BlockPos base = BlockPos.containing(center);
        for (int y = -8; y <= 8; y++) for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos pos = base.offset(x, y, z);
            if (pos.getY() < level.getMinBuildHeight() + 1 || pos.getY() >= level.getMaxBuildHeight() - 1
                    || !level.getWorldBorder().isWithinBounds(pos)) continue;
            level.getChunkAt(pos);
            Vec3 point = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            double distance = point.distanceToSqr(center);
            if (distance >= nearest) continue;
            if (free(level, player, point)) {
                nearest = distance; best = point;
            }
        }
        return best;
    }

    private static boolean free(ServerLevel level, Player player, Vec3 point) {
        var box = player.getDimensions(Pose.STANDING).makeBoundingBox(point);
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        level.getChunkAt(BlockPos.containing(point));
        return level.noCollision(player, box);
    }

    private static Vec3 position(CompoundTag tag) { return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")); }

    private static CompoundTag location(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", player.level().dimension().location().toString());
        tag.putDouble("X", player.getX()); tag.putDouble("Y", player.getY()); tag.putDouble("Z", player.getZ());
        tag.putFloat("Yaw", player.getYRot()); tag.putFloat("Pitch", player.getXRot());
        return tag;
    }

    private static boolean teleport(ServerPlayer player, ServerLevel level, Vec3 point, float yaw, float pitch) {
        TRANSFERRING.add(player.getUUID());
        try {
            player.stopRiding();
            player.teleportTo(level, point.x, point.y, point.z, yaw, pitch);
            player.fallDistance = 0;
            player.setDeltaMovement(Vec3.ZERO);
            return player.level() == level && player.position().distanceToSqr(point) < 0.01;
        } finally { TRANSFERRING.remove(player.getUUID()); }
    }

    public static boolean enter(ServerPlayer player, ItemStack photo) {
        if (!player.isAlive() || player.isSpectator() || active(player) || !KeeperData.owns(photo, player)
                || KeeperData.blank(photo) || !KeeperData.valid(photo.getTag()) || cooling(player)) return false;
        CompoundTag captured = photo.getTag().copy();
        ServerLevel target = world(player, captured.getString("Dim"));
        if (target == null) { KeeperData.message(player, "photo", "lost_world"); return false; }
        Vec3 spot = safeSpot(target, player, position(captured));
        if (spot == null) { KeeperData.message(player, "photo", "changed"); return false; }
        CompoundTag session = new CompoundTag();
        session.putUUID("PhotoId", captured.getUUID("PhotoId"));
        session.put("Photo", captured);
        session.put("Return", location(player));
        session.putFloat("EntryHealth", player.getHealth());
        CompoundTag food = new CompoundTag(); player.getFoodData().addAdditionalSaveData(food); session.put("EntryFood", food);
        session.putLong("Started", clock(player));
        clearTargets(player);
        ashes(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 0.7F);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1, 0.5F);
        player.closeContainer();
        player.getPersistentData().put(KeeperData.SESSION, session);
        if (!teleport(player, target, spot, captured.getFloat("Yaw"), captured.getFloat("Pitch"))) {
            player.getPersistentData().remove(KeeperData.SESSION);
            KeeperData.message(player, "photo", "changed"); return false;
        }
        session.put("PhotoPosition", location(player));
        player.addEffect(new MobEffectInstance(ModEffects.IN_PHOTO.get(), KeeperData.DURATION, 0, false, false, false));
        if (!player.hasEffect(ModEffects.IN_PHOTO.get())) { exit(player, false); return false; }
        invisibility(player, session, KeeperData.DURATION);
        KeeperNetwork.visibility(player, true);
        ashes(player);
        clearTargets(player);
        int visits = captured.getInt("Uses") + 1;
        photo.getOrCreateTag().putInt("Uses", visits);
        photo.getOrCreateTag().putBoolean("Blank", visits >= KeeperData.VISITS);
        player.inventoryMenu.broadcastChanges();
        KeeperData.message(player, "photo", "enter");
        return true;
    }

    private static void invisibility(ServerPlayer player, CompoundTag session, int remaining) {
        // Do not overwrite existing effects or insert a hidden low-level tail beneath them.
        if (!player.hasEffect(MobEffects.INVISIBILITY) && remaining > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, remaining, 0, false, false, false));
            session.putBoolean("OwnsInvisibility", true);
        }
    }

    private static void ashes(ServerPlayer player) {
        player.serverLevel().sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.7, 0.4, 0.01);
    }

    public static void clearTargets(ServerPlayer player) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32))) {
            if (mob.getTarget() == player) mob.setTarget(null);
            if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    && mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            if (mob instanceof Warden warden) warden.clearAnger(player);
        }
    }

    public static void tick(ServerPlayer player) {
        if (!active(player)) return;
        var session = player.getPersistentData().getCompound(KeeperData.SESSION);
        session.putInt("Elapsed", Math.min(KeeperData.DURATION, session.getInt("Elapsed") + 1));
        if (!player.isAlive()) { died(player); return; }
        if (!player.hasEffect(ModEffects.IN_PHOTO.get())) { exit(player); return; }
        invisibility(player, session, player.getEffect(ModEffects.IN_PHOTO.get()).getDuration());
        if (player.getY() < player.level().getMinBuildHeight() - 10) {
            var pos = player.getPersistentData().getCompound(KeeperData.SESSION).getCompound("PhotoPosition");
            ServerLevel target = world(player, pos.getString("Dim"));
            Vec3 safe = target == null ? null : safeSpot(target, player, position(pos));
            if (safe == null) exit(player); else teleport(player, target, safe, pos.getFloat("Yaw"), pos.getFloat("Pitch"));
        }
        player.fallDistance = 0;
    }

    public static void exit(ServerPlayer player) { exit(player, true); }

    private record Destination(ServerLevel level, Vec3 point) { }

    @Nullable private static Destination destination(ServerLevel level, ServerPlayer player, Vec3 point) {
        Vec3 safe = safeSpot(level, player, point);
        return safe == null ? null : new Destination(level, safe);
    }

    @Nullable private static Destination returnDestination(ServerPlayer player, CompoundTag back) {
        ServerLevel original = world(player, back.getString("Dim"));
        Destination result = original == null ? null : destination(original, player, position(back));
        if (result != null) return result;
        var respawnWorld = player.server.getLevel(player.getRespawnDimension());
        var spawn = player.getRespawnPosition();
        var respawn = respawnWorld != null && spawn != null
                ? Player.findRespawnPositionAndUseSpawnBlock(respawnWorld, spawn, player.getRespawnAngle(), player.isRespawnForced(), true)
                : java.util.Optional.<Vec3>empty();
        if (respawn.isPresent()) result = destination(respawnWorld, player, respawn.get());
        if (result != null) return result;
        ServerLevel overworld = player.server.overworld();
        Vec3 shared = Vec3.atBottomCenterOf(overworld.getSharedSpawnPos());
        result = destination(overworld, player, shared);
        if (result != null) return result;
        // A built-over spawn may need a higher escape point; never remove protection inside a block.
        int firstY = Mth.clamp(BlockPos.containing(shared).getY(), overworld.getMinBuildHeight(), overworld.getMaxBuildHeight() - 2);
        for (int y = firstY; y < overworld.getMaxBuildHeight() - 1; y++) {
            Vec3 point = new Vec3(shared.x, y, shared.z);
            if (free(overworld, player, point)) return new Destination(overworld, point);
        }
        return null;
    }

    private static void exit(ServerPlayer player, boolean applyCooldown) {
        if (!active(player)) return;
        CompoundTag session = player.getPersistentData().getCompound(KeeperData.SESSION).copy();
        if (!player.isAlive()) { died(player); return; }
        CompoundTag back = session.getCompound("Return");
        Destination target = returnDestination(player, back);
        if (target == null) return;
        // Keep recovery data until teleport succeeds, including logout/login retries.
        if (!teleport(player, target.level(), target.point(), back.getFloat("Yaw"), back.getFloat("Pitch"))) return;
        cleanup(player, session);
        player.setHealth(Math.min(player.getMaxHealth(), session.getFloat("EntryHealth")));
        player.getFoodData().readAdditionalSaveData(session.getCompound("EntryFood"));
        if (applyCooldown) cooldown(player);
        ashes(player);
        KeeperData.message(player, "photo", "return");
    }

    public static void tear(ServerPlayer player, ItemStack stack) {
        if (!player.isAlive() || !current(player, stack)) return;
        var session = player.getPersistentData().getCompound(KeeperData.SESSION).copy();
        var photo = session.getCompound("Photo");
        if (!KeeperData.valid(photo)) { exit(player); return; }
        cleanup(player, session);
        player.setHealth(Math.min(player.getMaxHealth(), photo.getFloat("Health")));
        int food = Mth.clamp(photo.getInt("Food"), 0, 20);
        player.getFoodData().setFoodLevel(food);
        player.getFoodData().setSaturation(Mth.clamp(photo.getFloat("Saturation"), 0, food));
        player.getFoodData().setExhaustion(0);
        stack.shrink(1);
        player.serverLevel().sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)),
                player.getX(), player.getY() + 1, player.getZ(), 25, 0.3, 0.4, 0.3, 0.05);
        for (int i = 0; i < 2; i++) player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 0.5F);
        cooldown(player);
        player.inventoryMenu.broadcastChanges();
        KeeperData.message(player, "photo", "tear");
    }

    private static void cleanup(ServerPlayer player, CompoundTag session) {
        player.getPersistentData().remove(KeeperData.SESSION);
        player.removeEffect(ModEffects.IN_PHOTO.get());
        // Player effects pause offline; world time must not age saved effects on recovery.
        int elapsed = Mth.clamp(session.getInt("Elapsed"), 0, KeeperData.DURATION);
        var current = player.getEffect(MobEffects.INVISIBILITY);
        if (current != null && session.getBoolean("OwnsInvisibility")) {
            var cleaned = withoutOwnInvisibility(current.save(new CompoundTag()), KeeperData.DURATION - elapsed);
            player.removeEffect(MobEffects.INVISIBILITY);
            if (cleaned != null) {
                var restored = MobEffectInstance.load(cleaned);
                if (restored != null) player.addEffect(restored);
            }
        }
        KeeperNetwork.visibility(player, false);
        player.fallDistance = 0;
    }

    @Nullable private static CompoundTag withoutOwnInvisibility(CompoundTag tag, int remaining) {
        CompoundTag hidden = tag.contains("HiddenEffect") ? withoutOwnInvisibility(tag.getCompound("HiddenEffect"), remaining) : null;
        if (tag.getByte("Amplifier") == 0 && !tag.getBoolean("ShowParticles") && !tag.getBoolean("ShowIcon")
                && tag.getInt("Duration") >= 0 && tag.getInt("Duration") <= remaining + 1) return hidden;
        if (hidden == null) tag.remove("HiddenEffect"); else tag.put("HiddenEffect", hidden);
        return tag;
    }

    public static void died(ServerPlayer player) {
        if (active(player)) cleanup(player, player.getPersistentData().getCompound(KeeperData.SESSION).copy());
    }

    public static void login(ServerPlayer player) {
        if (active(player)) exit(player);
        else player.removeEffect(ModEffects.IN_PHOTO.get());
        restoreCooldown(player);
    }

    private KeeperSessions() { }
}
