package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMenu;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A scrubber: its mode, the gases and gas tags it takes, what it is doing and its network's
 * pressure. The gases and tags are the ones its window offers ({@link ScrubberMenu#GASES}, {@link
 * ScrubberMenu#TAGS}), and its settings go through the scrubber's own hand setter, so a controller's
 * orders win over a computer's as they do over a player's (CONT-074).
 */
public final class ScrubberPeripheral extends DevicePeripheral<ScrubberBlockEntity> {
    ScrubberPeripheral(ScrubberBlockEntity scrubber) {
        super("zas_scrubber", scrubber);
    }

    /** {@code off}, {@code scrub}, {@code siphon} or {@code panic}. */
    @LuaFunction(mainThread = true)
    public String getMode() throws LuaException {
        return Lua.word(device().mode());
    }

    @LuaFunction(mainThread = true)
    public void setMode(String mode) throws LuaException {
        ScrubberBlockEntity scrubber = device();
        set(scrubber, Lua.named("mode", ScrubberMode.class, mode), scrubber.selection());
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

    /** Each gas and gas tag its window offers, and whether it scrubs it. */
    @LuaFunction(mainThread = true)
    public Map<String, Boolean> getScrubbed() throws LuaException {
        GasSelection selection = device().selection();
        Map<String, Boolean> table = new LinkedHashMap<>();
        for (ResourceLocation gas : ScrubberMenu.GASES) {
            table.put(Lua.option(gas, false), selection.gases().contains(gas));
        }
        for (GasTag tag : ScrubberMenu.TAGS) {
            table.put(tag.toString(), selection.tags().contains(tag));
        }
        return table;
    }

    /** Scrubs a gas or a gas tag its window offers, or stops scrubbing it. */
    @LuaFunction(mainThread = true)
    public void setScrubbed(String option, boolean scrubbed) throws LuaException {
        ScrubberBlockEntity scrubber = device();
        GasSelection selection = scrubber.selection();
        for (ResourceLocation gas : ScrubberMenu.GASES) {
            if (Lua.option(gas, false).equals(option)) {
                boolean now = selection.gases().contains(gas);
                set(scrubber, scrubber.mode(), now == scrubbed ? selection : selection.toggled(gas));
                return;
            }
        }
        for (GasTag tag : ScrubberMenu.TAGS) {
            if (tag.toString().equals(option)) {
                boolean now = selection.tags().contains(tag);
                set(scrubber, scrubber.mode(), now == scrubbed ? selection : selection.toggled(tag));
                return;
            }
        }
        throw new LuaException("a scrubber has no option " + option);
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

    private static void set(ScrubberBlockEntity scrubber, ScrubberMode mode, GasSelection selection)
            throws LuaException {
        if (!scrubber.setByHand(mode, selection)) {
            throw new LuaException("an area air controller controls this scrubber");
        }
    }
}
