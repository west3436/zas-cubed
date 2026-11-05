package io.github.west3436.zas.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The bookkeeping between the book's voices and the sound engine (CLIENT-015), with a stand-in
 * engine: every voice has one sound, and no sound outlives its voice.
 */
class LoopPlayerTest {
    /** A sound in the stand-in engine. */
    private static final class Sound {
        final LoopBook.Voice<String> voice;
        boolean stopped;
        boolean dropped;
        int tended;

        Sound(LoopBook.Voice<String> voice) {
            this.voice = voice;
        }
    }

    private static final class StandIn implements LoopPlayer.Engine<String, Sound> {
        final List<Sound> started = new ArrayList<>();
        final Set<String> mutedSounds = new HashSet<>();
        boolean refuses;

        @Override
        public Sound start(LoopBook.Voice<String> voice) {
            Sound s = new Sound(voice);
            s.dropped = refuses;
            started.add(s);
            return s;
        }

        @Override
        public void stop(Sound sound) {
            assertTrue(!sound.stopped, "a sound was stopped twice");
            sound.stopped = true;
        }

        @Override
        public boolean active(Sound sound) {
            return !sound.dropped;
        }

        @Override
        public void tend(Sound sound) {
            sound.tended++;
        }

        @Override
        public boolean muted(String sound) {
            return mutedSounds.contains(sound);
        }

        List<Sound> playing() {
            return started.stream().filter(s -> !s.stopped).toList();
        }
    }

    private final LoopBook<String> book = new LoopBook<>();
    private final StandIn engine = new StandIn();
    private final LoopPlayer<String, Sound> player = new LoopPlayer<>(engine);

    private void tick(boolean ask) {
        if (ask) {
            book.request("device", "hum", 1, 0, 0, 1, 1, 8);
        }
        book.tick(0, 0, 0, 1, (x, y, z) -> true);
        player.tick(book.voices());
    }

    private void tick(boolean ask, int times) {
        for (int i = 0; i < times; i++) {
            tick(ask);
        }
    }

    @Test
    void client_015_a_voice_has_one_sound_for_as_long_as_it_lives() {
        tick(true, 50);
        assertEquals(1, engine.started.size());
        assertEquals(1, engine.playing().size());
        assertEquals(1, player.size());
        assertTrue(engine.started.get(0).tended >= 48, "it wasn't tended every tick");
    }

    @Test
    void client_015_a_sound_stops_with_its_voice() {
        tick(true, 15);
        tick(false, LoopBook.LEASE_TICKS + LoopBook.FADE_TICKS + 2);
        assertEquals(1, engine.started.size());
        assertTrue(engine.playing().isEmpty(), "a sound outlived its voice");
        assertEquals(0, player.size());
    }

    @Test
    void client_015_a_sound_the_engine_drops_comes_back() {
        tick(true, 30);
        engine.started.get(0).dropped = true;
        tick(true);
        assertEquals(2, engine.started.size(), "a replacement");
        assertEquals(1, engine.playing().size(), "the dropped one is let go");
        assertEquals(engine.started.get(1), engine.playing().get(0));
    }

    @Test
    void client_015_an_engine_that_keeps_refusing_is_asked_once_a_second() {
        engine.refuses = true;
        tick(true, LoopPlayer.RETRY_TICKS * 5);
        assertTrue(engine.started.size() <= 6, "attempts: " + engine.started.size());
        assertTrue(engine.playing().size() <= 1, "more than one attempt held at once");
        engine.refuses = false;
        tick(true, LoopPlayer.RETRY_TICKS + 1);
        assertEquals(1, engine.playing().size());
        assertTrue(engine.active(engine.playing().get(0)));
    }

    @Test
    void client_015_a_loop_on_a_silent_slider_has_no_sound_until_the_slider_is_back() {
        engine.mutedSounds.add("hum");
        tick(true, 50);
        assertTrue(engine.started.isEmpty(), "started on a silent slider");
        engine.mutedSounds.clear();
        tick(true);
        assertEquals(1, engine.playing().size(), "no sound with the slider back up");
        engine.mutedSounds.add("hum");
        tick(true);
        assertTrue(engine.playing().isEmpty(), "still held with the slider at 0");
        engine.mutedSounds.clear();
        tick(true, LoopPlayer.RETRY_TICKS + 1);
        assertEquals(1, engine.playing().size(), "the slider came back up");
    }

    @Test
    void client_015_a_voice_released_on_a_silent_slider_leaves_nothing() {
        engine.mutedSounds.add("hum");
        tick(true, 5);
        tick(false, LoopBook.LEASE_TICKS + LoopBook.FADE_TICKS + 2);
        assertEquals(0, player.size());
        assertTrue(book.voices().isEmpty());
    }

    @Test
    void client_015_stop_all_stops_everything() {
        for (int i = 0; i < 8; i++) {
            book.request(i, "hum", i * 0.5, 0, 0, 1, 1, 8);
        }
        book.tick(0, 0, 0, 1, (x, y, z) -> true);
        player.tick(book.voices());
        assertEquals(8, engine.playing().size());
        book.clear();
        player.stopAll();
        assertTrue(engine.playing().isEmpty());
        assertEquals(0, player.size());
        book.tick(0, 0, 0, 1, (x, y, z) -> true);
        player.tick(book.voices());
        assertTrue(engine.playing().isEmpty(), "a sound came back");
    }

    /**
     * Sources come and go while the engine drops sounds and sliders move. Nothing is ever left
     * playing for a voice that has gone, and in the end nothing is left at all.
     */
    @Test
    void client_015_nothing_is_left_playing_through_random_comings_and_goings() {
        for (long seed = 0; seed < 20; seed++) {
            Random random = new Random(seed);
            LoopBook<String> b = new LoopBook<>();
            StandIn e = new StandIn();
            LoopPlayer<String, Sound> p = new LoopPlayer<>(e);
            for (int t = 0; t < 2_000; t++) {
                for (int source = 0; source < 12; source++) {
                    if (random.nextInt(3) == 0) {
                        b.request(source, source % 2 == 0 ? "hum" : "hiss", source, 0, 0, 1, 1, 16);
                    }
                }
                if (random.nextInt(100) == 0) {
                    e.mutedSounds.add("hum");
                }
                if (random.nextInt(50) == 0) {
                    e.mutedSounds.clear();
                }
                if (random.nextInt(50) == 0 && !e.playing().isEmpty()) {
                    e.playing().get(0).dropped = true;
                }
                if (random.nextInt(500) == 0) {
                    b.clear();
                    p.stopAll();
                }
                b.tick(0, 0, 0, 1, (x, y, z) -> true);
                p.tick(b.voices());
                assertTrue(e.playing().size() <= LoopBook.MAX_VOICES, "too many sounds at tick " + t);
                for (Sound s : e.playing()) {
                    assertTrue(s.voice.alive(), "a sound for a released voice at tick " + t);
                }
            }
            for (int t = 0; t < LoopBook.LEASE_TICKS + LoopBook.FADE_TICKS + 2; t++) {
                b.tick(0, 0, 0, 1, (x, y, z) -> true);
                p.tick(b.voices());
            }
            assertTrue(e.playing().isEmpty(), "a stuck sound, seed " + seed);
            assertEquals(0, p.size(), "a stuck sound, seed " + seed);
        }
    }
}
