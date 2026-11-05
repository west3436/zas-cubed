package io.github.west3436.zas.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** How the gas fire blocks around a listener become a few loops (FIRE-036). */
class FireLoopGroupsTest {
    private static final double EPSILON = 1e-6;

    /** A box of fire blocks, in this round. */
    private static void box(FireLoopGroups groups, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    groups.add(x, y, z);
                }
            }
        }
    }

    /** One fire block is one source, at the block's middle. */
    @Test
    void fire_036_one_block_is_one_source_at_its_middle() {
        FireLoopGroups groups = new FireLoopGroups();
        groups.add(2, 3, 4);
        List<FireLoopGroups.Group> chosen = groups.choose(2.5, 3.5, 8.5);
        assertEquals(1, chosen.size());
        FireLoopGroups.Group g = chosen.get(0);
        assertEquals(2.5, g.x(), EPSILON);
        assertEquals(3.5, g.y(), EPSILON);
        assertEquals(4.5, g.z(), EPSILON);
        assertEquals(1, g.blocks());
        assertEquals(FireLoopGroups.SMALLEST_VOLUME, g.volume(), EPSILON);
    }

    /** A cube's blocks are one source, at their middle, not at the cube's. */
    @Test
    void fire_036_blocks_in_a_cube_are_one_source_at_their_middle() {
        FireLoopGroups groups = new FireLoopGroups();
        groups.add(0, 0, 0);
        groups.add(2, 0, 0);
        List<FireLoopGroups.Group> chosen = groups.choose(0, 0, 0);
        assertEquals(1, chosen.size());
        assertEquals(1.5, chosen.get(0).x(), EPSILON);
        assertEquals(2, chosen.get(0).blocks());
    }

    /** A burning room far bigger than the loops takes only {@value FireLoopGroups#MAX_SOURCES} of them. */
    @Test
    void fire_036_a_big_fire_uses_few_loops() {
        FireLoopGroups groups = new FireLoopGroups();
        box(groups, -24, 0, -24, 24, 7, 24);
        List<FireLoopGroups.Group> chosen = groups.choose(0.5, 3.5, 0.5);
        assertEquals(FireLoopGroups.MAX_SOURCES, chosen.size());
        assertTrue(FireLoopGroups.MAX_SOURCES < LoopBook.MAX_VOICES);
        assertEquals(
                3,
                new HashSet<>(chosen.stream().map(FireLoopGroups.Group::source).toList()).size());
    }

    /** Louder first, by how much reaches the listener: a big fire further off can beat one block nearby. */
    @Test
    void fire_036_the_loudest_at_the_listener_are_chosen() {
        FireLoopGroups groups = new FireLoopGroups();
        groups.add(0, 0, 0); // volume 0.4, 2 blocks away
        box(groups, 8, 0, 0, 11, 3, 3); // volume 1 (64 blocks), about 8 blocks away
        List<FireLoopGroups.Group> chosen = groups.choose(0, 0, 2);
        assertEquals(2, chosen.size());
        assertEquals(64, chosen.get(0).blocks());
        assertEquals(1, chosen.get(1).blocks());
    }

    /** A source out of range isn't chosen, even if nothing else is. */
    @Test
    void fire_036_out_of_range_is_silent() {
        FireLoopGroups groups = new FireLoopGroups();
        groups.add(100, 0, 0);
        assertTrue(groups.choose(0, 0, 0).isEmpty());
    }

    /** Nothing is kept between rounds, so a fire gone from one round is gone from the sound. */
    @Test
    void fire_036_a_fire_gone_from_a_round_asks_for_nothing() {
        FireLoopGroups groups = new FireLoopGroups();
        groups.add(0, 0, 0);
        assertEquals(1, groups.choose(0, 0, 0).size());
        assertTrue(groups.choose(0, 0, 0).isEmpty());
    }

    /** A source keeps its name while its fire grows and moves within the cube, so its loop goes on. */
    @Test
    void fire_036_a_growing_fire_keeps_its_source() {
        FireLoopGroups groups = new FireLoopGroups();
        groups.add(1, 1, 1);
        FireLoopGroups.Group small = groups.choose(0, 0, 0).get(0);
        box(groups, 1, 1, 1, 4, 4, 4);
        FireLoopGroups.Group big = groups.choose(0, 0, 0).get(0);
        assertEquals(small.source(), big.source());
        assertEquals(small.pitch(), big.pitch());
        assertTrue(big.volume() > small.volume(), "louder as it grows");
        assertTrue(big.x() > small.x(), "at the middle of its blocks");
    }

    /** Negative coordinates fall in the right cubes, with no double-width cube around 0. */
    @Test
    void fire_036_cubes_are_even_across_zero() {
        FireLoopGroups groups = new FireLoopGroups();
        groups.add(-1, 0, 0);
        groups.add(0, 0, 0);
        assertEquals(2, groups.choose(0, 0, 0).size());
        groups.add(-8, 0, 0);
        groups.add(-1, 0, 0);
        assertEquals(1, groups.choose(-4, 0, 0).size());
    }

    /** A playing source isn't swapped for an equal one a little nearer, but is for one much louder. */
    @Test
    void fire_036_a_playing_source_stays_until_another_is_clearly_louder() {
        FireLoopGroups groups = new FireLoopGroups();
        // Four sources of one block in a row along x, 8 blocks apart.
        int[] xs = {0, 8, 16, 24};
        for (int x : xs) {
            groups.add(x, 0, 0);
        }
        Set<FireLoopGroups.Source> first = sources(groups.choose(8.5, 0.5, 0.5));
        assertTrue(first.contains(new FireLoopGroups.Source(0, 0, 0)), "the first three: " + first);
        assertTrue(first.contains(new FireLoopGroups.Source(2, 0, 0)), "the first three: " + first);
        // Walking towards the fourth: at 12.6 it is a little nearer than the first, but not by enough.
        for (int x : xs) {
            groups.add(x, 0, 0);
        }
        assertEquals(first, sources(groups.choose(12.6, 0.5, 0.5)));
        // Close to the fourth, and far from the first, it takes the place.
        for (int x : xs) {
            groups.add(x, 0, 0);
        }
        Set<FireLoopGroups.Source> later = sources(groups.choose(20.5, 0.5, 0.5));
        assertTrue(later.contains(new FireLoopGroups.Source(3, 0, 0)), "after the walk: " + later);
    }

    private static Set<FireLoopGroups.Source> sources(List<FireLoopGroups.Group> groups) {
        Set<FireLoopGroups.Source> out = new HashSet<>();
        groups.forEach(g -> out.add(g.source()));
        return out;
    }

    /** The volume climbs by the same step each time the blocks double, and stops at 1. */
    @Test
    void fire_036_the_volume_rises_with_the_fire_up_to_a_limit() {
        assertEquals(0.4, FireLoopGroups.volume(1), EPSILON);
        assertEquals(0.5, FireLoopGroups.volume(2), EPSILON);
        assertEquals(0.6, FireLoopGroups.volume(4), EPSILON);
        assertEquals(0.7, FireLoopGroups.volume(8), EPSILON);
        assertEquals(1.0, FireLoopGroups.volume(64), EPSILON);
        assertEquals(1.0, FireLoopGroups.volume(512), EPSILON);
    }

    /** Neighbouring cubes get different pitches, so loops started together don't play in step. */
    @Test
    void fire_036_neighbouring_cubes_differ_in_pitch() {
        FireLoopGroups.Source s = new FireLoopGroups.Source(3, -2, 7);
        float pitch = FireLoopGroups.pitch(s);
        assertTrue(pitch >= 0.94f - EPSILON && pitch <= 1.06f + EPSILON, "pitch " + pitch);
        assertNotEquals(pitch, FireLoopGroups.pitch(new FireLoopGroups.Source(4, -2, 7)));
        assertNotEquals(pitch, FireLoopGroups.pitch(new FireLoopGroups.Source(3, -1, 7)));
        assertNotEquals(pitch, FireLoopGroups.pitch(new FireLoopGroups.Source(3, -2, 8)));
        assertEquals(pitch, FireLoopGroups.pitch(new FireLoopGroups.Source(3, -2, 7)), "fixed by its cube");
    }

    /** Each scan renews a loop's lease before it lapses, so a steady fire's loops don't dip between them. */
    @Test
    void fire_036_a_steady_fires_loops_hold_between_scans() {
        FireLoopGroups groups = new FireLoopGroups();
        LoopBook<String> book = new LoopBook<>();
        double[] gains = new double[FireLoopGroups.MAX_SOURCES];
        for (int tick = 0; tick < 100; tick++) {
            if (tick % FireLoopGroups.SCAN_TICKS == 0) {
                box(groups, 0, 0, 0, 23, 7, 23);
                for (FireLoopGroups.Group g : groups.choose(12, 4, 12)) {
                    book.request(g.source(), "fire", g.x(), g.y(), g.z(), g.volume(), g.pitch(), FireLoopGroups.RANGE);
                }
            }
            book.tick(12, 4, 12, 1, (x, y, z) -> true);
            int i = 0;
            for (LoopBook.Voice<String> voice : book.voices()) {
                if (tick > LoopBook.FADE_TICKS + 1) {
                    assertEquals(gains[i], voice.gain(), 1e-9, "voice " + i + " at tick " + tick);
                }
                gains[i++] = voice.gain();
            }
        }
    }

    /** Fed to the loop book, a fire's loops play while it burns and all stop once it has gone. */
    @Test
    void fire_036_the_loops_stop_when_the_fire_goes_out() {
        FireLoopGroups groups = new FireLoopGroups();
        LoopBook<String> book = new LoopBook<>();
        for (int tick = 0; tick < 200; tick++) {
            if (tick % FireLoopGroups.SCAN_TICKS == 0 && tick < 100) {
                box(groups, 0, 0, 0, 23, 7, 23);
                for (FireLoopGroups.Group g : groups.choose(12, 4, 12)) {
                    book.request(g.source(), "fire", g.x(), g.y(), g.z(), g.volume(), g.pitch(), FireLoopGroups.RANGE);
                }
            }
            book.tick(12, 4, 12, 1, (x, y, z) -> true);
            if (tick >= 20 && tick < 100) {
                assertEquals(FireLoopGroups.MAX_SOURCES, book.voices().size(), "voices at tick " + tick);
            }
        }
        assertEquals(0, book.voices().size(), "voices after it went out");
        assertEquals(0, book.leaseCount(), "leases after it went out");
    }
}
