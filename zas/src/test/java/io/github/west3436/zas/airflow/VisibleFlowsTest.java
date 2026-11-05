package io.github.west3436.zas.airflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.flow.FlowEdge;
import io.github.west3436.zas.engine.flow.FlowField;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which airflow a client is sent to show, and when (CLIENT-003, DATA-015). */
class VisibleFlowsTest {
    private static final int WEST = 4;

    /** An edge from a zone at {@code kpa} to a vacuum, with one opening per {x, y, z}, flowing west. */
    private static FlowEdge edge(long zone, long other, double kpa, int[]... at) {
        long[] high = new long[at.length];
        long[] low = new long[at.length];
        for (int i = 0; i < at.length; i++) {
            high[i] = CellIds.of(at[i][0], at[i][1], at[i][2]);
            low[i] = CellIds.of(at[i][0] - 1, at[i][1], at[i][2]);
        }
        return new FlowEdge(zone, other, kpa, 0, high, low);
    }

    private static FlowField field(FlowEdge... edges) {
        return FlowField.of(List.of(edges));
    }

    @Test
    void client_003_an_opening_is_sent_within_32_blocks_and_not_beyond() {
        FlowField field = field(edge(1, FlowEdge.AMBIENT, 101, new int[] {0, 64, 0}));
        // The face between x = 0 and x = -1 is at x = 0; y 64.5, z 0.5.
        assertEquals(1, VisibleFlows.near(field, 31.5, 64.5, 0.5).size());
        assertTrue(VisibleFlows.near(field, 32.5, 64.5, 0.5).isEmpty());
    }

    @Test
    void client_003_it_carries_where_the_opening_is_and_the_way_the_flow_goes() {
        FlowField field = field(edge(1, FlowEdge.AMBIENT, 101.3, new int[] {10, 64, -20}));
        List<VisibleFlow> flows = VisibleFlows.near(field, 0, 64, 0);
        assertEquals(List.of(new VisibleFlow(101, 0, List.of(new VisibleOpening(10, 64, -20, WEST)))), flows);
        VisibleOpening o = flows.get(0).openings().get(0);
        assertEquals(-1, o.nx());
        assertEquals(0, o.ny());
        assertEquals(0, o.nz());
        assertEquals(10, o.mx(), 1e-9);
        assertEquals(64.5, o.my(), 1e-9);
        assertEquals(-19.5, o.mz(), 1e-9);
    }

    @Test
    void client_003_each_edge_is_its_own_flow_whichever_zones_it_joins() {
        FlowField field = field(
                edge(1, 2, 80, new int[] {0, 64, 0}, new int[] {0, 64, 1}),
                edge(3, 4, 30, new int[] {5, 64, 0}),
                edge(5, FlowEdge.AMBIENT, 50, new int[] {9, 64, 0}));
        List<VisibleFlow> flows = VisibleFlows.near(field, 0, 64, 0);
        assertEquals(3, flows.size());
        assertEquals(List.of(80, 30, 50), flows.stream().map(VisibleFlow::kpa).toList());
        assertEquals(2, flows.get(0).openings().size());
    }

    @Test
    void client_003_a_wide_breach_is_sampled_evenly_and_the_list_is_bounded() {
        // A wall 16 by 16.
        List<int[]> wall = new ArrayList<>();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                wall.add(new int[] {0, 64 + y, z});
            }
        }
        FlowField field = field(edge(1, FlowEdge.AMBIENT, 101, wall.toArray(int[][]::new)));
        List<VisibleOpening> sampled = VisibleFlows.near(field, 0, 72, 8).get(0).openings();
        assertEquals(VisibleFlows.MAX_OPENINGS_PER_FLOW, sampled.size());
        // It reaches across the whole wall, not only the part nearest the player.
        assertEquals(0, sampled.stream().mapToInt(VisibleOpening::z).min().orElseThrow());
        assertTrue(sampled.stream().mapToInt(VisibleOpening::z).max().orElseThrow() >= 12);
        assertEquals(64, sampled.stream().mapToInt(VisibleOpening::y).min().orElseThrow());
        assertTrue(sampled.stream().mapToInt(VisibleOpening::y).max().orElseThrow() >= 76);

        // Many edges: the nearest are kept, and the total stays within the limit.
        FlowEdge[] edges = new FlowEdge[VisibleFlows.MAX_FLOWS + 8];
        for (int i = 0; i < edges.length; i++) {
            int[][] holes = new int[64][];
            for (int k = 0; k < 64; k++) {
                holes[k] = new int[] {i - 20, 64 + k % 8, k / 8};
            }
            edges[i] = edge(i + 1, FlowEdge.AMBIENT, 50, holes);
        }
        List<VisibleFlow> flows = VisibleFlows.near(field(edges), 0, 66, 4);
        assertEquals(VisibleFlows.MAX_FLOWS, flows.size());
        assertTrue(flows.stream().mapToInt(f -> f.openings().size()).sum() <= VisibleFlows.MAX_OPENINGS);
        assertTrue(flows.stream().noneMatch(f -> f.openings().isEmpty()));
        assertTrue(flows.stream().noneMatch(f -> Math.abs(f.openings().get(0).x()) > 16), "a far edge was kept");
    }

    /** An edge with this flow, of openings at z = 0 along x, each {x}. */
    private static FlowField flowing(double moles, int... xs) {
        long[] high = new long[xs.length];
        long[] low = new long[xs.length];
        for (int i = 0; i < xs.length; i++) {
            high[i] = CellIds.of(xs[i], 64, 0);
            low[i] = CellIds.of(xs[i], 64, -1);
        }
        return FlowField.of(List.of(new FlowEdge(1, FlowEdge.AMBIENT, 101, 0, high, low, moles)));
    }

    @Test
    void client_010_the_flow_sent_is_what_passes_through_the_openings_in_range() {
        // Two openings share 40 mol/s; one is out of range.
        List<VisibleFlow> flows = VisibleFlows.near(flowing(40, 0, 60), 0, 64, 0);
        assertEquals(1, flows.size());
        assertEquals(1, flows.get(0).openings().size());
        assertEquals(20, flows.get(0).molesPerSecond(), 1);

        // A wall of openings, all in range: sampling them down doesn't lose any of the flow.
        long[] high = new long[VisibleFlows.MAX_OPENINGS_PER_FLOW + 20];
        long[] low = new long[high.length];
        for (int i = 0; i < high.length; i++) {
            high[i] = CellIds.of(i % 16, 64 + i / 16, 0);
            low[i] = CellIds.of(i % 16, 64 + i / 16, -1);
        }
        FlowField wide = FlowField.of(List.of(new FlowEdge(1, FlowEdge.AMBIENT, 101, 0, high, low, 40)));
        VisibleFlow sampled = VisibleFlows.near(wide, 8, 66, 0).get(0);
        assertEquals(VisibleFlows.MAX_OPENINGS_PER_FLOW, sampled.openings().size());
        assertEquals(40, sampled.molesPerSecond(), 2);
    }

    @Test
    void client_010_flow_steps_round_trip_closely_and_nothing_stays_nothing() {
        assertEquals(0, VisibleFlow.step(0));
        assertEquals(0, new VisibleFlow(0, 0, List.of()).molesPerSecond(), 1e-12);
        for (double moles : new double[] {1, 2.3, 5.4, 11.6, 23.6, 27, 69, 500, 10_000}) {
            double back = new VisibleFlow(0, VisibleFlow.step(moles), List.of()).molesPerSecond();
            assertEquals(moles, back, 0.05 * (1 + moles), "at " + moles);
        }
    }

    @Test
    void data_015_a_step_of_flow_is_a_change_and_less_is_not() {
        List<VisibleFlow> flow = VisibleFlows.near(flowing(27, 0), 0, 64, 0);
        assertFalse(VisibleFlows.shouldSend(flow, VisibleFlows.near(flowing(27.1, 0), 0, 64, 0), 20));
        assertTrue(VisibleFlows.shouldSend(flow, VisibleFlows.near(flowing(30, 0), 0, 64, 0), 20));
    }

    @Test
    void data_015_a_steady_breach_sends_nothing_until_it_changes() {
        List<VisibleFlow> steady =
                VisibleFlows.near(field(edge(1, FlowEdge.AMBIENT, 101.2, new int[] {0, 64, 0})), 0, 64, 0);
        // A difference within the same whole kPa, from a later atmos tick, with new zone ids.
        List<VisibleFlow> later =
                VisibleFlows.near(field(edge(7, FlowEdge.AMBIENT, 101.4, new int[] {0, 64, 0})), 0, 64, 0);
        assertFalse(VisibleFlows.shouldSend(steady, later, 20));
        List<VisibleFlow> changed =
                VisibleFlows.near(field(edge(1, FlowEdge.AMBIENT, 102.4, new int[] {0, 64, 0})), 0, 64, 0);
        assertTrue(VisibleFlows.shouldSend(steady, changed, 20), "a whole kPa changed");
    }

    @Test
    void data_015_an_opening_coming_into_range_or_leaving_is_a_change() {
        FlowField field = field(edge(1, FlowEdge.AMBIENT, 101, new int[] {0, 64, 0}, new int[] {30, 64, 0}));
        List<VisibleFlow> both = VisibleFlows.near(field, 15, 64, 0);
        List<VisibleFlow> one = VisibleFlows.near(field, -10, 64, 0);
        assertEquals(2, both.get(0).openings().size());
        assertEquals(1, one.get(0).openings().size());
        assertTrue(VisibleFlows.shouldSend(both, one, 20));
    }

    @Test
    void data_015_the_flow_reversing_is_a_change() {
        // The other side becomes the high one: the same hole, the other way.
        FlowField one = FlowField.of(List.of(
                new FlowEdge(1, 2, 100, 0, new long[] {CellIds.of(0, 64, 0)}, new long[] {CellIds.of(-1, 64, 0)})));
        FlowField other = FlowField.of(List.of(
                new FlowEdge(1, 2, 0, 100, new long[] {CellIds.of(0, 64, 0)}, new long[] {CellIds.of(-1, 64, 0)})));
        assertTrue(VisibleFlows.shouldSend(VisibleFlows.near(one, 0, 64, 0), VisibleFlows.near(other, 0, 64, 0), 20));
    }

    @Test
    void data_015_a_breach_ending_is_sent_once() {
        List<VisibleFlow> breach =
                VisibleFlows.near(field(edge(1, FlowEdge.AMBIENT, 101, new int[] {0, 64, 0})), 0, 64, 0);
        List<VisibleFlow> none = VisibleFlows.near(FlowField.EMPTY, 0, 64, 0);
        assertTrue(VisibleFlows.shouldSend(breach, none, 20), "the end");
        assertFalse(VisibleFlows.shouldSend(none, none, 20), "nothing, to no one");
        assertFalse(VisibleFlows.shouldSend(none, none, 100_000), "nothing is never resent");
    }

    @Test
    void data_015_an_unchanged_list_is_resent_as_a_failsafe() {
        List<VisibleFlow> breach =
                VisibleFlows.near(field(edge(1, FlowEdge.AMBIENT, 101, new int[] {0, 64, 0})), 0, 64, 0);
        assertFalse(VisibleFlows.shouldSend(breach, breach, VisibleFlows.RESEND_TICKS - 1));
        assertTrue(VisibleFlows.shouldSend(breach, breach, VisibleFlows.RESEND_TICKS));
    }
}
