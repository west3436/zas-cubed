package io.github.west3436.zas.engine.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Impact damage's formula (FLOW-005). */
class FlowImpactTest {
    private static final double EPS = 1e-9;

    @Test
    void flow_005_damage_is_speed_lost_times_ten_less_three() {
        assertEquals(7, FlowImpact.damage(1.0), EPS);
        assertEquals(12, FlowImpact.damage(1.5), EPS);
        assertEquals(0.5, FlowImpact.damage(0.35), EPS);
    }

    @Test
    void flow_005_a_small_loss_does_no_damage() {
        assertEquals(0, FlowImpact.damage(FlowImpact.LEAST_SPEED_LOST), EPS);
        assertEquals(0, FlowImpact.damage(0.1), EPS);
        assertEquals(0, FlowImpact.damage(0), EPS);
    }

    @Test
    void flow_005_head_on_it_loses_all_its_speed() {
        assertEquals(0.9, FlowImpact.speedLost(-0.9, 0, true, false), EPS);
        assertEquals(0.9, FlowImpact.speedLost(0, 0.9, false, true), EPS);
        assertEquals(Math.hypot(0.6, 0.8), FlowImpact.speedLost(0.6, 0.8, true, true), EPS);
    }

    @Test
    void flow_005_a_glancing_blow_loses_little() {
        assertEquals(Math.hypot(1, 0.05) - 1, FlowImpact.speedLost(1, 0.05, false, true), EPS);
        assertEquals(0, FlowImpact.damage(FlowImpact.speedLost(1, 0.05, false, true)), EPS);
    }

    @Test
    void flow_005_nothing_stopped_loses_nothing() {
        assertEquals(0, FlowImpact.speedLost(0.6, 0.8, false, false), EPS);
    }
}
