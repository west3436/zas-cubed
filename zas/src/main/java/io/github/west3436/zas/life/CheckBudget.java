package io.github.west3436.zas.life;

import java.util.function.IntSupplier;

/**
 * The cap on breath and hazard checks per server tick (LIFE-038). A check that would go over it
 * waits for the next tick. Server thread.
 */
public final class CheckBudget {
    private final IntSupplier cap;
    private long tick = Long.MIN_VALUE;
    private int used;

    /** @param cap the most checks per game tick, read at each tick's first check */
    public CheckBudget(IntSupplier cap) {
        this.cap = cap;
    }

    /**
     * Takes one check at game time {@code now}, if the tick has room for it.
     *
     * @return whether the check may run
     */
    public boolean take(long now) {
        if (now != tick) {
            tick = now;
            used = 0;
        }
        if (used >= cap.getAsInt()) {
            return false;
        }
        used++;
        return true;
    }

    /** The checks taken at game time {@code now}. */
    public int used(long now) {
        return now == tick ? used : 0;
    }
}
