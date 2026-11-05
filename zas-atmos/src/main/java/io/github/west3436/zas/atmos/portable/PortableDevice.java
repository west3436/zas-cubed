package io.github.west3436.zas.atmos.portable;

import io.github.west3436.zas.atmos.battery.Batteries;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * A portable's block entity (CONT-018, D-73): its battery slot (CONT-112), its switch and the air it
 * works on. It isn't a controller's part: it doesn't implement {@link
 * io.github.west3436.zas.atmos.power.PoweredDevice}, so no area air controller powers, orders or
 * lists it, and a link to it doesn't hold.
 */
public abstract class PortableDevice extends AtmosDevice implements MenuProvider {
    private static final String ON = "On";

    private final Batteries.Slot battery = new Batteries.Slot(this::setChanged);
    private boolean on;

    protected PortableDevice(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Its battery slot. */
    public Batteries.Slot battery() {
        return battery;
    }

    /** The items it drops when broken: its battery. */
    public List<ItemStack> heldItems() {
        return List.of(battery.battery());
    }

    /** The FE in its battery; 0 with none. */
    public int energy() {
        IEnergyStorage e = battery.energy();
        return e == null ? 0 : e.getEnergyStored();
    }

    /** Its battery's capacity, FE; 0 with none. */
    public int capacity() {
        IEnergyStorage e = battery.energy();
        return e == null ? 0 : e.getMaxEnergyStored();
    }

    /** Whether it is switched on. */
    public boolean on() {
        return on;
    }

    /** Switches it on or off, with a click (CONT-127). Server side. */
    public void setOn(boolean on) {
        if (on != this.on) {
            this.on = on;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.playSound(null, worldPosition, on ? switchOnSound() : switchOffSound(), SoundSource.BLOCKS);
            }
        }
    }

    /** What it plays when switched on. */
    protected abstract SoundEvent switchOnSound();

    /** What it plays when switched off. */
    protected abstract SoundEvent switchOffSound();

    /** Where it works: the air above it, in its own block's cell. */
    public Vec3 point() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 15 / 16.0, worldPosition.getZ() + 0.5);
    }

    /** Shows whether it is working, on its block. */
    protected void showWorking(ServerLevel level, boolean working) {
        BlockState state = getBlockState();
        if (state.getValue(PortableBlock.LIT) != working) {
            level.setBlock(worldPosition, state.setValue(PortableBlock.LIT, working), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * A value its GUI shows, by its menu's {@code DATA_} index. A subclass adds its own from {@link
     * PortableMenu#DATA_COUNT}.
     */
    protected int menuValue(int index) {
        return switch (index) {
            case PortableMenu.DATA_ON -> on ? 1 : 0;
            case PortableMenu.DATA_ENERGY_LO -> energy() & 0xFFFF;
            case PortableMenu.DATA_ENERGY_HI -> energy() >>> 16;
            case PortableMenu.DATA_CAPACITY_LO -> capacity() & 0xFFFF;
            case PortableMenu.DATA_CAPACITY_HI -> capacity() >>> 16;
            default -> 0;
        };
    }

    /** The {@link #menuValue}s, as a menu's data. */
    protected ContainerData menuData(int count) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return menuValue(index);
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return count;
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(ON, on);
        battery.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean(ON);
        battery.load(tag, registries);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }
}
