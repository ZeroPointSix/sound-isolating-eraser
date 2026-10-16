package com.zeropointsix.eraser.keeper;

import com.zeropointsix.eraser.ModMain;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class KeeperCameraItem extends Item {
    private static final String TEXT = "tooltip." + ModMain.MOD_ID + ".keeper_camera.";
    public KeeperCameraItem() { super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) return InteractionResultHolder.success(stack);
        if (!player.isAlive() || player.isSpectator() || KeeperSessions.active(player) || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        var tag = stack.getOrCreateTag();
        if (tag.hasUUID("Owner") && !KeeperData.owns(stack, player)) {
            KeeperData.message(player, "camera", "not_owner");
            return InteractionResultHolder.fail(stack);
        }
        KeeperData.refill(stack, Math.floorDiv(server.server.overworld().getDayTime(), 24000));
        if (!tag.hasUUID("Owner")) {
            tag.putUUID("Owner", player.getUUID()); tag.putString("OwnerName", player.getName().getString());
        }
        if (!tag.getBoolean("FirstUseShown")) {
            for (int i = 1; i <= 2; i++) player.displayClientMessage(Component.translatable(
                    "message." + ModMain.MOD_ID + ".keeper_camera.first_use_" + i).withStyle(ChatFormatting.DARK_RED), false);
            tag.putBoolean("FirstUseShown", true);
        }
        if (KeeperData.film(stack) == 0) {
            KeeperData.message(player, "camera", "no_film");
            return InteractionResultHolder.fail(stack);
        }
        ItemStack photo = KeeperData.capture(server);
        tag.putInt("Film", KeeperData.film(stack) - 1);
        if (!player.getInventory().add(photo)) player.drop(photo, false);
        var point = player.getEyePosition().add(player.getLookAngle());
        server.serverLevel().sendParticles(ParticleTypes.FLASH, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 1, 1.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 1.4F);
        player.getCooldowns().addCooldown(this, 20);
        KeeperNetwork.flash(server);
        player.inventoryMenu.broadcastChanges();
        return InteractionResultHolder.consume(stack);
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && level.getGameTime() % 20 == 0 && level.getServer() != null) {
            KeeperData.refill(stack, Math.floorDiv(level.getServer().overworld().getDayTime(), 24000));
        }
    }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13F * KeeperData.film(stack) / KeeperData.FILM); }
    @Override public int getBarColor(ItemStack stack) { return 0xA37B47; }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        for (String key : List.of("category", "flavor1", "flavor2", "usage")) lines.add(Component.translatable(TEXT + key).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(TEXT + "film", KeeperData.film(stack)).withStyle(ChatFormatting.GRAY));
        lines.add(stack.hasTag() && stack.getTag().hasUUID("Owner")
                ? Component.translatable(TEXT + "bound", stack.getTag().getString("OwnerName"))
                : Component.translatable(TEXT + "unbound"));
    }
}
