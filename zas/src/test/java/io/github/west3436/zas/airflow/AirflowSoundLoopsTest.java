package io.github.west3436.zas.airflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.airflow.AirflowSoundPlan.Loop;
import io.github.west3436.zas.airflow.AirflowSoundPlan.Sound;
import io.github.west3436.zas.sound.LoopBook;
import io.github.west3436.zas.sound.ThinAir;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Airflow's loops through the real loop manager, as {@code client.AirflowSounds} asks for them
 * (CLIENT-010): which play, that a steady breach goes on in the same voices, that a vacuum is silent,
 * and that none is left once the flow ends. How they sound is the owner's check.
 */
class AirflowSoundLoopsTest {
    private static final int WEST = 4;
    private static final double MOB = 65;
    private static final double HEAVY = 85;
    private static final double DEVICE_RANGE = 8;
    private static final float DEVICE_HARD = 1;
    private static final float DEVICE_TRICKLE = 0.35f;

    /** How long a loop nobody asks for can last: its lease, then its fade, then its release. */
    private static final int FORGOTTEN_TICKS = LoopBook.LEASE_TICKS + LoopBook.FADE_TICKS + 2;

    private final LoopBook<Object> book = new LoopBook<>();

    /** One hole at 0, 64, 0: its face's middle is at 0, 64.5, 0.5. */
    private static List<VisibleFlow> hole(int kpa, double moles) {
        return List.of(new VisibleFlow(kpa, VisibleFlow.step(moles), List.of(new VisibleOpening(0, 64, 0, WEST))));
    }

    /** Holes along z, a grid cell apart. */
    private static List<VisibleFlow> wideBreach(int holes) {
        List<VisibleFlow> flows = new ArrayList<>();
        for (int i = 0; i < holes; i++) {
            flows.add(new VisibleFlow(
                    101, VisibleFlow.step(27), List.of(new VisibleOpening(0, 64, i * AirflowSoundPlan.CELL, WEST))));
        }
        return flows;
    }

    private void ask(List<VisibleFlow> flows, double x) {
        for (Loop l : AirflowSoundPlan.loops(flows, x, 64.5, 0.5, MOB, HEAVY)) {
            book.request(l.source(), l.sound(), l.x(), l.y(), l.z(), l.volume(), l.pitch(), AirflowSoundPlan.RANGE);
        }
    }

    /** One tick for a listener on the line x, 64.5, 0.5: asks as the client does, then the book runs. */
    private void tick(List<VisibleFlow> flows, double x, double air) {
        ask(flows, x);
        book.tick(x, 64.5, 0.5, air, (bx, by, bz) -> true);
        assertTrue(book.voices().size() <= LoopBook.MAX_VOICES, () -> "voices: " + book.voices());
    }

    private void run(int ticks, List<VisibleFlow> flows, double x, double air) {
        for (int i = 0; i < ticks; i++) {
            tick(flows, x, air);
        }
    }

    private List<Object> playing() {
        return book.voices().stream().map(LoopBook.Voice::sound).toList();
    }

    private LoopBook.Voice<Object> voice(Object sound) {
        return book.voices().stream()
                .filter(v -> v.sound().equals(sound))
                .findFirst()
                .orElseThrow(() -> new AssertionError(sound + " isn't playing: " + playing()));
    }

    @Test
    void client_010_a_source_is_a_hiss_and_the_wind_of_its_class() {
        Map<Integer, Sound> byKpa = Map.of(
                20, Sound.WIND_LIGHT,
                64, Sound.WIND_LIGHT,
                65, Sound.WIND_MOB,
                84, Sound.WIND_MOB,
                85, Sound.WIND_HEAVY,
                500, Sound.WIND_HEAVY);
        byKpa.forEach((kpa, wind) -> {
            List<Loop> loops = AirflowSoundPlan.loops(hole(kpa, 27), 2, 64.5, 0.5, MOB, HEAVY);
            assertEquals(
                    List.of(Sound.HISS, wind), loops.stream().map(Loop::sound).toList(), "at " + kpa);
            Loop hiss = loops.get(0);
            Loop gust = loops.get(1);
            assertEquals(hiss.source(), gust.source());
            assertEquals(hiss.volume(), gust.volume());
            assertEquals(hiss.pitch(), gust.pitch());
            assertEquals(hiss.x(), gust.x());
        });
    }

    @Test
    void client_010_the_wind_follows_the_servers_thresholds() {
        assertEquals(Sound.WIND_LIGHT, AirflowSoundPlan.wind(90, 95, 200));
        assertEquals(Sound.WIND_MOB, AirflowSoundPlan.wind(90, 40, 200));
        assertEquals(Sound.WIND_HEAVY, AirflowSoundPlan.wind(90, 40, 60));
    }

    @Test
    void client_010_a_wide_breach_takes_at_most_four_of_the_8_slots() {
        run(30, wideBreach(30), 2, 1);
        assertEquals(AirflowSoundPlan.MAX_LOOPS, book.voices().size(), () -> "playing: " + playing());
        assertEquals(AirflowSoundPlan.MAX_LOOPS, book.leaseCount());
    }

    @Test
    void client_010_a_listener_in_a_vacuum_hears_nothing_and_one_in_the_air_does() {
        List<VisibleFlow> breach = hole(101, 27);
        run(30, breach, 2, ThinAir.factor(0));
        assertTrue(book.voices().isEmpty(), () -> "playing in a vacuum: " + playing());
        run(30, breach, 2, ThinAir.factor(101));
        assertEquals(2, book.voices().size(), () -> "playing: " + playing());
        assertTrue(voice(Sound.HISS).gain() > 0);
    }

    @Test
    void client_010_stepping_into_a_vacuum_fades_every_voice_away_though_the_flow_goes_on() {
        List<VisibleFlow> breach = hole(101, 27);
        run(30, breach, 2, 1);
        run(FORGOTTEN_TICKS, breach, 2, ThinAir.factor(1));
        assertTrue(book.voices().isEmpty(), () -> "still playing: " + playing());
    }

    @Test
    void client_010_a_steady_breach_plays_on_in_the_same_voices_as_its_volume_and_pitch_move() {
        run(30, hole(101, 27), 2, 1);
        LoopBook.Voice<Object> hiss = voice(Sound.HISS);
        LoopBook.Voice<Object> wind = voice(Sound.WIND_HEAVY);
        run(600, hole(101, 27), 2, 1);
        assertSame(hiss, voice(Sound.HISS));
        assertSame(wind, voice(Sound.WIND_HEAVY));
        double before = hiss.gain();
        double pitchBefore = hiss.pitch();
        // The flow halves and the difference falls, within one class.
        run(30, hole(90, 13), 2, 1);
        assertSame(hiss, voice(Sound.HISS));
        assertSame(wind, voice(Sound.WIND_HEAVY));
        assertTrue(hiss.gain() < before, () -> "gain " + hiss.gain());
        assertTrue(hiss.pitch() < pitchBefore, () -> "pitch " + hiss.pitch());
    }

    @Test
    void client_010_when_the_flow_ends_every_loop_stops_with_nothing_left_behind() {
        run(30, wideBreach(30), 2, 1);
        run(FORGOTTEN_TICKS, List.of(), 2, 1);
        assertTrue(book.voices().isEmpty(), () -> "still playing: " + playing());
        assertEquals(0, book.leaseCount());
    }

    @Test
    void client_010_walking_out_of_range_stops_it_and_walking_back_starts_it() {
        List<VisibleFlow> breach = hole(101, 27);
        run(30, breach, 2, 1);
        run(FORGOTTEN_TICKS, breach, 17, 1);
        assertTrue(book.voices().isEmpty(), () -> "still playing: " + playing());
        assertEquals(0, book.leaseCount());
        run(30, breach, 2, 1);
        assertEquals(2, book.voices().size());
    }

    @Test
    void client_010_as_a_room_drains_only_the_wind_changes_and_never_more_than_one_extra_voice() {
        run(30, hole(90, 27), 2, 1);
        LoopBook.Voice<Object> hiss = voice(Sound.HISS);
        for (int i = 0; i < LoopBook.LEASE_TICKS + LoopBook.FADE_TICKS; i++) {
            tick(hole(70, 20), 2, 1);
            assertTrue(book.voices().size() <= 3, () -> "playing: " + playing());
            assertSame(hiss, voice(Sound.HISS));
        }
        run(2, hole(70, 20), 2, 1);
        assertEquals(List.of(Sound.HISS, Sound.WIND_MOB), playing());
    }

    /** Asks for {@code count} machines' loops, named {@code name0} and on, all at x, 64.5, 0.5. */
    private void askMachines(String name, int count, double x, float volume) {
        for (int i = 0; i < count; i++) {
            book.request(name + i, name + i, x, 64.5, 0.5, volume, 1, DEVICE_RANGE);
        }
    }

    @Test
    void client_010_a_breach_takes_its_slots_from_the_quietest_loops_and_only_them() {
        // One hole at 1 atm, 2 blocks away, ranks at 0.42 * (1 - 2/16), about 0.37. Machines 1 block
        // from the listener rank at 0.875 working hard, and at 0.31 at a trickle.
        for (int i = 0; i < 20; i++) {
            askMachines("hard", 4, 3, DEVICE_HARD);
            askMachines("trickle", 6, 3, DEVICE_TRICKLE);
            tick(hole(101, 27), 2, 1);
        }
        Map<String, Integer> counts = new HashMap<>();
        for (Object sound : playing()) {
            counts.merge(sound instanceof Sound ? "airflow" : sound.toString().replaceAll("\\d", ""), 1, Integer::sum);
        }
        assertEquals(Map.of("hard", 4, "airflow", 2, "trickle", 2), counts);
    }

    @Test
    void client_010_airflow_at_its_loudest_leaves_machines_the_other_four_slots() {
        List<VisibleFlow> loudest = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            loudest.add(new VisibleFlow(
                    101, VisibleFlow.step(200), List.of(new VisibleOpening(0, 64, i * AirflowSoundPlan.CELL, WEST))));
        }
        for (int i = 0; i < 20; i++) {
            askMachines("machine", 8, 3, DEVICE_TRICKLE);
            tick(loudest, 2, 1);
        }
        long machines = playing().stream().filter(s -> !(s instanceof Sound)).count();
        assertEquals(LoopBook.MAX_VOICES - AirflowSoundPlan.MAX_LOOPS, machines, () -> "playing: " + playing());
    }
}
