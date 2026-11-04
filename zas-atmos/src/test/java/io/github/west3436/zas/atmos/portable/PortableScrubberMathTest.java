package io.github.west3436.zas.atmos.portable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.atmos.scrubber.ScrubberMath;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PortableScrubberMathTest {
    private static final ResourceLocation O2 = ResourceLocation.fromNamespaceAndPath("zas", "oxygen");
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final Predicate<ResourceLocation> KEEP_CO2 = CO2::equals;
    private static final double T = 293.15;
    private static final double ROOM = 67_500;
    private static final GasMixture EMPTY = new GasMixture(Map.of(), T, PortableGasDevice.STORE_LITRES);

    private static GasMixture room(double o2, double co2) {
        return new GasMixture(Map.of(O2, o2, CO2, co2), T, ROOM);
    }

    /** A trace of a gas is taken from the whole sample: the limit doesn't bind. */
    @Test
    void cont_018_a_trace_is_taken_from_the_whole_sample() {
        Map<ResourceLocation, Double> take = PortableScrubberMath.take(room(2_800, 28), KEEP_CO2, EMPTY);
        assertEquals(28 * 2_500 / ROOM, take.get(CO2), 1e-9);
        assertEquals(1, take.size(), "it asks for the gas it keeps and no other");
    }

    /** A room full of the gas is limited to the moles of 150 L of the air. */
    @Test
    void cont_018_what_it_moves_is_limited_to_150_litres_worth() {
        Map<ResourceLocation, Double> take = PortableScrubberMath.take(room(1_400, 1_400), KEEP_CO2, EMPTY);
        assertEquals(2_800 * 150 / ROOM, take.get(CO2), 1e-9);
    }

    /** The limit counts every gas selected, and keeps their proportions. */
    @Test
    void cont_018_the_limit_keeps_the_proportions_of_the_gases() {
        Map<ResourceLocation, Double> take = PortableScrubberMath.take(room(1_000, 3_000), gas -> true, EMPTY);
        assertEquals(4_000 * 150 / ROOM, take.get(O2) + take.get(CO2), 1e-9);
        assertEquals(3, take.get(CO2) / take.get(O2), 1e-9);
    }

    /** A held tank is smaller than the sample and the limit, so all its selected gas goes at once. */
    @Test
    void cont_018_a_small_tank_is_scrubbed_at_once() {
        GasMixture tank = new GasMixture(Map.of(O2, 5.0, CO2, 2.0), T, 70);
        Map<ResourceLocation, Double> take = PortableScrubberMath.take(tank, KEEP_CO2, EMPTY);
        assertEquals(2.0, take.get(CO2), 1e-9);
    }

    /** It stops at 50 atm in its store, and the take that would pass that is cut to what reaches it. */
    @Test
    void cont_018_it_stops_at_50_atm_in_its_store() {
        double full = ScrubberMath.STALL_KPA * PortableGasDevice.STORE_LITRES / (GasMixture.GAS_CONSTANT * T);
        GasMixture stalled = new GasMixture(Map.of(CO2, full), T, PortableGasDevice.STORE_LITRES);
        assertTrue(PortableScrubberMath.take(room(2_800, 28), KEEP_CO2, stalled).isEmpty());

        GasMixture nearly = new GasMixture(Map.of(CO2, full - 0.25), T, PortableGasDevice.STORE_LITRES);
        assertEquals(
                0.25,
                PortableScrubberMath.take(room(2_800, 28), KEEP_CO2, nearly).get(CO2),
                1e-9);
    }

    /** Nothing selected in the air, nothing to take. */
    @Test
    void cont_018_nothing_selected_means_nothing_to_take() {
        GasMixture air = new GasMixture(Map.of(O2, 2_800.0), T, ROOM);
        assertTrue(PortableScrubberMath.take(air, KEEP_CO2, EMPTY).isEmpty());
    }
}
