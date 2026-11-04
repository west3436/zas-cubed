package io.github.west3436.zas.atmos.battery;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * What counts as a battery (CONT-112): an item in the {@code zas_atmos:batteries} tag that exposes
 * the FE item capability. The tag holds ZAS³'s three batteries (CONT-111); a pack can add other mods'
 * FE items to it. A portable's battery slot and the recharger's take only these.
 */
public final class Batteries {
    /** The battery tag. */
    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, ZasAtmosMod.id("batteries"));

    private Batteries() {}

    /** Whether a stack is a battery: in the tag, with an FE item capability. */
    public static boolean isBattery(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TAG) && stack.getCapability(Capabilities.EnergyStorage.ITEM) != null;
    }

    /** A battery's FE store, or null if the stack isn't a battery. */
    public static IEnergyStorage energy(ItemStack stack) {
        return isBattery(stack) ? stack.getCapability(Capabilities.EnergyStorage.ITEM) : null;
    }

    /**
     * A one-battery slot: it takes one item, and only a battery. A device saves it under {@link
     * #SLOT_KEY} and drops its battery when broken.
     */
    public static class Slot extends ItemStackHandler {
        /** The key a device saves its slot under. */
        public static final String SLOT_KEY = "Battery";

        private final Runnable changed;

        public Slot(Runnable changed) {
            super(1);
            this.changed = changed;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isBattery(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            changed.run();
        }

        /** The battery in the slot, or an empty stack. */
        public ItemStack battery() {
            return getStackInSlot(0);
        }

        /** The battery's FE store, or null with no battery. */
        public IEnergyStorage energy() {
            return Batteries.energy(battery());
        }

        /** Saves the slot into a device's tag. */
        public void save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.put(SLOT_KEY, serializeNBT(registries));
        }

        /** Loads what {@link #save} wrote. */
        public void load(CompoundTag tag, HolderLookup.Provider registries) {
            if (tag.contains(SLOT_KEY)) {
                deserializeNBT(registries, tag.getCompound(SLOT_KEY));
            }
        }
    }
}
