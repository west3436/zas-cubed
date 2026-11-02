package io.github.west3436.zas.atmos.power;

import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The generator's fuel, burn and FE store (CONT-061, CONT-066). Each game tick while it burns it
 * makes {@code [power] generator_output} FE (40 by default) into its store, and one coal or charcoal
 * ({@code #minecraft:coals}) burns for {@code generator_seconds_per_fuel} seconds (80). It starts a
 * new piece only while its store has room, so a full generator with nothing drawing from it goes
 * out instead of wasting fuel. Its store gives FE to any block that takes it, and its fuel slot
 * takes coal from hoppers.
 */
public class GeneratorBlockEntity extends BlockEntity implements MenuProvider {
    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final EnergyStore energy = new EnergyStore(
            () -> AtmosServerConfig.get(AtmosServerConfig.GENERATOR_BUFFER), false, true, this::setChanged);

    private int burnLeft;
    private int burnTotal;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> burnLeft;
                case 1 -> burnTotal;
                case 2 -> energy.getEnergyStored();
                case 3 -> energy.getMaxEnergyStored();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {}

        @Override
        public int getCount() {
            return 4;
        }
    };

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.GENERATOR.get(), pos, state);
    }

    /** Whether an item is generator fuel: coal or charcoal. */
    public static boolean isFuel(ItemStack stack) {
        return stack.is(ItemTags.COALS);
    }

    /** One game tick: burn, make FE, start the next piece of fuel, and light or put out the block. */
    public void serverTick() {
        if (burnLeft > 0) {
            burnLeft--;
            energy.fill(AtmosServerConfig.get(AtmosServerConfig.GENERATOR_OUTPUT));
        }
        if (burnLeft <= 0 && energy.space() > 0 && !fuel.extractItem(0, 1, true).isEmpty()) {
            fuel.extractItem(0, 1, false);
            burnTotal = AtmosServerConfig.get(AtmosServerConfig.GENERATOR_SECONDS_PER_FUEL) * 20;
            burnLeft = burnTotal;
            setChanged();
        }
        boolean lit = burnLeft > 0;
        BlockState state = getBlockState();
        if (level != null && state.getValue(GeneratorBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(GeneratorBlock.LIT, lit), 3);
        }
    }

    /** The fuel slot. */
    public ItemStackHandler fuel() {
        return fuel;
    }

    /** The FE store. */
    public EnergyStore energy() {
        return energy;
    }

    /** Game ticks left on the burning piece of fuel. */
    public int burnLeft() {
        return burnLeft;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fuel", fuel.serializeNBT(registries));
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("BurnLeft", burnLeft);
        tag.putInt("BurnTotal", burnTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Fuel")) {
            fuel.deserializeNBT(registries, tag.getCompound("Fuel"));
        }
        energy.set(tag.getInt("Energy"));
        burnLeft = Math.max(0, tag.getInt("BurnLeft"));
        burnTotal = Math.max(burnLeft, tag.getInt("BurnTotal"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new GeneratorMenu(id, inventory, this, data);
    }
}
