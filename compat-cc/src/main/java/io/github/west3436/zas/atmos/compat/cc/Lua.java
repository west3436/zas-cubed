package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import io.github.west3436.zas.api.gas.GasTag;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** What the peripherals share: how a value is checked on its way in and written on its way out. */
final class Lua {
    private Lua() {}

    /**
     * A number a computer gave for a setting, if the device would keep it as given. The device's own
     * clamp decides, so the range is written once, in the device: a value it would change is refused,
     * with the range the clamp shows.
     *
     * @param what the setting, as the error names it
     * @param unit the unit the error names
     * @param value the number given
     * @param clamp the device's clamp for that setting
     * @return the value
     * @throws LuaException if the clamp would change it, or it isn't a number
     */
    static double inRange(String what, String unit, double value, DoubleUnaryOperator clamp) throws LuaException {
        if (Double.isNaN(value) || clamp.applyAsDouble(value) != value) {
            throw new LuaException(what + " must be " + number(clamp.applyAsDouble(-Double.MAX_VALUE)) + " to "
                    + number(clamp.applyAsDouble(Double.MAX_VALUE)) + " " + unit);
        }
        return value;
    }

    /** A number as an error shows it: no exponent, and no ".0" on a whole one. */
    static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** An enum constant's word in Lua: its name in lower case. */
    static String word(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The enum constant a computer named.
     *
     * @param what the setting, as the error names it
     * @param type the enum
     * @param word the word given
     * @throws LuaException if no constant has that word, listing the ones there are
     */
    static <E extends Enum<E>> E named(String what, Class<E> type, String word) throws LuaException {
        List<String> words = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            if (word(constant).equals(word)) {
                return constant;
            }
            words.add(word(constant));
        }
        throw new LuaException(what + " must be one of: " + String.join(", ", words));
    }

    /** A reading that may be missing: {@code nil} for NaN. */
    static @Nullable Double orNil(double value) {
        return Double.isNaN(value) ? null : value;
    }

    /** A position as a table of x, y and z, or {@code nil} for none. */
    static @Nullable Map<String, Integer> position(@Nullable BlockPos pos) {
        if (pos == null) {
            return null;
        }
        Map<String, Integer> table = new LinkedHashMap<>();
        table.put("x", pos.getX());
        table.put("y", pos.getY());
        table.put("z", pos.getZ());
        return table;
    }

    /** The moles of each gas, keyed by the gas's id. */
    static Map<String, Double> gases(Map<ResourceLocation, Double> moles) {
        Map<String, Double> table = new LinkedHashMap<>();
        moles.forEach((gas, amount) -> table.put(gas.toString(), amount));
        return table;
    }

    /** A gas's or a gas tag's name in a selection: the id, after a {@code #} for a tag. */
    static String option(ResourceLocation id, boolean tag) {
        return tag ? new GasTag(id).toString() : id.toString();
    }
}
