package com.zeropointsix.eraser.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** 风雷翅：胸甲槽装备的飞行法宝。收起时只在背后留发光小翼。 */
public class WindThunderWingsItem extends Item implements Equipable {
    public WindThunderWingsItem(Properties props) {
        super(props);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.CHEST;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_ELYTRA;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.sound_isolating_eraser.wind_thunder_wings.desc1")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.sound_isolating_eraser.wind_thunder_wings.desc2")
                .withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("item.sound_isolating_eraser.wind_thunder_wings.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Wings are worn when in the chest slot. */
    public static boolean worn(Entity entity) {
        return entity instanceof Player player
                && player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof WindThunderWingsItem;
    }
}
