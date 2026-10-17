package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.keeper.*;
import com.zeropointsix.eraser.registry.ModEffects;
import com.zeropointsix.eraser.registry.ModItems;
import java.util.UUID;
import io.netty.channel.embedded.EmbeddedChannel;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("keeper_tests")
@PrefixGameTestTemplate(false)
public final class KeeperGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "KeeperQA"));
        // Only network output is suppressed; teleport, damage, drops and packet handlers are vanilla.
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        connection.setProtocol(ConnectionProtocol.PLAY);
        player.connection = new ServerGamePacketListenerImpl(player.server, connection, player) {
            @Override public void send(Packet<?> packet) { }
            @Override public void send(Packet<?> packet, @Nullable PacketSendListener listener) { }
        };
        player.setGameMode(GameType.SURVIVAL);
        h.setBlock(1, 1, 1, Blocks.STONE);
        Vec3 pos = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        player.setPos(pos.x, pos.y, pos.z);
        player.setHealth(20); player.getFoodData().setFoodLevel(20);
        return player;
    }
    private static ItemStack capture(ServerPlayer p) {
        ItemStack photo = KeeperData.capture(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, photo);
        return photo;
    }
    private static ItemStack enter(GameTestHelper h, ServerPlayer p) {
        ItemStack photo = capture(p);
        h.assertTrue(KeeperSessions.enter(p, photo), "photo entry succeeds");
        return photo;
    }

    @GameTest(template = "empty") public static void cameraBindsShootsAndSerializes(GameTestHelper h) {
        var p = player(h);
        ItemStack camera = new ItemStack(ModItems.KEEPER_CAMERA.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, camera);
        h.assertTrue(KeeperData.film(camera) == 12 && camera.getMaxStackSize() == 1, "fresh camera has twelve film");
        camera.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(KeeperData.owns(camera, p) && camera.getTag().getBoolean("FirstUseShown"), "bind and once-only prompt recorded");
        h.assertTrue(KeeperData.film(camera) == 11, "one frame spent");
        var photo = p.getInventory().items.stream().filter(s -> s.is(ModItems.KEEPER_PHOTO.get())).findFirst().orElseThrow();
        h.assertTrue(KeeperData.valid(photo.getTag()) && KeeperData.owns(photo, p), "complete snapshot has owner and identity");
        h.assertTrue(photo.getTag().getFloat("Health") == 20 && photo.getTag().getInt("Food") == 20, "snapshot vitals");
        h.assertTrue(camera.is(TagKey.create(Registries.ITEM, new ResourceLocation("sound_isolating_eraser", "precious_memory"))), "precious memory tag");
        camera.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(KeeperData.film(camera) == 11, "one-second shutter cooldown");
        var reloaded = ItemStack.of(photo.save(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(ItemStack.isSameItemSameTags(photo, reloaded), "NBT survives save/load");
        for (int i = 0; i < 12; i++) {
            p.getCooldowns().removeCooldown(camera.getItem()); camera.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        }
        var ids = new java.util.HashSet<UUID>();
        for (var stack : p.getInventory().items) if (stack.is(ModItems.KEEPER_PHOTO.get())) ids.add(stack.getTag().getUUID("PhotoId"));
        h.assertTrue(ids.size() == 12 && KeeperData.film(camera) == 0, "twelve unique photos, no thirteenth free shot");
        var tab = com.zeropointsix.eraser.registry.ModCreativeTabs.QIJIE.get();
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS, true, h.getLevel().registryAccess()));
        h.assertTrue(tab.getDisplayItems().stream().anyMatch(s -> s.is(ModItems.KEEPER_CAMERA.get()))
                && tab.getDisplayItems().stream().noneMatch(s -> s.is(ModItems.KEEPER_PHOTO.get())), "creative exposes camera, not photos");
        h.succeed();
    }

    @GameTest(template = "empty") public static void filmRegeneratesOnlyAcrossDays(GameTestHelper h) {
        var camera = new ItemStack(ModItems.KEEPER_CAMERA.get());
        KeeperData.refill(camera, 50);
        camera.getTag().putInt("Film", 0);
        KeeperData.refill(camera, 50); h.assertTrue(KeeperData.film(camera) == 0, "same day no refill");
        KeeperData.refill(camera, 51); h.assertTrue(KeeperData.film(camera) == 1, "one per day");
        KeeperData.refill(camera, 100); h.assertTrue(KeeperData.film(camera) == 12, "capacity twelve");
        camera.getTag().putInt("Film", 4);
        KeeperData.refill(camera, 10); h.assertTrue(KeeperData.film(camera) == 4, "time rewind cannot refill");
        KeeperData.refill(camera, 11); h.assertTrue(KeeperData.film(camera) == 4, "rewound days cannot be collected twice");
        h.succeed();
    }

    @GameTest(template = "empty") public static void foreignOwnershipAndNoFilmDoNotSpend(GameTestHelper h) {
        var a = player(h); var b = player(h);
        ItemStack photo = capture(a);
        h.assertTrue(!KeeperSessions.enter(b, photo) && photo.getTag().getInt("Uses") == 0, "photo owner enforced");
        ItemStack camera = new ItemStack(ModItems.KEEPER_CAMERA.get());
        camera.getOrCreateTag().putUUID("Owner", a.getUUID()); camera.getTag().putInt("Film", 0);
        b.setItemInHand(InteractionHand.MAIN_HAND, camera);
        camera.use(h.getLevel(), b, InteractionHand.MAIN_HAND);
        h.assertTrue(KeeperData.film(camera) == 0 && !camera.getTag().getBoolean("FirstUseShown"), "foreign shutter changes nothing");
        a.setItemInHand(InteractionHand.MAIN_HAND, camera);
        camera.use(h.getLevel(), a, InteractionHand.MAIN_HAND);
        h.assertTrue(KeeperData.film(camera) == 0, "empty film cannot shoot");
        h.succeed();
    }

    @GameTest(template = "empty") public static void photoUseNeedsOneSecondAndCancelsCleanly(GameTestHelper h) {
        var p = player(h); var photo = capture(p);
        h.assertTrue(photo.getUseDuration() == 20 && photo.getUseAnimation() == UseAnim.SPYGLASS, "twenty-tick focus");
        photo.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.isUsingItem() && !KeeperSessions.active(p), "holding does not enter yet");
        p.releaseUsingItem();
        h.assertTrue(photo.getTag().getInt("Uses") == 0 && !KeeperSessions.active(p), "cancel is free");
        photo.finishUsingItem(h.getLevel(), p);
        h.assertTrue(KeeperSessions.active(p) && photo.getTag().getInt("Uses") == 1, "completion enters once");
        KeeperSessions.exit(p); h.succeed();
    }

    @GameTest(template = "empty") public static void sessionReturnRestoresOriginAndStartsSharedCooldown(GameTestHelper h) {
        var p = player(h); var photo = capture(p);
        p.setPos(p.getX() + 2, p.getY(), p.getZ()); Vec3 origin = p.position();
        p.setHealth(13); p.getFoodData().setFoodLevel(9); p.getFoodData().setSaturation(3);
        h.assertTrue(KeeperSessions.enter(p, photo), "entry");
        h.assertTrue(p.getEffect(ModEffects.IN_PHOTO.get()).getDuration() == 300 && !p.getEffect(ModEffects.IN_PHOTO.get()).showIcon(), "15 seconds; no HUD potion icon");
        h.assertTrue(p.hasEffect(MobEffects.INVISIBILITY) && !p.getAbilities().invulnerable, "invisible without ability mutation");
        KeeperSessions.exit(p);
        h.assertTrue(p.position().distanceToSqr(origin) < 0.01 && !KeeperSessions.active(p), "exact origin restored");
        h.assertTrue(p.getHealth() == 13 && p.getFoodData().getFoodLevel() == 9, "entry vitals restored, not captured vitals");
        h.assertTrue(!p.hasEffect(MobEffects.INVISIBILITY) && KeeperSessions.cooling(p), "own invisibility removed; shared cooldown");
        h.assertTrue(!KeeperSessions.enter(p, KeeperData.capture(p)), "another photo cannot bypass cooldown");
        var traveler = player(h); var cross = capture(traveler); var start = traveler.position();
        cross.getTag().putString("Dim", "minecraft:the_nether"); cross.getTag().putDouble("Y", 130);
        h.assertTrue(KeeperSessions.enter(traveler, cross) && traveler.level().dimension() == Level.NETHER, "real cross-dimension entry");
        KeeperSessions.exit(traveler);
        h.assertTrue(traveler.level() == h.getLevel() && traveler.position().distanceToSqr(start) < 0.01, "real cross-dimension return");
        traveler.discard();
        h.succeed();
    }

    @GameTest(template = "empty") public static void tearUsesSnapshotAndKeepsCurrentPosition(GameTestHelper h) {
        var p = player(h); p.setHealth(17); p.getFoodData().setFoodLevel(12); p.getFoodData().setSaturation(4);
        var photo = capture(p); p.setHealth(5);
        h.assertTrue(KeeperSessions.enter(p, photo), "entry");
        p.setPos(p.getX() + 1, p.getY(), p.getZ()); Vec3 point = p.position();
        photo.getTag().putFloat("Health", 1);
        KeeperSessions.tear(p, photo);
        h.assertTrue(photo.isEmpty() && !KeeperSessions.active(p), "photo consumed exactly once");
        h.assertTrue(p.position().distanceToSqr(point) < 0.01 && p.getHealth() == 17, "current location retained; immutable entry snapshot used");
        h.assertTrue(p.getFoodData().getFoodLevel() == 12 && p.getFoodData().getSaturationLevel() == 4, "snapshot hunger restored");
        h.assertTrue(KeeperSessions.cooling(p), "tear also cools all photos");
        h.succeed();
    }

    @GameTest(template = "empty") public static void eighthEntryCanStillReturnOrTear(GameTestHelper h) {
        var p = player(h); var photo = capture(p); photo.getTag().putInt("Uses", 7);
        h.assertTrue(KeeperSessions.enter(p, photo) && KeeperData.blank(photo), "eighth entry succeeds and fades white");
        h.assertTrue(KeeperSessions.current(p, photo), "blank active photo retains session identity");
        p.setShiftKeyDown(true); photo.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(photo.isEmpty() && !KeeperSessions.active(p), "blank eighth photo can be torn");
        var q = player(h); var last = capture(q); last.getTag().putInt("Uses", 7);
        h.assertTrue(KeeperSessions.enter(q, last), "second eighth entry");
        last.use(h.getLevel(), q, InteractionHand.MAIN_HAND);
        h.assertTrue(!KeeperSessions.active(q) && !last.isEmpty() && KeeperData.blank(last), "normal right-click exits blank photo");
        h.succeed();
    }

    @GameTest(template = "empty") public static void invalidAndMissingLocationsAreFreeFailures(GameTestHelper h) {
        var p = player(h); var photo = capture(p); var original = photo.getTag().copy();
        photo.getTag().putString("Dim", "keeper_tests:removed");
        h.assertTrue(!KeeperSessions.enter(p, photo), "missing dimension denied");
        photo.setTag(original.copy()); photo.getTag().putDouble("Y", 5000);
        h.assertTrue(!KeeperSessions.enter(p, photo), "no safe spot denied");
        photo.setTag(original.copy()); photo.getTag().putDouble("X", Double.NaN);
        h.assertTrue(!KeeperSessions.enter(p, photo), "malformed coordinates denied");
        photo.setTag(original.copy()); photo.getTag().putDouble("Yaw", Double.MAX_VALUE);
        h.assertTrue(!KeeperSessions.enter(p, photo), "double-to-float angle overflow denied");
        h.assertTrue(!KeeperSessions.active(p) && !KeeperSessions.cooling(p) && photo.getTag().getInt("Uses") == 0, "no failed entry spends use or cooldown");
        var trapped = player(h); var refuge = capture(trapped);
        Vec3 blockedOrigin = trapped.position().add(128, 40, 0);
        trapped.setPos(blockedOrigin);
        h.assertTrue(KeeperSessions.enter(trapped, refuge), "entry from later-blocked origin");
        BlockPos center = BlockPos.containing(blockedOrigin);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) for (int y = -9; y <= 10; y++)
            h.getLevel().setBlockAndUpdate(center.offset(x, y, z), Blocks.STONE.defaultBlockState());
        KeeperSessions.exit(trapped);
        h.assertTrue(!KeeperSessions.active(trapped) && trapped.position().distanceToSqr(blockedOrigin) > 64
                && trapped.level().noCollision(trapped), "filled return region falls back to a verified safe location");
        h.succeed();
    }

    @GameTest(template = "empty") public static void safeSpotPreservesCoordinatesAndChecksHeadroom(GameTestHelper h) {
        var p = player(h); Vec3 origin = p.position();
        h.assertTrue(KeeperSessions.safeSpot(h.getLevel(), p, origin) != null, "standing space found");
        var airborne = origin.add(0.23, 40.1, 0.17);
        // This point is outside the template; establish and restore its empty-air fixture.
        var previous = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        BlockPos feet = BlockPos.containing(airborne);
        try {
            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 2, 1))) {
                BlockPos at = pos.immutable();
                previous.put(at, h.getLevel().getBlockState(at));
                h.getLevel().setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            }
            h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox().move(airborne.subtract(origin))),
                    "airborne fixture has clear player headroom");
            h.assertTrue(airborne.equals(KeeperSessions.safeSpot(h.getLevel(), p, airborne)), "original airborne coordinate preserved without floor requirement");
        } finally {
            previous.forEach((pos, state) -> h.getLevel().setBlockAndUpdate(pos, state));
        }
        h.setBlock(1, 2, 1, Blocks.WATER); h.setBlock(1, 3, 1, Blocks.WATER);
        var underwater = capture(p);
        h.assertTrue(KeeperSessions.enter(p, underwater) && p.position().equals(origin), "underwater photo enters at exact coordinate");
        KeeperSessions.exit(p);
        h.setBlock(1, 2, 1, Blocks.STONE); h.setBlock(1, 3, 1, Blocks.STONE);
        Vec3 moved = KeeperSessions.safeSpot(h.getLevel(), p, origin);
        h.assertTrue(moved == null || moved.distanceToSqr(origin) > 0.1, "blocked spot never returned");
        h.succeed();
    }

    @GameTest(template = "empty") public static void expiryAndLoginRecoveryReturnSafely(GameTestHelper h) {
        var p = player(h); enter(h, p);
        for (int tick = 0; tick < 299; tick++) p.doTick();
        h.assertTrue(KeeperSessions.active(p), "session still active after 299 real player ticks");
        p.doTick();
        h.assertTrue(!KeeperSessions.active(p) && KeeperSessions.cooling(p), "effect expires and returns on tick 300");
        var q = player(h); enter(h, q);
        q.getPersistentData().getCompound(KeeperData.SESSION).putLong("Started", q.server.overworld().getGameTime() - 24000);
        q.removeEffect(ModEffects.IN_PHOTO.get());
        // GameTest players have no Netty channel; Forge TierSortingRegistry NPEs on EVENT_BUS.post.
        // Invoke the production login subscriber directly so recovery still runs for real.
        KeeperEvents.login(new PlayerEvent.PlayerLoggedInEvent(q));
        h.assertTrue(!KeeperSessions.active(q) && KeeperSessions.cooling(q), "persisted session recovered without effect");
        h.assertTrue(!q.hasEffect(MobEffects.INVISIBILITY), "offline world-time advance leaves no session invisibility");
        q.getCooldowns().removeCooldown(ModItems.KEEPER_PHOTO.get()); KeeperSessions.restoreCooldown(q);
        h.assertTrue(q.getCooldowns().isOnCooldown(ModItems.KEEPER_PHOTO.get()), "login restores persistent cooldown");
        h.succeed();
    }

    @GameTest(template = "empty") public static void deathCleansWithoutResurrection(GameTestHelper h) {
        var p = player(h); enter(h, p); p.kill(); KeeperSessions.tick(p);
        h.assertTrue(!KeeperSessions.active(p) && p.getHealth() == 0 && !p.hasEffect(ModEffects.IN_PHOTO.get()), "dead player never healed or returned");
        h.succeed();
    }

    @GameTest(template = "empty") public static void existingInvisibilityAndOtherEffectsSurvive(GameTestHelper h) {
        var p = player(h);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 1200, 0));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1000, 1));
        enter(h, p); KeeperSessions.exit(p);
        h.assertTrue(p.getEffect(MobEffects.INVISIBILITY).getDuration() == 1200, "existing long invisibility preserved");
        h.assertTrue(p.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1, "unrelated effect untouched");
        var q = player(h); q.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 1));
        enter(h, q); KeeperSessions.exit(q);
        h.assertTrue(!q.getEffect(MobEffects.INVISIBILITY).save(new net.minecraft.nbt.CompoundTag()).contains("HiddenEffect"), "no session invisibility tail under an existing stronger effect");
        h.succeed();
    }

    @GameTest(template = "empty") public static void protectedPlayerRejectsDamageTargetsAndWorldActions(GameTestHelper h) {
        var p = player(h);
        for (int tick = 0; tick < 61; tick++) p.tick();
        h.assertTrue(p.hurt(h.getLevel().damageSources().generic(), 2) && p.getHealth() < 20, "non-session control is damageable");
        p.invulnerableTime = 0;
        var photo = enter(h, p);
        var mob = h.spawn(EntityType.ZOMBIE, 1, 2, 1);
        mob.setTarget(p); h.assertTrue(mob.getTarget() != p, "new hostile target denied");
        var damage = new LivingAttackEvent(p, h.getLevel().damageSources().generic(), 10);
        h.assertTrue(MinecraftForge.EVENT_BUS.post(damage), "damage event cancelled");
        float health = p.getHealth(); p.hurt(h.getLevel().damageSources().generic(), 10);
        h.assertTrue(p.getHealth() == health, "real server-player damage leaves health unchanged");
        h.assertTrue(MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(p, mob)), "outgoing attack denied");
        var pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.assertTrue(MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(h.getLevel(), pos, Blocks.STONE.defaultBlockState(), p)), "block break denied");
        h.assertTrue(MinecraftForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(p, Level.NETHER)), "portal travel denied");
        var item = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.DIAMOND));
        h.assertTrue(MinecraftForge.EVENT_BUS.post(new EntityItemPickupEvent(p, item)), "pickup denied");
        h.assertTrue(!p.drop(false) && p.getMainHandItem() == photo, "native drop hook rejects before inventory removal");
        h.assertTrue(p.isIgnoringBlockTriggers(), "pressure plates and tripwire ignore photo players");
        h.setBlock(1, 1, 1, Blocks.FARMLAND);
        Blocks.FARMLAND.fallOn(h.getLevel(), Blocks.FARMLAND.defaultBlockState(), pos, p, 4);
        h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.FARMLAND), "native trample path leaves farmland intact");
        KeeperSessions.exit(p);
        h.assertTrue(!p.isIgnoringBlockTriggers(), "block triggers restored on exit");
        mob.discard(); h.succeed();
    }

    @GameTest(template = "empty") public static void foreignPhotoCannotExitAndVoidRescues(GameTestHelper h) {
        var p = player(h); var photo = enter(h, p);
        var another = KeeperData.capture(p);
        KeeperSessions.tear(p, another);
        h.assertTrue(KeeperSessions.active(p) && another.getCount() == 1, "different photo cannot tear current session");
        p.setPos(p.getX(), h.getLevel().getMinBuildHeight() - 11, p.getZ()); KeeperSessions.tick(p);
        h.assertTrue(p.getY() >= h.getLevel().getMinBuildHeight() && KeeperSessions.active(p), "void rescue returns to photo point");
        h.assertTrue(photo.getTag().getInt("Uses") == 1, "void rescue never spends another visit");
        KeeperSessions.exit(p); h.succeed();
    }

    private KeeperGameTests() { }
}
