package io.github.west3436.zas.api.atmosphere;

import io.github.west3436.zas.api.AnyThread;

/**
 * A summary of a zone's edges, the open connections through which it
 * exchanges gas (API-011).
 *
 * @param zoneEdges the number of edges to other zones
 * @param ambientEdges the number of edges to the ambient; more than zero means
 *     the zone is breached
 * @param openFaces the number of open cell faces across all its edges
 */
@AnyThread
public record EdgeSummary(int zoneEdges, int ambientEdges, int openFaces) {
    /** A zone with no edges at all. */
    public static final EdgeSummary NONE = new EdgeSummary(0, 0, 0);

    /**
     * Checks the counts.
     *
     * @throws IllegalArgumentException if a count is negative
     */
    public EdgeSummary {
        if (zoneEdges < 0 || ambientEdges < 0 || openFaces < 0) {
            throw new IllegalArgumentException("edge counts must be >= 0");
        }
    }
}
