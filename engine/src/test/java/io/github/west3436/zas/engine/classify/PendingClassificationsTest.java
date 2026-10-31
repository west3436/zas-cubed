package io.github.west3436.zas.engine.classify;

import static io.github.west3436.zas.engine.classify.TestWorld.AIR;
import static io.github.west3436.zas.engine.classify.TestWorld.air;
import static io.github.west3436.zas.engine.classify.TestWorld.cell;
import static io.github.west3436.zas.engine.classify.TestWorld.perLitre;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PendingClassificationsTest {
    private static GasMixture moles(double n) {
        GasMixture m = new GasMixture(TestWorld.REGISTRY, TestWorld.CONFIG, 1000, TestWorld.ROOM_T);
        m.setMoles(0, n);
        return m;
    }

    @Test
    void geo_028_operations_wait_for_classification() {
        // A new sealed room across a section boundary; the worker has only the first section.
        TestWorld world = new TestWorld().box(10, 1, 1, 20, 2, 2, AIR);
        long far = SectionKeys.section(1, 0, 0);
        short[] saved = world.save(far);
        world.forget(far);
        SectionTopology t = world.topology();
        GasMixture ambient = air(101.325);
        ZoneGraph graph = TestWorld.graph(t, ambient);
        EnclosureFill fill = new EnclosureFill();
        PendingClassifications<GasMixture> pending = new PendingClassifications<>();

        // Gas arrives at two cells of the room before anything is classified.
        GasMixture first = moles(5);
        GasMixture second = moles(7);
        GasMixture third = moles(11);
        pending.hold(cell(11, 1, 1), first);
        pending.hold(cell(11, 1, 1), second);
        pending.hold(cell(14, 2, 2), third);
        assertTrue(pending.isPending(cell(11, 1, 1)));
        assertEquals(2, pending.runnable().size());

        // Tick 1: the fill reaches the missing section, so every entry waits.
        for (PendingClassifications.Entry<GasMixture> e : pending.runnable()) {
            FillResult.Unresolved u = assertInstanceOf(FillResult.Unresolved.class, fill.fill(t, e.seed()));
            pending.await(e, u.missingSections());
        }
        assertEquals(List.of(), pending.runnable());
        assertArrayEquals(new long[] {far}, pending.missingSections());
        assertEquals(List.of(), pending.sectionsArrived(world::has), "still missing");
        assertEquals(0, graph.zoneCount());

        // The section arrives.
        world.restore(far, saved);
        assertEquals(2, pending.sectionsArrived(world::has).size());
        assertArrayEquals(new long[0], pending.missingSections());

        // Tick 2: the first entry classifies, its zone forms, and every held operation in the
        // region applies, in arrival order.
        PendingClassifications.Entry<GasMixture> entry = pending.runnable().get(0);
        FillResult.Enclosed room = assertInstanceOf(FillResult.Enclosed.class, fill.fill(t, entry.seed()));
        List<Zone> zones = EnclosureZones.create(graph, t, room.cells());
        assertEquals(1, zones.size());
        Zone zone = zones.get(0);
        List<GasMixture> ops = new ArrayList<>(pending.complete(entry));
        for (PendingClassifications.Entry<GasMixture> covered : pending.takeCovered(room)) {
            ops.addAll(covered.ops());
        }
        assertTrue(pending.isEmpty());
        assertEquals(List.of(first, second, third), ops);
        for (GasMixture op : ops) {
            assertTrue(graph.add(zone, op, LedgerCategory.API));
        }

        double expected = perLitre(ambient) * room.volume() + 5 + 7 + 11;
        assertEquals(expected, zone.mixture().totalMoles(), 1e-9 * expected, "the zone holds all of it");
        assertEquals(44, zone.cellCount());
        assertSame(zone, graph.zoneOf(cell(20, 2, 2)));
    }

    @Test
    void geo_028_pending_bookkeeping() {
        PendingClassifications<String> pending = new PendingClassifications<>();
        long a = cell(1, 1, 1);
        long b = cell(-1, 1, 1);
        PendingClassifications.Entry<String> ea = pending.hold(a, "x");
        PendingClassifications.Entry<String> eb = pending.request(b);
        assertSame(ea, pending.hold(a, "y"));
        assertEquals(List.of("x", "y"), ea.ops());
        assertEquals(List.of(), eb.ops());
        assertEquals(List.of(ea, eb), pending.entries(), "creation order");
        assertThrows(UnsupportedOperationException.class, () -> ea.ops().add("z"));

        long s1 = SectionKeys.section(2, 0, 0);
        long s2 = SectionKeys.section(-1, 0, 0);
        pending.await(ea, new long[] {s1, s2, s1});
        pending.await(eb, new long[] {s2});
        assertArrayEquals(new long[] {s2, s1}, ea.missingSections(), "sorted, distinct");
        assertArrayEquals(new long[] {s2, s1}, pending.missingSections());
        assertThrows(IllegalArgumentException.class, () -> pending.await(ea, new long[0]));

        assertEquals(List.of(eb), pending.sectionsArrived(s -> s == s2));
        assertTrue(ea.isWaiting());
        assertFalse(eb.isWaiting());
        assertEquals(List.of(eb), pending.runnable());

        assertEquals(List.of(), pending.complete(eb));
        assertFalse(pending.isPending(b));
        assertThrows(IllegalArgumentException.class, () -> pending.complete(eb));
        assertEquals(1, pending.size());
    }
}
