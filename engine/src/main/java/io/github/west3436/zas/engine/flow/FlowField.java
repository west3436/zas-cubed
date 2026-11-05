package io.github.west3436.zas.engine.flow;

import io.github.west3436.zas.engine.classify.CellIds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The airflow of one dimension at one atmos tick (FLOW-001, FLOW-002): every opening of the
 * {@link FlowEdge}s, bucketed by the 16-block section it is in, so a push looks only at the
 * openings near the entity, however large the zones are (FLOW-006). Immutable; any thread.
 *
 * <p>The server pushes items and mobs with it, and the client pushes its own player with the
 * openings the server sends it, through the same {@link #push} (FLOW-017).
 */
public final class FlowField {
    /** An entity's zone that matches every opening: the client, which doesn't know zones. */
    public static final long ANY_ZONE = Long.MIN_VALUE;

    /** An empty field. */
    public static final FlowField EMPTY = new FlowField(List.of());

    /**
     * One opening, from the cell on the higher-pressure side to the cell on the lower, at block
     * positions (the cells' blocks; their centres are half a block further).
     *
     * @param hx the high side's block x
     * @param hy the high side's block y
     * @param hz the high side's block z
     * @param lx the low side's block x
     * @param ly the low side's block y
     * @param lz the low side's block z
     * @param difference the edge's pressure difference, kPa
     * @param highZone the high side's zone, or {@link FlowEdge#AMBIENT}
     * @param lowZone the low side's zone, or {@link FlowEdge#AMBIENT}
     * @param molesPerSecond this opening's share of the edge's flow, shared evenly (CLIENT-010)
     */
    public record Opening(
            int hx,
            int hy,
            int hz,
            int lx,
            int ly,
            int lz,
            double difference,
            long highZone,
            long lowZone,
            double molesPerSecond) {
        /** An opening whose flow in moles isn't known, as the client gets them for pushing. */
        public Opening(int hx, int hy, int hz, int lx, int ly, int lz, double difference, long highZone, long lowZone) {
            this(hx, hy, hz, lx, ly, lz, difference, highZone, lowZone, 0);
        }

        /** Whether an entity in {@code zone} is on one of this opening's sides. */
        public boolean touches(long zone) {
            return zone == ANY_ZONE || zone == highZone || zone == lowZone;
        }

        /** The middle of the face between the two cells. */
        double mx() {
            return (hx + lx) * 0.5 + 0.5;
        }

        double my() {
            return (hy + ly) * 0.5 + 0.5;
        }

        double mz() {
            return (hz + lz) * 0.5 + 0.5;
        }

        /** The distance from a point to the middle of the face. */
        public double distance(double x, double y, double z) {
            double dx = x - mx();
            double dy = y - my();
            double dz = z - mz();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /**
         * The unit direction the flow carries a point: on the high side, towards the low cell's
         * centre, through the opening; on the low side, away from the high cell's centre.
         *
         * @return {x, y, z}, or null at the point the direction is undefined
         */
        public double[] direction(double x, double y, double z) {
            double nx = lx - hx;
            double ny = ly - hy;
            double nz = lz - hz;
            double side = (x - mx()) * nx + (y - my()) * ny + (z - mz()) * nz;
            double dx;
            double dy;
            double dz;
            if (side < 0) {
                dx = lx + 0.5 - x;
                dy = ly + 0.5 - y;
                dz = lz + 0.5 - z;
            } else {
                dx = x - (hx + 0.5);
                dy = y - (hy + 0.5);
                dz = z - (hz + 0.5);
            }
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-9) {
                return null;
            }
            return new double[] {dx / len, dy / len, dz / len};
        }
    }

    private final List<Opening> openings;
    private final Map<Long, List<Opening>> bySection = new HashMap<>();

    private FlowField(List<Opening> openings) {
        this.openings = List.copyOf(openings);
        for (Opening o : this.openings) {
            long key = key(Math.floorDiv(o.hx, 16), Math.floorDiv(o.hy, 16), Math.floorDiv(o.hz, 16));
            bySection.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }
    }

    /** The field of an atmos tick's flow edges; each opening runs from its edge's higher side. */
    public static FlowField of(List<FlowEdge> edges) {
        List<Opening> out = new ArrayList<>();
        for (FlowEdge e : edges) {
            boolean zoneHigh = e.pressure() >= e.otherPressure();
            long high = zoneHigh ? e.zone() : e.other();
            long low = zoneHigh ? e.other() : e.zone();
            double share = e.openings() == 0 ? 0 : e.molesPerSecond() / e.openings();
            for (int i = 0; i < e.openings(); i++) {
                long h = zoneHigh ? e.cell(i) : e.otherCell(i);
                long l = zoneHigh ? e.otherCell(i) : e.cell(i);
                out.add(new Opening(
                        CellIds.x(h),
                        CellIds.y(h),
                        CellIds.z(h),
                        CellIds.x(l),
                        CellIds.y(l),
                        CellIds.z(l),
                        e.difference(),
                        high,
                        low,
                        share));
            }
        }
        return out.isEmpty() ? EMPTY : new FlowField(out);
    }

    /** A field of these openings, as the client builds from what the server sent it. */
    public static FlowField ofOpenings(List<Opening> openings) {
        return openings.isEmpty() ? EMPTY : new FlowField(openings);
    }

    public boolean isEmpty() {
        return openings.isEmpty();
    }

    /** Every opening. */
    public List<Opening> openings() {
        return openings;
    }

    /**
     * The 16-block sections that hold openings, as {sx, sy, sz} section coordinates: an entity
     * airflow can reach is within the range of one of them.
     */
    public List<int[]> sections() {
        List<int[]> out = new ArrayList<>(bySection.size());
        for (List<Opening> list : bySection.values()) {
            Opening o = list.get(0);
            out.add(new int[] {Math.floorDiv(o.hx, 16), Math.floorDiv(o.hy, 16), Math.floorDiv(o.hz, 16)});
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The openings within {@code radius} of a point that an entity in {@code zone} is beside, whose
     * difference is at least {@code threshold}.
     */
    public List<Opening> near(double x, double y, double z, double radius, long zone, double threshold) {
        List<Opening> out = new ArrayList<>();
        int r = (int) Math.ceil((radius + 1) / 16);
        int sx = Math.floorDiv((int) Math.floor(x), 16);
        int sy = Math.floorDiv((int) Math.floor(y), 16);
        int sz = Math.floorDiv((int) Math.floor(z), 16);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    List<Opening> list = bySection.get(key(sx + dx, sy + dy, sz + dz));
                    if (list == null) {
                        continue;
                    }
                    for (Opening o : list) {
                        if (o.difference >= threshold && o.touches(zone) && o.distance(x, y, z) < radius) {
                            out.add(o);
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * The change of velocity airflow gives an entity this game tick (FLOW-002): from the opening
     * whose flow is fastest where the entity is, among those it is beside whose difference reaches
     * its class's threshold (FLOW-003). The entity's speed along the flow closes {@link
     * FlowSettings#response} of the gap to the flow's speed; nothing if it is already as fast.
     *
     * @param x the entity's centre
     * @param zone the entity's zone, {@link FlowEdge#AMBIENT}, or {@link #ANY_ZONE}
     * @param threshold the least difference that moves it, kPa
     * @return {dx, dy, dz} to add to its velocity, or null for none
     */
    public double[] push(
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            long zone,
            double threshold,
            FlowSettings s) {
        return push(x, y, z, vx, vy, vz, zone, threshold, s, 0);
    }

    /**
     * {@link #push(double, double, double, double, double, double, long, double, FlowSettings)} for
     * an entity with airflow resistance (FLOW-012): the flow's speed where it is, and so the push
     * from rest, scaled by (1 − r). An entity with r = 1 is never pushed.
     *
     * @param resistance its resistance r, 0 to 1
     */
    public double[] push(
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            long zone,
            double threshold,
            FlowSettings s,
            double resistance) {
        double keep = 1 - Math.min(1, Math.max(0, resistance));
        if (isEmpty() || keep <= 0) {
            return null;
        }
        Opening best = null;
        double bestSpeed = 0;
        for (Opening o : near(x, y, z, s.range(), zone, threshold)) {
            double speed = s.speed(o.difference, o.distance(x, y, z));
            if (speed > bestSpeed) {
                best = o;
                bestSpeed = speed;
            }
        }
        if (best == null) {
            return null;
        }
        return push(best, bestSpeed * keep, x, y, z, vx, vy, vz, s.response());
    }

    /**
     * The change of velocity a flow of {@code speed} through an opening gives an entity, closing
     * {@code response} of the gap between its speed along the flow and the flow's.
     *
     * @return {dx, dy, dz}, or null for none
     */
    public static double[] push(
            Opening o, double speed, double x, double y, double z, double vx, double vy, double vz, double response) {
        double[] d = o.direction(x, y, z);
        if (d == null || speed <= 0) {
            return null;
        }
        double along = vx * d[0] + vy * d[1] + vz * d[2];
        if (along >= speed) {
            return null;
        }
        double k = (speed - along) * response;
        return new double[] {d[0] * k, d[1] * k, d[2] * k};
    }

    private static long key(int sx, int sy, int sz) {
        return ((long) (sx & 0x3FFFFF) << 42) | ((long) (sz & 0x3FFFFF) << 20) | (sy & 0xFFFFF);
    }
}
