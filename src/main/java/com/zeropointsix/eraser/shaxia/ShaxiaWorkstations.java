package com.zeropointsix.eraser.shaxia;

import com.zeropointsix.eraser.ModMain;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MOD_ID)
public final class ShaxiaWorkstations {
    @SubscribeEvent public static void anvil(AnvilRepairEvent event) {
        ShaxiaStacks.normalize(event.getOutput(), ShaxiaConfig.flag(ShaxiaConfig.RESTORE_MISSING, true));
    }

    @SubscribeEvent public static void grind(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem(), bottom = event.getBottomItem();
        if (!ShaxiaStacks.isKnife(top) && !ShaxiaStacks.isKnife(bottom)) return;
        ItemStack output = grindOutput(top, bottom);
        if (output.isEmpty()) {
            // An empty override alone would fall through to vanilla and award innate-enchantment XP.
            event.setCanceled(true);
        } else {
            event.setOutput(output);
            event.setXp(experience(top, bottom));
        }
    }

    @SubscribeEvent public static void take(GrindstoneEvent.OnTakeItem event) {
        if (ShaxiaStacks.isKnife(event.getTopItem()) || ShaxiaStacks.isKnife(event.getBottomItem())) {
            event.setXp(experience(event.getTopItem(), event.getBottomItem()));
        }
    }

    public static ItemStack grindOutput(ItemStack top, ItemStack bottom) {
        if ((!top.isEmpty() && (!ShaxiaStacks.isKnife(top) || top.getCount() != 1))
                || (!bottom.isEmpty() && (!ShaxiaStacks.isKnife(bottom) || bottom.getCount() != 1))) return ItemStack.EMPTY;
        ItemStack input = top.isEmpty() ? bottom : top;
        if (input.isEmpty()) return ItemStack.EMPTY;
        boolean pair = !top.isEmpty() && !bottom.isEmpty();
        int damage = input.getDamageValue();
        if (pair) damage = Math.max(0, top.getDamageValue() + bottom.getDamageValue()
                - input.getMaxDamage() - input.getMaxDamage() * 5 / 100);
        boolean repair = pair && damage < Math.min(top.getDamageValue(), bottom.getDamageValue());
        if (!repair && removableCost(top) + removableCost(bottom) == 0) return ItemStack.EMPTY;
        ItemStack output = input.copy();
        output.setCount(1);
        output.setDamageValue(damage);
        Map<Enchantment, Integer> kept = new HashMap<>();
        for (ItemStack source : new ItemStack[]{top, bottom}) {
            EnchantmentHelper.getEnchantments(source).forEach((enchantment, level) -> {
                if (enchantment.isCurse()) kept.merge(enchantment, level, Math::max);
            });
        }
        boolean innate = ShaxiaStacks.active(top) || ShaxiaStacks.active(bottom)
                || ShaxiaConfig.flag(ShaxiaConfig.RESTORE_MISSING, true);
        if (innate) kept.put(ShaxiaEnchantments.JIJIE_SPECIAL_ATTACK.get(), 1);
        EnchantmentHelper.setEnchantments(kept, output);
        int repairCost = 0;
        for (Enchantment enchantment : kept.keySet()) {
            if (enchantment.isCurse()) repairCost = repairCost * 2 + 1;
        }
        output.setRepairCost(repairCost);
        return output;
    }

    private static int removableCost(ItemStack stack) {
        int result = 0;
        for (var entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            if (!entry.getKey().isCurse() && entry.getKey() != ShaxiaEnchantments.JIJIE_SPECIAL_ATTACK.get()) {
                result += entry.getKey().getMinCost(entry.getValue());
            }
        }
        return result;
    }

    private static int experience(ItemStack top, ItemStack bottom) {
        int half = (removableCost(top) + removableCost(bottom) + 1) / 2;
        return half == 0 ? 0 : half + ThreadLocalRandom.current().nextInt(half);
    }

    private ShaxiaWorkstations() {}
}
