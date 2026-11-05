package io.github.west3436.zas.airflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** How a client holds an edge's state between packets, and how it ends (DATA-015). */
class HeldFlowsTest {
    private static final String HERE = "minecraft:overworld";
    private static final String THERE = "minecraft:the_nether";
    private static final List<VisibleFlow> BREACH =
            List.of(new VisibleFlow(101, List.of(new VisibleOpening(1, 2, 3, 5))));

    @Test
    void data_015_a_steady_breach_keeps_showing_between_packets() {
        HeldFlows held = new HeldFlows();
        held.accept(BREACH, HERE);
        for (int i = 0; i < VisibleFlows.RESEND_TICKS; i++) {
            held.tick(HERE);
        }
        assertEquals(BREACH, held.flows());
    }

    @Test
    void data_015_a_resend_renews_it_for_as_long_as_the_breach_lasts() {
        HeldFlows held = new HeldFlows();
        held.accept(BREACH, HERE);
        for (int i = 0; i < 10 * HeldFlows.LAPSE_TICKS; i++) {
            if (i % VisibleFlows.RESEND_TICKS == 0) {
                held.accept(BREACH, HERE);
            }
            held.tick(HERE);
        }
        assertEquals(BREACH, held.flows());
    }

    @Test
    void data_015_an_empty_list_ends_it_and_a_newer_list_replaces_it() {
        HeldFlows held = new HeldFlows();
        held.accept(BREACH, HERE);
        List<VisibleFlow> other = List.of(new VisibleFlow(40, List.of(new VisibleOpening(9, 9, 9, 0))));
        held.accept(other, HERE);
        assertEquals(other, held.flows());
        held.accept(List.of(), HERE);
        assertTrue(held.flows().isEmpty());
    }

    @Test
    void data_015_it_lapses_if_no_resend_comes() {
        HeldFlows held = new HeldFlows();
        held.accept(BREACH, HERE);
        for (int i = 0; i < HeldFlows.LAPSE_TICKS - 1; i++) {
            held.tick(HERE);
        }
        assertEquals(BREACH, held.flows());
        held.tick(HERE);
        assertTrue(held.flows().isEmpty(), "it outlived its lapse");
    }

    @Test
    void data_015_the_lapse_outlasts_the_servers_resends() {
        assertTrue(HeldFlows.LAPSE_TICKS >= 2 * VisibleFlows.RESEND_TICKS);
    }

    @Test
    void data_015_leaving_the_dimension_ends_it_and_coming_back_doesn_t_revive_it() {
        HeldFlows held = new HeldFlows();
        held.accept(BREACH, HERE);
        held.tick(THERE);
        assertTrue(held.flows().isEmpty());
        held.tick(HERE);
        assertTrue(held.flows().isEmpty());
    }

    @Test
    void data_015_clear_drops_it() {
        HeldFlows held = new HeldFlows();
        held.accept(BREACH, HERE);
        held.clear();
        assertTrue(held.flows().isEmpty());
        held.tick(HERE);
        assertTrue(held.flows().isEmpty());
    }
}
