package io.github.west3436.zas.engine.classify;

import java.util.Objects;

/**
 * What a breach of zones opens onto: one region of unzoned cells, classified (GEO-023, GEO-025,
 * GEO-026). {@link BreachResolver#classify} plans these; {@link BreachResolver#apply} carries out
 * what the engine does for each.
 *
 * <p>The arrays are owned by the breach and must not be modified.
 */
public sealed interface Breach {
    /** The unzoned cells next to the zones that lie in this region, sorted ascending. */
    long[] seeds();

    /** The ids of the zones that open onto this region, sorted ascending. */
    long[] zones();

    /**
     * The region is an enclosed pocket: it joins the zones, bringing ambient gas (GEO-023).
     *
     * @param region the pocket's cells and volume
     */
    record Pocket(long[] seeds, long[] zones, FillResult.Enclosed region) implements Breach {
        public Pocket {
            Objects.requireNonNull(seeds, "seeds");
            Objects.requireNonNull(zones, "zones");
            Objects.requireNonNull(region, "region");
        }
    }

    /**
     * The region is open: the openings are ambient edges of the zones, and the zones vent through
     * them (GEO-025). The zone graph already counts every opening onto an unzoned cell as an
     * ambient connection, so nothing more needs doing.
     *
     * @param reason why the region is open
     */
    record Vent(long[] seeds, long[] zones, FillResult.Open.Reason reason) implements Breach {
        public Vent {
            Objects.requireNonNull(seeds, "seeds");
            Objects.requireNonNull(zones, "zones");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The region reaches sections the worker doesn't have (GEO-024). The zones must pause (GEO-030)
     * until the region can be classified (GEO-026): the caller pauses them and classifies the seeds
     * again once the missing sections arrive, for example through {@link PendingClassifications}.
     * While paused they must not exchange across the opening, which the graph counts as an ambient
     * connection.
     *
     * @param missingSections the sections the region reaches that the view doesn't hold, sorted by
     *     {@link io.github.west3436.zas.engine.SectionKeys#compare}
     */
    record Hold(long[] seeds, long[] zones, long[] missingSections) implements Breach {
        public Hold {
            Objects.requireNonNull(seeds, "seeds");
            Objects.requireNonNull(zones, "zones");
            Objects.requireNonNull(missingSections, "missingSections");
        }
    }
}
