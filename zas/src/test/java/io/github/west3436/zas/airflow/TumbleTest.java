package io.github.west3436.zas.airflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/** How a flung body turns as its client draws it, and how the client hears of it (CLIENT-027). */
class TumbleTest {
    private static final double EPS = 1e-9;

    /** Ticks a body moving east at this speed, flung or not, facing south. */
    private static boolean east(Tumble t, boolean flung, double speed) {
        return t.tick(flung, speed, 0, 0, 0, 1);
    }

    @Test
    void client_027_it_turns_faster_as_it_moves_faster_within_bounds() {
        assertEquals(Tumble.MIN_RATE, Tumble.rate(0), EPS);
        assertEquals(0.4 * Tumble.DEGREES_PER_BLOCK, Tumble.rate(0.4), EPS);
        assertTrue(Tumble.rate(0.6) > Tumble.rate(0.4));
        assertEquals(Tumble.MAX_RATE, Tumble.rate(5), EPS);

        Tumble slow = new Tumble();
        Tumble fast = new Tumble();
        for (int i = 0; i < 4; i++) {
            east(slow, true, 0.3);
            east(fast, true, 0.6);
        }
        assertTrue(fast.angle(1) > slow.angle(1), "the faster body didn't turn more");
    }

    @Test
    void client_027_it_turns_about_the_level_axis_across_its_motion() {
        Tumble t = new Tumble();
        east(t, true, 0.5);
        // Moving east (+x), the axis is (0, 0, -1): a positive turn about it tips the top east.
        assertEquals(0, t.axisX(), EPS);
        assertEquals(-1, t.axisZ(), EPS);

        Tumble north = new Tumble();
        north.tick(true, 0, 0.3, -0.5, 0, 1);
        assertEquals(-1, north.axisX(), EPS);
        assertEquals(0, north.axisZ(), EPS);
    }

    @Test
    void client_027_a_body_lifted_straight_up_turns_across_its_facing() {
        Tumble t = new Tumble();
        t.tick(true, 0, 0.4, 0, 1, 0);
        // Facing east, it turns as if it moved east.
        assertEquals(0, t.axisX(), EPS);
        assertEquals(-1, t.axisZ(), EPS);
        assertTrue(t.angle(1) > 0);
    }

    @Test
    void client_027_a_change_of_course_eases_the_axis_round() {
        Tumble t = new Tumble();
        east(t, true, 0.5);
        t.tick(true, 0, 0, 0.5, 0, 1);
        assertTrue(t.axisX() > 0 && t.axisZ() < 0, "the axis jumped: " + t.axisX() + ", " + t.axisZ());
        assertEquals(1, Math.hypot(t.axisX(), t.axisZ()), 1e-6);
        for (int i = 0; i < 40; i++) {
            t.tick(true, 0, 0, 0.5, 0, 1);
        }
        assertEquals(1, t.axisX(), 1e-3);
        assertEquals(0, t.axisZ(), 1e-3);
    }

    @Test
    void client_027_the_angle_is_drawn_between_ticks_and_wraps_without_a_jump() {
        Tumble t = new Tumble();
        double last = 0;
        for (int i = 0; i < 40; i++) {
            east(t, true, 2);
            double from = t.angle(0);
            double to = t.angle(1);
            assertEquals(Tumble.MAX_RATE, to - from, EPS, "a step that wasn't one tick's turn");
            assertEquals(from + (to - from) / 2, t.angle(0.5f), EPS);
            assertEquals(last % 360, from % 360, EPS, "the angle jumped between ticks");
            assertTrue(from >= -Tumble.MAX_RATE && to < 360 + Tumble.MAX_RATE, "it didn't wrap: " + to);
            last = to;
        }
    }

    @Test
    void client_027_it_settles_upright_by_the_shorter_way_once_it_lands() {
        Tumble back = new Tumble();
        for (int i = 0; i < 3; i++) {
            east(back, true, 0.6);
        }
        assertTrue(back.angle(1) > 0 && back.angle(1) < 180, "the test wants it under 180: " + back.angle(1));
        assertTrue(east(back, false, 0));
        assertTrue(back.angle(1) < back.angle(0), "under 180, it should turn back");
        int ticks = 1;
        while (east(back, false, 0)) {
            ticks++;
            assertTrue(ticks < 10, "it never settled");
        }
        assertEquals(0, back.angle(0), EPS);
        assertEquals(0, back.angle(1), EPS);

        Tumble on = new Tumble();
        for (int i = 0; i < 6; i++) {
            east(on, true, 1);
        }
        assertTrue(on.angle(1) > 180, "the test wants it over 180: " + on.angle(1));
        double before = on.angle(1);
        assertTrue(east(on, false, 0));
        assertTrue(on.angle(1) > before, "over 180, it should turn on");
        ticks = 1;
        while (east(on, false, 0)) {
            ticks++;
            assertTrue(ticks <= 180 / Tumble.SETTLE_RATE + 2, "it took too long to settle");
        }
        assertEquals(0, on.angle(1), EPS);
    }

    @Test
    void client_027_an_upright_body_that_isn_t_flung_needs_nothing() {
        assertFalse(east(new Tumble(), false, 0.5));
    }

    @Test
    void client_027_the_client_hears_when_a_tumble_starts_and_ends() {
        ByteBuf buf = Unpooled.buffer();
        TumblePayload.STREAM_CODEC.encode(buf, new TumblePayload(70_000, true));
        TumblePayload start = TumblePayload.STREAM_CODEC.decode(buf);
        assertEquals(new TumblePayload(70_000, true), start);
        assertEquals(0, buf.readableBytes());

        FlungBodies held = new FlungBodies();
        held.accept(start);
        assertTrue(held.flung(70_000));
        assertFalse(held.flung(70_001));
        held.accept(new TumblePayload(70_000, false));
        assertFalse(held.flung(70_000));

        held.accept(start);
        held.clear();
        assertFalse(held.flung(70_000));
    }
}
