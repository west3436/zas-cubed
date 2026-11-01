package io.github.west3436.zas.engine.bench;

import static io.github.west3436.zas.engine.bench.ZoneBench.check;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.engine.zone.ZoneGraph;

/**
 * GEO-044: opening or closing a door between two zones costs the same whatever the zones' size.
 * The door's cell belongs to the room west of it, so a toggle only adds or removes the non-direct
 * connection to the room east of it (GEO-007): no split check, no new zone, no cell moved.
 */
final class DoorBench {
    private DoorBench() {}

    static void run(boolean quick) {
        EngineBench.section("Door toggles (GEO-044)");
        int warm = quick ? 3 : 10;
        int runs = quick ? 10 : 50;
        // Interior size of each of the two rooms: x, y, z.
        int[][] rooms = {
            {10, 10, 10}, // 1,000
            {20, 20, 20}, // 8,000
            {50, 20, 50}, // 50,000
        };
        System.out.println("Two rooms side by side, joined by one door in their shared wall, opened and closed again "
                + runs + " times after " + warm + " warm-up times; one atmos tick after each change. Each pair is"
                + " alone in its engine, so the worker tick shows everything a toggle costs. The idle tick is the"
                + " same engine with the door left as it is, for reference.");
        System.out.println();
        System.out.println("| Each room | Split checks | Worker: idle tick, median | Worker tick per toggle: mean |"
                + " median | p99 | max | Server: close plus apply, median |");
        System.out.println("|---|---|---|---|---|---|---|---|");
        for (int[] r : rooms) {
            measure(r[0], r[1], r[2], warm, runs);
        }
        System.out.println();
        System.out.println("A toggle should cost the same at every size, close to the idle tick; compare the"
                + " placed block of \"Local edits\", whose split search is bounded but not free (CORE-025).");
        System.out.println();
    }

    private static void measure(int sx, int sy, int sz, int warm, int runs) {
        int cells = sx * sy * sz;
        int x0 = 0;
        int y0 = 64;
        int z0 = 0;
        int wall = x0 + sx;
        BenchWorld w = new BenchWorld(Ambient.VACUUM, BenchWorld.AIR).loadChunks(-3, -3, 7, 7);
        w.room(x0, y0, z0, x0 + sx - 1, y0 + sy - 1, z0 + sz - 1);
        w.room(wall + 1, y0, z0, wall + sx, y0 + sy - 1, z0 + sz - 1);
        int[] door = {wall, y0 + 1, z0 + sz / 2};
        // Closed at first, so each room zones on its own within the fill bound (GEO-020).
        w.set(door[0], door[1], door[2], BenchWorld.DOOR_EAST_CLOSED);
        w.clearDirty();
        try (Harness h = new Harness(w, 20)) {
            h.add(x0 + 1, y0 + 1, z0 + 1, Harness.air(cells + 1, 1.0), LedgerCategory.API);
            h.add(wall + 2, y0 + 1, z0 + 1, Harness.air(cells, 0.5), LedgerCategory.API);
            for (int k = 0; k < 10 && h.zoneCount() < 2; k++) {
                h.step();
            }
            check(h.zoneCount() == 2, "the rooms didn't zone: " + h.zoneCount() + " zones");
            ZoneView west = h.zoneAt(x0 + 1, y0 + 1, z0 + 1);
            ZoneView east = h.zoneAt(wall + 2, y0 + 1, z0 + 1);
            check(west.cellCount() == cells + 1, "the door's cell should join the west room");
            ZoneGraph graph = h.engine.graph();
            long splitChecks = graph.splitCheckCount();
            long nextZone = graph.nextZoneId();
            Stats idle = new Stats();
            Stats toggle = new Stats();
            Stats server = new Stats();
            for (int k = 0; k < warm + runs; k++) {
                boolean measured = k >= warm;
                h.step();
                if (measured) {
                    idle.add(h.workerNanos);
                }
                for (int id : new int[] {BenchWorld.DOOR_EAST, BenchWorld.DOOR_EAST_CLOSED}) {
                    w.set(door[0], door[1], door[2], id);
                    h.step();
                    if (measured) {
                        toggle.add(h.workerNanos);
                        server.add(h.closeNanos + h.applyNanos);
                    }
                    check(h.zoneCount() == 2, "a toggle changed the zones: " + h.zoneCount());
                    check(h.zoneAt(x0 + 1, y0 + 1, z0 + 1).id() == west.id(), "the west zone was rebuilt");
                    check(h.zoneAt(wall + 2, y0 + 1, z0 + 1).id() == east.id(), "the east zone was rebuilt");
                    check(h.zoneAt(door[0], door[1], door[2]).id() == west.id(), "the door left its room");
                }
            }
            long checks = graph.splitCheckCount() - splitChecks;
            check(graph.nextZoneId() == nextZone, "a toggle made a zone");
            check(h.warnings.get() == 0, h.warnings.get() + " engine warnings");
            System.out.printf(
                    "| %,d cells | %d | %s | %s | %s |%n",
                    cells, checks, Stats.ms(idle.medianMs()), toggle.row(), Stats.ms(server.medianMs()));
        }
    }
}
