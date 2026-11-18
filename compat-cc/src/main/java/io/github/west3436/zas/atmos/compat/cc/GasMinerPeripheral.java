package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.miner.GasMinerBlockEntity;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A creative gas miner (CONT-188): its switch, its gas, target and temperature, what it is doing and
 * the room's pressure. What its window sets, a computer sets, over the same ranges, and a gas the
 * server doesn't define is refused, as the window offers none.
 */
public final class GasMinerPeripheral extends DevicePeripheral<GasMinerBlockEntity> {
    GasMinerPeripheral(GasMinerBlockEntity miner) {
        super("zas_gas_miner", miner);
    }

    @LuaFunction(mainThread = true)
    public boolean isOn() throws LuaException {
        return device().isOn();
    }

    @LuaFunction(mainThread = true)
    public void setOn(boolean on) throws LuaException {
        device().setOn(on);
    }

    /** What it did at its last step. */
    @LuaFunction(mainThread = true)
    public String getState() throws LuaException {
        return Lua.word(device().status());
    }

    /** The gas it makes, by id. */
    @LuaFunction(mainThread = true)
    public String getGas() throws LuaException {
        return device().gas().toString();
    }

    @LuaFunction(mainThread = true)
    public void setGas(String gas) throws LuaException {
        ResourceLocation id = ResourceLocation.tryParse(gas);
        if (id == null || !device().setGas(id)) {
            throw new LuaException("no such gas: " + gas);
        }
    }

    /** Every gas it can make, by id: what its window offers. */
    @LuaFunction(mainThread = true)
    public List<String> getGases() throws LuaException {
        device();
        return GasMinerBlockEntity.gases().stream()
                .map(ResourceLocation::toString)
                .toList();
    }

    /** The pressure it fills the room to, kPa: named as a pump's is. */
    @LuaFunction(mainThread = true)
    public double getTargetPressure() throws LuaException {
        return device().target();
    }

    @LuaFunction(mainThread = true)
    public void setTargetPressure(double kPa) throws LuaException {
        device().setTarget(Lua.inRange("target", "kPa", kPa, GasMinerBlockEntity::clampTarget));
    }

    /** The temperature of the gas it makes, K. */
    @LuaFunction(mainThread = true)
    public double getTemperature() throws LuaException {
        return device().temperature();
    }

    @LuaFunction(mainThread = true)
    public void setTemperature(double kelvin) throws LuaException {
        device().setTemperature(Lua.inRange("temperature", "K", kelvin, GasMinerBlockEntity::clampTemperature));
    }

    /** The pressure in front of it at its last step, kPa, or nil while it is off or has no reading. */
    @LuaFunction(mainThread = true)
    public @Nullable Double getRoomPressure() throws LuaException {
        return Lua.orNil(device().roomPressure());
    }
}
