package com.zeropointsix.eraser.gametest;

import com.mojang.authlib.GameProfile;
import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.shaxia.*;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShaxiadaoGameTests {
    private static ItemStack knife() { return ModItems.SHAXIADAO.get().getDefaultInstance(); }

    private static FakePlayer player(GameTestHelper h, ItemStack stack) {
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "KnifeQA"));
        p.setGameMode(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        p.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(EquipmentSlot.MAINHAND));
        p.setOnGround(true);
        p.moveTo(h.absolutePos(new BlockPos(1, 2, 1)), 0, 0);
        charge(p, 100);
        return p;
    }

    private static void charge(FakePlayer p, int ticks) {
        ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, p, ticks, "f_20922_");
    }

    private static Creeper creeper(GameTestHelper h) {
        Creeper c = h.spawn(EntityType.CREEPER, new BlockPos(2, 2, 2));
        c.setNoAi(true);
        return c;
    }

    private static void attack(FakePlayer player, LivingEntity target) {
        boolean before = ShaxiaConfig.ALLOW_FAKE_PLAYERS.get();
        try {
            ShaxiaConfig.ALLOW_FAKE_PLAYERS.set(true);
            player.attack(target);
        } finally {
            ShaxiaConfig.ALLOW_FAKE_PLAYERS.set(before);
        }
    }

    private static void close(GameTestHelper h, float actual, float expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 0.01F, message + ": expected " + expected + ", got " + actual);
    }

    @GameTest(template = "empty")
    public static void shaxiaFactoryAndChannels(GameTestHelper h) {
        ItemStack stack = knife();
        var e = ShaxiaEnchantments.JIJIE_SPECIAL_ATTACK.get();
        h.assertTrue(stack.getMaxDamage() == 1561 && stack.getMaxStackSize() == 1 && !stack.hasFoil(), "fixed durability and no glint");
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(e, stack) == 1 && e.getMaxLevel() == 1, "one innate level");
        h.assertTrue(!e.isDiscoverable() && !e.isTradeable() && !e.isAllowedOnBooks()
                && !e.canApplyAtEnchantingTable(stack), "no random acquisition channels");
        h.assertTrue(!e.canEnchant(new ItemStack(Items.IRON_SWORD)), "only the knife can receive the enchantment");
        h.assertTrue(!stack.canPerformAction(ToolActions.SWORD_SWEEP), "no sweeping action");
        close(h, (float) player(h, stack).getAttributeValue(Attributes.ATTACK_DAMAGE), 4, "ordinary panel");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaHostileDamageAndOneDurability(GameTestHelper h) {
        ItemStack stack = knife();
        FakePlayer p = player(h, stack);
        Creeper target = creeper(h);
        attack(p, target);
        close(h, 20 - target.getHealth(), 8.5F, "creeper receives both bonuses");
        h.assertTrue(stack.getDamageValue() == 1, "two damage segments spend one durability");
        h.assertTrue(target.invulnerableTime == 20, "first hit protection remains");
        float last = ObfuscationReflectionHelper.getPrivateValue(LivingEntity.class, target, "f_20898_");
        close(h, last, 6.5F, "second segment preserves first segment's damage bookkeeping");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaArmorAndResistance(GameTestHelper h) {
        Creeper armored = creeper(h);
        armored.getAttribute(Attributes.ARMOR).setBaseValue(20);
        attack(player(h, knife()), armored);
        close(h, 20 - armored.getHealth(), 4.145F, "only the two-point segment bypasses armor");
        Creeper resistant = creeper(h);
        resistant.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        attack(player(h, knife()), resistant);
        close(h, 20 - resistant.getHealth(), 6.8F, "resistance also reduces the armor-bypassing segment");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaAbsorptionIsNotBypassed(GameTestHelper h) {
        Creeper target = creeper(h);
        target.setAbsorptionAmount(8);
        attack(player(h, knife()), target);
        close(h, target.getAbsorptionAmount(), 0, "absorption consumed first");
        close(h, 20 - target.getHealth(), 0.5F, "remaining damage reaches health");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaPassiveAndCalmFirstHit(GameTestHelper h) {
        Mob cow = h.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        cow.setNoAi(true);
        float before = cow.getHealth();
        attack(player(h, knife()), cow);
        close(h, before - cow.getHealth(), 4, "passive target has ordinary damage");
        EnderMan calm = h.spawn(EntityType.ENDERMAN, new BlockPos(2, 2, 2));
        calm.setNoAi(true);
        before = calm.getHealth();
        attack(player(h, knife()), calm);
        close(h, before - calm.getHealth(), 4, "calm first hit stays ordinary even when that hit angers the target");
        EnderMan angry = h.spawn(EntityType.ENDERMAN, new BlockPos(2, 2, 2));
        angry.setNoAi(true);
        FakePlayer anotherPlayer = player(h, knife());
        angry.setTarget(anotherPlayer);
        before = angry.getHealth();
        attack(player(h, knife()), angry);
        close(h, before - angry.getHealth(), 8.5F, "hostility towards another player also qualifies");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaFakePlayersDeniedByDefault(GameTestHelper h) {
        h.assertTrue(!ShaxiaConfig.ALLOW_FAKE_PLAYERS.get(), "test configuration restored to the real default");
        Creeper target = creeper(h);
        player(h, knife()).attack(target);
        close(h, 20 - target.getHealth(), 4, "automation does not get special damage by default");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaCancellationAtHurtAndDamage(GameTestHelper h) {
        Creeper hurtTarget = creeper(h), damageTarget = creeper(h);
        Consumer<LivingHurtEvent> cancelHurt = e -> { if (e.getEntity() == hurtTarget) e.setCanceled(true); };
        Consumer<LivingDamageEvent> cancelDamage = e -> { if (e.getEntity() == damageTarget) e.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingHurtEvent.class, cancelHurt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDamageEvent.class, cancelDamage);
        try {
            attack(player(h, knife()), hurtTarget);
            attack(player(h, knife()), damageTarget);
            close(h, hurtTarget.getHealth(), 20, "cancelled Hurt cannot leak true damage");
            close(h, damageTarget.getHealth(), 20, "cancelled Damage cannot leak true damage");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancelHurt);
            MinecraftForge.EVENT_BUS.unregister(cancelDamage);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaTotemAndDamageAttribution(GameTestHelper h) {
        Creeper rescued = creeper(h);
        rescued.setHealth(2);
        rescued.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        attack(player(h, knife()), rescued);
        close(h, rescued.getHealth(), 1, "primary totem resurrection is not followed by extra damage");
        h.assertTrue(rescued.isAlive() && rescued.getOffhandItem().isEmpty(), "one totem consumed");
        Creeper killed = creeper(h);
        killed.setHealth(8);
        FakePlayer p = player(h, knife());
        attack(p, killed);
        h.assertTrue(!killed.isAlive() && killed.getLastDamageSource().is(ShaxiaCombat.TRUE_DAMAGE)
                && killed.getLastDamageSource().getEntity() == p, "armor-bypassing kill belongs to the original player");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaCooldownAndNoSweep(GameTestHelper h) {
        FakePlayer p = player(h, knife());
        Creeper target = creeper(h), neighbor = creeper(h);
        charge(p, 0);
        float c = p.getAttackStrengthScale(0.5F);
        attack(p, target);
        close(h, 20 - target.getHealth(), 4 * (0.2F + 0.8F * c * c) + 4.5F * c, "both bonuses scale with cooldown");
        close(h, neighbor.getHealth(), 20, "no secondary sweeping target");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaNbtNormalizationPreservesOtherData(GameTestHelper h) {
        ItemStack stack = knife();
        stack.setDamageValue(71);
        stack.getOrCreateTag().putString("custom_test", "kept");
        stack.enchant(Enchantments.UNBREAKING, 2);
        CompoundTag duplicate = new CompoundTag();
        duplicate.putString("id", ShaxiaEnchantments.ID);
        duplicate.putShort("lvl", (short) 9);
        stack.getEnchantmentTags().add(duplicate);
        ShaxiaStacks.normalize(stack, true);
        h.assertTrue(stack.getEnchantmentTags().size() == 2 && ShaxiaStacks.active(stack), "innate duplicates reduced to one");
        h.assertTrue(stack.getDamageValue() == 71 && stack.getTag().getString("custom_test").equals("kept")
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, stack) == 2, "unrelated NBT preserved");
        stack.getOrCreateTag().put("Enchantments", new ListTag());
        ShaxiaStacks.normalize(stack, false);
        h.assertTrue(!ShaxiaStacks.active(stack), "disabled restoration respects missing enchantment");
        ShaxiaStacks.normalize(stack, true);
        h.assertTrue(ShaxiaStacks.active(ItemStack.of(stack.save(new CompoundTag()))), "factory state survives save/load");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaRealGrindstonePreservesInnate(GameTestHelper h) {
        FakePlayer p = player(h, knife());
        GrindstoneMenu menu = new GrindstoneMenu(0, p.getInventory(),
                ContainerLevelAccess.create(h.getLevel(), h.absolutePos(BlockPos.ZERO)));
        menu.getSlot(0).set(knife());
        h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "innate-only knife cannot produce repeatable XP");
        ItemStack enchanted = knife();
        enchanted.enchant(Enchantments.UNBREAKING, 2);
        enchanted.enchant(Enchantments.VANISHING_CURSE, 1);
        menu.getSlot(0).set(enchanted);
        ItemStack output = menu.getSlot(2).getItem();
        h.assertTrue(ShaxiaStacks.active(output) && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, output) == 0
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.VANISHING_CURSE, output) == 1, "real menu strips ordinary enchantments only");
        menu.getSlot(2).onTake(p, output.copy());
        h.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "real take consumes inputs");
        menu.getSlot(0).set(output.copy());
        h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "result cannot be ground repeatedly for XP");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaLastDurabilityAndRepair(GameTestHelper h) {
        ItemStack last = knife();
        last.setDamageValue(1560);
        Creeper target = creeper(h);
        attack(player(h, last), target);
        close(h, 20 - target.getHealth(), 8.5F, "last point of durability delivers both segments");
        h.assertTrue(last.isEmpty(), "last hit breaks the knife once");
        ItemStack a = knife(), b = knife();
        a.setDamageValue(1400);
        b.setDamageValue(1300);
        ItemStack repaired = ShaxiaWorkstations.grindOutput(a, b);
        h.assertTrue(!repaired.isEmpty() && repaired.getDamageValue() == 1061 && ShaxiaStacks.active(repaired),
                "two damaged knives retain the innate enchantment and vanilla repair bonus");
        h.succeed();
    }

    private ShaxiadaoGameTests() {}
}
