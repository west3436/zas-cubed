package io.github.west3436.zas.airflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.flow.FlowEdge;
import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.provider.Frame;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Airflow at blocks another mod shows somewhere else, as Sable shows a sub-level's plot at the ship
 * (COMPAT-101): what a player is sent, and where its client plays the sound and starts the particles
 * (CLIENT-003, CLIENT-010).
 */
class AirflowFramesTest {
    private static final int EAST = 5;

    /** Where the plot is: far out in the level, as Sable's first plot. */
    private static final int PX = 20_481_024;

    private static final int PY = 128;
    private static final int PZ = 20_481_024;

    /** Where the world shows the plot's corner. */
    private static final double SX = 10.25;

    private static final double SY = 200.5;
    private static final double SZ = -3.75;

    /**
     * The plot shown with its corner at (SX, SY, SZ), turned a quarter so its east is the world's
     * south, and moving a block north and a tenth up each game tick.
     */
    private static final Frame SHIP = new Frame() {
        @Override
        public double[] toShown(double x, double y, double z) {
            double[] t = turnToShown(x - PX, y - PY, z - PZ);
            return new double[] {t[0] + SX, t[1] + SY, t[2] + SZ};
        }

        @Override
        public double[] toLevel(double x, double y, double z) {
            double[] t = turnToLevel(x - SX, y - SY, z - SZ);
            return new double[] {t[0] + PX, t[1] + PY, t[2] + PZ};
        }

        @Override
        public double[] turnToShown(double x, double y, double z) {
            return new double[] {-z, y, x};
        }

        @Override
        public double[] turnToLevel(double x, double y, double z) {
            return new double[] {z, y, -x};
        }

        @Override
        public double[] shownVelocity(double x, double y, double z) {
            return new double[] {0, 0.1, -1};
        }
    };

    private static final Frame.Lookup ON_THE_PLOT = (x, z) -> x >= PX - 1024 && z >= PZ - 1024 ? SHIP : null;

    /** A hole in the plot leaving by its east face, two blocks along its x and z. */
    private static final VisibleOpening HOLE = new VisibleOpening(PX + 2, PY + 1, PZ + 2, EAST);

    private static double[] shownHole() {
        return SHIP.toShown(HOLE.mx(), HOLE.my(), HOLE.mz());
    }

    @Test
    void client_003_a_player_where_the_ship_shows_is_sent_its_openings() {
        FlowEdge edge = new FlowEdge(
                7, FlowEdge.AMBIENT, 203, 101, new long[] {CellIds.of(PX + 2, PY + 1, PZ + 2)}, new long[] {
                    CellIds.of(PX + 3, PY + 1, PZ + 2)
                });
        FlowField field = FlowField.of(List.of(edge));
        double[] hole = shownHole();
        double[] player = {hole[0] + 3, hole[1], hole[2] - 2};
        assertTrue(
                VisibleFlows.near(field, player[0], player[1], player[2]).isEmpty(),
                "found where the player is, though the opening is in the plot");
        List<VisibleFlow> flows =
                VisibleFlows.near(field, List.of(player, SHIP.toLevel(player[0], player[1], player[2])));
        assertEquals(1, flows.size());
        assertEquals(HOLE, flows.get(0).openings().get(0), "the opening as it is in the plot");
    }

    @Test
    void client_010_a_breach_on_the_ship_sounds_where_the_hole_shows() {
        VisibleFlow flow = new VisibleFlow(102, VisibleFlow.step(30), List.of(HOLE));
        double[] hole = shownHole();
        double[] ear = {hole[0] + 2, hole[1] + 1, hole[2] + 2};
        assertTrue(
                AirflowSoundPlan.plan(List.of(flow), ear[0], ear[1], ear[2]).isEmpty(),
                "heard without the frame, from the plot");
        List<AirflowSoundPlan.Source> heard = AirflowSoundPlan.plan(List.of(flow), ear[0], ear[1], ear[2], ON_THE_PLOT);
        assertEquals(1, heard.size());
        AirflowSoundPlan.Source s = heard.get(0);
        assertEquals(hole[0], s.x(), 1e-9);
        assertEquals(hole[1], s.y(), 1e-9);
        assertEquals(hole[2], s.z(), 1e-9);
        List<AirflowSoundPlan.Loop> loops =
                AirflowSoundPlan.loops(List.of(flow), ear[0], ear[1], ear[2], 20, 101, ON_THE_PLOT);
        assertEquals(2, loops.size(), "a hiss and a wind");
        assertEquals(AirflowSoundPlan.Sound.HISS, loops.get(0).sound());
        assertEquals(AirflowSoundPlan.Sound.WIND_HEAVY, loops.get(1).sound());
        assertEquals(hole[2], loops.get(0).z(), 1e-9);
    }

    @Test
    void client_003_puffs_on_the_ship_start_at_the_hole_and_go_the_way_the_ship_turns_the_flow() {
        VisibleFlow flow = new VisibleFlow(200, 0, List.of(HOLE));
        double[] hole = shownHole();
        Random random = new Random(3);
        record Started(double x, double y, double z, double vx, double vy, double vz) {}
        List<Started> started = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            AirflowParticlePlan.plan(
                    List.of(flow),
                    hole[0] + 4,
                    hole[1],
                    hole[2],
                    AirflowParticlePlan.ALL,
                    random,
                    ON_THE_PLOT,
                    (x, y, z, vx, vy, vz) -> started.add(new Started(x, y, z, vx, vy, vz)));
        }
        assertEquals(100, started.size(), "one a tick at 200 kPa");
        for (Started p : started) {
            assertTrue(
                    Math.abs(p.x() - hole[0]) <= 0.5
                            && Math.abs(p.y() - hole[1]) <= 0.5
                            && Math.abs(p.z() - hole[2]) <= 0.7,
                    "a puff starts off the hole: " + p + ", the hole at " + List.of(hole[0], hole[1], hole[2]));
            // The plot's east is the world's south, and the ship moves north at a block a tick.
            assertTrue(p.vz() + 1 > 0.4, "not carried south: " + p);
            assertTrue(Math.abs(p.vx()) < 0.2 * (p.vz() + 1), "not carried along the turned flow: " + p);
            assertEquals(0.1, p.vy(), 0.2 * (p.vz() + 1), "not moving up with the ship: " + p);
        }
        assertEquals(
                0,
                AirflowParticlePlan.plan(
                        List.of(flow),
                        hole[0] + 4,
                        hole[1],
                        hole[2],
                        AirflowParticlePlan.ALL,
                        random,
                        (x, y, z, vx, vy, vz) -> {}),
                "started without the frame, from the plot");
    }
}
