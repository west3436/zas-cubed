package io.github.west3436.zas.atmos.heater;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.api.mutation.HeatResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.battery.Batteries;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * A heater's block entity (CONT-019, CONT-087): a portable that heats the zone it stands in, on a
 * battery in its slot (CONT-112, D-73).
 *
 * <p>Each atmos tick, while it is on and has charge, it reads the zone at its {@link #point()} and
 * adds heat to it through API-013's heat mutation: what brings the zone to its setpoint (293–573 K),
 * but at most {@code [power] heater_watts} (5 kW) over the tick, and nothing at or above the
 * setpoint. With no zone there (open air, unresolved or paused) it does nothing. It never cools.
 *
 * <p>It draws from its battery only while it heats: {@code [power] heater} FE/t (60) at full power,
 * and in proportion below it. With too little charge left it adds what the charge pays for, then
 * stops. It isn't a controller's part: no area air controller powers, orders or lists it.
 *
 * <p>One heat mutation is outstanding at a time, so each step reads the zone after the last one
 * landed.
 */
public class HeaterBlockEntity extends AtmosDevice implements MenuProvider {
    /** Who the heat comes from, in the conservation ledger. */
    public static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "heater");

    private static final String ON = "On";
    private static final String SETPOINT = "Setpoint";

    private final Batteries.Slot battery = new Batteries.Slot(this::setChanged);
    private boolean on;
    private double setpoint = HeaterMath.DEFAULT_SETPOINT;
    private HeaterStatus status = HeaterStatus.OFF;
    private double watts;
    private double room = Double.NaN;
    private CompletableFuture<HeatResult> pending;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case HeaterMenu.DATA_ON -> on ? 1 : 0;
                case HeaterMenu.DATA_SETPOINT -> tenths(setpoint);
                case HeaterMenu.DATA_ROOM -> Double.isNaN(room) ? HeaterMenu.NO_ROOM : tenths(room);
                case HeaterMenu.DATA_STATUS -> status.ordinal();
                case HeaterMenu.DATA_WATTS -> (int) Math.round(watts);
                case HeaterMenu.DATA_ENERGY_LO -> energy() & 0xFFFF;
                case HeaterMenu.DATA_ENERGY_HI -> energy() >>> 16;
                case HeaterMenu.DATA_CAPACITY_LO -> capacity() & 0xFFFF;
                case HeaterMenu.DATA_CAPACITY_HI -> capacity() >>> 16;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return HeaterMenu.DATA_COUNT;
        }
    };

    public HeaterBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.HEATER.get(), pos, state);
    }

    /** A temperature in tenths of a kelvin, capped to fit menu data's shorts. */
    private static int tenths(double kelvin) {
        return (int) Math.max(0, Math.min(Short.MAX_VALUE, Math.round(kelvin * 10)));
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

    /** Whether it is switched on. */
    public boolean on() {
        return on;
    }

    /** Its setpoint, K. */
    public double setpoint() {
        return setpoint;
    }

    /** Switches it on or off. Server side. */
    public void setOn(boolean on) {
        if (on != this.on) {
            this.on = on;
            setChanged();
        }
    }

    /** Sets its setpoint, clamped to 293–573 K (CONT-019). Server side. */
    public void setSetpoint(double kelvin) {
        double next = HeaterMath.clampSetpoint(kelvin);
        if (next != setpoint) {
            setpoint = next;
            setChanged();
        }
    }

    /** What it did at its last step. */
    public HeaterStatus status() {
        return status;
    }

    /** The heat it added at its last step, as a power in W. */
    public double watts() {
        return watts;
    }

    /**
     * The temperature of its room, the zone at its point, as of its last step; NaN when there was
     * none or it was off. Server side.
     */
    public double roomTemperature() {
        return room;
    }

    /** Where it reads and heats: the air above it, in its own block's cell. */
    public Vec3 point() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 15 / 16.0, worldPosition.getZ() + 0.5);
    }

    @Override
    public boolean readyToStep() {
        return pending == null || pending.isDone();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        pending = null;
        watts = 0;
        room = Double.NaN;
        if (on) {
            AtmosphereSample air = Atmospheres.at(level, point());
            if (air.state() == AtmosphereState.ZONED) {
                room = air.temperature();
            }
            status = heat(level, air);
        } else {
            status = HeaterStatus.OFF;
        }
        boolean lit = status == HeaterStatus.HEATING;
        BlockState state = getBlockState();
        if (state.getValue(HeaterBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(HeaterBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    /** One step's heating of its room. */
    private HeaterStatus heat(ServerLevel level, AtmosphereSample air) {
        IEnergyStorage energy = battery.energy();
        if (energy == null) {
            return HeaterStatus.NO_BATTERY;
        }
        if (Double.isNaN(room)) {
            return HeaterStatus.NO_ROOM;
        }
        if (room >= setpoint) {
            return HeaterStatus.AT_SETPOINT;
        }
        double maxWatts = AtmosServerConfig.get(AtmosServerConfig.HEATER_WATTS);
        double c = GasProperties.heatCapacity(air.mixture().moles());
        double joules = HeaterMath.heat(c, room, setpoint, AtmosTicks.SECONDS, maxWatts);
        if (joules <= 0) {
            return HeaterStatus.AT_SETPOINT;
        }
        int cost = HeaterMath.energy(
                joules,
                AtmosTicks.SECONDS,
                maxWatts,
                AtmosServerConfig.get(AtmosServerConfig.HEATER_DRAW),
                AtmosTicks.PERIOD);
        if (cost > 0) {
            int paid = energy.extractEnergy(cost, false);
            if (paid <= 0) {
                return HeaterStatus.BATTERY_EMPTY;
            }
            setChanged();
            joules *= (double) paid / cost;
        }
        pending = HeatMutations.add(level, point(), joules, SOURCE);
        watts = joules / AtmosTicks.SECONDS;
        return HeaterStatus.HEATING;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(ON, on);
        tag.putDouble(SETPOINT, setpoint);
        battery.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean(ON);
        setpoint = tag.contains(SETPOINT)
                ? HeaterMath.clampSetpoint(tag.getDouble(SETPOINT))
                : HeaterMath.DEFAULT_SETPOINT;
        battery.load(tag, registries);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.heater");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HeaterMenu(id, inventory, battery, data, ContainerLevelAccess.create(level, worldPosition), this);
    }
}
