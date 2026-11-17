package io.github.west3436.zas.engine.scheduler;

import static io.github.west3436.zas.engine.scheduler.Sim.N2;
import static io.github.west3436.zas.engine.scheduler.Sim.O2;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryDeriver;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.zone.ZoneEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Carrying zones along when a mover moves their blocks (API-065, API-066, GEO-046, GEO-047), through
 * the pipeline: the tests play the server's part, handing each departure to the destination's
 * pipeline as an arrival.
 */
class RelocationTest {
    private static final double EPS = 1e-9;

    private static final GeometryPalette.Builder BUILDER = GeometryPalette.builder();
    /** A pane running north to south, as in {@link PointResolutionTest}. */
    private static final int PANE_NS = BUILDER.intern(GeometryDeriver.derive(pane(true)));
    /** The same pane turned a quarter: it runs east to west. */
    private static final int PANE_EW = BUILDER.intern(GeometryDeriver.derive(pane(false)));

    private static final GeometryPalette PALETTE = BUILDER.build();

    private static BitSet pane(boolean northSouth) {
        BitSet s = new BitSet(GeometryDeriver.VOXELS);
        if (northSouth) {
            GeometryDeriver.fill(s, 7, 0, 0, 9, 16, 16);
        } else {
            GeometryDeriver.fill(s, 0, 0, 7, 16, 16, 9);
        }
        return s;
    }

    private static Sim sim(Ambient ambient) {
        return new Sim(new TestWorld(ambient), PALETTE).withoutHeat();
    }

    /** The room at (0..2, 64..66, 0..2) with gas at about 150 kPa and a pin. */
    private static Sim pinnedRoom(Ambient ambient) {
        Sim sim = sim(ambient);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 30, 10);
        sim.pipeline.pin(SectionKeys.block(1, 65, 1), -1, "test:mover");
        sim.step();
        assertNotNull(sim.zoneAt(1, 65, 1), "the room didn't zone");
        return sim;
    }

    private static final Relocation.Box ROOM_BOX = new Relocation.Box(-1, 63, -1, 3, 67, 3);

    private static Relocation.Outcome outcome(Sim sim, long seq) {
        for (DimensionPipeline.Applied a : sim.applied) {
            for (Relocation.Outcome o : a.relocations()) {
                if (o.seq() == seq) {
                    return o;
                }
            }
        }
        return null;
    }

    private static List<ZoneEvent> events(Sim sim) {
        List<ZoneEvent> out = new ArrayList<>();
        for (DimensionPipeline.Applied a : sim.applied) {
            out.addAll(a.zoneEvents());
        }
        return out;
    }

    /** Hands a departure to the destination, as the server does; returns the arrival's seq. */
    private static long handOver(Relocation.Departure d, Sim to, boolean sameDimension) {
        return to.pipeline.arrive(sameDimension ? d.zoneId() : 0, d.zoneId(), d, d.moles());
    }

    /**
     * A sealed room at about 150 kPa with a pin, moved 100 blocks: the zone keeps its id, moles,
     * temperature and pin; nothing breaches, pauses or resumes for anyone to hear (API-065, API-066,
     * GEO-046).
     */
    @Test
    void api_065_room_moves_100_blocks_with_its_gas_and_pin() {
        Sim sim = pinnedRoom(Ambient.STANDARD_AIR);
        ZoneView before = sim.zoneAt(1, 65, 1);
        int eventsBefore = events(sim).size();

        long begin = sim.pipeline.beginRelocation(7, ROOM_BOX, 1000);
        sim.step();
        Relocation.Outcome begun = outcome(sim, begin);
        assertEquals(Relocation.Status.BEGUN, begun.status());
        assertArrayEquals(new long[] {before.id()}, begun.zones());

        // The mover clears the room and builds it again 100 blocks east.
        sim.world.box(-1, 63, -1, 3, 67, 3, TestWorld.AIR);
        sim.world.room(100, 64, 0, 102, 66, 2);
        sim.step();
        ZoneView held = sim.snapshot().zone(before.id());
        assertNotNull(held, "the suspended zone keeps its cells while blocks change around it");
        assertTrue(held.paused());
        assertEquals(before.moles(N2), held.moles(N2), EPS);

        long complete = sim.pipeline.completeRelocation(7, Relocation.Transform.offset(100, 0, 0));
        sim.step();
        Relocation.Outcome done = outcome(sim, complete);
        assertEquals(Relocation.Status.DEPARTED, done.status());
        assertEquals(1, done.departed().size());
        assertNull(sim.snapshot().zone(before.id()), "the zone left");

        long arrive = handOver(done.departed().get(0), sim, true);
        sim.steps(2);
        Relocation.Outcome arrived = outcome(sim, arrive);
        assertEquals(Relocation.Status.ARRIVED, arrived.status());
        assertEquals(before.id(), arrived.arrivedId(), "within a dimension the zone keeps its id");

        ZoneView after = sim.zoneAt(101, 65, 1);
        assertNotNull(after, "no zone at the destination");
        assertEquals(before.id(), after.id());
        assertEquals(before.cellCount(), after.cellCount());
        assertEquals(before.moles(N2), after.moles(N2), EPS);
        assertEquals(before.moles(O2), after.moles(O2), EPS);
        assertEquals(before.temperature(), after.temperature(), EPS);
        assertEquals(before.pressure(), after.pressure(), 1e-6);
        assertEquals(List.of("test:mover"), after.pins());
        assertFalse(after.paused());
        assertNull(sim.zoneAt(1, 65, 1), "nothing is left at the source");

        List<ZoneEvent> news = events(sim).subList(eventsBefore, events(sim).size());
        assertEquals(1, news.size(), () -> "only the move is news: " + news);
        assertEquals(ZoneEvent.Kind.RELOCATED, news.get(0).kind());
        assertEquals(before.id(), news.get(0).zoneId());
        assertEquals(before.id(), news.get(0).otherId());
    }

    /**
     * A move into another dimension, whose ambient is a vacuum: the zone gets a new id there, with the
     * same gas and pin, and the source engine no longer has it (API-065, GEO-046).
     */
    @Test
    void api_065_room_moves_into_another_dimension() {
        Sim from = pinnedRoom(Ambient.STANDARD_AIR);
        ZoneView before = from.zoneAt(1, 65, 1);
        Sim to = sim(Ambient.VACUUM);
        to.world.room(0, 100, 0, 2, 102, 2);
        to.step();
        assertEquals(0, to.snapshot().zoneCount());

        long begin = from.pipeline.beginRelocation(1, ROOM_BOX, 1000);
        from.step();
        assertEquals(Relocation.Status.BEGUN, outcome(from, begin).status());
        long complete = from.pipeline.completeRelocation(1, new Relocation.Transform(1, 65, 1, 1, 101, 1, 0));
        from.step();
        Relocation.Outcome done = outcome(from, complete);
        assertEquals(Relocation.Status.DEPARTED, done.status());

        long arrive = handOver(done.departed().get(0), to, false);
        to.steps(2);
        Relocation.Outcome arrived = outcome(to, arrive);
        assertEquals(Relocation.Status.ARRIVED, arrived.status());
        ZoneView after = to.zoneAt(1, 101, 1);
        assertNotNull(after);
        assertEquals(arrived.arrivedId(), after.id());
        assertEquals(before.moles(N2), after.moles(N2), EPS);
        assertEquals(before.moles(O2), after.moles(O2), EPS);
        assertEquals(before.temperature(), after.temperature(), EPS);
        assertEquals(List.of("test:mover"), after.pins());
        assertEquals(0, from.snapshot().zoneCount());
        for (ZoneEvent e : events(to)) {
            assertNotEquals(ZoneEvent.Kind.BREACHED, e.kind(), () -> "breached: " + e);
            assertNotEquals(ZoneEvent.Kind.RESUMED, e.kind(), () -> "resumed: " + e);
        }
    }

    /**
     * A room turned a quarter clockwise: its cell in the half of a pane on its side arrives in the half
     * of the turned pane on its side (GEO-046).
     */
    @Test
    void geo_046_turned_room_keeps_its_half_of_a_pane() {
        Sim sim = sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.set(3, 65, 1, PANE_NS); // the room has its west half
        sim.add(1, 65, 1, 0, 10);
        sim.step();
        ZoneView before = sim.zoneAt(1, 65, 1);
        assertEquals(28, before.cellCount());

        long begin = sim.pipeline.beginRelocation(3, new Relocation.Box(-1, 63, -1, 3, 67, 3), 1000);
        sim.step();
        assertEquals(Relocation.Status.BEGUN, outcome(sim, begin).status());
        // Turned clockwise about the room's centre and put 100 blocks east: the east wall is now the
        // south wall, and the pane in it runs east to west, the room on its north side.
        sim.world.room(100, 64, 0, 102, 66, 2);
        sim.world.set(101, 65, 3, PANE_EW);
        long complete = sim.pipeline.completeRelocation(3, new Relocation.Transform(1, 65, 1, 101, 65, 1, 1));
        sim.step();
        Relocation.Outcome done = outcome(sim, complete);
        assertEquals(Relocation.Status.DEPARTED, done.status());
        long arrive = handOver(done.departed().get(0), sim, true);
        sim.steps(2);
        assertEquals(Relocation.Status.ARRIVED, outcome(sim, arrive).status());

        ZoneView after = sim.zoneAt(101, 65, 1);
        assertNotNull(after);
        assertEquals(28, after.cellCount(), "the 27 cells and the pane's north half");
        Geometry turned = PALETTE.geometry(PANE_EW);
        int north = turned.cell(0).footprint(Face.UP).get(0, 0) ? 0 : 1;
        long pane = SectionKeys.block(101, 65, 3);
        assertSame(after, sim.snapshot().zoneAt(pane, north), "the north half is the room's");
        assertNull(sim.snapshot().zoneAt(pane, 1 - north), "the south half is outside");
        assertEquals(before.moles(O2), after.moles(O2), EPS);
    }

    /** Turning a hint four times gives it back, and a quarter turn takes each face to the next. */
    @Test
    void geo_046_hints_turn_with_the_move() {
        Relocation.Transform quarter = new Relocation.Transform(0, 0, 0, 0, 0, 0, 1);
        for (Face f : Face.values()) {
            for (int u = 0; u < 16; u += 5) {
                for (int v = 0; v < 16; v += 3) {
                    int hint = Relocation.hint(f, u, v);
                    int h = hint;
                    for (int i = 0; i < 4; i++) {
                        h = quarter.hint(h);
                    }
                    assertEquals(hint, h, f + " " + u + " " + v);
                }
            }
        }
        assertEquals(Face.EAST, Relocation.hintFace(quarter.hint(Relocation.hint(Face.NORTH, 0, 0))));
        assertEquals(Face.SOUTH, Relocation.hintFace(quarter.hint(Relocation.hint(Face.EAST, 0, 0))));
        assertEquals(Face.UP, Relocation.hintFace(quarter.hint(Relocation.hint(Face.UP, 0, 0))));
        // The voxel (0, 0, 0), north-west, goes north-east: (15, 0, 0).
        int down = quarter.hint(Relocation.hint(Face.DOWN, 0, 0));
        assertEquals(15, Relocation.hintU(down));
        assertEquals(0, Relocation.hintV(down));
        assertEquals(-1, quarter.hint(-1));
        // Positions turn as Minecraft's BlockPos.rotate(CLOCKWISE_90) turns them: (x, z) to (-z, x).
        long p = quarter.block(SectionKeys.block(2, 5, 1));
        assertEquals(-1, SectionKeys.blockX(p));
        assertEquals(5, SectionKeys.blockY(p));
        assertEquals(2, SectionKeys.blockZ(p));
    }

    /**
     * A zone with cells inside and outside the box stays where it is and is listed as left behind;
     * zones wholly inside are carried (GEO-046).
     */
    @Test
    void geo_046_a_zone_across_the_box_stays() {
        Sim sim = sim(Ambient.STANDARD_AIR);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.world.room(10, 64, 0, 14, 66, 2); // runs out of the box below
        sim.add(1, 65, 1, 0, 10);
        sim.add(11, 65, 1, 0, 10);
        sim.step();
        ZoneView inside = sim.zoneAt(1, 65, 1);
        ZoneView across = sim.zoneAt(11, 65, 1);
        long begin = sim.pipeline.beginRelocation(1, new Relocation.Box(-1, 63, -1, 12, 67, 3), 1000);
        sim.step();
        Relocation.Outcome o = outcome(sim, begin);
        assertEquals(Relocation.Status.BEGUN, o.status());
        assertArrayEquals(new long[] {inside.id()}, o.zones());
        assertArrayEquals(new long[] {across.id()}, o.straddling());
        assertFalse(sim.snapshot().zone(across.id()).paused(), "the zone across the box carries on");
        assertTrue(sim.snapshot().zone(inside.id()).paused());
    }

    /**
     * An arrival inside a sealed hangar takes its cells from the hangar's zone, which keeps its gas,
     * as when blocks fill its cells; a pocket of it the hull closed off goes to the ambient
     * (GEO-046, CORE-022, CORE-075).
     */
    @Test
    void geo_046_arrival_displaces_a_hangars_air() {
        Sim sim = sim(Ambient.VACUUM);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 0, 10);
        sim.world.room(20, 60, -5, 40, 75, 10); // the hangar
        sim.add(21, 61, -4, 0, 100);
        sim.step();
        ZoneView hangar = sim.zoneAt(21, 61, -4);
        ZoneView room = sim.zoneAt(1, 65, 1);
        assertNotNull(hangar);
        double hangarO2 = hangar.moles(O2);

        long begin = sim.pipeline.beginRelocation(1, ROOM_BOX, 1000);
        sim.step();
        assertEquals(Relocation.Status.BEGUN, outcome(sim, begin).status());
        // The hull goes up inside the hangar; the hangar air it closes off is a pocket of its own.
        sim.world.room(30, 65, 0, 32, 67, 2);
        sim.step();
        ZoneView pocket = sim.zoneAt(31, 66, 1);
        assertNotNull(pocket, "the closed-off air splits from the hangar");
        assertNotEquals(hangar.id(), pocket.id());
        double pocketO2 = pocket.moles(O2);

        long complete = sim.pipeline.completeRelocation(1, new Relocation.Transform(1, 65, 1, 31, 66, 1, 0));
        sim.step();
        long arrive = handOver(outcome(sim, complete).departed().get(0), sim, true);
        sim.steps(2);
        assertEquals(Relocation.Status.ARRIVED, outcome(sim, arrive).status());
        ZoneView after = sim.zoneAt(31, 66, 1);
        assertEquals(room.id(), after.id());
        assertEquals(room.moles(O2), after.moles(O2), EPS, "the room arrives with its own gas only");
        assertNull(sim.snapshot().zone(pocket.id()), "the pocket is gone");
        ZoneView hangarAfter = sim.zoneAt(21, 61, -4);
        assertEquals(hangarO2 - pocketO2, hangarAfter.moles(O2), 1e-6, "the hangar keeps the rest");
    }

    /** Cancelling lets the zones resume where they are, unchanged and with no news (API-065). */
    @Test
    void api_065_cancel_resumes_quietly() {
        Sim sim = pinnedRoom(Ambient.STANDARD_AIR);
        ZoneView before = sim.zoneAt(1, 65, 1);
        int eventsBefore = events(sim).size();
        sim.pipeline.beginRelocation(1, ROOM_BOX, 1000);
        sim.step();
        assertTrue(sim.snapshot().zone(before.id()).paused());
        long cancel = sim.pipeline.cancelRelocation(1);
        sim.steps(2);
        assertEquals(Relocation.Status.CANCELLED, outcome(sim, cancel).status());
        ZoneView after = sim.snapshot().zone(before.id());
        assertFalse(after.paused());
        assertEquals(before.moles(N2), after.moles(N2), EPS);
        assertEquals(eventsBefore, events(sim).size(), () -> "news: " + events(sim));
        long again = sim.pipeline.cancelRelocation(1);
        sim.step();
        assertEquals(Relocation.Status.ENDED, outcome(sim, again).status());
    }

    /**
     * A begin fails whole: on a zone another ticket holds, on zones with more cells than allowed, and
     * in a paused dimension (API-065, GEO-047).
     */
    @Test
    void geo_047_begin_refusals() {
        Sim sim = pinnedRoom(Ambient.STANDARD_AIR);
        ZoneView room = sim.zoneAt(1, 65, 1);
        long tooLarge = sim.pipeline.beginRelocation(1, ROOM_BOX, 26);
        sim.step();
        assertEquals(Relocation.Status.TOO_LARGE, outcome(sim, tooLarge).status());
        assertFalse(sim.snapshot().zone(room.id()).paused(), "nothing changed");

        long first = sim.pipeline.beginRelocation(2, ROOM_BOX, 27);
        long second = sim.pipeline.beginRelocation(3, ROOM_BOX, 1000);
        sim.step();
        assertEquals(Relocation.Status.BEGUN, outcome(sim, first).status());
        assertEquals(Relocation.Status.BUSY, outcome(sim, second).status());
        sim.pipeline.cancelRelocation(2);
        sim.step();

        sim.settings = sim.settings.withDimensionPaused(true);
        long paused = sim.pipeline.beginRelocation(4, ROOM_BOX, 1000);
        sim.step();
        assertEquals(Relocation.Status.PAUSED, outcome(sim, paused).status());
        long ended = sim.pipeline.completeRelocation(4, Relocation.Transform.IDENTITY);
        sim.step();
        assertEquals(Relocation.Status.ENDED, outcome(sim, ended).status());
    }

    /**
     * A departure whose component the server rejects isn't reported; the zones come back suspended and
     * the move runs again next tick, so the gas leaves exactly once (API-065, CORE-070).
     */
    @Test
    void api_065_rejected_departure_runs_again() {
        Sim sim = pinnedRoom(Ambient.STANDARD_AIR);
        ZoneView before = sim.zoneAt(1, 65, 1);
        sim.pipeline.beginRelocation(1, ROOM_BOX, 1000);
        sim.step();
        long complete = sim.pipeline.completeRelocation(1, Relocation.Transform.offset(100, 0, 0));
        long chunk = SectionKeys.chunk(0, 0);
        boolean[] once = {true};
        sim.betweenCloseAndApply = s -> {
            if (once[0]) {
                once[0] = false;
                s.world.unload(chunk);
                s.world.load(chunk); // a new lifecycle generation: the component is stale
            }
        };
        DimensionPipeline.Applied a = sim.step();
        assertEquals(1, a.rejected());
        assertNull(outcome(sim, complete), "a rejected departure isn't reported");
        ZoneView back = sim.snapshot().zone(before.id());
        assertNotNull(back, "answered from the last table");
        DimensionPipeline.Applied again = sim.step();
        Relocation.Outcome done = outcome(sim, complete);
        assertNotNull(done, () -> "the move ran again: " + again + " " + a + " " + sim.engine.relocating());
        assertEquals(Relocation.Status.DEPARTED, done.status());
        assertEquals(before.moles(N2), done.departed().get(0).moles()[N2], EPS);
        assertEquals(0, sim.snapshot().zoneCount());
    }

    /**
     * An arrival whose cells are all solid adds nothing to the graph and says so, so the server adds
     * its gas there as any addition (API-065).
     */
    @Test
    void api_065_arrival_with_no_cells_is_added_instead() {
        Sim sim = pinnedRoom(Ambient.STANDARD_AIR);
        sim.pipeline.beginRelocation(1, ROOM_BOX, 1000);
        sim.step();
        sim.world.box(99, 63, -1, 103, 67, 3, TestWorld.SOLID);
        long complete = sim.pipeline.completeRelocation(1, Relocation.Transform.offset(100, 0, 0));
        sim.step();
        long arrive = handOver(outcome(sim, complete).departed().get(0), sim, true);
        sim.steps(2);
        assertEquals(Relocation.Status.ADDED, outcome(sim, arrive).status());
        assertEquals(0, sim.snapshot().zoneCount());
    }
}
