package com.zeropointsix.eraser.gametest;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.registry.ModItems;
import com.zeropointsix.eraser.shaxia.ShaxiaConfig;
import com.zeropointsix.eraser.shaxia.ShaxiaEnchantments;
import com.zeropointsix.eraser.shaxia.ShaxiaStacks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModMain.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShaxiaAcceptanceGameTests {
    private static ItemStack specimen() {
        ItemStack stack = ModItems.SHAXIADAO.get().getDefaultInstance();
        stack.setDamageValue(71);
        stack.setHoverName(Component.literal("circulation specimen"));
        stack.enchant(Enchantments.UNBREAKING, 2);
        stack.getOrCreateTag().putString("qa_ownerless", UUID.randomUUID().toString());
        return stack;
    }

    private static Player player(GameTestHelper h) {
        Player player = h.makeMockSurvivalPlayer();
        player.setUUID(UUID.randomUUID());
        player.moveTo(h.absolutePos(new BlockPos(1, 2, 1)), 0, 0);
        return player;
    }

    private static void preserved(GameTestHelper h, ItemStack actual, ItemStack expected, String phase) {
        h.assertTrue(ShaxiaStacks.active(actual) && actual.getCount() == 1
                && ItemStack.isSameItemSameTags(actual, expected), phase + " preserves damage, name and all enchantments/NBT");
    }

    @GameTest(template = "empty")
    public static void shaxiaConfigSandboxDoesNotAliasLiveSections(GameTestHelper h) {
        var live = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER).stream()
                .filter(config -> config.getSpec() == ShaxiaConfig.SPEC).findFirst().orElseThrow().getConfigData();
        double before = ShaxiaConfig.TRUE_DAMAGE.get();
        List<? extends String> include = List.copyOf(ShaxiaConfig.INCLUDE.get());
        try (GameTestConfigs.LiveConfig ignored = GameTestConfigs.sandbox(ShaxiaConfig.SPEC)) {
            ShaxiaConfig.TRUE_DAMAGE.set(before + 1);
            ShaxiaConfig.INCLUDE.set(List.of("bat"));
            h.assertTrue(((Number) live.get("combat.trueDamage")).doubleValue() == before,
                    "nested numeric mutation cannot change live file-backed config");
            h.assertTrue(live.get("target.includeEntityTypes").equals(include),
                    "nested list replacement cannot change live file-backed config");
        }
        h.assertTrue(ShaxiaConfig.TRUE_DAMAGE.get() == before && ShaxiaConfig.INCLUDE.get().equals(include),
                "closing the sandbox restores values without manual resets");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaDeathDropCanBePickedUpByAnotherPlayer(GameTestHelper h) {
        h.assertTrue(!h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY), "death test requires normal inventory drops");
        // The lightweight Player mock does not spawn dropped entities; exercise ServerPlayer's real death path.
        var owner = h.makeMockServerPlayerInLevel();
        Player recipient = player(h);
        owner.setGameMode(GameType.SURVIVAL);
        owner.moveTo(h.absolutePos(new BlockPos(1, 2, 1)), 0, 0);
        ItemStack stack = specimen(), expected = stack.copy();
        try {
            owner.getInventory().setItem(0, stack);
            owner.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            h.assertTrue(!owner.isAlive() && owner.getInventory().getItem(0).isEmpty(), "death clears the original inventory");
            List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(3),
                    item -> ItemStack.isSameItemSameTags(item.getItem(), expected));
            h.assertTrue(drops.size() == 1, "death produces exactly one matching knife, with no soulbound retention");
            ItemEntity drop = drops.get(0);
            preserved(h, drop.getItem(), expected, "death drop");
            drop.setNoPickUpDelay();
            drop.playerTouch(recipient);
            h.assertTrue(drop.isRemoved(), "a different player can pick up the unbound knife");
            preserved(h, recipient.getInventory().getItem(0), expected, "other-player pickup");
        } finally {
            h.getLevel().getServer().getPlayerList().remove(owner);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaChestMenuSaveLoadAndTransfer(GameTestHelper h) {
        Player owner = player(h), recipient = player(h);
        ItemStack stack = specimen(), expected = stack.copy();
        owner.getInventory().setItem(0, stack);
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
        ChestMenu deposit = ChestMenu.threeRows(0, owner.getInventory(), chest);
        deposit.quickMoveStack(owner, 54);
        h.assertTrue(owner.getInventory().getItem(0).isEmpty(), "chest menu transfer removes the original item");
        preserved(h, chest.getItem(0), expected, "chest deposit");
        CompoundTag saved = chest.saveWithFullMetadata();
        chest.clearContent();
        chest.load(saved);
        preserved(h, chest.getItem(0), expected, "block entity save/load");
        ChestMenu withdraw = ChestMenu.threeRows(1, recipient.getInventory(), chest);
        withdraw.quickMoveStack(recipient, 0);
        h.assertTrue(chest.isEmpty(), "second player can remove the stored knife");
        var found = recipient.getInventory().items.stream().filter(ShaxiaStacks::isKnife).toList();
        h.assertTrue(found.size() == 1, "storage transfer does not duplicate the knife");
        preserved(h, found.get(0), expected, "chest withdrawal");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaTestOnlyMerchantExchangePreservesKnife(GameTestHelper h) {
        Player buyer = player(h);
        Villager merchant = h.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        merchant.setNoAi(true);
        merchant.setTradingPlayer(buyer);
        ItemStack expected = specimen();
        // Fixture only: the production mod must NOT add survival acquisition through villager offers.
        MerchantOffer offer = new MerchantOffer(new ItemStack(Items.EMERALD, 3), expected.copy(), 4, 1, 0);
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        merchant.setOffers(offers);
        MerchantMenu menu = new MerchantMenu(0, buyer.getInventory(), merchant);
        menu.getSlot(0).set(new ItemStack(Items.EMERALD, 3));
        preserved(h, menu.getSlot(2).getItem(), expected, "merchant preview");
        menu.quickMoveStack(buyer, 2);
        h.assertTrue(menu.getSlot(0).getItem().isEmpty() && offer.getUses() == 1, "actual merchant take consumes payment once");
        var found = buyer.getInventory().items.stream().filter(ShaxiaStacks::isKnife).toList();
        h.assertTrue(found.size() == 1, "merchant transaction yields exactly one item");
        preserved(h, found.get(0), expected, "merchant take");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaEnchantingCandidatesExcludeInnate(GameTestHelper h) {
        var innate = ShaxiaEnchantments.JIJIE_SPECIAL_ATTACK.get();
        int candidates = 0;
        for (var item : List.of(Items.BOOK, Items.IRON_SWORD, ModItems.SHAXIADAO.get())) {
            for (int power = 1; power <= 100; power++) {
                for (boolean treasure : new boolean[]{false, true}) {
                    var available = EnchantmentHelper.getAvailableEnchantmentResults(power, new ItemStack(item), treasure);
                    candidates += available.size();
                    h.assertTrue(available.stream().noneMatch(entry -> entry.enchantment == innate),
                            "real enchantment candidates exclude innate at power " + power + ", treasure=" + treasure);
                }
            }
        }
        h.assertTrue(candidates > 0, "ordinary enchantments remain available: candidate test is not vacuous");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shaxiaRandomLootAndLibrarianBooksExcludeInnate(GameTestHelper h) {
        var innate = ShaxiaEnchantments.JIJIE_SPECIAL_ATTACK.get();
        var loot = EnchantRandomlyFunction.randomApplicableEnchantment().build();
        var params = new LootParams.Builder(h.getLevel()).create(LootContextParamSets.EMPTY);
        Villager librarian = h.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        librarian.setNoAi(true);
        librarian.setVillagerData(librarian.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
        int books = 0;
        for (int seed = 1; seed <= 512; seed++) {
            var context = new LootContext.Builder(params).withOptionalRandomSeed(seed).create(null);
            ItemStack result = loot.apply(new ItemStack(Items.BOOK), context);
            h.assertTrue(result.is(Items.ENCHANTED_BOOK) && !EnchantmentHelper.getEnchantments(result).isEmpty(),
                    "real random loot must produce an enchanted positive control");
            h.assertTrue(!EnchantmentHelper.getEnchantments(result).containsKey(innate), "random loot cannot discover innate");
            for (var listings : VillagerTrades.TRADES.get(VillagerProfession.LIBRARIAN).values()) {
                for (var listing : listings) {
                    MerchantOffer offer = listing.getOffer(librarian, RandomSource.create(seed));
                    if (offer == null) continue;
                    ItemStack output = offer.getResult();
                    h.assertTrue(!ShaxiaStacks.isKnife(output) && !EnchantmentHelper.getEnchantments(output).containsKey(innate),
                            "natural librarian offers cannot create the knife or its innate enchantment");
                    if (output.is(Items.ENCHANTED_BOOK)) books++;
                }
            }
        }
        h.assertTrue(books > 0, "actual librarian enchanted-book offers were exercised");
        h.succeed();
    }

    private ShaxiaAcceptanceGameTests() {}
}
