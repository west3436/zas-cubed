package io.github.west3436.zas.atmos.portable;

import io.github.west3436.zas.atmos.gear.GasTankItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** A portable's held tank (CONT-018): one slot that takes one gas tank and nothing else. */
public class TankSlot extends ItemStackHandler {
    private final Runnable changed;

    public TankSlot(Runnable changed) {
        super(1);
        this.changed = changed;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return stack.getItem() instanceof GasTankItem;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        changed.run();
    }

    /** The tank in the slot, or an empty stack. */
    public ItemStack tank() {
        return getStackInSlot(0);
    }
}
