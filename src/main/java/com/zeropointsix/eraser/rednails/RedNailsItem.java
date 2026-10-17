package com.zeropointsix.eraser.rednails;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.memory.PreciousMemories;
import com.zeropointsix.eraser.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class RedNailsItem extends Item implements ICurioItem {
    public static final String MESSAGE = "message." + ModMain.MOD_ID + ".red_nails.";
    private static final String TIP = "tooltip." + ModMain.MOD_ID + ".red_nails.";
    private static final String[] FINGERS = {"little", "ring", "middle", "index"};
    public static final ResourceKey<DamageType> NAIL_TORN = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(ModMain.MOD_ID, "nail_torn"));
    public static Supplier<Component> keyName = () -> Component.literal("V");

    public RedNailsItem() { super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()); }

    public static List<ItemStack> worn(LivingEntity entity) {
        List<ItemStack> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(entity).ifPresent(inventory -> inventory.getCurios().forEach((id, handler) -> {
            if (!id.equals("ring") && !id.equals("hands")) return;
            var stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                var stack = stacks.getStackInSlot(i);
                if (stack.getItem() instanceof RedNailsItem) result.add(stack);
            }
        }));
        return result;
    }
    public static ItemStack findWorn(LivingEntity entity) {
        var stacks = worn(entity);
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
    }
    public static boolean isWorn(Player player, UUID id) {
        var stack = findWorn(player);
        return PreciousMemories.accepts(stack, player) && stack.hasTag() && stack.getTag().hasUUID("ItemId")
                && stack.getTag().getUUID("ItemId").equals(id);
    }
    @Override public boolean canEquip(SlotContext context, ItemStack stack) {
        return (context.identifier().equals("ring") || context.identifier().equals("hands"))
                && worn(context.entity()).stream().noneMatch(s -> s != stack);
    }
    @Override public void curioTick(SlotContext context, ItemStack stack) {
        if (context.entity() instanceof ServerPlayer player && player.tickCount % 20 == 0) maintain(stack, player);
    }
    @Override public void onUnequip(SlotContext context, ItemStack newStack, ItemStack stack) {
        // Curios also calls this when only the equipped stack's NBT changes.
        if (context.cosmetic() || newStack.getItem() instanceof RedNailsItem
                && stack.hasTag() && stack.getTag().hasUUID("ItemId")
                && newStack.hasTag() && newStack.getTag().hasUUID("ItemId")
                && stack.getTag().getUUID("ItemId").equals(newStack.getTag().getUUID("ItemId"))) return;
        if (context.entity() instanceof ServerPlayer player && stack.hasTag() && stack.getTag().hasUUID("ItemId")) {
            var entry = RedNailsState.get(player.getServer()).find(stack.getTag().getUUID("ItemId"));
            if (entry != null && entry.owner.equals(player.getUUID())) {
                boolean active = entry.active() > 0;
                recall(player.getServer(), entry);
                RedNailsState.get(player.getServer()).sync(stack, entry, RedNailsState.clock(player.getServer()));
                if (active) message(player, "unequipped");
            }
        }
    }
    public static boolean activate(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return false;
        ItemStack stack = findWorn(player);
        if (stack.isEmpty()) { message(player, "not_worn"); return false; }
        if (!PreciousMemories.accepts(stack, player)) { message(player, "not_owner"); return false; }
        var ledger = RedNailsState.get(player.getServer());
        var entry = ledger.read(stack);
        if (entry != null && !entry.owner.equals(player.getUUID())) { message(player, "not_owner"); return false; }
        if (player.isShiftKeyDown()) {
            if (entry != null) { recall(player.getServer(), entry); ledger.sync(stack, entry, RedNailsState.clock(player.getServer())); }
            message(player, "recall");
            return true;
        }
        long now = RedNailsState.clock(player.getServer());
        if (entry == null) entry = new RedNailsState.Entry(UUID.randomUUID(), player.getUUID());
        if (entry.active() == 4) { message(player, "all_out"); return false; }
        int nail = entry.available(now);
        if (nail < 0) {
            long next = Long.MAX_VALUE;
            for (int i = 0; i < 4; i++) if (entry.ghosts[i] == null) next = Math.min(next, entry.ready[i]);
            message(player, "none_ready", time(next - now)); return false;
        }
        var ghost = ModEntities.WATER_GHOST_SUMMON.get().create(player.level());
        if (ghost == null) return false;
        ghost.bind(player, entry.item, nail);
        spawnPosition(ghost, player);
        ghost.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200,
                com.zeropointsix.eraser.memory.SummonCombatConfig.value(
                        com.zeropointsix.eraser.memory.SummonCombatConfig.WATER_GHOST_REGENERATION), false, false));
        if (!player.serverLevel().addFreshEntity(ghost)) return false;
        ledger.begin(entry, nail, ghost.getUUID());
        PreciousMemories.bind(stack, player, entry.owner);
        ledger.sync(stack, entry, now);
        player.serverLevel().sendParticles(ParticleTypes.SPLASH, ghost.getX(), ghost.getY() + 0.5, ghost.getZ(), 30, 0.6, 0.5, 0.6, 0.1);
        player.serverLevel().sendParticles(ParticleTypes.BUBBLE, ghost.getX(), ghost.getY() + 0.5, ghost.getZ(), 30, 0.5, 0.4, 0.5, 0.05);
        ghost.playSound(SoundEvents.GENERIC_SPLASH, 1, 0.7F);
        ghost.playSound(SoundEvents.DROWNED_AMBIENT_WATER, 1, 0.6F);
        message(player, "summon", entry.active());
        PreciousMemories.firstUse(stack, player, MESSAGE, 6);
        return true;
    }
    private static void spawnPosition(WaterGhostSummonEntity ghost, Player player) {
        for (BlockPos at : BlockPos.withinManhattan(player.blockPosition(), 8, 4, 8)) {
            if (at.distSqr(player.blockPosition()) > 64 || !WaterGhostBody.waterAt(ghost, at)) continue;
            if (WaterGhostBody.place(ghost, Vec3.atBottomCenterOf(at), true)) return;
        }
        if (WaterGhostBody.place(ghost, ghost.guardPosition(player), false)) return;
        for (BlockPos at : BlockPos.withinManhattan(player.blockPosition(), 3, 1, 3)) {
            Vec3 position = Vec3.atBottomCenterOf(at);
            double distance = position.subtract(player.position()).horizontalDistanceSqr();
            if (distance >= 2.25 && distance <= 9 && WaterGhostBody.place(ghost, position, false)) return;
        }
        ghost.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
    }
    public static WaterGhostSummonEntity resolve(MinecraftServer server, UUID id) {
        if (id == null) return null;
        for (ServerLevel level : server.getAllLevels()) if (level.getEntity(id) instanceof WaterGhostSummonEntity ghost) return ghost;
        return null;
    }
    private static void recall(MinecraftServer server, RedNailsState.Entry entry) {
        var ledger = RedNailsState.get(server);
        for (int i = 0; i < 4; i++) {
            var id = entry.ghosts[i];
            if (id == null) continue;
            var ghost = resolve(server, id);
            if (ghost != null) ghost.finish(false);
            else ledger.finish(entry, i, id, false, RedNailsState.clock(server));
        }
    }
    public static void cancelOwner(Player player) {
        if (player.getServer() == null) return;
        var ledger = RedNailsState.get(player.getServer());
        for (var entry : ledger.owned(player.getUUID())) recall(player.getServer(), entry);
        for (var stack : allStacks(player)) {
            var entry = ledger.read(stack);
            if (entry != null) ledger.sync(stack, entry, RedNailsState.clock(player.getServer()));
        }
        RedNailsInput.cancel(player.getUUID());
    }
    public static void reconcileOwner(ServerPlayer player) {
        var ledger = RedNailsState.get(player.getServer());
        for (var entry : ledger.owned(player.getUUID())) {
            if (entry.active() > 0 && !isWorn(player, entry.item)) recall(player.getServer(), entry);
        }
        for (var stack : worn(player)) maintain(stack, player);
    }
    public static List<ItemStack> allStacks(Player player) {
        var stacks = new ArrayList<>(worn(player));
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof RedNailsItem) stacks.add(stack);
        }
        return stacks;
    }
    public static void ghostEnded(WaterGhostSummonEntity ghost, boolean died) {
        var server = ghost.getServer();
        var ledger = RedNailsState.get(server);
        var entry = ledger.find(ghost.itemId());
        if (entry == null || !ledger.finish(entry, ghost.nail(), ghost.getUUID(), died, RedNailsState.clock(server))) return;
        Player owner = ghost.getOwner() instanceof Player p ? p : server.getPlayerList().getPlayer(entry.owner);
        if (owner == null) return;
        for (var stack : allStacks(owner)) if (stack.hasTag() && stack.getTag().hasUUID("ItemId")
                && stack.getTag().getUUID("ItemId").equals(entry.item)) ledger.sync(stack, entry, RedNailsState.clock(server));
        if (died && owner.isAlive()) {
            owner.hurt(new DamageSource(owner.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(NAIL_TORN)), 4);
            owner.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60));
            owner.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100));
            owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            ((ServerLevel) owner.level()).sendParticles(new DustParticleOptions(new Vector3f(0.48F, 0.06F, 0.09F), 1),
                    owner.getX(), owner.getY() + 1, owner.getZ(), 16, 0.3, 0.2, 0.3, 0);
            owner.playSound(SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1, 0.6F);
            owner.playSound(SoundEvents.ITEM_BREAK, 1, 0.6F);
            owner.displayClientMessage(Component.translatable(MESSAGE + "nail_torn", finger(ghost.nail())).withStyle(ChatFormatting.DARK_RED), true);
        }
    }
    public static void maintain(ItemStack stack, ServerPlayer player) {
        var ledger = RedNailsState.get(player.getServer());
        var entry = ledger.read(stack);
        if (entry == null) return;
        long now = RedNailsState.clock(player.getServer());
        for (int i = 0; i < 4; i++) {
            if (entry.ready[i] != 0 && now >= entry.ready[i]) {
                entry.ready[i] = 0; ledger.setDirty();
                if (entry.owner.equals(player.getUUID())) message(player, "regrown", finger(i));
            }
            if (entry.ghosts[i] != null && resolve(player.getServer(), entry.ghosts[i]) == null) {
                if (entry.missingSince[i] < 0) entry.missingSince[i] = now;
                if (now - entry.missingSince[i] >= 40) ledger.finish(entry, i, entry.ghosts[i], false, now);
            } else entry.missingSince[i] = -1;
        }
        ledger.sync(stack, entry, now);
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (entity instanceof ServerPlayer player && player.tickCount % 20 == 0) maintain(stack, player);
    }
    public static int coolingCount(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        return (int) java.util.Arrays.stream(stack.getTag().getIntArray("NailLeft")).limit(4).filter(i -> i > 0).count();
    }
    public static Component finger(int nail) { return Component.translatable("finger." + ModMain.MOD_ID + "." + FINGERS[nail]); }
    public static String time(long ticks) { long seconds = Math.max(0, (ticks + 19) / 20); return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
    public static void message(Player player, String key, Object... args) { player.displayClientMessage(Component.translatable(MESSAGE + key, args), true); }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(TIP + "flavor").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(TIP + "usage", keyName.get()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(TIP + "recall", keyName.get()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(TIP + "stay").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(TIP + "cost").withStyle(ChatFormatting.GRAY));
        for (String key : List.of("stay_end", "backlash", "backlash_effects", "profile")) {
            lines.add(Component.translatable(TIP + key).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("tooltip.sound_isolating_eraser.summon_guard.range",
                com.zeropointsix.eraser.memory.SummonCombatConfig.guardRadius()).withStyle(ChatFormatting.GRAY));
        for (String key : List.of("priority", "protected", "pvp")) {
            lines.add(Component.translatable("tooltip.sound_isolating_eraser.summon_guard." + key).withStyle(ChatFormatting.GRAY));
        }
        var tag = stack.getTag();
        int[] left = tag == null ? new int[0] : tag.getIntArray("NailLeft");
        var out = tag == null ? new net.minecraft.nbt.ListTag() : tag.getList("NailOut", Tag.TAG_COMPOUND);
        var marks = Component.empty();
        int available = 0;
        var details = new ArrayList<Component>();
        for (int i = 0; i < 4; i++) {
            boolean cooling = i < left.length && left[i] > 0;
            boolean active = i < out.size() && out.getCompound(i).hasUUID("Ghost");
            if (!cooling && !active) available++;
            marks.append(Component.literal(cooling ? "\u25a1 " : "\u25a0 ").withStyle(cooling ? ChatFormatting.GRAY : ChatFormatting.DARK_RED));
            details.add(cooling ? Component.translatable(TIP + "nail_cooling", finger(i), time(left[i]))
                    : Component.translatable(TIP + (active ? "nail_out" : "nail_ready"), finger(i)));
        }
        lines.add(Component.translatable(TIP + "nails", marks, available));
        lines.addAll(details);
        lines.add(tag != null && tag.hasUUID("Owner") ? Component.translatable(TIP + "bound", tag.getString("OwnerName"))
                : Component.translatable(TIP + "unbound"));
    }
}
