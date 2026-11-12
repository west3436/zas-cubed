package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralSync;
import io.github.west3436.zas.atmos.controller.ZonePriority;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * A central atmospherics controller: its status, power and range, and the area air controllers it
 * has claimed, with each one's last readings and its zone's power priority. Any of the console's
 * four blocks is the peripheral.
 */
public final class CentralControllerPeripheral extends DevicePeripheral<CentralControllerBlockEntity> {
    CentralControllerPeripheral(CentralControllerBlockEntity central) {
        super("zas_central_atmos_controller", central);
    }

    /** {@code unpowered}, or the worst of its area air controllers: {@code safe}, {@code danger1} or {@code danger2}. */
    @LuaFunction(mainThread = true)
    public String getStatus() throws LuaException {
        return Lua.word(device().status());
    }

    /** The FE in its buffer. */
    @LuaFunction(mainThread = true)
    public int getEnergy() throws LuaException {
        return device().buffer().getEnergyStored();
    }

    /** The FE its buffer holds when full. */
    @LuaFunction(mainThread = true)
    public int getEnergyCapacity() throws LuaException {
        return device().buffer().getMaxEnergyStored();
    }

    /** How far it reaches, in chunks. */
    @LuaFunction(mainThread = true)
    public int getRange() throws LuaException {
        return device().range();
    }

    /** The farthest the server lets it reach, in chunks. */
    @LuaFunction(mainThread = true)
    public int getMaxRange() throws LuaException {
        device();
        return CentralControllerBlockEntity.maxRange();
    }

    @LuaFunction(mainThread = true)
    public void setRange(int chunks) throws LuaException {
        if (!device().setRange(chunks)) {
            throw new LuaException("range must be 1 to " + CentralControllerBlockEntity.maxRange() + " chunks");
        }
    }

    /**
     * Its area air controllers, in the order it claimed them: each a table of its {@code x}, {@code
     * y} and {@code z}, its zone's {@code pressure} in kPa and {@code temperature} in K (both left
     * out before its first reading), its {@code danger} from 0 to 2, whether it is {@code powered},
     * its {@code priority}, and whether it is in {@code contact}, so these are its readings now.
     */
    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getControllers() throws LuaException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (CentralSync.Row row : device().rows()) {
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("x", row.pos().getX());
            table.put("y", row.pos().getY());
            table.put("z", row.pos().getZ());
            if (!Double.isNaN(row.pressure())) {
                table.put("pressure", row.pressure());
            }
            if (!Double.isNaN(row.temperature())) {
                table.put("temperature", row.temperature());
            }
            table.put("danger", row.danger());
            table.put("powered", row.powered());
            table.put("priority", Lua.word(ZonePriority.values()[row.priority()]));
            table.put("contact", row.contact());
            list.add(table);
        }
        return list;
    }

    /** Sets the power priority of the zone of an area air controller it has claimed: {@code critical}, {@code normal} or {@code low}. */
    @LuaFunction(mainThread = true)
    public void setPriority(int x, int y, int z, String priority) throws LuaException {
        CentralControllerBlockEntity central = device();
        ZonePriority chosen = Lua.named("priority", ZonePriority.class, priority);
        if (!central.setPriority(new BlockPos(x, y, z), chosen)) {
            throw new LuaException("it has no area air controller at " + x + ", " + y + ", " + z);
        }
    }
}
