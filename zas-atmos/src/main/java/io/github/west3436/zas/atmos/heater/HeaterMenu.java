package io.github.west3436.zas.atmos.heater;

import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * A heater's GUI (CONT-019, CONT-074): on or off, its setpoint beside its room's temperature, its
 * network's temperature, what it did last step and whether it has power. The client sends only button ids; the server checks each
 * id, refuses a player too far away ({@link #stillValid}), and {@link HeaterMath#clampSetpoint}
 * keeps the setpoint within 293–573 K.
 */
public class HeaterMenu extends AbstractContainerMenu {
    /** Switches it on or off. */
    public static final int TOGGLE = 0;

    /** Changes the setpoint by −10, −1, +1 and +10 K (ids 1–4). */
    public static final int SETPOINT_DOWN_10 = 1;

    public static final int LAST_BUTTON = 4;

    private static final double[] STEPS = {-10, -1, 1, 10};

    /**
     * The synced values: on, setpoint and network temperature in tenths of a kelvin, status, power,
     * watts, and the room's temperature in tenths of a kelvin.
     */
    public static final int DATA_ON = 0;

    public static final int DATA_SETPOINT = 1;
    public static final int DATA_NETWORK = 2;
    /** The {@link HeaterStatus}'s ordinal. */
    public static final int DATA_STATUS = 3;

    public static final int DATA_POWERED = 4;
    /** The heat put into the network at the last step, W, rounded. */
    public static final int DATA_WATTS = 5;
    /** The room's temperature in tenths of a kelvin, or {@link #NO_ROOM}. */
    public static final int DATA_ROOM = 6;

    public static final int DATA_COUNT = 7;

    /** {@link #DATA_ROOM}'s value when the heater has no room: open air, unresolved, paused, or off. */
    public static final int NO_ROOM = -1;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final HeaterBlockEntity heater;

    /** The client's menu. */
    public HeaterMenu(int id, Inventory inventory) {
        this(id, new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL, null);
    }

    /** The server's menu, on a heater. */
    public HeaterMenu(int id, ContainerData data, ContainerLevelAccess access, HeaterBlockEntity heater) {
        super(AtmosMenus.HEATER.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.access = access;
        this.heater = heater;
        addDataSlots(data);
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    /** The synced status. */
    public HeaterStatus status() {
        return HeaterStatus.of(data.get(DATA_STATUS));
    }

    /** Applies a button: the server's half of the GUI. Unknown ids are refused. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (heater == null || heater.isRemoved() || id < TOGGLE || id > LAST_BUTTON) {
            return false;
        }
        if (id == TOGGLE) {
            heater.setOn(!heater.on());
        } else {
            heater.setSetpoint(heater.setpoint() + STEPS[id - SETPOINT_DOWN_10]);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AtmosBlocks.HEATER.get());
    }
}
