package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterRelease;
import java.util.Map;

/** A canister: its pressure, what it holds, its valve and its release pressure. */
public final class CanisterPeripheral extends DevicePeripheral<CanisterBlockEntity> {
    CanisterPeripheral(CanisterBlockEntity canister) {
        super("zas_canister", canister);
    }

    /** Its pressure, kPa. */
    @LuaFunction(mainThread = true)
    public double getPressure() throws LuaException {
        return device().pressure();
    }

    /** The gas it holds, mol. */
    @LuaFunction(mainThread = true)
    public double getAmount() throws LuaException {
        return device().share().totalMoles();
    }

    /** The moles of each gas it holds, by gas id. */
    @LuaFunction(mainThread = true)
    public Map<String, Double> getContents() throws LuaException {
        return Lua.gases(device().share().moles());
    }

    /** Whether it stands on a connector port. */
    @LuaFunction(mainThread = true)
    public boolean isOnPort() throws LuaException {
        return device().onPort();
    }

    @LuaFunction(mainThread = true)
    public boolean isValveOpen() throws LuaException {
        return device().valveOpen();
    }

    @LuaFunction(mainThread = true)
    public void setValveOpen(boolean open) throws LuaException {
        device().setValveOpen(open);
    }

    /** The pressure it releases to, kPa. */
    @LuaFunction(mainThread = true)
    public double getReleasePressure() throws LuaException {
        return device().releasePressure();
    }

    @LuaFunction(mainThread = true)
    public void setReleasePressure(double kPa) throws LuaException {
        CanisterBlockEntity canister = device();
        canister.setReleasePressure(Lua.inRange("release pressure", "kPa", kPa, CanisterRelease::clamp));
    }
}
