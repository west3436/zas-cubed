package io.github.west3436.zas.atmos.mixer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pump.PumpFlow;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A mixer's step: the fractions, the target, the rate and an input running short (CONT-077). */
class MixerMathTest {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final double R = GasMixture.GAS_CONSTANT;
    private static final double T = 293.15;
    private static final double TINY = 1e-9;

    private static GasStore store(double volume, ResourceLocation gas, double moles, double temperature) {
        GasStore s = new GasStore(volume);
        s.add(Map.of(gas, moles), temperature);
        return s;
    }

    private static GasStore empty(double volume) {
        return new GasStore(volume);
    }

    @Test
    void cont_077_the_output_gets_the_set_fractions_up_to_the_target() {
        GasStore oxygen = store(2_000, O2, 2_000, T);
        GasStore nitrogen = store(2_000, N2, 2_000, T);
        GasStore out = empty(500);
        MixerMath.Flow flow = MixerMath.moles(oxygen, nitrogen, out, 0.2, 101.325, 1);
        double total = 101.325 * 500 / (R * T);
        assertEquals(0.2 * total, flow.first(), TINY);
        assertEquals(0.8 * total, flow.second(), TINY);
        assertEquals(total, flow.total(), TINY);
        MixerMath.move(flow, oxygen, nitrogen, out);
        assertEquals(0.2, out.moles(O2) / out.totalMoles(), TINY);
        assertEquals(0.8, out.moles(N2) / out.totalMoles(), TINY);
        assertEquals(101.325, out.pressure(), 1e-6);
        assertEquals(2_000 - 0.2 * total, oxygen.totalMoles(), TINY);
        assertEquals(2_000 - 0.8 * total, nitrogen.totalMoles(), TINY);
    }

    @Test
    void cont_077_an_output_at_the_target_takes_no_more() {
        GasStore oxygen = store(2_000, O2, 2_000, T);
        GasStore nitrogen = store(2_000, N2, 2_000, T);
        GasStore out = empty(500);
        MixerMath.move(MixerMath.moles(oxygen, nitrogen, out, 0.5, 101.325, 1), oxygen, nitrogen, out);
        assertEquals(0, MixerMath.moles(oxygen, nitrogen, out, 0.5, 101.325, 1).total(), TINY);
        assertEquals(MixerMath.Flow.NONE, MixerMath.moles(oxygen, nitrogen, out, 0.5, 50, 1), "already above it");
    }

    @Test
    void cont_077_both_inputs_scale_together_when_one_runs_short() {
        // Asked for 10 mol in all: 5 from each. The first's 100 L hold only 1 mol, so it gives a
        // fifth of what it's asked for, and the second gives the same fifth.
        GasStore shortOne = store(2_000, O2, 20, T);
        GasStore plenty = store(2_000, N2, 2_000, T);
        GasStore out = empty(200);
        double target = 10 * R * T / 200;
        MixerMath.Flow flow = MixerMath.moles(shortOne, plenty, out, 0.5, target, 1);
        assertEquals(1.0, flow.first(), TINY);
        assertEquals(1.0, flow.second(), TINY);
        MixerMath.move(flow, shortOne, plenty, out);
        assertEquals(0.5, out.moles(O2) / out.totalMoles(), TINY);
        assertTrue(out.pressure() < target, "it falls short of the target instead of changing the fractions");
    }

    @Test
    void cont_077_the_scaling_holds_a_mix_that_is_not_even() {
        GasStore shortOne = store(1_000, O2, 10, T);
        GasStore plenty = store(1_000, N2, 1_000, T);
        GasStore out = empty(1_000);
        MixerMath.Flow flow = MixerMath.moles(shortOne, plenty, out, 0.25, 4_500, 1);
        assertEquals(1.0, flow.first(), TINY, "the first gives all a second allows");
        assertEquals(3.0, flow.second(), TINY, "and the second three times that");
    }

    @Test
    void cont_077_an_empty_input_stops_the_mixer_unless_its_fraction_is_0() {
        GasStore plenty = store(1_000, N2, 1_000, T);
        GasStore out = empty(1_000);
        assertEquals(MixerMath.Flow.NONE, MixerMath.moles(empty(1_000), plenty, out, 0.5, 101.325, 1));
        assertEquals(MixerMath.Flow.NONE, MixerMath.moles(plenty, empty(1_000), out, 0.5, 101.325, 1));
        // At a fraction of 0 it isn't asked for anything, so it's a pump from the other input.
        MixerMath.Flow flow = MixerMath.moles(empty(1_000), plenty, out, 0, 101.325, 1);
        assertEquals(0.0, flow.first());
        assertTrue(flow.second() > 0);
        flow = MixerMath.moles(plenty, empty(1_000), out, 1, 101.325, 1);
        assertTrue(flow.first() > 0);
        assertEquals(0.0, flow.second());
    }

    @Test
    void cont_077_each_input_gives_at_most_100_litres_a_second_at_its_own_conditions() {
        GasStore dense = store(1_000, O2, 1_000, T);
        GasStore thin = store(2_000, N2, 1_000, T);
        GasStore out = empty(100_000);
        // All asked for far more than a second allows: the input with the least per second limits it.
        MixerMath.Flow flow = MixerMath.moles(dense, thin, out, 0.5, 4_500, 1);
        assertEquals(50.0, flow.second(), TINY, "100 L of 2,000 L");
        assertEquals(50.0, flow.first(), TINY, "scaled with it, under its own limit of 100 mol");
        // A longer step moves in proportion.
        assertEquals(100.0, MixerMath.moles(dense, thin, out, 0.5, 4_500, 2).second(), TINY);
    }

    @Test
    void cont_077_a_mixer_never_takes_more_than_an_input_holds() {
        GasStore small = store(50, O2, 2, T);
        GasStore big = store(50, N2, 2, T);
        MixerMath.Flow flow = MixerMath.moles(small, big, empty(100_000), 0.5, 4_500, 1);
        assertEquals(2.0, flow.first(), TINY);
        assertEquals(2.0, flow.second(), TINY);
    }

    @Test
    void cont_077_each_input_makes_its_fraction_of_the_pressure_at_its_own_temperature() {
        // Each is asked for moles at its own temperature, so the colder input gives more moles and
        // each makes up its fraction of the rise in pressure.
        GasStore cold = store(5_000, N2, 5_000, 200);
        GasStore hot = store(5_000, N2, 5_000, 400);
        GasStore out = empty(500);
        MixerMath.Flow flow = MixerMath.moles(cold, hot, out, 0.5, 300, 1);
        assertEquals(0.5 * 300 * 500 / (R * 200), flow.first(), TINY);
        assertEquals(0.5 * 300 * 500 / (R * 400), flow.second(), TINY);
        MixerMath.move(flow, cold, hot, out);
        assertEquals(300, out.pressure(), 1e-6);
    }

    @Test
    void cont_077_the_output_lands_on_the_target_between_gases_of_different_heat_capacities() {
        // Hot oxygen and cold nitrogen into an output of nitrogen: the moles stay in proportion to
        // fraction over temperature, and the pressure still lands on the target.
        GasStore hot = store(10_000, O2, 100_000, 1_000);
        GasStore cold = store(10_000, N2, 100_000, 250);
        GasStore out = store(1_000, N2, 40, 300);
        MixerMath.Flow flow = MixerMath.moles(hot, cold, out, 0.25, 500, 1);
        assertEquals((0.25 / 1_000) / (0.75 / 250), flow.first() / flow.second(), TINY);
        MixerMath.move(flow, hot, cold, out);
        assertEquals(500, out.pressure(), TINY);
    }

    @Test
    void cont_077_moving_gas_conserves_moles_and_thermal_energy() {
        GasStore first = store(1_000, O2, 100, 400);
        GasStore second = store(1_000, N2, 100, 250);
        GasStore out = store(500, N2, 5, 300);
        double molesBefore = first.totalMoles() + second.totalMoles() + out.totalMoles();
        double energyBefore = first.thermalEnergy() + second.thermalEnergy() + out.thermalEnergy();
        MixerMath.Flow flow = MixerMath.moles(first, second, out, 0.3, 4_500, 1);
        assertTrue(flow.total() > 0);
        MixerMath.move(flow, first, second, out);
        assertEquals(molesBefore, first.totalMoles() + second.totalMoles() + out.totalMoles(), TINY);
        assertEquals(energyBefore, first.thermalEnergy() + second.thermalEnergy() + out.thermalEnergy(), 1e-6);
        assertEquals(400, first.temperature(), TINY, "an input keeps its temperature");
        assertEquals(250, second.temperature(), TINY);
    }

    @Test
    void cont_077_inputs_or_an_output_on_one_network_lose_no_gas() {
        GasStore both = store(1_000, N2, 100, T);
        GasStore out = empty(1_000);
        MixerMath.move(MixerMath.moles(both, both, out, 0.5, 4_500, 1), both, both, out);
        assertEquals(100, both.totalMoles() + out.totalMoles(), TINY);
        assertTrue(out.totalMoles() > 0);

        GasStore all = store(1_000, N2, 100, T);
        MixerMath.move(MixerMath.moles(all, all, all, 0.5, 4_500, 1), all, all, all);
        assertEquals(100, all.totalMoles(), TINY);
    }

    @Test
    void cont_077_settings_are_clamped() {
        assertEquals(4_500, PumpFlow.clampTarget(9_999));
        assertEquals(1, MixerMath.clampFraction(2));
        assertEquals(0, MixerMath.clampFraction(-1));
        assertEquals(0.5, MixerMath.clampFraction(Double.NaN));
        assertEquals(0.3, MixerMath.clampFraction(0.3));
    }

    @Test
    void cont_077_no_time_moves_nothing() {
        GasStore a = store(1_000, O2, 100, T);
        GasStore b = store(1_000, N2, 100, T);
        assertEquals(MixerMath.Flow.NONE, MixerMath.moles(a, b, empty(1_000), 0.5, 4_500, 0));
    }
}
