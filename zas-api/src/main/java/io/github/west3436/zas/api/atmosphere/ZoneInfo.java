package io.github.west3436.zas.api.atmosphere;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.gas.GasMixture;
import java.util.Objects;

/**
 * A zone as of the last applied atmos tick (API-011, API-046). It is an
 * immutable snapshot: later ticks never change it.
 *
 * @param id the zone's id
 * @param volume its gas volume in litres, the same as {@code mixture.volume()}
 * @param cellCount the number of cells in it
 * @param mixture its gas
 * @param edges a summary of its edges
 * @param breached whether it is open to the ambient; a zone that is not
 *     breached is enclosed
 * @param paused whether it is paused because part of it is in an unloaded
 *     chunk (GEO-030); its mixture is then the last-known one
 */
@AnyThread
public record ZoneInfo(
        ZoneId id,
        double volume,
        int cellCount,
        GasMixture mixture,
        EdgeSummary edges,
        boolean breached,
        boolean paused) {
    /**
     * Checks the arguments.
     *
     * @throws IllegalArgumentException if the cell count is not positive
     * @throws NullPointerException if an object argument is null
     */
    public ZoneInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mixture, "mixture");
        Objects.requireNonNull(edges, "edges");
        if (cellCount <= 0) {
            throw new IllegalArgumentException("a zone has at least one cell: " + cellCount);
        }
    }

    /**
     * Whether the zone is enclosed, the opposite of {@link #breached()}.
     *
     * <p>Thread: any.
     *
     * @return true if the zone is not open to the ambient
     */
    public boolean enclosed() {
        return !breached;
    }
}
