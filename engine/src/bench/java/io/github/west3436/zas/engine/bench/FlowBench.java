package io.github.west3436.zas.engine.bench;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.flow.FlowEdge;
import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.engine.flow.FlowSettings;
import java.util.List;
import java.util.Random;

/**
 * FLOW-006 and FLOW-013: airflow's cost follows the entities near openings, not the zones' size.
 * A room of 1,000, 8,000 or 50,000 cells is breached to vacuum through one hole; with airflow on,
 * each atmos tick exports the edge and its opening, the server builds a {@link FlowField} from it,
 * and every game tick pushes a fixed set of entities near the hole. The entity lookup by box is
 * Minecraft's and isn't here: it costs the entities in the boxes around the openings' sections.
 */
final class FlowBench {
    private FlowBench() {}

    private static final int[][] ROOMS = {{10, 10, 10}, {20, 20, 20}, {50, 20, 50}};
    private static final int[] ENTITIES = {50, 256, 5_000};

    static void run(boolean quick) {
        EngineBench.section("Airflow (FLOW-006, FLOW-013)");
        int ticks = quick ? 5 : 40;
        System.out.println("A room breached to vacuum by one hole, airflow exported at 0 kPa so every tick has the"
                + " edge. Worker: the atmos tick's median with airflow off and on, over " + ticks
                + " ticks; the first tick with airflow on, which searches the edge's openings in the zone. Field:"
                + " building the field from the tick's flow edges, once per atmos tick. Push: one game tick's"
                + " pushes of that many entities placed at random within 9 blocks of the hole, median over the"
                + " same ticks.");
        System.out.println();
        StringBuilder head = new StringBuilder(
                "| Zone cells | Openings | Worker, airflow off | Worker, first tick on | Worker, airflow on | Field |");
        StringBuilder rule = new StringBuilder("|---|---|---|---|---|---|");
        for (int n : ENTITIES) {
            head.append(String.format(" Push, %,d entities |", n));
            rule.append("---|");
        }
        System.out.println(head);
        System.out.println(rule);
        for (int[] size : ROOMS) {
            measure(size, ticks);
        }
        System.out.println();
        System.out.println("The server moves at most `max_entities_per_tick` (256 by default) entities per game tick"
                + " in each dimension, so the 256 column bounds a dimension's push; the 5,000 column is what the cap"
                + " saves. The GameTest `flow_013_the_cap_bounds_entities_per_tick` checks the cap holds.");
        System.out.println();
    }

    private static void measure(int[] size, int ticks) {
        int sx = size[0];
        int sy = size[1];
        int sz = size[2];
        int cells = sx * sy * sz;
        BenchWorld w = new BenchWorld(Ambient.VACUUM, BenchWorld.AIR).loadChunks(-3, -3, 7, 7);
        w.room(0, 64, 0, sx - 1, 64 + sy - 1, sz - 1);
        w.clearDirty();
        int hy = 64 + sy / 2;
        int hz = sz / 2;
        try (Harness h = new Harness(w, 20)) {
            h.add(sx / 2, hy, hz, Harness.air(cells, 1.0), LedgerCategory.API);
            for (int i = 0; i < 10 && h.zoneCount() == 0; i++) {
                h.step();
            }
            check(h.zoneCount() == 1 && h.zoneAt(sx / 2, hy, hz).cellCount() == cells, "the room didn't zone");
            w.set(-1, hy, hz, BenchWorld.AIR); // the hole, in the west wall
            h.step();
            h.steps(3);

            Stats off = new Stats();
            for (int i = 0; i < ticks; i++) {
                h.step();
                off.add(h.workerNanos);
            }
            h.settings = h.settings.withAirflow(0);
            h.step();
            long firstOn = h.workerNanos; // the openings searched: the cache is empty
            h.step();
            Stats on = new Stats();
            Stats field = new Stats();
            Stats[] push = new Stats[ENTITIES.length];
            for (int k = 0; k < push.length; k++) {
                push[k] = new Stats();
            }
            int openings = 0;
            double[][] entities = entities(ENTITIES[ENTITIES.length - 1], hy, hz);
            long zone = h.zoneAt(sx / 2, hy, hz).id();
            FlowSettings s = FlowSettings.DEFAULT;
            double sink = 0;
            for (int i = 0; i < ticks; i++) {
                h.step();
                on.add(h.workerNanos);
                List<FlowEdge> flows = h.pipeline.flows();
                check(!flows.isEmpty(), "no flow edges");
                long t0 = System.nanoTime();
                FlowField f = FlowField.of(flows);
                field.add(System.nanoTime() - t0);
                openings = f.openings().size();
                for (int k = 0; k < ENTITIES.length; k++) {
                    long t1 = System.nanoTime();
                    for (int e = 0; e < ENTITIES[k]; e++) {
                        double[] p = entities[e];
                        double[] d = f.push(p[0], p[1], p[2], 0, 0, 0, p[0] < 0 ? FlowEdge.AMBIENT : zone, 0, s);
                        if (d != null) {
                            sink += d[0];
                        }
                    }
                    push[k].add(System.nanoTime() - t1);
                }
            }
            check(sink != 0, "nothing was pushed");
            StringBuilder row = new StringBuilder(String.format(
                    "| %,d | %d | %s | %s | %s | %s |",
                    cells,
                    openings,
                    Stats.ms(off.medianMs()),
                    Stats.ms(firstOn / 1e6),
                    Stats.ms(on.medianMs()),
                    Stats.ms(field.medianMs())));
            for (Stats p : push) {
                row.append(' ').append(Stats.ms(p.medianMs())).append(" |");
            }
            System.out.println(row);
        }
    }

    /** Points within 9 blocks of the hole at (-1, hy, hz): half inside the room, half outside. */
    private static double[][] entities(int n, int hy, int hz) {
        Random random = new Random(6);
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) {
            double dx;
            double dy;
            double dz;
            do {
                dx = random.nextDouble() * 8.5 + 0.5;
                dy = random.nextDouble() * 6 - 3;
                dz = random.nextDouble() * 6 - 3;
            } while (dx * dx + dy * dy + dz * dz >= 81);
            double x = i % 2 == 0 ? -0.5 + dx : -0.5 - dx;
            out[i] = new double[] {x, hy + 0.5 + dy, hz + 0.5 + dz};
        }
        return out;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
    }
}
