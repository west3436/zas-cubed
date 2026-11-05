package io.github.west3436.zas.airflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** The particles' curves and caps (CLIENT-003); what they look like is the owner's check. */
class AirflowParticlePlanTest {
    private static final int EAST = 5;

    private record Started(double x, double y, double z, double vx, double vy, double vz) {}

    private static VisibleFlow oneHole(int kpa) {
        return new VisibleFlow(kpa, 0, List.of(new VisibleOpening(0, 0, 0, EAST)));
    }

    /** A flow of {@code n} openings in a line at this x, each leaving by the east face. */
    private static VisibleFlow line(int kpa, int x, int n) {
        List<VisibleOpening> openings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            openings.add(new VisibleOpening(x, 0, i, EAST));
        }
        return new VisibleFlow(kpa, 0, openings);
    }

    /** The particles started on average per tick. */
    private static double average(List<VisibleFlow> flows, double px, double density) {
        RandomGenerator random = new Random(1);
        int ticks = 20_000;
        long total = 0;
        for (int i = 0; i < ticks; i++) {
            total += AirflowParticlePlan.plan(flows, px, 0, 0, density, random, (x, y, z, vx, vy, vz) -> {});
        }
        return (double) total / ticks;
    }

    @Test
    void client_003_density_and_speed_rise_with_the_difference_up_to_their_limits() {
        double[] kpa = {20, 65, 101, 150, 200};
        for (int i = 1; i < kpa.length; i++) {
            assertTrue(
                    AirflowParticlePlan.perOpening((int) kpa[i]) > AirflowParticlePlan.perOpening((int) kpa[i - 1]),
                    "density at " + kpa[i]);
            assertTrue(
                    AirflowParticlePlan.speed((int) kpa[i]) >= AirflowParticlePlan.speed((int) kpa[i - 1]),
                    "speed at " + kpa[i]);
        }
        assertEquals(0.1, AirflowParticlePlan.perOpening(20), 1e-9);
        assertEquals(1, AirflowParticlePlan.perOpening(5000), 1e-9);
        assertEquals(0.6, AirflowParticlePlan.speed(5000), 1e-9);
        assertTrue(AirflowParticlePlan.speed(20) > 0.1, "a weak flow still moves");
    }

    @Test
    void client_003_a_stronger_flow_starts_more_particles() {
        double weak = average(List.of(oneHole(20)), 5, AirflowParticlePlan.ALL);
        double strong = average(List.of(oneHole(100)), 5, AirflowParticlePlan.ALL);
        assertEquals(0.1, weak, 0.01);
        assertEquals(0.5, strong, 0.02);
    }

    @Test
    void client_003_particles_start_at_the_opening_and_move_to_the_low_side() {
        RandomGenerator random = new Random(2);
        List<Started> started = new ArrayList<>();
        VisibleFlow east = line(150, 0, 16);
        for (int i = 0; i < 200; i++) {
            AirflowParticlePlan.plan(
                    List.of(east),
                    5,
                    0,
                    0,
                    AirflowParticlePlan.ALL,
                    random,
                    (x, y, z, vx, vy, vz) -> started.add(new Started(x, y, z, vx, vy, vz)));
        }
        assertTrue(started.size() > 100, "too few particles: " + started.size());
        for (Started p : started) {
            assertTrue(p.vx() > 0, "not moving to the low side: " + p);
            assertTrue(Math.abs(p.vy()) < p.vx() && Math.abs(p.vz()) < p.vx(), "more sideways than forwards: " + p);
            // The face between the blocks is at x = 1: just on the high side of it, or just past.
            assertTrue(p.x() >= 1 - 0.6 && p.x() <= 1 + 0.2, "not at the face: " + p);
            assertTrue(p.y() >= 0 && p.y() <= 1, "outside the opening: " + p);
            assertTrue(p.z() >= 0 && p.z() <= 16, "outside the openings: " + p);
        }
    }

    @Test
    void client_003_none_beyond_32_blocks() {
        // The face is at x = 1, y = 0.5, z = 0.5.
        VisibleFlow flow = new VisibleFlow(5000, 0, List.of(new VisibleOpening(0, 0, 0, EAST)));
        RandomGenerator random = new Random(3);
        for (int i = 0; i < 1000; i++) {
            assertEquals(
                    0,
                    AirflowParticlePlan.plan(
                            List.of(flow), 33.5, 0.5, 0.5, AirflowParticlePlan.ALL, random, (x, y, z, vx, vy, vz) -> {
                                throw new AssertionError("a particle beyond the range");
                            }));
        }
        assertEquals(1, average(List.of(flow), 32.5, AirflowParticlePlan.ALL), 1e-9);
    }

    @Test
    void client_003_caps_per_edge_and_per_client() {
        // Far past the cap on its own, and it stays there.
        assertEquals(
                AirflowParticlePlan.MAX_PER_FLOW,
                average(List.of(line(5000, 0, 64)), 5, AirflowParticlePlan.ALL),
                1e-9);

        // Ten such edges, past the total.
        List<VisibleFlow> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(line(5000, i, 64));
        }
        RandomGenerator random = new Random(4);
        for (int tick = 0; tick < 100; tick++) {
            int[] count = new int[1];
            int returned = AirflowParticlePlan.plan(
                    many, 5, 0, 0, AirflowParticlePlan.ALL, random, (x, y, z, vx, vy, vz) -> count[0]++);
            assertEquals(count[0], returned);
            assertEquals(AirflowParticlePlan.MAX_PER_TICK, count[0]);
        }
    }

    @Test
    void client_003_the_nearest_edges_get_the_budget_first() {
        // Five edges, each at its cap, at x = 0, -3, -6, -9 and -12. East of them all, the total
        // goes on the four nearest, whose particles are all east of x = -8.6.
        List<VisibleFlow> flows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            flows.add(line(5000, -i * 3, 64));
        }
        RandomGenerator random = new Random(5);
        List<Started> started = new ArrayList<>();
        AirflowParticlePlan.plan(
                flows,
                10,
                0,
                32,
                AirflowParticlePlan.ALL,
                random,
                (x, y, z, vx, vy, vz) -> started.add(new Started(x, y, z, vx, vy, vz)));
        assertEquals(AirflowParticlePlan.MAX_PER_TICK, started.size());
        for (Started p : started) {
            assertTrue(p.x() >= -8.6, "the farthest edge got some: " + p);
        }
    }

    @Test
    void client_003_follows_the_particles_setting() {
        List<VisibleFlow> flows = List.of(line(100, 0, 8));
        double all = average(flows, 5, AirflowParticlePlan.ALL);
        double decreased = average(flows, 5, AirflowParticlePlan.DECREASED);
        double minimal = average(flows, 5, AirflowParticlePlan.MINIMAL);
        assertEquals(4, all, 0.01);
        assertEquals(all * AirflowParticlePlan.DECREASED, decreased, 0.05);
        assertEquals(0, minimal, 0);
    }

    @Test
    void client_003_no_flows_no_particles() {
        assertEquals(
                0,
                AirflowParticlePlan.plan(
                        List.of(), 0, 0, 0, AirflowParticlePlan.ALL, new Random(6), (x, y, z, vx, vy, vz) -> {
                            throw new AssertionError("a particle from nothing");
                        }));
    }

    @Test
    void client_003_an_opening_inside_a_block_has_no_direction_but_stays_slow_and_local() {
        VisibleFlow flow = new VisibleFlow(100, 0, List.of(new VisibleOpening(0, 0, 0, VisibleOpening.NO_FACE)));
        RandomGenerator random = new Random(7);
        List<Started> started = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            AirflowParticlePlan.plan(
                    List.of(flow),
                    5,
                    0,
                    0,
                    AirflowParticlePlan.ALL,
                    random,
                    (x, y, z, vx, vy, vz) -> started.add(new Started(x, y, z, vx, vy, vz)));
        }
        assertTrue(!started.isEmpty());
        double speed = AirflowParticlePlan.speed(100);
        for (Started p : started) {
            assertTrue(Math.abs(p.vx()) < speed && Math.abs(p.vy()) < speed && Math.abs(p.vz()) < speed, "" + p);
            assertTrue(p.x() >= 0 && p.x() <= 1 && p.y() >= 0 && p.y() <= 1 && p.z() >= 0 && p.z() <= 1, "" + p);
        }
    }
}
