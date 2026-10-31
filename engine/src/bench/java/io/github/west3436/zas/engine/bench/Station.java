package io.github.west3436.zas.engine.bench;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import java.util.ArrayList;
import java.util.List;

/**
 * The engine side of S-10 (00-overview, scenario S-10): in a vacuum dimension, 100 zones holding
 * 40,000 zoned cells, the largest 8,000.
 *
 * <ul>
 *   <li>The big room is 20 × 20 × 20 (8,000 cells), on its own, with no doors.
 *   <li>99 small rooms sit in a 10 × 10 grid sharing walls: 98 of 8 × 5 × 8 (320 cells) and one
 *       of 8 × 10 × 8 (640). Each shared wall holds an open door, so neighbours are separate zones
 *       joined through it. The engine makes each open door's cell a zone of its own (GEO-007), so
 *       the 178 doors add 178 one-cell zones and 356 edges: more zones and edges than S-10's 100
 *       and spike S5's 180, which makes the load heavier, not lighter.
 *   <li>Rooms start at different pressures, so the edges are active at first and fall asleep.
 * </ul>
 *
 * <p>The scripted activity, per second of game time, is spike S5's: 408 gas operations (300
 * devices and 108 breathers, as engine operations; the devices themselves are platform work and
 * not here) when {@code withOps} is set, and every 60 s a breach of the big room that is resealed
 * and re-pressurized 10 s later.
 */
final class Station {
    static final int ROOMS = 99;
    static final int BIG_X0 = 0;
    static final int BIG_Y0 = 64;
    static final int BIG_Z0 = 0;
    static final int BIG_SIZE = 20;
    static final int GRID_X0 = 30;
    static final int GRID_Y0 = 64;
    static final int PITCH = 9;
    /** The block knocked out of the big room's east wall to breach it. */
    static final int[] BREACH = {BIG_X0 + BIG_SIZE, BIG_Y0 + 6, BIG_Z0 + 10};

    final Harness h;
    final boolean withOps;
    /** One interior point per small room, then the big room's. */
    final List<int[]> points = new ArrayList<>();

    final List<Integer> roomCells = new ArrayList<>();
    int doors;
    private boolean exploded;
    private final List<int[]> blast = new ArrayList<>();

    Station(int periodTicks, boolean withOps) {
        BenchWorld world = new BenchWorld(Ambient.VACUUM, BenchWorld.AIR).loadChunks(-4, -4, 12, 10);
        this.h = new Harness(world, periodTicks);
        this.withOps = withOps;
        build(world);
    }

    private static int slotX(int slot) {
        return GRID_X0 + (slot % 10) * PITCH;
    }

    private static int slotZ(int slot) {
        return (slot / 10) * PITCH;
    }

    private static int slotHeight(int slot) {
        return slot == ROOMS - 1 ? 10 : 5;
    }

    private void build(BenchWorld w) {
        for (int s = 0; s < ROOMS; s++) {
            int x = slotX(s);
            int z = slotZ(s);
            int height = slotHeight(s);
            w.room(x, GRID_Y0, z, x + 7, GRID_Y0 + height - 1, z + 7);
            points.add(new int[] {x + 3, GRID_Y0 + 2, z + 3});
            roomCells.add(64 * height);
        }
        // Doors last, so no later room's wall overwrites one.
        for (int s = 0; s < ROOMS; s++) {
            int east = s + 1;
            if (s % 10 != 9 && east < ROOMS) {
                w.set(slotX(s) + 8, GRID_Y0 + 1, slotZ(s) + 3, BenchWorld.DOOR);
                doors++;
            }
            int south = s + 10;
            if (south < ROOMS) {
                w.set(slotX(s) + 3, GRID_Y0 + 1, slotZ(s) + 8, BenchWorld.DOOR);
                doors++;
            }
        }
        w.room(BIG_X0, BIG_Y0, BIG_Z0, BIG_X0 + BIG_SIZE - 1, BIG_Y0 + BIG_SIZE - 1, BIG_Z0 + BIG_SIZE - 1);
        points.add(new int[] {BIG_X0 + 10, BIG_Y0 + 10, BIG_Z0 + 10});
        roomCells.add(BIG_SIZE * BIG_SIZE * BIG_SIZE);
    }

    int zonedCells() {
        return roomCells.stream().mapToInt(Integer::intValue).sum();
    }

    /** Pressurizes every room and runs until the zones exist; returns the atmos ticks it took. */
    int pressurize() {
        for (int i = 0; i < points.size(); i++) {
            int[] p = points.get(i);
            double factor = i == ROOMS ? 1.0 : 0.85 + 0.05 * (i % 6);
            h.add(p[0], p[1], p[2], Harness.air(roomCells.get(i), factor), LedgerCategory.API);
        }
        int ticks = 0;
        while (!allZoned() && ticks < 20) {
            h.step();
            ticks++;
        }
        if (!allZoned()) {
            throw new IllegalStateException("not every room zoned after " + ticks + " atmos ticks");
        }
        return ticks;
    }

    /** Whether every room is zoned. */
    boolean allZoned() {
        for (int[] p : points) {
            if (h.zoneAt(p[0], p[1], p[2]) == null) {
                return false;
            }
        }
        return true;
    }

    /** Atmos ticks per 60 s of game time. */
    int ticksPerMinute() {
        return 60 * 20 / h.periodTicks;
    }

    /**
     * Queues this atmos tick's scripted work, then runs it. {@code t} counts atmos ticks since the
     * script began.
     */
    void scriptedStep(int t) {
        int perMinute = ticksPerMinute();
        int phase = Math.floorMod(t, perMinute);
        if (phase == 0) {
            breach();
        } else if (phase == perMinute / 6) {
            reseal();
        }
        if (withOps) {
            queueOps(t);
        }
        h.step();
    }

    /** The operations of one atmos tick: 408 per second, scaled by the period. */
    private void queueOps(int t) {
        int devices = 300 * h.periodTicks / 20;
        int breathers = 108 * h.periodTicks / 20;
        for (int k = 0; k < devices; k++) {
            int room = (k * 7 + t) % ROOMS;
            int[] p = points.get(room);
            double[] gas = new double[Harness.REGISTRY.size()];
            // Vents and scrubbers in balance: half add air, half take as much away.
            gas[Harness.N2] = 0.79;
            gas[Harness.O2] = 0.21;
            if ((k & 1) == 0) {
                h.add(p[0], p[1], p[2], gas, LedgerCategory.DEVICE);
            } else {
                h.remove(p[0], p[1], p[2], gas, LedgerCategory.DEVICE);
            }
        }
        for (int k = 0; k < breathers; k++) {
            int room = (k * 13 + t) % (ROOMS + 1);
            int[] p = points.get(room);
            double[] o2 = new double[Harness.REGISTRY.size()];
            o2[Harness.O2] = 0.01;
            double[] co2 = new double[Harness.REGISTRY.size()];
            co2[Harness.CO2] = 0.01;
            h.remove(p[0], p[1], p[2], o2, LedgerCategory.LIFE);
            h.add(p[0], p[1], p[2], co2, LedgerCategory.LIFE);
        }
    }

    private void breach() {
        h.world.set(BREACH[0], BREACH[1], BREACH[2], BenchWorld.AIR);
    }

    /** Closes the hole and tops the big room back up to standard pressure. */
    private void reseal() {
        h.world.set(BREACH[0], BREACH[1], BREACH[2], BenchWorld.SOLID);
        int[] p = points.get(ROOMS);
        double[] full = Harness.air(BIG_SIZE * BIG_SIZE * BIG_SIZE, 1.0);
        ZoneView z = h.zoneAt(p[0], p[1], p[2]);
        double have = z == null ? 0 : z.totalMoles();
        double want = full[Harness.N2] + full[Harness.O2];
        if (want > have) {
            double f = (want - have) / want;
            full[Harness.N2] *= f;
            full[Harness.O2] *= f;
            h.add(p[0], p[1], p[2], full, LedgerCategory.API);
        }
    }

    /**
     * An explosion breaching the big room (NFR-002): a ball of radius 3 centred on its east wall
     * becomes air. Only the wall's blocks change (the room is empty), so it opens a disc of 29
     * blocks at once.
     *
     * @return the blocks that changed
     */
    int explode() {
        int cx = BIG_X0 + BIG_SIZE;
        int cy = BIG_Y0 + 10;
        int cz = BIG_Z0 + 10;
        blast.clear();
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int y = cy - 3; y <= cy + 3; y++) {
                for (int z = cz - 3; z <= cz + 3; z++) {
                    int dx = x - cx;
                    int dy = y - cy;
                    int dz = z - cz;
                    if (dx * dx + dy * dy + dz * dz <= 9) {
                        blast.add(new int[] {x, y, z});
                        h.world.set(x, y, z, BenchWorld.AIR);
                    }
                }
            }
        }
        exploded = true;
        int changed = 0;
        for (int[] b : blast) {
            if (b[0] == cx) {
                changed++;
            }
        }
        return changed;
    }

    /** Rebuilds what the explosion took: the wall solid again, the inside air. */
    void repairExplosion() {
        if (!exploded) {
            return;
        }
        for (int[] b : blast) {
            // The wall plane is solid again; the cells on either side were air before.
            h.world.set(b[0], b[1], b[2], b[0] == BIG_X0 + BIG_SIZE ? BenchWorld.SOLID : BenchWorld.AIR);
        }
        exploded = false;
    }
}
