package io.github.west3436.zas.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Which gas of a mixture may leave ZAS³ for Mekanism (MEK-013), and the rounding of a chemical that
 * is several gases to whole millibuckets.
 */
class GasPurityTest {
    private static final ResourceLocation O2 = id("zas:oxygen");
    private static final ResourceLocation N2 = id("zas:nitrogen");
    private static final ResourceLocation CO2 = id("zas:carbon_dioxide");
    private static final ResourceLocation D2 = id("zas:deuterium");
    private static final ResourceLocation T2 = id("zas:tritium");

    private static ResourceLocation id(String text) {
        return ResourceLocation.parse(text);
    }

    /** The default: a mixture leaves only when it is at least 99 % one gas. */
    @Test
    void mek_013_the_default_share_is_99_percent() {
        assertEquals(0.99, GasPurity.DEFAULT_MIN_SHARE);
    }

    private static void assertLeaves(ResourceLocation gas, double moles, Map<ResourceLocation, Double> mixture) {
        GasPurity.Leaving leaving = GasPurity.leaving(mixture, 0.99).orElse(null);
        assertTrue(leaving != null, "nothing leaves " + mixture);
        assertEquals(gas, leaving.gas());
        assertEquals(moles, leaving.moles(), 1e-9 * Math.max(1, moles));
    }

    /** Air gives nothing: neither of its gases is 99 % of it. One gas alone leaves, all of it. */
    @Test
    void mek_013_a_mixture_gives_nothing_and_one_gas_alone_gives_all_of_it() {
        assertEquals(Optional.empty(), GasPurity.leaving(Map.of(N2, 79.0, O2, 21.0), 0.99));
        assertEquals(Optional.empty(), GasPurity.leaving(Map.of(N2, 50.0, O2, 50.0), 0.99));
        assertEquals(Optional.of(new GasPurity.Leaving(O2, 3.0)), GasPurity.leaving(Map.of(O2, 3.0), 0.99));
        assertEquals(Optional.of(new GasPurity.Leaving(O2, 3.0)), GasPurity.leaving(Map.of(O2, 3.0), 1.0));
        assertEquals(Optional.of(new GasPurity.Leaving(O2, 3.0)), GasPurity.leaving(Map.of(O2, 3.0, N2, 0.0), 0.99));
        assertEquals(Optional.empty(), GasPurity.leaving(Map.of(), 0.99));
        assertEquals(Optional.empty(), GasPurity.leaving(Map.of(O2, 0.0), 0.99));
        // At a share of 1 nothing leaves a mixture, however slight the trace.
        assertEquals(Optional.empty(), GasPurity.leaving(Map.of(O2, 3.0, N2, 1e-9), 1.0));
    }

    /**
     * The main gas leaves as far as the rest stays at the share: a trace keeps 99 times its own
     * amount of the main gas with it, and never leaves itself.
     */
    @Test
    void mek_013_the_main_gas_leaves_as_far_as_the_rest_stays_at_the_share() {
        // 5 mol of nitrogen keep 495 mol of oxygen: 500 of the 995 may leave.
        assertLeaves(O2, 500, Map.of(O2, 995.0, N2, 5.0));
        // The share is of everything else together.
        assertLeaves(O2, 500, Map.of(O2, 995.0, N2, 2.5, CO2, 2.5));
        // Exactly at the share, and below it, nothing leaves.
        assertEquals(Optional.empty(), GasPurity.leaving(Map.of(O2, 98.9, N2, 1.1), 0.99));
        assertEquals(Optional.empty(), GasPurity.leaving(Map.of(O2, 98.5, N2, 0.75, CO2, 0.75), 0.99));
        assertTrue(GasPurity.leaving(Map.of(O2, 99.0, N2, 1.0), 0.99)
                .map(leaving -> leaving.moles() < 1e-9)
                .orElse(true));
        // A lower share lets a rougher mixture give its main gas, and never the other.
        GasPurity.Leaving air =
                GasPurity.leaving(Map.of(N2, 79.0, O2, 21.0), 0.75).orElseThrow();
        assertEquals(N2, air.gas());
        assertEquals(16.0, air.moles(), 1e-9);
        assertEquals(Optional.empty(), GasPurity.leaving(Map.of(N2, 79.0, O2, 21.0), 0.8));
    }

    /** Of two gases with the same amount the lower id is the main one, whatever order the map is in. */
    @Test
    void mek_013_a_tie_for_the_main_gas_goes_to_the_lower_id() {
        Map<ResourceLocation, Double> oneWay = new LinkedHashMap<>();
        oneWay.put(O2, 5.0);
        oneWay.put(N2, 5.0);
        Map<ResourceLocation, Double> otherWay = new LinkedHashMap<>();
        otherWay.put(N2, 5.0);
        otherWay.put(O2, 5.0);
        assertEquals(Optional.of(N2), GasPurity.mainGas(oneWay));
        assertEquals(Optional.of(N2), GasPurity.mainGas(otherWay));
        assertEquals(Optional.of(N2), GasPurity.mainGas(new TreeMap<>(oneWay)));
        assertEquals(Optional.of(O2), GasPurity.mainGas(Map.of(O2, 5.0, N2, 4.9)));
        assertEquals(Optional.empty(), GasPurity.mainGas(Map.of()));
        assertEquals(Optional.empty(), GasPurity.mainGas(Map.of(O2, 0.0)));
    }

    /**
     * However it is drawn, in one go or bit by bit, what is left is never less pure than the share,
     * the trace is all still there, and the main gas that left is what the rule allowed at the start.
     */
    @Test
    void mek_013_drawing_never_leaves_the_rest_below_the_share_and_never_takes_the_trace() {
        Random random = new Random(20261110);
        for (int i = 0; i < 20_000; i++) {
            double share = 0.5 + random.nextDouble() * 0.499;
            double trace = random.nextDouble() * 10;
            double main = trace * share / (1 - share) * (1 + random.nextDouble() * 5);
            Map<ResourceLocation, Double> pipe = new TreeMap<>(Map.of(O2, main, N2, trace * 0.6, CO2, trace * 0.4));
            double allowed =
                    GasPurity.leaving(pipe, share).map(GasPurity.Leaving::moles).orElse(0.0);
            double left = 0;
            for (int draw = 0; draw < 50; draw++) {
                GasPurity.Leaving leaving = GasPurity.leaving(pipe, share).orElse(null);
                if (leaving == null) {
                    break;
                }
                assertEquals(O2, leaving.gas());
                // A tube takes what it can carry, which may be less than what may leave.
                double taken = leaving.moles() * (draw % 3 == 0 ? 1 : random.nextDouble());
                pipe.merge(O2, -taken, Double::sum);
                left += taken;
                double rest = pipe.get(O2) + trace;
                assertTrue(pipe.get(O2) >= share * rest * (1 - 1e-12), "the rest fell below " + share + ": " + pipe);
            }
            assertTrue(left <= allowed * (1 + 1e-12), left + " mol left where " + allowed + " might");
            assertEquals(trace * 0.6, pipe.get(N2));
            assertEquals(trace * 0.4, pipe.get(CO2));
        }
    }

    /** A chemical that is one gas rounds as the unit factor does. */
    @Test
    void mek_013_a_chemical_of_one_gas_rounds_down_to_whole_millibuckets() {
        double factor = ChemicalUnits.molesPerMillibucket(1.0);
        ChemicalMapping oxygen = ChemicalMapping.of(id("mekanism:oxygen"), O2);
        assertEquals(10, oxygen.wholeMillibuckets(Map.of(O2, 10.5 * factor), factor));
        assertEquals(10, oxygen.wholeMillibuckets(Map.of(O2, 10 * factor, N2, 500.0), factor));
        assertEquals(0, oxygen.wholeMillibuckets(Map.of(O2, 0.999 * factor), factor));
        assertEquals(0, oxygen.wholeMillibuckets(Map.of(N2, 500.0), factor));
        assertEquals(0, oxygen.wholeMillibuckets(Map.of(), factor));
        assertEquals(0, oxygen.wholeMillibuckets(Map.of(O2, -1.0), factor));
    }

    /**
     * A chemical that is several gases is limited by the gas there is least of, for its share: the
     * whole millibuckets' gas is never more of any gas than there is, and one more millibucket
     * would be.
     */
    @Test
    void mek_013_a_split_chemical_rounds_down_by_its_scarcest_gas() {
        double factor = ChemicalUnits.molesPerMillibucket(1.0);
        ChemicalMapping fuel =
                new ChemicalMapping(id("mekanismgenerators:fusion_fuel"), Map.of(D2, 0.5, T2, 0.5), 0, false);
        assertEquals(1000, fuel.wholeMillibuckets(Map.of(D2, 500 * factor, T2, 500 * factor), factor));
        assertEquals(600, fuel.wholeMillibuckets(Map.of(D2, 500 * factor, T2, 300.4 * factor), factor));
        assertEquals(0, fuel.wholeMillibuckets(Map.of(D2, 500 * factor), factor));

        Random random = new Random(20261110);
        ChemicalMapping uneven = new ChemicalMapping(id("pack:mix"), Map.of(O2, 0.3, N2, 0.6, CO2, 0.1), 0, false);
        for (double litres : new double[] {1.0, 0.001, 1.0 / 3, 22.414}) {
            double f = ChemicalUnits.molesPerMillibucket(litres);
            for (int i = 0; i < 20_000; i++) {
                double scale = Math.pow(10, random.nextInt(9));
                Map<ResourceLocation, Double> there = Map.of(
                        O2, random.nextDouble() * scale * f,
                        N2, random.nextDouble() * scale * f,
                        CO2, random.nextDouble() * scale * f);
                long whole = uneven.wholeMillibuckets(there, f);
                Map<ResourceLocation, Double> made =
                        uneven.gas(whole, f, 293.15).moles();
                made.forEach((gas, n) -> assertTrue(n <= there.get(gas), n + " mol of " + gas + " from " + there));
                Map<ResourceLocation, Double> next =
                        uneven.gas(whole + 1, f, 293.15).moles();
                assertTrue(
                        next.entrySet().stream().anyMatch(e -> e.getValue() > there.get(e.getKey())),
                        "a whole millibucket was left behind of " + there);
            }
        }
    }
}
