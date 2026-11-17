package io.github.west3436.zas.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.Millibuckets;
import java.util.Map;
import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The rounding of a chemical, one gas or several, to whole millibuckets (MEK-013). */
class ChemicalRoundingTest {
    private static final ResourceLocation O2 = id("zas:oxygen");
    private static final ResourceLocation N2 = id("zas:nitrogen");
    private static final ResourceLocation CO2 = id("zas:carbon_dioxide");
    private static final ResourceLocation D2 = id("zas:deuterium");
    private static final ResourceLocation T2 = id("zas:tritium");

    private static ResourceLocation id(String text) {
        return ResourceLocation.parse(text);
    }

    /** A chemical that is one gas rounds as the unit factor does. */
    @Test
    void mek_013_a_chemical_of_one_gas_rounds_down_to_whole_millibuckets() {
        double factor = Millibuckets.molesPerMillibucket(1.0);
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
        double factor = Millibuckets.molesPerMillibucket(1.0);
        ChemicalMapping fuel =
                new ChemicalMapping(id("mekanismgenerators:fusion_fuel"), Map.of(D2, 0.5, T2, 0.5), 0, false);
        assertEquals(1000, fuel.wholeMillibuckets(Map.of(D2, 500 * factor, T2, 500 * factor), factor));
        assertEquals(600, fuel.wholeMillibuckets(Map.of(D2, 500 * factor, T2, 300.4 * factor), factor));
        assertEquals(0, fuel.wholeMillibuckets(Map.of(D2, 500 * factor), factor));

        Random random = new Random(20261110);
        ChemicalMapping uneven = new ChemicalMapping(id("pack:mix"), Map.of(O2, 0.3, N2, 0.6, CO2, 0.1), 0, false);
        for (double litres : new double[] {1.0, 0.001, 1.0 / 3, 22.414}) {
            double f = Millibuckets.molesPerMillibucket(litres);
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
