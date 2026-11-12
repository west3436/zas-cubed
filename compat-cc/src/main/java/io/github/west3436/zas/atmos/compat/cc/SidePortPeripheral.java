package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.filter.FilterOption;
import io.github.west3436.zas.atmos.inline.SidePortEntity;
import io.github.west3436.zas.atmos.mixer.MixerBlockEntity;
import io.github.west3436.zas.atmos.pump.PumpFlow;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import java.util.LinkedHashMap;
import java.util.Map;

/** A device with a port on its side: an inline device with an output and a target pressure for it. */
public abstract class SidePortPeripheral<T extends SidePortEntity> extends InlinePeripheral<T> {
    SidePortPeripheral(String type, T device) {
        super(type, device);
    }

    /** Its output network's pressure, kPa. */
    @LuaFunction(mainThread = true)
    public final double getOutputPressure() throws LuaException {
        return device().portPressure(SidePortEntity.FRONT);
    }

    /** The output pressure it works up to, kPa. */
    @LuaFunction(mainThread = true)
    public final double getTargetPressure() throws LuaException {
        return device().target();
    }

    @LuaFunction(mainThread = true)
    public final void setTargetPressure(double kPa) throws LuaException {
        device().setTarget(Lua.inRange("target pressure", "kPa", kPa, PumpFlow::clampTarget));
    }

    /** A filter: the selected gases leave by its side, the rest by its output. */
    public static final class Filter extends SidePortPeripheral<FilterBlockEntity> {
        Filter(FilterBlockEntity filter) {
            super("zas_filter", filter);
        }

        /** Its input network's pressure, kPa. */
        @LuaFunction(mainThread = true)
        public double getInputPressure() throws LuaException {
            return device().portPressure(SidePortEntity.BACK);
        }

        /** The pressure of the network its side sends the filtered gases to, kPa. */
        @LuaFunction(mainThread = true)
        public double getFilteredPressure() throws LuaException {
            return device().portPressure(SidePortEntity.SIDE);
        }

        /** Each gas and gas tag its window offers now, and whether it filters it out. */
        @LuaFunction(mainThread = true)
        public Map<String, Boolean> getFiltered() throws LuaException {
            FilterBlockEntity filter = device();
            GasSelection selection = filter.selection();
            Map<String, Boolean> table = new LinkedHashMap<>();
            for (FilterOption option : filter.offered()) {
                table.put(Lua.option(option.id(), option.tag()), option.in(selection));
            }
            return table;
        }

        /** Filters out a gas or a gas tag its window offers now, or stops filtering it. */
        @LuaFunction(mainThread = true)
        public void setFiltered(String option, boolean filtered) throws LuaException {
            FilterBlockEntity filter = device();
            GasSelection selection = filter.selection();
            for (FilterOption offered : filter.offered()) {
                if (Lua.option(offered.id(), offered.tag()).equals(option)) {
                    if (offered.in(selection) != filtered) {
                        filter.setSelection(offered.toggled(selection));
                    }
                    return;
                }
            }
            throw new LuaException("this filter has no option " + option);
        }
    }

    /** A mixer: its two inputs leave by its output, in the shares it is set to. */
    public static final class Mixer extends SidePortPeripheral<MixerBlockEntity> {
        Mixer(MixerBlockEntity mixer) {
            super("zas_mixer", mixer);
        }

        /** The pressure of the network at its back, input 1, kPa. */
        @LuaFunction(mainThread = true)
        public double getInput1Pressure() throws LuaException {
            return device().portPressure(SidePortEntity.BACK);
        }

        /** The pressure of the network at its side, input 2, kPa. */
        @LuaFunction(mainThread = true)
        public double getInput2Pressure() throws LuaException {
            return device().portPressure(SidePortEntity.SIDE);
        }

        /** Input 1's share of what it lets through, in percent; input 2's is the rest. */
        @LuaFunction(mainThread = true)
        public int getMix() throws LuaException {
            return device().backPercent();
        }

        @LuaFunction(mainThread = true)
        public void setMix(int percent) throws LuaException {
            MixerBlockEntity mixer = device();
            if (MixerBlockEntity.clampBackPercent(percent) != percent) {
                throw new LuaException("mix must be " + MixerBlockEntity.clampBackPercent(Integer.MIN_VALUE) + " to "
                        + MixerBlockEntity.clampBackPercent(Integer.MAX_VALUE) + " %");
            }
            mixer.setBackPercent(percent);
        }
    }
}
