package com.zeropointsix.eraser.gravity;

import com.zeropointsix.eraser.registry.ModItems;
import net.minecraft.world.entity.LivingEntity;
import top.theillusivec4.curios.api.CuriosApi;

public final class GravityEquipment {
    public static boolean isEquipped(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).map(inventory ->
                inventory.getStacksHandler("necklace").map(handler -> {
                    // getStacks deliberately excludes cosmetic slots.
                    var stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        if (stacks.getStackInSlot(i).is(ModItems.GRAVITY_JADE_PENDANT.get())) {
                            return true;
                        }
                    }
                    return false;
                }).orElse(false)).orElse(false);
    }

    private GravityEquipment() { }
}
