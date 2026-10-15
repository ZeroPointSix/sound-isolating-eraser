package com.zeropointsix.eraser.yuji.flare;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

public final class FlareLootModifier extends LootModifier {
    public static final Codec<FlareLootModifier> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, FlareLootModifier::new));
    public FlareLootModifier(LootItemCondition[] conditions) { super(conditions); }
    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        String table = context.getQueriedLootTableId().toString();
        if (table.equals("qihai:chests/yuji_supply_drop")) {
            add(loot, FlareContent.GUN.get(), FlareConfig.SUPPLY_GUNS.get());
            add(loot, FlareContent.RED.get(), FlareConfig.SUPPLY_RED.get());
            add(loot, FlareContent.YELLOW.get(), FlareConfig.SUPPLY_YELLOW.get());
            add(loot, FlareContent.GREEN.get(), FlareConfig.SUPPLY_GREEN.get());
        } else if (table.equals("qihai:chests/yuji_common") || table.equals("qihai:chests/yuji_mall") || table.equals("qihai:chests/yuji_hardware")) {
            if (context.getRandom().nextDouble() < FlareConfig.COMMON_CHANCE.get()) add(loot, FlareContent.ammunition(context.getRandom().nextInt(2)), 1);
            if (table.equals("qihai:chests/yuji_mall") && context.getRandom().nextDouble() < FlareConfig.GREEN_CHANCE.get()) add(loot, FlareContent.GREEN.get(), 1);
        }
        return loot;
    }
    private static void add(ObjectArrayList<ItemStack> loot, Item item, int count) {
        int maximum = new ItemStack(item).getMaxStackSize();
        while (count > 0) { int amount = Math.min(maximum, count); loot.add(new ItemStack(item, amount)); count -= amount; }
    }
}
