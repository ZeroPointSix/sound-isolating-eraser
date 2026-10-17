package com.zeropointsix.eraser.suxia;

import com.zeropointsix.eraser.registry.ModEntities;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SuxiaPocketWatchItem extends Item {
    private static final String MESSAGE = "message.sound_isolating_eraser.suxia_pocket_watch.";
    private static final String TOOLTIP = "tooltip.sound_isolating_eraser.suxia_pocket_watch.";

    public SuxiaPocketWatchItem() { super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResultHolder.success(stack);
        var tag = stack.getOrCreateTag();
        if (!com.zeropointsix.eraser.memory.PreciousMemories.accepts(stack, player)) {
            message(player, "not_owner");
            return InteractionResultHolder.fail(stack);
        }
        var ledger = SuxiaSummons.get(serverLevel.getServer());
        var entry = ledger.bind(stack, player);
        if (!entry.owner.equals(player.getUUID())) {
            message(player, "not_owner");
            return InteractionResultHolder.fail(stack);
        }
        long now = SuxiaMotion.clock(level);
        reconcile(ledger, entry, serverLevel.getServer(), now);
        if (entry.active()) {
            var summon = resolve(serverLevel.getServer(), entry);
            if (summon != null) summon.finish(false);
            else ledger.finish(entry, entry.summon, now, false);
            ledger.sync(stack, entry, now);
            syncInventory(player);
            return InteractionResultHolder.consume(stack);
        }
        if (entry.cooldown > now) {
            ledger.sync(stack, entry, now);
            message(player, "cooldown", time(entry.cooldown - now));
            return InteractionResultHolder.fail(stack);
        }
        var active = ledger.active(player.getUUID());
        if (active != null) reconcile(ledger, active, serverLevel.getServer(), now);
        if (ledger.active(player.getUUID()) != null) {
            message(player, "already_active");
            return InteractionResultHolder.fail(stack);
        }
        var summon = ModEntities.SUXIA_SUMMON.get().create(level);
        if (summon == null || !findPosition(summon, player)) {
            message(player, "no_space");
            return InteractionResultHolder.fail(stack);
        }
        summon.bind(player, entry.watch);
        if (!serverLevel.addFreshEntity(summon)) return InteractionResultHolder.fail(stack);
        ledger.begin(entry, summon, now);
        ledger.sync(stack, entry, now);
        player.getCooldowns().removeCooldown(this);
        SuxiaMotion.smoke(summon);
        summon.playSound(SoundEvents.DROWNED_AMBIENT, 0.8F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.7F, 1.6F);
        com.zeropointsix.eraser.memory.PreciousMemories.firstUse(stack, player, MESSAGE, 5);
        return InteractionResultHolder.consume(stack);
    }

    private static boolean findPosition(SuxiaSummonEntity summon, Player player) {
        Vec3 forward = player.getLookAngle().multiply(1, 0, 1).normalize().scale(2);
        BlockPos front = BlockPos.containing(player.position().add(forward));
        if (SuxiaMotion.place(summon, Vec3.atBottomCenterOf(front))) return true;
        for (int radius = 1; radius <= 3; radius++) for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius || x * x + z * z > 9) continue;
                for (int y = 0; y >= -1; y--) {
                    if (SuxiaMotion.place(summon, Vec3.atBottomCenterOf(player.blockPosition().offset(x, y, z)))) return true;
                }
            }
        }
        return false;
    }

    @Nullable public static SuxiaSummonEntity resolve(MinecraftServer server, SuxiaSummons.Entry entry) {
        if (entry.summon == null) return null;
        for (ServerLevel world : server.getAllLevels()) {
            if (world.getEntity(entry.summon) instanceof SuxiaSummonEntity summon) return summon;
        }
        return null;
    }

    private static void reconcile(SuxiaSummons ledger, SuxiaSummons.Entry entry, MinecraftServer server, long now) {
        if (!entry.active()) return;
        var summon = resolve(server, entry);
        if (now >= entry.until || summon == null && now >= entry.until - SuxiaSummons.DURATION + 40) {
            if (summon != null) summon.finish(false);
            else ledger.finish(entry, entry.summon, now, false);
        }
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 20 != 0 || !(entity instanceof Player player)
                || !stack.hasTag() || !stack.getTag().hasUUID("Owner")) return;
        syncStack(stack, player);
    }

    private static void syncStack(ItemStack stack, Player player) {
        if (!stack.hasTag() || !stack.getTag().hasUUID("Owner")) return;
        var ledger = SuxiaSummons.get(player.getServer());
        var entry = ledger.bind(stack, player);
        long now = SuxiaMotion.clock(player.level());
        reconcile(ledger, entry, player.getServer(), now);
        ledger.sync(stack, entry, now);
        // Vanilla cooldowns gate every stack of an item before use(). The ledger gates this watch instead.
        player.getCooldowns().removeCooldown(stack.getItem());
    }

    public static void syncInventory(Player player) {
        if (player.getServer() == null) return;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof SuxiaPocketWatchItem) syncStack(stack, player);
        }
    }

    public static void cancelOwner(Player player) {
        if (player.getServer() == null) return;
        var ledger = SuxiaSummons.get(player.getServer());
        var entry = ledger.active(player.getUUID());
        if (entry == null) return;
        var summon = resolve(player.getServer(), entry);
        if (summon != null) summon.finish(false);
        else ledger.finish(entry, entry.summon, SuxiaMotion.clock(player.level()), false);
        syncInventory(player);
    }

    private static void message(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(MESSAGE + key, args), true);
    }

    private static String time(long ticks) {
        long seconds = Math.max(0, (ticks + 19) / 20);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override public boolean isBarVisible(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean("Open");
    }
    @Override public int getBarWidth(ItemStack stack) {
        return stack.hasTag() ? Mth.clamp(Math.round(13F * stack.getTag().getInt("Remaining") / SuxiaSummons.DURATION), 0, 13) : 0;
    }
    @Override public int getBarColor(ItemStack stack) { return 0xA8141C; }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        for (String key : List.of("flavor1", "flavor2", "usage", "recall", "cooldown", "backlash", "profile")) {
            lines.add(Component.translatable(TOOLTIP + key).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("tooltip.sound_isolating_eraser.summon_guard.range",
                com.zeropointsix.eraser.memory.SummonCombatConfig.guardRadius()).withStyle(ChatFormatting.GRAY));
        for (String key : List.of("priority", "protected", "pvp")) {
            lines.add(Component.translatable("tooltip.sound_isolating_eraser.summon_guard." + key).withStyle(ChatFormatting.GRAY));
        }
        var tag = stack.getTag();
        lines.add(tag != null && tag.hasUUID("Owner")
                ? Component.translatable(TOOLTIP + "bound", tag.getString("OwnerName")).withStyle(ChatFormatting.GRAY)
                : Component.translatable(TOOLTIP + "unbound").withStyle(ChatFormatting.GRAY));
        if (tag != null && tag.getBoolean("Open")) {
            lines.add(Component.translatable(TOOLTIP + "active", (tag.getInt("Remaining") + 19) / 20)
                    .withStyle(ChatFormatting.DARK_RED));
        } else if (tag != null && tag.getInt("CooldownRemaining") > 0) {
            lines.add(Component.translatable(TOOLTIP + "cooling", time(tag.getInt("CooldownRemaining")))
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }
}
