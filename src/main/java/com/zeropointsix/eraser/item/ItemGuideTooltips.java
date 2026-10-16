package com.zeropointsix.eraser.item;

import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.gravity.GravityConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Common-side assembly has no client class references, including on a dedicated server. */
public final class ItemGuideTooltips {
    public record Modifiers(boolean shift, boolean control) { }
    private static Supplier<Modifiers> input = () -> new Modifiers(false, false);
    private static Function<String, Component> binding = Component::translatable;

    public static void configureClient(Supplier<Modifiers> modifiers, Function<String, Component> keyName) {
        input = modifiers;
        binding = keyName;
    }

    public static boolean showDetails() { return input.get().shift(); }
    public static void append(ItemStack stack, List<Component> lines, int index) {
        Modifiers modifiers = input.get();
        lines.addAll(index, describe(stack, modifiers.shift(), modifiers.control(), binding));
    }

    public static List<Component> describe(ItemStack stack, boolean shift, boolean control,
                                           Function<String, Component> keys) {
        var entry = ItemGuideCatalog.find(stack);
        if (entry == null) return List.of();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(entry.key("summary")).withStyle(ChatFormatting.GRAY));
        if (shift) {
            lines.add(Component.translatable(entry.key("usage"), arguments(entry, "usage", stack)).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(entry.key("controls"), entry.controls().stream().map(keys).toArray())
                    .withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable(entry.key("cooldown"), arguments(entry, "cooldown", stack))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(entry.key("cost"), arguments(entry, "cost", stack))
                    .withStyle(ChatFormatting.RED));
        }
        if (control) lines.add(Component.translatable(entry.key("lore")).withStyle(ChatFormatting.DARK_PURPLE));
        if (!shift || !control) {
            String hint = shift ? "hold_control" : control ? "hold_shift" : "hold_both";
            lines.add(Component.translatable("guide.sound_isolating_eraser." + hint).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    public static Object[] arguments(ItemGuideCatalog.Entry entry, String section, ItemStack stack) {
        String id = entry.id().toString();
        if (id.equals("sound_isolating_eraser:sound_isolating_eraser")) {
            if (section.equals("usage")) return new Object[]{CommonConfig.barrierHeight()};
            if (section.equals("cost")) return new Object[]{Math.max(0, stack.getMaxDamage() - stack.getDamageValue())};
        }
        if (id.equals("sound_isolating_eraser:gravity_jade_pendant") && section.equals("cooldown")) {
            int duration = GravityConfig.SPEC.isLoaded() ? GravityConfig.DURATION.get() : GravityConfig.DURATION.getDefault();
            int cooldown = GravityConfig.SPEC.isLoaded() ? GravityConfig.COOLDOWN.get() : GravityConfig.COOLDOWN.getDefault();
            return new Object[]{seconds(duration), seconds(cooldown)};
        }
        return new Object[0];
    }

    public static String seconds(int ticks) {
        return java.math.BigDecimal.valueOf(ticks).divide(java.math.BigDecimal.valueOf(20))
                .stripTrailingZeros().toPlainString();
    }
    private ItemGuideTooltips() { }
}
