package io.github.west3436.zas.sound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which looping sounds a client plays, and how loud (CLIENT-015): the logic behind {@code
 * ClientLoops}, with no game classes, so every rule here is tested without a client.
 *
 * <p>A caller {@linkplain #request requests} a loop: a source, a sound, a position, a volume, a pitch
 * and a range. Each {@linkplain #tick game tick} the book then:
 *
 * <ul>
 *   <li><b>Lets lapsed leases go.</b> A request lasts {@value #LEASE_TICKS} ticks, so a loop nobody
 *       asks for again stops, whatever happened to whoever asked. Nothing has to turn a loop off.
 *   <li><b>Chooses.</b> A lease in range, in a loaded chunk and with some volume is a candidate, and
 *       the {@value #MAX_VOICES} that will play loudest at the listener are chosen. That counts each
 *       request's weight, what the sound engine multiplies its volume by. Among loops of one volume,
 *       weight and range it is the nearest. A chosen loop gets a bonus, so loops at about the same
 *       loudness don't take turns. In a vacuum nothing is chosen.
 *   <li><b>Moves the voices.</b> A voice is one playing sound. Its {@linkplain Voice#gain gain} is a
 *       fade times how much distance leaves, a straight line down to 0 at the range. The fade ramps
 *       towards the volume times the thin-air factor over {@value #FADE_TICKS} ticks, whenever that
 *       changes, so nothing clicks on a start, a stop, a change of volume or a change of air. A voice
 *       not chosen ramps to 0 and is released.
 *   <li><b>Starts voices.</b> A chosen loop with no voice gets one if fewer than {@value #MAX_VOICES}
 *       exist, so a swap waits for the old voice to fade out, and there are never more than that.
 * </ul>
 *
 * <p>Not thread-safe: the client thread only.
 *
 * @param <S> what names a sound, compared with {@code equals}
 */
public final class LoopBook<S> {
    /** The most voices that exist at once: the owner's nearest 8. */
    public static final int MAX_VOICES = 8;

    /** How many game ticks a request lasts. */
    public static final int LEASE_TICKS = 10;

    /** How many game ticks a fade takes: half a second. */
    public static final int FADE_TICKS = 10;

    /** How much louder a loop must be than a chosen one to take its place. */
    static final double KEEP_BONUS = 1.25;

    /** How far a voice's pitch moves in a game tick, so a change glides. */
    static final double PITCH_STEP = 0.05;

    /** Whether a position's chunk is loaded on the client. */
    public interface Loaded {
        boolean at(double x, double y, double z);
    }

    private record Key(Object source, Object sound) {}

    /** What a source last asked for. */
    private static final class Lease<S> {
        final Key key;
        final S sound;
        final long order;
        double x;
        double y;
        double z;
        double volume;
        double pitch;
        double range;
        double weight;
        long expires;
        double loudness;

        Lease(Key key, S sound, long order) {
            this.key = key;
            this.sound = sound;
            this.order = order;
        }
    }

    /** One playing sound. A voice outlives its lease by as long as it takes to fade out. */
    public static final class Voice<S> {
        private final Key key;
        private final S sound;
        private double x;
        private double y;
        private double z;
        private double range;
        private double pitch;
        private double fade;
        private double fadeTarget;
        private double fadeStep;
        private int fadeLeft;
        private double gain;
        private boolean chosen = true;
        private boolean alive = true;

        private Voice(Key key, S sound, double x, double y, double z, double range, double pitch) {
            this.key = key;
            this.sound = sound;
            this.x = x;
            this.y = y;
            this.z = z;
            this.range = range;
            this.pitch = pitch;
        }

        public S sound() {
            return sound;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        /** The volume to play at now, 0 to 1. */
        public double gain() {
            return gain;
        }

        /** The pitch to play at now. */
        public double pitch() {
            return pitch;
        }

        /** False once the book has released it: it must stop, and nothing brings it back. */
        public boolean alive() {
            return alive;
        }

        /**
         * Moves the fade a tick towards its target, in {@link LoopBook#FADE_TICKS} even steps from
         * wherever it was when the target last changed, landing on the target exactly.
         */
        private void ramp(double target) {
            if (target != fadeTarget) {
                fadeTarget = target;
                fadeStep = (target - fade) / FADE_TICKS;
                fadeLeft = FADE_TICKS;
            }
            if (fadeLeft > 0) {
                fadeLeft--;
                fade = fadeLeft == 0 ? fadeTarget : fade + fadeStep;
            }
        }
    }

    private final Map<Key, Lease<S>> leases = new LinkedHashMap<>();
    private final Map<Key, Voice<S>> voices = new LinkedHashMap<>();
    private final Collection<Voice<S>> voicesView = Collections.unmodifiableCollection(voices.values());
    private final List<Lease<S>> ranked = new ArrayList<>();
    private long now;
    private long nextOrder;

    /** Asks for a loop with a weight of 1: see {@link #request(Object, Object, double, double, double, double, double, double, double)}. */
    public void request(
            Object source, S sound, double x, double y, double z, double volume, double pitch, double range) {
        request(source, sound, x, y, z, volume, pitch, range, 1);
    }

    /**
     * Asks for a loop, or renews it. A source and a sound together name one loop.
     *
     * @param volume its volume at the source, 0 to 1
     * @param pitch its pitch
     * @param range how far away it can be heard, in blocks, above 0
     * @param weight what the sound engine will multiply its volume by, which the book can't see. It
     *     counts only in choosing: the engine applies it to the gain itself. At 0 the loop isn't a
     *     candidate.
     */
    public void request(
            Object source,
            S sound,
            double x,
            double y,
            double z,
            double volume,
            double pitch,
            double range,
            double weight) {
        Key key = new Key(source, sound);
        Lease<S> lease = leases.computeIfAbsent(key, k -> new Lease<>(k, sound, nextOrder++));
        lease.x = x;
        lease.y = y;
        lease.z = z;
        lease.volume = volume;
        lease.pitch = pitch;
        lease.range = range;
        lease.weight = weight;
        lease.expires = now + LEASE_TICKS;
    }

    /**
     * One game tick.
     *
     * @param lx where the listener is
     * @param ly where the listener is
     * @param lz where the listener is
     * @param thinAir the {@link ThinAir} factor for the listener, 0 to 1
     * @param loaded whether a position's chunk is loaded
     */
    public void tick(double lx, double ly, double lz, double thinAir, Loaded loaded) {
        now++;
        rank(lx, ly, lz, thinAir, loaded);
        Iterator<Voice<S>> it = voices.values().iterator();
        while (it.hasNext()) {
            Voice<S> voice = it.next();
            Lease<S> lease = leases.get(voice.key);
            if (lease != null) {
                voice.x = lease.x;
                voice.y = lease.y;
                voice.z = lease.z;
                voice.range = lease.range;
                voice.pitch += Math.max(-PITCH_STEP, Math.min(PITCH_STEP, lease.pitch - voice.pitch));
            }
            voice.chosen = false;
            double target = 0;
            for (int i = 0; i < ranked.size() && i < MAX_VOICES; i++) {
                if (ranked.get(i) == lease) {
                    voice.chosen = true;
                    target = lease.volume * thinAir;
                    break;
                }
            }
            voice.ramp(target);
            double dx = voice.x - lx;
            double dy = voice.y - ly;
            double dz = voice.z - lz;
            double reach = reach(Math.sqrt(dx * dx + dy * dy + dz * dz), voice.range);
            voice.gain = voice.fade * reach;
            // A voice nobody wants is released once it is silent: faded out, or out of range.
            if (!voice.chosen && (voice.fade <= 0 || reach <= 0)) {
                voice.gain = 0;
                voice.alive = false;
                it.remove();
            }
        }
        for (int i = 0; i < ranked.size() && i < MAX_VOICES && voices.size() < MAX_VOICES; i++) {
            Lease<S> lease = ranked.get(i);
            if (!voices.containsKey(lease.key)) {
                voices.put(
                        lease.key,
                        new Voice<>(lease.key, lease.sound, lease.x, lease.y, lease.z, lease.range, lease.pitch));
            }
        }
    }

    /** Drops lapsed leases, and ranks the candidates, loudest first. */
    private void rank(double lx, double ly, double lz, double thinAir, Loaded loaded) {
        ranked.clear();
        Iterator<Lease<S>> it = leases.values().iterator();
        while (it.hasNext()) {
            Lease<S> lease = it.next();
            if (now > lease.expires) {
                it.remove();
                continue;
            }
            double dx = lease.x - lx;
            double dy = lease.y - ly;
            double dz = lease.z - lz;
            double heard = lease.volume * lease.weight * reach(Math.sqrt(dx * dx + dy * dy + dz * dz), lease.range);
            if (thinAir > 0 && heard > 0 && loaded.at(lease.x, lease.y, lease.z)) {
                Voice<S> voice = voices.get(lease.key);
                lease.loudness = voice != null && voice.chosen ? heard * KEEP_BONUS : heard;
                ranked.add(lease);
            }
        }
        ranked.sort((a, b) -> {
            int byLoudness = Double.compare(b.loudness, a.loudness);
            return byLoudness != 0 ? byLoudness : Long.compare(a.order, b.order);
        });
    }

    /** How much of a sound distance leaves: 1 at the source, falling in a straight line to 0 at the range. */
    private static double reach(double distance, double range) {
        return Math.max(0, 1 - distance / range);
    }

    /**
     * Drops every lease and voice at once, with no fade: the level is going, and the game has already
     * stopped its sounds. A released voice reads {@linkplain Voice#alive not alive}.
     */
    public void clear() {
        for (Voice<S> voice : voices.values()) {
            voice.gain = 0;
            voice.alive = false;
        }
        voices.clear();
        leases.clear();
        ranked.clear();
    }

    /** The voices playing now, as a live view. */
    public Collection<Voice<S>> voices() {
        return voicesView;
    }

    /** How many leases the book holds, which falls to 0 once nothing is asked for. */
    public int leaseCount() {
        return leases.size();
    }
}
