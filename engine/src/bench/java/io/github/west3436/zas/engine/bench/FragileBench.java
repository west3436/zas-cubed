package io.github.west3436.zas.engine.bench;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.flow.FragileSettings;

/**
 * FLOW-007: block pressure damage costs little while nothing is near the threshold. A room of 8,000
 * cells at 1 atm against a vacuum has windows in its walls. With the option off, or on with no
 * difference above the threshold, each window costs nothing. With a difference elsewhere in the
 * dimension, each window is judged every tick and holds.
 */
final class FragileBench {
    private FragileBench() {}

    private static final int SIZE = 20;
    private static final int[] WINDOWS = {0, 400, 2_400};

    static void run(boolean quick) {
        EngineBench.section("Block pressure damage (FLOW-007)");
        int ticks = quick ? 5 : 40;
        System.out.println("A room of 8,000 cells at 1 atm against a vacuum, with that many fragile blocks in its"
                + " walls (400 is a whole wall, 2,400 all six), and a second, small room far away. Worker: the atmos"
                + " tick's median over " + ticks + " ticks, with the option off; on, with nothing near the threshold;"
                + " and on, with the small room raised to 4 atm so every window is judged every tick. None breaks.");
        System.out.println();
        System.out.println(
                "| Windows | Option off | On, nothing near the threshold | Blocks judged | On, a difference elsewhere | Blocks judged |");
        System.out.println("|---|---|---|---|---|---|");
        for (int windows : WINDOWS) {
            measure(windows, ticks);
        }
        System.out.println();
    }

    private static void measure(int windows, int ticks) {
        BenchWorld w = new BenchWorld(Ambient.VACUUM, BenchWorld.AIR).loadChunks(-3, -3, 7, 7);
        w.room(0, 64, 0, SIZE - 1, 64 + SIZE - 1, SIZE - 1);
        glaze(w, windows);
        w.room(60, 64, 0, 62, 66, 2);
        w.clearDirty();
        try (Harness h = new Harness(w, 20)) {
            h.add(SIZE / 2, 64 + SIZE / 2, SIZE / 2, Harness.air(SIZE * SIZE * SIZE, 1.0), LedgerCategory.API);
            h.add(61, 65, 1, Harness.air(27, 0.001), LedgerCategory.API);
            for (int i = 0; i < 10 && h.zoneCount() < 2; i++) {
                h.step();
            }
            check(h.zoneCount() == 2, "the rooms didn't zone");
            h.steps(3);

            Stats off = new Stats();
            for (int i = 0; i < ticks; i++) {
                h.step();
                off.add(h.workerNanos);
            }
            h.settings = h.settings.withFragile(
                    new FragileSettings(FragileSettings.DEFAULT_THRESHOLD_KPA, FragileSettings.DEFAULT_MAX_BREAKS));
            h.step();
            long startClosed = h.engine.fragileBlocksJudged();
            Stats closed = new Stats();
            for (int i = 0; i < ticks; i++) {
                h.step();
                closed.add(h.workerNanos);
            }
            long judgedClosed = (h.engine.fragileBlocksJudged() - startClosed) / ticks;

            h.add(61, 65, 1, Harness.air(27, 4.0), LedgerCategory.API);
            h.steps(2);
            long startOpen = h.engine.fragileBlocksJudged();
            Stats open = new Stats();
            for (int i = 0; i < ticks; i++) {
                check(h.step().pressureBreaks().isEmpty(), "a window broke");
                open.add(h.workerNanos);
            }
            long judgedOpen = (h.engine.fragileBlocksJudged() - startOpen) / ticks;
            System.out.printf(
                    "| %,d | %s | %s | %,d | %s | %,d |%n",
                    windows,
                    Stats.ms(off.medianMs()),
                    Stats.ms(closed.medianMs()),
                    judgedClosed,
                    Stats.ms(open.medianMs()),
                    judgedOpen);
        }
    }

    /** Puts windows in the room's walls: its east wall first, then the rest, in fixed order. */
    private static void glaze(BenchWorld w, int windows) {
        int n = 0;
        for (int a = 0; a < SIZE && n < windows; a++) {
            for (int b = 0; b < SIZE && n < windows; b++, n++) {
                w.set(SIZE, 64 + a, b, BenchWorld.WINDOW);
            }
        }
        for (int a = 0; a < SIZE && n < windows; a++) {
            for (int b = 0; b < SIZE && n < windows; b++, n++) {
                w.set(-1, 64 + a, b, BenchWorld.WINDOW);
            }
        }
        for (int a = 0; a < SIZE && n < windows; a++) {
            for (int b = 0; b < SIZE && n < windows; b++, n++) {
                w.set(a, 63, b, BenchWorld.WINDOW);
            }
        }
        for (int a = 0; a < SIZE && n < windows; a++) {
            for (int b = 0; b < SIZE && n < windows; b++, n++) {
                w.set(a, 64 + SIZE, b, BenchWorld.WINDOW);
            }
        }
        for (int a = 0; a < SIZE && n < windows; a++) {
            for (int b = 0; b < SIZE && n < windows; b++, n++) {
                w.set(a, 64 + b, -1, BenchWorld.WINDOW);
            }
        }
        for (int a = 0; a < SIZE && n < windows; a++) {
            for (int b = 0; b < SIZE && n < windows; b++, n++) {
                w.set(a, 64 + b, SIZE, BenchWorld.WINDOW);
            }
        }
        check(n == windows, "not enough wall");
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
    }
}
