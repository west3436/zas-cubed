package zas.spike;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Zone membership and topology changes: join, merge (CORE-023), split
 * (CORE-024) and local split detection (CORE-025). Membership is stored per
 * section (chunk-aligned, DATA-002); each zone keeps a packed cell list so a
 * merge relabels only the smaller zone.
 */
public final class Zones {
    public static final class Zone {
        public final int id;
        long[] cells = new long[64];
        int size;
        public double volume;
        public boolean alive = true;

        Zone(int id) {
            this.id = id;
        }

        public int size() {
            return size;
        }
    }

    /** Per-section membership: zone id and index in the zone's cell list. */
    static final class Membership {
        final int[] zone = new int[4096];
        final int[] slot = new int[4096];
    }

    public final World world;
    final LongMap<Membership> members = new LongMap<>(1024);
    public final List<Zone> zones = new ArrayList<>();

    /** Work counters for the benchmark. */
    public long visits;
    public long relabels;

    public Zones(World world) {
        this.world = world;
        zones.add(null); // id 0 = unzoned
    }

    public int zoneAt(int x, int y, int z) {
        Membership m = members.get(Pos.section(x, y, z));
        return m == null ? 0 : m.zone[Pos.index(x, y, z)];
    }

    private Membership membership(int x, int y, int z) {
        long k = Pos.section(x, y, z);
        Membership m = members.get(k);
        if (m == null) {
            m = new Membership();
            members.put(k, m);
        }
        return m;
    }

    private void addCell(Zone zn, long p) {
        int x = Pos.x(p);
        int y = Pos.y(p);
        int z = Pos.z(p);
        Membership m = membership(x, y, z);
        int i = Pos.index(x, y, z);
        if (zn.size == zn.cells.length) {
            zn.cells = Arrays.copyOf(zn.cells, zn.size * 2);
        }
        m.zone[i] = zn.id;
        m.slot[i] = zn.size;
        zn.cells[zn.size++] = p;
        zn.volume += Shapes.VOLUME[world.get(x, y, z)];
    }

    private void removeCell(Zone zn, long p, float volume) {
        int x = Pos.x(p);
        int y = Pos.y(p);
        int z = Pos.z(p);
        Membership m = membership(x, y, z);
        int i = Pos.index(x, y, z);
        int slot = m.slot[i];
        long moved = zn.cells[--zn.size];
        zn.cells[slot] = moved;
        Membership mm = membership(Pos.x(moved), Pos.y(moved), Pos.z(moved));
        mm.slot[Pos.index(Pos.x(moved), Pos.y(moved), Pos.z(moved))] = slot;
        m.zone[i] = 0;
        zn.volume -= volume;
    }

    public Zone create(LongSet cells) {
        Zone zn = new Zone(zones.size());
        zones.add(zn);
        zn.cells = new long[Math.max(64, cells.size())];
        for (int i = 0; i < cells.size(); i++) {
            addCell(zn, cells.get(i));
        }
        return zn;
    }

    /** Merges the smaller zone into the larger; returns the survivor. */
    public Zone merge(Zone a, Zone b) {
        if (a == b) {
            return a;
        }
        Zone big = a.size >= b.size ? a : b;
        Zone small = big == a ? b : a;
        for (int i = 0; i < small.size; i++) {
            addCell(big, small.cells[i]);
        }
        relabels += small.size;
        small.size = 0;
        small.alive = false;
        return big;
    }

    /**
     * A solid block was placed at (x,y,z). Removes its cell from its zone and
     * resolves any split. Returns the new zones split off (may be empty).
     */
    public List<Zone> placeSolid(int x, int y, int z, short state) {
        int zid = zoneAt(x, y, z);
        float oldVolume = Shapes.VOLUME[world.get(x, y, z)];
        world.set(x, y, z, state);
        if (zid == 0) {
            return List.of();
        }
        Zone zn = zones.get(zid);
        long p = Pos.pack(x, y, z);
        removeCell(zn, p, oldVolume);
        // Seeds: neighbours in this zone. Any of them may now be cut off.
        long[] seeds = new long[6];
        int k = 0;
        for (int d = 0; d < 6; d++) {
            int nx = x + Pos.DX[d];
            int ny = y + Pos.DY[d];
            int nz = z + Pos.DZ[d];
            if (zoneAt(nx, ny, nz) == zid) {
                seeds[k++] = Pos.pack(nx, ny, nz);
            }
        }
        if (zn.size == 0) {
            zn.alive = false; // CORE-065
            return List.of();
        }
        if (k <= 1) {
            return List.of();
        }
        return splitCheck(zn, Arrays.copyOf(seeds, k));
    }

    /**
     * Interleaved breadth-first searches from each seed. Searches that meet are
     * unioned. Stops as soon as one group remains (connected, or the rest have
     * been split off). An exhausted group is a complete remnant and becomes a
     * new zone, so the cost is bounded by the meeting distance or by the
     * smaller remnants, never by the zone's size.
     */
    private List<Zone> splitCheck(Zone zn, long[] seeds) {
        int k = seeds.length;
        LongSet[] seen = new LongSet[k];
        long[][] queue = new long[k][];
        int[] head = new int[k];
        int[] tail = new int[k];
        int[] parent = new int[k];
        boolean[] done = new boolean[k];
        for (int i = 0; i < k; i++) {
            seen[i] = new LongSet(64);
            seen[i].add(seeds[i]);
            queue[i] = new long[64];
            queue[i][tail[i]++] = seeds[i];
            parent[i] = i;
        }
        List<Zone> out = new ArrayList<>();
        int groups = countGroups(parent, done);
        while (groups > 1) {
            for (int i = 0; i < k && groups > 1; i++) {
                if (done[find(parent, i)] || head[i] == tail[i]) {
                    continue;
                }
                long p = queue[i][head[i]++];
                visits++;
                int x = Pos.x(p);
                int y = Pos.y(p);
                int z = Pos.z(p);
                short st = world.get(x, y, z);
                for (int d = 0; d < 6; d++) {
                    int nx = x + Pos.DX[d];
                    int ny = y + Pos.DY[d];
                    int nz = z + Pos.DZ[d];
                    if (zoneAt(nx, ny, nz) != zn.id || !Shapes.open(st, d, world.get(nx, ny, nz))) {
                        continue;
                    }
                    long q = Pos.pack(nx, ny, nz);
                    if (!seen[i].add(q)) {
                        continue;
                    }
                    for (int j = 0; j < k; j++) {
                        if (j != i && find(parent, j) != find(parent, i) && seen[j].contains(q)) {
                            union(parent, i, j);
                            groups = countGroups(parent, done);
                        }
                    }
                    if (tail[i] == queue[i].length) {
                        queue[i] = Arrays.copyOf(queue[i], tail[i] * 2);
                    }
                    queue[i][tail[i]++] = q;
                }
            }
            // A group whose searches are all exhausted is a separate remnant.
            for (int i = 0; i < k && groups > 1; i++) {
                int g = find(parent, i);
                if (done[g] || !exhausted(parent, head, tail, g)) {
                    continue;
                }
                LongSet cells = new LongSet(256);
                for (int j = 0; j < k; j++) {
                    if (find(parent, j) == g) {
                        for (int c = 0; c < seen[j].size(); c++) {
                            cells.add(seen[j].get(c));
                        }
                    }
                }
                for (int c = 0; c < cells.size(); c++) {
                    long q = cells.get(c);
                    removeCell(zn, q, Shapes.VOLUME[world.get(Pos.x(q), Pos.y(q), Pos.z(q))]);
                }
                relabels += cells.size();
                out.add(create(cells));
                done[g] = true;
                groups = countGroups(parent, done);
            }
        }
        return out;
    }

    private static boolean exhausted(int[] parent, int[] head, int[] tail, int g) {
        for (int j = 0; j < parent.length; j++) {
            if (find(parent, j) == g && head[j] < tail[j]) {
                return false;
            }
        }
        return true;
    }

    private static int countGroups(int[] parent, boolean[] done) {
        int n = 0;
        for (int i = 0; i < parent.length; i++) {
            if (find(parent, i) == i && !done[i]) {
                n++;
            }
        }
        return n;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[Math.max(ra, rb)] = Math.min(ra, rb);
        }
    }

    /**
     * A block at (x,y,z) became air. The new cell joins the adjacent zone; if it
     * touches several zones they merge (the immediate-merge case of CORE-023).
     */
    public Zone openCell(int x, int y, int z) {
        world.set(x, y, z, Shapes.AIR);
        Zone joined = null;
        for (int d = 0; d < 6; d++) {
            int zid = zoneAt(x + Pos.DX[d], y + Pos.DY[d], z + Pos.DZ[d]);
            if (zid == 0) {
                continue;
            }
            Zone zn = zones.get(zid);
            if (joined == null) {
                joined = zn;
                addCell(zn, Pos.pack(x, y, z));
            } else if (zn != joined) {
                joined = merge(joined, zn);
            }
        }
        return joined;
    }
}
