package io.github.west3436.zas.atmos.power;

import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The generator's GUI (CONT-074): one fuel slot, which takes only furnace fuel, and the burn
 * time and stored FE. The fuel slot is the only setting, and the slot itself refuses anything else.
 */
public class GeneratorMenu extends AbstractContainerMenu {
    /** Where the fuel slot is drawn. */
    public static final int FUEL_X = 80;

    public static final int FUEL_Y = 35;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** On the server. */
    public GeneratorMenu(int id, Inventory inventory, GeneratorBlockEntity generator, ContainerData data) {
        this(
                id,
                inventory,
                generator.fuel(),
                data,
                ContainerLevelAccess.create(generator.getLevel(), generator.getBlockPos()));
    }

    /** On the client. */
    public GeneratorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(
                id,
                inventory,
                new ItemStackHandler(1) {
                    @Override
                    public boolean isItemValid(int slot, ItemStack stack) {
                        return GeneratorBlockEntity.isFuel(stack);
                    }
                },
                new SimpleContainerData(4),
                ContainerLevelAccess.NULL);
        buf.readBlockPos();
    }

    private GeneratorMenu(
            int id, Inventory inventory, IItemHandler fuel, ContainerData data, ContainerLevelAccess access) {
        super(AtmosMenus.GENERATOR.get(), id);
        this.data = data;
        this.access = access;
        addSlot(new SlotItemHandler(fuel, 0, FUEL_X, FUEL_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    /** Game ticks left on the burning fuel. */
    public int burnLeft() {
        return data.get(0);
    }

    /** Game ticks the burning fuel lasts in all. */
    public int burnTotal() {
        return data.get(1);
    }

    public int energy() {
        return data.get(2);
    }

    public int capacity() {
        return data.get(3);
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
        } else if (!GeneratorBlockEntity.isFuel(stack) || !moveItemStackTo(stack, 0, 1, false)) {
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
        return stillValid(access, player, AtmosBlocks.GENERATOR.get());
    }
}
