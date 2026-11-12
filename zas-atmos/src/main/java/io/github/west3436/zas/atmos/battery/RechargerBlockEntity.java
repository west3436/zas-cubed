package io.github.west3436.zas.atmos.battery;

import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.label.Labelled;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The recharger's block entity (CONT-113): an area air controller's part, powered like a vent
 * through its zone or a link, in the last priority group (CONT-064). While it holds a battery that
 * isn't full it draws {@code [power] recharger} FE/t (40) and puts {@code [power] recharger_rate}
 * FE/t (28) into the battery; full, empty-handed or unpowered, it draws and does nothing.
 *
 * <p>It charges only on power paid for, so a lease its controller gave it while it drew nothing
 * charges nothing ({@link DevicePower#paidFor}).
 */
public class RechargerBlockEntity extends AtmosDevice implements PoweredDevice, Labelled {
    /** What the recharger did at its last atmos tick, which its sounds follow. */
    public enum Status {
        /** Its slot is empty. */
        NO_BATTERY,
        /** Its battery is full. */
        FULL,
        /** Its battery isn't full, and no controller powers it. */
        UNPOWERED,
        /** Charging its battery. */
        CHARGING
    }

    private final Batteries.Slot battery = new Batteries.Slot(this::batteryChanged);
    private final DevicePower power = new DevicePower(PowerPriority.AUXILIARY, this::currentDraw, this::now);
    private Status status = Status.NO_BATTERY;

    public RechargerBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.RECHARGER.get(), pos, state);
    }

    /** Its battery slot. */
    public Batteries.Slot battery() {
        return battery;
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

    /** What it did at its last atmos tick. */
    public Status status() {
        return status;
    }

    /** Whether it holds a battery with room for more charge. */
    public boolean chargeable() {
        IEnergyStorage e = battery.energy();
        return e != null && e.receiveEnergy(1, true) > 0;
    }

    private int currentDraw() {
        return chargeable() ? AtmosServerConfig.get(AtmosServerConfig.RECHARGER_DRAW) : 0;
    }

    @Override
    public DevicePower power() {
        return power;
    }

    /** Its own cell: the recharger stands in its room. */
    @Override
    public Vec3 airPoint() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 15 / 16.0, worldPosition.getZ() + 0.5);
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        Status was = status;
        IEnergyStorage e = battery.energy();
        if (e == null) {
            status = Status.NO_BATTERY;
        } else if (e.receiveEnergy(1, true) <= 0) {
            status = Status.FULL;
        } else if (!paid()) {
            status = Status.UNPOWERED;
        } else {
            int rate = AtmosServerConfig.get(AtmosServerConfig.RECHARGER_RATE);
            if (e.receiveEnergy(rate * AtmosTicks.PERIOD, false) > 0) {
                setChanged();
            }
            status = Status.CHARGING;
        }
        if (status == Status.CHARGING) {
            reportRunning();
        } else {
            reportStopped();
        }
        sound(level, was);
        showCharge(level);
    }

    /**
     * Plays what a change of status sounds like (CONT-127): it starts charging, stops short, or
     * finishes. Neither its status nor its lease is saved, so one that was charging when its chunk left
     * starts again unpowered, silently, and sounds as it starts charging once more.
     */
    private void sound(ServerLevel level, Status was) {
        if (status == was || (status != Status.CHARGING && was != Status.CHARGING)) {
            return;
        }
        SoundEvent sound = status == Status.CHARGING
                ? AtmosSounds.RECHARGER_ON.get()
                : status == Status.FULL ? AtmosSounds.RECHARGER_DONE.get() : AtmosSounds.RECHARGER_OFF.get();
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS);
    }

    /** Whether it has power to charge with: paid for, or any power at all when it is set to draw nothing. */
    private boolean paid() {
        return power.paidFor() || (AtmosServerConfig.get(AtmosServerConfig.RECHARGER_DRAW) == 0 && power.isPowered());
    }

    private void batteryChanged() {
        setChanged();
        if (level instanceof ServerLevel server) {
            showCharge(server);
        }
    }

    /** Sets the block's battery and gauge properties from the battery. */
    private void showCharge(ServerLevel level) {
        BlockState state = getBlockState();
        if (!state.hasProperty(RechargerBlock.BATTERY)) {
            return;
        }
        boolean has = battery.energy() != null;
        int gauge = has ? RechargerBlock.gauge(energy(), capacity()) : 0;
        if (state.getValue(RechargerBlock.BATTERY) != has || state.getValue(RechargerBlock.CHARGE) != gauge) {
            level.setBlock(
                    worldPosition,
                    state.setValue(RechargerBlock.BATTERY, has).setValue(RechargerBlock.CHARGE, gauge),
                    Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        battery.save(tag, registries);
        power.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        battery.load(tag, registries);
        power.load(tag);
    }
}
