package io.github.west3436.zas.reaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.reaction.ReactionSet;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Reactions on a standalone mixture (API-019) and in containers (FIRE-031), with the shipped data. */
class StandaloneReactionsTest {
    private static final ResourceLocation H2 = id("hydrogen");
    private static final ResourceLocation O2 = id("oxygen");
    private static final ResourceLocation N2 = id("nitrogen");
    private static final ResourceLocation H2O = id("water_vapor");
    private static final ResourceLocation N2O = id("nitrous_oxide");
    private static final ResourceLocation CH4 = id("methane");
    private static final ResourceLocation CO = id("carbon_monoxide");
    private static final ResourceLocation CO2 = id("carbon_dioxide");
    private static final FireSettings FIRE = FireSettings.DEFAULT;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("zas", path);
    }

    /** 20 % H2, 30 % O2 and 50 % N2 at 1,000 kPa. */
    private static GasMixture tank(double litres, double temperature) {
        double total = 1000 * litres / (GasMixture.GAS_CONSTANT * temperature);
        return new GasMixture(Map.of(H2, total * 0.2, O2, total * 0.3, N2, total * 0.5), temperature, litres);
    }

    /** Σ nᵢcᵢ with the shipped heat capacities, and the API's default for a gas they lack. */
    private static double capacity(Map<ResourceLocation, Double> moles) throws IOException {
        GasRegistry gases = ReactionFormatTest.shippedGases();
        double c = 0;
        for (Map.Entry<ResourceLocation, Double> e : moles.entrySet()) {
            int i = gases.indexOf(e.getKey().toString());
            c += e.getValue() * (i < 0 ? GasProperties.DEFAULT_MOLAR_HEAT_CAPACITY : gases.molarHeatCapacity(i));
        }
        return c;
    }

    private static ReactionSet shipped() throws IOException {
        GasRegistry gases = ReactionFormatTest.shippedGases();
        return ReactionFormatTest.shippedSet(gases);
    }

    @Test
    void api_019_a_burning_mixture_at_several_volumes_and_times() throws IOException {
        ReactionSet set = shipped();
        for (double litres : new double[] {70, 375, 500, 1000, 3000}) {
            for (double seconds : new double[] {0.5, 1, 2}) {
                GasMixture before = tank(litres, 293.15);
                GasMixture after = StandaloneReactions.apply(before, seconds, true, set, FIRE);
                double share = Math.min(1, 375 * seconds / litres);
                String at = litres + " L, " + seconds + " s";
                assertEquals(before.moles(H2) * (1 - share), after.moles(H2), 1e-9, at);
                assertEquals(before.moles(H2) * share, after.moles(H2O), 1e-9, at);
                assertEquals(before.moles(O2) - before.moles(H2) * share / 2, after.moles(O2), 1e-9, at);
                assertEquals(litres, after.volume(), at);
                // The heat: C·T + 242 kJ per mol burned, at the new heat capacity.
                double energy = capacity(before.moles()) * before.temperature() + 242_000 * before.moles(H2) * share;
                assertEquals(energy / capacity(after.moles()), after.temperature(), 1e-6, at);
            }
        }
    }

    @Test
    void api_019_a_zone_reaction_at_several_times() throws IOException {
        ReactionSet set = shipped();
        double k = new io.github.west3436.zas.engine.reaction.ReactionRate(0.012, 250_000, 1000).at(1100);
        for (double seconds : new double[] {0.5, 1, 4}) {
            GasMixture before = new GasMixture(Map.of(N2O, 2.0, N2, 8.0), 1100, 500);
            GasMixture after = StandaloneReactions.apply(before, seconds, false, set, FIRE);
            assertEquals(2 * Math.exp(-k * seconds), after.moles(N2O), 1e-9, seconds + " s");
        }
    }

    @Test
    void api_019_nothing_reacting_gives_back_the_same_mixture() throws IOException {
        ReactionSet set = shipped();
        GasMixture air = new GasMixture(Map.of(O2, 8.0, N2, 30.0), 293.15, 1000);
        assertSame(air, StandaloneReactions.apply(air, 1, true, set, FIRE));
        GasMixture fuel = tank(70, 293.15);
        assertSame(fuel, StandaloneReactions.apply(fuel, 1, false, set, FIRE));
        assertSame(fuel, StandaloneReactions.apply(fuel, 0, true, set, FIRE));
        assertSame(fuel, StandaloneReactions.apply(fuel, 1, true, ReactionSet.EMPTY, FIRE));
    }

    @Test
    void api_019_gases_the_server_does_not_define_only_absorb_heat() throws IOException {
        ReactionSet set = shipped();
        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath("othermod", "argon");
        GasMixture plain = tank(70, 293.15);
        java.util.TreeMap<ResourceLocation, Double> moles = new java.util.TreeMap<>(plain.moles());
        moles.put(unknown, 5.0);
        GasMixture with = new GasMixture(moles, 293.15, 70);

        GasMixture burned = StandaloneReactions.apply(with, 1, true, set, FIRE);

        assertEquals(5.0, burned.moles(unknown));
        assertEquals(0, burned.moles(H2));
        double energy = capacity(with.moles()) * 293.15 + 242_000 * plain.moles(H2);
        assertEquals(energy / capacity(burned.moles()), burned.temperature(), 1e-6);
    }

    /**
     * FIRE-035: a tank's gas goes by the rule a zone's does, with the shipped reactions. At 12 % O₂,
     * it burns at 900 K with no source, and at 800 K not even with one.
     */
    @Test
    void fire_035_a_hot_tank_short_of_the_oxygen_fraction_burns() throws IOException {
        ReactionSet set = shipped();
        GasMixture hot = new GasMixture(Map.of(H2, 2.9, O2, 3.5, N2, 22.6), 900, 70);
        GasMixture burned = StandaloneReactions.apply(hot, 1, false, set, FIRE);
        assertEquals(0, burned.moles(H2));
        assertEquals(2.9, burned.moles(H2O), 1e-9);
        assertEquals(3.5 - 2.9 / 2, burned.moles(O2), 1e-9);

        GasMixture warm = new GasMixture(Map.of(H2, 2.9, O2, 3.5, N2, 22.6), 800, 70);
        assertSame(warm, StandaloneReactions.apply(warm, 1, true, set, FIRE));
    }

    /**
     * FIRE-005, FIRE-035: below the threshold, all of the burning carbon forms CO. At 850 K, under
     * CO's own autoignition point, it stays CO; at 900 K, CO burns on to CO₂.
     */
    @Test
    void fire_035_below_the_oxygen_fraction_methane_forms_only_co() throws IOException {
        ReactionSet set = shipped();
        GasMixture methane = new GasMixture(Map.of(CH4, 1.0, O2, 3.0, N2, 21.0), 850, 300);
        GasMixture burned = StandaloneReactions.apply(methane, 1, false, set, FIRE);
        assertEquals(0, burned.moles(CH4));
        assertEquals(1.0, burned.moles(CO), 1e-9);
        assertEquals(0, burned.moles(CO2));
        assertEquals(1.5, burned.moles(O2), 1e-9);

        GasMixture monoxide = new GasMixture(Map.of(CO, 5.0, O2, 3.0, N2, 17.0), 900, 300);
        GasMixture onward = StandaloneReactions.apply(monoxide, 1, false, set, FIRE);
        assertEquals(0, onward.moles(CO));
        assertEquals(5.0, onward.moles(CO2), 1e-9);

        GasMixture cooler = new GasMixture(Map.of(CO, 5.0, O2, 3.0, N2, 17.0), 850, 300);
        assertSame(cooler, StandaloneReactions.apply(cooler, 1, true, set, FIRE));
    }

    @Test
    void api_019_can_be_called_from_a_worker_thread() throws Exception {
        ReactionSet set = shipped();
        GasMixture here = StandaloneReactions.apply(tank(1000, 293.15), 1, true, set, FIRE);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            GasMixture there = worker.submit(() -> StandaloneReactions.apply(tank(1000, 293.15), 1, true, set, FIRE))
                    .get();
            assertEquals(here, there);
            assertTrue(there.moles(H2O) > 0);
        } finally {
            worker.shutdown();
        }
    }
}
