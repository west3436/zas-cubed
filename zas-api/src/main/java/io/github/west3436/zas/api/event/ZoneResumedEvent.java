package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A paused zone is simulated again (API-030). Also fired for a zone back from chunk storage once all
 * its chunks are loaded, and so after a restart with no {@link ZonePausedEvent} before it, and with no
 * {@link ZoneCreatedEvent}.
 *
 * <p>How it fires is in {@link ZoneLifecycleEvent}.
 */
@ServerThread
public final class ZoneResumedEvent extends ZoneLifecycleEvent {
    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param zone the zone resumed
     * @param pos a block in the zone
     */
    public ZoneResumedEvent(ServerLevel level, ZoneId zone, BlockPos pos) {
        super(level, zone, pos);
    }
}
