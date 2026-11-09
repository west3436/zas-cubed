package io.github.west3436.zas.s10;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.engine.zone.ExchangeSettings;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The enclosure cap step (owner, 2026-11-08; GEO-020, D-23): how far {@code [atmos]
 * enclosure_max_cells} can go. It runs after the scenario and the survey, which keep the default
 * cap. The station is let go first (its players leave and its chunks unload), so what's left on
 * the server thread is the halls' own work. Then each hall, 16 blocks high and 64 to 80 blocks
 * square inside, is tested in {@code zas_s10:halls}, a void dimension of its own with a vacuum
 * ambient, with the cap set to the hall's cells and one player by it. For each hall, in order:
 *
 * <ol>
 *   <li>A baseline: the hall built and not zoned, the player outside it.
 *   <li>b. The worst open fill: a hall like it 512 blocks away with a notch in one wall, so one cell
 *       over the cap, roofed so the column check (GEO-021) can't answer; an add inside it must
 *       come out open. Each repeat adds at a cell no add has used, which GEO-027 hasn't cached.
 *   <li>f. The heap of the halls' engine and platform, before the hall zones.
 *   <li>a. Zoning: one add in the hall's middle zones all of it.
 *   <li>e. Idle: the zoned hall with nothing happening in it.
 *   <li>f. The heap again, with the hall zoned.
 *   <li>d. Split and merge: a wall across the middle, built with one block missing; placing that
 *       block splits the hall (CORE-024), taking it out again merges the halves (CORE-023).
 *   <li>c. Breach and reseal, under ZAS's venting (owner, 2026-11-08), so the hall empties: a 3 × 3
 *       hole in the middle of the roof opens it to the vacuum; it vents until its zone dissolves
 *       (CORE-076), the hole is filled, and an add must zone the whole hall again. The venting
 *       setting goes back afterwards.
 *   <li>e. Active: a generator, an area air controller and a vent go in, the hall is below the
 *       controller's target so the vent blows throughout, and the player stands inside, breathing.
 * </ol>
 *
 * <p>Each server tick's atmos time is the bench's whole count, all of {@code zas} and {@code
 * zas_atmos}. The halls' worker ticks are timed by their own phase listener. Server thread.
 */
final class CapStep {
    static final ResourceKey<Level> HALLS =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_s10", "halls"));
    /** The halls' inside height, in blocks. */
    static final int HEIGHT = 16;
    /** The fraction of standard pressure a hall is filled to: below the controller's target. */
    static final double FILL_FRACTION = 0.9;
    /** Ticks an event keeps recording after it's done, for what follows from it. */
    static final int TAIL = 40;

    private static final int TIMEOUT = 60 * 20;
    /** How long a breached hall may take to empty and dissolve: about 30 s under ZAS's venting. */
    private static final int DISSOLVE_TIMEOUT = 180 * 20;

    private static final int SITE_SPACING = 1024;
    private static final int OVER_CAP_Z = 512;
    private static final int PLAYER = 9;

    /** The halls' inside sides, in blocks. */
    static List<Integer> sides(boolean quick) {
        return quick ? List.of(64, 80) : List.of(64, 68, 72, 76, 80);
    }

    /** One measured event: an action, the ticks until it's done, and {@link #TAIL} more. */
    static final class Event {
        final String name;
        final Window window;
        long actionNanos;
        long doneNanos = -1;
        long addedAtBatch = -1;
        long firstBatch = -1;
        long doneBatch = -1;
        String outcome = "timed out";
        final List<String> facts = new ArrayList<>();

        Event(String name, long tick) {
            this.name = name;
            this.window = new Window(name, tick);
        }

        boolean done() {
            return doneNanos >= 0;
        }

        /** Milliseconds from the action to done, or NaN. */
        double msToDone() {
            return done() ? (doneNanos - actionNanos) / 1e6 : Double.NaN;
        }

        /** Atmos ticks from the batch that carried an add to the one that completed it, or -1. */
        long rounds() {
            return firstBatch < 0 || doneBatch < 0 ? -1 : doneBatch - firstBatch + 1;
        }
    }

    /** What one hall did. */
    static final class Hall {
        final int side;
        /** The lowest corner of its inside. */
        final BlockPos inside;
        /** The lowest corner of the inside of its twin with one cell more. */
        final BlockPos overCap;

        Window baseline;
        Window idle;
        Window active;
        final List<Event> openFills = new ArrayList<>();
        Event zoning;
        final List<Event> splits = new ArrayList<>();
        final List<Event> merges = new ArrayList<>();
        Event breach;
        /**
         * The breach window's server tick that applied the dissolve, and its worker tick (the last
         * to finish before it): indexes into the window's ticks and worker rows, or -1.
         */
        int dissolveTick = -1;

        int dissolveWorker = -1;
        /** Seconds from the hole to the dissolve, or NaN. */
        double dissolveSeconds = Double.NaN;
        /** The cells the hall zoned again with after the reseal. */
        int rezoneCells;

        AtmosHeap heapBefore;
        AtmosHeap heapAfter;
        int zoneCells;
        String vent = "not run";
        final List<String> notes = new ArrayList<>();

        Hall(int index, int side) {
            this.side = side;
            this.inside = new BlockPos(index * SITE_SPACING, Station.FLOOR + 1, 0);
            this.overCap = inside.offset(0, 0, OVER_CAP_Z);
        }

        int cells() {
            return side * side * HEIGHT;
        }

        String label() {
            return String.format("%d × %d × %d", side, side, HEIGHT);
        }
    }

    /** One part of the script: begins once, then is polled each tick until it's done. */
    private abstract static class Step {
        final Hall hall;
        final String name;
        /** Runs even after its hall failed: letting go of the hall's chunks. */
        final boolean always;

        long began;

        Step(Hall hall, String name, boolean always) {
            this.hall = hall;
            this.name = name;
            this.always = always;
        }

        void begin() {}

        /** Whether it's done, {@code t} server ticks after it began. */
        abstract boolean poll(long t);
    }

    private final Bench bench;
    private final int window;
    private final int settle;
    private final int repeats;
    final List<Hall> halls = new ArrayList<>();
    final int configuredCap;
    final ExchangeSettings.Venting configuredVenting;
    final Map<String, String> notes = new java.util.LinkedHashMap<>();
    ServerLevel level;
    DimensionAtmos atmos;
    private WorkerTimes worker;
    private ServerPlayer player;
    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private Step current;
    private long now;
    private boolean finished;

    // What the tick in progress records into, and the event whose batches it counts.
    private Window recording;
    private Event event;
    private boolean primed;
    private long closedBefore;
    private boolean inFlightBefore;

    CapStep(Bench bench) {
        this.bench = bench;
        boolean quick = bench.settings.quick();
        this.window = quick ? 5 * 20 : 30 * 20;
        this.settle = quick ? 5 * 20 : 20 * 20;
        this.repeats = quick ? 1 : 3;
        this.configuredCap = ZasServerConfig.ENCLOSURE_MAX_CELLS.get();
        this.configuredVenting = ZasServerConfig.VENTING.get();
        List<Integer> sides = sides(quick);
        for (int i = 0; i < sides.size(); i++) {
            halls.add(new Hall(i, sides.get(i)));
        }
        steps.add(once(null, "let the station go", this::letStationGo));
        steps.add(until(null, "the station unloaded", this::stationUnloaded, TIMEOUT));
        for (Hall h : halls) {
            plan(h);
        }
        steps.add(always(null, "put the cap and the venting back", () -> {
            ZasServerConfig.ENCLOSURE_MAX_CELLS.set(configuredCap);
            ZasServerConfig.VENTING.set(configuredVenting);
        }));
    }

    boolean finished() {
        return finished;
    }

    // ---- The script --------------------------------------------------------------------------

    private void plan(Hall h) {
        steps.add(once(h, "set up", () -> setUp(h)));
        steps.add(until(h, "the last hall unloaded", () -> atmos.published().zoneCount() == 0, 30 * 20));
        steps.add(pause(h, "settle", settle));
        steps.add(costWindow(h, "baseline", w -> h.baseline = w));
        for (int k = 0; k < repeats; k++) {
            BlockPos seed = h.overCap.offset(h.side / 2 + 2 * k, HEIGHT / 2, h.side / 2 + 2 * k);
            steps.add(add(h, "open fill " + (k + 1), h.openFills::add, seed, air(1, 1)));
            steps.add(pause(h, "settle", 20));
        }
        steps.add(once(h, "let go of the twin", () -> force(h.overCap, h.side, false)));
        steps.add(pause(h, "settle", 100));
        steps.add(once(h, "heap before", () -> h.heapBefore = heap()));
        steps.add(add(h, "zoning", e -> h.zoning = e, middle(h), air(h.cells(), FILL_FRACTION)));
        steps.add(once(h, "zoned cells", () -> h.zoneCells = cells(left(h))));
        steps.add(pause(h, "settle", 100));
        steps.add(costWindow(h, "idle", w -> h.idle = w));
        steps.add(once(h, "heap after", () -> h.heapAfter = heap()));
        steps.add(once(h, "a dividing wall less one block", () -> divider(h, true)));
        int less = h.cells() - (h.side * HEIGHT - 1);
        steps.add(until(h, "the hall zoned round the wall", () -> cells(left(h)) == less, TIMEOUT));
        steps.add(pause(h, "settle", 100));
        for (int k = 0; k < repeats; k++) {
            steps.add(event(h, "split " + (k + 1), h.splits::add, e -> setBlock(gap(h), wall()), e -> {
                ZoneView a = zone(left(h));
                ZoneView b = zone(right(h));
                return a != null && b != null && a.id() != b.id();
            }));
            steps.add(pause(h, "settle", 20));
            steps.add(event(
                    h, "merge " + (k + 1), h.merges::add, e -> setBlock(gap(h), Blocks.AIR.defaultBlockState()), e -> {
                        ZoneView a = zone(left(h));
                        ZoneView b = zone(right(h));
                        return a != null && b != null && a.id() == b.id();
                    }));
            steps.add(pause(h, "settle", 20));
        }
        steps.add(once(h, "take the wall down", () -> divider(h, false)));
        steps.add(until(h, "the hall whole again", () -> cells(left(h)) == h.cells(), TIMEOUT));
        steps.add(pause(h, "settle", 100));
        steps.add(breach(h));
        steps.add(always(h, "put the venting back", () -> ZasServerConfig.VENTING.set(configuredVenting)));
        steps.add(pause(h, "settle", 100));
        steps.add(once(h, "devices and the player in", () -> devicesIn(h)));
        steps.add(pause(h, "settle", settle));
        steps.add(active(h));
        steps.add(always(h, "let go of the hall", () -> force(h.inside, h.side, false)));
    }

    /** One server tick of the step, before the game's own. */
    void tick(long gameTick) {
        now = gameTick;
        if (current == null) {
            current = steps.poll();
            if (current == null) {
                finished = true;
                return;
            }
            current.began = now;
            S10Mod.LOGGER.info(
                    "[S-10] cap step: {}{}", current.hall == null ? "" : current.hall.label() + ": ", current.name);
            try {
                current.begin();
            } catch (RuntimeException e) {
                failed(e);
                return;
            }
        }
        try {
            if (current.poll(now - current.began)) {
                current = null;
            }
        } catch (RuntimeException e) {
            failed(e);
        }
    }

    /** Drops what's left of a failed hall's script but letting go of its chunks. */
    private void failed(RuntimeException e) {
        Step s = current;
        S10Mod.LOGGER.error("[S-10] cap step failed at {}", s.name, e);
        String where = (s.hall == null ? "" : s.hall.label() + ", ") + s.name;
        if (s.hall != null) {
            s.hall.notes.add("Failed at " + s.name + ": " + e);
            steps.removeIf(x -> x.hall == s.hall && !x.always);
        } else {
            notes.put("Failure", where + ": " + e);
            steps.removeIf(x -> !x.always);
        }
        recording = null;
        event = null;
        current = null;
    }

    // ---- Recording ---------------------------------------------------------------------------

    /** The start of a server tick, before the script's step. */
    void before() {
        DimensionPipeline p = atmos == null ? null : atmos.pipeline();
        closedBefore = p == null ? Long.MIN_VALUE : p.closedTick();
        inFlightBefore = atmos != null && atmos.isInFlight();
        primed = true;
    }

    /** The end of a server tick: records it into the window or event in progress. */
    void after(long atmosNanos, long startNanos, long wallNanos, long cpuNanos, long[] parts, int partCount) {
        if (!primed) {
            return;
        }
        primed = false;
        DimensionPipeline p = atmos == null ? null : atmos.pipeline();
        boolean closed = p != null && p.closedTick() != closedBefore;
        boolean applied = inFlightBefore && (closed || !atmos.isInFlight());
        List<long[]> ticks = worker == null ? List.of() : worker.drain();
        Event e = event;
        if (e != null && closed && e.addedAtBatch >= 0 && e.firstBatch < 0 && p.closedTick() > e.addedAtBatch) {
            e.firstBatch = p.closedTick();
        }
        Window w = recording;
        if (w == null) {
            return;
        }
        w.tick(atmosNanos, startNanos, wallNanos, cpuNanos, parts, partCount);
        w.keepCalls(atmosNanos);
        if (closed) {
            w.close.add(atmos.lastCloseNanos());
            w.batchSections.add(p.lastBatchSections());
        }
        if (applied) {
            w.apply.add(atmos.lastApplyNanos());
        }
        w.batchWork(closed ? atmos.lastCloseNanos() : -1, applied ? atmos.lastApplyNanos() : -1);
        w.worker.addAll(ticks);
    }

    // ---- Steps -------------------------------------------------------------------------------

    private Step once(Hall h, String name, Runnable run) {
        return new Step(h, name, false) {
            @Override
            void begin() {
                run.run();
            }

            @Override
            boolean poll(long t) {
                return true;
            }
        };
    }

    private Step always(Hall h, String name, Runnable run) {
        return new Step(h, name, true) {
            @Override
            void begin() {
                run.run();
            }

            @Override
            boolean poll(long t) {
                return true;
            }
        };
    }

    private Step pause(Hall h, String name, int ticks) {
        return new Step(h, name, false) {
            @Override
            boolean poll(long t) {
                return t >= ticks;
            }
        };
    }

    private Step until(Hall h, String name, BooleanSupplier ok, int timeout) {
        return new Step(h, name, false) {
            @Override
            boolean poll(long t) {
                if (ok.getAsBoolean()) {
                    return true;
                }
                if (t >= timeout) {
                    String note = String.format("%s: not after %d s", name, t / 20);
                    if (h == null) {
                        notes.put(name, note);
                    } else {
                        h.notes.add(note);
                    }
                    return true;
                }
                return false;
            }
        };
    }

    /** Records every tick for {@link #window} ticks. */
    private Step costWindow(Hall h, String name, Consumer<Window> keep) {
        return new Step(h, name, false) {
            private Window w;

            @Override
            void begin() {
                w = new Window(h.side + " " + name, now);
                keep.accept(w);
                recording = w;
            }

            @Override
            boolean poll(long t) {
                if (t >= window) {
                    w.endNanos = System.nanoTime();
                    recording = null;
                    return true;
                }
                return false;
            }
        };
    }

    /** Makes an action and records until it's done, then {@link #TAIL} ticks more. */
    private Step event(Hall h, String name, Consumer<Event> keep, Consumer<Event> act, Predicate<Event> isDone) {
        return new Step(h, name, false) {
            private Event e;
            private long doneAt = -1;

            @Override
            void begin() {
                e = new Event(name, now);
                keep.accept(e);
                recording = e.window;
                event = e;
                e.actionNanos = System.nanoTime();
                act.accept(e);
            }

            @Override
            boolean poll(long t) {
                if (doneAt < 0) {
                    if (isDone.test(e)) {
                        if (!e.done()) {
                            e.doneNanos = System.nanoTime();
                            e.outcome = "done";
                        }
                        doneAt = t;
                    } else if (t >= TIMEOUT) {
                        doneAt = t;
                    }
                }
                if (doneAt >= 0 && t >= doneAt + TAIL) {
                    e.window.endNanos = System.nanoTime();
                    recording = null;
                    event = null;
                    return true;
                }
                return false;
            }
        };
    }

    /** An event whose action is one API add, done when the add completes. */
    private Step add(Hall h, String name, Consumer<Event> keep, BlockPos at, GasParcel gas) {
        return event(h, name, keep, e -> addNow(e, at, gas), Event::done);
    }

    private void addNow(Event e, BlockPos at, GasParcel gas) {
        DimensionPipeline p = atmos.pipeline();
        e.addedAtBatch = p == null ? 0 : p.closedTick();
        CompletableFuture<MutationResult> f;
        long t = Clock.enter();
        try {
            f = GasMutations.add(level, Vec3.atCenterOf(at), gas, Bench.SOURCE);
        } finally {
            Clock.exit(Clock.BENCH_API, t);
        }
        f.whenComplete((m, failure) -> {
            DimensionPipeline q = atmos.pipeline();
            e.doneBatch = q == null ? -1 : q.closedTick();
            e.outcome = failure != null ? "failed: " + failure : m.outcome().name();
            e.doneNanos = System.nanoTime();
        });
    }

    /**
     * Breach and reseal, under ZAS's venting so the hall empties (owner, 2026-11-08): the hole,
     * until the hall reads breached; venting until its zone dissolves (CORE-076); the hole filled;
     * once a batch has carried the fill, an add, until the hall zones again; then {@link #TAIL}
     * ticks more. A zone that doesn't dissolve has the hole filled anyway and must read sealed. The
     * step after it puts the venting back.
     */
    private Step breach(Hall h) {
        return new Step(h, "breach and reseal", false) {
            private static final int BREACHED = 0;
            private static final int VENTING = 1;
            private static final int RESEALED = 2;
            private static final int ZONING = 3;
            private static final int TAILING = 4;

            private Event e;
            private int phase = BREACHED;
            private long phaseAt;
            private long holeNanos;
            private double molesBefore;
            private ZoneView last;
            private long resealBatch;

            @Override
            void begin() {
                ZasServerConfig.VENTING.set(ExchangeSettings.Venting.FAITHFUL);
                e = new Event("breach and reseal", now);
                h.breach = e;
                recording = e.window;
                event = e;
                ZoneView z = zone(left(h));
                molesBefore = z == null ? Double.NaN : z.totalMoles();
                holeNanos = System.nanoTime();
                e.actionNanos = holeNanos;
                hole(h, true);
            }

            @Override
            boolean poll(long t) {
                ZoneView z = zone(left(h));
                switch (phase) {
                    case BREACHED -> {
                        if (z == null || z.breached() || t >= TIMEOUT) {
                            e.facts.add(
                                    z == null
                                            ? String.format("the zone went within %.1f s of the hole", t / 20.0)
                                            : z.breached()
                                                    ? String.format("read breached %.1f s after the hole", t / 20.0)
                                                    : "never read breached");
                            to(VENTING, t);
                        }
                    }
                    case VENTING -> {
                        if (z != null) {
                            last = z;
                        }
                        if (z == null) {
                            // This tick reads what the last one applied; the last worker tick to
                            // finish before that apply is the one that dissolved the zone.
                            h.dissolveTick = e.window.ticks - 1;
                            h.dissolveWorker = e.window.worker.size() - 1;
                            h.dissolveSeconds = (System.nanoTime() - holeNanos) / 1e9;
                            e.facts.add(String.format(
                                    "dissolved %.1f s after the hole%s",
                                    h.dissolveSeconds,
                                    last == null
                                            ? ""
                                            : String.format(
                                                    "; last read at %.3f kPa, %.4f %% of its moles, %d open faces",
                                                    last.pressure(),
                                                    100 * last.totalMoles() / molesBefore,
                                                    last.openFaces())));
                            reseal(t);
                        } else if (t - phaseAt >= DISSOLVE_TIMEOUT) {
                            e.facts.add(String.format(
                                    "not dissolved after %.0f s of venting: %.3f kPa, %.4f %% of its moles",
                                    DISSOLVE_TIMEOUT / 20.0, z.pressure(), 100 * z.totalMoles() / molesBefore));
                            reseal(t);
                        }
                    }
                    case RESEALED -> {
                        if (z == null) {
                            if (atmos.pipeline().closedTick() > resealBatch) {
                                // From here, the event's time to done is the zoning's.
                                e.actionNanos = System.nanoTime();
                                addNow(e, middle(h), air(h.cells(), FILL_FRACTION));
                                to(ZONING, t);
                            }
                        } else if (!z.breached()) {
                            e.doneNanos = System.nanoTime();
                            e.outcome = String.format("sealed again without dissolving, %,d cells", z.cellCount());
                            to(TAILING, t);
                        } else if (t - phaseAt >= TIMEOUT) {
                            e.outcome = "still breached after the reseal";
                            to(TAILING, t);
                        }
                    }
                    case ZONING -> {
                        if (e.done() || t - phaseAt >= TIMEOUT) {
                            e.outcome = "zoned again: " + e.outcome;
                            to(TAILING, t);
                        }
                    }
                    default -> {
                        if (t - phaseAt >= TAIL) {
                            h.rezoneCells = cells(left(h));
                            e.window.endNanos = System.nanoTime();
                            recording = null;
                            event = null;
                            return true;
                        }
                    }
                }
                return false;
            }

            /** Fills the hole, and notes the batch that will carry it, so the add comes after. */
            private void reseal(long t) {
                e.actionNanos = System.nanoTime();
                hole(h, false);
                resealBatch = atmos.pipeline().closedTick();
                to(RESEALED, t);
            }

            private void to(int next, long t) {
                phase = next;
                phaseAt = t;
            }
        };
    }

    /** The active window: the vent blowing, topped up every second, and the player inside. */
    private Step active(Hall h) {
        return new Step(h, "active", false) {
            private Window w;
            private double before;

            @Override
            void begin() {
                w = new Window(h.side + " active", now);
                h.active = w;
                recording = w;
                ZoneView z = zone(left(h));
                before = z == null ? Double.NaN : z.pressure();
            }

            @Override
            boolean poll(long t) {
                if (t % 20 == 0) {
                    topUp(h);
                }
                if (t >= window) {
                    w.endNanos = System.nanoTime();
                    recording = null;
                    ZoneView z = zone(left(h));
                    h.vent = level.getBlockEntity(vent(h)) instanceof VentBlockEntity v
                            ? String.format(
                                    "running %s, %s, %s; its network at %.0f kPa; the hall from %.2f to %.2f kPa",
                                    v.running(),
                                    v.status(),
                                    v.controlled() ? "ordered by the controller" : "not ordered by a controller",
                                    v.networkPressure(),
                                    before,
                                    z == null ? Double.NaN : z.pressure())
                            : "no vent";
                    return true;
                }
                return false;
            }
        };
    }

    // ---- Actions -----------------------------------------------------------------------------

    /** The station's players leave and its chunks go, so the halls have the server to themselves. */
    private void letStationGo() {
        if (bench.players != null) {
            bench.players.leave(bench.players.players);
        }
        for (long c : bench.station.mainChunks()) {
            bench.level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
        }
        level = bench.server.getLevel(HALLS);
        if (level == null) {
            throw new IllegalStateException("no " + HALLS.location() + " dimension: the bench's datapack didn't load");
        }
        atmos = AtmosService.current().dimensionFor(level);
    }

    /** Whether the station's dimension holds no chunk any more, so none is left to save and unload. */
    private boolean stationUnloaded() {
        return bench.level.getChunkSource().getLoadedChunksCount() == 0;
    }

    private void setUp(Hall h) {
        ZasServerConfig.ENCLOSURE_MAX_CELLS.set(h.cells());
        force(h.inside, h.side, true);
        force(h.overCap, h.side, true);
        shell(h.inside, h.side, false);
        shell(h.overCap, h.side, true);
        Vec3 at = outside(h);
        if (player == null) {
            player = bench.players.join(level, PLAYER, at.x, at.y, at.z);
        } else {
            BenchPlayers.teleport(player, at.x, at.y, at.z);
        }
        if (worker == null) {
            worker = new WorkerTimes(atmos.profiler().listener);
            try {
                atmos.inspect(engine -> {
                            engine.setPhaseListener(worker);
                            return Boolean.TRUE;
                        })
                        .get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("couldn't time the halls' worker", e);
            }
        }
    }

    /** Keeps a hall's chunks, and one more on each side, loaded, or lets them go. */
    private void force(BlockPos inside, int side, boolean keep) {
        int x0 = ((inside.getX() - 1) >> 4) - 1;
        int x1 = ((inside.getX() + side) >> 4) + 1;
        int z0 = ((inside.getZ() - 1) >> 4) - 1;
        int z1 = ((inside.getZ() + side) >> 4) + 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                level.setChunkForced(x, z, keep);
                if (keep) {
                    level.getChunk(x, z);
                }
            }
        }
    }

    /**
     * A hall's walls, floor and roof of station wall around its inside. With {@code notch}, one
     * more cell: the middle of the north wall's lowest row is air, walled behind.
     */
    private void shell(BlockPos inside, int side, boolean notch) {
        BlockState wall = wall();
        int x0 = inside.getX() - 1;
        int x1 = inside.getX() + side;
        int z0 = inside.getZ() - 1;
        int z1 = inside.getZ() + side;
        int y0 = Station.FLOOR;
        int y1 = Station.FLOOR + HEIGHT + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean side0 = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = y0; y <= y1; y++) {
                    if (side0 || y == y0 || y == y1) {
                        level.setBlock(p.set(x, y, z), wall, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        if (notch) {
            BlockPos cell = new BlockPos(inside.getX() + side / 2, inside.getY(), z0);
            level.setBlock(cell.north(), wall, Block.UPDATE_CLIENTS);
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Builds the wall across the hall's middle but its gap, or takes all of it down. */
    private void divider(Hall h, boolean build) {
        BlockState state = build ? wall() : Blocks.AIR.defaultBlockState();
        BlockPos gap = gap(h);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int z = 0; z < h.side; z++) {
            for (int y = 0; y < HEIGHT; y++) {
                p.set(gap.getX(), h.inside.getY() + y, h.inside.getZ() + z);
                if (!build || !p.equals(gap)) {
                    level.setBlock(p, state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Opens the 3 × 3 hole in the middle of the roof, or fills it again. */
    private void hole(Hall h, boolean open) {
        BlockState state = open ? Blocks.AIR.defaultBlockState() : wall();
        BlockPos m = middle(h);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlock(new BlockPos(m.getX() + dx, h.inside.getY() + HEIGHT, m.getZ() + dz), state);
            }
        }
    }

    /**
     * A generator, with an area air controller above it on the north wall, in the hall's corner,
     * and a vent in the floor over a capped pipe; then the player goes in.
     */
    private void devicesIn(Hall h) {
        BlockPos v = vent(h);
        BlockPos pipe = v.below();
        set(v, AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.UP));
        set(pipe, AtmosBlocks.PIPE.get().defaultBlockState());
        set(pipe.west(), AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.EAST));
        set(pipe.east(), AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.WEST));
        set(pipe, Block.updateFromNeighbourShapes(level.getBlockState(pipe), level, pipe));
        BlockPos generator = h.inside;
        set(generator, AtmosBlocks.GENERATOR.get().defaultBlockState());
        set(
                generator.above(),
                AtmosBlocks.AREA_AIR_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AreaAirControllerBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(generator) instanceof GeneratorBlockEntity g) {
            g.fuel().insertItem(0, new ItemStack(Items.COAL, 64), false);
        } else {
            throw new IllegalStateException("no generator in the hall");
        }
        topUp(h);
        Vec3 in = Vec3.atBottomCenterOf(middle(h).atY(h.inside.getY()));
        BenchPlayers.teleport(player, in.x, in.y, in.z);
    }

    /** Tops the vent's network up to the station's supply pressure, through its store. */
    private void topUp(Hall h) {
        long t = Clock.enter();
        try {
            if (level.getBlockEntity(vent(h)) instanceof VentBlockEntity v) {
                var gas = v.network().gas();
                double missing =
                        (Bench.SUPPLY_KPA - gas.pressure()) * gas.volume() / (Bench.R * StandardAir.TEMPERATURE);
                if (missing > 1) {
                    gas.add(
                            Map.of(
                                    StandardAir.NITROGEN, missing * StandardAir.NITROGEN_FRACTION,
                                    StandardAir.OXYGEN, missing * StandardAir.OXYGEN_FRACTION),
                            StandardAir.TEMPERATURE);
                }
            }
        } finally {
            Clock.exit(Clock.BENCH_API, t);
        }
    }

    /** Walks the halls' engine, pipeline and platform, as the station's heap step does. */
    private AtmosHeap heap() {
        AtmosHeap h = new AtmosHeap();
        DimensionPipeline p = atmos.pipeline();
        try {
            atmos.inspect(engine -> h.engine(engine, p)).get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("couldn't walk the halls' heap", e);
        }
        h.dimension(atmos);
        return h;
    }

    // ---- Places and helpers ------------------------------------------------------------------

    /** Air for some cells at a fraction of standard pressure, 20 °C. */
    static GasParcel air(int cells, double fraction) {
        double moles =
                fraction * cells * StandardAir.PRESSURE * StandardAir.CELL_VOLUME / (Bench.R * StandardAir.TEMPERATURE);
        return new GasParcel(
                Map.of(
                        StandardAir.NITROGEN, moles * StandardAir.NITROGEN_FRACTION,
                        StandardAir.OXYGEN, moles * StandardAir.OXYGEN_FRACTION),
                StandardAir.TEMPERATURE);
    }

    private static BlockState wall() {
        return AtmosBlocks.STATION_WALL.get().defaultBlockState();
    }

    /** A block change as a player's would be, neighbours told. */
    private void setBlock(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private void set(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    /** The hall's middle, halfway up: where the add that zones it goes. */
    private static BlockPos middle(Hall h) {
        return h.inside.offset(h.side / 2, HEIGHT / 2, h.side / 2);
    }

    /** A point in the west half, clear of the dividing wall. */
    private static BlockPos left(Hall h) {
        return h.inside.offset(h.side / 4, HEIGHT / 2, h.side / 2);
    }

    /** A point in the east half. */
    private static BlockPos right(Hall h) {
        return h.inside.offset(3 * h.side / 4, HEIGHT / 2, h.side / 2);
    }

    /** The dividing wall's missing block, on the floor in the middle. */
    private static BlockPos gap(Hall h) {
        return h.inside.offset(h.side / 2, 0, h.side / 2);
    }

    private static BlockPos vent(Hall h) {
        return h.inside.offset(2, -1, 2);
    }

    /** Where the player waits: 3 blocks out from the north wall, in the vacuum. */
    private static Vec3 outside(Hall h) {
        return new Vec3(h.inside.getX() + h.side / 2 + 0.5, h.inside.getY() + 7, h.inside.getZ() - 4 + 0.5);
    }

    private ZoneView zone(BlockPos at) {
        return atmos.published().zoneAt(at.getX(), at.getY(), at.getZ());
    }

    private int cells(BlockPos at) {
        ZoneView z = zone(at);
        return z == null ? 0 : z.cellCount();
    }
}
