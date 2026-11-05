package io.github.west3436.zas.atmos.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.DeviceActivity;
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
    void client_018_size_goes_by_ratio_and_stops_at_each_end() {
        assertEquals(0, JetPlan.scale(0.5, 1, 100), EPS);
        assertEquals(0, JetPlan.scale(1, 1, 100), EPS);
        assertEquals(0.5, JetPlan.scale(10, 1, 100), EPS);
        assertEquals(1, JetPlan.scale(100, 1, 100), EPS);
        assertEquals(1, JetPlan.scale(1e9, 1, 100), EPS);
        assertEquals(1, JetPlan.scale(Double.POSITIVE_INFINITY, 1, 100), EPS);
        assertEquals(0, JetPlan.scale(Double.NaN, 1, 100), EPS);
        assertEquals(0, JetPlan.scale(-5, 1, 100), EPS);
    }

    @Test
    void client_018_a_bigger_leak_is_a_bigger_jet() {
        JetPlan small = JetPlan.of(GasJetKind.LEAK, JetPlan.LEAK_LOW);
        JetPlan middle = JetPlan.of(GasJetKind.LEAK, 1);
        JetPlan big = JetPlan.of(GasJetKind.LEAK, JetPlan.LEAK_HIGH);
        assertTrue(small.particles() < middle.particles() && middle.particles() < big.particles());
        assertTrue(small.distance() < middle.distance() && middle.distance() < big.distance());
        assertTrue(small.size() < big.size());
        assertEquals(big, JetPlan.of(GasJetKind.LEAK, JetPlan.LEAK_HIGH * 100));
        assertEquals(small, JetPlan.of(GasJetKind.LEAK, JetPlan.LEAK_LOW / 100));
    }

    @Test
    void client_018_a_leak_is_a_narrow_jet_that_flies_its_distance() {
        JetPlan plan = JetPlan.of(GasJetKind.LEAK, 1);
        assertTrue(plan.spread() < Math.toRadians(30), "not a jet: " + plan.spread());
        assertEquals(plan.distance(), travelled(plan), EPS);
    }

    @Test
    void client_018_a_bigger_burst_is_a_bigger_cloud() {
        // A 70 L tank at 40 atm, and a canister at its 90 atm rating.
        JetPlan tank = JetPlan.of(GasJetKind.BURST, 116);
        JetPlan canister = JetPlan.of(GasJetKind.BURST, 3700);
        assertTrue(tank.particles() < canister.particles());
        assertTrue(tank.distance() < canister.distance());
        assertTrue(tank.size() < canister.size());
    }

    @Test
    void client_018_a_burst_runs_from_fewest_to_most_in_every_direction() {
        JetPlan small = JetPlan.of(GasJetKind.BURST, JetPlan.BURST_LOW);
        JetPlan big = JetPlan.of(GasJetKind.BURST, JetPlan.BURST_HIGH);
        assertEquals(JetPlan.BURST_FEWEST, small.particles(), EPS);
        assertEquals(JetPlan.BURST_MOST, big.particles(), EPS);
        assertEquals(Math.PI, big.spread(), EPS);
        assertEquals(big.distance(), travelled(big), EPS);
    }

    @Test
    void client_017_the_biggest_burst_fits_a_sources_cap() {
        JetPlan big = JetPlan.of(GasJetKind.BURST, JetPlan.BURST_HIGH);
        assertTrue(big.perTick() <= JetEmitters.PER_SOURCE, big.perTick() + " a tick");
        JetPlan leak = JetPlan.of(GasJetKind.LEAK, JetPlan.LEAK_HIGH);
        assertTrue(leak.perTick() <= JetEmitters.PER_SOURCE, leak.perTick() + " a tick");
        JetPlan end = JetPlan.of(GasJetKind.OPEN_END, JetPlan.OPEN_END_MAX_FLOW);
        assertTrue(end.perTick() <= JetEmitters.PER_SOURCE, end.perTick() + " a tick");
    }

    @Test
    void client_019_a_trickle_shows_nothing() {
        assertFalse(JetPlan.showsFlow(0));
        assertFalse(JetPlan.showsFlow(0.2499));
        assertFalse(JetPlan.showsFlow(-0.2499));
        assertFalse(JetPlan.showsFlow(Double.NaN));
        assertTrue(JetPlan.showsFlow(JetPlan.OPEN_END_MIN_FLOW));
        assertTrue(JetPlan.showsFlow(-JetPlan.OPEN_END_MIN_FLOW));
        assertTrue(JetPlan.showsFlow(100));
    }

    @Test
    void client_019_a_lone_pipe_a_little_over_its_room_is_about_the_threshold() {
        // A 70 L pipe 0.5 atm above a large room: the open end's 0.163 of the 1.44 moles over the room's.
        double overRoom = 0.5 * 101.325 * 70 / (8.314 * 293.15);
        assertEquals(JetPlan.OPEN_END_MIN_FLOW, 0.163 * overRoom, 0.03);
    }

    @Test
    void client_019_more_flow_is_a_bigger_jet_either_way() {
        JetPlan small = JetPlan.of(GasJetKind.OPEN_END, JetPlan.OPEN_END_MIN_FLOW);
        JetPlan middle = JetPlan.of(GasJetKind.OPEN_END, 4);
        JetPlan big = JetPlan.of(GasJetKind.OPEN_END, JetPlan.OPEN_END_MAX_FLOW);
        assertTrue(small.perTick() < middle.perTick() && middle.perTick() < big.perTick());
        assertTrue(small.distance() < middle.distance() && middle.distance() < big.distance());
        assertTrue(small.size() < big.size());
        assertEquals(big, JetPlan.of(GasJetKind.OPEN_END, JetPlan.OPEN_END_MAX_FLOW * 100));

        JetPlan drawn = JetPlan.of(GasJetKind.OPEN_END, -4);
        assertFalse(middle.inward());
        assertTrue(drawn.inward());
        assertEquals(middle.perTick(), drawn.perTick(), EPS);
        assertEquals(middle.distance(), drawn.distance(), EPS);
    }

    @Test
    void client_019_a_flowing_end_runs_into_its_next_atmos_tick() {
        JetPlan plan = JetPlan.of(GasJetKind.OPEN_END, 1);
        assertTrue(plan.ticks() > AtmosTicks.PERIOD, "there would be a gap each atmos tick");
        assertTrue(plan.ticks() <= AtmosTicks.PERIOD + 5, "it would run on too long after the flow stops");
        assertEquals(plan.distance(), travelled(plan), EPS);
    }

    @Test
    void client_019_a_device_working_hard_shows_more_than_one_just_working() {
        JetPlan working = JetPlan.of(GasJetKind.DEVICE, DeviceActivity.WORKING);
        JetPlan hard = JetPlan.of(GasJetKind.DEVICE, DeviceActivity.HARD);
        assertTrue(working.perTick() < hard.perTick());
        assertTrue(working.distance() < hard.distance());
        assertTrue(working.size() < hard.size());
        assertEquals(working.distance(), travelled(working), EPS);
        assertEquals(hard.distance(), travelled(hard), EPS);
    }

    @Test
    void client_019_a_device_is_the_same_size_either_way() {
        JetPlan out = JetPlan.of(GasJetKind.DEVICE, DeviceActivity.HARD);
        JetPlan in = JetPlan.of(GasJetKind.DEVICE, -DeviceActivity.HARD);
        assertFalse(out.inward());
        assertTrue(in.inward());
        assertEquals(out.perTick(), in.perTick(), EPS);
        assertEquals(out.distance(), in.distance(), EPS);
        assertEquals(out.size(), in.size(), EPS);
    }

    @Test
    void client_019_a_device_is_light() {
        // At its hardest, the most of its particles in the air at once: a dozen or so.
        JetPlan hard = JetPlan.of(GasJetKind.DEVICE, DeviceActivity.HARD);
        assertTrue(hard.perTick() * hard.lifetime() <= 12, hard.perTick() * hard.lifetime() + " in the air");
        assertTrue(hard.perTick() <= JetEmitters.PER_SOURCE, hard.perTick() + " a tick");
    }

    @Test
    void client_019_only_a_device_starts_across_a_patch() {
        assertEquals(
                JetPlan.DEVICE_PATCH,
                JetPlan.of(GasJetKind.DEVICE, DeviceActivity.HARD).patch(),
                EPS);
        for (GasJetKind kind : GasJetKind.values()) {
            if (kind != GasJetKind.DEVICE) {
                assertEquals(0, JetPlan.of(kind, 1).patch(), EPS, kind.name());
            }
        }
    }

    @Test
    void client_019_a_patch_stays_across_its_axis_and_fills_it() {
        Random random = new Random(4);
        double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, -1}};
        for (double[] axis : axes) {
            double widest = 0;
            for (int i = 0; i < 2000; i++) {
                double[] p = JetPlan.across(axis[0], axis[1], axis[2], 0.35, random);
                assertEquals(0, p[0] * axis[0] + p[1] * axis[1] + p[2] * axis[2], EPS, "it left the plane");
                for (double v : p) {
                    assertTrue(Math.abs(v) <= 0.35 + EPS, "it left the patch: " + v);
                    widest = Math.max(widest, Math.abs(v));
                }
            }
            assertTrue(widest > 0.33, "it never got near the edge: " + widest);
        }
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
