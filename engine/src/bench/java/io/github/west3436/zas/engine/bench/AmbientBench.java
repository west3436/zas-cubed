package io.github.west3436.zas.engine.bench;

import static io.github.west3436.zas.engine.bench.ZoneBench.check;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.zone.Zone;
import io.github.west3436.zas.engine.zone.ZoneGraph;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * ENV-017: what an ambient provider changing the ambient every atmos tick costs, against the same
 * world under an ambient that holds still. The change is a day and night: the temperature swings
 * through a sine a day long, and an ambient with gas swings its pressure by 1 % with it.
 *
 * <p>Each world runs twice in each mode, in turn, and the second of each is reported, so the JIT has
 * seen both modes before either is measured.
 */
final class AmbientBench {
    /** A day, in atmos ticks: 20 minutes at one atmos tick a second. */
    private static final int DAY = 1200;

    private static final int ROOMS_X = 10;
    private static final int ROOMS_Z = 10;
    private static final int PITCH = 5;
    private static final int Y0 = 64;

    private AmbientBench() {}

    static void run(boolean quick) {
        EngineBench.section("An ambient changing every atmos tick (ENV-017)");
        int warm = quick ? 20 : 600;
        int ticks = quick ? 20 : DAY;
        System.out.println("Each row is " + ticks + " atmos ticks after " + warm + " of warm-up. \"Changing\" gives the"
                + " engine a new ambient every tick: a temperature sine of ±10 K over " + DAY + " ticks, and ±1 % on"
                + " the pressure of an ambient with gas. In the vacuum world only the background temperature changes,"
                + " 100 ± 50 K. The stations are S-10's rooms and doors with no scripted activity. The pinned rooms"
                + " each have a hole, so each has one ambient edge that always exchanges (D-64). Exchange calls and"
                + " equalized groups are per atmos tick.");
        System.out.println();
        System.out.println("| World | Ambient | Exchange calls | Equalized groups | Worker tick: mean | median | p99 |"
                + " max | Server: close plus apply, median |");
        System.out.println("|---|---|---|---|---|---|---|---|---|");
        world("S-10 station, standard air", AmbientBench::station, AmbientBench::air, warm, ticks);
        world("S-10 station, vacuum", AmbientBench::station, AmbientBench::space, warm, ticks);
        world("S-10 and 1,000 idle rooms, standard air", AmbientBench::idleRooms, AmbientBench::air, warm, ticks);
        world("100 pinned rooms with holes, standard air", AmbientBench::pinnedRooms, AmbientBench::air, warm, ticks);
        System.out.println();
    }

    private static Harness station(Ambient ambient) {
        Station st = new Station(20, false, ambient);
        st.pressurize();
        return st.h;
    }

    private static Harness idleRooms(Ambient ambient) {
        Station st = new Station(20, false, ambient);
        st.pressurize();
        ScalingBench.addIdleZones(st, Harness.BUDGET_NANOS);
        return st.h;
    }

    /** Rooms of 27 cells that each exchange with the ambient through a hole in the roof. */
    private static Harness pinnedRooms(Ambient ambient) {
        BenchWorld w = new BenchWorld(ambient, BenchWorld.AIR).loadChunks(-1, -1, 4, 4);
        for (int i = 0; i < ROOMS_X; i++) {
            for (int j = 0; j < ROOMS_Z; j++) {
                w.room(i * PITCH, Y0, j * PITCH, i * PITCH + 2, Y0 + 2, j * PITCH + 2);
            }
        }
        w.clearDirty();
        Harness h = new Harness(w, 20);
        for (int i = 0; i < ROOMS_X; i++) {
            for (int j = 0; j < ROOMS_Z; j++) {
                h.add(i * PITCH + 1, Y0 + 1, j * PITCH + 1, Harness.air(27, 1.05), LedgerCategory.API);
            }
        }
        h.steps(10);
        ZoneGraph graph = h.engine.graph();
        check(graph.zoneCount() == ROOMS_X * ROOMS_Z, "the rooms didn't zone: " + graph.zoneCount());
        for (Zone z : graph.zones()) {
            graph.setPinned(z, true);
        }
        for (int i = 0; i < ROOMS_X; i++) {
            for (int j = 0; j < ROOMS_Z; j++) {
                w.set(i * PITCH + 1, Y0 + 3, j * PITCH + 1, BenchWorld.AIR);
            }
        }
        return h;
    }

    private static void world(
            String name, Function<Ambient, Harness> world, IntFunction<Ambient> changing, int warm, int ticks) {
        Row still = null;
        Row moving = null;
        for (int round = 0; round < 2; round++) {
            still = measure(world, changing, false, warm, ticks);
            moving = measure(world, changing, true, warm, ticks);
        }
        print(name, "still", still);
        print(name, "changing", moving);
    }

    private record Row(Stats worker, Stats server, double exchanges, double equalized) {}

    private static Row measure(
            Function<Ambient, Harness> world, IntFunction<Ambient> changing, boolean change, int warm, int ticks) {
        // Tick 0 of the day is the ambient's middle, so both modes start from the same one.
        try (Harness h = world.apply(changing.apply(0))) {
            ZoneGraph graph = h.engine.graph();
            h.steps(warm);
            Stats worker = new Stats();
            Stats server = new Stats();
            long exchanges = graph.exchangeCount();
            long equalized = graph.equalizeCount();
            for (int k = 1; k <= ticks; k++) {
                if (change) {
                    h.world.ambient = changing.apply(k);
                }
                h.step();
                worker.add(h.workerNanos);
                server.add(h.closeNanos + h.applyNanos);
            }
            check(h.warnings.get() == 0, h.warnings.get() + " engine warnings");
            return new Row(
                    worker,
                    server,
                    (graph.exchangeCount() - exchanges) / (double) ticks,
                    (graph.equalizeCount() - equalized) / (double) ticks);
        }
    }

    private static void print(String name, String mode, Row r) {
        System.out.printf(
                "| %s | %s | %.2f | %.2f | %s | %s |%n",
                name, mode, r.exchanges, r.equalized, r.worker.row(), Stats.ms(r.server.medianMs()));
    }

    private static double sine(int tick) {
        return Math.sin(2 * Math.PI * tick / DAY);
    }

    private static Ambient air(int tick) {
        double scale = 1 + 0.01 * sine(tick);
        TreeMap<String, Double> pressures = new TreeMap<>();
        Ambient.STANDARD_AIR.partialPressures().forEach((gas, p) -> pressures.put(gas, p * scale));
        return new Ambient(pressures, Ambient.STANDARD_AIR.temperature() + 10 * sine(tick));
    }

    private static Ambient space(int tick) {
        return Ambient.vacuum(100 + 50 * sine(tick));
    }
}
