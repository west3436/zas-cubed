package io.github.west3436.zas.atmos.power;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How a provider shares one atmos tick's power among the parts it covers when its store may not
 * meet the demand (CONT-064, architecture §11.7).
 *
 * <p>Groups are served in priority order: life support, then gas handling, then the rest. Within a
 * group, parts are served in the order given, and each part whose cost fits what is left is
 * powered. If any part of a group misses out, the groups after it get nothing, so a cheap recharger
 * never runs while a vent is starved. A part that draws nothing now is always powered, at no
 * cost. Pure: no world access, so it is unit-tested.
 */
public final class PowerFeed {
    private PowerFeed() {}

    /**
     * One part's demand for this atmos tick.
     *
     * @param priority its group
     * @param cost what one atmos tick of power costs, in FE ({@link DevicePower#drawPerAtmosTick})
     */
    public record Demand(PowerPriority priority, long cost) {}

    /**
     * The outcome.
     *
     * @param powered for each demand, in the order given, whether it is powered
     * @param spent the FE taken from the store
     */
    public record Plan(boolean[] powered, long spent) {}

    /**
     * Shares a store among demands.
     *
     * @param store the FE available
     * @param demands the parts' demands, in the order to serve each group in
     * @return who is powered and what it costs
     */
    public static Plan plan(long store, List<Demand> demands) {
        boolean[] powered = new boolean[demands.size()];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < demands.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing(i -> demands.get(i).priority()));
        long left = store;
        boolean starved = false;
        PowerPriority group = null;
        boolean groupMissed = false;
        for (int i : order) {
            Demand d = demands.get(i);
            if (d.priority() != group) {
                starved |= groupMissed;
                group = d.priority();
                groupMissed = false;
            }
            if (d.cost() <= 0) {
                powered[i] = true;
            } else if (!starved && d.cost() <= left) {
                powered[i] = true;
                left -= d.cost();
            } else {
                groupMissed = true;
            }
        }
        return new Plan(powered, store - left);
    }
}
