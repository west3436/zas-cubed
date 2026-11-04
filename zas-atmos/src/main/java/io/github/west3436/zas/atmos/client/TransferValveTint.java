package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.registry.AtmosItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The colour a TTV's grey tanks are tinted, by each tank's kind, after the tanks' own sprites (CONT-103). */
final class TransferValveTint {
    private static final int OXYGEN = 0x0066FF;
    private static final int AIR = 0xF7C631;
    private static final int PLAIN = 0xC8C8C8;

    private TransferValveTint() {}

    /** A tank's tint, RGB. */
    static int of(ItemStack tank) {
        Item item = tank.getItem();
        if (item == AtmosItems.OXYGEN_TANK.get() || item == AtmosItems.EMERGENCY_OXYGEN_TANK.get()) {
            return OXYGEN;
        }
        return item == AtmosItems.AIR_TANK.get() ? AIR : PLAIN;
    }
}
