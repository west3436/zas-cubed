package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.atmos.airlock.AirSensorBlock;
import io.github.west3436.zas.atmos.airlock.AirSensorBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.gauge.GaugeReading;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * An air sensor (COMPAT-025): the air in front of its wall as it read it at its last step, what its
 * panel shows, the airlock controller that links it if one does, and the reading a comparator takes
 * of it, which a computer can turn as a sneaking use does. Linked or not it reads. Before its first
 * step its readings are {@code nil}.
 */
public final class AirSensorPeripheral extends DevicePeripheral<AirSensorBlockEntity> {
    AirSensorPeripheral(AirSensorBlockEntity sensor) {
        super("zas_air_sensor", sensor);
    }

    /** The pressure it read, kPa. */
    @LuaFunction(mainThread = true)
    public @Nullable Double getPressure() throws LuaException {
        return Lua.orNil(device().pressure());
    }

    /** The temperature it read, K; nil with no gas. */
    @LuaFunction(mainThread = true)
    public @Nullable Double getTemperature() throws LuaException {
        AtmosphereSample sample = device().sample();
        return sample == null || !(sample.mixture().totalMoles() > 0) ? null : sample.temperature();
    }

    /** Each gas's share of the moles it read, 0 to 1, by gas id. */
    @LuaFunction(mainThread = true)
    public @Nullable Map<String, Double> getGases() throws LuaException {
        AtmosphereSample sample = device().sample();
        return sample == null ? null : Lua.gases(sample.composition());
    }

    /** What its panel shows: {@code standby}, {@code cycling} or {@code alert}. */
    @LuaFunction(mainThread = true)
    public String getStatus() throws LuaException {
        BlockState state = device().getBlockState();
        return state.hasProperty(AirSensorBlock.STATUS)
                ? state.getValue(AirSensorBlock.STATUS).getSerializedName()
                : "standby";
    }

    /** Where the airlock controller that links it is, or nil if none does or it isn't loaded. */
    @LuaFunction(mainThread = true)
    public @Nullable Map<String, Integer> getController() throws LuaException {
        AirSensorBlockEntity sensor = device();
        if (!(sensor.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        return Lua.position(AirlockControllerBlockEntity.linkingSensor(level, sensor.getBlockPos())
                .map(BlockEntity::getBlockPos)
                .orElse(null));
    }

    /**
     * What a comparator reads of it: {@code pressure}, {@code temperature}, or a gas's share by its
     * name, as a gauge's readings are named.
     */
    @LuaFunction(mainThread = true)
    public String getReading() throws LuaException {
        return Lua.word(device().reading());
    }

    @LuaFunction(mainThread = true)
    public void setReading(String reading) throws LuaException {
        AirSensorBlockEntity sensor = device();
        sensor.setReading(Lua.named("reading", GaugeReading.class, reading));
    }

    /** The value of its reading: kPa, K, or a share from 0 to 1. */
    @LuaFunction(mainThread = true)
    public @Nullable Double getValue() throws LuaException {
        return Lua.orNil(device().value());
    }

    /** What a comparator reads of it, 0 to 15. */
    @LuaFunction(mainThread = true)
    public int getSignal() throws LuaException {
        return device().signal();
    }
}
