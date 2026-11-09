package io.github.west3436.zas.s10;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.fire.Ignition;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.engine.zone.ExchangeSettings;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The S-10 script (00-overview): builds the station, fills it, brings in the players and mobs,
 * lights the fires, runs the scenario while measuring, then the heap, the explosion breach
 * (NFR-002), spike #80's survey and the enclosure cap step ({@link CapStep}), writes the report
 * and stops the server.
 *
 * <p>The scenario while it runs: one breach a minute, in turn in the 7 player rooms of row 0,
 * resealed after 10 s; the wing's player leaves for a minute every 2 minutes, so the wing's chunks
 * unload and its zones pause, then resume. Every 10 s the supply networks are topped back up to
 * 2,000 kPa, a stand-in for a station's supply that the bench adds through the networks' stores.
 */
final class Bench {
    static final SourceId SOURCE = SourceId.of("zas_s10", "bench");
    static final ResourceKey<Level> STATION =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_s10", "station"));
    static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    static final double R = 8.314462618;
    static final double SUPPLY_KPA = 2000;
    static final int FIRE_ROOM = 44;
    static final int WING_PLAYER_ROOM = 84;
    static final int PLAYERS = 8;
    static final int MOBS = 100;

    enum Stage {
        WAIT,
        BUILT,
        FILLING,
        IGNITING,
        WARMUP,
        MEASURE,
        HEAP,
        BLAST_START,
        BLAST,
        SURVEY,
        CAP,
        REPORT,
        DONE
    }

    final MinecraftServer server;
    final Settings settings;
    final Station station = new Station();
    final Map<String, String> notes = new LinkedHashMap<>();

    ServerLevel level;
    DimensionAtmos atmos;
    BenchPlayers players;
    WorkerTimes worker;
    EscapeSurvey survey;
    CapStep cap;
    AtmosHeap heap;

    Stage stage = Stage.WAIT;
    private long stageTick;
    private long startTick = -1;
    private long disturbStart;

    Window window;
    Window measure;
    Window blast;
    Window surveyWindow;

    private final TickTimes tick = new TickTimes();
    private long closedBefore;
    private boolean inFlightBefore;
    private long[] partBuf = new long[64];
    private long requestedOffset = -1;

    private final List<CompletableFuture<MutationResult>> fills = new ArrayList<>();
    private final List<CompletableFuture<Ignition>> ignitions = new ArrayList<>();
    final Map<String, String> fillOutcomes = new LinkedHashMap<>();
    final Map<String, String> ignitionOutcomes = new LinkedHashMap<>();
    ServerPlayer traveller;
    private Vec3 travellerHome;
    private long leftAt;
    int trips;
    int breaches;
    private BlockPos openBreach;
    private long resealAt;
    int relights;
    int topUps;
    final Window.Series zonesSeen = new Window.Series();
    final Window.Series pausedSeen = new Window.Series();
    // Each sample's zones, paused zones and loaded wing chunks, as "z/p/w".
    final List<String> zoneSamples = new ArrayList<>();
    // The tickets near the wing while its player was away and some of it stayed loaded.
    final Set<String> wingTickets = new TreeSet<>();
    // Samples taken while the wing's player was away, and those in which some wing room wasn't zoned.
    int wingAwaySamples;
    int wingPausedSamples;
    // What loaded the wing's chunks while its player was away, and how often.
    final Map<String, Integer> wingLoads = new TreeMap<>();
    final Map<String, List<String>> fireChecks = new LinkedHashMap<>();
    final List<String> blastTrace = new ArrayList<>();
    String blastVerdict = "not run";
    boolean blastVented;
    private final List<double[]> blastZone = new ArrayList<>();
    int cowsSpawned;
    final Map<String, Object> scenario = new LinkedHashMap<>();

    Bench(MinecraftServer server, Settings settings) {
        this.server = server;
        this.settings = settings;
    }

    /** The bench's settings, from the system properties the Gradle task sets. */
    record Settings(boolean quick, String out, int warmup, int measure, int blast, int breachEvery, int travelEvery) {
        static Settings fromSystem() {
            boolean quick = Boolean.parseBoolean(System.getProperty("zas.s10.quick", "false"));
            String out = System.getProperty("zas.s10.out", "");
            return quick
                    ? new Settings(true, out, 20 * 20, 60 * 20, 20 * 20, 60 * 20, 40 * 20)
                    : new Settings(false, out, 120 * 20, 600 * 20, 60 * 20, 60 * 20, 120 * 20);
        }
    }

    // ---- The tick ----------------------------------------------------------------------------

    /** The start of a server tick: the first listener of all. */
    void preTick() {
        tick.start();
        DimensionPipeline p = atmos == null ? null : atmos.pipeline();
        closedBefore = p == null ? Long.MIN_VALUE : p.closedTick();
        inFlightBefore = atmos != null && atmos.isInFlight();
        if (cap != null && stage == Stage.CAP) {
            cap.before();
        }
        try {
            step();
        } catch (RuntimeException e) {
            S10Mod.LOGGER.error("[S-10] the script failed at {}", stage, e);
            notes.put("Failure", stage + ": " + e);
            stage = Stage.REPORT;
        }
        if (players != null) {
            players.tick();
        }
    }

    /** The end of a server tick: the last listener of all. */
    void postTick() {
        long atmosNanos = Clock.endTick(partBuffer());
        tick.end();
        long wall = tick.wallNanos();
        boolean closed = false;
        boolean applied = false;
        long[] requested = new long[0];
        DimensionPipeline p = atmos == null ? null : atmos.pipeline();
        if (p != null) {
            closed = p.closedTick() != closedBefore;
            applied = inFlightBefore && (closed || !atmos.isInFlight());
            if (applied) {
                requested = requested(p);
            }
        }
        List<long[]> workerTicks = worker == null ? List.of() : worker.drain();
        Window w = window;
        if (w != null) {
            w.tick(atmosNanos, tick.startNanos(), wall, tick.cpuNanos(), partBuf, Clock.partCount());
            if (w == measure || w == blast) {
                // NFR-002 is judged on these windows' ticks by zas's own time.
                w.keepCalls(atmosNanos);
                w.batchWork(closed ? atmos.lastCloseNanos() : -1, applied ? atmos.lastApplyNanos() : -1);
            }
            if (closed) {
                w.close.add(atmos.lastCloseNanos());
                w.batchSections.add(p.lastBatchSections());
            }
            if (applied) {
                w.apply.add(atmos.lastApplyNanos());
                if (requested.length > 0) {
                    w.requestBatches++;
                    w.requestedSections += requested.length;
                }
            }
            w.worker.addAll(workerTicks);
        }
        if (survey != null && stage == Stage.SURVEY) {
            survey.after(closed, applied, requested, workerTicks);
        }
        if (cap != null && stage == Stage.CAP) {
            cap.after(atmosNanos, tick.startNanos(), wall, tick.cpuNanos(), partBuf, Clock.partCount());
        }
        if (players != null) {
            players.count(w);
        }
    }

    private long[] partBuffer() {
        int n = Clock.partCount();
        if (partBuf.length < n) {
            partBuf = new long[n * 2];
        }
        java.util.Arrays.fill(partBuf, 0);
        return partBuf;
    }

    private long[] requested(DimensionPipeline p) {
        if (requestedOffset < 0) {
            requestedOffset = Unsafes.offset(DimensionPipeline.class, "requested");
        }
        Object r = Unsafes.get(p, requestedOffset);
        return r instanceof long[] a ? a : new long[0];
    }

    private long now() {
        return server.getTickCount();
    }

    private long elapsed() {
        return now() - stageTick;
    }

    private void enter(Stage next) {
        stage = next;
        stageTick = now();
        S10Mod.LOGGER.info("[S-10] {}", next);
    }

    private void step() {
        if (startTick < 0) {
            startTick = now();
        }
        switch (stage) {
            case WAIT -> {
                if (now() - startTick >= 40) {
                    build();
                    enter(Stage.BUILT);
                }
            }
            case BUILT -> {
                if (elapsed() >= 60) {
                    fill();
                    enter(Stage.FILLING);
                }
            }
            case FILLING -> {
                if (fills.stream().allMatch(CompletableFuture::isDone) || elapsed() >= 60 * 20) {
                    populate();
                    enter(Stage.IGNITING);
                }
            }
            case IGNITING -> {
                if (ignitions.stream().allMatch(CompletableFuture::isDone) || elapsed() >= 20 * 20) {
                    recordIgnitions();
                    installWorkerTimes();
                    disturbStart = now();
                    window = new Window("warm-up", now());
                    enter(Stage.WARMUP);
                }
            }
            case WARMUP -> {
                disturb();
                if (elapsed() >= settings.warmup()) {
                    window.endNanos = System.nanoTime();
                    measure = new Window("measure", now());
                    window = measure;
                    checkFires("measure start");
                    enter(Stage.MEASURE);
                }
            }
            case MEASURE -> {
                disturb();
                if (elapsed() % 200 == 0) {
                    sampleZones();
                }
                if (elapsed() >= settings.measure()) {
                    measure.endNanos = System.nanoTime();
                    window = null;
                    checkFires("measure end");
                    enter(Stage.HEAP);
                }
            }
            case HEAP -> {
                // A tick of its own: the walk holds the server thread, and no window is open.
                measureHeap();
                enter(Stage.BLAST_START);
            }
            case BLAST_START -> {
                blast = new Window("blast", now());
                window = blast;
                explode();
                enter(Stage.BLAST);
            }
            case BLAST -> {
                disturb();
                if (elapsed() % 20 == 0) {
                    traceBlast();
                }
                if (elapsed() >= settings.blast()) {
                    blast.endNanos = System.nanoTime();
                    judgeBlast();
                    reseal();
                    surveyWindow = new Window("survey", now());
                    window = surveyWindow;
                    survey = new EscapeSurvey(level, atmos, settings.quick());
                    enter(Stage.SURVEY);
                }
            }
            case SURVEY -> {
                survey.tick(now());
                if (survey.done()) {
                    surveyWindow.endNanos = System.nanoTime();
                    window = null;
                    cap = new CapStep(this);
                    enter(Stage.CAP);
                }
            }
            case CAP -> {
                cap.tick(now());
                if (cap.finished()) {
                    enter(Stage.REPORT);
                }
            }
            case REPORT -> {
                try {
                    new Report(this).write();
                } finally {
                    if (players != null) {
                        players.leave();
                        players = null;
                    }
                    enter(Stage.DONE);
                    server.halt(false);
                }
            }
            case DONE -> {}
        }
    }

    // ---- Setting up --------------------------------------------------------------------------

    private void build() {
        level = server.getLevel(STATION);
        if (level == null) {
            throw new IllegalStateException(
                    "no " + STATION.location() + " dimension: the bench's datapack didn't load");
        }
        AtmosService service = AtmosService.current();
        atmos = service.dimensionFor(level);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        for (long c : station.mainChunks()) {
            level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
            level.getChunk(ChunkPos.getX(c), ChunkPos.getZ(c));
        }
        // The wing is loaded to build it and bring in its player, then only that player keeps it.
        for (long c : station.wingChunks()) {
            level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
            level.getChunk(ChunkPos.getX(c), ChunkPos.getZ(c));
        }
        long t = System.nanoTime();
        station.build(level);
        notes.put("Build", String.format("%,d ms for the station's blocks", (System.nanoTime() - t) / 1_000_000));
    }

    /** Gas for some cells of standard air at a temperature: 101.325 kPa, 79 % N2 and 21 % O2. */
    static GasParcel air(int cells, double kelvin) {
        return mix(cells, kelvin, 0);
    }

    /** Air with a fraction of H2 in place of some of its N2. */
    static GasParcel mix(int cells, double kelvin, double h2) {
        double moles = cells * StandardAir.PRESSURE * StandardAir.CELL_VOLUME / (R * kelvin);
        Map<ResourceLocation, Double> m = new HashMap<>();
        m.put(StandardAir.NITROGEN, moles * (StandardAir.NITROGEN_FRACTION - h2));
        m.put(StandardAir.OXYGEN, moles * StandardAir.OXYGEN_FRACTION);
        if (h2 > 0) {
            m.put(H2, moles * h2);
        }
        return new GasParcel(m, kelvin);
    }

    private void fill() {
        for (Station.Room r : station.rooms) {
            GasParcel gas;
            if (r.slot() == Station.BIG) {
                gas = mix(r.cells(), 600, 0.05); // the hot zone, burning, at 5 % H2
            } else if (r.slot() == FIRE_ROOM) {
                gas = mix(r.cells(), StandardAir.TEMPERATURE, 0.12); // the second fire
            } else {
                gas = air(r.cells(), StandardAir.TEMPERATURE);
            }
            CompletableFuture<MutationResult> f = api(() -> GasMutations.add(level, r.centrePoint(), gas, SOURCE));
            fills.add(f);
            f.thenAccept(m -> fillOutcomes.put(r.label(), m.outcome().name()));
        }
    }

    private void populate() {
        // The mobs: in every room but the fire room, the big room and the player rooms of row 0.
        List<Station.Room> homes = station.rooms.stream()
                .filter(r -> r.slot() != Station.BIG && r.slot() != FIRE_ROOM && r.slot() >= 7)
                .toList();
        for (int i = 0; i < MOBS; i++) {
            Station.Room r = homes.get(i % homes.size());
            Cow cow = EntityType.COW.create(level);
            if (cow == null) {
                continue;
            }
            Vec3 at = r.standing(1 + i / homes.size());
            cow.moveTo(at.x, at.y, at.z, 0f, 0f);
            cow.setPersistenceRequired();
            if (level.addFreshEntity(cow)) {
                cowsSpawned++;
            }
        }
        players = new BenchPlayers(server);
        for (int i = 0; i < PLAYERS - 1; i++) {
            Vec3 at = station.room(i).standing(0);
            players.join(level, i + 1, at.x, at.y, at.z);
        }
        Vec3 home = station.room(WING_PLAYER_ROOM).standing(0);
        traveller = players.join(level, PLAYERS, home.x, home.y, home.z);
        travellerHome = home;
        for (long c : station.wingChunks()) {
            level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
        }
        for (Station.Room r : List.of(station.big, station.room(FIRE_ROOM))) {
            CompletableFuture<Ignition> f = api(() -> Fires.ignite(level, r.centre(), SOURCE));
            ignitions.add(f);
            f.thenAccept(ig -> ignitionOutcomes.put(r.label(), ig.outcome().name()));
        }
    }

    private void recordIgnitions() {
        AtmosSnapshot s = atmos.published();
        int cells = 0;
        int largest = 0;
        for (ZoneView z : s.zones()) {
            cells += z.cellCount();
            largest = Math.max(largest, z.cellCount());
        }
        scenario.put("Zones after the fill", s.zoneCount());
        scenario.put("Zoned cells after the fill", cells);
        scenario.put("Largest zone's cells", largest);
        scenario.put("Rooms as built (cells inside)", station.cells());
        scenario.put("Mobs (cows) spawned", cowsSpawned);
        scenario.put("Players", players.players.size());
        long failed =
                fillOutcomes.values().stream().filter(o -> !o.equals("ZONE")).count();
        scenario.put("Fills that didn't land in a zone", failed + " of " + station.rooms.size());
        scenario.put("Ignitions", ignitionOutcomes.toString());
        int devices = 0;
        int controllers = 0;
        for (Station.Room r : station.rooms) {
            for (BlockPos p : List.of(r.vent(), r.scrubber(), r.alarm())) {
                if (level.getBlockEntity(p) instanceof io.github.west3436.zas.atmos.power.PoweredDevice) {
                    devices++;
                }
            }
            if (level.getBlockEntity(r.controller())
                    instanceof io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity) {
                controllers++;
            }
        }
        scenario.put("Powered devices (vents, scrubbers, fire alarms)", devices);
        scenario.put("Area air controllers", controllers);
    }

    private void installWorkerTimes() {
        worker = new WorkerTimes(atmos.profiler().listener);
        try {
            atmos.inspect(engine -> {
                        engine.setPhaseListener(worker);
                        return Boolean.TRUE;
                    })
                    .get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("couldn't time the worker", e);
        }
    }

    // ---- The scenario's events ---------------------------------------------------------------

    private void disturb() {
        long t = now() - disturbStart;
        if (t > 0 && t % settings.breachEvery() == 200 % settings.breachEvery()) {
            Station.Room r = station.room((int) ((t / settings.breachEvery()) % (PLAYERS - 1)));
            openBreach = r.breach();
            level.setBlock(openBreach, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            resealAt = now() + 200;
            breaches++;
            note("breach " + r.label());
        }
        if (openBreach != null && now() >= resealAt) {
            reseal();
        }
        int half = settings.travelEvery() / 2;
        if (t > 0 && t % settings.travelEvery() == half) {
            BenchPlayers.teleport(traveller, travellerHome.x + 2000, travellerHome.y, travellerHome.z + 2000);
            trips++;
            leftAt = now();
            note("the wing's player leaves");
        } else if (t > 0 && t % settings.travelEvery() == 0) {
            BenchPlayers.teleport(traveller, travellerHome.x, travellerHome.y, travellerHome.z);
            note("the wing's player returns");
        }
        if (t % 200 == 0) {
            topUp();
            relight();
        }
    }

    private void reseal() {
        if (openBreach != null) {
            level.setBlock(
                    openBreach,
                    io.github.west3436.zas.atmos.registry.AtmosBlocks.STATION_WALL
                            .get()
                            .defaultBlockState(),
                    Block.UPDATE_ALL);
            openBreach = null;
        }
    }

    private void note(String what) {
        if (window != null) {
            window.events.put(now() - window.firstTick, what);
        }
    }

    /** Tops each loaded supply network back up to {@link #SUPPLY_KPA}: the bench's stand-in supply. */
    private void topUp() {
        long t = Clock.enter();
        try {
            for (int row = 0; row < 10; row++) {
                BlockPos v = station.room(row * Station.ROOMS_PER_ROW).vent();
                if (!level.isLoaded(v) || !(level.getBlockEntity(v) instanceof VentBlockEntity vent)) {
                    continue;
                }
                var gas = vent.network().gas();
                double missing = (SUPPLY_KPA - gas.pressure()) * gas.volume() / (R * StandardAir.TEMPERATURE);
                if (missing > 1) {
                    gas.add(
                            Map.of(
                                    StandardAir.NITROGEN, missing * StandardAir.NITROGEN_FRACTION,
                                    StandardAir.OXYGEN, missing * StandardAir.OXYGEN_FRACTION),
                            StandardAir.TEMPERATURE);
                    topUps++;
                }
            }
        } finally {
            Clock.exit(Clock.BENCH_API, t);
        }
    }

    /** Lights a fire again if it went out, so two burn throughout. */
    private void relight() {
        for (Station.Room r : List.of(station.big, station.room(FIRE_ROOM))) {
            AtmosphereSample s = Atmospheres.at(level, r.centrePoint());
            if (s.zone().isPresent() && !Fires.isBurning(level, s.zone().get())) {
                api(() -> Fires.ignite(level, r.centre(), SOURCE));
                relights++;
                note("relight " + r.label());
            }
        }
    }

    private void checkFires(String when) {
        List<String> lines = new ArrayList<>();
        for (Station.Room r : List.of(station.big, station.room(FIRE_ROOM))) {
            AtmosphereSample s = Atmospheres.at(level, r.centrePoint());
            String fire = s.zone()
                    .map(z -> Fires.hotspots(level, z).size() + " hotspot(s)")
                    .orElse("no zone");
            lines.add(String.format(
                    "%s: %s, %.1f kPa, %.0f K, %.1f %% H2",
                    r.label(),
                    fire,
                    s.pressure(),
                    s.temperature(),
                    100 * s.composition().getOrDefault(H2, 0.0)));
        }
        fireChecks.put(when, lines);
    }

    private void sampleZones() {
        AtmosSnapshot s = atmos.published();
        int paused = 0;
        for (ZoneView z : s.zones()) {
            if (z.paused()) {
                paused++;
            }
        }
        StringBuilder wing = new StringBuilder();
        int loaded = 0;
        for (long c : station.wingChunks()) {
            if (level.getChunkSource().hasChunk(ChunkPos.getX(c), ChunkPos.getZ(c))) {
                loaded++;
            } else {
                wing.append(wing.isEmpty() ? " (out: " : " ")
                        .append(ChunkPos.getX(c))
                        .append(',')
                        .append(ChunkPos.getZ(c));
            }
        }
        if (!wing.isEmpty()) {
            wing.append(')');
        }
        // How the wing's rooms read: zoned, paused, unresolved (unloaded) or ambient.
        Map<AtmosphereState, Integer> rooms = new java.util.EnumMap<>(AtmosphereState.class);
        for (Station.Room r : station.rooms) {
            if (r.wing()) {
                rooms.merge(Atmospheres.at(level, r.centrePoint()).state(), 1, Integer::sum);
            }
        }
        // Once the tickets the player left behind have had time to go.
        if (traveller.distanceToSqr(travellerHome) > 1 && now() - leftAt >= 100) {
            wingAwaySamples++;
            if (loaded > 0) {
                wingTickets.addAll(ticketsNearWing());
            }
            if (rooms.getOrDefault(AtmosphereState.ZONED, 0)
                    < rooms.values().stream().mapToInt(i -> i).sum()) {
                wingPausedSamples++;
            }
        }
        zonesSeen.add(s.zoneCount());
        pausedSeen.add(paused);
        zoneSamples.add(s.zoneCount() + "/" + paused + "/" + loaded + wing + " " + rooms);
    }

    /**
     * Notes what loaded a chunk of the wing while its player was away: the frames above Minecraft's
     * chunk loading, when the load was synchronous on the server thread. Server thread.
     */
    void chunkLoaded(net.minecraft.world.level.LevelAccessor at, ChunkPos pos) {
        if (at != level
                || traveller == null
                || traveller.distanceToSqr(travellerHome) <= 1
                || !station.wingChunks().contains(pos.toLong())) {
            return;
        }
        StackTraceElement[] stack = new Throwable().getStackTrace();
        int from = -1;
        for (int i = 0; i < stack.length; i++) {
            if (stack[i].getClassName().equals("net.minecraft.server.level.ServerChunkCache")
                    && stack[i].getMethodName().equals("getChunk")) {
                from = i;
            }
        }
        StringBuilder by = new StringBuilder();
        if (from < 0) {
            by.append("not on a synchronous load");
        } else {
            int shown = 0;
            for (int i = from + 1; i < stack.length && shown < 4; i++) {
                String c = stack[i].getClassName();
                if (c.startsWith("net.minecraft.world.level.Level") || c.endsWith("LevelReader")) {
                    continue;
                }
                by.append(shown++ == 0 ? "" : " < ")
                        .append(c.substring(c.lastIndexOf('.') + 1))
                        .append('.')
                        .append(stack[i].getMethodName());
            }
        }
        wingLoads.merge(by.toString(), 1, Integer::sum);
    }

    /** The chunk tickets on the wing's chunks, which keep them loaded: "x,z type level". */
    private Set<String> ticketsNearWing() {
        Set<String> out = new TreeSet<>();
        Object manager = level.getChunkSource().chunkMap.getDistanceManager();
        var tickets = (Map<?, ?>) Unsafes.get(manager, Unsafes.offset(DistanceManager.class, "tickets"));
        Set<Long> wing = station.wingChunks();
        for (Map.Entry<?, ?> e : tickets.entrySet()) {
            long c = (Long) e.getKey();
            if (wing.contains(c)) {
                for (Object t : (Iterable<?>) e.getValue()) {
                    Ticket<?> ticket = (Ticket<?>) t;
                    out.add(ChunkPos.getX(c) + "," + ChunkPos.getZ(c) + " " + ticket.getType() + " "
                            + ticket.getTicketLevel());
                }
            }
        }
        return out;
    }

    private <T> T api(java.util.function.Supplier<T> call) {
        long t = Clock.enter();
        try {
            return call.get();
        } finally {
            Clock.exit(Clock.BENCH_API, t);
        }
    }

    // ---- Heap, blast ---------------------------------------------------------------------------

    private void measureHeap() {
        AtmosHeap h = new AtmosHeap();
        DimensionPipeline p = atmos.pipeline();
        long t = System.nanoTime();
        try {
            atmos.inspect(engine -> h.engine(engine, p)).get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("couldn't walk the engine's heap", e);
        }
        Set<Long> chunks = new TreeSet<>(station.mainChunks());
        chunks.addAll(station.wingChunks());
        h.dimension(atmos).statics().devices(level, chunks).attachments(level.getAllEntities());
        notes.put(
                "Heap walk",
                String.format("%,d ms, outside every measured window", (System.nanoTime() - t) / 1_000_000));
        AtmosSnapshot s = atmos.published();
        int cells = 0;
        for (ZoneView z : s.zones()) {
            cells += z.cellCount();
        }
        scenario.put("Zones at the heap walk", s.zoneCount());
        scenario.put("Zoned cells at the heap walk", cells);
        heap = h;
    }

    /** NFR-002's explosion: TNT's power, inside the big room by its east wall. */
    private void explode() {
        checkFires("before the explosion");
        blastTrace.add(blastLine(0));
        BlockPos at = new BlockPos(19, Station.FLOOR + 6, 10);
        level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4f, Level.ExplosionInteraction.TNT);
        int open = 0;
        for (int y = Station.FLOOR; y <= station.big.ceiling(); y++) {
            for (int z = 0; z <= station.big.z1(); z++) {
                if (level.getBlockState(new BlockPos(station.big.x1(), y, z)).isAir()) {
                    open++;
                }
            }
        }
        scenario.put("East wall blocks the explosion took out", open);
    }

    private String blastLine(long ticks) {
        AtmosphereSample s = Atmospheres.at(level, station.big.centrePoint());
        String zone = s.zone().map(ZoneId::value).map(String::valueOf).orElse("-");
        Optional<ZoneInfo> info = s.zone().flatMap(z -> Atmospheres.zone(level, z));
        String breached = info.map(i -> i.breached() ? "breached" : "sealed").orElse("-");
        // Seconds, moles, open faces, volume and breached or not, for the verdict.
        blastZone.add(info.map(i -> new double[] {
                    ticks / 20.0,
                    i.mixture().totalMoles(),
                    i.edges().openFaces(),
                    i.volume(),
                    i.breached() && s.state() == AtmosphereState.ZONED ? 1 : 0
                })
                .orElse(new double[] {ticks / 20.0, Double.NaN, 0, 0, 0}));
        return String.format(
                "%5.1f s: %s, zone %s (%s), %.2f kPa, %.0f K, %,.0f mol, %d open faces",
                ticks / 20.0,
                s.state(),
                zone,
                breached,
                s.pressure(),
                s.temperature(),
                info.map(i -> i.mixture().totalMoles()).orElse(Double.NaN),
                info.map(i -> i.edges().openFaces()).orElse(0));
    }

    private void traceBlast() {
        blastTrace.add(blastLine(elapsed()));
    }

    /**
     * Whether the big room vents as the exchange model says it should (CORE-032, CORE-035): from
     * the first second it reads breached, its moles fall every second, and what it lost by the end
     * is between three quarters and four thirds of what its open faces and volume give, each second,
     * through the vacuum ambient. The burning hotspot's own change of moles is a small fraction of
     * that and is left in. The trace's last line is the window's last tick.
     */
    private void judgeBlast() {
        // As DimensionAtmos sets it up.
        double period = ZasServerConfig.scheduler().periodSeconds();
        ExchangeSettings exchange = ExchangeSettings.DEFAULT.withPeriod(period).withVenting(ZasServerConfig.venting());
        int first = -1;
        for (int i = 0; i < blastZone.size(); i++) {
            if (blastZone.get(i)[4] == 1) {
                first = i;
                break;
            }
        }
        if (first < 0 || first == blastZone.size() - 1) {
            blastVented = false;
            blastVerdict = "DOESN'T VENT: it never read breached and zoned";
            return;
        }
        boolean falling = true;
        double kept = 1;
        for (int i = first + 1; i < blastZone.size(); i++) {
            double[] a = blastZone.get(i - 1);
            double[] b = blastZone.get(i);
            falling &= b[4] == 1 && b[1] < a[1];
            // Each atmos tick in between closes the model's fraction toward an empty ambient.
            double ticks = (b[0] - a[0]) / period;
            kept *= Math.pow(1 - exchange.ambientRatio((int) a[2], a[3]), ticks);
        }
        double[] start = blastZone.get(first);
        double[] end = blastZone.getLast();
        double lost = 1 - end[1] / start[1];
        double expected = 1 - kept;
        double ratio = lost / expected;
        blastVented = falling && ratio >= 0.75 && ratio <= 1 / 0.75;
        blastVerdict = String.format(
                "%s: from %.0f s to %.0f s it lost %.3f %% of its moles, and the exchange model (%s venting) gives"
                        + " %.3f %% for its open faces and volume%s",
                blastVented ? "vents as modelled" : "DOESN'T VENT AS MODELLED",
                start[0],
                end[0],
                100 * lost,
                exchange.venting(),
                100 * expected,
                falling ? "" : "; its moles didn't fall every second");
    }
}
