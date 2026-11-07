package io.github.west3436.zas.atmos.electrolyzer;

import io.github.west3436.zas.atmos.inline.InlineDeviceMenu;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * The electrolyzer's GUI (CONT-074): the base's switch, power, status and outlet pressures, and its
 * water. It has no buttons of its own, so the server refuses any id but the switch.
 */
public final class ElectrolyzerMenu extends InlineDeviceMenu {
    /** The water in its tank, mB, rounded up so that any water shows. */
    public static final int DATA_WATER = InlineDeviceMenu.DATA_COUNT;

    public static final int DATA_COUNT = DATA_WATER + 1;

    /** The client's menu. */
    public ElectrolyzerMenu(int id) {
        super(AtmosMenus.ELECTROLYZER.get(), id, new SimpleContainerData(DATA_COUNT), DATA_COUNT, null);
    }

    /** The server's menu, on an electrolyzer. */
    public ElectrolyzerMenu(int id, ElectrolyzerBlockEntity electrolyzer) {
        super(AtmosMenus.ELECTROLYZER.get(), id, electrolyzer.data(), DATA_COUNT, electrolyzer);
    }

    /** The synced water, mB. */
    public int water() {
        return value(DATA_WATER);
    }

    @Override
    protected boolean ownButton(int id) {
        return false;
    }
}
