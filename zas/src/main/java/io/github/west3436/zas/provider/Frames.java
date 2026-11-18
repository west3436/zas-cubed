package io.github.west3436.zas.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link Frame}s that show parts of a level somewhere else (COMPAT-101, API-068), from the
 * sources adapters add: Sable's adapter adds its sub-levels. Without a source, every block shows
 * where it is, and each method here answers as much at once.
 *
 * <p>What uses them: airflow, to find the openings near a player or a mob where the world shows them,
 * and to push it along the flow as the frame turns it; airflow's crawl, to see the blocks a frame
 * shows in the way ({@link #collides}); the client, to play airflow's sounds and particles where the
 * hole shows; and {@code Relocations.shownAt}, for the coordinates a player is shown.
 *
 * <p>The level's own thread: the server thread for a server level, the client thread for the
 * client's.
 */
public final class Frames {
    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    private Frames() {}

    /** A mod's frames. */
    public interface Source {
        /**
         * The frame that shows a block column of a level.
         *
         * @return the frame, or null where this source shows none
         */
        @Nullable
        Frame at(Level level, int blockX, int blockZ);

        /**
         * The frames whose shown bounds meet a box, in no particular order.
         *
         * @param shown a box of shown points
         */
        List<Frame> near(Level level, AABB shown);

        /**
         * Whether a box of shown points meets the blocks this source shows somewhere else, as an
         * entity's box there would: what {@link Level#noCollision} misses, because it looks only
         * where the blocks are kept. Without it, this source's blocks stop nothing.
         *
         * @param shown a box of shown points
         */
        default boolean collides(Level level, AABB shown) {
            return false;
        }
    }

    /** Adds a source. */
    public static void add(Source source) {
        SOURCES.add(Objects.requireNonNull(source, "source"));
    }

    /** For GameTests: takes away a source {@link #add} added. */
    public static void removeForTest(Source source) {
        SOURCES.remove(source);
    }

    /** Whether any source was added, so the callers can skip the work without one. */
    public static boolean any() {
        return !SOURCES.isEmpty();
    }

    /** The frame that shows a block column, or null where it shows where it is. */
    public static @Nullable Frame at(Level level, int blockX, int blockZ) {
        for (Source source : SOURCES) {
            Frame frame = source.at(level, blockX, blockZ);
            if (frame != null) {
                return frame;
            }
        }
        return null;
    }

    /** {@link #at} for one level. */
    public static Frame.Lookup lookup(Level level) {
        return SOURCES.isEmpty() ? Frame.Lookup.NONE : (x, z) -> at(level, x, z);
    }

    /** The frames whose shown bounds meet a box of shown points. */
    public static List<Frame> near(Level level, AABB shown) {
        if (SOURCES.isEmpty()) {
            return List.of();
        }
        List<Frame> out = null;
        for (Source source : SOURCES) {
            List<Frame> found = source.near(level, shown);
            if (!found.isEmpty()) {
                if (out == null) {
                    out = new ArrayList<>(found.size());
                }
                out.addAll(found);
            }
        }
        return out == null ? List.of() : out;
    }

    /**
     * Whether a box of shown points meets blocks a source shows there (FLOW-019), such as a ship's
     * hull: the part of a collision check {@link Level#noCollision} can't see. False without a
     * source.
     */
    public static boolean collides(Level level, AABB shown) {
        for (Source source : SOURCES) {
            if (source.collides(level, shown)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The points a shown point stands for when looking for what is near it in the level: itself,
     * then its point in each frame whose shown bounds come within {@code range} of it. A frame keeps
     * distances, so what is within a range of one of them is within that range where it shows.
     */
    public static List<Vec3> pointsNear(Level level, Vec3 shown, double range) {
        List<Frame> frames = near(level, new AABB(shown, shown).inflate(range));
        if (frames.isEmpty()) {
            return List.of(shown);
        }
        List<Vec3> out = new ArrayList<>(frames.size() + 1);
        out.add(shown);
        for (Frame frame : frames) {
            double[] p = frame.toLevel(shown.x, shown.y, shown.z);
            out.add(new Vec3(p[0], p[1], p[2]));
        }
        return out;
    }

    /** Where a point of a level shows (API-068): itself, unless a frame shows its column. */
    public static Vec3 shown(Level level, Vec3 point) {
        if (SOURCES.isEmpty()) {
            return point;
        }
        Frame frame = at(level, (int) Math.floor(point.x), (int) Math.floor(point.z));
        if (frame == null) {
            return point;
        }
        double[] p = frame.toShown(point.x, point.y, point.z);
        return new Vec3(p[0], p[1], p[2]);
    }
}
