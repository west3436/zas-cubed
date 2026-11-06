package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A zone stopped being simulated (API-030) and keeps its last-known state: part of it is in an
 * unloaded chunk, it opens onto a region that can't be classified yet, or an operator paused the
 * dimension, which pauses every zone in it. Also fired for a zone whose last chunks unloaded, which
 * is stored with them.
 *
 * <p>How it fires is in {@link ZoneLifecycleEvent}.
 */
@ServerThread
public final class ZonePausedEvent extends ZoneLifecycleEvent {
    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param zone the zone paused
     * @param pos a block in the zone
     */
    public ZonePausedEvent(ServerLevel level, ZoneId zone, BlockPos pos) {
        super(level, zone, pos);
    }
}
