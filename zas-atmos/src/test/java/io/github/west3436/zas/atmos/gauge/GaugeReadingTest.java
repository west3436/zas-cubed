package io.github.west3436.zas.atmos.gauge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBurst;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A gauge's readings: their order, their values and their scales (#488). */
class GaugeReadingTest {
    private static final ResourceLocation O2 = ResourceLocation.fromNamespaceAndPath("zas", "oxygen");
    private static final ResourceLocation N2 = ResourceLocation.fromNamespaceAndPath("zas", "nitrogen");
    private static final double EPS = 1e-9;

    @Test
    void the_readings_go_round_in_order_both_ways() {
        Set<GaugeReading> seen = new HashSet<>();
        GaugeReading r = GaugeReading.PRESSURE;
        for (int i = 0; i < GaugeReading.values().length; i++) {
            seen.add(r);
            assertEquals(r, r.next().previous());
            r = r.next();
        }
        assertEquals(GaugeReading.PRESSURE, r);
        assertEquals(GaugeReading.values().length, seen.size());
        assertEquals(GaugeReading.TEMPERATURE, GaugeReading.PRESSURE.next());
        assertEquals(GaugeReading.WATER_VAPOR, GaugeReading.PRESSURE.previous());
    }

    @Test
    void only_the_shares_have_a_gas_and_each_is_one_of_ours() {
        assertNull(GaugeReading.PRESSURE.gas());
        assertNull(GaugeReading.TEMPERATURE.gas());
        for (GaugeReading r : GaugeReading.values()) {
            if (r != GaugeReading.PRESSURE && r != GaugeReading.TEMPERATURE) {
                assertNotNull(r.gas());
                assertEquals("zas", r.gas().getNamespace());
                assertEquals(r.getSerializedName(), r.gas().getPath());
            }
        }
    }

    @Test
    void a_pipes_pressure_bands_end_where_a_vessels_do_on_the_arc() {
        double burst = PipeBurst.THRESHOLD_KPA;
        double rated = ContainerFailure.CANISTER_RATED_KPA;
        double explode = ContainerFailure.CANISTER_EXPLODE_KPA;
        GaugeReading p = GaugeReading.PRESSURE;
        assertEquals(0, p.along(0, false), EPS);
        assertEquals(0.4, p.along(burst / 2, false), EPS);
        assertEquals(0.8, p.along(burst, false), EPS);
        assertEquals(1, p.along(burst * 1.25, false), EPS);
        assertEquals(0.4, p.along(rated / 2, true), EPS);
        assertEquals(0.8, p.along(rated, true), EPS);
        assertEquals(0.9, p.along((rated + explode) / 2, true), EPS);
        assertEquals(1, p.along(explode, true), EPS);
        // Past the top it stays there, and below nothing at rest.
        assertEquals(1, p.along(1e9, false), EPS);
        assertEquals(0, p.along(-5, true), EPS);
        assertEquals(0, p.along(Double.NaN, true), EPS);
    }

    @Test
    void temperature_and_shares_are_straight_scales() {
        assertEquals(0.29315, GaugeReading.TEMPERATURE.along(293.15, false), EPS);
        assertEquals(1, GaugeReading.TEMPERATURE.along(5_000, false), EPS);
        assertEquals(0.21, GaugeReading.OXYGEN.along(0.21, true), EPS);
    }

    @Test
    void each_value_is_read_from_the_gas_and_none_reads_nothing() {
        GasStore gas = new GasStore(1_000);
        for (GaugeReading r : GaugeReading.values()) {
            assertEquals(0, r.value(gas), EPS, r + " of nothing");
        }
        gas.add(new GasParcel(Map.of(O2, 21.0, N2, 79.0), 300));
        assertEquals(gas.pressure(), GaugeReading.PRESSURE.value(gas), EPS);
        assertEquals(300, GaugeReading.TEMPERATURE.value(gas), EPS);
        assertEquals(0.21, GaugeReading.OXYGEN.value(gas), EPS);
        assertEquals(0.79, GaugeReading.NITROGEN.value(gas), EPS);
        assertEquals(0, GaugeReading.METHANE.value(gas), EPS);
    }
}
