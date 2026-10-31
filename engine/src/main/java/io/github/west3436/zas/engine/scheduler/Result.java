package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * What one atmos tick of one dimension produced, handed back to the server thread and applied
 * there at a fixed game tick (architecture §2.3, §3.2, CORE-069). Immutable.
 *
 * @param tick the atmos tick number, the same as its {@link Batch}'s
 * @param snapshot the zone table to publish (API-046)
 * @param components the exchange components, by index (§3.3)
 * @param outcomes the operations that finished this tick, in sequence order; operations still
 *     waiting for classification (GEO-028) are not listed
 * @param events open-air events, in sequence order (CORE-058)
 * @param conservation the invariant checker's report, or null when it was off (CORE-073)
 * @param mirroredSections the sections the worker now mirrors, ascending: the scheduler passes
 *     them as the interest set when draining dirty sections (GEO-011), and records their chunks'
 *     generations at batch close (CORE-070)
 * @param requestedSections sections the worker wants in the next batch, ascending: loaded
 *     sections a fill reached that the mirror lacks (§4.3); new zone-triggering operations get
 *     their neighbourhood prefetched at batch close instead ({@link Classifier#prefetchRadius()})
 * @param bufferedOps how many operations wait for classification
 */
public record Result(
        long tick,
        AtmosSnapshot snapshot,
        List<Component> components,
        List<OpOutcome> outcomes,
        List<OpenAirEvent> events,
        ConservationReport conservation,
        long[] mirroredSections,
        long[] requestedSections,
        int bufferedOps) {

    public Result {
        Objects.requireNonNull(snapshot, "snapshot");
        components = List.copyOf(components);
        outcomes = List.copyOf(outcomes);
        events = List.copyOf(events);
        mirroredSections = mirroredSections.clone();
        requestedSections = requestedSections.clone();
    }

    @Override
    public long[] mirroredSections() {
        return mirroredSections.clone();
    }

    @Override
    public long[] requestedSections() {
        return requestedSections.clone();
    }

    /** Whether the worker mirrors a section ({@link SectionKeys}). */
    public boolean mirrors(long section) {
        return Arrays.binarySearch(mirroredSections, section) >= 0;
    }

    /** The outcome of an operation finished this tick, or null. */
    public OpOutcome outcome(long seq) {
        int lo = 0;
        int hi = outcomes.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long v = outcomes.get(mid).seq();
            if (v < seq) {
                lo = mid + 1;
            } else if (v > seq) {
                hi = mid - 1;
            } else {
                return outcomes.get(mid);
            }
        }
        return null;
    }
}
