package io.github.west3436.zas.engine.zone;

import static io.github.west3436.zas.engine.zone.Grid.cell;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.CELL;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.ROOM_T;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.air;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.assertConsistent;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.fillWith;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.graph;
import static io.github.west3436.zas.engine.zone.ZoneFixtures.vacuum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.zone.ExchangeSettings.Venting;
import org.junit.jupiter.api.Test;

/**
 * Venting through one opening, into the ambient and into a much larger zone, under each venting
 * model (CORE-035, CORE-068, D-21). The rooms are 10 cells (a 10 × 1 × 1 row) and 1,000 cells (10
 * × 10 × 10) of 1,000 L, at standard pressure, and the opening is one face at the room's corner.
 */
class VentingTest {
    private static final ExchangeSettings VOLUME_AWARE = ExchangeSettings.DEFAULT;
    private static final ExchangeSettings FAITHFUL = ExchangeSettings.DEFAULT.withVenting(Venting.FAITHFUL);
    /** The sharer volume V_s, 4,000 L. */
    private static final double VS = ExchangeSettings.DEFAULT.sharerVolume();
    /** The sharing ratio per second through one opening. */
    private static final double R = ExchangeSettings.DEFAULT.ratio(1);

    private static final double SMALL = 10 * CELL;
    private static final double LARGE = 1000 * CELL;
    /** The much larger zone: 100 cells of 10⁶ L, as large as 100,000 cells. */
    private static final double HALL = 100 * 1e6;

    /** The two rooms' sizes: nx, ny, nz. */
    private static final int[] SMALL_ROOM = {10, 1, 1};

    private static final int[] LARGE_ROOM = {10, 10, 10};

    /** A room of nx × ny × nz cells from the origin, with no openings yet. */
    private static Grid room(int[] n) {
        return new Grid().box(0, 0, 0, n[0] - 1, n[1] - 1, n[2] - 1, CELL);
    }

    private static Zone fill(ZoneGraph graph, int[] n) {
        Zone room = graph.createZone(Grid.cells(0, 0, 0, n[0] - 1, n[1] - 1, n[2] - 1));
        fillWith(graph, room, air(101.325, CELL, ROOM_T));
        return room;
    }

    /**
     * The fraction of its gas a room loses in one 1 s tick through one opening into a vacuum
     * ambient: one unzoned cell beside the room's corner.
     */
    private static double ventToAmbient(int[] n, ExchangeSettings settings) {
        Grid grid = room(n).box(n[0], 0, 0, n[0], 0, 0, CELL);
        ZoneGraph graph = graph(grid, vacuum(), settings);
        Zone room = fill(graph, n);
        assertEquals(1, room.ambientEdge().connections(), "one opening");
        double before = room.mixture().totalMoles();
        graph.exchange();
        assertTrue(room.isAlive());
        assertConsistent(graph, grid);
        return 1 - room.mixture().totalMoles() / before;
    }

    /**
     * The fraction of its gas a room loses in one 1 s tick through one opening, a door so the
     * zones never merge, into a hall of {@link #HALL} at vacuum. The hall is 10 × 10 × 1 cells
     * beyond the room's x = nx − 1 face; every face between them is closed but the one at the
     * corner.
     */
    private static double ventToHall(int[] n, ExchangeSettings settings) {
        Grid grid = room(n).box(n[0], 0, 0, n[0] + 9, 9, 0, 1e6);
        for (int y = 0; y < n[1]; y++) {
            for (int z = 0; z < n[2]; z++) {
                grid.close(cell(n[0] - 1, y, z), cell(n[0], y, z));
            }
        }
        grid.door(cell(n[0] - 1, 0, 0), cell(n[0], 0, 0));
        ZoneGraph graph = graph(grid, vacuum(), settings);
        Zone room = fill(graph, n);
        Zone hall = graph.createZone(Grid.cells(n[0], 0, 0, n[0] + 9, 9, 0));
        assertEquals(HALL, hall.volume(), 1e-6);
        assertEquals(0, hall.mixture().totalMoles(), "the hall is at vacuum");
        assertEquals(1, graph.edgeBetween(room, hall).connections(), "one opening");
        assertEquals(1, room.edges().size(), "the room's only edge is to the hall");
        double before = room.mixture().totalMoles();
        graph.exchange();
        assertEquals(before, room.mixture().totalMoles() + hall.mixture().totalMoles(), 1e-9 * before);
        assertConsistent(graph, grid);
        return 1 - room.mixture().totalMoles() / before;
    }

    /**
     * Volume-aware venting into the ambient (CORE-035): the same opening passes the same moles per
     * tick at the same pressure, through its 4,000 L sharer, so the fraction a room loses falls in
     * proportion to its volume plus the sharer's, r·V_s/(V + V_s). The 1,000-cell room loses 72
     * times less than the 10-cell one.
     */
    @Test
    void core_035_larger_room_vents_proportionally_slower_into_the_ambient() {
        double small = ventToAmbient(SMALL_ROOM, VOLUME_AWARE);
        double large = ventToAmbient(LARGE_ROOM, VOLUME_AWARE);
        assertEquals(R * VS / (VS + SMALL), small, 1e-12);
        assertEquals(R * VS / (VS + LARGE), large, 1e-12);
        assertEquals(
                small * (SMALL + VS), large * (LARGE + VS), 1e-9 * small * (SMALL + VS), "in proportion to V + V_s");
        assertTrue(large < small / 70, "the large room lost " + large + ", the small one " + small);
    }

    /**
     * Volume-aware venting into a much larger zone (CORE-035): the rooms drain into a hall 100
     * times the large room's volume as they do into the ambient, the limit of an infinitely large
     * zone, and the larger loses a proportionally smaller fraction. A zone of any size drains
     * slower than into the ambient.
     */
    @Test
    void core_035_larger_room_vents_proportionally_slower_into_a_much_larger_zone() {
        double small = ventToHall(SMALL_ROOM, VOLUME_AWARE);
        double large = ventToHall(LARGE_ROOM, VOLUME_AWARE);
        for (double[] room : new double[][] {{SMALL, small}, {LARGE, large}}) {
            double v = room[0];
            double reduced = v * HALL / (v + HALL);
            assertEquals(R * VS / (VS + reduced) * HALL / (v + HALL), room[1], 1e-12, "V = " + v);
            double intoAmbient = R * VS / (VS + v);
            assertTrue(room[1] < intoAmbient, "faster than into the ambient, V = " + v);
            assertEquals(intoAmbient, room[1], 1e-3 * intoAmbient, "much as into the ambient, V = " + v);
        }
        assertEquals(small * (SMALL + VS), large * (LARGE + VS), 1e-3 * small * (SMALL + VS));
        assertTrue(large < small / 70, "the large room lost " + large + ", the small one " + small);
    }

    /**
     * The faithful option, into the ambient (CORE-068): ZAS's sharer is the zone's volume plus V_s,
     * so a room loses r·(V + V_s)/(2V + V_s), which doesn't depend on its volume beyond ZAS's
     * four-cell term: both rooms lose between r/2 and 0.6·r per tick, within 17 % of each other,
     * where volume-aware venting puts them a factor of 72 apart.
     */
    @Test
    void core_068_faithful_rooms_lose_the_same_fraction_into_the_ambient() {
        double small = ventToAmbient(SMALL_ROOM, FAITHFUL);
        double large = ventToAmbient(LARGE_ROOM, FAITHFUL);
        assertEquals(R * (SMALL + VS) / (2 * SMALL + VS), small, 1e-12);
        assertEquals(R * (LARGE + VS) / (2 * LARGE + VS), large, 1e-12);
        for (double f : new double[] {small, large}) {
            assertTrue(f >= R / 2 && f <= 0.6 * R, "lost " + f + " of r = " + R);
        }
        assertTrue(small / large < 1.17, "the small room lost " + small + ", the large one " + large);
    }

    /**
     * The faithful option, into a much larger zone (CORE-068): ZAS moves both zones the full ratio
     * toward their volume-weighted mean, which is the hall's, so both rooms lose r·V_hall/(V +
     * V_hall), r to within 1 %, whatever their size.
     */
    @Test
    void core_068_faithful_rooms_lose_the_same_fraction_into_a_much_larger_zone() {
        double small = ventToHall(SMALL_ROOM, FAITHFUL);
        double large = ventToHall(LARGE_ROOM, FAITHFUL);
        assertEquals(R * HALL / (SMALL + HALL), small, 1e-12);
        assertEquals(R * HALL / (LARGE + HALL), large, 1e-12);
        assertEquals(R, small, 0.01 * R);
        assertEquals(R, large, 0.01 * R);
        assertEquals(small, large, 0.01 * small, "the same fraction");
    }

    /**
     * Venting is defined per second under both models (CORE-046): the 10-cell room keeps the same
     * gas after 10 s at periods of 0.5, 1 and 2 s.
     */
    @Test
    void core_046_venting_is_per_second() {
        for (ExchangeSettings model : new ExchangeSettings[] {VOLUME_AWARE, FAITHFUL}) {
            double at1 = leftAfterTenSeconds(model.withPeriod(1));
            assertEquals(at1, leftAfterTenSeconds(model.withPeriod(0.5)), 1e-12, model.venting() + ", 0.5 s");
            assertEquals(at1, leftAfterTenSeconds(model.withPeriod(2)), 1e-12, model.venting() + ", 2 s");
            assertTrue(at1 < 0.9, model.venting() + ": it hardly vented, so the check proves nothing");
        }
    }

    private static double leftAfterTenSeconds(ExchangeSettings settings) {
        Grid grid = room(SMALL_ROOM).box(SMALL_ROOM[0], 0, 0, SMALL_ROOM[0], 0, 0, CELL);
        ZoneGraph graph = graph(grid, vacuum(), settings);
        Zone room = fill(graph, SMALL_ROOM);
        double before = room.mixture().totalMoles();
        int ticks = (int) Math.round(10 / settings.periodSeconds());
        for (int t = 0; t < ticks; t++) {
            graph.exchange();
        }
        assertTrue(room.isAlive());
        return room.mixture().totalMoles() / before;
    }
}
