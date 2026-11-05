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

    /** A sealed room this many blocks across and 3 high at 1 atm, as one hole opens to a vacuum. */
    private static FlowEdge breached(int side) {
        Sim sim = new Sim(Ambient.VACUUM);
        sim.settings = sim.settings.withAirflow(20);
        sim.world.room(1, 64, 1, side, 66, side);
        double moles = 41.57 * side * side * 3; // 1 atm in 1,000 L at 293.15 K is 41.57 mol.
        sim.add(1, 65, 1, moles * 0.79, moles * 0.21);
        sim.step();
        assertEquals(101.3, sim.zoneAt(1, 65, 1).pressure(), 0.5);
        sim.world.set(0, 65, 1, TestWorld.AIR);
        sim.step();
        List<FlowEdge> flows = sim.pipeline.flows();
        assertEquals(1, flows.size(), () -> "flows: " + flows);
        return flows.get(0);
    }

    @Test
    void client_010_the_flow_is_the_gas_the_zone_lost_per_second() {
        Sim sim = pressurisedRoom();
        sim.world.set(0, 65, 3, TestWorld.AIR);
        sim.step();
        double before = sim.zoneAt(3, 65, 3).totalMoles();
        sim.step();
        double after = sim.zoneAt(3, 65, 3).totalMoles();
        FlowEdge e = sim.pipeline.flows().get(0);
        assertEquals(
                before - after, e.molesPerSecond() * sim.settings.exchange().periodSeconds(), 1e-6);
        assertTrue(e.molesPerSecond() > 0);
    }

    /**
     * What one hole moves at 1 atm to a vacuum, which CLIENT-010's note quotes. A bigger room moves
     * more, but the volume-aware venting (CORE-035) levels it off near 27 mol/s.
     */
    @Test
    void client_010_a_big_room_moves_more_moles_than_a_closet_at_the_same_pressure() {
        assertEquals(11.6, breached(1).molesPerSecond(), 0.3);
        assertEquals(23.6, breached(3).molesPerSecond(), 0.3);
        assertEquals(27.0, breached(15).molesPerSecond(), 0.3);
    }

    @Test
    void client_010_a_field_shares_the_flow_evenly_among_openings() {
        Sim sim = pressurisedRoom();
        sim.world.set(0, 65, 3, TestWorld.AIR);
        sim.world.set(6, 65, 3, TestWorld.AIR);
        sim.step();
        FlowEdge e = sim.pipeline.flows().get(0);
        assertEquals(2, e.openings());
        List<FlowField.Opening> openings = FlowField.of(List.of(e)).openings();
        assertEquals(e.molesPerSecond() / 2, openings.get(0).molesPerSecond(), 1e-12);
        assertEquals(e.molesPerSecond() / 2, openings.get(1).molesPerSecond(), 1e-12);
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
