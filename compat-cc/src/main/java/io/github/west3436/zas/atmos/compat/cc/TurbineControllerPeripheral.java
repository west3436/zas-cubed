package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.turbine.CompressorBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlockEntity.Part;
import io.github.west3436.zas.atmos.turbine.TurbineFuels;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A gas turbine controller (CONT-163, CONT-190): the engine's switch, fuel, oxidizer, burn rate and
 * auto relight, its igniter's spark, what the engine is doing, its shaft, chamber and compressor's
 * buffer, its FE, its links, and its redstone setting (COMPAT-023). What its window sets, a computer
 * sets, over the same ranges; a gas the engine doesn't burn is refused, as the window offers none. The
 * turbine and the compressor are no peripherals: the controller is the engine's one face.
 */
public final class TurbineControllerPeripheral extends DevicePeripheral<TurbineControllerBlockEntity> {
    TurbineControllerPeripheral(TurbineControllerBlockEntity controller) {
        super("zas_turbine_controller", controller);
    }

    @LuaFunction(mainThread = true)
    public boolean isOn() throws LuaException {
        return device().on();
    }

    @LuaFunction(mainThread = true)
    public void setOn(boolean on) throws LuaException {
        device().setOn(on);
    }

    /** What the engine did at its last step: off, unlinked, no_compressor, chamber_open, no_fuel, idle or running. */
    @LuaFunction(mainThread = true)
    public String getState() throws LuaException {
        return Lua.word(device().status());
    }

    /** The fuel its compressor feeds, by id, or nil. */
    @LuaFunction(mainThread = true)
    public @Nullable String getFuel() throws LuaException {
        ResourceLocation fuel = device().fuel();
        return fuel == null ? null : fuel.toString();
    }

    @LuaFunction(mainThread = true)
    public void setFuel(String gas) throws LuaException {
        ResourceLocation id = ResourceLocation.tryParse(gas);
        if (id == null || !device().setFuel(id)) {
            throw new LuaException("not a fuel: " + gas);
        }
    }

    /** The oxidizer its compressor feeds, by id, or nil. */
    @LuaFunction(mainThread = true)
    public @Nullable String getOxidizer() throws LuaException {
        ResourceLocation oxidizer = device().oxidizer();
        return oxidizer == null ? null : oxidizer.toString();
    }

    @LuaFunction(mainThread = true)
    public void setOxidizer(String gas) throws LuaException {
        ResourceLocation id = ResourceLocation.tryParse(gas);
        if (id == null || !device().setOxidizer(id)) {
            throw new LuaException("not an oxidizer: " + gas);
        }
    }

    /** Every fuel it can feed, by id: what its window offers. */
    @LuaFunction(mainThread = true)
    public List<String> getFuels() throws LuaException {
        device();
        return TurbineFuels.fuels().stream().map(ResourceLocation::toString).toList();
    }

    /** Every oxidizer it can feed, by id: what its window offers. */
    @LuaFunction(mainThread = true)
    public List<String> getOxidizers() throws LuaException {
        device();
        return TurbineFuels.oxidizers().stream().map(ResourceLocation::toString).toList();
    }

    /** Its burn rate, mol of fuel per second. */
    @LuaFunction(mainThread = true)
    public double getBurnRate() throws LuaException {
        return device().burnRate();
    }

    @LuaFunction(mainThread = true)
    public void setBurnRate(double molPerSecond) throws LuaException {
        TurbineControllerBlockEntity controller = device();
        controller.setBurnRate(
                Lua.inRange("burn rate", "mol/s", molPerSecond, TurbineControllerBlockEntity::clampBurnRate));
    }

    /** The most its burn rate may be set to, mol/s. */
    @LuaFunction(mainThread = true)
    public double getMaxBurnRate() throws LuaException {
        device();
        return TurbineControllerBlockEntity.maxBurnRate();
    }

    /** Whether it relights its chamber by itself. */
    @LuaFunction(mainThread = true)
    public boolean isAutoIgnite() throws LuaException {
        return device().autoIgnite();
    }

    @LuaFunction(mainThread = true)
    public void setAutoIgnite(boolean auto) throws LuaException {
        device().setAutoIgnite(auto);
    }

    /** Sparks its igniter once; false if it has none linked. */
    @LuaFunction(mainThread = true)
    public boolean ignite() throws LuaException {
        return device().ignite();
    }

    /** Whether each part is linked and there: {@code turbine}, {@code compressor} and {@code igniter}. */
    @LuaFunction(mainThread = true)
    public Map<String, Boolean> getLinks() throws LuaException {
        TurbineControllerBlockEntity controller = device();
        Map<String, Boolean> table = new LinkedHashMap<>();
        for (Part part : Part.values()) {
            table.put(Lua.word(part), controller.linked(part));
        }
        return table;
    }

    /** Its turbine's shaft speed, rpm; 0 with no turbine. */
    @LuaFunction(mainThread = true)
    public double getRpm() throws LuaException {
        TurbineBlockEntity turbine = device().turbine();
        return turbine == null ? 0 : turbine.rpm();
    }

    /** The shaft's top speed, rpm. */
    @LuaFunction(mainThread = true)
    public int getMaxRpm() throws LuaException {
        device();
        return AtmosServerConfig.get(AtmosServerConfig.TURBINE_MAX_RPM);
    }

    /**
     * The chamber at the turbine's last step: {@code pressure} (kPa) and {@code temperature} (K), 0
     * while open; nil with no turbine.
     */
    @LuaFunction(mainThread = true)
    public @Nullable Map<String, Double> getChamber() throws LuaException {
        TurbineBlockEntity turbine = device().turbine();
        if (turbine == null) {
            return null;
        }
        Map<String, Double> table = new LinkedHashMap<>();
        table.put("pressure", turbine.chamberPressure());
        table.put("temperature", turbine.chamberTemperature());
        return table;
    }

    /**
     * The compressor's buffer: {@code pressure} (kPa), {@code temperature} (K) and {@code contents}, the
     * moles of each gas; nil with no compressor.
     */
    @LuaFunction(mainThread = true)
    public @Nullable Map<String, Object> getBuffer() throws LuaException {
        CompressorBlockEntity compressor = device().compressor();
        if (compressor == null) {
            return null;
        }
        GasMixture buffer = compressor.buffer();
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("pressure", buffer.pressure());
        table.put("temperature", buffer.temperature());
        table.put("contents", Lua.gases(buffer.moles()));
        return table;
    }

    /** What the turbine's generator made over its last step, FE/t. */
    @LuaFunction(mainThread = true)
    public double getPower() throws LuaException {
        TurbineBlockEntity turbine = device().turbine();
        return turbine == null ? 0 : turbine.powerPerTick();
    }

    /** The FE it has stored. */
    @LuaFunction(mainThread = true)
    public int getEnergy() throws LuaException {
        return device().energy().getEnergyStored();
    }

    /** The FE it can store. */
    @LuaFunction(mainThread = true)
    public int getEnergyCapacity() throws LuaException {
        return device().energy().getMaxEnergyStored();
    }

    /** Its redstone setting (COMPAT-023): {@code ignored}, {@code high} or {@code low}. */
    @LuaFunction(mainThread = true)
    public final String getRedstoneMode() throws LuaException {
        return Lua.redstoneMode(device());
    }

    @LuaFunction(mainThread = true)
    public final void setRedstoneMode(String mode) throws LuaException {
        Lua.setRedstoneMode(device(), mode);
    }
}
