package io.github.west3436.zas.engine.scheduler;

import static io.github.west3436.zas.engine.scheduler.Sim.N2;
import static io.github.west3436.zas.engine.scheduler.Sim.O2;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Resetting a zone to the ambient by operator command (OPS-013): the zone goes whole, and the gas
 * is an operator sink attributed to whoever asked (CORE-036, CORE-073).
 */
class RegionResetTest {
    private static final double EPS = 1e-9;
    private static final String ALEX = "command:alex";

    private static long reset(Sim sim, int x, int y, int z) {
        return sim.pipeline.reset(SectionKeys.block(x, y, z), -1, ALEX);
    }

    /** Two sealed rooms in the vacuum: x 4 to 10 with 80 N2 and 20 O2, x 14 to 20 with 8 and 2. */
    private static Sim twoRooms() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(4, 64, 1, 10, 66, 3);
        sim.world.room(14, 64, 1, 20, 66, 3);
        sim.add(5, 65, 2, 80, 20);
        sim.add(15, 65, 2, 8, 2);
        sim.step();
        return sim;
    }

    /** The whole zone goes, the other room stays, and the checker attributes the gas. */
    @Test
    void ops_013_a_zone_reverts_and_the_checker_records_the_sink() {
        Sim sim = twoRooms();
        ZoneView room = sim.zoneAt(5, 65, 2);
        ZoneView other = sim.zoneAt(15, 65, 2);

        long seq = reset(sim, 7, 65, 2);
        DimensionPipeline.Applied applied = sim.step();

        assertNull(sim.zoneAt(5, 65, 2), "the reset cell's neighbour is still zoned");
        assertNull(sim.zoneAt(7, 65, 2), "the reset cell is still zoned");
        assertNull(sim.zoneAt(10, 66, 3), "the far corner is still zoned");
        OpOutcome outcome = sim.outcomes.get(seq);
        assertEquals(OpOutcome.Outcome.ZONE, outcome.outcome());
        assertEquals(room.id(), outcome.zoneId());
        assertEquals(80, outcome.moved(N2), EPS);
        assertEquals(20, outcome.moved(O2), EPS);

        ConservationReport report = applied.conservation();
        assertTrue(report.conserved(), () -> "violations: " + report.violations());
        assertEquals(-80, report.recorded(LedgerCategory.OPERATOR, N2), EPS);
        assertEquals(-20, report.recorded(LedgerCategory.OPERATOR, O2), EPS);
        assertEquals(0, report.recorded(LedgerCategory.AMBIENT, N2), EPS, "recorded as an exchange with the ambient");
        assertEquals(80, report.removedBy(ALEX, N2), EPS);
        assertEquals(20, report.removedBy(ALEX, O2), EPS);
        assertEquals(0, report.removedBy("command:someone_else", N2));

        ZoneView kept = sim.zoneAt(15, 65, 2);
        assertEquals(other.id(), kept.id());
        assertArrayEquals(other.moles(), kept.moles());

        // The attribution is that period's: the next one starts clean.
        ConservationReport next = sim.step().conservation();
        assertEquals(0, next.removedBy(ALEX, N2));
        assertEquals(0, next.recorded(LedgerCategory.OPERATOR, N2));
    }

    /** The zone's chunks are marked for saving, so the reset outlasts a restart (DATA-002). */
    @Test
    void ops_013_the_zones_chunks_save_again() {
        Sim sim = twoRooms();
        long id = sim.zoneAt(5, 65, 2).id();
        long chunk = SectionKeys.chunk(0, 0);
        reset(sim, 7, 65, 2);
        long[] changed = sim.step().changedChunks();
        assertTrue(
                Arrays.stream(changed).anyMatch(c -> c == chunk), () -> "changed chunks: " + Arrays.toString(changed));
        // The other room also has cells in this chunk, so it still saves a record.
        ChunkRecord record = sim.pipeline.recordFor(chunk, false);
        assertTrue(record.zones().stream().noneMatch(s -> s.zoneId() == id), "the chunk still saves the reset zone");
    }

    /** Resetting where there's no zone does nothing and reports the ambient. */
    @Test
    void ops_013_no_zone_means_nothing_to_do() {
        Sim sim = twoRooms();
        reset(sim, 7, 65, 2);
        sim.step();
        long again = reset(sim, 7, 65, 2);
        ConservationReport report = sim.step().conservation();
        OpOutcome outcome = sim.outcomes.get(again);
        assertEquals(OpOutcome.Outcome.AMBIENT, outcome.outcome());
        assertEquals(0, outcome.moved(N2));
        assertEquals(0, report.removedBy(ALEX, N2));
    }

    /** Two resets of one zone in a batch remove its gas once. */
    @Test
    void ops_013_two_resets_of_a_zone_remove_its_gas_once() {
        Sim sim = twoRooms();
        long first = reset(sim, 5, 65, 2);
        long second = reset(sim, 9, 65, 2);
        ConservationReport report = sim.step().conservation();
        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(first).outcome());
        assertEquals(OpOutcome.Outcome.AMBIENT, sim.outcomes.get(second).outcome());
        assertEquals(80, report.removedBy(ALEX, N2), EPS);
        assertTrue(report.conserved());
    }

    /** A paused zone keeps its state (GEO-030): nothing moves, and the outcome says it is paused. */
    @Test
    void ops_013_a_paused_zone_is_left_as_it_was() {
        Sim sim = twoRooms();
        sim.settings = sim.settings.withDimensionPaused(true);
        sim.step();
        ZoneView paused = sim.zoneAt(5, 65, 2);
        assertTrue(paused.paused());

        long seq = reset(sim, 5, 65, 2);
        ConservationReport report = sim.step().conservation();

        OpOutcome outcome = sim.outcomes.get(seq);
        assertEquals(OpOutcome.Outcome.UNRESOLVED, outcome.outcome());
        assertEquals(paused.id(), outcome.zoneId());
        assertEquals(0, outcome.moved(N2));
        ZoneView kept = sim.zoneAt(5, 65, 2);
        assertEquals(paused.id(), kept.id());
        assertArrayEquals(paused.moles(), kept.moles());
        assertEquals(0, report.recorded(LedgerCategory.OPERATOR, N2));
    }

    /** A pinned zone resets too, and its pins go with it: it was an operator's explicit order. */
    @Test
    void ops_013_a_pinned_zone_resets_and_its_pins_go() {
        Sim sim = twoRooms();
        sim.pipeline.pin(SectionKeys.block(5, 65, 2), -1, "zas:owner");
        sim.step();
        assertEquals(List.of("zas:owner"), sim.zoneAt(5, 65, 2).pins());

        long seq = reset(sim, 5, 65, 2);
        sim.step();
        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(seq).outcome());
        assertNull(sim.zoneAt(5, 65, 2), "the pinned zone is still there");

        sim.add(5, 65, 2, 1, 0);
        sim.step();
        assertEquals(List.of(), sim.zoneAt(5, 65, 2).pins(), "a later zone there inherited the pin");
    }

    /**
     * An addition in the same batch sees the cells unzoned, whichever was queued first, so it forms
     * a new zone holding only its own gas.
     */
    @Test
    void ops_013_gas_added_in_the_same_batch_forms_a_new_zone() {
        Sim sim = twoRooms();
        long old = sim.zoneAt(5, 65, 2).id();

        long before = sim.add(5, 65, 2, 3, 0);
        reset(sim, 5, 65, 2);
        long after = sim.add(5, 65, 2, 4, 0);
        ConservationReport report = sim.step().conservation();

        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(before).outcome());
        assertEquals(OpOutcome.Outcome.ZONE, sim.outcomes.get(after).outcome());
        ZoneView fresh = sim.zoneAt(5, 65, 2);
        assertNotNull(fresh, "no zone formed");
        assertNotEquals(old, fresh.id());
        assertEquals(7, fresh.moles(N2), EPS);
        assertEquals(0, fresh.moles(O2), EPS);
        assertEquals(80, report.removedBy(ALEX, N2), EPS);
        assertTrue(report.conserved(), () -> "violations: " + report.violations());
    }

    /**
     * A zone beside the reset one, joined by an edge, now opens onto ambient cells, so it gains
     * ambient connections and vents, as beside a dissolved zone (CORE-076).
     */
    @Test
    void ops_013_a_neighbour_across_an_edge_now_opens_onto_the_ambient() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.world.room(4, 64, 1, 10, 66, 3);
        sim.world.room(12, 64, 1, 19, 66, 3);
        sim.add(5, 65, 2, 1, 0);
        sim.add(13, 65, 2, 80, 10);
        sim.step();
        sim.world.set(11, 65, 2, TestWorld.AIR);
        sim.step();
        ZoneView neighbour = sim.zoneAt(13, 65, 2);
        assertEquals(1, neighbour.zoneEdges(), "no edge between the rooms");

        reset(sim, 5, 65, 2);
        ConservationReport report = sim.step().conservation();

        assertNull(sim.zoneAt(5, 65, 2));
        ZoneView after = sim.zoneAt(13, 65, 2);
        assertEquals(0, after.zoneEdges(), "the edge to the reset zone is still there");
        assertTrue(after.moles(N2) < neighbour.moles(N2), "the neighbour isn't venting");
        assertTrue(report.conserved(), () -> "violations: " + report.violations());
    }

    /**
     * A reset comes before the undo point, so a block changing in the room while the batch is out
     * can't undo it: there is nothing of the zone left to reject.
     */
    @Test
    void ops_013_a_block_change_during_the_tick_doesnt_undo_a_reset() {
        Sim sim = twoRooms();
        reset(sim, 5, 65, 2);
        sim.betweenCloseAndApply = s -> s.world.set(7, 65, 2, TestWorld.SOLID);
        ConservationReport report = sim.step().conservation();
        sim.betweenCloseAndApply = s -> {};
        assertNull(sim.zoneAt(5, 65, 2));
        assertEquals(80, report.removedBy(ALEX, N2), EPS);
        sim.steps(2);
        assertNull(sim.zoneAt(5, 65, 2), "the zone came back");
    }
}
