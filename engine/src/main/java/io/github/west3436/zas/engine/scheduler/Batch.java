package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Everything one atmos tick of one dimension needs, closed on the server thread and handed to
 * the worker (architecture §2.3, §3.2). Plain data: the worker never reads the live world
 * (CORE-049).
 *
 * @param tick the atmos tick number N; batch N closes at game tick N · T
 * @param sections sections captured at batch close, in {@link SectionKeys#compare} order, at
 *     most one per key: dirty sections the worker mirrors (GEO-011) and sections it asked for
 * @param loadedChunks chunks that loaded since the last batch, ascending
 * @param unloadedChunks chunks that unloaded since the last batch, ascending
 * @param unavailableSections sections the worker asked for that couldn't be captured because
 *     their chunk isn't loaded, ascending; a fill that needs them is unresolved (GEO-024)
 * @param ambient the dimension's ambient now (ENV-001)
 * @param ops the operations queued since the last batch, in sequence order
 * @param rejection the previous result's components the server thread discarded, or null
 * @param settings the config for this tick
 */
public record Batch(
        long tick,
        List<SectionSource> sections,
        long[] loadedChunks,
        long[] unloadedChunks,
        long[] unavailableSections,
        Ambient ambient,
        List<GasOp> ops,
        Rejection rejection,
        EngineSettings settings) {

    /**
     * Checks and copies the arguments.
     *
     * @throws IllegalArgumentException if the operations aren't in ascending sequence order
     */
    public Batch {
        sections = List.copyOf(sections);
        loadedChunks = sorted(loadedChunks);
        unloadedChunks = sorted(unloadedChunks);
        unavailableSections = sorted(unavailableSections);
        Objects.requireNonNull(ambient, "ambient");
        ops = List.copyOf(ops);
        for (int i = 1; i < ops.size(); i++) {
            if (ops.get(i).seq() <= ops.get(i - 1).seq()) {
                throw new IllegalArgumentException("operations must be in ascending sequence order");
            }
        }
        Objects.requireNonNull(settings, "settings");
    }

    /** A batch with only operations and an ambient, as tests and the headless harness use. */
    public static Batch of(long tick, Ambient ambient, List<SectionSource> sections, List<GasOp> ops) {
        return new Batch(
                tick, sections, new long[0], new long[0], new long[0], ambient, ops, null, EngineSettings.DEFAULT);
    }

    /** This batch with other settings. */
    public Batch withSettings(EngineSettings newSettings) {
        return new Batch(
                tick,
                sections,
                loadedChunks,
                unloadedChunks,
                unavailableSections,
                ambient,
                ops,
                rejection,
                newSettings);
    }

    /** This batch carrying a rejection. */
    public Batch withRejection(Rejection newRejection) {
        return new Batch(
                tick,
                sections,
                loadedChunks,
                unloadedChunks,
                unavailableSections,
                ambient,
                ops,
                newRejection,
                settings);
    }

    private static long[] sorted(long[] keys) {
        long[] copy = keys.clone();
        Arrays.sort(copy);
        return copy;
    }

    @Override
    public long[] loadedChunks() {
        return loadedChunks.clone();
    }

    @Override
    public long[] unloadedChunks() {
        return unloadedChunks.clone();
    }

    @Override
    public long[] unavailableSections() {
        return unavailableSections.clone();
    }
}
