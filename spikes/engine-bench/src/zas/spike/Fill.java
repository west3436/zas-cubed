package zas.spike;

/**
 * Bounded enclosure test (GEO-020): a breadth-first fill through gas-permeable
 * faces, stopped once it exceeds the cap (open), meets an unloaded section
 * (unresolved, GEO-024) or leaves the build height (open, GEO-031).
 */
public final class Fill {
    public enum Outcome { ENCLOSED, OPEN, UNRESOLVED }

    public static final class Result {
        public Outcome outcome;
        public LongSet cells;
        public double volume;
        public int visited;
    }

    private Fill() {}

    public static Result run(World w, long seed, int cap) {
        Result r = new Result();
        LongSet seen = new LongSet(256);
        long[] queue = new long[1024];
        int head = 0;
        int tail = 0;
        seen.add(seed);
        queue[tail++] = seed;
        double volume = 0;
        // Cache the last section: most steps stay inside it.
        long lastKey = Long.MIN_VALUE;
        short[] last = null;
        while (head < tail) {
            long p = queue[head++];
            int x = Pos.x(p);
            int y = Pos.y(p);
            int z = Pos.z(p);
            long key = Pos.section(x, y, z);
            if (key != lastKey) {
                last = w.sections.get(key);
                lastKey = key;
            }
            short st = last[Pos.index(x, y, z)];
            volume += Shapes.VOLUME[st];
            for (int d = 0; d < 6; d++) {
                int nx = x + Pos.DX[d];
                int ny = y + Pos.DY[d];
                int nz = z + Pos.DZ[d];
                if (ny < World.MIN_Y || ny > World.MAX_Y) {
                    if (Shapes.COVERAGE[st * 6 + d] != Shapes.FULL) {
                        return finish(r, Outcome.OPEN, seen, volume);
                    }
                    continue;
                }
                long nkey = Pos.section(nx, ny, nz);
                short[] ns = nkey == lastKey ? last : w.sections.get(nkey);
                if (ns == null) {
                    if (Shapes.COVERAGE[st * 6 + d] != Shapes.FULL) {
                        return finish(r, Outcome.UNRESOLVED, seen, volume);
                    }
                    continue;
                }
                short nst = ns[Pos.index(nx, ny, nz)];
                if (!Shapes.open(st, d, nst)) {
                    continue;
                }
                long np = Pos.pack(nx, ny, nz);
                if (seen.add(np)) {
                    if (seen.size() > cap) {
                        return finish(r, Outcome.OPEN, seen, volume);
                    }
                    if (tail == queue.length) {
                        if (head > queue.length / 2) {
                            System.arraycopy(queue, head, queue, 0, tail - head);
                            tail -= head;
                            head = 0;
                        } else {
                            queue = java.util.Arrays.copyOf(queue, queue.length * 2);
                        }
                    }
                    queue[tail++] = np;
                }
            }
        }
        return finish(r, Outcome.ENCLOSED, seen, volume);
    }

    private static Result finish(Result r, Outcome o, LongSet seen, double volume) {
        r.outcome = o;
        r.cells = seen;
        r.volume = volume;
        r.visited = seen.size();
        return r;
    }
}
