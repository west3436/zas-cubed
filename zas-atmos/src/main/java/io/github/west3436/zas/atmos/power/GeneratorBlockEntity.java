package io.github.west3436.zas.atmos.power;

import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.api.client.LoopRequest;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.DeviceActivity;
import io.github.west3436.zas.atmos.device.DeviceData;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The generator's fuel, burn and FE store (CONT-061, CONT-066). Each game tick while it burns it
 * makes {@code [power] generator_output} FE (40 by default) into its store. It burns anything a
 * furnace burns ({@link #isFuel}): one coal burns for {@code generator_seconds_per_fuel} seconds
 * (80), and any other fuel for that times its furnace burn time over coal's ({@link #burnTicks}).
 * It starts a new piece only while its store has room, so a full generator with nothing drawing
 * from it goes out instead of wasting fuel. Its store gives FE to any block that takes it, and its
 * fuel slot takes fuel from hoppers. It never touches the air (D-35, CONT-067).
 */
public class GeneratorBlockEntity extends BlockEntity implements MenuProvider {
    /** How far its running loop is heard, in blocks: as far as any device's. */
    private static final double LOOP_RANGE = 8;

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

    /** A coal's furnace burn time, in game ticks: the fuel {@code generator_seconds_per_fuel} is for. */
    public static final int COAL_BURN_TIME = 1600;

    /**
     * Whether an item is generator fuel (CONT-066): anything with a furnace burn time, which includes
     * NeoForge's furnace fuel data map and the tags vanilla's fuels come from. A fuel that leaves an
     * item behind, such as a lava bucket's empty bucket, isn't: the slot has nowhere to put it.
     */
    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING) > 0 && !stack.hasCraftingRemainingItem();
    }

    /**
     * How long one of a fuel burns, in game ticks: {@code generator_seconds_per_fuel} for one coal,
     * scaled by the fuel's furnace burn time over coal's 1,600 ticks, and at least 1.
     */
    public static int burnTicks(ItemStack stack) {
        long perCoal = AtmosServerConfig.get(AtmosServerConfig.GENERATOR_SECONDS_PER_FUEL) * 20L;
        long ticks = Math.round(perCoal * (double) stack.getBurnTime(RecipeType.SMELTING) / COAL_BURN_TIME);
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, ticks));
    }

    /** One game tick: burn, make FE, start the next piece of fuel, and light or put out the block. */
    public void serverTick() {
        if (burnLeft > 0) {
            burnLeft--;
            energy.fill(AtmosServerConfig.get(AtmosServerConfig.GENERATOR_OUTPUT));
        }
        ItemStack next = fuel.extractItem(0, 1, true);
        if (burnLeft <= 0 && energy.space() > 0 && isFuel(next)) {
            fuel.extractItem(0, 1, false);
            burnTotal = burnTicks(next);
            burnLeft = burnTotal;
            setChanged();
        }
        boolean lit = burnLeft > 0;
        BlockState state = getBlockState();
        if (level != null && state.getValue(GeneratorBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(GeneratorBlock.LIT, lit), 3);
            level.playSound(
                    null,
                    worldPosition,
                    (lit ? AtmosSounds.GENERATOR_ON : AtmosSounds.GENERATOR_OFF).get(),
                    SoundSource.BLOCKS);
        }
    }

    /**
     * One game tick on a client: the running loop, for as long as the block shows it burning
     * (CONT-127). The generator isn't an {@code AtmosDevice}, so it has no device activity, but its
     * {@link GeneratorBlock#LIT} already reaches every client. A block entity that has unloaded or
     * been broken has no ticker, so its request lapses and the loop fades.
     *
     * @param state its block state
     * @param pos where it is
     */
    static void clientTick(BlockState state, BlockPos pos) {
        if (state.getValue(GeneratorBlock.LIT)) {
            ClientSounds.loop(new LoopRequest(
                    pos.immutable(),
                    AtmosSounds.GENERATOR_RUN.get(),
                    SoundSource.BLOCKS,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    DeviceActivity.volume(DeviceActivity.WORKING),
                    1f,
                    LOOP_RANGE));
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

    /** Game ticks the burning piece of fuel burns for in all; 0 before the first. */
    public int burnTotal() {
        return burnTotal;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        DeviceData.writeVersion(tag);
        tag.put("Fuel", fuel.serializeNBT(registries));
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("BurnLeft", burnLeft);
        tag.putInt("BurnTotal", burnTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        DeviceData.migrate(tag);
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
