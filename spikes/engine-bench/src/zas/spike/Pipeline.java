package zas.spike;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Spike S5: the off-thread engine pipeline on an S-10-shaped station.
 *
 * <p>Protocol under test:
 * <ol>
 *   <li>Every period (20 game ticks) the server thread closes a batch: block
 *       changes with section snapshots, chunk load/unload events, queued gas
 *       operations, and the rejections from the previous apply. It records each
 *       chunk's lifecycle generation and hands the batch to the worker.</li>
 *   <li>The worker owns the zone graph. It runs the atmos tick and builds an
 *       immutable snapshot of every zone, keeping an undo copy of each zone's
 *       mixture from just after the geometry phase.</li>
 *   <li>A fixed number of game ticks after submission (APPLY_DELAY) the server
 *       thread applies the result: it publishes the snapshot and completes
 *       futures, except for exchange components that touch a chunk whose
 *       lifecycle generation changed in between (an unload). Those are rejected:
 *       the worker restores them from undo and re-queues their operations at the
 *       start of the next tick (CORE-070, CORE-074).</li>
 * </ol>
 * Applying at a fixed game tick, not when the worker happens to finish, makes
 * the outcome independent of thread timing (CORE-044, CORE-071).
 */
public final class Pipeline {
    static final int GASES = 8;
    static final int O2 = 0;
    static final int N2 = 1;
    static final int CO2 = 2;
    static final double R = 8.314;
    static final double[] CP = {29.4, 29.1, 37.1, 38.6, 33.6, 28.8, 29.1, 35.7}; // J/(mol K), placeholders
    static final int PERIOD = 20;
    static final int APPLY_DELAY = 10;
    static final double[] SHARE_2S = {0.30, 0.40, 0.48, 0.54, 0.60, 0.66};

    // ------------------------------------------------------------ gas math

    /** Moles per gas and temperature; volume lives on the zone. */
    static final class Mix {
        final double[] n = new double[GASES];
        double t = 293.15;

        double heatCapacity() {
            double c = 0;
            for (int g = 0; g < GASES; g++) {
                c += n[g] * CP[g];
            }
            return Math.max(c, 0.0003);
        }

        double total() {
            double s = 0;
            for (double v : n) {
                s += v;
            }
            return s;
        }

        void merge(double[] dn, double dt) {
            double c0 = heatCapacity();
            double c1 = 0;
            for (int g = 0; g < GASES; g++) {
                c1 += dn[g] * CP[g];
            }
            for (int g = 0; g < GASES; g++) {
                n[g] += dn[g];
            }
            if (c1 > 0) {
                t = (c0 * t + c1 * dt) / (c0 + c1);
            }
        }

        /** Removes a fraction of every gas into out (CORE-004, CORE-060). */
        void takeFraction(double f, double[] out) {
            for (int g = 0; g < GASES; g++) {
                out[g] = n[g] * f;
                n[g] -= out[g];
            }
        }

        Mix copy() {
            Mix m = new Mix();
            System.arraycopy(n, 0, m.n, 0, GASES);
            m.t = t;
            return m;
        }
    }

    // ------------------------------------------------------------ engine state (worker-owned)

    static final class Zone {
        final int id;
        double volume;
        Mix mix = new Mix();
        long[] chunks; // chunk keys this zone spans
        double[] chunkVolume; // its volume in each chunk, for persistence shares
        boolean paused;
        boolean inMemory = true;

        Zone(int id) {
            this.id = id;
        }
    }

    static final class Edge {
        final int a;
        final int b; // -1 = ambient (vacuum)
        final int connections;
        boolean sleeping;

        Edge(int a, int b, int connections) {
            this.a = a;
            this.b = b;
            this.connections = connections;
        }
    }

    /** A queued gas operation (API mutation, device or respiration). */
    record Op(long seq, int zone, int gas, double moles, double temp) {}

    /** Server-thread to worker. */
    static final class Batch {
        long tick;
        int[] unloads = new int[0]; // chunk indices
        int[] loads = new int[0];
        int[] breaches = new int[0]; // zone ids gaining an ambient edge this tick
        int[] reseals = new int[0];
        List<Op> ops = new ArrayList<>();
        List<int[]> rejectedComponents = new ArrayList<>();
        long[][] sectionSnapshots = new long[0][]; // copied PalettedContainer storage
        double[][][] loadedShares = new double[0][][]; // per load: zone -> saved share (a copy)
    }

    /** Immutable per-zone record, readable from any thread (API-046). */
    record ZoneSnap(int id, double volume, double[] n, double t, boolean paused, boolean inMemory) {}

    /** Worker to server thread. */
    static final class Result {
        long tick;
        ZoneSnap[] snapshot;
        int[] component; // zone id -> exchange component id
        List<Op> appliedOps = new ArrayList<>();
        double[] ledgerOps = new double[GASES];
        double[] ledgerAmbient = new double[GASES];
        double[] ledgerVoided = new double[GASES];
        double[] ledgerRestore = new double[GASES];
        double[] ledgerReassembly = new double[GASES];
        long workerNanos;
    }

    static final class Engine {
        final List<Zone> zones = new ArrayList<>();
        final List<Edge> edges = new ArrayList<>();
        final int chunkCount;
        final boolean[] chunkLoaded;
        // Undo from the last tick: mixtures after the geometry phase, and ops.
        Mix[] undo;
        List<Op>[] undoOps;
        // Shares received with chunk loads for zones not in memory, until all of
        // the zone's chunks are loaded: zone -> chunk slot -> moles (+ T).
        final double[][][] pending;
        final double[] periodScale = new double[SHARE_2S.length];

        @SuppressWarnings("unchecked")
        Engine(int chunkCount, int zoneCount) {
            this.chunkCount = chunkCount;
            chunkLoaded = new boolean[chunkCount];
            Arrays.fill(chunkLoaded, true);
            pending = new double[zoneCount][][];
            undoOps = new List[zoneCount];
            for (int i = 0; i < SHARE_2S.length; i++) {
                periodScale[i] = 1 - Math.pow(1 - SHARE_2S[i], (PERIOD / 20.0) / 2.0); // CORE-031
            }
        }

        double share(int connections) {
            return periodScale[Math.min(connections, SHARE_2S.length) - 1];
        }

        Result tick(Batch b) {
            long t0 = System.nanoTime();
            Result r = new Result();
            r.tick = b.tick;

            // 0. Rejections from the previous apply: restore and re-queue.
            List<Op> requeued = new ArrayList<>();
            for (int[] comp : b.rejectedComponents) {
                for (int zid : comp) {
                    Zone z = zones.get(zid);
                    for (int g = 0; g < GASES; g++) {
                        r.ledgerRestore[g] += undo[zid].n[g] - z.mix.n[g];
                    }
                    z.mix = undo[zid].copy();
                    if (undoOps[zid] != null) {
                        requeued.addAll(undoOps[zid]);
                    }
                }
            }
            requeued.sort((x, y) -> Long.compare(x.seq, y.seq));

            // 1-2. Geometry and lifecycle. Breaches/reseals stand in for block
            // changes (S4 measures their cost); unloads pause zones; a zone with
            // every chunk unloaded leaves memory, and comes back from its saved
            // shares once every chunk is loaded again.
            decodeSections(b.sectionSnapshots);
            for (int c : b.unloads) {
                chunkLoaded[c] = false;
            }
            for (int i = 0; i < b.loads.length; i++) {
                int c = b.loads[i];
                chunkLoaded[c] = true;
                double[][] shares = b.loadedShares[i];
                for (Zone z : zones) {
                    if (z.inMemory || shares == null || shares[z.id] == null) {
                        continue; // a zone still in memory is authoritative
                    }
                    if (pending[z.id] == null) {
                        pending[z.id] = new double[z.chunks.length][];
                    }
                    for (int slot = 0; slot < z.chunks.length; slot++) {
                        if (z.chunks[slot] == c) {
                            pending[z.id][slot] = shares[z.id];
                        }
                    }
                }
            }
            for (Zone z : zones) {
                boolean all = true;
                boolean any = false;
                for (long c : z.chunks) {
                    all &= chunkLoaded[(int) c];
                    any |= chunkLoaded[(int) c];
                }
                if (!any && z.inMemory) {
                    z.inMemory = false; // shares were saved at unload
                    z.mix = new Mix();
                }
                if (all && !z.inMemory) {
                    Mix m = new Mix();
                    double heat = 0;
                    double cap = 0;
                    for (int slot = 0; slot < z.chunks.length; slot++) {
                        double[] s = pending[z.id][slot];
                        for (int g = 0; g < GASES; g++) {
                            m.n[g] += s[g];
                            r.ledgerReassembly[g] += s[g];
                        }
                        double sc = 0;
                        for (int g = 0; g < GASES; g++) {
                            sc += s[g] * CP[g];
                        }
                        heat += sc * s[GASES];
                        cap += sc;
                    }
                    pending[z.id] = null;
                    m.t = cap > 0 ? heat / cap : 293.15;
                    z.mix = m;
                    z.inMemory = true;
                }
                z.paused = !all;
            }
            for (int zid : b.breaches) {
                edges.add(new Edge(zid, -1, 1));
            }
            for (int zid : b.reseals) {
                edges.removeIf(e -> e.a == zid && e.b == -1);
            }

            // Undo point: after geometry, before gas operations.
            undo = new Mix[zones.size()];
            for (Zone z : zones) {
                undo[z.id] = z.mix.copy();
                undoOps[z.id] = null;
            }

            // 3. Gas operations, in sequence order (re-queued ones first).
            List<Op> ops = new ArrayList<>(requeued.size() + b.ops.size());
            ops.addAll(requeued);
            ops.addAll(b.ops);
            double[] parcel = new double[GASES];
            for (Op op : ops) {
                Zone z = zones.get(op.zone);
                if (z.paused || !z.inMemory) {
                    // GEO-036 / GEO-038: voided into, or supplied from, a paused zone.
                    r.ledgerVoided[op.gas] += op.moles;
                    r.appliedOps.add(op);
                    continue;
                }
                Arrays.fill(parcel, 0);
                if (op.moles >= 0) {
                    parcel[op.gas] = op.moles;
                    z.mix.merge(parcel, op.temp);
                    r.ledgerOps[op.gas] += op.moles;
                } else {
                    double take = Math.min(-op.moles, z.mix.n[op.gas]); // CORE-061
                    z.mix.n[op.gas] -= take;
                    r.ledgerOps[op.gas] -= take;
                }
                if (undoOps[op.zone] == null) {
                    undoOps[op.zone] = new ArrayList<>();
                }
                undoOps[op.zone].add(op);
                r.appliedOps.add(op);
            }

            // 4. Edge exchange; union-find builds the exchange components.
            int[] parent = new int[zones.size()];
            for (int i = 0; i < parent.length; i++) {
                parent[i] = i;
            }
            double[] fromA = new double[GASES];
            double[] fromB = new double[GASES];
            for (Edge e : edges) {
                Zone a = zones.get(e.a);
                if (a.paused || !a.inMemory) {
                    continue;
                }
                double f = share(e.connections);
                if (e.b < 0) {
                    // Volume-aware venting to vacuum (D-21): f scaled by a sharer
                    // volume that grows with the zone, so big zones drain slower.
                    double vs = 4 * 1000.0;
                    double frac = f * vs / (a.volume + vs);
                    a.mix.takeFraction(frac, fromA);
                    for (int g = 0; g < GASES; g++) {
                        r.ledgerAmbient[g] -= fromA[g];
                    }
                    e.sleeping = a.mix.total() < 1e-6;
                    continue;
                }
                Zone bz = zones.get(e.b);
                if (bz.paused || !bz.inMemory) {
                    continue; // the far side shows its last-known state; a fuller model trades with it
                }
                if (e.sleeping && !differs(a.mix, bz.mix)) {
                    continue;
                }
                double va = a.volume;
                double vb = bz.volume;
                double ta = a.mix.t;
                double tb = bz.mix.t;
                a.mix.takeFraction(f * vb / (va + vb), fromA);
                bz.mix.takeFraction(f * va / (va + vb), fromB);
                a.mix.merge(fromB, tb);
                bz.mix.merge(fromA, ta);
                e.sleeping = !differs(a.mix, bz.mix);
                union(parent, e.a, e.b);
            }

            // 5-8. Heat, hotspots, reactions and GC are out of scope for S5.

            // Build the immutable snapshot off-thread.
            r.snapshot = new ZoneSnap[zones.size()];
            r.component = new int[zones.size()];
            for (Zone z : zones) {
                r.snapshot[z.id] = new ZoneSnap(z.id, z.volume, z.mix.n.clone(), z.mix.t, z.paused || !z.inMemory, z.inMemory);
                r.component[z.id] = find(parent, z.id);
            }
            r.workerNanos = System.nanoTime() - t0;
            return r;
        }

        /** Unpacks copied section storage to state ids, as the real geometry phase would. */
        long decodeChecksum;

        void decodeSections(long[][] snaps) {
            short[] states = new short[4096];
            for (long[] s : snaps) {
                for (int i = 0; i < 4096; i++) {
                    states[i] = (short) ((s[i >> 3] >>> ((i & 7) << 3)) & 0xFF);
                }
                decodeChecksum += states[4095];
            }
        }

        static boolean differs(Mix a, Mix b) {
            if (Math.abs(a.t - b.t) > 0.5) {
                return true;
            }
            for (int g = 0; g < GASES; g++) {
                double d = Math.abs(a.n[g] - b.n[g]);
                if (d > 0.1 && d > 0.001 * Math.max(a.n[g], b.n[g])) {
                    return true;
                }
            }
            return false;
        }

        static int find(int[] p, int i) {
            while (p[i] != i) {
                p[i] = p[p[i]];
                i = p[i];
            }
            return i;
        }

        static void union(int[] p, int a, int b) {
            int ra = find(p, a);
            int rb = find(p, b);
            if (ra != rb) {
                p[Math.max(ra, rb)] = Math.min(ra, rb);
            }
        }
    }

    // ------------------------------------------------------------ station (S-10 shape)

    static final int CHUNKS_X = 12;

    /** 100 zones, 40,000 cells, the largest 8,000; doors between neighbours. */
    static Engine buildStation() {
        int zoneCount = 100;
        Engine e = new Engine(CHUNKS_X * CHUNKS_X, zoneCount);
        Random rnd = new Random(10);
        int[] cells = new int[zoneCount];
        cells[0] = 8000;
        int rest = 40_000 - 8000;
        for (int i = 1; i < zoneCount; i++) {
            cells[i] = rest / (zoneCount - 1) + (i == zoneCount - 1 ? rest % (zoneCount - 1) : 0);
        }
        for (int i = 0; i < zoneCount; i++) {
            Zone z = new Zone(i);
            z.volume = cells[i] * 1000.0;
            // ~21 % O2 / 79 % N2 at 101.325 kPa and 293.15 K.
            double n = 101.325 * z.volume / (R * 293.15);
            z.mix.n[O2] = 0.21 * n;
            z.mix.n[N2] = 0.79 * n;
            int gx = i % 10;
            int gz = i / 10;
            int span = i == 0 ? 4 : 1 + rnd.nextInt(2);
            z.chunks = new long[span];
            z.chunkVolume = new double[span];
            for (int s = 0; s < span; s++) {
                int cx = Math.min(CHUNKS_X - 1, gx + (s & 1));
                int cz = Math.min(CHUNKS_X - 1, gz + (s >> 1));
                z.chunks[s] = cx + (long) cz * CHUNKS_X;
                z.chunkVolume[s] = z.volume / span;
            }
            e.zones.add(z);
        }
        for (int i = 0; i < zoneCount; i++) {
            if (i % 10 != 9) {
                e.edges.add(new Edge(i, i + 1, 1 + rnd.nextInt(2)));
            }
            if (i < 90) {
                e.edges.add(new Edge(i, i + 10, 1));
            }
        }
        return e;
    }

    // ------------------------------------------------------------ server-thread side

    static final class Server {
        final Engine engine;
        final ExecutorService worker; // null = inline (single-threaded) mode
        final Random rnd;
        final int[] lifecycleGen;
        // Chunk attachments: chunk -> zone -> saved share. Owned by this thread;
        // authoritative only for zones that are not in memory.
        final double[][][] saved;
        ZoneSnap[] published;
        long seq;
        List<Op> pendingOps = new ArrayList<>();
        List<Integer> pendingUnloads = new ArrayList<>();
        List<Integer> pendingLoads = new ArrayList<>();
        List<Integer> pendingBreaches = new ArrayList<>();
        List<Integer> pendingReseals = new ArrayList<>();
        List<int[]> rejections = new ArrayList<>();
        int dirtySections;
        Future<Result> inFlight;
        Result inlineResult;
        int[] genAtSubmit;
        long submitTick = -1;
        boolean breached;
        boolean usePerChunkRejection = true;

        // Ledger, all categories, for the conservation check (CORE-073).
        final double[] ledger = new double[GASES];
        double[] initial;

        // Measurements.
        long snapshotNanos;
        long applyNanos;
        long waitNanos;
        long maxServerTickNanos;
        long workerNanosTotal;
        long workerNanosMax;
        int ticksApplied;
        int rejectedComponents;
        int rejectedOps;
        long[] serverTickNanos;

        Server(Engine engine, ExecutorService worker, long seed, int ticks) {
            this.engine = engine;
            this.worker = worker;
            this.rnd = new Random(seed);
            this.lifecycleGen = new int[engine.chunkCount];
            this.saved = new double[engine.chunkCount][][];
            serverTickNanos = new long[ticks];
            initial = totals();
        }

        double[] totals() {
            double[] t = new double[GASES];
            for (Zone z : engine.zones) {
                if (z.inMemory) {
                    for (int g = 0; g < GASES; g++) {
                        t[g] += z.mix.n[g];
                    }
                }
            }
            // Saved shares hold the gas only of zones that have left memory.
            for (double[][] c : saved) {
                if (c == null) {
                    continue;
                }
                for (int zid = 0; zid < c.length; zid++) {
                    if (c[zid] != null && !engine.zones.get(zid).inMemory) {
                        for (int g = 0; g < GASES; g++) {
                            t[g] += c[zid][g];
                        }
                    }
                }
            }
            return t;
        }

        void run(int ticks) {
            for (long tick = 0; tick < ticks; tick++) {
                long t0 = System.nanoTime();
                gameplay(tick);
                if (submitTick >= 0 && tick == submitTick + APPLY_DELAY) {
                    apply();
                }
                if (tick % PERIOD == 0) {
                    submit(tick);
                }
                long dt = System.nanoTime() - t0;
                serverTickNanos[(int) tick] = dt;
                maxServerTickNanos = Math.max(maxServerTickNanos, dt);
            }
            // Drain the last tick.
            if (submitTick >= 0) {
                apply();
            }
        }

        /** What happens in the world each game tick (seeded, so both modes see the same). */
        void gameplay(long tick) {
            // 300 devices plus 108 breathers each queue about one op per second.
            int opsThisTick = 408 / PERIOD + (tick % PERIOD < 408 % PERIOD ? 1 : 0);
            for (int i = 0; i < opsThisTick; i++) {
                int zone = rnd.nextInt(engine.zones.size());
                boolean breathe = rnd.nextInt(408) < 108;
                if (breathe) {
                    pendingOps.add(new Op(seq++, zone, O2, -0.0042, 310));
                    pendingOps.add(new Op(seq++, zone, CO2, 0.0035, 310));
                } else {
                    pendingOps.add(new Op(seq++, zone, rnd.nextBoolean() ? O2 : N2, rnd.nextDouble() * 0.5, 293.15));
                }
            }
            // Block edits mark sections dirty (snapshotted at submit).
            if (rnd.nextInt(4) == 0) {
                dirtySections++;
            }
            // A breach of the 8,000-cell zone every 60 s, resealed 10 s later.
            if (tick % 1200 == 600) {
                pendingBreaches.add(0);
                dirtySections += 2;
            }
            if (tick % 1200 == 800) {
                pendingReseals.add(0);
                dirtySections += 2;
            }
            // A player leaves view distance every 2 minutes: a 3x3 chunk corner
            // unloads, and loads again 30 s later. Unloads land at odd ticks so
            // some fall inside the submit-to-apply window.
            if (tick % 2400 == 1205) {
                for (int cx = 0; cx < 3; cx++) {
                    for (int cz = 0; cz < 3; cz++) {
                        unload(cx + cz * CHUNKS_X);
                    }
                }
            }
            if (tick % 2400 == 1805) {
                for (int cx = 0; cx < 3; cx++) {
                    for (int cz = 0; cz < 3; cz++) {
                        pendingLoads.add(cx + cz * CHUNKS_X);
                        lifecycleGen[cx + cz * CHUNKS_X]++;
                    }
                }
            }
        }

        /** ChunkEvent.Unload: save this chunk's shares from the committed state. */
        void unload(int chunk) {
            lifecycleGen[chunk]++;
            pendingUnloads.add(chunk);
            if (published == null) {
                return;
            }
            double[][] perZone = saved[chunk] == null ? new double[engine.zones.size()][] : saved[chunk];
            for (Zone z : engine.zones) {
                for (int s = 0; s < z.chunks.length; s++) {
                    if (z.chunks[s] == chunk && published[z.id].inMemory()) {
                        ZoneSnap snap = published[z.id];
                        double[] share = new double[GASES + 1];
                        for (int g = 0; g < GASES; g++) {
                            share[g] = snap.n()[g] * z.chunkVolume[s] / z.volume;
                        }
                        share[GASES] = snap.t();
                        perZone[z.id] = share;
                    }
                }
            }
            saved[chunk] = perZone;
        }

        void submit(long tick) {
            long t0 = System.nanoTime();
            Batch b = new Batch();
            b.tick = tick;
            b.ops = pendingOps;
            pendingOps = new ArrayList<>();
            b.unloads = pendingUnloads.stream().mapToInt(Integer::intValue).toArray();
            b.loads = pendingLoads.stream().mapToInt(Integer::intValue).toArray();
            b.loadedShares = new double[b.loads.length][][];
            for (int i = 0; i < b.loads.length; i++) {
                double[][] src = saved[b.loads[i]];
                if (src != null) {
                    b.loadedShares[i] = new double[src.length][];
                    for (int z = 0; z < src.length; z++) {
                        b.loadedShares[i][z] = src[z] == null ? null : src[z].clone();
                    }
                }
            }
            b.breaches = pendingBreaches.stream().mapToInt(Integer::intValue).toArray();
            b.reseals = pendingReseals.stream().mapToInt(Integer::intValue).toArray();
            pendingUnloads.clear();
            pendingLoads.clear();
            pendingBreaches.clear();
            pendingReseals.clear();
            b.rejectedComponents = rejections;
            rejections = new ArrayList<>();
            // Section snapshots: PalettedContainer#copy() is a clone of the packed
            // long[] (8 bits per entry here: 512 longs) plus the palette.
            b.sectionSnapshots = new long[dirtySections][];
            for (int i = 0; i < dirtySections; i++) {
                b.sectionSnapshots[i] = SECTION_STORAGE.clone();
            }
            dirtySections = 0;
            genAtSubmit = lifecycleGen.clone();
            submitTick = tick;
            snapshotNanos += System.nanoTime() - t0;
            if (worker == null) {
                inlineResult = engine.tick(b);
            } else {
                inFlight = worker.submit(() -> engine.tick(b));
            }
        }

        void apply() {
            long w0 = System.nanoTime();
            Result r;
            try {
                r = worker == null ? inlineResult : inFlight.get();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            long t0 = System.nanoTime();
            waitNanos += t0 - w0;
            workerNanosTotal += r.workerNanos;
            workerNanosMax = Math.max(workerNanosMax, r.workerNanos);
            ticksApplied++;

            // Which exchange components touch a chunk whose lifecycle changed?
            boolean[] rejectComp = new boolean[r.snapshot.length];
            if (usePerChunkRejection) {
                for (Zone z : engine.zones) {
                    for (long c : z.chunks) {
                        if (lifecycleGen[(int) c] != genAtSubmit[(int) c]) {
                            rejectComp[r.component[z.id]] = true;
                        }
                    }
                }
            }
            ZoneSnap[] next = r.snapshot.clone();
            List<List<Integer>> comps = new ArrayList<>();
            for (int i = 0; i < r.snapshot.length; i++) {
                comps.add(null);
            }
            boolean anyRejected = false;
            for (int zid = 0; zid < next.length; zid++) {
                int c = r.component[zid];
                if (rejectComp[c]) {
                    anyRejected = true;
                    next[zid] = published == null ? next[zid] : published[zid];
                    if (comps.get(c) == null) {
                        comps.set(c, new ArrayList<>());
                    }
                    comps.get(c).add(zid);
                }
            }
            for (List<Integer> c : comps) {
                if (c != null) {
                    rejections.add(c.stream().mapToInt(Integer::intValue).toArray());
                    rejectedComponents++;
                }
            }
            if (anyRejected) {
                for (Op op : r.appliedOps) {
                    if (rejectComp[r.component[op.zone]] && !(next[op.zone].paused())) {
                        rejectedOps++;
                    }
                }
            }
            // Commit the ledger (the restore entries of rejected work arrive
            // with the next result, as ledgerRestore).
            for (int g = 0; g < GASES; g++) {
                ledger[g] += r.ledgerOps[g] + r.ledgerAmbient[g] + r.ledgerRestore[g];
            }
            published = next;
            // Futures for applied ops would complete here; count them as the cost.
            int completed = r.appliedOps.size();
            if (completed < 0) {
                throw new AssertionError();
            }
            applyNanos += System.nanoTime() - t0;
            submitTick = -1;
        }
    }

    static final long[] SECTION_STORAGE = new long[512];

    static {
        Random r = new Random(3);
        for (int i = 0; i < SECTION_STORAGE.length; i++) {
            SECTION_STORAGE[i] = r.nextLong();
        }
    }

    // ------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        int ticks = args.length > 0 ? Integer.parseInt(args[0]) : 20 * 60 * 10; // 10 minutes
        System.out.println("== S5 off-thread pipeline, S-10 shape, " + ticks / 20 + " s of game time");

        // Warm up the JIT on both paths.
        for (int i = 0; i < 3; i++) {
            run(false, 7, 6000, true);
            run(true, 7, 6000, true);
        }

        Server inline = run(false, 42, ticks, true);
        Server threaded = run(true, 42, ticks, true);
        report("inline (single-threaded)", inline);
        report("threaded (one worker)", threaded);
        String h1 = hash(inline);
        String h2 = hash(threaded);
        System.out.println("state hash inline   " + h1);
        System.out.println("state hash threaded " + h2);
        System.out.println("CORE-071 single-threaded equivalence: " + (h1.equals(h2) ? "IDENTICAL" : "DIFFERENT"));

        Server noReject = run(true, 42, ticks, false);
        System.out.println("\nWith rejection disabled (control):");
        conservation(noReject);

        // Snapshot cost per section in isolation.
        long[] sink = null;
        long t0 = System.nanoTime();
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            sink = SECTION_STORAGE.clone();
        }
        double perSection = (System.nanoTime() - t0) / (double) n;
        System.out.printf("%nsection snapshot (clone of 512 longs): %.0f ns each, %d%n", perSection, sink.length);
        Engine e = new Engine(1, 1);
        long[][] snaps = new long[64][];
        Arrays.fill(snaps, SECTION_STORAGE);
        t0 = System.nanoTime();
        for (int i = 0; i < 2000; i++) {
            e.decodeSections(snaps);
        }
        System.out.printf("section decode to state ids (worker): %.1f us each%n", (System.nanoTime() - t0) / 2000.0 / 64 / 1e3);
    }

    static Server run(boolean threaded, long seed, int ticks, boolean reject) throws Exception {
        Engine e = buildStation();
        ExecutorService ex = threaded ? Executors.newSingleThreadExecutor(rn -> {
            Thread t = new Thread(rn, "zas-atmos-worker");
            t.setDaemon(true);
            return t;
        }) : null;
        Server s = new Server(e, ex, seed, ticks);
        s.usePerChunkRejection = reject;
        s.run(ticks);
        if (ex != null) {
            ex.shutdown();
        }
        return s;
    }

    static void report(String name, Server s) {
        long[] t = s.serverTickNanos.clone();
        Arrays.sort(t);
        double mean = Arrays.stream(t).average().orElse(0);
        System.out.printf("%n%s%n", name);
        System.out.printf("  atmos ticks applied %d; rejected components %d, ops re-queued %d%n", s.ticksApplied,
                s.rejectedComponents, s.rejectedOps);
        System.out.printf("  worker tick: mean %.3f ms, max %.3f ms%n", s.workerNanosTotal / 1e6 / s.ticksApplied,
                s.workerNanosMax / 1e6);
        System.out.printf("  server thread per atmos tick: snapshot %.4f ms, apply %.4f ms, waiting on worker %.4f ms%n",
                s.snapshotNanos / 1e6 / s.ticksApplied, s.applyNanos / 1e6 / s.ticksApplied,
                s.waitNanos / 1e6 / s.ticksApplied);
        System.out.printf("  server thread per game tick (incl. simulated gameplay): mean %.4f ms, p99 %.4f ms, max %.4f ms%n",
                mean / 1e6, t[(int) (t.length * 0.99)] / 1e6, t[t.length - 1] / 1e6);
        conservation(s);
    }

    static void conservation(Server s) {
        double[] now = s.totals();
        double worst = 0;
        for (int g = 0; g < GASES; g++) {
            double expect = s.initial[g] + s.ledger[g];
            worst = Math.max(worst, Math.abs(now[g] - expect) / Math.max(1, Math.abs(expect)));
        }
        System.out.printf("  conservation: worst relative unexplained change %.3e (%s)%n", worst,
                worst < 1e-9 ? "OK" : "VIOLATION");
    }

    static String hash(Server s) {
        long h = 1125899906842597L;
        for (ZoneSnap z : s.published) {
            for (double v : z.n()) {
                h = 31 * h + Double.doubleToLongBits(v);
            }
            h = 31 * h + Double.doubleToLongBits(z.t());
        }
        return Long.toHexString(h);
    }
}
