package com.zeropointsix.eraser.item;

import com.zeropointsix.eraser.config.CommonConfig;
import com.zeropointsix.eraser.fertilizer.FertilizerConfig;
import com.zeropointsix.eraser.gravity.GravityConfig;
import com.zeropointsix.eraser.yuji.YujiConfig;
import com.zeropointsix.eraser.yuji.flare.FlareConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;

/** Common-side assembly has no client class references, including on a dedicated server. */
public final class ItemGuideTooltips {
    public record Modifiers(boolean shift, boolean control) { }
    private static Supplier<Modifiers> input = () -> new Modifiers(false, false);
    private static Function<String, Component> binding = Component::translatable;

    /** Returns a restoration action for scoped previews and regression tests. */
    public static Runnable configureClient(Supplier<Modifiers> modifiers, Function<String, Component> keyName) {
        var previousInput = input;
        var previousBinding = binding;
        input = modifiers;
        binding = keyName;
        return () -> {
            input = previousInput;
            binding = previousBinding;
        };
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
        if (id.equals("sound_isolating_eraser:enhancement_pill_pack")) {
            if (section.equals("usage")) return new Object[]{seconds(CommonConfig.value(CommonConfig.PILL_DURATION, 48000))};
            if (section.equals("cooldown")) return new Object[]{seconds(CommonConfig.value(CommonConfig.USE_DURATION, 32))};
            if (section.equals("cost")) return new Object[]{seconds(CommonConfig.value(CommonConfig.WITHDRAWAL_DURATION, 12000))};
        }
        if (id.startsWith("qihai:flare_") && section.equals("cooldown")) {
            return new Object[]{seconds(value(YujiConfig.SPEC, FlareConfig.COOLDOWN))};
        }
        if (id.equals("qihai:flare_red") && section.equals("usage")) {
            return new Object[]{value(YujiConfig.SPEC, FlareConfig.TAUNT_RADIUS),
                    seconds(value(YujiConfig.SPEC, FlareConfig.RED_LIFETIME))};
        }
        if (id.equals("qihai:flare_yellow") && section.equals("usage")) {
            return new Object[]{value(YujiConfig.SPEC, FlareConfig.LIGHT_RADIUS),
                    seconds(value(YujiConfig.SPEC, FlareConfig.YELLOW_LIFETIME))};
        }
        if (id.equals("qihai:flare_green") && section.equals("usage")) {
            return new Object[]{seconds(value(YujiConfig.SPEC, FlareConfig.GREEN_DELAY))};
        }
        if (id.equals("qihai:supply_crate") && section.equals("cooldown")) {
            return new Object[]{seconds(value(YujiConfig.SPEC, FlareConfig.EMPTY_LIFETIME))};
        }
        if (id.equals("sound_isolating_eraser:super_fertilizer_bag")) {
            if (section.equals("usage")) return new Object[]{FertilizerConfig.value(FertilizerConfig.THRESHOLD),
                    FertilizerConfig.value(FertilizerConfig.TREE_COUNT)};
            if (section.equals("cost")) return new Object[]{Math.max(0, stack.getMaxDamage() - stack.getDamageValue()),
                    stack.getMaxDamage()};
        }
        if (id.equals("sound_isolating_eraser:fertile_fruit") && section.equals("usage")) {
            return new Object[]{FertilizerConfig.value(FertilizerConfig.NUTRITION),
                    FertilizerConfig.value(FertilizerConfig.HEAL),
                    seconds(FertilizerConfig.value(FertilizerConfig.REGEN_TICKS))};
        }
        if (List.of("sound_isolating_eraser:fertile_log", "sound_isolating_eraser:fertile_leaves",
                "sound_isolating_eraser:fertile_planks").contains(id) && section.equals("usage")) {
            return new Object[]{FertilizerConfig.value(FertilizerConfig.AURA_THRESHOLD)};
        }
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
    private static <T> T value(ForgeConfigSpec spec, ForgeConfigSpec.ConfigValue<T> option) {
        return spec.isLoaded() ? option.get() : option.getDefault();
    }
    private ItemGuideTooltips() { }
}
