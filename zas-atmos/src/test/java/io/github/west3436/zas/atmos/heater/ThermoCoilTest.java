package io.github.west3436.zas.atmos.heater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.power.DevicePower;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * A vent's heater and a scrubber's air conditioner (CONT-166, CONT-167): what an order costs, which
 * is the standalone machine's cost (CONT-087), and when it lapses.
 */
class ThermoCoilTest {
    private static final SourceId SOURCE = SourceId.of("zas_atmos", "test");
    private final AtomicLong now = new AtomicLong(1000);
    private final ThermoCoil coil = new ThermoCoil(ThermoCoil.Kind.HEATS, SOURCE, () -> 5000, () -> 60, now::get);

    /** Full power costs the heater's full draw; half costs half; nothing costs nothing. */
    @Test
    void cont_166_an_order_costs_what_the_heater_would() {
        assertEquals(0, coil.draw(), "no order");
        coil.order(5000);
        assertEquals(60, coil.draw(), "full power");
        coil.order(2500);
        assertEquals(30, coil.draw(), "half power");
        coil.order(1);
        assertEquals(1, coil.draw(), "a little rounds up to 1 FE/t");
        assertEquals(
                HeaterMath.energy(1, AtmosTicks.SECONDS, 5000, 60, AtmosTicks.PERIOD),
                1,
                "the heater's own cost of 1 J");
    }

    /** What some FE pays for, ordered, costs no more than that FE. */
    @Test
    void cont_166_what_spare_fe_pays_for_costs_no_more() {
        assertEquals(5000 * AtmosTicks.SECONDS, coil.joulesFor(1_000_000), "plenty");
        assertEquals(0, coil.joulesFor(19), "less than 1 FE/t");
        for (long fe : new long[] {20, 300, 333, 1199}) {
            coil.order(coil.joulesFor(fe));
            long paid = (long) coil.draw() * AtmosTicks.PERIOD;
            assertTrue(paid <= fe, fe + " FE paid for " + paid);
            assertTrue(paid > fe - AtmosTicks.PERIOD, fe + " FE bought only " + paid);
        }
    }

    /** An order is clamped to what the coil moves in a step. */
    @Test
    void cont_166_an_order_is_clamped() {
        coil.order(1e9);
        assertEquals(5000 * AtmosTicks.SECONDS, coil.ordered());
        coil.order(-5);
        assertEquals(0, coil.ordered());
        coil.order(Double.NaN);
        assertEquals(0, coil.ordered());
    }

    /** An order not used within a lease is gone, and costs nothing. */
    @Test
    void cont_166_an_order_lapses() {
        coil.order(3000);
        now.addAndGet(DevicePower.LEASE_TICKS);
        assertEquals(0, coil.ordered(), "lapsed");
        assertEquals(0, coil.draw(), "lapsed");
    }
}
