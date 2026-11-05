package io.github.west3436.zas.sound;

import io.github.west3436.zas.sound.LoopBook.Voice;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Keeps one playing sound per {@link Voice} (CLIENT-015): the bookkeeping between {@link LoopBook}
 * and the sound engine, with no game classes.
 *
 * <p>A voice gets a sound while it is alive, unless its slider is at 0, and loses it when released.
 * The engine can drop a sound by itself, as when the audio device changes. A voice whose sound has
 * gone gets a new one, at most once every {@value #RETRY_TICKS} ticks, so one the engine keeps
 * refusing costs little.
 *
 * <p>Not thread-safe: the client thread only.
 *
 * @param <S> what names a sound
 * @param <H> the engine's handle on a playing sound
 */
public final class LoopPlayer<S, H> {
    /** How long a voice waits to try again for a sound, in game ticks. */
    public static final int RETRY_TICKS = 20;

    /** What this needs of the sound engine. */
    public interface Engine<S, H> {
        /** Starts a sound for a voice. */
        H start(Voice<S> voice);

        /** Stops a sound, which won't be used again. */
        void stop(H sound);

        /** Whether the engine still has a sound. */
        boolean active(H sound);

        /** Says a sound is still wanted, once a tick. */
        void tend(H sound);

        /** Whether a sound's slider is at 0, when the engine plays nothing and forgets badly. */
        boolean muted(S sound);
    }

    private static final class Playing<H> {
        H sound;
        long nextTry;
    }

    private final Engine<S, H> engine;
    private final Map<Voice<S>, Playing<H>> playing = new HashMap<>();
    private long now;

    public LoopPlayer(Engine<S, H> engine) {
        this.engine = engine;
    }

    /**
     * One game tick, after the book's.
     *
     * @param voices the book's voices
     */
    public void tick(Iterable<Voice<S>> voices) {
        now++;
        Iterator<Map.Entry<Voice<S>, Playing<H>>> it = playing.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Voice<S>, Playing<H>> e = it.next();
            if (!e.getKey().alive()) {
                stop(e.getValue());
                it.remove();
            }
        }
        for (Voice<S> voice : voices) {
            Playing<H> p = playing.computeIfAbsent(voice, v -> new Playing<>());
            boolean muted = engine.muted(voice.sound());
            if (p.sound != null) {
                engine.tend(p.sound);
                if (muted || !engine.active(p.sound)) {
                    stop(p);
                }
            }
            if (p.sound == null && !muted && now >= p.nextTry) {
                p.sound = engine.start(voice);
                p.nextTry = now + RETRY_TICKS;
            }
        }
    }

    private void stop(Playing<H> p) {
        if (p.sound != null) {
            engine.stop(p.sound);
            p.sound = null;
        }
    }

    /** Stops every sound at once, for a {@link LoopBook#clear}. */
    public void stopAll() {
        playing.values().forEach(this::stop);
        playing.clear();
    }

    /** How many sounds are playing, which falls to 0 once every voice is released. */
    public int size() {
        return (int) playing.values().stream().filter(p -> p.sound != null).count();
    }
}
