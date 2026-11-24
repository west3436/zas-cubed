package io.github.west3436.zas.atmos.airlock;

import io.github.west3436.ss13.api.label.Labelled;
import io.github.west3436.ss13.device.Comparators;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.gauge.GaugeReading;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * An air sensor's work, once per atmos tick (CONT-142): it reads the air at the middle of its own
 * block, in front of the wall, and shows {@link SensorStatus#of} by its pressure. It needs no power.
 * A controller it's linked to reads the same point for its cycle, and marks it as cycling while it
 * cycles ({@link #markCycling}).
 *
 * <p>Linked or not, it is also a sensor for redstone and computers (CONT-177): a comparator reads
 * where its {@linkplain #reading() reading}, a {@link GaugeReading} as a gauge's dial has, stands on
 * its scale, and a sneaking use with an empty hand turns it to the next. The reading is saved.
 */
public class AirSensorBlockEntity extends AtmosDevice implements Labelled {
    private static final String READING = "Reading";

    private final Comparators.Output comparator = new Comparators.Output();
    private long cyclingUntil = Long.MIN_VALUE;
    private double kpa = Double.NaN;
    private @Nullable AtmosphereSample last;
    private GaugeReading reading = GaugeReading.PRESSURE;

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
            last = sample;
        }
        show(level);
        comparator.update(level, getBlockPos(), getBlockState().getBlock(), signal());
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

    /** The air it read at its last step; null before its first. Server side. */
    public @Nullable AtmosphereSample sample() {
        return last;
    }

    /** What a comparator reads of it (CONT-177). */
    public GaugeReading reading() {
        return reading;
    }

    /** Sets what a comparator reads of it, and tells the comparators at once. Server side. */
    public void setReading(GaugeReading next) {
        if (next != reading) {
            reading = next;
            setChanged();
            if (level instanceof ServerLevel server && !isRemoved()) {
                comparator.update(server, getBlockPos(), getBlockState().getBlock(), signal());
            }
        }
    }

    /** The value of its reading at its last step: kPa, K or a share from 0 to 1; NaN before its first. */
    public double value() {
        return last == null ? Double.NaN : reading.value(last.mixture());
    }

    /**
     * What a comparator reads (CONT-177): where its reading stands on its scale, 0 to 15. A pressure
     * runs from none to 2 atm, a temperature from 0 to 1,000 K, and a gas's share from none to all.
     */
    public int signal() {
        double value = value();
        return Double.isNaN(value) ? 0 : Comparators.dial(reading.alongInRoom(value));
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (reading != GaugeReading.PRESSURE) {
            tag.putString(READING, reading.getSerializedName());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        GaugeReading saved = GaugeReading.named(tag.getString(READING));
        reading = saved == null ? GaugeReading.PRESSURE : saved;
    }
}
