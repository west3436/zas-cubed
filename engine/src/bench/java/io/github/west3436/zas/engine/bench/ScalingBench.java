package io.github.west3436.zas.engine.bench;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;

/**
 * NFR-003 and CORE-054: cost follows activity, not world size. Two stations run the same script
 * side by side, in interleaved blocks so drift and JIT noise fall on both; one of them has
 * something extra that should cost nothing: 1,000 idle zones, or twice the loaded chunks that hold
 * no zones. Then world generation and exploration far from any zone must create no zone and no
 * fill.
 */
final class ScalingBench {
    private ScalingBench() {}

    static void run(boolean quick) {
        EngineBench.section("Cost scales with activity (NFR-003, CORE-054)");
        int blocks = quick ? 3 : 20;
        int perBlock = 60;
        System.out.println("Each comparison: two S-10 stations with ops at the 1 s period, warmed up for 2 minutes,"
                + " then " + blocks + " interleaved blocks of " + perBlock + " atmos ticks each.");
        System.out.println();
        System.out.println("| Extra in B | A: server per tick | B: server per tick | Δ server per server tick |"
                + " A: worker per tick | B: worker per tick | Δ worker per server tick | Δ total |");
        System.out.println("|---|---|---|---|---|---|---|---|");
        long[][] burst = new long[1][];
        compare(
                "1,000 idle zones (27 cells, all edges asleep)",
                blocks,
                perBlock,
                st -> burst[0] = addIdleZones(st, Harness.BUDGET_NANOS));
        compare("Twice the loaded chunks with no zones (255 → 510, terrain in each)", blocks, perBlock, st -> {
            terrain(st.h.world, -4, -4, 12, 10);
        });
        System.out.println();
        System.out.println("(In the chunk comparison A gets the same terrain in its own 255 chunks, and B also"
                + " loads 255 more chunks of terrain far away.)");
        System.out.println();
        // The same burst with no budget left at all: every prefetch is skipped, and the adds wait
        // for the sections their classification asks for (CORE-047's deferral).
        Station starved = new Station(20, true);
        starved.pressurize();
        long[] deferred = addIdleZones(starved, 0);
        starved.h.close();
        System.out.println("Burst (CORE-047): 1,000 rooms zoned by 1,000 adds queued in one atmos tick.");
        System.out.println();
        System.out.println(
                "| Prefetch budget | Atmos ticks until all zoned | Worst close | Worst apply | Worst worker tick |");
        System.out.println("|---|---|---|---|---|");
        System.out.printf(
                "| 10 ms (default) | %d | %s | %s | %s |%n",
                burst[0][3], Stats.ms(burst[0][0] / 1e6), Stats.ms(burst[0][1] / 1e6), Stats.ms(burst[0][2] / 1e6));
        System.out.printf(
                "| 0 (always over budget: every prefetch deferred) | %d | %s | %s | %s |%n",
                deferred[3], Stats.ms(deferred[0] / 1e6), Stats.ms(deferred[1] / 1e6), Stats.ms(deferred[2] / 1e6));
        System.out.println();
        worldGeneration(quick);
    }

    interface Setup {
        void apply(Station st);
    }

    private static void compare(String name, int blocks, int perBlock, Setup extra) {
        Station a = new Station(20, true);
        Station b = new Station(20, true);
        boolean chunks = name.startsWith("Twice");
        a.pressurize();
        b.pressurize();
        if (chunks) {
            terrain(a.h.world, -4, -4, 12, 10);
        }
        extra.apply(b);
        if (chunks) {
            b.h.world.loadChunks(40, -4, 56, 10);
            terrain(b.h.world, 40, -4, 56, 10);
        }
        a.h.world.clearDirty();
        b.h.world.clearDirty();
        int warm = 2 * a.ticksPerMinute();
        int ta = 0;
        int tb = 0;
        for (; ta < warm; ta++, tb++) {
            a.scriptedStep(ta);
            b.scriptedStep(tb);
        }
        Stats aServer = new Stats();
        Stats bServer = new Stats();
        Stats aWorker = new Stats();
        Stats bWorker = new Stats();
        for (int block = 0; block < blocks; block++) {
            for (int i = 0; i < perBlock; i++, ta++) {
                a.scriptedStep(ta);
                aServer.add(a.h.closeNanos + a.h.applyNanos);
                aWorker.add(a.h.workerNanos);
            }
            for (int i = 0; i < perBlock; i++, tb++) {
                b.scriptedStep(tb);
                bServer.add(b.h.closeNanos + b.h.applyNanos);
                bWorker.add(b.h.workerNanos);
            }
        }
        double dServer = (bServer.meanMs() - aServer.meanMs()) / 20;
        double dWorker = (bWorker.meanMs() - aWorker.meanMs()) / 20;
        double aTotal = aServer.meanMs() + aWorker.meanMs();
        double bTotal = bServer.meanMs() + bWorker.meanMs();
        System.out.printf(
                "| %s | %s | %s | %s | %s | %s | %s | %+.1f %% |%n",
                name,
                Stats.ms(aServer.meanMs()),
                Stats.ms(bServer.meanMs()),
                signed(dServer),
                Stats.ms(aWorker.meanMs()),
                Stats.ms(bWorker.meanMs()),
                signed(dWorker),
                100 * (bTotal - aTotal) / aTotal);
        a.h.close();
        b.h.close();
    }

    private static String signed(double ms) {
        return (ms < 0 ? "−" : "+") + Stats.ms(Math.abs(ms));
    }

    /**
     * 1,000 sealed 3 × 3 × 3 rooms in a grid away from the station, each zoned by one add, all
     * queued in the same atmos tick, on a station already pressurized. They have no edges, so once
     * zoned they are idle.
     *
     * @param budgetNanos the prefetch budget while the rooms zone
     * @return the burst: the worst close, the worst apply, the worst worker tick (ns) and the atmos
     *     ticks until every room was zoned
     */
    static long[] addIdleZones(Station st, long budgetNanos) {
        BenchWorld w = st.h.world;
        w.loadChunks(12, -4, 27, 10);
        int n = 0;
        for (int i = 0; i < 40; i++) {
            for (int j = 0; j < 25; j++) {
                int x = 200 + i * 5;
                int z = j * 5;
                w.room(x, 64, z, x + 2, 66, z + 2);
            }
        }
        w.clearDirty();
        int before = st.h.zoneCount();
        for (int i = 0; i < 40; i++) {
            for (int j = 0; j < 25; j++) {
                st.h.add(201 + i * 5, 65, 1 + j * 5, Harness.air(27, 1.0), LedgerCategory.API);
                n++;
            }
        }
        int expected = before + n;
        long[] burst = new long[4];
        st.h.budgetNanos = budgetNanos;
        for (int i = 0; i < 100 && st.h.zoneCount() < expected; i++) {
            st.h.step();
            burst[0] = Math.max(burst[0], st.h.closeNanos);
            burst[1] = Math.max(burst[1], st.h.applyNanos);
            burst[2] = Math.max(burst[2], st.h.workerNanos);
            burst[3]++;
        }
        st.h.budgetNanos = Harness.BUDGET_NANOS;
        if (st.h.zoneCount() != expected) {
            throw new IllegalStateException("idle rooms didn't zone: " + st.h.zoneCount() + " zones");
        }
        return burst;
    }

    /**
     * Terrain in every chunk of the square: stone from y = 0 to 47 with a sealed 5 × 5 × 5 cave in
     * each chunk, the kind of enclosed air that must never zone by itself (CORE-051, CORE-054).
     */
    static int terrain(BenchWorld w, int cx0, int cz0, int cx1, int cz1) {
        int changes = 0;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                w.loadChunk(SectionKeys.chunk(cx, cz));
                int x0 = cx * 16;
                int z0 = cz * 16;
                w.box(x0, 0, z0, x0 + 15, 47, z0 + 15, BenchWorld.SOLID);
                w.box(x0 + 5, 20, z0 + 5, x0 + 9, 24, z0 + 9, BenchWorld.AIR);
                changes += 16 * 48 * 16 + 125;
            }
        }
        return changes;
    }

    /**
     * World generation and exploration with the station zoned but quiet: 8 new chunks of terrain
     * with caves each atmos tick, and 500 block edits a tick by "players" in the new terrain.
     * Nothing may be captured, classified or zoned (CORE-054, GEO-011).
     */
    private static void worldGeneration(boolean quick) {
        int ticks = quick ? 10 : 40;
        Station st = new Station(20, false);
        try (Harness h = st.h) {
            st.pressurize();
            h.steps(20);
            int zones = h.zoneCount();
            long captures = h.world.captures;
            long classified = h.classifications.get();
            Stats quiet = new Stats();
            for (int i = 0; i < 20; i++) {
                h.step();
                quiet.add(h.closeNanos + h.applyNanos);
            }
            Stats gen = new Stats();
            Stats genClose = new Stats();
            long changes = 0;
            long writeNanos = 0;
            int nextChunk = 0;
            java.util.Random random = new java.util.Random(54);
            for (int t = 0; t < ticks; t++) {
                long w0 = System.nanoTime();
                for (int k = 0; k < 8; k++, nextChunk++) {
                    int cx = 100 + nextChunk % 32;
                    int cz = 100 + nextChunk / 32;
                    changes += terrain(h.world, cx, cz, cx, cz);
                    h.pipeline.chunkLoaded(SectionKeys.chunk(cx, cz));
                }
                for (int k = 0; k < 500; k++) {
                    int m = random.nextInt(nextChunk);
                    int x = (100 + m % 32) * 16 + random.nextInt(16);
                    int z = (100 + m / 32) * 16 + random.nextInt(16);
                    int y = random.nextInt(48);
                    h.world.set(x, y, z, random.nextBoolean() ? BenchWorld.AIR : BenchWorld.SOLID);
                    changes++;
                }
                writeNanos += System.nanoTime() - w0;
                h.step();
                gen.add(h.closeNanos + h.applyNanos);
                genClose.add(h.closeNanos);
            }
            long newCaptures = h.world.captures - captures;
            long newFills = h.classifications.get() - classified;
            System.out.printf(
                    "World generation and exploration (CORE-054): %d atmos ticks, each loading 8 new chunks of"
                            + " terrain with a sealed cave apiece and making 500 block edits in them, %,d block writes in"
                            + " all, with the station zoned but quiet. Sections captured: **%d**. Enclosure fills:"
                            + " **%d**. Zones: %d before, **%d** after.%n",
                    ticks, changes, newCaptures, newFills, zones, h.zoneCount());
            System.out.println();
            System.out.println("| Measure | Mean | Median | p99 | Max |");
            System.out.println("|---|---|---|---|---|");
            System.out.println("| Server: close plus apply, quiet station | " + quiet.row() + " |");
            System.out.println("| Server: close plus apply, generating | " + gen.row() + " |");
            System.out.printf(
                    "| Writes through the change hook, per atmos tick (includes storing the blocks) | %s | | | |%n",
                    Stats.ms(writeNanos / 1e6 / ticks));
            System.out.println();
            if (newCaptures != 0 || newFills != 0 || h.zoneCount() != zones) {
                System.out.println("**FAIL:** world generation reached the engine.");
                System.out.println();
            }
        }
    }
}
