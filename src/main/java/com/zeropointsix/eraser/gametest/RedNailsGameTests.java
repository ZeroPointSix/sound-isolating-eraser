package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.rednails.*;
import com.zeropointsix.eraser.registry.ModEntities;
import com.zeropointsix.eraser.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

@GameTestHolder("red_nails_tests")
@PrefixGameTestTemplate(false)
public final class RedNailsGameTests {
    private static final String BATCH = "red_nails";
    private static final List<Fixture> FIXTURES = new ArrayList<>();
    private static final class Fixture {
        final ServerLevel level;
        final GameTestHelper helper;
        final BlockPos center;
        final List<Entity> entities = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final List<ChunkPos> chunks = new ArrayList<>();
        final LinkedHashMap<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Fixture(GameTestHelper h) {
            level = h.getLevel();
            helper = h;
            int index = FIXTURES.size();
            center = new BlockPos(-96 + index % 5 * 48, 130, -96 + index / 5 * 48);
            FIXTURES.add(this);
            var base = new ChunkPos(center);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                var chunk = new ChunkPos(base.x + x, base.z + z);
                level.getChunkSource().addRegionTicket(TicketType.PLAYER, chunk, 2, chunk);
                level.getChunk(chunk.x, chunk.z); chunks.add(chunk);
            }
            for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
                set(center.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y < 6; y++) set(center.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        void set(BlockPos pos, BlockState state) {
            blocks.putIfAbsent(pos.immutable(), level.getBlockState(pos)); level.setBlock(pos, state, 3);
        }
        <T extends Entity> T add(T entity, int x, int z) {
            entity.setPos(Vec3.atBottomCenterOf(center.offset(x, 0, z)));
            if (entity instanceof Mob mob) mob.setNoAi(true);
            level.addFreshEntity(entity); entities.add(entity); return entity;
        }
        ServerPlayer player(int x, int z) {
            var p = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "NailsTest"));
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channels.add(new EmbeddedChannel(connection)); connection.setProtocol(ConnectionProtocol.PLAY);
            p.connection = new ServerGamePacketListenerImpl(level.getServer(), connection, p);
            p.setGameMode(GameType.SURVIVAL); p.setPos(Vec3.atBottomCenterOf(center.offset(x, 0, z)));
            level.addNewPlayer(p); entities.add(p);
            // No socket pumps this fixture's connection; drive the real player simulation.
            helper.onEachTick(() -> { if (!p.isRemoved()) p.doTick(); });
            return p;
        }
        ItemStack wear(ServerPlayer p) {
            var stack = new ItemStack(ModItems.RED_NAILS.get());
            equip(p, stack); return stack;
        }
        void equip(ServerPlayer p, ItemStack stack) {
            CuriosApi.getCuriosInventory(p).orElseThrow(() -> new IllegalStateException("Missing Curios inventory"))
                    .getStacksHandler("ring").orElseThrow(() -> new IllegalStateException("Missing ring slot"))
                    .getStacks().setStackInSlot(0, stack);
        }
        RedNailsState.Entry entry(ItemStack stack) { return RedNailsState.get(level.getServer()).read(stack); }
        WaterGhostSummonEntity summon(ServerPlayer player, ItemStack stack) {
            if (!RedNailsItem.activate(player)) throw new IllegalStateException("Summon rejected");
            var entry = entry(stack);
            for (int nail : RedNailsState.ORDER) {
                var ghost = RedNailsItem.resolve(level.getServer(), entry.ghosts[nail]);
                if (ghost != null && !entities.contains(ghost)) { ghost.setNoAi(true); entities.add(ghost); return ghost; }
            }
            throw new IllegalStateException("No new summon");
        }
        WaterGhostEntity hostile(int x, int z) { return add(ModEntities.WATER_GHOST.get().create(level), x, z); }
        void water(int min, int max) {
            for (int x = min; x <= max; x++) for (int z = min; z <= max; z++) for (int y = 0; y < 5; y++) {
                set(center.offset(x, y, z), Blocks.WATER.defaultBlockState());
            }
        }
        void close() {
            entities.stream().filter(e -> e instanceof ServerPlayer).forEach(e -> RedNailsItem.cancelOwner((ServerPlayer) e));
            entities.forEach(Entity::discard);
            blocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
            chunks.forEach(c -> level.getChunkSource().removeRegionTicket(TicketType.PLAYER, c, 2, c));
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
    @AfterBatch(batch = BATCH) public static void cleanup(ServerLevel level) {
        FIXTURES.forEach(Fixture::close); FIXTURES.clear(); RedNailsInput.clear();
    }
    private static void check(GameTestHelper h, boolean value, String message) { h.assertTrue(value, message); }

    @GameTest(template = "empty", batch = BATCH) public static void accessory_only(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0);
        p.getInventory().setItem(0, new ItemStack(ModItems.RED_NAILS.get()));
        check(h, !RedNailsItem.activate(p), "Held item must not activate");
        var stack = f.wear(p);
        check(h, RedNailsItem.findWorn(p) == stack, "Functional ring is visible");
        check(h, ModItems.RED_NAILS.get().canEquip(new top.theillusivec4.curios.api.SlotContext("ring", p, 1, false, true),
                new ItemStack(ModItems.RED_NAILS.get())) == false, "Second accessory denied");
        h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH) public static void four_slots_order_and_recall(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p);
        for (int nail : RedNailsState.ORDER) check(h, f.summon(p, stack).nail() == nail, "Fixed finger order");
        check(h, f.entry(stack).active() == 4 && !RedNailsItem.activate(p), "Four-summon cap");
        check(h, stack.getTag().getBoolean("FirstUseShown"), "First-use flag persisted");
        p.setShiftKeyDown(true); RedNailsItem.activate(p); p.setShiftKeyDown(false);
        check(h, f.entry(stack).active() == 0 && RedNailsItem.coolingCount(stack) == 0, "Recall has no penalty");
        check(h, RedNailsItem.activate(p), "Immediate resummon allowed"); h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH) public static void binding_and_foreign_owner(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var other = f.player(8, 0); var stack = f.wear(p);
        p.setShiftKeyDown(true); RedNailsItem.activate(p); p.setShiftKeyDown(false);
        check(h, !stack.hasTag() || !stack.getTag().hasUUID("Owner"), "Recall cannot bind an unused memory");
        f.summon(p, stack);
        check(h, stack.getTag().getUUID("Owner").equals(p.getUUID()), "Successful first summon binds");
        f.equip(p, ItemStack.EMPTY); f.equip(other, stack);
        check(h, !RedNailsItem.activate(other), "Foreign owner rejected"); h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 160) public static void server_chant_release_and_latch(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p);
        RedNailsInput.key(p, true);
        h.runAfterDelay(10, () -> { RedNailsInput.key(p, false); check(h, f.entry(stack) == null, "Short press cannot summon"); });
        h.runAfterDelay(15, () -> RedNailsInput.key(p, true));
        h.runAfterDelay(45, () -> check(h, f.entry(stack) != null && f.entry(stack).active() == 1, "Server authorizes after 20 ticks"));
        h.runAfterDelay(80, () -> {
            check(h, f.entry(stack).active() == 1, "Holding never repeats");
            RedNailsInput.key(p, false); RedNailsInput.key(p, true);
        });
        h.runAfterDelay(110, () -> { check(h, f.entry(stack).active() == 2, "Release rearms input"); h.succeed(); });
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 120) public static void death_backlash_ignores_armor_and_is_idempotent(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        h.runAfterDelay(65, () -> {
            var ghost = f.summon(p, stack); var survivor = f.summon(p, stack);
            ghost.hurt(ghost.damageSources().genericKill(), 1000);
            check(h, Math.abs(p.getHealth() - 16) < 0.01, "Backlash deals four damage through diamond armor: " + p.getHealth());
            check(h, p.hasEffect(MobEffects.DARKNESS) && p.hasEffect(MobEffects.CONFUSION)
                    && p.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 1, "Backlash effects");
            check(h, f.entry(stack).ready[1] == RedNailsState.clock(f.level.getServer()) + 12000, "Ten-minute finger cooldown");
            check(h, survivor.isAlive() && RedNailsItem.coolingCount(stack) == 1, "Only one finger is lost");
            RedNailsItem.ghostEnded(ghost, true);
            check(h, p.getHealth() == 16, "Duplicate death callback cannot hurt twice");
            survivor.hurt(survivor.damageSources().genericKill(), 1000);
            check(h, p.getHealth() == 12 && RedNailsItem.coolingCount(stack) == 2,
                    "Separate deaths in the same tick each apply their own penalty"); h.succeed();
        });
    }
    @GameTest(template = "empty", batch = BATCH) public static void cooldown_roundtrip_and_boundary(GameTestHelper h) {
        var state = new RedNailsState(); var entry = new RedNailsState.Entry(UUID.randomUUID(), UUID.randomUUID());
        UUID ghost = UUID.randomUUID(); state.begin(entry, 1, ghost); state.finish(entry, 1, ghost, true, 500);
        var restored = RedNailsState.load(state.save(new CompoundTag())).find(entry.item);
        check(h, restored.ready[1] == 12500 && restored.available(12499) == 0, "Independent cooldown persists");
        check(h, restored.available(12500) == 1, "Exact deadline regrows first finger");
        var stack = new ItemStack(ModItems.RED_NAILS.get()); state.sync(stack, entry, 12499);
        check(h, RedNailsItem.coolingCount(ItemStack.of(stack.save(new CompoundTag()))) == 1, "Item roundtrip retains visual cooldown"); h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100) public static void unequip_cleans_without_cooldown(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p); var ghost = f.summon(p, stack);
        f.equip(p, ItemStack.EMPTY); p.getInventory().setItem(1, stack);
        h.runAfterDelay(30, () -> {
            check(h, ghost.isRemoved() && f.entry(stack).active() == 0, "Unequip removes summon");
            check(h, f.entry(stack).ready[1] == 0, "Unequip never tears a nail"); h.succeed();
        });
    }
    @GameTest(template = "empty", batch = BATCH) public static void logout_and_dimension_cleanup(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p); var ghost = f.summon(p, stack);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
        check(h, ghost.isRemoved() && f.entry(stack).active() == 0, "Logout recalls");
        ghost = f.summon(p, stack);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, LevelKey.OVERWORLD, LevelKey.NETHER));
        check(h, ghost.isRemoved() && f.entry(stack).active() == 0 && RedNailsItem.coolingCount(stack) == 0, "Dimension recalls without backlash"); h.succeed();
    }
    private static final class LevelKey {
        static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> OVERWORLD = net.minecraft.world.level.Level.OVERWORLD;
        static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> NETHER = net.minecraft.world.level.Level.NETHER;
    }
    @GameTest(template = "empty", batch = BATCH) public static void formations_and_target_sharing(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p); p.setYRot(0);
        var a = f.add(EntityType.ZOMBIE.create(f.level), 6, 0); a.setTarget(p);
        var b = f.add(EntityType.SKELETON.create(f.level), -6, 0); b.setTarget(p);
        var ghosts = new ArrayList<WaterGhostSummonEntity>();
        for (int i = 0; i < 4; i++) { var g = f.summon(p, stack); g.chooseTarget(); ghosts.add(g); }
        check(h, ghosts.stream().filter(g -> g.getTarget() == a).count() == 2
                && ghosts.stream().filter(g -> g.getTarget() == b).count() == 2, "Two attackers per target");
        check(h, ghosts.get(0).guardPosition(p).z == p.getZ() + 2.5, "Ring finger guards in front");
        check(h, ghosts.stream().map(g -> g.guardPosition(p)).distinct().count() == 4, "Four distinct guard positions"); h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH) public static void friendly_fire_and_pvp_policy(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p); var g = f.summon(p, stack);
        var wolf = f.add(EntityType.WOLF.create(f.level), 3, 0); wolf.setTame(true); wolf.setOwnerUUID(p.getUUID());
        var suxia = f.add(ModEntities.SUXIA_SUMMON.get().create(f.level), 4, 0); suxia.bind(p, UUID.randomUUID());
        check(h, !g.wantsToAttack(p, p) && !g.wantsToAttack(wolf, p) && !g.wantsToAttack(suxia, p)
                && !g.wantsToAttack(f.add(EntityType.VILLAGER.create(f.level), 5, 0), p)
                && !g.wantsToAttack(f.add(EntityType.IRON_GOLEM.create(f.level), 7, 0), p), "Friendly entities protected");
        boolean previous = f.level.getServer().isPvpAllowed();
        try {
            f.level.getServer().setPvpAllowed(false);
            check(h, !g.wantsToAttack(f.player(9, 0), p), "PvP-disabled players protected");
        } finally { f.level.getServer().setPvpAllowed(previous); }
        h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100) public static void attributes_regeneration_and_swim_box(GameTestHelper h) {
        var f = new Fixture(h); f.water(-5, 5); var g = f.hostile(0, 0);
        check(h, g.getMaxHealth() == 40 && g.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3
                && g.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.23, "Zombie-based attributes");
        h.runAfterDelay(8, () -> {
            check(h, g.ghostBody().swimming() && Math.abs(g.getBbHeight() - 1.2) < 0.01, "Deep-water dimensions");
            check(h, g.getEffect(MobEffects.REGENERATION).getAmplifier() == 1, "Permanent regeneration II");
            g.removeEffect(MobEffects.REGENERATION);
        });
        h.runAfterDelay(12, () -> { check(h, g.hasEffect(MobEffects.REGENERATION), "Cleansed regeneration restored"); h.succeed(); });
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100) public static void low_ceiling_prevents_standing(GameTestHelper h) {
        var f = new Fixture(h); f.water(-2, 2); var g = f.hostile(0, 0);
        h.runAfterDelay(8, () -> {
            check(h, g.ghostBody().swimming(), "Initially swimming");
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = 0; y < 6; y++)
                f.set(f.center.offset(x, y, z), Blocks.AIR.defaultBlockState());
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) f.set(f.center.offset(x, 2, z), Blocks.STONE.defaultBlockState());
        });
        h.runAfterDelay(18, () -> {
            check(h, !g.isInWater() && g.ghostBody().swimming(), "Low roof retains compact collision box");
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) f.set(f.center.offset(x, 2, z), Blocks.AIR.defaultBlockState());
        });
        h.runAfterDelay(25, () -> { check(h, !g.ghostBody().swimming() && g.getBbHeight() > 2.3, "Clear headroom restores standing"); h.succeed(); });
    }
    @GameTest(template = "empty", batch = BATCH) public static void copied_item_cannot_reset_ledger(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p); var g = f.summon(p, stack);
        var stale = stack.copy(); g.hurt(g.damageSources().genericKill(), 1000);
        f.equip(p, stale); RedNailsItem.maintain(stale, p);
        check(h, f.entry(stale).ready[1] > RedNailsState.clock(f.level.getServer())
                && RedNailsItem.coolingCount(stale) == 1, "Stale copied NBT cannot reset authoritative cooldown");
        check(h, f.summon(p, stale).nail() == 0, "A copy advances to next available finger"); h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100) public static void orphan_and_owner_teleport_cleanup(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p); var g = f.summon(p, stack);
        g.setPos(p.position().add(15, 0, 0));
        var orphan = f.add(ModEntities.WATER_GHOST_SUMMON.get().create(f.level), -6, 0);
        orphan.bind(p, UUID.randomUUID(), 0);
        h.runAfterDelay(12, () -> {
            check(h, orphan.isRemoved(), "Reloaded orphan without an authoritative ledger is discarded");
            check(h, g.isAlive() && g.distanceToSqr(p) <= 18, "Guardian teleports safely beyond twelve blocks");
            check(h, f.entry(stack).active() == 1, "Teleport preserves the active finger"); h.succeed();
        });
    }
    @GameTest(template = "empty", batch = BATCH) public static void grab_lock_duration_and_escape(GameTestHelper h) {
        var f = new Fixture(h); var g = f.hostile(0, 0); var second = f.hostile(0, 2);
        var victim = f.add(EntityType.VILLAGER.create(f.level), 0, 1);
        f.set(f.center.offset(2, 0, 1), Blocks.WATER.defaultBlockState());
        g.setTarget(victim); second.setTarget(victim); var grab = g.ghostBody().grab;
        check(h, grab.canUse(), "Valid nearby victim"); grab.start();
        check(h, !second.ghostBody().grab.canUse(), "One ghost per victim");
        grab.damage(7.99F); check(h, grab.held() == victim, "Less than eight damage keeps grip");
        grab.damage(0.01F); check(h, grab.held() == null && !g.ghostBody().grabbing(), "Eight damage releases");
        check(h, grab.cooldownUntil() == f.level.getGameTime() + 160, "Eight-second grab cooldown");
        check(h, second.ghostBody().grab.canUse(), "Release clears victim lease");
        var other = second.ghostBody().grab; other.start();
        for (int i = 0; i < 60; i++) other.tick();
        check(h, !other.canContinueToUse(), "Land hold ends at sixty ticks"); other.stop(); h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH) public static void boss_size_and_creative_exclusions(GameTestHelper h) {
        var f = new Fixture(h);
        check(h, !WaterGhostGrab.grabbable(f.add(EntityType.WARDEN.create(f.level), 0, 0)), "Warden too large");
        check(h, !WaterGhostGrab.grabbable(f.add(EntityType.WITHER.create(f.level), 4, 0)), "Boss excluded");
        var p = f.player(8, 0); p.setGameMode(GameType.CREATIVE);
        check(h, !WaterGhostGrab.grabbable(p), "Creative player excluded"); h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH) public static void hostile_shore_and_persistence(GameTestHelper h) {
        var f = new Fixture(h); var g = f.hostile(0, 0); var p = f.player(5, 0); g.setTarget(p);
        check(h, !g.mayPursue(), "Dry passer-by cannot trigger pursuit");
        f.set(p.blockPosition().offset(2, 0, 0), Blocks.WATER.defaultBlockState());
        check(h, g.mayPursue(), "Within six blocks near water triggers");
        p.setPos(p.position().add(5, 0, 0));
        check(h, !g.mayPursue(), "Outside six blocks does not initiate");
        check(h, g.isPersistenceRequired() && g.isAlliedTo(f.add(EntityType.ZOMBIE.create(f.level), -4, 0)), "Hostile persistence and monster alliance"); h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 80) public static void watched_target_does_not_block_nearby_prey(GameTestHelper h) {
        var f = new Fixture(h); f.water(-1, 1); var g = f.hostile(0, 0);
        var passer = f.player(12, 0);
        h.runAfterDelay(15, () -> {
            check(h, g.getTarget() == passer && !g.mayPursue(), "Distant dry player is watched without pursuit");
            var near = f.add(EntityType.VILLAGER.create(f.level), 3, 0);
            f.set(near.blockPosition(), Blocks.WATER.defaultBlockState());
        });
        h.runAfterDelay(30, () -> {
            check(h, g.getTarget() != passer && g.getTarget() instanceof net.minecraft.world.entity.npc.Villager
                    && g.mayPursue(), "Nearby shore prey replaces a non-pursued watched target"); h.succeed();
        });
    }
    @GameTest(template = "empty", batch = BATCH) public static void summon_prefers_shore_water_when_grabbing(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p); var g = f.summon(p, stack);
        for (int x = 2; x <= 4; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y < 4; y++)
            f.set(f.center.offset(x, y, z), Blocks.WATER.defaultBlockState());
        g.setPos(Vec3.atBottomCenterOf(f.center)); g.setYRot(0);
        var victim = f.add(EntityType.ZOMBIE.create(f.level), 0, 1);
        g.setTarget(victim); g.ghostBody().grab.start(); g.ghostBody().grab.tick();
        check(h, g.getDeltaMovement().x > 0, "Shore summon pulls its victim toward nearby water");
        g.ghostBody().grab.stop(); h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 120) public static void underwater_grab_oxygen_and_boat(GameTestHelper h) {
        var f = new Fixture(h); f.water(-5, 5); var g = f.hostile(0, 0);
        var victim = f.add(EntityType.VILLAGER.create(f.level), 0, 1);
        var boat = f.add(EntityType.BOAT.create(f.level), 0, 1); victim.startRiding(boat, true);
        h.runAfterDelay(8, () -> {
            victim.stopRiding(); victim.setPos(g.position().add(0, 0, 1));
        });
        h.runAfterDelay(12, () -> {
            victim.startRiding(boat, true); g.setTarget(victim); g.ghostBody().grab.start();
            check(h, !victim.isPassenger(), "Grab dismounts boat passenger");
            victim.setPos(g.position().add(0, 0, 1));
        });
        h.runAfterDelay(16, () -> {
            victim.setAirSupply(-18); float health = victim.getHealth(); g.ghostBody().grab.tick();
            check(h, victim.getAirSupply() == 0 && victim.getHealth() < health, "Extra oxygen drain cannot skip vanilla drowning boundary");
            for (int i = 1; i < 60; i++) g.ghostBody().grab.tick();
            check(h, g.ghostBody().grab.canContinueToUse(), "Water grip lasts beyond sixty ticks");
            for (int i = 60; i < 100; i++) g.ghostBody().grab.tick();
            check(h, !g.ghostBody().grab.canContinueToUse(), "Water grip ends at one hundred ticks"); h.succeed();
        });
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 150) public static void curio_nbt_changes_keep_other_summons(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p);
        var first = f.summon(p, stack);
        h.runAfterDelay(20, () -> f.summon(p, stack));
        h.runAfterDelay(40, () -> check(h, f.entry(stack).active() == 2 && first.isAlive(),
                "Curios NBT update must not recall equipped summons"));
        h.runAfterDelay(70, () -> first.hurt(first.damageSources().genericKill(), 1000));
        h.runAfterDelay(100, () -> {
            check(h, f.entry(stack).active() == 1 && RedNailsItem.coolingCount(stack) == 1,
                    "Death and cooldown sync preserve the surviving summon");
            f.equip(p, ItemStack.EMPTY);
        });
        h.runAfterDelay(125, () -> {
            check(h, f.entry(stack).active() == 0 && f.entry(stack).ready[1] > RedNailsState.clock(f.level.getServer()),
                    "Real unequip recalls survivors without clearing the dead finger cooldown"); h.succeed();
        });
    }
    @GameTest(template = "empty", batch = BATCH) public static void grab_rechecks_pvp_and_target_policy(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p); var g = f.summon(p, stack);
        var other = f.player(0, 1); g.setPos(p.position()); g.setTarget(other);
        boolean previous = f.level.getServer().isPvpAllowed();
        try {
            f.level.getServer().setPvpAllowed(false);
            check(h, !g.ghostBody().grab.canUse(), "Grab cannot bypass disabled PvP");
            f.level.getServer().setPvpAllowed(true);
            g.ghostBody().grab.start();
            check(h, g.ghostBody().grab.held() == other, "Hostile PvP target can be grabbed");
            f.level.getServer().setPvpAllowed(false);
            g.ghostBody().grab.tick();
            check(h, g.ghostBody().grab.held() == null, "Turning PvP off releases the target immediately");
        } finally { g.ghostBody().grab.stop(); f.level.getServer().setPvpAllowed(previous); }
        var hostile = f.hostile(3, 0); hostile.setTarget(f.add(EntityType.ZOMBIE.create(f.level), 3, 1));
        check(h, !hostile.ghostBody().grab.canUse(), "Hostile grab cannot attack allied monsters"); h.succeed();
    }
}
