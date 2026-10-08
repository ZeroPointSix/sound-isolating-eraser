package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.pill.*;
import com.zeropointsix.eraser.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillGameTests {
    private static FakePlayer player(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "PillQA"));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static PillState state(FakePlayer player) {
        return player.getCapability(PillProvider.CAPABILITY).resolve().orElseThrow();
    }

    private static ItemStack take(FakePlayer player, ItemStack pack) {
        return pack.finishUsingItem(player.level(), player);
    }

    private static void expire(FakePlayer player) {
        state(player).consume(1, 200, 1, 12);
        PillEffects.refresh(player, state(player));
        PillEffects.tick(player, state(player));
    }

    @GameTest(template = "empty")
    public static void pillUseAnimationCancelAndConsume(GameTestHelper h) {
        FakePlayer player = player(h);
        ItemStack pack = new ItemStack(ModItems.ENHANCEMENT_PILL_PACK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pack);
        h.assertTrue(pack.getMaxStackSize() == 1 && pack.getMaxDamage() == 12, "one pack contains twelve uses");
        h.assertTrue(pack.getUseDuration() == 32 && pack.getUseAnimation() == UseAnim.EAT, "32-tick swallowing");
        pack.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.isUsingItem(), "full hunger still permits medicine");
        player.releaseUsingItem();
        h.assertTrue(pack.getDamageValue() == 0 && !state(player).active(), "cancel does not spend a pill");
        ItemStack result = take(player, pack);
        h.assertTrue(result == pack && pack.getDamageValue() == 1, "completion consumes exactly one dose");
        h.assertTrue(state(player).remainingTicks() == 48000, "authoritative forty-minute duration");
        for (MobEffect effect : new MobEffect[]{MobEffects.DAMAGE_BOOST, MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED}) {
            h.assertTrue(player.getEffect(effect).getAmplifier() == 1, "level II boost");
            h.assertTrue(!player.getEffect(effect).isVisible() && player.getEffect(effect).showIcon(), "icons without potion particles");
        }
        h.assertTrue(player.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() == 0, "resistance I");
        h.assertTrue(!pack.isEnchantable() && !pack.getItem().isRepairable(pack) && !pack.hasFoil(), "finite unrepairable unenchanted pack");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillCleansesAndDeniesTaggedEffectsOnly(GameTestHelper h) {
        FakePlayer a = player(h), b = player(h);
        a.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        take(a, new ItemStack(ModItems.ENHANCEMENT_PILL_PACK.get()));
        h.assertTrue(!a.hasEffect(MobEffects.POISON), "existing poison cleansed");
        MobEffect[] suppressed = {MobEffects.DARKNESS, MobEffects.BLINDNESS, MobEffects.CONFUSION,
                MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN,
                MobEffects.POISON, MobEffects.WITHER, MobEffects.HUNGER, MobEffects.LEVITATION};
        for (MobEffect effect : suppressed) {
            h.assertTrue(PillEffects.suppressed(effect), "data tag includes suppression target");
            h.assertTrue(!a.addEffect(new MobEffectInstance(effect, 100)), "active player rejects tagged effect");
        }
        h.assertTrue(a.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100)), "untagged effects are allowed");
        h.assertTrue(b.addEffect(new MobEffectInstance(MobEffects.POISON, 100)) && !state(b).active(), "another player remains unaffected");
        h.assertTrue(!a.getAbilities().invulnerable && a.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() == 0,
                "pill never grants invulnerability; real damage is checked by the native-client test");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillCliffAndWithdrawalExpiryAreExact(GameTestHelper h) {
        FakePlayer player = player(h);
        PillState state = state(player);
        state.consume(2, 3, 1, 12);
        PillEffects.refresh(player, state);
        PillEffects.tick(player, state);
        h.assertTrue(state.remainingTicks() == 1 && !state.withdrawing(), "last active tick remains active");
        PillEffects.tick(player, state);
        h.assertTrue(!state.active() && state.withdrawalTicks() == 3, "withdrawal begins without decrementing its first tick");
        h.assertTrue(!player.hasEffect(MobEffects.DAMAGE_BOOST) && !player.hasEffect(MobEffects.MOVEMENT_SPEED), "boosts removed at cliff");
        h.assertTrue(player.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 1, "slowness II immediately");
        h.assertTrue(player.hasEffect(MobEffects.DARKNESS) && player.hasEffect(MobEffects.DIG_SLOWDOWN)
                && player.hasEffect(MobEffects.WEAKNESS) && !player.hasEffect(MobEffects.CONFUSION), "default withdrawal bundle");
        h.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.POISON, 100)), "immunity ends at cliff");
        for (int i = 0; i < 3; i++) PillEffects.tick(player, state);
        h.assertTrue(!state.withdrawing() && !player.hasEffect(MobEffects.DARKNESS)
                && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "withdrawal ends without lingering debuffs");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillMilkDoesNotChangeAuthoritativeClock(GameTestHelper h) {
        FakePlayer player = player(h);
        take(player, new ItemStack(ModItems.ENHANCEMENT_PILL_PACK.get()));
        new ItemStack(Items.MILK_BUCKET).finishUsingItem(h.getLevel(), player);
        h.assertTrue(state(player).remainingTicks() == 48000 && !player.hasEffect(MobEffects.DAMAGE_BOOST), "milk clears presentation only");
        player.tickCount = 40;
        PillEffects.tick(player, state(player));
        h.assertTrue(player.hasEffect(MobEffects.DAMAGE_BOOST) && state(player).remainingTicks() == 47999, "boost presentation restored");
        expire(player);
        new ItemStack(Items.MILK_BUCKET).finishUsingItem(h.getLevel(), player);
        PillEffects.milkFinished(player);
        h.assertTrue(state(player).withdrawing() && !player.hasEffect(MobEffects.DARKNESS), "default milk cannot cure withdrawal");
        for (int i = 1; i <= 40; i++) {
            player.tickCount = i;
            PillEffects.tick(player, state(player));
        }
        h.assertTrue(state(player).withdrawalTicks() == 160 && player.hasEffect(MobEffects.DARKNESS), "withdrawal returns within forty ticks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillRedoseRefreshesWithoutStacking(GameTestHelper h) {
        FakePlayer player = player(h);
        ItemStack pack = new ItemStack(ModItems.ENHANCEMENT_PILL_PACK.get());
        take(player, pack);
        PillEffects.tick(player, state(player));
        take(player, pack);
        h.assertTrue(state(player).remainingTicks() == 48000 && pack.getDamageValue() == 2, "redose refreshes not adds");
        h.assertTrue(player.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier() == 1, "redose never doubles strength");
        expire(player);
        take(player, pack);
        h.assertTrue(state(player).active() && !state(player).withdrawing() && pack.getDamageValue() == 3, "dependency loop consumes another pill");
        h.assertTrue(!player.hasEffect(MobEffects.DARKNESS) && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "withdrawal removed immediately");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillTwelfthDoseBecomesInertEmptyPack(GameTestHelper h) {
        FakePlayer player = player(h);
        ItemStack pack = new ItemStack(ModItems.ENHANCEMENT_PILL_PACK.get());
        for (int i = 0; i < 12; i++) pack = take(player, pack);
        h.assertTrue(pack.is(ModItems.EMPTY_PILL_PACK.get()) && pack.getCount() == 1, "twelfth dose retains the empty heirloom");
        h.assertTrue(pack.getUseAnimation() == UseAnim.NONE && pack.getUseDuration() == 0, "empty pack cannot be eaten");
        h.assertTrue(state(player).active() && state(player).packDamage() == 12, "last pill still grants full effect and empty HUD icon");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillCapabilitySerializationAndDeathClone(GameTestHelper h) {
        FakePlayer original = player(h), restored = player(h), reborn = player(h);
        take(original, new ItemStack(ModItems.ENHANCEMENT_PILL_PACK.get()));
        PillEffects.tick(original, state(original));
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        h.assertTrue(saved.getCompound("ForgeCaps").contains(ModMain.MOD_ID + ":pill_effect"), "real player NBT contains capability");
        restored.load(saved);
        h.assertTrue(state(restored).remainingTicks() == 47999, "save and reload restores remaining ticks");
        expire(original);
        original.invalidateCaps();
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(reborn, original, true));
        h.assertTrue(state(reborn).withdrawalTicks() == 200, "death cannot reset withdrawal");
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(reborn, false));
        h.assertTrue(reborn.hasEffect(MobEffects.DARKNESS), "respawn restores withdrawal presentation");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillNonDeathCloneAndDimensionChange(GameTestHelper h) {
        FakePlayer original = player(h), returned = player(h);
        take(original, new ItemStack(ModItems.ENHANCEMENT_PILL_PACK.get()));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(returned, original, false));
        h.assertTrue(state(returned).remainingTicks() == 48000, "End return clone preserves active clock");
        returned.removeAllEffects();
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(returned,
                net.minecraft.world.level.Level.OVERWORLD, net.minecraft.world.level.Level.NETHER));
        h.assertTrue(state(returned).remainingTicks() == 48000 && returned.hasEffect(MobEffects.DIG_SPEED), "dimension event restores presentation without resetting clock");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillCreativeMilkAndOptionalEffectsConfiguration(GameTestHelper h) {
        boolean oldConsume = CommonConfig.CONSUME_IN_CREATIVE.get();
        boolean oldMilk = CommonConfig.ALLOW_MILK_CURE.get();
        boolean oldNausea = CommonConfig.WITHDRAWAL_NAUSEA.get();
        boolean oldDarkness = CommonConfig.WITHDRAWAL_DARKNESS.get();
        try {
            FakePlayer player = player(h);
            player.setGameMode(GameType.CREATIVE);
            ItemStack pack = new ItemStack(ModItems.ENHANCEMENT_PILL_PACK.get());
            CommonConfig.CONSUME_IN_CREATIVE.set(false);
            take(player, pack);
            h.assertTrue(pack.getDamageValue() == 0 && state(player).active(), "creative default does not consume");
            CommonConfig.CONSUME_IN_CREATIVE.set(true);
            take(player, pack);
            h.assertTrue(pack.getDamageValue() == 1, "creative consumption can be enabled");
            CommonConfig.WITHDRAWAL_NAUSEA.set(true);
            CommonConfig.WITHDRAWAL_DARKNESS.set(false);
            expire(player);
            h.assertTrue(player.hasEffect(MobEffects.CONFUSION) && !player.hasEffect(MobEffects.DARKNESS), "optional withdrawal effects follow config");
            CommonConfig.ALLOW_MILK_CURE.set(true);
            ItemStack milk = new ItemStack(Items.MILK_BUCKET);
            ItemStack beforeUse = milk.copy();
            ItemStack result = milk.finishUsingItem(h.getLevel(), player);
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, beforeUse, 0, result));
            h.assertTrue(!state(player).withdrawing(), "debug option cures authoritative withdrawal");
            PillEffects.tick(player, state(player));
            h.assertTrue(!player.hasEffect(MobEffects.CONFUSION), "cured withdrawal stays gone");
        } finally {
            CommonConfig.CONSUME_IN_CREATIVE.set(oldConsume);
            CommonConfig.ALLOW_MILK_CURE.set(oldMilk);
            CommonConfig.WITHDRAWAL_NAUSEA.set(oldNausea);
            CommonConfig.WITHDRAWAL_DARKNESS.set(oldDarkness);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillCannotRefillOnAnvilButCanBeRenamed(GameTestHelper h) {
        FakePlayer player = player(h);
        ItemStack pack = new ItemStack(ModItems.ENHANCEMENT_PILL_PACK.get());
        pack.setDamageValue(10);
        var repair = new net.minecraftforge.event.AnvilUpdateEvent(pack, pack.copy(), "", 0, player);
        MinecraftForge.EVENT_BUS.post(repair);
        h.assertTrue(repair.isCanceled(), "anvil cannot refill doses from another pack");
        var rename = new net.minecraftforge.event.AnvilUpdateEvent(pack, ItemStack.EMPTY, "Keepsake", 0, player);
        MinecraftForge.EVENT_BUS.post(rename);
        h.assertTrue(!rename.isCanceled(), "renaming remains available without a repair ingredient");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillStatusPacketRoundTripAndMalformedState(GameTestHelper h) {
        PillState state = new PillState();
        state.consume(48000, 12000, 4, 12);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PillNetwork.Status expected = PillNetwork.Status.from(state);
            expected.encode(buffer);
            h.assertTrue(expected.equals(PillNetwork.Status.decode(buffer)), "S2C packet round trip includes private timer and pack image");
        } finally { buffer.release(); }
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("remainingTicks", -1);
        malformed.putInt("withdrawalTicks", -99);
        malformed.putInt("packUses", 0);
        state.load(malformed);
        h.assertTrue(!state.active() && !state.withdrawing() && state.packUses() == 1, "malformed persisted state cannot underflow");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillDoesNotRemoveUnrelatedLongerBuff(GameTestHelper h) {
        FakePlayer player = player(h);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2000, 3));
        expire(player);
        h.assertTrue(player.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier() == 3,
                "withdrawal removes pill presentation, not another stronger potion");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillRestoresWeakerHiddenBuffAtCliff(GameTestHelper h) {
        FakePlayer player = player(h);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2000, 0));
        state(player).consume(2, 3, 1, 12);
        PillEffects.refresh(player, state(player));
        h.assertTrue(player.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier() == 1, "pill overlays weaker potion");
        PillEffects.tick(player, state(player));
        PillEffects.tick(player, state(player));
        MobEffectInstance restored = player.getEffect(MobEffects.DAMAGE_BOOST);
        h.assertTrue(restored != null && restored.getAmplifier() == 0 && restored.getDuration() == 1998,
                "cliff restores weaker potion with its elapsed duration, not the pill's hidden chain");
        h.assertTrue(restored.isVisible(), "external potion keeps its original particle setting");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillPreservesSameLevelShortExternalBuffs(GameTestHelper h) {
        FakePlayer before = player(h), during = player(h);
        before.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 10, 1));
        for (FakePlayer player : new FakePlayer[]{before, during}) {
            state(player).consume(2, 3, 1, 12);
            PillEffects.refresh(player, state(player));
        }
        during.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 10, 1));
        for (FakePlayer player : new FakePlayer[]{before, during}) {
            PillEffects.tick(player, state(player));
            PillEffects.tick(player, state(player));
            MobEffectInstance external = player.getEffect(MobEffects.DAMAGE_BOOST);
            h.assertTrue(external != null && external.getAmplifier() == 1 && external.getDuration() == 8,
                    "short same-level potion survives whether supplied before or during the pill");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillWithdrawalExpiryPreservesExternalDebuff(GameTestHelper h) {
        FakePlayer player = player(h);
        state(player).consume(1, 3, 1, 12);
        PillEffects.refresh(player, state(player));
        PillEffects.tick(player, state(player));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
        for (int i = 0; i < 3; i++) PillEffects.tick(player, state(player));
        MobEffectInstance external = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(!state(player).withdrawing() && external != null && external.getDuration() == 17,
                "withdrawal expiry does not cure an independently applied slowness potion");
        h.assertTrue(!player.hasEffect(MobEffects.WEAKNESS), "pill-only withdrawal still ends exactly");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillMilkNeverResurrectsHiddenExternalBuff(GameTestHelper h) {
        FakePlayer player = player(h);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2000, 0));
        state(player).consume(2, 3, 1, 12);
        PillEffects.refresh(player, state(player));
        new ItemStack(Items.MILK_BUCKET).finishUsingItem(h.getLevel(), player);
        h.assertTrue(!player.hasEffect(MobEffects.DAMAGE_BOOST) && state(player).active(),
                "milk removes the complete visible and hidden presentation, not the authoritative clock");
        PillEffects.tick(player, state(player));
        PillEffects.tick(player, state(player));
        h.assertTrue(!player.hasEffect(MobEffects.DAMAGE_BOOST), "cleansed external potion must not return at the cliff");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillEffectOwnershipSurvivesReloadButNotDeath(GameTestHelper h) {
        FakePlayer original = player(h), restored = player(h), reborn = player(h);
        original.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2000, 0));
        state(original).consume(2, 3, 1, 12);
        PillEffects.refresh(original, state(original));
        PillEffects.tick(original, state(original));
        restored.load(original.saveWithoutId(new CompoundTag()));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(restored));
        PillEffects.tick(restored, state(restored));
        MobEffectInstance external = restored.getEffect(MobEffects.DAMAGE_BOOST);
        h.assertTrue(external != null && external.getAmplifier() == 0 && external.getDuration() == 1998,
                "save/load preserves ownership and remaining external duration");
        original.invalidateCaps();
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(reborn, original, true));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(reborn, false));
        PillEffects.tick(reborn, state(reborn));
        h.assertTrue(state(reborn).withdrawing() && !reborn.hasEffect(MobEffects.DAMAGE_BOOST),
                "death preserves the phase but does not revive an old external potion");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pillPreservesExternalHiddenChainAndExpiry(GameTestHelper h) {
        FakePlayer player = player(h), expired = player(h);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2000, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20, 2));
        expired.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1, 0));
        for (FakePlayer target : new FakePlayer[]{player, expired}) {
            state(target).consume(2, 3, 1, 12);
            PillEffects.refresh(target, state(target));
            PillEffects.tick(target, state(target));
            PillEffects.tick(target, state(target));
        }
        MobEffectInstance external = player.getEffect(MobEffects.DAMAGE_BOOST);
        CompoundTag hidden = external.save(new CompoundTag()).getCompound("HiddenEffect");
        h.assertTrue(external.getAmplifier() == 2 && external.getDuration() == 18
                && hidden.getInt("Duration") == 1998 && hidden.getByte("Amplifier") == 0,
                "an existing external hidden chain is deep-copied and ages with the visible layer");
        h.assertTrue(!expired.hasEffect(MobEffects.DAMAGE_BOOST), "expired external effects are never resurrected");
        h.succeed();
    }

    private PillGameTests() { }
}
