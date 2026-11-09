package io.github.west3436.zas.atmos.vent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import org.junit.jupiter.api.Test;

/**
 * A vent's moles per second: its bounds (CONT-014), its rate (CONT-078), and its share beside other
 * vents on one chamber (CONT-152).
 */
class VentFlowTest {
    private static final double R = GasMixture.GAS_CONSTANT;
    private static final double T = 293.15;
    /** The chamber of the airlock GameTests, L. */
    private static final double CHAMBER = 5_783.2;

    @Test
    void cont_078_a_vent_blows_at_most_half_its_buffer_per_second() {
        // A big network at high pressure into a big, low room: only the rate limits.
        double netVolume = 1_000 + 400 + 70 * 3;
        double netMoles = 3_000;
        double n = VentFlow.blow(50, 100_000, netMoles, netVolume, T, VentSettings.blow(101.325));
        assertEquals(0.5 * netMoles * 400 / netVolume, n, 1e-9);
    }

    @Test
    void cont_078_a_vent_siphons_at_most_half_a_buffer_of_the_room_per_second() {
        double zoneMoles = 5_000;
        double zoneVolume = 100_000;
        double n = VentFlow.siphon(zoneMoles, zoneVolume, T, 0, 470, T, VentSettings.siphon(0));
        assertEquals(0.5 * zoneMoles * 400 / zoneVolume, n, 1e-9);
    }

    @Test
    void cont_014_blowing_stops_at_the_external_bound() {
        double zoneVolume = 27_000;
        // Close to the bound, the vent moves just what reaches it.
        double n = VentFlow.blow(100.0, zoneVolume, 3_000, 1_470, T, VentSettings.blow(101.325));
        assertEquals(1.325 * zoneVolume / (R * T), n, 1e-9);
        assertEquals(0, VentFlow.blow(101.325, zoneVolume, 3_000, 1_470, T, VentSettings.blow(101.325)));
        assertEquals(0, VentFlow.blow(150, zoneVolume, 3_000, 1_470, T, VentSettings.blow(101.325)));
    }

    @Test
    void cont_014_blowing_stops_at_the_internal_bound() {
        double netVolume = 1_470;
        double netPressure = 200;
        double netMoles = netPressure * netVolume / (R * T);
        VentSettings s = new VentSettings(VentMode.BLOW, 5_000, 199);
        double n = VentFlow.blow(0, 1_000_000, netMoles, netVolume, T, s);
        assertEquals(1.0 * netVolume / (R * T), n, 1e-9);
        assertEquals(
                0, VentFlow.blow(0, 1_000_000, netMoles, netVolume, T, new VentSettings(VentMode.BLOW, 5_000, 200)));
    }

    @Test
    void cont_014_siphoning_stops_at_the_external_bound_and_the_internal_bound() {
        double zoneVolume = 27_000;
        double zoneMoles = 101.0 * zoneVolume / (R * T);
        double n = VentFlow.siphon(zoneMoles, zoneVolume, T, 0, 470, T, VentSettings.siphon(100.9));
        assertEquals(0.1 * zoneVolume / (R * T), n, 1e-6);
        assertEquals(0, VentFlow.siphon(zoneMoles, zoneVolume, T, 0, 470, T, VentSettings.siphon(101)));
        // A network already at the internal bound takes nothing.
        double full = 300 * 470 / (R * T);
        assertEquals(
                0, VentFlow.siphon(zoneMoles, zoneVolume, T, full, 470, T, new VentSettings(VentMode.SIPHON, 0, 300)));
    }

    @Test
    void cont_014_bounds_are_clamped_to_0_to_5066_kpa() {
        VentSettings s = new VentSettings(VentMode.BLOW, 9_999, -5);
        assertEquals(VentSettings.MAX_KPA, s.external());
        assertEquals(0, s.internal());
        assertEquals(0, new VentSettings(VentMode.BLOW, Double.NaN, 0).external());
        assertEquals(VentMode.OFF, new VentSettings(null, 1, 1).mode());
        assertTrue(VentSettings.siphon(50).internal() == VentSettings.MAX_KPA);
        assertEquals(VentMode.OFF, VentSettings.OFF.mode());
    }

    @Test
    void cont_014_an_idle_vent_says_why() {
        double min = 1e-4;
        double netVolume = 1_470;
        double zoneVolume = 27_000;
        double highNet = 3_000 * netVolume / (R * T); // moles at 3,000 kPa
        double room = 100 * zoneVolume / (R * T); // moles at 100 kPa

        // Siphoning into a network at 3,000 kPa with the internal bound at 400: the network.
        VentSettings siphon = new VentSettings(VentMode.SIPHON, 0, 400);
        assertEquals(
                VentStatus.NETWORK_BOUND, VentFlow.siphonIdle(room, zoneVolume, T, highNet, netVolume, T, siphon, min));
        // Raise the bound above the network, and it siphons.
        VentSettings higher = new VentSettings(VentMode.SIPHON, 0, 5_000);
        assertEquals(VentStatus.MOVING, VentFlow.siphonIdle(room, zoneVolume, T, highNet, netVolume, T, higher, min));
        // The room already at or below the external bound: the room.
        VentSettings floor = new VentSettings(VentMode.SIPHON, 150, 5_000);
        assertEquals(
                VentStatus.ROOM_BOUND, VentFlow.siphonIdle(room, zoneVolume, T, highNet, netVolume, T, floor, min));
        // Nothing in front: empty.
        assertEquals(VentStatus.ROOM_EMPTY, VentFlow.siphonIdle(0, zoneVolume, T, highNet, netVolume, T, higher, min));

        // Blowing into a room already at the external bound: the room.
        assertEquals(
                VentStatus.ROOM_BOUND,
                VentFlow.blowIdle(101.325, zoneVolume, highNet, netVolume, T, VentSettings.blow(101.325), min));
        // Blowing with the network at its internal bound: the network.
        VentSettings keep = new VentSettings(VentMode.BLOW, 5_000, 3_000);
        assertEquals(VentStatus.NETWORK_BOUND, VentFlow.blowIdle(100, zoneVolume, highNet, netVolume, T, keep, min));
        // Blowing from an empty network: empty.
        assertEquals(
                VentStatus.NETWORK_EMPTY,
                VentFlow.blowIdle(100, zoneVolume, 0, netVolume, T, VentSettings.blow(5_000), min));
        assertEquals(
                VentStatus.MOVING,
                VentFlow.blowIdle(100, zoneVolume, highNet, netVolume, T, VentSettings.blow(5_000), min));
    }

    @Test
    void cont_152_shares_follow_what_each_vent_could_move_and_add_up_to_1() {
        double[] shares = VentFlow.shares(new double[] {30, 10, 0, 60});
        assertEquals(0.3, shares[0], 1e-12);
        assertEquals(0.1, shares[1], 1e-12);
        assertEquals(0, shares[2], 1e-12);
        assertEquals(0.6, shares[3], 1e-12);
        assertEquals(1, VentFlow.shares(new double[] {7})[0], 1e-12);
        // With nothing any could move, they are equal.
        double[] none = VentFlow.shares(new double[] {0, 0, 0, 0});
        for (double s : none) {
            assertEquals(0.25, s, 1e-12);
        }
        // A share of 1 is the vent alone.
        double n = VentFlow.blow(50, 5_000, 100, 400, T, VentSettings.blow(101.325));
        assertEquals(n, VentFlow.blow(50, 5_000, 100, 400, T, VentSettings.blow(101.325), 1), 0);
    }

    /**
     * Steps 1, 2 and 4 vents, each on its own 400 L network, cycling a 5.8 m³ chamber from 450 kPa
     * down to 200 kPa and back up to 300 kPa, as an airlock controller runs them (CONT-139, CONT-152).
     * Each step every vent reads the same chamber and moves its share of what it needs. Together
     * they never pass the target, so neither way turns back; each moves no more than its own rate
     * (CONT-078); and they arrive within 5.07 kPa.
     */
    @Test
    void cont_152_vents_that_split_the_need_reach_the_target_without_passing_it() {
        for (int count : new int[] {1, 2, 4}) {
            double[] nets = new double[count];
            double zone = 450 * CHAMBER / (R * T);
            zone = splitCycle(count, nets, zone, 200);
            splitCycle(count, nets, zone, 300);
        }
    }

    /**
     * Without the split, two vents that each bound themselves by the target blow past it: cont_139's
     * last step back, before the split, from 87.6 kPa toward 95.6 kPa with 33.84 mol in each vent.
     */
    @Test
    void cont_152_two_vents_bound_on_their_own_pass_the_target() {
        double zone = 87.6 * CHAMBER / (R * T);
        double net = 33.84;
        VentSettings order = VentSettings.blow(95.6);
        double p = zone * R * T / CHAMBER;
        double each = VentFlow.blow(p, CHAMBER, net, 400, T, order);
        assertTrue((zone + 2 * each) * R * T / CHAMBER > 95.6 + 5.07, "two vents alone didn't pass the target");
        double[] shares = VentFlow.shares(
                new double[] {VentFlow.blowCapacity(net, 400, T, order), VentFlow.blowCapacity(net, 400, T, order)});
        double split = VentFlow.blow(p, CHAMBER, net, 400, T, order, shares[0])
                + VentFlow.blow(p, CHAMBER, net, 400, T, order, shares[1]);
        assertEquals(95.6, (zone + split) * R * T / CHAMBER, 1e-9);
    }

    /**
     * Runs one cycle of {@link #cont_152_vents_that_split_the_need_reach_the_target_without_passing_it}
     * toward {@code target}, kPa, to within 5.07 kPa, checking each step.
     *
     * @return the chamber's moles at the end
     */
    private static double splitCycle(int count, double[] nets, double zone, double target) {
        double rt = R * T;
        boolean down = zone * rt / CHAMBER > target;
        VentSettings order = down ? VentSettings.siphon(target) : VentSettings.blow(target);
        for (int step = 0; step < 200; step++) {
            double p = zone * rt / CHAMBER;
            if (Math.abs(p - target) <= 0.05 * 101.325) {
                return zone;
            }
            assertEquals(down, p > target, count + " vents turned back at " + p + " kPa toward " + target);
            double[] capacity = new double[count];
            for (int i = 0; i < count; i++) {
                capacity[i] = down
                        ? VentFlow.siphonCapacity(zone, CHAMBER, T, nets[i], 400, T, order)
                        : VentFlow.blowCapacity(nets[i], 400, T, order);
            }
            double[] shares = VentFlow.shares(capacity);
            double moved = 0;
            for (int i = 0; i < count; i++) {
                double n = down
                        ? VentFlow.siphon(zone, CHAMBER, T, nets[i], 400, T, order, shares[i])
                        : VentFlow.blow(p, CHAMBER, nets[i], 400, T, order, shares[i]);
                double rate = down ? VentFlow.RATE * zone * 400 / CHAMBER : VentFlow.RATE * nets[i];
                assertTrue(n <= rate + 1e-9, count + " vents: one moved " + n + " mol, over its rate " + rate);
                nets[i] += down ? n : -n;
                moved += n;
            }
            zone += down ? -moved : moved;
            double after = zone * rt / CHAMBER;
            assertTrue(
                    down ? after >= target - 1e-9 : after <= target + 1e-9,
                    count + " vents passed " + target + " kPa: " + p + " to " + after);
        }
        throw new AssertionError(count + " vents didn't reach " + target + " kPa in 200 steps");
    }
}
