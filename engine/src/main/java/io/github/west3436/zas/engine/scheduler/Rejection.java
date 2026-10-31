package io.github.west3436.zas.engine.scheduler;

import java.util.Arrays;

/**
 * The exchange components of one {@link Result} that the server thread discarded as stale
 * (CORE-070), sent back to the worker in the next {@link Batch}. The worker restores them from its
 * undo point and runs their operations again (CORE-074, architecture §3.3).
 *
 * @param tick the atmos tick of the result
 * @param components the rejected components' indices in {@link Result#components()}, ascending
 */
public record Rejection(long tick, int[] components) {
    public Rejection {
        components = components.clone();
        Arrays.sort(components);
    }

    @Override
    public int[] components() {
        return components.clone();
    }

    public boolean isEmpty() {
        return components.length == 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Rejection r && tick == r.tick && Arrays.equals(components, r.components);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(tick) * 31 + Arrays.hashCode(components);
    }

    @Override
    public String toString() {
        return "Rejection[tick " + tick + ", components " + Arrays.toString(components) + "]";
    }
}
