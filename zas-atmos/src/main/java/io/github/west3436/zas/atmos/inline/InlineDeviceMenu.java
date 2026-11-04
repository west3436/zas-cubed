package io.github.west3436.zas.atmos.inline;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * The base of an inline device's GUI (CONT-074): its switch, whether it has power, what it did last
 * atmos tick and the pressure at each port. The client sends only button ids. The server checks
 * each one here and in {@link #ownButton}, where a device refuses ids it doesn't know and clamps its
 * settings.
 *
 * <p>A device's own data slots follow {@link #DATA_COUNT}, and its own buttons follow {@link
 * #TOGGLE}.
 */
public abstract class InlineDeviceMenu extends AbstractContainerMenu {
    /** Switches the device on or off. */
    public static final int TOGGLE = 0;

    /** Whether it is switched on. */
    public static final int DATA_ON = 0;

    /** Whether it has power. */
    public static final int DATA_POWERED = 1;

    /** The {@link InlineStatus}'s ordinal. */
    public static final int DATA_STATUS = 2;

    /** Each port's network's pressure from here on, kPa, rounded and capped at 32,767. */
    public static final int DATA_PRESSURE = 3;

    /** The most ports a device has. */
    public static final int MAX_PORTS = 3;

    /** The base's data slots. */
    public static final int DATA_COUNT = DATA_PRESSURE + MAX_PORTS;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final InlineDeviceEntity device;

    /**
     * @param type the menu's type
     * @param id the menu's id
     * @param data its data slots: a {@code SimpleContainerData} on the client, the device's {@link
     *     InlineDeviceEntity#data()} on the server
     * @param dataCount how many it needs
     * @param device the device on the server, null on the client
     */
    protected InlineDeviceMenu(MenuType<?> type, int id, ContainerData data, int dataCount, InlineDeviceEntity device) {
        super(type, id);
        checkContainerDataCount(data, dataCount);
        this.data = data;
        this.device = device;
        this.access = device == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(device.getLevel(), device.getBlockPos());
        addDataSlots(data);
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    /** The synced switch. */
    public boolean on() {
        return data.get(DATA_ON) != 0;
    }

    /** Whether it has power, as synced. */
    public boolean powered() {
        return data.get(DATA_POWERED) != 0;
    }

    /** The synced status. */
    public InlineStatus status() {
        return InlineStatus.of(data.get(DATA_STATUS));
    }

    /** A port's network's pressure, as synced: kPa, rounded. */
    public int pressure(int port) {
        return data.get(DATA_PRESSURE + port);
    }

    /** Applies a button: the server's half of the GUI. */
    @Override
    public final boolean clickMenuButton(Player player, int id) {
        if (device == null || device.isRemoved()) {
            return false;
        }
        if (id == TOGGLE) {
            device.setOn(!device.on());
            return true;
        }
        return ownButton(id);
    }

    /**
     * Applies one of the device's own buttons, on the server.
     *
     * @param id the button's id, not {@link #TOGGLE}, and not yet checked
     * @return false to refuse it
     */
    protected abstract boolean ownButton(int id);

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return device == null
                || stillValid(access, player, device.getBlockState().getBlock());
    }
}
