package io.github.west3436.zas.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When gas catching fire plays its whoomp, and how loud (FIRE-036). */
class IgnitionWhoompsTest {
    private static final double EPSILON = 1e-5;

    /** A fire relighting every second plays one whoomp each 5 s. */
    @Test
    void fire_036_a_fire_that_keeps_relighting_does_not_machine_gun() {
        IgnitionWhoomps whoomps = new IgnitionWhoomps();
        int played = 0;
        for (long tick = 0; tick < 400; tick += 20) {
            if (whoomps.allow(0, 64, 0, tick)) {
                played++;
            }
        }
        assertEquals(4, played, "at ticks 0, 100, 200 and 300");
    }

    /** The quiet ends exactly {@value IgnitionWhoomps#QUIET_TICKS} ticks after a whoomp. */
    @Test
    void fire_036_the_quiet_ends_after_5_seconds() {
        IgnitionWhoomps whoomps = new IgnitionWhoomps();
        assertTrue(whoomps.allow(0, 64, 0, 1000));
        assertFalse(whoomps.allow(0, 64, 0, 1000 + IgnitionWhoomps.QUIET_TICKS - 1));
        assertTrue(whoomps.allow(0, 64, 0, 1000 + IgnitionWhoomps.QUIET_TICKS));
    }

    /** A refused whoomp doesn't start the quiet again, so a steady relight still plays on time. */
    @Test
    void fire_036_a_refused_whoomp_does_not_extend_the_quiet() {
        IgnitionWhoomps whoomps = new IgnitionWhoomps();
        assertTrue(whoomps.allow(0, 64, 0, 0));
        assertFalse(whoomps.allow(0, 64, 0, 99));
        assertTrue(whoomps.allow(0, 64, 0, 100));
    }

    /** The limit is by area: within 16 blocks counts, whichever cell or chunk it falls in, and beyond doesn't. */
    @Test
    void fire_036_the_limit_is_by_area() {
        IgnitionWhoomps whoomps = new IgnitionWhoomps();
        assertTrue(whoomps.allow(0, 64, 0, 0));
        assertFalse(whoomps.allow(IgnitionWhoomps.RADIUS, 64, 0, 1), "at the radius, over a chunk border");
        assertFalse(whoomps.allow(-1, 64, 0, 1), "next door, over a chunk border");
        assertTrue(whoomps.allow(IgnitionWhoomps.RADIUS + 1, 64, 0, 1), "just past the radius");
        assertTrue(whoomps.allow(0, 64, 100, 1), "a fire elsewhere has its own");
    }

    /** Each whoomp is checked against all the recent ones, not only the last. */
    @Test
    void fire_036_every_recent_whoomp_counts() {
        IgnitionWhoomps whoomps = new IgnitionWhoomps();
        assertTrue(whoomps.allow(0, 64, 0, 0));
        assertTrue(whoomps.allow(100, 64, 0, 1));
        assertFalse(whoomps.allow(0, 64, 1, 2), "beside the older one");
        assertFalse(whoomps.allow(100, 64, 1, 2), "beside the newer one");
    }

    /** The volume climbs with the log of the fuel, between its limits. */
    @Test
    void fire_036_a_whoomp_scales_with_the_fuel() {
        assertEquals(0.4f, IgnitionWhoomps.volume(0), EPSILON);
        assertEquals(0.4f, IgnitionWhoomps.volume(1), EPSILON);
        assertEquals(1.0f, IgnitionWhoomps.volume(10), EPSILON);
        assertEquals(1.6f, IgnitionWhoomps.volume(100), EPSILON);
        assertEquals(2.2f, IgnitionWhoomps.volume(1000), EPSILON);
        assertEquals(3.0f, IgnitionWhoomps.volume(1e6), EPSILON);
    }
}
