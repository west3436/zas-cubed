package io.github.west3436.zas.atmos.airlock;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.label.Labelled;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * An air sensor's work, once per atmos tick (CONT-142): it reads the pressure at the middle of its
 * own block, in front of the wall, and shows {@link SensorStatus#of}. It needs no power. A controller
 * it's linked to reads the same point for its cycle, and marks it as cycling while it cycles
 * ({@link #markCycling}).
 */
public class AirSensorBlockEntity extends AtmosDevice implements Labelled {
    private long cyclingUntil = Long.MIN_VALUE;
    private double kpa = Double.NaN;

    public AirSensorBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.AIR_SENSOR.get(), pos, state);
    }

    /** The point it reads: the middle of its own block. */
    public Vec3 airPoint() {
        return Vec3.atCenterOf(getBlockPos());
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        AtmosphereSample sample = Atmospheres.at(level, airPoint());
        if (sample.state() != AtmosphereState.UNRESOLVED) {
            kpa = sample.pressure();
        }
        show(level);
    }

    /** Called each atmos tick by a controller that is cycling with this sensor. */
    public void markCycling(ServerLevel level) {
        boolean was = cycling();
        cyclingUntil = now() + DevicePower.LEASE_TICKS;
        if (!was) {
            show(level);
        }
    }

    /** Whether a controller it's linked to marked it as cycling within the last two atmos ticks. */
    public boolean cycling() {
        return now() < cyclingUntil;
    }

    /** The pressure it read at its last step, kPa; NaN before its first. */
    public double pressure() {
        return kpa;
    }

    private void show(ServerLevel level) {
        if (!cycling() && Double.isNaN(kpa)) {
            return;
        }
        SensorStatus status = SensorStatus.of(cycling(), kpa);
        BlockState state = getBlockState();
        if (state.hasProperty(AirSensorBlock.STATUS) && state.getValue(AirSensorBlock.STATUS) != status) {
            level.setBlock(getBlockPos(), state.setValue(AirSensorBlock.STATUS, status), Block.UPDATE_CLIENTS);
        }
    }
}
