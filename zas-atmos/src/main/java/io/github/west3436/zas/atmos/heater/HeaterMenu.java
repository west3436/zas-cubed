package io.github.west3436.zas.atmos.heater;

import io.github.west3436.zas.atmos.battery.Batteries;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * A heater's GUI (CONT-019, CONT-074): its battery slot, on or off, its setpoint beside its room's
 * temperature, what it did last step and its battery's charge. The battery slot takes only a battery
 * (CONT-112). The client sends only button ids; the server checks each id, refuses a player too far
 * away ({@link #stillValid}), and {@link HeaterMath#clampSetpoint} keeps the setpoint within
 * 293–573 K.
 */
public class HeaterMenu extends AbstractContainerMenu {
    /** Switches it on or off. */
    public static final int TOGGLE = 0;

    /** Changes the setpoint by −10, −1, +1 and +10 K (ids 1–4). */
    public static final int SETPOINT_DOWN_10 = 1;

    public static final int LAST_BUTTON = 4;

    private static final double[] STEPS = {-10, -1, 1, 10};

    /** Where the battery slot is drawn. */
    public static final int BATTERY_X = 176;

    public static final int BATTERY_Y = 81;

    /** Where the player's inventory starts. */
    public static final int INVENTORY_Y = 134;

    /** The synced values: on, setpoint in tenths of a kelvin, ... */
    public static final int DATA_ON = 0;

    public static final int DATA_SETPOINT = 1;
    /** The room's temperature in tenths of a kelvin, or {@link #NO_ROOM}. */
    public static final int DATA_ROOM = 2;
    /** The {@link HeaterStatus}'s ordinal. */
    public static final int DATA_STATUS = 3;
    /** The heat added at the last step, W, rounded. */
    public static final int DATA_WATTS = 4;
    /** The battery's FE and capacity, each in two 16-bit halves, since menu data syncs as shorts. */
    public static final int DATA_ENERGY_LO = 5;

    public static final int DATA_ENERGY_HI = 6;
    public static final int DATA_CAPACITY_LO = 7;
    public static final int DATA_CAPACITY_HI = 8;
    public static final int DATA_COUNT = 9;

    /** {@link #DATA_ROOM}'s value when the heater has no room: open air, unresolved, paused, or off. */
    public static final int NO_ROOM = -1;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final HeaterBlockEntity heater;

    /** The client's menu. */
    public HeaterMenu(int id, Inventory inventory) {
        this(
                id,
                inventory,
                new Batteries.Slot(() -> {}),
                new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL,
                null);
    }

    /** The server's menu, on a heater. */
    public HeaterMenu(
            int id,
            Inventory inventory,
            IItemHandler battery,
            ContainerData data,
            ContainerLevelAccess access,
            HeaterBlockEntity heater) {
        super(AtmosMenus.HEATER.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.access = access;
        this.heater = heater;
        addSlot(new SlotItemHandler(battery, 0, BATTERY_X, BATTERY_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
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

    /** The battery's FE. */
    public int energy() {
        return (data.get(DATA_ENERGY_LO) & 0xFFFF) | (data.get(DATA_ENERGY_HI) & 0xFFFF) << 16;
    }

    /** The battery's capacity, FE; 0 with none. */
    public int capacity() {
        return (data.get(DATA_CAPACITY_LO) & 0xFFFF) | (data.get(DATA_CAPACITY_HI) & 0xFFFF) << 16;
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
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!Batteries.isBattery(stack) || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AtmosBlocks.HEATER.get());
    }
}
