package io.github.west3436.zas.atmos.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** How a central controller shares an atmos tick's FE (CONT-115, CONT-120, CONT-121). */
class CentralFeedTest {
    private static final long OWN = 100;

    private static CentralFeed.Zone zone(ZonePriority p, long shortfall, long room) {
        return new CentralFeed.Zone(p, shortfall, room);
    }

    @Test
    void cont_115_it_pays_its_own_draw_first_and_without_it_pays_nothing() {
        CentralFeed.Plan none = CentralFeed.plan(60, 30, OWN, List.of(zone(ZonePriority.CRITICAL, 50, 10_000)));
        assertFalse(none.powered(), "powered with 90 FE for a 100 FE draw");
        assertArrayEquals(new long[] {0}, none.paid(), "paid while unpowered");
        assertEquals(90, none.left(), "what it keeps while unpowered");

        CentralFeed.Plan own = CentralFeed.plan(60, 40, OWN, List.of());
        assertTrue(own.powered(), "unpowered with 100 FE for a 100 FE draw");
        assertEquals(0, own.left(), "what's left after its own draw");
    }

    @Test
    void cont_120_shortfalls_come_first_then_the_spare_intake_is_split_evenly() {
        // 1,000 stored and 1,400 taken in: own 100 and shortfalls 300 and 200 leave 800 spare intake.
        CentralFeed.Plan plan = CentralFeed.plan(
                1_000,
                1_400,
                OWN,
                List.of(zone(ZonePriority.NORMAL, 300, 10_000), zone(ZonePriority.NORMAL, 200, 10_000)));
        assertArrayEquals(new long[] {300, 200}, plan.paid(), "the shortfalls");
        assertArrayEquals(new long[] {400, 400}, plan.topUp(), "the spare intake, split evenly");
        assertEquals(1_000, plan.left(), "its own buffer, which the spare intake doesn't come from");
    }

    @Test
    void cont_120_only_the_ticks_intake_tops_up_and_never_past_a_buffers_room() {
        CentralFeed.Plan noIntake = CentralFeed.plan(50_000, 0, OWN, List.of(zone(ZonePriority.NORMAL, 0, 10_000)));
        assertArrayEquals(new long[] {0}, noIntake.topUp(), "the reserve topped up a buffer");
        assertEquals(50_000 - OWN, noIntake.left(), "the reserve");

        // 900 spare: the zone with 100 of room takes 100, and the other two share the rest.
        CentralFeed.Plan plan = CentralFeed.plan(
                0,
                1_000,
                OWN,
                List.of(
                        zone(ZonePriority.NORMAL, 0, 10_000),
                        zone(ZonePriority.LOW, 0, 100),
                        zone(ZonePriority.CRITICAL, 0, 10_000)));
        assertArrayEquals(new long[] {400, 100, 400}, plan.topUp(), "a share one can't hold passes on");
        assertEquals(0, plan.left(), "left");

        CentralFeed.Plan full = CentralFeed.plan(0, 1_000, OWN, List.of(zone(ZonePriority.NORMAL, 50, 250)));
        assertEquals(50, full.paid()[0], "the shortfall");
        assertEquals(200, full.topUp()[0], "the top-up, to the room after the shortfall");
        assertEquals(650, full.left(), "what doesn't fit stays");
    }

    @Test
    void cont_121_critical_is_paid_first_then_normal_then_low_and_claim_order_within_one() {
        // 650 after its own draw: Critical's 300, then the first Normal's 300, then 50 of the next.
        List<CentralFeed.Zone> zones = List.of(
                zone(ZonePriority.LOW, 300, 10_000),
                zone(ZonePriority.NORMAL, 300, 10_000),
                zone(ZonePriority.NORMAL, 300, 10_000),
                zone(ZonePriority.CRITICAL, 300, 10_000));
        CentralFeed.Plan plan = CentralFeed.plan(750, 0, OWN, zones);
        assertArrayEquals(new long[] {0, 300, 50, 300}, plan.paid(), "who is paid");
        assertEquals(0, plan.left(), "left");
    }

    @Test
    void cont_120_nothing_is_lost_and_nothing_made() {
        Random random = new Random(120);
        for (int run = 0; run < 2_000; run++) {
            long stored = random.nextInt(20_000);
            long intake = random.nextInt(20_000);
            List<CentralFeed.Zone> zones = new ArrayList<>();
            int n = random.nextInt(6);
            for (int i = 0; i < n; i++) {
                zones.add(zone(
                        ZonePriority.values()[random.nextInt(3)],
                        random.nextInt(3) == 0 ? 0 : random.nextInt(3_000),
                        random.nextInt(4) == 0 ? 0 : random.nextInt(10_000)));
            }
            CentralFeed.Plan plan = CentralFeed.plan(stored, intake, OWN, zones);
            long given = 0;
            long room = 0;
            long spent = plan.powered() ? OWN : 0;
            for (int i = 0; i < n; i++) {
                assertTrue(plan.paid()[i] >= 0 && plan.topUp()[i] >= 0, "a negative share");
                assertTrue(plan.given(i) <= Math.max(0, zones.get(i).room()), "past a buffer's room");
                assertTrue(plan.paid()[i] <= zones.get(i).shortfall(), "more than the shortfall");
                given += plan.given(i);
                room += zones.get(i).room() - plan.paid()[i];
                spent += plan.paid()[i];
            }
            assertEquals(stored + intake, plan.left() + given + (plan.powered() ? OWN : 0), "FE lost or made");
            assertTrue(plan.left() >= 0, "overdrawn");
            if (plan.powered()) {
                long spare = Math.max(0, intake - spent);
                long topped = given - (spent - OWN);
                assertEquals(Math.min(spare, room), topped, "the spare intake that should have been placed");
                long most = 0;
                long least = Long.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (zones.get(i).room() - plan.given(i) > 0) { // not capped by its room
                        most = Math.max(most, plan.topUp()[i]);
                        least = Math.min(least, plan.topUp()[i]);
                    }
                }
                assertTrue(
                        least == Long.MAX_VALUE || most - least <= 1, "the top-up isn't even: " + least + ", " + most);
            }
        }
    }
}
