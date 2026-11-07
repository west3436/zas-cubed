package io.github.west3436.zas.atmos.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How a central atmospherics controller shares one atmos tick's FE (CONT-115, CONT-120, CONT-121).
 * Pure: no world access, so it is unit-tested.
 *
 * <ol>
 *   <li>It pays its own draw from what it holds and what it took in this tick. Without that it is
 *       unpowered, and pays nothing else.
 *   <li>It pays each area air controller's shortfall in zone priority order, and in the order it
 *       claimed them within a priority. A shortfall it can't pay in full is paid in part, and the
 *       later ones then get nothing.
 *   <li>What it took in this tick and hasn't spent is split evenly among the area air controllers
 *       whose buffers still have room, each up to its room. A share one can't hold goes to the
 *       others.
 *   <li>Whatever is left stays in its own buffer. Nothing is lost.
 * </ol>
 */
public final class CentralFeed {
    private CentralFeed() {}

    /**
     * One claimed area air controller, as the central controller sees it this atmos tick.
     *
     * @param priority its zone power priority
     * @param shortfall what its next step needs that its own buffer doesn't hold, in FE
     * @param room the room in its buffer, in FE; nothing it is given goes past this
     */
    public record Zone(ZonePriority priority, long shortfall, long room) {}

    /**
     * The outcome.
     *
     * @param powered whether the central controller paid its own draw
     * @param paid what each zone gets towards its shortfall, in the order given
     * @param topUp what each zone gets of the spare intake, in the order given
     * @param left what stays in the central controller's buffer
     */
    public record Plan(boolean powered, long[] paid, long[] topUp, long left) {
        /** What zone {@code i} is given in all. */
        public long given(int i) {
            return paid[i] + topUp[i];
        }
    }

    /**
     * Shares one atmos tick's FE.
     *
     * @param stored what the central controller's buffer held before this tick's intake
     * @param intake what it took in from the blocks next to it this tick
     * @param own its own draw for this tick
     * @param zones its claimed area air controllers that are in contact, in the order it claimed them
     * @return what each gets and what it keeps
     */
    public static Plan plan(long stored, long intake, long own, List<Zone> zones) {
        int n = zones.size();
        long[] paid = new long[n];
        long[] topUp = new long[n];
        long available = stored + intake;
        if (available < own) {
            return new Plan(false, paid, topUp, available);
        }
        available -= own;
        long spent = own;

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing(i -> zones.get(i).priority())); // stable: claim order within one
        for (int i : order) {
            Zone z = zones.get(i);
            long pay = Math.min(Math.min(Math.max(0, z.shortfall()), Math.max(0, z.room())), available);
            paid[i] = pay;
            available -= pay;
            spent += pay;
        }

        long spare = Math.min(Math.max(0, intake - spent), available);
        share(spare, zones, paid, topUp);
        for (long t : topUp) {
            available -= t;
        }
        return new Plan(true, paid, topUp, available);
    }

    /**
     * Splits {@code spare} evenly among the zones with room left after their shortfalls, smallest
     * room first, so a share one can't hold passes on to those with more.
     */
    private static void share(long spare, List<Zone> zones, long[] paid, long[] topUp) {
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < zones.size(); i++) {
            if (room(zones, paid, i) > 0) {
                open.add(i);
            }
        }
        open.sort(Comparator.comparingLong(i -> room(zones, paid, i))); // stable: claim order within one
        long left = spare;
        for (int k = 0; k < open.size() && left > 0; k++) {
            int i = open.get(k);
            long give = Math.min(room(zones, paid, i), left / (open.size() - k));
            topUp[i] = give;
            left -= give;
        }
    }

    private static long room(List<Zone> zones, long[] paid, int i) {
        return Math.max(0, zones.get(i).room() - paid[i]);
    }
}
