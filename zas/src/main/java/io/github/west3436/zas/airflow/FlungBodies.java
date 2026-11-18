package io.github.west3436.zas.airflow;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * The entities the server last said airflow has flung (CLIENT-027), by network id, from {@link
 * TumblePayload}s. The client drops an id once its entity is gone, and everything on leaving the
 * server. Platform-free. Client thread.
 */
public final class FlungBodies {
    /** The client's own. */
    public static final FlungBodies CLIENT = new FlungBodies();

    private final IntSet flung = new IntOpenHashSet();

    /** Takes a payload. */
    public void accept(TumblePayload p) {
        if (p.flung()) {
            flung.add(p.entityId());
        } else {
            flung.remove(p.entityId());
        }
    }

    /** Whether the server last said this entity is flung. */
    public boolean flung(int entityId) {
        return flung.contains(entityId);
    }

    /** The ids it holds, for the client to look over each tick; it may remove from it. */
    public IntSet ids() {
        return flung;
    }

    /** Drops what it holds, as when leaving a server. */
    public void clear() {
        flung.clear();
    }
}
