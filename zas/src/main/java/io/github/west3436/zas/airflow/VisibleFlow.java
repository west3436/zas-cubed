package io.github.west3436.zas.airflow;

import java.util.List;

/**
 * One active edge, as a client needs it to show the flow (CLIENT-003, DATA-015). Its zones aren't
 * included (D-53). Platform-free.
 *
 * @param kpa the edge's pressure difference, in whole kPa, so a change smaller than that isn't a
 *     change worth a packet
 * @param openings its openings within the player's range, at most {@link
 *     VisibleFlows#MAX_OPENINGS_PER_FLOW}
 */
public record VisibleFlow(int kpa, List<VisibleOpening> openings) {
    public VisibleFlow {
        openings = List.copyOf(openings);
    }
}
