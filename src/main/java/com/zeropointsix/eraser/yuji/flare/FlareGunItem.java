package com.zeropointsix.eraser.yuji.flare;

import com.zeropointsix.eraser.yuji.city.RainCityContent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;
import javax.annotation.Nullable;

public final class FlareGunItem extends Item {
    public FlareGunItem() { super(new Properties().stacksTo(1)); }

    public static ItemStack ammunition(Player player) {
        if (FlareContent.color(player.getOffhandItem()) >= 0) return player.getOffhandItem();
        for (ItemStack stack : player.getInventory().items)
            if (!stack.isEmpty() && FlareContent.color(stack) >= 0) return stack;
        return ItemStack.EMPTY;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(gun);
        if (!FlareConfig.ALL_DIMENSIONS.get() && !RainCityContent.isCity(level)) return InteractionResultHolder.fail(gun);
        ItemStack ammo = ammunition(player);
        int color = FlareContent.color(ammo);
        if (ammo.isEmpty()) {
            if (!level.isClientSide) level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, .7F, 1);
            return InteractionResultHolder.fail(gun);
        }
        if (color == 2 && !FlarePlacement.openSky(level, player.blockPosition())) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.qihai.flare.open_sky"), true);
            return InteractionResultHolder.fail(gun);
        }
        if (!level.isClientSide) {
            FlareProjectile flare = new FlareProjectile(FlareContent.FLARE.get(), level);
            flare.setOwner(player); flare.setColor(color);
            flare.setPos(player.getX(), player.getEyeY() - .1, player.getZ());
            if (color == 2) flare.shoot(0, 1, 0, 1.7F, 0);
            else flare.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.5F, .4F);
            if (!level.addFreshEntity(flare)) return InteractionResultHolder.fail(gun);
            if (!player.getAbilities().instabuild || !FlareConfig.CREATIVE_FREE.get()) ammo.shrink(1);
            player.getCooldowns().addCooldown(this, FlareConfig.COOLDOWN.get());
            level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1, 1);
        }
        return InteractionResultHolder.sidedSuccess(gun, level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.qihai.flare_gun"));
    }
}
