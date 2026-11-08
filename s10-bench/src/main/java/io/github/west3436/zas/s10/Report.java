package io.github.west3436.zas.s10;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes the bench's report: {@code s10-report.md}, with each window's per-tick times in CSV
 * beside it, into the run directory and the Gradle build's {@code s10} directory.
 *
 * <p>Every budget is given with its margin: how many times over the figure the budget is. The rig
 * is faster per core than D-40's reference parts, so a time budget only counts as met here when its
 * margin is at least {@link #REQUIRED_MARGIN}. Bytes, of heap or sent, don't depend on the
 * processor, so a byte budget counts as met at its figure.
 */
final class Report {
    /**
     * The margin a budget needs on the owner's i7-13700K for D-40's slower cores: its P-cores' single-thread
     * speed over a Ryzen 5 5600's, about 2,100 against 1,500 in Cinebench R23's single-core test, so about 1.4.
     */
    static final double REQUIRED_MARGIN = 1.4;

    private static final double MS = 1e6;
    private final Bench b;
    private final StringBuilder md = new StringBuilder();
    private final Map<String, String> verdicts = new LinkedHashMap<>();

    Report(Bench bench) {
        this.b = bench;
    }

    void write() {
        header();
        scenario();
        if (b.measure != null) {
            serverThread(b.measure, "NFR-001: atmos work on the server thread, while the scenario runs");
            closeAndApply(b.measure);
            worker(b.measure);
            sync(b.measure);
            escapesInRun(b.measure);
        }
        heap();
        blast();
        survey();
        capStep();
        wholeTicks();
        notes();
        String text = md.toString();
        List<Path> dirs = new ArrayList<>();
        dirs.add(Path.of("."));
        if (!b.settings.out().isEmpty()) {
            dirs.add(Path.of(b.settings.out()));
        }
        String verdictText = summary();
        text = text.replace("<<SUMMARY>>", verdictText);
        for (Path dir : dirs) {
            try {
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("s10-report.md"), text, StandardCharsets.UTF_8);
                for (Window w : new Window[] {b.measure, b.blast}) {
                    if (w != null) {
                        Files.writeString(dir.resolve("s10-" + w.name + "-ticks.csv"), csv(w), StandardCharsets.UTF_8);
                    }
                }
            } catch (IOException e) {
                S10Mod.LOGGER.error("[S-10] couldn't write the report to {}", dir, e);
            }
        }
        S10Mod.LOGGER.info("[S-10] report written:\n{}", verdictText);
    }

    // ---- Sections -----------------------------------------------------------------------------

    private void header() {
        line("# S-10 bench report");
        line();
        line("Written " + ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                + (b.settings.quick() ? ". **A quick run: its figures mean nothing.**" : "."));
        line();
        line("Machine: " + Machine.describe() + ".");
        line("Every figure is from this machine. D-40's reference parts (Ryzen 5 5600, i5-12400) are slower per"
                + " core, so a time budget counts as met only with a margin of at least " + REQUIRED_MARGIN
                + " (the budget over the figure). Bytes, of heap or sent, don't depend on the processor.");
        line();
        line("<<SUMMARY>>");
        line();
    }

    private String summary() {
        StringBuilder s =
                new StringBuilder("## Summary\n\n| Budget | Figure | Margin | Verdict |\n|---|---|---|---|\n");
        for (Map.Entry<String, String> e : verdicts.entrySet()) {
            s.append(e.getValue()).append('\n');
        }
        return s.toString();
    }

    private void verdict(String id, String what, double figure, double budget, String unit) {
        double margin = figure <= 0 ? Double.POSITIVE_INFINITY : budget / figure;
        // Only times scale with the processor's speed.
        boolean time = unit.equals("ms");
        String v = figure > budget
                ? "**fails**"
                : !time || margin >= REQUIRED_MARGIN ? "met" : "**met here, but not with the margin**";
        verdicts.put(
                id + what,
                String.format(
                        "| %s %s (≤ %s %s) | %s %s | %s | %s |",
                        id,
                        what,
                        num(budget),
                        unit,
                        num(figure),
                        unit,
                        margin == Double.POSITIVE_INFINITY ? "∞" : String.format("%.2f", margin),
                        v));
    }

    private void scenario() {
        line("## The scenario as run");
        line();
        line("| What | Value |");
        line("|---|---|");
        for (Map.Entry<String, Object> e : b.scenario.entrySet()) {
            line("| " + e.getKey() + " | " + e.getValue() + " |");
        }
        line("| Pipe networks | 20 (a supply and a waste line for each of 10 rows) |");
        line("| Breaches (each resealed after 10 s) | " + b.breaches + " |");
        line("| Times the wing's player left | " + b.trips + " |");
        line("| Supply top-ups (the bench's stand-in supply) | " + b.topUps + " |");
        line("| Fires relit after going out | " + b.relights + " |");
        if (b.zonesSeen.size() > 0) {
            line(String.format(
                    "| Zones while measuring (every 10 s) | %d to %d, of which paused %d to %d |",
                    min(b.zonesSeen), b.zonesSeen.max(), min(b.pausedSeen), b.pausedSeen.max()));
            line("| Each sample: zones / paused / the wing's "
                    + b.station.wingChunks().size() + " chunks loaded, and how the wing's rooms read | "
                    + String.join("; ", b.zoneSamples) + " |");
            line(String.format(
                    "| Samples with the wing's player away in which its rooms paused or unloaded | %s of %d |",
                    b.wingPausedSamples == 0 && b.wingAwaySamples > 0 ? "**0**" : String.valueOf(b.wingPausedSamples),
                    b.wingAwaySamples));
            if (!b.wingTickets.isEmpty()) {
                line("| Chunk tickets on the wing while its player was away | " + String.join("; ", b.wingTickets)
                        + " |");
            }
            if (!b.wingLoads.isEmpty()) {
                line("| What loaded the wing's chunks while its player was away (times) | " + b.wingLoads + " |");
            }
        }
        line();
        for (Map.Entry<String, List<String>> e : b.fireChecks.entrySet()) {
            line("Fires at " + e.getKey() + ": " + String.join("; ", e.getValue()) + ".");
            line();
        }
    }

    private void serverThread(Window w, String title) {
        line("## " + title);
        line();
        line(String.format(
                "%,d server ticks over %.0f s. Per server tick, all of `zas` and `zas_atmos` together:",
                w.ticks, w.seconds()));
        line();
        line("| | ms |");
        line("|---|---|");
        line(String.format("| Average | %.3f |", w.atmos.average() / MS));
        line(String.format("| 99th percentile | %.3f |", w.atmos.quantile(0.99) / MS));
        line(String.format("| Largest | %.3f |", w.atmos.max() / MS));
        line();
        if (w == b.measure) {
            verdict("NFR-001", "average", w.atmos.average() / MS, 2, "ms");
            verdict("NFR-001", "99th percentile", w.atmos.quantile(0.99) / MS, 5, "ms");
            // NFR-002 says no server tick, not only the explosion's; its Verify is the explosion.
            verdict("NFR-002", "largest tick while the scenario runs", w.atmos.max() / MS, 10, "ms");
            line(String.format("Ticks over NFR-002's 10 ms: %d of %,d.", w.atmos.countAbove(10_000_000L), w.ticks));
            line();
        }
        line("By NFR-001's list (average per server tick, and its share):");
        line();
        Map<String, double[]> byCategory = new TreeMap<>();
        List<Clock.Part> parts = Clock.parts();
        double total = Math.max(1, w.atmos.sum());
        for (Clock.Part p : parts) {
            if (p.index >= w.parts.size()) {
                continue;
            }
            Window.Series s = w.parts.get(p.index);
            byCategory.computeIfAbsent(p.category, k -> new double[1])[0] += s.sum();
        }
        line("| Category | ms per tick | Share |");
        line("|---|---|---|");
        byCategory.entrySet().stream()
                .sorted((a, c) -> Double.compare(c.getValue()[0], a.getValue()[0]))
                .forEach(e -> line(String.format(
                        "| %s | %.4f | %.1f %% |",
                        e.getKey(), e.getValue()[0] / w.ticks / MS, 100 * e.getValue()[0] / total)));
        line();
        line("By part (each listener, ticker or hook; a call made inside another counts in the outer one,"
                + " except that each device's step counts by its type, out of the device ticker's):");
        line();
        line("| Part | Category | ms per tick | 99th pct ms | Largest ms | Ticks it ran |");
        line("|---|---|---|---|---|---|");
        parts.stream()
                .filter(p -> p.index < w.parts.size() && w.parts.get(p.index).sum() > 0)
                .sorted(Comparator.comparingLong(
                                (Clock.Part p) -> w.parts.get(p.index).sum())
                        .reversed())
                .forEach(p -> {
                    Window.Series s = w.parts.get(p.index);
                    line(String.format(
                            "| %s | %s | %.4f | %.3f | %.3f | %,d |",
                            p.name,
                            p.category,
                            s.sum() / (double) w.ticks / MS,
                            s.quantile(0.99) / MS,
                            s.max() / MS,
                            s.nonZero()));
                });
        line();
        worstTicks(w, 5);
    }

    private void worstTicks(Window w, int n) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < w.ticks; i++) {
            order.add(i);
        }
        order.sort((x, y) -> Long.compare(w.atmos.get(y), w.atmos.get(x)));
        line("The " + n + " slowest ticks, and where their time went:");
        line();
        List<Clock.Part> parts = Clock.parts();
        for (int k = 0; k < Math.min(n, order.size()); k++) {
            int i = order.get(k);
            List<String> top = new ArrayList<>();
            parts.stream()
                    .filter(p ->
                            p.index < w.parts.size() && w.parts.get(p.index).get(i) > 0)
                    .sorted(Comparator.comparingLong(
                                    (Clock.Part p) -> w.parts.get(p.index).get(i))
                            .reversed())
                    .limit(4)
                    .forEach(p -> top.add(String.format(
                            "%s %.2f", p.name, w.parts.get(p.index).get(i) / MS)));
            long gc = GcPauses.within(w.starts.get(i), w.starts.get(i) + w.wall.get(i));
            line(String.format(
                    "- tick %,d (%.1f s in): %.3f ms: %s%s",
                    i,
                    i / 20.0,
                    w.atmos.get(i) / MS,
                    String.join(", ", top),
                    gc > 0 ? String.format(" (a collector pause of %.1f ms fell in this tick)", gc / MS) : ""));
        }
        line();
    }

    private void closeAndApply(Window w) {
        line("### Closing and applying the station's batches");
        line();
        line("Once per atmos tick each; their time is inside the server-thread figures above (`AtmosService`).");
        line();
        line("| | Batches | Average ms | 99th pct ms | Largest ms |");
        line("|---|---|---|---|---|");
        line(row("Close (capture and prefetch)", w.close));
        line(row("Apply", w.apply));
        line();
        line(String.format(
                "Sections a batch carried: %.1f on average, %d at most.",
                w.batchSections.average(), w.batchSections.max()));
        line();
    }

    private static String row(String what, Window.Series s) {
        return String.format(
                "| %s | %,d | %.3f | %.3f | %.3f |",
                what, s.size(), s.average() / MS, s.quantile(0.99) / MS, s.max() / MS);
    }

    private void worker(Window w) {
        line("## NFR-005: the worker");
        line();
        line("The station's atmos ticks on the one worker thread (`worker_threads` is 1 by default), whole and by"
                + " phase. The other three dimensions share the thread; they hold no zones.");
        line();
        Window.Series whole = new Window.Series();
        List<Window.Series> phases = new ArrayList<>();
        for (int i = 0; i < WorkerTimes.PHASES.length; i++) {
            phases.add(new Window.Series());
        }
        for (long[] r : w.worker) {
            whole.add(r[WorkerTimes.WHOLE]);
            for (int i = 0; i < WorkerTimes.PHASES.length; i++) {
                phases.get(i).add(r[i]);
            }
        }
        line("| | Ticks | Average ms | 99th pct ms | Largest ms |");
        line("|---|---|---|---|---|");
        line(row("Whole tick", whole));
        for (int i = 0; i < WorkerTimes.PHASES.length; i++) {
            line(row(WorkerTimes.PHASES[i].name(), phases.get(i)));
        }
        line();
        verdict("NFR-005", "largest worker tick", whole.max() / MS, 250, "ms");
    }

    private void sync(Window w) {
        line("## DATA-006, DATA-014, DATA-015: what each player was sent");
        line();
        line("Counted at each player's connection: every packet the server sent it, encoded with the play"
                + " protocol's codec, compressed as the server compresses (threshold "
                + b.server.getCompressionThreshold()
                + ") and framed. TCP and IP headers aren't counted; encryption adds no bytes. Atmos sync is"
                + " the mods' own payloads and their sounds; no player had the debug overlay on.");
        line();
        double seconds = w.seconds();
        Map<String, double[]> perPayload = new TreeMap<>();
        line("| Player | Atmos B/s | Own state (DATA-014) B/s | Airflow (DATA-015) B/s | Vanilla B/s |");
        line("|---|---|---|---|---|");
        double atmosSum = 0;
        int players = 0;
        for (Map.Entry<String, Map<String, long[]>> e : w.bytes.entrySet()) {
            long atmos = 0;
            long own = 0;
            long airflow = 0;
            long vanilla = 0;
            for (Map.Entry<String, long[]> k : e.getValue().entrySet()) {
                String what = k.getKey();
                long bytes = k.getValue()[1];
                if (what.startsWith(BenchPlayers.ATMOS)) {
                    atmos += bytes;
                    double[] pp =
                            perPayload.computeIfAbsent(what.substring(BenchPlayers.ATMOS.length()), x -> new double[2]);
                    pp[0] += k.getValue()[0];
                    pp[1] += bytes;
                    if (what.endsWith("zas:atmos_hud")) {
                        own += bytes;
                    }
                    if (what.contains("zas:airflow")) {
                        airflow += bytes;
                    }
                } else {
                    vanilla += bytes;
                }
            }
            atmosSum += atmos / seconds;
            players++;
            line(String.format(
                    "| %s | %.1f | %.1f | %.1f | %.0f |",
                    e.getKey(), atmos / seconds, own / seconds, airflow / seconds, vanilla / seconds));
        }
        line();
        double average = players == 0 ? 0 : atmosSum / players;
        line(String.format("Average atmos sync per player: **%.1f B/s** (DATA-006's budget is 2,048 B/s).", average));
        line();
        verdict("DATA-006", "average atmos sync per player", average, 2048, "B/s");
        line("By payload, over all players (messages per player per second, and bytes per player per second):");
        line();
        line("| Payload | Messages/s | B/s | Bytes each |");
        line("|---|---|---|---|");
        int n = Math.max(1, players);
        perPayload.entrySet().stream()
                .sorted((x, y) -> Double.compare(y.getValue()[1], x.getValue()[1]))
                .forEach(e -> line(String.format(
                        "| %s | %.2f | %.1f | %.1f |",
                        e.getKey(),
                        e.getValue()[0] / n / seconds,
                        e.getValue()[1] / n / seconds,
                        e.getValue()[1] / Math.max(1, e.getValue()[0]))));
        line();
        if (!b.players.encodeFailures.isEmpty()) {
            line("Packets that couldn't be encoded, so aren't counted: " + b.players.encodeFailures + ".");
            line();
        }
    }

    private void escapesInRun(Window w) {
        line("### Spike #80 in the scenario itself");
        line();
        line(String.format(
                "Station batches that carried sections the worker had asked for, because a fill or a breach check"
                        + " reached past what it held: %d (%d sections), of %d batches.",
                w.requestBatches, w.requestedSections, w.apply.size()));
        line();
    }

    private void heap() {
        line("## NFR-004: atmos heap");
        line();
        AtmosHeap h = b.heap;
        if (h == null) {
            line("Not measured.");
            line();
            return;
        }
        line("Retained size, each object counted once at HotSpot's shallow size and charged to the first part"
                + " that reaches it, walked as the engine bench walks its station, then on through the platform.");
        line();
        line("| Part | Bytes | MiB |");
        line("|---|---|---|");
        for (AtmosHeap.Part p : AtmosHeap.Part.values()) {
            line(String.format("| %s | %,d | %.2f |", p.label, h.bytes(p), h.bytes(p) / 1048576.0));
        }
        long engine = h.total(true);
        long all = h.total(false);
        line(String.format(
                "| **Engine and pipeline (the engine bench's count)** | %,d | %.2f |", engine, engine / 1048576.0));
        line(String.format("| **Everything above** | %,d | %.2f |", all, all / 1048576.0));
        line();
        Object cellsObj = b.scenario.get("Zoned cells at the heap walk");
        Object zonesObj = b.scenario.get("Zones at the heap walk");
        int cells = cellsObj instanceof Integer c ? c : 0;
        int zones = zonesObj instanceof Integer z ? z : 0;
        double perCell = cells == 0 ? 0 : (double) h.total(AtmosHeap.Scale.CELL) / cells;
        double perZone = zones == 0 ? 0 : (double) h.total(AtmosHeap.Scale.ZONE) / zones;
        line(String.format(
                "%,d zoned cells and %,d zones: %.0f bytes per zoned cell in the parts that scale with cells, and"
                        + " %.0f bytes per zone in the parts that scale with zones.",
                cells, zones, perCell, perZone));
        line();
        verdict("NFR-004", "bytes per zoned cell", perCell, 256, "B");
        verdict("NFR-004", "bytes per zone", perZone, 4096, "B");
        verdict("NFR-004", "total", all / 1048576.0, 64, "MiB");
        line("Largest classes in each part:");
        line();
        for (AtmosHeap.Part p : AtmosHeap.Part.values()) {
            List<String> top = h.topClasses(p, 3);
            if (!top.isEmpty()) {
                line("- " + p.name() + ": " + String.join("; ", top));
            }
        }
        line();
    }

    private void blast() {
        line("## NFR-002: an explosion breaching the 8,000-cell zone");
        line();
        Window w = b.blast;
        if (w == null) {
            line("Not run.");
            line();
            return;
        }
        line(String.format(
                "TNT's power inside the big room by its east wall, then %.0f s with the scenario running. Ticks over"
                        + " 10 ms of atmos work: **%d** of %,d. The largest: %.3f ms. The zone: %s.",
                w.seconds(), w.atmos.countAbove(10_000_000L), w.ticks, w.atmos.max() / MS, b.blastVerdict));
        line();
        verdict("NFR-002", "largest tick after the explosion", w.atmos.max() / MS, 10, "ms");
        verdicts.put(
                "NFR-002 vents",
                "| NFR-002 the zone still vents correctly | " + (b.blastVented ? "as modelled" : "not as modelled")
                        + " | | " + (b.blastVented ? "met" : "**fails**") + " |");
        line("The big room's middle, each second:");
        line();
        line("```");
        b.blastTrace.forEach(this::line);
        line("```");
        line();
        worstTicks(w, 5);
        closeAndApply(w);
    }

    private void survey() {
        line("## Spike #80: enclosure fills against the prefetch cube");
        line();
        if (b.survey == null) {
            line("Not run.");
            line();
            return;
        }
        Window w = b.surveyWindow;
        double medianClose = w == null ? 0 : w.close.quantile(0.5) / MS;
        line("Each shape on its own, in station wall far from the station, filled by one API add at its trigger"
                + " point. Rounds are atmos ticks from the batch that carried the add to the one that completed"
                + " it; more than 1 means the fill escaped the 7³-section cube the server thread prefetched."
                + String.format(" The survey's median batch close, for comparison: %.3f ms.", medianClose));
        line();
        line("| Shape | Cells | Outcome | Zone's cells | Rounds | Batches asking for sections | Sections asked"
                + " for | First batch: sections, close ms | Largest worker tick ms |");
        line("|---|---|---|---|---|---|---|---|---|");
        for (EscapeSurvey.Result r : b.survey.results) {
            String first = r.closeNanos.isEmpty()
                    ? "-"
                    : String.format("%d, %.3f", r.batchSections.getFirst(), r.closeNanos.getFirst() / MS);
            line(String.format(
                    "| %s | %,d | %s | %,d | %d | %d | %d | %s | %.1f |",
                    r.shape.name(),
                    r.shape.cells(),
                    r.outcome,
                    r.zoneCells,
                    r.rounds(),
                    r.requestBatches,
                    r.requestedSections,
                    first,
                    r.maxWorkerNanos / MS));
        }
        line();
        line("There's no sample of real player bases, so how often players build shapes like these is unknown.");
        line();
    }

    // ---- The enclosure cap step ----------------------------------------------------------------

    /** A row of a hall's table. {@code pass} is false for a budget missed, or met without the margin. */
    private record CapRow(
            String step,
            String what,
            String figure,
            String budget,
            String margin,
            String verdict,
            String pauses,
            boolean pass) {}

    private static final String[] CAP_STEPS = {
        "a. Zoning", "b. Open fill", "c. Breach and reseal", "d. Split and merge", "e. Running cost", "f. Memory"
    };

    private void capStep() {
        line("## Enclosure cap step (owner, 2026-11-08; GEO-020, D-23)");
        line();
        CapStep c = b.cap;
        if (c == null) {
            line("Not run.");
            line();
            return;
        }
        line("How far `[atmos] enclosure_max_cells` can go. The scenario and the survey above ran at the default cap of"
                + String.format(
                        " %,d. Then the station was let go (its players left and its chunks unloaded),",
                        c.configuredCap)
                + " and each hall below was built in `zas_s10:halls`, a void dimension of its own with a vacuum"
                + " ambient, with the cap set to the hall's cells and one player by it, in ticking chunks. Server"
                + " ticks are the bench's whole count of `zas` and `zas_atmos` on the server thread; worker ticks"
                + " are the halls' dimension's own.");
        line();
        line("- a. One add in the middle of a fresh sealed hall zones it. Time to zone runs from the add to its"
                + " result's apply; atmos ticks are the batches from the one that carried the add.");
        line("- b. A twin of the hall with a notch in one wall, so one cell over the cap, roofed so the column"
                + " check can't answer: an add inside it must come out open, after a fill to the cap and one cell"
                + " more. Each repeat adds at a cell no add used before, which isn't cached (GEO-027).");
        line("- c. Under ZAS's venting (`venting = \"FAITHFUL\"`), so the hall empties: a 3 × 3 hole in the middle"
                + " of the roof opens it to the vacuum, it vents until its zone dissolves (CORE-076), the hole is"
                + " filled, and an add must zone the whole hall again. The dissolve's ticks are given on their own:"
                + " the worker tick that dissolved the zone and the server tick that applied it. The venting"
                + " setting goes back afterwards.");
        line("- d. A wall across the middle is built with one block missing; placing that block splits the hall"
                + " (CORE-024), taking it out again merges the halves (CORE-023). Times are to the next server tick"
                + " that reads the change.");
        line("- e. The server's atmos time per tick with the hall built but not zoned (the baseline), zoned and"
                + " idle with the player outside it, and active: a generator, an area air controller and a vent in"
                + " the hall, below the controller's target so the vent blows, its network topped up each second,"
                + " and the player inside, breathing. The hall's share is the difference from the baseline.");
        line("- f. The halls' engine and platform walked before and after the hall zones: bytes per zoned cell are"
                + " what the parts that scale with cells grew by, over the hall's cells; NFR-004's 64 MiB is judged"
                + " on the halls dimension's whole atmos heap with the hall zoned, what it held before plus what"
                + " zoning added.");
        line();
        line("Time budgets need a margin of " + REQUIRED_MARGIN + "; memory needs none. NFR-002 is judged on zas's own"
                + " time: a collector pause inside zas's calls in that tick is taken out, and given in the last"
                + " column with the tick's other pauses. Ticks over 10 ms are listed after each table, with where"
                + " their time went and the batch close and apply in them, so a stall that isn't the work shows.");
        line();
        for (Map.Entry<String, String> e : c.notes.entrySet()) {
            line("- " + e.getValue());
        }
        if (!c.notes.isEmpty()) {
            line();
        }
        List<String> summary = new ArrayList<>();
        CapStep.Hall best = null;
        boolean smallerFailed = false;
        for (CapStep.Hall h : c.halls) {
            List<CapRow> rows = capRows(h);
            line(String.format("### %s: %,d cells, the cap at %,d", h.label(), h.cells(), h.cells()));
            line();
            line("| Step | What | Figure | Budget | Margin | Verdict | GC pauses and stalls |");
            line("|---|---|---|---|---|---|---|");
            for (CapRow r : rows) {
                line(String.format(
                        "| %s | %s | %s | %s | %s | %s | %s |",
                        r.step(), r.what(), r.figure(), r.budget(), r.margin(), r.verdict(), r.pauses()));
            }
            line();
            capFacts(h);
            StringBuilder s = new StringBuilder(String.format("| %s | %,d |", h.label(), h.cells()));
            boolean all = true;
            for (String step : CAP_STEPS) {
                boolean ok = rows.stream().filter(r -> r.step().equals(step)).allMatch(CapRow::pass);
                all &= ok;
                s.append(ok ? " met |" : " **no** |");
            }
            s.append(all ? " **yes** |" : " no |");
            summary.add(s.toString());
            if (all) {
                best = h;
            } else if (best == null) {
                smallerFailed = true;
            }
        }
        line("### Every size");
        line();
        StringBuilder head = new StringBuilder("| Hall | Cells |");
        StringBuilder rule = new StringBuilder("|---|---|");
        for (String step : CAP_STEPS) {
            head.append(' ').append(step).append(" |");
            rule.append("---|");
        }
        line(head + " Every budget |");
        line(rule + "---|");
        summary.forEach(this::line);
        line();
        String answer = best == null ? "none of them" : String.format("%s, %,d cells", best.label(), best.cells());
        line("The largest hall that passes every budget: **" + answer + "**."
                + (best != null && smallerFailed ? " A smaller one didn't, so read the tables." : ""));
        line();
        verdicts.put(
                "GEO-020 cap",
                "| GEO-020 enclosure cap step: the largest hall that passes every budget | " + answer + " | | "
                        + (best == null ? "**none passes**" : "see the step") + " |");
    }

    private List<CapRow> capRows(CapStep.Hall h) {
        List<CapRow> rows = new ArrayList<>();
        String zoning = CAP_STEPS[0];
        if (h.zoning == null) {
            rows.add(must(zoning, "Outcome", "not run", "ZONE, the whole hall", false));
        } else {
            CapStep.Event e = h.zoning;
            rows.add(must(
                    zoning,
                    "Outcome",
                    String.format("%s, %,d cells", e.outcome, h.zoneCells),
                    "ZONE, the whole hall",
                    e.outcome.equals("ZONE") && h.zoneCells == h.cells()));
            rows.add(workerRow(zoning, List.of(e)));
            rows.add(serverRow(zoning, List.of(e)));
            rows.add(info(zoning, "Time to zone", String.format("%,.0f ms, %s", e.msToDone(), rounds(e.rounds()))));
        }
        String openFill = CAP_STEPS[1];
        if (h.openFills.isEmpty()) {
            rows.add(must(openFill, "Outcome", "not run", "open (AMBIENT)", false));
        } else {
            List<String> outcomes = h.openFills.stream().map(e -> e.outcome).toList();
            rows.add(must(
                    openFill,
                    "Outcome of each repeat",
                    String.join(", ", outcomes),
                    "open (AMBIENT)",
                    outcomes.stream().allMatch("AMBIENT"::equals)));
            rows.add(workerRow(openFill, h.openFills));
            rows.add(serverRow(openFill, h.openFills));
            rows.add(info(
                    openFill,
                    "Time to the outcome, each repeat",
                    String.join(
                            "; ",
                            h.openFills.stream()
                                    .map(e -> String.format("%,.0f ms, %s", e.msToDone(), rounds(e.rounds())))
                                    .toList())));
        }
        String breach = CAP_STEPS[2];
        String expected = "dissolved, then zoned again whole";
        if (h.breach == null) {
            rows.add(must(breach, "Outcome", "not run", expected, false));
        } else {
            CapStep.Event e = h.breach;
            boolean dissolved = h.dissolveTick >= 0;
            rows.add(must(
                    breach,
                    "Outcome",
                    String.format(
                            "%s, %s, %,d cells", dissolved ? "dissolved" : "not dissolved", e.outcome, h.rezoneCells),
                    expected,
                    dissolved && e.outcome.equals("zoned again: ZONE") && h.rezoneCells == h.cells()));
            rows.add(workerTick(
                    breach,
                    "Dissolve: the worker tick that dissolved the zone",
                    dissolved && h.dissolveWorker >= 0 ? e.window.worker.get(h.dissolveWorker) : null));
            rows.add(serverTick(
                    breach,
                    "Dissolve: the server tick that applied it, zas's own time",
                    e.window,
                    dissolved ? h.dissolveTick : -1));
            rows.add(workerRow(breach, List.of(e)));
            rows.add(serverRow(breach, List.of(e)));
            rows.add(info(
                    breach,
                    "Time from the hole to the dissolve; from the add after the reseal to zoned",
                    String.format("%.1f s; %,.0f ms, %s", h.dissolveSeconds, e.msToDone(), rounds(e.rounds()))));
        }
        String splitMerge = CAP_STEPS[3];
        List<CapStep.Event> both = new ArrayList<>(h.splits);
        both.addAll(h.merges);
        if (both.isEmpty()) {
            rows.add(must(splitMerge, "Outcome", "not run", "each splits and merges", false));
        } else {
            long split = h.splits.stream().filter(CapStep.Event::done).count();
            long merged = h.merges.stream().filter(CapStep.Event::done).count();
            rows.add(must(
                    splitMerge,
                    "Outcome",
                    String.format("split %d of %d, merged %d of %d", split, h.splits.size(), merged, h.merges.size()),
                    "each splits and merges",
                    split == h.splits.size() && merged == h.merges.size()));
            rows.add(workerRow(splitMerge, both));
            rows.add(serverRow(splitMerge, both));
            rows.add(info(
                    splitMerge,
                    "Time to split, and to merge, the slowest",
                    String.format(
                            "%,.0f ms, %,.0f ms",
                            h.splits.stream()
                                    .mapToDouble(CapStep.Event::msToDone)
                                    .max()
                                    .orElse(Double.NaN),
                            h.merges.stream()
                                    .mapToDouble(CapStep.Event::msToDone)
                                    .max()
                                    .orElse(Double.NaN))));
        }
        String running = CAP_STEPS[4];
        if (h.baseline == null || h.idle == null || h.active == null) {
            rows.add(must(running, "Windows", "not all run", "baseline, idle and active", false));
        } else {
            double base = h.baseline.atmos.average() / MS;
            rows.add(info(running, "Baseline, not zoned: average per server tick", String.format("%.3f ms", base)));
            for (Window w : new Window[] {h.idle, h.active}) {
                String which = w == h.idle ? "Idle" : "Active";
                double avg = w.atmos.average() / MS;
                rows.add(judged(
                        running,
                        String.format("%s: average per server tick (the hall's share %+.3f ms)", which, avg - base),
                        avg,
                        2,
                        "ms",
                        "NFR-001",
                        ""));
            }
            rows.add(judged(
                    running,
                    "Active: 99th percentile per server tick",
                    h.active.atmos.quantile(0.99) / MS,
                    5,
                    "ms",
                    "NFR-001",
                    ""));
            CapRow worker = workerRow(running, List.of(), h.idle, h.active);
            rows.add(new CapRow(
                    worker.step(),
                    "Largest worker tick, idle and active",
                    worker.figure(),
                    worker.budget(),
                    worker.margin(),
                    worker.verdict(),
                    worker.pauses(),
                    worker.pass()));
            rows.add(info(
                    running,
                    "Worker ticks' average, idle and active",
                    String.format("%.2f ms, %.2f ms", workerAverage(h.idle) / MS, workerAverage(h.active) / MS)));
            rows.add(info(
                    running,
                    "The halls' batch close and apply, per server tick, active",
                    String.format(
                            "%.4f ms",
                            (h.active.close.sum() + h.active.apply.sum())
                                    / (double) Math.max(1, h.active.ticks)
                                    / MS)));
            rows.add(info(running, "The vent at the end", h.vent));
        }
        String memory = CAP_STEPS[5];
        if (h.heapBefore == null || h.heapAfter == null || h.zoneCells == 0) {
            rows.add(must(memory, "Heap", "not walked", "before and after zoning", false));
        } else {
            long cellParts = h.heapAfter.total(AtmosHeap.Scale.CELL) - h.heapBefore.total(AtmosHeap.Scale.CELL);
            rows.add(judged(
                    memory,
                    "Bytes per zoned cell, in the parts that scale with cells",
                    (double) cellParts / h.zoneCells,
                    256,
                    "B",
                    "NFR-004",
                    ""));
            long before = h.heapBefore.total(false);
            long after = h.heapAfter.total(false);
            rows.add(info(
                    memory,
                    "What zoning the hall added, and what the dimension held before",
                    String.format(
                            "%.2f MiB (%.0f B per cell), on %.2f MiB",
                            (after - before) / 1048576.0,
                            (double) (after - before) / h.zoneCells,
                            before / 1048576.0)));
            rows.add(judged(
                    memory,
                    "The halls dimension's whole atmos heap, the hall zoned",
                    after / 1048576.0,
                    64,
                    "MiB",
                    "NFR-004",
                    ""));
        }
        return rows;
    }

    /** The hall's facts, notes, and where the largest ticks' time went. */
    private void capFacts(CapStep.Hall h) {
        List<String> facts = new ArrayList<>(h.notes);
        if (h.breach != null) {
            h.breach.facts.forEach(x -> facts.add("Breach: " + x));
        }
        if (h.heapAfter != null && h.heapBefore != null) {
            List<String> grew = new ArrayList<>();
            for (AtmosHeap.Part p : AtmosHeap.Part.values()) {
                long d = h.heapAfter.bytes(p) - h.heapBefore.bytes(p);
                if (Math.abs(d) >= 64 * 1024) {
                    grew.add(String.format("%s %+.2f MiB", p.name(), d / 1048576.0));
                }
            }
            if (!grew.isEmpty()) {
                facts.add("What zoning the hall added to the heap: " + String.join(", ", grew));
            }
            List<String> held = new ArrayList<>();
            for (AtmosHeap.Part p : AtmosHeap.Part.values()) {
                if (h.heapBefore.bytes(p) >= 256 * 1024) {
                    held.add(String.format(
                            "%s %.2f MiB (%s)",
                            p.name(),
                            h.heapBefore.bytes(p) / 1048576.0,
                            String.join("; ", h.heapBefore.topClasses(p, 2))));
                }
            }
            facts.add(String.format(
                    "The halls' dimension before the hall zoned: %.2f MiB%s",
                    h.heapBefore.total(false) / 1048576.0,
                    held.isEmpty() ? "" : ", of which " + String.join(", ", held)));
        }
        List<String> slow = new ArrayList<>();
        List<Window> windows = new ArrayList<>();
        for (Window w : new Window[] {h.baseline, h.idle, h.active}) {
            if (w != null) {
                windows.add(w);
            }
        }
        List<CapStep.Event> events = new ArrayList<>(h.openFills);
        if (h.zoning != null) {
            events.add(h.zoning);
        }
        events.addAll(h.splits);
        events.addAll(h.merges);
        if (h.breach != null) {
            events.add(h.breach);
        }
        events.forEach(e -> windows.add(e.window));
        for (Window w : windows) {
            for (int i = 0; i < w.ticks; i++) {
                if (w.atmos.get(i) > 10_000_000L) {
                    slow.add(String.format(
                            "%s, tick %d: %.3f ms, zas's own %.3f ms%s; %s; %s",
                            w.name,
                            i,
                            w.atmos.get(i) / MS,
                            own(w, i) / MS,
                            tickPause(w, i) > 0
                                    ? String.format(", a %.1f ms pause in the tick", tickPause(w, i) / MS)
                                    : "",
                            topParts(w, i, 3),
                            batchWork(w, i)));
                }
            }
        }
        for (CapStep.Event e : events) {
            long[] r = largestWorker(List.of(e.window));
            if (r != null) {
                facts.add(String.format(
                        "%s: largest worker tick %.1f ms, mostly %s", e.name, r[WorkerTimes.WHOLE] / MS, topPhases(r)));
            }
        }
        if (h.breach != null && h.dissolveTick >= 0) {
            Window w = h.breach.window;
            String worker = h.dissolveWorker < 0
                    ? "no worker tick recorded"
                    : String.format(
                            "its worker tick %.1f ms, mostly %s",
                            w.worker.get(h.dissolveWorker)[WorkerTimes.WHOLE] / MS,
                            topPhases(w.worker.get(h.dissolveWorker)));
            facts.add(String.format(
                    "Dissolve: %s; the server tick that applied it %.3f ms (%s)",
                    worker, w.atmos.get(h.dissolveTick) / MS, topParts(w, h.dissolveTick, 3)));
        }
        for (Window w : new Window[] {h.idle, h.active}) {
            if (w != null && w.ticks > 0) {
                facts.add((w == h.idle ? "Idle" : "Active") + ", the 3 slowest server ticks: " + slowest(w, 3));
            }
        }
        if (!slow.isEmpty()) {
            facts.add("Server ticks over 10 ms: " + String.join("; ", slow.subList(0, Math.min(12, slow.size())))
                    + (slow.size() > 12 ? String.format("; and %d more", slow.size() - 12) : ""));
        }
        for (String x : facts) {
            line("- " + x);
        }
        if (!facts.isEmpty()) {
            line();
        }
    }

    private CapRow judged(
            String step, String what, double figure, double budget, String unit, String id, String pauses) {
        double margin = figure <= 0 ? Double.POSITIVE_INFINITY : budget / figure;
        boolean time = unit.equals("ms");
        boolean pass = figure <= budget && (!time || margin >= REQUIRED_MARGIN);
        String v = figure > budget ? "**fails**" : pass ? "met" : "**met here, but not with the margin**";
        return new CapRow(
                step,
                what,
                num(figure) + " " + unit,
                id + " ≤ " + num(budget) + " " + unit,
                margin == Double.POSITIVE_INFINITY ? "∞" : String.format("%.2f", margin),
                v,
                pauses,
                pass);
    }

    private static CapRow info(String step, String what, String figure) {
        return new CapRow(step, what, figure, "", "", "", "", true);
    }

    private static CapRow must(String step, String what, String figure, String expected, boolean ok) {
        return new CapRow(step, what, figure, expected, "", ok ? "met" : "**fails**", "", ok);
    }

    private static String rounds(long n) {
        return n < 0 ? "rounds unknown" : n == 1 ? "1 atmos tick" : n + " atmos ticks";
    }

    /** The largest server tick by zas's own time over the events' windows, judged on NFR-002. */
    private CapRow serverRow(String step, List<CapStep.Event> events) {
        Window worst = null;
        int at = -1;
        long best = -1;
        long withPauses = 0;
        for (CapStep.Event e : events) {
            Window w = e.window;
            for (int i = 0; i < w.ticks; i++) {
                long own = own(w, i);
                if (own > best) {
                    best = own;
                    worst = w;
                    at = i;
                }
                withPauses = Math.max(withPauses, w.atmos.get(i));
            }
        }
        if (worst == null) {
            return must(step, "Largest server tick, zas's own time", "none recorded", "NFR-002 ≤ 10 ms", false);
        }
        String pauses = pauses(worst, at);
        if (withPauses > worst.atmos.get(at)) {
            pauses += String.format("; the largest tick with its pauses: %.3f ms", withPauses / MS);
        }
        return judged(step, "Largest server tick, zas's own time", best / MS, 10, "ms", "NFR-002", pauses);
    }

    /** One server tick, judged on NFR-002 by zas's own time; {@code at} -1 for none. */
    private CapRow serverTick(String step, String what, Window w, int at) {
        if (at < 0 || at >= w.ticks) {
            return must(step, what, "none recorded", "NFR-002 ≤ 10 ms", false);
        }
        return judged(step, what, own(w, at) / MS, 10, "ms", "NFR-002", pauses(w, at));
    }

    /** What a tick's batch close and apply took, inside {@code AtmosService}'s time, if it had either. */
    private static String batchWork(Window w, int i) {
        long[] b = w.batchWork.get(i);
        if (b == null) {
            return "no batch closed or applied in it";
        }
        List<String> out = new ArrayList<>();
        if (b[0] >= 0) {
            out.add(String.format("its batch close %.3f ms", b[0] / MS));
        }
        if (b[1] >= 0) {
            out.add(String.format("its batch apply %.3f ms", b[1] / MS));
        }
        return String.join(", ", out);
    }

    /** A tick's collector pauses, and how much of them fell in zas's calls. */
    private static String pauses(Window w, int at) {
        long pause = tickPause(w, at);
        return pause == 0
                ? "none in that tick"
                : String.format(
                        "a %.1f ms pause in that tick, %.1f ms of it in zas's calls", pause / MS, zasPause(w, at) / MS);
    }

    private CapRow workerRow(String step, List<CapStep.Event> events, Window... more) {
        List<Window> windows = new ArrayList<>();
        events.forEach(e -> windows.add(e.window));
        windows.addAll(List.of(more));
        return workerTick(step, "Largest worker tick", largestWorker(windows));
    }

    /** One worker tick, judged on NFR-005; null for none. */
    private CapRow workerTick(String step, String what, long[] r) {
        if (r == null) {
            return must(step, what, "none recorded", "NFR-005 ≤ 250 ms", false);
        }
        long pause = GcPauses.within(r[WorkerTimes.START], r[WorkerTimes.START] + r[WorkerTimes.WHOLE]);
        return judged(
                step,
                what,
                r[WorkerTimes.WHOLE] / MS,
                250,
                "ms",
                "NFR-005",
                pause == 0 ? "none in that tick" : String.format("a %.1f ms pause in that tick", pause / MS));
    }

    private static long[] largestWorker(List<Window> windows) {
        long[] best = null;
        for (Window w : windows) {
            for (long[] r : w.worker) {
                if (best == null || r[WorkerTimes.WHOLE] > best[WorkerTimes.WHOLE]) {
                    best = r;
                }
            }
        }
        return best;
    }

    private static double workerAverage(Window w) {
        long sum = 0;
        for (long[] r : w.worker) {
            sum += r[WorkerTimes.WHOLE];
        }
        return w.worker.isEmpty() ? 0 : (double) sum / w.worker.size();
    }

    /** A tick's atmos time less any collector pause inside zas's calls. */
    private static long own(Window w, int i) {
        return Math.max(0, w.atmos.get(i) - zasPause(w, i));
    }

    /** Collector pause time inside a tick's timed calls. */
    private static long zasPause(Window w, int i) {
        long[] calls = w.calls.get(i);
        if (calls == null) {
            return 0;
        }
        long sum = 0;
        for (int k = 0; k + 1 < calls.length; k += 2) {
            sum += GcPauses.within(calls[k], calls[k + 1]);
        }
        return sum;
    }

    /** Collector pause time anywhere in a tick. */
    private static long tickPause(Window w, int i) {
        return GcPauses.within(w.starts.get(i), w.starts.get(i) + w.wall.get(i));
    }

    private static String topParts(Window w, int i, int n) {
        List<String> top = new ArrayList<>();
        Clock.parts().stream()
                .filter(p -> p.index < w.parts.size() && w.parts.get(p.index).get(i) > 0)
                .sorted(Comparator.comparingLong(
                                (Clock.Part p) -> w.parts.get(p.index).get(i))
                        .reversed())
                .limit(n)
                .forEach(p -> top.add(
                        String.format("%s %.2f", p.name, w.parts.get(p.index).get(i) / MS)));
        return top.isEmpty() ? "no part" : String.join(", ", top);
    }

    /** A window's slowest ticks by atmos time, each with where its time went. */
    private static String slowest(Window w, int n) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < w.ticks; i++) {
            order.add(i);
        }
        order.sort((x, y) -> Long.compare(w.atmos.get(y), w.atmos.get(x)));
        List<String> out = new ArrayList<>();
        for (int i : order.subList(0, Math.min(n, order.size()))) {
            out.add(String.format("%.3f ms (%s)", w.atmos.get(i) / MS, topParts(w, i, 3)));
        }
        return String.join("; ", out);
    }

    private static String topPhases(long[] r) {
        List<String> top = new ArrayList<>();
        java.util.stream.IntStream.range(0, WorkerTimes.PHASES.length)
                .boxed()
                .sorted((x, y) -> Long.compare(r[y], r[x]))
                .limit(2)
                .forEach(i -> top.add(String.format("%s %.1f ms", WorkerTimes.PHASES[i].name(), r[i] / MS)));
        return String.join(", ", top);
    }

    private void wholeTicks() {
        line("## Whole server ticks, for context");
        line();
        line("From the first listener of a tick to the last, everything the server did, not only atmos work.");
        line();
        line("| Window | Ticks | Average ms | 99th pct ms | Largest ms |");
        line("|---|---|---|---|---|");
        for (Window w : new Window[] {b.measure, b.blast, b.surveyWindow}) {
            if (w != null) {
                line(String.format(
                        "| %s | %,d | %.3f | %.3f | %.3f |",
                        w.name, w.ticks, w.wall.average() / MS, w.wall.quantile(0.99) / MS, w.wall.max() / MS));
            }
        }
        line();
    }

    private void notes() {
        line("## Notes");
        line();
        for (Map.Entry<String, String> e : b.notes.entrySet()) {
            line("- " + e.getKey() + ": " + e.getValue());
        }
        line("- What isn't timed: Minecraft's own work for the mods' blocks and entities (block updates, rendering"
                + " packets, entity ticks of the cows themselves), and anything on other threads.");
        line("- The station's walls conduct no heat (CONT-129), so rooms don't warm each other through them.");
        line("- `[fire] spread_chance` is 0, so the 2 hotspots stay 2. A fire spreading through a large flammable"
                + " zone isn't benchmarked here.");
        line("- The players stand still; a client's movement packets, and their handling, aren't simulated.");
        if (b.measure != null && !b.measure.events.isEmpty()) {
            line("- What happened while measuring, by tick: " + b.measure.events);
        }
        line();
        long gcCount = 0;
        long gcMillis = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcMillis += Math.max(0, gc.getCollectionTime());
        }
        line(String.format(
                "Garbage collections over the run: %,d, %,d ms in all; of them, pauses seen while the bench ran: %s.",
                gcCount, gcMillis, GcPauses.summary()));
        line();
    }

    private String csv(Window w) {
        StringBuilder s = new StringBuilder("tick,atmos_ns,wall_ns\n");
        for (int i = 0; i < w.ticks; i++) {
            s.append(i)
                    .append(',')
                    .append(w.atmos.get(i))
                    .append(',')
                    .append(w.wall.get(i))
                    .append('\n');
        }
        return s.toString();
    }

    // ---- Helpers -------------------------------------------------------------------------------

    private static long min(Window.Series s) {
        long m = Long.MAX_VALUE;
        for (int i = 0; i < s.size(); i++) {
            m = Math.min(m, s.get(i));
        }
        return s.size() == 0 ? 0 : m;
    }

    private static String num(double v) {
        return v >= 100 ? String.format("%,.0f", v) : String.format("%.3f", v);
    }

    private void line(String s) {
        md.append(s).append('\n');
    }

    private void line() {
        md.append('\n');
    }
}
