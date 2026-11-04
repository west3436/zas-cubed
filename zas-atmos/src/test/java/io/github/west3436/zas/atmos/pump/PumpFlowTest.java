package io.github.west3436.zas.atmos.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A pump's moles per second: its target (CONT-011) and its rate (CONT-076). */
class PumpFlowTest {
    private static final double R = GasMixture.GAS_CONSTANT;
    private static final double T = 293.15;
    private static final double MOLAR = 20.8;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;

    /** A side of one gas at a pressure, with a molar heat capacity of {@link #MOLAR}. */
    private static PumpFlow.Side side(double kpa, double temperature, double volume) {
        double moles = kpa * volume / (R * temperature);
        return new PumpFlow.Side(moles, moles * MOLAR, temperature, volume);
    }

    private static GasStore store(ResourceLocation gas, double kpa, double temperature, double volume) {
        GasStore s = new GasStore(volume);
        s.add(new GasParcel(Map.of(gas, kpa * volume / (R * temperature)), temperature));
        return s;
    }

    @Test
    void cont_011_moves_the_moles_that_bring_the_output_to_the_target() {
        // The output's whole network: 200 L for the port's buffer and 140 L of pipes, the buffer once.
        double volume = 340;
        double n = PumpFlow.molesToTarget(500, side(1_000, T, 1_000), side(200, T, volume));
        assertEquals(300 * volume / (R * T), n, 1e-9);
    }

    @Test
    void cont_011_counts_the_output_buffer_once() {
        double n = PumpFlow.molesToTarget(500, side(1_000, T, 1_000), side(0, T, 200));
        assertEquals(500 * 200 / (R * T), n, 1e-9);
        assertTrue(n < 500 * 400 / (R * T), "vg counts the buffer twice, which would move twice this");
    }

    @Test
    void cont_011_moves_nothing_at_or_above_the_target() {
        assertEquals(0, PumpFlow.molesToTarget(500, side(1_000, T, 1_000), side(500, T, 340)));
        assertEquals(0, PumpFlow.molesToTarget(500, side(1_000, T, 1_000), side(800, T, 340)));
        assertEquals(0, PumpFlow.molesToTarget(0, side(1_000, T, 1_000), side(0, T, 340)));
    }

    @Test
    void cont_011_moves_nothing_from_an_empty_input() {
        assertEquals(0, PumpFlow.pressurePump(500, side(0, T, 1_000), side(0, T, 340)));
    }

    @Test
    void cont_011_counts_the_input_at_its_own_temperature() {
        // Empty output: every mole brought in is at the input's temperature.
        double hot = 1_000;
        double n = PumpFlow.molesToTarget(500, side(1_000, hot, 1_000), side(0, T, 340));
        assertEquals(500 * 340 / (R * hot), n, 1e-9);
        // With equal molar heat capacities, the pressure a mole adds goes with its own temperature.
        double m = PumpFlow.molesToTarget(500, side(1_000, hot, 1_000), side(200, T, 340));
        assertEquals(300 * 340 / (R * hot), m, 1e-9);
    }

    @Test
    void cont_011_reaches_the_target_when_gases_of_different_heat_capacity_mix() {
        // Hot gas of 30 J/(mol·K) into cool gas of 20.8: the mix ends hotter than moles alone say,
        // and the moves still hit the target once it has settled. The reverse undershoots if counted
        // by moles alone.
        double inMolar = 30;
        double outMoles = 10;
        double volume = 340;
        for (double[] temps : new double[][] {{1_000, 300}, {300, 1_000}, {T, T}}) {
            PumpFlow.Side in = new PumpFlow.Side(100, 100 * inMolar, temps[0], 1_000);
            PumpFlow.Side out = new PumpFlow.Side(outMoles, outMoles * MOLAR, temps[1], volume);
            double target = outMoles * R * temps[1] / volume + 200;
            double n = PumpFlow.molesToTarget(target, in, out);
            double mixed =
                    (out.heatCapacity() * temps[1] + n * inMolar * temps[0]) / (out.heatCapacity() + n * inMolar);
            double pressure = (outMoles + n) * R * mixed / volume;
            assertEquals(target, pressure, 1e-9, temps[0] + " K into " + temps[1] + " K");
        }
    }

    @Test
    void cont_076_moves_at_most_100_litres_a_second_at_input_conditions() {
        // A far target: only the rate limits. 100 L of a 1,000 L network is a tenth of its moles.
        PumpFlow.Side in = side(2_000, T, 1_000);
        double n = PumpFlow.pressurePump(4_500, in, side(0, T, 100_000));
        assertEquals(in.moles() * 100 / 1_000, n, 1e-9);
        assertEquals(2_000 * 100 / (R * T), n, 1e-9);
    }

    @Test
    void cont_076_the_rate_is_at_the_input_conditions_whatever_they_are() {
        for (double[] c : new double[][] {{101.325, T}, {5_000, T}, {101.325, 1_500}, {5_000, 90}}) {
            PumpFlow.Side in = side(c[0], c[1], 340);
            double n = PumpFlow.pressurePump(4_500, in, side(0, T, 100_000));
            assertEquals(c[0] * 100 / (R * c[1]), n, 1e-9, c[0] + " kPa at " + c[1] + " K");
        }
    }

    @Test
    void cont_076_the_target_limits_below_the_rate() {
        PumpFlow.Side in = side(2_000, T, 1_000);
        double n = PumpFlow.pressurePump(10, in, side(0, T, 340));
        assertEquals(10 * 340 / (R * T), n, 1e-9);
        assertTrue(n < PumpFlow.molesInVolume(PumpFlow.MAX_RATE, in));
    }

    @Test
    void cont_076_never_takes_more_than_the_input_holds() {
        // A 70 L input is smaller than the rate.
        PumpFlow.Side in = side(1_000, T, 70);
        assertEquals(in.moles(), PumpFlow.pressurePump(4_500, in, side(0, T, 100_000)), 1e-9);
    }

    @Test
    void cont_011_a_side_reads_a_store_and_a_mixture_alike() {
        GasStore s = store(N2, 300, 400, 340);
        PumpFlow.Side fromStore = PumpFlow.Side.of(s);
        assertEquals(s.totalMoles(), fromStore.moles());
        assertEquals(s.heatCapacity(), fromStore.heatCapacity());
        assertEquals(400, fromStore.temperature());
        assertEquals(340, fromStore.volume());
        assertEquals(fromStore, PumpFlow.Side.of(s.mixture()));
    }

    /** Moving gas with a pump's own step conserves moles and thermal energy (CONT-008). */
    @Test
    void cont_011_moving_gas_conserves_moles_and_thermal_energy() {
        GasStore in = store(N2, 1_000, 1_200, 1_000);
        GasStore out = store(StandardAir.OXYGEN, 100, 280, 340);
        double moles = in.totalMoles() + out.totalMoles();
        double energy = in.thermalEnergy() + out.thermalEnergy();
        double n = PumpFlow.pressurePump(800, PumpFlow.Side.of(in), PumpFlow.Side.of(out));
        out.add(in.takeFraction(n / in.totalMoles()));
        assertTrue(n > 0);
        assertEquals(moles, in.totalMoles() + out.totalMoles(), 1e-9);
        assertEquals(energy, in.thermalEnergy() + out.thermalEnergy(), 1e-6);
    }
}
