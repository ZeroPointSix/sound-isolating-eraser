package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import com.zeropointsix.eraser.eraser.EraserAggro;
import com.zeropointsix.eraser.registry.ModBlocks;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.suxia.SuxiaEntity;
import com.zeropointsix.eraser.suxia.SuxiaEvents;
import com.zeropointsix.eraser.suxia.SuxiaMotion;
import com.zeropointsix.eraser.suxia.SuxiaPocketWatchItem;
import com.zeropointsix.eraser.suxia.SuxiaSummonEntity;
import com.zeropointsix.eraser.suxia.SuxiaSummons;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("suxia_tests")
@PrefixGameTestTemplate(false)
public final class SuxiaGameTests {
    private static final String BATCH = "suxia";
    private static final List<Fixture> FIXTURES = new ArrayList<>();

    /** Live Forge world, real players and vanilla listeners on in-process Netty channels. */
    private static final class Fixture {
        final ServerLevel level;
        final BlockPos center;
        final List<Entity> entities = new ArrayList<>();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final List<ChunkPos> tickets = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        Fixture(GameTestHelper h) {
            level = h.getLevel();
            int slot = FIXTURES.size();
            // Isolated arenas inside the already-generated spawn area avoid expensive remote chunk generation.
            center = new BlockPos(-104 + slot % 5 * 48, 90, -88 + slot / 5 * 48);
            FIXTURES.add(this);
            ChunkPos chunk = new ChunkPos(center);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                var c = new ChunkPos(chunk.x + x, chunk.z + z);
                level.getChunkSource().addRegionTicket(TicketType.PLAYER, c, 2, c);
                level.getChunk(c.x, c.z);
                tickets.add(c);
            }
            for (int x = -17; x <= 17; x++) for (int z = -17; z <= 17; z++) {
                set(center.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 4; y++) set(center.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            // This is the disposable QA world; a prior crashed run may have left arena entities behind.
            level.getEntities(null, new net.minecraft.world.phys.AABB(center).inflate(18)).forEach(Entity::discard);
        }
        void set(BlockPos p, BlockState state) {
            blocks.putIfAbsent(p.immutable(), level.getBlockState(p));
            level.setBlock(p, state, 3);
        }
        <T extends Entity> T add(T entity, int x, int z) {
            entity.setPos(Vec3.atBottomCenterOf(center.offset(x, 0, z)));
            level.addFreshEntity(entity);
            entities.add(entity);
            return entity;
        }
        ServerPlayer player(int x, int z) {
            var profile = new GameProfile(UUID.randomUUID(), "SuxiaTest");
            var p = new ServerPlayer(level.getServer(), level, profile);
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channels.add(new EmbeddedChannel(connection));
            connection.setProtocol(ConnectionProtocol.PLAY);
            p.connection = new ServerGamePacketListenerImpl(level.getServer(), connection, p);
            p.setGameMode(GameType.SURVIVAL);
            p.setPos(Vec3.atBottomCenterOf(center.offset(x, 0, z)));
            level.addNewPlayer(p); entities.add(p);
            return p;
        }
        SuxiaEntity hostile(int x, int z) { return add(ModEntities.SUXIA.get().create(level), x, z); }
        ItemStack watch(ServerPlayer p) {
            var stack = new ItemStack(ModItems.SUXIA_POCKET_WATCH.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            return stack;
        }
        InteractionResult use(ServerPlayer p) {
            return p.gameMode.useItem(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND);
        }
        SuxiaSummonEntity summon(ServerPlayer p) {
            use(p);
            var entry = ledger().active(p.getUUID());
            if (entry == null) throw new IllegalStateException("Watch did not summon");
            var entity = SuxiaPocketWatchItem.resolve(level.getServer(), entry);
            if (entity == null) throw new IllegalStateException("Summon not in world");
            entities.add(entity);
            return entity;
        }
        SuxiaSummons ledger() { return SuxiaSummons.get(level.getServer()); }
        long now() { return SuxiaMotion.clock(level); }
        void close() {
            entities.stream().filter(e -> e instanceof ServerPlayer).forEach(e -> SuxiaPocketWatchItem.cancelOwner((ServerPlayer) e));
            entities.forEach(Entity::discard);
            blocks.forEach((p, state) -> level.setBlock(p, state, 3));
            tickets.forEach(c -> level.getChunkSource().removeRegionTicket(TicketType.PLAYER, c, 2, c));
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }

    @AfterBatch(batch = BATCH) public static void cleanup(ServerLevel level) {
        FIXTURES.forEach(Fixture::close);
        FIXTURES.clear();
    }

    private static void near(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) < 0.0001, what + ": " + actual);
    }
    private static CompoundTag state(SuxiaEntity entity) {
        var tag = new CompoundTag(); entity.addAdditionalSaveData(tag); return tag;
    }

    private static void ready(GameTestHelper h, Consumer<Fixture> action) {
        var f = new Fixture(h);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(f.level.isPositionEntityTicking(f.center),
                "fixture chunk is entity-ticking"))
                .thenIdle(10).thenExecute(() -> action.accept(f));
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void shared_stats_pose_and_immunities(GameTestHelper h) {
        ready(h, f -> {
            var hostile = f.hostile(0, 0);
            var summon = ModEntities.SUXIA_SUMMON.get().create(f.level);
            for (Mob mob : List.of(hostile, summon)) {
                near(h, mob.getMaxHealth(), 100, "health");
                near(h, mob.getAttributeValue(Attributes.ATTACK_DAMAGE), 15, "base attack");
                near(h, mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 1, "knockback resistance");
                near(h, mob.getAttributeValue(Attributes.FOLLOW_RANGE), 32, "follow range");
                h.assertTrue(mob.canBreatheUnderwater() && !mob.causeFallDamage(100, 1, mob.damageSources().fall()), "fall and water immunity");
            }
            near(h, hostile.getDimensions(Pose.STANDING).height, 2.9, "standing height");
            for (int i = 0; i < 10; i++) hostile.setCrawling(true);
            near(h, hostile.getBbWidth(), .9, "crawl width");
            near(h, hostile.getBbHeight(), .95, "crawl height");
            near(h, hostile.getAttributeValue(Attributes.MOVEMENT_SPEED), .35, "crawl speed does not stack");
            hostile.setPlayingDead(true);
            near(h, hostile.getBbWidth(), 1.2, "dead width");
            near(h, hostile.getBbHeight(), .6, "dead height");
            near(h, hostile.getAttributeValue(Attributes.MOVEMENT_SPEED), .25, "sleep resets speed");
            h.assertTrue(!summon.shouldDropExperience(), "summon has no XP");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void watch_bind_summon_recall(GameTestHelper h) {
        ready(h, f -> {
            var p = f.player(0, 0); var watch = f.watch(p);
            var summon = f.summon(p); var entry = f.ledger().active(p.getUUID());
            h.assertTrue(watch.getMaxStackSize() == 1 && watch.getRarity() == Rarity.EPIC && watch.getItem().isFireResistant(), "precious watch properties");
            h.assertTrue(watch.getTag().getUUID("Owner").equals(p.getUUID()) && watch.getTag().getBoolean("FirstUseShown"), "bind and first-use narrative persisted");
            h.assertTrue(summon.getOwner() == p && entry.until == f.now() + 1200, "real owner and duration");
            h.assertTrue(watch.isBarVisible() && watch.getBarWidth() == 13 && watch.getBarColor() == 0xA8141C, "remaining duration bar");
            f.use(p);
            h.assertTrue(summon.isRemoved() && !entry.active() && entry.cooldown == f.now() + 3600, "recall starts normal cooldown");
            h.assertTrue(!watch.isBarVisible() && !watch.getTag().getBoolean("Open")
                    && watch.getTag().getInt("CooldownRemaining") == 3600
                    && !p.getCooldowns().isOnCooldown(watch.getItem()), "recall closes model with stack-only cooldown");
            h.assertTrue(f.use(p) == InteractionResult.FAIL && f.ledger().active(p.getUUID()) == null, "cooldown rejects reuse");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void wrong_owner_and_duplicate_watch_rejected(GameTestHelper h) {
        ready(h, f -> {
            var p = f.player(0, 0); var watch = f.watch(p); var summon = f.summon(p);
            var other = f.player(10, 0); other.setItemInHand(InteractionHand.MAIN_HAND, watch.copy());
            var before = other.getMainHandItem().copy();
            h.assertTrue(f.use(other) == InteractionResult.FAIL && ItemStack.matches(before, other.getMainHandItem()), "foreign owner cannot mutate watch");
            var second = f.watch(p);
            h.assertTrue(f.use(p) == InteractionResult.FAIL, "second watch cannot duplicate active summon");
            h.assertTrue(f.ledger().active(p.getUUID()).summon.equals(summon.getUUID()), "original remains active");
            p.setItemInHand(InteractionHand.MAIN_HAND, watch); f.use(p);
            p.setItemInHand(InteractionHand.MAIN_HAND, second);
            var secondSummon = f.summon(p);
            p.getInventory().setItem(1, watch);
            SuxiaPocketWatchItem.syncInventory(p);
            h.assertTrue(!p.getCooldowns().isOnCooldown(second.getItem()), "another watch's cooldown must not prevent active recall");
            f.use(p);
            h.assertTrue(secondSummon.isRemoved(), "second summon recalled");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void no_space_has_no_cooldown(GameTestHelper h) {
        ready(h, f -> {
            var p = f.player(0, 0); var watch = f.watch(p);
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) for (int y = 0; y <= 3; y++) {
                f.set(f.center.offset(x, y, z), Blocks.STONE.defaultBlockState());
            }
            h.assertTrue(f.use(p) == InteractionResult.FAIL, "solid spawn area rejected");
            var entry = f.ledger().find(watch.getTag().getUUID("WatchUUID"));
            h.assertTrue(!entry.active() && entry.cooldown == 0 && !p.getCooldowns().isOnCooldown(watch.getItem()), "failed placement costs no cooldown");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void low_ceiling_spawn_uses_crawl(GameTestHelper h) {
        ready(h, f -> {
            var mob = ModEntities.SUXIA_SUMMON.get().create(f.level);
            f.set(f.center.above(), Blocks.STONE.defaultBlockState());
            h.assertTrue(SuxiaMotion.place(mob, Vec3.atBottomCenterOf(f.center)) && mob.isCrawling(), "one-block gap accepts crawl");
            f.set(f.center, Blocks.WATER.defaultBlockState());
            h.assertTrue(!SuxiaMotion.place(mob, Vec3.atBottomCenterOf(f.center)), "water is not a safe spawn");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void expiry_and_ledger_round_trip(GameTestHelper h) {
        ready(h, f -> {
            var p = f.player(0, 0); var watch = f.watch(p); var summon = f.summon(p);
            var entry = f.ledger().active(p.getUUID());
            var reloaded = SuxiaSummons.load(f.ledger().save(new CompoundTag()));
            h.assertTrue(reloaded.find(entry.watch).summon.equals(summon.getUUID()) && reloaded.find(entry.watch).until == entry.until, "active ledger survives save/load");
            entry.until = f.now(); summon.tick();
            h.assertTrue(summon.isRemoved() && !entry.active() && entry.cooldown == f.now() + 3600, "expiry at exact boundary");
            watch.getTag().putInt("CooldownRemaining", 0);
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(p));
            h.assertTrue(watch.getTag().getInt("CooldownRemaining") == 3600
                    && !p.getCooldowns().isOnCooldown(watch.getItem()), "login restores per-watch cooldown without blocking other watches");
            reloaded = SuxiaSummons.load(f.ledger().save(new CompoundTag()));
            h.assertTrue(reloaded.find(entry.watch).cooldown == entry.cooldown && !reloaded.find(entry.watch).active(), "closed ledger survives save/load");
            f.watch(p);
            var missing = f.summon(p); var stale = f.ledger().active(p.getUUID());
            missing.discard(); stale.until = f.now() + SuxiaSummons.DURATION - 40;
            SuxiaPocketWatchItem.syncInventory(p);
            h.assertTrue(!stale.active() && stale.cooldown == f.now() + 3600, "missing entity reconciled after load grace");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void death_backlash_survives_dropped_watch(GameTestHelper h) {
        ready(h, f -> {
            var p = f.player(0, 0); var watch = f.watch(p); var summon = f.summon(p);
            var entry = f.ledger().active(p.getUUID()); p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            summon.hurt(summon.damageSources().genericKill(), 1000);
            h.assertTrue(!summon.isAlive() && !entry.active() && entry.cooldown == f.now() + 12000, "death sets ten-minute world-owned cooldown");
            h.assertTrue(p.hasEffect(MobEffects.CONFUSION) && p.getEffect(MobEffects.CONFUSION).getDuration() == 100, "five-second nausea");
            h.assertTrue(p.hasEffect(MobEffects.WEAKNESS) && p.getEffect(MobEffects.WEAKNESS).getDuration() == 200, "ten-second weakness");
            p.setItemInHand(InteractionHand.MAIN_HAND, watch); SuxiaPocketWatchItem.syncInventory(p);
            h.assertTrue(watch.getTag().getInt("CooldownRemaining") == 12000 && !watch.getTag().getBoolean("Open"), "returning watch receives authoritative death cooldown");
            long cooldown = entry.cooldown; summon.finish(false);
            h.assertTrue(entry.cooldown == cooldown, "repeated cleanup cannot shorten death cooldown");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void logout_and_dimension_remove_summon(GameTestHelper h) {
        ready(h, f -> {
            var p = f.player(0, 0); f.watch(p); var summon = f.summon(p);
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
            h.assertTrue(summon.isRemoved() && f.ledger().active(p.getUUID()) == null, "logout removes active summon");
            f.watch(p); summon = f.summon(p);
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, ServerLevel.OVERWORLD, ServerLevel.NETHER));
            h.assertTrue(summon.isRemoved() && f.ledger().active(p.getUUID()) == null, "dimension change removes active summon");
            f.watch(p); summon = f.summon(p);
            p.setPos(Vec3.atBottomCenterOf(f.center.east(70)));
            summon.tick();
            h.assertTrue(summon.isRemoved() && f.ledger().active(p.getUUID()) == null, "beyond 64 blocks without a supported teleport destination ends summon");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void summon_safety_filters(GameTestHelper h) {
        ready(h, f -> {
            var p = f.player(0, 0); f.watch(p); var summon = f.summon(p);
            Wolf pet = f.add(EntityType.WOLF.create(f.level), 4, 4); pet.setOwnerUUID(p.getUUID()); pet.setTame(true);
            var villager = f.add(EntityType.VILLAGER.create(f.level), 5, 4);
            var golem = f.add(EntityType.IRON_GOLEM.create(f.level), 6, 4);
            var zombie = f.add(EntityType.ZOMBIE.create(f.level), 7, 4);
            for (var safe : List.of(p, pet, villager, golem, summon)) {
                h.assertTrue(!summon.wantsToAttack(safe, p) && !summon.doHurtTarget(safe), "friendly entity protected: " + safe.getType());
            }
            h.assertTrue(summon.wantsToAttack(zombie, p), "hostile remains attackable");
            var other = f.player(12, 0); other.setGameMode(GameType.CREATIVE);
            h.assertTrue(!summon.wantsToAttack(other, p), "creative player protected");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 160)
    public static void summon_acquires_mob_targeting_owner(GameTestHelper h) {
        ready(h, f -> {
            var p = f.player(0, 0); f.watch(p); var summon = f.summon(p);
            var zombie = f.add(EntityType.ZOMBIE.create(f.level), 7, 0); zombie.setNoAi(true); zombie.setTarget(p);
            h.succeedWhen(() -> h.assertTrue(summon.getTarget() == zombie || !zombie.isAlive(), "active attacker acquired by live goal"));
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void scent_strict_health_and_time_boundaries(GameTestHelper h) {
        ready(h, f -> {
            var mob = f.hostile(0, 0); var prey = f.add(EntityType.VILLAGER.create(f.level), 8, 0);
            prey.setNoAi(true);
            h.assertTrue(!mob.smells(prey), "undamaged full-health prey is silent");
            prey.setHealth(10); h.assertTrue(!mob.smells(prey), "exactly half health is not blood scent");
            prey.setHealth(9); h.assertTrue(mob.smells(prey), "below half health is blood scent");
            prey.setHealth(20); prey.getPersistentData().putLong(SuxiaEvents.LAST_HURT, f.now() - 199);
            h.assertTrue(mob.smells(prey), "recent damage within 200 ticks");
            prey.getPersistentData().putLong(SuxiaEvents.LAST_HURT, f.now() - 200);
            h.assertTrue(!mob.smells(prey), "damage scent expires exactly at 200 ticks");
            prey.getPersistentData().remove(SuxiaEvents.LAST_HURT); prey.hurt(prey.damageSources().generic(), 1);
            h.assertTrue(prey.getPersistentData().getLong(SuxiaEvents.LAST_HURT) == f.now() && mob.smells(prey), "real damage event records scent");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void hearing_filters_and_projectile_lure(GameTestHelper h) {
        ready(h, f -> {
            var mob = f.hostile(0, 0); var p = f.player(8, 0);
            var hearing = mob.getVibrationUser();
            h.assertTrue(hearing.getListenerRadius() == 16 && hearing.canReceiveVibration(f.level, p.blockPosition(), GameEvent.STEP, GameEvent.Context.of(p)), "normal step in hearing radius");
            p.setGameMode(GameType.CREATIVE);
            h.assertTrue(!hearing.canReceiveVibration(f.level, p.blockPosition(), GameEvent.STEP, GameEvent.Context.of(p)), "creative vibration ignored");
            p.setGameMode(GameType.SURVIVAL); mob.setPlayingDead(true);
            h.assertTrue(!hearing.canReceiveVibration(f.level, p.blockPosition(), GameEvent.STEP, GameEvent.Context.of(p)), "sleep hearing limited to three blocks");
            mob.setPlayingDead(false);
            var snowball = f.add(EntityType.SNOWBALL.create(f.level), 5, 0); snowball.setOwner(p);
            hearing.onReceiveVibration(f.level, snowball.blockPosition(), GameEvent.PROJECTILE_LAND, snowball, p, 5);
            h.assertTrue(mob.getTarget() == null && state(mob).contains("SuxiaSound") && BlockPos.of(state(mob).getLong("SuxiaSound")).equals(snowball.blockPosition()), "projectile investigates landing location, not shooter");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 120)
    public static void real_vibration_listener_acquires_prey(GameTestHelper h) {
        ready(h, f -> {
            var mob = f.hostile(0, 0); var p = f.player(8, 0);
            h.runAfterDelay(25, () -> f.level.gameEvent(p, GameEvent.STEP, p.position()));
            h.succeedWhen(() -> h.assertTrue(mob.getTarget() == p, "registered dynamic listener receives live step"));
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 140)
    public static void sneaking_and_wool_block_real_vibrations(GameTestHelper h) {
        ready(h, f -> {
            var mob = f.hostile(0, 0); var p = f.player(8, 0);
            p.setShiftKeyDown(true);
            h.runAfterDelay(25, () -> f.level.gameEvent(p, GameEvent.STEP, p.position()));
            h.runAfterDelay(50, () -> {
                h.assertTrue(mob.getTarget() == null, "sneaking step ignored");
                p.setShiftKeyDown(false);
                for (int y = 0; y <= 3; y++) for (int z = -8; z <= 8; z++) f.set(f.center.offset(4, y, z), Blocks.WHITE_WOOL.defaultBlockState());
                f.level.gameEvent(p, GameEvent.STEP, p.position());
            });
            h.runAfterDelay(85, () -> { h.assertTrue(mob.getTarget() == null, "wool occludes live step"); h.succeed(); });
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void eraser_blocks_sound_but_not_blood(GameTestHelper h) {
        ready(h, f -> {
            var mob = f.hostile(0, 0); var prey = f.add(EntityType.VILLAGER.create(f.level), 8, 0);
            prey.setNoAi(true); prey.setHealth(8);
            for (int y = 0; y <= 4; y++) for (int z = -2; z <= 2; z++) f.set(f.center.offset(4, y, z), ModBlocks.ERASER_BARRIER.get().defaultBlockState());
            h.assertTrue(EraserAggro.soundBlocked(mob, prey.position()), "eraser blocks acoustic ray");
            h.assertTrue(!mob.getVibrationUser().canReceiveVibration(f.level, prey.blockPosition(), GameEvent.STEP, GameEvent.Context.of(prey)), "eraser rejects hearing");
            mob.tickCount = 19; f.level.tickNonPassenger(mob);
            h.assertTrue(mob.getTarget() == prey, "blood crosses eraser wall independently");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void sleep_threshold_wake_and_persistence(GameTestHelper h) {
        ready(h, f -> {
            var mob = f.hostile(0, 0);
            var tag = state(mob); tag.putLong("SuxiaLastSense", f.now() - 599); mob.readAdditionalSaveData(tag); mob.tick();
            h.assertTrue(!mob.isPlayingDead(), "awake before 600 quiet ticks");
            tag = state(mob); tag.putLong("SuxiaLastSense", f.now() - 600); mob.readAdditionalSaveData(tag); mob.tick();
            h.assertTrue(mob.isPlayingDead(), "sleep at 600 quiet ticks");
            var loaded = ModEntities.SUXIA.get().create(f.level); loaded.readAdditionalSaveData(state(mob));
            h.assertTrue(loaded.isPlayingDead(), "sleep pose survives entity NBT round trip");
            var cow = f.add(EntityType.COW.create(f.level), 2, 0); cow.setNoAi(true);
            mob.tickCount = 4; f.level.tickNonPassenger(mob);
            h.assertTrue(!mob.isPlayingDead() && mob.getTarget() == null, "non-prey creature wakes without becoming an attack target");
            cow.discard(); mob.setPlayingDead(true);
            var p = f.player(2, 0); mob.tickCount = 4; f.level.tickNonPassenger(mob);
            h.assertTrue(!mob.isPlayingDead() && mob.getTarget() == p && state(mob).getLong("SuxiaAmbushUntil") == f.now() + 60, "three-block proximity wakes and arms ambush");
            mob.doHurtTarget(p);
            h.assertTrue(state(mob).getLong("SuxiaAmbushUntil") == 0, "ambush consumed by first strike");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 240)
    public static void blind_enemy_forgets_quiet_full_health_target(GameTestHelper h) {
        ready(h, f -> {
            var mob = f.hostile(0, 0); var p = f.player(12, 0);
            p.setShiftKeyDown(true); mob.perceive(p);
            // Keep physical contact from refreshing the sense timer, without disabling perception AI.
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            h.runAfterDelay(165, () -> { h.assertTrue(mob.getTarget() == null, "vision does not refresh blind targeting after eight seconds"); h.succeed(); });
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void restored_sleep_wakes_from_damage_and_non_prey(GameTestHelper h) {
        ready(h, f -> {
            var original = f.hostile(0, 0);
            original.setPlayingDead(true);
            var saved = original.saveWithoutId(new CompoundTag());
            original.discard();
            var restored = ModEntities.SUXIA.get().create(f.level);
            restored.load(saved);
            f.add(restored, 0, 0);
            h.assertTrue(restored.isPlayingDead() && restored.isPersistenceRequired(), "restored sleeping entity is live and persistent");
            restored.hurt(restored.damageSources().generic(), 1);
            h.assertTrue(!restored.isPlayingDead() && restored.getTarget() == null
                    && state(restored).getLong("SuxiaAmbushUntil") == f.now() + 60, "environmental damage wakes restored entity without inventing prey");
            restored.setPlayingDead(true);
            var cow = f.add(EntityType.COW.create(f.level), 2, 0); cow.setNoAi(true);
            restored.tickCount = 4; f.level.tickNonPassenger(restored);
            h.assertTrue(!restored.isPlayingDead() && restored.getTarget() == null, "non-prey wakes restored entity without being attacked");
            f.level.tickNonPassenger(restored);
            h.assertTrue(!restored.isPlayingDead(), "woken entity does not immediately fall asleep again");
            cow.discard(); restored.setPlayingDead(true);
            var creative = f.player(2, 0); creative.setGameMode(GameType.CREATIVE);
            restored.tickCount = 4; f.level.tickNonPassenger(restored);
            h.assertTrue(!restored.isPlayingDead() && restored.getTarget() == null,
                    "creative player wakes restored entity but is never prey");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void hostile_allies_and_command_persistence(GameTestHelper h) {
        ready(h, f -> {
            var mob = f.hostile(0, 0); var zombie = f.add(EntityType.ZOMBIE.create(f.level), 4, 0);
            zombie.setTarget(mob);
            h.assertTrue(zombie.getTarget() == null && mob.isAlliedTo(zombie), "hostile mobs cannot retaliate against hostile Suxia");
            h.assertTrue(mob.isPersistenceRequired(), "new hostile persists before spawn finalization");
            var source = f.level.getServer().createCommandSourceStack().withLevel(f.level).withPermission(4);
            for (String nbt : List.of("{}", "{PersistenceRequired:0b}")) {
                BlockPos pos = f.center.offset(0, 0, 8);
                int result = f.level.getServer().getCommands().performPrefixedCommand(source,
                        "summon sound_isolating_eraser:suxia " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + nbt);
                h.assertTrue(result == 1, "real summon command succeeds with " + nbt);
                var spawned = f.level.getEntitiesOfClass(SuxiaEntity.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(1)).get(0);
                f.entities.add(spawned);
                h.assertTrue(spawned.isPersistenceRequired(), "explicit command NBT cannot disable persistence");
                spawned.checkDespawn();
                h.assertTrue(!spawned.isRemoved(), "command hostile survives despawn check");
                var restored = ModEntities.SUXIA.get().create(f.level);
                restored.load(spawned.saveWithoutId(new CompoundTag()));
                h.assertTrue(restored.isPersistenceRequired(), "full entity load preserves persistence");
                spawned.discard();
            }
            var player = f.player(8, 0); player.setGameMode(GameType.SPECTATOR);
            h.assertTrue(!SuxiaEntity.prey(player), "spectator is never prey");
            h.succeed();
        });
    }

    private SuxiaGameTests() { }
}
