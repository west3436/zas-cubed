package io.github.west3436.zas.atmos.device;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Steps each member once per period, spread across the period's game ticks (D-31, CONT-006). It is
 * plain Java, so its timing is unit-tested without Minecraft; {@link DeviceTicker} keeps one per
 * level and runs it on the server thread. It is not thread-safe.
 *
 * <p><b>Spreading.</b> The n-th member added is first due {@code 1 + n % period} game ticks after
 * it is added, so members added together are spread over the period. After a step, a member is
 * due again one period later.
 *
 * <p><b>Waiting.</b> A member that isn't {@linkplain Member#readyToStep() ready} when it is due,
 * because a zone mutation of one of its ports is still outstanding (CONT-072), is asked again
 * every game tick until it is. It then steps and is due one period after that, so it keeps its new
 * place in the period. API mutations complete when the framework applies an atmos tick, so a
 * device that waits settles just after that point and stops waiting.
 *
 * <p><b>Passing over.</b> A member that isn't {@linkplain Member#ticking() ticking} when it is due,
 * because its chunk doesn't tick block entities now, isn't stepped or asked whether it is ready. It is
 * due again one period later, so it keeps its place in the period, and steps at the first of those
 * after it ticks again.
 *
 * <p><b>Order.</b> Members due at the same game tick step in the order they became due, then in
 * the order they were added, so a run is deterministic (CORE-044).
 *
 * @param <M> the members
 */
public final class DeviceSchedule<M extends DeviceSchedule.Member> {
    /** Something stepped once per period. */
    public interface Member {
        /**
         * Whether the member can step now. A device with a zone mutation outstanding on a port
         * returns false (CONT-072).
         *
         * @return true to step now, false to be asked again next game tick
         */
        default boolean readyToStep() {
            return true;
        }

        /**
         * Whether the member steps at all now. A device whose chunk doesn't tick block entities
         * returns false, and is passed over until a period after it does.
         *
         * @return true to be stepped when due, false to be passed over for this period
         */
        default boolean ticking() {
            return true;
        }

        /** Runs one period's work: one atmos tick, {@link AtmosTicks#SECONDS} seconds. */
        void step();
    }

    private static final class Entry<M> {
        final M member;
        final long order;
        long due;
        boolean removed;

        Entry(M member, long order, long due) {
            this.member = member;
            this.order = order;
            this.due = due;
        }
    }

    private final int period;
    private final PriorityQueue<Entry<M>> queue =
            new PriorityQueue<>(Comparator.<Entry<M>>comparingLong(e -> e.due).thenComparingLong(e -> e.order));
    // Lookup by identity is all this map is for; its insertion order lists the members.
    private final Map<M, Entry<M>> entries = new LinkedHashMap<>();
    private long added;

    /**
     * @param period game ticks between a member's steps, at least 1
     * @throws IllegalArgumentException if the period is less than 1
     */
    public DeviceSchedule(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be at least 1: " + period);
        }
        this.period = period;
    }

    /** The game ticks between a member's steps. */
    public int period() {
        return period;
    }

    /**
     * Adds a member. Adding one already here does nothing.
     *
     * @param member the member
     * @param now the current game tick
     */
    public void add(M member, long now) {
        if (entries.containsKey(member)) {
            return;
        }
        Entry<M> e = new Entry<>(member, added, now + 1 + added % period);
        added++;
        entries.put(member, e);
        queue.add(e);
    }

    /**
     * Removes a member; it never steps again unless added again. Removing one that isn't here does
     * nothing. A member may remove itself, or another, while stepping.
     *
     * @param member the member
     */
    public void remove(M member) {
        Entry<M> e = entries.remove(member);
        if (e != null) {
            e.removed = true;
        }
    }

    /** Whether a member is here. */
    public boolean contains(M member) {
        return entries.containsKey(member);
    }

    /** The members, in the order they were added. */
    public List<M> members() {
        return new ArrayList<>(entries.keySet());
    }

    /** The number of members. */
    public int size() {
        return entries.size();
    }

    /**
     * Steps every member due at or before a game tick that is ticking and ready, asks those that
     * aren't ready again next game tick, and passes over those that aren't ticking until a period
     * later. Call it once per game tick.
     *
     * @param now the current game tick
     */
    public void tick(long now) {
        while (!queue.isEmpty() && queue.peek().due <= now) {
            Entry<M> e = queue.poll();
            if (e.removed) {
                continue;
            }
            if (!e.member.ticking()) {
                e.due += period; // its phase stays
                queue.add(e);
                continue;
            }
            boolean ready = e.member.readyToStep();
            e.due = now + (ready ? period : 1);
            queue.add(e);
            if (ready) {
                e.member.step();
            }
        }
    }

    /**
     * Steps every ready member once now, in the order they were added, ticking or not, without
     * changing when they are next due. For GameTests that drive atmos ticks themselves.
     */
    public void stepAll() {
        for (M member : members()) {
            if (entries.containsKey(member) && member.readyToStep()) {
                member.step();
            }
        }
    }
}
