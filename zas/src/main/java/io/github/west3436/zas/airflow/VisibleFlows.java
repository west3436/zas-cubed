package io.github.west3436.zas.airflow;

import io.github.west3436.zas.engine.flow.FlowField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which airflow a player's client is sent to show (CLIENT-003, DATA-015), and when. Platform-free.
 *
 * <p>This is wider than what the client pushes its player with ({@link AirflowSyncPayload}): every
 * opening the engine exported within 32 blocks, on any edge, whichever zone the player is in, at
 * the lowest class threshold, and for creative players and spectators too.
 *
 * <p>The server sends a player's whole list when it changes: when a difference changes by a whole
 * kPa, the flow in moles by a step, or an opening comes into range, leaves it, or changes
 * direction. So a steady breach sends nothing; the client holds the last list ({@link HeldFlows}),
 * and an edge that is missing from a newer list has ended. As a failsafe against a client left showing a flow that ended, a list that
 * isn't empty is sent again every {@value #RESEND_TICKS} game ticks and lapses on the client after
 * {@value HeldFlows#LAPSE_TICKS}.
 */
public final class VisibleFlows {
    /** How far from a player an opening is shown, blocks (CLIENT-003). */
    public static final double RANGE = 32;

    /** The most edges in one list. If there are more, the ones nearest the player are kept. */
    public static final int MAX_FLOWS = 32;

    /** The most openings of one edge in a list. Beyond it they are sampled evenly. */
    public static final int MAX_OPENINGS_PER_FLOW = 64;

    /** The most openings in one list. Beyond it each edge gets a smaller share. */
    public static final int MAX_OPENINGS = 256;

    /** The game ticks between resends of an unchanged list that isn't empty. */
    public static final int RESEND_TICKS = 200;

    private VisibleFlows() {}

    private record EdgeKey(long high, long low) {}

    /** The flows to show a player at this point, in an order that doesn't depend on the player. */
    public static List<VisibleFlow> near(FlowField field, double x, double y, double z) {
        if (field.isEmpty()) {
            return List.of();
        }
        Map<EdgeKey, List<FlowField.Opening>> edges = new HashMap<>();
        for (FlowField.Opening o : field.near(x, y, z, RANGE, FlowField.ANY_ZONE, 0)) {
            edges.computeIfAbsent(new EdgeKey(o.highZone(), o.lowZone()), k -> new ArrayList<>())
                    .add(o);
        }
        if (edges.isEmpty()) {
            return List.of();
        }
        List<EdgeKey> keys = new ArrayList<>(edges.keySet());
        if (keys.size() > MAX_FLOWS) {
            Map<EdgeKey, Double> nearest = new HashMap<>();
            for (EdgeKey key : keys) {
                nearest.put(
                        key,
                        edges.get(key).stream()
                                .mapToDouble(o -> o.distance(x, y, z))
                                .min()
                                .orElse(Double.MAX_VALUE));
            }
            keys.sort(Comparator.comparingDouble(nearest::get));
            keys = new ArrayList<>(keys.subList(0, MAX_FLOWS));
        }
        keys.sort(Comparator.comparingLong(EdgeKey::high).thenComparingLong(EdgeKey::low));
        int cap = Math.min(MAX_OPENINGS_PER_FLOW, MAX_OPENINGS / keys.size());
        List<VisibleFlow> out = new ArrayList<>(keys.size());
        for (EdgeKey key : keys) {
            List<FlowField.Opening> openings = edges.get(key);
            double moles = 0;
            for (FlowField.Opening o : openings) {
                moles += o.molesPerSecond();
            }
            out.add(new VisibleFlow(
                    (int) Math.round(openings.get(0).difference()), VisibleFlow.step(moles), sample(openings, cap)));
        }
        return out;
    }

    /** At most {@code cap} of the openings, in position order, evenly across that order. */
    private static List<VisibleOpening> sample(List<FlowField.Opening> openings, int cap) {
        List<VisibleOpening> all = new ArrayList<>(openings.size());
        for (FlowField.Opening o : openings) {
            all.add(new VisibleOpening(
                    o.hx(), o.hy(), o.hz(), VisibleOpening.faceOf(o.lx() - o.hx(), o.ly() - o.hy(), o.lz() - o.hz())));
        }
        all.sort(Comparator.comparingInt(VisibleOpening::x)
                .thenComparingInt(VisibleOpening::z)
                .thenComparingInt(VisibleOpening::y)
                .thenComparingInt(VisibleOpening::face));
        int n = all.size();
        if (n <= cap) {
            return all;
        }
        List<VisibleOpening> out = new ArrayList<>(cap);
        for (int i = 0; i < cap; i++) {
            out.add(all.get((int) ((long) i * n / cap)));
        }
        return out;
    }

    /**
     * Whether a player must be sent this list.
     *
     * @param last what it was last sent, empty for nothing or an end
     * @param now what it should be showing
     * @param ticksSinceSent game ticks since {@code last} was sent
     */
    public static boolean shouldSend(List<VisibleFlow> last, List<VisibleFlow> now, long ticksSinceSent) {
        if (!now.equals(last)) {
            return true;
        }
        return !now.isEmpty() && ticksSinceSent >= RESEND_TICKS;
    }
}
