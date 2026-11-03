package io.github.west3436.zas.engine.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.classify.CellIds;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The push of airflow (FLOW-001, FLOW-002, FLOW-003, FLOW-010). */
class FlowFieldTest {
    private static final double EPS = 1e-9;
    private static final FlowSettings S = FlowSettings.DEFAULT;
    private static final long ROOM = 7;

    /** A room at {@code kpa} in a vacuum with one hole: room cell (0, 64, 0), outside (-1, 64, 0). */
    private static FlowField breach(double kpa) {
        return FlowField.of(List.of(new FlowEdge(
                ROOM, FlowEdge.AMBIENT, kpa, 0, new long[] {CellIds.of(0, 64, 0)}, new long[] {CellIds.of(-1, 64, 0)
                })));
    }

    private static double speedTowardsHole(FlowField f, double x, long zone, double threshold) {
        double[] p = f.push(
                x,
                64.5,
                0.5,
                0,
                0,
                0,
                zone,
                threshold,
                new FlowSettings(S.range(), S.speedAtOneAtm(), S.maxSpeed(), 1));
        return p == null ? 0 : -p[0];
    }

    @Test
    void flow_002_speed_is_proportional_to_the_difference_and_capped() {
        assertEquals(1.0, S.speed(FlowSettings.ONE_ATM, 0), EPS);
        assertEquals(0.5, S.speed(FlowSettings.ONE_ATM / 2, 0), EPS);
        assertEquals(S.maxSpeed(), S.speed(10 * FlowSettings.ONE_ATM, 0), EPS);
        assertEquals(0, S.speed(0, 0), EPS);
    }

    @Test
    void flow_001_speed_falls_to_nothing_at_the_range() {
        assertEquals(0.5, S.speed(FlowSettings.ONE_ATM, 4.5), EPS);
        assertEquals(0, S.speed(FlowSettings.ONE_ATM, 9), EPS);
        assertEquals(0, S.speed(FlowSettings.ONE_ATM, 20), EPS);
    }

    @Test
    void flow_001_an_entity_in_the_room_is_carried_to_the_hole() {
        FlowField f = breach(FlowSettings.ONE_ATM);
        double[] p = f.push(4.5, 64.5, 0.5, 0, 0, 0, ROOM, 0, S);
        assertNotNull(p);
        assertTrue(p[0] < 0);
        assertEquals(0, p[1], EPS);
        assertEquals(0, p[2], EPS);
        // Faster nearer the hole.
        assertTrue(speedTowardsHole(f, 1.5, ROOM, 0) > speedTowardsHole(f, 5.5, ROOM, 0));
        // Nothing beyond the range: the hole's face is at x 0, so x 9.5 is 9.5 away.
        assertEquals(0, speedTowardsHole(f, 9.5, ROOM, 0), EPS);
    }

    @Test
    void flow_001_outside_it_blows_away_from_the_hole() {
        FlowField f = breach(FlowSettings.ONE_ATM);
        double[] p = f.push(-3.5, 64.5, 0.5, 0, 0, 0, FlowEdge.AMBIENT, 0, S);
        assertNotNull(p);
        assertTrue(p[0] < 0, "not out of the hole");
    }

    @Test
    void flow_002_a_higher_difference_pushes_harder() {
        assertTrue(speedTowardsHole(breach(80), 3.5, ROOM, 0) > speedTowardsHole(breach(40), 3.5, ROOM, 0));
    }

    @Test
    void flow_003_below_the_class_threshold_nothing_moves() {
        FlowField f = breach(50);
        assertTrue(speedTowardsHole(f, 3.5, ROOM, 49.9) > 0);
        assertEquals(0, speedTowardsHole(f, 3.5, ROOM, 50.1), EPS);
    }

    @Test
    void flow_001_only_entities_beside_the_edge_are_pushed() {
        FlowField f = breach(FlowSettings.ONE_ATM);
        assertEquals(0, speedTowardsHole(f, 3.5, 99, 0), EPS);
        assertTrue(speedTowardsHole(f, 3.5, FlowField.ANY_ZONE, 0) > 0);
    }

    @Test
    void flow_002_an_entity_faster_than_the_flow_is_left_alone() {
        FlowField f = breach(FlowSettings.ONE_ATM);
        assertNull(f.push(3.5, 64.5, 0.5, -2, 0, 0, ROOM, 0, S));
        // A slower one closes the response's share of the gap.
        double flow = S.speed(FlowSettings.ONE_ATM, 3.5);
        double[] p = f.push(3.5, 64.5, 0.5, -0.1, 0, 0, ROOM, 0, S);
        assertEquals(-(flow - 0.1) * S.response(), p[0], EPS);
    }

    @Test
    void flow_012_resistance_scales_the_flow() {
        FlowField f = breach(FlowSettings.ONE_ATM);
        double[] bare = f.push(3.5, 64.5, 0.5, 0, 0, 0, ROOM, 0, S, 0);
        double[] half = f.push(3.5, 64.5, 0.5, 0, 0, 0, ROOM, 0, S, 0.5);
        assertEquals(bare[0], f.push(3.5, 64.5, 0.5, 0, 0, 0, ROOM, 0, S)[0], EPS);
        assertEquals(bare[0] / 2, half[0], EPS);
        assertNull(f.push(3.5, 64.5, 0.5, 0, 0, 0, ROOM, 0, S, 1));
        // The flow's speed is what's scaled: an entity at half the flow's speed isn't pushed at r = 0.5.
        double flow = S.speed(FlowSettings.ONE_ATM, 3.5);
        assertNull(f.push(3.5, 64.5, 0.5, -flow / 2, 0, 0, ROOM, 0, S, 0.5));
    }

    @Test
    void flow_006_openings_are_found_by_section() {
        FlowField f = breach(FlowSettings.ONE_ATM);
        assertEquals(1, f.near(3.5, 64.5, 0.5, 9, ROOM, 0).size());
        assertEquals(0, f.near(300.5, 64.5, 0.5, 9, ROOM, 0).size());
        assertEquals(1, f.sections().size());
        assertTrue(FlowField.EMPTY.isEmpty());
        assertNull(FlowField.EMPTY.push(0, 0, 0, 0, 0, 0, ROOM, 0, S));
    }

    @Test
    void settings_are_checked() {
        assertThrows(IllegalArgumentException.class, () -> new FlowSettings(0, 1, 1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new FlowSettings(9, 1, 1, 1.5));
    }
}
