package io.github.west3436.zas.atmos.compat.jade;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.airlock.AirSensorBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlockEntity;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.heater.ThermoMachineBlockEntity;
import io.github.west3436.zas.atmos.holder.TankHolderBlockEntity;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.inline.PortedBlock;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipePort;
import io.github.west3436.zas.atmos.portable.PortableGasDevice;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveBlockEntity;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * What each of the content mod's devices reads when a player looks at it (COMPAT-030, COMPAT-031).
 * The values are the device's own, taken from its block entity: its devices offer no gas
 * capability, one with several ports holds several bodies of gas that one capability couldn't tell
 * apart, and a controller or a sensor holds no gas at all.
 *
 * <ul>
 *   <li><b>One body of gas</b> (a pipe and every device that is one node of a pipe network, a
 *       canister, a large gas tank from either half, a portable pump or scrubber): its pressure,
 *       temperature and gases. A node reads as its network does, since it holds its share of the
 *       network's gas by volume.
 *   <li><b>Several ports</b> (a pump, filter, mixer or electrolyzer): each port's pressure and
 *       temperature, and no gases.
 *   <li><b>Tanks</b>: a tank holder reads the tank a click takes out, the last one put in; a tank
 *       transfer valve reads each tank it holds, and the gases if it holds one.
 *   <li><b>A room's reading</b> (an area air controller that has power, an air sensor, an airlock
 *       controller, a heater or air conditioner that is on): the pressure or temperature the device
 *       last read, as its own window shows, and never the room's gases. Those are the analyzer's
 *       (COMPAT-032).
 *   <li><b>Anything else</b> reads nothing: a central controller, fire alarm, igniter, firelock,
 *       airlock, recharger or generator.
 * </ul>
 *
 * <p>Reading changes nothing: no network is built and nothing is marked changed. Server thread.
 */
final class DeviceReadings {
    private static final Reading.Lead[] PORTS = {Reading.Lead.IN, Reading.Lead.OUT, Reading.Lead.SIDE};

    private DeviceReadings() {}

    /**
     * Whether a block entity is of a kind that can have a reading. The client asks the server only
     * about these, so it uses nothing but the block entity's class.
     */
    static boolean reads(@Nullable BlockEntity be) {
        return be instanceof PipeNodeEntity
                || be instanceof InlineDeviceEntity
                || be instanceof PortableGasDevice
                || be instanceof TankHolderBlockEntity
                || be instanceof TransferValveBlockEntity
                || be instanceof AreaAirControllerBlockEntity
                || be instanceof AirSensorBlockEntity
                || be instanceof AirlockControllerBlockEntity
                || be instanceof ThermoMachineBlockEntity;
    }

    /**
     * What a block entity reads now.
     *
     * @return its reading, or null if it has none: it isn't one of the kinds above, or it has read
     *     nothing yet
     */
    static @Nullable Reading of(@Nullable BlockEntity be) {
        if (be == null || be.isRemoved()) {
            return null;
        }
        if (be instanceof PipeNodeEntity node) {
            GasParcel gas = node.share();
            return body(Reading.Lead.BODY, "", gas.moles(), gas.temperature(), node.nodeVolume());
        }
        if (be instanceof InlineDeviceEntity device) {
            return ports(device);
        }
        if (be instanceof PortableGasDevice portable) {
            GasStore store = portable.store();
            return body(Reading.Lead.BODY, "", store.moles(), store.temperature(), store.volume());
        }
        if (be instanceof TankHolderBlockEntity holder) {
            int count = holder.count();
            return count == 0 ? null : tanks(List.of(holder.tanks().getStackInSlot(count - 1)));
        }
        if (be instanceof TransferValveBlockEntity valve) {
            ItemStack item = valve.item();
            List<ItemStack> held = new ArrayList<>(2);
            for (int place : new int[] {TransferValve.FIRST, TransferValve.SECOND}) {
                ItemStack tank = TransferValveItem.tank(item, place);
                if (!tank.isEmpty()) {
                    held.add(tank);
                }
            }
            return tanks(held);
        }
        if (be instanceof AreaAirControllerBlockEntity controller) {
            double[] values = controller.values();
            // Pressure and temperature come first among its readings.
            return controller.powered() && values.length >= 2 ? room(values[0], values[1]) : null;
        }
        if (be instanceof AirSensorBlockEntity sensor) {
            return room(sensor.pressure(), Double.NaN);
        }
        if (be instanceof AirlockControllerBlockEntity airlock) {
            return room(airlock.chamberKpa(), Double.NaN);
        }
        if (be instanceof ThermoMachineBlockEntity machine) {
            return room(Double.NaN, machine.roomTemperature());
        }
        return null;
    }

    /** A row for each of a device's ports, in the ports' order. */
    private static @Nullable Reading ports(InlineDeviceEntity device) {
        if (!(device.getBlockState().getBlock() instanceof PortedBlock block)) {
            return null;
        }
        int count = Math.min(block.portFaces(device.getBlockState()).size(), Reading.MOST_ROWS);
        List<Reading.Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            PipePort port = device.port(i);
            GasParcel gas = port.share();
            if (device instanceof ElectrolyzerBlockEntity) {
                // Both of its ports are outlets, so each shows the gas it gives.
                String given = (i == ElectrolyzerBlockEntity.OXYGEN ? StandardAir.OXYGEN : ElectrolyzerBlockEntity.H2)
                        .toString();
                rows.add(row(Reading.Lead.GAS, given, gas.moles(), gas.temperature(), port.nodeVolume()));
            } else {
                rows.add(row(PORTS[i], "", gas.moles(), gas.temperature(), port.nodeVolume()));
            }
        }
        return new Reading(rows, List.of());
    }

    /** A row for each tank, and the gases if there is one tank. */
    private static @Nullable Reading tanks(List<ItemStack> tanks) {
        List<Reading.Row> rows = new ArrayList<>(tanks.size());
        GasParcel last = GasParcel.EMPTY;
        for (ItemStack tank : tanks) {
            if (!(tank.getItem() instanceof GasTankItem kind)) {
                continue;
            }
            last = GasTankItem.gas(tank);
            String item = BuiltInRegistries.ITEM.getKey(kind).toString();
            rows.add(row(Reading.Lead.ITEM, item, last.moles(), last.temperature(), kind.volume()));
        }
        if (rows.isEmpty()) {
            return null;
        }
        return new Reading(rows, rows.size() == 1 ? Shares.of(last.moles()) : List.of());
    }

    /** One body of gas: its row, and its gases. */
    private static Reading body(
            Reading.Lead lead, String id, Map<ResourceLocation, Double> moles, double temperature, double volume) {
        return new Reading(List.of(row(lead, id, moles, temperature, volume)), Shares.of(moles));
    }

    /** What a device last read of its room; null if it read neither value. */
    private static @Nullable Reading room(double kpa, double kelvin) {
        if (Double.isNaN(kpa) && Double.isNaN(kelvin)) {
            return null;
        }
        return Reading.of(new Reading.Row(Reading.Lead.BODY, (float) kpa, (float) kelvin));
    }

    /** A body of gas in its volume, at nRT/V. A vacuum has a pressure, 0, and no temperature. */
    private static Reading.Row row(
            Reading.Lead lead, String id, Map<ResourceLocation, Double> moles, double temperature, double volume) {
        double total = 0;
        for (double amount : moles.values()) {
            total += amount;
        }
        if (!(total > 0)) {
            return new Reading.Row(lead, id, 0, Float.NaN);
        }
        float kpa = (float) (total * GasMixture.GAS_CONSTANT * temperature / volume);
        return new Reading.Row(lead, id, kpa, (float) temperature);
    }
}
