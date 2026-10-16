package com.zeropointsix.eraser.keeper;

import com.zeropointsix.eraser.ModMain;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class KeeperPhotoItem extends Item {
    private static final String TEXT = "tooltip." + ModMain.MOD_ID + ".keeper_photo.";
    public KeeperPhotoItem() { super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }
    @Override public int getUseDuration(ItemStack stack) { return 20; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.SPYGLASS; }
    @Override public Component getName(ItemStack stack) {
        return KeeperData.blank(stack) ? Component.translatable("item." + ModMain.MOD_ID + ".keeper_photo.blank") : super.getName(stack);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (KeeperSessions.active(player)) {
            if (player instanceof ServerPlayer server) {
                if (!KeeperSessions.current(player, stack)) return InteractionResultHolder.fail(stack);
                if (player.isShiftKeyDown()) KeeperSessions.tear(server, stack); else KeeperSessions.exit(server);
            }
            return InteractionResultHolder.consume(stack);
        }
        if (!KeeperData.owns(stack, player)) {
            if (!level.isClientSide) KeeperData.message(player, "photo", "not_owner");
            return InteractionResultHolder.fail(stack);
        }
        if (!player.isAlive() || player.isSpectator() || KeeperData.blank(stack)) return InteractionResultHolder.fail(stack);
        if (player instanceof ServerPlayer server && KeeperSessions.cooling(server)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) KeeperSessions.enter(player, stack);
        return stack;
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        if (KeeperData.blank(stack)) { lines.add(Component.translatable(TEXT + "blank").withStyle(ChatFormatting.GRAY)); return; }
        var tag = stack.getTag();
        String dimensionId = tag.getString("Dim");
        Component dimension = switch (dimensionId) {
            case "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end" ->
                    Component.translatable(TEXT + "dimension." + dimensionId.substring(10));
            default -> Component.literal(dimensionId);
        };
        lines.add(Component.translatable(TEXT + "taken_at", dimension,
                (int) Math.floor(tag.getDouble("X")), (int) Math.floor(tag.getDouble("Y")), (int) Math.floor(tag.getDouble("Z"))));
        lines.add(Component.translatable(TEXT + "taken_time", tag.getLong("Day"), tag.getString("Clock")));
        for (String key : List.of("enter", "tear")) lines.add(Component.translatable(TEXT + key).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(TEXT + "uses", Math.max(0, KeeperData.VISITS - tag.getInt("Uses"))));
    }
}
