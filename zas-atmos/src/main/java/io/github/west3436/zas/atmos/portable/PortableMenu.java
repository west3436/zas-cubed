package io.github.west3436.zas.atmos.portable;

import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A portable's GUI (CONT-018, CONT-074): its slots, which take only what they hold (CONT-112), its
 * switch and its battery's charge, above the player's inventory. The client sends only button ids;
 * the server checks each id and refuses a player too far away ({@link #stillValid}). A subclass adds
 * its own buttons from {@link #TOGGLE} + 1 and its own values from {@link #DATA_COUNT}.
 */
public abstract class PortableMenu extends AbstractContainerMenu {
    /** Switches it on or off. */
    public static final int TOGGLE = 0;

    /** The synced values: on or off, ... */
    public static final int DATA_ON = 0;

    /** The battery's FE and capacity, each in two 16-bit halves, since menu data syncs as shorts. */
    public static final int DATA_ENERGY_LO = 1;

    public static final int DATA_ENERGY_HI = 2;
    public static final int DATA_CAPACITY_LO = 3;
    public static final int DATA_CAPACITY_HI = 4;
    /** How many values every portable syncs. */
    public static final int DATA_COUNT = 5;

    private final int deviceSlots;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final Block block;
    private final PortableDevice device;

    /**
     * @param type the menu's type
     * @param id the menu's id
     * @param inventory the player's inventory
     * @param slots the portable's own slots, which a shift-click fills in order
     * @param inventoryY where the player's inventory starts
     * @param data the synced values
     * @param dataCount how many there must be
     * @param access where the portable stands
     * @param block its block
     * @param device the portable; null on the client
     */
    protected PortableMenu(
            MenuType<?> type,
            int id,
            Inventory inventory,
            List<Slot> slots,
            int inventoryY,
            ContainerData data,
            int dataCount,
            ContainerLevelAccess access,
            Block block,
            PortableDevice device) {
        super(type, id);
        checkContainerDataCount(data, dataCount);
        this.deviceSlots = slots.size();
        this.data = data;
        this.access = access;
        this.block = block;
        this.device = device;
        slots.forEach(this::addSlot);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, inventoryY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, inventoryY + 58));
        }
        addDataSlots(data);
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    /** The battery's FE. */
    public int energy() {
        return (data.get(DATA_ENERGY_LO) & 0xFFFF) | (data.get(DATA_ENERGY_HI) & 0xFFFF) << 16;
    }

    /** The battery's capacity, FE; 0 with none. */
    public int capacity() {
        return (data.get(DATA_CAPACITY_LO) & 0xFFFF) | (data.get(DATA_CAPACITY_HI) & 0xFFFF) << 16;
    }

    /** Whether the server can apply a button: it has a portable, and the portable is still there. */
    protected boolean live() {
        return device != null && !device.isRemoved();
    }

    /** Applies a button: the server's half of the GUI. Unknown ids are refused. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != TOGGLE || !live()) {
            return false;
        }
        device.setOn(!device.on());
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
        boolean moved = index < deviceSlots
                ? moveItemStackTo(stack, deviceSlots, slots.size(), true)
                : moveItemStackTo(stack, 0, deviceSlots, false); // each slot takes only what it holds
        if (!moved) {
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
        return stillValid(access, player, block);
    }
}
