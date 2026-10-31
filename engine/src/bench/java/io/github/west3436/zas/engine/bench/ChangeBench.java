package io.github.west3436.zas.engine.bench;

/**
 * GEO-011: a block change away from every zone costs a constant amount of work. Each change does
 * the change hook's work ({@link BenchWorld#hook}: a block generation and a dirty bit), and the
 * next batch close drains the dirty sections through the pipeline's locality filter, which drops
 * them. The cost per change must not grow with the number of changes or of zones.
 */
final class ChangeBench {
    private ChangeBench() {}

    static void run(boolean quick) {
        EngineBench.section("Change tracking and the locality filter (GEO-011)");
        int rounds = quick ? 3 : 7;
        System.out.println("Changes to distinct positions far from the station, in rows of 16 along x as a fill"
                + " writes them; then one batch close. Best of " + rounds + " rounds. \"Per change\" is (hook time +"
                + " close time − a quiet close) / changes.");
        System.out.println();
        System.out.println("| Zones | Changes | Dirty sections | Hook per change | Close (drain + filter) |"
                + " Per change, all in |");
        System.out.println("|---|---|---|---|---|---|");
        Station st = new Station(20, false);
        try (Harness h = st.h) {
            st.pressurize();
            h.steps(10);
            measure(h, rounds, quick, false); // warm-up, not printed
            measure(h, rounds, quick, true);
            ScalingBench.addIdleZones(st, Harness.BUDGET_NANOS);
            h.steps(10);
            measure(h, rounds, quick, true);
        }
        System.out.println();
        System.out.println("The geometry-id comparison in front of the hook (`GeometryTable#geometryId` of the old and"
                + " new state) needs Minecraft and isn't included; GEO-040's cache makes it an array lookup.");
        System.out.println();
    }

    private static void measure(Harness h, int rounds, boolean quick, boolean print) {
        int[] counts = quick ? new int[] {1 << 14, 1 << 18} : new int[] {1 << 14, 1 << 18, 1 << 20};
        for (int n : counts) {
            double bestHook = Double.MAX_VALUE;
            double bestClose = Double.MAX_VALUE;
            double bestAll = Double.MAX_VALUE;
            int sections = 0;
            for (int r = 0; r < rounds; r++) {
                Stats quiet = new Stats();
                for (int i = 0; i < 5; i++) {
                    h.step();
                    quiet.add(h.closeNanos);
                }
                double quietClose = quiet.medianMs() * 1e6;
                long t0 = System.nanoTime();
                for (int i = 0; i < n; i++) {
                    int x = 4000 + (i & 255);
                    int z = 4000 + ((i >> 8) & 255);
                    int y = (i >> 16) - 64;
                    h.world.hook(x, y, z);
                }
                long hook = System.nanoTime() - t0;
                sections = h.world.dirtySections();
                long captures = h.world.captures;
                h.step();
                if (h.world.captures != captures) {
                    throw new IllegalStateException("far changes were captured");
                }
                if (h.world.dirtySections() != 0) {
                    throw new IllegalStateException("the close left dirty sections");
                }
                bestHook = Math.min(bestHook, hook / (double) n);
                bestClose = Math.min(bestClose, h.closeNanos);
                bestAll = Math.min(bestAll, (hook + h.closeNanos - quietClose) / n);
            }
            if (!print) {
                continue;
            }
            System.out.printf(
                    "| %,d | %,d | %,d | %.1f ns | %s | %.1f ns |%n",
                    h.zoneCount(), n, sections, bestHook, Stats.ms(bestClose / 1e6), bestAll);
        }
    }
}
