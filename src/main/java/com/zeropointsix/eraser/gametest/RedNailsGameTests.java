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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import net.minecraft.world.item.TooltipFlag;
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
    private static void sealShorePit(Fixture f, int floorY) {
        for (int x = -4; x <= 1; x++) for (int z = -4; z <= 4; z++) {
            for (int y = floorY; y <= -1; y++) f.set(f.center.offset(x, y, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = 2; x <= 7; x++) for (int z = -3; z <= 3; z++) {
            f.set(f.center.offset(x, floorY, z), Blocks.STONE.defaultBlockState());
            for (int y = floorY + 1; y <= -1; y++) f.set(f.center.offset(x, y, z), Blocks.WATER.defaultBlockState());
        }
    }
    private static int tooltipAvailable(ItemStack stack, ServerPlayer player) {
        for (Component line : stack.getTooltipLines(player, TooltipFlag.NORMAL)) {
            if (line.getContents() instanceof TranslatableContents text
                    && text.getKey().equals("tooltip.sound_isolating_eraser.red_nails.nails")
                    && text.getArgs().length > 1) {
                Object count = text.getArgs()[1];
                return count instanceof Number n ? n.intValue() : Integer.parseInt(count.toString());
            }
        }
        throw new IllegalStateException("Missing nails available tooltip");
    }

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
        check(h, RedNailsInput.chanting(p.getUUID()), "Press packet starts a server chant");
        h.runAfterDelay(10, () -> {
            RedNailsInput.key(p, false);
            check(h, !RedNailsInput.chanting(p.getUUID()), "Release packet clears the chant");
            check(h, RedNailsInput.releasedAt(p.getUUID()) >= RedNailsInput.pressedAt(p.getUUID()),
                    "Server observed press then release");
            check(h, f.entry(stack) == null, "Short press cannot summon");
        });
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
    @AfterBatch(batch = "red_nails_natural_targets")
    public static void cleanupNaturalTargets(ServerLevel level) { cleanup(level); }
    @GameTest(template = "empty", batch = "red_nails_natural_targets", timeoutTicks = 200)
    public static void natural_targets_owner_attacker_both_scheduler_phases(GameTestHelper h) {
        naturalTargets(h, 0);
    }
    @GameTest(template = "empty", batch = "red_nails_natural_targets", timeoutTicks = 200)
    public static void natural_targets_owner_victim_both_scheduler_phases(GameTestHelper h) {
        naturalTargets(h, 1);
    }
    @GameTest(template = "empty", batch = "red_nails_natural_targets", timeoutTicks = 200)
    public static void natural_targets_threat_both_scheduler_phases(GameTestHelper h) {
        naturalTargets(h, 2);
    }
    private static void naturalTargets(GameTestHelper h, int source) {
        var f = new Fixture(h);
        h.startSequence().thenWaitUntil(() -> check(h, f.level.isPositionEntityTicking(f.center),
                "Natural-target fixture is entity-ticking")).thenIdle(10).thenExecute(() -> {
            var owner = f.player(0, 0);
            var stack = f.wear(owner);
            var first = f.summon(owner, stack);
            var second = f.summon(owner, stack);
            check(h, (first.getId() & 1) != (second.getId() & 1), "Both entity-ID phases are present");
            var ghosts = List.of(first, second);
            ghosts.forEach(g -> g.setNoAi(false));
            var victim = f.add(EntityType.ZOMBIE.create(f.level), 8, 0);
            victim.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            victim.setHealth(200);
            // Wait out the player's 60-tick spawn protection and the initial AI
            // phase; never assign a guardian's target or invoke its target goal.
            h.startSequence().thenWaitUntil(() -> check(h, first.tickCount >= 65 && second.tickCount >= 65,
                    "Both guardians passed spawn protection and reached the alternating selector schedule"))
                    .thenExecute(() -> {
                        check(h, first.tickCount == second.tickCount, "Both ID phases have the same natural age");
                        check(h, ghosts.stream().allMatch(g -> g.getTarget() == null), "No target before hostility");
                        if (source == 0) {
                            check(h, owner.hurt(owner.damageSources().mobAttack(victim), 1), "Actual owner damage accepted");
                            check(h, owner.getLastHurtByMob() == victim, "Damage records owner attacker");
                        } else if (source == 1) {
                            owner.attack(victim);
                            check(h, owner.getLastHurtMob() == victim, "Actual owner attack records victim");
                        } else {
                            victim.setTarget(owner);
                        }
                        System.out.println("RED_NAILS_NATURAL_TARGET_START source=" + source
                                + " serverTick=" + f.level.getServer().getTickCount()
                                + " ids=" + first.getId() + "," + second.getId()
                                + " age=" + first.tickCount);
                    }).thenWaitUntil(() -> check(h,
                            ghosts.stream().allMatch(g -> g.isAlive() && g.getTarget() == victim),
                            "Both natural selector phases acquire source=" + source + " targets="
                                    + (first.getTarget() == victim) + "," + (second.getTarget() == victim)))
                    .thenExecute(() -> System.out.println("RED_NAILS_NATURAL_TARGET_PASSED source=" + source
                            + " age=" + first.tickCount + " ids=" + first.getId() + "," + second.getId()))
                    .thenSucceed();
        });
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
        // A shore destination must have a reachable descent, not water on a stone slab.
        for (int x = -4; x <= 1; x++) for (int z = -4; z <= 4; z++) {
            for (int y = -5; y <= -1; y++) f.set(f.center.offset(x, y, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = 2; x <= 7; x++) for (int z = -3; z <= 3; z++) {
            f.set(f.center.offset(x, -5, z), Blocks.STONE.defaultBlockState());
            for (int y = -4; y <= -1; y++) f.set(f.center.offset(x, y, z), Blocks.WATER.defaultBlockState());
        }
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
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 80) public static void land_to_water_grab_gets_full_water_stage(GameTestHelper h) {
        var f = new Fixture(h);
        var g = f.hostile(0, 0);
        var victim = f.add(EntityType.VILLAGER.create(f.level), 0, 1);
        g.setTarget(victim);
        g.ghostBody().grab.start();
        for (int i = 0; i < 40; i++) g.ghostBody().grab.tick();
        check(h, g.ghostBody().grab.held() == victim, "Still holding after forty land ticks");
        f.water(-3, 3);
        g.setPos(Vec3.atBottomCenterOf(f.center.offset(0, 2, 0)));
        victim.setPos(g.position().add(0, 0, 1));
        h.runAfterDelay(8, () -> {
            check(h, g.isInWater() && g.ghostBody().swimming(), "Grab has entered water");
            check(h, g.ghostBody().grab.held() == victim, "Hold survives the land-to-water transition");
            for (int i = 0; i < 99; i++) g.ghostBody().grab.tick();
            check(h, g.ghostBody().grab.canContinueToUse() && g.ghostBody().grab.held() == victim,
                    "Water stage still has a full hundred ticks after forty land ticks");
            for (int i = 0; i < 5; i++) g.ghostBody().grab.tick();
            check(h, !g.ghostBody().grab.canContinueToUse(), "Water stage ends after one hundred water ticks");
            h.succeed();
        });
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 280) public static void hostile_ai_grabs_and_drags_in_water(GameTestHelper h) {
        var f = new Fixture(h);
        f.water(-4, 4);
        var g = ModEntities.WATER_GHOST.get().create(f.level);
        g.setPos(Vec3.atBottomCenterOf(f.center.offset(0, 3, 0)));
        g.setNoAi(false);
        f.level.addFreshEntity(g);
        f.entities.add(g);
        var victim = EntityType.VILLAGER.create(f.level);
        victim.setPos(Vec3.atBottomCenterOf(f.center.offset(0, 3, 1)));
        victim.setNoAi(true);
        f.level.addFreshEntity(victim);
        f.entities.add(victim);
        double startY = g.getY();
        long[] grabAt = { -1 };
        long[] releaseAt = { -1 };
        int[] airAtGrab = { -1 };
        h.startSequence().thenWaitUntil(() -> {
            check(h, g.ghostBody().grab.held() == victim, "Goal selector grabs without a direct start() call");
            check(h, g.ghostBody().swimming(), "AI grab is in water");
        }).thenExecute(() -> {
            grabAt[0] = f.level.getGameTime();
            airAtGrab[0] = victim.getAirSupply();
        }).thenWaitUntil(() -> {
            check(h, g.ghostBody().grab.held() == victim, "AI grab still holds before water-stage expiry");
            check(h, g.getY() < startY - 0.8, "Ghost sank under effective AI");
            check(h, victim.getY() < startY, "Victim was dragged downward");
            check(h, victim.getAirSupply() < airAtGrab[0], "Water grab drains oxygen");
        }).thenWaitUntil(() -> check(h, g.ghostBody().grab.held() == null,
                "AI grab releases without extending production duration"))
        .thenExecute(() -> {
            releaseAt[0] = f.level.getGameTime();
            long lived = releaseAt[0] - grabAt[0];
            System.out.println("RED_NAILS_AI_GRAB t0=" + grabAt[0] + " release=" + releaseAt[0] + " dt=" + lived);
            check(h, lived >= 99 && lived <= 101,
                    "Observed water grab lifetime is 100 ticks within one observation tick: " + lived);
            check(h, WaterGhostGrab.grabbable(victim), "Natural release clears victim lease");
            long remaining = g.ghostBody().grab.cooldownUntil() - f.level.getGameTime();
            check(h, remaining >= 150 && remaining <= 160,
                    "Natural release starts 160-tick cooldown: remaining=" + remaining);
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 320) public static void summon_ai_carries_enemy_from_shore_into_water(GameTestHelper h) {
        // Deep pit: floor below the 6-block water-stage cap so release is by duration, not terrain.
        runShoreCarryUntilRelease(h, -10, false);
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 320) public static void summon_ai_shore_grab_stops_on_shallow_seafloor(GameTestHelper h) {
        runShoreCarryUntilRelease(h, -5, true);
    }
    private static void runShoreCarryUntilRelease(GameTestHelper h, int floorY, boolean expectSeafloor) {
        var f = new Fixture(h);
        var p = f.player(0, 0);
        var stack = f.wear(p);
        sealShorePit(f, floorY);
        var g = f.summon(p, stack);
        g.setPos(Vec3.atBottomCenterOf(f.center));
        g.setYRot(0);
        var victim = f.add(EntityType.PILLAGER.create(f.level), 0, 1);
        check(h, !victim.canBreatheUnderwater(), "Shore oxygen fixture cannot breathe underwater");
        victim.setNoAi(true);
        double startX = g.getX();
        double[] waterEntryY = { Double.NaN };
        int[] airAtWaterEntry = { -1 };
        long[] waterEntryAt = { -1 };
        long[] releaseAt = { -1 };
        double[] minY = { Double.POSITIVE_INFINITY };
        boolean[] hitFloor = { false };
        int[] swimTicks = { 0 };
        int[] extraDrainTicks = { 0 };
        int[] prevAir = { -1 };
        boolean[] sampleAir = { false };
        h.onEachTick(() -> {
            if (g.ghostBody().grab.held() == victim && g.ghostBody().swimming()) {
                minY[0] = Math.min(minY[0], g.getY());
                if (g.verticalCollisionBelow || !f.level.noCollision(g, g.getBoundingBox().move(0, -0.08, 0))) {
                    hitFloor[0] = true;
                }
                if (sampleAir[0]) {
                    int air = victim.getAirSupply();
                    if (prevAir[0] >= 0 && swimTicks[0] < 10) {
                        if (prevAir[0] - air >= 3) extraDrainTicks[0]++;
                        swimTicks[0]++;
                    }
                    prevAir[0] = air;
                }
            }
            if (g.tickCount % 10 == 0) System.out.println("RED_NAILS_SHORE_TRACE t=" + f.level.getGameTime()
                    + " held=" + (g.ghostBody().grab.held() == victim)
                    + " ghost=" + g.position() + " victim=" + victim.position()
                    + " swimming=" + g.ghostBody().swimming() + " victimInWater=" + victim.isInWater()
                    + " canSink=" + f.level.noCollision(g, g.getBoundingBox().move(0, -0.08, 0))
                    + " floor=" + g.verticalCollisionBelow + " victimHealth=" + victim.getHealth()
                    + " air=" + victim.getAirSupply()
                    + " eyeInWater=" + victim.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                    + " eyeY=" + victim.getEyeY());
        });
        // activate() may spawn in nearby water. Let the relocated fixture update its
        // real fluid/pose state on dry land before the goal selector can start a grab.
        h.startSequence().thenWaitUntil(() -> {
            // NoAI teleports never set onGround; wait for real fluid/pose instead.
            check(h, !g.isInWater() && !g.ghostBody().swimming() && g.getY() >= f.center.getY() - 0.01,
                    "Shore fixture is dry and on the bank before enabling AI");
            check(h, g.ghostBody().grab.held() == null, "Shore fixture has no pre-existing grab");
        }).thenExecute(() -> {
            System.out.println("RED_NAILS_SHORE_READY t=" + f.level.getGameTime()
                    + " ghost=" + g.position() + " swimming=" + g.ghostBody().swimming()
                    + " inWater=" + g.isInWater() + " onGround=" + g.onGround());
            victim.setTarget(p);
            p.setLastHurtMob(victim);
            g.setTarget(victim);
            g.setNoAi(false);
        }).thenWaitUntil(() -> {
            check(h, g.ghostBody().grab.held() == victim, "Summon goal selector grabs on shore without start()");
            check(h, !g.ghostBody().swimming(), "First grab is still on land");
        }).thenWaitUntil(() -> {
            check(h, g.ghostBody().grab.held() == victim, "Shore grab keeps the enemy while walking to water");
            check(h, g.getX() > startX + 0.4 || g.isInWater() || g.ghostBody().swimming(),
                    "Summon carries the enemy toward nearby water");
        }).thenWaitUntil(() -> {
            check(h, g.ghostBody().grab.held() == victim, "Hold survives the AI shore-to-water carry");
            check(h, g.ghostBody().swimming() && g.isInWater(), "Summon entered water under its own AI");
            check(h, victim.isInWater(), "Victim actually entered water under the summon AI");
            var tag = victim.getPersistentData();
            check(h, tag.hasUUID("sound_isolating_eraser_grabbed_by")
                            && tag.getUUID("sound_isolating_eraser_grabbed_by").equals(g.getUUID()),
                    "Victim water state is still locked to this summon");
        }).thenExecute(() -> {
            waterEntryY[0] = g.getY();
            airAtWaterEntry[0] = victim.getAirSupply();
            waterEntryAt[0] = f.level.getGameTime();
            minY[0] = g.getY();
            System.out.println("RED_NAILS_SHORE_ENTRY t=" + waterEntryAt[0]
                    + " holder=" + victim.getPersistentData().getUUID("sound_isolating_eraser_grabbed_by")
                    + " ghost=" + g.position() + " victim=" + victim.position()
                    + " victimInWater=" + victim.isInWater());
        }).thenWaitUntil(() -> {
            check(h, g.ghostBody().grab.held() == victim, "Shore-to-water grab remains active during descent");
            check(h, g.getY() < waterEntryY[0] - 0.5, "Summon sinks after entering water");
            check(h, victim.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                    && victim.getY() < waterEntryY[0], "Carried victim is submerged and dragged downward");
            check(h, victim.getAirSupply() < airAtWaterEntry[0], "Carried victim loses oxygen underwater");
        }).thenExecute(() -> {
            sampleAir[0] = true;
            prevAir[0] = victim.getAirSupply();
            swimTicks[0] = 0;
            extraDrainTicks[0] = 0;
        }).thenWaitUntil(() -> {
            check(h, g.ghostBody().grab.held() == victim, "Hold survives the extra-oxygen sample window");
            check(h, swimTicks[0] >= 10, "Observed ten submerged water-stage oxygen ticks");
        }).thenExecute(() -> {
            System.out.println("RED_NAILS_SHORE_AIR t=" + f.level.getGameTime()
                    + " extraDrainTicks=" + extraDrainTicks[0] + " air=" + victim.getAirSupply());
            check(h, extraDrainTicks[0] >= 8,
                    "Extra -3 air applied on most submerged water-stage ticks: " + extraDrainTicks[0]);
        }).thenWaitUntil(() -> check(h, g.ghostBody().grab.held() == null,
                "Shore-to-water grab releases without extending production duration"))
        .thenExecute(() -> {
            releaseAt[0] = f.level.getGameTime();
            long lived = releaseAt[0] - waterEntryAt[0];
            double descent = waterEntryY[0] - minY[0];
            long remaining = g.ghostBody().grab.cooldownUntil() - f.level.getGameTime();
            System.out.println("RED_NAILS_SHORE_RELEASE t=" + releaseAt[0] + " dt=" + lived
                    + " descent=" + descent + " minY=" + minY[0] + " floor=" + hitFloor[0]
                    + " air=" + victim.getAirSupply() + " cooldown=" + remaining
                    + " seafloor=" + expectSeafloor);
            check(h, lived >= 99 && lived <= 101,
                    "Observed shore-to-water water-stage lifetime is 100 ticks: " + lived);
            check(h, WaterGhostGrab.grabbable(victim), "Natural shore release clears victim lease");
            check(h, remaining >= 150 && remaining <= 160,
                    "Natural shore release starts 160-tick cooldown: remaining=" + remaining);
            check(h, descent <= 6.2, "Water-stage descent does not exceed the six-block cap: " + descent);
            if (expectSeafloor) {
                check(h, hitFloor[0], "Shallow shore pit hits the seafloor before the six-block cap");
                check(h, descent < 5.5, "Shallow seafloor stop is shallower than the six-block cap: " + descent);
            } else {
                check(h, descent >= 5.5, "Deep shore pit reaches the six-block water-stage cap: " + descent);
                check(h, minY[0] >= waterEntryY[0] - 6.2, "Deep sink stops at the six-block cap, not through the floor");
            }
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 80) public static void tooltip_counts_active_healthy_nails(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(0, 0); var stack = f.wear(p);
        h.runAfterDelay(65, () -> {
            f.summon(p, stack); f.summon(p, stack); f.summon(p, stack); f.summon(p, stack);
            check(h, tooltipAvailable(stack, p) == 4, "Four healthy active nails count as 4/4");
            var ghost = RedNailsItem.resolve(p.getServer(), f.entry(stack).ghosts[RedNailsState.ORDER[0]]);
            ghost.hurt(ghost.damageSources().genericKill(), 1000);
            check(h, tooltipAvailable(stack, p) == 3 && RedNailsItem.coolingCount(stack) == 1,
                    "Three healthy active nails plus one cooling nail are 3/4");
            p.setShiftKeyDown(true); RedNailsItem.activate(p); p.setShiftKeyDown(false);
            check(h, tooltipAvailable(stack, p) == 3 && f.entry(stack).active() == 0,
                    "Three idle healthy nails plus one cooling nail stay 3/4");
            h.succeed();
        });
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 80) public static void player_velocity_correction_blocks_boat_and_swim_escape(GameTestHelper h) {
        var f = new Fixture(h); f.water(-5, 5);
        var g = f.hostile(0, 0);
        var p = f.player(0, 1);
        var boat = f.add(EntityType.BOAT.create(f.level), 0, 1);
        g.setPos(Vec3.atBottomCenterOf(f.center.offset(0, 2, 0)));
        p.setPos(Vec3.atBottomCenterOf(f.center.offset(0, 2, 1)));
        boat.setPos(p.position());
        p.startRiding(boat, true);
        boat.setDeltaMovement(0.35, 0, 0);
        h.runAfterDelay(4, () -> {
            g.setPos(p.position().add(0, 0, -1));
            g.setTarget(p);
            g.ghostBody().grab.start();
            check(h, !p.isPassenger(), "Grab dismounts a moving boat player");
            check(h, g.ghostBody().grab.held() == p, "ServerPlayer velocity-correction branch holds the rider");
        });
        h.onEachTick(() -> {
            if (g.ghostBody().grab.held() != p) return;
            p.zza = 1;
            p.xxa = 1;
            p.setJumping(true);
            p.setSprinting(true);
            p.setDeltaMovement(new Vec3(0.8, 0.45, 0.8));
            g.ghostBody().grab.tick();
        });
        h.runAfterDelay(24, () -> {
            check(h, g.ghostBody().grab.held() == p, "Walk/jump/swim input cannot break the player hold");
            check(h, p.distanceTo(g) < 3.5, "Corrected velocity keeps the real player at the hold point");
            h.succeed();
        });
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200) public static void hostile_ai_grabs_moving_swimmer(GameTestHelper h) {
        var f = new Fixture(h); f.water(-5, 5);
        var g = ModEntities.WATER_GHOST.get().create(f.level);
        g.setPos(Vec3.atBottomCenterOf(f.center.offset(0, 3, 0)));
        g.setNoAi(false);
        f.level.addFreshEntity(g); f.entities.add(g);
        var victim = EntityType.VILLAGER.create(f.level);
        victim.setPos(Vec3.atBottomCenterOf(f.center.offset(1, 3, 0)));
        victim.setNoAi(false);
        f.level.addFreshEntity(victim); f.entities.add(victim);
        g.setTarget(victim);
        h.onEachTick(() -> {
            if (g.ghostBody().grab.held() == victim) return;
            victim.setDeltaMovement(0.35, victim.getDeltaMovement().y, 0.2);
        });
        h.startSequence().thenWaitUntil(() -> {
            check(h, g.ghostBody().grab.held() == victim, "AI grab catches a moving swimmer");
            check(h, g.ghostBody().swimming(), "Moving-swimmer grab is in water");
        }).thenExecute(() -> victim.setDeltaMovement(0.5, 0.2, 0.5))
        .thenIdle(15)
        .thenExecute(() -> {
            check(h, g.ghostBody().grab.held() == victim, "Hold survives the swimmer trying to swim away");
            check(h, victim.distanceTo(g) < 3.5, "Swimmer cannot walk or swim out of the hold");
            check(h, g.getVoicePitch() == WaterGhostBody.HURT_DEATH_PITCH
                            && WaterGhostBody.HURT_DEATH_PITCH == 0.7F,
                    "Hostile hurt/death voice pitch matches design 0.7");
            check(h, WaterGhostBody.AMBIENT_PITCH == 0.6F, "Hostile ambient pitch matches design 0.6");
            check(h, WaterGhostBody.GRAB_PITCH == 0.5F, "Grab-start pitch matches design 0.5");
            h.succeed();
        });
    }
}
