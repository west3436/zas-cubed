package io.github.west3436.zas.airflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The airflow sound's curves, grouping and cap (CLIENT-010); how it sounds is the owner's check. */
class AirflowSoundPlanTest {
    private static final int WEST = 4;

    private static VisibleFlow flow(int kpa, double moles, VisibleOpening... openings) {
        return new VisibleFlow(kpa, VisibleFlow.step(moles), List.of(openings));
    }

    private static VisibleOpening at(int x, int y, int z) {
        return new VisibleOpening(x, y, z, WEST);
    }

    @Test
    void client_010_no_flow_is_silence_and_more_flow_is_louder_up_to_a_cap() {
        assertEquals(0, AirflowSoundPlan.volume(0));
        double[] moles = {2, 5, 12, 24, 27, 60};
        for (int i = 1; i < moles.length; i++) {
            assertTrue(AirflowSoundPlan.volume(moles[i]) > AirflowSoundPlan.volume(moles[i - 1]), "at " + moles[i]);
        }
        assertEquals(1, AirflowSoundPlan.volume(64));
        assertEquals(1, AirflowSoundPlan.volume(100_000));
    }

    @Test
    void client_010_a_big_room_is_louder_than_a_closet_at_the_same_pressure() {
        // FlowExportTest's measurements at 1 atm through one hole, to a vacuum.
        float cupboard = AirflowSoundPlan.volume(11.6);
        float closet = AirflowSoundPlan.volume(23.6);
        float hall = AirflowSoundPlan.volume(27.0);
        assertTrue(cupboard < closet && closet < hall, cupboard + ", " + closet + ", " + hall);
    }

    @Test
    void client_010_pitch_rises_with_the_difference_within_limits() {
        assertEquals(1, AirflowSoundPlan.pitch(101.325), 1e-6);
        assertEquals(0.75, AirflowSoundPlan.pitch(20), 0.01);
        assertTrue(AirflowSoundPlan.pitch(500) > AirflowSoundPlan.pitch(200));
        assertEquals(1.5, AirflowSoundPlan.pitch(1_000_000), 1e-6);
        assertEquals(0.7, AirflowSoundPlan.pitch(0.5), 1e-6);
    }

    @Test
    void client_010_pitch_follows_the_difference_and_volume_the_flow_alone() {
        // Same difference, different flow: the same pitch, a different volume.
        List<AirflowSoundPlan.Source> a = AirflowSoundPlan.plan(List.of(flow(101, 10, at(0, 64, 0))), 0, 64, 0);
        List<AirflowSoundPlan.Source> b = AirflowSoundPlan.plan(List.of(flow(101, 40, at(0, 64, 0))), 0, 64, 0);
        assertEquals(a.get(0).pitch(), b.get(0).pitch(), 1e-6);
        assertTrue(b.get(0).volume() > a.get(0).volume());
        // Same flow, different difference: the other way round.
        List<AirflowSoundPlan.Source> c = AirflowSoundPlan.plan(List.of(flow(300, 10, at(0, 64, 0))), 0, 64, 0);
        assertEquals(a.get(0).volume(), c.get(0).volume(), 1e-6);
        assertTrue(c.get(0).pitch() > a.get(0).pitch());
    }

    @Test
    void client_010_many_edges_at_one_breach_are_one_source_that_adds_up() {
        List<VisibleFlow> flows = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            flows.add(flow(90 + i, 5, at(1, 65 + i % 2, 1 + i)));
        }
        List<AirflowSoundPlan.Source> sources = AirflowSoundPlan.plan(flows, 4, 65, 3);
        assertEquals(1, sources.size(), () -> "sources: " + sources);
        AirflowSoundPlan.Source s = sources.get(0);
        assertEquals(30, s.molesPerSecond(), 3);
        // The middle of the flow: the openings' faces at x = 1, z from 1.5 to 6.5.
        assertEquals(1, s.x(), 1e-6);
        assertEquals(4, s.z(), 1e-6);
        assertEquals(92.5, s.kpa(), 0.1);
    }

    @Test
    void client_010_it_is_never_a_roar_however_many_edges_add_up() {
        List<VisibleFlow> flows = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            flows.add(flow(500, 500, at(1, 65, 1 + i % 6)));
        }
        List<AirflowSoundPlan.Source> sources = AirflowSoundPlan.plan(flows, 4, 65, 3);
        assertEquals(1, sources.size());
        assertEquals(1, sources.get(0).volume());
    }

    @Test
    void client_010_a_source_keeps_its_key_as_the_listener_moves_and_the_flow_changes() {
        List<VisibleFlow> flows = List.of(flow(101, 27, at(1, 65, 1)));
        long key = AirflowSoundPlan.plan(flows, 4, 65, 3).get(0).key();
        assertEquals(key, AirflowSoundPlan.plan(flows, -8, 60, 6).get(0).key());
        assertEquals(
                key,
                AirflowSoundPlan.plan(List.of(flow(50, 12, at(1, 65, 1))), 4, 65, 3)
                        .get(0)
                        .key());
    }

    @Test
    void client_010_heard_within_16_blocks_and_not_beyond() {
        List<VisibleFlow> flows = List.of(flow(101, 27, at(0, 64, 0)));
        // The face is at x = 0, y = 64.5, z = 0.5.
        assertEquals(1, AirflowSoundPlan.plan(flows, 15.5, 64.5, 0.5).size());
        assertTrue(AirflowSoundPlan.plan(flows, 16.5, 64.5, 0.5).isEmpty());
    }

    @Test
    void client_010_a_flow_of_nothing_makes_no_source() {
        assertTrue(AirflowSoundPlan.plan(List.of(flow(101, 0, at(0, 64, 0))), 0, 64, 0)
                .isEmpty());
        assertTrue(AirflowSoundPlan.plan(List.of(), 0, 64, 0).isEmpty());
    }

    @Test
    void client_010_a_wide_breach_is_at_most_two_sources_the_loudest_where_the_listener_is() {
        // Openings in a line along z, a cell apart, each as strong: the listener is near z = 40.
        List<VisibleFlow> flows = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            flows.add(flow(101, 27, at(0, 64, i * AirflowSoundPlan.CELL)));
        }
        List<AirflowSoundPlan.Source> sources = AirflowSoundPlan.plan(flows, 2, 64, 40);
        assertEquals(AirflowSoundPlan.MAX_SOURCES, sources.size());
        for (AirflowSoundPlan.Source s : sources) {
            assertTrue(Math.abs(s.z() - 40.5) <= AirflowSoundPlan.CELL + 0.5, "a far source: " + s);
        }
        assertEquals(40.5, sources.get(0).z(), 1e-6);
    }

    @Test
    void client_010_a_big_breach_a_listener_is_far_from_can_lose_to_a_small_one_beside_it() {
        // Loudness at the listener decides, not the source's own: 27 mol/s 15 blocks away against
        // 12 mol/s 1 block away.
        List<VisibleFlow> flows =
                List.of(flow(101, 27, at(16, 64, 0)), flow(101, 12, at(0, 64, 0)), flow(101, 12, at(0, 64, 8)));
        List<AirflowSoundPlan.Source> sources = AirflowSoundPlan.plan(flows, 1, 64.5, 0.5);
        assertEquals(2, sources.size());
        assertEquals(0, sources.get(0).x(), 1e-6);
        assertEquals(0.5, sources.get(0).z(), 1e-6);
        assertEquals(8.5, sources.get(1).z(), 1e-6);
    }
}
