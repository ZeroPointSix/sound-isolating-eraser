package com.zeropointsix.eraser.registry;

import com.zeropointsix.eraser.ModMain;
import com.zeropointsix.eraser.brickrot.BrickrotSpawnEggItem;
import com.zeropointsix.eraser.yuji.YujiContent;
import java.util.Comparator;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModMain.MOD_ID);
    public static final RegistryObject<CreativeModeTab> QIJIE = TABS.register("qijie", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sound_isolating_eraser.qijie"))
                    .icon(() -> ModItems.SOUND_ISOLATING_ERASER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> ForgeRegistries.ITEMS.getValues().stream()
                            .filter(item -> owns(ForgeRegistries.ITEMS.getKey(item)))
                            .sorted(Comparator.comparingInt(ModCreativeTabs::group)
                                    .thenComparing(ModCreativeTabs::sortKey))
                            // Preserve default NBT, including the knife's built-in enchantments.
                            .forEach(item -> output.accept(item.getDefaultInstance())))
                    .build());

    public static boolean owns(ResourceLocation id) {
        return id != null && (ModMain.MOD_ID.equals(id.getNamespace())
                || YujiContent.ID.equals(id.getNamespace()));
    }

    private static int group(Item item) {
        if (item == ModItems.SUXIA_SPAWN_EGG.get() || item == ModItems.WATER_GHOST_SPAWN_EGG.get()) return 0;
        if (item instanceof SpawnEggItem || item instanceof BrickrotSpawnEggItem) return 2;
        return item instanceof BlockItem ? 1 : 0;
    }

    private static String sortKey(Item item) {
        if (item == ModItems.RED_NAILS.get()) return ModMain.MOD_ID + ":suxia_pocket_watch~1";
        if (item == ModItems.WATER_GHOST_SPAWN_EGG.get()) return ModMain.MOD_ID + ":suxia_pocket_watch~2";
        return ForgeRegistries.ITEMS.getKey(item).toString();
    }

    private ModCreativeTabs() { }
}
