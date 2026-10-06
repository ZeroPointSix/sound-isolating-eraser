package com.zeropointsix.eraser.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** 雷鹏骨羽：炼制风雷翅的材料（幻翼被雷劈中掉落/古城战利品）。 */
public class ThunderFeatherItem extends Item {
    public ThunderFeatherItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.sound_isolating_eraser.thunder_feather.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
