package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.atmos.vent.VentSettings;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * A vent: its mode, its two bounds, what it is doing and its network's pressure. Its settings go
 * through the vent's own hand setter, as the window's do, so a controller's orders win over a
 * computer's as they do over a player's (CONT-074).
 */
public final class VentPeripheral extends DevicePeripheral<VentBlockEntity> {
    VentPeripheral(VentBlockEntity vent) {
        super("zas_vent", vent);
    }

    /** {@code off}, {@code blow} or {@code siphon}. */
    @LuaFunction(mainThread = true)
    public String getMode() throws LuaException {
        return Lua.word(device().settings().mode());
    }

    @LuaFunction(mainThread = true)
    public void setMode(String mode) throws LuaException {
        VentBlockEntity vent = device();
        set(vent, vent.settings().withMode(Lua.named("mode", VentMode.class, mode)));
    }

    /** What it did at its last step. */
    @LuaFunction(mainThread = true)
    public String getState() throws LuaException {
        return Lua.word(device().status());
    }

    /** Its pipe network's pressure, kPa. */
    @LuaFunction(mainThread = true)
    public double getNetworkPressure() throws LuaException {
        return device().networkPressure();
    }

    /** The room's pressure it blows up to or siphons down to, kPa. */
    @LuaFunction(mainThread = true)
    public double getExternalBound() throws LuaException {
        return device().settings().external();
    }

    @LuaFunction(mainThread = true)
    public void setExternalBound(double kPa) throws LuaException {
        VentBlockEntity vent = device();
        VentSettings now = vent.settings();
        set(
                vent,
                new VentSettings(
                        now.mode(), Lua.inRange("external bound", "kPa", kPa, VentSettings::clamp), now.internal()));
    }

    /** The network's pressure it blows down to or siphons up to, kPa. */
    @LuaFunction(mainThread = true)
    public double getInternalBound() throws LuaException {
        return device().settings().internal();
    }

    @LuaFunction(mainThread = true)
    public void setInternalBound(double kPa) throws LuaException {
        VentBlockEntity vent = device();
        VentSettings now = vent.settings();
        set(
                vent,
                new VentSettings(
                        now.mode(), now.external(), Lua.inRange("internal bound", "kPa", kPa, VentSettings::clamp)));
    }

    @LuaFunction(mainThread = true)
    public boolean isPowered() throws LuaException {
        return device().power().isPowered();
    }

    /** Whether an area air controller orders it, so its settings can't be changed here. */
    @LuaFunction(mainThread = true)
    public boolean isControlled() throws LuaException {
        return device().controlled();
    }

    /** Where the controller that orders it is, if it is controlled and that is known. */
    @LuaFunction(mainThread = true)
    public @Nullable Map<String, Integer> getController() throws LuaException {
        return Lua.position(device().controller().orElse(null));
    }

    private static void set(VentBlockEntity vent, VentSettings settings) throws LuaException {
        if (!vent.setByHand(settings)) {
            throw new LuaException("an area air controller controls this vent");
        }
    }
}
