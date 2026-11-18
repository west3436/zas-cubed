package io.github.west3436.zas.sound;

import io.github.west3436.zas.provider.Frame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the gas fire blocks around a listener into a few looping sounds (FIRE-036), so a big fire
 * doesn't use up the client's {@value LoopBook#MAX_VOICES} loops: the logic behind {@code
 * client.GasFireLoops}, with no game classes.
 *
 * <p>Space is cut into cubes of {@value #CELL_BLOCKS} blocks, fixed in the world, and the fire blocks
 * in one cube are one source, at their middle. A cube keeps its name while a fire grows and shrinks
 * in it, so its loop follows the fire rather than restarting. The {@value #MAX_SOURCES} cubes that
 * reach the listener loudest play, with a bonus for those already playing, so walking past a big fire
 * doesn't swap its loops back and forth.
 *
 * <p>Blocks another mod shows somewhere else, as Sable shows a sub-level's plot at the ship, are
 * grouped where they are kept, in cubes fixed in the plot, and play where their {@link Frame} shows
 * them (COMPAT-101): a fire in a ship's cabin is heard in the cabin, and keeps its loops as the ship
 * flies and turns. {@link #follow} places them again between looks, so they keep up with the ship.
 *
 * <p>Not thread-safe: the client thread only.
 */
public final class FireLoopGroups {
    /** How many loops a fire can use, however big. */
    public static final int MAX_SOURCES = 3;

    /** The side of a cube, in blocks. */
    public static final int CELL_BLOCKS = 8;

    /** How far away a source can be heard, in blocks: the range each loop asks for. */
    public static final double RANGE = 16;

    /** How often the blocks are looked at, in game ticks: as often as a loop's lease needs. */
    public static final int SCAN_TICKS = LoopBook.LEASE_TICKS;

    /** The volume of a source of one block. */
    static final double SMALLEST_VOLUME = 0.4;

    /** How many blocks in a source reach the full volume. Doubling them adds the same step each time. */
    static final int FULL_BLOCKS = 64;

    /** How much louder a source must be than a playing one to take its place, as {@link LoopBook}'s. */
    static final double KEEP_BONUS = 1.25;

    private static final int CELL_BITS = 3;

    /** A cube of the level, by its coordinates in cubes: what names a loop. */
    public record Source(int x, int y, int z) {}

    /**
     * One source to play.
     *
     * @param source what names its loop
     * @param x where it plays: the middle of its blocks, where the world shows them
     * @param y where it plays
     * @param z where it plays
     * @param blocks how many blocks it stands for
     * @param volume its volume at the source
     * @param pitch its pitch
     */
    public record Group(Source source, double x, double y, double z, int blocks, float volume, float pitch) {}

    private static final class Sum {
        long x;
        long y;
        long z;
        int blocks;
    }

    private record Candidate(Group group, double loudness) {}

    /** A chosen source on a frame, with the middle of its blocks where they are kept. */
    private record Moving(Group group, double x, double y, double z) {}

    private final Map<Source, Sum> sums = new HashMap<>();
    private Set<Source> playing = Set.of();
    private List<Moving> moving = List.of();

    /** Counts a fire block in this round. */
    public void add(int x, int y, int z) {
        Sum sum = sums.computeIfAbsent(new Source(x >> CELL_BITS, y >> CELL_BITS, z >> CELL_BITS), s -> new Sum());
        sum.x += x;
        sum.y += y;
        sum.z += z;
        sum.blocks++;
    }

    /**
     * Ends the round: the sources to ask for now, loudest first, none of them out of range. Nothing is
     * kept of the blocks, so a block gone from the next round has gone from the sound.
     *
     * @param lx where the listener is
     * @param ly where the listener is
     * @param lz where the listener is
     * @return at most {@value #MAX_SOURCES} sources
     */
    public List<Group> choose(double lx, double ly, double lz) {
        return choose(lx, ly, lz, Frame.Lookup.NONE);
    }

    /**
     * Ends the round, for blocks some of which may show somewhere else: each source plays where its
     * frame shows the middle of its blocks, and its distance is measured from there.
     *
     * @param lx where the listener is, as the world shows it
     * @param ly where the listener is
     * @param lz where the listener is
     * @param frames the frames that show the level's blocks
     * @return at most {@value #MAX_SOURCES} sources, placed where they show
     */
    public List<Group> choose(double lx, double ly, double lz, Frame.Lookup frames) {
        List<Candidate> candidates = new ArrayList<>(sums.size());
        Map<Source, double[]> kept = new HashMap<>();
        sums.forEach((source, sum) -> {
            double kx = (double) sum.x / sum.blocks + 0.5;
            double ky = (double) sum.y / sum.blocks + 0.5;
            double kz = (double) sum.z / sum.blocks + 0.5;
            double x = kx;
            double y = ky;
            double z = kz;
            Frame frame = frameOf(source, frames);
            if (frame != null) {
                double[] shown = frame.toShown(kx, ky, kz);
                x = shown[0];
                y = shown[1];
                z = shown[2];
                kept.put(source, new double[] {kx, ky, kz});
            }
            float volume = volume(sum.blocks);
            double reach = 1 - Math.sqrt(squared(x - lx) + squared(y - ly) + squared(z - lz)) / RANGE;
            if (reach > 0) {
                candidates.add(new Candidate(
                        new Group(source, x, y, z, sum.blocks, volume, pitch(source)),
                        volume * reach * (playing.contains(source) ? KEEP_BONUS : 1)));
            }
        });
        sums.clear();
        candidates.sort((a, b) -> {
            int byLoudness = Double.compare(b.loudness(), a.loudness());
            return byLoudness != 0
                    ? byLoudness
                    : compare(a.group().source(), b.group().source());
        });
        List<Group> chosen = new ArrayList<>(MAX_SOURCES);
        Set<Source> nowPlaying = new HashSet<>();
        List<Moving> nowMoving = List.of();
        for (int i = 0; i < candidates.size() && i < MAX_SOURCES; i++) {
            Group group = candidates.get(i).group();
            chosen.add(group);
            nowPlaying.add(group.source());
            double[] k = kept.get(group.source());
            if (k != null) {
                if (nowMoving.isEmpty()) {
                    nowMoving = new ArrayList<>(MAX_SOURCES);
                }
                nowMoving.add(new Moving(group, k[0], k[1], k[2]));
            }
        }
        playing = nowPlaying;
        moving = nowMoving;
        return chosen;
    }

    /**
     * Between looks: the sources of the last round that a frame shows, placed again where it shows
     * them now, so a fire on a ship keeps up with it as it flies and turns. A source where it is was
     * asked for at the look, as it always was, and isn't here; nor is one whose frame has gone.
     *
     * @param frames the frames that show the level's blocks
     * @return those sources, as the last round chose them but where they show now
     */
    public List<Group> follow(Frame.Lookup frames) {
        if (moving.isEmpty()) {
            return List.of();
        }
        List<Group> out = new ArrayList<>(moving.size());
        for (Moving m : moving) {
            Frame frame = frameOf(m.group().source(), frames);
            if (frame != null) {
                double[] shown = frame.toShown(m.x(), m.y(), m.z());
                Group g = m.group();
                out.add(new Group(g.source(), shown[0], shown[1], shown[2], g.blocks(), g.volume(), g.pitch()));
            }
        }
        return out;
    }

    /** The frame that shows a cube, or null: a cube lies in one chunk, so one frame shows all of it. */
    private static Frame frameOf(Source s, Frame.Lookup frames) {
        return frames == Frame.Lookup.NONE ? null : frames.at(s.x() << CELL_BITS, s.z() << CELL_BITS);
    }

    /**
     * The volume of a source of so many blocks: {@value #SMALLEST_VOLUME} for one, rising by the same
     * step each time the blocks double, up to 1 at {@value #FULL_BLOCKS}.
     */
    static float volume(int blocks) {
        double doublings = Math.log(Math.max(1, blocks)) / Math.log(2);
        double full = Math.log(FULL_BLOCKS) / Math.log(2);
        return (float) (SMALLEST_VOLUME + (1 - SMALLEST_VOLUME) * Math.min(1, doublings / full));
    }

    /**
     * A source's pitch: one of five near 1, fixed by its cube. Neighbouring cubes that start in the same
     * round would otherwise play the same file sample for sample, which adds up rather than sounding
     * like more fire.
     */
    static float pitch(Source s) {
        int step = Math.floorMod(s.x() + 2 * s.y() + 3 * s.z(), 5) - 2;
        return 1 + step * 0.03f;
    }

    private static double squared(double d) {
        return d * d;
    }

    private static int compare(Source a, Source b) {
        int byX = Integer.compare(a.x(), b.x());
        if (byX != 0) {
            return byX;
        }
        int byY = Integer.compare(a.y(), b.y());
        return byY != 0 ? byY : Integer.compare(a.z(), b.z());
    }
}
