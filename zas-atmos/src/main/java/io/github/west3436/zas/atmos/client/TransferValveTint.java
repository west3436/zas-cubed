package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.gear.TankKind;
import net.minecraft.world.item.ItemStack;

/** The colour a TTV's grey tanks are tinted, by each tank's kind, after the tanks' own sprites (CONT-103). */
final class TransferValveTint {
    private TransferValveTint() {}

    /** A tank's tint, RGB. */
    static int of(ItemStack tank) {
        return TankKind.of(tank).tint();
    }
}
