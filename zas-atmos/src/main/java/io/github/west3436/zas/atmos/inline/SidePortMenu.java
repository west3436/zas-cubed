package io.github.west3436.zas.atmos.inline;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;

/**
 * The GUI of a device with a side port (CONT-074): the base's, with its target and buttons to step
 * it and to flip the side port. The device clamps the target. A device's own data slots follow
 * {@link #DATA_COUNT}, and its own buttons follow {@link #LAST_BUTTON}.
 */
public abstract class SidePortMenu extends InlineDeviceMenu {
    /** Flips the side port. */
    public static final int FLIP = TOGGLE + 1;

    /** The target's largest step down; the smaller step down, the smaller up and the largest up follow. */
    public static final int STEP_DOWN_BIG = FLIP + 1;

    public static final int LAST_BUTTON = STEP_DOWN_BIG + 3;

    /** The target, kPa, rounded. */
    public static final int DATA_TARGET = InlineDeviceMenu.DATA_COUNT;

    public static final int DATA_COUNT = DATA_TARGET + 1;

    private final SidePortEntity device;

    /**
     * @param type the menu's type
     * @param id the menu's id
     * @param data its data slots: a {@code SimpleContainerData} on the client, the device's on the
     *     server
     * @param dataCount how many it needs
     * @param device the device on the server, null on the client
     */
    protected SidePortMenu(MenuType<?> type, int id, ContainerData data, int dataCount, SidePortEntity device) {
        super(type, id, data, dataCount, device);
        this.device = device;
    }

    @Override
    protected final boolean ownButton(int id) {
        if (id == FLIP) {
            device.flip();
            return true;
        }
        if (id >= STEP_DOWN_BIG && id <= LAST_BUTTON) {
            device.step(id - STEP_DOWN_BIG);
            return true;
        }
        return deviceButton(id);
    }

    /**
     * Applies one of the device's own buttons, on the server.
     *
     * @param id the button's id, not one of this class's, and not yet checked
     * @return false to refuse it
     */
    protected abstract boolean deviceButton(int id);
}
