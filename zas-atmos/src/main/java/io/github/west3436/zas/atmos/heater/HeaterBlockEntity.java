package io.github.west3436.zas.atmos.heater;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
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

/**
 * A heater's block entity (CONT-019, CONT-087): a node with a port buffer (200 L) that heats its
 * whole network's gas, by at most {@code [power] heater_watts} (5 kW). Vents on the same network
 * carry the heat into a room.
 *
 * <p>Its setpoint, 293–573 K, is for the room: the zone its {@link #airPoint} is in. Each atmos tick
 * it reads that zone and heats its network's gas toward {@link HeaterMath#roomTarget}, as hot as the
 * room needs, up to 573 K, and adds nothing once the room is at or above the setpoint. With no zone
 * there (open air, unresolved or paused) it heats its network's gas toward the setpoint instead. It
 * never cools.
 *
 * <p>It runs only while switched on and powered (CONT-060), drawing {@code [power] heater} FE/t
 * (60, CONT-087) whenever it is on, in the auxiliary group (CONT-064): an area air controller
 * powers it after its vents. Its point for a controller's zone (CONT-068) is the air above it, in
 * its own block's cell.
 *
 * <p>It changes only its network's gas, which the content mod holds, so it calls no API mutation.
 */
public class HeaterBlockEntity extends PipeNodeEntity implements PoweredDevice, MenuProvider {
    private static final String ON = "On";
    private static final String SETPOINT = "Setpoint";

    private final DevicePower power = new DevicePower(PowerPriority.AUXILIARY, this::currentDraw, this::now);
    private boolean on;
    private double setpoint = HeaterMath.DEFAULT_SETPOINT;
    private HeaterStatus status = HeaterStatus.OFF;
    private double watts;
    private double room = Double.NaN;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case HeaterMenu.DATA_ON -> on ? 1 : 0;
                case HeaterMenu.DATA_SETPOINT -> tenths(setpoint);
                case HeaterMenu.DATA_NETWORK -> tenths(networkTemperature());
                case HeaterMenu.DATA_STATUS -> status.ordinal();
                case HeaterMenu.DATA_POWERED -> power.isPowered() ? 1 : 0;
                case HeaterMenu.DATA_WATTS -> (int) Math.round(watts);
                case HeaterMenu.DATA_ROOM -> Double.isNaN(room) ? HeaterMenu.NO_ROOM : tenths(room);
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
        super(AtmosBlockEntities.HEATER.get(), pos, state, PipeVolumes.PORT);
    }

    /** A temperature in tenths of a kelvin, capped to fit menu data's shorts. */
    private static int tenths(double kelvin) {
        return (int) Math.max(0, Math.min(Short.MAX_VALUE, Math.round(kelvin * 10)));
    }

    /** What the heater draws now: its configured draw while on (CONT-087). */
    private int currentDraw() {
        return on ? AtmosServerConfig.get(AtmosServerConfig.HEATER_DRAW) : 0;
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

    /** The heat it put into its network at its last step, as a power in W. */
    public double watts() {
        return watts;
    }

    /** Whether it is running: on and powered. */
    public boolean running() {
        return on && power.isPowered();
    }

    @Override
    public DevicePower power() {
        return power;
    }

    /** The air above the heater, in its block's cell. */
    @Override
    public Vec3 airPoint() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 15 / 16.0, worldPosition.getZ() + 0.5);
    }

    /**
     * The temperature of the room it regulates, the zone at its air point, as of its last step;
     * NaN when there was none or it was off. Server side.
     */
    public double roomTemperature() {
        return room;
    }

    /** Its network's gas temperature, K; 0 if removed. Server side. */
    public double networkTemperature() {
        return isRemoved() ? 0 : network().gas().temperature();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        watts = 0;
        room = Double.NaN;
        if (!on) {
            status = HeaterStatus.OFF;
        } else {
            AtmosphereSample air = Atmospheres.at(level, airPoint());
            if (air.state() == AtmosphereState.ZONED) {
                room = air.temperature();
            }
            status = power.isPowered() ? heat(network().gas(), air) : HeaterStatus.UNPOWERED;
        }
        boolean lit = status == HeaterStatus.HEATING;
        BlockState state = getBlockState();
        if (state.getValue(HeaterBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(HeaterBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    /** One step's heating of the network's gas, for the room at its air point or without one. */
    private HeaterStatus heat(GasStore net, AtmosphereSample air) {
        double c = net.heatCapacity();
        if (net.isEmpty() || !(c > 0)) {
            return HeaterStatus.NETWORK_EMPTY;
        }
        double target;
        if (Double.isNaN(room)) {
            target = setpoint;
        } else if (room >= setpoint) {
            return HeaterStatus.AT_SETPOINT;
        } else {
            target = HeaterMath.roomTarget(
                    GasProperties.heatCapacity(air.mixture().moles()), room, c, setpoint);
        }
        double maxWatts = AtmosServerConfig.get(AtmosServerConfig.HEATER_WATTS);
        double joules = HeaterMath.heat(c, net.temperature(), target, AtmosTicks.SECONDS, maxWatts);
        if (joules <= 0) {
            if (Double.isNaN(room)) {
                return HeaterStatus.AT_SETPOINT;
            }
            return target >= HeaterMath.MAX_GAS ? HeaterStatus.AT_LIMIT : HeaterStatus.CARRYING;
        }
        net.setTemperature(Math.min(target, net.temperature() + joules / c));
        watts = joules / AtmosTicks.SECONDS;
        return HeaterStatus.HEATING;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(ON, on);
        tag.putDouble(SETPOINT, setpoint);
        power.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean(ON);
        setpoint = tag.contains(SETPOINT)
                ? HeaterMath.clampSetpoint(tag.getDouble(SETPOINT))
                : HeaterMath.DEFAULT_SETPOINT;
        power.load(tag);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.heater");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HeaterMenu(id, data, ContainerLevelAccess.create(level, worldPosition), this);
    }
}
