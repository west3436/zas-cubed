package io.github.west3436.zas.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.sound.LoopBook.Voice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Which loops a client plays, how they fade and, above all, that none is ever left playing
 * (CLIENT-015, CLIENT-016). A stuck loop is the worst bug the sounds could ship, so most of this is
 * the bookkeeping.
 */
class LoopBookTest {
    private static final double RANGE = 8;

    /** How many ticks a forgotten loop can last: its lease, then its fade, then its release. */
    private static final int FORGOTTEN_TICKS = LoopBook.LEASE_TICKS + LoopBook.FADE_TICKS + 2;

    private final LoopBook<String> book = new LoopBook<>();
    private double listenerX;
    private double air = 1;
    private LoopBook.Loaded loaded = (x, y, z) -> true;

    /** Requests a loop from {@code source}, at x = {@code x}, with a sound and pitch of its own. */
    private void want(int source, double x) {
        want(source, x, 1);
    }

    private void want(int source, double x, double volume) {
        book.request(source, "hum", x, 0, 0, volume, 1, RANGE);
    }

    private void tick() {
        book.tick(listenerX, 0, 0, air, loaded);
    }

    /** Runs {@code ticks} ticks, renewing the given sources at their x before each. */
    private void run(int ticks, Map<Integer, Double> sources) {
        for (int i = 0; i < ticks; i++) {
            sources.forEach((source, x) -> want(source, x));
            tick();
            checkInvariants();
        }
    }

    private void checkInvariants() {
        assertTrue(
                book.voices().size() <= LoopBook.MAX_VOICES,
                () -> "voices: " + book.voices().size());
        for (Voice<String> v : book.voices()) {
            assertTrue(v.alive(), "a released voice in the book");
            assertTrue(v.gain() >= 0 && v.gain() <= 1, () -> "gain: " + v.gain());
        }
    }

    private Set<Double> voiceXs() {
        Set<Double> xs = new HashSet<>();
        book.voices().forEach(v -> xs.add(v.x()));
        return xs;
    }

    private Voice<String> onlyVoice() {
        assertEquals(1, book.voices().size(), "voices");
        return book.voices().iterator().next();
    }

    /** {@code count} sources at x = 1, 2, 3 and so on, each the source numbered by its x. */
    private static Map<Integer, Double> line(int count, double spacing) {
        Map<Integer, Double> sources = new HashMap<>();
        for (int i = 1; i <= count; i++) {
            sources.put(i, i * spacing);
        }
        return sources;
    }

    // ---- Which loops play --------------------------------------------------------------------

    @Test
    void client_015_only_the_nearest_8_play() {
        run(LoopBook.FADE_TICKS + 2, line(12, 0.5));
        Set<Double> expected = new HashSet<>();
        for (int i = 1; i <= 8; i++) {
            expected.add(i * 0.5);
        }
        assertEquals(expected, voiceXs());
    }

    @Test
    void client_015_a_source_beyond_its_range_never_starts() {
        run(30, Map.of(1, RANGE, 2, RANGE + 0.01, 3, 100.0));
        assertTrue(book.voices().isEmpty(), "at or beyond the range there is nothing to hear");
    }

    @Test
    void client_015_each_source_has_its_own_range() {
        for (int i = 0; i < 5; i++) {
            book.request("device", "hum", 10, 0, 0, 1, 1, 8);
            book.request("breach", "hiss", 10, 0, 0, 1, 1, 16);
            tick();
        }
        assertEquals("hiss", onlyVoice().sound());
    }

    @Test
    void client_015_a_loud_source_isnt_crowded_out_by_quiet_ones_nearer() {
        Map<Integer, Double> quiet = line(8, 0.25);
        for (int i = 0; i < 5; i++) {
            quiet.forEach((source, x) -> want(source, x, 0.1));
            book.request("breach", "hiss", 6, 0, 0, 1, 1, 16);
            tick();
        }
        assertTrue(book.voices().stream().anyMatch(v -> v.sound().equals("hiss")), "the hiss isn't playing");
        assertEquals(LoopBook.MAX_VOICES, book.voices().size());
    }

    @Test
    void client_015_a_source_with_no_volume_plays_nothing() {
        for (int i = 0; i < 5; i++) {
            want(1, 1, 0);
            tick();
        }
        assertTrue(book.voices().isEmpty());
    }

    @Test
    void client_015_two_sounds_from_one_source_are_two_loops() {
        for (int i = 0; i < 5; i++) {
            book.request("device", "hum", 1, 0, 0, 1, 1, RANGE);
            book.request("device", "valve", 1, 0, 0, 1, 1, RANGE);
            tick();
        }
        assertEquals(2, book.voices().size());
    }

    @Test
    void client_015_a_nearer_source_takes_the_place_of_the_farthest() {
        Map<Integer, Double> eight = line(8, 0.5);
        run(LoopBook.FADE_TICKS + 2, eight);
        Map<Integer, Double> nine = new HashMap<>(eight);
        nine.put(9, 0.25);
        run(LoopBook.FADE_TICKS + 3, nine);
        Set<Double> xs = voiceXs();
        assertTrue(xs.contains(0.25), "the nearer source isn't playing: " + xs);
        assertFalse(xs.contains(4.0), "the farthest is still playing: " + xs);
        assertEquals(LoopBook.MAX_VOICES, xs.size());
    }

    @Test
    void client_015_a_swap_waits_for_the_old_voice_to_fade_and_never_goes_over_8() {
        Map<Integer, Double> eight = line(8, 0.5);
        run(LoopBook.FADE_TICKS + 2, eight);
        Map<Integer, Double> nine = new HashMap<>(eight);
        nine.put(9, 0.25);
        int newcomerStarted = -1;
        for (int t = 0; t < LoopBook.FADE_TICKS + 3; t++) {
            run(1, nine);
            if (newcomerStarted < 0 && voiceXs().contains(0.25)) {
                newcomerStarted = t;
            }
        }
        assertTrue(newcomerStarted >= LoopBook.FADE_TICKS - 1, "started at tick " + newcomerStarted);
    }

    @Test
    void client_015_loops_about_as_loud_dont_take_turns() {
        Map<Integer, Double> sources = new HashMap<>();
        for (int i = 0; i < 12; i++) {
            sources.put(i, 4.0);
        }
        run(LoopBook.FADE_TICKS + 2, sources);
        Set<Voice<String>> playing = new HashSet<>(book.voices());
        Random random = new Random(42);
        for (int t = 0; t < 200; t++) {
            // A listener shuffling on the spot, and sources that differ by a hair.
            listenerX = random.nextDouble() * 0.1;
            sources.replaceAll((source, x) -> 4.0 + random.nextDouble() * 0.05);
            run(1, sources);
            assertEquals(playing, new HashSet<>(book.voices()), "the voices changed at tick " + t);
        }
    }

    @Test
    void client_015_an_exact_tie_keeps_the_older_request() {
        run(3, Map.of(1, 4.0));
        Map<Integer, Double> tied = line(0, 0);
        for (int i = 1; i <= 9; i++) {
            tied.put(i, 4.0);
        }
        run(LoopBook.FADE_TICKS + 2, tied);
        assertEquals(LoopBook.MAX_VOICES, book.voices().size());
    }

    // ---- Fades -------------------------------------------------------------------------------

    @Test
    void client_015_a_start_fades_in_with_no_jump() {
        double last = 0;
        for (int t = 0; t < LoopBook.FADE_TICKS + 3; t++) {
            want(1, 0);
            tick();
            double gain = onlyVoice().gain();
            assertTrue(gain - last <= 1.0 / LoopBook.FADE_TICKS + 1e-9, "a jump to " + gain + " from " + last);
            assertTrue(gain >= last, "the fade went down");
            last = gain;
        }
        assertEquals(1, last, 1e-9);
    }

    @Test
    void client_015_the_first_tick_of_a_voice_is_silent() {
        want(1, 0);
        tick();
        assertEquals(0, onlyVoice().gain(), "a voice must start at silence");
    }

    @Test
    void client_015_a_quiet_loop_takes_as_long_to_fade_in_as_a_loud_one() {
        want(1, 0, 0.2);
        tick();
        for (int t = 0; t < LoopBook.FADE_TICKS - 1; t++) {
            want(1, 0, 0.2);
            tick();
        }
        assertTrue(
                onlyVoice().gain() < 0.2,
                "it reached its volume early: " + onlyVoice().gain());
        want(1, 0, 0.2);
        tick();
        assertEquals(0.2, onlyVoice().gain(), 1e-9);
    }

    @Test
    void client_015_a_stop_fades_out_with_no_jump_then_releases_the_voice() {
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 0.0));
        Voice<String> voice = onlyVoice();
        // The last request came before the last tick, so one of its lease's ticks has gone.
        for (int t = 0; t < LoopBook.LEASE_TICKS - 1; t++) {
            tick();
            assertEquals(1, voice.gain(), 1e-9, "a lease holds until it lapses");
        }
        double last = voice.gain();
        for (int t = 0; t < LoopBook.FADE_TICKS - 1; t++) {
            tick();
            assertTrue(voice.alive(), "released at fade tick " + t);
            assertTrue(last - voice.gain() <= 1.0 / LoopBook.FADE_TICKS + 1e-9, "a jump");
            assertTrue(voice.gain() < last, "the fade didn't fall");
            last = voice.gain();
        }
        tick();
        assertFalse(voice.alive(), "the voice outlived its fade");
        assertEquals(0, voice.gain());
        assertTrue(book.voices().isEmpty());
    }

    @Test
    void client_015_a_change_of_volume_fades() {
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 0.0));
        double last = onlyVoice().gain();
        for (int t = 0; t < LoopBook.FADE_TICKS + 2; t++) {
            want(1, 0, 0.1);
            tick();
            double gain = onlyVoice().gain();
            assertTrue(Math.abs(gain - last) <= 1.0 / LoopBook.FADE_TICKS + 1e-9, "a jump");
            last = gain;
        }
        assertEquals(0.1, last, 1e-9);
    }

    @Test
    void client_015_a_renewal_during_the_fade_revives_the_same_voice() {
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 0.0));
        Voice<String> voice = onlyVoice();
        for (int t = 0; t < LoopBook.LEASE_TICKS + 5; t++) {
            tick();
        }
        double faded = voice.gain();
        assertTrue(faded < 1 && voice.alive(), "should be part way down: " + faded);
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 0.0));
        assertTrue(voice.alive(), "the voice was replaced");
        assertEquals(voice, onlyVoice());
        assertEquals(1, voice.gain(), 1e-9);
    }

    @Test
    void client_015_gain_falls_with_distance_in_a_straight_line() {
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 4.0));
        assertEquals(0.5, onlyVoice().gain(), 1e-9);
        run(1, Map.of(1, 6.0));
        assertEquals(0.25, onlyVoice().gain(), 1e-9);
    }

    @Test
    void client_015_a_voice_follows_its_source_and_glides_to_a_new_pitch() {
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 2.0));
        book.request(1, "hum", 3, 1, 2, 1, 2, RANGE);
        tick();
        Voice<String> voice = onlyVoice();
        assertEquals(3, voice.x());
        assertEquals(1, voice.y());
        assertEquals(2, voice.z());
        assertEquals(1 + LoopBook.PITCH_STEP, voice.pitch(), 1e-9);
        for (int t = 0; t < 40; t++) {
            book.request(1, "hum", 3, 1, 2, 1, 2, RANGE);
            tick();
        }
        assertEquals(2, voice.pitch(), 1e-9);
    }

    // ---- No stuck loops ----------------------------------------------------------------------

    @Test
    void client_015_a_forgotten_loop_stops_with_nothing_left_behind() {
        run(LoopBook.FADE_TICKS + 2, line(8, 0.5));
        assertEquals(LoopBook.MAX_VOICES, book.voices().size());
        for (int t = 0; t < FORGOTTEN_TICKS; t++) {
            tick();
            checkInvariants();
        }
        assertTrue(book.voices().isEmpty(), "voices were left playing");
        assertEquals(0, book.leaseCount(), "leases were left behind");
    }

    @Test
    void client_015_a_lease_renewed_on_time_never_lapses() {
        Voice<String> voice = null;
        for (int t = 0; t < 500; t++) {
            if (t % LoopBook.LEASE_TICKS == 0) {
                want(1, 0);
            }
            tick();
            if (t > 0) {
                assertEquals(voice == null ? onlyVoice() : voice, onlyVoice(), "the voice changed at tick " + t);
            }
            voice = onlyVoice();
        }
        assertEquals(1, voice.gain(), 1e-9);
    }

    @Test
    void client_015_leaving_range_stops_it_even_while_its_caller_keeps_asking() {
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 0.0));
        Voice<String> voice = onlyVoice();
        double last = voice.gain();
        for (int t = 1; t <= 80; t++) {
            listenerX = t * 0.216; // walking pace: 4.3 blocks a second
            run(1, Map.of(1, 0.0));
            assertTrue(last - voice.gain() <= 0.05, "a jump at tick " + t + ": " + last + " to " + voice.gain());
            last = voice.gain();
        }
        assertFalse(voice.alive(), "still playing out of range");
        assertTrue(book.voices().isEmpty());
        assertEquals(0, voice.gain());
        listenerX = 0;
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 0.0));
        assertEquals(1, onlyVoice().gain(), 1e-9, "coming back in range starts it again");
    }

    @Test
    void client_015_a_chunk_that_leaves_stops_it_even_while_its_caller_keeps_asking() {
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 1.0));
        Voice<String> voice = onlyVoice();
        loaded = (x, y, z) -> false;
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 1.0));
        assertFalse(voice.alive(), "still playing with its chunk gone");
        assertTrue(book.voices().isEmpty());
        loaded = (x, y, z) -> true;
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 1.0));
        assertFalse(book.voices().isEmpty(), "the chunk's return starts it again");
    }

    @Test
    void client_015_clear_stops_everything_at_once() {
        run(LoopBook.FADE_TICKS + 2, line(8, 0.5));
        List<Voice<String>> voices = new ArrayList<>(book.voices());
        book.clear();
        assertTrue(book.voices().isEmpty());
        assertEquals(0, book.leaseCount());
        for (Voice<String> v : voices) {
            assertFalse(v.alive(), "a voice survived");
            assertEquals(0, v.gain());
        }
        tick();
        assertTrue(book.voices().isEmpty(), "a voice came back");
    }

    @Test
    void client_015_a_voice_released_is_never_used_again() {
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 0.0));
        Voice<String> first = onlyVoice();
        for (int t = 0; t < FORGOTTEN_TICKS; t++) {
            tick();
        }
        assertFalse(first.alive());
        run(1, Map.of(1, 0.0));
        run(1, Map.of(1, 0.0));
        assertFalse(first.alive(), "the old voice came back");
        assertTrue(onlyVoice() != first, "the old voice was reused");
    }

    /**
     * Many sources come and go at random while the listener wanders, the air changes and chunks leave.
     * At every tick nothing breaks the rules, and when everyone stops asking, everything stops.
     */
    @Test
    void client_015_the_book_stays_right_through_random_comings_and_goings() {
        for (long seed = 0; seed < 20; seed++) {
            Random random = new Random(seed);
            LoopBook<String> b = new LoopBook<>();
            Map<Integer, double[]> sources = new HashMap<>();
            Map<Voice<String>, Double> lastFade = new HashMap<>();
            List<Voice<String>> everSeen = new ArrayList<>();
            double[] listener = new double[3];
            for (int t = 0; t < 2_000; t++) {
                int roll = random.nextInt(100);
                if (roll < 15) {
                    int id = random.nextInt(40);
                    sources.put(id, new double[] {
                        random.nextDouble() * 40 - 20,
                        random.nextDouble() * 10,
                        random.nextDouble() * 40 - 20,
                        random.nextDouble(),
                        4 + random.nextDouble() * 12
                    });
                } else if (roll < 30 && !sources.isEmpty()) {
                    sources.remove(sources.keySet().iterator().next());
                }
                listener[0] += random.nextDouble() - 0.5;
                listener[2] += random.nextDouble() - 0.5;
                double thinAir = t % 300 < 100 ? 1 : t % 300 < 200 ? random.nextDouble() : 0;
                boolean chunks = random.nextInt(50) != 0;
                // Sources renew at random gaps, some of them longer than a lease.
                sources.forEach((id, s) -> {
                    if (random.nextInt(4) != 0) {
                        b.request(id, "hum", s[0], s[1], s[2], s[3], 1, s[4]);
                    }
                });
                b.tick(listener[0], listener[1], listener[2], thinAir, (x, y, z) -> chunks);

                assertTrue(b.voices().size() <= LoopBook.MAX_VOICES, "too many voices at tick " + t);
                assertTrue(b.leaseCount() <= 40, "leases grew");
                for (Voice<String> v : b.voices()) {
                    assertTrue(v.alive());
                    assertTrue(v.gain() >= 0 && v.gain() <= 1, "gain " + v.gain());
                    if (!lastFade.containsKey(v)) {
                        everSeen.add(v);
                        assertEquals(0, v.gain(), "a voice started above silence");
                    }
                    lastFade.put(v, v.gain());
                }
                for (Voice<String> v : everSeen) {
                    if (!v.alive()) {
                        assertEquals(0, v.gain(), "a released voice has a gain");
                    }
                }
            }
            for (int t = 0; t < FORGOTTEN_TICKS; t++) {
                b.tick(listener[0], listener[1], listener[2], 1, (x, y, z) -> true);
            }
            assertTrue(b.voices().isEmpty(), "a stuck voice, seed " + seed);
            assertEquals(0, b.leaseCount(), "a stuck lease, seed " + seed);
            for (Voice<String> v : everSeen) {
                assertFalse(v.alive(), "a voice left alive, seed " + seed);
            }
        }
    }

    // ---- Thin air ----------------------------------------------------------------------------

    @Test
    void client_016_a_vacuum_is_total_silence_with_no_voice_at_all() {
        air = 0;
        run(30, Map.of(1, 0.0));
        assertTrue(book.voices().isEmpty(), "a voice in a vacuum");
    }

    @Test
    void client_016_stepping_into_a_vacuum_fades_to_silence_and_back() {
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 0.0));
        Voice<String> voice = onlyVoice();
        air = 0;
        double last = voice.gain();
        for (int t = 0; t < LoopBook.FADE_TICKS; t++) {
            run(1, Map.of(1, 0.0));
            assertTrue(last - voice.gain() <= 1.0 / LoopBook.FADE_TICKS + 1e-9, "a jump");
            last = voice.gain();
        }
        assertFalse(voice.alive(), "still playing in a vacuum");
        assertEquals(0, voice.gain());
        assertTrue(book.voices().isEmpty());
        air = 1;
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 0.0));
        assertEquals(1, onlyVoice().gain(), 1e-9, "the air came back");
    }

    @Test
    void client_016_thin_air_scales_a_loop_smoothly() {
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 0.0));
        air = 0.5;
        run(LoopBook.FADE_TICKS + 2, Map.of(1, 0.0));
        assertEquals(0.5, onlyVoice().gain(), 1e-9);
    }
}
