package io.github.west3436.zas.atmos.battery;

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
 * The recharger's GUI (CONT-113, CONT-074): its battery slot, what it is doing and its battery's
 * charge. It has no buttons; the slot takes only a battery (CONT-112), and {@link #stillValid}
 * refuses a player too far away.
 */
public class RechargerMenu extends AbstractContainerMenu {
    /** Where the battery slot is drawn. */
    public static final int BATTERY_X = 176;

    public static final int BATTERY_Y = 20;

    /** Where the player's inventory starts. */
    public static final int INVENTORY_Y = 84;

    /** The synced values: the status's ordinal, then the battery's FE and capacity in 16-bit halves. */
    public static final int DATA_STATUS = 0;

    public static final int DATA_ENERGY_LO = 1;
    public static final int DATA_ENERGY_HI = 2;
    public static final int DATA_CAPACITY_LO = 3;
    public static final int DATA_CAPACITY_HI = 4;
    public static final int DATA_COUNT = 5;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** The client's menu. */
    public RechargerMenu(int id, Inventory inventory) {
        this(
                id,
                inventory,
                new Batteries.Slot(() -> {}),
                new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL);
    }

    /** The server's menu, on a recharger. */
    public RechargerMenu(
            int id, Inventory inventory, IItemHandler battery, ContainerData data, ContainerLevelAccess access) {
        super(AtmosMenus.RECHARGER.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.access = access;
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

    /** The synced status. */
    public RechargerBlockEntity.Status status() {
        return RechargerBlockEntity.Status.of(data.get(DATA_STATUS));
    }

    /** The battery's FE. */
    public int energy() {
        return (data.get(DATA_ENERGY_LO) & 0xFFFF) | (data.get(DATA_ENERGY_HI) & 0xFFFF) << 16;
    }

    /** The battery's capacity, FE; 0 with none. */
    public int capacity() {
        return (data.get(DATA_CAPACITY_LO) & 0xFFFF) | (data.get(DATA_CAPACITY_HI) & 0xFFFF) << 16;
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
        return stillValid(access, player, AtmosBlocks.RECHARGER.get());
    }
}
