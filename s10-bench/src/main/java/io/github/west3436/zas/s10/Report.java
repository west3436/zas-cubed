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
