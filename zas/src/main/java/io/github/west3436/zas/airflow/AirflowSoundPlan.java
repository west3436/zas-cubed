package io.github.west3436.zas.airflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a client's airflow sounds are (CLIENT-010): the grouping of openings into sources, the
 * loudness and pitch curves, and the cap, with nothing of Minecraft's.
 *
 * <ul>
 *   <li><b>Grouping:</b> the openings are grouped by the {@value #CELL}-block cell of the world
 *       grid they are in, whichever edge they belong to, and each cell is one source at the middle
 *       of its flow. So many edges at one breach add up to one sound, and a cell's source keeps its
 *       identity as the listener moves. A wide breach is several sources, of which a listener hears
 *       the {@value #MAX_SOURCES} loudest at most, so it can't use up the client's loop slots.
 *   <li><b>Two loops a source:</b> a hiss, which says where the leak is, and the wind of the class
 *       its pressure difference reaches (FLOW-011), which gives it body: {@link #loops}. So a client
 *       plays at most {@value #MAX_LOOPS} airflow loops.
 *   <li><b>Loudness</b> follows the moles per second through the source's openings, and no flow is
 *       silence: {@link #volume}. It is capped, so a source can't be a roar however much it adds up.
 *   <li><b>Pitch</b> follows the pressure difference: {@link #pitch}.
 * </ul>
 */
public final class AirflowSoundPlan {
    /** How far from a source it is heard, blocks (CLIENT-010). */
    public static final double RANGE = 16;

    /** The most sources a listener hears at once. */
    public static final int MAX_SOURCES = 2;

    /** The most airflow loops a client plays at once: each source's hiss and wind. */
    public static final int MAX_LOOPS = 2 * MAX_SOURCES;

    /** The side of the grid's cells, blocks. */
    public static final int CELL = 8;

    /**
     * The moles per second at which a source reaches full volume. One hole in a big room at one
     * atmosphere is about 27, and many holes about 69 ({@code FlowExportTest}).
     */
    private static final double FULL_VOLUME_MOLES = 64;

    /** The difference at which a source plays at the sound's own pitch, kPa: one atmosphere. */
    private static final double NATURAL_PITCH_KPA = 101.325;

    /** How much the pitch rises for each doubling of the difference. */
    private static final double PITCH_PER_DOUBLING = 0.107;

    private static final double MIN_PITCH = 0.7;
    private static final double MAX_PITCH = 1.5;

    private AirflowSoundPlan() {}

    /**
     * One source of airflow sound.
     *
     * @param key names the grid cell, the same for as long as the cell has openings
     * @param x the middle of its flow
     * @param molesPerSecond the moles per second through its openings
     * @param kpa the pressure difference across them, weighted by their flow
     */
    public record Source(long key, double x, double y, double z, double molesPerSecond, double kpa) {
        public float volume() {
            return AirflowSoundPlan.volume(molesPerSecond);
        }

        public float pitch() {
            return AirflowSoundPlan.pitch(kpa);
        }

        double distance(double px, double py, double pz) {
            double dx = px - x;
            double dy = py - y;
            double dz = pz - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** The sounds a source can play. */
    public enum Sound {
        HISS,
        WIND_LIGHT,
        WIND_MOB,
        WIND_HEAVY
    }

    /**
     * One looping sound to ask for.
     *
     * @param source names its source: a source and a sound together name one loop
     * @param sound the sound
     * @param x where it is
     * @param volume its volume at the source, 0 to 1
     * @param pitch its pitch
     */
    public record Loop(long source, Sound sound, double x, double y, double z, float volume, float pitch) {}

    /** The wind for a pressure difference: the heaviest class it reaches, and light below them all. */
    public static Sound wind(double kpa, double mobKpa, double heavyKpa) {
        if (kpa >= heavyKpa) {
            return Sound.WIND_HEAVY;
        }
        return kpa >= mobKpa ? Sound.WIND_MOB : Sound.WIND_LIGHT;
    }

    /**
     * The loops to ask for now: each source's hiss and wind, at most {@value #MAX_LOOPS}.
     *
     * @param flows what the client holds
     * @param px the listener's position
     * @param mobKpa the mob class's threshold (FLOW-011)
     * @param heavyKpa the heavy class's threshold (FLOW-011)
     */
    public static List<Loop> loops(
            List<VisibleFlow> flows, double px, double py, double pz, double mobKpa, double heavyKpa) {
        List<Source> sources = plan(flows, px, py, pz);
        List<Loop> loops = new ArrayList<>(sources.size() * 2);
        for (Source s : sources) {
            float volume = s.volume();
            float pitch = s.pitch();
            loops.add(new Loop(s.key(), Sound.HISS, s.x(), s.y(), s.z(), volume, pitch));
            loops.add(new Loop(s.key(), wind(s.kpa(), mobKpa, heavyKpa), s.x(), s.y(), s.z(), volume, pitch));
        }
        return loops;
    }

    /**
     * A source's volume for this flow, 0 to 1, in proportion to it: 0 for none, 1 from the full. This
     * is the one place loudness is decided, so another rule goes here.
     */
    public static float volume(double molesPerSecond) {
        return (float) Math.min(1, Math.max(0, molesPerSecond) / FULL_VOLUME_MOLES);
    }

    /** A source's pitch at this difference: higher by a fixed amount for each doubling. */
    public static float pitch(double kpa) {
        double doublings = Math.log(Math.max(1, kpa) / NATURAL_PITCH_KPA) / Math.log(2);
        return (float) Math.clamp(1 + PITCH_PER_DOUBLING * doublings, MIN_PITCH, MAX_PITCH);
    }

    /** Sums a cell's openings as they are added. */
    private static final class Cell {
        final long key;
        double moles;
        double x;
        double y;
        double z;
        double kpa;

        Cell(long key) {
            this.key = key;
        }

        void add(VisibleOpening o, double share, int flowKpa) {
            moles += share;
            x += o.mx() * share;
            y += o.my() * share;
            z += o.mz() * share;
            kpa += flowKpa * share;
        }

        Source source() {
            return new Source(key, x / moles, y / moles, z / moles, moles, kpa / moles);
        }
    }

    /**
     * The sources a listener hears now: within {@value #RANGE} blocks, at most {@value
     * #MAX_SOURCES}, the loudest where it is first.
     *
     * @param flows what the client holds
     * @param px the listener's position
     */
    public static List<Source> plan(List<VisibleFlow> flows, double px, double py, double pz) {
        if (flows.isEmpty()) {
            return List.of();
        }
        Map<Long, Cell> cells = new HashMap<>();
        for (VisibleFlow flow : flows) {
            double moles = flow.molesPerSecond();
            if (moles <= 0 || flow.openings().isEmpty()) {
                continue;
            }
            // Shared among the openings the client knows of, which may be a sample of those in range.
            double share = moles / flow.openings().size();
            for (VisibleOpening o : flow.openings()) {
                cells.computeIfAbsent(cellKey(o), Cell::new).add(o, share, flow.kpa());
            }
        }
        List<Source> heard = new ArrayList<>();
        for (Cell cell : cells.values()) {
            Source s = cell.source();
            if (s.distance(px, py, pz) <= RANGE) {
                heard.add(s);
            }
        }
        heard.sort(Comparator.comparingDouble((Source s) -> -loudnessAt(s, px, py, pz))
                .thenComparingLong(Source::key));
        return heard.size() <= MAX_SOURCES ? heard : List.copyOf(heard.subList(0, MAX_SOURCES));
    }

    /** How loud a source is where the listener is: its volume, falling to 0 at the range. */
    private static double loudnessAt(Source s, double px, double py, double pz) {
        return s.volume() * (1 - s.distance(px, py, pz) / RANGE);
    }

    private static long cellKey(VisibleOpening o) {
        long cx = Math.floorDiv(o.x(), CELL);
        long cy = Math.floorDiv(o.y(), CELL);
        long cz = Math.floorDiv(o.z(), CELL);
        return ((cx & 0x3FFFFFFL) << 38) | ((cz & 0x3FFFFFFL) << 12) | (cy & 0xFFFL);
    }
}
