package io.github.west3436.zas.atmos.compat.cc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dan200.computercraft.api.lua.LuaException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** How a computer's values are checked on their way in and written on their way out (COMPAT-021). */
class LuaTest {
    /** A device's clamp: 0 to 4500, and its default for what is no number, as the pumps' is. */
    private static final DoubleUnaryOperator CLAMP = v -> Double.isNaN(v) ? 0 : Math.max(0, Math.min(4500, v));

    private enum Mode {
        OFF,
        BLOW,
        SIPHON
    }

    @Test
    void compat_021_a_number_the_devices_clamp_would_keep_is_taken_as_given() throws LuaException {
        assertEquals(0, Lua.inRange("target pressure", "kPa", 0, CLAMP));
        assertEquals(4500, Lua.inRange("target pressure", "kPa", 4500, CLAMP));
        assertEquals(250.5, Lua.inRange("target pressure", "kPa", 250.5, CLAMP));
    }

    @Test
    void compat_021_a_number_the_devices_clamp_would_change_is_refused_with_the_clamps_range() {
        for (double value : new double[] {
            -0.001, 4500.001, 1e9, -1e300, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
        }) {
            LuaException refused =
                    assertThrows(LuaException.class, () -> Lua.inRange("target pressure", "kPa", value, CLAMP));
            assertEquals("target pressure must be 0 to 4500 kPa", refused.getMessage(), "at " + value);
        }
    }

    @Test
    void compat_021_a_number_in_an_error_has_no_exponent_and_no_trailing_zero() {
        assertEquals("0", Lua.number(0));
        assertEquals("4500", Lua.number(4500.0));
        assertEquals("293.15", Lua.number(293.15));
        assertEquals("5066", Lua.number(5066));
        assertEquals("10000000", Lua.number(1e7));
        assertEquals("-0.5", Lua.number(-0.5));
    }

    @Test
    void compat_021_a_choice_is_its_word_in_lower_case_and_anything_else_is_refused_with_the_choices()
            throws LuaException {
        assertEquals("siphon", Lua.word(Mode.SIPHON));
        for (Mode mode : Mode.values()) {
            assertEquals(mode, Lua.named("mode", Mode.class, Lua.word(mode)));
        }
        for (String word : List.of("", "Blow", "BLOW", "blow ", "vacuum")) {
            LuaException refused = assertThrows(LuaException.class, () -> Lua.named("mode", Mode.class, word));
            assertEquals("mode must be one of: off, blow, siphon", refused.getMessage(), "for \"" + word + "\"");
        }
    }

    @Test
    void compat_020_a_missing_reading_is_nil_and_a_position_is_a_table() {
        assertNull(Lua.orNil(Double.NaN));
        assertEquals(101.325, Lua.orNil(101.325));
        assertEquals(0.0, Lua.orNil(0));
        assertNull(Lua.position(null));
        assertEquals(Map.of("x", 3, "y", -60, "z", 7), Lua.position(new BlockPos(3, -60, 7)));
    }

    @Test
    void compat_020_a_gas_mix_is_keyed_by_gas_id_and_a_tag_starts_with_a_hash() {
        Map<ResourceLocation, Double> moles = new LinkedHashMap<>();
        moles.put(ResourceLocation.fromNamespaceAndPath("zas", "oxygen"), 21.0);
        moles.put(ResourceLocation.fromNamespaceAndPath("zas", "nitrogen"), 79.0);
        assertEquals(Map.of("zas:oxygen", 21.0, "zas:nitrogen", 79.0), Lua.gases(moles));
        assertEquals(
                List.of("zas:oxygen", "zas:nitrogen"),
                List.copyOf(Lua.gases(moles).keySet()));
        ResourceLocation toxic = ResourceLocation.fromNamespaceAndPath("zas", "toxic");
        assertEquals("zas:toxic", Lua.option(toxic, false));
        assertEquals("#zas:toxic", Lua.option(toxic, true));
    }
}
