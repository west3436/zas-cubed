package io.github.west3436.zas.atmos.compat.jade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Which gases of a body a reading lists (COMPAT-031): the largest few, without the traces, so the
 * list stays short. The analyzer is what lists every gas (COMPAT-032).
 */
final class Shares {
    /** A gas with a smaller share than this is a trace, and isn't listed. */
    static final double TRACE = 0.005;

    /**
     * The order the device windows list gases in: by path, then namespace, as a gas's id compares.
     * A gas keeps its place while the shares change.
     */
    private static final Comparator<Reading.Share> BY_ID =
            Comparator.comparing((Reading.Share share) -> path(share.gas())).thenComparing(Reading.Share::gas);

    private static final Comparator<Reading.Share> LARGEST_FIRST = Comparator.comparingDouble(
                    (Reading.Share share) -> share.fraction())
            .reversed()
            .thenComparing(BY_ID);

    private Shares() {}

    /**
     * The shares to list for a body of gas.
     *
     * @param moles each gas's moles, by its id
     * @return at most {@link Reading#MOST_SHARES} shares of at least {@link #TRACE}, the largest if
     *     there are more, in the windows' order; empty for a vacuum
     */
    static List<Reading.Share> of(Map<?, Double> moles) {
        double total = 0;
        for (double amount : moles.values()) {
            total += Math.max(0, amount);
        }
        if (!(total > 0)) {
            return List.of();
        }
        List<Reading.Share> shares = new ArrayList<>(moles.size());
        for (Map.Entry<?, Double> gas : moles.entrySet()) {
            double fraction = gas.getValue() / total;
            if (fraction >= TRACE) {
                shares.add(new Reading.Share(gas.getKey().toString(), (float) fraction));
            }
        }
        if (shares.size() > Reading.MOST_SHARES) {
            shares.sort(LARGEST_FIRST);
            shares = new ArrayList<>(shares.subList(0, Reading.MOST_SHARES));
        }
        shares.sort(BY_ID);
        return shares;
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }
}
