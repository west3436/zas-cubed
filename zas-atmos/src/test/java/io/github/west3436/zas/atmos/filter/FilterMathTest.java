package io.github.west3436.zas.atmos.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A filter's step: what leaves by which outlet, the target and the rate (CONT-013). */
class FilterMathTest {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final Predicate<ResourceLocation> ONLY_CO2 = Set.of(CO2)::contains;
    private static final double RT = GasMixture.GAS_CONSTANT * 293.15;
    private static final double T = 293.15;
    private static final double TINY = 1e-9;

    private static GasStore store(double volume, Map<ResourceLocation, Double> moles, double temperature) {
        GasStore s = new GasStore(volume);
        s.add(moles, temperature);
        return s;
    }

    private static GasStore empty(double volume) {
        return new GasStore(volume);
    }

    @Test
    void cont_013_selected_gases_leave_by_the_side_outlet_and_the_rest_by_the_output() {
        GasStore in = store(1_000, Map.of(N2, 30.0, CO2, 10.0), T);
        GasStore out = empty(1_000);
        GasStore side = empty(1_000);
        double n = FilterMath.moles(in, out, side, ONLY_CO2, 4_500, 1);
        assertEquals(4.0, n, TINY, "a tenth of the input: 100 of its 1,000 L");
        FilterMath.move(n, in, out, side, ONLY_CO2);
        assertEquals(1.0, side.moles(CO2), TINY);
        assertEquals(0.0, side.moles(N2));
        assertEquals(3.0, out.moles(N2), TINY);
        assertEquals(0.0, out.moles(CO2));
        assertEquals(27.0, in.moles(N2), TINY);
        assertEquals(9.0, in.moles(CO2), TINY);
    }

    @Test
    void cont_013_with_nothing_selected_all_gas_leaves_by_the_output() {
        GasStore in = store(1_000, Map.of(N2, 30.0, CO2, 10.0), T);
        GasStore out = empty(1_000);
        GasStore side = empty(1_000);
        Predicate<ResourceLocation> none = g -> false;
        FilterMath.move(FilterMath.moles(in, out, side, none, 4_500, 1), in, out, side, none);
        assertTrue(side.isEmpty());
        assertEquals(4.0, out.totalMoles(), TINY);
    }

    @Test
    void cont_013_with_every_gas_selected_all_gas_leaves_by_the_side_outlet() {
        GasStore in = store(1_000, Map.of(N2, 30.0, CO2, 10.0), T);
        GasStore out = empty(1_000);
        GasStore side = empty(1_000);
        Predicate<ResourceLocation> all = g -> true;
        FilterMath.move(FilterMath.moles(in, out, side, all, 4_500, 1), in, out, side, all);
        assertTrue(out.isEmpty());
        assertEquals(4.0, side.totalMoles(), TINY);
    }

    @Test
    void cont_013_the_side_outlet_stops_at_the_target() {
        // All selected: only the side outlet limits it, and it reaches the target and no more.
        GasStore in = store(200, Map.of(CO2, 1_000.0), T);
        GasStore out = empty(200);
        GasStore side = store(500, Map.of(CO2, 20.0), T);
        double n = FilterMath.moles(in, out, side, ONLY_CO2, 101.325, 1);
        assertEquals((101.325 - side.pressure()) * 500 / RT, n, TINY);
        FilterMath.move(n, in, out, side, ONLY_CO2);
        assertEquals(101.325, side.pressure(), TINY);
        assertEquals(0, FilterMath.moles(in, out, side, ONLY_CO2, 101.325, 1), TINY, "no more once it's there");
        assertTrue(out.isEmpty());
    }

    @Test
    void cont_013_the_output_stops_at_the_target() {
        GasStore in = store(200, Map.of(N2, 1_000.0), T);
        GasStore out = store(500, Map.of(N2, 20.0), T);
        GasStore side = empty(200);
        double n = FilterMath.moles(in, out, side, ONLY_CO2, 101.325, 1);
        assertEquals((101.325 - out.pressure()) * 500 / RT, n, TINY);
        FilterMath.move(n, in, out, side, ONLY_CO2);
        assertEquals(101.325, out.pressure(), TINY);
        assertEquals(0, FilterMath.moles(in, out, side, ONLY_CO2, 101.325, 1), TINY);
    }

    @Test
    void cont_013_the_filter_stops_when_either_outlet_reaches_the_target() {
        GasStore in = store(200, Map.of(N2, 50.0, CO2, 50.0), T);
        double fullMoles = 101.325 * 500 / RT;
        // Gas the selection doesn't touch still waits on a full side outlet, and the other way round.
        GasStore fullSide = store(500, Map.of(CO2, fullMoles), T);
        assertEquals(0, FilterMath.moles(in, empty(500), fullSide, ONLY_CO2, 101.325, 1), TINY);
        GasStore fullOut = store(500, Map.of(N2, fullMoles), T);
        assertEquals(0, FilterMath.moles(in, fullOut, empty(500), ONLY_CO2, 101.325, 1), TINY);
        GasStore overOut = store(500, Map.of(N2, fullMoles * 2), T);
        assertEquals(0, FilterMath.moles(in, overOut, empty(500), ONLY_CO2, 101.325, 1));
    }

    @Test
    void cont_013_the_outlet_that_fills_first_limits_the_slice() {
        // Half and half into a roomy output and a tight side outlet: the side outlet's room sets the
        // slice, and neither passes the target.
        GasStore in = store(200, Map.of(N2, 500.0, CO2, 500.0), T);
        GasStore out = empty(5_000);
        GasStore side = empty(100);
        double sideRoom = 500 * 100 / RT;
        double n = FilterMath.moles(in, out, side, ONLY_CO2, 500, 1);
        assertEquals(sideRoom * 2, n, TINY);
        FilterMath.move(n, in, out, side, ONLY_CO2);
        assertEquals(500, side.pressure(), TINY);
        assertEquals(sideRoom, out.moles(N2), TINY);
        assertTrue(out.pressure() < 500);
    }

    @Test
    void cont_013_a_filter_takes_at_most_100_litres_a_second_at_input_conditions() {
        GasStore in = store(2_000, Map.of(N2, 800.0), T);
        double n = FilterMath.moles(in, empty(10_000), empty(10_000), ONLY_CO2, 4_500, 1);
        assertEquals(800.0 * 100 / 2_000, n, TINY);
        // Whatever the input's pressure, a second moves its moles in 100 L.
        GasStore thin = store(2_000, Map.of(N2, 8.0), T);
        assertEquals(8.0 * 100 / 2_000, FilterMath.moles(thin, empty(100), empty(100), ONLY_CO2, 4_500, 1), TINY);
        // A longer step moves in proportion.
        assertEquals(800.0 * 200 / 2_000, FilterMath.moles(in, empty(10_000), empty(10_000), ONLY_CO2, 4_500, 2), TINY);
    }

    @Test
    void cont_013_a_filter_never_takes_more_than_the_input_holds() {
        GasStore in = store(50, Map.of(N2, 2.0), T);
        assertEquals(2.0, FilterMath.moles(in, empty(10_000), empty(10_000), ONLY_CO2, 4_500, 1), TINY);
    }

    @Test
    void cont_013_the_target_is_at_most_4500_kpa() {
        assertEquals(4_500, FilterMath.clampTarget(9_999));
        assertEquals(0, FilterMath.clampTarget(-5));
        assertEquals(0, FilterMath.clampTarget(Double.NaN));
        assertEquals(250, FilterMath.clampTarget(250));
        // A higher target acts as 4,500 kPa.
        GasStore in = store(200, Map.of(N2, 10_000.0), T);
        GasStore out = empty(200);
        GasStore side = empty(200);
        FilterMath.move(FilterMath.moles(in, out, side, ONLY_CO2, 9_999, 1), in, out, side, ONLY_CO2);
        assertEquals(4_500, out.pressure(), 1e-6);
    }

    @Test
    void cont_013_an_empty_or_cold_input_or_no_time_moves_nothing() {
        assertEquals(0, FilterMath.moles(empty(200), empty(200), empty(200), ONLY_CO2, 4_500, 1));
        GasStore cold = store(200, Map.of(N2, 10.0), 0);
        assertEquals(0, FilterMath.moles(cold, empty(200), empty(200), ONLY_CO2, 4_500, 1));
        GasStore in = store(200, Map.of(N2, 10.0), T);
        assertEquals(0, FilterMath.moles(in, empty(200), empty(200), ONLY_CO2, 4_500, 0));
        assertEquals(0, FilterMath.moles(in, empty(200), empty(200), ONLY_CO2, 0, 1), "a target of 0 kPa");
    }

    @Test
    void cont_013_moving_gas_conserves_moles_and_thermal_energy() {
        GasStore in = store(1_000, Map.of(N2, 30.0, CO2, 10.0, O2, 5.0), 400);
        GasStore out = store(500, Map.of(O2, 8.0), 250);
        GasStore side = store(500, Map.of(N2, 2.0), 300);
        double molesBefore = in.totalMoles() + out.totalMoles() + side.totalMoles();
        double energyBefore = in.thermalEnergy() + out.thermalEnergy() + side.thermalEnergy();
        double n = FilterMath.moles(in, out, side, ONLY_CO2, 4_500, 1);
        assertTrue(n > 0);
        FilterMath.move(n, in, out, side, ONLY_CO2);
        assertEquals(molesBefore, in.totalMoles() + out.totalMoles() + side.totalMoles(), TINY);
        assertEquals(energyBefore, in.thermalEnergy() + out.thermalEnergy() + side.thermalEnergy(), 1e-6);
        assertEquals(400, in.temperature(), TINY, "the input keeps its temperature");
        assertTrue(out.temperature() > 250 && out.temperature() < 400);
        assertTrue(side.temperature() > 300 && side.temperature() < 400);
    }

    @Test
    void cont_013_hot_gas_reaches_the_target_by_pressure_not_by_moles() {
        // Like gases at different temperatures: mixing conserves energy, so the pressure lands on
        // the target exactly.
        GasStore in = store(200, Map.of(N2, 500.0), 600);
        GasStore out = store(1_000, Map.of(N2, 10.0), T);
        GasStore side = empty(200);
        double n = FilterMath.moles(in, out, side, ONLY_CO2, 500, 1);
        FilterMath.move(n, in, out, side, ONLY_CO2);
        assertEquals(500, out.pressure(), 1e-6);
    }

    @Test
    void cont_013_outlets_on_the_input_network_lose_no_gas() {
        // A pipe from an outlet back to the input makes them one network: gas only goes round.
        GasStore loop = store(1_000, Map.of(N2, 30.0, CO2, 10.0), T);
        GasStore side = empty(1_000);
        double n = FilterMath.moles(loop, loop, side, ONLY_CO2, 4_500, 1);
        FilterMath.move(n, loop, loop, side, ONLY_CO2);
        assertEquals(40.0, loop.totalMoles() + side.totalMoles(), TINY);
        assertEquals(1.0, side.moles(CO2), TINY);

        GasStore all = store(1_000, Map.of(N2, 30.0, CO2, 10.0), T);
        n = FilterMath.moles(all, all, all, ONLY_CO2, 4_500, 1);
        FilterMath.move(n, all, all, all, ONLY_CO2);
        assertEquals(30.0, all.moles(N2), TINY);
        assertEquals(10.0, all.moles(CO2), TINY);
    }
}
