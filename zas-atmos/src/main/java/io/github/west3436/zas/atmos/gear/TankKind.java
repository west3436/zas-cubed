package io.github.west3436.zas.atmos.gear;

import io.github.west3436.zas.atmos.registry.AtmosItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The kinds a personal tank shows as, where code tints a grey tank: on a tank transfer valve
 * (CONT-103) and in a tank holder (CONT-151). The colours follow the tanks' own sprites.
 */
public enum TankKind {
    /** O₂ and emergency O₂ tanks: blue. */
    OXYGEN(0x0066FF),
    /** Air tanks: yellow. */
    AIR(0xF7C631),
    /** Any other tank: grey. */
    OTHER(0xC8C8C8);

    private final int tint;

    TankKind(int tint) {
        this.tint = tint;
    }

    /** Its tint, RGB. */
    public int tint() {
        return tint;
    }

    /** A tank's kind; anything that isn't an O₂ or air tank is {@link #OTHER}. */
    public static TankKind of(ItemStack tank) {
        Item item = tank.getItem();
        if (item == AtmosItems.OXYGEN_TANK.get() || item == AtmosItems.EMERGENCY_OXYGEN_TANK.get()) {
            return OXYGEN;
        }
        return item == AtmosItems.AIR_TANK.get() ? AIR : OTHER;
    }
}
