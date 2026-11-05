package io.github.west3436.zas.airflow;

import java.util.List;

/**
 * The airflow a client was last told to show (CLIENT-003, DATA-015). The server sends only changes
 * ({@link VisibleFlows}), so this holds the last list between packets, for a steady breach to keep
 * showing. A newer list replaces it whole, so an edge that isn't in it has ended, and an empty list
 * ends them all. It also ends on leaving the dimension or the server, and lapses by itself if no
 * resend comes. Platform-free. Client thread.
 */
public final class HeldFlows {
    /** The client's own. */
    public static final HeldFlows CLIENT = new HeldFlows();

    /** The game ticks a list lasts with no packet: three of the server's resends. */
    public static final int LAPSE_TICKS = 3 * VisibleFlows.RESEND_TICKS;

    private List<VisibleFlow> flows = List.of();
    private Object dimension;
    private int ticksLeft;

    /**
     * Takes a list in place of the last.
     *
     * @param dimension the dimension the player is in, which the list belongs to
     */
    public void accept(List<VisibleFlow> flows, Object dimension) {
        this.flows = List.copyOf(flows);
        this.dimension = dimension;
        ticksLeft = flows.isEmpty() ? 0 : LAPSE_TICKS;
    }

    /**
     * One game tick of the client.
     *
     * @param dimension the dimension the player is in now
     */
    public void tick(Object dimension) {
        if (flows.isEmpty()) {
            return;
        }
        if (!dimension.equals(this.dimension) || --ticksLeft <= 0) {
            clear();
        }
    }

    /** The flows to show now. */
    public List<VisibleFlow> flows() {
        return flows;
    }

    /** Drops what it holds, as when leaving a server. */
    public void clear() {
        flows = List.of();
        dimension = null;
        ticksLeft = 0;
    }
}
