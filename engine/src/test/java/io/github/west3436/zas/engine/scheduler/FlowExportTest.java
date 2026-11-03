package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.flow.FlowEdge;
import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.engine.flow.FlowSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The flow edges an atmos tick exports for airflow (FLOW-001, FLOW-006). */
class FlowExportTest {

    /** A sealed 5 × 3 × 5 room near 1 atm in a vacuum, exporting edges at 20 kPa and up. */
    private static Sim pressurisedRoom() {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.settings = sim.settings.withAirflow(20);
        sim.world.room(1, 64, 1, 5, 66, 5);
        sim.add(3, 65, 3, 2400, 640);
        sim.step();
        assertNotNull(sim.zoneAt(3, 65, 3));
        assertTrue(sim.zoneAt(3, 65, 3).pressure() > 80, "the room isn't near 1 atm");
        return sim;
    }

    @Test
    void flow_001_a_sealed_room_exports_nothing() {
        Sim sim = pressurisedRoom();
        sim.step();
        assertEquals(List.of(), sim.pipeline.flows());
    }

    @Test
    void flow_001_a_breach_exports_its_edge_with_the_opening() {
        Sim sim = pressurisedRoom();
        sim.world.set(0, 65, 3, TestWorld.AIR);
        sim.step();
        List<FlowEdge> flows = sim.pipeline.flows();
        assertEquals(1, flows.size(), () -> "flows: " + flows);
        FlowEdge e = flows.get(0);
        assertTrue(e.isAmbient());
        assertEquals(sim.zoneAt(3, 65, 3).id(), e.zone());
        assertEquals(1, e.openings());
        assertEquals(io.github.west3436.zas.engine.classify.CellIds.of(1, 65, 3, 0), e.cell(0));
        assertEquals(io.github.west3436.zas.engine.classify.CellIds.of(0, 65, 3, 0), e.otherCell(0));
        assertEquals(sim.pipeline.published().tick(), sim.pipeline.flowsTick());

        // An item in the room is carried towards the hole, along -x.
        FlowField field = FlowField.of(flows);
        double[] push = field.push(3.5, 65.5, 3.5, 0, 0, 0, e.zone(), 0, FlowSettings.DEFAULT);
        assertNotNull(push);
        assertTrue(push[0] < 0, "not towards the hole");
        assertEquals(0, push[1], 1e-9);
        assertEquals(0, push[2], 1e-9);
    }

    @Test
    void flow_001_off_or_below_the_threshold_exports_nothing() {
        Sim sim = pressurisedRoom();
        sim.settings = sim.settings.withAirflow(Double.POSITIVE_INFINITY);
        sim.world.set(0, 65, 3, TestWorld.AIR);
        sim.step();
        assertEquals(List.of(), sim.pipeline.flows());

        sim.settings = sim.settings.withAirflow(1_000);
        sim.step();
        assertEquals(List.of(), sim.pipeline.flows());
    }

    @Test
    void flow_006_openings_follow_a_change_of_topology() {
        Sim sim = pressurisedRoom();
        sim.world.set(0, 65, 3, TestWorld.AIR);
        sim.step();
        assertEquals(1, sim.pipeline.flows().get(0).openings());

        // A second hole: the cached openings are searched again.
        sim.add(3, 65, 3, 2400, 640);
        sim.world.set(6, 65, 3, TestWorld.AIR);
        sim.step();
        assertEquals(2, sim.pipeline.flows().get(0).openings());

        // Both sealed: the edge goes, and with it the flow.
        sim.world.set(0, 65, 3, TestWorld.SOLID);
        sim.world.set(6, 65, 3, TestWorld.SOLID);
        sim.step();
        assertEquals(List.of(), sim.pipeline.flows());
    }
}
