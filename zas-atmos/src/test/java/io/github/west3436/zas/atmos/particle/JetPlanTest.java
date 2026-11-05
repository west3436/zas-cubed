package io.github.west3436.zas.atmos.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.atmos.tool.FireExtinguisherItem;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The numbers behind each kind of jet, and the directions within its cone (CLIENT-018). */
class JetPlanTest {
    private static final double EPS = 1e-9;

    /** Where a particle ends up: its speed, losing {@link JetPlan#FRICTION} each tick of its life. */
    private static double travelled(JetPlan plan) {
        double speed = plan.speed();
        double at = 0;
        for (int i = 0; i < plan.lifetime(); i++) {
            at += speed;
            speed *= JetPlan.FRICTION;
        }
        return at;
    }

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    @Test
    void client_018_a_spray_flies_as_far_as_it_reaches() {
        assertEquals(5.0, travelled(JetPlan.of(GasJetKind.SPRAY, 5.0)), EPS);
        assertEquals(2.5, travelled(JetPlan.of(GasJetKind.SPRAY, 2.5)), EPS);
    }

    @Test
    void client_018_a_spray_runs_until_the_next_one() {
        JetPlan plan = JetPlan.of(GasJetKind.SPRAY, 5.0);
        assertEquals(FireExtinguisherItem.SPRAY_INTERVAL, JetPlan.SPRAY_TICKS);
        assertEquals(JetPlan.SPRAY_TICKS, plan.ticks());
        assertEquals(JetPlan.SPRAY_PARTICLES, plan.particles(), EPS);
    }

    @Test
    void client_018_a_spray_stopped_at_once_still_shows() {
        assertEquals(JetPlan.SPRAY_MIN_DISTANCE, JetPlan.of(GasJetKind.SPRAY, 0).distance(), EPS);
        assertEquals(
                JetPlan.SPRAY_MIN_DISTANCE,
                JetPlan.of(GasJetKind.SPRAY, Double.NaN).distance(),
                EPS);
    }

    @Test
    void client_018_a_cone_stays_within_its_half_angle() {
        Random random = new Random(1);
        double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, -1}, {0.6, 0, 0.8}, {0.48, 0.6, 0.64}};
        for (double[] axis : axes) {
            for (int i = 0; i < 2000; i++) {
                double[] d = JetPlan.direction(axis[0], axis[1], axis[2], JetPlan.SPRAY_SPREAD, random);
                assertEquals(1.0, length(d), 1e-9);
                double cos = d[0] * axis[0] + d[1] * axis[1] + d[2] * axis[2];
                assertTrue(cos >= Math.cos(JetPlan.SPRAY_SPREAD) - 1e-9, "outside the cone: cos " + cos);
            }
        }
    }

    @Test
    void client_018_a_cone_fills_its_angle() {
        Random random = new Random(2);
        double widest = 1;
        for (int i = 0; i < 2000; i++) {
            widest = Math.min(widest, JetPlan.direction(0, 0, 1, JetPlan.SPRAY_SPREAD, random)[2]);
        }
        assertTrue(widest < Math.cos(JetPlan.SPRAY_SPREAD * 0.95), "it never got near the edge: cos " + widest);
    }

    @Test
    void client_018_no_axis_means_every_direction() {
        Random random = new Random(3);
        double[] sum = new double[3];
        boolean down = false;
        boolean up = false;
        int n = 5000;
        for (int i = 0; i < n; i++) {
            double[] d = JetPlan.direction(0, 0, 0, 0, random);
            assertEquals(1.0, length(d), 1e-9);
            for (int k = 0; k < 3; k++) {
                sum[k] += d[k];
            }
            down |= d[2] < -0.9;
            up |= d[2] > 0.9;
        }
        assertTrue(down && up, "it missed part of the sphere");
        assertTrue(length(sum) / n < 0.05, "the directions lean one way: " + length(sum) / n);
    }
}
