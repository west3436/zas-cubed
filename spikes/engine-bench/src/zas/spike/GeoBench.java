package zas.spike;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Spikes S3 (bounded fills) and S4 (merge, split, local split detection). */
public final class GeoBench {
    static final int CAP = 65_536;

    public static void main(String[] args) {
        System.out.println("JVM " + System.getProperty("java.version") + ", " + Runtime.getRuntime().availableProcessors()
                + " cpus, max heap " + (Runtime.getRuntime().maxMemory() >> 20) + " MiB");
        s3();
        s4();
    }

    // ---------------------------------------------------------------- S3

    static void s3() {
        System.out.println("\n== S3 bounded fills (cap " + CAP + " cells)");

        World sealed = new World();
        sealed.load(-1, -1, -1, 64, 16, 64);
        sealed.room(-1, -1, -1, 64, 16, 64, Shapes.STONE); // interior 64 x 16 x 64 = 65,536
        timeFill("sealed room, 65,536 cells -> ENCLOSED", sealed, Pos.pack(10, 5, 10), 30);

        World open = new World();
        open.load(-40, -1, -40, 200, 319, 200);
        open.fill(-40, 50, -40, 200, 100, 200, Shapes.AIR); // a big open air volume above the room
        open.room(-1, -1, -1, 64, 16, 64, Shapes.STONE);
        open.set(10, 16, 10, Shapes.AIR); // one hole in the ceiling
        open.fill(10, 16, 10, 10, 49, 10, Shapes.AIR); // a shaft up to the open air
        timeFill("room breached to open air -> OPEN at cap", open, Pos.pack(10, 5, 10), 30);

        World mixed = new World();
        mixed.load(-1, -1, -1, 64, 16, 64);
        mixed.room(-1, -1, -1, 64, 16, 64, Shapes.STONE);
        Random r = new Random(1);
        for (int i = 0; i < 20_000; i++) {
            mixed.set(r.nextInt(64), r.nextInt(16), r.nextInt(64), (short) (Shapes.BOTTOM_SLAB + (i & 1) * 2));
        }
        timeFill("sealed room with 20k slabs (mixed shapes)", mixed, Pos.pack(0, 0, 0), 30);

        World small = new World();
        small.load(-1, -1, -1, 3, 3, 3);
        small.room(-1, -1, -1, 3, 3, 3, Shapes.STONE);
        timeFill("3x3x3 room, 27 cells", small, Pos.pack(1, 1, 1), 20_000);

        World station = new World();
        station.load(-1, -1, -1, 40, 10, 40);
        station.room(-1, -1, -1, 40, 10, 40, Shapes.STONE); // 40 x 10 x 40 = 16,000; S-10 largest is 8,000
        station.room(-1, -1, -1, 20, 10, 40, Shapes.STONE);
        timeFill("S-10 largest zone, 20x10x40 = 8,000 cells", station, Pos.pack(5, 5, 5), 200);

        // Fill plus zone creation (the worker-side part of trigger-to-zone latency).
        long[] t = new long[20];
        for (int i = 0; i < t.length; i++) {
            long t0 = System.nanoTime();
            Fill.Result res = Fill.run(sealed, Pos.pack(10, 5, 10), CAP);
            Zones zs = new Zones(sealed);
            zs.create(res.cells);
            t[i] = System.nanoTime() - t0;
        }
        report("fill + zone creation, 65,536 cells", t, 5);

        // GEO-021 open-column shortcut: scan the column above the seed.
        long[] c = new long[20_000];
        for (int i = 0; i < c.length; i++) {
            long t0 = System.nanoTime();
            int y = 60;
            boolean openSky = true;
            while (y < World.MAX_Y) {
                short st = open.get(100, y, 100);
                if (Shapes.COVERAGE[st * 6 + 1] == Shapes.FULL) {
                    openSky = false;
                    break;
                }
                y++;
            }
            if (!openSky) {
                throw new AssertionError();
            }
            c[i] = System.nanoTime() - t0;
        }
        report("GEO-021 column scan, 260 positions (sky)", c, 2000);
    }

    static void timeFill(String name, World w, long seed, int reps) {
        Fill.Result last = null;
        long[] t = new long[reps];
        int warm = Math.max(5, reps / 4);
        for (int i = -warm; i < reps; i++) {
            long t0 = System.nanoTime();
            last = Fill.run(w, seed, CAP);
            long dt = System.nanoTime() - t0;
            if (i >= 0) {
                t[i] = dt;
            }
        }
        double medMs = median(t) / 1e6;
        System.out.printf("%-48s %-10s cells=%6d volume=%9.0f L  median %8.3f ms  p99 %8.3f ms  %5.1f Mcells/s%n", name,
                last.outcome, last.visited, last.volume, medMs, pct(t, 0.99) / 1e6, last.visited / medMs / 1e3);
    }

    // ---------------------------------------------------------------- S4

    static void s4() {
        System.out.println("\n== S4 merge, split, local split detection (50,000-cell zone)");
        World w = new World();
        w.load(-1, -1, -1, 50, 20, 50);
        w.room(-1, -1, -1, 50, 20, 50, Shapes.STONE); // interior 50 x 20 x 50 = 50,000
        Zones zs = new Zones(w);
        Zones.Zone z = zs.create(Fill.run(w, Pos.pack(1, 1, 1), CAP).cells);
        System.out.println("zone cells " + z.size() + " volume " + z.volume + " L");

        // A. Random interior placements that don't split.
        Random r = new Random(7);
        int n = 5_000;
        long[] t = new long[n];
        long[] v = new long[n];
        int splits = 0;
        for (int i = 0; i < n; i++) {
            int x = 2 + r.nextInt(46);
            int y = 2 + r.nextInt(16);
            int zz = 2 + r.nextInt(46);
            if (w.get(x, y, zz) != Shapes.AIR) {
                continue;
            }
            long v0 = zs.visits;
            long t0 = System.nanoTime();
            List<Zones.Zone> out = zs.placeSolid(x, y, zz, Shapes.STONE);
            t[i] = System.nanoTime() - t0;
            v[i] = zs.visits - v0;
            splits += out.size();
        }
        report("A. random interior block, no split (" + splits + " splits)", t, 1000);
        System.out.printf("   visits per placement: median %d, p99 %d, max %d%n", (long) median(v), (long) pct(v, 0.99),
                Arrays.stream(v).max().getAsLong());

        // A2. Same, then remove them again: cell joins (O(1)).
        Random r2 = new Random(7);
        long[] tj = new long[n];
        for (int i = 0; i < n; i++) {
            int x = 2 + r2.nextInt(46);
            int y = 2 + r2.nextInt(16);
            int zz = 2 + r2.nextInt(46);
            if (w.get(x, y, zz) != Shapes.STONE) {
                continue;
            }
            long t0 = System.nanoTime();
            zs.openCell(x, y, zz);
            tj[i] = System.nanoTime() - t0;
        }
        report("A2. removing those blocks (cell joins zone)", tj, 1000);
        System.out.println("   zone cells back to " + z.size());

        // B. Build a wall across x = 25, leaving one hole at a corner until last.
        //    Placements near completion only connect around through the hole.
        int placed = 0;
        long total = 0;
        long worst = 0;
        long worstVisits = 0;
        long totalVisits = 0;
        int lastY = 0;
        int lastZ = 0;
        for (int y = 19; y >= 0; y--) {
            for (int zz = 49; zz >= 0; zz--) {
                if (y == 0 && zz == 0) {
                    lastY = y;
                    lastZ = zz;
                    continue; // the hole
                }
                long v0 = zs.visits;
                long t0 = System.nanoTime();
                List<Zones.Zone> out = zs.placeSolid(25, y, zz, Shapes.STONE);
                long dt = System.nanoTime() - t0;
                if (!out.isEmpty()) {
                    throw new AssertionError("unexpected split while building wall");
                }
                long dv = zs.visits - v0;
                total += dt;
                totalVisits += dv;
                worst = Math.max(worst, dt);
                worstVisits = Math.max(worstVisits, dv);
                placed++;
            }
        }
        System.out.printf("B. wall of %d blocks with a corner hole: total %.1f ms, mean %.3f ms, worst %.3f ms, worst visits %d, mean visits %d%n",
                placed, total / 1e6, total / 1e6 / placed, worst / 1e6, worstVisits, totalVisits / placed);

        // C. Close the hole: a real split into two remnants.
        long v0 = zs.visits;
        long r0 = zs.relabels;
        long t0 = System.nanoTime();
        List<Zones.Zone> out = zs.placeSolid(25, lastY, lastZ, Shapes.STONE);
        long dt = System.nanoTime() - t0;
        System.out.printf("C. closing the hole splits: %.3f ms, %d new zone(s) of %d cells, original keeps %d, visits %d, relabels %d%n",
                dt / 1e6, out.size(), out.isEmpty() ? 0 : out.get(0).size(), z.size(), zs.visits - v0, zs.relabels - r0);

        // D. Break the wall block again: the two halves merge.
        r0 = zs.relabels;
        t0 = System.nanoTime();
        Zones.Zone merged = zs.openCell(25, lastY, lastZ);
        dt = System.nanoTime() - t0;
        System.out.printf("D. reopening merges: %.3f ms, merged zone %d cells, relabels %d%n", dt / 1e6, merged.size(),
                zs.relabels - r0);

        // E. Repeat split/merge to get a stable number.
        long[] ts = new long[50];
        long[] tm = new long[50];
        for (int i = 0; i < ts.length; i++) {
            t0 = System.nanoTime();
            zs.placeSolid(25, lastY, lastZ, Shapes.STONE);
            ts[i] = System.nanoTime() - t0;
            t0 = System.nanoTime();
            zs.openCell(25, lastY, lastZ);
            tm[i] = System.nanoTime() - t0;
        }
        report("E. repeated split of the 50k zone into 24.5k + 25k", ts, 5);
        report("E. repeated merge of 24.5k + 25k", tm, 5);

        // B2. Worst case for a change that leaves the zone connected: the wall is
        //     complete except a hole at one corner and the last gap at the far
        //     corner. Closing the far gap only reconnects through the hole.
        long[] tw = new long[30];
        long wv = 0;
        for (int i = 0; i < tw.length; i++) {
            zs.openCell(25, 19, 49);
            zs.openCell(25, lastY, lastZ); // now two gaps; the zone is one again
            v0 = zs.visits;
            t0 = System.nanoTime();
            List<Zones.Zone> o = zs.placeSolid(25, 19, 49, Shapes.STONE);
            tw[i] = System.nanoTime() - t0;
            wv = zs.visits - v0;
            if (!o.isEmpty()) {
                throw new AssertionError("B2 must not split");
            }
        }
        report("B2. connected only by a 70-block detour (worst case)", tw, 5);
        System.out.println("   visits " + wv + " (both searches run until they meet at the far hole)");

        // F. Small split far from a big zone: a 3x3 alcove cut off by one block.
        World w2 = new World();
        w2.load(-1, -1, -1, 54, 20, 50);
        w2.room(-1, -1, -1, 50, 20, 50, Shapes.STONE);
        w2.room(49, 0, 10, 54, 4, 14, Shapes.STONE); // alcove 4x3x3 outside the room
        w2.set(50, 1, 12, Shapes.AIR);
        w2.set(49, 1, 12, Shapes.AIR); // doorway through the room wall
        Zones zs2 = new Zones(w2);
        zs2.create(Fill.run(w2, Pos.pack(1, 1, 1), CAP).cells);
        long[] tf = new long[200];
        for (int i = 0; i < tf.length; i++) {
            t0 = System.nanoTime();
            List<Zones.Zone> o = zs2.placeSolid(49, 1, 12, Shapes.STONE);
            tf[i] = System.nanoTime() - t0;
            if (o.size() != 1) {
                throw new AssertionError("expected alcove split");
            }
            zs2.openCell(49, 1, 12);
        }
        report("F. cutting a 36-cell alcove off a 50k zone (+ re-merge)", tf, 50);
    }

    // ---------------------------------------------------------------- helpers

    static void report(String name, long[] t, int warm) {
        long[] m = Arrays.stream(Arrays.copyOfRange(t, Math.min(warm, t.length - 1), t.length)).filter(x -> x > 0).toArray();
        System.out.printf("%-60s median %9.4f ms  p99 %9.4f ms  max %9.4f ms%n", name, median(m) / 1e6, pct(m, 0.99) / 1e6,
                Arrays.stream(m).max().getAsLong() / 1e6);
    }

    static double median(long[] t) {
        return pct(t, 0.5);
    }

    static double pct(long[] t, double p) {
        long[] s = t.clone();
        Arrays.sort(s);
        return s[Math.min(s.length - 1, (int) Math.floor(p * (s.length - 1)))];
    }
}
